package jp.linkserver.goldy

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class InstallPermission { Allowed, UserPermissionRequired, MissingManifestPermission, Unavailable }
enum class InstallFailure { MissingManifestPermission, PermissionUnavailable, ProviderMisconfigured, NoInstaller,
    FileMissing, FileChanged, InvalidFile, ApkRejected, Storage, LaunchDenied, LaunchFailed }
data class InstallError(val reason: InstallFailure, val apkFailure: ApkFailure? = null)
sealed interface InstallPreparation {
    data class Ready(val request: InstallRequest) : InstallPreparation
    data class PermissionRequired(val settingsIntent: Intent) : InstallPreparation
    data class Failed(val error: InstallError) : InstallPreparation
}
sealed interface InstallLaunchResult {
    /** Android accepted the Activity launch. This does NOT mean the APK was installed. */
    data object HandedOff : InstallLaunchResult
    data class PermissionRequired(val settingsIntent: Intent) : InstallLaunchResult
    data class Failed(val error: InstallError) : InstallLaunchResult
}
/** A persisted lease. Keep it until the installer has finished using its URI. */
class InstallRequest internal constructor(val id: String, val uri: Uri, val metadata: ApkMetadata)
internal class InstallOperationException(val reason: InstallFailure) : Exception(reason.name)

/** Host owns the Activity, settings navigation and install outcome. This class never calls startActivity. */
class ApkInstaller private constructor(context: Context) {
    private val context = context.applicationContext
    private val files = InstallFiles(this.context.filesDir)
    private val inspector = AndroidApkInspector(this.context)
    internal val authority: String = this.context.packageName + ".goldy.fileprovider"
    companion object {
        private val gate = Mutex()
        fun create(context: Context) = ApkInstaller(context)
    }

    @Suppress("DEPRECATION")
    fun permissionStatus(): InstallPermission = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        if (Manifest.permission.REQUEST_INSTALL_PACKAGES !in info.requestedPermissions.orEmpty()) InstallPermission.MissingManifestPermission
        else if (pm.canRequestPackageInstalls()) InstallPermission.Allowed else InstallPermission.UserPermissionRequired
    } catch (_: Exception) { InstallPermission.Unavailable }

    fun unknownSourcesSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:" + context.packageName))

    suspend fun prepare(apk: VerifiedApk): InstallPreparation = gate.withLock {
        permissionPreparation()?.let { return@withLock it }
        var committed: String? = null
        try {
            withContext(Dispatchers.IO) {
                checkProvider()
                val root = File(context.cacheDir, "goldy-apks").canonicalFile
                val source = apk.file.canonicalFile
                if (source.parentFile != root || source != File(root, apk.file.name))
                    throw InstallOperationException(InstallFailure.InvalidFile)
                val size = apk.asset.size ?: source.length()
                val coroutine = currentCoroutineContext()
                ApkRevalidator.verify(source, apk.metadata, apk.sha256, size, inspector, coroutine)
                files.initialize()
                val id = UUID.randomUUID().toString()
                val partial = File(files.directory, id + ".part")
                val target = files.apk(id)
                var retained = false
                try {
                    source.inputStream().use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var count = 0L
                            while (true) {
                                coroutine.ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                count += n
                                if (count > size) throw InstallOperationException(InstallFailure.FileChanged)
                                output.write(buffer, 0, n)
                            }
                            output.fd.sync()
                        }
                    }
                    ApkRevalidator.verify(partial, apk.metadata, apk.sha256, size, inspector, coroutine)
                    if (!partial.renameTo(target) || !target.setReadOnly()) throw IOException("Could not freeze APK")
                    val record = InstallRecord(id, apk.sha256, size, apk.metadata)
                    synchronized(InstallFiles.lock) { files.write(record) }
                    committed = id
                    val request = request(record)
                    installerIntent(request) ?: throw InstallOperationException(InstallFailure.NoInstaller)
                    coroutine.ensureActive()
                    retained = true
                    InstallPreparation.Ready(request)
                } finally {
                    partial.delete()
                    if (!retained) synchronized(InstallFiles.lock) { files.delete(id) }
                }
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { committed?.let { synchronized(InstallFiles.lock) { files.delete(it) } } }
            throw error
        } catch (error: Exception) { InstallPreparation.Failed(classify(error)) }
    }

    /** Rechecks permission, file/hash/archive and current installation immediately before the host callback. */
    suspend fun launch(request: InstallRequest, startActivity: (Intent) -> Unit): InstallLaunchResult = gate.withLock {
        when (val permission = permissionPreparation()) {
            is InstallPreparation.PermissionRequired -> return@withLock InstallLaunchResult.PermissionRequired(permission.settingsIntent)
            is InstallPreparation.Failed -> return@withLock InstallLaunchResult.Failed(permission.error)
            else -> Unit
        }
        val intent = try {
            withContext(Dispatchers.IO) {
                checkProvider()
                val record = synchronized(InstallFiles.lock) { files.read(request.id) }
                if (request.uri != uri(record) || request.metadata != record.metadata) throw InstallOperationException(InstallFailure.InvalidFile)
                ApkRevalidator.verify(files.apk(record.id), record.metadata, record.sha256, record.size, inspector, currentCoroutineContext())
                installerIntent(request) ?: throw InstallOperationException(InstallFailure.NoInstaller)
            }
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) { return@withLock InstallLaunchResult.Failed(classify(error)) }
        currentCoroutineContext().ensureActive()
        try {
            startActivity(intent)
            InstallLaunchResult.HandedOff
        } catch (error: CancellationException) { throw error
        } catch (_: ActivityNotFoundException) { InstallLaunchResult.Failed(InstallError(InstallFailure.NoInstaller))
        } catch (_: SecurityException) { InstallLaunchResult.Failed(InstallError(InstallFailure.LaunchDenied))
        } catch (_: Exception) { InstallLaunchResult.Failed(InstallError(InstallFailure.LaunchFailed)) }
        // Never delete here: even an unsuccessful/ambiguous callback may have handed the URI to Android.
    }

    /** Restore leases after process death. Launch revalidates them; no success is inferred.
     * Storage/record errors propagate; retain files rather than guess they are unused.
     */
    suspend fun pendingRequests(): List<InstallRequest> = gate.withLock {
        withContext(Dispatchers.IO) { synchronized(InstallFiles.lock) { files.all().map(::request) } }
    }

    /** Explicitly call ONLY once no installer/reader needs this URI. Never call in a launch finally/onResume. */
    suspend fun release(request: InstallRequest) = gate.withLock {
        withContext(Dispatchers.IO) {
            synchronized(InstallFiles.lock) {
                val expected = FileProvider.getUriForFile(context, authority, files.apk(request.id))
                require(request.uri == expected)
                context.revokeUriPermission(request.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                files.delete(request.id)
            }
        }
    }

    private fun request(record: InstallRecord) = InstallRequest(record.id, uri(record), record.metadata)
    private fun uri(record: InstallRecord): Uri = FileProvider.getUriForFile(context, authority, files.apk(record.id))
    private fun permissionPreparation(): InstallPreparation? = when (permissionStatus()) {
        InstallPermission.Allowed -> null
        InstallPermission.UserPermissionRequired -> InstallPreparation.PermissionRequired(unknownSourcesSettingsIntent())
        InstallPermission.MissingManifestPermission -> InstallPreparation.Failed(InstallError(InstallFailure.MissingManifestPermission))
        InstallPermission.Unavailable -> InstallPreparation.Failed(InstallError(InstallFailure.PermissionUnavailable))
    }
    @Suppress("DEPRECATION")
    private fun checkProvider() {
        val info = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)
        if (info?.name != GoldyFileProvider::class.java.name || info.packageName != context.packageName || info.exported || !info.grantUriPermissions)
            throw InstallOperationException(InstallFailure.ProviderMisconfigured)
    }
    @Suppress("DEPRECATION")
    private fun installerIntent(request: InstallRequest): Intent? {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(request.uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("APK", request.uri)
        val pm = context.packageManager
        val candidates = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY)
            .filter { it.activityInfo?.exported == true && it.activityInfo.enabled &&
                (it.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0 }
        val preferred = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        val target = candidates.firstOrNull { it.activityInfo.packageName == preferred?.packageName && it.activityInfo.name == preferred.name }
            ?: candidates.sortedWith(compareBy({ it.activityInfo.packageName }, { it.activityInfo.name })).firstOrNull()
            ?: return null
        return intent.setClassName(target.activityInfo.packageName, target.activityInfo.name)
    }
    private fun classify(error: Exception) = when (error) {
        is InstallOperationException -> InstallError(error.reason)
        is ApkException -> InstallError(InstallFailure.ApkRejected, error.reason)
        is ActivityNotFoundException -> InstallError(InstallFailure.NoInstaller)
        is SecurityException -> InstallError(InstallFailure.LaunchDenied)
        is java.io.FileNotFoundException -> InstallError(InstallFailure.FileMissing)
        is IOException -> InstallError(InstallFailure.Storage)
        is IllegalArgumentException -> InstallError(InstallFailure.ProviderMisconfigured)
        else -> InstallError(InstallFailure.LaunchFailed)
    }
}
