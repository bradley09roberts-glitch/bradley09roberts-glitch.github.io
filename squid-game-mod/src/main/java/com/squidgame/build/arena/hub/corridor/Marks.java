package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;

/**
 * Markers of the guard compound: {@code guard.post} (rank=triangle armed soldiers, circle workers, square managers) at
 * the checkpoint, the corners, the stairway doorway and inside the rooms, and {@code guard.patrol} routes
 * (a = east arm to the stairway, b = west arm, c = staff wing / barracks loop) with {@code i} = order.
 * All standing positions are on floor cells (y = 0) with two free blocks above.
 */
final class Marks {
    private Marks() {
    }

    private static void post(BuildContext c, double x, double z, float yaw, String rank) {
        c.marker("guard.post", x, 0.0, z, yaw, "rank=" + rank);
    }

    private static void patrol(BuildContext c, String route, int i, double x, double z, float yaw) {
        c.marker("guard.patrol", x, 0.0, z, yaw, "route=" + route + ",i=" + i);
    }

    static void build(BuildContext c) {
        // checkpoint hall: armed guards by the dorm door and beside the lane, workers at the booths, a manager at the gate
        post(c, -3.5, -35.5 - 0.0, 180f, "triangle");
        post(c, 4.5, -35.5, 180f, "triangle");
        post(c, -6.5, -37.5, -90f, "triangle");
        post(c, 7.5, -37.5, 90f, "triangle");
        post(c, -6.5, -40.5, -90f, "circle");
        post(c, 7.5, -40.5, 90f, "circle");
        post(c, -2.5, -42.5, 0f, "square");
        // junction and corners
        post(c, 4.5, -50.5, 180f, "triangle");
        post(c, 27.5, -46.5, 90f, "triangle");
        post(c, 25.5, -62.5, -90f, "triangle");
        post(c, 27.5, -76.5, 90f, "triangle");
        post(c, -27.5, -76.5, -90f, "triangle");
        post(c, -25.5, -62.5, 90f, "triangle");
        post(c, -27.5, -46.5, -90f, "triangle");
        // stairway doorway
        post(c, -5.5, -78.5, 0f, "triangle");
        post(c, 6.5, -78.5, 0f, "triangle");
        // rooms
        post(c, -27.5, -39.5, -90f, "circle");   // canteen server
        post(c, 14.5, -39.5, 0f, "circle");      // infirmary nurse
        post(c, -12.5, -57.5, 0f, "circle");     // barracks
        post(c, 12.5, -54.5, 180f, "square");    // monitoring supervisor
        post(c, -18.5, -66.5, -90f, "triangle"); // armory
        post(c, -3.5, -69.5, 0f, "square");      // manager's office
        post(c, 14.5, -67.5, 0f, "circle");      // store

        // route a: dorm door -> checkpoint -> east arm -> E2 -> stairway doorway
        double[][] a = {{0.5, -37.5}, {0.5, -42.5}, {0.5, -48.5}, {10.5, -48.5}, {19.5, -48.5}, {26.5, -48.5},
                {26.5, -56.5}, {26.5, -64.5}, {26.5, -72.5}, {26.5, -76.5}, {18.5, -76.5}, {9.5, -76.5}, {2.5, -76.5}, {0.5, -78.5}};
        float[] ya = {180, 180, -90, -90, -90, 180, 180, 180, 180, 90, 90, 90, 90, 180};
        for (int i = 0; i < a.length; i++) {
            patrol(c, "a", i, a[i][0], a[i][1], ya[i]);
        }
        // route b: junction -> west arm -> E2 west half
        double[][] b = {{0.5, -48.5}, {-10.5, -48.5}, {-19.5, -48.5}, {-26.5, -48.5}, {-26.5, -56.5}, {-26.5, -64.5},
                {-26.5, -72.5}, {-26.5, -76.5}, {-18.5, -76.5}, {-9.5, -76.5}, {-2.5, -76.5}};
        float[] yb = {90, 90, 90, 180, 180, 180, 180, -90, -90, -90, -90};
        for (int i = 0; i < b.length; i++) {
            patrol(c, "b", i, b[i][0], b[i][1], yb[i]);
        }
        // route c: staff wing -> barracks aisle and back, then the monitoring room
        double[][] cc = {{0.5, -50.5}, {0.5, -55.5}, {-6.5, -57.5}, {-14.5, -57.5}, {-20.5, -57.5}, {-14.5, -57.5},
                {-6.5, -57.5}, {0.5, -57.5}, {6.5, -57.5}, {13.5, -54.5}, {6.5, -57.5}, {0.5, -55.5}};
        float[] yc = {180, 180, 90, 90, 90, -90, -90, -90, -90, 0, 90, 0};
        for (int i = 0; i < cc.length; i++) {
            patrol(c, "c", i, cc[i][0], cc[i][1], yc[i]);
        }
    }
}
