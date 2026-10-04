package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;

/** The finish lounge behind the exit door (placeholder, filled in below). */
final class Lounge {
    private Lounge() {
    }

    /** True for cells covered by lounge furniture (no spawn spots there). */
    static boolean furniture(int x, int z) {
        return Math.abs(x) >= 17 || z >= Geo.LZ1 - 4;
    }

    static void build(BuildContext c) {
        c.room(Geo.LX0 - 1, Geo.DECK, Geo.LZ0, Geo.LX1 + 1, 54, Geo.LZ1 + 1, Pal.TILE_W, Pal.PINK_PANEL, Pal.BLK);
    }
}
