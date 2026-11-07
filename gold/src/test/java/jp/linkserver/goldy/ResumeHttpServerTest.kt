package jp.linkserver.goldy

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real TCP/HTTP and persisted files. Only the APK inspector is a JVM fixture. */
class ResumeHttpServerTest {
    private class Harness(val mode: String = "206") : AutoCloseable {
        val bytes = "Full HTTP body must survive a disconnected first execution".toByteArray()
        val dir = Files.createTempDirectory("goldy-http-resume").toFile()
        val requests = CopyOnWriteArrayList<Pair<String?, String?>>()
        val redirectRequests = CopyOnWriteArrayList<Triple<String,String?,String?>>()
        private var token = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable).apply { isDaemon = true } }
        val repo = GitHubRepository("owner", "app")
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        val asset = GitHubAsset(2, "app.apk", repo.url+"/releases/download/2.0/app.apk", if(mode == "unknown-size") null else bytes.size.toLong(), "sha256:$hash")
        val release = GitHubRelease(1, "2.0", url=repo.url+"/releases/tag/2.0", assets=listOf(asset))
        var inspected = 0
        val metadata = ApkMetadata("example.app", "2.0", 2, ApkSigners(setOf("a".repeat(64)), false))
        val inspector = object : ApkInspector {
            override fun installed() = metadata.copy(versionCode=1)
            override fun inspect(file: File): ApkMetadata { inspected++; assertArrayEquals(bytes, file.readBytes()); return metadata }
        }
        init {
            server.executor = executor
            server.createContext("/") { exchange ->
                val range = exchange.requestHeaders.getFirst("Range")
                if(mode.startsWith("redirect")) {
                    redirectRequests.add(Triple(exchange.requestURI.path,range,exchange.requestHeaders.getFirst("If-Range")))
                    if(exchange.requestURI.path == "/canonical") {
                        exchange.responseHeaders.set("Location","https://release-assets.githubusercontent.com/token-${++token}")
                        exchange.sendResponseHeaders(302,-1); exchange.close(); return@createContext
                    }
                    if(mode == "redirect-expired" && exchange.requestURI.path == "/token-2") {
                        exchange.sendResponseHeaders(403,-1); exchange.close(); return@createContext
                    }
                    if(mode == "redirect-denied" && token >= 2) {
                        exchange.sendResponseHeaders(403,-1); exchange.close(); return@createContext
                    }
                }
                requests.add(range to exchange.requestHeaders.getFirst("If-Range"))
                val first = requests.size == 1
                if(mode == "date") exchange.responseHeaders.set("Last-Modified", "Sun, 06 Nov 1994 08:49:37 GMT")
                else exchange.responseHeaders.set("ETag", if (mode == "weak") "W/\"one\"" else if (mode == "etag-change" && !first) "\"two\"" else "\"one\"")
                try {
                    if (!first && range != null && mode == "416") {
                        exchange.responseHeaders.set("Content-Range", "bytes */${bytes.size}")
                        exchange.sendResponseHeaders(416, -1)
                    } else if (!first && range != null && mode !in setOf("200", "weak")) {
                        val offset = range.removePrefix("bytes=").removeSuffix("-").toInt()
                        exchange.responseHeaders.set("Content-Range", when(mode) {
                            "bad-start" -> "bytes ${offset-1}-${bytes.size-1}/${bytes.size}"
                            "bad-range" -> "bytes $offset-${bytes.size-1}/*"
                            "bad-total" -> "bytes $offset-${bytes.size-1}/${bytes.size+1}"
                            else -> "bytes $offset-${bytes.size-1}/${bytes.size}"
                        })
                        if (mode == "encoding") exchange.responseHeaders.set("Content-Encoding", "gzip")
                        val length = bytes.size-offset
                        exchange.sendResponseHeaders(206, (if(mode == "bad-length") length-1 else length).toLong())
                        val body = bytes.copyOf()
                        if(mode == "corrupt-tail") body[body.lastIndex] = (body.last().toInt() xor 1).toByte()
                        exchange.responseBody.write(body, offset, if(mode == "bad-length") length-1 else length)
                    } else {
                        exchange.sendResponseHeaders(200, bytes.size.toLong())
                        exchange.responseBody.write(bytes, 0, if(first) 7 else bytes.size)
                        if(first && mode in setOf("timeout","cancel")) { exchange.responseBody.flush(); Thread.sleep(500) }
                    }
                } finally { runCatching { exchange.close() } }
            }
            server.start()
        }
        // Production transport sends real Range/If-Range. Only the test endpoint maps to loopback.
        val transport = object : RangeAssetTransport {
            override fun open(url: String, userAgent: String) = openRange(url, userAgent, 0, null)
            override fun openRange(url: String, userAgent: String, offset: Long, validator: String?): AssetConnection {
                val path = if(mode.startsWith("redirect")) { if(url == asset.downloadUrl) "/canonical" else java.net.URI(url).path } else "/"
                val endpoint = "http://127.0.0.1:${server.address.port}$path"
                if(mode != "timeout") return UrlAssetTransport.openRange(endpoint,userAgent,offset,validator)
                // Short test timeout on a real HTTP socket; production timeout remains 30 seconds.
                val connection = java.net.URI(endpoint).toURL().openConnection() as java.net.HttpURLConnection
                connection.connectTimeout=1000; connection.readTimeout=100
                if(offset > 0) { connection.setRequestProperty("Range","bytes=$offset-"); connection.setRequestProperty("If-Range",validator) }
                return object : AssetConnection {
                    override val status get() = connection.responseCode
                    override fun header(name: String) = connection.getHeaderField(name)
                    override fun input() = connection.inputStream
                    override fun close() = connection.disconnect()
                }
            }
        }
        fun downloader() = ApkDownloader(ApkDownloadConfig(repo, maxAttempts=1), dir, inspector, transport)
        suspend fun interrupt() {
            try { downloader().downloadResumable(release); fail("first response must be incomplete") }
            catch(error: ApkException) { assertEquals(if(mode == "timeout") ApkFailure.Network else ApkFailure.SizeMismatch, error.reason) }
            assertEquals(0, inspected)
        }
        override fun close() { server.stop(0); executor.shutdownNow(); dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }

    @Test fun realHttp206ResumesAcrossInstancesAndVerifiesWholeFile() = runBlocking {
        Harness().use { h ->
            h.interrupt()
            assertEquals(7L, h.downloader().savedProgress(h.release)!!.bytes)
            val events = mutableListOf<ApkProgress>()
            val result = h.downloader().downloadResumable(h.release, events::add)
            assertEquals("bytes=7-" to "\"one\"", h.requests[1])
            assertEquals(7L, events.first().bytes); assertEquals(h.hash, result.sha256)
            assertEquals(1, h.inspected)
        }
    }
    @Test fun ignoredRange200RestartsWithoutAppending() = runBlocking {
        Harness("200").use { h ->
            h.interrupt(); val events = mutableListOf<ApkProgress>()
            assertEquals(h.hash, h.downloader().downloadResumable(h.release, events::add).sha256)
            assertEquals(0L, events.first().bytes)
        }
    }
    @Test fun responseTotalPersistsWhenAssetSizeIsUnknown() = runBlocking {
        Harness("unknown-size").use { h ->
            h.interrupt()
            assertEquals(h.bytes.size.toLong(), h.downloader().savedProgress(h.release)!!.total)
            assertEquals(h.hash, h.downloader().downloadResumable(h.release).sha256)
        }
    }
    @Test fun unsatisfiedRangeRestartsFromCanonicalRequest() = runBlocking {
        Harness("416").use { h ->
            h.interrupt(); assertEquals(h.hash, h.downloader().downloadResumable(h.release).sha256)
            assertEquals(listOf(null, "bytes=7-", null), h.requests.map { it.first })
        }
    }
    @Test fun weakEtagNeverSendsIfRange() = runBlocking {
        Harness("weak").use { h ->
            h.interrupt(); assertNull(h.downloader().savedProgress(h.release))
            assertEquals(h.hash, h.downloader().downloadResumable(h.release).sha256)
            assertTrue(h.requests.all { it.first == null && it.second == null })
        }
    }
    @Test fun strongLastModifiedCanResumeWithoutAnEtag() = runBlocking {
        Harness("date").use { h ->
            h.interrupt(); assertEquals(h.hash, h.downloader().downloadResumable(h.release).sha256)
            assertEquals("bytes=7-" to "Sun, 06 Nov 1994 08:49:37 GMT", h.requests[1])
        }
    }
    @Test fun differentAssetIdNeverReusesThePreviousPartial() = runBlocking {
        Harness().use { h ->
            h.interrupt()
            val different = h.release.copy(assets = listOf(h.asset.copy(id = 999)))
            assertEquals(h.hash, h.downloader().downloadResumable(different).sha256)
            assertNull(h.requests[1].first)
        }
    }
    @Test fun contradictory206ResponsesNeverReachInspector() = runBlocking {
        for (mode in listOf("bad-start", "bad-range", "bad-total", "bad-length", "etag-change", "encoding")) {
            Harness(mode).use { h ->
                h.interrupt()
                try { h.downloader().downloadResumable(h.release); fail(mode) }
                catch(error: ApkException) { assertEquals(mode, ApkFailure.SizeMismatch, error.reason) }
                assertEquals(0, h.inspected)
                assertNull(h.downloader().savedProgress(h.release))
            }
        }
    }
    @Test fun alteredPartialAndBrokenMetadataAreDiscarded() = runBlocking {
        for (metadata in listOf(false, true)) Harness().use { h ->
            h.interrupt()
            if(metadata) h.dir.listFiles()!!.single { it.extension == "meta" }.writeText("broken")
            else h.dir.listFiles()!!.single { it.extension == "part" }.writeBytes(ByteArray(7))
            assertEquals(h.hash, h.downloader().downloadResumable(h.release).sha256)
            assertNull(h.requests[1].first)
        }
    }
    @Test fun resumedDigestMismatchNeverReachesApkInspector() = runBlocking {
        Harness("corrupt-tail").use { h ->
            h.interrupt()
            try { h.downloader().download(h.release); fail("Changed whole-file digest") }
            catch(error: ApkException) { assertEquals(ApkFailure.DigestMismatch, error.reason) }
            assertEquals("bytes=7-", h.requests[1].first)
            assertEquals(0, h.inspected)
            assertNull(h.downloader().savedProgress(h.release))
        }
    }
    @Test fun explicitCancellationDiscardsOnlyTheSelectedCheckpoint() = runBlocking {
        Harness().use { h ->
            h.interrupt()
            val unrelated = File(h.dir,"caller-owned.apk").apply { writeText("lease outside checkpoint namespace") }
            h.downloader().discardPartial(h.release)
            assertNull(h.downloader().savedProgress(h.release))
            assertTrue(unrelated.exists())
            assertEquals(h.hash, h.downloader().download(h.release).sha256)
            assertNull(h.requests[1].first)
        }
    }
    @Test fun timeoutRetainsBytesForAnotherExecution() = runBlocking {
        Harness("timeout").use { h ->
            h.interrupt()
            assertEquals(7L,h.downloader().savedProgress(h.release)!!.bytes)
            assertEquals(h.hash,h.downloader().download(h.release).sha256)
            assertEquals("bytes=7-",h.requests[1].first)
        }
    }
    @Test fun redirectsForwardRangeAndAlwaysRefreshCanonicalSignedUrl() = runBlocking {
        Harness("redirect").use { h ->
            h.interrupt()
            assertEquals(h.hash,h.downloader().download(h.release).sha256)
            assertEquals(listOf("/canonical","/token-1","/canonical","/token-2"),h.redirectRequests.map { it.first })
            assertEquals(listOf(null,null,"bytes=7-","bytes=7-"),h.redirectRequests.map { it.second })
            assertEquals(listOf(null,null,"\"one\"","\"one\""),h.redirectRequests.map { it.third })
            assertEquals(1,h.inspected)
        }
    }
    @Test fun storageFailureRetainsCheckpointWithoutRetryingOrPublishingReceipt() = runBlocking {
        Harness("416").use { h ->
            h.interrupt()
            val blocked = File(h.dir,"blocked-completion-directory").apply { writeText("not a directory") }
            try {
                ResumableTransfer(ApkDownloadConfig(h.repo,maxAttempts=3),h.dir,h.inspector,h.transport,blocked)
                    .download(h.release,h.asset) {}
                fail("A completed file cannot be published to the blocked directory")
            } catch(error: ApkException) { assertEquals(ApkFailure.Storage,error.reason) }
            assertEquals(3,h.requests.size) // initial disconnect, 416, one full response; no storage retry
            assertEquals(h.bytes.size.toLong(),h.downloader().savedProgress(h.release)!!.bytes)
            assertEquals(0,h.dir.listFiles()!!.count { it.extension == "apk" })
            blocked.delete()
            assertEquals(h.hash,h.downloader().download(h.release).sha256)
        }
    }
    @Test fun coroutineInterruptionRetainsBytesUntilExplicitDiscard() = runBlocking {
        Harness("cancel").use { h ->
            val started = CompletableDeferred<Unit>()
            val job = launch(Dispatchers.IO) { h.downloader().download(h.release) { if(it.bytes > 0) started.complete(Unit) } }
            withTimeout(2000) { started.await(); job.cancelAndJoin() }
            assertEquals(7L,h.downloader().savedProgress(h.release)!!.bytes)
            assertEquals(h.hash,h.downloader().download(h.release).sha256)
            assertEquals("bytes=7-",h.requests[1].first)
        }
    }
    @Test fun connectionTimeoutDoesNotEraseThePreviousExecutionCheckpoint() = runBlocking {
        Harness().use { h ->
            h.interrupt()
            val unavailable = object : RangeAssetTransport {
                override fun open(url: String,userAgent: String): AssetConnection = error("Range entry expected")
                override fun openRange(url: String,userAgent: String,offset: Long,validator: String?): AssetConnection {
                    assertEquals(7L,offset); throw java.net.SocketTimeoutException("connect timed out")
                }
            }
            try { ApkDownloader(ApkDownloadConfig(h.repo,maxAttempts=1),h.dir,h.inspector,unavailable).download(h.release); fail("Timeout") }
            catch(error: ApkException) { assertEquals(ApkFailure.Network,error.reason) }
            assertEquals(7L,h.downloader().savedProgress(h.release)!!.bytes)
            assertEquals(h.hash,h.downloader().download(h.release).sha256)
            assertEquals("bytes=7-",h.requests[1].first)
        }
    }
    @Test fun expiredSignedCdnUrlRefreshesCanonicalWithoutLosingOffset() = runBlocking {
        Harness("redirect-expired").use { h ->
            h.interrupt()
            assertEquals(h.hash,h.downloader().download(h.release).sha256)
            assertEquals(listOf("/canonical","/token-1","/canonical","/token-2","/canonical","/token-3"),h.redirectRequests.map { it.first })
            assertEquals(listOf(null,null,"bytes=7-","bytes=7-","bytes=7-","bytes=7-"),h.redirectRequests.map { it.second })
            assertEquals(1,h.inspected)
        }
    }
    @Test fun persistentCdnDenialStopsAfterOneRefreshAndRetainsUnverifiedPrefix() = runBlocking {
        Harness("redirect-denied").use { h ->
            h.interrupt()
            try { h.downloader().download(h.release); fail("CDN denial") }
            catch(error: ApkException) { assertEquals(ApkFailure.Http,error.reason); assertEquals(403,error.status) }
            assertEquals(6,h.redirectRequests.size)
            assertEquals(7L,h.downloader().savedProgress(h.release)!!.bytes)
            assertEquals(0,h.inspected)
            assertTrue(h.dir.listFiles()!!.none { it.extension == "apk" })
        }
    }
}
