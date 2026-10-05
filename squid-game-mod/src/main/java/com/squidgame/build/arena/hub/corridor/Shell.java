package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.corridor.Plan.Space;

/**
 * Builds the solid mass of the compound, carves every space out of it and skins floors, ceilings and walls with the
 * patterns of {@link Surfaces}. Doors are cut afterwards by {@link Doors}.
 */
final class Shell {
    private Shell() {
    }

    static void build(BuildContext c) {
        // plinth and solid mass (the outer faces are only seen from outside the hub)
        c.fill(Plan.X0, -6, Plan.Z0, Plan.X1, -2, Plan.Z1, "minecraft:polished_blackstone_bricks");
        c.fill(Plan.X0, -1, Plan.Z0, Plan.X1, Plan.ROOF_Y, Plan.Z1, Pal.GRAY);
        // roof cap and a pink band round the outside
        c.fill(Plan.X0, Plan.ROOF_Y, Plan.Z0, Plan.X1, Plan.ROOF_Y, Plan.Z1, Pal.BLACK);
        for (int x = Plan.X0; x <= Plan.X1; x++) {
            c.set(x, 8, Plan.Z0, Pal.PINK);
            c.set(x, 8, Plan.Z1, Pal.PINK);
        }
        for (int z = Plan.Z0; z <= Plan.Z1; z++) {
            c.set(Plan.X0, 8, z, Pal.PINK);
            c.set(Plan.X1, 8, z, Pal.PINK);
        }

        // carve + floor + ceiling (later spaces override earlier ones)
        for (Space s : Plan.ALL) {
            c.fill(s.x0, 0, s.z0, s.x1, s.h - 1, s.z1, Pal.AIR);
        }
        for (Space s : Plan.ALL) {
            for (int x = s.x0; x <= s.x1; x++) {
                for (int z = s.z0; z <= s.z1; z++) {
                    if (Plan.at(x, z) != s) {
                        continue;
                    }
                    c.set(x, Plan.FLOOR_Y, z, Surfaces.floor(s, x, z));
                    c.set(x, s.h, z, Surfaces.ceiling(s, x, z));
                }
            }
        }
        // walls: rooms first, then halls, then corridors so that corridor patterns win the shared walls
        for (int pass = 0; pass < 3; pass++) {
            for (Space s : Plan.ALL) {
                int p = switch (s.kind) {
                    case ROOM -> 0;
                    case HALL, CORNER -> 1;
                    default -> 2;
                };
                if (p == pass) {
                    paintWalls(c, s);
                }
            }
        }
    }

    /** Paints the four wall rows around a space (cells that belong to any space are skipped: openings, junctions). */
    private static void paintWalls(BuildContext c, Space s) {
        // north wall (z0-1), room is to the south of it: dir S=2
        for (int x = s.x0; x <= s.x1; x++) {
            paintColumn(c, s, x, s.z0 - 1, x, 2);
            paintColumn(c, s, x, s.z1 + 1, x, 0);
        }
        for (int z = s.z0; z <= s.z1; z++) {
            paintColumn(c, s, s.x0 - 1, z, z, 1);
            paintColumn(c, s, s.x1 + 1, z, z, 3);
        }
    }

    private static void paintColumn(BuildContext c, Space s, int x, int z, int u, int dir) {
        if (Plan.at(x, z) != null) {
            return;
        }
        for (int y = 0; y < s.h; y++) {
            c.set(x, y, z, Surfaces.wall(s, u, y, dir));
        }
    }
}
