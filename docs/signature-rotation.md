# APK署名ローテーションと版名の本人確認

Gold uses `com.android.tools.build:apksig:9.4.1` to authenticate the complete archive and its Proof-of-Rotation for exactly `Build.VERSION.SDK_INT`. PackageManager still supplies package/version metadata; its effective current signer must agree with apksig. Certificate extraction or history intersection alone never grants rotation.

## Accepted signing updates

- Exact current signer set and single/multiple-signer classification: retained.
- Different single current signer: require an authenticated v3/v3.1 lineage for this platform, ordered oldest-to-current, no duplicates, current certificate at the terminal node, installed current certificate strictly before it, and `installed-data` capability for that exact installed certificate.
- The OS-established installed history must match the contiguous ancestor history ending at that certificate. Unrelated forks, missing history/capability, reverse rotation and ambiguous evidence are rejected.
- Multiple signers require complete set equality; partial equality and multiple-to-single changes remain rejected.
- `ROLLBACK` capability and platform rollback exceptions do not enable reverse rotation in Gold. Unverified development-platform SDK targeting is also rejected.

The active SDK-targeted signer's own lineage/capabilities are used, rather than apksig's combined lineage, whose flags can differ by targeted signer. All cryptographic errors, missing certificates and PackageManager/apksig disagreement fail closed.

| Platform | Verification |
|---|---|
| API26/27 | Effective v1/v2 identity only; different current signers cannot be accepted. A multi-target APK may legitimately retain the original v2 signer. |
| API28–32 | v3/PoR for this SDK; v3.1's future rotated signer does not replace the v3 fallback signer. |
| API33+ | v3.1 when applicable, otherwise the SDK-appropriate scheme. Future-targeted/development-release ambiguity is rejected. |

These are preflight checks. PackageInstaller remains responsible for the final update decision, including conditions outside signer/package/version identity. `HandedOff` is not installation success.

## ABI and evidence lifetime

`ApkSigners(Set<String>, Boolean)` and `ApkMetadata` constructors, data-class copy/component/equality methods and prior public descriptors remain. `certificateHistorySha256` is an additive read-only view. Authenticated history is outside the primary constructor and is deliberately not copied by `ApkSigners.copy()` or restored from an install-record JSON. A caller-created/copied value cannot assert a rotation. The production inspector authenticates the file again before prepare, launch and provider reads; restored leases therefore obtain fresh cryptographic evidence.

No public flag or developer option disables verification. Custom `ApkInspector` implementations retain the existing obligation to authenticate signatures. Install files remain immutable, hashed, isolated behind the nonexported provider, and explicitly leased until the host knows readers have finished. No age-based release, onResume deletion, or inferred install success was added.

## Strict published version binding

`ApkVersionBindings.Exact` is unchanged. `Published` and the four app aliases remove exactly one lowercase `v` immediately before a numeric version, then require exact equality with APK `versionName`. Numeric dotted versions with explicit alphanumeric suffix/build/revision tokens are accepted without a channel whitelist. An uppercase `V` is retained literally; `V1.2.0` can match only `V1.2.0`, never `1.2.0`. Empty/whitespace/control/path values, empty segments and repeated version prefixes are rejected. Case, digit counts, separators, revisions and build values are never normalized. Version ordering equality does not establish APK identity.

## Verification and reproducibility

`verification/generate-rotation-fixtures.py` creates tiny manifest-only APKs using disposable RSA keys. Private test keys stay under ignored `build/rotation-fixtures/keys`; only public signed APK fixtures are committed. The fixture set covers A, unrelated B/X, A→B, A→B→C, installed-data denied, v3.1 SDK targeting, multiple AB/AC, tampered contents and tampered lineage.

`ApksigFixtureTest` verifies real fixture bytes for SDK26/27/28/29/32/33/36. `verification/run-rotation-device.ps1 -Serial emulator-...` compares Android inspection and 14 actual OS install/update outcomes on a disposable emulator. `verify-api-compatibility.py` compares old public descriptors; `verify-signer-binary-compatibility.py` compiles against the old AAR and runs the unchanged client with only the new AAR.

Do not infer a device/API result from JVM SDK emulation. Record each run under an ignored build directory. No production signing key, silent installer, root or Shizuku is used.

## Primary references

- [AOSP v3.1 SDK-targeted signer behavior](https://source.android.com/docs/security/features/apksigning/v3-1)
- [AOSP PackageManager update capability checks](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerServiceUtils.java)
- [ApkVerifier: full APK verification and per-signer lineages](https://android.googlesource.com/platform/tools/apksig/+/refs/heads/main/src/main/java/com/android/apksig/ApkVerifier.java)
- [SigningCertificateLineage: ordered certificates and installed-data/rollback capabilities](https://android.googlesource.com/platform/tools/apksig/+/refs/heads/main/src/main/java/com/android/apksig/SigningCertificateLineage.java)
