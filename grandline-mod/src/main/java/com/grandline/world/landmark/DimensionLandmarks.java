package com.grandline.world.landmark;

import com.grandline.registry.ModBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Set pieces of the White Sea (sky) and the deep sea. Coordinates are local to each footprint. */
final class DimensionLandmarks {
    interface Registrar {
        void reg(String id, Landmarks.Spec spec, Landmarks.LandmarkBuilder builder);
    }

    static void registerAll(Registrar r) {
        r.reg("heavens_gate", new Landmarks.Spec(23, 17, true, false, 0), DimensionLandmarks::heavensGate);
        r.reg("cloud_chapel", new Landmarks.Spec(19, 19, true, false, 0), DimensionLandmarks::cloudChapel);
        r.reg("shandora_ruins", new Landmarks.Spec(47, 47, true, false, 0), DimensionLandmarks::shandoraRuins);
        r.reg("golden_belfry", new Landmarks.Spec(17, 17, true, false, 0), DimensionLandmarks::goldenBelfry);
        r.reg("sacrificial_altar", new Landmarks.Spec(17, 17, true, false, 0), DimensionLandmarks::sacrificialAltar);
        r.reg("weather_lab", new Landmarks.Spec(21, 21, true, false, 0), DimensionLandmarks::weatherLab);
        r.reg("cloud_end_gate", new Landmarks.Spec(13, 11, true, false, 0), DimensionLandmarks::cloudEndGate);
        r.reg("ryugu_palace", new Landmarks.Spec(43, 43, true, false, 0), DimensionLandmarks::ryuguPalace);
        r.reg("mermaid_cove", new Landmarks.Spec(27, 27, false, true, 0), DimensionLandmarks::mermaidCove);
        r.reg("gyoncorde_plaza", new Landmarks.Spec(29, 29, true, false, 0), DimensionLandmarks::gyoncordePlaza);
        r.reg("sea_forest_graveyard", new Landmarks.Spec(37, 37, true, false, 0), DimensionLandmarks::seaForest);
    }

    private static CompoundTag role(String r) {
        CompoundTag t = new CompoundTag();
        t.putString("role", r);
        return t;
    }

    private static Block cloud() {
        return ModBlocks.ISLAND_CLOUD.get();
    }

    // ------------------------------------------------------------------ the White Sea

    /** A towering cloud arch with a toll booth: the entrance to the sky islands. */
    static void heavensGate(Builder b, Island isl, BuildStyle st) {
        b.foundation(0, 0, 22, 16, cloud(), 8);
        b.fill(0, 0, 0, 22, 0, 16, cloud());
        b.clear(0, 1, 0, 22, 24, 16);
        // two pillars and the arch
        for (int px : new int[]{2, 18}) {
            b.fill(px, 1, 6, px + 2, 18, 8, Blocks.WHITE_CONCRETE);
            b.fill(px - 1, 1, 5, px + 3, 2, 9, cloud());
            b.set(px + 1, 19, 7, Blocks.PEARLESCENT_FROGLIGHT);
        }
        b.fill(2, 19, 6, 20, 21, 8, cloud());
        b.fill(5, 18, 7, 17, 18, 7, Blocks.WHITE_CONCRETE);
        b.fill(8, 22, 7, 14, 22, 7, Blocks.GOLD_BLOCK);
        b.set(11, 23, 7, Blocks.BELL);
        // toll booth
        b.fill(6, 1, 11, 12, 4, 15, Blocks.WHITE_CONCRETE);
        b.clear(7, 1, 12, 11, 3, 14);
        b.fill(6, 5, 11, 12, 5, 15, Blocks.SMOOTH_QUARTZ_SLAB);
        b.set(9, 2, 11, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        b.door(6, 1, 13, Blocks.BIRCH_DOOR, Direction.WEST);
        b.set(10, 1, 14, Blocks.LECTERN);
        b.hanging(9, 3, 13, Blocks.PEARLESCENT_FROGLIGHT);
        b.marker("npc", 9, 1, 13, role("keeper"));
        b.marker("npc", 15, 1, 12, role("guard"));
        b.chest(11, 1, 12, Direction.WEST, Buildings.LOOT_SHOP);
        // cloud steps up to the arch
        for (int i = 0; i < 4; i++) b.fill(9, 1, i, 13, 1 + i / 2, i, cloud());
    }

    /** Angel Island's round white chapel with a bell and a dial merchant. */
    static void cloudChapel(Builder b, Island isl, BuildStyle st) {
        b.foundation(0, 0, 18, 18, cloud(), 6);
        b.fill(0, 0, 0, 18, 0, 18, Blocks.SMOOTH_QUARTZ);
        b.clear(0, 1, 0, 18, 22, 18);
        b.cylinder(9, 9, 1, 8, 7, Blocks.WHITE_CONCRETE, true);
        b.cylinder(9, 9, 0, 0, 7, Blocks.SMOOTH_QUARTZ, false);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            int wx = 9 + (int) Math.round(Math.cos(ang) * 7), wz = 9 + (int) Math.round(Math.sin(ang) * 7);
            b.fill(wx, 3, wz, wx, 5, wz, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        b.dome(9, 8, 9, 7, Blocks.QUARTZ_BLOCK);
        b.clear(9 - 5, 9, 9 - 5, 9 + 5, 13, 9 + 5);
        b.dome(9, 8, 9, 6, Blocks.AIR);
        b.fill(9, 15, 9, 9, 17, 9, Blocks.QUARTZ_PILLAR);
        b.set(9, 18, 9, Blocks.BELL);
        b.clear(8, 1, 2, 10, 3, 2);
        b.door(9, 1, 2, Blocks.BIRCH_DOOR, Direction.NORTH);
        b.fill(7, 1, 14, 11, 1, 15, Blocks.GOLD_BLOCK);
        b.set(9, 2, 15, Blocks.LECTERN);
        for (int z = 6; z <= 12; z += 2) {
            b.stairs(6, 1, z, Blocks.BIRCH_STAIRS, Direction.NORTH, Half.BOTTOM);
            b.stairs(12, 1, z, Blocks.BIRCH_STAIRS, Direction.NORTH, Half.BOTTOM);
        }
        b.hanging(9, 7, 9, Blocks.PEARLESCENT_FROGLIGHT);
        b.marker("npc", 9, 1, 13, role("priest"));
        b.marker("npc", 4, 1, 4, role("merchant"));
        b.marker("npc", 14, 1, 5, role("resident"));
        b.chest(5, 1, 13, Direction.EAST, Buildings.LOOT_SHRINE);
    }

    /** The lost city of gold's ruins in the Upper Yard jungle: broken walls, columns, a poneglyph and a sealed vault. */
    static void shandoraRuins(Builder b, Island isl, BuildStyle st) {
        b.foundation(0, 0, 46, 46, Blocks.STONE_BRICKS, 8);
        for (int x = 0; x <= 46; x++)
            for (int z = 0; z <= 46; z++) b.set(x, 0, z, (x * 7 + z * 13) % 5 == 0 ? Blocks.MOSSY_STONE_BRICKS : (x + z) % 7 == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
        b.clear(0, 1, 0, 46, 18, 46);
        // outer walls, broken
        for (int i = 0; i <= 46; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, 46}, {0, i}, {46, i}}) {
                int h = 2 + b.rand(i * 31 + p[0] * 7 + p[1], 6);
                if (b.rand(i * 17 + p[1], 5) == 0) continue;
                for (int y = 1; y <= h; y++) b.set(p[0], y, p[1], y == h && h > 5 ? Blocks.CHISELED_STONE_BRICKS : (y + i) % 3 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS);
                if (b.rand(i * 3 + p[0], 3) == 0) b.set(p[0], h + 1, p[1], Blocks.VINE);
            }
        }
        // avenue of columns
        for (int z = 6; z <= 40; z += 6)
            for (int x : new int[]{16, 30}) {
                int h = 5 + b.rand(z * 11 + x, 8);
                b.fill(x, 1, z, x, h, z, Blocks.STONE_BRICK_WALL);
                if (h > 9) b.fill(x - 1, h + 1, z - 1, x + 1, h + 1, z + 1, Blocks.STONE_BRICK_SLAB);
                b.set(x, 1, z + 1, Blocks.MOSS_CARPET);
            }
        // ruined houses
        for (int[] h : new int[][]{{4, 6}, {4, 30}, {36, 8}, {36, 32}}) {
            b.walls(h[0], 1, h[1], h[0] + 7, 3 + b.rand(h[0] + h[1], 3), h[1] + 7, Blocks.MOSSY_STONE_BRICKS);
            b.clear(h[0] + 1, 1, h[1] + 1, h[0] + 6, 6, h[1] + 6);
            b.clear(h[0] + 3, 1, h[1], h[0] + 4, 2, h[1]);
            b.chest(h[0] + 1, 1, h[1] + 6, Direction.EAST, Buildings.LOOT_RUIN);
        }
        // central altar with the poneglyph
        b.fill(19, 1, 19, 27, 1, 27, Blocks.CHISELED_STONE_BRICKS);
        b.fill(20, 2, 20, 26, 2, 26, Blocks.POLISHED_ANDESITE);
        b.fill(23, 3, 23, 23, 5, 23, ModBlocks.PONEGLYPH.get());
        b.marker("poneglyph", 23, 3, 23);
        b.set(20, 3, 20, Blocks.GOLD_BLOCK);
        b.set(26, 3, 26, Blocks.GOLD_BLOCK);
        // sealed vault under the altar (a cracked slab marks the way)
        b.set(21, 1, 22, Blocks.CRACKED_STONE_BRICKS);
        b.fill(19, -6, 19, 27, -1, 27, Blocks.STONE_BRICKS);
        b.clear(20, -5, 20, 26, -2, 26);
        b.clear(21, -1, 22, 21, 0, 22);
        b.ladderUp(21, -5, 23, 6, Direction.NORTH);
        b.fill(20, -5, 20, 26, -5, 20, Blocks.GOLD_BLOCK);
        b.chest(23, -5, 25, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.chest(25, -5, 25, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.hanging(23, -2, 23, Blocks.LANTERN);
        b.marker("treasure", 23, -5, 25);
        // the trials' guardians
        b.marker("npc", 23, 1, 12, role("ruin_guardian"));
        b.marker("npc", 12, 1, 23, role("ruin_guardian"));
        b.marker("npc", 34, 1, 23, role("ruin_guardian"));
    }

    /** Giant Jack: a colossal beanstalk spiralling up with the golden bell of Shandora at the top. */
    static void goldenBelfry(Builder b, Island isl, BuildStyle st) {
        int cx = 8, cz = 8, top = 140;
        b.fill(0, 0, 0, 16, 0, 16, Blocks.MOSS_BLOCK);
        b.clear(0, 1, 0, 16, top + 12, 16);
        for (int y = -2; y <= top; y++) {
            int r = y < 8 ? 5 : y < 40 ? 4 : 3;
            b.cylinder(cx, cz, y, y, r, y % 9 < 2 ? Blocks.MOSS_BLOCK : Blocks.JUNGLE_LOG, false);
            // spiral vine path around the stalk
            double ang = y * 0.21;
            int px = cx + (int) Math.round(Math.cos(ang) * (r + 1)), pz = cz + (int) Math.round(Math.sin(ang) * (r + 1));
            b.set(px, y, pz, Blocks.JUNGLE_PLANKS);
            int qx = cx + (int) Math.round(Math.cos(ang) * (r + 2)), qz = cz + (int) Math.round(Math.sin(ang) * (r + 2));
            b.set(qx, y, qz, Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            if (y % 25 == 12) b.cylinder(cx, cz, y, y, r + 3, Blocks.JUNGLE_LEAVES, false);
        }
        // belfry platform
        b.cylinder(cx, cz, top + 1, top + 1, 7, Blocks.GOLD_BLOCK, false);
        for (int[] c : new int[][]{{3, 3}, {13, 3}, {3, 13}, {13, 13}}) b.fill(c[0], top + 2, c[1], c[0], top + 7, c[1], Blocks.GOLD_BLOCK);
        b.fill(3, top + 8, 3, 13, top + 8, 13, Blocks.GOLD_BLOCK);
        b.clear(4, top + 8, 4, 12, top + 8, 12);
        b.fill(6, top + 9, 6, 10, top + 9, 10, Blocks.GOLD_BLOCK);
        b.fill(8, top + 6, 8, 8, top + 7, 8, Blocks.CHAIN);
        b.set(8, top + 5, 8, Blocks.BELL);
        b.chest(5, top + 2, 5, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.marker("treasure", 5, top + 2, 5);
        b.marker("npc", 11, top + 2, 11, role("master"));
    }

    /** A stone altar where the sky priests held their trials. */
    static void sacrificialAltar(Builder b, Island isl, BuildStyle st) {
        b.foundation(0, 0, 16, 16, Blocks.STONE_BRICKS, 6);
        b.fill(0, 0, 0, 16, 0, 16, Blocks.MOSSY_COBBLESTONE);
        b.clear(0, 1, 0, 16, 14, 16);
        b.fill(4, 1, 4, 12, 1, 12, Blocks.STONE_BRICKS);
        b.fill(5, 2, 5, 11, 2, 11, Blocks.POLISHED_ANDESITE);
        b.fill(7, 3, 7, 9, 3, 9, Blocks.CHISELED_STONE_BRICKS);
        b.fill(8, 4, 8, 8, 10, 8, Blocks.STONE_BRICK_WALL);
        b.set(8, 11, 8, Blocks.LIGHTNING_ROD);
        for (int[] c : new int[][]{{2, 2}, {14, 2}, {2, 14}, {14, 14}}) {
            b.fill(c[0], 1, c[1], c[0], 3, c[1], Blocks.MOSSY_STONE_BRICK_WALL);
            b.set(c[0], 4, c[1], Blocks.SOUL_LANTERN);
        }
        b.chest(8, 4, 6, Direction.SOUTH, Buildings.LOOT_SHRINE);
        b.marker("secret", 8, 4, 6);
        b.marker("npc", 8, 1, 2, role("ruin_guardian"));
    }

    /** Weatheria's research lab: a glass dome, weather balloons, scholars and their instruments. */
    static void weatherLab(Builder b, Island isl, BuildStyle st) {
        b.foundation(0, 0, 20, 20, cloud(), 6);
        b.fill(0, 0, 0, 20, 0, 20, Blocks.SMOOTH_QUARTZ);
        b.clear(0, 1, 0, 20, 20, 20);
        b.walls(3, 1, 3, 17, 5, 17, Blocks.WHITE_CONCRETE);
        b.clear(4, 1, 4, 16, 5, 16);
        b.fill(3, 6, 3, 17, 6, 17, Blocks.SMOOTH_QUARTZ);
        b.dome(10, 6, 10, 6, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.dome(10, 6, 10, 5, Blocks.AIR);
        for (int x = 5; x <= 15; x += 5) b.fill(x, 3, 3, x, 4, 3, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        b.clear(10, 1, 3, 10, 2, 3);
        b.door(10, 1, 3, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(5, 1, 15, Blocks.CARTOGRAPHY_TABLE);
        b.set(6, 1, 15, Blocks.LECTERN);
        b.set(14, 1, 15, Blocks.LOOM);
        b.set(15, 1, 15, Blocks.BREWING_STAND);
        b.set(10, 1, 10, Blocks.LIGHTNING_ROD);
        b.hanging(10, 5, 10, Blocks.SEA_LANTERN);
        b.chest(15, 1, 5, Direction.WEST, Buildings.LOOT_SHRINE);
        b.barrel(15, 1, 6, Buildings.LOOT_SHOP);
        b.marker("npc", 8, 1, 12, role("scholar"));
        b.marker("npc", 12, 1, 8, role("scholar"));
        // weather balloons tethered around the lab
        for (int[] c : new int[][]{{1, 1}, {19, 1}, {1, 19}, {19, 19}}) {
            b.fill(c[0], 1, c[1], c[0], 8, c[1], Blocks.BIRCH_FENCE);
            b.dome(c[0], 10, c[1], 2, (c[0] + c[1]) % 3 == 0 ? Blocks.LIGHT_BLUE_WOOL : Blocks.WHITE_WOOL);
            b.fill(c[0] - 1, 9, c[1] - 1, c[0] + 1, 9, c[1] + 1, Blocks.WHITE_WOOL);
        }
    }

    /** The edge of the White Sea: a cloud arch and a guide who warns sailors of the long way down. */
    static void cloudEndGate(Builder b, Island isl, BuildStyle st) {
        b.foundation(0, 0, 12, 10, cloud(), 6);
        b.fill(0, 0, 0, 12, 0, 10, cloud());
        b.clear(0, 1, 0, 12, 12, 10);
        b.fill(1, 1, 4, 2, 9, 6, cloud());
        b.fill(10, 1, 4, 11, 9, 6, cloud());
        b.fill(1, 10, 4, 11, 11, 6, cloud());
        b.fill(5, 12, 5, 7, 12, 5, Blocks.PEARLESCENT_FROGLIGHT);
        b.set(6, 1, 8, Blocks.BIRCH_SIGN);
        b.marker("npc", 6, 1, 2, role("canal_guide"));
    }

    // ------------------------------------------------------------------ the deep sea

    /** Ryugu Palace crowning Fish-Man Island: coral towers, a domed keep and the throne room. */
    static void ryuguPalace(Builder b, Island isl, BuildStyle st) {
        int s = 42;
        b.foundation(0, 0, s, s, Blocks.PRISMARINE_BRICKS, 12);
        b.fill(0, 0, 0, s, 0, s, Blocks.DARK_PRISMARINE);
        b.clear(0, 1, 0, s, 34, s);
        // curtain wall with crenels
        b.walls(2, 1, 2, s - 2, 6, s - 2, Blocks.PRISMARINE_BRICKS);
        for (int i = 2; i <= s - 2; i += 2) {
            b.set(i, 7, 2, Blocks.PRISMARINE_BRICK_SLAB);
            b.set(i, 7, s - 2, Blocks.PRISMARINE_BRICK_SLAB);
            b.set(2, 7, i, Blocks.PRISMARINE_BRICK_SLAB);
            b.set(s - 2, 7, i, Blocks.PRISMARINE_BRICK_SLAB);
        }
        b.clear(19, 1, 2, 23, 5, 2);
        b.bigDoor(21, 1, 2, Blocks.WARPED_DOOR, Direction.NORTH);
        // corner towers
        for (int[] c : new int[][]{{4, 4}, {s - 4, 4}, {4, s - 4}, {s - 4, s - 4}}) {
            b.cylinder(c[0], c[1], 1, 16, 3, Blocks.DARK_PRISMARINE, true);
            b.cylinder(c[0], c[1], 17, 17, 4, Blocks.PRISMARINE_BRICKS, false);
            b.dome(c[0], 17, c[1], 3, Blocks.TUBE_CORAL_BLOCK);
            b.set(c[0], 21, c[1], Blocks.SEA_LANTERN);
        }
        // keep
        b.walls(11, 1, 13, 31, 14, 33, Blocks.PRISMARINE_BRICKS);
        b.clear(12, 1, 14, 30, 13, 32);
        b.fill(11, 15, 13, 31, 15, 33, Blocks.DARK_PRISMARINE);
        b.dome(21, 15, 23, 9, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.dome(21, 15, 23, 8, Blocks.AIR);
        b.fill(21, 24, 23, 21, 27, 23, Blocks.PRISMARINE_WALL);
        b.set(21, 28, 23, Blocks.SEA_LANTERN);
        for (int x = 13; x <= 29; x += 4) for (int y = 4; y <= 10; y += 6) {
            b.set(x, y, 13, Blocks.LIGHT_BLUE_STAINED_GLASS);
            b.set(x, y, 33, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        b.clear(20, 1, 13, 22, 4, 13);
        b.door(21, 1, 13, Blocks.WARPED_DOOR, Direction.NORTH);
        // throne room
        b.fill(18, 1, 14, 24, 1, 32, Blocks.LIGHT_BLUE_CARPET);
        b.fill(18, 1, 28, 24, 2, 31, Blocks.GOLD_BLOCK);
        b.stairs(21, 3, 30, Blocks.QUARTZ_STAIRS, Direction.SOUTH, Half.BOTTOM);
        for (int z = 16; z <= 28; z += 4) {
            b.fill(14, 1, z, 14, 12, z, Blocks.DARK_PRISMARINE);
            b.fill(28, 1, z, 28, 12, z, Blocks.DARK_PRISMARINE);
            b.hanging(16, 12, z, Blocks.SEA_LANTERN);
            b.hanging(26, 12, z, Blocks.SEA_LANTERN);
        }
        b.chest(13, 1, 31, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(29, 1, 31, Direction.WEST, Buildings.LOOT_TREASURE);
        b.marker("treasure", 13, 1, 31);
        b.marker("npc", 21, 3, 29, role("noble"));
        b.marker("npc", 18, 1, 26, role("fishman_guard"));
        b.marker("npc", 24, 1, 26, role("fishman_guard"));
        b.marker("npc", 20, 1, 5, role("fishman_guard"));
        b.marker("npc", 22, 1, 5, role("fishman_guard"));
        // gardens of coral in the courtyard
        for (int[] c : new int[][]{{7, 20}, {35, 20}, {10, 36}, {32, 36}}) {
            b.fill(c[0] - 1, 1, c[1] - 1, c[0] + 1, 1, c[1] + 1, Blocks.BRAIN_CORAL_BLOCK);
            b.set(c[0], 2, c[1], Blocks.SEA_LANTERN);
        }
    }

    /** A lagoon ringed with rocks and coral where merfolk gather, with a little café. */
    static void mermaidCove(Builder b, Island isl, BuildStyle st) {
        int s = 26;
        for (int x = 0; x <= s; x++)
            for (int z = 0; z <= s; z++) {
                double d = Math.hypot(x - 13, z - 13);
                if (d > 13) continue;
                if (d > 11) b.fill(x, -3, z, x, 1 + b.rand(x * 31 + z, 2), z, (x + z) % 3 == 0 ? Blocks.TUBE_CORAL_BLOCK : Blocks.SANDSTONE);
                else {
                    b.fill(x, -4, z, x, -4, z, Blocks.SAND);
                    b.fill(x, -3, z, x, 0, z, Blocks.WATER);
                    if (b.rand(x * 7 + z * 3, 9) == 0) b.set(x, -3, z, Blocks.BRAIN_CORAL);
                    if (b.rand(x * 5 + z * 11, 13) == 0) b.set(x, -3, z, Blocks.SEA_PICKLE);
                }
            }
        // rocks to sit on
        for (int[] c : new int[][]{{9, 9}, {17, 10}, {13, 17}}) b.fill(c[0], -3, c[1], c[0] + 1, 1, c[1] + 1, Blocks.SMOOTH_SANDSTONE);
        // café on the rim
        b.fill(19, 1, 20, 25, 1, 25, Blocks.SMOOTH_SANDSTONE);
        b.walls(19, 2, 20, 25, 4, 25, Blocks.PRISMARINE_BRICKS);
        b.clear(20, 2, 21, 24, 4, 24);
        b.fill(19, 5, 20, 25, 5, 25, Blocks.DARK_PRISMARINE_SLAB);
        b.clear(22, 2, 20, 22, 3, 20);
        b.set(21, 2, 24, Blocks.BARREL);
        b.hanging(22, 4, 22, Blocks.SEA_LANTERN);
        b.marker("npc", 23, 2, 23, role("bartender"));
        b.marker("npc", 10, 2, 10, role("resident"));
        b.marker("npc", 16, 2, 9, role("resident"));
        b.marker("npc", 13, 2, 18, role("child"));
        b.barrel(24, 2, 24, Buildings.LOOT_TAVERN);
    }

    /** The great plaza of the Fish-Man District: fountain, statues, notice boards. */
    static void gyoncordePlaza(Builder b, Island isl, BuildStyle st) {
        int s = 28;
        b.foundation(0, 0, s, s, Blocks.SMOOTH_SANDSTONE, 6);
        for (int x = 0; x <= s; x++)
            for (int z = 0; z <= s; z++) {
                double d = Math.hypot(x - 14, z - 14);
                b.set(x, 0, z, (int) d % 4 == 0 ? Blocks.PRISMARINE_BRICKS : Blocks.SMOOTH_SANDSTONE);
            }
        b.clear(0, 1, 0, s, 12, s);
        b.cylinder(14, 14, 1, 1, 5, Blocks.PRISMARINE_BRICKS, true);
        b.cylinder(14, 14, 1, 1, 4, Blocks.WATER, false);
        b.fill(14, 1, 14, 14, 5, 14, Blocks.PRISMARINE_WALL);
        b.set(14, 6, 14, Blocks.SEA_LANTERN);
        for (int[] c : new int[][]{{4, 4}, {24, 4}, {4, 24}, {24, 24}}) {
            b.fill(c[0], 1, c[1], c[0], 2, c[1], Blocks.DARK_PRISMARINE);
            b.fill(c[0], 3, c[1], c[0], 4, c[1], Blocks.PRISMARINE);
            b.set(c[0], 5, c[1], Blocks.SEA_LANTERN);
        }
        b.marker("quest_board", 14, 1, 2);
        b.set(14, 1, 2, Blocks.OAK_PLANKS);
        b.set(14, 2, 2, Blocks.OAK_WALL_SIGN);
        b.marker("bounty_board", 2, 1, 14);
        b.marker("npc", 10, 1, 10, role("merchant"));
        b.marker("npc", 18, 1, 18, role("resident"));
        b.marker("npc", 20, 1, 9, role("fishman_guard"));
        b.marker("npc", 8, 1, 20, role("child"));
    }

    /** The Sea Forest: ships that sank into the deep, a huge coral tree and the graves of those who sailed them. */
    static void seaForest(Builder b, Island isl, BuildStyle st) {
        int s = 36;
        b.foundation(0, 0, s, s, Blocks.SAND, 6);
        b.fill(0, 0, 0, s, 0, s, Blocks.SAND);
        b.clear(0, 1, 0, s, 30, s);
        // the coral tree
        for (int y = 1; y <= 18; y++) b.cylinder(18, 18, y, y, y < 4 ? 3 : 2, Blocks.BRAIN_CORAL_BLOCK, false);
        for (int a = 0; a < 6; a++) {
            double ang = a * Math.PI / 3;
            for (int i = 1; i <= 8; i++) {
                int x = 18 + (int) Math.round(Math.cos(ang) * i), z = 18 + (int) Math.round(Math.sin(ang) * i);
                b.set(x, 18 + i / 2, z, a % 2 == 0 ? Blocks.FIRE_CORAL_BLOCK : Blocks.HORN_CORAL_BLOCK);
            }
        }
        b.set(18, 22, 18, Blocks.SEA_LANTERN);
        // wrecked hulls lying in the sand
        for (int[] w : new int[][]{{2, 4, 0}, {26, 22, 1}}) {
            int x0 = w[0], z0 = w[1];
            for (int i = 0; i < 14; i++) {
                int lx = w[2] == 0 ? x0 + i : x0, lz = w[2] == 0 ? z0 : z0 + i;
                int half = i < 2 || i > 11 ? 1 : 2;
                for (int k = -half; k <= half; k++) {
                    int px = w[2] == 0 ? lx : lx + k, pz = w[2] == 0 ? lz + k : lz;
                    b.set(px, 1, pz, Blocks.DARK_OAK_PLANKS);
                    if (Math.abs(k) == half && b.rand(i * 13 + k, 3) > 0) b.set(px, 2, pz, Blocks.STRIPPED_DARK_OAK_LOG);
                }
            }
            b.chest(w[2] == 0 ? x0 + 7 : x0, 2, w[2] == 0 ? z0 : z0 + 7, Direction.NORTH, Buildings.LOOT_WRECK);
        }
        // graves
        for (int i = 0; i < 6; i++) {
            int gx = 6 + i * 4, gz = 30;
            b.set(gx, 1, gz, Blocks.STONE_BRICK_WALL);
            b.set(gx, 2, gz, Blocks.CANDLE);
            b.set(gx, 0, gz - 1, Blocks.COARSE_DIRT);
        }
        b.chest(18, 1, 31, Direction.NORTH, Buildings.LOOT_SHRINE);
        b.marker("treasure", 18, 1, 31);
        b.marker("npc", 14, 1, 26, role("keeper"));
    }

    private DimensionLandmarks() {}
}
