package jp.linkserver.goldy

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

/** Public result is for host state restoration, never a claim based on Activity launch. */
data class InstallReconciliation(
    val pendingRequests: List<InstallRequest>,
    val installedRequestIds: Set<String>,
    val supersededRequestIds: Set<String>,
    val deferredCleanupRequestIds: Set<String>,
    val retainedBytes: Long
)

internal data class InstalledApkSnapshot(
    val metadata: ApkMetadata, val file: File, val lastUpdateTime: Long
)
internal data class InstallMaintenanceResult(
    val pending: List<InstallRecord>, val installed: Set<String>, val obsolete: Set<String>,
    val deferred: Set<String>, val retainedBytes: Long
)

/**
 * Replaces a duplicate with the authenticated, byte-identical installed APK as URI backing.
 * Existing Linux file descriptors survive unlink; future reads keep the same URI/bytes.
 * No URI grant is revoked here. Older-version entries can be removed because the existing
 * provider verification already rejects reopening a downgrade; open descriptors still work.
 */
internal class InstallMaintenance(
    private val files: InstallFiles,
    private val installed: InstalledApkSnapshot,
    private val inspector: ApkInspector,
    private val commit: (InstallRecord) -> Unit = files::write
) {
    fun run(coroutine: CoroutineContext? = null, packageReplaced: Boolean = false): InstallMaintenanceResult {
        val pending=mutableListOf<InstallRecord>()
        val completed=mutableSetOf<String>(); val obsolete=mutableSetOf<String>(); val deferred=mutableSetOf<String>()
        var retainedBytes=0L
        for(id in files.ids()) {
            coroutine?.ensureActive()
            var record: InstallRecord? = null
            try {
                record=files.read(id)
                require(record.metadata.packageName==installed.metadata.packageName)
                val old=record.lifecycle
                val backing=when {
                    record.metadata.versionCode < installed.metadata.versionCode -> InstallBacking.Obsolete
                    matchesInstalled(record,coroutine) -> {
                        // Authenticate actual installed bytes/signatures, not metadata or an old hash state.
                        ApkRevalidator.verify(installed.file,record.metadata,record.sha256,record.size,inspector,coroutine)
                        InstallBacking.Installed
                    }
                    old.backing!=InstallBacking.Retained -> throw IOException("Installed APK backing changed")
                    else -> InstallBacking.Retained
                }
                val confirmed=backing==InstallBacking.Installed && (packageReplaced || old.confirmedInstalled ||
                    old.launchedFromCode!=null && old.launchedFromUpdateTime!=null &&
                    (old.launchedFromCode!=installed.metadata.versionCode || old.launchedFromUpdateTime!=installed.lastUpdateTime))
                val next=old.copy(backing=backing,confirmedInstalled=confirmed)
                // Keep the original object on commit failure: in-memory state must not outrun disk.
                if(next!=old) {
                    val updated=InstallRecord(record.id,record.sha256,record.size,record.metadata).also { it.lifecycle=next }
                    commit(updated)
                    record=updated
                }
                if(backing!=InstallBacking.Retained) {
                    val copy=files.apk(id)
                    if(copy.exists() && !copy.delete()) deferred+=id
                }
                when {
                    backing==InstallBacking.Obsolete -> obsolete+=id
                    confirmed -> completed+=id
                    else -> pending+=record
                }
                if(backing==InstallBacking.Obsolete && id !in deferred) files.delete(id)
            } catch(cancel: CancellationException) { throw cancel }
            catch(_: Exception) {
                deferred+=id
                // Uncertain/corrupt data is never converted into install authority or deleted by age.
                record?.takeIf { it.lifecycle.backing==InstallBacking.Retained }?.let { if(pending.none { p -> p.id==it.id }) pending+=it }
            }
            val bytes=runCatching { files.apk(id).takeIf(File::isFile)?.length() ?: 0L }.getOrDefault(0L)
            retainedBytes=if(Long.MAX_VALUE-retainedBytes < bytes) Long.MAX_VALUE else retainedBytes+bytes
        }
        return InstallMaintenanceResult(pending,completed,obsolete,deferred,retainedBytes)
    }
    private fun matchesInstalled(record: InstallRecord, coroutine: CoroutineContext?): Boolean = record.metadata.packageName==installed.metadata.packageName &&
        record.metadata.versionCode==installed.metadata.versionCode && record.metadata.versionName==installed.metadata.versionName &&
        installed.file.isFile && installed.file.length()==record.size &&
        ApkRevalidator.digest(installed.file,record.size,coroutine)==record.sha256
}

/** Resolve only OS-reported own source paths; no persisted arbitrary file path is trusted. */
internal fun resolveInstallSource(files: InstallFiles,record: InstallRecord,installed: InstalledApkSnapshot): File = when(record.lifecycle.backing) {
    InstallBacking.Retained -> files.apk(record.id)
    InstallBacking.Obsolete -> throw ApkException(ApkFailure.VersionCodeTooLow)
    InstallBacking.Installed -> {
        if(installed.metadata.packageName!=record.metadata.packageName || installed.metadata.versionCode!=record.metadata.versionCode ||
            installed.metadata.versionName!=record.metadata.versionName) throw InstallOperationException(InstallFailure.FileChanged)
        installed.file
    }
}
