package example.goldyprobe

import android.app.Activity
import android.os.Bundle
import android.os.ResultReceiver

/** Separate UID verifies URI grants in the disposable Android test setup. */
class UriProbeActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Bundle()
        val uri = intent.data!!
        try { contentResolver.openInputStream(uri)!!.use { check(it.read() >= 0) }; result.putBoolean("read", true) }
        catch (error: Exception) { result.putString("readError", error.javaClass.simpleName) }
        try { contentResolver.openOutputStream(uri)!!.close(); result.putBoolean("writeDenied", false) }
        catch (_: Exception) { result.putBoolean("writeDenied", true) }
        intent.getParcelableExtra<ResultReceiver>("reply")!!.send(0, result)
        finish()
    }
}
