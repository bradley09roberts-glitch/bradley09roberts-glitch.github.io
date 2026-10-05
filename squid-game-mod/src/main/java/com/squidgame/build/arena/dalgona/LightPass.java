package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;
import com.squidgame.build.StateString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes the enclosed hall bright. Block light drops by one per block, so lamps hanging near a 22 high ceiling
 * cannot light the floor. This pass simulates vanilla block light over the finished structure (every visible
 * fixture counts: lanterns, shroomlights, glowing window panels...) and then greedily places the fewest invisible
 * {@code minecraft:light[level=15]} blocks (no collision, ignored by pathfinding) on free cells so that every walkable
 * standing cell reaches {@link #FEET_TARGET} at the feet and {@link #HEAD_TARGET} at head height.
 *
 * <p>Walkable cells considered: the floor (y 1), the gate doorway row, the stage (y 2), the gallery (y 11) and the stair
 * run to it. Slabs, stairs, fences and the like are conservatively treated as light blockers (vanilla lets light pass
 * through them), so the result holds under either model.
 */
public final class LightPass {
    public static final int FEET_TARGET = 12;
    public static final int HEAD_TARGET = 11;

    private static final int X0 = -40, X1 = 40, Y0 = -2, Y1 = 26, Z0 = -96, Z1 = 4;
    private static final int NX = X1 - X0 + 1, NY = Y1 - Y0 + 1, NZ = Z1 - Z0 + 1;
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final BuildContext c;
    private final byte[] kind = new byte[NX * NY * NZ];      // 0 open air, 1 transparent partial block, 2 opaque
    private final byte[] light = new byte[NX * NY * NZ];
    private final Set<Integer> avoid = new HashSet<>();
    private final List<int[]> targets = new ArrayList<>();   // feet cells
    private final int[] targetAt = new int[NX * NY * NZ];
    private final Map<String, int[]> cache = new HashMap<>();
    private int placed;

    private LightPass(BuildContext c) {
        this.c = c;
    }

    /** Runs the pass and returns the number of light blocks placed. */
    public static int run(BuildContext c) {
        LightPass p = new LightPass(c);
        p.scan();
        p.solve();
        return p.placed;
    }

    private static int idx(int x, int y, int z) {
        return ((y - Y0) * NZ + (z - Z0)) * NX + (x - X0);
    }

    private static boolean inside(int x, int y, int z) {
        return x >= X0 && x <= X1 && y >= Y0 && y <= Y1 && z >= Z0 && z <= Z1;
    }

    // ---------------------------------------------------------------------------------------------
    // classification

    private int[] info(String state) {
        return cache.computeIfAbsent(state == null ? "" : state, s -> classify(s.isEmpty() ? null : s));
    }

    /** {kind, emission}. */
    static int[] classify(String state) {
        if (state == null) {
            return new int[]{0, 0};
        }
        String id = StateString.blockId(state);
        String n = id.substring(id.indexOf(':') + 1);
        int emit = 0;
        switch (n) {
            case "lantern", "shroomlight", "sea_lantern", "glowstone", "jack_o_lantern", "froglight", "lava" -> emit = 15;
            case "soul_lantern" -> emit = 10;
            case "torch", "wall_torch", "end_rod" -> emit = 14;
            case "magma_block" -> emit = 3;
            case "light" -> {
                String lv = StateString.property(state, "level");
                emit = lv == null ? 15 : Integer.parseInt(lv);
            }
            case "campfire" -> emit = "true".equals(StateString.property(state, "lit")) ? 15 : 0;
            case "candle" -> {
                String lit = StateString.property(state, "lit");
                String cn = StateString.property(state, "candles");
                emit = "true".equals(lit) ? 3 * (cn == null ? 1 : Integer.parseInt(cn)) : 0;
            }
            default -> {
                if (n.startsWith("panel_light_")) {
                    emit = 15;
                }
            }
        }
        int kind;
        if (n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("light")) {
            kind = 0;
        } else if (n.equals("sea_lantern")) {
            kind = 2;
        } else if (isPartial(n)) {
            kind = 1;
        } else {
            kind = 2;
        }
        return new int[]{kind, emit};
    }

    private static boolean isPartial(String n) {
        // Deliberately pessimistic: slabs, stairs, trapdoors, fences and walls are treated as light blockers (vanilla
        // lets light pass), so the result holds under either model.
        String[] parts = {"glass", "pane", "bars", "chain", "ladder", "lantern",
                "carpet", "button", "pressure_plate", "torch", "sign", "banner", "candle", "flower_pot", "potted_",
                "rail", "lever", "string", "tripwire", "snow", "end_rod", "lectern", "bell", "hook",
                "vine", "azalea_leaves", "honey_block"};
        for (String p : parts) {
            if (n.contains(p)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // scan the finished structure

    private void scan() {
        java.util.Arrays.fill(targetAt, -1);
        for (int y = Y0; y <= Y1; y++) {
            for (int z = Z0; z <= Z1; z++) {
                for (int x = X0; x <= X1; x++) {
                    int[] in = info(c.get(x, y, z));
                    int i = idx(x, y, z);
                    kind[i] = (byte) in[0];
                    light[i] = (byte) in[1];
                }
            }
        }
        propagateAll();
        c.buffer().forEachMarker(m -> {
            int bx = (int) Math.floor(m.x()) - c.originX();
            int by = (int) Math.floor(m.y() + 1e-6) - c.originY();
            int bz = (int) Math.floor(m.z()) - c.originZ();
            if (inside(bx, by, bz)) {
                avoid.add(idx(bx, by, bz));
                if (inside(bx, by + 1, bz)) {
                    avoid.add(idx(bx, by + 1, bz));
                }
            }
        });
        for (int z = Geo.Z_FRONT; z <= Geo.REAR_WALL_Z; z++) {      // up to and including the gate doorway row
            for (int x = -Geo.HALF_W; x <= Geo.HALF_W; x++) {
                for (int y = 1; y <= 11; y++) {
                    if (!wanted(x, y, z)) {
                        continue;
                    }
                    if (kind[idx(x, y, z)] == 0 && kind[idx(x, y + 1, z)] == 0 && kind[idx(x, y - 1, z)] == 2) {
                        targetAt[idx(x, y, z)] = targets.size();
                        targets.add(new int[]{x, y, z});
                    }
                }
            }
        }
        // the stair run to the gallery: the cell above every step
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < Rear.STAIR_STEPS; k++) {
                for (int d = Rear.STAIR_X0; d <= Rear.STAIR_X1; d++) {
                    int x = side * d, y = 2 + k, z = Rear.STAIR_Z0 + k;
                    if (kind[idx(x, y, z)] == 0 && kind[idx(x, y + 1, z)] == 0 && targetAt[idx(x, y, z)] < 0) {
                        targetAt[idx(x, y, z)] = targets.size();
                        targets.add(new int[]{x, y, z});
                    }
                }
            }
        }
    }

    /** Floor, stage and gallery levels only (not shelves, brackets or other high ledges). */
    private boolean wanted(int x, int y, int z) {
        if (y == 1) {
            return true;
        }
        if (y == 2) {
            return z <= Geo.STAGE_Z1 && Math.abs(x) <= Geo.STAGE_X;
        }
        return y == Geo.BALCONY_FLOOR_Y + 1 && z >= Geo.BALCONY_Z0 - 7;
    }

    // ---------------------------------------------------------------------------------------------
    // light propagation

    private void propagateAll() {
        List<List<int[]>> buckets = newBuckets();
        for (int y = Y0; y <= Y1; y++) {
            for (int z = Z0; z <= Z1; z++) {
                for (int x = X0; x <= X1; x++) {
                    int l = light[idx(x, y, z)];
                    if (l > 1) {
                        buckets.get(l).add(new int[]{x, y, z});
                    }
                }
            }
        }
        flood(buckets);
    }

    private static List<List<int[]>> newBuckets() {
        List<List<int[]>> b = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            b.add(new ArrayList<>());
        }
        return b;
    }

    private void flood(List<List<int[]>> buckets) {
        for (int l = 15; l >= 2; l--) {
            List<int[]> cur = buckets.get(l);
            for (int k = 0; k < cur.size(); k++) {
                int[] p = cur.get(k);
                if (light[idx(p[0], p[1], p[2])] != l) {
                    continue;
                }
                for (int[] d : DIRS) {
                    int nx = p[0] + d[0], ny = p[1] + d[1], nz = p[2] + d[2];
                    if (!inside(nx, ny, nz)) {
                        continue;
                    }
                    int ni = idx(nx, ny, nz);
                    if (kind[ni] != 2 && light[ni] < l - 1) {
                        light[ni] = (byte) (l - 1);
                        buckets.get(l - 1).add(new int[]{nx, ny, nz});
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // greedy placement

    private boolean satisfied(int[] t, int[] extra) {
        int fi = idx(t[0], t[1], t[2]);
        int hi = idx(t[0], t[1] + 1, t[2]);
        int f = Math.max(light[fi], extra == null ? 0 : extra[0]);
        int h = Math.max(light[hi], extra == null ? 0 : extra[1]);
        return f >= FEET_TARGET && h >= HEAD_TARGET;
    }

    private boolean deficient(int ti) {
        return !satisfied(targets.get(ti), null);
    }

    private void solve() {
        boolean[] done = new boolean[targets.size()];
        for (int ti = 0; ti < targets.size(); ti++) {
            if (done[ti]) {
                continue;
            }
            if (satisfied(targets.get(ti), null)) {
                continue;
            }
            int[] t = targets.get(ti);
            int[] best = null;
            int bestScore = -1;
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dx = -3; dx <= 3; dx++) {
                        if (Math.abs(dx) + Math.abs(dz) + dy > 3) {
                            continue;
                        }
                        int qx = t[0] + dx, qy = t[1] + dy, qz = t[2] + dz;
                        if (!inside(qx, qy, qz) || kind[idx(qx, qy, qz)] != 0 || avoid.contains(idx(qx, qy, qz))) {
                            continue;
                        }
                        if (Math.abs(qx) > Geo.HALF_W || qz < Geo.Z_FRONT || qz > Geo.REAR_WALL_Z) {
                            continue;
                        }
                        int score = score(qx, qy, qz, ti);
                        if (score > bestScore) {
                            bestScore = score;
                            best = new int[]{qx, qy, qz};
                        }
                    }
                }
            }
            if (best == null || bestScore <= 0) {
                best = new int[]{t[0], t[1] + 1, t[2]};
            }
            place(best[0], best[1], best[2]);
        }
    }

    /** Number of currently deficient targets (always including ti) that a light at q would satisfy. */
    private int score(int qx, int qy, int qz, int ti) {
        Map<Integer, Integer> dist = bfs(qx, qy, qz, 4);
        int n = 0;
        boolean self = false;
        for (Map.Entry<Integer, Integer> e : dist.entrySet()) {
            int cell = e.getKey();
            // a target's feet cell: look it up through its own index
            int tj = targetAt[cell];
            if (tj < 0) {
                continue;
            }
            int[] t = targets.get(tj);
            if (satisfied(t, null)) {
                continue;
            }
            int fd = e.getValue();
            Integer hd = dist.get(idx(t[0], t[1] + 1, t[2]));
            int[] extra = {15 - fd, hd == null ? 0 : 15 - hd};
            if (satisfied(t, extra)) {
                n++;
                if (tj == ti) {
                    self = true;
                }
            }
        }
        return self ? n : 0;
    }

    private Map<Integer, Integer> bfs(int sx, int sy, int sz, int maxDepth) {
        Map<Integer, Integer> dist = new HashMap<>();
        List<int[]> frontier = new ArrayList<>();
        dist.put(idx(sx, sy, sz), 0);
        frontier.add(new int[]{sx, sy, sz});
        for (int depth = 1; depth <= maxDepth; depth++) {
            List<int[]> next = new ArrayList<>();
            for (int[] p : frontier) {
                for (int[] d : DIRS) {
                    int nx = p[0] + d[0], ny = p[1] + d[1], nz = p[2] + d[2];
                    if (!inside(nx, ny, nz)) {
                        continue;
                    }
                    int ni = idx(nx, ny, nz);
                    if (kind[ni] == 2 || dist.containsKey(ni)) {
                        continue;
                    }
                    dist.put(ni, depth);
                    next.add(new int[]{nx, ny, nz});
                }
            }
            frontier = next;
        }
        return dist;
    }

    private void place(int x, int y, int z) {
        c.set(x, y, z, Pal.LIGHT);
        int i = idx(x, y, z);
        kind[i] = 0;
        light[i] = 15;
        List<List<int[]>> buckets = newBuckets();
        buckets.get(15).add(new int[]{x, y, z});
        flood(buckets);
        placed++;
    }
}
