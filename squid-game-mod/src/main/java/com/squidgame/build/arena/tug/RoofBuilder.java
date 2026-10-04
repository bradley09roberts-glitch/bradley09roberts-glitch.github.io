package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

/**
 * Ceiling: coffered concrete roof with rows of fluorescent panel lights, 15 planar steel trusses spanning the hall
 * (pairs of blocks at x = 10k-1 .. 10k), lantern-lit bottom chords and heavy pendant lamps over the decks.
 */
final class RoofBuilder {
    private RoofBuilder() {
    }

    static void build(BuildContext c) {
        ceiling(c);
        for (int k = -7; k <= 7; k++) {
            truss(c, 10 * k - 1);
        }
        for (int k = -5; k <= 5; k++) {
            if (k != 0) {                                // the control booth hangs at the centre
                pendant(c, 10 * k - 1);
            }
        }
        dangling(c);
    }

    /** Loose chains with hooks and weights dangling from the truss bottom chords over the pit (pure atmosphere). */
    private static void dangling(BuildContext c) {
        java.util.Random r = new java.util.Random(404);
        for (int i = 0; i < 46; i++) {
            int k = r.nextInt(15) - 7;
            int x = 10 * k - 1 + r.nextInt(2);
            int az = 7 + r.nextInt(12);
            int z = r.nextBoolean() ? az : -az;
            if (Math.abs(x) <= 9) {
                continue;                                 // keep the centre (booth, gantry) clear
            }
            int len = 4 + r.nextInt(10);
            c.fill(x, 65 - len, z, x, 65, z, Pal.chain());
            c.set(x, 65 - len - 1, z, r.nextBoolean() ? Pal.IRON : "minecraft:lantern[hanging=true]");
        }
    }

    private static void ceiling(BuildContext c) {
        // coffer grid on the underside of the roof (y = 75)
        Sym.pattern(c, Geo.HX0, Geo.CEIL, -Geo.HZ, -1, Geo.CEIL, Geo.HZ, (x, y, z) -> {
            int px = Math.floorMod(x + 1, 10);            // truss pair sits at px 9 / 0 -> x = 10k-1, 10k
            boolean trussLine = px == 9 || px == 0;
            boolean lightRow = px >= 3 && px <= 6;       // 4-wide fluorescent strip between trusses
            int pz = Math.floorMod(z + 35, 12);
            if (lightRow && pz >= 2 && pz <= 9) {
                return Pal.PANEL;
            }
            if (trussLine) {
                return Pal.CONC_D;
            }
            if (pz == 0 || pz == 11 || px == 1 || px == 8) {
                return Pal.CONC_D;
            }
            return Pal.CONC_L;
        });
    }

    /** One planar truss in the yz plane at x, x+1; chords at y 73..74 (top) and 66..67 (bottom), Pratt web. */
    private static void truss(BuildContext c, int x) {
        int x2 = x + 1;
        c.fill(x, 73, -Geo.HZ, x2, 74, Geo.HZ, Pal.STEEL);
        c.fill(x, 66, -Geo.HZ, x2, 67, Geo.HZ, Pal.STEEL);
        for (int i = 0; i <= 10; i++) {
            int z = -35 + 7 * i;
            c.fill(x, 68, z, x2, 72, z, Pal.STEEL);          // verticals
        }
        for (int i = 0; i < 5; i++) {
            int za = -35 + 7 * i, zb = za + 7;
            for (int xx = x; xx <= x2; xx++) {
                c.line(xx, 68, za + 1, xx, 72, zb - 1, Pal.STEEL);
                c.line(xx, 68, -zb + 1 + 0, xx, 72, -za - 1 + 0, Pal.STEEL);
            }
        }
        // lanterns in the bottom chord every 7 blocks (flush) and under the chord nodes
        for (int i = 0; i <= 10; i++) {
            int z = -35 + 7 * i;
            if (i != 0 && i != 10) {
                c.fill(x, 65, z, x2, 65, z, Pal.SEA);
            }
        }
        // end bearings
        c.fill(x, 66, -Geo.HZ, x2, 74, -Geo.HZ, Pal.IRON);
        c.fill(x, 66, Geo.HZ, x2, 74, Geo.HZ, Pal.IRON);
    }

    /** Heavy pendant lamp over the deck lane (z 0..1), hanging from the truss pair x, x+1: chains, cap, disc shade, caged core. */
    private static void pendant(BuildContext c, int x) {
        int x2 = x + 1;
        for (int xx : new int[]{x, x2}) {
            c.fill(xx, 57, 0, xx, 64, 0, Pal.chain());
            c.fill(xx, 57, 1, xx, 65, 1, Pal.chain());
        }
        c.fill(x, 56, 0, x2, 56, 1, Pal.IRON);
        for (int xx = x - 2; xx <= x2 + 2; xx++) {                          // octagonal disc shade, 6 x 6 without corners
            for (int zz = -2; zz <= 3; zz++) {
                boolean corner = (xx == x - 2 || xx == x2 + 2) && (zz == -2 || zz == 3);
                if (!corner) {
                    c.set(xx, 55, zz, Pal.BLACK);
                }
            }
        }
        c.fill(x - 1, 54, -1, x2 + 1, 54, 2, Pal.STEEL);
        c.fill(x, 52, 0, x2, 54, 1, Pal.SHROOM);                            // glowing core, 3 high
        for (int xx = x - 1; xx <= x2 + 1; xx++) {                          // bars cage around the lower core
            for (int zz = -1; zz <= 2; zz++) {
                boolean edge = xx == x - 1 || xx == x2 + 1 || zz == -1 || zz == 2;
                if (edge) {
                    c.set(xx, 53, zz, Pal.BARS);
                    c.set(xx, 52, zz, Pal.BARS);
                }
            }
        }
    }
}
