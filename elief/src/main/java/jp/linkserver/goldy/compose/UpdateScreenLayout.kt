package jp.linkserver.goldy.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import jp.linkserver.goldy.ApkProgress
import jp.linkserver.goldy.GitHubRelease

/** Display/events only; operation, verified file and installer ownership stays in the host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateScreenLayout(
    state: UpdateUiState, onBack: () -> Unit, onOpenUrl: (String) -> Unit, onAction: (UpdateAction) -> Unit,
    modifier: Modifier, strings: UpdateStrings, channelLabel: String,
    loadingIndicator: @Composable () -> Unit, header: @Composable (UpdateUiState) -> Unit,
    presentation: UpdatePresentation
) {
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        val inlineActions = LocalDensity.current.fontScale >= 1.5f || maxHeight < 480.dp
        val maxActionsHeight = maxHeight * 0.4f
        // Layout adaptation must not reset the release's scroll/expansion state.
        key(state.update?.release?.id) {
            val contentScroll = rememberScrollState()
            val actionsScroll = rememberScrollState()
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                topBar = {
                    TopAppBar(title = { Text(strings.title, style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = strings.back }) {
                            presentation.backIcon()
                        }
                    })
                },
                bottomBar = {
                    if (!inlineActions && state.update != null) {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
                                    Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().heightIn(max = maxActionsHeight)
                                        .verticalScroll(actionsScroll).testTag("goldy-docked-actions").padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        TransferContent(state, strings, onAction, onOpenUrl, loadingIndicator)
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(contentScroll)
                        .testTag("goldy-content").padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        header(state)
                        if (state.checking) {
                            loadingIndicator()
                            StatusText(strings.checking)
                        }
                        val candidate = state.update
                        if (candidate == null) {
                            if (!state.checking) Text(state.message ?: strings.noUpdate)
                        } else {
                            VersionRow(presentation.versionLabel(candidate.release), channelLabel, prominent = true)
                            state.message?.let { Text(it) }
                            if (inlineActions) {
                                Surface(shape = updateCardShape(), color = updateCardColor(),
                                    modifier = Modifier.fillMaxWidth().testTag("goldy-inline-actions")) {
                                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        TransferContent(state, strings, onAction, onOpenUrl, loadingIndicator)
                                    }
                                }
                            }
                            Text(strings.releaseNotes, Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Surface(shape = updateCardShape(), color = updateCardColor(),
                                modifier = Modifier.fillMaxWidth().testTag("latest-release-notes")) {
                                ReleaseNotes(candidate.release.notes, onOpenUrl, Modifier.fillMaxWidth().padding(22.dp), strings.noNotes)
                            }
                            val history = candidate.intermediateReleases.distinctBy { it.id }
                            if (history.isNotEmpty()) History(history, presentation, strings, onOpenUrl)
                            if (!candidate.catalogComplete) Text(strings.incompleteHistory)
                            if (state.apkAvailable) ReleaseLink(candidate.release, strings, onOpenUrl)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(version: String, channel: String, prominent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(version, Modifier.weight(1f),
            style = if (prominent) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleSmall)
        // Noninteractive display chip: no no-op button in the accessibility tree.
        Surface(shape = MaterialTheme.shapes.extraSmall, color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text(channel, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun History(releases: List<GitHubRelease>, presentation: UpdatePresentation, strings: UpdateStrings, open: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "history arrow")
    Surface(shape = updateCardShape(), color = updateCardColor(), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().testTag("history-toggle")
                .semantics { stateDescription = if (expanded) strings.collapse else strings.expand }
                .clickable(role = Role.Button, onClickLabel = if (expanded) strings.collapse else strings.expand) { expanded = !expanded }
                .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${strings.history} (${releases.size})", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium)
                Icon(painterResource(R.drawable.elief_expand_more), contentDescription = null,
                    modifier = Modifier.rotate(angle))
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    releases.forEach { release ->
                        key(release.id) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth().testTag("history-${release.id}")) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    VersionRow(presentation.versionLabel(release), presentation.historyChannelLabel(release))
                                    ReleaseNotes(release.notes, open, Modifier.fillMaxWidth(), strings.noNotes)
                                    ReleaseLink(release, strings, open)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseLink(release: GitHubRelease, strings: UpdateStrings, open: (String) -> Unit) {
    TextButton(onClick = { if (safeWebUrl(release.url)) open(release.url) }, enabled = safeWebUrl(release.url)) { Text(strings.openRelease) }
}

@Composable
private fun StatusText(text: String) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TransferContent(state: UpdateUiState, s: UpdateStrings, action: (UpdateAction) -> Unit,
                            open: (String) -> Unit, loading: @Composable () -> Unit) {
    val release = state.update?.release ?: return
    if (!state.apkAvailable && state.transfer == UpdateTransferState.Idle) {
        Button(onClick = { if (safeWebUrl(release.url)) open(release.url) }, enabled = safeWebUrl(release.url), modifier = Modifier.primaryAction()) { Text(s.openRelease) }
        Text(s.noApk, style = MaterialTheme.typography.bodySmall)
        return
    }
    when (val transfer = state.transfer) {
        is UpdateTransferState.Downloading -> {
            OutlinedButton(onClick = { action(UpdateAction.CancelDownload) }, modifier = Modifier.fillMaxWidth()) { Text(s.cancel) }
            StatusText(s.downloading)
            DownloadProgress(transfer.progress)
        }
        UpdateTransferState.Verifying -> { Box(Modifier.size(24.dp)) { loading() }; StatusText(s.verifying) }
        UpdateTransferState.PreparingInstall -> { Box(Modifier.size(24.dp)) { loading() }; StatusText(s.preparing) }
        UpdateTransferState.Ready -> {
            Button(onClick = { action(UpdateAction.Install) }, modifier = Modifier.primaryAction()) { Text(s.install) }
            StatusText(s.ready)
        }
        UpdateTransferState.PermissionRequired -> {
            Button(onClick = { action(UpdateAction.OpenInstallPermissionSettings) }, modifier = Modifier.primaryAction()) { Text(s.allowInstall) }
            OutlinedButton(onClick = { action(UpdateAction.Install) }, modifier = Modifier.fillMaxWidth()) { Text(s.retry) }
            StatusText(s.permissionRequired)
        }
        UpdateTransferState.HandedOff -> StatusText(s.handedOff)
        is UpdateTransferState.Failed -> {
            transfer.retry?.let { retry ->
                Button(onClick = { action(retry) }, enabled = retry != UpdateAction.Download || state.apkAvailable, modifier = Modifier.primaryAction()) { Text(s.retry) }
            }
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("update-error")) {
                Text(transfer.message, Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        UpdateTransferState.Cancelled -> {
            if (state.apkAvailable) Button(onClick = { action(UpdateAction.Download) }, modifier = Modifier.primaryAction()) { Text(s.retry) }
            StatusText(s.cancelled)
        }
        UpdateTransferState.Idle -> if (state.apkAvailable) {
            Button(onClick = { action(UpdateAction.Download) }, enabled = !state.checking, modifier = Modifier.primaryAction()) { Text(s.download) }
        }
    }
    if (!state.apkAvailable) ReleaseLink(release, s, open)
}

private fun Modifier.primaryAction() = this.fillMaxWidth().heightIn(min = 56.dp)

@Composable
private fun updateCardColor() = LocalUpdateCardStyle.current.containerColor.let {
    if (it == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerHigh else it
}

@Composable
private fun updateCardShape(): Shape = LocalUpdateCardStyle.current.shape ?: MaterialTheme.shapes.large

@Composable
private fun DownloadProgress(progress: ApkProgress) {
    val total = progress.total?.takeIf { it > 0L }
    if (total == null) LinearProgressIndicator(Modifier.fillMaxWidth())
    else LinearProgressIndicator(progress = { (progress.bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    Text(if (total == null) "${progress.bytes} B · #${progress.attempt}" else "${progress.bytes} / $total B · #${progress.attempt}",
        style = MaterialTheme.typography.labelMedium)
}
