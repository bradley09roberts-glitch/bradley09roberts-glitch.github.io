"""The Landing — the south-rim waystation where the campaign opens (Prologue)."""
from __future__ import annotations

import random

from .. import poi
from ..buildkit import autoconnect, bed, door, gable_roof, stairs, timber_walls, chimney, window
from ..structure import Build, container_nbt, item_stack_nbt, sign_nbt
from . import Piece

SITE = "landing"
ORIGIN = (-24, 84, 328)          # world position of template (0,0,0)
SIZE = (48, 24, 54)
FLOOR = 2                        # local y of the walking level (world y 86)


def W(lx, ly, lz):
    return (ORIGIN[0] + lx, ORIGIN[1] + ly, ORIGIN[2] + lz)


def road_x(lz: float) -> float:
    return 24.0 if lz >= 32 else 24.0 + (32 - lz) * (12.0 / 42.0)


def build() -> list[Piece]:
    rng = random.Random(0x1A4D1)
    b = Build(*SIZE)
    F = FLOOR

    # --- cobbled road through the Landing (clears vegetation above it) ---------------------------
    for lz in range(0, SIZE[2]):
        cx = road_x(lz)
        for lx in range(int(cx) - 2, int(cx) + 3):
            d = abs(lx - cx)
            if d <= 1.6:
                r = rng.random()
                blk = "cobblestone" if r < 0.55 else ("mossy_cobblestone" if r < 0.8 else "gravel")
            elif d <= 2.6 and rng.random() < 0.5:
                blk = "coarse_dirt"
            else:
                continue
            b.set(lx, F - 1, lz, blk)
            b.fill(lx, F, lz, lx, F + 3, lz, "air")

    # --- the Gate of Vell (rim arch) --------------------------------------------------------------
    gz = 47
    for px in (20, 28):
        b.fill(px, F, gz, px, F + 5, gz, "stone_bricks")
        b.set(px, F, gz, "mossy_stone_bricks")
        b.set(px, F + 3, gz, "cracked_stone_bricks")
        b.set(px, F + 6, gz, "chiseled_stone_bricks")
    for x in range(19, 30):
        b.set(x, F + 7, gz, "stone_brick_slab[type=bottom]")
    for x in range(21, 28):
        b.set(x, F + 6, gz, "stone_bricks" if x not in (21, 27) else stairs("stone_brick", "west" if x == 21 else "east", "top"))
    b.set(24, F + 7, gz, "chiseled_stone_bricks")
    b.set(24, F + 5, gz, "iron_chain[axis=y]")
    b.set(24, F + 4, gz, "lantern[hanging=true]")
    b.set(24, F + 6, gz + 1, "oak_wall_sign[facing=south]", sign_nbt(["", "VELL", "~ the Landing ~", ""], kind="minecraft:sign"))

    # --- the Waystation (two storeys, timber-framed) ----------------------------------------------
    x0, z0, x1, z1 = 6, 20, 14, 28
    b.fill(x0 - 1, F - 1, z0 - 1, x1 + 1, F - 1, z1 + 1, "cobblestone")          # plinth
    b.fill(x0, F - 1, z0, x1, F - 1, z1, "spruce_planks")                       # floor
    timber_walls(b, x0, z0, x1, z1, F, F + 3, "stripped_dark_oak_log", "calcite", base="stone_bricks", post_every=4)
    timber_walls(b, x0, z0, x1, z1, F + 4, F + 6, "stripped_dark_oak_log", "calcite", post_every=4)
    b.fill(x0 + 1, F + 3, z0 + 1, x1 - 1, F + 3, z1 - 1, "spruce_planks")       # loft floor
    b.fill(x0 + 1, F, z0 + 1, x1 - 1, F + 2, z1 - 1, "air")
    b.fill(x0 + 1, F + 4, z0 + 1, x1 - 1, F + 6, z1 - 1, "air")
    ridge = gable_roof(b, x0, z0, x1, z1, F + 7, "dark_oak", ridge_axis="x", overhang=1, fill_gable="calcite")
    # windows
    for (wx, wz) in ((x0, 23), (x0, 25), (x1, 22), (x1, 26)):
        window(b, wx, F + 1, wz, "glass_pane")
    for (wx, wz) in ((10, z0), (10, z1)):
        window(b, wx, F + 1, wz, "glass_pane")
        window(b, wx, F + 5, wz, "glass_pane")
    # door (east, facing the road) and porch
    door(b, x1, F, 24, "west", "spruce")
    b.set(x1 + 1, F - 1, 23, "spruce_planks"); b.set(x1 + 1, F - 1, 24, "spruce_planks"); b.set(x1 + 1, F - 1, 25, "spruce_planks")
    b.set(x1 + 1, F + 2, 23, "lantern[hanging=false]")
    b.set(x1 + 1, F, 26, stairs("spruce", "west"))      # porch bench
    b.set(x1 + 1, F, 27, stairs("spruce", "west"))
    # chimney on west gable + hearth
    chimney(b, x0 - 1, 24, F, ridge + 1, "bricks")
    b.set(x0 + 1, F, 24, "campfire[lit=false,facing=east,signal_fire=false]")
    b.set(x0 + 1, F + 1, 24, "bricks"); b.set(x0 + 1, F + 2, 24, "bricks")
    b.set(x0 + 1, F, 23, "bricks"); b.set(x0 + 1, F, 25, "bricks")
    # interior furniture
    b.set(12, F, 21, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:bread", 3, slot=0), item_stack_nbt("minecraft:torch", 4, slot=1)]))
    b.set(13, F, 21, "crafting_table")
    b.set(8, F, 27, "bookshelf"); b.set(9, F, 27, "bookshelf")
    b.set(11, F, 27, "lectern[facing=north,has_book=false,powered=false]")
    b.set(12, F, 26, "spruce_fence"); b.set(12, F + 1, 26, "spruce_pressure_plate[powered=false]")
    b.set(12, F, 25, stairs("spruce", "south")); b.set(13, F, 26, stairs("spruce", "west"))
    b.set(10, F + 2, 24, "lantern[hanging=true]")
    b.set(10, F + 3, 24, "spruce_planks")
    # ladder to loft (north-east corner inside)
    for y in range(F, F + 4):
        b.set(13, y, 21 + 1, "ladder[facing=west]") if False else None
    b.set(13, F + 3, 22, "air")
    for y in range(F, F + 4):
        b.set(13, y, 22, "ladder[facing=west]")
    # loft: dusty bed, chest (a keepsake of the Eleven), shelves
    bed(b, 8, F + 4, 26, "north", "light_gray")
    b.set(8, F + 4, 22, "chest[facing=south,type=single,waterlogged=false]", container_nbt("minecraft:chest", [
        item_stack_nbt("minecraft:paper", 1, name={"text": "Waystation ledger (torn)", "italic": False},
                       lore=["Last entry: \"Road closed. The fog", "came off the water tonight.\""], slot=4),
        item_stack_nbt("minecraft:paper", 1, name={"text": "Child's drawing", "italic": False},
                       lore=["Crayon on ledger paper: a woman with a lamp", "as big as the sun. \"MUM AT WORK\" — N.F."],
                       custom={"pm": {"keepsake": 8}}, slot=13)]))
    b.set(11, F + 4, 27, "spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]")
    poi.add("landing.loft_chest", *W(8, F + 4, 22))

    # --- the noticeboard (first interaction) --------------------------------------------------------
    nx = 20
    for nz in (19, 22):
        b.fill(nx, F, nz, nx, F + 3, nz, "spruce_log[axis=y]")
    b.fill(nx, F + 1, 20, nx, F + 2, 21, "spruce_planks")
    for nz in range(18, 24):
        b.set(nx, F + 4, nz, "spruce_slab[type=bottom]")
    b.set(nx + 1, F + 2, 20, "spruce_wall_sign[facing=east]", sign_nbt(["NOTICES", "", "Road to Hollin", "CLOSED"], kind="minecraft:sign"))
    b.set(nx + 1, F + 2, 21, "spruce_wall_sign[facing=east]", sign_nbt(["For the", "Surveyor", "", "(a letter)"], kind="minecraft:sign", color="brown"))
    b.set(nx + 1, F + 1, 21, "air")
    poi.add("landing.noticeboard", nx + 1.5 + ORIGIN[0], F + 1 + ORIGIN[1], 21.0 + ORIGIN[2], facing="east")

    # --- the broken Landing lamp (Prologue build objective) -------------------------------------
    lx, lz = 29, 18
    b.fill(lx - 2, F - 1, lz - 2, lx + 2, F - 1, lz + 2, "mossy_cobblestone")
    b.set(lx - 2, F - 1, lz - 2, "grass_block[snowy=false]"); b.set(lx + 2, F - 1, lz + 2, "grass_block[snowy=false]")
    b.fill(lx - 2, F, lz - 2, lx + 2, F + 4, lz + 2, "air")
    b.set(lx, F, lz, "mossy_stone_bricks")
    parts = [("stone_bricks", F + 1), ("stone_bricks", F + 2), ("iron_chain[axis=y]", F + 3), ("lantern[hanging=false]", F + 4)]
    poi.add("landing.lamp", *W(lx, F, lz), parts=[{"pos": list(W(lx, y, lz)), "block": blk} for blk, y in parts])
    # the fallen top lies beside it (visual clue)
    b.set(lx + 1, F, lz + 1, "cracked_stone_bricks")
    b.set(lx - 1, F, lz, stairs("stone_brick", "east"))
    b.set(lx + 1, F, lz - 1, "stone_brick_slab[type=bottom]")

    # --- Tamsin's camp ----------------------------------------------------------------------------
    cx0, cz0 = 36, 2
    b.fill(cx0 - 2, F - 1, cz0 - 1, cx0 + 10, F - 1, cz0 + 12, "coarse_dirt")
    b.fill(cx0 - 2, F, cz0 - 1, cx0 + 10, F + 7, cz0 + 12, "air")
    for z in range(cz0 + 1, cz0 + 6):
        for i, (dx, dy) in enumerate(((0, 0), (1, 1), (2, 2), (3, 3), (4, 2), (5, 1), (6, 0))):
            b.set(cx0 + 1 + dx, F + dy, z, "white_wool" if (z % 2 or dy == 3) else "light_gray_wool")
    for dx, h in ((1, 0), (2, 1), (3, 2), (4, 3), (5, 2), (6, 1), (7, 0)):
        for dy in range(0, h + 1):
            b.set(cx0 + dx, F + dy, cz0 + 1, "white_wool")
    # clean the tent interior
    for z in range(cz0 + 2, cz0 + 6):
        b.fill(cx0 + 2, F, z, cx0 + 6, F, z, "air")
        b.fill(cx0 + 3, F + 1, z, cx0 + 5, F + 1, z, "air")
        b.set(cx0 + 4, F + 2, z, "air")
    bed(b, cx0 + 2, F, cz0 + 3, "north", "white")
    b.set(cx0 + 6, F, cz0 + 2, "chest[facing=west,type=single,waterlogged=false]", container_nbt("minecraft:chest", [
        item_stack_nbt("minecraft:bread", 4, slot=0),
        item_stack_nbt("minecraft:torch", 6, slot=1),
        item_stack_nbt("minecraft:iron_nugget", 8, slot=2),
        item_stack_nbt("minecraft:stone_bricks", 2, slot=3),
        item_stack_nbt("minecraft:iron_chain", 1, slot=4),
        item_stack_nbt("minecraft:lantern", 1, slot=5, name={"text": "Tamsin's Lantern", "italic": False, "color": "gold"},
                       lore=["Brass, dented, polished at the handle.", "It burns something that isn't oil."],
                       custom={"pm": {"item": "tamsin_lantern"}}),
        item_stack_nbt("minecraft:map", 1, slot=6),
        item_stack_nbt("minecraft:apple", 2, slot=7),
    ]))
    poi.add("landing.camp_chest", *W(cx0 + 6, F, cz0 + 2))
    b.set(cx0 + 4, F + 2, cz0 + 5, "lantern[hanging=true]")
    # campfire circle
    fx, fz = cx0 + 4, cz0 + 9
    b.set(fx, F, fz, "campfire[lit=true,facing=south,signal_fire=false]")
    b.set(fx - 2, F, fz, "stripped_spruce_log[axis=z]")
    b.set(fx + 2, F, fz, "stripped_spruce_log[axis=z]")
    b.set(fx, F, fz + 2, "stripped_spruce_log[axis=x]")
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.set(fx + dx, F - 1, fz + dz, "cobblestone")
    b.set(cx0 + 9, F, cz0 + 6, "crafting_table")
    b.set(cx0 + 9, F, cz0 + 4, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:paper", 6, slot=0), item_stack_nbt("minecraft:compass", 1, slot=1)]))
    # survey tripod (theodolite prop)
    tx, tz = cx0 - 1, cz0 + 8
    b.set(tx, F, tz, "spruce_fence"); b.set(tx, F + 1, tz, "spruce_fence"); b.set(tx, F + 2, tz, "lightning_rod[facing=up,powered=false,waterlogged=false]")
    b.set(tx, F + 3, tz, "red_banner[rotation=0]")
    poi.box("landing.camp", *W(cx0 - 2, F - 1, cz0 - 1), *W(cx0 + 10, F + 6, cz0 + 12))

    # --- signpost at the north exit -------------------------------------------------------------
    sx = int(road_x(3)) + 3
    b.set(sx, F, 3, "spruce_fence")
    b.set(sx, F + 1, 3, "spruce_sign[rotation=8,waterlogged=false]", sign_nbt(["HOLLIN", "^ north ^", "", "(half a league)"], kind="minecraft:sign"))

    # --- scenery: low walls, boulders, eyeblossoms -------------------------------------------------
    for lz in range(30, 46):
        if rng.random() < 0.8:
            b.set(17, F, lz, "mossy_cobblestone_wall" if rng.random() < 0.5 else "cobblestone_wall")
    for (bx, bz) in ((3, 40), (40, 30), (44, 44), (2, 8)):
        for dx in range(-1, 2):
            for dz in range(-1, 2):
                if rng.random() < 0.7:
                    b.set(bx + dx, F, bz + dz, rng.choice(["andesite", "cobblestone", "mossy_cobblestone", "stone"]))
        b.set(bx, F + 1, bz, "andesite")
    for _ in range(30):
        x, z = rng.randrange(1, SIZE[0] - 1), rng.randrange(1, SIZE[2] - 1)
        if b.get(x, F, z) is None and b.get(x, F - 1, z) is None:
            b.set(x, F - 1, z, "grass_block[snowy=false]")
            b.set(x, F, z, rng.choice(["open_eyeblossom", "closed_eyeblossom", "short_grass", "pale_moss_carpet[bottom=true,east=none,north=none,south=none,west=none]"]))

    autoconnect(b)

    # --- points of interest ---------------------------------------------------------------------
    poi.add("landing.spawn", 0, 86, 352, yaw=180.0)
    poi.box("landing.area", *W(0, F - 1, 0), *W(SIZE[0] - 1, F + 12, SIZE[2] - 1))
    poi.box("landing.waystation", *W(x0, F, z0), *W(x1, F + 6, z1))
    poi.add("landing.figure", 12, 86, 300)
    return [Piece("main", b, ORIGIN)]
