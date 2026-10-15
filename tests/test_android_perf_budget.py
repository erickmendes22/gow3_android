"""Android CPU/GPU overlap policy. Source checks; do not claim measured FPS."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class AndroidPerfBudgetTests(unittest.TestCase):
    def test_direct_draw_does_not_start_unused_speculative_workers(self):
        draw = (ROOT / 'gpu/shadps4/video_core/renderer_vulkan/vk_draw_prep.cpp').read_text()
        start = draw.index('u32 DefaultWorkerCount() {')
        end = draw.index('\n}', start)
        policy = draw[start:end]
        self.assertIn('std::getenv("GOW3_PREP_WORKERS")', policy)
        self.assertIn('std::getenv("GOW3_ANDROID_PORT")', policy)
        self.assertRegex(policy, r'if \(std::getenv\("GOW3_ANDROID_PORT"\)\)\s*\{\s*return 0;')
        self.assertIn('return std::clamp<u32>(Gow3Threads::Available() / 2, 1, 8)', policy)
        self.assertIn('if (!Enabled())', draw)
        self.assertIn('return {};', draw)
        raster = (ROOT / 'gpu/shadps4/video_core/renderer_vulkan/vk_rasterizer.cpp').read_text()
        self.assertIn('if (std::getenv("GOW3_ANDROID_PORT")) return false;', raster)

    def test_two_frames_in_flight_on_new_android_installs(self):
        ini = (ROOT / 'tools/android/config/gow3-default.ini').read_text()
        launch = (ROOT / 'android-launcher/app/src/main/java/com/gow3/android/data/LaunchParams.kt').read_text()
        self.assertIn('frames_queued=2', ini)
        setting = launch.split('key = "frames_queued"', 1)[1].split('descriptionRes', 1)[0]
        self.assertIn('defaultValue = "2"', setting)
        self.assertIn('LaunchOption("1"', setting)
        self.assertIn('LaunchOption("0"', setting)
        presenter = (ROOT / 'gpu/shadps4/video_core/renderer_vulkan/vk_presenter.cpp').read_text()
        self.assertIn('Gow3Settings::Get().frames_queued.load()', presenter)
        self.assertIn('draw_scheduler.Wait(tick)', presenter)

if __name__ == '__main__':
    unittest.main()
