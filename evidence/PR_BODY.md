# [M0] Safety foundation and auditable baseline

## Repository and scope
- **Repository**: `grloper/SwipeDelete-Zero`
- **Milestone**: `M0`
- **Base branch + SHA**: `codex/verified-photos-backup` (`6e0f5a9d5200914743dcebe8da5e7c4372fbd1da`)
- **Head branch + tested SHA**: `worker/m0-safety-foundation` (`c69c6fd4da0836514dab62d13ca1d40b33587c3d`)
- **Depends on PRs**: PR #15 (`codex/verified-photos-backup`, OPEN) and PR #14 (`codex/play-release-readiness`, OPEN)
- **Excluded from this PR**: M1–M5 roadmap items (Drive originals vault, Room snapshot/manifest migrations, clean-install remote restore, M2 visual token UI implementation, release signing, and iOS target).

## User-visible result
- **What now works or is deliberately blocked**:
  - **Deliberately Blocked (Central M0 Safety Containment)**: In Play/cloud builds, local deletion (`PERMANENT_PURGE` and `OS_TRASH_30_DAY`) is centrally locked in `PurgeEngine.checkDeletionEligibility`. Metadata-only Google Photos backup verification can no longer authorize deleting local originals.
  - **Working**: Local review decks, swipe-to-stage, keep, undo, unstage/restore queue actions, and the air-gapped F-Droid offline edition remain 100% functional.
  - **Repaired**:
    - **4-State Media Visibility (M0-V2-01)**: Media inspection and purge planning preserve `UNKNOWN` (ambiguous/revoked/type-isolated access) in the queue. Only confirmed `ABSENT` and `TRASHED` items are reconciled from queue, without crediting reclaimed storage. Unrelated permission grants (e.g. audio-only grant) never prove visual media visibility.
    - **Guarded Disconnect & Cancellation (M0-V2-02)**: In `PhotosUploadWorker`, `checkAccountActive()` runs before persisting `RemoteVerified`. Writes to `STATE_VERIFIED`, backup ledger, and staging table execute atomically. Append-token acquisition/refresh preserves and rethrows `CancellationException`. In `DriveCloudBackup`, `signOut()` actively cancels running backup jobs, sets `SignedOut`, and guards against post-disconnect file creation or status overwrites.
    - **Real Room v4-to-v5 Migration (M0-V2-03)**: Verified on native SQLite database seeded with 7 tables of in-flight, verified, and failed states; validates schema with Room v5, isolates unowned active rows to `STATE_FAILED` with `accountName NULL`, preserves intact all existing records, closes and reopens cleanly with DAO verification. Destructive fallback confirmed absent.
    - **Behavioral Cancellation & Truthful Coverage (M0-V2-04)**: Tested non-empty batch cancellation preserving queue, resetting pending state, and triggering no follow-on batches. Resumable session parser tested with full transport/orchestrator boundary. Flavor-specific tests use explicit JUnit assumptions to record truthful `<skipped/>` tags in XML (no silent early-return passes).
    - **Reproducible Evidence Packaging (M0-V2-05)**: Built from clean source commit C (`c69c6fd`), verified with Android tools (`aapt2`, `apksigner`), actual Android 36 emulator screen captures, and external archive checksum validation.
- **What the user should notice**:
  - `StagingSheet` honestly displays "Safety Staging" and explains: "Cleanup is unavailable in this test build. Your originals stay on this device." The cleanup button is disabled with truthful guidance.

## Safety changes
- **Entry points audited**: `PurgeEngine.preparePurge`, `PurgeEngine.checkDeletionEligibility`, `PurgeEngine.confirmMediaPurged`, `MediaStoreRepository.inspectMediaState`, `StoragePermissionManager.hasAccessFor`, `StagingSheet`, `PhotosUploadWorker`, `DriveCloudBackup`.
- **Proof/state transitions changed**:
  - Four distinct media item states: `PRESENT`, `ABSENT`, `TRASHED`, and `UNKNOWN`.
  - Storage permissions evaluated per collection/media type; audio access does not grant visual access.
  - Disconnect checks atomic with success/ledger writes.
  - Real Room migration v4->v5 isolates unowned active uploads.
- **Source/account/race behavior**:
  - Contained same-size local file edit race via the central M0 domain lock while M1 immutable snapshot + SHA-256 streaming hash is pending.
  - Bound `PhotosUploadWorker` and `DriveCloudBackup` to signed-in Google account with disconnect cancellation.
  - Bounded `preparePurge` batch size to `MAX_PURGE_BATCH_SIZE = 100` with zero implicit follow-on batches after cancellation.

## Task evidence
| Task ID | Status | Code paths | Test IDs / artifact IDs | Remaining blocker |
|---|---|---|---|---|
| `M0-01` | `PASS` | `app/build.gradle.kts` | `BASE-01` / `art-base-log`, `art-test-log` | None |
| `M0-02` | `PASS` | `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt`, `app/src/main/java/com/swipedelete/zero/ui/screens/staging/StagingSheet.kt` | `SAFE-01`, `SAFE-02` / `art-test-log` | None |
| `M0-03` | `PASS` | `app/src/main/java/com/swipedelete/zero/domain/backup/PhotosMediaReadiness.kt`, `app/src/test/java/com/swipedelete/zero/M0SafetyContainmentTest.kt` | `SAFE-03`, `SAFE-04` / `art-test-log` | None |
| `M0-04` | `PASS` | `app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploader.kt`, `app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploadWorker.kt`, `app/src/cloud/java/com/swipedelete/zero/backup/DriveCloudBackup.kt` | `NET-01`, `NET-02`, `NET-03` / `art-test-log` | None |
| `M0-05` | `PASS` | `app/src/main/java/com/swipedelete/zero/data/repository/MediaStoreRepository.kt`, `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt`, `app/src/main/java/com/swipedelete/zero/data/repository/StoragePermissionManager.kt` | `DELETE-01`, `DELETE-02` / `art-test-log` | None |
| `M0-06` | `PASS` | `evidence/action_inventory.md`, `evidence/design/proposal_review_deck.svg`, `evidence/design/proposal_safety_staging.svg`, `evidence/design/proposal_cloud_backups.svg` | `UX-01` / `art-action-inv`, `art-screen-base`, `art-prop-deck`, `art-prop-staging`, `art-prop-cloud` | None |
| `M0-07` | `PASS` | `app/build.gradle.kts`, `app/src/test/java/com/swipedelete/zero/M0PrivacyFlavorSecurityTest.kt` | `SEC-01` / `art-sec-log`, `art-test-log` | None |
| `M0-08` | `PASS` | `evidence/handoff.json`, `evidence/apk/app-play-debug.apk` | `BUILD-01`, `HANDOFF-01` / `art-apk-play-debug`, `art-apksigner-log` | None |

## Executed checks
1. **Play & F-Droid Unit Test Suites (`UNIT`)**:
   - Command: `.\gradlew.bat :app:testPlayDebugUnitTest :app:testFdroidDebugUnitTest`
   - Environment: Windows 11, JDK 21.0.6 (Android Studio JBR), Android SDK 36, Gradle 8.13
   - Source Commit Tested: `c69c6fd4da0836514dab62d13ca1d40b33587c3d`
   - Exit code: `0`
   - Play Debug Suite: 30 test suites, 150 tests, 0 failures, 0 errors, 4 skipped
   - F-Droid Debug Suite: 27 test suites, 131 tests, 0 failures, 0 errors, 1 skipped
   - Total Unit Assertions Executed: 281 tests, 0 failures, 0 errors, 5 skipped
   - Raw artifact: `evidence/logs/test_execution.log`, `evidence/test-results/play/*.xml`, `evidence/test-results/fdroid/*.xml`
2. **Play Debug APK Assembly & Signature Verification (`BUILD`)**:
   - Command: `.\gradlew.bat :app:assemblePlayDebug && apksigner.bat verify --print-certs -v evidence/apk/app-play-debug.apk`
   - Exit code: `0`
   - Raw artifacts: `evidence/apk/app-play-debug.apk`, `evidence/logs/BUILD-01_apksigner.log`
3. **Live Emulator UI Smoke Captures (`EMULATOR`)**:
   - Target: Android 36 (`emulator-5554`, API 36, x86_64)
   - Raw artifacts: `evidence/screens/current_build_m0_dashboard.png`, `evidence/screens/current_build_m0_staging_lock.png`, `evidence/screens/current_build_staging_window.xml`
4. **Handoff Contract Offline Validation (`STATIC`)**:
   - Command: `python SwipeDelete_Worker_Pack_v1/scripts/validate_handoff.py evidence/handoff.json --evidence-root evidence --tasks SwipeDelete_Worker_Pack_v1/tasks.json --require-ready`
   - Exit code: `0`

## Design evidence
- **Actual build captures versus labeled proposals**:
  - Live emulator current-build captures (`evidence/screens/current_build_m0_dashboard.png`, `evidence/screens/current_build_m0_staging_lock.png`, `evidence/screens/current_build_staging_window.xml`) demonstrate actual running UI and locked cleanup state on Android 36.
  - Baseline capture (`evidence/screens/baseline_dashboard.png`) and legacy docs mockups (`evidence/screens/legacy_reference_design_dashboard.png`, `legacy_reference_design_staging.png`) are distinctly labeled as legacy reference material.
  - Future M2 proposals (`evidence/design/proposal_review_deck.svg`, `evidence/design/proposal_safety_staging.svg`, `evidence/design/proposal_cloud_backups.svg`) remain separated.
- **Accessibility & Contrast**:
  - Minimum touch targets >= 48dp, explicit non-swipe action buttons, shape+text+color status badges.

## Artifacts
- **APK filename**: `evidence/apk/app-play-debug.apk`
- **SHA-256**: `aec56c17547bce36d4fd3ed802ac8744b67ff693a7ff98737366e64399575601`
- **Package ID & Version**: `com.swipedelete.zero.debug` — `versionName="4.0.1"`, `versionCode=8`, `targetSdkVersion=36`, `minSdkVersion=29`
- **Build type/signature**: `playDebug` (`DEBUG-SIGNED`, APK Signature Scheme v2), Signer `CN=Android Debug, O=Android, C=US`, Certificate SHA-256: `d0ced91bfd3c223622aebcc7d737c83cfc824839bab856122f0e2ebb6b9e2e25`
- **Tested source SHA**: `c69c6fd4da0836514dab62d13ca1d40b33587c3d`
- **No personal media or secrets in artifacts**: Confirmed (`contains_sensitive_data = false`).

## Risks and open blockers
- **Architectural Blocker (Gated to M1)**: Google Photos Library API does not guarantee byte-for-byte original restoration and re-encodes/strips certain containers. Therefore, local deletion in Play/cloud builds remains centrally locked in `PurgeEngine` until the M1 private user-owned Drive originals vault + fresh download SHA-256 verification (`M1-01` through `M1-06`) is implemented and accepted.
- **Physical Device Testing (Deferred to Post-Review)**: Per protocol, physical Samsung Galaxy S24 Ultra device testing is deferred until the reviewer inspects this verified M0 build.

## Owner action
Relay the evidence package archive to the reviewer.

## Review request
Review the code and evidence at commit `c69c6fd4da0836514dab62d13ca1d40b33587c3d`. Do not infer store readiness from this draft PR.
