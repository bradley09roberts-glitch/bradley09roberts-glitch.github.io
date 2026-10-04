package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

/**
 * Guard posts (25 here plus the 5 of the waiting room prefab = 30): armed triangles on the guard ring and on the
 * side gantries overlooking the whole bridge, managers (squares) on the cross gantry and the gallery, workers
 * (circles) on the platforms and in the lounge; and one patrol route round the guard ring.
 */
final class Guards {
    private Guards() {
    }

    private static void post(BuildContext c, double x, double y, double z, float yaw, String rank) {
        c.marker(CommonMarkers.GUARD_POST, x, y, z, yaw, "rank=" + rank);
    }

    static void build(BuildContext c) {
        double ring = Geo.RING + 1;
        // triangles on the guard ring along the long walls (walkway cell x = -43 / 42), facing the bridge
        for (int z : new int[]{15, 43, 71}) {
            post(c, -42.5, ring, z + 0.5, -90f, "triangle");
            post(c, 42.5, ring, z + 0.5, 90f, "triangle");
        }
        // triangles over the exit wall, facing the start
        post(c, 20.5, ring, 77.5, 180f, "triangle");
        post(c, -20.5, ring, 77.5, 180f, "triangle");
        // triangles on the two side gantries (stand x = 22 / -23)
        for (int z : new int[]{16, 56}) {
            post(c, 22.5, Geo.SIDE_GANTRY + 1, z + 0.5, 90f, "triangle");
            post(c, -22.5, Geo.SIDE_GANTRY + 1, z + 0.5, -90f, "triangle");
        }
        // squares on the cross gantry
        for (int x : new int[]{-31, -9, 8, 30}) {
            post(c, x + 0.5, Geo.GANTRY + 1, Geo.ZC + 0.5, 0f, "square");
        }
        // a square in the gallery
        post(c, -13.5, BALCONY_STAND, -4.5, 0f, "square");
        // circles on the platforms (black tile cells, not queue/gathering slots) and in the lounge
        post(c, -7.5, Geo.STAND, 6.5, 0f, "circle");
        post(c, 7.5, Geo.STAND, 6.5, 0f, "circle");
        post(c, -5.5, Geo.STAND, -5.5, 0f, "circle");
        post(c, -7.5, Geo.STAND, 70.5, 180f, "circle");
        post(c, 7.5, Geo.STAND, 70.5, 180f, "circle");
        post(c, -5.5, Geo.STAND, 76.5, 180f, "circle");
        post(c, 8.5, Geo.STAND, 87.5, 180f, "circle");
        post(c, -8.5, Geo.STAND, 87.5, 180f, "circle");
        patrol(c);
    }

    static final double BALCONY_STAND = WallArt.BALCONY_Y + 1;

    /** One loop round the guard ring (walkway cells x = -44 / 43, z = -6 / 78). */
    private static void patrol(BuildContext c) {
        double y = Geo.RING + 1;
        double[][] pts = {
                {-43.5, -5.5}, {-43.5, 36.5}, {-43.5, 78.5}, {0.5, 78.5},
                {43.5, 78.5}, {43.5, 36.5}, {43.5, -5.5}, {0.5, -5.5}
        };
        float[] yaw = {0f, 0f, -90f, -90f, 180f, 180f, 90f, 90f};
        for (int i = 0; i < pts.length; i++) {
            c.marker("guard.patrol", pts[i][0], y, pts[i][1], yaw[i], "route=ring,i=" + i);
        }
    }
}
