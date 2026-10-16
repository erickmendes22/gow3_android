#!/usr/bin/env bash
# Native Bionic backend, adapted from ducvd89/Bloodborne-Android 856bd99b05ee088782e881b99fca7945110ea0ca.
# Only the MIT framegen backend and shader extractor are built; no Vulkan layer or player DLL.
set -Eeuo pipefail
trap 'printf "Bionic LSFG build failed at line %s: %s\n" "$LINENO" "$BASH_COMMAND" >&2' ERR
cd -- "$(dirname -- "$0")/../../.."
ndk=${GOW3_NDK:-${ANDROID_NDK_HOME:-}}
: "${ndk:?Set GOW3_NDK or ANDROID_NDK_HOME}"
api=${GOW3_BIONIC_API:-30}
llvm=$ndk/toolchains/llvm/prebuilt/linux-x86_64
root=$PWD
src=$root/.local-deps/android/lsfg-vk-android
commit=0802cd33319c101e4c41f6828153d3331d8a3258
build=$root/out/bionic/lsfg-build
out=$root/out/bionic
[[ -f $ndk/build/cmake/android.toolchain.cmake ]] || { echo "Missing Android NDK: $ndk" >&2; exit 1; }
if [[ ! -d $src/.git ]]; then
    mkdir -p "${src%/*}"
    git clone --branch release https://github.com/Ragnarok93/lsfg-vk-android.git "$src"
fi
git -C "$src" cat-file -e "$commit^{commit}" 2>/dev/null || git -C "$src" fetch origin release
git -C "$src" -c advice.detachedHead=false checkout --detach "$commit"
git -C "$src" submodule update --init thirdparty/dxbc thirdparty/pe-parse thirdparty/volk
[[ $(git -C "$src" rev-parse HEAD) == "$commit" ]]
reset_source() { git -C "$src" checkout -- .; git -C "$src" clean -fd; }
reset_source
trap reset_source EXIT
for patch in tools/android/lsfg-android-patches/*.patch; do
    git -C "$src" apply --check "$root/$patch"
    git -C "$src" apply "$root/$patch"
done
mkdir -p "$build" "$out/lib" "$out/licenses/lsfg"
cmake -S tools/android/lsfg -B "$build" -G Ninja -DLSFG_SRC="$src" \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM="android-$api" -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release -DBUILD_TESTING=OFF \
    -DCMAKE_EXPORT_COMPILE_COMMANDS=ON
cmake --build "$build" --target lsfg-bb --parallel "${GOW3_JOBS:-2}"
cp "$build/liblsfg-gow3.so" "$out/lib/liblsfg-gow3.so"
cp "$src/LICENSE.md" "$out/licenses/lsfg/lsfg-vk-android.LICENSE.md"
cp "$src/thirdparty/dxbc/LICENSE.md" "$out/licenses/lsfg/lsfg-dxbc.LICENSE"
cp "$src/thirdparty/pe-parse/LICENSE" "$out/licenses/lsfg/lsfg-pe-parse.LICENSE"
cp "$src/thirdparty/volk/LICENSE.md" "$out/licenses/lsfg/lsfg-volk.LICENSE.md"
printf 'lsfg-vk-android %s + tools/android/lsfg-android-patches\nBloodborne-Android 856bd99b05ee088782e881b99fca7945110ea0ca\n' "$commit" > "$out/licenses/lsfg/SOURCE.txt"
"$llvm/bin/llvm-nm" -D --defined-only "$out/lib/liblsfg-gow3.so" | awk '{print $3}' | sort
