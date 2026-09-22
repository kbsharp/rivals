#!/usr/bin/env bash
# Runs the instrumented tests (app/src/androidTest) against the local Firebase Auth and
# Firestore emulators, on the Android emulator. The Firebase emulators load firestore.rules,
# start, run the tests, and shut down again. Nothing touches the real project.
#
# Usage: scripts/emulator-tests.sh [extra gradle args]
# Set ANDROID_SERIAL to pick a device; it must be an Android emulator (the tests reach the
# host at 10.0.2.2), so it defaults to emulator-5554 rather than a plugged-in phone.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(mise where java@temurin-21 2>/dev/null || true)"
    export JAVA_HOME
fi
[[ -n "$JAVA_HOME" ]] && export PATH="$JAVA_HOME/bin:$PATH"

export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
firebase emulators:exec --only auth,firestore "./gradlew connectedDebugAndroidTest $*"
