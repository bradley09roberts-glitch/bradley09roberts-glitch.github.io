package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * Surveillance and service props: black security cameras on the walls (the towers carry their own, see BunkTower), loudspeakers,
 * louvred vents, conduit runs along the walls and caged pendant lamps over the avenue. All are small block models that
 * keep clear of the walkable space.
 */
final class Props {
    private Props() {
    }

    private static final String BLACK = Pal.BLACK;
    private static final String GLASS = "minecraft:black_stained_glass";

    static void build(BuildContext c) {
        wallCameras(c);
        domeCameras(c);
        speakers(c);
        vents(c);
        conduits(c);
        pendants(c);
        ducts(c);
    }

    // ------------------------------------------------------------------------------------------ cameras

    /**
     * Box camera: a bracket on the wall, a 2-block black body and a dark lens, with a red recording LED on top. (x,y,z) is
     * the cell next to the wall, (dx,dz) the horizontal viewing direction (away from the wall).
     */
    static void camera(BuildContext c, int x, int y, int z, int dx, int dz) {
        c.set(x, y + 1, z, Pal.STEEL_WALL);                // bracket from the wall
        c.set(x, y, z, BLACK);
        c.set(x + dx, y, z + dz, BLACK);
        c.set(x + 2 * dx, y, z + 2 * dz, GLASS);
        c.set(x + dx, y + 1, z + dz, Pal.LED);
    }

    private static void wallCameras(BuildContext c) {
        // north wall (look south) and south wall (look north)
        for (int x : new int[]{-30, -16, -8, 8, 16, 30}) {
            camera(c, x, 31, -32, 0, 1);
        }
        for (int x : new int[]{-30, -16, 16, 30}) {
            camera(c, x, 31, 32, 0, -1);
        }
        // west wall (look east); east wall beyond the windows (look west)
        for (int z : new int[]{-26, -12, 12, 26}) {
            camera(c, -40, 31, z, 1, 0);
        }
        for (int z : new int[]{-26, 26}) {
            camera(c, 40, 31, z, -1, 0);
        }
    }

    /** Dome cameras hanging on short chains over the plaza and the arrival pad. */
    private static void domeCameras(BuildContext c) {
        int[][] pts = {{-10, -4}, {10, -4}, {-10, 4}, {10, 4}, {0, 20}, {0, -20}, {0, 28}};
        for (int[] p : pts) {
            int y = 27;
            c.fill(p[0], y + 1, p[1], p[0], Layout.TRUSS_BOTTOM - 1, p[1], Pal.CHAIN);
            c.set(p[0], y, p[1], BLACK);
            c.set(p[0], y - 1, p[1], GLASS);
        }
    }

    // ------------------------------------------------------------------------------------------ speakers

    /** Loudspeaker: two stacked black boxes with a barred grille on the front. */
    static void speaker(BuildContext c, int x, int y, int z, int dx, int dz) {
        c.set(x, y, z, BLACK);
        c.set(x, y + 1, z, BLACK);
        c.set(x + dx, y, z + dz, Pal.BARS);
        c.set(x + dx, y + 1, z + dz, Pal.BARS);
        c.set(x, y + 2, z, Pal.STEEL_WALL);
    }

    private static void speakers(BuildContext c) {
        for (int x : new int[]{-24, -4, 4, 24}) {
            speaker(c, x, 24, -32, 0, 1);
        }
        for (int x : new int[]{-24, -4, 4, 24}) {
            speaker(c, x, 24, 32, 0, -1);
        }
        for (int z : new int[]{-22, 22}) {
            speaker(c, -40, 24, z, 1, 0);
            speaker(c, 40, 24, z, -1, 0);
        }
    }

    // ------------------------------------------------------------------------------------------ vents

    private static void vents(BuildContext c) {
        // north and south wall: louvre panels in the bays between the pilasters, high up
        int[][] bays = {{-34, -30}, {-26, -22}, {-18, -14}, {14, 18}, {22, 26}, {30, 34}};
        String vent = "minecraft:iron_trapdoor[facing=south,half=bottom,open=true]";
        c.at(0, 0, 0, 0, () -> {
            for (int[] b : bays) {
                c.fill(b[0], 29, -32, b[1], 30, -32, vent);
                c.fill(b[0], 28, -32, b[1], 28, -32, Pal.STEEL_TILES);
                c.fill(b[0], 31, -32, b[1], 31, -32, Pal.STEEL_TILES);
            }
        });
        c.at(0, 0, 0, 2, () -> {
            for (int[] b : bays) {
                c.fill(b[0], 29, -32, b[1], 30, -32, vent);
                c.fill(b[0], 28, -32, b[1], 28, -32, Pal.STEEL_TILES);
                c.fill(b[0], 31, -32, b[1], 31, -32, Pal.STEEL_TILES);
            }
        });
    }

    // ------------------------------------------------------------------------------------------ conduits

    /** Two thin pipes (walls connect into a continuous run) along the north and south walls, with clamps at the pilasters. */
    private static void conduits(BuildContext c) {
        for (int side = -1; side <= 1; side += 2) {
            int z = side * 31;
            for (int x = -39; x <= 39; x++) {
                if (Math.abs(x) <= 5 && side < 0) {
                    continue;
                }
                c.set(x, 33, z, Pal.STEEL_WALL);
                c.set(x, 34, z, Pal.STEEL_WALL);
            }
            for (int x = -36; x <= 36; x += 8) {
                c.fill(x, 32, z, x, 35, z, Pal.STEEL);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ pendants

    /** Caged pendant lamps on chains from the trusses, in a row over the avenue (clear of the pig). */
    private static void pendants(BuildContext c) {
        for (int z : new int[]{-28, -20, -12, 12, 20, 28}) {
            for (int x : new int[]{-8, 8}) {
                int y = 25;
                c.fill(x, y + 1, z, x, Layout.TRUSS_BOTTOM - 1, z, Pal.CHAIN);
                c.set(x, y, z, Pal.SEA);
                c.set(x + 1, y, z, Pal.BARS);
                c.set(x - 1, y, z, Pal.BARS);
                c.set(x, y, z + 1, Pal.BARS);
                c.set(x, y, z - 1, Pal.BARS);
                c.set(x, y - 1, z, Pal.BARS);
                c.set(x, y + 1, z, BLACK);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ ducts

    /** Two big rectangular ventilation ducts (3 x 2) running north-south under the trusses over the side aisles. */
    private static void ducts(BuildContext c) {
        for (int x : new int[]{-22, 22}) {
            c.fill(x - 1, 29, -30, x + 1, 30, 30, Pal.LGRAY);
            // darker collars every six blocks and a seam line along the sides
            for (int z = -30; z <= 30; z += 6) {
                c.fill(x - 1, 29, z, x + 1, 30, z, Pal.STEEL_TILES);
            }
            c.fill(x - 2, 29, -30, x - 2, 29, 30, Pal.STEEL_SLAB_T);
            c.fill(x + 2, 29, -30, x + 2, 29, 30, Pal.STEEL_SLAB_T);
        }
    }
}
