package jp.linkserver.goldy

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class HttpRequest(val url: String, val headers: Map<String, String>, val maxBytes: Int)
internal data class HttpResponse(val status: Int, val body: String, val headers: Map<String, String> = emptyMap()) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
}
internal fun interface HttpTransport { suspend fun execute(request: HttpRequest): HttpResponse }

/** GitHub.com metadata client; API redirects are rejected, never followed to another repository. */
class GitHubReleaseClient internal constructor(
    private val config: GoldyConfig,
    private val transport: HttpTransport,
    private val now: () -> Long
) {
    constructor(config: GoldyConfig) : this(config, UrlConnectionTransport, System::currentTimeMillis)

    private data class Page(val etag: String?, val response: HttpResponse)
    private val pages = linkedMapOf<String, Page>()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun fetch(): ReleaseCatalog = withContext(Dispatchers.IO) {
        mutex.lock()
        try { fetchLocked() } finally { mutex.unlock() }
    }

    private suspend fun fetchLocked(): ReleaseCatalog {
        val feed = config.policy.feed
        val latest = feed == ReleaseFeed.Latest
        val list = feed as? ReleaseFeed.ListReleases
        var url: String? = config.repository.apiUrl +
            if (latest) "/latest" else "?per_page=" + list!!.perPage + "&page=1"
        var count = 0
        var bytes = 0L
        val releases = linkedMapOf<Long, GitHubRelease>()
        val seen = mutableSetOf<String>()
        while (url != null && count < (list?.maxPages ?: 1)) {
            coroutineContext.ensureActive()
            val requestUrl = url
            requireApiUrl(requestUrl, list)
            if (!seen.add(requestUrl)) throw GitHubException(GoldyError.InvalidResponse)
            val cached = pages[requestUrl]
            val headers = mutableMapOf(
                "Accept" to "application/vnd.github+json", "User-Agent" to config.userAgent,
                "X-GitHub-Api-Version" to "2022-11-28"
            )
            cached?.etag?.let { headers["If-None-Match"] = it }
            var response = try {
                transport.execute(HttpRequest(requestUrl, headers, config.maxResponseBytes))
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: GitHubException) { throw error }
            catch (_: Exception) { throw GitHubException(GoldyError.Network) }
            val freshResponse = response.status != 304
            if (response.status == 304) {
                response = cached?.response ?: throw GitHubException(GoldyError.InvalidResponse)
            } else {
                if (response.status in 300..399) throw GitHubException(GoldyError.InvalidUrl)
                if (response.status != 200) throw GitHubException(
                    GoldyError.Http(response.status, retryAt(response))
                )
            }
            val pageBytes = response.body.toByteArray(Charsets.UTF_8).size
            bytes += pageBytes
            if (pageBytes > config.maxResponseBytes || bytes > config.maxCatalogBytes) {
                throw GitHubException(GoldyError.InvalidResponse)
            }
            val parsed = GitHubReleaseParser.parse(config.repository, response.body, latest, config.policy.allowNameAsTag)
            parsed.forEach { releases.putIfAbsent(it.id, it) }
            count++
            url = if (latest) null else nextLink(response.header("Link"))
            url?.let { requireApiUrl(it, list) }
            if (freshResponse) {
                pages.remove(requestUrl)
                pages[requestUrl] = Page(response.header("ETag"), response)
                while (pages.values.sumOf { it.response.body.toByteArray(Charsets.UTF_8).size.toLong() } > config.maxCatalogBytes) {
                    pages.remove(pages.keys.first())
                }
            }
        }
        coroutineContext.ensureActive()
        return ReleaseCatalog(releases.values.toList(), complete = url == null)
    }

    private fun requireApiUrl(value: String, feed: ReleaseFeed.ListReleases?) {
        try {
            val uri = secureUri(value, "api.github.com")
            val expected = URI(config.repository.apiUrl).rawPath
            require(uri.rawFragment == null && safePath(uri.rawPath))
            if (feed == null) {
                require(uri.rawPath == expected + "/latest" && uri.rawQuery == null)
            } else {
                require(uri.rawPath == expected)
                val parts = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
                require(parts.size == 2 && parts.all { it.size == 2 })
                val query = parts.associate { it[0] to it[1] }
                require(query.keys == setOf("per_page", "page"))
                require(query["per_page"]?.toIntOrNull() == feed.perPage)
                require((query["page"]?.toIntOrNull() ?: 0) > 0)
            }
        } catch (_: Exception) { throw GitHubException(GoldyError.InvalidUrl) }
    }

    private fun nextLink(header: String?): String? {
        if (header == null) return null
        val links = Regex("""<([^>]+)>\s*;\s*rel="([^"]+)"""").findAll(header).toList()
        val next = links.filter { it.groupValues[2] == "next" }
        if (next.size > 1 || (header.contains("next") && next.isEmpty())) {
            throw GitHubException(GoldyError.InvalidResponse)
        }
        return next.singleOrNull()?.groupValues?.get(1)
    }

    private fun retryAt(response: HttpResponse): Long? {
        if (response.status != 403 && response.status != 429) return null
        val retry = response.header("Retry-After")
        val seconds = retry?.toLongOrNull()
        if (seconds != null && seconds >= 0) {
            return runCatching { Math.addExact(now(), Math.multiplyExact(seconds, 1000)) }.getOrDefault(Long.MAX_VALUE)
        }
        if (retry != null) runCatching {
            ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }
        return response.header("X-RateLimit-Reset")?.toLongOrNull()?.let {
            runCatching { Math.multiplyExact(it, 1000) }.getOrNull()
        }
    }
}

private object UrlConnectionTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = coroutineScope {
        val connection = (URI(request.url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        val worker = async(Dispatchers.IO) {
            val status = connection.responseCode
            val headers = connection.headerFields.entries.filter { it.key != null }.associate {
                it.key to it.value.joinToString(",")
            }
            val body = if (status == 200) connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size().toLong() + count > request.maxBytes) throw GitHubException(GoldyError.InvalidResponse)
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            } else ""
            HttpResponse(status, body, headers)
        }
        try { worker.await() } finally { connection.disconnect() }
    }
}
