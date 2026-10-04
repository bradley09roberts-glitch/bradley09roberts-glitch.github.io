package com.squidgame.build.arena.marbles;

import com.squidgame.build.BuildContext;

/** Small street furniture, placed in the current frame of the {@link BuildContext}. y is the block layer. */
final class Props {
    private Props() {
    }

    private static final String[] POTS = {"potted_azalea_bush", "potted_fern", "potted_red_tulip", "potted_dandelion",
            "potted_blue_orchid", "potted_cornflower", "potted_oxeye_daisy", "potted_poppy", "potted_flowering_azalea_bush",
            "potted_bamboo", "potted_oak_sapling"};
    private static final String[] COLORS = {"white", "red", "yellow", "light_blue", "pink", "lime", "orange", "cyan", "light_gray", "magenta"};

    static void barrel(BuildContext c, int x, int y, int z) {
        c.set(x, y, z, "minecraft:barrel[facing=up]");
    }

    static void crate(BuildContext c, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            c.set(x, y + i, z, i % 2 == 0 ? Mat.SPRUCE : "minecraft:oak_planks");
        }
    }

    static void pot(BuildContext c, int x, int y, int z, U.Rnd r) {
        c.set(x, y, z, "minecraft:" + r.pick(POTS));
    }

    static void jar(BuildContext c, int x, int y, int z) {
        c.set(x, y, z, "minecraft:cauldron");
    }

    static void firewood(BuildContext c, int x, int y, int z, String axis) {
        c.set(x, y, z, Mat.log("minecraft:oak_log", axis));
        c.set(x, y + 1, z, Mat.log("minecraft:oak_log", axis));
        int dx = axis.equals("x") ? 0 : 1, dz = axis.equals("x") ? 1 : 0;
        c.set(x + dx, y, z + dz, Mat.log("minecraft:spruce_log", axis));
    }

    /** Leafy planter box 1x1 (bush on a wooden box). */
    static void planter(BuildContext c, int x, int y, int z, boolean flowering) {
        c.set(x, y, z, Mat.PLANKS);
        c.set(x, y + 1, z, Mat.leaves(flowering ? "minecraft:flowering_azalea_leaves" : "minecraft:azalea_leaves"));
    }

    /** Stone lantern (seokdeung style): wall pedestal, lantern, tile cap. Light source at height y+1. */
    static void stoneLamp(BuildContext c, int x, int y, int z) {
        c.set(x, y, z, Mat.SB_WALL);
        c.set(x, y + 1, z, Mat.lantern(false));
        c.set(x, y + 2, z, Mat.slabB(Mat.SB_SL));
    }

    /** Taller lamp post: timber post with a hanging lantern on an arm (arm towards +dx/+dz as given, 0,0 = on top). */
    static void lampPost(BuildContext c, int x, int y, int z, int h) {
        c.fill(x, y, z, x, y + h - 1, z, Mat.LOG_S);
        c.set(x, y + h, z, Mat.slabB(Mat.TILE_SL));
        c.set(x, y + h - 1, z, Mat.lantern(true));
        c.set(x, y + h - 2, z, Mat.LOG_S);
    }

    /** Bicycle leaning along an axis (x or z), 3 long. */
    static void bicycle(BuildContext c, int x, int y, int z, String axis) {
        int dx = axis.equals("x") ? 1 : 0, dz = axis.equals("x") ? 0 : 1;
        String wheel = "minecraft:iron_trapdoor[facing=" + (axis.equals("x") ? "north" : "east") + ",half=bottom,open=true]";
        c.set(x, y, z, wheel);
        c.set(x + 2 * dx, y, z + 2 * dz, wheel);
        c.set(x + dx, y, z + dz, Mat.BARS);
        c.set(x + dx, y + 1, z + dz, "minecraft:black_carpet");
        c.set(x + 2 * dx, y + 1, z + 2 * dz, Mat.BARS);
    }

    /** Clothes line: chain along x from x1..x2 at (y, z) with hanging coloured cloths. */
    static void laundryX(BuildContext c, int x1, int x2, int y, int z, U.Rnd r) {
        c.fill(x1, y, z, x2, y, z, Mat.CHAIN_X);
        for (int x = x1 + 1; x < x2; x++) {
            if (r.chance(0.6)) {
                String col = r.pick(COLORS);
                c.set(x, y - 1, z, Mat.wool(col));
                if (r.chance(0.35)) {
                    c.set(x, y - 2, z, Mat.carpet(col));
                }
            }
        }
    }

    static void laundryZ(BuildContext c, int z1, int z2, int y, int x, U.Rnd r) {
        c.fill(x, y, z1, x, y, z2, Mat.CHAIN_Z);
        for (int z = z1 + 1; z < z2; z++) {
            if (r.chance(0.6)) {
                String col = r.pick(COLORS);
                c.set(x, y - 1, z, Mat.wool(col));
                if (r.chance(0.35)) {
                    c.set(x, y - 2, z, Mat.carpet(col));
                }
            }
        }
    }

    /** Wooden bench 3 long along x at z, seat facing +z (stairs facing south = back at north). */
    static void benchX(BuildContext c, int x1, int y, int z, int len, String facing) {
        for (int i = 0; i < len; i++) {
            c.set(x1 + i, y, z, Mat.stair("minecraft:spruce_stairs", facing));
        }
    }

    static String randomColor(U.Rnd r) {
        return r.pick(COLORS);
    }
}
