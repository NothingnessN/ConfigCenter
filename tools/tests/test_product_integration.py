# SPDX-License-Identifier: Apache-2.0
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]


class ProductIntegrationTest(unittest.TestCase):
    def test_settings_overlay_selects_native_factory(self):
        self.assert_single_string(
            ROOT / "product/overlay/packages/apps/Settings/res/values/config.xml",
            "config_featureFactory",
            "com.android.settings.overlay.FeatureFactoryImpl",
        )

    def test_systemui_overlay_selects_installed_picker(self):
        self.assert_single_string(
            ROOT / "product/overlay/frameworks/base/packages/SystemUI/res/values/config.xml",
            "config_wallpaperPickerPackage",
            "com.android.wallpaper",
        )

    def test_overlay_paths_shadow_android_source_paths(self):
        overlay = ROOT / "product/overlay"
        expected = (
            "packages/apps/Settings/res/values/config.xml",
            "frameworks/base/packages/SystemUI/res/values/config.xml",
        )
        for relative in expected:
            self.assertTrue((overlay / relative).is_file(), relative)

    def test_complete_fragment_prepends_overlay_and_inherits_package_list(self):
        text = (ROOT / "product/d4rk_crash_fixes.mk").read_text()
        assignment = text.index("PRODUCT_PACKAGE_OVERLAYS :=")
        own_overlay = text.index("$(_d4rk_config_center_product_path)/overlay", assignment)
        inherited = text.index("$(PRODUCT_PACKAGE_OVERLAYS)", own_overlay)
        self.assertLess(own_overlay, inherited)
        exclusion = text.index("PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS :=")
        self.assertIn("$(_d4rk_config_center_product_path)/overlay", text[exclusion:])
        self.assertIn("/lineageparts.mk)", text)

    def test_lineageparts_package_is_explicit(self):
        self.assertEqual(
            (ROOT / "product/lineageparts.mk").read_text().splitlines()[-1],
            "PRODUCT_PACKAGES += LineageParts",
        )
        patch = (ROOT / "patches/settings/0004-Settings-require-LineageParts.patch").read_text()
        self.assertIn('+        "LineageParts",', patch)

    def assert_single_string(self, path, name, value):
        root = ET.parse(path).getroot()
        self.assertEqual(root.tag, "resources")
        strings = root.findall("string")
        self.assertEqual(len(strings), 1)
        self.assertEqual(strings[0].get("name"), name)
        self.assertEqual((strings[0].text or "").strip(), value)
        self.assertEqual(strings[0].get("translatable"), "false")


if __name__ == "__main__":
    unittest.main()
