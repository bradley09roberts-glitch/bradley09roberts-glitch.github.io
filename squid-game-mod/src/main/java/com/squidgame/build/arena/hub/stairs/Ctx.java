package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.BuildContext;
import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared build state: the DSL context, the planning grid and the recorded centre line of the main route.
 * In probe mode writes are not performed: flagged writes only test whether their cells are still free ({@link #conflict}).
 */
final class Ctx {
    static final int X0 = -45, X1 = 45, Z0 = -170, Z1 = -82, Y0 = 0, Y1 = 72;

    final BuildContext c;
    final Occ occ = new Occ();
    final Rng rng;
    /** centre line of the main route in walking order: {x, standLevel, z} per cell */
    final List<int[]> line = new ArrayList<>();
    /** landings of the main route: {x0, z0, x1, z1, level} */
    final List<int[]> landings = new ArrayList<>();
    /** every walking floor rectangle {x0, z0, x1, z1, level} (main route and decor) that gets rim railings */
    final List<int[]> walkRects = new ArrayList<>();
    /** all landings (main and decor) {x0, z0, x1, z1, level} for pillars and lights */
    final List<int[]> allLandings = new ArrayList<>();

    /** every flight built {dirOrdinal, cx, cz, H, rise, lanes, lw, tread, side, accent, main} for portals */
    final List<int[]> flights = new ArrayList<>();

    boolean probe;
    boolean conflict;
    /** head-room reserved above walkways (6 for the main route, less for decor so it can pack tighter) */
    int head = 6;

    Ctx(BuildContext c) {
        this.c = c;
        // private stream (independent of what the other hub parts drew from the shared generator)
        this.rng = c.rng().fork(0x57A1F5L);
    }

    private boolean blocked(int x, int y, int z) {
        return !inHall(x, y, z) || occ.has(x, y, z, Occ.SOLID | Occ.CLEAR | Occ.DECOR | Occ.KEEP) || c.isSolid(x, y, z);
    }

    private boolean boxBlocked(int x1, int y1, int z1, int x2, int y2, int z2) {
        int lx = Math.min(x1, x2), hx = Math.max(x1, x2), ly = Math.min(y1, y2), hy = Math.max(y1, y2);
        int lz = Math.min(z1, z2), hz = Math.max(z1, z2);
        if (lx < X0 || hx > X1 || lz < Z0 || hz > Z1 || ly < Y0 || hy > Y1) {
            return true;
        }
        if (occ.any(lx, ly, lz, hx, hy, hz, Occ.SOLID | Occ.CLEAR | Occ.DECOR | Occ.KEEP)) {
            return true;
        }
        for (int y = ly; y <= hy; y++) {
            for (int z = lz; z <= hz; z++) {
                for (int x = lx; x <= hx; x++) {
                    if (c.isSolid(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    void set(int x, int y, int z, String s) {
        if (!probe) {
            c.set(x, y, z, s);
        }
    }

    void set(int x, int y, int z, String s, int flag) {
        if (probe) {
            if ((flag & (Occ.SOLID | Occ.DECOR)) != 0 && blocked(x, y, z)) {
                conflict = true;
            }
            return;
        }
        c.set(x, y, z, s);
        occ.mark(x, y, z, x, y, z, flag);
    }

    /** Sets only where nothing solid has been written yet. */
    boolean setFree(int x, int y, int z, String s, int flag) {
        if (blocked(x, y, z)) {
            return false;
        }
        if (!probe) {
            c.set(x, y, z, s);
            occ.mark(x, y, z, x, y, z, flag);
        }
        return true;
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String s) {
        if (!probe) {
            c.fill(x1, y1, z1, x2, y2, z2, s);
        }
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String s, int flag) {
        if (probe) {
            if ((flag & (Occ.SOLID | Occ.DECOR)) != 0 && boxBlocked(x1, y1, z1, x2, y2, z2)) {
                conflict = true;
            }
            return;
        }
        c.fill(x1, y1, z1, x2, y2, z2, s);
        occ.mark(x1, y1, z1, x2, y2, z2, flag);
    }

    /** Reserves head-room: in probe mode tests it against structures. */
    void mark(int x1, int y1, int z1, int x2, int y2, int z2, int flag) {
        if (probe) {
            int lx = Math.min(x1, x2), hx = Math.max(x1, x2), ly = Math.min(y1, y2), hy = Math.max(y1, y2);
            int lz = Math.min(z1, z2), hz = Math.max(z1, z2);
            if (lx < X0 || hx > X1 || lz < Z0 || hz > Z1 || hy > Y1) {
                conflict = true;
            } else if (occ.any(lx, ly, lz, hx, hy, hz, Occ.SOLID | Occ.DECOR | Occ.KEEP)) {
                conflict = true;
            } else {
                for (int y = ly; y <= hy && !conflict; y++) {
                    for (int z = lz; z <= hz && !conflict; z++) {
                        for (int x = lx; x <= hx; x++) {
                            if (c.isSolid(x, y, z)) {
                                conflict = true;
                                break;
                            }
                        }
                    }
                }
            }
            return;
        }
        occ.mark(x1, y1, z1, x2, y2, z2, flag);
    }

    /** Fills a free-space fill for decor: only cells that are free (no solid, not reserved). */
    int fillFree(int x1, int y1, int z1, int x2, int y2, int z2, String s, int flag) {
        int n = 0;
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    if (setFree(x, y, z, s, flag)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    String get(int x, int y, int z) {
        return c.get(x, y, z);
    }

    boolean solid(int x, int y, int z) {
        return c.isSolid(x, y, z);
    }

    static boolean inHall(int x, int y, int z) {
        return x >= X0 && x <= X1 && z >= Z0 && z <= Z1 && y >= Y0 && y <= Y1;
    }
}
