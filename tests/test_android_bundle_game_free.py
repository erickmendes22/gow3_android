"""The Android runtime bundle must build without proprietary game files."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / 'tools/android/build/build_arm64_bundle.py'
spec = importlib.util.spec_from_file_location('gow3_arm64_bundle', BUNDLE)
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)


class GameFreeBundleTests(unittest.TestCase):
    def test_missing_eboot_is_not_a_build_failure(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(bundle.subprocess, 'run') as run:
            stage, eboot = Path(tmp) / 'bundle', Path(tmp) / 'eboot.elf'
            self.assertFalse(bundle.stage_legacy_patch_parts(stage, eboot, skip=False))
            self.assertTrue((stage / 'patch-parts').is_dir())
            run.assert_not_called()

    def test_release_flag_skips_even_when_local_eboot_exists(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(bundle.subprocess, 'run') as run:
            stage, eboot = Path(tmp) / 'bundle', Path(tmp) / 'eboot.elf'
            eboot.write_bytes(b'not real game content')
            self.assertFalse(bundle.stage_legacy_patch_parts(stage, eboot, skip=True))
            self.assertTrue((stage / 'patch-parts').is_dir())
            run.assert_not_called()

    def test_local_legacy_generation_requires_explicit_existing_eboot(self):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(bundle.subprocess, 'run') as run:
            stage, eboot = Path(tmp) / 'bundle', Path(tmp) / 'eboot.elf'
            eboot.write_bytes(b'test fixture')
            self.assertTrue(bundle.stage_legacy_patch_parts(stage, eboot, skip=False))
            run.assert_called_once()
            argv = run.call_args.args[0]
            self.assertEqual(argv[argv.index('--eboot') + 1], str(eboot))
            self.assertEqual(argv[argv.index('--out') + 1], str(stage / 'patch-parts'))
            self.assertTrue(run.call_args.kwargs['check'])

    def test_android_still_compiles_real_patches_at_game_launch(self):
        runtime = (ROOT / 'tools/android/device/run-gow3.sh').read_text()
        assemble = (ROOT / 'tools/android/runtime/build_release_runtime.py').read_text()
        workflow = (ROOT / '.github/workflows/android-dev-apk.yml').read_text()
        self.assertIn('"$base/scripts/patches.py"', runtime)
        self.assertIn('gow3port/scripts/', assemble)
        self.assertIn("tar.add(ROOT / 'patches'", assemble)
        self.assertIn('GOW3_SKIP_GAME_PATCH_PARTS=1 python3', workflow)


if __name__ == '__main__':
    unittest.main()
