package jp.linkserver.goldy

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.io.File
import kotlinx.coroutines.runBlocking

/** Test-only launcher avoids instrumentation's force-stop/revocation when preparing a live reader. */
class InstallLifecycleActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Thread {
            val report = File(filesDir, "maintenance-prepare.txt")
            try {
                val restoredId = intent.getStringExtra("id")
                if (restoredId != null) {
                    val lines = runBlocking { InstallLifecycleChecks(this@InstallLifecycleActivity).verify(restoredId) }
                    val installer = ApkInstaller.create(this@InstallLifecycleActivity)
                    val request = runBlocking { installer.pendingRequests().single { it.id == restoredId } }
                    val hash = lines.single { it.startsWith("sha256=") }.removePrefix("sha256=")
                    val launched = runBlocking { installer.launch(request) { installIntent ->
                        // Use the real SDK's freshly revalidated handoff/ClipData/read flag,
                        // replacing only the recipient with our disposable external reader.
                        startActivity(installIntent.setClassName("example.goldyprobe", "example.goldyprobe.FreshUriReaderActivity")
                            .putExtra("sha256", hash).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } }
                    check(launched == InstallLaunchResult.HandedOff)
                    File(filesDir, "maintenance-verify.txt").writeText(lines.joinToString("\n"))
                    return@Thread
                }
                val lines = runBlocking { InstallLifecycleChecks(this@InstallLifecycleActivity).prepare() }
                val uri = Uri.parse(lines.single { it.startsWith("uri=") }.removePrefix("uri="))
                val sha = lines.single { it.startsWith("sha256=") }.removePrefix("sha256=")
                val reader = Intent().setClassName("example.goldyprobe", "example.goldyprobe.HeldUriReaderActivity")
                    .setData(uri).putExtra("sha256", sha)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                reader.clipData = ClipData.newRawUri("APK", uri)
                startActivity(reader)
                report.writeText(lines.joinToString("\n"))
            } catch (error: Throwable) {
                val target = if(intent.hasExtra("id")) File(filesDir, "maintenance-verify.txt") else report
                target.writeText("failure=" + error.stackTraceToString())
            }
            finally { runOnUiThread { finish() } }
        }.start()
    }
}
