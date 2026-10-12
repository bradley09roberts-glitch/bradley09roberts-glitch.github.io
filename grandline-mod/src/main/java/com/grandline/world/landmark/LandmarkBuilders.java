package com.grandline.world.landmark;

import com.grandline.registry.ArchBlocks;
import com.grandline.registry.ModBlocks;
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

import static com.grandline.world.landmark.LandmarkBuildersA.*;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

/**
 * Builders for named set pieces. Coordinates are local to the landmark footprint (entrance side north, z = 0). The
 * small and generic pieces live here; the big canon set pieces of this group are in {@link LandmarkBuildersA}
 * (helpers, Marine HQ and branch base), {@link LandmarkBuildersA2} (Baratie, Arlong Park, Drum Castle),
 * {@link LandmarkBuildersA3} (Water 7, Tower of Justice, Impel Down, Mary Geoise) and {@link LandmarkBuildersA4}
 * (the giant trees and Laugh Tale).
 */
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

    // ================================================================== starter shrine

    /**
     * Starter shrine (9x9): a fieldstone platform with a timber pavilion under a hip roof in the island's own roof
     * tiles, an altar with the island's record book, lanterns on the beams, a bench for the elder and the starter
     * chest, flower beds and a stone lantern at the approach. Materials follow the island's style.
     */
    static void starterShrine(Builder b, Island island, BuildStyle st) {
        Mats m = Mats.of(st, b.seed);
        int w = b.w, c = w / 2;
        BlockState frame = m.timbered() ? m.frame : col(ArchBlocks.DARK_BEAM);
        b.foundation(0, 0, w - 1, b.d - 1, ArchBlocks.FIELDSTONE.get(), 10);
        b.clear(0, 1, 0, w - 1, 10, b.d - 1);
        LandmarkKit.patchGround(b, 3, 0, Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT);
        b.fill(1, 0, 1, 7, 0, 7, S(ArchBlocks.FIELDSTONE));
        b.fill(2, 0, 2, 6, 0, 6, S(Blocks.POLISHED_ANDESITE));
        b.fill(c, 0, 0, c, 0, 1, S(Blocks.DIRT_PATH));
        // pavilion: four posts, beam ring, hip roof
        for (int[] p : new int[][]{{2, 2}, {6, 2}, {2, 6}, {6, 6}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], frame);
        b.fill(2, 4, 2, 6, 4, 2, m.timbered() ? m.beam(Direction.Axis.X) : frame);
        b.fill(2, 4, 6, 6, 4, 6, m.timbered() ? m.beam(Direction.Axis.X) : frame);
        b.fill(2, 4, 3, 2, 4, 5, m.timbered() ? m.beam(Direction.Axis.Z) : frame);
        b.fill(6, 4, 3, 6, 4, 5, m.timbered() ? m.beam(Direction.Axis.Z) : frame);
        Mats rm = Mats.of(st, b.seed);
        rm.eaveBrackets = false;
        if (rm.roofKind == Arch.RoofKind.FLAT) {
            rm.roof = S(ArchBlocks.TERRACOTTA_ROOF_TILES);
            rm.roofStairs = S(ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS);
            rm.roofSlab = S(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB);
        }
        int top = Arch.hip(b, rm, 2, 2, 6, 6, 5, 1);
        b.fill(3, 4, 3, 5, 4, 5, S(Blocks.SPRUCE_PLANKS));
        b.set(c, top + 1, c, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), false));
        // altar with the record book, the elder's bench, the chest
        b.fill(3, 1, 5, 5, 1, 5, S(Blocks.CHISELED_STONE_BRICKS));
        b.set(c, 2, 5, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, NORTH));
        b.set(3, 2, 5, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(5, 2, 5, Blocks.POTTED_POPPY);
        b.set(3, 1, 3, decor(ArchBlocks.PLANK_BENCH, EAST));
        b.chest(5, 1, 3, WEST, "grandline:chests/starter");
        b.hanging(3, 3, 4, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(5, 3, 4, ArchBlocks.BRASS_LANTERN.get());
        // flower beds and a stone lantern at the approach
        for (int[] f : new int[][]{{1, 0}, {7, 0}, {0, 3}, {8, 3}, {0, 5}, {8, 5}})
            b.set(f[0], 1, f[1], b.rand(f[0] * 7 + f[1], 2) == 0 ? S(Blocks.POPPY) : S(Blocks.OXEYE_DAISY));
        b.set(1, 1, 8, decor(ArchBlocks.FLOWER_PLANTER, SOUTH));
        b.set(7, 1, 8, decor(ArchBlocks.FLOWER_PLANTER, SOUTH));
        b.set(2, 1, 0, S(ArchBlocks.FIELDSTONE_WALL));
        b.set(2, 2, 0, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), false));
        b.set(6, 1, 0, S(ArchBlocks.FIELDSTONE_WALL));
        b.set(6, 2, 0, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), false));
        b.marker("respawn_anchor", c, 1, 2, new CompoundTag());
        b.marker("npc", c, 1, 4, role("elder"));
    }

    // ================================================================== treasure cache

    /** Buried treasure (7x7): a sandy patch with a red X, a weathered marker post with a skull, rocks and the chest two blocks down. */
    static void treasureCache(Builder b, Island island, BuildStyle st) {
        b.foundation(0, 0, 6, 6, Blocks.COBBLESTONE, 6);
        b.clear(0, 1, 0, 6, 4, 6);
        LandmarkKit.patchGround(b, 17, 0, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.GRAVEL, Blocks.COARSE_DIRT);
        for (int i = -1; i <= 1; i++) {
            b.set(3 + i, 0, 3 + i, S(Blocks.RED_TERRACOTTA));
            b.set(3 + i, 0, 3 - i, S(Blocks.RED_TERRACOTTA));
        }
        b.fill(2, -3, 2, 4, -1, 4, Blocks.DIRT);
        b.chest(3, -2, 3, NORTH, Buildings.LOOT_TREASURE);
        b.marker("treasure", 3, -2, 3, new CompoundTag());
        // marker post with a skull, a dropped lantern, rocks
        b.fill(0, 1, 0, 0, 2, 0, col(ArchBlocks.WEATHERED_BEAM));
        b.set(0, 3, 0, Blocks.SKELETON_SKULL.defaultBlockState());
        b.set(1, 1, 0, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, EAST));
        b.set(6, 1, 1, S(Blocks.MOSSY_COBBLESTONE));
        b.set(5, 1, 6, S(Blocks.COBBLESTONE_WALL));
        b.set(6, 1, 5, S(ArchBlocks.FIELDSTONE_SLAB));
        b.set(1, 1, 5, decor(ArchBlocks.ROPE_COIL, NORTH));
        b.set(2, 1, 6, LandmarkKit.lantern(Blocks.LANTERN, false));
    }

    // ================================================================== ancient ruin

    /**
     * Ancient ruin (15x15): a raised temple platform of mossy and cracked stone with steps, a colonnade of weathered
     * marble columns (some whole with capitals and a stretch of architrave, some broken, one fallen across the
     * grass), and the ruined cella whose surviving vaulted apse shelters the poneglyph. Moss, vines and rubble; a
     * chest half buried in the rubble at the corner.
     */
    static void ancientRuin(Builder b, Island island, BuildStyle st) {
        int w = b.w;
        BlockState marble = col(ArchBlocks.MARBLE_COLUMN), chis = S(Blocks.CHISELED_STONE_BRICKS);
        b.foundation(0, 0, w - 1, b.d - 1, Blocks.MOSSY_STONE_BRICKS, 10);
        b.clear(0, 1, 0, w - 1, 12, b.d - 1);
        LandmarkKit.patchGround(b, 29, 0, Blocks.GRASS_BLOCK, Blocks.MOSSY_COBBLESTONE, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK);
        // platform with steps on the north side
        for (int x = 2; x <= 12; x++)
            for (int z = 2; z <= 12; z++) {
                int r = b.rand(x * 31 + z * 17, 6);
                b.set(x, 1, z, r == 0 ? S(Blocks.CRACKED_STONE_BRICKS) : r < 3 ? S(Blocks.MOSSY_STONE_BRICKS) : S(Blocks.STONE_BRICKS));
            }
        for (int x = 4; x <= 10; x++) b.set(x, 1, 1, up(S(Blocks.STONE_BRICK_STAIRS), SOUTH));
        b.set(4, 1, 1, up(S(Blocks.MOSSY_STONE_BRICK_STAIRS), SOUTH));
        LandmarkKit.scatter(b, 41, 10, 4, 4, 10, 10, 2, S(Blocks.MOSS_CARPET));
        // colonnade: 16 marble columns, some whole, some broken
        int[][] cols = {{3, 3}, {5, 3}, {7, 3}, {9, 3}, {11, 3}, {3, 11}, {5, 11}, {7, 11}, {9, 11}, {11, 11}, {3, 5}, {3, 7}, {3, 9}, {11, 5}, {11, 7}, {11, 9}};
        boolean[] whole = new boolean[cols.length];
        for (int i = 0; i < cols.length; i++) {
            int[] p = cols[i];
            int r = b.rand(i * 97 + 5, 10);
            int h = r < 4 ? 6 : r < 8 ? 1 + b.rand(i * 13, 4) : 0;
            whole[i] = h == 6;
            b.set(p[0], 2, p[1], chis);
            if (h > 0) b.fill(p[0], 3, p[1], p[0], 2 + h, p[1], marble);
            if (h == 6) b.set(p[0], 9, p[1], chis);
            else if (h == 0) LandmarkKit.heap(b, i * 7, p[0], p[1], 2, 1, 1, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS);
            if (h >= 3 && b.rand(i * 3 + 1, 2) == 0)
                b.set(p[0], 2 + h / 2, p[1] - 1, Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true));
        }
        // a stretch of architrave on the front row where neighbours stand whole
        for (int i = 0; i + 1 < 5; i++)
            if (whole[i] && whole[i + 1]) b.fill(cols[i][0], 10, 3, cols[i + 1][0], 10, 3, S(Blocks.STONE_BRICK_SLAB));
        // the cella: broken walls, the vaulted apse over the poneglyph
        for (int x = 5; x <= 9; x++)
            for (int z = 7; z <= 11; z++) {
                if (!(x == 5 || x == 9 || z == 11)) continue;
                int h = z >= 9 ? 5 : 1 + b.rand(x * 7 + z * 11, 4);
                for (int y = 2; y <= 1 + h; y++) b.set(x, y, z, b.rand(x + y * 5 + z * 3, 3) == 0 ? S(Blocks.CRACKED_STONE_BRICKS) : S(Blocks.MOSSY_STONE_BRICKS));
            }
        b.fill(5, 7, 9, 9, 7, 11, S(Blocks.MOSSY_STONE_BRICKS));
        b.fill(6, 6, 9, 8, 6, 10, Blocks.AIR.defaultBlockState());
        for (int x = 6; x <= 8; x++) b.set(x, 6, 9, top(S(Blocks.STONE_BRICK_STAIRS), SOUTH));
        b.fill(5, 8, 10, 9, 8, 11, S(Blocks.MOSSY_STONE_BRICK_SLAB));
        b.set(7, 2, 10, ModBlocks.PONEGLYPH.get().defaultBlockState());
        b.set(7, 3, 10, ModBlocks.PONEGLYPH.get().defaultBlockState());
        b.marker("poneglyph", 7, 2, 10, tag("kind", b.rand(3, 5) == 0 ? "road" : "historical"));
        b.set(6, 2, 9, LandmarkKit.candles(Blocks.CANDLE, 2));
        // the fallen column and rubble
        b.fill(2, 1, 13, 7, 1, 13, Arch.axis(S(ArchBlocks.MARBLE_COLUMN), Direction.Axis.X));
        b.set(1, 1, 13, chis);
        LandmarkKit.heap(b, 71, 12, 13, 1, 1, 1, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.GRAVEL);
        b.chest(1, 1, 12, EAST, Buildings.LOOT_RUIN);
        b.marker("npc", 7, 2, 6, role("ruin_guardian"));
    }

    // ================================================================== fruit tree grove

    /** A Devil Fruit tree (11x11): a gnarled trunk with a branch, a round canopy, the fruit bloom hidden in it, a ring of mossy stones and flowers. */
    static void fruitTreeGrove(Builder b, Island island, BuildStyle st) {
        b.foundation(0, 0, 10, 10, Blocks.DIRT, 6);
        b.fill(0, 0, 0, 10, 0, 10, Blocks.GRASS_BLOCK);
        b.clear(0, 1, 0, 10, 12, 10);
        int cx = 5, cz = 5;
        // stone ring with gaps
        for (int k = 0; k < 16; k++) {
            if (k % 4 == 1) continue;
            double a = k * Math.PI / 8;
            int x = cx + (int) Math.round(Math.cos(a) * 4.5), z = cz + (int) Math.round(Math.sin(a) * 4.5);
            b.set(x, 1, z, b.rand(k, 2) == 0 ? S(Blocks.MOSSY_COBBLESTONE) : S(ArchBlocks.FIELDSTONE));
        }
        // gnarled trunk, root flare, a side branch
        b.fill(cx, 1, cz, cx, 6, cz, col(Blocks.JUNGLE_LOG));
        for (Direction f : Direction.Plane.HORIZONTAL) b.set(cx + f.getStepX(), 1, cz + f.getStepZ(), Arch.axis(S(Blocks.JUNGLE_LOG), f.getAxis()));
        BlockState lv = LandmarkKit.leaves(Blocks.JUNGLE_LEAVES), az = LandmarkKit.leaves(Blocks.FLOWERING_AZALEA_LEAVES);
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                for (int y = 5; y <= 9; y++) {
                    double dd = x * x + z * z + (y - 7) * (y - 7) * 2.0;
                    if (dd > 12.5) continue;
                    if (x == 0 && z == 0 && y < 7) continue;
                    if (dd > 9 && b.rand(x * 31 + z * 17 + y, 4) == 0) continue;
                    b.set(cx + x, y, cz + z, b.rand(x * 7 + z * 3 + y * 11, 7) == 0 ? az : lv);
                }
        b.set(cx, 7, cz, col(Blocks.JUNGLE_LOG));
        b.set(cx - 1, 4, cz, Arch.axis(S(Blocks.JUNGLE_LOG), Direction.Axis.X));
        b.set(cx - 2, 5, cz, Arch.axis(S(Blocks.JUNGLE_LOG), Direction.Axis.X));
        b.set(cx + 1, 5, cz, ModBlocks.FRUIT_BLOOM.get().defaultBlockState());
        b.marker("fruit_spawn", cx + 1, 5, cz, new CompoundTag());
        for (int i = 0; i < 10; i++) {
            int x = 1 + b.rand(i, 9), z = 1 + b.rand(i + 9, 9);
            if (Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1) continue;
            b.set(x, 1, z, i % 3 == 0 ? S(Blocks.ALLIUM) : i % 3 == 1 ? S(Blocks.AZURE_BLUET) : S(Blocks.SHORT_GRASS));
        }
    }

    // ================================================================== windmill (Foosha / Dawn)

    /** True when (dx, dz) lies in an octagon of radius r (corners cut at 45 degrees). */
    private static boolean oct(int dx, int dz, double r) {
        return Math.max(Math.abs(dx), Math.abs(dz)) <= r && Math.abs(dx) + Math.abs(dz) <= r * 1.42;
    }

    /**
     * Foosha-style tower windmill (13x11): a seven-wide octagonal lower stage (fieldstone, then plaster) carrying a
     * railed reefing stage, a slimmer five-wide plaster upper stage banded with dark beams, a thatched boat cap and
     * four big lattice sails with canvas on the front; a flour shed with a lean-to on the side. Inside: the
     * millstone under the main shaft with sacks and barrels, a ladder up the shaft to the loft, whose door opens
     * onto the reefing stage.
     */
    static void windmill(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, cx = 6, cz = 6;
        BlockState plaster = S(ArchBlocks.WHITE_PLASTER), field = S(ArchBlocks.FIELDSTONE), planks = S(Blocks.SPRUCE_PLANKS);
        b.foundation(0, 0, w - 1, d - 1, Blocks.DIRT, 6);
        b.fill(0, 0, 0, w - 1, 0, d - 1, Blocks.GRASS_BLOCK);
        b.clear(0, 1, 0, w - 1, 22, d - 1);
        b.fill(cx, 0, 0, cx, 0, 2, Blocks.DIRT_PATH);
        // the lower stage: a seven-wide octagon, fieldstone below and plaster above, carrying the reefing stage
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (!oct(dx, dz, 3.5)) continue;
                boolean wall = !oct(dx, dz, 2.5);
                b.set(cx + dx, 0, cz + dz, field);
                for (int y = 1; y <= 4; y++) b.set(cx + dx, y, cz + dz, wall ? (y <= 3 ? field : plaster) : Blocks.AIR.defaultBlockState());
                b.set(cx + dx, 5, cz + dz, planks);
            }
        // the upper stage: a five-wide plaster octagon with dark beam bands
        for (int y = 6; y <= 12; y++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (!oct(dx, dz, 2.2)) continue;
                    boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2 || Math.abs(dx) + Math.abs(dz) == 3;
                    if (!wall) b.set(cx + dx, y, cz + dz, Blocks.AIR.defaultBlockState());
                    else b.set(cx + dx, y, cz + dz, y == 9 || y == 12 ? S(ArchBlocks.DARK_BEAM) : plaster);
                }
        // reefing stage: railing round the lower stage's roof
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                if (oct(dx, dz, 3.5) && !oct(dx, dz, 2.5)) b.set(cx + dx, 6, cz + dz, post(Blocks.SPRUCE_FENCE));
        b.clear(cx, 6, cz + 3, cx, 6, cz + 3);
        b.door(cx, 6, cz + 2, Blocks.SPRUCE_DOOR, SOUTH);
        // thatched boat cap, ridge along z (pointing into the wind)
        Mats cap = Mats.of(BuildStyle.VILLAGE, b.seed);
        cap.roof = S(ArchBlocks.THATCH);
        cap.roofStairs = S(ArchBlocks.THATCH_STAIRS);
        cap.roofSlab = S(ArchBlocks.THATCH_SLAB);
        cap.roofKind = Arch.RoofKind.GABLE;
        cap.eaveBrackets = false;
        b.fill(cx - 2, 13, cz - 2, cx + 2, 13, cz + 2, planks);
        Arch.gable(b, cap, cx - 2, cz - 2, cx + 2, cz + 2, 14, false, 1, S(ArchBlocks.WEATHERED_PLANKS));
        // windows, door, the sails' hub and axle
        for (int y : new int[]{2, 8, 11}) {
            int rr = y <= 4 ? 3 : 2;
            b.set(cx + rr, y, cz, Arch.along(S(ArchBlocks.MULLION_WINDOW), Direction.Axis.Z));
            b.set(cx - rr, y, cz, Arch.along(S(ArchBlocks.MULLION_WINDOW), Direction.Axis.Z));
        }
        b.bigDoor(cx, 1, cz - 3, Blocks.SPRUCE_DOOR, NORTH);
        b.set(cx, 4, cz - 4, decor(ArchBlocks.RED_AWNING, NORTH));
        int hy = 12, hz = 1;
        b.fill(cx, hy, 2, cx, hy, 3, Arch.axis(S(Blocks.SPRUCE_LOG), Direction.Axis.Z));
        b.set(cx, hy, hz, S(Blocks.SPRUCE_PLANKS));
        // four sails: a spar and a lattice canvas panel on the trailing side
        int L = 6;
        for (int k = 0; k < 4; k++) {
            int ux = k == 1 ? 1 : k == 3 ? -1 : 0, uy = k == 0 ? 1 : k == 2 ? -1 : 0; // spar direction in the x-y plane
            int vx = uy, vy = -ux; // trailing side
            for (int i = 1; i <= L; i++) {
                int sx = cx + ux * i, sy = hy + uy * i;
                b.set(sx, sy, hz, Arch.axis(S(Blocks.STRIPPED_SPRUCE_LOG), ux != 0 ? Direction.Axis.X : Direction.Axis.Y));
                if (i >= 2) {
                    b.set(sx + vx, sy + vy, hz, (i & 1) == 0 ? S(Blocks.WHITE_WOOL) : S(Blocks.SPRUCE_PLANKS));
                    b.set(sx + 2 * vx, sy + 2 * vy, hz, S(Blocks.WHITE_WOOL));
                }
            }
        }
        // interior: the millstone under the main shaft, sacks and barrels; a ladder to the loft
        b.set(cx, 1, cz, Blocks.GRINDSTONE.defaultBlockState());
        b.fill(cx, 2, cz, cx, 12, cz, col(Blocks.SPRUCE_LOG));
        b.set(cx - 2, 1, cz + 1, decor(ArchBlocks.CARGO_SACKS, EAST));
        b.set(cx - 2, 1, cz - 1, decor(ArchBlocks.CARGO_SACKS, EAST));
        b.set(cx - 1, 1, cz + 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(cx + 2, 1, cz + 1, decor(ArchBlocks.CARGO_SACKS, WEST));
        LandmarkKit.ladder(b, cx + 1, 1, cz, 5, EAST, null);
        b.set(cx - 1, 6, cz - 1, decor(ArchBlocks.CARGO_SACKS, SOUTH));
        b.set(cx - 1, 6, cz + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.hanging(cx - 1, 4, cz - 1, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(cx + 1, 12, cz - 1, ArchBlocks.BRASS_LANTERN.get());
        // flour shed on the east side with a lean-to
        b.fill(10, 0, 4, 12, 0, 9, S(Blocks.SPRUCE_PLANKS));
        b.fill(10, 1, 9, 12, 2, 9, S(ArchBlocks.WEATHERED_PLANKS));
        b.fill(12, 1, 4, 12, 2, 9, S(ArchBlocks.WEATHERED_PLANKS));
        b.fill(12, 1, 4, 12, 3, 4, col(ArchBlocks.WEATHERED_BEAM));
        b.fill(12, 1, 9, 12, 3, 9, col(ArchBlocks.WEATHERED_BEAM));
        Mats shed = Mats.of(BuildStyle.VILLAGE, b.seed);
        shed.roofSlab = S(ArchBlocks.THATCH_SLAB);
        Arch.leanTo(b, shed, 10, 3, 12, 10, 4, EAST);
        for (int z = 5; z <= 8; z++) Arch.cargo(b, 11, 1, z, z + 1);
        b.set(9, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(10, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(1, 1, 9, decor(ArchBlocks.PLANK_BENCH, EAST));
        b.marker("npc", cx, 1, cz - 2, role("miller"));
    }

    // ================================================================== thin delegations to the batch-A files

    static void baratie(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA2.baratie(b, island, st);
    }

    static void arlongPark(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA2.arlongPark(b, island, st);
    }

    static void marineBase(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA.marineBase(b, island, st);
    }

    static void marineHeadquarters(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA.marineHeadquarters(b, island, st);
    }

    static void drumCastle(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA2.drumCastle(b, island, st);
    }

    static void water7Fountain(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA3.water7Fountain(b, island, st);
    }

    static void towerOfJustice(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA3.towerOfJustice(b, island, st);
    }

    static void impelDown(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA3.impelDown(b, island, st);
    }

    static void maryGeoise(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA3.maryGeoise(b, island, st);
    }

    static void giantTree(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA4.treasureTree(b, island, st);
    }

    static void giantTreeRuined(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA4.treeOfKnowledge(b, island, st);
    }

    static void laughTaleRuins(Builder b, Island island, BuildStyle st) {
        LandmarkBuildersA4.laughTaleRuins(b, island, st);
    }

    // ================================================================== execution platform (Loguetown)

    /**
     * Loguetown's execution platform (13x13) on its cobbled square: a stone plinth carrying a tall dark-timber
     * scaffold with braces and a railed deck, the gallows frame with the stocks on top, a long timber stair up the
     * west side, Marine banners on the deck edge and lamp posts at the corners.
     */
    static void executionPlatform(Builder b, Island island, BuildStyle st) {
        BlockState beamY = col(ArchBlocks.DARK_BEAM), dark = S(Blocks.DARK_OAK_PLANKS);
        b.foundation(0, 0, 12, 12, Blocks.STONE_BRICKS, 8);
        b.clear(0, 1, 0, 12, 22, 12);
        LandmarkKit.patchGround(b, 13, 0, ArchBlocks.FIELDSTONE.get(), Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE, ArchBlocks.FIELDSTONE.get());
        // plinth
        b.fill(2, 1, 2, 10, 2, 10, S(Blocks.STONE_BRICKS));
        b.fill(2, 2, 2, 10, 2, 2, S(Blocks.STONE_BRICK_SLAB));
        b.fill(3, 1, 1, 9, 1, 1, up(S(Blocks.STONE_BRICK_STAIRS), SOUTH));
        // scaffold: corner and mid posts, brace rings, deck at y 11
        for (int x = 3; x <= 9; x += 3)
            for (int z = 3; z <= 9; z += 3) {
                if (x == 6 && z == 6) continue;
                b.fill(x, 3, z, x, 10, z, beamY);
            }
        for (int y : new int[]{6, 9}) {
            b.fill(3, y, 3, 9, y, 3, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            b.fill(3, y, 9, 9, y, 9, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
            b.fill(3, y, 4, 3, y, 8, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
            b.fill(9, y, 4, 9, y, 8, beam(ArchBlocks.DARK_BEAM, Direction.Axis.Z));
        }
        b.fill(2, 11, 2, 10, 11, 10, dark);
        ring(b, 2, 10, 2, 10, 10, dark);
        LandmarkKit.outline(b, 2, 12, 2, 10, 10, Blocks.DARK_OAK_FENCE);
        b.set(2, 12, 2, Blocks.AIR.defaultBlockState());
        b.set(2, 12, 3, LandmarkKit.connect(Blocks.DARK_OAK_FENCE, false, false, true, false));
        b.set(3, 12, 2, LandmarkKit.connect(Blocks.DARK_OAK_FENCE, false, true, false, false));
        // the gallows frame and stocks
        b.fill(4, 12, 7, 4, 17, 7, beamY);
        b.fill(8, 12, 7, 8, 17, 7, beamY);
        b.fill(3, 18, 7, 9, 18, 7, beam(ArchBlocks.DARK_BEAM, Direction.Axis.X));
        b.fill(5, 14, 7, 7, 14, 7, S(Blocks.DARK_OAK_SLAB));
        b.set(6, 15, 7, LandmarkKit.trapdoor(Blocks.DARK_OAK_TRAPDOOR, NORTH, Half.TOP, false));
        b.set(6, 17, 7, S(Blocks.CHAIN));
        b.set(6, 16, 7, S(Blocks.CHAIN));
        // the stair up the west side (two wide), on timber trestles
        LandmarkKit.stairsUp(b, 0, 1, 12, NORTH, 11, Blocks.DARK_OAK_STAIRS, 2, Blocks.DARK_OAK_FENCE);
        b.fill(0, 11, 1, 1, 11, 1, dark);
        b.clear(0, 12, 1, 1, 14, 1);
        // banners, lamps, the crowd barrier
        for (int x : new int[]{4, 8}) {
            b.set(x, 10, 1, wallBanner(Blocks.BLUE_WALL_BANNER, NORTH));
            b.set(x + 1, 10, 1, wallBanner(Blocks.WHITE_WALL_BANNER, NORTH));
        }
        b.set(11, 10, 6, wallBanner(Blocks.BLUE_WALL_BANNER, EAST));
        for (int[] l : new int[][]{{12, 0}, {12, 12}}) LandmarkKit.lampPost(b, l[0], 1, l[1], Blocks.IRON_BARS, 3, ArchBlocks.BRASS_LANTERN.get());
        b.marker("legend", 6, 12, 5, tag("story", "execution_platform"));
        b.marker("npc", 6, 1, 0, role("marine"));
        b.marker("npc", 10, 1, 1, role("marine"));
    }

    // ================================================================== lighthouse (Twin Capes; also the 7x7 port beacon)

    /**
     * Lighthouse for a 9x9 (Twin Capes) or 7x7 (port beacon) plot: a rock plinth, a round red-and-white banded tower
     * (radius 3, then 2 above the first gallery of windows), a spiral stair round a centre post up to the lantern
     * room, the railed gallery, glazed lantern with the light, and a verdigris copper dome. The keeper and the Log
     * Pose vendor work on the ground floor.
     */
    static void twinCapesLighthouse(Builder b, Island island, BuildStyle st) {
        int c = Math.min(b.w, b.d) / 2, cz = b.d / 2;
        boolean big = Math.min(b.w, b.d) >= 9;
        BlockState white = S(ArchBlocks.WHITE_PLASTER), red = S(Blocks.RED_TERRACOTTA), field = S(ArchBlocks.FIELDSTONE);
        b.foundation(0, 0, b.w - 1, b.d - 1, ArchBlocks.FIELDSTONE.get(), 12);
        b.clear(0, 1, 0, b.w - 1, 26, b.d - 1);
        b.fill(0, 0, 0, b.w - 1, 0, b.d - 1, S(Blocks.STONE));
        LandmarkKit.disc(b, c, cz, 0, big ? 4.2 : 3.4, field);
        if (big) {
            LandmarkKit.ring(b, c, cz, 1, 4, S(ArchBlocks.FIELDSTONE_SLAB));
            b.set(c, 1, cz - 4, Blocks.AIR.defaultBlockState());
        }
        // tower: radius 3 to y 8, radius 2 above; bands of three
        for (int y = 1; y <= 17; y++) {
            BlockState s = y <= 2 ? field : ((y / 3) % 2 == 0 ? white : red);
            if (y <= 8) {
                LandmarkKit.ring(b, c, cz, y, 3, s);
                LandmarkKit.disc(b, c, cz, y, 2.2, Blocks.AIR.defaultBlockState());
            } else {
                LandmarkKit.disc(b, c, cz, y, 2.9, s);
                LandmarkKit.disc(b, c, cz, y, 1.5, Blocks.AIR.defaultBlockState());
            }
        }
        LandmarkKit.disc(b, c, cz, 9, 2.9, S(ArchBlocks.PALE_MASONRY));
        LandmarkKit.disc(b, c, cz, 9, 1.5, Blocks.AIR.defaultBlockState());
        // gallery deck and railing, lantern room, dome
        LandmarkKit.disc(b, c, cz, 18, 3.2, S(ArchBlocks.PALE_MASONRY));
        LandmarkKit.ring(b, c, cz, 19, 3, Arch.connect(S(Blocks.IRON_BARS), false, false, false, false));
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                double dd = Math.hypot(dx, dz);
                if (dd > 2.5 && dd <= 3.5) {
                    boolean n = Math.hypot(dx, dz - 1) > 2.5 && Math.hypot(dx, dz - 1) <= 3.5, s = Math.hypot(dx, dz + 1) > 2.5 && Math.hypot(dx, dz + 1) <= 3.5;
                    boolean e = Math.hypot(dx + 1, dz) > 2.5 && Math.hypot(dx + 1, dz) <= 3.5, ww = Math.hypot(dx - 1, dz) > 2.5 && Math.hypot(dx - 1, dz) <= 3.5;
                    b.set(c + dx, 19, cz + dz, Arch.connect(S(Blocks.IRON_BARS), n, e, s, ww));
                }
            }
        for (int y = 19; y <= 21; y++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (!(dx == 0 && dz == 0) && !(y < 21 && dx == 1 && dz == -1)) b.set(c + dx, y, cz + dz, S(Blocks.GLASS));
        b.fill(c, 1, cz, c, 18, cz, col(ArchBlocks.DARK_BEAM));
        b.set(c, 19, cz, S(Blocks.SEA_LANTERN));
        b.set(c, 20, cz, S(Blocks.GLOWSTONE));
        b.set(c, 21, cz, S(Blocks.SEA_LANTERN));
        Mats cu = Mats.of(BuildStyle.VILLAGE, b.seed);
        cu.roof = S(Blocks.WAXED_OXIDIZED_CUT_COPPER);
        cu.roofStairs = S(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS);
        cu.roofSlab = S(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB);
        cu.eaveBrackets = false;
        b.fill(c - 1, 22, cz - 1, c + 1, 22, cz + 1, S(Blocks.WAXED_OXIDIZED_CUT_COPPER));
        int capTop = Arch.hip(b, cu, c - 1, cz - 1, c + 1, cz + 1, 22, 1);
        b.set(c, capTop + 1, cz, Blocks.LIGHTNING_ROD.defaultBlockState());
        // spiral stair: from the ring cell inside the door up to the lantern floor
        LandmarkBuildersA.spiral(b, S(Blocks.SPRUCE_STAIRS), c, cz, 1, 1, 18, 1);
        // door, windows, keeper's corner
        b.door(c, 1, cz - 3, Blocks.SPRUCE_DOOR, NORTH);
        b.set(c, 3, cz - 3, S(ArchBlocks.PALE_MASONRY));
        for (int y : new int[]{5, 12, 15}) {
            int r = y <= 8 ? 3 : 2;
            b.set(c + r, y, cz, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.Z));
            b.set(c - r, y, cz, Arch.along(S(Blocks.GLASS_PANE), Direction.Axis.Z));
        }
        b.set(c - 2, 1, cz + 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(c + 1, 1, cz + 2, Blocks.CARTOGRAPHY_TABLE);
        b.hanging(c + 2, 3, cz, ArchBlocks.BRASS_LANTERN.get());
        if (big) Arch.wallLantern(b, Mats.of(BuildStyle.VILLAGE, b.seed), c + 1, 2, cz - 3, NORTH);
        b.marker("npc", c + 2, 1, cz, role("lighthouse_keeper"));
        b.marker("log_pose_vendor", c - 2, 1, cz, new CompoundTag());
    }

    // ================================================================== Sea Train station

    /**
     * Puffing Tom station (15x11): the track along the north edge on sleepers, a platform under a terracotta canopy
     * on iron posts with benches and lamps, and the stuccoed station hall: low-pitched wings either side of a taller
     * centre with the clock gable. Inside: the ticket counter, timetable boards, the departure bell, waiting benches
     * and luggage.
     */
    static void seaTrainStation(Builder b, Island island, BuildStyle st) {
        int w = b.w, d = b.d, c = w / 2;
        Mats m = Mats.of(BuildStyle.VENETIAN, b.seed);
        m.roofKind = Arch.RoofKind.LOW_GABLE;
        BlockState harbour = S(ArchBlocks.HARBOUR_STONE), sand = S(ArchBlocks.DRESSED_SANDSTONE), trim = S(ArchBlocks.WHITE_PLASTER);
        b.foundation(0, 0, w - 1, d - 1, ArchBlocks.HARBOUR_STONE.get(), 10);
        b.clear(0, 1, 0, w - 1, 14, d - 1);
        b.fill(0, 0, 0, w - 1, 0, d - 1, harbour);
        // track: gravel bed, sleepers, rails
        b.fill(0, 0, 0, w - 1, 0, 0, S(Blocks.GRAVEL));
        for (int x = 0; x < w; x += 2) b.set(x, 0, 0, Arch.axis(S(ArchBlocks.DARK_BEAM), Direction.Axis.Z));
        b.fill(0, 1, 0, w - 1, 1, 0, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
        // platform with the yellow edge, canopy on posts
        b.fill(0, 0, 1, w - 1, 0, 1, S(Blocks.YELLOW_TERRACOTTA));
        for (int x = 1; x < w; x += 4) b.fill(x, 1, 3, x, 3, 3, post(Blocks.IRON_BARS));
        b.fill(0, 4, 1, w - 1, 4, 3, slabB(S(ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB)));
        for (int x = 0; x < w; x++) b.set(x, 3, 1, decor(ArchBlocks.BLUE_AWNING, NORTH));
        for (int x = 3; x < w - 1; x += 4) b.hanging(x, 3, 2, ArchBlocks.BRASS_LANTERN.get());
        b.set(3, 1, 3, decor(ArchBlocks.PLANK_BENCH, NORTH));
        b.set(11, 1, 3, decor(ArchBlocks.PLANK_BENCH, NORTH));
        // station hall: wings x 1..4 and 10..13, centre x 5..9; z 4..10
        int z1 = 4, z2 = d - 2;
        b.fill(1, 0, z1, w - 2, 0, z2, S(Blocks.POLISHED_ANDESITE));
        box(b, 1, 1, z1, w - 2, 4, z2, m.wall);
        b.fill(1, 1, z1, w - 2, 1, z1, sand);
        b.fill(1, 1, z2, w - 2, 1, z2, sand);
        b.clear(2, 1, z1 + 1, w - 3, 4, z2 - 1);
        corners(b, 1, 1, z1, w - 2, 4, z2, trim);
        b.fill(1, 5, z1, w - 2, 5, z2, S(Blocks.SPRUCE_PLANKS));
        Arch.gable(b, m, 1, z1, 4, z2, 6, true, 0, m.wall);
        Arch.gable(b, m, 10, z1, w - 2, z2, 6, true, 0, m.wall);
        // the taller centre with the clock gable (ridge along z: the gable faces the platform)
        box(b, 5, 5, z1, 9, 8, z2, m.wall);
        b.clear(6, 5, z1 + 1, 8, 8, z2 - 1);
        b.fill(5, 5, z1, 9, 5, z1, trim);
        Mats cm = Mats.of(BuildStyle.VENETIAN, b.seed);
        cm.roofKind = Arch.RoofKind.GABLE;
        Arch.gable(b, cm, 5, z1, 9, z2, 9, false, 0, m.wall);
        b.clear(6, 5, z1 + 1, 8, 5, z2 - 1);
        b.fill(5, 5, z1, 9, 9, z1, sand);
        b.fill(6, 6, z1, 8, 8, z1, S(Blocks.WHITE_CONCRETE));
        b.set(7, 7, z1, S(Blocks.BLACK_CONCRETE));
        b.set(7, 8, z1, S(Blocks.BLACK_CONCRETE));
        b.set(7, 7, z1 - 1, LandmarkKit.button(Blocks.POLISHED_BLACKSTONE_BUTTON, NORTH));
        b.set(7, 10, z1, Arch.along(S(ArchBlocks.MULLION_WINDOW), Direction.Axis.X));
        // doors and windows
        b.bigDoor(c, 1, z1, Blocks.SPRUCE_DOOR, NORTH);
        for (int x : new int[]{3, 11}) Arch.window(b, m, x, 2, z1, NORTH, 2, true);
        for (int x = 3; x <= 11; x += 4) Arch.window(b, m, x, 2, z2, SOUTH, 2, true);
        Arch.window(b, m, 1, 2, 7, WEST, 2, true);
        Arch.window(b, m, w - 2, 2, 7, EAST, 2, true);
        // interior: ticket counter, timetables, bell, benches, luggage
        for (int x = 5; x <= 9; x++) b.set(x, 1, 7, decor(ArchBlocks.SHOP_COUNTER, NORTH));
        b.set(6, 2, 7, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, NORTH));
        b.set(c - 2, 1, 8, Blocks.BELL.defaultBlockState());
        b.set(5, 3, 8, wallSign(Blocks.SPRUCE_WALL_SIGN, NORTH));
        b.set(9, 3, 8, wallSign(Blocks.SPRUCE_WALL_SIGN, NORTH));
        for (int z = 5; z <= 7; z += 2) {
            b.set(2, 1, z, decor(ArchBlocks.PLANK_BENCH, EAST));
            b.set(12, 1, z, decor(ArchBlocks.PLANK_BENCH, WEST));
        }
        Arch.cargo(b, 3, 1, 8, 1);
        Arch.cargo(b, 11, 1, 8, 0);
        b.hanging(c, 7, 6, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(3, 4, 7, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(11, 4, 7, ArchBlocks.BRASS_LANTERN.get());
        b.marker("npc", c, 1, 8, role("station_master"));
        b.marker("sea_train_stop", c, 1, 1, new CompoundTag());
    }

    // ================================================================== sword dojo

    /**
     * Sword dojo (15x13) in the Wano manner: a gravel court entered through a roofed gate between stone lanterns, the
     * hall raised on a stone plinth with a veranda all round, white plaster between dark posts, lattice screens, a
     * flared kawara hip roof; inside a pale training floor, calligraphy scrolls, the household shrine shelf with
     * candles, sword racks and straw targets.
     */
    static void swordDojo(Builder b, Island island, BuildStyle st) {
        Mats m = Mats.of(BuildStyle.WANO, b.seed);
        int w = b.w, d = b.d, c = w / 2;
        BlockState post = col(ArchBlocks.DARK_BEAM), plaster = S(ArchBlocks.WHITE_PLASTER), deck = S(Blocks.SPRUCE_PLANKS);
        b.foundation(0, 0, w - 1, d - 1, Blocks.STONE_BRICKS, 8);
        b.clear(0, 1, 0, w - 1, 14, d - 1);
        LandmarkKit.patchGround(b, 19, 0, Blocks.GRAVEL, Blocks.GRAVEL, Blocks.GRAVEL, Blocks.COARSE_DIRT);
        for (int z = 0; z <= 2; z++) b.set(c, 0, z, S(Blocks.SMOOTH_STONE));
        // plinth and veranda (y 1), hall x 2..12, z 4..10
        b.fill(1, 0, 3, 13, 0, 11, S(Blocks.STONE_BRICKS));
        b.fill(1, 1, 3, 13, 1, 11, deck);
        for (int x = c - 1; x <= c + 1; x++) b.set(x, 1, 2, up(S(Blocks.SPRUCE_STAIRS), SOUTH));
        int x1 = 2, x2 = 12, z1 = 4, z2 = 10;
        b.fill(x1, 1, z1, x2, 1, z2, S(Blocks.BIRCH_PLANKS));
        box(b, x1, 2, z1, x2, 5, z2, plaster);
        b.clear(x1 + 1, 2, z1 + 1, x2 - 1, 5, z2 - 1);
        for (int x = x1; x <= x2; x += 2) {
            b.fill(x, 2, z1, x, 5, z1, post);
            b.fill(x, 2, z2, x, 5, z2, post);
        }
        for (int z = z1; z <= z2; z += 3) {
            b.fill(x1, 2, z, x1, 5, z, post);
            b.fill(x2, 2, z, x2, 5, z, post);
        }
        b.fill(x1, 5, z1, x2, 5, z1, Arch.axis(S(ArchBlocks.DARK_BEAM), Direction.Axis.X));
        b.fill(x1, 5, z2, x2, 5, z2, Arch.axis(S(ArchBlocks.DARK_BEAM), Direction.Axis.X));
        b.fill(x1, 5, z1, x1, 5, z2, Arch.axis(S(ArchBlocks.DARK_BEAM), Direction.Axis.Z));
        b.fill(x2, 5, z1, x2, 5, z2, Arch.axis(S(ArchBlocks.DARK_BEAM), Direction.Axis.Z));
        // veranda posts carrying the eaves
        for (int[] p : new int[][]{{1, 3}, {13, 3}, {1, 11}, {13, 11}, {c - 2, 3}, {c + 2, 3}}) b.fill(p[0], 2, p[1], p[0], 5, p[1], post);
        b.fill(x1, 6, z1, x2, 6, z2, S(Blocks.DARK_OAK_PLANKS));
        int top = Arch.hip(b, m, x1, z1, x2, z2, 6, 1);
        b.fill(c - 2, top + 1, 7, c + 2, top + 1, 7, slabB(S(ArchBlocks.KAWARA_ROOF_TILE_SLAB)));
        // lattice screens and the open front
        b.clear(c - 1, 2, z1, c + 1, 4, z1);
        for (int x : new int[]{4, 10}) b.fill(x - 1, 2, z1, x, 4, z1, Arch.along(S(ArchBlocks.LATTICE_WINDOW), Direction.Axis.X));
        for (int z : new int[]{6, 8}) {
            b.fill(x1, 3, z, x1, 4, z, Arch.along(S(ArchBlocks.LATTICE_WINDOW), Direction.Axis.Z));
            b.fill(x2, 3, z, x2, 4, z, Arch.along(S(ArchBlocks.LATTICE_WINDOW), Direction.Axis.Z));
        }
        for (int x = 4; x <= 10; x += 3) b.fill(x, 3, z2, x, 4, z2, Arch.along(S(ArchBlocks.LATTICE_WINDOW), Direction.Axis.X));
        // the gate with its little kawara roof, stone lanterns
        b.fill(c - 2, 1, 0, c - 2, 4, 0, post);
        b.fill(c + 2, 1, 0, c + 2, 4, 0, post);
        b.fill(c - 3, 4, 0, c + 3, 4, 0, Arch.axis(S(ArchBlocks.DARK_BEAM), Direction.Axis.X));
        for (int x = c - 3; x <= c + 3; x++) b.set(x, 5, 0, slabB(S(ArchBlocks.KAWARA_ROOF_TILE_SLAB)));
        b.set(c - 3, 5, 0, up(S(ArchBlocks.KAWARA_ROOF_TILE_STAIRS), EAST));
        b.set(c + 3, 5, 0, up(S(ArchBlocks.KAWARA_ROOF_TILE_STAIRS), WEST));
        b.set(c, 3, 0, LandmarkKit.lantern(ArchBlocks.RED_PAPER_LANTERN.get(), true));
        for (int x : new int[]{2, 12}) {
            b.set(x, 1, 1, S(Blocks.STONE_BRICK_WALL));
            b.set(x, 2, 1, LandmarkKit.lantern(ArchBlocks.RED_PAPER_LANTERN.get(), false));
            b.set(x, 3, 1, S(Blocks.STONE_BRICK_SLAB));
        }
        for (int[] bb : new int[][]{{0, 12}, {14, 12}, {0, 11}}) {
            for (int y = 1; y <= 4; y++) b.set(bb[0], y, bb[1], Blocks.BAMBOO.defaultBlockState());
        }
        // interior: scrolls, the shrine shelf, racks, targets, mats
        for (int x = 4; x <= 10; x += 3) b.set(x, 4, z2 - 1, wallBanner(Blocks.WHITE_WALL_BANNER, NORTH));
        b.fill(c - 1, 4, z2 - 1, c + 1, 4, z2 - 1, slabT(S(Blocks.DARK_OAK_SLAB)));
        b.set(c, 5, z2 - 1, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.set(c - 1, 5, z2 - 1, Blocks.POTTED_BAMBOO);
        b.hanging(c, 5, 6, ArchBlocks.RED_PAPER_LANTERN.get());
        for (int z = 5; z <= 9; z += 2) {
            b.set(x1 + 1, 2, z, post(Blocks.DARK_OAK_FENCE));
            b.set(x1 + 1, 3, z, LandmarkKit.trapdoor(Blocks.DARK_OAK_TRAPDOOR, EAST, Half.TOP, false));
        }
        b.set(x2 - 1, 2, 6, Blocks.HAY_BLOCK);
        b.set(x2 - 1, 3, 6, Blocks.TARGET);
        b.set(x2 - 1, 2, 8, Blocks.HAY_BLOCK);
        b.set(x2 - 1, 3, 8, Blocks.TARGET);
        b.fill(c - 1, 2, 6, c + 1, 2, 7, S(Blocks.LIME_CARPET));
        b.marker("npc", c, 2, z2 - 2, role("master"));
        b.marker("npc", c - 2, 2, 6, role("student"));
    }

    // ================================================================== Knock Up Stream marker

    /** The Knock Up Stream launch point (5x5 on the sea): a churning ring of bubbles round a dark down-draught over a vent. */
    static void knockUpMarker(Builder b, Island island, BuildStyle st) {
        int c = b.w / 2;
        b.cylinder(c, c, -3, 0, 2, Blocks.WATER, false);
        b.cylinder(c, c, -4, -4, 2, Blocks.SOUL_SAND, false);
        for (int y = -3; y <= 0; y++) b.cylinder(c, c, y, y, 2, Blocks.BUBBLE_COLUMN, true);
        b.set(c, -4, c, Blocks.MAGMA_BLOCK);
        b.set(c, -5, c, Blocks.MAGMA_BLOCK);
        for (int y = -3; y <= 0; y++) b.set(c, y, c, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, true));
        b.marker("knock_up_stream", c, 0, c, new CompoundTag());
    }

    private LandmarkBuilders() {}
}
