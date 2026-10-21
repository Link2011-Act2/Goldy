package jp.linkserver.goldy

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubReleaseClientTest {
    private val config = GoldyConfig(testRepository)
    private class Scripted(vararg responses: HttpResponse) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        val responses = ArrayDeque(responses.toList())
        override suspend fun execute(request: HttpRequest): HttpResponse { requests += request; return responses.removeFirst() }
    }

    @Test fun parserHandlesDraftNullNotesAssetsAndLatest() {
        val json = releaseJson("v2.0").put("body", JSONObject.NULL).put("assets", JSONArray().put(
            JSONObject().put("id", 10).put("name", "app.apk")
                .put("browser_download_url", testRepository.url + "/releases/download/v2.0/app.apk")
                .put("size", 1024).put("digest", "sha256:unverified-metadata")))
        val body = JSONArray().put(releaseJson("v9.0", draft = true)).put(json).toString()
        val release = GitHubReleaseParser.parse(testRepository, body).single()
        assertEquals("", release.notes)
        assertEquals(1024L, release.assets.single().size)
        assertEquals("sha256:unverified-metadata", release.assets.single().digest)
        assertEquals("v2.0", GitHubReleaseParser.parse(testRepository, json.toString(), latest = true).single().tag)
    }

    @Test fun nameFallbackIsExplicitAndInvalidResponseFailsClosed() {
        val json = releaseJson("").put("name", "v2.0").put("html_url", testRepository.url + "/releases/tag/v2.0")
        assertTrue(GitHubReleaseParser.parse(testRepository, JSONArray().put(json).toString()).isEmpty())
        assertEquals("v2.0", GitHubReleaseParser.parse(testRepository, JSONArray().put(json).toString(), allowNameAsTag = true).single().tag)
        for (body in listOf("not json", "{}", "[{}]",
            JSONArray().put(releaseJson("v2.0").put("html_url", "https://evil.example/release")).toString())) {
            assertThrows(GitHubException::class.java) { GitHubReleaseParser.parse(testRepository, body) }
        }
    }

    @Test fun paginationEtagAndDeduplication() = runTest {
        val next = "https://api.github.com/repos/owner/app/releases?per_page=100&page=2"
        val transport = Scripted(
            HttpResponse(200, response("v2.0"), mapOf("ETag" to "p1", "Link" to "<$next>; rel=\"next\"")),
            HttpResponse(200, response("v1.5", "v2.0"), mapOf("ETag" to "p2")),
            HttpResponse(304, ""), HttpResponse(304, "")
        )
        val client = GitHubReleaseClient(config, transport) { 0 }
        assertEquals(listOf("v2.0", "v1.5"), client.fetch().releases.map { it.tag })
        assertTrue(client.fetch().complete)
        assertEquals("p1", transport.requests[2].headers["If-None-Match"])
        assertEquals("p2", transport.requests[3].headers["If-None-Match"])
        assertEquals("Goldy/0.1.0", transport.requests.first().headers["User-Agent"])
    }

    @Test fun truncatedCatalogIsMarkedIncomplete() = runTest {
        val transport = Scripted(HttpResponse(200, response("v2.0"),
            mapOf("Link" to "<https://api.github.com/repos/owner/app/releases?per_page=100&page=2>; rel=\"next\"")))
        val client = GitHubReleaseClient(config.copy(policy = UpdatePolicies.shutUp()), transport) { 0 }
        assertFalse(client.fetch().complete)
        assertEquals(1, transport.requests.size)
    }

    @Test fun latestUsesTheLatestEndpointOnly() = runTest {
        val transport = Scripted(HttpResponse(200, releaseJson("v1.0").toString()))
        val client = GitHubReleaseClient(config.copy(policy = UpdatePolicies.glyphBarty()), transport) { 0 }
        assertEquals("v1.0", client.fetch().releases.single().tag)
        assertEquals("https://api.github.com/repos/owner/app/releases/latest", transport.requests.single().url)
    }

    @Test fun untrustedPaginationAndRedirectsAreRejected() = runTest {
        val invalid = listOf("http://api.github.com/repos/owner/app/releases?per_page=100&page=2",
            "https://api.github.com.evil.example/next", "https://api.github.com/repos/other/app/releases?per_page=100&page=2",
            "https://api.github.com/repos/owner/app/releases?per_page=100&page=2&token=secret",
            "https://api.github.com/repos/owner/app/releases?per_page=30&page=2")
        for (url in invalid) {
            val transport = Scripted(HttpResponse(200, response("v2.0"), mapOf("Link" to "<$url>; rel=\"next\"")))
            try { GitHubReleaseClient(config, transport) { 0 }.fetch(); fail(url) }
            catch (error: GitHubException) { assertEquals(GoldyError.InvalidUrl, error.error) }
            assertEquals(1, transport.requests.size)
        }
        try { GitHubReleaseClient(config, Scripted(HttpResponse(302, ""))) { 0 }.fetch(); fail() }
        catch (error: GitHubException) { assertEquals(GoldyError.InvalidUrl, error.error) }
    }

    @Test fun httpRateLimitAndNetworkFailuresAreTyped() = runTest {
        for (status in listOf(404, 500, 403, 429)) {
            try {
                GitHubReleaseClient(config, Scripted(HttpResponse(status, "", mapOf("Retry-After" to "60")))) { 1000 }.fetch()
                fail()
            } catch (error: GitHubException) {
                assertEquals(GoldyError.Http(status, if (status == 403 || status == 429) 61000 else null), error.error)
            }
        }
        try { GitHubReleaseClient(config, HttpTransport { throw IOException() }) { 0 }.fetch(); fail() }
        catch (error: GitHubException) { assertEquals(GoldyError.Network, error.error) }
    }

    @Test fun responseBoundsAndUnsolicited304AreRejected() = runTest {
        for (transport in listOf(Scripted(HttpResponse(200, response("v1.0"))), Scripted(HttpResponse(304, "")))) {
            try { GitHubReleaseClient(config.copy(maxResponseBytes = 8), transport) { 0 }.fetch(); fail() }
            catch (error: GitHubException) { assertEquals(GoldyError.InvalidResponse, error.error) }
        }
    }

    @Test fun cancellationIsNotConvertedIntoNetworkFailure() = runTest {
        try {
            GitHubReleaseClient(config, HttpTransport { throw kotlinx.coroutines.CancellationException("cancel") }) { 0 }.fetch()
            fail()
        } catch (_: kotlinx.coroutines.CancellationException) { }
    }

    @Test fun repositoryUrlParsingRejectsLookalikesAndTraversal() {
        assertEquals(testRepository, GitHubRepository.fromUrl("https://github.com/owner/app/releases"))
        for (url in listOf("https://github.com.evil.example/owner/app", "https://evil/github.com/owner/app",
            "http://github.com/owner/app", "https://user@github.com/owner/app",
            "https://github.com/owner/app/../other", "https://github.com/owner/app?token=x",
            "https://github.com/owner/app/releases/download/%2e%2e/x.apk")) {
            assertThrows(IllegalArgumentException::class.java) { GitHubRepository.fromUrl(url) }
        }
    }
}
