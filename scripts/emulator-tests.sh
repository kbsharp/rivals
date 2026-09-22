#!/usr/bin/env bash
# Runs the instrumented tests (app/src/androidTest) against the local Firebase Auth and
# Firestore emulators, on an Android emulator. The Firebase emulators load firestore.rules,
# start, run the tests, and shut down again. Nothing touches the real project.
#
# If no Android emulator is running, one is booted headless from the AVD in $AVD (default
# pool36) and shut down afterwards. A phone that's plugged in is left alone: the tests reach
# the host at 10.0.2.2, which only works from an emulator.
#
# Usage: scripts/emulator-tests.sh [extra gradle args]
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(mise where java@temurin-21 2>/dev/null || true)"
    export JAVA_HOME
fi
[[ -n "$JAVA_HOME" ]] && export PATH="$JAVA_HOME/bin:$PATH"

sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
adb="$sdk/platform-tools/adb"
avd="${AVD:-pool36}"

running_emulator() { "$adb" devices | awk '/^emulator-[0-9]+\tdevice$/ { print $1; exit }'; }

serial="${ANDROID_SERIAL:-$(running_emulator)}"
started=""
if [[ -z "$serial" ]]; then
    echo "No Android emulator running; booting $avd headless..."
    "$sdk/emulator/emulator" -avd "$avd" -no-window -no-audio -no-boot-anim -no-snapshot-save \
        -gpu swiftshader_indirect >/dev/null 2>&1 &
    started=1
    for _ in $(seq 1 90); do
        serial="$(running_emulator)"
        [[ -n "$serial" && "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
        sleep 2
    done
    [[ -n "$serial" ]] || { echo "Emulator didn't boot" >&2; exit 1; }
fi
trap '[[ -n "$started" ]] && "$adb" -s "$serial" emu kill >/dev/null 2>&1 || true' EXIT

export ANDROID_SERIAL="$serial"

# Clear last run's renders on the device, so a screen that no longer exists doesn't linger in
# app/build/screenshots looking current.
"$adb" -s "$serial" shell rm -rf /sdcard/Android/data/com.kevinbevan.rivals/files/screenshots >/dev/null 2>&1 || true

status=0
# Leave the apps installed so the screenshots survive until they are pulled.
firebase emulators:exec --only auth,firestore \
    "./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true $*" || status=$?

# Screens rendered by the Screenshots test, for looking at.
rm -rf app/build/screenshots
"$adb" -s "$serial" pull /sdcard/Android/data/com.kevinbevan.rivals/files/screenshots app/build/screenshots >/dev/null 2>&1 \
    && echo "Screenshots in app/build/screenshots"
exit $status
