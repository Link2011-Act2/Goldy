package example.jitpackconsumer
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import jp.linkserver.goldy.*
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val goldy = Goldy.create(this, GoldyConfig(GitHubRepository("Link2011-Act2", "Goldy"), UpdatePolicies.nittcScheduler()))
        val installer = ApkInstaller.create(this)
        setContentView(TextView(this).apply { text = "${goldy.state.value} / ${installer.permissionStatus()}" })
    }
}
