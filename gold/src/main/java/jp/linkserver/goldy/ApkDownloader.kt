package jp.linkserver.goldy

import android.content.Context
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Retry always starts a fresh transfer; partial bytes are never appended or reused. */
data class ApkDownloadConfig(
    val repository: GitHubRepository,
    val selection: ApkSelectionPolicy = ApkSelectionPolicies.Single,
    val versionBinding: ApkVersionBinding = ApkVersionBindings.Exact,
    val maxBytes: Long = 256L * 1024 * 1024,
    val maxAttempts: Int = 3,
    val userAgent: String = "Goldy/0.1.0"
) {
    init {
        require(maxBytes > 0); require(maxAttempts in 1..5)
        require(userAgent.isNotBlank() && userAgent.none { it == '\r' || it == '\n' })
    }
}
data class ApkProgress(val bytes: Long, val total: Long?, val attempt: Int)
/** The caller owns this cache file and should delete it after use. Never modify it before use. */
class VerifiedApk internal constructor(val file: File, val metadata: ApkMetadata, val sha256: String, val asset: GitHubAsset)

/** No background scope or installer. All transfers in this process share one cancellable gate. */
class ApkDownloader internal constructor(
    private val config: ApkDownloadConfig,
    private val directory: File,
    private val inspector: ApkInspector,
    private val transport: AssetTransport
) {
    companion object {
        private val transferGate = Mutex()
        fun create(context: Context, config: ApkDownloadConfig): ApkDownloader = ApkDownloader(
            config, File(context.applicationContext.cacheDir, "goldy-apks"), AndroidApkInspector(context), UrlAssetTransport)
    }

    suspend fun download(release: GitHubRelease, onProgress: (ApkProgress) -> Unit = {}): VerifiedApk = transferGate.withLock {
        val asset = config.selection.select(release) ?: throw ApkException(ApkFailure.NoAsset)
        if (asset !in release.assets || asset.id <= 0 || !asset.name.endsWith(".apk", true)) throw ApkException(ApkFailure.NoAsset)
        if (!config.repository.acceptsReleaseUrl(release.url) || !acceptsInitial(release, asset)) throw ApkException(ApkFailure.InvalidUrl)
        if (asset.size != null && (asset.size <= 0 || asset.size > config.maxBytes)) throw ApkException(ApkFailure.TooLarge)
        val expectedDigest = asset.digest?.let {
            if (!it.matches(Regex("sha256:[0-9a-fA-F]{64}"))) throw ApkException(ApkFailure.InvalidDigest)
            it.substringAfter(':').lowercase()
        }
        for (attempt in 1..config.maxAttempts) {
            currentCoroutineContext().ensureActive()
            try {
                return@withLock transfer(release, asset, expectedDigest, attempt, onProgress)
            } catch (error: CancellationException) { throw error
            } catch (error: ApkException) {
                val retryable = error.reason == ApkFailure.Network || error.cause is EOFException ||
                    (error.reason == ApkFailure.Http && (error.status == 408 || error.status in 500..599))
                if (!retryable || attempt == config.maxAttempts) throw error
                delay(250L * attempt)
            }
        }
        error("unreachable")
    }

    private fun acceptsInitial(release: GitHubRelease, asset: GitHubAsset): Boolean = runCatching {
        val assetPrefix = "/" + config.repository.owner + "/" + config.repository.name + "/releases/download/"
        val releasePrefix = "/" + config.repository.owner + "/" + config.repository.name + "/releases/tag/"
        val assetPath = URI(asset.downloadUrl).path
        val releasePath = URI(release.url).path
        config.repository.acceptsAssetUrl(asset.downloadUrl) &&
            '/' !in asset.name && '\\' !in asset.name &&
            assetPath.startsWith(assetPrefix, true) && assetPath.substring(assetPrefix.length) == release.tag + "/" + asset.name &&
            releasePath.startsWith(releasePrefix, true) && releasePath.substring(releasePrefix.length) == release.tag
    }.getOrDefault(false)

    /** Removes only this component's abandoned partial files, serialized against active transfers. */
    suspend fun cleanPartialFiles() = transferGate.withLock {
        kotlinx.coroutines.withContext(Dispatchers.IO) { cleanParts() }
    }
    private fun cleanParts() {
        if (!directory.exists() && !directory.mkdirs()) throw ApkException(ApkFailure.Storage)
        val files = directory.listFiles() ?: throw ApkException(ApkFailure.Storage)
        for (file in files) if (file.name.matches(Regex("goldy-[0-9a-f-]{36}\\.part")) && !file.delete())
            throw ApkException(ApkFailure.Storage)
    }

    private suspend fun transfer(release: GitHubRelease, asset: GitHubAsset, expectedDigest: String?, attempt: Int,
                                 progress: (ApkProgress) -> Unit): VerifiedApk {
        var completedFile: File? = null
        try { return coroutineScope {
            var active: AssetConnection? = null
            val task = async(Dispatchers.IO) {
                cleanParts()
                val id = UUID.randomUUID().toString()
                val partial = File(directory, "goldy-" + id + ".part")
                val destination = File(directory, "goldy-" + id + ".apk")
                var committed = false
                try {
                    var url = asset.downloadUrl
                    var redirects = 0
                    var connection: AssetConnection
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        connection = try { transport.open(url, config.userAgent) } catch (e: IOException) {
                            currentCoroutineContext().ensureActive(); throw ApkException(ApkFailure.Network, cause = e)
                        }
                        synchronized(this@ApkDownloader) { active = connection }
                        currentCoroutineContext().ensureActive()
                        val status = try { connection.status } catch (error: IOException) { currentCoroutineContext().ensureActive(); throw ApkException(ApkFailure.Network, cause = error) }
                        if (status !in setOf(301, 302, 303, 307, 308)) {
                            if (status != 200) throw ApkException(ApkFailure.Http, status)
                            break
                        }
                        if (++redirects > 5) throw ApkException(ApkFailure.RedirectLimit)
                        val next = connection.header("Location")?.let { runCatching { URI(url).resolve(it).toString() }.getOrNull() }
                            ?: throw ApkException(ApkFailure.InvalidUrl)
                        if (!AssetUrls.acceptsRedirect(next)) throw ApkException(ApkFailure.InvalidUrl)
                        connection.close()
                        url = next
                    }
                    if (connection.header("Content-Encoding")?.let { !it.equals("identity", true) } == true)
                        throw ApkException(ApkFailure.SizeMismatch)
                    val lengthHeader = connection.header("Content-Length")
                    val length = lengthHeader?.toLongOrNull()
                    if (lengthHeader != null && (length == null || length <= 0)) throw ApkException(ApkFailure.SizeMismatch)
                    if (length != null && length > config.maxBytes) throw ApkException(ApkFailure.TooLarge)
                    if (length != null && asset.size != null && length != asset.size) throw ApkException(ApkFailure.SizeMismatch)
                    val total = length ?: asset.size
                    progress(ApkProgress(0, total, attempt))
                    val digest = MessageDigest.getInstance("SHA-256")
                    var bytes = 0L
                    try {
                        connection.input().use { input ->
                            val outputFile = try { partial.outputStream() } catch (e: IOException) { throw ApkException(ApkFailure.Storage, cause = e) }
                            outputFile.use { output ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = try { input.read(buffer) } catch (e: IOException) { currentCoroutineContext().ensureActive(); throw ApkException(ApkFailure.Network, cause = e) }
                                    if (count < 0) break
                                    if (count == 0) continue
                                    if (bytes > config.maxBytes - count) throw ApkException(ApkFailure.TooLarge)
                                    bytes += count
                                    if ((length != null && bytes > length) || (asset.size != null && bytes > asset.size))
                                        throw ApkException(ApkFailure.SizeMismatch)
                                    try { output.write(buffer, 0, count) } catch (e: IOException) { throw ApkException(ApkFailure.Storage, cause = e) }
                                    digest.update(buffer, 0, count)
                                    progress(ApkProgress(bytes, total, attempt))
                                }
                            }
                        }
                    } catch (e: IOException) { currentCoroutineContext().ensureActive(); throw ApkException(ApkFailure.Network, cause = e) }
                    currentCoroutineContext().ensureActive()
                    if (bytes == 0L || (length != null && bytes != length) || (asset.size != null && bytes != asset.size))
                        throw ApkException(ApkFailure.SizeMismatch, cause = EOFException("Incomplete APK transfer"))
                    val actualDigest = digest.digest().hex()
                    if (expectedDigest != null && actualDigest != expectedDigest) throw ApkException(ApkFailure.DigestMismatch)
                    val metadata = try { ApkVerifier.verify(inspector.installed(), inspector.inspect(partial), release, config.versionBinding) }
                        catch (e: ApkException) { throw e }
                        catch (e: Exception) { throw ApkException(ApkFailure.InvalidApk, cause = e) }
                    currentCoroutineContext().ensureActive()
                    if (!partial.renameTo(destination)) throw ApkException(ApkFailure.Storage)
                    currentCoroutineContext().ensureActive()
                    committed = true
                    completedFile = destination
                    VerifiedApk(destination, metadata, actualDigest, asset)
                } catch (e: IOException) { currentCoroutineContext().ensureActive(); throw ApkException(ApkFailure.Storage, cause = e)
                } finally {
                    synchronized(this@ApkDownloader) { active?.close(); active = null }
                    if (partial.exists() && !partial.delete()) throw ApkException(ApkFailure.Storage)
                    if (!committed && destination.exists()) destination.delete()
                }
            }
            try { task.await() } finally {
                // Disconnect blocking response/read operations before coroutineScope waits for the IO child.
                synchronized(this@ApkDownloader) { active?.close() }
            }
        } } catch (error: Throwable) {
            // Includes prompt cancellation after the IO child has finalized its result.
            completedFile?.delete()
            throw error
        }
    }
}

internal object AssetUrls {
    private val hosts = setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    fun acceptsRedirect(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && uri.host?.lowercase() in hosts && uri.userInfo == null &&
            uri.port in setOf(-1, 443) && uri.rawFragment == null && safePath(uri.rawPath) && uri.rawPath.startsWith('/')
    }.getOrDefault(false)
}
internal interface AssetConnection : Closeable {
    val status: Int
    fun header(name: String): String?
    fun input(): InputStream
}
internal fun interface AssetTransport { fun open(url: String, userAgent: String): AssetConnection }
internal object UrlAssetTransport : AssetTransport {
    override fun open(url: String, userAgent: String): AssetConnection {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000; connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Encoding", "identity")
        return object : AssetConnection {
            override val status get() = connection.responseCode
            override fun header(name: String): String? = connection.getHeaderField(name)
            override fun input(): InputStream = connection.inputStream
            override fun close() = connection.disconnect()
        }
    }
}
