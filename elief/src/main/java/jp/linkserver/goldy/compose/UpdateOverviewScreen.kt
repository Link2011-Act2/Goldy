package jp.linkserver.goldy.compose

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

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
