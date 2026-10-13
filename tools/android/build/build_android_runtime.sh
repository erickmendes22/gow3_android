#!/usr/bin/env bash
# Build the complete, game-free ARM64 runtime input chain for the Gow3 Android APK.
set -euo pipefail
cd -- "$(dirname -- "$0")/../../.."
root=$PWD
sysroot=${GOW3_ARM64_SYSROOT:-$root/.local-deps/android/arm64-sysroot}
rootfs=${GOW3_RUNTIME_ROOTFS:-$root/.local-deps/android/migration/x/rootfs}
export GOW3_RUNTIME_ROOTFS=$rootfs
device_libs=${GOW3_DEVICE_LIBS:-$root/out/arm64/device-libs.txt}

die() { echo "build_android_runtime: $*" >&2; exit 1; }
[[ -d $sysroot ]] || die "ARM64 sysroot missing: $sysroot"
[[ -d $rootfs ]] || die "fexdroid migration rootfs missing: $rootfs"
command -v clang >/dev/null || die 'clang is required'
if [[ -z ${GOW3_FEX_SOURCE:-} && -f $root/../Bloodborne-Android/third_party/FEX/CMakeLists.txt ]]; then
    export GOW3_FEX_SOURCE=$root/../Bloodborne-Android/third_party/FEX
fi

bash tools/android/build/build_arm64.sh
mkdir -p "$(dirname -- "$device_libs")"
find "$rootfs/usr/lib/aarch64-linux-gnu" -type f -print |
    sed 's|.*/||' | sort -u > "$device_libs"
clang --target=aarch64-linux-gnu --sysroot="$sysroot" -O2 -fuse-ld=lld \
    tools/android/build/kgsl_chip_id.c -o out/arm64/kgsl-chip-id
python3 tools/android/build_arm64_bundle.py \
    --device-libs "$device_libs" --sysroot "$sysroot"

bash tools/android/build/build_turnip.sh main
bash tools/android/build/build_turnip.sh gen8
bash tools/android/build/build_lsfg.sh
python3 tools/android/runtime/build_release_runtime.py
echo "Android runtime ready: $root/out/release/runtime.tar.gz"
