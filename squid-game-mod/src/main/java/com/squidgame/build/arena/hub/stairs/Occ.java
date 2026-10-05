package com.squidgame.build.arena.hub.stairs;

/**
 * Planning grid over the hall (local coordinates): remembers where the main route's structure and head-room are, so
 * decorative elements keep clear of them, and which cells are walking surfaces that want railings.
 */
final class Occ {
    static final int MINX = -49, MAXX = 49, MINY = -6, MAXY = 78, MINZ = -177, MAXZ = -80;
    private static final int SX = MAXX - MINX + 1, SY = MAXY - MINY + 1, SZ = MAXZ - MINZ + 1;

    /** structure of the main route (never overwritten by decor) */
    static final int SOLID = 1;
    /** head-room above the main route that decor must keep free */
    static final int CLEAR = 2;
    /** decorative structure */
    static final int DECOR = 4;
    /** standing cell above a landing / bridge floor (candidate for railings) */
    static final int WALK = 8;
    /** railing placed */
    static final int RAIL = 16;
    /** protected view corridor / reserved by shell features */
    static final int KEEP = 32;

    private final byte[] g = new byte[SX * SY * SZ];

    static boolean in(int x, int y, int z) {
        return x >= MINX && x <= MAXX && y >= MINY && y <= MAXY && z >= MINZ && z <= MAXZ;
    }

    private static int idx(int x, int y, int z) {
        return ((y - MINY) * SZ + (z - MINZ)) * SX + (x - MINX);
    }

    void mark(int x1, int y1, int z1, int x2, int y2, int z2, int flag) {
        int lx = Math.max(MINX, Math.min(x1, x2)), hx = Math.min(MAXX, Math.max(x1, x2));
        int ly = Math.max(MINY, Math.min(y1, y2)), hy = Math.min(MAXY, Math.max(y1, y2));
        int lz = Math.max(MINZ, Math.min(z1, z2)), hz = Math.min(MAXZ, Math.max(z1, z2));
        for (int y = ly; y <= hy; y++) {
            for (int z = lz; z <= hz; z++) {
                int base = idx(lx, y, z);
                for (int x = lx; x <= hx; x++) {
                    g[base++] |= (byte) flag;
                }
            }
        }
    }

    void unmark(int x, int y, int z, int flag) {
        if (in(x, y, z)) {
            g[idx(x, y, z)] &= (byte) ~flag;
        }
    }

    boolean has(int x, int y, int z, int mask) {
        return in(x, y, z) && (g[idx(x, y, z)] & mask) != 0;
    }

    /** True if any cell of the box carries a flag of {@code mask} (cells outside the grid count as free). */
    boolean any(int x1, int y1, int z1, int x2, int y2, int z2, int mask) {
        int lx = Math.max(MINX, Math.min(x1, x2)), hx = Math.min(MAXX, Math.max(x1, x2));
        int ly = Math.max(MINY, Math.min(y1, y2)), hy = Math.min(MAXY, Math.max(y1, y2));
        int lz = Math.max(MINZ, Math.min(z1, z2)), hz = Math.min(MAXZ, Math.max(z1, z2));
        for (int y = ly; y <= hy; y++) {
            for (int z = lz; z <= hz; z++) {
                int base = idx(lx, y, z);
                for (int x = lx; x <= hx; x++) {
                    if ((g[base++] & mask) != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    int count(int x1, int y1, int z1, int x2, int y2, int z2, int mask) {
        int n = 0;
        int lx = Math.max(MINX, Math.min(x1, x2)), hx = Math.min(MAXX, Math.max(x1, x2));
        int ly = Math.max(MINY, Math.min(y1, y2)), hy = Math.min(MAXY, Math.max(y1, y2));
        int lz = Math.max(MINZ, Math.min(z1, z2)), hz = Math.min(MAXZ, Math.max(z1, z2));
        for (int y = ly; y <= hy; y++) {
            for (int z = lz; z <= hz; z++) {
                int base = idx(lx, y, z);
                for (int x = lx; x <= hx; x++) {
                    if ((g[base++] & mask) != 0) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
