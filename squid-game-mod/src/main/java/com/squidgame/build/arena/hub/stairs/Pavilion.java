package com.squidgame.build.arena.hub.stairs;

/**
 * Arcaded support under a platform: a hollow tower with 2-thick walls, a floor slab every 12 blocks and round-headed
 * arch openings through every wall and every storey (see-through, "repeating arches"). The ground storey is open to
 * the hall. Used for the main landings and a share of the decor.
 */
final class Pavilion {
    private Pavilion() {
    }

    /** Footprint check without writing: is the volume free? */
    static boolean fits(Ctx k, int x0, int z0, int x1, int z1, int H) {
        int top = H - 4;
        if (top < 6) {
            return false;
        }
        k.probe = true;
        k.conflict = false;
        try {
            k.fill(x0, 0, z0, x1, top, z1, Pal.AIR, Occ.SOLID);
        } finally {
            k.probe = false;
        }
        boolean ok = !k.conflict;
        k.conflict = false;
        return ok;
    }

    static void build(Ctx k, int x0, int z0, int x1, int z1, int H, int wallCol, int ringCol, int bandCol) {
        int top = H - 4;
        // solid ring of 2-thick walls with colour bands every 6 rows
        for (int y = 0; y <= top; y++) {
            boolean band = y % 12 == 11 || y % 12 == 10 && y > 10 || y == top;
            String s = Pal.block(band ? bandCol : wallCol);
            k.fill(x0, y, z0, x1, y, z0 + 1, s, Occ.SOLID);
            k.fill(x0, y, z1 - 1, x1, y, z1, s, Occ.SOLID);
            k.fill(x0, y, z0 + 2, x0 + 1, y, z1 - 2, s, Occ.SOLID);
            k.fill(x1 - 1, y, z0 + 2, x1, y, z1 - 2, s, Occ.SOLID);
        }
        // interior: hollow; floor slabs every 12 blocks (2 thick)
        int ix0 = x0 + 2, ix1 = x1 - 2, iz0 = z0 + 2, iz1 = z1 - 2;
        if (ix1 >= ix0 && iz1 >= iz0) {
            k.fill(ix0, 0, iz0, ix1, top, iz1, Pal.AIR, Occ.SOLID);
            for (int s = 1; 12 * s - 1 <= top; s++) {
                k.fill(ix0, 12 * s - 2, iz0, ix1, 12 * s - 1, iz1, Pal.block(Pal.CREAM), Occ.SOLID);
            }
            // a warm light in the underside of each slab and of the roof
            for (int s = 0; 12 * s + 10 <= top + 1; s++) {
                int y = Math.min(12 * s + 10, top);
                if (s > 0 && y > top) {
                    break;
                }
                int cx = (ix0 + ix1) / 2, cz = (iz0 + iz1) / 2;
                k.set(cx, y, cz, Pal.LIGHT_WARM);
                if (ix1 - ix0 >= 4) {
                    k.set(ix0 + 1, y, iz0 + 1, Pal.LIGHT_WARM);
                    k.set(ix1 - 1, y, iz1 - 1, Pal.LIGHT_WARM);
                }
                if (iz1 - iz0 >= 4) {
                    k.set(ix0 + 1, y, iz1 - 1, Pal.LIGHT_WARM);
                    k.set(ix1 - 1, y, iz0 + 1, Pal.LIGHT_WARM);
                }
            }
        }
        // arch openings in every storey
        for (int s = 0; 12 * s <= top - 6; s++) {
            int y0 = 12 * s;
            int avail = (12 * s + 10 <= top ? 10 : top - y0 + 1);
            int openH = Math.min(9, avail - 1);
            if (openH < 5) {
                continue;
            }
            int ring = s % 2 == 0 ? ringCol : Pal.wheel(ringCol + 2);
            sideOpenings(k, true, z0, z0 + 1, x0, x1, y0, openH, ring, wallCol);
            sideOpenings(k, true, z1 - 1, z1, x0, x1, y0, openH, ring, wallCol);
            sideOpenings(k, false, x0, x0 + 1, z0, z1, y0, openH, ring, wallCol);
            sideOpenings(k, false, x1 - 1, x1, z0, z1, y0, openH, ring, wallCol);
        }
    }

    /** Openings along one side; {@code alongX}: the wall runs along x at z in [f0, f1]; otherwise along z at x in [f0, f1]. */
    private static void sideOpenings(Ctx k, boolean alongX, int f0, int f1, int a0, int a1, int y0, int openH, int ring, int wallCol) {
        int len = a1 - a0 + 1;
        int aw = len >= 15 ? 7 : (len >= 11 ? 5 : 3);
        aw = Math.min(aw, openH - 2 >= 5 ? 7 : 5);
        int n = Math.max(1, (len - 2) / (aw + 3));
        for (int i = 0; i < n; i++) {
            int centre = a0 + (int) Math.round((i + 0.5) * len / n - 0.5);
            opening(k, alongX, f0, f1, centre, y0, aw, openH, ring);
        }
    }

    private static void opening(Ctx k, boolean alongX, int f0, int f1, int ctr, int y0, int aw, int openH, int ring) {
        int hw = (aw - 1) / 2;
        double r = hw + 0.5;
        int straight = openH - 1 - hw;
        for (int y = y0; y <= y0 + openH; y++) {
            for (int a = ctr - hw - 1; a <= ctr + hw + 1; a++) {
                int da = a - ctr;
                boolean inside = insideArch(da, y - y0, hw, straight, r, openH);
                boolean rim = false;
                if (!inside) {
                    rim = insideArch(da + 1, y - y0, hw, straight, r, openH) || insideArch(da - 1, y - y0, hw, straight, r, openH)
                            || insideArch(da, y - y0 + 1, hw, straight, r, openH) || insideArch(da, y - y0 - 1, hw, straight, r, openH);
                }
                if (inside) {
                    if (alongX) {
                        k.fill(a, y, f0, a, y, f1, Pal.AIR);
                    } else {
                        k.fill(f0, y, a, f1, y, a, Pal.AIR);
                    }
                } else if (rim && y >= y0) {
                    for (int f = f0; f <= f1; f++) {
                        // rim only on the two faces of the wall
                        if (f == f0 || f == f1) {
                            if (alongX) {
                                k.set(a, y, f, Pal.block(ring));
                            } else {
                                k.set(f, y, a, Pal.block(ring));
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean insideArch(int da, int dy, int hw, int straight, double r, int openH) {
        if (dy < 0 || dy >= openH) {
            return false;
        }
        if (dy <= straight) {
            return Math.abs(da) <= hw;
        }
        double ddy = dy - straight;
        return da * da + ddy * ddy <= r * r - 0.25;
    }
}
