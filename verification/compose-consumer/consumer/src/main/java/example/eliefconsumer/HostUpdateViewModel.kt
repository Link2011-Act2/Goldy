package example.eliefconsumer

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Integration example. Construct with a host factory and the matching three policies. */
class HostUpdateViewModel(
    app: Application,
    repository: GitHubRepository,
    policy: UpdatePolicy,
    selection: ApkSelectionPolicy,
    private val binding: ApkVersionBinding
) : AndroidViewModel(app) {
    val goldy = Goldy.create(app, GoldyConfig(repository, policy))
    private val downloader = ApkDownloader.create(app, ApkDownloadConfig(repository, selection, binding))
    private val installer = ApkInstaller.create(app)
    private val transfer = MutableStateFlow<UpdateTransferState>(UpdateTransferState.Idle)
    private val pinned = MutableStateFlow<UpdateCandidate?>(null)
    val ui = combine(goldy.state, transfer, pinned) { metadata, operation, selected ->
        metadata.copy(availableUpdate = selected ?: metadata.availableUpdate).toUpdateUiState(policy.versions, selection, operation,
            message = if (metadata.lastOutcome is CheckOutcome.Failure) "Could not check for updates." else null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UpdateUiState())
    private var job: Job? = null
    private var installing = false
    private var verified: VerifiedApk? = null
    private var request: InstallRequest? = null

    init { viewModelScope.launch { goldy.restoreCachedState() } }
    fun check() { viewModelScope.launch { goldy.check(CheckTrigger.Manual) } }

    /** Main-thread calls. Claim state synchronously before the coroutine can start. */
    fun download() {
        if (job?.isActive == true || installing || verified != null || request != null) return
        val candidate = pinned.value ?: goldy.state.value.availableUpdate ?: return
        pinned.value = candidate
        val release = candidate.release
        transfer.value = UpdateTransferState.Downloading(ApkProgress(0, null, 1))
        job = viewModelScope.launch {
            try {
                verified = downloader.download(release) { transfer.value = UpdateTransferState.Downloading(it) }
                transfer.value = UpdateTransferState.Ready
            } catch (cancel: CancellationException) {
                transfer.value = UpdateTransferState.Cancelled
                throw cancel
            } catch (error: ApkException) {
                // Replace with localized, user-oriented messages in the app.
                transfer.value = UpdateTransferState.Failed("APK rejected or download failed: " + error.reason)
            }
        }
    }
    fun cancel() { job?.cancel() }
    fun settingsIntent(): Intent = installer.unknownSourcesSettingsIntent()

    /** Call in the Activity's lifecycleScope. Gold revalidates immediately before this host callback. */
    suspend fun install(startActivity: (Intent) -> Unit) {
        if (installing || job?.isActive == true || transfer.value == UpdateTransferState.HandedOff) return
        val apk = verified
        if (apk == null && request == null) return
        installing = true
        transfer.value = UpdateTransferState.PreparingInstall
        try {
            if (request == null) when (val prepared = installer.prepare(requireNotNull(apk))) {
                is InstallPreparation.Ready -> {
                    request = prepared.request
                    withContext(Dispatchers.IO) { apk.file.delete() }
                    verified = null
                }
                is InstallPreparation.PermissionRequired -> { transfer.value = UpdateTransferState.PermissionRequired; return }
                is InstallPreparation.Failed -> { fail(prepared.error); return }
            }
            when (val launched = installer.launch(requireNotNull(request), startActivity)) {
                InstallLaunchResult.HandedOff -> transfer.value = UpdateTransferState.HandedOff
                is InstallLaunchResult.PermissionRequired -> transfer.value = UpdateTransferState.PermissionRequired
                is InstallLaunchResult.Failed -> fail(launched.error)
            }
        } finally {
            installing = false
            // Cancellation does not release an installer lease or imply installation success.
            if (transfer.value == UpdateTransferState.PreparingInstall) transfer.value = UpdateTransferState.Ready
        }
    }
    private fun fail(error: InstallError) {
        transfer.value = UpdateTransferState.Failed("Installer could not be opened: " + error.reason, UpdateAction.Install)
    }
    /** After process death: present pending requests for an explicit host/user decision. */
    suspend fun pending(): List<InstallRequest> = installer.pendingRequests()
    fun selectPending(pending: InstallRequest, candidate: UpdateCandidate) {
        require(binding.matches(candidate.release, pending.metadata))
        check(!installing && job?.isActive != true)
        pinned.value = candidate
        request = pending
        transfer.value = UpdateTransferState.Ready
    }
    /** Host must confirm that no installer/reader still needs this request. Never call onDispose/onResume. */
    suspend fun releaseAfterInstallerFinished() {
        val pending = request ?: return
        installer.release(pending)
        request = null
        pinned.value = null
        transfer.value = UpdateTransferState.Idle
    }
}
