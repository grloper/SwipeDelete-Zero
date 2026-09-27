# In-app backup integration status

## Available implementation

One Google connection requests the existing Drive and Photos permissions. Settings
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
verification retains the local file and records no completed receipt. Files over
5 MiB use a Drive resumable session. The private app store saves the session URL
before sending bytes; retries query Drive's received offset and send only the
remaining bytes. Before creating a new upload, the app searches its Drive folder
for a matching manifest from the same local source and independently downloads
that object to verify the bytes. This can recover a completed upload whose local
receipt was interrupted. Older remote objects without a source identifier are
not automatically reused, so a retry of an older interrupted upload may still
leave a duplicate Drive object.
Live account testing of session expiry, API errors, account switch, and ambiguous
network failures is still required before release.

## Still required for backup-then-delete release

Database v6 adds account/provider-scoped receipts with the checked original hash
and size for new Drive uploads. New remote Drive objects carry an app-specific
manifest (hash, size and version) so a newly installed app can list and restore
backups even when the local database is gone. Restore downloads into private
temporary storage, compares the actual bytes, writes to the user-selected new
Android document, and reads it back to verify again. Old pre-manifest uploads
will not appear in clean-install Restore; they must be re-backed up.
The Drive work list includes staged originals once each. Legacy rows remain
separate because they have no hash or authenticated owner; they cannot be promoted into verified receipts.
Required work includes:

1. Immutable local upload snapshots and complete manifest lifecycle/recovery,
   including older uploaded files and orphan cleanup.
2. Live validation of resumable transfer and lifecycle recovery, storage/quota
   errors and account switch handling throughout the persistent queue.
3. Live restore to a user-selected destination on a clean install and revalidation
   of the unchanged local original immediately before a confirmed delete.
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
