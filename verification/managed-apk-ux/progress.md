# Managed update APK UX — 2026-11-08 JST

Continues the existing migration goal. Scope is app-update UI/logic only; app functions and AI runtime/model download UI remain unchanged.

Baseline byte copies, staged/unstaged patches and heads: build/verification/managed-apk-ux/baseline/.
Goldy27692fc and ShutUpaace54c are clean. Scheduler is now the user-committed f18c14a with concise Japanese update text. Glyph's incomplete migration and original changes are preserved. Beast's original modifications and staged deletions are preserved.

Phase1 complete: preserve Scheduler's current concise used text. Six unreferenced Goldy strings removed; `:app:processDebugResources` passed in 28 seconds. UI due for removal in Phase2 has not been rewritten.

Phase2 in progress: shared reconciliation compares the actual OS-installed APK (package/version, full SHA-256 and archive signature). It durably switches the same URI to the authenticated byte-identical installed APK before unlinking the redundant copy; it does not revoke URI grants. Already-open file descriptors must remain readable; this is being tested with a real OS update and external reader. Unknown/cancelled future updates remain retained, without an age timeout. Superseded lower-version files can be retired because existing provider validation already rejects downgrade opens. Duplicate prepare reuses an authenticated request. Protected MY_PACKAGE_REPLACED and startup reconciliation are supported. Existing public constructors/methods remain; the reconciliation API is additive.

Gold 171 and Elief 23 Unit Tests passed (no failures/errors/skips). Debug Demo + UI-test APKs and release AARs built. Existing 807 Gold + 244 Elief JVM descriptors are retained against public 27692fc. Two API36 UI tests passed, covering restored installation without Release metadata at 320dp/fontScale1.8 and the permission interaction. The Demo now uses the normal Install action after restoration, with no retained-request cards, release dialog, or separate reopen path.

API36 live lifecycle test passed (`build/verification/managed-apk-ux/live-cleanup-api36.json`): actual signed version1→2 replacement, protected MY_PACKAGE_REPLACED cleanup before any explicit reconciliation, duplicate APK removal, byte-identical full reads from an already-open descriptor in another UID, new-process restore, full revalidation and same-URI read on an explicit `ApkInstaller.launch` retry. Unknown/cancelled future installs stayed retained; repeat prepare reused the existing request. No installer launch was counted as success.

Platform/test distinctions: Android kills stable provider-dependent processes and can revoke grants when updating their provider package; Goldy never revokes grants during maintenance. The external FD lifetime test acquires then releases an unstable provider client. Reopening after replacement uses the SDK's actual Intent/ClipData/read grant, rather than assuming the old OS grant survives. The test callback routes that handoff to a disposable reader; real package replacement itself uses adb and a test signing key. The ADB `content` command cannot acquire a private provider via a path grant and is not used as an installer substitute. The reader has explicit visibility for its known test host and runs in a separate task; otherwise Android delivers a later root-task Intent to the reader. Google Play parallel auto-updates caused an install timeout; the read-only emulator's Play Store was stopped and non-streaming adb used. Fixture manifest generation now uses the new AGP DSL and an explicit task dependency; a missing-generated-output first build also passed.

API28 passed the same actual replacement/held-FD/restore/explicit SDK retry checks with the same signed fixtures. Final Demo Release R8 passed (unsigned APK 1,606,713 bytes). Gold/Elief/Demo Debug and Demo Release Lint passed with no Error/Fatal (Gold debug5/release4 warnings, Elief0, Demo23). An additional same-versionCode/updateTime confirmation case passed in the focused 15-case lifecycle suite. Existing Demo Unit Tests also passed. The new API's independent public Consumer source now exercises reconciliation and the prepared-installation screen.

Local SDK gates are complete. New-SHA publication and public Consumer checks are still pending; no host has adopted an unverified public dependency.

Next: complete shared lifecycle tests and Elief flow, then adapt ShutUp and Scheduler, publish/verify a new SDK SHA, resume Glyph, then Gold-only Beast. No app commits/pushes. No README edits.

Previous successful checks are in Scheduler verification/phase4, plus unchanged Goldy/ShutUp Phase3 records. Reuse SDK internal evidence except changed lifecycle code, and avoid repeating host/API/theme Cartesian matrices.
