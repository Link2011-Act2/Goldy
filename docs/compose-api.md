# Elief Compose UI

Elief is the optional Android UI artifact `jp.linkserver.goldy:elief:0.1.0-SNAPSHOT` (module `:elief`), minSdk 26. It exports Gold transitively. The host applies the Kotlin Compose plugin and enables Compose. No Pyrite dependency or theme is required.

## Display and events

`UpdateOverviewScreen(state, onBack, onOpenUrl, onAction)` displays a version, channel, notes, intermediate history, download/install state and release links. `ReleaseNotes(markdown, onOpenUrl)` is also usable separately. Both inherit MaterialTheme. The screen has a saveable scroll position and individually saveable history expansion, keyed to release IDs. Stable, unique IDs must be retained when reconstructing metadata.

`GoldyState.toUpdateUiState(versionPolicy, apkSelection, transfer, message)` uses the exact host policies. It does not download or authenticate an asset. Pass the same selection policy to ApkDownloadConfig. An ambiguous/missing APK gives a GitHub link and no download button. The host supplies localized metadata errors through message; absence of an update alone is not evidence that a network check succeeded.

The transfer model is `UpdateTransferState`: Idle, Downloading(ApkProgress), Verifying, Ready, PreparingInstall, PermissionRequired, HandedOff, Cancelled, Failed(message, retry?). `UpdateAction` is Download, CancelDownload, Install, OpenInstallPermissionSettings. Ready represents a host-held VerifiedApk or prepared InstallRequest; it does not represent unverified downloaded bytes. Failed.retry can be null for non-retryable failures.

The screen displays all content in a single vertical scroll, including action buttons. This allows long labels, 320dp widths and large fonts without a fixed bottom bar consuming the viewport. Return to the action card at the top to start/cancel a transfer. Downloading shows byte counts, attempt number and determinate progress when a positive total is known; otherwise the progress bar is indeterminate.

## Host ownership and real integration

A compile-checked, independent AAR consumer is in [Consumer.kt](../verification/compose-consumer/consumer/src/main/java/example/eliefconsumer/Consumer.kt). [HostUpdateViewModel.kt](../verification/compose-consumer/consumer/src/main/java/example/eliefconsumer/HostUpdateViewModel.kt) demonstrates download, verification, preparation, permission handling, launch-time revalidation and pending leases using existing Gold APIs.

- Own work in ViewModel/viewModelScope, never a Composable-owned coroutine. Use the Activity lifecycleScope for the final install command with its current Activity callback. The library never starts an Activity, opens a browser, registers a service, or requests installation permission.
- Handle screen and system back in the host (for example BackHandler/navigation). onBack is the app-bar callback. Collect UI state using the host's lifecycle conventions.
- Route Download/CancelDownload to the ViewModel; route Install from the Activity to its lifecycleScope and call the model's install with the current Activity's startActivity callback. Route OpenInstallPermissionSettings to the host's chosen settings navigation, catching ActivityNotFoundException/SecurityException there. On return, Install explicitly retries and Gold rechecks permission.
- Synchronously claim an operation before launching a coroutine. Disabled/busy UI alone cannot prevent two events in the same frame. The fixture ViewModel and consumer integration example guard duplicate operations. Progress callbacks can arrive on IO; use StateFlow or dispatch to Main rather than mutating UI view state on IO.
- Pin the displayed UpdateCandidate while a transfer/verified APK/request is active; do not silently swap to a newly discovered release. End/cancel the operation before selecting a different update. A host should map APK and installer errors to actionable, localized messages; no raw server notes should become an error title or system command.
- ApkDownloader returns only after its existing package, signer, digest, size and version checks. It does not expose a separate verification callback, so final verification remains covered by the downloading state until Ready. Verifying is available for host-specific orchestration.
- Installer preparation/launch is done by ApkInstaller. REQUEST_INSTALL_PACKAGES must be explicitly declared by a host that enables installation. Elief does not declare it. Gold's unique FileProvider and its restricted paths remain unchanged.

A host Activity can route the four events as follows (its ViewModel/UI collector supplies state):

```kotlin
UpdateOverviewScreen(
    state = state,
    onBack = navigateBack,
    onOpenUrl = openApprovedWebUrl,
    onAction = { action ->
        when (action) {
            UpdateAction.Download -> model.download()
            UpdateAction.CancelDownload -> model.cancel()
            UpdateAction.Install -> lifecycleScope.launch {
                model.install { intent -> startActivity(intent) }
            }
            UpdateAction.OpenInstallPermissionSettings -> {
                // Host catches launch exceptions and updates its own error UI.
                openInstallPermissionSettings(model.settingsIntent())
            }
        }
    }
)
```

## File lifetime, cancellation and restoration

A VerifiedApk is an in-memory verification receipt for a cache file. Keep it in the host ViewModel across Activity recreation; do not serialize it or reconstruct it from an arbitrary path. A process death before preparation requires a new verified download. Gold cleans its partial files on cancellation; the host owns deletion of completed cache files. Once prepare returns Ready, Gold has staged its own verified immutable copy, so the original cache file can be deleted on IO.

InstallRequest is a persisted lease. Keep it after launch and after launch failure: a callback may have started a reader before reporting an ambiguous failure. **Never release it in onDispose, onCleared, onResume, a launch finally block, or a timer.** Release explicitly with ApkInstaller.release only after the host knows no installer/reader needs the URI. Do not infer that from Activity recreation or an Activity simply resuming. Use pendingRequests after process death and let the host/user choose a safe retry or cleanup. Gold revalidates before every launch and URI open. See [installer-api.md](installer-api.md).

HandedOff means Android accepted an Activity request. The standard UI explicitly says installation is unconfirmed. Installation success detection remains a host responsibility; Elief intentionally has no Installed state.

## Themes, strings and slots

Use the host MaterialTheme or MaterialExpressiveTheme around the screen. Colors, shapes and text styles are read from that theme; no Elief theme is installed. Typography.fontFamily is also resolved for the Markdown TextView. Set a Nothing font through the host typography (the font file/license remains the host's responsibility). Code blocks retain monospace behavior.

UpdateStrings replaces titles, actions, status/empty messages and history labels; channelLabel accepts a localized channel. Failed.message and UpdateUiState.message are host-supplied. loadingIndicator replaces the standard loading indicator. header adds app-specific decoration or introductory content. ReleaseNotes accepts an optional TextStyle. Numeric transfer counts use bytes (B) and an attempt number.

Elief compiles against the existing Compose BOM 2026.02.01 (Compose UI 1.10.4, stable Material3 1.4.0), with Kotlin Compose plugin 2.2.10 and the existing AGP 9.4.1. The sample alone opts into Material3 1.5.0-beta01 for the public experimental Expressive theme used by Glyph Barty. It does not force that prerelease version onto Elief consumers.

## Markdown trust boundary

Markwon core + ext-tables 4.6.2 render headings, emphasis, lists, links, inline/fenced code, blockquotes and tables. HTML block/inline nodes are ignored. Images render their alt text only: no drawable spans, HTML/image plugin, WebView, JavaScript, auto-linkify, remote image request or fallback Activity launch is installed. Links are delivered only for absolute HTTP(S) URLs with a host and without credentials/control characters. Relative, javascript:, intent:, file: and content: links are inert. The host should apply its own navigation policy to the callback; a link is not trusted merely because its protocol is HTTP(S).

The Markdown view is text-selectable and uses native accessibility. Markdown tables use Markwon's fitted column layout, not an interactive spreadsheet; very wide tables and unbroken strings need host/content testing. Notes are parsed synchronously when changed; keep the bounded Gold metadata limits and avoid injecting arbitrarily large host-created strings.

## Offline sample

`:app` is the runnable fixture sample. It has light/dark, standard/Expressive, API31+ dynamic colors and a monospace custom-font example. Horizontally scroll the theme/scenario chips to access every option. Scenarios include update/no update, progress with known/unknown size, retryable failure, permission, missing APK, empty notes, handoff, checking and hostile Markdown. No action downloads or installs an APK; the header reports callbacks.

## App adoption (separate approval required)

| Host | Metadata policy | APK selection / version binding |
|---|---|---|
| ShutUp | UpdatePolicies.shutUp(), VersionPolicies.ShutUp | ApkSelectionPolicies.ShutUp / ApkVersionBindings.ShutUp |
| NITTC Scheduler | UpdatePolicies.nittcScheduler(), VersionPolicies.NittcScheduler | ApkSelectionPolicies.NittcScheduler / ApkVersionBindings.NittcScheduler |
| Glyph Barty | UpdatePolicies.glyphBarty(), VersionPolicies.GlyphBarty | ApkSelectionPolicies.GlyphBarty / ApkVersionBindings.GlyphBarty |

Each host supplies its existing GitHubRepository, notification preferences and UI resources. Replace only its detail screen with UpdateOverviewScreen under its existing theme, retain the above policies (including Scheduler's non-app release filter), connect actions to the existing host ViewModel and Activity navigation, and opt into installation permission only where needed. APK naming bindings retain all Phase 2-2 restrictions; rendering a channel never authorizes a previously unconfirmed APK/tag mapping. No application migration is included in this phase.

References: [Markwon plugin configuration](https://noties.io/Markwon/docs/v4/core/plugins.html), [Material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3).
