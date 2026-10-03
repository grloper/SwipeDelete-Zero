# Local review feedback and safety boundaries

The five audio samples under `docs/audio` are original procedural 90 ms mono PCM previews of the same harmonic gestures generated in the app. They are local review confirmations: keep, stage, queue, star and undo. No sound claims a remote backup completed or an original was deleted. There is no cloud-success sound in this change.

Mute is saved locally and available in Settings. Playback respects silent media volume, foreground activity and review-screen lifecycle, audio focus and active music; bursts are limited to one chime per 180 ms. Feedback happens only after the local action, progress persistence and UI commit. Audio failure cannot undo a successful operation. Leaving the review screen or backgrounding suppresses late confirmations.

Photo onboarding requests images only on Android 13+, plus selected-visual permission on Android 14+. Videos/audio are separate optional choices. Android 12 and older use the legacy shared storage permission and cannot offer the same granular scope. Selection changes clear old dashboard summaries and force a fresh scan; older in-flight summary results cannot replace newer ones.

Review completion summarizes staged bytes/files and states Nothing was deleted. The actual purge-result component retains its verified-deletion wording. Review completion does not play reclaimed-space reward ticks. Swipe/undo/deck changes and comparison actions reject overlapping gestures. Loading a new deck clears old undo and per-session staged counts. Offline Star undo removes only that starred URI from the exclusion vault. Photos undo only cancels queued work; already running uploads may continue and are disclosed.

## Validation prepared

`PhotoPermissionRequestTest`, `ReviewFeedbackTest`, `ReviewViewModelConcurrencyTest` and `ExclusionUndoTest` cover permission scope, silent failures/cancellation, mute and playback failure, delayed mixed actions, single-step comparison progression, deck-scoped undo, failed stage with retry, precise exclusion undo and persistent mute. Run both flavor unit suites, lint and debug builds with the coordinated build owner. These tests have been authored but not run in this implementation worker, by instruction. Device screenshots, actual listening and selected/revoked-access instrumentation remain the build/device owner's validation responsibilities.

Central Play cleanup lock and iCloud limitations are unchanged. There are no OAuth or personal-media operations in this change.
