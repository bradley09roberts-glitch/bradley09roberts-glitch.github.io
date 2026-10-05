package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/**
 * The glass deck: 18 rows x 2 lanes of 2x2 {@code squidgame:bridge_glass} panels at y = 40, row r at
 * z = 10 + 3r .. 11 + 3r, lane 0 at x -3..-2 and lane 1 at x 1..2. Nothing is placed at deck level between panels.
 *
 * <p>Two light rails hang beside the deck at x = -5 and x = 4 (outside the deck box x -4..3), at y = 42: lit segments
 * exactly as long as each panel row, separated by dark connectors over the row gaps, hung from the roof beams by
 * chains. The lowest block of a rail is at y = 42 (top face at 43, unreachable by a jump from the deck), and every
 * deck cell gets block light >= 11 from them.
 */
final class Deck {
    private Deck() {
    }

    static final int RAIL_W = -5;
    static final int RAIL_E = 4;
    static final int RAIL_Y = 42;

    static int rowZ(int row) {
        return Geo.FIRST_Z + Geo.PITCH * row;
    }

    static int laneX(int lane) {
        return lane == 0 ? Geo.LANE0_X : Geo.LANE1_X;
    }

    static void build(BuildContext c) {
        for (int r = 0; r < Geo.ROWS; r++) {
            for (int lane = 0; lane < 2; lane++) {
                int x0 = laneX(lane);
                int z0 = rowZ(r);
                c.fill(x0, Geo.DECK, z0, x0 + 1, Geo.DECK, z0 + 1, Pal.GLASS);
            }
        }
        lightRail(c, RAIL_W);
        lightRail(c, RAIL_E);
        // row numbers (1..18) on the lit rail segments, facing the deck
        for (int r = 0; r < Geo.ROWS; r++) {
            String n = Integer.toString(r + 1);
            double z = rowZ(r) + 1.0;
            c.text(RAIL_W + 1.03, RAIL_Y + 0.5, z, n, "#1B1B24", 2.0f, -90f, false);
            c.text(RAIL_E - 0.03, RAIL_Y + 0.5, z, n, "#1B1B24", 2.0f, 90f, false);
        }
    }

    private static void lightRail(BuildContext c, int x) {
        int zFirst = Geo.FIRST_Z - 1; // 9, attached to the gate wall
        int zLast = Geo.LAST_Z + 1;   // 63
        for (int z = zFirst; z <= zLast; z++) {
            boolean connector = Math.floorMod(z - zFirst, Geo.PITCH) == 0;
            c.set(x, RAIL_Y, z, connector ? Pal.BLK : Pal.WHITE_LIGHT);
            if (!connector) {
                c.set(x, RAIL_Y + 1, z, Pal.chain('z'));
            }
        }
        // hangers over every second connector (and the free end), up to the roof beams
        for (int z = zFirst + 3; z <= zLast; z += 6) {
            c.fill(x, RAIL_Y + 1, z, x, Geo.TRUSS_BOT - 1, z, Pal.chain());
        }
        c.fill(x, RAIL_Y + 1, zLast, x, Geo.TRUSS_BOT - 1, zLast, Pal.chain());
        // cap block at the free end
        c.set(x, RAIL_Y, zLast, Pal.PBS);
    }
}
