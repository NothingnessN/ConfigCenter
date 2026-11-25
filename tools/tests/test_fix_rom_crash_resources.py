# SPDX-License-Identifier: Apache-2.0
import contextlib
import io
from pathlib import Path
import stat
import tempfile
import unittest

from tools import fix_rom_crash_resources as fixer


def resources(kind, value=None):
    name, previous, _ = fixer.FIXES[kind]
    return f'<resources>\n    <string name="{name}">{value or previous}</string>\n</resources>\n'


class ResourceAlignmentTest(unittest.TestCase):
    def test_google_settings_factory_is_aligned(self):
        original = resources("settings")
        result = fixer.align_resources(original, [fixer.FIXES["settings"]])
        self.assertEqual(result, original.replace(fixer.FIXES["settings"][1],
                                                  fixer.FIXES["settings"][2]))

    def test_google_wallpaper_package_is_aligned(self):
        original = resources("systemui")
        result = fixer.align_resources(original, [fixer.FIXES["systemui"]])
        self.assertEqual(result, original.replace(fixer.FIXES["systemui"][1],
                                                  fixer.FIXES["systemui"][2]))

    def test_native_settings_is_unchanged(self):
        original = resources("settings", fixer.FIXES["settings"][2])
        self.assertEqual(fixer.align_resources(original, [fixer.FIXES["settings"]]), original)

    def test_native_wallpaper_is_unchanged(self):
        original = resources("systemui", fixer.FIXES["systemui"][2])
        self.assertEqual(fixer.align_resources(original, [fixer.FIXES["systemui"]]), original)

    def test_comments_licenses_links_xliff_and_newlines_are_preserved(self):
        previous, desired = fixer.FIXES["systemui"][1:]
        original = (
            '\ufeff<?xml version="1.0" encoding="utf-8"?>\r\n'
            '<!-- Apache-2.0: <string name="config_wallpaperPickerPackage">'
            + previous + '</string> -->\r\n'
            '<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">\r\n'
            '    <string name="credits">NothingnessN / 1yigito / D4RK OS</string>\r\n'
            '    <string name="link">https://github.com/NothingnessN</string>\r\n'
            '    <string name="example"><xliff:g id="brand" example="Havoc">%1$s</xliff:g></string>\r\n'
            "    <string translatable='false' name='config_wallpaperPickerPackage'>\r\n"
            f'        {previous}\r\n'
            '    </string>\r\n'
            '</resources>\r\n'
        )
        expected = original.replace(f'        {previous}\r\n', f'        {desired}\r\n')
        self.assertEqual(fixer.align_resources(original, [fixer.FIXES["systemui"]]), expected)

    def test_missing_definition_is_rejected(self):
        with self.assertRaises(ValueError):
            fixer.align_resources("<resources/>", [fixer.FIXES["settings"]])

    def test_duplicate_definition_is_rejected(self):
        original = resources("settings").replace('</resources>',
                    resources("settings").splitlines()[1] + '\n</resources>')
        with self.assertRaises(ValueError):
            fixer.align_resources(original, [fixer.FIXES["settings"]])

    def test_unexpected_oem_factory_is_rejected(self):
        with self.assertRaises(ValueError):
            fixer.align_resources(resources("settings", "com.vendor.Factory"),
                                  [fixer.FIXES["settings"]])

    def test_unknown_wallpaper_package_is_rejected(self):
        with self.assertRaises(ValueError):
            fixer.align_resources(resources("systemui", "com.vendor.wallpaper"),
                                  [fixer.FIXES["systemui"]])

    def test_markup_in_configuration_is_rejected(self):
        original = resources("settings").replace(fixer.FIXES["settings"][1],
                    '<b>' + fixer.FIXES["settings"][1] + '</b>')
        with self.assertRaises(ValueError):
            fixer.align_resources(original, [fixer.FIXES["settings"]])

    def test_inline_comment_in_configuration_is_not_removed(self):
        original = resources("settings").replace(fixer.FIXES["settings"][1],
                    fixer.FIXES["settings"][1] + '<!-- retain -->')
        with self.assertRaises(ValueError):
            fixer.align_resources(original, [fixer.FIXES["settings"]])

    def test_non_resources_xml_is_rejected(self):
        with self.assertRaises(ValueError):
            fixer.align_resources("<manifest/>", [fixer.FIXES["settings"]])

    def test_two_values_in_one_file_are_aligned(self):
        original = resources("settings").replace('</resources>',
                    resources("systemui").splitlines()[1] + '\n</resources>')
        expected = original
        for _, previous, desired in fixer.FIXES.values():
            expected = expected.replace(previous, desired)
        self.assertEqual(fixer.align_resources(original, list(fixer.FIXES.values())), expected)


class ResourceFileTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="d4rk-resource-tests-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.settings = self.root / "settings.xml"
        self.settings.write_bytes(resources("settings").encode("utf-8"))

    def test_dry_run_does_not_write_or_create_backup(self):
        original = self.settings.read_bytes()
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(fixer.main(["--settings-config", str(self.settings)]), 0)
        self.assertIn("Dry run only", output.getvalue())
        self.assertEqual(self.settings.read_bytes(), original)
        self.assertFalse(self.settings.with_name(self.settings.name + fixer.BACKUP_SUFFIX).exists())

    def test_apply_keeps_original_bytes_and_permissions(self):
        original = self.settings.read_bytes().replace(b"\n", b"\r\n")
        self.settings.write_bytes(original)
        self.settings.chmod(0o640)
        plans = fixer.plan_changes({self.settings: [fixer.FIXES["settings"]]})
        fixer.apply_changes(plans)
        backup = self.settings.with_name(self.settings.name + fixer.BACKUP_SUFFIX)
        self.assertEqual(backup.read_bytes(), original)
        self.assertEqual(stat.S_IMODE(self.settings.stat().st_mode), 0o640)
        self.assertEqual(stat.S_IMODE(backup.stat().st_mode), 0o640)
        self.assertEqual(self.settings.read_bytes(), original.replace(
            fixer.FIXES["settings"][1].encode(), fixer.FIXES["settings"][2].encode()))

    def test_existing_backup_prevents_all_writes(self):
        other = self.root / "systemui.xml"
        other.write_bytes(resources("systemui").encode())
        backup = other.with_name(other.name + fixer.BACKUP_SUFFIX)
        backup.write_text("user backup")
        plans = fixer.plan_changes({self.settings: [fixer.FIXES["settings"]],
                                    other: [fixer.FIXES["systemui"]]})
        with self.assertRaises(ValueError):
            fixer.apply_changes(plans)
        self.assertEqual(self.settings.read_text(), resources("settings"))
        self.assertEqual(other.read_text(), resources("systemui"))
        self.assertEqual(backup.read_text(), "user backup")

    def test_concurrent_source_change_is_not_overwritten(self):
        plans = fixer.plan_changes({self.settings: [fixer.FIXES["settings"]]})
        self.settings.write_text(resources("settings") + "<!-- user edit -->\n")
        with self.assertRaises(ValueError):
            fixer.apply_changes(plans)
        self.assertTrue(self.settings.read_text().endswith("<!-- user edit -->\n"))

    def test_invalid_second_file_leaves_first_file_unchanged(self):
        other = self.root / "systemui.xml"
        other.write_text("<resources/>")
        with contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as error:
                fixer.main(["--settings-config", str(self.settings),
                            "--systemui-config", str(other), "--apply"])
        self.assertEqual(error.exception.code, 2)
        self.assertEqual(self.settings.read_text(), resources("settings"))
        self.assertFalse(self.settings.with_name(self.settings.name + fixer.BACKUP_SUFFIX).exists())

    def test_same_file_arguments_produce_one_backup(self):
        original = resources("settings").replace('</resources>',
                    resources("systemui").splitlines()[1] + '\n</resources>')
        self.settings.write_text(original)
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(fixer.main(["--settings-config", str(self.settings),
                             "--systemui-config", str(self.settings), "--apply"]), 0)
        backups = list(self.root.glob("*" + fixer.BACKUP_SUFFIX))
        self.assertEqual(len(backups), 1)
        self.assertEqual(backups[0].read_text(), original)
        self.assertIn(fixer.FIXES["settings"][2], self.settings.read_text())
        self.assertIn(fixer.FIXES["systemui"][2], self.settings.read_text())


if __name__ == "__main__":
    unittest.main()
