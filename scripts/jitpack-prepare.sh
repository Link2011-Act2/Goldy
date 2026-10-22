#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_HOME:?An Android SDK is required}"
sdkmanager="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then
  sdkmanager="$(command -v sdkmanager)"
fi
chmod +x gradlew
# yes's expected SIGPIPE must not turn accepted licenses into a failed setup.
set +o pipefail
yes | "$sdkmanager" --licenses >/dev/null
set -o pipefail
"$sdkmanager" 'platforms;android-37.0' 'build-tools;36.0.0'
java -version
