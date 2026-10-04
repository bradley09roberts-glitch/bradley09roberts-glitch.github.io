"""Cube-style block textures: glass, cash, monitor, light panels, playground, tiles, pastels, symbols.

Every texture here is 16x16 (monitor_screen is an animated 16x128 strip) and fully opaque except
``bridge_glass`` (translucent on purpose) and ``invisible_wall`` (particle only).
"""
from __future__ import annotations

import numpy as np

from common import (Canvas, Out, PANEL_LIGHTS, PASTELS, SYMBOLS, TILES, art, bayer, darken, fbm, lch_to_rgb,
                    lighten, mix, ramp, rgb, rng, shade, value_noise)

# --------------------------------------------------------------------------- pastel palette
# Hand tuned in LCH so that the seven candy colours carry the same visual weight.
# (L, C, h) -> sRGB; yellow and cream sit a little lighter because yellow reads darker at equal L.
PASTEL_LCH = {
    "pink":   (80.0, 30.0, 5.0),
    "mint":   (85.0, 29.0, 160.0),
    "yellow": (91.0, 40.0, 95.0),
    "sky":    (81.0, 24.0, 247.0),
    "lilac":  (77.0, 28.0, 305.0),
    "peach":  (83.0, 33.0, 52.0),
    "cream":  (93.0, 12.0, 88.0),
}


def pastel_rgb(name: str):
    return lch_to_rgb(*PASTEL_LCH[name])


# --------------------------------------------------------------------------- bridge glass
def bridge_glass() -> Canvas:
    """Pale cyan tempered-glass pane in a thin darker frame.  Same texture for safe/fragile panels."""
    c = Canvas(16)
    frame = (84, 150, 168, 244)
    inner_edge = (172, 222, 232, 168)
    body = (206, 240, 246, 104)
    c.rect(0, 0, 16, 16, frame)
    c.rect(1, 1, 15, 15, inner_edge)
    c.rect(2, 2, 14, 14, body)
    # very soft vertical sheen: slightly denser glass towards the lower right
    for y in range(2, 14):
        for x in range(2, 14):
            t = (x + y - 4) / 20.0
            a = int(round(98 + 20 * t))
            c.px(x, y, (206, 240, 246, a))
    # corner bolts / rubber seats
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        c.px(x, y, (118, 178, 194, 205))
    # diagonal glints (top-left long, bottom-right short) like vanilla glass but softer
    glint = (244, 253, 255, 232)
    for (x, y) in [(4, 8), (5, 7), (6, 6), (7, 5), (8, 4)]:
        c.px(x, y, glint)
    for (x, y) in [(4, 6), (5, 5), (6, 4)]:
        c.px(x, y, (232, 250, 255, 170))
    for (x, y) in [(10, 12), (11, 11), (12, 10)]:
        c.px(x, y, (238, 252, 255, 200))
    return c


# --------------------------------------------------------------------------- cash block
def _note_edge_rows(r: np.random.Generator) -> list:
    pal = [(206, 218, 126), (190, 206, 108), (176, 194, 94), (160, 182, 84), (222, 228, 150)]
    rows = []
    last = -1
    for _ in range(16):
        while True:
            k = int(r.choice(len(pal), p=[0.28, 0.27, 0.2, 0.1, 0.15]))
            if k != last:
                break
        rows.append(pal[k])
        last = k
    return rows


def _band(c: Canvas, x0: int, x1: int, y0: int, y1: int, vertical: bool = True) -> None:
    """Paper band wrapped around the bundle (x0..x1 inclusive columns when vertical)."""
    paper = (236, 228, 200)
    paper_dark = (206, 196, 164)
    paper_shadow = (118, 134, 66)
    if vertical:
        c.rect(x0 - 1, y0, x0, y1, paper_shadow)
        c.rect(x1 + 1, y0, x1 + 2, y1, paper_shadow)
        c.rect(x0, y0, x1 + 1, y1, paper)
        c.rect(x0, y0, x0 + 1, y1, paper_dark)
        c.rect(x1, y0, x1 + 1, y1, paper_dark)
        # printed bank marks in muted red
        mark = (176, 72, 62)
        for yy in range(y0 + 2, y1 - 2, 4):
            c.px(x0 + 1, yy, mark)
            c.px(x1 - 1, yy, mark)
        c.px(x0 + 1, (y0 + y1) // 2, (150, 52, 46))


def cash_block_side() -> Canvas:
    r = rng("cash_side")
    c = Canvas(16, fill=(0, 0, 0, 255))
    rows = _note_edge_rows(r)
    for y in range(16):
        c.rect(0, y, 16, y + 1, rows[y])
        # fibre specks along the edge of the stack
        for x in range(16):
            if r.random() < 0.10:
                c.px(x, y, shade(rows[y], 0.93 if r.random() < 0.6 else 1.06))
    # slightly darker top/bottom rows so the stack reads as bundled
    for x in range(16):
        c.px(x, 0, shade(c.get(x, 0)[:3], 0.9))
        c.px(x, 15, shade(c.get(x, 15)[:3], 0.84))
    _band(c, 6, 9, 0, 16)
    return c


def cash_block_top() -> Canvas:
    """Top of the bundle: a stylised yellow-green banknote (generic ornament, no real design) + band."""
    r = rng("cash_top")
    base = (190, 206, 106)
    c = Canvas(16, fill=base + (255,))
    # guilloche-ish background hatch
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                c.px(x, y, (180, 198, 98))
            elif r.random() < 0.08:
                c.px(x, y, (200, 214, 118))
    # border
    c.outline(0, 0, 16, 16, (104, 132, 56))
    c.outline(1, 1, 15, 15, (222, 228, 150))
    # corner ornaments
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        c.px(x, y, (104, 132, 56))
    # left: oval portrait medallion
    c.mask(c.ellipse_mask(3.5, 8.0, 2.0, 3.0), (150, 178, 78))
    c.mask(c.ring_mask(3.5, 8.0, 2.1, 3.1) & c.ellipse_mask(3.5, 8.0, 2.4, 3.6), (104, 132, 56))
    c.px(3, 7, (112, 140, 62))
    c.px(3, 8, (112, 140, 62))
    c.px(4, 8, (112, 140, 62))
    # right: value mark and text lines
    c.rect(11, 5, 14, 6, (104, 132, 56))
    c.rect(11, 7, 14, 8, (104, 132, 56))
    c.rect(11, 9, 13, 10, (104, 132, 56))
    c.rect(11, 11, 14, 12, (150, 178, 78))
    _band(c, 6, 9, 0, 16)
    return c


# --------------------------------------------------------------------------- monitor
def monitor_case() -> Canvas:
    r = rng("monitor_case")
    c = Canvas(16, fill=(37, 40, 46, 255))
    n = value_noise(16, 16, 4, r)
    pal = [(31, 33, 39), (35, 38, 44), (40, 43, 50), (45, 48, 56)]
    c.fill_rgb(ramp(n, pal, dither=0.6))
    # soft bevel: lighter top/left, darker bottom/right
    for i in range(16):
        c.px(i, 0, (54, 58, 66))
        c.px(0, i, (50, 54, 62))
        c.px(i, 15, (23, 25, 29))
        c.px(15, i, (26, 28, 33))
    return c


def monitor_front() -> Canvas:
    """World-aligned front plate: only the 2px/1px bezel ring is visible (screen is recessed behind)."""
    r = rng("monitor_front")
    c = Canvas(16, fill=(40, 43, 50, 255))
    n = value_noise(16, 16, 4, r)
    c.fill_rgb(ramp(n, [(33, 35, 41), (38, 41, 47), (43, 46, 53)], dither=0.5))
    # top bezel rows 0..1 : highlight then body
    for x in range(16):
        c.px(x, 0, (62, 66, 75))
        c.px(x, 1, (46, 49, 56))
    # bottom chin rows 14..15
    for x in range(16):
        c.px(x, 14, (44, 47, 54))
        c.px(x, 15, (24, 26, 30))
    for y in range(2, 14):
        c.px(0, y, (58, 62, 70))
        c.px(15, y, (30, 32, 37))
    # inner lip around the screen opening (dark line right next to the glass)
    for x in range(1, 15):
        c.px(x, 2, (14, 15, 18))
    # power LED + brand dash on the chin
    c.px(13, 14, (120, 255, 170))
    c.px(13, 15, (60, 150, 100))
    c.rect(3, 14, 6, 15, (80, 86, 98))
    return c


def monitor_screen() -> Canvas:
    """8 frames (16x16 each) stacked vertically: scanlines, refresh bar, static, drifting pale shapes."""
    frames = 8
    out = Canvas(16, 16 * frames)
    r = rng("monitor_screen")
    pale = (176, 238, 226)
    for f in range(frames):
        c = Canvas(16, fill=(0, 0, 0, 255))
        # base teal gradient (slightly brighter at the middle: a lit glass look)
        for y in range(16):
            for x in range(16):
                g = 1.0 - 0.28 * ((x - 7.5) ** 2 / 64.0 + (y - 7.5) ** 2 / 64.0)
                col = (int(20 * g + 6), int(58 * g + 10), int(66 * g + 10))
                c.px(x, y, col)
        # scanlines: every second row a touch darker, the pattern rolls 2px per frame (loops in 8 frames)
        for y in range(16):
            if (y + 2 * f) % 4 < 2:
                for x in range(16):
                    cr = c.get(x, y)
                    c.px(x, y, (max(0, cr[0] - 5), max(0, cr[1] - 12), max(0, cr[2] - 12)))
        # refresh bar: 3 rows drifting down 2px per frame
        by = (2 * f) % 16
        for k in range(3):
            y = (by + k) % 16
            for x in range(16):
                cr = c.get(x, y)
                add = (14, 30, 30) if k == 1 else (7, 15, 15)
                c.px(x, y, tuple(min(255, cr[i] + add[i]) for i in range(3)) + (255,))
        # faint UI grid
        for x in (4, 8, 12):
            for y in range(1, 15, 2):
                cr = c.get(x, y)
                c.px(x, y, (cr[0] + 4, cr[1] + 10, cr[2] + 10, 255))
        # drifting pale shapes (wrap around)
        def blob(cx, cy, pts, col):
            for dx, dy in pts:
                c.px((cx + dx) % 16, (cy + dy) % 16, col)
        circle = [(1, 0), (0, 1), (1, 1), (2, 1), (1, 2)]
        tri = [(1, 0), (0, 1), (1, 1), (2, 1)]
        blob((2 + 2 * f) % 16, 4, circle, pale)
        blob((14 - 2 * f) % 16, 11, tri, (150, 214, 204))
        if f in (0, 1, 2, 3, 5, 6):
            blob(11, 2, [(0, 0), (1, 0), (0, 1), (1, 1)], (132, 196, 188))
        # trailing dim pixel behind the circle so the motion reads
        c.px((1 + 2 * f) % 16, 5, (70, 130, 128))
        # status dot blinks pink
        if f % 4 < 2:
            c.px(13, 13, (255, 130, 170))
        # glitch row on two frames
        if f in (2, 6):
            gy = 8 if f == 2 else 13
            for x in range(3, 13):
                cr = c.get(x, gy)
                c.px(x, gy, (cr[0] + 28, cr[1] + 52, cr[2] + 50, 255))
        # static speckle
        n = int(r.integers(7, 12))
        for _ in range(n):
            x, y = int(r.integers(0, 16)), int(r.integers(0, 16))
            cr = c.get(x, y)
            k = int(r.integers(30, 70))
            c.px(x, y, (min(255, cr[0] + k // 2), min(255, cr[1] + k), min(255, cr[2] + k), 255))
        out.blit(c, 0, 16 * f, over=False)
    return out


# --------------------------------------------------------------------------- light panels
def panel_light(kind: str) -> Canvas:
    pal = {
        "white": dict(frame=(168, 180, 192), line=(222, 229, 236), cell=(247, 250, 253),
                      mid=(255, 255, 255), edge=(206, 216, 226)),
        "warm": dict(frame=(184, 150, 98), line=(244, 224, 170), cell=(255, 243, 206),
                     mid=(255, 250, 228), edge=(226, 196, 138)),
        "pink": dict(frame=(184, 108, 144), line=(244, 182, 208), cell=(255, 216, 232),
                     mid=(255, 234, 244), edge=(226, 150, 184)),
    }[kind]
    c = Canvas(16, fill=pal["cell"] + (255,))
    # 3x3 diffuser cells of 4px separated by 1px lines, 1px frame + 1px inner bevel handled by lines
    for i in (5, 10):
        c.rect(i, 1, i + 1, 15, pal["line"])
        c.rect(1, i, 15, i + 1, pal["line"])
    # cell brightness: centre cell glows, outer cells slightly dimmer, bevel towards the frame
    for cy in range(3):
        for cx in range(3):
            x0, y0 = 1 + cx * 5, 1 + cy * 5
            col = pal["mid"] if (cx, cy) == (1, 1) else pal["cell"]
            c.rect(x0, y0, x0 + 4, y0 + 4, col)
            # inner glow: 2x2 core brighter
            if (cx, cy) != (1, 1):
                c.rect(x0 + 1, y0 + 1, x0 + 3, y0 + 3, mix(pal["cell"], pal["mid"], 0.55))
    c.outline(0, 0, 16, 16, pal["frame"])
    # soften frame corners by one tone so adjacent panels read as a metallic lattice
    for (x, y) in [(0, 0), (15, 0), (0, 15), (15, 15)]:
        c.px(x, y, shade(pal["frame"], 0.86))
    # outer bevel highlight inside the frame (top/left lighter, bottom/right a little darker)
    for i in range(1, 15):
        c.px(i, 1, pal["edge"])
        c.px(1, i, pal["edge"])
    return c


# --------------------------------------------------------------------------- playground
def playground_ground() -> Canvas:
    """Sun-baked packed school-yard sand with pebbles and hairline cracks. Tiles seamlessly."""
    r = rng("playground_ground")
    n = fbm(16, 16, r, [(8, 0.5), (4, 0.35), (2, 0.15)])
    n = (n - n.min()) / (n.max() - n.min() + 1e-9)
    pal = [(176, 146, 104), (189, 158, 114), (201, 171, 126), (212, 184, 139), (222, 197, 152)]
    c = Canvas(16, fill=(0, 0, 0, 255))
    c.fill_rgb(ramp(n, pal, dither=0.5))
    # fine speckle
    for _ in range(34):
        x, y = int(r.integers(0, 16)), int(r.integers(0, 16))
        base = c.get(x, y)[:3]
        c.px(x, y, shade(base, 0.92) if r.random() < 0.5 else lighten(base, 0.10))
    # hairline sun-baked cracks (drawn with wrap-around)
    def wrap_px(x, y, col):
        c.px(x % 16, y % 16, col)
    crack = (160, 128, 90)
    for (x, y, steps) in [(2, 3, [(1, 0), (1, 1), (1, 0), (0, 1)]), (10, 11, [(1, 0), (1, -1), (1, 0)]),
                          (6, 14, [(0, 1), (1, 1), (1, 0), (1, 0)])]:
        for dx, dy in steps:
            wrap_px(x, y, crack)
            x, y = x + dx, y + dy
    # pebbles: (x, y, w, h)
    pebble = [(5, 5, 2, 2), (12, 3, 2, 1), (1, 10, 2, 2), (9, 9, 1, 1), (14, 13, 2, 2), (4, 13, 1, 1), (11, 7, 1, 1)]
    for (px_, py_, w, h) in pebble:
        # cast shadow first (down-right)
        for yy in range(h):
            for xx in range(w):
                wrap_px(px_ + xx + 1, py_ + yy + 1, (150, 120, 86))
        for yy in range(h):
            for xx in range(w):
                t = (xx + yy) / max(1, (w + h - 2))
                col = (156, 146, 132) if t < 0.5 else (126, 114, 102)
                if w == 1 and h == 1:
                    col = (146, 134, 120)
                wrap_px(px_ + xx, py_ + yy, col)
        wrap_px(px_, py_, (190, 182, 168))
    return c


# --------------------------------------------------------------------------- floor tiles
TILE_STYLE = {
    "pink":  dict(base=(236, 150, 178), hi=(250, 196, 214), hi2=(255, 226, 236), lo=(214, 122, 154),
                  grout=(172, 96, 126)),
    "white": dict(base=(228, 233, 238), hi=(246, 249, 252), hi2=(255, 255, 255), lo=(204, 211, 219),
                  grout=(160, 170, 182)),
    "black": dict(base=(30, 32, 38), hi=(64, 68, 78), hi2=(96, 102, 114), lo=(19, 20, 24),
                  grout=(8, 9, 11)),
}


def tile(kind: str) -> Canvas:
    """2x2 glossy square tiles (8px pitch) with 1px grout on the right/bottom of each tile."""
    st = TILE_STYLE[kind]
    r = rng("tile_" + kind)
    c = Canvas(16, fill=st["grout"] + (255,))
    for ty in range(2):
        for tx in range(2):
            x0, y0 = tx * 8, ty * 8
            # tiny per-tile brightness drift keeps big floors from looking printed
            k = 1.0 + (r.random() - 0.5) * (0.035 if kind != "black" else 0.12)
            base = shade(st["base"], k)
            c.rect(x0, y0, x0 + 7, y0 + 7, base)
            # bevel: top + left lighter, bottom + right darker
            c.rect(x0, y0, x0 + 7, y0 + 1, mix(base, st["hi"], 0.75))
            c.rect(x0, y0, x0 + 1, y0 + 7, mix(base, st["hi"], 0.75))
            c.rect(x0 + 6, y0 + 1, x0 + 7, y0 + 7, st["lo"])
            c.rect(x0 + 1, y0 + 6, x0 + 7, y0 + 7, st["lo"])
            # gloss: diagonal reflection streaks from the upper-left corner
            variant = (tx + 2 * ty) % 4
            streak = [[(2, 1), (1, 2), (3, 1), (1, 3)],
                      [(2, 2), (3, 1), (1, 3), (2, 1), (1, 2)],
                      [(3, 1), (2, 2), (1, 3), (4, 1), (1, 4)],
                      [(1, 1), (2, 1), (1, 2)]][variant]
            for (dx, dy) in streak:
                c.px(x0 + dx, y0 + dy, st["hi"])
            c.px(x0 + 2, y0 + 2, st["hi2"] if variant != 3 else st["hi"])
            if variant in (0, 2):
                c.px(x0 + 1, y0 + 1, st["hi2"])
            # a faint secondary glint at the bottom right
            c.px(x0 + 5, y0 + 4, mix(base, st["hi"], 0.55))
            c.px(x0 + 4, y0 + 5, mix(base, st["hi"], 0.55))
    # grout intersections a touch darker
    for (x, y) in [(7, 7), (15, 7), (7, 15), (15, 15)]:
        c.px(x, y, shade(st["grout"], 0.82))
    return c


# --------------------------------------------------------------------------- pastel blocks
def pastel(name: str) -> Canvas:
    """Flat matte candy colour with a very subtle pixel texture and a slightly lighter edge line."""
    r = rng("pastel_" + name)
    base = np.array(pastel_rgb(name), dtype=float)
    c = Canvas(16, fill=tuple(int(v) for v in base) + (255,))
    n = value_noise(16, 16, 4, r) * 0.6 + r.random((16, 16)) * 0.4
    tones = [0.972, 0.986, 1.0, 1.012, 1.024]
    idx = np.clip(np.floor(n * len(tones)).astype(int), 0, len(tones) - 1)
    arr = np.zeros((16, 16, 3))
    for k, t in enumerate(tones):
        arr[idx == k] = base * t
    arr = np.clip(np.round(arr), 0, 255).astype(np.uint8)
    c.fill_rgb(arr)
    edge = tuple(int(v) for v in np.clip(base + (255 - base) * 0.30, 0, 255))
    inner = tuple(int(v) for v in np.clip(base * 1.0 + (255 - base) * 0.10, 0, 255))
    c.outline(0, 0, 16, 16, edge)
    # a faint second ring on the inside so the edge reads as a soft chamfer, not a line
    for i in range(1, 15):
        for (x, y) in [(i, 1), (i, 14), (1, i), (14, i)]:
            old = c.get(x, y)[:3]
            c.px(x, y, mix(old, inner, 0.55))
    return c


# --------------------------------------------------------------------------- symbol blocks
SYM_BG = (19, 19, 23)
SYM_FG = (238, 238, 238)


def _symbol_ground(seed: str) -> Canvas:
    r = rng(seed)
    c = Canvas(16, fill=SYM_BG + (255,))
    n = r.random((16, 16))
    pal = [(16, 16, 20), (19, 19, 23), (22, 22, 26)]
    c.fill_rgb(ramp(n, pal))
    c.outline(0, 0, 16, 16, (34, 34, 40))
    return c


def symbol_plain() -> Canvas:
    return _symbol_ground("symbol_plain")


def symbol(kind: str) -> Canvas:
    c = _symbol_ground("symbol_" + kind)
    xs, ys = c.grid()
    if kind == "circle":
        d2 = (xs - 8) ** 2 + (ys - 8) ** 2
        m = (d2 <= 6.6 ** 2) & (d2 > 4.4 ** 2)
    elif kind == "square":
        outer = (np.abs(xs - 8) <= 5.0) & (np.abs(ys - 8) <= 5.0)
        inner = (np.abs(xs - 8) <= 3.0) & (np.abs(ys - 8) <= 3.0)
        m = outer & ~inner
    else:  # triangle pointing up: apex (8, 2.2), base y = 13.2
        def tri(offset):
            top, bot, half = 2.0 + offset * 1.7, 13.4 - offset, 6.4
            # inside if y between top and bot and |x-8| <= half*(y-top)/(bot-top)
            t = (ys - top) / (bot - top)
            return (ys >= top) & (ys <= bot) & (np.abs(xs - 8) <= (half - offset * 2.0) * t + 0.0)
        outer = tri(0.0)
        # inner triangle: offset edges inwards by 2px
        top_i, bot_i = 2.0 + 5.3, 13.4 - 2.0
        t_i = (ys - 4.9) / (bot_i - 4.9)
        inner = (ys >= 4.9) & (ys <= bot_i) & (np.abs(xs - 8) <= 3.5 * t_i - 0.0)
        m = outer & ~inner
    c.mask(m, SYM_FG)
    return c


# --------------------------------------------------------------------------- invisible wall
def invisible_wall_particle() -> Canvas:
    """Particle-only texture: a faint frosted-cyan lattice (never rendered as a block)."""
    c = Canvas(16, fill=(0, 0, 0, 0))
    for i in range(16):
        for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]:
            c.px(x, y, (190, 235, 245, 150))
    for i in range(2, 14, 2):
        c.px(i, i, (190, 235, 245, 110))
        c.px(15 - i, i, (190, 235, 245, 110))
    return c


# --------------------------------------------------------------------------- driver
def generate(out: Out) -> None:
    out.png("textures/block/bridge_glass.png", bridge_glass())
    out.png("textures/block/cash_block_side.png", cash_block_side())
    out.png("textures/block/cash_block_top.png", cash_block_top())
    out.png("textures/block/monitor_case.png", monitor_case())
    out.png("textures/block/monitor_front.png", monitor_front())
    out.png("textures/block/monitor_screen.png", monitor_screen())
    out.json("textures/block/monitor_screen.png.mcmeta",
             {"animation": {"interpolate": False, "frametime": 3, "frames": list(range(8))}})
    for k in PANEL_LIGHTS:
        out.png(f"textures/block/panel_light_{k}.png", panel_light(k))
    out.png("textures/block/playground_ground.png", playground_ground())
    for k in TILES:
        out.png(f"textures/block/tile_{k}.png", tile(k))
    for k in PASTELS:
        out.png(f"textures/block/pastel_{k}.png", pastel(k))
    for k in SYMBOLS:
        out.png(f"textures/block/symbol_{k}.png", symbol(k))
    out.png("textures/block/symbol_plain.png", symbol_plain())
    out.png("textures/block/invisible_wall.png", invisible_wall_particle())


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
