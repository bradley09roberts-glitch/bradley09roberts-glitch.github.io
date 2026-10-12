#!/usr/bin/env python3
"""Packs the Grand Line Shaders source folder into an installable Iris/OptiFine shader pack zip.

    python3 tools/pack_shaders.py            -> dist/GrandLineShaders-1.0.0.zip

The zip root holds shaders/ (plus README.md and CREDITS.md), which is the layout Iris expects. Before writing, the
script checks that every dimension folder named in shaders/dimension.properties exists and holds the core programs.
Pure Python (zipfile); entries get a fixed timestamp so the same sources give the same zip.
"""
import re
import sys
import zipfile
from pathlib import Path

VERSION = "1.0.0"
MOD = Path(__file__).resolve().parent.parent
PACK = MOD / "shaderpack" / "GrandLineShaders"
SHADERS = PACK / "shaders"
OUT = MOD / "dist" / f"GrandLineShaders-{VERSION}.zip"
FIXED_TIME = (2026, 1, 1, 0, 0, 0)
CORE_PROGRAMS = ["gbuffers_terrain", "gbuffers_water", "gbuffers_entities", "gbuffers_textured", "deferred", "composite", "final"]


def dimension_folders():
    folders = []
    for line in (SHADERS / "dimension.properties").read_text().splitlines():
        m = re.match(r"^\s*dimension\.([\w-]+)\s*=", line)
        if m:
            folders.append(m.group(1))
    return folders


def check():
    problems = []
    for folder in dimension_folders():
        d = SHADERS / folder
        if not d.is_dir():
            problems.append(f"dimension folder {folder} is missing")
            continue
        for prog in CORE_PROGRAMS:
            for ext in ("vsh", "fsh"):
                if not (d / f"{prog}.{ext}").is_file():
                    problems.append(f"{folder}/{prog}.{ext} is missing")
    for required in ("shaders.properties", "block.properties", "dimension.properties", "lang/en_us.lang"):
        if not (SHADERS / required).is_file():
            problems.append(f"shaders/{required} is missing")
    return problems


def main():
    problems = check()
    if problems:
        print("not packed:\n  " + "\n  ".join(problems))
        return 1
    files = [p for p in sorted(SHADERS.rglob("*")) if p.is_file()]
    extras = [PACK / name for name in ("README.md", "CREDITS.md") if (PACK / name).is_file()]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for p in extras + files:
            arc = p.relative_to(PACK).as_posix()
            info = zipfile.ZipInfo(arc, FIXED_TIME)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, p.read_bytes())
    print(f"wrote {OUT.relative_to(MOD)} ({len(files) + len(extras)} files, {OUT.stat().st_size // 1024} KiB)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
