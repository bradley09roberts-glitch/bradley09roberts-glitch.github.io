package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * Guard posts (triangles overlook the whole court from towers, bastions, the school roof and the rim wall; squares
 * manage from the gallery terrace and the school porch; circles work the gate and the podium), the patrol route, the
 * exit gathering point and the arena bounds.
 */
final class Guards {
    private Guards() {
    }

    static void markers(BuildContext c) {
        // triangles on the rim wall (stand on the cap, 4.0), away from the lanterns
        c.marker("guard.post", -50.5, 4.0, -0.5, -90f, "rank=triangle");
        c.marker("guard.post", 51.5, 4.0, -43.5, 90f, "rank=triangle");
        c.marker("guard.post", 51.5, 4.0, 44.5, 90f, "rank=triangle");
        // triangles on the school roof (stand 13.0)
        c.marker("guard.post", -35.5, 13.0, -54.5, 0f, "rank=triangle");
        c.marker("guard.post", 35.5, 13.0, -54.5, 0f, "rank=triangle");
        // squares (managers) on the grandstand terrace and at the school porch
        for (int z : new int[]{-24, 24}) {
            c.marker("guard.post", 84.5, Gallery.TOP_FLOOR + 1.0, z + 0.5, 90f, "rank=square");
        }
        c.marker("guard.post", 0.5, 2.0, -49.5, 0f, "rank=square");
        // circles (workers) beside the podium
        c.marker("guard.post", -9.5, 1.0, -39.5, 0f, "rank=circle");
        c.marker("guard.post", 9.5, 1.0, -39.5, 0f, "rank=circle");

        // patrol: a loop around the court inside the clear belt
        double[][] loop = {{-10.5, -29.5}, {0.5, -30.5}, {10.5, -29.5}, {10.5, -14.5}, {10.5, 0.5}, {10.5, 14.5},
                {10.5, 28.5}, {0.5, 29.5}, {-10.5, 28.5}, {-10.5, 14.5}, {-10.5, 0.5}, {-10.5, -14.5}};
        for (int i = 0; i < loop.length; i++) {
            c.marker("guard.patrol", loop[i][0], 1.0, loop[i][1], 0f, "route=a,i=" + i);
        }

        // survivors gather at the front of the terrace, facing the court
        c.marker(CommonMarkers.EXIT, 80.5, Gallery.TOP_FLOOR + 1.0, 0.5, 90f);

        c.region(CommonMarkers.REGION_BOUNDS, YX0, 0, YZ0, YX1, 50, YZ1);
    }
}
