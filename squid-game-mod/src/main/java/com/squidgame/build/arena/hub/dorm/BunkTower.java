package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * One bunk-bed tower, a cage of dark steel 7 x 9 blocks in plan and 25 high: six sleeping tiers (pitch 4) with a
 * spine of lanterns down the middle, iron-bar cage walls, thin green-grey mattresses with white pillows (twelve beds a
 * tier), a ladder on each long side and a ring beam with lamps on top.
 *
 * <p>Local frame (origin = tower centre on the floor, standing level y = 0): x[-3,3] z[-4,4] y[0,25]. Posts stand at
 * x in {-3, 0, 3} and z in {-4, 0, 4}; the beds lie along x on both sides of the spine (x = 0), three to a bay.
 * Ladders: east face at (4, y, -1) climbing a pylon at (4, y, 0); west face at (-4, y, +1) with a pylon at (-4, y, 0).
 * The cage is open at the foot of each ladder (x = +-3, z = -+1) at every tier so the decks can be entered.
 */
final class BunkTower {
    private BunkTower() {
    }

    private static final String BEAM = Pal.STEEL_TILES;
    private static final String POST = Pal.STEEL;
    private static final String DECK = "minecraft:polished_andesite_slab[type=top]";
    private static final String LAMP = Pal.LANTERN_HANG;

    static void build(BuildContext c, Layout.Tower t) {
        int top = Layout.TOWER_TOP;
        for (int tier = 0; tier < Layout.TIERS; tier++) {
            int y = tier * Layout.PITCH;
            deck(c, t, tier, y);
            cage(c, y);
        }
        for (int tier = 0; tier <= Layout.TIERS; tier++) {
            beams(c, tier * Layout.PITCH);
        }
        posts(c, top);
        lanterns(c);
        ladders(c, top);
        baseLights(c);
        cap(c, top, t);
        numbers(c, t);
    }

    /** Beam grid at one level: outer ring, spine and the cross beam between the two bays. */
    private static void beams(BuildContext c, int y) {
        c.fill(-3, y, -4, 3, y, -4, BEAM);
        c.fill(-3, y, 4, 3, y, 4, BEAM);
        c.fill(-3, y, -4, -3, y, 4, BEAM);
        c.fill(3, y, -4, 3, y, 4, BEAM);
        c.fill(0, y, -4, 0, y, 4, BEAM);
        c.fill(-3, y, 0, 3, y, 0, BEAM);
    }

    /** Slab decks and mattresses of one tier. */
    private static void deck(BuildContext c, Layout.Tower t, int tier, int y) {
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -3; z <= 3; z++) {
                if (z == 0) {
                    continue;
                }
                int head = side;       // cell next to the spine holds the pillow
                int foot = 2 * side;
                c.set(head, y, z, DECK);
                c.set(foot, y, z, DECK);
                c.set(head, y + 1, z, Pal.PILLOW);
                c.set(foot, y + 1, z, sheet(t, tier, side, z));
            }
        }
    }

    private static String sheet(Layout.Tower t, int tier, int side, int z) {
        return Noise.pick(900 + t.id(), tier * 3 + side, tier, z,
                new String[]{Pal.SHEET_GREEN, Pal.SHEET_LGRAY, Pal.SHEET_GRAY},
                new int[]{46, 34, 20});
    }

    /** Iron-bar cage between the posts of one tier (3 high) with the ladder gaps and the lantern slots left open. */
    private static void cage(BuildContext c, int y) {
        // long faces: gap at the ladder foot
        for (int z = -3; z <= 3; z++) {
            if (z == 0) {
                continue;
            }
            boolean gapEast = z == -1;
            boolean gapWest = z == 1;
            for (int dy = 1; dy <= 3; dy++) {
                if (!(gapEast && dy <= 2)) {
                    c.set(3, y + dy, z, gapEast && dy == 3 ? LAMP : Pal.BARS);
                }
                if (!(gapWest && dy <= 2)) {
                    c.set(-3, y + dy, z, gapWest && dy == 3 ? LAMP : Pal.BARS);
                }
            }
        }
        // end faces
        for (int x : new int[]{-2, -1, 1, 2}) {
            c.fill(x, y + 1, -4, x, y + 3, -4, Pal.BARS);
            c.fill(x, y + 1, 4, x, y + 3, 4, Pal.BARS);
        }
        // spine
        for (int z = -3; z <= 3; z++) {
            if (z == 0) {
                continue;
            }
            int hi = (z == -2 || z == 2) ? 2 : 3;
            c.fill(0, y + 1, z, 0, y + hi, z, Pal.BARS);
        }
    }

    private static void posts(BuildContext c, int top) {
        for (int x : new int[]{-3, 3}) {
            for (int z : new int[]{-4, 4}) {
                c.fill(x, 0, z, x, top, z, POST);
            }
            c.fill(x, 1, 0, x, top - 1, 0, POST);
        }
        for (int z : new int[]{-4, 4}) {
            c.fill(0, 0, z, 0, top, z, POST);
        }
        c.fill(0, 1, 0, 0, top - 1, 0, Pal.STEEL_WALL);
    }

    /** A lantern under every beam level, two per tier on the spine, lighting both rows of beds. */
    private static void lanterns(BuildContext c) {
        for (int tier = 0; tier < Layout.TIERS; tier++) {
            int y = tier * Layout.PITCH + 3;
            c.set(0, y, -2, LAMP);
            c.set(0, y, 2, LAMP);
        }
    }

    private static void ladders(BuildContext c, int top) {
        // east side: pylon at (4, y, 0), ladder at (4, y, -1) facing north (attached to the pylon on its south side)
        c.fill(4, 0, 0, 4, top, 0, POST);
        for (int y = 0; y <= top - 2; y++) {
            c.set(4, y, -1, "minecraft:ladder[facing=north]");
        }
        c.fill(-4, 0, 0, -4, top, 0, POST);
        for (int y = 0; y <= top - 2; y++) {
            c.set(-4, y, 1, "minecraft:ladder[facing=south]");
        }
    }

    /** Glowing panels let into the foot beams: the floor lighting of the aisles. */
    private static void baseLights(BuildContext c) {
        for (int z : new int[]{-2, 2}) {
            c.set(3, 0, z, Pal.PANEL_WHITE);
            c.set(-3, 0, z, Pal.PANEL_WHITE);
        }
        for (int x : new int[]{-2, 2}) {
            c.set(x, 0, -4, Pal.PANEL_WHITE);
            c.set(x, 0, 4, Pal.PANEL_WHITE);
        }
    }

    /**
     * The crown: corner posts with glowing caps (one carries the camera), and at both ends a black plate with the tower's
     * letter above the ring beam.
     */
    private static void cap(BuildContext c, int top, Layout.Tower t) {
        int camSide = t.cz() <= 0 ? 4 : -4;
        for (int x : new int[]{-3, 3}) {
            for (int z : new int[]{-4, 4}) {
                c.fill(x, top + 1, z, x, top + 3, z, Pal.STEEL_WALL);
                boolean cam = x == 3 && z == camSide;
                if (cam) {
                    c.set(x, top + 4, z, Pal.BLACK);
                    c.set(x, top + 4, z + Integer.signum(z), "minecraft:black_stained_glass");
                    c.set(x, top + 5, z, "minecraft:red_concrete");
                } else {
                    c.set(x, top + 4, z, Pal.SEA);
                }
            }
        }
        // end plates between the corner posts
        for (int z : new int[]{-4, 4}) {
            c.fill(-2, top + 1, z, 2, top + 3, z, Pal.BLACK);
            c.fill(-2, top + 3, z, 2, top + 3, z, Pal.PINK);
            // text displays are anchored at the bottom centre of the text: a 9x letter is 2.25 tall, the plate spans y 25..28
            c.text(0.5, top + 1.4, z > 0 ? 5.04 : -4.04, t.label(), "white", 9f, z > 0 ? 0f : 180f, false);
        }
    }

    /** Bunk numbers on the beam fronts of every tier, both long faces, two bays each: label "A-01" .. "A-24". */
    private static void numbers(BuildContext c, Layout.Tower t) {
        for (int tier = 0; tier < Layout.TIERS; tier++) {
            double y = tier * Layout.PITCH + 0.3;
            for (int face = 0; face < 2; face++) {
                for (int bay = 0; bay < 2; bay++) {
                    int n = tier * 4 + face * 2 + bay + 1;
                    String label = t.label() + "-" + (n < 10 ? "0" : "") + n;
                    double z = bay == 0 ? -1.5 : 2.5;
                    if (face == 0) {
                        c.text(4.04, y, z, label, "white", 1.5f, -90f, false);
                    } else {
                        c.text(-3.04, y, z, label, "white", 1.5f, 90f, false);
                    }
                }
            }
        }
    }
}
