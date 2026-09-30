"""Hollin — the lakeside village (Chapter 1) and the player's home base."""
from __future__ import annotations

import random

from .. import poi
from ..buildkit import autoconnect, door, gable_roof, stairs, window, bed
from ..houses import STYLES, cottage, fence_line, ground_at, sign, street_lamp, tree
from ..structure import Build, container_nbt, item_stack_nbt, sign_nbt, text_display
from . import Piece

SITE = "hollin"
ORIGIN = (70, 60, 68)
SIZE = (100, 48, 100)
FL = 7      # local floor (world 67)
SQUARE = (30, 36, 70, 64)


def W(lx, ly, lz):
    return (ORIGIN[0] + lx, ORIGIN[1] + ly, ORIGIN[2] + lz)


def L(wx, wz):
    return (wx - ORIGIN[0], wz - ORIGIN[2])


def _ground(b: Build, x, z, block, clear=4):
    b.set(x, FL - 1, z, block)
    for y in range(FL, FL + clear):
        if b.get(x, y, z) is None:
            b.set(x, y, z, "air")


def build() -> list[Piece]:
    rng = random.Random(0x4011)
    b = Build(*SIZE)
    plain, warm, stone, brick = STYLES["plain"], STYLES["warm"], STYLES["stone"], STYLES["brick"]

    # ------------------------------------------------------------ plaza and streets
    sx0, sz0, sx1, sz1 = SQUARE
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            if (x - 50) ** 2 / 400 + (z - 50) ** 2 / 196 <= 1.0:
                r = rng.random()
                _ground(b, x, z, "cobblestone" if r < 0.5 else ("stone_bricks" if r < 0.75 else ("mossy_cobblestone" if r < 0.92 else "andesite")))
    # main street: south gate (37,88) -> square (46,63)
    for i in range(0, 60):
        t = i / 59
        cx = 37 + (46 - 37) * t
        cz = 92 - (92 - 62) * t
        for dx in range(-2, 3):
            x, z = int(round(cx)) + dx, int(round(cz))
            if b.get(x, FL - 1, z) is None:
                _ground(b, x, z, "cobblestone" if abs(dx) < 2 else rng.choice(["gravel", "coarse_dirt", "cobblestone"]))
    # east lane: square -> east exit (98, 42) toward Aldercross road
    for x in range(70, 100):
        cz = 44 - (x - 70) * 0.12
        for dz in range(-1, 2):
            z = int(round(cz)) + dz
            if b.get(x, FL - 1, z) is None:
                _ground(b, x, z, rng.choice(["cobblestone", "gravel", "coarse_dirt"]))
    # lake lane: square NW corner (32,40) -> ferry steps (14,16), following terrain
    lane = []
    for i in range(0, 40):
        t = i / 39
        lane.append((32 + (13 - 32) * t, 40 + (15 - 40) * t))
    for (cx, cz) in lane:
        for dx in range(-1, 2):
            x, z = int(round(cx)) + dx, int(round(cz))
            gy = ground_at(SITE, ORIGIN[0] + x, ORIGIN[2] + z)
            ly = gy - ORIGIN[1]
            if 1 <= ly <= FL:
                b.set(x, ly - 1, z, "cobblestone" if dx == 0 else "gravel")
                b.fill(x, ly, z, x, ly + 3, z, "air")

    # ------------------------------------------------------------ the well (plaza centre)
    for x in range(48, 53):
        for z in range(48, 53):
            edge = x in (48, 52) or z in (48, 52)
            b.set(x, FL - 1, z, "stone_bricks" if edge else "water[level=0]")
            b.set(x, FL - 2, z, "stone_bricks" if edge else "water[level=0]")
            b.set(x, FL - 3, z, "stone_bricks")
            if edge:
                b.set(x, FL, z, "stone_brick_wall" if (x in (48, 52) and z in (48, 52)) else "stone_brick_slab[type=bottom]")
    for (x, z) in ((48, 48), (52, 48), (48, 52), (52, 52)):
        b.set(x, FL + 1, z, "dark_oak_fence"); b.set(x, FL + 2, z, "dark_oak_fence")
    for x in range(47, 54):
        for z in range(47, 54):
            if x in (47, 53) or z in (47, 53):
                b.set(x, FL + 3, z, stairs("dark_oak", "south" if z == 47 else "north" if z == 53 else ("east" if x == 47 else "west")))
            else:
                b.set(x, FL + 3, z, "dark_oak_slab[type=bottom]" if not (x == 50 and z == 50) else "dark_oak_planks")
    b.set(50, FL + 2, 50, "iron_chain[axis=y]")
    b.set(50, FL + 1, 50, "iron_chain[axis=y]")
    poi.box("hollin.well", *W(46, FL - 1, 46), *W(54, FL + 4, 54))

    # ------------------------------------------------------------ the bell tower (north of the square)
    tx0, tz0, tx1, tz1 = 46, 24, 54, 32
    b.fill(tx0 - 1, FL - 2, tz0 - 1, tx1 + 1, FL - 1, tz1 + 1, "cobblestone")
    for y in range(FL, FL + 20):
        mat = "stone_bricks" if y < FL + 14 else "stripped_dark_oak_log[axis=y]"
        for x in range(tx0, tx1 + 1):
            for z in range(tz0, tz1 + 1):
                wall = x in (tx0, tx1) or z in (tz0, tz1)
                if not wall:
                    b.set(x, y, z, "air")
                    continue
                corner = x in (tx0, tx1) and z in (tz0, tz1)
                if y < FL + 14:
                    b.set(x, y, z, "chiseled_stone_bricks" if corner and y % 5 == 0 else ("mossy_stone_bricks" if rng.random() < 0.18 else "stone_bricks"))
                else:
                    # belfry: open arches between corner posts
                    if corner:
                        b.set(x, y, z, "stripped_dark_oak_log[axis=y]")
                    elif y == FL + 14 or y == FL + 19:
                        b.set(x, y, z, "dark_oak_planks")
                    elif y == FL + 18:
                        b.set(x, y, z, stairs("dark_oak", "south" if z == tz0 else "north" if z == tz1 else ("east" if x == tx0 else "west"), "top"))
                    else:
                        b.set(x, y, z, "air")
    # tower floors and ladder (inner north-west corner)
    lx, lz = tx0 + 1, tz0 + 1
    b.fill(tx0 + 1, FL + 13, tz0 + 1, tx1 - 1, FL + 13, tz1 - 1, "dark_oak_planks")      # belfry floor at FL+14 walking level
    b.fill(tx0 + 1, FL + 19, tz0 + 1, tx1 - 1, FL + 19, tz1 - 1, "dark_oak_planks")      # belfry ceiling / cradle floor
    for y in range(FL, FL + 19):
        b.set(lx, y, lz, "ladder[facing=south,waterlogged=false]")
    b.set(lx, FL + 13, lz, "ladder[facing=south,waterlogged=false]")
    # the hatch to the cradle (locked until the Round is rung)
    b.set(lx, FL + 19, lz, "iron_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]")
    poi.add("hollin.hatch", *W(lx, FL + 19, lz))
    # windows and door
    for y in (FL + 4, FL + 9):
        for (x, z) in ((50, tz0), (50, tz1), (tx0, 28), (tx1, 28)):
            b.set(x, y, z, "glass_pane")
            b.set(x, y + 1, z, "glass_pane")
    door(b, 50, FL, tz1, "north", "dark_oak")
    b.set(50, FL + 2, tz1, "chiseled_stone_bricks")
    sign(b, 51, FL + 2, tz1 + 1, "south", ["", "BELL TOWER", "of Hollin", ""])
    # bells: four family bells hanging from the belfry ceiling
    bell_y = FL + 18
    bells = {"dunn": (50, tz0 + 2), "pell": (tx1 - 2, 28), "marsh": (50, tz1 - 2), "tarn": (tx0 + 2, 28)}
    names = {"dunn": "DUNN", "pell": "PELL", "marsh": "MARSH", "tarn": "TARN"}
    for fam, (bx, bz) in bells.items():
        b.set(bx, bell_y + 1, bz, "dark_oak_planks")
        b.set(bx, bell_y, bz, "bell[attachment=ceiling,facing=north,powered=false]")
        poi.add(f"hollin.bell.{fam}", *W(bx, bell_y, bz))
    # name plaques on the belfry posts, facing inward under each bell
    for fam, (bx, bz) in bells.items():
        if bz == tz0 + 2:
            b.set(bx, FL + 16, tz0 + 1, "dark_oak_planks"); sign(b, bx, FL + 16, tz0 + 2, "south", ["", names[fam], "", ""], "dark_oak", "white", True)
        elif bz == tz1 - 2:
            b.set(bx, FL + 16, tz1 - 1, "dark_oak_planks"); sign(b, bx, FL + 16, tz1 - 2, "north", ["", names[fam], "", ""], "dark_oak", "white", True)
        elif bx == tx1 - 2:
            b.set(tx1 - 1, FL + 16, bz, "dark_oak_planks"); sign(b, tx1 - 2, FL + 16, bz, "west", ["", names[fam], "", ""], "dark_oak", "white", True)
        else:
            b.set(tx0 + 1, FL + 16, bz, "dark_oak_planks"); sign(b, tx0 + 2, FL + 16, bz, "east", ["", names[fam], "", ""], "dark_oak", "white", True)
    poi.box("hollin.belfry", *W(tx0, FL + 14, tz0), *W(tx1, FL + 18, tz1))
    # the cradle (top platform) with parapet, spire and the Wakelamp positions
    cy = FL + 20
    for x in range(tx0 - 1, tx1 + 2):
        for z in range(tz0 - 1, tz1 + 2):
            edge = x in (tx0 - 1, tx1 + 1) or z in (tz0 - 1, tz1 + 1)
            b.set(x, cy - 1, z, "stone_bricks" if edge else "dark_oak_planks")
            if edge:
                b.set(x, cy, z, "stone_brick_wall")
    b.set(lx, cy - 1, lz, "iron_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]")
    poi.add("hollin.hatch_top", *W(lx, cy - 1, lz))
    # four spire posts and the roof
    for (x, z) in ((tx0, tz0), (tx1, tz0), (tx0, tz1), (tx1, tz1)):
        for y in range(cy, cy + 5):
            b.set(x, y, z, "stripped_dark_oak_log[axis=y]")
    top = gable_roof(b, tx0, tz0, tx1, tz1, cy + 5, "deepslate_tile", ridge_axis="x", overhang=1)
    b.set(50, top + 1, 28, "lightning_rod[facing=up,powered=false,waterlogged=false]")
    lamp_c = (50, cy + 1, 28)
    b.fill(49, cy, 27, 51, cy, 29, "polished_andesite")                   # lamp plinth
    b.set(50, cy + 4, 28, "dark_oak_planks")                              # beam the chains hang from
    parts = [
        ("pearlescent_froglight", (50, cy + 1, 28)),
        ("copper_grate", (49, cy + 1, 28)), ("copper_grate", (51, cy + 1, 28)),
        ("copper_grate", (50, cy + 1, 27)), ("copper_grate", (50, cy + 1, 29)),
        ("iron_chain[axis=y]", (50, cy + 2, 28)), ("iron_chain[axis=y]", (50, cy + 3, 28)),
    ]
    poi.add("hollin.wakelamp", *W(*lamp_c), parts=[{"pos": list(W(*p)), "block": blk} for blk, p in parts])
    # the cradle chest (holds the Stillglass Lamp once the Round unlocks the hatch)
    b.set(tx1 - 1, cy, tz1 - 1, "chest[facing=west,type=single,waterlogged=false]", container_nbt("minecraft:chest", []))
    poi.add("hollin.cradle_chest", *W(tx1 - 1, cy, tz1 - 1))
    poi.box("hollin.cradle", *W(tx0 - 1, cy, tz0 - 1), *W(tx1 + 1, cy + 4, tz1 + 1))

    # ------------------------------------------------------------ the Hall (west of the square)
    hx0, hz0, hx1, hz1 = 16, 38, 30, 56
    info = cottage(b, hx0, hz0, hx1 - hx0 + 1, hz1 - hz0 + 1, FL, "east", stone, rng, role="hall", furnish=False, ridge="z", chimney_side="west")
    for z in range(hz0 + 2, hz1 - 1):
        for x in (hx0 + 3, hx0 + 4, hx0 + 6, hx0 + 7, hx0 + 9, hx0 + 10):
            if z % 3 != 0:
                b.set(x, FL, z, stairs("spruce", "west"))
    b.set(hx0 + 1, FL, 47, "lectern[facing=east,has_book=false,powered=false]")
    b.set(hx0 + 1, FL + 1, 44, "candle[candles=3,lit=false,waterlogged=false]") if b.get(hx0 + 1, FL + 1, 44) and b.get(hx0 + 1, FL + 1, 44).name == "minecraft:air" else None
    # the Round, painted on the north wall inside (text display)
    b.entity(hx0 + 7.5, FL + 1.6, hz0 + 1.02, text_display(
        [{"text": "THE LAMPLIGHTERS' ROUND\n", "color": "gold"},
         {"text": "Bread for the morning,\nwater for noon,\nwords for the evening,\nthe ferry for home.", "color": "white"}],
        yaw=0.0, scale=0.55, line_width=220, background=0x55000000))
    poi.box("hollin.hall", *W(hx0 + 1, FL, hz0 + 1), *W(hx1 - 1, FL + 3, hz1 - 1))
    poi.add("hollin.round_text", *W(hx0 + 7, FL + 1, hz0 + 1))

    # ------------------------------------------------------------ the Bakery (east of the square)
    bx0, bz0 = 70, 40
    info_b = cottage(b, bx0, bz0, 11, 11, FL, "west", brick, rng, role="shop", furnish=True, ridge="x", chimney_side="east")
    for z in range(bz0 + 6, bz0 + 10):
        b.set(bx0 + 9, FL, z, "smoker[facing=west,lit=false]" if z % 2 else "furnace[facing=west,lit=false]")
    b.set(bx0 + 8, FL, bz0 + 8, "hay_block[axis=y]")
    b.set(bx0 + 1, FL, bz0 + 9, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:paper", 1, name={"text": "Recipe card", "italic": False},
                       lore=["\"Emory's seed loaf — don't tell Mirelle", "I wrote it down.\""], custom={"pm": {"keepsake": 6}}, slot=13)]))
    sign(b, bx0 - 1, FL + 2, bz0 + 3, "west", ["", "DUNN & DAUGHTER", "Bakers", ""])
    poi.box("hollin.bakery", *W(bx0 + 1, FL, bz0 + 1), *W(bx0 + 9, FL + 3, bz0 + 9))
    poi.add("hollin.keepsake.emory", *W(bx0 + 1, FL, bz0 + 9))

    # ------------------------------------------------------------ cottages
    homes = [
        (32, 16, 9, 8, "south", plain, "jory"),      # Jory Pell, bell-ringer (near the tower)
        (56, 76, 9, 8, "west", warm, "odile"),       # Odile Marsh, lamplighter (by the south gate)
        (60, 60, 9, 9, "north", plain, "rest"),      # the Surveyor's Rest (home plot)
        (10, 60, 9, 8, "east", warm, "house1"),
        (22, 68, 8, 8, "north", plain, "house2"),
        (74, 58, 9, 8, "west", stone, "house3"),
        (76, 72, 10, 8, "west", warm, "house4"),
    ]
    for (x0, z0, w, d, side, st, name) in homes:
        role = "rest" if name == "rest" else "home"
        inf = cottage(b, x0, z0, w, d, FL, side, st, rng, role=role, storeys=2 if name == "jory" else 1)
        poi.box(f"hollin.home.{name}", *W(x0 + 1, FL, z0 + 1), *W(x0 + w - 2, FL + 3, z0 + d - 2))
    # Jory's loft keepsake (Agnes Pell's scarf)
    b.set(33, FL + 4, 17, "chest[facing=south,type=single,waterlogged=false]", container_nbt("minecraft:chest", [
        item_stack_nbt("minecraft:white_wool", 1, name={"text": "Knitted scarf", "italic": False},
                       lore=["Grey wool, darned many times.", "A name stitched in the corner: A. PELL."], custom={"pm": {"keepsake": 3}}, slot=11)]))
    poi.add("hollin.keepsake.agnes", *W(33, FL + 4, 17))

    # ------------------------------------------------------------ south gate
    for gx in (33, 41):
        for y in range(FL, FL + 5):
            b.set(gx, y, 88, "stone_bricks" if y < FL + 4 else "chiseled_stone_bricks")
        b.set(gx, FL + 5, 88, "lantern[hanging=false]")
    for x in range(33, 42):
        b.set(x, FL + 4, 88, "stone_bricks" if x in (33, 41) else "dark_oak_slab[type=top]")
    sign(b, 37, FL + 3, 89, "south", ["", "HOLLIN", "~", ""])
    poi.box("hollin.gate_area", *W(26, FL - 1, 80), *W(48, FL + 6, 99))
    poi.add("hollin.gate", *W(37, FL, 86))
    # Odile's lamp: the one lamp she lights every evening, by the gate
    odile_lamp = street_lamp(b, 44, FL, 84, lit=True)
    poi.add("hollin.odile", *W(45, FL, 83), yaw=135.0)

    # ------------------------------------------------------------ street lamps (unlit until restored)
    lamps = []
    for (x, z) in ((31, 50), (69, 50), (50, 35), (50, 65), (38, 72), (44, 60), (80, 43), (92, 41), (26, 34), (19, 24)):
        if b.get(x, FL, z) is None or b.get(x, FL, z).name == "minecraft:air":
            lamps.append(street_lamp(b, x, FL, z, lit=False))
    poi.add("hollin.lamps", *W(50, FL, 50), lamps=[list(W(*p)) for p in lamps])
    plaza_lamps = [(34, 44), (66, 44), (34, 58), (66, 58)]
    pl = []
    for (x, z) in plaza_lamps:
        pl.append(street_lamp(b, x, FL, z, lit=False))
    poi.add("hollin.plaza_lamps", *W(50, FL, 50), lamps=[list(W(*p)) for p in pl])
    poi.box("hollin.plaza", *W(28, FL - 1, 34), *W(72, FL + 8, 66))

    # ------------------------------------------------------------ ferry steps, boathouse and jetty
    shore = []
    for z in range(0, 30):
        for x in range(0, 30):
            wx, wz = ORIGIN[0] + x, ORIGIN[2] + z
            g = ground_at(SITE, wx, wz)
            if g <= 63:
                shore.append((x, z, g))
    # jetty: from the end of the lake lane (13,15) out to the north-west over the water
    jx, jz = 12, 14
    g0 = ground_at(SITE, ORIGIN[0] + jx, ORIGIN[2] + jz) - ORIGIN[1]
    deck = 63 - ORIGIN[1]      # world y 63: one above the water surface block (62)
    for i in range(0, 12):
        x, z = jx - i, jz - i
        if not b.inside(x, deck, z) or not b.inside(x - 1, deck, z):
            break
        for w in (0, 1):
            b.set(x - w, deck, z, "spruce_planks")
            gz = ground_at(SITE, ORIGIN[0] + x - w, ORIGIN[2] + z) - ORIGIN[1]
            if gz <= deck:
                for y in range(max(0, gz - 1), deck):
                    if (x + z) % 3 == 0 and w == 0:
                        b.set(x - w, y, z, "spruce_log[axis=y]")
            else:
                b.fill(x - w, deck + 1, z, x - w, min(SIZE[1] - 1, gz + 2), z, "air")
        if i % 3 == 0:
            b.set(x + 1, deck + 1, z, "spruce_fence")
    b.set(jx - 11, deck + 1, jz - 11, "lantern[hanging=false]") if b.inside(jx - 11, deck + 1, jz - 11) else None
    poi.box("hollin.ferry", *W(jx - 12, deck - 1, jz - 12), *W(jx + 4, FL + 4, jz + 4))
    poi.add("hollin.jetty_end", *W(max(0, jx - 10), deck + 1, max(0, jz - 10)))
    # boathouse keepsake (Wendel Tarn's tin whistle) in a barrel by the jetty foot
    wy = max(1, g0)
    b.set(jx + 2, wy, jz + 1, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:iron_nugget", 1, name={"text": "Tin whistle", "italic": False},
                       lore=["Dented. The mouthpiece is worn smooth.", "Scratched on it: W.T."], custom={"pm": {"keepsake": 4}}, slot=13)]))
    poi.add("hollin.keepsake.wendel", *W(jx + 2, wy, jz + 1))
    sign(b, jx + 3, wy + 1, jz + 1, "east", ["", "TARN", "Ferry", "to the Meridian"]) if b.inside(jx + 3, wy + 1, jz + 1) else None

    # ------------------------------------------------------------ gardens, trees, details
    fence_line(b, [(10, 70), (20, 70), (20, 78), (10, 78), (10, 70)], FL)
    for x in range(11, 20):
        for z in range(71, 78):
            b.set(x, FL - 1, z, "farmland[moisture=0]")
            b.set(x, FL, z, "wheat[age=2]" if (x + z) % 2 else "carrots[age=3]")
    for (x, z, kind) in ((8, 50, "oak"), (86, 56, "birch"), (88, 28, "oak"), (60, 86, "birch"), (24, 86, "oak"), (78, 88, "oak")):
        if b.get(x, FL, z) is None:
            b.set(x, FL - 1, z, "grass_block[snowy=false]")
            tree(b, x, FL, z, rng, kind, 5)
    for _ in range(40):
        x, z = rng.randrange(4, 96), rng.randrange(4, 96)
        if b.get(x, FL, z) is None and b.get(x, FL - 1, z) is None and (x - 50) ** 2 + (z - 50) ** 2 < 46 ** 2:
            b.set(x, FL - 1, z, "grass_block[snowy=false]")
            b.set(x, FL, z, rng.choice(["pale_moss_carpet[bottom=true,east=none,north=none,south=none,west=none]", "short_grass", "closed_eyeblossom"]))

    autoconnect(b)
    poi.box("hollin.area", *W(0, 0, 0), *W(SIZE[0] - 1, SIZE[1] - 1, SIZE[2] - 1))
    return [Piece("village", b, ORIGIN)]
