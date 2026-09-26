# [M0] Safety foundation and auditable baseline

## Repository and scope
- **Repository**: `grloper/SwipeDelete-Zero`
- **Milestone**: `M0`
- **Base branch + SHA**: `codex/verified-photos-backup` (`6e0f5a9d5200914743dcebe8da5e7c4372fbd1da`)
- **Head branch + tested SHA**: `worker/m0-safety-foundation` (`dd720379c98ca012e114f5abecd75f670aa87124`)
- **Depends on PRs**: PR #15 (`codex/verified-photos-backup`, OPEN) and PR #14 (`codex/play-release-readiness`, OPEN)
- **Excluded from this PR**: M1–M5 roadmap items (Drive originals vault, Room snapshot/manifest migrations, clean-install remote restore, M2 visual token UI implementation, release signing, and iOS target).

## User-visible result
- **What now works or is deliberately blocked**:
  - **Deliberately Blocked (Central M0 Safety Containment)**: In Play/cloud builds, local deletion (`PERMANENT_PURGE` and `OS_TRASH_30_DAY`) is centrally locked in `PurgeEngine.checkDeletionEligibility`. Metadata-only Google Photos backup verification can no longer authorize deleting local originals.
  - **Working**: Local review decks, swipe-to-stage, keep, undo, unstage/restore queue actions, and the air-gapped F-Droid offline edition remain 100% functional.
  - **Repaired (Review 3 Close-Out)**:
    - **Guarded Disconnect & Cancellation (M0-V3-02)**: In `DriveCloudBackup`, added `currentSessionId`, active connection stream abort (`copyStreamWithCancellation`), coroutine context session validation (`isSessionActive()`, `checkSessionActive()`), and rethrows `CancellationException`. `signOut()` cleanly transitions to `SignedOut` without clearing newer session guards. In `PhotosUploadWorker`, added account/cancellation rechecks after token acquisition and before cloud calls; 404/410 handled as clean session reset.
    - **Room Migration Sentinels & Session Recovery (M0-V3-03)**: `DatabaseMigrationTest` seeds sentinel records across all 7 tables under v4, including `exclusions` and `media_analysis`, asserting preservation across migration and close/reopen. `PhotosUploadWorkerAuthTest` covers 5 HTTP/orchestration boundaries for session recovery (active partial offset, full offset, lost completion receipt, lost token, expired session 404/410). `PurgeBatchReconciliationTest` adds confirmation request counter asserting exactly 1 request before cancel and 0 after cancel.
    - **Truthful XML Evidence & Audit Tool (M0-V3-01 & M0-V3-04)**: `tools/audit_junit.py` and `tools/expected_skips.json` validate exact testsuite headers and cases without zeroing skips. Dual-flavor test suites produce 291 total tests (286 passed, 0 failures, 0 errors, 5 skipped) with documented flavor-specific applicability.
- **What the user should notice**:
  - `StagingSheet` honestly displays "Safety Staging" and explains: "Cleanup is unavailable in this test build. Your originals stay on this device." The cleanup button is disabled with truthful guidance.

## Review 3 Responses (Close-out — Review ID 5325884041)

| Finding ID | Fix Commit | Production Path | Executed Regression | Raw Evidence | Status | Remaining Limitation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **M0-V3-01** | `dd72037` | `tools/audit_junit.py`, `tools/expected_skips.json`, `validate_handoff.py` | Truthful JUnit XML count reporting without zeroing skips. Audited 57 testsuites via `audit_junit.py`: 291 total tests, 286 executed/passed, 0 failures, 0 errors, 5 skipped (Play: 160 total, 156 passed, 4 skipped; F-Droid: 131 total, 130 passed, 1 skipped). Skips documented with exact applicability reasons and counterpart passed tests. 14 audit tool self-tests passed. | `evidence/junit_audit.json`, `evidence/test-results/play/*.xml`, `evidence/test-results/fdroid/*.xml` | **PASS** | Skips reflect deliberate flavor boundaries (`assumeTrue(isPlayFlavor)` / `assumeFalse(isPlayFlavor)`), not unexecuted test requirements. |
| **M0-V3-02** | `dd72037` | `DriveCloudBackup.kt`, `PhotosUploadWorker.kt` | Guarded disconnect and cancellation semantics. Added `currentSessionId` (`AtomicLong`), active HTTP connection reference with stream abort (`copyStreamWithCancellation`), `isSessionActive()` and `checkSessionActive()` coroutine context validation. Rethrows `CancellationException` without recording failure. State cleanly transitions to `SignedOut` without clearing newer session guards. Added 4 deterministic unit tests in `DriveCloudBackupAuthTest`: (1) Auth callback signs out and returns token -> 0 folder resolver calls, 0 upload calls; (2) Upload callback observes sign-out and throws 401 -> 0 token refresh, 0 reupload after disconnect; (3) Per-file `CancellationException` is rethrown, not counted as failed; (4) Old job completion cannot clear new session guard or overwrite newer account state. In `PhotosUploadWorkerAuthTest`: Test 5 verifies disconnect observed after Photos read token acquisition before readback -> 0 `getMediaItem` calls, 0 ledger/staging writes. | `evidence/test-results/play/TEST-com.swipedelete.zero.backup.DriveCloudBackupAuthTest.xml`, `evidence/test-results/play/TEST-com.swipedelete.zero.photos.PhotosUploadWorkerAuthTest.xml` | **PASS** | Pre-existing TCP bytes already transmitted across the socket prior to client disconnect cannot be remotely cancelled at Google Drive/Photos server; client cleanly drops response, halts further operations, cancels session, and writes no ledger records. |
| **M0-V3-03** | `dd72037` | `DatabaseMigrationTest.kt`, `PhotosUploadWorker.kt`, `PhotosUploadWorkerAuthTest.kt`, `PurgeBatchReconciliationTest.kt` | (1) Real Room v4->v5 migration test with sentinel records seeded across all 7 tables under v4, including `exclusions` (hash `987654321L`) and `media_analysis` (pHash `22222L`, size `50000L`). Verified intact after migration and close/reopen cycle via DAO queries. Documented baseline schema origin in KDoc. (2) Added production-orchestrator HTTP-boundary coverage for 5 session recovery cases: active partial offset, full/out-of-range offset, lost completion receipt with token, final status with lost token, expired session 404/410. Verified persisted state, request counts, upload offsets, and bounded recovery. (3) Restored `sdkInt = Build.VERSION_CODES.R` in `PurgeBatchReconciliationTest`, verified non-empty pending batch cancellation, with `confirmationRequestCount` asserting exactly 1 confirmation request before cancel and 0 follow-on requests after cancel. | `evidence/test-results/play/TEST-com.swipedelete.zero.DatabaseMigrationTest.xml`, `evidence/test-results/fdroid/TEST-com.swipedelete.zero.DatabaseMigrationTest.xml`, `evidence/test-results/play/TEST-com.swipedelete.zero.photos.PhotosUploadWorkerAuthTest.xml`, `evidence/test-results/play/TEST-com.swipedelete.zero.PurgeBatchReconciliationTest.xml`, `evidence/test-results/fdroid/TEST-com.swipedelete.zero.PurgeBatchReconciliationTest.xml` | **PASS** | Live network HTTP boundaries use controlled in-memory transport fakes without live external Google account writes. |
| **M0-V3-04** | `dd72037` | `.github/workflows/play.yml`, `scripts/package_evidence.py`, `build_identity.json`, `handoff.json` | Full canonical CI integration and evidence packaging. `.github/workflows/play.yml` runs both `:app:testPlayDebugUnitTest` and `:app:testFdroidDebugUnitTest`, assembles Play debug APK, runs `audit_junit.py`, packages `evidence.zip` with independent external SHA-256 hash, and uploads `play-debug-apk` and `evidence-archive` artifacts. Updated `validate_handoff.py` to accept authorized cross-flavor skips in BASE-01. | `evidence/junit_audit.json`, `evidence/build_identity.json`, `evidence/SHA256SUMS.txt`, GitHub Actions Run | **PASS** | Delivered APK is a DEBUG-SIGNED debug binary for testing and review verification, not an unverified release or store binary. |

## Executed checks
1. **Play & F-Droid Unit Test Suites (`UNIT`)**:
   - Command: `.\gradlew.bat :app:testPlayDebugUnitTest :app:testFdroidDebugUnitTest`
   - Environment: Windows 11, JDK 21.0.6 (Android Studio JBR), Android SDK 36, Gradle 8.13
   - Exit code: `0`
   - Play Debug Suite: 30 test suites, 160 tests, 0 failures, 0 errors, 4 skipped
   - F-Droid Debug Suite: 27 test suites, 131 tests, 0 failures, 0 errors, 1 skipped
   - Total Unit Assertions Executed: 291 tests, 286 passed, 0 failures, 0 errors, 5 skipped
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

## Artifacts
- **APK filename**: `evidence/apk/app-play-debug.apk`
- **SHA-256**: `b6d57467851a08f6609dfd02ae92304a23abb4ea7f5dd1f0a1c36b40949ae9d9`
- **Package ID & Version**: `com.swipedelete.zero.debug` — `versionName="4.0.1"`, `versionCode=8`, `targetSdkVersion=36`, `minSdkVersion=29`
- **Build type/signature**: `playDebug` (`DEBUG-SIGNED`, APK Signature Scheme v2), Signer `CN=Android Debug, O=Android, C=US`, Certificate SHA-256: `d0ced91bfd3c223622aebcc7d737c83cfc824839bab856122f0e2ebb6b9e2e25`
- **No personal media or secrets in artifacts**: Confirmed (`contains_sensitive_data = false`).

## Risks and open blockers
- **Architectural Blocker (Gated to M1)**: Google Photos Library API does not guarantee byte-for-byte original restoration and re-encodes/strips certain containers. Therefore, local deletion in Play/cloud builds remains centrally locked in `PurgeEngine` until the M1 private user-owned Drive originals vault + fresh download SHA-256 verification (`M1-01` through `M1-06`) is implemented and accepted.
- **Physical Device Testing (Deferred to Post-Review)**: Per protocol, physical Samsung Galaxy S24 Ultra device testing is deferred until the reviewer inspects this verified M0 build.

## Owner action
Relay the evidence package archive to the reviewer.

## Review request
Review the code and evidence on branch `worker/m0-safety-foundation`. Do not infer store readiness from this draft PR.
