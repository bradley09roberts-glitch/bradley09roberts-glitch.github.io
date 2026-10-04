package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

/** The raw hall: solid mass, interior carve, hall floor ring, the pit and the two end piers. */
final class HallShell {
    private HallShell() {
    }

    static void build(BuildContext c) {
        // 1. solid mass: walls, roof, foundation and the future piers, all poured concrete
        c.fill(Geo.WX0, Geo.FOUND, -Geo.WZ, Geo.WX1, Geo.ROOF_TOP, Geo.WZ, Pal.CONC_D);
        // 2. hall interior above the floor ring
        c.clear(Geo.HX0, 1, -Geo.HZ, Geo.HX1, Geo.CEIL - 1, Geo.HZ);
        // 3. hall floor (the ring around the pit), pit carved out of it afterwards
        Sym.pattern(c, Geo.HX0, 0, -Geo.HZ, -1, 0, Geo.HZ, (x, y, z) -> Surf.ring(x, z));
        // 4. the pit: open volume from the floor layer up to the ring level
        c.clear(Geo.PX0, Geo.PIT + 1, -Geo.PZ, Geo.PX1, 0, Geo.PZ);
        // 5. end piers rise from the ring to the plateau floor (y = 39), full hall width
        Sym.fill(c, Geo.PIER_X0, 1, -Geo.HZ, Geo.PIER_X1, Geo.DECK - 1, Geo.HZ, Pal.CONC_L);
    }
}
