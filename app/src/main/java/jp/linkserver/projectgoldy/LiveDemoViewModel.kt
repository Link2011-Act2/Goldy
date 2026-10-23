package jp.linkserver.projectgoldy

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.goldy.*
import kotlinx.coroutines.*

/** These repositories are metadata examples, never installation targets. */
internal enum class LiveTarget(val title: String, val repoName: String) {
    ShutUp("ShutUp", "ShutUp"), Scheduler("NITTC Scheduler", "NITTC_Scheduler"),
    Glyph("Glyph Barty", "GlyphBarty");

    val repository get() = GitHubRepository("Link2011-Act2", repoName)
    fun policy() = when (this) {
        ShutUp -> UpdatePolicies.shutUp()
        Scheduler -> UpdatePolicies.nittcScheduler()
        Glyph -> UpdatePolicies.glyphBarty()
    }
}

internal data class LiveSnapshot(
    val catalog: ReleaseCatalog? = null,
    val checkedAt: Long? = null,
    val retryAt: Long? = null,
    val checking: Boolean = false,
    val fromCache: Boolean = false,
    val message: String = "Not fetched. No network request runs until you choose Fetch releases."
)

/** Session cache survives Activity recreation. No APK/downloader/installer is exposed here. */
internal class LiveDemoViewModel(
    private val fetchers: Map<LiveTarget, suspend () -> ReleaseCatalog> = LiveTarget.entries.associateWith {
        val client = GitHubReleaseClient(GoldyConfig(it.repository, it.policy(), userAgent = "Goldy-Demo/0.1"))
        client::fetch
    }
) : ViewModel() {
    var target by mutableStateOf(LiveTarget.ShutUp)
        private set
    private var snapshots by mutableStateOf(emptyMap<LiveTarget, LiveSnapshot>())
    val snapshot get() = snapshots[target] ?: LiveSnapshot()
    var comparisonVersion by mutableStateOf("0.0.0-Release")
    var stableOnly by mutableStateOf(false)
    var selectedId by mutableStateOf<Long?>(null)
        private set
    private var operation: Job? = null

    fun select(value: LiveTarget) {
        if (operation?.isActive == true) return
        target = value
        selectedId = null
        snapshots[value]?.let { put(value, it.copy(fromCache = it.catalog != null)) }
    }

    fun selectRelease(id: Long) { selectedId = id }
    fun policy() = target.policy().let { if (stableOnly) it.copy(channels = ChannelPolicy.StableOnly) else it }
    fun comparison(): CheckOutcome? = snapshot.catalog?.let {
        if (comparisonVersion.isBlank()) CheckOutcome.UnsupportedVersion
        else ReleaseSelector.select(it, InstalledVersion(comparisonVersion.trim(), 0), policy())
    }
    fun selectedRelease(): GitHubRelease? = snapshot.catalog?.releases?.let { releases ->
        releases.firstOrNull { it.id == selectedId }
            ?: (comparison() as? CheckOutcome.Available)?.update?.release ?: releases.firstOrNull()
    }

    fun fetch() {
        if (operation?.isActive == true || snapshot.checking) return
        val key = target
        val old = snapshot
        if ((old.retryAt ?: 0) > System.currentTimeMillis()) {
            put(key, old.copy(message = "Rate limited. Retry after ${java.time.Instant.ofEpochMilli(old.retryAt!!)}."))
            return
        }
        put(key, old.copy(checking = true, fromCache = old.catalog != null, message = "Fetching GitHub releases…"))
        operation = viewModelScope.launch {
            try {
                val catalog = fetchers.getValue(key).invoke()
                put(key, LiveSnapshot(catalog, System.currentTimeMillis(), message = "Fetched from GitHub (conditional HTTP cache may be used)."))
            } catch (cancel: CancellationException) {
                put(key, old.copy(fromCache = old.catalog != null, message = "Fetch cancelled. Previous session cache retained."))
                throw cancel
            } catch (error: Exception) {
                val failure = (error as? GitHubException)?.error ?: GoldyError.Network
                put(key, old.copy(fromCache = old.catalog != null,
                    retryAt = (failure as? GoldyError.Http)?.retryAtMillis, message = metadataError(failure)))
            }
        }
    }

    fun cancel() { operation?.cancel() }
    private fun put(key: LiveTarget, value: LiveSnapshot) { snapshots = snapshots + (key to value) }
}

internal fun metadataError(error: GoldyError): String = when (error) {
    GoldyError.Network -> "Connection failed. Check your network and retry; cached information may be stale."
    GoldyError.InvalidResponse -> "GitHub returned invalid or oversized release information."
    GoldyError.InvalidUrl -> "An unexpected GitHub URL or redirect was rejected."
    GoldyError.Storage -> "Could not read or save update information."
    is GoldyError.Http -> if (error.status == 403 || error.status == 429)
        "GitHub access/rate limit (HTTP ${error.status})." + (error.retryAtMillis?.let { " Retry after ${java.time.Instant.ofEpochMilli(it)}." } ?: " Retry later.")
        else "GitHub request failed (HTTP ${error.status})."
}

internal fun comparisonText(outcome: CheckOutcome?): String = when (outcome) {
    null -> "Fetch releases to compare versions."
    is CheckOutcome.Available -> "Newer release: ${outcome.update.release.tag}"
    CheckOutcome.UpToDate -> "No newer eligible release for the comparison version."
    CheckOutcome.NoPublishedReleases -> "No published releases."
    CheckOutcome.NoEligibleRelease -> "No release matches this channel policy."
    CheckOutcome.UnsupportedVersion -> "Enter a version supported by this repository's Gold policy."
    is CheckOutcome.Incomplete -> "Partial catalog: ${outcome.update?.release?.tag ?: "no candidate in fetched pages"}. History may be incomplete."
    is CheckOutcome.Failure -> metadataError(outcome.error)
    is CheckOutcome.Throttled -> "Rate limited until ${java.time.Instant.ofEpochMilli(outcome.retryAtMillis)}."
    CheckOutcome.Disabled -> "Checks disabled."
}
