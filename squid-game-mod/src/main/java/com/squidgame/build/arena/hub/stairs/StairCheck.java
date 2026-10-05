package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.BlockBuffer;
import com.squidgame.build.BuildContext;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import com.squidgame.build.arena.hub.StairwayPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer validation of the stairway hall (not used at runtime):
 * {@code java -cp <classes>:<fastutil> com.squidgame.build.arena.hub.stairs.StairCheck [seed]} builds the part into a
 * buffer and checks, with the half-block walking model, that
 * (1) every stairs.path waypoint is standable and consecutive waypoints are connected by walking (steps of at most
 * half a block, no jumping), (2) the route reaches every gate doorway, (3) the route is at least 3 wide everywhere,
 * (4) block light (pessimistic: stairs and slabs block light) is at least 10 on every walkable position reachable
 * from the entrance, and (5) there are no unprotected drops next to the main route.
 */
public final class StairCheck {
    private StairCheck() {
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1L;
        BlockBuffer buf = new BlockBuffer();
        BuildContext ctx = new BuildContext(buf, 0, 64, 0, seed);
        long t0 = System.nanoTime();
        new StairwayPart().build(ctx);
        long t1 = System.nanoTime();
        System.out.printf("build: %d ms, %,d blocks%n", (t1 - t0) / 1_000_000, buf.solidCount());
        report(buf, true);
    }

    /** A reachable standing sub-column among the four touching the marker (an entity straddles them), or null. */
    private static int[] findPos(Walker w, Walker.Reach r, Marker m) {
        int bx = (int) Math.floor(m.x()), bz = (int) Math.floor(m.z());
        int h = (int) Math.round(m.y() * 2 - 128);
        for (int qx = 0; qx < 2; qx++) {
            for (int qz = 0; qz < 2; qz++) {
                int sx = 2 * bx + qx, sz = 2 * bz + qz;
                if (r.has(sx, sz, h)) {
                    return new int[]{sx, sz, h};
                }
            }
        }
        return null;
    }

    static boolean report(BlockBuffer buf, boolean verbose) {
        boolean ok = true;
        Grid g = Grid.snapshot(buf, 64);
        g.computeLight();
        Walker w = new Walker(g);
        int sx0 = 1, sz0 = -163, h0 = 0;
        Walker.Reach strict = w.flood(sx0, sz0, h0, 1, 1);
        Walker.Reach hop = w.flood(sx0, sz0, h0, 2, 2);
        System.out.printf("walkable positions: strict %,d, with 1-block hops %,d%n", strict.order.size(), hop.order.size());

        // waypoints
        List<Marker> path = new ArrayList<>(buf.markers("stairs.path"));
        path.sort((a, b) -> Integer.compare(a.getInt("i", 0), b.getInt("i", 0)));
        System.out.println("stairs.path markers: " + path.size());
        int bad = 0;
        double maxGap = 0, minGap = 1e9, total = 0;
        for (int i = 0; i < path.size(); i++) {
            Marker m = path.get(i);
            int[] pos = findPos(w, strict, m);
            if (pos == null) {
                bad++;
                System.out.printf("  waypoint %d (%.1f, %.1f, %.1f) is not strictly reachable%n", i, m.x(), m.y() - 64, m.z());
                continue;
            }
            if (i > 0) {
                Marker p = path.get(i - 1);
                double d = Math.abs(m.x() - p.x()) + Math.abs(m.z() - p.z()) + Math.abs(m.y() - p.y());
                maxGap = Math.max(maxGap, d);
                minGap = Math.min(minGap, d);
                total += d;
                int[] pp = findPos(w, strict, p);
                if (pp == null) {
                    continue;
                }
                // consecutive waypoints must be linked by walking (no hops) without leaving the neighbourhood
                Walker.Reach r = w.flood(pp[0], pp[1], pp[2], 1, 1);
                int[] tgt = findPos(w, r, m);
                if (tgt == null) {
                    bad++;
                    System.out.printf("  waypoints %d -> %d are not connected by walking%n", i - 1, i);
                } else {
                    List<int[]> seg = w.path(r, tgt[0], tgt[1], tgt[2]);
                    if (seg != null && seg.size() > 2 * (int) d + 40) {
                        System.out.printf("  note: waypoints %d -> %d detour (%d sub-steps for %.0f)%n", i - 1, i, seg.size(), d);
                    }
                }
            }
        }
        if (!path.isEmpty()) {
            System.out.printf("waypoint spacing (manhattan incl. height): min %.1f, max %.1f, mean %.1f%n", minGap, maxGap,
                    total / Math.max(1, path.size() - 1));
        }
        ok &= bad == 0 && !path.isEmpty();

        // gates
        String[] gates = {"gate.red_light", "gate.dalgona", "gate.tug_of_war", "gate.marbles", "gate.glass_bridge", "gate.final"};
        int gatesOk = 0;
        for (String name : gates) {
            Region rg = buf.region(name);
            if (rg == null) {
                System.out.println("  missing region " + name);
                ok = false;
                continue;
            }
            int lx = rg.minX(), hx = rg.maxX(), y = rg.minY() - 64, z = rg.minZ();
            int found = 0, total2 = 0;
            for (int sx = 2 * lx; sx <= 2 * hx + 1; sx++) {
                for (int sz = 2 * z; sz <= 2 * z + 1; sz++) {
                    total2++;
                    if (strict.has(sx, sz, 2 * y)) {
                        found++;
                    }
                }
            }
            System.out.printf("  %-18s x[%d,%d] y[%d,%d] z[%d,%d] size %dx%dx%d, walkable sub-cells inside: %d/%d%n", name,
                    rg.minX(), rg.maxX(), rg.minY() - 64, rg.maxY() - 64, rg.minZ(), rg.maxZ(), rg.sizeX(), rg.sizeY(),
                    rg.sizeZ(), found, total2);
            if (found == 0) {
                ok = false;
            } else {
                gatesOk++;
            }
        }
        System.out.println("gates reachable: " + gatesOk + "/6");

        // width along the route to the last waypoint
        if (!path.isEmpty()) {
            Marker last = path.get(path.size() - 1);
            int[] lp = findPos(w, strict, last);
            List<int[]> route = lp == null ? null : w.path(strict, lp[0], lp[1], lp[2]);
            if (route != null) {
                int minRun = Integer.MAX_VALUE;
                int[] at = null;
                for (int i = 0; i < route.size(); i++) {
                    int[] a = route.get(Math.max(0, i - 4)), b = route.get(Math.min(route.size() - 1, i + 4));
                    int dx = Math.abs(b[0] - a[0]), dz = Math.abs(b[1] - a[1]);
                    boolean alongX = dx < dz;   // lateral axis perpendicular to travel
                    int run = w.lateralRun(route.get(i)[0], route.get(i)[1], route.get(i)[2], alongX, 1, 1);
                    if (run < minRun) {
                        minRun = run;
                        at = route.get(i);
                    }
                }
                System.out.printf("route to last waypoint: %d sub-steps, minimum lateral width %.1f blocks at (%.1f, %d, %.1f)%n",
                        route.size(), minRun / 2.0, at[0] / 2.0, at[2] / 2, at[1] / 2.0);
                ok &= minRun >= 6;
            } else {
                System.out.println("no route to the last waypoint");
                ok = false;
            }
        }

        // unprotected drops next to the route (within 6 blocks of a waypoint) and anywhere reachable
        int dropsNear = 0, dropsAll = 0;
        int[] sample = null;
        java.util.HashSet<Long> seenEdge = new java.util.HashSet<>();
        int[][] nd = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] p : strict.order) {
            int[] c = Walker.cellOf(p[0], p[1], p[2]);
            for (int[] dd : nd) {
                int nx = c[0] + dd[0], nz = c[2] + dd[1];
                if (Math.floorDiv(p[0], 2) == nx && Math.floorDiv(p[1], 2) == nz) {
                    continue;
                }
                // the neighbouring block column: walking off needs free feet/head cells and a drop of 3+ below
                int fy = c[1];
                boolean free = Blocks.kind(g.info(nx, fy, nz)) == Blocks.NONE && Blocks.kind(g.info(nx, fy + 1, nz)) == Blocks.NONE;
                if (!free) {
                    continue;
                }
                boolean drop = true;
                for (int dy = 1; dy <= 3; dy++) {
                    if (Blocks.kind(g.info(nx, fy - dy, nz)) != Blocks.NONE) {
                        drop = false;
                        break;
                    }
                }
                if (!drop || fy - 3 <= 0) {
                    continue;
                }
                long key = (((long) (c[0] + 100)) << 42) | (((long) (c[1] + 100)) << 30) | (((long) (c[2] + 300)) << 8) | (dd[0] + 1) * 3 + (dd[1] + 1);
                if (!seenEdge.add(key)) {
                    continue;
                }
                dropsAll++;
                if (dropsAll <= 12) {
                    System.out.printf("  open edge at block (%d,%d,%d) toward (%d,%d)%n", c[0], c[1], c[2], dd[0], dd[1]);
                }
                boolean near = false;
                for (Marker m : path) {
                    if (Math.abs(m.x() - (p[0] / 2.0)) <= 6 && Math.abs(m.z() - (p[1] / 2.0)) <= 6 && Math.abs(m.y() - 64 - p[2] / 2.0) <= 3) {
                        near = true;
                        break;
                    }
                }
                if (near) {
                    dropsNear++;
                    if (sample == null) {
                        sample = new int[]{c[0], c[1], c[2], dd[0], dd[1]};
                    }
                }
            }
        }
        System.out.printf("unprotected drop edges: near the route %d%s, anywhere reachable %d%n", dropsNear,
                sample == null ? "" : " (e.g. block " + sample[0] + "," + sample[1] + "," + sample[2] + " toward " + sample[3] + "," + sample[4] + ")",
                dropsAll);
        ok &= dropsNear == 0;

        // light
        int[] hist = new int[16];
        int dark = 0;
        int minL = 99;
        int[] worst = null;
        java.util.HashSet<Long> cells = new java.util.HashSet<>();
        for (int[] p : hop.order) {
            int[] c = Walker.cellOf(p[0], p[1], p[2]);
            long key = (((long) (c[0] + 100)) << 40) | (((long) (c[1] + 100)) << 20) | (c[2] + 300);
            if (!cells.add(key)) {
                continue;
            }
            int l = g.lightAt(c[0], c[1], c[2]);
            hist[l]++;
            if (l < 10) {
                dark++;
            }
            if (l < minL) {
                minL = l;
                worst = c;
            }
        }
        System.out.printf("light on %,d walkable cells (incl. 1-block hops): min %d at %s, below 10: %d%n", cells.size(), minL,
                worst == null ? "-" : worst[0] + "," + worst[1] + "," + worst[2], dark);
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < 16; l++) {
            if (hist[l] > 0) {
                sb.append(l).append(':').append(hist[l]).append(' ');
            }
        }
        System.out.println("light histogram " + sb);
        ok &= dark == 0;
        // ambience: light next to visible surfaces anywhere in the hall (not just where one can walk)
        int[] sh = new int[16];
        long surf = 0;
        for (int y = 0; y <= 72; y++) {
            for (int z = -170; z <= -82; z++) {
                for (int x = -45; x <= 45; x++) {
                    int inf = g.info(x, y, z);
                    if (Blocks.opaque(inf)) {
                        continue;
                    }
                    boolean touches = Blocks.opaque(g.info(x + 1, y, z)) || Blocks.opaque(g.info(x - 1, y, z))
                            || Blocks.opaque(g.info(x, y + 1, z)) || Blocks.opaque(g.info(x, y - 1, z))
                            || Blocks.opaque(g.info(x, y, z + 1)) || Blocks.opaque(g.info(x, y, z - 1));
                    if (touches) {
                        surf++;
                        sh[g.lightAt(x, y, z)]++;
                    }
                }
            }
        }
        StringBuilder sb2 = new StringBuilder();
        long below8 = 0, below5 = 0;
        for (int l = 0; l < 16; l++) {
            if (sh[l] > 0) {
                sb2.append(l).append(':').append(sh[l]).append(' ');
            }
            if (l < 8) {
                below8 += sh[l];
            }
            if (l < 5) {
                below5 += sh[l];
            }
        }
        System.out.printf("air cells touching a surface: %,d, below light 8: %,d (%.1f%%), below 5: %,d (%.1f%%)%n", surf, below8,
                100.0 * below8 / surf, below5, 100.0 * below5 / surf);
        System.out.println("surface light histogram " + sb2);
        System.out.println(ok ? "CHECK OK" : "CHECK FAILED");
        return ok;
    }
}
