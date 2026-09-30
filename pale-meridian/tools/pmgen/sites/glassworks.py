"""The Vell Glassworks — kilns, workshop and the foreman's office under the north cliffs (Chapter 3).

Built in world coordinates through the WB wrapper (the template's origin is subtracted on write).
The mine itself (the Deepcut) is a separate site, see deepcut.py; this template only builds its mouth.
"""
from __future__ import annotations

import math
import random

from .. import poi
from ..buildkit import door, gable_roof, stairs
from ..houses import STYLES, cottage, sign, street_lamp
from ..structure import Build, container_nbt, item_stack_nbt
from . import Piece

SITE = "glassworks"
ORIGIN = (-12, 64, -332)
SIZE = (72, 52, 84)
Y = 70          # plateau floor (first air block)


class WB:
    """World-coordinate view of a Build."""

    def __init__(self, b: Build, origin: tuple):
        self.b, self.o = b, origin

    def _l(self, x, y, z):
        return x - self.o[0], y - self.o[1], z - self.o[2]

    def set(self, x, y, z, block, nbt_tag=None):
        self.b.set(*self._l(x, y, z), block, nbt_tag)

    def get(self, x, y, z):
        return self.b.get(*self._l(x, y, z))

    def inside(self, x, y, z):
        return self.b.inside(*self._l(x, y, z))

    def fill(self, x1, y1, z1, x2, y2, z2, block, nbt_tag=None):
        self.b.fill(*self._l(x1, y1, z1), *self._l(x2, y2, z2), block, nbt_tag)

    def air(self, x1, y1, z1, x2, y2, z2):
        self.b.air(*self._l(x1, y1, z1), *self._l(x2, y2, z2))

    def soft_air(self, x1, y1, z1, x2, y2, z2):
        """Air only where nothing was placed yet."""
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    if self.get(x, y, z) is None:
                        self.set(x, y, z, "air")


def lx(x):
    return x - ORIGIN[0]


def lz(z):
    return z - ORIGIN[2]


def build() -> list[Piece]:
    rng = random.Random(0x61A55)
    b = Build(*SIZE)
    w = WB(b, ORIGIN)
    FL = Y - ORIGIN[1]

    # ------------------------------------------------------------ the kiln yard (paved circle)
    cx, cz = 24, -296
    for x in range(cx - 14, cx + 15):
        for z in range(cz - 14, cz + 15):
            d = math.hypot(x - cx, z - cz)
            if d <= 13.5:
                w.set(x, Y - 1, z, rng.choice(["cobblestone", "stone", "gravel", "cobblestone", "andesite", "tuff"]))
    # road into the yard from the east gate
    for x in range(cx + 10, 58):
        for dz in (-1, 0, 1):
            w.set(x, Y - 1, -284 + dz + (0 if x < 44 else 0), rng.choice(["dirt_path", "gravel", "coarse_dirt"]))
    for z in range(-296, -283):
        for dx in (-1, 0, 1):
            w.set(cx + 10 + dx, Y - 1, z, rng.choice(["gravel", "cobblestone"]))
    w.soft_air(cx - 13, Y, cz - 13, cx + 13, Y + 4, cz + 13)
    # candle pedestals (the surge relights these)
    candles = []
    for (x, z) in ((16, -304), (32, -304), (16, -288), (32, -288)):
        w.set(x, Y, z, "polished_andesite")
        w.set(x, Y + 1, z, "candle[candles=4,lit=false,waterlogged=false]")
        candles.append([x, Y + 1, z])
    poi.add("glassworks.yard", cx + 0.5, Y, cz + 0.5, candles=candles)
    # the kiln yard crate (Hob Tulley's dice)
    w.set(18, Y, -309, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:coal", 3, slot=0),
        item_stack_nbt("minecraft:bone", 2, name={"text": "Bone dice", "italic": False},
                       lore=["A pair of dice, worn round at the corners.", "They always seem to land on six. H.T."],
                       custom={"pm": {"keepsake": 9}}, slot=13)]))
    poi.add("glassworks.keepsake.hob", 18, Y, -309)
    for (x, z) in ((19, -309), (18, -310)):
        w.set(x, Y, z, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [], "palemeridian:chests/works"))

    # ------------------------------------------------------------ Kiln Three (beehive kiln)
    kx, kz, kr = 5, -298, 6
    for y in range(Y - 1, Y + 9):
        dy = y - (Y - 1)
        rr = kr if dy <= 4 else kr - (dy - 4)
        for x in range(kx - kr - 1, kx + kr + 2):
            for z in range(kz - kr - 1, kz + kr + 2):
                d = math.hypot(x - kx, z - kz)
                if y == Y - 1:
                    if d <= kr + 0.5:
                        w.set(x, y, z, "bricks")
                elif d <= rr + 0.5:
                    if d >= rr - 0.7 or dy >= 8:
                        w.set(x, y, z, "bricks" if rng.random() < 0.85 else "mud_bricks")
                    else:
                        w.set(x, y, z, "air")
    # hearth inside: magma floor and campfires (lit when the kiln fires)
    fires = []
    for (x, z) in ((kx, kz), (kx - 2, kz), (kx + 2, kz), (kx, kz - 2), (kx, kz + 2)):
        w.set(x, Y - 1, z, "magma_block")
        w.set(x, Y, z, "campfire[facing=north,lit=false,signal_fire=false,waterlogged=false]")
        fires.append([x, Y, z])
    # firebox mouth on the east side (barrel) with a grate above to see the fire
    fb = (kx + kr + 1, Y, kz)
    w.set(fb[0] - 1, Y, kz, "air")
    w.set(*fb, "barrel[facing=east,open=false]", container_nbt("minecraft:barrel", []))
    w.set(fb[0] - 1, Y + 1, kz, "iron_bars")
    w.set(fb[0], Y + 1, kz, "bricks")
    w.set(fb[0], Y + 2, kz, "brick_slab[type=bottom]")
    sign(w.b, lx(fb[0] + 1), Y + 1 - ORIGIN[1], lz(kz), "east", ["KILN THREE", "firebox", "coal or", "charcoal"], "spruce")
    # the kiln hatch (forged pieces appear here)
    hatch = (kx + kr + 1, Y, kz + 3)
    w.set(*hatch, "chest[facing=east,type=single,waterlogged=false]", container_nbt("minecraft:chest", []))
    sign(w.b, lx(hatch[0] + 1), Y + 1 - ORIGIN[1], lz(hatch[2]), "east", ["", "KILN HATCH", "", ""], "spruce")
    # control panel with three levers (UP = unpowered on a wall lever)
    px = kx + kr + 3
    w.fill(px, Y - 1, -305, px, Y + 3, -302, "bricks")
    w.set(px, Y + 4, -305, "brick_slab[type=bottom]"); w.set(px, Y + 4, -302, "brick_slab[type=bottom]")
    w.fill(px, Y + 4, -304, px, Y + 4, -303, "brick_slab[type=bottom]")
    levers = {}
    # start: bellows already right (up), flue and damper wrong, so flipping everything does not solve it
    for name, z, initial in (("bellows", -304, "false"), ("flue", -303, "false"), ("damper", -302, "true")):
        w.set(px + 1, Y + 1, z, f"lever[face=wall,facing=east,powered={initial}]")
        levers[name] = [px + 1, Y + 1, z]
    sign(w.b, lx(px + 1), Y + 2 - ORIGIN[1], lz(-304), "east", ["BELLOWS", "", "", ""], "spruce")
    sign(w.b, lx(px + 1), Y + 2 - ORIGIN[1], lz(-303), "east", ["FLUE", "", "", ""], "spruce")
    sign(w.b, lx(px + 1), Y + 2 - ORIGIN[1], lz(-302), "east", ["DAMPER", "", "", ""], "spruce")
    poi.add("glassworks.kiln", kx + 0.5, Y, kz + 0.5, fires=fires)
    poi.add("glassworks.firebox", *fb)
    poi.add("glassworks.hatch", *hatch)
    poi.add("glassworks.levers", px + 1, Y + 1, -303, **levers)
    poi.box("glassworks.kiln_area", kx - 8, Y - 2, kz - 12, px + 8, Y + 8, kz + 8)

    # ------------------------------------------------------------ the kiln tower (chimney with the Wakelamp)
    tx, tz = 0, -308
    top = Y + 28
    for y in range(Y - 1, top):
        for x in range(tx - 2, tx + 3):
            for z in range(tz - 2, tz + 3):
                edge = x in (tx - 2, tx + 2) or z in (tz - 2, tz + 2)
                if y == Y - 1:
                    w.set(x, y, z, "bricks")
                elif edge:
                    w.set(x, y, z, "bricks" if (y + x + z) % 7 else "mud_bricks")
                else:
                    w.set(x, y, z, "air")
    # door (south), ladder in the north-west inner corner
    w.set(tx, Y, tz + 2, "air"); w.set(tx, Y + 1, tz + 2, "air")
    for y in range(Y, top + 1):
        w.set(tx - 1, y, tz - 1, "ladder[facing=south,waterlogged=false]")
    # platform with a parapet
    for x in range(tx - 3, tx + 4):
        for z in range(tz - 3, tz + 4):
            w.set(x, top, z, "stone_bricks")
            if x in (tx - 3, tx + 3) or z in (tz - 3, tz + 3):
                w.set(x, top + 1, z, "stone_brick_wall")
    w.set(tx - 1, top, tz - 1, "ladder[facing=south,waterlogged=false]")
    w.set(tx - 1, top, tz - 2, "bricks")
    ly = top + 1
    parts = [("pearlescent_froglight", (tx, ly, tz)),
             ("glass", (tx - 1, ly, tz)), ("glass", (tx + 1, ly, tz)), ("glass", (tx, ly, tz - 1)), ("glass", (tx, ly, tz + 1)),
             ("iron_bars", (tx, ly + 1, tz)), ("iron_bars", (tx, ly + 2, tz))]
    poi.add("glassworks.wakelamp", tx, ly, tz, parts=[{"pos": list(p), "block": blk} for blk, p in parts])
    poi.box("glassworks.tower_top", tx - 4, top - 1, tz - 4, tx + 4, top + 6, tz + 4)

    # ------------------------------------------------------------ the glass workshop (east)
    x0, z0, x1, z1 = 38, -313, 52, -301
    w.fill(x0, Y - 1, z0, x1, Y - 1, z1, "stone_bricks")
    for y in range(Y, Y + 5):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                corner = x in (x0, x1) and z in (z0, z1)
                if edge:
                    if corner or (x - x0) % 4 == 0 and z in (z0, z1) or (z - z0) % 4 == 0 and x in (x0, x1):
                        w.set(x, y, z, "stripped_spruce_log[axis=y]")
                    else:
                        w.set(x, y, z, "bricks" if y < Y + 2 else "glass_pane" if y == Y + 2 and (x + z) % 3 == 0 else "bricks")
                else:
                    w.set(x, y, z, "air")
    gable_roof(w.b, lx(x0), lz(z0), lx(x1), lz(z1), Y + 5 - ORIGIN[1], "deepslate_tile", ridge_axis="x", fill_gable="bricks")
    door(w.b, lx(x0), Y - ORIGIN[1], lz(-307), "west", "spruce")
    for i, x in enumerate(range(x0 + 2, x1 - 1, 3)):
        w.set(x, Y, z0 + 1, "furnace[facing=south,lit=false]")
        w.set(x + 1, Y, z0 + 1, "glass")
    w.set(x1 - 1, Y, z1 - 1, "crafting_table")
    w.set(x1 - 2, Y, z1 - 1, "anvil[facing=north]")
    w.set(x1 - 1, Y, z1 - 3, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [], "palemeridian:chests/works"))
    supply = (x0 + 2, Y, z1 - 1)
    w.set(*supply, "chest[facing=north,type=single,waterlogged=false]", container_nbt("minecraft:chest", [
        item_stack_nbt("minecraft:glass", 4, slot=11), item_stack_nbt("minecraft:sand", 8, slot=12),
        item_stack_nbt("minecraft:coal", 4, slot=13)]))
    poi.add("glassworks.supply", *supply)
    poi.box("glassworks.workshop", x0, Y - 1, z0, x1, Y + 6, z1)
    # sand and clay heaps behind the workshop
    for (hx, hz, blk) in ((44, -296, "sand"), (48, -295, "sand"), (46, -292, "clay"), (50, -297, "sand")):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                w.set(hx + dx, Y, hz + dz, blk)
        w.set(hx, Y + 1, hz, blk)

    # ------------------------------------------------------------ the foreman's office (by the mine mouth)
    o0, oz0, o1, oz1 = 30, -321, 38, -314
    w.fill(o0, Y - 1, oz0, o1, Y - 1, oz1, "spruce_planks")
    for y in range(Y, Y + 4):
        for x in range(o0, o1 + 1):
            for z in range(oz0, oz1 + 1):
                edge = x in (o0, o1) or z in (oz0, oz1)
                if edge:
                    corner = x in (o0, o1) and z in (oz0, oz1)
                    w.set(x, y, z, "stripped_dark_oak_log[axis=y]" if corner else ("stone_bricks" if y < Y + 2 else "mossy_stone_bricks" if (x + y) % 5 == 0 else "stone_bricks"))
                else:
                    w.set(x, y, z, "air")
    gable_roof(w.b, lx(o0), lz(oz0), lx(o1), lz(oz1), Y + 4 - ORIGIN[1], "dark_oak", ridge_axis="x", fill_gable="stone_bricks")
    door(w.b, lx(34), Y - ORIGIN[1], lz(oz1), "south", "dark_oak")
    w.set(o0, Y + 1, -317, "glass_pane"); w.set(o1, Y + 1, -317, "glass_pane")
    lect = (36, Y, -319)
    w.set(*lect, "lectern[facing=south,has_book=false,powered=false]")
    poi.add("glassworks.log", lect[0] + 0.5, Y, lect[2] + 1.3)
    desk = (32, Y, -320)
    w.set(*desk, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:paper", 3, slot=0),
        item_stack_nbt("minecraft:glass_pane", 1, name={"text": "Wire spectacles", "italic": False},
                       lore=["Thick lenses in a bent wire frame.", "Scratched on the arm: S. CRANE, FOREMAN."],
                       custom={"pm": {"keepsake": 7}}, slot=13)]))
    poi.add("glassworks.keepsake.silas", *desk)
    theo = (31, Y, -316)
    w.set(*theo, "chest[facing=east,type=single,waterlogged=false]", container_nbt("minecraft:chest", [
        item_stack_nbt("minecraft:spyglass", 1, name={"text": "Tamsin's Theodolite", "italic": False, "color": "gold"},
                       lore=["A surveyor's sighting instrument, brass and glass.", "Engraved: T. REED — CHARTERED SURVEY."],
                       custom={"pm": {"item": "theodolite"}}, slot=13)]))
    poi.add("glassworks.theodolite_chest", *theo)
    w.set(37, Y, -316, "bookshelf"); w.set(37, Y + 1, -316, "bookshelf")
    w.set(33, Y, -320, "spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
    w.set(33, Y + 3, -317, "lantern[hanging=true,waterlogged=false]") if False else None
    poi.box("glassworks.office", o0, Y - 1, oz0, o1, Y + 5, oz1)
    poi.add("glassworks.tamsin", 34.5, Y, -311.5, yaw=180.0)

    # ------------------------------------------------------------ the mine mouth (the adit continues in deepcut.py)
    mz = -320
    w.air(23, Y, mz - 1, 25, Y + 3, mz + 1)
    for z in (mz - 1, mz + 1):
        for y in range(Y, Y + 4):
            w.set(22, y, z, "stripped_spruce_log[axis=y]")
            w.set(26, y, z, "stripped_spruce_log[axis=y]")
        for x in range(22, 27):
            w.set(x, Y + 4, z, "stripped_spruce_log[axis=x]")
    for x in range(21, 28):
        for z in (mz - 1, mz, mz + 1):
            if w.get(x, Y + 5, z) is None:
                w.set(x, Y + 5, z, rng.choice(["cobblestone", "stone", "andesite"]))
    sign(w.b, lx(24), Y + 3 - ORIGIN[1], lz(mz + 2), "south", ["THE DEEPCUT", "No. 1 Adit", "", "KEEPER'S WORKS"], "spruce")
    for z in range(mz - 1, mz + 3):
        w.set(24, Y, z, "rail[shape=north_south,waterlogged=false]")
    poi.add("glassworks.mouth", 24.5, Y, mz + 0.5)

    # ------------------------------------------------------------ bunkhouse (west)
    cottage(w.b, lx(-6), lz(-286), 13, 8, FL, "north", STYLES["stone"], rng, role="home", storeys=1, ridge="x")

    # ------------------------------------------------------------ gate (east)
    for z in (-288, -280):
        for y in range(Y, Y + 4):
            w.set(46, y, z, "bricks")
        w.set(46, Y + 4, z, "brick_slab[type=bottom]")
    sign(w.b, lx(47), Y + 2 - ORIGIN[1], lz(-288), "east", ["", "VELL", "GLASSWORKS", ""], "spruce")
    poi.box("glassworks.gate_area", 36, Y - 4, -294, 58, Y + 10, -272)
    poi.add("glassworks.gate", 44.5, Y, -283.5)

    # ------------------------------------------------------------ lamps
    lamps = [street_lamp(w.b, lx(x), FL, lz(z), lit=False) for (x, z) in ((20, -283), (30, -283), (38, -296), (12, -288), (26, -312))]
    poi.add("glassworks.lamps", cx, Y, cz, lamps=[[p[0] + ORIGIN[0], p[1] + ORIGIN[1], p[2] + ORIGIN[2]] for p in lamps])

    from ..buildkit import autoconnect
    autoconnect(b)
    return [Piece("works", b, ORIGIN)]
