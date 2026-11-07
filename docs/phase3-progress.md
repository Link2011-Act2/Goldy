# Durable APK transfers (Phase 3)

Implemented and locally verified on API28/API36, based on the previous public
847d098cd1ddff7cc740d8189c25a1f854e23b28. Public artifact/Consumer validation is
a separate gate: use only a SHA whose JitPack artifacts have been verified.
The connected ShutUp verification/phase3 report records publication and adoption.
Its previous theme, icon, permission and Android execution work is preserved.

Implemented so far:

- ResumeHttp validates single 206 ranges, payload length, encoding, total size,
  strong ETag and RFC-qualified Last-Modified. Weak tags cannot be If-Range.
- ResumeCheckpoint uses deterministic asset identity, app-private partial files,
  synced bytes and atomically replaced metadata. Prefix SHA-256 allows recovery
  by truncating an uncheckpointed crash tail after checking the committed prefix.
  Metadata hashes are corruption detection, not APK authenticity.
- ApkDownloader.downloadResumable connects these components. Canonical URL and
  allowlisted HTTPS redirects retain Range/If-Range; 200 truncates/restarts;
  416 discards/restarts rather than granting completion. Completion rehashes the
  entire file and invokes ApkVerifier before constructing VerifiedApk.
- Both standard create/download and createResumable/downloadResumable use this
  single pipeline, preserving existing public method/configuration descriptors.
  createResumable uses filesDir/goldy-transfers, separate from installer leases.
  Only fully verified APKs move to the existing cacheDir/goldy-apks handoff
  directory; ApkInstaller retains its restricted source-directory checks.
  Coroutine interruption retains checkpoints; explicit discardPartial is separate.
- A real filesystem test interrupts a transfer at byte 7, creates another
  downloader, appends via 206, checks cumulative progress and whole-file SHA-256,
  and verifies the inspector sees the complete bytes only.
- ResumeHttpServerTest uses an actual loopback TCP/HTTP server and production
  UrlAssetTransport. Sixteen cases cover disconnect/resume, 200 fallback, 416,
  weak ETag, Last-Modified, contradictory 206 responses, corruption and asset ID
  changes, HTTP timeouts, final digest mismatch after resume, explicit deletion,
  renewed redirect URLs with Range/If-Range forwarding, storage failure,
  cancellation with retained bytes, injected connection timeout and
  restoring HTTP total when asset size is unknown. APK inspection in JVM tests
  is a fixture and is not claimed as real Android signature verification.
- Writer, savedProgress and explicit discard now share a cancellable OS file
  lock. Checkpoint I/O is Storage failure; short response segments are bounded.
- A committed-prefix crash-tail test proves truncation only after prefix hashing.
- Properties schema 1 stores identity fields, committed bytes, known total,
  prefix hash, partial state, strong validator and observed Date. Records and
  part bytes are fsynced before atomic metadata replacement every 1 MiB.
  Prefix hashes detect corruption and do not authenticate APKs.
- Cleanup preserves valid checkpoints and completed/leased APKs, removes legacy
  UUID partials, orphan parts, malformed records and abandoned temporary writes.
  All operations share the OS lock. The hard maximum remains 256 MiB.
  Storage failures stop without retry loops and retain recoverable data.
- ShutUp Runtime uses persistent downloadResumable, restores checkpoint progress,
  keeps network-error checkpoints, deletes them on explicit cancellation and
  reports a server-driven restart. Paused UI exposes saved progress and Cancel.
  The connected host's Kotlin/AndroidTest compile and 190 JVM tests pass using
  the development composite (phase3-connected-host-tests.log). Public settings
  are restored after each development build.
- API28/API36 pass real persisted Range continuation of a signed ShutUp APK:
  separate processes resume at 6,291,456 / 4,194,304 of 51,048,274 bytes using HTTP 206,
  performs whole-file digest/Android signature/package/version checks and
  create readable InstallRequests without automatic launches, then explicitly
  hand off exactly once despite two taps. HTTP/restored/notification progress
  agrees. Existing Gold Android installer/provider checks and real GitHub HTTPS
  transfer tests pass on both APIs; 14 OS signature-rotation install cases pass
  per API. The API36 ShutUp permission/background/process/lease suite, 44 UI cases,
  and Goldy Demo's 22 narrow/font1.8 UI cases also pass.
- Gold 155 / Elief 23 JVM tests pass without skips. Gold/Elief Debug and Release
  builds and Lint, Demo Debug/Release R8 and Lint pass. Lint has no errors;
  pre-existing dependency/KTX/catalog/style warnings remain.

Publication and host adoption gates:

1. Commit and push Goldy only after verification. Retrieve new JitPack artifacts and run
   independent consumers before updating ShutUp's public dependency. Never
   commit/push ShutUp automatically.

The connected ShutUp Phase 3 report records the exact adopted SHA, final gates
and public-consumer evidence. The current JVM ABI
comparison retains all declared public descriptors against 847d098. Four Kotlin
access$ bridges to private ApkDownloader implementation members disappear with
the duplicate pipeline; the verifier reports them separately as compiler-private
implementation details, not supported source-level public API.

Coroutine cancellation now means interruption; hosts must cancel/await the job
and call discardPartial(release) for explicit user cancellation. Completion
always rehashes the entire file and runs all existing APK/rotation checks;
prepare and launch revalidate again. HandedOff never means installation success.
Canonical URLs are reopened each time, with allowlisted HTTPS redirects and
Range/If-Range forwarding. A 200 truncates/restarts, a 206 is strictly validated,
and a 416 permits only a bounded restart, never a completion shortcut.
Weak ETags cannot be used for If-Range; Last-Modified must satisfy the strong-date
rule from [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-if-range).
Android force-stop and vendor restrictions can prevent automatic execution;
durable checkpoints support explicit continuation without promising perpetual
background execution.
