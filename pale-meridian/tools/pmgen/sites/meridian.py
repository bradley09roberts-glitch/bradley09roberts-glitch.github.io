"""The Meridian — the Keeper's island observatory in Vellmere, and the causeway (Chapter 4 / finale).

World-coordinate builder. The island plateau is at y=68 (first air). The Lens Gallery on top of the
tower is the final arena. The Long Chart in the Chart Room is a floor mosaic of the valley computed
from layout.json, with the Deepcut left blank.
"""
from __future__ import annotations

import json
import math
import random

from .. import poi
from ..buildkit import door, gable_roof
from ..houses import sign
from ..paths import LAYOUT
from ..structure import Build, container_nbt, item_stack_nbt, nbt
from . import Piece
from .glassworks import WB

SITE = "meridian"
ORIGIN = (-48, 48, -48)
SIZE = (80, 68, 80)          # x -48..31, y 48..115, z -48..31
C_ORIGIN = (-16, 48, 12)
C_SIZE = (13, 26, 92)        # causeway x -16..-4, y 48..73, z 12..103
Y = 68
TX, TZ = -16, -14            # tower centre
GY = 96                      # gallery floor level (first air)

MOSAIC = {
    "landing": "yellow_terracotta", "hollin": "orange_terracotta", "aldercross": "lime_terracotta",
    "glassworks": "red_terracotta", "deepcut": "white_concrete", "mere": "light_blue_concrete", "fen": "green_terracotta",
    "westwood": "brown_terracotta", "southmoor": "light_gray_terracotta", "rim": "gray_terracotta", "outside": "polished_andesite",
}


def _district(x: float, z: float, L: dict) -> str:
    """Approximation of ValleyTerrain.district() for the chart (no boundary jitter)."""
    r = math.hypot(x - L["valley"]["center"][0], z - L["valley"]["center"][1])
    if r > L["valley"]["biome_radius"]:
        return "outside"
    c = L["cliff"]
    cliff_z = c["base_z"] + c["curve"] * (x - c["x0"]) ** 2
    d = L["districts"]
    if z < cliff_z - 3 and math.hypot(x - d["deepcut_seed"][0], z - d["deepcut_seed"][1]) < d["deepcut_radius"]:
        return "deepcut"
    lk = L["lake"]
    if ((x - lk["center"][0]) / lk["radius_x"]) ** 2 + ((z - lk["center"][1]) / lk["radius_z"]) ** 2 < 1.02:
        return "mere"
    if r >= d["rim_radius"]:
        return "rim"
    best, score = "hollin", 1e18
    for name, (sx, sz, wgt) in d["seeds"].items():
        s = math.hypot(x - sx, z - sz) / wgt
        if s < score:
            best, score = name, s
    return best


def build() -> list[Piece]:
    rng = random.Random(0x3E121D)
    L = json.loads(LAYOUT.read_text())
    b = Build(*SIZE)
    w = WB(b, ORIGIN)

    def wsign(x, y, z, facing, lines, wood="dark_oak"):
        sign(b, x - ORIGIN[0], y - ORIGIN[1], z - ORIGIN[2], facing, lines, wood)

    # ------------------------------------------------------------ paths across the island
    for z in range(-6, 20):
        for dx in (-1, 0, 1):
            w.set(-10 + dx, Y - 1, z, rng.choice(["gravel", "dirt_path", "cobblestone"]))
            w.soft_air(-10 + dx, Y, z, -10 + dx, Y + 3, z)

    # ------------------------------------------------------------ the tower
    R_OUT = 7
    for y in range(Y - 1, GY - 1):
        for x in range(TX - R_OUT - 1, TX + R_OUT + 2):
            for z in range(TZ - R_OUT - 1, TZ + R_OUT + 2):
                d = math.hypot(x - TX, z - TZ)
                if d <= R_OUT + 0.5:
                    if y == Y - 1:
                        w.set(x, y, z, "polished_diorite" if (x + z) % 2 else "calcite")
                    elif d >= R_OUT - 0.5:
                        band = (y - Y) % 8 == 7
                        win = (y - Y) % 8 in (3, 4) and (x == TX or z == TZ)
                        w.set(x, y, z, "glass_pane" if win else "polished_diorite" if band else "calcite")
                    else:
                        w.set(x, y, z, "air")
    # doors: south (outside) and east (to the Chart Room)
    for y in (Y, Y + 1):
        w.set(TX, y, TZ + R_OUT, "air")
        w.set(TX + R_OUT, y, TZ, "air")
    w.set(TX, Y + 2, TZ + R_OUT, "polished_diorite"); w.set(TX + R_OUT, Y + 2, TZ, "polished_diorite")
    # square spiral stair (two wide, 2x2 landings in the corners): 28 steps from the floor to the gallery
    x0, z0, x1, z1 = TX - 4, TZ - 4, TX + 4, TZ + 4
    corner = {"SW": [(x0, z1), (x0 + 1, z1), (x0, z1 - 1), (x0 + 1, z1 - 1)],
              "SE": [(x1, z1), (x1 - 1, z1), (x1, z1 - 1), (x1 - 1, z1 - 1)],
              "NE": [(x1, z0), (x1 - 1, z0), (x1, z0 + 1), (x1 - 1, z0 + 1)],
              "NW": [(x0, z0), (x0 + 1, z0), (x0, z0 + 1), (x0 + 1, z0 + 1)]}
    edges = [("east", [[(x, z1), (x, z1 - 1)] for x in range(x0 + 2, x1 - 1)], "SE"),
             ("north", [[(x1, z), (x1 - 1, z)] for z in range(z1 - 2, z0 + 1, -1)], "NE"),
             ("west", [[(x, z0), (x, z0 + 1)] for x in range(x1 - 2, x0 + 1, -1)], "NW"),
             ("south", [[(x0, z), (x0 + 1, z)] for z in range(z0 + 2, z1 - 1)], "SW")]
    STEPS = GY - Y            # 28
    k, e = 0, 0
    top_cells, top_landing, stair_cells = [], [], set()
    while k < STEPS:
        facing, cells, next_corner = edges[e % 4]
        for pair in cells:
            if k < STEPS:
                for (cx_, cz_) in pair:
                    w.set(cx_, Y + k, cz_, f"polished_diorite_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]")
                    if Y + k == GY - 1:
                        stair_cells.add((cx_, cz_))       # the last step sits at floor level: keep it clear
                    if GY - 4 <= Y + k <= GY - 2:          # the gallery floor above these must stay open (headroom)
                        top_cells.append((cx_, cz_))
                k += 1
            else:
                for (cx_, cz_) in pair:           # rest of the edge becomes the top landing, flush with the gallery floor
                    w.set(cx_, GY - 1, cz_, "polished_diorite")
                    top_landing.append((cx_, cz_))
        if k < STEPS:
            for (cx_, cz_) in corner[next_corner]:
                w.set(cx_, Y + k - 1, cz_, "polished_diorite")
                if GY - 4 <= Y + k - 1 <= GY - 2:
                    top_cells.append((cx_, cz_))
        else:
            if not top_landing:
                for (cx_, cz_) in corner[next_corner]:
                    w.set(cx_, GY - 1, cz_, "polished_diorite")
                    top_landing.append((cx_, cz_))
        e += 1
    poi.box("meridian.tower_base", TX - 6, Y - 1, TZ - 6, TX + 6, Y + 4, TZ + 6)

    # ------------------------------------------------------------ the Lens Gallery (arena) on top
    GR = 12
    for y in range(GY - 5, GY):
        rr = R_OUT + (y - (GY - 5)) * (GR - R_OUT) / 4.0
        for x in range(TX - GR - 1, TX + GR + 2):
            for z in range(TZ - GR - 1, TZ + GR + 2):
                d = math.hypot(x - TX, z - TZ)
                if R_OUT - 0.5 <= d <= rr + 0.5 or (y == GY - 1 and d <= GR + 0.5):
                    if w.get(x, y, z) is None or w.get(x, y, z).name in ("minecraft:air", "minecraft:calcite", "minecraft:glass_pane"):
                        w.set(x, y, z, "polished_diorite" if y == GY - 1 and (int(d) % 3 == 0) else "calcite")
    # open the floor above the top flight, rail it off
    for (cx_, cz_) in top_cells:
        w.set(cx_, GY - 1, cz_, "air")
    for (cx_, cz_) in top_cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = cx_ + dx, cz_ + dz
            if (nx, nz) not in top_cells and (nx, nz) not in top_landing and (nx, nz) not in stair_cells:
                w.set(nx, GY, nz, "diorite_wall")
    for y in range(GY, GY + 8):
        for x in range(TX - GR, TX + GR + 1):
            for z in range(TZ - GR, TZ + GR + 1):
                if math.hypot(x - TX, z - TZ) <= GR - 0.5 and w.get(x, y, z) is None:
                    w.set(x, y, z, "air")
    # parapet
    for x in range(TX - GR - 1, TX + GR + 2):
        for z in range(TZ - GR - 1, TZ + GR + 2):
            d = math.hypot(x - TX, z - TZ)
            if GR - 0.5 < d <= GR + 0.5:
                w.set(x, GY, z, "diorite_wall")
                w.set(x, GY - 1, z, "polished_diorite")
    # the Great Lens: dais, gold pyramid under a (later) beacon, vertical stillglass disc
    for x in range(TX - 2, TX + 3):
        for z in range(TZ - 2, TZ + 3):
            if math.hypot(x - TX, z - TZ) <= 2.3:
                w.set(x, GY, z, "polished_diorite_slab[type=bottom,waterlogged=false]")
    for x in range(TX - 1, TX + 2):
        for z in range(TZ - 1, TZ + 2):
            w.set(x, GY, z, "gold_block")                # a one-level beacon base; the beacon is set when the Lens burns
    lens = []
    for dy in range(-3, 4):
        for dz in range(-3, 4):
            if dy * dy + dz * dz <= 10:
                p = (TX, GY + 5 + dy, TZ + dz)
                rim = dy * dy + dz * dz >= 9 and dz != 0
                w.set(*p, "waxed_cut_copper" if rim else "light_blue_stained_glass")
                lens.append(list(p))
    for y in range(GY + 1, GY + 2):
        w.set(TX, y, TZ, "air")
    w.set(TX, GY + 1, TZ - 3, "waxed_copper_block"); w.set(TX, GY + 1, TZ + 3, "waxed_copper_block")
    for y in range(GY + 1, GY + 5):
        w.set(TX, y, TZ - 4, "waxed_cut_copper"); w.set(TX, y, TZ + 4, "waxed_cut_copper")
    poi.add("meridian.lens", TX, GY + 5, TZ, lens=lens, beacon=[TX, GY + 1, TZ])
    poi.add("meridian.cradle", TX + 2.5, GY + 0.5, TZ + 0.5)
    # relay lamps (N, E, S, W)
    relays = []
    for (dx, dz) in ((0, -9), (9, 0), (0, 9), (-9, 0)):
        x, z = TX + dx, TZ + dz
        w.set(x, GY, z, "diorite_wall")
        w.set(x, GY + 1, z, "diorite_wall")
        w.set(x, GY + 2, z, "waxed_copper_bulb[lit=true,powered=false]")
        w.set(x, GY + 3, z, "polished_diorite_slab[type=bottom,waterlogged=false]")
        relays.append([x, GY + 2, z])
    poi.add("meridian.arena", TX + 0.5, GY, TZ + 0.5, relays=relays, radius=GR - 1)
    spawns = []
    for a in range(0, 360, 45):
        spawns.append([round(TX + 10 * math.cos(math.radians(a + 22.5))), GY, round(TZ + 10 * math.sin(math.radians(a + 22.5)))])
    poi.add("meridian.arena_spawns", TX, GY, TZ, spawns=spawns)

    # ------------------------------------------------------------ the Chart Room (east annex) with the Long Chart
    ax0, az0, ax1, az1 = -6, -22, 10, -6
    for y in range(Y - 1, Y + 7):
        for x in range(ax0, ax1 + 1):
            for z in range(az0, az1 + 1):
                edge = x in (ax0, ax1) or z in (az0, az1)
                if y == Y - 1:
                    w.set(x, y, z, "polished_deepslate")
                elif y == Y + 6:
                    w.set(x, y, z, "dark_oak_planks")
                elif edge:
                    col = (x - ax0) % 4 == 0 and z in (az0, az1) or (z - az0) % 4 == 0 and x in (ax0, ax1)
                    w.set(x, y, z, "polished_diorite" if col else ("calcite" if y != Y + 3 or (x + z) % 3 else "glass_pane"))
                else:
                    w.set(x, y, z, "air")
    gable_roof(b, ax0 - ORIGIN[0], az0 - ORIGIN[2], ax1 - ORIGIN[0], az1 - ORIGIN[2], Y + 7 - ORIGIN[1], "deepslate_tile", ridge_axis="z", overhang=1, fill_gable="calcite")
    # corridor from the tower door
    for x in range(TX + R_OUT, ax0 + 1):
        for y in (Y, Y + 1):
            w.set(x, y, TZ, "air")
        w.set(x, Y - 1, TZ, "polished_deepslate")
        w.set(x, Y + 2, TZ, "calcite")
        for dz in (-1, 1):
            for y in range(Y - 1, Y + 3):
                if w.get(x, y, TZ + dz) is None:
                    w.set(x, y, TZ + dz, "calcite")
    # the mosaic: 11 x 11 cells, one per 80 blocks of the valley, north up
    mcx, mcz = 2, -14
    cells = {}
    for i in range(-5, 6):
        for j in range(-5, 6):
            dist = _district(i * 80.0, j * 80.0, L)
            if i == 0 and j == 0:
                dist = "meridian"
            cells[(i, j)] = dist
            w.set(mcx + i, Y - 1, mcz + j, "chiseled_quartz_block" if dist == "meridian" else MOSAIC[dist])
    for i in range(-6, 7):
        for j in (-6, 6):
            w.set(mcx + i, Y - 1, mcz + j, "polished_blackstone")
            w.set(mcx + j, Y - 1, mcz + i, "polished_blackstone")
    blank = [[mcx + i, Y - 1, mcz + j] for (i, j), d in cells.items() if d == "deepcut"]
    frame = [[mcx + i, Y - 1, mcz + j] for i in range(-6, 7) for j in (-6, 6)] + [[mcx + j, Y - 1, mcz + i] for i in range(-5, 6) for j in (-6, 6)]
    poi.add("meridian.chart", mcx, Y, mcz, blank=blank, frame=frame)
    poi.add("meridian.chart_table", mcx + 0.5, Y, mcz - 6.5, yaw=0.0)
    w.set(mcx, Y, mcz - 7, "lectern[facing=south,has_book=false,powered=false]")
    # Hesper's desk and chair in the dark
    w.set(ax0 + 2, Y, az0 + 2, "dark_oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]")
    w.set(ax0 + 3, Y, az0 + 2, "dark_oak_fence"); w.set(ax0 + 3, Y + 1, az0 + 2, "dark_oak_pressure_plate[powered=false]")
    w.set(ax0 + 3, Y, az0 + 3, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [
        item_stack_nbt("minecraft:paper", 5, slot=0), item_stack_nbt("minecraft:ink_sac", 2, slot=1)]))
    for z in range(az0 + 1, az1, 3):
        w.set(ax1 - 1, Y, z, "bookshelf"); w.set(ax1 - 1, Y + 1, z, "bookshelf")
    poi.add("meridian.hesper", ax0 + 2.5, Y, az0 + 3.5, yaw=-90.0)
    poi.box("meridian.chartroom", ax0, Y - 1, az0, ax1, Y + 5, az1)
    for yaw_, name, (px, pz) in ((0.0, "tamsin", (mcx - 4, mcz + 6)), (0.0, "brannoc", (mcx + 4, mcz + 6)), (-90.0, "odile", (mcx + 6, mcz))):
        poi.add(f"meridian.{name}", px + 0.5, Y, pz + 0.5, yaw=yaw_ + 180.0)
    door(b, 2 - ORIGIN[0], Y - ORIGIN[1], az1 - ORIGIN[2], "south", "dark_oak")

    # ------------------------------------------------------------ the Keeper's quarters (south-west)
    qx0, qz0, qx1, qz1 = -32, -4, -22, 4
    for y in range(Y - 1, Y + 4):
        for x in range(qx0, qx1 + 1):
            for z in range(qz0, qz1 + 1):
                edge = x in (qx0, qx1) or z in (qz0, qz1)
                if y == Y - 1:
                    w.set(x, y, z, "spruce_planks")
                elif edge:
                    corner = x in (qx0, qx1) and z in (qz0, qz1)
                    w.set(x, y, z, "stripped_birch_log[axis=y]" if corner else ("glass_pane" if y == Y + 1 and (x + z) % 4 == 0 else "calcite"))
                else:
                    w.set(x, y, z, "air")
    gable_roof(b, qx0 - ORIGIN[0], qz0 - ORIGIN[2], qx1 - ORIGIN[0], qz1 - ORIGIN[2], Y + 4 - ORIGIN[1], "dark_oak", ridge_axis="x", fill_gable="calcite")
    door(b, qx1 - ORIGIN[0], Y - ORIGIN[1], 0 - ORIGIN[2], "east", "birch")
    w.set(qx0 + 1, Y, qz0 + 1, "white_bed[facing=east,part=foot,occupied=false]")
    w.set(qx0 + 2, Y, qz0 + 1, "white_bed[facing=east,part=head,occupied=false]")
    w.set(qx0 + 1, Y, qz1 - 1, "white_bed[facing=east,part=foot,occupied=false]")
    w.set(qx0 + 2, Y, qz1 - 1, "white_bed[facing=east,part=head,occupied=false]")
    drawer = (qx0 + 5, Y, qz0 + 1)
    w.set(*drawer, "chest[facing=south,type=single,waterlogged=false]", container_nbt("minecraft:chest", [
        item_stack_nbt("minecraft:compass", 1, name={"text": "Brass compass", "italic": False},
                       lore=["The needle ignores north and points at the island.", "Engraved inside the lid: FOR TOBIN, WHO ALWAYS FINDS HIS WAY HOME."],
                       custom={"pm": {"keepsake": 1}}, slot=13),
        item_stack_nbt("minecraft:bread", 3, slot=0)]))
    poi.add("meridian.keepsake.tobin", *drawer)
    w.set(qx0 + 7, Y, qz0 + 1, "bookshelf"); w.set(qx0 + 8, Y, qz0 + 1, "crafting_table")
    w.set(qx0 + 8, Y, qz1 - 1, "furnace[facing=north,lit=false]")
    poi.box("meridian.quarters", qx0, Y - 1, qz0, qx1, Y + 4, qz1)

    # ------------------------------------------------------------ docks and lamps
    for z in range(14, 30):
        for x in (-21, -20, -19):
            w.set(x, 63, z, "spruce_planks")
        if z % 4 == 0:
            w.set(-22, 63, z, "stripped_spruce_log[axis=y]")
            for yy in range(52, 63):
                w.set(-22, yy, z, "stripped_spruce_log[axis=y]")
    for z in range(10, 15):
        for x in (-21, -20, -19):
            yy = 67 - max(0, z - 10)
            w.set(x, yy, z, "spruce_planks")
    b.entity(-17 - ORIGIN[0] + 0.5, 63 - ORIGIN[1], 24 - ORIGIN[2] + 0.5, nbt.Compound({"id": nbt.String("minecraft:oak_boat")}))
    poi.add("meridian.dock", -10.5, Y, 16.5)
    poi.box("meridian.island", -40, 60, -40, 20, 120, 22)
    lamps = []
    for (x, z) in ((-12, 8), (-8, 0), (-19, -3), (-6, -24)):
        w.set(x, Y, z, "diorite_wall")
        w.set(x, Y + 1, z, "dark_oak_fence"); w.set(x, Y + 2, z, "dark_oak_fence")
        w.set(x, Y + 3, z, "waxed_exposed_copper_bulb[lit=false,powered=false]")
        w.set(x, Y + 4, z, "polished_diorite_slab[type=bottom,waterlogged=false]")
        lamps.append([x, Y + 3, z])
    poi.add("meridian.lamps", TX, Y, TZ, lamps=lamps)
    poi.add("meridian.spawn", -10.5, Y, 6.5, yaw=180.0)
    wsign(-12, Y + 1, 12, "south", ["THE MERIDIAN", "Observatory of", "the Long Chart", ""])

    from ..buildkit import autoconnect
    autoconnect(b)

    # ------------------------------------------------------------ the causeway (piece 2)
    cb = Build(*C_SIZE)
    cw = WB(cb, C_ORIGIN)
    gap = range(52, 58)
    deck = {}
    for z in range(14, 102):
        wy = 67 if z < 40 else (66 if z < 80 else 65)
        deck[z] = wy
        for x in (-11, -10, -9):
            if z in gap:
                continue
            cw.set(x, wy, z, "stone_bricks" if (x + z) % 5 else "mossy_stone_bricks")
            for y in range(wy + 1, wy + 4):
                cw.set(x, y, z, "air")
        if z not in gap:
            for x in (-12, -8):
                cw.set(x, wy, z, "stone_bricks")
                cw.set(x, wy + 1, z, "stone_brick_wall")
        if z % 8 == 0 and z not in gap:
            for y in range(50, wy):
                for x in (-11, -10, -9):
                    cw.set(x, y, z, "stone_bricks")
    # steps where the deck changes height
    for z in (39, 79):
        for x in (-11, -10, -9):
            cw.set(x, deck[z], z, f"stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
    parts = [{"pos": [x, deck[z], z], "block": "stone_bricks"} for z in gap for x in (-11, -10, -9)]
    poi.add("meridian.causeway_gap", -10, 66, 54.5, parts=parts)
    clamps = []
    for z in (24, 40, 64, 88):
        cw.set(-12, deck[z] + 2, z, "dark_oak_fence")
        cw.set(-12, deck[z] + 3, z, "waxed_exposed_copper_bulb[lit=false,powered=false]")
        clamps.append([-12, deck[z] + 3, z])
    poi.add("meridian.causeway_lamps", -10, 66, 60, lamps=clamps)
    return [Piece("island", b, ORIGIN), Piece("causeway", cb, C_ORIGIN)]
