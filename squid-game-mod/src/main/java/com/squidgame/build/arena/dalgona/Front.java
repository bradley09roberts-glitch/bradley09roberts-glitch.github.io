package com.squidgame.build.arena.dalgona;

import com.squidgame.build.BuildContext;

/**
 * The front (north) wall of the hall: wainscot and plastered gable, the huge green chalkboard in its wooden
 * frame with chalk drawings of the four candy shapes, the big wall clock above it, framed maps and charts,
 * bookshelves, hanging school flags and old wall speakers. The wall's local frame is the world frame shifted
 * so that z = 0 is the wall's inner layer (world z = -92) and +z points into the hall.
 */
public final class Front {
    private Front() {
    }

    public static final int BOARD_X = 16;         // frame half width (frame cells x = +-16)
    public static final int BOARD_Y0 = 4;         // lowest frame row (chalk tray)
    public static final int BOARD_Y1 = 14;        // top frame row
    public static final double BOARD_CENTER_Y = 9.5;

    private static final String CHALK = "minecraft:white_concrete";
    private static final String FRAME = Pal.log("stripped_dark_oak_wood", 'x');
    private static final String FRAME_Y = Pal.log("stripped_dark_oak_wood", 'y');

    public static void build(BuildContext c) {
        c.at(0, 0, Geo.FRONT_WALL_Z, 0, () -> wall(c));
    }

    private static void wall(BuildContext c) {
        // wainscot and plaster
        for (int x = -35; x <= 35; x++) {
            c.set(x, 1, 0, Pal.DARK_OAK);
            boolean post = (x & 1) == 0;
            String panel = post ? Pal.log("stripped_spruce_wood", 'y') : Pal.SPRUCE;
            c.set(x, 2, 0, panel);
            c.set(x, 3, 0, panel);
            c.set(x, 4, 0, FRAME);
            int top = Shell.gableTop(x);
            for (int y = 5; y <= top; y++) {
                c.set(x, y, 0, Walls.plaster(x, y, 3));
            }
            // raking trim along the roof line
            c.set(x, top, 0, Pal.DARK_OAK);
            c.set(x, top, 1, Pal.stairs("dark_oak", "north", true));
        }
        // posts either side of the board area
        for (int sx = -1; sx <= 1; sx += 2) {
            int x = sx * 19;
            c.fill(x, 1, 0, x, 2, 0, Pal.DARK_OAK);
            c.fill(x, 3, 0, x, Geo.EAVES - 2, 0, Pal.log("stripped_dark_oak_wood", 'y'));
        }
        board(c);
        clock(c, 0, 19);
        sideDecor(c, -1);
        sideDecor(c, 1);
    }

    // ---------------------------------------------------------------------------------------------
    // chalkboard

    private static void board(BuildContext c) {
        // frame (one block proud of the wall) and board face
        for (int x = -BOARD_X; x <= BOARD_X; x++) {
            for (int y = BOARD_Y0; y <= BOARD_Y1; y++) {
                boolean edge = Math.abs(x) == BOARD_X || y == BOARD_Y0 || y == BOARD_Y1;
                if (edge) {
                    c.set(x, y, 1, Math.abs(x) == BOARD_X ? FRAME_Y : FRAME);
                    c.set(x, y, 0, Pal.DARK_OAK);
                    continue;
                }
                boolean divider = Math.abs(x) == 8;
                if (divider) {
                    c.set(x, y, 1, FRAME_Y);
                }
                String s = Pal.GREEN_BOARD;
                if (Noise.hash(x, y, 4, 77) < 0.045) {
                    s = "minecraft:green_terracotta";
                }
                c.set(x, y, 0, s);
            }
        }
        // chalk tray
        for (int x = -BOARD_X + 1; x <= BOARD_X - 1; x++) {
            c.set(x, BOARD_Y0, 1, "minecraft:spruce_slab[type=top]");
        }
        for (int x : new int[]{-13, -11, -3, 4, 5, 12}) {
            c.set(x, BOARD_Y0 + 1, 1, "minecraft:stone_button[face=floor,facing=north,powered=false]");
        }
        // chalk drawings on the two side panels: circle + triangle left, star + umbrella right
        circleChalk(c, -12, 11, 2.6);
        triangleChalk(c, -12, 7);
        starChalk(c, 12, 11);
        umbrellaChalk(c, 12, 7);
        // chalk lines and ticks on the central panel margins
        for (int x = -6; x <= 6; x++) {
            if ((x & 1) == 0) {
                c.set(x, 6, 0, CHALK);
            }
        }
        c.text(0.5, 12.0, 1.02, "THE HONEYCOMB GAME", "#f4f1e6", 3.4f, 0f, false);
    }

    private static void dot(BuildContext c, int x, int y) {
        c.set(x, y, 0, CHALK);
    }

    private static void circleChalk(BuildContext c, int cx, int cy, double r) {
        for (int a = 0; a < 360; a += 4) {
            double t = Math.toRadians(a);
            dot(c, (int) Math.round(cx + r * Math.cos(t)), (int) Math.round(cy + r * Math.sin(t)));
        }
    }

    private static void triangleChalk(BuildContext c, int cx, int cy) {
        c.line(cx, cy + 2, 0, cx - 2, cy - 2, 0, CHALK);
        c.line(cx, cy + 2, 0, cx + 2, cy - 2, 0, CHALK);
        c.line(cx - 2, cy - 2, 0, cx + 2, cy - 2, 0, CHALK);
    }

    private static void starChalk(BuildContext c, int cx, int cy) {
        int[][] p = new int[5][2];
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(90 + 72 * i);
            p[i][0] = (int) Math.round(cx + 2.8 * Math.cos(a));
            p[i][1] = (int) Math.round(cy + 0.2 + 2.8 * Math.sin(a));
        }
        for (int i = 0; i < 5; i++) {
            int j = (i + 2) % 5;
            c.line(p[i][0], p[i][1], 0, p[j][0], p[j][1], 0, CHALK);
        }
    }

    private static void umbrellaChalk(BuildContext c, int cx, int cy) {
        for (int a = 0; a <= 180; a += 6) {
            double t = Math.toRadians(a);
            dot(c, (int) Math.round(cx + 2.8 * Math.cos(t)), (int) Math.round(cy + 0.8 + 1.8 * Math.sin(t)));
        }
        c.line(cx, cy + 3, 0, cx, cy - 2, 0, CHALK);
        dot(c, cx - 1, cy - 3);
        dot(c, cx - 2, cy - 2);
        for (int x = -2; x <= 2; x += 2) {
            dot(c, cx + x, cy + 1);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // clock

    private static void clock(BuildContext c, int cx, int cy) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                double r = Math.hypot(dx, dy);
                if (r <= 2.9) {
                    c.set(cx + dx, cy + dy, 0, Pal.WHITE);
                } else if (r <= 3.9) {
                    c.set(cx + dx, cy + dy, 0, r <= 3.5 ? Pal.DARK_OAK : Pal.log("stripped_dark_oak_wood", 'y'));
                    c.set(cx + dx, cy + dy, 1, Pal.DARK_OAK);
                }
            }
        }
        // hour marks and hands (ten past ten)
        for (int a = 0; a < 360; a += 30) {
            double t = Math.toRadians(a);
            c.set(cx + (int) Math.round(2.3 * Math.sin(t)), cy + (int) Math.round(2.3 * Math.cos(t)), 0, Pal.BLACK);
        }
        c.line(cx, cy, 0, cx + 2, cy + 1, 0, Pal.BLACK);      // minute hand towards 2
        c.line(cx, cy, 0, cx - 1, cy + 1, 0, Pal.BLACK);      // hour hand towards 10
        c.set(cx, cy, 1, Pal.BLACK);
    }

    // ---------------------------------------------------------------------------------------------
    // the two side areas of the front wall (sign = -1 west, +1 east)

    private static void sideDecor(BuildContext c, int sign) {
        int mid = sign * 28;
        // bookshelves along the wall foot with a plank top
        for (int x = 21; x <= 33; x++) {
            int xx = sign * x;
            c.fill(xx, 1, 1, xx, 3, 1, "minecraft:bookshelf");
            c.set(xx, 4, 1, Pal.slab("dark_oak", false));
        }
        // framed chart (west: map, east: chart)
        for (int dx = -5; dx <= 5; dx++) {
            for (int y = 7; y <= 13; y++) {
                boolean edge = Math.abs(dx) == 5 || y == 7 || y == 13;
                if (edge) {
                    c.set(mid + dx, y, 1, Pal.DARK_OAK);
                    continue;
                }
                String s;
                if (sign < 0) {
                    double land = Noise.fbm2(dx * 1.1 + 50, y * 1.4, 3.6, 41);
                    s = land > 0.6 ? (land > 0.7 ? "squidgame:pastel_peach" : "squidgame:pastel_mint")
                            : land > 0.54 ? "squidgame:pastel_yellow" : "squidgame:pastel_sky";
                } else {
                    // periodic-table style chart of coloured cells with gaps
                    boolean gap = ((dx + 6) & 1) == 1 || (y & 1) == 0;
                    String[] cols = {"squidgame:pastel_peach", "squidgame:pastel_yellow", "squidgame:pastel_mint",
                            "squidgame:pastel_sky", "squidgame:pastel_pink", "squidgame:pastel_lilac"};
                    s = gap ? Pal.CREAM_WORN : cols[(Noise.hashInt(dx, y, 0, 5) >>> 1) % cols.length];
                }
                c.set(mid + dx, y, 0, s);
            }
        }
        c.text(mid + 0.5, 14.2, 1.02, sign < 0 ? "WORLD MAP" : "CANDY CHART", "#5a3a1e", 1.6f, 0f, false);
        // speaker high up beside the board
        speaker(c, sign * 20, 13);
        // school flag on a pole
        flag(c, sign * 33, 15, sign < 0 ? "minecraft:pink_concrete" : "minecraft:orange_concrete");
    }

    /** Old wall speaker: wooden cabinet, grille and cone. */
    public static void speaker(BuildContext c, int x, int y) {
        c.fill(x - 1, y, 1, x + 1, y + 2, 1, Pal.DARK_OAK);
        c.fill(x, y + 1, 1, x, y + 1, 1, Pal.BLACK);
        c.set(x - 1, y + 1, 1, "minecraft:iron_bars");
        c.set(x + 1, y + 1, 1, "minecraft:iron_bars");
        c.set(x, y, 1, "minecraft:iron_bars");
        c.set(x, y + 2, 1, "minecraft:iron_bars");
        c.set(x, y + 1, 2, "minecraft:polished_andesite");
        c.set(x, y - 1, 1, Pal.stairs("dark_oak", "south", true));
    }

    /** Pole flag projecting from the wall. */
    private static void flag(BuildContext c, int x, int y, String cloth) {
        c.fill(x - 3 * Integer.signum(x), y + 2, 1, x + 2 * Integer.signum(x), y + 2, 1, Pal.log("stripped_spruce_wood", 'x'));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                c.set(x + dx, y + dy, 1, cloth);
            }
        }
        c.set(x, y, 1, Pal.WHITE);
        c.set(x - 3 * Integer.signum(x), y + 3, 1, "minecraft:gold_block");
        c.set(x + 2 * Integer.signum(x), y + 3, 1, "minecraft:gold_block");
    }
}
