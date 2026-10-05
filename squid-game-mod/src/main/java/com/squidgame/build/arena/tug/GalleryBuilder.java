package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

/**
 * Guard / spectator galleries: the lower galleries along both long walls at deck level (y = 40), the upper catwalks at
 * y = 58 reached by four long stairs, the cross gantry over the gap and the control booth hanging at its centre.
 */
final class GalleryBuilder {
    private GalleryBuilder() {
    }

    /** Joint / bracket positions on the west half (mirrored east): x = -5, -15 ... -55. */
    static final int[] JOINTS = {-5, -15, -25, -35, -45, -55};

    static void build(BuildContext c) {
        lowerGalleries(c);
        stairs(c);
        upperCatwalks(c);
        gantry(c);
        booth(c);
    }

    /** Gallery frame: sgn = -1 north wall, +1 south wall; zz = distance index from the hall centre (30 = rail row, 35 = wall side). */
    private static int Z(int sgn, int zz) {
        return sgn * zz;
    }

    // ------------------------------------------------------------------ lower galleries (y = 40)

    static boolean galleryLight(int x) {
        // west half: x = -58, -54 ... -2 (mirrored: 1, 5 ... 57)
        return x >= -58 && x <= -2 && Math.floorMod(x + 58, 4) == 0;
    }

    private static void lowerGalleries(BuildContext c) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            final int sg = sgn;
            int zLo = Math.min(Z(sg, 30), Z(sg, 35)), zHi = Math.max(Z(sg, 30), Z(sg, 35));   // floor incl. the edge-beam row
            int pLo = Math.min(Z(sg, 31), Z(sg, 35)), pHi = Math.max(Z(sg, 31), Z(sg, 35));   // walking part
            // floor, west half mirrored: edge beam, hazard stripe along the rail, flush lights, 5x5 panel joints
            Sym.pattern(c, -60, Geo.DECK, zLo, -1, Geo.DECK, zHi, (x, y, z) -> {
                int zz = Math.abs(z);
                if (zz == 30) {
                    return Pal.STEEL;
                }
                if (zz == 33 && galleryLight(x)) {
                    return Pal.PANEL;
                }
                if (zz == 31) {
                    return Pal.hazard(x + 100);
                }
                if (Math.floorMod(x, 5) == 0 || zz == 35) {
                    return Pal.CONC_D;
                }
                double h = Pal.hash(x, z, 31);
                return h < 0.10 ? Pal.STONE : h < 0.16 ? Pal.ANDESITE : Pal.CONC_L;
            });
            Sym.fill(c, -60, 41, Z(sg, 30), -1, 42, Z(sg, 30), Pal.BARS);                    // rail row
            Sym.fill(c, -60, 39, pLo, -1, 39, pHi, Pal.BLACK);                              // underside plate
            Sym.fill(c, -60, 37, Z(sg, 30), -1, 39, Z(sg, 30), Pal.STEEL);                  // edge beam
            Sym.fill(c, -60, 36, Z(sg, 30), -1, 36, Z(sg, 30), Pal.IRON);                   // bottom flange
            // brackets at every joint: diagonal knee braces from the wall up to the edge beam
            for (int xj : JOINTS) {
                for (int dx = 0; dx < 2; dx++) {
                    Sym.line(c, xj + dx, 27, Z(sg, 35), xj + dx, 36, Z(sg, 31), Pal.STEEL);
                }
                Sym.fill(c, xj, 38, pLo, xj + 1, 38, pHi, Pal.STEEL);
            }
            // columns every 20 blocks down to the hall floor, collared and hazard-footed
            for (int xc : new int[]{-55, -35, -15}) {
                int cLo = Math.min(Z(sg, 29), Z(sg, 30)), cHi = Math.max(Z(sg, 29), Z(sg, 30));
                Sym.fill(c, xc, 1, cLo, xc + 1, 38, cHi, Pal.STEEL);
                for (int y : new int[]{37, 24, 12, 4}) {
                    Sym.fill(c, xc, y, cLo, xc + 1, y, cHi, Pal.IRON);
                }
                for (int y = 1; y <= 3; y++) {
                    Sym.pattern(c, xc, y, cLo, xc + 1, y, cHi, (x, yy, z) -> Pal.hazard(x + yy + Math.abs(z)));
                }
            }
        }
    }

    // ------------------------------------------------------------------ stairs: plateau -> catwalk (18 steps along the long walls)

    static final int ST_X0 = -72;          // first step block x (west frame), rises toward +x
    static final int ST_STEPS = 18;

    private static void stairs(BuildContext c) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            for (int k = 0; k < ST_STEPS; k++) {
                int x = ST_X0 + k;
                int y = Geo.DECK + 1 + k;
                int zA = sgn < 0 ? -35 : 33, zB = sgn < 0 ? -33 : 35;
                Sym.fill(c, x, y, zA, x, y, zB, Pal.stairs("polished_deepslate", "east", false));
                Sym.fill(c, x, y - 1, zA, x, y - 1, zB, Pal.STEEL);
                // handrail on the open side
                int zr = sgn < 0 ? -32 : 32;
                Sym.set(c, x, y + 1, zr, Pal.BARS);
                Sym.set(c, x, y + 2, zr, Pal.BARS);
                // stringer on the open side; every third one is a lantern so the flight is lit
                Sym.set(c, x, y - 1, zr, k % 3 == 1 ? Pal.SEA : Pal.STEEL);
            }
        }
    }

    // ------------------------------------------------------------------ upper catwalks (y = 58)

    static final int CAT_X0 = ST_X0 + ST_STEPS;      // -54: first catwalk block in the west frame

    static boolean catLight(int x) {
        return x >= -54 && x <= -2 && Math.floorMod(x + 54, 4) == 0;
    }

    private static void upperCatwalks(BuildContext c) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            final int sg = sgn;
            int zWall = Z(sg, 35), zRail = Z(sg, 31);
            int zLo = Math.min(zWall, zRail), zHi = Math.max(zWall, zRail);
            Sym.pattern(c, CAT_X0, Geo.CAT, zLo, -1, Geo.CAT, zHi, (x, y, z) -> {
                int zz = Math.abs(z);
                if (zz == 31) {
                    return Pal.STEEL;
                }
                if (zz == 33 && catLight(x)) {
                    return Pal.PANEL;
                }
                if (zz == 32) {
                    return Pal.hazard(x + 100);
                }
                return Math.floorMod(x, 3) == 0 ? Pal.DS_TILES : Pal.DS_POLISHED;
            });
            Sym.fill(c, CAT_X0, Geo.CAT + 1, zRail, -1, Geo.CAT + 2, zRail, Pal.BARS);
            // underside beam and wall brackets at every joint
            Sym.fill(c, CAT_X0, Geo.CAT - 2, zRail, -1, Geo.CAT - 1, zRail, Pal.STEEL);
            for (int xj : JOINTS) {
                if (xj < CAT_X0) {
                    continue;
                }
                for (int dx = 0; dx < 2; dx++) {
                    Sym.line(c, xj + dx, Geo.CAT - 10, zWall, xj + dx, Geo.CAT - 2, zRail, Pal.STEEL);
                }
                // hanger chains up to the roof truss
                Sym.fill(c, xj, Geo.CAT + 1, zRail, xj, Geo.CEIL - 9, zRail, Pal.chain());
            }
        }
    }

    // ------------------------------------------------------------------ cross gantry over the gap (x -3..2, y = 58)

    private static void gantry(BuildContext c) {
        // open the catwalk rails where the gantry joins
        c.clear(-3, Geo.CAT + 1, -Geo.HZ + 4, 2, Geo.CAT + 2, -Geo.HZ + 4);
        c.clear(-3, Geo.CAT + 1, Geo.HZ - 4, 2, Geo.CAT + 2, Geo.HZ - 4);
        // floor from wall catwalk to wall catwalk
        c.pattern(-3, Geo.CAT, -Geo.HZ, 2, Geo.CAT, Geo.HZ, (x, y, z) -> {
            if (Math.abs(z) % 4 == 2 && (x == -1 || x == 0)) {
                return Pal.PANEL;
            }
            return Math.abs(z) % 3 == 0 ? Pal.DS_TILES : Pal.DS_POLISHED;
        });
        // rails on both long sides, except inside the booth footprint
        for (int z = -Geo.HZ; z <= Geo.HZ; z++) {
            if (Math.abs(z) <= 7 || Math.abs(z) >= 31) {
                continue;                                  // booth footprint / catwalk strips stay open
            }
            for (int x : new int[]{-3, 2}) {
                c.set(x, Geo.CAT + 1, z, Pal.BARS);
                c.set(x, Geo.CAT + 2, z, Pal.BARS);
            }
        }
        // under-beams (two girders) and cross ribs
        c.fill(-3, Geo.CAT - 2, -Geo.HZ, -3, Geo.CAT - 1, Geo.HZ, Pal.STEEL);
        c.fill(2, Geo.CAT - 2, -Geo.HZ, 2, Geo.CAT - 1, Geo.HZ, Pal.STEEL);
        for (int z = -Geo.HZ; z <= Geo.HZ; z += 4) {
            c.fill(-3, Geo.CAT - 1, z, 2, Geo.CAT - 1, z, Pal.STEEL);
        }
        // hangers from the roof truss (truss pair x = -1..0): cross beam under it, chains down to the rail posts
        for (int z : new int[]{-30, -22, -14, 14, 22, 30}) {
            c.fill(-3, Geo.CEIL - 10, z, 2, Geo.CEIL - 10, z, Pal.STEEL);
            c.fill(-3, Geo.CAT + 3, z, -3, Geo.CEIL - 11, z, Pal.chain());
            c.fill(2, Geo.CAT + 3, z, 2, Geo.CEIL - 11, z, Pal.chain());
            c.fill(-3, Geo.CAT + 1, z, -3, Geo.CAT + 2, z, Pal.IRON);
            c.fill(2, Geo.CAT + 1, z, 2, Geo.CAT + 2, z, Pal.IRON);
        }
    }

    // ------------------------------------------------------------------ control booth on the gantry

    private static void booth(BuildContext c) {
        int x0 = -8, x1 = 7, z0 = -7, z1 = 7;
        int fy = Geo.CAT;                    // floor y
        // floor slab and roof
        c.fill(x0, fy, z0, x1, fy, z1, Pal.DS_POLISHED);
        c.fill(x0, fy - 1, z0, x1, fy - 1, z1, Pal.STEEL);
        c.fill(x0 - 1, fy + 7, z0 - 1, x1 + 1, fy + 7, z1 + 1, Pal.STEEL);
        c.fill(x0, fy + 8, z0, x1, fy + 8, z1, Pal.BLACK);
        // carve interior
        c.clear(x0 + 1, fy + 1, z0 + 1, x1 - 1, fy + 6, z1 - 1);
        // frame posts and glazing (light gray tinted glass), steel sill and header
        for (int y = fy + 1; y <= fy + 6; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z : new int[]{z0, z1}) {
                    c.set(x, y, z, glazing(y - fy, x, x0, x1));
                }
            }
            for (int z = z0; z <= z1; z++) {
                for (int x : new int[]{x0, x1}) {
                    c.set(x, y, z, glazing(y - fy, z, z0, z1));
                }
            }
        }
        // doorways on the gantry axis (north and south walls): 4 wide, 4 high, framed
        c.clear(-2, fy + 1, z0, 1, fy + 4, z0);
        c.clear(-2, fy + 1, z1, 1, fy + 4, z1);
        for (int z : new int[]{z0, z1}) {
            c.fill(-3, fy + 1, z, -3, fy + 5, z, Pal.IRON);
            c.fill(2, fy + 1, z, 2, fy + 5, z, Pal.IRON);
            c.fill(-2, fy + 5, z, 1, fy + 5, z, Pal.IRON);
        }
        // ceiling lights
        for (int x = -6; x <= 5; x += 4) {
            for (int z = -5; z <= 5; z += 4) {
                c.fill(x, fy + 6, z, x + 1, fy + 6, z + 1, Pal.PANEL);
            }
        }
        // flush floor lights keep the booth above level 10
        for (int x : new int[]{-5, 0, 4}) {
            for (int z : new int[]{-4, 0, 4}) {
                c.set(x, fy, z, Pal.SEA);
            }
        }
        // consoles against the east and west windows with monitors facing the room
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? x0 + 1 : x1 - 1;
            String face = side == 0 ? "east" : "west";
            for (int z = -5; z <= 5; z++) {
                c.set(x, fy + 1, z, "minecraft:polished_blackstone_slab[type=bottom]");
                if (z % 2 == 0) {
                    c.set(x, fy + 2, z, "squidgame:monitor[facing=" + face + "]");
                }
            }
        }
        // hangers: four chains to the roof truss and cross struts
        for (int x : new int[]{x0, x1}) {
            for (int z : new int[]{z0, z1}) {
                c.fill(x, fy + 9, z, x, Geo.CEIL - 11, z, Pal.chain());
            }
        }
        c.fill(x0, Geo.CEIL - 10, z0, x1, Geo.CEIL - 10, z0, Pal.STEEL);
        c.fill(x0, Geo.CEIL - 10, z1, x1, Geo.CEIL - 10, z1, Pal.STEEL);
    }

    private static String glazing(int h, int pos, int lo, int hi) {
        boolean post = pos == lo || pos == hi || (pos - lo) % 5 == 0;
        if (h == 1) {
            return Pal.STEEL;                                // sill
        }
        if (h == 6) {
            return Pal.STEEL;                                // header
        }
        return post ? Pal.STEEL : "minecraft:light_gray_stained_glass";
    }
}
