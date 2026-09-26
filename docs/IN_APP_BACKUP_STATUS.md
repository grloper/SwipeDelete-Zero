# In-app backup integration status

## Available implementation

One Google connection grants the existing Drive and Photos permissions. Settings
contains the connection and Drive backup action; Backups shows the Photos queue
and upload history. The routine setup wizard entry is removed; troubleshooting
remains available after an authentication failure.

- Google Drive: existing kept-file and staged-file uploads now hash the bytes sent, download the
  uploaded object in a separate authenticated request, compare SHA-256 and byte
  count, and re-read the local original before recording completion.
- Google Photos: existing swipe-up and staged-file uploads use the durable queue
  and app-created item readback. This confirms an item, not original-byte recovery.
- Backup history opens Drive IDs in Drive and Photos IDs through Photos readback.
- No deletion eligibility changes. Play cleanup remains centrally disabled.

The Drive verification costs a full download in addition to the upload. Failed
verification retains the local file and records no completed receipt. Retrying a
failed multipart operation may leave duplicate/orphan objects remotely; resumable,
content-addressed upload recovery is still required before production release.

## Still required for backup-then-delete release

Database v6 adds account/provider-scoped receipts with the checked original hash
and size for new Drive uploads. The Drive work list includes staged originals once each. Legacy rows remain separate because they have no
hash or authenticated owner; they cannot be promoted into verified receipts.
Required work includes:

1. Immutable local upload snapshots and a remote manifest discoverable after a
   clean install, without relying on the current device database.
2. Resumable transfer and lifecycle recovery, storage/quota errors and account
   switch handling throughout the persistent queue.
3. Restore to a user-selected destination, verify its bytes, then separately
   revalidate the unchanged local original immediately before a confirmed delete.
4. Live synthetic-file upload/restore tests against the configured Google project,
   clean-install restore tests and physical-device validation.

These changes do not satisfy or unlock the deletion gate.

## Apple integration

An app-specific iCloud vault via CloudKit Web Services is a candidate for a separate
integration. CloudKit is not an API for importing into the user's iCloud Photos
library or arbitrary iCloud Drive folders. No iCloud connection is shipped or
advertised by this change.

It requires an Apple Developer app/container, a deployed private-database schema,
web authentication configuration and a supported account-authentication flow.
No configured container or live Apple integration credentials were available in
this checkout. Verify service terms, account eligibility, authentication on Android,
asset limits, quota behavior, restore and revocation with a prototype before
promising Android iCloud backup. Never embed Apple passwords or a server private
key in the Android application.

Official references checked 2026-09-27:
- https://developers.google.com/workspace/drive/api/guides/manage-downloads
- https://developer.apple.com/library/archive/documentation/DataManagement/Conceptual/CloudKitWebServicesReference/index.html
- https://developer.apple.com/library/archive/documentation/DataManagement/Conceptual/CloudKitWebServicesReference/SettingUpWebServices.html
