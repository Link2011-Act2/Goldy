package jp.linkserver.projectgoldy

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import kotlinx.coroutines.*

/** Offline demonstration. No network, APK, permissions or installer is used by fixture actions. */
class FixtureUpdateViewModel : ViewModel() {
    var scenario by mutableIntStateOf(0)
        private set
    var state by mutableStateOf(fixture(0))
        private set
    var event by mutableStateOf("Offline fixtures · no APK is installed")
    var downloadStarts = 0
        private set
    private var operation: Job? = null

    fun select(index: Int) {
        operation?.cancel()
        operation = null
        scenario = index
        state = fixture(index)
    }

    fun action(action: UpdateAction) {
        // Claim synchronously on Main, before launching: even two taps in one frame start one job.
        when (action) {
            UpdateAction.Download -> {
                if (operation?.isActive == true || state.transfer is UpdateTransferState.Downloading) return
                downloadStarts++
                state = state.copy(transfer = UpdateTransferState.Downloading(ApkProgress(0, 100, 1)))
                operation = viewModelScope.launch {
                    try {
                        for (step in 1..10) {
                            delay(300)
                            state = state.copy(transfer = UpdateTransferState.Downloading(ApkProgress(step * 10L, 100, 1)))
                        }
                        state = state.copy(transfer = UpdateTransferState.Verifying)
                        delay(300)
                        state = state.copy(transfer = UpdateTransferState.Ready)
                    } catch (cancel: CancellationException) {
                        // select() owns replacement state; cancel button sets Cancelled synchronously.
                        throw cancel
                    }
                }
            }
            UpdateAction.CancelDownload -> {
                operation?.cancel()
                operation = null
                state = state.copy(transfer = UpdateTransferState.Cancelled)
            }
            UpdateAction.Install -> {
                if (state.transfer == UpdateTransferState.PreparingInstall || state.transfer == UpdateTransferState.HandedOff) return
                state = state.copy(transfer = UpdateTransferState.HandedOff)
                event = "Fixture handoff only · no Activity launched"
            }
            UpdateAction.OpenInstallPermissionSettings -> event = "Fixture permission guide · no settings Activity launched"
        }
    }
}

val scenarioNames = listOf("Update", "No update", "Downloading", "Unknown size", "Failure", "Permission", "No APK", "Empty notes", "Handed off", "Checking", "Unsafe notes", "Cancelled", "No history", "Release", "IntDev")

fun fixture(index: Int): UpdateUiState {
    val release = GitHubRelease(20, "v2.4.0-Beta", notes = fixtureNotes,
        url = "https://github.com/example/goldy/releases/tag/v2.4.0-Beta",
        assets = listOf(GitHubAsset(1, "fixture.apk", "https://github.com/example/goldy/releases/download/v2.4.0-Beta/fixture.apk", 100, null)))
    val candidate = UpdateCandidate(release, listOf(
        release.copy(id = 19, tag = "v2.3.0-Release", notes = "## Earlier improvements\n\n- Improved reliability.\n- Preserved your settings."),
        release.copy(id = 18, tag = "v2.2.0-Release", notes = "")
    ))
    val base = UpdateUiState(candidate, ReleaseChannel.Beta, apkAvailable = true)
    return when (index) {
        1 -> UpdateUiState()
        2 -> base.copy(transfer = UpdateTransferState.Downloading(ApkProgress(42, 100, 1)))
        3 -> base.copy(transfer = UpdateTransferState.Downloading(ApkProgress(42000, null, 2)))
        4 -> base.copy(transfer = UpdateTransferState.Failed("Connection interrupted. Your APK has not been installed."))
        5 -> base.copy(transfer = UpdateTransferState.PermissionRequired)
        6 -> base.copy(apkAvailable = false)
        7 -> base.copy(update = candidate.copy(release = release.copy(notes = "")))
        8 -> base.copy(transfer = UpdateTransferState.HandedOff)
        9 -> UpdateUiState(checking = true)
        10 -> base.copy(update = candidate.copy(release = release.copy(notes = "[Safe](https://example.org/safe) [Unsafe](javascript:alert) ![Remote](https://127.0.0.1/image.png)\n\n<script>bad()</script>\n\n<b>HTML</b>")))
        11 -> base.copy(transfer = UpdateTransferState.Cancelled)
        12 -> base.copy(update = candidate.copy(intermediateReleases = emptyList()))
        13 -> base.copy(channel = ReleaseChannel.Release, update = candidate.copy(release = release.copy(tag = "v2.4.0-Release")))
        14 -> base.copy(channel = ReleaseChannel.IntDev, update = candidate.copy(release = release.copy(tag = "v2.5.0-IntDev_rev3", prerelease = true)))
        else -> base
    }
}

val fixtureNotes = """
# A little more clarity

A smoother update experience with **clear progress** and *careful verification*.

## What changed

- Follow download progress and cancel at any time.
- Read intermediate release notes before updating.
- Keep your own theme, typography and navigation.

| Component | Change |
| --- | --- |
| Gold | APK verification |
| Elief | Update details |

Use `GoldyState` in your ViewModel.

```kotlin
onAction(UpdateAction.Download)
```

[Release information](https://github.com/example/goldy/releases)

> Installation is completed by Android. Opening the installer does not confirm success.

## More details

""".trimIndent() + (1..8).joinToString("\n\n") { "Detail paragraph " + it + ". Long release notes remain readable with large fonts and on narrow screens." }
