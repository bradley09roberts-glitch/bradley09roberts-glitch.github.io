package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * The four enormous backdrop walls: solid 6-thick cores, the painted-sky mural on the inner faces, a dark cap and
 * a lighting gantry on top (sea lanterns on chains), and a "backstage" outer side with pilasters.
 */
public final class Walls {
    private Walls() {
    }

    private static final String CORE = "minecraft:light_gray_concrete";
    private static final String OUTER = "minecraft:gray_concrete";
    private static final String RIB = "minecraft:black_concrete";
    private static final String DECK = "minecraft:gray_concrete";
    private static final String CAP = "minecraft:black_concrete";

    // outer extents of the wall ring
    private static final int OX = FACE_X + WALL_T - 1;                  // 62
    private static final int OZ0 = NEAR_FACE_Z - (WALL_T - 1);          // -13
    private static final int OZ1 = FAR_FACE_Z + (WALL_T - 1);           // 184

    public static void build(BuildContext c) {
        // foundation under everything (the whole footprint incl. the waiting room), visible as a plinth from outside
        c.fill(-OX - 1, -12, OZ0 - 19, OX + 1, -2, OZ1 + 1, "minecraft:stone");
        c.fill(-OX - 1, -3, OZ0 - 19, OX + 1, -2, OZ1 + 1, "minecraft:stone_bricks");
        // solid cores
        c.fill(-OX, -1, OZ0, OX, WALL_TOP, NEAR_FACE_Z, CORE);          // near
        c.fill(-OX, -1, FAR_FACE_Z, OX, WALL_TOP, OZ1, CORE);           // far
        c.fill(-OX, -1, OZ0, -FACE_X, WALL_TOP, OZ1, CORE);             // west
        c.fill(FACE_X, -1, OZ0, OX, WALL_TOP, OZ1, CORE);               // east

        paintMural(c);
        outerSides(c);
        topDeck(c);
        stageLights(c);
    }

    // ------------------------------------------------------------------ mural

    /** Inner-face block of loop position p: {x, z, inwardX, inwardZ}. */
    public static int[] face(int p) {
        int w = 2 * HALF_W + 1;
        int d = FIELD_Z1 - FIELD_Z0 + 1;
        p = Math.floorMod(p, PERIMETER);
        if (p < w) {
            return new int[]{-HALF_W + p, NEAR_FACE_Z, 0, 1};
        }
        p -= w;
        if (p < d) {
            return new int[]{FACE_X, FIELD_Z0 + p, -1, 0};
        }
        p -= d;
        if (p < w) {
            return new int[]{HALF_W - p, FAR_FACE_Z, 0, -1};
        }
        p -= w;
        return new int[]{-FACE_X, FIELD_Z1 - p, 1, 0};
    }

    private static void paintMural(BuildContext c) {
        Mural m = new Mural();
        for (int p = 0; p < PERIMETER; p++) {
            int[] f = face(p);
            for (int y = MURAL_Y0; y <= MURAL_Y1; y++) {
                c.set(f[0], y, f[1], m.at(p, y));
            }
            // dark cap row
            c.set(f[0], WALL_TOP, f[1], CAP);
            // base row (below the painted grass) so the mural meets the ground cleanly
            c.set(f[0], 0, f[1], "minecraft:green_concrete");
        }
        // corner columns (hidden diagonally from the field, but keep the ring closed and in the picture)
        int[][] corners = {{FACE_X, NEAR_FACE_Z}, {FACE_X, FAR_FACE_Z}, {-FACE_X, FAR_FACE_Z}, {-FACE_X, NEAR_FACE_Z}};
        for (int[] k : corners) {
            for (int y = MURAL_Y0; y <= MURAL_Y1; y++) {
                c.set(k[0], y, k[1], "minecraft:light_blue_concrete");
            }
            c.set(k[0], WALL_TOP, k[1], CAP);
        }
    }

    // ------------------------------------------------------------------ outside ("backstage")

    private static void outerSides(BuildContext c) {
        // faces
        c.fill(-OX, 0, OZ0, OX, WALL_TOP - 1, OZ0, OUTER);
        c.fill(-OX, 0, OZ1, OX, WALL_TOP - 1, OZ1, OUTER);
        c.fill(-OX, 0, OZ0, -OX, WALL_TOP - 1, OZ1, OUTER);
        c.fill(OX, 0, OZ0, OX, WALL_TOP - 1, OZ1, OUTER);
        // pilasters every 14 blocks, 2 wide, one block proud
        for (int i = -56; i <= 56; i += 14) {
            c.fill(i, 0, OZ0 - 1, i + 1, WALL_TOP, OZ0 - 1, RIB);
            c.fill(i, 0, OZ1 + 1, i + 1, WALL_TOP, OZ1 + 1, RIB);
        }
        for (int j = OZ0 + 7; j <= OZ1 - 7; j += 14) {
            c.fill(-OX - 1, 0, j, -OX - 1, WALL_TOP, j + 1, RIB);
            c.fill(OX + 1, 0, j, OX + 1, WALL_TOP, j + 1, RIB);
        }
        // horizontal bands
        for (int y : new int[]{10, 21, 32}) {
            c.fill(-OX, y, OZ0, OX, y, OZ0, "minecraft:light_gray_concrete");
            c.fill(-OX, y, OZ1, OX, y, OZ1, "minecraft:light_gray_concrete");
            c.fill(-OX, y, OZ0, -OX, y, OZ1, "minecraft:light_gray_concrete");
            c.fill(OX, y, OZ0, OX, y, OZ1, "minecraft:light_gray_concrete");
        }
    }

    // ------------------------------------------------------------------ top

    private static void topDeck(BuildContext c) {
        c.fill(-OX, WALL_TOP, OZ0, OX, WALL_TOP, NEAR_FACE_Z - 1, DECK);
        c.fill(-OX, WALL_TOP, FAR_FACE_Z + 1, OX, WALL_TOP, OZ1, DECK);
        c.fill(-OX, WALL_TOP, NEAR_FACE_Z - 1, -FACE_X - 1, WALL_TOP, FAR_FACE_Z + 1, DECK);
        c.fill(FACE_X + 1, WALL_TOP, NEAR_FACE_Z - 1, OX, WALL_TOP, FAR_FACE_Z + 1, DECK);
        // inner cap row already placed in paintMural; outer parapet (1 high) so the deck reads as a catwalk
        c.fill(-OX, WALL_TOP + 1, OZ0, OX, WALL_TOP + 1, OZ0, "minecraft:iron_bars");
        c.fill(-OX, WALL_TOP + 1, OZ1, OX, WALL_TOP + 1, OZ1, "minecraft:iron_bars");
        c.fill(-OX, WALL_TOP + 1, OZ0, -OX, WALL_TOP + 1, OZ1, "minecraft:iron_bars");
        c.fill(OX, WALL_TOP + 1, OZ0, OX, WALL_TOP + 1, OZ1, "minecraft:iron_bars");
    }

    /** Hanging stage lights: a short cantilever arm, two chains and a 3-wide light bar, every 8 blocks. */
    private static void stageLights(BuildContext c) {
        for (int p = 4; p < PERIMETER; p += 8) {
            int[] f = face(p);
            // keep clear of corners
            int a = Math.floorMod(p, PERIMETER);
            int w = 2 * HALF_W + 1, d = FIELD_Z1 - FIELD_Z0 + 1;
            int[] cuts = {0, w, w + d, 2 * w + d};
            boolean nearCorner = false;
            for (int k : cuts) {
                int dist = Math.min(Math.abs(a - k), PERIMETER - Math.abs(a - k));
                if (dist < 4) {
                    nearCorner = true;
                }
            }
            if (nearCorner) {
                continue;
            }
            int rot = f[3] == 1 ? 0 : (f[2] == -1 ? 1 : (f[3] == -1 ? 2 : 3));
            boolean warm = (p / 8) % 5 == 3;
            c.at(f[0], 0, f[1], rot, () -> {
                // local +Z is into the field, local x along the wall
                c.fill(0, WALL_TOP, 1, 0, WALL_TOP, 3, "minecraft:black_concrete");
                c.fill(-1, WALL_TOP, 3, 1, WALL_TOP, 3, "minecraft:black_concrete");
                c.fill(-1, WALL_TOP - 3, 3, -1, WALL_TOP - 1, 3, "minecraft:chain[axis=y]");
                c.fill(1, WALL_TOP - 3, 3, 1, WALL_TOP - 1, 3, "minecraft:chain[axis=y]");
                String lamp = warm ? "minecraft:shroomlight" : "minecraft:sea_lantern";
                c.fill(-1, WALL_TOP - 4, 3, 1, WALL_TOP - 4, 3, lamp);
            });
        }
    }
}
