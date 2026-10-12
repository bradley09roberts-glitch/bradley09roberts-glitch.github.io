package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.world.Island;
import com.grandline.world.build.Arch;
import com.grandline.world.build.Arch.Mats;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Buildings;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import static com.grandline.world.landmark.LandmarkKit.*;
import static com.grandline.world.landmark.LandmarksC.*;

/** Set pieces of the second half of Paradise and the Calm Belt: Water 7, Enies Lobby, Thriller Bark, Kuraigana, Sabaody, Amazon Lily. */
final class GrandLineLandmarks {

    // ============================================================== Water 7: Galley-La dock

    /** Galley-La's Dock One on a stone quay at sea level: the numbered dock gate over the launch channel, a hull on the
     *  slipway under a gantry crane, a second crane over the channel, the lumber yard and sawmill shed, and the company
     *  headquarters (salmon stucco, terracotta roofs, a four-storey centre pavilion with a portico, balcony and flag);
     *  the president's office on the third floor hides the old warship plans behind a bookcase door. */
    static void galleyLaDocks(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 45 x 41: y=0 is the sea surface, the quay deck is y=1 (feet at y=2)
        Mats m = Mats.of(BuildStyle.VENETIAN, b.seed);
        m.wall = S(ArchBlocks.SALMON_PLASTER);
        m.upperWall = m.wall;
        m.frame = S(ArchBlocks.WHITE_PLASTER);
        m.trim = S(ArchBlocks.WHITE_PLASTER);
        m.base = S(ArchBlocks.HARBOUR_STONE);
        m.floor = S(Blocks.SPRUCE_PLANKS);
        m.shutter = ArchBlocks.GREEN_SHUTTER.get();
        m.planters = true;
        m.door = S(Blocks.SPRUCE_DOOR);
        roofTiles(m, ArchBlocks.TERRACOTTA_ROOF_TILES, ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS, ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.LOW_GABLE;
        BlockState quay = S(ArchBlocks.HARBOUR_STONE), water = S(Blocks.WATER), planks = S(Blocks.SPRUCE_PLANKS);
        BlockState beamY = log(ArchBlocks.DARK_BEAM, Direction.Axis.Y), beamX = log(ArchBlocks.DARK_BEAM, Direction.Axis.X), beamZ = log(ArchBlocks.DARK_BEAM, Direction.Axis.Z);
        // ---- the quay body; the sea front, the launch channel and the slipway are cut out of it
        b.clear(0, 2, 0, w - 1, 46, d - 1);
        b.fill(0, -8, 0, w - 1, -8, d - 1, Blocks.STONE);
        b.fill(0, -7, 0, w - 1, 1, d - 1, quay);
        for (int x = 0; x < w; x++)
            for (int z = 4; z < d; z++) if (b.rand(x * 31 + z * 17, 7) == 0) b.set(x, 1, z, Blocks.POLISHED_ANDESITE);
        b.fill(0, -7, 0, w - 1, 0, 3, water);
        b.clear(0, 1, 0, w - 1, 1, 3);
        b.fill(17, -7, 4, 27, 0, 25, water);
        b.clear(17, 1, 4, 27, 1, 25);
        // slipway (x 2..12): stepped down from the yard into the sea, two timber ways along it
        for (int z = 4; z <= 28; z++) {
            int top = slipTop(z);
            b.clear(2, top + 1, z, 12, 1, z);
            if (top < 0) b.fill(2, top + 1, z, 12, 0, z, water);
            b.set(4, top, z, log(ArchBlocks.OAK_BEAM, Direction.Axis.Z));
            b.set(10, top, z, log(ArchBlocks.OAK_BEAM, Direction.Axis.Z));
        }
        b.marker("ship_spawn", 22, 0, 14, new CompoundTag());
        b.marker("dock", 15, 2, 9, new CompoundTag());
        // ---- the hull on the stocks: planked forward, still only ribs aft
        int kx = 7;
        for (int z = 9; z <= 27; z++) {
            int hb = z == 9 ? 0 : z <= 11 ? 1 : z <= 13 ? 2 : 3, top = slipTop(z);
            boolean framed = z > 20, rib = z % 3 == 0;
            if (z % 4 == 2) {
                b.fill(kx, top + 1, z, kx, 0, z, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
                for (int s : new int[]{-2, 2}) b.fill(kx + s, top + 1, z, kx + s, 1, z, Blocks.SPRUCE_FENCE);
            }
            b.set(kx, 1, z, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
            for (int dx = -hb + 1; dx <= hb - 1; dx++) if (!framed || rib) b.set(kx + dx, 2, z, planks);
            for (int y = 2; y <= 5; y++) {
                int sx = y == 2 ? hb - 1 : hb;
                if (sx <= 0) continue;
                BlockState s = rib ? log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y) : framed ? null : planks;
                if (s == null) continue;
                b.set(kx - sx, y, z, s);
                b.set(kx + sx, y, z, s);
            }
            if (rib && hb > 1) b.fill(kx - hb + 1, 5, z, kx + hb - 1, 5, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
            if (!framed && hb > 0) {
                b.set(kx - hb, 6, z, slabS(S(Blocks.SPRUCE_SLAB), SlabType.BOTTOM));
                b.set(kx + hb, 6, z, slabS(S(Blocks.SPRUCE_SLAB), SlabType.BOTTOM));
            }
        }
        b.fill(kx, 2, 9, kx, 7, 9, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        b.set(kx, 6, 8, log(Blocks.DARK_OAK_LOG, Direction.Axis.Z));
        b.fill(kx, 2, 27, kx, 7, 27, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        for (int z = 14; z <= 19; z++) if (z % 3 != 0) b.fill(kx - 2, 5, z, kx + 2, 5, z, slabS(S(Blocks.SPRUCE_SLAB), SlabType.TOP));
        for (int z : new int[]{13, 17, 21}) {
            b.fill(3, slipTop(z) + 1, z, 3, 5, z, Blocks.SCAFFOLDING);
            b.fill(11, slipTop(z) + 1, z, 11, 5, z, Blocks.SCAFFOLDING);
        }
        for (int z = 22; z <= 26; z += 2) Arch.cargo(b, 12, 1, z, z);
        b.set(2, 1, 26, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        npc(b, "shipwright", 3, 1, 24);
        npc(b, "shipwright", 11, 1, 27);
        // gantry crane over the slipway, lifting a rib
        for (int x : new int[]{1, 13}) {
            for (int z : new int[]{17, 19}) b.fill(x, 2, z, x, 14, z, beamY);
            b.fill(x, 15, 17, x, 15, 19, beamZ);
            b.fill(x, 8, 17, x, 8, 19, beamZ);
        }
        b.fill(1, 16, 18, 13, 16, 18, beamX);
        b.fill(6, 15, 18, 8, 15, 18, Blocks.IRON_BLOCK);
        b.fill(7, 10, 18, 7, 14, 18, Blocks.CHAIN);
        b.fill(5, 9, 18, 9, 9, 18, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        // ---- the dock gate with its great number over the channel mouth, the leaves swung open against the walls
        BlockState mas = S(ArchBlocks.PALE_MASONRY);
        for (int[] p : new int[][]{{13, 16}, {28, 31}}) {
            b.fill(p[0], 2, 4, p[1], 16, 6, mas);
            b.fill(p[0], 2, 4, p[1], 3, 6, quay);
            for (int y : new int[]{9, 16}) b.fill(p[0], y, 4, p[1], y, 6, S(ArchBlocks.WHITE_PLASTER));
            for (int x : new int[]{p[0], p[1]}) {
                b.fill(x, -7, 3, x, 2, 3, quay);
                b.fill(x, 3, 3, x, 15, 3, S(ArchBlocks.HARBOUR_STONE_WALL));
            }
            pyramid(b, p[0], 4, p[1], 6, 17, S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS), S(ArchBlocks.TERRACOTTA_ROOF_TILES), null);
            b.set((p[0] + p[1]) / 2, 5, 3, hang(m.lantern));
            b.set((p[0] + p[1]) / 2, 6, 3, S(ArchBlocks.HARBOUR_STONE));
        }
        b.fill(17, 9, 4, 27, 16, 6, mas);
        archX(b, 22, 5, 1, 4, 6, 7);
        b.fill(17, 16, 4, 27, 16, 6, S(ArchBlocks.WHITE_PLASTER));
        // the crest: a blue plaque framed in white with the dock number, under a stepped pediment
        b.fill(18, 17, 4, 26, 21, 5, mas);
        b.fill(19, 12, 3, 25, 22, 3, S(ArchBlocks.HARBOUR_STONE));
        b.fill(20, 13, 3, 24, 21, 3, S(Blocks.BLUE_CONCRETE));
        number(b, "1", 23, 19, 3, Direction.WEST, S(ArchBlocks.WHITE_PLASTER));
        for (int i = 0; i <= 4; i++) {
            int y = 22 + i, a = 18 + i, c = 26 - i;
            b.fill(a, y, 3, c, y, 5, mas);
            if (a < c) {
                b.fill(a, y, 3, a, y, 5, stairS(S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS), Direction.EAST, Half.BOTTOM));
                b.fill(c, y, 3, c, y, 5, stairS(S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS), Direction.WEST, Half.BOTTOM));
            }
        }
        flag(b, 22, 27, 4, 4, Direction.EAST, 3, S(Blocks.BLUE_WOOL), S(Blocks.WHITE_WOOL));
        for (int x : new int[]{17, 27}) {
            b.fill(x, -3, 7, x, 10, 12, Blocks.DARK_OAK_PLANKS);
            for (int y = -1; y <= 9; y += 4) b.fill(x, y, 7, x, y, 12, beamZ);
            b.fill(x, -3, 7, x, 10, 7, beamY);
        }
        // ---- gantry crane over the channel, a crate on the hook
        for (int x : new int[]{16, 28}) {
            for (int z : new int[]{14, 16}) b.fill(x, 2, z, x, 17, z, beamY);
            b.fill(x, 18, 14, x, 18, 16, beamZ);
            b.fill(x, 9, 14, x, 9, 16, beamZ);
        }
        b.fill(16, 19, 15, 28, 19, 15, beamX);
        b.fill(21, 18, 15, 23, 18, 15, Blocks.IRON_BLOCK);
        b.fill(22, 12, 15, 22, 17, 15, Blocks.CHAIN);
        b.set(22, 11, 15, S(ArchBlocks.CARGO_CRATE));
        // quay furniture: bollards, rope, ladders out of the water, lamps
        bollardsX(b, 33, 41, 2, 4, 4);
        for (int z : new int[]{10, 19, 23}) {
            b.set(16, 2, z, decor(ArchBlocks.BOLLARD, Direction.NORTH));
            b.set(28, 2, z, decor(ArchBlocks.BOLLARD, Direction.NORTH));
        }
        b.set(29, 2, 10, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        b.set(15, 2, 23, decor(ArchBlocks.ROPE_COIL, Direction.EAST));
        for (int[] l : new int[][]{{17, 21, 0}, {27, 21, 1}, {35, 3, 2}}) {
            Direction f = l[2] == 0 ? Direction.EAST : l[2] == 1 ? Direction.WEST : Direction.NORTH;
            b.ladderUp(l[0], -1, l[1], 3, f);
        }
        for (int[] p : new int[][]{{14, 12}, {30, 12}, {14, 26}, {30, 27}, {1, 29}, {43, 29}}) lampPost(b, p[0], 2, p[1], Blocks.IRON_BARS, 3, ArchBlocks.BRASS_LANTERN.get());
        for (int z = 8; z <= 10; z += 2) Arch.cargo(b, 30, 2, z, z + 1);
        b.set(31, 2, 9, S(ArchBlocks.CARGO_CRATE));
        // ---- lumber yard and the open sawmill shed (east)
        b.fill(32, 2, 7, 35, 3, 13, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Z));
        b.fill(38, 2, 7, 42, 2, 13, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        b.fill(38, 3, 8, 42, 3, 8, log(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        b.fill(38, 3, 12, 42, 3, 12, log(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        b.fill(38, 4, 7, 42, 4, 13, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        b.fill(39, 5, 7, 41, 5, 13, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        Mats shed = Mats.of(BuildStyle.VENETIAN, b.seed + 7);
        roofTiles(shed, ArchBlocks.TERRACOTTA_ROOF_TILES, ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS, ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        shed.roofKind = Arch.RoofKind.LOW_GABLE;
        BlockState wb = log(ArchBlocks.WEATHERED_BEAM, Direction.Axis.Y), wp = S(ArchBlocks.WEATHERED_PLANKS);
        b.fill(31, 1, 18, 43, 1, 27, S(ArchBlocks.DOCK_PLANKS));
        b.fill(31, 2, 27, 43, 6, 27, wp);
        b.fill(43, 2, 18, 43, 6, 27, wp);
        b.fill(31, 2, 23, 31, 6, 27, wp);
        for (int x : new int[]{31, 37, 43}) for (int z : new int[]{18, 27}) b.fill(x, 2, z, x, 6, z, wb);
        b.fill(31, 6, 18, 43, 6, 18, log(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        Arch.gable(b, shed, 31, 18, 43, 27, 7, true, 1, wp);
        b.fill(32, 7, 22, 42, 7, 22, log(ArchBlocks.WEATHERED_BEAM, Direction.Axis.X));
        for (int x : new int[]{34, 40}) Arch.window(b, shed, x, 3, 27, Direction.SOUTH, 2, false);
        Arch.window(b, shed, 43, 3, 22, Direction.EAST, 2, false);
        for (int x = 33; x <= 41; x += 2) b.set(x, 2, 26, Blocks.STONECUTTER);
        b.set(33, 2, 21, Blocks.CRAFTING_TABLE);
        b.set(34, 2, 21, Blocks.SMITHING_TABLE);
        b.set(39, 2, 21, Blocks.GRINDSTONE);
        b.set(40, 2, 21, Blocks.ANVIL);
        b.chest(42, 2, 20, Direction.WEST, Buildings.LOOT_SMITH);
        b.barrel(42, 2, 24, Buildings.LOOT_WAREHOUSE);
        b.barrel(42, 2, 25, Buildings.LOOT_WAREHOUSE);
        b.set(32, 2, 24, S(ArchBlocks.CARGO_CRATE));
        b.set(32, 3, 24, decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
        b.fill(35, 2, 24, 39, 2, 24, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        b.hanging(34, 6, 22, m.lantern.getBlock());
        b.hanging(40, 6, 22, m.lantern.getBlock());
        npc(b, "shipwright", 37, 2, 22);
        npc(b, "dockworker", 36, 2, 15);
        npc(b, "dockworker", 20, 2, 27);
        // ---- company headquarters: a three-storey range with a four-storey centre pavilion facing the channel
        int hx1 = 4, hx2 = 40, hz1 = 31, hz2 = 39, px1 = 17, px2 = 27, pz1 = 29;
        storeys(b, m, hx1, 1, hz1, hx2, hz2, 3, 4, planks);
        Arch.gable(b, m, hx1, hz1, hx2, hz2, 14, true, 1, m.wall);
        Arch.chimney(b, m, 8, 37, 14, 18, true);
        Arch.chimney(b, m, 36, 37, 14, 18, false);
        storeys(b, m, px1, 1, pz1, px2, hz2, 4, 4, S(Blocks.POLISHED_ANDESITE));
        int top = Arch.hip(b, m, px1, pz1, px2, hz2, 18, 1);
        flag(b, 22, top + 1, 34, 5, Direction.EAST, 3, S(Blocks.BLUE_WOOL), S(Blocks.WHITE_WOOL));
        for (int fy : new int[]{1, 5, 9}) {
            int y = fy + 2;
            winX(b, m, 6, 15, y, hz1, Direction.NORTH, 3, 2, true, -9, 0);
            winX(b, m, 29, 38, y, hz1, Direction.NORTH, 3, 2, true, -9, 0);
            for (int x : new int[]{6, 9, 12, 15, 19, 22, 25, 29, 32, 35, 38}) Arch.window(b, m, x, y, hz2, Direction.SOUTH, 2, true);
            winZ(b, m, 33, 36, y, hx1, Direction.WEST, 3, 2, true, -9, 0);
            winZ(b, m, 33, 36, y, hx2, Direction.EAST, 3, 2, true, -9, 0);
            if (fy > 1) for (int x : new int[]{19, 25}) Arch.window(b, m, x, y, pz1, Direction.NORTH, 2, false);
        }
        for (int x : new int[]{19, 22, 25}) {
            if (x != 22) Arch.window(b, m, x, 15, pz1, Direction.NORTH, 2, false);
            Arch.window(b, m, x, 15, hz2, Direction.SOUTH, 2, false);
        }
        Arch.window(b, m, 22, 7, pz1, Direction.NORTH, 2, false);
        // the portico: two columns carry the third-floor balcony over the triple doors
        for (int x = 21; x <= 23; x++) b.bigDoor(x, 2, pz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        for (int x : new int[]{20, 24}) {
            b.set(x, 2, 28, S(ArchBlocks.HARBOUR_STONE));
            b.fill(x, 3, 28, x, 8, 28, log(ArchBlocks.MARBLE_COLUMN, Direction.Axis.Y));
        }
        b.fill(19, 9, 27, 25, 9, 28, S(ArchBlocks.WHITE_PLASTER));
        b.fill(19, 8, 27, 25, 8, 27, stairS(S(ArchBlocks.HARBOUR_STONE_STAIRS), Direction.SOUTH, Half.TOP));
        line(b, 19, 10, 27, 25, 27, Blocks.IRON_BARS);
        for (int x : new int[]{19, 25}) b.set(x, 10, 28, connect(Blocks.IRON_BARS, true, false, true, false));
        b.door(22, 10, pz1, Blocks.SPRUCE_DOOR, Direction.NORTH);
        b.hanging(22, 8, 28, m.lantern.getBlock());
        for (int x = 20; x <= 24; x++) b.set(x, 16, 28, banner(Blocks.BLUE_WALL_BANNER, Direction.NORTH));
        // interior doorways between the pavilion and the wings on each floor
        for (int fy : new int[]{1, 5, 9}) for (int x : new int[]{px1, px2}) b.clear(x, fy + 1, 35, x, fy + 3, 35);
        // stairs: three flights up the back of the pavilion
        BlockState sp = S(Blocks.SPRUCE_STAIRS);
        flight(b, sp, 19, 2, 37, Direction.EAST, 3, 2, planks);
        flight(b, sp, 25, 6, 38, Direction.WEST, 3, 2, planks);
        flight(b, sp, 19, 10, 37, Direction.EAST, 3, 2, planks);
        for (int fy : new int[]{5, 13}) {
            line(b, 18, fy + 1, 36, 21, 36, Blocks.SPRUCE_FENCE);
        }
        line(b, 23, 10, 36, 26, 36, Blocks.SPRUCE_FENCE);
        // ground floor: reception (centre), drafting hall (west), model workshop (east)
        for (int x = 20; x <= 24; x++) b.set(x, 2, 33, decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.set(18, 2, 31, decor(ArchBlocks.PLANK_BENCH, Direction.EAST));
        b.set(26, 2, 31, decor(ArchBlocks.PLANK_BENCH, Direction.WEST));
        b.set(18, 2, 33, Blocks.POTTED_FERN);
        b.hanging(22, 4, 31, m.lantern.getBlock());
        for (int x : new int[]{7, 10, 13}) {
            b.set(x, 2, 34, Blocks.CARTOGRAPHY_TABLE);
            b.set(x + 1, 2, 34, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
            b.set(x + 1, 3, 34, Blocks.WHITE_CARPET);
            b.set(x + 1, 2, 35, stairS(sp, Direction.SOUTH, Half.BOTTOM));
        }
        for (int x : new int[]{7, 8, 10, 11, 13, 14}) b.fill(x, 2, 38, x, 4, 38, Blocks.BOOKSHELF);
        b.fill(5, 2, 32, 5, 3, 33, Blocks.BARREL);
        b.hanging(10, 4, 35, m.lantern.getBlock());
        npc(b, "shipwright", 11, 2, 36);
        b.set(29, 2, 38, Blocks.CRAFTING_TABLE);
        b.set(30, 2, 38, Blocks.LOOM);
        b.set(31, 2, 38, Blocks.SMITHING_TABLE);
        for (int x = 33; x <= 37; x++) b.set(x, 2, 35, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.fill(34, 3, 35, 36, 3, 35, slabS(S(Blocks.SPRUCE_SLAB), SlabType.BOTTOM));
        b.set(35, 4, 35, Blocks.SPRUCE_FENCE);
        b.set(35, 5, 35, Blocks.WHITE_CARPET);
        for (int z = 32; z <= 34; z++) Arch.cargo(b, 39, 2, z, z);
        b.hanging(34, 4, 33, m.lantern.getBlock());
        // second floor: offices (west), waiting landing (centre), archive (east)
        for (int x : new int[]{8, 12}) {
            b.set(x, 6, 33, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
            b.set(x, 7, 33, candles(Blocks.CANDLE, 1));
            b.set(x, 6, 34, stairS(sp, Direction.SOUTH, Half.BOTTOM));
            b.set(x + 1, 6, 33, facing(Blocks.LECTERN, Direction.SOUTH));
        }
        b.chest(15, 6, 38, Direction.NORTH, Buildings.LOOT_SHOP);
        b.fill(5, 6, 38, 6, 7, 38, Blocks.BOOKSHELF);
        b.hanging(10, 8, 35, m.lantern.getBlock());
        npc(b, "merchant", 10, 6, 36);
        b.set(19, 6, 31, decor(ArchBlocks.PLANK_BENCH, Direction.SOUTH));
        b.set(20, 6, 31, decor(ArchBlocks.PLANK_BENCH, Direction.SOUTH));
        b.set(24, 6, 31, Blocks.POTTED_BAMBOO);
        b.hanging(22, 8, 33, m.lantern.getBlock());
        for (int z : new int[]{33, 37}) for (int x = 30; x <= 37; x++) if (x != 34) b.fill(x, 6, z, x, 7, z, Blocks.BOOKSHELF);
        b.fill(38, 6, 32, 39, 6, 32, Blocks.BARREL);
        b.hanging(34, 8, 35, m.lantern.getBlock());
        // third floor: the president's office (west), the plans vault behind a bookcase door, the board room (east)
        carpet(b, 9, 10, 32, 16, 38, Blocks.BLUE_CARPET);
        b.set(12, 10, 35, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(13, 10, 35, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(12, 11, 35, Blocks.POTTED_FERN);
        b.set(13, 11, 35, candles(Blocks.CANDLE, 2));
        b.set(12, 10, 36, stairS(S(Blocks.DARK_OAK_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.set(16, 10, 38, Blocks.POTTED_BAMBOO);
        b.fill(8, 10, 32, 8, 12, 38, Blocks.BOOKSHELF);
        b.door(8, 10, 35, Blocks.SPRUCE_DOOR, Direction.EAST);
        b.chest(6, 10, 35, Direction.EAST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 6, 10, 35);
        b.set(6, 10, 33, facing(Blocks.LECTERN, Direction.EAST));
        b.set(5, 10, 37, Blocks.CARTOGRAPHY_TABLE);
        b.hanging(6, 12, 35, Blocks.LANTERN);
        b.hanging(12, 12, 34, m.lantern.getBlock());
        npc(b, "noble", 14, 10, 33);
        npc(b, "guard", 16, 10, 34);
        longTableX(b, 30, 37, 10, 35, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        b.chest(39, 10, 38, Direction.WEST, Buildings.LOOT_WAREHOUSE);
        b.hanging(33, 12, 35, m.lantern.getBlock());
        b.hanging(22, 12, 33, m.lantern.getBlock());
        // the lookout loft at the top of the pavilion
        b.set(22, 14, 33, Blocks.CARTOGRAPHY_TABLE);
        b.set(21, 14, 33, stairS(sp, Direction.WEST, Half.BOTTOM));
        b.set(23, 14, 33, stairS(sp, Direction.EAST, Half.BOTTOM));
        b.set(25, 16, 34, LandmarkKit.bellCeiling());
        b.hanging(22, 16, 31, m.lantern.getBlock());
        b.marker("legend", 22, 2, 27, tag("story", "galley_la_docks"));
    }

    /** Surface height of the Galley-La slipway at row z (it steps down into the sea toward z=4). */
    private static int slipTop(int z) {
        return z >= 23 ? 0 : z >= 17 ? -1 : z >= 11 ? -2 : -3;
    }

    // ============================================================== Enies Lobby: the Gates of Justice

    /** The colossal sea gate: two pale masonry piers with marble pilasters and pyramid caps carry a lintel with the
     *  World Government emblem under a stepped crown; the iron-studded leaves stand almost closed over a drag current.
     *  Each pier has a landing stage, a guard room and stairs up through five floors (armoury, winch room, records) to
     *  the gate control room at the top of the west pier. */
    static void gatesOfJustice(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d; // 47 x 17, y=0 is the sea surface
        b.clear(0, 1, 0, w - 1, 56, d - 1);
        b.fill(0, -9, 0, w - 1, -9, d - 1, Blocks.STONE);
        b.fill(0, -8, 0, w - 1, 0, d - 1, Blocks.WATER);
        BlockState mas = S(ArchBlocks.PALE_MASONRY), masSt = S(ArchBlocks.PALE_MASONRY_STAIRS), hs = S(ArchBlocks.HARBOUR_STONE);
        BlockState gold = S(Blocks.GOLD_BLOCK), rod = S(Blocks.LIGHTNING_ROD);
        justicePier(b, 1, 12, true);
        justicePier(b, 34, 45, false);
        // the lintel between the piers, its emblem and the stepped crown
        b.fill(13, 31, 3, 33, 40, 13, mas);
        for (int y : new int[]{31, 39}) {
            b.fill(13, y, 3, 33, y, 3, hs);
            b.fill(13, y, 13, 33, y, 13, hs);
        }
        for (int x = 12; x <= 34; x++) {
            b.set(x, 40, 2, stairS(masSt, Direction.SOUTH, Half.TOP));
            b.set(x, 40, 14, stairS(masSt, Direction.NORTH, Half.TOP));
        }
        for (int z : new int[]{3, 4, 5, 6, 10, 11, 12, 13}) {
            b.set(13, 30, z, stairS(masSt, Direction.WEST, Half.TOP));
            b.set(33, 30, z, stairS(masSt, Direction.EAST, Half.TOP));
        }
        for (int z : new int[]{2, 14}) {
            for (int dx = -4; dx <= 4; dx++)
                for (int dy = -4; dy <= 4; dy++) {
                    double r = Math.sqrt(dx * dx + dy * dy);
                    if (r > 4.4) continue;
                    BlockState s = r > 3.4 ? gold : (dx == 0 || dy == 0) && r < 3.2 ? gold : Math.abs(dx) == Math.abs(dy) && r < 2.5 ? S(Blocks.WHITE_CONCRETE) : S(Blocks.BLUE_CONCRETE);
                    b.set(23 + dx, 35 + dy, z, s);
                }
        }
        int[][] crown = {{15, 31, 5, 11}, {17, 29, 6, 10}, {19, 27, 7, 9}, {21, 25, 8, 8}};
        for (int i = 0; i < crown.length; i++) {
            int[] c = crown[i];
            int y = 41 + i;
            b.fill(c[0], y, c[2], c[1], y, c[3], mas);
            for (int z = c[2]; z <= c[3]; z++) {
                b.set(c[0], y, z, stairS(masSt, Direction.EAST, Half.BOTTOM));
                b.set(c[1], y, z, stairS(masSt, Direction.WEST, Half.BOTTOM));
            }
        }
        b.set(23, 45, 8, gold);
        b.set(23, 46, 8, rod);
        // the gate leaves: tiled iron-studded slabs in a blackstone frame, a narrow gap between them
        BlockState fr = S(Blocks.POLISHED_BLACKSTONE_BRICKS), iron = S(Blocks.IRON_BLOCK), chain = S(Blocks.CHAIN);
        for (int[] lf : new int[][]{{13, 20}, {26, 33}}) {
            b.fill(lf[0], -8, 7, lf[1], 30, 9, S(Blocks.DEEPSLATE_TILES));
            b.fill(lf[0], -8, 7, lf[0], 30, 9, fr);
            b.fill(lf[1], -8, 7, lf[1], 30, 9, fr);
            for (int y : new int[]{0, 10, 20, 30}) b.fill(lf[0], y, 7, lf[1], y, 9, fr);
            for (int y = 5; y <= 25; y += 10) {
                b.fill(lf[0] + 1, y, 6, lf[1] - 1, y, 6, log(Blocks.CHAIN, Direction.Axis.X));
                b.fill(lf[0] + 1, y, 10, lf[1] - 1, y, 10, log(Blocks.CHAIN, Direction.Axis.X));
            }
            for (int x = lf[0] + 2; x <= lf[1] - 2; x += 3) {
                b.set(x, 15, 6, iron);
                b.set(x, 15, 10, iron);
            }
            int kx = lf[0] == 13 ? 19 : 27;
            for (int z : new int[]{6, 10}) {
                b.set(kx, 16, z, gold);
                b.fill(kx, 14, z, kx, 15, z, chain);
            }
        }
        // the drag current through the gap
        for (int x = 21; x <= 25; x++)
            for (int z = 3; z <= 13; z++) {
                b.set(x, -9, z, Blocks.MAGMA_BLOCK);
                for (int y = -8; y <= 0; y++) b.set(x, y, z, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true));
            }
        b.marker("arc_anchor", 23, 0, 14, tag("arc", "gates_of_justice"));
    }

    /**
     * One gate pier of the Gates of Justice over [x1..x2] x [3..13]: harbour-stone footing, pale masonry shaft with
     * marble pilasters and string courses, slit windows, a cornice and a pyramid cap; inside, five floors seven blocks
     * apart joined by straight flights along alternate walls, a landing stage in front and furnished rooms.
     */
    private static void justicePier(Builder b, int x1, int x2, boolean west) {
        BlockState mas = S(ArchBlocks.PALE_MASONRY), masSt = S(ArchBlocks.PALE_MASONRY_STAIRS), hs = S(ArchBlocks.HARBOUR_STONE);
        BlockState floor = S(Blocks.POLISHED_ANDESITE), st = S(Blocks.STONE_BRICK_STAIRS), bars = S(Blocks.IRON_BARS);
        Mats m = Mats.of(BuildStyle.MARINE, b.seed + x1);
        m.lantern = S(ArchBlocks.BRASS_LANTERN);
        b.fill(x1, -8, 3, x2, 36, 13, mas);
        b.fill(x1, -8, 3, x2, 1, 13, hs);
        int[] fls = {1, 8, 15, 22, 29};
        for (int fy : fls) {
            b.fill(x1 + 1, fy, 4, x2 - 1, fy, 12, floor);
            b.clear(x1 + 1, fy + 1, 4, x2 - 1, fy + 6, 12);
            if (fy > 1) wallsS(b, x1, fy, 3, x2, fy, 13, hs);
        }
        // pilasters and slit windows
        for (int x : new int[]{x1 + 2, x2 - 2})
            for (int z : new int[]{2, 14}) {
                b.fill(x, z == 14 ? -8 : 2, z, x, 2, z, hs);
                b.fill(x, 3, z, x, 35, z, log(ArchBlocks.MARBLE_COLUMN, Direction.Axis.Y));
            }
        int mid = (x1 + x2) / 2;
        for (int fy : fls) {
            for (int z : new int[]{3, 13}) {
                if (z == 3 && fy == 1) continue;
                for (int x = mid; x <= mid + 1; x++) for (int y = fy + 3; y <= fy + 4; y++) b.set(x, y, z, Arch.along(bars, Direction.Axis.X));
            }
            int outer = west ? x1 : x2;
            for (int y = fy + 3; y <= fy + 4; y++) b.set(outer, y, 8, Arch.along(bars, Direction.Axis.Z));
        }
        // cornice and pyramid cap
        for (int x = x1; x <= x2; x++) {
            b.set(x, 36, 2, stairS(masSt, Direction.SOUTH, Half.TOP));
            b.set(x, 36, 14, stairS(masSt, Direction.NORTH, Half.TOP));
        }
        for (int z = 2; z <= 14; z++) {
            int ox = west ? x1 - 1 : x2 + 1;
            if (ox >= 0 && ox < b.w) b.set(ox, 36, z, stairS(masSt, west ? Direction.EAST : Direction.WEST, Half.TOP));
        }
        int tip = pyramid(b, x1, 3, x2, 13, 37, masSt, mas, S(Blocks.GOLD_BLOCK));
        b.set(mid, tip + 1, 8, S(Blocks.LIGHTNING_ROD));
        // landing stage on pilings, the door, lamps
        b.fill(x1, 1, 0, x2, 1, 2, S(ArchBlocks.DOCK_PLANKS));
        for (int x : new int[]{x1, x1 + 4, x2 - 4, x2}) b.fill(x, -8, 0, x, 0, 0, log(ArchBlocks.WEATHERED_BEAM, Direction.Axis.Y));
        b.set(x1 + 2, 2, 0, decor(ArchBlocks.BOLLARD, Direction.NORTH));
        b.set(x2 - 2, 2, 0, decor(ArchBlocks.BOLLARD, Direction.NORTH));
        b.set(x2 - 1, 2, 0, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        lampPost(b, x1, 2, 1, Blocks.IRON_BARS, 2, ArchBlocks.BRASS_LANTERN.get());
        lampPost(b, x2, 2, 1, Blocks.IRON_BARS, 2, ArchBlocks.BRASS_LANTERN.get());
        int dx = x1 + 6;
        b.bigDoor(dx, 2, 3, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(dx + 1, 3, 2, button(Blocks.STONE_BUTTON, Direction.NORTH));
        b.set(dx - 1, 3, 4, button(Blocks.STONE_BUTTON, Direction.SOUTH));
        b.fill(dx - 1, 5, 2, dx + 1, 5, 2, stairS(masSt, Direction.SOUTH, Half.TOP));
        // stairs: even flights along the south wall heading east, odd flights along the north wall heading west
        for (int f = 0; f < 4; f++) {
            int fy = fls[f];
            if (f % 2 == 0) {
                flight(b, st, x1 + 2, fy + 1, 11, Direction.EAST, 6, 2, mas);
                line(b, x1 + 4, fy + 8, 10, x1 + 7, 10, Blocks.IRON_BARS);
            } else {
                flight(b, st, x2 - 2, fy + 1, 5, Direction.WEST, 6, 2, mas);
                line(b, x1 + 4, fy + 8, 6, x1 + 7, 6, Blocks.IRON_BARS);
            }
        }
        for (int fy : fls) b.hanging(mid, fy + 6, 8, m.lantern.getBlock());
        BlockState chair = S(Blocks.STONE_BRICK_STAIRS);
        if (west) {
            // guard room
            b.bed(x1 + 1, 2, 5, Blocks.BLUE_BED, Direction.SOUTH);
            b.bed(x1 + 2, 2, 5, Blocks.BLUE_BED, Direction.SOUTH);
            Arch.tableSet(b, x1 + 8, 2, 7, chair);
            b.set(x1 + 8, 3, 7, candles(Blocks.CANDLE, 2));
            b.chest(x2 - 1, 2, 9, Direction.WEST, Buildings.LOOT_MARINE);
            b.barrel(x2 - 1, 2, 5, Buildings.LOOT_WAREHOUSE);
            npc(b, "marine", x1 + 5, 2, 9);
            // armoury
            b.set(x1 + 1, 9, 8, Blocks.SMITHING_TABLE);
            b.set(x1 + 1, 9, 9, Blocks.GRINDSTONE);
            b.set(x1 + 1, 9, 10, Blocks.ANVIL);
            b.fill(x2 - 1, 9, 7, x2 - 1, 10, 8, Blocks.BARREL);
            b.set(x2 - 1, 9, 10, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
            // winch room: a great drum and chains up to the gate gear
            b.fill(x1 + 4, 17, 8, x1 + 8, 17, 8, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
            for (int x : new int[]{x1 + 3, x1 + 9}) b.fill(x, 16, 8, x, 17, 8, Blocks.POLISHED_BLACKSTONE_BRICKS);
            for (int x : new int[]{x1 + 5, x1 + 7}) b.fill(x, 18, 8, x, 21, 8, Blocks.CHAIN);
            b.set(x1 + 4, 16, 9, Blocks.GRINDSTONE);
            b.set(x1 + 8, 16, 9, Blocks.GRINDSTONE);
            // records
            b.fill(x1 + 1, 23, 7, x1 + 1, 25, 10, Blocks.BOOKSHELF);
            b.set(x1 + 3, 23, 8, facing(Blocks.LECTERN, Direction.EAST));
            b.set(x1 + 7, 23, 8, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
            b.set(x1 + 7, 23, 9, stairS(chair, Direction.SOUTH, Half.BOTTOM));
            b.set(x2 - 1, 23, 8, Blocks.BARREL);
            // the control room: levers on the gate wall, windows to the sea
            for (int x = x1 + 4; x <= x1 + 8; x++) for (int y = 31; y <= 33; y++) b.set(x, y, 3, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
            for (int z = 6; z <= 10; z += 2) b.set(x2 - 1, 31, z, lever(Direction.WEST));
            b.set(x1 + 8, 30, 11, Blocks.GRINDSTONE);
            b.set(x1 + 9, 30, 11, facing(Blocks.PISTON, Direction.UP));
            b.set(x1 + 9, 31, 11, facing(Blocks.OBSERVER, Direction.NORTH));
            b.fill(x1 + 8, 31, 11, x1 + 8, 35, 11, Blocks.CHAIN);
            b.chest(x1 + 2, 30, 11, Direction.EAST, Buildings.LOOT_MARINE);
            b.set(x1 + 5, 30, 9, facing(Blocks.LECTERN, Direction.SOUTH));
            npc(b, "marine_officer", x1 + 6, 30, 8);
            npc(b, "marine", x1 + 8, 2, 1);
        } else {
            // barracks
            for (int x : new int[]{x1 + 1, x1 + 2, x2 - 3, x2 - 2, x2 - 1}) b.bed(x, 2, 5, Blocks.LIGHT_BLUE_BED, Direction.NORTH);
            Arch.tableSet(b, x1 + 6, 2, 8, chair);
            b.barrel(x2 - 1, 2, 9, Buildings.LOOT_WAREHOUSE);
            npc(b, "marine", x1 + 6, 2, 10);
            npc(b, "marine", x1 + 5, 2, 1);
            // stores, mess, signal room, lookout
            for (int z = 7; z <= 9; z++) Arch.cargo(b, x1 + 1, 9, z, z);
            b.fill(x2 - 1, 9, 7, x2 - 1, 10, 9, Blocks.BARREL);
            longTableX(b, x1 + 3, x1 + 8, 16, 8, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_STAIRS, true);
            b.set(x2 - 1, 23, 8, LandmarkKit.bellCeiling());
            b.set(x2 - 1, 24, 8, mas);
            b.set(x1 + 1, 23, 8, Blocks.NOTE_BLOCK);
            b.set(x1 + 1, 23, 9, Blocks.CARTOGRAPHY_TABLE);
            for (int x = x1 + 4; x <= x1 + 8; x++) for (int y = 31; y <= 33; y++) b.set(x, y, 3, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.X));
            b.set(x1 + 6, 30, 9, Blocks.CARTOGRAPHY_TABLE);
            b.set(x1 + 6, 30, 10, stairS(chair, Direction.SOUTH, Half.BOTTOM));
        }
    }

    // ============================================================== Thriller Bark: the haunted mansion

    /** A symmetrical gothic mansion behind a walled graveyard: a three-storey centre under a steep slate gable with a
     *  rose window, two wings with their own gables, round corner turrets and a bell tower with a spire behind. Inside:
     *  entrance hall with a grand stair, the mad surgeon's laboratory (and a frozen vault behind its ice wall), the
     *  banquet hall, a library, the master's chambers, the wedding chapel and the shadow throne hall under the roof. */
    static void hauntedMansion(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        Mats m = Mats.of(BuildStyle.GOTHIC, b.seed);
        m.wall = S(Blocks.DEEPSLATE_BRICKS);
        m.upperWall = m.wall;
        m.frame = log(ArchBlocks.DARK_BEAM, Direction.Axis.Y);
        m.trim = m.frame;
        m.base = S(Blocks.COBBLED_DEEPSLATE);
        m.floor = S(Blocks.DARK_OAK_PLANKS);
        roofTiles(m, ArchBlocks.SLATE_ROOF_TILES, ArchBlocks.SLATE_ROOF_TILE_STAIRS, ArchBlocks.SLATE_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.STEEP;
        m.shutter = null;
        m.planters = false;
        m.lantern = S(Blocks.SOUL_LANTERN);
        m.fence = S(Blocks.DARK_OAK_FENCE);
        BlockState slate = S(ArchBlocks.SLATE_ROOF_TILES), slateSt = S(ArchBlocks.SLATE_ROOF_TILE_STAIRS), dstairs = S(Blocks.DARK_OAK_STAIRS);
        BlockState bkb = S(Blocks.POLISHED_BLACKSTONE_BRICKS), soul = S(Blocks.SOUL_LANTERN);
        site(b, Blocks.PODZOL, Blocks.DIRT, 64);
        patchGround(b, 91, 0, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MOSS_BLOCK, Blocks.MYCELIUM);
        // ---- the iron fence round the graveyard with a wrought-iron gate arch
        for (int x = 0; x < w; x++) {
            if (x >= c - 2 && x <= c + 2) continue;
            boolean pier = x % 6 == 0 || x == c - 3 || x == c + 3;
            if (pier) {
                b.fill(x, 1, 0, x, 3, 0, bkb);
                b.set(x, 4, 0, stand(soul));
            } else {
                b.set(x, 1, 0, connected(Blocks.IRON_BARS, Direction.Axis.X));
                b.set(x, 2, 0, connected(Blocks.IRON_BARS, Direction.Axis.X));
            }
        }
        for (int x : new int[]{0, w - 1})
            for (int z = 1; z <= 15; z++) {
                if (z % 6 == 0) b.fill(x, 1, z, x, 3, z, bkb);
                else {
                    b.set(x, 1, z, connected(Blocks.IRON_BARS, Direction.Axis.Z));
                    b.set(x, 2, z, connected(Blocks.IRON_BARS, Direction.Axis.Z));
                }
            }
        for (int x : new int[]{c - 3, c + 3}) {
            b.fill(x, 1, 0, x, 5, 0, bkb);
            b.set(x, 6, 0, Blocks.WITHER_SKELETON_SKULL);
        }
        line(b, c - 2, 6, 0, c + 2, 0, Blocks.IRON_BARS);
        b.set(c - 3, 6, 0, Blocks.WITHER_SKELETON_SKULL);
        b.hanging(c, 5, 0, Blocks.SOUL_LANTERN);
        // the path and forecourt
        for (int z = 0; z <= 14; z++) for (int x = c - 1; x <= c + 1; x++) b.set(x, 0, z, b.rand(x * 7 + z * 13, 4) == 0 ? Blocks.GRAVEL : Blocks.COBBLED_DEEPSLATE);
        for (int z = 11; z <= 14; z++) for (int x = c - 4; x <= c + 4; x++) b.set(x, 0, z, b.rand(x * 5 + z * 11, 3) == 0 ? Blocks.GRAVEL : Blocks.COBBLED_DEEPSLATE);
        // ---- graveyard: headstones, crosses, graves and dead trees; a mausoleum on the west side
        int[][] trees = {{5, 12}, {39, 10}, {17, 3}};
        for (int[] t : trees) deadTree(b, t[0], 1, t[1], 6 + b.rand(t[0] * 3 + t[1], 3), t[0] * 7 + t[1], Blocks.DARK_OAK_LOG);
        b.set(6, 6, 12, Blocks.COBWEB);
        b.set(38, 7, 10, Blocks.COBWEB);
        int[] gxs = {11, 14, 28, 31, 34, 37, 40};
        for (int gx : gxs)
            for (int gz = 3; gz <= 12; gz += 3) {
                boolean nearTree = false;
                for (int[] t : trees) if (Math.abs(t[0] - gx) <= 2 && Math.abs(t[1] - gz) <= 2) nearTree = true;
                if (nearTree) continue;
                grave(b, gx, gz, b.rand(gx * 13 + gz * 7, 5));
            }
        // mausoleum (x 2..8, z 3..9), door to the east
        Mats mm = Mats.of(BuildStyle.GOTHIC, b.seed + 3);
        roofTiles(mm, ArchBlocks.SLATE_ROOF_TILES, ArchBlocks.SLATE_ROOF_TILE_STAIRS, ArchBlocks.SLATE_ROOF_TILE_SLAB);
        mm.roofKind = Arch.RoofKind.STEEP;
        b.fill(2, 1, 3, 8, 1, 9, S(Blocks.COBBLED_DEEPSLATE));
        wallsS(b, 2, 2, 3, 8, 5, 9, S(Blocks.DEEPSLATE_BRICKS));
        b.clear(3, 2, 4, 7, 5, 8);
        for (int[] p : new int[][]{{2, 3}, {8, 3}, {2, 9}, {8, 9}}) b.fill(p[0], 1, p[1], p[0], 6, p[1], bkb);
        b.fill(3, 6, 4, 7, 6, 8, S(Blocks.DEEPSLATE_TILES));
        Arch.gable(b, mm, 2, 3, 8, 9, 6, true, 1, S(Blocks.DEEPSLATE_BRICKS));
        b.clear(8, 2, 6, 8, 4, 6);
        b.set(9, 1, 6, stairS(S(Blocks.COBBLED_DEEPSLATE_STAIRS), Direction.WEST, Half.BOTTOM));
        b.fill(4, 2, 5, 6, 2, 7, S(Blocks.POLISHED_BLACKSTONE));
        b.fill(4, 3, 6, 6, 3, 6, slabS(S(Blocks.DARK_OAK_SLAB), SlabType.BOTTOM));
        b.set(3, 2, 4, candles(Blocks.PURPLE_CANDLE, 3));
        b.set(3, 2, 8, candles(Blocks.PURPLE_CANDLE, 2));
        b.hanging(5, 5, 6, Blocks.SOUL_LANTERN);
        b.set(5, 4, 9, connected(Blocks.IRON_BARS, Direction.Axis.X));
        npc(b, "ruin_guardian", 7, 2, 6);
        npc(b, "ruin_guardian", 32, 1, 5);
        npc(b, "ruin_guardian", 13, 1, 8);

        // ---- the porch (built first: the centre's front wall overwrites its back)
        b.fill(c - 3, 1, 15, c + 3, 1, 17, S(Blocks.COBBLED_DEEPSLATE));
        for (int x = c - 2; x <= c + 2; x++) b.set(x, 1, 14, stairS(S(Blocks.COBBLED_DEEPSLATE_STAIRS), Direction.SOUTH, Half.BOTTOM));
        for (int x : new int[]{c - 3, c + 3}) b.fill(x, 2, 15, x, 5, 15, m.frame);
        b.fill(c - 3, 6, 15, c + 3, 6, 17, log(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        Mats pm = Mats.of(BuildStyle.GOTHIC, b.seed + 5);
        roofTiles(pm, ArchBlocks.SLATE_ROOF_TILES, ArchBlocks.SLATE_ROOF_TILE_STAIRS, ArchBlocks.SLATE_ROOF_TILE_SLAB);
        pm.roofKind = Arch.RoofKind.GABLE;
        pm.eaveBrackets = false;
        Arch.gable(b, pm, c - 3, 15, c + 3, 17, 7, false, 1, m.wall);
        b.clear(c - 2, 2, 15, c + 2, 5, 15);
        b.hanging(c, 5, 16, Blocks.SOUL_LANTERN);
        // ---- the wings (west x 4..15, east x 29..40), two storeys of five, steep gables facing front and back
        int[][] wings = {{4, 15}, {29, 40}};
        for (int[] wg : wings) {
            storeys(b, m, wg[0], 1, 20, wg[1], 36, 2, 5, S(Blocks.POLISHED_DEEPSLATE));
            Arch.gable(b, m, wg[0], 20, wg[1], 36, 12, false, 1, m.wall);
            int cx = (wg[0] + wg[1]) / 2;
            b.fill(cx, 15, 20, cx + 1, 16, 20, Arch.along(S(Blocks.PURPLE_STAINED_GLASS_PANE), Direction.Axis.X));
            b.fill(cx, 15, 36, cx + 1, 16, 36, Arch.along(S(Blocks.PURPLE_STAINED_GLASS_PANE), Direction.Axis.X));
        }
        Arch.chimney(b, m, 13, 34, 12, 23, true);
        Arch.chimney(b, m, 31, 34, 12, 23, false);
        // ---- the centre (x 15..29, z 18..38): three storeys of five under the tallest gable
        storeys(b, m, 15, 1, 18, 29, 38, 3, 5, S(Blocks.POLISHED_DEEPSLATE));
        Arch.gable(b, m, 15, 18, 29, 38, 17, false, 1, m.wall);
        // rose window in the front gable
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -3; dy <= 3; dy++) {
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r > 3.3) continue;
                b.set(c + dx, 22 + dy, 18, r > 2.4 ? bkb : (dx == 0 || dy == 0) ? S(Blocks.POLISHED_BLACKSTONE) : S(Blocks.PURPLE_STAINED_GLASS));
            }
        // corner turrets with spires
        for (int[] t : new int[][]{{3, 19}, {41, 19}}) {
            b.cylinder(t[0], t[1], 1, 18, 2, Blocks.DEEPSLATE_BRICKS, false);
            b.cylinder(t[0], t[1], 1, 2, 2, Blocks.COBBLED_DEEPSLATE, false);
            for (int y : new int[]{6, 11, 18}) LandmarkKit.ring(b, t[0], t[1], y, 2, bkb);
            eaveRing(b, t[0], t[1], 19, 2.6, slateSt);
            spire(b, t[0], t[1], 20, 1.6, 3, slate, S(Blocks.LIGHTNING_ROD));
            int side = t[0] < c ? -1 : 1;
            for (int y : new int[]{8, 13}) {
                b.set(t[0] + 2 * side, y, t[1], S(Blocks.PURPLE_STAINED_GLASS));
                b.set(t[0], y, t[1] - 2, S(Blocks.PURPLE_STAINED_GLASS));
            }
        }
        // the bell tower behind (x 19..25, z 38..44) with a spiral stair to the belfry
        int tx1 = 19, tx2 = 25, tz1 = 38, tz2 = 44;
        b.fill(tx1, 1, tz1 + 1, tx2, 1, tz2, m.base);
        wallsS(b, tx1, 2, tz1, tx2, 44, tz2, m.wall);
        b.clear(tx1 + 1, 2, tz1 + 1, tx2 - 1, 43, tz2 - 1);
        for (int[] p : new int[][]{{tx1, tz1}, {tx2, tz1}, {tx1, tz2}, {tx2, tz2}}) {
            b.fill(p[0], 2, p[1], p[0], 44, p[1], bkb);
            b.set(p[0], 45, p[1], Blocks.POLISHED_BLACKSTONE_WALL);
            b.set(p[0], 46, p[1], stand(soul));
        }
        for (int y : new int[]{16, 31, 38}) wallsS(b, tx1, y, tz1, tx2, y, tz2, bkb);
        b.fill(tx1 + 2, 2, tz1 + 2, tx2 - 2, 37, tz2 - 2, S(Blocks.POLISHED_DEEPSLATE));
        b.fill(tx1 + 1, 38, tz1 + 1, tx2 - 1, 38, tz2 - 1, m.floor);
        spiral(b, tx1 + 1, tz1 + 1, tx2 - 1, tz2 - 1, 1, 38, dstairs, S(Blocks.DEEPSLATE_TILES));
        b.clear(tx1 + 1, 2, tz1, tx1 + 1, 4, tz1);
        for (int y = 39; y <= 42; y++) {
            for (int x = c - 1; x <= c + 1; x++) {
                b.set(x, y, tz2, Blocks.AIR);
                b.set(x, y, tz1, Blocks.AIR);
            }
            for (int z = 40; z <= 42; z++) {
                b.set(tx1, y, z, Blocks.AIR);
                b.set(tx2, y, z, Blocks.AIR);
            }
        }
        b.fill(tx1 + 1, 44, tz1 + 1, tx2 - 1, 44, tz2 - 1, m.wall);
        b.set(c, 43, 41, LandmarkKit.bellCeiling());
        squareSpire(b, tx1 + 1, tz1 + 1, tx2 - 1, tz2 - 1, 45, 4, slate, slateSt, S(Blocks.LIGHTNING_ROD));
        for (int y = 18; y <= 28; y += 5) {
            b.set(tx1, y, 41, S(Blocks.PURPLE_STAINED_GLASS));
            b.set(tx2, y, 41, S(Blocks.PURPLE_STAINED_GLASS));
        }
        // windows: tall and narrow, deepslate sills
        for (int fy : new int[]{1, 6, 11}) {
            int y = fy + 2;
            for (int x : new int[]{18, 26}) Arch.window(b, m, x, y, 18, Direction.NORTH, 3, false);
            if (fy == 11) Arch.window(b, m, c, y, 18, Direction.NORTH, 3, false);
            for (int x : new int[]{17, 27}) Arch.window(b, m, x, y, 38, Direction.SOUTH, 3, false);
            if (fy == 11) continue;
            for (int x : new int[]{7, 12, 32, 37}) {
                Arch.window(b, m, x, y, 20, Direction.NORTH, 3, false);
                Arch.window(b, m, x, y, 36, Direction.SOUTH, 3, false);
            }
            for (int z : new int[]{25, 29, 33}) {
                Arch.window(b, m, 4, y, z, Direction.WEST, 3, false);
                Arch.window(b, m, 40, y, z, Direction.EAST, 3, false);
            }
        }
        // the front door
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 2, 18, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.fill(c - 2, 5, 18, c + 2, 5, 18, S(Blocks.CHISELED_POLISHED_BLACKSTONE));
        for (int x : new int[]{c - 2, c + 2}) Arch.wallLantern(b, m, x, 3, 18, Direction.NORTH);
        // doorways between centre and wings
        for (int fy : new int[]{1, 6}) for (int x : new int[]{15, 29}) b.clear(x, fy + 1, 27, x, fy + 3, 28);

        // ---- ground floor: entrance hall and grand stair
        carpet(b, c - 1, 2, 19, c + 1, 30, Blocks.PURPLE_CARPET);
        flight(b, dstairs, c + 1, 2, 31, Direction.SOUTH, 4, 3, m.floor);
        for (int z = 32; z <= 34; z++) {
            b.set(c - 2, 7, z, Arch.along(m.fence, Direction.Axis.Z));
            b.set(c + 2, 7, z, Arch.along(m.fence, Direction.Axis.Z));
        }
        line(b, c - 2, 7, 31, c + 2, 31, Blocks.DARK_OAK_FENCE);
        for (int x : new int[]{17, 27}) {
            b.set(x, 2, 20, bkb);
            b.set(x, 3, 20, Blocks.SKELETON_SKULL);
        }
        b.set(16, 2, 30, decor(ArchBlocks.PLANK_BENCH, Direction.EAST));
        b.set(28, 2, 30, decor(ArchBlocks.PLANK_BENCH, Direction.WEST));
        b.hanging(c, 5, 23, Blocks.SOUL_LANTERN);
        b.hanging(c, 5, 28, Blocks.SOUL_LANTERN);
        b.hanging(c, 5, 36, Blocks.SOUL_LANTERN);
        npc(b, "ruin_guardian", 18, 2, 24);
        // laboratory (west wing): operating tables, specimen jars, the ice wall of the cold store
        for (int z : new int[]{23, 27}) {
            b.fill(8, 2, z, 10, 2, z, slabS(S(Blocks.SMOOTH_STONE_SLAB), SlabType.TOP));
            b.fill(8, 3, z, 10, 3, z, Blocks.WHITE_CARPET);
        }
        for (int z = 21; z <= 29; z += 2) {
            b.set(5, 2, z, Blocks.CAULDRON);
            b.set(5, 3, z, z % 4 == 1 ? Blocks.LIME_STAINED_GLASS : Blocks.GREEN_STAINED_GLASS);
        }
        b.set(14, 2, 21, Blocks.BREWING_STAND);
        b.set(14, 2, 22, Blocks.BREWING_STAND);
        b.set(14, 2, 29, facing(Blocks.LECTERN, Direction.WEST));
        b.chest(14, 2, 30, Direction.WEST, Buildings.LOOT_RUIN);
        b.hanging(10, 5, 25, Blocks.LANTERN);
        npc(b, "ruin_guardian", 11, 2, 25);
        b.fill(5, 2, 31, 14, 5, 31, Blocks.PACKED_ICE);
        b.set(10, 2, 31, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.set(10, 3, 31, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.fill(5, 1, 32, 14, 1, 35, Blocks.BLUE_ICE);
        b.fill(8, 2, 33, 12, 2, 35, Blocks.PACKED_ICE);
        b.fill(9, 3, 33, 11, 4, 35, Blocks.ICE);
        b.set(10, 5, 34, Blocks.ZOMBIE_HEAD);
        b.chest(6, 2, 33, Direction.EAST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 6, 2, 33);
        b.set(14, 2, 32, Blocks.SOUL_LANTERN);
        // banquet hall (east wing)
        longTableZ(b, 23, 33, 2, 34, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        for (int x = 30; x <= 39; x++) if (x != 32 && x != 37) b.fill(x, 2, 35, x, 3, 35, Blocks.BOOKSHELF);
        b.set(39, 2, 21, Blocks.BREWING_STAND);
        b.barrel(39, 2, 23, Buildings.LOOT_TAVERN);
        b.fill(39, 2, 24, 39, 2, 26, Blocks.BARREL);
        for (int z = 25; z <= 31; z += 6) b.hanging(34, 5, z, Blocks.SOUL_LANTERN);
        npc(b, "pirate", 31, 2, 26);
        // ---- first floor: wedding chapel (centre), library (west), master's chambers (east)
        flight(b, dstairs, 28, 7, 30, Direction.SOUTH, 4, 2, m.floor);
        for (int z = 31; z <= 33; z++) b.set(26, 12, z, Arch.along(m.fence, Direction.Axis.Z));
        line(b, 26, 12, 30, 28, 30, Blocks.DARK_OAK_FENCE);
        b.fill(c - 2, 7, 19, c + 2, 7, 20, S(Blocks.CHISELED_POLISHED_BLACKSTONE));
        b.set(c, 8, 19, candles(Blocks.PURPLE_CANDLE, 4));
        b.set(c - 2, 8, 19, candles(Blocks.PURPLE_CANDLE, 2));
        b.set(c + 2, 8, 19, candles(Blocks.PURPLE_CANDLE, 2));
        carpet(b, c - 1, 7, 21, c + 1, 30, Blocks.PURPLE_CARPET);
        for (int z = 22; z <= 28; z += 2) {
            for (int x = 16; x <= c - 2; x++) chair(b, x, 7, z, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
            for (int x = c + 2; x <= 26; x++) chair(b, x, 7, z, Blocks.DARK_OAK_STAIRS, Direction.SOUTH);
        }
        b.hanging(c, 10, 25, Blocks.SOUL_LANTERN);
        for (int z = 21; z <= 35; z++) if (z % 4 != 1) b.fill(5, 7, z, 5, 9, z, Blocks.BOOKSHELF);
        b.fill(7, 7, 34, 12, 8, 34, Blocks.BOOKSHELF);
        b.set(10, 7, 27, Blocks.ENCHANTING_TABLE);
        b.set(11, 7, 23, facing(Blocks.LECTERN, Direction.NORTH));
        b.set(12, 7, 30, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(12, 8, 30, candles(Blocks.CANDLE, 3));
        b.hanging(10, 10, 26, Blocks.SOUL_LANTERN);
        npc(b, "ruin_guardian", 8, 7, 30);
        b.bed(35, 7, 33, Blocks.BLACK_BED, Direction.SOUTH);
        b.bed(36, 7, 33, Blocks.BLACK_BED, Direction.SOUTH);
        carpet(b, 31, 7, 22, 38, 32, Blocks.BLACK_CARPET);
        b.chest(38, 7, 22, Direction.WEST, Buildings.LOOT_TREASURE);
        b.set(31, 7, 35, Blocks.ENDER_CHEST);
        b.fill(39, 7, 29, 39, 8, 31, Blocks.BARREL);
        b.set(33, 7, 34, candles(Blocks.CANDLE, 1));
        b.hanging(34, 10, 27, Blocks.SOUL_LANTERN);
        npc(b, "pirate", 33, 7, 25);
        // ---- second floor (centre): the shadow throne hall
        b.fill(c - 2, 12, 19, c + 2, 12, 20, S(Blocks.POLISHED_BLACKSTONE));
        b.set(c, 13, 20, stairS(dstairs, Direction.NORTH, Half.BOTTOM));
        b.fill(c, 13, 19, c, 15, 19, bkb);
        b.set(c - 1, 13, 20, stairS(S(Blocks.POLISHED_BLACKSTONE_STAIRS), Direction.EAST, Half.TOP));
        b.set(c + 1, 13, 20, stairS(S(Blocks.POLISHED_BLACKSTONE_STAIRS), Direction.WEST, Half.TOP));
        carpet(b, c - 1, 12, 21, c + 1, 33, Blocks.PURPLE_CARPET);
        for (int z = 22; z <= 26; z += 4)
            for (int x : new int[]{18, 26}) {
                b.set(x, 12, z, bkb);
                b.set(x, 13, z, candles(Blocks.PURPLE_CANDLE, 3));
            }
        b.hanging(c, 15, 26, Blocks.SOUL_LANTERN);
        npc(b, "pirate_captain", c, 12, 23);
        b.marker("arc_anchor", c, 1, 13, tag("arc", "ghost_ship_island"));
    }

    /** One grave of the Thriller Bark graveyard at (x, z): kind 0 headstone, 1 cross, 2 mound and stone, 3 obelisk, 4 slab. */
    private static void grave(Builder b, int x, int z, int kind) {
        switch (kind) {
            case 0 -> {
                b.set(x, 1, z, Blocks.MOSSY_STONE_BRICK_WALL);
                b.set(x, 2, z, slabS(S(Blocks.STONE_BRICK_SLAB), SlabType.BOTTOM));
                b.set(x, 0, z + 1, Blocks.COARSE_DIRT);
            }
            case 1 -> {
                b.fill(x, 1, z, x, 3, z, Blocks.DARK_OAK_FENCE);
                b.set(x, 2, z, connect(Blocks.DARK_OAK_FENCE, false, true, false, true));
                b.set(x - 1, 2, z, connect(Blocks.DARK_OAK_FENCE, false, true, false, false));
                b.set(x + 1, 2, z, connect(Blocks.DARK_OAK_FENCE, false, false, false, true));
                b.set(x, 0, z + 1, Blocks.PODZOL);
            }
            case 2 -> {
                b.set(x, 1, z, Blocks.COBBLED_DEEPSLATE_WALL);
                b.set(x, 1, z + 1, slabS(S(Blocks.COARSE_DIRT), SlabType.BOTTOM));
                b.set(x, 0, z + 1, Blocks.COARSE_DIRT);
                b.set(x + 1, 1, z, candles(Blocks.CANDLE, 1));
            }
            case 3 -> {
                b.set(x, 1, z, Blocks.POLISHED_BLACKSTONE);
                b.set(x, 2, z, Blocks.POLISHED_BLACKSTONE_WALL);
                b.set(x, 3, z, Blocks.POLISHED_BLACKSTONE_WALL);
            }
            default -> {
                b.set(x, 1, z, Blocks.STONE_BRICK_WALL);
                b.set(x, 1, z + 1, slabS(S(Blocks.STONE_BRICK_SLAB), SlabType.BOTTOM));
            }
        }
    }

    // ============================================================== Kuraigana: the ruined castle

    private static final Block[] RUIN = {Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.COBBLESTONE};

    private static Block ruin(Builder b, int salt) {
        return RUIN[b.rand(salt, RUIN.length)];
    }

    /** Broken wall run: random height between lo and hi, built along x or z, merlons where it still stands tall. */
    private static void brokenWall(Builder b, int x1, int z1, int x2, int z2, int lo, int hi, int salt) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                int h = lo + b.rand(salt + x * 31 + z * 17, hi - lo + 1);
                for (int y = 1; y <= h; y++) b.set(x, y, z, ruin(b, salt + x * 7 + y * 131 + z * 13));
                if (h >= hi - 1 && ((x + z) & 1) == 0) b.set(x, h + 1, z, ruin(b, salt + x * 5 + z * 3));
            }
    }

    /** The swordsman's castle in Kuraigana's gloomy forest: a ruined curtain wall with a half-fallen gatehouse, a broken
     *  round tower, a training yard of slashed dummies, and the keep that is still lived in: three storeys of dark stone
     *  under a steep slate roof that has partly fallen in, a tall round tower with a spiral stair and a slate spire.
     *  Inside: the great hall, the master's rooms, a library under the broken roof, and below a wine cellar whose
     *  cracked back wall hides the old armoury. */
    static void ruinedCastle(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 20; // 41 x 41
        site(b, Blocks.PODZOL, Blocks.DIRT, 44);
        patchGround(b, 101, 0, Blocks.PODZOL, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT, Blocks.MOSSY_COBBLESTONE);
        scatter(b, 102, 14, 2, 2, w - 3, 16, 1, Blocks.BROWN_MUSHROOM.defaultBlockState());
        scatter(b, 103, 8, 2, 2, w - 3, 16, 1, Blocks.RED_MUSHROOM.defaultBlockState());
        Mats m = Mats.of(BuildStyle.STONE_TOWN, b.seed);
        m.wall = S(Blocks.STONE_BRICKS);
        m.upperWall = S(Blocks.STONE_BRICKS);
        m.frame = S(Blocks.DEEPSLATE_BRICKS);
        m.trim = S(Blocks.DEEPSLATE_BRICKS);
        m.base = S(Blocks.COBBLED_DEEPSLATE);
        m.floor = S(Blocks.DARK_OAK_PLANKS);
        roofTiles(m, ArchBlocks.SLATE_ROOF_TILES, ArchBlocks.SLATE_ROOF_TILE_STAIRS, ArchBlocks.SLATE_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.STEEP;
        m.shutter = null;
        m.window = S(Blocks.IRON_BARS);
        m.lantern = S(Blocks.LANTERN);
        m.fence = S(Blocks.DARK_OAK_FENCE);
        BlockState slate = S(ArchBlocks.SLATE_ROOF_TILES), slateSt = S(ArchBlocks.SLATE_ROOF_TILE_STAIRS), st2 = S(Blocks.STONE_BRICK_STAIRS);
        // ---- the curtain wall with its breaches, and the gatehouse (north)
        brokenWall(b, 1, 1, 13, 1, 3, 7, 111);
        brokenWall(b, 27, 1, w - 2, 1, 2, 7, 112);
        brokenWall(b, 1, d - 2, w - 2, d - 2, 3, 8, 113);
        brokenWall(b, 1, 2, 1, d - 3, 2, 7, 114);
        brokenWall(b, w - 2, 2, w - 2, 10, 1, 6, 115);
        brokenWall(b, w - 2, 17, w - 2, d - 3, 2, 7, 116);
        for (int[] g : new int[][]{{14, 17, 12}, {23, 26, 7}}) {
            for (int x = g[0]; x <= g[1]; x++)
                for (int z = 0; z <= 3; z++) {
                    int h = g[2] - (g[2] < 10 ? b.rand(x * 7 + z, 3) : 0);
                    for (int y = 1; y <= h; y++) b.set(x, y, z, ruin(b, x * 13 + y * 7 + z * 3));
                    if (g[2] >= 10 && (x == g[0] || x == g[1] || z == 0 || z == 3) && ((x + z) & 1) == 0) b.set(x, h + 1, z, Blocks.STONE_BRICKS);
                }
            b.clear(g[0] + 1, 1, 1, g[1] - 1, 3, 2);
        }
        b.set(15, 2, 0, Blocks.IRON_BARS);
        b.fill(18, 7, 0, 20, 8, 3, Blocks.STONE_BRICKS);
        b.set(21, 7, 0, Blocks.CRACKED_STONE_BRICKS);
        b.fill(18, 6, 1, 20, 6, 1, Blocks.IRON_BARS);
        b.hanging(16, 5, 4, Blocks.LANTERN);
        b.set(16, 6, 4, Blocks.STONE_BRICKS);
        heap(b, 104, 24, 5, 1, 1, 1, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL);
        // the broken round tower (north-west)
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
        heap(b, 105, 7, 7, 1, 2, 1, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL);
        npc(b, "ruin_guardian", 9, 1, 5);
        // the training yard: dummies with arms, slashed posts, a target, a bench
        for (int x = 25; x <= 34; x += 3) {
            b.set(x, 1, 5, Blocks.SPRUCE_FENCE);
            b.set(x, 2, 5, Blocks.HAY_BLOCK);
            b.set(x, 3, 5, Blocks.CARVED_PUMPKIN);
            b.set(x - 1, 2, 5, connect(Blocks.SPRUCE_FENCE, false, true, false, false));
            b.set(x + 1, 2, 5, connect(Blocks.SPRUCE_FENCE, false, false, false, true));
            b.fill(x, 1, 10, x, 2 + b.rand(x, 2), 10, log(Blocks.STRIPPED_OAK_LOG, Direction.Axis.Y));
        }
        b.set(36, 1, 8, Blocks.TARGET);
        b.set(36, 1, 11, decor(ArchBlocks.PLANK_BENCH, Direction.WEST));
        b.set(36, 1, 6, Blocks.GRINDSTONE);
        npc(b, "master", 31, 1, 7);
        npc(b, "ruin_guardian", 24, 1, 12);
        npc(b, "ruin_guardian", 34, 1, 3);
        // dark oaks pushing through the ruins
        tree(b, 16, 1, 36, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, 6, 3, 121, null);
        tree(b, 35, 1, 35, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, 7, 3, 122, null);
        deadTree(b, 4, 1, 22, 6, 123, Blocks.DARK_OAK_LOG);
        deadTree(b, 37, 1, 26, 5, 124, Blocks.DARK_OAK_LOG);

        // ---- the keep (x 11..29, z 19..31): three storeys of five, a steep slate roof along x
        int x1 = 11, x2 = 29, z1 = 19, z2 = 31;
        b.fill(x1, 0, z1, x2, 0, z2, Blocks.STONE_BRICKS);
        storeys(b, m, x1, 0, z1, x2, z2, 3, 5, S(Blocks.STONE_BRICKS));
        for (int x = x1; x <= x2; x++)
            for (int y = 2; y <= 14; y++)
                for (int z : new int[]{z1, z2}) if (b.rand(x * 7 + y * 131 + z, 5) == 0 && y % 5 != 0) b.set(x, y, z, ruin(b, x + y + z));
        Arch.gable(b, m, x1, z1, x2, z2, 16, true, 1, m.wall);
        // the fallen part of the roof: a hole over the east end with bare rafters
        b.clear(22, 15, 18, 26, 32, 24);
        for (int i = 0; i <= 6; i++) for (int x : new int[]{22, 26}) b.fill(x, 16 + 2 * i, 18 + i, x, 17 + 2 * i, 18 + i, log(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        b.fill(22, 30, 25, 26, 30, 25, log(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        heap(b, 106, 24, 22, 11, 2, 1, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS);
        // windows: barred, a few broken open
        for (int fy : new int[]{0, 5, 10}) {
            for (int x : new int[]{13, 16, 24, 27}) {
                Arch.window(b, m, x, fy + 2, z1, Direction.NORTH, 2, false);
                Arch.window(b, m, x, fy + 2, z2, Direction.SOUTH, 2, false);
                if (b.rand(x * 3 + fy, 3) == 0) b.clear(x, fy + 2, z2, x, fy + 3, z2);
            }
            for (int z = 23; z <= 27; z += 4) {
                Arch.window(b, m, x1, fy + 2, z, Direction.WEST, 2, false);
                Arch.window(b, m, x2, fy + 2, z, Direction.EAST, 2, false);
            }
        }
        // the door with a carved lintel and steps
        b.bigDoor(c, 1, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.fill(c - 1, 4, z1, c + 1, 4, z1, Blocks.CHISELED_STONE_BRICKS);
        for (int x = c - 2; x <= c + 2; x += 4) Arch.wallLantern(b, m, x, 2, z1, Direction.NORTH);
        b.set(c, 5, z1 - 1, banner(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
        // ---- the tall round tower (north-east), spiral stair to the lookout, slate spire
        int tx = 33, tz = 16;
        b.cylinder(tx, tz, 0, 0, 4, Blocks.COBBLED_DEEPSLATE, false);
        for (int y = 1; y <= 30; y++) {
            LandmarkKit.ring(b, tx, tz, y, 4, S(y % 6 == 0 ? Blocks.DEEPSLATE_BRICKS : Blocks.STONE_BRICKS));
            b.cylinder(tx, tz, y, y, 3, Blocks.AIR, false);
        }
        b.cylinder(tx, tz, 26, 26, 3, Blocks.DARK_OAK_PLANKS, false);
        spiral(b, tx - 2, tz - 2, tx + 2, tz + 2, 0, 26, S(Blocks.STONE_BRICK_STAIRS), S(Blocks.STONE_BRICKS));
        b.clear(tx - 4, 1, tz, tx - 4, 3, tz);
        b.door(tx - 4, 1, tz, Blocks.DARK_OAK_DOOR, Direction.WEST);
        for (int y : new int[]{7, 13, 19}) {
            b.set(tx + 4, y, tz, Blocks.IRON_BARS);
            b.set(tx, y, tz + 4, Blocks.IRON_BARS);
            b.set(tx, y, tz - 4, Blocks.IRON_BARS);
        }
        for (int[] o : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) b.fill(tx + o[0], 27, tz + o[1], tx + o[0], 29, tz + o[1], Blocks.AIR);
        b.set(tx, 30, tz, Blocks.STONE_BRICKS);
        b.hanging(tx, 29, tz, Blocks.LANTERN);
        LandmarkKit.disc(b, tx, tz, 31, 3.6, S(Blocks.DEEPSLATE_BRICKS));
        eaveRing(b, tx, tz, 31, 4.4, slateSt);
        spire(b, tx, tz, 32, 3.6, 2, slate, S(Blocks.LIGHTNING_ROD));
        b.set(tx + 1, 27, tz + 1, decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
        // a slender square tower on the keep's south-west corner
        b.fill(6, 1, 32, 10, 24, 36, Blocks.STONE_BRICKS);
        b.clear(7, 1, 33, 9, 23, 35);
        for (int y = 4; y <= 22; y += 6) for (int[] o : new int[][]{{8, 32}, {6, 34}, {8, 36}}) b.set(o[0], y, o[1], Blocks.IRON_BARS);
        wallsS(b, 6, 24, 32, 10, 24, 36, S(Blocks.DEEPSLATE_BRICKS));
        squareSpire(b, 5, 31, 11, 37, 25, 3, slate, slateSt, S(Blocks.LIGHTNING_ROD));
        // ---- ground floor: the great hall
        longTableX(b, 14, 23, 1, 25, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_STAIRS, true);
        b.set(25, 1, 25, stairS(S(Blocks.DARK_OAK_STAIRS), Direction.EAST, Half.BOTTOM));
        b.fill(26, 1, 25, 26, 2, 25, Blocks.DARK_OAK_PLANKS);
        b.set(28, 3, 25, banner(Blocks.BLACK_WALL_BANNER, Direction.WEST));
        b.fill(x1 + 1, 1, 24, x1 + 1, 3, 26, Blocks.BRICKS);
        b.set(x1 + 1, 1, 25, Blocks.CAMPFIRE);
        b.set(x1 + 1, 2, 25, Blocks.AIR);
        b.fill(x1 + 1, 4, 24, x1 + 1, 4, 26, Blocks.BRICKS);
        b.fill(x2 - 1, 1, 21, x2 - 1, 2, 23, Blocks.BARREL);
        for (int x = 15; x <= 25; x += 5) b.hanging(x, 4, 25, Blocks.LANTERN);
        npc(b, "ruin_guardian", 26, 1, 27);
        flight(b, st2, 13, 1, 20, Direction.EAST, 4, 2, S(Blocks.STONE_BRICKS));
        line(b, 12, 6, 22, 16, 22, Blocks.DARK_OAK_FENCE);
        line(b, 19, 1, 28, 22, 28, Blocks.DARK_OAK_FENCE);
        // first floor: the master's rooms
        b.bed(14, 6, 29, Blocks.BLACK_BED, Direction.SOUTH);
        b.chest(13, 6, 30, Direction.EAST, Buildings.LOOT_HOUSE);
        b.fill(16, 6, 30, 19, 6, 30, Blocks.DARK_OAK_PLANKS);
        for (int x = 16; x <= 19; x++) b.set(x, 7, 30, Arch.along(S(Blocks.IRON_BARS), Direction.Axis.X));
        b.fill(x1 + 1, 6, 24, x1 + 1, 8, 27, Blocks.BOOKSHELF);
        b.set(17, 6, 26, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(17, 7, 26, candles(Blocks.CANDLE, 2));
        b.set(17, 6, 27, stairS(S(Blocks.DARK_OAK_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.set(22, 6, 30, Blocks.BARREL);
        b.set(23, 6, 30, Blocks.BREWING_STAND);
        b.hanging(18, 9, 27, Blocks.LANTERN);
        flight(b, st2, 27, 6, 30, Direction.WEST, 4, 2, S(Blocks.STONE_BRICKS));
        line(b, 23, 11, 28, 26, 28, Blocks.DARK_OAK_FENCE);
        // second floor: library under the broken roof
        for (int x = 13; x <= 19; x += 3) b.fill(x, 11, 27, x + 1, 13, 27, Blocks.BOOKSHELF);
        b.set(15, 11, 23, Blocks.LECTERN);
        b.set(16, 11, 21, Blocks.ENCHANTING_TABLE);
        b.hanging(16, 14, 24, Blocks.LANTERN);
        // ---- the wine cellar and the old armoury behind its cracked wall
        shell(b, 12, -6, 22, 24, 0, 31, Blocks.COBBLED_DEEPSLATE);
        b.fill(12, 0, 22, 24, 0, 31, Blocks.STONE_BRICKS);
        b.fill(18, 0, 29, 22, 0, 30, Blocks.AIR);
        flight(b, st2, 18, -5, 29, Direction.EAST, 5, 2, S(Blocks.COBBLED_DEEPSLATE));
        for (int x = 13; x <= 23; x++) {
            b.barrel(x, -5, 23, Buildings.LOOT_TAVERN);
            b.set(x, -4, 23, Blocks.BARREL);
        }
        b.hanging(17, -2, 26, Blocks.LANTERN);
        b.fill(24, -6, 23, 29, 0, 30, Blocks.COBBLED_DEEPSLATE);
        b.clear(25, -5, 24, 28, -3, 29);
        b.set(24, -5, 26, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.set(24, -4, 26, Blocks.CRACKED_DEEPSLATE_BRICKS);
        b.chest(27, -5, 25, Direction.SOUTH, Buildings.LOOT_TREASURE);
        mark(b, "treasure", 27, -5, 25);
        b.set(28, -5, 28, Blocks.GRINDSTONE);
        b.set(26, -5, 29, Blocks.SMITHING_TABLE);
        b.fill(28, -5, 25, 28, -4, 26, Blocks.IRON_BARS);
        b.hanging(26, -3, 27, Blocks.SOUL_LANTERN);
        b.marker("legend", c, 1, 14, tag("story", "ruined_castle"));
    }

    // ============================================================== Sabaody: a mangrove grove

    /** Grove 13 of the Sabaody Archipelago: a giant Yarukiman mangrove whose flared, hollow trunk carries the grove number
     *  painted in white, with eight arching roots, resin bubbles drifting up and a wide canopy over a lookout balcony
     *  (reached by a spiral stair inside the trunk; a resin-sealed cache hides in the trunk wall). Among the roots: the
     *  rip-off bar (west), the coating yard with a ship model under a bubble film (east) and a bubble-souvenir stall. */
    static void mangroveGrove(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 18; // 37 x 37
        site(b, Blocks.MUD, Blocks.DIRT, 42);
        patchGround(b, 131, 0, Blocks.GRASS_BLOCK, Blocks.MUD, Blocks.MOSS_BLOCK, Blocks.PACKED_MUD, Blocks.GRASS_BLOCK);
        BlockState bark = S(Blocks.MANGROVE_WOOD), roots = S(Blocks.MANGROVE_ROOTS), muddy = S(Blocks.MUDDY_MANGROVE_ROOTS);
        BlockState planks = S(Blocks.MANGROVE_PLANKS), lv = leaves(Blocks.MANGROVE_LEAVES);
        // ---- the trunk: flared at the foot, hollow inside, capped at y=31
        for (int y = 1; y <= 31; y++) {
            int r = y <= 3 ? 10 - y : 6;
            for (int x = -r - 1; x <= r + 1; x++)
                for (int z = -r - 1; z <= r + 1; z++) {
                    double dd = Math.sqrt(x * x + z * z);
                    if (dd > r + 0.5) continue;
                    b.set(c + x, y, c + z, dd > 6.5 ? muddy : bark);
                }
        }
        for (int y = 1; y <= 30; y++) b.cylinder(c, c, y, y, 4, Blocks.AIR, false);
        b.cylinder(c, c, 0, 0, 4, Blocks.MANGROVE_PLANKS, false);
        b.cylinder(c, c, 27, 27, 4, Blocks.MANGROVE_PLANKS, false);
        spiral(b, c - 3, c - 3, c + 3, c + 3, 0, 27, S(Blocks.MANGROVE_STAIRS), planks);
        for (int y = 5; y <= 21; y += 8) for (int[] o : new int[][]{{5, 0}, {-5, 0}, {0, -5}, {0, 5}}) if (!(o[1] == 5 && y < 4)) b.set(c + o[0], y, c + o[1], Blocks.SHROOMLIGHT);
        // the doorway (south) through the flare
        b.clear(c - 1, 1, c + 5, c + 1, 3, c + 9);
        b.fill(c - 1, 0, c + 5, c + 1, 0, c + 9, planks);
        b.door(c, 1, c + 5, Blocks.MANGROVE_DOOR, Direction.SOUTH);
        b.set(c, 3, c + 5, Blocks.AIR);
        for (int x : new int[]{c - 2, c + 2}) b.fill(x, 1, c + 6, x, 4, c + 6, log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.Y));
        b.fill(c - 2, 4, c + 6, c + 2, 4, c + 6, log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.X));
        lampPost(b, c + 3, 1, c + 11, Blocks.MANGROVE_FENCE, 3, Blocks.LANTERN);
        // the grove number painted on the north face of the trunk
        int[][] glyph = {{0, 1, 0, 0, 1, 1, 1}, {1, 1, 0, 0, 0, 0, 1}, {0, 1, 0, 0, 0, 1, 1}, {0, 1, 0, 0, 0, 0, 1}, {1, 1, 1, 0, 1, 1, 1}};
        b.fill(c - 4, 10, c - 6, c + 4, 16, c - 6, bark);
        for (int row = 0; row < 5; row++)
            for (int u = 0; u < 7; u++) {
                if (glyph[row][u] == 0) continue;
                b.set(c + 3 - u, 15 - row, c - 6, Blocks.WHITE_CONCRETE);
            }
        // the resin-sealed cache in the east wall of the trunk
        b.set(c + 4, 8, c, Blocks.HONEY_BLOCK);
        b.chest(c + 5, 8, c, Direction.WEST, Buildings.LOOT_TREASURE);
        mark(b, "treasure", c + 5, 8, c);
        // ---- eight arching roots
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(22.5 + 45 * i);
            double px = -Math.sin(a), pz = Math.cos(a);
            for (int k = 0; k <= 9; k++) {
                double rr = 6 + k;
                int rx = c + (int) Math.round(Math.cos(a) * rr), rz = c + (int) Math.round(Math.sin(a) * rr);
                int ry = (int) Math.round(7 * Math.cos(Math.PI / 2 * k / 9.0));
                int sx = c + (int) Math.round(Math.cos(a) * rr + px), sz = c + (int) Math.round(Math.sin(a) * rr + pz);
                b.set(rx, Math.max(1, ry), rz, bark);
                b.set(rx, Math.max(1, ry - 1), rz, roots);
                b.set(sx, Math.max(1, ry), sz, roots);
                if (k == 9) {
                    b.fill(rx, 1, rz, rx, Math.max(1, ry), rz, bark);
                    b.set(rx, 0, rz, muddy);
                    b.set(sx, 1, sz, muddy);
                }
            }
        }
        // ---- the canopy, its branches and the lookout balcony
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(30 + 60 * i);
            for (int k = 5; k <= 10; k++) b.set(c + (int) Math.round(Math.cos(a) * k), 30 + k / 4, c + (int) Math.round(Math.sin(a) * k), bark);
        }
        for (int x = -13; x <= 13; x++)
            for (int z = -13; z <= 13; z++)
                for (int y = 27; y <= 38; y++) {
                    double v = (x * x + z * z) / 169.0 + Math.pow((y - 32) / 6.0, 2);
                    if (v > 1 || b.rand(x * 131 + z * 17 + y, 6) == 0) continue;
                    if (x * x + z * z <= 42 && y <= 31) continue;
                    b.set(c + x, y, c + z, lv);
                }
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                double dd = Math.sqrt(x * x + z * z);
                if (dd > 9.5 || dd <= 6.5) continue;
                b.set(c + x, 27, c + z, planks);
                b.clear(c + x, 28, c + z, c + x, 31, c + z);
            }
        LandmarkKit.ring(b, c, c, 28, 9.0, S(Blocks.MANGROVE_FENCE));
        b.clear(c, 28, c + 4, c, 30, c + 7);
        b.bed(c - 1, 28, c, Blocks.LIGHT_BLUE_BED, Direction.SOUTH);
        b.set(c + 1, 28, c - 1, Blocks.CRAFTING_TABLE);
        b.set(c + 1, 28, c + 1, Blocks.BARREL);
        b.hanging(c, 30, c, Blocks.LANTERN);
        b.set(c + 3, 28, c + 8, decor(ArchBlocks.PLANK_BENCH, Direction.WEST));
        b.set(c - 3, 28, c + 8, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        npc(b, "resident", c + 1, 28, c + 8);
        // ---- the rip-off bar among the roots (west)
        Mats m = Mats.of(BuildStyle.VILLAGE, b.seed);
        m.wall = S(ArchBlocks.WHITE_PLASTER);
        m.upperWall = m.wall;
        m.frame = log(ArchBlocks.OAK_BEAM, Direction.Axis.Y);
        m.trim = m.frame;
        m.base = S(ArchBlocks.FIELDSTONE);
        m.floor = S(Blocks.SPRUCE_PLANKS);
        roofTiles(m, ArchBlocks.TEAL_ROOF_TILES, ArchBlocks.TEAL_ROOF_TILE_STAIRS, ArchBlocks.TEAL_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.GABLE;
        m.shutter = ArchBlocks.GREEN_SHUTTER.get();
        m.planters = true;
        m.eaveBrackets = true;
        m.door = S(Blocks.SPRUCE_DOOR);
        storeys(b, m, 1, 0, 15, 8, 21, 1, 5, S(Blocks.SPRUCE_PLANKS));
        Arch.gable(b, m, 1, 15, 8, 21, 6, false, 1, m.wall);
        Arch.chimney(b, m, 2, 20, 6, 12, true);
        b.bigDoor(8, 1, 18, Blocks.SPRUCE_DOOR, Direction.EAST);
        Arch.window(b, m, 8, 2, 16, Direction.EAST, 2, true);
        Arch.window(b, m, 8, 2, 20, Direction.EAST, 2, true);
        Arch.window(b, m, 1, 2, 18, Direction.WEST, 2, true);
        Arch.window(b, m, 4, 2, 15, Direction.NORTH, 2, true);
        Arch.window(b, m, 4, 2, 21, Direction.SOUTH, 2, true);
        for (int z = 17; z <= 19; z++) b.set(9, 4, z, decor(ArchBlocks.GREEN_AWNING, Direction.EAST));
        b.set(9, 3, 16, log(ArchBlocks.OAK_BEAM, Direction.Axis.X));
        b.set(10, 3, 16, Blocks.BARREL);
        b.set(10, 2, 16, hang(S(Blocks.LANTERN)));
        for (int z = 16; z <= 19; z++) b.set(4, 1, z, decor(ArchBlocks.SHOP_COUNTER, Direction.EAST));
        for (int z : new int[]{16, 18, 20}) b.barrel(2, 1, z, Buildings.LOOT_TAVERN);
        b.set(2, 2, 18, Blocks.BREWING_STAND);
        for (int z : new int[]{17, 19}) b.set(2, 1, z, decor(ArchBlocks.GOODS_SHELF, Direction.EAST));
        for (int z : new int[]{17, 19}) b.set(5, 1, z, stairS(S(Blocks.SPRUCE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.set(7, 1, 20, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(7, 2, 20, candles(Blocks.LIGHT_BLUE_CANDLE, 2));
        b.set(6, 1, 20, stairS(S(Blocks.SPRUCE_STAIRS), Direction.WEST, Half.BOTTOM));
        b.hanging(5, 4, 18, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "bartender", 3, 1, 18);
        npc(b, "patron", 6, 1, 17);
        b.marker("quest_board", 7, 2, 16, new CompoundTag());
        // patio by the door
        b.set(11, 1, 19, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(11, 1, 18, decor(ArchBlocks.PLANK_BENCH, Direction.NORTH));
        b.set(11, 1, 20, decor(ArchBlocks.PLANK_BENCH, Direction.SOUTH));
        // ---- the coating yard (east): a ship model under a bubble film, resin vats
        b.fill(26, 0, 14, 35, 0, 22, S(ArchBlocks.DOCK_PLANKS));
        b.fill(29, 1, 17, 31, 1, 19, Blocks.SPRUCE_PLANKS);
        b.fill(30, 2, 18, 31, 2, 18, Blocks.SPRUCE_PLANKS);
        b.set(28, 1, 18, stairS(S(Blocks.SPRUCE_STAIRS), Direction.WEST, Half.BOTTOM));
        b.set(32, 1, 18, stairS(S(Blocks.SPRUCE_STAIRS), Direction.EAST, Half.BOTTOM));
        b.fill(30, 3, 18, 30, 4, 18, Blocks.SPRUCE_FENCE);
        b.set(31, 3, 18, Blocks.WHITE_WOOL);
        b.set(31, 4, 18, Blocks.WHITE_WOOL);
        LandmarkKit.ellipsoid(b, 30, 1, 18, 3.6, 5.0, 3.6, S(Blocks.LIGHT_BLUE_STAINED_GLASS), true, 0);
        b.set(35, 1, 16, Blocks.HONEY_BLOCK);
        b.set(35, 1, 17, Blocks.SLIME_BLOCK);
        b.set(35, 1, 19, Blocks.CAULDRON);
        b.set(35, 1, 20, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        b.chest(35, 1, 14, Direction.WEST, Buildings.LOOT_WAREHOUSE);
        b.set(26, 1, 14, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
        lampPost(b, 26, 1, 22, Blocks.MANGROVE_FENCE, 3, Blocks.LANTERN);
        npc(b, "shipwright", 34, 1, 22);
        // ---- the bubble-souvenir stall (north)
        for (int[] p : new int[][]{{15, 2}, {21, 2}, {15, 5}, {21, 5}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], Blocks.MANGROVE_FENCE);
        for (int x = 15; x <= 21; x++) {
            b.set(x, 4, 2, decor(ArchBlocks.BLUE_AWNING, Direction.NORTH));
            b.set(x, 4, 5, decor(ArchBlocks.BLUE_AWNING, Direction.SOUTH));
        }
        b.fill(15, 4, 3, 21, 4, 4, Blocks.CYAN_WOOL);
        for (int x = 16; x <= 20; x++) b.set(x, 1, 2, decor(ArchBlocks.SHOP_COUNTER, Direction.NORTH));
        b.set(17, 2, 2, Blocks.LIGHT_BLUE_STAINED_GLASS);
        b.set(19, 2, 2, Blocks.WHITE_STAINED_GLASS);
        b.barrel(16, 1, 4, Buildings.LOOT_SHOP);
        npc(b, "merchant", 18, 1, 4);
        // ---- drifting resin bubbles
        LandmarksC.bubble(b, 8, 11, 6, 1.6, S(Blocks.WHITE_STAINED_GLASS));
        LandmarksC.bubble(b, 29, 13, 8, 2.1, S(Blocks.LIGHT_BLUE_STAINED_GLASS));
        LandmarksC.bubble(b, 6, 19, 30, 1.6, S(Blocks.WHITE_STAINED_GLASS));
        LandmarksC.bubble(b, 31, 9, 29, 1.4, S(Blocks.WHITE_STAINED_GLASS));
        LandmarksC.bubble(b, 12, 4, 5, 1.2, S(Blocks.LIGHT_BLUE_STAINED_GLASS));
        LandmarksC.bubble(b, 25, 21, 3, 1.4, S(Blocks.LIGHT_BLUE_STAINED_GLASS));
        LandmarksC.bubble(b, 4, 7, 33, 1.2, S(Blocks.WHITE_STAINED_GLASS));
        for (int[] p : new int[][]{{10, 1, 9}, {27, 1, 10}, {9, 1, 28}, {27, 1, 27}}) b.set(p[0], p[1], p[2], Blocks.WHITE_STAINED_GLASS);
        // the path south from the door
        for (int z = c + 10; z < d; z++) for (int x = c - 1; x <= c + 1; x++) b.set(x, 0, z, b.rand(x * 3 + z * 7, 3) == 0 ? Blocks.PACKED_MUD : Blocks.MUD_BRICKS);
        npc(b, "resident", c + 2, 1, c + 11);
        b.marker("legend", c, 1, c + 10, tag("story", "mangrove_grove"));
    }

    // ============================================================== Sabaody: the auction house

    /** Height of the auction house's tent roof at (x, z): a concave big-top over the hall, peaking above (16, 19). */
    private static int tentY(int x, int z) {
        double dx = Math.abs(x - 16) / 15.0, dz = Math.abs(z - 19) / 15.0;
        double t = Math.pow(Math.pow(dx, 4) + Math.pow(dz, 4), 0.25);
        if (t > 1.0) return -1;
        return 11 + (int) Math.round(15 * (1 - t));
    }

    /** The Human Auctioning House of Grove 1: a cream hall with ochre pilasters under a red-and-white striped big-top
     *  roof with a scalloped valance and a flag, a columned portico with steps and an illuminated marquee, bubbles
     *  drifting past. Inside: raked rows of seats, side boxes for the nobles reached by stairs from the foyer, the stage
     *  with its curtain, wings and footlights, and backstage the cells for the lots and the strong room. */
    static void auctionHouse(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 16; // 33 x 35
        site(b, Blocks.POLISHED_ANDESITE, Blocks.STONE, 32);
        BlockState cream = S(ArchBlocks.CREAM_PLASTER), ochre = S(ArchBlocks.OCHRE_PLASTER), hs = S(ArchBlocks.HARBOUR_STONE);
        BlockState gold = S(Blocks.GOLD_BLOCK), red = S(Blocks.RED_WOOL), white = S(Blocks.WHITE_WOOL);
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) if (((x + z) % 4) == 0) b.set(x, 0, z, hs);
        int x1 = 2, x2 = 30, z1 = 5, z2 = 33;
        // ---- the hall walls (y 1..10)
        b.fill(x1, 0, z1, x2, 0, z2, Blocks.DARK_OAK_PLANKS);
        wallsS(b, x1, 1, z1, x2, 10, z2, cream);
        b.clear(x1 + 1, 1, z1 + 1, x2 - 1, 30, z2 - 1);
        wallsS(b, x1, 1, z1, x2, 1, z2, hs);
        wallsS(b, x1, 10, z1, x2, 10, z2, gold);
        for (int x = x1; x <= x2; x += 4) {
            b.fill(x, 2, z1, x, 9, z1, ochre);
            b.fill(x, 2, z2, x, 9, z2, ochre);
        }
        for (int z = z1; z <= z2; z += 4) {
            b.fill(x1, 2, z, x1, 9, z, ochre);
            b.fill(x2, 2, z, x2, 9, z, ochre);
        }
        for (int z = 11; z <= 27; z += 4) {
            for (int y = 7; y <= 8; y++) {
                b.set(x1, y, z, Arch.along(S(Blocks.YELLOW_STAINED_GLASS_PANE), Direction.Axis.Z));
                b.set(x2, y, z, Arch.along(S(Blocks.YELLOW_STAINED_GLASS_PANE), Direction.Axis.Z));
            }
        }
        // ---- the striped big-top roof
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                int y = tentY(x, z);
                if (y < 0) y = 11;
                int lo = y;
                for (Direction dd : Direction.Plane.HORIZONTAL) {
                    int ny = tentY(x + dd.getStepX(), z + dd.getStepZ());
                    if (ny < 0 || x + dd.getStepX() < x1 - 1 || x + dd.getStepX() > x2 + 1 || z + dd.getStepZ() < z1 - 1 || z + dd.getStepZ() > z2 + 1) ny = 11;
                    lo = Math.min(lo, ny + 1);
                }
                double a = Math.atan2(z - 19, x - c);
                boolean redStripe = (int) Math.floor((a + Math.PI) / (Math.PI / 10)) % 2 == 0;
                BlockState full = redStripe ? S(ArchBlocks.TERRACOTTA_ROOF_TILES) : S(ArchBlocks.WHITE_PLASTER);
                BlockState stair = redStripe ? S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS) : S(ArchBlocks.WHITE_PLASTER_STAIRS);
                if (lo < y) b.fill(x, lo, z, x, y - 1, z, full);
                int ddx = c - x, ddz = 19 - z;
                if (ddx == 0 && ddz == 0) b.set(x, y, z, full);
                else b.set(x, y, z, stairS(stair, Math.abs(ddx) >= Math.abs(ddz) ? (ddx > 0 ? Direction.EAST : Direction.WEST) : (ddz > 0 ? Direction.SOUTH : Direction.NORTH), Half.BOTTOM));
            }
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            b.set(x, 10, z1 - 1, decor(ArchBlocks.RED_AWNING, Direction.NORTH));
            b.set(x, 10, z2 + 1, decor(ArchBlocks.RED_AWNING, Direction.SOUTH));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, 10, z, decor(ArchBlocks.RED_AWNING, Direction.WEST));
            b.set(x2 + 1, 10, z, decor(ArchBlocks.RED_AWNING, Direction.EAST));
        }
        int peak = tentY(c, 19);
        b.set(c, peak + 1, 19, gold);
        flag(b, c, peak + 2, 19, 4, Direction.EAST, 3, S(Blocks.RED_WOOL), S(Blocks.YELLOW_WOOL));
        LandmarksC.bubble(b, 3, 17, 3, 1.5, S(Blocks.LIGHT_BLUE_STAINED_GLASS));
        LandmarksC.bubble(b, 29, 15, 4, 1.5, S(Blocks.WHITE_STAINED_GLASS));
        LandmarksC.bubble(b, 30, 14, 31, 1.2, S(Blocks.LIGHT_BLUE_STAINED_GLASS));
        // ---- the portico: two steps, four columns, an entablature and the marquee
        b.fill(9, 1, 2, 23, 2, 4, hs);
        flight(b, S(ArchBlocks.HARBOUR_STONE_STAIRS), 23, 1, 0, Direction.SOUTH, 2, 15, hs);
        for (int x : new int[]{9, 13, 19, 23}) {
            b.set(x, 3, 2, S(ArchBlocks.DRESSED_SANDSTONE));
            b.fill(x, 4, 2, x, 9, 2, log(ArchBlocks.MARBLE_COLUMN, Direction.Axis.Y));
        }
        b.fill(8, 10, 1, 24, 10, 4, cream);
        b.fill(8, 10, 1, 24, 10, 1, gold);
        for (int x = 8; x <= 24; x++) b.set(x, 9, 1, stairS(S(ArchBlocks.CREAM_PLASTER_STAIRS), Direction.SOUTH, Half.TOP));
        b.fill(10, 11, 1, 22, 14, 2, Blocks.DARK_OAK_PLANKS);
        for (int x = 10; x <= 22; x++) {
            b.set(x, 11, 1, gold);
            b.set(x, 14, 1, gold);
        }
        for (int x = 11; x <= 21; x += 2) b.set(x, 13, 1, Blocks.SHROOMLIGHT);
        b.fill(11, 12, 1, 21, 12, 1, S(Blocks.RED_CONCRETE));
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 3, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.set(c - 3, 7, 4, banner(Blocks.RED_WALL_BANNER, Direction.NORTH));
        b.set(c + 3, 7, 4, banner(Blocks.RED_WALL_BANNER, Direction.NORTH));
        b.hanging(c, 9, 3, ArchBlocks.BRASS_LANTERN.get());
        npc(b, "guard", 11, 3, 3);
        npc(b, "guard", 21, 3, 3);
        // ---- the auditorium: a raked floor, rows of seats facing the stage, a centre aisle
        for (int z = z1 + 1; z <= 20; z++) {
            int top = z <= 13 ? 2 : z <= 17 ? 1 : 0;
            if (top > 0) b.fill(x1 + 1, 1, z, x2 - 1, top, z, Blocks.DARK_OAK_PLANKS);
            if (z >= 11 && (z & 1) == 1)
                for (int x = 5; x <= 27; x++) if (Math.abs(x - c) > 1) chair(b, x, top + 1, z, Blocks.DARK_OAK_STAIRS, Direction.NORTH);
            b.fill(c - 1, top + 1, z, c + 1, top + 1, z, Blocks.RED_CARPET);
        }
        npc(b, "patron", 8, 3, 12);
        npc(b, "patron", 23, 2, 16);
        npc(b, "patron", 11, 1, 20);
        // the nobles' boxes along the side walls, reached by stairs from the foyer
        BlockState dst = S(Blocks.DARK_OAK_STAIRS);
        for (boolean west : new boolean[]{true, false}) {
            int a = west ? 3 : 27, e = west ? 5 : 29, inner = west ? 6 : 26;
            b.fill(a, 6, 9, e, 6, 18, Blocks.DARK_OAK_PLANKS);
            b.fill(a, 5, 9, e, 5, 18, stairS(dst, Direction.SOUTH, Half.TOP));
            line(b, inner, 7, 9, inner, 18, Blocks.DARK_OAK_FENCE);
            for (int z = 11; z <= 17; z += 3) chair(b, west ? 4 : 28, 7, z, Blocks.QUARTZ_STAIRS, Direction.NORTH);
            flight(b, dst, west ? 4 : 29, 3, 6, Direction.SOUTH, 3, 2, S(Blocks.DARK_OAK_PLANKS));
            b.set(west ? 5 : 27, 7, 18, Blocks.POTTED_FERN);
        }
        npc(b, "noble", 5, 7, 15);
        npc(b, "noble", 27, 7, 12);
        // chandeliers on chains from the tent
        for (int[] l : new int[][]{{c, 12}, {9, 16}, {23, 16}, {c, 23}}) {
            int ry = tentY(l[0], l[1]);
            b.fill(l[0], 13, l[1], l[0], ry - 1, l[1], Blocks.CHAIN);
            b.hanging(l[0], 12, l[1], ArchBlocks.BRASS_LANTERN.get());
        }
        // ---- the stage: footlights, wings, curtain, the auctioneer's podium and the lot on show
        b.fill(x1 + 1, 1, 21, x2 - 1, 1, 26, Blocks.SPRUCE_PLANKS);
        for (int x = 5; x <= 27; x += 3) b.set(x, 1, 21, Blocks.SEA_LANTERN);
        b.fill(x1 + 1, 2, 26, x2 - 1, 9, 26, red);
        b.fill(x1 + 1, 2, 21, x1 + 3, 9, 25, red);
        b.fill(x2 - 3, 2, 21, x2 - 1, 9, 25, red);
        b.fill(x1 + 4, 9, 21, x2 - 4, 9, 21, red);
        b.fill(x1 + 4, 8, 21, x2 - 4, 8, 21, decor(ArchBlocks.RED_AWNING, Direction.NORTH));
        b.set(c, 2, 22, facing(Blocks.LECTERN, Direction.NORTH));
        b.set(c - 4, 2, 24, gold);
        b.set(c - 4, 3, 24, Blocks.AMETHYST_CLUSTER);
        b.set(c + 4, 2, 24, Blocks.IRON_BARS);
        b.set(c + 4, 3, 24, Blocks.IRON_BARS);
        npc(b, "merchant", c, 2, 23);
        // ---- backstage: cells for the lots (west), the strong room (east; only one of three levers opens it)
        b.door(c, 2, 26, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        b.fill(x1 + 1, 1, 27, x2 - 1, 1, z2 - 1, Blocks.STONE_BRICKS);
        for (int x = 6; x <= 14; x += 4) b.fill(x, 2, 29, x, 5, z2 - 1, Blocks.STONE_BRICKS);
        line(b, x1 + 1, 2, 28, 14, 28, Blocks.IRON_BARS);
        line(b, x1 + 1, 3, 28, 14, 28, Blocks.IRON_BARS);
        line(b, x1 + 1, 4, 28, 14, 28, Blocks.IRON_BARS);
        b.fill(x1 + 1, 5, 28, 14, 5, 28, Blocks.STONE_BRICKS);
        b.fill(x1 + 1, 6, 28, 14, 6, z2 - 1, Blocks.STONE_BRICKS);
        for (int x : new int[]{4, 8, 12}) mark(b, "cell", x, 2, 31);
        npc(b, "guard", c, 2, 29);
        b.fill(18, 2, 28, x2 - 1, 5, 28, Blocks.STONE_BRICKS);
        b.fill(18, 2, 29, 18, 5, z2 - 1, Blocks.STONE_BRICKS);
        b.fill(18, 6, 28, x2 - 1, 6, z2 - 1, Blocks.STONE_BRICKS);
        b.door(24, 2, 28, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(25, 3, 27, lever(Direction.NORTH));
        b.set(21, 3, 27, lever(Direction.NORTH));
        b.set(28, 4, 27, lever(Direction.NORTH));
        b.chest(20, 2, 31, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.chest(28, 2, 31, Direction.SOUTH, Buildings.LOOT_TREASURE);
        b.set(24, 2, 32, gold);
        b.hanging(24, 5, 30, Blocks.LANTERN);
        mark(b, "treasure", 24, 2, 31);
        b.hanging(c, 8, 30, Blocks.LANTERN);
        b.set(c, 9, 30, Blocks.STONE_BRICKS);
        b.marker("arc_anchor", c, 3, 3, tag("arc", "auction_house"));
    }

    // ============================================================== Amazon Lily: the Kuja palace

    /** Coiled snake statue (base at y, about 5 high) facing {@code f}: green scales, a glazed head, yellow eyes. */
    private static void snakeStatue(Builder b, int x, int y, int z, Direction f) {
        b.fill(x - 1, y, z - 1, x + 1, y, z + 1, Blocks.GREEN_TERRACOTTA);
        b.set(x, y, z, Blocks.LIME_TERRACOTTA);
        b.set(x + f.getStepX(), y + 1, z + f.getStepZ(), Blocks.GREEN_TERRACOTTA);
        b.fill(x, y + 1, z, x, y + 3, z, Blocks.GREEN_TERRACOTTA);
        b.set(x, y + 2, z, Blocks.LIME_TERRACOTTA);
        int hx = x + f.getStepX(), hz = z + f.getStepZ();
        b.set(hx, y + 3, hz, Blocks.GREEN_TERRACOTTA);
        b.set(hx, y + 4, hz, Blocks.GREEN_GLAZED_TERRACOTTA);
        b.set(hx + f.getStepX(), y + 4, hz + f.getStepZ(), Blocks.YELLOW_CONCRETE);
    }

    /** The warrior empress's palace: the round fighting arena with its stands in front, a grand stair flanked by
     *  serpents up to a balustraded terrace, the throne hall of red columns and cream walls under a two-tier flared roof of
     *  green tiles with orange trim and serpent-head tips, and two pavilions (the bath and bedchamber west, the kitchen
     *  and the three-lever treasury east). */
    static void kujaPalace(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = 22; // 45 x 45
        site(b, Blocks.GRASS_BLOCK, Blocks.DIRT, 34);
        Mats m = Mats.of(BuildStyle.WANO, b.seed);
        m.wall = S(ArchBlocks.CREAM_PLASTER);
        m.upperWall = m.wall;
        m.frame = log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.Y);
        m.trim = S(Blocks.ORANGE_TERRACOTTA);
        m.base = S(ArchBlocks.DRESSED_SANDSTONE);
        m.floor = S(Blocks.BAMBOO_PLANKS);
        roofTiles(m, ArchBlocks.TEAL_ROOF_TILES, ArchBlocks.TEAL_ROOF_TILE_STAIRS, ArchBlocks.TEAL_ROOF_TILE_SLAB);
        m.roofKind = Arch.RoofKind.FLARED;
        m.eaveBrackets = false;
        m.lantern = S(ArchBlocks.RED_PAPER_LANTERN);
        m.window = S(ArchBlocks.LATTICE_WINDOW);
        BlockState col = log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.Y), orange = S(Blocks.ORANGE_TERRACOTTA), sandSt = S(ArchBlocks.DRESSED_SANDSTONE_STAIRS);
        BlockState redLantern = S(ArchBlocks.RED_PAPER_LANTERN);
        // ---- the fighting arena (centre (c, 10)): sand ring, barrier, two rows of stands and an outer wall, open south
        int az = 10;
        for (int x = c - 11; x <= c + 11; x++)
            for (int z = 0; z <= 16; z++) {
                double r = Math.sqrt((x - c) * (x - c) + (z - az) * (z - az));
                if (r > 10.5) continue;
                boolean gate = z < 5 && Math.abs(x - c) <= 2, south = z > 14;
                if (r <= 6.5) b.set(x, 0, z, b.rand(x * 7 + z * 13, 6) == 0 ? Blocks.COARSE_DIRT : Blocks.SAND);
                else if (gate || south) b.set(x, 0, z, S(Blocks.SMOOTH_SANDSTONE));
                else if (r <= 7.5) b.set(x, 1, z, S(ArchBlocks.DRESSED_SANDSTONE));
                else if (r <= 9.5) {
                    int h = r <= 8.5 ? 1 : 2;
                    if (h == 2) b.set(x, 1, z, S(Blocks.SANDSTONE));
                    Direction out = Math.abs(x - c) >= Math.abs(z - az) ? (x > c ? Direction.EAST : Direction.WEST) : (z > az ? Direction.SOUTH : Direction.NORTH);
                    b.set(x, h, z, stairS(h == 1 ? sandSt : S(Blocks.SMOOTH_SANDSTONE_STAIRS), out, Half.BOTTOM));
                } else {
                    b.fill(x, 1, z, x, 3, z, S(ArchBlocks.DRESSED_SANDSTONE));
                    b.set(x, 4, z, slabS(S(ArchBlocks.DRESSED_SANDSTONE_SLAB), SlabType.BOTTOM));
                }
            }
        for (int deg : new int[]{195, 220, 245, 295, 320, 345}) {
            double a = Math.toRadians(deg);
            int px = c + (int) Math.round(Math.cos(a) * 10), pz = az + (int) Math.round(Math.sin(a) * 10);
            b.fill(px, 1, pz, px, 5, pz, col);
            b.set(px, 6, pz, orange);
            b.set(px, 7, pz, stand(redLantern));
        }
        for (int[] g : new int[][]{{c - 3, 1}, {c + 3, 1}}) {
            b.fill(g[0], 1, g[1], g[0], 6, g[1], col);
            b.set(g[0], 7, g[1], Blocks.GREEN_GLAZED_TERRACOTTA);
        }
        b.fill(c - 3, 7, 0, c + 3, 7, 0, stairS(S(ArchBlocks.TEAL_ROOF_TILE_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.fill(c - 2, 7, 1, c + 2, 7, 1, orange);
        b.fill(c - 3, 8, 1, c + 3, 8, 1, slabS(S(ArchBlocks.TEAL_ROOF_TILE_SLAB), SlabType.BOTTOM));
        b.set(c, 6, 1, hang(redLantern));
        npc(b, "kuja_warrior", c - 3, 1, az);
        npc(b, "kuja_warrior", c + 3, 1, az);
        npc(b, "snakeneck_lancer", c, 1, 1); // the Snakeneck tribe serve the Kuja at the gate
        for (int z = 4; z <= 14; z += 5) {
            b.set(1, 1, z, Blocks.TARGET);
            b.set(w - 2, 1, z, Blocks.TARGET);
            b.set(1, 2, z, Blocks.HAY_BLOCK);
            b.set(w - 2, 2, z, Blocks.HAY_BLOCK);
        }
        // ---- the terrace (top y=3) with its balustrade and the grand stair flanked by serpents
        b.fill(2, 1, 18, 42, 3, 44, S(ArchBlocks.DRESSED_SANDSTONE));
        b.fill(3, 3, 19, 41, 3, 43, S(Blocks.SMOOTH_SANDSTONE));
        for (int x = 2; x <= 42; x++) {
            if (Math.abs(x - c) > 4) b.set(x, 4, 18, Arch.along(S(ArchBlocks.DRESSED_SANDSTONE_WALL), Direction.Axis.X));
            b.set(x, 4, 44, Arch.along(S(ArchBlocks.DRESSED_SANDSTONE_WALL), Direction.Axis.X));
        }
        for (int z = 18; z <= 44; z++) {
            b.set(2, 4, z, Arch.along(S(ArchBlocks.DRESSED_SANDSTONE_WALL), Direction.Axis.Z));
            b.set(42, 4, z, Arch.along(S(ArchBlocks.DRESSED_SANDSTONE_WALL), Direction.Axis.Z));
        }
        for (int[] p : new int[][]{{2, 18}, {42, 18}, {2, 44}, {42, 44}, {c - 5, 18}, {c + 5, 18}}) {
            b.set(p[0], 4, p[1], S(ArchBlocks.DRESSED_SANDSTONE));
            b.set(p[0], 5, p[1], stand(redLantern));
        }
        flight(b, sandSt, c + 4, 1, 15, Direction.SOUTH, 3, 9, S(ArchBlocks.DRESSED_SANDSTONE));
        snakeStatue(b, c - 6, 1, 16, Direction.NORTH);
        snakeStatue(b, c + 6, 1, 16, Direction.NORTH);
        for (int[] p : new int[][]{{5, 4, 21}, {39, 4, 21}, {6, 1, 13}, {38, 1, 13}, {5, 1, 5}, {39, 1, 5}}) palm(b, p[0], p[1], p[2], 7 + b.rand(p[0] + p[2], 3), p[0] * 31 + p[2]);
        for (int x = 7; x <= 11; x += 2) for (int z : new int[]{20, 23}) b.set(x, 4, z, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        for (int x = 33; x <= 37; x += 2) for (int z : new int[]{20, 23}) b.set(x, 4, z, decor(ArchBlocks.FLOWER_PLANTER, Direction.NORTH));
        // ---- the throne hall: walls x 14..30, z 24..38 (floor y=3), a veranda of red columns round it
        storeys(b, m, 14, 3, 24, 30, 38, 1, 8, S(Blocks.BAMBOO_PLANKS));
        for (int x = 12; x <= 32; x += 4) for (int z : new int[]{22, 40}) b.fill(x, 4, z, x, 9, z, col);
        for (int z = 22; z <= 40; z += 3) for (int x : new int[]{12, 32}) b.fill(x, 4, z, x, 9, z, col);
        for (int x = 12; x <= 32; x++) {
            b.set(x, 10, 22, orange);
            b.set(x, 10, 40, orange);
        }
        for (int z = 22; z <= 40; z++) {
            b.set(12, 10, z, orange);
            b.set(32, 10, z, orange);
        }
        // lower skirt roof round the hall, then the clerestory and the upper flared roof
        Arch.hip(b, m, 12, 22, 32, 40, 11, 1);
        b.clear(14, 15, 24, 30, 33, 38);
        b.fill(14, 11, 24, 30, 11, 38, m.floor);
        b.clear(16, 11, 26, 28, 11, 36);
        wallsS(b, 15, 12, 25, 29, 17, 37, m.wall);
        for (int[] p : new int[][]{{15, 25}, {29, 25}, {15, 37}, {29, 37}}) b.fill(p[0], 12, p[1], p[0], 17, p[1], col);
        wallsS(b, 15, 17, 25, 29, 17, 37, orange);
        for (int x = 17; x <= 27; x += 2) for (int y = 14; y <= 16; y++) {
            b.set(x, y, 25, Arch.along(m.window, Direction.Axis.X));
            b.set(x, y, 37, Arch.along(m.window, Direction.Axis.X));
        }
        for (int z = 27; z <= 35; z += 2) for (int y = 14; y <= 16; y++) {
            b.set(15, y, z, Arch.along(m.window, Direction.Axis.Z));
            b.set(29, y, z, Arch.along(m.window, Direction.Axis.Z));
        }
        int top = Arch.hip(b, m, 15, 25, 29, 37, 18, 2);
        b.fill(c - 1, top + 1, 31, c + 1, top + 1, 31, orange);
        for (int x : new int[]{c - 2, c + 2}) b.set(x, top, 31, Blocks.GREEN_GLAZED_TERRACOTTA);
        b.fill(16, 18, 26, 28, 18, 36, m.floor);
        for (int[] t : new int[][]{{13, 23}, {31, 23}, {13, 39}, {31, 39}}) b.set(t[0], 19, t[1], Blocks.GREEN_GLAZED_TERRACOTTA);
        for (int[] t : new int[][]{{11, 21}, {33, 21}, {11, 41}, {33, 41}}) b.set(t[0], 12, t[1], Blocks.GREEN_GLAZED_TERRACOTTA);
        // hall windows and doors
        for (int x : new int[]{17, 27}) Arch.window(b, m, x, 5, 24, Direction.NORTH, 3, false);
        for (int x : new int[]{17, 21, 25}) Arch.window(b, m, x, 5, 38, Direction.SOUTH, 3, false);
        for (int z : new int[]{27, 35}) {
            Arch.window(b, m, 14, 5, z, Direction.WEST, 3, false);
            Arch.window(b, m, 30, 5, z, Direction.EAST, 3, false);
        }
        for (int x = c - 1; x <= c + 1; x++) b.bigDoor(x, 4, 24, Blocks.JUNGLE_DOOR, Direction.NORTH);
        Arch.wallLantern(b, m, c - 2, 6, 24, Direction.NORTH);
        Arch.wallLantern(b, m, c + 2, 6, 24, Direction.NORTH);
        for (int x : new int[]{14, 30}) {
            b.clear(x, 4, 31, x, 6, 32);
        }
        // the throne hall inside: serpent-scale floor, red columns, the empress's throne with her serpent
        for (int x = 15; x <= 29; x++) for (int z = 25; z <= 37; z++) b.set(x, 3, z, ((x + z) & 1) == 0 ? Blocks.GREEN_TERRACOTTA : Blocks.LIME_TERRACOTTA);
        for (int z = 27; z <= 35; z += 4) {
            b.fill(17, 4, z, 17, 10, z, col);
            b.fill(27, 4, z, 27, 10, z, col);
        }
        carpet(b, c - 1, 4, 25, c + 1, 33, Blocks.PURPLE_CARPET);
        b.fill(c - 3, 4, 34, c + 3, 4, 37, Blocks.GOLD_BLOCK);
        b.fill(c - 2, 5, 36, c + 2, 5, 37, Blocks.PURPUR_BLOCK);
        b.set(c, 6, 36, stairS(S(Blocks.PURPUR_STAIRS), Direction.SOUTH, Half.BOTTOM));
        b.fill(c, 6, 37, c, 8, 37, Blocks.PURPUR_PILLAR);
        snakeStatue(b, c - 5, 4, 33, Direction.NORTH);
        snakeStatue(b, c + 5, 4, 33, Direction.NORTH);
        for (int z = 27; z <= 35; z += 4) b.set(c, 17, z, hang(redLantern));
        npc(b, "elder", c - 1, 5, 35);
        npc(b, "kuja_warrior", 19, 4, 30);
        npc(b, "kuja_warrior", 25, 4, 33);
        // ---- west pavilion: the bath and the bedchamber
        Mats pm = Mats.of(BuildStyle.WANO, b.seed + 1);
        pm.wall = m.wall;
        pm.upperWall = m.wall;
        pm.frame = col;
        pm.trim = orange;
        pm.base = m.base;
        pm.floor = S(Blocks.BAMBOO_PLANKS);
        roofTiles(pm, ArchBlocks.TEAL_ROOF_TILES, ArchBlocks.TEAL_ROOF_TILE_STAIRS, ArchBlocks.TEAL_ROOF_TILE_SLAB);
        pm.roofKind = Arch.RoofKind.FLARED;
        pm.eaveBrackets = false;
        pm.window = m.window;
        pm.shutter = null;
        pm.planters = false;
        for (int[] wg : new int[][]{{3, 10}, {34, 41}}) {
            storeys(b, pm, wg[0], 3, 26, wg[1], 40, 1, 7, S(Blocks.BAMBOO_PLANKS));
            Arch.hip(b, pm, wg[0], 26, wg[1], 40, 11, 1);
            int outer = wg[0] == 3 ? wg[0] : wg[1];
            Direction od = wg[0] == 3 ? Direction.WEST : Direction.EAST;
            for (int z = 29; z <= 37; z += 4) Arch.window(b, pm, outer, 5, z, od, 3, false);
            Arch.window(b, pm, (wg[0] + wg[1]) / 2, 5, 40, Direction.SOUTH, 3, false);
            int inner = wg[0] == 3 ? wg[1] : wg[0];
            b.bigDoor(inner, 4, 31, Blocks.JUNGLE_DOOR, wg[0] == 3 ? Direction.EAST : Direction.WEST);
            if (wg[0] == 3) b.bigDoor((wg[0] + wg[1]) / 2, 4, 26, Blocks.JUNGLE_DOOR, Direction.NORTH);
        }
        b.fill(5, 1, 28, 8, 1, 35, Blocks.SMOOTH_QUARTZ);
        b.fill(5, 2, 28, 8, 3, 35, Blocks.WATER);
        for (int z = 28; z <= 35; z++) {
            b.set(4, 3, z, Blocks.SMOOTH_QUARTZ);
            b.set(9, 3, z, Blocks.SMOOTH_QUARTZ);
        }
        b.set(4, 4, 27, Blocks.POTTED_FERN);
        b.set(9, 4, 27, Blocks.POTTED_FERN);
        b.hanging(6, 9, 31, ArchBlocks.RED_PAPER_LANTERN.get());
        b.fill(4, 4, 37, 9, 4, 39, Blocks.PINK_CARPET);
        b.bed(5, 4, 38, Blocks.PINK_BED, Direction.WEST);
        b.set(9, 4, 39, decor(ArchBlocks.FLOWER_PLANTER, Direction.WEST));
        npc(b, "kuja_warrior", 8, 4, 37);
        // ---- east pavilion: the kitchen (north) and the treasury (south: iron door, three levers, one is real)
        kitchenX(b, 35, 4, 27, Direction.SOUTH, Buildings.LOOT_TAVERN);
        b.set(40, 4, 27, Blocks.CRAFTING_TABLE);
        b.set(37, 4, 30, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(38, 4, 30, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(37, 5, 30, Blocks.MELON);
        b.set(40, 4, 30, decor(ArchBlocks.CARGO_SACKS, Direction.WEST));
        npc(b, "cook", 36, 4, 29);
        b.fill(35, 4, 33, 40, 8, 33, m.wall);
        b.fill(35, 9, 34, 40, 9, 39, m.wall);
        b.door(37, 4, 33, Blocks.IRON_DOOR, Direction.NORTH);
        b.set(38, 5, 32, lever(Direction.NORTH));
        b.set(35, 5, 32, lever(Direction.NORTH));
        b.set(40, 7, 32, lever(Direction.NORTH));
        b.chest(35, 4, 35, Direction.EAST, Buildings.LOOT_TREASURE);
        b.chest(40, 4, 35, Direction.WEST, Buildings.LOOT_TREASURE);
        for (int x = 35; x <= 40; x += 2) b.set(x, 4, 38, Blocks.GOLD_BLOCK);
        b.set(37, 4, 39, Blocks.EMERALD_BLOCK);
        b.hanging(37, 8, 36, Blocks.LANTERN);
        mark(b, "treasure", 37, 4, 35);
        b.hanging(37, 9, 30, ArchBlocks.RED_PAPER_LANTERN.get());
        b.marker("arc_anchor", c, 1, az, tag("arc", "warrior_isle"));
    }

    private GrandLineLandmarks() {}
}
