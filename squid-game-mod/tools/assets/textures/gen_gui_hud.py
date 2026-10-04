"""HUD icons (16x16, white-ish, transparent background -> tintable), number plate, red vignette, panel."""
from __future__ import annotations

import math

import numpy as np

from common import Canvas, Out, dilate, erode, rng

W = (246, 246, 248)      # main line colour
M = (204, 204, 212)      # secondary (details)
CLEAR = (0, 0, 0, 0)


def _rows(rows, legend=None) -> Canvas:
    legend = legend or {"#": W, "+": M}
    c = Canvas(16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in legend:
                c.px(x, y, legend[ch])
    return c


# ------------------------------------------------------------------------------ people
def _sprite(c: Canvas, x0: int, y0: int, rows, col) -> np.ndarray:
    """Paint an 'X' sprite and return its mask."""
    m = np.zeros((c.h, c.w), dtype=bool)
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == "X":
                m[y0 + dy, x0 + dx] = True
    c.mask(m, col)
    return m


def icon_survivors() -> Canvas:
    """Three people: a bold one in front, two shadow figures partly hidden behind."""
    c = Canvas(16)
    head_s = [".XX.", "XXXX", "XXXX", ".XX."]
    c_head = [".XXXX.", "XXXXXX", "XXXXXX", "XXXXXX", "XXXXXX", ".XXXX."]
    c_body = [".XXXXXX.", "XXXXXXXX", "XXXXXXXX", "XXXXXXXX", "XXXXXXXX", "XXXXXXXX", "XXXXXXXX", "XXXXXXXX"]
    body_l = [".XXX", "XXXX", "XXXX", "XXXX", "XXXX"]
    body_r = ["XXX.", "XXXX", "XXXX", "XXXX", "XXXX"]
    probe = Canvas(16)
    front = _sprite(probe, 4, 7, c_body, W)               # front body, used to cut a 1px gap into the back figures
    gap = dilate(front, 1, diag=False)
    back = Canvas(16)
    mask_back = _sprite(back, 0, 10, body_l, M) | _sprite(back, 12, 10, body_r, M)
    c.mask(mask_back & ~gap, M)
    _sprite(c, 0, 5, head_s, M)
    _sprite(c, 12, 5, head_s, M)
    _sprite(c, 5, 0, c_head, W)
    _sprite(c, 4, 7, c_body, W)
    return c


# ------------------------------------------------------------------------------ timer
def icon_timer() -> Canvas:
    c = Canvas(16)
    xs, ys = c.grid()
    d2 = (xs - 8) ** 2 + (ys - 9) ** 2
    c.mask((d2 <= 6.5 ** 2) & (d2 > 5.5 ** 2), W)              # case
    c.rect(6, 0, 10, 1, W)                                      # crown
    c.rect(7, 1, 9, 3, W)
    for (x, y) in [(12, 3), (13, 2), (13, 3), (12, 2)]:
        c.px(x, y, W)                                           # side pusher
    c.rect(7, 5, 9, 10, W)                                      # minute hand (to 12)
    c.rect(9, 8, 12, 10, W)                                     # hour hand (to 3)
    return c


# ------------------------------------------------------------------------------ stamina bolt
def icon_stamina() -> Canvas:
    c = Canvas(16)
    pts = [(9.2, 0.0), (2.6, 9.2), (7.2, 9.2), (5.0, 16.0), (13.6, 6.2), (9.0, 6.2), (12.6, 0.0)]
    m = c.poly_mask(pts)
    c.mask(m, W)
    return c


# ------------------------------------------------------------------------------ marble
def icon_marble() -> Canvas:
    c = Canvas(16)
    xs, ys = c.grid()
    d2 = (xs - 8) ** 2 + (ys - 8) ** 2
    c.mask((d2 <= 6.5 ** 2) & (d2 > 5.5 ** 2), W)
    pts = [(5.0, 9.6), (6.0, 11.0), (8.0, 11.4), (10.2, 10.4), (10.8, 8.2), (9.4, 6.4), (7.2, 6.4), (6.4, 8.0), (7.6, 8.8)]
    for a, b in zip(pts, pts[1:]):
        c.line(a[0], a[1], b[0], b[1], M, 1.0)
    for (x, y) in [(4, 4), (5, 3), (4, 5)]:
        c.px(x, y, W)
    return c


# ------------------------------------------------------------------------------ lollipop (lick)
def icon_lick() -> Canvas:
    """Swirl lollipop (the dalgona 'lick' action): ring + inner spiral + stick."""
    c = Canvas(16)
    xs, ys = c.grid()
    cx = cy = 6.0
    d = np.hypot(xs - cx, ys - cy)
    c.mask((d <= 5.6) & (d > 4.6), W)
    pts = []
    th = 0.0
    while True:
        rr = 0.5 + 2.0 * th / (2 * math.pi)
        if rr > 4.1:
            break
        pts.append((cx + rr * math.cos(th + 1.0), cy + rr * math.sin(th + 1.0)))
        th += 0.12
    spiral = np.zeros((16, 16), dtype=bool)
    for a, b in zip(pts, pts[1:]):
        spiral |= c.line_mask(a[0], a[1], b[0], b[1], 1.0)
    c.mask(spiral, W)
    c.line(10.2, 10.2, 14.6, 14.6, W, 2.0)
    return c


# ------------------------------------------------------------------------------ skull
def icon_skull() -> Canvas:
    c = Canvas(16)
    xs, ys = c.grid()
    cran = ((xs - 8) / 6.1) ** 2 + ((ys - 6.7) / 5.7) ** 2 <= 1.0
    jaw = (np.abs(xs - 8) <= 3.9) & (ys >= 9.6) & (ys <= 14.0)
    sk = cran | jaw
    c.mask(sk, W)
    eye = ((xs - 5.2) ** 2 + (ys - 7.5) ** 2 <= 1.95 ** 2) | ((xs - 10.8) ** 2 + (ys - 7.5) ** 2 <= 1.95 ** 2)
    c.mask(eye & sk, CLEAR)
    nose = (np.abs(xs - 8) <= 0.9) & (ys >= 9.2) & (ys <= 10.9)
    c.mask(nose & sk, CLEAR)
    for x in (6, 8, 10):
        c.rect(x, 12, x + 1, 14, CLEAR)
    return c


# ------------------------------------------------------------------------------ warning triangle
def icon_warning() -> Canvas:
    return _rows([
        "................",
        ".......##.......",
        "......####......",
        "......#..#......",
        ".....##..##.....",
        ".....#.##.#.....",
        "....##.##.##....",
        "....#..##..#....",
        "...##..##..##...",
        "...#...##...#...",
        "..##...##...##..",
        "..#..........#..",
        ".##....##....##.",
        ".#.....##.....#.",
        ".##############.",
        "................",
    ])


# ------------------------------------------------------------------------------ eyes
def _lens(c: Canvas, cx, cy, half_w, half_h):
    xs, ys = c.grid()
    R = (half_w ** 2 + half_h ** 2) / (2 * half_h)
    d_top = (xs - cx) ** 2 + (ys - (cy + R - half_h)) ** 2 <= R * R
    d_bot = (xs - cx) ** 2 + (ys - (cy - R + half_h)) ** 2 <= R * R
    return d_top & d_bot


def icon_eye_red() -> Canvas:
    """Open, staring eye with alert rays (hint of red so it also reads untinted)."""
    c = Canvas(16)
    line = (255, 200, 200)
    iris = (255, 112, 112)
    xs, ys = c.grid()
    lens = _lens(c, 8.0, 9.0, 7.2, 3.9)
    c.mask(lens & ~erode(lens, 1), line)
    disc = (xs - 8) ** 2 + (ys - 9) ** 2 <= 2.9 ** 2
    c.mask(disc & lens, iris)
    pupil = (xs - 8) ** 2 + (ys - 9) ** 2 <= 1.2 ** 2
    c.mask(pupil & lens, (255, 255, 255))
    for (x0, y0, x1, y1) in [(8, 0.6, 8, 2.8), (3.6, 1.6, 4.8, 3.6), (12.4, 1.6, 11.2, 3.6)]:
        c.line(x0, y0, x1, y1, line, 1.0)
    return c


def icon_eye_green() -> Canvas:
    """Calm open eye under a relaxed brow (hint of green so it also reads untinted)."""
    c = Canvas(16)
    line = (200, 255, 220)
    iris = (112, 255, 164)
    xs, ys = c.grid()
    lens = _lens(c, 8.0, 9.0, 7.2, 3.9)
    c.mask(lens & ~erode(lens, 1), line)
    disc = (xs - 8) ** 2 + (ys - 9) ** 2 <= 2.9 ** 2
    c.mask(disc & lens, iris)
    c.mask((xs - 8) ** 2 + (ys - 9) ** 2 <= 1.2 ** 2, (255, 255, 255))
    # relaxed brow arc
    brow = (np.abs(ys - (3.0 + 1.6 * ((xs - 8) / 6.0) ** 2)) <= 0.55) & (np.abs(xs - 8) <= 5.4)
    c.mask(brow, line)
    return c


# ------------------------------------------------------------------------------ heart
def icon_heart() -> Canvas:
    c = _rows([
        "................",
        "...####..####...",
        "..######.######.",
        ".###############",
        ".###############",
        ".###############",
        ".###############",
        "..#############.",
        "...###########..",
        "....#########...",
        ".....#######....",
        "......#####.....",
        ".......###......",
        "........#.......",
        "................",
        "................",
    ])
    for (x, y) in [(3, 3), (4, 3), (3, 4)]:
        c.px(x, y, (255, 255, 255))
    for (x, y) in [(12, 7), (11, 8), (10, 9), (9, 10), (8, 11)]:
        c.px(x, y, M)
    return c


# ------------------------------------------------------------------------------ rope (looped, crossed tails)
def icon_rope() -> Canvas:
    """Tug-of-war rope: twisted horizontal strand with a flag on the centre line."""
    c = Canvas(16)
    band = np.zeros((16, 16), dtype=bool)
    band[9:12, 0:16] = True
    c.mask(band, W)
    yy, xx = np.nonzero(band)
    for y, x in zip(yy, xx):
        if (x + (y - 9)) % 4 == 0:
            c.px(int(x), int(y), M)
    c.px(0, 9, CLEAR)
    c.px(0, 11, CLEAR)
    c.px(15, 9, CLEAR)
    c.px(15, 11, CLEAR)
    # flag pole and pennant
    c.rect(8, 2, 9, 9, W)
    c.mask(c.poly_mask([(9.0, 2.0), (14.0, 4.0), (9.0, 6.0)]), W)
    # strain marks at both ends
    for (x, y) in [(1, 6), (2, 7), (14, 14), (13, 13)]:
        pass
    return c


# ------------------------------------------------------------------------------ glass pane
def icon_glass() -> Canvas:
    c = Canvas(16)
    c.outline(2, 2, 14, 14, W)
    c.outline(3, 3, 13, 13, M)
    for (x, y) in [(5, 8), (6, 7), (7, 6), (8, 5)]:
        c.px(x, y, W)
    for (x, y) in [(5, 6), (6, 5)]:
        c.px(x, y, W)
    for (x, y) in [(10, 11), (11, 10)]:
        c.px(x, y, W)
    return c


# ------------------------------------------------------------------------------ guard symbols
def icon_circle() -> Canvas:
    c = Canvas(16)
    xs, ys = c.grid()
    d2 = (xs - 8) ** 2 + (ys - 8) ** 2
    c.mask((d2 <= 7.0 ** 2) & (d2 > 5.0 ** 2), W)
    return c


def icon_triangle() -> Canvas:
    c = Canvas(16)
    xs, ys = c.grid()

    def tri(top, bot, half):
        t = (ys - top) / (bot - top)
        return (ys >= top) & (ys <= bot) & (np.abs(xs - 8) <= half * t)

    c.mask(tri(1.0, 14.6, 7.4) & ~tri(5.2, 12.6, 3.9), W)
    return c


def icon_square() -> Canvas:
    c = Canvas(16)
    c.rect(1, 1, 15, 15, W)
    c.rect(3, 3, 13, 13, CLEAR)
    return c


# ------------------------------------------------------------------------------ number plate / vignette / panel
def number_plate() -> Canvas:
    r = rng("number_plate")
    c = Canvas(32, 16, fill=(247, 245, 239, 255))
    for _ in range(26):
        x, y = int(r.integers(1, 31)), int(r.integers(1, 15))
        c.px(x, y, (236, 233, 226))
    c.outline(0, 0, 32, 16, (132, 130, 124))
    c.outline(1, 1, 31, 15, (234, 232, 225))
    for (x, y) in [(0, 0), (31, 0), (0, 15), (31, 15)]:
        c.px(x, y, CLEAR)
    for (x, y) in [(2, 2), (29, 2), (2, 13), (29, 13)]:
        c.px(x, y, (190, 188, 182))
    return c


def vignette_red(size: int = 256) -> Canvas:
    """Radial vignette: transparent centre, opaque-ish red edges. Smooth, lightly dithered.

    The RGB is a pale red (not pure red) so Java can multiply it with any danger colour (``RenderSystem.setShaderColor``)
    and still get that colour at full strength, while an untinted blit still reads as a red/pink danger flash.
    """
    c = Canvas(size)
    ys, xs = np.mgrid[0:size, 0:size]
    cx = cy = (size - 1) / 2.0
    dx = np.abs(xs - cx) / (size / 2.0)
    dy = np.abs(ys - cy) / (size / 2.0)
    d = (dx ** 3 + dy ** 3) ** (1 / 3.0)
    t = np.clip((d - 0.38) / (1.12 - 0.38), 0, 1)
    a = (t ** 1.9) * 0.94
    noise = ((xs * 73856093) ^ (ys * 19349663)) % 256 / 256.0 - 0.5
    alpha = np.clip(np.round(a * 255 + noise * 1.2), 0, 255).astype(np.uint8)
    c.a[..., 0] = 255
    c.a[..., 1] = 150
    c.a[..., 2] = 150
    c.a[..., 3] = alpha
    return c


def panel() -> Canvas:
    """48x48 nine-slice (4px slices): translucent dark body, thin pink border (same colours as SquidHud's panels)."""
    n = 48
    pink = (224, 69, 123)                      # SquidHud.PINK
    c = Canvas(n, fill=(16, 16, 24, 176))      # SquidHud.PANEL
    c.outline(0, 0, n, n, pink + (255,))
    c.outline(1, 1, n - 1, n - 1, (24, 20, 34, 214))
    c.outline(2, 2, n - 2, n - 2, pink + (64,))
    for (x, y) in [(0, 0), (n - 1, 0), (0, n - 1), (n - 1, n - 1)]:
        c.px(x, y, CLEAR)
    for (x, y) in [(1, 0), (0, 1), (n - 2, 0), (n - 1, 1), (1, n - 1), (0, n - 2), (n - 2, n - 1), (n - 1, n - 2)]:
        c.px(x, y, pink + (170,))
    return c


ICONS = {
    "icon_survivors": icon_survivors,
    "icon_timer": icon_timer,
    "icon_stamina": icon_stamina,
    "icon_marble": icon_marble,
    "icon_lick": icon_lick,
    "icon_skull": icon_skull,
    "icon_warning": icon_warning,
    "icon_eye_red": icon_eye_red,
    "icon_eye_green": icon_eye_green,
    "icon_heart": icon_heart,
    "icon_rope": icon_rope,
    "icon_glass": icon_glass,
    "icon_circle": icon_circle,
    "icon_triangle": icon_triangle,
    "icon_square": icon_square,
}


def generate(out: Out) -> None:
    for name, fn in ICONS.items():
        out.png(f"textures/gui/hud/{name}.png", fn())
    out.png("textures/gui/hud/number_plate.png", number_plate())
    out.png("textures/gui/hud/vignette_red.png", vignette_red())
    out.png("textures/gui/hud/panel.png", panel())


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
