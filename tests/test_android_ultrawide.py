"""Validate Hor+ geometry, all four camera trampolines and the Android display contract."""
import struct
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import paths
import graphics_options as graphics
import patches
from ultrawide_fixture import FRAGMENTS
from test_launch_env import LaunchEnvReasonTests

ROOT = paths.ROOT
XML = ROOT / 'patches/God_of_War_III_Remastered.xml'


def sample_image():
    end = max(address + len(bytes.fromhex(raw)) for address, raw in FRAGMENTS)
    image = bytearray(end - patches.EBOOT_BASE)
    for address, raw in FRAGMENTS:
        data = bytes.fromhex(raw)
        offset = address - patches.EBOOT_BASE
        image[offset:offset + len(data)] = data
    return image


class UltrawidePatchTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('java'), 'Java runtime required')
    def test_android_surface_aspect_helper(self):
        source = '''package ro.cobrabm.fexdroid;
public class AspectCheck {
  static void expect(int[] size, int w, int h) {
    if (size[0]!=w || size[1]!=h) throw new AssertionError(size[0]+"x"+size[1]);
  }
  public static void main(String[] args) {
    for (String size : new String[]{"840x360","1120x480","1260x540","1680x720"}) {
      expect(RenderAspect.of(size),7,3);
      expect(RenderAspect.fit(2688,1216,size,false),2688,1152);
      expect(RenderAspect.fit(2688,1216,size,true),2688,1216);
      expect(RenderAspect.fit(1080,1080,size,false),1080,462);
    }
    expect(RenderAspect.fit(2688,1216,"720p",false),2161,1216);
    expect(RenderAspect.of("unknown"),16,9);
  }
}'''
        with tempfile.TemporaryDirectory() as directory:
            check = Path(directory) / 'AspectCheck.java'
            check.write_text(source)
            helper = ROOT / 'android-launcher/app/src/main/java/ro/cobrabm/fexdroid/RenderAspect.java'
            subprocess.run(['java', 'com.sun.tools.javac.Main', '-d', directory,
                            str(helper), str(check)], check=True, capture_output=True)
            subprocess.run(['java', '-cp', directory, 'ro.cobrabm.fexdroid.AspectCheck'],
                           check=True, capture_output=True)

    def test_geometry_camera_jumps_and_independent_arena(self):
        image = sample_image()
        for name, (width, height) in graphics.ULTRAWIDE_SIZES.items():
            with self.subTest(name=name):
                self.assertEqual(width * 3, height * 7)
                self.assertLessEqual(width * height, 1680 * 720)
                names = ['Resolution Patch - ' + name]
                writes = patches.compile_patches(XML, names, '01.02', [(0, len(image))], image)
                self.assertEqual(len(writes), 29)
                expected = (width, height, width, height, height, width, width, height,
                            width, height, height, height, width // 2, height // 2)
                self.assertEqual([int.from_bytes(data, 'little') for _, data in writes[:14]],
                                 list(expected))
                self.assertEqual(struct.unpack('<ff', writes[15][1]),
                                 (struct.unpack('<f', struct.pack('<f', width / height))[0],) * 2)
                k_address, k = writes[16]
                self.assertEqual(struct.unpack('<f', k)[0], 21 / 16)
                for camera in range(4):
                    site, first, second = writes[17 + camera * 3:20 + camera * 3]
                    site_addr, site_bytes = site
                    first_addr, first_bytes = first
                    second_addr, second_bytes = second
                    self.assertEqual(site_bytes[0], 0xe9)
                    self.assertEqual(site_addr + 5 + struct.unpack_from('<i', site_bytes, 1)[0], first_addr)
                    self.assertEqual(first_bytes[:8], image[site_addr:site_addr + 8])
                    self.assertEqual(first_addr + 13 + struct.unpack_from('<i', first_bytes, 9)[0], second_addr)
                    self.assertEqual(second_addr + 8 + struct.unpack_from('<i', second_bytes, 4)[0], k_address)
                    self.assertEqual(second_addr + 13 + struct.unpack_from('<i', second_bytes, 9)[0], site_addr + 8)
                ordered = sorted((at, at + len(data)) for at, data in writes)
                self.assertTrue(all(end <= following for (_, end), (following, _) in zip(ordered, ordered[1:])))
                off = patches.compile_patches(XML, names, '01.02', [(0, len(image))], image,
                                               expand_video_arena=False)
                self.assertEqual(off, writes[:14] + writes[15:])

    def test_camera_signature_failure_leaves_no_partial_resolution_patch(self):
        image = sample_image()
        pattern = patches.parse_pattern('c4 c1 78 29 47 10 c4 c1 78 29 07 c5 fa 10 89 d8 00 00 00')
        image[patches.find_pattern(image, pattern)[0]] = 0
        skipped = {}
        writes = patches.compile_patches(XML, ['Resolution Patch - 1680x720'], '01.02',
                                          [(0, len(image))], image, skipped)
        self.assertEqual(writes, [])
        self.assertIn('Resolution Patch - 1680x720', skipped)

    def test_cutscene_bars_native_code(self):
        with tempfile.TemporaryDirectory() as directory:
            binary = str(Path(directory) / 'movie-bars')
            subprocess.run(['g++', '-std=c++20', '-Wall', '-Wextra', '-Werror',
                            '-Igpu/shadps4', 'tests/test_movie_bars.cpp', '-o', binary],
                           cwd=ROOT, check=True, capture_output=True)
            subprocess.run([binary], check=True, capture_output=True)


class UltrawideLaunchTests(LaunchEnvReasonTests):
    def test_each_wide_size_reaches_the_android_presenter_and_keeps_fps(self):
        for name, (width, height) in graphics.ULTRAWIDE_SIZES.items():
            self.config.write_text(f'render_resolution={name}\nengine_fps=240\nexpand_video_arena=0\n')
            plan = self._plan({'GOW3_ANDROID_PORT': '1'})
            self.assertEqual(plan['updates']['GOW3_SCREEN_W'], str(width))
            self.assertEqual(plan['updates']['GOW3_SCREEN_H'], str(height))
            self.assertIn('Resolution Patch - ' + name, plan['patch_names'])
            self.assertIn('Frame Rate Patch - 240 FPS', plan['patch_names'])
            self.assertEqual(plan['dmem'], 5056)

    def test_standard_android_output_remains_1280x720(self):
        self.config.write_text('render_resolution=720p\n')
        plan = self._plan({'GOW3_ANDROID_PORT': '1'})
        self.assertEqual((plan['updates']['GOW3_SCREEN_W'], plan['updates']['GOW3_SCREEN_H']),
                         ('1280', '720'))
