package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * Exposed timber roof structure under the deck: eight curved stripped-wood ribs (one above every inner
 * pilaster pair) springing from the pilasters, five purlins along the hall, and rows of hanging
 * round honey-glass lamps (shroomlight core) on chains.
 */
public final class Roof {
    private Roof() {
    }

    /** z of the ribs / inner pilasters. */
    public static int ribZ(int p) {
        return Geo.Z_REAR - 10 * p;
    }

    /** Lowest cell row of the lamps (their centre is one above). */
    public static final int LAMP_Y = 12;
    public static final int[] PURLIN_X = {-24, -12, 0, 12, 24};

    private static final String RIB = Pal.log("stripped_dark_oak_wood", 'x');
    private static final String PURLIN = Pal.log("stripped_spruce_wood", 'z');

    /** Top cell of a rib at column x. */
    static int ribTop(int x) {
        double ax = Math.abs(x) / 35.0;
        double drop = 5.0 * Math.pow(ax, 2.4);
        int deck = (int) Math.floor(Geo.roofUnder(x) - 0.001);
        return (int) Math.round(deck - 1 - drop);
    }

    public static void build(BuildContext c) {
        for (int p = 1; p <= 8; p++) {
            rib(c, ribZ(p), p != 1);       // the first rib stands over the balcony stairs: no corbels in their headroom
        }
        for (int x : PURLIN_X) {
            int y = (int) Math.floor(Geo.roofUnder(x) - 0.001) - 1;
            c.fill(x, y, Geo.Z_FRONT, x, y, Geo.Z_REAR, PURLIN);
        }
        // ridge beam, doubled
        c.fill(0, 21, Geo.Z_FRONT, 0, 21, Geo.Z_REAR, PURLIN);
        lamps(c);
    }

    private static void rib(BuildContext c, int z, boolean corbels) {
        for (int x = -35; x <= 35; x++) {
            int top = ribTop(x);
            c.fill(x, top - 1, z, x, top, z, RIB);
        }
        // corbels where the ribs spring from the pilasters
        for (int side = -1; side <= 1 && corbels; side += 2) {
            int x = side * 35;
            int top = ribTop(x);
            c.fill(x, top - 3, z, x, top - 2, z, Pal.DARK_OAK);
            c.set(x - side, top - 2, z, Pal.stairs("dark_oak", side < 0 ? "west" : "east", true));
            c.set(x, top - 4, z, Pal.stairs("dark_oak", side < 0 ? "east" : "west", true));
        }
        // king post and braces at the ridge
        int crown = ribTop(0);
        c.fill(0, crown - 3, z, 0, crown - 2, z, Pal.log("stripped_dark_oak_wood", 'y'));
        c.set(0, crown - 4, z, Pal.DARK_OAK);
    }

    private static void lamps(BuildContext c) {
        for (int k = 1; k <= 7; k++) {            // none over the stage: they would hide the chalkboard
            int z = -7 - 10 * k;             // mid-bay between two ribs
            for (int x : PURLIN_X) {
                lamp(c, x, z);
            }
        }
    }

    /** One pendant: chain from the purlin, round honey-glass globe with a shroomlight core. */
    static void lamp(BuildContext c, int x, int z) {
        int purlinY = (int) Math.floor(Geo.roofUnder(x) - 0.001) - 1;
        int cy = LAMP_Y + 1;
        c.fill(x, cy + 4, z, x, purlinY - 1, z, Pal.chain());
        c.set(x, cy + 2, z, "minecraft:dark_oak_slab[type=bottom]");
        c.set(x, cy + 3, z, "minecraft:dark_oak_slab[type=top]");
        c.sphere(x, cy, z, 1.3, Pal.HONEY, true);
        c.set(x, cy, z, Pal.SHROOMLIGHT);
        c.set(x, cy - 2, z, "minecraft:dark_oak_slab[type=top]");
    }
}
