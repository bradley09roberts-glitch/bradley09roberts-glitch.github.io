package com.squidgame.build.arena.hub.stairs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Half-block resolution walking model: every block column is split into 2 x 2 sub-columns, collision comes from the
 * exact stair / slab shapes, a standing position needs support below and 4 half-layers (2 blocks) of free space above.
 * Positions are (sx, sz, h) with h = 2 * standing level. Moves are 4-neighbour steps between sub-columns.
 */
final class Walker {
    static final int SX0 = 2 * Grid.X0, SZ0 = 2 * Grid.Z0;
    static final int SXN = Grid.NX * 2, SZN = Grid.NZ * 2;
    static final int MAXL = 16;

    final Grid g;
    final short[][] levels;

    Walker(Grid g) {
        this.g = g;
        levels = new short[SXN * SZN][];
        int layers = Grid.NY * 2;
        boolean[] solid = new boolean[layers];
        short[] tmp = new short[MAXL];
        for (int sx = SX0; sx < SX0 + SXN; sx++) {
            int x = Math.floorDiv(sx, 2), qx = Math.floorMod(sx, 2);
            for (int sz = SZ0; sz < SZ0 + SZN; sz++) {
                int z = Math.floorDiv(sz, 2), qz = Math.floorMod(sz, 2);
                for (int y = Grid.Y0; y <= Grid.Y1; y++) {
                    int info = g.info[Grid.idx(x, y, z)];
                    int base = 2 * (y - Grid.Y0);
                    if (info == 0) {
                        solid[base] = false;
                        solid[base + 1] = false;
                    } else {
                        solid[base] = Blocks.solidHalf(info, qx, qz, 0);
                        solid[base + 1] = Blocks.solidHalf(info, qx, qz, 1);
                    }
                }
                int n = 0;
                for (int lb = 1; lb + 3 < layers; lb++) {
                    if (solid[lb - 1] && !solid[lb] && !solid[lb + 1] && !solid[lb + 2] && !solid[lb + 3]) {
                        if (n < MAXL) {
                            tmp[n++] = (short) (lb + 2 * Grid.Y0);
                        }
                    }
                }
                levels[col(sx, sz)] = n == 0 ? null : java.util.Arrays.copyOf(tmp, n);
            }
        }
    }

    static int col(int sx, int sz) {
        return (sx - SX0) * SZN + (sz - SZ0);
    }

    static boolean inCols(int sx, int sz) {
        return sx >= SX0 && sx < SX0 + SXN && sz >= SZ0 && sz < SZ0 + SZN;
    }

    int levelIndex(int sx, int sz, int h) {
        if (!inCols(sx, sz)) {
            return -1;
        }
        short[] l = levels[col(sx, sz)];
        if (l == null) {
            return -1;
        }
        for (int i = 0; i < l.length; i++) {
            if (l[i] == h) {
                return i;
            }
        }
        return -1;
    }

    /** Result of a flood fill. */
    static final class Reach {
        final Walker w;
        final boolean[] seen = new boolean[SXN * SZN * MAXL];
        final int[] parent = new int[SXN * SZN * MAXL];
        final List<int[]> order = new ArrayList<>();

        Reach(Walker w) {
            this.w = w;
        }

        static int pid(int col, int li) {
            return col * MAXL + li;
        }

        boolean has(int sx, int sz, int h) {
            int li = w.levelIndex(sx, sz, h);
            return li >= 0 && seen[pid(col(sx, sz), li)];
        }
    }

    private static final int[] DX = {1, -1, 0, 0}, DZ = {0, 0, 1, -1};

    /**
     * Flood fill from a start position. {@code maxUp} / {@code maxDown} are the largest allowed height changes in half
     * blocks per step (1 = walk, 2 = also hop one block).
     */
    Reach flood(int sx, int sz, int h, int maxUp, int maxDown) {
        return flood(sx, sz, h, maxUp, maxDown, null);
    }

    Reach flood(int sx, int sz, int h, int maxUp, int maxDown, int[] stopAt) {
        Reach r = new Reach(this);
        int li0 = levelIndex(sx, sz, h);
        if (li0 < 0) {
            return r;
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int p0 = Reach.pid(col(sx, sz), li0);
        r.seen[p0] = true;
        r.parent[p0] = -1;
        q.add(new int[]{sx, sz, li0});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int cx = c[0], cz = c[1];
            int ch = levels[col(cx, cz)][c[2]];
            r.order.add(new int[]{cx, cz, ch});
            if (stopAt != null && cx == stopAt[0] && cz == stopAt[1] && ch == stopAt[2]) {
                return r;
            }
            int cp = Reach.pid(col(cx, cz), c[2]);
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d], nz = cz + DZ[d];
                if (!inCols(nx, nz)) {
                    continue;
                }
                short[] nl = levels[col(nx, nz)];
                if (nl == null) {
                    continue;
                }
                for (int i = 0; i < nl.length; i++) {
                    int dh = nl[i] - ch;
                    if (dh > maxUp || dh < -maxDown) {
                        continue;
                    }
                    int np = Reach.pid(col(nx, nz), i);
                    if (r.seen[np]) {
                        continue;
                    }
                    r.seen[np] = true;
                    r.parent[np] = cp;
                    q.add(new int[]{nx, nz, i});
                }
            }
        }
        return r;
    }

    /** Position (sx, sz, h) from a pid. */
    int[] decode(int pid) {
        int col = pid / MAXL, li = pid % MAXL;
        int sx = col / SZN + SX0, sz = col % SZN + SZ0;
        return new int[]{sx, sz, levels[col][li]};
    }

    /** Path from the flood's start to the position (sx, sz, h), or null. */
    List<int[]> path(Reach r, int sx, int sz, int h) {
        int li = levelIndex(sx, sz, h);
        if (li < 0 || !r.seen[Reach.pid(col(sx, sz), li)]) {
            return null;
        }
        List<int[]> out = new ArrayList<>();
        int p = Reach.pid(col(sx, sz), li);
        while (p >= 0) {
            out.add(decode(p));
            p = r.parent[p];
        }
        java.util.Collections.reverse(out);
        return out;
    }

    /** Evaluation cell (block coordinates) of a standing position: the first free cell above the support. */
    static int[] cellOf(int sx, int sz, int h) {
        return new int[]{Math.floorDiv(sx, 2), (h + 1) >> 1, Math.floorDiv(sz, 2)};
    }

    /** Number of contiguous standing sub-columns through (sx, sz, h) along a lateral axis (both directions). */
    int lateralRun(int sx, int sz, int h, boolean alongX, int maxUp, int maxDown) {
        int n = 1;
        for (int dir = -1; dir <= 1; dir += 2) {
            int cx = sx, cz = sz, ch = h;
            while (true) {
                int nx = cx + (alongX ? dir : 0), nz = cz + (alongX ? 0 : dir);
                if (!inCols(nx, nz)) {
                    break;
                }
                short[] nl = levels[col(nx, nz)];
                int pick = Integer.MIN_VALUE;
                if (nl != null) {
                    for (short v : nl) {
                        int dh = v - ch;
                        if (dh <= maxUp && dh >= -maxDown) {
                            pick = v;
                            break;
                        }
                    }
                }
                if (pick == Integer.MIN_VALUE) {
                    break;
                }
                cx = nx;
                cz = nz;
                ch = pick;
                n++;
                if (n > 200) {
                    break;
                }
            }
        }
        return n;
    }
}
