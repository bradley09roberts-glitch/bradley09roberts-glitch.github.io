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

import static com.grandline.world.landmark.LandmarkBuildersA.*;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.WEST;

/** Landmark batch A, the great trees and the last island: Elbaf's Treasure Tree, Ohara's Tree of Knowledge, Laugh Tale. */
final class LandmarkBuildersA4 {
    private LandmarkBuildersA4() {}

    private static BlockState wood(Block b) {
        return Arch.axis(S(b), Direction.Axis.Y);
    }

    // ================================================================== Treasure Tree (Elbaf)

    /** Trunk radius of the Treasure Tree at height y: a wide root flare narrowing into a near-straight bole. */
    private static double treeR(int y) {
        return y < 8 ? 8 + (8 - y) * 0.55 : 8 - (y - 8) * 0.012;
    }

    /**
     * Elbaf's Treasure Tree (41x41): a colossal trunk of mixed spruce and dark bark flaring into six buttress roots,
     * a giant-sized doorway into the hollow bole, and a square spiral stair winding up the inside past two landings
     * that open onto branch walks with leaf crowns, to the lodge of the elders: a railed platform under a thatched
     * pavilion inside a canopy of several leaf lobes. The ground hall holds the hearth and a long table.
     */
    static void treasureTree(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        BlockState planks = S(Blocks.SPRUCE_PLANKS), fence = S(Blocks.SPRUCE_FENCE);
        BlockState lvA = LandmarkKit.leaves(Blocks.DARK_OAK_LEAVES), lvB = LandmarkKit.leaves(Blocks.OAK_LEAVES);
        b.clear(0, 1, 0, w - 1, 120, w - 1);
        b.foundation(0, 0, w - 1, w - 1, Blocks.DIRT, 10);
        LandmarkKit.patchGround(b, 23, 0, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.PODZOL, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT);
        // ---- buttress roots
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(15 + k * 60);
            for (double t = 0; t <= 1.0; t += 0.04) {
                double rr = 8 + t * 11;
                int h = (int) Math.round(8 * Math.pow(1 - t, 1.3));
                for (int s = -1; s <= 1; s++) {
                    int x = c + (int) Math.round(Math.cos(a) * rr - Math.sin(a) * s * 0.8);
                    int z = c + (int) Math.round(Math.sin(a) * rr + Math.cos(a) * s * 0.8);
                    for (int y = 0; y <= h - (s == 0 ? 0 : 1); y++) b.set(x, y, z, wood(b.rand(x * 7 + y * 3 + z, 4) == 0 ? Blocks.DARK_OAK_WOOD : Blocks.SPRUCE_WOOD));
                }
            }
        }
        // ---- trunk shell (hollow) up to the platform at y 96
        for (int y = 0; y <= 96; y++) {
            double inner = treeR(y) - 2.3;
            int R = (int) Math.ceil(treeR(y) + 0.8);
            for (int x = c - R; x <= c + R; x++)
                for (int z = c - R; z <= c + R; z++) {
                    double dd = Math.hypot(x - c, z - c);
                    // bark ridges: the outline swells and shrinks with the angle, twisting slowly upward
                    double r = treeR(y) + 0.75 * Math.sin(Math.atan2(z - c, x - c) * 7 + y * 0.04);
                    if (dd > r) continue;
                    if (dd <= inner && y > 0) {
                        b.set(x, y, z, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    int seg = (int) Math.floor((Math.atan2(z - c, x - c) + Math.PI) / (Math.PI / 10));
                    boolean streak = ((seg * 7 + y / 6) % 5) == 0;
                    b.set(x, y, z, wood(streak ? Blocks.DARK_OAK_WOOD : Blocks.SPRUCE_WOOD));
                }
        }
        // ground hall floor, hearth, long table, shields, lights
        LandmarkKit.disc(b, c, c, 0, treeR(0) - 2.3, S(Blocks.SPRUCE_PLANKS));
        b.set(c, 0, c, S(Blocks.COBBLESTONE));
        b.set(c, 1, c, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        for (int x = c - 2; x <= c + 2; x++) {
            b.set(x, 1, c + 2, decor(ArchBlocks.PLANK_TABLE, NORTH));
            if ((x & 1) == 0) b.set(x, 1, c + 3, up(S(Blocks.SPRUCE_STAIRS), SOUTH));
        }
        b.set(c - 2, 1, c - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        b.set(c + 2, 1, c - 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        // ---- the giant door on the north side
        int doorZ = c - (int) Math.ceil(treeR(1)) - 1;
        b.clear(c - 1, 1, doorZ, c + 1, 6, c - 5);
        b.fill(c - 1, 0, doorZ, c + 1, 0, c - 5, planks);
        b.set(c - 1, 6, doorZ + 1, top(S(Blocks.SPRUCE_STAIRS), EAST));
        b.set(c + 1, 6, doorZ + 1, top(S(Blocks.SPRUCE_STAIRS), WEST));
        b.hanging(c, 6, doorZ + 4, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(c, 6, c - 6, ArchBlocks.BRASS_LANTERN.get());
        // ---- landings at y 32 and 64, branch walks with leaf crowns
        int[] landings = {32, 64};
        for (int li = 0; li < landings.length; li++) {
            int ly = landings[li];
            LandmarkKit.disc(b, c, c, ly, treeR(ly) - 2.3, planks);
            for (int side = -1; side <= 1; side += 2) {
                boolean alongX = li == 0;
                for (int t = (int) treeR(ly) - 2; t <= 15; t++) {
                    for (int s = -1; s <= 1; s++) {
                        int x = alongX ? c + side * t : c + s, z = alongX ? c + s : c + side * t;
                        b.set(x, ly, z, Arch.axis(S(Blocks.SPRUCE_WOOD), alongX ? Direction.Axis.X : Direction.Axis.Z));
                        b.set(x, ly - 1, z, Arch.axis(S(Blocks.SPRUCE_WOOD), alongX ? Direction.Axis.X : Direction.Axis.Z));
                        if (Math.abs(s) == 1 && t > treeR(ly)) b.set(x, ly + 1, z, LandmarkKit.connected(Blocks.SPRUCE_FENCE, alongX ? Direction.Axis.X : Direction.Axis.Z));
                    }
                }
                int ex = alongX ? c + side * 15 : c, ez = alongX ? c : c + side * 15;
                LandmarkKit.ellipsoid(b, ex, ly + 4, ez, 4.5, 3.5, 4.5, lvA, false, -1);
                LandmarkKit.carveEllipsoid(b, ex, ly + 2, ez, 1.6, 1.4, 1.6, 0);
                b.set(ex, ly + 1, ez, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), false));
                // the doorway out through the bark
                int dx = alongX ? c + side * ((int) treeR(ly) - 1) : c, dz = alongX ? c : c + side * ((int) treeR(ly) - 1);
                for (int k = -2; k <= 2; k++) {
                    int ox = alongX ? dx + side * k : dx, oz = alongX ? dz : dz + side * k;
                    b.clear(ox, ly + 1, oz, ox, ly + 3, oz);
                    b.set(ox, ly, oz, planks);
                }
            }
            b.hanging(c + 5, ly + 3, c, ArchBlocks.BRASS_LANTERN.get());
            b.hanging(c - 5, ly + 3, c, ArchBlocks.BRASS_LANTERN.get());
        }
        // window slits up the bole
        for (int y = 12; y < 92; y += 9) {
            double a = (y * 37 % 360) * Math.PI / 180;
            int x = c + (int) Math.round(Math.cos(a) * (treeR(y) - 1)), z = c + (int) Math.round(Math.sin(a) * (treeR(y) - 1));
            b.clear(x, y, z, x, y + 1, z);
            int x2 = c + (int) Math.round(Math.cos(a) * (treeR(y) - 2)), z2 = c + (int) Math.round(Math.sin(a) * (treeR(y) - 2));
            b.clear(x2, y, z2, x2, y + 1, z2);
        }
        // ---- canopy: a broad crown and four lower lobes, limbs reaching into them
        for (int x = c - 18; x <= c + 18; x++)
            for (int z = c - 18; z <= c + 18; z++)
                for (int y = 86; y <= 116; y++) {
                    double v = Math.pow((x - c) / 15.5, 2) + Math.pow((y - 103) / 9.0, 2) + Math.pow((z - c) / 15.5, 2);
                    boolean lobe = v <= 1;
                    for (int k = 0; k < 4 && !lobe; k++) {
                        int lx = c + ((k & 1) == 0 ? -10 : 10), lz = c + ((k & 2) == 0 ? -10 : 10);
                        lobe = Math.pow((x - lx) / 8.0, 2) + Math.pow((y - 93) / 5.5, 2) + Math.pow((z - lz) / 8.0, 2) <= 1;
                    }
                    if (!lobe) continue;
                    if (b.rand(x * 131 + z * 17 + y * 7, 11) == 0) continue;
                    if (y <= 96 && Math.hypot(x - c, z - c) <= treeR(y) + 1.3) continue;
                    b.set(x, y, z, b.rand(x * 3 + y * 5 + z * 11, 4) == 0 ? lvB : lvA);
                }
        for (int k = 0; k < 4; k++) {
            int ox = (k & 1) == 0 ? -1 : 1, oz = (k & 2) == 0 ? -1 : 1;
            for (int t = 5; t <= 11; t++) {
                b.set(c + ox * t, 86 + t, c + oz * t, wood(Blocks.SPRUCE_WOOD));
                b.set(c + ox * t, 85 + t, c + oz * t, wood(Blocks.SPRUCE_WOOD));
            }
        }
        // ---- the elders' lodge: platform, rail, thatched pavilion in a carved hollow of the crown
        for (int y = 97; y <= 108; y++) LandmarkKit.disc(b, c, c, y, 9.5, Blocks.AIR.defaultBlockState());
        LandmarkKit.disc(b, c, c, 96, 8.4, planks);
        LandmarkKit.ring(b, c, c, 96, 8.6, wood(Blocks.SPRUCE_WOOD));
        for (int k = 0; k < 48; k++) {
            double a = k * Math.PI * 2 / 48;
            int x = c + (int) Math.round(Math.cos(a) * 8), z = c + (int) Math.round(Math.sin(a) * 8);
            b.set(x, 97, z, post(Blocks.SPRUCE_FENCE));
        }
        Mats g = Mats.of(BuildStyle.GIANT, b.seed);
        for (int[] p : new int[][]{{c - 5, c - 5}, {c + 5, c - 5}, {c - 5, c + 5}, {c + 5, c + 5}}) b.fill(p[0], 97, p[1], p[0], 101, p[1], wood(Blocks.SPRUCE_LOG));
        b.fill(c - 5, 102, c - 5, c + 5, 102, c + 5, planks);
        int roofTop = Arch.hip(b, g, c - 5, c - 5, c + 5, c + 5, 103, 1);
        b.set(c, roofTop + 1, c, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), false));
        b.set(c, 97, c, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        b.set(c, 96, c, S(Blocks.COBBLESTONE));
        for (int x = c - 3; x <= c + 3; x += 3) b.set(x, 97, c + 4, decor(ArchBlocks.PLANK_BENCH, NORTH));
        b.set(c - 4, 97, c + 2, decor(ArchBlocks.PLANK_TABLE, NORTH));
        b.set(c - 4, 98, c + 2, LandmarkKit.candles(Blocks.CANDLE, 3));
        b.chest(c + 3, 97, c + 3, WEST, Buildings.LOOT_TREASURE);
        b.hanging(c - 3, 101, c - 3, ArchBlocks.BRASS_LANTERN.get());
        b.hanging(c + 3, 101, c + 3, ArchBlocks.BRASS_LANTERN.get());
        // ---- the spiral stair inside the bole: from the hall floor to the lodge
        spiral(b, S(Blocks.SPRUCE_STAIRS), c, c, 4, 1, 96, 4);
        // people
        LandmarkKit.npc(b, "giant_elder", c + 2, 97, c);
        LandmarkKit.npc(b, "giant_warrior", c + 3, 1, doorZ - 2);
    }

    // ================================================================== Tree of Knowledge (Ohara)

    /**
     * Ohara's Tree of Knowledge after the Buster Call (31x31): the burnt hollow trunk of the great library tree, its
     * top broken off in a jagged charred rim with dead limbs, the bark streaked with char. Inside, three levels of
     * bookshelves line the walls, some burnt out, with two galleries reached by stairs; the poneglyph stands in the
     * open atrium, scholars' tables and a chest of salvaged writings nearby, and one green sapling is coming up through
     * the ash.
     */
    static void treeOfKnowledge(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        BlockState shelf = S(Blocks.BOOKSHELF), floorP = S(Blocks.DARK_OAK_PLANKS), rail = S(Blocks.DARK_OAK_FENCE);
        b.clear(0, 1, 0, w - 1, 40, w - 1);
        b.foundation(0, 0, w - 1, w - 1, Blocks.DIRT, 10);
        LandmarkKit.patchGround(b, 37, 0, Blocks.COARSE_DIRT, Blocks.GRAVEL, Blocks.GRASS_BLOCK, Blocks.SOUL_SOIL, Blocks.COARSE_DIRT);
        // ---- trunk with a jagged burnt top
        for (int y = 0; y <= 32; y++) {
            double r = y < 5 ? 10 + (5 - y) * 0.7 : 10;
            int R = (int) Math.ceil(r);
            for (int x = c - R; x <= c + R; x++)
                for (int z = c - R; z <= c + R; z++) {
                    double dd = Math.hypot(x - c, z - c);
                    if (dd > r) continue;
                    int seg = (int) Math.floor((Math.atan2(z - c, x - c) + Math.PI) / (Math.PI / 8));
                    int topY = 21 + b.rand(seg * 13 + 5, 9);
                    if (y > topY) continue;
                    if (dd <= 8.0 && y > 0) {
                        b.set(x, y, z, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    int h = b.rand(x * 31 + y * 7 + z * 13, 20);
                    boolean charred = y > topY - 3 || h < 4;
                    BlockState s = charred ? (h % 2 == 0 ? S(Blocks.BLACKSTONE) : wood(Blocks.BASALT)) : h < 7 ? wood(Blocks.STRIPPED_DARK_OAK_WOOD) : wood(Blocks.DARK_OAK_WOOD);
                    b.set(x, y, z, s);
                }
        }
        // dead charred limbs at the top
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(30 + k * 90);
            for (int t = 9; t <= 13; t++) {
                int x = c + (int) Math.round(Math.cos(a) * t), z = c + (int) Math.round(Math.sin(a) * t);
                b.set(x, 19 + (t - 9) / 2, z, Arch.axis(S(Blocks.BASALT), Math.abs(Math.cos(a)) > 0.7 ? Direction.Axis.X : Direction.Axis.Z));
            }
        }
        // ---- floors: ground (with ash and char), two galleries (annulus 5..8) with rails
        LandmarkKit.disc(b, c, c, 0, 8, floorP);
        LandmarkKit.scatter(b, 51, 18, c - 7, c - 7, c + 7, c + 7, 0, S(Blocks.SOUL_SOIL));
        for (int gy : new int[]{7, 14}) {
            for (int x = c - 8; x <= c + 8; x++)
                for (int z = c - 8; z <= c + 8; z++) {
                    double dd = Math.hypot(x - c, z - c);
                    if (dd > 5.0 && dd <= 8.0) b.set(x, gy, z, (b.rand(x * 3 + z * 7 + gy, 9) == 0) ? S(Blocks.COAL_BLOCK) : floorP);
                    if (dd > 4.5 && dd <= 5.5) b.set(x, gy + 1, z, post(Blocks.DARK_OAK_FENCE));
                }
        }
        // bookshelves lining the wall on every level, some burnt out
        for (int y = 1; y <= 20; y++) {
            if (y == 7 || y == 14) continue;
            for (int x = c - 8; x <= c + 8; x++)
                for (int z = c - 8; z <= c + 8; z++) {
                    double dd = Math.hypot(x - c, z - c);
                    if (dd <= 7.2 || dd > 8.0) continue;
                    int r = b.rand(x * 17 + y * 29 + z * 3, 9);
                    b.set(x, y, z, r < 6 ? shelf : r < 7 ? S(Blocks.CHISELED_BOOKSHELF) : r < 8 ? S(Blocks.COAL_BLOCK) : Blocks.AIR.defaultBlockState());
                }
        }
        // ---- the north door
        b.clear(c - 2, 1, c - 14, c + 2, 5, c - 7);
        b.fill(c - 2, 0, c - 14, c + 2, 0, c - 7, floorP);
        b.set(c - 2, 5, c - 10, top(S(Blocks.DARK_OAK_STAIRS), EAST));
        b.set(c + 2, 5, c - 10, top(S(Blocks.DARK_OAK_STAIRS), WEST));
        // ---- stairs to the galleries: east flight along the south side, west flight along the north side
        Arch.staircase(b, S(Blocks.DARK_OAK_STAIRS), c - 3, 1, c + 6, EAST, 7);
        Arch.staircase(b, S(Blocks.DARK_OAK_STAIRS), c + 3, 8, c - 6, WEST, 7);
        for (int x = c - 3; x <= c + 3; x++) {
            b.set(x, 0, c + 6, floorP);
            for (int y = 1; y < 1 + (x - (c - 3)); y++) b.set(x, y, c + 6, floorP);
        }
        for (int x = c - 3; x <= c + 3; x++)
            for (int y = 8; y < 8 + ((c + 3) - x); y++) b.set(x, y, c - 6, floorP);
        // ---- the atrium: poneglyph, scholars' tables, salvaged writings, a sapling
        b.fill(c - 1, 0, c - 1, c + 1, 0, c + 1, S(Blocks.CHISELED_STONE_BRICKS));
        b.set(c, 1, c, ModBlocks.PONEGLYPH.get().defaultBlockState());
        b.set(c, 2, c, ModBlocks.PONEGLYPH.get().defaultBlockState());
        b.marker("poneglyph", c, 1, c, LandmarkKit.tag("kind", "historical"));
        Arch.tableSet(b, c - 4, 1, c + 2, S(Blocks.DARK_OAK_STAIRS));
        b.set(c - 4, 2, c + 2, LandmarkKit.candles(Blocks.CANDLE, 2));
        b.set(c - 4, 1, c - 2, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, EAST));
        b.chest(c + 3, 1, c + 3, WEST, Buildings.LOOT_RUIN);
        b.set(c + 4, 1, c + 1, decor(ArchBlocks.CARGO_SACKS, WEST));
        b.set(c + 2, 0, c - 3, S(Blocks.GRASS_BLOCK));
        b.set(c + 2, 1, c - 3, S(Blocks.OAK_SAPLING));
        for (int[] l : new int[][]{{c - 6, c}, {c + 6, c}}) b.set(l[0], 1, l[1], LandmarkKit.lantern(Blocks.LANTERN, false));
        b.set(c, 1, c + 4, Blocks.COBWEB.defaultBlockState());
        // a little green coming back on the broken rim
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(k * 72 + 10);
            int x = c + (int) Math.round(Math.cos(a) * 9), z = c + (int) Math.round(Math.sin(a) * 9);
            int seg = (int) Math.floor((Math.atan2(z - c, x - c) + Math.PI) / (Math.PI / 8));
            int topY = 21 + b.rand(seg * 13 + 5, 9);
            b.set(x, topY + 1, z, LandmarkKit.leaves(Blocks.AZALEA_LEAVES));
        }
        for (int i = 0; i < 6; i++) b.set(1 + b.rand(i * 3, w - 2), 1, b.rand(i * 3 + 1, 4), S(Blocks.DEAD_BUSH));
        b.marker("npc", c - 3, 1, c, LandmarkKit.role("scholar"));
    }

    // ================================================================== Laugh Tale

    private static final String[] LAUGHING_FACE = {
            "..GGG..",
            ".GGGGG.",
            "GG.G.GG",
            "GGGGGGG",
            "G.GGG.G",
            "GG...GG",
            ".GGGGG."};

    /**
     * Laugh Tale (25x25): a stepped temple of weathered stone with gilded bands, overgrown with moss and vines, a
     * grand stair up the north face to the summit shrine (columns, a gilded roof, the laughing face laid in gold in
     * its floor, the treasure chest), and the inner chamber reached by a tunnel from the south, where the final Road
     * Poneglyph stands lit by soul fire. Palms at the corners.
     */
    static void laughTaleRuins(Builder b, Island island, BuildStyle st) {
        int w = b.w, c = w / 2;
        BlockState gild = S(Blocks.GILDED_BLACKSTONE), dark = S(Blocks.POLISHED_BLACKSTONE), gold = S(Blocks.GOLD_BLOCK);
        b.foundation(0, 0, w - 1, w - 1, Blocks.MOSSY_STONE_BRICKS, 12);
        b.clear(0, 1, 0, w - 1, 30, w - 1);
        LandmarkKit.patchGround(b, 61, 0, Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT, Blocks.GRASS_BLOCK);
        // ---- three tiers
        int[][] tiers = {{2, 1, 3}, {5, 4, 6}, {8, 7, 9}};
        for (int[] t : tiers) {
            int o = t[0];
            for (int x = o; x <= w - 1 - o; x++)
                for (int z = o; z <= w - 1 - o; z++)
                    for (int y = t[1]; y <= t[2]; y++) {
                        int r = b.rand(x * 31 + y * 7 + z * 17, 10);
                        b.set(x, y, z, r < 3 ? S(Blocks.MOSSY_STONE_BRICKS) : r < 4 ? S(Blocks.CRACKED_STONE_BRICKS) : S(Blocks.STONE_BRICKS));
                    }
            ring(b, o, t[2], o, w - 1 - o, w - 1 - o, gild);
            for (int x = o + 2; x <= w - 3 - o; x += 4) {
                b.set(x, t[1] + 1, o, S(Blocks.CHISELED_STONE_BRICKS));
                b.set(x, t[1] + 1, w - 1 - o, S(Blocks.CHISELED_STONE_BRICKS));
                b.set(o, t[1] + 1, x, S(Blocks.CHISELED_STONE_BRICKS));
                b.set(w - 1 - o, t[1] + 1, x, S(Blocks.CHISELED_STONE_BRICKS));
            }
        }
        // ---- the inner chamber and its tunnel from the south
        b.clear(7, 1, 7, 17, 5, 17);
        b.fill(7, 0, 7, 17, 0, 17, S(Blocks.POLISHED_BLACKSTONE_BRICKS));
        b.clear(c - 1, 1, 17, c + 1, 3, w - 3);
        b.fill(c - 1, 0, 17, c + 1, 0, w - 3, S(Blocks.POLISHED_BLACKSTONE_BRICKS));
        b.set(c - 1, 3, w - 3, top(S(Blocks.STONE_BRICK_STAIRS), EAST));
        b.set(c + 1, 3, w - 3, top(S(Blocks.STONE_BRICK_STAIRS), WEST));
        b.fill(c - 2, 4, w - 3, c + 2, 4, w - 3, gild);
        for (int z = 8; z <= 16; z += 4) {
            b.fill(8, 1, z, 8, 4, z, col(ArchBlocks.SANDSTONE_COLUMN));
            b.fill(16, 1, z, 16, 4, z, col(ArchBlocks.SANDSTONE_COLUMN));
        }
        b.fill(c - 1, 1, 10, c + 1, 3, 10, ModBlocks.PONEGLYPH.get().defaultBlockState());
        b.fill(c - 2, 0, 9, c + 2, 0, 11, gild);
        b.marker("poneglyph", c, 1, 10, LandmarkKit.tag("kind", "final"));
        for (int x : new int[]{9, 15}) {
            b.set(x, 1, 11, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            b.set(x, 0, 11, S(Blocks.POLISHED_BLACKSTONE));
        }
        b.hanging(c, 5, 14, Blocks.SOUL_LANTERN);
        b.set(c, 6, 14, S(Blocks.STONE_BRICKS));
        for (int x = 9; x <= 15; x += 3) {
            b.set(x, 3, 6, S(Blocks.CHISELED_STONE_BRICKS));
            if (x != c) b.set(x, 3, 18, S(Blocks.CHISELED_STONE_BRICKS));
        }
        // ---- the grand stair up the north face with balustrades
        for (int i = 0; i <= 8; i++) {
            int z = i, y = 1 + i;
            for (int x = c - 2; x <= c + 2; x++) {
                b.fill(x, 1, z, x, y - 1, z, S(Blocks.STONE_BRICKS));
                b.set(x, y, z, up(S(Blocks.STONE_BRICK_STAIRS), SOUTH));
                b.clear(x, y + 1, z, x, y + 3, z);
            }
            for (int x : new int[]{c - 3, c + 3}) {
                b.fill(x, 1, z, x, y, z, S(Blocks.MOSSY_STONE_BRICKS));
                b.set(x, y + 1, z, LandmarkKit.connected(Blocks.MOSSY_STONE_BRICK_WALL, Direction.Axis.Z));
            }
        }
        // ---- the summit shrine with the laughing face in gold
        pixels(b, LAUGHING_FACE, c + 3, 9, c + 3, -1, 0, 0, 0, 0, -1, ch -> ch == 'G' ? gold : dark);
        for (int[] p : new int[][]{{9, 9}, {15, 9}, {9, 15}, {15, 15}, {9, 12}, {15, 12}}) {
            b.fill(p[0], 10, p[1], p[0], 13, p[1], col(ArchBlocks.SANDSTONE_COLUMN));
            b.set(p[0], 10, p[1], S(Blocks.CHISELED_STONE_BRICKS));
        }
        b.fill(8, 14, 8, 16, 14, 16, gild);
        b.clear(9, 14, 9, 15, 14, 15);
        b.fill(9, 14, 9, 15, 14, 9, gild);
        b.fill(9, 14, 15, 15, 14, 15, gild);
        b.fill(9, 14, 10, 9, 14, 14, gild);
        b.fill(15, 14, 10, 15, 14, 14, gild);
        Mats roof = Mats.of(BuildStyle.PALACE, b.seed);
        roof.roof = S(Blocks.POLISHED_BLACKSTONE_BRICKS);
        roof.roofStairs = S(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS);
        roof.roofSlab = S(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        roof.roofKind = Arch.RoofKind.HIP;
        roof.eaveBrackets = false;
        int top = Arch.hip(b, roof, 9, 9, 15, 15, 15, 1);
        b.set(c, top + 1, c, gold);
        b.set(c, top + 2, c, Blocks.LIGHTNING_ROD.defaultBlockState());
        b.chest(c, 10, 14, NORTH, Buildings.LOOT_TREASURE);
        b.set(c - 1, 10, 14, LandmarkKit.candles(Blocks.CANDLE, 4));
        b.set(c + 1, 10, 14, LandmarkKit.candles(Blocks.CANDLE, 4));
        b.set(10, 10, 10, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), false));
        b.set(14, 10, 10, LandmarkKit.lantern(ArchBlocks.BRASS_LANTERN.get(), false));
        // ---- overgrowth: moss carpets on the terraces, vines down the faces, palms at the corners
        for (int x = 2; x <= w - 3; x++)
            for (int z = 2; z <= w - 3; z++) {
                boolean t1 = x < 5 || x > w - 6 || z < 5 || z > w - 6, t2 = !t1 && (x < 8 || x > w - 9 || z < 8 || z > w - 9);
                if (Math.abs(x - c) <= 3 && z < 9) continue;
                if ((t1 || t2) && b.rand(x * 41 + z * 7, 5) == 0) b.set(x, t1 ? 4 : 7, z, S(Blocks.MOSS_CARPET));
            }
        for (int i = 0; i < 14; i++) {
            int x = 3 + b.rand(i * 5, w - 6);
            int side = b.rand(i * 5 + 1, 4);
            int o = 2 + 3 * b.rand(i * 5 + 2, 2);
            Direction f = Direction.from2DDataValue(side);
            int vx = f.getAxis() == Direction.Axis.X ? (f == EAST ? w - o : o - 1) : x;
            int vz = f.getAxis() == Direction.Axis.Z ? (f == SOUTH ? w - o : o - 1) : x;
            if (Math.abs(vx - c) <= 3 && vz < 9) continue;
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(f.getOpposite()), true);
            int ytop = o == 2 ? 3 : 6;
            for (int y = ytop; y >= ytop - 1 - b.rand(i, 2); y--) b.set(vx, y, vz, vine);
        }
        for (int[] q : new int[][]{{1, 1}, {w - 2, 1}, {1, w - 2}, {w - 2, w - 2}}) {
            LandmarkKit.ellipsoid(b, q[0], 1, q[1], 1.2, 1.4, 1.2, LandmarkKit.leaves(Blocks.JUNGLE_LEAVES), false, 0);
            b.set(q[0], 1, q[1], wood(Blocks.JUNGLE_LOG));
        }
        b.marker("arc_anchor", 5, 1, 1, LandmarkKit.tag("arc", "final_island"));
    }
}
