# Google Drive Backup — one-time setup

The **cloud** APK (`SwipeDeleteZero-x.y.z-cloud-drive-debug.apk`) can back up every
file you *keep* or *star* to your own Google Drive. Each file is uploaded **once**;
later runs only pick up newly kept files — nothing is uploaded twice.

Google requires every app that touches Drive to have an OAuth client. Because this
project ships no secrets, **you** create that client in your own (free) Google
Cloud project. It takes about ten minutes, once.

## 1. Create a Google Cloud project

1. Open <https://console.cloud.google.com/projectcreate>.
2. Name it anything (e.g. `swipedelete-backup`) → **Create**.

## 2. Enable the Drive API

1. In the console, go to **APIs & Services → Library**.
2. Search **Google Drive API** → **Enable**.

## 3. Configure the OAuth consent screen

1. **APIs & Services → OAuth consent screen**.
2. User type: **External** → Create.
3. Fill only the required fields (app name, your email) → Save.
4. Under **Test users**, add the Google account you'll back up to.
   (In *Testing* mode only test users can sign in — that's you, and it never
   needs Google review.)

## 4. Create the Android OAuth client

1. **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Application type: **Android**.
3. Package name:

   ```
   com.swipedelete.zero.debug
   ```

   (Debug builds append `.debug` to the application id.)
4. SHA-1 certificate fingerprint — the repo ships a committed debug keystore so
   this value is stable for every APK built by CI:

   ```
   BB:3D:21:3A:EE:FA:11:D4:65:C7:77:6C:6E:99:2B:8B:98:1B:1E:DB
   ```

   To verify it yourself: `keytool -list -v -keystore app/debug.keystore -alias androiddebugkey -storepass android | grep SHA1`
5. **Create**. No client id needs to be pasted anywhere — Android OAuth clients
   are matched by package name + signature automatically.

## 5. Use it

1. Install the **cloud** APK from the Releases page.
2. App → ⚙ Settings → **Google Drive Backup** → *Connect Google Drive*.
3. Sign in with the test-user account and accept the `drive.file` scope — the
   app can only see files it created, never your whole Drive.
4. Tap **Back up now**. Files land in a Drive folder named
   **SwipeDelete Zero Backup**.

## Privacy notes

- Only the `cloud` flavor declares `android.permission.INTERNET`. The `fdroid`
  APK remains architecturally incapable of network access.
- The only scope requested is `drive.file` (non-sensitive): the app sees and
  writes **only files it created itself**.
- Backup runs only when you tap the button. No background sync, no telemetry.
