"""Tiny pixel-art painting toolkit used by the model generators.

* :class:`Canvas` - RGBA float canvas that is quantised to uint8 when saved.
* :class:`Face`   - a rectangular window onto the canvas with face-local ``(i, j)`` coordinates
  (``i`` to the right, ``j`` downwards, as seen by a viewer looking at that face from outside).
* :class:`BoxPaint` - the six faces of one box-UV cube (see ``geom.box_faces`` for orientation).
* deterministic integer-hash noise so every run produces identical pixels.
"""
from __future__ import annotations

import math
from typing import Callable, Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np
from PIL import Image

from .geom import box_faces

Color = Tuple[int, int, int]


# ----------------------------------------------------------------------------------
# colour helpers
# ----------------------------------------------------------------------------------
def hexc(s: str) -> Color:
    s = s.lstrip("#")
    return int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16)


def mix(a: Sequence[float], b: Sequence[float], t: float) -> Color:
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))  # type: ignore


def mul(c: Sequence[float], k: float) -> Color:
    return tuple(int(round(max(0, min(255, x * k)))) for x in c)  # type: ignore


def add(c: Sequence[float], d: float) -> Color:
    return tuple(int(round(max(0, min(255, x + d)))) for x in c)  # type: ignore


def grey(v: float) -> Color:
    v = int(round(max(0, min(255, v))))
    return (v, v, v)


def ramp(colors: Sequence[Color], t: float) -> Color:
    """Pick from a discrete colour ramp: t in colour-index units, rounded and clamped."""
    i = int(math.floor(t + 0.5))
    i = max(0, min(len(colors) - 1, i))
    return colors[i]


def hash01(x: int, y: int, seed: int = 0) -> float:
    """Deterministic pseudo-random in [0,1) from integer coordinates."""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    h = (h ^ (h >> 16)) & 0xFFFFFFFF
    return h / 4294967296.0


# ----------------------------------------------------------------------------------
# Canvas / Face
# ----------------------------------------------------------------------------------
class Canvas:
    def __init__(self, w: int, h: int):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), dtype=np.float32)  # RGBA 0..255, alpha 0 = transparent

    def put(self, x: int, y: int, c: Sequence[float], alpha: float = 255.0) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x, :3] = c[:3]
            self.a[y, x, 3] = alpha

    def get(self, x: int, y: int) -> Tuple[float, float, float, float]:
        return tuple(self.a[y, x])  # type: ignore

    def save(self, path: str) -> None:
        arr = np.clip(np.rint(self.a), 0, 255).astype(np.uint8)
        # fully transparent pixels: zero the colour for determinism / smaller PNGs
        arr[arr[..., 3] == 0, :3] = 0
        Image.fromarray(arr, "RGBA").save(path, optimize=True)


class Face:
    """Window onto a canvas. ``i`` = column (viewer's right), ``j`` = row (down)."""

    def __init__(self, cv: Canvas, x: int, y: int, w: int, h: int, name: str = ""):
        self.cv, self.x, self.y, self.w, self.h, self.name = cv, x, y, w, h, name

    # primitive -------------------------------------------------------------------
    def set(self, i: int, j: int, c: Sequence[float], alpha: float = 255.0) -> None:
        if 0 <= i < self.w and 0 <= j < self.h:
            self.cv.put(self.x + i, self.y + j, c, alpha)

    def get(self, i: int, j: int):
        return self.cv.get(self.x + i, self.y + j)

    def rect(self, i: int, j: int, w: int, h: int, c: Sequence[float], alpha: float = 255.0) -> None:
        for jj in range(j, j + h):
            for ii in range(i, i + w):
                self.set(ii, jj, c, alpha)

    def hline(self, j: int, i0: int, i1: int, c: Sequence[float]) -> None:
        for i in range(i0, i1 + 1):
            self.set(i, j, c)

    def vline(self, i: int, j0: int, j1: int, c: Sequence[float]) -> None:
        for j in range(j0, j1 + 1):
            self.set(i, j, c)

    def clear(self) -> None:
        for j in range(self.h):
            for i in range(self.w):
                self.cv.a[self.y + j, self.x + i, :] = 0

    def fill(self, c: Sequence[float], alpha: float = 255.0) -> None:
        self.rect(0, 0, self.w, self.h, c, alpha)

    def fn(self, f: Callable[[int, int], Optional[Sequence[float]]]) -> None:
        """Paint every pixel with ``f(i, j)`` (None = leave untouched)."""
        for j in range(self.h):
            for i in range(self.w):
                c = f(i, j)
                if c is not None:
                    self.set(i, j, c)

    def shade_where(self, pred: Callable[[int, int], bool], k: float) -> None:
        """Multiply existing opaque pixels matching ``pred`` by ``k``."""
        for j in range(self.h):
            for i in range(self.w):
                if pred(i, j):
                    px = self.cv.a[self.y + j, self.x + i]
                    if px[3] > 0:
                        px[:3] = np.clip(px[:3] * k, 0, 255)

    def edge_ao(self, k: float = 0.88, sides: str = "lrtb", depth: int = 1) -> None:
        """Darken the outer ring of pixels (cheap ambient occlusion on cube edges)."""
        def pred(i, j):
            return (("l" in sides and i < depth) or ("r" in sides and i >= self.w - depth)
                    or ("t" in sides and j < depth) or ("b" in sides and j >= self.h - depth))
        self.shade_where(pred, k)

    def stamp(self, i0: int, j0: int, rows: Sequence[str], palette: Dict[str, Sequence[float]]) -> None:
        """ASCII-art stamp: '.' (or ' ') = transparent/untouched."""
        for dj, row in enumerate(rows):
            for di, ch in enumerate(row):
                if ch in ". ":
                    continue
                self.set(i0 + di, j0 + dj, palette[ch])

    def stamp_mask(self, rows: Sequence[str]) -> np.ndarray:
        return np.array([[ch not in ". " for ch in r] for r in rows])


class BoxPaint:
    """The six faces of a box-UV cube at ``(u, v)``."""

    def __init__(self, cv: Canvas, u: int, v: int, sx: int, sy: int, sz: int, name: str = ""):
        self.u, self.v, self.sx, self.sy, self.sz, self.name = u, v, sx, sy, sz, name
        rects = box_faces(u, v, sx, sy, sz)
        self.faces: Dict[str, Face] = {k: Face(cv, *r, name=f"{name}.{k}") for k, r in rects.items()}

    def __getitem__(self, k: str) -> Face:
        return self.faces[k]

    @property
    def front(self) -> Face: return self.faces["north"]
    @property
    def back(self) -> Face: return self.faces["south"]
    @property
    def right(self) -> Face: return self.faces["east"]   # entity's right side
    @property
    def left(self) -> Face: return self.faces["west"]    # entity's left side
    @property
    def top(self) -> Face: return self.faces["up"]
    @property
    def bottom(self) -> Face: return self.faces["down"]

    def each(self):
        return self.faces.values()

    def fill(self, c: Sequence[float]) -> None:
        for f in self.each():
            f.fill(c)


# ----------------------------------------------------------------------------------
# reusable surface generators
# ----------------------------------------------------------------------------------
def fabric(face: Face, colors: Sequence[Color], seed: int = 0, noise: float = 0.5, top_light: float = 0.35,
           bottom_dark: float = 0.55, base_index: float = 1.0) -> None:
    """Soft knit/jersey: discrete ramp, vertical light gradient, sparse speckle."""
    h = max(face.h - 1, 1)
    for j in range(face.h):
        g = top_light * (1 - j / h) - bottom_dark * (j / h)
        for i in range(face.w):
            n = (hash01(face.x + i, face.y + j, seed) - 0.5) * 2 * noise
            if hash01(face.x + i, face.y + j, seed + 91) > 0.82:
                n *= 1.6
            face.set(i, j, ramp(colors, base_index + g + n))


def speckle(face: Face, c: Sequence[float], p: float, seed: int = 0, alpha: float = 255.0) -> None:
    for j in range(face.h):
        for i in range(face.w):
            if hash01(face.x + i, face.y + j, seed) < p and face.get(i, j)[3] > 0:
                face.set(i, j, c, alpha)
