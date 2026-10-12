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

/** New World set pieces, part one: Punk Hazard, Dressrosa, Green Bit, Zou and Whole Cake Island. */
final class NewWorldLandmarks {

    // ============================================================== Punk Hazard: the abandoned laboratory

    /** A government research facility split by catastrophe: the west half scorched, the east half frozen solid. */
    static void abandonedLab(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 35
        site(b, Blocks.STONE, Blocks.STONE, 30);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                int r = b.rand(x * 31 + z * 17, 8);
                Block g = x < c ? (r < 3 ? Blocks.BLACKSTONE : r < 5 ? Blocks.BASALT : r < 6 ? Blocks.COARSE_DIRT : Blocks.GRAVEL)
                        : (r < 3 ? Blocks.SNOW_BLOCK : r < 5 ? Blocks.PACKED_ICE : r < 6 ? Blocks.ICE : Blocks.SNOW_BLOCK);
                b.set(x, 0, z, g);
            }
        // scorched debris and smouldering fires (west), ice spikes and drifts (east)
        heap(b, 141, 3, 3, 1, 2, 2, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.COAL_BLOCK);
        heap(b, 142, 2, 32, 1, 2, 1, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.BASALT);
        for (int[] f : new int[][]{{9, 2}, {14, 33}, {1, 18}}) b.set(f[0], 1, f[1], Blocks.CAMPFIRE);
        deadTree(b, 12, 1, 2, 5, 143, Blocks.CRIMSON_STEM);
        for (int[] s : new int[][]{{36, 2}, {39, 9}, {38, 31}, {31, 33}}) {
            int h = 4 + b.rand(s[0], 4);
            b.fill(s[0], 1, s[1], s[0], h, s[1], Blocks.PACKED_ICE);
            b.set(s[0] + 1, 1, s[1], Blocks.PACKED_ICE);
            b.set(s[0], 1, s[1] + 1, Blocks.BLUE_ICE);
        }
        scatter(b, 144, 14, c + 1, 0, w - 1, d - 1, 1, Blocks.SNOW.defaultBlockState());
        scatter(b, 145, 10, 27, 7, 35, 29, 1, Blocks.SNOW.defaultBlockState());
        // ---- the facility (x 4..36, z 6..30), floors y=0 and y=6, flat roof y=12
        int x1 = 4, x2 = 36, z1 = 6, z2 = 30;
        for (int f = 0; f < 2; f++) {
            int y0 = f * 6;
            b.fill(x1, y0, z1, x2, y0, z2, f == 0 ? Blocks.SMOOTH_STONE : Blocks.LIGHT_GRAY_CONCRETE);
            b.walls(x1, y0 + 1, z1, x2, y0 + 5, z2, Blocks.WHITE_CONCRETE);
            b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 5, z2 - 1);
            b.fill(x1, y0 + 4, z1, x2, y0 + 4, z1, Blocks.CYAN_CONCRETE);
            b.fill(x1, y0 + 4, z2, x2, y0 + 4, z2, Blocks.CYAN_CONCRETE);
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z1, 3, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x2, 3, Blocks.GLASS_PANE);
        }
        b.fill(x1, 12, z1, x2, 12, z2, Blocks.LIGHT_GRAY_CONCRETE);
        b.walls(x1, 13, z1, x2, 13, z2, Blocks.WHITE_CONCRETE);
        // damage: scorch marks and holes on the west, frost on the east
        for (int x = x1; x <= x2; x++)
            for (int y = 1; y <= 13; y++)
                for (int z : new int[]{z1, z2}) {
                    int r = b.rand(x * 7 + y * 131 + z * 3, 9);
                    if (x < c && r < 2) b.set(x, y, z, r == 0 ? Blocks.BLACKSTONE : Blocks.COAL_BLOCK);
                    if (x < c - 6 && y > 8 && r == 2) b.set(x, y, z, Blocks.AIR);
                    if (x > c && r < 2) b.set(x, y, z, Blocks.PACKED_ICE);
                }
        for (int z = z1; z <= z2; z++)
            for (int y = 1; y <= 13; y++) {
                int r = b.rand(z * 11 + y * 37, 7);
                b.set(x1, y, z, r < 2 ? Blocks.BLACKSTONE : Blocks.WHITE_CONCRETE);
            }
        for (int x = c + 1; x <= x2; x++) for (int z = z1; z <= z2; z++) if (b.rand(x * 3 + z * 5, 2) == 0) b.set(x, 13, z, Blocks.SNOW_BLOCK);
        // blown-open entrance
        b.clear(c - 1, 1, z1, c + 1, 3, z1);
        b.set(c - 2, 1, z1 - 1, Blocks.IRON_BLOCK);
        b.set(c + 2, 1, z1 - 1, Blocks.IRON_BARS);
        // ---- ground floor
        // lobby: reception counter and a wall map
        b.fill(c - 3, 1, 10, c + 3, 1, 10, Blocks.WHITE_CONCRETE);
        b.set(c, 1, 11, Blocks.CARTOGRAPHY_TABLE);
        npc(b, "scholar", c, 1, 12);
        // corridor wall along z=15 with doors into the labs
        b.fill(x1 + 1, 1, 15, x2 - 1, 5, 15, Blocks.WHITE_CONCRETE);
        b.fill(14, 1, z1 + 1, 14, 5, 14, Blocks.WHITE_CONCRETE);
        b.fill(26, 1, z1 + 1, 26, 5, 14, Blocks.WHITE_CONCRETE);
        b.door(14, 1, 11, Blocks.IRON_DOOR, Direction.EAST);
        b.set(15, 2, 12, button(Blocks.STONE_BUTTON, Direction.EAST));
        b.set(13, 2, 10, button(Blocks.STONE_BUTTON, Direction.WEST));
        b.clear(26, 1, 11, 26, 3, 11);
        for (int x : new int[]{9, 31}) b.clear(x, 1, 15, x, 3, 15);
        b.clear(c - 1, 1, 15, c + 1, 3, 15);
        // west lab: burnt benches and cracked tanks
        for (int x = 6; x <= 12; x += 3) {
            b.set(x, 1, 8, Blocks.BREWING_STAND);
            b.set(x, 1, 13, Blocks.CAULDRON);
            b.fill(x, 1, 18, x, 3, 18, Blocks.LIME_STAINED_GLASS);
            b.set(x, 4, 18, Blocks.IRON_BLOCK);
        }
        b.fill(6, 1, 24, 12, 1, 24, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        b.set(7, 2, 24, Blocks.BLACKSTONE);
        b.chest(5, 1, 28, Direction.EAST, Buildings.LOOT_RUIN);
        b.hanging(9, 5, 11, Blocks.LANTERN);
        b.hanging(9, 5, 22, Blocks.LANTERN);
        npc(b, "pirate", 9, 1, 21);
        // east lab: cryo pods
        for (int x = 28; x <= 34; x += 3) {
            b.fill(x, 1, 18, x, 3, 18, Blocks.GLASS);
            b.set(x, 2, 18, Blocks.BLUE_ICE);
            b.fill(x, 1, 26, x, 3, 26, Blocks.GLASS);
            b.set(x, 2, 26, Blocks.PACKED_ICE);
        }
        b.fill(27, 0, 7, 35, 0, 29, Blocks.PACKED_ICE);
        b.barrel(35, 1, 8, Buildings.LOOT_WAREHOUSE);
        b.hanging(31, 5, 11, Blocks.LANTERN);
        b.hanging(31, 5, 22, Blocks.LANTERN);
        npc(b, "pirate", 30, 1, 22);
        // the sealed gas laboratory at the back centre (glass walls, iron door)
        b.fill(16, 1, 18, 24, 5, 18, Blocks.GREEN_STAINED_GLASS);
        b.fill(16, 1, 18, 16, 5, 29, Blocks.WHITE_CONCRETE);
        b.fill(24, 1, 18, 24, 5, 29, Blocks.WHITE_CONCRETE);
        b.door(c, 1, 18, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c + 1, 2, 17, button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(c - 1, 2, 19, button(Blocks.STONE_BUTTON, Direction.SOUTH));
        for (int x = 17; x <= 23; x += 2) {
            b.fill(x, 1, 28, x, 3, 28, Blocks.LIME_STAINED_GLASS);
            b.set(x, 4, 28, Blocks.OBSERVER);
        }
        b.set(17, 1, 22, Blocks.CAULDRON);
        b.set(23, 1, 22, facing(Blocks.PISTON, Direction.UP));
        b.hanging(c, 5, 24, Blocks.SEA_LANTERN);
        // stairs up from the lobby
        stairsUp(b, 24, 1, 13, Direction.NORTH, 6, Blocks.STONE_BRICK_STAIRS, 2, Blocks.WHITE_CONCRETE);
        // ---- upper floor: archive (west), director's office (centre-back), staff bunks (east)
        b.fill(14, 7, z1 + 1, 14, 11, z2 - 1, Blocks.BOOKSHELF);
        b.door(14, 7, 10, Blocks.IRON_DOOR, Direction.EAST);
        b.set(15, 8, 11, button(Blocks.STONE_BUTTON, Direction.EAST));
        b.set(13, 8, 9, button(Blocks.STONE_BUTTON, Direction.WEST));
        for (int z = 9; z <= 27; z += 3) {
            b.fill(6, 7, z, 11, 8, z, Blocks.BOOKSHELF);
            b.set(12, 7, z, facing(Blocks.LECTERN, Direction.EAST));
        }
        b.hanging(9, 11, 18, Blocks.LANTERN);
        npc(b, "scholar", 7, 7, 17);
        b.fill(15, 7, 17, 25, 11, 17, Blocks.WHITE_CONCRETE);
        b.door(c, 7, 17, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c + 1, 8, 16, button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(c - 1, 8, 18, button(Blocks.STONE_BUTTON, Direction.SOUTH));
        b.fill(26, 7, 17, 26, 11, z2 - 1, Blocks.WHITE_CONCRETE);
        table(b, 19, 7, 24, Blocks.QUARTZ_SLAB);
        table(b, 20, 7, 24, Blocks.QUARTZ_SLAB);
        table(b, 21, 7, 24, Blocks.QUARTZ_SLAB);
        chair(b, c, 7, 25, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        b.fill(23, 7, 27, 23, 10, 27, Blocks.PURPLE_STAINED_GLASS);
        b.set(23, 11, 27, Blocks.IRON_BLOCK);
        b.set(17, 7, 28, Blocks.BREWING_STAND);
        b.chest(15, 7, 29, Direction.EAST, Buildings.LOOT_TREASURE);
        b.hanging(c, 11, 22, Blocks.SEA_LANTERN);
        mark(b, "treasure", 15, 7, 29);
        npc(b, "pirate_captain", 21, 7, 21);
        for (int z = 19; z <= 28; z += 3) {
            b.bed(28, 7, z, Blocks.WHITE_BED, Direction.EAST);
            b.bed(33, 7, z, Blocks.WHITE_BED, Direction.EAST);
        }
        b.barrel(35, 7, 18, Buildings.LOOT_HOUSE);
        b.hanging(31, 11, 23, Blocks.LANTERN);
        b.hanging(31, 11, 11, Blocks.LANTERN);
        npc(b, "pirate", 31, 7, 12);
        b.marker("arc_anchor", c, 1, 3, tag("arc", "fire_and_ice"));
    }

    // ============================================================== Dressrosa: the Corrida Colosseum

    /** Round colosseum: arcaded outer ring, stepped stands over a ring concourse, sand arena, and the gladiators'
     *  quarters, cells and prize vault underneath. */
    static void corridaColosseum(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 25; // 51 x 51
        site(b, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, 18);
        // the gladiators' underground (built first: the arena floor is its roof)
        shell(b, c - 11, -7, c - 11, c + 11, -1, c + 11, Blocks.STONE_BRICKS);
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                double dd = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
                if (dd > 24.5) {
                    if (((x + z) & 3) == 0) b.set(x, 0, z, Blocks.CUT_SANDSTONE);
                    continue;
                }
                if (dd > 22.5) {
                    for (int y = 1; y <= 14; y++) b.set(x, y, z, y % 5 == 0 ? Blocks.ORANGE_TERRACOTTA : y == 14 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE);
                    b.set(x, 15, z, dd > 23.5 ? Blocks.ORANGE_TERRACOTTA : Blocks.AIR);
                } else if (dd > 12.5) {
                    int h = (int) Math.round(dd - 12);
                    for (int y = 1; y <= h; y++) b.set(x, y, z, y == h ? ((h & 1) == 0 ? Blocks.RED_TERRACOTTA : Blocks.SMOOTH_SANDSTONE) : Blocks.SANDSTONE);
                    // concourse under the upper stands
                    if (dd > 19.5) b.clear(x, 1, z, x, 4, z);
                } else if (dd > 11.5) {
                    b.fill(x, 1, z, x, 3, z, Blocks.STONE_BRICKS);
                    b.set(x, 0, z, Blocks.STONE_BRICKS);
                } else {
                    b.set(x, 0, z, b.rand(x * 13 + z, 5) == 0 ? Blocks.COARSE_DIRT : Blocks.SAND);
                }
            }
        // arcade openings all round, and four tunnels from the concourse to the front row
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            int ax = c + (int) Math.round(Math.cos(a) * 23.5), az = c + (int) Math.round(Math.sin(a) * 23.5);
            b.clear(ax - 1, 1, az - 1, ax + 1, 4, az + 1);
            b.fill(ax - 1, 0, az - 1, ax + 1, 0, az + 1, Blocks.CUT_SANDSTONE);
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int k = 13; k <= 20; k++) {
                int tx = c + dir.getStepX() * k, tz = c + dir.getStepZ() * k;
                Direction side = dir.getClockWise();
                for (int s = -1; s <= 1; s++) b.clear(tx + side.getStepX() * s, 1, tz + side.getStepZ() * s, tx + side.getStepX() * s, 3, tz + side.getStepZ() * s);
            }
        }
        // banners on the crown of the wall
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int fx = c + (int) Math.round(Math.cos(a) * 23.6), fz = c + (int) Math.round(Math.sin(a) * 23.6);
            b.fill(fx, 15, fz, fx, 18, fz, Blocks.OAK_FENCE);
            b.set(fx, 19, fz, (i & 1) == 0 ? Blocks.RED_WOOL : Blocks.YELLOW_WOOL);
        }
        // the gladiators' ramp up into the arena (south)
        stairsUp(b, c + 1, -6, c + 3, Direction.SOUTH, 7, Blocks.STONE_BRICK_STAIRS, 3, Blocks.STONE_BRICKS);
        line(b, c - 2, 1, c + 6, c - 2, c + 9, Blocks.IRON_BARS);
        line(b, c + 2, 1, c + 6, c + 2, c + 9, Blocks.IRON_BARS);
        // gladiators' gates through the arena barrier (north and south tunnels)
        for (int s : new int[]{-12, 12}) {
            b.clear(c - 1, 1, c + s, c + 1, 3, c + s);
            b.set(c - 1, 1, c + s, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
            b.set(c + 1, 1, c + s, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
        }
        for (int[] p : new int[][]{{c + 6, c - 14}, {c - 15, c + 5}, {c + 12, c + 10}}) {
            double dd = Math.sqrt((p[0] - c) * (p[0] - c) + (p[1] - c) * (p[1] - c));
            npc(b, "patron", p[0], (int) Math.round(dd - 12) + 1, p[1]);
        }
        npc(b, "guard", c - 2, 1, 0);
        npc(b, "guard", c + 2, 1, 0);
        b.marker("arc_anchor", c, 1, c, tag("arc", "colosseum_tournament"));
        // ticket booth in the north-west corner
        for (int[] p : new int[][]{{1, 1}, {5, 1}, {1, 4}, {5, 4}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], Blocks.OAK_FENCE);
        b.fill(1, 4, 1, 5, 4, 4, Blocks.RED_WOOL);
        for (int x = 2; x <= 4; x++) table(b, x, 1, 1, Blocks.OAK_SLAB);
        b.barrel(2, 1, 4, Buildings.LOOT_SHOP);
        npc(b, "merchant", 3, 1, 3);
        // ---- underground: hall with benches and racks, cells (west), champion's corner, prize vault (east)
        b.fill(c - 10, -7, c - 10, c + 10, -7, c + 10, Blocks.POLISHED_ANDESITE);
        for (int x = c - 4; x <= c + 4; x++) chair(b, x, -6, c - 9, Blocks.SPRUCE_STAIRS, Direction.NORTH);
        for (int z = c - 4; z <= c + 2; z += 2) {
            b.set(c + 4, -6, z, Blocks.GRINDSTONE);
            b.fill(c + 4, -5, z, c + 4, -4, z, Blocks.IRON_BARS);
        }
        b.barrel(c - 3, -6, c + 1, Buildings.LOOT_SMITH);
        b.set(c - 2, -6, c + 1, Blocks.ANVIL);
        for (int[] l : new int[][]{{c, c - 5}, {c, c + 1}, {c - 8, c}, {c + 8, c + 4}}) b.hanging(l[0], -2, l[1], Blocks.LANTERN);
        npc(b, "pirate", c - 1, -6, c - 6);
        npc(b, "pirate", c + 2, -6, c - 1);
        for (int z = c - 9; z <= c + 3; z += 3) {
            b.fill(c - 10, -6, z, c - 7, -3, z, Blocks.STONE_BRICKS);
            if (z < c + 3) mark(b, "cell", c - 9, -6, z + 1);
        }
        for (int z = c - 9; z <= c + 3; z++) for (int y = -6; y <= -4; y++) b.set(c - 6, y, z, connected(Blocks.IRON_BARS, Direction.Axis.Z));
        b.fill(c - 10, -3, c - 9, c - 6, -3, c + 3, Blocks.STONE_BRICKS);
        b.bed(c - 9, -6, c + 6, Blocks.RED_BED, Direction.SOUTH);
        b.chest(c - 7, -6, c + 9, Direction.NORTH, Buildings.LOOT_HOUSE);
        npc(b, "master", c - 6, -6, c + 6);
        b.fill(c + 5, -6, c - 10, c + 5, -3, c - 4, Blocks.STONE_BRICKS);
        b.fill(c + 5, -6, c - 3, c + 10, -3, c - 3, Blocks.STONE_BRICKS);
        b.fill(c + 6, -3, c - 10, c + 10, -3, c - 4, Blocks.STONE_BRICKS);
        b.door(c + 7, -6, c - 3, Blocks.IRON_DOOR, Direction.SOUTH);
        b.set(c + 8, -5, c - 2, lever(Direction.SOUTH));
        b.set(c + 5, -5, c - 2, lever(Direction.SOUTH));
        b.set(c + 10, -4, c - 2, lever(Direction.SOUTH));
        b.fill(c + 7, -6, c - 9, c + 9, -6, c - 9, Blocks.GOLD_BLOCK);
        b.set(c + 8, -5, c - 9, Blocks.GLASS);
        b.chest(c + 6, -6, c - 7, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(c + 10, -6, c - 7, Direction.WEST, Buildings.LOOT_TREASURE);
        b.hanging(c + 8, -4, c - 6, Blocks.LANTERN);
        mark(b, "treasure", c + 8, -6, c - 7);
        npc(b, "guard", c + 7, -6, c);
    }

    // ============================================================== Dressrosa: the royal plateau

    /** The palace on its plateau: a lift tower and cliff stair from the flower plaza, a whitewashed palace with
     *  terracotta roofs on top, and a secret factory hollowed out of the rock below it. */
    static void royalPlateau(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        final int T = 12; // plateau top
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 34);
        // ---- the plateau (x 3..41, z 9..43)
        for (int x = 3; x <= 41; x++)
            for (int z = 9; z <= 43; z++)
                for (int y = 1; y <= T; y++) {
                    int r = b.rand(x * 7 + z * 13 + y * 131, 7);
                    b.set(x, y, z, y == T ? Blocks.GRASS_BLOCK : (y % 4 == 0 ? Blocks.GRANITE : r == 0 ? Blocks.ANDESITE : Blocks.STONE));
                }
        // flower plaza below, with a fountain
        for (int x = 1; x <= w - 2; x++) for (int z = 1; z <= 7; z++) if ((x + z) % 3 == 0) b.set(x, 1, z, (x / 4) % 3 == 0 ? Blocks.RED_TULIP : (x / 4) % 3 == 1 ? Blocks.PINK_TULIP : Blocks.POPPY);
        b.fill(30, 0, 1, 42, 0, 7, Blocks.SMOOTH_SANDSTONE);
        b.clear(30, 1, 1, 42, 1, 7);
        ring(b, 36, 4, 1, 2, Blocks.CUT_SANDSTONE.defaultBlockState());
        b.set(36, 0, 4, Blocks.WATER);
        b.fill(36, 1, 4, 36, 2, 4, Blocks.CHISELED_SANDSTONE);
        npc(b, "merchant", 33, 1, 3);
        npc(b, "child", 40, 1, 6);
        // the lift tower and the cliff stair
        for (int[] p : new int[][]{{20, 5}, {24, 5}, {20, 8}, {24, 8}}) b.fill(p[0], 1, p[1], p[0], T + 3, p[1], Blocks.IRON_BARS);
        b.fill(20, T + 4, 5, 24, T + 4, 8, Blocks.IRON_BLOCK);
        b.fill(21, 1, 6, 23, 1, 7, Blocks.SMOOTH_STONE_SLAB);
        b.ladderUp(22, 1, 8, T, Direction.NORTH);
        b.fill(21, T, 8, 21, T, 8, Blocks.STONE_BRICKS);
        b.fill(23, T, 8, 23, T, 8, Blocks.STONE_BRICKS);
        b.hanging(22, T + 3, 6, Blocks.LANTERN);
        stairsUp(b, 4, 1, 7, Direction.EAST, T, Blocks.STONE_BRICK_STAIRS, 2, Blocks.STONE_BRICKS);
        // ---- on top: forecourt with a flower field, the palace (x 9..35, z 20..40)
        int y0 = T;
        for (int x = 4; x <= 40; x++) for (int z = 10; z <= 19; z++) if ((x * 3 + z) % 4 == 0 && Math.abs(x - c) > 2) b.set(x, y0 + 1, z, (x & 1) == 0 ? Blocks.RED_TULIP : Blocks.ORANGE_TULIP);
        b.fill(c - 2, y0, 9, c + 2, y0, 20, Blocks.SMOOTH_SANDSTONE);
        b.clear(c - 2, y0 + 1, 9, c + 2, y0 + 1, 20);
        npc(b, "resident", 12, y0 + 1, 14);
        int x1 = 9, x2 = 35, z1 = 20, z2 = 40;
        for (int f = 0; f < 2; f++) {
            int fy = y0 + f * 6;
            b.fill(x1, fy, z1, x2, fy, z2, f == 0 ? Blocks.POLISHED_GRANITE : Blocks.SPRUCE_PLANKS);
            b.walls(x1, fy + 1, z1, x2, fy + 5, z2, Blocks.WHITE_TERRACOTTA);
            b.clear(x1 + 1, fy + 1, z1 + 1, x2 - 1, fy + 5, z2 - 1);
            windowsX(b, x1 + 2, x2 - 2, fy + 2, z1, 3, Blocks.GLASS_PANE);
            windowsX(b, x1 + 2, x2 - 2, fy + 2, z2, 3, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, fy + 2, x1, 3, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, fy + 2, x2, 3, Blocks.GLASS_PANE);
        }
        b.fill(x1, y0 + 12, z1, x2, y0 + 12, z2, Blocks.SPRUCE_PLANKS);
        hipRoof(b, x1 - 1, z1 - 1, x2 + 1, z2 + 1, y0 + 12, Blocks.BRICK_STAIRS, Blocks.BRICKS);
        for (int[] t : new int[][]{{x1, z1}, {x2, z1}}) {
            b.cylinder(t[0], t[1], y0 + 1, y0 + 16, 3, Blocks.WHITE_TERRACOTTA, true);
            b.cylinder(t[0], t[1], y0 + 1, y0 + 16, 2, Blocks.AIR, false);
            cone(b, t[0], t[1], y0 + 17, 7, 4, 0.5, Blocks.TERRACOTTA.defaultBlockState(), false);
        }
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, y0 + 1, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        npc(b, "guard", c - 3, y0 + 1, z1 - 1);
        npc(b, "guard", c + 3, y0 + 1, z1 - 1);
        // ground floor: throne room (middle), guard hall (west), dining (east)
        b.fill(17, y0 + 1, z1 + 1, 17, y0 + 5, z2 - 1, Blocks.WHITE_TERRACOTTA);
        b.fill(27, y0 + 1, z1 + 1, 27, y0 + 5, z2 - 1, Blocks.WHITE_TERRACOTTA);
        b.clear(17, y0 + 1, 24, 17, y0 + 3, 25);
        b.clear(27, y0 + 1, 24, 27, y0 + 3, 25);
        carpet(b, c - 1, y0 + 1, z1 + 1, c + 1, 35, Blocks.PINK_CARPET);
        b.fill(19, y0 + 1, 36, 25, y0 + 1, 39, Blocks.GOLD_BLOCK);
        b.set(c, y0 + 2, 38, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.set(c, y0 + 2, 39, Blocks.PINK_WOOL);
        b.set(c, y0 + 3, 39, Blocks.PINK_WOOL);
        for (int z = 24; z <= 32; z += 4) {
            b.set(19, y0 + 1, z, Blocks.POTTED_RED_TULIP);
            b.set(25, y0 + 1, z, Blocks.POTTED_RED_TULIP);
        }
        b.hanging(c, y0 + 5, 30, Blocks.LANTERN);
        npc(b, "noble", c, y0 + 2, 37);
        b.bed(11, y0 + 1, 36, Blocks.RED_BED, Direction.SOUTH);
        b.bed(13, y0 + 1, 36, Blocks.RED_BED, Direction.SOUTH);
        b.chest(16, y0 + 1, 39, Direction.WEST, Buildings.LOOT_MARINE);
        b.set(10, y0 + 1, 24, Blocks.GRINDSTONE);
        b.hanging(13, y0 + 5, 30, Blocks.LANTERN);
        npc(b, "guard", 13, y0 + 1, 28);
        longTableZ(b, 24, 34, y0 + 1, 31, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        kitchenX(b, 28, y0 + 1, 39, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.hanging(31, y0 + 5, 30, Blocks.LANTERN);
        npc(b, "cook", 33, y0 + 1, 37);
        stairsUp(b, 34, y0 + 1, 23, Direction.SOUTH, 6, Blocks.SPRUCE_STAIRS, 2, Blocks.SPRUCE_PLANKS);
        // upper floor: royal suite and library
        b.bed(c - 1, y0 + 7, 37, Blocks.PINK_BED, Direction.SOUTH);
        b.bed(c + 1, y0 + 7, 37, Blocks.PINK_BED, Direction.SOUTH);
        carpet(b, 18, y0 + 7, 30, 26, 36, Blocks.ORANGE_CARPET);
        b.chest(19, y0 + 7, 39, Direction.EAST, Buildings.LOOT_HOUSE);
        b.fill(x1 + 1, y0 + 7, z1 + 1, x1 + 1, y0 + 9, z2 - 1, Blocks.BOOKSHELF);
        b.set(13, y0 + 7, 30, Blocks.ENCHANTING_TABLE);
        b.hanging(c, y0 + 11, 32, Blocks.LANTERN);
        b.hanging(13, y0 + 11, 30, Blocks.LANTERN);
        // ---- the secret factory inside the plateau: a hatch behind the throne, and a cracked door in the cliff
        b.clear(8, 2, 14, 36, 8, 36);
        b.fill(8, 1, 14, 36, 1, 36, Blocks.SMOOTH_STONE);
        hatch(b, 26, y0, 36, 2, Direction.NORTH, Blocks.SPRUCE_TRAPDOOR, Blocks.STONE);
        b.set(26, y0 + 1, 36, Blocks.RED_CARPET);
        b.clear(30, 2, 10, 30, 3, 13);
        b.set(30, 2, 9, Blocks.CRACKED_STONE_BRICKS);
        b.set(30, 3, 9, Blocks.CRACKED_STONE_BRICKS);
        for (int z = 16; z <= 34; z += 6) {
            b.fill(10, 2, z, 30, 2, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
            for (int x = 11; x <= 29; x += 3) b.set(x, 2, z + 1, (x & 1) == 0 ? facing(Blocks.BLAST_FURNACE, Direction.NORTH) : facing(Blocks.SMOKER, Direction.NORTH));
            b.set(32, 2, z, Blocks.HOPPER);
            b.barrel(33, 2, z, Buildings.LOOT_WAREHOUSE);
            b.set(34, 2, z, Blocks.CAULDRON);
        }
        b.chest(9, 2, 35, Direction.EAST, Buildings.LOOT_TREASURE);
        for (int x = 12; x <= 32; x += 10) for (int z = 18; z <= 33; z += 7) b.hanging(x, 8, z, Blocks.LANTERN);
        mark(b, "treasure", 9, 2, 35);
        npc(b, "pirate", 15, 2, 20);
        npc(b, "pirate", 28, 2, 32);
        npc(b, "pirate_captain", 24, 2, 26);
        b.marker("arc_anchor", c, y0 + 1, 12, tag("arc", "toy_kingdom"));
    }

    // ============================================================== Green Bit: the dwarves' village

    private static final Block[] TINY_WALL = {Blocks.OAK_PLANKS, Blocks.BIRCH_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.MOSSY_COBBLESTONE};

    /** A two-block tall dwarf cottage (decorative): trapdoor door, little window, slab roof and chimney. */
    private static void tinyHouse(Builder b, int x, int z, int salt) {
        Block wall = TINY_WALL[b.rand(salt, TINY_WALL.length)];
        b.fill(x, 1, z, x + 1, 2, z + 1, wall);
        b.set(x, 1, z, trapdoor(Blocks.OAK_TRAPDOOR, Direction.NORTH, Half.BOTTOM, true));
        b.set(x + 1, 2, z, connected(Blocks.GLASS_PANE, Direction.Axis.X));
        b.fill(x, 3, z, x + 1, 3, z + 1, (salt & 1) == 0 ? Blocks.RED_TERRACOTTA : Blocks.BROWN_MUSHROOM_BLOCK);
        b.set(x + 1, 4, z + 1, Blocks.COBBLESTONE_WALL);
        b.set(x - 1, 1, z, Blocks.FLOWER_POT);
    }

    /** The tiny people's kingdom among the roots of a great tree: a royal hall inside the trunk (with a storehouse
     *  below), dwarf cottages, a mushroom house and flower gardens by a stream. */
    static void tontattaVillage(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 14; // 29 x 29
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 30);
        scatter(b, 151, 40, 0, 0, w - 1, d - 1, 1, Blocks.DANDELION.defaultBlockState());
        scatter(b, 152, 30, 0, 0, w - 1, d - 1, 1, Blocks.PINK_TULIP.defaultBlockState());
        scatter(b, 153, 20, 0, 0, w - 1, d - 1, 1, Blocks.CORNFLOWER.defaultBlockState());
        // a stream across the south with little bridges
        for (int x = 0; x < w; x++) {
            b.set(x, 0, 24, Blocks.WATER);
            b.set(x, -1, 24, Blocks.CLAY);
            b.clear(x, 1, 24, x, 1, 24);
        }
        for (int x : new int[]{6, 20}) b.set(x, 1, 24, Blocks.OAK_SLAB);
        // the great tree: trunk r=4, hollow hall inside, roots, canopy
        for (int y = 1; y <= 18; y++) b.cylinder(c, c, y, y, 4, Blocks.OAK_WOOD, y > 6);
        b.cylinder(c, c, 1, 6, 3, Blocks.AIR, false);
        b.cylinder(c, c, 7, 7, 3, Blocks.OAK_PLANKS, false);
        b.cylinder(c, c, 0, 0, 3, Blocks.MOSS_BLOCK, false);
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++)
                for (int y = 16; y <= 25; y++) {
                    double dd = (x * x + z * z) / 100.0 + Math.pow((y - 20) / 5.0, 2);
                    if (dd > 1 || b.rand(x * 131 + z * 17 + y, 8) == 0) continue;
                    if (Math.abs(x) <= 4 && Math.abs(z) <= 4 && y <= 18) continue;
                    b.set(c + x, y, c + z, leaves(y > 22 && b.rand(x + z * 3, 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.OAK_LEAVES));
                }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.5;
            for (int k = 4; k <= 8; k++) {
                int rx = c + (int) Math.round(Math.cos(a) * k), rz = c + (int) Math.round(Math.sin(a) * k);
                b.set(rx, 1, rz, Blocks.OAK_WOOD);
                if (k < 6) b.set(rx, 2, rz, Blocks.OAK_WOOD);
            }
        }
        b.bigDoor(c, 1, c - 4, Blocks.OAK_DOOR, Direction.NORTH);
        b.clear(c, 1, c - 5, c, 3, c - 5);
        b.set(c - 2, 1, c - 5, Blocks.LANTERN);
        b.set(c + 2, 1, c - 5, Blocks.LANTERN);
        // the royal hall: little throne, tiny tables with candles, banners
        b.set(c, 1, c + 3, Blocks.GOLD_BLOCK);
        b.set(c, 2, c + 3, Blocks.POTTED_DANDELION);
        b.set(c - 1, 1, c + 3, candles(Blocks.YELLOW_CANDLE, 3));
        b.set(c + 1, 1, c + 3, candles(Blocks.YELLOW_CANDLE, 3));
        for (int[] t : new int[][]{{c - 2, c}, {c + 2, c}}) {
            b.set(t[0], 1, t[1], Blocks.OAK_PRESSURE_PLATE);
            b.set(t[0], 1, t[1] + 1, candles(Blocks.CANDLE, 2));
        }
        b.set(c - 3, 3, c, banner(Blocks.YELLOW_WALL_BANNER, Direction.EAST));
        b.set(c + 3, 3, c, banner(Blocks.YELLOW_WALL_BANNER, Direction.WEST));
        b.set(c + 2, 1, c + 2, facing(Blocks.LECTERN, Direction.WEST));
        b.hanging(c, 6, c, Blocks.LANTERN);
        npc(b, "elder", c, 1, c + 2);
        npc(b, "guard", c - 1, 1, c - 2);
        // the storehouse under the hall (hatch by the throne)
        shell(b, c - 3, -5, c - 3, c + 3, 0, c + 3, Blocks.OAK_PLANKS);
        b.cylinder(c, c, 0, 0, 3, Blocks.MOSS_BLOCK, false);
        hatch(b, c - 2, 0, c + 2, -4, Direction.WEST, Blocks.OAK_TRAPDOOR, Blocks.OAK_PLANKS);
        b.chest(c + 2, -4, c + 2, Direction.WEST, Buildings.LOOT_TREASURE);
        b.barrel(c + 2, -4, c - 2, Buildings.LOOT_HOUSE);
        b.fill(c - 2, -4, c - 2, c - 2, -3, c - 2, Blocks.HAY_BLOCK);
        b.hanging(c, -1, c, Blocks.LANTERN);
        mark(b, "treasure", c + 2, -4, c + 2);
        // dwarf cottages among the roots
        int s = 0;
        for (int[] h : new int[][]{{3, 3}, {7, 2}, {20, 3}, {25, 3}, {2, 19}, {7, 21}, {21, 20}, {25, 15}}) tinyHouse(b, h[0], h[1], 160 + s++);
        npc(b, "guard", c + 2, 1, c - 6);
        npc(b, "child", 10, 1, 22);
        npc(b, "farmer", 4, 1, 12);
        // the mushroom house (player-sized)
        int mx = 22, mz = 9;
        b.fill(mx - 2, 1, mz - 2, mx + 2, 3, mz + 2, Blocks.MUSHROOM_STEM);
        b.clear(mx - 1, 1, mz - 1, mx + 1, 3, mz + 1);
        b.fill(mx - 2, 0, mz - 2, mx + 2, 0, mz + 2, Blocks.SPRUCE_PLANKS);
        ellipsoid(b, mx, 4, mz, 3.6, 2.4, 3.6, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), false, 0);
        b.clear(mx - 1, 4, mz - 1, mx + 1, 4, mz + 1);
        b.door(mx, 1, mz - 2, Blocks.OAK_DOOR, Direction.NORTH);
        b.bed(mx - 1, 1, mz, Blocks.RED_BED, Direction.SOUTH);
        b.barrel(mx + 1, 1, mz + 1, Buildings.LOOT_HOUSE);
        b.hanging(mx, 4, mz, Blocks.LANTERN);
        b.set(mx, 5, mz, Blocks.RED_MUSHROOM_BLOCK);
        npc(b, "resident", mx + 1, 1, mz - 1);
        b.marker("legend", c, 1, c - 7, tag("story", "dwarf_kingdom"));
    }

    // ============================================================== Zou: the gate of the great legs

    /** Two colossal elephant legs bridged by a belly-span (with a lookout and a hidden alcove reached up the
     *  hollow west leg), a palisade gate between them, the lord's tree lodge and mink houses. */
    static void giantLegs(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 41
        site(b, Blocks.PODZOL, Blocks.DIRT, 40);
        patchGround(b, 171, 0, Blocks.PODZOL, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK);
        // legs
        for (int lx : new int[]{11, 29}) {
            for (int y = 1; y <= 28; y++) {
                for (int x = -5; x <= 5; x++)
                    for (int z = -5; z <= 5; z++) {
                        double dd = Math.sqrt(x * x + z * z);
                        if (dd > 4.6) continue;
                        int r = b.rand(lx * 7 + x * 31 + y * 131 + z * 17, 9);
                        b.set(lx + x, y, 22 + z, y % 3 == 0 ? Blocks.GRAY_TERRACOTTA : r == 0 ? Blocks.TUFF : Blocks.LIGHT_GRAY_TERRACOTTA);
                    }
            }
            for (int k = -2; k <= 2; k += 2) {
                if (lx == 11 && k == 0) continue; // the west leg's door
                b.fill(lx + k, 1, 17, lx + k, 2, 17, Blocks.SMOOTH_QUARTZ);
            }
        }
        // the belly span (arched underside), walkway on top
        for (int x = 11; x <= 29; x++) {
            int under = 24 + (int) Math.round(4 * Math.sin(Math.PI * (x - 11) / 18.0));
            b.fill(x, under, 18, x, 32, 26, Blocks.LIGHT_GRAY_TERRACOTTA);
            b.fill(x, under, 18, x, under, 26, Blocks.GRAY_TERRACOTTA);
        }
        line(b, 11, 33, 18, 29, 18, Blocks.SPRUCE_FENCE);
        line(b, 11, 33, 26, 29, 26, Blocks.SPRUCE_FENCE);
        b.hanging(c, 27, 22, Blocks.LANTERN);
        npc(b, "resident", c, 33, 22);
        // the hollow west leg: door, ladder through the belly to the walkway
        b.cylinder(11, 22, 1, 27, 3, Blocks.AIR, false);
        b.cylinder(11, 22, 0, 0, 3, Blocks.SPRUCE_PLANKS, false);
        b.bigDoor(11, 1, 18, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.clear(11, 28, 25, 11, 32, 25);
        b.ladderUp(11, 1, 25, 32, Direction.NORTH);
        b.hanging(10, 27, 22, Blocks.LANTERN);
        // a hidden alcove in the belly, behind a tuff patch beside the ladder
        b.set(12, 29, 25, Blocks.TUFF);
        b.set(12, 30, 25, Blocks.TUFF);
        b.clear(13, 29, 23, 16, 31, 25);
        b.chest(16, 29, 24, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(13, 31, 23, Blocks.SHROOMLIGHT);
        mark(b, "treasure", 16, 29, 24);
        // palisade gate between the legs
        for (int x = 16; x <= 24; x++) if (x < 19 || x > 21) b.fill(x, 1, 22, x, 4, 22, Blocks.STRIPPED_SPRUCE_LOG);
        b.fill(16, 5, 22, 24, 5, 22, axis(Blocks.SPRUCE_LOG, Direction.Axis.X));
        b.set(19, 1, 22, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
        b.set(21, 1, 22, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));
        b.hanging(20, 4, 22, Blocks.LANTERN);
        npc(b, "guard", 18, 1, 20);
        npc(b, "guard", 22, 1, 20);
        // the lord's lodge in a great tree (south)
        tree(b, c, 1, 34, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, 14, 6, 181, null);
        b.fill(c, 1, 34, c + 1, 14, 35, Blocks.JUNGLE_LOG);
        b.fill(12, 7, 29, 28, 7, 39, Blocks.JUNGLE_PLANKS);
        b.clear(12, 8, 29, 28, 11, 39);
        b.fill(c, 8, 34, c + 1, 11, 35, Blocks.JUNGLE_LOG);
        stairsUp(b, 12, 1, 38, Direction.NORTH, 7, Blocks.JUNGLE_STAIRS, 2, Blocks.JUNGLE_PLANKS);
        b.walls(15, 8, 30, 26, 10, 38, Blocks.JUNGLE_PLANKS);
        b.fill(15, 11, 30, 26, 11, 38, Blocks.JUNGLE_SLAB);
        b.door(c, 8, 30, Blocks.JUNGLE_DOOR, Direction.NORTH);
        windowsZ(b, 32, 36, 9, 15, 4, Blocks.GLASS_PANE);
        windowsZ(b, 32, 36, 9, 26, 4, Blocks.GLASS_PANE);
        line(b, 12, 8, 29, 14, 29, Blocks.JUNGLE_FENCE);
        b.bed(17, 8, 36, Blocks.YELLOW_BED, Direction.SOUTH);
        b.chest(16, 8, 31, Direction.EAST, Buildings.LOOT_HOUSE);
        table(b, 23, 8, 32, Blocks.JUNGLE_SLAB);
        b.set(23, 9, 32, candles(Blocks.CANDLE, 2));
        b.set(25, 9, 34, banner(Blocks.YELLOW_WALL_BANNER, Direction.WEST));
        b.hanging(23, 10, 36, Blocks.LANTERN);
        npc(b, "elder", 23, 8, 35);
        // mink houses
        Buildings.house(sub(b, 1, 0, 2, 9, 9, Rotation.NONE), BuildStyle.JUNGLE, 1, Buildings.Interior.HOME);
        Buildings.house(sub(b, 31, 0, 2, 9, 9, Rotation.NONE), BuildStyle.JUNGLE, 1, Buildings.Interior.SHOP);
        npc(b, "child", 20, 1, 12);
        b.marker("legend", c, 1, 14, tag("story", "great_legs"));
    }

    // ============================================================== Whole Cake Island: the cake chateau

    private static Block cakeLayer(int y) {
        int m = y % 5;
        return m == 0 ? Blocks.RED_TERRACOTTA : m == 4 ? Blocks.WHITE_CONCRETE : Blocks.SMOOTH_SANDSTONE;
    }

    private static void cakeTier(Builder b, int c, int r, int y1, int y2) {
        for (int y = y1; y <= y2; y++) {
            b.cylinder(c, c, y, y, r, cakeLayer(y), true);
            b.cylinder(c, c, y, y, r - 1, Blocks.AIR, false);
        }
        b.cylinder(c, c, y2 + 1, y2 + 1, r, Blocks.WHITE_CONCRETE, false);
        // frosting drips down the wall
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            int x = c + (int) Math.round(Math.cos(a) * r), z = c + (int) Math.round(Math.sin(a) * r);
            int len = 1 + b.rand(i * 7 + r, 3);
            b.fill(x, y2 + 1 - len, z, x, y2, z, Blocks.WHITE_CONCRETE);
        }
    }

    /** A castle that is a three-tier cake: candy garden outside, the banquet hall and kitchens in the bottom tier, the
     *  throne room (and a mirror-sealed vault) above, private chambers on top under giant candles. */
    static void cakeChateau(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 25; // 51 x 51
        site(b, Blocks.PINK_TERRACOTTA, Blocks.DIRT, 40);
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) if (((x >> 2) + (z >> 2)) % 2 == 0) b.set(x, 0, z, Blocks.WHITE_TERRACOTTA);
        // candy garden: lollipop trees, candy canes, cookies
        Block[] candy = {Blocks.PINK_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.YELLOW_CONCRETE};
        int i = 0;
        for (int[] p : new int[][]{{3, 3}, {47, 3}, {3, 47}, {47, 47}, {8, 12}, {42, 12}}) {
            b.fill(p[0], 1, p[1], p[0], 5, p[1], Blocks.WHITE_CONCRETE);
            ellipsoid(b, p[0], 7, p[1], 2.2, 2.2, 1.2, candy[i++ % candy.length].defaultBlockState(), false, -100);
        }
        for (int[] p : new int[][]{{12, 3}, {38, 3}, {3, 25}, {47, 25}}) {
            for (int y = 1; y <= 6; y++) b.set(p[0], y, p[1], (y & 1) == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
            b.set(p[0] + 1, 6, p[1], Blocks.RED_CONCRETE);
            b.set(p[0] + 1, 5, p[1], Blocks.WHITE_CONCRETE);
        }
        for (int[] p : new int[][]{{6, 40}, {44, 40}, {18, 4}}) disc(b, p[0], p[1], 1, 1.8, Blocks.BROWN_TERRACOTTA.defaultBlockState());
        npc(b, "resident", 10, 1, 6);
        npc(b, "child", 40, 1, 7);
        // ---- the tiers
        cakeTier(b, c, 18, 1, 9);
        b.cylinder(c, c, 0, 0, 17, Blocks.PINK_GLAZED_TERRACOTTA, false);
        cakeTier(b, c, 13, 11, 19);
        cakeTier(b, c, 8, 21, 27);
        // strawberries on the terraces
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            int sx = c + (int) Math.round(Math.cos(a) * 15.5), sz = c + (int) Math.round(Math.sin(a) * 15.5);
            b.set(sx, 11, sz, Blocks.RED_CONCRETE);
            b.set(sx, 12, sz, Blocks.GREEN_CARPET);
            int tx = c + (int) Math.round(Math.cos(a) * 10.5), tz = c + (int) Math.round(Math.sin(a) * 10.5);
            b.set(tx, 21, tz, Blocks.RED_CONCRETE);
            b.set(tx, 22, tz, Blocks.GREEN_CARPET);
        }
        // giant candles on the top
        for (int k = 0; k < 5; k++) {
            double a = k * Math.PI * 2 / 5;
            int cx = c + (int) Math.round(Math.cos(a) * 5), cz = c + (int) Math.round(Math.sin(a) * 5);
            b.fill(cx, 29, cz, cx, 33, cz, Blocks.WHITE_CONCRETE);
            b.set(cx, 34, cz, Blocks.SHROOMLIGHT);
        }
        // windows
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int wx = c + (int) Math.round(Math.cos(a) * 18), wz = c + (int) Math.round(Math.sin(a) * 18);
            b.fill(wx, 4, wz, wx, 6, wz, Blocks.PINK_STAINED_GLASS);
            int ux = c + (int) Math.round(Math.cos(a) * 13), uz = c + (int) Math.round(Math.sin(a) * 13);
            b.fill(ux, 14, uz, ux, 16, uz, Blocks.PINK_STAINED_GLASS);
        }
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, c - 18, Blocks.CHERRY_DOOR, Direction.NORTH);
        b.fill(c - 2, 4, c - 18, c + 2, 4, c - 18, Blocks.GOLD_BLOCK);
        // ---- bottom tier: banquet hall and kitchen
        carpet(b, c - 1, 1, c - 17, c + 1, c - 12, Blocks.PINK_CARPET);
        for (int z = c - 9; z <= c + 7; z++) {
            table(b, c, 1, z, Blocks.CHERRY_SLAB);
            if ((z & 1) == 0) {
                chair(b, c - 1, 1, z, Blocks.CHERRY_STAIRS, Direction.WEST);
                chair(b, c + 1, 1, z, Blocks.CHERRY_STAIRS, Direction.EAST);
            }
            b.set(c, 2, z, (z % 3 == 0) ? Blocks.CANDLE_CAKE.defaultBlockState().setValue(CandleCakeBlock.LIT, true) : Blocks.CAKE.defaultBlockState());
        }
        for (int z = c - 8; z <= c + 6; z += 7) b.hanging(c, 9, z, Blocks.LANTERN);
        b.hanging(c - 8, 9, c, Blocks.LANTERN);
        npc(b, "waiter", c + 3, 1, c - 4);
        npc(b, "pirate", c - 4, 1, c + 2);
        b.fill(c - 12, 1, c + 10, c + 6, 9, c + 10, Blocks.SMOOTH_SANDSTONE);
        b.door(c - 3, 1, c + 10, Blocks.CHERRY_DOOR, Direction.NORTH);
        kitchenX(b, c - 7, 1, c + 15, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.barrel(c - 2, 1, c + 15, Buildings.LOOT_TAVERN);
        b.barrel(c - 1, 1, c + 15, Buildings.LOOT_TAVERN);
        table(b, c - 4, 1, c + 12, Blocks.SMOOTH_STONE_SLAB);
        b.set(c - 4, 2, c + 12, Blocks.CAKE);
        b.hanging(c - 4, 9, c + 13, Blocks.LANTERN);
        npc(b, "cook", c - 6, 1, c + 12);
        npc(b, "cook", c - 1, 1, c + 13);
        stairsUp(b, c + 10, 1, c + 4, Direction.NORTH, 10, Blocks.CHERRY_STAIRS, 2, Blocks.CHERRY_PLANKS);
        // ---- middle tier: throne room
        b.cylinder(c, c, 10, 10, 17, Blocks.SMOOTH_SANDSTONE, false);
        b.clear(c + 10, 10, c - 4, c + 11, 10, c + 1);
        carpet(b, c - 1, 11, c - 8, c + 1, c + 7, Blocks.MAGENTA_CARPET);
        b.fill(c - 3, 11, c + 8, c + 3, 11, c + 11, Blocks.GOLD_BLOCK);
        b.fill(c - 2, 12, c + 10, c + 2, 12, c + 11, Blocks.PINK_WOOL);
        b.set(c, 13, c + 10, stair(Blocks.PURPUR_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.fill(c, 13, c + 11, c, 16, c + 11, Blocks.PINK_WOOL);
        b.set(c, 17, c + 11, Blocks.GOLD_BLOCK);
        for (int k = 0; k < 6; k++) {
            int px = c + (k < 3 ? -6 : 6), pz = c - 6 + (k % 3) * 6;
            for (int y = 11; y <= 19; y++) b.set(px, y, pz, (y & 1) == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
        }
        b.hanging(c, 19, c, Blocks.LANTERN);
        b.hanging(c, 19, c + 6, Blocks.LANTERN);
        npc(b, "pirate_captain", c, 12, c + 8);
        npc(b, "pirate", c - 3, 11, c + 5);
        npc(b, "pirate", c + 3, 11, c + 5);
        // the mirror room: a vault sealed behind a wall of mirrors (tinted glass)
        b.fill(c - 12, 11, c - 3, c - 8, 15, c - 3, Blocks.SMOOTH_SANDSTONE);
        b.fill(c - 12, 11, c + 3, c - 8, 15, c + 3, Blocks.SMOOTH_SANDSTONE);
        b.fill(c - 12, 15, c - 2, c - 8, 15, c + 2, Blocks.SMOOTH_SANDSTONE);
        b.fill(c - 8, 11, c - 2, c - 8, 14, c + 2, Blocks.TINTED_GLASS);
        b.chest(c - 11, 11, c, Direction.EAST, Buildings.LOOT_TREASURE);
        b.set(c - 11, 11, c - 2, Blocks.GOLD_BLOCK);
        b.set(c - 11, 11, c + 2, Blocks.AMETHYST_BLOCK);
        b.set(c - 10, 14, c, Blocks.SHROOMLIGHT);
        mark(b, "treasure", c - 11, 11, c);
        stairsUp(b, c + 3, 11, c + 4, Direction.NORTH, 10, Blocks.CHERRY_STAIRS, 2, Blocks.CHERRY_PLANKS);
        // ---- top tier: private chamber, ladder to the roof
        b.cylinder(c, c, 20, 20, 12, Blocks.SMOOTH_SANDSTONE, false);
        b.clear(c + 3, 20, c - 5, c + 4, 20, c + 1);
        b.bed(c - 3, 21, c + 3, Blocks.PINK_BED, Direction.SOUTH);
        b.chest(c - 5, 21, c + 1, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(c - 2, 21, c - 5, facing(Blocks.LOOM, Direction.SOUTH));
        carpet(b, c - 3, 21, c - 2, c, c + 1, Blocks.PINK_CARPET);
        b.hanging(c, 27, c, Blocks.LANTERN);
        b.ladderUp(c - 7, 21, c, 8, Direction.EAST);
        b.clear(c - 7, 28, c, c - 7, 28, c);
        b.marker("arc_anchor", c, 1, c - 20, tag("arc", "tea_party"));
    }

    private NewWorldLandmarks() {}
}
