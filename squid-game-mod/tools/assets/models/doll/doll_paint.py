"""Texture painting for the doll: 256x256 atlas, 1:1 texel density (16 texels / block, like terrain).

Design rules (the doll is 9 blocks tall and is mostly seen from 100+ blocks away):
  * big surfaces (dress, hair, sleeves) get only LOW-contrast texture so they do not shimmer
    when minified without mip-maps; bold colour blocks carry the read
  * hard-edged pixel art, no anti-aliasing, 0/255 alpha only
  * eyes are the only emissive texels (glow mask = every opaque texel of the eye_on regions
    that is not the dark outline ring)
"""
from __future__ import annotations

import zlib

import numpy as np

import paint as P
from paint import hx, mix, shade

REGIONS = {
    # solids (2x2) - hidden / uniform faces
    "solid_dark": (2, 2), "solid_white": (2, 2), "skin_dark": (2, 2), "hair_dark": (2, 2), "solid_yellow": (2, 2),
    "solid_yellow_d": (2, 2), "solid_lip_d": (2, 2), "solid_eye_d": (2, 2), "solid_glow": (2, 2),
    # base
    "plinth_top": (54, 54), "plinth_side": (54, 3), "step_top": (42, 42), "step_side": (42, 1),
    "shoe_front": (9, 4), "shoe_side_r": (15, 4), "shoe_side_l": (15, 4),
    "shoe_top": (9, 15), "sock": (7, 12), "petticoat": (46, 2),
    # dress
    "skirt_wall": (44, 42), "skirt_top": (8, 8),
    # body
    "waistband": (24, 3), "bodice_front": (22, 24), "bodice_back": (22, 24), "bodice_side": (15, 24),
    "collar_edge": (31, 4), "collar_top": (31, 22), "neck_core": (12, 4), "bib": (20, 8),
    # neck
    "ring_a": (17, 3), "ring_b": (17, 3), "ring_c": (17, 3), "ring_d": (17, 3), "flange": (19, 3),
    # arms
    "puff": (11, 10), "puff_top": (10, 11), "sleeve": (9, 15), "cuff": (10, 3), "hand": (7, 7), "thumb": (2, 4),
    # head
    "face": (34, 32), "eye_off": (11, 11), "eye_on": (12, 12), "mouth": (7, 3), "cheek": (5, 4), "nose": (3, 3),
    # hair
    "hair_top": (37, 36), "hair_front": (37, 10), "hair_back": (37, 33), "hair_side_r": (36, 33), "hair_side_l": (36, 33),
    "fringe": (37, 11), "strand": (4, 3), "lock_front": (4, 19), "pigtail": (7, 17), "pigtail_tip": (6, 4), "tie": (6, 3),
    "ribbon_knot": (5, 5), "ribbon_wing": (11, 7), "ribbon_wing_b": (11, 7),
}

# --------------------------------------------------------------------------- palette (muted, slightly desaturated)
OR = hx("#F0862C"); OR_L = hx("#F79A45"); OR_HL = hx("#FBB868"); OR_D = hx("#D56F22"); OR_DD = hx("#AE5519")
YE = hx("#F3C23A"); YE_L = hx("#F9D766"); YE_HL = hx("#FDEBA0"); YE_D = hx("#D49E22"); YE_DD = hx("#A87615")
WH = hx("#EAE6DB"); WH_L = hx("#F6F3EA"); WH_HL = hx("#FFFFFF"); WH_D = hx("#C9C4B6"); WH_DD = hx("#A29D8F")
SK = hx("#F2D5BF"); SK_L = hx("#F9E4D2"); SK_HL = hx("#FFF1E4"); SK_D = hx("#DDB599"); SK_DD = hx("#BE9078")
BL = hx("#EC8F8B"); BL_L = hx("#F2A7A0"); BL_D = hx("#D77274")
LIP = hx("#C03A42"); LIP_L = hx("#E0605F"); LIP_D = hx("#85202D")
HA = hx("#1F1D29"); HA_L = hx("#2F2C40"); HA_HL = hx("#5A5778"); HA_D = hx("#14121B"); HA_DD = hx("#0A090F")
ME = hx("#505760"); ME_L = hx("#6D7681"); ME_HL = hx("#9AA4AF"); ME_D = hx("#363B43"); ME_DD = hx("#22262C")
PL = hx("#454A53"); PL_L = hx("#5B616B"); PL_HL = hx("#7A818C"); PL_D = hx("#30343B"); PL_DD = hx("#1F2227")
SH = hx("#1F1E25"); SH_L = hx("#3B3A45"); SH_HL = hx("#6A6876")
HZ_Y = hx("#F0BB00"); HZ_K = hx("#1C1C1C"); LED = hx("#D8281C")
EY_W = hx("#C4BEB2"); EY_WD = hx("#9B958A"); EY_I = hx("#3B2B26"); EY_ID = hx("#241815"); EY_P = hx("#0B0807")
EY_HL = hx("#8D8882"); EY_RIM = hx("#150E0C")
GL_RIM = hx("#3A0C06"); GL_D = hx("#B3180B"); GL_M = hx("#E8331B"); GL_O = hx("#FF6A1E"); GL_Y = hx("#FFB23A"); GL_W = hx("#FFF0C4")

_SOLIDS = {
    "solid_dark": ME_DD, "solid_white": WH, "skin_dark": SK_D, "hair_dark": HA_D, "solid_yellow": YE,
    "solid_yellow_d": YE_D, "solid_lip_d": LIP_D, "solid_eye_d": EY_RIM, "solid_glow": GL_O,
}

PAINTERS = {}


def painter(*names):
    def deco(fn):
        for n in names:
            PAINTERS[n] = fn
        return fn

    return deco


def rng_for(name):
    return np.random.RandomState(zlib.crc32(name.encode()) & 0x7FFFFFFF)


# =========================================================================== helpers
def fab_noise(a, rng, amount=0.025):
    """very low contrast fabric grain (quantised to 3 levels)."""
    P.noise_tint(a, rng, amount)


def band(a, y0, y1, c):
    P.rect(a, 0, y0, a.shape[1], y1 - y0, c)


def strands(a, rng, base, dark, light, x0=0, x1=None, y0=0, y1=None, seg=(3, 9), tilt=0.0):
    """Vertical hair strand streaks: per column, random-length segments in base/dark/light."""
    h, w = a.shape[:2]
    x1 = w if x1 is None else x1
    y1 = h if y1 is None else y1
    for x in range(x0, x1):
        y = y0 - int(rng.randint(0, seg[1]))
        while y < y1:
            ln = int(rng.randint(seg[0], seg[1] + 1))
            r = rng.random_sample()
            c = base if r < 0.62 else (dark if r < 0.86 else light)
            for yy in range(max(y, y0), min(y + ln, y1)):
                xx = x + int(round(tilt * (yy - y0)))
                if x0 <= xx < x1:
                    P._px(a, xx, yy, c)
            y += ln


def sheen(a, y_center, thickness, c_mid, c_hi=None, bow=0.0, x0=0, x1=None, mask=None):
    """Horizontal glossy highlight band (dithered), optionally bowed (curved like the head)."""
    h, w = a.shape[:2]
    x1 = w if x1 is None else x1
    yy, xx = np.mgrid[0:h, 0:w]
    cx = (x0 + x1) / 2.0
    yc = y_center + bow * ((xx + 0.5 - cx) / max((x1 - x0) / 2.0, 1)) ** 2
    d = np.abs(yy + 0.5 - yc) / (thickness / 2.0)
    th = P.BAYER4[yy % 4, xx % 4]
    base = (d < 1.0) & (xx >= x0) & (xx < x1)
    if mask is not None:
        base &= mask
    sel = base & ((1 - d) * 1.4 > th)
    a[sel, :3] = c_mid
    if c_hi is not None:
        sel2 = base & ((1 - d) > 0.62) & ((1 - d) * 1.1 > th + 0.15)
        a[sel2, :3] = c_hi


# =========================================================================== solids
@painter(*_SOLIDS.keys())
def p_solid(a, rng, name=None):
    P.fill(a, _SOLIDS[name])


# =========================================================================== plinth / feet
def _plus_mask(n=54, cut=4):
    m = np.zeros((n, n), dtype=bool)
    m[cut:n - cut, :] = True
    m[:, cut:n - cut] = True
    return m


@painter("plinth_top")
def p_plinth_top(a, rng, name=None):
    h, w = a.shape[:2]
    m = _plus_mask(54, 4)
    P.fill(a, PL)
    P.noise_tint(a, rng, 0.035)
    P.speckle(a, rng, PL_L, 0.05)
    P.speckle(a, rng, PL_D, 0.05)
    # raised rim: bevel on the plus-shaped outline + inner groove
    ob = P.outline_mask(m)
    a[ob, :3] = PL_HL
    inner = P.outline_mask(m & ~ob)
    a[inner, :3] = PL_L
    g = np.zeros_like(m)
    g[6:-6, 6:-6] = True
    gm = P.outline_mask(g & m)
    a[gm, :3] = PL_D
    a[~m] = 0
    # hazard-yellow ticks at the four ends (small chevrons), a bit of colour on the dark base
    for (cx, cy, vertical) in ((27, 3, False), (27, 50, False), (3, 27, True), (50, 27, True)):
        for k in (-5, -2, 1, 4):
            if vertical:
                P.rect(a, cx - 1, cy + k, 3, 2, HZ_Y)
            else:
                P.rect(a, cx + k, cy - 1, 2, 3, HZ_Y)


@painter("plinth_side")
def p_plinth_side(a, rng, name=None):
    P.fill(a, PL)
    band(a, 0, 1, PL_HL)
    band(a, 1, 2, PL_L)
    band(a, 2, 3, PL_D)
    P.speckle(a, rng, PL_D, 0.06)


@painter("step_top")
def p_step_top(a, rng, name=None):
    P.fill(a, PL_L)
    P.noise_tint(a, rng, 0.03)
    P.speckle(a, rng, PL, 0.05)
    P.speckle(a, rng, PL_HL, 0.03)
    P.darken_edge(a, 0.0, ("b",))


@painter("step_side")
def p_step_side(a, rng, name=None):
    P.fill(a, PL_HL)


@painter("shoe_front")
def p_shoe_front(a, rng, name=None):
    P.fill(a, SH)
    band(a, 0, 1, SH_L)
    P.put(a, 2, 1, SH_HL)
    P.put(a, 3, 1, SH_L)
    band(a, 3, 4, ME_DD)


@painter("shoe_side_r")
def p_shoe_side_r(a, rng, name=None):
    # profile seen from the right: toe at the RIGHT edge
    P.fill(a, SH)
    band(a, 0, 1, SH_L)
    band(a, 3, 4, ME_DD)
    P.rect(a, 9, 1, 4, 1, SH_HL)       # shine on the toe cap
    P.rect(a, 2, 1, 2, 1, SH_L)
    P.put(a, 14, 0, SH_L)
    P.put(a, 14, 3, ME_DD)


@painter("shoe_side_l")
def p_shoe_side_l(a, rng, name=None):
    p_shoe_side_r(a, rng)
    a[...] = a[:, ::-1].copy()


@painter("shoe_top")
def p_shoe_top(a, rng, name=None):
    # top face: texture bottom edge = toe (front)
    P.fill(a, SH)
    P.noise_tint(a, rng, 0.02)
    P.rect(a, 1, 11, 7, 3, SH_L)          # toe cap
    P.rect(a, 2, 12, 3, 1, SH_HL)
    P.rect(a, 0, 5, 9, 2, ME_D)            # mary-jane strap across the instep
    P.rect(a, 6, 5, 2, 2, YE)              # buckle
    P.put(a, 6, 5, YE_L)
    P.rect(a, 2, 0, 5, 3, SH_L)            # heel opening
    P.rect(a, 3, 1, 3, 1, ME_DD)


@painter("sock")
def p_sock(a, rng, name=None):
    P.fill(a, WH)
    for y in range(1, 12, 2):
        band(a, y, y + 1, WH_L)
    band(a, 0, 1, WH_D)
    band(a, 11, 12, WH_D)
    P.rect(a, 0, 0, 1, 12, WH_D)
    P.rect(a, 6, 0, 1, 12, WH_D)
    P.noise_tint(a, rng, 0.015)


@painter("petticoat")
def p_petticoat(a, rng, name=None):
    P.fill(a, WH_L)
    for x in range(a.shape[1]):
        if x % 3 == 1:
            P.put(a, x, 1, WH_D)
        else:
            P.put(a, x, 1, WH)


# =========================================================================== dress
@painter("skirt_wall")
def p_skirt_wall(a, rng, name=None):
    h, w = a.shape[:2]            # 42 rows (waist -> hem) x 44 cols
    fab = [OR, OR, OR_L, OR]       # subtle knife pleats (period 4), low contrast
    for x in range(w):
        c = fab[x % 4]
        a[:, x, :3] = c
        a[:, x, 3] = 1
    # slow vertical shading: slightly brighter near the waist, darker towards the hem (dithered)
    P.vgrad(a, [OR_L, OR, OR, OR_D], 0, h)
    for x in range(w):
        if x % 4 == 2:
            for y in range(h):
                if (x + y) % 2 == 0:
                    a[y, x, :3] = shade(a[y, x, :3], 1.06)
        if x % 4 == 3:
            for y in range(h):
                if (x + y) % 2 == 1:
                    a[y, x, :3] = shade(a[y, x, :3], 0.94)
    P.noise_tint(a, rng, 0.012)
    # hem trim (bottom tier = bottom 6 rows): shadow line, darker band, thin yellow rick-rack
    band(a, 35, 36, OR_D)
    band(a, 38, 39, YE)
    for x in range(0, w, 2):
        P.put(a, x, 39, YE_D)
    band(a, 41, 42, OR_DD)
    band(a, 40, 41, OR_D)
    # waist gather (top rows)
    band(a, 0, 1, OR_D)


@painter("skirt_top")
def p_skirt_top(a, rng, name=None):
    P.fill(a, OR_L)     # FLAT on purpose: coplanar A/B tier tops must look identical (no z-fight flicker)


@painter("waistband")
def p_waistband(a, rng, name=None):
    P.fill(a, OR_D)
    band(a, 1, 2, YE)
    band(a, 0, 1, OR)
    band(a, 2, 3, OR_DD)


# =========================================================================== bodice / collar
def _bodice(a, rng, back=False):
    h, w = a.shape[:2]
    P.fill(a, OR)
    # rounded torso shading: centre bright, edges darker
    P.hgrad(a, [OR_D, OR, OR_L, OR, OR_D], 0, w)
    P.noise_tint(a, rng, 0.012)
    # shadow under the collar
    band(a, 0, 1, OR_DD)
    band(a, 1, 2, OR_D)
    P.rect(a, 0, 0, 1, h, OR_D)
    P.rect(a, w - 1, 0, 1, h, OR_D)
    cx = w // 2
    if not back:
        # front: centre seam and three yellow buttons
        P.rect(a, cx - 1, 2, 1, h - 2, OR_D)
        for y in (10, 15, 20):
            P.rect(a, cx, y, 2, 2, YE_L)
            P.put(a, cx + 1, y + 1, YE_D)
        # bust darts
        P.line(a, 4, 5, 6, 9, OR_D)
        P.line(a, w - 5, 5, w - 7, 9, OR_D)
    else:
        # back: zipper with a yellow pull tab
        P.rect(a, cx - 1, 2, 2, h - 2, OR_D)
        for y in range(3, h - 1, 2):
            P.put(a, cx - 1, y, ME_L)
            P.put(a, cx, y + 1, ME_L)
        P.rect(a, cx - 1, 2, 2, 3, YE)
        P.line(a, 4, 5, 6, 9, OR_D)
        P.line(a, w - 5, 5, w - 7, 9, OR_D)


@painter("bodice_front")
def p_bodice_front(a, rng, name=None):
    _bodice(a, rng, False)


@painter("bodice_back")
def p_bodice_back(a, rng, name=None):
    _bodice(a, rng, True)


@painter("bodice_side")
def p_bodice_side(a, rng, name=None):
    h, w = a.shape[:2]
    P.fill(a, OR_D)
    P.hgrad(a, [OR_DD, OR_D, OR, OR_D, OR_DD], 0, w)
    P.noise_tint(a, rng, 0.012)
    band(a, 0, 2, OR_DD)


@painter("collar_edge")
def p_collar_edge(a, rng, name=None):
    h, w = a.shape[:2]
    P.fill(a, YE)
    band(a, 0, 1, YE_HL)
    band(a, 1, 2, YE_L)
    band(a, 3, 4, YE_D)
    for x in range(1, w, 4):
        P.put(a, x, 2, YE_D)


@painter("collar_top")
def p_collar_top(a, rng, name=None):
    h, w = a.shape[:2]       # 22 rows (back -> front) x 31 cols
    P.fill(a, YE)
    P.noise_tint(a, rng, 0.02)
    # soft lighting: outer rim lighter, towards the neck hole darker
    cx, cy = w / 2.0, h / 2.0
    P.radial(a, cx, cy, 15.5, [YE_D, YE, YE_L], mask=None, power=1.0)
    P.rect(a, 0, 0, w, 1, YE_L)
    P.rect(a, 0, h - 1, w, 1, YE_L)
    P.rect(a, 0, 0, 1, h, YE_L)
    P.rect(a, w - 1, 0, 1, h, YE_L)
    # stitched inner line + centre front seam
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt(((xx + 0.5 - cx) / 11.5) ** 2 + ((yy + 0.5 - cy) / 8.2) ** 2)
    ring = (np.abs(d - 1.0) < 0.07) & ((xx + yy) % 3 != 0)
    a[ring, :3] = YE_DD
    P.rect(a, int(cx), h - 7, 1, 7, YE_D)       # centre seam towards the front (bottom edge)
    # collar tips (rounded corner pixels slightly darker)
    for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        P.put(a, x, y, YE_D)


@painter("neck_core")
def p_neck_core(a, rng, name=None):
    P.fill(a, ME_D)
    for x in range(0, a.shape[1], 3):
        P.rect(a, x, 0, 1, a.shape[0], ME)
    band(a, 0, 1, ME_DD)
    band(a, a.shape[0] - 1, a.shape[0], ME_DD)


# =========================================================================== neck cog ring + flange
@painter("ring_a")
def p_ring_a(a, rng, name=None):
    # hazard stripes (yellow / black, diagonal) - reads strongly when the ring turns
    h, w = a.shape[:2]
    for y in range(h):
        for x in range(w):
            P._px(a, x, y, HZ_Y if ((x + y) // 2) % 2 == 0 else HZ_K)
    band(a, 0, 1, shade(HZ_Y, 0.8))


@painter("ring_b")
def p_ring_b(a, rng, name=None):
    h, w = a.shape[:2]
    P.fill(a, ME_D)
    band(a, 0, 1, ME_L)
    band(a, 2, 3, ME_DD)
    for x in (3, 8, 13):
        P.put(a, x, 1, ME_HL)
        P.put(a, x + 1, 1, ME_L)


@painter("ring_c")
def p_ring_c(a, rng, name=None):
    h, w = a.shape[:2]
    P.fill(a, ME)
    band(a, 0, 1, ME_L)
    band(a, 2, 3, ME_D)
    P.rect(a, 6, 0, 5, 3, ME_DD)                # indicator housing
    P.rect(a, 7, 1, 3, 1, LED)                  # red status LED (not emissive)
    P.put(a, 7, 1, hx("#FF6A5A"))
    for x in (1, 14):
        P.put(a, x, 1, ME_HL)


@painter("ring_d")
def p_ring_d(a, rng, name=None):
    h, w = a.shape[:2]
    P.fill(a, ME_L)
    band(a, 0, 1, ME_HL)
    band(a, 2, 3, ME_D)
    # yellow index arrow (points up) so the ring's rotation is readable
    P.put(a, 8, 0, YE)
    P.rect(a, 7, 1, 3, 1, YE)
    P.rect(a, 6, 2, 5, 1, YE_D)


@painter("flange")
def p_flange(a, rng, name=None):
    h, w = a.shape[:2]       # 19 x 3
    P.fill(a, ME_D)
    band(a, 0, 1, ME_L)
    band(a, 2, 3, ME_DD)
    for x in range(1, w, 4):                   # dial tick marks
        P.rect(a, x, 1, 1, 1, YE)


# =========================================================================== arms
@painter("puff")
def p_puff(a, rng, name=None):
    h, w = a.shape[:2]
    P.fill(a, WH)
    P.hgrad(a, [WH_D, WH, WH_L, WH, WH_D], 0, w)
    band(a, 0, 1, WH_L)
    for x in (3, 7):                            # gathers
        P.rect(a, x, 1, 1, h - 3, WH_D)
    band(a, h - 2, h - 1, WH_D)
    band(a, h - 1, h, WH_DD)
    P.noise_tint(a, rng, 0.01)


@painter("puff_top")
def p_puff_top(a, rng, name=None):
    P.fill(a, WH_L)
    P.rect(a, 0, 0, a.shape[1], 1, WH)
    P.noise_tint(a, rng, 0.01)


@painter("sleeve")
def p_sleeve(a, rng, name=None):
    h, w = a.shape[:2]
    P.fill(a, WH)
    P.hgrad(a, [WH_D, WH, WH_L, WH, WH_D], 0, w)
    for x in (2, 5):
        for y in range(0, h, 2):
            P.put(a, x, y, WH_D)
    band(a, h - 1, h, WH_D)
    P.noise_tint(a, rng, 0.01)


@painter("cuff")
def p_cuff(a, rng, name=None):
    P.fill(a, YE)
    band(a, 0, 1, YE_L)
    band(a, 2, 3, YE_D)
    for x in range(1, a.shape[1], 3):
        P.put(a, x, 1, YE_D)


@painter("hand")
def p_hand(a, rng, name=None):
    h, w = a.shape[:2]
    P.fill(a, SK)
    P.hgrad(a, [SK_D, SK, SK_L, SK, SK_D], 0, w)
    for x in (2, 4):                      # finger separations
        P.rect(a, x, 4, 1, 3, SK_D)
    band(a, 6, 7, SK_DD)
    band(a, 0, 1, SK_L)


@painter("thumb")
def p_thumb(a, rng, name=None):
    P.fill(a, SK)
    band(a, 3, 4, SK_D)


# =========================================================================== face
@painter("face")
def p_face(a, rng, name=None):
    h, w = a.shape[:2]          # 32 x 34: row 0 = y 132 (hairline), row 31 = chin; col 0 = doll's right (x=-17)
    P.fill(a, SK)               # smooth porcelain: flat base, only a few deliberate shading shapes
    yy, xx = np.mgrid[0:h, 0:w]
    th = P.BAYER4[yy % 4, xx % 4]
    mid = w / 2.0
    # side + jaw falloff
    P.rect(a, 0, 0, 1, h, SK_D)
    P.rect(a, w - 1, 0, 1, h, SK_D)
    for y in range(0, h, 2):
        P.put(a, 1, y, SK_D)
        P.put(a, w - 2, y, SK_D)
    band(a, h - 1, h, SK_D)
    for x in range(0, w, 2):
        P.put(a, x, h - 2, SK_D)
    # forehead: shadow cast by the fringe, then a soft gloss
    band(a, 10, 11, SK_D)
    for x in range(0, w, 2):
        P.put(a, x, 11, SK_D)
    gloss = (((xx + 0.5 - mid) / 6.5) ** 2 + ((yy + 0.5 - 13.6) / 1.3) ** 2) <= 1.0
    a[gloss & (th > 0.3), :3] = SK_HL
    # eye sockets: soft slightly darker rounded shapes behind the eye cubes (show at their transparent corners)
    soft = mix(SK, SK_D, 0.55)
    for cx in (9.0, 25.0):
        sock = (((xx + 0.5 - cx) / 6.6) ** 2 + ((yy + 0.5 - 20.0) / 6.8) ** 2) <= 1.0
        a[sock, :3] = soft
    # eyebrows: thin, high, arched (wide-eyed look), 2 px thick in the middle
    arch = [(0, 2), (1, 1), (2, 1), (3, 0), (4, 0), (5, 0), (6, 1), (7, 1), (8, 2)]
    for c0 in (5, 21):
        for (dx, dy) in arch:
            P.put(a, c0 + dx, 12 + dy, HA)
        for dx in (3, 4, 5):
            P.put(a, c0 + dx, 13, HA_L)
    # nose bridge shadow, philtrum / chin highlights
    P.put(a, 16, 25, SK_D)
    P.put(a, 17, 25, SK_D)
    P.rect(a, 15, 30, 4, 1, SK_L)
    # blush on the cheek bones (dither) behind the cheek cubes
    for cx in (6.5, 27.5):
        bm = (((xx + 0.5 - cx) / 5.0) ** 2 + ((yy + 0.5 - 27.0) / 3.4) ** 2) <= 1.0
        a[bm & (th > 0.55), :3] = BL_L


@painter("eye_off")
def p_eye_off(a, rng, name=None):
    h, w = a.shape[:2]    # 11 x 11 : glossy but dull doll bead eye, mostly dark
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt((xx + 0.5 - 5.5) ** 2 + (yy + 0.5 - 5.5) ** 2)
    disc = d <= 5.6
    P.set_mask(a, disc, EY_RIM)                                   # lid / lash rim
    P.set_mask(a, d <= 4.8, EY_W)                                 # dull sclera (only a thin crescent shows)
    P.set_mask(a, (d <= 4.8) & (yy < 3), EY_WD)                   # lid shadow on the sclera
    iris = (((xx + 0.5 - 5.5) / 4.0) ** 2 + ((yy + 0.5 - 5.7) / 4.7) ** 2) <= 1.0
    P.set_mask(a, iris, EY_I)
    P.set_mask(a, d <= 3.3, EY_ID)
    P.set_mask(a, d <= 1.9, EY_P)                                 # pupil
    P.set_mask(a, disc & (yy <= 1), EY_RIM)                       # heavy upper lashes
    P.rect(a, 3, 3, 2, 2, EY_HL)                                  # dull highlight
    P.put(a, 6, 7, EY_WD)
    a[~disc, 3] = 0
    a[~disc, :3] = 0


@painter("eye_on")
def p_eye_on(a, rng, name=None):
    h, w = a.shape[:2]    # 12 x 12
    yy, xx = np.mgrid[0:h, 0:w]
    cx = cy = 6.0
    d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2)
    disc = d <= 6.0
    P.set_mask(a, disc, GL_RIM)                           # dark outline ring (NOT emissive)
    core = d <= 5.1
    P.radial(a, cx, cy, 5.1, [GL_W, GL_Y, GL_O, GL_O, GL_M, GL_D], mask=core, power=0.85)
    # lens ring + scanner reticle
    ring = core & (np.abs(d - 3.5) < 0.55)
    a[ring, :3] = GL_M
    P.set_mask(a, d <= 1.5, GL_W)
    P.put(a, 4, 4, GL_W)
    a[~disc, 3] = 0
    a[~disc, :3] = 0


@painter("mouth")
def p_mouth(a, rng, name=None):
    # 7x3 closed doll mouth with a cupid's bow
    P.clear(a)
    rows = ["rrdrr..".replace(".", "."),
            "RRRdRRR",
            ".rrlrr."]
    rows = [".RRdRR.", "RRRdRRR", ".rrlrr."]
    pal = {"R": LIP, "r": LIP_L, "d": LIP_D, "l": hx("#EF8A83")}
    P.stamp(a, 0, 0, rows, pal)
    P.put(a, 0, 1, LIP_D)
    P.put(a, 6, 1, LIP_D)


@painter("cheek")
def p_cheek(a, rng, name=None):
    P.clear(a)
    rows = [".BbB.", "BbbbB", "BbbbB", ".BbB."]
    P.stamp(a, 0, 0, rows, {"B": BL_L, "b": BL})


@painter("nose")
def p_nose(a, rng, name=None):
    P.stamp(a, 0, 0, ["lHl", "sss", "dDd"], {"l": SK_L, "H": SK_HL, "s": SK, "d": SK_D, "D": SK_DD})


# =========================================================================== hair
def _hair_base(a, rng, cols=None, seg=(3, 8), tilt=0.0):
    strands(a, rng, HA, HA_D, HA_L, seg=seg, tilt=tilt)


@painter("hair_top")
def p_hair_top(a, rng, name=None):
    h, w = a.shape[:2]      # 36 rows (back -> front) x 37 cols
    P.fill(a, HA)
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = w / 2.0, 17.0
    ang = np.arctan2(yy + 0.5 - cy, xx + 0.5 - cx)
    d = np.sqrt((xx + 0.5 - cx) ** 2 + ((yy + 0.5 - cy) * 1.1) ** 2)
    # radial strand streaks from the whorl at the crown
    k = (np.floor((ang + np.pi) / (2 * np.pi) * 46)).astype(int)
    rr = np.random.RandomState(5).random_sample(64)
    col = np.where(rr[k % 64] < 0.30, 1, np.where(rr[k % 64] > 0.82, 2, 0))
    a[..., :3] = np.where(col[..., None] == 1, HA_D, np.where(col[..., None] == 2, HA_L, HA))
    # glossy 'angel ring' halo (the strongest read on a black head)
    ringm = np.abs(d - 10.5) < 3.0
    th = P.BAYER4[yy % 4, xx % 4]
    sel = ringm & ((1 - np.abs(d - 10.5) / 3.0) * 1.3 > th)
    a[sel, :3] = HA_L
    sel2 = ringm & ((1 - np.abs(d - 10.5) / 3.0) > 0.72) & (yy < 20)
    a[sel2, :3] = HA_HL
    # parting line
    P.rect(a, int(cx), 0, 1, 16, HA_DD)
    P.darken_edge(a, 0.25, ("t", "l", "r", "b"))
    a[..., 3] = 1


@painter("bib")
def p_bib(a, rng, name=None):
    h, w = a.shape[:2]       # 20 x 8: collar bib hanging on the chest (scalloped lower edge)
    P.fill(a, YE)
    band(a, 0, 1, YE_L)
    band(a, h - 2, h - 1, YE_D)
    P.rect(a, w // 2 - 1, 1, 1, h - 2, YE_D)       # centre seam
    for x in range(w):
        if x % 4 in (1, 2):
            a[h - 1, x] = 0
    for x in range(2, w - 2, 4):
        P.put(a, x, 3, YE_L)


@painter("hair_front")


@painter("hair_back")
def p_hair_back(a, rng, name=None):
    h, w = a.shape[:2]       # 33 rows x 41 cols; cap = rows 0..9, bob panel = rows 10..32
    _hair_base(a, rng, seg=(4, 10))
    sheen(a, 6.0, 4.4, HA_L, HA_HL, bow=2.0)
    P.rect(a, w // 2, 0, 1, 14, HA_DD)       # centre parting at the nape
    band(a, h - 2, h - 1, HA_D)
    band(a, h - 1, h, HA_DD)
    # a few lighter locks near the bottom edge
    for x in (6, 14, 24, 32):
        P.rect(a, x, h - 6, 1, 4, HA_L)


@painter("hair_side_r")
def p_hair_side_r(a, rng, name=None):
    h, w = a.shape[:2]       # profile seen from the doll's right: front (face) at the RIGHT edge
    _hair_base(a, rng, seg=(4, 10), tilt=-0.05)
    sheen(a, 5.0, 4.2, HA_L, HA_HL, bow=-1.5)
    band(a, h - 2, h - 1, HA_D)
    band(a, h - 1, h, HA_DD)
    for x in (5, 12, 22):
        P.rect(a, x, 14, 1, 10, HA_L)


@painter("hair_side_l")
def p_hair_side_l(a, rng, name=None):
    p_hair_side_r(a, rng)
    a[...] = a[:, ::-1].copy()


@painter("fringe")
def p_fringe(a, rng, name=None):
    h, w = a.shape[:2]       # 39 x 11
    _hair_base(a, rng, seg=(3, 7))
    sheen(a, 3.0, 3.4, HA_L, HA_HL, bow=1.0)
    for x in (5, 10, 16, 21, 26, 32):        # bang separations
        P.rect(a, x, 3, 1, h - 3, HA_DD)
    band(a, h - 1, h, HA_D)


@painter("strand")
def p_strand(a, rng, name=None):
    P.fill(a, HA)
    P.rect(a, 1, 0, 1, a.shape[0], HA_L)
    P.rect(a, 3, 0, 1, a.shape[0], HA_D)


@painter("lock_front")
def p_lock_front(a, rng, name=None):
    h, w = a.shape[:2]       # 4 x 19
    strands(a, rng, HA, HA_D, HA_L, seg=(4, 9))
    P.rect(a, 1, 0, 1, h, HA_L)
    band(a, h - 1, h, HA_D)


@painter("pigtail")
def p_pigtail(a, rng, name=None):
    h, w = a.shape[:2]       # 7 x 17
    strands(a, rng, HA, HA_D, HA_L, seg=(4, 9))
    P.rect(a, 2, 1, 1, h - 6, HA_HL)
    P.rect(a, 3, 3, 1, h - 7, HA_L)
    band(a, h - 1, h, HA_D)


@painter("pigtail_tip")
def p_pigtail_tip(a, rng, name=None):
    P.fill(a, HA)
    P.rect(a, 2, 0, 1, 3, HA_L)
    band(a, 3, 4, HA_D)


@painter("tie")
def p_tie(a, rng, name=None):
    P.fill(a, YE)
    band(a, 0, 1, YE_L)
    band(a, 2, 3, YE_D)
    for x in range(1, 6, 2):
        P.put(a, x, 1, YE_D)


@painter("ribbon_knot")
def p_ribbon_knot(a, rng, name=None):
    P.fill(a, YE)
    P.rect(a, 1, 1, 3, 3, YE_L)
    P.put(a, 2, 2, YE_HL)
    P.darken_edge(a, 0.15, ("b", "r"))


@painter("ribbon_wing")
def p_ribbon_wing(a, rng, name=None):
    h, w = a.shape[:2]       # 11 x 7
    P.fill(a, YE)
    P.vgrad(a, [YE_L, YE, YE_D], 0, h)
    P.line(a, 2, 1, 8, 1, YE_HL)
    P.line(a, 1, 3, 9, 3, YE_D)           # fold
    P.darken_edge(a, 0.12, ("b",))
    # swallow-tail notch at the outer end (left edge of the picture)
    for y, x in ((2, 0), (3, 0), (4, 0), (3, 1)):
        a[y, x] = 0


@painter("ribbon_wing_b")
def p_ribbon_wing_b(a, rng, name=None):
    p_ribbon_wing(a, rng)
    a[..., :3] = np.where(a[..., 3:4] > 0, shade(a[..., :3], 0.8), 0)


# =========================================================================== driver
def paint_all(atlas):
    for name in REGIONS:
        a = atlas.view(name)
        fn = PAINTERS.get(name)
        if fn is None:
            raise KeyError("no painter for region " + name)
        rng = rng_for(name)
        if name in _SOLIDS:
            fn(a, rng, name)
        else:
            fn(a, rng, name)
    # emissive texels: eyes only
    r = atlas.regions["eye_on"]
    yy, xx = np.mgrid[0:r.h, 0:r.w]
    d = np.sqrt((xx + 0.5 - 6.0) ** 2 + (yy + 0.5 - 6.0) ** 2)
    atlas.mark_glow("eye_on", d <= 5.1)
    atlas.mark_glow("solid_glow")
