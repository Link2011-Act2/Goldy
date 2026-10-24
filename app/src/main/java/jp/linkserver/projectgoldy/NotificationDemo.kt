package jp.linkserver.projectgoldy

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Real Gold state machine with an explicitly offline, metadata-only source. No APKs or network. */
class NotificationDemoViewModel(application: Application) : AndroidViewModel(application) {
    private val store = InMemoryUpdateStateStore()
    private val config = GoldyConfig(GitHubRepository("Link2011-Act2", "Goldy"), UpdatePolicies.beastLocator())
    var scenario by mutableIntStateOf(0)
        private set
    var engine by mutableStateOf(newEngine())
        private set
    var busy by mutableStateOf(false)
        private set
    var selected by mutableStateOf<UpdateCandidate?>(null)
        private set
    var event by mutableStateOf("Offline source · no network or APK installation")
        private set
    var checks by mutableIntStateOf(0)
        private set
    private var operationJob: Job? = null

    private fun newEngine() = Goldy.createWithSource(getApplication(), config, {
        // Capture the scenario before suspending; a change applies to the next request.
        val requested = withContext(Dispatchers.Main.immediate) { checks++; scenario }
        delay(if (requested == 3) 10_000 else 500)
        if (requested == 2) throw GitHubException(GoldyError.Network)
        if (requested == 1) ReleaseCatalog(listOf(notificationRelease(1, "v0.0.0-Release")))
        else ReleaseCatalog(listOf(notificationRelease(30, "v2.1.1-IntDev_rev10"),
            notificationRelease(29, "v2.1.1-IntDev_rev2"), notificationRelease(28, "v2.1.0-Release")))
    }, store)

    fun scenario(index: Int) { if (!busy) { scenario = index; event = "Next check uses the selected offline scenario" } }
    fun check() = operation { engine.check(CheckTrigger.Manual); event = "Offline check completed" }
    fun hide() = operation { engine.hideBanner(); event = "Banner hidden for this session; badge and manual access remain" }
    fun show() = operation { engine.showBanner(); event = "Temporary banner hiding cleared; ignored versions stay ignored" }
    fun ignore(tag: String) = operation { engine.ignoreVersion(tag); event = "Ignored $tag; manual details remain available" }
    fun resetIgnored() = operation { engine.resetIgnoredVersions(); event = "Ignored versions reset; temporary hiding is unchanged" }
    fun restore() = operation {
        engine = newEngine()
        engine.restoreCachedState()
        event = "New Gold session restored from the in-memory store without fetching"
    }
    fun open(candidate: UpdateCandidate) { selected = candidate }
    fun back() { selected = null }
    fun link(url: String) { event = "Fixture URL callback only: $url" }
    fun cancelCheck() {
        if (engine.state.value.checking) {
            operationJob?.cancel()
            event = "Offline check cancelled; saved information retained"
        }
    }

    private fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true // Claim on Main before launch; fixture checks cannot double-start.
        operationJob = viewModelScope.launch {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { event = "Offline fixture operation failed" }
            finally { busy = false }
        }
    }
}

private fun notificationRelease(id: Long, tag: String) = GitHubRelease(id, tag,
    notes = "## Offline notification showcase\n\nShutUp ordering compares rev2 before rev10.\n\n- Hiding only affects the banner.\n- Ignoring only affects notifications.\n- Manual details stay available.",
    url = "https://github.com/Link2011-Act2/Goldy/releases/tag/$tag", prerelease = "IntDev" in tag)

@Composable
internal fun NotificationShowcase(model: NotificationDemoViewModel) {
    val state by model.engine.state.collectAsState()
    var prompt by rememberSaveable { mutableStateOf(false) }
    val selected = model.selected
    if (selected != null) {
        androidx.activity.compose.BackHandler { model.back() }
        UpdateOverviewScreen(state.copy(availableUpdate = selected).toUpdateUiState(), model::back,
            model::link, {}, header = { Text("Offline notification details · no APK is supplied") })
        return
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState())
        .padding(16.dp).testTag("notification-showcase"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Notification showcase", style = MaterialTheme.typography.headlineSmall)
        Text(model.event, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Available", "Up to date", "Network failure", "Slow check").forEachIndexed { index, text ->
                FilterChip(model.scenario == index, { model.scenario(index) }, { Text(text) }, enabled = !model.busy)
            }
        }
        Text("Checks: ${model.checks}", Modifier.testTag("notification-check-count"))
        Text(if (state.availableUpdate == null) "No available update" else "Available: ${state.availableUpdate!!.release.tag}")
        UpdateNotificationBanner(state, model::open, model::hide)
        Row {
            UpdateNotificationIcon(state, { prompt = true })
            Button(onClick = { prompt = true }, modifier = Modifier.testTag("notification-manual-check")) {
                Text("Manual update check")
            }
        }
        Button(onClick = model::check, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Run offline check") }
        OutlinedButton(onClick = model::show, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Show banner again") }
        OutlinedButton(onClick = model::resetIgnored, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Reset ignored versions") }
        OutlinedButton(onClick = model::restore, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Restore cached session") }
        if (state.checking) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = model::cancelCheck) { Text("Cancel check") }
        }
        if (state.lastOutcome is CheckOutcome.Failure) Text("Offline network failure · cached candidate retained",
            color = MaterialTheme.colorScheme.error)
    }
    if (prompt) UpdateCheckDialog(state, model::check,
        onOpenDetails = { candidate -> prompt = false; model.open(candidate) },
        onIgnoreVersion = model::ignore, onDismiss = { prompt = false })
}
