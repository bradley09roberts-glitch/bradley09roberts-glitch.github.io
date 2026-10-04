package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

import java.util.ArrayList;
import java.util.List;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * Markers and regions of the Red Light arena (the contract is in docs/ARENA_MARKERS.md): the start spawn grid, the
 * zone / line / bounds regions, the doll and tree anchors, the survivors' exit and the spectator viewpoint.
 */
public final class Markers {
    private Markers() {
    }

    /**
     * Start grid: 7 rows (1.4 apart, nearest the line first) x 71 columns (1.4 apart) inside the start zone
     * (z 0..11, x -50..50), facing +Z. Slots are numbered centre-out in a wide block so that any number of contestants
     * forms a compact crowd behind the middle of the line rather than a thin strip across the whole width.
     */
    public static void startSpawns(BuildContext c) {
        List<double[]> pts = new ArrayList<>();
        for (int j = 0; j < 7; j++) {
            double z = 10.5 - 1.4 * j;
            for (int k = -35; k <= 35; k++) {
                double x = 0.5 + 1.4 * k;
                double key = Math.max(Math.abs(k) / 4.0, j);
                pts.add(new double[]{x, z, key, j, Math.abs(k), k});
            }
        }
        pts.sort((a, b) -> {
            for (int i = 2; i < 6; i++) {
                int cmp = Double.compare(a[i], b[i]);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        });
        int slot = 0;
        for (double[] p : pts) {
            c.marker("redlight.start_spawn", p[0], 1.0, p[1], 0f, "slot=" + slot++);
        }
    }

    public static void keyMarkers(BuildContext c) {
        // the doll stands on her pedestal facing the tree (yaw 0 = +Z)
        c.marker("redlight.doll", DOLL_X + 0.5, 2.0, DOLL_Z + 0.5, 0f);
        c.marker("redlight.tree", TREE_X + 0.5, 1.0, TREE_Z + 0.5, 0f);
        // survivors gather in the western lounge of the safe zone, facing the doll
        c.marker(CommonMarkers.EXIT, -24.5, 1.0, 158.5, -90f);
        GatePortal.markers(c);
    }

    public static void regions(BuildContext c) {
        c.region("arena.bounds", -HALF_W, -4, -6, HALF_W, 80, 178);
        c.region("redlight.start_zone", -LANE_X, 0, START_ZONE_Z0, LANE_X, 12, START_ZONE_Z1);
        c.region("redlight.start_line", -LANE_X, 0, START_LINE_Z, LANE_X, 3, START_LINE_Z + 1);
        c.region("redlight.finish_line", -LANE_X, 0, FINISH_LINE_Z, LANE_X, 3, FINISH_LINE_Z + 1);
        c.region("redlight.safe_zone", -HALF_W, 0, SAFE_Z0, HALF_W, 60, SAFE_Z1);
    }
}
