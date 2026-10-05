package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

/**
 * Outer skin of the control room (seen from outside the hub and from the stairway hall's windows): glowing pink ring
 * emblem on the east face, light bands and slit windows on the long faces, a pink-lit roof edge with an antenna mast,
 * and the underside's pylons (built by {@link Shell}).
 */
final class Exterior {
    private Exterior() {
    }

    static void build(BuildContext c) {
        // east face (x = 86): ring emblem with an inner ring and the three symbols
        for (int z = -19; z <= 19; z++) {
            for (int y = 13; y <= 34; y++) {
                double r = Math.hypot(z, y - 24);
                if (Math.abs(r - 9.0) <= 0.6 || Math.abs(r - 5.0) <= 0.5) {
                    c.set(86, y, z, Pal.LIGHT_PINK);
                } else if (r > 9.6) {
                    c.set(86, y, z, ((z + y) & 3) == 0 ? Pal.PBS : Pal.BLACK);
                }
            }
        }
        c.set(86, 27, 0, Pal.SYM_CIRCLE);
        c.set(86, 24, 0, Pal.SYM_TRIANGLE);
        c.set(86, 21, 0, Pal.SYM_SQUARE);
        // long faces (z = -20 north, z = 20 south): light bands, slit windows, pilaster ribs
        for (int x = 43; x <= 86; x++) {
            for (int side = -1; side <= 1; side += 2) {
                int z = side * 20;
                boolean rib = Math.floorMod(x, 6) == 1;
                for (int y = 13; y <= 34; y++) {
                    String b = rib ? Pal.PBS : Pal.BLACK;
                    if (y == 18 || y == 30) {
                        b = (x & 1) == 0 ? Pal.LIGHT_PINK : Pal.PBS;
                    } else if (!rib && y >= 21 && y <= 27 && Math.floorMod(x, 6) >= 3) {
                        b = y == 24 ? Pal.SEA : Pal.PBS_BRICKS;
                    }
                    c.set(x, y, z, b);
                }
            }
        }
        // roof: glowing edge and an antenna mast with a beacon
        for (int x = 43; x <= 86; x++) {
            c.set(x, 35, -20, (x & 1) == 0 ? Pal.LIGHT_PINK : Pal.PBS);
            c.set(x, 35, 20, (x & 1) == 0 ? Pal.LIGHT_PINK : Pal.PBS);
        }
        for (int y = 36; y <= 46; y++) {
            c.set(64, y, 0, "minecraft:iron_bars");
        }
        c.set(64, 47, 0, Pal.SEA);
        c.set(64, 48, 0, Pal.RED);
        for (int y = 36; y <= 40; y++) {
            c.set(70, y, 0, Pal.PBS);
        }
        c.set(70, 41, 0, Pal.LIGHT_PINK);
    }
}
