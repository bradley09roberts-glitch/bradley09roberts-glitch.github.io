package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

/**
 * The glowing holographic map table in the middle of the room: a black drum with a pink-lit rim and a sea-lantern top
 * under a glass sheet, with a hologram of glass rings, light-blue gate pillars and an end-rod spire floating above.
 */
final class HoloTable {
    private HoloTable() {
    }

    static final int CX = 56, CZ = 0;
    static final double R = 4.6;

    static void build(BuildContext c) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.hypot(dx, dz);
                int x = CX + dx, z = CZ + dz;
                if (r > R + 0.3) {
                    continue;
                }
                c.set(x, 11, z, Pal.PBS);
                c.set(x, 12, z, Pal.PBS_BRICKS);
                if (r >= R - 1.2) {
                    // rim
                    c.set(x, 13, z, ((dx + dz) & 1) == 0 ? Pal.LIGHT_PINK : Pal.PBS);
                } else {
                    c.set(x, 13, z, Pal.SEA);
                    c.set(x, 14, z, Pal.GLASS_CYAN);
                }
            }
        }
        // hologram: glass rings and gate pillars
        ring(c, 3.0, 16, Pal.GLASS_CYAN);
        ring(c, 2.0, 18, Pal.GLASS_BLUE);
        ring(c, 1.0, 20, Pal.GLASS_CYAN);
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(k * 60 + 15);
            int x = CX + (int) Math.round(3.0 * Math.cos(a));
            int z = CZ + (int) Math.round(3.0 * Math.sin(a));
            c.set(x, 15, z, Pal.GLASS_BLUE);
            c.set(x, 16, z, k % 2 == 0 ? Pal.GLASS_PINK : Pal.GLASS_BLUE);
        }
        for (int y = 15; y <= 22; y++) {
            c.set(CX, y, CZ, y == 19 ? Pal.SEA : Pal.endRod("up"));
        }
        // spotlight ring on the ceiling above the table
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.hypot(dx, dz);
                if (r >= 4.2 && r <= 5.2) {
                    c.set(CX + dx, 33, CZ + dz, Pal.SEA);
                }
            }
        }
    }

    private static void ring(BuildContext c, double r, int y, String glass) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.hypot(dx, dz);
                if (Math.abs(d - r) <= 0.5) {
                    c.set(CX + dx, y, CZ + dz, glass);
                }
            }
        }
    }
}
