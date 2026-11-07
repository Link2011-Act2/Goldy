package jp.linkserver.goldy

import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResumableTransferTest {
    @Test fun separateDownloaderAppendsPersistedBytesAndHashesTheWholeFile() = runBlocking {
        val dir = Files.createTempDirectory("goldy-resume-integration").toFile()
        val bytes = "complete APK bytes for disk resume".toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        val repo = GitHubRepository("owner", "app")
        val asset = GitHubAsset(12, "app.apk", repo.url + "/releases/download/2.0/app.apk", bytes.size.toLong(), "sha256:$hash")
        val release = GitHubRelease(11, "2.0", url = repo.url + "/releases/tag/2.0", assets = listOf(asset))
        val metadata = ApkMetadata("example.app", "2.0", 2, ApkSigners(setOf("a".repeat(64)), false))
        var inspections = 0
        val inspector = object : ApkInspector {
            override fun installed() = metadata.copy(versionCode = 1)
            override fun inspect(file: File): ApkMetadata { inspections++; assertArrayEquals(bytes, file.readBytes()); return metadata }
        }
        val offsets = mutableListOf<Long>()
        val transport = object : RangeAssetTransport {
            var first = true
            override fun open(url: String, userAgent: String) = openRange(url, userAgent, 0, null)
            override fun openRange(url: String, userAgent: String, offset: Long, validator: String?): AssetConnection {
                offsets.add(offset)
                val fail = first.also { first = false }
                if (!fail) { assertEquals(7L, offset); assertEquals("\"one\"", validator) }
                val input = if (fail) object : InputStream() {
                    var position = 0
                    override fun read(): Int { if (position == 7) throw IOException("link lost"); return bytes[position++].toInt() and 255 }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (position == 7) throw IOException("link lost")
                        val n = minOf(len, 7-position); bytes.copyInto(b, off, position, position+n); position += n; return n
                    }
                } else ByteArrayInputStream(bytes, 7, bytes.size-7)
                return object : AssetConnection {
                    override val status = if (fail) 200 else 206
                    override fun header(name: String): String? = when (name) {
                        "ETag" -> "\"one\""
                        "Content-Length" -> (if (fail) bytes.size else bytes.size-7).toString()
                        "Content-Range" -> if (fail) null else "bytes 7-${bytes.size-1}/${bytes.size}"
                        else -> null
                    }
                    override fun input() = input
                    override fun close() = input.close()
                }
            }
        }
        val config = ApkDownloadConfig(repo, maxAttempts = 1)
        try {
            try { ApkDownloader(config, dir, inspector, transport).downloadResumable(release); fail("Disconnected transfer") }
            catch (expected: ApkException) { assertEquals(ApkFailure.Network, expected.reason) }
            assertEquals(0, inspections)
            val second = ApkDownloader(config, dir, inspector, transport)
            assertEquals(7L, second.savedProgress(release)!!.bytes)
            val progress = mutableListOf<ApkProgress>()
            val result = second.downloadResumable(release, progress::add)
            assertEquals(listOf(0L, 7L), offsets)
            assertEquals(7L, progress.first().bytes)
            assertEquals(hash, result.sha256); assertArrayEquals(bytes, result.file.readBytes())
            assertEquals(1, inspections); assertNull(second.savedProgress(release))
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
}
