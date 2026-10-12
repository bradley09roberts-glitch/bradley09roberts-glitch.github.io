package com.grandline.world.landmark;

import com.grandline.registry.ModBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import static com.grandline.world.landmark.LandmarkKit.*;

/** Set pieces of the first half of Paradise: Whisky Peak, Little Garden, Alabasta and Jaya. */
final class ParadiseLandmarks {

    // ============================================================== Whisky Peak: the welcome arch

    private static void cactusRock(Builder b, int x, int z, int h) {
        for (int y = 1; y <= h; y++) b.fill(x, y, z, x + 1, y, z + 1, y % 4 == 0 ? Blocks.BROWN_TERRACOTTA : Blocks.TERRACOTTA);
        b.fill(x, h + 1, z, x + 1, h + 1, z + 1, Blocks.SMOOTH_RED_SANDSTONE_SLAB);
        int a = h / 2;
        b.fill(x - 1, a, z, x - 1, a, z + 1, Blocks.TERRACOTTA);
        b.fill(x - 2, a, z, x - 2, a + 4, z + 1, Blocks.TERRACOTTA);
        int a2 = h / 2 + 2;
        b.fill(x + 2, a2, z, x + 2, a2, z + 1, Blocks.TERRACOTTA);
        b.fill(x + 3, a2, z, x + 3, a2 + 3, z + 1, Blocks.TERRACOTTA);
    }

    /** The bounty hunters' town gate: a festive welcome arch, a party plaza and a saloon hiding their base below. */
    static void welcomeArch(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 27 x 25
        site(b, Blocks.PACKED_MUD, Blocks.DIRT, 24);
        patchGround(b, 41, 0, Blocks.PACKED_MUD, Blocks.COARSE_DIRT, Blocks.DIRT);
        b.fill(12, 0, 0, 14, 0, 13, Blocks.MUD_BRICKS);
        // saguaro-shaped rock spires flanking the road
        cactusRock(b, 2, 2, 12);
        cactusRock(b, 23, 2, 13);
        // the arch with its sign and bunting
        b.fill(7, 1, 1, 8, 10, 2, Blocks.STRIPPED_SPRUCE_LOG);
        b.fill(18, 1, 1, 19, 10, 2, Blocks.STRIPPED_SPRUCE_LOG);
        b.fill(6, 11, 1, 20, 11, 2, axis(Blocks.SPRUCE_LOG, Direction.Axis.X));
        b.fill(7, 12, 1, 19, 12, 2, Blocks.SPRUCE_SLAB);
        b.fill(9, 8, 1, 17, 10, 1, Blocks.YELLOW_TERRACOTTA);
        for (int x = 9; x <= 17; x += 2) b.set(x, 9, 1, Blocks.RED_TERRACOTTA);
        b.hanging(9, 10, 2, Blocks.LANTERN);
        b.hanging(17, 10, 2, Blocks.LANTERN);
        Block[] flags = {Blocks.RED_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL};
        for (int x = 9; x <= 17; x++) if ((x & 1) == 0) b.set(x, 7, 1, flags[(x / 2) % flags.length]);
        // ---- party plaza
        longTableX(b, 4, 9, 1, 7, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        longTableX(b, 17, 22, 1, 7, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        b.barrel(1, 1, 9, Buildings.LOOT_TAVERN);
        b.barrel(1, 1, 10, Buildings.LOOT_TAVERN);
        b.set(1, 2, 9, Blocks.BARREL);
        b.barrel(25, 1, 9, Buildings.LOOT_TAVERN);
        b.set(25, 1, 10, Blocks.HAY_BLOCK);
        lampPost(b, 4, 1, 4, Blocks.SPRUCE_FENCE, 3, Blocks.LANTERN);
        lampPost(b, 22, 1, 4, Blocks.SPRUCE_FENCE, 3, Blocks.LANTERN);
        b.fill(2, 1, 11, 6, 1, 12, Blocks.SPRUCE_SLAB);
        b.set(3, 2, 12, Blocks.NOTE_BLOCK);
        b.set(5, 2, 12, Blocks.JUKEBOX);
        npc(b, "patron", 6, 1, 5);
        npc(b, "patron", 20, 1, 9);
        npc(b, "resident", 21, 1, 4);
        npc(b, "child", 10, 1, 10);

        // ---- the saloon (z 14..24), balcony on the plaza side
        int x1 = 3, x2 = 23, z1 = 14, z2 = 24;
        // the bounty hunters' cellar first (its roof is the saloon floor)
        shell(b, 4, -6, 15, 22, 0, 24, Blocks.STONE_BRICKS);
        b.fill(x1, 0, z1, x2, 0, z2, Blocks.SPRUCE_PLANKS);
        b.walls(x1, 1, z1, x2, 5, z2, Blocks.SPRUCE_PLANKS);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 5, z2 - 1);
        b.fill(x1, 6, z1, x2, 6, z2, Blocks.SPRUCE_PLANKS);
        b.walls(x1, 7, z1, x2, 10, z2, Blocks.SPRUCE_PLANKS);
        b.clear(x1 + 1, 7, z1 + 1, x2 - 1, 10, z2 - 1);
        b.fill(x1, 11, z1, x2, 11, z2, Blocks.SPRUCE_PLANKS);
        posts(b, x1, 1, z1, x2, 10, z2, Blocks.STRIPPED_SPRUCE_LOG);
        b.fill(x1, 6, z1, x2, 6, z1, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        b.gableRoof(x1 - 1, z1, x2 + 1, z2, 11, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.SPRUCE_PLANKS);
        for (int x : new int[]{5, 8, 17, 20}) {
            b.set(x, 2, z1, connected(Blocks.GLASS_PANE, Direction.Axis.X));
            b.set(x, 3, z1, connected(Blocks.GLASS_PANE, Direction.Axis.X));
        }
        windowsZ(b, 16, 22, 2, x1, 3, Blocks.GLASS_PANE);
        windowsZ(b, 16, 22, 2, x2, 3, Blocks.GLASS_PANE);
        windowsX(b, 5, 20, 8, z2, 5, Blocks.GLASS_PANE);
        b.bigDoor(12, 1, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.bigDoor(13, 1, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.set(12, 4, z1, Blocks.STRIPPED_SPRUCE_LOG);
        // bar along the back
        b.fill(5, 1, 20, 11, 1, 20, axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        for (int x = 5; x <= 10; x++) {
            b.barrel(x, 1, 23, Buildings.LOOT_TAVERN);
            if ((x & 1) == 1) b.set(x, 2, 23, Blocks.BREWING_STAND);
        }
        npc(b, "bartender", 8, 1, 22);
        // tables
        for (int[] t : new int[][]{{15, 17}, {18, 17}, {15, 21}, {18, 21}, {7, 16}}) {
            table(b, t[0], 1, t[1], Blocks.DARK_OAK_SLAB);
            b.set(t[0], 2, t[1], candles(Blocks.CANDLE, 2));
            chair(b, t[0] - 1, 1, t[1], Blocks.SPRUCE_STAIRS, Direction.WEST);
            chair(b, t[0] + 1, 1, t[1], Blocks.SPRUCE_STAIRS, Direction.EAST);
        }
        b.set(4, 1, 15, Blocks.NOTE_BLOCK);
        b.set(4, 1, 16, Blocks.NOTE_BLOCK);
        chair(b, 5, 1, 15, Blocks.SPRUCE_STAIRS, Direction.EAST);
        for (int[] l : new int[][]{{8, 18}, {13, 18}, {17, 19}}) lamp(b, l[0], 5, l[1]);
        b.marker("quest_board", x1 + 1, 2, 18, new net.minecraft.nbt.CompoundTag());
        npc(b, "patron", 16, 1, 19);
        npc(b, "patron", 10, 1, 18);
        // stairs to the guest rooms (east side)
        stairsUp(b, 21, 1, 22, Direction.NORTH, 6, Blocks.SPRUCE_STAIRS, 2, Blocks.SPRUCE_PLANKS);
        line(b, 20, 7, 18, 20, 23, Blocks.SPRUCE_FENCE);
        // upstairs: corridor along the front, three rooms behind
        b.fill(x1 + 1, 7, 17, 20, 10, 17, Blocks.SPRUCE_PLANKS);
        for (int x : new int[]{9, 15}) b.fill(x, 7, 18, x, 10, z2 - 1, Blocks.SPRUCE_PLANKS);
        for (int x : new int[]{6, 12, 18}) b.door(x, 7, 17, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.bed(5, 7, 22, Blocks.RED_BED, Direction.SOUTH);
        b.chest(7, 7, 23, Direction.WEST, Buildings.LOOT_HOUSE);
        b.bed(11, 7, 22, Blocks.YELLOW_BED, Direction.SOUTH);
        b.bed(13, 7, 22, Blocks.YELLOW_BED, Direction.SOUTH);
        b.bed(17, 7, 22, Blocks.GREEN_BED, Direction.SOUTH);
        b.barrel(19, 7, 23, Buildings.LOOT_HOUSE);
        for (int x : new int[]{6, 12, 17}) lamp(b, x, 10, 20);
        lamp(b, 13, 10, 15);
        // balcony over the plaza
        b.fill(x1 + 1, 6, 11, x2 - 1, 6, 13, Blocks.SPRUCE_PLANKS);
        line(b, x1 + 1, 7, 11, x2 - 1, 11, Blocks.SPRUCE_FENCE);
        for (int x : new int[]{x1 + 1, 9, 17, x2 - 1}) b.fill(x, 1, 11, x, 5, 11, Blocks.STRIPPED_SPRUCE_LOG);
        b.door(13, 7, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.hanging(11, 5, 12, Blocks.LANTERN);
        b.hanging(15, 5, 12, Blocks.LANTERN);
        npc(b, "resident", 8, 7, 12);
        // ---- the hidden base: hatch behind the bar, wanted posters, bounty board, armoury, bunks
        hatch(b, 11, 0, 23, -5, Direction.NORTH, Blocks.SPRUCE_TRAPDOOR, null);
        b.fill(5, -6, 16, 21, -6, 23, Blocks.SPRUCE_PLANKS);
        for (int x = 6; x <= 20; x += 3) b.set(x, -3, 16, banner(Blocks.WHITE_WALL_BANNER, Direction.SOUTH));
        b.marker("bounty_board", 13, -4, 16, new net.minecraft.nbt.CompoundTag());
        longTableX(b, 8, 14, -5, 19, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        b.set(16, -5, 17, Blocks.CARTOGRAPHY_TABLE);
        b.set(5, -5, 17, Blocks.GRINDSTONE);
        b.set(5, -5, 18, Blocks.ANVIL);
        b.set(5, -5, 19, Blocks.SMITHING_TABLE);
        b.barrel(5, -5, 20, Buildings.LOOT_SMITH);
        for (int x = 15; x <= 19; x += 2) b.bed(x, -5, 22, Blocks.BLACK_BED, Direction.SOUTH);
        b.chest(21, -5, 20, Direction.WEST, Buildings.LOOT_TREASURE);
        for (int x : new int[]{8, 14, 19}) b.hanging(x, -1, 18, Blocks.LANTERN);
        npc(b, "bandit", 9, -5, 17);
        npc(b, "bandit", 17, -5, 20);
        mark(b, "treasure", 21, -5, 20);
        b.marker("legend", 13, 1, 3, tag("story", "welcome_arch"));
    }

    // ============================================================== Little Garden: the giants' duel ground

    /** Giant-scale hut (11x11, door on local north) with a giant bed, table, fire and an enormous axe. */
    private static void giantHut(Builder h, String loot) {
        h.foundation(0, 0, 10, 10, Blocks.DIRT, 6);
        h.fill(0, 0, 0, 10, 0, 10, Blocks.SPRUCE_PLANKS);
        h.walls(0, 1, 0, 10, 8, 10, Blocks.SPRUCE_LOG);
        h.clear(1, 1, 1, 9, 8, 9);
        posts(h, 0, 1, 0, 10, 8, 10, Blocks.SPRUCE_WOOD);
        h.clear(4, 1, 0, 6, 6, 0);
        h.fill(3, 7, 0, 7, 7, 0, axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        h.gableRoof(-1, -1, 11, 11, 9, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS);
        for (int z = 3; z <= 7; z += 2) {
            h.set(0, 4, z, connected(Blocks.SPRUCE_FENCE, Direction.Axis.Z));
            h.set(10, 4, z, connected(Blocks.SPRUCE_FENCE, Direction.Axis.Z));
        }
        h.fill(1, 8, 5, 9, 8, 5, axis(Blocks.SPRUCE_LOG, Direction.Axis.X));
        h.hanging(3, 7, 5, Blocks.LANTERN);
        h.hanging(7, 7, 5, Blocks.LANTERN);
        // giant bed of furs
        h.fill(1, 1, 2, 3, 1, 9, Blocks.BROWN_WOOL);
        h.fill(1, 2, 2, 3, 2, 8, Blocks.BROWN_CARPET);
        h.fill(1, 2, 9, 3, 2, 9, Blocks.WHITE_WOOL);
        // giant table with a mug and a cask
        for (int[] l : new int[][]{{6, 6}, {9, 6}, {6, 9}, {9, 9}}) h.fill(l[0], 1, l[1], l[0], 3, l[1], Blocks.SPRUCE_LOG);
        h.fill(6, 4, 6, 9, 4, 9, Blocks.SPRUCE_PLANKS);
        h.set(7, 5, 7, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        h.barrel(8, 5, 8, Buildings.LOOT_TAVERN);
        // fire pit
        h.set(8, 0, 3, Blocks.COBBLESTONE);
        h.set(8, 1, 3, Blocks.CAMPFIRE);
        // the axe leaning by the door
        h.fill(9, 1, 1, 9, 6, 1, Blocks.SPRUCE_LOG);
        h.fill(8, 5, 1, 8, 7, 1, Blocks.IRON_BLOCK);
        h.fill(9, 7, 1, 9, 7, 1, Blocks.IRON_BLOCK);
        h.chest(1, 1, 1, Direction.EAST, loot);
        h.barrel(2, 1, 1, Buildings.LOOT_HOUSE);
        npc(h, "giant_warrior", 5, 1, 3);
    }

    /** A jungle clearing where two giants have duelled for a century: giant huts, a beast skeleton and tally stones. */
    static void giantDuelGround(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 32);
        patchGround(b, 51, 0, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.PODZOL, Blocks.COARSE_DIRT);
        scatter(b, 52, 40, 0, 30, w - 1, d - 1, 1, Blocks.FERN.defaultBlockState());
        scatter(b, 53, 20, 0, 0, w - 1, 12, 1, Blocks.SHORT_GRASS.defaultBlockState());
        // the trampled arena with craters from a hundred years of blows
        disc(b, c, c, 0, 10.5, Blocks.COARSE_DIRT.defaultBlockState());
        for (int i = 0; i < 5; i++) {
            int cx = c - 6 + b.rand(60 + i, 13), cz = c - 6 + b.rand(70 + i, 13);
            disc(b, cx, cz, 0, 1.5 + b.rand(80 + i, 2), Blocks.ROOTED_DIRT.defaultBlockState());
            b.set(cx, -1, cz, Blocks.DIRT);
        }
        for (double a = 0; a < Math.PI * 2 - 0.01; a += Math.PI / 4) {
            if (Math.abs(Math.cos(a)) > 0.9) continue; // leave the hut sides open
            int sx = c + (int) Math.round(Math.cos(a) * 11), sz = c + (int) Math.round(Math.sin(a) * 11);
            int hgt = 3 + b.rand((int) (a * 100), 3);
            b.fill(sx, 1, sz, sx, hgt, sz, Blocks.MOSSY_COBBLESTONE);
            b.set(sx, hgt + 1, sz, Blocks.MOSSY_COBBLESTONE_WALL);
        }
        b.marker("arc_anchor", c, 1, c, tag("arc", "little_garden_duel"));
        // the two camps facing each other across the arena
        giantHut(sub(b, 1, 0, 17, 11, 11, Rotation.CLOCKWISE_90), Buildings.LOOT_TREASURE);
        giantHut(sub(b, 33, 0, 17, 11, 11, Rotation.COUNTERCLOCKWISE_90), Buildings.LOOT_HOUSE);
        // a great beast's skeleton along the north edge
        for (int x = 8; x <= 32; x++) {
            int sy = 4 + (int) Math.round(3 * Math.sin(Math.PI * (x - 8) / 24.0));
            b.set(x, sy, 6, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
            if (x >= 13 && x <= 28 && (x & 1) == 0) {
                for (int k = 1; k <= 3; k++) {
                    int ry = sy - (k * k) / 2;
                    if (ry < 1) ry = 1;
                    b.set(x, ry, 6 - k, Blocks.BONE_BLOCK);
                    b.set(x, ry, 6 + k, Blocks.BONE_BLOCK);
                }
                b.fill(x, 1, 3, x, sy - 4 < 1 ? 1 : sy - 4, 3, Blocks.BONE_BLOCK);
                b.fill(x, 1, 9, x, sy - 4 < 1 ? 1 : sy - 4, 9, Blocks.BONE_BLOCK);
            }
        }
        for (int[] leg : new int[][]{{15, 4}, {15, 8}, {26, 4}, {26, 8}}) b.fill(leg[0], 1, leg[1], leg[0], 3, leg[1], Blocks.BONE_BLOCK);
        b.fill(33, 2, 4, 36, 5, 8, Blocks.BONE_BLOCK);
        b.clear(35, 4, 4, 35, 4, 4);
        b.clear(35, 4, 8, 35, 4, 8);
        b.fill(34, 1, 5, 37, 1, 7, Blocks.BONE_BLOCK);
        for (int z = 5; z <= 7; z++) b.set(37, 2, z, Blocks.CALCITE);
        b.set(36, 6, 4, Blocks.BONE_BLOCK);
        b.set(36, 7, 3, Blocks.BONE_BLOCK);
        // buried under the skull: a cache left by the first explorers
        b.fill(33, -3, 5, 35, -1, 7, Blocks.DIRT);
        b.chest(34, -2, 6, Direction.NORTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 34, -2, 6);
        // tally stones: a century of drawn duels
        for (int x = 10; x <= 34; x++) {
            b.set(x, 1, 38, Blocks.STONE_BRICKS);
            b.set(x, 2, 38, x % 5 == 4 ? Blocks.CRACKED_STONE_BRICKS : Blocks.CHISELED_STONE_BRICKS);
        }
        b.marker("legend", c, 1, 37, tag("story", "giant_duel"));
        // jungle around the edges
        for (int[] p : new int[][]{{5, 36}, {40, 40}, {16, 40}, {29, 40}, {39, 33}, {4, 10}}) palm(b, p[0], 1, p[1], 7 + b.rand(p[0] * 7 + p[1], 3), p[0] * 13 + p[1]);
        tree(b, 41, 1, 9, Blocks.JUNGLE_LOG, Blocks.JUNGLE_LEAVES, 8, 3, 91, null);
        npc(b, "scholar", 22, 1, 34);
    }

    // ============================================================== Little Garden: the volcano vent

    private static final Block[] LAVA_ROCK = {Blocks.BASALT, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.SMOOTH_BASALT, Blocks.BLACKSTONE, Blocks.BASALT};

    /** The volcano whose eruptions start each duel: a smoking cone with a lava crater and a lava-tube cave inside. */
    static void volcanoVent(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = 13; // 27 x 27
        site(b, Blocks.COARSE_DIRT, Blocks.DIRT, 26);
        patchGround(b, 61, 0, Blocks.COARSE_DIRT, Blocks.BLACKSTONE, Blocks.TUFF, Blocks.GRAVEL);
        // the cone
        for (int y = 1; y <= 17; y++) {
            double r = 12.4 - y * 0.55;
            for (int x = 0; x < w; x++)
                for (int z = 0; z < w; z++) {
                    double dd = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
                    if (dd > r + b.rand(x * 31 + z * 17 + y, 2) * 0.6) continue;
                    b.set(x, y, z, LAVA_ROCK[b.rand(x * 7 + y * 131 + z * 13, LAVA_ROCK.length)]);
                }
        }
        // crater with a lava lake and smoke
        for (int y = 13; y <= 18; y++) disc(b, c, c, y, 4.4, Blocks.AIR.defaultBlockState());
        disc(b, c, c, 12, 4.4, Blocks.BASALT.defaultBlockState());
        disc(b, c, c, 11, 3.5, Blocks.MAGMA_BLOCK.defaultBlockState());
        disc(b, c, c, 12, 2.5, Blocks.LAVA.defaultBlockState());
        b.set(c + 4, 13, c, Blocks.CAMPFIRE);
        b.set(c - 4, 13, c, Blocks.CAMPFIRE);
        b.set(c, 13, c + 4, Blocks.CAMPFIRE);
        // the lava tube: tunnel from the north into a domed chamber
        carveEllipsoid(b, c, 2, c, 6.2, 5.2, 6.2, -1);
        b.fill(c - 6, 0, c - 6, c + 6, 0, c + 6, Blocks.SMOOTH_BASALT);
        b.clear(c - 1, 1, 0, c + 1, 3, c - 4);
        b.fill(c - 1, 0, 0, c + 1, 0, c - 4, Blocks.SMOOTH_BASALT);
        b.fill(c - 2, 1, 0, c - 2, 4, 0, axis(Blocks.BASALT, Direction.Axis.Y));
        b.fill(c + 2, 1, 0, c + 2, 4, 0, axis(Blocks.BASALT, Direction.Axis.Y));
        b.fill(c - 2, 4, 0, c + 2, 4, 0, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(c, 3, 1, lantern(Blocks.LANTERN, true));
        b.set(c - 1, 1, 1, Blocks.SKELETON_SKULL);
        // chamber dressing: glowing seams, a lava pocket behind glass, crystals
        for (int[] g : new int[][]{{c, c}, {c - 3, c - 2}, {c + 3, c + 2}, {c - 2, c + 3}}) b.set(g[0], 7, g[1], Blocks.SHROOMLIGHT);
        b.set(c, 6, c, lantern(Blocks.LANTERN, true));
        b.fill(c - 6, 2, c - 1, c - 6, 3, c + 1, Blocks.TINTED_GLASS);
        b.fill(c - 7, 2, c - 1, c - 7, 3, c + 1, Blocks.LAVA);
        b.fill(c - 8, 1, c - 2, c - 8, 4, c + 2, Blocks.BASALT);
        b.fill(c - 7, 1, c - 2, c - 7, 1, c + 2, Blocks.BASALT);
        b.fill(c - 7, 4, c - 2, c - 7, 4, c + 2, Blocks.BASALT);
        b.set(c - 7, 2, c - 2, Blocks.BASALT);
        b.set(c - 7, 3, c - 2, Blocks.BASALT);
        b.set(c - 7, 2, c + 2, Blocks.BASALT);
        b.set(c - 7, 3, c + 2, Blocks.BASALT);
        b.set(c + 2, 1, c - 5, Blocks.AMETHYST_CLUSTER);
        b.set(c - 4, 1, c + 4, Blocks.AMETHYST_CLUSTER);
        // a volcanologist's camp
        b.bed(c + 2, 1, c + 2, Blocks.ORANGE_BED, Direction.SOUTH);
        b.set(c + 4, 1, c + 1, Blocks.CRAFTING_TABLE);
        b.set(c + 4, 1, c, Blocks.BREWING_STAND);
        b.set(c + 4, 1, c - 1, facing(Blocks.LECTERN, Direction.WEST));
        b.chest(c + 4, 1, c - 2, Direction.WEST, Buildings.LOOT_HOUSE);
        npc(b, "scholar", c + 1, 1, c - 1);
        // the sealed grotto behind a cracked seam (east)
        b.set(c + 6, 1, c + 3, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.set(c + 6, 2, c + 3, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.clear(c + 5, 1, c + 3, c + 5, 2, c + 3);
        b.clear(c + 7, 1, c + 1, c + 8, 3, c + 4);
        b.fill(c + 7, 0, c + 1, c + 8, 0, c + 4, Blocks.OBSIDIAN);
        b.set(c + 8, 1, c + 1, Blocks.AMETHYST_BLOCK);
        b.set(c + 8, 2, c + 1, Blocks.AMETHYST_CLUSTER);
        b.set(c + 7, 3, c + 4, Blocks.CRYING_OBSIDIAN);
        b.chest(c + 8, 1, c + 3, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(c + 8, 3, c + 2, Blocks.OCHRE_FROGLIGHT);
        mark(b, "treasure", c + 8, 1, c + 3);
        b.marker("legend", c, 1, 2, tag("story", "volcano_vent"));
    }

    // ============================================================== Alabasta: Alubarna palace

    /** Onion-domed tower: round shaft r=3 from y1 to y2, bulb and spire above. */
    private static void onionTower(Builder b, int x, int z, int y1, int y2, Block wall, Block dome) {
        b.cylinder(x, z, y1, y2, 3, wall, true);
        b.cylinder(x, z, y1, y2, 2, Blocks.AIR, false);
        ellipsoid(b, x, y2 + 3, z, 4, 3.5, 4, dome.defaultBlockState(), false, -2);
        cone(b, x, z, y2 + 6, 4, 2, 0.4, dome.defaultBlockState(), false);
        b.set(x, y2 + 10, z, Blocks.GOLD_BLOCK);
        b.set(x, y2 + 11, z, Blocks.LIGHTNING_ROD);
    }

    /** The royal palace of the desert kingdom: plaza with a clock tower, a domed throne hall and the royal tomb below. */
    static void alubarnaPalace(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 27; // 55 x 55
        final int P = 3; // palace floor level (top of the platform)
        Block wall = Blocks.SMOOTH_SANDSTONE, cut = Blocks.CUT_SANDSTONE, chis = Blocks.CHISELED_SANDSTONE;
        site(b, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, 46);
        // ---- plaza: paving grid, fountain, palms, the clock tower
        for (int x = 0; x < w; x += 6) b.fill(x, 0, 0, x, 0, 16, cut);
        for (int z = 0; z <= 16; z += 6) b.fill(0, 0, z, w - 1, 0, z, cut);
        ring(b, c, 8, 1, 3, cut.defaultBlockState());
        disc(b, c, 8, 0, 2.5, Blocks.WATER.defaultBlockState());
        b.fill(c, 0, 8, c, 4, 8, chis);
        b.set(c, 5, 8, Blocks.GOLD_BLOCK);
        for (int[] p : new int[][]{{5, 5}, {35, 4}, {5, 13}, {49, 13}}) palm(b, p[0], 1, p[1], 7, p[0] * 31 + p[1]);
        // clock tower (x 41..47, z 3..9)
        b.fill(41, 1, 3, 47, 24, 9, cut);
        b.clear(42, 1, 4, 46, 23, 8);
        b.fill(41, 0, 3, 47, 0, 9, chis);
        b.door(41, 1, 6, Blocks.ACACIA_DOOR, Direction.WEST);
        b.fill(42, 18, 4, 46, 18, 8, Blocks.ACACIA_PLANKS);
        b.ladderUp(46, 1, 6, 18, Direction.WEST);
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) if (dx * dx + dy * dy <= 5) b.set(44 + dx, 21 + dy, 3, Blocks.WHITE_CONCRETE);
        b.fill(44, 21, 3, 44, 23, 3, Blocks.BLACK_CONCRETE);
        b.fill(45, 21, 3, 46, 21, 3, Blocks.BLACK_CONCRETE);
        b.set(44, 21, 3, Blocks.GOLD_BLOCK);
        b.set(42, 20, 3, connected(Blocks.IRON_BARS, Direction.Axis.X));
        ellipsoid(b, 44, 25, 6, 3.6, 3, 3.6, wall.defaultBlockState(), false, 0);
        b.set(44, 29, 6, Blocks.GOLD_BLOCK);
        b.chest(42, 19, 4, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.hanging(45, 23, 7, Blocks.LANTERN);
        npc(b, "guard", 43, 19, 7);
        for (int x = 42; x <= 46; x += 2) b.hanging(x, 17, 5, Blocks.LANTERN);
        npc(b, "merchant", 12, 1, 10);
        npc(b, "resident", 20, 1, 4);
        npc(b, "child", 33, 1, 12);

        // ---- the raised palace platform, front stairs and outer wall
        b.fill(6, 1, 17, 48, P, 52, cut);
        b.fill(6, P, 17, 48, P, 52, wall);
        stairsUp(b, 32, 1, 14, Direction.SOUTH, 3, Blocks.SANDSTONE_STAIRS, 11, cut);
        b.walls(6, P + 1, 20, 48, P + 8, 52, wall);
        crenelX(b, 6, 48, P + 9, 20, Blocks.SANDSTONE_WALL);
        crenelX(b, 6, 48, P + 9, 52, Blocks.SANDSTONE_WALL);
        crenelZ(b, 20, 52, P + 9, 6, Blocks.SANDSTONE_WALL);
        crenelZ(b, 20, 52, P + 9, 48, Blocks.SANDSTONE_WALL);
        archX(b, c, 2, P + 1, 20, 20, 4);
        for (int x : new int[]{23, 31}) b.set(x, P + 5, 19, banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.NORTH));
        npc(b, "guard", 24, P + 1, 18);
        npc(b, "guard", 30, P + 1, 18);
        // courtyard: reflecting pool and palms
        b.fill(24, P, 22, 30, P, 26, Blocks.WATER);
        b.fill(23, P, 21, 31, P, 21, chis);
        for (int[] p : new int[][]{{11, 23}, {43, 23}}) palm(b, p[0], P + 1, p[1], 6, p[0] * 7);
        for (int x = 9; x <= 45; x += 4) if (x < 22 || x > 32) b.set(x, P + 1, 26, Blocks.POTTED_CACTUS);

        // ---- the palace (x 12..42, z 28..50): two storeys (floors P and P+7), throne hall under the great dome
        int x1 = 12, x2 = 42, z1 = 28, z2 = 50;
        b.walls(x1, P + 1, z1, x2, P + 12, z2, wall);
        b.clear(x1 + 1, P + 1, z1 + 1, x2 - 1, P + 12, z2 - 1);
        b.fill(x1, P + 13, z1, x2, P + 13, z2, cut);
        crenelX(b, x1, x2, P + 14, z1, Blocks.SANDSTONE_WALL);
        crenelX(b, x1, x2, P + 14, z2, Blocks.SANDSTONE_WALL);
        b.fill(x1 + 1, P + 7, z1 + 1, x2 - 1, P + 7, z2 - 1, Blocks.ACACIA_PLANKS);
        b.fill(x1, P + 6, z1, x2, P + 6, z1, Blocks.BLUE_TERRACOTTA);
        for (int x = x1 + 2; x <= x2 - 2; x += 3) {
            if (Math.abs(x - c) <= 2) continue;
            b.set(x, P + 2, z1, connected(Blocks.ORANGE_STAINED_GLASS_PANE, Direction.Axis.X));
            b.set(x, P + 3, z1, connected(Blocks.ORANGE_STAINED_GLASS_PANE, Direction.Axis.X));
            b.set(x, P + 9, z1, connected(Blocks.ORANGE_STAINED_GLASS_PANE, Direction.Axis.X));
            b.set(x, P + 10, z1, connected(Blocks.ORANGE_STAINED_GLASS_PANE, Direction.Axis.X));
        }
        windowsZ(b, z1 + 3, z2 - 3, P + 9, x1, 4, Blocks.ORANGE_STAINED_GLASS_PANE);
        windowsZ(b, z1 + 3, z2 - 3, P + 9, x2, 4, Blocks.ORANGE_STAINED_GLASS_PANE);
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, P + 1, z1, Blocks.ACACIA_DOOR, Direction.NORTH);
        b.fill(c - 2, P + 4, z1, c + 2, P + 4, z1, chis);
        // corner towers with onion domes
        for (int[] t : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) onionTower(b, t[0], t[1], P + 1, P + 16, wall, Blocks.SMOOTH_QUARTZ);
        // the north-west tower holds a lookout: a doorway from the foyer, a ladder up to a room under the bulb
        b.clear(x1 + 3, P + 1, z1 + 1, x1 + 3, P + 2, z1 + 1);
        b.ladderUp(x1, P + 1, z1 - 2, 13, Direction.SOUTH);
        b.fill(x1 - 1, P + 13, z1 - 1, x1 + 1, P + 13, z1 + 1, Blocks.ACACIA_PLANKS);
        b.set(x1 - 3, P + 15, z1, Blocks.AIR);
        b.set(x1, P + 15, z1 - 3, Blocks.AIR);
        npc(b, "guard", x1, P + 14, z1);
        // internal walls: front foyer (z 29..31), throne hall (x 20..34), wings either side
        b.fill(x1 + 1, P + 1, 32, x2 - 1, P + 12, 32, wall);
        b.fill(19, P + 1, 33, 19, P + 12, z2 - 1, wall);
        b.fill(35, P + 1, 33, 35, P + 12, z2 - 1, wall);
        archX(b, c, 2, P + 1, 32, 32, 3);
        for (int x : new int[]{16, 38}) {
            b.clear(x, P + 1, 32, x, P + 3, 32);
            b.clear(x, P + 8, 32, x, P + 10, 32);
        }
        b.clear(19, P + 1, 40, 19, P + 3, 41);
        b.clear(35, P + 1, 40, 35, P + 3, 41);
        // the great dome over the throne hall
        b.dome(c, P + 12, 41, 8, wall);
        disc(b, c, 41, P + 13, 7.5, Blocks.AIR.defaultBlockState());
        b.clear(20, P + 7, 33, 34, P + 7, z2 - 1);
        b.fill(20, P + 12, 33, 34, P + 12, z2 - 1, Blocks.AIR);
        ring(b, c, 41, P + 12, 8, Blocks.BLUE_TERRACOTTA.defaultBlockState());
        b.set(c, P + 21, 41, Blocks.GOLD_BLOCK);
        b.set(c, P + 22, 41, Blocks.LIGHTNING_ROD);
        b.hanging(c, P + 19, 41, Blocks.LANTERN);
        b.set(c, P + 20, 41, wall);
        // throne hall dressing
        carpet(b, c - 1, P + 1, 33, c + 1, 45, Blocks.RED_CARPET);
        for (int z = 35; z <= 47; z += 4) {
            b.fill(21, P + 1, z, 21, P + 10, z, Blocks.CUT_SANDSTONE);
            b.fill(33, P + 1, z, 33, P + 10, z, Blocks.CUT_SANDSTONE);
            b.set(21, P + 11, z, chis);
            b.set(33, P + 11, z, chis);
        }
        b.fill(24, P + 1, 46, 30, P + 1, 49, Blocks.SMOOTH_QUARTZ);
        b.set(c, P + 2, 48, stair(Blocks.SANDSTONE_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.set(c, P + 2, 49, Blocks.GOLD_BLOCK);
        b.set(c, P + 3, 49, Blocks.GOLD_BLOCK);
        b.set(c - 1, P + 2, 48, Blocks.GOLD_BLOCK);
        b.set(c + 1, P + 2, 48, Blocks.GOLD_BLOCK);
        for (int x : new int[]{23, 31}) b.set(x, P + 6, 49, banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.NORTH));
        npc(b, "noble", c, P + 2, 47);
        npc(b, "guard", 24, P + 1, 44);
        npc(b, "guard", 30, P + 1, 44);
        // west wing: barracks below, royal bedrooms above; stair up along the west wall
        for (int z = 35; z <= 47; z += 3) b.bed(14, P + 1, z, Blocks.LIGHT_BLUE_BED, Direction.WEST);
        b.chest(18, P + 1, 34, Direction.WEST, Buildings.LOOT_MARINE);
        b.set(18, P + 1, 36, Blocks.SMITHING_TABLE);
        b.set(18, P + 1, 37, Blocks.GRINDSTONE);
        lamp(b, 16, P + 6, 38);
        npc(b, "guard", 16, P + 1, 44);
        stairsUp(b, 17, P + 1, 49, Direction.NORTH, 7, Blocks.SANDSTONE_STAIRS, 2, cut);
        b.fill(x1 + 1, P + 8, 41, 18, P + 12, 41, wall);
        b.door(15, P + 8, 41, Blocks.ACACIA_DOOR, Direction.NORTH);
        b.bed(14, P + 8, 44, Blocks.WHITE_BED, Direction.SOUTH);
        b.bed(16, P + 8, 44, Blocks.WHITE_BED, Direction.SOUTH);
        carpet(b, 13, P + 8, 34, 18, 39, Blocks.ORANGE_CARPET);
        b.chest(13, P + 8, 47, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(18, P + 8, 47, facing(Blocks.LOOM, Direction.WEST));
        lamp(b, 15, P + 12, 36);
        lamp(b, 15, P + 12, 45);
        npc(b, "noble", 16, P + 8, 36);
        // east wing: dining room and kitchen below; library and shrine above
        longTableZ(b, 35, 43, P + 1, 38, Blocks.ACACIA_SLAB, Blocks.ACACIA_STAIRS, true);
        kitchenX(b, 36, P + 1, 49, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.barrel(41, P + 1, 49, Buildings.LOOT_TAVERN);
        lamp(b, 38, P + 6, 39);
        npc(b, "cook", 39, P + 1, 47);
        npc(b, "waiter", 40, P + 1, 34);
        b.fill(36, P + 8, 49, 41, P + 10, 49, Blocks.BOOKSHELF);
        b.fill(41, P + 8, 34, 41, P + 10, 46, Blocks.BOOKSHELF);
        table(b, 38, P + 8, 40, Blocks.ACACIA_SLAB);
        b.set(38, P + 9, 40, candles(Blocks.CANDLE, 3));
        b.set(37, P + 8, 36, Blocks.ENCHANTING_TABLE);
        lamp(b, 38, P + 12, 42);
        npc(b, "scholar", 37, P + 8, 44);
        npc(b, "priest", 26, P + 8, 30);
        // front foyer: balcony over the courtyard on the upper floor
        b.fill(22, P + 7, 26, 32, P + 7, 27, cut);
        line(b, 22, P + 8, 26, 32, 26, Blocks.SANDSTONE_WALL);
        b.door(c, P + 8, z1, Blocks.ACACIA_DOOR, Direction.NORTH);
        lamp(b, 20, P + 6, 30);
        lamp(b, 34, P + 6, 30);

        // ---- the royal tomb beneath the throne: hatch under a rug behind the throne, a ladder down
        shell(b, 19, -10, 33, 35, -1, 50, Blocks.SANDSTONE);
        hatch(b, 29, P + 1, 49, -9, Direction.NORTH, Blocks.ACACIA_TRAPDOOR, wall);
        b.set(29, P + 2, 49, Blocks.RED_CARPET);
        for (int x = 20; x <= 34; x++)
            for (int y = -9; y <= -2; y++) {
                if ((x + y) % 4 == 0) b.set(x, y, 33, Blocks.CHISELED_RED_SANDSTONE);
            }
        for (int z = 36; z <= 46; z += 5) {
            b.fill(22, -9, z, 22, -2, z, chis);
            b.fill(32, -9, z, 32, -2, z, chis);
        }
        for (int z = 37; z <= 45; z += 4) {
            b.fill(24, -9, z, 26, -9, z, Blocks.CHISELED_SANDSTONE);
            b.fill(24, -8, z, 26, -8, z, Blocks.SMOOTH_SANDSTONE_SLAB);
            b.fill(28, -9, z, 30, -9, z, Blocks.CHISELED_SANDSTONE);
            b.fill(28, -8, z, 30, -8, z, Blocks.SMOOTH_SANDSTONE_SLAB);
        }
        b.set(c, -9, 34, ModBlocks.PONEGLYPH.get());
        b.set(c, -8, 34, ModBlocks.PONEGLYPH.get());
        b.marker("poneglyph", c, -9, 35, tag("kind", "historical"));
        b.chest(21, -9, 35, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(33, -9, 35, Direction.WEST, Buildings.LOOT_TREASURE);
        for (int z = 36; z <= 46; z += 5) b.hanging(c, -2, z, Blocks.SOUL_LANTERN);
        mark(b, "treasure", 21, -9, 35);
        b.marker("arc_anchor", c, P + 1, 36, tag("arc", "desert_kingdom"));
    }

    // ============================================================== Alabasta: Nanohana port

    private static void awningStall(Builder b, int x, int z, Block wool, String loot) {
        for (int[] c : new int[][]{{x, z}, {x + 4, z}, {x, z + 3}, {x + 4, z + 3}}) b.fill(c[0], 2, c[1], c[0], 4, c[1], Blocks.ACACIA_FENCE);
        for (int i = 0; i <= 4; i++) b.fill(x + i, 5, z, x + i, 5, z + 3, (i & 1) == 0 ? wool : Blocks.WHITE_WOOL);
        for (int i = 1; i <= 3; i++) table(b, x + i, 2, z, Blocks.ACACIA_SLAB);
        b.barrel(x + 1, 2, z + 3, loot);
        b.set(x + 3, 2, z + 3, Blocks.DECORATED_POT);
        b.set(x + 2, 3, z, Blocks.FLOWER_POT);
        npc(b, "merchant", x + 2, 2, z + 2);
    }

    /** The desert port: a harbour basin with piers, a market quay with stalls, shops and a warehouse, a minaret watch. */
    static void nanohanaPort(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 41 x 35, y=0 is sea level, quay surface at y=1
        int q = 14; // quay edge
        b.clear(0, 1, 0, w - 1, 34, d - 1);
        b.fill(0, -6, 0, w - 1, -6, q - 1, Blocks.SAND);
        b.fill(0, -5, 0, w - 1, 0, q - 1, Blocks.WATER);
        b.fill(0, -6, q, w - 1, 0, d - 1, Blocks.SANDSTONE);
        b.fill(0, 1, q, w - 1, 1, d - 1, Blocks.SMOOTH_SANDSTONE);
        b.fill(0, 1, q, w - 1, 1, q, Blocks.CUT_SANDSTONE);
        for (int x = 2; x < w; x += 5) if (x != 37) b.set(x, 2, q, Blocks.SANDSTONE_WALL);
        b.ladderUp(20, -1, q - 1, 3, Direction.NORTH);
        // piers on pilings
        for (int px : new int[]{6, 32}) {
            b.fill(px, 1, 1, px + 2, 1, q - 1, Blocks.SPRUCE_PLANKS);
            for (int z = 1; z < q; z += 3) {
                b.fill(px, -5, z, px, 0, z, Blocks.SPRUCE_LOG);
                b.fill(px + 2, -5, z, px + 2, 0, z, Blocks.SPRUCE_LOG);
            }
            b.fill(px + 1, -1, 1, px + 1, 0, 1, Blocks.SPRUCE_LOG);
            b.ladderUp(px + 1, -1, 0, 3, Direction.NORTH);
            lampPost(b, px, 2, 1, Blocks.SPRUCE_FENCE, 2, Blocks.LANTERN);
            lampPost(b, px + 2, 2, 1, Blocks.SPRUCE_FENCE, 2, Blocks.LANTERN);
            b.set(px + 2, 2, 7, Blocks.BARREL);
            b.set(px, 2, 9, Blocks.HAY_BLOCK);
        }
        npc(b, "dockworker", 7, 2, 5);
        npc(b, "dockworker", 33, 2, 8);
        b.marker("ship_spawn", 20, 0, 6, new net.minecraft.nbt.CompoundTag());
        b.marker("dock", 7, 2, 2, new net.minecraft.nbt.CompoundTag());
        // a smuggler's barrel lashed under the east pier
        b.barrel(33, 0, 10, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 33, 0, 10);
        // ---- market quay with stalls
        awningStall(b, 3, 16, Blocks.ORANGE_WOOL, Buildings.LOOT_SHOP);
        awningStall(b, 14, 16, Blocks.RED_WOOL, Buildings.LOOT_SHOP);
        awningStall(b, 24, 16, Blocks.YELLOW_WOOL, Buildings.LOOT_SHOP);
        npc(b, "marine", 11, 2, 19);
        npc(b, "marine", 30, 2, 15);
        npc(b, "child", 21, 2, 20);
        // minaret watch at the east end of the quay
        b.fill(36, 2, 15, 39, 13, 18, Blocks.SMOOTH_SANDSTONE);
        b.clear(37, 2, 16, 38, 12, 17);
        b.door(37, 2, 15, Blocks.ACACIA_DOOR, Direction.NORTH);
        b.ladderUp(38, 2, 17, 11, Direction.NORTH);
        b.fill(35, 13, 14, 40, 13, 19, Blocks.CUT_SANDSTONE_SLAB);
        b.clear(38, 13, 17, 38, 13, 17);
        b.clear(37, 11, 15, 38, 12, 15);
        outline(b, 35, 14, 14, 40, 19, Blocks.SANDSTONE_WALL);
        for (int[] p : new int[][]{{35, 14}, {40, 14}, {35, 19}, {40, 19}}) b.fill(p[0], 15, p[1], p[0], 16, p[1], Blocks.CUT_SANDSTONE);
        b.fill(35, 17, 14, 40, 17, 19, Blocks.SMOOTH_SANDSTONE_SLAB);
        b.fill(36, 18, 15, 39, 18, 18, Blocks.SMOOTH_QUARTZ_SLAB);
        b.set(37, 19, 16, Blocks.GOLD_BLOCK);
        b.hanging(37, 16, 16, Blocks.LANTERN);
        npc(b, "marine", 36, 14, 16);
        // ---- shops, tavern and warehouse behind the market (doors onto the quay)
        Buildings.house(sub(b, 1, 1, 22, 9, 9, Rotation.NONE), BuildStyle.DESERT, 1, Buildings.Interior.SHOP);
        Buildings.house(sub(b, 12, 1, 22, 13, 11, Rotation.NONE), BuildStyle.DESERT, 1, Buildings.Interior.TAVERN);
        Buildings.house(sub(b, 27, 1, 22, 13, 9, Rotation.NONE), BuildStyle.DESERT, 1, Buildings.Interior.WAREHOUSE);
        b.marker("legend", 20, 2, 15, tag("story", "nanohana_port"));
    }

    // ============================================================== Alabasta: Rainbase casino

    /** A stepped golden casino crowned with a crocodile, gaming floor, VIP lounge with a vault, and the trap basement. */
    static void rainbaseCasino(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 18; // 37 x 37
        Block wall = Blocks.SMOOTH_SANDSTONE, gold = Blocks.YELLOW_TERRACOTTA;
        site(b, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE, 30);
        // moat in front, bridge to the doors
        b.fill(2, -1, 0, w - 3, -1, 3, Blocks.SANDSTONE);
        b.fill(2, 0, 0, w - 3, 0, 3, Blocks.WATER);
        b.fill(c - 2, 0, 0, c + 2, 0, 3, Blocks.CUT_SANDSTONE);
        line(b, c - 3, 1, 0, c - 3, 3, Blocks.SANDSTONE_WALL);
        line(b, c + 3, 1, 0, c + 3, 3, Blocks.SANDSTONE_WALL);
        // the basement (built first; its roof becomes the gaming floor)
        shell(b, 3, -7, 14, 33, 0, 34, Blocks.SANDSTONE);
        // tiers
        int[][] tiers = {{2, 5, 34, 35, 1, 6}, {6, 9, 30, 31, 8, 12}, {10, 13, 26, 27, 14, 17}};
        for (int[] t : tiers) {
            b.fill(t[0], t[4] - 1, t[1], t[2], t[4] - 1, t[3], t[4] == 1 ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_SANDSTONE);
            b.walls(t[0], t[4], t[1], t[2], t[5], t[3], wall);
            b.clear(t[0] + 1, t[4], t[1] + 1, t[2] - 1, t[5], t[3] - 1);
            b.fill(t[0], t[5], t[1], t[2], t[5], t[1], gold);
            b.fill(t[0], t[5], t[3], t[2], t[5], t[3], gold);
            b.fill(t[0], t[4], t[1], t[0], t[5], t[1], Blocks.GOLD_BLOCK);
            b.fill(t[2], t[4], t[1], t[2], t[5], t[1], Blocks.GOLD_BLOCK);
            for (int x = t[0] + 3; x <= t[2] - 3; x += 4) {
                b.set(x, t[4] + 1, t[1], connected(Blocks.ORANGE_STAINED_GLASS_PANE, Direction.Axis.X));
                b.set(x, t[4] + 2, t[1], connected(Blocks.ORANGE_STAINED_GLASS_PANE, Direction.Axis.X));
            }
        }
        b.fill(2, 7, 5, 34, 7, 35, wall);
        b.fill(6, 13, 9, 30, 13, 31, wall);
        b.fill(10, 18, 13, 26, 18, 27, wall);
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, 5, Blocks.ACACIA_DOOR, Direction.NORTH);
        b.fill(c - 2, 4, 5, c + 2, 5, 5, Blocks.GOLD_BLOCK);
        b.fill(c - 2, 0, 4, c + 2, 0, 4, Blocks.CUT_SANDSTONE);
        // ---- gaming floor (y 1..6)
        carpet(b, c - 1, 1, 6, c + 1, 13, Blocks.RED_CARPET);
        for (int[] t : new int[][]{{7, 11}, {26, 11}, {7, 21}, {26, 21}}) {
            for (int x = t[0]; x <= t[0] + 2; x++)
                for (int z = t[1]; z <= t[1] + 1; z++) {
                    table(b, x, 1, z, Blocks.DARK_OAK_SLAB);
                    b.set(x, 2, z, Blocks.GREEN_CARPET);
                }
            for (int x = t[0]; x <= t[0] + 2; x += 2) {
                chair(b, x, 1, t[1] - 1, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
                chair(b, x, 1, t[1] + 2, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
            }
        }
        // roulette wheel
        b.cylinder(c, 18, 1, 1, 2, Blocks.POLISHED_BLACKSTONE, false);
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                if (x * x + z * z > 5) continue;
                b.set(c + x, 2, 18 + z, ((x + z) & 1) == 0 ? Blocks.RED_CARPET : Blocks.BLACK_CARPET);
            }
        b.set(c, 2, 18, Blocks.GOLD_BLOCK);
        // slot machines along the west wall
        for (int z = 8; z <= 30; z += 2) {
            if (z >= 23 && z <= 29) continue;
            b.set(3, 1, z, facing(Blocks.DISPENSER, Direction.EAST));
            b.set(3, 2, z, Blocks.GLOWSTONE);
            chair(b, 4, 1, z, Blocks.SPRUCE_STAIRS, Direction.EAST);
        }
        // bar along the east wall
        b.fill(31, 1, 18, 31, 1, 28, axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
        for (int z = 18; z <= 28; z += 2) b.barrel(33, 1, z, Buildings.LOOT_TAVERN);
        npc(b, "bartender", 32, 1, 23);
        for (int z = 9; z <= 31; z += 6) {
            b.fill(12, 1, z, 12, 6, z, Blocks.GOLD_BLOCK);
            b.fill(24, 1, z, 24, 6, z, Blocks.GOLD_BLOCK);
        }
        for (int[] l : new int[][]{{c, 10}, {c, 24}, {8, 16}, {28, 16}, {8, 28}, {28, 28}}) lamp(b, l[0], 6, l[1]);
        npc(b, "patron", 9, 1, 14);
        npc(b, "patron", 27, 1, 19);
        npc(b, "patron", 16, 1, 22);
        npc(b, "guard", 15, 1, 7);
        // stair down to the basement (west-back) and up to the VIP lounge (east-back)
        stairsUp(b, 5, -6, 23, Direction.SOUTH, 7, Blocks.SANDSTONE_STAIRS, 2, Blocks.SANDSTONE);
        line(b, 6, 1, 25, 6, 29, Blocks.ACACIA_FENCE);
        line(b, 4, 1, 25, 5, 25, Blocks.ACACIA_FENCE);
        stairsUp(b, 27, 1, 33, Direction.NORTH, 7, Blocks.DARK_OAK_STAIRS, 2, wall);
        line(b, 26, 8, 27, 26, 30, Blocks.ACACIA_FENCE);
        // ---- VIP lounge (y 8..12), office, vault
        carpet(b, 7, 8, 10, 25, 30, Blocks.PURPLE_CARPET);
        b.fill(17, 8, 10, 17, 12, 18, wall);
        b.fill(7, 8, 19, 17, 12, 19, wall);
        b.door(17, 8, 14, Blocks.DARK_OAK_DOOR, Direction.EAST);
        b.set(10, 8, 12, facing(Blocks.LECTERN, Direction.SOUTH));
        table(b, 12, 8, 14, Blocks.DARK_OAK_SLAB);
        table(b, 13, 8, 14, Blocks.DARK_OAK_SLAB);
        chair(b, 12, 8, 15, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        b.fill(8, 8, 18, 16, 10, 18, Blocks.BOOKSHELF);
        b.set(13, 9, 14, candles(Blocks.CANDLE, 3));
        b.hanging(12, 12, 14, Blocks.LANTERN);
        npc(b, "pirate_captain", 10, 8, 15);
        // vault behind an iron door (lever beside it in the lounge)
        b.fill(7, 8, 22, 13, 12, 22, wall);
        b.fill(13, 8, 23, 13, 12, 30, wall);
        b.door(10, 8, 22, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(11, 9, 21, lever(Direction.NORTH));
        b.fill(8, 8, 23, 12, 8, 30, Blocks.AIR);
        for (int x = 8; x <= 12; x += 2) b.set(x, 8, 30, Blocks.GOLD_BLOCK);
        b.chest(8, 8, 27, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(12, 8, 27, Direction.WEST, Buildings.LOOT_TREASURE);
        b.hanging(10, 12, 26, Blocks.LANTERN);
        mark(b, "treasure", 10, 8, 28);
        // lounge: sofas, bar, guards
        for (int x = 19; x <= 25; x++) chair(b, x, 8, 11, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
        table(b, 22, 8, 13, Blocks.SMOOTH_QUARTZ_SLAB);
        b.set(22, 9, 13, Blocks.POTTED_CACTUS);
        b.fill(19, 8, 22, 24, 8, 22, axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
        b.barrel(20, 8, 24, Buildings.LOOT_TAVERN);
        for (int[] l : new int[][]{{22, 16}, {22, 26}}) b.hanging(l[0], 12, l[1], Blocks.LANTERN);
        npc(b, "guard", 21, 8, 17);
        npc(b, "guard", 15, 8, 20);
        // ---- crown suite (y 14..17) reached by a ladder from the lounge
        ladder(b, 25, 8, 26, 6, Direction.NORTH, wall);
        b.bed(12, 14, 24, Blocks.YELLOW_BED, Direction.SOUTH);
        b.chest(14, 14, 26, Direction.NORTH, Buildings.LOOT_HOUSE);
        carpet(b, 15, 14, 18, 21, 23, Blocks.YELLOW_CARPET);
        b.hanging(18, 17, 20, Blocks.LANTERN);
        // the crocodile on the roof
        b.fill(17, 19, 15, 19, 21, 25, Blocks.GREEN_TERRACOTTA);
        b.fill(17, 19, 11, 19, 19, 14, Blocks.GREEN_TERRACOTTA);
        b.fill(17, 21, 11, 19, 21, 14, Blocks.GREEN_TERRACOTTA);
        for (int z = 11; z <= 14; z++) {
            b.set(17, 20, z, Blocks.WHITE_CONCRETE);
            b.set(19, 20, z, Blocks.WHITE_CONCRETE);
        }
        b.set(17, 22, 14, Blocks.YELLOW_CONCRETE);
        b.set(19, 22, 14, Blocks.YELLOW_CONCRETE);
        for (int z = 15; z <= 25; z += 2) b.set(18, 22, z, Blocks.LIME_TERRACOTTA);
        for (int[] l : new int[][]{{16, 16}, {20, 16}, {16, 24}, {20, 24}}) b.set(l[0], 19, l[1], Blocks.GREEN_TERRACOTTA);
        b.fill(18, 19, 26, 18, 20, 27, Blocks.GREEN_TERRACOTTA);
        // ---- basement: a sunken pool under a walkway, seastone cells, thugs
        b.fill(4, -7, 15, 32, -7, 33, Blocks.SANDSTONE);
        b.fill(15, -11, 19, 31, -8, 32, Blocks.SANDSTONE);
        b.fill(16, -10, 20, 30, -7, 31, Blocks.WATER);
        line(b, 16, -6, 19, 30, 19, Blocks.IRON_BARS);
        for (int x = 6; x <= 12; x += 3) {
            b.fill(x, -6, 15, x, -4, 18, wall);
            mark(b, "cell", x + 1, -6, 16);
        }
        b.fill(6, -6, 18, 14, -4, 18, Blocks.IRON_BARS);
        for (int x = 6; x <= 14; x++) for (int y = -6; y <= -4; y++) b.set(x, y, 18, connected(Blocks.IRON_BARS, Direction.Axis.X));
        b.fill(6, -7, 15, 14, -7, 17, ModBlocks.SEASTONE.get());
        b.barrel(32, -6, 33, Buildings.LOOT_WAREHOUSE);
        b.barrel(31, -6, 33, Buildings.LOOT_WAREHOUSE);
        for (int[] l : new int[][]{{10, 25}, {22, 25}, {28, 16}}) b.hanging(l[0], -1, l[1], Blocks.LANTERN);
        npc(b, "bandit", 10, -6, 22);
        npc(b, "bandit", 12, -6, 30);
        b.marker("arc_anchor", c, 1, 9, tag("arc", "desert_casino"));
    }

    // ============================================================== Alabasta: Yuba oasis

    /** A sand-buried oasis town: a half-dug inn, the well an old man keeps digging, and a forgotten cistern below. */
    static void yubaOasis(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 29 x 29
        site(b, Blocks.SAND, Blocks.SANDSTONE, 22);
        scatter(b, 78, 12, 0, 0, w - 1, d - 1, 1, Blocks.DEAD_BUSH.defaultBlockState());
        // dunes
        heap(b, 71, 22, 24, 1, 4, 2, Blocks.SAND);
        heap(b, 72, 4, 4, 1, 3, 2, Blocks.SAND);
        heap(b, 73, 25, 4, 1, 3, 1, Blocks.SAND);
        // ---- the buried inn (floor y=-4, roof y=2) and the trench dug down to its door
        shell(b, 3, -4, 15, 13, 2, 25, Blocks.SANDSTONE);
        b.fill(4, -4, 16, 12, -4, 24, Blocks.SMOOTH_SANDSTONE);
        b.fill(4, 1, 16, 12, 1, 24, Blocks.AIR);
        b.fill(3, 2, 15, 13, 2, 25, Blocks.SANDSTONE_SLAB);
        b.fill(3, 1, 15, 13, 1, 15, Blocks.CUT_SANDSTONE);
        heap(b, 74, 11, 22, 3, 2, 1, Blocks.SAND);
        for (int x = 2; x <= 14; x++) for (int z = 14; z <= 26; z++) {
            boolean edge = x == 2 || x == 14 || z == 26;
            if (edge) b.fill(x, 1, z, x, 2, z, Blocks.SAND);
        }
        stairsUp(b, 7, -4, 14, Direction.NORTH, 5, Blocks.SANDSTONE_STAIRS, 3, Blocks.SANDSTONE);
        b.fill(6, -4, 10, 6, 0, 14, Blocks.SANDSTONE);
        b.fill(10, -4, 10, 10, 0, 14, Blocks.SANDSTONE);
        b.door(8, -3, 15, Blocks.ACACIA_DOOR, Direction.NORTH);
        b.fill(4, -3, 20, 12, 1, 20, Blocks.SANDSTONE);
        b.door(8, -3, 20, Blocks.ACACIA_DOOR, Direction.NORTH);
        // common room
        b.fill(4, -3, 18, 6, -3, 18, axis(Blocks.STRIPPED_ACACIA_LOG, Direction.Axis.X));
        b.barrel(4, -3, 19, Buildings.LOOT_TAVERN);
        b.barrel(5, -3, 19, Buildings.LOOT_TAVERN);
        table(b, 10, -3, 17, Blocks.ACACIA_SLAB);
        chair(b, 11, -3, 17, Blocks.ACACIA_STAIRS, Direction.EAST);
        chair(b, 9, -3, 17, Blocks.ACACIA_STAIRS, Direction.WEST);
        b.set(10, -2, 17, candles(Blocks.CANDLE, 2));
        b.hanging(8, 1, 18, Blocks.LANTERN);
        b.set(12, -3, 16, Blocks.SAND);
        b.set(12, -2, 16, Blocks.SAND);
        npc(b, "innkeeper", 5, -3, 17);
        // guest rooms
        for (int x = 4; x <= 12; x += 2) if (x != 8) b.bed(x, -3, 23, Blocks.ORANGE_BED, Direction.SOUTH);
        b.chest(8, -3, 24, Direction.NORTH, Buildings.LOOT_HOUSE);
        b.hanging(8, 1, 22, Blocks.LANTERN);

        // ---- the well, dug out by hand, a crank above it
        int wx = 20, wz = 8;
        b.fill(wx - 2, -13, wz - 2, wx + 2, 0, wz + 2, Blocks.SANDSTONE);
        b.clear(wx - 1, -12, wz - 1, wx + 1, 0, wz + 1);
        b.fill(wx - 1, -12, wz - 1, wx + 1, -11, wz + 1, Blocks.WATER);
        outline(b, wx - 2, 1, wz - 2, wx + 2, wz + 2, Blocks.SANDSTONE_WALL);
        b.clear(wx, 1, wz - 2, wx, 1, wz - 2);
        b.fill(wx - 2, 2, wz, wx - 2, 3, wz, Blocks.ACACIA_FENCE);
        b.fill(wx + 2, 2, wz, wx + 2, 3, wz, Blocks.ACACIA_FENCE);
        b.fill(wx - 2, 4, wz, wx + 2, 4, wz, axis(Blocks.STRIPPED_ACACIA_LOG, Direction.Axis.X));
        b.set(wx, 3, wz, Blocks.CHAIN);
        b.ladderUp(wx + 1, -10, wz + 1, 11, Direction.WEST);
        b.clear(wx + 2, 1, wz + 1, wx + 2, 1, wz + 1);
        // the cistern found at the bottom: a side tunnel south to a vaulted water chamber
        b.clear(wx + 1, -10, wz + 2, wx + 1, -9, wz + 5);
        shell(b, wx - 4, -11, wz + 6, wx + 4, -6, wz + 12, Blocks.SANDSTONE);
        b.clear(wx + 1, -10, wz + 6, wx + 1, -9, wz + 6);
        b.fill(wx - 3, -11, wz + 8, wx + 1, -11, wz + 11, Blocks.WATER);
        b.fill(wx - 3, -10, wz + 9, wx - 1, -10, wz + 11, Blocks.WATER);
        b.chest(wx + 3, -10, wz + 11, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(wx + 3, -10, wz + 7, Blocks.DECORATED_POT);
        b.hanging(wx, -7, wz + 9, Blocks.LANTERN);
        b.set(wx, -6, wz + 9, Blocks.SANDSTONE);
        mark(b, "treasure", wx + 3, -10, wz + 11);
        // the digger's camp
        for (int z = 14; z <= 17; z++) {
            b.set(22, 1, z, Blocks.WHITE_WOOL);
            b.set(26, 1, z, Blocks.WHITE_WOOL);
            b.set(23, 2, z, Blocks.WHITE_WOOL);
            b.set(25, 2, z, Blocks.WHITE_WOOL);
            b.set(24, 3, z, Blocks.WHITE_WOOL);
        }
        b.fill(22, 1, 17, 26, 2, 17, Blocks.WHITE_WOOL);
        b.bed(24, 1, 15, Blocks.BROWN_BED, Direction.SOUTH);
        b.barrel(23, 1, 16, Buildings.LOOT_HOUSE);
        b.set(24, 0, 12, Blocks.SANDSTONE);
        b.set(24, 1, 12, Blocks.CAMPFIRE);
        b.set(18, 1, 12, Blocks.COMPOSTER);
        npc(b, "elder", 19, 1, 12);
        npc(b, "merchant", 23, 1, 11);
        // dead palms, buried wall-tops and one stubborn living palm
        for (int[] p : new int[][]{{3, 9}, {14, 3}, {26, 21}}) b.fill(p[0], 1, p[1], p[0], 3 + b.rand(p[0], 3), p[1], Blocks.STRIPPED_JUNGLE_LOG);
        palm(b, 16, 1, 22, 6, 77);
        for (int x = 16; x <= 26; x += 2) {
            int h = 1 + b.rand(x * 3, 3);
            if (x >= 20 && x <= 22) continue;
            b.fill(x, 1, 26, x, h, 26, Blocks.CUT_SANDSTONE);
        }
        b.marker("legend", wx, 1, wz - 3, tag("story", "yuba_oasis"));
    }

    // ============================================================== Jaya: the half house

    /** The explorer's house, only its ornate front half standing, with the salvage crew's camp and dive pool behind. */
    static void cricketHouse(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 21 x 19
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 20);
        patchGround(b, 81, 0, Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.COARSE_DIRT);
        int x1 = 3, x2 = 17, z1 = 2, z2 = 6;
        Block face = Blocks.BIRCH_PLANKS, trim = Blocks.LIGHT_BLUE_TERRACOTTA, cutWall = Blocks.SPRUCE_PLANKS;
        // two storeys, three blocks deep, sliced off at z2
        for (int f = 0; f < 2; f++) {
            int y0 = f * 5;
            b.fill(x1, y0, z1, x2, y0, z2, Blocks.OAK_PLANKS);
            b.walls(x1, y0 + 1, z1, x2, y0 + 4, z2, face);
            b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 4, z2 - 1);
        }
        b.fill(x1, 10, z1, x2, 10, z2, Blocks.OAK_PLANKS);
        for (int x = x1; x <= x2; x++)
            for (int y = 1; y <= 9; y++) b.set(x, y, z2, b.rand(x * 7 + y, 4) == 0 ? Blocks.OAK_PLANKS : cutWall);
        b.fill(x1, 5, z1, x2, 5, z1, trim);
        posts(b, x1, 1, z1, x2, 9, z1, Blocks.STRIPPED_BIRCH_LOG);
        // front-facing gable roof (ridge runs front to back) cut off at the back
        for (int i = 0; i <= 7; i++) {
            for (int z = z1 - 1; z <= z2; z++) {
                b.set(x1 - 1 + i, 11 + i, z, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, Half.BOTTOM));
                b.set(x2 + 1 - i, 11 + i, z, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, Half.BOTTOM));
            }
            if (i > 0 && i < 7) {
                b.fill(x1 + i, 11, z1, x2 - i, 10 + i, z1, face);
                b.fill(x1 + i, 11, z2, x2 - i, 10 + i, z2, cutWall);
            }
        }
        for (int z = z1 - 1; z <= z2; z++) b.set(10, 18, z, Blocks.DARK_OAK_SLAB);
        b.set(10, 13, z1, Blocks.GLASS);
        b.set(10, 14, z1, Blocks.GLASS);
        for (int x = x1; x <= x2; x += 2) b.set(x, 10, z1 - 1, stair(Blocks.BIRCH_STAIRS, Direction.SOUTH, Half.TOP));
        // windows, bay and porch
        for (int x : new int[]{5, 8, 12, 15}) {
            b.set(x, 2, z1, connected(Blocks.GLASS_PANE, Direction.Axis.X));
            b.set(x, 3, z1, connected(Blocks.GLASS_PANE, Direction.Axis.X));
            b.set(x, 7, z1, connected(Blocks.GLASS_PANE, Direction.Axis.X));
            b.set(x, 8, z1, connected(Blocks.GLASS_PANE, Direction.Axis.X));
        }
        b.bigDoor(10, 1, z1, Blocks.BIRCH_DOOR, Direction.NORTH);
        b.fill(7, 0, 0, 13, 0, 1, Blocks.OAK_PLANKS);
        for (int x : new int[]{7, 13}) b.fill(x, 1, 0, x, 4, 0, Blocks.BIRCH_FENCE);
        b.fill(7, 5, 0, 13, 5, 1, Blocks.BIRCH_SLAB);
        b.hanging(10, 4, 1, Blocks.LANTERN);
        b.set(6, 1, 1, Blocks.POTTED_RED_TULIP);
        // ground floor: parlour with the old logbooks, kitchen corner
        stairsUp(b, 4, 1, 4, Direction.EAST, 5, Blocks.OAK_STAIRS, 2, Blocks.OAK_PLANKS);
        b.fill(12, 1, z2 - 1, 16, 2, z2 - 1, Blocks.BOOKSHELF);
        b.set(12, 1, 3, facing(Blocks.LECTERN, Direction.SOUTH));
        chair(b, 14, 1, 3, Blocks.OAK_STAIRS, Direction.NORTH);
        b.set(16, 1, 3, Blocks.CRAFTING_TABLE);
        lamp(b, 13, 4, 4);
        npc(b, "elder", 11, 1, 4);
        // upper floor: bedroom and a little study
        b.bed(14, 6, 3, Blocks.LIGHT_BLUE_BED, Direction.EAST);
        b.chest(16, 6, 5, Direction.WEST, Buildings.LOOT_HOUSE);
        b.set(11, 6, 5, Blocks.CARTOGRAPHY_TABLE);
        b.set(12, 6, 5, Blocks.BOOKSHELF);
        lamp(b, 12, 9, 4);
        line(b, 5, 6, 3, 8, 3, Blocks.OAK_FENCE);
        // ---- salvage camp behind the house
        for (int z = 10; z <= 15; z++) {
            b.set(2, 1, z, Blocks.WHITE_WOOL);
            b.set(7, 1, z, Blocks.WHITE_WOOL);
            b.set(3, 2, z, Blocks.WHITE_WOOL);
            b.set(6, 2, z, Blocks.WHITE_WOOL);
            b.fill(4, 3, z, 5, 3, z, Blocks.WHITE_WOOL);
        }
        b.fill(3, 1, 15, 6, 2, 15, Blocks.WHITE_WOOL);
        b.bed(4, 1, 13, Blocks.BROWN_BED, Direction.SOUTH);
        b.barrel(5, 1, 14, Buildings.LOOT_HOUSE);
        b.set(12, 1, 10, Blocks.CARTOGRAPHY_TABLE);
        table(b, 13, 1, 10, Blocks.SPRUCE_SLAB);
        b.set(13, 2, 10, candles(Blocks.CANDLE, 1));
        b.set(14, 0, 13, Blocks.COBBLESTONE);
        b.set(14, 1, 13, Blocks.CAMPFIRE);
        b.set(12, 1, 13, axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Z));
        b.set(16, 1, 13, axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Z));
        // diving bells, anchor and rope
        ellipsoid(b, 17, 2, 16, 1.6, 1.6, 1.6, Blocks.GLASS.defaultBlockState(), true, -1);
        b.set(17, 4, 16, Blocks.CHAIN);
        b.set(19, 1, 11, Blocks.IRON_BLOCK);
        b.fill(19, 2, 11, 19, 4, 11, Blocks.CHAIN);
        b.set(18, 1, 11, Blocks.ANVIL);
        b.barrel(19, 1, 14, Buildings.LOOT_WAREHOUSE);
        npc(b, "dockworker", 11, 1, 12);
        npc(b, "dockworker", 15, 1, 16);
        // the practice dive pool: something glints at the bottom
        b.fill(7, -8, 15, 11, 0, 18, Blocks.STONE);
        b.fill(8, -7, 16, 10, 0, 17, Blocks.WATER);
        b.chest(9, -7, 17, Direction.NORTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 9, -7, 17);
        b.marker("legend", 10, 1, 8, tag("story", "half_house"));
    }

    private ParadiseLandmarks() {}
}
