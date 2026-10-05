package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * Masked guards' posts: armed triangles overlook the hall from the two galleries and flank the exit door, squares
 * (managers) stand at the exit and round the podium, circles (workers) at the registration plinth and the arrival pad.
 * 18 posts, all on free floor / deck with two blocks of air above.
 */
final class Guards {
    private Guards() {
    }

    static void build(BuildContext c) {
        // east gallery (catwalk, standing level 12), facing the hall (west)
        for (int z : new int[]{-14, -9, 9, 14}) {
            post(c, 38.5, Layout.GALLERY_Y, z + 0.5, 90f, "triangle");
        }
        // west gallery, facing east
        for (int z : new int[]{-14, -9, 9, 14}) {
            post(c, -38.5, Layout.GALLERY_Y, z + 0.5, -90f, "triangle");
        }
        // exit door: two armed guards at the jambs, a manager and a worker behind them
        post(c, -6.5, 0, -29.5, 0f, "triangle");
        post(c, 7.5, 0, -29.5, 0f, "triangle");
        post(c, -10.5, 0, -30.5, 0f, "square");
        post(c, 11.5, 0, -30.5, 0f, "circle");
        // plaza: managers either side of the podium
        post(c, -10.5, 0, 0.5, -90f, "square");
        post(c, 11.5, 0, 0.5, 90f, "square");
        // registration: workers at the plinth, armed guards on the arrival pad
        post(c, -9.5, 0, 28.5, 180f, "circle");
        post(c, 10.5, 0, 28.5, 180f, "circle");
        post(c, -10.5, 0, 22.5, 180f, "triangle");
        post(c, 11.5, 0, 22.5, 180f, "triangle");
    }

    private static void post(BuildContext c, double x, double y, double z, float yaw, String rank) {
        c.marker("guard.post", x, y, z, yaw, "rank=" + rank);
    }
}
