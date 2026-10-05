package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.corridor.Plan.Kind;
import com.squidgame.build.arena.hub.corridor.Plan.Space;

/**
 * Detailing of the long 7-wide corridors (E1, E2, N1, N2 and the branch corridor): hanging black ceiling beams every
 * four blocks, cable trays along both edges, vent grilles, ceiling security cameras, sliding security doors on the
 * blank wall panels and the big painted symbol murals at the far ends (the vanishing points).
 */
final class Corridors {
    private Corridors() {
    }

    static void build(BuildContext c) {
        for (Space s : new Space[]{Plan.E1, Plan.E2, Plan.N1, Plan.N2, Plan.BC}) {
            decorate(c, s);
        }
        slidingDoors(c);
        murals(c);
    }

    // ------------------------------------------------------------------ generic corridor decor

    private static void decorate(BuildContext c, Space s) {
        boolean ew = s.kind == Kind.CORRIDOR_X;
        int u0 = ew ? s.x0 : s.z0, u1 = ew ? s.x1 : s.z1;
        int vc = ew ? (s.z0 + s.z1) / 2 : (s.x0 + s.x1) / 2;
        // facing from the wall into the corridor for the cells at v=-3 and v=+3
        String faceNeg = ew ? "south" : "east";
        String facePos = ew ? "north" : "west";
        String along = ew ? "east" : "south";
        int camera = 0;
        for (int u = u0; u <= u1; u++) {
            int cx = ew ? u : vc, cz = ew ? vc : u;
            if (Plan.at(cx, cz) != s) {
                continue;
            }
            boolean beam = Math.floorMod(u, 4) == 0;
            for (int v = -3; v <= 3; v++) {
                int px = ew ? u : vc + v, pz = ew ? vc + v : u;
                if (beam) {
                    c.set(px, 5, pz, Pal.BEAM);
                } else if (Math.abs(v) == 3) {
                    c.set(px, 5, pz, Pal.stairs("minecraft:smooth_quartz_stairs", v < 0 ? faceNeg : facePos, true));
                }
            }
            // cable run: a slab tray with a chain bundle running the length of the corridor on the +v side
            {
                int px = ew ? u : vc + 2, pz = ew ? vc + 2 : u;
                if (!beam) {
                    c.set(px, 5, pz, "minecraft:polished_blackstone_slab[type=top]");
                }
                c.set(px, 4, pz, Pal.chain(ew ? "x" : "z"));
            }
            // vent grilles on the white band, centred in every pink panel
            if (Math.floorMod(u, 4) == 2) {
                int nx = ew ? u : vc - 3, nz = ew ? vc - 3 : u;
                int px = ew ? u : vc + 3, pz = ew ? vc + 3 : u;
                c.set(nx, 4, nz, Pal.trapdoor("minecraft:iron_trapdoor", faceNeg, true, false));
                c.set(px, 4, pz, Pal.trapdoor("minecraft:iron_trapdoor", facePos, true, false));
            }
            // ceiling cameras under every fourth beam, alternating sides
            if (beam && Math.floorMod(u, 16) == 8) {
                int side = (camera++ % 2 == 0) ? -3 : 3;
                int px = ew ? u : vc + side, pz = ew ? vc + side : u;
                Props.camera(c, px, 4, pz, along);
            }
        }
    }

    // ------------------------------------------------------------------ sliding security doors (blank panels)

    /**
     * A three-block-wide sliding security door in a wall plane: gray leaves with a black seam, number plate above.
     * {@code planeZ}: plane z = fixed spanning x, else plane x = fixed spanning z; {@code toward} = the direction
     * from the wall to the viewer (north/south/east/west).
     */
    static void slidingDoor(BuildContext c, boolean planeZ, int fixed, int u0, String toward, int number) {
        for (int i = 0; i < 3; i++) {
            String b = i == 1 ? Pal.BLACK : Pal.LGRAY;
            for (int y = 1; y <= 3; y++) {
                if (planeZ) {
                    c.set(u0 + i, y, fixed, b);
                } else {
                    c.set(fixed, y, u0 + i, b);
                }
            }
        }
        String num = String.format("%02d", number);
        double um = u0 + 1.5;
        switch (toward) {
            case "south" -> c.text(um, 4.2, fixed + 1.04, num, "white", 1.6f, 0f, false);
            case "north" -> c.text(um, 4.2, fixed - 0.04, num, "white", 1.6f, 180f, false);
            case "east" -> c.text(fixed + 1.04, 4.2, um, num, "white", 1.6f, -90f, false);
            default -> c.text(fixed - 0.04, 4.2, um, num, "white", 1.6f, 90f, false);
        }
    }

    private static void slidingDoors(BuildContext c) {
        int n = 20;
        // E1 north wall (z=-52, viewer south) and south wall (z=-44, viewer north); positions avoid the room doors,
        // the arms' openings and the checkpoint murals
        for (int u0 : new int[]{-19, 17}) {
            slidingDoor(c, true, -52, u0, "south", n++);
        }
        for (int u0 : new int[]{-27, 25}) {
            slidingDoor(c, true, -44, u0, "north", n++);
        }
        // E2 north wall (z=-80, viewer south) outside the vestibule and the corner murals; south wall (z=-72, viewer north)
        for (int u0 : new int[]{-19, -15, -11, 9, 13, 17}) {
            slidingDoor(c, true, -80, u0, "south", n++);
        }
        for (int u0 : new int[]{-21, -11, 19}) {
            slidingDoor(c, true, -72, u0, "north", n++);
        }
        // N2 (security wing): outer wall x=-30 (viewer east) and inner wall x=-22 (viewer west)
        for (int u0 : new int[]{-71, -63, -55}) {
            slidingDoor(c, false, -30, u0, "east", n++);
        }
        slidingDoor(c, false, -22, -63, "west", n++);
        // N1 outer wall x=30 (viewer west)
        for (int u0 : new int[]{-71, -55}) {
            slidingDoor(c, false, 30, u0, "west", n++);
        }
    }

    // ------------------------------------------------------------------ murals at the corridor ends

    private static void murals(BuildContext c) {
        String w = Pal.WHITE, k = Pal.BLACK;
        // E1 east / west end caps (outer walls x = +-30), 7 wide, y 1..8
        Murals.paint(c, 0, false, 30, -51, -45, 1, 8, -47.5, 4.5, 3.0, w, k);
        Murals.paint(c, 2, false, -30, -51, -45, 1, 8, -47.5, 4.5, 3.0, w, k);
        // north end caps of the arms (z = -80)
        Murals.paint(c, 1, true, -80, 23, 29, 1, 8, 26.5, 4.5, 3.0, w, k);
        Murals.paint(c, 0, true, -80, -29, -23, 1, 8, -26.5, 4.5, 3.0, w, k);
        // E2 end caps
        Murals.paint(c, 2, false, 30, -79, -73, 1, 8, -75.5, 4.5, 3.0, w, k);
        Murals.paint(c, 1, false, -30, -79, -73, 1, 8, -75.5, 4.5, 3.0, w, k);
    }
}
