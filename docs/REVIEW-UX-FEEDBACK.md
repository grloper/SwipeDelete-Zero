# Local review feedback and safety boundaries

The five audio samples under `docs/audio` are original procedural 90 ms mono PCM previews of the same harmonic gestures generated in the app. They are local review confirmations: keep, stage, queue, star and undo. No sound claims a remote backup completed or an original was deleted. There is no cloud-success sound in this change.

Mute is saved locally and available in Settings. Playback respects silent media volume, foreground activity and review-screen lifecycle, audio focus and active music; bursts are limited to one chime per 180 ms. Feedback happens only after the local action, progress persistence and UI commit. Audio failure cannot undo a successful operation. Leaving the review screen or backgrounding suppresses late confirmations.

Photo onboarding requests images only on Android 13+, plus selected-visual permission on Android 14+. Videos/audio are separate optional choices. Android 12 and older use the legacy shared storage permission and cannot offer the same granular scope. Selection changes clear old dashboard summaries and force a fresh scan; older in-flight summary results cannot replace newer ones.

Review completion summarizes staged bytes/files and states Nothing was deleted. The actual purge-result component retains its verified-deletion wording. Review completion does not play reclaimed-space reward ticks. Swipe/undo/deck changes and comparison actions reject overlapping gestures. Loading a new deck clears old undo and per-session staged counts. Offline Star undo removes only that starred URI from the exclusion vault. Photos undo only cancels queued work; already running uploads may continue and are disclosed.

## Validation prepared

`PhotoPermissionRequestTest`, `ReviewFeedbackTest`, `ReviewViewModelConcurrencyTest` and `ExclusionUndoTest` cover permission scope, silent failures/cancellation, mute and playback failure, delayed mixed actions, single-step comparison progression, deck-scoped undo, failed stage with retry, precise exclusion undo and persistent mute. Run both flavor unit suites, lint and debug builds with the coordinated build owner. These tests have been authored but not run in this implementation worker, by instruction. Device screenshots, actual listening and selected/revoked-access instrumentation remain the build/device owner's validation responsibilities.

Central Play cleanup lock and iCloud limitations are unchanged. There are no OAuth or personal-media operations in this change.

## Full-app synthetic endurance and permission journey

`FullAppReviewJourneyTest.syntheticThirtySessionsPreserveOriginalsAndPersistUndo`
launches the production MainActivity and repository wiring on an isolated API35
AVD. It writes 120 committed positive-size synthetic PNG originals by default
(`fixtureCount` accepts 100–1000), then performs 30 enter/stage/undo/keep-or-stage/
exit cycles. It recreates the activity midway and at the end, checks persisted
staged/kept row deltas, checks the visible cleanup lock, and rehashes every
original after every cycle. It never invokes upload or cleanup. Original rows
are deliberately retained as evidence until the disposable AVD is removed.

Evidence contains actual screen captures, Compose semantics snapshots and URI/
SHA256 manifest in external-files `journey-evidence`, pulled to the CI artifact.
This proves synthetic local review journeys only; no account, cloud restore,
selected-photo picker interaction or personal-image deletion is covered.

`scripts/ci_permission_journey.sh` performs two real OS revoke/regrant cycles
without clearing app data. Revocation must happen outside instrumentation:
Android kills the target UID when a runtime permission is revoked. Each denied
phase checks the real permission state and photo-only onboarding; each regrant
phase launches a fresh activity and checks the library returns. It uses API35
permission names and is intended only for the synthetic CI AVD. The full run has
a 900-second bounded instrumentation deadline and the CI job a 35-minute budget.

These new tests must compile and pass on exact-head CI before claiming endurance
or permission runtime evidence. Authoring and static review are not a runtime pass.

Permission regrant phases additionally load the durable full-journey checkpoint,
assert unchanged staged/kept counts, reopen a nonempty deck with enabled Stage
and Keep controls, return without making another decision, and recompute the
SHA256 of every owned synthetic original. The manifest is retained across the
separate instrumentation processes; denial screenshots cannot overwrite it
with an empty manifest. Each URI is checked against the fixture naming prefix
before reading its bytes.

The emulator action invokes one checked-in `ci_review_journey.sh` script so its
multiline failure gate runs in one shell, rather than separate action script lines.
