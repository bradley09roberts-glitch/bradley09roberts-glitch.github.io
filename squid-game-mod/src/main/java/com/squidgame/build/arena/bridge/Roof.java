package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * The roof structure: five giant black steel trusses spanning the hall (on the rib lines), longitudinal purlins that
 * carry the deck light rails, and rows of hanging spotlight rigs (cages of iron bars around sea lanterns) on chains.
 */
final class Roof {
    private Roof() {
    }

    /** z of the truss centres (3 thick each), on the rib lines of the side walls. */
    static final int[] TRUSS_Z = {8, 22, 36, 50, 64};

    /** Rig columns: {x0, x1, top y}; mirror symmetric about the bridge axis. */
    private static final int[][] RIG_X = {
            {-2, 1, 64},
            {12, 14, 68}, {-15, -13, 68},
            {26, 28, 71}, {-29, -27, 71},
            {39, 41, 74}, {-42, -40, 74}
    };

    static void build(BuildContext c) {
        for (int zc : TRUSS_Z) {
            truss(c, zc);
        }
        purlins(c);
        for (int zc : TRUSS_Z) {
            if (zc == Geo.ZC) {
                continue; // the cross gantry hangs under this truss
            }
            for (int[] r : RIG_X) {
                rig(c, r[0], r[1], zc, r[2]);
            }
        }
    }

    // ------------------------------------------------------------------ trusses

    private static void truss(BuildContext c, int zc) {
        int z0 = zc - 1, z1 = zc + 1;
        // chords (the centre truss carries the 5 wide cross gantry, so its bottom chord is 5 wide)
        int bz0 = zc == Geo.ZC ? zc - 2 : z0, bz1 = zc == Geo.ZC ? zc + 2 : z1;
        c.fill(Geo.X0, Geo.TRUSS_BOT, bz0, Geo.X1, Geo.TRUSS_BOT + 1, bz1, Pal.BLK);
        c.fill(Geo.X0, Geo.TRUSS_TOP - 1, z0, Geo.X1, Geo.TRUSS_TOP, z1, Pal.BLK);
        // chord edge highlights
        c.fill(Geo.X0, Geo.TRUSS_BOT, bz0, Geo.X1, Geo.TRUSS_BOT, bz0, Pal.PBS);
        c.fill(Geo.X0, Geo.TRUSS_BOT, bz1, Geo.X1, Geo.TRUSS_BOT, bz1, Pal.PBS);
        // end posts at the walls
        c.fill(Geo.X0, Geo.TRUSS_BOT, z0, Geo.X0 + 2, Geo.TRUSS_TOP, z1, Pal.BLK);
        c.fill(Geo.X1 - 2, Geo.TRUSS_BOT, z0, Geo.X1, Geo.TRUSS_TOP, z1, Pal.BLK);
        // Warren web: bottom nodes at blocks {6k-1, 6k}, top nodes at {6k+2, 6k+3}; diagonals 2 thick
        int yb = Geo.TRUSS_BOT + 2, yt = Geo.TRUSS_TOP - 2;
        for (int k = -8; k <= 7; k++) {
            for (int z = z0; z <= z1; z++) {
                for (int d = 0; d <= 1; d++) {
                    diag(c, 6 * k - 1 + d, yb, 6 * k + 2 + d, yt, z);
                    diag(c, 6 * k + 2 + d, yt, 6 * k + 5 + d, yb, z);
                }
            }
        }
        // node plates (gusset blocks) on the chords at the bottom nodes
        for (int k = -7; k <= 7; k++) {
            c.fill(6 * k - 1, Geo.TRUSS_BOT - 1, z0, 6 * k, Geo.TRUSS_BOT - 1, z1, Pal.DSP);
        }
    }

    private static void diag(BuildContext c, int xa, int ya, int xb, int yb, int z) {
        // only inside the hall width
        if (Math.min(xa, xb) < Geo.X0 || Math.max(xa, xb) > Geo.X1) {
            return;
        }
        c.line(xa, ya, z, xb, yb, z, Pal.GRY);
    }

    // ------------------------------------------------------------------ purlins

    private static void purlins(BuildContext c) {
        // under-chord beams along z that carry the deck light rail hangers (x -6..-5 and 4..5)
        for (int[] xs : new int[][]{{-6, -5}, {4, 5}}) {
            c.fill(xs[0], Geo.TRUSS_BOT, 7, xs[1], Geo.TRUSS_BOT + 1, 65, Pal.BLK);
            c.fill(xs[0], Geo.TRUSS_BOT - 1, 7, xs[0], Geo.TRUSS_BOT - 1, 65, Pal.PBS);
        }
    }

    // ------------------------------------------------------------------ spotlight rigs

    /** A caged spotlight hanging on four chains: top cap, two cage layers around sea lanterns, glowing underside. */
    private static void rig(BuildContext c, int x0, int x1, int zc, int topY) {
        int z0 = zc - 1, z1 = zc + 1;
        for (int x : new int[]{x0, x1}) {
            for (int z : new int[]{z0, z1}) {
                c.fill(x, topY + 1, z, x, Geo.TRUSS_BOT - 1, z, Pal.chain());
            }
        }
        c.fill(x0, topY, z0, x1, topY, z1, Pal.BLK);
        for (int y = topY - 1; y >= topY - 2; y--) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    c.set(x, y, z, edge ? Pal.BARS : Pal.SEA);
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                c.set(x, topY - 3, z, edge ? Pal.BLK : Pal.WHITE_LIGHT);
            }
        }
    }
}
