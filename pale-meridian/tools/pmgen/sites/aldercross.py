"""Aldercross — the orchard terraces, the windmill and the Heartwood (Chapter 2)."""
from __future__ import annotations

import math
import random

from .. import poi
from ..buildkit import autoconnect, door, stairs, window, gable_roof
from ..houses import STYLES, cottage, fence_line, ground_at, sign, street_lamp, tree
from ..structure import Build, container_nbt, item_stack_nbt
from . import Piece

SITE = "aldercross"
ORIGIN = (280, 72, -90)
SIZE = (100, 60, 100)
FL = 6        # world 78


def W(lx, ly, lz):
    return (ORIGIN[0] + lx, ORIGIN[1] + ly, ORIGIN[2] + lz)


def _ground(b: Build, x, z, block, clear=4):
    b.set(x, FL - 1, z, block)
    for y in range(FL, FL + clear):
        if b.get(x, y, z) is None:
            b.set(x, y, z, "air")


def fruit_tree(b: Build, x, z, rng, pale: bool) -> None:
    b.set(x, FL - 1, z, "grass_block[snowy=false]")
    h = rng.randint(3, 4)
    log = "pale_oak_log[axis=y]" if pale else "oak_log[axis=y]"
    leaves = "pale_oak_leaves[distance=1,persistent=true,waterlogged=false]" if pale else "oak_leaves[distance=1,persistent=true,waterlogged=false]"
    for i in range(h):
        b.set(x, FL + i, z, log)
    for dy in (h - 1, h, h + 1):
        r = 2 if dy < h + 1 else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r + (1 if dy == h else 0) and not (dx == 0 and dz == 0 and dy < h):
                    if b.get(x + dx, FL + dy, z + dz) is None and rng.random() < 0.92:
                        b.set(x + dx, FL + dy, z + dz, leaves)
    if pale and rng.random() < 0.6:
        b.set(x + 1, FL + h - 2, z, "pale_hanging_moss[tip=true]") if b.get(x + 1, FL + h - 2, z) is None else None


def build() -> list[Piece]:
    rng = random.Random(0xA1D3)
    b = Build(*SIZE)
    plain, warm, pale = STYLES["plain"], STYLES["warm"], STYLES["pale"]

    # ------------------------------------------------------------ lanes
    # entry from the west (road from Hollin arrives at world (318,-34) = local (38, 56))
    for x in range(20, 64):
        cz = 56 - (x - 20) * 0.1
        for dz in range(-1, 2):
            _ground(b, x, int(round(cz)) + dz, rng.choice(["dirt_path", "dirt_path", "coarse_dirt", "gravel"]))
    for z in range(20, 80):
        for dx in range(-1, 2):
            _ground(b, 50 + dx, z, rng.choice(["dirt_path", "dirt_path", "coarse_dirt"]))
    # orchard gate (west)
    for gz in (52, 60):
        b.fill(20, FL, gz, 20, FL + 3, gz, "stripped_oak_log[axis=y]")
    for z in range(52, 61):
        b.set(20, FL + 4, z, "oak_slab[type=bottom]")
    sign(b, 19, FL + 3, 56, "west", ["", "ALDERCROSS", "Orchards", "& Apiary"], "oak")
    poi.box("aldercross.gate_area", *W(8, FL - 1, 44), *W(30, FL + 6, 68))
    poi.add("aldercross.gate", *W(22, FL, 56))

    # ------------------------------------------------------------ orchard rows (fruit trees, overrun by pale oak)
    for row in range(4):
        for col in range(5):
            x = 58 + col * 7 + (row % 2) * 3
            z = 22 + row * 8
            if x < 96 and b.get(x, FL, z) is None:
                fruit_tree(b, x, z, rng, pale=rng.random() < 0.55)
    for row in range(3):
        for col in range(4):
            x = 26 + col * 6
            z = 72 + row * 8
            if z < 96:
                fruit_tree(b, x, z, rng, pale=rng.random() < 0.6)
    # terrace walls
    for x in range(54, 98):
        b.set(x, FL, 18, "mossy_cobblestone_wall" if rng.random() < 0.5 else "cobblestone_wall")
    for z in range(18, 54):
        b.set(54, FL, z, "mossy_cobblestone_wall" if rng.random() < 0.5 else "cobblestone_wall")

    # ------------------------------------------------------------ Brannoc's farmhouse (near the gate)
    cottage(b, 26, 38, 11, 9, FL, "south", warm, rng, role="home", storeys=1, ridge="x")
    poi.add("aldercross.brannoc", *W(31.5, FL, 49.5), yaw=0.0)
    poi.add("aldercross.brannoc_home", *W(31.5, FL, 49.5), yaw=0.0)

    # ------------------------------------------------------------ the Apiary (north-west)
    ax0, az0 = 24, 8
    fence_line(b, [(ax0, az0), (ax0 + 20, az0), (ax0 + 20, az0 + 18), (ax0, az0 + 18), (ax0, az0)], FL, "oak_fence")
    b.set(ax0 + 10, FL, az0 + 18, "oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
    hives = []
    for i, (hx, hz) in enumerate(((ax0 + 4, az0 + 4), (ax0 + 8, az0 + 4), (ax0 + 12, az0 + 4), (ax0 + 16, az0 + 4),
                                   (ax0 + 4, az0 + 11), (ax0 + 8, az0 + 11), (ax0 + 12, az0 + 11), (ax0 + 16, az0 + 11))):
        b.set(hx, FL, hz, "oak_fence")
        b.set(hx, FL + 1, hz, "beehive[facing=south,honey_level=0]", None)
        hives.append(list(W(hx, FL + 1, hz)))
    for x in range(ax0 + 1, ax0 + 20):
        for z in range(az0 + 14, az0 + 18):
            b.set(x, FL - 1, z, "grass_block[snowy=false]")
            b.set(x, FL, z, rng.choice(["closed_eyeblossom", "short_grass", "pale_moss_carpet[bottom=true,east=none,north=none,south=none,west=none]"]))
    # apiary shed with shelf (Ruth Anning's pressed flower)
    b.fill(ax0 + 1, FL, az0 + 1, ax0 + 3, FL + 2, az0 + 1, "oak_planks")
    b.set(ax0 + 2, FL, az0 + 2, "chest[facing=south,type=single,waterlogged=false]", container_nbt("minecraft:chest", [
        item_stack_nbt("minecraft:honeycomb", 8, slot=0),
        item_stack_nbt("minecraft:glass_bottle", 3, slot=1),
        item_stack_nbt("minecraft:paper", 1, name={"text": "Pressed flower", "italic": False},
                       lore=["A cornflower, flattened between two", "pages torn from a hymnal. For R.A."], custom={"pm": {"keepsake": 5}}, slot=13)]))
    poi.add("aldercross.apiary_chest", *W(ax0 + 2, FL, az0 + 2))
    poi.box("aldercross.apiary", *W(ax0, FL - 1, az0), *W(ax0 + 20, FL + 5, az0 + 18))
    poi.add("aldercross.hives", *W(ax0 + 10, FL + 1, az0 + 8), hives=hives)

    # ------------------------------------------------------------ the Cider Press (a barn; Tamsin's second camp in the loft)
    px0, pz0 = 60, 60
    cottage(b, px0, pz0, 13, 10, FL, "west", STYLES["plain"], rng, role="shop", storeys=2, ridge="x", furnish=False)
    for x in range(px0 + 2, px0 + 11, 2):
        b.set(x, FL, pz0 + 1, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [], "palemeridian:chests/cider"))
    b.set(px0 + 6, FL, pz0 + 5, "piston[extended=false,facing=down]")
    b.set(px0 + 6, FL - 1, pz0 + 5, "cauldron")
    b.set(px0 + 7, FL, pz0 + 5, "grindstone[face=floor,facing=north]")
    # loft camp
    b.set(px0 + 2, FL + 4, pz0 + 7, "white_carpet"); b.set(px0 + 3, FL + 4, pz0 + 7, "white_carpet")
    b.set(px0 + 5, FL + 4, pz0 + 7, "lantern[hanging=false]")
    for y in range(FL, FL + 4):
        b.set(px0 + 11, y, pz0 + 1, "ladder[facing=west,waterlogged=false]")
    b.set(px0 + 11, FL + 3, pz0 + 1, "ladder[facing=west,waterlogged=false]")
    poi.box("aldercross.press", *W(px0 + 1, FL, pz0 + 1), *W(px0 + 11, FL + 7, pz0 + 8))
    poi.add("aldercross.tamsin_note", *W(px0 + 4, FL + 4, pz0 + 7))

    # ------------------------------------------------------------ the Brothers' Tree (old oak by the wall)
    tx, tz = 40, 86
    for y in range(FL, FL + 7):
        for (dx, dz) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            b.set(tx + dx, y, tz + dz, "oak_wood[axis=y]")
    b.set(tx, FL + 1, tz, "air")    # the hollow
    b.set(tx, FL, tz, "chest[facing=south,type=single,waterlogged=false]", container_nbt("minecraft:chest", [
        item_stack_nbt("minecraft:apple", 1, name={"text": "Carved apple token", "italic": False},
                       lore=["Wood, carved into an apple. On the base:", "C.H. — for luck underground."], custom={"pm": {"keepsake": 2}},
                       extra_components={"!minecraft:consumable": {}}, slot=13)]))
    for dy in range(5, 10):
        r = 4 if dy < 8 else 3
        for dx in range(-r, r + 2):
            for dz in range(-r, r + 2):
                if (dx - 0.5) ** 2 + (dz - 0.5) ** 2 <= r * r and b.get(tx + dx, FL + dy, tz + dz) is None and rng.random() < 0.9:
                    b.set(tx + dx, FL + dy, tz + dz, "oak_leaves[distance=1,persistent=true,waterlogged=false]")
    sign(b, tx, FL + 2, tz + 2, "south", ["", "B + C", "", ""], "oak")
    poi.box("aldercross.tree", *W(tx - 5, FL - 1, tz - 5), *W(tx + 6, FL + 8, tz + 6))
    poi.add("aldercross.keepsake.col", *W(tx, FL, tz))

    # ------------------------------------------------------------ the Windmill (Wakelamp in the cap)
    mx, mz = 76, 44      # centre
    r = 4
    for y in range(FL, FL + 16):
        rr = r - (1 if y > FL + 10 else 0)
        for x in range(mx - r - 1, mx + r + 2):
            for z in range(mz - r - 1, mz + r + 2):
                d = math.hypot(x - mx, z - mz)
                if d <= rr + 0.5:
                    if d >= rr - 0.6:
                        b.set(x, y, z, "stone_bricks" if y < FL + 4 else ("stripped_spruce_log[axis=y]" if (x + z) % 3 == 0 else "spruce_planks"))
                    else:
                        b.set(x, y, z, "air")
    b.fill(mx - r, FL - 1, mz - r, mx + r, FL - 1, mz + r, "spruce_planks")
    # spiral of ladders up the inside west wall
    for y in range(FL, FL + 14):
        b.set(mx - r + 1, y, mz, "ladder[facing=east,waterlogged=false]")
    b.fill(mx - r + 1, FL + 14, mz - r + 1, mx + r - 1, FL + 14, mz + r - 1, "spruce_planks")   # lamp chamber floor
    b.set(mx - r + 1, FL + 14, mz, "ladder[facing=east,waterlogged=false]")
    door(b, mx - r, FL, mz, "east", "spruce")
    for y in (FL + 4, FL + 9):
        b.set(mx + r, y, mz, "glass_pane"); b.set(mx, y, mz - r, "glass_pane"); b.set(mx, y, mz + r, "glass_pane")
    # cap (lamp chamber) with windows
    for y in range(FL + 15, FL + 19):
        for x in range(mx - 3, mx + 4):
            for z in range(mz - 3, mz + 4):
                d = math.hypot(x - mx, z - mz)
                if 2.5 <= d <= 3.5:
                    b.set(x, y, z, "glass" if y in (FL + 16, FL + 17) and (x == mx or z == mz) else "spruce_planks")
                elif d < 2.5:
                    b.set(x, y, z, "air")
    for y in range(FL + 19, FL + 23):
        rr = 4 - (y - FL - 19)
        for x in range(mx - 4, mx + 5):
            for z in range(mz - 4, mz + 5):
                if math.hypot(x - mx, z - mz) <= rr + 0.3:
                    b.set(x, y, z, "dark_oak_planks" if y == FL + 22 else "spruce_planks")
    # sails (south face)
    sx, sy, sz = mx, FL + 12, mz + r + 1
    b.set(sx, sy, sz, "spruce_log[axis=z]")
    for i in range(1, 8):
        for (dx, dy) in ((i, i), (-i, i), (i, -i), (-i, -i)):
            if b.inside(sx + dx, sy + dy, sz):
                b.set(sx + dx, sy + dy, sz, "spruce_fence")
            if 2 <= i <= 6 and b.inside(sx + dx + (1 if dx > 0 else -1), sy + dy, sz):
                b.set(sx + dx + (1 if dx > 0 else -1), sy + dy, sz, "white_wool" if i % 2 else "light_gray_wool")
    # the lamp plinth and blueprint parts
    ly = FL + 15
    b.set(mx, ly - 1, mz, "polished_andesite")
    parts = [("pearlescent_froglight", (mx, ly, mz)),
             ("lantern[hanging=false]", (mx - 1, ly, mz - 1)), ("lantern[hanging=false]", (mx + 1, ly, mz - 1)),
             ("lantern[hanging=false]", (mx - 1, ly, mz + 1)), ("lantern[hanging=false]", (mx + 1, ly, mz + 1)),
             ("honeycomb_block", (mx, ly, mz - 1)), ("honeycomb_block", (mx, ly, mz + 1))]
    poi.add("aldercross.wakelamp", *W(mx, ly, mz), parts=[{"pos": list(W(*p)), "block": blk} for blk, p in parts])
    # pale roots choking the lamp chamber stair (removed when the Heartwood's hearts are broken)
    roots = []
    for y in range(FL + 10, FL + 15):
        for (x, z) in ((mx - r + 1, mz), (mx - r + 1, mz - 1), (mx - r + 1, mz + 1), (mx - r + 2, mz)):
            if b.get(x, y, z) is not None and b.get(x, y, z).name in ("minecraft:air", "minecraft:ladder"):
                b.set(x, y, z, "pale_oak_wood[axis=y]" if (x + y) % 2 else "pale_oak_log[axis=y]")
                roots.append(list(W(x, y, z)))
    for y in range(FL + 15, FL + 18):
        b.set(mx - 2, y, mz, "pale_oak_log[axis=y]"); roots.append(list(W(mx - 2, y, mz)))
        b.set(mx + 2, y, mz, "pale_oak_log[axis=y]"); roots.append(list(W(mx + 2, y, mz)))
    poi.add("aldercross.roots", *W(mx, FL + 12, mz), blocks=roots)
    poi.box("aldercross.windmill", *W(mx - r - 1, FL - 1, mz - r - 1), *W(mx + r + 1, FL + 22, mz + r + 1))

    # ------------------------------------------------------------ the Heartwood (giant pale oak with three hearts)
    hx, hz = 78, 76
    trunk_h = 20
    for y in range(FL, FL + trunk_h):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if abs(dx) + abs(dz) <= 2:
                    b.set(hx + dx, y, hz + dz, "pale_oak_wood[axis=y]" if (dx or dz) else "pale_oak_log[axis=y]")
    # trunk heart (axis y) with logs above and below
    th = (hx, FL + 7, hz)
    b.set(hx, FL + 6, hz, "pale_oak_log[axis=y]")
    b.set(*th, "creaking_heart[axis=y,creaking_heart_state=dormant,natural=true]")
    b.set(hx, FL + 8, hz, "pale_oak_log[axis=y]")
    b.set(hx, FL + 7, hz + 1, "resin_clump[down=false,east=false,north=false,south=true,up=false,waterlogged=false,west=false]") if False else None
    # roots: arms along x to the east and west, each with a heart (axis x)
    hearts = [th]
    for side in (-1, 1):
        for i in range(2, 9):
            b.set(hx + side * i, FL, hz, "pale_oak_log[axis=x]")
            if i < 5:
                b.set(hx + side * i, FL + 1, hz, "pale_oak_log[axis=x]")
        hp = (hx + side * 6, FL + 1, hz)
        b.set(hx + side * 5, FL + 1, hz, "pale_oak_log[axis=x]")
        b.set(*hp, "creaking_heart[axis=x,creaking_heart_state=dormant,natural=true]")
        b.set(hx + side * 7, FL + 1, hz, "pale_oak_log[axis=x]")
        hearts.append(hp)
    # canopy
    for y in range(FL + 12, FL + trunk_h + 4):
        rr = 9 if y < FL + 18 else (7 if y < FL + 21 else 4)
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if dx * dx + dz * dz <= rr * rr and b.inside(hx + dx, y, hz + dz) and b.get(hx + dx, y, hz + dz) is None and rng.random() < 0.8:
                    b.set(hx + dx, y, hz + dz, "pale_oak_leaves[distance=1,persistent=true,waterlogged=false]")
    for _ in range(40):
        dx, dz = rng.randint(-8, 8), rng.randint(-8, 8)
        y = FL + 11
        if b.get(hx + dx, y, hz + dz) is None and b.get(hx + dx, y + 1, hz + dz) is not None:
            b.set(hx + dx, y, hz + dz, "pale_hanging_moss[tip=true]")
    for x in range(hx - 12, hx + 13):
        for z in range(hz - 12, hz + 13):
            if b.inside(x, FL - 1, z) and b.get(x, FL - 1, z) is None and (x - hx) ** 2 + (z - hz) ** 2 <= 144:
                b.set(x, FL - 1, z, "pale_moss_block")
                if b.get(x, FL, z) is None and rng.random() < 0.3:
                    b.set(x, FL, z, "pale_moss_carpet[bottom=true,east=none,north=none,south=none,west=none]")
    poi.add("aldercross.hearts", *W(hx, FL + 1, hz), hearts=[list(W(*h)) for h in hearts])
    poi.box("aldercross.heartwood", *W(hx - 14, FL - 1, hz - 14), *W(hx + 14, FL + 24, hz + 14))

    # ------------------------------------------------------------ lamps
    lamps = []
    for (x, z) in ((30, 60), (46, 30), (54, 56), (46, 70), (62, 50)):
        if b.get(x, FL, z) is None or b.get(x, FL, z).name == "minecraft:air":
            lamps.append(street_lamp(b, x, FL, z, lit=False))
    poi.add("aldercross.lamps", *W(50, FL, 50), lamps=[list(W(*p)) for p in lamps])

    autoconnect(b)
    from ..houses import benchmark
    benchmark(b, ORIGIN, 298, 78, -44, 4, "north")
    return [Piece("orchard", b, ORIGIN)]
