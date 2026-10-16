#!/usr/bin/env bash
# Target .pc files only. Conda's pkg-config wrapper adds the host prefix even
# when PKG_CONFIG_LIBDIR is set; keep Conda on PATH for native generators instead.
set -euo pipefail
sysroot=${GOW3_PKG_CONFIG_SYSROOT:-${PKG_CONFIG_SYSROOT_DIR:-}}
[[ -n $sysroot && -d $sysroot ]] || {
    echo 'target-pkg-config: an existing target sysroot is required' >&2
    exit 1
}
pkg_config=
for candidate in "${GOW3_SYSTEM_PKG_CONFIG:-}" /usr/bin/pkg-config /opt/homebrew/bin/pkg-config /usr/local/bin/pkg-config; do
    if [[ -x $candidate ]]; then pkg_config=$candidate; break; fi
done
[[ -n $pkg_config ]] || { echo 'Install system pkg-config (outside Conda)' >&2; exit 1; }
export PKG_CONFIG_SYSROOT_DIR=$sysroot
export PKG_CONFIG_LIBDIR="$sysroot/usr/lib/aarch64-linux-gnu/pkgconfig:$sysroot/usr/share/pkgconfig"
export PKG_CONFIG_PATH=
unset PKG_CONFIG_SYSTEM_INCLUDE_PATH PKG_CONFIG_SYSTEM_LIBRARY_PATH
unset PKG_CONFIG_ALLOW_SYSTEM_CFLAGS PKG_CONFIG_ALLOW_SYSTEM_LIBS
export PKG_CONFIG_DONT_DEFINE_PREFIX=1 PKG_CONFIG_FDO_SYSROOT_RULES=1
args=()
if "$pkg_config" --dont-define-prefix --version >/dev/null 2>&1; then
    args+=(--dont-define-prefix)
fi
exec "$pkg_config" "${args[@]}" "$@"
