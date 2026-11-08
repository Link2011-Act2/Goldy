package jp.linkserver.goldy.compose

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import jp.linkserver.goldy.ApkMetadata

/** Original entry point and JVM signature retained for existing source and binary consumers. */
@Composable
fun UpdateOverviewScreen(
    state: UpdateUiState,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAction: (UpdateAction) -> Unit,
    modifier: Modifier = Modifier,
    strings: UpdateStrings = UpdateStrings(),
    channelLabel: String = state.channel.name,
    loadingIndicator: @Composable () -> Unit = { CircularProgressIndicator() },
    header: @Composable (UpdateUiState) -> Unit = {}
) {
    UpdateScreenLayout(state, onBack, onOpenUrl, onAction, modifier, strings, channelLabel,
        loadingIndicator, header, remember { UpdatePresentation() })
}

/**
 * Continue an already prepared installation after process restoration, even without release
 * metadata. This is display only: the host still launches/revalidates its persisted request.
 * No fabricated Release or downloaded-file management UI is needed.
 */
@Composable
fun UpdateOverviewScreen(
    preparedApk: ApkMetadata,
    transfer: UpdateTransferState,
    onBack: () -> Unit,
    onAction: (UpdateAction) -> Unit,
    modifier: Modifier = Modifier,
    strings: UpdateStrings = UpdateStrings(),
    message: String? = null,
    channelLabel: String = "",
    loadingIndicator: @Composable () -> Unit = { CircularProgressIndicator() },
    header: @Composable (UpdateUiState) -> Unit = {},
    presentation: UpdatePresentation = remember { UpdatePresentation() }
) {
    UpdateScreenLayout(UpdateUiState(transfer = transfer, message = message), onBack, {}, onAction,
        modifier, strings, channelLabel, loadingIndicator, header, presentation, preparedApk)
}

/** Optional display configuration; all update operations remain owned by the host. */
@Composable
fun UpdateOverviewScreen(
    state: UpdateUiState,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAction: (UpdateAction) -> Unit,
    modifier: Modifier = Modifier,
    strings: UpdateStrings = UpdateStrings(),
    channelLabel: String = state.channel.name,
    loadingIndicator: @Composable () -> Unit = { CircularProgressIndicator() },
    header: @Composable (UpdateUiState) -> Unit = {},
    presentation: UpdatePresentation
) {
    UpdateScreenLayout(state, onBack, onOpenUrl, onAction, modifier, strings, channelLabel,
        loadingIndicator, header, presentation)
}
