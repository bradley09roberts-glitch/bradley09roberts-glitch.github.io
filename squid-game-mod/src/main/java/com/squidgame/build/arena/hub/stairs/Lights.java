package com.squidgame.build.arena.hub.stairs;

import com.squidgame.build.BlockBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Lighting pass over the finished hall. Block light is simulated pessimistically (stairs and slabs block light); the
 * walkable cells reachable from the entrance (walking, hopping one block) are found with the half-block walker; flush
 * floor panels go onto a diagonal lattice (every cell within 3 blocks of a panel), and whatever is still below level 11
 * gets a lamp block set into the nearest suitable wall, floor or soffit. Everything placed is a visible fixture
 * (light panels, sea lanterns, shroomlights).
 */
final class Lights {
    private Lights() {
    }

    static int placed, lattice, unfixed, needCells;

    static void run(Ctx k) {
        BlockBuffer b = k.c.buffer();
        int oy = k.c.worldY(0);
        Grid g = Grid.snapshot(b, oy);
        Walker w = new Walker(g);
        Walker.Reach reach = w.flood(1, -163, 0, 2, 2);
        // need cells: first free cell above every reachable standing position
        boolean[] need = new boolean[g.info.length];
        List<int[]> needs = new ArrayList<>();
        for (int[] p : reach.order) {
            int[] c = Walker.cellOf(p[0], p[1], p[2]);
            if (!Grid.in(c[0], c[1], c[2])) {
                continue;
            }
            int id = Grid.idx(c[0], c[1], c[2]);
            if (!need[id]) {
                need[id] = true;
                needs.add(c);
            }
        }
        needCells = needs.size();
        // pass 1: floor panel lattice
        for (int[] c : needs) {
            if (Math.floorMod(c[0] + 7 * c[2], 25) != 0) {
                continue;
            }
            int below = g.info(c[0], c[1] - 1, c[2]);
            if (Blocks.kind(below) == Blocks.FULL && Blocks.opaque(below) && Blocks.emit(below) == 0 && lampSafe(g, c[0], c[1] - 1, c[2])) {
                String s = ((c[0] + c[2]) & 1) == 0 ? Pal.LIGHT_WHITE : Pal.LIGHT_WARM;
                put(k, g, c[0], c[1] - 1, c[2], s);
                lattice++;
            }
        }
        g.computeLight();
        // pass 2: greedy patches
        byte[] dark = new byte[g.info.length];
        int remaining = 0;
        for (int[] c : needs) {
            if (g.lightAt(c[0], c[1], c[2]) < 11) {
                dark[Grid.idx(c[0], c[1], c[2])] = 1;
                remaining++;
            }
        }
        List<int[]> order = new ArrayList<>(needs);
        order.sort((a, bb) -> a[1] != bb[1] ? Integer.compare(a[1], bb[1]) : a[2] != bb[2] ? Integer.compare(a[2], bb[2]) : Integer.compare(a[0], bb[0]));
        for (int[] f : order) {
            int fid = Grid.idx(f[0], f[1], f[2]);
            int guard = 0;
            while (dark[fid] == 1 && guard++ < 12) {
                int[] best = null;
                int bestScore = Integer.MIN_VALUE;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                            if (dist > 3) {
                                continue;
                            }
                            int cx = f[0] + dx, cy = f[1] + dy, cz = f[2] + dz;
                            if (!Grid.in(cx, cy, cz)) {
                                continue;
                            }
                            int info = g.info[Grid.idx(cx, cy, cz)];
                            if (Blocks.kind(info) != Blocks.FULL || !Blocks.opaque(info) || Blocks.emit(info) > 0) {
                                continue;
                            }
                            int type = lampType(g, cx, cy, cz, need);
                            if (type < 0 || !lampSafe(g, cx, cy, cz)) {
                                continue;
                            }
                            int gain = 0;
                            for (int ex = -4; ex <= 4; ex++) {
                                for (int ey = -4 + Math.abs(ex); ey <= 4 - Math.abs(ex); ey++) {
                                    for (int ez = -4 + Math.abs(ex) + Math.abs(ey); ez <= 4 - Math.abs(ex) - Math.abs(ey); ez++) {
                                        int nx = cx + ex, ny = cy + ey, nz = cz + ez;
                                        if (Grid.in(nx, ny, nz) && dark[Grid.idx(nx, ny, nz)] == 1) {
                                            gain++;
                                        }
                                    }
                                }
                            }
                            int score = gain * 100 - dist * 12 - type * 6;
                            if (score > bestScore) {
                                bestScore = score;
                                best = new int[]{cx, cy, cz, type};
                            }
                        }
                    }
                }
                if (best == null) {
                    unfixed++;
                    dark[fid] = 2;
                    break;
                }
                String s = switch (best[3]) {
                    case 0, 1 -> ((best[0] + best[2]) & 1) == 0 ? Pal.LIGHT_WHITE : Pal.LIGHT_WARM;
                    default -> ((best[0] + best[1] + best[2]) & 1) == 0 ? Pal.SEA : Pal.SHROOM;
                };
                put(k, g, best[0], best[1], best[2], s);
                // refresh the dark flags around the new lamp
                for (int ex = -5; ex <= 5; ex++) {
                    for (int ey = -5; ey <= 5; ey++) {
                        for (int ez = -5; ez <= 5; ez++) {
                            int nx = best[0] + ex, ny = best[1] + ey, nz = best[2] + ez;
                            if (Grid.in(nx, ny, nz)) {
                                int id = Grid.idx(nx, ny, nz);
                                if (dark[id] == 1 && g.light[id] >= 11) {
                                    dark[id] = 0;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A lamp block must not sit where it would be a standing surface of the main flow in a way that breaks anything. */
    private static boolean lampSafe(Grid g, int x, int y, int z) {
        // do not replace the hall's outermost shell, only blocks that border the interior
        return x >= -46 && x <= 46 && z >= -171 && z <= -81 && y >= -1 && y <= 73;
    }

    /** 0 floor tile (cell above is a walking cell), 1 soffit (cell below is transparent), 2 wall; -1 = buried. */
    private static int lampType(Grid g, int x, int y, int z, boolean[] need) {
        boolean up = !Blocks.opaque(g.info(x, y + 1, z));
        boolean down = !Blocks.opaque(g.info(x, y - 1, z));
        boolean side = !Blocks.opaque(g.info(x + 1, y, z)) || !Blocks.opaque(g.info(x - 1, y, z))
                || !Blocks.opaque(g.info(x, y, z + 1)) || !Blocks.opaque(g.info(x, y, z - 1));
        if (up && Grid.in(x, y + 1, z) && need[Grid.idx(x, y + 1, z)]) {
            return 0;
        }
        if (down) {
            return 1;
        }
        if (side || up) {
            return 2;
        }
        return -1;
    }

    private static void put(Ctx k, Grid g, int x, int y, int z, String state) {
        k.set(x, y, z, state);
        g.setInfo(x, y, z, Blocks.classify(state));
        g.addSource(x, y, z, 15);
        placed++;
    }
}
