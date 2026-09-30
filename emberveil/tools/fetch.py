#!/usr/bin/env python3
"""Download every locked mod into a local cache and verify SHA-512 (used for testing / inspection only;
the distributed .mrpack references Modrinth CDN URLs instead of bundling third-party jars)."""
import json, sys, hashlib, urllib.request, pathlib, os
ROOT = pathlib.Path(__file__).resolve().parents[1]
cache = pathlib.Path(os.environ.get("EMBERVEIL_CACHE", ROOT / ".cache")) / "mods"
cache.mkdir(parents=True, exist_ok=True)
UA = {"User-Agent": "emberveil-modpack/1.0"}
lock = json.loads((ROOT / "pack/mods.lock.json").read_text())
bad = 0
for m in lock["mods"]:
    p = cache / m["filename"]
    if not p.exists() or hashlib.sha512(p.read_bytes()).hexdigest() != m["sha512"]:
        with urllib.request.urlopen(urllib.request.Request(m["url"], headers=UA), timeout=120) as r:
            p.write_bytes(r.read())
    h = hashlib.sha512(p.read_bytes()).hexdigest()
    ok = h == m["sha512"] and p.stat().st_size == m["size"]
    bad += not ok
    print(("OK  " if ok else "BAD ") + m["filename"])
print(f"cache: {cache}")
sys.exit(1 if bad else 0)
