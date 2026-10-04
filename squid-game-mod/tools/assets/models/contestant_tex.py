"""Procedural pixel-art for the contestant (128x128).

Skin and hair are painted in GREYSCALE so Java can tint them; face decals are opaque ink
on transparent (no tint); tracksuit / shoes are in colour.
"""
from __future__ import annotations

from common import *  # noqa: F401,F403
from lib.build import ModelBuilder
from lib.paint import (BoxPaint, Canvas, Face, add, fabric, grey, hash01, hexc, mix, mul, ramp)

# ---- palette ----------------------------------------------------------------------------------
TEAL = [hexc("1c5a50"), hexc("276f61"), hexc("2f8171"), hexc("3e9886"), hexc("63b7a2")]   # jacket ramp
TROU = [hexc("184d45"), hexc("215f54"), hexc("2a7062"), hexc("358672")]                   # trouser ramp
WHITE = [hexc("b8c4c2"), hexc("dde5e3"), hexc("f1f5f4"), hexc("ffffff")]
SILVER = hexc("c4cacf")
SILVER_D = hexc("7d868e")
SOLE = hexc("c9cdd6")
SOLE_D = hexc("8f95a3")
SHOE = [hexc("c5cad4"), hexc("e3e6ec"), hexc("f6f7fa")]

INK = hexc("1a1519")
INK_W = hexc("f3f3f6")
BROW = hexc("2e211c")
MOUTH = hexc("7c3a3e")
FRECK = hexc("a2694e")
BAG = hexc("6e6478")


def sk(v: float):
    return grey(v)


# =========================================================================================
# tracksuit
# =========================================================================================
def paint_jacket(mb: ModelBuilder, cv: Canvas) -> None:
    bp = mb.paint_box(cv, "jacket")
    for k, f in bp.faces.items():
        fabric(f, TEAL, seed=11 + len(k), noise=0.30, top_light=0.35, bottom_dark=0.30)
    # --- front: zipper (centre two columns) above and below the bib (rows 3..7 stay plain green)
    f = bp.front
    for j in list(range(0, 3)) + list(range(8, 12)):
        f.set(3, j, TEAL[0])                       # zipper tape (shadow side)
        f.set(4, j, SILVER if j % 2 == 0 else SILVER_D)   # teeth
    f.set(3, 2, mix(TEAL[0], SILVER_D, 0.35))
    # side pockets (slanted slits) below the bib
    for k in range(2):
        f.set(1 + k, 9 + k, TEAL[0]); f.set(6 - k, 9 + k, TEAL[0])
        f.set(1 + k, 10 + k - 1 + 1, TEAL[1]) if False else None
    # armhole shading at the upper corners
    f.set(0, 2, TEAL[0]); f.set(7, 2, TEAL[0]); f.set(0, 3, TEAL[0]); f.set(7, 3, TEAL[0])
    f.edge_ao(0.86, "lr")
    # --- back: yoke seam + centre seam below the number bib (rows 3..7 plain)
    b = bp.back
    b.hline(2, 0, 7, TEAL[0])
    for j in range(8, 12):
        b.set(3, j, TEAL[0]); b.set(4, j, TEAL[2])
    b.edge_ao(0.86, "lr")
    # --- sides: side seam + armpit shadow
    for side in (bp.left, bp.right):
        side.vline(1, 2, 11, TEAL[0])
        side.edge_ao(0.9, "lr")
        side.shade_where(lambda i, j: j in (2, 3), 0.93)
    bp.top.fill(TEAL[2])
    bp.bottom.fill(TEAL[0])


def paint_hem(mb: ModelBuilder, cv: Canvas) -> None:
    bp = mb.paint_box(cv, "hem")
    for k, f in bp.faces.items():
        for j in range(f.h):
            for i in range(f.w):
                rib = ((i + (f.x % 2)) % 2 == 0)
                f.set(i, j, TEAL[1] if rib else TEAL[0])
        f.hline(f.h - 1, 0, f.w - 1, TEAL[0])
    f = bp.front
    for j in range(2):
        f.set(3, j, TEAL[0]); f.set(4, j, SILVER if j == 0 else SILVER_D)
    bp.top.fill(TEAL[0]); bp.bottom.fill(TEAL[0])


def paint_collar(mb: ModelBuilder, cv: Canvas) -> None:
    bp = mb.paint_box(cv, "collar")
    for k, f in bp.faces.items():
        fabric(f, WHITE, seed=5, noise=0.3, top_light=0.4, bottom_dark=0.4, base_index=1.5)
        if k in ("north", "south", "east", "west"):
            f.hline(f.h - 1, 0, f.w - 1, WHITE[0])     # shadow line where the collar meets the jacket
    f = bp.front
    for j in range(2):
        f.set(3, j, SILVER_D if j else TEAL[0]); f.set(4, j, SILVER)
    f.set(4, 1, SILVER_D); f.set(3, 1, SILVER)           # zipper pull
    b = bp.back
    b.set(3, 1, WHITE[0]); b.set(4, 1, WHITE[0])         # care-label tab
    bp.top.fill(WHITE[1]); bp.bottom.fill(WHITE[0])


def stripe_cols(face: Face, cols, j0: int, j1: int, color) -> None:
    for i in cols:
        face.vline(i, j0, j1, color)


def paint_limb(mb: ModelBuilder, cv: Canvas, name: str, left: bool, ramp_, rows_stripe, cuff_rows=0,
               seed=0, hem_dark=False) -> BoxPaint:
    bp = mb.paint_box(cv, name)
    for k, f in bp.faces.items():
        fabric(f, ramp_, seed=seed + len(k), noise=0.26, top_light=0.30, bottom_dark=0.30)
    outer = bp.left if left else bp.right
    inner = bp.right if left else bp.left
    j0, j1 = rows_stripe
    # white tracksuit stripe: a thin line on the outer face (a little forward of centre) plus
    # a 1px line on the outer edge of the front and back faces, so it reads from every side
    outer_col = 1 if left else 2           # left side face: i=0 is the front; right side face: i=0 is the back
    outer.vline(outer_col, j0, j1, WHITE[2])
    front_outer = (bp.front.w - 1) if left else 0
    back_outer = 0 if left else (bp.back.w - 1)
    bp.front.vline(front_outer, j0, j1, WHITE[2])
    bp.back.vline(back_outer, j0, j1, WHITE[1])
    # AO on the inner side & edges
    inner.shade_where(lambda i, j: True, 0.88)
    for f in (bp.front, bp.back, outer, inner):
        f.edge_ao(0.9, "lr")
    if cuff_rows:
        for f in (bp.front, bp.back, outer, inner):
            for j in range(f.h - cuff_rows, f.h):
                for i in range(f.w):
                    f.set(i, j, ramp_[0] if (i % 2 == 0) else ramp_[1])
        for f in (bp.front, bp.back, outer, inner):
            f.hline(f.h - cuff_rows, 0, f.w - 1, ramp_[0])
    bp.bottom.fill(ramp_[0])
    return bp


def paint_arms(mb: ModelBuilder, cv: Canvas) -> None:
    for n, left in (("left", True), ("right", False)):
        bp = paint_limb(mb, cv, f"{n}_sleeve_upper", left, TEAL, (1, 6), seed=31)
        bp.top.fill(TEAL[3])
        bp.top.edge_ao(0.9)
        # shoulder seam
        for f in (bp.front, bp.back, bp.left, bp.right):
            f.hline(1, 0, f.w - 1, TEAL[0])
        bp = paint_limb(mb, cv, f"{n}_sleeve_lower", left, TEAL, (0, 3), cuff_rows=2, seed=47)
        bp.top.fill(TEAL[1])


def paint_legs(mb: ModelBuilder, cv: Canvas) -> None:
    for n, left in (("left", True), ("right", False)):
        bp = paint_limb(mb, cv, f"{n}_thigh", left, TROU, (0, 6), seed=61)
        bp.top.fill(TROU[1])
        # waistband hint / knee crease
        for f in (bp.front, bp.back):
            f.hline(6, 0, f.w - 1, TROU[2])
        bp = paint_limb(mb, cv, f"{n}_shin_c", left, TROU, (0, 5), seed=71)
        for f in (bp.front, bp.back, bp.left, bp.right):
            f.hline(5, 0, f.w - 1, TROU[0])     # trouser hem
            f.hline(2, 0, f.w - 1, TROU[2])     # soft knee crease
        bp.top.fill(TROU[1])


def paint_shoes(mb: ModelBuilder, cv: Canvas) -> None:
    for n, left in (("left", True), ("right", False)):
        bp = mb.paint_box(cv, f"{n}_shoe_c")
        for k, f in bp.faces.items():
            f.fill(SHOE[2])
        outer = bp.left if left else bp.right
        inner = bp.right if left else bp.left
        for side in (outer, inner):
            # side faces are 6 wide x 3 high; left face: i=0 is the front (toe), right face: i=5 is the front
            toe = (0, 1) if side is bp.left else (4, 5)
            heel = (4, 5) if side is bp.left else (0, 1)
            side.hline(side.h - 1, 0, side.w - 1, SOLE)                      # rubber sole
            for i in range(side.w):
                side.set(i, side.h - 2, SHOE[1])                              # midsole shade
            for i in toe:
                side.set(i, side.h - 2, SHOE[0])                              # toe-bumper
            side.set(toe[0] if side is bp.left else toe[1], side.h - 3, SHOE[1])
            for i in heel:
                side.set(i, 0, SHOE[1]); side.set(i, 1, SHOE[1])              # heel counter
            lace_c = (2, 3)
            for i in lace_c:
                side.set(i, 0, SHOE[0])                                       # lace edge shadow
            side.set(lace_c[1] if side is bp.left else lace_c[0], 1, SHOE[1])
        f = bp.front
        f.hline(f.h - 1, 0, f.w - 1, SOLE)
        f.hline(f.h - 2, 0, f.w - 1, SHOE[1])
        f.set(1, 0, SHOE[1]); f.set(2, 0, SHOE[1])
        b_ = bp.back
        b_.hline(b_.h - 1, 0, b_.w - 1, SOLE)
        b_.hline(b_.h - 2, 0, b_.w - 1, SHOE[1])
        b_.set(1, 0, SHOE[0]); b_.set(2, 0, SHOE[0])                          # pull tab
        t = bp.top
        t.fill(SHOE[2])
        t.hline(0, 0, 3, SOLE_D)                                              # ankle opening (back edge)
        t.hline(1, 0, 3, SHOE[0])
        for j in (2, 4):
            t.hline(j, 0, 3, SHOE[1])                                         # lace bars
        t.set(1, 3, SHOE[0]); t.set(2, 3, SHOE[0])
        bt = bp.bottom
        for j in range(bt.h):
            for i in range(bt.w):
                bt.set(i, j, SOLE if (i + j) % 2 == 0 else SOLE_D)


# =========================================================================================
# skin (greyscale, tinted by Java)
# =========================================================================================
def paint_head(mb: ModelBuilder, cv: Canvas) -> None:
    bp = mb.paint_box(cv, "head")
    for f in bp.faces.values():
        f.fill(sk(244))
    f = bp.front
    f.rect(0, 0, 8, 3, sk(247))                       # forehead
    f.hline(3, 0, 7, sk(241))                         # brow shadow
    for i in (1, 2, 5, 6):                            # cheek blush
        f.set(i, 6, sk(237))
    f.set(3, 5, sk(231)); f.set(4, 5, sk(231))        # nose
    f.set(3, 4, sk(251)); f.set(4, 4, sk(251))        # nose bridge highlight
    f.hline(7, 0, 7, sk(230))                         # chin/jaw line
    f.set(0, 6, sk(236)); f.set(7, 6, sk(236)); f.set(0, 7, sk(222)); f.set(7, 7, sk(222))
    for side in (bp.left, bp.right):
        side.hline(7, 0, 7, sk(228))
        side.rect(0, 0, 8, 2, sk(246))
        # temple / jaw shading toward the front
    bp.back.rect(0, 5, 8, 3, sk(234))                 # nape
    bp.top.fill(sk(242))
    bp.bottom.fill(sk(214))
    bp.bottom.rect(0, 0, 8, 2, sk(204))
    # ears
    for n in ("ear_l", "ear_r"):
        e = mb.paint_box(cv, n)
        e.fill(sk(238))
        for f in (e.left, e.right):
            f.rect(0, 0, 2, 2, sk(238))
            f.set(0 if f is e.left else 1, 1, sk(214))
            f.set(1 if f is e.left else 0, 0, sk(226))
    nk = mb.paint_box(cv, "neck")
    for f in nk.faces.values():
        f.fill(sk(232))
    nk.front.hline(2, 0, 3, sk(224)); nk.back.hline(2, 0, 3, sk(224))
    nk.bottom.fill(sk(210))


def paint_hands(mb: ModelBuilder, cv: Canvas) -> None:
    for n, left in (("left", True), ("right", False)):
        bp = mb.paint_box(cv, f"{n}_hand")
        for f in bp.faces.values():
            f.fill(sk(242))
        # fingers curled: front of the fist (the palm side faces inward) - finger gaps + thumb line
        f = bp.front
        f.hline(0, 0, 3, sk(214))           # shadow under the cuff
        f.vline(1, 1, 3, sk(226)); f.vline(2, 1, 3, sk(226))
        f.hline(3, 0, 3, sk(228))
        inner = bp.right if left else bp.left
        inner.hline(0, 0, 3, sk(214))
        inner.hline(1, 0, 3, sk(236))        # thumb
        inner.hline(2, 0, 2, sk(226))
        outer = bp.left if left else bp.right
        outer.hline(0, 0, 3, sk(216)); outer.hline(3, 0, 3, sk(230))
        bp.back.hline(0, 0, 3, sk(214)); bp.back.hline(3, 0, 3, sk(230))
        bp.bottom.fill(sk(222)); bp.top.fill(sk(224))


# =========================================================================================
# hair (greyscale)
# =========================================================================================
def strand(face: Face, i: int, j: int, seed: int, base=204, amp=46, brk=3) -> float:
    col = hash01(face.x + i, 7, seed)
    seg = hash01(face.x + i, (j + int(col * 5)) // brk, seed + 13)
    v = base + (col - 0.5) * amp + (seg - 0.5) * amp * 0.9
    return round(v / 10) * 10


def paint_hair_face(face: Face, cov, seed: int, base=204, amp=46, sheen_rows=1, dark_edge=True) -> None:
    for j in range(face.h):
        for i in range(face.w):
            if cov(i, j):
                v = strand(face, i, j, seed, base, amp)
                if j < sheen_rows:
                    v += 14
                face.set(i, j, grey(min(250, v)))
    if dark_edge:
        # darken the lowest covered pixel of each column (hair-line shadow / ends)
        for i in range(face.w):
            last = -1
            for j in range(face.h):
                if cov(i, j):
                    last = j
            if last >= 0:
                px = face.get(i, last)
                face.set(i, last, grey(px[0] * 0.78))


def cover(rows_by_col):
    """rows_by_col: callable i -> max covered row (inclusive) or -1."""
    return lambda i, j: j <= rows_by_col(i)


def shell(bp: BoxPaint, seed: int, front, sides, back, top=lambda i, j: True, base=204, amp=46,
          front_side_free=(0, 0)) -> None:
    """sides(i, j, is_right_face) ; front/back(i, j)."""
    paint_hair_face(bp.front, front, seed + 1, base, amp)
    paint_hair_face(bp.back, back, seed + 2, base, amp)
    paint_hair_face(bp.right, lambda i, j: sides(i, j, True), seed + 3, base, amp)
    paint_hair_face(bp.left, lambda i, j: sides(i, j, False), seed + 4, base, amp)
    paint_hair_face(bp.top, top, seed + 5, base, amp, sheen_rows=0, dark_edge=False)
    # top: crown swirl highlight
    t = bp.top
    for j in range(t.h):
        for i in range(t.w):
            if top(i, j):
                d = ((i - 3.5) ** 2 + (j - 2.5) ** 2) ** 0.5
                px = t.get(i, j)
                if d < 1.6:
                    t.set(i, j, grey(min(250, px[0] + 16)))


def vnoise(x: float, y: float, seed: int) -> float:
    """Smooth value noise in [0,1)."""
    import math
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    a = hash01(x0, y0, seed); b = hash01(x0 + 1, y0, seed)
    c = hash01(x0, y0 + 1, seed); d = hash01(x0 + 1, y0 + 1, seed)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def curl_face(f: Face, cov, seed: int, scale: float = 1.9) -> None:
    """Clumpy curls: smooth noise bands + dark gaps + bright curl tops."""
    for j in range(f.h):
        for i in range(f.w):
            if not cov(i, j):
                continue
            n = vnoise((f.x + i) / scale + 3.1 * seed, (f.y + j) / scale, seed)
            n2 = vnoise((f.x + i) / (scale * 0.6) + 17, (f.y + j) / (scale * 0.6), seed + 5)
            v = 120 + n * 120 + (n2 - 0.5) * 50
            if hash01(f.x + i, f.y + j, seed + 3) > 0.9:
                v -= 55
            f.set(i, j, grey(max(90, min(250, round(v / 14) * 14))))


def paint_hair(mb: ModelBuilder, cv: Canvas) -> None:
    # ---- buzz cut -------------------------------------------------------------------------
    bp = mb.paint_box(cv, "buzz")
    shell(bp, 100,
          front=lambda i, j: j == 0 or (j == 1 and i in (0, 7)),
          sides=lambda i, j, right: j <= 2 or (j == 3 and ((i <= 3) if right else (i >= 4))),
          back=lambda i, j: j <= 4,
          base=190, amp=26)
    for f in bp.faces.values():
        for j in range(f.h):                       # stipple: bristly
            for i in range(f.w):
                if f.get(i, j)[3] > 0 and hash01(f.x + i, f.y + j, 9) > 0.55:
                    px = f.get(i, j)
                    f.set(i, j, grey(px[0] - 18))

    # ---- short ----------------------------------------------------------------------------
    bp = mb.paint_box(cv, "short")
    shell(bp, 200,
          front=lambda i, j: j <= 1 or (j == 2 and i in (0, 1, 3, 4, 6, 7)) or (j == 3 and i in (0, 7)),
          sides=lambda i, j, right: j <= 3 or (j == 4 and ((i == 7) if right else (i == 0))),
          back=lambda i, j: j <= 6,
          base=200, amp=50)
    bt = mb.paint_box(cv, "short_top")
    for k, f in bt.faces.items():
        paint_hair_face(f, lambda i, j: True, 210 + len(k), 214, 40, sheen_rows=0, dark_edge=False)

    # ---- side parted ------------------------------------------------------------------------
    bp = mb.paint_box(cv, "parted")
    shell(bp, 300,
          front=lambda i, j: j <= 1 or (j == 2 and i >= 2) or (j == 3 and i >= 6),
          sides=lambda i, j, right: j <= 3 or (j == 4 and ((i == 7) if right else (i == 0))),
          back=lambda i, j: j <= 6,
          base=206, amp=46)
    t = bp.top
    for j in range(2, 8):                          # part line (i=5, front -> back)
        t.set(5, j, grey(110))
    for j in range(0, 8):
        for i in range(8):
            if i > 5 and (i + j) % 3 == 0:
                px = t.get(i, j)
                t.set(i, j, grey(px[0] - 22))
    f = bp.front                                    # swept-fringe highlight
    for (i, j) in [(3, 2), (4, 2), (5, 2), (6, 3), (7, 3), (6, 2)]:
        px = f.get(i, j)
        if px[3] > 0:
            f.set(i, j, grey(min(250, px[0] + 24)))
    for n in ("parted_quiff", "parted_puff"):
        bq = mb.paint_box(cv, n)
        for k, f in bq.faces.items():
            paint_hair_face(f, lambda i, j: True, 330 + len(k) + len(n), 222, 36, sheen_rows=0, dark_edge=False)

    # ---- curly / afro -----------------------------------------------------------------------
    bp = mb.paint_box(cv, "curly")
    curl_face(bp.front, lambda i, j: j <= 2 or (j == 3 and i in (0, 1, 6, 7)), 4)
    curl_face(bp.back, lambda i, j: j <= 6, 5)
    curl_face(bp.right, lambda i, j: j <= 6 and not (i == 7 and j >= 3), 6)
    curl_face(bp.left, lambda i, j: j <= 6 and not (i == 0 and j >= 3), 7)
    curl_face(bp.top, lambda i, j: True, 8)
    for n in ("curly_top", "curly_back", "curly_side_l", "curly_side_r"):
        bq = mb.paint_box(cv, n)
        for k, f in bq.faces.items():
            curl_face(f, lambda i, j: True, 20 + len(k) + len(n))

    # ---- long -------------------------------------------------------------------------------
    bp = mb.paint_box(cv, "long")
    shell(bp, 500,
          front=lambda i, j: j <= 1 or (j == 2 and i in (0, 1, 2, 5, 6, 7)) or (j == 3 and i in (0, 7)),
          sides=lambda i, j, right: not (j >= 4 and ((i == 7) if right else (i == 0))),
          back=lambda i, j: True,
          base=208, amp=44)
    for n in ("long_curtain", "long_side_l", "long_side_r"):
        bq = mb.paint_box(cv, n)
        for k, f in bq.faces.items():
            paint_hair_face(f, lambda i, j: True, 520 + len(k), 208, 44, sheen_rows=0,
                            dark_edge=(k in ("north", "south", "east", "west")))

    # ---- ponytail ---------------------------------------------------------------------------
    bp = mb.paint_box(cv, "pony")
    shell(bp, 600,
          front=lambda i, j: j == 0 or (j == 1 and i in (0, 1, 6, 7)),
          sides=lambda i, j, right: j <= 2 or (j == 3 and ((i <= 2) if right else (i >= 5))),
          back=lambda i, j: j <= 4,
          base=206, amp=44)
    for n in ("pony_seg0", "pony_seg1"):
        bq = mb.paint_box(cv, n)
        for k, f in bq.faces.items():
            paint_hair_face(f, lambda i, j: True, 620 + len(k) + len(n), 208, 44, sheen_rows=0,
                            dark_edge=(k in ("north", "south", "east", "west")))
    bq = mb.paint_box(cv, "pony_seg1")
    for f in (bq.front, bq.back, bq.left, bq.right):          # darker, thinner tip
        f.hline(f.h - 1, 0, f.w - 1, grey(120))
        f.hline(f.h - 2, 0, f.w - 1, grey(160))
    bq = mb.paint_box(cv, "pony_tie")
    for k, f in bq.faces.items():
        f.fill(grey(246))
        f.hline(0, 0, f.w - 1, grey(196))
        f.hline(f.h - 1, 0, f.w - 1, grey(196))

    # ---- bun --------------------------------------------------------------------------------
    bp = mb.paint_box(cv, "bun_cap")
    shell(bp, 700,
          front=lambda i, j: j == 0 or (j == 1 and i in (0, 7)),
          sides=lambda i, j, right: j <= 2,
          back=lambda i, j: j <= 3,
          base=206, amp=44)
    bq = mb.paint_box(cv, "bun_knot")
    for k, f in bq.faces.items():
        curl_face(f, lambda i, j: True, 50 + len(k), scale=1.6)
    bq = mb.paint_box(cv, "bun_tie")
    for k, f in bq.faces.items():
        f.fill(grey(246))
        f.hline(0, 0, f.w - 1, grey(196))


# =========================================================================================
# faces / glasses
# =========================================================================================
FACES = [
    # 0 neutral
    ["........",
     "........",
     "........",
     ".BB..BB.",
     ".WK..KW.",
     ".WK..KW.",
     "...MM...",
     "........"],
    # 1 narrow eyes, smirk
    ["........",
     "........",
     "........",
     "........",
     ".KK..KK.",
     ".WK..KW.",
     "....MMM.",
     "........"],
    # 2 round eyes, thick brows, small open mouth
    ["........",
     "........",
     "........",
     "BBB..BBB",
     ".WK..KW.",
     ".WK..KW.",
     ".WK..KW.",
     "...MM..."],
    # 3 freckles
    ["........",
     "........",
     "........",
     ".BB..BB.",
     ".WK..KW.",
     ".WK..KW.",
     ".F.FF.F.",
     "..FMMF.."],
    # 4 tired / worried
    ["........",
     "........",
     "..B..B..",
     ".B....B.",
     ".KK..KK.",
     ".WK..KW.",
     ".DD..DD.",
     "..MMMM.."],
    # 5 stern
    ["........",
     "........",
     ".B....B.",
     "..B..B..",
     ".WK..KW.",
     ".KK..KK.",
     "..MMMM..",
     ".M....M."],
]
FACE_PAL = {"K": INK, "W": INK_W, "B": BROW, "M": MOUTH, "F": FRECK, "D": BAG}


def paint_faces(mb: ModelBuilder, cv: Canvas) -> None:
    for i, rows in enumerate(FACES):
        f = mb.paint_rect(cv, f"face{i}")
        f.stamp(0, 0, rows, FACE_PAL)
    g = mb.paint_rect(cv, "glasses_front")
    g.stamp(0, 0, ["KKKK.KKKK",
                   "K..KKK..K",
                   "KKKK.KKKK"], {"K": hexc("23201f")})
    for n in ("glasses_temple_l", "glasses_temple_r"):
        bp = mb.paint_box(cv, n)
        bp.fill(hexc("23201f"))


def paint_contestant(mb: ModelBuilder) -> Canvas:
    cv = Canvas(mb.geo.tex_w, mb.geo.tex_h)
    paint_jacket(mb, cv)
    paint_hem(mb, cv)
    paint_collar(mb, cv)
    paint_arms(mb, cv)
    paint_legs(mb, cv)
    paint_shoes(mb, cv)
    paint_head(mb, cv)
    paint_hands(mb, cv)
    paint_hair(mb, cv)
    paint_faces(mb, cv)
    return cv
