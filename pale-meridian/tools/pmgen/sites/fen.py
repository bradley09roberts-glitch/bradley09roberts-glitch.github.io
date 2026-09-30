"""The drowned chapel in the western Fen (optional content: the founding vow, a sluice puzzle, keepsake 10)."""
from __future__ import annotations

import random

from .. import poi
from ..buildkit import gable_roof
from ..houses import sign
from ..structure import Build, container_nbt, item_stack_nbt
from . import Piece
from .glassworks import WB

SITE = "fen"
ORIGIN = (-360, 54, 56)
SIZE = (36, 26, 30)          # x -360..-325, y 54..79, z 56..85
Y = 64                       # first air on the chapel plateau


def build() -> list[Piece]:
    rng = random.Random(0xFE11)
    b = Build(*SIZE)
    w = WB(b, ORIGIN)

    def wsign(x, y, z, facing, lines, wood="mangrove"):
        sign(b, x - ORIGIN[0], y - ORIGIN[1], z - ORIGIN[2], facing, lines, wood)

    x0, x1, z0, z1 = -348, -334, 65, 75
    # the crypt below the nave (flooded until the sluices are set)
    cx0, cx1, cz0, cz1 = -346, -337, 67, 73
    for x in range(cx0 - 1, cx1 + 2):
        for z in range(cz0 - 1, cz1 + 2):
            for y in range(Y - 7, Y - 1):
                edge = x in (cx0 - 1, cx1 + 1) or z in (cz0 - 1, cz1 + 1) or y == Y - 7
                w.set(x, y, z, "mossy_stone_bricks" if edge and rng.random() < 0.4 else "stone_bricks" if edge else "water[level=0]")
    crypt_water = [cx0, Y - 6, cz0, cx1, Y - 2, cz1]
    # nothing waterloggable in the crypt: after draining, waterlogged blocks would keep spilling water
    chest = (cx0 + 1, Y - 6, 70)
    w.set(*chest, "barrel[facing=east,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:paper", 1, name={"text": "Hymn sheet", "italic": False},
                       lore=["Water-stained. The alto line is pencilled", "in by hand. Signed: M. OSTRANDER."],
                       custom={"pm": {"keepsake": 10}}, slot=11),
        item_stack_nbt("minecraft:wooden_shovel", 1, name={"text": "Ferryman's Oar", "italic": False, "color": "gold"},
                       lore=["Too short to row with. Carried at every", "Tarn funeral for two hundred years."],
                       extra_components={"minecraft:enchantments": {"minecraft:efficiency": 2}}, slot=15)]))
    poi.add("fen.keepsake.mae", *chest)
    w.set(cx0 + 1, Y - 6, 68, "chiseled_stone_bricks"); w.set(cx0 + 1, Y - 6, 72, "chiseled_stone_bricks")
    # a ladder shaft down from the east end of the nave; a thick glass window looks into the flooded crypt
    sx, sz = cx1 + 2, 67
    for y in range(Y - 7, Y):
        for (ax, az) in ((sx + 1, sz), (sx, sz - 1), (sx, sz + 1)):
            w.set(ax, y, az, "stone_bricks")
        w.set(sx, y, sz, "ladder[facing=west,waterlogged=false]" if y >= Y - 6 else "stone_bricks")
    grate = []
    for y in range(Y - 6, Y - 3):
        w.set(cx1 + 1, y, sz, "glass")
        grate.append([cx1 + 1, y, sz])
    poi.add("fen.crypt", (cx0 + cx1) / 2, Y - 6, 70, water=crypt_water, grate=grate)
    poi.box("fen.crypt_area", cx0, Y - 7, cz0, sx, Y - 2, cz1)

    # the nave: half-sunk, roof partly fallen
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            if not (cx0 - 1 <= x <= cx1 + 1 and cz0 - 1 <= z <= cz1 + 1):
                w.set(x, Y - 1, z, "water[level=0]" if x < -342 and not edge and rng.random() < 0.7 else "mossy_cobblestone")
            else:
                w.set(x, Y - 1, z, "mossy_stone_bricks")
            for y in range(Y, Y + 6):
                if edge:
                    broken = y >= Y + 4 and rng.random() < 0.35
                    if not broken:
                        w.set(x, y, z, rng.choice(["mossy_stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_cobblestone"]))
                else:
                    w.set(x, y, z, "air")
    w.set(sx, Y - 1, sz, "ladder[facing=west,waterlogged=false]")     # re-open the shaft through the nave floor
    for z in (z0, z1):
        for x in (-345, -341, -337):
            w.set(x, Y + 2, z, "glass_pane" if rng.random() < 0.5 else "air")
    # doorway (east)
    for z in (69, 70, 71):
        for y in (Y, Y + 1, Y + 2):
            w.set(x1, y, z, "air")
    gable_roof(b, x0 - ORIGIN[0], z0 - ORIGIN[2], x1 - ORIGIN[0], z1 - ORIGIN[2], Y + 6 - ORIGIN[1], "mangrove", ridge_axis="x", fill_gable="mossy_stone_bricks")
    for _ in range(18):                    # holes in the roof
        x, z = rng.randint(x0, x0 + 8), rng.randint(z0, z1)
        for y in range(Y + 6, Y + 12):
            if w.get(x, y, z) is not None and "mangrove" in w.get(x, y, z).name:
                w.set(x, y, z, "air")
    # the altar and the vow
    w.set(x0 + 1, Y, 70, "chiseled_stone_bricks")
    w.set(x0 + 2, Y, 70, "lectern[facing=east,has_book=false,powered=false]")
    poi.add("fen.vow", x0 + 3.0, Y, 70.5)
    wsign(x0 + 1, Y + 2, 72, "east", ["Open the north", "to let the fen", "breathe. Shut the", "middle ..."])
    wsign(x0 + 1, Y + 2, 68, "east", ["... against the", "lake. Open the", "south to send", "it home."])
    # three sluice levers on the south wall, labelled
    levers = {}
    for name, x in (("north", -343), ("middle", -341), ("south", -339)):
        w.set(x, Y + 1, z0 + 1, "lever[face=wall,facing=south,powered=false]")
        wsign(x, Y + 2, z0 + 1, "south", ["", name.upper(), "SLUICE", ""])
        levers[name] = [x, Y + 1, z0 + 1]
    poi.add("fen.levers", -341, Y + 1, z0 + 1, **levers)
    poi.box("fen.chapel", x0 - 4, Y - 8, z0 - 4, x1 + 6, Y + 10, z1 + 4)
    # pews
    for x in range(-344, -336, 2):
        for z in (67, 68, 72, 73):
            if w.get(x, Y, z) is not None and w.get(x, Y, z).name == "minecraft:air":
                w.set(x, Y, z, "mangrove_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
    wsign(x1 + 1, Y + 2, 68, "east", ["", "CHAPEL OF", "THE VOW", ""])
    return [Piece("chapel", b, ORIGIN)]
