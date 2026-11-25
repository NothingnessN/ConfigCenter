#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Align explicitly selected ROM XML overrides; dry-run unless --apply is set."""

import argparse
import difflib
import os
from pathlib import Path
import re
import shutil
import stat
import sys
import tempfile
import xml.etree.ElementTree as ET


FIXES = {
    "settings": (
        "config_featureFactory",
        "com.google.android.settings.overlay.FeatureFactoryImpl",
        "com.android.settings.overlay.FeatureFactoryImpl",
    ),
    "systemui": (
        "config_wallpaperPickerPackage",
        "com.google.android.apps.wallpaper",
        "com.android.wallpaper",
    ),
}

STRING = re.compile(
    r"<string\b(?P<attrs>(?:[^<>\"']|\"[^\"]*\"|'[^']*')*)>"
    r"(?P<value>[^<]*)</string\s*>",
    re.DOTALL,
)
NAME = re.compile(r"(?:^|\s)name\s*=\s*(['\"])(?P<name>[^'\"]+)\1")
BACKUP_SUFFIX = ".d4rk-crash-fix.bak"


def align_resources(text, fixes):
    """Replace only the selected plain string values, preserving other XML bytes."""
    root = ET.fromstring(text)
    if root.tag != "resources":
        raise ValueError("Expected an Android <resources> XML file")
    searchable = re.sub(r"<!--.*?-->", lambda match: " " * len(match[0]), text,
                        flags=re.DOTALL)
    replacements = []
    for name, previous, desired in fixes:
        entries = [entry for entry in root.findall("string")
                   if entry.get("name") == name]
        if len(entries) != 1:
            raise ValueError(f"Expected exactly one <string name=\"{name}\"> definition")
        entry = entries[0]
        if len(entry):
            raise ValueError(f"{name} must be a plain-text configuration string")
        current = (entry.text or "").strip()
        if current not in (previous, desired):
            raise ValueError(f"Unexpected {name} value: {current!r}; not changing it")
        if current == desired:
            continue
        matches = []
        for match in STRING.finditer(searchable):
            attribute = NAME.search(match["attrs"])
            if attribute and attribute["name"] == name:
                matches.append(match)
        if len(matches) != 1:
            raise ValueError(f"Cannot locate a unique plain-text {name} definition")
        match = matches[0]
        start, end = match.span("value")
        value = text[start:end]
        if "<" in value:
            raise ValueError(f"{name} contains markup; not rewriting it")
        leading = value[:len(value) - len(value.lstrip())]
        trailing = value[len(value.rstrip()):]
        replacements.append((start, end, leading + desired + trailing))
    result = text
    for start, end, value in sorted(replacements, reverse=True):
        result = result[:start] + value + result[end:]
    ET.fromstring(result)
    return result


def plan_changes(requested):
    plans = []
    for path, fixes in requested.items():
        original = path.read_bytes()
        updated = align_resources(original.decode("utf-8"), fixes).encode("utf-8")
        if original != updated:
            plans.append((path, original, updated))
    return plans


def apply_changes(plans):
    """Keep exclusive backups and replace files only after every plan is validated."""
    for path, original, _ in plans:
        backup = path.with_name(path.name + BACKUP_SUFFIX)
        if backup.exists() or backup.is_symlink():
            raise ValueError(f"Backup already exists: {backup}; not overwriting it")
        if path.read_bytes() != original:
            raise ValueError(f"Source changed since validation: {path}")
    temporary_paths = []
    try:
        for path, _, updated in plans:
            with tempfile.NamedTemporaryFile(dir=path.parent,
                                             prefix=".d4rk-crash-fix-",
                                             delete=False) as handle:
                temporary = Path(handle.name)
                temporary_paths.append(temporary)
                handle.write(updated)
            os.chmod(temporary, stat.S_IMODE(path.stat().st_mode))
        for path, original, _ in plans:
            if path.read_bytes() != original:
                raise ValueError(f"Source changed since validation: {path}")
            backup = path.with_name(path.name + BACKUP_SUFFIX)
            with backup.open("xb") as handle:
                handle.write(original)
            shutil.copystat(path, backup)
        for (path, original, _), temporary in zip(plans, temporary_paths):
            if path.read_bytes() != original:
                raise ValueError(f"Source changed since validation: {path}")
            os.replace(temporary, path)
    finally:
        for temporary in temporary_paths:
            temporary.unlink(missing_ok=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--settings-config", type=Path,
        help="Opt in to the native Settings factory in this XML override. "
             "Google-only Settings additions will no longer be selected.",
    )
    parser.add_argument(
        "--systemui-config", type=Path,
        help="Set this XML override's wallpaper picker to the installed com.android.wallpaper.",
    )
    parser.add_argument("--apply", action="store_true",
                        help="Write the displayed changes, keeping exclusive .bak backups.")
    args = parser.parse_args(argv)
    if not args.settings_config and not args.systemui_config:
        parser.error("Select --settings-config and/or --systemui-config")
    try:
        requested = {}
        for kind, path in (("settings", args.settings_config),
                           ("systemui", args.systemui_config)):
            if path is not None:
                path = path.resolve(strict=True)
                requested.setdefault(path, []).append(FIXES[kind])
        plans = plan_changes(requested)
        if not plans:
            print("Selected native resource values are already correct; no files changed.")
            return 0
        for path, original, updated in plans:
            sys.stdout.write("".join(difflib.unified_diff(
                original.decode("utf-8").splitlines(keepends=True),
                updated.decode("utf-8").splitlines(keepends=True),
                fromfile=str(path), tofile=str(path),
            )))
        if args.apply:
            apply_changes(plans)
            for path, _, _ in plans:
                print(f"Updated {path}; backup: {path.name}{BACKUP_SUFFIX}")
        else:
            print("Dry run only. Use --apply to write these changes with backups.")
    except (OSError, UnicodeError, ValueError, ET.ParseError) as error:
        parser.exit(2, f"error: {error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
