#!/usr/bin/env bash
# Builds libtvshields_engine.so for the Android ABIs (rules T3) into app/src/main/jniLibs.
# Usage: engine/build-android.sh [--debug]
# Needs ANDROID_NDK_HOME and cargo-ndk (see docs/resources/toolchain.md).
set -euo pipefail

cd "$(dirname "$0")"
out=../app/src/main/jniLibs

profile=(--release)
if [[ "${1:-}" == "--debug" ]]; then
    profile=()
fi

# --platform 24 matches minSdk (rules T2).
cargo ndk -t armeabi-v7a -t arm64-v8a --platform 24 -o "$out" build "${profile[@]}"

for so in "$out"/*/libtvshields_engine.so; do
    bytes=$(wc -c < "$so")
    printf '%-12s %8d bytes (%s MB)\n' "$(basename "$(dirname "$so")")" "$bytes" \
        "$(awk "BEGIN { printf \"%.2f\", $bytes / 1048576 }")"
done
