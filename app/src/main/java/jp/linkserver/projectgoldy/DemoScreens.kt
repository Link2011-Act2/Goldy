package jp.linkserver.projectgoldy

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import java.time.Instant

@Composable
internal fun LiveDemoScreen(model: LiveDemoViewModel, open: (String) -> Unit) {
    val snapshot = model.snapshot
    key(model.target) {
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding().testTag("live-content"), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Goldy Demo · Live", style = MaterialTheme.typography.headlineSmall)
                Text("Metadata only. APK installation is unavailable.")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveTarget.entries.forEach { target ->
                        FilterChip(model.target == target, { model.select(target) }, { Text(target.title) }, enabled = !snapshot.checking)
                    }
                }
                Text("Link2011-Act2/${model.target.repoName}", style = MaterialTheme.typography.bodySmall)
                Text(if (model.policy().feed == ReleaseFeed.Latest) "Source: latest GitHub release" else "Source: GitHub release list", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = model::fetch, enabled = !snapshot.checking, modifier = Modifier.testTag("live-fetch")) { Text("Fetch releases") }
                    if (snapshot.checking) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        OutlinedButton(onClick = model::cancel) { Text("Cancel fetch") }
                    }
                    Text(snapshot.message, Modifier.testTag("live-status"))
                    Text(if (snapshot.catalog == null) "Cache: empty" else
                        "Cache: ${if (snapshot.fromCache) "session cache / may be stale" else "last successful fetch"} · ${snapshot.checkedAt?.let(Instant::ofEpochMilli)}")
                    Text("Cache is kept for this session, including Activity recreation. A process restart requires a new fetch.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                OutlinedTextField(model.comparisonVersion, { model.comparisonVersion = it.take(128) },
                    label = { Text("Comparison version (not installed app)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FilterChip(model.stableOnly, { model.stableOnly = !model.stableOnly }, { Text("Stable releases only") })
                Text(comparisonText(model.comparison()), Modifier.testTag("live-comparison"))
                if (snapshot.catalog?.complete == false) Text("Fetched pages are incomplete. This is not a complete release history.")
            }
            item {
                val releases = snapshot.catalog?.releases.orEmpty().filter(model.policy().releaseFilter)
                if (releases.isNotEmpty()) {
                    Text("Browse fetched releases", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        releases.forEach { release ->
                            FilterChip(model.selectedRelease()?.id == release.id, { model.selectRelease(release.id) }, { Text(release.tag) })
                        }
                    }
                }
            }
            model.selectedRelease()?.let { release ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(release.tag, style = MaterialTheme.typography.titleLarge)
                            Text("Channel: ${model.target.policy().versions.channel(release.tag, release.prerelease)} · prerelease=${release.prerelease}")
                            Text("Published: ${release.publishedAt ?: "unknown"}")
                            Text("Assets (${release.assets.size}) · metadata, not verified APKs", style = MaterialTheme.typography.titleSmall)
                            release.assets.forEach { asset ->
                                Text("${asset.name} · ${asset.size?.let { "$it bytes" } ?: "unknown size"}\n${asset.digest ?: "No published digest"}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { open(release.url) }) { Text("Open release on GitHub") }
                        }
                    }
                }
                item {
                    Text("Release notes", style = MaterialTheme.typography.titleLarge)
                    ReleaseNotes(release.notes, open, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun UpdateDemoScreen(
    model: DemoUpdateViewModel,
    back: () -> Unit,
    open: (String) -> Unit,
    install: () -> Unit,
    resume: (InstallRequest) -> Unit,
    permission: () -> Unit
) {
    var releaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    releaseId?.let { id ->
        AlertDialog(onDismissRequest = { releaseId = null }, title = { Text("Release installer file?") },
            text = { Text("Close the Android installer and ensure every installer/URI reader has finished. Releasing now revokes URI access and deletes the staged APK. Returning to this app alone is not sufficient.") },
            confirmButton = { TextButton(onClick = {
                model.pending.firstOrNull { it.id == id }?.let(model::releaseAfterReadersFinished)
                releaseId = null
            }) { Text("All readers finished — release") } },
            dismissButton = { TextButton(onClick = { releaseId = null }) { Text("Keep file") } })
    }
    UpdateOverviewScreen(model.ui, back, open, { action ->
        when (action) {
            UpdateAction.Download -> model.download()
            UpdateAction.CancelDownload -> model.cancel()
            UpdateAction.Install -> install()
            UpdateAction.OpenInstallPermissionSettings -> permission()
        }
    }, header = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Goldy Demo · self update", style = MaterialTheme.typography.titleLarge)
            Text("Updates only Goldy Demo. Opening the installer does not confirm installation.")
            model.identity?.let { identity ->
                Text("${identity.packageName}\n${identity.versionName} · versionCode ${identity.versionCode}", Modifier.testTag("demo-identity"), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = model::check, enabled = model.canCheck, modifier = Modifier.testTag("demo-check")) { Text("Check Demo update") }
            if (model.metadata.checking) OutlinedButton(onClick = model::cancel) { Text("Cancel check") }
            Text("Install permission: ${model.permission}")
            TextButton(onClick = permission, enabled = !model.busy) { Text("Installation source settings") }
            Text("${if (model.metadata.fromCache) "Cached" else "Last fetched"}: ${model.metadata.lastSuccessMillis?.let(Instant::ofEpochMilli) ?: "never"}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Hide signing / distribution details" else "Show signing / distribution details") }
            if (showDetails) {
                model.identity?.let { identity ->
                    Text("Signing certificate SHA-256:\n${identity.signers.currentSha256.sorted().joinToString("\n")}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Distribution: ${DemoDistribution.repository.url}\nTag v<version>-Release · one goldy-demo-*.apk", style = MaterialTheme.typography.bodySmall)
            }
            if (model.restoring) CircularProgressIndicator()
            if (model.restorationFailed) OutlinedButton(onClick = model::restore) { Text("Retry restoration") }
            if (model.canDiscard) OutlinedButton(onClick = model::discardDownload) { Text("Discard verified download") }
            model.pending.forEach { request ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Retained installer request · ${request.metadata.versionName} (${request.metadata.versionCode})")
                        Text("${request.metadata.packageName}\n${request.id}", style = MaterialTheme.typography.bodySmall)
                        Text("Installation unconfirmed. The file is retained across Activity/process recreation.")
                        OutlinedButton(onClick = { resume(request) }, enabled = !model.busy) { Text("Retry installer explicitly") }
                        TextButton(onClick = { releaseId = request.id }, enabled = !model.busy) { Text("Release file after installer finishes…") }
                    }
                }
            }
        }
    })
}
