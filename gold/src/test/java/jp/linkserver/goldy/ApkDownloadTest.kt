package jp.linkserver.goldy

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ApkDownloadTest {
    private val content = "test apk contents".toByteArray()
    private val signer = ApkSigners(setOf("a".repeat(64)), false)
    private val metadata = ApkMetadata("example.app", "2.0", 2, signer)
    private val inspector = object : ApkInspector {
        override fun installed() = metadata.copy(versionCode = 1)
        override fun inspect(file: File) = metadata
    }
    private fun asset(size: Long? = content.size.toLong(), digest: String? = "sha256:" + sha256(content)) =
        GitHubAsset(1, "app.apk", testRepository.url + "/releases/download/2.0/app.apk", size, digest)
    private fun release(asset: GitHubAsset = asset()) = jp.linkserver.goldy.release("2.0").copy(assets = listOf(asset))
    private class Response(val code: Int = 200, val headers: Map<String, String> = emptyMap(),
                           val stream: InputStream = ByteArrayInputStream(byteArrayOf())) : AssetConnection {
        var closed = false
        override val status get() = code
        override fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        override fun input() = stream
        override fun close() { closed = true; stream.close() }
    }
    private fun response(bytes: ByteArray = content, length: String? = bytes.size.toString()) =
        Response(headers = length?.let { mapOf("Content-Length" to it) } ?: emptyMap(), stream = ByteArrayInputStream(bytes))
    private fun downloader(dir: File, transport: AssetTransport, attempts: Int = 1, max: Long = 1000, inspector: ApkInspector = this.inspector) =
        ApkDownloader(ApkDownloadConfig(testRepository, maxBytes = max, maxAttempts = attempts), dir, inspector, transport)
    private fun failure(reason: ApkFailure, block: suspend () -> Unit) = runBlocking {
        try { block(); fail("Expected " + reason) } catch (e: ApkException) { assertEquals(reason, e.reason) }
    }
    private fun temp() = Files.createTempDirectory("goldy-test").toFile()
    private fun assertEmpty(dir: File) { assertTrue(dir.listFiles().isNullOrEmpty()); dir.delete() }

    @Test fun successDigestAndProgress() = runBlocking {
        val dir = temp(); val response = response(); val events = mutableListOf<ApkProgress>()
        val result = downloader(dir, AssetTransport { _, _ -> response }).download(release(), events::add)
        assertEquals(sha256(content), result.sha256); assertArrayEquals(content, result.file.readBytes())
        assertEquals(0L, events.first().bytes); assertEquals(content.size.toLong(), events.last().bytes)
        assertEquals(content.size.toLong(), events.last().total); assertTrue(response.closed)
        result.file.delete(); assertEmpty(dir)
    }
    @Test fun unknownLengthAndUnknownAssetSize() = runBlocking {
        val dir = temp(); val events = mutableListOf<ApkProgress>()
        val result = downloader(dir, AssetTransport { _, _ -> response(length = null) }).download(release(asset(null)), events::add)
        assertTrue(events.all { it.total == null }); result.file.delete(); assertEmpty(dir)
    }
    @Test fun assetSizeSuppliesProgressWhenLengthUnknown() = runBlocking {
        val dir = temp(); val events = mutableListOf<ApkProgress>()
        val result = downloader(dir, AssetTransport { _, _ -> response(length = null) }).download(release(), events::add)
        assertEquals(content.size.toLong(), events.last().total); result.file.delete(); assertEmpty(dir)
    }
    @Test fun digestMismatchRemovesPart() {
        val dir = temp(); failure(ApkFailure.DigestMismatch) {
            downloader(dir, AssetTransport { _, _ -> response() }).download(release(asset(digest = "sha256:" + "0".repeat(64))))
        }; assertEmpty(dir)
    }
    @Test fun unsupportedDigestRejectedBeforeNetwork() {
        val dir = temp(); failure(ApkFailure.InvalidDigest) {
            downloader(dir, AssetTransport { _, _ -> error("network") }).download(release(asset(digest = "md5:123")))
        }; assertEmpty(dir)
    }
    @Test fun contentLengthMismatch() {
        val dir = temp(); failure(ApkFailure.SizeMismatch) {
            downloader(dir, AssetTransport { _, _ -> response(length = "100") }).download(release())
        }; assertEmpty(dir)
    }
    @Test fun shortBodyRejectedAndDeleted() {
        val dir = temp(); failure(ApkFailure.SizeMismatch) {
            downloader(dir, AssetTransport { _, _ -> response(content.copyOf(3), content.size.toString()) }).download(release())
        }; assertEmpty(dir)
    }
    @Test fun assetSizeCheckedWithoutLength() {
        val dir = temp(); failure(ApkFailure.SizeMismatch) {
            downloader(dir, AssetTransport { _, _ -> response(length = null) }).download(release(asset(100)))
        }; assertEmpty(dir)
    }
    @Test fun streamingLimitWithoutAnySize() {
        val dir = temp(); failure(ApkFailure.TooLarge) {
            downloader(dir, AssetTransport { _, _ -> response(length = null) }, max = 4).download(release(asset(null)))
        }; assertEmpty(dir)
    }
    @Test fun metadataLimitBeforeNetwork() {
        val dir = temp(); failure(ApkFailure.TooLarge) {
            downloader(dir, AssetTransport { _, _ -> error("network") }, max = 4).download(release())
        }; assertEmpty(dir)
    }
    @Test fun headerLimitBeforeBody() {
        val dir = temp(); failure(ApkFailure.TooLarge) {
            downloader(dir, AssetTransport { _, _ -> response(length = "1001") }).download(release(asset(null)))
        }; assertEmpty(dir)
    }
    @Test fun githubCdnRedirectAccepted() = runBlocking {
        val dir = temp(); val calls = mutableListOf<String>(); val redirect = Response(302, mapOf("Location" to "https://release-assets.githubusercontent.com/github-production-release-asset/123/file?sig=x%2Fy"))
        val result = downloader(dir, AssetTransport { url, _ -> calls.add(url); if (calls.size == 1) redirect else response() }).download(release())
        assertEquals(2, calls.size); assertTrue(redirect.closed); result.file.delete(); assertEmpty(dir)
    }
    @Test fun unsafeRedirectsNeverContacted() {
        for (url in listOf("http://release-assets.githubusercontent.com/a", "https://evil.example/a", "https://release-assets.githubusercontent.com.evil.example/a", "https://user@release-assets.githubusercontent.com/a", "https://release-assets.githubusercontent.com:444/a", "https://release-assets.githubusercontent.com/%2e%2e/a", "https://127.0.0.1/a")) {
            val dir = temp(); var calls = 0
            failure(ApkFailure.InvalidUrl) { downloader(dir, AssetTransport { _, _ -> calls++; Response(302, mapOf("Location" to url)) }).download(release()) }
            assertEquals(1, calls); assertEmpty(dir)
        }
    }
    @Test fun redirectLoopBounded() {
        val dir = temp(); var calls = 0
        failure(ApkFailure.RedirectLimit) { downloader(dir, AssetTransport { _, _ -> calls++; Response(302, mapOf("Location" to "https://objects.githubusercontent.com/a")) }).download(release()) }
        assertEquals(6, calls); assertEmpty(dir)
    }
    @Test fun wrongRepositoryOrTagRejected() {
        for (url in listOf("https://github.com/other/app/releases/download/2.0/app.apk", testRepository.url + "/releases/download/1.0/app.apk")) {
            val dir = temp(); failure(ApkFailure.InvalidUrl) { downloader(dir, AssetTransport { _, _ -> error("network") }).download(release(asset().copy(downloadUrl = url))) }; assertEmpty(dir)
        }
    }
    @Test fun httpRetriesOnlyTransientStatus() = runBlocking {
        for (status in listOf(503, 404, 429)) {
            val dir = temp(); var calls = 0
            try {
                val result = downloader(dir, AssetTransport { _, _ -> calls++; if (calls == 1) Response(status) else response() }, attempts = 2).download(release())
                assertEquals(503, status); result.file.delete()
            } catch (e: ApkException) { assertTrue(status != 503); assertEquals(status, e.status) }
            assertEquals(if (status == 503) 2 else 1, calls); assertEmpty(dir)
        }
    }
    @Test fun interruptedStreamRetriesFromZero() = runBlocking {
        val dir = temp(); var calls = 0; val events = mutableListOf<ApkProgress>()
        val broken = object : InputStream() { var read = 0; override fun read(): Int = if (read++ < 3) 1 else throw IOException("offline") }
        val result = downloader(dir, AssetTransport { _, _ -> calls++; if (calls == 1) Response(stream = broken) else response() }, attempts = 2).download(release(), events::add)
        assertEquals(2, calls); assertEquals(0L, events.first { it.attempt == 2 }.bytes)
        assertArrayEquals(content, result.file.readBytes()); result.file.delete(); assertEmpty(dir)
    }
    @Test fun cancellationDisconnectsBlockingReadAndCleansPart() = runBlocking {
        val dir = temp(); val entered = CountDownLatch(1); val closed = CountDownLatch(1)
        val stream = object : InputStream() {
            override fun read(): Int { entered.countDown(); if (!closed.await(10, TimeUnit.SECONDS)) error("disconnect missing"); throw IOException("closed") }
            override fun close() { closed.countDown() }
        }
        val job = launch(Dispatchers.Default) { downloader(dir, AssetTransport { _, _ -> Response(stream = stream) }).download(release()) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        withTimeout(3000) { job.cancelAndJoin() }; assertEquals(0, closed.count); assertEmpty(dir)
    }
    @Test fun globalGateSerializesInstancesAndWaitingCanCancel() = runBlocking {
        val firstDir = temp(); val secondDir = temp(); val entered = CountDownLatch(1); val closed = CountDownLatch(1); val secondCalls = AtomicInteger()
        val stream = object : InputStream() {
            override fun read(): Int { entered.countDown(); closed.await(10, TimeUnit.SECONDS); throw IOException() }
            override fun close() { closed.countDown() }
        }
        val first = launch(Dispatchers.Default) { downloader(firstDir, AssetTransport { _, _ -> Response(stream = stream) }).download(release()) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        val second = launch { downloader(secondDir, AssetTransport { _, _ -> secondCalls.incrementAndGet(); response() }).download(release()) }
        delay(100); second.cancelAndJoin(); assertEquals(0, secondCalls.get())
        first.cancelAndJoin(); assertEmpty(firstDir); assertEmpty(secondDir)
    }
    @Test fun abandonedPartsCleanedButUnrelatedFilesPreserved() = runBlocking {
        val dir = temp(); val part = File(dir, "goldy-00000000-0000-0000-0000-000000000000.part").apply { writeText("old") }
        val other = File(dir, "host.part").apply { writeText("keep") }
        downloader(dir, AssetTransport { _, _ -> error("network") }).cleanPartialFiles()
        assertFalse(part.exists()); assertTrue(other.exists()); other.delete(); assertEmpty(dir)
    }
    @Test fun archiveRejectionCleansPart() {
        val dir = temp(); val invalid = object : ApkInspector {
            override fun installed() = metadata
            override fun inspect(file: File): ApkMetadata? = null
        }
        failure(ApkFailure.InvalidApk) { downloader(dir, AssetTransport { _, _ -> response() }, inspector = invalid).download(release()) }; assertEmpty(dir)
    }
    @Test fun openingNetworkFailureRetriesAndStopsAtConfiguredLimit() {
        val dir = temp(); var calls = 0
        failure(ApkFailure.Network) { downloader(dir, AssetTransport { _, _ -> calls++; throw IOException("offline") }, attempts = 2).download(release()) }
        assertEquals(2, calls); assertEmpty(dir)
    }
    @Test fun cancellationDuringVerificationLeavesNoFile() = runBlocking {
        val dir = temp(); val entered = CountDownLatch(1); val resume = CountDownLatch(1)
        val blocking = object : ApkInspector {
            override fun installed() = metadata
            override fun inspect(file: File): ApkMetadata { entered.countDown(); resume.await(5, TimeUnit.SECONDS); return metadata }
        }
        val job = launch(Dispatchers.Default) { downloader(dir, AssetTransport { _, _ -> response() }, inspector = blocking).download(release()) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        job.cancel(); resume.countDown(); withTimeout(3000) { job.join() }; assertEmpty(dir)
    }
    @Test fun storageFailureIsNotRetried() {
        val dir = temp(); val notDirectory = File(dir, "file").apply { writeText("host") }; var calls = 0
        failure(ApkFailure.Storage) { downloader(notDirectory, AssetTransport { _, _ -> calls++; response() }, attempts = 3).download(release()) }
        assertEquals(0, calls); assertEquals("host", notDirectory.readText()); notDirectory.delete(); assertEmpty(dir)
    }

    @Test fun repositoryCaseIsInsensitiveButTagAndAssetIdentityAreExact() = runBlocking {
        val dir = temp(); val base = release(); val asset = asset().copy(downloadUrl = asset().downloadUrl.replace("/owner/app/", "/OWNER/APP/"))
        val result = downloader(dir, AssetTransport { _, _ -> response() }).download(base.copy(url = base.url.replace("/owner/app/", "/OWNER/APP/"), assets = listOf(asset)))
        result.file.delete(); assertEmpty(dir)
    }

    @Test fun prematureEofRetriesFromZeroAndStillChecksSizeAndDigest() = runBlocking {
        val dir = temp(); var calls = 0
        val result = downloader(dir, AssetTransport { _, _ -> calls++; if (calls == 1) response(content.copyOf(3), content.size.toString()) else response() }, attempts = 2).download(release())
        assertEquals(2, calls); assertArrayEquals(content, result.file.readBytes()); result.file.delete(); assertEmpty(dir)
    }

}
