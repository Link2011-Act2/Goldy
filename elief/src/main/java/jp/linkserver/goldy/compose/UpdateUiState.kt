package jp.linkserver.goldy.compose

import jp.linkserver.goldy.*

/** Commands only. The host owns Jobs, verified files, permissions and InstallRequest leases. */
enum class UpdateAction { Download, CancelDownload, Install, OpenInstallPermissionSettings }

sealed interface UpdateTransferState {
    data object Idle : UpdateTransferState
    data class Downloading(val progress: ApkProgress) : UpdateTransferState
    data object Verifying : UpdateTransferState
    data object Ready : UpdateTransferState
    data object PreparingInstall : UpdateTransferState
    data object PermissionRequired : UpdateTransferState
    /** Only an Activity handoff, never evidence of installation success. */
    data object HandedOff : UpdateTransferState
    data object Cancelled : UpdateTransferState
    data class Failed(val message: String, val retry: UpdateAction? = UpdateAction.Download) : UpdateTransferState
}

data class UpdateUiState(
    val update: UpdateCandidate? = null,
    val channel: ReleaseChannel = ReleaseChannel.Unknown,
    val apkAvailable: Boolean = false,
    val checking: Boolean = false,
    val transfer: UpdateTransferState = UpdateTransferState.Idle,
    /** Localized metadata error or empty-state message, supplied by the host. */
    val message: String? = null
)

/** Uses the host downloader's policies. This does not authenticate an APK. */
fun GoldyState.toUpdateUiState(
    versionPolicy: VersionPolicy = VersionPolicies.Standard,
    apkSelection: ApkSelectionPolicy = ApkSelectionPolicies.Single,
    transfer: UpdateTransferState = UpdateTransferState.Idle,
    message: String? = null
): UpdateUiState = UpdateUiState(
    update = availableUpdate,
    channel = availableUpdate?.release?.let { versionPolicy.channel(it.tag, it.prerelease) } ?: ReleaseChannel.Unknown,
    apkAvailable = availableUpdate?.release?.let { apkSelection.select(it) != null } == true,
    checking = checking, transfer = transfer, message = message
)

/** HTTP(S) only, with a host and no credentials. No link is launched by Elief. */
internal fun safeWebUrl(value: String): Boolean = try {
    val uri = java.net.URI(value)
    uri.scheme?.lowercase(java.util.Locale.ROOT) in setOf("https", "http") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null && value.none { it.isISOControl() }
} catch (_: Exception) { false }
