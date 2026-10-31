#!/usr/bin/env bash
# Installs the Android SDK pieces the build needs into $1, from Google's
# command-line tools, checked against the SHA-1 Google publishes in
# https://dl.google.com/android/repository/repository2-3.xml. Used by CI (the
# usual setup action isn't from a verified creator, so the org doesn't allow
# it) and by cloud sessions. Linux only; on a Mac, Android Studio has the SDK.
set -euo pipefail
sdk="${1:?usage: android-sdk.sh <sdk dir>}"
# 19.0, the last with the classic sdkmanager: from 23.0 it hands over to the Android CLI, whose
# unzip rejects platforms;android-36 ("Illegal entry path escaping destination directory").
TOOLS=commandlinetools-linux-13114758_latest.zip
TOOLS_SHA1=5fdcc763663eefb86a5b8879697aa6088b041e70
PACKAGES=("platforms;android-37.0" "platforms;android-36" "build-tools;36.0.0" "platform-tools")

mkdir -p "$sdk/cmdline-tools"
# The runner pools share one SDK: one install at a time.
exec 9> "$sdk/.install-lock"
flock 9
# The runners keep the SDK between jobs: replace tools that aren't the pinned ones.
if [ "$(cat "$sdk/cmdline-tools/latest/.pinned" 2>/dev/null)" != "$TOOLS" ]; then
  rm -rf "$sdk/cmdline-tools/latest"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS"
  echo "$TOOLS_SHA1  $tmp/tools.zip" | sha1sum -c -
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mv "$tmp/cmdline-tools" "$sdk/cmdline-tools/latest"
  echo "$TOOLS" > "$sdk/cmdline-tools/latest/.pinned"
  rm -rf "$tmp"
fi
# Newer tools no longer ask and stop reading at once; fed this way, yes being cut off fails nothing.
"$sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk" --licenses > /dev/null < <(yes)
"$sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$sdk" "${PACKAGES[@]}"
