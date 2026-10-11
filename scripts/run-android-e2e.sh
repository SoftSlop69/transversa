#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -uo pipefail

recording_dir=app/build/recordings
device_dir=/sdcard/transversa-e2e
mkdir -p "$recording_dir"
rm -f "$recording_dir"/*
adb shell mkdir -p "$device_dir"

# Install both APKs before recording. The connected-test Gradle task performs
# installation as part of its setup, which otherwise leaves long blank lead-in
# footage while each per-test Gradle invocation starts.
app_apk=app/build/outputs/apk/debug/app-debug.apk
test_apk=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if [[ ! -f "$app_apk" || ! -f "$test_apk" ]]; then
  echo "Expected app and instrumentation APKs from the workflow build step." >&2
  exit 1
fi
echo "Installing app and instrumentation APKs before starting screen recordings."
adb install -r "$app_apk"
adb install -r "$test_apk"

tests=(
  navigationHistoryThemeDonateAndLicenseFlowsWork
  customPresetCanBeCreatedEditedAndDeleted
  selectedImageCanBeConvertedAndSharedThroughAndroidChooser
)

failed=0
for test_name in "${tests[@]}"; do
  echo "::group::Android E2E: ${test_name}"
  device_video="${device_dir}/${test_name}.mp4"
  video="${recording_dir}/${test_name}.mp4"
  temporary_video="${recording_dir}/${test_name}.faststart.mp4"
  screenshot="${recording_dir}/${test_name}-failure.png"
  log="${recording_dir}/${test_name}.log"

  adb shell rm -f "$device_video"
  adb shell screenrecord --time-limit 170 "$device_video" \
    >"${RUNNER_TEMP:-/tmp}/${test_name}-screenrecord.log" 2>&1 &
  recorder_shell_pid=$!
  sleep 2

  ./gradlew :app:connectedDebugAndroidTest --no-daemon \
    -Pandroid.testInstrumentationRunnerArguments.class="com.example.transversa.TransversaEndToEndTest#${test_name}" \
    2>&1 | tee "$log"
  test_status=${PIPESTATUS[0]}

  recorder_device_pid="$(adb shell pidof screenrecord 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "$recorder_device_pid" ]]; then
    adb shell kill -2 $recorder_device_pid >/dev/null 2>&1 || true
  fi
  wait "$recorder_shell_pid" >/dev/null 2>&1 || true

  if adb pull "$device_video" "$video" >/dev/null 2>&1; then
    if ffmpeg -hide_banner -loglevel error -y -i "$video" -c copy -movflags +faststart "$temporary_video"; then
      mv "$temporary_video" "$video"
    else
      rm -f "$temporary_video"
      echo "Could not add fast-start metadata to ${test_name} recording" >&2
    fi
  else
    failed=1
    echo "No emulator recording was produced for ${test_name}" >&2
  fi

  if (( test_status != 0 )); then
    failed=1
    adb exec-out screencap -p >"$screenshot" || rm -f "$screenshot"
    echo "${test_name} failed; retained its video and failure screenshot when available."
  else
    rm -f "$screenshot"
  fi
  adb shell rm -f "$device_video" >/dev/null 2>&1 || true
  echo "::endgroup::"
done

exit "$failed"
