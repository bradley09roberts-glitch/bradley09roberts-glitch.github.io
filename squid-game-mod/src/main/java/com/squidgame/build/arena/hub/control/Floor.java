package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.hub.control.Geo.*;

/**
 * The black tile floor with concentric pink light arcs round the commander's dais (the functional lighting of the
 * room: every walkable cell is within about three blocks of a glowing line) and a polished border.
 */
final class Floor {
    private Floor() {
    }

    /** Radii of the pink arcs (in blocks from the dais centre at the east wall). */
    static final double[] ARCS = {12, 17, 22, 27, 32, 37, 41};

    static void build(BuildContext c) {
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                double r = Math.hypot(x - 84, z);
                String b = ((x + z) & 1) == 0 ? Pal.TILE_BLACK : Pal.TILE_BLACK;
                for (double a : ARCS) {
                    if (Math.abs(r - a) <= 0.55) {
                        b = Pal.LIGHT_PINK;
                    }
                }
                c.set(x, 11, z, b);
            }
        }
        // polished border along the walls (the glow of the arcs shows against it)
        for (int x = X0; x <= X1; x++) {
            c.set(x, 11, Z0, Pal.PBS);
            c.set(x, 11, Z1, Pal.PBS);
        }
        for (int z = Z0; z <= Z1; z++) {
            c.set(X1, 11, z, Pal.PBS);
        }
    }
}
