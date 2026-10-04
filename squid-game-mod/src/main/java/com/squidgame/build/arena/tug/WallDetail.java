package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

/** Poured-concrete skin of the hall: long walls, end walls, pier faces. */
final class WallDetail {
    private WallDetail() {
    }

    static void build(BuildContext c) {
        // long walls (inner face = first wall layer), west half mirrored east
        Sym.pattern(c, Geo.HX0, 1, -Geo.HZ - 1, -1, Geo.CEIL - 1, -Geo.HZ - 1, (x, y, z) -> Surf.wall(x, y));
        Sym.pattern(c, Geo.HX0, 1, Geo.HZ + 1, -1, Geo.CEIL - 1, Geo.HZ + 1, (x, y, z) -> Surf.wall(x, y));
        // end walls above the plateau (u = z, plinth sits on the plateau floor)
        Sym.pattern(c, Geo.HX0 - 1, Geo.DECK + 1, -Geo.HZ, Geo.HX0 - 1, Geo.CEIL - 1, Geo.HZ, (x, y, z) -> Surf.wall(z, y - Geo.DECK));
        // pier faces toward the pit: clean concrete above the ring, dirty pit wall below
        Sym.pattern(c, Geo.PIER_X1, 1, -Geo.HZ, Geo.PIER_X1, Geo.DECK - 1, Geo.HZ, (x, y, z) -> Surf.wall(z, y));
    }
}
