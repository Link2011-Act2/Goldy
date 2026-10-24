package example.jitpackconsumer
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import jp.linkserver.goldy.*
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val goldy = Goldy.createWithSource(this,
            GoldyConfig(GitHubRepository("Link2011-Act2", "Goldy"), UpdatePolicies.beastLocator()),
            { ReleaseCatalog(emptyList()) })
        val installer = ApkInstaller.create(this)
        setContentView(TextView(this).apply { text = "${goldy.state.value} / ${installer.permissionStatus()} / " +
            VersionPolicies.Common.compare("1.0-IntDev_rev2", "1.0-IntDev_rev10") })
    }
}
