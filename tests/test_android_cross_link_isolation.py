"""The Android ARM64 cross toolchain must not link host micromamba libraries."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "tools/android/build"
WF = (ROOT / ".github/workflows/android-dev-apk.yml").read_text()

class CrossLinkIsolationTests(unittest.TestCase):
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
        self.assertIn("test_android_cross_link_isolation.py", WF)

if __name__ == "__main__":
    unittest.main()
