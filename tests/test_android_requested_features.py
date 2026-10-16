"""Android stable-branch user feature regression contracts."""
from paths import ROOT
import unittest
import xml.etree.ElementTree as ET
BASE=ROOT/'android-launcher/app/src/main'
class AndroidRequestedFeatures(unittest.TestCase):
    def test_controller(self):
        pad=(BASE/'java/ro/cobrabm/fexdroid/PadBridge.java').read_text()
        game=(BASE/'java/ro/cobrabm/fexdroid/GameActivity.java').read_text()
        self.assertIn('swapFaceButtons ? "triangle" : "square"',pad)
        self.assertIn('swapFaceButtons ? "square" : "triangle"',pad)
        self.assertIn('pad.setSwapFaceButtons(on)',game)
        self.assertIn('env.put("GOW3_LANGUAGE","pt".equals(lang) ? "7" : "1")',game)
        self.assertIn('setting("android_fullscreen")',game)
    def test_patches_location_and_runtime(self):
        nav=(BASE/'java/com/gow3/android/ui/LauncherScreen.kt').read_text()
        params=(BASE/'java/com/gow3/android/data/LaunchParams.kt').read_text()
        launch=(ROOT/'scripts/launch_env.py').read_text()
        self.assertIn('NavItem(LauncherTab.MODS,',nav)
        self.assertNotIn('key = "skip_intro"',params)
        self.assertIn("selection_key = 'patches_{}_{}'",launch)
        self.assertIn('texture_fix_available=not use_saved',launch)
        self.assertEqual((BASE/'assets/God_of_War_III_Remastered.xml').read_bytes(),
                         (ROOT/'patches/God_of_War_III_Remastered.xml').read_bytes())
        names=[m.attrib['Name'] for m in ET.parse(ROOT/'patches/God_of_War_III_Remastered.xml').getroot().iter('Metadata')]
        self.assertEqual(len(names),len(set(names)))
        for name in ('Disable SSAO','Disable Motion Blur','Disable Depth of Field',
                     'Disable Bloom','Disable Soft Particles','Disable Dynamic Shadows'):
            self.assertIn(name,names)
if __name__=="__main__": unittest.main()
