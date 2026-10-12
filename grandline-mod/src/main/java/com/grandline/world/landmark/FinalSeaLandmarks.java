package com.grandline.world.landmark;

import com.grandline.world.Island;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import static com.grandline.world.landmark.LandmarkKit.*;

/** New World set pieces, part two: Wano, Onigashima, Baltigo, Egghead and Hachinosu. */
final class FinalSeaLandmarks {

    // ============================================================== Wano: the Flower Capital castle

    /** Stepped eave: an upturned slab flare outside the rectangle, then n rings of stairs stepping inward and up. */
    private static void eave(Builder b, int x1, int z1, int x2, int z2, int y, int n, Block stairs, Block slab) {
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            b.set(x, y, z1 - 1, slabTop(slab));
            b.set(x, y, z2 + 1, slabTop(slab));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, y, z, slabTop(slab));
            b.set(x2 + 1, y, z, slabTop(slab));
        }
        for (int i = 0; i < n; i++) {
            int ax = x1 + i, bx = x2 - i, az = z1 + i, bz = z2 - i, yy = y + 1 + i;
            for (int x = ax; x <= bx; x++) {
                b.set(x, yy, az, stair(stairs, Direction.SOUTH, Half.BOTTOM));
                b.set(x, yy, bz, stair(stairs, Direction.NORTH, Half.BOTTOM));
            }
            for (int z = az + 1; z < bz; z++) {
                b.set(ax, yy, z, stair(stairs, Direction.EAST, Half.BOTTOM));
                b.set(bx, yy, z, stair(stairs, Direction.WEST, Half.BOTTOM));
            }
        }
    }

    /** White-walled storey with dark posts every 4 blocks and slatted windows. */
    private static void castleStorey(Builder b, int x1, int z1, int x2, int z2, int y1, int y2, Block floor) {
        b.fill(x1, y1 - 1, z1, x2, y1 - 1, z2, floor);
        b.walls(x1, y1, z1, x2, y2, z2, Blocks.WHITE_CONCRETE);
        b.clear(x1 + 1, y1, z1 + 1, x2 - 1, y2, z2 - 1);
        for (int x = x1; x <= x2; x += 4) {
            b.fill(x, y1, z1, x, y2, z1, Blocks.DARK_OAK_LOG);
            b.fill(x, y1, z2, x, y2, z2, Blocks.DARK_OAK_LOG);
        }
        for (int z = z1; z <= z2; z += 4) {
            b.fill(x1, y1, z, x1, y2, z, Blocks.DARK_OAK_LOG);
            b.fill(x2, y1, z, x2, y2, z, Blocks.DARK_OAK_LOG);
        }
        posts(b, x1, y1, z1, x2, y2, z2, Blocks.DARK_OAK_LOG);
        int wy = y1 + 1;
        for (int x = x1 + 2; x <= x2 - 2; x += 4) {
            b.set(x, wy, z1, connected(Blocks.DARK_OAK_FENCE, Direction.Axis.X));
            b.set(x, wy, z2, connected(Blocks.DARK_OAK_FENCE, Direction.Axis.X));
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 4) {
            b.set(x1, wy, z, connected(Blocks.DARK_OAK_FENCE, Direction.Axis.Z));
            b.set(x2, wy, z, connected(Blocks.DARK_OAK_FENCE, Direction.Axis.Z));
        }
    }

    private static void stoneLantern(Builder b, int x, int z) {
        b.set(x, 1, z, Blocks.STONE_BRICK_WALL);
        b.set(x, 2, z, Blocks.STONE_BRICKS);
        b.set(x, 3, z, lantern(Blocks.LANTERN, false));
        b.set(x, 4, z, Blocks.STONE_BRICK_SLAB);
    }

    /** A many-tiered castle keep on a battered stone base, behind a gatehouse and a courtyard of cherry trees; an
     *  escape passage hides under the audience hall. */
    static void flowerCapitalCastle(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        Block roofS = Blocks.DEEPSLATE_TILE_STAIRS, roofSl = Blocks.DEEPSLATE_TILE_SLAB, roofB = Blocks.DEEPSLATE_TILES;
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 44);
        b.fill(c - 1, 0, 0, c + 1, 0, 13, Blocks.GRAVEL);
        // ---- gatehouse (z 1..5)
        b.fill(15, 1, 1, 18, 4, 5, Blocks.STONE_BRICKS);
        b.fill(26, 1, 1, 29, 4, 5, Blocks.STONE_BRICKS);
        castleStorey(b, 15, 1, 29, 5, 5, 8, Blocks.DARK_OAK_PLANKS);
        b.fill(15, 9, 1, 29, 9, 5, Blocks.DARK_OAK_PLANKS);
        flaredRoof(b, 15, 1, 29, 5, 9, roofS, roofSl, roofB);
        b.set(17, 13, 3, Blocks.GOLD_BLOCK);
        b.set(27, 13, 3, Blocks.GOLD_BLOCK);
        b.ladderUp(19, 1, 3, 4, Direction.EAST);
        b.fill(18, 1, 3, 18, 4, 3, Blocks.STONE_BRICKS);
        b.clear(19, 4, 3, 19, 4, 3);
        b.chest(27, 5, 3, Direction.WEST, Buildings.LOOT_MARINE);
        b.hanging(22, 8, 3, Blocks.LANTERN);
        npc(b, "guard", 23, 5, 3);
        npc(b, "guard", 19, 1, 7);
        npc(b, "guard", 25, 1, 7);
        // ---- courtyard: cherry trees, stone lanterns, a koi pond
        for (int[] t : new int[][]{{6, 9}, {38, 9}, {5, 30}, {39, 30}}) tree(b, t[0], 1, t[1], Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, 5, 3, t[0] * 7 + t[1], null);
        for (int[] l : new int[][]{{19, 9}, {25, 9}, {19, 12}, {25, 12}}) stoneLantern(b, l[0], l[1]);
        b.fill(29, -1, 7, 35, -1, 11, Blocks.CLAY);
        b.fill(29, 0, 7, 35, 0, 11, Blocks.WATER);
        for (int[] p : new int[][]{{30, 8}, {33, 10}, {34, 7}}) b.set(p[0], 1, p[1], Blocks.LILY_PAD);
        npc(b, "resident", 12, 1, 10);
        // ---- the battered stone base (x 8..36, z 14..42), top at y=6
        for (int y = 1; y <= 6; y++) {
            int in = (y - 1) / 2;
            for (int x = 8 + in; x <= 36 - in; x++)
                for (int z = 14 + in; z <= 42 - in; z++) {
                    int r = b.rand(x * 7 + y * 131 + z * 13, 6);
                    b.set(x, y, z, r == 0 ? Blocks.MOSSY_STONE_BRICKS : r == 1 ? Blocks.ANDESITE : r == 2 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS);
                }
        }
        stairsUp(b, c + 1, 1, 11, Direction.SOUTH, 6, Blocks.STONE_BRICK_STAIRS, 3, Blocks.STONE_BRICKS);
        // ---- keep: four storeys with eaves between them
        castleStorey(b, 12, 18, 32, 38, 7, 12, Blocks.BAMBOO_MOSAIC);
        b.fill(12, 13, 18, 32, 13, 38, Blocks.DARK_OAK_PLANKS);
        eave(b, 12, 18, 32, 38, 13, 3, roofS, roofSl);
        castleStorey(b, 15, 21, 29, 35, 14, 18, Blocks.DARK_OAK_PLANKS);
        b.fill(15, 19, 21, 29, 19, 35, Blocks.DARK_OAK_PLANKS);
        eave(b, 15, 21, 29, 35, 19, 3, roofS, roofSl);
        castleStorey(b, 18, 24, 26, 32, 20, 23, Blocks.DARK_OAK_PLANKS);
        b.fill(18, 24, 24, 26, 24, 32, Blocks.DARK_OAK_PLANKS);
        eave(b, 18, 24, 26, 32, 24, 2, roofS, roofSl);
        castleStorey(b, 20, 26, 24, 30, 25, 27, Blocks.DARK_OAK_PLANKS);
        b.fill(20, 28, 26, 24, 28, 30, Blocks.DARK_OAK_PLANKS);
        flaredRoof(b, 20, 26, 24, 30, 28, roofS, roofSl, roofB);
        b.set(21, 31, 28, Blocks.GOLD_BLOCK);
        b.set(23, 31, 28, Blocks.GOLD_BLOCK);
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 7, 18, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        // storey 1: audience hall with the lord's dais and golden screens
        carpet(b, c - 1, 7, 19, c + 1, 33, Blocks.RED_CARPET);
        b.fill(16, 7, 34, 28, 7, 37, slabTop(Blocks.BIRCH_SLAB));
        b.fill(17, 8, 37, 27, 10, 37, Blocks.YELLOW_WOOL);
        for (int x = 17; x <= 27; x += 2) b.set(x, 9, 37, Blocks.GOLD_BLOCK);
        b.set(c, 8, 36, Blocks.RED_CARPET);
        for (int z = 22; z <= 32; z += 3) {
            b.set(15, 7, z, Blocks.RED_CARPET);
            b.set(29, 7, z, Blocks.RED_CARPET);
        }
        for (int z = 22; z <= 34; z += 6) {
            b.hanging(16, 12, z, Blocks.LANTERN);
            b.hanging(28, 12, z, Blocks.LANTERN);
        }
        npc(b, "noble", c, 8, 35);
        npc(b, "guard", 18, 7, 30);
        npc(b, "guard", 26, 7, 30);
        stairsUp(b, 27, 7, 36, Direction.NORTH, 7, Blocks.DARK_OAK_STAIRS, 2, Blocks.DARK_OAK_PLANKS);
        // storey 2: armoury
        for (int x = 17; x <= 23; x += 2) {
            b.set(x, 14, 34, Blocks.DARK_OAK_FENCE);
            b.set(x, 15, 34, connected(Blocks.IRON_BARS, Direction.Axis.X));
        }
        b.set(16, 14, 22, Blocks.GRINDSTONE);
        b.set(17, 14, 22, Blocks.SMITHING_TABLE);
        b.barrel(16, 14, 24, Buildings.LOOT_SMITH);
        b.barrel(16, 14, 25, Buildings.LOOT_SMITH);
        b.hanging(22, 18, 26, Blocks.LANTERN);
        npc(b, "master", 20, 14, 27);
        stairsUp(b, 24, 14, 33, Direction.NORTH, 6, Blocks.DARK_OAK_STAIRS, 2, Blocks.DARK_OAK_PLANKS);
        // storey 3: the lord's private chamber
        b.bed(19, 20, 29, Blocks.RED_BED, Direction.SOUTH);
        b.chest(19, 20, 25, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(23, 20, 25, facing(Blocks.LECTERN, Direction.SOUTH));
        b.set(21, 20, 27, Blocks.FLOWER_POT);
        b.set(20, 20, 27, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
        b.hanging(22, 23, 28, Blocks.LANTERN);
        ladder(b, 22, 20, 29, 5, Direction.NORTH, Blocks.DARK_OAK_PLANKS);
        // storey 4: lookout
        b.hanging(22, 27, 27, Blocks.LANTERN);
        npc(b, "guard", 21, 25, 28);
        // ---- the escape passage beneath the audience hall
        shell(b, 9, -5, 16, 15, -1, 22, Blocks.STONE_BRICKS);
        hatch(b, 14, 6, 20, -4, Direction.WEST, Blocks.SPRUCE_TRAPDOOR, Blocks.STONE_BRICKS);
        b.set(14, 7, 20, Blocks.RED_CARPET);
        b.chest(10, -4, 17, Direction.EAST, Buildings.LOOT_TREASURE);
        b.barrel(10, -4, 21, Buildings.LOOT_HOUSE);
        b.fill(9, -4, 19, 9, -3, 19, Blocks.GRAVEL);
        b.hanging(12, -2, 19, Blocks.LANTERN);
        mark(b, "treasure", 10, -4, 17);
        b.marker("arc_anchor", c, 1, 6, tag("arc", "flower_capital"));
    }

    // ============================================================== Wano: the waterfall gate

    /** The gate where ships ride the falls into the country: a torii over the plunge pool, a guard house, a shrine on
     *  the terrace above, and a cave hidden behind the waterfall. */
    static void waterfallGate(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 11; // 25 x 23
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 28);
        // terrace (z 11..22) up to y=8
        for (int x = 0; x < w; x++)
            for (int z = 11; z < d; z++)
                for (int y = 1; y <= 8; y++) {
                    int r = b.rand(x * 7 + y * 131 + z * 13, 5);
                    b.set(x, y, z, y == 8 ? Blocks.GRASS_BLOCK : r == 0 ? Blocks.MOSSY_COBBLESTONE : r == 1 ? Blocks.ANDESITE : Blocks.STONE);
                }
        // the river on the terrace and the falls
        b.fill(c - 2, 6, 11, c + 2, 6, d - 1, Blocks.STONE);
        b.fill(c - 2, 7, 11, c + 2, 8, d - 1, Blocks.WATER);
        BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
        b.fill(c - 2, 1, 10, c + 2, 8, 10, falling);
        // plunge pool
        b.fill(4, -4, 2, 18, -4, 10, Blocks.STONE);
        b.fill(4, -3, 2, 18, 0, 10, Blocks.WATER);
        for (int[] p : new int[][]{{6, 4}, {16, 7}, {5, 9}}) b.set(p[0], 1, p[1], Blocks.LILY_PAD);
        // the cave behind the waterfall
        b.clear(c - 2, 1, 11, c + 2, 4, 14);
        b.fill(c - 2, 0, 11, c + 2, 0, 14, Blocks.STONE);
        b.chest(c, 1, 14, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.set(c - 2, 1, 14, Blocks.AMETHYST_CLUSTER);
        b.set(c + 2, 1, 13, Blocks.DECORATED_POT);
        b.hanging(c, 4, 13, Blocks.LANTERN);
        mark(b, "treasure", c, 1, 14);
        // torii over the pool
        for (int px : new int[]{5, 17}) {
            b.set(px, 0, 1, Blocks.POLISHED_BLACKSTONE);
            b.fill(px, 1, 1, px, 7, 1, Blocks.RED_CONCRETE);
        }
        b.fill(4, 8, 1, 18, 8, 1, Blocks.POLISHED_BLACKSTONE);
        b.set(3, 8, 1, Blocks.POLISHED_BLACKSTONE_SLAB);
        b.set(19, 8, 1, Blocks.POLISHED_BLACKSTONE_SLAB);
        b.fill(5, 6, 1, 17, 6, 1, Blocks.RED_CONCRETE);
        b.set(c, 7, 1, Blocks.GOLD_BLOCK);
        // ferryman by the pool
        npc(b, "dockworker", 19, 1, 9);
        // guard house (x 20..24, z 2..8)
        room(b, 20, 0, 2, 24, 8, 3, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS);
        posts(b, 20, 1, 2, 24, 3, 8, Blocks.DARK_OAK_LOG);
        hipRoof(b, 19, 1, 24, 9, 5, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES);
        b.door(20, 1, 5, Blocks.SPRUCE_DOOR, Direction.WEST);
        b.set(24, 2, 5, connected(Blocks.DARK_OAK_FENCE, Direction.Axis.Z));
        b.bed(22, 1, 6, Blocks.WHITE_BED, Direction.SOUTH);
        b.barrel(23, 1, 3, Buildings.LOOT_HOUSE);
        b.hanging(22, 3, 4, Blocks.LANTERN);
        npc(b, "guard", 22, 1, 4);
        npc(b, "guard", 19, 1, 1);
        // stair up the terrace (west)
        stairsUp(b, 3, 1, 11, Direction.SOUTH, 8, Blocks.STONE_BRICK_STAIRS, 2, Blocks.STONE_BRICKS);
        // shrine on the terrace
        int sx1 = 17, sx2 = 21, sz1 = 15, sz2 = 19;
        b.fill(sx1 - 1, 8, sz1 - 1, sx2 + 1, 8, sz2 + 1, Blocks.POLISHED_ANDESITE);
        b.walls(sx1, 9, sz1, sx2, 11, sz2, Blocks.CHERRY_PLANKS);
        b.clear(sx1 + 1, 9, sz1 + 1, sx2 - 1, 11, sz2 - 1);
        posts(b, sx1, 9, sz1, sx2, 11, sz2, Blocks.DARK_OAK_LOG);
        b.fill(sx1, 12, sz1, sx2, 12, sz2, Blocks.CHERRY_PLANKS);
        flaredRoof(b, sx1, sz1, sx2, sz2, 13, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES);
        b.door(19, 9, sz1, Blocks.CHERRY_DOOR, Direction.NORTH);
        b.set(19, 11, 18, bellCeiling());
        b.set(18, 9, 18, candles(Blocks.WHITE_CANDLE, 3));
        b.set(20, 9, 18, Blocks.TINTED_GLASS);
        b.chest(20, 9, 16, Direction.WEST, Buildings.LOOT_SHOP);
        npc(b, "priest", 18, 9, 16);
        tree(b, 5, 9, 17, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, 4, 3, 211, null);
        stoneLantern(sub(b, 0, 8, 0, w, d, Rotation.NONE), 15, 13);
        b.marker("legend", c, 1, 1, tag("story", "waterfall_gate"));
    }

    // ============================================================== Onigashima: Skull Dome

    private static final Block[] BONE = {Blocks.CALCITE, Blocks.BONE_BLOCK, Blocks.WHITE_TERRACOTTA, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ};

    /** The giant skull: a two-block bone shell with horns, eye-socket windows and a toothed mouth for a gate; inside
     *  the banquet floor and stage, a gallery ring, the overlord's throne deck and a vault under the stage. */
    static void skullDome(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 25, cz = 28; // 51 x 51
        double ox = 22, oy = 26, oz = 20, ix = 20, iy = 24, iz = 18;
        site(b, Blocks.BLACKSTONE, Blocks.STONE, 50);
        patchGround(b, 221, 0, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.GRAVEL);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++)
                for (int y = 1; y <= 27; y++) {
                    double vo = sq(x - c) / (ox * ox) + sq(y) / (oy * oy) + sq(z - cz) / (oz * oz);
                    if (vo > 1) continue;
                    double vi = sq(x - c) / (ix * ix) + sq(y) / (iy * iy) + sq(z - cz) / (iz * iz);
                    if (vi < 1) b.set(x, y, z, Blocks.AIR);
                    else b.set(x, y, z, BONE[b.rand(x * 7 + y * 131 + z * 13, BONE.length)]);
                }
        // floor of the hall
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                double v = sq(x - c) / (ix * ix) + sq(z - cz) / (iz * iz);
                if (v <= 1) b.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE : Blocks.DARK_OAK_PLANKS);
            }
        // eye sockets, nose, the mouth gate with teeth
        for (int ex : new int[]{c - 8, c + 8}) {
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = -3; dy <= 3; dy++) {
                    if (dx * dx + dy * dy > 10) continue;
                    for (int z = 6; z <= 14; z++) {
                        double vo = sq(ex + dx - c) / (ox * ox) + sq(15 + dy) / (oy * oy) + sq(z - cz) / (oz * oz);
                        double vi = sq(ex + dx - c) / (ix * ix) + sq(15 + dy) / (iy * iy) + sq(z - cz) / (iz * iz);
                        if (vo <= 1 && vi >= 1) b.set(ex + dx, 15 + dy, z, Blocks.BLACK_STAINED_GLASS);
                    }
                }
        }
        for (int dy = 0; dy <= 2; dy++)
            for (int dx = -2 + dy; dx <= 2 - dy; dx++)
                for (int z = 6; z <= 12; z++) {
                    double vo = sq(dx) / (ox * ox) + sq(10 - dy) / (oy * oy) + sq(z - cz) / (oz * oz);
                    double vi = sq(dx) / (ix * ix) + sq(10 - dy) / (iy * iy) + sq(z - cz) / (iz * iz);
                    if (vo <= 1 && vi >= 1) b.set(c + dx, 10 - dy, z, Blocks.BLACK_CONCRETE);
                }
        b.clear(c - 8, 1, 6, c + 8, 6, 13);
        b.fill(c - 8, 0, 6, c + 8, 0, 13, Blocks.POLISHED_BLACKSTONE);
        for (int x = c - 8; x <= c + 8; x += 2) b.fill(x, 5, 9, x, 6, 9, Blocks.CALCITE);
        b.fill(c - 9, 7, 8, c + 9, 7, 10, Blocks.CALCITE);
        for (int x : new int[]{c - 9, c + 9}) b.fill(x, 1, 8, x, 6, 10, Blocks.CALCITE);
        // horns
        for (int side : new int[]{-1, 1}) {
            for (int t = 0; t <= 10; t++) {
                double hx = c + side * (13 + t * 0.9), hy = 20 + t * 1.3, hz = cz;
                double r = 3 - t * 0.22;
                Block blk = t >= 9 ? Blocks.BLACKSTONE : Blocks.BONE_BLOCK;
                ellipsoid(b, (int) Math.round(hx), (int) Math.round(hy), (int) hz, r, r, r, blk.defaultBlockState(), false, -100);
            }
        }
        b.marker("arc_anchor", c, 1, 4, tag("arc", "skull_island"));
        // ---- the banquet floor: long tables, sake barrels, lanterns
        for (int tz : new int[]{18, 26, 34}) {
            longTableX(b, c - 12, c - 4, 1, tz, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
            longTableX(b, c + 4, c + 12, 1, tz, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        }
        for (int z = 16; z <= 36; z += 4) {
            b.barrel(c - 14, 1, z, Buildings.LOOT_TAVERN);
            b.barrel(c + 14, 1, z, Buildings.LOOT_TAVERN);
        }
        for (int[] l : new int[][]{{c, 20}, {c, 30}, {c - 9, 25}, {c + 9, 25}}) {
            double inside = 1 - sq(l[0] - c) / (ix * ix) - sq(l[1] - cz) / (iz * iz);
            int top = (int) Math.ceil(iy * Math.sqrt(Math.max(0, inside)));
            b.fill(l[0], 9, l[1], l[0], top, l[1], Blocks.CHAIN);
            b.set(l[0], 8, l[1], lantern(Blocks.LANTERN, true));
        }
        npc(b, "pirate", c - 8, 1, 21);
        npc(b, "pirate", c + 7, 1, 23);
        npc(b, "pirate", c - 6, 1, 30);
        npc(b, "pirate", c + 9, 1, 31);
        npc(b, "pirate", c, 1, 15);
        // stage at the back with lights and drums
        b.fill(c - 8, 1, 38, c + 8, 2, 43, Blocks.DARK_OAK_PLANKS);
        b.set(c - 3, 3, 41, Blocks.NOTE_BLOCK);
        b.set(c + 3, 3, 41, Blocks.NOTE_BLOCK);
        b.set(c, 3, 42, Blocks.JUKEBOX);
        for (int x : new int[]{c - 8, c + 8}) {
            b.fill(x, 3, 38, x, 6, 38, Blocks.IRON_BARS);
            b.set(x, 7, 38, Blocks.SEA_LANTERN);
        }
        b.fill(c - 8, 3, 44, c + 8, 8, 44, Blocks.PURPLE_WOOL);
        b.set(c - 4, 6, 43, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        b.set(c + 4, 6, 43, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        stairsUp(b, c + 1, 1, 37, Direction.SOUTH, 1, Blocks.DARK_OAK_STAIRS, 3, null);
        npc(b, "pirate_captain", c, 3, 40);
        // ---- gallery ring at y=10 with ladders up, the throne deck at the back
        double gy = 10, f = 1 - gy * gy / (iy * iy);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                double v = (sq(x - c) / (ix * ix) + sq(z - cz) / (iz * iz)) / f;
                if (v > 1) continue;
                if (v >= 0.75) b.set(x, 10, z, Blocks.DARK_OAK_PLANKS);
                else if (v >= 0.68 && v < 0.75 && z > 12) b.set(x, 11, z, Blocks.DARK_OAK_FENCE);
            }
        b.fill(c - 6, 10, 37, c + 6, 10, 44, Blocks.DARK_OAK_PLANKS);
        for (int x = c - 5; x <= c + 5; x++) if (Math.abs(x - c) > 1) b.set(x, 11, 37, Blocks.DARK_OAK_FENCE);
        for (int side : new int[]{-1, 1}) {
            int lx = c + side * 15;
            b.fill(lx + side, 1, cz, lx + side, 9, cz, Blocks.POLISHED_BLACKSTONE_BRICKS);
            b.ladderUp(lx, 1, cz, 10, side < 0 ? Direction.EAST : Direction.WEST);
            b.clear(lx, 11, cz, lx, 12, cz);
        }
        b.fill(c - 2, 11, 42, c + 2, 11, 44, Blocks.GOLD_BLOCK);
        b.set(c, 12, 43, stair(Blocks.BLACKSTONE_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.fill(c, 12, 44, c, 14, 44, Blocks.RED_WOOL);
        b.set(c - 1, 12, 44, Blocks.WITHER_SKELETON_SKULL);
        b.set(c + 1, 12, 44, Blocks.WITHER_SKELETON_SKULL);
        b.chest(c - 5, 11, 43, Direction.EAST, Buildings.LOOT_TREASURE);
        b.fill(c + 4, 11, 43, c + 5, 11, 44, Blocks.GOLD_BLOCK);
        npc(b, "pirate_captain", c, 11, 41);
        npc(b, "lookout", c - 8, 11, 15);
        // ---- the vault under the stage (hatch on the stage's corner)
        shell(b, c - 5, -7, 38, c + 5, -1, 44, Blocks.POLISHED_BLACKSTONE_BRICKS);
        hatch(b, c + 7, 2, 39, -6, Direction.NORTH, Blocks.DARK_OAK_TRAPDOOR, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.clear(c + 5, -6, 39, c + 7, -5, 39);
        for (int x = c - 4; x <= c + 2; x += 2) b.set(x, -6, 43, Blocks.GOLD_BLOCK);
        b.chest(c - 4, -6, 40, Direction.EAST, Buildings.LOOT_TREASURE);
        b.hanging(c, -2, 41, Blocks.SOUL_LANTERN);
        mark(b, "treasure", c - 4, -6, 40);
    }

    private static double sq(double v) {
        return v * v;
    }

    // ============================================================== Baltigo: the revolutionaries' headquarters

    /** A white stone fortress: curtain wall with corner towers, a drill yard, and a three-floor keep with the war
     *  room, infirmary, barracks, mess, the leader's office and a sealed intelligence archive; a signal mast on top. */
    static void revolutionaryHq(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 41
        Block stone = Blocks.POLISHED_DIORITE, trim = Blocks.CALCITE;
        site(b, Blocks.CALCITE, Blocks.DIORITE, 34);
        // curtain wall and towers
        for (int y = 1; y <= 8; y++) {
            b.walls(2, y, 2, w - 3, y, d - 3, stone);
            b.walls(3, y, 3, w - 4, y, d - 4, stone);
        }
        crenelX(b, 2, w - 3, 9, 2, trim);
        crenelX(b, 2, w - 3, 9, d - 3, trim);
        crenelZ(b, 2, d - 3, 9, 2, trim);
        crenelZ(b, 2, d - 3, 9, w - 3, trim);
        for (int[] t : new int[][]{{4, 4}, {w - 5, 4}, {4, d - 5}, {w - 5, d - 5}}) {
            b.cylinder(t[0], t[1], 1, 12, 3, stone, true);
            b.cylinder(t[0], t[1], 1, 12, 2, Blocks.AIR, false);
            b.cylinder(t[0], t[1], 12, 12, 3, trim, false);
            for (int x = -3; x <= 3; x += 2) {
                b.set(t[0] + x, 13, t[1] - 3, trim);
                b.set(t[0] + x, 13, t[1] + 3, trim);
            }
        }
        b.clear(18, 1, 2, 22, 5, 3);
        line(b, 18, 5, 2, 22, 2, Blocks.IRON_BARS);
        for (int x : new int[]{16, 24}) b.set(x, 6, 1, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        npc(b, "revolutionary", 17, 1, 5);
        npc(b, "revolutionary", 23, 1, 5);
        // drill yard
        for (int x = 7; x <= 15; x += 4) {
            b.set(x, 1, 9, Blocks.HAY_BLOCK);
            b.set(x, 2, 9, Blocks.CARVED_PUMPKIN);
        }
        for (int x = 26; x <= 34; x += 4) b.set(x, 1, 8, Blocks.TARGET);
        b.fill(c, 1, 9, c, 9, 9, Blocks.IRON_BARS);
        b.set(c, 9, 8, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        npc(b, "revolutionary", 10, 1, 12);
        npc(b, "revolutionary", 30, 1, 11);
        // ---- the keep (x 9..31, z 16..35): floors y=0, 6, 12, roof 18
        int x1 = 9, x2 = 31, z1 = 16, z2 = 35;
        for (int f = 0; f < 3; f++) {
            int y0 = f * 6;
            b.fill(x1, y0, z1, x2, y0, z2, f == 0 ? Blocks.POLISHED_ANDESITE : Blocks.BIRCH_PLANKS);
            b.walls(x1, y0 + 1, z1, x2, y0 + 5, z2, stone);
            b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 5, z2 - 1);
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z1, 4, Blocks.GLASS_PANE);
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z2, 4, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x1, 4, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x2, 4, Blocks.GLASS_PANE);
            b.fill(x1, y0 + 5, z1, x2, y0 + 5, z1, trim);
        }
        b.fill(x1, 18, z1, x2, 18, z2, trim);
        crenelX(b, x1, x2, 19, z1, trim);
        crenelX(b, x1, x2, 19, z2, trim);
        crenelZ(b, z1, z2, 19, x1, trim);
        crenelZ(b, z1, z2, 19, x2, trim);
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, z1, Blocks.BIRCH_DOOR, Direction.NORTH);
        // signal mast on the roof
        b.fill(c, 19, 30, c, 30, 30, Blocks.IRON_BARS);
        b.fill(c - 3, 26, 30, c + 3, 26, 30, Blocks.IRON_BARS);
        b.set(c, 31, 30, Blocks.LIGHTNING_ROD);
        b.set(c - 3, 27, 30, facing(Blocks.END_ROD, Direction.UP));
        b.set(c + 3, 27, 30, facing(Blocks.END_ROD, Direction.UP));
        // ground floor: war room (centre), infirmary (west), armoury (east)
        b.fill(15, 1, z1 + 1, 15, 5, z2 - 1, stone);
        b.fill(25, 1, z1 + 1, 25, 5, z2 - 1, stone);
        b.clear(15, 1, 20, 15, 3, 20);
        b.clear(25, 1, 20, 25, 3, 20);
        for (int x = 17; x <= 23; x++)
            for (int z = 24; z <= 28; z++) {
                table(b, x, 1, z, Blocks.DARK_OAK_SLAB);
                int r = b.rand(x * 7 + z, 5);
                b.set(x, 2, z, r < 2 ? Blocks.BLUE_CARPET : r < 4 ? Blocks.LIME_CARPET : Blocks.YELLOW_CARPET);
            }
        for (int x = 17; x <= 23; x += 2) {
            chair(b, x, 1, 23, Blocks.BIRCH_STAIRS, Direction.NORTH);
            chair(b, x, 1, 29, Blocks.BIRCH_STAIRS, Direction.SOUTH);
        }
        b.set(c, 1, 33, Blocks.CARTOGRAPHY_TABLE);
        b.set(c, 3, 34, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        b.hanging(c, 5, 26, Blocks.LANTERN);
        npc(b, "scholar", 18, 1, 31);
        npc(b, "revolutionary", 22, 1, 21);
        for (int z = 18; z <= 33; z += 3) b.bed(11, 1, z, Blocks.WHITE_BED, Direction.WEST);
        b.set(14, 1, 18, Blocks.BREWING_STAND);
        b.set(14, 1, 19, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        b.chest(14, 1, 33, Direction.WEST, Buildings.LOOT_HOUSE);
        b.hanging(12, 5, 26, Blocks.LANTERN);
        npc(b, "doctor", 13, 1, 25);
        for (int z = 18; z <= 32; z += 2) {
            b.set(x2 - 1, 1, z, Blocks.DARK_OAK_FENCE);
            b.set(x2 - 1, 2, z, connected(Blocks.IRON_BARS, Direction.Axis.Z));
        }
        b.set(27, 1, 18, Blocks.ANVIL);
        b.set(28, 1, 18, Blocks.GRINDSTONE);
        b.barrel(27, 1, 33, Buildings.LOOT_SMITH);
        b.barrel(28, 1, 33, Buildings.LOOT_SMITH);
        b.hanging(28, 5, 26, Blocks.LANTERN);
        npc(b, "revolutionary", 27, 1, 26);
        stairsUp(b, 23, 1, 34, Direction.NORTH, 6, Blocks.BIRCH_STAIRS, 2, Blocks.BIRCH_PLANKS);
        // first floor: barracks (west), mess (east)
        for (int z = 18; z <= 33; z += 3) {
            b.bed(11, 7, z, Blocks.GRAY_BED, Direction.WEST);
            b.bed(14, 7, z, Blocks.GRAY_BED, Direction.EAST);
        }
        b.chest(12, 7, 34, Direction.NORTH, Buildings.LOOT_MARINE);
        b.hanging(12, 11, 26, Blocks.LANTERN);
        longTableZ(b, 18, 30, 7, 28, Blocks.BIRCH_SLAB, Blocks.BIRCH_STAIRS, true);
        kitchenX(b, 25, 7, 34, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.hanging(28, 11, 24, Blocks.LANTERN);
        npc(b, "cook", 26, 7, 32);
        npc(b, "revolutionary", 20, 7, 22);
        stairsUp(b, 17, 7, 22, Direction.SOUTH, 6, Blocks.BIRCH_STAIRS, 2, Blocks.BIRCH_PLANKS);
        // top floor: the leader's office and the archive (its back shelves hide a sealed records room)
        carpet(b, 20, 13, 18, 30, 26, Blocks.GRAY_CARPET);
        table(b, 25, 13, 21, Blocks.DARK_OAK_SLAB);
        table(b, 26, 13, 21, Blocks.DARK_OAK_SLAB);
        chair(b, 25, 13, 22, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        b.set(26, 14, 21, candles(Blocks.CANDLE, 2));
        b.set(28, 13, 18, facing(Blocks.LECTERN, Direction.SOUTH));
        b.set(c + 5, 15, z1 + 1, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
        b.chest(30, 13, 25, Direction.WEST, Buildings.LOOT_MARINE);
        b.hanging(25, 17, 22, Blocks.LANTERN);
        npc(b, "revolutionary", 24, 13, 24);
        b.fill(x1 + 1, 13, 29, x2 - 1, 15, 29, Blocks.BOOKSHELF);
        b.clear(c, 13, 29, c, 14, 29);
        b.door(c, 13, 29, Blocks.BIRCH_DOOR, Direction.NORTH);
        for (int z = 31; z <= 33; z += 2) b.fill(x1 + 1, 13, z, 18, 15, z, Blocks.BOOKSHELF);
        b.set(24, 13, 32, facing(Blocks.LECTERN, Direction.NORTH));
        b.hanging(24, 17, 31, Blocks.LANTERN);
        npc(b, "scholar", 26, 13, 31);
        b.fill(x2 - 4, 13, 30, x2 - 4, 15, z2 - 1, Blocks.BOOKSHELF);
        b.set(x2 - 4, 13, 32, facing(Blocks.CHISELED_BOOKSHELF, Direction.WEST));
        b.set(x2 - 4, 14, 32, Blocks.BOOKSHELF);
        b.chest(x2 - 1, 13, 33, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(x2 - 2, 13, 31, facing(Blocks.LECTERN, Direction.WEST));
        b.set(x2 - 2, 15, 33, Blocks.SEA_LANTERN);
        mark(b, "treasure", x2 - 1, 13, 33);
        b.ladderUp(x1 + 1, 13, z1 + 1, 5, Direction.SOUTH);
        b.clear(x1 + 1, 18, z1 + 1, x1 + 1, 18, z1 + 1);
        b.marker("arc_anchor", c, 1, 13, tag("arc", "revolution"));
    }

    // ============================================================== Egghead: the future laboratory

    private static void sentinel(Builder b, int x, int z) {
        b.fill(x - 1, 1, z, x - 1, 3, z, Blocks.IRON_BLOCK);
        b.fill(x + 1, 1, z, x + 1, 3, z, Blocks.IRON_BLOCK);
        b.fill(x - 1, 4, z, x + 1, 6, z, Blocks.IRON_BLOCK);
        b.fill(x - 2, 5, z, x - 2, 6, z, Blocks.IRON_BLOCK);
        b.fill(x + 2, 5, z, x + 2, 6, z, Blocks.IRON_BLOCK);
        b.set(x, 7, z, facing(Blocks.OBSERVER, Direction.SOUTH));
        b.set(x, 8, z, Blocks.LIGHTNING_ROD);
    }

    /** An egg-shaped laboratory dome banded with glass: labs and a robot bay below, the glowing data core above,
     *  the chief scientist's office with a lever-locked vault at the top; robot sentinels guard the door. */
    static void futureLab(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20, cz = 22; // 41 x 41
        double ox = 15, oy = 24, oz = 14;
        site(b, Blocks.LIGHT_GRAY_CONCRETE, Blocks.STONE, 30);
        for (int x = 0; x < w; x += 5) b.fill(x, 0, 0, x, 0, d - 1, Blocks.CYAN_CONCRETE);
        // the egg shell with glass bands
        for (int x = c - 16; x <= c + 16; x++)
            for (int z = cz - 15; z <= cz + 15; z++)
                for (int y = 1; y <= 25; y++) {
                    double vo = sq(x - c) / (ox * ox) + sq(y) / (oy * oy) + sq(z - cz) / (oz * oz);
                    if (vo > 1) continue;
                    double vi = sq(x - c) / sq(ox - 1.3) + sq(y) / sq(oy - 1.3) + sq(z - cz) / sq(oz - 1.3);
                    if (vi < 1) b.set(x, y, z, Blocks.AIR);
                    else b.set(x, y, z, y % 6 == 3 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.WHITE_CONCRETE);
                }
        // floors inside at y=0, 8, 15
        for (int fy : new int[]{0, 8, 15}) {
            double k = 1 - sq(fy + 0.5) / sq(oy - 1.3);
            for (int x = c - 16; x <= c + 16; x++)
                for (int z = cz - 15; z <= cz + 15; z++) {
                    double v = sq(x - c) / sq(ox - 1.3) + sq(z - cz) / sq(oz - 1.3);
                    if (v <= k) b.set(x, fy, z, fy == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.WHITE_CONCRETE);
                }
        }
        // central pillar with the lift ladder
        b.fill(c, 1, cz + 1, c, 20, cz + 1, Blocks.QUARTZ_PILLAR);
        b.ladderUp(c, 1, cz, 16, Direction.NORTH);
        for (int y = 2; y <= 14; y += 4) b.set(c, y, cz + 2, Blocks.SEA_LANTERN);
        // entrance: glass vestibule on the north
        b.clear(c - 2, 1, cz - 14, c + 2, 4, cz - 10);
        b.fill(c - 2, 0, cz - 14, c + 2, 0, cz - 10, Blocks.SMOOTH_QUARTZ);
        b.fill(c - 3, 5, cz - 14, c + 3, 5, cz - 11, Blocks.WHITE_CONCRETE);
        b.door(c - 1, 1, cz - 12, Blocks.IRON_DOOR, Direction.NORTH);
        b.door(c + 1, 1, cz - 12, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c, 1, cz - 12, Blocks.GLASS);
        b.set(c, 2, cz - 12, Blocks.GLASS);
        b.fill(c - 2, 1, cz - 12, c - 2, 4, cz - 12, Blocks.WHITE_CONCRETE);
        b.fill(c + 2, 1, cz - 12, c + 2, 4, cz - 12, Blocks.WHITE_CONCRETE);
        b.set(c - 2, 2, cz - 13, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.NORTH));
        b.set(c + 2, 2, cz - 13, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.NORTH));
        b.set(c - 2, 2, cz - 11, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH));
        b.set(c + 2, 2, cz - 11, button(Blocks.POLISHED_BLACKSTONE_BUTTON, Direction.SOUTH));
        b.fill(c - 1, 3, cz - 12, c + 1, 4, cz - 12, Blocks.LIGHT_BLUE_STAINED_GLASS);
        sentinel(b, c - 6, cz - 16);
        sentinel(b, c + 6, cz - 16);
        for (int[] l : new int[][]{{4, 4}, {36, 4}, {4, 37}, {36, 37}}) {
            b.fill(l[0], 1, l[1], l[0], 3, l[1], Blocks.IRON_BARS);
            b.set(l[0], 4, l[1], Blocks.SEA_LANTERN);
            b.set(l[0], 5, l[1], facing(Blocks.END_ROD, Direction.UP));
        }
        // landing pad
        disc(b, 34, 34, 0, 4.5, Blocks.GRAY_CONCRETE.defaultBlockState());
        ring(b, 34, 34, 0, 3, Blocks.YELLOW_CONCRETE.defaultBlockState());
        npc(b, "guard", c - 4, 1, cz - 18);
        npc(b, "guard", c + 4, 1, cz - 18);
        // ---- level 1: lab benches and the robot bay
        for (int x = c - 9; x <= c - 4; x++) {
            table(b, x, 1, cz - 4, Blocks.SMOOTH_QUARTZ_SLAB);
            table(b, x, 1, cz + 4, Blocks.SMOOTH_QUARTZ_SLAB);
        }
        b.set(c - 8, 2, cz - 4, Blocks.BREWING_STAND);
        b.set(c - 5, 2, cz - 4, Blocks.BREWING_STAND);
        b.set(c - 7, 2, cz + 4, facing(Blocks.END_ROD, Direction.UP));
        b.set(c - 10, 1, cz, Blocks.CRAFTER);
        b.set(c - 10, 1, cz + 1, facing(Blocks.OBSERVER, Direction.EAST));
        for (int k = 0; k < 2; k++) {
            int rx = c + 5 + k * 4, rz = cz + 3;
            b.fill(rx, 1, rz, rx, 2, rz, Blocks.IRON_BLOCK);
            b.set(rx, 3, rz, Blocks.IRON_BLOCK);
            if (k == 0) b.set(rx, 4, rz, facing(Blocks.OBSERVER, Direction.NORTH));
            b.set(rx, 1, rz - 1, Blocks.ANVIL);
        }
        b.set(c + 9, 1, cz - 3, facing(Blocks.PISTON, Direction.UP));
        b.set(c + 8, 1, cz - 3, Blocks.SMITHING_TABLE);
        b.barrel(c + 10, 1, cz - 4, Buildings.LOOT_SMITH);
        for (int[] l : new int[][]{{c - 6, cz}, {c + 6, cz}}) b.set(l[0], 7, l[1], Blocks.SEA_LANTERN);
        npc(b, "scholar", c - 6, 1, cz);
        npc(b, "scholar", c + 6, 1, cz - 1);
        // ---- level 2: the data core
        ellipsoid(b, c, 11, cz + 6, 2.6, 2.6, 2.6, Blocks.PINK_STAINED_GLASS.defaultBlockState(), true, -100);
        b.fill(c - 1, 10, cz + 5, c + 1, 12, cz + 7, Blocks.SEA_LANTERN);
        for (int x = c - 8; x <= c - 4; x += 2) {
            b.set(x, 9, cz - 3, Blocks.NOTE_BLOCK);
            b.set(x, 10, cz - 3, facing(Blocks.OBSERVER, Direction.SOUTH));
            b.set(x, 11, cz - 3, facing(Blocks.END_ROD, Direction.UP));
        }
        b.set(c + 6, 9, cz - 4, Blocks.LODESTONE);
        b.set(c + 7, 9, cz - 4, Blocks.TARGET);
        npc(b, "scholar", c - 6, 9, cz + 1);
        b.set(c + 3, 14, cz - 2, Blocks.SEA_LANTERN);
        // ---- level 3: the chief scientist's office and the lever-locked vault
        table(b, c - 3, 16, cz - 3, Blocks.SMOOTH_QUARTZ_SLAB);
        table(b, c - 2, 16, cz - 3, Blocks.SMOOTH_QUARTZ_SLAB);
        chair(b, c - 3, 16, cz - 2, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        b.set(c - 2, 17, cz - 3, facing(Blocks.END_ROD, Direction.UP));
        b.set(c + 3, 16, cz - 3, facing(Blocks.LECTERN, Direction.SOUTH));
        npc(b, "guard", c + 2, 16, cz - 1);
        // the vault: interior (c+3..c+4, cz+2..cz+3); only the lever beside the door opens it
        b.fill(c + 2, 16, cz + 1, c + 5, 18, cz + 1, Blocks.WHITE_CONCRETE);
        b.fill(c + 2, 16, cz + 4, c + 5, 18, cz + 4, Blocks.WHITE_CONCRETE);
        b.fill(c + 2, 16, cz + 2, c + 2, 18, cz + 3, Blocks.WHITE_CONCRETE);
        b.fill(c + 5, 16, cz + 2, c + 5, 18, cz + 3, Blocks.WHITE_CONCRETE);
        b.fill(c + 2, 19, cz + 1, c + 5, 19, cz + 4, Blocks.WHITE_CONCRETE);
        b.door(c + 3, 16, cz + 1, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c + 4, 17, cz, lever(Direction.NORTH));
        b.set(c + 5, 18, cz, lever(Direction.NORTH));
        b.set(c + 6, 17, cz + 2, lever(Direction.EAST));
        b.chest(c + 4, 16, cz + 3, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.set(c + 3, 16, cz + 3, Blocks.SEA_LANTERN);
        mark(b, "treasure", c + 4, 16, cz + 3);
        b.marker("arc_anchor", c, 1, cz - 16, tag("arc", "future_island"));
    }

    // ============================================================== Hachinosu: the pirate hive

    private static final Block[] HIVE = {Blocks.BLACKSTONE, Blocks.BASALT, Blocks.TUFF, Blocks.BLACKSTONE, Blocks.POLISHED_BASALT};

    /** The pirate island's beehive fortress: a banded rock hive honeycombed with levels (tavern and black market,
     *  barracks and cannon gallery, the captain's lair with a honey-sealed hoard, a lookout), a harbour plaza before it. */
    static void pirateHive(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20, cz = 23; // 41 x 41
        site(b, Blocks.BLACKSTONE, Blocks.STONE, 40);
        patchGround(b, 251, 0, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.GRAVEL);
        // hive shell (2 thick), radius shrinking with height
        for (int y = 1; y <= 33; y++) {
            double r = 15.5 - y * 0.39;
            for (int x = c - 16; x <= c + 16; x++)
                for (int z = cz - 16; z <= cz + 16; z++) {
                    double dd = Math.sqrt(sq(x - c) + sq(z - cz));
                    if (dd > r) continue;
                    if (dd < r - 2.2 && y < 32) {
                        b.set(x, y, z, Blocks.AIR);
                        continue;
                    }
                    b.set(x, y, z, y % 4 == 0 ? Blocks.HONEYCOMB_BLOCK : HIVE[b.rand(x * 7 + y * 131 + z * 13, HIVE.length)]);
                }
        }
        // floors at y=0, 9, 17, 25
        for (int fy : new int[]{9, 17, 25}) {
            double r = 15.5 - fy * 0.39 - 1.5;
            disc(b, c, cz, fy, r, Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
        disc(b, c, cz, 0, 14, Blocks.SPRUCE_PLANKS.defaultBlockState());
        // central pillar with ladders through every floor
        b.fill(c, 1, cz + 1, c, 28, cz + 1, Blocks.HONEYCOMB_BLOCK);
        b.ladderUp(c, 1, cz, 26, Direction.NORTH);
        // windows on each level
        for (int ly : new int[]{3, 12, 20, 27}) {
            double r = 15.5 - ly * 0.39;
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + Math.PI / 6;
                for (double dr = r - 2.5; dr <= r + 0.5; dr += 0.5) {
                    int x = c + (int) Math.round(Math.cos(a) * dr), z = cz + (int) Math.round(Math.sin(a) * dr);
                    b.clear(x, ly, z, x, ly + 1, z);
                }
            }
        }
        // the gate (north)
        for (int z = cz - 16; z <= cz - 11; z++) b.clear(c - 2, 1, z, c + 2, 5, z);
        b.fill(c - 3, 6, cz - 16, c + 3, 6, cz - 12, Blocks.HONEYCOMB_BLOCK);
        for (int x : new int[]{c - 3, c + 3}) b.fill(x, 1, cz - 15, x, 5, cz - 15, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(c, 7, cz - 15, Blocks.SKELETON_SKULL);
        // ---- harbour plaza: flags, cannons, a bounty post
        for (int x = 3; x <= 37; x += 34) {
            b.fill(x, 1, 3, x, 9, 3, Blocks.DARK_OAK_FENCE);
            b.set(x, 9, 4, banner(Blocks.BLACK_WALL_BANNER, Direction.SOUTH));
        }
        for (int x = 8; x <= 32; x += 6) {
            b.set(x, 1, 1, facing(Blocks.DISPENSER, Direction.NORTH));
            b.set(x, 2, 1, Blocks.POLISHED_BLACKSTONE_SLAB);
        }
        b.fill(28, 1, 4, 30, 1, 4, Blocks.BARREL);
        b.marker("bounty_board", 14, 2, 5, new CompoundTag());
        b.fill(13, 1, 5, 15, 1, 5, Blocks.DARK_OAK_FENCE);
        b.fill(13, 2, 5, 15, 3, 5, Blocks.DARK_OAK_PLANKS);
        npc(b, "pirate", 10, 1, 4);
        npc(b, "pirate", 25, 1, 3);
        // ---- level 1 (y 1..8): tavern and black market
        b.fill(c - 9, 1, cz + 4, c - 4, 1, cz + 4, axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
        for (int x = c - 9; x <= c - 5; x += 2) b.barrel(x, 1, cz + 6, Buildings.LOOT_TAVERN);
        npc(b, "bartender", c - 7, 1, cz + 5);
        for (int[] t : new int[][]{{c - 6, cz - 3}, {c + 6, cz - 3}, {c - 3, cz - 7}}) {
            table(b, t[0], 1, t[1], Blocks.DARK_OAK_SLAB);
            b.set(t[0], 2, t[1], candles(Blocks.ORANGE_CANDLE, 2));
            chair(b, t[0] - 1, 1, t[1], Blocks.DARK_OAK_STAIRS, Direction.WEST);
            chair(b, t[0] + 1, 1, t[1], Blocks.DARK_OAK_STAIRS, Direction.EAST);
        }
        for (int[] s : new int[][]{{c + 5, cz + 4}, {c + 9, cz + 1}}) {
            b.fill(s[0], 1, s[1], s[0] + 2, 1, s[1], slabTop(Blocks.SPRUCE_SLAB));
            b.barrel(s[0], 1, s[1] + 1, Buildings.LOOT_SHOP);
            b.set(s[0] + 2, 1, s[1] + 1, Blocks.DECORATED_POT);
        }
        npc(b, "merchant", c + 7, 1, cz + 6);
        b.hanging(c - 5, 8, cz, Blocks.LANTERN);
        b.hanging(c + 5, 8, cz, Blocks.LANTERN);
        npc(b, "pirate", c - 2, 1, cz - 2);
        // ---- level 2 (y 10..16): bunks, armoury, cannon gallery
        for (int k = 0; k < 4; k++) {
            int bx = c - 7 + k * 2;
            b.bed(bx, 10, cz + 3, Blocks.BLACK_BED, Direction.SOUTH);
        }
        b.set(c + 5, 10, cz + 4, Blocks.ANVIL);
        b.set(c + 6, 10, cz + 4, Blocks.GRINDSTONE);
        b.chest(c + 7, 10, cz + 2, Direction.WEST, Buildings.LOOT_SMITH);
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + Math.PI / 6;
            double r = 15.5 - 12 * 0.39 - 3;
            int x = c + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
            Direction out = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? Direction.EAST : Direction.WEST) : (Math.sin(a) > 0 ? Direction.SOUTH : Direction.NORTH);
            b.set(x, 12, z, facing(Blocks.DISPENSER, out));
        }
        b.hanging(c - 4, 16, cz - 2, Blocks.LANTERN);
        npc(b, "pirate", c + 3, 10, cz - 4);
        // ---- level 3 (y 18..24): the captain's lair, a hoard sealed behind honey
        b.fill(c - 2, 18, cz + 3, c + 2, 18, cz + 5, Blocks.GOLD_BLOCK);
        b.set(c, 19, cz + 4, stair(Blocks.BLACKSTONE_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.fill(c, 19, cz + 5, c, 21, cz + 5, Blocks.RED_WOOL);
        b.set(c - 4, 18, cz - 2, Blocks.CARTOGRAPHY_TABLE);
        b.chest(c + 4, 18, cz - 2, Direction.WEST, Buildings.LOOT_TREASURE);
        b.hanging(c - 2, 24, cz - 1, Blocks.LANTERN);
        npc(b, "pirate_captain", c, 19, cz + 3);
        // the hidden hold under the tavern floor (a hatch under a rug by the bar)
        shell(b, c - 5, -5, cz + 3, c - 1, -1, cz + 7, Blocks.POLISHED_BLACKSTONE_BRICKS);
        hatch(b, c - 4, 0, cz + 5, -4, Direction.EAST, Blocks.DARK_OAK_TRAPDOOR, null);
        b.set(c - 4, 1, cz + 5, Blocks.BROWN_CARPET);
        b.chest(c - 2, -4, cz + 6, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(c - 2, -4, cz + 4, Blocks.GOLD_BLOCK);
        b.set(c - 3, -4, cz + 4, Blocks.HONEY_BLOCK);
        b.hanging(c - 3, -2, cz + 5, Blocks.LANTERN);
        mark(b, "treasure", c - 2, -4, cz + 6);
        // ---- level 4 (y 26..): lookout
        npc(b, "lookout", c + 1, 26, cz - 2);
        b.marker("arc_anchor", c, 1, 6, tag("arc", "pirate_island"));
    }

    private FinalSeaLandmarks() {}
}
