# Shared paused-download UI verification

Base: 809dda97ab45cd28812f150697cba126c6d4d406. APK transfer, security and execution code unchanged.

- Added opt-in PausedDownload through an additive UpdatePresentation constructor. Original JVM constructors and all 809dda9 public descriptors retained (Gold and Elief).
- Paused progress, status, resume and cancel share the bottom action panel. Unknown totals remain static without a percentage. Downloading always uses normal colors. Inline actions follow the release content.
- Restoring without release metadata shows the loading state, not a false no-update result.
- Gold JVM 172 tests, Elief JVM 24 tests, demo JVM 9 tests: zero failures/skips.
- Gold/Elief Debug/Release, demo Debug/Release R8, Gold/Elief Lint: passed.
- API36 new paused UI tests: 2/2 passed, including 320dp/fontScale 1.8 and unknown size.
- Existing EliefLayoutTest/UpdateScreenTest: 19/20 passed. IME test could not observe an on-screen keyboard on this emulator, including after enabling show_ime_with_hard_keyboard. Test remains enabled. Initial startup ANRs resolved after package compilation; no application exception was observed.
- API28 and physical-device UI checks not repeated for this presentation-only change.

Detailed local logs: build/verification/crossapp-ui. Public distribution verification is recorded separately after push; these local results do not certify a new JitPack SHA.
