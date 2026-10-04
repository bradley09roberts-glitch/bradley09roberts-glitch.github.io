#!/usr/bin/env python3
"""Software previews of the doll (reads the generated resource files, so it previews exactly what ships).

    python3 preview.py                         # regenerate every sheet in ./preview
    python3 preview.py pose --anim idle_tree --t 1.3 --view 3/4 --out /tmp/x.png
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE))

import numpy as np
from PIL import Image, ImageDraw

import gl_model as gl
import render as rd

ASSETS = HERE.parents[3] / "src" / "main" / "resources" / "assets" / "squidgame"
OUT = HERE / "preview"
PRE = "animation.doll."

EYES_ON = {"idle_players", "scan_players", "lock_on"}   # animations previewed with the glowing eyes
SIZE_F = (232, 330)

VIEWS = {
    "front": (0, 4), "3/4": (35, 10), "right": (90, 4), "back": (180, 4), "left": (-90, 4), "top": (0, 89),
}


def load():
    geo = gl.load_json(ASSETS / "geo" / "entity" / "doll.geo.json")
    anims = gl.parse_animations(gl.load_json(ASSETS / "animations" / "entity" / "doll.animation.json"))
    tex = rd.load_tex(ASSETS / "textures" / "entity" / "doll.png")
    glow = rd.load_tex(ASSETS / "textures" / "entity" / "doll_glowmask.png")
    return gl.bake(geo), anims, tex, glow


def make_pose(model, anims, name, t, eyes_on):
    anim = anims.get(PRE + name) if name else None
    pose = gl.pose_at(model, anim, t)
    pose.hidden.add("eyes_off" if eyes_on else "eyes_on")
    return pose


def render_view(model, tex, glow, pose, view="front", size=SIZE_F, scale=2.0, night=False, center=(0, 72, 0), ssaa=2,
                elevation=None, azimuth=None, mode="ortho", distance=900.0):
    az, el = VIEWS.get(view, (0, 4))
    if azimuth is not None:
        az = azimuth
    if elevation is not None:
        el = elevation
    cam = rd.Camera(azimuth=az, elevation=el, center=center, scale=scale, mode=mode, distance=distance)
    return rd.render(model, tex, glow, pose, cam, size=size, ssaa=ssaa, night=night)


def _plot(ts, series, title, size=(1400, 240), ylim=None):
    """Tiny line plot with PIL: series = [(label, ys, colour)]."""
    W, H = size
    img = Image.new("RGB", size, (24, 26, 32))
    d = ImageDraw.Draw(img)
    f = rd._font(12)
    ys_all = np.concatenate([np.asarray(s[1]) for s in series])
    lo, hi = (ylim if ylim else (float(ys_all.min()) - 5, float(ys_all.max()) + 5))
    L, R, T, B = 46, W - 12, 26, H - 44
    d.text((8, 4), title, fill=(235, 235, 235), font=rd._font(14))
    for g in np.linspace(lo, hi, 5):
        y = B - (g - lo) / (hi - lo) * (B - T)
        d.line([(L, y), (R, y)], fill=(48, 52, 62))
        d.text((6, y - 7), "%.0f" % g, fill=(150, 156, 170), font=f)
    t0, t1 = ts[0], ts[-1]
    for k in range(0, 11):
        x = L + (R - L) * k / 10
        d.line([(x, T), (x, B)], fill=(40, 44, 54))
        d.text((x - 10, B + 4), "%.2f" % (t0 + (t1 - t0) * k / 10), fill=(150, 156, 170), font=f)
    for (label, ys, col), i in zip(series, range(len(series))):
        pts = [(L + (R - L) * (t - t0) / (t1 - t0), B - (y - lo) / (hi - lo) * (B - T)) for t, y in zip(ts, ys)]
        d.line(pts, fill=col, width=2)
        d.text((L + 40 + 170 * i, H - 18), label, fill=col, font=f)
    return img


def sheet_for(model, anims, tex, glow, name, n=None, times=None, top_row=True):
    a = anims[PRE + name]
    L = a.length_ticks / 20.0
    eyes = name in EYES_ON
    if times is None:
        if a.loop is True:
            n = n or 6
            times = [L * i / n for i in range(n)]
        else:
            n = n or 8
            times = [L * i / (n - 1) for i in range(n)]
    frames, labels = [], []
    for t in times:
        p = make_pose(model, anims, name, min(t, L - 1e-4) if a.loop is not True else t, eyes)
        frames.append(render_view(model, tex, glow, p, "front", night=eyes))
        labels.append("%s  t=%.2fs" % (name, t))
    if top_row:
        for t in times:
            p = make_pose(model, anims, name, min(t, L - 1e-4) if a.loop is not True else t, eyes)
            frames.append(render_view(model, tex, glow, p, "top", size=SIZE_F, scale=3.4, center=(0, 100, 0), night=eyes))
            labels.append("top")
    cols = len(times)
    sheet = rd.contact_sheet(frames, labels, cols=cols, title="%s  (length %.2fs, loop=%s)" % (PRE + name, L, a.loop))
    return sheet


def yaw_plot(model, anims, name, dt=1 / 120.0):
    a = anims[PRE + name]
    L = a.length_ticks / 20.0
    ts = np.arange(0, L + 1e-9, dt)
    head, ring, flange = [], [], []
    for t in ts:
        p = gl.pose_at(model, a, min(t, L - 1e-6) if a.loop is True else t, loop_wrap=False)
        hy = -math.degrees(p.rot["head"][1])
        ry = -math.degrees(p.rot["neck_joint"][1])
        fy = -math.degrees(p.rot["neck_ring"][1])
        head.append(hy)
        ring.append(ry)
        flange.append(ry + fy)
    return _plot(ts, [("head yaw", head, (255, 170, 80)), ("cog ring yaw", ring, (120, 200, 255)), ("flange (abs)", flange, (150, 255, 150))],
                 "%s: yaw (deg, +y = turn to the doll's right) vs time" % name)


def atlas_preview(tex, glow):
    im = Image.fromarray(tex, "RGBA")
    bg = Image.new("RGBA", im.size, (60, 64, 72, 255))
    # checker
    px = np.array(bg)
    yy, xx = np.mgrid[0:im.size[1], 0:im.size[0]]
    px[((xx // 4 + yy // 4) % 2) == 0] = (74, 78, 88, 255)
    bg = Image.fromarray(px, "RGBA")
    bg.alpha_composite(im)
    g = np.array(glow)
    ov = np.zeros_like(px)
    m = g[..., 3] > 0
    ov[m] = (0, 255, 255, 160)
    bg.alpha_composite(Image.fromarray(ov, "RGBA"))
    return bg.resize((im.size[0] * 3, im.size[1] * 3), Image.NEAREST).convert("RGB")


def make_all(only=None):
    OUT.mkdir(exist_ok=True)
    model, anims, tex, glow = load()
    # 1. turnaround, eyes off (day) and eyes on (night)
    pose = make_pose(model, anims, None, 0, False)
    fr, lb = [], []
    for v in ("front", "3/4", "right", "back", "left", "top"):
        fr.append(render_view(model, tex, glow, pose, v, scale=2.0 if v != "top" else 3.0, center=(0, 72, 0) if v != "top" else (0, 100, 0)))
        lb.append(v + " (eyes off, day)")
    pose_on = make_pose(model, anims, None, 0, True)
    for v in ("front", "3/4"):
        fr.append(render_view(model, tex, glow, pose_on, v, night=True))
        lb.append(v + " (eyes on, night)")
    rd.contact_sheet(fr, lb, cols=4, title="doll - turnaround (rest pose, no animation)").save(OUT / "00_turnaround.png")
    # 2. face close-ups
    fr, lb = [], []
    for nm, p, night, v, az in (("eyes off", pose, False, "front", 0), ("eyes on (night)", pose_on, True, "front", 0), ("eyes on 3/4 (night)", pose_on, True, "front", 28),
                                ("eyes off side", pose, False, "right", 90)):
        fr.append(render_view(model, tex, glow, p, v, size=(420, 420), scale=9.0, center=(0, 114, 0), night=night, azimuth=az, elevation=3))
        lb.append(nm)
    rd.contact_sheet(fr, lb, cols=4, title="doll - head close-ups").save(OUT / "01_face_closeup.png")
    # 3. far view simulation (what a player sees from 100 / 50 / 25 blocks; nearest sampling, no AA)
    fr, lb = [], []
    for dist, scale in ((100, 0.55), (50, 1.1), (25, 2.2)):
        for nm, p, night in (("eyes off", pose, False), ("eyes on", pose_on, True)):
            im = render_view(model, tex, glow, p, "front", size=(int(120 * max(scale, 0.55)) // 1, int(160 * max(scale, 0.55)) // 1), scale=scale,
                             center=(0, 72, 0), night=night, ssaa=1, elevation=0)
            # upscale for viewing only
            k = max(1, int(round(4.0 / max(scale, 0.5))))
            fr.append(im.resize((im.size[0] * k, im.size[1] * k), Image.NEAREST))
            lb.append("%d blocks, %s (x%d)" % (dist, nm, k))
    w = max(i.size[0] for i in fr)
    h = max(i.size[1] for i in fr)
    fr = [i if i.size == (w, h) else _pad(i, (w, h)) for i in fr]
    rd.contact_sheet(fr, lb, cols=len(fr), title="doll - simulated distance views (1 texel = 1/16 block, no mip-maps, no AA)").save(OUT / "02_far_view.png")
    atlas_preview(tex, glow).save(OUT / "03_texture_atlas.png")
    # 4. animations
    names = [k[len(PRE):] for k in anims]
    for nm in names:
        if only and nm not in only:
            continue
        if nm in ("turn_to_players", "turn_to_tree"):
            times = [0.0, 0.07, 0.13, 0.20, 0.36, 0.50, 0.60, 0.79, 0.87, 1.0]
            sh = sheet_for(model, anims, tex, glow, nm, times=times)
        elif nm == "lock_on":
            sh = sheet_for(model, anims, tex, glow, nm, times=[0.0, 0.03, 0.055, 0.09, 0.14, 0.2, 0.3, 0.4])
        elif nm == "wake":
            sh = sheet_for(model, anims, tex, glow, nm, times=[0.0, 0.3, 0.5, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6, 1.8, 1.9, 2.0])
        else:
            sh = sheet_for(model, anims, tex, glow, nm)
        if nm in ("turn_to_players", "turn_to_tree", "scan_players", "lock_on", "idle_players", "wake"):
            plot = yaw_plot(model, anims, nm)
            canvas = Image.new("RGB", (max(sh.size[0], plot.size[0]), sh.size[1] + plot.size[1]), (24, 26, 32))
            canvas.paste(sh, (0, 0))
            canvas.paste(plot, (0, sh.size[1]))
            sh = canvas
        sh.save(OUT / ("anim_%s.png" % nm))
    print("previews written to", OUT)


def _pad(im, size):
    c = Image.new("RGB", size, (24, 26, 32))
    c.paste(im, ((size[0] - im.size[0]) // 2, (size[1] - im.size[1]) // 2))
    return c


def main(argv=None):
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd")
    p = sub.add_parser("pose")
    p.add_argument("--anim", default=None)
    p.add_argument("--t", type=float, default=0.0)
    p.add_argument("--view", default="front")
    p.add_argument("--eyes-on", action="store_true")
    p.add_argument("--night", action="store_true")
    p.add_argument("--scale", type=float, default=2.0)
    p.add_argument("--out", default="pose.png")
    a = sub.add_parser("all")
    a.add_argument("--only", nargs="*")
    args = ap.parse_args(argv)
    if args.cmd == "pose":
        model, anims, tex, glow = load()
        pose = make_pose(model, anims, args.anim, args.t, args.eyes_on)
        render_view(model, tex, glow, pose, args.view, scale=args.scale, night=args.night).save(args.out)
        print("saved", args.out)
    else:
        make_all(only=getattr(args, "only", None))


if __name__ == "__main__":
    main()
