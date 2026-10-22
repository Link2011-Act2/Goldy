package jp.linkserver.goldy

import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

/** Deterministic Android checks plus opt-in live network; installs only signed test APKs. */
class GoldyTestRunner : Instrumentation() {
    private lateinit var arguments: Bundle
    private val lines = mutableListOf<String>()
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); this.arguments = arguments; start() }
    private fun passed(name: String) { lines.add("PASS: " + name) }
    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking {
                when (arguments.getString("mode") ?: "installer") {
                    "permission" -> permission()
                    "installer" -> installer()
                    "network" -> network()
                    "no-installer" -> unavailable(InstallFailure.NoInstaller)
                    "provider-disabled" -> {
                        val component = android.content.ComponentName(context, GoldyFileProvider::class.java)
                        val pm = context.packageManager
                        pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                        try { unavailable(InstallFailure.ProviderMisconfigured) }
                        finally { pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, android.content.pm.PackageManager.DONT_KILL_APP) }
                    }
                    "cleanup" -> {
                        val manager = ApkInstaller.create(context)
                        manager.pendingRequests().forEach { manager.release(it) }
                        check(manager.pendingRequests().isEmpty()); passed("explicit release after installer closed")
                    }
                    else -> error("Unknown test mode")
                }
            }
            result.putString("result", lines.joinToString("\n")); result.putInt("sdk", android.os.Build.VERSION.SDK_INT)
            finish(0, result)
        } catch (error: Throwable) {
            result.putString("result", lines.joinToString("\n")); result.putString("failure", error.stackTraceToString()); finish(1, result)
        }
    }
    private fun receipt(): VerifiedApk {
        val inspector = AndroidApkInspector(context)
        val file = File(File(context.cacheDir, "goldy-apks").apply { mkdirs() }, "goldy-" + UUID.randomUUID() + ".apk")
        File(context.applicationInfo.sourceDir).copyTo(file)
        val metadata = inspector.inspect(file) ?: error("Self archive parse failed")
        check(metadata == inspector.installed())
        val hash = ApkRevalidator.digest(file, file.length())
        return VerifiedApk(file, metadata, hash, GitHubAsset(1, "test.apk", "https://github.com/example/test/releases/download/1/test.apk", file.length(), "sha256:" + hash))
    }
    private suspend fun unavailable(reason: InstallFailure) {
        val manager = ApkInstaller.create(context)
        val apk = receipt()
        try {
            val result = manager.prepare(apk)
            check(result is InstallPreparation.Failed && result.error.reason == reason) { "Unexpected result: " + result }
            check(manager.pendingRequests().isEmpty())
            passed("real disabled component rejected as " + reason + "; no handoff lease leaked")
        } finally { apk.file.delete() }
    }
    private suspend fun permission() {
        val manager = ApkInstaller.create(context)
        check(manager.permissionStatus() == InstallPermission.UserPermissionRequired)
        val receipt = receipt()
        val result = manager.prepare(receipt)
        check(result is InstallPreparation.PermissionRequired)
        check(result.settingsIntent.action == android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        check(result.settingsIntent.data == Uri.parse("package:" + context.packageName))
        context.startActivity(result.settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        receipt.file.delete(); passed("unknown-source denied; package-scoped settings Activity launched; no APK lease created")
        check(manager.pendingRequests().isEmpty())
    }
    private suspend fun installer() {
        val manager = ApkInstaller.create(context)
        check(manager.permissionStatus() == InstallPermission.Allowed)
        val inspector = AndroidApkInspector(context)
        for ((filename, reason) in listOf("foreign-signer.apk" to ApkFailure.SignerMismatch, "multi-signers.apk" to ApkFailure.SignerMismatch)) {
            val archive = inspector.inspect(File(context.filesDir, filename)) ?: error("Missing signed fixture " + filename)
            check(archive.packageName == inspector.installed().packageName)
            try { ApkVerifier.verifyInstalled(inspector.installed(), archive); error("Wrong signers accepted") }
            catch (error: ApkException) { check(error.reason == reason) }
        }
        passed("real PackageManager single/multiple signer extraction; foreign and partial signer sets rejected")
        val corrupt = File(context.filesDir, "corrupt.apk")
        check(inspector.inspect(corrupt) == null); passed("real tampered signed APK rejected")
        val receipt = receipt()
        val prepared = manager.prepare(receipt)
        check(prepared is InstallPreparation.Ready) { "prepare: " + prepared }
        val request = prepared.request
        check(request.uri.scheme == "content" && request.uri.authority == context.packageName + ".goldy.fileprovider")
        check(manager.pendingRequests().any { it.id == request.id })
        val existing = File(context.filesDir, "host-shared/file.txt").apply { parentFile!!.mkdirs(); writeText("host") }
        check(FileProvider.getUriForFile(context, context.packageName + ".host.fileprovider", existing).authority != request.uri.authority)
        try { FileProvider.getUriForFile(context, manager.authority, existing); error("Host file shared by Goldy") } catch (_: IllegalArgumentException) { }
        passed("host FileProvider coexists; Goldy URI excludes host files")
        receipt.file.delete()
        context.contentResolver.openInputStream(request.uri)!!.use { check(it.readBytes().size.toLong() == receipt.asset.size) }
        var writeDenied = false
        try { context.contentResolver.openOutputStream(request.uri)!!.close() } catch (_: Exception) { writeDenied = true }
        check(writeDenied)
        passed("staged APK remains readable after original deletion; writes denied")
        val denied = receiver(request.uri, grant = false)
        check(!denied.getBoolean("read")); passed("another UID cannot read without a grant")
        val granted = receiver(request.uri, grant = true)
        check(granted.getBoolean("read") && granted.getBoolean("writeDenied")); passed("another UID gets temporary read only")
        for ((error, expected) in listOf(ActivityNotFoundException() to InstallFailure.NoInstaller, SecurityException() to InstallFailure.LaunchDenied, IllegalStateException() to InstallFailure.LaunchFailed, IllegalArgumentException() to InstallFailure.LaunchFailed)) {
            val launch = manager.launch(request) { throw error }
            check(launch is InstallLaunchResult.Failed && launch.error.reason == expected)
            check(context.contentResolver.openInputStream(request.uri)!!.use { it.read() } >= 0)
        }
        passed("host launch exceptions classified; ambiguous failure retains APK")
        manager.release(request)
        check(!receiver(request.uri, grant = true).getBoolean("read")); passed("explicit release revokes/removes shared APK")
        val changedReceipt = receipt()
        val changed = (manager.prepare(changedReceipt) as InstallPreparation.Ready).request
        val staged = File(context.filesDir, "goldy-apks/install/" + changed.id + ".apk")
        staged.setWritable(true); staged.writeBytes(ByteArray(staged.length().toInt()))
        val changedLaunch = manager.launch(changed) { error("Changed APK launched") }
        check(changedLaunch is InstallLaunchResult.Failed && changedLaunch.error.reason == InstallFailure.FileChanged)
        var rejected = false
        try { context.contentResolver.openInputStream(changed.uri)?.close() } catch (_: Exception) { rejected = true }
        check(rejected); manager.release(changed); changedReceipt.file.delete()
        passed("mutation after prepare rejected again at launch AND provider open")
        val missing = receipt(); missing.file.delete()
        check((manager.prepare(missing) as InstallPreparation.Failed).error.reason == InstallFailure.FileMissing)
        passed("deleted source rejected")
        val lastReceipt = receipt()
        val last = (manager.prepare(lastReceipt) as InstallPreparation.Ready).request
        val outcome = manager.launch(last) { intent ->
            check(intent.flags == Intent.FLAG_GRANT_READ_URI_PERMISSION)
            check(intent.clipData!!.getItemAt(0).uri == last.uri)
            check(intent.component != null && intent.data == last.uri)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        check(outcome == InstallLaunchResult.HandedOff)
        check(manager.pendingRequests().any { it.id == last.id })
        check(context.contentResolver.openInputStream(last.uri)!!.use { it.read() } >= 0)
        lastReceipt.file.delete()
        passed("system installer Activity launched with only URI read grant; handed-off file retained; install success NOT inferred")
        lines.add("retained URI: " + last.uri)
    }
    private fun receiver(uri: Uri, grant: Boolean): Bundle {
        val latch = CountDownLatch(1)
        var response: Bundle? = null
        val reply = object : ResultReceiver(null) { override fun onReceiveResult(code: Int, data: Bundle) { response = data; latch.countDown() } }
        val intent = Intent().setClassName("example.goldyprobe", "example.goldyprobe.UriProbeActivity")
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .putExtra("reply", reply).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (grant) { intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); intent.clipData = ClipData.newRawUri("APK", uri) }
        val parcel = android.os.Parcel.obtain()
        reply.writeToParcel(parcel, 0); parcel.setDataPosition(0)
        val remoteReply = ResultReceiver.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        intent.putExtra("reply", remoteReply)
        context.startActivity(intent)
        check(latch.await(20, TimeUnit.SECONDS)) { "Cross-UID receiver timed out" }
        return response!!
    }
    private suspend fun network() {
        val repo = GitHubRepository("Link2011-Act2", "ShutUp")
        val asset = GitHubAsset(647477047, "shutup_v110release.apk", repo.url + "/releases/download/v1.1.0-Release/shutup_v110release.apk", 2878925,
            "sha256:4f567b5cee03217a9c3c5efc11031edf5b6abaff29754022fb74f546e6e21f6b")
        val release = GitHubRelease(415013948, "v1.1.0-Release", url = repo.url + "/releases/tag/v1.1.0-Release", assets = listOf(asset))
        val config = ApkDownloadConfig(repo, ApkSelectionPolicies.ShutUp, ApkVersionBindings.ShutUp, maxAttempts = 1)
        val events = mutableListOf<ApkProgress>()
        try { ApkDownloader.create(context, config).download(release) { events.add(it) }; error("Official different package accepted") }
        catch (error: ApkException) { check(error.reason == ApkFailure.PackageMismatch) { "Live transfer failed: " + error.reason + " cause=" + error.cause } }
        check(events.first().bytes == 0L && events.last().bytes == asset.size)
        check(events.last().total == asset.size)
        passed("PRODUCTION HttpURLConnection live GitHub HTTPS/CDN transfer, progress, size, SHA256, PackageManager parse; foreign package safely rejected")
        lines.add("progress callbacks=" + events.size + ", final bytes=" + events.last().bytes)
        var reached = false
        coroutineScope {
        val transfer = launch(Dispatchers.Default) {
            try { ApkDownloader.create(context, config).download(release) { if (it.bytes > 0) { reached = true; throw CancellationException("test cancellation") } } }
            catch (_: CancellationException) { }
        }
        transfer.join(); check(reached)
        }
        check(File(context.cacheDir, "goldy-apks").listFiles().orEmpty().none { it.name.endsWith(".part") })
        passed("real HTTPS transfer cancellation cleans .part")
        val broken = release.copy(assets = listOf(asset.copy(downloadUrl = repo.url + "/releases/download/v1.1.0-Release/missing-goldy-test.apk", name = "missing-goldy-test.apk")))
        try { ApkDownloader.create(context, config).download(broken); error("Missing asset accepted") }
        catch (error: ApkException) { check(error.reason == ApkFailure.Http && error.status == 404) }
        passed("real GitHub HTTP404 surfaced; no partial file retained")
    }
}
