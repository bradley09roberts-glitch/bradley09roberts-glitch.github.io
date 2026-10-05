package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * The winners' podium in the middle of the hall, under the hanging pig: three black / white / gold tiers (surfaces at
 * y = 1, 2, 3), a broad stair on the south (entrance) side, the three symbol blocks on the north risers and glowing
 * strips in every riser. Marker {@code hub.podium} sits on the top tier, yaw 0 (faces the entrance).
 *
 * <p>The ceremony places contestants at the marker + (0..3, 0..4) * 1.5 blocks (east and south of it), so the marker is
 * the north-west part of the 11 x 7 top tier.
 */
final class Podium {
    private Podium() {
    }

    private static final String QUARTZ = "minecraft:quartz_block";
    private static final String STAIR = "minecraft:quartz_stairs";

    static void build(BuildContext c) {
        // tier 1: black plinth with a pink rim and black-and-white checker
        c.fill(-7, 0, -5, 7, 0, 5, Pal.BLACK);
        c.pattern(-7, 0, -5, 7, 0, 5, (x, y, z) -> {
            boolean rim = Math.abs(x) == 7 || Math.abs(z) == 5;
            if (rim) {
                return Pal.PINK;
            }
            return ((x + z) & 1) == 0 ? Pal.TILE_BLACK : Pal.TILE_WHITE;
        });
        // risers of tier 1 are black: the plinth sides get a dark skin one block below the rim
        for (int x = -7; x <= 7; x++) {
            c.set(x, -1, -5, Pal.BLACK);
            c.set(x, -1, 5, Pal.BLACK);
        }
        // tier 2: white quartz with a gold rim
        c.fill(-6, 1, -4, 6, 1, 4, QUARTZ);
        c.pattern(-6, 1, -4, 6, 1, 4, (x, y, z) -> {
            if (Math.abs(x) == 6 || Math.abs(z) == 4) {
                return Pal.GOLD;
            }
            return ((x + z) & 1) == 0 ? QUARTZ : "minecraft:smooth_quartz";
        });
        // tier 3: pink and white winners' tiles with a gold rim
        c.fill(-5, 2, -3, 5, 2, 3, QUARTZ);
        c.pattern(-5, 2, -3, 5, 2, 3, (x, y, z) -> {
            if (Math.abs(x) == 5 || Math.abs(z) == 3) {
                return Pal.GOLD;
            }
            return ((x + z) & 1) == 0 ? Pal.TILE_WHITE : Pal.TILE_PINK;
        });
        // risers: dark steel skirt under tier 2 and 3 so the tiers read as stacked
        for (int x = -6; x <= 6; x++) {
            c.set(x, 1, -4, Pal.BLACK);
            c.set(x, 1, 4, Pal.BLACK);
        }
        for (int z = -4; z <= 4; z++) {
            c.set(-6, 1, z, Pal.BLACK);
            c.set(6, 1, z, Pal.BLACK);
        }
        // the three symbols on the north risers (the way on to the games)
        c.set(-2, 1, -4, "squidgame:symbol_circle");
        c.set(0, 1, -4, "squidgame:symbol_triangle");
        c.set(2, 1, -4, "squidgame:symbol_square");
        // stair on the south side: floor -> tier 1 -> tier 2 -> tier 3
        for (int i = 0; i < 3; i++) {
            c.fill(-3, i, 6 - i, 3, i, 6 - i, Pal.stair(STAIR, "north", false));
        }
        // glowing strips in the risers
        for (int z : new int[]{-3, 0, 3}) {
            c.set(-7, 0, z, Pal.PANEL_PINK);
            c.set(7, 0, z, Pal.PANEL_PINK);
        }
        for (int x : new int[]{-5, -1, 5, 1}) {
            c.set(x, 0, -5, Pal.PANEL_PINK);
        }
        for (int x : new int[]{-6, 6}) {
            c.set(x, 0, 5, Pal.PANEL_PINK);
        }
        for (int z : new int[]{-2, 2}) {
            c.set(-6, 1, z, Pal.SEA);
            c.set(6, 1, z, Pal.SEA);
        }
        for (int x : new int[]{-4, 4}) {
            c.set(x, 1, -4, Pal.SEA);
        }
        for (int z : new int[]{-1, 1}) {
            c.set(-5, 2, z, Pal.SEA);
            c.set(5, 2, z, Pal.SEA);
        }
        c.marker("hub.podium", -1.5, 3.0, -2.5, 0f);
    }
}
