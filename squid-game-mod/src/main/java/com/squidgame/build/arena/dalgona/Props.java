package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * Hall dressing: brass lamp posts down the central aisle, tub plants, two coal stoves with stovepipes, tall
 * school banners hanging from the roof ribs and old speakers on the long-wall pilasters.
 */
public final class Props {
    private Props() {
    }

    public static void build(BuildContext c) {
        aisleLamps(c);
        plants(c);
        stove(c, -28, -12);
        stove(c, 28, -12);
        banners(c);
        // old speakers on the pilasters of the long walls (west wall / east wall)
        c.at(-36, 0, -2, 3, () -> {
            Front.speaker(c, 30, 14);
            Front.speaker(c, 70, 14);
        });
        c.at(36, 0, -92, 1, () -> {
            Front.speaker(c, 20, 14);
            Front.speaker(c, 60, 14);
        });
    }

    /** A lamp post on both sides of the runner at every rib. */
    private static void aisleLamps(BuildContext c) {
        for (int p = 1; p <= 8; p++) {
            int z = Roof.ribZ(p);
            for (int sx = -1; sx <= 1; sx += 2) {
                int x = sx * 3;
                c.set(x, 1, z, Pal.slab("dark_oak", false));
                c.fill(x, 2, z, x, 4, z, "minecraft:dark_oak_fence");
                c.set(x, 5, z, Pal.lantern(false));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Tub plant: terracotta pot, trunk and a round crown of azalea leaves with a few blossoms. */
    public static void plant(BuildContext c, int x, int y, int z, int trunk) {
        int cy = y + trunk + 2;
        String leaves = "minecraft:azalea_leaves[distance=7,persistent=true,waterlogged=false]";
        String bloom = "minecraft:flowering_azalea_leaves[distance=7,persistent=true,waterlogged=false]";
        c.sphere(x, cy, z, 2.0, leaves, false);
        c.pattern(x - 2, cy - 2, z - 2, x + 2, cy + 2, z + 2, (px, py, pz) -> {
            String s = c.get(px, py, pz);
            if (s != null && s.startsWith("minecraft:azalea_leaves") && Noise.hash(px, py, pz, 13) < 0.22) {
                return bloom;
            }
            return null;
        });
        c.set(x, y, z, "minecraft:terracotta");
        for (int k = 1; k <= trunk + 1; k++) {
            c.set(x, y + k, z, "minecraft:jungle_log[axis=y]");
        }
    }

    private static void plants(BuildContext c) {
        int[][] spots = {{-30, -14}, {30, -14}, {-18, -89}, {18, -89}, {-9, -4}, {9, -4}, {-33, -52}, {33, -52},
                {-33, -78}, {33, -78}};
        for (int[] s : spots) {
            plant(c, s[0], 1, s[1], 2);
        }
        // two taller ones on the stage
        plant(c, -13, 2, -90, 2);
        plant(c, 13, 2, -90, 2);
    }

    // ---------------------------------------------------------------------------------------------

    /** Round coal stove: cast iron body, glowing fire window, stovepipe up to the roof. */
    private static void stove(BuildContext c, int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double r = Math.hypot(dx, dz);
                if (r <= 1.6) {
                    c.fill(cx + dx, 1, cz + dz, cx + dx, 3, cz + dz, "minecraft:gray_concrete");
                    c.set(cx + dx, 4, cz + dz, "minecraft:polished_blackstone_slab[type=bottom]");
                } else if (r <= 2.3 && r > 1.6) {
                    c.set(cx + dx, 1, cz + dz, "minecraft:polished_blackstone_slab[type=bottom]");
                }
            }
        }
        // fire window faces the middle of the hall
        int fx = cx < 0 ? 1 : -1;
        c.set(cx + fx, 2, cz, "minecraft:magma_block");
        c.set(cx + fx, 3, cz, "minecraft:iron_bars");
        c.set(cx, 4, cz, "minecraft:polished_blackstone_slab[type=top]");
        int top = (int) Math.floor(Geo.roofUnder(cx) - 0.001) - 1;
        for (int y = 5; y <= top; y++) {
            c.set(cx, y, cz, "minecraft:polished_blackstone_wall");
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static final String[] CLOTH = {"squidgame:pastel_peach", "squidgame:pastel_pink", "squidgame:pastel_yellow",
            "squidgame:pastel_mint"};

    /** Tall banners hung from the ribs (every other rib), two over the aisle edges. */
    private static void banners(BuildContext c) {
        int k = 0;
        for (int p = 2; p <= 6; p += 2) {         // not the ribs near the board
            int z = Roof.ribZ(p);
            for (int sx = -1; sx <= 1; sx += 2) {
                banner(c, sx * 6, z, CLOTH[k % CLOTH.length], k % 4);
                k++;
            }
        }
    }

    /** 3 wide, 8 tall banner with a swallow-tail and a candy-shape icon, hanging in the plane of the rib. */
    private static void banner(BuildContext c, int cx, int z, String cloth, int icon) {
        int top = Roof.ribTop(cx) - 2;       // just under the rib
        // hanging bar and cords
        c.fill(cx - 2, top, z, cx + 2, top, z, Pal.log("stripped_dark_oak_wood", 'x'));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 8; dy++) {
                boolean tail = dy >= 7 && dx == 0;
                if (!tail) {
                    c.set(cx + dx, top - dy, z, cloth);
                }
            }
        }
        // icon (3x3) in honey brown
        String ink = "minecraft:orange_terracotta";
        int iy = top - 4;
        switch (icon) {
            case 0 -> {      // circle
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx != 0 || dy != 0) {
                            c.set(cx + dx, iy + dy, z, ink);
                        }
                    }
                }
            }
            case 1 -> {      // triangle
                c.set(cx, iy + 1, z, ink);
                for (int dx = -1; dx <= 1; dx++) {
                    c.set(cx + dx, iy, z, ink);
                    c.set(cx + dx, iy - 1, z, ink);
                }
            }
            case 2 -> {      // star
                c.set(cx, iy + 1, z, ink);
                c.set(cx, iy - 1, z, ink);
                for (int dx = -1; dx <= 1; dx++) {
                    c.set(cx + dx, iy, z, ink);
                }
            }
            default -> {     // umbrella
                for (int dx = -1; dx <= 1; dx++) {
                    c.set(cx + dx, iy + 1, z, ink);
                }
                c.set(cx, iy, z, ink);
                c.set(cx, iy - 1, z, ink);
                c.set(cx - 1, iy - 1, z, ink);
            }
        }
    }
}
