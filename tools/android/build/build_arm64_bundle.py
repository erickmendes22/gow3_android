#!/usr/bin/env python3
"""Stage the native ARM64 build (tools/android/build_arm64.sh) for the Thor.

bin/gow3-probe runs natively in fexdroid's Debian trixie arm64 rootfs; lib/ holds libgow3gpu, libgow3cpu
(FEXCore) and the libraries that rootfs lacks, from the same Debian release (the sysroot).
"""
import argparse
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'out/arm64'
OURS = {'libgow3gpu.so': OUT / 'gpu/libgow3gpu.so', 'libgow3cpu.so': OUT / 'fex/libgow3cpu.so'}
# FFmpeg's libraries share internal symbols: always the same build of all of them.
FFMPEG = ('libavformat.so.61', 'libavcodec.so.61', 'libavutil.so.59', 'libswscale.so.8',
          'libswresample.so.5')


def needed(path):
    output = subprocess.check_output(['llvm-readelf', '-d', str(path)], text=True)
    return [line.split('[', 1)[1].split(']', 1)[0] for line in output.splitlines() if '(NEEDED)' in line]


def stage_legacy_patch_parts(stage, eboot=None, skip=None):
    """Keep the historical bundle directory without requiring proprietary game files.

    Android packages include Python and XML patches, not compiled game code. The
    legacy Thor generator is optional for local builds *only* when an explicit
    eboot.elf exists and a caller did not disable it.
    """
    path = Path(eboot) if eboot is not None else ROOT / 'out/eboot.elf'
    force_skip = os.environ.get('GOW3_SKIP_GAME_PATCH_PARTS') == '1' if skip is None else skip
    output = Path(stage) / 'patch-parts'
    if force_skip or not path.is_file():
        output.mkdir(parents=True, exist_ok=True)
        print('Legacy Thor patch-parts: skipped; Android patches compiled on-device')
        return False
    subprocess.run([sys.executable, str(ROOT / 'tools/android/build_gow3_patch_parts.py'),
                    '--eboot', str(path), '--out', str(output)], check=True)
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sysroot', type=Path, default=ROOT / '.local-deps/android/arm64-sysroot')
    parser.add_argument('--device-libs', type=Path, required=True,
                        help='names of the shared libraries in the Thor rootfs (one per line)')
    parser.add_argument('--out', type=Path, default=OUT / 'bundle')
    args = parser.parse_args()
    have = set(args.device_libs.read_text().split())
    libdirs = [args.sysroot / 'usr/lib/aarch64-linux-gnu', args.sysroot / 'usr/lib']

    def find(name):
        if name in OURS:
            return OURS[name]
        for directory in libdirs:
            if (directory / name).exists():
                return directory / name
        raise SystemExit(f'not in the sysroot: {name}')

    ship, queue, seen = {}, [OUT / 'gow3-probe', *(find(n) for n in FFMPEG)], set()
    for name in FFMPEG:
        ship[name] = find(name)
    while queue:
        for name in needed(queue.pop()):
            if name in seen:
                continue
            seen.add(name)
            if name in have and name not in OURS and name not in FFMPEG:
                continue
            ship[name] = find(name)
            queue.append(ship[name])
    stage = args.out
    if stage.exists():
        shutil.rmtree(stage)
    (stage / 'bin').mkdir(parents=True)
    (stage / 'lib').mkdir()
    shutil.copy2(OUT / 'gow3-probe', stage / 'bin/gow3-probe')
    shutil.copy2(OUT / 'kgsl-chip-id', stage / 'bin/kgsl-chip-id')
    # Legacy Thor precompiled patch-parts require a player's eboot.elf. Android
    # release builds never carry game content: the current device run-gow3.sh
    # calls scripts/patches.py after preparing the user's own eboot.bin instead.
    stage_legacy_patch_parts(stage)
    # FSR 4 model assets (tools/fetch_fsr4_assets.sh, not in the repository), when fetched.
    if (ROOT / 'fsr4_shaders').is_dir():
        shutil.copytree(ROOT / 'fsr4_shaders', stage / 'fsr4_shaders')
    for name, source in sorted(ship.items()):
        shutil.copy2(source.resolve(), stage / 'lib' / name)
    for path in [stage / 'bin/gow3-probe', stage / 'bin/kgsl-chip-id', *(stage / 'lib').iterdir()]:
        subprocess.run(['llvm-strip', '--strip-debug', str(path)], check=True)
        if path.parent.name == 'lib':
            subprocess.run(['patchelf', '--set-rpath', '$ORIGIN', str(path)], check=True)
    # copy2 preserves the source mode, but a stale umask or an unpacked toolchain
    # must never make a release entrypoint non-executable.  Do not normalize
    # shared-library modes or dereference symlinks here.
    for path in (stage / 'bin').iterdir():
        if path.is_symlink():
            continue
        path.chmod(path.stat().st_mode | 0o111)
    for path in stage.rglob('*.sh'):
        if path.is_symlink():
            continue
        path.chmod(path.stat().st_mode | 0o111)
    records = []
    for path in sorted(stage.rglob('*')):
        info = path.lstat()
        if stat.S_ISLNK(info.st_mode):
            records.append({'path': str(path.relative_to(stage)), 'type': 'symlink',
                            'mode': stat.S_IMODE(info.st_mode), 'target': path.readlink().as_posix()})
        elif path.is_file():
            records.append({'path': str(path.relative_to(stage)), 'type': 'file',
                            'mode': stat.S_IMODE(info.st_mode), 'bytes': info.st_size,
                            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    (stage / 'manifest.json').write_text(json.dumps(records, indent=2) + '\n')
    total = sum(r['bytes'] for r in records) / 2**20
    print(f'Staged {len(records)} files, {total:.1f} MiB: {stage}')


if __name__ == '__main__':
    main()
