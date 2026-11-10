# Verification tools

This directory contains independent consumers, Android probe applications, and
reusable verification scripts. Generated logs, screenshots, downloaded artifacts,
and result summaries belong under the repository's ignored `build/` directory.

Set `JAVA_HOME` to the JDK required by the [main build](../README.md) and
`ANDROID_HOME` to the Android SDK. Scripts that accept SDK/JDK arguments can
override these environment variables. Python tools require Python 3; PowerShell
scripts use the repository's Gradle Wrapper and Windows Android SDK tools.

## Local tests and consumers

```powershell
./gradlew.bat :gold:testDebugUnitTest :elief:testDebugUnitTest :app:testDebugUnitTest
./gradlew.bat -p verification/consumer :consumer:testDebugUnitTest :consumer:assembleRelease :consumer:verifyCoreDependencies
./gradlew.bat -p verification/compose-consumer :consumer:assembleRelease :consumer:verifyPublishedUi
```

The [Gold consumer](consumer/) and [Compose consumer](compose-consumer/) exercise
locally published AARs. [HostUpdateViewModel.kt](compose-consumer/consumer/src/main/java/example/eliefconsumer/HostUpdateViewModel.kt)
shows host-owned transfer and installer actions. The
[Android probe](android-probe/) checks provider and URI behavior from a separate UID.

## Public JitPack distribution

Use `verify-jitpack.ps1 -Version <published-commit>` to check public artifacts.
`run-jitpack-consumer.ps1 -GoldyVersion <published-commit> -Feature gold` (or
`elief`) builds the [independent JitPack consumer](jitpack-consumer/) with no
producer checkout or local Maven dependency. `-RefreshDependencies`, `-IncludeDebug`,
and `-PomOnly` select additional distribution checks. Use an already published
commit; these scripts do not publish the SDK.

## Compatibility and security fixtures

- `verify-api-compatibility.py --baseline-dir <old-aars> --output-dir <build-output>`
  compares published JVM descriptors.
- `verify-version-binary-compatibility.py --baseline-dir <old-aars> --baseline-commit <commit> --stdlib <kotlin-stdlib.jar> --output-dir <build-output>`
  checks version-policy forwarding APIs with previously compiled clients.
- `verify-signer-binary-compatibility.py --baseline <old.aar> --current <new.aar> --stdlib <kotlin-stdlib.jar> --output <build-output>`
  checks signer data-class compatibility without assuming a Gradle cache location.
- `generate-rotation-fixtures.py --java-home <jdk> --android-home <sdk>` creates
  signed test APKs using disposable keys kept under ignored build output.
- `prepare-corrupt-apk.py` prepares a negative APK fixture; `verify-update-icon.py`
  compares vector geometry and bundled icon attribution.

See [signature rotation](../docs/signature-rotation.md) and
[installer ownership](../docs/installer-api.md) for the invariants being tested.

## Android device tests

Use an explicitly selected disposable emulator. These scripts install test APKs,
change test-package permissions, and may clear test-package data. Do not point
them at a phone containing personal data.

`run-demo-ui.ps1`, `run-elief-ui.ps1`, `run-pyrite-style-ui.ps1`, and
`run-compose-ui.ps1` require `-Serial` and `-Label` and restore display settings
after the run. `run-android-instrumentation.ps1` checks installer/provider flows;
`run-rotation-device.ps1` checks real OS signing outcomes. The
[live cleanup check](managed-apk-ux/verify_live_cleanup.py) verifies APK replacement
while another UID holds a URI open; pass `--serial`, `--adb`, and `--artifacts`.

UI logs and captures go under `build/`; the paths on the device are test fixture
contracts. Loopback addresses and fixed ports in test transports are intentional
and must agree with their fixture server. They do not identify a developer's PC.
