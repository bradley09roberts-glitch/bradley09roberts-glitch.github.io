"""Pixel-art painting helpers.  All functions work in-place on a float RGBA array view
``a`` of shape (h, w, 4) with straight alpha in [0, 1].  No anti-aliasing: every pixel is
either fully set or untouched, so the result is crisp pixel art with 1:1 texel density."""
from __future__ import annotations

import numpy as np

BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=float) + 0.5) / 16.0


def hx(s: str) -> np.ndarray:
    s = s.lstrip("#")
    if len(s) == 3:
        s = "".join(ch * 2 for ch in s)
    return np.array([int(s[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


def mix(c0, c1, t):
    return np.asarray(c0) * (1 - t) + np.asarray(c1) * t


def shade(c, f):
    """f<1 darkens, f>1 lightens (towards white)."""
    c = np.asarray(c, dtype=float)
    if f <= 1:
        return np.clip(c * f, 0, 1)
    return np.clip(c + (1 - c) * (f - 1), 0, 1)


def _px(a, x, y, c, alpha=1.0):
    h, w = a.shape[:2]
    if 0 <= x < w and 0 <= y < h:
        a[y, x, :3] = c
        a[y, x, 3] = alpha


def put(a, x, y, c):
    _px(a, int(x), int(y), c)


def clear(a):
    a[...] = 0


def fill(a, c):
    a[..., :3] = c
    a[..., 3] = 1.0


def rect(a, x, y, w, h, c):
    H, W = a.shape[:2]
    x0, x1 = max(int(x), 0), min(int(x + w), W)
    y0, y1 = max(int(y), 0), min(int(y + h), H)
    if x1 > x0 and y1 > y0:
        a[y0:y1, x0:x1, :3] = c
        a[y0:y1, x0:x1, 3] = 1.0


def hline(a, x0, x1, y, c):
    rect(a, min(x0, x1), y, abs(x1 - x0) + 1, 1, c)


def vline(a, x, y0, y1, c):
    rect(a, x, min(y0, y1), 1, abs(y1 - y0) + 1, c)


def line(a, x0, y0, x1, y1, c):
    x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        _px(a, x0, y0, c)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def _grid(a):
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    return xx + 0.5, yy + 0.5


def disc(a, cx, cy, r, c):
    xx, yy = _grid(a)
    m = (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r
    a[m, :3] = c
    a[m, 3] = 1.0
    return m


def ellipse(a, cx, cy, rx, ry, c):
    xx, yy = _grid(a)
    m = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1.0
    a[m, :3] = c
    a[m, 3] = 1.0
    return m


def ellipse_mask(shape, cx, cy, rx, ry):
    h, w = shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    return (((xx + 0.5 - cx) / rx) ** 2 + ((yy + 0.5 - cy) / ry) ** 2) <= 1.0


def set_mask(a, m, c, alpha=1.0):
    a[m, :3] = c
    a[m, 3] = alpha


def dither_blend(a, m, c, t_map):
    """Where mask m: set colour c wherever Bayer threshold < t_map (t_map 0..1 per pixel)."""
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    th = BAYER4[yy % 4, xx % 4]
    sel = m & (t_map > th)
    a[sel, :3] = c
    a[sel, 3] = 1.0


def vgrad(a, ramp, y0=0, y1=None, mask=None, x0=0, x1=None):
    """Vertical dithered gradient through a colour ramp (list of colours), top -> bottom."""
    h, w = a.shape[:2]
    y1 = h if y1 is None else y1
    x1 = w if x1 is None else x1
    n = len(ramp)
    yy, xx = np.mgrid[0:h, 0:w]
    t = np.clip((yy + 0.5 - y0) / max(y1 - y0, 1e-6), 0, 1) * (n - 1)
    th = BAYER4[yy % 4, xx % 4]
    # idx = floor(t + threshold), threshold in (0,1): ordered dithering between neighbouring ramp entries
    idx = np.clip(np.floor(t + th).astype(int), 0, n - 1)
    sel = np.zeros((h, w), dtype=bool)
    sel[y0:y1, x0:x1] = True
    if mask is not None:
        sel &= mask
    cols = np.array(ramp)[idx]
    a[sel, :3] = cols[sel]
    a[sel, 3] = 1.0


def hgrad(a, ramp, x0=0, x1=None, mask=None):
    h, w = a.shape[:2]
    x1 = w if x1 is None else x1
    n = len(ramp)
    yy, xx = np.mgrid[0:h, 0:w]
    t = np.clip((xx + 0.5 - x0) / max(x1 - x0, 1e-6), 0, 1) * (n - 1)
    th = BAYER4[yy % 4, xx % 4]
    idx = np.clip(np.floor(t + th).astype(int), 0, n - 1)
    sel = np.ones((h, w), dtype=bool) if mask is None else mask
    cols = np.array(ramp)[idx]
    a[sel, :3] = cols[sel]
    a[sel, 3] = 1.0


def radial(a, cx, cy, r, ramp, mask=None, power=1.0):
    """Radial dithered gradient: ramp[0] at centre -> ramp[-1] at radius r."""
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2) / r
    n = len(ramp)
    t = np.clip(d, 0, 1) ** power * (n - 1)
    th = BAYER4[yy % 4, xx % 4]
    idx = np.clip(np.floor(t + th).astype(int), 0, n - 1)
    sel = (d <= 1.0) if mask is None else (mask & (d <= 1.0))
    cols = np.array(ramp)[idx]
    a[sel, :3] = cols[sel]
    a[sel, 3] = 1.0


def speckle(a, rng, c, density, mask=None):
    h, w = a.shape[:2]
    m = rng.random_sample((h, w)) < density
    if mask is not None:
        m &= mask
    m &= a[..., 3] > 0.5
    a[m, :3] = c


def noise_tint(a, rng, amount=0.04, mask=None):
    """Per-pixel brightness jitter (quantised to 3 levels so it stays pixel-arty)."""
    h, w = a.shape[:2]
    lv = rng.randint(-1, 2, size=(h, w)).astype(float) * amount
    m = a[..., 3] > 0.5
    if mask is not None:
        m &= mask
    a[m, :3] = np.clip(a[m, :3] + lv[m][:, None], 0, 1)


def darken_edge(a, amount=0.15, sides=("b", "r"), width=1):
    h, w = a.shape[:2]
    if "b" in sides:
        a[h - width:, :, :3] *= (1 - amount)
    if "r" in sides:
        a[:, w - width:, :3] *= (1 - amount)
    if "t" in sides:
        a[:width, :, :3] *= (1 - amount)
    if "l" in sides:
        a[:, :width, :3] *= (1 - amount)


def lighten_edge(a, amount=0.12, sides=("t", "l"), width=1):
    h, w = a.shape[:2]

    def f(sl):
        a[sl][..., :3] = np.clip(a[sl][..., :3] + (1 - a[sl][..., :3]) * amount, 0, 1)

    if "t" in sides:
        f((slice(0, width), slice(None)))
    if "l" in sides:
        f((slice(None), slice(0, width)))
    if "b" in sides:
        f((slice(h - width, h), slice(None)))
    if "r" in sides:
        f((slice(None), slice(w - width, w)))


def stamp(a, x, y, rows, palette):
    """Draw a small pixel sprite: rows = list of strings; palette maps a char -> colour (or None)."""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            c = palette.get(ch)
            if c is not None:
                _px(a, int(x) + i, int(y) + j, c)


def outline_mask(m):
    """Boolean mask of the 4-neighbour inner border of m."""
    p = np.pad(m, 1, constant_values=False)
    inner = p[1:-1, 1:-1] & p[:-2, 1:-1] & p[2:, 1:-1] & p[1:-1, :-2] & p[1:-1, 2:]
    return m & ~inner
