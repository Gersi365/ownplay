#!/usr/bin/env bash
set -euo pipefail

if command -v gradle >/dev/null 2>&1; then
  gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
else
  echo "Gradle is not installed. Use the pinned GitHub Actions workflow or install Gradle 9.5.0." >&2
  exit 2
fi
