#!/usr/bin/env bash
# Native ARM64 build for the device's Linux userland (Debian trixie arm64), cross-compiled with
# Clang: the runtime and the GPU library run natively, the game's x86-64 code runs in FEXCore
# (libgow3cpu.so). The Android counterpart of build.sh's Linux branch.
#
# Inputs (see docs/ANDROID_PORT.md):
#   .local-deps/android/arm64-sysroot   Debian trixie arm64 sysroot (tools/android/fetch_arm64_sysroot.py)
#   .local-deps/root/usr                host header-only deps: Boost, tsl-robin-map, VMA, glslang
#   GOW3_FEX_SOURCE                     FEXCore source; defaults to third_party/FEX
#   gpu/third_party/{miniz,magic_enum,imgui,xbyak,fsr-vulkan,sirit}  submodules/vendored
set -euo pipefail
cd -- "$(dirname -- "$0")/../../.."
root=$PWD
out=$root/out/arm64
sysroot=${GOW3_ARM64_SYSROOT:-$root/.local-deps/android/arm64-sysroot}
headers=${GOW3_ARM64_HEADERS:-$root/.local-deps/root/usr}
fex_source=${GOW3_FEX_SOURCE:-$root/third_party/FEX}
boost_dir=${GOW3_BOOST_DIR:-$headers/lib/cmake/Boost-1.87.0}
toolchain=$root/tools/android/build/aarch64-toolchain.cmake
# The in-rootfs interpreter is baked into the probe at link time (Android has no /lib).
rootfs=${GOW3_ROOTFS:-/data/user/0/com.gow3.android/files/rootfs}
export PATH="$headers/bin:$PATH"   # glslangValidator for gpu/host_shaders

[[ -f $sysroot/usr/lib/aarch64-linux-gnu/libc.so.6 ]] || { echo "No sysroot: $sysroot" >&2; exit 1; }
[[ -d $headers/share/cmake/tsl-robin-map ]] || { echo "No host deps in $headers (Boost/robin-map/VMA/glslang)" >&2; exit 1; }
[[ -f $fex_source/CMakeLists.txt ]] || {
    echo "No FEX source: $fex_source (set GOW3_FEX_SOURCE or initialize third_party/FEX)" >&2
    exit 1
}
mkdir -p "$out"

# This port's changes to FSR-Vulkan (gpu/patches/fsr-vulkan), applied once, as build.sh does.
for patch in gpu/patches/fsr-vulkan/*.patch; do
    if ! git -C gpu/third_party/fsr-vulkan apply --reverse --check "$root/$patch" 2>/dev/null; then
        git -C gpu/third_party/fsr-vulkan apply "$root/$patch"
    fi
done

# ---- FEXCore + libgow3cpu.so (the guest CPU) -------------------------------
if [[ ! -f $out/fex/build.ninja ]]; then
    cmake -S "$fex_source" -B "$out/fex" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$toolchain" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_PROJECT_FEX_INCLUDE="$root/cpu/fex_project.cmake" \
        -DBUILD_TESTING=OFF -DBUILD_THUNKS=OFF -DBUILD_FEXCONFIG=OFF -DBUILD_STEAM_SUPPORT=OFF \
        -DBUILD_FEX_LINUX_TESTS=OFF -DENABLE_ZYDIS=OFF -DTUNE_ARCH=armv8.2-a -DTUNE_CPU=generic >/dev/null
fi
ninja -C "$out/fex" gow3cpu > "$out/fex-build.log" 2>&1 || { tail -40 "$out/fex-build.log" >&2; exit 1; }

# ---- libgow3gpu.so (shadPS4 video core + drivers) --------------------------
if [[ ! -f $out/gpu/build.ninja ]]; then
    cmake -S gpu -B "$out/gpu" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$toolchain" \
        -DCMAKE_BUILD_TYPE=RelWithDebInfo -DGOW3_PGO=off -DGOW3_LTO=OFF \
        -DGOW3_ARM64_HEADER_PREFIX="$headers" \
        -Dtsl-robin-map_DIR="$headers/share/cmake/tsl-robin-map" \
        -DVulkanMemoryAllocator_DIR="$headers/share/cmake/VulkanMemoryAllocator" \
        -DBoost_DIR="$boost_dir" \
        -DZydis_DIR="$sysroot/usr/lib/aarch64-linux-gnu/cmake/zydis" >/dev/null
fi
ninja -C "$out/gpu" gow3gpu > "$out/gpu-build.log" 2>&1 || { grep -v '^\[' "$out/gpu-build.log" | tail -40 >&2; exit 1; }

# ---- gow3-probe (the loader) ----------------------------------------------
cc=(clang --target=aarch64-linux-gnu --sysroot="$sysroot" -fuse-ld=lld)
export PKG_CONFIG_SYSROOT_DIR=$sysroot
export PKG_CONFIG_LIBDIR=$sysroot/usr/lib/aarch64-linux-gnu/pkgconfig:$sysroot/usr/share/pkgconfig
read -r -a includes <<< "$(pkg-config --cflags vulkan sdl3 libavcodec libavutil libswscale)"
read -r -a libraries <<< "$(pkg-config --libs vulkan sdl3 libavcodec libavutil libswscale)"
mkdir -p "$out/atrac9"
for source in third_party/LibAtrac9/C/src/*.c; do
    "${cc[@]}" -std=c99 -O2 -w -c "$source" -o "$out/atrac9/$(basename "${source%.c}").o"
done
archiver=${GOW3_AR:-$(command -v llvm-ar || true)}
[[ -n $archiver ]] || { echo "No LLVM archiver (set GOW3_AR)" >&2; exit 1; }
"$archiver" rcs "$out/libatrac9.a" "$out"/atrac9/*.o
# src/runtime*.c includes runtime_cpu.c (a stub on ARM64: libgow3cpu.so implements guest_cpu.h).
"${cc[@]}" -std=c11 -O2 -g -Wall -Wextra -Werror -pthread -no-pie "${includes[@]}" -I. -Isrc \
    src/probe.c src/runtime*.c src/vulkan_smoke.c "$out/libatrac9.a" -lm \
    -L"$out/gpu" -lgow3gpu -L"$out/fex" -lgow3cpu -Wl,-rpath,'$ORIGIN/../lib' \
    -Wl,--dynamic-linker="$rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1" -rdynamic \
    "${libraries[@]}" -o "$out/gow3-probe"
echo "Built $out/gow3-probe (aarch64)"
