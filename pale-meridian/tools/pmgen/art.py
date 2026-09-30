"""Procedural pixel art for the resource pack: NPC skins, locator-bar sprite, mod icon, journal backdrop.

All art is original and generated from code (no third-party assets). Skins use the standard 64x64
player skin layout; 'faded' variants are the same person drained of colour by the Pall.
"""
from __future__ import annotations

import colorsys
import io
import random
from dataclasses import dataclass, field

from PIL import Image

from .jsonio import write_bytes, write_json
from .paths import PM_ASSETS


def _hex(c: str) -> tuple:
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)


def _shade(c: tuple, f: float) -> tuple:
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def _png(img: Image.Image) -> bytes:
    buf = io.BytesIO()
    img.save(buf, "PNG", optimize=True)
    return buf.getvalue()


@dataclass
class Look:
    skin: str
    hair: str
    eyes: str
    top: str                       # shirt / tunic
    coat: str | None = None        # over-layer jacket (overlay)
    trousers: str = "#3b3a36"
    boots: str = "#2b2118"
    hair_style: str = "short"      # short | long | bun | bald | cap | hood
    beard: str | None = None
    hat: str | None = None         # colour of a hat brim / cap (overlay)
    apron: str | None = None
    scarf: str | None = None
    accent: str | None = None      # buttons / belt buckle / trims
    model: str = "wide"
    freckles: bool = False
    glasses: bool = False
    age_lines: bool = False
    seed: int = 1


# (x, y, w, h) face rectangles for each box, by name.
def _box_faces(ox, oy, w, h, d):
    """UV faces for a box whose texture region starts at (ox, oy) with size w (x), h (y), d (z)."""
    return {
        "top": (ox + d, oy, w, d),
        "bottom": (ox + d + w, oy, w, d),
        "right": (ox, oy + d, d, h),
        "front": (ox + d, oy + d, w, h),
        "left": (ox + d + w, oy + d, d, h),
        "back": (ox + d + w + d, oy + d, w, h),
    }


def _layout(model: str) -> dict:
    aw = 3 if model == "slim" else 4
    return {
        "head": _box_faces(0, 0, 8, 8, 8), "hat": _box_faces(32, 0, 8, 8, 8),
        "body": _box_faces(16, 16, 8, 12, 4), "jacket": _box_faces(16, 32, 8, 12, 4),
        "rarm": _box_faces(40, 16, aw, 12, 4), "rsleeve": _box_faces(40, 32, aw, 12, 4),
        "larm": _box_faces(32, 48, aw, 12, 4), "lsleeve": _box_faces(48, 48, aw, 12, 4),
        "rleg": _box_faces(0, 16, 4, 12, 4), "rpants": _box_faces(0, 32, 4, 12, 4),
        "lleg": _box_faces(16, 48, 4, 12, 4), "lpants": _box_faces(0, 48, 4, 12, 4),
    }


class _Canvas:
    def __init__(self, rng: random.Random):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rng = rng

    def fill(self, rect, color, noise=0.06, shade=1.0):
        x0, y0, w, h = rect
        for x in range(x0, x0 + w):
            for y in range(y0, y0 + h):
                f = shade * (1.0 + self.rng.uniform(-noise, noise))
                self.px[x, y] = _shade(color, f)

    def set(self, x, y, color):
        if 0 <= x < 64 and 0 <= y < 64:
            self.px[x, y] = color


def paint_skin(look: Look) -> Image.Image:
    rng = random.Random(look.seed)
    c = _Canvas(rng)
    L = _layout(look.model)
    skin, hair, eyes = _hex(look.skin), _hex(look.hair), _hex(look.eyes)
    top, trousers, boots = _hex(look.top), _hex(look.trousers), _hex(look.boots)

    # --- head ------------------------------------------------------------------------------------
    for face, shade in (("top", 1.05), ("bottom", 0.8), ("right", 0.88), ("front", 1.0), ("left", 0.88), ("back", 0.92)):
        c.fill(L["head"][face], skin, noise=0.03, shade=shade)
    fx, fy, _, _ = L["head"]["front"]
    # hair on top/back/sides
    c.fill(L["head"]["top"], hair, noise=0.1, shade=1.0)
    c.fill(L["head"]["back"], hair, noise=0.1, shade=0.9)
    for side in ("right", "left"):
        x, y, w, h = L["head"][side]
        c.fill((x, y, w, 3 if look.hair_style != "long" else 7), hair, noise=0.1, shade=0.85)
        if look.hair_style in ("short", "bun", "cap", "hood"):
            c.fill((x + (5 if side == "right" else 0), y + 3, 3, 1), hair, noise=0.1, shade=0.8)
    # fringe
    if look.hair_style != "bald":
        for x in range(fx, fx + 8):
            c.set(x, fy, _shade(hair, 0.95))
            if rng.random() < 0.55 or x in (fx, fx + 7):
                c.set(x, fy + 1, _shade(hair, 0.9))
        if look.hair_style == "long":
            for y in range(fy + 1, fy + 8):
                c.set(fx, y, _shade(hair, 0.85))
                c.set(fx + 7, y, _shade(hair, 0.85))
    # eyes, brows, mouth
    white = (236, 236, 230, 255)
    for ex in (fx + 1, fx + 5):
        c.set(ex, fy + 4, white)
        c.set(ex + 1, fy + 4, eyes)
        c.set(ex, fy + 3, _shade(hair, 0.7))
        c.set(ex + 1, fy + 3, _shade(hair, 0.7))
    c.set(fx + 3, fy + 5, _shade(skin, 0.85))
    c.set(fx + 4, fy + 5, _shade(skin, 0.85))
    for x in range(fx + 3, fx + 5):
        c.set(x, fy + 6, _shade(skin, 0.62))
    if look.freckles:
        for (x, y) in ((1, 5), (6, 5), (2, 5), (5, 5)):
            c.set(fx + x, fy + y, _shade(skin, 0.82))
    if look.age_lines:
        c.set(fx + 1, fy + 5, _shade(skin, 0.85))
        c.set(fx + 6, fy + 5, _shade(skin, 0.85))
    if look.beard:
        b = _hex(look.beard)
        for x in range(fx + 1, fx + 7):
            c.set(x, fy + 7, _shade(b, 0.9))
        for x in (fx + 1, fx + 2, fx + 5, fx + 6):
            c.set(x, fy + 6, _shade(b, 0.95))
        c.set(fx, fy + 6, b); c.set(fx + 7, fy + 6, b)
    if look.glasses:
        g = (70, 60, 40, 255)
        for x in range(fx + 1, fx + 7):
            c.set(x, fy + 3 + (0 if x in (fx + 3, fx + 4) else 0), g) if x in (fx + 3, fx + 4) else None
        for ex in (fx + 1, fx + 5):
            c.set(ex - 0, fy + 3, g); c.set(ex + 1, fy + 3, g)
    # hat overlay
    if look.hat or look.hair_style in ("cap", "hood"):
        hc = _hex(look.hat or look.hair)
        for face, shade in (("top", 1.0), ("right", 0.85), ("front", 0.95), ("left", 0.85), ("back", 0.9)):
            x, y, w, h = L["hat"][face]
            rows = h if face == "top" else (3 if look.hair_style == "cap" else 2)
            if look.hair_style == "hood" and face != "front":
                rows = h
            c.fill((x, y, w, rows if face != "top" else h), hc, noise=0.08, shade=shade)
        if look.hair_style == "hood":
            x, y, w, h = L["hat"]["front"]
            for yy in range(y, y + h):
                c.set(x, yy, _shade(hc, 0.85)); c.set(x + w - 1, yy, _shade(hc, 0.85))
    elif look.hair_style == "bun":
        x, y, w, h = L["hat"]["back"]
        c.fill((x + 3, y + 1, 2, 2), hair, noise=0.1, shade=1.05)

    # --- body --------------------------------------------------------------------------------------
    for face, shade in (("top", 1.05), ("bottom", 0.8), ("right", 0.85), ("front", 1.0), ("left", 0.85), ("back", 0.92)):
        c.fill(L["body"][face], top, noise=0.07, shade=shade)
    bx, by, bw, bh = L["body"]["front"]
    # collar
    for x in range(bx + 3, bx + 5):
        c.set(x, by, _shade(skin, 0.95))
    if look.accent:
        a = _hex(look.accent)
        for y in range(by + 2, by + 10, 2):
            c.set(bx + 4, y, a)
        for x in range(bx, bx + bw):
            c.set(x, by + 9, _shade(_hex("#3a2a1c"), 1.0))
        c.set(bx + 3, by + 9, a); c.set(bx + 4, by + 9, a)
    if look.apron:
        ap = _hex(look.apron)
        c.fill((bx + 1, by + 3, 6, 9), ap, noise=0.06, shade=1.0)
    if look.scarf:
        sc = _hex(look.scarf)
        for x in range(bx, bx + bw):
            c.set(x, by, sc); c.set(x, by + 1, _shade(sc, 0.9))
        c.set(bx + 2, by + 2, sc); c.set(bx + 2, by + 3, _shade(sc, 0.9))
    # coat overlay (jacket + sleeves + lower legs of a long coat)
    if look.coat:
        co = _hex(look.coat)
        for face, shade in (("top", 1.0), ("right", 0.82), ("front", 0.97), ("left", 0.82), ("back", 0.9), ("bottom", 0.8)):
            x, y, w, h = L["jacket"][face]
            c.fill((x, y, w, h), co, noise=0.08, shade=shade)
        x, y, w, h = L["jacket"]["front"]
        for yy in range(y + 1, y + h):          # open front showing the shirt
            c.set(x + 3, yy, (0, 0, 0, 0)); c.set(x + 4, yy, (0, 0, 0, 0))
        if look.accent:
            for yy in range(y + 2, y + h, 3):
                c.set(x + 2, yy, _hex(look.accent))
        for part in ("rsleeve", "lsleeve"):
            for face, shade in (("top", 1.0), ("right", 0.82), ("front", 0.95), ("left", 0.82), ("back", 0.88)):
                x, y, w, h = L[part][face]
                c.fill((x, y, w, h - 2), co, noise=0.08, shade=shade)
        for part in ("rpants", "lpants"):
            for face, shade in (("right", 0.82), ("front", 0.95), ("left", 0.82), ("back", 0.88)):
                x, y, w, h = L[part][face]
                c.fill((x, y, w, 4), co, noise=0.08, shade=shade)

    # --- arms ----------------------------------------------------------------------------------------
    for arm in ("rarm", "larm"):
        for face, shade in (("top", 1.05), ("bottom", 0.8), ("right", 0.85), ("front", 1.0), ("left", 0.85), ("back", 0.9)):
            x, y, w, h = L[arm][face]
            if face in ("top", "bottom"):
                c.fill((x, y, w, h), top if face == "top" else skin, noise=0.06, shade=shade)
                continue
            c.fill((x, y, w, h - 3), top, noise=0.07, shade=shade)
            c.fill((x, y + h - 3, w, 3), skin, noise=0.03, shade=shade)

    # --- legs ----------------------------------------------------------------------------------------
    for leg in ("rleg", "lleg"):
        for face, shade in (("top", 1.0), ("bottom", 0.7), ("right", 0.85), ("front", 1.0), ("left", 0.85), ("back", 0.9)):
            x, y, w, h = L[leg][face]
            if face in ("top", "bottom"):
                c.fill((x, y, w, h), trousers if face == "top" else boots, noise=0.06, shade=shade)
                continue
            c.fill((x, y, w, h - 4), trousers, noise=0.07, shade=shade)
            c.fill((x, y + h - 4, w, 4), boots, noise=0.07, shade=shade)
    return c.img


def fade(img: Image.Image, strength: float = 0.82) -> Image.Image:
    """The Pall's touch: drain saturation, lift and cool the values, soften contrast."""
    out = img.copy()
    px = out.load()
    for x in range(out.width):
        for y in range(out.height):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            s *= (1.0 - strength)
            l = 0.42 + (l - 0.42) * 0.55 + 0.12
            rr, gg, bb = colorsys.hls_to_rgb(0.58 if s < 0.05 else h, min(1, max(0, l)), s)
            rr, gg, bb = rr * 0.96, gg * 0.99, min(1.0, bb * 1.06)
            px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
    return out


# ------------------------------------------------------------------------------------------------
CAST: dict[str, Look] = {
    "tamsin": Look(skin="#c99a78", hair="#5a3a24", eyes="#3f5a3a", top="#8a7f6a", coat="#5b4632", trousers="#4a4538",
                   boots="#3a2a1c", hair_style="bun", scarf="#8c3b2e", accent="#c9a45a", model="slim", freckles=True, seed=11),
    "odile": Look(skin="#d8b49a", hair="#d9d4cc", eyes="#5a6f8a", top="#5d6f7c", coat="#39434d", trousers="#3b3a36",
                  boots="#2b2118", hair_style="long", scarf="#b58a3a", accent="#d8c070", model="slim", age_lines=True, seed=12),
    "brannoc": Look(skin="#b98563", hair="#6b4a2a", eyes="#4a3a2a", top="#7a6a3a", apron="#c9a45a", trousers="#4a3d2c",
                    boots="#2e2218", hair_style="short", beard="#6b4a2a", accent="#9a7a3a", seed=13),
    "hesper": Look(skin="#e0c2a8", hair="#9a9aa6", eyes="#40506a", top="#2e3550", coat="#1f2440", trousers="#2a2c3a",
                   boots="#1c1c24", hair_style="long", accent="#c9b26a", model="slim", glasses=True, age_lines=True, seed=14),
    "mirelle": Look(skin="#a8704e", hair="#2a1d16", eyes="#3a2a1a", top="#b85a3a", apron="#e8e0d0", trousers="#4a3a30",
                    boots="#3a2a1c", hair_style="bun", model="slim", seed=15),
    "jory": Look(skin="#c49070", hair="#bdb7ac", eyes="#4a4a3a", top="#4f5a3f", coat="#3f4a34", trousers="#3a3a30",
                 boots="#2b2118", hair_style="cap", hat="#5a4a3a", beard="#bdb7ac", seed=16),
    "miner": Look(skin="#b88a6a", hair="#3a2a20", eyes="#3a3a3a", top="#6a5a4a", trousers="#3f3a34", boots="#2a2018",
                  hair_style="cap", hat="#8a7a5a", scarf="#6a4a3a", seed=17),
    "tobin": Look(skin="#d2ad90", hair="#7a5a3a", eyes="#40506a", top="#6a6a5a", coat="#4a4438", trousers="#3a3630",
                  boots="#2a2018", hair_style="short", scarf="#40506a", seed=18),
}

# The eleven echo figures in the Deepcut (faded only). Echo 1 is the crew lead and shares his look.
_ECHO_LOOKS = [
    ("#c49070", "#2a1d16", "#5a4a3a", "#6a5a4a", "cap", None, "slim"),
    ("#d8b49a", "#b58a3a", "#8a7a5a", "#4f5a3f", "long", "#7a3a2a", "slim"),
    ("#a8704e", "#1f1a14", "#6a4a3a", "#3f4a5a", "short", None, "wide"),
    ("#e0c2a8", "#c9a45a", "#5a6a7a", "#6a5a4a", "bun", "#40506a", "slim"),
    ("#b98563", "#3a2a20", "#7a6a5a", "#5a4a3a", "short", None, "wide"),
    ("#c99a78", "#6b4a2a", "#4a5a4a", "#7a6a3a", "cap", "#8c3b2e", "wide"),
    ("#d2ad90", "#9a9aa6", "#3a3a3a", "#5d6f7c", "bald", None, "wide"),
    ("#a07050", "#2a1d16", "#8a6a4a", "#6a4a3a", "long", "#b58a3a", "slim"),
    ("#e8c8b0", "#d9a060", "#5a5a4a", "#4a4438", "short", None, "wide"),
    ("#8a5a3e", "#1a1410", "#6a6a5a", "#3f3a34", "cap", "#40506a", "wide"),
]
for _i, (_sk, _hair, _top, _coat, _style, _scarf, _model) in enumerate(_ECHO_LOOKS, start=2):
    CAST[f"echo_{_i}"] = Look(skin=_sk, hair=_hair, eyes="#3a3a3a", top=_top, coat=_coat, trousers="#3a3630", boots="#2a2018",
                              hair_style=_style, hat="#6a5a4a" if _style == "cap" else None, scarf=_scarf, model=_model,
                              beard=_hair if _model == "wide" and _i % 3 == 0 else None, seed=100 + _i)
CAST["echo_1"] = CAST["tobin"]

# which skin states are shipped per NPC (texture id suffixes)
SKIN_STATES = {
    "tamsin": ["faded", "restored"],
    "odile": ["faded", "restored"],
    "brannoc": ["faded", "restored"],
    "hesper": ["faded", "restored"],
    "mirelle": ["faded", "restored"],
    "jory": ["faded", "restored"],
    "miner": ["faded", "restored"],
    "tobin": ["faded", "restored"],
    **{f"echo_{i}": ["faded"] for i in range(1, 12)},
}


def generate_skins() -> None:
    for npc, look in CAST.items():
        base = paint_skin(look)
        for state in SKIN_STATES[npc]:
            img = base if state == "restored" else fade(base)
            write_bytes(PM_ASSETS / "textures" / "entity" / "npc" / f"{npc}_{state}.png", _png(img))
        # also expose a model hint for the generator
    write_bytes(PM_ASSETS / "textures" / "entity" / "npc" / "none_none.png", _png(Image.new("RGBA", (64, 64), (0, 0, 0, 0))))


def generate_sprites() -> None:
    # Locator-bar lantern (white; tinted by the waypoint colour). 4 sizes by distance.
    shapes = [
        ["..#####..", ".#.....#.", "..#####..", "..#.#.#..", "..##.##..", "..#.#.#..", "..#####..", "...###...", "........."],
        [".........", "..#####..", "..#...#..", "..#.#.#..", "..##.##..", "..#####..", "...###...", ".........", "........."],
        [".........", ".........", "...###...", "...#.#...", "...###...", "....#....", ".........", ".........", "........."],
        [".........", ".........", ".........", "....#....", "...###...", "....#....", ".........", ".........", "........."],
    ]
    for i, rows in enumerate(shapes):
        img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    img.putpixel((x, y), (255, 255, 255, 255))
        write_bytes(PM_ASSETS / "textures" / "gui" / "sprites" / "hud" / "locator_bar_dot" / f"lamp_{i}.png", _png(img))
    write_json(PM_ASSETS / "waypoint_style" / "lamp.json", {"sprites": [f"palemeridian:lamp_{i}" for i in range(4)]})


def generate_icon() -> None:
    rng = random.Random(7)
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = y / size
            base = (int(150 + 40 * t), int(156 + 36 * t), int(162 + 30 * t))
            n = rng.uniform(-6, 6)
            px[x, y] = (int(base[0] + n), int(base[1] + n), int(base[2] + n), 255)
    # lantern silhouette with a warm core
    cx = 64
    for y in range(30, 100):
        for x in range(40, 88):
            dx = abs(x - cx)
            inside = (38 <= y <= 92 and dx <= 20) or (y < 38 and dx <= 12 and y > 30)
            if inside:
                edge = dx >= 18 or y in (38, 39, 91, 92) or (y < 38 and dx >= 10)
                if edge:
                    px[x, y] = (40, 36, 34, 255)
                else:
                    d = ((x - cx) ** 2 + (y - 66) ** 2) ** 0.5
                    glow = max(0.0, 1.0 - d / 26)
                    px[x, y] = (int(120 + 135 * glow), int(90 + 120 * glow), int(40 + 60 * glow), 255)
    for y in range(18, 30):
        for x in range(58, 70):
            if (x - 64) ** 2 + (y - 24) ** 2 in range(20, 40):
                px[x, y] = (40, 36, 34, 255)
    for y in range(100, 108):
        for x in range(46, 82):
            px[x, y] = (40, 36, 34, 255)
    write_bytes(PM_ASSETS / "icon.png", _png(img))


def generate() -> None:
    generate_skins()
    generate_sprites()
    generate_icon()
