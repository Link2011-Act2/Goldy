package jp.linkserver.goldy

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ApkRevalidationTest {
    private val bytes = "signed test fixture".toByteArray()
    private val signer = ApkSigners(setOf("a".repeat(64)), false)
    private val metadata = ApkMetadata("example.app", "2.0", 2, signer)
    private fun inspector(current: ApkMetadata = metadata, archive: ApkMetadata? = metadata) = object : ApkInspector {
        override fun installed() = current
        override fun inspect(file: File) = archive
    }
    private fun file() = Files.createTempFile("goldy-revalidation", ".apk").toFile().apply { writeBytes(bytes) }
    private fun verify(file: File, inspector: ApkInspector = inspector()) =
        ApkRevalidator.verify(file, metadata, sha256(bytes), bytes.size.toLong(), inspector)
    private fun installFailure(reason: InstallFailure, action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (error: InstallOperationException) { assertEquals(reason, error.reason) }
    }
    private fun apkFailure(reason: ApkFailure, action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (error: ApkException) { assertEquals(reason, error.reason) }
    }
    @Test fun authenticUnchangedArchiveAccepted() { val f = file(); try { assertEquals(metadata, verify(f)) } finally { f.delete() } }
    @Test fun deletedFileRejected() { val f = file(); f.delete(); installFailure(InstallFailure.FileMissing) { verify(f) } }
    @Test fun sameSizeMutationRejected() { val f = file(); try { f.writeBytes(ByteArray(bytes.size)); installFailure(InstallFailure.FileChanged) { verify(f) } } finally { f.delete() } }
    @Test fun truncatedFileRejected() { val f = file(); try { f.writeBytes(bytes.copyOf(3)); installFailure(InstallFailure.FileChanged) { verify(f) } } finally { f.delete() } }
    @Test fun currentInstalledSignatureIsReadAgain() { val f = file(); try { apkFailure(ApkFailure.SignerMismatch) { verify(f, inspector(metadata.copy(signers = ApkSigners(setOf("b".repeat(64)), false)))) } } finally { f.delete() } }
    @Test fun currentInstalledVersionCodeIsReadAgain() { val f = file(); try { apkFailure(ApkFailure.VersionCodeTooLow) { verify(f, inspector(metadata.copy(versionCode = 3))) } } finally { f.delete() } }
    @Test fun currentInstalledPackageIsReadAgain() { val f = file(); try { apkFailure(ApkFailure.PackageMismatch) { verify(f, inspector(metadata.copy(packageName = "other"))) } } finally { f.delete() } }
    @Test fun corruptArchiveRejected() { val f = file(); try { apkFailure(ApkFailure.InvalidApk) { verify(f, inspector(archive = null)) } } finally { f.delete() } }
    @Test fun archiveMetadataMustAlsoEqualVerifiedReceipt() { val f = file(); try { installFailure(InstallFailure.FileChanged) { verify(f, inspector(archive = metadata.copy(versionName = "2.0-Stable"))) } } finally { f.delete() } }
    @Test fun hashingHonorsCancellation() { val f = file(); try { val job = Job().apply { cancel() }; try { ApkRevalidator.digest(f, bytes.size.toLong(), job); fail("Expected cancellation") } catch (_: CancellationException) { } } finally { f.delete() } }
    @Test fun durableRecordRoundTripAndExplicitDeletion() {
        val dir = Files.createTempDirectory("goldy-install-store").toFile(); val store = InstallFiles(dir)
        try {
            store.initialize(); val id = "00000000-0000-0000-0000-000000000000"
            val record = InstallRecord(id, sha256(bytes), bytes.size.toLong(), metadata)
            store.apk(id).writeBytes(bytes); store.write(record)
            assertEquals(record, store.read(id)); assertEquals(listOf(record), InstallFiles(dir).all())
            store.delete(id); assertTrue(store.all().isEmpty()); assertFalse(store.apk(id).exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun pathTraversalNeverEscapesOwnedDirectory() {
        val dir = Files.createTempDirectory("goldy-install-store").toFile(); val store = InstallFiles(dir)
        try { for (id in listOf("../other", "abc", "00000000-0000-0000-0000-000000000000/../x")) {
            try { store.apk(id); fail("Unsafe id") } catch (_: IllegalArgumentException) { }
        } } finally { dir.deleteRecursively() }
    }
    @Test fun abandonedStagingIsCleanedWithoutExpiringLeases() {
        val dir = Files.createTempDirectory("goldy-install-store").toFile(); val store = InstallFiles(dir)
        try {
            store.initialize(); val id = "00000000-0000-0000-0000-000000000000"
            val record = InstallRecord(id, sha256(bytes), bytes.size.toLong(), metadata)
            store.apk(id).writeBytes(bytes); store.write(record)
            val partial = File(store.directory, id + ".part").apply { writeText("partial") }
            val unrelated = File(store.directory, "host.part").apply { writeText("keep") }
            store.initialize()
            assertFalse(partial.exists()); assertTrue(unrelated.exists())
            assertEquals(record, store.read(id)); assertTrue(store.apk(id).isFile)
        } finally { dir.deleteRecursively() }
    }

}
