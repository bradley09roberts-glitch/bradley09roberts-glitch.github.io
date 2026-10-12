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

/** Set pieces of the second half of Paradise and the Calm Belt: Water 7, Enies Lobby, Thriller Bark, Kuraigana, Sabaody, Amazon Lily. */
final class GrandLineLandmarks {

    // ============================================================== Water 7: Galley-La dock

    /** The shipwrights' Dock One: a dry dock with a hull on the stocks, a launch channel under a gantry crane,
     *  a lumber yard and the company headquarters (the president's office hides the old warship plans). */
    static void galleyLaDocks(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 45 x 41; y=0 is sea level, quay surface at y=1
        // quay body everywhere, then the sea front and the launch channel are cut out
        b.clear(0, 2, 0, w - 1, 44, d - 1);
        b.fill(0, -7, 0, w - 1, -7, d - 1, Blocks.STONE);
        b.fill(0, -6, 0, w - 1, 0, d - 1, Blocks.STONE_BRICKS);
        b.fill(0, 1, 0, w - 1, 1, d - 1, Blocks.POLISHED_ANDESITE);
        b.fill(0, -6, 0, w - 1, 0, 2, Blocks.WATER);
        b.clear(0, 1, 0, w - 1, 1, 2);
        b.fill(14, -6, 0, 30, 0, 26, Blocks.WATER);
        b.clear(14, 1, 0, 30, 1, 26);
        b.fill(13, 1, 3, 13, 1, 27, Blocks.SMOOTH_STONE);
        b.fill(31, 1, 3, 31, 1, 27, Blocks.SMOOTH_STONE);
        b.fill(14, 1, 27, 30, 1, 27, Blocks.SMOOTH_STONE);
        for (int z = 5; z <= 25; z += 5) {
            b.set(13, 2, z, Blocks.STONE_BRICK_WALL);
            b.set(31, 2, z, Blocks.STONE_BRICK_WALL);
        }
        for (int[] l : new int[][]{{14, 10, 13}, {30, 10, 31}, {14, 22, 13}}) {
            Direction f = l[2] < l[0] ? Direction.EAST : Direction.WEST;
            b.ladderUp(l[0], -1, l[1], 3, f);
        }
        for (int x : new int[]{6, 38}) b.ladderUp(x, -1, 2, 3, Direction.NORTH);
        b.marker("ship_spawn", 22, 0, 8, new CompoundTag());
        b.marker("dock", 13, 2, 4, new CompoundTag());
        // gantry crane over the channel
        for (int x : new int[]{13, 31}) b.fill(x, 2, 18, x, 15, 18, Blocks.STRIPPED_DARK_OAK_LOG);
        b.fill(13, 16, 18, 31, 16, 18, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        b.fill(21, 15, 17, 23, 15, 19, Blocks.IRON_BLOCK);
        b.fill(22, 9, 18, 22, 14, 18, Blocks.CHAIN);
        b.set(22, 8, 18, Blocks.IRON_BLOCK);
        b.ladderUp(13, 2, 19, 14, Direction.SOUTH);
        // ---- the dry dock (west) with a hull on the stocks
        b.clear(3, -4, 5, 11, 1, 27);
        b.fill(3, -5, 5, 11, -5, 27, Blocks.STONE_BRICKS);
        b.fill(3, -4, 4, 11, 1, 4, Blocks.DARK_OAK_PLANKS);
        for (int x = 3; x <= 11; x += 2) b.set(x, 1, 4, Blocks.IRON_BLOCK);
        stairsUp(b, 4, -4, 22, Direction.SOUTH, 6, Blocks.STONE_BRICK_STAIRS, 2, Blocks.STONE_BRICKS);
        int kx = 7;
        for (int z = 7; z <= 25; z++) b.set(kx, -4, z, axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
        for (int z = 8; z <= 24; z += 2) {
            int hw = z < 11 ? 1 + (z - 8) / 2 : z > 21 ? 1 + (24 - z) / 2 : 3;
            for (int y = -3; y <= 0; y++) {
                int off = y == -3 ? hw - 1 : hw;
                b.set(kx - off, y, z, Blocks.STRIPPED_SPRUCE_LOG);
                b.set(kx + off, y, z, Blocks.STRIPPED_SPRUCE_LOG);
            }
            if (z >= 12 && z <= 18) {
                for (int y = -3; y <= -2; y++) {
                    b.set(kx - hw, y, z + 1, Blocks.SPRUCE_PLANKS);
                    b.set(kx + hw, y, z + 1, Blocks.SPRUCE_PLANKS);
                }
            }
        }
        b.fill(kx, -3, 6, kx, 1, 6, Blocks.STRIPPED_SPRUCE_LOG);
        b.fill(kx, -3, 26, kx, 2, 26, Blocks.STRIPPED_SPRUCE_LOG);
        for (int z = 6; z <= 20; z += 2) {
            b.fill(4, -4, z, 4, 0, z, Blocks.SCAFFOLDING);
            b.fill(11, -4, z, 11, 0, z, Blocks.SCAFFOLDING);
        }
        npc(b, "shipwright", 9, -4, 10);
        npc(b, "shipwright", 5, -4, 13);
        // ---- lumber yard and the sawmill shed (east)
        b.fill(34, 2, 6, 37, 3, 13, axis(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Z));
        b.fill(39, 2, 6, 42, 4, 13, axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Z));
        for (int[] p : new int[][]{{33, 17}, {43, 17}, {33, 26}, {43, 26}, {38, 17}, {38, 26}}) b.fill(p[0], 2, p[1], p[0], 6, p[1], Blocks.SPRUCE_LOG);
        b.fill(32, 7, 16, 44, 7, 27, Blocks.SPRUCE_SLAB);
        b.fill(33, 2, 27, 43, 6, 27, Blocks.SPRUCE_PLANKS);
        for (int x = 34; x <= 42; x += 2) b.set(x, 2, 26, Blocks.STONECUTTER);
        b.set(35, 2, 20, Blocks.CRAFTING_TABLE);
        b.set(36, 2, 20, Blocks.SMITHING_TABLE);
        b.set(40, 2, 20, Blocks.GRINDSTONE);
        b.set(41, 2, 20, Blocks.ANVIL);
        b.chest(43, 2, 22, Direction.WEST, Buildings.LOOT_SMITH);
        b.barrel(43, 2, 24, Buildings.LOOT_WAREHOUSE);
        b.barrel(42, 2, 24, Buildings.LOOT_WAREHOUSE);
        b.hanging(38, 6, 21, Blocks.LANTERN);
        npc(b, "shipwright", 38, 2, 22);
        npc(b, "dockworker", 35, 2, 15);
        npc(b, "dockworker", 20, 2, 28);
        // ---- company headquarters (x 6..38, z 30..39): three floors, doors onto the yard
        int x1 = 6, x2 = 38, z1 = 30, z2 = 39;
        int[] fl = {1, 7, 12};
        for (int f = 0; f < 3; f++) {
            int y0 = fl[f], h = f == 0 ? 5 : 4;
            b.fill(x1, y0, z1, x2, y0, z2, f == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SPRUCE_PLANKS);
            b.walls(x1, y0 + 1, z1, x2, y0 + h, z2, Blocks.WHITE_TERRACOTTA);
            b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + h, z2 - 1);
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z1, 3, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x1, 3, Blocks.GLASS_PANE);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x2, 3, Blocks.GLASS_PANE);
        }
        b.fill(x1, 17, z1, x2, 17, z2, Blocks.SPRUCE_PLANKS);
        posts(b, x1, 2, z1, x2, 16, z2, Blocks.SMOOTH_QUARTZ);
        b.fill(x1, 7, z1, x2, 7, z1, Blocks.SMOOTH_QUARTZ);
        b.fill(x1, 12, z1, x2, 12, z1, Blocks.SMOOTH_QUARTZ);
        hipRoof(b, x1 - 1, z1 - 1, x2 + 1, z2 + 1, 18, Blocks.BRICK_STAIRS, Blocks.BRICKS);
        for (int x = 21; x <= 23; x++) b.bigDoor(x, 2, z1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        for (int x : new int[]{18, 26}) b.set(x, 10, z1 - 1, banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        b.set(22, 15, z1 - 1, banner(Blocks.WHITE_WALL_BANNER, Direction.NORTH));
        // ground floor: drafting hall
        for (int x = 9; x <= 33; x += 6) {
            b.set(x, 2, 34, Blocks.CARTOGRAPHY_TABLE);
            table(b, x + 1, 2, 34, Blocks.SPRUCE_SLAB);
            b.set(x + 1, 3, 34, Blocks.WHITE_CARPET);
            chair(b, x + 1, 2, 35, Blocks.SPRUCE_STAIRS, Direction.SOUTH);
        }
        b.fill(x1 + 1, 2, z2 - 1, 30, 3, z2 - 1, Blocks.BOOKSHELF);
        b.fill(x1 + 1, 2, 31, 9, 2, 31, Blocks.BARREL);
        for (int x = 10; x <= 34; x += 8) b.hanging(x, 6, 35, Blocks.LANTERN);
        npc(b, "shipwright", 16, 2, 32);
        stairsUp(b, 36, 2, 38, Direction.NORTH, 6, Blocks.SPRUCE_STAIRS, 2, Blocks.SPRUCE_PLANKS);
        // second floor: offices
        for (int x = 10; x <= 30; x += 5) {
            table(b, x, 8, 33, Blocks.DARK_OAK_SLAB);
            b.set(x, 9, 33, candles(Blocks.CANDLE, 1));
            chair(b, x, 8, 34, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
            b.set(x + 1, 8, 33, Blocks.LECTERN);
        }
        b.chest(x2 - 3, 8, z2 - 1, Direction.NORTH, Buildings.LOOT_SHOP);
        for (int x = 12; x <= 32; x += 10) b.hanging(x, 11, 35, Blocks.LANTERN);
        npc(b, "merchant", 20, 8, 36);
        stairsUp(b, 8, 8, 38, Direction.NORTH, 5, Blocks.SPRUCE_STAIRS, 2, Blocks.SPRUCE_PLANKS);
        line(b, 10, 13, 33, 10, 37, Blocks.SPRUCE_FENCE);
        // third floor: the president's office, a shelf wall hides the old plans
        carpet(b, 12, 13, 31, 20, 38, Blocks.BLUE_CARPET);
        table(b, 16, 13, 36, Blocks.DARK_OAK_SLAB);
        table(b, 17, 13, 36, Blocks.DARK_OAK_SLAB);
        chair(b, 16, 13, 37, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        b.set(17, 14, 36, Blocks.POTTED_FERN);
        b.fill(14, 13, 31, 18, 13, 31, Blocks.SPRUCE_PLANKS);
        b.set(15, 14, 31, stair(Blocks.SPRUCE_STAIRS, Direction.EAST, Half.BOTTOM));
        b.set(17, 14, 31, stair(Blocks.SPRUCE_STAIRS, Direction.WEST, Half.BOTTOM));
        b.set(16, 14, 31, Blocks.SPRUCE_FENCE);
        b.set(16, 15, 31, Blocks.WHITE_WOOL);
        b.fill(21, 13, z1 + 1, 21, 16, z2 - 1, Blocks.BOOKSHELF);
        b.set(21, 13, 35, facing(Blocks.CHISELED_BOOKSHELF, Direction.WEST));
        b.set(21, 14, 35, Blocks.BOOKSHELF);
        b.door(21, 13, 31, Blocks.SPRUCE_DOOR, Direction.WEST);
        b.clear(22, 13, 34, 23, 14, 36);
        b.fill(24, 13, 33, 24, 15, 37, Blocks.WHITE_TERRACOTTA);
        b.fill(22, 13, 33, 23, 15, 33, Blocks.WHITE_TERRACOTTA);
        b.fill(22, 13, 37, 23, 15, 37, Blocks.WHITE_TERRACOTTA);
        b.fill(22, 15, 34, 23, 15, 36, Blocks.WHITE_TERRACOTTA);
        b.chest(23, 13, 35, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(23, 13, 34, facing(Blocks.LECTERN, Direction.WEST));
        mark(b, "treasure", 23, 13, 35);
        b.hanging(16, 16, 34, Blocks.LANTERN);
        npc(b, "noble", 15, 13, 35);
        npc(b, "guard", 26, 13, 34);
        b.chest(36, 13, 31, Direction.SOUTH, Buildings.LOOT_WAREHOUSE);
        b.hanging(30, 16, 35, Blocks.LANTERN);
        b.marker("legend", 22, 2, 28, tag("story", "galley_la_docks"));
    }

    // ============================================================== Enies Lobby: the Gates of Justice

    /** The colossal sea gate between two towers, half open over a dragging whirlpool, with a control room up high. */
    static void gatesOfJustice(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 47 x 17, y=0 is sea level
        b.clear(0, 1, 0, w - 1, 52, d - 1);
        b.fill(0, -9, 0, w - 1, -9, d - 1, Blocks.STONE);
        b.fill(0, -8, 0, w - 1, 0, d - 1, Blocks.WATER);
        Block skin = Blocks.SMOOTH_QUARTZ, band = Blocks.POLISHED_BLACKSTONE_BRICKS, door = Blocks.DEEPSLATE_TILES;
        // the two towers (x 1..13 and 33..45, z 3..13)
        for (int[] t : new int[][]{{1, 13}, {33, 45}}) {
            for (int y = -8; y <= 44; y++) {
                Block blk = (y % 9 == 0) ? band : skin;
                b.walls(t[0], y, 3, t[1], y, 13, blk);
                if (y <= 1 || y % 9 == 1) b.fill(t[0] + 1, y, 4, t[1] - 1, y, 12, y <= 1 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE);
            }
            b.fill(t[0], 45, 3, t[1], 45, 13, Blocks.SMOOTH_STONE);
            crenelX(b, t[0], t[1], 46, 3, skin);
            crenelX(b, t[0], t[1], 46, 13, skin);
            crenelZ(b, 3, 13, 46, t[0], skin);
            crenelZ(b, 3, 13, 46, t[1], skin);
            hipRoof(b, t[0] + 2, 5, t[1] - 2, 11, 46, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES);
            for (int y = 5; y <= 41; y += 9) {
                for (int z = 6; z <= 10; z += 4) {
                    b.set(t[0], y, z, connected(Blocks.IRON_BARS, Direction.Axis.Z));
                    b.set(t[1], y, z, connected(Blocks.IRON_BARS, Direction.Axis.Z));
                }
            }
        }
        // the gate leaves (z 7..8), half open in the middle, and the lintel
        for (int[] leaf : new int[][]{{14, 19}, {27, 32}}) {
            b.fill(leaf[0], -8, 7, leaf[1], 40, 8, door);
            for (int y = -6; y <= 38; y += 4)
                for (int x = leaf[0] + 1; x <= leaf[1] - 1; x += 2) b.set(x, y, 6, Blocks.IRON_BLOCK.defaultBlockState());
            b.fill(leaf[0], 18, 6, leaf[1], 20, 6, Blocks.CHISELED_DEEPSLATE);
        }
        b.fill(14, 41, 3, 32, 44, 13, skin);
        b.fill(14, 41, 3, 32, 41, 13, band);
        b.fill(20, 42, 2, 26, 44, 2, Blocks.GOLD_BLOCK);
        b.fill(21, 43, 1, 25, 43, 1, Blocks.GOLD_BLOCK);
        // the drag current in the gap
        for (int x = 20; x <= 26; x++)
            for (int z = 4; z <= 11; z++) {
                b.set(x, -9, z, Blocks.MAGMA_BLOCK);
                for (int y = -8; y <= 0; y++) b.set(x, y, z, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true));
            }
        // west tower: landing pier, guard hall, ladder shaft, control room
        b.fill(1, 1, 0, 13, 1, 2, Blocks.SPRUCE_PLANKS);
        for (int x = 1; x <= 13; x += 4) b.fill(x, -8, 0, x, 0, 0, Blocks.SPRUCE_LOG);
        b.fill(13, -1, 1, 13, 0, 1, Blocks.SPRUCE_LOG);
        b.ladderUp(14, -1, 1, 3, Direction.EAST);
        b.bigDoor(7, 2, 3, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(8, 3, 2, button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(6, 3, 4, button(Blocks.STONE_BUTTON, Direction.SOUTH));
        lampPost(b, 1, 2, 0, Blocks.IRON_BARS, 2, Blocks.LANTERN);
        lampPost(b, 13, 2, 0, Blocks.IRON_BARS, 2, Blocks.LANTERN);
        b.clear(2, 2, 4, 12, 9, 12);
        b.fill(2, 1, 4, 12, 1, 12, Blocks.SMOOTH_STONE);
        b.bed(3, 2, 10, Blocks.BLUE_BED, Direction.SOUTH);
        b.bed(5, 2, 10, Blocks.BLUE_BED, Direction.SOUTH);
        b.chest(12, 2, 11, Direction.WEST, Buildings.LOOT_MARINE);
        table(b, 9, 2, 8, Blocks.SMOOTH_STONE_SLAB);
        b.set(9, 3, 8, candles(Blocks.CANDLE, 2));
        chair(b, 10, 2, 8, Blocks.STONE_BRICK_STAIRS, Direction.EAST);
        b.hanging(7, 9, 7, Blocks.LANTERN);
        b.set(7, 10, 7, skin);
        npc(b, "marine", 6, 2, 6);
        npc(b, "marine", 9, 2, 1);
        npc(b, "marine", 39, 2, 1);
        // a ladder in the corner climbs through every floor
        b.ladderUp(12, 2, 5, 35, Direction.WEST);
        // control room (floor y=37)
        b.fill(2, 37, 4, 12, 37, 12, Blocks.POLISHED_DEEPSLATE);
        b.set(12, 37, 5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        b.clear(2, 38, 4, 12, 44, 12);
        b.fill(13, 39, 6, 13, 40, 10, Blocks.GLASS);
        for (int z = 5; z <= 11; z += 2) b.set(2, 39, z, lever(Direction.EAST));
        b.set(7, 38, 11, Blocks.GRINDSTONE);
        b.set(8, 38, 11, facing(Blocks.PISTON, Direction.UP));
        b.set(9, 38, 11, facing(Blocks.OBSERVER, Direction.NORTH));
        b.fill(5, 38, 12, 9, 40, 12, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
        b.chest(3, 38, 12, Direction.EAST, Buildings.LOOT_MARINE);
        b.set(6, 38, 6, facing(Blocks.LECTERN, Direction.EAST));
        b.hanging(7, 44, 8, Blocks.LANTERN);
        npc(b, "marine_officer", 8, 38, 8);
        // east tower: its own landing and a barracks at the base
        b.fill(33, 1, 0, 45, 1, 2, Blocks.SPRUCE_PLANKS);
        for (int x = 33; x <= 45; x += 4) b.fill(x, -8, 0, x, 0, 0, Blocks.SPRUCE_LOG);
        b.fill(33, -1, 1, 33, 0, 1, Blocks.SPRUCE_LOG);
        b.ladderUp(32, -1, 1, 3, Direction.WEST);
        b.bigDoor(39, 2, 3, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(40, 3, 2, button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(38, 3, 4, button(Blocks.STONE_BUTTON, Direction.SOUTH));
        b.clear(34, 2, 4, 44, 9, 12);
        b.fill(34, 1, 4, 44, 1, 12, Blocks.SMOOTH_STONE);
        for (int x = 35; x <= 43; x += 2) b.bed(x, 2, 11, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.barrel(44, 2, 5, Buildings.LOOT_WAREHOUSE);
        b.hanging(39, 9, 8, Blocks.LANTERN);
        b.set(39, 10, 8, skin);
        npc(b, "marine", 40, 2, 7);
        b.marker("arc_anchor", 23, 0, 14, tag("arc", "gates_of_justice"));
    }

    // ============================================================== Thriller Bark: the haunted mansion

    /** A gothic mansion behind a graveyard: banquet hall, a mad surgeon's laboratory, the master's chambers and a
     *  frozen vault behind the lab's cold-store wall. */
    static void hauntedMansion(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        Block wall = Blocks.DEEPSLATE_BRICKS, frame = Blocks.DARK_OAK_LOG, glass = Blocks.PURPLE_STAINED_GLASS_PANE;
        site(b, Blocks.PODZOL, Blocks.DIRT, 40);
        patchGround(b, 91, 0, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MOSS_BLOCK, Blocks.MYCELIUM);
        // iron fence with stone piers and a gate
        for (int x = 0; x < w; x++) {
            if (x % 6 == 0) b.fill(x, 1, 0, x, 3, 0, Blocks.POLISHED_BLACKSTONE_BRICKS);
            else b.set(x, 1, 0, connected(Blocks.IRON_BARS, Direction.Axis.X));
            if (x % 6 != 0) b.set(x, 2, 0, connected(Blocks.IRON_BARS, Direction.Axis.X));
        }
        b.clear(c - 2, 1, 0, c + 2, 2, 0);
        b.fill(c - 3, 1, 0, c - 3, 4, 0, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.fill(c + 3, 1, 0, c + 3, 4, 0, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(c - 3, 5, 0, Blocks.SOUL_LANTERN);
        b.set(c + 3, 5, 0, Blocks.SOUL_LANTERN);
        b.fill(c - 1, 0, 0, c + 1, 0, 15, Blocks.GRAVEL);
        // graveyard
        for (int gx = 3; gx <= 41; gx += 4)
            for (int gz = 3; gz <= 12; gz += 4) {
                if (Math.abs(gx - c) <= 3) continue;
                int r = b.rand(gx * 13 + gz, 4);
                b.set(gx, 1, gz, r == 0 ? Blocks.COBBLED_DEEPSLATE_WALL : Blocks.STONE_BRICK_WALL);
                if (r != 0) b.set(gx, 2, gz, r == 1 ? Blocks.MOSSY_STONE_BRICK_WALL : Blocks.STONE_BRICK_WALL);
                b.set(gx, 0, gz + 1, Blocks.COARSE_DIRT);
                if (r == 2) b.set(gx + 1, 1, gz, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
            }
        for (int[] t : new int[][]{{8, 7}, {36, 6}, {14, 13}, {30, 12}}) deadTree(b, t[0], 1, t[1], 6 + b.rand(t[0], 3), t[0] * 7 + t[1], Blocks.DARK_OAK_LOG);
        scatter(b, 92, 10, 1, 1, w - 2, 14, 1, Blocks.COBWEB.defaultBlockState());
        b.clear(c - 1, 1, 1, c + 1, 3, 15);
        npc(b, "ruin_guardian", 10, 1, 10);
        npc(b, "ruin_guardian", 34, 1, 4);

        // ---- the mansion (x 6..38, z 16..40), floors at y=0 and y=7, roof at 13
        int x1 = 6, x2 = 38, z1 = 16, z2 = 40;
        for (int f = 0; f < 2; f++) {
            int y0 = f * 7, h = 6;
            b.fill(x1, y0, z1, x2, y0, z2, f == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.DARK_OAK_PLANKS);
            b.walls(x1, y0 + 1, z1, x2, y0 + h, z2, wall);
            b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + h, z2 - 1);
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z1, 4, glass);
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z2, 4, glass);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x1, 4, glass);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x2, 4, glass);
        }
        b.fill(x1, 13, z1, x2, 13, z2, Blocks.DARK_OAK_PLANKS);
        posts(b, x1, 1, z1, x2, 13, z2, frame);
        for (int x = x1; x <= x2; x += 8) b.fill(x, 1, z1, x, 12, z1, frame);
        b.gableRoof(x1 - 1, z1 - 1, x2 + 1, z2 + 1, 14, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, wall);
        // front door, steps, gargoyle lights
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.fill(c - 2, 4, z1, c + 2, 4, z1, Blocks.CHISELED_POLISHED_BLACKSTONE);
        b.set(c - 2, 5, z1 - 1, Blocks.WITHER_SKELETON_SKULL);
        b.set(c + 2, 5, z1 - 1, Blocks.WITHER_SKELETON_SKULL);
        b.set(c - 2, 4, z1 - 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.set(c + 2, 4, z1 - 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
        b.hanging(c - 3, 3, z1 - 1, Blocks.SOUL_LANTERN);
        b.set(c - 3, 4, z1 - 1, Blocks.POLISHED_BLACKSTONE_BRICKS);
        // central tower with a spire and a bell
        b.fill(c - 3, 13, 26, c + 3, 26, 32, wall);
        b.clear(c - 2, 13, 27, c + 2, 25, 31);
        b.clear(c - 3, 22, 28, c + 3, 23, 30);
        b.clear(c - 1, 22, 26, c + 1, 23, 32);
        b.set(c, 25, 29, bellCeiling());
        cone(b, c, 29, 27, 10, 4.5, 0.5, Blocks.BLACKSTONE.defaultBlockState(), false);
        b.set(c, 37, 29, Blocks.LIGHTNING_ROD);
        // ground floor: hall (middle), banquet (east), laboratory (west)
        b.fill(14, 1, z1 + 1, 14, 6, z2 - 1, wall);
        b.fill(30, 1, z1 + 1, 30, 6, z2 - 1, wall);
        b.clear(14, 1, 20, 14, 3, 21);
        b.clear(30, 1, 20, 30, 3, 21);
        carpet(b, c - 1, 1, z1 + 1, c + 1, z2 - 6, Blocks.PURPLE_CARPET);
        stairsUp(b, 20, 1, z2 - 2, Direction.NORTH, 6, Blocks.DARK_OAK_STAIRS, 5, Blocks.DARK_OAK_PLANKS);
        b.hanging(c, 6, 22, Blocks.SOUL_LANTERN);
        b.set(16, 1, 17, Blocks.SKELETON_SKULL);
        npc(b, "ruin_guardian", 18, 1, 24);
        // banquet hall
        longTableZ(b, 19, 36, 1, 34, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        b.fill(31, 1, z2 - 1, 37, 2, z2 - 1, Blocks.BOOKSHELF);
        b.set(37, 1, 18, Blocks.BREWING_STAND);
        b.barrel(37, 1, 20, Buildings.LOOT_TAVERN);
        for (int z = 21; z <= 33; z += 6) b.hanging(34, 6, z, Blocks.SOUL_LANTERN);
        npc(b, "pirate", 32, 1, 26);
        // laboratory: operating tables, specimen jars, a cold store at the back
        for (int z = 20; z <= 28; z += 4) {
            b.fill(9, 1, z, 11, 1, z, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            b.fill(9, 2, z, 11, 2, z, Blocks.WHITE_CARPET);
        }
        for (int z = 18; z <= 30; z += 2) {
            b.set(x1 + 1, 1, z, Blocks.CAULDRON);
            b.set(x1 + 1, 2, z, z % 4 == 0 ? Blocks.LIME_STAINED_GLASS : Blocks.GREEN_STAINED_GLASS);
        }
        b.set(13, 1, 18, Blocks.BREWING_STAND);
        b.set(13, 1, 19, Blocks.BREWING_STAND);
        b.set(13, 1, 30, facing(Blocks.LECTERN, Direction.WEST));
        b.chest(13, 1, 31, Direction.WEST, Buildings.LOOT_RUIN);
        b.hanging(10, 6, 24, Blocks.LANTERN);
        b.fill(x1 + 1, 1, 33, 13, 6, 33, Blocks.PACKED_ICE);
        b.set(10, 1, 33, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.set(10, 2, 33, Blocks.CRACKED_DEEPSLATE_BRICKS);
        npc(b, "ruin_guardian", 11, 1, 22);
        // the frozen vault behind the cold store
        b.fill(x1 + 1, 0, 34, 13, 0, z2 - 1, Blocks.BLUE_ICE);
        b.clear(x1 + 1, 1, 34, 13, 6, z2 - 1);
        b.fill(8, 1, 37, 12, 1, 39, Blocks.PACKED_ICE);
        b.fill(9, 2, 37, 11, 4, 39, Blocks.ICE);
        b.fill(10, 5, 38, 10, 5, 38, Blocks.ZOMBIE_HEAD);
        b.chest(7, 1, 35, Direction.EAST, Buildings.LOOT_TREASURE);
        b.set(13, 1, 35, Blocks.SOUL_LANTERN);
        mark(b, "treasure", 7, 1, 35);
        // upper floor: master's chambers (east), library (west), wedding chapel (middle)
        b.fill(14, 8, z1 + 1, 14, 12, z2 - 1, wall);
        b.fill(30, 8, z1 + 1, 30, 12, z2 - 1, wall);
        b.clear(14, 8, 28, 14, 10, 29);
        b.clear(30, 8, 28, 30, 10, 29);
        line(b, 19, 8, 32, 19, 38, Blocks.DARK_OAK_FENCE);
        line(b, 25, 8, 32, 25, 38, Blocks.DARK_OAK_FENCE);
        for (int z = 20; z <= 28; z += 2) {
            for (int x = 16; x <= 20; x++) chair(b, x, 8, z, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
            for (int x = 24; x <= 28; x++) chair(b, x, 8, z, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        }
        b.fill(c - 2, 8, z1 + 1, c + 2, 8, z1 + 2, Blocks.CHISELED_POLISHED_BLACKSTONE);
        b.set(c, 9, z1 + 1, candles(Blocks.PURPLE_CANDLE, 4));
        b.hanging(c, 12, 24, Blocks.SOUL_LANTERN);
        b.bed(34, 8, 36, Blocks.BLACK_BED, Direction.SOUTH);
        b.bed(35, 8, 36, Blocks.BLACK_BED, Direction.SOUTH);
        carpet(b, 31, 8, 18, 37, 33, Blocks.BLACK_CARPET);
        b.chest(37, 8, 18, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(31, 8, z2 - 1, Blocks.ENDER_CHEST);
        b.hanging(34, 12, 26, Blocks.SOUL_LANTERN);
        npc(b, "pirate_captain", 34, 8, 28);
        npc(b, "pirate", 33, 8, 20);
        b.fill(x1 + 1, 8, z1 + 1, x1 + 1, 10, z2 - 1, Blocks.BOOKSHELF);
        b.fill(x1 + 2, 8, z2 - 1, 13, 10, z2 - 1, Blocks.BOOKSHELF);
        b.set(10, 8, 28, Blocks.ENCHANTING_TABLE);
        b.set(11, 8, 22, facing(Blocks.LECTERN, Direction.NORTH));
        b.hanging(10, 12, 26, Blocks.SOUL_LANTERN);
        npc(b, "ruin_guardian", 10, 8, 32);
        b.marker("arc_anchor", c, 1, 18, tag("arc", "ghost_ship_island"));
    }

    // ============================================================== Kuraigana: the ruined castle

    private static final Block[] RUIN = {Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE};

    private static Block ruin(Builder b, int salt) {
        return RUIN[b.rand(salt, RUIN.length)];
    }

    /** Broken wall run: random height between lo and hi, built along x or z. */
    private static void brokenWall(Builder b, int x1, int z1, int x2, int z2, int lo, int hi, int salt) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                int h = lo + b.rand(salt + x * 31 + z * 17, hi - lo + 1);
                for (int y = 1; y <= h; y++) b.set(x, y, z, ruin(b, salt + x * 7 + y * 131 + z * 13));
            }
    }

    /** A ruined castle in a gloomy forest: crumbling walls, a broken tower, a sword-training yard and a keep that is
     *  still lived in (great hall, master's rooms), with a wine cellar that hides an old armoury. */
    static void ruinedCastle(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 41
        site(b, Blocks.PODZOL, Blocks.DIRT, 36);
        patchGround(b, 101, 0, Blocks.PODZOL, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT, Blocks.MOSSY_COBBLESTONE);
        scatter(b, 102, 14, 1, 1, w - 2, d - 2, 1, Blocks.BROWN_MUSHROOM.defaultBlockState());
        scatter(b, 103, 8, 1, 1, w - 2, d - 2, 1, Blocks.RED_MUSHROOM.defaultBlockState());
        // outer walls with gaps
        brokenWall(b, 1, 1, w - 2, 1, 2, 7, 111);
        brokenWall(b, 1, d - 2, w - 2, d - 2, 3, 8, 112);
        brokenWall(b, 1, 2, 1, d - 3, 2, 7, 113);
        brokenWall(b, w - 2, 2, w - 2, d - 3, 1, 6, 114);
        b.clear(c - 2, 1, 1, c + 2, 8, 1);
        b.clear(w - 2, 1, 12, w - 2, 8, 15);
        // gatehouse stumps
        b.fill(c - 4, 1, 0, c - 3, 9, 2, Blocks.STONE_BRICKS);
        b.fill(c + 3, 1, 0, c + 4, 7, 2, Blocks.MOSSY_STONE_BRICKS);
        b.hanging(c - 3, 5, 3, Blocks.LANTERN);
        b.set(c - 3, 6, 3, Blocks.STONE_BRICKS);
        // broken round tower (north-west)
        for (int y = 1; y <= 18; y++) {
            int cut = b.rand(y * 17, 3);
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 4; z++) {
                    double dd = Math.sqrt(x * x + z * z);
                    if (dd > 4.5 || dd < 3.5) continue;
                    if (y > 10 && x + z + y / 3 > 2 + cut) continue;
                    b.set(7 + x, y, 7 + z, ruin(b, x * 31 + y * 7 + z));
                }
        }
        b.clear(10, 1, 7, 11, 3, 7);
        b.fill(4, 0, 4, 10, 0, 10, Blocks.COBBLESTONE);
        heap(b, 104, 7, 7, 1, 2, 1, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL);
        npc(b, "ruin_guardian", 9, 1, 5);
        // training yard: dummies and slashed posts
        for (int x = 26; x <= 36; x += 3) {
            b.set(x, 1, 5, Blocks.HAY_BLOCK);
            b.set(x, 2, 5, Blocks.CARVED_PUMPKIN);
            b.fill(x, 1, 9, x, 3, 9, Blocks.STRIPPED_OAK_LOG);
        }
        b.set(31, 1, 12, Blocks.TARGET);
        npc(b, "master", 31, 1, 7);
        npc(b, "ruin_guardian", 24, 1, 11);
        npc(b, "ruin_guardian", 35, 1, 13);
        // dark oaks pushing through the ruins
        tree(b, 5, 1, 34, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, 6, 3, 121, null);
        tree(b, 35, 1, 35, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, 7, 3, 122, null);

        // ---- the keep (x 11..29, z 17..35): ground floor y=0, upper floor y=6, roof y=12, half collapsed
        int x1 = 11, x2 = 29, z1 = 17, z2 = 35;
        for (int f = 0; f < 2; f++) {
            int y0 = f * 6;
            b.fill(x1, y0, z1, x2, y0, z2, f == 0 ? Blocks.STONE_BRICKS : Blocks.DARK_OAK_PLANKS);
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    for (int y = y0 + 1; y <= y0 + 5; y++) b.set(x, y, z, edge ? ruin(b, x * 13 + y * 7 + z * 3) : Blocks.AIR.defaultBlockState().getBlock());
                }
            windowsX(b, x1 + 2, x2 - 2, y0 + 2, z1, 4, Blocks.IRON_BARS);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x1, 4, Blocks.IRON_BARS);
            windowsZ(b, z1 + 2, z2 - 2, y0 + 2, x2, 4, Blocks.IRON_BARS);
        }
        b.fill(x1, 12, z1, x2, 12, z2, Blocks.DARK_OAK_PLANKS);
        for (int x = x1; x <= x2; x++) if (x < 20 || b.rand(x, 2) == 0) b.set(x, 13, z1, Blocks.STONE_BRICKS);
        for (int x = 22; x <= x2; x++) for (int z = z1; z <= z1 + 5; z++) if (b.rand(x * 5 + z, 3) != 0) b.set(x, 12, z, Blocks.AIR);
        heap(b, 105, 26, 21, 7, 2, 1, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE);
        b.bigDoor(c, 1, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.fill(c - 1, 4, z1, c + 1, 4, z1, Blocks.CHISELED_STONE_BRICKS);
        // great hall
        longTableZ(b, 22, 31, 1, c, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        b.set(c, 1, 33, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.set(c, 2, 34, Blocks.DARK_OAK_PLANKS);
        b.fill(x1 + 1, 1, 33, x1 + 1, 3, 33, Blocks.BRICKS);
        b.set(x1 + 2, 1, 33, Blocks.CAMPFIRE);
        b.fill(x1 + 1, 1, 32, x1 + 2, 3, 32, Blocks.BRICKS);
        b.fill(x1 + 1, 1, 34, x1 + 2, 3, 34, Blocks.BRICKS);
        b.set(x1 + 2, 3, 33, Blocks.BRICKS);
        for (int z = 20; z <= 32; z += 6) b.hanging(c, 5, z, Blocks.LANTERN);
        b.fill(x2 - 1, 1, 19, x2 - 1, 2, 23, Blocks.BARREL);
        npc(b, "ruin_guardian", 26, 1, 26);
        stairsUp(b, 27, 1, 33, Direction.NORTH, 5, Blocks.STONE_BRICK_STAIRS, 2, Blocks.STONE_BRICKS);
        line(b, 26, 7, 28, 26, 33, Blocks.DARK_OAK_FENCE);
        // master's rooms upstairs: bed, sword rack, books, a view over the forest
        b.bed(14, 7, 32, Blocks.BLACK_BED, Direction.SOUTH);
        b.chest(13, 7, 34, Direction.EAST, Buildings.LOOT_HOUSE);
        for (int x = 16; x <= 20; x++) b.set(x, 8, z2 - 1, connected(Blocks.IRON_BARS, Direction.Axis.X));
        b.fill(16, 7, z2 - 1, 20, 7, z2 - 1, Blocks.DARK_OAK_PLANKS);
        b.fill(x1 + 1, 7, 24, x1 + 1, 9, 30, Blocks.BOOKSHELF);
        table(b, 16, 7, 27, Blocks.DARK_OAK_SLAB);
        b.set(16, 8, 27, candles(Blocks.CANDLE, 2));
        chair(b, 16, 7, 28, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        b.hanging(17, 11, 29, Blocks.LANTERN);
        // the wine cellar (hatch by the hearth) and the old armoury sealed behind its racks
        shell(b, 12, -6, 26, 22, 0, 34, Blocks.COBBLED_DEEPSLATE);
        b.fill(12, 0, 26, 22, 0, 34, Blocks.STONE_BRICKS);
        hatch(b, 14, 0, 30, -5, Direction.EAST, Blocks.DARK_OAK_TRAPDOOR, Blocks.COBBLED_DEEPSLATE);
        for (int z = 27; z <= 33; z++) {
            b.barrel(21, -5, z, Buildings.LOOT_TAVERN);
            b.set(21, -4, z, Blocks.BARREL);
        }
        b.hanging(17, -1, 30, Blocks.LANTERN);
        // the old armoury north of the cellar, sealed behind a cracked patch of its wall
        b.fill(15, -6, 22, 19, -2, 26, Blocks.COBBLED_DEEPSLATE);
        b.clear(16, -5, 23, 18, -3, 25);
        b.set(17, -5, 26, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.set(17, -4, 26, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.chest(16, -5, 23, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.set(18, -5, 23, Blocks.GRINDSTONE);
        b.set(18, -5, 25, Blocks.SMITHING_TABLE);
        b.hanging(17, -3, 23, Blocks.SOUL_LANTERN);
        mark(b, "treasure", 16, -5, 23);
        b.marker("legend", c, 1, 14, tag("story", "ruined_castle"));
    }

    // ============================================================== Sabaody: a mangrove grove

    private static void bubble(Builder b, int x, int y, int z, double r, Block glass) {
        ellipsoid(b, x, y, z, r, r, r, glass.defaultBlockState(), true, -100);
    }

    /** A giant resin-weeping mangrove: arching roots shelter a bar, bubbles drift up, a coating yard works the shore
     *  and a ladder climbs the hollow trunk to a lookout (and a resin-sealed cache). */
    static void mangroveGrove(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 18; // 37 x 37
        site(b, Blocks.MUD, Blocks.DIRT, 40);
        patchGround(b, 131, 0, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.MOSS_BLOCK, Blocks.PACKED_MUD);
        // hollow trunk (r=5) up to y=30, canopy above
        for (int y = 1; y <= 30; y++) {
            b.cylinder(c, c, y, y, 5, Blocks.MANGROVE_LOG, true);
            b.cylinder(c, c, y, y, 4, Blocks.AIR, false);
        }
        b.fill(c - 4, 0, c - 4, c + 4, 0, c + 4, Blocks.MANGROVE_PLANKS);
        for (int y = 8; y <= 20; y++) b.set(c, y, c - 5, Blocks.WHITE_CONCRETE);
        b.fill(c - 1, 20, c - 5, c, 20, c - 5, Blocks.WHITE_CONCRETE);
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 12; z++)
                for (int y = 28; y <= 38; y++) {
                    double dd = (x * x + z * z) / 144.0 + Math.pow((y - 32) / 5.0, 2);
                    if (dd > 1 || b.rand(x * 131 + z * 17 + y, 7) == 0) continue;
                    if (Math.abs(x) <= 4 && Math.abs(z) <= 4 && y <= 30) continue;
                    b.set(c + x, y, c + z, leaves(Blocks.MANGROVE_LEAVES));
                }
        // arching roots radiating out
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + 0.2;
            for (int k = 0; k <= 8; k++) {
                int rx = c + (int) Math.round(Math.cos(a) * (5 + k)), rz = c + (int) Math.round(Math.sin(a) * (5 + k));
                int ry = (int) Math.round(6 * Math.sin(Math.PI * (k + 2) / 12.0));
                if (ry < 1) ry = 1;
                b.set(rx, ry, rz, Blocks.MANGROVE_ROOTS);
                if (k == 8) b.fill(rx, 1, rz, rx, ry, rz, Blocks.MANGROVE_ROOTS);
            }
        }
        // the trunk doorway (south) and its ladder to the lookout
        b.door(c, 1, c + 5, Blocks.MANGROVE_DOOR, Direction.SOUTH);
        b.ladderUp(c, 1, c - 4, 30, Direction.SOUTH);
        b.fill(c - 4, 30, c - 4, c + 4, 30, c + 4, Blocks.MANGROVE_PLANKS);
        b.clear(c, 30, c - 4, c, 30, c - 4);
        b.clear(c - 4, 31, c - 4, c + 4, 33, c + 4);
        outline(b, c - 4, 31, c - 4, c + 4, c + 4, Blocks.MANGROVE_FENCE);
        b.clear(c, 31, c - 4, c, 31, c - 4);
        b.set(c + 2, 31, c + 2, Blocks.CRAFTING_TABLE);
        b.bed(c - 2, 31, c + 1, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.set(c + 2, 33, c, lantern(Blocks.LANTERN, true));
        b.set(c + 2, 34, c, Blocks.MANGROVE_PLANKS);
        npc(b, "resident", c + 1, 31, c - 2);
        b.hanging(c, 12, c, Blocks.LANTERN);
        b.set(c, 13, c, Blocks.MANGROVE_PLANKS);
        // resin-sealed cache in the trunk wall (honey seals a pocket)
        b.set(c + 4, 6, c, Blocks.HONEY_BLOCK);
        b.chest(c + 5, 6, c, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(c + 5, 7, c, Blocks.MANGROVE_LOG);
        mark(b, "treasure", c + 5, 6, c);
        // ---- the bar among the roots (west)
        int bx1 = 1, bx2 = 10, bz1 = 13, bz2 = 23;
        room(b, bx1, 0, bz1, bx2, bz2, 4, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.MUD_BRICKS);
        posts(b, bx1, 1, bz1, bx2, 4, bz2, Blocks.MANGROVE_LOG);
        b.bigDoor(bx2, 1, 18, Blocks.MANGROVE_DOOR, Direction.EAST);
        windowsZ(b, bz1 + 2, bz2 - 2, 2, bx1, 3, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        b.fill(bx1 + 1, 1, bz1 + 3, bx1 + 6, 1, bz1 + 3, axis(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.X));
        for (int x = bx1 + 1; x <= bx1 + 6; x += 2) b.barrel(x, 1, bz1 + 1, Buildings.LOOT_TAVERN);
        b.set(bx1 + 2, 2, bz1 + 1, Blocks.BREWING_STAND);
        npc(b, "bartender", bx1 + 4, 1, bz1 + 2);
        for (int[] t : new int[][]{{4, 19}, {7, 21}}) {
            table(b, t[0], 1, t[1], Blocks.MANGROVE_SLAB);
            b.set(t[0], 2, t[1], candles(Blocks.LIGHT_BLUE_CANDLE, 2));
            chair(b, t[0] - 1, 1, t[1], Blocks.MANGROVE_STAIRS, Direction.WEST);
            chair(b, t[0] + 1, 1, t[1], Blocks.MANGROVE_STAIRS, Direction.EAST);
        }
        b.hanging(5, 4, 18, Blocks.LANTERN);
        b.hanging(5, 4, 21, Blocks.LANTERN);
        b.marker("quest_board", bx1 + 1, 2, 22, new CompoundTag());
        npc(b, "patron", 6, 1, 17);
        // ---- coating yard (east): a ship model under a bubble film, resin vats
        b.fill(26, 0, 22, 35, 0, 33, Blocks.SPRUCE_PLANKS);
        for (int[] p : new int[][]{{26, 22}, {35, 22}, {26, 33}, {35, 33}}) b.fill(p[0], 1, p[1], p[0], 5, p[1], Blocks.SPRUCE_LOG);
        b.fill(26, 6, 22, 35, 6, 33, Blocks.SPRUCE_SLAB);
        b.fill(29, 1, 26, 31, 1, 28, Blocks.SPRUCE_PLANKS);
        b.set(30, 2, 26, stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.set(30, 2, 28, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, Half.BOTTOM));
        b.set(30, 2, 27, Blocks.SPRUCE_FENCE);
        b.set(30, 3, 27, Blocks.WHITE_WOOL);
        ellipsoid(b, 30, 2, 27, 3.6, 3.6, 3.6, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), true, -1);
        b.set(33, 1, 24, Blocks.HONEY_BLOCK);
        b.set(34, 1, 24, Blocks.SLIME_BLOCK);
        b.set(34, 1, 26, Blocks.CAULDRON);
        b.chest(34, 1, 31, Direction.WEST, Buildings.LOOT_WAREHOUSE);
        b.hanging(30, 5, 32, Blocks.LANTERN);
        npc(b, "shipwright", 28, 1, 31);
        // drifting bubbles and a souvenir stall
        bubble(b, 8, 10, 6, 1.6, Blocks.WHITE_STAINED_GLASS);
        bubble(b, 28, 14, 8, 2.1, Blocks.LIGHT_BLUE_STAINED_GLASS);
        bubble(b, 6, 20, 30, 1.6, Blocks.WHITE_STAINED_GLASS);
        bubble(b, 31, 9, 14, 1.4, Blocks.WHITE_STAINED_GLASS);
        bubble(b, 13, 4, 5, 1.2, Blocks.LIGHT_BLUE_STAINED_GLASS);
        for (int[] cst : new int[][]{{27, 2}, {31, 2}, {27, 5}, {31, 5}}) b.fill(cst[0], 1, cst[1], cst[0], 3, cst[1], Blocks.MANGROVE_FENCE);
        b.fill(27, 4, 2, 31, 4, 5, Blocks.CYAN_WOOL);
        b.barrel(28, 1, 5, Buildings.LOOT_SHOP);
        npc(b, "merchant", 29, 1, 3);
        npc(b, "resident", c, 1, c + 8);
        b.marker("legend", c, 1, c + 7, tag("story", "mangrove_grove"));
    }

    // ============================================================== Sabaody: the auction house

    /** The grand auction house: a domed theatre with tiered seats, a curtained stage, private boxes, backstage cells
     *  and a strong room for the lots. */
    static void auctionHouse(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 16; // 33 x 35
        Block wall = Blocks.RED_TERRACOTTA, trim = Blocks.GOLD_BLOCK, cream = Blocks.SMOOTH_SANDSTONE;
        site(b, Blocks.POLISHED_ANDESITE, Blocks.STONE, 30);
        int x1 = 2, x2 = 30, z1 = 4, z2 = 33;
        b.fill(x1, 0, z1, x2, 0, z2, Blocks.DARK_OAK_PLANKS);
        b.walls(x1, 1, z1, x2, 10, z2, wall);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 10, z2 - 1);
        b.fill(x1, 11, z1, x2, 11, z2, cream);
        b.fill(x1, 10, z1, x2, 10, z1, trim);
        b.fill(x1, 10, z2, x2, 10, z2, trim);
        ellipsoid(b, c, 11, 18, 11, 7, 11, Blocks.RED_CONCRETE.defaultBlockState(), true, 1);
        b.set(c, 19, 18, Blocks.GOLD_BLOCK);
        b.set(c, 20, 18, Blocks.LIGHTNING_ROD);
        for (int x = x1 + 3; x <= x2 - 3; x += 4) {
            if (Math.abs(x - c) <= 1) continue;
            b.set(x, 7, z1, connected(Blocks.YELLOW_STAINED_GLASS_PANE, Direction.Axis.X));
            b.set(x, 8, z1, connected(Blocks.YELLOW_STAINED_GLASS_PANE, Direction.Axis.X));
        }
        windowsZ(b, 8, 28, 3, x1, 5, Blocks.YELLOW_STAINED_GLASS_PANE);
        windowsZ(b, 8, 28, 3, x2, 5, Blocks.YELLOW_STAINED_GLASS_PANE);
        // portico with columns
        b.fill(c - 6, 0, 0, c + 6, 0, 3, cream);
        for (int x = c - 6; x <= c + 6; x += 3) b.fill(x, 1, 1, x, 8, 1, Blocks.QUARTZ_PILLAR);
        b.fill(c - 7, 9, 0, c + 7, 9, 3, cream);
        b.fill(c - 6, 10, 1, c + 6, 10, 2, Blocks.SMOOTH_SANDSTONE_SLAB);
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 1, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.set(c - 3, 6, 3, banner(Blocks.RED_WALL_BANNER, Direction.NORTH));
        b.set(c + 3, 6, 3, banner(Blocks.RED_WALL_BANNER, Direction.NORTH));
        b.hanging(c, 8, 2, Blocks.LANTERN);
        npc(b, "guard", c - 4, 1, 2);
        npc(b, "guard", c + 4, 1, 2);
        // rows of seats facing the stage, a centre aisle
        carpet(b, c - 1, 1, z1 + 1, c + 1, 18, Blocks.RED_CARPET);
        for (int z = 8; z <= 16; z += 2)
            for (int x = x1 + 5; x <= x2 - 5; x++) {
                if (Math.abs(x - c) <= 1) continue;
                chair(b, x, 1, z, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
            }
        npc(b, "patron", 8, 1, 9);
        npc(b, "patron", 23, 1, 13);
        npc(b, "patron", 11, 1, 15);
        // the stage: curtained backdrop, wings, the auctioneer's podium and the lot on show
        b.fill(x1 + 3, 1, 20, x2 - 3, 1, 25, Blocks.SPRUCE_PLANKS);
        b.fill(x1 + 3, 2, 26, x2 - 3, 9, 26, Blocks.RED_WOOL);
        b.fill(x1 + 3, 2, 20, x1 + 4, 9, 20, Blocks.RED_WOOL);
        b.fill(x2 - 4, 2, 20, x2 - 3, 9, 20, Blocks.RED_WOOL);
        b.fill(x1 + 5, 9, 20, x2 - 5, 9, 20, Blocks.RED_WOOL);
        stairsUp(b, c + 1, 1, 19, Direction.SOUTH, 1, Blocks.DARK_OAK_STAIRS, 3, null);
        b.set(c, 2, 21, facing(Blocks.LECTERN, Direction.NORTH));
        b.set(c - 3, 2, 23, Blocks.GOLD_BLOCK);
        b.set(c + 3, 2, 23, Blocks.AMETHYST_CLUSTER);
        b.hanging(c, 10, 23, Blocks.LANTERN);
        b.hanging(c, 10, 13, Blocks.LANTERN);
        b.hanging(8, 10, 12, Blocks.LANTERN);
        b.hanging(24, 10, 12, Blocks.LANTERN);
        npc(b, "merchant", c, 2, 22);
        // private boxes along the side walls (ladders behind the stage wings)
        for (boolean west : new boolean[]{true, false}) {
            int a = west ? x1 + 1 : x2 - 3, e = west ? x1 + 3 : x2 - 1, inner = west ? e : a, lx = west ? x1 + 2 : x2 - 2;
            b.fill(a, 6, 9, e, 6, 18, Blocks.DARK_OAK_PLANKS);
            line(b, inner, 7, 9, inner, 18, Blocks.DARK_OAK_FENCE);
            for (int z = 11; z <= 16; z += 3) chair(b, (a + e) / 2, 7, z, Blocks.QUARTZ_STAIRS, west ? Direction.WEST : Direction.EAST);
            b.fill(lx, 1, 20, lx, 6, 20, wall);
            ladder(b, lx, 1, 19, 6, Direction.NORTH, null);
        }
        npc(b, "noble", x1 + 2, 7, 17);
        npc(b, "noble", x2 - 2, 7, 10);
        // backstage: corridor, cells for the lots, the strong room (iron door; only one of three levers opens it)
        b.door(c, 2, 26, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.fill(x1 + 1, 0, 27, x2 - 1, 0, z2 - 1, Blocks.STONE_BRICKS);
        for (int x = 6; x <= 14; x += 4) b.fill(x, 1, 29, x, 4, z2 - 1, wall);
        line(b, x1 + 1, 1, 28, 14, 28, Blocks.IRON_BARS);
        line(b, x1 + 1, 2, 28, 14, 28, Blocks.IRON_BARS);
        line(b, x1 + 1, 3, 28, 14, 28, Blocks.IRON_BARS);
        b.fill(x1 + 1, 4, 28, 14, 4, 28, wall);
        b.fill(x1 + 1, 5, 28, 14, 5, z2 - 1, wall);
        for (int x : new int[]{4, 8, 12}) mark(b, "cell", x, 1, 31);
        npc(b, "guard", 16, 1, 28);
        b.fill(18, 1, 28, x2 - 1, 4, 28, wall);
        b.fill(18, 1, 29, 18, 4, z2 - 1, wall);
        b.fill(18, 5, 28, x2 - 1, 5, z2 - 1, wall);
        b.door(24, 1, 28, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(25, 2, 27, lever(Direction.NORTH));
        b.set(21, 2, 27, lever(Direction.NORTH));
        b.set(28, 3, 27, lever(Direction.NORTH));
        b.chest(20, 1, 31, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.chest(28, 1, 31, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.set(24, 1, 32, Blocks.GOLD_BLOCK);
        b.hanging(24, 4, 30, Blocks.LANTERN);
        b.hanging(10, 10, 27, Blocks.LANTERN);
        mark(b, "treasure", 24, 1, 31);
        b.marker("arc_anchor", c, 1, 2, tag("arc", "auction_house"));
    }

    // ============================================================== Amazon Lily: the Kuja palace

    /** Coiled snake statue (4 high) facing {@code f}. */
    private static void snakeStatue(Builder b, int x, int z, Direction f) {
        b.fill(x - 1, 1, z - 1, x + 1, 1, z + 1, Blocks.GREEN_TERRACOTTA);
        b.set(x, 1, z, Blocks.LIME_TERRACOTTA);
        b.fill(x, 2, z, x, 4, z, Blocks.GREEN_TERRACOTTA);
        int hx = x + f.getStepX(), hz = z + f.getStepZ();
        b.set(hx, 4, hz, Blocks.GREEN_TERRACOTTA);
        b.set(hx, 5, hz, Blocks.GREEN_GLAZED_TERRACOTTA);
        b.set(hx + f.getStepX(), 5, hz + f.getStepZ(), Blocks.YELLOW_CONCRETE);
    }

    /** The warrior empress's palace: arena ring before a terraced hall of pillars and flared roofs, the throne room,
     *  a private bath and a treasury whose door answers only one of three levers. */
    static void kujaPalace(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        Block stone = Blocks.SMOOTH_SANDSTONE, pillar = Blocks.QUARTZ_PILLAR, roofS = Blocks.WARPED_STAIRS, roofB = Blocks.WARPED_PLANKS;
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 34);
        // ---- arena ring in front (z 2..16)
        disc(b, c, 9, 0, 7.5, Blocks.SAND.defaultBlockState());
        ring(b, c, 9, 1, 8, Blocks.CUT_SANDSTONE.defaultBlockState());
        ring(b, c, 9, 2, 8, Blocks.SANDSTONE_WALL.defaultBlockState());
        b.clear(c - 1, 1, 16, c + 1, 2, 17);
        b.clear(c - 1, 1, 1, c + 1, 2, 1);
        for (double a = Math.PI / 6; a < Math.PI * 2; a += Math.PI / 3) {
            if (Math.sin(a) > 0.9) continue;
            int px = c + (int) Math.round(Math.cos(a) * 9.5), pz = 9 + (int) Math.round(Math.sin(a) * 9.5);
            lampPost(b, px, 1, pz, Blocks.BAMBOO_FENCE, 2, Blocks.LANTERN);
        }
        npc(b, "kuja_warrior", c - 3, 1, 9);
        npc(b, "kuja_warrior", c + 3, 1, 9);
        npc(b, "snakeneck_lancer", c, 1, 9); // the Snakeneck tribe serve the Kuja at the gate
        // training targets on the flanks
        for (int z = 4; z <= 14; z += 5) {
            b.set(2, 1, z, Blocks.TARGET);
            b.set(w - 3, 1, z, Blocks.TARGET);
        }
        // ---- terrace (y 1..2) and the palace hall (x 8..36, z 20..42)
        b.fill(6, 1, 18, 38, 2, 44, Blocks.CUT_SANDSTONE);
        stairsUp(b, c + 2, 1, 16, Direction.SOUTH, 2, Blocks.SANDSTONE_STAIRS, 5, Blocks.CUT_SANDSTONE);
        snakeStatue(b, c - 5, 16, Direction.NORTH);
        snakeStatue(b, c + 5, 16, Direction.NORTH);
        int x1 = 8, x2 = 36, z1 = 20, z2 = 42, y0 = 2;
        b.fill(x1, y0, z1, x2, y0, z2, Blocks.POLISHED_DIORITE);
        b.walls(x1, y0 + 1, z1 + 1, x2, y0 + 8, z2, stone);
        b.clear(x1 + 1, y0 + 1, z1 + 2, x2 - 1, y0 + 8, z2 - 1);
        // open colonnade along the front
        for (int x = x1; x <= x2; x += 4) b.fill(x, y0 + 1, z1, x, y0 + 8, z1, pillar);
        b.clear(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 7, z1 + 1);
        for (int x = x1; x <= x2; x += 4) b.fill(x, y0 + 1, z1 + 1, x, y0 + 7, z1 + 1, pillar);
        b.fill(x1, y0 + 9, z1, x2, y0 + 9, z2, Blocks.CUT_SANDSTONE);
        flaredRoof(b, x1, z1, x2, z2, y0 + 10, roofS, Blocks.WARPED_SLAB, roofB);
        b.fill(c - 4, y0 + 1, z1 + 1, c + 4, y0 + 1, z1 + 1, Blocks.AIR);
        for (int[] o : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) b.set(o[0], y0 + 9, o[1], Blocks.GOLD_BLOCK);
        // front wall with a grand doorway behind the colonnade
        b.fill(x1 + 1, y0 + 1, z1 + 2, x2 - 1, y0 + 8, z1 + 2, stone);
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, y0 + 1, z1 + 2, Blocks.WARPED_DOOR, Direction.NORTH);
        windowsX(b, x1 + 3, x2 - 3, y0 + 3, z1 + 2, 6, Blocks.LIME_STAINED_GLASS_PANE);
        // throne room (middle), snake-scale floor, raised throne
        for (int x = 15; x <= 29; x++) for (int z = z1 + 3; z <= z2 - 1; z++) b.set(x, y0, z, ((x + z) & 1) == 0 ? Blocks.GREEN_TERRACOTTA : Blocks.LIME_TERRACOTTA);
        b.fill(14, y0 + 1, z1 + 3, 14, y0 + 8, z2 - 1, stone);
        b.fill(30, y0 + 1, z1 + 3, 30, y0 + 8, z2 - 1, stone);
        b.clear(14, y0 + 1, 28, 14, y0 + 3, 29);
        b.clear(30, y0 + 1, 36, 30, y0 + 3, 37);
        for (int z = 26; z <= 38; z += 4) {
            b.fill(16, y0 + 1, z, 16, y0 + 8, z, pillar);
            b.fill(28, y0 + 1, z, 28, y0 + 8, z, pillar);
        }
        b.fill(c - 3, y0 + 1, 38, c + 3, y0 + 1, 41, Blocks.GOLD_BLOCK);
        b.fill(c - 2, y0 + 2, 40, c + 2, y0 + 2, 41, Blocks.PURPUR_BLOCK);
        b.set(c, y0 + 3, 40, stair(Blocks.PURPUR_STAIRS, Direction.SOUTH, Half.BOTTOM));
        b.set(c, y0 + 4, 41, Blocks.PURPUR_PILLAR);
        snakeStatue(sub(b, 0, y0, 0, w, d, Rotation.NONE), c - 5, 40, Direction.NORTH);
        snakeStatue(sub(b, 0, y0, 0, w, d, Rotation.NONE), c + 5, 40, Direction.NORTH);
        carpet(b, c - 1, y0 + 1, z1 + 3, c + 1, 37, Blocks.PURPLE_CARPET);
        for (int z = 27; z <= 37; z += 5) b.hanging(c, y0 + 8, z, Blocks.LANTERN);
        npc(b, "elder", c - 1, y0 + 2, 38);
        npc(b, "kuja_warrior", 19, y0 + 1, 30);
        npc(b, "kuja_warrior", 25, y0 + 1, 34);
        // west wing: the private bath
        b.fill(9, y0 - 1, 25, 13, y0, 36, Blocks.WATER);
        b.fill(9, y0 - 2, 25, 13, y0 - 2, 36, Blocks.SMOOTH_QUARTZ);
        b.fill(9, y0 + 1, 37, 13, y0 + 1, 37, Blocks.SMOOTH_QUARTZ);
        b.set(11, y0 + 1, 24, Blocks.CAMPFIRE);
        b.set(11, y0, 24, Blocks.SMOOTH_QUARTZ);
        for (int x = 9; x <= 13; x += 2) b.set(x, y0 + 1, 23, Blocks.POTTED_FERN);
        b.hanging(11, y0 + 8, 30, Blocks.LANTERN);
        b.fill(9, y0 + 1, 39, 13, y0 + 1, 41, Blocks.PINK_CARPET);
        b.bed(10, y0 + 1, 40, Blocks.PINK_BED, Direction.WEST);
        npc(b, "kuja_warrior", 12, y0 + 1, 39);
        // east wing: kitchen and the treasury (iron door, three levers — one is real)
        kitchenX(b, 31, y0 + 1, z2 - 1, Direction.NORTH, Buildings.LOOT_TAVERN);
        table(b, 33, y0 + 1, 37, Blocks.BAMBOO_SLAB);
        table(b, 34, y0 + 1, 37, Blocks.BAMBOO_SLAB);
        npc(b, "cook", 32, y0 + 1, 35);
        b.fill(31, y0 + 1, 31, 35, y0 + 5, 31, stone);
        b.fill(31, y0 + 6, 23, 35, y0 + 6, 31, stone);
        b.door(33, y0 + 1, 31, Blocks.IRON_DOOR, Direction.SOUTH);
        b.set(34, y0 + 2, 32, lever(Direction.SOUTH));
        b.set(31, y0 + 2, 32, lever(Direction.SOUTH));
        b.set(35, y0 + 4, 32, lever(Direction.SOUTH));
        b.fill(31, y0 + 1, 23, 35, y0 + 5, 23, stone);
        b.chest(31, y0 + 1, 24, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(35, y0 + 1, 24, Direction.WEST, Buildings.LOOT_TREASURE);
        for (int x = 31; x <= 35; x += 2) b.set(x, y0 + 1, 26, Blocks.GOLD_BLOCK);
        b.set(33, y0 + 1, 28, Blocks.EMERALD_BLOCK);
        b.hanging(33, y0 + 5, 25, Blocks.LANTERN);
        b.set(33, y0 + 6, 25, stone);
        mark(b, "treasure", 33, y0 + 1, 24);
        b.hanging(33, y0 + 8, 36, Blocks.LANTERN);
        b.marker("arc_anchor", c, 1, 9, tag("arc", "warrior_isle"));
    }

    private GrandLineLandmarks() {}
}
