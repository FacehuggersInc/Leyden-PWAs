#!/bin/sh
set -e
cd "$(dirname "$0")/.."
./gradlew installDebug
id=$(sed -n 's/^ *applicationId = "\(.*\)"/\1/p' app/build.gradle.kts | head -n 1)
adb shell monkey -p "$id" -c android.intent.category.LAUNCHER 1 > /dev/null
