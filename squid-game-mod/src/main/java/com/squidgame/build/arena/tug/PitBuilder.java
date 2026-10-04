package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

import java.util.Random;

/**
 * The pit: sheer concrete walls with pipe runs and caged lamps, a dark tiled floor with hazard-striped border, grating
 * channels glowing faintly from lanterns underneath, a concentric drain "eye" under the rope, scattered mattresses and debris.
 */
final class PitBuilder {
    private PitBuilder() {
    }

    static void build(BuildContext c) {
        walls(c);
        floor(c);
        pipes(c);
        cageLamps(c);
        debris(c);
        mattresses(c);
    }

    // ------------------------------------------------------------------ walls

    private static void walls(BuildContext c) {
        // long pit walls (north z = -21, south z = 21), y -30..0
        Sym.pattern(c, Geo.PX0, Geo.PIT, -Geo.PZ - 1, -1, 0, -Geo.PZ - 1, (x, y, z) -> Surf.pitWall(x, y));
        Sym.pattern(c, Geo.PX0, Geo.PIT, Geo.PZ + 1, -1, 0, Geo.PZ + 1, (x, y, z) -> Surf.pitWall(x, y));
        // pier faces in the pit section
        Sym.pattern(c, Geo.PIER_X1, Geo.PIT, -Geo.PZ, Geo.PIER_X1, 0, Geo.PZ, (x, y, z) -> Surf.pitWall(z, y));
    }

    // ------------------------------------------------------------------ floor

    /** Distance from the pit centre (0.5, 0.5) in blocks. */
    private static double rad(int x, int z) {
        return Math.hypot(x + 0.5 - 0.0, z - 0.0 + 0.5 - 0.5 + 0.0 - 0.0) ;
    }

    static boolean grateCell(int x, int z) {
        int ax = Math.abs(x + 0.5 > 0 ? x : x), az = Math.abs(z);
        // channels along x at z = +-9..10 (2 wide), with lantern pits every 6 blocks
        boolean channel = (z == 9 || z == 10 || z == -9 || z == -10) && x >= -56 && x <= 55;
        // channels across the pit at x = -30.5.. and 29.5..
        boolean cross = (x == -31 || x == -30 || x == 29 || x == 30) && Math.abs(z) <= 17;
        return channel || cross;
    }

    private static void floor(BuildContext c) {
        Sym.pattern(c, Geo.PX0, Geo.PIT, -Geo.PZ, -1, Geo.PIT, Geo.PZ, (x, y, z) -> floorTile(x, z));
        // lanterns buried under the grates (visible through the holes), spaced out so the glow stays faint
        for (int x = Geo.PX0; x <= Geo.PX1; x++) {
            for (int z = -Geo.PZ; z <= Geo.PZ; z++) {
                if (grateCell(x, z) && ((Math.floorMod(x, 6) == 0 && Math.abs(z) >= 9) || (Math.floorMod(z, 6) == 3 && Math.abs(z) < 9))) {
                    c.set(x, Geo.PIT - 1, z, Pal.SEA);
                }
            }
        }
        // the drain eye under the rope: steel rim, grating rings, lanterns beneath the inner ring
        for (int x = -8; x <= 7; x++) {
            for (int z = -8; z <= 8; z++) {
                double r = Math.hypot(x + 0.5, z);
                if (r > 7.2) {
                    continue;
                }
                String st;
                if (r > 6.2) {
                    st = Pal.IRON;
                } else if (r > 5.0) {
                    st = Pal.hazard(x + z + 100);
                } else if (r > 3.6) {
                    st = Pal.GRATE;
                    if (Math.floorMod(x + z, 3) == 0) {
                        c.set(x, Geo.PIT - 1, z, Pal.SEA);
                    }
                } else if (r > 2.6) {
                    st = Pal.BS_POLISHED;
                } else {
                    st = Pal.GRATE;
                    if (r < 1.6 || Math.floorMod(x + z, 2) == 0) {
                        c.set(x, Geo.PIT - 1, z, Pal.SEA);
                    }
                }
                c.set(x, Geo.PIT, z, st);
            }
        }
    }

    private static String floorTile(int x, int z) {
        int ax = x < 0 ? -x - 1 + 1 : x;     // distance in west-half coordinates is handled by the mirror, keep simple
        if (x <= Geo.PX0 + 1 || Math.abs(z) >= Geo.PZ - 1) {
            return Pal.hazard(x + z + 200);                 // hazard border along the walls
        }
        if (grateCell(x, z)) {
            return Pal.GRATE;
        }
        int fx = Math.floorMod(x, 10), fz = Math.floorMod(z, 10);
        if (fx == 0 || fz == 0) {
            return Pal.BS_POLISHED;
        }
        double h = Pal.hash(x, z, 17);
        if (h < 0.10) {
            return Pal.DS_CRACKED;
        }
        if (h < 0.14) {
            return Pal.DS_BRICKS;
        }
        return Pal.DS_TILES;
    }

    // ------------------------------------------------------------------ pipes and lamps on the pit walls

    private static void pipes(BuildContext c) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int zp = sgn * Geo.PZ;                 // pipes hug the wall, inside the open pit
            for (int y : new int[]{-12, -22}) {
                Sym.fill(c, Geo.PX0, y, zp, -1, y, zp, "minecraft:oxidized_copper");
                Sym.fill(c, Geo.PX0, y + 1, zp, -1, y + 1, zp, "minecraft:oxidized_copper");
                for (int x = Geo.PX0 + 4; x <= -1; x += 10) {
                    Sym.fill(c, x, y - 1, zp, x + 1, y + 2, zp, Pal.IRON);          // clamps
                }
            }
            // a vertical riser every 20 blocks
            for (int x = -50; x <= -10; x += 20) {
                Sym.fill(c, x, Geo.PIT + 1, zp, x, -10, zp, "minecraft:oxidized_copper");
            }
        }
    }

    private static void cageLamps(BuildContext c) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            int zl = sgn * Geo.PZ;
            for (int x = -55; x <= -5; x += 10) {
                for (int y : new int[]{-6, -17}) {
                    // 1x2 lantern in a cage, set into the wall face
                    Sym.fill(c, x, y, zl, x, y + 1, zl, Pal.SEA);
                    Sym.fill(c, x - 1, y - 1, zl - sgn, x + 1, y + 2, zl - sgn, Pal.BARS);
                }
            }
        }
    }

    // ------------------------------------------------------------------ debris and mattresses

    private static boolean free(BuildContext c, int x, int y, int z) {
        if (c.isSolid(x, y, z)) {
            return false;
        }
        String below = c.get(x, y - 1, z);
        return below != null && (below.contains("deepslate") || below.contains("blackstone") || below.contains("concrete"))
                && !below.contains("grate");
    }

    private static boolean keepClear(int x, int z) {
        return Math.abs(x) <= 8 && Math.abs(z) <= 8;       // the eye and the marker spot stay clean
    }

    private static void debris(BuildContext c) {
        Random r = new Random(7001);
        String[] rubble = {"minecraft:cobbled_deepslate", "minecraft:gravel", "minecraft:cobbled_deepslate_slab[type=bottom]",
                "minecraft:deepslate_brick_slab[type=bottom]", "minecraft:light_gray_concrete", "minecraft:cracked_stone_bricks"};
        int placed = 0;
        for (int i = 0; i < 400 && placed < 130; i++) {
            int x = r.nextInt(115) - 57, z = r.nextInt(35) - 17;
            if (keepClear(x, z) || !free(c, x, Geo.PIT + 1, z)) {
                continue;
            }
            int kind = r.nextInt(6);
            switch (kind) {
                case 0, 1 -> {                          // rubble mound
                    for (int dx = 0; dx <= r.nextInt(3); dx++) {
                        for (int dz = 0; dz <= r.nextInt(3); dz++) {
                            if (free(c, x + dx, Geo.PIT + 1, z + dz) && !keepClear(x + dx, z + dz)) {
                                c.set(x + dx, Geo.PIT + 1, z + dz, rubble[r.nextInt(rubble.length)]);
                            }
                        }
                    }
                    c.set(x, Geo.PIT + 1, z, rubble[r.nextInt(2)]);
                }
                case 2 -> c.set(x, Geo.PIT + 1, z, "minecraft:barrel[facing=" + (r.nextBoolean() ? "up" : "north") + "]");
                case 3 -> {                             // fallen steel beam
                    boolean alongX = r.nextBoolean();
                    int len = 4 + r.nextInt(7);
                    for (int k = 0; k < len; k++) {
                        int bx = alongX ? x + k : x, bz = alongX ? z : z + k;
                        if (free(c, bx, Geo.PIT + 1, bz) && !keepClear(bx, bz)) {
                            c.set(bx, Geo.PIT + 1, bz, Pal.STEEL);
                        }
                    }
                }
                case 4 -> {                             // broken planks and chain
                    c.set(x, Geo.PIT + 1, z, "minecraft:dark_oak_planks");
                    if (free(c, x + 1, Geo.PIT + 1, z)) {
                        c.set(x + 1, Geo.PIT + 1, z, "minecraft:dark_oak_slab[type=bottom]");
                    }
                    if (free(c, x, Geo.PIT + 1, z + 1)) {
                        c.set(x, Geo.PIT + 1, z + 1, "minecraft:chain[axis=x]");
                    }
                }
                default -> {                            // crate stack
                    c.set(x, Geo.PIT + 1, z, "minecraft:spruce_planks");
                    if (r.nextBoolean()) {
                        c.set(x, Geo.PIT + 2, z, "minecraft:dark_oak_planks");
                    }
                }
            }
            placed++;
        }
    }

    private static void mattresses(BuildContext c) {
        Random r = new Random(5150);
        String[] colors = {"white", "white", "white", "light_gray", "light_gray", "gray", "blue", "red"};
        String[] dirs = {"north", "east", "south", "west"};
        int[][] off = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int placed = 0;
        for (int i = 0; i < 600 && placed < 70; i++) {
            int x = r.nextInt(113) - 56, z = r.nextInt(33) - 16;
            int d = r.nextInt(4);
            int hx = x + off[d][0], hz = z + off[d][1];
            if (keepClear(x, z) || keepClear(hx, hz) || !free(c, x, Geo.PIT + 1, z) || !free(c, hx, Geo.PIT + 1, hz)) {
                continue;
            }
            String col = colors[r.nextInt(colors.length)];
            c.set(x, Geo.PIT + 1, z, "minecraft:" + col + "_bed[facing=" + dirs[d] + ",part=foot]");
            c.set(hx, Geo.PIT + 1, hz, "minecraft:" + col + "_bed[facing=" + dirs[d] + ",part=head]");
            placed++;
        }
    }
}
