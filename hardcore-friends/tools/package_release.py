#!/usr/bin/env python3
"""Builds the release folder: the mod JAR, a CurseForge modpack import ZIP and checksums.

Usage (after ./gradlew build):  python3 tools/package_release.py

The CurseForge ZIP follows the standard modpack layout (manifest.json, modlist.html, overrides/).
Fabric API is referenced by its CurseForge project and file ids, so the CurseForge app downloads the
official file itself. Hardcore Friends is not on CurseForge, so its JAR ships in overrides/mods/.
"""
from __future__ import annotations

import hashlib
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RELEASE = ROOT / "release"

MINECRAFT = "26.3"
FABRIC_LOADER = "0.19.5"
# Verified 2026-10-06: CurseForge "Fabric API 0.162.0+26.3" (project 306612, file 9078180) has the same
# SHA-1 as the official Modrinth file: 273cd2dcbd92d1559edcc91c9f23fee47f6ff93f.
FABRIC_API_PROJECT = 306612
FABRIC_API_FILE = 9078180
FABRIC_API_NAME = "Fabric API 0.162.0+26.3"


def mod_version() -> str:
    props = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    return re.search(r"^mod_version=(.+)$", props, re.M).group(1).strip()


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    version = mod_version()
    jar = ROOT / "build" / "libs" / f"hardcore-friends-{version}.jar"
    if not jar.exists():
        raise SystemExit(f"Missing {jar}; run ./gradlew build first")
    RELEASE.mkdir(exist_ok=True)
    out_jar = RELEASE / jar.name
    shutil.copy2(jar, out_jar)

    manifest = {
        "minecraft": {
            "version": MINECRAFT,
            "modLoaders": [{"id": f"fabric-{FABRIC_LOADER}", "primary": True}],
        },
        "manifestType": "minecraftModpack",
        "manifestVersion": 1,
        "name": "Hardcore Friends",
        "version": version,
        "author": "bradley09roberts",
        "files": [{"projectID": FABRIC_API_PROJECT, "fileID": FABRIC_API_FILE, "required": True}],
        "overrides": "overrides",
    }
    modlist = (
        "<ul>\n"
        f'<li><a href="https://www.curseforge.com/minecraft/mc-mods/fabric-api">{FABRIC_API_NAME}</a> (required)</li>\n'
        f"<li>Hardcore Friends {version} (included in overrides/mods)</li>\n"
        "</ul>\n"
    )
    zip_path = RELEASE / f"Hardcore-Friends-{version}-CurseForge.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", json.dumps(manifest, indent=2) + "\n")
        z.writestr("modlist.html", modlist)
        z.write(out_jar, f"overrides/mods/{out_jar.name}")

    sums = [f"{sha256(p)}  {p.name}" for p in (out_jar, zip_path)]
    (RELEASE / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")
    print("\n".join(sums))


if __name__ == "__main__":
    main()
