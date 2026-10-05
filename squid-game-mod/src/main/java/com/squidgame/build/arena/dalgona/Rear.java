package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

/**
 * The rear (south) wall and the gallery balcony. The wall skin sits at z = -1 directly in front of the waiting
 * room prefab: wainscot and plaster, a timber portal around the gate opening (7 x 5), a wide observation window
 * either side (aligned with the prefab's glass), lockers along the foot, five arched windows above the balcony.
 * The balcony (deck block y = 10, standing height 11) runs the full width with a round VIP loge in the middle,
 * fence railing, columns, and a 3 wide stair at each end.
 *
 * <p>The wall's local frame is rotated half a turn: local x runs along -x (to the right as seen from inside),
 * z = 0 is the skin cell (world z = -1) and +z points into the hall.
 */
public final class Rear {
    private Rear() {
    }

    /** Loge: half disc of this radius bulging north from the balcony's front row. */
    private static final int LOGE_R = 7;
    /** The two stair runs: x = +-33..35, 10 steps ascending south from z = -18 (cell above step k: y = 2 + k). */
    public static final int STAIR_X0 = 33;
    public static final int STAIR_X1 = 35;
    public static final int STAIR_Z0 = -18;
    public static final int STAIR_STEPS = 10;

    public static void build(BuildContext c) {
        c.at(0, 0, Geo.REAR_WALL_Z, 2, () -> wall(c));
        balcony(c);
        stairs(c);
    }

    private static boolean inLoge(int x, int z) {
        int dz = z - Geo.BALCONY_Z0;
        return dz < 0 && x * x + dz * dz <= LOGE_R * LOGE_R;
    }

    // ---------------------------------------------------------------------------------------------
    // wall skin (local frame)

    private static void wall(BuildContext c) {
        for (int x = -35; x <= 35; x++) {
            c.set(x, 1, 0, Pal.DARK_OAK);
            boolean post = (x & 1) == 0;
            String panel = post ? Pal.log("stripped_spruce_wood", 'y') : Pal.SPRUCE;
            c.set(x, 2, 0, panel);
            c.set(x, 3, 0, panel);
            c.set(x, 4, 0, Pal.log("stripped_dark_oak_wood", 'x'));
            int top = Shell.gableTop(x);
            for (int y = 5; y <= top; y++) {
                c.set(x, y, 0, Walls.plaster(x, y, 4));
            }
            c.set(x, top, 0, Pal.DARK_OAK);
            c.set(x, top, 1, Pal.stairs("dark_oak", "north", true));
        }
        // the gate: opening 7 x 5 with a timber portal
        c.clear(-3, 1, 0, 3, 5, 0);
        for (int side = -1; side <= 1; side += 2) {
            for (int d = 4; d <= 5; d++) {
                int x = side * d;
                c.fill(x, 1, 0, x, 8, 0, Pal.log("stripped_dark_oak_wood", 'y'));
                if (d == 5) {
                    c.fill(x, 1, 1, x, 8, 1, Pal.log("stripped_dark_oak_wood", 'y'));
                }
            }
        }
        for (int x = -5; x <= 5; x++) {
            c.fill(x, 6, 0, x, 7, 0, Pal.DARK_OAK);
            c.set(x, 6, 1, Pal.stairs("dark_oak", "north", true));
            c.set(x, 8, 1, Pal.slab("dark_oak", false));
        }
        c.text(0.5, 7.0, 1.55, "DALGONA", "#ffd86a", 2.2f, 0f, false);
        // observation windows into the waiting room (the prefab's glass is right behind)
        for (int side = -1; side <= 1; side += 2) {
            int a = side > 0 ? 7 : -17;
            int b = side > 0 ? 17 : -7;
            c.clear(a, 3, 0, b, 7, 0);
            c.fill(a - 1, 3, 0, a - 1, 8, 0, Pal.log("stripped_dark_oak_wood", 'y'));
            c.fill(b + 1, 3, 0, b + 1, 8, 0, Pal.log("stripped_dark_oak_wood", 'y'));
            c.fill(a, 8, 0, b, 8, 0, Pal.DARK_OAK);
            c.fill(a, 2, 0, b, 2, 0, Pal.DARK_OAK);
            for (int x = a + 2; x <= b - 2; x += 4) {
                c.fill(x, 3, 0, x, 7, 0, Pal.log("stripped_dark_oak_wood", 'y'));
            }
        }
        // lockers
        lockers(c, -34, -19, 3);
        lockers(c, 19, 34, 3);
        lockers(c, -17, -7, 2);
        lockers(c, 7, 17, 2);
        // arched windows above the balcony, shaped to the gable
        Walls.window(c, 0, 13, 8, 1);
        Walls.window(c, -11, 13, 7, 1);
        Walls.window(c, 11, 13, 7, 1);
        Walls.window(c, -22, 13, 4, 1);
        Walls.window(c, 22, 13, 4, 1);
        // old speakers high on the wall
        Front.speaker(c, -17, 15);
        Front.speaker(c, 17, 15);
    }

    private static void lockers(BuildContext c, int x0, int x1, int height) {
        for (int x = x0; x <= x1; x++) {
            boolean dark = (Math.floorDiv(x, 2) & 1) == 0;
            String body = dark ? Pal.DARK_OAK : Pal.SPRUCE;
            c.fill(x, 1, 1, x, 2, 1, body);
            if (height >= 3) {
                c.set(x, 3, 1, "minecraft:iron_bars");
            }
            c.set(x, height + 1, 1, Pal.slab("dark_oak", false));
            if ((x & 1) == 0) {
                c.set(x, 2, 2, "minecraft:tripwire_hook[attached=false,facing=south,powered=false]");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // balcony

    private static String deckBlock(int x, int z) {
        return (z & 1) == 0 ? Pal.DARK_OAK : Pal.SPRUCE;
    }

    private static boolean deck(int x, int z) {
        return (z >= Geo.BALCONY_Z0 && z <= Geo.BALCONY_Z1 && Math.abs(x) <= 35) || inLoge(x, z);
    }

    private static void balcony(BuildContext c) {
        int y = Geo.BALCONY_FLOOR_Y;
        int z0 = Geo.BALCONY_Z0;
        // deck: main strip + round loge
        for (int z = z0 - LOGE_R; z <= Geo.BALCONY_Z1; z++) {
            for (int x = -35; x <= 35; x++) {
                if (deck(x, z)) {
                    c.set(x, y, z, deckBlock(x, z));
                }
            }
        }
        // underside: beams along x and cross beams every 6 blocks, ring beam under the loge edge
        c.fill(-35, y - 1, -3, 35, y - 1, -3, Pal.log("stripped_spruce_wood", 'x'));
        c.fill(-35, y - 1, -6, 35, y - 1, -6, Pal.log("stripped_spruce_wood", 'x'));
        for (int x = -30; x <= 30; x += 6) {
            c.fill(x, y - 1, z0 + 1, x, y - 1, -2, Pal.log("stripped_spruce_wood", 'z'));
        }
        for (int x = -35; x <= 35; x++) {
            if (Math.abs(x) > LOGE_R) {
                c.set(x, y - 1, z0, Pal.DARK_OAK);
                c.set(x, y - 2, z0, Pal.stairs("dark_oak", "south", true));
            }
        }
        for (int z = z0 - LOGE_R; z < z0; z++) {
            for (int x = -LOGE_R; x <= LOGE_R; x++) {
                if (inLoge(x, z) && !(deck(x - 1, z) && deck(x + 1, z) && deck(x, z - 1))) {
                    c.set(x, y - 1, z, Pal.DARK_OAK);
                    c.set(x, y - 2, z, Pal.slab("dark_oak", true));
                }
            }
        }
        // railing: fence + cap on every deck cell that borders open air (north, west or east), except the stair heads
        for (int z = z0 - LOGE_R; z <= Geo.BALCONY_Z1; z++) {
            for (int x = -32; x <= 32; x++) {
                if (!deck(x, z)) {
                    continue;
                }
                boolean open = !deck(x, z - 1) || !deck(x - 1, z) || !deck(x + 1, z);
                if (open) {
                    c.set(x, y + 1, z, "minecraft:dark_oak_fence");
                    c.set(x, y + 2, z, Pal.slab("dark_oak", false));
                }
            }
        }
        // columns holding the gallery
        int[][] cols = {{-32, z0}, {-24, z0}, {-16, z0}, {-LOGE_R, z0}, {LOGE_R, z0}, {16, z0}, {24, z0}, {32, z0},
                {-5, -12}, {5, -12}};
        for (int[] p : cols) {
            c.fill(p[0], 2, p[1], p[0], y - 2, p[1], Pal.log("stripped_dark_oak_wood", 'y'));
            c.set(p[0], 1, p[1], Pal.DARK_OAK);
            c.set(p[0], y - 1, p[1], Pal.DARK_OAK);
        }
        // benches against the wall and potted plants at the ends
        for (int x = -30; x <= -10; x++) {
            c.set(x, y + 1, Geo.BALCONY_Z1, Pal.stairs("spruce", "south", false));
        }
        for (int x = 10; x <= 30; x++) {
            c.set(x, y + 1, Geo.BALCONY_Z1, Pal.stairs("spruce", "south", false));
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            c.set(sx * 8, y + 1, Geo.BALCONY_Z1, "minecraft:potted_fern");
            c.set(sx * 34, y + 1, -3, "minecraft:potted_fern");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // stairs at both ends (3 wide, 10 steps, ascending south onto the deck)

    private static void stairs(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < STAIR_STEPS; k++) {
                int y = 1 + k;
                int z = STAIR_Z0 + k;
                for (int d = STAIR_X0; d <= STAIR_X1; d++) {
                    int x = side * d;
                    c.set(x, y, z, Pal.stairs("dark_oak", "south", false));
                    if (y > 1) {
                        c.fill(x, 1, z, x, y - 1, z, Pal.SPRUCE);
                    }
                }
                // stringer wall under the hand rail so the posts stand on something
                c.fill(side * (STAIR_X0 - 1), 1, z, side * (STAIR_X0 - 1), y, z, Pal.DARK_OAK);
                c.set(side * (STAIR_X0 - 1), y + 1, z, "minecraft:dark_oak_fence");
            }
            c.set(side * (STAIR_X0 - 1), 12, STAIR_Z0 + STAIR_STEPS - 1, Pal.slab("dark_oak", false));
        }
    }

    /** Markers on the balcony: the spectator viewpoint on the loge. */
    public static void markers(BuildContext c) {
        c.marker(CommonMarkers.SPECTATOR, 0.5, Geo.BALCONY_FLOOR_Y + 1.0, -11.5, 180f);
    }
}
