package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.finale.Layout.*;

/**
 * The four corner floodlight towers (steel lattice legs, a guard deck and banks of sea lanterns / shroomlights aimed at
 * the court) and the judges' tower beside the court, whose roof-less deck is the spectator viewpoint.
 */
final class Towers {
    private Towers() {
    }

    static final int[][] CORNERS = {{-46, -56}, {46, -56}, {-46, 56}, {46, 56}};
    static final int DECK_Y = 19;         // deck block y (stand 20.0)

    static void build(BuildContext c) {
        for (int[] k : CORNERS) {
            final int sx = k[0] < 0 ? 1 : -1;      // lamps look towards the court centre
            final int sz = k[1] < 0 ? 1 : -1;
            c.at(k[0], 0, k[1], 0, () -> floodlight(c, sx, sz));
            // the guard on the deck stands on the outer corner, overlooking the whole yard
            c.marker("guard.post", k[0] - sx * 2 + 0.5, DECK_Y + 1.0, k[1] - sz * 2 + 0.5, sz > 0 ? 0f : 180f, "rank=triangle");
        }
        judgesTower(c, 18, 2);
    }

    /** One floodlight tower in its own frame (centre 0,0). Lamp banks face +sx (east/west) and +sz (south/north). */
    private static void floodlight(BuildContext c, int sx, int sz) {
        String leg = "minecraft:andesite_wall";
        // footing
        c.fill(-3, -2, -3, 3, 0, 3, "minecraft:stone_bricks");
        c.fill(-3, 1, -3, 3, 1, 3, "minecraft:stone_bricks");
        c.fill(-2, 2, -2, 2, 2, 2, "minecraft:polished_andesite");
        // four legs with horizontal braces every 5 blocks and X bracing
        for (int x : new int[]{-2, 2}) {
            for (int z : new int[]{-2, 2}) {
                c.fill(x, 3, z, x, DECK_Y - 1, z, leg);
            }
        }
        for (int y = 6; y < DECK_Y; y += 5) {
            for (int i = -1; i <= 1; i++) {
                c.set(i, y, -2, "minecraft:iron_bars");
                c.set(i, y, 2, "minecraft:iron_bars");
                c.set(-2, y, i, "minecraft:iron_bars");
                c.set(2, y, i, "minecraft:iron_bars");
            }
        }
        // deck 7x7 with a railing; a ladder stub that starts out of reach on the outer face
        c.fill(-3, DECK_Y, -3, 3, DECK_Y, 3, "minecraft:smooth_stone");
        c.fill(-3, DECK_Y, -3, 3, DECK_Y, -3, "minecraft:polished_blackstone");
        c.fill(-3, DECK_Y, 3, 3, DECK_Y, 3, "minecraft:polished_blackstone");
        c.fill(-3, DECK_Y, -3, -3, DECK_Y, 3, "minecraft:polished_blackstone");
        c.fill(3, DECK_Y, -3, 3, DECK_Y, 3, "minecraft:polished_blackstone");
        for (int i = -3; i <= 3; i++) {
            c.fill(i, DECK_Y + 1, -3, i, DECK_Y + 2, -3, "minecraft:iron_bars");
            c.fill(i, DECK_Y + 1, 3, i, DECK_Y + 2, 3, "minecraft:iron_bars");
            c.fill(-3, DECK_Y + 1, i, -3, DECK_Y + 2, i, "minecraft:iron_bars");
            c.fill(3, DECK_Y + 1, i, 3, DECK_Y + 2, i, "minecraft:iron_bars");
        }
        // mast carrying the lamp banks
        c.fill(-1, DECK_Y + 1, -1, 1, DECK_Y + 7, 1, "minecraft:polished_andesite");
        c.fill(0, DECK_Y + 8, 0, 0, DECK_Y + 10, 0, "minecraft:lightning_rod[facing=up]");
        // two banks (5 wide x 3 high) aimed at the court, framed in dark blackstone
        for (int i = -2; i <= 2; i++) {
            for (int dy = 0; dy < 3; dy++) {
                String lamp = (i + dy) % 2 == 0 ? "minecraft:sea_lantern" : "minecraft:shroomlight";
                c.set(i, DECK_Y + 3 + dy, sz * 2, lamp);                  // bank facing north/south
                c.set(sx * 2, DECK_Y + 3 + dy, i, lamp);                  // bank facing east/west
            }
            c.set(i, DECK_Y + 2, sz * 2, "minecraft:polished_blackstone");
            c.set(i, DECK_Y + 6, sz * 2, "minecraft:polished_blackstone");
            c.set(sx * 2, DECK_Y + 2, i, "minecraft:polished_blackstone");
            c.set(sx * 2, DECK_Y + 6, i, "minecraft:polished_blackstone");
        }
        c.set(sx * 2, DECK_Y + 3, sz * 2, "minecraft:sea_lantern");
        // a lantern at the foot so the base is never dark
        c.set(sx * 3, 2, sz * 3, "minecraft:lantern[hanging=false]");
        c.set(-sx * 3, 2, -sz * 3, "minecraft:lantern[hanging=false]");
    }

    /** Referee / judges' tower: a tall timber stand with a roofed deck at stand height 12.0 and a ladder on the south side. */
    private static void judgesTower(BuildContext c, int x0, int z0) {
        int h = 11;
        // legs
        for (int dx : new int[]{0, 4}) {
            for (int dz : new int[]{0, 4}) {
                c.fill(x0 + dx, 1, z0 + dz, x0 + dx, h, z0 + dz, "minecraft:spruce_log[axis=y]");
            }
        }
        // cross braces
        for (int y : new int[]{4, 8}) {
            for (int i = 1; i <= 3; i++) {
                c.set(x0 + i, y, z0, "minecraft:spruce_planks");
                c.set(x0 + i, y, z0 + 4, "minecraft:spruce_planks");
                c.set(x0, y, z0 + i, "minecraft:spruce_planks");
                c.set(x0 + 4, y, z0 + i, "minecraft:spruce_planks");
            }
        }
        // deck 5x5, railings, roof on four posts
        c.fill(x0 - 1, h, z0 - 1, x0 + 5, h, z0 + 5, "minecraft:spruce_planks");
        for (int i = -1; i <= 5; i++) {
            c.set(x0 + i, h + 1, z0 - 1, "minecraft:spruce_fence");
            c.set(x0 + i, h + 1, z0 + 5, "minecraft:spruce_fence");
            c.set(x0 - 1, h + 1, z0 + i, "minecraft:spruce_fence");
            c.set(x0 + 5, h + 1, z0 + i, "minecraft:spruce_fence");
        }
        c.set(x0 + 2, h + 1, z0 + 5, "minecraft:air");            // ladder gap
        for (int dx : new int[]{-1, 5}) {
            for (int dz : new int[]{-1, 5}) {
                c.fill(x0 + dx, h + 1, z0 + dz, x0 + dx, h + 4, z0 + dz, "minecraft:spruce_log[axis=y]");
            }
        }
        c.fill(x0 - 2, h + 5, z0 - 2, x0 + 6, h + 5, z0 + 6, "minecraft:spruce_slab[type=bottom]");
        c.fill(x0 - 1, h + 5, z0 - 1, x0 + 5, h + 5, z0 + 5, "minecraft:spruce_planks");
        c.set(x0 + 2, h + 4, z0 + 2, "minecraft:lantern[hanging=true]");
        // ladder on the south face up to the deck (attached to the deck-level cross brace column)
        c.fill(x0 + 2, 1, z0 + 5, x0 + 2, h - 1, z0 + 5, "minecraft:spruce_planks");
        for (int y = 1; y <= h; y++) {
            c.set(x0 + 2, y, z0 + 6, "minecraft:ladder[facing=south]");
        }
        // seat and the viewpoint marker
        c.set(x0 + 3, h + 1, z0 + 1, "minecraft:spruce_stairs[facing=west,half=bottom,shape=straight]");
        c.marker("arena.spectator", x0 + 1.5, h + 1.0, z0 + 2.5, 90f);
        c.marker("guard.post", x0 + 3.5, h + 1.0, z0 + 3.5, 90f, "rank=square");
    }
}
