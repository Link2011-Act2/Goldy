package example.goldyprobe

import android.app.Activity
import android.os.Bundle
import java.io.File
import java.security.MessageDigest

/** Receives the real SDK handoff grant after the provider has been updated and restored. */
class FreshUriReaderActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Thread {
            val result = File(filesDir, "reader-reopen-result")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                contentResolver.openInputStream(intent.data!!)!!.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                check(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == intent.getStringExtra("sha256"))
                result.writeText("PASS: explicit SDK retry reopened identical installed APK through the same URI")
            } catch (error: Throwable) { result.writeText("failure=" + error.stackTraceToString()) }
            finally { runOnUiThread { finish() } }
        }.start()
    }
}
