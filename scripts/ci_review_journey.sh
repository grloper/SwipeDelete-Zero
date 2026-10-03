#!/usr/bin/env bash
set -euo pipefail
./gradlew :app:assemblePlayDebug :app:assemblePlayDebugAndroidTest --no-daemon --max-workers=2
adb install -r app/build/outputs/apk/play/debug/app-play-debug.apk
adb install -r app/build/outputs/apk/androidTest/play/debug/app-play-debug-androidTest.apk
mkdir -p smoke
timeout --kill-after=5s 190 adb shell screenrecord --time-limit 180 /sdcard/review-journey.mp4 > smoke/screenrecord.txt 2>&1 &
recorder_pid=$!
collect_runtime_files() {
  # This fresh CI emulator owns the only recorder. SIGINT finalizes its MP4.
  timeout --kill-after=5s 10 adb shell pkill -2 -x screenrecord >> smoke/collection.txt 2>&1 || true
  wait "$recorder_pid" >> smoke/collection.txt 2>&1 || true
  timeout --kill-after=5s 20 adb pull /sdcard/review-journey.mp4 smoke/review-journey.mp4 >> smoke/collection.txt 2>&1
}
collect_failure_diagnostics() {
  local status=$?
  trap - EXIT
  if [ "$status" -ne 0 ]; then
    # Best effort diagnostics must preserve the original failing exit code.
    collect_runtime_files || true
    timeout --kill-after=5s 20 adb pull /sdcard/Android/data/com.swipedelete.zero.debug/files smoke/runtime-files >> smoke/collection.txt 2>&1 || true
    timeout --kill-after=5s 10 adb logcat -d > smoke/logcat.txt 2>&1 || true
  fi
  exit "$status"
}
trap collect_failure_diagnostics EXIT
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

# Success requires finalized, nonempty, fully decodable footage and key screens.
collect_runtime_files
python scripts/ci_validate_visuals.py smoke
