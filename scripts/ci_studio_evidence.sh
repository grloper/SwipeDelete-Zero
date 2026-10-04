#!/usr/bin/env bash
# Independent real-screen profiles on the runner-owned disposable AVD.
set -Eeuo pipefail
: "${ANDROID_HOME:?}"
ADB="$ANDROID_HOME/platform-tools/adb"
mkdir -p evidence/studio
emulator_pid=
overall_status=0
printf 'profile\tresult\n' > evidence/studio/profile-results.tsv
python3 - <<'PY'
import hashlib,json,subprocess
from pathlib import Path
apk=Path('app/build/outputs/apk/play/debug/app-play-debug.apk')
test=Path('app/build/outputs/apk/androidTest/play/debug/app-play-debug-androidTest.apk')
Path('evidence/studio/build.json').write_text(json.dumps({'head':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'tree':subprocess.check_output(['git','rev-parse','HEAD^{tree}'],text=True).strip(),'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'test_apk_sha256':hashlib.sha256(test.read_bytes()).hexdigest(),'api':35,'source':'Actual MainActivity, isolated synthetic emulator; no mockups','fixtures':json.loads(Path('app/src/androidTest/assets/studio/provenance.json').read_text()),'configurations':['360x640dp font1','411x731dp font1','320x640dp font1','360x640dp font1.3','360x640dp font2','360x640dp motion0'],'capture_order':'All still profiles independently, then recording'},indent=2))
PY
timeout 120 "$ADB" install -r app/build/outputs/apk/play/debug/app-play-debug.apk > evidence/studio/install.txt
timeout 120 "$ADB" install -r app/build/outputs/apk/androidTest/play/debug/app-play-debug-androidTest.apk > evidence/studio/test-install.txt
ensure_device() {
  local label=$1 deadline=$((SECONDS+20))
  if [ "$(timeout --kill-after=2s 3 "$ADB" get-state 2>/dev/null || true)" = device ]; then return 0; fi
  echo "$(date -u +%FT%TZ) ADB transport unavailable at $label; bounded reconnect"
  timeout --kill-after=2s 5 "$ADB" reconnect offline > "evidence/studio/$label-reconnect.txt" 2>&1 || true
  while [ "$SECONDS" -lt "$deadline" ]; do
    if [ -n "$emulator_pid" ] && ! kill -0 "$emulator_pid" 2>/dev/null; then return 1; fi
    if [ "$(timeout --kill-after=2s 3 "$ADB" get-state 2>/dev/null || true)" = device ]; then
      echo "$(date -u +%FT%TZ) ADB transport recovered at $label"
      return 0
    fi
    sleep 1
  done
  timeout --kill-after=2s 3 "$ADB" devices -l > "evidence/studio/$label-offline.txt" 2>&1 || true
  return 1
}
collect_screens() {
  # A stable parent destination merges the remote directory on every profile, without nesting copies.
  ensure_device "$1-pull" || return
  if timeout --kill-after=5s 15 "$ADB" pull /sdcard/Android/data/com.swipedelete.zero.debug/files/practice-evidence evidence/studio/ > "evidence/studio/$1-pull.txt" 2>&1; then return 0; fi
  # Retry transport/file transfer once; UI test assertions and their results are never rerun here.
  ensure_device "$1-pull-retry" || return
  timeout --kill-after=5s 15 "$ADB" pull /sdcard/Android/data/com.swipedelete.zero.debug/files/practice-evidence evidence/studio/ >> "evidence/studio/$1-pull.txt" 2>&1
}
cleanup() {
  local status=$?
  trap - EXIT
  set +e
  collect_screens final
  timeout --kill-after=5s 15 "$ADB" pull /sdcard/studio-interaction.mp4 evidence/studio/interaction.mp4 > evidence/studio/video-pull.txt 2>&1
  timeout --kill-after=5s 5 "$ADB" pull /sdcard/studio-record.log evidence/studio/recorder.log > evidence/studio/recorder-pull.txt 2>&1
  timeout --kill-after=5s 10 "$ADB" logcat -d -t 1500 > evidence/studio/logcat.txt 2>&1
  exit "$status"
}
trap cleanup EXIT
configure_device() {
  local size=$1 density=$2 font=$3 motion=$4
  ensure_device "configure-$size" || return
  timeout --kill-after=5s 15 "$ADB" shell am force-stop com.swipedelete.zero.debug || return
  timeout --kill-after=5s 15 "$ADB" shell wm size "$size" || return
  timeout --kill-after=5s 15 "$ADB" shell wm density "$density" || return
  timeout --kill-after=5s 15 "$ADB" shell settings put system font_scale "$font" || return
  timeout --kill-after=5s 15 "$ADB" shell settings put global animator_duration_scale "$motion" || return
  timeout --kill-after=5s 15 "$ADB" shell settings put global transition_animation_scale "$motion" || return
  timeout --kill-after=5s 15 "$ADB" shell settings put global window_animation_scale "$motion" || return
  sleep 2
}
capture() {
  local label=$1 size=$2 density=$3 font=$4 motion=$5
  echo "$(date -u +%FT%TZ) Starting capture $label"
  configure_device "$size" "$density" "$font" "$motion" || return
  if ! timeout --kill-after=5s 240 "$ADB" shell am instrument -w -r -e class com.swipedelete.zero.StudioVisualEvidenceTest -e visualLabel "$label" com.swipedelete.zero.debug.test/androidx.test.runner.AndroidJUnitRunner | tee "evidence/studio/$label-test.txt"; then return 1; fi
  grep -q 'OK (1 test)' "evidence/studio/$label-test.txt" || return 1
  echo "$(date -u +%FT%TZ) Passed capture $label"
}
run_profile() {
  local label=$1 result
  if capture "$@"; then result=passed; else result=failed; overall_status=1; fi
  printf '%s\t%s\n' "$label" "$result" >> evidence/studio/profile-results.tsv
  if ! collect_screens "$label"; then overall_status=1; printf '%s-evidence\tfailed\n' "$label" >> evidence/studio/profile-results.tsv; fi
}
run_profile compact-font1 720x1280 320 1.0 1
run_profile large-font1 1080x1920 420 1.0 1
run_profile narrow-font1 640x1280 320 1.0 1
run_profile compact-font13 720x1280 320 1.3 1
run_profile compact-font2 720x1280 320 2.0 1
run_profile compact-reduced 720x1280 320 1.0 0
record_interaction() {
  configure_device 720x1280 320 1.0 1 || return
  if ! timeout --kill-after=5s 120 "$ADB" shell am instrument -w -r -e class com.swipedelete.zero.StudioInteractionEvidenceTest -e studioRecording true com.swipedelete.zero.debug.test/androidx.test.runner.AndroidJUnitRunner | tee evidence/studio/interaction-test.txt; then return 1; fi
  grep -q 'OK (1 test)' evidence/studio/interaction-test.txt || return 1
}
echo "$(date -u +%FT%TZ) All still profiles attempted; starting independent recording test"
if record_interaction; then
  printf 'interaction\tpassed\n' >> evidence/studio/profile-results.tsv
else
  overall_status=1
  printf 'interaction\tfailed\n' >> evidence/studio/profile-results.tsv
fi
timeout --kill-after=5s 15 "$ADB" shell settings put system font_scale 1.0 || overall_status=1
timeout --kill-after=5s 15 "$ADB" shell settings put global animator_duration_scale 1 || overall_status=1
timeout --kill-after=5s 15 "$ADB" shell settings put global transition_animation_scale 1 || overall_status=1
timeout --kill-after=5s 15 "$ADB" shell settings put global window_animation_scale 1 || overall_status=1
timeout --kill-after=5s 15 "$ADB" shell wm size reset || overall_status=1
timeout --kill-after=5s 15 "$ADB" shell wm density reset || overall_status=1
python3 - <<'PY'
import csv,json
from pathlib import Path
rows=list(csv.DictReader(Path('evidence/studio/profile-results.tsv').open(),delimiter='\t'))
Path('evidence/studio/profile-results.json').write_text(json.dumps(rows,indent=2))
PY
exit "$overall_status"
