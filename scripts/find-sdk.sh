#!/bin/sh
cd "$(dirname "$0")/.."
for sdk in "$ANDROID_HOME" "$ANDROID_SDK_ROOT" "$HOME/Android/Sdk" /opt/android-sdk; do
    if [ -n "$sdk" ] && [ -d "$sdk/platforms" ]; then
        printf 'sdk.dir=%s\n' "$sdk" > local.properties
        echo "Using the Android SDK in $sdk"
        exit 0
    fi
done
echo "No Android SDK found. Install it with Android Studio, or set ANDROID_HOME, then run this again." >&2
exit 1
