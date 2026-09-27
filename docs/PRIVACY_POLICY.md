# SwipeRise privacy policy

Last updated: September 27, 2026

This policy describes the Google Play edition of SwipeRise (`com.swipedelete.zero`).

With permission, SwipeRise reads the photos, videos and other files you allow it to review. It stores review choices, exclusions, analysis results, staging, backup receipts and cleanup history in its local database. It does not run ads or analytics, and does not send files to the developer's server.

Connecting a Google account is optional. When you choose to archive a photo or video, the app uploads it to **your Google Photos account** and stores the returned item ID locally. The Google Photos API can confirm that the app-created item exists; it does not provide an independent checksum of the original bytes. When you tap **Back up now**, the app uploads kept and staged files to an app-created folder in **your Google Drive account**, stores a local account-scoped receipt, and checks a fresh download against the original SHA-256 hash. Drive files created with the current manifest can be discovered after a clean install and restored to a location you choose, with the downloaded and saved bytes checked again. Your filenames, file bytes and backup metadata are sent to Google for these requested operations; Google processes them under your Google account and its terms.

The Play test build keeps local cleanup locked. Staging and undo do not delete the original. A Google Photos item or Drive receipt is not a promise of permanent cloud retention. You can disconnect Google in the app, revoke access through your Google account, delete your uploaded copies in Google, or revoke Android media permission in Settings. Disconnecting does not delete remote files. Clearing app data or uninstalling removes the local database, but not the originals in your Android library or uploaded Google files. Legacy Drive uploads made before restore metadata was added might not appear in the clean-install Restore tab.

The separately distributed F-Droid edition does not request internet access or connect to Google; see [cloud setup](DRIVE_BACKUP_SETUP.md) for the Play edition. For privacy requests or questions, use the [project issue tracker](https://github.com/grloper/SwipeDelete-Zero/issues). There is no developer-hosted account to erase.
