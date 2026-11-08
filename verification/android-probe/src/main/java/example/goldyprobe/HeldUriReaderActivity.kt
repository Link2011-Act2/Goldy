package example.goldyprobe

import android.app.Activity
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.security.MessageDigest

/** Separate UID keeps an actual descriptor open across replacement of its provider's APK. */
class HeldUriReaderActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Thread {
            val ready = File(filesDir, "reader-ready")
            val proceed = File(filesDir, "reader-continue")
            val result = File(filesDir, "reader-result")
            try {
                val expected = intent.getStringExtra("sha256")!!
                val uri = intent.data!!
                // A stable provider client is killed by Android when the provider is updated.
                // Release an unstable client after receiving the actual descriptor so this
                // test can observe the kernel file lifetime independently of that policy.
                val descriptor = contentResolver.acquireUnstableContentProviderClient(uri)!!.use {
                    it.openFile(uri, "r")!!
                }
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val prefix = ByteArray(4096)
                    val count = input.read(prefix)
                    check(count > 0)
                    digest.update(prefix, 0, count)
                    ready.writeText("open")
                    val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
                    while (!proceed.exists()) {
                        check(android.os.SystemClock.elapsedRealtime() < deadline) { "Host update timed out" }
                        Thread.sleep(100)
                    }
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                    check(digest.digest().hex() == expected) { "Open descriptor bytes changed" }
                }
                File(filesDir, "reader-fd-verified").writeText("identical")
                result.writeText("PASS: external open descriptor survived with identical bytes")
            } catch (error: Throwable) { result.writeText("failure=" + error.stackTraceToString()) }
            finally { runOnUiThread { finish() } }
        }.start()
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
}
