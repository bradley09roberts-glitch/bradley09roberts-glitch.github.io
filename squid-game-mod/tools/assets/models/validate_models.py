#!/usr/bin/env python3
"""Validate the generated contestant / guard assets against docs/ASSET_CONTRACT.md.

Checks
  * geo / animation / bones JSON parse; texture exists, 128x128 RGBA
  * every contract bone and animation exists (hierarchy relations where the contract states them)
  * every UV face lies inside the texture; no two cubes share texture pixels
  * skin / hair regions are greyscale (Java tints them); face decals are fully opaque-or-transparent ink
  * every animation: valid easing names, strictly increasing key times <= animation_length, finite numbers,
    only bones that exist, no scale channel, loop type / length as in the contract
  * loop animations: first and last keyframe of every channel match
  * one-shots (once / hold) start from the idle pose (or their documented reference animation); plain "once" clips
    also END at the idle pose (or the pose they continue into: pull_* -> pull_idle, dalgona_* -> dalgona_sit,
    fire -> aim; marble_reveal ends on the open hand) - warning otherwise
  * overlay animations only touch their allowed bones
  * contestant_bones.json agrees with the geo and the Java-facing role lists

Exit status 0 = all good.
"""
from __future__ import annotations

import json
import math
import sys
from collections import defaultdict
from typing import Dict, List, Optional, Tuple

from common import *  # noqa: F401,F403
import numpy as np
from PIL import Image

from lib.anim import EASINGS
from lib.geom import box_faces
from lib.rig import Clip, ClipLibrary, Rig

ERR: List[str] = []
WARN: List[str] = []


def err(msg: str) -> None:
    ERR.append(msg)
    print("  ERROR  " + msg)


def warn(msg: str) -> None:
    WARN.append(msg)
    print("  warn   " + msg)


def ok(msg: str) -> None:
    print("  ok     " + msg)


# ----------------------------------------------------------------------------------------------
# contract data
# ----------------------------------------------------------------------------------------------
LOOP, ONCE, HOLD = "loop", "once", "hold"

CONTESTANT_BONES = (
    ["root", "waist", "body", "head", "head_skin", "neck_skin",
     "hair_buzz", "hair_short", "hair_parted", "hair_curly", "hair_long", "hair_ponytail", "hair_bun"]
    + [f"face_{i}" for i in range(6)]
    + ["glasses", "left_arm", "right_arm", "left_hand_skin", "right_hand_skin", "item_left", "item_right",
       "left_leg", "right_leg", "left_shoe", "right_shoe", "number_chest", "number_back"])
CONTESTANT_PARENTS = {   # bone -> required (direct) parent or tuple of allowed parents
    "waist": "root", "body": "waist", "head": "body", "head_skin": "head", "neck_skin": ("head", "body"),
    "glasses": "head", "left_arm": "body", "right_arm": "body", "left_leg": "waist", "right_leg": "waist",
    "number_chest": "body", "number_back": "body",
    **{f"hair_{h}": "head" for h in ("buzz", "short", "parted", "curly", "long", "ponytail", "bun")},
    **{f"face_{i}": "head" for i in range(6)},
}
CONTESTANT_DESCENDANTS = {   # bone -> ancestor it must sit under
    "left_hand_skin": "left_arm", "right_hand_skin": "right_arm", "item_left": "left_arm", "item_right": "right_arm",
    "left_shoe": "left_leg", "right_shoe": "right_leg",
}
GUARD_BONES = ["root", "waist", "body", "head", "hood", "mask_circle", "mask_triangle", "mask_square",
               "left_arm", "right_arm", "left_hand", "right_hand", "item_right", "item_left", "rifle",
               "left_leg", "right_leg", "left_boot", "right_boot", "collar_black"]
GUARD_PARENTS = {"waist": "root", "body": "waist", "head": "body", "hood": "head", "mask_circle": "head",
                 "mask_triangle": "head", "mask_square": "head", "left_arm": "body", "right_arm": "body",
                 "rifle": "body", "collar_black": "body", "left_leg": "waist", "right_leg": "waist"}
GUARD_DESCENDANTS = {"left_hand": "left_arm", "right_hand": "right_arm", "item_left": "left_arm", "item_right": "right_arm",
                     "left_boot": "left_leg", "right_boot": "right_leg"}

# name: (loop type, length or None)
CONTESTANT_ANIMS: Dict[str, Tuple[str, Optional[float]]] = {
    "idle": (LOOP, 4.0), "idle_nervous": (LOOP, None), "idle_confident": (LOOP, None), "walk": (LOOP, 1.0),
    "run": (LOOP, 0.6), "stop_skid": (ONCE, 0.5), "turn_left": (ONCE, 0.4), "turn_right": (ONCE, 0.4),
    "freeze_balance": (LOOP, None), "freeze_stiff": (LOOP, None), "sneak_walk": (LOOP, 1.2),
    "jump_leap": (HOLD, 0.6), "fall_loop": (LOOP, 0.5), "land": (ONCE, 0.35),
    "stumble": (ONCE, 0.9), "lose_balance": (ONCE, 1.2), "shocked": (ONCE, 1.0), "cower": (LOOP, None),
    "sob": (LOOP, None), "relieved": (ONCE, 1.8), "celebrate": (LOOP, 1.0), "celebrate_fist": (LOOP, 1.0),
    "wave": (ONCE, 1.4), "point": (HOLD, 1.0), "nod": (ONCE, 0.6), "shake_head": (ONCE, 0.8), "think": (LOOP, 3.0),
    "inspect": (LOOP, 2.5), "interact": (ONCE, 0.9), "eliminated_forward": (HOLD, 1.6),
    "eliminated_backward": (HOLD, 1.6), "knocked_down": (HOLD, 1.2),
    "pull_idle": (LOOP, 1.6), "pull_heave": (ONCE, 0.5), "pull_strain": (LOOP, 0.8), "pull_slip": (ONCE, 0.8),
    "dalgona_sit": (LOOP, 3.0), "dalgona_carve": (LOOP, 2.0), "dalgona_lick": (ONCE, 1.8),
    "dalgona_crack": (ONCE, 0.7), "dalgona_success": (ONCE, 2.0), "dalgona_fail": (HOLD, 2.0),
    "marble_hold": (LOOP, 2.0), "marble_guess": (ONCE, 1.2), "marble_reveal": (ONCE, 1.0),
    "marble_throw_windup": (HOLD, 0.5), "marble_throw_release": (ONCE, 0.5),
    "bridge_step": (ONCE, 0.9), "bridge_hesitate": (LOOP, 2.5), "bridge_balance": (LOOP, 1.2),
    "fight_stance": (LOOP, 1.0), "punch_left": (ONCE, 0.35), "punch_right": (ONCE, 0.35), "shove": (ONCE, 0.5),
    "block": (LOOP, 1.0), "dodge_left": (ONCE, 0.4), "dodge_right": (ONCE, 0.4), "knocked_back": (ONCE, 0.6),
    "sprint_attack": (LOOP, 0.5), "sit_idle": (LOOP, 4.0), "attention": (LOOP, 4.0), "hands_up": (LOOP, 2.0),
}
GUARD_ANIMS: Dict[str, Tuple[str, Optional[float]]] = {
    "idle": (LOOP, None), "idle_alert": (LOOP, None), "idle_rigid": (LOOP, None), "walk": (LOOP, 1.0),
    "run": (LOOP, 0.6), "aim": (LOOP, None), "fire": (ONCE, 0.3), "lower": (ONCE, 0.5), "turn_left": (ONCE, 0.4),
    "turn_right": (ONCE, 0.4), "point_forward": (HOLD, 1.0), "point_down": (ONCE, 0.8), "salute": (ONCE, 1.2),
    "open_door": (ONCE, 1.2), "inspect": (ONCE, 2.0), "carry_pose": (LOOP, None), "clap": (ONCE, 1.0),
    "wave_on": (ONCE, 1.0),
}
ARM_R = {"right_arm", "right_forearm", "right_hand_skin", "right_hand", "item_right"}
OVERLAYS = {   # contestant overlay animations -> allowed bones
    "wave": ARM_R | {"head"},
    "point": ARM_R,
    "nod": {"head"},
    "shake_head": {"head"},
    "think": ARM_R | {"head"},
}
# one-shots whose t=0 pose is NOT the idle pose: name -> reference animation (optionally "@end")
DEFAULT_REFS: Dict[str, Dict[str, str]] = {"contestant": {}, "guard": {}}

ROT_TOL = 1.0      # degrees
POS_TOL = 0.12     # pixels
END_ROT_TOL = 2.0  # degrees: "once" clips end at the idle pose (or the pose they continue into)
END_POS_TOL = 0.3  # pixels
# one-shots that deliberately end somewhere else than the idle pose
END_REFS = {"contestant": {"pull_heave": "pull_idle", "pull_slip": "pull_idle", "dalgona_lick": "dalgona_sit",
                           "dalgona_crack": "dalgona_sit", "dalgona_success": "dalgona_sit"},
            "guard": {"fire": "aim"}}
END_EXCEPT = {"contestant": {"marble_reveal"},          # ends on the open hand (the point of the clip)
              "guard": set()}
LOOP_ROT_TOL = 0.05
LOOP_POS_TOL = 0.01


# ----------------------------------------------------------------------------------------------
def load_json(path, what):
    try:
        with open(path) as f:
            return json.load(f)
    except Exception as e:  # noqa: BLE001
        err(f"{what}: cannot parse {path}: {e}")
        return None


def finite(v) -> bool:
    return all(isinstance(x, (int, float)) and math.isfinite(x) for x in v)


def check_geo(model: str, geo: dict, tex_path, required_bones, parents, descendants) -> Optional[Rig]:
    g = geo["minecraft:geometry"][0]
    desc = g["description"]
    if geo.get("format_version") != "1.12.0":
        err(f"{model}: format_version must be 1.12.0")
    if desc.get("identifier") != f"geometry.squidgame.{model}":
        err(f"{model}: identifier {desc.get('identifier')!r}")
    tw, th = desc["texture_width"], desc["texture_height"]
    if (tw, th) != (128, 128):
        err(f"{model}: texture size in geo is {tw}x{th}, contract says 128x128")
    bones = {b["name"]: b for b in g["bones"]}
    if len(bones) != len(g["bones"]):
        err(f"{model}: duplicate bone names")
    for b in required_bones:
        if b not in bones:
            err(f"{model}: missing required bone {b}")
    for b, par in parents.items():
        if b not in bones:
            continue
        allowed = par if isinstance(par, tuple) else (par,)
        if bones[b].get("parent") not in allowed:
            err(f"{model}: bone {b} must be a child of {allowed}, is {bones[b].get('parent')}")

    def ancestors(n):
        out = []
        p = bones[n].get("parent")
        while p:
            out.append(p)
            p = bones[p].get("parent")
        return out
    for b, anc in descendants.items():
        if b in bones and anc not in ancestors(b):
            err(f"{model}: bone {b} must be a descendant of {anc}")
    for n, b in bones.items():
        p = b.get("parent")
        if p and p not in bones:
            err(f"{model}: bone {n} has unknown parent {p}")
        if "pivot" not in b:
            err(f"{model}: bone {n} has no pivot (GeckoLib needs one)")
        elif not finite(b["pivot"]):
            err(f"{model}: bone {n} pivot not finite")
        if b.get("rotation") and any(abs(x) > 1e-9 for x in b["rotation"]):
            warn(f"{model}: bone {n} has a bind rotation (GeckoLib adds animation Euler angles to it)")
    # cubes / UV
    occupancy = np.zeros((th, tw), dtype=np.int32)
    owner: Dict[Tuple[int, int], str] = {}
    n_cubes = 0
    for n, b in bones.items():
        for ci, c in enumerate(b.get("cubes", [])):
            n_cubes += 1
            tag = f"{n}[{ci}]"
            if not (finite(c["origin"]) and finite(c["size"])):
                err(f"{model}: cube {tag} has non-finite origin/size")
            uv = c["uv"]
            rects = []
            if isinstance(uv, list):
                sx, sy, sz = (math.floor(s) for s in c["size"])
                if any(abs(s - round(s)) > 1e-9 for s in c["size"]):
                    err(f"{model}: box-UV cube {tag} has non-integer size {c['size']}")
                for face, (x, y, w, h) in box_faces(int(uv[0]), int(uv[1]), sx, sy, sz).items():
                    rects.append((face, x, y, w, h))
            else:
                for face, f in uv.items():
                    rects.append((face, f["uv"][0], f["uv"][1], f["uv_size"][0], f["uv_size"][1]))
            for face, x, y, w, h in rects:
                if w == 0 or h == 0:
                    continue
                if x < 0 or y < 0 or x + w > tw or y + h > th:
                    err(f"{model}: cube {tag} face {face} UV rect ({x},{y},{w},{h}) outside {tw}x{th}")
                    continue
                x0, y0, x1, y1 = int(x), int(y), int(x + w), int(y + h)
                region = occupancy[y0:y1, x0:x1]
                if region.any():
                    ys, xs = np.nonzero(region)
                    first = (int(xs[0]) + x0, int(ys[0]) + y0)
                    err(f"{model}: cube {tag} face {face} overlaps UV pixels of {owner.get(first, '?')} (at {first})")
                occupancy[y0:y1, x0:x1] += 1
                for yy in range(y0, y1):
                    for xx in range(x0, x1):
                        owner[(xx, yy)] = tag
    ok(f"{model}: {len(bones)} bones, {n_cubes} cubes, UVs inside {tw}x{th} ({int((occupancy > 0).sum())} px used)")
    return Rig(geo)


def check_texture(model: str, geo: dict, tex_path, skin_hair_bones, face_bones, ink_bones=()):
    try:
        im = Image.open(tex_path)
    except Exception as e:  # noqa: BLE001
        err(f"{model}: cannot open texture {tex_path}: {e}")
        return
    if im.size != (128, 128):
        err(f"{model}: texture is {im.size}, contract says 128x128")
    if im.mode != "RGBA":
        warn(f"{model}: texture mode {im.mode}, expected RGBA")
    arr = np.asarray(im.convert("RGBA"))
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}

    def rects_of(c):
        uv = c["uv"]
        if isinstance(uv, list):
            sx, sy, sz = (math.floor(s) for s in c["size"])
            return list(box_faces(int(uv[0]), int(uv[1]), sx, sy, sz).values())
        return [(f["uv"][0], f["uv"][1], f["uv_size"][0], f["uv_size"][1]) for f in uv.values()]
    for bn in skin_hair_bones:
        if bn not in bones:
            continue
        worst = 0
        npx = 0
        for c in bones[bn].get("cubes", []):
            for (x, y, w, h) in rects_of(c):
                reg = arr[int(y):int(y + h), int(x):int(x + w)].astype(int)
                m = reg[..., 3] > 0
                if not m.any():
                    continue
                px = reg[m][:, :3]
                npx += len(px)
                worst = max(worst, int((px.max(1) - px.min(1)).max()))
        if worst > 3:
            err(f"{model}: tint bone {bn} must be greyscale, max channel spread {worst}")
        if npx == 0:
            err(f"{model}: tint bone {bn} has no opaque texels")
    for bn in face_bones:
        if bn not in bones:
            continue
        npx = 0
        for c in bones[bn].get("cubes", []):
            for (x, y, w, h) in rects_of(c):
                reg = arr[int(y):int(y + h), int(x):int(x + w)]
                a = reg[..., 3]
                if ((a != 0) & (a != 255)).any():
                    err(f"{model}: {bn} has semi-transparent texels (cutout shaders need 0/255 alpha)")
                npx += int((a == 255).sum())
                if (a == 0).sum() < 8:
                    err(f"{model}: {bn} decal has no transparent area")
        if npx == 0:
            err(f"{model}: decal bone {bn} is empty")
    # overall alpha sanity
    a = arr[..., 3]
    if ((a != 0) & (a != 255)).any():
        warn(f"{model}: texture contains semi-transparent texels (cutout render type keeps them opaque)")
    ok(f"{model}: texture {im.size[0]}x{im.size[1]} {im.mode}; tint regions greyscale; decals clean")


# ----------------------------------------------------------------------------------------------
def pose_at(clip: Clip, t: float, bone: str, key: str) -> Optional[Tuple[float, ...]]:
    st = clip.sample(t, loop=False)
    if bone in st and key in st[bone]:
        v = st[bone][key]
        if key == "rot":      # baked radians (-x,-y,+z) -> file degrees
            return (-math.degrees(v[0]), -math.degrees(v[1]), math.degrees(v[2]))
        return tuple(v)
    return None


def check_animations(model: str, anim_json: dict, rig: Rig, contract: Dict[str, Tuple[str, Optional[float]]],
                     refs: Dict[str, str]) -> None:
    anims = anim_json.get("animations", {})
    prefix = f"animation.{model}."
    for name in contract:
        if prefix + name not in anims:
            err(f"{model}: missing required animation {prefix}{name}")
    lib = ClipLibrary(anim_json)
    bone_names = set(rig.bones)
    n_checked = 0
    for full, aj in anims.items():
        if not full.startswith(prefix):
            err(f"{model}: animation {full} does not start with {prefix}")
            continue
        name = full[len(prefix):]
        n_checked += 1
        length = aj.get("animation_length")
        if not isinstance(length, (int, float)) or not math.isfinite(length) or length <= 0:
            err(f"{full}: bad animation_length {length!r}")
            continue
        loop = aj.get("loop")
        kind = LOOP if loop is True else HOLD if loop == "hold_on_last_frame" else ONCE if loop is False else "?"
        if kind == "?":
            err(f"{full}: bad loop value {loop!r}")
        if name in contract:
            want, wlen = contract[name]
            if kind != want:
                err(f"{full}: loop type is {kind}, contract wants {want}")
            if wlen is not None and abs(length - wlen) > 1e-6:
                err(f"{full}: length {length} s, contract wants {wlen} s")
        for bone, bj in aj.get("bones", {}).items():
            if bone not in bone_names:
                err(f"{full}: animates unknown bone {bone}")
                continue
            if "scale" in bj:
                err(f"{full}: bone {bone} animates scale (contract: never scale)")
            if name in OVERLAYS and model == "contestant" and bone not in OVERLAYS[name]:
                err(f"{full}: overlay animation touches {bone} (allowed: {sorted(OVERLAYS[name])})")
            for ch in ("rotation", "position"):
                tr = bj.get(ch)
                if tr is None:
                    continue
                if not isinstance(tr, dict):
                    err(f"{full}: {bone}.{ch} must be a time-keyed object")
                    continue
                times = []
                for k, v in tr.items():
                    try:
                        t = float(k)
                    except ValueError:
                        err(f"{full}: {bone}.{ch} bad key {k!r}")
                        continue
                    times.append(t)
                    vec = v["vector"] if isinstance(v, dict) else v
                    if not (isinstance(vec, list) and len(vec) == 3 and finite(vec)):
                        err(f"{full}: {bone}.{ch}@{k} bad/NaN vector {vec!r}")
                    if ch == "rotation" and finite(vec) and max(abs(x) for x in vec) > 400:
                        err(f"{full}: {bone}.{ch}@{k} rotation {vec} suspiciously large")
                    if isinstance(v, dict):
                        e = v.get("easing", "linear")
                        if e not in EASINGS:
                            err(f"{full}: {bone}.{ch}@{k} unknown easing {e!r}")
                if any(b <= a for a, b in zip(times, times[1:])):
                    err(f"{full}: {bone}.{ch} key times not strictly increasing")
                if times and (times[0] < 0 or times[-1] > length + 1e-6):
                    err(f"{full}: {bone}.{ch} key times outside 0..{length}")
                if times and times[0] > 1e-6 and kind != "?":
                    warn(f"{full}: {bone}.{ch} first key at {times[0]} (constant before it)")
                if kind == LOOP and times and abs(times[-1] - length) > 1e-6:
                    err(f"{full}: loop {bone}.{ch} has no key at the end ({times[-1]} vs {length})")
    # loops: first == last
    for full, aj in anims.items():
        if not full.startswith(prefix) or aj.get("loop") is not True:
            continue
        for bone, bj in aj.get("bones", {}).items():
            for ch, tol in (("rotation", LOOP_ROT_TOL), ("position", LOOP_POS_TOL)):
                tr = bj.get(ch)
                if not tr:
                    continue
                ks = list(tr.values())
                first = ks[0]["vector"] if isinstance(ks[0], dict) else ks[0]
                last = ks[-1]["vector"] if isinstance(ks[-1], dict) else ks[-1]
                if max(abs(a - b) for a, b in zip(first, last)) > tol:
                    err(f"{full}: loop {bone}.{ch} first {first} != last {last}")
    # one-shots start from the idle pose (or their documented reference)
    idle = lib.clips.get(prefix + "idle")
    for full, clip in lib.clips.items():
        name = full[len(prefix):]
        aj = anims[full]
        if aj.get("loop") is True:
            continue
        ref_name = refs.get(name, "idle")
        ref_t = 0.0
        if "@end" in ref_name:
            ref_name, ref_t = ref_name.split("@")[0], None
        ref = lib.clips.get(prefix + ref_name)
        if ref is None:
            err(f"{full}: reference animation {ref_name} not found")
            continue
        if ref_t is None:
            ref_t = ref.length
        overlay = (model == "contestant" and name in OVERLAYS)
        for bone, bj in aj.get("bones", {}).items():
            for ch, key, tol in (("rotation", "rot", ROT_TOL), ("position", "pos", POS_TOL)):
                if ch not in bj:
                    continue
                mine = pose_at(clip, 0.0, bone, key)
                theirs = pose_at(ref, ref_t, bone, key) or (0.0, 0.0, 0.0)
                if mine is None:
                    continue
                d = max(abs(a - b) for a, b in zip(mine, theirs))
                if d > tol:
                    err(f"{full}: t=0 {bone}.{ch} {tuple(round(x, 2) for x in mine)} differs from {ref_name}"
                        f"@{ref_t:.2f} {tuple(round(x, 2) for x in theirs)}")
        if not overlay:
            # bones the reference moves away from rest but this clip never touches would pop
            for bone, bj in ref.bones.items():
                if bone in aj.get("bones", {}):
                    continue
                st = pose_at(ref, ref_t, bone, "rot")
                if st is not None and max(abs(x) for x in st) > ROT_TOL:
                    warn(f"{full}: does not touch {bone} which {ref_name} holds at {tuple(round(x,1) for x in st)}")
    # one-shots (plain "once") end at the idle pose, or at the pose they continue into; hold clips keep their pose
    for full, clip in lib.clips.items():
        name = full[len(prefix):]
        aj = anims[full]
        if aj.get("loop") is not False or name in END_EXCEPT.get(model, ()):
            continue
        ref_name = END_REFS.get(model, {}).get(name, "idle")
        ref = lib.clips.get(prefix + ref_name)
        if ref is None:
            err(f"{full}: end reference animation {ref_name} not found")
            continue
        worst = None
        for bone, bj in aj.get("bones", {}).items():
            for ch, key, tol in (("rotation", "rot", END_ROT_TOL), ("position", "pos", END_POS_TOL)):
                if ch not in bj:
                    continue
                mine = pose_at(clip, clip.length, bone, key)
                theirs = pose_at(ref, 0.0, bone, key) or (0.0, 0.0, 0.0)
                if mine is None:
                    continue
                d = max(abs(a - b) for a, b in zip(mine, theirs))
                if d > tol and (worst is None or d > worst[0]):
                    worst = (d, bone, ch)
        if worst:
            warn(f"{full}: ends {worst[0]:.1f} off {ref_name}@0 in {worst[1]}.{worst[2]} (one-shots should end at the idle pose)")
    ok(f"{model}: {n_checked} animations checked ({len(contract)} required)")


def check_contestant_extra() -> None:
    geo = load_json(GEO_DIR / "contestant.geo.json", "contestant geo")
    bones_json = load_json(HERE / "contestant_bones.json", "contestant_bones.json")
    if not geo or not bones_json:
        return
    names = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
    for role in ("skin", "hair", "face", "accessory", "anchor", "structure", "limbs"):
        for b in bones_json.get(role, []):
            if b not in names:
                err(f"contestant_bones.json: {role} bone {b} not in geo")
    if set(bones_json.get("all_bones", [])) != names:
        err("contestant_bones.json: all_bones differs from the geo")
    # anchors on the surface, centred
    bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    for n, z in (("number_chest", -2.05), ("number_back", 2.05)):
        p = bones[n]["pivot"]
        if abs(p[0]) > 1e-6 or abs(p[2] - z) > 1e-6 or bones[n].get("cubes"):
            err(f"{n}: must be an empty anchor centred on x=0 at z={z}, got pivot {p}, cubes {len(bones[n].get('cubes', []))}")
    # chest surface: the jacket front is at z=-2, back at z=+2
    jacket = [c for c in bones["body"]["cubes"] if c["size"] == [8, 12, 4]]
    if not jacket or abs(jacket[0]["origin"][2] + 2) > 1e-6:
        err("body: expected an 8x12x4 jacket cube at z=-2")
    for b in ("head", "head_skin", "left_hand_skin", "right_hand_skin", "neck_skin"):
        if not bones[b]:
            err(f"{b} missing")
    for h in bones_json["hair"]:
        if not bones[h].get("cubes"):
            err(f"hair bone {h} has no cubes")
    for f in bones_json["face"]:
        if len(bones[f].get("cubes", [])) != 1:
            err(f"face bone {f} should have exactly one decal cube")
    for pair in (("left_arm", "right_arm"), ("left_leg", "right_leg"), ("left_hand_skin", "right_hand_skin")):
        a, b = bones[pair[0]]["pivot"], bones[pair[1]]["pivot"]
        if abs(a[0] + b[0]) > 1e-6 or abs(a[1] - b[1]) > 1e-6 or abs(a[2] - b[2]) > 1e-6:
            err(f"{pair}: pivots not mirror-symmetric {a} {b}")
    ok("contestant_bones.json consistent; anchors centred on the chest/back surface; hair/face bones populated")


def main() -> int:
    print("== contestant ==")
    geo = load_json(GEO_DIR / "contestant.geo.json", "contestant geo")
    anim = load_json(ANIM_DIR / "contestant.animation.json", "contestant animations")
    meta = load_json(HERE / "contestant_anim_meta.json", "contestant meta") or {"refs": {}}
    if geo:
        rig = check_geo("contestant", geo, TEX_DIR / "contestant.png", CONTESTANT_BONES, CONTESTANT_PARENTS, CONTESTANT_DESCENDANTS)
        skin = ["head_skin", "neck_skin", "left_hand_skin", "right_hand_skin"]
        hair = ["hair_buzz", "hair_short", "hair_parted", "hair_curly", "hair_long", "hair_ponytail", "hair_bun"]
        check_texture("contestant", geo, TEX_DIR / "contestant.png", skin + hair, [f"face_{i}" for i in range(6)])
        if anim and rig:
            check_animations("contestant", anim, rig, CONTESTANT_ANIMS, meta.get("refs", {}))
        check_contestant_extra()
    print("== guard ==")
    geo = load_json(GEO_DIR / "guard.geo.json", "guard geo")
    anim = load_json(ANIM_DIR / "guard.animation.json", "guard animations")
    meta = load_json(HERE / "guard_anim_meta.json", "guard meta") or {"refs": {}}
    if geo:
        rig = check_geo("guard", geo, TEX_DIR / "guard.png", GUARD_BONES, GUARD_PARENTS, GUARD_DESCENDANTS)
        check_texture("guard", geo, TEX_DIR / "guard.png", [], [])
        if anim and rig:
            check_animations("guard", anim, rig, GUARD_ANIMS, meta.get("refs", {}))
    print()
    print(f"{len(ERR)} error(s), {len(WARN)} warning(s)")
    return 1 if ERR else 0


if __name__ == "__main__":
    sys.exit(main())
