#!/bin/sh
cd "$(dirname "$0")/.."
id=$(sed -n 's/^ *applicationId = "\(.*\)"/\1/p' app/build.gradle.kts | head -n 1)
pid=$(adb shell pidof -s "$id")
if [ -z "$pid" ]; then
    adb shell monkey -p "$id" -c android.intent.category.LAUNCHER 1 > /dev/null
    sleep 1
    pid=$(adb shell pidof -s "$id")
fi
exec adb logcat --pid="$pid"
