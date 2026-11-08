package jp.linkserver.goldy

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import java.io.File

/** Uses real signed APKs and OS replacement. No production verifier is replaced by a test double. */
internal class InstallLifecycleChecks(private val context: Context) {
    suspend fun prepare(): List<String> {
        val inspector = AndroidApkInspector(context)
        val source = File(context.cacheDir, "goldy-apks/maintenance-target.apk").apply {
            parentFile!!.mkdirs()
            File(context.filesDir, "maintenance-target.apk").copyTo(this, overwrite = true)
        }
        val metadata = inspector.inspect(source) ?: error("Target APK signature rejected")
        check(metadata.versionCode > inspector.installed().versionCode)
        ApkVerifier.verifyInstalled(inspector.installed(), metadata)
        val hash = ApkRevalidator.digest(source, source.length())
        val apk = VerifiedApk(source, metadata, hash, GitHubAsset(1, "target.apk",
            "https://github.com/example/test/releases/download/v1/target.apk", source.length(), "sha256:$hash"))
        val manager = ApkInstaller.create(context)
        val request = (manager.prepare(apk) as InstallPreparation.Ready).request
        check((manager.prepare(apk) as InstallPreparation.Ready).request.id == request.id)
        // Persist pre-launch baseline without opening any installer UI. A real adb update follows.
        check(manager.launch(request) { } == InstallLaunchResult.HandedOff)
        val cancelled = manager.reconcileInstalledUpdates()
        check(cancelled.pendingRequests.any { it.id == request.id })
        check(cancelled.installedRequestIds.isEmpty())
        check(File(context.filesDir, "goldy-apks/install/${request.id}.apk").isFile)
        source.delete()
        return listOf("PASS: real signed future APK verified; duplicate prepare reused; unknown/cancelled handoff retained",
            "id=${request.id}", "uri=${request.uri}", "sha256=$hash", "size=${apk.asset.size}")
    }

    suspend fun verify(id: String): List<String> {
        val manager = ApkInstaller.create(context)
        val result = manager.reconcileInstalledUpdates()
        check(id in result.installedRequestIds) { "Actual installed APK did not confirm replacement: $result" }
        check(result.pendingRequests.none { it.id == id })
        val files = InstallFiles(context.filesDir)
        check(!files.apk(id).exists()) { "Redundant copy was not removed" }
        val record = files.read(id)
        val request = manager.pendingRequests().single { it.id == id }
        val bytes = context.contentResolver.openInputStream(request.uri)!!.use { it.readBytes() }
        check(bytes.size.toLong() == record.size && sha256(bytes) == record.sha256)
        context.contentResolver.query(request.uri, arrayOf(OpenableColumns.SIZE), null, null, null)!!.use {
            check(it.moveToFirst() && it.getLong(0) == record.size)
        }
        check(manager.reconcileInstalledUpdates().installedRequestIds.contains(id))
        return listOf("PASS: real package update confirmed; duplicate removed; restored URI returns identical authenticated installed APK; cleanup is idempotent",
            "id=$id", "sha256=${record.sha256}", "size=${record.size}")
    }
}
