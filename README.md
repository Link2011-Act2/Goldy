# Project Goldy

Goldy is an Android library for GitHub release updates. **gold** is the Core
module; **elief** is the optional Compose UI module. Both require Android API 26
or newer. Gold has no Compose, Markwon or Pyrite dependency. Elief exports Gold
transitively and uses the host's Material theme and typography.

## Installation

The JitPack publication is being verified during Phase 2-5. Verified immutable
coordinates will replace this paragraph after the public build and independent
consumer have succeeded. Local `jp.linkserver.goldy:*:0.1.0-SNAPSHOT` artifacts
are development fixtures, not public installation coordinates.

## Core

```kotlin
val repository = GitHubRepository("Link2011-Act2", "ShutUp")
val goldy = Goldy.create(context, GoldyConfig(repository, UpdatePolicies.shutUp()))
// In the host's ViewModel/lifecycle scope:
goldy.restoreCachedState()
val outcome = goldy.check(CheckTrigger.Manual)
// Collect goldy.state for availableUpdate / notificationUpdate / showBanner.
```

Gold does not start work automatically or install a Service/Worker. The host
owns scopes, navigation and notification preferences. `hideBanner`,
`ignoreVersion`, `resetIgnoredVersions` and `updateOptions` maintain the
notification state without hiding the update detail candidate.

| Host | Update policy | APK selection / version binding |
|---|---|---|
| ShutUp | `UpdatePolicies.shutUp()` | `ApkSelectionPolicies.ShutUp` / `ApkVersionBindings.ShutUp` |
| NITTC Scheduler | `UpdatePolicies.nittcScheduler()` | `ApkSelectionPolicies.NittcScheduler` / `ApkVersionBindings.NittcScheduler` |
| Glyph Barty | `UpdatePolicies.glyphBarty()` | `ApkSelectionPolicies.GlyphBarty` / `ApkVersionBindings.GlyphBarty` |

Ordering a version and authenticating the APK's release identity are separate
checks. Unknown tag/version mappings are rejected. APK package, current signer
set, versionCode, size and available digest are checked independently.

## Compose UI

```kotlin
MaterialTheme {
    UpdateOverviewScreen(
        state = metadata.toUpdateUiState(
            VersionPolicies.ShutUp, ApkSelectionPolicies.ShutUp, transfer
        ),
        onBack = navigateBack,
        onOpenUrl = openApprovedWebUrl,
        onAction = handleUpdateAction,
        strings = UpdateStrings(title = "更新内容", back = "戻る")
    )
}
```

These callback/state names are supplied by the host. The host applies the Kotlin
Compose plugin and enables Compose. A compiled integration example is in
`verification/compose-consumer/consumer/src/main/java/example/eliefconsumer/`.
Claim download/install operations synchronously on Main before launching a
coroutine. Keep verified files and operations in the host ViewModel; UI disposal
must not release an install request. See [Compose API](docs/compose-api.md).

## Installation permission and file lifetime

Only a host that enables installation adds this declaration:

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Gold's Manifest supplies INTERNET, an installer query and one restricted,
nonexported provider at `${applicationId}.goldy.fileprovider`. Keep an existing
host FileProvider under its own authority. No broad file paths, write grant,
persistable grant or prefix grant is required.

`ApkDownloader.download` is main-safe; its progress callback runs on IO. Dispatch
UI-only mutations to Main. Cancellation propagates and cleans partial files.
Keep the returned `VerifiedApk` receipt in memory; do not recreate it from a path.

`ApkInstaller.prepare` creates its own verified shared copy. After preparation,
the original cache file can be deleted. `launch` calls the host's Activity
callback on the caller's context; call it from Main. **HandedOff means only that
the Activity request was accepted. It does not mean the APK was installed.**

Retain `InstallRequest` until no installer/reader needs its URI, then explicitly
call `release`. Do not release on `onDispose`, `onCleared`, `onResume`, a timer,
or an ambiguous launch failure. Use `pendingRequests` after process death;
record/storage failures must be handled without deleting potentially active
files. A process death before preparation requires a new verified download.
See [installer API and complete example](docs/installer-api.md).

## Build and verification

Install JDK 21, Android SDK platform `platforms;android-37.0` and build tools
`36.0.0`. The wrapper pins Gradle 9.6.0 and its checksum; AGP is 9.4.1. Kotlin
language/API is 2.2 and the libraries target JVM 11. Set `JAVA_HOME` and
`ANDROID_HOME` for your own machine, then run:

```sh
./gradlew :gold:testDebugUnitTest :elief:testDebugUnitTest :gold:lintRelease :elief:lintRelease :app:assembleRelease
```

The Compose fixture sample supports light/dark, standard/Expressive, API31+
dynamic colors and a custom-font example. It does not download or install APKs.
Phase 2-4 UI records cover 320dp, font scale 1.8, back, retry, duplicate events,
Activity recreation and Markdown trust boundaries. See the
[Phase 2-4 report](docs/phase-2-4-compose-report.md).

## Limits and license

API26/27, API29-35, physical devices and OEM-specific installers have not been
runtime-tested. Actual install completion is unverified; no silent installation
or automatic signer rotation is supported. Coordination is within one process.
ETag page cache is in memory, while the release catalog is persisted. Markdown
parsing is synchronous and very wide tables need content-specific testing.
HTML/images are not loaded and only absolute credential-free HTTP(S) links are
delegated to the host. Host fonts and URL policy remain host responsibilities.

The three existing applications have not been migrated. Their settings/cache
adapters and application-specific restoration need testing during adoption.

MIT License; see [LICENSE](LICENSE), [NOTICE](NOTICE), and
[third-party licenses](docs/third-party-licenses.md).
