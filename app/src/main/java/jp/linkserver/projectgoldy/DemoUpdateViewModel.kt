package jp.linkserver.projectgoldy

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Only releases intentionally named for this Demo may become download candidates. */
internal object DemoDistribution {
    val repository = GitHubRepository("Link2011-Act2", "Goldy")
    val selection = ApkSelectionPolicy { release -> release.assets.filter {
        it.name.startsWith("goldy-demo-") && it.name.endsWith(".apk", true)
    }.singleOrNull() }
    val binding = ApkVersionBinding { release, apk ->
        release.tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+-Release")) &&
            release.tag == "v" + apk.versionName
    }
    val policy = UpdatePolicies.Standard.copy(id = "goldy-demo-v1", channels = ChannelPolicy.StableOnly,
        feed = ReleaseFeed.ListReleases(30, 1),
        releaseFilter = { it.tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+-Release")) && selection.select(it) != null })
}

/** Main-thread commands; verified source files and persisted installer leases have different lifetimes. */
internal class DemoUpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val goldy = Goldy.create(app, GoldyConfig(DemoDistribution.repository, DemoDistribution.policy))
    private val downloader = ApkDownloader.create(app, ApkDownloadConfig(DemoDistribution.repository,
        DemoDistribution.selection, DemoDistribution.binding))
    private val installer = ApkInstaller.create(app)
    var metadata by mutableStateOf(GoldyState())
        private set
    var identity by mutableStateOf<ApkMetadata?>(null)
        private set
    var transfer by mutableStateOf<UpdateTransferState>(UpdateTransferState.Idle)
        private set
    var pending by mutableStateOf(emptyList<InstallRequest>())
        private set
    var restoring by mutableStateOf(false)
        private set
    var restorationFailed by mutableStateOf(false)
        private set
    var installing by mutableStateOf(false)
        private set
    var notice by mutableStateOf("Not configured until a matching signed Demo APK is published. Check explicitly to query GitHub.")
        private set
    var permission by mutableStateOf(installer.permissionStatus())
        private set
    private var pinned: UpdateCandidate? by mutableStateOf(null)
    private var verified: VerifiedApk? = null
    private var request: InstallRequest? = null
    private var operation: Job? = null
    val busy get() = restoring || installing || operation?.isActive == true
    val canCheck get() = !busy && !restorationFailed && pinned == null && verified == null && request == null
    val canDiscard get() = !busy && pinned != null && request == null
    val ui get() = metadata.copy(availableUpdate = pinned ?: metadata.availableUpdate).toUpdateUiState(
        DemoDistribution.policy.versions, DemoDistribution.selection, transfer, notice)

    init {
        viewModelScope.launch { goldy.state.collect { metadata = it } }
        restore()
    }

    fun restore() {
        if (busy || verified != null || request != null) return
        restoring = true
        restorationFailed = false
        viewModelScope.launch {
            try {
                identity = withContext(Dispatchers.IO) { AndroidApkInspector(getApplication()).installed() }
                pending = installer.pendingRequests()
                goldy.restoreCachedState()
                if (pending.isNotEmpty()) notice = "Restored ${pending.size} installer request(s). Installation is unconfirmed."
            } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) {
                restorationFailed = true
                notice = "Could not restore identity/cache/installer requests. Retry restoration before continuing."
            }
            finally { restoring = false }
        }
    }

    fun refreshPermission() { permission = installer.permissionStatus() }
    fun settingsIntent(): Intent = installer.unknownSourcesSettingsIntent()
    fun navigationFailed() { notice = "Android could not open the requested screen. No installation was confirmed." }

    fun check() {
        if (!canCheck) return
        operation = viewModelScope.launch {
            notice = "Checking Demo distribution…"
            try {
                notice = when (val outcome = goldy.check(CheckTrigger.Manual)) {
                    CheckOutcome.NoPublishedReleases, CheckOutcome.NoEligibleRelease ->
                        "Not configured: no eligible Goldy Demo APK release. No file has been downloaded."
                    else -> comparisonText(outcome)
                }
            } catch (cancel: CancellationException) { notice = "Check cancelled. Cached information retained."; throw cancel }
        }
    }

    fun download() {
        if (busy || restorationFailed || verified != null || request != null || identity == null) return
        val candidate = pinned ?: metadata.availableUpdate ?: return
        pinned = candidate
        transfer = UpdateTransferState.Downloading(ApkProgress(0, null, 1))
        operation = viewModelScope.launch {
            try {
                verified = coroutineScope {
                    // Conflation bounds UI work; this child cannot outlive the transfer or overwrite Ready.
                    val progress = Channel<ApkProgress>(Channel.CONFLATED)
                    val collector = launch { for (value in progress) transfer = UpdateTransferState.Downloading(value) }
                    try { downloader.download(candidate.release) { progress.trySend(it) } }
                    finally { progress.close(); collector.cancelAndJoin() }
                }
                transfer = UpdateTransferState.Ready
                notice = "Verified this Demo's package, current signer, versionCode and release version. Not installed."
            } catch (cancel: CancellationException) {
                transfer = UpdateTransferState.Cancelled
                throw cancel
            } catch (error: ApkException) {
                transfer = UpdateTransferState.Failed("Download/verification rejected: ${error.reason}. No installer was opened.")
            } catch (_: Exception) {
                transfer = UpdateTransferState.Failed("Could not store or download the APK. No installer was opened.")
            }
        }
    }

    fun cancel() { if (!installing) operation?.cancel() }

    /** Invoked in the current Activity's lifecycleScope, never retaining an Activity in this ViewModel. */
    suspend fun install(startActivity: (Intent) -> Unit) {
        if (busy || transfer == UpdateTransferState.HandedOff) return
        if (verified == null && request == null) return
        installing = true
        transfer = UpdateTransferState.PreparingInstall
        try {
            if (request == null) when (val prepared = installer.prepare(requireNotNull(verified))) {
                is InstallPreparation.Ready -> {
                    request = prepared.request
                    pending = pending + prepared.request
                    val source = verified
                    verified = null
                    withContext(Dispatchers.IO) { source?.file?.delete() }
                }
                is InstallPreparation.PermissionRequired -> { transfer = UpdateTransferState.PermissionRequired; return }
                is InstallPreparation.Failed -> { installFailed(prepared.error); return }
            }
            launchRequest(requireNotNull(request), startActivity)
        } finally {
            installing = false
            if (transfer == UpdateTransferState.PreparingInstall) transfer = UpdateTransferState.Ready
        }
    }

    suspend fun resumePending(value: InstallRequest, startActivity: (Intent) -> Unit) {
        if (busy || verified != null || pending.none { it.id == value.id }) return
        installing = true
        request = value
        transfer = UpdateTransferState.PreparingInstall
        try { launchRequest(value, startActivity) }
        finally {
            installing = false
            if (transfer == UpdateTransferState.PreparingInstall) transfer = UpdateTransferState.Ready
        }
    }

    private suspend fun launchRequest(value: InstallRequest, startActivity: (Intent) -> Unit) {
        when (val result = installer.launch(value, startActivity)) {
            InstallLaunchResult.HandedOff -> {
                transfer = UpdateTransferState.HandedOff
                notice = "Installer opened. Installation is UNCONFIRMED; returning here does not mean success."
            }
            is InstallLaunchResult.PermissionRequired -> {
                transfer = UpdateTransferState.PermissionRequired
                notice = "Allow this Demo as an installation source, then retry explicitly."
            }
            is InstallLaunchResult.Failed -> installFailed(result.error)
        }
        refreshPermission()
    }

    private fun installFailed(error: InstallError) {
        notice = "Installer rejected: ${error.reason}" + (error.apkFailure?.let { " / $it" } ?: "")
        transfer = UpdateTransferState.Failed(notice, UpdateAction.Install)
    }

    fun discardDownload() {
        if (!canDiscard) return
        val source = verified
        verified = null
        pinned = null
        transfer = UpdateTransferState.Idle
        notice = "Current download discarded. You can check for another Demo update."
        if (source != null) operation = viewModelScope.launch { withContext(Dispatchers.IO) { source.file.delete() } }
    }

    /** UI must first ask the user to confirm that all installer/URI readers have finished. */
    fun releaseAfterReadersFinished(value: InstallRequest) {
        if (busy || pending.none { it.id == value.id }) return
        operation = viewModelScope.launch {
            try {
                installer.release(value)
                pending = pending.filterNot { it.id == value.id }
                if (request?.id == value.id) {
                    request = null
                    pinned = null
                    transfer = UpdateTransferState.Idle
                }
                notice = "Installer file released after your confirmation. Installation remains unconfirmed."
            } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) { notice = "Could not release the installer file. Request retained for retry." }
        }
    }

    override fun onCleared() {
        // Only the unshared download source. Never release a staged InstallRequest here.
        verified?.file?.delete()
    }
}
