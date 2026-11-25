#!/usr/bin/env python3
"""Fetch selected, commit-pinned Android 13 sources; never fetch full archives."""

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import json
from pathlib import Path
import urllib.request


PINS = {
    "android_frameworks_base": "736450e6c566fcba5dcae95db1b0d4529bdc14ae",
    "android_lineage-sdk": "f43bdfb252798127ed36a711e43621da15da29c7",
    "android_vendor_support": "567edc76fa3274b2745aff9066d127159a9e8678",
}

FRAMEWORK_NAMES = {
    "Settings.java", "SystemSettingsValidators.java", "SecureSettingsValidators.java",
    "GlobalSettingsValidators.java", "PhoneWindowManager.java", "PowerManagerService.java",
    "MediaSessionService.java", "AudioService.java", "InputMethodService.java",
    "CutoutFullscreenController.java", "ClockController.java", "Clock.java",
    "BatteryMeterView.java", "BatteryMeterViewController.java", "CircleBatteryDrawable.kt",
    "ThemedBatteryDrawable.kt", "AccessorizedBatteryDrawable.kt", "BatteryController.java",
    "NetworkTraffic.java", "NetworkTrafficSB.java", "TunerService.java", "TunerServiceImpl.java",
    "PhoneStatusBarView.java", "PhoneStatusBarViewController.kt", "TileLayout.java",
    "PagedTileLayout.java", "QuickQSPanel.java", "QuickQSPanelController.java",
    "QSPanelControllerBase.java", "QSPanelController.java", "QSPanel.java",
    "QSTileViewImpl.kt", "QuickStatusBarHeader.java", "QSFooterView.java",
    "QSFooterViewController.java", "NotificationInterruptStateProviderImpl.java",
    "HeadsUpManager.java", "HeadsUpManagerPhone.java", "KeyguardIndicationController.java",
    "KeyguardUpdateMonitor.java", "KeyguardStatusViewController.java", "KeyButtonView.java",
    "StatusBarManagerService.java", "StatusBarManagerInternal.java", "IStatusBarService.aidl",
    "CentralSurfaces.java", "CentralSurfacesImpl.java", "CollapsedStatusBarFragment.java",
    "NotificationManagerService.java", "NotificationManagerInternal.java", "StatusBar.java",
    "CommandQueue.java", "BrightnessController.java", "KeyguardStatusBarView.java",
    "AssistManager.java", "Android.bp", "AndroidManifest.xml", "status_bar.xml",
    "AbsSeekBar.java", "WindowState.java", "WindowManagerService.java",
    "NotificationPanelViewController.java", "StatusBarWindowController.java",
    "VolumeDialogControllerImpl.java", "VolumeDialogController.java",
    "FlashlightController.java", "FlashlightControllerImpl.java", "ActionUtils.java",
    "QuickStatusBarHeaderController.java", "NavigationBarInflaterView.java",
    "NavigationBarView.java", "KeyguardViewMediator.java", "PhoneStatusBarPolicy.java",
    "StackScrollAlgorithm.java", "NotificationStackScrollLayout.java",
    "DisplayPolicy.java", "DisplayContent.java", "DisplayFrames.java",
    "LetterboxUiController.java", "WindowLayout.java", "QuickSettingsController.java",
    "KeyguardStatusBarViewController.java", "Grids.kt",
    "ActivityManagerService.java", "ActivityTaskManagerService.java", "PhoneWindow.java",
    "IActivityManager.aidl",
    "ApplicationsState.java", "ObservableFragment.java",
}


def download(url):
    request = urllib.request.Request(url, headers={"User-Agent": "D4RK-source-audit"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def fetch_repo(name, sha, output):
    repo = "Havoc-OS/" + name
    root = output / name
    root.mkdir(parents=True, exist_ok=True)
    index_path = root / "tree-index.json"
    if index_path.exists():
        index = json.loads(index_path.read_text())
        if index["sha"] != sha:
            raise ValueError(f"Wrong revision in {index_path}")
    else:
        data = download(f"https://api.github.com/repos/{repo}/git/trees/{sha}?recursive=1")
        index = json.loads(data)
        if index.get("truncated") or index["sha"] != sha:
            raise ValueError(f"Incomplete or wrong tree for {repo}")
        index_path.write_bytes(data)

    paths = []
    for entry in index["tree"]:
        path = entry["path"]
        if entry["type"] != "blob" or "/tests/" in path or path.startswith("tests/"):
            continue
        if name == "android_frameworks_base":
            selected = (
                (Path(path).name in FRAMEWORK_NAMES and path.startswith((
                    "core/", "services/", "packages/SystemUI/", "packages/SettingsLib/",
                    "packages/SettingsProvider/")))
                or "SmartPixels" in path
                or "AdaptivePlayback" in path
                or path.startswith("core/res/res/values/havoc_")
                or (path.startswith("packages/SettingsLib/")
                    and "/res/values/" in path and path.endswith(".xml"))
                or path in {"core/res/res/values/config.xml", "core/res/res/values/symbols.xml",
                            "packages/SystemUI/res/values/dimens.xml",
                            "packages/SystemUI/res/values/config.xml"}
            )
        else:
            selected = path.endswith((".java", ".xml", ".bp", ".flags", ".txt"))
        if selected:
            paths.append(path)

    manifest_path = root / "manifest.json"
    manifest = {"repository": repo, "sha": sha, "files": {}, "errors": {}}
    if manifest_path.exists():
        manifest = json.loads(manifest_path.read_text())

    def save(path):
        file = root / "tree" / path
        if file.exists():
            data = file.read_bytes()
            known = manifest["files"].get(path)
            if known and hashlib.sha256(data).hexdigest() != known:
                raise ValueError(f"Modified reference: {file}")
        else:
            data = download(f"https://raw.githubusercontent.com/{repo}/{sha}/{path}")
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(data)
        return path, hashlib.sha256(data).hexdigest()

    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(save, path): path for path in paths}
        for future in as_completed(futures):
            path = futures[future]
            try:
                path, digest = future.result()
                manifest["files"][path] = digest
                manifest["errors"].pop(path, None)
            except Exception as error:
                manifest["errors"][path] = str(error)
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    print(f"{repo}@{sha}: {len(manifest['files'])} files, "
          f"{len(manifest['errors'])} errors", flush=True)
    if manifest["errors"]:
        raise RuntimeError(json.dumps(manifest["errors"], indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("/tmp/opencode/d4rk-references"))
    parser.add_argument("--repo", choices=PINS, action="append")
    args = parser.parse_args()
    for name in args.repo or PINS:
        fetch_repo(name, PINS[name], args.output)


if __name__ == "__main__":
    main()
