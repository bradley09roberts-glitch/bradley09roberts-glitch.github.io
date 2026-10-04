package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * Ground of the schoolyard: the foundation, the pale sand (noise mix, flat), the dry ground outside the rim wall and
 * the low whitewashed rim wall with its dark cap and lanterns.
 */
final class Yard {
    private Yard() {
    }

    static final String SAND = "minecraft:sand";
    static final String SMOOTH = "minecraft:smooth_sandstone";
    static final String SANDSTONE = "minecraft:sandstone";
    static final String POWDER = "minecraft:white_concrete_powder";
    static final String WARM = "minecraft:white_terracotta";

    // ------------------------------------------------------------------ ground

    static void ground(BuildContext c) {
        // solid mass below the whole enclosure so nothing floats and nobody can fall out of the world
        c.fill(BX0 - 4, -8, BZ0 - 4, BX1 + 4, -3, BZ1 + 4, "minecraft:stone");
        // outside the rim wall: dry ground (seen from the grandstand and the guard towers)
        for (int x = BX0 - 4; x <= BX1 + 4; x++) {
            for (int z = BZ0 - 4; z <= BZ1 + 4; z++) {
                if (x >= WX0 && x <= WX1 && z >= WZ0 && z <= WZ1) {
                    continue;
                }
                c.set(x, -2, z, "minecraft:dirt");
                c.set(x, -1, z, "minecraft:dirt");
                c.set(x, 0, z, outerGround(x, z));
            }
        }
        // yard under the rim wall footprint and inside it
        c.fill(WX0, -2, WZ0, WX1, -2, WZ1, SANDSTONE);
        c.fill(WX0, -1, WZ0, WX1, -1, WZ1, SAND);
        for (int x = WX0; x <= WX1; x++) {
            for (int z = WZ0; z <= WZ1; z++) {
                c.set(x, 0, z, sandAt(x, z));
            }
        }
    }

    /** Sand surface block of the yard: pale sand with hardpan patches, warm dusk stains and dusty powder away from the court. */
    static String sandAt(int x, int z) {
        double big = Noise.fbm(x * 0.06 + 11, z * 0.06 + 7, 5, 3);
        double mid = Noise.fbm(x * 0.21, z * 0.21 + 40, 9, 2);
        double h = Noise.hash01(x, z, 3);
        boolean nearCourt = x >= -HW - 4 && x <= HW + 4 && z >= COURT_TOP - 4 && z <= ZQ + 4;
        if (nearCourt) {
            // the court: calm warm sand so the white lines read clearly; hardpan patches kept subtle
            if (big > 0.58 && mid > 0.5 && h < 0.8) {
                return SMOOTH;
            }
            if (h < 0.05) {
                return SANDSTONE;
            }
            if (h < 0.065) {
                return WARM;
            }
            return SAND;
        }
        if (big > 0.60 && mid > 0.45) {
            return h < 0.9 ? SMOOTH : SAND;
        }
        if (big < 0.30 && mid > 0.52 && h < 0.40) {
            return POWDER;
        }
        if (h < 0.025) {
            return WARM;
        }
        if (h < 0.075) {
            return SANDSTONE;
        }
        return SAND;
    }

    static String outerGround(int x, int z) {
        double n = Noise.fbm(x * 0.09 + 3, z * 0.09 + 17, 21, 3);
        double h = Noise.hash01(x, z, 8);
        if (n > 0.58) {
            return h < 0.9 ? "minecraft:grass_block" : "minecraft:coarse_dirt";
        }
        if (n < 0.34) {
            return h < 0.7 ? "minecraft:sand" : "minecraft:coarse_dirt";
        }
        return h < 0.6 ? "minecraft:coarse_dirt" : (h < 0.85 ? "minecraft:dirt" : "minecraft:gravel");
    }

    // ------------------------------------------------------------------ rim wall

    static String wallBlock(int x, int y, int z) {
        double h = Noise.hash01(x * 7 + y, z * 5 + y * 3, 17);
        switch (y) {
            case 1:
                return h < 0.74 ? "minecraft:bricks" : (h < 0.90 ? "minecraft:stone_bricks" : "minecraft:mud_bricks");
            case 2:
                return h < 0.50 ? "minecraft:calcite" : (h < 0.74 ? "minecraft:white_concrete"
                        : (h < 0.86 ? "minecraft:diorite" : "minecraft:white_terracotta"));
            default:
                return h < 0.60 ? "minecraft:deepslate_tiles" : (h < 0.85 ? "minecraft:polished_blackstone" : "minecraft:deepslate_bricks");
        }
    }

    /** One straight wall piece (inclusive box, normally 2 thick), 3 blocks high on a stone-brick footing. */
    static void wallBox(BuildContext c, int x1, int z1, int x2, int z2) {
        c.fill(x1, -2, z1, x2, 0, z2, "minecraft:stone_bricks");
        c.pattern(x1, 1, z1, x2, 3, z2, (x, y, z) -> wallBlock(x, y, z));
    }

    static void rimWall(BuildContext c) {
        wallBox(c, WX0, WZ0, WX1, WZ0 + 1);           // north
        wallBox(c, WX0, WZ1 - 1, WX1, WZ1);           // south
        wallBox(c, WX0, WZ0, WX0 + 1, WZ1);           // west
        wallBox(c, WX1 - 1, WZ0, WX1, WZ1);           // east
        // corner piers, a little taller, with a lantern each
        int[][] corners = {{WX0, WZ0}, {WX1 - 2, WZ0}, {WX0, WZ1 - 2}, {WX1 - 2, WZ1 - 2}};
        for (int[] k : corners) {
            c.fill(k[0], 1, k[1], k[0] + 2, 4, k[1] + 2, "minecraft:stone_bricks");
            c.fill(k[0], 5, k[1], k[0] + 2, 5, k[1] + 2, "minecraft:deepslate_tile_slab[type=bottom]");
            c.set(k[0] + 1, 5, k[1] + 1, "minecraft:deepslate_tiles");
            c.set(k[0] + 1, 6, k[1] + 1, "minecraft:lantern[hanging=false]");
        }
        // lanterns on the wall cap every 8 blocks (inner face side)
        for (int x = -48; x <= 48; x += 8) {
            if (Math.abs(x) <= RAMP_HALF + 1 || x == 0) {
                continue;
            }
            c.set(x, 4, WZ0 + 1, "minecraft:lantern[hanging=false]");
            c.set(x, 4, WZ1 - 1, "minecraft:lantern[hanging=false]");
        }
        for (int z = -56; z <= 56; z += 8) {
            c.set(WX0 + 1, 4, z, "minecraft:lantern[hanging=false]");
            c.set(WX1 - 1, 4, z, "minecraft:lantern[hanging=false]");
        }
    }
}
