package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

import java.util.List;

/** Guard posts: armed triangles on roofs / the hall terrace / the tower, circle workers at alley ends, square managers on the stage. */
final class Guards {
    private Guards() {
    }

    static void build(BuildContext c, List<double[]> roofPosts) {
        // hall terrace (floor y = 12): corner kiosks and the front parapet
        for (int[] g : Hall.guardSpots()) {
            c.marker(CommonMarkers.GUARD_POST, g[0] + 0.5, Hall.TERRACE_Y, g[1] + 0.5, g[2], "rank=triangle");
        }
        // lookout tower deck
        c.marker(CommonMarkers.GUARD_POST, -2.5, Civic.TOWER_DECK_Y, 20 + 4.5, 0f, "rank=triangle");
        c.marker(CommonMarkers.GUARD_POST, 3.5, Civic.TOWER_DECK_Y, 20 + 4.5, 0f, "rank=triangle");
        // roof terraces of flat-roof houses (x, y, z, yaw) collected by the builder
        for (double[] p : roofPosts) {
            c.marker(CommonMarkers.GUARD_POST, p[0], p[1], p[2], (float) p[3], "rank=triangle");
        }
        // workers at the alley ends and mouths
        double[][] circles = {
                {-46.5, 1, -50.5, 0}, {-46.5, 1, 50.5, 180}, {47.5, 1, -50.5, 0}, {47.5, 1, 50.5, 180},
                {-24.5, 1, -30.5, 0}, {-24.5, 1, 31.5, 180}, {25.5, 1, -30.5, 0}, {25.5, 1, 31.5, 180},
                {-62.5, 1, 3.5, -90}, {63.5, 1, 3.5, 90}, {-20.5, 1, -37.5, -90}, {21.5, 1, -37.5, 90},
                {-20.5, 1, 38.5, -90}, {21.5, 1, 38.5, 90}
        };
        for (double[] p : circles) {
            c.marker(CommonMarkers.GUARD_POST, p[0], p[1], p[2], (float) p[3], "rank=circle");
        }
        // managers on the stage
        for (double x : new double[]{-5.5, 0.5, 6.5}) {
            c.marker(CommonMarkers.GUARD_POST, x, 2.0, 13.5, 180f, "rank=square");
        }
        // a patrol ring around the square's paving
        double[][] ring = {{-13.5, -4.5}, {-13.5, 10.5}, {-4.5, 10.5}, {4.5, 10.5}, {13.5, 10.5}, {13.5, -4.5}, {4.5, -4.5}, {-4.5, -4.5}};
        for (int i = 0; i < ring.length; i++) {
            c.marker(CommonMarkers.GUARD_PATROL, ring[i][0], 1.0, ring[i][1], 0f, "route=square,i=" + i);
        }
    }
}
