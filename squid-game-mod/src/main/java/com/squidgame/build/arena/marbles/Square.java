package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The pairing square x[-15,15] z[-9,15]: stone paving with a glowing circle / triangle / square inlay (the three
 * symbols nested), a roofed well on the west side, a big flowering tree on the east side with benches around it,
 * lantern posts at the corners and a raised wooden stage for the managers on the south edge.
 * The hall's arcade runs along the north edge (z -9..-7, built by {@link Hall}).
 */
final class Square {
    private Square() {
    }

    static final int CX = 0, CZ = 3;
    static final int WELL_X = -10, WELL_Z = -2;
    static final int TREE_X = 10, TREE_Z = -2;
    static final int STAGE_X0 = -8, STAGE_X1 = 8, STAGE_Z0 = 12, STAGE_Z1 = 15;

    static void build(BuildContext c) {
        paving(c);
        symbols(c);
        well(c, WELL_X, WELL_Z);
        tree(c, TREE_X, TREE_Z);
        stage(c);
        furniture(c);
    }

    // ------------------------------------------------------------------ paving

    private static void paving(BuildContext c) {
        c.pattern(Layout.SQ_X0, 0, Layout.SQ_Z0, Layout.SQ_X1, 0, Layout.SQ_Z1, (x, y, z) -> {
            double r = U.rand(x, z, 301);
            // 4x4 slab grid with joint lines
            boolean joint = Math.floorMod(x + 1, 4) == 0 || Math.floorMod(z + 1, 4) == 0;
            double d = Math.hypot(x - CX, (z - CZ) * 1.0);
            if (joint) {
                return r < 0.25 ? Mat.SB_CRACK : Mat.COBBLE;
            }
            // lighter inside the medallion radius
            if (d < 9.5) {
                String[] pal = {Mat.P_ANDESITE, Mat.SMOOTH, Mat.ANDESITE, Mat.SB};
                return U.pick(r, pal, new double[]{45, 30, 15, 10});
            }
            String[] pal = {Mat.SB, Mat.SB_CRACK, Mat.SB_MOSS, Mat.ANDESITE, Mat.P_ANDESITE};
            return U.pick(r, pal, new double[]{50, 14, 8, 14, 14});
        });
    }

    /** Circle, triangle and square, nested, in glowing inlay: lights the whole square from the floor. */
    private static void symbols(BuildContext c) {
        String glow = Mat.GLOW;
        // circle r = 6
        c.ring(CX, 0, CZ, 6.0, glow);
        // square side 13 around the circle
        c.fill(CX - 6, 0, CZ - 6, CX + 6, 0, CZ - 6, glow);
        c.fill(CX - 6, 0, CZ + 6, CX + 6, 0, CZ + 6, glow);
        c.fill(CX - 6, 0, CZ - 6, CX - 6, 0, CZ + 6, glow);
        c.fill(CX + 6, 0, CZ - 6, CX + 6, 0, CZ + 6, glow);
        // triangle inscribed in the circle: apex north
        int ax = CX, az = CZ - 6;
        int bx = CX + 5, bz = CZ + 3;
        int cx2 = CX - 5, cz2 = CZ + 3;
        c.line(ax, 0, az, bx, 0, bz, glow);
        c.line(bx, 0, bz, cx2, 0, cz2, glow);
        c.line(cx2, 0, cz2, ax, 0, az, glow);
        // second thin ring of white concrete around the symbols
        c.ring(CX, 0, CZ, 9.0, Mat.concrete("white"));
    }

    // ------------------------------------------------------------------ well

    private static void well(BuildContext c, int cx, int cz) {
        // stone rim (ring r=2) 2 blocks high, water inside
        c.disc(cx, 0, cz, 1.6, Mat.SB_MOSS);
        c.disc(cx, 0, cz, 1.2, "minecraft:water");
        c.ring(cx, 1, cz, 2.0, Mat.SB_MOSS);
        c.ring(cx, 2, cz, 2.0, Mat.SB);
        c.ring(cx, 3, cz, 2.0, Mat.slabB(Mat.SB_SL));
        // water inside the rim shaft
        // posts and beam
        for (int s = -1; s <= 1; s += 2) {
            c.fill(cx + 2 * s, 3, cz, cx + 2 * s, 7, cz, Mat.log(Mat.LOG, "y"));
        }
        c.fill(cx - 2, 8, cz, cx + 2, 8, cz, Mat.log(Mat.LOG_S, "x"));
        // small gable roof along x
        c.fill(cx - 3, 9, cz - 2, cx + 3, 9, cz - 2, Mat.stair(Mat.TILE_ST, "south"));
        c.fill(cx - 3, 9, cz + 2, cx + 3, 9, cz + 2, Mat.stair(Mat.TILE_ST, "north"));
        c.fill(cx - 3, 9, cz - 1, cx + 3, 9, cz + 1, Mat.TILE);
        c.fill(cx - 3, 10, cz - 1, cx + 3, 10, cz + 1, Mat.stair(Mat.TILE_ST, "south"));
        c.fill(cx - 3, 10, cz, cx + 3, 10, cz, Mat.TILE);
        c.fill(cx - 3, 11, cz, cx + 3, 11, cz, Mat.slabB(Mat.TILE_SL));
        // bucket on a chain and two lanterns
        c.fill(cx, 5, cz, cx, 7, cz, Mat.CHAIN_Y);
        c.set(cx, 4, cz, "minecraft:cauldron");
        c.set(cx - 1, 7, cz + 1, Mat.lantern(true));
        c.set(cx + 1, 7, cz - 1, Mat.lantern(true));
        c.fill(cx - 1, 8, cz + 1, cx - 1, 8, cz + 1, Mat.log(Mat.LOG_S, "x"));
        c.fill(cx + 1, 8, cz - 1, cx + 1, 8, cz - 1, Mat.log(Mat.LOG_S, "x"));
        // buckets, stone paving apron
        c.ring(cx, 0, cz, 3.0, Mat.COBBLE);
        c.marker("marbles.well", cx + 0.5, 1.0, cz + 0.5, 0f, "stand=0");
    }

    // ------------------------------------------------------------------ tree

    private static void tree(BuildContext c, int cx, int cz) {
        // planter ring
        c.ring(cx, 1, cz, 3.4, Mat.MOSSY_COBBLE);
        c.disc(cx, 0, cz, 3.0, Mat.COARSE);
        c.disc(cx, 1, cz, 2.6, Mat.COARSE);
        // 2x2 trunk (dark oak), flared base
        c.fill(cx, 1, cz, cx + 1, 9, cz + 1, Mat.log(Mat.LOG, "y"));
        c.set(cx - 1, 1, cz, Mat.log(Mat.LOG, "x"));
        c.set(cx + 2, 1, cz + 1, Mat.log(Mat.LOG, "x"));
        c.set(cx, 1, cz - 1, Mat.log(Mat.LOG, "z"));
        c.set(cx + 1, 1, cz + 2, Mat.log(Mat.LOG, "z"));
        // branches
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] d : dirs) {
            int bx = cx + (d[0] > 0 ? 1 : 0), bz = cz + (d[1] > 0 ? 1 : 0);
            c.line(bx, 8, bz, bx + d[0] * 4, 11, bz + d[1] * 4, Mat.log(Mat.LOG, "y"));
        }
        // canopy: noisy ellipsoid
        int ccx = cx, ccz = cz, ccy = 12;
        double rx = 6.0, ry = 4.0, rz = 6.0;
        String[] leaves = {Mat.leaves("minecraft:cherry_leaves"), Mat.leaves("minecraft:azalea_leaves"),
                Mat.leaves("minecraft:flowering_azalea_leaves")};
        double[] lw = {50, 38, 12};
        c.pattern(ccx - 7, ccy - 5, ccz - 7, ccx + 8, ccy + 5, ccz + 8, (x, y, z) -> {
            double dx = (x - ccx - 0.5) / rx, dy = (y - ccy) / ry, dz = (z - ccz - 0.5) / rz;
            double d = dx * dx + dy * dy + dz * dz;
            double n = U.vnoise(x * 0.8, z * 0.8 + y * 1.3, 77);
            if (d > 1.0 + 0.25 * (n - 0.5)) {
                return null;
            }
            if (d < 0.12) {
                return null; // keep the trunk / branches visible inside
            }
            return U.pick(U.rand(x, y * 7 + z, 78), leaves, lw);
        });
        // lanterns hanging under the canopy on chains
        int[][] ls = {{-4, 0}, {4, 1}, {0, -4}, {1, 4}, {-3, -3}, {4, 4}, {-3, 4}, {4, -3}};
        for (int[] l : ls) {
            int lx = cx + l[0], lz = cz + l[1];
            c.fill(lx, 8, lz, lx, 10, lz, Mat.CHAIN_Y);
            c.set(lx, 7, lz, Mat.lantern(true));
        }
        // lanterns on the planter ring around the trunk
        for (int[] l : new int[][]{{-2, 0}, {3, 1}, {0, -2}, {1, 3}}) {
            c.set(cx + l[0], 2, cz + l[1], Mat.lantern(false));
        }
        c.marker("marbles.tree", cx + 1.0, 1.0, cz + 1.0, 0f, "stand=0");
    }

    // ------------------------------------------------------------------ stage

    private static void stage(BuildContext c) {
        int x0 = STAGE_X0, x1 = STAGE_X1, z0 = STAGE_Z0, z1 = STAGE_Z1;
        c.fill(x0, 1, z0, x1, 1, z1, Mat.PLANKS);
        c.fill(x0, 1, z0, x1, 1, z0, Mat.log(Mat.LOG_S, "x"));
        c.fill(x0, 0, z0, x1, 0, z1, Mat.SB);
        // steps in front (centre)
        c.fill(-2, 1, z0 - 1, 2, 1, z0 - 1, Mat.stair("minecraft:spruce_stairs", "south"));
        c.fill(x0 - 1, 1, z0, x0 - 1, 1, z1, Mat.stair("minecraft:spruce_stairs", "east"));
        c.fill(x1 + 1, 1, z0, x1 + 1, 1, z1, Mat.stair("minecraft:spruce_stairs", "west"));
        // back wall with the title board
        c.fill(x0, 2, z1, x1, 7, z1, Mat.concrete("black"));
        c.fill(x0 + 1, 3, z1, x1 - 1, 6, z1, Mat.concrete("gray"));
        c.fill(x0, 8, z1, x1, 8, z1, Mat.TILE_SL + "[type=bottom]");
        // posts and eaves with lanterns
        for (int x = x0; x <= x1; x += 4) {
            if (x == 0) {
                continue;            // keep the title board unobstructed
            }
            c.fill(x, 2, z1 - 1, x, 6, z1 - 1, Mat.log(Mat.LOG, "y"));
            c.set(x, 7, z1 - 2, Mat.lantern(true));   // hangs from the tile slab at y = 8
        }
        c.fill(x0, 7, z1 - 1, x1, 7, z1 - 1, Mat.log(Mat.LOG_S, "x"));
        c.fill(x0, 8, z1 - 1, x1, 8, z1 - 1, Mat.stair(Mat.TILE_ST, "south"));
        c.fill(x0, 8, z1 - 2, x1, 8, z1 - 2, Mat.slabB(Mat.TILE_SL));
        c.fill(x0, 9, z1, x1, 9, z1, Mat.stair(Mat.TILE_ST, "south"));
        c.text(0.5, 5.2, z1 - 0.03, "MARBLES", "#FFD84A", 6.0f, 180f, false);
        c.text(0.5, 3.8, z1 - 0.03, "FIND A PARTNER - ODD OR EVEN", "white", 1.5f, 180f, false);
        c.marker("marbles.stage", 0.5, 2.0, (z0 + z1) / 2.0 + 0.5, 180f, "stand=0");
        for (int x = x0 + 1; x < x1; x += 4) {
            c.set(x, 2, z0, Mat.lantern(false));
        }
    }

    // ------------------------------------------------------------------ benches, lamps, planters

    private static void furniture(BuildContext c) {
        // lamp posts at the four corners
        int[][] corners = {{-14, -5}, {14, -5}, {-14, 10}, {14, 10}};
        for (int[] p : corners) {
            Props.lampPost(c, p[0], 1, p[1], 5);
            c.set(p[0], 1, p[1] + 1, Mat.SB_WALL);
        }
        // benches facing the medallion
        for (int s = -1; s <= 1; s += 2) {
            Props.benchX(c, s * 7 - 1, 1, -6, 3, "north");   // north row, backs towards the hall
        }
        // benches on the south half facing the medallion, backs towards the stage
        Props.benchX(c, -13, 1, 7, 3, "south");
        Props.benchX(c, 11, 1, 7, 3, "south");
        // seats around the tree base and the well (on the sides facing the hall gate axis)
        for (int i = -1; i <= 1; i++) {
            c.set(TREE_X - 4, 1, TREE_Z + i, Mat.stair("minecraft:spruce_stairs", "west"));
            c.set(WELL_X + 5, 1, WELL_Z + i, Mat.stair("minecraft:spruce_stairs", "east"));
        }
        // lamps beside the stage steps
        Props.stoneLamp(c, -10, 1, 11);
        Props.stoneLamp(c, 10, 1, 11);
        // planters in the corners
        for (int[] p : new int[][]{{-14, -3}, {14, -3}, {-14, 12}, {14, 12}}) {
            Props.planter(c, p[0], 1, p[1], p[0] < 0);
        }
    }

    /** Standing spots for the contestants: checkerboard grid over the free paving of the square. */
    static List<int[]> freeSpots(BuildContext c) {
        List<int[]> out = new ArrayList<>();
        for (int z = Layout.SQ_Z0 + 3; z <= Layout.SQ_Z1 - 1; z++) {
            for (int x = Layout.SQ_X0; x <= Layout.SQ_X1; x++) {
                if (((x + z) & 1) != 0) {
                    continue;
                }
                if (isFree(c, x, z)) {
                    out.add(new int[]{x, z});
                }
            }
        }
        return out;
    }

    private static boolean isFree(BuildContext c, int x, int z) {
        String below = c.get(x, 0, z);
        if (below == null || below.contains("water") || below.equals(Mat.AIR)) {
            return false;
        }
        for (int y = 1; y <= 3; y++) {
            if (c.isSolid(x, y, z)) {
                return false;
            }
        }
        // keep a neighbour ring clear so entities do not stand wedged against walls / posts
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (c.isSolid(x + dx, 1, z + dz) && c.isSolid(x + dx, 2, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }
}
