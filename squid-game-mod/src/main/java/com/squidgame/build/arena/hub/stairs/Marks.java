package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.BuildContext;

import java.util.List;

/** Markers: the waypoints of the main route and the guard posts. */
final class Marks {
    private Marks() {
    }

    static int pathCount, guardCount;

    private static boolean freeAir(Ctx k, int x, int y, int z) {
        String a = k.get(x, y, z), b = k.get(x, y + 1, z);
        return (a == null || a.equals("minecraft:air")) && (b == null || b.equals("minecraft:air"));
    }

    private static boolean plainFloor(String b) {
        if (b == null || b.equals("minecraft:air")) {
            return false;
        }
        for (String t : new String[]{"panel_light", "_stairs", "_slab", "pane", "lantern", "rod", "chain", "light"}) {
            if (b.contains(t)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A standing cell on flat floor must not be a flush light panel (tools treat the name as a thin block): shift by one
     * block to a plain floor cell with 2 free blocks above. Cells over stairs are left alone.
     */
    private static int[] stand(Ctx k, int x, int y, int z) {
        String below = k.get(x, y - 1, z);
        if (below == null || !below.contains("panel_light")) {
            return new int[]{x, z};
        }
        int[][] opts = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}};
        for (int[] o : opts) {
            String b = k.get(x + o[0], y - 1, z + o[1]);
            if (plainFloor(b) && freeAir(k, x + o[0], y, z + o[1])) {
                return new int[]{x + o[0], z + o[1]};
            }
        }
        return new int[]{x, z};
    }

    /** stairs.path: a waypoint every 7 cells of the recorded centre line, first just inside the entrance, last at the gates. */
    static void path(Ctx k) {
        BuildContext c = k.c;
        List<int[]> line = k.line;
        int n = line.size();
        int step = 7;
        int count = 0;
        int last = -1;
        for (int i = 0; i < n; i += step) {
            last = i;
            count++;
        }
        boolean addLast = last != n - 1;
        int total = count + (addLast ? 1 : 0);
        int idx = 0;
        for (int j = 0; j < total; j++) {
            int i = j < count ? j * step : n - 1;
            int[] p = line.get(i);
            int[] q = line.get(Math.min(n - 1, i + step));
            if (q == p || i >= n - 1) {
                q = p;
                int[] before = line.get(Math.max(0, i - 3));
                // keep the heading of the previous stretch
                q = new int[]{p[0] + (p[0] - before[0]), p[1], p[2] + (p[2] - before[2])};
            }
            double dx = q[0] - p[0], dz = q[2] - p[2];
            float yaw = (dx == 0 && dz == 0) ? 180f : (float) Math.toDegrees(Math.atan2(-dx, dz));
            int[] sp = stand(k, p[0], p[1], p[2]);
            c.marker("stairs.path", sp[0] + 0.5, p[1], sp[1] + 0.5, yaw, "i=" + idx++);
        }
        pathCount = idx;
    }

    /** guard.post: triangles overlook the hall from the terrace edge and upper landings, squares and circles near the gates. */
    static void guards(Ctx k) {
        BuildContext c = k.c;
        int[][] posts = {
                // x, standLevel, z, yaw, rank (0 circle, 1 triangle, 2 square)
                {-40, 60, -162, 0, 1}, {-26, 60, -162, 0, 1}, {-13, 60, -162, 0, 1}, {13, 60, -162, 0, 1},
                {26, 60, -162, 0, 1}, {40, 60, -162, 0, 1},
                {-5, 60, -166, 0, 2}, {5, 60, -166, 0, 2},
                {-43, 60, -168, 0, 0}, {43, 60, -168, 0, 0},
        };
        String[] rank = {"circle", "triangle", "square"};
        for (int[] p : posts) {
            int[] sp = stand(k, p[0], p[1], p[2]);
            c.marker("guard.post", sp[0] + 0.5, p[1], sp[1] + 0.5, p[3], "rank=" + rank[p[4]]);
            guardCount++;
        }
        // upper landings of the main route: one armed guard each (on the cell beside the walking line)
        for (int[] l : k.landings) {
            if ((l[4] == 36 || l[4] == 48 || l[4] == 24) && Math.min(l[2] - l[0], l[3] - l[1]) >= 8) {
                int gx = (l[0] + l[2]) / 2, gz = (l[1] + l[3]) / 2;
                // choose a cell off the centre line toward the longer side
                int ox = (l[2] - l[0]) >= (l[3] - l[1]) ? 0 : 2;
                int oz = ox == 0 ? 2 : 0;
                int[] sp = stand(k, gx + ox, l[4], gz + oz);
                c.marker("guard.post", sp[0] + 0.5, l[4], sp[1] + 0.5, 0f, "rank=triangle");
                guardCount++;
            }
        }
    }
}
