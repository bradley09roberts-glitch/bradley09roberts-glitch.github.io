#!/usr/bin/env python3
"""Geometric QA of the generated animations (reads the files, uses the GeckoLib port in lib/rig.py).

  * ground contact: lowest model point over time (penetration / floating)
  * foot planting: horizontal travel of the lowest foot point while it is on the floor (loops only)
  * limb vs torso/head interpenetration of hands, elbows and knees

    python3 qa_anims.py contestant            # all animations
    python3 qa_anims.py guard walk run aim
"""
from __future__ import annotations

import sys
from typing import Dict, List

import numpy as np

from common import *  # noqa: F401,F403
from lib.rig import ClipLibrary, Rig

OPTIONAL = {"contestant": ["hair_buzz", "hair_short", "hair_parted", "hair_curly", "hair_long", "hair_ponytail",
                           "hair_bun", "face_0", "face_1", "face_2", "face_3", "face_4", "face_5", "glasses"],
            "guard": ["mask_circle", "mask_triangle", "collar_black"]}
HAND = {"contestant": ("item_left", "item_right"), "guard": ("item_left", "item_right")}
AIRBORNE = {"jump_leap", "fall_loop", "celebrate", "celebrate_fist", "knocked_back", "land", "run", "sprint_attack"}
LYING = {"eliminated_forward", "eliminated_backward", "knocked_down"}


def lowest_points(rig: Rig, mats, hidden):
    ymin = 1e9
    for bone, cube, verts in rig.vertices_world(mats, hidden=hidden):
        ymin = min(ymin, float(verts[..., 1].min()))
    return ymin


def foot_points(rig: Rig, mats, model):
    """Lowest vertex (x,y,z) of each foot bone."""
    out = {}
    for side in ("left", "right"):
        bn = f"{side}_shoe" if model == "contestant" else f"{side}_boot"
        best = None
        b = rig.bones[bn]
        for c in b.cubes:
            m = mats[bn] @ rig.cube_matrix(c)
            v = np.concatenate([q.verts for q in c.quads], axis=0)
            vh = np.concatenate([v, np.ones((len(v), 1))], axis=1) @ m.T
            i = int(np.argmin(vh[:, 1]))
            if best is None or vh[i, 1] < best[1]:
                best = vh[i, :3]
        out[side] = best
    return out


def main() -> int:
    model = sys.argv[1]
    names = sys.argv[2:]
    rig = Rig.load(str(GEO_DIR / f"{model}.geo.json"))
    lib = ClipLibrary(str(ANIM_DIR / f"{model}.animation.json"))
    hidden = OPTIONAL[model]
    prefix = f"animation.{model}."
    rows = []
    for full, clip in lib.clips.items():
        name = full[len(prefix):]
        if names and name not in names:
            continue
        N = 24
        times = [clip.length * i / N for i in range(N + 1)] if clip.loop != "loop" else [clip.length * i / N for i in range(N)]
        lows = []
        pen = {}
        footy = {"left": [], "right": []}
        footz = {"left": [], "right": []}
        for t in times:
            st = clip.sample(t)
            m = rig.world_matrices(st)
            lows.append(lowest_points(rig, m, hidden))
            fp = foot_points(rig, m, model)
            for s in ("left", "right"):
                footy[s].append(fp[s][1])
                footz[s].append(fp[s][2])
            # interpenetration: hand anchors & elbows vs torso box (body frame) and head box
            inv_body = np.linalg.inv(m["body"])
            inv_head = np.linalg.inv(m["head"])
            for side in ("left", "right"):
                hand_bone = HAND[model][0 if side == "left" else 1]
                sx = 1 if side == "left" else -1
                pts = {
                    "hand": rig.point(hand_bone, (sx * 6, 11.7, 0), m),
                    "elbow": rig.point(f"{side}_forearm", (sx * 6, 17, 0), m),
                }
                for k, p in pts.items():
                    pb = (inv_body @ np.append(p, 1))[:3]     # baked body-local coords (x flipped)
                    inside = (abs(pb[0]) < 3.4 and 12.6 < pb[1] < 23.6 and abs(pb[2]) < 1.6)
                    if inside:
                        pen[f"{side}_{k}_in_torso"] = pen.get(f"{side}_{k}_in_torso", 0) + 1
                    ph = (inv_head @ np.append(p, 1))[:3]
                    if abs(ph[0]) < 3.6 and 24.4 < ph[1] < 31.6 and abs(ph[2]) < 3.6:
                        pen[f"{side}_{k}_in_head"] = pen.get(f"{side}_{k}_in_head", 0) + 1
        lo = min(lows)
        hi = max(lows)
        flag = []
        if name not in LYING and lo < -0.35:
            flag.append(f"PENETRATES floor by {-lo:.2f}px")
        if name not in AIRBORNE and name not in LYING and clip.loop != "once" and hi > 1.2 and name not in ("celebrate",):
            flag.append(f"floats up to {hi:.2f}px")
        if pen:
            flag.append("clip:" + ",".join(f"{k}x{v}" for k, v in pen.items()))
        # foot slip: while a foot's lowest point is on the floor (<0.4px) how far does it travel (z) relative to the body?
        slip = ""
        if clip.loop == "loop" and name in ("walk", "run", "sneak_walk", "sprint_attack"):
            for s in ("left", "right"):
                on = [i for i, y in enumerate(footy[s]) if y < 0.45]
                if len(on) > 2:
                    dz = [footz[s][on[k + 1]] - footz[s][on[k]] for k in range(len(on) - 1) if on[k + 1] == on[k] + 1]
                    slip += f" {s}:on={len(on)}/{len(times)} travel={sum(dz):.1f}px"
        rows.append((name, lo, hi, "; ".join(flag), slip))
    for name, lo, hi, flag, slip in rows:
        print(f"{name:22s} low[{lo:6.2f},{hi:6.2f}] {flag}{slip}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
