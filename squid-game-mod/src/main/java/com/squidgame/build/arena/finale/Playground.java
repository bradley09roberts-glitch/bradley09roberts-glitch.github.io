package com.squidgame.build.arena.finale;

import com.squidgame.build.BuildContext;

/**
 * Lays out the old playground props around the court (never inside the 6 block belt): the west strip is the play
 * area proper (swings, slides, jungle gym, monkey bars, see-saws, sandbox, merry-go-round, tyres, the big old tree),
 * the east strip holds low props that do not block the view from the grandstand, the north forecourt and the south
 * approach carry benches, lamps, trees and the wash stand. The straight route from the gate to the square (|x| <= 12)
 * stays completely clear.
 */
final class Playground {
    private Playground() {
    }

    private static void put(BuildContext c, int x, int z, int rot, Runnable body) {
        c.at(x, 0, z, rot, body);
    }

    static void build(BuildContext c) {
        west(c);
        east(c);
        forecourt(c);
        approach(c);
    }

    // ------------------------------------------------------------------ west strip: the play area

    private static void west(BuildContext c) {
        // north-west: jungle gym and two slides
        put(c, -43, -38, 0, () -> Props.jungleGym(c, "minecraft:red_terracotta"));
        put(c, -34, -44, 0, () -> Props.slide(c, "minecraft:yellow_terracotta"));
        put(c, -27, -42, 0, () -> Props.slide(c, "minecraft:blue_terracotta"));
        // swings and the merry-go-round
        put(c, -47, -22, 0, () -> Props.swingSet(c, 3));
        put(c, -28, -22, 0, () -> Props.merryGoRound(c));
        // monkey bars (green frame) and see-saws
        put(c, -34, -13, 0, () -> Props.monkeyBars(c, 8, "minecraft:green_terracotta"));
        put(c, -46, -6, 0, () -> Props.seesaw(c));
        put(c, -42, -6, 0, () -> Props.seesaw(c));
        put(c, -38, -6, 0, () -> Props.seesaw(c));
        // sandbox, tyres
        put(c, -30, 2, 0, () -> Props.sandbox(c));
        put(c, -47, 9, 0, () -> Props.tyreArches(c, 5));
        put(c, -22, 6, 0, () -> Props.tyreRing(c, 2));
        // the old tree with its tyre swing and lamps
        Tree.bigOak(c, -35, 24);
        // benches facing the court
        put(c, -17, -20, 3, () -> Props.bench(c, 6));
        put(c, -17, 17, 3, () -> Props.bench(c, 6));
        // football goal at the far west wall
        put(c, -48, 38, 1, () -> Props.goal(c));
        // lamp posts along the belt
        for (int z = -28; z <= 28; z += 14) {
            put(c, -15, z, 0, () -> Props.lampPost(c, 6));
        }
    }

    // ------------------------------------------------------------------ east strip: low props in front of the grandstand

    private static void east(BuildContext c) {
        put(c, 24, -40, 0, () -> Props.seesaw(c));
        put(c, 28, -40, 0, () -> Props.seesaw(c));
        put(c, 32, -40, 0, () -> Props.seesaw(c));
        put(c, 40, -28, 2, () -> Props.slide(c, "minecraft:red_terracotta"));
        put(c, 26, -20, 0, () -> Props.swingSet(c, 2));
        put(c, 46, -12, 1, () -> Props.hopscotch(c));
        put(c, 24, -6, 0, () -> Props.tyreRing(c, 5));
        put(c, 24, 16, 0, () -> Props.monkeyBars(c, 10, "minecraft:blue_terracotta"));
        put(c, 38, 8, 0, () -> Props.jungleGym(c, "minecraft:blue_terracotta"));
        put(c, 25, 25, 0, () -> Props.tyreArches(c, 4));
        // benches along the east wall, facing the court
        for (int z : new int[]{-30, -22, -9, 10, 20, 29}) {
            put(c, 48, z, 1, () -> Props.bench(c, 6));
        }
        // marbles ring painted in the sand
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 3.25 && d > 2.25) {
                    c.set(34 + dx, 0, 40 + dz, "minecraft:white_concrete");
                }
            }
        }
        for (int z = -28; z <= 28; z += 14) {
            if (z == 0 || z == -14) {
                continue;
            }
            put(c, 15, z, 2, () -> Props.lampPost(c, 6));
        }
    }

    // ------------------------------------------------------------------ north forecourt

    private static void forecourt(BuildContext c) {
        put(c, -30, -48, 0, () -> Props.fountain(c));
        put(c, 24, -47, 0, () -> Props.bench(c, 6));
        put(c, 32, -47, 0, () -> Props.bench(c, 6));
        put(c, -13, -41, 0, () -> Props.bench(c, 5));
        put(c, 9, -41, 0, () -> Props.bench(c, 5));
        for (int x : new int[]{-12, 12}) {
            put(c, x, -36, 0, () -> Props.lampPost(c, 6));
        }
        Tree.blossom(c, -45, -47, 9, true);
        Tree.blossom(c, 45, -47, 9, true);
    }

    // ------------------------------------------------------------------ south approach (keep x in [-12,12] clear)

    private static void approach(BuildContext c) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            final int s = sgn;
            // benches beside the route, facing it (east side faces west, west side faces east)
            put(c, s * 17, s > 0 ? 38 : 43, s > 0 ? 1 : 3, () -> Props.bench(c, 6));
            put(c, s * 17, s > 0 ? 50 : 55, s > 0 ? 1 : 3, () -> Props.bench(c, 6));
            Tree.blossom(c, s * 24, 44, 8, true);
            Tree.blossom(c, s * 40, 52, 8, false);
            put(c, s * 28, 56, 0, () -> Props.tyreArches(c, 3));
            put(c, s * 14, 36, s > 0 ? 2 : 0, () -> Props.lampPost(c, 6));
            put(c, s * 14, 52, s > 0 ? 2 : 0, () -> Props.lampPost(c, 6));
        }
    }
}
