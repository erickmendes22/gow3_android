#!/usr/bin/env python3
"""Reject glibc, libvulkan/SDL/X11 linkage and unresolved strong symbols before packaging."""
import argparse
from pathlib import Path
import re
import subprocess


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--ndk', type=Path, required=True)
    p.add_argument('--api', type=int, default=30)
    p.add_argument('--root', type=Path, default=Path('out/bionic'))
    args = p.parse_args()
    llvm = args.ndk/'toolchains/llvm/prebuilt/linux-x86_64'
    system = llvm/f'sysroot/usr/lib/aarch64-linux-android/{args.api}'
    allowed = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libnativewindow.so',
               'libc++_shared.so', 'libgow3cpu.so', 'libgow3gpu.so'}
    files = [args.root/'bin/gow3-probe', args.root/'bin/gow3-prepare', args.root/'bin/kgsl-chip-id',
             args.root/'lib/libgow3cpu.so', args.root/'lib/libgow3gpu.so', args.root/'lib/libgow3present.so',
             args.root/'lib/libc++_shared.so']
    nm = str(llvm/'bin/llvm-nm')
    readelf = str(llvm/'bin/llvm-readelf')
    def output(*cmd):
        return subprocess.check_output(cmd, text=True)
    providers = set()
    for f in [*files, *[system/n for n in allowed if (system/n).is_file()]]:
        for line in output(nm, '-D', '--defined-only', str(f)).splitlines():
            fields = line.split()
            if len(fields) >= 3:
                providers.add(fields[-1].split('@')[0])
    failed = False
    for f in files:
        elf = output(readelf, '-h', str(f))
        if 'AArch64' not in elf or 'DYN ' not in elf:
            raise SystemExit(f'Not an ARM64 PIE/DSO: {f}')
        if f.parent.name == 'bin':
            if '/system/bin/linker64' not in output(readelf, '-l', str(f)):
                raise SystemExit(f'Not an Android executable: {f}')
        needed = set(re.findall(r'NEEDED.*?\[(.*?)\]', output(readelf, '-d', str(f))))
        if needed - allowed:
            raise SystemExit(f'{f}: disallowed NEEDED: {sorted(needed-allowed)}')
        missing = set()
        for line in output(nm, '-D', '--undefined-only', str(f)).splitlines():
            fields = line.split()
            if len(fields) >= 2 and fields[-2] == 'U':
                symbol = fields[-1].split('@')[0]
                if symbol not in providers:
                    missing.add(symbol)
        if missing:
            print(f'{f}: unresolved strong symbols: {sorted(missing)}')
            failed = True
        else:
            print(f'{f.name}: Bionic ARM64, dependencies and symbols OK')
    return int(failed)


if __name__ == '__main__':
    raise SystemExit(main())
