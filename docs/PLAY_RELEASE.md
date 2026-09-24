# Google Play release preparation

The Play variant is the offline edition. Its package is `com.swipedelete.zero`, versionCode 8, target API 36. It has no INTERNET or MANAGE_EXTERNAL_STORAGE permission. Do not upload the debug APK from the GitHub Releases page.

## Build and sign

Create and securely retain a **new private upload key**. Never use the committed `debug.keystore` and never commit an upload key or its passwords. Set these environment variables on the build machine:

- `SDZ_UPLOAD_KEYSTORE`: absolute path to the upload keystore
- `SDZ_UPLOAD_STORE_PASSWORD`
- `SDZ_UPLOAD_KEY_ALIAS`
- `SDZ_UPLOAD_KEY_PASSWORD`

Run `./gradlew :app:testPlayDebugUnitTest :app:bundlePlayRelease`. Upload `app/build/outputs/bundle/playRelease/app-play-release.aab` to the Play Console internal testing track first. Enroll in Play App Signing and keep the upload key safe. Advance the track only after testing on actual Android devices, including Android 10, 13, 14, and 16. For subsequent releases increment `versionCode`.

The `Play release bundle` GitHub workflow builds the signed bundle only when the four `SDZ_UPLOAD_*` secrets are set. Its artifact is private to Actions, rather than a public GitHub Release.

## Console checklist

- Create the app using the exact package ID above and upload the signed AAB.
- Add [privacy policy](PRIVACY_POLICY.md) as a public URL: `https://github.com/grloper/SwipeDelete-Zero/blob/main/docs/PRIVACY_POLICY.md` after this change reaches main. Match the Data safety answers to the **exact uploaded bundle** and any SDK behavior.
- Complete app access, content rating, target audience, ads and permission declarations honestly. The Play variant has no all-files permission or account.
- Provide a launcher icon, feature graphic and real screenshots captured from the installed Play build. Do not substitute the design mockups in `docs/` for real app screenshots.
- If this is a personal Play developer account subject to the closed test requirement, complete the required opt-in testing period before applying for production access.
- Check the pre-launch report and perform a real media-access, swipe, staged preview, restore, Android Trash, permanent delete, and permission-denial pass on devices. Confirm the space counter only advances on permanent deletion.

## Draft store copy

**Title:** SwipeDelete Zero

**Short description:** Review photos and videos, clear clutter, and keep everything offline.

**Description:** Make space in your photo library one decision at a time. Review large videos, screenshots, possible duplicates and blurry shots. Swipe to keep or stage an item, preview what you staged, and confirm deletion through Android. Your library stays on your device. SwipeDelete Zero's Google Play edition has no ads, account, analytics or internet permission. Some duplicate and blur suggestions require an on-device scan. Available files depend on the media access you grant.

## Known release checks

- Android 10's OS Trash action is not supported by the platform's batched trash API. Verify behavior and copy on API 29 before claiming the trash feature for that version.
- Non-media file cleanup requires additional SAF selection; verify this path before advertising APK/ZIP cleanup in the Play listing.
- A working Play Console account and private upload key are required to publish. This repository alone cannot grant either.
