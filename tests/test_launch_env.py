import tempfile
import unittest
from pathlib import Path
from unittest import mock

import paths  # noqa: F401  (puts scripts/ on sys.path)
import patches
from launch_env import derive

GOW3_XML = paths.ROOT / 'patches/God_of_War_III_Remastered.xml'


def _validated_profile():
    """A fake validated CUSA01623 profile (no hash: derive does not need to read eboot)."""
    return ({'CUSA01623'}, GOW3_XML, '01.02', None, None)


class LaunchEnvReasonTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.dir = Path(self.temp.name)
        self.config = self.dir / 'gow3.ini'
        self.state = self.dir / 'state.json'

    def tearDown(self):
        self.temp.cleanup()

    def _plan(self, env=None):
        env = dict(env or {})
        with mock.patch.object(patches, 'patch_profile_status',
                               return_value={'title_id': 'CUSA01623', 'app_version': '01.02',
                                             'supported': True, 'eboot_sha256': 'x',
                                             'eboot_matches': True, 'patch_sites_match': True,
                                             'reason': 'validated'}):
            with mock.patch.object(patches, 'game_profile', return_value=_validated_profile()):
                return derive(self.dir / 'game', self.config, self.state, env)

    def test_validated_default_patch_set_and_reason(self):
        plan = self._plan()
        self.assertEqual(plan['updates']['GOW3_PATCH_STATUS'], 'validated')
        self.assertIn('Bug Fix - Texture Corruption Fix', plan['patch_names'])

    def test_patch_conflict_reason_and_no_patches(self):
        # Two resolution patches are exclusive: select none and report the conflict.
        plan = self._plan({'GOW3_PATCHES': 'Resolution Patch - 4K;Resolution Patch - 720p',
                           'GOW3_PATCHES_ONLY': '1'})
        self.assertEqual(plan['updates']['GOW3_PATCH_STATUS'], 'patch-conflict')
        self.assertEqual(plan['patch_names'], [])
        self.assertIsNone(plan['dmem'])

    def test_insufficient_memory_budget_reason_and_no_patches(self):
        # The resolution patch needs 11200 MiB; a 4 GB device cannot hold it.
        plan = self._plan({'GOW3_PATCHES': 'Resolution Patch - 4K', 'GOW3_PATCHES_ONLY': '1',
                           'GOW3_DEVICE_MEM_MB': '4096'})
        self.assertEqual(plan['updates']['GOW3_PATCH_STATUS'], 'insufficient-memory-budget')
        self.assertEqual(plan['patch_names'], [])
        self.assertIsNone(plan['dmem'])

    def test_memory_budget_is_ignored_when_no_device_value(self):
        plan = self._plan({'GOW3_PATCHES': 'Resolution Patch - 4K', 'GOW3_PATCHES_ONLY': '1'})
        self.assertEqual(plan['updates']['GOW3_PATCH_STATUS'], 'validated')
        self.assertIsNotNone(plan['dmem'])

    def test_android_saved_all_off_is_not_overridden_by_xml_defaults(self):
        self.config.write_text('render_resolution=native\nengine_fps=60\npatches_CUSA01623_01_02=\n')
        plan = self._plan()
        self.assertEqual(plan['patch_names'], [])
        self.assertEqual(plan['updates']['GOW3_PATCHES_ONLY'], '1')
        self.assertEqual(plan['updates']['GOW3_PATCHES'], '')

    def test_android_saved_all_off_preserves_home_resolution(self):
        self.config.write_text('render_resolution=720p\nengine_fps=60\npatches_CUSA01623_01_02=\n')
        plan = self._plan()
        self.assertEqual(plan['patch_names'], ['Resolution Patch - 720p'])
        self.assertEqual(plan['updates']['GOW3_PATCHES_ONLY'], '1')
        self.assertEqual(plan['updates']['GOW3_PATCHES'], 'Resolution Patch - 720p')

    def test_player_files_remain_byte_identical(self):
        game = self.dir / 'game'
        (game / 'sce_sys').mkdir(parents=True)
        eboot = game / 'eboot.bin'
        eboot.write_bytes(b'retail game image bytes' * 100)
        before = eboot.read_bytes()
        self._plan({'GOW3_PATCHES': 'Resolution Patch - 4K', 'GOW3_PATCHES_ONLY': '1'})
        self.assertEqual(eboot.read_bytes(), before)
        self.assertFalse((game / 'sce_sys' / 'param.sfo').exists())


if __name__ == '__main__':
    unittest.main()
