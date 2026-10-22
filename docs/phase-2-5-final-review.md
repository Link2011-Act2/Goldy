# Phase 2-5 final review

2026-10-22. Starting branch: master. Starting HEAD:
176d6946fee53b0fe2fd0e89d5196d39a6b2f357. Phase 2-3/2-4 changes were present
as 11 modified and 64 untracked files. All 172 initial files, Git state and
working-tree patch were backed up under ignored build/phase-2-5 before editing.
No reset/clean was used. Existing application repositories and Pyrite were not
modified. Workstation/sibling-repository baseline snapshots stay local; public
Phase 2-3/2-4 evidence uses relative paths after removing workstation prefixes.

## Review and changes

See [public API contracts](public-api-contracts.md) for ownership, coroutine
contexts, exception handling and file lifetime. The installer authority was
made internal; Android's provider entry point remains public. Existing parser,
selector, policy and verification APIs used by independent consumers were kept.
No package/signer/code/release-identity, URL/CDN, file or permission condition
was relaxed. No automatic install-success state or lease cleanup was added.

Gold/Elief now inherit one configurable publication group/version, keeping
project dependencies, POM and Gradle metadata consistent. Preflight publication
using com.github.Link2011-Act2.Goldy and phase-2-5-preflight contains matching
Gold transitive dependencies in Elief's POM and both metadata API/runtime variants.
Both publish sources JARs and MIT/SCM/developer POM information. Elief's AAR
also includes its LICENSE/NOTICE. Root LICENSE/NOTICE and dependency-license
documentation were added.

The clean build setup uses JDK21, wrapper Gradle9.6.0 with checksum, AGP9.4.1,
SDK platforms;android-37.0 and build-tools;36.0.0. Library Kotlin language/API
remains 2.2 and JVM bytecode 11. jitpack.yml installs the SDK packages and
publishes only Gold/Elief with the requested immutable VERSION. There is no
machine-specific JDK path or required JDK25 download.

References: [JitPack multi-module/build environment](https://docs.jitpack.io/building/),
[AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes).

## Local verification

The interrupted pre-task invocation was recorded and restarted only after
confirming no Java process was running. JDK21 checks finished successfully:

| Check | Result |
|---|---|
| Gold JVM | 103 passed, zero failures/errors |
| Elief JVM | 10 passed, zero failures/errors |
| Sample JVM | 1 passed, zero failures/errors |
| Existing independent core consumer | 4 passed, Release build, lint, producer hash, no Compose/Markwon/Pyrite |
| Existing independent UI consumer | Release build, stable Material3 1.4.0, transitive Gold and producer hashes |
| Debug/Release | Gold/Elief AARs, sample APKs, both instrumentation APKs built |
| Debug/Release lint | No errors; Gold 2 warnings per variant, Elief 0, sample 8 |
| Installer Manifest | Unique nonexported Gold provider coexists with host provider; permission opt-in retained |
| JitPack-coordinate preflight | POM and metadata both use matching Gold group/version |
| Isolated R8 baseline | Scheduler-profile Release build and manifest check passed |

Phase 2-4's successful UI records are reused: API28 at 320dp with font scales
1.0/1.8 each passed 12 tests; API36 at 320dp/font1.8 passed 12, earlier font1.0
run passed 11. They cover standard/Expressive, light/dark, dynamic color on
supported APIs, custom typography, duplicate events, retry/cancel, back,
Activity recreation/saveable history/scroll and Markdown trust boundaries.
This phase changed no UI logic, so those runs were not repeated pointlessly.

Phase 2-3's 36 Android verification groups, including actual HTTPS/cancellation,
URI grants and system Activity handoff, remain supporting evidence. They are
not counted as JUnit tests and do not assert successful APK installation.

Local Release AAR sizes: Gold 280404 bytes; Elief 59447 bytes. These are normal
AARs, excluding transitive dependency bytes. Public Linux/JitPack sizes and
R8 APK deltas will be recorded after actual public builds, rather than inferred
from local artifacts.

## Public verification status

Pending GitHub commit/push, explicitly authorized private-to-public visibility
change, JitPack build and artifact retrieval. No public success is claimed here.
The fixture in verification/jitpack-consumer has only JitPack for Goldy modules,
plus Google/Maven Central for external dependencies. It has no local Maven,
project dependency or includeBuild. The runner copies it outside the producer
project and checks matching versions, Gold-only dependency isolation, stable/
Expressive Material3, R8 mapping and host Manifest for minSdk28/JVM17 (ShutUp),
minSdk26/JVM17 (Scheduler), minSdk33/JVM11 (Glyph). APK sizes compare the same
host baseline with Gold and Elief.

## Remaining limits

API26/27, API29-35, physical/OEM devices and actual installation completion are
unverified. Multiprocess coordination and automatic signer rotation are out of
scope. Large synchronous Markdown input/wide tables need host testing. The
existing three applications still require separately scoped migration of their
settings/cache, navigation, lifecycle and process-restoration behavior. No tags
or GitHub Releases are created by this phase.
