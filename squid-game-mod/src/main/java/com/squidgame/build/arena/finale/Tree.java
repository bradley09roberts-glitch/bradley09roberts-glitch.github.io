package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

/** Trees of the schoolyard: the big old oak (with a tyre swing and a ring bench) and small blossom trees. */
final class Tree {
    private Tree() {
    }

    private static String log(String type, char axis) {
        return "minecraft:" + type + "[axis=" + axis + "]";
    }

    /** A big old oak, trunk centred on block (x, z), canopy about 22 wide and 26 high. */
    static void bigOak(BuildContext c, int x, int z) {
        // trunk: flared roots, then a thick stem that thins out
        for (int y = 1; y <= 13; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    double r = y <= 2 ? 2.4 : (y <= 8 ? 1.5 : (y <= 11 ? 1.1 : 0.5));
                    if (d <= r) {
                        double h = Noise.hash01(x + dx * 3 + y, z + dz * 5, 12);
                        c.set(x + dx, y, z + dz, log(h < 0.3 ? "dark_oak_log" : "oak_log", 'y'));
                    }
                }
            }
        }
        // root arms creeping along the ground
        int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int i = 0; i < arms.length; i++) {
            int len = 3 + (i % 3);
            for (int k = 2; k <= len; k++) {
                int px = x + arms[i][0] * k, pz = z + arms[i][1] * k;
                c.set(px, 0, pz, "minecraft:coarse_dirt");
                if (k <= 3) {
                    c.set(px, 1, pz, log("oak_log", arms[i][0] != 0 ? 'x' : 'z'));
                }
            }
        }
        // main branches and leaf blobs
        int[][] ends = new int[7][];
        double[] ang = {0, 52, 105, 160, 215, 262, 310};
        for (int i = 0; i < ends.length; i++) {
            double a = Math.toRadians(ang[i] + 7 * Math.sin(i * 2.3));
            int len = 8 + (i * 5 % 4);
            int ex = x + (int) Math.round(len * Math.cos(a));
            int ez = z + (int) Math.round(len * Math.sin(a));
            int y0 = 9 + (i % 3), y1 = y0 + 4 + (i % 2);
            branch(c, x, y0, z, ex, y1, ez);
            ends[i] = new int[]{ex, y1, ez};
        }
        // leaf blobs: crown + one per branch + a few fillers
        blob(c, x, 22, z, 8.5, 5.0, 8.5, 1);
        for (int[] e : ends) {
            blob(c, e[0], e[1] + 3, e[2], 6.2, 4.4, 6.2, e[0] * 3 + e[2]);
        }
        blob(c, x + 4, 17, z - 3, 5.5, 3.5, 5.5, 5);
        blob(c, x - 4, 18, z + 4, 5.5, 3.5, 5.5, 6);
        // tyre swing under the strongest branch
        int bx = ends[1][0] * 2 / 3 + x / 3 + 0, bz = ends[1][2] * 2 / 3 + z / 3 + 0;
        bx = x + (int) Math.round((ends[1][0] - x) * 0.62);
        bz = z + (int) Math.round((ends[1][2] - z) * 0.62);
        int by = 10 + (int) Math.round((ends[1][1] - 10) * 0.62);
        c.fill(bx, 5, bz, bx, by - 1, bz, "minecraft:chain[axis=y]");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                if (dx != 0 || dy != 1) {
                    c.set(bx + dx, 2 + dy, bz, "minecraft:black_concrete");
                }
            }
        }
        c.set(bx, 4, bz, "minecraft:black_concrete");
        // ring bench around the trunk
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 3.6 && d < 4.6 && ((dx + dz * 3 + 40) % 5) != 0) {
                    c.set(x + dx, 1, z + dz, "minecraft:spruce_slab[type=bottom]");
                }
            }
        }
        // fallen leaves / worn ground around the roots
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 8 && !c.isSolid(x + dx, 0, z + dz) && Noise.hash01(x + dx, z + dz, 77) < 0.28 * (1 - d / 9)) {
                    c.set(x + dx, 0, z + dz, Noise.hash01(dx, dz, 5) < 0.6 ? "minecraft:coarse_dirt" : "minecraft:moss_block");
                }
            }
        }
    }

    /** One-block-thick log branch from (x0,y0,z0) to (x1,y1,z1) with the log axis following the dominant direction. */
    static void branch(BuildContext c, int x0, int y0, int z0, int x1, int y1, int z1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            int px = (int) Math.round(x0 + (x1 - x0) * t);
            int py = (int) Math.round(y0 + (y1 - y0) * t + 1.2 * Math.sin(Math.PI * t));
            int pz = (int) Math.round(z0 + (z1 - z0) * t);
            int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
            char axis = dx >= dz && dx >= dy ? 'x' : (dz >= dy ? 'z' : 'y');
            c.set(px, py, pz, log("oak_log", axis));
            if (i < steps / 2 && steps > 4) {
                c.set(px, py - 1, pz, log("oak_log", axis));       // thicker near the trunk
            }
        }
    }

    /** Irregular leaf blob (noise-perturbed ellipsoid with small holes). */
    static void blob(BuildContext c, int cx, int cy, int cz, double rx, double ry, double rz, int seed) {
        int ix = (int) Math.ceil(rx) + 1, iy = (int) Math.ceil(ry) + 1, iz = (int) Math.ceil(rz) + 1;
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -iy; dy <= iy; dy++) {
                for (int dz = -iz; dz <= iz; dz++) {
                    double d = Math.sqrt((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz));
                    double n = Noise.value((cx + dx) * 0.45, (cz + dz) * 0.45 + dy * 0.9, 300 + seed) - 0.5;
                    if (d + 0.35 * n < 1.0) {
                        double h = Noise.hash01(cx + dx, cy + dy, cz + dz, 9 + seed);
                        if (h < 0.05) {
                            continue;
                        }
                        String leaf = h < 0.30 ? "minecraft:dark_oak_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
                        c.setIfFree(cx + dx, cy + dy, cz + dz, leaf);
                    }
                }
            }
        }
    }

    /** A small blossom tree (trunk of the given height, round crown). */
    static void blossom(BuildContext c, int x, int z, int h, boolean cherry) {
        String trunk = cherry ? "cherry_log" : "oak_log";
        String leaves = cherry ? "minecraft:cherry_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
        c.fill(x, 1, z, x, h, z, log(trunk, 'y'));
        c.set(x + 1, 1, z, log(trunk, 'x'));
        c.set(x - 1, 1, z, log(trunk, 'x'));
        c.set(x, 1, z + 1, log(trunk, 'z'));
        c.set(x, 1, z - 1, log(trunk, 'z'));
        c.set(x, h - 2, z + 1, log(trunk, 'z'));
        c.set(x + 1, h - 3, z, log(trunk, 'x'));
        double r = 3.2 + h * 0.18;
        int ix = (int) Math.ceil(r) + 1;
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -ix; dz <= ix; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz * 1.0 + dy * dy * 1.9);
                    double n = Noise.hash01(x + dx, z + dz + dy * 13, 31);
                    if (d < r - 0.4 * n && n > 0.06) {
                        c.setIfFree(x + dx, h + 1 + dy, z + dz, leaves);
                    }
                }
            }
        }
        // blossom petals on the ground below the crown
        if (cherry) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Noise.hash01(x + dx, z + dz, 33) < 0.18 && !c.isSolid(x + dx, 1, z + dz)) {
                        c.set(x + dx, 1, z + dz, "minecraft:pink_petals[facing=north,flower_amount=2]");
                    }
                }
            }
        }
    }
}
