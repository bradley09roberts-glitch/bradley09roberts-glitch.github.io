"""Procedural pixel-art for the guard (128x128): pink jumpsuit, black hood/mask/gloves/boots, rifle."""
from __future__ import annotations

from common import *  # noqa: F401,F403
from lib.build import ModelBuilder
from lib.paint import BoxPaint, Canvas, Face, fabric, grey, hash01, hexc, mix, ramp

PINK = [hexc("a03a62"), hexc("d4568a"), hexc("ee6c9c"), hexc("f98cb2"), hexc("ffb0cb")]
BLK = [hexc("0d0d10"), hexc("17171b"), hexc("232329"), hexc("34343c"), hexc("4a4a54")]
SILVER = hexc("b9bec6")
SILVER_D = hexc("6c727c")
WHITE = hexc("f1f1f4")
GUN = [hexc("2a2d33"), hexc("3a3e46"), hexc("4d525b"), hexc("686e79")]
SOLE = hexc("3a3a42")
SOLE_L = hexc("56565f")


def paint_torso(mb: ModelBuilder, cv: Canvas) -> None:
    bp = mb.paint_box(cv, "torso")
    for k, f in bp.faces.items():
        fabric(f, PINK, seed=3 + len(k), noise=0.28, top_light=0.35, bottom_dark=0.25, base_index=2.0)
    f = bp.front
    for j in range(12):                                   # zipper
        f.set(3, j, PINK[0])
        f.set(4, j, SILVER if j % 2 == 0 else SILVER_D)
    f.set(3, 0, SILVER_D); f.set(4, 0, SILVER)
    for (i0) in (0, 5):                                   # chest pockets
        f.rect(i0, 4, 3, 3, PINK[1])
        f.hline(4, i0, i0 + 2, PINK[0])
        f.hline(6, i0, i0 + 2, PINK[3] if False else PINK[1])
        f.set(i0 + 1, 5, PINK[2])
        f.vline(i0, 4, 6, PINK[0]) if i0 == 0 else f.vline(i0 + 2, 4, 6, PINK[0])
    f.set(1, 6, SILVER_D); f.set(6, 6, SILVER_D)          # snaps
    f.edge_ao(0.88, "lr")
    b = bp.back
    b.hline(2, 0, 7, PINK[0])
    b.vline(3, 3, 11, PINK[0]); b.vline(4, 3, 11, PINK[2])
    b.edge_ao(0.88, "lr")
    for side in (bp.left, bp.right):
        side.vline(1, 2, 11, PINK[0])
        side.edge_ao(0.9, "lr")
    bp.top.fill(PINK[3]); bp.bottom.fill(PINK[0])

    bp = mb.paint_box(cv, "belt")
    for k, f in bp.faces.items():
        for j in range(f.h):
            for i in range(f.w):
                f.set(i, j, BLK[2] if j == 0 else BLK[1])
        f.hline(f.h - 1, 0, f.w - 1, BLK[0])
    for i in range(0, 8, 2):                               # belt loops texture
        bp.front.set(i, 0, BLK[3])
    bp = mb.paint_box(cv, "buckle")
    for k, f in bp.faces.items():
        f.fill(SILVER)
        f.edge_ao(0.8)
    bp.front.rect(0, 0, 2, 2, SILVER); bp.front.set(0, 0, WHITE); bp.front.set(1, 1, SILVER_D)
    for n in ("pouch_l", "pouch_r"):
        bp = mb.paint_box(cv, n)
        for k, f in bp.faces.items():
            f.fill(BLK[2]); f.edge_ao(0.78)
        bp.front.hline(0, 0, 1, BLK[0]); bp.front.set(1, 1, SILVER_D)
    bp = mb.paint_box(cv, "collar")
    for k, f in bp.faces.items():
        fabric(f, BLK, seed=21, noise=0.35, top_light=0.3, bottom_dark=0.3, base_index=1.2)
        f.edge_ao(0.85, "lr")
    bp.front.vline(3, 0, 2, BLK[0]); bp.front.vline(4, 0, 2, BLK[0])
    bp.front.set(3, 0, SILVER_D)


def hood_cloth(f: Face, seed: int, folds=True) -> None:
    fabric(f, BLK, seed=seed, noise=0.38, top_light=0.5, bottom_dark=0.25, base_index=1.1)
    if folds:
        for i in range(f.w):
            if hash01(f.x + i, 3, seed) > 0.62:
                f.vline(i, 0, f.h - 1, BLK[0])
            elif hash01(f.x + i, 5, seed) > 0.8:
                f.vline(i, 0, f.h - 1, BLK[3])


def paint_head(mb: ModelBuilder, cv: Canvas) -> None:
    bp = mb.paint_box(cv, "hood_shell")
    for k, f in bp.faces.items():
        hood_cloth(f, 40 + len(k))
    t = bp.top
    t.vline(3, 0, 7, BLK[0]); t.vline(4, 0, 7, BLK[3])             # centre seam
    # hood opening border on the front: lighter fold ring
    f = bp.front
    for i in range(8):
        f.set(i, 0, BLK[3]); f.set(i, 7, BLK[0])
    for j in range(8):
        f.set(0, j, BLK[0]); f.set(7, j, BLK[0])
        f.set(1, j, BLK[2]); f.set(6, j, BLK[2])
    bp.back.vline(3, 0, 7, BLK[0]); bp.back.vline(4, 0, 7, BLK[3])
    for side in (bp.left, bp.right):
        for j in range(8):
            side.set(2 + (j // 3), j, BLK[0])
    bp = mb.paint_box(cv, "hood_cowl")
    for k, f in bp.faces.items():
        hood_cloth(f, 60 + len(k))
    bp = mb.paint_box(cv, "hood_tail")
    for k, f in bp.faces.items():
        hood_cloth(f, 70 + len(k))
    bp = mb.paint_box(cv, "hood_peak")
    for k, f in bp.faces.items():
        hood_cloth(f, 80 + len(k), folds=False)

    shapes = {
        "mask_circle": [".###.", "#...#", "#...#", "#...#", ".###."],
        "mask_triangle": ["..#..", ".#.#.", ".#.#.", "#...#", "#####"],
        "mask_square": ["#####", "#...#", "#...#", "#...#", "#####"],
    }
    for name, rows in shapes.items():
        bp = mb.paint_box(cv, name)
        for k, f in bp.faces.items():
            f.fill(BLK[0])
        f = bp.front
        PLATE = hexc("34343c")
        f.fill(PLATE)
        for j in range(7):
            for i in range(7):
                if i in (0, 6) or j in (0, 6):
                    f.set(i, j, hexc("1a1a1f"))
        f.hline(0, 1, 5, hexc("5a5a66")); f.vline(0, 1, 5, hexc("46464f"))        # bevel highlight
        f.hline(6, 1, 5, hexc("101014")); f.vline(6, 1, 5, hexc("101014"))
        f.set(1, 1, hexc("4a4a55")); f.set(2, 1, hexc("3e3e47"))
        f.stamp(1, 1, rows, {"#": WHITE})
        # tiny gloss
        f.set(1, 1, f.get(1, 1)) if False else None
        for side in (bp.left, bp.right):
            side.fill(BLK[0])
        bp.top.fill(BLK[2]); bp.bottom.fill(BLK[0])


def paint_limbs(mb: ModelBuilder, cv: Canvas) -> None:
    for n, left in (("left", True), ("right", False)):
        bp = mb.paint_box(cv, f"{n}_sleeve_upper")
        for k, f in bp.faces.items():
            fabric(f, PINK, seed=91 + len(k), noise=0.25, top_light=0.3, bottom_dark=0.3, base_index=2.0)
            if k in ("north", "south", "east", "west"):
                f.hline(1, 0, f.w - 1, PINK[0])                       # shoulder seam
                f.edge_ao(0.9, "lr")
        outer = bp.left if left else bp.right
        outer.vline(1 if left else 2, 2, 6, PINK[1])                  # sleeve seam
        bp.top.fill(PINK[3]); bp.bottom.fill(PINK[0])
        bp = mb.paint_box(cv, f"{n}_sleeve_lower")
        for k, f in bp.faces.items():
            fabric(f, PINK, seed=101 + len(k), noise=0.25, top_light=0.3, bottom_dark=0.3, base_index=2.0)
            if k in ("north", "south", "east", "west"):
                f.edge_ao(0.9, "lr")
                for j in range(f.h - 2, f.h):                         # cuff
                    for i in range(f.w):
                        f.set(i, j, PINK[1] if i % 2 == 0 else PINK[0])
        bp.back.rect(0, 2, 4, 3, PINK[1]); bp.back.rect(1, 3, 2, 1, PINK[2])   # elbow patch
        bp.top.fill(PINK[2]); bp.bottom.fill(PINK[0])
        bp = mb.paint_box(cv, f"{n}_glove")
        for k, f in bp.faces.items():
            fabric(f, BLK, seed=111 + len(k), noise=0.3, top_light=0.4, bottom_dark=0.3, base_index=1.3)
        bp.front.hline(0, 0, 3, BLK[0]); bp.front.vline(1, 1, 3, BLK[0]); bp.front.vline(2, 1, 3, BLK[0])
        bp.front.set(0, 1, BLK[4]); bp.front.set(3, 1, BLK[4])
        # legs
        bp = mb.paint_box(cv, f"{n}_thigh")
        for k, f in bp.faces.items():
            fabric(f, PINK, seed=121 + len(k), noise=0.25, top_light=0.3, bottom_dark=0.3, base_index=1.9)
            if k in ("north", "south", "east", "west"):
                f.edge_ao(0.9, "lr")
        outer = bp.left if left else bp.right
        outer.rect(0, 2, 3, 3, PINK[1]); outer.hline(2, 0, 2, PINK[0]); outer.set(1, 3, PINK[2])   # cargo pocket
        bp.front.vline(1, 0, 6, PINK[3]) if False else None
        bp.top.fill(PINK[1]); bp.bottom.fill(PINK[0])
        bp = mb.paint_box(cv, f"{n}_shin_c")
        for k, f in bp.faces.items():
            fabric(f, PINK, seed=131 + len(k), noise=0.25, top_light=0.25, bottom_dark=0.35, base_index=1.8)
            if k in ("north", "south", "east", "west"):
                f.edge_ao(0.9, "lr")
        bp.front.rect(0, 2, 4, 2, PINK[1]); bp.front.hline(2, 0, 3, PINK[0])                         # knee pad
        bp.top.fill(PINK[1]); bp.bottom.fill(PINK[0])
        bp = mb.paint_box(cv, f"{n}_boot_c")
        for k, f in bp.faces.items():
            fabric(f, BLK, seed=141 + len(k), noise=0.3, top_light=0.4, bottom_dark=0.2, base_index=1.3)
        outer = bp.left if left else bp.right
        inner = bp.right if left else bp.left
        for side in (outer, inner):
            side.hline(side.h - 1, 0, side.w - 1, SOLE)                      # sole
            side.hline(side.h - 2, 0, side.w - 1, BLK[0])
            front_cols = (0, 1) if side is bp.left else (4, 5)
            for i in front_cols:
                side.set(i, 1, BLK[3])                                       # toe cap sheen
            for i in (2, 3):
                side.set(i, 0, BLK[3] if (i % 2) else BLK[0])
        bp.front.hline(bp.front.h - 1, 0, 3, SOLE); bp.front.hline(bp.front.h - 2, 0, 3, BLK[0])
        bp.back.hline(bp.back.h - 1, 0, 3, SOLE)
        t = bp.top
        t.hline(0, 0, 3, BLK[0])
        for j in (2, 4):
            t.set(0, j, SILVER_D); t.set(3, j, SILVER_D)                     # eyelets
        bt = bp.bottom
        for j in range(bt.h):
            for i in range(bt.w):
                bt.set(i, j, SOLE if (i + j) % 2 == 0 else SOLE_L)


def paint_rifle(mb: ModelBuilder, cv: Canvas) -> None:
    def metal(name, ramp_, seed, edge=0.82):
        bp = mb.paint_box(cv, name)
        for k, f in bp.faces.items():
            fabric(f, ramp_, seed=seed + len(k), noise=0.22, top_light=0.5, bottom_dark=0.3, base_index=1.2)
            f.edge_ao(edge)
        return bp
    metal("r_grip", BLK, 151)
    bp = metal("r_receiver", GUN, 152)
    bp.right.hline(1, 0, bp.right.w - 1, GUN[0]); bp.left.hline(1, 0, bp.left.w - 1, GUN[0])
    bp.right.rect(1, 0, 2, 1, GUN[3]); bp.left.rect(bp.left.w - 3, 0, 2, 1, GUN[3])        # ejection port
    metal("r_stock", BLK, 153)
    metal("r_buttpad", BLK, 154, 0.7)
    bp = metal("r_guard", BLK, 155)
    for f in (bp.left, bp.right):
        for i in range(1, f.w, 2):
            f.set(i, 0, BLK[3]); f.set(i, 1, BLK[0])                                          # vent slots
    metal("r_barrel", GUN, 156, 0.9)
    metal("r_muzzle", GUN, 157, 0.8)
    metal("r_rail", GUN, 158, 0.9)
    metal("r_fsight", GUN, 159, 0.9)
    bp = metal("r_mag", BLK, 160)
    for f in (bp.left, bp.right):
        for j in range(1, f.h, 2):
            f.hline(j, 0, f.w - 1, BLK[0])                                                    # ribs


def paint_guard(mb: ModelBuilder) -> Canvas:
    cv = Canvas(mb.geo.tex_w, mb.geo.tex_h)
    paint_torso(mb, cv)
    paint_head(mb, cv)
    paint_limbs(mb, cv)
    paint_rifle(mb, cv)
    return cv
