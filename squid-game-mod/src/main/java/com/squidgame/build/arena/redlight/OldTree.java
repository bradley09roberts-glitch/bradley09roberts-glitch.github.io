package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;

import java.util.Random;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * The big old tree behind the doll: a gnarled trunk about 4-5 blocks across with a buttressed, flaring root base, a
 * fork into thick limbs and side branches, and a broad crown (about 30 wide, 25 high) of oak and azalea leaves built
 * from noisy, partly hollow puffs so sunlight still dapples through. All leaves are {@code persistent=true}.
 * Hanging lanterns and floor uplights keep the ground under the crown lit.
 */
public final class OldTree {
    private OldTree() {
    }

    private static final int CX = TREE_X;
    private static final int CZ = TREE_Z;
    private static final int TRUNK_TOP = 17;
    private static final double[] ROOT_ANGLES = {0.15, 0.95, 1.75, 2.45, 3.55, 4.45, 5.35};

    public static void build(BuildContext c) {
        trunk(c);
        roots(c);
        glowKnots(c);
        branches(c);
        crown(c);
        hangings(c);
    }

    // ------------------------------------------------------------------ bark

    private static String bark(int x, int y, int z, String axis) {
        double n = Noise.value3(301, x * 0.55, y * 0.55, z * 0.55);
        double r = Noise.rand(302, x, y, z);
        String id;
        if (n < 0.30) {
            id = "minecraft:dark_oak_log";
        } else if (n < 0.86) {
            id = r < 0.93 ? "minecraft:oak_log" : "minecraft:stripped_oak_log";
        } else {
            id = "minecraft:spruce_log";
        }
        return id + "[axis=" + axis + "]";
    }

    private static String leaf(int x, int y, int z) {
        double a = Noise.fbm3(311, x / 4.2, y / 4.2, z / 4.2, 2);
        double b = Noise.fbm3(312, x / 3.0, y / 3.0, z / 3.0, 2);
        if (b > 0.64) {
            return "minecraft:flowering_azalea_leaves[persistent=true]";
        }
        if (a > 0.58) {
            return "minecraft:azalea_leaves[persistent=true]";
        }
        return "minecraft:oak_leaves[persistent=true]";
    }

    // ------------------------------------------------------------------ trunk and roots

    private static void trunk(BuildContext c) {
        for (int y = 1; y <= TRUNK_TOP; y++) {
            // slightly fluted, gently tapering trunk; buttresses (lobes) fade out within the first 6 blocks
            double rBase = 2.6 + 0.22 * Math.sin(y * 0.7) - (y > 11 ? 0.28 * (y - 11) : 0);
            double buttress = 2.9 * Math.exp(-(y - 1) / 1.7);
            int ext = 8;
            for (int dx = -ext; dx <= ext; dx++) {
                for (int dz = -ext; dz <= ext; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > 8) {
                        continue;
                    }
                    double ang = Math.atan2(dz, dx);
                    double lobes = 0;
                    for (double a : ROOT_ANGLES) {
                        double da = Math.atan2(Math.sin(ang - a), Math.cos(ang - a));
                        lobes += Math.exp(-da * da / 0.11);
                    }
                    // buttresses are shorter on the side facing the doll (-z)
                    double front = Math.sin(ang) < 0 ? 0.45 + 0.55 * (1 + Math.sin(ang)) : 1.0;
                    double lump = 0.90 + 0.22 * Noise.value3(321, Math.cos(ang) * 1.6 + y * 0.05, Math.sin(ang) * 1.6, y * 0.17);
                    double r = (rBase + buttress * Math.min(lobes, 1.15) * front) * lump;
                    if (d <= r) {
                        c.set(CX + dx, y, CZ + dz, bark(CX + dx, y, CZ + dz, "y"));
                    }
                }
            }
        }
    }

    private static void roots(BuildContext c) {
        Random rnd = new Random(5150);
        for (int k = 0; k < ROOT_ANGLES.length; k++) {
            double a = ROOT_ANGLES[k] + (rnd.nextDouble() - 0.5) * 0.25;
            boolean front = Math.sin(a) < -0.35;
            double len = front ? 6.5 + rnd.nextDouble() * 1.5 : 9 + rnd.nextDouble() * 4.5;
            double wig = (rnd.nextDouble() - 0.5) * 3.0;
            String axis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? "x" : "z";
            int steps = (int) (len * 2.4);
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                double dist = 3.2 + (len - 3.2) * t;
                double lat = wig * Math.sin(t * 4.0 + k);
                double px = CX + 0.5 + Math.cos(a) * dist - Math.sin(a) * lat;
                double pz = CZ + 0.5 + Math.sin(a) * dist + Math.cos(a) * lat;
                double rad = 1.35 - 0.85 * t;
                int h = t < 0.35 ? 2 : 1;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int bx = (int) Math.floor(px) + dx, bz = (int) Math.floor(pz) + dz;
                        if (Math.hypot(bx + 0.5 - px, bz + 0.5 - pz) <= rad) {
                            for (int y = 1; y <= h; y++) {
                                if (!c.isSolid(bx, y, bz)) {
                                    String s = (t > 0.55 && Noise.rand(331, bx, y, bz) < 0.35)
                                            ? "minecraft:muddy_mangrove_roots[axis=" + axis + "]"
                                            : bark(bx, y, bz, axis);
                                    c.set(bx, y, bz, s);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Glowing knots (shroomlight) set into the bark and on top of the buttress roots: organic-looking orange nodules
     * that also light the ledges of the base, which floor lights cannot reach.
     */
    private static void glowKnots(BuildContext c) {
        double[] azim = {0.35, 1.25, 2.15, 3.05, 3.95, 4.85, 5.55, 5.9, 0.8, 2.6, 4.4};
        int[] ys = {2, 4, 3, 5, 2, 4, 3, 6, 7, 6, 3};
        for (int i = 0; i < azim.length; i++) {
            for (double rr = 8; rr >= 1; rr -= 0.5) {
                int x = CX + (int) Math.round(Math.cos(azim[i]) * rr);
                int z = CZ + (int) Math.round(Math.sin(azim[i]) * rr);
                String s = c.get(x, ys[i], z);
                if (s != null && s.contains("_log")) {
                    c.set(x, ys[i], z, "minecraft:shroomlight");
                    break;
                }
            }
        }
        // nodules on top of the buttress roots, one per lobe and a second further out on the long ones
        for (double a : ROOT_ANGLES) {
            for (double rr : new double[]{4.2, 6.4}) {
                int x = CX + (int) Math.round(Math.cos(a) * rr);
                int z = CZ + (int) Math.round(Math.sin(a) * rr);
                for (int y = 6; y >= 1; y--) {
                    if (c.isSolid(x, y, z)) {
                        if (y <= 4 && c.get(x, y, z).contains("_log")) {
                            c.set(x, y, z, "minecraft:shroomlight");
                        }
                        break;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ limbs

    private static void limb(BuildContext c, double x0, double y0, double z0, double x1, double y1, double z1, double r0, double r1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = (int) Math.ceil(len * 2.0);
        String axis = Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz) ? "y" : (Math.abs(dx) >= Math.abs(dz) ? "x" : "z");
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double px = x0 + dx * t, py = y0 + dy * t, pz = z0 + dz * t;
            double r = r0 + (r1 - r0) * t;
            int ir = (int) Math.ceil(r);
            for (int ax = -ir; ax <= ir; ax++) {
                for (int ay = -ir; ay <= ir; ay++) {
                    for (int az = -ir; az <= ir; az++) {
                        int bx = (int) Math.floor(px) + ax, by = (int) Math.floor(py) + ay, bz = (int) Math.floor(pz) + az;
                        double d = Math.sqrt((bx + 0.5 - px) * (bx + 0.5 - px) + (by + 0.5 - py) * (by + 0.5 - py) + (bz + 0.5 - pz) * (bz + 0.5 - pz));
                        if (d <= r + 0.15) {
                            c.set(bx, by, bz, bark(bx, by, bz, axis));
                        }
                    }
                }
            }
        }
    }

    private static void polyline(BuildContext c, double[][] pts, double r0, double r1) {
        double total = 0;
        for (int i = 1; i < pts.length; i++) {
            total += Math.sqrt(Math.pow(pts[i][0] - pts[i - 1][0], 2) + Math.pow(pts[i][1] - pts[i - 1][1], 2) + Math.pow(pts[i][2] - pts[i - 1][2], 2));
        }
        double acc = 0;
        for (int i = 1; i < pts.length; i++) {
            double seg = Math.sqrt(Math.pow(pts[i][0] - pts[i - 1][0], 2) + Math.pow(pts[i][1] - pts[i - 1][1], 2) + Math.pow(pts[i][2] - pts[i - 1][2], 2));
            double ra = r0 + (r1 - r0) * (acc / total);
            double rb = r0 + (r1 - r0) * ((acc + seg) / total);
            limb(c, pts[i - 1][0], pts[i - 1][1], pts[i - 1][2], pts[i][0], pts[i][1], pts[i][2], ra, rb);
            acc += seg;
        }
    }

    private static void branches(BuildContext c) {
        double x = CX + 0.5, z = CZ + 0.5;
        // main limbs from the fork
        polyline(c, new double[][]{{x - 1, 15, z}, {x - 5, 21, z - 2}, {x - 8, 26, z - 3}, {x - 9, 29, z - 3}}, 2.3, 1.1);
        polyline(c, new double[][]{{x + 1, 15, z}, {x + 5, 21, z - 2}, {x + 8, 26, z - 2}, {x + 9, 29, z - 3}}, 2.3, 1.1);
        polyline(c, new double[][]{{x, 16, z + 1}, {x - 2, 23, z + 4}, {x - 3, 28, z + 8}, {x - 3, 30, z + 10}}, 2.3, 1.1);
        polyline(c, new double[][]{{x, 16, z + 1}, {x + 3, 24, z + 4}, {x + 5, 29, z + 7}, {x + 5, 31, z + 9}}, 2.2, 1.1);
        polyline(c, new double[][]{{x, 15, z}, {x, 24, z}, {x, 31, z + 2}, {x, 34, z + 2}}, 2.0, 1.0);
        // thick lower side branches reaching out
        polyline(c, new double[][]{{x - 2, 11, z}, {x - 6, 13, z - 1}, {x - 10, 16, z}, {x - 12, 18, z}}, 1.8, 0.9);
        polyline(c, new double[][]{{x + 2, 12, z}, {x + 6, 14, z + 1}, {x + 10, 17, z + 2}, {x + 12, 19, z + 2}}, 1.8, 0.9);
        polyline(c, new double[][]{{x, 10, z + 2}, {x - 3, 12, z + 6}, {x - 5, 15, z + 10}, {x - 6, 16, z + 12}}, 1.6, 0.8);
        polyline(c, new double[][]{{x + 2, 13, z - 2}, {x + 4, 17, z - 4}, {x + 6, 20, z - 5}}, 1.5, 0.8);
        polyline(c, new double[][]{{x - 2, 13, z - 2}, {x - 4, 17, z - 4}, {x - 6, 21, z - 5}}, 1.4, 0.8);
        // twigs
        Random r = new Random(6006);
        double[][] twigRoots = {{x - 7, 24, z - 3}, {x + 7, 24, z - 2}, {x - 3, 26, z + 6}, {x + 4, 27, z + 6}, {x - 9, 16, z}, {x + 9, 17, z + 1}, {x, 28, z + 1}};
        for (double[] t : twigRoots) {
            for (int i = 0; i < 4; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double len = 4 + r.nextDouble() * 4;
                limb(c, t[0], t[1], t[2], t[0] + Math.cos(a) * len, t[1] + 1 + r.nextDouble() * 3, t[2] + Math.sin(a) * len, 0.9, 0.4);
            }
        }
    }

    // ------------------------------------------------------------------ crown

    private static void puff(BuildContext c, double cx, double cy, double cz, double rx, double ry, double rz, int seed) {
        int ix = (int) Math.ceil(rx * 1.4), iy = (int) Math.ceil(ry * 1.4), iz = (int) Math.ceil(rz * 1.4);
        int bx0 = (int) Math.round(cx), by0 = (int) Math.round(cy), bz0 = (int) Math.round(cz);
        double minR = Math.min(rx, Math.min(ry, rz));
        double shell = 2.6 / minR;
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -iy; dy <= iy; dy++) {
                for (int dz = -iz; dz <= iz; dz++) {
                    int x = bx0 + dx, y = by0 + dy, z = bz0 + dz;
                    double ex = (x - cx) / rx, ey = (y - cy) / ry, ez = (z - cz) / rz;
                    double d = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    double n = Noise.value3(seed, x * 0.42, y * 0.42, z * 0.42) - 0.5;
                    double n2 = Noise.rand(seed + 1, x, y, z) - 0.5;
                    double lim = 1.0 + 0.34 * n + 0.14 * n2;
                    if (d > lim || d < lim - shell) {
                        continue;
                    }
                    c.setIfFree(x, y, z, leaf(x, y, z));
                }
            }
        }
    }

    private static void crown(BuildContext c) {
        double x = CX + 0.5, z = CZ + 0.5;
        double[][] puffs = {
                // cx, cy, cz, rx, ry, rz     (layered umbrella of pads, about 30 wide and 25 high)
                // top layer
                {x, 34.5, z + 2, 6.5, 3.4, 6.5},
                {x - 4, 32, z + 5, 5, 3, 5},
                // upper-middle layer
                {x - 6.5, 29, z - 1, 6.2, 3.6, 6},
                {x + 6.5, 29.5, z - 1, 6.2, 3.6, 6},
                {x, 29, z + 7, 7, 3.8, 6.5},
                {x, 30, z - 2, 5.5, 3.4, 5},
                // lower-middle layer
                {x - 9.5, 24, z + 1, 5.5, 3.2, 5.5},
                {x + 9.5, 25, z + 2, 5.5, 3.2, 5.5},
                {x - 5, 24.5, z + 10, 5.5, 3.2, 5},
                {x + 6, 25, z + 10, 5.5, 3.2, 5},
                {x - 5, 25, z - 6, 4.6, 3, 4.2},
                {x + 5, 25.5, z - 6, 4.6, 3, 4.2},
                // low skirts at the ends of the thick side branches
                {x - 11, 19, z, 4.2, 2.8, 4.2},
                {x + 11, 20, z + 2, 4.2, 2.8, 4.2},
                {x - 6.5, 17.5, z + 12, 3.8, 2.6, 3.8},
        };
        int seed = 400;
        for (double[] p : puffs) {
            puff(c, p[0], p[1], p[2], p[3], p[4], p[5], seed);
            seed += 3;
        }
    }

    // ------------------------------------------------------------------ hanging lights

    private static void hangings(BuildContext c) {
        Random r = new Random(8008);
        int placed = 0;
        for (int attempt = 0; attempt < 400 && placed < 22; attempt++) {
            int x = CX - 15 + r.nextInt(31);
            int z = CZ - 8 + r.nextInt(25);
            // find the lowest leaf layer above the ground in this column (underside of the crown)
            for (int y = 14; y <= 32; y++) {
                if (c.isSolid(x, y, z) && !c.isSolid(x, y - 1, z) && !c.isSolid(x, y - 2, z) && !c.isSolid(x, y - 3, z)) {
                    int len = 1 + r.nextInt(2);
                    if (y - len - 1 <= 12) {
                        break;
                    }
                    for (int k = 1; k <= len; k++) {
                        c.set(x, y - k, z, "minecraft:chain[axis=y]");
                    }
                    c.set(x, y - len - 1, z, "minecraft:lantern[hanging=true]");
                    placed++;
                    break;
                }
            }
        }
    }

    /** True when something solid (leaves, branches) hangs above this floor cell. */
    private static boolean shaded(BuildContext c, int x, int z) {
        for (int y = 3; y <= 46; y++) {
            if (c.isSolid(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean floorFree(BuildContext c, int x, int z) {
        return c.isSolid(x, 0, z) && !c.isSolid(x, 1, z);
    }

    /**
     * Floor uplights (flush sea lanterns) on a covering lattice under the crown. Sky light only reaches the ground under
     * a canopy by creeping in sideways from the open columns (one level per block), so every cell five or more blocks
     * (in plan) from open sky gets a light within three blocks (standing level 11 or more). Call after the props.
     */
    public static int floorLights(BuildContext c) {
        final int x0 = CX - 30, x1 = CX + 30, z0 = SAFE_Z0 - 8, z1 = FIELD_Z1;
        final int w = x1 - x0 + 1, d = z1 - z0 + 1;
        final int[][] dist = new int[w][d];
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!shaded(c, x, z)) {
                    dist[x - x0][z - z0] = 0;
                    queue.add(new int[]{x, z});
                } else {
                    dist[x - x0][z - z0] = Integer.MAX_VALUE;
                }
            }
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] q = queue.poll();
            int dq = dist[q[0] - x0][q[1] - z0];
            for (int[] dd : dirs) {
                int nx = q[0] + dd[0], nz = q[1] + dd[1];
                if (nx < x0 || nx > x1 || nz < z0 || nz > z1) {
                    continue;
                }
                if (dist[nx - x0][nz - z0] > dq + 1) {
                    dist[nx - x0][nz - z0] = dq + 1;
                    queue.add(new int[]{nx, nz});
                }
            }
        }
        return Lighting.floorGrid(c, x0, z0, x1, z1, 3, "minecraft:sea_lantern",
                (x, z) -> x >= x0 && x <= x1 && z >= z0 && z <= z1 && floorFree(c, x, z) && dist[x - x0][z - z0] >= 5,
                (x, z) -> floorFree(c, x, z), "minecraft:sea_lantern");
    }
}
