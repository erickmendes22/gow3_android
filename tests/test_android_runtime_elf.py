import struct
import tempfile
import unittest
from pathlib import Path

from tools.android.validate_runtime_elf import ElfError, inspect, rewrite_interpreter, validate_tree


def fake_elf(path, interpreter, capacity=64):
    """Create the smallest ELF64 image needed to exercise PT_INTERP handling."""
    header_size = 64
    phoff = header_size
    phentsize = 56
    interp_offset = header_size + phentsize
    value = interpreter.encode() + b"\0"
    if len(value) > capacity:
        raise ValueError("test interpreter does not fit its fixture")
    header = bytearray(64)
    header[:4] = b"\x7fELF"
    header[4:6] = b"\x02\x01"  # ELF64, little-endian
    struct.pack_into("<HHIQQQIHHHHHH", header, 16,
                     2, 183, 1, 0, phoff, 0, 0, 64, phentsize, 1, 0, 0, 0)
    phdr = struct.pack("<IIQQQQQQ", 3, 0, interp_offset, 0, 0, capacity, capacity, 1)
    path.write_bytes(bytes(header) + phdr + value.ljust(capacity, b"\0"))


class RuntimeElfTests(unittest.TestCase):
    def test_migration_does_not_rewrite_symlink_targets_outside_rootfs(self):
        from tools.android.inputs.fetch_rootfs import OLD_ROOT, NEW_ROOT, migrate_interpreters
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            root = base / 'rootfs'
            root.mkdir()
            host = base / 'external'
            old = OLD_ROOT + '/usr/lib/ld.so'
            fake_elf(host, old, capacity=160)
            (root / 'external-link').symlink_to(host)
            fake_elf(root / 'device', old, capacity=160)
            self.assertEqual(migrate_interpreters(root), 1)
            self.assertEqual(inspect(host)['interpreter'], old)
            self.assertEqual(inspect(root / 'device')['interpreter'],
                             NEW_ROOT + '/usr/lib/ld.so')

    def test_rootfs_symlinks_do_not_inspect_files_outside_the_root(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            root = base / 'rootfs'
            root.mkdir()
            host = base / 'host-awk'
            fake_elf(host, '/lib64/ld-linux-x86-64.so.2')
            fake_elf(root / 'device-awk', '/device/ld.so')
            (root / 'absolute-link').symlink_to(host)
            (root / 'relative-link').symlink_to('../host-awk')
            (root / 'device-link').symlink_to('device-awk')
            records = validate_tree(root, '/device/ld.so')
            self.assertEqual([record['path'] for record in records],
                             [str(root / 'device-awk')])
            # Real files with a wrong loader must still fail validation.
            fake_elf(root / 'wrong-loader', '/lib64/ld-linux-x86-64.so.2')
            with self.assertRaisesRegex(ElfError, 'wrong-loader'):
                validate_tree(root, '/device/ld.so')

    def test_rewrite_preserves_fixed_interpreter_slot(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "probe"
            fake_elf(path, "/lib/ld-linux-aarch64.so.1")
            old = inspect(path)
            updated = rewrite_interpreter(path, "/data/user/0/gow3/rootfs/ld.so")
            self.assertEqual(old["interpreter_capacity"], updated["interpreter_capacity"])
            self.assertEqual(updated["interpreter"], "/data/user/0/gow3/rootfs/ld.so")

    def test_rewrite_rejects_interpreter_growth(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "probe"
            fake_elf(path, "/lib/ld.so", capacity=16)
            with self.assertRaisesRegex(ElfError, "explicit loader"):
                rewrite_interpreter(path, "/data/user/0/com.gow3.android/files/rootfs/ld.so")

