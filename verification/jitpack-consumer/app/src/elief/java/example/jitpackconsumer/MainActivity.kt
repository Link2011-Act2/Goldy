package example.jitpackconsumer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val goldy = Goldy.create(this, GoldyConfig(GitHubRepository("Link2011-Act2", "Goldy"), UpdatePolicies.nittcScheduler()))
        val release = GitHubRelease(1, "v1.0.0-Release", notes = "# Public consumer\n\n**Gold** and Elief.\n\n| A | B |\n| - | - |\n| Core | UI |", url = "https://github.com/Link2011-Act2/Goldy/releases/tag/v1.0.0-Release")
        val candidate = UpdateCandidate(release)
        val state = goldy.state.value.copy(availableUpdate = candidate, notificationUpdate = candidate, showBanner = true)
        setContent { HostTheme {
            var prompt by remember { mutableStateOf(false) }
            var details by remember { mutableStateOf(false) }
            if (details) UpdateOverviewScreen(state.toUpdateUiState(), { details = false }, {}, {})
            else Column {
                UpdateNotificationBanner(state, { details = true }, { lifecycleScope.launch { goldy.hideBanner() } })
                UpdateNotificationIcon(state, { prompt = true })
            }
            if (prompt) UpdateCheckDialog(state,
                { lifecycleScope.launch { goldy.check(CheckTrigger.Manual) } },
                { prompt = false; details = true },
                { tag -> lifecycleScope.launch { goldy.ignoreVersion(tag) } }, { prompt = false })
        } }
    }
}
