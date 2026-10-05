package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The prize: a giant transparent piggy bank (about 15 wide x 11 tall x 9 deep) hanging by chains from the roof
 * trusses over the plaza. Pink stained-glass shell one block thick, rounded-box body, snout facing east towards the
 * control room, two ears, four legs with dark hooves, curly tail, coin slot with a gold coin floating above it.
 *
 * <p>The hollow interior is left as air; the server fills it with cash blocks through the {@code prize.fill} regions
 * (one stacked box per layer, bottom to top, each lying completely inside the hollow interior). Markers:
 * {@code prize.pig} (centre of the body) and {@code prize.counter} (below / in front of the pig, facing the entrance).
 *
 * <p>Pig frame: origin x/z = plaza centre, y = 0 is the soles of the hooves (hub y = {@link Layout#PIG_BOTTOM}).
 */
final class Pig {
    private Pig() {
    }

    // body: rounded box (super-ellipsoid), cell centres are (i + 0.5)
    private static final double CX = -0.5, CY = 6.0, CZ = 0.5;
    private static final double RX = 6.0, RY = 5.0, RZ = 4.5, EXP = 2.7;

    private static final int X_MIN = -12, X_MAX = 12, Y_MAX = 14, Z_MIN = -8, Z_MAX = 8;
    private static final int NX = X_MAX - X_MIN + 1, NY = Y_MAX + 1, NZ = Z_MAX - Z_MIN + 1;

    private boolean[][][] body;
    private boolean[][][] inner;

    static void build(BuildContext c) {
        new Pig().run(c);
    }

    private void run(BuildContext c) {
        computeSets();
        c.at(Layout.PIG_X, Layout.PIG_BOTTOM, Layout.PIG_Z, 0, () -> pig(c));
        rig(c);
        board(c);
        c.marker("prize.pig", Layout.PIG_X + CX, Layout.PIG_BOTTOM + CY, Layout.PIG_Z + CZ, 0f);
        c.marker("prize.counter", Layout.PIG_X + 0.5, Layout.PIG_BOTTOM - 2.6, Layout.PIG_Z + 5.3, 0f);
    }

    // ------------------------------------------------------------------------------------------ geometry

    private static boolean inBody(int x, int y, int z) {
        double dx = Math.abs((x + 0.5 - CX) / RX), dy = Math.abs((y + 0.5 - CY) / RY), dz = Math.abs((z + 0.5 - CZ) / RZ);
        return Math.pow(dx, EXP) + Math.pow(dy, EXP) + Math.pow(dz, EXP) <= 1.0;
    }

    private boolean b(int x, int y, int z) {
        int i = x - X_MIN, j = y, k = z - Z_MIN;
        return i >= 0 && i < NX && j >= 0 && j < NY && k >= 0 && k < NZ && body[i][j][k];
    }

    private void computeSets() {
        body = new boolean[NX][NY][NZ];
        inner = new boolean[NX][NY][NZ];
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int y = 0; y <= Y_MAX; y++) {
                for (int z = Z_MIN; z <= Z_MAX; z++) {
                    body[x - X_MIN][y][z - Z_MIN] = inBody(x, y, z);
                }
            }
        }
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int y = 0; y <= Y_MAX; y++) {
                for (int z = Z_MIN; z <= Z_MAX; z++) {
                    inner[x - X_MIN][y][z - Z_MIN] = b(x, y, z) && b(x + 1, y, z) && b(x - 1, y, z) && b(x, y + 1, z)
                            && b(x, y - 1, z) && b(x, y, z + 1) && b(x, y, z - 1);
                }
            }
        }
    }

    private boolean in(int x, int y, int z) {
        int i = x - X_MIN, j = y, k = z - Z_MIN;
        return i >= 0 && i < NX && j >= 0 && j < NY && k >= 0 && k < NZ && inner[i][j][k];
    }

    /** Highest body cell at (x, z), or -1. */
    private int topY(int x, int z) {
        for (int y = Y_MAX; y >= 0; y--) {
            if (b(x, y, z)) {
                return y;
            }
        }
        return -1;
    }

    private void pig(BuildContext c) {
        // shell + hollow interior
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int y = 0; y <= Y_MAX; y++) {
                for (int z = Z_MIN; z <= Z_MAX; z++) {
                    if (in(x, y, z)) {
                        c.set(x, y, z, Pal.AIR);
                    } else if (b(x, y, z)) {
                        c.set(x, y, z, Pal.PIG_GLASS);
                    }
                }
            }
        }
        seam(c);
        studs(c);
        legs(c);
        snout(c);
        ears(c);
        eyes(c);
        tail(c);
        coinSlot(c);
        fillRegions(c);
    }

    /** Opaque pink seam ring round the belly, like the join of a ceramic piggy bank. */
    private void seam(BuildContext c) {
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int z = Z_MIN; z <= Z_MAX; z++) {
                int y = 6;
                if (b(x, y, z) && !in(x, y, z)) {
                    // every third cell of the ring is a glowing froglight: lights the shell, the cash inside and the hall
                    boolean lamp = Math.floorMod(x + z, 4) == 0;
                    c.set(x, y, z, lamp ? Pal.FROG : Pal.PIG_SOLID);
                }
            }
        }
    }

    /** Glowing froglight studs in the belly and the back so the cash inside is lit from every side. */
    private void studs(BuildContext c) {
        int[][] belly = {{-3, 0}, {3, 0}, {0, -2}, {0, 2}, {-1, 0}, {2, 0}};
        for (int[] p : belly) {
            for (int y = 0; y <= Y_MAX; y++) {
                if (b(p[0], y, p[1])) {
                    c.set(p[0], y, p[1], Pal.FROG);
                    break;
                }
            }
        }
        int[][] back = {{-3, -2}, {-3, 2}, {1, -2}, {1, 2}, {4, -2}, {4, 2}};
        for (int[] p : back) {
            int y = topY(p[0], p[1]);
            if (y >= 0) {
                c.set(p[0], y, p[1], Pal.FROG);
            }
        }
    }

    private void legs(BuildContext c) {
        int[][] feet = {{3, -3}, {3, 2}, {-5, -3}, {-5, 2}};
        for (int[] f : feet) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    int x = f[0] + dx, z = f[1] + dz;
                    // from the soles up to the belly
                    for (int y = 0; y <= 6; y++) {
                        if (b(x, y, z)) {
                            break;
                        }
                        c.set(x, y, z, y == 0 ? Pal.PIG_DARK : Pal.PIG_SOLID);
                    }
                }
            }
        }
    }

    private void snout(BuildContext c) {
        // short cylinder on the front of the head, radius about 2.2, centre (y 6, z 0)
        for (int x = 6; x <= 7; x++) {
            for (int y = 3; y <= 9; y++) {
                for (int z = -3; z <= 3; z++) {
                    double dy = y + 0.5 - 6.5, dz = z + 0.5 - 0.5;
                    if (dy * dy + dz * dz <= 5.4) {
                        c.set(x, y, z, x == 7 ? Pal.PIG_SOLID : Pal.PIG_GLASS);
                    }
                }
            }
        }
        // nostrils
        c.set(7, 6, -1, Pal.BLACK);
        c.set(7, 6, 1, Pal.BLACK);
        c.set(7, 7, -1, Pal.BLACK);
        c.set(7, 7, 1, Pal.BLACK);
    }

    private void ears(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            int z = side < 0 ? -3 : 3;
            int base = Math.max(topY(2, z), topY(3, z)) + 1;
            // a small ear folded forward
            c.fill(2, base, z, 3, base, z, Pal.PIG_SOLID);
            c.fill(3, base + 1, z, 4, base + 1, z, Pal.PIG_SOLID);
            c.set(4, base + 2, z, Pal.PIG_SOLID);
            c.set(2, base + 1, z, Pal.PIG_GLASS);
            c.set(3, base + 2, z, Pal.PIG_GLASS);
            // darker inner ear
            int zi = z + (side < 0 ? 1 : -1);
            c.set(3, base, zi, Pal.PIG_DARK);
            c.set(4, base + 1, zi, Pal.PIG_DARK);
        }
    }

    private void eyes(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            int x = 4, y = 7;
            int z = -99;
            // outermost shell cell at this (x, y) on the given side
            for (int zz = side < 0 ? Z_MIN : Z_MAX; side < 0 ? zz <= Z_MAX : zz >= Z_MIN; zz += side < 0 ? 1 : -1) {
                if (b(x, y, zz)) {
                    z = zz;
                    break;
                }
            }
            if (z == -99) {
                continue;
            }
            c.set(x, y, z, Pal.BLACK);
            c.set(x - 1, y, z, "minecraft:white_concrete");
            c.set(x, y + 1, z, "minecraft:white_concrete");
        }
    }

    private void tail(BuildContext c) {
        int[][] cells = {{-7, 6, 0}, {-8, 6, 0}, {-8, 7, 0}, {-8, 8, 0}, {-7, 8, 0}, {-7, 9, 0}, {-6, 9, 0}};
        for (int[] p : cells) {
            if (!b(p[0], p[1], p[2])) {
                c.set(p[0], p[1], p[2], Pal.PIG_SOLID);
            }
        }
    }

    private void coinSlot(BuildContext c) {
        for (int x = -3; x <= 1; x++) {
            int y = topY(x, 0);
            if (y >= 0) {
                c.set(x, y, 0, Pal.BLACK);
            }
        }
        // gold coin hovering over the slot (a disc in the x-y plane)
        double ccx = -0.5, ccy = topY(-1, 0) + 5.5;
        for (int x = -4; x <= 3; x++) {
            for (int y = (int) Math.floor(ccy - 3); y <= (int) Math.ceil(ccy + 3); y++) {
                double d = Math.hypot(x + 0.5 - ccx, y + 0.5 - ccy);
                if (d <= 2.4) {
                    c.set(x, y, 0, d > 1.6 ? Pal.GOLD : "minecraft:yellow_concrete");
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ prize regions

    /** One box per layer of the hollow interior: the largest axis-aligned rectangle lying completely inside it. */
    private void fillRegions(BuildContext c) {
        List<int[]> boxes = new ArrayList<>();
        for (int y = 0; y <= Y_MAX; y++) {
            int[] best = null;
            int bestArea = 0;
            for (int x0 = X_MIN; x0 <= X_MAX; x0++) {
                for (int z0 = Z_MIN; z0 <= Z_MAX; z0++) {
                    if (!in(x0, y, z0)) {
                        continue;
                    }
                    for (int x1 = x0; x1 <= X_MAX && in(x1, y, z0); x1++) {
                        for (int z1 = z0; z1 <= Z_MAX; z1++) {
                            boolean ok = true;
                            for (int x = x0; x <= x1 && ok; x++) {
                                ok = in(x, y, z1);
                            }
                            if (!ok) {
                                break;
                            }
                            int area = (x1 - x0 + 1) * (z1 - z0 + 1);
                            if (area > bestArea) {
                                bestArea = area;
                                best = new int[]{x0, y, z0, x1, y, z1};
                            }
                        }
                    }
                }
            }
            if (best != null) {
                boxes.add(best);
            }
        }
        for (int[] bx : boxes) {
            c.region("prize.fill", bx[0], bx[1], bx[2], bx[3], bx[4], bx[5]);
        }
    }

    // ------------------------------------------------------------------------------------------ prize board

    /** A black board hanging under the pig's chin from two chains; the server's prize text floats in front of it. */
    private void board(BuildContext c) {
        int y0 = Layout.PIG_BOTTOM - 3, y1 = Layout.PIG_BOTTOM - 1;
        c.fill(-5, y0, 4, 5, y1, 4, Pal.BLACK);
        c.fill(-5, y0, 4, 5, y0, 4, Pal.PINK);
        c.fill(-5, y1, 4, 5, y1, 4, Pal.PINK);
        c.fill(-5, y0, 4, -5, y1, 4, Pal.PINK);
        c.fill(5, y0, 4, 5, y1, 4, Pal.PINK);
        for (int x : new int[]{-4, 4}) {
            c.fill(x, y1 + 1, 4, x, Layout.TRUSS_BOTTOM - 1, 4, Pal.CHAIN);
        }
        // lamps on the top edge so the numbers are well lit
        for (int x : new int[]{-3, 0, 3}) {
            c.set(x, y1 + 1, 4, Pal.SEA);
        }
    }

    // ------------------------------------------------------------------------------------------ rig

    /** Hanger beams under the truss chords and four chains down to the pig's back (hub frame; pig x/z = hub x/z). */
    private void rig(BuildContext c) {
        int bottom = Layout.TRUSS_BOTTOM;
        int[][] attach = {{-4, -3}, {-4, 3}, {4, -3}, {4, 3}};
        for (int x : new int[]{-4, 4}) {
            c.fill(x, bottom, -4, x, bottom, 4, Pal.STEEL);
            c.fill(x, bottom + 1, -4, x, bottom + 1, 4, Pal.STEEL_SLAB_B);
        }
        for (int[] a : attach) {
            int x = a[0], z = a[1];
            // walk towards the centre until the column crosses the body
            while (topY(x, z) < 0 && x != 0) {
                x -= Integer.signum(x);
            }
            int y0 = Layout.PIG_BOTTOM + topY(x, z) + 1;
            c.set(x, y0, z, Pal.SEA);
            c.fill(x, y0 + 1, z, x, bottom - 1, z, Pal.CHAIN);
            if (x != a[0]) {
                // short tie from the beam column to the chain column
                c.fill(Math.min(x, a[0]), bottom - 1, z, Math.max(x, a[0]), bottom - 1, z, Pal.STEEL_BRICKS);
            }
        }
    }
}
