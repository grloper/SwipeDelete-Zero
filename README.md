<div align="center">

# 🗂️ SwipeDelete Zero

### Tinder for your Android Storage. 100% Offline. Zero Net-Permissions.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-00E676.svg?style=for-the-badge)](LICENSE)
[![Permissions: Zero Network](https://img.shields.io/badge/Network_Permissions-ZERO-FF3B30.svg?style=for-the-badge)](#-the-air-gap-guarantee)
[![Min SDK 29](https://img.shields.io/badge/Min_SDK-29_(Android_10)-00F0FF.svg?style=for-the-badge)](#)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-FFD700.svg?style=for-the-badge)](#)

**Reclaim gigabytes in minutes.** Blitz through your photo library one card at a
time — swipe left to trash, right to keep, up to star. Nothing ever leaves your
device.

### [⬇️ Download the latest APK](../../releases/latest)

Every push builds a fresh, directly-installable APK and attaches it to the
[Releases page](../../releases). Grab the `.apk`, open it on your Android device,
allow "unknown sources", and you're in.

</div>

---

## 🔒 The Air-Gap Guarantee

The **fdroid** build of SwipeDelete Zero is architecturally incapable of phoning
home. Its manifest **does not declare `android.permission.INTERNET`** — so the
OS itself blocks every socket. All scanning, perceptual hashing, blur detection
and deletion happen locally.

- ✅ Zero telemetry, zero analytics, zero ads
- ✅ No cloud, no accounts, no background uploads
- ✅ Open source (GPL v3) — audit every line

> If you ever see the fdroid build try to open a network connection, it's a bug
> worth a CVE. It cannot, by construction.

### ☁️ Optional: the `cloud` build

Each release also ships a clearly-labelled **cloud** APK — the *only* build with
the INTERNET permission — adding two opt-in features on your own OAuth
credentials:

- **Google Drive backup** of the files you keep or star (each uploaded exactly
  once, incremental forever after), via the non-sensitive `drive.file` scope.
- **Swipe-up "Archive to Google Photos"**: the up-swipe uploads the card to
  Google Photos with resumable, process-death-surviving chunked uploads
  (`photoslibrary.appendonly`, upload-only scope). The local copy is **only**
  offered for deletion after a verification handshake returns a valid
  `mediaItemId` — and even then it goes through the normal Safety Staging queue
  and OS confirmation dialog. Every card shows a live
  `[ Cloud Backed Up ]` / `[ Local Only ]` chip from the verified ledger.

Setup guide: [docs/DRIVE_BACKUP_SETUP.md](docs/DRIVE_BACKUP_SETUP.md).
If you want the hard air-gap, simply install the fdroid APK (there, swipe-up
keeps its offline meaning: star & exclude).

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

## 🛟 3-Tier Safety Pipeline

```
[ Active Deck Engine ] ──swipe left──► [ Staging Review Drawer ] ──batch confirm──► [ OS Trash / SAF Purge ]
        keep · trash · star                review · restore · clear                 30-Day Trash or Permanent
```

1. **Active Deck** — flick cards; a 5-second Undo toast recovers any mistake.
2. **Safety Staging** — a bottom-sheet queue you can review, restore or clear.
   Live readout: *"38 files • 2.4 GB ready to purge"*, plus a lifetime
   *"14.2 GB Reclaimed"* counter fed only by verified deletions.
3. **Disk Execution** — choose **30-Day OS Trash**
   (`MediaStore.createTrashRequest`) or **Permanent Purge**
   (`createDeleteRequest` / SAF). Batched into a single OS confirmation.

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

- **Scoped Storage:** media via MediaStore; non-media (`.apk`/`.zip`) via SAF or
  (Play flavor only) `MANAGE_EXTERNAL_STORAGE`, behind one abstraction.
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

# Play flavor (optional MANAGE_EXTERNAL_STORAGE for non-media)
./gradlew :app:assemblePlayDebug

# Unit tests (pure-JVM algorithm coverage)
./gradlew :app:testFdroidDebugUnitTest
```

Requirements: JDK 17, Android SDK 35.

## 📦 Distribution

- **F-Droid:** `fdroid` flavor — reproducible, no proprietary blobs, no
  `MANAGE_EXTERNAL_STORAGE`.
- **Play:** `play` flavor — may request all-files access for one-tap non-media
  purge, subject to Play policy declaration.

## 📄 License

[GNU General Public License v3.0](LICENSE) — free as in freedom.

<div align="center">
<sub>Built with ❤️ and zero network sockets.</sub>
</div>
