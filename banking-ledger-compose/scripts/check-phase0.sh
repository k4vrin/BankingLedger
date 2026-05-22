#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
./gradlew \
  :shared:compileKotlinMetadata \
  :shared:jvmTest \
  :desktopApp:compileKotlin \
  :androidApp:lintDebug \
  :androidApp:assembleDebug \
  "$@"
