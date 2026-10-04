package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * Guard walkways high above the deck: the cross gantry (managers) hanging directly under the centre truss at
 * y = 75, and two side gantries (armed guards) at y = 56 over the pit beside the bridge, hung from the trusses by
 * chains. None of them is connected to the platforms or to the pit.
 */
final class Gantries {
    private Gantries() {
    }

    static void build(BuildContext c) {
        crossGantry(c);
        sideGantry(c, 20);
        sideGantry(c, -25);
    }

    private static void crossGantry(BuildContext c) {
        int y = Geo.GANTRY;
        int zc = Geo.ZC;
        for (int x = Geo.X0; x <= Geo.X1; x++) {
            for (int z = zc - 2; z <= zc + 2; z++) {
                boolean edge = z == zc - 2 || z == zc + 2;
                c.set(x, y, z, edge ? Pal.BLK : (((x + z) & 1) == 0 ? Pal.DST : Pal.DSP));
            }
            // under-slung spine
            c.set(x, y - 1, zc, Pal.BLK);
        }
        for (int x = Geo.X0 + 2; x <= Geo.X1 - 2; x += 4) {
            c.set(x, y, zc, Pal.PEARL);
        }
        for (int z : new int[]{zc - 2, zc + 2}) {
            for (int x = Geo.X0; x <= Geo.X1; x++) {
                c.set(x, y + 1, z, Pal.BARS);
            }
            // posts every 6 that reach the truss chord above (y = 78)
            for (int x = Geo.X0 + 1; x <= Geo.X1; x += 6) {
                c.fill(x, y + 1, z, x, y + 2, z, Pal.BLK);
            }
        }
        // cross ribs under the floor
        for (int x = Geo.X0 + 3; x <= Geo.X1 - 3; x += 6) {
            c.fill(x, y - 1, zc - 2, x, y - 1, zc + 2, Pal.BLK);
        }
    }

    /** A 5 wide gantry (rails at both edges, 3 wide walkway) from z = 3 to 70 at y = 56, hung on 10 chains. */
    private static void sideGantry(BuildContext c, int xa) {
        int y = Geo.SIDE_GANTRY;
        int z0 = 3, z1 = 70;
        for (int x = xa; x <= xa + 4; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == xa || x == xa + 4;
                c.set(x, y, z, edge ? Pal.BLK : (((x + z) & 1) == 0 ? Pal.DST : Pal.DSP));
            }
        }
        for (int z = z0 + 2; z <= z1 - 2; z += 4) {
            c.set(xa + 2, y, z, Pal.PEARL);
        }
        // spine and ribs underneath
        c.fill(xa + 2, y - 1, z0, xa + 2, y - 1, z1, Pal.BLK);
        for (int z = z0 + 1; z <= z1; z += 6) {
            c.fill(xa, y - 1, z, xa + 4, y - 1, z, Pal.BLK);
        }
        // rails
        for (int x : new int[]{xa, xa + 4}) {
            for (int z = z0; z <= z1; z++) {
                c.set(x, y + 1, z, Pal.BARS);
                c.set(x, y + 2, z, Pal.BARS);
            }
            for (int zc : Roof.TRUSS_Z) {
                c.fill(x, y + 1, zc, x, y + 2, zc, Pal.BLK);
                c.fill(x, y + 3, zc, x, Geo.TRUSS_BOT - 1, zc, Pal.chain());
            }
            c.fill(x, y + 1, z0, x, y + 3, z0, Pal.BLK);
            c.fill(x, y + 1, z1, x, y + 3, z1, Pal.BLK);
            c.set(x, y + 3, z0, Pal.PINK_LIGHT);
            c.set(x, y + 3, z1, Pal.PINK_LIGHT);
        }
    }
}
