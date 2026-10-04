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

    /** Cream plaster with stained patches and the odd speck. */
    public static String plaster(int lx, int y, int tag) {
        double n = Noise.value3(lx, y, tag * 17, 4.5, 23);
        if (n > 0.73) {
            return Pal.CREAM_WORN;
        }
        if (Noise.hash(lx, y, tag, 7) < 0.018) {
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

    private static boolean inOpening(int dx, int y) {
        int ax = Math.abs(dx);
        if (y >= 6 && y <= 13) {
            return ax <= 2;
        }
        if (y == 14) {
            return ax <= 1;
        }
        return y == 15 && ax == 0;
    }

    /** Tall arched window: warm glowing backing, glass panes, white frame, quartz sill. */
    private static void window(BuildContext c, int mid) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int y = 5; y <= 17; y++) {
                boolean open = inOpening(dx, y);
                if (open) {
                    c.air(mid + dx, y, -1);
                    boolean mullion = dx == 0 || y == 9 || y == 12;
                    c.set(mid + dx, y, -2, mullion ? Pal.WHITE_PANE : Pal.GLASS_PANE);
                    c.set(mid + dx, y, -3, Pal.WARM_PANEL);
                    continue;
                }
                // white frame ring (8-neighbourhood of the opening)
                boolean ring = false;
                for (int ox = -1; ox <= 1 && !ring; ox++) {
                    for (int oy = -1; oy <= 1; oy++) {
                        if (inOpening(dx + ox, y + oy)) {
                            ring = true;
                            break;
                        }
                    }
                }
                if (ring) {
                    boolean key = dx == 0 && y == 16;
                    c.set(mid + dx, y, -1, key ? Pal.OCHRE : Pal.QUARTZ);
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            c.set(mid + dx, 5, 0, Pal.slab("quartz", false));
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
                    s = land > 0.7 ? "minecraft:brown_terracotta" : "minecraft:green_terracotta";
                } else if (land > 0.52) {
                    s = "minecraft:yellow_terracotta";
                } else {
                    s = "minecraft:light_blue_terracotta";
                }
                c.set(mid + dx, y, -1, s);
            }
        }
        c.text(mid + 0.5, 16.15, 0.02, "WORLD MAP", "#5a3a1e", 1.6f, 0f, false);
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
            for (int y = 1; y <= 14; y++) {
                boolean post = Math.abs(dx) == 3;
                boolean lintel = y >= 13;
                boolean door = Math.abs(dx) <= 2 && y <= 11;
                if (post || lintel) {
                    c.set(mid + dx, y, 0, post ? Pal.log("stripped_dark_oak_wood", 'y') : Pal.DARK_OAK);
                } else if (door) {
                    boolean rail = y == 1 || y == 6 || y == 11 || dx == 0 || Math.abs(dx) == 2;
                    c.set(mid + dx, y, -1, rail ? Pal.DARK_OAK : Pal.SPRUCE);
                    c.air(mid + dx, y, 0);
                } else if (y == 12) {
                    // transom glass with warm light behind
                    c.set(mid + dx, y, -2, Pal.GLASS_PANE);
                    c.set(mid + dx, y, -3, Pal.WARM_PANEL);
                    c.air(mid + dx, y, -1);
                    c.air(mid + dx, y, 0);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            c.set(mid + dx, 12, -1, Pal.QUARTZ);
        }
        c.set(mid - 1, 6, 0, "minecraft:tripwire_hook[attached=false,facing=south,powered=false]");
        c.set(mid + 1, 6, 0, "minecraft:tripwire_hook[attached=false,facing=south,powered=false]");
    }
}
