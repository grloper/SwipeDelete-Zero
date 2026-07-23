# SwipeDelete Zero — Android UX Redesign Blueprint

**Role**: Principal Mobile Product Designer / Lead Android UX Architect
**Scope**: Complete product audit, UX architecture redesign, and execution-ready UI/UX
specification for the existing SwipeDelete Zero codebase (Jetpack Compose, Material 3,
minSdk 29, zero network permissions).

Every finding in this document is grounded in the current implementation
(`app/src/main/java/com/swipedelete/zero/…`) — file references are included so each
recommendation maps to a concrete change.

---

## Phase 1 — UX & Strategic Product Audit

### 1.1 Mental Model & Job To Be Done

**Core JTBD**: *"When my phone warns me storage is almost full (usually at the worst
possible moment — mid-photo, mid-update), help me reclaim gigabytes in under five
minutes without the fear of deleting something I'll regret, and without uploading my
photos anywhere."*

Three psychological forces shape the design:

1. **Loss aversion dominates**. Deleting photos is emotionally expensive; every
   competitor that makes deletion feel *final* loses users at the moment of commitment.
   The existing 3-tier pipeline (Swipe → Staging Drawer → OS Trash) is the correct
   architecture: it converts one terrifying irreversible decision into fifty trivial
   reversible ones. The redesign doubles down on this: **no swipe is ever destructive**,
   and the UI must *say so* at every step.
2. **Momentum is the engagement engine**. The Tinder-swipe mental model works because
   each decision takes < 1 second and produces immediate kinesthetic feedback. The
   50-card deck cap (`Deck.kt`, `MAX_DECK_SIZE`) is the right "snackable session" bound —
   the redesign adds a visible progress runway and a session-end payoff moment so users
   finish decks instead of abandoning them.
3. **Privacy is the trust wedge**. "Architecturally incapable of phoning home" (no
   `INTERNET` permission) is the differentiator no VC-funded cleaner can copy. It must
   be *visible in the chrome*, not buried in a README — hence the persistent
   "0 net-permissions" status chip in the dashboard header.

### 1.2 Target User Context

| Context dimension | Reality | Design consequence |
|---|---|---|
| **Grip** | One-handed, thumb-only, often standing/commuting | All actionable controls in the bottom 40% of the screen; card gestures require no reach above the vertical midpoint |
| **Session shape** | 2–5 min bursts, frequently interrupted | Per-deck resumable cursor (already in `SwipeSessionEntity`); cold-start-to-first-swipe target < 2 s |
| **Lighting** | Evening/bedside use dominant for photo triage | OLED pitch-black `#000000` canvas (already core to brand); no light theme — but *tone-mapped* image surrounds to avoid halation around bright photos |
| **Latency tolerance** | Zero. A swipe that stutters breaks the flow state | Preload next 3 card bitmaps; decode at display resolution; all hashing/blur analysis off the UI thread via WorkManager (already in `MediaAnalysisWorker`) |
| **Trust posture** | Skeptical of "cleaner" apps (ad-ridden category) | No interstitials ever; permission requests only after a rationale screen; celebrate what was freed, never nag |

### 1.3 UX Friction Breakdown — Top 3 Competitor Failures & Our Systematic Answers

**Friction 1 — "The Grid of Doom."** Gallery cleaners (Files by Google, Gallery
Doctor clones) present a checkbox grid of 400 thumbnails. Selection cost is high,
decisions are batched, and one mis-tap in a grid is invisible until it's too late.
→ **Our answer**: one full-bleed card, one decision, one gesture. The deck engine
(`SwipeEngineScreen.kt`) shows a single ~0.72 aspect card with the next card peeking
behind it. Decision throughput is *higher* than a grid (users swipe ~40 cards/min)
while error rate collapses because every action has a distinct gesture + color + haptic
signature and a 5-second undo.

**Friction 2 — "Where did my photo go?"** Competitors delete immediately or bury
files in a proprietary recycle bin. Users don't purge because they can't predict the
consequence. → **Our answer**: the Staging Drawer is a *named, visible, inspectable*
holding area with a live readout ("38 files • 2.4 GB ready to purge") and per-item
restore. Execution offers an explicit fork: **30-Day OS Trash**
(`MediaStore.createTrashRequest`) vs **Permanent Purge** — the safe option is always
the visually primary one.

**Friction 3 — "Cold-start paralysis."** Opening a cleaner to 40,000 unsorted photos
gives no obvious first move. → **Our answer**: the Deck Builder (`DeckBuilder.kt`)
pre-curates four high-yield entry points (Time Machine, Heavy Hitters, Clutter
Hotspots, Duplicates & Blurry), each labeled with its reclaimable size in cyan. The
dashboard's job is to answer one question in < 3 seconds: *"Where do I get the most
gigabytes back for the least effort?"* The redesign sorts decks by
`reclaimableBytes / totalCount` (yield per swipe) and badges the top deck "Best value".

---

## Phase 2 — Modern Android Design System Strategy

### 2.1 Material Design 3 Theme Specs

#### Color system

The app is intentionally **dark-only** (brand + OLED battery). The current palette
(`ui/theme/Color.kt`) is kept as the brand layer, but the redesign introduces proper
**M3 surface-container tiers** — today `surface` and `surfaceVariant` both map to
`#0D0F12`, which flattens all spatial hierarchy.

**Seed & brand tokens** (unchanged, canonical):

| Token | Hex | Role |
|---|---|---|
| `PitchBlack` | `#000000` | Background — pixels physically off on OLED |
| `ElectricEmerald` | `#00E676` | `primary` — Keep / positive / progress |
| `HyperCoral` | `#FF3B30` | `error` — Trash / destructive |
| `CrispCyan` | `#00F0FF` | `secondary` — data readouts, live counters |
| `StarGold` | `#FFD700` | `tertiary` — Star / favourite / exclusion |
| `PureWhite` | `#F4F6FA` | `onSurface` primary text |
| `MutedGray` | `#8E95A2` | `onSurfaceVariant` secondary text |

**New surface-container ladder** (add to `SdzColors` and wire into
`darkColorScheme(...)` in `Theme.kt`):

| M3 slot | Hex | Usage |
|---|---|---|
| `surfaceContainerLowest` | `#000000` | Screen canvas |
| `surfaceContainerLow` | `#080A0C` | Grouped list backgrounds |
| `surfaceContainer` | `#0D0F12` | Cards, deck tiles (current `Obsidian`) |
| `surfaceContainerHigh` | `#14171C` | Bottom sheets, dialogs, undo toast |
| `surfaceContainerHighest` | `#1A1E24` | Pressed/selected states, chips |
| `outlineVariant` | `#1AFFFFFF` | 1 dp hairline strokes (current `Hairline`) |

**Contrast audit**: `PureWhite #F4F6FA` on `#0D0F12` ≈ 17.6:1 (AAA).
`MutedGray #8E95A2` on `#0D0F12` ≈ 6.1:1 (AAA for large text, AA for body — never use
below `labelMedium` size for critical info). `CrispCyan` on black ≈ 14.9:1 (AAA).
`HyperCoral #FF3B30` on black ≈ 5.0:1 — passes AA for its ≥ 18 sp bold usage, but
**never place coral text on Obsidian below 14 sp bold**; use it as container fill with
white foreground instead (as the Review Drawer button already does).

**On-accent rule**: all accent-filled containers use `PitchBlack` foreground
(`onPrimary = #000000`), giving ≥ 15:1 on emerald/cyan/gold.

**Dark-mode-only mapping note**: because there is no light theme, "dark mode colors"
*are* the theme. The one adaptation left open: an **AMOLED-dim variant** (Settings
toggle) that drops all accents ~20% in luminance for bedside triage
(`ElectricEmerald → #00C766`, `CrispCyan → #00D2E0`, `StarGold → #E6C200`).

#### Typography & scale

The current scale (`ui/theme/Type.kt`) is sound; two corrections:

| Style | Spec | Usage rule |
|---|---|---|
| `displayLarge` | 40/44, Black, −0.5 tracking | **Numbers only** — the "2.4 GB" hero readout. Never for labels. |
| `displaySmall` *(add)* | 30/34, Black, −0.25 | Deck-complete headline, purge-success total |
| `headlineMedium` | 26/30, Bold | Screen titles ("SwipeDelete Zero") |
| `titleLarge` | 20/24, SemiBold | Section headers, deck titles in engine |
| `titleMedium` | 16/20, SemiBold | Card/tile titles |
| `bodyLarge` / `bodyMedium` | 16/22, 14/18 Regular | Explanatory copy, rationale screens |
| `labelLarge` | 14/16 Bold +0.5 | Buttons, chips, data pills |
| `labelMedium` | 12/14 Medium +0.4 | Metadata, timestamps — minimum size in app |

Tabular figures (`FontFeatureSettings "tnum"`) on all live counters (staging byte
count, deck progress) so digits don't jitter as values tick.

#### Elevation & surface depth

Shadows are invisible on `#000000`. Depth is communicated by a three-signal system:

1. **Tonal step** — each layer up uses the next `surfaceContainer` tier.
2. **Hairline stroke** — 1 dp `#1AFFFFFF` on every raised container (already the
   house style).
3. **Scale + parallax** — the card behind the active swipe card renders at 0.94 scale,
   12 dp inset (already in `CardStack`); redesign animates it to 1.0 scale with a
   spring as the top card leaves, which *is* the elevation change.

Corner radius ladder: 28 dp (swipe cards) / 24 dp (hero containers) / 22 dp (deck
tiles) / 16 dp (buttons, toasts) / 12 dp (chips, pills) / full (circular actions).

### 2.2 Ergonomics & Touch Targets

**Audit finding**: several current controls violate the 48 dp minimum —
`Settings` icon 28 dp (`DashboardScreen.kt:107`), back arrows 26 dp
(`SwipeEngineScreen.kt:74`, `StagingDrawerScreen.kt:96`), "Clear" and "UNDO" are bare
clickable `Text`. **Rule**: every interactive element gets a ≥ 48×48 dp touch target
via `Modifier.minimumInteractiveComponentSize()` or `IconButton`, regardless of visual
glyph size.

**Thumb-zone map** (portrait, one-handed right grip):

- **Bottom 0–20% (prime)**: Action bar (Trash 72 dp / Star 60 dp / Keep 72 dp circles —
  current sizes are correct), Review Drawer launcher, purge CTA buttons.
- **20–40% (comfortable)**: Undo toast (currently at 120 dp bottom padding — correct),
  bottom-sheet content, deck carousel on dashboard (redesign moves it from upper-middle
  to lower-middle).
- **40–75% (neutral)**: Swipe card body — gesture-driven, no precision taps required.
- **Top 25% (stretch)**: Read-only chrome only — titles, progress counters, status
  chips. The back affordance is redundant with the system back gesture; keep it for
  discoverability but never make it the *only* path.

**Bottom sheets over full screens** where the task is transient: the Staging Drawer
becomes a **modal bottom sheet** (expandable to full) so it physically slides up from
the thumb, matching its "drawer" name and mental model.

**Haptic feedback design** (via `LocalHapticFeedback` + `HapticFeedbackConstants`):

| Trigger | Haptic | Rationale |
|---|---|---|
| Card crosses swipe commit threshold (±40% width) | `GestureThresholdActivate` (tick) | Confirms "release now = action" before release |
| Card released → trash | `Confirm` double-pulse | Weightier feel for the destructive-ish path |
| Card released → keep/star | `Confirm` single | Light positive |
| Undo tapped | `LongPress` | "Grabbed it back" |
| Staging purge confirm pressed | `Confirm`, then `Confirm` again on OS dialog return | Bracket the irreversible moment |
| Deck complete (last card) | Success pattern: tick–tick–heavy | The payoff moment |
| Pull-to-refresh deck list | `GestureStart` on threshold | Standard M3 behavior |

Only the Review Drawer button currently has haptics (`DashboardScreen.kt:142`); the
core swipe loop — the highest-frequency interaction in the app — has none. This is the
single highest-leverage polish fix in the codebase.

---

## Phase 3 — Screen-by-Screen UI Layout Breakdown

### 3.0 Journey map

```
First launch ─► Permission Rationale ─► Dashboard (Hub)
                                          │
                     ┌────────────────────┼──────────────────────┐
                     ▼                    ▼                      ▼
              Swipe Engine         Dual-Card Compare        Settings
             (single decks)       (duplicates decks)
                     │                    │
                     └────────┬───────────┘
                              ▼
                     Staging Drawer (bottom sheet)
                              ▼
                  OS confirm ─► Purge Success moment ─► Dashboard
```

### 3.1 Permission Rationale (new screen — currently missing)

**Audit finding**: `DashboardScreen.kt:62-70` fires the permission dialog in a
`LaunchedEffect(Unit)` with zero context — the highest-abandonment anti-pattern on
Android. Users who deny here are unrecoverable without Settings deep-links.

- **Header/nav**: none — full-bleed, single-purpose. No back (it's the entry gate).
- **Composition**: centered column. Shield glyph in an emerald-stroked 96 dp circle →
  `headlineMedium` "Your photos never leave this phone" → three bullet rows
  (icon + `bodyMedium`): "No internet permission — the OS blocks all traffic",
  "Deletes go to the 30-day system trash first", "Open source, GPL-3.0".
  Bottom-anchored 56 dp full-width emerald button **"Scan my library"**; below it a
  `labelMedium` gray link "What exactly gets scanned?" opening an info sheet.
- **Micro-interactions**: bullets stagger-fade in (60 ms apart, spring). Button press
  scales 0.97 with `Confirm` haptic, then launches the system dialog.
- **Edge states**: on denial, swap body to a calm recovery card ("SwipeDelete can't
  see any media yet") with a "Open App Settings" button — never a dead end. On
  partial grant (Android 14 "Select photos"), show an amber chip "Limited access —
  N items visible" with a "Manage" action.

### 3.2 Dashboard / Main Hub (`DashboardScreen.kt`)

- **Header & nav**: two-line identity block (app name `headlineMedium`; under it the
  trust chip — a 12 dp-radius pill, Obsidian fill, emerald dot + "OFFLINE · 0
  NET-PERMISSIONS" `labelMedium`). Right: 48 dp `IconButton` settings. Header
  collapses (fade + 8 dp rise) as the deck area scrolls. Status-bar area transparent,
  content padded by insets (see Phase 5).
- **Composition** (top → bottom):
  1. **Storage hero** (24 dp-radius Obsidian container): 96 dp `StorageRing` (cyan,
     animated sweep on entry, 600 ms `FastOutSlowIn`) + right column: label, the
     `displayLarge` emerald byte readout, and free/total in gray. The readout animates
     count-up on change.
  2. **"Recommended Decks"** `titleLarge` + deck carousel. **Redesign**: replace the
     `LazyRow` of 180 dp portrait tiles with a **2×2 grid of 168 dp-tall tiles**
     (all four kinds visible without scrolling; horizontal carousels hide inventory).
     Each tile: kind glyph top-left, progress ring top-right (44 dp), title
     `titleMedium`, subtitle gray, reclaimable size in cyan `labelLarge` bottom-left.
     The highest-yield deck gets a 1 dp emerald border + "BEST VALUE" 10 sp chip.
  3. **Momentum strip** (new): full-width low-container card — "This week: 312 swiped
     · 1.8 GB freed" with a 7-day micro bar chart in emerald.
- **Bottom anchor**: Review Drawer launcher stays bottom-pinned (correct thumb-zone
  call). **Redesign the resting state**: coral is an alarm color and it currently
  screams even when the drawer is empty. Empty → Obsidian container, hairline border,
  gray text "Staging drawer · empty". ≥ 1 item → coral fill, white text, count + bytes,
  subtle 1.02 scale pulse *once* when count increments (not looping).
- **Micro-interactions**: deck tile press: 0.96 scale spring
  (`Spring.DampingRatioMediumBouncy`); tile → engine is a **shared-element
  transition** (Compose 1.7 `SharedTransitionLayout`): the tile's ring morphs into the
  engine header progress ring, tile bounds expand into the first card's frame
  (~350 ms, `FastOutSlowIn`).
- **Edge states**:
  - **Loading**: replace the current "Scanning your library…" text with **4 skeleton
    tiles** (Obsidian base, 1.2 s shimmer sweep `#14171C`) + an indeterminate 2 dp
    cyan progress hairline under the storage hero with live count "Indexed 3,412
    items…" (the scanner already streams counts).
  - **Empty (library clean)**: emerald check-ring, `displaySmall` "Nothing to clean 🎉",
    body "Your library is tidy. New decks appear as clutter accumulates." + gray
    "Rescan" text button.
  - **Permission lost** (revoked mid-life): rationale card inline where decks would be.

### 3.3 Swipe Engine (`SwipeEngineScreen.kt`) — the deep-execution screen

- **Header & nav**: 48 dp back `IconButton` (auto-mirrored `ArrowBack`), deck title
  `titleLarge` + live counter "24/50" in cyan with **tabular figures**. Right edge:
  a 4 dp-tall rounded **progress runway** spanning the header bottom, emerald fill,
  spring-animated per swipe. Back = predictive-back enabled (Phase 5); in-progress
  state is already persisted, so back is always safe — never gate with an "Are you
  sure?".
- **Composition**: card area takes the flexible middle (`weight(1f)`, aspect 0.72,
  28 dp radius). Card content: full-bleed media (`MediaPreview`), bottom-anchored
  36 dp scrim gradient carrying metadata pills (size in cyan, date, resolution;
  `MetadataPill.kt`). Video cards: center 56 dp play glyph, duration pill top-right,
  tap-to-preview with sound *off* by default.
  Behind-card at 0.94 scale/12 dp inset (existing) — animate its promotion to full
  scale as the top card exits.
  Bottom 40%: the three-circle action bar (72/60/72 dp — keep) with `labelMedium`
  captions beneath each ("Trash · Star · Keep") for first-session learnability;
  captions auto-hide after 20 lifetime swipes (DataStore flag).
- **Micro-interactions & motion**:
  - Drag: card follows finger with rotation `= offsetX / 60f` degrees (max ±14°),
    spring return (`stiffness = 400f`) on sub-threshold release.
  - **Swipe stamps** (`SwipeStamps.kt`): "TRASH" coral / "KEEP" emerald / "STAR" gold
    stamps fade+rotate in proportional to drag distance, hitting full opacity at the
    commit threshold, synced with the threshold haptic tick.
  - Commit: card flies out along its velocity vector (min fling velocity 1200 dp/s),
    250 ms; action circle corresponding to the gesture flashes its accent fill for
    150 ms so button-tappers and swipers see one unified feedback language.
  - Undo toast (existing 5 s, bottom 120 dp): add a **circular countdown ring** around
    the UNDO label draining over 5 s — visible urgency without reading.
  - Every 10th swipe: the runway emits a 6-particle emerald spark burst (300 ms) —
    cheap, cheerful, non-blocking.
- **Edge states**:
  - **Loading**: full card-shaped skeleton with shimmer + gray "Preparing deck…".
  - **Deck complete**: upgrade the existing 🎉 view into the **payoff moment** —
    `displaySmall` "Deck complete", stat rows (trashed n · kept n · starred n, colored
    dots), staged-bytes readout in `displayLarge` emerald, primary emerald button
    **"Review N files"** → Staging sheet, secondary text button "Back to decks".
    One-shot spring scale-in (0.8 → 1.0, `DampingRatioMediumBouncy`).
  - **Media missing** (deleted externally mid-session): card auto-skips with a brief
    gray "File no longer exists" ghost card, no crash, cursor advances.

### 3.4 Dual-Card Compare (`DualCardSplitScreen.kt`) — duplicates only

- **Header & nav**: same pattern as engine; subtitle "Pair 6 of 18". A gold "NEAR
  DUPLICATE · 94% match" chip centered under the header (distance from
  `PerceptualHasher` mapped to a percentage).
- **Composition**: vertical A/B split, two 16:10 cards separated by a 12 dp gutter
  with a small "vs" divider dot. The algorithm's suggested keeper
  (`ComparisonPair.primary`) carries a 1.5 dp emerald border + "SUGGESTED KEEP" chip;
  diff badges (`DiffBadges.kt`) annotate each card: `+2.1 MP` `sharper` `smaller` in
  12 dp pills. Bottom action row: per-card "Keep this" buttons + a center "Keep both"
  text button; "Trash both" tucked behind overflow (dangerous default avoided).
- **Micro-interactions**: tapping a card zooms it to full-screen lightbox (shared
  element, pinch-zoom enabled) for pixel-peeping; long-press either card overlays a
  50/50 **swipe-wipe comparator** slider. Choosing a keeper: the loser card desaturates
  + shrinks toward the staging drawer icon (200 ms) — literal spatial storytelling.
- **Edge states**: pair where one file vanished → auto-resolve, toast "1 file was
  already gone — kept the other". Skeleton = two shimmering split frames.

### 3.5 Staging Drawer (`StagingDrawerScreen.kt`) → modal bottom sheet

- **Header & nav**: drag-handle pill, `titleLarge` "Staging Drawer", live cyan
  readout "38 files • 2.4 GB ready to purge" (tabular figures). "Clear all" becomes a
  48 dp overflow-menu item with confirm dialog (currently a bare tap-to-clear text —
  audit finding: one accidental tap silently empties the queue).
- **Composition**: `LazyColumn` of 72 dp rows — 56 dp thumbnail (12 dp radius), name
  (`titleMedium`, ellipsized middle), size + source deck (`labelMedium` gray), and a
  48 dp restore `IconButton` (gold `Restore` glyph). Section-grouped by source deck
  with sticky low-container headers. **Bottom execution dock** (high-container,
  top-hairline): a segmented control — **"30-Day Trash"** (emerald, default) vs
  **"Permanent"** (coral, requires a second tap to arm) — above a full-width 56 dp
  CTA "Free up 2.4 GB" that carries the live total.
- **Micro-interactions**: row swipe-right = restore (gold background reveal + haptic);
  restore animates the row out and the header total counts down. CTA press → OS
  confirmation → on success, the sheet collapses into the **purge success moment**:
  full-screen black, `displayLarge` emerald count-up "2.4 GB freed", ring burst
  animation, auto-dismiss 2.5 s or tap. (Currently success is a plain `Toast` —
  `StagingDrawerScreen.kt:63-68` — the single biggest missed delight in the app.)
- **Edge states**: empty → gray inbox glyph, "Nothing staged yet", body "Swipe left
  in any deck to stage files here." SAF-needed (non-media files) → inline amber card
  listing affected folders with per-folder "Grant access" buttons (replaces the
  current toast, which is unactionable). Partial purge failure → rows that failed
  remain, marked with a coral dot + "retry" affordance.

### 3.6 Settings (`SettingsScreen.kt`)

- **Header**: standard 48 dp back + `titleLarge`. Sections in low-container groups
  with hairline separators: **Safety** (default execution mode, undo window length),
  **Exclusions** (starred/protected folders — surfaced from `ExclusionRepository`),
  **Scanning** (rescan now, analysis on charge-only toggle), **About** (version,
  GPL-3.0 license, "Verify our zero-permission claim" → opens the OS app-info
  permissions page — a trust flex no competitor can make).
- **Edge states**: none critical; every toggle persists instantly, no save button.

---

## Phase 4 — Feature Matrix & Innovation Layer

### Tier 1 — Core Utilities (must-haves; mostly shipped, gaps noted)

| Feature | Status | Gap to close |
|---|---|---|
| 4-deck curation engine (Time/Heavy/Hotspots/Dupes+Blurry) | ✅ `DeckBuilder.kt` | Sort dashboard by yield-per-swipe |
| Single-card swipe engine w/ undo | ✅ | Haptics on threshold/commit; stamp-drag sync |
| Dual-card duplicate compare | ✅ | Lightbox zoom; wipe comparator |
| Staging drawer + 3-tier safety pipeline | ✅ | Bottom-sheet form; confirm on "Clear all" |
| 30-day trash vs permanent purge fork | ✅ `PurgeEngine.kt` | Two-tap arming on Permanent |
| Resumable per-deck sessions | ✅ Room entities | Surface "Resume" state on deck tiles |
| Permission rationale flow | ❌ missing | New gate screen (§3.1) |
| ≥ 48 dp touch targets everywhere | ❌ violations | `IconButton`/min-size sweep |

### Tier 2 — UX Differentiators (the "feels faster than everything else" layer)

1. **Purge Success Moment** — count-up "GB freed" full-screen payoff (§3.5). This is
   the screenshot users share; it's also the emotional reward loop that drives return
   sessions.
2. **Shared-element deck-open transition** — tile → card morph makes the app feel
   spatially continuous and premium (§3.2).
3. **Threshold haptics + stamp choreography** — the swipe *feels* mechanical-precise;
   this is the difference between "web-view cleaner" and "instrument".
4. **Next-3 bitmap preloading** — decode upcoming cards at display size on a
   background dispatcher; zero blank frames between swipes. Flow-state protection.
5. **Momentum strip + weekly freed counter** — lightweight, local-only streak
   mechanics; no gamification cringe, just evidence of progress.
6. **Best-value deck badging** — answers "where do I start" in one glance.
7. **Trust chrome** — the offline chip + Settings "verify our claim" deep-link turn
   the architecture into visible product surface.

### Tier 3 — Smart/AI Automation Layer (all on-device, preserving the air gap)

1. **Smart Keeper Suggestion (shipped, extend)** — `ComparisonPair` already picks a
   suggested keeper from sharpness/resolution/size. Extend with a transparent score
   breakdown chip ("sharper +2, larger res +1") — explainable AI, no black box.
2. **Auto-Stage Confidence Queue** — for *exact* duplicates (identical pHash + same
   dimensions + size within 1%), pre-stage the losers into a separate "Auto-picked —
   review anytime" section of the drawer. User always reviews before purge; the swipe
   count for boring exact-dupes drops to zero.
3. **Burst Collapse** — detect camera bursts (≤ 2 s apart, same folder, near-identical
   hash); present as one card with a filmstrip; one swipe handles n files, best frame
   auto-suggested by Laplacian sharpness (`BlurDetector` already computes this).
4. **Predictive Deck Scheduling** — WorkManager learns when analysis is cheapest
   (charging + idle, already constrained in `AnalysisScheduler`) and pre-builds
   tomorrow's decks; dashboard always opens warm.
5. **Adaptive Deck Ordering** — reorder dashboard tiles by the user's historical
   trash-rate per deck kind (stored locally in Room): if a user never trashes from
   Time Machine but always clears Screenshots, Hotspots float to slot 1.
6. **Storage Pressure Trigger** — listen to `ACTION_DEVICE_STORAGE_LOW` /
   `StorageManager` thresholds and post a local notification: "Storage at 92% — a
   6-minute session can free ~3.1 GB." Deep-links straight into the best-value deck.

---

## Phase 5 — Native Android Platform Integration

### 5.1 Edge-to-edge & predictive back

**Audit finding**: `Theme.kt:48-49` sets `window.statusBarColor` /
`navigationBarColor` (deprecated API 35+) and the app never draws behind system bars.

- Call `enableEdgeToEdge()` in `MainActivity`; remove the deprecated window-color
  calls. With a black theme, bars become transparent over the canvas — the app
  gains ~10% vertical canvas for free and looks native on Android 15+ (which forces
  edge-to-edge anyway).
- Inset handling: `Scaffold` + `WindowInsets.safeDrawing` padding on top chrome;
  bottom action bars/sheets pad by `navigationBars` so the 3-button nav never
  overlaps the trash circle. Swipe cards may extend under the gesture nav bar
  (non-interactive region) for full-bleed drama.
- **Predictive back**: `android:enableOnBackInvokedCallback="true"` in the manifest;
  Compose Navigation 2.8+ gives the engine → dashboard pop a peek-behind animation.
  Sessions persist per-swipe, so predictive back needs no guard dialogs anywhere.

### 5.2 Dynamic color (Monet) — deliberate partial adoption

Full Monet would destroy the emerald/coral/gold semantic language (Keep must always
be green; Trash must always be red — these are safety colors, not decoration).
**Strategy: brand-locked accents, Monet-tinted neutrals.** Offer a Settings toggle
"Match system palette" that maps `surfaceContainer*` tiers to
`@android:color/system_neutral1_900/800/700` (S+) while keeping the four semantic
accents fixed. Themed (monochrome) app icon supplied via `ic_launcher_monochrome`.

### 5.3 Widgets (Glance)

1. **Storage Ring widget (2×2)** — the cyan ring + "% used" + staged-GB readout;
   tap → dashboard. Uses `RemoteViews`-safe Glance composables; updates via the
   analysis worker's completion callback.
2. **Quick Deck widget (4×2)** — "Best value" deck tile with reclaimable size and a
   "Swipe now →" button deep-linking into that deck (`deck/{id}` route already
   exists). Interactive `actionRunCallback` refresh.
3. **Lock-screen shortcut (API 34+)**: contribute a lock-screen quick-affordance-style
   entry via the widget on Android 15's lock-screen widget surface where available.

### 5.4 Rich notifications & glanceable status

- **Analysis progress**: ongoing low-priority notification while `MediaAnalysisWorker`
  runs — "Analyzing library · 8,412/23,000" with determinate progress; silent,
  dismissible, `setOngoing(false)` on API 34 (users can swipe it away).
- **Purge summary**: post-purge notification "2.4 GB freed · 38 files in 30-day
  trash" with an "Undo window: view trash" action → system Photos trash.
- **Storage pressure card** (§4 Tier 3.6): actionable, respectful (max 1/week,
  toggleable in Settings).
- No Live Activities equivalent abuse: nothing persistent while the app is closed and
  idle — silence is a privacy-brand feature.

### 5.5 Misc platform hygiene

- `createTrashRequest`/`createDeleteRequest` batched single-dialog flow (shipped).
- Android 14 partial photo access: first-class "Limited access" chip state (§3.1).
- App Shortcuts (long-press icon): "Best deck", "Staging drawer", "Rescan".
- `FLAG_SECURE` optional toggle for users triaging sensitive galleries.

---

## Phase 6 — Fable Ready-to-Render UI Prompt (Hero / Main Screen)

> **Self-contained spec — paste into Fable as-is.**

Render a single Android phone screen (1080×2400, 20:9, status bar time 9:41,
transparent status/nav bars, gesture pill visible) — the **Dashboard** of
"SwipeDelete Zero", a dark-only, OLED pitch-black storage-cleaning app.

**Canvas**: `#000000`. All containers: fill `#0D0F12`, 1 dp stroke `rgba(255,255,255,0.10)`,
no drop shadows. Font: Roboto (system). Base grid: 20 dp side margins, 20 dp vertical
rhythm. All text left-aligned unless noted. WCAG AAA: primary text `#F4F6FA`,
secondary `#8E95A2` at ≥ 12 sp Medium only; accent text always ≥ 14 sp Bold.

**Layout, top → bottom:**

1. **Header row** (top inset + 24 dp): Left column — "SwipeDelete Zero" 26 sp Black
   `#F4F6FA`; beneath it a pill chip (radius 12 dp, fill `#0D0F12`, hairline stroke):
   8 dp dot `#00E676` + "OFFLINE · 0 NET-PERMISSIONS" 12 sp Medium `#00E676`,
   letter-spacing 0.4. Right — 48 dp circular icon button, gear glyph 24 dp `#8E95A2`.
2. **Storage hero card** (radius 24 dp, padding 20 dp, height ~148 dp): Left — 96 dp
   progress ring, 10 dp stroke, track `#1A1E24`, progress 72% in `#00F0FF`, round
   caps; centered inside: "72%" 16 sp Black `#00F0FF` over "used" 12 sp `#8E95A2`.
   Right column (16 dp gap): "STAGED SPACE READY TO FREE" 12 sp Medium `#8E95A2`
   +0.4 tracking; "2.4 GB" 40 sp Black `#00E676`, −0.5 tracking, tabular figures;
   "41.2 GB free of 128 GB" 12 sp `#8E95A2`.
3. **Section title**: "Recommended Decks" 20 sp SemiBold `#F4F6FA`.
4. **Deck grid** — 2×2, 14 dp gutters, tiles radius 22 dp, padding 16 dp, height
   168 dp. Each tile: top row = 24 dp kind glyph (tinted per tile) left, 44 dp
   progress ring right (4 dp stroke, remaining-count number centered 14 sp Black
   white). Bottom block: title 16 sp SemiBold white, subtitle 12 sp `#8E95A2`,
   reclaimable size 14 sp Bold `#00F0FF`.
   - Tile A "Screenshots" / "Clutter Hotspot" / "1.2 GB" — glyph + ring `#00E676`;
     **this tile carries a 1 dp `#00E676` border and a top-right chip "BEST VALUE"
     (10 sp Black `#000000` on `#00E676`, radius 8 dp)**; ring shows "50".
   - Tile B "Heavy Hitters" / "Files over 100 MB" / "3.8 GB" — accents `#FF3B30`, ring "12".
   - Tile C "July 2024" / "Time Machine" / "890 MB" — accents `#00E676`, ring "26/50
     resumed" state: ring 48% filled, chip "RESUME" 10 sp gold `#FFD700` outline.
   - Tile D "Duplicates" / "23 near-dupe pairs" / "640 MB" — accents `#FFD700`, ring "23".
5. **Momentum strip** (radius 16 dp, fill `#080A0C`, hairline, padding 14 dp, height
   ~64 dp): left — "THIS WEEK" 10 sp Medium `#8E95A2`; "312 swiped · 1.8 GB freed"
   14 sp Bold `#F4F6FA`. Right — 7 slim vertical bars (4 dp wide, 6 dp gaps, heights
   varied 8–28 dp) in `#00E676`, last bar brightest.
6. **Bottom-pinned action bar** (20 dp margins, above nav inset): full-width 64 dp
   button, radius 20 dp, fill `#FF3B30`: left group — 24 dp white sweep-delete glyph +
   "Review Staging Drawer" 16 sp Black `#FFFFFF`; right — "38 · 2.4 GB" 14 sp Bold
   white. Button has a faint inner highlight top edge `rgba(255,255,255,0.08)`.

**States to imply**: all rings have subtle round-cap gloss; no gradients anywhere
except a barely-visible radial `#0D0F12 → #000000` behind the hero card. Nothing
overlaps the gesture pill. Every tap target ≥ 48 dp. The overall read: a precision
instrument — black glass, hairlines, four disciplined neon accents (emerald `#00E676`,
coral `#FF3B30`, cyan `#00F0FF`, gold `#FFD700`), zero decorative noise.

---

## Appendix — Prioritized engineering punch-list (from audit)

| # | Fix | Files | Effort |
|---|---|---|---|
| 1 | Haptics on swipe threshold/commit/undo/complete | `SwipeableCard.kt`, `SwipeEngineScreen.kt` | S |
| 2 | 48 dp touch-target sweep (`IconButton` everywhere) | Dashboard/Engine/Staging/Settings screens | S |
| 3 | `enableEdgeToEdge()` + inset padding, drop deprecated bar colors | `MainActivity.kt`, `Theme.kt` | S |
| 4 | Permission rationale gate screen + denial recovery | new `onboarding/` screen, `AppNavigation.kt` | M |
| 5 | Surface-container tier tokens | `Color.kt`, `Theme.kt` | S |
| 6 | Purge success moment (replace Toast) | `StagingDrawerScreen.kt`, `StagingViewModel.kt` | M |
| 7 | Skeleton loaders + real empty states | Dashboard, Engine, Staging | M |
| 8 | Staging as modal bottom sheet; confirm-on-clear | `StagingDrawerScreen.kt`, nav | M |
| 9 | Shared-element deck-open transition | `AppNavigation.kt`, Dashboard, Engine | M |
| 10 | Predictive back opt-in | `AndroidManifest.xml` | XS |
| 11 | Deck grid (2×2) + best-value badge + resume chips | `DashboardScreen.kt`, `DashboardViewModel.kt` | M |
| 12 | Next-3 bitmap preload | `SwipeEngineViewModel.kt`, `MediaPreview.kt` | M |
| 13 | Glance widgets (ring + quick deck) | new `widget/` module | L |
| 14 | Monet-neutral toggle + themed icon | `Theme.kt`, res | M |
| 15 | Burst collapse + auto-stage exact dupes | `DeckBuilder.kt`, `PerceptualHasher.kt` | L |
