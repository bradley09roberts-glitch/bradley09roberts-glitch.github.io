#!/usr/bin/env python3
"""Render preview sheets from the GENERATED files in src/main/resources/assets/squidgame.

    python3 preview.py contestant --static              # turn-around + hair/face variants
    python3 preview.py contestant --anim walk run       # one contact sheet per animation
    python3 preview.py contestant --all                 # every animation (parallel)
    python3 preview.py guard --all

PNGs go to tools/assets/models/preview/<model>/ .  (The renderer replicates GeckoLib's bake and
transform conventions - see lib/rig.py - but cannot replace an in-game check.)
"""
from __future__ import annotations

import argparse
import multiprocessing as mp
import os
import sys
import time

from common import *  # noqa: F401,F403
import numpy as np
from PIL import Image

from lib import render
from lib.rig import ClipLibrary, Rig

SKIN = {"light": (1.00, 0.84, 0.72), "tan": (0.93, 0.72, 0.55), "dark": (0.52, 0.34, 0.25)}
HAIR = {"black": (0.12, 0.10, 0.10), "brown": (0.42, 0.28, 0.18), "grey": (0.62, 0.62, 0.64),
        "blond": (0.85, 0.68, 0.38)}


def model_paths(model: str):
    return (GEO_DIR / f"{model}.geo.json", ANIM_DIR / f"{model}.animation.json", TEX_DIR / f"{model}.png")


def appearance(model: str, hair="short", face=0, skin="tan", haircol="brown", glasses=False, mask="triangle",
               collar=False, rifle=True):
    """Return (tint dict, hidden set) the way the Java renderer would configure the bones."""
    if model == "contestant":
        hair_bones = ["hair_buzz", "hair_short", "hair_parted", "hair_curly", "hair_long", "hair_ponytail", "hair_bun"]
        tint = {b: SKIN[skin] for b in ("head_skin", "neck_skin", "left_hand_skin", "right_hand_skin")}
        for b in hair_bones:
            tint[b] = HAIR[haircol]
        hidden = {b for b in hair_bones if b != f"hair_{hair}"}
        hidden |= {f"face_{i}" for i in range(6) if i != face}
        if not glasses:
            hidden.add("glasses")
        return tint, hidden
    tint = {}
    hidden = {m for m in ("mask_circle", "mask_triangle", "mask_square") if m != f"mask_{mask}"}
    if not collar:
        hidden.add("collar_black")
    if not rifle:
        hidden.add("rifle")
    return tint, hidden


WOOD = (0.55, 0.40, 0.27)
PROPS = {
    "bench": ((-7.0, 0.0, -3.5), (7.0, 4.0, 6.5), WOOD),
    "table": ((-11.0, 0.0, -17.0), (11.0, 8.0, -7.0), (0.66, 0.54, 0.38)),
    "rope": ((-1.0, 16.4, -48.0), (0.2, 17.6, -0.5), (0.78, 0.66, 0.40)),
    "glass": ((-14.0, -1.2, -18.0), (14.0, -0.2, 18.0), (0.66, 0.84, 0.90)),
}


def props_for(name: str):
    if name.startswith("dalgona_"):
        return [PROPS["bench"], PROPS["table"]]
    if name == "sit_idle":
        return [PROPS["bench"]]
    if name.startswith("pull_"):
        return [PROPS["rope"]]
    if name.startswith("bridge_"):
        return [PROPS["glass"]]
    return None


def load(model: str):
    g, a, t = model_paths(model)
    rig = Rig.load(str(g))
    tex = render.load_texture(str(t))
    clips = ClipLibrary(str(a)) if a.exists() else None
    return rig, tex, clips


def static_sheets(model: str, outdir: str, ss: int = 2):
    rig, tex, clips = load(model)
    tint, hidden = appearance(model)
    views = [dict(name="front", azimuth=0), dict(name="3/4", azimuth=-35, elevation=10),
             dict(name="side", azimuth=90), dict(name="back", azimuth=180),
             dict(name="3/4 back", azimuth=145, elevation=10)]
    im = render.sheet(rig, tex, None, [0.0], views=views, cell=(230, 250), scale=6.2, center=(0, 17.5, 0),
                      tint=tint, hidden=hidden, ss=ss, title=f"{model} turn-around")
    im.save(os.path.join(outdir, f"{model}_static.png"))
    if model == "contestant":
        # hair styles x faces
        styles = ["buzz", "short", "parted", "curly", "long", "ponytail", "bun"]
        cell = (150, 175)
        rows = []
        sheet = Image.new("RGB", (cell[0] * len(styles), cell[1] * 4), (255, 255, 255))
        configs = [("front", 0, dict(face=0)), ("3/4", -38, dict(face=1)), ("side", 90, dict(face=2)), ("back", 180, dict(face=3))]
        for r, (nm, az, extra) in enumerate(configs):
            for c, st in enumerate(styles):
                tnt, hid = appearance(model, hair=st, haircol=["black", "brown", "blond", "grey"][c % 4],
                                      skin=["light", "tan", "dark"][c % 3], **extra)
                v = render.render(rig, tex, None, azimuth=az, elevation=8 if az not in (0, 90) else 0,
                                  scale=8.5, center=(0, 29, 0), size=cell, tint=tnt, hidden=hid, ss=ss)
                render.label(v, f"{st} {nm}")
                sheet.paste(v, (c * cell[0], r * cell[1]))
        sheet.save(os.path.join(outdir, "contestant_hair.png"))
        # six faces close up
        cell = (150, 150)
        sheet = Image.new("RGB", (cell[0] * 6, cell[1] * 2), (255, 255, 255))
        for r, (skin, hair) in enumerate((("light", "parted"), ("dark", "buzz"))):
            for c in range(6):
                tnt, hid = appearance(model, hair=hair, face=c, skin=skin, glasses=(c == 3 and r == 0))
                v = render.render(rig, tex, None, azimuth=-12, elevation=4, scale=14, center=(0, 28, 0), size=cell,
                                  tint=tnt, hidden=hid, ss=ss)
                render.label(v, f"face_{c} {skin}")
                sheet.paste(v, (c * cell[0], r * cell[1]))
        sheet.save(os.path.join(outdir, "contestant_faces.png"))
    # texture overview
    from lib.render import texture_sheet
    _, _, tp = model_paths(model)
    texture_sheet(str(tp), zoom=5).save(os.path.join(outdir, f"{model}_texture.png"))


def anim_sheet(args):
    model, name, outdir, ss, nframes = args
    rig, tex, clips = load(model)
    full = f"animation.{model}.{name}"
    clip = clips[full]
    tint, hidden = appearance(model)
    if clip.loop == "loop":
        times = [clip.length * i / nframes for i in range(nframes)]
    else:
        times = [clip.length * i / (nframes - 1) for i in range(nframes)]
    views = [dict(name="front", azimuth=0), dict(name="side", azimuth=90), dict(name="3/4", azimuth=-38, elevation=12)]
    # fit the window: lying poses need a lower centre
    pr = props_for(name)
    im = render.sheet(rig, tex, clip, times, views=views, cell=(170, 176), scale=4.0, center=(0, 17.0, 0),
                      tint=tint, hidden=hidden, ss=ss, title=f"{full}  [{clip.loop}, {clip.length:.2f}s]", props=pr)
    path = os.path.join(outdir, f"{name}.png")
    im.save(path)
    # also a side filmstrip
    fs = render.filmstrip(rig, tex, clip, n=10, azimuth=90, tint=tint, hidden=hidden, ss=ss, props=pr)
    fs.save(os.path.join(outdir, f"{name}_strip.png"))
    return path


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model", choices=["contestant", "guard"])
    ap.add_argument("--static", action="store_true")
    ap.add_argument("--anim", nargs="*")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--frames", type=int, default=8)
    ap.add_argument("--ss", type=int, default=1)
    ap.add_argument("--jobs", type=int, default=4)
    a = ap.parse_args()
    outdir = str(PREVIEW_DIR / a.model)
    os.makedirs(outdir, exist_ok=True)
    t0 = time.time()
    if a.static:
        static_sheets(a.model, outdir, ss=max(a.ss, 2))
    names = []
    if a.all or a.anim:
        _, _, clips = load(a.model)
        prefix = f"animation.{a.model}."
        allnames = [n[len(prefix):] for n in clips.clips]
        names = allnames if a.all else a.anim
    if names:
        jobs = [(a.model, n, outdir, a.ss, a.frames) for n in names]
        if len(jobs) > 1 and a.jobs > 1:
            with mp.Pool(a.jobs) as pool:
                for p in pool.imap_unordered(anim_sheet, jobs):
                    pass
        else:
            for j in jobs:
                anim_sheet(j)
    print(f"done in {time.time() - t0:.1f}s -> {outdir}")


if __name__ == "__main__":
    main()
