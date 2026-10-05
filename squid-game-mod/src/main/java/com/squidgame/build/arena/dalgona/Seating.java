package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * Eight rows of four bench blocks (5 seats each, seats 2 blocks apart): per row, from the front (north) to
 * the back: the far desk cell, the station cell (a {@code squidgame:dalgona_station} at every seat), the free
 * seat cell (the contestant's marker, floor level) and the 0.5 high bench directly behind it. 160 seats.
 *
 * <pre>
 *   z0     far desk (dark oak slab)            |  contestants face north (yaw 180)
 *   z0+1   station per seat / spruce slab      |
 *   z0+2   SEAT marker cell (free, floor)      v
 *   z0+3   bench (dark oak slab) behind it
 * </pre>
 */
public final class Seating {
    private Seating() {
    }

    /** Offset of the seat marker inside its cell towards the bench behind it (cell is z0+2, bench face at +1.0). */
    public static final double SEAT_Z = 0.7;

    public static void build(BuildContext c) {
        int slot = 0;
        for (int row = 0; row < Geo.ROWS; row++) {
            int z0 = Geo.rowZ(row);
            for (int b = 0; b < Geo.BLOCKS.length; b++) {
                int xa = Geo.BLOCKS[b][0];
                int xb = Geo.BLOCKS[b][1];
                furniture(c, xa, xb, z0);
                for (int j = 0; j < Geo.SEATS_PER_BLOCK; j++) {
                    int x = Geo.seatX(b, j);
                    c.set(x, 1, z0 + 1, Pal.STATION + "[facing=north]");
                    c.marker("dalgona.seat", x + 0.5, 1.0, z0 + 2 + SEAT_Z, 180f, "slot=" + slot);
                    c.marker("dalgona.station", x + 0.5, 2.0, z0 + 1 + 0.5, 180f, "slot=" + slot);
                    slot++;
                }
            }
        }
        c.region("dalgona.seating", Geo.BLOCKS[0][0] - 1, 0, Geo.rowZ(0) - 1,
                Geo.BLOCKS[Geo.BLOCKS.length - 1][1] + 1, 4, Geo.rowZ(Geo.ROWS - 1) + 4);
    }

    private static void furniture(BuildContext c, int xa, int xb, int z0) {
        for (int x = xa; x <= xb; x++) {
            boolean end = x == xa || x == xb;
            String face = x == xa ? "west" : "east";
            // far desk (dark edge), station row (spruce) and bench behind the seat cell; stair caps close the ends
            c.set(x, 1, z0, end ? Pal.stairs("dark_oak", face, false) : Pal.slab("dark_oak", false));
            c.set(x, 1, z0 + 1, end ? Pal.stairs("spruce", face, false) : Pal.slab("spruce", false));
            c.set(x, 1, z0 + 3, end ? Pal.stairs("dark_oak", face, false) : Pal.slab("dark_oak", false));
        }
    }
}
