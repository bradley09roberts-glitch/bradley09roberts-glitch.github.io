package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * Eight rows of four bench blocks (5 seats each, seats 2 blocks apart): per row, from the front (north) to
 * the back: the far desk cell, the station cell (a {@code squidgame:dalgona_station} at every seat), the free
 * seat cell (the contestant's marker, floor level) and the 0.5 high bench directly behind it. 160 seats.
 *
 * <pre>
 *   z0     desk (spruce slab)                  |  contestants face north (yaw 180)
 *   z0+1   station per seat / desk slab        |
 *   z0+2   SEAT marker cell (free, floor)      v
 *   z0+3   bench (dark oak slab) behind it
 * </pre>
 */
public final class Seating {
    private Seating() {
    }

    public static int seatCount() {
        return Geo.ROWS * Geo.BLOCKS.length * Geo.SEATS_PER_BLOCK;
    }

    public static void build(BuildContext c) {
        int slot = 0;
        for (int row = 0; row < Geo.ROWS; row++) {
            int z0 = Geo.rowZ(row);
            for (int b = 0; b < Geo.BLOCKS.length; b++) {
                int xa = Geo.BLOCKS[b][0];
                int xb = Geo.BLOCKS[b][1];
                furniture(c, xa, xb, z0, row, b);
                for (int j = 0; j < Geo.SEATS_PER_BLOCK; j++) {
                    int x = Geo.seatX(b, j);
                    c.set(x, 1, z0 + 1, Pal.STATION + "[facing=south]");
                    c.marker("dalgona.seat", x + 0.5, 1.0, z0 + 2 + 0.5, 180f, "slot=" + slot);
                    c.marker("dalgona.station", x + 0.5, 2.0, z0 + 1 + 0.5, 180f, "slot=" + slot);
                    slot++;
                }
            }
        }
        c.region("dalgona.seating", Geo.BLOCKS[0][0] - 1, 0, Geo.rowZ(0) - 1,
                Geo.BLOCKS[Geo.BLOCKS.length - 1][1] + 1, 4, Geo.rowZ(Geo.ROWS - 1) + 4);
    }

    private static void furniture(BuildContext c, int xa, int xb, int z0, int row, int block) {
        for (int x = xa; x <= xb; x++) {
            boolean end = x == xa || x == xb;
            String desk = end ? Pal.slab("dark_oak", false) : Pal.slab("spruce", false);
            c.set(x, 1, z0, desk);
            c.set(x, 1, z0 + 1, desk);
            c.set(x, 1, z0 + 3, Pal.slab("dark_oak", false));
        }
        // a sheet of paper or a pot here and there on the far desk
        for (int j = 0; j < Geo.SEATS_PER_BLOCK; j++) {
            int x = xa + 1 + 2 * j;
            double r = Noise.hash(x, z0, 41);
            if (r < 0.3) {
                c.set(x, 2, z0, "minecraft:white_carpet");
            }
        }
    }
}
