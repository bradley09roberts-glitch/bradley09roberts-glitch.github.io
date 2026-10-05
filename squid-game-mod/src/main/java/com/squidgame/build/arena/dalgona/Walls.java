package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * The two long (east / west) walls. Written once in a local frame and placed twice with
 * {@code BuildContext.at}: local x runs along the wall (to the right as seen from inside the hall), local y is
 * height, local z is the depth into the hall (z = 0 the pilaster plane, z = -1 the recessed plaster plane,
 * z = -2 the glass plane of the windows, z = -3 the outermost layer behind the glass).
 *
 * <p>Nine bays between ten pilasters (every 10 blocks, matching the roof ribs): from the rear wall
 * two plain bays beside the balcony stairs, window, mural, window, mural, window, wall chart, side door.
 */
public final class Walls {
    private Walls() {
    }

    public enum Bay { PLAIN, WINDOW, MURAL, CHART, DOOR }

    /** Bay types counted from the rear (south) wall. */
    private static final Bay[] BAYS = {Bay.PLAIN, Bay.PLAIN, Bay.WINDOW, Bay.MURAL, Bay.WINDOW, Bay.MURAL,
            Bay.WINDOW, Bay.CHART, Bay.DOOR};

    public static final String PLATE = Pal.log("stripped_dark_oak_wood", 'x');

    public static void build(BuildContext c) {
        c.at(-36, 0, -2, 3, () -> paint(c, true));
        c.at(36, 0, -92, 1, () -> paint(c, false));
    }

    private static void paint(BuildContext c, boolean west) {
        int tag = west ? 1 : 2;
        c.fill(0, Geo.EAVES, 0, 90, Geo.EAVES, 0, PLATE);
        for (int p = 0; p <= 9; p++) {
            pilaster(c, 10 * p);
        }
        for (int j = 0; j < 9; j++) {
            int lo = 10 * j + 1;
            int hi = lo + 8;
            int mid = lo + 4;
            int fromRear = west ? j : 8 - j;
            bay(c, BAYS[fromRear], lo, hi, mid, tag, west, fromRear);
        }
        // wall sconces on the inner pilasters
        for (int p = 1; p <= 8; p++) {
            boolean stairSide = (west ? p : 9 - p) == 1;     // the balcony stairs stand against this pilaster
            if (stairSide) {
                continue;
            }
            c.set(10 * p, 9, 1, Pal.log("stripped_dark_oak_wood", 'z'));
            c.set(10 * p, 8, 1, Pal.lantern(true));
        }
    }

    private static void pilaster(BuildContext c, int lx) {
        c.fill(lx, 1, 0, lx, 2, 0, Pal.DARK_OAK);
        c.fill(lx, 3, 0, lx, 15, 0, Pal.log("stripped_dark_oak_wood", 'y'));
        c.set(lx, 16, 0, Pal.DARK_OAK);
    }

    private static void bay(BuildContext c, Bay type, int lo, int hi, int mid, int tag, boolean west, int fromRear) {
        // wainscot (y 1..4) in the pilaster plane, recess + plaster above
        for (int lx = lo; lx <= hi; lx++) {
            wainscot(c, lx);
            if (type == Bay.PLAIN) {
                // flush plaster (the balcony stairs run along these two bays)
                for (int y = 5; y <= 16; y++) {
                    c.set(lx, y, 0, plaster(lx, y, tag));
                }
                continue;
            }
            for (int y = 5; y <= 16; y++) {
                c.air(lx, y, 0);
                c.set(lx, y, -1, plaster(lx, y, tag));
            }
            c.set(lx, 16, 0, Pal.stairs("dark_oak", "north", true));
        }
        switch (type) {
            case WINDOW -> window(c, mid);
            case MURAL -> Murals.paint(c, west ? (fromRear == 3 ? Murals.Kind.CIRCLE : Murals.Kind.TRIANGLE)
                    : (fromRear == 3 ? Murals.Kind.STAR : Murals.Kind.UMBRELLA), mid);
            case CHART -> chart(c, mid, tag);
            case DOOR -> sideDoor(c, lo, hi, mid);
            default -> {
            }
        }
    }

    /** Cream plaster with a few stained patches, the odd speck and a honeycomb frieze under the cornice. */
    public static String plaster(int lx, int y, int tag) {
        if (y == 16) {
            return Pal.HONEYCOMB;        // the frieze: a band of honeycomb blocks, the candy of the game
        }
        double n = Noise.value3(lx, y, tag * 17, 3.2, 23);
        if (n > 0.8) {
            return Pal.CREAM_WORN;
        }
        if (Noise.hash(lx, y, tag, 7) < 0.015) {
            return Pal.CREAM_SPECK;
        }
        return Pal.CREAM;
    }

    private static void wainscot(BuildContext c, int lx) {
        c.set(lx, 1, 0, Pal.DARK_OAK);
        boolean post = (lx & 1) == 0;
        String panel = post ? Pal.log("stripped_spruce_wood", 'y') : Pal.SPRUCE;
        c.set(lx, 2, 0, panel);
        c.set(lx, 3, 0, panel);
        c.set(lx, 4, 0, Pal.log("stripped_dark_oak_wood", 'x'));
    }

    // ---------------------------------------------------------------------------------------------
    // windows

    /** Opening mask of an arched window {@code h} rows tall, 5 wide; dy counted from the bottom row. */
    private static boolean inOpening(int dx, int dy, int h) {
        int ax = Math.abs(dx);
        if (dy < 0 || dy >= h) {
            return false;
        }
        if (dy <= h - 3) {
            return ax <= 2;
        }
        if (dy == h - 2) {
            return ax <= 1;
        }
        return ax == 0;
    }

    private static void window(BuildContext c, int mid) {
        window(c, mid, 6, 10, 0);
    }

    /**
     * Tall arched window: warm glowing backing, glass panes, white frame, quartz sill. {@code base} is the y of the
     * bottom opening row, {@code h} the opening height, {@code plane} shifts the planes (0 = long wall layout:
     * opening plane z = -1, glass -2, backing -3; 1 = the rear wall whose plaster plane is z = 0).
     */
    public static void window(BuildContext c, int mid, int base, int h, int plane) {
        int zo = -1 + plane;           // opening / frame plane
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -1; dy <= h + 1; dy++) {
                int y = base + dy;
                if (inOpening(dx, dy, h)) {
                    c.air(mid + dx, y, zo);
                    boolean mullion = dx == 0 || dy == 3 || dy == 6;
                    c.set(mid + dx, y, zo - 1, mullion ? Pal.WHITE_PANE : Pal.GLASS_PANE);
                    c.set(mid + dx, y, zo - 2, Pal.WARM_PANEL);
                    continue;
                }
                boolean ring = false;
                for (int ox = -1; ox <= 1 && !ring; ox++) {
                    for (int oy = -1; oy <= 1; oy++) {
                        if (inOpening(dx + ox, dy + oy, h)) {
                            ring = true;
                            break;
                        }
                    }
                }
                if (ring) {
                    boolean key = dx == 0 && dy == h;
                    c.set(mid + dx, y, zo, key ? Pal.OCHRE : Pal.QUARTZ);
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            c.set(mid + dx, base - 1, zo + 1, Pal.slab("quartz", false));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // wall chart (a framed map)

    private static void chart(BuildContext c, int mid, int tag) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int y = 6; y <= 15; y++) {
                boolean edge = Math.abs(dx) == 4 || y == 6 || y == 15;
                if (edge) {
                    c.set(mid + dx, y, 0, Pal.DARK_OAK);
                    continue;
                }
                double land = Noise.fbm2(mid + dx + tag * 40, y * 1.3, 4.2, 61);
                String s;
                if (land > 0.58) {
                    s = land > 0.7 ? "squidgame:pastel_peach" : "squidgame:pastel_mint";
                } else if (land > 0.52) {
                    s = "squidgame:pastel_yellow";
                } else {
                    s = "squidgame:pastel_sky";
                }
                c.set(mid + dx, y, -1, s);
            }
        }
        // in front of the cornice stairs of the top row (a text inside that block would be hidden)
        c.text(mid + 0.5, 16.1, 1.05, tag == 1 ? "WORLD MAP" : "SCHOOL MAP", "#5a3a1e", 1.6f, 0f, false);
    }

    // ---------------------------------------------------------------------------------------------
    // decorative double door (closed, solid blocks only so nothing can be opened)

    private static void sideDoor(BuildContext c, int lo, int hi, int mid) {
        // clear the wainscot where the door stands, build a timber portal in the pilaster plane
        for (int dx = -3; dx <= 3; dx++) {
            for (int y = 1; y <= 4; y++) {
                c.air(mid + dx, y, 0);
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            int ax = Math.abs(dx);
            for (int y = 1; y <= 14; y++) {
                if (ax == 3) {
                    c.set(mid + dx, y, 0, y <= 12 ? Pal.log("stripped_dark_oak_wood", 'y') : Pal.DARK_OAK);
                } else if (y >= 13) {
                    c.set(mid + dx, y, 0, Pal.DARK_OAK);                       // lintel
                } else if (y <= 10) {
                    // two door leaves, panelled: rails at y 1, 5, 10 and stiles at the edges and the centre line
                    boolean rail = y == 1 || y == 5 || y == 10 || dx == 0 || ax == 2;
                    c.set(mid + dx, y, -1, rail ? Pal.DARK_OAK : Pal.SPRUCE);
                    c.air(mid + dx, y, 0);
                } else if (y == 11) {
                    c.set(mid + dx, y, -1, Pal.QUARTZ);                        // transom bar
                    c.air(mid + dx, y, 0);
                } else {
                    // transom window with warm light behind it
                    c.air(mid + dx, y, 0);
                    c.air(mid + dx, y, -1);
                    c.set(mid + dx, y, -2, Pal.GLASS_PANE);
                    c.set(mid + dx, y, -3, Pal.WARM_PANEL);
                }
            }
        }
        c.set(mid - 1, 6, 0, "minecraft:tripwire_hook[attached=false,facing=south,powered=false]");
        c.set(mid + 1, 6, 0, "minecraft:tripwire_hook[attached=false,facing=south,powered=false]");
        c.text(mid + 0.5, 15.0, 0.05, "STAFF ONLY", "#8a4b1f", 1.3f, 0f, false);
    }
}
