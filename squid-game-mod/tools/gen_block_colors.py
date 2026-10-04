#!/usr/bin/env python3
"""Builds tools/block_colors.json: average colour per vanilla block (from the extracted client
textures) used by preview.py. Custom squidgame blocks are resolved at preview time from
src/main/resources/assets/squidgame/textures/block/.

Usage: gen_block_colors.py <path to extracted assets/minecraft dir>
"""
import json
import os
import re
import sys
from PIL import Image
import numpy as np

SUFFIXES = ["_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_button", "_pressure_plate",
            "_trapdoor", "_door", "_wall_sign", "_sign", "_hanging_sign", "_wall_hanging_sign",
            "_carpet", "_pane", "_wall_banner", "_banner", "_bed", "_candle", "_shulker_box", "_glazed_terracotta"]
TINT = {"grass_block": (0x79, 0xC0, 0x5A), "grass": (0x79, 0xC0, 0x5A), "short_grass": (0x79, 0xC0, 0x5A),
        "tall_grass": (0x79, 0xC0, 0x5A), "fern": (0x79, 0xC0, 0x5A), "oak_leaves": (0x48, 0xB5, 0x18),
        "birch_leaves": (0x80, 0xA7, 0x55), "spruce_leaves": (0x61, 0x99, 0x61), "jungle_leaves": (0x48, 0xB5, 0x18),
        "acacia_leaves": (0x48, 0xB5, 0x18), "dark_oak_leaves": (0x48, 0xB5, 0x18), "vine": (0x48, 0xB5, 0x18),
        "lily_pad": (0x20, 0x80, 0x30), "water": (0x3F, 0x76, 0xE4), "sugar_cane": (0x79, 0xC0, 0x5A)}
SPECIAL = {"water": "#3F76E4", "lava": "#E8641B", "air": None, "cave_air": None, "void_air": None,
           "light": None, "barrier": "#FF00FF", "structure_void": None, "glass": "#C8E6F0",
           "iron_bars": "#7A7A7A", "chain": "#3A3A44", "ladder": "#8B6B3D", "torch": "#FFD060",
           "lantern": "#F2B14A", "sea_lantern": "#BFE6E0", "glowstone": "#E8C070", "shroomlight": "#F59A3A",
           "end_rod": "#F0F0F0", "redstone_lamp": "#8B5A2B", "snow": "#F5F8FA", "powder_snow": "#F5F8FA"}


def avg(path):
    im = Image.open(path).convert("RGBA")
    a = np.asarray(im, dtype=np.float64)
    if a.shape[0] > a.shape[1]:  # animated strip: first frame
        a = a[: a.shape[1]]
    mask = a[..., 3] > 40
    if not mask.any():
        return None, 0.0
    rgb = a[..., :3][mask].mean(axis=0)
    alpha = a[..., 3][mask].mean() / 255.0 * (mask.mean())
    return rgb, alpha


def main(root):
    tex = os.path.join(root, "textures", "block")
    names = set(f[:-4] for f in os.listdir(tex) if f.endswith(".png"))
    states = [f[:-5] for f in os.listdir(os.path.join(root, "blockstates")) if f.endswith(".json")]
    out = {}

    def find(base):
        for cand in (base, base + "_top", base + "_side", base + "_front", base + "_planks", base + "_block",
                     base + "s", base + "_0", base + "_still", base + "_bottom"):
            if cand in names:
                return cand
        return None

    for s in sorted(states):
        key = "minecraft:" + s
        if s in SPECIAL:
            if SPECIAL[s]:
                out[key] = {"c": SPECIAL[s], "a": 0.35 if s in ("glass", "water") else 1.0}
            continue
        base = s
        t = find(base)
        if t is None:
            for suf in SUFFIXES:
                if base.endswith(suf):
                    b2 = base[: -len(suf)]
                    if suf in ("_carpet",):
                        b2 = b2 + "_wool" if (b2 + "_wool") in names else b2
                    if suf == "_pane" and (b2 + "_stained_glass") in names:
                        b2 = b2 + "_stained_glass"
                    t = find(b2)
                    if t is None and b2.endswith("_stained_glass"):
                        t = b2 if b2 in names else None
                    if t is None:
                        t = find(b2 + "_planks") or find(b2 + "_block")
                    if t:
                        break
        if t is None and base.endswith("_wall") is False and base.startswith("potted_"):
            t = find(base[len("potted_"):])
        if t is None:
            continue
        rgb, alpha = avg(os.path.join(tex, t + ".png"))
        if rgb is None:
            continue
        if base in TINT or t in TINT:
            tint = np.array(TINT.get(base, TINT.get(t)), dtype=np.float64) / 255.0
            rgb = rgb * tint
        out[key] = {"c": "#%02X%02X%02X" % tuple(int(round(v)) for v in rgb), "a": round(float(min(1.0, alpha)), 2)}
    for k, v in SPECIAL.items():
        if v and ("minecraft:" + k) not in out:
            out["minecraft:" + k] = {"c": v, "a": 1.0}
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "block_colors.json")
    with open(path, "w") as f:
        json.dump(out, f, indent=0, sort_keys=True)
    print("wrote", path, len(out), "blocks")


if __name__ == "__main__":
    main(sys.argv[1])
