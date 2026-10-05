package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * The masked guards' formation: armed triangles in two long rows along both side walls (just outside the painted
 * boundary lines) and in a line behind the start, square-masked managers on small raised podiums (3x3, 3 high) at
 * x = +-30, z = 100, circle workers near the start line and the safe zone. 24 posts here (+5 from the waiting room
 * prefab).
 *
 * <p>The podiums are the only solid things inside the field. NPC lanes start at the spawn slot's x (plus a small
 * random offset) and the slots are numbered centre-out, so with the default roster (128) every lane stays within
 * |x| &lt;= 26 and never meets them; with very large rosters an NPC may sidestep around one (the NPC "stuck" logic
 * handles that).
 */
public final class Guards {
    private Guards() {
    }

    private static final String BLOCK = "minecraft:polished_blackstone_bricks";
    private static final String TRIM = "minecraft:orange_concrete";

    /** Z and |x| of the podium centres. */
    private static final int PODIUM_Z = 100;
    private static final int PODIUM_X = 30;

    // ------------------------------------------------------------------ podiums (blocks)

    public static void build(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            int x = side > 0 ? PODIUM_X : -PODIUM_X;
            c.at(x, 0, PODIUM_Z, 0, () -> podium(c));
        }
    }

    /**
     * Local frame: origin = podium centre at floor level. Body x,z in [-1,1], y 1..3 (stand height 4.0), a 3-step
     * stair flight on the north side (z -4..-2) climbing towards +Z, an iron-bar rail on the other three sides.
     */
    private static void podium(BuildContext c) {
        c.fill(-1, 1, -1, 1, 3, 1, BLOCK);
        c.fill(-1, 3, -1, 1, 3, 1, "minecraft:black_concrete");
        c.fill(-1, 2, -1, 1, 2, -1, TRIM);
        // recessed lights in the side faces
        c.set(-1, 2, 0, "minecraft:sea_lantern");
        c.set(1, 2, 0, "minecraft:sea_lantern");
        c.set(0, 2, 1, "minecraft:sea_lantern");
        // stair flight
        String stair = "minecraft:polished_blackstone_brick_stairs[facing=south,half=bottom,shape=straight]";
        for (int x = -1; x <= 1; x++) {
            c.set(x, 1, -4, stair);
            c.set(x, 1, -3, BLOCK);
            c.set(x, 2, -3, stair);
            c.set(x, 1, -2, BLOCK);
            c.set(x, 2, -2, BLOCK);
            c.set(x, 3, -2, stair);
        }
        // rail around the top (leaves the stair side open and the centre free)
        for (int i = -1; i <= 1; i++) {
            c.set(i, 4, 1, "minecraft:iron_bars");
            c.set(-1, 4, i, "minecraft:iron_bars");
            c.set(1, 4, i, "minecraft:iron_bars");
        }
        c.clear(0, 4, 0, 0, 6, 0);
        c.clear(-1, 4, -1, 1, 6, -1);
    }

    // ------------------------------------------------------------------ guard.post markers

    public static void markers(BuildContext c) {
        int[] rowZ = {22, 39, 56, 73, 90, 107, 124};
        for (int z : rowZ) {
            post(c, 54.5, 1.0, z + 0.5, 90f, "triangle");
            post(c, mxd(54.5), 1.0, z + 0.5, -90f, "triangle");
        }
        // line behind the start (on the apron in front of the near wall)
        for (double x : new double[]{19.5, 33.5}) {
            post(c, x, 1.0, -4.5, 0f, "triangle");
            post(c, mxd(x), 1.0, -4.5, 0f, "triangle");
        }
        // managers on the podiums
        post(c, PODIUM_X + 0.5, 4.0, PODIUM_Z + 0.5, 90f, "square");
        post(c, mxd(PODIUM_X + 0.5), 4.0, PODIUM_Z + 0.5, -90f, "square");
        // workers near the start line and the safe zone
        post(c, 54.5, 1.0, 17.5, 90f, "circle");
        post(c, mxd(54.5), 1.0, 17.5, -90f, "circle");
        post(c, 54.5, 1.0, 150.5, 90f, "circle");
        post(c, mxd(54.5), 1.0, 150.5, -90f, "circle");
    }

    private static double mxd(double x) {
        return Layout.mxd(x);
    }

    private static void post(BuildContext c, double x, double y, double z, float yaw, String rank) {
        c.marker(CommonMarkers.GUARD_POST, x, y, z, yaw, "rank=" + rank);
    }
}
