#!/usr/bin/env python3
"""Merges every fragment in tools/lang/*.json and tools/assets/lang/*.json into
src/main/resources/assets/squidgame/lang/en_us.json (sorted, deterministic).
Later files win on duplicate keys; run after editing any fragment."""
import glob
import json
import os

here = os.path.dirname(os.path.abspath(__file__))
out = {}
files = sorted(glob.glob(os.path.join(here, "lang", "*.json"))) + sorted(glob.glob(os.path.join(here, "assets", "lang", "*.json")))
for f in files:
    with open(f, encoding="utf-8") as fh:
        out.update(json.load(fh))
dest = os.path.join(here, "..", "src", "main", "resources", "assets", "squidgame", "lang", "en_us.json")
os.makedirs(os.path.dirname(dest), exist_ok=True)
with open(dest, "w", encoding="utf-8") as fh:
    json.dump(dict(sorted(out.items())), fh, indent=2, ensure_ascii=False)
    fh.write("\n")
print("merged %d keys from %d fragment(s) -> %s" % (len(out), len(files), os.path.normpath(dest)))
