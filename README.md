# PWA App

A custom android app to make PWA's easier

An Android app in Kotlin.

- `sh scripts/run.sh` builds it, installs it on the connected device and opens it.
- `sh scripts/logcat.sh` shows its log.
- `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew testDebugUnitTest` runs the tests.

The device needs USB or wireless debugging turned on in its developer options.
`local.properties` says where the Android SDK is; `sh scripts/find-sdk.sh` writes it.
`sh scripts/make-wrapper.sh` remakes `./gradlew` if it is missing, with Gradle installed.
