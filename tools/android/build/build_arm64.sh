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

# ---- progress helpers -------------------------------------------------------
# Banners/elapsed time print on every run; the rolling "[N/M]" line only shows on a TTY
# (no-op when output is piped, so CI logs stay clean).
step=0
total=4
_t0=0

begin() { step=$((step + 1)); _t0=$(date +%s); printf '\n==> [%d/%d] %s\n' "$step" "$total" "$*"; }

end_stage() {
    local sec=$(( $(date +%s) - _t0 ))
    printf '    %s (%dm%02ds)\n' "$*" "$((sec / 60))" "$((sec % 60))"
}

# Print a live status line on a TTY only.
live() { [[ -t 1 ]] && printf '\r    %s\033[K' "$*"; }
live_end() { [[ -t 1 ]] && printf '\r\033[K\n'; }

# Run ninja and return its exit status. On a TTY, run it in the background and poll the
# full log (kept in $3 for diagnostics) for the latest "[N/M] ..." line, shown as a rolling
# progress line. On non-TTY, run it in the foreground exactly as before.
build_ninja() {
    local dir=$1 target=$2 log=$3 pid line last="" status=0
    if [[ -t 1 ]]; then
        ninja -C "$dir" "$target" > "$log" 2>&1 &
        pid=$!
        trap 'kill "$pid" 2>/dev/null || true' INT TERM
        while kill -0 "$pid" 2>/dev/null; do
            line=$(grep -E '^\[[0-9]+/[0-9]+\]' "$log" 2>/dev/null | tail -1 || true)
            if [[ -n $line && $line != "$last" ]]; then
                live "${line:0:100}"
                last=$line
            fi
            sleep 2
        done
        wait "$pid" || status=$?
        trap - INT TERM
        if [[ -n $last ]]; then live_end; fi
        return "$status"
    fi
    ninja -C "$dir" "$target" > "$log" 2>&1 || status=$?
    return "$status"
}

# This port's changes to FSR-Vulkan (gpu/patches/fsr-vulkan), applied once, as build.sh does.
for patch in gpu/patches/fsr-vulkan/*.patch; do
    if ! git -C gpu/third_party/fsr-vulkan apply --reverse --check "$root/$patch" 2>/dev/null; then
        git -C gpu/third_party/fsr-vulkan apply "$root/$patch"
    fi
done

# ---- 1/4 FEXCore + libgow3cpu.so (the guest CPU) ----------------------------
begin "FEXCore + libgow3cpu.so"
if [[ ! -f $out/fex/build.ninja ]]; then
    echo "    cmake: $fex_source"
    cmake -S "$fex_source" -B "$out/fex" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$toolchain" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_PROJECT_FEX_INCLUDE="$root/cpu/fex_project.cmake" \
        -DBUILD_TESTING=OFF -DBUILD_THUNKS=OFF -DBUILD_FEXCONFIG=OFF -DBUILD_STEAM_SUPPORT=OFF \
        -DBUILD_FEX_LINUX_TESTS=OFF -DENABLE_ZYDIS=OFF -DTUNE_ARCH=armv8.2-a -DTUNE_CPU=generic >/dev/null
fi
if ! build_ninja "$out/fex" gow3cpu "$out/fex-build.log"; then
    echo "    FEX build failed (full log: $out/fex-build.log)" >&2
    tail -40 "$out/fex-build.log" >&2
    exit 1
fi
end_stage "libgow3cpu.so"

# ---- 2/4 libgow3gpu.so (shadPS4 video core + drivers) -----------------------
begin "libgow3gpu.so (shadPS4 video core)"
if [[ ! -f $out/gpu/build.ninja ]]; then
    echo "    cmake: gpu/"
    cmake -S gpu -B "$out/gpu" -G Ninja -DCMAKE_TOOLCHAIN_FILE="$toolchain" \
        -DCMAKE_BUILD_TYPE=RelWithDebInfo -DGOW3_PGO=off -DGOW3_LTO=OFF \
        -DGOW3_ARM64_HEADER_PREFIX="$headers" \
        -Dtsl-robin-map_DIR="$headers/share/cmake/tsl-robin-map" \
        -DVulkanMemoryAllocator_DIR="$headers/share/cmake/VulkanMemoryAllocator" \
        -DBoost_DIR="$boost_dir" \
        -DZydis_DIR="$sysroot/usr/lib/aarch64-linux-gnu/cmake/zydis" >/dev/null
fi
if ! build_ninja "$out/gpu" gow3gpu "$out/gpu-build.log"; then
    echo "    GPU build failed (full log: $out/gpu-build.log)" >&2
    grep -v '^\[' "$out/gpu-build.log" | tail -40 >&2
    exit 1
fi
end_stage "libgow3gpu.so"

# ---- 3/4 gow3-probe (the loader) --------------------------------------------
cc=(clang --target=aarch64-linux-gnu --sysroot="$sysroot" -fuse-ld=lld)
export PKG_CONFIG_SYSROOT_DIR=$sysroot
export PKG_CONFIG_LIBDIR=$sysroot/usr/lib/aarch64-linux-gnu/pkgconfig:$sysroot/usr/share/pkgconfig
read -r -a includes <<< "$(pkg-config --cflags vulkan sdl3 libavcodec libavutil libswscale)"
read -r -a libraries <<< "$(pkg-config --libs vulkan sdl3 libavcodec libavutil libswscale)"

begin "LibAtrac9 (third-party decoders)"
mkdir -p "$out/atrac9"
atrac9_sources=(third_party/LibAtrac9/C/src/*.c)
atrac9_total=${#atrac9_sources[@]}
n=0
for source in "${atrac9_sources[@]}"; do
    n=$((n + 1))
    live "atrac9 $n/$atrac9_total  $(basename "$source")"
    "${cc[@]}" -std=c99 -O2 -w -c "$source" -o "$out/atrac9/$(basename "${source%.c}").o"
done
live_end
archiver=${GOW3_AR:-$(command -v llvm-ar || true)}
[[ -n $archiver ]] || { echo "No LLVM archiver (set GOW3_AR)" >&2; exit 1; }
"$archiver" rcs "$out/libatrac9.a" "$out"/atrac9/*.o
end_stage "libatrac9.a"

# ---- 4/4 gow3-probe (the loader) --------------------------------------------
begin "gow3-probe (loader)"
# src/runtime*.c includes runtime_cpu.c (a stub on ARM64: libgow3cpu.so implements guest_cpu.h).
"${cc[@]}" -std=c11 -O2 -g -Wall -Wextra -Werror -pthread -no-pie "${includes[@]}" -I. -Isrc \
    src/probe.c src/runtime*.c src/vulkan_smoke.c "$out/libatrac9.a" -lm \
    -L"$out/gpu" -lgow3gpu -L"$out/fex" -lgow3cpu -Wl,-rpath,'$ORIGIN/../lib' \
    -Wl,--dynamic-linker="$rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1" -rdynamic \
    "${libraries[@]}" -o "$out/gow3-probe"
end_stage "gow3-probe"

printf '\nAll ARM64 artifacts built:\n'
ls -lh "$out/fex/libgow3cpu.so" "$out/gpu/libgow3gpu.so" "$out/gow3-probe"
