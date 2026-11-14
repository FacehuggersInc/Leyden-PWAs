#!/bin/sh
cd "$(dirname "$0")/.."
version=${1:-9.6.1}
scratch=$(mktemp -d)
(cd "$scratch" && touch settings.gradle.kts && gradle wrapper --gradle-version "$version" --quiet) &&
    cp -r "$scratch/gradlew" "$scratch/gradlew.bat" "$scratch/gradle" .
status=$?
rm -rf "$scratch"
exit $status
