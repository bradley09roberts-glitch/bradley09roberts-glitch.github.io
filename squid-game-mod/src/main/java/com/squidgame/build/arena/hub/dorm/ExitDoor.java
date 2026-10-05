package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * The exit door in the north wall (z = -33..-34): a 7 x 6 opening (x[-3,3], y[0,5]) left empty for the sliding door the
 * server installs, in a heavy black frame with hazard stripes, lit sign plate and flanking lamps. The opening is carved
 * through both wall layers so the corridor behind (starting at z = -35) meets it exactly. Marker {@code dorm.exit_door}
 * on the door plane (z = -33), yaw 180, {@code w=7,h=6}.
 */
final class ExitDoor {
    private ExitDoor() {
    }

    private static final String HAZ = "minecraft:yellow_concrete";

    static void build(BuildContext c) {
        // the opening, through both wall layers
        c.clear(-3, 0, -34, 3, 5, -33);
        // heavy frame (black) round the opening, in the inner wall layer and one block proud of it
        for (int z = -33; z <= -32; z++) {
            c.fill(-5, 0, z, -4, 7, z, Pal.BLACK);
            c.fill(4, 0, z, 5, 7, z, Pal.BLACK);
            c.fill(-5, 6, z, 5, 7, z, Pal.BLACK);
        }
        // diagonal hazard stripes on the proud face of the jambs and the lintel
        for (int y = 0; y <= 7; y++) {
            for (int x = -5; x <= 5; x++) {
                boolean frame = Math.abs(x) >= 4 || y >= 6;
                if (frame) {
                    c.set(x, y, -32, Math.floorMod(x + y, 4) < 2 ? HAZ : Pal.BLACK);
                }
            }
        }
        // sign plate above
        c.fill(-6, 8, -33, 6, 10, -33, Pal.BLACK);
        c.fill(-6, 8, -33, 6, 8, -33, Pal.PINK);
        c.fill(-6, 10, -33, 6, 10, -33, Pal.PINK);
        // the sign plate sits in the wall layer z = -33 (it spans -33..-32); the text floats just in front of its face
        c.text(0.5, 9.0, -31.96, "EXIT", "#FF4D6A", 3.6f, 0f, false);
        // flanking lamps
        c.set(-6, 3, -32, Pal.PANEL_WHITE);
        c.set(6, 3, -32, Pal.PANEL_WHITE);
        c.set(-6, 5, -32, Pal.PANEL_WHITE);
        c.set(6, 5, -32, Pal.PANEL_WHITE);
        booth(c, -10);
        booth(c, 8);
        c.marker("dorm.exit_door", 0.5, 0.0, -32.5, 180f, "w=7,h=6");
    }

    /** A black guard desk (3 wide, 2 deep) with two monitors facing the hall, against the north wall. */
    private static void booth(BuildContext c, int x0) {
        c.fill(x0, 0, -32, x0 + 2, 0, -31, Pal.BLACK);
        c.fill(x0, 1, -32, x0 + 2, 1, -32, Pal.STEEL_TILES);
        c.fill(x0, 1, -31, x0 + 2, 1, -31, Pal.STEEL_SLAB_B);
        c.set(x0, 2, -32, "squidgame:monitor[facing=south]");
        c.set(x0 + 2, 2, -32, "squidgame:monitor[facing=south]");
        c.set(x0 + 1, 2, -32, "squidgame:monitor[facing=south]");
    }
}
