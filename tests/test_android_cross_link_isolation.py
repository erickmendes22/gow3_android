"""The Android ARM64 cross toolchain must not link host micromamba libraries."""
import subprocess
import os
import shutil
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "tools/android/build"
WF = (ROOT / ".github/workflows/android-dev-apk.yml").read_text()

class CrossLinkIsolationTests(unittest.TestCase):
    def test_target_pkg_config_ignores_host_packages_and_does_not_double_prefix(self):
        pkg_config = os.environ.get('GOW3_SYSTEM_PKG_CONFIG') or shutil.which('pkg-config')
        if not pkg_config:
            self.skipTest('pkg-config is not installed')
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            target = base / 'sysroot'
            pcs = target / 'usr/lib/aarch64-linux-gnu/pkgconfig'
            pcs.mkdir(parents=True)
            host = base / 'host'
            host.mkdir()
            def pc(name):
                return (f'prefix=/usr\nlibdir=${{prefix}}/lib/aarch64-linux-gnu\n'
                        f'includedir=${{prefix}}/include\nName: {name}\n'
                        f'Description: isolation fixture\nVersion: 1.0\n'
                        f'Libs: -L${{libdir}} -l{name}\nCflags: -I${{includedir}}/{name}\n')
            (pcs / 'target-only.pc').write_text(pc('target-only'))
            (host / 'SPIRV-Tools.pc').write_text(pc('SPIRV-Tools'))
            # Simulate the Conda PATH wrapper reintroducing host search paths.
            fake_bin = base / 'bin'
            fake_bin.mkdir()
            fake = fake_bin / 'pkg-config'
            fake.write_text('#!/bin/sh\necho host-wrapper-used >&2\nexit 99\n')
            fake.chmod(0o755)
            env = dict(os.environ, GOW3_SYSTEM_PKG_CONFIG=str(Path(pkg_config).resolve()),
                       GOW3_PKG_CONFIG_SYSROOT=str(target), PKG_CONFIG_PATH=str(host),
                       PKG_CONFIG_LIBDIR=str(host), PKG_CONFIG_SYSROOT_DIR='/wrong-root',
                       PATH=f'{fake_bin}:{os.environ["PATH"]}')
            wrapper = BUILD / 'target-pkg-config.sh'
            result = subprocess.run([str(wrapper), '--cflags', '--libs', 'target-only'],
                                    env=env, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f'-I{target}/usr/include/target-only', result.stdout)
            self.assertIn(f'-L{target}/usr/lib/aarch64-linux-gnu', result.stdout)
            self.assertNotIn(f'{target}{target}', result.stdout)
            result = subprocess.run([str(wrapper), '--exists', 'SPIRV-Tools'], env=env)
            self.assertEqual(result.returncode, 1)

    def test_all_graphics_configurations_are_checked_before_runtime(self):
        self.assertLess(WF.index('Configure both Turnip variants and LSFG before native builds'),
                        WF.index('Build ARM64 native runtime'))

    def test_env_sanitizer_clears_host_flags_but_preserves_host_tool_path(self):
        script = r"""
export CFLAGS=-march=haswell CXXFLAGS=-march=haswell CPPFLAGS=-DHOST
export LDFLAGS='-L/micromamba/x86_64/lib -Wl,-rpath,/micromamba/x86_64/lib'
export LIBRARY_PATH='/micromamba/x86_64/lib'
export CMAKE_PREFIX_PATH='/micromamba/x86_64'
export PKG_CONFIG_PATH='/micromamba/x86_64/pkgconfig'
export CC=x86_64-conda-linux-gnu-cc CXX=x86_64-conda-linux-gnu-c++
source tools/android/build/clean_cross_env.sh
for name in CFLAGS CXXFLAGS CPPFLAGS LDFLAGS LIBRARY_PATH CMAKE_PREFIX_PATH PKG_CONFIG_PATH CC CXX; do
    [[ ! ${!name+x} ]] || exit 3
done
[[ -n "$PATH" ]] || exit 4
printf ok
"""
        res = subprocess.run(["bash", "-euc", script], cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertEqual(res.stdout, "ok")

    def test_both_cross_builders_source_same_sanitizer(self):
        for name in ("build_turnip.sh", "build_lsfg.sh"):
            s = (BUILD / name).read_text()
            self.assertIn("source tools/android/build/clean_cross_env.sh", s)

    def test_real_target_link_is_required_before_expensive_native_build(self):
        self.assertIn("Preflight ARM64 linker and libatomic before native builds", WF)
        self.assertLess(WF.index("Preflight ARM64 linker and libatomic before native builds"),
                        WF.index("Build ARM64 native runtime"))
        src = (BUILD / "preflight_turnip_link.sh").read_text()
        for expected in ("--target=aarch64-linux-gnu", "-latomic",
                         "llvm-readelf -h", "source tools/android/build/clean_cross_env.sh"):
            self.assertIn(expected, src)
        self.assertIn("test_android_*.py", WF)

if __name__ == "__main__":
    unittest.main()
