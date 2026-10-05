package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Light guarantee. The village has no ambient light (roofed sky), so after everything is built this pass floods block
 * light from every emitter (lanterns, glowstone, sea lanterns ...) the way the game does and then adds ground lanterns
 * next to walls wherever a walkable ground cell is darker than {@link #TARGET}. Stairs and slabs count as opaque
 * (pessimistic), so the real light is never lower than what is verified here. Pair plots are never touched (they are
 * lit by their own lamps by design).
 */
final class Lighting {
    static final int TARGET = 10;
    private static final int X0 = -70, X1 = 70, Z0 = -60, Z1 = 60, Y0 = -1, Y1 = 17;
    private static final int NX = X1 - X0 + 1, NY = Y1 - Y0 + 1, NZ = Z1 - Z0 + 1;
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final BuildContext c;
    private final boolean[] opaque = new boolean[NX * NY * NZ];
    private final byte[] light = new byte[NX * NY * NZ];
    private final Map<String, int[]> cache = new HashMap<>();
    private final List<Region> plots = new ArrayList<>();
    private final boolean[] forbidden = new boolean[NX * NZ];
    int placed;

    private Lighting(BuildContext c) {
        this.c = c;
    }

    static int ensure(BuildContext c) {
        Lighting l = new Lighting(c);
        l.scan();
        l.run();
        return l.placed;
    }

    private static int idx(int x, int y, int z) {
        return ((x - X0) * NY + (y - Y0)) * NZ + (z - Z0);
    }

    private static boolean in(int x, int y, int z) {
        return x >= X0 && x <= X1 && y >= Y0 && y <= Y1 && z >= Z0 && z <= Z1;
    }

    /** {opaque?1:0, emit level} for a block state string. */
    private int[] info(String s) {
        int[] r = cache.get(s);
        if (r != null) {
            return r;
        }
        String b = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
        b = b.substring(b.indexOf(':') + 1);
        boolean trans = b.equals("air") || b.equals("cave_air") || b.equals("void_air") || b.contains("glass") || b.contains("pane")
                || b.contains("fence") || b.endsWith("_wall") || b.equals("chain") || b.contains("lantern") && !b.equals("sea_lantern")
                || b.contains("torch") || b.endsWith("door") || b.contains("trapdoor") || b.equals("ladder") || b.endsWith("carpet")
                || b.endsWith("leaves") || b.equals("iron_bars") || b.equals("flower_pot") || b.startsWith("potted") || b.contains("rod")
                || b.contains("sign") || b.contains("banner") || b.equals("cauldron") || b.equals("water") || b.equals("lily_pad")
                || b.equals("light") || b.contains("candle") || b.contains("button") || b.contains("plate") || b.contains("rail")
                || b.equals("vine") || b.equals("bell") || b.contains("sapling");
        int emit = 0;
        if (b.equals("soul_lantern")) {
            emit = 10;
        } else if (b.equals("lantern") || b.equals("sea_lantern") || b.equals("glowstone") || b.equals("shroomlight")
                || b.equals("jack_o_lantern") || b.contains("froglight") || b.equals("campfire") || b.startsWith("panel_light")) {
            emit = 15;
        } else if (b.contains("torch") || b.equals("end_rod")) {
            emit = 14;
        } else if (b.equals("light")) {
            int i = s.indexOf("level=");
            emit = i < 0 ? 15 : Integer.parseInt(s.substring(i + 6, s.indexOf(']', i)));
        } else if (b.endsWith("candle") && s.contains("lit=true")) {
            int i = s.indexOf("candles=");
            emit = 3 * (i < 0 ? 1 : s.charAt(i + 8) - '0');
        }
        r = new int[]{trans ? 0 : 1, emit};
        cache.put(s, r);
        return r;
    }

    private void scan() {
        List<int[]> sources = new ArrayList<>();
        for (int x = X0; x <= X1; x++) {
            for (int y = Y0; y <= Y1; y++) {
                for (int z = Z0; z <= Z1; z++) {
                    String s = c.get(x, y, z);
                    if (s == null) {
                        continue;
                    }
                    int[] in = info(s);
                    int i = idx(x, y, z);
                    opaque[i] = in[0] == 1;
                    if (in[1] > 0) {
                        sources.add(new int[]{x, y, z, in[1]});
                    }
                }
            }
        }
        for (int[] s : sources) {
            spread(s[0], s[1], s[2], s[3]);
        }
        for (Region r : c.buffer().regions("marbles.plot")) {
            plots.add(r);
        }
        // forbid cells near markers so a lantern never lands on a spawn / standing spot
        c.buffer().forEachMarker(m -> {
            int mx = m.bx() - c.originX(), mz = m.bz() - c.originZ();
            int rad = m.name().startsWith("marbles.pair") || m.name().startsWith("gate") || m.name().startsWith("waiting")
                    || m.name().equals("arena.exit") || m.name().startsWith("marbles.exit") ? 1 : 0;
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    int x = mx + dx, z = mz + dz;
                    if (x >= X0 && x <= X1 && z >= Z0 && z <= Z1) {
                        forbidden[(x - X0) * NZ + (z - Z0)] = true;
                    }
                }
            }
        });
    }

    private final int[] queue = new int[NX * NY * NZ * 2];

    /** max-merge light flood from one source cell. */
    private void spread(int sx, int sy, int sz, int level) {
        int si = idx(sx, sy, sz);
        if (light[si] >= level) {
            return;
        }
        light[si] = (byte) level;
        int head = 0, tail = 0;
        queue[tail++] = sx;
        queue[tail++] = sy;
        queue[tail++] = sz;
        while (head < tail) {
            int x = queue[head++], y = queue[head++], z = queue[head++];
            int cur = light[idx(x, y, z)];
            if (cur <= 1) {
                continue;
            }
            for (int[] d : DIRS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (!in(nx, ny, nz)) {
                    continue;
                }
                int ni = idx(nx, ny, nz);
                if (opaque[ni] || light[ni] >= cur - 1) {
                    continue;
                }
                light[ni] = (byte) (cur - 1);
                if (tail + 3 > queue.length) {
                    tail = compact(head, tail);
                    head = 0;
                }
                queue[tail++] = nx;
                queue[tail++] = ny;
                queue[tail++] = nz;
            }
        }
    }

    private int compact(int head, int tail) {
        int n = tail - head;
        System.arraycopy(queue, head, queue, 0, n);
        return n;
    }

    // ------------------------------------------------------------------ walkable cells and greedy fill

    private boolean isFloor(int x, int z) {
        String b = c.get(x, 0, z);
        if (b == null) {
            return false;
        }
        String id = b.contains("[") ? b.substring(0, b.indexOf('[')) : b;
        return !(id.endsWith("air") || id.contains("water") || id.contains("carpet") || id.contains("lantern") && !id.contains("sea")
                || id.contains("plate") || id.contains("pane") || id.contains("bars") || id.contains("chain") || id.contains("lily"));
    }

    private boolean freeBody(int x, int y, int z) {
        String b = c.get(x, y, z);
        if (b == null) {
            return true;
        }
        String id = b.substring(b.indexOf(':') + 1);
        if (id.contains("[")) {
            id = id.substring(0, id.indexOf('['));
        }
        return id.endsWith("air") || id.contains("carpet") || id.equals("lantern") || id.contains("torch") || id.contains("sign")
                || id.contains("banner") || id.contains("button") || id.contains("plate") || id.contains("rail") || id.equals("light")
                || id.equals("lily_pad") || id.contains("candle") || id.startsWith("potted") || id.equals("end_rod");
    }

    private boolean walkable(int x, int z) {
        return x >= Layout.X0 && x <= Layout.X1 && z >= Layout.Z0 && z <= Layout.Z1 && isFloor(x, z)
                && freeBody(x, 1, z) && freeBody(x, 2, z);
    }

    private boolean inPlot(int x, int z) {
        double wx = c.originX() + x + 0.5, wz = c.originZ() + z + 0.5;
        for (Region r : plots) {
            if (r.containsXZ(wx, wz)) {
                return true;
            }
        }
        return false;
    }

    private boolean wallAdjacent(int x, int z) {
        for (int[] d : DIRS) {
            if (d[1] != 0) {
                continue;
            }
            int nx = x + d[0], nz = z + d[2];
            if (in(nx, 1, nz) && opaque[idx(nx, 1, nz)] && opaque[idx(nx, 2, nz)]) {
                return true;
            }
        }
        return false;
    }

    private void run() {
        // collect walkable ground cells
        List<int[]> cells = new ArrayList<>();
        for (int x = Layout.X0; x <= Layout.X1; x++) {
            for (int z = Layout.Z0; z <= Layout.Z1; z++) {
                if (walkable(x, z)) {
                    cells.add(new int[]{x, z});
                }
            }
        }
        for (int iter = 0; iter < 900; iter++) {
            int[] worst = null;
            int wl = TARGET;
            for (int[] p : cells) {
                int l = light[idx(p[0], 1, p[1])];
                if (l < wl) {
                    wl = l;
                    worst = p;
                }
            }
            if (worst == null) {
                break;
            }
            // choose the best wall-adjacent cell near the worst cell: the one lifting most dark cells
            int bestScore = -1;
            int[] best = null;
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    int x = worst[0] + dx, z = worst[1] + dz;
                    if (Math.abs(dx) + Math.abs(dz) > 5 || !walkable(x, z) || forbidden[(x - X0) * NZ + (z - Z0)]
                            || inPlot(x, z) || !wallAdjacent(x, z) || light[idx(x, 1, z)] >= 15) {
                        continue;
                    }
                    int score = 0;
                    for (int ex = -6; ex <= 6; ex++) {
                        for (int ez = -6; ez <= 6; ez++) {
                            int d = Math.abs(ex) + Math.abs(ez);
                            if (d > 6) {
                                continue;
                            }
                            int cx = x + ex, cz = z + ez;
                            if (!in(cx, 1, cz) || opaque[idx(cx, 1, cz)]) {
                                continue;
                            }
                            int cur = light[idx(cx, 1, cz)];
                            int nl = 15 - d;
                            if (cur < TARGET && nl > cur && nl >= TARGET - 1) {
                                score += Math.min(nl, TARGET) - cur + 1;
                            }
                        }
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = new int[]{x, z};
                    }
                }
            }
            if (best == null) {
                // nothing suitable next to the worst cell: drop it from the list so we continue with the rest
                final int[] w = worst;
                cells.removeIf(p -> p[0] == w[0] && p[1] == w[1]);
                continue;
            }
            c.set(best[0], 1, best[1], Mat.lantern(false));
            spread(best[0], 1, best[1], 15);
            placed++;
        }
    }
}
