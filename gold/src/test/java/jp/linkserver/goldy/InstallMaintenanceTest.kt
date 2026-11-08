package jp.linkserver.goldy

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstallMaintenanceTest {
    @get:Rule val temporary=TemporaryFolder()
    private val id="00000000-0000-0000-0000-000000000001"
    private val bytes="authenticated target bytes".toByteArray()
    private val signer=ApkSigners(setOf("a".repeat(64)),false)
    private val target=ApkMetadata("example.app","2.0-Release",2,signer)
    private fun store(): InstallFiles = InstallFiles(temporary.newFolder()).also { it.initialize() }
    private fun record(store: InstallFiles,metadata: ApkMetadata=target): InstallRecord =
        InstallRecord(id,sha256(bytes),bytes.size.toLong(),metadata).also {
            store.apk(id).writeBytes(bytes);store.write(it)
        }
    private fun snapshot(metadata: ApkMetadata=target,data: ByteArray=bytes,time: Long=20): InstalledApkSnapshot =
        InstalledApkSnapshot(metadata,temporary.newFile().apply { writeBytes(data) },time)
    private fun inspector(installed: ApkMetadata,archive: ApkMetadata=installed)=object: ApkInspector {
        override fun installed()=installed
        override fun inspect(file: File)=archive
    }
    @Test fun confirmedUpdateRemovesDuplicateButKeepsTheSameReadableArtifact() {
        val store=store(); val record=record(store)
        record.lifecycle=InstallLifecycle(launchedFromCode=1,launchedFromUpdateTime=10);store.write(record)
        val installed=snapshot(); val check=inspector(target)
        val result=InstallMaintenance(store,installed,check).run()
        assertEquals(setOf(id),result.installed);assertTrue(result.pending.isEmpty())
        assertFalse(store.apk(id).exists());assertEquals(0L,result.retainedBytes)
        val restored=InstallFiles(store.directory.parentFile!!.parentFile!!).read(id)
        assertEquals(InstallBacking.Installed,restored.lifecycle.backing)
        val source=resolveInstallSource(store,restored,installed)
        assertArrayEquals(bytes,source.readBytes())
        assertEquals(target,ApkRevalidator.verify(source,target,record.sha256,record.size,check))
    }
    @Test fun cancelledOrUnknownFutureUpdateIsRetainedWithoutAgeExpiry() {
        val store=store();record(store);store.apk(id).setLastModified(1)
        val old=target.copy(versionName="1.0-Release",versionCode=1)
        val result=InstallMaintenance(store,snapshot(old,"old APK".toByteArray()),inspector(old,target)).run()
        assertEquals(listOf(id),result.pending.map { it.id });assertTrue(result.installed.isEmpty())
        assertTrue(store.apk(id).isFile);assertArrayEquals(bytes,store.apk(id).readBytes())
    }
    @Test fun sameMetadataButDifferentInstalledBytesCannotReleaseTheCopy() {
        val store=store();record(store)
        val result=InstallMaintenance(store,snapshot(data=ByteArray(bytes.size)),inspector(target)).run()
        assertEquals(listOf(id),result.pending.map { it.id });assertTrue(store.apk(id).isFile)
        assertEquals(InstallBacking.Retained,store.read(id).lifecycle.backing)
    }
    @Test fun realSignatureInspectionIsStillRequiredForInstalledBacking() {
        val store=store();record(store)
        val wrong=target.copy(signers=ApkSigners(setOf("b".repeat(64)),false))
        val result=InstallMaintenance(store,snapshot(),inspector(target,wrong)).run()
        assertEquals(setOf(id),result.deferred);assertTrue(store.apk(id).exists())
        assertEquals(InstallBacking.Retained,store.read(id).lifecycle.backing)
    }
    @Test fun failedAtomicRecordCommitKeepsOriginalBytesAndPendingState() {
        val store=store();record(store)
        val result=InstallMaintenance(store,snapshot(),inspector(target)) { throw IOException("disk full") }.run()
        assertEquals(setOf(id),result.deferred);assertEquals(listOf(id),result.pending.map { it.id })
        assertTrue(store.apk(id).isFile);assertEquals(InstallBacking.Retained,store.read(id).lifecycle.backing)
    }
    @Test fun interruptedCleanupRestoresCommittedRedirectAndRetriesUnlink() {
        val store=store();val record=record(store)
        record.lifecycle=InstallLifecycle(InstallBacking.Installed,1,10,true);store.write(record)
        val installed=snapshot();val result=InstallMaintenance(store,installed,inspector(target)).run()
        assertEquals(setOf(id),result.installed);assertFalse(store.apk(id).exists())
        assertArrayEquals(bytes,resolveInstallSource(store,store.read(id),installed).readBytes())
    }
    @Test fun higherInstalledCodeRetiresOldCopyAndRecordWithoutClaimingThatItWasInstalled() {
        val store=store();record(store,target.copy(versionName="1.0-Release",versionCode=1))
        val result=InstallMaintenance(store,snapshot(),inspector(target)).run()
        assertEquals(setOf(id),result.obsolete);assertTrue(result.installed.isEmpty())
        assertFalse(store.apk(id).exists());assertTrue(store.ids().isEmpty())
    }
    @Test fun legacyRecordsRemainReadableAndCanBeReconciledAfterProtectedReplacement() {
        val store=store();record(store)
        val jsonFile=File(store.directory.parentFile,"records/$id.json")
        val json=JSONObject(jsonFile.readText()).apply { remove("lifecycle") };jsonFile.writeText(json.toString())
        assertEquals(InstallLifecycle(),store.read(id).lifecycle)
        val result=InstallMaintenance(store,snapshot(),inspector(target)).run(packageReplaced=true)
        assertEquals(setOf(id),result.installed);assertFalse(store.apk(id).exists())
    }
    @Test fun alreadyInstalledReinstallCanStillBeRetriedWithoutKeepingAnotherCopy() {
        val store=store();val record=record(store)
        record.lifecycle=InstallLifecycle(launchedFromCode=2,launchedFromUpdateTime=20);store.write(record)
        val result=InstallMaintenance(store,snapshot(),inspector(target)).run()
        assertTrue(result.installed.isEmpty());assertEquals(listOf(id),result.pending.map { it.id })
        assertEquals(InstallBacking.Installed,store.read(id).lifecycle.backing)
    }
    @Test fun actualReplacementAtTheSameVersionCodeUsesThePackageUpdateTime() {
        val store=store();val record=record(store)
        record.lifecycle=InstallLifecycle(launchedFromCode=2,launchedFromUpdateTime=10);store.write(record)
        val result=InstallMaintenance(store,snapshot(time=20),inspector(target)).run()
        assertEquals(setOf(id),result.installed);assertTrue(result.pending.isEmpty())
        assertFalse(store.apk(id).exists())
    }
    @Test fun persistedCompletionFlagNeverDeletesAnUninstalledFutureUpdate() {
        val store=store();val record=record(store)
        record.lifecycle=InstallLifecycle(confirmedInstalled=true);store.write(record)
        val old=target.copy(versionName="1.0-Release",versionCode=1)
        val result=InstallMaintenance(store,snapshot(old,"old".toByteArray()),inspector(old,target)).run()
        assertTrue(result.installed.isEmpty());assertTrue(store.apk(id).isFile)
        assertEquals(listOf(id),result.pending.map { it.id })
    }
    @Test fun cancellationCannotStartCleanup() {
        val store=store();record(store);val job=Job().apply { cancel() }
        try { InstallMaintenance(store,snapshot(),inspector(target)).run(job);fail("Cancellation swallowed") }
        catch(_: CancellationException) { }
        assertTrue(store.apk(id).exists())
    }
    @Test fun staleObsoleteFlagDoesNotDeleteAfterInstalledVersionChanges() {
        val store=store();val record=record(store)
        record.lifecycle=InstallLifecycle(backing=InstallBacking.Obsolete);store.write(record)
        val old=target.copy(versionName="1.0-Release",versionCode=1)
        val result=InstallMaintenance(store,snapshot(old,"old".toByteArray()),inspector(old,target)).run()
        assertEquals(setOf(id),result.deferred);assertTrue(result.obsolete.isEmpty())
        assertTrue(store.apk(id).isFile)
    }
    @Test fun corruptedRecordCannotAuthorizeCleanup() {
        val store=store();record(store)
        File(store.directory.parentFile,"records/$id.json").writeText("{incomplete")
        val result=InstallMaintenance(store,snapshot(),inspector(target)).run()
        assertEquals(setOf(id),result.deferred);assertTrue(result.installed.isEmpty())
        assertTrue(store.apk(id).isFile)
    }
    @Test fun installedBackingIsRevalidatedAfterEveryRestore() {
        val store=store();val record=record(store);val installed=snapshot()
        InstallMaintenance(store,installed,inspector(target)).run()
        installed.file.writeBytes(ByteArray(bytes.size))
        try {
            ApkRevalidator.verify(resolveInstallSource(store,store.read(id),installed),target,record.sha256,record.size,inspector(target))
            fail("Changed installed backing accepted")
        } catch(_: InstallOperationException) { }
        val result=InstallMaintenance(store,installed,inspector(target)).run()
        assertEquals(setOf(id),result.deferred);assertTrue(result.installed.isEmpty())
    }
}
