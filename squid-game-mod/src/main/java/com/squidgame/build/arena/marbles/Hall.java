package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

/**
 * Dresses the standard waiting-room prefab as the village hall: plaster and timber cladding on its outer walls, a pillared
 * arcade with a tiled lean-to roof along the front (the gate opens onto the square under it), tile skirts on the other
 * sides and a roof terrace with parapet, corner kiosks and a water tank where armed guards stand.
 *
 * <p>Hall frame = arena-local: gate wall plane z = HALL_ZG (-10), room towards -Z, x in [-19,19], terrace top y = 12.
 */
final class Hall {
    private Hall() {
    }

    static final int ZG = Layout.HALL_ZG;
    static final int HW = Layout.HALL_W / 2;               // 18 -> shell x +-19
    static final int ZB = ZG - Layout.HALL_D - 1;          // back wall plane
    static final int TERRACE_Y = 12;                       // standing height on the terrace

    static void build(BuildContext c) {
        int xl = -HW - 1, xr = HW + 1;                    // shell x range
        sides(c, xl, xr);
        back(c, xl, xr);
        front(c, xl, xr);
        terrace(c, xl, xr);
        floorLights(c, xl, xr);
    }

    /**
     * The prefab only lights the room from its 9 high ceiling (about level 5 on the floor), so glowing pink floor tiles are
     * inlaid in a diamond lattice (every cell within 4 blocks of a panel -> level >= 10 everywhere in the room).
     */
    private static void floorLights(BuildContext c, int xl, int xr) {
        int x0 = xl + 1, x1 = xr - 1, z0 = ZB + 1, z1 = ZG - 1;
        java.util.List<int[]> best = null;
        for (int ox = 0; ox < 41; ox++) {
            java.util.List<int[]> pts = new java.util.ArrayList<>();
            for (int i = -12; i <= 12; i++) {
                for (int j = -12; j <= 12; j++) {
                    int x = (ox % 5) + 4 * i + 5 * j + x0 - 2, z = (ox / 5) + 5 * i - 4 * j + z0 - 2;
                    if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
                        pts.add(new int[]{x, z});
                    }
                }
            }
            // greedy patch for uncovered cells
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean cov = false;
                    for (int[] p : pts) {
                        if (Math.abs(p[0] - x) + Math.abs(p[1] - z) <= 4) {
                            cov = true;
                            break;
                        }
                    }
                    if (!cov) {
                        pts.add(new int[]{Math.max(x0, Math.min(x1, x + (x < 0 ? 2 : -2))), Math.max(z0, Math.min(z1, z + 2))});
                    }
                }
            }
            if (best == null || pts.size() < best.size()) {
                best = pts;
            }
        }
        for (int[] p : best) {
            c.set(p[0], 0, p[1], "minecraft:pearlescent_froglight[axis=y]");
        }
    }

    // ------------------------------------------------------------------ cladding

    private static String[] plaster() {
        return new String[]{Mat.PLASTER, Mat.CALCITE, Mat.DIORITE};
    }

    private static final double[] PW = {62, 30, 8};

    /** west / east walls: cladding at x = xl-1 / xr+1 (y 1..10), posts every 4, lit lattice windows, tile skirt. */
    private static void sides(BuildContext c, int xl, int xr) {
        for (int side = -1; side <= 1; side += 2) {
            int x = side < 0 ? xl - 1 : xr + 1;
            int out = side;               // direction away from the building
            c.fill(x, 1, ZB - 1, x, 1, ZG + 1, Mat.SB);
            c.noise(x, 2, ZB - 1, x, 8, ZG + 1, plaster(), PW);
            c.fill(x, 9, ZB - 1, x, 9, ZG + 1, Mat.log(Mat.LOG_S, "z"));
            c.fill(x, 10, ZB - 1, x, 10, ZG + 1, Mat.PLANKS);
            for (int z = ZB - 1; z <= ZG + 1; z += 4) {
                c.fill(x, 2, z, x, 8, z, Mat.log(Mat.LOG_S, "y"));
            }
            // lit windows between the posts
            for (int z = ZB + 2; z <= ZG - 3; z += 4) {
                c.fill(x, 4, z, x, 5, z + 1, Mat.GLOW);
                c.fill(x, 3, z, x, 3, z + 1, Mat.SB);
                c.fill(x, 6, z, x, 6, z + 1, Mat.log(Mat.LOG_S, "z"));
            }
            // skirt: three rows rising towards the building
            String faceIn = side < 0 ? "east" : "west";
            c.fill(x + 2 * out, 9, ZB - 3, x + 2 * out, 9, ZG + 3, Mat.stair(Mat.TILE_ST, faceIn));
            c.fill(x + out, 10, ZB - 2, x + out, 10, ZG + 2, Mat.stair(Mat.TILE_ST, faceIn));
            c.fill(x, 11, ZB - 1, x, 11, ZG + 1, Mat.stair(Mat.TILE_ST, faceIn));
            for (int z = ZB - 3; z <= ZG + 3; z += 8) {
                c.fill(x + 2 * out, 1, z, x + 2 * out, 7, z, Mat.log(Mat.LOG, "y"));
            }
            // underside rafters
            c.fill(x + 2 * out, 8, ZB - 3, x + 2 * out, 8, ZG + 3, Mat.slabT("minecraft:dark_oak_slab"));
            c.fill(x + out, 9, ZB - 2, x + out, 9, ZG + 2, Mat.slabT("minecraft:dark_oak_slab"));
            // lantern brackets under the skirt
            for (int z = ZB + 4; z <= ZG - 4; z += 8) {
                c.set(x + out, 8, z, Mat.lantern(true));
            }
        }
    }

    private static void back(BuildContext c, int xl, int xr) {
        int z = ZB - 1;
        c.fill(xl - 1, 1, z, xr + 1, 1, z, Mat.SB);
        c.noise(xl - 1, 2, z, xr + 1, 8, z, plaster(), PW);
        c.fill(xl - 1, 9, z, xr + 1, 9, z, Mat.log(Mat.LOG_S, "x"));
        c.fill(xl - 1, 10, z, xr + 1, 10, z, Mat.PLANKS);
        for (int x = xl - 1; x <= xr + 1; x += 4) {
            c.fill(x, 2, z, x, 8, z, Mat.log(Mat.LOG_S, "y"));
        }
        for (int x = xl + 2; x <= xr - 3; x += 4) {
            c.fill(x, 4, z, x + 1, 5, z, Mat.GLOW);
            c.fill(x, 3, z, x + 1, 3, z, Mat.SB);
            c.fill(x, 6, z, x + 1, 6, z, Mat.log(Mat.LOG_S, "x"));
        }
        c.fill(xl - 3, 9, z - 2, xr + 3, 9, z - 2, Mat.stair(Mat.TILE_ST, "south"));
        c.fill(xl - 2, 10, z - 1, xr + 2, 10, z - 1, Mat.stair(Mat.TILE_ST, "south"));
        c.fill(xl - 1, 11, z, xr + 1, 11, z, Mat.stair(Mat.TILE_ST, "south"));
        c.fill(xl - 3, 8, z - 2, xr + 3, 8, z - 2, Mat.slabT("minecraft:dark_oak_slab"));
        c.fill(xl - 2, 9, z - 1, xr + 2, 9, z - 1, Mat.slabT("minecraft:dark_oak_slab"));
        // a back door / loading hatch with a lantern (decorative, closed)
        c.set(0, 2, z, Mat.door("minecraft:dark_oak_door", "north", false, "left", false));
        c.set(0, 3, z, Mat.door("minecraft:dark_oak_door", "north", true, "left", false));
        c.set(-2, 3, z - 1, Mat.lantern(false));
        c.set(2, 3, z - 1, Mat.lantern(false));
        c.set(-2, 2, z - 1, Mat.SB_WALL);
        c.set(2, 2, z - 1, Mat.SB_WALL);
    }

    // ------------------------------------------------------------------ front arcade

    private static void front(BuildContext c, int xl, int xr) {
        int zw = ZG;                      // wall plane
        // cover the white wall above and beside the glazing with plaster + timber (cladding on the outer face, z = zw+1)
        int zc = zw + 1;
        c.noise(xl, 8, zc, xr, 9, zc, plaster(), PW);
        c.noise(xl, 3, zc, xl + 3, 9, zc, plaster(), PW);
        c.noise(xr - 3, 3, zc, xr, 9, zc, plaster(), PW);
        c.noise(xl, 1, zc, xl + 3, 2, zc, plaster(), PW);
        c.noise(xr - 3, 1, zc, xr, 2, zc, plaster(), PW);
        c.fill(xl, 8, zc, xr, 8, zc, Mat.log(Mat.LOG_S, "x"));
        for (int x : new int[]{xl, xl + 4, xr - 4, xr}) {
            c.fill(x, 3, zc, x, 9, zc, Mat.log(Mat.LOG_S, "y"));
        }
        // pillars + beams
        int pz = zw + 3;
        for (int x : new int[]{-17, -13, -9, -5, 5, 9, 13, 17}) {
            c.fill(x, 1, pz, x, 1, pz, Mat.SB);
            c.fill(x, 2, pz, x, 7, pz, Mat.log(Mat.LOG, "y"));
        }
        for (int x : new int[]{xl, xr}) {
            c.fill(x, 1, pz, x, 1, pz, Mat.SB);
            c.fill(x, 2, pz, x, 7, pz, Mat.log(Mat.LOG, "y"));
        }
        c.fill(xl, 8, pz, xr, 8, pz, Mat.log(Mat.LOG_S, "x"));
        c.fill(xl, 8, zw + 2, xr, 8, zw + 2, Mat.slabT("minecraft:dark_oak_slab"));
        // lean-to roof: three rows rising towards the wall (north)
        c.fill(xl - 1, 9, zw + 4, xr + 1, 9, zw + 4, Mat.stair(Mat.TILE_ST, "north"));
        c.fill(xl - 1, 9, zw + 3, xr + 1, 9, zw + 3, Mat.stair(Mat.TILE_ST, "north"));
        c.fill(xl - 1, 10, zw + 2, xr + 1, 10, zw + 2, Mat.stair(Mat.TILE_ST, "north"));
        c.fill(xl - 1, 11, zw + 1, xr + 1, 11, zw + 1, Mat.stair(Mat.TILE_ST, "north"));
        c.fill(xl - 1, 9, zw + 2, xr + 1, 9, zw + 2, Mat.TILE);
        c.fill(xl - 1, 10, zw + 1, xr + 1, 10, zw + 1, Mat.TILE);
        c.fill(xl - 1, 9, zw + 1, xr + 1, 9, zw + 1, Mat.TILE);
        // upturned eave tips at the two outer corners
        for (int x : new int[]{xl - 1, xr + 1}) {
            c.set(x, 10, zw + 4, Mat.stair(Mat.TILE_ST, "north"));
        }
        // hanging lanterns at the bay centres, banners over the gate
        for (int x : new int[]{-19, -15, -11, -7, 0, 7, 11, 15, 19}) {
            c.set(x, 7, pz, Mat.lantern(true));
            c.set(x, 7, zw + 1, Mat.lantern(true));
        }
        // wooden steps are not needed: the arcade floor is level with the square. Hall title over the gate
        c.text(0.5, 8.6, zc + 1.02, "MARBLES", "#FFD84A", 3.2f, 0f, false);
        // paved arcade floor
        c.pattern(xl - 1, 0, zw + 1, xr + 1, 0, zw + 3, (x, y, z) -> U.rand(x, z, 9) < 0.35 ? Mat.P_ANDESITE : Mat.SB);
    }

    // ------------------------------------------------------------------ roof terrace

    private static void terrace(BuildContext c, int xl, int xr) {
        int zf = ZG, zb = ZB;
        // paved top (replaces the prefab's white concrete top layer)
        c.pattern(xl, 11, zb, xr, 11, zf, (x, y, z) -> {
            double r = U.rand(x, z, 411);
            return r < 0.55 ? Mat.SB : r < 0.75 ? Mat.P_ANDESITE : r < 0.9 ? Mat.COBBLE : Mat.SB_MOSS;
        });
        // parapet (stone-brick wall) around, coping lanterns every 6 blocks
        c.fill(xl, 12, zb, xr, 12, zb, Mat.SB_WALL);
        c.fill(xl, 12, zf, xr, 12, zf, Mat.SB_WALL);
        c.fill(xl, 12, zb, xl, 12, zf, Mat.SB_WALL);
        c.fill(xr, 12, zb, xr, 12, zf, Mat.SB_WALL);
        for (int x = xl + 3; x <= xr - 3; x += 6) {
            c.set(x, 13, zf, Mat.lantern(false));
            c.set(x, 13, zb, Mat.lantern(false));
        }
        for (int z = zb + 4; z <= zf - 4; z += 6) {
            c.set(xl, 13, z, Mat.lantern(false));
            c.set(xr, 13, z, Mat.lantern(false));
        }
        // corner kiosks (5x5 posts, hip tile roof) - guards stand under them
        int[][] kiosks = {{xl + 1, zb + 1}, {xr - 5, zb + 1}, {xl + 1, zf - 5}, {xr - 5, zf - 5}};
        for (int[] k : kiosks) {
            kiosk(c, k[0], k[1]);
        }
        // water tank + stand and an antenna in the middle of the terrace
        House.tank(c, -10, 12, -22);
        House.tank(c, 8, 12, -24);
        c.fill(0, 12, -22, 0, 18, -22, Mat.log(Mat.LOG_S, "y"));
        c.set(0, 19, -22, "minecraft:lightning_rod[facing=up]");
        // laundry line on the terrace
        c.fill(-3, 12, -17, -3, 14, -17, Mat.FENCE);
        c.fill(5, 12, -17, 5, 14, -17, Mat.FENCE);
        Props.laundryX(c, -3, 5, 14, -17, new U.Rnd(99));
        // roof hatch
        c.fill(-2, 12, -28, 2, 12, -28, Mat.SB_WALL);
    }

    private static void kiosk(BuildContext c, int x0, int z0) {
        for (int dx = 0; dx <= 4; dx += 4) {
            for (int dz = 0; dz <= 4; dz += 4) {
                c.fill(x0 + dx, 12, z0 + dz, x0 + dx, 15, z0 + dz, Mat.log(Mat.LOG, "y"));
            }
        }
        c.fill(x0, 16, z0, x0 + 4, 16, z0 + 4, Mat.PLANKS);
        Roofs.hip(c, x0 - 1, z0 - 1, x0 + 5, z0 + 5, 17, Roofs.TILE, 16, Roofs.TILE.block());
        c.set(x0 + 2, 15, z0 + 2, Mat.lantern(true));
    }

    /** Standing spots (floor y = 12.0) of the terrace for armed guards, as {x, z, yaw}. */
    static int[][] guardSpots() {
        int xl = -HW - 1, xr = HW + 1;
        return new int[][]{
                {xl + 3, ZB + 3, 180}, {xr - 3, ZB + 3, 180},
                {xl + 3, ZG - 3, 0}, {xr - 3, ZG - 3, 0}
        };
    }
}
