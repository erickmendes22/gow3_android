"""Reject launching expensive ARM64 builds without Mesa's host shader compiler."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
WORKFLOW=(ROOT/'.github/workflows/android-dev-apk.yml').read_text()
TURNIP=(ROOT/'tools/android/build/build_turnip.sh').read_text()
ENVIRONMENT=(ROOT/'ci/environment.yml').read_text()

class GraphicsToolchainPreflight(unittest.TestCase):
    def test_pinned_host_glslang(self):
        self.assertIn('  - glslang\n',ENVIRONMENT)
        self.assertIn('command -v glslangValidator',TURNIP)
        self.assertIn('glslangValidator --version',TURNIP)
    def test_conda_login_shell_used_for_turnip_and_lsfg(self):
        block=WORKFLOW.split('      - name: Build Turnip and LSFG variants',1)[1].split('      - name: ',1)[0]
        self.assertIn('shell: bash -el {0}',block)
        self.assertIn('glslangValidator --version',block)
        self.assertIn('build_turnip.sh main',block)
        self.assertIn('build_turnip.sh gen8',block)
        self.assertIn('build_lsfg.sh',block)
    def test_preflight_before_native_cross_compilation(self):
        self.assertLess(WORKFLOW.index('      - name: Preflight graphics build tools'),
                        WORKFLOW.index('      - name: Build ARM64 native runtime'))
        block=WORKFLOW.split('      - name: Preflight graphics build tools',1)[1].split('      - name: ',1)[0]
        self.assertIn('shell: bash -el {0}',block)
        for name in ('glslangValidator','bison','flex','llvm-strip','ninja'):
            self.assertIn(name,block)

if __name__=='__main__':
    unittest.main()
