package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * The bare building: foundation, carved interior, the shallow gabled roof deck with half-block steps and the
 * solid walls (long walls 4 thick, gable end walls). Detail (panels, windows, trusses...) is added by the other
 * helpers afterwards.
 */
public final class Shell {
    private Shell() {
    }

    private static final String SKIN = Pal.CREAM;

    public static void build(BuildContext c) {
        foundation(c);
        c.clear(-35, 1, Geo.Z_FRONT, 35, 26, Geo.Z_REAR);
        floorBase(c);
        roofDeck(c);
        // walls are written after the deck so they win where the overhanging deck overlaps them
        c.fill(-39, 1, -95, -36, Geo.EAVES, 2, SKIN);
        c.fill(36, 1, -95, 39, Geo.EAVES, 2, SKIN);
        for (int x = -35; x <= 35; x++) {
            int top = gableTop(x);
            c.fill(x, 1, -95, x, top, Geo.FRONT_WALL_Z, SKIN);
            c.fill(x, 1, Geo.REAR_WALL_Z, x, top, 2, SKIN);
        }
    }

    /** Highest full wall cell of a gable end wall at column x (reaches the roof underside). */
    public static int gableTop(int x) {
        return ((Geo.roofUnder2(x) + 1) >> 1) - 1;
    }

    private static void foundation(BuildContext c) {
        c.noise(-41, -3, -96, 41, -1, 3,
                new String[]{Pal.STONE_BRICKS, "minecraft:cracked_stone_bricks", "minecraft:andesite", "minecraft:stone"},
                new double[]{70, 12, 10, 8});
    }

    private static void floorBase(BuildContext c) {
        c.fill(-36, 0, -92, 36, 0, -1, Pal.SPRUCE);
    }

    /** Roof deck with half-block steps: planks underneath, dark oak core, deepslate tiles on top. */
    private static void roofDeck(BuildContext c) {
        for (int x = -42; x <= 42; x++) {
            int under2 = Geo.roofUnder2(x);
            int top2 = under2 + 6;
            int y0 = under2 >> 1;
            int y1 = (top2 + 1) >> 1;
            for (int y = y0; y <= y1; y++) {
                int lo = Math.max(under2, 2 * y);
                int hi = Math.min(top2, 2 * y + 2);
                if (hi <= lo) {
                    continue;
                }
                int depth = lo - under2;            // half blocks below the visible underside
                String mat;
                String slabMat;
                if (depth < 2) {
                    boolean dark = Noise.hash(x, 0, 3, 17) < 0.18;
                    mat = dark ? Pal.DARK_OAK : Pal.SPRUCE;
                    slabMat = dark ? "dark_oak" : "spruce";
                } else if (depth < 4) {
                    mat = Pal.DARK_OAK;
                    slabMat = "dark_oak";
                } else {
                    double r = Noise.hash(x, y, 0, 29);
                    boolean brick = r < 0.2;
                    boolean ridge = Math.abs(x) <= 1 && depth >= 4;
                    mat = ridge ? Pal.STONE_BRICKS : brick ? "minecraft:deepslate_bricks" : Pal.DEEPSLATE_TILES;
                    slabMat = ridge ? "stone_brick" : brick ? "deepslate_brick" : "deepslate_tile";
                }
                String state;
                if (hi - lo >= 2) {
                    state = mat;
                } else if (lo == 2 * y) {
                    state = Pal.slab(slabMat, false);
                } else {
                    state = Pal.slab(slabMat, true);
                }
                c.fill(x, y, -96, x, y, 3, state);
            }
        }
    }
}
