package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * The teacher's podium at the north end: a raised dark-oak stage (standing height 2) with a wide central stair
 * and side stairs, the teacher's heavy desk with lectern, plants and candles, a high-backed chair and two
 * standing lamps.
 */
public final class Stage {
    private Stage() {
    }

    private static final int X = Geo.STAGE_X;

    public static void build(BuildContext c) {
        // platform: dark oak boards with spruce stripes, trim along the edges
        for (int z = Geo.STAGE_Z0; z <= Geo.STAGE_Z1; z++) {
            for (int x = -X; x <= X; x++) {
                String s = ((z & 1) == 0) ? Pal.DARK_OAK : Pal.SPRUCE;
                if (Noise.hash(x, z, 3) < 0.08) {
                    s = Pal.DARK_OAK;
                }
                c.set(x, 1, z, s);
            }
        }
        for (int x = -X; x <= X; x++) {
            c.set(x, 1, Geo.STAGE_Z1, Pal.log("stripped_dark_oak_wood", 'x'));
        }
        for (int z = Geo.STAGE_Z0; z <= Geo.STAGE_Z1; z++) {
            c.set(-X, 1, z, Pal.log("stripped_dark_oak_wood", 'z'));
            c.set(X, 1, z, Pal.log("stripped_dark_oak_wood", 'z'));
        }
        // central stair (9 wide) and side stairs
        for (int x = -4; x <= 4; x++) {
            c.set(x, 1, Geo.STAGE_Z1 + 1, Pal.stairs("dark_oak", "north", false));
        }
        for (int z = Geo.STAGE_Z0 + 1; z <= Geo.STAGE_Z1 - 1; z++) {
            c.set(-X - 1, 1, z, Pal.stairs("dark_oak", "east", false));
            c.set(X + 1, 1, z, Pal.stairs("dark_oak", "west", false));
        }
        desk(c);
        // standing lamps at the front corners
        for (int sx = -1; sx <= 1; sx += 2) {
            int x = sx * (X - 1);
            c.fill(x, 2, Geo.STAGE_Z1 - 1, x, 4, Geo.STAGE_Z1 - 1, "minecraft:dark_oak_fence");
            c.set(x, 5, Geo.STAGE_Z1 - 1, Pal.lantern(false));
        }
    }

    private static void desk(BuildContext c) {
        int z0 = -89;
        int z1 = -88;
        c.fill(-3, 2, z0, 3, 2, z1, Pal.DARK_OAK);
        // drawer fronts
        for (int x = -3; x <= 3; x++) {
            boolean knob = (x & 1) == 0;
            c.set(x, 2, z1, knob ? Pal.SPRUCE : Pal.DARK_OAK);
        }
        c.fill(-4, 3, z0, 4, 3, z1 + 1, Pal.slab("spruce", false));
        // things on the desk
        c.set(-2, 4, z0, "minecraft:lectern[facing=north,has_book=false,powered=false]");
        c.set(3, 4, z0, "minecraft:potted_fern");
        c.set(1, 4, z1, "minecraft:candle[candles=3,lit=false]");
        c.set(-4, 4, z1 + 1, "minecraft:candle[candles=1,lit=false]");
        // the teacher's chair
        c.set(0, 2, -90, Pal.stairs("dark_oak", "north", false));
        c.set(0, 3, -90, Pal.trapdoor("dark_oak", "north", true, false));
    }
}
