package example.jitpackconsumer
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import jp.linkserver.goldy.*
import kotlinx.coroutines.*
class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val goldy = Goldy.createWithSource(this,
            GoldyConfig(GitHubRepository("Link2011-Act2", "Goldy"), UpdatePolicies.beastLocator()),
            { ReleaseCatalog(emptyList()) })
        val installer = ApkInstaller.create(this)
        val label = TextView(this).apply { text = "${goldy.state.value} / ${installer.permissionStatus()} / " +
            VersionPolicies.Common.compare("1.0-IntDev_rev2", "1.0-IntDev_rev10") }
        setContentView(label)
        scope.launch {
            val restored = installer.reconcileInstalledUpdates()
            runOnUiThread { label.append(" / pending=" + restored.pendingRequests.size + " / retained bytes=" + restored.retainedBytes) }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
