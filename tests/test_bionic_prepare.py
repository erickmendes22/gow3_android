"""Byte parity of native preparation with the existing GoW3 Python pipeline."""
from paths import ROOT
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from bionic_prepare_fixture import synthetic_game, write_game


class BionicPrepareParity(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.native = ROOT/'out/host/gow3-prepare'
        if not cls.native.is_file():
            raise unittest.SkipTest('Build the host tool with tools/android/bionic/build_prepare.sh host')

    def compare(self, path, root):
        py = root/'py'
        native = root/'native'
        env = dict(os.environ, PYTHONPATH=str(ROOT/"scripts"))
        a = subprocess.run([sys.executable, str(ROOT/'tools/android/inputs/prepare_game.py'), str(path), str(py)],
                           env=env, text=True, capture_output=True)
        b = subprocess.run([str(self.native), str(path), str(native)], env=env, text=True, capture_output=True)
        self.assertEqual(b.returncode, a.returncode, f'Python: {a.stderr}\nNative: {b.stderr}')
        if a.returncode == 0:
            self.assertIn("Game image:", b.stdout)
        if a.returncode == 0:
            for name in ['boot-linked.bin','content.bin','game-source.json']:
                self.assertEqual((native/name).read_bytes(), (py/name).read_bytes(), name)
        return a, b

    def test_synthetic_game_imports_relocations_tls_and_marker_reuse(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game = write_game(root/'game', *synthetic_game())
            a, _ = self.compare(game, root)
            self.assertEqual(a.returncode, 0, a.stderr)
            self.compare(game, root)

    def test_user_content_parameters(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game = write_game(root/'game', *synthetic_game(), params={
                'USER_DEFINED_PARAM_1': 0, 'USER_DEFINED_PARAM_2': 100,
                'USER_DEFINED_PARAM_3': 0xffffffff, 'USER_DEFINED_PARAM_4': 42})
            a, _ = self.compare(game, root)
            self.assertEqual(a.returncode, 0, a.stderr)

    def test_damaged_image_and_missing_module_fail(self):
        for missing in ['eboot.bin','sce_module/libc.prx','sce_module/libSceFios2.prx']:
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                game = write_game(root/'game', *synthetic_game())
                (game/missing).unlink()
                a, _ = self.compare(game, root)
                self.assertNotEqual(a.returncode, 0)
                self.assertFalse((root/'native/game-source.json').exists())

    def test_empty_sfo_fails_without_cached_image(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            game = write_game(root/'game', *synthetic_game())
            (game/'sce_sys/param.sfo').write_bytes(b'')
            a, _ = self.compare(game, root)
            self.assertNotEqual(a.returncode, 0)
            self.assertFalse((root/'native/game-source.json').exists())


if __name__ == '__main__':
    unittest.main()
