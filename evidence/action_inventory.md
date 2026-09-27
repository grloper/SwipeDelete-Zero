# SwipeDelete Zero — M0 UI Action Inventory & Control Audit (`UX-01`)

> **Scope**: Complete audit of all 7 primary screens in `com.swipedelete.zero` at the M0 baseline (`6e0f5a9d5200914743dcebe8da5e7c4372fbd1da` -> `worker/m0-safety-foundation`).
> **Classification**:
> - `WORKING`: Verified functional locally without data loss.
> - `CONTAINED (M0_SAFETY_LOCK)`: Destructive operation intentionally blocked in domain layer (`PurgeEngine`) because Google Photos metadata proof does not guarantee original-byte restoration (deferred to M1 Drive Vault).
> - `REPAIRED_IN_M0`: Previously exhibited an auth loop, crash on lost finalization, or premature deletion confirmation; fixed and covered by unit tests in M0.
> - `GATED_FOR_M1_M2`: Feature requires M1 independent restore vault or M2 native token UI implementation.

---

## 1. Dashboard Screen (`ui/screens/dashboard/DashboardScreen.kt`)

| Control / Element | Action / Intent | Status | Notes & Verification |
| :--- | :--- | :--- | :--- |
| **Deck Category Cards** (Screenshots, Large Videos, Blur, WhatsApp, All Media) | Navigates to `SwipeEngineScreen(deckId)` | `WORKING` | Queries MediaStore safely; non-destructive read-only entry. |
| **Dual Comparison Mode Button** | Navigates to `DualCardSplitScreen` | `WORKING` | Side-by-side burst/similar photo review; stages intent only. |
| **Staging Queue Pill / Bottom Bar** | Opens `StagingSheet` / `StagingDrawer` | `WORKING` | Displays accurate count and byte sum of staged items. |
| **Cloud Backup Manager Button** | Navigates to `CloudManagerScreen` | `WORKING` | Visible in Play/cloud builds; hidden or offline-badged in F-Droid. |
| **Settings Icon Button** | Navigates to `SettingsScreen` | `WORKING` | Opens app configuration and execution mode preferences. |
| **Storage Reclaimed Counter** | Displays historical byte tally | `REPAIRED_IN_M0` | Combined with `DELETE-01` post-OS reconciliation so unconfirmed/trashed items are not falsely counted as reclaimed permanent bytes. |

---

## 2. Swipe Review Engine (`ui/screens/swipe/SwipeEngineScreen.kt`)

| Control / Element | Action / Intent | Status | Notes & Verification |
| :--- | :--- | :--- | :--- |
| **Swipe Left / Trash Button** | Stages item into `StagedFileDao` (`stageForDeletion`) and enqueues background Photos copy | `WORKING` | Never deletes immediately; only records staging intent. |
| **Swipe Right / Keep Button** | Marks item reviewed/kept (`ReviewedFileDao`) | `WORKING` | Advances deck and removes from future review queues. |
| **Undo Button** | Reverts last swipe (`undoLastDecision`), unstages item, cancels queued upload | `WORKING` | Preserves deck order and cancels pending `STATE_QUEUED` upload via `PhotosArchive.cancelIfQueued`. |
| **Filmstrip Thumbnail Bar** | Jumps to selected media item in active deck | `WORKING` | Synchronized with `SwipeEngineViewModel` index state. |
| **Video Playback / Mute Controls** | Toggles ExoPlayer/Media3 preview playback | `WORKING` | Bounded lifecycle; pauses on background/sheet expand. |
| **Open Staging Sheet Button** | Expands `StagingSheet` for batch review | `WORKING` | Shows per-item cloud verification badges. |

---

## 3. Staging Sheet & Drawer (`ui/screens/staging/StagingSheet.kt`, `StagingDrawer.kt`)

| Control / Element | Action / Intent | Status | Notes & Verification |
| :--- | :--- | :--- | :--- |
| **Restore / Unstage Item Button** | Removes single item from staging queue (`unstageItem`) | `WORKING` | Immediately returns item to safe kept state. |
| **Restore All / Clear Queue Button** | Clears entire staging queue without deleting files | `WORKING` | Safe zero-mutation escape hatch. |
| **Backup Status Banner** | Displays `PhotosArchive` upload/verification progress | `REPAIRED_IN_M0` | Updated copy in `StagingSheet.kt` explicitly discloses M0 safety containment and explains that metadata-only Photos proof does not unlock local deletion. |
| **Retry Failed Uploads Button** | Calls `PhotosArchive.retryAllFailed()` | `WORKING` | Re-queues `STATE_FAILED` entities with reset attempt counter. |
| **Execute Purge / Move to Trash CTA** | Calls `PurgeEngine.preparePurge(staged, mode)` | `CONTAINED (M0_SAFETY_LOCK)` | **Central M0 Containment**: In Play/cloud builds, `PurgeEngine.checkDeletionEligibility` returns `DeletionEligibility.Blocked(M0_SAFETY_LOCK_MESSAGE)` even when all items report `STATE_VERIFIED` in Google Photos. Zero MediaStore/SAF deletions can occur. |
| **Post-Consent Result Handler** | Calls `PurgeEngine.confirmMediaPurged(uris, mode)` | `REPAIRED_IN_M0` | Previously treated any unreadable/missing cursor as deleted. Now uses `MediaStoreRepository.inspectMediaState` (`ABSENT`, `PRESENT`, `TRASHED`, `UNKNOWN`) with `QUERY_ARG_MATCH_TRASHED`. |

---

## 4. Cloud Backup Manager (`ui/screens/cloud/CloudManagerScreen.kt`)

| Control / Element | Action / Intent | Status | Notes & Verification |
| :--- | :--- | :--- | :--- |
| **Google Account Connection Card** | Launches Google Sign-In / OAuth consent for `appendonly` and `readonly.appcreateddata` | `REPAIRED_IN_M0` | Worker now binds to the initial signed-in account (`NET-02`) and refreshes read vs upload tokens independently without cross-scope invalidation. |
| **Active Queue Item Cancel Button** | Calls `PhotosArchive.cancel(contentUri)` | `WORKING` | Cancels WorkManager job and deletes `CloudUploadEntity` row. |
| **Failed Item Retry Button** | Calls `PhotosArchive.retry(contentUri)` | `REPAIRED_IN_M0` | Resumes from server-queried byte offset (`querySession`) or resets session via `UploadEvent.SessionReset` if finalized token was lost (`NET-01`). |
| **Clear Finished History Button** | Calls `PhotosArchive.clearFinished()` | `WORKING` | Removes completed rows from UI queue list while retaining `BackedUpFileDao` receipts. |
| **Open in Google Photos Button** | Launches `PhotosArchive.openInPhotosIntent()` | `WORKING` | Opens Google Photos package or web fallback. |
| **Independent Cloud Restore Browser** | Browse and download byte-identical original backups after reinstall | `GATED_FOR_M1_M2` | Scheduled for M1 (`M1-03` to `M1-05`). Google Photos API does not provide original-byte vault semantics. |

---

## 5. Cloud & Storage Setup Wizard (`ui/screens/setup/CloudSetupScreen.kt`)

| Control / Element | Action / Intent | Status | Notes & Verification |
| :--- | :--- | :--- | :--- |
| **Grant Media Access Button** | Requests `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (or `READ_EXTERNAL_STORAGE` on API <= 32) | `WORKING` | Handles full and Android 14+ visual media selected access. |
| **Connect Google Photos Button** | Requests OAuth scopes for cloud backup | `WORKING` | Disabled/omitted in `fdroid` offline flavor (`BuildConfig.SUPPORTS_PHOTOS_ARCHIVE == false`). |
| **Skip / Continue Offline Button** | Proceeds to Dashboard | `WORKING` | Preserves non-destructive local review capability. |

---

## 6. Dual Card Split Comparison (`ui/screens/dual/DualCardSplitScreen.kt`)

| Control / Element | Action / Intent | Status | Notes & Verification |
| :--- | :--- | :--- | :--- |
| **Keep Top / Stage Bottom CTA** | Keeps card A, stages card B for deletion | `WORKING` | Stages into `StagedFileDao`; subject to central `PurgeEngine` M0 lock. |
| **Keep Bottom / Stage Top CTA** | Keeps card B, stages card A for deletion | `WORKING` | Stages into `StagedFileDao`; subject to central `PurgeEngine` M0 lock. |
| **Keep Both Button** | Marks both items reviewed without staging | `WORKING` | Non-destructive. |
| **Badge / Heuristic Indicator** | Shows resolution, file size, and sharpness delta (`ComparisonBadge`) | `WORKING` | Pure local calculation (`BlurDetector`, `PerceptualHasher`). |

---

## 7. Settings Screen (`ui/screens/settings/SettingsScreen.kt`)

| Control / Element | Action / Intent | Status | Notes & Verification |
| :--- | :--- | :--- | :--- |
| **Execution Mode Selector** (`OS_TRASH_30_DAY` vs `PERMANENT_PURGE`) | Persists preferred cleanup mode in preferences | `CONTAINED (M0_SAFETY_LOCK)` | Preference is saved, but both `OS_TRASH_30_DAY` and `PERMANENT_PURGE` are blocked in `PurgeEngine` under Play/cloud M0 containment (`SAFE-01`). |
| **SAF Directory Grant Picker** | Launches `ACTION_OPEN_DOCUMENT_TREE` | `WORKING` | Persists URI permission for non-MediaStore document cleanup. |
| **Haptics / Sound Toggles** | Controls tactile feedback on swipe commits | `WORKING` | Local preference only. |

---

## 8. Separation of Real Captures vs. Proposed Design Artifacts

1. **Real Baseline Captures (`evidence/screens/`)**:
   - `evidence/screens/baseline_dashboard.png` — Actual Android emulator screenshot captured from CI run `36148366952` (`emulator-smoke-artifacts`), showing the live baseline UI before M2 redesign.
2. **Design Proposals (`evidence/design/`)**:
   - `evidence/design/proposal_review_deck.svg` — Proposed M2 Review Deck screen adhering to `SwipeDelete_Worker_Pack_v1/design/tokens.json` ("Quiet precision", `#101418` dark canvas, `#7DE6C1` accent, 52dp primary control height, >= 48dp hit targets).
   - `evidence/design/proposal_safety_staging.svg` — Proposed M2 Safety & Staging Sheet clearly distinguishing metadata-only Google Photos status from original-byte vault protection, with non-color-only proof iconography.
   - `evidence/design/proposal_cloud_backups.svg` — Proposed M2 Cloud Backups & Recovery screen with explicit account binding, resumable byte progress, and independent restore entry point.
   - *All proposals are explicitly watermarked `PROPOSED DESIGN — NOT IMPLEMENTED UI` and contain 100% synthetic fixture labels.*
