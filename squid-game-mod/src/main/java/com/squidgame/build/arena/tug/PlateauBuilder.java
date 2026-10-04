package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

/**
 * The two end plateaus (tops of the piers, floor y = 40): the team lobbies. Each has a hazard-striped edge with rails,
 * a painted team zone, flush floor lights and the steel portal that opens onto its deck.
 */
final class PlateauBuilder {
    private PlateauBuilder() {
    }

    static void build(BuildContext c) {
        plateau(c, true, Pal.RED);
        plateau(c, false, Pal.BLUE);
    }

    /** Maps a west-frame block x to the plateau's own side. */
    static int X(boolean west, int x) {
        return west ? x : Sym.mx(x);
    }

    static boolean isLight(int x, int z) {
        return (x == -74 || x == -69 || x == -64) && Math.floorMod(z, 5) == 0 && Math.abs(z) <= 35;
    }

    private static String floor(int x, int z, String team) {
        int az = Math.abs(z);
        if (x >= -63 && x <= -62 && az >= 6) {
            return Pal.hazard(x + z);                       // edge hazard band
        }
        if (x == -75 && az <= 4) {
            return Pal.hazard(z + 100);                      // door apron
        }
        if (isLight(x, z)) {
            return Pal.PANEL;
        }
        boolean zoneEdge = (x == -73 || x == -65) && az <= 14 || (az == 14 && x >= -73 && x <= -65);
        if (zoneEdge) {
            return team;
        }
        if (Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0) {
            return Pal.CONC_D;
        }
        double h = Pal.hash(x, z, 23);
        return h < 0.10 ? Pal.STONE : h < 0.16 ? Pal.ANDESITE : Pal.CONC_L;
    }

    private static void plateau(BuildContext c, boolean w, String team) {
        for (int x = Geo.PIER_X0; x <= Geo.PIER_X1; x++) {
            for (int z = -Geo.HZ; z <= Geo.HZ; z++) {
                c.set(X(w, x), Geo.DECK, z, floor(x, z, team));
            }
        }
        // rails along the pier edge (not in front of the deck portal, nor at the gallery openings)
        for (int z = 5; z <= 30; z++) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                c.set(X(w, Geo.PIER_X1), 41, z * sgn, Pal.BARS);
                c.set(X(w, Geo.PIER_X1), 42, z * sgn, Pal.BARS);
            }
        }
        // portal onto the deck: two steel posts and a team-coloured lintel
        int x0 = X(w, -62), x1 = X(w, -61);
        for (int zA : new int[]{3, -4}) {
            c.fill(x0, 41, zA, x1, 47, zA + 1, Pal.STEEL);
            for (int y = 41; y <= 43; y++) {
                c.fill(x0, y, zA, x1, y, zA + 1, Pal.hazard(y + zA));
            }
            c.fill(x0, 46, zA, x1, 46, zA + 1, Pal.IRON);
        }
        c.fill(x0, 48, -4, x1, 49, 4, team);
        c.fill(x0, 47, -4, x1, 47, 4, Pal.IRON);
        // clear the lane under the lintel (7 high)
        c.clear(x0, 41, -2, x1, 47, 2);
    }
}
