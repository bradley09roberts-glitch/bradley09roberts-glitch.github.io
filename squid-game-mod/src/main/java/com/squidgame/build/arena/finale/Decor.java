package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;
import com.squidgame.build.Marker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * Last pass of small ground decoration: footprint-like one block dents trodden by children, a few sand piles, dry
 * bushes at the wall foot and tufts outside the rim wall. Everything here keeps away from the court belt, from the
 * route between the gate and the square, from every marker and from anything already standing.
 */
final class Decor {
    private Decor() {
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** Local cells of every marker written so far (any marker is a place an entity may stand or a point we must not disturb). */
    private static Set<Long> markerCells(BuildContext c) {
        Set<Long> out = new HashSet<>();
        c.buffer().forEachMarker((Marker m) -> {
            int lx = (int) Math.floor(m.x()) - c.originX();
            int lz = (int) Math.floor(m.z()) - c.originZ();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    out.add(key(lx + dx, lz + dz));
                }
            }
        });
        return out;
    }

    private static boolean isSand(String b) {
        return b != null && (b.equals(Yard.SAND) || b.equals(Yard.SMOOTH) || b.equals(Yard.SANDSTONE)
                || b.equals(Yard.POWDER) || b.equals(Yard.WARM));
    }

    /** True where a decoration may alter the ground: open yard sand, outside the belt and the gate route, nothing above. */
    private static boolean free(BuildContext c, int x, int z, Set<Long> reserved) {
        if (x < YX0 + 1 || x > YX1 - 1 || z < YZ0 + 1 || z > YZ1 - 1) {
            return false;
        }
        if (inBelt(x, z) || (Math.abs(x) <= 12 && z > 24)) {
            return false;
        }
        if (x >= 45 && Math.abs(z) <= 5) {
            return false;                                   // steps up to the grandstand
        }
        if (!isSand(c.get(x, 0, z)) || c.isSolid(x, 1, z) || c.isSolid(x, 2, z) || reserved.contains(key(x, z))) {
            return false;
        }
        return true;
    }

    private static void dent(BuildContext c, int x, int z, Set<Long> reserved) {
        if (free(c, x, z, reserved)) {
            c.air(x, 0, z);                                 // one block deep, sand below stays visible
        }
    }

    /** A line of alternating left/right footprints. */
    private static void trail(BuildContext c, double x0, double z0, double x1, double z1, Set<Long> reserved) {
        double dx = x1 - x0, dz = z1 - z0;
        double len = Math.hypot(dx, dz);
        double nx = -dz / len, nz = dx / len;
        int n = (int) (len / 1.8);
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            double side = (i & 1) == 0 ? 0.65 : -0.65;
            int fx = (int) Math.round(x0 + dx * t + nx * side);
            int fz = (int) Math.round(z0 + dz * t + nz * side);
            dent(c, fx, fz, reserved);
        }
    }

    static void build(BuildContext c) {
        Set<Long> reserved = markerCells(c);
        // trodden paths
        double[][] trails = {
                {-8, -42, -23, -31}, {9, -44, 22, -33}, {-40, -30, -30, -9}, {-34, -2, -30, 12}, {-24, 22, -16, 34},
                {30, -17, 30, -9}, {38, -3, 30, 6}, {26, 30, 36, 20}, {44, 14, 36, 30}, {20, 44, 30, 36}, {-20, 46, -30, 36},
                {-44, 2, -40, 22}, {-12, 40, -26, 52}, {14, 56, 28, 46}, {-4, -37, 4, -37}
        };
        for (double[] t : trails) {
            trail(c, t[0], t[1], t[2], t[3], reserved);
        }
        // scattered single dents and shallow scuffs
        for (int x = YX0 + 2; x <= YX1 - 2; x++) {
            for (int z = YZ0 + 2; z <= YZ1 - 2; z++) {
                if (Noise.hash01(x, z, 1301) < 0.0065) {
                    dent(c, x, z, reserved);
                }
            }
        }
        // sand piles (children's heaps): 2x2 mounds one block high
        int[][] piles = {{-22, -8}, {37, -9}, {-44, 31}, {20, 33}, {42, -44}, {-20, 44}};
        for (int[] p : piles) {
            boolean ok = true;
            for (int dx = 0; dx <= 1 && ok; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    ok &= free(c, p[0] + dx, p[1] + dz, reserved);
                }
            }
            if (ok) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        c.set(p[0] + dx, 1, p[1] + dz, Yard.SAND);
                    }
                }
                c.set(p[0], 2, p[1], Yard.SAND);
            }
        }
        // dry bushes at the foot of the rim wall
        for (int x = YX0 + 1; x <= YX1 - 1; x++) {
            for (int z : new int[]{YZ0 + 1, YZ1 - 1}) {
                if (Noise.hash01(x, z, 1401) < 0.05 && free(c, x, z, reserved) && !(Math.abs(x) <= 8 && z > 0)) {
                    c.set(x, 1, z, "minecraft:dead_bush");
                }
            }
        }
        for (int z = YZ0 + 1; z <= YZ1 - 1; z++) {
            for (int x : new int[]{YX0 + 1, YX1 - 1}) {
                if (Noise.hash01(x, z, 1402) < 0.05 && free(c, x, z, reserved)) {
                    c.set(x, 1, z, "minecraft:dead_bush");
                }
            }
        }
        outside(c);
    }

    /** Tufts and a few boulders on the dry ground between the rim wall and the painted wall (seen from the grandstand). */
    private static void outside(BuildContext c) {
        for (int x = BX0 + 1; x <= BX1 - 1; x++) {
            for (int z = BZ0 + 1; z <= BZ1 - 1; z++) {
                if (x >= WX0 - 1 && x <= WX1 + 1 && z >= WZ0 - 1 && z <= WZ1 + 1) {
                    continue;
                }
                if (c.isSolid(x, 1, z)) {
                    continue;
                }
                String g = c.get(x, 0, z);
                double h = Noise.hash01(x, z, 1501);
                if ("minecraft:grass_block".equals(g) && h < 0.30) {
                    c.set(x, 1, z, h < 0.04 ? "minecraft:fern" : "minecraft:short_grass");
                } else if ("minecraft:sand".equals(g) && h < 0.02) {
                    c.set(x, 1, z, "minecraft:dead_bush");
                } else if (h > 0.9985) {
                    int s = 1 + (int) (Noise.hash01(x, z, 1502) * 2);
                    c.fill(x, 1, z, x + s, 1, z + s, h > 0.9993 ? "minecraft:mossy_cobblestone" : "minecraft:stone");
                    if (s == 2) {
                        c.set(x + 1, 2, z + 1, "minecraft:stone");
                    }
                }
            }
        }
    }
}
