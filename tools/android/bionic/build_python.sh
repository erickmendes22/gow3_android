#!/usr/bin/env bash
# CPython's supported Android cross build, for the fork's existing patch/config validators.
set -Eeuo pipefail
cd -- "$(dirname -- "$0")/../../.."
root=$PWD
unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS CPATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH LIBRARY_PATH PKG_CONFIG_PATH PKG_CONFIG_LIBDIR PKG_CONFIG_SYSROOT_DIR
ndk=${GOW3_NDK:-${ANDROID_NDK_HOME:-$root/.local-deps/android/sdk/ndk/android-ndk-r29}}
api=${GOW3_BIONIC_API:-30}
src=$root/.local-deps/android/bionic-src/python-3.13.7
build=$root/.local-deps/android/bionic-build/python-host
cross=$root/.local-deps/android/bionic-build/python-android
out=$root/out/bionic/python
j=${GOW3_JOBS:-4}
if [[ ! -f $src/configure ]]; then
    mkdir -p "$src"
    curl -fsSL --retry 3 https://www.python.org/ftp/python/3.13.7/Python-3.13.7.tar.xz -o "$src/source.tar.xz"
    tar --no-same-owner -xf "$src/source.tar.xz" -C "$src" --strip-components=1
fi
mkdir -p "$build" "$cross" "$out"
if [[ ! -f $build/Makefile ]]; then
    (cd "$build"; "$src/configure" --without-ensurepip --disable-test-modules > configure.log 2>&1)
fi
make -C "$build" -j"$j" > "$build/build.log" 2>&1
llvm=$ndk/toolchains/llvm/prebuilt/linux-x86_64
if [[ ! -f $cross/Makefile ]]; then
    (cd "$cross"
     CC="$llvm/bin/aarch64-linux-android$api-clang" \
     CXX="$llvm/bin/aarch64-linux-android$api-clang++" \
     AR="$llvm/bin/llvm-ar" RANLIB="$llvm/bin/llvm-ranlib" READELF="$llvm/bin/llvm-readelf" \
     PKG_CONFIG_LIBDIR=/nonexistent PKG_CONFIG_PATH= \
     CFLAGS=-D__BIONIC_NO_PAGE_SIZE_MACRO \
     LDFLAGS="-Wl,-rpath,'\$\$ORIGIN/../lib' -Wl,-z,max-page-size=16384" \
     "$src/configure" --host=aarch64-linux-android --build="$($build/python -c 'import sysconfig; print(sysconfig.get_config_var("BUILD_GNU_TYPE"))')" \
       --with-build-python="$build/python" --prefix="$out" --enable-shared --without-static-libpython \
       --without-ensurepip --disable-test-modules > configure.log 2>&1)
fi
make -C "$cross" -j"$j" > "$cross/build.log" 2>&1
make -C "$cross" install > "$cross/install.log" 2>&1
# Used through PYTHONHOME in the launcher; no compiled-in build path is required at runtime.
cp "$src/LICENSE" "$out/LICENSE-Python.txt"
echo "Bionic Python built: $out"
