package example.jitpackconsumer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val goldy = Goldy.create(this, GoldyConfig(GitHubRepository("Link2011-Act2", "Goldy"), UpdatePolicies.nittcScheduler()))
        val release = GitHubRelease(1, "v1.0.0-Release", notes = "# Public consumer\n\n**Gold** and Elief.\n\n| A | B |\n| - | - |\n| Core | UI |", url = "https://github.com/Link2011-Act2/Goldy/releases/tag/v1.0.0-Release")
        val state = goldy.state.value.copy(availableUpdate = UpdateCandidate(release)).toUpdateUiState()
        setContent { HostTheme { UpdateOverviewScreen(state, { finish() }, {}, {}) } }
    }
}
