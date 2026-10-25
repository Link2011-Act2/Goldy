# Internal naming and compatibility review

Date: 2026-10-25. Published baseline: `189092a7b9a142e5d432017b5b9ce71404a5758b`.
Starting HEAD: `ec80320624210a8f85b98476edec92ebc55cbbcd`, master, clean.
The preceding naming audit was reported in chat; no audit file was created then.

## Names and behavior

| Previous name/reference | Current implementation | Compatibility |
|---|---|---|
| compareShutUpVersions | internal compareReleaseVersions | Old JVM method forwards to the new method |
| internal/ShutUpVersions.kt implementation | internal/ReleaseVersions.kt | Old file remains a thin JVM facade |
| UpdatePresentation default NittcScheduler | Common | Both reference the same VersionPolicy instance |
| Demo: ShutUp ordering compares rev2 before rev10. | Common version ordering compares rev2 before rev10. | Fixture explanation only |
| Consumer Glyph Beta10 == Beta2 assertion | Beta10 > Beta2 assertion | Follows the existing published Common contract |

The new implementation file is exactly the baseline source with only the comparison
function name replaced. Parsing, core numbers, v prefix, channels, natural suffix
numbers, rev separators, unknown suffixes, null/overflow handling and stable
equivalence are unchanged. IntDev2 versus IntDev_rev2 and absent versus explicit
rev0 retain their existing ordering. VersionPolicy.kt only changes import/calls.
No candidate selection, notification, APK identity, download or install rule changed.

## JVM and public API

`ShutUpVersionsKt.compareShutUpVersions(String,String): Integer` and
`ShutUpVersionsKt.commonReleaseChannel(String): ReleaseChannel` remain. The channel
shim has the Kotlin name `legacyCommonReleaseChannel` with `@JvmName`; the canonical
Kotlin function resides in ReleaseVersions.kt without a duplicate implementation.

All **739 Gold / 237 Elief** public method and field descriptors in the actual
189092a7 AARs are retained. Earlier 733/178 figures used the older 0d89991 baseline.
UpdatePresentation constructors and default-argument entry points are retained.

`verification/verify-version-binary-compatibility.py` compiles a Java client against
the baseline AAR and runs that same bytecode with only the new Gold JAR and Kotlin
runtime. Both old facade methods link. **4,096 pair comparisons and 64 channel
classifications match exactly**, including four-app tags and boundary inputs. The
script separately asserts source identity apart from the function name.

## Validation

| Check | Result |
|---|---|
| Gold JVM: frozen oracle, real tags, ordering laws, selection and APK regressions | 119 passed, 0 failures/errors/skips |
| Elief JVM, including new default history-label regression | 18 passed, 0 failures/errors/skips |
| Demo JVM | 6 passed, 0 failures/errors/skips |
| Old independent Consumer JVM | 4 passed, 0 failures/errors/skips |
| Demo Debug / Release with R8 | Passed; mapping produced |
| Gold / Elief / Demo Release lint; Demo Debug lint | Passed; 0 errors. Release warnings: 2 / 0 / 9 |
| Independent local Gold Consumer Release | Passed; exact AAR, coroutines, no Compose/Markwon/Pyrite |
| Independent local Elief Consumer Release | Passed; exact Gold/Elief AARs, transitive Gold, stable Material3 1.4.0, no Pyrite |

The other old Consumer metadata/selection, storage/coroutines and strict APK
identity expectations remain valid. No test was removed, disabled or weakened.
That consumer exposes testDebugUnitTest; no Gradle configuration change was needed.
Frozen legacy comparison tests are unchanged.

Reproduction with JDK 21 / Android SDK 37:

```powershell
./gradlew.bat :gold:testDebugUnitTest :elief:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :gold:lintRelease :elief:lintRelease :app:lintRelease :app:lintDebug :gold:publishReleasePublicationToConsumerRepository :elief:publishReleasePublicationToConsumerRepository --offline
./gradlew.bat -p verification/consumer :consumer:testDebugUnitTest :consumer:assembleRelease :consumer:verifyCoreDependencies --offline
./gradlew.bat -p verification/compose-consumer :consumer:assembleRelease :consumer:verifyPublishedUi --offline
python verification/verify-api-compatibility.py --baseline-dir <189092a7-artifacts> --output-dir <results>
python verification/verify-version-binary-compatibility.py --baseline-dir <189092a7-artifacts> --baseline-commit 189092a7b9a142e5d432017b5b9ce71404a5758b --output-dir <results> --stdlib <kotlin-stdlib.jar>
```

Machine results: [internal-naming-results.json](../verification/internal-naming-results.json).
Local logs and javap/client output: ignored `build/internal-naming/`.
Exact pushed-SHA JitPack results will be recorded after publication verification.

## Protected names and limits

App-specific UpdatePolicies, ApkSelectionPolicies, ApkVersionBindings and all four
public VersionPolicies getters remain. Persisted IDs, LICENSE/NOTICE attribution,
old oracle packages, historical reports/JSON and real repository/package/APK names
remain unchanged. README was not edited. No sibling app or Pyrite was written.

No full emulator UI matrix was repeated: the only Elief substitution uses identical
policy aliases, covered by the new JVM test for valid, unknown and invalid tags with
both prerelease flags. Existing UI/security records remain historical evidence,
not runs of this commit. API26/OEM runtime and actual installation completion are
not newly verified. Existing lint warnings remain. No APK distribution, tag or
GitHub Release is part of this work.
