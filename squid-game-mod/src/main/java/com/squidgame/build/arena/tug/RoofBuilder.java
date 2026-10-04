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

    /** Heavy pendant lamp over the deck lane at z = -1..2, hanging from the truss pair at x, x+1. */
    private static void pendant(BuildContext c, int x) {
        int x2 = x + 1;
        for (int z : new int[]{0, 1}) {
            c.fill(x, 56, z, x, 65, z, Pal.chain());
            c.fill(x2, 56, z, x2, 65, z, Pal.chain());
        }
        c.fill(x - 1, 55, -1, x2 + 1, 55, 2, Pal.STEEL);
        c.fill(x - 1, 54, -1, x2 + 1, 54, 2, Pal.BLACK);
        c.fill(x, 54, 0, x2, 54, 1, Pal.SHROOM);
        c.fill(x, 53, 0, x2, 53, 1, Pal.SHROOM);
        // bowl skirt (iron bars) around the core
        for (int xx = x - 1; xx <= x2 + 1; xx++) {
            for (int zz = -1; zz <= 2; zz++) {
                boolean edge = xx == x - 1 || xx == x2 + 1 || zz == -1 || zz == 2;
                if (edge) {
                    c.set(xx, 53, zz, Pal.BARS);
                }
            }
        }
    }
}
