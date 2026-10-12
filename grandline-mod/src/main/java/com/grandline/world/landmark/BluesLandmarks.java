package com.grandline.world.landmark;

import com.grandline.world.Island;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import static com.grandline.world.landmark.LandmarkKit.*;

/** Set pieces of the four Blues: Dawn, Syrup, Cocoyasi, Flevance, Lvneel, Spider Miles and Ilisia. */
final class BluesLandmarks {

    private static final Block[] SCRAP_PLANKS = {Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.MOSSY_COBBLESTONE};
    private static final Block[] SCRAP_SLABS = {Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.DARK_OAK_SLAB, Blocks.BIRCH_SLAB};

    /** Ramshackle hut of mismatched planks with a slab roof; door on the given wall. Interior y = 1..h. */
    private static void shack(Builder b, int x1, int z1, int x2, int z2, int h, int doorX, int doorZ, Direction doorFacing, int salt) {
        b.fill(x1, 0, z1, x2, 0, z2, Blocks.SPRUCE_PLANKS);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                for (int y = 1; y <= h; y++) {
                    if (edge) b.set(x, y, z, SCRAP_PLANKS[b.rand(salt + x * 31 + y * 7 + z * 13, SCRAP_PLANKS.length)]);
                    else b.set(x, y, z, Blocks.AIR);
                }
                b.set(x, h + 1, z, SCRAP_SLABS[b.rand(salt + x * 5 + z * 3, SCRAP_SLABS.length)]);
            }
        // a patched window on the long walls
        b.set((x1 + x2) / 2 + 1, 2, z1, connected(Blocks.IRON_BARS, Direction.Axis.X));
        b.set((x1 + x2) / 2 - 1, 2, z2, connected(Blocks.GLASS_PANE, Direction.Axis.X));
        b.door(doorX, 1, doorZ, Blocks.OAK_DOOR, doorFacing);
    }

    // ============================================================== Dawn Island: Gray Terminal

    /** The garbage-heap shanty town against the kingdom's wall: shacks, junk heaps, a gate guardroom and a treehouse. */
    static void grayTerminal(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 37 x 33
        site(b, Blocks.COARSE_DIRT, Blocks.DIRT, 26);
        patchGround(b, 11, 0, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT);
        scatter(b, 25, 14, 1, 1, 15, 26, 1, Blocks.SHORT_GRASS.defaultBlockState());
        scatter(b, 26, 6, 21, 9, 34, 26, 1, Blocks.DEAD_BUSH.defaultBlockState());
        // central track from the slum to the city gate
        b.fill(17, 0, 0, 19, 0, 27, Blocks.DIRT_PATH);
        b.clear(17, 1, 0, 19, 1, 27);

        // ---- the kingdom's great wall along the back (z 28..32) with a barred gate and a guardroom
        for (int x = 0; x < w; x++)
            for (int z = 28; z < d; z++)
                for (int y = 1; y <= 13; y++) {
                    int r = b.rand(x * 131 + y * 17 + z * 7, 9);
                    b.set(x, y, z, r == 0 ? Blocks.MOSSY_STONE_BRICKS : r == 1 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS);
                }
        crenelX(b, 0, w - 1, 14, 28, Blocks.STONE_BRICKS);
        crenelX(b, 0, w - 1, 14, d - 1, Blocks.STONE_BRICKS);
        b.clear(16, 1, 28, 20, 6, d - 1);
        for (int y = 1; y <= 6; y++) line(b, 16, y, 30, 20, 30, Blocks.IRON_BARS);
        b.fill(15, 7, 28, 21, 7, 28, Blocks.CHISELED_STONE_BRICKS);
        b.set(18, 8, 27, banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        // guardroom inside the wall
        b.clear(22, 1, 29, 29, 4, 31);
        b.fill(22, 0, 29, 29, 0, 31, Blocks.SPRUCE_PLANKS);
        b.door(25, 1, 28, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(24, 2, 27, button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(26, 2, 29, button(Blocks.STONE_BUTTON, Direction.SOUTH));
        b.bed(28, 1, 29, Blocks.BLUE_BED, Direction.SOUTH);
        b.chest(29, 1, 31, Direction.WEST, Buildings.LOOT_HOUSE);
        table(b, 24, 1, 31, Blocks.SPRUCE_SLAB);
        chair(b, 23, 1, 31, Blocks.SPRUCE_STAIRS, Direction.WEST);
        b.set(24, 2, 31, candles(Blocks.CANDLE, 2));
        b.set(27, 1, 31, Blocks.LECTERN);
        lamp(b, 25, 4, 30);
        // ladder shaft up to the wall walk
        b.clear(22, 5, 30, 22, 13, 30);
        b.ladderUp(22, 1, 30, 13, Direction.EAST);
        npc(b, "guard", 18, 14, 30);
        npc(b, "guard", 14, 1, 26);
        npc(b, "guard", 25, 1, 30);

        // ---- junk heaps with smouldering fires
        heap(b, 21, 5, 4, 1, 3, 3, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SPRUCE_PLANKS, Blocks.COMPOSTER);
        heap(b, 22, 31, 23, 1, 3, 3, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.TUFF, Blocks.OAK_PLANKS, Blocks.BONE_BLOCK);
        heap(b, 23, 12, 3, 1, 2, 2, Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.SPRUCE_PLANKS);
        heap(b, 24, 13, 21, 1, 2, 2, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.HAY_BLOCK, Blocks.MOSSY_COBBLESTONE);
        b.set(9, 0, 6, Blocks.COBBLESTONE);
        b.set(9, 1, 6, Blocks.CAMPFIRE);
        b.set(27, 0, 21, Blocks.COBBLESTONE);
        b.set(27, 1, 21, Blocks.CAMPFIRE);

        // ---- communal fire pit
        b.set(13, 0, 10, Blocks.COBBLESTONE);
        b.set(13, 1, 10, Blocks.CAMPFIRE);
        b.set(11, 1, 10, axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Z));
        b.set(15, 1, 10, axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Z));
        b.set(13, 1, 12, axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.X));
        b.set(14, 1, 8, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2));
        npc(b, "resident", 12, 1, 9);

        // ---- shacks
        shack(b, 2, 12, 8, 17, 3, 8, 14, Direction.EAST, 31);
        b.bed(3, 1, 13, Blocks.BROWN_BED, Direction.SOUTH);
        b.barrel(7, 1, 16, Buildings.LOOT_HOUSE);
        b.set(3, 1, 16, Blocks.CRAFTING_TABLE);
        b.set(4, 1, 16, candles(Blocks.CANDLE, 1));
        npc(b, "resident", 5, 1, 14);

        shack(b, 24, 12, 30, 17, 3, 24, 15, Direction.WEST, 47);
        b.bed(29, 1, 13, Blocks.LIGHT_GRAY_BED, Direction.SOUTH);
        b.bed(27, 1, 13, Blocks.LIGHT_GRAY_BED, Direction.SOUTH);
        b.barrel(29, 1, 16, Buildings.LOOT_HOUSE);
        b.set(26, 1, 16, Blocks.COMPOSTER);
        npc(b, "child", 27, 1, 15);

        // bandit hideout: bigger shack with a lookout perch
        shack(b, 2, 20, 9, 26, 4, 9, 23, Direction.EAST, 61);
        b.chest(3, 1, 25, Direction.EAST, Buildings.LOOT_TREASURE);
        b.barrel(3, 1, 21, Buildings.LOOT_TAVERN);
        b.barrel(4, 1, 21, Buildings.LOOT_TAVERN);
        table(b, 6, 1, 23, Blocks.DARK_OAK_SLAB);
        chair(b, 5, 1, 23, Blocks.DARK_OAK_STAIRS, Direction.WEST);
        b.set(6, 2, 23, lantern(Blocks.LANTERN, false));
        b.bed(7, 1, 25, Blocks.RED_BED, Direction.WEST);
        b.fill(3, 6, 22, 3, 7, 22, Blocks.SPRUCE_FENCE);
        b.set(3, 7, 21, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        npc(b, "bandit", 5, 1, 21);
        npc(b, "bandit", 7, 1, 22);

        // junk trader's awning stall
        for (int[] c : new int[][]{{21, 3}, {25, 3}, {21, 7}, {25, 7}}) b.fill(c[0], 1, c[1], c[0], 3, c[1], Blocks.SPRUCE_FENCE);
        for (int x = 21; x <= 25; x++) b.fill(x, 4, 3, x, 4, 7, (x & 1) == 0 ? Blocks.BROWN_WOOL : Blocks.WHITE_WOOL);
        for (int x = 22; x <= 24; x++) table(b, x, 1, 3, Blocks.SPRUCE_SLAB);
        b.barrel(22, 1, 7, Buildings.LOOT_SHOP);
        b.barrel(24, 1, 7, Buildings.LOOT_SHOP);
        b.set(23, 2, 3, Blocks.FLOWER_POT);
        npc(b, "merchant", 23, 1, 5);

        // ---- the old oak with the brothers' treehouse and a cache hidden inside its trunk
        tree(b, 31, 1, 5, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 14, 5, 71, null);
        b.fill(30, 1, 4, 32, 13, 6, Blocks.OAK_LOG);
        b.fill(27, 8, 1, 35, 8, 9, Blocks.SPRUCE_PLANKS);
        b.walls(28, 9, 2, 34, 11, 8, Blocks.SPRUCE_PLANKS);
        b.clear(29, 9, 3, 33, 11, 7);
        b.fill(30, 9, 4, 32, 11, 6, Blocks.OAK_LOG);
        b.fill(28, 12, 2, 34, 12, 8, Blocks.SPRUCE_SLAB);
        windowsX(b, 30, 32, 9, 8, 2, Blocks.GLASS_PANE);
        b.set(34, 10, 5, connected(Blocks.GLASS_PANE, Direction.Axis.Z));
        b.clear(31, 9, 2, 31, 10, 2);
        b.clear(31, 8, 3, 31, 8, 3);
        b.ladderUp(31, 1, 3, 8, Direction.NORTH);
        b.chest(29, 9, 7, Direction.EAST, Buildings.LOOT_HOUSE);
        b.bed(33, 9, 3, Blocks.ORANGE_BED, Direction.SOUTH);
        b.set(29, 9, 3, Blocks.CRAFTING_TABLE);
        b.set(29, 10, 3, candles(Blocks.CANDLE, 2));
        for (int y = 13; y <= 16; y++) b.set(34, y, 2, Blocks.SPRUCE_FENCE);
        b.set(34, 16, 1, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        npc(b, "child", 33, 9, 6);
        // the hollow in the trunk (hint: one stripped log on the south side)
        b.set(31, 1, 6, Blocks.STRIPPED_OAK_LOG);
        b.clear(31, 2, 5, 31, 2, 5);
        b.chest(31, 1, 5, Direction.SOUTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 31, 1, 5);
        b.marker("legend", 18, 1, 20, tag("story", "gray_terminal"));
    }

    // ============================================================== Syrup Village: the hilltop mansion

    /** A two-storey Western mansion behind a walled garden, with a hidden study behind the library shelves. */
    static void hilltopMansion(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 33 x 29
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 28);
        Block wall = Blocks.WHITE_TERRACOTTA, post = Blocks.DARK_OAK_LOG, floor = Blocks.DARK_OAK_PLANKS, trim = Blocks.POLISHED_ANDESITE;
        int x1 = 2, x2 = 24, z1 = 10, z2 = 25;
        // ---- garden: wall with iron railing, gate, path, hedges and a little fountain
        for (int x = 0; x < w; x++) b.set(x, 1, 0, Blocks.STONE_BRICKS);
        line(b, 0, 2, 0, w - 1, 0, Blocks.IRON_BARS);
        for (int z = 0; z < d; z++) {
            b.set(0, 1, z, Blocks.STONE_BRICKS);
            b.set(w - 1, 1, z, Blocks.STONE_BRICKS);
        }
        line(b, 0, 2, 1, 0, d - 1, Blocks.IRON_BARS);
        line(b, w - 1, 2, 1, w - 1, d - 1, Blocks.IRON_BARS);
        b.clear(12, 1, 0, 14, 2, 0);
        for (int px : new int[]{11, 15}) {
            b.fill(px, 1, 0, px, 3, 0, Blocks.STONE_BRICKS);
            b.set(px, 4, 0, lantern(Blocks.LANTERN, false));
        }
        b.fill(12, 0, 0, 14, 0, 9, Blocks.GRAVEL);
        b.fill(13, 0, 0, 13, 0, 9, Blocks.SMOOTH_STONE);
        for (int z = 2; z <= 8; z++) {
            b.set(9, 1, z, leaves(Blocks.AZALEA_LEAVES));
            b.set(17, 1, z, leaves(Blocks.AZALEA_LEAVES));
        }
        for (int x = 2; x <= 7; x++) b.set(x, 1, 6, x % 2 == 0 ? Blocks.RED_TULIP : Blocks.PINK_TULIP);
        for (int x = 19; x <= 23; x++) b.set(x, 1, 4, x % 2 == 0 ? Blocks.POPPY : Blocks.ALLIUM);
        b.fill(4, 0, 2, 6, 0, 4, Blocks.STONE_BRICKS);
        b.set(5, 0, 3, Blocks.WATER);
        b.set(5, 1, 3, Blocks.STONE_BRICK_WALL);
        npc(b, "guard", 12, 1, 2);
        npc(b, "farmer", 21, 1, 6);

        // ---- shell of the house: two storeys (floors at y=0 and y=5), ceiling at y=10
        for (int f = 0; f < 2; f++) {
            int y0 = f * 5;
            b.fill(x1, y0, z1, x2, y0, z2, floor);
            b.walls(x1, y0 + 1, z1, x2, y0 + 4, z2, wall);
            b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 4, z2 - 1);
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z1, 3, Blocks.GLASS_PANE);
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z2, 3, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x1, 3, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x2, 3, Blocks.GLASS_PANE);
        }
        b.fill(x1, 10, z1, x2, 10, z2, floor);
        posts(b, x1, 1, z1, x2, 9, z2, post);
        b.fill(x1, 5, z1, x2, 5, z1, trim);
        b.fill(x1, 5, z2, x2, 5, z2, trim);
        hipRoof(b, x1 - 1, z1 - 1, x2 + 1, z2 + 1, 11, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES);
        // chimney over the dining fireplace
        b.fill(x1, 1, 18, x1, 17, 18, Blocks.BRICKS);
        b.fill(x1 + 1, 1, 17, x1 + 1, 3, 19, Blocks.BRICKS);
        b.set(x1 + 1, 1, 18, Blocks.CAMPFIRE);
        b.clear(x1 + 1, 2, 18, x1 + 1, 3, 18);
        b.set(x1, 18, 18, Blocks.BRICK_SLAB);
        // porch with columns and the front door
        b.fill(10, 0, 7, 16, 0, 9, trim);
        for (int px : new int[]{10, 16}) {
            b.fill(px, 1, 7, px, 4, 7, Blocks.QUARTZ_PILLAR);
        }
        b.fill(10, 5, 7, 16, 5, 9, Blocks.SMOOTH_QUARTZ_SLAB);
        b.bigDoor(13, 1, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.hanging(12, 4, z1 - 1, Blocks.LANTERN);

        // ---- ground floor partitions: dining (west) | hall with stair (middle) | parlour + kitchen (east)
        b.fill(10, 1, z1 + 1, 10, 4, z2 - 1, wall);
        b.fill(16, 1, z1 + 1, 16, 4, z2 - 1, wall);
        b.fill(17, 1, 19, x2 - 1, 4, 19, wall);
        b.clear(10, 1, 13, 10, 3, 14);
        b.clear(16, 1, 13, 16, 3, 14);
        b.clear(20, 1, 19, 20, 3, 19);
        // grand staircase in the hall
        stairsUp(b, 12, 1, 21, Direction.NORTH, 5, Blocks.DARK_OAK_STAIRS, 3, Blocks.DARK_OAK_PLANKS);
        carpet(b, 11, 1, 11, 15, 16, Blocks.RED_CARPET);
        lamp(b, 13, 4, 13);
        b.set(11, 1, 24, Blocks.POTTED_FERN);
        b.set(15, 1, 24, Blocks.POTTED_FERN);
        npc(b, "waiter", 14, 1, 15);
        // dining room
        longTableZ(b, 13, 22, 1, 6, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        b.set(9, 1, 11, Blocks.BOOKSHELF);
        b.barrel(9, 1, 24, Buildings.LOOT_TAVERN);
        lamp(b, 6, 4, 15);
        lamp(b, 6, 4, 20);
        // parlour
        carpet(b, 18, 1, 12, 22, 17, Blocks.GREEN_CARPET);
        table(b, 20, 1, 14, Blocks.SPRUCE_SLAB);
        b.set(20, 2, 14, Blocks.POTTED_RED_TULIP);
        chair(b, 18, 1, 14, Blocks.SPRUCE_STAIRS, Direction.WEST);
        chair(b, 22, 1, 14, Blocks.SPRUCE_STAIRS, Direction.EAST);
        b.fill(23, 1, 11, 23, 2, 13, Blocks.BOOKSHELF);
        b.set(17, 1, 11, Blocks.NOTE_BLOCK);
        b.set(18, 1, 11, Blocks.NOTE_BLOCK);
        lamp(b, 20, 4, 14);
        // kitchen
        kitchenX(b, 17, 1, 24, Direction.NORTH, Buildings.LOOT_HOUSE);
        b.barrel(22, 1, 24, Buildings.LOOT_TAVERN);
        b.barrel(23, 1, 24, Buildings.LOOT_TAVERN);
        table(b, 20, 1, 21, Blocks.SMOOTH_STONE_SLAB);
        lamp(b, 20, 4, 22);
        npc(b, "cook", 19, 1, 22);

        // ---- upper floor: landing with railing, bedroom with the tree outside its window, guest room, study
        line(b, 11, 6, 17, 11, 20, Blocks.DARK_OAK_FENCE);
        line(b, 15, 6, 17, 15, 20, Blocks.DARK_OAK_FENCE);
        line(b, 12, 6, 21, 14, 21, Blocks.DARK_OAK_FENCE);
        b.fill(10, 6, z1 + 1, 10, 9, z2 - 1, wall);
        b.fill(16, 6, z1 + 1, 16, 9, z2 - 1, wall);
        b.fill(17, 6, 19, x2 - 1, 9, 19, wall);
        b.fill(x1 + 1, 6, 18, 9, 9, 18, wall);
        b.clear(10, 6, 12, 10, 8, 12);
        b.clear(16, 6, 12, 16, 8, 12);
        b.clear(20, 6, 19, 20, 8, 19);
        lamp(b, 13, 9, 13);
        // heiress' bedroom (east, windows facing the old tree)
        carpet(b, 17, 6, 11, 23, 18, Blocks.PINK_CARPET);
        b.bed(21, 6, 15, Blocks.PINK_BED, Direction.EAST);
        for (int[] c : new int[][]{{21, 14}, {21, 16}, {23, 14}, {23, 16}}) b.fill(c[0], 6, c[1], c[0], 8, c[1], Blocks.BIRCH_FENCE);
        b.fill(21, 9, 14, 23, 9, 16, Blocks.WHITE_WOOL);
        b.set(18, 6, 11, Blocks.BOOKSHELF);
        b.set(19, 6, 11, facing(Blocks.LOOM, Direction.SOUTH));
        b.chest(17, 6, 18, Direction.EAST, Buildings.LOOT_HOUSE);
        b.set(23, 6, 18, Blocks.POTTED_ALLIUM);
        lamp(b, 19, 9, 15);
        npc(b, "noble", 19, 6, 16);
        npc(b, "doctor", 18, 6, 13);
        // guest room
        b.bed(18, 6, 21, Blocks.WHITE_BED, Direction.SOUTH);
        b.bed(22, 6, 21, Blocks.WHITE_BED, Direction.SOUTH);
        b.barrel(20, 6, 24, Buildings.LOOT_HOUSE);
        lamp(b, 20, 9, 22);
        // study (west front) — its back wall of shelves hides a secret room
        b.fill(x1 + 1, 6, 17, 9, 8, 17, Blocks.BOOKSHELF);
        b.set(6, 6, 17, facing(Blocks.CHISELED_BOOKSHELF, Direction.NORTH));
        b.set(6, 7, 17, Blocks.BOOKSHELF);
        b.clear(6, 6, 18, 6, 7, 18);
        b.set(5, 6, 13, facing(Blocks.LECTERN, Direction.SOUTH));
        table(b, 7, 6, 13, Blocks.DARK_OAK_SLAB);
        chair(b, 7, 6, 12, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
        b.set(7, 7, 13, candles(Blocks.CANDLE, 3));
        carpet(b, 4, 6, 14, 8, 15, Blocks.BROWN_CARPET);
        lamp(b, 6, 9, 14);
        // the hidden room: a disguised captain's den
        b.clear(x1 + 1, 6, 19, 9, 8, z2 - 1);
        b.fill(x1 + 1, 5, 19, 9, 5, z2 - 1, Blocks.SPRUCE_PLANKS);
        b.chest(4, 6, 24, Direction.NORTH, Buildings.LOOT_TREASURE);
        b.set(5, 6, 24, Blocks.LECTERN);
        b.set(7, 6, 24, Blocks.SMITHING_TABLE);
        b.set(8, 7, 24, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        b.set(3, 6, 21, Blocks.BARREL);
        b.set(6, 8, 21, lantern(Blocks.SOUL_LANTERN, true));
        b.set(6, 9, 21, Blocks.SPRUCE_PLANKS);
        mark(b, "treasure", 4, 6, 24);
        // the window tree
        tree(b, 29, 1, 17, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 9, 3, 91, null);
    }

    // ============================================================== Cocoyasi: the tangerine grove

    /** A small orchard cottage among rows of tangerine trees, a grave on the hill and a stash under the rug. */
    static void tangerineGrove(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 25 x 21
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 14);
        // orchard rows inside a fence
        outline(b, 11, 1, 1, w - 1, d - 2, Blocks.OAK_FENCE);
        b.set(11, 1, 10, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST).setValue(FenceGateBlock.OPEN, true));
        for (int z = 2; z <= d - 3; z++) b.set(16, 0, z, Blocks.DIRT_PATH);
        for (int z = 2; z <= d - 3; z++) b.set(20, 0, z, Blocks.DIRT_PATH);
        b.fill(12, 0, 10, w - 2, 0, 10, Blocks.DIRT_PATH);
        int i = 0;
        for (int tx : new int[]{14, 18, 22})
            for (int tz : new int[]{4, 8, 13, 16}) {
                if (tz == 8 && tx == 18) continue;
                tree(b, tx, 1, tz, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 3, 2, 200 + i++ * 37, Blocks.ORANGE_CONCRETE);
                b.set(tx, 0, tz, Blocks.ROOTED_DIRT);
            }
        // basket corner and harvest crates
        b.set(18, 1, 8, Blocks.COMPOSTER);
        b.barrel(19, 1, 8, Buildings.LOOT_HOUSE);
        b.set(17, 1, 8, Blocks.HAY_BLOCK);
        b.set(17, 2, 8, Blocks.ORANGE_CONCRETE);
        npc(b, "farmer", 16, 1, 6);

        // ---- the cottage (door north), white walls, orange roof
        int x1 = 1, x2 = 9, z1 = 5, z2 = 12;
        room(b, x1, 0, z1, x2, z2, 4, Blocks.WHITE_TERRACOTTA, Blocks.OAK_PLANKS, Blocks.OAK_PLANKS);
        posts(b, x1, 1, z1, x2, 4, z2, Blocks.STRIPPED_OAK_LOG);
        b.gableRoof(x1 - 1, z1 - 1, x2 + 1, z2 + 1, 5, Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_PLANKS);
        b.door(8, 1, z1, Blocks.OAK_DOOR, Direction.NORTH);
        windowsX(b, 3, 5, 2, z1, 2, Blocks.GLASS_PANE);
        windowsX(b, 4, 6, 2, z2, 2, Blocks.GLASS_PANE);
        windowsZ(b, 8, 9, 2, x2, 1, Blocks.GLASS_PANE);
        b.set(7, 1, z1 - 1, Blocks.POTTED_ORANGE_TULIP);
        kitchenX(b, 2, 1, 6, Direction.SOUTH, Buildings.LOOT_HOUSE);
        table(b, 5, 1, 9, Blocks.OAK_SLAB);
        b.set(5, 2, 9, Blocks.ORANGE_CONCRETE);
        chair(b, 4, 1, 9, Blocks.OAK_STAIRS, Direction.WEST);
        chair(b, 6, 1, 9, Blocks.OAK_STAIRS, Direction.EAST);
        b.bed(2, 1, 10, Blocks.ORANGE_BED, Direction.SOUTH);
        b.bed(4, 1, 10, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.set(3, 1, 11, Blocks.SPRUCE_PLANKS);
        b.set(3, 2, 11, candles(Blocks.CANDLE, 1));
        b.set(8, 1, 11, Blocks.BOOKSHELF);
        b.set(8, 2, 11, Blocks.POTTED_CACTUS);
        lamp(b, 5, 4, 8);
        npc(b, "resident", 7, 1, 8);
        // the savings stash: a chest set into the floor under the rug
        b.chest(6, 0, 11, Direction.NORTH, Buildings.LOOT_TREASURE);
        carpet(b, 5, 1, 10, 7, 11, Blocks.CYAN_CARPET);
        mark(b, "treasure", 6, 0, 11);

        // ---- the grave on the little rise, with flowers
        b.fill(3, 1, 15, 7, 1, 19, Blocks.GRASS_BLOCK);
        b.set(5, 2, 17, Blocks.OAK_FENCE);
        b.set(5, 3, 17, connect(Blocks.OAK_FENCE, false, true, false, true));
        b.set(4, 3, 17, connect(Blocks.OAK_FENCE, false, true, false, false));
        b.set(6, 3, 17, connect(Blocks.OAK_FENCE, false, false, false, true));
        b.set(5, 4, 17, Blocks.OAK_FENCE);
        b.set(4, 2, 16, Blocks.POPPY);
        b.set(6, 2, 16, Blocks.OXEYE_DAISY);
        b.set(5, 2, 16, candles(Blocks.ORANGE_CANDLE, 2));
        b.set(3, 2, 18, Blocks.CORNFLOWER);
        b.set(7, 2, 18, Blocks.AZURE_BLUET);
        npc(b, "child", 5, 2, 15);
        b.marker("legend", 5, 2, 17, tag("story", "tangerine_grove"));
    }

    // ============================================================== Flevance: the White City

    private static final Block[] WHITE = {Blocks.WHITE_CONCRETE, Blocks.CALCITE, Blocks.QUARTZ_BRICKS, Blocks.SMOOTH_QUARTZ, Blocks.WHITE_TERRACOTTA};

    private static Block white(Builder b, int salt) {
        return WHITE[b.rand(salt, WHITE.length)];
    }

    /** Abandoned white house: shell with broken roof and scorched patches; door north. */
    private static void whiteHouse(Builder b, int x1, int z1, int x2, int z2, int salt, boolean burnt) {
        b.fill(x1, 0, z1, x2, 0, z2, Blocks.SMOOTH_QUARTZ);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                for (int y = 1; y <= 4; y++) {
                    if (!edge) {
                        b.set(x, y, z, Blocks.AIR);
                        continue;
                    }
                    int r = b.rand(salt + x * 31 + y * 7 + z * 13, 14);
                    if (y >= 3 && r == 0) continue; // collapsed
                    b.set(x, y, z, burnt && r < 3 ? Blocks.BLACKSTONE : white(b, salt + x + z * 3 + y));
                }
                if (b.rand(salt + x * 3 + z * 5, 4) != 0) b.set(x, 5, z, Blocks.QUARTZ_SLAB);
            }
        int dx = (x1 + x2) / 2;
        b.clear(dx, 1, z1, dx, 3, z1);
        b.set(dx - 2, 2, z1, connected(Blocks.WHITE_STAINED_GLASS_PANE, Direction.Axis.X));
        b.set(dx + 2, 2, z1, connected(Blocks.WHITE_STAINED_GLASS_PANE, Direction.Axis.X));
        b.bed(x1 + 1, 1, z2 - 2, Blocks.WHITE_BED, Direction.SOUTH);
        b.set(x2 - 1, 1, z2 - 1, Blocks.COBWEB);
        b.set(x2 - 1, 1, z1 + 1, Blocks.FLOWER_POT);
    }

    /** The white town of Flevance, abandoned: square with a dry fountain, church, hospital, houses and the lead mine. */
    static void whiteCity(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 47 x 47
        site(b, Blocks.SNOW_BLOCK, Blocks.STONE, 30);
        patchGround(b, 13, 0, Blocks.SNOW_BLOCK, Blocks.CALCITE, Blocks.WHITE_CONCRETE, Blocks.SMOOTH_QUARTZ);
        // streets
        b.fill(21, 0, 0, 25, 0, d - 1, Blocks.QUARTZ_BRICKS);
        b.fill(0, 0, 21, w - 1, 0, 25, Blocks.QUARTZ_BRICKS);
        // quarantine barricade along the north edge
        for (int x = 0; x < w; x++) {
            if (x >= 20 && x <= 26) continue;
            if (x % 4 == 0) b.fill(x, 1, 0, x, 2, 0, Blocks.SPRUCE_FENCE);
            else b.set(x, 1, 0, connected(Blocks.IRON_BARS, Direction.Axis.X));
        }
        b.set(20, 1, 0, Blocks.SPRUCE_FENCE);
        b.set(26, 1, 0, Blocks.SPRUCE_FENCE);
        for (int x = 21; x <= 25; x += 2) b.set(x, 1, 1, Blocks.SPRUCE_FENCE);
        b.set(23, 2, 1, Blocks.SKELETON_SKULL);

        // ---- square: dry fountain and a white statue
        int c = 23;
        ring(b, c, c, 1, 4, Blocks.QUARTZ_BRICKS.defaultBlockState());
        disc(b, c, c, 0, 3.5, Blocks.CALCITE.defaultBlockState());
        b.fill(c, 1, c, c, 4, c, Blocks.QUARTZ_PILLAR);
        b.set(c, 5, c, Blocks.CHISELED_QUARTZ_BLOCK);
        for (int[] o : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) lampPost(b, c + o[0], 1, c + o[1], Blocks.BIRCH_FENCE, 3, Blocks.SOUL_LANTERN);
        npc(b, "bandit", c + 2, 1, c - 5);

        // ---- white trees along the avenue (birch with white canopies)
        for (int[] t : new int[][]{{20, 6}, {20, 13}, {26, 6}}) tree(b, t[0], 1, t[1], Blocks.BIRCH_LOG, Blocks.WHITE_WOOL, 5, 2, t[0] * 7 + t[1], null);

        // ---- the hospital (west, z 3..17): wards, an office with the doctor's records
        int hx1 = 2, hx2 = 17, hz1 = 3, hz2 = 17;
        b.fill(hx1, 0, hz1, hx2, 0, hz2, Blocks.SMOOTH_QUARTZ);
        b.walls(hx1, 1, hz1, hx2, 5, hz2, Blocks.QUARTZ_BRICKS);
        b.clear(hx1 + 1, 1, hz1 + 1, hx2 - 1, 5, hz2 - 1);
        b.fill(hx1, 6, hz1, hx2, 6, hz2, Blocks.SMOOTH_QUARTZ);
        for (int x = hx1; x <= hx2; x++) if (b.rand(x * 19, 5) == 0) b.set(x, 6, hz1 + 2 + b.rand(x, 10), Blocks.AIR);
        windowsX(b, hx1 + 2, hx2 - 2, 2, hz1, 3, Blocks.WHITE_STAINED_GLASS_PANE);
        windowsZ(b, hz1 + 2, hz2 - 2, 2, hx1, 3, Blocks.WHITE_STAINED_GLASS_PANE);
        b.bigDoor(hx2, 1, 10, Blocks.BIRCH_DOOR, Direction.EAST);
        b.set(hx2, 4, 10, Blocks.RED_CONCRETE);
        b.set(hx2 + 1, 4, 9, Blocks.RED_CONCRETE);
        b.set(hx2 + 1, 4, 11, Blocks.RED_CONCRETE);
        b.set(hx2 + 1, 5, 10, Blocks.RED_CONCRETE);
        b.set(hx2 + 1, 3, 10, Blocks.RED_CONCRETE);
        for (int z = hz1 + 2; z <= hz2 - 2; z += 2) {
            b.bed(hx1 + 2, 1, z, Blocks.WHITE_BED, Direction.WEST);
            if (b.rand(z * 3, 3) == 0) b.set(hx1 + 3, 1, z, Blocks.COBWEB);
        }
        for (int z = hz1 + 2; z <= 9; z += 2) b.bed(9, 1, z, Blocks.WHITE_BED, Direction.EAST);
        b.set(7, 1, 15, Blocks.BREWING_STAND);
        b.set(8, 1, 15, Blocks.CAULDRON);
        lamp(b, 6, 5, 8);
        lamp(b, 12, 5, 12);
        // office in the south-east corner of the hospital
        b.fill(11, 1, 12, 16, 4, 12, Blocks.QUARTZ_BRICKS);
        b.door(13, 1, 12, Blocks.BIRCH_DOOR, Direction.NORTH);
        b.set(15, 1, 16, facing(Blocks.LECTERN, Direction.NORTH));
        b.fill(11, 1, 16, 12, 2, 16, Blocks.BOOKSHELF);
        b.chest(16, 1, 14, Direction.WEST, Buildings.LOOT_RUIN);
        table(b, 14, 1, 14, Blocks.BIRCH_SLAB);
        b.set(14, 2, 14, candles(Blocks.WHITE_CANDLE, 2));
        npc(b, "scholar", 13, 1, 15);

        // ---- the church (north-east) with a bell tower
        int cx1 = 29, cx2 = 43, cz1 = 3, cz2 = 18;
        b.fill(cx1, 0, cz1, cx2, 0, cz2, Blocks.POLISHED_DIORITE);
        b.walls(cx1, 1, cz1, cx2, 7, cz2, Blocks.CALCITE);
        b.clear(cx1 + 1, 1, cz1 + 1, cx2 - 1, 7, cz2 - 1);
        b.gableRoof(cx1 - 1, cz1 - 1, cx2 + 1, cz2 + 1, 8, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.CALCITE);
        windowsZ(b, cz1 + 3, cz2 - 2, 3, cx1, 3, Blocks.WHITE_STAINED_GLASS_PANE);
        windowsZ(b, cz1 + 3, cz2 - 2, 3, cx2, 3, Blocks.WHITE_STAINED_GLASS_PANE);
        b.bigDoor(36, 1, cz1, Blocks.BIRCH_DOOR, Direction.NORTH);
        for (int z = cz1 + 5; z <= cz2 - 5; z += 2) {
            for (int x = cx1 + 2; x <= 34; x++) chair(b, x, 1, z, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
            for (int x = 38; x <= cx2 - 2; x++) chair(b, x, 1, z, Blocks.QUARTZ_STAIRS, Direction.SOUTH);
        }
        b.fill(34, 1, cz2 - 2, 38, 1, cz2 - 1, Blocks.QUARTZ_BLOCK);
        b.set(36, 2, cz2 - 2, candles(Blocks.WHITE_CANDLE, 4));
        b.set(35, 2, cz2 - 1, candles(Blocks.WHITE_CANDLE, 2));
        b.set(37, 2, cz2 - 1, candles(Blocks.WHITE_CANDLE, 2));
        lamp(b, 36, 7, 10);
        // bell tower at the front-west corner
        b.fill(cx1, 1, cz1, cx1 + 3, 15, cz1 + 3, Blocks.CALCITE);
        b.clear(cx1 + 1, 1, cz1 + 1, cx1 + 2, 14, cz1 + 2);
        b.clear(cx1, 12, cz1 + 1, cx1 + 3, 13, cz1 + 2);
        b.clear(cx1 + 1, 12, cz1, cx1 + 2, 13, cz1 + 3);
        b.set(cx1 + 1, 14, cz1 + 1, bellCeiling());
        hipRoof(b, cx1 - 1, cz1 - 1, cx1 + 4, cz1 + 4, 16, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_BLOCK);
        b.set(cx1 + 3, 1, cz1 + 2, Blocks.AIR);
        b.set(cx1 + 3, 2, cz1 + 2, Blocks.AIR);
        b.ladderUp(cx1 + 2, 1, cz1 + 1, 13, Direction.SOUTH);
        npc(b, "bandit", 36, 1, 9);

        // ---- houses south of the square
        whiteHouse(b, 3, 29, 10, 36, 301, true);
        whiteHouse(b, 12, 29, 19, 36, 302, false);
        whiteHouse(b, 3, 38, 10, 45, 303, false);
        whiteHouse(b, 12, 38, 19, 45, 304, true);
        b.barrel(17, 1, 34, Buildings.LOOT_HOUSE);
        b.barrel(4, 1, 44, Buildings.LOOT_HOUSE);
        // graves of the town in the south-middle
        for (int gx = 27; gx <= 31; gx += 2)
            for (int gz = 29; gz <= 37; gz += 4) {
                b.set(gx, 1, gz, Blocks.CALCITE);
                b.set(gx, 2, gz, Blocks.DIORITE_WALL);
                b.set(gx, 1, gz + 1, Blocks.SNOW.defaultBlockState());
            }
        b.set(29, 1, 41, Blocks.LILY_OF_THE_VALLEY);

        // ---- the white lead mine (south-east): a white hill with an adit stepping down to a gallery
        heap(b, 401, 39, 38, 1, 7, 7, Blocks.CALCITE, Blocks.DIORITE, Blocks.CALCITE, Blocks.SNOW_BLOCK);
        b.fill(37, 1, 31, 41, 4, 33, Blocks.CALCITE);
        b.clear(38, 1, 31, 40, 3, 37);
        b.fill(37, 1, 31, 37, 3, 31, Blocks.STRIPPED_BIRCH_LOG);
        b.fill(41, 1, 31, 41, 3, 31, Blocks.STRIPPED_BIRCH_LOG);
        b.fill(37, 4, 31, 41, 4, 31, axis(Blocks.STRIPPED_BIRCH_LOG, Direction.Axis.X));
        b.set(39, 3, 32, lantern(Blocks.LANTERN, true));
        // gallery
        shell(b, 30, -7, 42, 44, -2, 46, Blocks.CALCITE);
        // stairs down to the gallery (y = -6)
        for (int i = 0; i < 6; i++) {
            b.fill(38, -i, 37 + i, 40, -i, 37 + i, stair(Blocks.POLISHED_DIORITE_STAIRS, Direction.NORTH, Half.BOTTOM));
            b.clear(38, 1 - i, 37 + i, 40, 3 - i, 37 + i);
            b.fill(37, -i - 1, 37 + i, 41, -i - 1, 37 + i, Blocks.CALCITE);
            b.fill(37, -i, 37 + i, 37, 3 - i, 37 + i, Blocks.CALCITE);
            b.fill(41, -i, 37 + i, 41, 3 - i, 37 + i, Blocks.CALCITE);
            b.fill(38, 4 - i, 37 + i, 40, 4 - i, 37 + i, Blocks.CALCITE);
        }
        b.fill(31, -7, 44, 43, -7, 44, Blocks.SMOOTH_STONE);
        b.fill(31, -6, 44, 43, -6, 44, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
        for (int x = 32; x <= 42; x += 2) {
            b.set(x, -6, 43, Blocks.WHITE_GLAZED_TERRACOTTA);
            b.set(x, -4, 45, Blocks.DIORITE);
        }
        lamp(b, 34, -3, 44);
        lamp(b, 40, -3, 44);
        b.chest(42, -6, 45, Direction.WEST, Buildings.LOOT_RUIN);
        npc(b, "ruin_guardian", 35, -6, 45);
        // a sealed side gallery behind a cracked wall: the last records of the town
        shell(b, 26, -7, 42, 30, -3, 46, Blocks.CALCITE);
        b.set(30, -5, 44, Blocks.CRACKED_STONE_BRICKS);
        b.set(30, -6, 44, Blocks.CRACKED_STONE_BRICKS);
        b.chest(27, -6, 44, Direction.EAST, Buildings.LOOT_TREASURE);
        b.set(27, -6, 43, facing(Blocks.LECTERN, Direction.EAST));
        b.set(28, -4, 45, lantern(Blocks.SOUL_LANTERN, true));
        b.set(28, -3, 45, Blocks.CALCITE);
        mark(b, "treasure", 27, -6, 44);
        b.marker("legend", c, 1, c - 3, tag("story", "white_city"));
    }

    // ============================================================== Lvneel: the Explorers' Monument

    /** Plaza with a bronze explorer statue on a plinth (hollow, with a hidden vault) and a small museum hall. */
    static void explorersMonument(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 27 x 27
        site(b, Blocks.POLISHED_ANDESITE, Blocks.STONE, 26);
        int c = 13;
        // compass-rose plaza
        disc(b, c, 8, 0, 7.5, Blocks.STONE_BRICKS.defaultBlockState());
        ring(b, c, 8, 0, 6, Blocks.POLISHED_GRANITE.defaultBlockState());
        for (int i = -7; i <= 7; i++) {
            b.set(c + i, 0, 8, Blocks.CHISELED_STONE_BRICKS);
            b.set(c, 0, 8 + i, Blocks.CHISELED_STONE_BRICKS);
        }
        b.set(c, 0, 1, Blocks.GOLD_BLOCK);
        // plinth (5x5, 5 high) with a plaque, and the statue above
        b.fill(c - 2, 1, 6, c + 2, 4, 10, Blocks.POLISHED_DEEPSLATE);
        b.fill(c - 2, 5, 6, c + 2, 5, 10, Blocks.DEEPSLATE_TILE_SLAB);
        b.set(c, 1, 5, facing(Blocks.LECTERN, Direction.NORTH));
        BlockState bronze = Blocks.OXIDIZED_CUT_COPPER.defaultBlockState();
        b.fill(c - 1, 6, 8, c - 1, 8, 8, Blocks.OXIDIZED_COPPER);
        b.fill(c + 1, 6, 8, c + 1, 8, 8, Blocks.OXIDIZED_COPPER);
        b.fill(c - 1, 9, 8, c + 1, 12, 8, bronze);
        b.fill(c - 1, 9, 9, c + 1, 11, 9, Blocks.OXIDIZED_COPPER); // coat
        b.set(c, 13, 8, Blocks.OXIDIZED_COPPER);
        b.set(c, 14, 8, Blocks.OXIDIZED_CUT_COPPER);
        b.set(c, 15, 8, Blocks.OXIDIZED_CUT_COPPER_SLAB);
        b.set(c, 13, 7, stair(Blocks.OXIDIZED_CUT_COPPER_STAIRS, Direction.SOUTH, Half.TOP)); // beard
        b.fill(c + 2, 12, 8, c + 3, 12, 8, bronze); // pointing arm with a spyglass
        b.set(c + 4, 12, 8, facing(Blocks.LIGHTNING_ROD, Direction.EAST));
        b.set(c - 2, 11, 8, Blocks.OXIDIZED_CUT_COPPER);
        b.set(c - 2, 10, 8, Blocks.OXIDIZED_CUT_COPPER);
        for (int[] o : new int[][]{{-6, 2}, {6, 2}, {-6, 14}, {6, 14}}) lampPost(b, c + o[0], 1, o[1], Blocks.DARK_OAK_FENCE, 3, Blocks.LANTERN);
        for (int x : new int[]{4, 22}) {
            for (int z = 4; z <= 12; z += 4) chair(b, x, 1, z, Blocks.SPRUCE_STAIRS, x < c ? Direction.WEST : Direction.EAST);
        }
        for (int x = 1; x <= 4; x++) b.set(x, 1, 1, Blocks.BLUE_ORCHID);
        for (int x = 22; x <= 25; x++) b.set(x, 1, 1, Blocks.BLUE_ORCHID);
        npc(b, "child", 9, 1, 3);
        npc(b, "resident", 18, 1, 12);
        // the hidden vault under the plinth: a loose chiseled block on the plinth's back hides the way down
        b.clear(c - 1, 1, 7, c + 1, 4, 9);
        b.set(c, 1, 10, Blocks.CHISELED_DEEPSLATE);
        b.set(c, 2, 10, Blocks.CHISELED_DEEPSLATE);
        shell(b, c - 3, -6, 5, c + 3, 0, 11, Blocks.DEEPSLATE_BRICKS);
        b.fill(c - 1, 0, 7, c + 1, 0, 9, Blocks.POLISHED_DEEPSLATE);
        b.clear(c, 0, 9, c, 0, 9);
        b.ladderUp(c, -5, 9, 6, Direction.NORTH);
        b.fill(c, -5, 10, c, 0, 10, Blocks.DEEPSLATE_BRICKS);
        b.set(c - 2, -5, 6, Blocks.GOLD_BLOCK);
        b.set(c - 2, -4, 6, facing(Blocks.BELL, Direction.SOUTH));
        b.chest(c + 2, -5, 6, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.set(c, -2, 7, lantern(Blocks.LANTERN, true));
        b.set(c, -1, 7, Blocks.DEEPSLATE_BRICKS);
        mark(b, "treasure", c + 2, -5, 6);

        // ---- the Explorers' Hall (museum) along the back
        int x1 = 3, x2 = 23, z1 = 16, z2 = 25;
        room(b, x1, 0, z1, x2, z2, 6, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.SPRUCE_PLANKS);
        posts(b, x1, 1, z1, x2, 6, z2, Blocks.STRIPPED_SPRUCE_LOG);
        for (int x = x1 + 3; x <= x2 - 3; x += 4) b.fill(x, 1, z1, x, 6, z1, Blocks.STRIPPED_SPRUCE_LOG);
        windowsX(b, x1 + 2, x2 - 2, 3, z1, 4, Blocks.GLASS_PANE);
        windowsZ(b, z1 + 2, z2 - 2, 3, x1, 3, Blocks.GLASS_PANE);
        windowsZ(b, z1 + 2, z2 - 2, 3, x2, 3, Blocks.GLASS_PANE);
        hipRoof(b, x1 - 1, z1 - 1, x2 + 1, z2 + 1, 8, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS);
        b.bigDoor(c, 1, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.set(c - 1, 4, z1 - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        b.set(c + 1, 4, z1 - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        carpet(b, c - 1, 1, z1 + 1, c + 1, z2 - 1, Blocks.BLUE_CARPET);
        // display cases: pedestal + glass with a relic inside
        int[][] cases = {{5, 19}, {9, 19}, {17, 19}, {21, 19}, {5, 23}, {21, 23}};
        Block[] relics = {Blocks.GOLD_BLOCK, Blocks.SKELETON_SKULL, Blocks.BELL, Blocks.DECORATED_POT, Blocks.RAW_GOLD_BLOCK, Blocks.AMETHYST_CLUSTER};
        for (int i = 0; i < cases.length; i++) {
            int px = cases[i][0], pz = cases[i][1];
            b.set(px, 1, pz, Blocks.POLISHED_BLACKSTONE);
            b.set(px, 2, pz, relics[i]);
            b.set(px, 3, pz, Blocks.GLASS);
        }
        // model ship and a wall of logbooks
        b.fill(8, 1, 24, 18, 1, 24, Blocks.BOOKSHELF);
        b.fill(8, 2, 24, 18, 2, 24, Blocks.BOOKSHELF);
        b.set(c, 3, 24, Blocks.BOOKSHELF);
        b.set(9, 1, 22, Blocks.CARTOGRAPHY_TABLE);
        b.set(17, 1, 22, facing(Blocks.LECTERN, Direction.NORTH));
        b.fill(11, 1, 22, 15, 1, 22, Blocks.SPRUCE_PLANKS);
        b.set(11, 2, 22, stair(Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM));
        b.set(15, 2, 22, stair(Blocks.SPRUCE_STAIRS, Direction.WEST, Half.BOTTOM));
        b.fill(12, 2, 22, 14, 2, 22, Blocks.SPRUCE_SLAB);
        b.set(c, 3, 22, Blocks.SPRUCE_FENCE);
        b.set(c, 4, 22, Blocks.WHITE_WOOL);
        b.set(c, 5, 22, Blocks.WHITE_WOOL);
        lamp(b, 7, 6, 20);
        lamp(b, 19, 6, 20);
        lamp(b, c, 6, 20);
        b.chest(x2 - 1, 1, z2 - 1, Direction.WEST, Buildings.LOOT_SHOP);
        npc(b, "scholar", 16, 1, 21);
        npc(b, "guard", 11, 1, 18);
        b.marker("legend", c, 1, 4, tag("story", "explorers_monument"));
    }

    // ============================================================== Spider Miles: the junkyard fort

    private static final Block[] SCRAP = {Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_CUT_COPPER, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF,
            Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SMOOTH_BASALT, Blocks.IRON_BLOCK, Blocks.MOSSY_COBBLESTONE, Blocks.BARREL, Blocks.WAXED_EXPOSED_CUT_COPPER};

    /** A pirate fort cobbled from junk: scrap walls, an overturned ship hull as the great hall, a crow's nest and a smithy. */
    static void junkyardFort(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 37 x 37
        site(b, Blocks.GRAVEL, Blocks.COBBLESTONE, 22);
        patchGround(b, 17, 0, Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.TUFF, Blocks.COBBLED_DEEPSLATE);
        // scrap curtain wall (2 thick), ragged top, gate on the north side
        for (int x = 0; x < w; x++)
            for (int z = 0; z < d; z++) {
                boolean edge = x <= 1 || z <= 1 || x >= w - 2 || z >= d - 2;
                if (!edge) continue;
                int h = 4 + b.rand(x * 37 + z * 11, 3);
                for (int y = 1; y <= h; y++) b.set(x, y, z, SCRAP[b.rand(x * 7 + y * 131 + z * 17, SCRAP.length)]);
                if ((x + z) % 5 == 0) b.set(x, h + 1, z, connected(Blocks.IRON_BARS, x <= 1 || x >= w - 2 ? Direction.Axis.Z : Direction.Axis.X));
            }
        b.clear(16, 1, 0, 20, 4, 1);
        b.fill(15, 5, 0, 21, 5, 1, Blocks.DARK_OAK_PLANKS);
        b.set(16, 4, 0, Blocks.CHAIN);
        b.set(20, 4, 0, Blocks.CHAIN);
        b.set(18, 6, 0, Blocks.DAMAGED_ANVIL);
        b.set(18, 6, 1, Blocks.SKELETON_SKULL);
        npc(b, "pirate", 17, 1, 3);
        npc(b, "pirate", 21, 1, 4);

        // ---- the great hall: an overturned hull (axis along x)
        int hx = 18, hz = 22;
        ellipsoid(b, hx, 0, hz, 10.5, 8.5, 6.5, Blocks.DARK_OAK_PLANKS.defaultBlockState(), true, 1);
        for (int x = hx - 9; x <= hx + 9; x += 3) {
            for (int z = hz - 6; z <= hz + 6; z++) {
                double v = 1 - ((x - hx) * (x - hx)) / (10.5 * 10.5) - ((z - hz) * (z - hz)) / (6.5 * 6.5);
                if (v <= 0) continue;
                int top = (int) Math.floor(8.5 * Math.sqrt(v));
                if (top >= 1) b.set(x, top, z, Blocks.STRIPPED_SPRUCE_LOG);
            }
        }
        b.fill(hx - 9, 9, hz, hx + 9, 9, hz, axis(Blocks.SPRUCE_LOG, Direction.Axis.X));
        b.fill(hx - 10, 0, hz - 6, hx + 10, 0, hz + 6, Blocks.SPRUCE_PLANKS);
        // entrance cut through the bow (west)
        b.clear(hx - 11, 1, hz - 1, hx - 8, 3, hz + 1);
        b.fill(hx - 9, 1, hz - 1, hx - 9, 3, hz + 1, Blocks.DARK_OAK_PLANKS);
        b.door(hx - 9, 1, hz, Blocks.SPRUCE_DOOR, Direction.WEST);
        // mess hall
        longTableX(b, hx - 6, hx + 2, 1, hz, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        for (int x : new int[]{hx - 4, hx, hx + 3}) {
            b.set(x, 6, hz, Blocks.DARK_OAK_PLANKS);
            b.hanging(x, 5, hz, Blocks.LANTERN);
        }
        b.barrel(hx - 7, 1, hz - 3, Buildings.LOOT_TAVERN);
        b.barrel(hx - 6, 1, hz - 3, Buildings.LOOT_TAVERN);
        b.barrel(hx - 7, 1, hz + 3, Buildings.LOOT_WAREHOUSE);
        b.set(hx - 3, 1, hz + 4, Blocks.SMOKER.defaultBlockState().setValue(SmokerBlock.FACING, Direction.NORTH));
        b.set(hx - 2, 1, hz + 4, Blocks.CAMPFIRE);
        npc(b, "pirate", hx - 4, 1, hz - 2);
        npc(b, "pirate", hx, 1, hz + 2);
        // captain's cabin at the stern (east), partitioned inside the hull only
        for (int y = 1; y <= 7; y++)
            for (int z = hz - 6; z <= hz + 6; z++) {
                double v = 25 / 88.36 + (y * y) / 54.76 + ((z - hz) * (z - hz)) / 29.16;
                if (v < 1.0) b.set(hx + 5, y, z, Blocks.SPRUCE_PLANKS);
            }
        b.door(hx + 5, 1, hz, Blocks.SPRUCE_DOOR, Direction.WEST);
        carpet(b, hx + 6, 1, hz - 2, hx + 8, hz + 2, Blocks.RED_CARPET);
        b.bed(hx + 6, 1, hz + 2, Blocks.BLACK_BED, Direction.SOUTH);
        b.set(hx + 8, 1, hz - 1, facing(Blocks.LECTERN, Direction.WEST));
        b.chest(hx + 6, 1, hz - 3, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.set(hx + 7, 4, hz, Blocks.DARK_OAK_PLANKS);
        b.hanging(hx + 7, 3, hz, Blocks.LANTERN);
        npc(b, "pirate_captain", hx + 7, 1, hz);

        // ---- crow's nest from a salvaged mast
        b.fill(5, 1, 6, 5, 18, 6, Blocks.SPRUCE_LOG);
        disc(b, 5, 6, 14, 2.3, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = 3; x <= 7; x++)
            for (int z = 4; z <= 8; z++) {
                double dd = Math.sqrt((x - 5) * (x - 5) + (z - 6) * (z - 6));
                if (dd > 1.6 && dd <= 2.8) b.set(x, 15, z, Blocks.SPRUCE_FENCE);
            }
        b.set(5, 15, 6, Blocks.SPRUCE_LOG);
        b.clear(5, 14, 7, 5, 14, 7);
        b.clear(5, 15, 8, 5, 15, 8);
        b.ladderUp(5, 1, 7, 14, Direction.SOUTH);
        b.set(6, 18, 6, banner(Blocks.BLACK_WALL_BANNER, Direction.EAST));
        npc(b, "lookout", 6, 15, 5);

        // ---- scrap smithy (open shed, east)
        for (int[] p : new int[][]{{26, 4}, {33, 4}, {26, 10}, {33, 10}}) b.fill(p[0], 1, p[1], p[0], 4, p[1], Blocks.SPRUCE_LOG);
        b.fill(25, 5, 3, 34, 5, 11, Blocks.WEATHERED_CUT_COPPER_SLAB);
        b.fill(27, 0, 5, 32, 0, 9, Blocks.SMOOTH_BASALT);
        b.set(28, 1, 9, Blocks.ANVIL);
        b.set(29, 1, 9, Blocks.SMITHING_TABLE);
        b.set(31, 1, 9, facing(Blocks.BLAST_FURNACE, Direction.NORTH));
        b.set(32, 1, 9, Blocks.GRINDSTONE);
        b.set(32, 1, 6, Blocks.LAVA_CAULDRON);
        b.chest(27, 1, 6, Direction.EAST, Buildings.LOOT_SMITH);
        b.set(29, 4, 7, lantern(Blocks.LANTERN, true));
        b.set(29, 5, 7, Blocks.WEATHERED_CUT_COPPER);
        npc(b, "pirate", 30, 1, 7);

        // ---- junk heaps and a prisoner cage
        heap(b, 501, 9, 30, 1, 3, 3, Blocks.GRAVEL, Blocks.EXPOSED_COPPER, Blocks.TUFF, Blocks.DARK_OAK_PLANKS, Blocks.CHAIN);
        heap(b, 502, 30, 30, 1, 3, 3, Blocks.GRAVEL, Blocks.WEATHERED_COPPER, Blocks.COBBLED_DEEPSLATE, Blocks.BARREL);
        heap(b, 503, 10, 9, 1, 2, 2, Blocks.GRAVEL, Blocks.IRON_BARS, Blocks.SPRUCE_PLANKS);
        outline(b, 25, 1, 14, 29, 18, Blocks.IRON_BARS);
        outline(b, 25, 2, 14, 29, 18, Blocks.IRON_BARS);
        b.fill(25, 3, 14, 29, 3, 18, Blocks.IRON_BLOCK);
        mark(b, "cell", 27, 1, 16);
        // buried stash beneath the south-east heap (a skull marks it)
        b.set(30, 4, 30, Blocks.SKELETON_SKULL);
        b.fill(29, -3, 29, 31, -1, 31, Blocks.GRAVEL);
        b.chest(30, -2, 30, Direction.NORTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 30, -2, 30);
        b.marker("arc_anchor", 18, 1, 8, tag("arc", "junkyard_family"));
    }

    // ============================================================== Ilisia: the royal castle

    /** A white royal castle: curtain wall with round towers, gatehouse, flower courtyard and a three-storey keep. */
    static void royalCastle(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 51 x 51
        Block stone = Blocks.QUARTZ_BRICKS, trim = Blocks.SMOOTH_QUARTZ, roof = Blocks.LIGHT_BLUE_TERRACOTTA;
        site(b, Blocks.GRASS_BLOCK, Blocks.STONE, 44);
        // ---- curtain wall (y 1..9), 2 thick, walk on top
        for (int y = 1; y <= 9; y++) {
            b.walls(2, y, 2, w - 3, y, d - 3, stone);
            b.walls(3, y, 3, w - 4, y, d - 4, stone);
        }
        crenelX(b, 2, w - 3, 10, 2, stone);
        crenelX(b, 2, w - 3, 10, d - 3, stone);
        crenelZ(b, 2, d - 3, 10, 2, stone);
        crenelZ(b, 2, d - 3, 10, w - 3, stone);
        // corner towers
        for (int[] t : new int[][]{{5, 5}, {w - 6, 5}, {5, d - 6}, {w - 6, d - 6}}) {
            int tx = t[0], tz = t[1];
            int sx = tx < w / 2 ? 1 : -1, sz = tz < d / 2 ? 1 : -1; // toward the courtyard
            b.cylinder(tx, tz, 1, 15, 4, stone, true);
            b.cylinder(tx, tz, 1, 15, 3, Blocks.AIR, false);
            b.cylinder(tx, tz, 0, 0, 3, Blocks.SMOOTH_QUARTZ, false);
            b.cylinder(tx, tz, 9, 9, 3, Blocks.SPRUCE_PLANKS, false);
            cone(b, tx, tz, 16, 8, 5, 0.5, roof.defaultBlockState(), false);
            b.set(tx, 24, tz, Blocks.GOLD_BLOCK);
            b.set(tx - 4 * sx, 12, tz, connected(Blocks.GLASS_PANE, Direction.Axis.Z));
            b.set(tx, 12, tz - 4 * sz, connected(Blocks.GLASS_PANE, Direction.Axis.X));
            // doors from the courtyard, openings onto both wall walks, a ladder up the outer side
            b.clear(tx + 4 * sx, 1, tz, tx + 4 * sx, 3, tz);
            b.clear(tx, 1, tz + 4 * sz, tx, 3, tz + 4 * sz);
            for (int x = tx - 4; x <= tx + 4; x++)
                for (int z = tz - 4; z <= tz + 4; z++) {
                    boolean curtain = x == 2 || x == 3 || x == w - 4 || x == w - 3 || z == 2 || z == 3 || z == d - 4 || z == d - 3;
                    double dd = Math.sqrt((x - tx) * (x - tx) + (z - tz) * (z - tz));
                    if (curtain && dd > 3.4) b.clear(x, 10, z, x, 11, z);
                }
            b.ladderUp(tx, 1, tz - 3 * sz, 9, sz > 0 ? Direction.SOUTH : Direction.NORTH);
            b.hanging(tx, 8, tz + sz, Blocks.LANTERN);
            b.set(tx, 15, tz, Blocks.SPRUCE_PLANKS);
            b.hanging(tx, 14, tz, Blocks.LANTERN);
        }
        // ---- gatehouse (north, x 19..31)
        b.fill(19, 1, 0, 31, 13, 6, stone);
        b.clear(20, 8, 1, 30, 12, 5);
        b.fill(20, 7, 1, 30, 7, 5, trim);
        archX(b, 25, 2, 1, 0, 6, 3);
        line(b, 24, 6, 0, 26, 0, Blocks.IRON_BARS);
        crenelX(b, 19, 31, 14, 0, stone);
        crenelX(b, 19, 31, 14, 6, stone);
        b.fill(23, 0, 0, 27, 0, 6, Blocks.POLISHED_ANDESITE);
        for (int x : new int[]{21, 29}) b.set(x, 9, 7, banner(Blocks.LIGHT_BLUE_WALL_BANNER, Direction.SOUTH));
        npc(b, "guard", 23, 1, 7);
        npc(b, "guard", 27, 1, 7);
        // gatehouse upper guardroom (y 8..12) reached by a ladder from the courtyard side
        b.ladderUp(21, 1, 7, 8, Direction.SOUTH);
        b.clear(21, 8, 6, 21, 9, 6);
        b.bed(29, 8, 2, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.chest(28, 8, 5, Direction.NORTH, Buildings.LOOT_MARINE);
        table(b, 24, 8, 3, Blocks.SPRUCE_SLAB);
        b.set(24, 9, 3, candles(Blocks.CANDLE, 2));
        lamp(b, 25, 12, 3);
        npc(b, "guard", 25, 8, 4);

        // ---- courtyard: flower beds, a fountain, paths
        b.fill(24, 0, 7, 26, 0, 21, Blocks.SMOOTH_STONE);
        for (int x = 10; x <= 40; x++)
            for (int z = 10; z <= 20; z++) {
                if (x >= 23 && x <= 27) continue;
                if ((x + z) % 7 == 0) b.set(x, 1, z, (x / 3) % 3 == 0 ? Blocks.PINK_TULIP : (x / 3) % 3 == 1 ? Blocks.WHITE_TULIP : Blocks.LILY_OF_THE_VALLEY);
            }
        for (int x = 10; x <= 40; x += 6) for (int z : new int[]{9, 20}) if (x < 22 || x > 28) b.set(x, 1, z, leaves(Blocks.FLOWERING_AZALEA_LEAVES));
        ring(b, 25, 14, 1, 3, Blocks.QUARTZ_BRICKS.defaultBlockState());
        disc(b, 25, 14, 0, 2.5, Blocks.WATER.defaultBlockState());
        b.fill(25, 0, 14, 25, 3, 14, Blocks.QUARTZ_PILLAR);
        b.set(25, 4, 14, Blocks.WATER);
        npc(b, "resident", 15, 1, 14);
        npc(b, "child", 34, 1, 13);

        // ---- the keep (x 9..41, z 22..45): three storeys (floors y 0, 7, 13), roof at 19
        int kx1 = 9, kx2 = 41, kz1 = 22, kz2 = 45;
        int[] fy = {0, 7, 13};
        for (int f = 0; f < 3; f++) {
            int y0 = fy[f], h = f == 0 ? 6 : 5;
            b.fill(kx1, y0, kz1, kx2, y0, kz2, f == 0 ? Blocks.POLISHED_DIORITE : Blocks.SPRUCE_PLANKS);
            b.walls(kx1, y0 + 1, kz1, kx2, y0 + h, kz2, stone);
            b.clear(kx1 + 1, y0 + 1, kz1 + 1, kx2 - 1, y0 + h, kz2 - 1);
            windowsX(b, kx1 + 2, kx2 - 2, y0 + 2, kz1, 4, Blocks.GLASS_PANE);
            windowsX(b, kx1 + 2, kx2 - 2, y0 + 2, kz2, 4, Blocks.GLASS_PANE);
            windowsZ(b, kz1 + 2, kz2 - 2, y0 + 2, kx1, 4, Blocks.GLASS_PANE);
            windowsZ(b, kz1 + 2, kz2 - 2, y0 + 2, kx2, 4, Blocks.GLASS_PANE);
        }
        b.fill(kx1, 19, kz1, kx2, 19, kz2, trim);
        b.fill(kx1, 7, kz1, kx2, 7, kz1, trim);
        b.fill(kx1, 13, kz1, kx2, 13, kz1, trim);
        crenelX(b, kx1, kx2, 20, kz1, stone);
        crenelX(b, kx1, kx2, 20, kz2, stone);
        crenelZ(b, kz1, kz2, 20, kx1, stone);
        crenelZ(b, kz1, kz2, 20, kx2, stone);
        // central spire
        b.cylinder(25, 33, 20, 28, 4, stone, true);
        cone(b, 25, 33, 29, 10, 5, 0.5, roof.defaultBlockState(), false);
        b.set(25, 39, 33, Blocks.GOLD_BLOCK);
        b.fill(25, 40, 33, 25, 42, 33, Blocks.IRON_BARS);
        b.set(25, 42, 32, banner(Blocks.PINK_WALL_BANNER, Direction.NORTH));
        b.bigDoor(25, 1, kz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.bigDoor(24, 1, kz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.bigDoor(26, 1, kz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.fill(23, 4, kz1, 27, 4, kz1, Blocks.CHISELED_QUARTZ_BLOCK);
        // ground floor: throne hall in the middle (x 18..32), banquet hall west, kitchen east
        b.fill(17, 1, kz1 + 1, 17, 6, kz2 - 1, stone);
        b.fill(33, 1, kz1 + 1, 33, 6, kz2 - 1, stone);
        b.clear(17, 1, 26, 17, 3, 27);
        b.clear(33, 1, 26, 33, 3, 27);
        for (int z = kz1 + 3; z <= kz2 - 4; z += 4) {
            b.fill(19, 1, z, 19, 6, z, Blocks.QUARTZ_PILLAR);
            b.fill(31, 1, z, 31, 6, z, Blocks.QUARTZ_PILLAR);
        }
        carpet(b, 24, 1, kz1 + 1, 26, kz2 - 5, Blocks.RED_CARPET);
        b.fill(21, 1, kz2 - 4, 29, 1, kz2 - 1, Blocks.QUARTZ_BLOCK);
        b.fill(23, 2, kz2 - 3, 27, 2, kz2 - 1, Blocks.QUARTZ_BLOCK);
        b.set(25, 3, kz2 - 2, stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.set(25, 4, kz2 - 1, Blocks.GOLD_BLOCK);
        b.set(24, 3, kz2 - 2, Blocks.GOLD_BLOCK);
        b.set(26, 3, kz2 - 2, Blocks.GOLD_BLOCK);
        for (int x : new int[]{21, 29}) b.set(x, 5, kz2 - 1, banner(Blocks.PINK_WALL_BANNER, Direction.NORTH));
        for (int z = kz1 + 4; z <= kz2 - 4; z += 6) lamp(b, 25, 6, z);
        npc(b, "noble", 25, 3, kz2 - 3);
        npc(b, "guard", 22, 2, kz2 - 3);
        npc(b, "guard", 28, 2, kz2 - 3);
        npc(b, "guard", 21, 1, 27);
        // banquet hall
        longTableZ(b, 25, 42, 1, 13, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
        b.fill(kx1 + 1, 1, kz2 - 1, 16, 2, kz2 - 1, Blocks.BOOKSHELF);
        for (int z = 26; z <= 42; z += 8) lamp(b, 13, 6, z);
        npc(b, "waiter", 11, 1, 30);
        // kitchen & storerooms
        kitchenX(b, 35, 1, kz2 - 1, Direction.NORTH, Buildings.LOOT_TAVERN);
        b.barrel(40, 1, kz2 - 1, Buildings.LOOT_WAREHOUSE);
        b.barrel(40, 1, kz2 - 2, Buildings.LOOT_WAREHOUSE);
        table(b, 37, 1, 34, Blocks.SMOOTH_STONE_SLAB);
        table(b, 38, 1, 34, Blocks.SMOOTH_STONE_SLAB);
        lamp(b, 37, 6, 34);
        npc(b, "cook", 36, 1, 38);
        // grand stair (east hall) from the ground floor to floor 2, and on to floor 3
        stairsUp(b, 35, 1, 30, Direction.NORTH, 7, Blocks.QUARTZ_STAIRS, 3, stone);
        stairsUp(b, 38, 8, 44, Direction.NORTH, 6, Blocks.SPRUCE_STAIRS, 3, Blocks.SPRUCE_PLANKS);
        // second floor: royal chambers west, library east of the stairwell, chapel middle
        b.fill(17, 8, kz1 + 1, 17, 12, kz2 - 1, stone);
        b.fill(33, 8, kz1 + 1, 33, 12, kz2 - 1, stone);
        b.clear(17, 8, 25, 17, 10, 25);
        b.clear(33, 8, 25, 33, 10, 25);
        b.bed(11, 8, 40, Blocks.PINK_BED, Direction.SOUTH);
        b.bed(13, 8, 40, Blocks.PINK_BED, Direction.SOUTH);
        carpet(b, 10, 8, 32, 16, 38, Blocks.PINK_CARPET);
        b.chest(16, 8, 44, Direction.WEST, Buildings.LOOT_HOUSE);
        b.set(10, 8, 44, facing(Blocks.LOOM, Direction.EAST));
        b.set(10, 8, 24, Blocks.POTTED_PINK_TULIP);
        lamp(b, 13, 12, 36);
        npc(b, "noble", 14, 8, 35);
        b.fill(19, 8, kz2 - 1, 31, 10, kz2 - 1, Blocks.BOOKSHELF);
        b.fill(19, 8, 30, 19, 10, 40, Blocks.BOOKSHELF);
        b.set(25, 8, 36, Blocks.ENCHANTING_TABLE);
        table(b, 24, 8, 32, Blocks.SPRUCE_SLAB);
        table(b, 26, 8, 32, Blocks.SPRUCE_SLAB);
        lamp(b, 25, 12, 34);
        npc(b, "priest", 23, 8, 36);
        // third floor: open hall for the guard captain, and the way to the roof
        lamp(b, 25, 18, 30);
        b.chest(kx1 + 1, 14, kz1 + 1, Direction.SOUTH, Buildings.LOOT_MARINE);
        b.bed(11, 14, 42, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.ladderUp(kx2 - 1, 14, 30, 5, Direction.WEST);
        b.clear(kx2 - 1, 19, 30, kx2 - 1, 19, 30);
        npc(b, "guard", 30, 14, 30);

        // ---- the secret treasury under the throne: a hatch hidden behind the throne, iron door with a lever
        shell(b, 20, -7, 34, 32, -1, kz2, Blocks.DEEPSLATE_BRICKS);
        b.fill(21, -6, 35, 31, -6, kz2 - 1, Blocks.POLISHED_DEEPSLATE);
        hatch(b, 28, 1, kz2 - 2, -5, Direction.NORTH, Blocks.SPRUCE_TRAPDOOR, Blocks.QUARTZ_BLOCK);
        b.set(28, 2, kz2 - 2, Blocks.RED_CARPET);
        b.fill(21, -5, 39, 31, -2, 39, Blocks.DEEPSLATE_BRICKS);
        b.door(25, -5, 39, Blocks.IRON_DOOR, Direction.SOUTH);
        b.set(26, -4, 40, lever(Direction.SOUTH));
        for (int x = 22; x <= 30; x += 2) b.set(x, -5, 35, Blocks.GOLD_BLOCK);
        b.chest(23, -5, 37, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(27, -5, 37, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(25, -3, 36, lantern(Blocks.LANTERN, true));
        b.set(25, -2, 36, Blocks.DEEPSLATE_BRICKS);
        b.set(25, -3, 42, lantern(Blocks.LANTERN, true));
        b.set(25, -2, 42, Blocks.DEEPSLATE_BRICKS);
        mark(b, "treasure", 25, -5, 36);
        b.marker("arc_anchor", 25, 1, 12, tag("arc", "royal_court"));
    }

    private BluesLandmarks() {}
}
