package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.BlockBuffer;

/** Snapshot of the hall region of a buffer (hub-local coordinates) as classification words, plus a block-light solver. */
final class Grid {
    static final int X0 = -49, X1 = 49, Y0 = -8, Y1 = 78, Z0 = -177, Z1 = -80;
    static final int NX = X1 - X0 + 1, NY = Y1 - Y0 + 1, NZ = Z1 - Z0 + 1;

    final int[] info = new int[NX * NY * NZ];
    final byte[] light = new byte[NX * NY * NZ];

    static int idx(int x, int y, int z) {
        return ((y - Y0) * NZ + (z - Z0)) * NX + (x - X0);
    }

    static boolean in(int x, int y, int z) {
        return x >= X0 && x <= X1 && y >= Y0 && y <= Y1 && z >= Z0 && z <= Z1;
    }

    int info(int x, int y, int z) {
        return in(x, y, z) ? info[idx(x, y, z)] : Blocks.FULL | 8;
    }

    /** Reads the buffer; hub origin is world (0, originY, 0). */
    static Grid snapshot(BlockBuffer b, int originY) {
        Grid g = new Grid();
        int[] palInfo = new int[b.paletteSize()];
        for (int i = 0; i < palInfo.length; i++) {
            palInfo[i] = i == 0 ? 0 : Blocks.classify(b.paletteState(i));
        }
        for (int y = Y0; y <= Y1; y++) {
            for (int z = Z0; z <= Z1; z++) {
                for (int x = X0; x <= X1; x++) {
                    int id = b.getRaw(x, y + originY, z);
                    g.info[idx(x, y, z)] = palInfo[id];
                }
            }
        }
        return g;
    }

    void setInfo(int x, int y, int z, int inf) {
        if (in(x, y, z)) {
            info[idx(x, y, z)] = inf;
        }
    }

    // ------------------------------------------------------------------ light

    private static final int[] DX = {1, -1, 0, 0, 0, 0}, DY = {0, 0, 1, -1, 0, 0}, DZ = {0, 0, 0, 0, 1, -1};

    /** Full recompute: sources are all emitters, light travels through non-opaque cells, one level per block. */
    void computeLight() {
        java.util.Arrays.fill(light, (byte) 0);
        int[][] queue = new int[16][];
        int[] qn = new int[16];
        for (int l = 0; l < 16; l++) {
            queue[l] = new int[1024];
        }
        for (int y = Y0; y <= Y1; y++) {
            for (int z = Z0; z <= Z1; z++) {
                for (int x = X0; x <= X1; x++) {
                    int id = idx(x, y, z);
                    int e = Blocks.emit(info[id]);
                    if (e > 0) {
                        light[id] = (byte) e;
                        if (qn[e] == queue[e].length) {
                            queue[e] = java.util.Arrays.copyOf(queue[e], qn[e] * 2);
                        }
                        queue[e][qn[e]++] = id;
                    }
                }
            }
        }
        for (int l = 15; l >= 2; l--) {
            for (int qi = 0; qi < qn[l]; qi++) {
                int id = queue[l][qi];
                if (light[id] != l) {
                    continue;
                }
                spread(id, l, queue, qn);
            }
        }
    }

    private void spread(int id, int l, int[][] queue, int[] qn) {
        int x = id % NX + X0;
        int t = id / NX;
        int z = t % NZ + Z0;
        int y = t / NZ + Y0;
        for (int d = 0; d < 6; d++) {
            int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
            if (!in(nx, ny, nz)) {
                continue;
            }
            int nid = idx(nx, ny, nz);
            if (Blocks.opaque(info[nid]) || light[nid] >= l - 1) {
                continue;
            }
            light[nid] = (byte) (l - 1);
            int nl = l - 1;
            if (nl >= 2) {
                if (qn[nl] == queue[nl].length) {
                    queue[nl] = java.util.Arrays.copyOf(queue[nl], qn[nl] * 2);
                }
                queue[nl][qn[nl]++] = nid;
            }
        }
    }

    /** Adds a light source of level {@code e} at a cell (already stored in info) and spreads it incrementally. */
    void addSource(int x, int y, int z, int e) {
        if (!in(x, y, z)) {
            return;
        }
        int[][] queue = new int[16][];
        int[] qn = new int[16];
        for (int l = 0; l < 16; l++) {
            queue[l] = new int[256];
        }
        int id = idx(x, y, z);
        if (light[id] < e) {
            light[id] = (byte) e;
        }
        queue[e][qn[e]++] = id;
        for (int l = e; l >= 2; l--) {
            for (int qi = 0; qi < qn[l]; qi++) {
                int c = queue[l][qi];
                if (light[c] != l) {
                    continue;
                }
                spread(c, l, queue, qn);
            }
        }
    }

    int lightAt(int x, int y, int z) {
        return in(x, y, z) ? light[idx(x, y, z)] : 0;
    }

    /** Light a source of {@code e} at (cx,cy,cz) would give to (x,y,z) through free cells, within radius e (no state change). */
    int trial(int cx, int cy, int cz, int e, int x, int y, int z) {
        int dist = Math.abs(cx - x) + Math.abs(cy - y) + Math.abs(cz - z);
        if (dist >= e) {
            return 0;
        }
        // BFS limited to the diamond; small and local
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        java.util.HashMap<Integer, Integer> seen = new java.util.HashMap<>();
        q.add(new int[]{cx, cy, cz, e});
        seen.put(idx(cx, cy, cz), e);
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[0] == x && c[1] == y && c[2] == z) {
                return c[3];
            }
            if (c[3] <= 1) {
                continue;
            }
            for (int d = 0; d < 6; d++) {
                int nx = c[0] + DX[d], ny = c[1] + DY[d], nz = c[2] + DZ[d];
                if (!in(nx, ny, nz) || Blocks.opaque(info[idx(nx, ny, nz)])) {
                    continue;
                }
                int nl = c[3] - 1;
                // prune: must be able to still reach the target
                if (nl - (Math.abs(nx - x) + Math.abs(ny - y) + Math.abs(nz - z)) < 1) {
                    continue;
                }
                Integer prev = seen.get(idx(nx, ny, nz));
                if (prev != null && prev >= nl) {
                    continue;
                }
                seen.put(idx(nx, ny, nz), nl);
                q.add(new int[]{nx, ny, nz, nl});
            }
        }
        return 0;
    }
}
