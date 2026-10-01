# SwipeDelete Zero / SwipeRise

An Android photo-review application with swipe-based keep/stage/undo flows. F-Droid and Play variants have different capabilities and network policies. The SwipeRise backup/restore work is on a dependent draft branch; it must not be mistaken for fully verified main or a published store product.

## Recorded review-branch evidence

[Draft PR16](https://github.com/grloper/SwipeDelete-Zero/pull/16) documents head `ca1f45736752b15abf50565b8ad84b12e18862a7`, depending on draft PRs15 and14. Its [workflow36310543845](https://github.com/grloper/SwipeDelete-Zero/actions/runs/36310543845) recorded 304 passing tests and five flavor skips, both debug variants, and an unsigned Play bundle. The portfolio audit downloaded and inspected the raw test/archive artifacts before expiration; it did not independently rerun a physical device or live account.

The archived emulator evidence exercised three positive-size synthetic MediaStore images, keep/stage/undo, restart persistence and disabled Play cleanup controls. The recorded APK SHA-256 is `9768cb89671f8949006dc37e8ae5a0c72a019a160aed237338a83b1ced98b3eb`. That evidence belongs to this exact reviewed APK, not every branch or device.

## Implemented direction and boundaries

F-Droid is the offline variant and retains its separate manual deletion behavior. Play backup experiments include Google Drive original-byte verification and Google Photos app-created-item confirmation. A Photos confirmation does not prove recoverable original bytes. Play cleanup remains centrally locked in PR16; backup-to-deletion eligibility is not implemented or validated. iCloud integration is not implemented.

This is a useful photo triage and backup-safety engineering prototype. Do not treat staging, metadata confirmation or mocked upload responses as proof that deleting a personal original is safe.

## Local inspection

The app uses Kotlin/Compose and Android Gradle tooling. Inspect the chosen branch's SDK versions and flavor configuration in `app/build.gradle.kts`; then use the included Gradle wrapper with a dedicated JDK/SDK. Tests and artifacts must be attributed to the branch actually checked out. Building an APK does not establish physical-device permissions, store signing or cloud authorization.

No production account should be used to infer unverified behavior from these fixtures. Live Google OAuth, uploads, clean-install restore, account switching, expiry/quota/network interruption, physical-device media access, signing and Play submission remain unresolved. The dependent safety PR stays draft and unmerged until its stated gates are met. No full backup-safety, universal privacy or production-readiness claim is established.
