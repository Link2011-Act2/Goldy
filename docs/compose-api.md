# Elief Compose UI

The new banner, badge and manual-check dialog APIs are documented in
[update-notifications.md](update-notifications.md). They require the new verified
revision listed there; older README coordinates do not contain these additions.

Elief is the optional Android UI module `:elief`, minSdk 26. Use the verified public JitPack coordinate in the [README](../README.md); `jp.linkserver.goldy:elief:0.1.0-SNAPSHOT` is only the local publication fixture. It exports Gold transitively. The host applies the Kotlin Compose plugin and enables Compose. No Pyrite dependency or theme is required.

## Display and events

`UpdateOverviewScreen(state, onBack, onOpenUrl, onAction)` displays a version, channel, notes, intermediate history, download/install state and release links. `ReleaseNotes(markdown, onOpenUrl)` is also usable separately. Both inherit MaterialTheme. The screen has a saveable scroll position and one saveable expansion state for the whole history, keyed to the selected release ID. Stable, unique IDs must be retained when reconstructing metadata.

`GoldyState.toUpdateUiState(versionPolicy, apkSelection, transfer, message)` uses the exact host policies. It does not download or authenticate an asset. Pass the same selection policy to ApkDownloadConfig. An ambiguous/missing APK gives a GitHub link and no download button. The host supplies localized metadata errors through message; absence of an update alone is not evidence that a network check succeeded.

The transfer model is `UpdateTransferState`: Idle, Downloading(ApkProgress), Verifying, Ready, PreparingInstall, PermissionRequired, HandedOff, Cancelled, Failed(message, retry?). `UpdateAction` is Download, CancelDownload, Install, OpenInstallPermissionSettings. Ready represents a host-held VerifiedApk or prepared InstallRequest; it does not represent unverified downloaded bytes. Failed.retry can be null for non-retryable failures.

Actions normally use a fixed bottom region. For large fonts or a short viewport they move into the content scroll, so 320dp layouts and long permission/error messages remain accessible. Downloading shows byte counts, attempt number and determinate progress when a positive total is known; otherwise the progress bar is indeterminate. See the layout adaptation rules below.

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

## Standard layout and display customization

The standard screen uses an arrow IconButton (`UpdateStrings.back` is its accessibility description), a horizontal version/channel row, a filled theme surface for notes, and one saveable expansion control for the whole intermediate history. History cards show their version and channel. Content and docked actions are centered with an 840dp maximum width.

Actions are docked below the scroll at ordinary sizes. At fontScale >= 1.5 or available height < 480dp, including reduced IME space, actions join the same scroll so they cannot crowd out the content. The docked region is bounded to 40% of the available height and can itself scroll for long permission/error messages. Navigation-bar insets, IME padding and Scaffold content insets are applied without a fixed spacer. These are layout choices, not an operation/installation policy.

The original `UpdateOverviewScreen` signature is retained. A separate overload requires `presentation`; existing calls remain unambiguous. Other state, strings, Markdown and adapter signatures are unchanged. Defaults preserve the raw version tag on screen and classify history channels with `VersionPolicies.Common`. This has the same behavior as the retained `NittcScheduler` alias. To remove `v` only for display, or localize history channels:

```kotlin
UpdateOverviewScreen(
    state = ui,
    onBack = navigateBack,
    onOpenUrl = openApprovedWebUrl,
    onAction = onUpdateAction,
    strings = UpdateStrings(title = "更新内容", back = "戻る"),
    presentation = remember(policy) {
        UpdatePresentation(
            versionLabel = { it.tag.removePrefix("v") },
            historyChannelLabel = { policy.versions.channel(it.tag, it.prerelease).name }
        )
    }
)
```

`UpdatePresentation.backIcon` optionally replaces the decorative icon; its surrounding IconButton still owns the back event and description. `channelLabel` continues to control the selected release's channel. Channel chips are noninteractive labels. Display formatters never mutate GitHubRelease, comparison inputs or APK version binding. The host still owns system-back navigation, coroutine scope, file lifetime and all installer actions.

### Host card roles and Pyrite

Elief uses the host typography without forcing a bold version label. Cards use 22dp inner padding; history uses the official expand/shrink and fade animations. The main action has a 56dp minimum height. No proprietary font or palette is bundled.

`LocalUpdateCardStyle` optionally bridges a host's card color and shape without changing ColorScheme roles or either UpdateOverviewScreen overload. Unspecified values use `surfaceContainerHigh` and `MaterialTheme.shapes.large`. For example, inside the host theme:

```kotlin
CompositionLocalProvider(
    LocalUpdateCardStyle provides UpdateCardStyle(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large
    )
) {
    UpdateOverviewScreen(ui, navigateBack, openApprovedWebUrl, onUpdateAction)
}
```

Goldy Demo alone depends on public Pyrite SHA `508b1cf5131679fee7581acbc7ea55a365b6ff2c`. It uses real PyriteTheme/PyriteFonts and a 22dp card shape. M3E light bridges surfaceContainerLowest; M3E dark and Nothing use surfaceContainerHigh, matching Pyrite's card implementation. Nothing keeps Pyrite's monochrome primary and ignores Dynamic Color. A separate Style reference tab renders public Pyrite cards directly. The exclusive Style picker offers Material 3 Expressive (default), Nothing, and standard Material 3. Style, light/dark, dynamic-color preference and the custom-font choice persist in Demo-only settings. Nothing displays Dynamic as disabled/off without discarding the preference for the other styles.

Demo aligns its UI, tooling and test artifacts to Pyrite's Compose 1.12.0 / Material3 1.5.0-beta01. Elief keeps its existing BOM and stable Material3 1.4.0 baseline, adds only official Compose Animation, and does not depend on Pyrite. Public API compatibility and stable consumer verification are recorded separately in the style review.

## Markdown trust boundary

Markwon core + ext-tables 4.6.2 render headings, emphasis, lists, links, inline/fenced code, blockquotes and tables. HTML block/inline nodes are ignored. Images render their alt text only: no drawable spans, HTML/image plugin, WebView, JavaScript, auto-linkify, remote image request or fallback Activity launch is installed. Links are delivered only for absolute HTTP(S) URLs with a host and without credentials/control characters. Relative, javascript:, intent:, file: and content: links are inert. The host should apply its own navigation policy to the callback; a link is not trusted merely because its protocol is HTTP(S).

The Markdown view is text-selectable and uses native accessibility. Markdown tables use Markwon's fitted column layout, not an interactive spreadsheet; very wide tables and unbroken strings need host/content testing. Notes are parsed synchronously when changed; keep the bounded Gold metadata limits and avoid injecting arbitrarily large host-created strings.

## Offline sample

`:app` is Goldy Demo. Its UI Showcase retains the offline fixtures, all three styles, light/dark, API31+ dynamic colors and a monospace custom-font example. Horizontally scroll the settings/scenario rows to access every option; the Style dialog offers one selected radio option and scrolls vertically when needed. Showcase actions never download or install an APK. Separate Live Demo and self Update Demo modes use real Gold APIs only after explicit user actions; see [Goldy Demo](goldy-demo.md) for distribution rules, file lifetime and safe local test instructions.

## App adoption (separate approval required)

| Host | Metadata policy | APK selection / version binding |
|---|---|---|
| ShutUp | UpdatePolicies.shutUp(), VersionPolicies.ShutUp | ApkSelectionPolicies.ShutUp / ApkVersionBindings.ShutUp |
| NITTC Scheduler | UpdatePolicies.nittcScheduler(), VersionPolicies.NittcScheduler | ApkSelectionPolicies.NittcScheduler / ApkVersionBindings.NittcScheduler |
| Glyph Barty | UpdatePolicies.glyphBarty(), VersionPolicies.GlyphBarty | ApkSelectionPolicies.GlyphBarty / ApkVersionBindings.GlyphBarty |

Each host supplies its existing GitHubRepository, notification preferences and UI resources. Replace only its detail screen with UpdateOverviewScreen under its existing theme, retain the above policies (including Scheduler's non-app release filter), connect actions to the existing host ViewModel and Activity navigation, and opt into installation permission only where needed. APK naming bindings retain all Phase 2-2 restrictions; rendering a channel never authorizes a previously unconfirmed APK/tag mapping. No application migration is included in this phase.

References: [Markwon plugin configuration](https://noties.io/Markwon/docs/v4/core/plugins.html), [Material3 release notes](https://developer.android.com/jetpack/androidx/releases/compose-material3).
