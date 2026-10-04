#!/usr/bin/env python3
"""Validates the standing markers of a .sqbuf dump: every marker is a position an entity will stand on, so the block
below it must be solid (not air / not a thin decoration) and the two blocks at/above must be free.

  python3 tools/check_markers.py tools/out/red_light.sqbuf [--skip REGEX] [--only REGEX] [--verbose]

Markers whose names match the built-in non-standing list (prize.*, *.tree, *.rope*, *.board, *.target, *.table,
control.*, guard.patrol, camera.*) are skipped; a marker with data `stand=0` is skipped too.
Exit code 1 if any standing marker is invalid.
"""
import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np  # noqa: E402
from preview import load, parse_meta  # noqa: E402

NON_STANDING = re.compile(r"^(prize\.|.*\.tree$|.*rope.*|.*\.board$|.*\.target$|.*\.table$|control\.|guard\.patrol|camera\.|.*\.station$|.*hanging.*)")
AIR = ("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:light", "minecraft:barrier", "squidgame:invisible_wall")
THIN = ("carpet", "pressure_plate", "button", "torch", "lantern", "sign", "banner", "rail", "string", "tripwire", "snow", "pane", "bars", "chain", "ladder", "vine", "flower", "grass", "fern")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("file")
    ap.add_argument("--skip", default=None)
    ap.add_argument("--only", default=None)
    ap.add_argument("--verbose", action="store_true")
    a = ap.parse_args()
    palette, secs, meta = load(a.file)
    # sparse lookup
    sections = {(sx, sy, sz): arr for sx, sy, sz, arr in secs}

    def block(x, y, z):
        arr = sections.get((x >> 4, y >> 4, z >> 4))
        if arr is None:
            return None
        i = arr[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]
        return None if i == 0 else palette[i]

    def is_air(st):
        return st is None or st.split("[")[0] in AIR

    def is_floor(st):
        if is_air(st):
            return False
        b = st.split("[")[0]
        return not any(t in b for t in THIN)

    markers, regions, _ = parse_meta(meta)
    raw = {}
    for line in meta:
        p = line.split("\t")
        if p[0] == "M":
            raw.setdefault(p[1], []).append(p)
    bad = {}
    checked = 0
    for name, items in raw.items():
        if NON_STANDING.match(name) or (a.skip and re.search(a.skip, name)) or (a.only and not re.search(a.only, name)):
            continue
        for p in items:
            data = p[6] if len(p) > 6 else ""
            if "stand=0" in data:
                continue
            x, y, z = float(p[2]), float(p[3]), float(p[4])
            bx, by, bz = int(np.floor(x)), int(np.floor(y + 1e-6)), int(np.floor(z))
            checked += 1
            below = block(bx, by - 1, bz)
            feet = block(bx, by, bz)
            head = block(bx, by + 1, bz)
            problems = []
            if not is_floor(below):
                problems.append("no solid floor below (%s)" % below)
            if not is_air(feet) and not any(t in (feet or "") for t in THIN):
                problems.append("feet blocked by %s" % feet)
            if not is_air(head):
                problems.append("head blocked by %s" % head)
            if problems:
                bad.setdefault(name, []).append(((bx, by, bz), problems))
    print("checked %d standing markers in %d marker kinds" % (checked, len(raw)))
    for name, lst in sorted(bad.items()):
        print("  %-28s %d invalid, e.g. %s: %s" % (name, len(lst), lst[0][0], "; ".join(lst[0][1])))
        if a.verbose:
            for pos, pr in lst[:40]:
                print("      ", pos, "; ".join(pr))
    if bad:
        sys.exit(1)
    print("all standing markers valid")


if __name__ == "__main__":
    main()
