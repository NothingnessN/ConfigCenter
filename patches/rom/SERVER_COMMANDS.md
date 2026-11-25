# Server checklist

Nothing in this checklist has been run on the build server. Replace every placeholder path and target before running it.

## 1. Clone the prepared integration branch

```sh
cd ~/havoc
test ! -e packages/apps/ConfigCenter
git clone --branch d4rk-v3-rom-integration --single-branch \
    https://github.com/NothingnessN/ConfigCenter.git \
    packages/apps/ConfigCenter
```

If that destination already exists, inspect and preserve it instead of overwriting it; use the integration branch as the source of the new files.

## 2. Apply the fourth Settings patch

```sh
cd ~/havoc
CONFIG_CENTER="$PWD/packages/apps/ConfigCenter"

git -C packages/apps/Settings apply --check \
    "$CONFIG_CENTER/patches/settings/0004-Settings-require-LineageParts.patch"
git -C packages/apps/Settings apply \
    "$CONFIG_CENTER/patches/settings/0004-Settings-require-LineageParts.patch"
```

This patch is intended to follow the existing `0001`–`0003` Settings patches.

## 3. Place the pinned LineageParts source

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

The final command must print `6bb38ac07319582679525fb20fc4af16a3dbba15`.

## 4. Include the product integration

Open the top-level product makefile that already inherits the ROM and GApps configuration:

```sh
PRODUCT_MK="/path/to/havoc/top-level-product.mk"
${EDITOR:-vi} "$PRODUCT_MK"
```

Add this line **after** the GApps/product configuration:

```make
include packages/apps/ConfigCenter/product/d4rk_crash_fixes.mk
```

Use a direct `include`; do not replace it with `inherit-product`.

## 5. Check the product after `lunch`

```sh
cd ~/havoc
source build/envsetup.sh
lunch your_existing_on7xelte_target

get_build_var PRODUCT_PACKAGE_OVERLAYS
get_build_var PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS
get_build_var PRODUCT_PACKAGES | tr ' ' '\n' | grep -x LineageParts
```

Expected before building:

- `packages/apps/ConfigCenter/product/overlay` is the first matching product-overlay root.
- The same path appears in `PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS`.
- The last command prints `LineageParts`.

## 6. SettingsGoogle risk check

Find every real definition, including definitions in a separate SettingsGoogle vendor resource directory:

```sh
cd ~/havoc
rg -n --glob='*.xml' 'config_featureFactory' packages vendor device
rg -n 'SettingsGoogle|overrides:[[:space:]]*\[?[[:space:]]*"Settings"' packages vendor device
```

If SettingsGoogle has its own final XML definition, preview and then apply the native factory directly to that file:

```sh
SETTINGS_OVERRIDE_XML="/path/to/the/actual/SettingsGoogle/config.xml"

python3 packages/apps/ConfigCenter/tools/fix_rom_crash_resources.py \
    --settings-config "$SETTINGS_OVERRIDE_XML"
python3 packages/apps/ConfigCenter/tools/fix_rom_crash_resources.py \
    --settings-config "$SETTINGS_OVERRIDE_XML" --apply
```

Review the displayed diff before `--apply`. The tool accepts only the confirmed Google value or the already-correct native value, and preserves a `.d4rk-crash-fix.bak` backup.

Alternatively, stop selecting the `SettingsGoogle` replacement in the product/GApps configuration and build the supplied native `Settings` module. Native Settings already uses `com.android.settings.overlay.FeatureFactoryImpl`; this avoids the missing Google Settings Intelligence dependency but intentionally drops Google-only Settings integrations. The exact module-selection edit must be made in the server's GApps/product files, which are not available locally.

## 7. Verify after your normal build, installation, and boot

```sh
adb shell pm path org.lineageos.lineageparts
adb shell cmd overlay lookup --user 0 \
    com.android.settings com.android.settings:string/config_featureFactory
adb shell cmd overlay lookup --user 0 \
    com.android.systemui com.android.systemui:string/config_wallpaperPickerPackage
adb shell cmd package resolve-activity --brief \
    -a android.intent.action.SET_WALLPAPER -p com.android.wallpaper
```

Expected:

- `pm path` returns a LineageParts APK path.
- Settings reports `com.android.settings.overlay.FeatureFactoryImpl`.
- SystemUI reports `com.android.wallpaper`.
- The wallpaper action resolves to the installed ThemePicker.

Finally retest ConfigCenter → Gestures, individual App info, and the actual lock-screen long-press button. A successful local test run does not replace this post-build device verification.
