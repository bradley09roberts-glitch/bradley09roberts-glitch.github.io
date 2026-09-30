"""The Deepcut — the stillglass mine inside the north cliffs (Chapter 3 and the finale's consequence).

Everything here is underground inside the 'deepcut_mine' quiet zone (layout.json), where natural caves
are suppressed, so the authored tunnels are the only open space. Built in world coordinates.
"""
from __future__ import annotations

import math
import random

from .. import poi
from ..houses import sign
from ..structure import Build, container_nbt, item_stack_nbt
from . import Piece
from .glassworks import WB

SITE = "deepcut"
ORIGIN = (-30, 44, -448)
SIZE = (112, 50, 127)      # x -30..81, y 44..93, z -448..-322
Y = 70                     # adit floor (first air block)


def _l(x, y, z):
    return x - ORIGIN[0], y - ORIGIN[1], z - ORIGIN[2]


def build() -> list[Piece]:
    rng = random.Random(0xDEE9C7)
    b = Build(*SIZE)
    w = WB(b, ORIGIN)

    def wsign(x, y, z, facing, lines, wood="spruce"):
        sign(b, *_l(x, y, z), facing, lines, wood)

    # ------------------------------------------------------------ Adit A (mouth at z=-322, north to the collapse)
    z_top, z_end = -322, -392
    bulbs = []
    for z in range(z_end, z_top + 1):
        for x in range(22, 27):
            w.set(x, Y - 1, z, rng.choice(["cobblestone", "stone", "gravel", "cobbled_deepslate"]) if x in (23, 24, 25) else "stone")
        w.air(23, Y, z, 25, Y + 3, z)
        w.set(24, Y, z, "rail[shape=north_south,waterlogged=false]")
        if z % 4 == 0:
            post = "pale_oak_log[axis=y]" if rng.random() < 0.25 else "stripped_spruce_log[axis=y]"
            for y in range(Y, Y + 4):
                w.set(22, y, z, post)
                w.set(26, y, z, post)
            for x in range(22, 27):
                w.set(x, Y + 4, z, "stripped_spruce_log[axis=x]")
        if z % 8 == 0:
            w.set(24, Y + 3, z, "waxed_copper_bulb[lit=false,powered=false]")
            bulbs.append([24, Y + 3, z])
        if rng.random() < 0.08:
            w.set(rng.choice([23, 25]), Y, z, "cobweb")
    # heart posts (Watchers roam the adit; hearts need pale oak logs above and below)
    hearts = []
    for (hx, hz) in ((22, -352), (26, -376)):
        w.set(hx, Y, hz, "pale_oak_log[axis=y]")
        w.set(hx, Y + 1, hz, "creaking_heart[axis=y,creaking_heart_state=dormant,natural=true]")
        w.set(hx, Y + 2, hz, "pale_oak_log[axis=y]")
        w.set(hx, Y + 3, hz, "pale_oak_log[axis=y]")
        hearts.append([hx, Y + 1, hz])
        for dz in (-2, -1, 1, 2):
            if w.get(24, Y + 3, hz + dz) is not None and w.get(24, Y + 3, hz + dz).name == "minecraft:air":
                w.set(24, Y + 3, hz + dz, "pale_hanging_moss[tip=true]")
            w.set(23 if hx == 22 else 25, Y, hz + dz, "pale_moss_carpet[bottom=true,east=none,north=none,south=none,west=none]")
    poi.add("deepcut.hearts", 24, Y, -364, hearts=hearts)
    # supply barrels along the way
    for z in (-334, -358, -382):
        w.set(25, Y, z + 1, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [], "palemeridian:chests/mine"))
    poi.box("deepcut.inside", 21, Y - 2, -340, 27, Y + 6, -322)

    # ------------------------------------------------------------ the blue seam (the old safe limit)
    for z in range(-392, -383):
        for y in range(Y - 1, Y + 5):
            for x in (22, 26):
                if w.get(x, y, z) is None or w.get(x, y, z).name not in ("minecraft:stripped_spruce_log", "minecraft:pale_oak_log"):
                    w.set(x, y, z, rng.choice(["light_blue_terracotta", "calcite", "light_blue_stained_glass", "light_blue_terracotta", "tuff"]))
        for x in range(23, 26):
            if w.get(x, Y + 4, z) is None or w.get(x, Y + 4, z).name != "minecraft:stripped_spruce_log":
                w.set(x, Y + 4, z, rng.choice(["light_blue_terracotta", "calcite", "light_blue_stained_glass"]))
    wsign(23, Y + 2, -384, "east", ["BLUE SEAM", "SAFE LIMIT", "No work beyond", "this mark"])
    wsign(25, Y + 2, -389, "west", ["KEEPER'S ORDERS", "CONTINUE", "", "- T.V."])
    poi.box("deepcut.seam", 21, Y - 1, -392, 27, Y + 5, -383)

    # ------------------------------------------------------------ the collapse (rubble to dig through)
    for z in range(-398, -392):
        for x in range(22, 27):
            for y in range(Y - 1, Y + 5):
                if x in (22, 26) or y in (Y - 1, Y + 4):
                    w.set(x, y, z, rng.choice(["cobbled_deepslate", "tuff", "deepslate"]))
                else:
                    r = rng.random()
                    w.set(x, y, z, "gravel" if y == Y and r < 0.4 else
                          "stripped_spruce_log[axis=z]" if r < 0.12 else
                          rng.choice(["cobbled_deepslate", "tuff", "cobblestone", "andesite"]))
    poi.box("deepcut.collapse", 21, Y - 1, -399, 27, Y + 5, -392)

    # ------------------------------------------------------------ the Last Gallery (memorial chamber)
    gx0, gx1, gz0, gz1 = 12, 36, -423, -399
    for x in range(gx0 - 1, gx1 + 2):
        for z in range(gz0 - 1, gz1 + 1):
            w.set(x, Y - 1, z, "polished_deepslate" if (x + z) % 2 else "cobbled_deepslate")
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            vault = 6 + int(2 * math.sin(math.pi * (x - gx0) / (gx1 - gx0)))
            for y in range(Y, Y + vault + 1):
                w.set(x, y, z, "air")
            w.set(x, Y + vault + 1, z, "deepslate_bricks")
    for x in range(gx0 - 1, gx1 + 2):
        for y in range(Y - 1, Y + 10):
            w.set(x, y, gz0 - 1, "polished_deepslate")
    for z in range(gz0, gz1 + 1, 4):
        for x in (gx0, gx1):
            for y in range(Y, Y + 6):
                w.set(x, y, z, "stripped_spruce_log[axis=y]")
    plaques, candles = [], []
    for i, x in enumerate(range(14, 35, 2)):
        wsign(x, Y + 2, gz0, "south", ["", "", "", ""], "dark_oak")
        plaques.append([x, Y + 2, gz0])
        w.set(x, Y, gz0, "candle[candles=1,lit=false,waterlogged=false]")
        candles.append([x, Y, gz0])
    poi.add("deepcut.memorial_wall", 24, Y + 2, gz0, plaques=plaques, candles=candles)
    figs = [(16, -412), (20, -412), (24, -412), (28, -412), (32, -412), (14, -416), (18, -416), (22, -416), (26, -416), (30, -416), (34, -416)]
    order = [1, 3, 11, 7, 2, 9, 5, 4, 6, 8, 10]       # Tobin in the middle of the front row
    for n, (x, z) in zip(order, figs):
        poi.add(f"deepcut.fig.{n}", x + 0.5, Y, z + 0.5, yaw=180.0)
    poi.box("deepcut.memorial", gx0, Y - 1, gz0, gx1, Y + 8, gz1)
    # the entrance from the collapse into the chamber
    w.air(23, Y, -399, 25, Y + 3, -399)

    # ------------------------------------------------------------ side tunnel east to the Assay Room
    for x in range(26, 36):
        for z in (-339, -338, -337):
            w.set(x, Y - 1, z, "cobblestone")
            for y in range(Y, Y + 4):
                w.set(x, y, z, "air")
    for x in (28, 32):
        for z in (-340, -336):
            for y in range(Y, Y + 4):
                w.set(x, y, z, "stripped_spruce_log[axis=y]")
        for z in range(-340, -335):
            w.set(x, Y + 4, z, "stripped_spruce_log[axis=z]")
    ax0, ax1, az0, az1 = 36, 44, -344, -332
    for x in range(ax0, ax1 + 1):
        for z in range(az0, az1 + 1):
            w.set(x, Y - 1, z, "spruce_planks")
            for y in range(Y, Y + 5):
                w.set(x, y, z, "air")
            w.set(x, Y + 5, z, "stripped_spruce_log[axis=x]" if z % 3 == 0 else "spruce_planks")
    w.set(43, Y, -333, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [], "palemeridian:chests/mine"))
    w.set(43, Y, -334, "fletching_table")
    w.set(40, Y, -333, "spruce_fence"); w.set(40, Y + 1, -333, "spruce_pressure_plate[powered=false]")
    w.set(38, Y, -333, "candle[candles=3,lit=false,waterlogged=false]")
    wsign(44, Y + 2, -338, "west", ["ASSAY", "Keep your lamp", "lit. Keep your", "eyes open."])
    # stair up (north) to the upper gallery
    for k in range(12):
        z = -345 - k
        for x in (38, 39, 40):
            for y in range(Y - 1 + k, Y + 5 + k):
                w.set(x, y, z, "air")
            w.set(x, Y - 1 + k, z, "cobblestone")
            w.set(x, Y + k, z, "stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
    # ------------------------------------------------------------ the upper gallery (Tamsin)
    UY = Y + 12      # 82
    ux0, ux1, uz0, uz1 = 34, 62, -366, -357
    for x in range(ux0, ux1 + 1):
        for z in range(uz0, uz1 + 1):
            w.set(x, UY - 1, z, "spruce_planks" if (x // 3) % 2 else "stripped_spruce_log[axis=x]")
            for y in range(UY, UY + 4):
                w.set(x, y, z, "air")
    for x in (35, 41, 53, 59):
        for z in (uz0, uz1):
            for y in range(UY, UY + 4):
                w.set(x, y, z, "stripped_spruce_log[axis=y]")
    for x in (38, 39, 40):
        for y in range(UY, UY + 4):
            w.set(x, y, uz1 + 1, "air")
    # Tamsin's camp: bedroll, dead lamp, crate with a lamplighter's hook
    w.set(57, UY, -365, "white_carpet"); w.set(58, UY, -365, "white_carpet")
    w.set(59, UY, -364, "spruce_fence"); w.set(59, UY + 1, -364, "waxed_copper_bulb[lit=false,powered=false]")
    w.set(60, UY, -365, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:paper", 2, slot=0),
        item_stack_nbt("minecraft:stick", 1, name={"text": "Lamp hook", "italic": False},
                       lore=["A lamplighter's hook, the brass worn bright.", "Burned into the handle: P. LUND."],
                       custom={"pm": {"keepsake": 11}}, slot=13)]))
    poi.add("deepcut.keepsake.piet", 60, UY, -365)
    poi.add("deepcut.tamsin", 56.5, UY, -362.5, yaw=90.0)
    poi.box("deepcut.gallery", ux0, UY - 2, uz0, ux1, UY + 5, uz1 + 1)
    # ------------------------------------------------------------ the stillglass cavern
    ccx, ccy, ccz, rx, ry, rz = 46, 74, -382, 15, 11, 12
    for x in range(ccx - rx - 1, ccx + rx + 2):
        for y in range(ccy - ry - 1, ccy + ry + 2):
            for z in range(ccz - rz - 1, ccz + rz + 2):
                d = ((x - ccx) / rx) ** 2 + ((y - ccy) / ry) ** 2 + ((z - ccz) / rz) ** 2
                if d <= 1.0:
                    if y < ccy - ry * 0.55:
                        w.set(x, y, z, "stone" if rng.random() < 0.8 else "calcite")      # a level-ish floor
                    else:
                        w.set(x, y, z, "air")
                elif d <= 1.18 and y >= ccy - ry * 0.55 - 1:
                    r = rng.random()
                    w.set(x, y, z, "light_blue_stained_glass" if r < 0.12 else "calcite" if r < 0.3 else
                          "amethyst_block" if r < 0.36 else "smooth_basalt" if r < 0.4 else "stone")
    # stillglass column in the middle
    for y in range(int(ccy - ry * 0.55), ccy + 8):
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            w.set(ccx + dx, y, ccz + dz, "light_blue_stained_glass" if (y + dx) % 3 else "tinted_glass")
    # passage from Adit A into the cavern
    for x in range(26, 33):
        for z in (-381, -380, -379):
            w.set(x, Y - 1, z, "cobblestone")
            for y in range(Y, Y + 4):
                w.set(x, y, z, "air")
    # look-out: a crack from the gallery down into the stillglass cavern
    for z in range(-375, -366):
        for x in (45, 46, 47):
            for y in range(UY, UY + 3):
                w.set(x, y, z, "air")
            w.set(x, UY - 1, z, "cobblestone")
    for x in (45, 46, 47):
        w.set(x, UY, -372, "spruce_fence")

    poi.box("deepcut.cavern", ccx - rx, ccy - ry, ccz - rz, ccx + rx, ccy + ry, ccz + rz)

    poi.add("deepcut.lamps", 24, Y + 3, -360, bulbs=bulbs)
    return [Piece("mine", b, ORIGIN)]
