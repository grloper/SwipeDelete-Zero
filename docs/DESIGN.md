# SwipeDelete Zero — Design System

**Direction: "Negative Space"** · v4.0.0

Both the metaphor and the visual language come from photography, not from
generic dark-mode app defaults. The app is about *reclaiming room*, and the
mark is a film negative whose aperture is cut clean through as a zero — the
empty frame is the product.

Retired in this rebuild: flat `#000000`, stock neon green `#00E676`, and
thin-stroke outlined Material cards. All three are gone from the codebase, not
merely reduced.

---

## 1. Where values live

`app/src/main/java/com/swipedelete/zero/ui/theme/SdzTokens.kt` is the only
place a colour, radius, spacing step, elevation or duration may be defined.
`Type.kt` is the only place a text style may be defined. Screens compose named
tokens; **no screen contains a raw hex value or a bare `.dp` styling number.**

That constraint is the actual fix for the old inconsistency. The previous UI
had 300 direct references to a flat palette object, each screen choosing
locally, which is how the same green ended up meaning "pile of clutter" on Home
and "keep this" on the deck.

---

## 2. Colour — one meaning each, nowhere else

| Role | Token | Value | Means, always and only |
|---|---|---|---|
| Keep / confirmed | `Azure` | `#5B9DF9` | safe and settled: kept, protected, verified, step complete |
| Reclaim | `Amber` | `#E8A33D` | space you can get back — queued, or freed |
| Archive | `Teal` | `#4FC3B4` | goes to the cloud |
| Critical | `Safelight` | `#E5484D` | irreversible, or storage genuinely critical |
| Brand | `Phosphor` | `#F2EDE4` | identity and primary text — never a state |

**Why amber for trash.** Queueing a file is reversible and it is the entire
point of the app; dressing it as danger was the single most misleading thing in
the old palette, which used the same red for "delete forever" and for a
friendly "tap to review" banner. Amber means *reclaimable space* consistently:
the queued file, the reclaimable segment of the storage meter, and the number
that counts up when space comes back. One idea, one colour.

**Azure's scope is "settled and safe."** That covers the keep decision, a
verified backup, and a completed setup step — one idea, not three. Anything
that is *in flight to the cloud* is Teal instead, which is why upload progress
is teal even though a finished upload leaves a file safe.

**Red is rationed.** Safelight red appears in exactly two places — the
permanent-purge button and a storage meter that is genuinely critical. It is
never chrome and never the default delete tint.

**Surfaces** are a four-step warm charcoal ramp (`#0D0C0B` → `#363029`), not
true black. Depth comes from tone and shadow, so borders are rare rather than
drawn around every box.

**Contrast**, measured on `Surface1`: primary text ~15:1, secondary ~7.8:1,
tertiary ~4.8:1 — all clear of the 4.5:1 body floor, so even the dimmest text
role passes for body copy rather than only for large text.

---

## 3. Type — two families, one rule

| | Family | Used for |
|---|---|---|
| Display | **Space Grotesk** (500/700) | numeric values, screen titles, wordmark |
| Body | **Inter** (400/500/600) | everything else |

Space Grotesk descends from Space Mono, so its figures have the squared
instrument-panel character of a light meter, and it holds at 56 sp where a
neutral UI face goes bland. Inter is drawn for small-size screen UI and stays
legible at 11 sp, which Space Grotesk does not.

Both are **bundled** (1.1 MB). Downloadable fonts would route through Play
Services and break the fdroid build's air-gap guarantee.

Every numeric style sets `tnum`. A counter that reflows while animating is
unreadable, and the freed-space figure animates.

Scale: `HeroNumber` 56 · `StatNumber` 28 · `Title` 22 · `Subtitle` 17 ·
`Body` 15 · `BodySmall` 13 · `Label` 14 · `Numeric` 13 · `LabelSmall` 11 ·
`Overline` 11.

---

## 4. Space, radius, motion

- **Spacing** — 4 dp base: 2, 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.
- **Radius** — xs 6 · sm 10 · md 14 · lg 20 · xl 28 · pill.
- **Elevation** — flat 0 · raised 2 · floating 8 · dialog 16 · dragging 24.
- **Touch** — nothing tappable below 48 dp; primary deck actions 72 dp.
- **Motion** — Instant 90 ms · Quick 160 · Standard 240 · Expressive 400 ·
  Celebration 900. Emphasised easing `cubic-bezier(0.2, 0, 0, 1)`.
  Cards settle on a bouncy low-stiffness spring; they exit critically damped
  carrying the finger's release velocity.

---

## 5. Iconography

One set, one 24 dp grid, 1.8 dp stroke (2.1 for the cross so it survives small
sizes), round caps and joins: `res/drawable/ic_action_*.xml` and
`ic_bucket_*.xml`, surfaced through `SdzIcons`.

The three decision icons are drawn so their **silhouettes** differ, not just
their colours: Keep is an enclosed shield, Reclaim is an open diagonal cross,
Archive is a rounded cloud. They read apart in greyscale and at 16 px.

**Two tiers, one rule.** Anything that carries meaning — the four decision
actions, the five bucket categories, back — is custom-drawn on the grid above
and reached through `SdzIcons`. Purely utilitarian glyphs (copy, external link,
check, close) come from **Material Icons Rounded only**, one variant, never
mixed with Filled or Outlined. The old build mixed variants — it used both
`Icons.Rounded.ArrowBack` and `Icons.AutoMirrored.Rounded.ArrowBack` for the
same affordance — which is exactly the drift this rule removes.

---

## 6. Logo

Three concepts were built as real geometry; all three are in the repo.

1. **The negative** *(shipped)* — a film frame, sprocket notches biting into
   the left and right edges, the aperture cut through as a `0`. One evenOdd
   path, so it is a true silhouette: one flat colour, no strokes, inverts
   cleanly. `ic_logo_mark.xml`
2. **The deck** — three stacked frames, the top one mid-swipe.
   `ic_logo_concept_2.xml`. Rejected: three overlapping bodies merge into a
   blob below ~24 px, and the rotation never sits square in a launcher grid.
3. **The aperture** — six iris blades stopping down to a circular opening.
   `ic_logo_concept_3.xml`. Rejected: thin radial wedges alias into a grey disc
   at 16 px, and it collides with every camera app icon ever made.

Concept 1 ships because it is legible at 16 px, encodes both halves of the name
(*negative* and *zero*), and needs no colour to work.

Geometry, 48 dp viewport: frame `x 4..44, y 4..44, r 7.5`; notches `r 4`
centred on the edge at `y 18` and `y 30`, both sides; zero `ellipse (24,24)
rx 9 ry 11.5`.

Delivered: adaptive icon with separate foreground/background layers (mark
scaled into the 66 dp safe circle) · monochrome layer for themed icons ·
splash screen on `surface_0` via `core-splashscreen` so it works on API 29+ ·
`SdzWordmark` lockup where the mark's height equals the wordmark cap-height and
the gap is half the mark's width.

---

## 7. The ten fixes

**1 · Colour meant contradictory things.** Rebuilt as the exclusive-role table
above. Green is gone entirely; keep is Azure, reclaim is Amber, and the four
unrelated decorative colours on the bucket cards are gone — bucket rows now
share one neutral treatment and are distinguished by icon, not hue.

**2 · Hierarchy was inverted.** `StorageMeter` is now the hero: the percentage
is set in the 56 sp display face and coloured by urgency, with free space
beside it. The less-actionable "flagged" figure moved into the single primary
call-to-action below it. Urgency is carried by four independent channels —
colour, fill proportion, a status word ("Filling up" / "Critically full"), and
a breathing leading cell that appears *only* at critical so the motion means
something.

**3 · Red-vs-green decisions.** Replaced with blue-vs-amber, the canonical
CVD-safe pair, and hue is never load-bearing: every decision also carries a
distinct icon silhouette, a fixed screen position (reclaim left, keep right,
archive up — matching the gestures), and a visible text label.

**4 · Contradictory comparison badges.** `ComparisonPair` used `>=`/`<=`, so
ties silently resolved to `primary` and two byte-identical files were tagged
both "Higher Res" and "Smaller". Every accessor now returns null unless one
file is genuinely better, with epsilons for noise (2% on size and resolution,
5.0 on Laplacian variance). Identical pairs show no badges and say so.

**5 · Stale aggregates.** A bucket showed "0 items" beside a size from when it
held nine, because the count used remaining items while the size summed all of
them forever. `Deck.remainingBytes` derives from `remainingItems` — the same
set the count uses — so the two cannot drift. `totalBytes` remains available
where an all-time figure is genuinely wanted.

**6 · Siblings exposed different fields.** Both library lenses now render
through one `LibraryEntry` model and one `LibraryRow`, so every row in every
list shows cover, title, "N left · SIZE", progress — same fields, same order,
by construction rather than by discipline.

**7 · Unlabelled icon-only actions.** `DecisionActionRow` gives every control a
**visible** label plus an accessible name, applies `navigationBarsPadding()` so
it always clears the gesture inset, and keeps every target ≥48 dp. A one-time
`DeckCoachmark` teaches the three gestures on first run, persisted in DataStore.

**8 · No emotional payoff.** `FreedCelebration` counts the freed figure *up*
from zero in the display face while a strip of film cells fills beneath it,
ticking haptics as it climbs and landing on a three-beat confirm. It fires at
deck complete and after a purge. The count-down is the point of the app; it now
has a moment.

**9 · Text screenshots sorted as photos.** `TextDetector` measures luminance
bimodality on the 32×32 matrix the hashing pass already decodes — a page of
text is strongly bimodal, a photograph fills the midtones — so classification
costs no extra decode. `MediaClass` turns that into PHOTO / VIDEO / SCREENSHOT
/ DOCUMENT; documents get their own bucket and non-photos carry a card badge
saying what they are.

**10 · Generic logo.** Replaced per §6. The old mark was an outlined card with
a red/green collision that repeated the palette bug at 48 dp.

---

## 8. IA changes

- **Home** has exactly one primary button. Storage is the hero; everything else
  is a list.
- **Buckets and sprints** are stated as *the same photos, two lenses* and
  switched with a toggle — "By content" vs "By date" — rather than being two
  unrelated stacked sections.
- **Compare** is tap-the-keeper: the photograph itself is the target, so the
  primary decision costs one tap on the thing you are already looking at.
  "Keep both" / "Reclaim both" are demoted to tertiary links.
- **Pre-permission** — `PurgeConfirmSheet` runs before the OS dialog, naming
  what goes, how much comes back, whether it is reversible, and warning that a
  system prompt is next. Permanent purge is the only place the destructive
  button style appears.
- **Documents** is a real bucket, per fix 9.

---

## 9. Accessibility

- Body text ≥4.5:1; the dimmest text role measures ~4.8:1.
- Touch targets ≥48 dp, with `navigationBarsPadding()` on every bottom-anchored
  control.
- Every icon-only control has a visible label or a `contentDescription`.
- No decision depends on hue: icon silhouette, position and text all carry it.
- The storage meter exposes a spoken summary; the celebration is an assertive
  live region so the result is announced rather than skipped.
