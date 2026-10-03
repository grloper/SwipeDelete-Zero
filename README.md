<div align="center">

# 🗂️ SwipeRise

### Make room for what matters. Review your library on your device.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-00E676.svg?style=for-the-badge)](LICENSE)
[![F-Droid: Offline](https://img.shields.io/badge/F--Droid-Offline-FF3B30.svg?style=for-the-badge)](#-the-air-gap-guarantee)
[![Min SDK 29](https://img.shields.io/badge/Min_SDK-29_(Android_10)-00F0FF.svg?style=for-the-badge)](#)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-FFD700.svg?style=for-the-badge)](#)

Review your photo library one card at a time: swipe left to stage, right to
keep, or up to archive to Photos in the Play build. The F-Droid build works
offline. The Play test build offers optional Google backup, but cleanup in every edition
remains locked until the complete safety path is validated live.

### [⬇️ Test the current Play APK](../../pull/20)

The pull request's **Play release bundle** workflow attaches an
`evidence-archive` artifact with a debug APK after the checks pass. Extract
the APK and install it on an Android device. This is not a signed Play release.

</div>

---

## 🔒 The Air-Gap Guarantee

The **fdroid** build of SwipeRise is architecturally incapable of phoning
home. Its manifest **does not declare `android.permission.INTERNET`** — so the
OS itself blocks every socket. All scanning, perceptual hashing, blur detection
and review happen locally. Cleanup is currently locked in every edition.

- ✅ Zero telemetry, zero analytics, zero ads
- ✅ No cloud, no accounts, no background uploads
- ✅ Open source (GPL v3) — audit every line

> This offline guarantee applies to the F-Droid flavor, not the Play flavor.

### ☁️ Optional: the Play backup build

The Play flavor requests INTERNET for two opt-in Google features. Google OAuth
must be configured for the installed app's package and signing certificate:

- **Google Drive backup** of kept and staged files; a fresh remote download is
  compared to the original SHA-256 hash. Current files carry restore metadata
  for discovery after a clean install.
- **Swipe-up Google Photos archive** uploads selected media and confirms the
  app-created item exists. Photos does not provide the original-byte hash.
  Neither backup path unlocks local deletion in this test build.

Setup guide: [docs/DRIVE_BACKUP_SETUP.md](docs/DRIVE_BACKUP_SETUP.md).
The F-Droid flavor remains offline; there, swipe-up means star and exclude.

## ✨ Features

| Deck | What it surfaces |
|------|------------------|
| ⏳ **Cleanup Sprints** | Month-by-month sprints on a horizontal rail with progress rings ("May 2024 — 45% complete") |
| 🐘 **Heavy Hitters** | Biggest files first — 100 MB+ videos, `.apk`, `.zip`, huge audio |
| 🎬 **Large Videos** | AI bucket for single files ≥1 GB — the fastest storage wins |
| 🔥 **Clutter Hotspots** | Screenshots, WhatsApp Media, Telegram, Camera Bursts, Downloads |
| 👯 **Duplicates & Blurry** | pHash/dHash near-dupes + Laplacian-variance blur, side-by-side |
| 🧾 **Screenshots & Receipts** | AI bucket from path/filename heuristics (screenshots, receipts, invoices, scans) |

Every deck is capped at **50 cards** to keep sessions snackable, with resumable
progress ("24/50 swiped in July 2024").

### 🎥 Video Review Engine

Videos auto-loop **muted** on the top card through a single reused ExoPlayer
(one hardware decoder for the whole session — no per-card re-allocation lag),
layered over the Coil thumbnail so there is never a black flash. A
**10-thumbnail filmstrip scrubber** at the card's foot drags through the
timeline with closest-sync-frame seeks; the metadata pill reads like a spec
sheet (`2.4 GB • 4K 60fps • HEVC`), with a coral flame accent on >1 GB /
>25 Mbps / 4K storage hogs.

### 🌈 Dynamic backdrop & motion

The screen behind the card stack is a blurred gradient sampled from the active
card's dominant palette (androidx.palette over a 64px sidecar decode, blended
toward pitch black). Cards commit positionally *or* by velocity — a fast flick
commits early — with progressive haptics: a tick at 50% of the threshold, a
pulse when it arms, and distinct reject/confirm/double-tick signatures.

## 🛟 Safety pipeline

1. **Active Deck** — flick cards; a 5-second Undo toast recovers any mistake.
2. **Safety Staging** — a bottom-sheet queue you can review, restore or clear.
   Local originals remain in place in the Play test build.
3. **Backup and restore** — Drive checks a fresh download against the original
   SHA-256 hash and supports restoring current-manifest files. Photos confirms
   an app-created item, without original-byte verification. Cleanup in every edition stays
   locked until live original-byte recovery is qualified.

## 🎨 Design System

Material 3 Expressive, 120 Hz **OLED Pitch-Black**:

| Token | Hex |
|-------|-----|
| Background | `#000000` |
| Cards / Surfaces | `#0D0F12` (1px `#1AFFFFFF` border) |
| Keep (primary) | `#00E676` Electric Emerald |
| Trash (danger) | `#FF3B30` Hyper Coral |
| Data readouts | `#00F0FF` Crisp Cyan |
| Star / favourite | `#FFD700` Star Gold |
| Secondary text | `#8E95A2` Muted Gray |

Thumb-zone-first ergonomics: every core control lives in the bottom 40% of the
screen.

## 🏛️ Architecture

Clean Architecture, single-activity Jetpack Compose, Hilt DI.

```
app/
├── ui/
│   ├── components/   # SwipeableCard (velocity physics), PaletteBackdrop, CloudChip, MetadataPill
│   ├── video/        # TopCardPlayer (single ExoPlayer), FilmstripScrubber
│   ├── haptics/      # SdzHaptics (progressive, API-tiered)
│   ├── screens/      # dashboard (sprints + AI buckets), swipe, dual, staging (+sheet), settings
│   └── theme/        # Color.kt, Theme.kt, Type.kt (OLED tokens)
├── domain/
│   ├── algorithm/    # PerceptualHasher (pHash/dHash), BlurDetector (Laplacian variance)
│   ├── backup/       # CloudBackup + PhotosArchive seams, UploadReducer (pure)
│   ├── model/        # MediaItem, Deck, SwipeAction, SwipeCommitDecider, PlaybackReducer, Filmstrip
│   └── scanner/      # DeckBuilder, MediaAnalysisWorker, VideoMetadataExtractor, AnalysisScheduler
└── data/
    ├── local/        # Room v3: StagedFile, DeckSession, Exclusion, MediaAnalysis, CloudUpload
    └── repository/   # MediaStore + SAF bridge, PurgeEngine, MediaPreloader, StatsStore
```

### Engineered safeguards

- **Scoped Storage:** media via MediaStore; the Play edition does not request
  all-files access.
- **Restricted app dirs** (`Android/media/com.whatsapp/...`): `SecurityException`
  degrades to an empty result, never a crash.
- **Battery:** hashing/blur run in WorkManager with `setRequiresCharging(true)` +
  `setRequiresDeviceIdle(true)`; bitmaps downsampled to 32×32 grayscale first.
- **OOM:** previews decode capped to card bounds; video playback goes through
  **one shared, muted ExoPlayer** (top card only) — never a decoder per card;
  filmstrip thumbnails are 96×54 Coil decodes riding the shared caches.
- **Data drift:** strict existence re-check before purge; cloud/`IS_PENDING`
  tombstones excluded; partial-success transactional queue updates.
- **Prompt batching:** all trashable media grouped into one OS request.

## 🏗️ Build

```bash
# F-Droid flavor (no all-files permission, SAF fallback)
./gradlew :app:assembleFdroidDebug

# Play flavor (scoped storage and optional Google backup over INTERNET)
./gradlew :app:assemblePlayDebug

# Play release App Bundle (signed only with a private upload key; see docs/PLAY_RELEASE.md)
./gradlew :app:bundlePlayRelease

# Unit tests (pure-JVM algorithm coverage)
./gradlew :app:testFdroidDebugUnitTest :app:testPlayDebugUnitTest
```

Requirements: JDK 17, Android SDK 36.

## 📦 Distribution

- **F-Droid:** `fdroid` flavor — reproducible, no proprietary blobs, no
  `MANAGE_EXTERNAL_STORAGE`.
- **Play:** `play` flavor — scoped media access only. See
  [release preparation](docs/PLAY_RELEASE.md) and the
  [privacy policy](docs/PRIVACY_POLICY.md). Non-media cleanup needs explicit
  document access and should be verified before being promoted in the listing.

## 📄 License

[GNU General Public License v3.0](LICENSE) — free as in freedom.

<div align="center">
<sub>SwipeRise · review with care</sub>
</div>

## Validation boundaries

PR20 consolidates the independently reviewed backup and session work. Final local checks cover Play, F-Droid and Cloud with 501 unit tests, lint and development packages; exact-head emulator evidence remains mandatory before merge. No live original-byte clean-install cloud restore or physical pose/device qualification is claimed. iCloud is unimplemented. The earlier PR16/36310543845 evidence belongs only to its archived ca1f457 APK and cannot certify this snapshot.

