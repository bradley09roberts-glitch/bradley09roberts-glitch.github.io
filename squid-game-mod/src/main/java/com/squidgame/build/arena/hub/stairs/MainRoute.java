package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.arena.hub.stairs.Route.Exit;

/** The one walkable route from the entrance up to the gate terrace (y=60), built with the {@link Route} turtle. */
final class MainRoute {
    private MainRoute() {
    }

    static void build(Ctx k) {
        Look a = Look.main(Pal.MINT, Pal.YELLOW, 100);   // massive first flight
        Look b = Look.main(Pal.SKY, Pal.LILAC, 2);
        Look c = Look.main(Pal.YELLOW, Pal.MINT, 2);
        Look d = Look.main(Pal.LILAC, Pal.PEACH, 2);
        Look e = Look.main(Pal.PEACH, Pal.SKY, 2);
        Look f = Look.main(Pal.MINT, Pal.LILAC, 2);

        // from just inside the entrance across the plaza
        for (int z = -83; z > -89; z--) {
            k.line.add(new int[]{0, 0, z});
        }
        Route r = new Route(k, Dir.N, 0, -89, 0, true);
        // grand stair at the entrance: three lanes, 12 steps
        r.flight(12, 3, a);
        r.landing(9, 21, Exit.LEFT, a.withDepth(3), Kit.PAT_CHECK);          // y=12
        r.flight(12, 1, b);                                                    // west, to y=24
        r.landing(9, 11, Exit.RIGHT, b.withDepth(3), Kit.PAT_FRAME);         // y=24, turn north
        r.flight(12, 1, c);                                                    // north, to y=36
        r.landing(9, 11, Exit.RIGHT, c.withDepth(3), Kit.PAT_CHECK);         // y=36, turn east
        r.flight(12, 1, d);                                                    // east, to y=48
        r.landing(19, 11, Exit.LEFT, d.withDepth(3), Kit.PAT_FRAME);          // y=48, turn north
        r.bridge(14, 7, e.withDepth(3));                                       // north at y=48
        r.flight(12, 1, f);                                                    // north to y=60
        // across the terrace to the middle of the gate gallery (the last waypoint stands there)
        for (int z = -160; z >= -166; z--) {
            k.line.add(new int[]{0, 60, z});
        }
    }
}
