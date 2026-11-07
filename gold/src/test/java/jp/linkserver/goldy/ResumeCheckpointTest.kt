package jp.linkserver.goldy

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ResumeCheckpointTest {
    private val identity = ResumeCheckpoint.Identity("owner", "app", 10, "v2.0", 11, "app.apk",
        "https://github.com/owner/app/releases/download/v2.0/app.apk", 30, "sha256:" + "a".repeat(64))
    private val validator = ResumeHttp.Validator("ETag", "\"version-1\"")
    @Test fun crashTailIsTruncatedOnlyAfterValidatingCommittedPrefix() {
        val dir = Files.createTempDirectory("goldy-crash-tail").toFile()
        try {
            val store = ResumeCheckpoint(dir, 100)
            val file = store.file(identity)
            file.writeBytes(ByteArray(15) { it.toByte() }); store.save(identity, validator)
            file.appendBytes(byteArrayOf(9, 8, 7))
            assertEquals(15L, store.load(identity, recoverTail = true)!!.bytes)
            assertEquals(15L, file.length())
            file.appendBytes(byteArrayOf(1))
            java.io.RandomAccessFile(file, "rw").use { it.write(99) }
            assertNull(store.load(identity, recoverTail = true))
            assertEquals(16L, file.length()) // A corrupt prefix is never blessed by truncation.
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun checkpointSurvivesNewInstanceAndRejectsChangedBytesAndAsset() {
        val dir = Files.createTempDirectory("goldy-checkpoint").toFile()
        try {
            val first = ResumeCheckpoint(dir, 100)
            first.file(identity).writeBytes(ByteArray(15) { it.toByte() })
            first.save(identity, validator)
            val restored = ResumeCheckpoint(dir, 100).load(identity)!!
            assertEquals(15L, restored.bytes); assertEquals(validator, restored.validator)
            assertNull(first.load(identity.copy(assetId = 12)))
            assertNull(first.load(identity.copy(digest = "sha256:" + "b".repeat(64))))
            restored.file.writeBytes(ByteArray(15) { 0 })
            assertNull(first.load(identity))
            first.discard(identity)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
    @Test fun malformedMetadataAndSizeMismatchAreRejected() {
        val dir = Files.createTempDirectory("goldy-checkpoint").toFile()
        try {
            val store = ResumeCheckpoint(dir, 100)
            val file = store.file(identity)
            file.writeBytes(ByteArray(15)); store.save(identity, validator)
            file.appendBytes(byteArrayOf(1)); assertNull(store.load(identity))
            file.writeBytes(ByteArray(15)); store.save(identity, validator)
            dir.listFiles()!!.single { it.extension == "meta" }.writeText("schema=broken")
            assertNull(store.load(identity))
            store.discard(identity)
        } finally { dir.listFiles()?.forEach { it.delete() }; dir.delete() }
    }
}
