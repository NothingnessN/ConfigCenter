# Install the D4RK OS Android 13 crash fixes

This guide prepares source changes on the ROM build server. It does not claim that a ROM has been built or that the fixes have passed device testing.

## What the three fixes do

1. **ConfigCenter → Gestures:** installs the compatible LineageParts package and catalog containing `touchscreen_gesture_settings`. This addresses the Settings preference-inflation crash.
2. **Individual App info:** selects the native `com.android.settings.overlay.FeatureFactoryImpl` when Google Settings Intelligence is absent. This prevents SettingsGoogle from selecting its missing Google battery-history provider. If SettingsGoogle defines the resource in its own vendor directory, that final definition must be aligned separately.
3. **Lock-screen long-press button:** sets SystemUI's `config_wallpaperPickerPackage` to the installed `com.android.wallpaper` ThemePicker instead of the absent Google Wallpaper package.

No ConfigCenter controls, setting providers, fragment names, branding, logo, credits, or links are changed.

## 1. Clone ConfigCenter's integration branch

Run on the build server:

```sh
cd ~/havoc
test ! -e packages/apps/ConfigCenter
git clone --branch d4rk-v3-rom-integration --single-branch \
    https://github.com/NothingnessN/ConfigCenter.git \
    packages/apps/ConfigCenter
```

If `packages/apps/ConfigCenter` already exists, preserve it and update it deliberately rather than deleting it. The required source is branch `d4rk-v3-rom-integration`.

## 2. Apply Settings patch 0004

Patch `0004` follows the existing Settings patches `0001`–`0003`:

```sh
cd ~/havoc
CONFIG_CENTER="$PWD/packages/apps/ConfigCenter"

git -C packages/apps/Settings apply --check \
    "$CONFIG_CENTER/patches/settings/0004-Settings-require-LineageParts.patch"
git -C packages/apps/Settings apply \
    "$CONFIG_CENTER/patches/settings/0004-Settings-require-LineageParts.patch"
```

## 3. Place the pinned LineageParts source

LineageParts remains a separate upstream project; it is not copied into the ConfigCenter repository.

```sh
cd ~/havoc
test ! -e packages/apps/LineageParts
git clone --branch thirteen --single-branch \
    https://github.com/Havoc-OS/android_packages_apps_LineageParts.git \
    packages/apps/LineageParts
git -C packages/apps/LineageParts checkout --detach \
    6bb38ac07319582679525fb20fc4af16a3dbba15
git -C packages/apps/LineageParts rev-parse HEAD
```

Expected commit:

```text
6bb38ac07319582679525fb20fc4af16a3dbba15
```

Do not replace it with the newer upstream `lineage-20.0` head.

## 4. Add the product include

Open the top-level product makefile that already selects the ROM and GApps configuration:

```sh
PRODUCT_MK="/path/to/havoc/top-level-product.mk"
${EDITOR:-vi} "$PRODUCT_MK"
```

Add this direct include **after** the GApps/product configuration:

```make
include packages/apps/ConfigCenter/product/d4rk_crash_fixes.mk
```

Do not use `inherit-product` for this line. The direct include ensures the corrective product overlay precedes inherited overlay paths.

## 5. Verify build variables after `lunch`

```sh
cd ~/havoc
source build/envsetup.sh
lunch your_existing_on7xelte_target

get_build_var PRODUCT_PACKAGE_OVERLAYS
get_build_var PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS
get_build_var PRODUCT_PACKAGES | tr ' ' '\n' | grep -x LineageParts
```

Before building, verify:

- `packages/apps/ConfigCenter/product/overlay` is the first matching product-overlay root.
- The same path is present in `PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS`.
- The final command prints `LineageParts`.

## 6. Handle a separate SettingsGoogle factory resource

The base Android 13 Settings source already uses the native factory. SettingsGoogle may override it from a separate vendor resource directory, which the `packages/apps/Settings` overlay cannot target.

Find the final definition and SettingsGoogle module files:

```sh
cd ~/havoc
rg -n --glob='*.xml' 'config_featureFactory' packages vendor device
rg -n 'SettingsGoogle|overrides:[[:space:]]*\[?[[:space:]]*"Settings"' \
    packages vendor device
```

If the final SettingsGoogle XML still contains `com.google.android.settings.overlay.FeatureFactoryImpl`, preview the guarded one-value change first:

```sh
SETTINGS_OVERRIDE_XML="/path/to/the/actual/SettingsGoogle/config.xml"

python3 packages/apps/ConfigCenter/tools/fix_rom_crash_resources.py \
    --settings-config "$SETTINGS_OVERRIDE_XML"
```

Review the diff. Only then apply it:

```sh
python3 packages/apps/ConfigCenter/tools/fix_rom_crash_resources.py \
    --settings-config "$SETTINGS_OVERRIDE_XML" --apply
```

The tool creates a `.d4rk-crash-fix.bak` backup and refuses unknown, duplicate, or markup-containing definitions.

**Alternative:** stop selecting the SettingsGoogle replacement in the server's product/GApps configuration and use the supplied native `Settings` module. Native Settings already selects `com.android.settings.overlay.FeatureFactoryImpl`. This avoids the missing Settings Intelligence dependency but intentionally removes Google-only Settings integrations. The exact module-selection file exists only on the server and was not available for local editing.

## 7. Build and flash using your normal server process

No generic build or flashing command is provided here because the device/product invocation belongs to the server configuration. Review the source changes, then use the project's established build and installation procedure.

## 8. Verify after the new build is installed

```sh
adb shell pm path org.lineageos.lineageparts
adb shell cmd overlay lookup --user 0 \
    com.android.settings com.android.settings:string/config_featureFactory
adb shell cmd overlay lookup --user 0 \
    com.android.systemui com.android.systemui:string/config_wallpaperPickerPackage
adb shell cmd package resolve-activity --brief \
    -a android.intent.action.SET_WALLPAPER -p com.android.wallpaper
```

Expected results:

- LineageParts has an installed APK path.
- Settings reports `com.android.settings.overlay.FeatureFactoryImpl`.
- SystemUI reports `com.android.wallpaper`.
- `ACTION_SET_WALLPAPER` resolves to the installed ThemePicker.

Then manually retest:

1. ConfigCenter → Gestures.
2. Individual App info.
3. The actual lock-screen long-press settings button.

## Not verified by this repository work

- No ROM was built.
- No APK or ROM was installed.
- Nothing was flashed.
- The build-server product and GApps files were not available for inspection.
- The resulting behavior has not been tested on a device.
