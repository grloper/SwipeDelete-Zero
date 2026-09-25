# SwipeRise Product Direction & Architectural Roadmap

> **Document Status**: Product Decisions & Future Milestone Planning (Non-Code Baseline)  
> **Prepared**: 2026-09-26  
> **Applicability**: Post-M0 Roadmap Guidance. Package identity, application ID, signing keys, and repository name remain strictly unchanged during M0.

---

## 1. Brand Identity & Store Placement

- **Working Consumer Brand**: **SwipeRise** (clean brand, no promotional badges).
- **Package & Namespace Containment**:
  - `applicationId`: `com.swipedelete.zero` (unchanged).
  - Kotlin package namespace: `com.swipedelete.zero` (unchanged).
  - OAuth client credentials, SHA-1 fingerprints, and keystores remain unchanged.
  - Store listings, display names, and launcher icons remain untouched in M0; any display-name transition requires a dedicated, reviewed milestone post-M0 acceptance.
- **Store Compliance & Metadata Guardrails**:
  - Google Play Policy and Apple App Store Review Guideline 2.3.7 explicitly prohibit promotional or pricing elements (e.g. "FREE", "#1", "Best", "Ad-Free") in app titles, icons, or developer names.
  - The generated concept artwork (`swiperise_free_brand_presentation.png`) is a preliminary moodboard only. Its promotional price badges are **not approved** for store deployment.
  - All store metadata and visual marks must present the clean name "SwipeRise" without promotional badges.
  - Explanatory copy concerning free functionality belongs in in-app help/onboarding and app store descriptions, grounded in policy compliance.

---

## 2. Gesture Contract & Core UX Architecture

SwipeRise centers on a deliberate, non-destructive tri-directional gesture model:

| Direction | Action | Semantics & Safety Invariants |
| :--- | :--- | :--- |
| **UP (Signature)** | **Queue Cloud Upload** | Queues the item for background backup to the user's deliberately configured cloud destination and account. Never deletes local original. Never reduces to a favorite/star toggle. Supplemented by accessible buttons. |
| **RIGHT** | **Keep Locally** | Protects the photo locally, keeping it on device storage. Excludes from cleanup queues. |
| **LEFT** | **Stage for Cleanup** | Places item in Staging Review Drawer (Tier 2). **Never deletes immediately**. Subject to batch confirmation and user verification. |

### Up-Swipe Contract & First-Use Handling
1. **Explicit Account & Destination Binding**:
   - First up-swipe when no cloud provider is configured triggers the setup wizard explaining destination options. No media is silently uploaded or routed to an unconfigured destination.
   - Account changes immediately stop and quarantine unassociated queued items; work is never reassigned to another account.
2. **Accessible Control Alternatives**:
   - Visible buttons supplement all gestures for accessibility; buttons serve as alternative controls, not replacements for the signature swipe-up gesture.
3. **No Automatic Local Eviction**:
   - Swiping up authorizes an upload; it never authorizes deleting the local original.
   - In M0, the local deletion gate remains **permanently locked** across all builds.
4. **Transparent Cancellation**:
   - Cancelling an in-flight review or clearing staging does not imply an already-uploaded remote item has been deleted.

---

## 3. Truthful Lifecycle State Machine

Items transition through transparent, discrete states. Visual animations or upload receipts never substitute for server verification:

```
[QUEUED] ──► [UPLOADING] ──► [PROCESSING] ──► [AVAILABLE IN PHOTOS] ──► [ORIGINAL RESTORE VERIFIED (M1)]
   │              │                 │
   ▼              ▼                 ▼
[FAILED]      [FAILED]          [FAILED]
```

1. **`QUEUED`**: Item is persistently recorded in local Room DB with durable `accountName`.
2. **`UPLOADING`**: Active chunked resumable upload in progress under validated account session.
3. **`PROCESSING`**: Media created; waiting for provider indexing/transcoding (videos).
4. **`AVAILABLE_IN_PHOTOS`**: Confirmed visible via readback GET metadata.
5. **`ORIGINAL_RESTORE_VERIFIED`**: (M1 Target) Byte-for-byte original recovery verified from the cloud originals vault. **Does not exist in M0**.

---

## 4. Provider Integrations & Quota Truthfulness

### Google Photos & Originals Vault
- Google Photos API compresses or alters certain media based on provider storage settings.
- Google Photos remains an active integration, but a separate **Originals Vault** (M1) must be explicitly selected and named. It must never be deceptively conflated with standard Google Photos backup.

### iCloud Integration (iOS Milestone)
- iCloud support is reserved for the future iOS codebase.
- Architectural distinction must be maintained: **iCloud Photos** (which synchronizes deletions across all user devices) vs **iCloud Drive / CloudKit** (independent container storage).
- Deleting an asset under iCloud Photos synchronizes deletions; it is not a local-only eviction.
- No simulated iCloud features, private APIs, or credential scraping will be introduced on Android.

### Free App vs Provider Storage Quotas
- The application is 100% free: no paid tiers, subscriptions, trial limits, artificial swipe caps, ads, or tracking SDKs.
- Third-party cloud storage (Google Drive/Photos, Apple iCloud) remains subject to each provider's account limits and quotas.
- Copy must truthfully explain provider quotas without upsell pressure or false claims of expanding cloud storage capacity.

---

## 5. Visual Design Direction

- **Primary Motif**: Upward photo-to-cloud motion, expressive depth, deep slate backgrounds (`#0B0F17`) with vibrant cyan (`#00F2FE`), electric blue (`#4FACFE`), and violet accents (`#7F00FF`).
- **Running M0 Scope**: M0 preserves the existing running UI with the immediate M0 Safety Lock banner and explanation sheet. Full UI restyling, polished vector icons, and branding transitions are scheduled as independent milestones post-M0.
