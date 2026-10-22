package jp.linkserver.goldy.compose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import jp.linkserver.goldy.ApkProgress
import jp.linkserver.goldy.GitHubRelease

/**
 * Stateless update operations. Saveable scroll/history belong to the current release.
 * Callbacks must synchronously claim an operation in the host before launching a coroutine.
 * No InstallRequest is owned or released here. Host handles system back as well as onBack.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(strings.title) }, navigationIcon = {
            TextButton(onClick = onBack) { Text(strings.back) }
        }) }
    ) { padding ->
        // Key the saveable scroll and expanded history to the selected release.
        key(state.update?.release?.id) {
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).testTag("goldy-content")
                .padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                header(state)
                val candidate = state.update
                if (state.checking) {
                    loadingIndicator()
                    Text(strings.checking, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (candidate == null) {
                    if (!state.checking) Text(state.message ?: strings.noUpdate)
                } else {
                    Text(candidate.release.tag, style = MaterialTheme.typography.headlineMedium)
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(channelLabel, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    state.message?.let { Text(it) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TransferContent(state, strings, onAction, loadingIndicator)
                            ReleaseLink(candidate.release, strings, onOpenUrl)
                        }
                    }
                    Text(strings.releaseNotes, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    ReleaseNotes(candidate.release.notes, onOpenUrl, Modifier.fillMaxWidth(), strings.noNotes)
                    if (candidate.intermediateReleases.isNotEmpty()) {
                        HorizontalDivider()
                        Text(strings.history, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        candidate.intermediateReleases.distinctBy { it.id }.forEach { release ->
                            key(release.id) {
                                var expanded by rememberSaveable { mutableStateOf(false) }
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(release.tag, style = MaterialTheme.typography.titleMedium)
                                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("history-" + release.id)
                                            .semantics { stateDescription = if (expanded) strings.collapse else strings.expand }) {
                                            Text(if (expanded) strings.collapse else strings.expand)
                                        }
                                        if (expanded) {
                                            ReleaseNotes(release.notes, onOpenUrl, Modifier.fillMaxWidth(), strings.noNotes)
                                            ReleaseLink(release, strings, onOpenUrl)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!candidate.catalogComplete) Text(strings.incompleteHistory)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ReleaseLink(release: GitHubRelease, strings: UpdateStrings, open: (String) -> Unit) {
    TextButton(onClick = { if (safeWebUrl(release.url)) open(release.url) }, enabled = safeWebUrl(release.url)) {
        Text(strings.openRelease)
    }
}

@Composable
private fun TransferContent(state: UpdateUiState, s: UpdateStrings, action: (UpdateAction) -> Unit,
                            loading: @Composable () -> Unit) {
    if (!state.apkAvailable && state.transfer == UpdateTransferState.Idle) {
        Text(s.noApk)
        return
    }
    val transfer = state.transfer
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (transfer) {
            is UpdateTransferState.Downloading -> {
                Text(s.downloading, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                DownloadProgress(transfer.progress)
                OutlinedButton(onClick = { action(UpdateAction.CancelDownload) }) { Text(s.cancel) }
            }
            UpdateTransferState.Verifying -> { loading(); Text(s.verifying) }
            UpdateTransferState.PreparingInstall -> { loading(); Text(s.preparing) }
            UpdateTransferState.Ready -> {
                Text(s.ready)
                Button(onClick = { action(UpdateAction.Install) }) { Text(s.install) }
            }
            UpdateTransferState.PermissionRequired -> {
                Text(s.permissionRequired)
                Button(onClick = { action(UpdateAction.OpenInstallPermissionSettings) }) { Text(s.allowInstall) }
                OutlinedButton(onClick = { action(UpdateAction.Install) }) { Text(s.retry) }
            }
            UpdateTransferState.HandedOff -> Text(s.handedOff, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            is UpdateTransferState.Failed -> {
                Text(transfer.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error)
                transfer.retry?.let { retry -> Button(onClick = { action(retry) }, enabled = retry != UpdateAction.Download || state.apkAvailable) { Text(s.retry) } }
            }
            UpdateTransferState.Cancelled -> {
                Text(s.cancelled)
                if (state.apkAvailable) Button(onClick = { action(UpdateAction.Download) }) { Text(s.retry) }
            }
            UpdateTransferState.Idle -> if (state.apkAvailable) {
                Button(onClick = { action(UpdateAction.Download) }, enabled = !state.checking) { Text(s.download) }
            }
        }
    }
}

@Composable
private fun DownloadProgress(progress: ApkProgress) {
    val total = progress.total?.takeIf { it > 0L }
    if (total == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { (progress.bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    Text(if (total == null) "${progress.bytes} B · #${progress.attempt}" else "${progress.bytes} / $total B · #${progress.attempt}",
        style = MaterialTheme.typography.labelMedium)
}
