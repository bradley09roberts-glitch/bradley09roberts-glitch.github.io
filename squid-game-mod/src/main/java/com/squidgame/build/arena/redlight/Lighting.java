package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;

import java.util.function.BiPredicate;

/**
 * Light planning helpers. Light falls off by one level per block (Manhattan distance), so a standing cell that is
 * {@code d} blocks (in plan) from a light tile set flush into the floor receives {@code 14 - d}. A perfect
 * L1 covering lattice (sources where {@code x + (2r+1) z == 0 mod (2r^2+2r+1)}) keeps every cell within {@code r}
 * of a source with the fewest tiles; leftovers near edges are patched greedily.
 */
public final class Lighting {
    private Lighting() {
    }

    /**
     * Sets flush floor light tiles (y = 0) covering the rectangle so that every cell for which {@code need} is true is
     * within L1 distance {@code r} of a light (standing light level at least 14 - r). Lattice points are only used when
     * some needed cell lies within {@code r} of them. Cells where {@code canPlace} is false are never replaced; floor
     * blocks already equal to one of {@code existing} count as lights.
     *
     * @return number of lights placed
     */
    public static int floorGrid(BuildContext c, int x0, int z0, int x1, int z1, int r, String state,
                                BiPredicate<Integer, Integer> need, BiPredicate<Integer, Integer> canPlace, String... existing) {
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        boolean[][] src = new boolean[w][d];
        boolean[][] fresh = new boolean[w][d];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                String cur = c.get(x, 0, z);
                for (String e : existing) {
                    if (e.equals(cur)) {
                        src[x - x0][z - z0] = true;
                    }
                }
            }
        }
        int m = 2 * r * r + 2 * r + 1, k = 2 * r + 1;
        int placed = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (Math.floorMod(x + k * z, m) == 0 && canPlace.test(x, z) && !src[x - x0][z - z0] && anyNeed(need, x, z, r)) {
                    src[x - x0][z - z0] = true;
                    fresh[x - x0][z - z0] = true;
                    placed++;
                }
            }
        }
        // greedy patch of cells not covered
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (!need.test(x, z) || covered(src, x0, z0, x, z, r)) {
                    continue;
                }
                int bx = Integer.MIN_VALUE, bz = 0, best = Integer.MAX_VALUE;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        int dist = Math.abs(dx) + Math.abs(dz);
                        int px = x + dx, pz = z + dz;
                        if (dist > r || px < x0 || px > x1 || pz < z0 || pz > z1 || !canPlace.test(px, pz)) {
                            continue;
                        }
                        // prefer the cell itself, then a placeable neighbour
                        int score = dist;
                        if (score < best) {
                            best = score;
                            bx = px;
                            bz = pz;
                        }
                    }
                }
                if (bx != Integer.MIN_VALUE) {
                    src[bx - x0][bz - z0] = true;
                    fresh[bx - x0][bz - z0] = true;
                    placed++;
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (fresh[x - x0][z - z0]) {
                    c.set(x, 0, z, state);
                }
            }
        }
        return placed;
    }

    private static boolean anyNeed(BiPredicate<Integer, Integer> need, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r + Math.abs(dx); dz <= r - Math.abs(dx); dz++) {
                if (need.test(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean covered(boolean[][] src, int x0, int z0, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r + Math.abs(dx); dz <= r - Math.abs(dx); dz++) {
                int sx = x + dx - x0, sz = z + dz - z0;
                if (sx >= 0 && sx < src.length && sz >= 0 && sz < src[0].length && src[sx][sz]) {
                    return true;
                }
            }
        }
        return false;
    }
}
