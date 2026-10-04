package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

/**
 * Guard posts of the hall (the waiting room prefab adds 5 more, so the arena has 24): armed triangle soldiers on
 * the gallery and along the side walls, circle workers by the doors and stairs, square managers on the stage;
 * plus two optional patrol routes (the floor loop and the gallery rail).
 */
public final class Guards {
    private Guards() {
    }

    private static void post(BuildContext c, double x, double y, double z, float yaw, String rank) {
        c.marker(CommonMarkers.GUARD_POST, x, y, z, yaw, "rank=" + rank);
    }

    public static void build(BuildContext c) {
        double gy = Geo.BALCONY_FLOOR_Y + 1.0;
        // triangles: gallery (6) and the side walls (4)
        for (int x : new int[]{-24, -14, 14, 24}) {
            post(c, x + 0.5, gy, -6.5, 180f, "triangle");
        }
        post(c, -4.5, gy, -9.5, 180f, "triangle");
        post(c, 5.5, gy, -9.5, 180f, "triangle");
        for (int z : new int[]{-35, -63}) {
            post(c, -33.5, 1.0, z + 0.5, -90f, "triangle");
            post(c, 34.5, 1.0, z + 0.5, 90f, "triangle");
        }
        // circles: by the gate, the side doors and the stair feet
        post(c, -5.5, 1.0, -3.5, 180f, "circle");
        post(c, 6.5, 1.0, -3.5, 180f, "circle");
        post(c, -33.5, 1.0, -87.5, -90f, "circle");
        post(c, 34.5, 1.0, -87.5, 90f, "circle");
        post(c, -33.5, 1.0, -20.5, -90f, "circle");
        post(c, 34.5, 1.0, -20.5, 90f, "circle");
        // squares: managers on the stage
        post(c, -11.5, Geo.STAGE_TOP, -87.5, 0f, "square");
        post(c, 12.5, Geo.STAGE_TOP, -87.5, 0f, "square");
        post(c, 6.5, Geo.STAGE_TOP, -89.5, 0f, "square");

        // patrol loop on the floor (clockwise seen from above)
        double[][] loop = {{33.5, -82.5}, {33.5, -50.5}, {33.5, -21.5}, {18.5, -18.5}, {0.5, -14.5}, {-17.5, -18.5},
                {-32.5, -21.5}, {-32.5, -50.5}, {-32.5, -82.5}, {-17.5, -82.5}, {0.5, -82.5}, {18.5, -82.5}};
        for (int i = 0; i < loop.length; i++) {
            c.marker("guard.patrol", loop[i][0], 1.0, loop[i][1], 0f, "route=floor,i=" + i);
        }
        // gallery rail
        double[] rail = {-30.5, -18.5, -8.5, 8.5, 18.5, 30.5};
        for (int i = 0; i < rail.length; i++) {
            c.marker("guard.patrol", rail[i], gy, -6.5, 180f, "route=gallery,i=" + i);
        }
    }
}
