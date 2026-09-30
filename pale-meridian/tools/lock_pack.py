#!/usr/bin/env python3
"""Resolve the pinned pack contents to exact files and write pack/lock.json.

Every entry is looked up by EXACT version number on the Modrinth API (never "latest"); the lock
records Modrinth's project/version ids, the primary file's CDN URL, size, SHA-1 and SHA-512, the
licence and the declared client/server support. The Fabric server launcher is pinned by URL, size
and SHA-256 (it is served by Fabric's official meta service and is byte-for-byte reproducible).

Usage: python3 tools/lock_pack.py            (network: api.modrinth.com, meta.fabricmc.net)
       python3 tools/lock_pack.py --check    (re-resolve and fail if anything differs from the lock)
"""
from __future__ import annotations

import hashlib
import json
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCK = ROOT / "pack" / "lock.json"
UA = {"User-Agent": "pale-meridian-lock/1.0 (github.com/bradley09roberts-glitch)"}

MINECRAFT = "26.2"
FABRIC_LOADER = "0.19.5"
FABRIC_INSTALLER = "1.1.2"
JAVA = "25"

# slug, exact version_number, role, env(client, server), why
PINS = [
    ("fabric-api", "0.161.0+26.2", "required", ("required", "required"), "Fabric API: required by the Pale Meridian mod"),
    ("lithium", "mc26.2-0.25.3-fabric", "performance", ("optional", "required"), "Server/game-logic performance (no gameplay changes)"),
    ("ferrite-core", "9.0.0-fabric", "performance", ("optional", "required"), "Memory use reduction"),
    ("sodium", "mc26.2-0.9.2-fabric", "client-performance", ("required", "unsupported"), "Rendering performance (fog and dialogs verified compatible by design; shaders not included)"),
    ("immediatelyfast", "1.16.5+26.2-fabric", "client-performance", ("optional", "unsupported"), "HUD/text rendering performance"),
    ("entityculling", "1.11.2", "client-performance", ("optional", "unsupported"), "Skips rendering hidden entities"),
    ("appleskin", "3.0.10+mc26.2", "qol", ("optional", "optional"), "Food and saturation display"),
    ("mouse-tweaks", "26.2-2.31-fabric", "client-qol", ("optional", "unsupported"), "Inventory handling"),
    ("chunky", "1.5.3", "server-tool", ("unsupported", "optional"), "Optional: pre-generate the valley on a server"),
    ("spark", "1.10.187-fabric", "server-tool", ("unsupported", "optional"), "Optional: profiler for diagnosing lag"),
]


def get(url: str):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def fetch_bytes(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
        return r.read()


def resolve() -> dict:
    mods = []
    for slug, want, role, (client, server), why in PINS:
        proj = get(f"https://api.modrinth.com/v2/project/{slug}")
        vers = get(f"https://api.modrinth.com/v2/project/{slug}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22{MINECRAFT}%22%5D")
        match = [v for v in vers if v["version_number"] == want]
        if len(match) != 1:
            raise SystemExit(f"{slug}: expected exactly one fabric/{MINECRAFT} version '{want}', found {len(match)}")
        v = match[0]
        files = [f for f in v["files"] if f["primary"]] or v["files"][:1]
        f = files[0]
        mods.append({
            "slug": slug, "title": proj["title"], "project_id": proj["id"], "version_id": v["id"], "version_number": v["version_number"],
            "version_type": v["version_type"], "date_published": v["date_published"], "license": proj["license"]["id"],
            "project_client_side": proj["client_side"], "project_server_side": proj["server_side"],
            "role": role, "env": {"client": client, "server": server}, "why": why,
            "file": {"filename": f["filename"], "url": f["url"], "size": f["size"], "sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "dependencies": sorted(({"project_id": d.get("project_id"), "version_id": d.get("version_id"), "type": d["dependency_type"]} for d in v["dependencies"]),
                                   key=lambda d: (d["project_id"] or "", d["type"])),  # API order varies
        })
        time.sleep(0.25)
    # every required dependency must itself be in the lock
    ids = {m["project_id"] for m in mods}
    for m in mods:
        for d in m["dependencies"]:
            if d["type"] == "required" and d["project_id"] and d["project_id"] not in ids:
                raise SystemExit(f"{m['slug']} requires project {d['project_id']} which is not pinned")
    loaders = get(f"https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT}")
    if not any(l["loader"]["version"] == FABRIC_LOADER for l in loaders):
        raise SystemExit(f"Fabric loader {FABRIC_LOADER} is not listed for {MINECRAFT}")
    launcher_url = f"https://meta.fabricmc.net/v2/versions/loader/{MINECRAFT}/{FABRIC_LOADER}/{FABRIC_INSTALLER}/server/jar"
    data = fetch_bytes(launcher_url)
    return {
        "schema": 1,
        "_comment": "Generated by tools/lock_pack.py. Exact, hash-verified contents of Pale Meridian. Do not edit by hand.",
        "pack": {"name": "Pale Meridian", "version": "1.0.0"},
        "minecraft": MINECRAFT,
        "java": {"major": JAVA, "note": "Java 25 or newer (the official launcher bundles it; servers need a JDK/JRE 25)"},
        "fabric": {"loader": FABRIC_LOADER, "installer": FABRIC_INSTALLER,
                   "server_launcher": {"filename": f"fabric-server-mc.{MINECRAFT}-loader.{FABRIC_LOADER}-launcher.{FABRIC_INSTALLER}.jar",
                                       "url": launcher_url, "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}},
        "mods": mods,
    }


def main() -> None:
    lock = resolve()
    if "--check" in sys.argv:
        old = json.loads(LOCK.read_text())
        strip = lambda d: json.dumps({k: v for k, v in d.items() if k != "_generated"}, sort_keys=True)
        if strip(old) != strip(lock):
            raise SystemExit("lock.json differs from what the pins resolve to today")
        print("lock.json matches the live metadata")
        return
    LOCK.parent.mkdir(parents=True, exist_ok=True)
    LOCK.write_text(json.dumps(lock, indent=1) + "\n")
    print(f"wrote {LOCK} ({len(lock['mods'])} mods)")


if __name__ == "__main__":
    main()
