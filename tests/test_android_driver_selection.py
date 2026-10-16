"""Execute the actual runner's driver-selection block with isolated fixture paths."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]


class DriverSelectionTests(unittest.TestCase):
    def test_system_bundled_custom_and_invalid_selection(self):
        script=(ROOT/'tools/android/bionic/run-bionic.sh').read_text()
        begin=script.index('turnip=${GOW3_BIONIC_DRIVER:-auto}')
        end=script.index('export GOW3_ANDROID_INPUT=1',begin)
        block=script[begin:end]
        with tempfile.TemporaryDirectory(prefix='driver path ') as directory:
            root=Path(directory)
            bundled=root/'runtime/drivers/gen8/libvulkan_freedreno.so'
            custom=root/'gpu-drivers'/('a'*64)/'libcustom.so'
            for p in [bundled,custom]:p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'fixture')
            def run(selection,chip='0x44050001',library=custom):
                env=dict(os.environ,GOW3_BIONIC_DRIVER=selection,GOW3_CUSTOM_VULKAN_DRIVER=str(library),
                         GOW3_VULKAN_DRIVER='/stale/driver.so',VK_DRIVER_FILES='/stale/icd.json',
                         FIXTURE_ROOT=str(root),FIXTURE_CHIP=chip)
                return subprocess.run(['sh','-c','set -eu\nfiles="$FIXTURE_ROOT"\nruntime="$files/runtime"\nLD_LIBRARY_PATH=/runtime/lib\nchip="$FIXTURE_CHIP"\n'+block+
                    '\nprintf "RESULT=%s|%s|%s" "$turnip" "${GOW3_VULKAN_DRIVER:-system}" "$LD_LIBRARY_PATH"\n'+
                    '[ -z "${VK_DRIVER_FILES:-}" ]'],env=env,text=True,capture_output=True)
            for selection,expected in [('system','system|system|/runtime/lib'),('gen8','gen8|'+str(bundled)+'|/runtime/lib'),
                                       ('custom','custom|'+str(custom)+'|/runtime/lib:'+str(custom.parent))]:
                result=run(selection);self.assertEqual(result.returncode,0,result.stderr);self.assertIn('RESULT='+expected,result.stdout)
            self.assertIn('RESULT=gen8|',run('auto').stdout)
            self.assertIn('RESULT=system|system|',run('auto',chip='0x43000000').stdout)
            self.assertNotEqual(run('bad').returncode,0)
            self.assertNotEqual(run('custom',library=root/'elsewhere/driver.so').returncode,0)
            custom.unlink()
            self.assertNotEqual(run('custom').returncode,0)  # no silent fallback
            self.assertIn('RESULT=system|system|',run('system').stdout)
