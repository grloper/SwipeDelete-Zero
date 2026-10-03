#!/usr/bin/env bash
set -euo pipefail
./gradlew :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-daemon --max-workers=2
adb install -r app/build/outputs/apk/play/debug/app-play-debug.apk
adb install -r app/build/outputs/apk/androidTest/play/debug/app-play-debug-androidTest.apk
mkdir -p smoke
adb shell screenrecord --time-limit 180 /sdcard/review-journey.mp4 > smoke/screenrecord.txt 2>&1 &
recorder_pid=$!
collect_evidence() {
  local status=$?
  trap - EXIT
  kill "$recorder_pid" 2>/dev/null || true
  timeout --kill-after=5s 20 adb pull /sdcard/review-journey.mp4 smoke/review-journey.mp4 || true
  timeout --kill-after=5s 20 adb pull /sdcard/Android/data/com.swipedelete.zero.debug/files smoke/runtime-files || true
  exit "$status"
}
trap collect_evidence EXIT
timeout --kill-after=5s 900 adb shell am instrument -w com.swipedelete.zero.debug.test/androidx.test.runner.AndroidJUnitRunner | tee smoke/instrumentation.txt
if ! grep -Eq 'OK \(([3-9]|[1-9][0-9]+) tests\)' smoke/instrumentation.txt || grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' smoke/instrumentation.txt; then
  echo 'Mandatory fixture and card recovery instrumentation did not pass' >&2
  exit 1
fi
bash scripts/ci_permission_journey.sh
adb shell am start -W -n com.swipedelete.zero.debug/com.swipedelete.zero.MainActivity
sleep 5
adb shell pidof com.swipedelete.zero.debug
adb exec-out screencap -p > smoke/dashboard.png
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml smoke/window.xml
