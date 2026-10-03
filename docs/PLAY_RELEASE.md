# Google Play release preparation

SwipeRise's Play variant uses package `com.swipedelete.zero`, versionCode 8, and target API 36. It has INTERNET for optional Google backups and no all-files permission. **This is a test build with local cleanup locked.** Do not advertise automatic freeing of space or publish it as a fully functioning cleaner until the safety gate is implemented and validated against live accounts and devices.

## Build and sign

Create and securely retain a private upload key. Never use the committed debug keystore for Play and never commit passwords. Set `SDZ_UPLOAD_KEYSTORE` to its absolute path plus `SDZ_UPLOAD_STORE_PASSWORD`, `SDZ_UPLOAD_KEY_ALIAS`, and `SDZ_UPLOAD_KEY_PASSWORD`. Run `./gradlew :app:testPlayDebugUnitTest :app:testFdroidDebugUnitTest :app:bundlePlayRelease`. The GitHub workflow creates a signed AAB only if all four corresponding repository secrets are set, including `SDZ_UPLOAD_KEYSTORE_BASE64`; otherwise it compiles but does not publish a signed bundle. Enroll in Play App Signing and upload the signed AAB first to internal testing. Increment versionCode for later releases.

## Before any public launch

- Set up Google Cloud Android OAuth clients for the actual Play signing certificate, enable the Drive and Photos APIs, configure consent and complete required OAuth scope verification. Changing OAuth client identity can affect access to app-created Photos items.
- Perform live sign-in, Photos upload/readback, Drive original-byte backup, clean-install remote discovery/restore, failed/revoked/offline transfer, account switch, process death and duplicate retry tests on real Android devices. Test the OS media permission paths across Android 10, 13, 14 and 16. The isolated CI emulator uses synthetic media and does **not** authenticate to a real Google account.
- Implement and test deletion eligibility only after independently restoring the **same original bytes** and rechecking provider, account, remote identity and current local file before Android's deletion request. Preserve default-deny for unknown states and all other file types. Do not lift the Play cleanup lock on the strength of Photos metadata alone.
- Inspect the Play pre-launch report, complete the media-permission declaration for continuous library review, and fill in Data safety, app access, rating, audience and ads answers against the exact signed binary and SDK behavior. Host this [privacy policy](PRIVACY_POLICY.md) at a public URL; this repository is private until you deliberately publish a policy page.
- Supply genuine installed-app screenshots, launcher icon, feature graphic and copy that describes only working features. Complete any closed testing required for the developer account before requesting production access.

Google Photos does not expose an original-byte checksum through the app-created media API. Drive's current manifest and restore checks provide a stronger independent byte check but still need live-account validation. A provider can later remove files; no app can guarantee permanent retention. iCloud Photos and iCloud Drive are not connected in this Android build. CloudKit can only address an app-specific container and requires Apple developer setup; it is not access to a user's arbitrary iCloud library.

## Draft store copy for a backup beta

**Title:** SwipeRise

**Short description:** Review your media and back up selected originals to your Google account.

**Description:** Review photos and videos on your device. Keep, stage or undo your choices. Connect Google to archive selected media to Google Photos or back up kept and staged files to your own Google Drive. Drive backup checks a fresh download against the original file, and its Restore tab can save eligible originals to a location you choose. Local deletion is unavailable in this test build. No ads or analytics.
