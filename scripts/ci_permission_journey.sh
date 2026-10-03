#!/usr/bin/env bash
set -euo pipefail
# Run only on an isolated synthetic AVD. Revoke kills the target UID, so every
# denial/regrant phase starts a fresh instrumentation process. Never clear app data.
package=com.swipedelete.zero.debug
runner=$package.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p smoke
run_phase() {
  local phase=$1 method=$2
  timeout --kill-after=5s 90 adb shell am instrument -w \
    -e permissionJourney "$phase" \
    -e class "com.swipedelete.zero.FullAppReviewJourneyTest#$method" "$runner" \
    | tee "smoke/permission-$phase-$cycle.txt"
  grep -Eq 'OK \(1 test\)' "smoke/permission-$phase-$cycle.txt"
  ! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "smoke/permission-$phase-$cycle.txt"
}
for cycle in 1 2; do
  adb shell am force-stop "$package"
  # CI API35: revoke both blanket and partial photo access. Permission was
  # granted by the full journey; no uninstall/reset wipes persisted decisions.
  adb shell pm revoke "$package" android.permission.READ_MEDIA_IMAGES
  adb shell pm revoke "$package" android.permission.READ_MEDIA_VISUAL_USER_SELECTED
  run_phase denied deniedPermissionShowsPhotoOnboarding
  run_phase granted afterGrantShowsLibrary
done
adb pull "/sdcard/Android/data/$package/files/journey-evidence" smoke/journey-evidence
