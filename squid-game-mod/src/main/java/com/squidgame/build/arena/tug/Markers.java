package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All markers and regions of the Tug of War arena (the prefab emits the waiting room's own). */
final class Markers {
    private Markers() {
    }

    static final int SLOTS = 48;

    static void build(BuildContext c) {
        slots(c);
        lobbies(c);
        spare(c);
        regions(c);
        guards(c);
        c.marker("tug.pit_floor", 0.5, Geo.PIT + 1, 0.5, 0f);
        c.marker(CommonMarkers.SPECTATOR, 0.5, Geo.CAT + 1, 2.5, -90f);
        c.marker(CommonMarkers.EXIT, 90.5, Geo.DECK + 1, 0.5, -90f);
    }

    private static void slots(BuildContext c) {
        for (int k = 0; k < SLOTS; k++) {
            c.marker("tug.slot_a", -8.5 - k, Geo.DECK + 1, 0.5, -90f, "slot=" + k);
            c.marker("tug.slot_b", 8.5 + k, Geo.DECK + 1, 0.5, 90f, "slot=" + k);
        }
        c.marker("tug.rope_a", -7.0, 42.2, 0.5, -90f);
        c.marker("tug.rope_b", 7.0, 42.2, 0.5, 90f);
        c.marker("tug.rope_center", 0.0, 42.2, 0.5, 0f);
    }

    /** Waiting grids on the plateaus: 2-block pitch, inside the painted team zone, ordered nearest the deck gate first. */
    private static void lobbies(BuildContext c) {
        List<int[]> cells = new ArrayList<>();
        for (int x = -72; x <= -66; x += 2) {
            for (int z = -13; z <= 13; z += 2) {
                if (!PlateauBuilder.isLight(x, z)) {
                    cells.add(new int[]{x, z});
                }
            }
        }
        cells.sort(Comparator.comparingDouble(a -> Math.hypot(a[0] + 61, a[1])));
        for (int i = 0; i < cells.size(); i++) {
            int[] p = cells.get(i);
            c.marker("tug.waiting_a", p[0] + 0.5, Geo.DECK + 1, p[1] + 0.5, -90f, "slot=" + i);
            c.marker("tug.waiting_b", Sym.mx(p[0]) + 0.5, Geo.DECK + 1, p[1] + 0.5, 90f, "slot=" + i);
        }
    }

    /** Gallery for contestants who sit out: the south lower gallery, two rows, facing the pit. */
    private static void spare(BuildContext c) {
        List<int[]> cells = new ArrayList<>();
        for (int x = -58; x <= 57; x += 2) {
            for (int z : new int[]{32, 34}) {
                cells.add(new int[]{x, z});
            }
        }
        cells.sort(Comparator.comparingDouble(a -> Math.abs(a[0] + 0.5) + (a[1] == 34 ? 0.5 : 0)));
        for (int i = 0; i < cells.size(); i++) {
            int[] p = cells.get(i);
            c.marker("tug.spare", p[0] + 0.5, Geo.DECK + 1, p[1] + 0.5, 180f, "slot=" + i);
        }
    }

    private static void regions(BuildContext c) {
        c.region("tug.edge_a", -9, Geo.DECK, -3, -8, Geo.DECK + 5, 3);
        c.region("tug.edge_b", 7, Geo.DECK, -3, 8, Geo.DECK + 5, 3);
        c.region("tug.pit", Geo.PX0, Geo.PIT, -Geo.PZ, Geo.PX1, Geo.DECK - 1, Geo.PZ);
        c.region("arena.bounds", Geo.HX0, Geo.PIT, -Geo.HZ, Geo.HX1, Geo.CEIL - 1, Geo.HZ);
    }

    private static void guards(BuildContext c) {
        final String G = CommonMarkers.GUARD_POST;
        // triangles (armed): upper catwalks overlooking both decks, facing the pit (6 per catwalk)
        for (int x : new int[]{-46, -30, -14, 13, 29, 45}) {
            c.marker(G, x + 0.5, Geo.CAT + 1, -33.5, 0f, "rank=triangle");
            c.marker(G, x + 0.5, Geo.CAT + 1, 33.5, 180f, "rank=triangle");
        }
        // triangles on the plateaus beside the deck portals
        for (int sgn : new int[]{-1, 1}) {
            c.marker(G, -64.5, Geo.DECK + 1, 7.5 * sgn, -90f, "rank=triangle");
            c.marker(G, 63.5, Geo.DECK + 1, 7.5 * sgn, 90f, "rank=triangle");
        }
        // squares (managers): inside the control booth and on the gantry approaches
        c.marker(G, -4.5, Geo.CAT + 1, 3.5, -90f, "rank=square");
        c.marker(G, 3.5, Geo.CAT + 1, -3.5, 90f, "rank=square");
        c.marker(G, 0.5, Geo.CAT + 1, -14.5, 0f, "rank=square");
        c.marker(G, -0.5, Geo.CAT + 1, 15.5, 180f, "rank=square");
        // circles (workers): beside the gate door and the exit door, on the lower gallery
        c.marker(G, -73.5, Geo.DECK + 1, -6.5, -90f, "rank=circle");
        c.marker(G, -73.5, Geo.DECK + 1, 6.5, -90f, "rank=circle");
        c.marker(G, 72.5, Geo.DECK + 1, -6.5, 90f, "rank=circle");
        c.marker(G, 72.5, Geo.DECK + 1, 6.5, 90f, "rank=circle");
        c.marker(G, -0.5, Geo.DECK + 1, -33.5, 0f, "rank=circle");
    }
}
