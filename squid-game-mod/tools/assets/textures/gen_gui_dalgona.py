"""Dalgona minigame GUI textures: cookie disc, wooden table, needle, crack overlays."""
from __future__ import annotations

import math

import numpy as np

from common import Canvas, Out, dilate, erode, fbm, lighten, mix, ramp, rng, shade
import paint

CLEAR = (0, 0, 0, 0)


# ------------------------------------------------------------------------------ cookie
def cookie() -> Canvas:
    """128x128 honey-brown dalgona disc, mottled caramel with air bubbles and a soft rim. No shape drawn."""
    S = 128
    r = rng("gui_cookie")
    c = Canvas(S)
    xs, ys = c.grid()
    cx = cy = 64.0
    R = 61.0
    dx, dy = xs - cx, ys - cy
    d = np.hypot(dx, dy)
    disc = d <= R

    f = paint.caramel_field(S, S, "gui_cookie_field", scale=2)
    grain = fbm(S, S, r, [(4, 0.6), (2, 0.4)])
    light = np.clip((-dx - dy) / (2 * R), -0.5, 0.5)           # lit from the upper left
    sheen = np.exp(-(((dx + 22) / 34.0) ** 2 + ((dy + 24) / 26.0) ** 2))
    v = np.clip(0.16 + 0.70 * f + 0.14 * grain + 0.20 * light + 0.12 * sheen, 0, 1)
    pal = [(104, 56, 14), (128, 72, 20), (152, 90, 26), (176, 112, 36), (198, 136, 52), (218, 160, 76),
           (234, 186, 108), (246, 208, 138)]
    base = ramp(v, pal, dither=0.55)
    c.mask(disc, base)

    # sugar pores: small air pockets, upper-left wall in shadow, lower-right wall catching light
    placed: list[tuple[float, float, float]] = []
    tries = 0
    while len(placed) < 36 and tries < 2000:
        tries += 1
        a = r.random() * math.tau
        rr = math.sqrt(r.random()) * (R - 12)
        bx, by = cx + rr * math.cos(a), cy + rr * math.sin(a)
        br = float(r.choice([1.2, 1.8, 2.4, 3.0], p=[0.30, 0.34, 0.24, 0.12]))
        if any(math.hypot(bx - px, by - py) < br + pr + 3 for px, py, pr in placed):
            continue
        placed.append((bx, by, br))
    for bx, by, br in placed:
        bd = np.hypot(xs - bx, ys - by)
        inner = bd <= br
        lipm = (bd <= br + 1.1) & ~inner
        diag = (xs - bx) + (ys - by)
        c.mask(inner, (146, 88, 28))
        c.mask(inner & (diag > br * 0.3), (226, 170, 88))
        c.mask(lipm & (diag < 0), (250, 218, 150))
        c.mask(lipm & (diag >= 0), (176, 110, 36))
    # tiny bright sugar crystals
    for _ in range(70):
        a = r.random() * math.tau
        rr = math.sqrt(r.random()) * (R - 6)
        x, y = int(cx + rr * math.cos(a)), int(cy + rr * math.sin(a))
        if disc[y, x]:
            c.px(x, y, (255, 232, 170))
    # rim: dark outline, lit bevel on the upper-left, shadowed bevel on the lower-right
    outline = disc & (d > R - 2.2)
    bevel = disc & (d > R - 8.0) & ~outline
    c.mask(bevel & (light >= 0.0), (240, 196, 120))
    c.mask(bevel & (light < 0.0), (150, 88, 26))
    # blend bevel towards the underlying colour so it feels like a soft rim, not a ring
    arr = c.a[..., :3].astype(float)
    underl = np.array(base, dtype=float)
    t = np.clip((d - (R - 8.0)) / 6.0, 0, 1)[..., None]
    arr = np.where(bevel[..., None], underl * (1 - 0.35 * t) + arr * (0.35 * t), arr)
    c.a[..., :3] = np.clip(arr, 0, 255).astype(np.uint8)
    c.mask(outline, (84, 44, 12))
    c.mask(disc & (d > R - 3.4) & (d <= R - 2.2) & (light > 0.05), (250, 214, 144))
    return c


# ------------------------------------------------------------------------------ table
def table() -> Canvas:
    """64x64 tileable worn wooden tabletop: four 16px planks, seams, nails, scratches."""
    pal = [(78, 52, 32), (92, 62, 38), (106, 72, 44), (120, 83, 51), (134, 94, 58), (148, 106, 66)]
    c = Canvas(64)
    c.fill_rgb(paint.wood(64, 64, "gui_table", plank=16, pal=pal, tone_var=0.08, knots=1, seams=True, nails=True,
                          scratches=30, gap_col=(40, 26, 16)))
    return c


# ------------------------------------------------------------------------------ needle
def needle() -> Canvas:
    """16x32: thin steel needle, brass collar, wooden handle.  The tip is the opaque pixel (0,0)."""
    c = Canvas(16, 32)
    p0 = np.array([0.5, 0.5])
    p1 = np.array([11.8, 31.0])
    d = p1 - p0
    L = np.linalg.norm(d)
    u = d / L
    n = np.array([-u[1], u[0]])
    xs, ys = c.grid()
    rel = np.stack([xs - p0[0], ys - p0[1]], axis=-1)
    t = rel @ u                       # along the needle
    s = rel @ n                       # across (signed)
    shaft_end, collar_end = 19.0, 22.0
    # steel shaft: tapered, 1.5px wide near the collar, a point at the tip
    half = np.clip(0.28 + t / shaft_end * 0.55, 0.28, 0.85)
    shaft = (t >= -0.2) & (t < shaft_end) & (np.abs(s) <= half)
    steel = np.where(s[..., None] < -0.15, np.array([236, 242, 250]),
                     np.where(s[..., None] > 0.25, np.array([116, 126, 142]), np.array([178, 188, 202])))
    c.mask(shaft, steel.astype(np.uint8))
    # brass collar
    collar = (t >= shaft_end) & (t < collar_end) & (np.abs(s) <= 1.45)
    brass = np.where(s[..., None] < -0.3, np.array([236, 200, 112]), np.array([176, 132, 56]))
    c.mask(collar, brass.astype(np.uint8))
    # wooden handle: slightly swelling
    hh = 1.55 + 0.55 * np.sin(np.clip((t - collar_end) / (L - collar_end), 0, 1) * math.pi * 0.85)
    handle = (t >= collar_end) & (t <= L - 0.3) & (np.abs(s) <= hh)
    wood = np.where(s[..., None] < -0.7, np.array([196, 142, 88]),
                    np.where(s[..., None] > 0.7, np.array([112, 74, 42]), np.array([154, 108, 64])))
    c.mask(handle, wood.astype(np.uint8))
    # darker outline of the handle's end cap
    c.mask(handle & (t > L - 1.6), (92, 60, 34))
    c.px(0, 0, (250, 252, 255))      # the tip, always opaque
    return c


# ------------------------------------------------------------------------------ cracks
def _crack_segments():
    """Deterministic crack network: list of (x0, y0, x1, y1, width, stage); later stages extend earlier ones."""
    r = rng("dalgona_cracks_v2")
    segs: list[tuple[float, float, float, float, float, int]] = []

    def walk(x, y, ang, steps, stage0, width, jitter, branch_p, depth=0):
        for i in range(steps):
            ang += (r.random() - 0.5) * 2 * jitter
            ln = 2.2 + r.random() * 2.4
            nx, ny = x + math.cos(ang) * ln, y + math.sin(ang) * ln
            stage = min(3, stage0 + (i * 3) // max(1, steps))
            segs.append((x, y, nx, ny, width, stage))
            if branch_p and r.random() < branch_p and depth < 2:
                side = 1 if r.random() < 0.5 else -1
                bang = ang + side * (0.40 + r.random() * 0.45)
                blen = int(3 + r.random() * 5)
                walk(nx, ny, bang, blen, min(3, stage + 1), 1.0, 0.65, 0.10, depth + 1)
            x, y = nx, ny

    ox, oy = 32.0, 31.0
    base = r.random() * math.tau
    for k in range(4):
        a = base + k * math.tau / 4 + (r.random() - 0.5) * 0.9
        walk(ox, oy, a, 11 if k < 2 else 8, 0 if k < 2 else 1, 1.7, 0.55, 0.20)
    # long fracture lines that run clear across in the last stages
    walk(ox, oy, base + 0.25, 20, 2, 1.7, 0.30, 0.10)
    walk(ox, oy, base + math.pi - 0.2, 20, 2, 1.7, 0.30, 0.10)
    return segs


def crack(stage: int) -> Canvas:
    c = Canvas(64)
    segs = [s for s in _crack_segments() if s[5] <= stage]
    core = np.zeros((64, 64), dtype=bool)
    thin = np.zeros((64, 64), dtype=bool)
    for (x0, y0, x1, y1, w, st) in segs:
        m = c.line_mask(x0, y0, x1, y1, w)
        if w >= 1.5:
            core |= m
        else:
            thin |= m
    # keep every crack inside the cookie disc (the overlay is drawn at 2x over the 128px cookie)
    xs, ys = c.grid()
    inside = np.hypot(xs - 32, ys - 32) <= 29.5
    core &= inside
    thin &= inside
    glow = dilate(core | thin, 1, diag=False) & ~(core | thin) & inside
    c.mask(glow, (255, 255, 255, 64))
    c.mask(thin, (255, 255, 255, 214))
    c.mask(core, (255, 255, 255, 255))
    return c


def generate(out: Out) -> None:
    out.png("textures/gui/dalgona/cookie.png", cookie())
    out.png("textures/gui/dalgona/table.png", table())
    out.png("textures/gui/dalgona/needle.png", needle())
    for i in range(4):
        out.png(f"textures/gui/dalgona/crack_{i}.png", crack(i))


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
