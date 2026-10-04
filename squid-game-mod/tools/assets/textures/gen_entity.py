"""Non-Geo entity textures: tug-of-war rope, red flag ribbon and the player tracksuit overlays."""
from __future__ import annotations

import math

import numpy as np

from common import Canvas, Out, darken, lighten, mix, ramp, rng, shade, value_noise

# =========================================================================== rope
ROPE_PAL = [(88, 62, 36), (122, 90, 54), (156, 118, 72), (188, 150, 98), (216, 184, 130), (232, 206, 156)]


def rope() -> Canvas:
    """16x16 tileable three-strand natural rope; strands twist along the vertical axis of the image."""
    r = rng("rope")
    c = Canvas(16)
    per = 8.0                                 # two strand repeats across the rope
    tone = np.zeros((16, 16))
    for y in range(16):
        for x in range(16):
            u = ((x + 0.5) + (y + 0.5) * 0.5) % per          # diagonal phase, tiles in both axes
            t = u / per
            bump = math.sin(math.pi * t)                       # 0 in the groove, 1 on the strand crest
            cyl = 1.0 - 0.40 * ((x - 7.5) / 7.5) ** 2          # rope is round: edges fall off
            tone[y, x] = (0.12 + 0.88 * bump ** 0.8) * cyl
    # fibre noise runs along the strand direction (down-left)
    for y in range(16):
        for x in range(16):
            along = (2 * y - x) % 16
            tone[y, x] += (value_noise(16, 16, 8, r)[y, x] - 0.5) * 0.0
    fib = r.random((16, 16))
    tone += (fib - 0.5) * 0.16
    tone = np.clip(tone, 0, 0.999)
    c.fill_rgb(ramp(tone, ROPE_PAL))
    # darken the groove line explicitly so the twist reads at a glance
    for y in range(16):
        for x in range(16):
            u = ((x + 0.5) + (y + 0.5) * 0.5) % per
            if u < 0.9 or u > per - 0.4:
                c.px(x, y, shade(c.get(x, y)[:3], 0.74))
    return c


# =========================================================================== flag ribbon
def rope_flag() -> Canvas:
    """16x16 red cloth ribbon with soft vertical folds, weave and stitched edges (fully opaque)."""
    r = rng("rope_flag")
    base = (196, 36, 52)
    c = Canvas(16, fill=base + (255,))
    for y in range(16):
        for x in range(16):
            fold = 0.5 + 0.5 * math.sin((x + 0.5) / 16.0 * math.tau * 1.5 + y * 0.12)
            weave = 0.02 if (x + y) % 2 == 0 else -0.02
            v = 0.40 + 0.45 * fold + weave + (r.random() - 0.5) * 0.04
            pal = [(118, 18, 34), (150, 24, 42), (178, 32, 50), (204, 42, 58), (226, 70, 84), (240, 108, 118)]
            idx = int(np.clip(math.floor(v * len(pal)), 0, len(pal) - 1))
            c.px(x, y, pal[idx])
    # hemmed edges with a pale stitch line
    for i in range(16):
        c.px(i, 0, (120, 18, 34))
        c.px(i, 15, (120, 18, 34))
        c.px(0, i, (132, 20, 38))
        c.px(15, i, (132, 20, 38))
        if i % 2 == 0:
            c.px(i, 1, (240, 170, 176))
            c.px(i, 14, (240, 170, 176))
    return c


# =========================================================================== tracksuit
G0 = (48, 126, 105)
GL = (64, 148, 124)
GD = (36, 102, 86)
GDD = (27, 78, 67)
WHITE = (238, 240, 238)
WHITE_D = (196, 202, 202)
SILVER = (206, 212, 216)
TEETH = (84, 108, 100)
SHOE = (244, 245, 247)
SHOE_D = (206, 210, 220)
SOLE = (170, 176, 190)
SOLE_D = (124, 130, 146)
LACE = (150, 156, 172)
CLEAR = (0, 0, 0, 0)


def _rects(u0, v0, w, h, d):
    return {
        "top": (u0 + d, v0, w, d),
        "bottom": (u0 + d + w, v0, w, d),
        "right": (u0, v0 + d, d, h),
        "front": (u0 + d, v0 + d, w, h),
        "left": (u0 + d + w, v0 + d, d, h),
        "back": (u0 + 2 * d + w, v0 + d, w, h),
    }


class _Face:
    """Paint helper: local coordinates (fx, fy) inside a face rectangle on the canvas."""

    def __init__(self, c: Canvas, rect):
        self.c, (self.x0, self.y0, self.w, self.h) = c, rect

    def px(self, fx, fy, col):
        if 0 <= fx < self.w and 0 <= fy < self.h:
            self.c.px(self.x0 + fx, self.y0 + fy, col)

    def fill(self, col):
        self.c.rect(self.x0, self.y0, self.x0 + self.w, self.y0 + self.h, col)

    def row(self, fy, col, fx0=0, fx1=None):
        for fx in range(fx0, self.w if fx1 is None else fx1):
            self.px(fx, fy, col)

    def col(self, fx, col, fy0=0, fy1=None):
        for fy in range(fy0, self.h if fy1 is None else fy1):
            self.px(fx, fy, col)


def _knit(f: _Face, base=G0, light=GL, dark=GD, edge_dark: bool = True):
    """Soft knit fabric: faint checker texture, dark edges."""
    f.fill(base)
    for fy in range(f.h):
        for fx in range(f.w):
            if (fx + fy) % 2 == 0 and (fx * 7 + fy * 3) % 5 == 0:
                f.px(fx, fy, light)
    if edge_dark:
        f.col(0, mix(base, dark, 0.55))
        f.col(f.w - 1, mix(base, dark, 0.8))


def _torso(c: Canvas, u0: int, v0: int) -> None:
    R = _rects(u0, v0, 8, 12, 4)
    for name in ("front", "back", "right", "left"):
        f = _Face(c, R[name])
        _knit(f)
        f.row(0, WHITE)                       # collar trim
        f.row(1, mix(G0, WHITE, 0.18))
        f.row(f.h - 1, GD)                    # hem band
        f.row(f.h - 2, mix(G0, GD, 0.5))
    # front: zipper, pull tab, pockets
    f = _Face(c, R["front"])
    f.col(3, TEETH, 0)
    f.col(4, SILVER, 0)
    f.px(3, 2, SILVER)
    f.px(4, 3, WHITE)
    f.px(3, 3, SILVER)
    for fx in (1, 6):
        f.px(fx, 8, GDD)
        f.px(fx, 9, GDD)
        f.px(fx, 10, mix(G0, GL, 0.6))
    # back: centre seam
    b = _Face(c, R["back"])
    b.col(3, mix(G0, GD, 0.4), 2)
    # top (shoulders / collar top) and bottom (hem) faces
    _knit(_Face(c, R["top"]), edge_dark=False)
    _Face(c, R["top"]).fill(mix(G0, WHITE, 0.14))
    _knit(_Face(c, R["bottom"]), base=GD, edge_dark=False)


def _arm(c: Canvas, u0: int, v0: int, w: int, outer: str, inner: str) -> None:
    R = _rects(u0, v0, w, 12, 4)
    for name in ("front", "back", "right", "left"):
        f = _Face(c, R[name])
        _knit(f)
        # cuff bands, hand left transparent (skin shows through)
        f.row(8, GD)
        f.row(9, GDD)
        f.row(10, CLEAR)
        f.row(11, CLEAR)
        for fx in range(f.w):
            f.px(fx, 10, CLEAR)
            f.px(fx, 11, CLEAR)
        f.row(0, mix(G0, WHITE, 0.12))
    o = _Face(c, R[outer])
    for fy in range(0, 8):
        o.px(1, fy, WHITE)
        o.px(2, fy, WHITE_D)
    i = _Face(c, R[inner])
    i.col(0 if inner == "left" else i.w - 1, GD, 0, 9)
    t = _Face(c, R["top"])
    _knit(t, edge_dark=False)
    t.fill(mix(G0, WHITE, 0.10))
    # bottom (the hand) stays transparent


def _leg(c: Canvas, u0: int, v0: int, outer: str, inner: str) -> None:
    R = _rects(u0, v0, 4, 12, 4)
    for name in ("front", "back", "right", "left"):
        f = _Face(c, R[name])
        _knit(f)
        f.row(0, GD)                          # waistband
        f.row(1, mix(G0, GD, 0.45))
        f.row(8, GD)                          # ankle cuff
        # sneaker
        f.row(9, SHOE)
        f.row(10, SHOE)
        f.row(11, SOLE)
    fr = _Face(c, R["front"])
    fr.px(1, 9, LACE)
    fr.px(2, 9, LACE)
    fr.px(0, 10, SHOE_D)
    fr.px(3, 10, SHOE_D)
    bk = _Face(c, R["back"])
    bk.row(10, SHOE_D)
    bk.px(1, 9, SHOE_D)
    bk.px(2, 9, SHOE_D)
    o = _Face(c, R[outer])
    for fy in range(2, 8):
        o.px(1, fy, WHITE)
        o.px(2, fy, WHITE_D)
    for fx in range(o.w):
        o.px(fx, 10, SHOE if fx not in (1, 2) else SHOE_D)       # side stripe on the shoe
    inn = _Face(c, R[inner])
    inn.col(0 if inner == "left" else inn.w - 1, GD, 0, 9)
    top = _Face(c, R["top"])
    top.fill(GD)
    sole = _Face(c, R["bottom"])
    sole.fill(SOLE)
    for fy in range(sole.h):
        for fx in range(sole.w):
            if (fx + fy) % 2 == 0:
                sole.px(fx, fy, SOLE_D)


def tracksuit(slim: bool) -> Canvas:
    c = Canvas(64)
    aw = 3 if slim else 4
    # first layer (body, arms, legs) -- head area stays transparent
    _torso(c, 16, 16)
    _arm(c, 40, 16, aw, outer="right", inner="left")      # right arm
    _arm(c, 32, 48, aw, outer="left", inner="right")      # left arm
    _leg(c, 0, 16, outer="right", inner="left")           # right leg
    _leg(c, 16, 48, outer="left", inner="right")          # left leg
    # second layer (jacket, sleeves, trousers)
    _torso(c, 16, 32)
    _arm(c, 40, 32, aw, outer="right", inner="left")      # right sleeve
    _arm(c, 48, 48, aw, outer="left", inner="right")      # left sleeve
    _leg(c, 0, 32, outer="right", inner="left")           # right trouser leg
    _leg(c, 0, 48, outer="left", inner="right")           # left trouser leg
    return c


def generate(out: Out) -> None:
    out.png("textures/entity/rope.png", rope())
    out.png("textures/entity/rope_flag.png", rope_flag())
    out.png("textures/entity/player_tracksuit.png", tracksuit(False))
    out.png("textures/entity/player_tracksuit_slim.png", tracksuit(True))


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
