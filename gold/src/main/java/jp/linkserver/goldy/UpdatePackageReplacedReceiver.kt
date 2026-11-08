package jp.linkserver.goldy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

/** Protected OS event only. Does not start UI or infer success from a previous Activity launch. */
class UpdatePackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action!=Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try { withTimeout(8_000) { ApkInstaller.create(context).reconcileAfterPackageReplacement() } }
            catch(_: Exception) { /* Atomic records/copies stay available; the host retries reconciliation on startup. */ }
            finally { pending.finish() }
        }
    }
}
