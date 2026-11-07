# Publication verification — 2026-11-07

Verified implementation: `af5f415db976f62f991369224cb236d272f0103a`, built from start HEAD `d4d9e1e9d561be9eccddda081385a5b8fa6f6157`.
No release or tag was created. A later documentation-only HEAD does not change the pinned implementation SHA.

The [JitPack build](https://jitpack.io/com/github/Link2011-Act2/Goldy/af5f415db976f62f991369224cb236d272f0103a/build.log) produced Gold and Elief AARs, POMs, Gradle Module Metadata and sources. An initial 60-second POM request timed out while the build was pending; subsequent direct downloads and dependency refresh succeeded. No prior SHA was substituted.

- Gold AAR: 281,381 bytes; Elief AAR: 112,004 bytes.
- Public source JARs match the source commit (20 Gold / 8 Elief files, line endings normalized).
- Downloaded public AARs match Gradle's resolved AAR hashes and local public JVM signatures.
- Independent Gold-only, Elief/Gold transitive, and Elief POM-only consumers pass Debug, Release/R8 and both Lints with refreshed dependencies. Each uses only public Maven repositories, with no Pyrite, producer checkout, local Maven or included build.
- Elief exports the same SHA's Gold. Gold's runtime POM/metadata includes apksig 9.4.1 from Google Maven. Embedded licenses and scoped FileProvider manifests were checked.

Reproduce using `verify-jitpack.ps1 -Version <SHA>` and `run-jitpack-consumer.ps1 -GoldyVersion <SHA> -HostProfile shutup -Feature gold` (or `elief`) `-RefreshDependencies -IncludeDebug`; add `-PomOnly` for the POM path. Set `-JavaHome` and `-AndroidHome` to the installed JDK21/SDK paths. Generated logs, AARs and consumers are ignored build outputs. Machine-readable results are in [rotation verification](../verification/rotation-2026-11-07.json).

Gold 129 and Elief 18 JVM tests pass without skips. Debug/Release library and Demo builds, Demo Release/R8, and both library Lints pass. Old public AAR comparison removes zero descriptors (Gold 103 types / 739 descriptors; Elief 25 / 237). Unchanged signer-client bytecode compiled against the old AAR runs with the new AAR alone.

See [signature rotation](signature-rotation.md) for API-specific runtime and cryptographic tests. JitPack success alone does not demonstrate OS installation success or host migration.
