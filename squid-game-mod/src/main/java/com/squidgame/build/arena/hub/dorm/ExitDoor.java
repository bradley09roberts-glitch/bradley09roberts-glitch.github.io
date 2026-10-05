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
        // hazard stripes on the jambs and the lintel
        for (int y = 0; y <= 5; y++) {
            String s = (y & 1) == 0 ? HAZ : Pal.BLACK;
            c.set(-4, y, -32, s);
            c.set(4, y, -32, s);
        }
        for (int x = -4; x <= 4; x++) {
            c.set(x, 6, -32, (x & 1) == 0 ? HAZ : Pal.BLACK);
        }
        // sign plate above
        c.fill(-6, 8, -33, 6, 10, -33, Pal.BLACK);
        c.fill(-6, 8, -33, 6, 8, -33, Pal.PINK);
        c.fill(-6, 10, -33, 6, 10, -33, Pal.PINK);
        c.text(0.5, 9.0, -32.9, "EXIT", "#FF4D6A", 3.6f, 0f, false);
        // flanking lamps
        c.set(-6, 3, -32, Pal.PANEL_WHITE);
        c.set(6, 3, -32, Pal.PANEL_WHITE);
        c.set(-6, 5, -32, Pal.PANEL_WHITE);
        c.set(6, 5, -32, Pal.PANEL_WHITE);
        c.marker("dorm.exit_door", 0.5, 0.0, -32.5, 180f, "w=7,h=6");
    }
}
