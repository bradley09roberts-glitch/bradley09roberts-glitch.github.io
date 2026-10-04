"""Marble minigame GUI textures: closed / open hand (48x48), big glass marble (16x16), ring target (128x128)."""
from __future__ import annotations

import numpy as np

from common import Canvas, Out, dilate, seg_dist
import paint
from gen_items import MARBLE_BODY, MARBLE_SWIRL

OUTLINE = (84, 48, 34)
SKIN = (233, 186, 144)
SKIN_L = (248, 212, 174)
SKIN_D = (202, 148, 108)
SKIN_DD = (168, 114, 82)
SLEEVE = (50, 128, 108)
SLEEVE_D = (36, 96, 82)
SLEEVE_L = (74, 160, 136)
STRIPE = (240, 242, 240)


def _capsule(c: Canvas, x0, y0, x1, y1, r) -> np.ndarray:
    xs, ys = c.grid()
    return seg_dist(xs, ys, x0, y0, x1, y1) <= r


def _rrect(c: Canvas, x0, y0, x1, y1, rad) -> np.ndarray:
    xs, ys = c.grid()
    cx = np.clip(xs, x0 + rad, x1 - rad)
    cy = np.clip(ys, y0 + rad, y1 - rad)
    return (np.hypot(xs - cx, ys - cy) <= rad) & (xs >= x0) & (xs <= x1) & (ys >= y0) & (ys <= y1)


def _part(c: Canvas, m: np.ndarray, base, light, dark, outline=OUTLINE, light_dir=(-1.0, -1.0), spec=None) -> None:
    """Paint a shaded, outlined part over what is already there (painter's algorithm)."""
    ys_, xs_ = np.nonzero(m)
    if len(xs_) == 0:
        return
    cx, cy = xs_.mean() + 0.5, ys_.mean() + 0.5
    ext = max(xs_.max() - xs_.min(), ys_.max() - ys_.min(), 1) / 2.0 + 0.5
    X, Y = c.grid()
    t = ((X - cx) * light_dir[0] + (Y - cy) * light_dir[1]) / (ext * 1.41)     # -1 (dark side) .. 1 (lit side)
    out = dilate(m, 1, diag=False) & ~m
    c.mask(out, outline)
    c.mask(m, base)
    c.mask(m & (t > 0.45), light)
    c.mask(m & (t < -0.40), dark)
    # rim shading hugging the outline on the dark side
    inner_edge = m & dilate(~m, 1, diag=False)
    c.mask(inner_edge & (t < -0.05), dark)
    if spec is not None:
        c.mask(m & (t > 0.8), spec)


def _sleeve(c: Canvas, x0, x1, y0, y1) -> None:
    m = _rrect(c, x0, y0, x1, y1 + 6, 3)
    c.mask(dilate(m, 1, diag=False) & ~m, OUTLINE)
    c.mask(m, SLEEVE)
    xs, ys = c.grid()
    c.mask(m & (xs < x0 + 3), SLEEVE_L)
    c.mask(m & (xs > x1 - 4), SLEEVE_D)
    c.mask(m & (xs >= x0 + 4) & (xs < x0 + 6), STRIPE)      # white stripe down the sleeve
    # cuff (elastic band)
    c.mask(m & (ys >= y0) & (ys < y0 + 3), SLEEVE_D)
    c.mask(m & (ys >= y0 + 3) & (ys < y0 + 4), SLEEVE_L)


def hand_closed() -> Canvas:
    c = Canvas(48)
    # forearm in a green tracksuit sleeve (bottom), then wrist
    _sleeve(c, 14, 34, 38, 47)
    wrist = _rrect(c, 16, 31, 32, 40, 2)
    _part(c, wrist, SKIN, SKIN_L, SKIN_D)
    # back of the fist (mass)
    mass = _rrect(c, 9, 12, 39, 36, 7)
    _part(c, mass, SKIN, SKIN_L, SKIN_D)
    # four curled fingers, each a rounded block with a knuckle crease and a lit fingertip pad
    for i in range(4):
        x0 = 10 + i * 7
        finger = _rrect(c, x0, 11 + (1 if i in (0, 3) else 0), x0 + 7, 31, 3.4)
        _part(c, finger, SKIN, SKIN_L, SKIN_D)
        xs, ys = c.grid()
        crease_y = 21 + (1 if i in (0, 3) else 0)
        c.mask(finger & (ys >= crease_y) & (ys < crease_y + 1) & (xs > x0 + 0.6) & (xs < x0 + 6.4), SKIN_DD)
        c.mask(finger & (ys >= crease_y + 1) & (ys < crease_y + 2) & (xs > x0 + 0.6) & (xs < x0 + 4.6), SKIN_L)
    # thumb across the front
    thumb = _capsule(c, 11, 31.5, 27, 27.5, 3.8)
    _part(c, thumb, SKIN, SKIN_L, SKIN_D)
    xs, ys = c.grid()
    nail = _capsule(c, 24.5, 28.0, 27.0, 27.4, 1.6) & thumb
    c.mask(nail, (246, 214, 200))
    return c


def hand_open() -> Canvas:
    c = Canvas(48)
    _sleeve(c, 13, 35, 40, 47)
    wrist = _rrect(c, 15, 36, 33, 44, 2)
    _part(c, wrist, SKIN, SKIN_L, SKIN_D)
    # thumb (behind the palm edge)
    thumb = _capsule(c, 14, 34.0, 5.5, 25.0, 3.4)
    _part(c, thumb, SKIN, SKIN_L, SKIN_D)
    # fingers: (base x, tip x, tip y, radius)
    fingers = [(17.0, 15.6, 12.0, 3.0), (23.0, 22.4, 7.0, 3.1), (29.0, 29.6, 9.0, 3.0), (34.6, 36.6, 15.5, 2.7)]
    for bx, tx, ty, r in fingers:
        f = _capsule(c, bx, 28.0, tx, ty, r)
        _part(c, f, SKIN, SKIN_L, SKIN_D)
    palm = _rrect(c, 12, 25, 37, 41, 6)
    _part(c, palm, SKIN, SKIN_L, SKIN_D)
    xs, ys = c.grid()
    # palm creases
    c.line(16.5, 33.0, 24.0, 35.4, SKIN_DD, 1.0)
    c.line(24.0, 35.4, 32.5, 32.4, SKIN_DD, 1.0)
    c.line(17.5, 29.0, 27.0, 31.0, SKIN_D, 1.0)
    # finger separations just above the palm
    for x in (20, 26, 32):
        c.mask((xs >= x) & (xs < x + 1) & (ys >= 22) & (ys < 27), SKIN_D)
    # fingertip pads (lit) and tiny nail hints
    for bx, tx, ty, r in fingers:
        c.mask(c.disc_mask(tx, ty + 1.2, 1.3), SKIN_L)
    return c


def marble_big() -> Canvas:
    c = paint.glass_marble(16, 8.0, 8.0, 6.6, MARBLE_BODY, MARBLE_SWIRL, "gui_marble_big", outline=(22, 36, 70),
                           swirl_turns=0.55)
    for (x, y) in [(4, 4), (5, 3), (4, 5), (5, 4)]:
        c.px(x, y, (255, 255, 255))
    c.px(6, 4, (236, 248, 255))
    c.px(11, 12, (214, 240, 255))
    c.px(12, 11, (190, 226, 252))
    return c


def ring_target() -> Canvas:
    """128x128 top-down target: five concentric bands (blue, white, red, white, red bullseye)."""
    S = 128
    c = Canvas(S)
    xs, ys = c.grid()
    d = np.hypot(xs - 64, ys - 64)
    bands = [(62.0, (34, 82, 168)), (50.0, (244, 244, 248)), (38.0, (210, 44, 60)),
             (26.0, (244, 244, 248)), (14.0, (210, 44, 60))]
    shadow = [(62.0, (24, 58, 124)), (50.0, (206, 208, 218)), (38.0, (168, 30, 44)), (26.0, (206, 208, 218)),
              (14.0, (168, 30, 44))]
    light = [(62.0, (64, 118, 204)), (50.0, (255, 255, 255)), (38.0, (238, 84, 98)), (26.0, (255, 255, 255)),
             (14.0, (238, 84, 98))]
    X, Y = xs - 64, ys - 64
    lit = (-(X + Y)) / (np.hypot(X, Y) + 1e-6)         # +1 towards the upper left
    for (R, col), (_, sh), (_, lg) in zip(bands, shadow, light):
        m = d <= R
        c.mask(m, col)
    # bevel each band edge: lit on the upper-left of each ring step, shaded on the lower-right
    for (R, col), (_, sh), (_, lg) in zip(bands, shadow, light):
        edge_out = (d <= R) & (d > R - 3.0)
        c.mask(edge_out & (lit < -0.35), sh)
        c.mask((d <= R - 9.0) & (d > R - 12.0) & (lit > 0.45), lg)
    # dark separator lines
    for R, _ in bands:
        c.mask((d <= R) & (d > R - 1.2), (26, 30, 52))
    # bullseye centre dot
    c.mask(d <= 4.0, (250, 224, 120))
    c.mask(d <= 2.0, (255, 246, 200))
    # outer shadow ring
    c.mask((d > 61.0) & (d <= 63.4), (18, 22, 40))
    return c


def generate(out: Out) -> None:
    out.png("textures/gui/marbles/hand_closed.png", hand_closed())
    out.png("textures/gui/marbles/hand_open.png", hand_open())
    out.png("textures/gui/marbles/marble_big.png", marble_big())
    out.png("textures/gui/marbles/ring_target.png", ring_target())


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
