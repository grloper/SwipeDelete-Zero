# Google Drive Backup & Google Photos Archive — one-time setup

The **cloud** APK (`SwipeDeleteZero-x.y.z-cloud-drive-debug.apk`) can back up every
file you *keep* or *star* to your own Google Drive. Each file is uploaded **once**;
later runs only pick up newly kept files — nothing is uploaded twice.

The same build also powers the **swipe-up "Archive to Google Photos"** action:
resumable chunked uploads to your Photos library, with the local copy offered
for deletion only after Google confirms the upload with a valid `mediaItemId`.
Both features share one Google sign-in and the OAuth client below.

Google requires every app that touches Drive to have an OAuth client. Because this
project ships no secrets, **you** create that client in your own (free) Google
Cloud project. It takes about ten minutes, once.

> ### 👉 Use the in-app wizard instead of this document
>
> Open **Settings → Open setup wizard**. It walks the same five steps, opens each
> Google Cloud page directly, and — critically — shows the package name and
> SHA-1 **read from the app installed on your device**, with copy buttons.
>
> That matters because the fingerprint below is only valid for APKs signed with
> this repo's committed debug keystore. Rebuild locally, or install a
> differently-signed build, and the real fingerprint changes while this document
> does not — which is exactly how you end up staring at
> `Sign-in failed (code 10)` while believing everything is configured correctly.
> The wizard cannot go stale, and it decodes every sign-in error into the step
> that fixes it.
>
> The steps below remain as a reference for setting things up from a desktop.

## 1. Create a Google Cloud project

1. Open <https://console.cloud.google.com/projectcreate>.
2. Name it anything (e.g. `swipedelete-backup`) → **Create**.

## 2. Enable the APIs

1. In the console, go to **APIs & Services → Library**.
2. Search **Google Drive API** → **Enable**.
3. Search **Google Photos Library API** → **Enable** (needed only for the
   swipe-up Photos archive; skip if you'll only use Drive backup).

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
4. SHA-1 certificate fingerprint. **The authoritative value is the one the app
   shows in Settings → setup wizard → step 3**, because it is read from the
   installed APK's actual signing certificate.

   For APKs built by this repo's CI with the committed debug keystore it is:

   ```
   BB:3D:21:3A:EE:FA:11:D4:65:C7:77:6C:6E:99:2B:8B:98:1B:1E:DB
   ```

   If the wizard shows something different, **trust the wizard** — your APK was
   signed with a different key, and this value will not work.

   To check a keystore yourself: `keytool -list -v -keystore app/debug.keystore -alias androiddebugkey -storepass android | grep SHA1`
5. **Create**. No client id needs to be pasted anywhere — Android OAuth clients
   are matched by package name + signature automatically.

## 5. Use it

1. Install the **cloud** APK from the Releases page.
2. App → ⚙ Settings → **Open setup wizard** → *Connect Google account*.
3. Sign in with the test-user account and accept both scopes — `drive.file`
   (the app sees only files it created, never your whole Drive) and
   `photoslibrary.appendonly` (upload-only; it can add to your Photos library
   but can never read or delete from it).
4. Tap **Verify connection**. This makes a real Drive API call and checks the
   Photos scope was granted, then reports exactly what responded — so you know
   it works before trusting it with anything.
5. Tap **Back up now** for Drive, or swipe a card up to archive it to Google
   Photos. Drive files land in a folder named **SwipeDelete Zero Backup**.

## Troubleshooting

The wizard decodes errors for you, but for reference:

| Error | Cause | Fix |
|---|---|---|
| `code 10` DEVELOPER_ERROR | No OAuth client matches this app's package **and** signing fingerprint | Wizard step 3 — copy both values from the app, not from this doc |
| `code 5` INVALID_ACCOUNT | Account isn't on the test-user list | Wizard step 4 |
| `code 17` API_NOT_CONNECTED | Photos or Drive API not enabled | Wizard step 2 |
| `code 12501` | You dismissed the account picker | Just try again |

## Privacy notes

- Only the `cloud` flavor declares `android.permission.INTERNET`. The `fdroid`
  APK remains architecturally incapable of network access.
- Two scopes are requested: `drive.file` (non-sensitive — the app sees and
  writes **only files it created itself**) and `photoslibrary.appendonly`
  (upload-only — the app can add to your Photos library but can never read,
  list or delete anything in it; this also means it survives Google's
  March 2025 Photos API scope removals).
- `photoslibrary.appendonly` counts as a sensitive scope: in *Testing* mode it
  works for your test users without review; publishing to strangers would
  require Google's OAuth verification.
- Drive backup runs only when you tap the button; Photos uploads run only for
  cards you deliberately swipe up, as a visible foreground task. No telemetry.
