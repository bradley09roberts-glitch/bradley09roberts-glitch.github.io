"""Multi-cube blocks: monitor, registration terminal, dalgona station (textures + model JSON).

All textures here are fully opaque (no cutout needed) and 16x16.  Models face NORTH (-z) by default;
the blockstates rotate them for the other three facings.
"""
from __future__ import annotations

import math

import numpy as np

from common import Canvas, Out, mix, ramp, rng, shade, value_noise
import paint

NS = "squidgame"


def T(name: str) -> str:
    return f"{NS}:block/{name}"


# =========================================================================== model helpers
def face(tex: str, uv=None, cull: str | None = None, rot: int | None = None) -> dict:
    d: dict = {}
    if uv is not None:
        d["uv"] = [round(float(v), 4) if float(v) != int(v) else int(v) for v in uv]
    d["texture"] = tex
    if cull:
        d["cullface"] = cull
    if rot:
        d["rotation"] = rot
    return d


def element(name: str, frm, to, faces: dict, rotation: dict | None = None, shade_flag: bool | None = None) -> dict:
    e: dict = {"name": name, "from": list(frm), "to": list(to)}
    if rotation:
        e["rotation"] = rotation
    if shade_flag is False:
        e["shade"] = False
    e["faces"] = faces
    return e


def _boundary_cull(frm, to) -> dict:
    """cullface per direction when the face lies exactly on the block boundary."""
    return {
        "down": "down" if frm[1] == 0 else None,
        "up": "up" if to[1] == 16 else None,
        "north": "north" if frm[2] == 0 else None,
        "south": "south" if to[2] == 16 else None,
        "west": "west" if frm[0] == 0 else None,
        "east": "east" if to[0] == 16 else None,
    }


def box(name: str, frm, to, tex: dict, uv: dict | None = None, rotation=None, shade_flag=None,
        cull: bool = True) -> dict:
    """``tex`` maps face -> texture ref; faces not mentioned are omitted.  Adds cullfaces on block borders."""
    uv = uv or {}
    cf = _boundary_cull(frm, to) if (cull and not rotation) else {}
    faces = {}
    for f_, t in tex.items():
        faces[f_] = face(t, uv.get(f_), cf.get(f_))
    return element(name, frm, to, faces, rotation, shade_flag)


# =========================================================================== MONITOR
def monitor_model() -> dict:
    case, front, screen = "#case", "#front", "#screen"
    els = [
        # back housing; its front face is the (animated) glass, framed by the bezel pieces below
        box("housing", [0, 0, 11], [16, 16, 16],
            {"north": screen, "south": case, "up": case, "down": case, "west": case, "east": case},
            uv={"north": [0, 0, 16, 16]}),
        box("bezel_top", [0, 14, 10], [16, 16, 11],
            {"north": front, "up": case, "down": case, "west": case, "east": case}),
        box("bezel_chin", [0, 0, 10], [16, 2, 11],
            {"north": front, "up": case, "down": case, "west": case, "east": case}),
        box("bezel_left", [0, 2, 10], [1, 14, 11],
            {"north": front, "west": case, "east": case}),
        box("bezel_right", [15, 2, 10], [16, 14, 11],
            {"north": front, "west": case, "east": case}),
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": T("monitor_case"), "case": T("monitor_case"),
                     "front": T("monitor_front"), "screen": T("monitor_screen")},
        "elements": els,
    }


# =========================================================================== REGISTRATION TERMINAL
PINK = (255, 134, 190)
PINK_L = (255, 196, 224)
PINK_D = (224, 84, 148)
PEARL = (243, 244, 249)
PEARL_L = (252, 252, 255)
PEARL_D = (220, 224, 234)
SEAM = (180, 185, 202)
GRAPHITE = (52, 55, 66)
GRAPHITE_L = (82, 86, 100)
GRAPHITE_D = (34, 36, 44)


def _pearl_base() -> Canvas:
    """Smooth pearl-white plastic: flat with a faint dither so big surfaces do not look printed."""
    c = Canvas(16, fill=PEARL + (255,))
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 5) % 11 == 0:
                c.px(x, y, mix(PEARL, PEARL_D, 0.45))
    return c


def terminal_body() -> Canvas:
    c = _pearl_base()
    c.outline(0, 0, 16, 16, mix(PEARL, SEAM, 0.35))
    for y in range(1, 15):
        c.px(1, y, PEARL_L)
        c.px(14, y, PEARL_D)
    c.rect(7, 1, 8, 15, mix(PEARL, SEAM, 0.30))         # one soft vertical panel seam
    for x in (4, 5, 6):                                  # three short vent slits near the foot
        c.rect(x + 5, 11, x + 6, 14, mix(PEARL_D, SEAM, 0.6))
    c.rect(2, 3, 5, 4, PINK)                             # tiny pink accent
    return c


def terminal_front() -> Canvas:
    """World-aligned: the body's front face spans columns 4..11, rows 7..13; the ring light hides rows 7..8."""
    c = _pearl_base()
    c.outline(0, 0, 16, 16, mix(PEARL, SEAM, 0.35))
    # card-reader plate (columns 4..11, rows 7..13)
    c.rect(4, 7, 12, 14, mix(PEARL, SEAM, 0.14))
    c.rect(4, 7, 12, 8, PEARL_L)
    c.rect(4, 13, 12, 14, PEARL_D)
    # status LEDs (row 9, just below the ring)
    c.px(5, 9, PINK)
    c.px(6, 9, (126, 232, 168))
    c.px(10, 9, (255, 214, 120))
    # pink glow strip (row 10), dark card slot (row 11) with a lit bevel at both ends
    c.rect(5, 10, 11, 11, PINK)
    c.px(4, 10, PINK_D)
    c.px(11, 10, PINK_D)
    c.rect(5, 11, 11, 12, (26, 27, 34))
    c.px(4, 11, GRAPHITE)
    c.px(11, 11, GRAPHITE)
    # speaker dots (row 12)
    for x in (5, 7, 9, 11):
        c.px(x, 12, SEAM)
    # card strip (used by the protruding card): rows 0..1
    c.rect(0, 0, 6, 2, (250, 250, 252))
    c.rect(0, 1, 6, 2, PINK)
    return c


def terminal_screen() -> Canvas:
    """12x7 face region at columns 2..13, rows 0..6: a glowing sign-up form."""
    c = Canvas(16, fill=(18, 28, 58, 255))
    c.rect(2, 0, 14, 7, GRAPHITE_D)                     # bezel
    c.rect(3, 1, 13, 6, (24, 44, 92))                   # glass
    c.rect(3, 1, 13, 2, PINK)                           # header bar
    c.px(12, 1, PINK_L)
    c.rect(3, 2, 7, 6, (54, 86, 150))                   # id photo
    c.px(4, 3, PINK_L)
    c.px(5, 3, PINK_L)
    c.rect(4, 4, 6, 6, PINK)
    c.rect(8, 2, 13, 3, (184, 224, 255))                # form lines
    c.rect(8, 3, 11, 4, (120, 168, 232))
    c.rect(8, 4, 12, 5, (184, 224, 255))
    c.rect(8, 5, 13, 6, (24, 36, 70))                   # progress bar
    c.rect(8, 5, 11, 6, (120, 255, 190))
    return c


def terminal_top() -> Canvas:
    c = Canvas(16, fill=GRAPHITE + (255,))
    r = rng("terminal_top")
    n = value_noise(16, 16, 4, r)
    c.fill_rgb(ramp(n, [GRAPHITE_D, GRAPHITE, mix(GRAPHITE, GRAPHITE_L, 0.5)], dither=0.6))
    c.rect(2, 0, 14, 1, mix(GRAPHITE, GRAPHITE_L, 0.8))
    for (x, y) in [(7, 0), (8, 0), (9, 0), (7, 1), (9, 1), (7, 2), (8, 2), (9, 2)]:   # camera ring (rows 0..2)
        c.px(x, y, PINK)
    c.px(8, 1, (14, 15, 20))
    return c


def terminal_base() -> Canvas:
    c = Canvas(16, fill=GRAPHITE + (255,))
    r = rng("terminal_base")
    n = value_noise(16, 16, 4, r)
    c.fill_rgb(ramp(n, [GRAPHITE_D, GRAPHITE, mix(GRAPHITE, GRAPHITE_L, 0.5)], dither=0.6))
    c.rect(2, 2, 14, 14, GRAPHITE)                      # top plate (region 2..13 x 2..13)
    c.outline(2, 2, 14, 14, mix(GRAPHITE, GRAPHITE_L, 0.7))
    c.rect(0, 14, 16, 15, PINK)                         # side band rows 14..15: lit pink edge + dark foot
    c.rect(0, 15, 16, 16, GRAPHITE_D)
    return c


def terminal_ring() -> Canvas:
    """Glowing pink band.  Rows 0..1 are the 2px side faces (hot core row over a saturated pink row); the top /
    bottom faces map region (3..12, 4..11): a calm pink pane with a brighter inner glow."""
    c = Canvas(16, fill=PINK + (255,))
    c.rect(3, 4, 13, 12, mix(PINK, PINK_L, 0.18))
    c.rect(4, 5, 12, 11, mix(PINK, PINK_L, 0.45))
    c.rect(6, 6, 10, 10, mix(PINK, PINK_L, 0.7))
    c.outline(3, 4, 13, 12, mix(PINK, PINK_D, 0.4))
    c.rect(0, 0, 16, 1, (255, 232, 244))
    c.rect(0, 1, 16, 2, (255, 150, 204))
    return c


def terminal_model() -> dict:
    els = [
        box("plinth", [2, 0, 2], [14, 2, 14],
            {"north": "#base", "south": "#base", "west": "#base", "east": "#base", "up": "#base", "down": "#base"}),
        box("body", [4, 2, 5], [12, 9, 11],
            {"north": "#front", "south": "#body", "west": "#body", "east": "#body"}),
        box("ring_light", [3, 7, 4], [13, 9, 12],
            {"north": "#ring", "south": "#ring", "west": "#ring", "east": "#ring", "up": "#ring", "down": "#ring"},
            uv={"north": [0, 0, 10, 2], "south": [0, 0, 10, 2], "west": [0, 0, 8, 2], "east": [0, 0, 8, 2],
                "up": [3, 4, 13, 12], "down": [3, 4, 13, 12]}),
        # sloped screen housing, leaning back 22.5 degrees from its lower front edge
        element("screen_head", [2, 9, 4], [14, 16, 7], {
            "north": face("#screen", [2, 0, 14, 7]),
            "south": face("#body", [2, 0, 14, 7]),
            "up": face("#top", [2, 0, 14, 3]),
            "down": face("#body", [2, 0, 14, 3]),
            "west": face("#body", [4, 0, 7, 7]),
            "east": face("#body", [4, 0, 7, 7]),
        }, rotation={"origin": [8, 9, 4], "axis": "x", "angle": 22.5}),
        # id card poking out of the reader slot
        box("card", [5.5, 4.3, 3], [10.5, 4.7, 5],
            {"north": "#front", "up": "#front", "down": "#front", "west": "#front", "east": "#front"},
            uv={"north": [0, 0, 5, 1], "up": [0, 0, 5, 2], "down": [0, 0, 5, 2], "west": [0, 0, 2, 1],
                "east": [0, 0, 2, 1]}, cull=False),
    ]
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": T("registration_terminal_body"), "body": T("registration_terminal_body"),
                     "front": T("registration_terminal_front"), "screen": T("registration_terminal_screen"),
                     "top": T("registration_terminal_top"), "base": T("registration_terminal_base"),
                     "ring": T("registration_terminal_ring")},
        "elements": els,
    }


# =========================================================================== DALGONA STATION
def station_top() -> Canvas:
    c = Canvas(16)
    c.fill_rgb(paint.wood(16, 16, "dalgona_top", plank=8, tone_var=0.05, knots=0, seams=False, nails=False,
                          scratches=4, gap_col=(98, 66, 40)))
    # worn edge bevel on the outermost pixels so single stations look finished
    for i in range(16):
        for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]:
            c.px(x, y, shade(c.get(x, y)[:3], 0.88))
    return c


def station_side() -> Canvas:
    """rows 0..1: table edge (horizontal grain); rows 2..7: leg wood (vertical grain); rest: plain wood."""
    r = rng("dalgona_side")
    c = Canvas(16)
    pal = paint.WOOD_PAL
    n = paint.aniso_noise(16, 16, 8, 2, r)
    c.fill_rgb(ramp(n, pal[1:5], dither=0.5))
    # table edge strip
    for x in range(16):
        c.px(x, 0, pal[5])
        c.px(x, 1, pal[1])
    # legs: vertical grain
    nv = paint.aniso_noise(16, 16, 2, 8, r)
    for y in range(2, 8):
        for x in range(16):
            idx = int(np.clip(math.floor(nv[y, x] * 4), 0, 3))
            c.px(x, y, pal[idx + 1])
        c.px(0, y, shade(c.get(0, y)[:3], 1.12))
        c.px(1, y, shade(c.get(1, y)[:3], 0.82))
    return c


def station_tin() -> Canvas:
    """Top view of the round tin (rows 0..13, centred at 8,8) + steel side strip (rows 14..15)."""
    c = Canvas(16, fill=(120, 126, 138, 255))
    xs, ys = c.grid()
    d = np.hypot(xs - 8, ys - 8)
    r = rng("dalgona_tin")
    steel = [(96, 104, 118), (122, 130, 146), (150, 158, 172), (180, 188, 200), (206, 213, 222)]
    n = value_noise(16, 16, 4, r)
    c.fill_rgb(ramp(n * 0.35 + 0.35 + (np.clip(-(xs - 8) - (ys - 8), -6, 6) / 6.0) * 0.12, steel, dither=0.5))
    # concentric tin ridges
    ring = (d >= 3.0) & (d < 4.2)
    c.mask(ring, (190, 198, 210))
    c.mask((d >= 4.2) & (d < 5.2), (94, 100, 114))
    # dark floor of the tin (mostly hidden by the cookie)
    c.mask(d < 3.0, (70, 74, 86))
    # highlight arc top-left
    for (x, y) in [(5, 5), (6, 4), (7, 4), (4, 6), (4, 7)]:
        c.px(x, y, (232, 238, 246))
    # side strip rows 14..15 (explicit UV): light rim row + body row, with embossed ticks
    for x in range(16):
        c.px(x, 14, (196, 204, 216))
        c.px(x, 15, (118, 126, 142))
    for x in range(1, 16, 4):
        c.px(x, 15, (94, 100, 114))
    # needle steel texel (0,15) and wooden handle texel (8,15) used by tiny needle faces
    c.px(0, 15, (214, 220, 228))
    c.px(8, 15, (150, 104, 62))
    return c


def station_cookie() -> Canvas:
    """Honeycomb cookie seen from above, 16x16 mapped onto the 6px disc; a five-point star stamped into it."""
    r = rng("dalgona_cookie_small")
    f = paint.caramel_field(16, 16, "dalgona_cookie_small", scale=1)
    pal = [(150, 92, 30), (176, 112, 38), (198, 134, 52), (216, 158, 72), (232, 182, 100)]
    c = Canvas(16)
    c.fill_rgb(ramp(f, pal, dither=0.5))
    xs, ys = c.grid()
    d = np.hypot(xs - 8, ys - 8)
    # tiny air bubbles first, so the stamp and rim stay clean
    for _ in range(7):
        bx, by = int(r.integers(2, 14)), int(r.integers(2, 14))
        if 3.4 < math.hypot(bx - 8, by - 8) < 6.2:
            c.px(bx, by, (246, 214, 144))
            c.px(bx + 1, by + 1, (150, 92, 30))
    # stamped five-point star: dark groove with a light lip on the lower right
    pts = paint.star_polygon(8, 8.5, 5.6, 2.4)
    groove = paint.polygon_outline_mask(c, pts, 1.0)
    lip = np.roll(np.roll(groove, 1, axis=0), 1, axis=1) & ~groove
    c.mask(lip, (248, 214, 140))
    c.mask(groove, (92, 48, 12))
    # soft raised rim: glossy band inside, dark edge outside (also fills the chamfered corners of the 6x6 area)
    c.mask((d >= 6.1) & (d < 7.3), (236, 190, 112))
    c.mask(d >= 7.3, (122, 70, 22))
    # edge texels used by the cookie's side faces (bottom-right 2x2)
    c.rect(14, 14, 16, 16, (136, 80, 26))
    return c


def _octagon(name: str, cx: float, cz: float, flat: float, y0: float, y1: float, tex: str, *, side_uv,
             top_uv: dict, dy=(0.0, 0.01, -0.01, -0.02), with_down: bool = False) -> list[dict]:
    """Regular octagon (flat-to-flat ``flat``) as four slabs: two axis-aligned, two rotated +-45 degrees.

    The slabs' top faces sit a hair apart (``dy``) so that overlapping tops never z-fight; only the end faces
    of each slab are emitted because the long faces are always inside the union.
    ``top_uv`` = {"a": uv, "b": uv, "c": uv, "d": uv} (None = default world-aligned uv).
    """
    s = flat / (1 + math.sqrt(2))
    hw, hs = flat / 2.0, s / 2.0
    w = round(s, 2)

    def sl(tag, frm, to, faces, rot=None):
        uv = {f: side_uv(w) for f in faces if f != "up"}
        if top_uv.get(tag) is not None:
            uv["up"] = top_uv[tag]
        tx = {f: tex for f in faces}
        if with_down:
            tx["down"] = tex
        return box(f"{name}_{tag}", frm, to, tx, uv=uv, rotation=rot, cull=False)

    r = lambda ang: {"origin": [cx, y0, cz], "axis": "y", "angle": ang}   # noqa: E731
    return [
        sl("a", [cx - hw, y0, cz - hs], [cx + hw, y1 + dy[0], cz + hs], ("west", "east", "up")),
        sl("b", [cx - hs, y0, cz - hw], [cx + hs, y1 + dy[1], cz + hw], ("north", "south", "up")),
        sl("c", [cx - hw, y0, cz - hs], [cx + hw, y1 + dy[2], cz + hs], ("west", "east", "up"), r(45)),
        sl("d", [cx - hw, y0, cz - hs], [cx + hw, y1 + dy[3], cz + hs], ("west", "east", "up"), r(-45)),
    ]


def dalgona_model() -> dict:
    els = []
    wood_edge = {"north": "#side", "south": "#side", "west": "#side", "east": "#side"}
    # table top (2px) and four legs
    els.append(box("tabletop", [0, 6, 0], [16, 8, 16],
                   {**wood_edge, "up": "#top", "down": "#side"},
                   uv={"north": [0, 0, 16, 2], "south": [0, 0, 16, 2], "west": [0, 0, 16, 2], "east": [0, 0, 16, 2],
                       "up": [0, 0, 16, 16], "down": [0, 2, 16, 16]}))
    for i, (x, z) in enumerate([(1, 1), (13, 1), (1, 13), (13, 13)]):
        els.append(box(f"leg_{i}", [x, 0, z], [x + 2, 6, z + 2],
                       {"north": "#side", "south": "#side", "west": "#side", "east": "#side", "down": "#side"},
                       uv={"north": [0, 2, 2, 8], "south": [0, 2, 2, 8], "west": [0, 2, 2, 8], "east": [0, 2, 2, 8],
                           "down": [0, 2, 2, 4]}))
    # under-top rails between the legs
    for i, (frm, to) in enumerate([([3, 4, 1], [13, 6, 2]), ([3, 4, 14], [13, 6, 15]),
                                   ([1, 4, 3], [2, 6, 13]), ([14, 4, 3], [15, 6, 13])]):
        els.append(box(f"rail_{i}", frm, to,
                       {"north": "#side", "south": "#side", "west": "#side", "east": "#side"},
                       uv={"north": [0, 2, 10, 4], "south": [0, 2, 10, 4], "west": [0, 2, 2, 4], "east": [0, 2, 2, 4]},
                       cull=False))
    # round tin: octagon 8px across, 2px tall (y 8..10); steel side strip, top view painted on the texture
    els += _octagon("tin", 8, 8, 8.0, 8, 10, "#tin", side_uv=lambda w: [0, 14, w, 16],
                    top_uv={"a": None, "b": None, "c": [11, 8, 12, 9], "d": [11, 8, 12, 9]})
    # honeycomb cookie on top: a chamfered 6px disc (y 10..11) made of three axis-aligned slabs.  One continuous
    # 16x16 texture is spread over the 6x6 area, so the stamped star stays intact across the slabs.
    for i, (z0, z1, x0, x1) in enumerate([(5, 6, 6, 10), (6, 10, 5, 11), (10, 11, 6, 10)]):
        k = 16.0 / 6.0
        els.append(box(f"cookie_{i}", [x0, 10, z0], [x1, 11, z1],
                       {"north": "#cookie", "south": "#cookie", "west": "#cookie", "east": "#cookie", "up": "#cookie"},
                       uv={"up": [round((x0 - 5) * k, 3), round((z0 - 5) * k, 3), round((x1 - 5) * k, 3),
                                  round((z1 - 5) * k, 3)],
                           "north": [14, 14, 16, 16], "south": [14, 14, 16, 16],
                           "west": [14, 14, 16, 16], "east": [14, 14, 16, 16]}, cull=False))
    # needle lying on the contestant's right-hand side (the seated contestant is at the front/north, so their right is
    # -x): wooden handle towards them, steel point towards the tin
    els.append(box("needle", [3.0, 8, 3.5], [3.5, 8.5, 8.0],
                   {f: "#tin" for f in ("north", "south", "west", "east", "up")},
                   uv={f: [0, 15, 1, 16] for f in ("north", "south", "west", "east", "up")}, cull=False))
    els.append(box("needle_handle", [2.75, 8, 1.5], [3.75, 9, 3.5],
                   {f: "#tin" for f in ("north", "south", "west", "east", "up")},
                   uv={f: [8, 15, 9, 16] for f in ("north", "south", "west", "east", "up")}, cull=False))
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": T("dalgona_station_top"), "top": T("dalgona_station_top"),
                     "side": T("dalgona_station_side"), "tin": T("dalgona_station_tin"),
                     "cookie": T("dalgona_station_cookie")},
        "elements": els,
    }


# =========================================================================== exports
def models() -> dict[str, dict]:
    return {
        "monitor": monitor_model(),
        "registration_terminal": terminal_model(),
        "dalgona_station": dalgona_model(),
    }


def generate(out: Out) -> None:
    out.png("textures/block/registration_terminal_body.png", terminal_body())
    out.png("textures/block/registration_terminal_front.png", terminal_front())
    out.png("textures/block/registration_terminal_screen.png", terminal_screen())
    out.png("textures/block/registration_terminal_top.png", terminal_top())
    out.png("textures/block/registration_terminal_base.png", terminal_base())
    out.png("textures/block/registration_terminal_ring.png", terminal_ring())
    out.png("textures/block/dalgona_station_top.png", station_top())
    out.png("textures/block/dalgona_station_side.png", station_side())
    out.png("textures/block/dalgona_station_tin.png", station_tin())
    out.png("textures/block/dalgona_station_cookie.png", station_cookie())


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
