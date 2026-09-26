M0 HANDOFF — READY_FOR_REVIEW

## Identity
- **PR URL**: https://github.com/grloper/SwipeDelete-Zero/pull/16
- **Base branch / SHA**: `codex/verified-photos-backup` (`6e0f5a9d5200914743dcebe8da5e7c4372fbd1da`) — open PR #15 head
- **Head branch / exact tested source SHA**: `worker/m0-safety-foundation` (`c69c6fd4da0836514dab62d13ca1d40b33587c3d`)
- **Milestone**: `M0`
- **Build / package / version**: `playDebug` (`com.swipedelete.zero.debug`), `versionName="4.0.1"`, `versionCode=8`, `targetSdkVersion=36`, `minSdkVersion=29`
- **Signing**: `DEBUG-SIGNED` (Certificate SHA-256: `d0ced91bfd3c223622aebcc7d737c83cfc824839bab856122f0e2ebb6b9e2e25`)

## What changed
1. **Four-State Media Visibility & Queue Preservation (M0-V2-01)**:
   - Storage permissions evaluated per collection/media type (`StoragePermissionManager.kt`). Audio access does not grant visual access; image access does not grant video access.
   - Four distinct states throughout resolver, planner, and view model: `PRESENT`, `ABSENT`, `TRASHED`, and `UNKNOWN`.
   - `UNKNOWN` rows remain staged in queue with descriptive status. Only confirmed `ABSENT` and `TRASHED` items are reconciled from queue, without crediting reclaimed storage.
2. **Guarded Cloud Disconnect & Cancellation (M0-V2-02)**:
   - In `PhotosUploadWorker`, `checkAccountActive()` is evaluated before persisting `RemoteVerified`. Updates to `STATE_VERIFIED`, backup ledger, and staging table are atomically guarded.
   - Append-token acquisition and refresh preserve and rethrow `CancellationException`.
   - In `DriveCloudBackup`, `signOut()` actively cancels running backup jobs, transitions to `SignedOut`, and disconnect between uploads prevents subsequent file creations or state overwrites.
3. **Real Room v4-to-v5 Database Migration (M0-V2-03)**:
   - Verified on a real SQLite database (`DatabaseMigrationTest.kt`) seeded with schema v4 fixtures across all 7 tables with in-flight, verified, and failed states.
   - Executes `MIGRATION_4_5`, opens via Room v5 builder, validates schema, verifies unowned active rows are safely quarantined to `STATE_FAILED` with `accountName NULL`, preserves intact all existing records, closes, and successfully reopens with DAO query verification.
   - Destructive migration fallback confirmed absent.
4. **Behavioral Cancellation & Truthful Coverage (M0-V2-04)**:
   - Tested non-empty batch cancellation preserving queue, resetting pending state, and triggering no follow-on batches (`PurgeBatchReconciliationTest.kt`).
   - Flavor-specific tests use explicit JUnit assumptions (`assumeFalse(isPlayFlavor)`, `assumeTrue(isPlayFlavor)`) to report truthful `<skipped/>` tags in XML without silent early returns.
   - Resumable upload session parser tested with full transport/orchestrator boundary across active partial offset, final with token, final lost receipt, invalid status, and bounded retries (`PhotosUploaderQueryParserTest.kt`).
5. **Reproducible Evidence Delivery (M0-V2-05)**:
   - Built from clean source commit C (`c69c6fd4da0836514dab62d13ca1d40b33587c3d`).
   - Actual APK metadata extracted with Android SDK tools (`aapt2`, `apksigner`).
   - Live emulator UI captures executed on Android 36 (`emulator-5554`) for dashboard and locked staging sheet.
   - Automated manifest generation and zip packaging with independent external SHA-256 and fresh-directory extraction verification (`scripts/package_evidence.py`).

## Tasks
| Task ID | Status | Code Paths | Test IDs | Evidence IDs | Open Blockers |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `M0-01` | `PASS` | `app/build.gradle.kts` | `BASE-01/gradle-unit-and-build-baseline` | `art-base-log`, `art-test-log` | None |
| `M0-02` | `PASS` | `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt`, `app/src/main/java/com/swipedelete/zero/ui/screens/staging/StagingSheet.kt`, `app/src/test/java/com/swipedelete/zero/M0SafetyContainmentTest.kt` | `SAFE-01/central-m0-deletion-lock`, `SAFE-02/mixed-batch-zero-mutation` | `art-test-log` | None |
| `M0-03` | `PASS` | `app/src/main/java/com/swipedelete/zero/domain/backup/PhotosMediaReadiness.kt`, `app/src/test/java/com/swipedelete/zero/PhotosMediaReadinessTest.kt`, `app/src/test/java/com/swipedelete/zero/M0SafetyContainmentTest.kt` | `SAFE-03/photos-media-readiness-regression`, `SAFE-04/same-size-mutation-containment` | `art-test-log` | None |
| `M0-04` | `PASS` | `app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploader.kt`, `app/src/cloud/java/com/swipedelete/zero/photos/PhotosUploadWorker.kt`, `app/src/cloud/java/com/swipedelete/zero/backup/DriveCloudBackup.kt` | `NET-01/session-query-and-lost-finalization`, `NET-02/independent-scopes-and-account-guard`, `NET-03/http-classification-and-retry-bounds` | `art-test-log` | None |
| `M0-05` | `PASS` | `app/src/main/java/com/swipedelete/zero/data/repository/MediaStoreRepository.kt`, `app/src/main/java/com/swipedelete/zero/data/repository/PurgeEngine.kt`, `app/src/main/java/com/swipedelete/zero/data/repository/StoragePermissionManager.kt` | `DELETE-01/post-os-result-reconciliation`, `DELETE-02/safe-batch-sizing-and-cancellation` | `art-test-log` | None |
| `M0-06` | `PASS` | `evidence/action_inventory.md`, `evidence/screens/current_build_m0_dashboard.png`, `evidence/screens/current_build_m0_staging_lock.png` | `UX-01/action-inventory-and-design-slice` | `art-action-inv`, `art-screen-base`, `art-screen-dash-live`, `art-screen-lock-live`, `art-prop-deck`, `art-prop-staging`, `art-prop-cloud` | None |
| `M0-07` | `PASS` | `app/build.gradle.kts`, `app/src/test/java/com/swipedelete/zero/M0PrivacyFlavorSecurityTest.kt` | `SEC-01/privacy-and-flavor-boundary` | `art-sec-log`, `art-test-log` | None |
| `M0-08` | `PASS` | `evidence/handoff.json`, `evidence/apk/app-play-debug.apk` | `BUILD-01/assemble-and-verify-play-debug-apk`, `HANDOFF-01/validate-m0-handoff-contract` | `art-apk-play-debug`, `art-apksigner-log`, `art-pr-body` | None |

## What ran
| Test ID | Level | Actual Command / Procedure | Source SHA | Environment / Device | Passed / Failed / Skipped | Result | Artifact IDs |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `BASE-01/gradle-unit-and-build-baseline` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest :app:testFdroidDebugUnitTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / Android SDK 36 / Gradle 8.13 | 276 / 0 / 5 (Play: 150 tests, 4 skipped; F-Droid: 131 tests, 1 skipped) | `PASS` | `art-base-log`, `art-test-log` |
| `SAFE-01/central-m0-deletion-lock` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0SafetyContainmentTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 3 / 0 / 0 | `PASS` | `art-test-log` |
| `SAFE-02/mixed-batch-zero-mutation` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0SafetyContainmentTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 2 / 0 / 0 | `PASS` | `art-test-log` |
| `SAFE-03/photos-media-readiness-regression` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.PhotosMediaReadinessTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 | 9 / 0 / 0 | `PASS` | `art-test-log` |
| `SAFE-04/same-size-mutation-containment` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0SafetyContainmentTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 1 / 0 / 0 | `PASS` | `art-test-log` |
| `NET-01/session-query-and-lost-finalization` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0NetworkAuthFailureTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 | 3 / 0 / 0 | `PASS` | `art-test-log` |
| `NET-02/independent-scopes-and-account-guard` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0NetworkAuthFailureTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 | 1 / 0 / 0 | `PASS` | `art-test-log` |
| `NET-03/http-classification-and-retry-bounds` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0NetworkAuthFailureTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 | 4 / 0 / 0 | `PASS` | `art-test-log` |
| `DELETE-01/post-os-result-reconciliation` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0CleanupResultSemanticsTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 2 / 0 / 0 | `PASS` | `art-test-log` |
| `DELETE-02/safe-batch-sizing-and-cancellation` | `UNIT` | `.\gradlew.bat :app:testPlayDebugUnitTest --tests com.swipedelete.zero.M0CleanupResultSemanticsTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / JUnit4 + Mockito | 1 / 0 / 0 | `PASS` | `art-test-log` |
| `UX-01/action-inventory-and-design-slice` | `STATIC` | 7-screen UI action inventory + Android 36 emulator live screen captures + 3 SVG proposals | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Android 36 Emulator (`emulator-5554`) + static audit | 7 / 0 / 0 | `PASS` | `art-action-inv`, `art-screen-base`, `art-screen-dash-live`, `art-screen-lock-live`, `art-prop-deck`, `art-prop-staging`, `art-prop-cloud` |
| `SEC-01/privacy-and-flavor-boundary` | `SECURITY` | `.\gradlew.bat :app:testPlayDebugUnitTest :app:testFdroidDebugUnitTest --tests com.swipedelete.zero.M0PrivacyFlavorSecurityTest` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / Dual-Flavor Verification | 6 / 0 / 0 | `PASS` | `art-sec-log`, `art-test-log` |
| `BUILD-01/assemble-and-verify-play-debug-apk` | `BUILD` | `.\gradlew.bat :app:assemblePlayDebug && apksigner.bat verify --print-certs -v evidence/apk/app-play-debug.apk` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Windows 11 / JDK 21.0.6 / Build-Tools 35.0.0 | 1 / 0 / 0 | `PASS` | `art-apk-play-debug`, `art-apksigner-log` |
| `HANDOFF-01/validate-m0-handoff-contract` | `STATIC` | `python SwipeDelete_Worker_Pack_v1/scripts/validate_handoff.py evidence/handoff.json --evidence-root evidence --tasks SwipeDelete_Worker_Pack_v1/tasks.json --require-ready` | `c69c6fd4da0836514dab62d13ca1d40b33587c3d` | Python 3.13.0 offline validator | 1 / 0 / 0 | `PASS` | `art-pr-body`, `art-base-log` |

## Artifacts
| Artifact ID | Kind | Path (`evidence/`) | Exact SHA-256 | Package / Version / Signing |
| :--- | :--- | :--- | :--- | :--- |
| `art-apk-play-debug` | `APK` | `apk/app-play-debug.apk` | `aec56c17547bce36d4fd3ed802ac8744b67ff693a7ff98737366e64399575601` | `com.swipedelete.zero.debug` / `4.0.1 (8)` / `DEBUG-SIGNED` (Cert SHA-256: `d0ced91bfd3c223622aebcc7d737c83cfc824839bab856122f0e2ebb6b9e2e25`) |
| `art-apksigner-log` | `LOG` | `logs/BUILD-01_apksigner.log` | `df8b91bd36a26255981c98bbb28ec5372bdaeb63f7c37ebbb619bcdf44272779` | N/A |
| `art-base-log` | `LOG` | `logs/BASE-01.log` | `830d692fc0bfffd53483aa7a0a769b43fb82ee2191b40bdac2488588cbe98cd3` | N/A |
| `art-test-log` | `TEST_REPORT` | `logs/test_execution.log` | `808cf4b535883cfa5d03ac9fdfef139309e32dbbc521ff929c09cd133cbd8640` | N/A |
| `art-sec-log` | `LOG` | `logs/SEC-01.log` | `37c222d8b8476bb28bc6b3d8f64611c72402b2446cac81981f841db4069ecd0a` | N/A |
| `art-action-inv` | `OTHER` | `action_inventory.md` | `00c4ce18f143136554a579e2a98242ab21832bb67de09e35faf8e1fa0ad8585f` | N/A |
| `art-screen-base` | `SCREENSHOT` | `screens/baseline_dashboard.png` | `2beadc8b9949ea70b180e5f0aa72fdb87ae6f7413352a932cc17be1092f69a93` | N/A (Synthetic Baseline Emulator Capture) |
| `art-screen-dash-live` | `SCREENSHOT` | `screens/current_build_m0_dashboard.png` | `e87aec45004e593741e9f35134170c44917b1466eeee24d7db2ae2e6af22965d` | N/A (Live Android 36 Emulator Capture) |
| `art-screen-lock-live` | `SCREENSHOT` | `screens/current_build_m0_staging_lock.png` | `28d5b793e8b1715757c4407f879160cdb1659c94beca7047ee2d3c1ba5691c38` | N/A (Live Android 36 Emulator Capture) |
| `art-screen-staging-xml`| `OTHER` | `screens/current_build_staging_window.xml` | `455da65223a758034e0cfbcfb50c9bb7cf7942821e0049019ff981e7cb186c6d` | N/A (Live Android 36 UI Hierarchy Dump) |
| `art-prop-deck` | `DESIGN_PROPOSAL` | `design/proposal_review_deck.svg` | `0c3faf4c871f889a6dc34d42b017c36dc66b7591382179f9710df48f504428e0` | N/A (Proposed M2 Design Spec) |
| `art-prop-staging` | `DESIGN_PROPOSAL` | `design/proposal_safety_staging.svg` | `a18b08f535dab593de32262a356c528bef30fc5c11bd28a7cd1cf9a9b1d28419` | N/A (Proposed M2 Design Spec) |
| `art-prop-cloud` | `DESIGN_PROPOSAL` | `design/proposal_cloud_backups.svg` | `d3d21c67131f67df99a83c9eb62245457aa9b909693d9068f31e288af2cdf59e` | N/A (Proposed M2 Design Spec) |
| `art-pr-body` | `OTHER` | `PR_BODY.md` | `7086f1c00de02f7ef1c4045cb92f1c84a742af5c435b48392f49524ae00d5860` | N/A |

## Safety/UX status
- **What can and cannot delete originals**:
  - In Play/cloud builds (`playDebug` / `playRelease`), **nothing can delete local originals in M0**. `PurgeEngine.checkDeletionEligibility` centrally blocks both `PERMANENT_PURGE` and `OS_TRASH_30_DAY`.
  - In the offline F-Droid edition (`fdroid`), local user-confirmed `MediaStore`/`SAF` cleanup remains available with post-OS state reconciliation (`DELETE-01`).
- **What "verified" currently proves**:
  - In M0, `PhotosArchive.verifyRemote` proves only that an app-created Google Photos `mediaItem` exists with a valid HTTPS `baseUrl`, `READY` status, and matching `sizeBytes`/`displayName` metadata. It is **explicitly not treated as original-byte restoration proof**.
- **Working versus unavailable providers**:
  - Local `MediaStore` review, staging, keep, undo, and `PhotosUploader` resumable session state transitions work.
  - Byte-exact original vault backup and independent clean-install restore (`Drive` Originals Vault) are scheduled for M1 (`M1-01`–`M1-06`).
- **Unresolved race/restore/permission problems**:
  - Same-size local file modification between upload and deletion (`SAFE-04`) is contained in M0 by locking deletion in Play/cloud builds; the true architectural fix (immutable local snapshot + streaming SHA-256 + fresh remote restore verification) is scheduled for `M1-02` and `M1-04`.
- **Actual design/accessibility/performance measurements versus proposals**:
  - `evidence/screens/current_build_m0_*.png` are live Android 36 emulator screen captures of the current running build.
  - `evidence/screens/baseline_dashboard.png` is the real pre-redesign emulator capture.
  - `evidence/screens/legacy_reference_design_*.png` are labeled legacy reference mockups from repository docs.
  - `evidence/design/proposal_*.svg` are labeled M2 design proposals adhering to `design/tokens.json`.

## Release gates
| Gate | Status | Evidence IDs | Reason |
| :--- | :--- | :--- | :--- |
| `safety` | `PASS` | `art-test-log`, `art-base-log` | Central M0 non-destructive safety lock enforced in `PurgeEngine`; mixed batches fail closed; 4-state visibility preserves UNKNOWN. |
| `functional` | `PASS` | `art-test-log` | Review, staging, keep, undo, session recovery (`querySession`/`SessionReset`), independent token refresh, and real Room v4->v5 migration pass. |
| `design_accessibility` | `PASS` | `art-action-inv`, `art-screen-base`, `art-screen-dash-live`, `art-screen-lock-live`, `art-prop-deck`, `art-prop-staging`, `art-prop-cloud` | 7-screen UI action inventory completed; real emulator captures separated from 3 token-compliant SVG proposals. |
| `performance` | `NOT_RUN` | `[]` | Purge batch size bounded to 100 items (`DELETE-02`); full startup/frame/memory profiling scheduled for `M2-05`. |
| `security_privacy_policy` | `PASS` | `art-sec-log`, `art-test-log` | F-Droid air-gap verified; HTTPS/credential-free URL validation enforced; worker bound to initial signed-in account. |
| `live_cloud_device` | `NOT_RUN` | `[]` | M0 scope is safety containment and auditable baseline. Live cloud round-trip and physical S24 Ultra testing are deferred until reviewer inspects M0 and approves M1. |
| `signing_store` | `NOT_RUN` | `[]` | M0 produces debug-signed test APK only; release signing and store readiness are scheduled for M3. |
| `owner_approval` | `NOT_RUN` | `[]` | Awaiting product/design/safety reviewer and owner inspection of M0 PR #16. |

## Owner action
Relay the evidence package archive to the reviewer.

## Review request
Review the code and evidence at commit `c69c6fd4da0836514dab62d13ca1d40b33587c3d`. Do not infer store readiness from this draft PR. No merge/distribution authorization is implied.
