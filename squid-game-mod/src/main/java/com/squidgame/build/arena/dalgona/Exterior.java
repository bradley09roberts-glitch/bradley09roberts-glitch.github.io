package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * Outside of the building (only ever seen by flying spectators and in whole-structure previews): stone plinth,
 * buttresses over the pilasters, weathered plaster and a dark roof edge. Nothing here is walkable.
 */
public final class Exterior {
    private Exterior() {
    }

    public static void build(BuildContext c) {
        // plinth bands, one block proud of the walls
        c.fill(-40, -3, -96, -40, 1, 3, Pal.STONE_BRICKS);
        c.fill(40, -3, -96, 40, 1, 3, Pal.STONE_BRICKS);
        c.fill(-40, -3, -96, 40, 1, -96, Pal.STONE_BRICKS);
        c.fill(-40, -3, 3, -23, 1, 3, Pal.STONE_BRICKS);
        c.fill(23, -3, 3, 40, 1, 3, Pal.STONE_BRICKS);
        c.fill(-40, 2, -96, 40, 2, -96, Pal.slab("stone_brick", false));
        c.fill(-40, 2, -96, -40, 2, 3, Pal.slab("stone_brick", false));
        c.fill(40, 2, -96, 40, 2, 3, Pal.slab("stone_brick", false));
        // buttresses above every pilaster of the long walls, with a sloped cap
        for (int p = 0; p <= 9; p++) {
            int z = Geo.Z_REAR - 10 * p;
            for (int side = -1; side <= 1; side += 2) {
                int x = side * 40;
                c.fill(x, 2, z, x, 13, z, Pal.STONE_BRICKS);
                c.set(x, 14, z, Pal.stairs("stone_brick", side < 0 ? "east" : "west", false));
            }
        }
        // weathered plaster on the outer faces
        for (int y = 3; y <= Geo.EAVES; y++) {
            for (int z = -95; z <= 2; z++) {
                for (int side = -1; side <= 1; side += 2) {
                    weather(c, side * 39, y, z);
                }
            }
            for (int x = -39; x <= 39; x++) {
                if (y > Shell.gableTop(x)) {
                    continue;
                }
                weather(c, x, y, -95);
                if (Math.abs(x) > 22) {
                    weather(c, x, y, 2);
                }
            }
        }
    }

    /** Replaces plain plaster only, so windows, doors and trims written earlier stay untouched. */
    private static void weather(BuildContext c, int x, int y, int z) {
        if (Pal.CREAM.equals(c.get(x, y, z))) {
            c.set(x, y, z, outer(x, y, z));
        }
    }

    private static String outer(int x, int y, int z) {
        double n = Noise.value3(x, y, z, 5.0, 91);
        if (n > 0.7) {
            return Pal.CREAM_WORN;
        }
        if (n < 0.18) {
            return Pal.CREAM_SPECK;
        }
        return Pal.CREAM;
    }
}
