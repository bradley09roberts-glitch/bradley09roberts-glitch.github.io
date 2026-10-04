"""Cube-style block textures: glass, cash, monitor, light panels, playground, tiles, pastels, symbols.

Every texture here is 16x16 (monitor_screen is an animated 16x128 strip) and fully opaque except
``bridge_glass`` (translucent on purpose) and ``invisible_wall`` (particle only).
"""
from __future__ import annotations

import numpy as np

from common import (Canvas, Out, PANEL_LIGHTS, PASTELS, SYMBOLS, TILES, art, fbm, lch_to_rgb,
                    lighten, mix, ramp, rng, shade, value_noise)

# --------------------------------------------------------------------------- pastel palette
# Hand tuned in LCH so that the seven candy colours carry the same visual weight.
# (L, C, h) -> sRGB; yellow and cream sit a little lighter because yellow reads darker at equal L.
PASTEL_LCH = {
    "pink":   (81.0, 30.0, 5.0),
    "mint":   (85.0, 29.0, 160.0),
    "yellow": (90.0, 40.0, 95.0),
    "sky":    (82.0, 25.0, 247.0),
    "lilac":  (79.0, 28.0, 305.0),
    "peach":  (84.0, 33.0, 52.0),
    "cream":  (93.0, 12.0, 88.0),
}


def pastel_rgb(name: str):
    return lch_to_rgb(*PASTEL_LCH[name])


# --------------------------------------------------------------------------- bridge glass
def bridge_glass() -> Canvas:
    """Pale cyan tempered-glass pane in a thin darker frame.  Same texture for safe/fragile panels."""
    c = Canvas(16)
    frame = (78, 148, 168, 246)
    inner_edge = (160, 226, 238, 176)
    c.rect(0, 0, 16, 16, frame)
    c.rect(1, 1, 15, 15, inner_edge)
    # glass body: pale cyan, a touch denser towards the lower right (reads as thicker glass)
    for y in range(2, 14):
        for x in range(2, 14):
            t = (x + y - 4) / 20.0
            c.px(x, y, (170, 238, 250, int(round(112 + 24 * t))))
    # corner seats
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        c.px(x, y, (110, 176, 194, 214))
    # diagonal glints (long one top-left, short one bottom-right) + a fainter echo
    glint = (246, 254, 255, 236)
    for (x, y) in [(4, 8), (5, 7), (6, 6), (7, 5), (8, 4)]:
        c.px(x, y, glint)
    for (x, y) in [(4, 6), (5, 5), (6, 4)]:
        c.px(x, y, (236, 252, 255, 176))
    for (x, y) in [(10, 12), (11, 11), (12, 10)]:
        c.px(x, y, (240, 253, 255, 206))
    return c


# --------------------------------------------------------------------------- cash block
def _note_edge_rows(r) -> list:
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
    """Top of the bundle: a stylised yellow-green banknote (generic ornament, no real design) + paper band.

    Left of the band: a medallion portrait; right of the band: a vertical "50" value mark.
    """
    legend = {
        "B": (98, 126, 54),       # dark green ink
        "b": (228, 234, 164),     # light frame
        "g": (190, 206, 106),     # note body
        "d": (176, 196, 96),      # fine pattern
        "m": (150, 176, 78),      # medallion fill
    }
    grid = [["g"] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                grid[y][x] = "d"
    for i in range(16):
        for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]:
            grid[y][x] = "B"
        for (x, y) in [(i, 1), (i, 14), (1, i), (14, i)]:
            if grid[y][x] != "B":
                grid[y][x] = "b"

    def paste(x0, y0, rows):
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row):
                if ch != ".":
                    grid[y0 + dy][x0 + dx] = ch

    paste(2, 5, [".m.", "mBm", "mBm", "mBB", ".m."])                    # solid medallion (portrait silhouette)
    paste(11, 3, ["BBB", "B..", "BBB", "..B", "BBB"])                   # "5"
    paste(11, 9, ["BBB", "B.B", "B.B", "B.B", "BBB"])                   # "0"
    for (x, y) in [(2, 2), (13, 2), (2, 13)]:
        grid[y][x] = "B"
    # the "." cells of the digits keep the note body colour
    c = art(["".join(r) for r in grid], legend)
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
        # scanlines: every second row a touch darker (static, so the screen does not strobe)
        for y in range(0, 16, 2):
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
        "pink": dict(frame=(196, 126, 158), line=(246, 194, 216), cell=(255, 224, 238),
                     mid=(255, 240, 248), edge=(232, 164, 194)),
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
    n = fbm(16, 16, r, [(4, 0.5), (2, 0.3), (8, 0.2)])
    n = (n - n.min()) / (n.max() - n.min() + 1e-9)
    pal = [(180, 149, 106), (192, 161, 117), (203, 173, 128), (213, 186, 141), (223, 198, 154)]
    c = Canvas(16, fill=(0, 0, 0, 255))
    c.fill_rgb(ramp(n, pal, dither=0.6))
    # fine speckle: dry grit
    for _ in range(40):
        x, y = int(r.integers(0, 16)), int(r.integers(0, 16))
        base = c.get(x, y)[:3]
        c.px(x, y, shade(base, 0.93) if r.random() < 0.55 else lighten(base, 0.10))

    def wrap_px(x, y, col):
        c.px(x % 16, y % 16, col)

    # a few small pebbles: (x, y, w, h); kept sparse so the four random rotations do not read as a pattern
    for (px_, py_, w, h) in [(5, 4, 2, 1), (12, 10, 1, 1), (2, 12, 2, 2)]:
        for yy in range(h):
            for xx in range(w):
                wrap_px(px_ + xx + 1, py_ + yy + 1, (156, 126, 92))           # cast shadow
        for yy in range(h):
            for xx in range(w):
                t = (xx + yy) / max(1, (w + h - 2))
                col = (166, 156, 142) if t < 0.5 else (136, 124, 110)
                if w == 1 and h == 1:
                    col = (152, 140, 126)
                wrap_px(px_ + xx, py_ + yy, col)
        wrap_px(px_, py_, (196, 188, 174))
    return c


# --------------------------------------------------------------------------- floor tiles
TILE_STYLE = {
    "pink":  dict(base=(236, 150, 178), hi=(250, 196, 214), hi2=(255, 226, 236), lo=(214, 122, 154),
                  grout=(172, 96, 126)),
    "white": dict(base=(228, 233, 238), hi=(246, 249, 252), hi2=(255, 255, 255), lo=(204, 211, 219),
                  grout=(160, 170, 182)),
    "black": dict(base=(28, 30, 36), hi=(46, 50, 60), hi2=(88, 94, 108), lo=(18, 19, 23),
                  grout=(54, 57, 66)),
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
        m = (d2 <= 6.0 ** 2) & (d2 > 4.0 ** 2)
    elif kind == "square":
        outer = (np.abs(xs - 8) <= 5.0) & (np.abs(ys - 8) <= 5.0)
        inner = (np.abs(xs - 8) <= 3.0) & (np.abs(ys - 8) <= 3.0)
        m = outer & ~inner
    else:  # triangle pointing up, outline roughly 2px thick
        def tri(top, bot, half):
            return (ys >= top) & (ys <= bot) & (np.abs(xs - 8) <= half * (ys - top) / (bot - top))
        m = tri(2.0, 13.4, 6.4) & ~tri(4.9, 11.4, 3.5)
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
