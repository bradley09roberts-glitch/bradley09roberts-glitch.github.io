package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * Film-set and playground props that live in the margins beside the field (|x| >= 54) or in the safe zone: camera
 * booms (black blocks on arms), loudspeakers on tall poles, benches, orange cones and chalk drawings. Nothing here
 * stands in the running lanes (|x| <= 53) below head height; the booms only reach inward high above the ground.
 */
public final class Props {
    private Props() {
    }

    private static final String BLACK = "minecraft:black_concrete";
    private static final String GRAY = "minecraft:gray_concrete";
    private static final String LGRAY = "minecraft:light_gray_concrete";

    public static void build(BuildContext c) {
        sideProps(c);
        loungeAndCones(c);
        chalk(c);
        c.text(0.5, 4.0, START_LINE_Z + 0.4, "START", "white", 5f, 0f, false);
    }

    // ------------------------------------------------------------------ side margins

    private static void sideProps(BuildContext c) {
        int[] speakerZ = {30, 64, 115};
        int[] cameraZ = {47, 81, 132};
        for (int side = -1; side <= 1; side += 2) {
            int x = side > 0 ? 55 : -55;
            int rot = side > 0 ? 1 : 3;
            for (int z : speakerZ) {
                c.at(x, 0, z, rot, () -> speakerPole(c));
            }
            for (int z : cameraZ) {
                c.at(x, 0, z, rot, () -> cameraBoom(c));
            }
        }
    }

    /** Local frame: origin = base block, +Z faces the field. 15 high loudspeaker array on a pole. */
    private static void speakerPole(BuildContext c) {
        c.fill(-1, 1, -1, 1, 1, 1, GRAY);
        c.fill(0, 2, 0, 0, 14, 0, LGRAY);
        // cross arm and two horn boxes (3 wide) facing +Z, mirrored about the pole
        c.fill(-4, 15, 0, 4, 15, 0, BLACK);
        for (int s = -1; s <= 1; s += 2) {
            int x1 = s < 0 ? -4 : 2;
            c.fill(x1, 16, 0, x1 + 2, 18, 1, BLACK);
            c.fill(x1, 16, 2, x1 + 2, 18, 2, GRAY);
            c.set(x1 + 1, 17, 2, BLACK);
            c.set(x1 + 1, 17, 3, LGRAY);
        }
        // indicator light on top of the pole
        c.set(0, 15, 1, "minecraft:red_concrete");
        c.set(0, 15, 0, BLACK);
    }

    /** Local frame: origin = base block, +Z faces the field. A camera on a swivel arm, 9 high. */
    private static void cameraBoom(BuildContext c) {
        c.fill(-1, 1, -1, 1, 1, 1, GRAY);
        c.fill(0, 2, 0, 0, 8, 0, LGRAY);
        c.set(0, 9, 0, "minecraft:iron_block");
        // arm: black beam with a counterweight behind
        c.fill(0, 9, 1, 0, 9, 4, BLACK);
        c.fill(0, 9, -2, 0, 9, -1, BLACK);
        c.set(0, 8, -2, GRAY);
        c.set(0, 10, -2, GRAY);
        // camera body (3 wide, 2 tall, 3 deep) with lens, viewfinder and REC light
        c.fill(-1, 9, 5, 1, 10, 7, BLACK);
        c.set(0, 9, 8, "minecraft:blue_stained_glass");
        c.set(0, 10, 8, BLACK);
        c.set(0, 11, 6, GRAY);
        c.set(1, 11, 5, "minecraft:red_concrete");
        c.set(-1, 11, 7, LGRAY);
        // cable down the pole
        c.fill(1, 2, 0, 1, 8, 0, "minecraft:chain[axis=y]");
    }

    // ------------------------------------------------------------------ safe zone lounges, benches, cones

    private static void loungeAndCones(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            int cx = side * 25;
            // three benches in a U around an open centre (the survivors gather here)
            c.at(cx - 2, 0, 151, 0, () -> bench(c, 5));
            c.at(cx + 2, 0, 166, 2, () -> bench(c, 5));
            if (side > 0) {
                c.at(cx + 9, 0, 154, 1, () -> bench(c, 5));
            } else {
                c.at(cx - 9, 0, 158, 3, () -> bench(c, 5));
            }
            // cones at the corners of the lounge
            for (int dz : new int[]{149, 168}) {
                for (int dx : new int[]{-8, 8}) {
                    cone(c, cx + dx, dz);
                }
            }
        }
        // cones where the lines end, outside the lanes
        for (int side = -1; side <= 1; side += 2) {
            int x = side * 54;
            cone(c, x, START_LINE_Z - 1);
            cone(c, x, START_LINE_Z + 2);
            cone(c, x, FINISH_LINE_Z - 1);
            cone(c, x, FINISH_LINE_Z + 2);
        }
    }

    /** A bench of the given length along +X (local), seat 1 block above the floor, facing +Z. */
    private static void bench(BuildContext c, int len) {
        for (int i = 0; i < len; i++) {
            c.set(i, 1, 0, "minecraft:spruce_slab[type=bottom]");
            c.set(i, 1, -1, "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]");
        }
        c.set(0, 1, 0, "minecraft:dark_oak_log[axis=y]");
        c.set(len - 1, 1, 0, "minecraft:dark_oak_log[axis=y]");
        c.set(0, 2, 0, "minecraft:dark_oak_slab[type=bottom]");
        c.set(len - 1, 2, 0, "minecraft:dark_oak_slab[type=bottom]");
    }

    private static void cone(BuildContext c, int x, int z) {
        c.set(x, 1, z, "minecraft:orange_concrete");
        c.set(x, 2, z, "minecraft:white_concrete");
        c.set(x, 3, z, "minecraft:orange_concrete");
    }

    // ------------------------------------------------------------------ chalk

    /** Hopscotch ladders and round markings in the side margins at the start end, flush with the ground. */
    private static void chalk(BuildContext c) {
        String w = "minecraft:white_concrete";
        for (int side = -1; side <= 1; side += 2) {
            int x0 = side > 0 ? 54 : -56;       // 3 wide strip x0..x0+2
            // ladder: side lines and rungs every 3
            for (int i = 0; i < 6; i++) {
                int z0 = -4 + i * 3;
                c.fill(x0, 0, z0, x0 + 2, 0, z0, w);
            }
            c.fill(x0, 0, -4, x0, 0, 14, w);
            c.fill(x0 + 2, 0, -4, x0 + 2, 0, 14, w);
        }
    }
}
