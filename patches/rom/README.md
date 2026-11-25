# Android 13 crash fixes

These changes address the three captured failures without changing ConfigCenter's controls, native setting providers, fragment names, branding, logo, credits, or links. No runtime compatibility classes or exception-suppression patches are added.

Use [`INSTALL_CRASH_FIXES.md`](INSTALL_CRASH_FIXES.md) for the complete setup guide or [`SERVER_COMMANDS.md`](SERVER_COMMANDS.md) for the short command checklist.

## What is fixed locally

- `../settings/0004-Settings-require-LineageParts.patch` adds `LineageParts` to the Android 13 Settings module's required packages. The original supplied Settings source is not stored or modified here.
- Apply it after the existing three Settings patches.
- `../../product/d4rk_crash_fixes.mk` is the preferred one-line product integration. It includes LineageParts and a source overlay containing the two confirmed resource corrections.
- `../../product/lineageparts.mk` remains available if you want only the LineageParts package and will fix the two resource definitions in their original server files.
- Use `Havoc-OS/android_packages_apps_LineageParts`, branch **`thirteen`**, pinned to commit `6bb38ac07319582679525fb20fc4af16a3dbba15`. It is synchronized with the pinned Havoc Lineage SDK from October 2023 and includes the expected `VendorSupport-preference` dependency. Its `parts_catalog.xml` contains `touchscreen_gesture_settings`, with its fragment and XML present. Do not substitute the newer upstream `lineage-20.0` head.
- `../../tools/fix_rom_crash_resources.py` prepares the two resource-value corrections in XML override files you explicitly select on the server. It defaults to a dry run, preserves other XML text, and keeps exclusive backups when applying changes.

The patch, product integration, tool, and documentation are prepared in source form. The server product/resource changes and a new ROM build are **not** applied or runtime-verified here.

## 1. Install LineageParts: ConfigCenter → native Gestures

Copy/sync the recovered LineageParts source to `packages/apps/LineageParts` on the server. It builds the `LineageParts` module and requires its existing platform-signed privileged permission whitelist.

From the server's ROM root, with ConfigCenter at `packages/apps/ConfigCenter`:

```sh
CONFIG_CENTER="$PWD/packages/apps/ConfigCenter"
git -C packages/apps/Settings apply "$CONFIG_CENTER/patches/settings/0004-Settings-require-LineageParts.patch"
```

In your top-level ROM product makefile, **after** the GApps/product configuration, directly include the complete fix set:

```make
include packages/apps/ConfigCenter/product/d4rk_crash_fixes.mk
```

Use `include`, not `inherit-product`, for this complete fragment. Android product inheritance expands sibling nodes by path order, while a direct include writes the overlay path into the top-level product before inherited overlay values are expanded. Android 13 gives the first matching `PRODUCT_PACKAGE_OVERLAYS` path priority. The fragment excludes this specific path from automatic RRO conversion, keeping both corrections as deterministic compile-time resources rather than runtime overlay packages. It separately inherits its small `lineageparts.mk` package list through the standard product mechanism.

The product inclusion is important if `SettingsGoogle` overrides the native `Settings` module: a `required` dependency on an APK that is not installed does not ensure LineageParts ships. Keep `LineageParts`, its catalog key, and the native Gestures destination; do not remove the whole Gestures page.

After `lunch`, confirm the integration before starting the long build:

```sh
get_build_var PRODUCT_PACKAGE_OVERLAYS
get_build_var PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS
get_build_var PRODUCT_PACKAGES | tr ' ' '\n' | grep -x LineageParts
```

`packages/apps/ConfigCenter/product/overlay` must be the first matching product-overlay root and must appear in the RRO exclusion list. `LineageParts` must be printed by the final command.

## 2. App info: correct the selected Settings variant/dependencies

SettingsGoogle can reuse the supplied Settings sources and ConfigCenter patches. The device's effective `config_featureFactory` is nevertheless **`com.google.android.settings.overlay.FeatureFactoryImpl`**, and its Google battery provider calls the absent `com.google.android.settings.intelligence` component.

The supplied native Settings source already selects **`com.android.settings.overlay.FeatureFactoryImpl`**. Editing that already-correct default alone will not fix a higher-priority Google resource override.

Choose one server-side configuration:

- **Native Settings behavior (implemented by `d4rk_crash_fixes.mk`):** install native `Settings` instead of the Google replacement, or restore the final Settings variant's `config_featureFactory` override to the supplied native factory. The product source overlay and the tool's `--settings-config` option explicitly opt into this resource correction. This selects the native factory globally; Google-only Settings additions and Google's enhanced battery-history integration are no longer selected. ConfigCenter's own provider/value mappings are unchanged.
- **Keep Google-only Settings behavior:** keep the Google factory and ship the matching Settings Intelligence APK/module containing `BatterySettingsContentProvider`. That package must support this `armeabi-v7a` Android 13 build. No matching Google dependency or server module definition is supplied here, so its module name cannot safely be guessed. Do **not** use `--settings-config` for this choice.

Do not conceal this failure by catching the missing-component exception in App info or rerouting ConfigCenter providers.

## 3. Lock-screen long-press: correct SystemUI's picker resource

The popup crashes **SystemUI**, not Settings. Its effective **`config_wallpaperPickerPackage`** is `com.google.android.apps.wallpaper`, but the installed ThemePicker package is **`com.android.wallpaper`** and resolves `android.intent.action.SET_WALLPAPER`.

Restore the **SystemUI** override to `com.android.wallpaper`. `d4rk_crash_fixes.mk` supplies that final source overlay. The supplied pinned framework default already has the same value, so the overlay deliberately defeats the actual product/Google override rather than editing only the correct default. This is not Settings' differently named `config_wallpaper_picker_package`, and the working native `android.settings.LOCK_SCREEN_SETTINGS` action is unrelated to the popup's wallpaper intent.

## Alternative: align the original XML overrides on the server

You do not normally need this alternative when using `d4rk_crash_fixes.mk`. It is required if SettingsGoogle compiles its final `config_featureFactory` from a separate vendor resource directory that the `packages/apps/Settings` overlay does not target. Locate definitions in the server tree and identify the one belonging to the final SettingsGoogle module, not comments or the already-correct base Settings default:

```sh
rg -n --glob '*.xml' 'config_featureFactory|config_wallpaperPickerPackage' packages vendor device
```

For the native-Settings choice, preview both corrections using the real file paths you found:

```sh
python3 "$CONFIG_CENTER/tools/fix_rom_crash_resources.py" \
    --settings-config "$SETTINGS_OVERRIDE_XML" \
    --systemui-config "$SYSTEMUI_OVERRIDE_XML"
```

Define `SETTINGS_OVERRIDE_XML` and `SYSTEMUI_OVERRIDE_XML` as the actual XML paths first. After reviewing the displayed diff, rerun with `--apply`. To preserve Google Settings, omit `--settings-config` and instead provide the missing matching Settings Intelligence dependency.

For SettingsGoogle specifically, the minimal sequence is: find its actual XML definition, run the tool once without `--apply`, review the one-value diff, rerun with `--apply`, and then confirm the effective value after installation. Instead of changing the factory, you may stop selecting SettingsGoogle in the server's product/GApps files and use the supplied native `Settings` module; its base resource already selects the native factory. That choice intentionally drops Google-only Settings integrations.

The tool edits only these two selected plain `<string>` values. Missing, duplicate, unexpected, or markup-containing definitions are rejected. Existing `.d4rk-crash-fix.bak` files are never overwritten. It does not scan-and-edit the tree, modify Java/provider implementations, install APKs, alter the device, or flash anything.

If the Google override exists only inside a prebuilt APK, this XML-source tool cannot change it. Fix the product's APK selection or supply its matching dependency instead; do not decompile/re-sign a privileged APK as a substitute.

## Verification after your server build and installation

```sh
adb shell pm path org.lineageos.lineageparts
adb shell cmd overlay lookup --user 0 com.android.settings com.android.settings:string/config_featureFactory
adb shell cmd overlay lookup --user 0 com.android.systemui com.android.systemui:string/config_wallpaperPickerPackage
```

Expect an installed LineageParts path, the native factory, and `com.android.wallpaper` when using the complete product fragment. Also inspect the build output to confirm both overlay resource directories were discovered. If the final SettingsGoogle module uses only a separate vendor resource directory, the `packages/apps/Settings` source overlay will not target that directory; use the XML-alignment alternative on the module's actual `config_featureFactory` definition. If keeping the Google factory instead, omit the Settings correction and verify the matching Settings Intelligence package/provider exists. Then retest Gestures, individual App info, and the actual lock-screen long-press button. No new ROM build has been verified by this repository work.

Host-only resource-tool tests:

```sh
python3 -m unittest discover -s tools/tests -v
```
