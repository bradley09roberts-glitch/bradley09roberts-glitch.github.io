"""Shared procedural painters: wood, caramel (dalgona), glass spheres, star outlines."""
from __future__ import annotations

import math

import numpy as np

from common import Canvas, bayer, dilate, fbm, ramp, rng, shade, mix, lighten, darken, rgb


# --------------------------------------------------------------------------- noise
def aniso_noise(w: int, h: int, cx: int, cy: int, r: np.random.Generator) -> np.ndarray:
    """Tileable value noise with different feature sizes per axis (cx wide, cy tall)."""
    gw, gh = max(1, w // cx), max(1, h // cy)
    grid = r.random((gh + 1, gw + 1))
    grid[gh, :] = grid[0, :]
    grid[:, gw] = grid[:, 0]
    ys = (np.arange(h) + 0.5) / cy
    xs = (np.arange(w) + 0.5) / cx
    y0 = np.floor(ys).astype(int)
    x0 = np.floor(xs).astype(int)
    fy = ys - y0
    fx = xs - x0
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    g00 = grid[np.ix_(y0, x0)]
    g01 = grid[np.ix_(y0, x0 + 1)]
    g10 = grid[np.ix_(y0 + 1, x0)]
    g11 = grid[np.ix_(y0 + 1, x0 + 1)]
    top = g00 * (1 - fx)[None, :] + g01 * fx[None, :]
    bot = g10 * (1 - fx)[None, :] + g11 * fx[None, :]
    return top * (1 - fy)[:, None] + bot * fy[:, None]


# --------------------------------------------------------------------------- wood
WOOD_PAL = [(108, 72, 42), (126, 86, 50), (144, 100, 60), (160, 114, 70), (176, 130, 82), (190, 146, 96)]


def wood(w: int, h: int, seed: str, plank: int, pal=None, tone_var: float = 0.07, knots: int = 0,
         seams: bool = True, nails: bool = True, scratches: int = 0, gap_col=None) -> np.ndarray:
    """Tileable worn plank wood; planks run along y, ``plank`` px wide (last px is the gap). Returns RGB."""
    r = rng(seed)
    pal = pal or WOOD_PAL
    n = len(pal)
    out = np.zeros((h, w, 3), dtype=np.uint8)
    grain = aniso_noise(w, h, max(2, plank // 4), max(8, h // 2), r)
    fine = aniso_noise(w, h, 2, 16 if h >= 32 else 8, r)
    n_planks = w // plank
    gap_col = gap_col or darken(pal[0], 0.32)
    for p in range(n_planks):
        x0 = p * plank
        off = (r.random() - 0.5) * 2 * tone_var
        for x in range(x0, x0 + plank):
            for y in range(h):
                v = 0.62 * grain[y, x] + 0.38 * fine[y, x] + off
                idx = int(np.clip(math.floor(v * n), 0, n - 1))
                out[y, x] = pal[idx]
        # slightly darker edge px just before the gap, lighter px just after it (bevel)
        for y in range(h):
            out[y, x0 + plank - 2] = shade(out[y, x0 + plank - 2], 0.9)
            out[y, x0] = mix(out[y, x0], (255, 235, 200), 0.10)
        for y in range(h):
            out[y, x0 + plank - 1] = gap_col
        # darker grain streaks
        for _ in range(max(1, plank // 3)):
            gx = x0 + int(r.integers(0, plank - 2))
            gy = int(r.integers(0, h))
            ln = int(r.integers(max(3, h // 8), max(5, h // 3)))
            for k in range(ln):
                yy = (gy + k) % h
                out[yy, gx] = shade(out[yy, gx], 0.9)
        if seams and h >= 16:
            sy = int(r.integers(h // 5, h - h // 5)) if h > 16 else int(r.integers(3, h - 3))
            for x in range(x0, x0 + plank - 1):
                out[sy, x] = shade(out[sy, x], 0.62)
                out[(sy + 1) % h, x] = mix(out[(sy + 1) % h, x], (255, 230, 190), 0.12)
            if nails and plank >= 8:
                for nx in (x0 + 2, x0 + plank - 4):
                    for dy in (-2, 2):
                        yy = (sy + dy) % h
                        out[yy, nx] = (70, 60, 52)
                        out[(yy - 1) % h, nx - 1 if nx > 0 else nx] = shade(out[(yy - 1) % h, nx - 1 if nx > 0 else nx], 1.0)
        for _ in range(knots if p % 2 == 0 else 0):
            kx = x0 + plank // 2 + int(r.integers(-2, 3))
            ky = int(r.integers(6, max(7, h - 6)))
            for dy in range(-5, 6):
                for dx in range(-3, 4):
                    d = (dx / 2.4) ** 2 + (dy / 4.4) ** 2
                    xx, yy = kx + dx, (ky + dy) % h
                    if xx < x0 or xx >= x0 + plank - 1:
                        continue
                    if d <= 0.35:
                        out[yy, xx] = darken(pal[0], 0.35)
                    elif d <= 0.9:
                        out[yy, xx] = darken(pal[0], 0.1)
                    elif d <= 1.5:
                        out[yy, xx] = shade(out[yy, xx], 0.86)
    for _ in range(scratches):
        sx, sy = int(r.integers(0, w)), int(r.integers(0, h))
        ln = int(r.integers(4, 12))
        dx, dy = (1, 0) if r.random() < 0.5 else (1, 1)
        light = r.random() < 0.6
        for k in range(ln):
            x, y = (sx + dx * k) % w, (sy + dy * k) % h
            if (x % plank) == plank - 1:
                continue
            out[y, x] = lighten(out[y, x], 0.18) if light else shade(out[y, x], 0.78)
    return out


# --------------------------------------------------------------------------- caramel / dalgona
CARAMEL = [(112, 60, 16), (136, 78, 22), (160, 98, 30), (184, 122, 42), (204, 146, 60), (222, 170, 88), (238, 196, 122)]


def caramel_field(w: int, h: int, seed: str, scale: int = 1) -> np.ndarray:
    """Mottled caramel colour field (float 0..1) used for dalgona textures."""
    r = rng(seed)
    f = fbm(w, h, r, [(max(2, 32 * scale), 0.45), (max(2, 16 * scale), 0.30), (max(2, 8 * scale), 0.15),
                      (max(1, 4 * scale), 0.10)], wrap=True)
    f = (f - f.min()) / (f.max() - f.min() + 1e-9)
    return f


# --------------------------------------------------------------------------- shapes
def star_polygon(cx: float, cy: float, r_out: float, r_in: float, points: int = 5, rot: float = -90.0):
    pts = []
    for i in range(points * 2):
        ang = math.radians(rot + i * 180.0 / points)
        rr = r_out if i % 2 == 0 else r_in
        pts.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
    return pts


def polygon_outline_mask(c: Canvas, pts, thick: float = 1.0) -> np.ndarray:
    """Union of thick segments along a closed polygon outline."""
    m = np.zeros((c.h, c.w), dtype=bool)
    n = len(pts)
    for i in range(n):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % n]
        m |= c.line_mask(x0, y0, x1, y1, thick)
    return m


# --------------------------------------------------------------------------- glass sphere (marbles)
def glass_marble(size: int, cx: float, cy: float, radius: float, body_pal, swirl_pal, seed: str,
                 outline=None, swirl_turns: float = 1.6, bands: int = 5) -> Canvas:
    """Shaded pixel-art glass marble with a colour swirl inside. Hard bands, no anti-aliasing."""
    r = rng(seed)
    c = Canvas(size)
    xs, ys = c.grid()
    dx, dy = (xs - cx) / radius, (ys - cy) / radius
    d2 = dx * dx + dy * dy
    inside = d2 <= 1.0
    z = np.sqrt(np.clip(1.0 - d2, 0, 1))
    # light from the upper left
    L = np.array([-0.45, -0.55, 0.70])
    L = L / np.linalg.norm(L)
    lam = np.clip(dx * L[0] + dy * L[1] + z * L[2], 0, 1)
    # swirl pattern in sphere space: spiral arms around the view axis, bent by depth
    ang = np.arctan2(dy, dx)
    rad = np.sqrt(d2)
    sw = np.sin(ang * 2 + rad * swirl_turns * math.pi * 2 + z * 1.4)
    arm = sw > 0.35
    # body shading, banded
    shade_v = 0.30 + 0.70 * lam
    body = ramp(shade_v, body_pal)
    arm_col = ramp(0.25 + 0.75 * lam, swirl_pal)
    img = np.where(arm[..., None], arm_col, body)
    c.mask(inside, img)
    # rim darkening on the lower right (thicker glass)
    rim = inside & (d2 > 0.70) & (dx + dy > 0.1)
    arr = c.a[..., :3].astype(float)
    arr[rim] *= 0.82
    c.a[..., :3] = np.clip(arr, 0, 255).astype(np.uint8)
    if outline is not None:
        edge = dilate(inside, 1, diag=False) & ~inside
        c.mask(edge, outline)
    return c
