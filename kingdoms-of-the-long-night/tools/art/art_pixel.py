"""Tiny pixel-art toolkit used by every generator module.

Everything here is deterministic: no function draws random numbers unless it is
handed an explicit ``random.Random`` instance by the caller.
"""
from __future__ import annotations

import random
from pathlib import Path

import numpy as np
from PIL import Image

CLEAR = (0, 0, 0, 0)


# --------------------------------------------------------------------------- colours
def C(v, a: int = 255):
    """Normalise '#rrggbb', 'rrggbb', '#rrggbbaa', (r,g,b) or (r,g,b,a) to an RGBA tuple."""
    if v is None:
        return CLEAR
    if isinstance(v, str):
        s = v.lstrip('#')
        r, g, b = int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16)
        if len(s) == 8:
            a = int(s[6:8], 16)
        return (r, g, b, a)
    v = tuple(int(x) for x in v)
    if len(v) == 3:
        return v + (a,)
    return v


def mix(c1, c2, t: float):
    """Linear mix of two colours (alpha taken from c1)."""
    c1, c2 = C(c1), C(c2)
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3)) + (c1[3],)


def grey(v: float):
    g = int(round(max(0.0, min(1.0, v)) * 255))
    return (g, g, g, 255)


def ramp(base, shadow='#2c1e30', light='#fff2d6', dark=(0.55, 0.32), lite=(0.22, 0.45)):
    """Hue-shifted 5-step ramp [darkest, dark, base, light, lightest].

    Shadows drift toward ``shadow`` (a cool purple-brown by default) and lights
    toward ``light`` (warm cream) which is how hand-made pixel art usually shades.
    """
    b = C(base)
    return [mix(b, shadow, dark[0]), mix(b, shadow, dark[1]), b, mix(b, light, lite[0]), mix(b, light, lite[1])]


def multiply(img: np.ndarray, tint) -> np.ndarray:
    """Multiply RGB of an RGBA array by a tint colour (what the game does at runtime)."""
    t = np.array(C(tint)[:3], dtype=np.float32) / 255.0
    out = img.copy()
    out[..., :3] = np.clip(img[..., :3].astype(np.float32) * t + 0.5, 0, 255).astype(np.uint8)
    return out


def alpha_over(dst: np.ndarray, src: np.ndarray) -> np.ndarray:
    """Binary-alpha composite: opaque pixels of src replace dst."""
    out = dst.copy()
    m = src[..., 3] > 0
    out[m] = src[m]
    return out


# --------------------------------------------------------------------------- canvas
class Canvas:
    """An RGBA numpy canvas with pixel-art drawing helpers. Coordinates are (x, y)."""

    def __init__(self, w: int, h: int, fill=None):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), dtype=np.uint8)
        if fill is not None:
            self.a[:, :] = C(fill)

    # basic access ---------------------------------------------------------
    def inb(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def set(self, x, y, c):
        if self.inb(x, y):
            self.a[y, x] = C(c)

    def get(self, x, y):
        return tuple(int(v) for v in self.a[y, x])

    def opaque(self, x, y):
        return self.inb(x, y) and self.a[y, x, 3] > 0

    def clear(self, x, y):
        if self.inb(x, y):
            self.a[y, x] = CLEAR

    # shapes ---------------------------------------------------------------
    def rect(self, x0, y0, x1, y1, c):
        """Filled rectangle, inclusive corners."""
        x0, x1 = sorted((x0, x1))
        y0, y1 = sorted((y0, y1))
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(self.w - 1, x1), min(self.h - 1, y1)
        if x0 <= x1 and y0 <= y1:
            self.a[y0:y1 + 1, x0:x1 + 1] = C(c)

    def frame(self, x0, y0, x1, y1, c):
        self.hline(x0, x1, y0, c)
        self.hline(x0, x1, y1, c)
        self.vline(x0, y0, y1, c)
        self.vline(x1, y0, y1, c)

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def line(self, x0, y0, x1, y1, c):
        """Bresenham line, inclusive."""
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def disc(self, cx, cy, r, c):
        """Filled disc centred on a pixel-grid point (cx, cy may be .5 values)."""
        for y in range(self.h):
            for x in range(self.w):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.a[y, x] = C(c)

    def ascii(self, rows, pal, ox=0, oy=0):
        """Blit ASCII art. Characters missing from ``pal`` (and '.' / ' ') are skipped."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in ('.', ' '):
                    continue
                if ch not in pal:
                    raise KeyError(f'palette has no entry for {ch!r}')
                col = pal[ch]
                if col is None:
                    self.clear(ox + x, oy + y)
                else:
                    self.set(ox + x, oy + y, col)

    def outline(self, c, diagonal=False, only_outside=True):
        """Paint ``c`` on transparent pixels that touch an opaque pixel."""
        src = self.a[..., 3] > 0
        add = np.zeros_like(src)
        offs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if diagonal:
            offs += [(1, 1), (1, -1), (-1, 1), (-1, -1)]
        for dx, dy in offs:
            sh = np.zeros_like(src)
            ys = slice(max(0, dy), self.h + min(0, dy))
            yd = slice(max(0, -dy), self.h + min(0, -dy))
            xs = slice(max(0, dx), self.w + min(0, dx))
            xd = slice(max(0, -dx), self.w + min(0, -dx))
            sh[yd, xd] = src[ys, xs]
            add |= sh
        add &= ~src
        self.a[add] = C(c)

    def recolor(self, old, new):
        m = np.all(self.a == np.array(C(old), dtype=np.uint8), axis=-1)
        self.a[m] = C(new)

    def paste(self, other: 'Canvas', ox=0, oy=0):
        for y in range(other.h):
            for x in range(other.w):
                if other.a[y, x, 3] > 0:
                    self.set(ox + x, oy + y, tuple(other.a[y, x]))

    def copy(self):
        c = Canvas(self.w, self.h)
        c.a = self.a.copy()
        return c

    def image(self) -> Image.Image:
        return Image.fromarray(self.a, 'RGBA')

    def save(self, path):
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image().save(path, 'PNG', optimize=True)
        return path


# --------------------------------------------------------------------------- texture helpers
def blob(rng: random.Random, w, h, seeds, steps, mask=None, bias=None):
    """Grow an organic cluster mask (used for moss / lichen / stains).

    ``seeds`` are (x, y) start points; each step adds a random 4-neighbour of the
    current cluster. ``bias`` (dx, dy) weights growth direction (e.g. downwards).
    """
    m = np.zeros((h, w), dtype=bool)
    pts = []
    for x, y in seeds:
        if 0 <= x < w and 0 <= y < h:
            m[y, x] = True
            pts.append((x, y))
    if not pts:
        return m
    for _ in range(steps):
        x, y = rng.choice(pts)
        dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if bias:
            dirs += [bias] * 2
        dx, dy = rng.choice(dirs)
        nx, ny = x + dx, y + dy
        if 0 <= nx < w and 0 <= ny < h and not m[ny, nx] and (mask is None or mask[ny, nx]):
            m[ny, nx] = True
            pts.append((nx, ny))
    return m


def scale_nearest(img: Image.Image, k: int) -> Image.Image:
    return img.resize((img.width * k, img.height * k), Image.Resampling.NEAREST)
