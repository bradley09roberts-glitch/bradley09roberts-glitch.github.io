package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.BuildContext;

/** Orchestrates the build passes of the stairway hall. */
public final class Builder {
    private Builder() {
    }

    /** {@code -Dsquid.stairs.debug=true} prints pass statistics (developer tooling only). */
    private static final boolean DEBUG = Boolean.getBoolean("squid.stairs.debug");

    public static void build(BuildContext c) {
        Ctx k = new Ctx(c);
        Shell.build(k);
        Terrace.build(k);
        // the galleries behind the facade arches stay clear of decor
        k.occ.mark(-45, 0, -170, 45, 54, -163, Occ.KEEP);
        MainRoute.build(k);
        // keep the entrance plaza and the view up the grand stair free of decor
        k.occ.mark(-16, 0, -112, 16, 72, -82, Occ.KEEP);
        for (int[] l : k.landings) {
            if (l[4] >= 12) {
                Pavilion.build(k, l[0], l[1], l[2], l[3], l[4], Pal.wheel(l[4] / 12 + 1), Pal.wheel(l[4] / 12 + 3), Pal.CREAM);
            }
        }
        Extras.arcades(k, 400);
        Wander.run(k, 2500, 5);
        Extras.soloFlights(k, 4000);
        Extras.ceilingStairs(k, 600);
        if (DEBUG) {
            long vol = 91L * 73 * 89;
            int solid = k.occ.count(-45, 0, -170, 45, 72, -82, Occ.SOLID | Occ.DECOR);
            int any = k.occ.count(-45, 0, -170, 45, 72, -82, Occ.SOLID | Occ.DECOR | Occ.CLEAR | Occ.KEEP);
            System.out.printf("occupancy after decor: solid %.1f%%, solid+headroom %.1f%%%n", 100.0 * solid / vol, 100.0 * any / vol);
            System.out.println("decor chains " + Wander.chains + ", segments " + Wander.segments + ", solo flights " + Extras.solo
                    + ", hanging " + Extras.hanging + ", arcades " + Extras.arcades);
        }
        for (int[] l : k.allLandings) {
            if (k.landings.contains(l) || l[4] < 9) {
                continue;
            }
            int w = l[2] - l[0] + 1, d = l[3] - l[1] + 1;
            if (w >= 9 && d >= 9 && k.rng.chance(0.45) && Pavilion.fits(k, l[0], l[1], l[2], l[3], l[4])) {
                Pavilion.build(k, l[0], l[1], l[2], l[3], l[4], Pal.wheel(l[4] / 6 + 2), Pal.wheel(l[4] / 6 + 4), Pal.CREAM);
            }
        }
        Extras.portals(k);
        Kit.rails(k, k.walkRects);
        Lights.run(k);
        Marks.path(k);
        Marks.guards(k);
        if (DEBUG) {
            System.out.println("portals " + Extras.portals + ", lights: lattice " + Lights.lattice + ", patches " + Lights.placed
                    + ", unfixed " + Lights.unfixed + ", need cells " + Lights.needCells + ", waypoints " + Marks.pathCount
                    + ", guard posts " + Marks.guardCount);
        }
    }
}
