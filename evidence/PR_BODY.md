# [M0] Safety foundation and auditable baseline

## Repository and scope
- **Repository**: `grloper/SwipeDelete-Zero`
- **Milestone**: `M0`
- **Base branch + SHA**: `codex/verified-photos-backup` (`6e0f5a9d5200914743dcebe8da5e7c4372fbd1da`)
- **Head branch + tested SHA**: `worker/m0-safety-foundation` (`ac0a95acf426ef142ab9d64e33c63d4cc62b4755`)
- **Depends on PRs**: PR #15 (`codex/verified-photos-backup`, open) and PR #14 (merged into `main` at `c17a265694f3a84f342764ca042faee8607bc277`)
- **Excluded from this PR**: M1â€“M5 roadmap items (Drive originals vault, Room snapshot/manifest migrations, clean-install remote restore, M2 visual token UI implementation, release signing, and iOS target).

## User-visible result
- **What now works or is deliberately blocked**:
  - **Deliberately Blocked (Central M0 Safety Containment)**: In Play/cloud builds, local deletion (`PERMANENT_PURGE` and `OS_TRASH_30_DAY`) is centrally locked in `PurgeEngine.checkDeletionEligibility`. Metadata-only Google Photos backup verification can no longer authorize deleting local originals.
  - **Working**: Local review decks, swipe-to-stage, keep, undo, unstage/restore queue actions, and the air-gapped F-Droid offline edition remain 100% functional.
  - **Repaired**: Resumable upload session reconciliation (`querySession`) recovers finalized upload tokens or resets cleanly (`UploadEvent.SessionReset`) instead of crashing when a finalization receipt is lost; read vs. append OAuth tokens refresh independently with bounded retries and account-rotation protection; post-OS deletion reconciliation (`confirmMediaPurged`) distinguishes `ABSENT`, `PRESENT`, `TRASHED`, and `UNKNOWN` so unreadable/permission-lost items are never falsely marked deleted.
- **What the user should notice**:
  - `StagingSheet` honestly explains the M0 safety lock when items are verified in Google Photos, guiding the user that local originals remain protected until M1 original-byte restore verification lands.

## Safety changes
- **Entry points audited**: `PurgeEngine.preparePurge`, `PurgeEngine.checkDeletionEligibility`, `PurgeEngine.confirmMediaPurged`, `MediaStoreRepository.inspectMediaState`, `MediaStoreRepository.stillExists`, `SafStorageBridge`, `StagingSheet`, `PhotosUploadWorker`, `PhotosUploader`.
- **Proof/state transitions changed**:
  - Introduced `PurgeEngine.DeletionEligibility` (`Permitted` vs `Blocked(reason)`).
  - Enforced `M0_SAFETY_LOCK_MESSAGE` centrally in `PurgeEngine` whenever `photosArchive.isAvailable` is active, and blocked deletion when `BuildConfig.SUPPORTS_PHOTOS_ARCHIVE && !photosArchive.isAvailable`.
  - Added `UploadEvent.SessionReset` in `UploadReducer` to clear stale session state without fabricating an upload token.
  - Added `MediaStoreRepository.MediaItemState` (`ABSENT`, `PRESENT`, `TRASHED`, `UNKNOWN`) using `MediaStore.QUERY_ARG_MATCH_TRASHED = MATCH_INCLUDE`.
- **Source/account/race behavior**:
  - Contained same-size local file edit race (`SAFE-04`) via the central M0 domain lock while M1 immutable snapshot + SHA-256 streaming hash is pending.
  - Bound `PhotosUploadWorker` to the initial signed-in Google account (`initialAccountName`) and bounded 401 token refresh retries (`maxAuthRetries = 2`).
  - Bounded `preparePurge` batch size to `MAX_PURGE_BATCH_SIZE = 100` with zero implicit follow-on batches after cancellation.
- **Migration/rollback implications**:
  - Zero destructive database schema changes in M0; rollback-safe with existing Room schema.

## Task evidence
| Task ID | PASS / FAIL / BLOCKED / NOT_RUN | Code paths | Test IDs / artifact IDs | Remaining blocker |
|---|---|---|---|---|
| `M0-01` | `PASS` | `app/build.gradle.kts` | `BASE-01` / `art-base-log`, `art-test-log` | None |
| `M0-02` | `PASS` | `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt`, `app/src/main/java/com/swipedelete/zero/ui/screens/staging/StagingSheet.kt` | `SAFE-01`, `SAFE-02` / `art-test-log` | None |
| `M0-03` | `PASS` | `app/src/main/java/com/swipedelete/zero/domain/backup/PhotosMediaReadiness.kt`, `app/src/test/java/com/swipedelete/zero/M0SafetyContainmentTest.kt` | `SAFE-03`, `SAFE-04` / `art-test-log` | None |
| `M0-04` | `PASS` | `app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploader.kt`, `app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploadWorker.kt`, `app/src/main/java/com/swipedelete/zero/domain/backup/UploadReducer.kt` | `NET-01`, `NET-02`, `NET-03` / `art-test-log` | None |
| `M0-05` | `PASS` | `app/src/main/java/com/swipedelete/zero/data/repository/MediaStoreRepository.kt`, `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt` | `DELETE-01`, `DELETE-02` / `art-test-log` | None |
| `M0-06` | `PASS` | `evidence/action_inventory.md`, `evidence/design/proposal_review_deck.svg`, `evidence/design/proposal_safety_staging.svg`, `evidence/design/proposal_cloud_backups.svg` | `UX-01` / `art-action-inv`, `art-screen-base`, `art-prop-deck`, `art-prop-staging`, `art-prop-cloud` | None |
| `M0-07` | `PASS` | `app/build.gradle.kts`, `app/src/test/java/com/swipedelete/zero/M0PrivacyFlavorSecurityTest.kt` | `SEC-01` / `art-sec-log`, `art-test-log` | None |
| `M0-08` | `PASS` | `evidence/handoff.json`, `evidence/apk/app-play-debug.apk` | `BUILD-01`, `HANDOFF-01` / `art-apk-play-debug`, `art-apksigner-log` | None |

## Executed checks
1. **Play & F-Droid Unit Test Suites (`UNIT`)**:
   - Command: `.\gradlew.bat :app:testPlayDebugUnitTest :app:testFdroidDebugUnitTest`
   - Environment: Windows 11, JDK 21.0.6 (Android Studio JBR), Android SDK 35.0.0, Gradle 8.9
   - Commit: `ac0a95acf426ef142ab9d64e33c63d4cc62b4755`
   - Exit code: `0`
   - Counts: `121 passed, 0 failed, 0 skipped` per flavor (`242` total unit assertions executed across Play and F-Droid)
   - Raw artifact: `evidence/logs/test_execution.log`
2. **Play Debug APK Assembly & Signature Verification (`BUILD`)**:
   - Command: `.\gradlew.bat :app:assemblePlayDebug && apksigner.bat verify --print-certs -v evidence/apk/app-play-debug.apk`
   - Commit: `ac0a95acf426ef142ab9d64e33c63d4cc62b4755`
   - Exit code: `0`
   - Raw artifacts: `evidence/apk/app-play-debug.apk`, `evidence/logs/BUILD-01_apksigner.log`
3. **Handoff Contract Offline Validation (`STATIC`)**:
   - Command: `python SwipeDelete_Worker_Pack_v1/scripts/validate_handoff.py evidence/handoff.json --evidence-root evidence --tasks SwipeDelete_Worker_Pack_v1/tasks.json --require-ready`
   - Exit code: `0`

## Design evidence
- **Actual build captures versus labeled proposals**:
  - Real baseline capture (`evidence/screens/baseline_dashboard.png`) is strictly separated from proposed M2 SVG specifications (`evidence/design/proposal_review_deck.svg`, `evidence/design/proposal_safety_staging.svg`, `evidence/design/proposal_cloud_backups.svg`).
- **Light/dark/large-text/reduced-motion/accessibility states**:
  - Proposals use `design/tokens.json` dark theme palette (`#101418` background, `#191F25` surface, `#7DE6C1` accent, `#F4CE78` warning), `>= 48dp` minimum interactive targets, `52dp` primary button height, explicit non-swipe buttons (`Keep Original` / `Stage Cleanup`), and shape+icon+text status badges (never relying on color alone).
- **Asset provenance**:
  - Hand-authored SVG specifications and synthetic emulator smoke capture; zero bundled font files or personal media.

## Artifacts
- **APK filename**: `evidence/apk/app-play-debug.apk` (`app/build/outputs/apk/play/debug/app-play-debug.apk`)
- **SHA-256**: `d42926dfcb8de1d4046e3208d719b8ea5eef63f1cc0faf1fc91b1bf260ff90e4`
- **Package/bundle ID and version**: `com.swipedelete.zero.debug` â€” `versionName="4.0.1"`, `versionCode=8`
- **Build type/signature/certificate fingerprint**: `playDebug` (`DEBUG`, APK Signature Scheme v2), Signer `CN=Android Debug, O=Android, C=US`, Certificate SHA-256: `d0ced91bfd3c223622aebcc7d737c83cfc824839bab856122f0e2ebb6b9e2e25`
- **Tested source SHA**: `ac0a95acf426ef142ab9d64e33c63d4cc62b4755`
- **No personal media or secrets in artifacts**: Confirmed (`contains_sensitive_data = false`).

## Risks and open blockers
- **Architectural Blocker (Gated to M1)**: Google Photos Library API does not guarantee byte-for-byte original restoration and re-encodes/strips certain containers. Therefore, local deletion in Play/cloud builds remains centrally locked in `PurgeEngine` until the M1 private user-owned Drive originals vault + fresh download SHA-256 verification (`M1-01` through `M1-06`) is implemented and accepted.
- **Unrun Environments**: Live Google Cloud / Drive round-trip (`REAL_CLOUD`) and physical Samsung Galaxy S24 Ultra device testing (`DEVICE_MANUAL`) were not executed in M0 per protocol ("do not ask the owner to run destructive or real-account tests until the reviewer inspects the M0 build").

## Owner action
None before reviewer inspection.

## Review request
Review the code/evidence at the stated SHA (`ac0a95acf426ef142ab9d64e33c63d4cc62b4755`). Do not infer store readiness from this draft PR. No merge/distribution authorization is implied.
