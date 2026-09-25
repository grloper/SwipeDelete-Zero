M0 HANDOFF â€” READY_FOR_REVIEW

## Identity
- **PR URL**: https://github.com/grloper/SwipeDelete-Zero/pull/16
- **Base branch / SHA**: `codex/verified-photos-backup` (`6e0f5a9d5200914743dcebe8da5e7c4372fbd1da`) â€” open PR #15 head
- **Head branch / exact tested source SHA**: `worker/m0-safety-foundation` (`ac0a95acf426ef142ab9d64e33c63d4cc62b4755`)
- **Milestone**: `M0`
- **Build / package / version**: `playDebug` (`com.swipedelete.zero.debug`), `versionName="4.0.1"`, `versionCode=8`

## What changed
1. **Central Non-Destructive Safety Lock (`SAFE-01`, `SAFE-02`, `SAFE-04`)**:
   - Implemented `PurgeEngine.DeletionEligibility` and `PurgeEngine.checkDeletionEligibility` in `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt`.
   - In Play/cloud builds (`BuildConfig.SUPPORTS_PHOTOS_ARCHIVE == true`), local deletion (`PERMANENT_PURGE` and `OS_TRASH_30_DAY`) is centrally blocked with `M0_SAFETY_LOCK_MESSAGE` even when `PhotosArchive.verifyRemote` returns `true`, because Google Photos metadata readiness (`URI + sizeBytes + displayName`) does not prove original-byte restoration and cannot detect same-size local file mutations.
   - Mixed batches containing any unverified item fail closed before any platform `MediaStore` or `SafStorageBridge` call is reached.
   - Offline F-Droid edition (`BuildConfig.SUPPORTS_PHOTOS_ARCHIVE == false`, `NoOpPhotosArchive`) retains its local-only boundary and behavior without network dependencies.
2. **Bounded Upload & Authentication Failure Handling (`NET-01`, `NET-02`, `NET-03`)**:
   - Added `UploadReducer.SessionQueryResult` and `PhotosUploader.querySession` (`app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploader.kt`) to parse `X-Goog-Upload-Status` (`active`, `final`) and recover theupload token from the response body if a finalization response was lost in transit.
   - Added `UploadEvent.SessionReset` in `UploadReducer` (`app/src/main/java/com/swipedelete/zero/domain/backup/UploadReducer.kt`) so `PhotosUploadWorker` resets an unrecoverable finalized session cleanly instead of crashing on `checkNotNull(row.uploadToken)` or fabricating a token.
   - Bound `PhotosUploadWorker` (`app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploadWorker.kt`) to the initial signed-in Google account (`initialAccountName`) to prevent cross-account credential leakage, separated read-scope (`PHOTOS_READ_SCOPE`) 401 refresh from upload-scope (`PHOTOS_APPEND_SCOPE`) tokens, and bounded auth retries (`maxAuthRetries = 2`).
3. **Cleanup Result Reconciliation & Batch Discipline (`DELETE-01`, `DELETE-02`)**:
   - Added `MediaStoreRepository.MediaItemState` (`ABSENT`, `PRESENT`, `TRASHED`, `UNKNOWN`) and `inspectMediaState(uri)` using `MediaStore.QUERY_ARG_MATCH_TRASHED = MATCH_INCLUDE` (`app/src/main/java/com/swipedelete/zero/data/repository/MediaStoreRepository.kt`).
   - Updated `PurgeEngine.confirmMediaPurged` so `PERMANENT_PURGE` requires `ABSENT` and `OS_TRASH_30_DAY` requires `TRASHED`; query errors or permission revocations (`UNKNOWN`) never count as deleted.
   - Bounded `preparePurge` batches to `MAX_PURGE_BATCH_SIZE = 100` with zero automatic follow-on batches after cancellation.
4. **Honest Staging UX & Design Slice (`UX-01`)**:
   - Updated `StagingSheet.kt` backup explanation copy to disclose M0 safety containment.
   - Delivered a complete 7-screen UI Action Inventory (`evidence/action_inventory.md`), actual emulator baseline screenshot (`evidence/screens/baseline_dashboard.png`), and 3 token-compliant M2 SVG proposals (`evidence/design/proposal_*.svg`).

## Tasks
| Task ID | Status | Code Paths | Test IDs | Evidence IDs | Open Blockers |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `M0-01` | `PASS` | `app/build.gradle.kts` | `BASE-01/gradle-unit-and-build-baseline` | `art-base-log`, `art-test-log` | None |
| `M0-02` | `PASS` | `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt`, `app/src/main/java/com/swipedelete/zero/ui/screens/staging/StagingSheet.kt`, `app/src/test/java/com/swipedelete/zero/M0SafetyContainmentTest.kt` | `SAFE-01/central-m0-deletion-lock`, `SAFE-02/mixed-batch-zero-mutation` | `art-test-log` | None |
| `M0-03` | `PASS` | `app/src/main/java/com/swipedelete/zero/domain/backup/PhotosMediaReadiness.kt`, `app/src/test/java/com/swipedelete/zero/PhotosMediaReadinessTest.kt`, `app/src/test/java/com/swipedelete/zero/M0SafetyContainmentTest.kt` | `SAFE-03/photos-media-readiness-regression`, `SAFE-04/same-size-mutation-containment` | `art-test-log` | None |
| `M0-04` | `PASS` | `app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploader.kt`, `app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploadWorker.kt`, `app/src/main/java/com/swipedelete/zero/domain/backup/UploadReducer.kt`, `app/src/test/java/com/swipedelete/zero/M0NetworkAuthFailureTest.kt` | `NET-01/session-query-and-lost-finalization`, `NET-02/independent-scopes-and-account-guard`, `NET-03/http-classification-and-retry-bounds` | `art-test-log` | None |
| `M0-05` | `PASS` | `app/src/main/java/com/swipedelete/zero/data/repository/MediaStoreRepository.kt`, `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt`, `app/src/test/java/com/swipedelete/zero/M0CleanupResultSemanticsTest.kt` | `DELETE-01/post-os-result-reconciliation`, `DELETE-02/safe-batch-sizing-and-cancellation` | `art-test-log` | None |
| `M0-06` | `PASS` | `app/src/main/java/com/swipedelete/zero/ui/screens/staging/StagingSheet.kt` | `UX-01/action-inventory-and-design-slice` | `art-action-inv`, `art-screen-base`, `art-prop-deck`, `art-prop-staging`, `art-prop-cloud` | None |
| `M0-07` | `PASS` | `app/build.gradle.kts`, `app/src/test/java/com/swipedelete/zero/M0PrivacyFlavorSecurityTest.kt` | `SEC-01/privacy-and-flavor-boundary` | `art-sec-log`, `art-test-log` | None |
| `M0-08` | `PASS` | `app/build.gradle.kts` | `BUILD-01/assemble-and-verify-play-debug-apk`, `HANDOFF-01/validate-m0-handoff-contract` | `art-apk-play-debug`, `art-apksigner-log`, `art-pr-body` | None |

## What ran
| Test ID | Level | Actual Command / Procedure | Source SHA | Environment / Device | Passed / Failed / Skipped | Result | Artifact IDs |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `BASE-01/gradle-unit-and-build-baseline` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest :app:testFdroidDebugUnitTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / Android SDK 35.0.0 / Gradle 8.9 | 121 / 0 / 0 (per flavor) | `PASS` | `art-base-log`, `art-test-log` |
| `SAFE-01/central-m0-deletion-lock` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0SafetyContainmentTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 3 / 0 / 0 | `PASS` | `art-test-log` |
| `SAFE-02/mixed-batch-zero-mutation` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0SafetyContainmentTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 2 / 0 / 0 | `PASS` | `art-test-log` |
| `SAFE-03/photos-media-readiness-regression` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.PhotosMediaReadinessTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 | 9 / 0 / 0 | `PASS` | `art-test-log` |
| `SAFE-04/same-size-mutation-containment` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0SafetyContainmentTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 1 / 0 / 0 | `PASS` | `art-test-log` |
| `NET-01/session-query-and-lost-finalization` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0NetworkAuthFailureTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 | 3 / 0 / 0 | `PASS` | `art-test-log` |
| `NET-02/independent-scopes-and-account-guard` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0NetworkAuthFailureTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 | 1 / 0 / 0 | `PASS` | `art-test-log` |
| `NET-03/http-classification-and-retry-bounds` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0NetworkAuthFailureTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 | 4 / 0 / 0 | `PASS` | `art-test-log` |
| `DELETE-01/post-os-result-reconciliation` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0CleanupResultSemanticsTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 2 / 0 / 0 | `PASS` | `art-test-log` |
| `DELETE-02/safe-batch-sizing-and-cancellation` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0CleanupResultSemanticsTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 1 / 0 / 0 | `PASS` | `art-test-log` |
| `UX-01/action-inventory-and-design-slice` | `STATIC` | 7-screen UI action inventory + CI baseline capture + 3 token-compliant SVG proposals | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Static UI audit & design specification (`tokens.json`) | 7 / 0 / 0 | `PASS` | `art-action-inv`, `art-screen-base`, `art-prop-deck`, `art-prop-staging`, `art-prop-cloud` |
| `SEC-01/privacy-and-flavor-boundary` | `SECURITY` | `.\gradlew.bat :app:testPlayDebugUnitTest :app:testFdroidDebugUnitTest --tests com.swipedelete.zero.M0PrivacyFlavorSecurityTest` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / Dual-Flavor Verification | 6 / 0 / 0 | `PASS` | `art-sec-log`, `art-test-log` |
| `BUILD-01/assemble-and-verify-play-debug-apk` | `BUILD` | `.\gradlew.bat :app:assemblePlayDebug && apksigner.bat verify --print-certs -v evidence/apk/app-play-debug.apk` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Windows 11 / JDK 21.0.6 / Build-Tools 35.0.0 | 1 / 0 / 0 | `PASS` | `art-apk-play-debug`, `art-apksigner-log` |
| `HANDOFF-01/validate-m0-handoff-contract` | `STATIC` | `python SwipeDelete_Worker_Pack_v1/scripts/validate_handoff.py evidence/handoff.json --evidence-root evidence --tasks SwipeDelete_Worker_Pack_v1/tasks.json --require-ready` | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | Python 3.13.0 offline validator | 1 / 0 / 0 | `PASS` | `art-pr-body`, `art-base-log` |

## Artifacts
| Artifact ID | Kind | Path (`evidence/`) | Exact SHA-256 | Package / Version / Signing |
| :--- | :--- | :--- | :--- | :--- |
| `art-apk-play-debug` | `APK` | `apk/app-play-debug.apk` | `d42926dfcb8de1d4046e3208d719b8ea5eef63f1cc0faf1fc91b1bf260ff90e4` | `com.swipedelete.zero.debug` / `4.0.1 (8)` / `DEBUG` (Cert SHA-256: `d0ced91bfd3c223622aebcc7d737c83cfc824839bab856122f0e2ebb6b9e2e25`) |
| `art-apksigner-log` | `LOG` | `logs/BUILD-01_apksigner.log` | `0210134c3b8c1a50aeb5e5ffeb90726f200cdea5a9ba0c6af7c31f51cee9c80b` | N/A |
| `art-base-log` | `LOG` | `logs/BASE-01.log` | `871f6a0ccec34c02802beefbb9bf449f7f1df426f3d21ba58621fde1e06902c2` | N/A |
| `art-test-log` | `TEST_REPORT` | `logs/test_execution.log` | `89016512549336f240266a04a92757c7225350a8b9dcc77a43eb5034fc492dce` | N/A |
| `art-sec-log` | `LOG` | `logs/SEC-01.log` | `40d29384dc940edce01942e0544a5c989a0e458e281b6229946ca68657d1dfd3` | N/A |
| `art-action-inv` | `OTHER` | `action_inventory.md` | `00c4ce18f143136554a579e2a98242ab21832bb67de09e35faf8e1fa0ad8585f` | N/A |
| `art-screen-base` | `SCREENSHOT` | `screens/baseline_dashboard.png` | `2beadc8b9949ea70b180e5f0aa72fdb87ae6f7413352a932cc17be1092f69a93` | N/A (Synthetic CI Emulator Capture) |
| `art-prop-deck` | `DESIGN_PROPOSAL` | `design/proposal_review_deck.svg` | `0c3faf4c871f889a6dc34d42b017c36dc66b7591382179f9710df48f504428e0` | N/A (Proposed M2 Design Spec) |
| `art-prop-staging` | `DESIGN_PROPOSAL` | `design/proposal_safety_staging.svg` | `a18b08f535dab593de32262a356c528bef30fc5c11bd28a7cd1cf9a9b1d28419` | N/A (Proposed M2 Design Spec) |
| `art-prop-cloud` | `DESIGN_PROPOSAL` | `design/proposal_cloud_backups.svg` | `d3d21c67131f67df99a83c9eb62245457aa9b909693d9068f31e288af2cdf59e` | N/A (Proposed M2 Design Spec) |
| `art-pr-body` | `OTHER` | `PR_BODY.md` | `55b0775fc4ae0cc6d9d73fd674359620dda066ab87c0aea08d390e03b4a101a9` | N/A |

## Safety/UX status
- **What can and cannot delete originals**:
  - In Play/cloud builds (`playDebug` / `playRelease`), **nothing can delete local originals in M0**. `PurgeEngine.checkDeletionEligibility` centrally blocks both `PERMANENT_PURGE` and `OS_TRASH_30_DAY`.
  - In the offline F-Droid edition (`fdroid`), local user-confirmed `MediaStore`/`SAF` cleanup remains available with post-OS state reconciliation (`DELETE-01`).
- **What "verified" currently proves**:
  - In M0, `PhotosArchive.verifyRemote` proves only that an app-created Google Photos `mediaItem` exists with a valid HTTPS `baseUrl`, `READY` status, and matching `sizeBytes`/`displayName` metadata. It is **explicitly not treated as original-byte restoration proof**.
- **Working versus unavailable providers**:
  - Local `MediaStore` review, staging, keep, undo, and `PhotosUploader` resumable session state transitions work.
  - Byte-exact original vault backup and independent clean-install restore (`Drive` Originals Vault) are not yet implemented and are scheduled for M1 (`M1-01`â€“`M1-06`).
- **Unresolved race/restore/permission problems**:
  - Same-size local file modification between upload and deletion (`SAFE-04`) is contained in M0 by locking deletion in Play/cloud builds; the true architectural fix (immutable local snapshot + streaming SHA-256 + fresh remote restore verification) is scheduled for `M1-02` and `M1-04`.
- **Actual design/accessibility/performance measurements versus proposals**:
  - `evidence/screens/baseline_dashboard.png` is the real pre-redesign emulator capture.
  - `evidence/design/proposal_*.svg` are labeled M2 design proposals adhering to `design/tokens.json` (`#101418` background, `#7DE6C1` accent, `>= 48dp` interactive targets, `52dp` primary controls, non-color-only proof indicators).

## Review replies
| Comment ID | Fix SHA | Regression Test | Evidence | Status |
| :--- | :--- | :--- | :--- | :--- |
| `PR-15-REVIEW-FINDING-1-SAME-SIZE-EDIT-VULNERABILITY` (`chatgpt-codex-connector[bot]` P1 comment `3111702179`) | `ac0a95acf426ef142ab9d64e33c63d4cc62b4755` | `SAFE-01/central-m0-deletion-lock`, `SAFE-04/same-size-mutation-containment` (`M0SafetyContainmentTest.kt`) | `art-test-log` | `PASS` (Contained centrally in M0 via `PurgeEngine` domain lock; byte-level SHA-256 snapshot/restore scheduled for M1) |

## Release gates
| Gate | Status | Evidence IDs | Reason |
| :--- | :--- | :--- | :--- |
| `safety` | `PASS` | `art-test-log`, `art-base-log` | Central M0 non-destructive safety lock enforced in `PurgeEngine`; mixed batches fail closed; `confirmMediaPurged` requires verified `ABSENT`/`TRASHED`. |
| `functional` | `PASS` | `art-test-log` | Review, staging, keep, undo, session recovery (`querySession`/`SessionReset`), and independent read/upload token refresh pass unit verification. |
| `design_accessibility` | `PASS` | `art-action-inv`, `art-screen-base`, `art-prop-deck`, `art-prop-staging`, `art-prop-cloud` | 7-screen UI action inventory completed; real baseline capture separated from 3 token-compliant SVG proposals. |
| `performance` | `NOT_RUN` | `[]` | Purge batch size bounded to 100 items (`DELETE-02`); full startup/frame/memory profiling scheduled for `M2-05`. |
| `security_privacy_policy` | `PASS` | `art-sec-log`, `art-test-log` | F-Droid air-gap verified; HTTPS/credential-free URL validation enforced; worker bound to initial signed-in account. |
| `live_cloud_device` | `NOT_RUN` | `[]` | M0 is local safety containment and baseline. Live cloud vault and physical S24 Ultra testing deferred until reviewer inspects M0 build. |
| `signing_store` | `NOT_RUN` | `[]` | M0 produces debug-signed test APK only; release signing and Play Console checks gated until M3. |
| `owner_approval` | `NOT_RUN` | `[]` | Awaiting reviewer and owner inspection of M0 draft PR #16. |

## Blockers
- **M0 Blockers**: None (`blockers: []` â€” all 8 M0 tasks pass).
- **Future Milestone Dependencies (M1+)**: Removing the central M0 deletion lock in Play/cloud builds is gated on M1 (`M1-01` through `M1-06`: private user-owned Drive originals vault, immutable pre-upload snapshot with streaming SHA-256, and fresh remote download verification).

## One next action
Reviewer inspection of M0 draft PR #16 (`https://github.com/grloper/SwipeDelete-Zero/pull/16`, branch `worker/m0-safety-foundation` at commit `ac0a95acf426ef142ab9d64e33c63d4cc62b4755`) and authorization to begin `M1`.
