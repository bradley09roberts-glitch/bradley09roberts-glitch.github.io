#!/usr/bin/env python3
"""Build the release artifacts from pack/lock.json and the built mod jar.

Outputs (dist/):
  PaleMeridian-<ver>.mrpack             client pack (Modrinth format 1): pinned mods by CDN URL + hashes,
                                         the Pale Meridian mod in overrides/mods
  PaleMeridian-Server-<ver>.zip         server package: install/start/backup/update scripts (Windows + Linux),
                                         the Pale Meridian mod, server-files.tsv (pinned downloads), lock.json
  SHA256SUMS.txt                        checksums of the above

Nothing restricted is bundled: Minecraft itself and third-party mods are downloaded by the user's
launcher or by the server installer, from their official sources, and verified by hash.

Usage: (cd mod && ./gradlew build) && python3 tools/build_dist.py
"""
from __future__ import annotations

import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCK = json.loads((ROOT / "pack" / "lock.json").read_text())
VER = LOCK["pack"]["version"]
JAR = ROOT / "mod" / "build" / "libs" / f"palemeridian-{VER}.jar"
DIST = ROOT / "dist"
ZIP_TIME = (2026, 1, 1, 0, 0, 0)       # fixed timestamps: reproducible archives


def _add(z: zipfile.ZipFile, arcname: str, data: bytes, executable: bool = False) -> None:
    info = zipfile.ZipInfo(arcname, date_time=ZIP_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = (0o100755 if executable else 0o100644) << 16
    z.writestr(info, data)


def _crlf(text: str) -> bytes:
    return text.replace("\r\n", "\n").replace("\n", "\r\n").encode("ascii")


def mrpack() -> Path:
    files = []
    for m in LOCK["mods"]:
        f = m["file"]
        files.append({
            "path": f"mods/{f['filename']}",
            "hashes": {"sha1": f["sha1"], "sha512": f["sha512"]},
            "env": {"client": m["env"]["client"], "server": m["env"]["server"]},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": VER,
        "name": "Pale Meridian",
        "summary": "A story campaign for Minecraft Java 26.2: chart what the fog forgot.",
        "files": files,
        "dependencies": {"minecraft": LOCK["minecraft"], "fabric-loader": LOCK["fabric"]["loader"]},
    }
    out = DIST / f"PaleMeridian-{VER}.mrpack"
    with zipfile.ZipFile(out, "w") as z:
        _add(z, "modrinth.index.json", (json.dumps(index, indent=1) + "\n").encode())
        _add(z, f"overrides/mods/{JAR.name}", JAR.read_bytes())
    return out


def server_zip() -> Path:
    rows = ["# kind\tfilename\turl\thash-algorithm\thash   (generated from pack/lock.json; do not edit)"]
    sl = LOCK["fabric"]["server_launcher"]
    rows.append(f"launcher\t{sl['filename']}\t{sl['url']}\tsha256\t{sl['sha256']}")
    for m in LOCK["mods"]:
        srv = m["env"]["server"]
        if srv == "unsupported":
            continue
        kind = "tool" if m["role"] == "server-tool" else "mod"
        f = m["file"]
        rows.append(f"{kind}\t{f['filename']}\t{f['url']}\tsha512\t{f['sha512']}")
    tsv = "\n".join(rows) + "\n"
    out = DIST / f"PaleMeridian-Server-{VER}.zip"
    base = f"PaleMeridian-Server-{VER}/"
    sdir = ROOT / "server"
    with zipfile.ZipFile(out, "w") as z:
        for p in sorted(sdir.iterdir()):
            if p.suffix == ".bat":
                _add(z, base + p.name, _crlf(p.read_text()))
            elif p.suffix == ".sh":
                _add(z, base + p.name, p.read_bytes(), executable=True)
            else:
                _add(z, base + p.name, p.read_bytes())
        _add(z, base + "server-files.tsv", tsv.encode())
        _add(z, base + "lock.json", (ROOT / "pack" / "lock.json").read_bytes())
        _add(z, base + "SERVER_GUIDE.md", (ROOT / "docs" / "SERVER_GUIDE.md").read_bytes())
        _add(z, base + "ATTRIBUTION.md", (ROOT / "docs" / "ATTRIBUTION.md").read_bytes())
        _add(z, base + f"mods/{JAR.name}", JAR.read_bytes())
    return out


def main() -> None:
    if not JAR.exists():
        raise SystemExit(f"{JAR} not found: run (cd mod && ./gradlew build) first")
    DIST.mkdir(exist_ok=True)
    outs = [mrpack(), server_zip()]
    sums = []
    for o in outs + [JAR]:
        sums.append(f"{hashlib.sha256(o.read_bytes()).hexdigest()}  {o.name}")
    (DIST / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n")
    for s in sums:
        print(s)


if __name__ == "__main__":
    main()
