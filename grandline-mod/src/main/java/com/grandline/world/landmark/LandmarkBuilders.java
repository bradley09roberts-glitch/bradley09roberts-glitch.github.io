package com.grandline.world.landmark;

import com.grandline.registry.ModBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Builders for named set pieces. Coordinates are local to the landmark footprint. */
final class LandmarkBuilders {

    private static CompoundTag role(String r) {
        CompoundTag t = new CompoundTag();
        t.putString("role", r);
        return t;
    }

    private static CompoundTag tag(String k, String v) {
        CompoundTag t = new CompoundTag();
        t.putString(k, v);
        return t;
    }

    /** Fallback for landmarks without a dedicated builder: a plinth with a story marker (tracked as placeholder). */
    static void placeholder(Builder b, Island island, BuildStyle st, String id) {
        b.foundation(0, 0, b.w - 1, b.d - 1, st.foundation, 10);
        b.fill(0, 0, 0, b.w - 1, 0, b.d - 1, st.foundation);
        b.clear(0, 1, 0, b.w - 1, 6, b.d - 1);
        int cx = b.w / 2, cz = b.d / 2;
        b.fill(cx - 1, 1, cz - 1, cx + 1, 1, cz + 1, st.accent);
        b.fill(cx, 2, cz, cx, 4, cz, st.frame);
        b.set(cx, 5, cz, st.light);
        b.marker("landmark", cx, 1, cz, tag("id", id));
    }

    static void starterShrine(Builder b, Island island, BuildStyle st) {
        b.foundation(0, 0, 8, 8, Blocks.STONE_BRICKS, 10);
        b.fill(0, 0, 0, 8, 0, 8, Blocks.POLISHED_ANDESITE);
        b.clear(0, 1, 0, 8, 8, 8);
        for (int[] c : new int[][]{{1, 1}, {7, 1}, {1, 7}, {7, 7}}) {
            b.fill(c[0], 1, c[1], c[0], 4, c[1], Blocks.STRIPPED_OAK_LOG);
            b.set(c[0], 5, c[1], Blocks.LANTERN);
        }
        b.fill(3, 1, 3, 5, 1, 5, Blocks.CHISELED_STONE_BRICKS);
        b.set(4, 2, 4, Blocks.LECTERN);
        b.marker("respawn_anchor", 4, 1, 2, new CompoundTag());
        b.marker("npc", 4, 1, 6, role("elder"));
        b.chest(2, 1, 6, Direction.SOUTH, "grandline:chests/starter");
    }

    static void treasureCache(Builder b, Island island, BuildStyle st) {
        b.foundation(0, 0, 6, 6, Blocks.COBBLESTONE, 6);
        b.clear(0, 1, 0, 6, 4, 6);
        b.fill(0, 0, 0, 6, 0, 6, Blocks.COARSE_DIRT);
        // buried chest under an X
        b.set(3, 0, 3, Blocks.RED_WOOL);
        b.set(2, 0, 2, Blocks.RED_WOOL);
        b.set(4, 0, 4, Blocks.RED_WOOL);
        b.set(2, 0, 4, Blocks.RED_WOOL);
        b.set(4, 0, 2, Blocks.RED_WOOL);
        b.fill(2, -3, 2, 4, -1, 4, Blocks.DIRT);
        b.chest(3, -2, 3, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.set(0, 1, 0, Blocks.SKELETON_SKULL);
        b.marker("treasure", 3, -2, 3, new CompoundTag());
    }

    static void ancientRuin(Builder b, Island island, BuildStyle st) {
        b.foundation(0, 0, 14, 14, Blocks.MOSSY_STONE_BRICKS, 10);
        b.fill(0, 0, 0, 14, 0, 14, Blocks.MOSSY_STONE_BRICKS);
        b.clear(0, 1, 0, 14, 12, 14);
        for (int i = 0; i < 6; i++) {
            int px = 2 + (i % 3) * 5, pz = 2 + (i / 3) * 10;
            int h = 3 + b.rand(i * 17, 7);
            for (int y = 1; y <= h; y++) b.set(px, y, pz, y % 3 == 0 ? Blocks.CHISELED_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS);
        }
        b.set(7, 1, 7, ModBlocks.PONEGLYPH.get());
        b.set(7, 2, 7, ModBlocks.PONEGLYPH.get());
        b.marker("poneglyph", 7, 1, 7, tag("kind", b.rand(3, 5) == 0 ? "road" : "historical"));
        b.chest(1, 1, 13, Direction.EAST, Buildings.LOOT_RUIN);
        b.marker("npc", 4, 1, 7, role("ruin_guardian"));
    }

    static void fruitTreeGrove(Builder b, Island island, BuildStyle st) {
        b.foundation(0, 0, 10, 10, Blocks.DIRT, 6);
        b.fill(0, 0, 0, 10, 0, 10, Blocks.GRASS_BLOCK);
        b.clear(0, 1, 0, 10, 12, 10);
        int cx = 5, cz = 5;
        b.fill(cx, 1, cz, cx, 6, cz, Blocks.JUNGLE_LOG);
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                for (int y = 5; y <= 8; y++) {
                    if (x * x + z * z + (y - 6) * (y - 6) * 2 > 12) continue;
                    if (x == 0 && z == 0 && y < 7) continue;
                    b.set(cx + x, y, cz + z, Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                }
        b.set(cx + 1, 5, cz, ModBlocks.FRUIT_BLOOM.get());
        b.marker("fruit_spawn", cx + 1, 5, cz, new CompoundTag());
        for (int i = 0; i < 6; i++) b.set(1 + b.rand(i, 9), 1, 1 + b.rand(i + 9, 9), Blocks.ALLIUM);
    }

    static void windmill(Builder b, Island island, BuildStyle st) {
        b.foundation(0, 0, 8, 8, Blocks.COBBLESTONE, 10);
        b.clear(0, 1, 0, 8, 22, 8);
        b.fill(0, 0, 0, 8, 0, 8, Blocks.COBBLESTONE);
        for (int y = 1; y <= 12; y++) {
            int r = y < 8 ? 3 : 2;
            b.cylinder(4, 4, y, y, r, Blocks.WHITE_TERRACOTTA, true);
        }
        b.cylinder(4, 4, 13, 13, 3, Blocks.SPRUCE_PLANKS, false);
        b.cylinder(4, 4, 14, 15, 2, Blocks.SPRUCE_PLANKS, false);
        b.set(4, 16, 4, Blocks.SPRUCE_SLAB);
        b.door(4, 1, 1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        // sails
        b.set(4, 11, 0, Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        for (int i = 1; i <= 6; i++) {
            b.set(4, 11 + i, -1, Blocks.WHITE_WOOL);
            b.set(4, 11 - i, -1, Blocks.WHITE_WOOL);
            b.set(4 + i, 11, -1, Blocks.WHITE_WOOL);
            b.set(4 - i, 11, -1, Blocks.WHITE_WOOL);
        }
        b.fill(4, 1, 4, 4, 10, 4, Blocks.SPRUCE_LOG);
        b.marker("npc", 4, 1, 3, role("miller"));
    }

    static void baratie(Builder b, Island island, BuildStyle st) {
        // A floating restaurant ship: a fish-shaped hull sitting on the water (y=0 is sea level).
        int w = b.w, d = b.d;
        for (int z = 0; z < d; z++) {
            double t = z / (double) (d - 1);
            int half = (int) Math.round((w / 2.0 - 1) * Math.sin(Math.PI * Math.min(1, t * 1.15)));
            half = Math.max(2, half);
            int cx = w / 2;
            b.fill(cx - half, -3, z, cx + half, 0, z, Blocks.DARK_OAK_PLANKS);
            b.fill(cx - half + 1, -2, z, cx + half - 1, 0, z, Blocks.SPRUCE_PLANKS);
            b.fill(cx - half, 1, z, cx + half, 1, z, Blocks.OAK_PLANKS);
            b.clear(cx - half, 2, z, cx + half, 14, z);
            b.set(cx - half, 2, z, Blocks.DARK_OAK_FENCE);
            b.set(cx + half, 2, z, Blocks.DARK_OAK_FENCE);
        }
        // restaurant hall
        int x1 = 3, x2 = w - 4, z1 = 10, z2 = 32;
        b.walls(x1, 2, z1, x2, 7, z2, Blocks.WHITE_TERRACOTTA);
        b.fill(x1, 8, z1, x2, 8, z2, Blocks.RED_TERRACOTTA);
        b.fill(x1 + 1, 9, z1 + 1, x2 - 1, 9, z2 - 1, Blocks.RED_TERRACOTTA);
        for (int z = z1 + 2; z < z2 - 1; z += 3) {
            b.fill(x1, 4, z, x1, 5, z, Blocks.GLASS_PANE);
            b.fill(x2, 4, z, x2, 5, z, Blocks.GLASS_PANE);
        }
        b.bigDoor(w / 2, 2, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        for (int z = z1 + 3; z < z2 - 6; z += 4)
            for (int x = x1 + 3; x < x2 - 1; x += 5) {
                b.set(x, 2, z, Blocks.SPRUCE_FENCE);
                b.set(x, 3, z, Blocks.WHITE_CARPET);
                b.stairs(x - 1, 2, z, Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM);
                b.stairs(x + 1, 2, z, Blocks.SPRUCE_STAIRS, Direction.WEST, Half.BOTTOM);
            }
        // kitchen
        b.fill(x1 + 1, 2, z2 - 4, x2 - 1, 2, z2 - 4, Blocks.SMOOTH_STONE);
        b.set(x1 + 2, 2, z2 - 2, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.NORTH));
        b.set(x1 + 3, 2, z2 - 2, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.NORTH));
        b.set(x2 - 2, 2, z2 - 2, Blocks.CAMPFIRE);
        b.barrel(x2 - 3, 2, z2 - 2, Buildings.LOOT_TAVERN);
        for (int x = x1 + 2; x < x2; x += 3) b.hanging(x, 7, (z1 + z2) / 2, Blocks.LANTERN);
        // fins (dining decks) and the head
        b.fill(0, 1, 18, 2, 1, 24, Blocks.OAK_PLANKS);
        b.fill(w - 3, 1, 18, w - 1, 1, 24, Blocks.OAK_PLANKS);
        b.fill(w / 2 - 2, 2, 1, w / 2 + 2, 4, 3, Blocks.ORANGE_TERRACOTTA);
        b.set(w / 2 - 1, 4, 1, Blocks.BLACK_CONCRETE);
        b.set(w / 2 + 1, 4, 1, Blocks.BLACK_CONCRETE);
        b.marker("npc", w / 2, 2, z2 - 3, role("head_chef"));
        b.marker("npc", w / 2 - 3, 2, z2 - 3, role("cook"));
        b.marker("npc", w / 2 + 3, 2, z2 - 3, role("cook"));
        b.marker("npc", w / 2, 2, z1 + 3, role("waiter"));
        b.marker("dock", w / 2, 1, 0, new CompoundTag());
    }

    static void arlongPark(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, Blocks.STONE_BRICKS, 14);
        b.clear(0, 1, 0, w - 1, 22, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.STONE_BRICKS);
        // outer wall
        b.walls(0, 1, 0, w - 1, 6, d - 1, Blocks.STONE_BRICKS);
        b.fill(0, 7, 0, w - 1, 7, 0, Blocks.STONE_BRICK_WALL);
        b.fill(0, 7, d - 1, w - 1, 7, d - 1, Blocks.STONE_BRICK_WALL);
        b.fill(0, 7, 0, 0, 7, d - 1, Blocks.STONE_BRICK_WALL);
        b.fill(w - 1, 7, 0, w - 1, 7, d - 1, Blocks.STONE_BRICK_WALL);
        // gate
        b.clear(w / 2 - 2, 1, 0, w / 2 + 2, 5, 0);
        // inner pool connected to the sea
        b.fill(4, -3, 4, w - 5, 0, 12, Blocks.WATER);
        // central tower
        int tx = w / 2;
        for (int y = 1; y <= 18; y++) b.cylinder(tx, 21, y, y, y < 14 ? 5 : 3, Blocks.LIGHT_GRAY_CONCRETE, true);
        b.cylinder(tx, 21, 19, 19, 3, Blocks.CYAN_TERRACOTTA, false);
        b.cylinder(tx, 21, 6, 6, 4, Blocks.SMOOTH_STONE, false);
        b.cylinder(tx, 21, 12, 12, 4, Blocks.SMOOTH_STONE, false);
        b.door(tx, 1, 16, Blocks.IRON_DOOR, Direction.NORTH);
        b.clear(tx, 1, 16, tx, 3, 16);
        b.ladderUp(tx, 1, 25, 18, Direction.NORTH);
        b.chest(tx - 2, 13, 21, Direction.EAST, Buildings.LOOT_TREASURE);
        // throne terrace
        b.fill(tx - 3, 1, 14, tx + 3, 1, 15, Blocks.POLISHED_DEEPSLATE);
        b.stairs(tx, 2, 15, Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH, Half.BOTTOM);
        b.marker("npc", tx, 2, 14, role("fishman_boss"));
        b.marker("npc", 6, 1, 15, role("fishman_brute"));
        b.marker("npc", w - 7, 1, 15, role("fishman_brute"));
        b.marker("npc", 8, 1, 2, role("fishman_guard"));
        b.marker("npc", w - 9, 1, 2, role("fishman_guard"));
        b.marker("arc_anchor", tx, 1, 10, tag("arc", "fishman_occupation"));
    }

    static void executionPlatform(Builder b, Island island, BuildStyle st) {
        int c = 6;
        b.foundation(0, 0, 12, 12, Blocks.STONE_BRICKS, 8);
        b.fill(0, 0, 0, 12, 0, 12, Blocks.POLISHED_ANDESITE);
        b.clear(0, 1, 0, 12, 20, 12);
        b.fill(c - 3, 1, c - 3, c + 3, 9, c + 3, Blocks.DARK_OAK_PLANKS);
        b.clear(c - 2, 1, c - 2, c + 2, 8, c + 2);
        b.fill(c - 4, 10, c - 4, c + 4, 10, c + 4, Blocks.DARK_OAK_PLANKS);
        b.walls(c - 4, 11, c - 4, c + 4, 11, c + 4, Blocks.DARK_OAK_FENCE);
        for (int y = 11; y <= 16; y++) {
            b.set(c - 2, y, c, Blocks.DARK_OAK_LOG);
            b.set(c + 2, y, c, Blocks.DARK_OAK_LOG);
        }
        b.fill(c - 2, 17, c, c + 2, 17, c, Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        b.ladderUp(c, 1, c - 2, 10, Direction.SOUTH);
        b.clear(c, 1, c - 3, c, 2, c - 3);
        b.marker("legend", c, 11, c, tag("story", "execution_platform"));
        b.marker("npc", c, 1, 0, role("marine"));
        b.marker("npc", c + 4, 1, 1, role("marine"));
    }

    static void marineBase(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, Blocks.STONE_BRICKS, 16);
        b.clear(0, 1, 0, w - 1, 30, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.SMOOTH_STONE);
        b.walls(0, 1, 0, w - 1, 5, d - 1, Blocks.WHITE_CONCRETE);
        b.fill(0, 6, 0, w - 1, 6, 0, Blocks.STONE_BRICK_WALL);
        b.fill(0, 6, d - 1, w - 1, 6, d - 1, Blocks.STONE_BRICK_WALL);
        b.fill(0, 6, 0, 0, 6, d - 1, Blocks.STONE_BRICK_WALL);
        b.fill(w - 1, 6, 0, w - 1, 6, d - 1, Blocks.STONE_BRICK_WALL);
        b.clear(w / 2 - 2, 1, 0, w / 2 + 2, 4, 0);
        // main building
        int x1 = 6, x2 = w - 7, z1 = 9, z2 = d - 3;
        for (int floor = 0; floor < 4; floor++) {
            int y0 = 1 + floor * 5;
            b.walls(x1, y0, z1, x2, y0 + 4, z2, Blocks.WHITE_CONCRETE);
            b.fill(x1, y0 + 4, z1, x2, y0 + 4, z1, Blocks.BLUE_CONCRETE);
            b.fill(x1 + 1, y0 + 4, z1 + 1, x2 - 1, y0 + 4, z2 - 1, Blocks.SMOOTH_STONE);
            for (int x = x1 + 2; x < x2 - 1; x += 3) b.fill(x, y0 + 1, z1, x, y0 + 2, z1, Blocks.GLASS_PANE);
            b.hanging((x1 + x2) / 2, y0 + 3, (z1 + z2) / 2, Blocks.LANTERN);
            b.ladderUp(x2 - 1, y0, z2 - 1, 5, Direction.NORTH);
        }
        b.fill(x1, 21, z1, x2, 21, z2, Blocks.SMOOTH_STONE);
        // big MARINE letters stand-in: blue band and flag
        b.fill(x1, 22, z1, x2, 22, z1, Blocks.BLUE_CONCRETE);
        b.fill((x1 + x2) / 2, 22, (z1 + z2) / 2, (x1 + x2) / 2, 28, (z1 + z2) / 2, Blocks.IRON_BARS);
        b.set((x1 + x2) / 2, 28, (z1 + z2) / 2 - 1, Blocks.BLUE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.NORTH));
        b.bigDoor((x1 + x2) / 2, 1, z1, Blocks.IRON_DOOR, Direction.NORTH);
        b.set((x1 + x2) / 2 + 1, 2, z1 - 1, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.NORTH));
        b.set((x1 + x2) / 2 - 1, 2, z1 + 1, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH));
        // cells
        b.fill(x1 + 1, 1, z2 - 4, x1 + 6, 3, z2 - 4, Blocks.IRON_BARS);
        b.marker("cell", x1 + 3, 1, z2 - 2, new CompoundTag());
        b.chest(x2 - 2, 16, z2 - 2, Direction.WEST, Buildings.LOOT_MARINE);
        b.set(x1 + 2, 16, z1 + 2, Blocks.LECTERN);
        // training yard
        for (int x = 3; x < w - 3; x += 4) b.set(x, 1, 4, Blocks.TARGET);
        b.marker("npc", w / 2, 1, 3, role("marine"));
        b.marker("npc", 4, 1, 6, role("marine"));
        b.marker("npc", w - 5, 1, 6, role("marine"));
        b.marker("npc", (x1 + x2) / 2, 16, (z1 + z2) / 2, role("marine_captain"));
        b.marker("npc", (x1 + x2) / 2, 6, (z1 + z2) / 2, role("marine_officer"));
        b.marker("bounty_board", w / 2 + 3, 2, 1, new CompoundTag());
    }

    static void marineHeadquarters(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2;
        b.foundation(0, 0, w - 1, d - 1, Blocks.STONE_BRICKS, 20);
        b.clear(0, 1, 0, w - 1, 70, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.SMOOTH_STONE);
        // plaza
        b.walls(0, 1, 0, w - 1, 2, d - 1, Blocks.STONE_BRICK_WALL);
        b.clear(c - 3, 1, 0, c + 3, 2, 0);
        // tiered tower
        int[][] tiers = {{14, 0, 14}, {11, 15, 26}, {8, 27, 40}, {5, 41, 52}};
        for (int[] t : tiers) {
            int r = t[0];
            for (int y = t[1] + 1; y <= t[2]; y++) {
                b.walls(c - r, y, c - r, c + r, y, c + r, (y % 6 == 0) ? Blocks.BLUE_CONCRETE : Blocks.WHITE_CONCRETE);
            }
            b.fill(c - r, t[2] + 1, c - r, c + r, t[2] + 1, c + r, Blocks.SMOOTH_STONE);
            for (int x = c - r + 2; x < c + r - 1; x += 3)
                for (int y = t[1] + 3; y < t[2]; y += 5) b.set(x, y, c - r, Blocks.GLASS_PANE);
            b.hanging(c, t[2], c, Blocks.LANTERN);
        }
        b.cylinder(c, c, 53, 58, 3, Blocks.WHITE_CONCRETE, true);
        b.dome(c, 59, c, 3, Blocks.LIGHT_BLUE_CONCRETE);
        b.bigDoor(c, 1, c - 14, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c + 1, 2, c - 15, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.NORTH));
        b.set(c - 1, 2, c - 13, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH));
        // internal ladder shaft
        b.ladderUp(c, 1, c + 1, 52, Direction.SOUTH);
        b.fill(c, 1, c + 2, c, 52, c + 2, Blocks.SMOOTH_STONE);
        for (int[] t : tiers) b.clear(c, t[2] + 1, c + 1, c, t[2] + 1, c + 1);
        b.chest(c + 3, 42, c + 3, Direction.WEST, Buildings.LOOT_MARINE);
        b.marker("npc", c, 1, c - 10, role("marine"));
        b.marker("npc", c - 6, 1, c - 6, role("marine"));
        b.marker("npc", c + 6, 1, c - 6, role("marine"));
        b.marker("npc", c, 16, c, role("vice_admiral"));
        b.marker("npc", c, 42, c, role("admiral"));
        b.marker("bounty_board", c + 4, 2, c - 15, new CompoundTag());
    }

    static void drumCastle(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        b.foundation(0, 0, w - 1, w - 1, Blocks.STONE_BRICKS, 8);
        b.clear(0, 1, 0, w - 1, 40, w - 1);
        b.fill(0, 0, 0, w - 1, 0, w - 1, Blocks.STONE_BRICKS);
        b.walls(2, 1, 2, w - 3, 12, w - 3, Blocks.STONE_BRICKS);
        b.fill(2, 13, 2, w - 3, 13, w - 3, Blocks.STONE_BRICKS);
        for (int[] t : new int[][]{{3, 3}, {w - 4, 3}, {3, w - 4}, {w - 4, w - 4}}) {
            for (int y = 1; y <= 22; y++) b.cylinder(t[0], t[1], y, y, 2, Blocks.STONE_BRICKS, true);
            b.dome(t[0], 23, t[1], 2, Blocks.BLUE_TERRACOTTA);
        }
        for (int y = 14; y <= 26; y++) b.cylinder(c, c, y, y, 4, Blocks.STONE_BRICKS, true);
        b.dome(c, 27, c, 4, Blocks.BLUE_TERRACOTTA);
        b.bigDoor(c, 1, 2, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.fill(4, 1, w - 6, w - 5, 1, w - 6, Blocks.BREWING_STAND);
        b.set(c, 1, w - 5, Blocks.BREWING_STAND);
        b.bed(4, 1, 5, Blocks.WHITE_BED, Direction.SOUTH);
        b.bed(6, 1, 5, Blocks.WHITE_BED, Direction.SOUTH);
        b.chest(w - 5, 1, 5, Direction.WEST, Buildings.LOOT_TREASURE);
        b.ladderUp(c, 1, w - 4, 13, Direction.NORTH);
        b.marker("npc", c, 1, c, role("doctor"));
        b.marker("npc", c + 3, 1, c, role("apprentice_doctor"));
        b.marker("arc_anchor", c, 1, c - 4, tag("arc", "winter_kingdom"));
    }

    static void twinCapesLighthouse(Builder b, Island island, BuildStyle st) {
        b.foundation(0, 0, b.w - 1, b.d - 1, Blocks.STONE_BRICKS, 12);
        b.clear(0, 1, 0, b.w - 1, 26, b.d - 1);
        int c = b.w / 2;
        for (int y = 0; y <= 18; y++) b.cylinder(c, c, y, y, 3, (y / 3) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.RED_CONCRETE, y > 0);
        b.cylinder(c, c, 19, 19, 3, Blocks.SMOOTH_STONE, false);
        b.set(c, 20, c, Blocks.GLOWSTONE);
        b.set(c, 21, c, Blocks.SEA_LANTERN);
        b.cylinder(c, c, 20, 21, 2, Blocks.GLASS, true);
        b.cylinder(c, c, 22, 22, 2, Blocks.SMOOTH_STONE_SLAB, false);
        b.ladderUp(c, 1, c - 2, 18, Direction.SOUTH);
        b.door(c, 1, c - 3, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.marker("npc", c, 1, c, role("lighthouse_keeper"));
        b.marker("log_pose_vendor", c + 1, 1, c, new CompoundTag());
    }

    static void seaTrainStation(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d;
        b.foundation(0, 0, w - 1, d - 1, Blocks.STONE_BRICKS, 10);
        b.clear(0, 1, 0, w - 1, 10, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.POLISHED_ANDESITE);
        b.walls(1, 1, 3, w - 2, 5, d - 2, Blocks.BRICKS);
        b.fill(1, 6, 3, w - 2, 6, d - 2, Blocks.DARK_OAK_PLANKS);
        b.bigDoor(w / 2, 1, 3, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.fill(0, 1, 0, w - 1, 1, 0, Blocks.RAIL);
        b.set(w / 2, 1, d - 3, Blocks.BELL);
        b.marker("npc", w / 2, 1, d / 2, role("station_master"));
        b.marker("sea_train_stop", w / 2, 1, 1, new CompoundTag());
    }

    static void water7Fountain(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        b.foundation(0, 0, w - 1, w - 1, Blocks.STONE_BRICKS, 12);
        b.clear(0, 1, 0, w - 1, 34, w - 1);
        b.fill(0, 0, 0, w - 1, 0, w - 1, Blocks.SMOOTH_QUARTZ);
        b.cylinder(c, c, 1, 2, 9, Blocks.QUARTZ_BRICKS, true);
        b.cylinder(c, c, 1, 1, 8, Blocks.WATER, false);
        b.cylinder(c, c, 1, 24, 2, Blocks.QUARTZ_PILLAR, false);
        b.cylinder(c, c, 25, 25, 5, Blocks.QUARTZ_BRICKS, false);
        b.cylinder(c, c, 26, 26, 4, Blocks.WATER, false);
        b.cylinder(c, c, 27, 30, 1, Blocks.QUARTZ_PILLAR, false);
        b.set(c, 31, c, Blocks.WATER);
        b.marker("npc", 2, 1, c, role("canal_guide"));
        b.marker("town_center", c, 1, 1, new CompoundTag());
    }

    static void towerOfJustice(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        b.foundation(0, 0, w - 1, w - 1, Blocks.QUARTZ_BRICKS, 14);
        b.clear(0, 1, 0, w - 1, 50, w - 1);
        b.fill(0, 0, 0, w - 1, 0, w - 1, Blocks.QUARTZ_BRICKS);
        for (int floor = 0; floor < 8; floor++) {
            int y0 = 1 + floor * 5;
            int r = 7 - floor / 3;
            b.walls(c - r, y0, c - r, c + r, y0 + 4, c + r, Blocks.QUARTZ_BRICKS);
            b.fill(c - r, y0 + 4, c - r, c + r, y0 + 4, c + r, Blocks.SMOOTH_QUARTZ);
            for (int x = c - r + 2; x < c + r - 1; x += 3) b.fill(x, y0 + 1, c - r, x, y0 + 2, c - r, Blocks.GLASS_PANE);
            b.ladderUp(c, y0, c + r - 1, 5, Direction.NORTH);
            b.hanging(c, y0 + 3, c, Blocks.LANTERN);
        }
        b.set(c, 41, c, Blocks.LIGHTNING_ROD);
        b.fill(c - 1, 41, c - 1, c + 1, 41, c + 1, Blocks.CHISELED_QUARTZ_BLOCK);
        b.bigDoor(c, 1, c - 7, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c + 1, 2, c - 8, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.NORTH));
        b.set(c - 1, 2, c - 6, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH));
        b.chest(c - 2, 36, c, Direction.EAST, Buildings.LOOT_TREASURE);
        b.marker("npc", c, 1, c, role("cp_agent"));
        b.marker("npc", c, 11, c, role("cp_agent"));
        b.marker("npc", c, 21, c, role("cp_agent"));
        b.marker("npc", c, 36, c, role("cp_leader"));
    }

    static void impelDown(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        b.foundation(0, 0, w - 1, w - 1, ModBlocks.SEASTONE_BRICKS.get(), 30);
        b.clear(0, 1, 0, w - 1, 40, w - 1);
        b.fill(0, 0, 0, w - 1, 0, w - 1, ModBlocks.SEASTONE_BRICKS.get());
        for (int y = 1; y <= 30; y++) b.cylinder(c, c, y, y, 12, Blocks.DEEPSLATE_BRICKS, true);
        b.cylinder(c, c, 31, 31, 12, Blocks.DEEPSLATE_TILES, false);
        b.cylinder(c, c, 32, 34, 3, Blocks.DEEPSLATE_BRICKS, true);
        b.dome(c, 35, c, 3, Blocks.DEEPSLATE_TILES);
        // levels going down into the sea floor
        for (int lvl = 0; lvl < 5; lvl++) {
            int y0 = -6 - lvl * 6;
            b.cylinder(c, c, y0, y0 + 5, 11, ModBlocks.SEASTONE_BRICKS.get(), true);
            b.cylinder(c, c, y0, y0, 10, Blocks.DEEPSLATE_TILES, false);
            b.cylinder(c, c, y0 + 1, y0 + 5, 10, Blocks.AIR, false);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                int cx = c + (int) Math.round(Math.cos(a) * 7), cz = c + (int) Math.round(Math.sin(a) * 7);
                b.fill(cx, y0 + 1, cz, cx, y0 + 3, cz, Blocks.IRON_BARS);
            }
            b.marker("npc", c + 3, y0 + 1, c, role(lvl < 2 ? "jailer" : "jailer_beast"));
            b.marker("cell", c + 7, y0 + 1, c, CompoundTagOf("level", lvl + 1));
        }
        b.cylinder(c, c, -36, 30, 1, Blocks.AIR, false);
        b.ladderUp(c, -35, c + 2, 65, Direction.NORTH);
        b.fill(c, -35, c + 3, c, 30, c + 3, Blocks.DEEPSLATE_BRICKS);
        b.bigDoor(c, 1, c - 12, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(c + 1, 2, c - 13, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.NORTH));
        b.set(c - 1, 2, c - 11, Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH));
        b.marker("npc", c, 1, c - 6, role("warden"));
        b.marker("prison", c, 1, c, new CompoundTag());
    }

    private static CompoundTag CompoundTagOf(String k, int v) {
        CompoundTag t = new CompoundTag();
        t.putInt(k, v);
        return t;
    }

    static void swordDojo(Builder b, Island island, BuildStyle st) {
        Buildings.house(b, BuildStyle.WANO, 1, Buildings.Interior.DOJO);
    }

    static void knockUpMarker(Builder b, Island island, BuildStyle st) {
        // a ring of white foam over a dark whirlpool: the Knock Up Stream launch point
        int c = b.w / 2;
        b.cylinder(c, c, 0, 0, 2, Blocks.WATER, false);
        b.set(c, -1, c, Blocks.MAGMA_BLOCK);
        b.set(c, -2, c, Blocks.MAGMA_BLOCK);
        b.marker("knock_up_stream", c, 0, c, new CompoundTag());
    }

    static void giantTree(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        b.clear(0, 1, 0, w - 1, 130, w - 1);
        b.foundation(0, 0, w - 1, w - 1, Blocks.DIRT, 10);
        for (int y = 0; y <= 95; y++) {
            int r = y < 10 ? 10 - y / 3 : 7;
            b.cylinder(c, c, y, y, r, Blocks.SPRUCE_WOOD, false);
            if (y > 2 && y < 94) b.cylinder(c, c, y, y, r - 2, Blocks.AIR, false);
        }
        // spiral stair inside the trunk
        for (int y = 1; y < 94; y++) {
            double a = y * 0.35;
            int sx = c + (int) Math.round(Math.cos(a) * 4), sz = c + (int) Math.round(Math.sin(a) * 4);
            b.set(sx, y, sz, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            if (y % 12 == 0) b.set(sx, y + 1, sz, Blocks.LANTERN);
        }
        b.bigDoor(c, 3, c - 7, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.clear(c - 1, 3, c - 8, c + 1, 6, c - 6);
        // canopy
        for (int x = -18; x <= 18; x++)
            for (int z = -18; z <= 18; z++)
                for (int y = 90; y <= 112; y++) {
                    double dd = (x * x + z * z) / 324.0 + Math.pow((y - 100) / 11.0, 2);
                    if (dd > 1) continue;
                    if (b.rand(x * 131 + z * 17 + y, 9) == 0) continue;
                    b.set(c + x, y, c + z, Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                }
        b.fill(c - 6, 96, c - 6, c + 6, 96, c + 6, Blocks.SPRUCE_PLANKS);
        b.chest(c, 97, c, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.marker("npc", c + 2, 97, c, role("giant_elder"));
        b.marker("npc", c, 3, c - 10, role("giant_warrior"));
    }

    static void giantTreeRuined(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        b.clear(0, 1, 0, w - 1, 50, w - 1);
        b.foundation(0, 0, w - 1, w - 1, Blocks.DIRT, 10);
        for (int y = 0; y <= 30; y++) {
            int r = y < 6 ? 12 - y / 2 : 9;
            b.cylinder(c, c, y, y, r, y > 20 ? Blocks.BLACKSTONE : Blocks.STRIPPED_DARK_OAK_WOOD, y > 1);
        }
        for (int y = 2; y <= 20; y += 3) b.cylinder(c, c, y, y, 8, Blocks.BOOKSHELF, true);
        b.bigDoor(c, 1, c - 9, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.set(c, 1, c, ModBlocks.PONEGLYPH.get());
        b.marker("poneglyph", c, 1, c, tag("kind", "historical"));
        b.chest(c + 3, 1, c + 3, Direction.WEST, Buildings.LOOT_RUIN);
        b.marker("npc", c - 3, 1, c, role("scholar"));
    }

    static void laughTaleRuins(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        b.foundation(0, 0, w - 1, w - 1, Blocks.MOSSY_STONE_BRICKS, 12);
        b.clear(0, 1, 0, w - 1, 30, w - 1);
        b.fill(0, 0, 0, w - 1, 0, w - 1, Blocks.MOSSY_STONE_BRICKS);
        for (int y = 1; y <= 14; y++) {
            int r = 11 - y * 2 / 3;
            b.walls(c - r, y, c - r, c + r, y, c + r, y % 4 == 0 ? Blocks.GILDED_BLACKSTONE : Blocks.MOSSY_STONE_BRICKS);
        }
        b.bigDoor(c, 1, c - 11, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.fill(c - 1, 1, c - 1, c + 1, 3, c + 1, ModBlocks.PONEGLYPH.get());
        b.marker("poneglyph", c, 1, c, tag("kind", "final"));
        b.chest(c, 4, c, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.marker("arc_anchor", c, 1, c - 6, tag("arc", "final_island"));
    }

    static void maryGeoise(Builder b, Island island, BuildStyle st) {
        // Holy Land on top of the Red Line (y fixed at the wall top)
        int w = b.w, c = w / 2;
        b.fill(0, 0, 0, w - 1, 0, w - 1, Blocks.QUARTZ_BRICKS);
        b.clear(0, 1, 0, w - 1, 40, w - 1);
        for (int y = 1; y <= 24; y++) b.cylinder(c, c, y, y, 8, Blocks.QUARTZ_BRICKS, true);
        b.dome(c, 25, c, 8, Blocks.GOLD_BLOCK);
        for (int[] t : new int[][]{{4, 4}, {w - 5, 4}, {4, w - 5}, {w - 5, w - 5}}) {
            for (int y = 1; y <= 30; y++) b.cylinder(t[0], t[1], y, y, 2, Blocks.QUARTZ_PILLAR, false);
            b.dome(t[0], 31, t[1], 2, Blocks.GOLD_BLOCK);
        }
        b.bigDoor(c, 1, c - 8, Blocks.IRON_DOOR, Direction.NORTH);
        b.marker("npc", c, 1, c, role("celestial_guard"));
        b.marker("arc_anchor", c, 1, c - 4, tag("arc", "holy_land"));
    }

    private LandmarkBuilders() {}
}
