#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
cd -- "$script_dir/../../.."

if [[ ${GOW3_REBUILD_DEBUG_RUNTIME:-0} == 1 || ! -f out/debug/runtime.tar.gz ]]; then
    python3 tools/android/runtime/build_release_runtime.py --profile debug
else
    python3 tools/android/runtime/validate_runtime_archive.py out/debug/runtime.tar.gz \
        --digest out/debug/runtime.sha256 --manifest tools/android/debug/runtime-manifest.json
fi

# Stage the debug runtime payload where the Kotlin launcher's debug source set reads it
# (android-launcher/app/build.gradle.kts: out/android-debug/assets).
mkdir -p out/android-debug/assets
cp out/debug/runtime.tar.gz out/android-debug/assets/runtime.payload
cp out/debug/runtime.sha256 out/android-debug/assets/runtime.sha256

bash tools/android/apk/build_kotlin_launcher.sh
version=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' android-launcher/app/build.gradle.kts)
mkdir -p out/debug
cp android-launcher/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk \
    "out/debug/God of War III-debug-$version.apk"
apk="out/debug/God of War III-debug-$version.apk"
sdk=${GOW3_ANDROID_SDK:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
bt="$sdk/build-tools/${GOW3_BUILD_TOOLS:-35.0.0}"
"$bt/apksigner" verify "$apk"
"$bt/aapt" dump badging "$apk" | grep -F com.gow3.android >/dev/null
python3 tools/android/apk/validate_release_apk.py "$apk" --aapt "$bt/aapt" \
    --allow-debuggable --manifest tools/android/debug/runtime-manifest.json
if command -v shasum >/dev/null; then
    (cd out/debug && shasum -a 256 "$(basename "$apk")" > "$(basename "$apk").sha256")
fi
echo "Debug APK: out/debug/God of War III-debug-$version.apk"
