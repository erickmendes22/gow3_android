#!/usr/bin/env bash
set -Eeuo pipefail
trap 'printf "Bionic GPU build failed at line %s: %s\n" "$LINENO" "$BASH_COMMAND" >&2' ERR
cd -- "$(dirname -- "$0")/../../.."
ndk=${GOW3_NDK:-${ANDROID_NDK_HOME:-}}
: "${ndk:?Set GOW3_NDK or ANDROID_NDK_HOME}"
api=${GOW3_BIONIC_API:-30}
root=$PWD
prefix=$root/.local-deps/android/bionic-prefix
headers=${GOW3_BIONIC_HEADERS:-$root/.local-deps/root/usr}
out=$root/out/bionic
[[ -f $prefix/.done-ffmpeg ]] || { echo 'Run build_deps.sh first' >&2; exit 1; }
unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS CPATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH LIBRARY_PATH CMAKE_PREFIX_PATH
export PATH="$headers/bin:$PATH"
export PKG_CONFIG_LIBDIR="$prefix/lib/pkgconfig:$prefix/share/pkgconfig"
export PKG_CONFIG_PATH=$PKG_CONFIG_LIBDIR
unset PKG_CONFIG_SYSROOT_DIR
for patch in gpu/patches/fsr-vulkan/*.patch; do
    if ! git -C gpu/third_party/fsr-vulkan apply --reverse --check "$root/$patch" 2>/dev/null; then
        git -C gpu/third_party/fsr-vulkan apply "$root/$patch"
    fi
done
# Let CMake discover include/vma as well as include; discard the old forced cache path.
cmake -S gpu -B "$out/gpu" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM="android-$api" -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=RelWithDebInfo -DGOW3_PGO=off -DGOW3_LTO=OFF \
    "-DCMAKE_FIND_ROOT_PATH=$prefix;$headers" -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH \
    -DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=BOTH "-DCMAKE_PREFIX_PATH=$prefix;$headers" \
    -UVMA_INCLUDE_DIR
cmake --build "$out/gpu" --target gow3gpu --parallel "${GOW3_JOBS:-2}"
mkdir -p "$out/lib"
cp "$out/gpu/libgow3gpu.so" "$out/lib/"
