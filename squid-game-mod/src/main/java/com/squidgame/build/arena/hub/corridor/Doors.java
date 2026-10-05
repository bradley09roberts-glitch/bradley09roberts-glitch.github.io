package com.squidgame.build.arena.hub.corridor;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.corridor.Plan.Door;
import com.squidgame.build.arena.hub.corridor.Plan.Space;

/**
 * Cuts the doorways through the one-block walls and gives each one a frame: black jambs and header, a symbol cube in
 * the cornice, and a number plate (text display) on the header band. All openings are open (no door leaves) so humans
 * and vanilla path finding can walk through; the large passages (full height) get pilaster frames instead.
 */
final class Doors {
    private Doors() {
    }

    static void buildAll(BuildContext c) {
        for (Door d : Plan.DOORS) {
            cut(c, d);
        }
    }

    private static int minHeight(Door d) {
        int h = Integer.MAX_VALUE;
        if (d.a != null) {
            h = Math.min(h, d.a.h);
        }
        if (d.b != null) {
            h = Math.min(h, d.b.h);
        }
        return h;
    }

    static String title(Space s) {
        if (s == null) {
            return "DORMITORY";
        }
        return switch (s.name) {
            case "checkpoint" -> "CHECKPOINT";
            case "canteen" -> "CANTEEN";
            case "infirmary" -> "INFIRMARY";
            case "barracks" -> "BARRACKS";
            case "monitor" -> "MONITORING";
            case "armory" -> "ARMORY";
            case "office" -> "MANAGER";
            case "store" -> "STORAGE";
            case "branch" -> "STAFF WING";
            case "e2", "e2_vestibule" -> "STAIRWAY";
            default -> "CORRIDOR";
        };
    }

    private static void cut(BuildContext c, Door d) {
        int top = minHeight(d);
        boolean big = d.h >= 6;
        // opening + threshold
        c.fill(d.x0, 0, d.z0, d.x1, d.h - 1, d.z1, Pal.AIR);
        // glowing threshold (functional light for both sides of the doorway)
        for (int x = d.x0; x <= d.x1; x++) {
            for (int z = d.z0; z <= d.z1; z++) {
                int off = d.alongX ? Math.abs(x - (d.x0 + d.x1) / 2) : Math.abs(z - (d.z0 + d.z1) / 2);
                boolean lit = !big || off <= 1;
                c.set(x, Plan.FLOOR_Y, z, lit ? Pal.LIGHT_WHITE : Pal.TILE_PINK);
            }
        }
        // jamb cells (lower side / higher side of the opening)
        int[][] jambs = d.alongX
                ? new int[][]{{d.x0 - 1, d.z0}, {d.x1 + 1, d.z0}}
                : new int[][]{{d.x0, d.z0 - 1}, {d.x0, d.z1 + 1}};
        for (int[] j : jambs) {
            for (int y = 0; y < top; y++) {
                c.set(j[0], y, j[1], y == 0 ? Pal.BLACK : (big ? Pal.PINK : Pal.BLACK));
            }
        }
        if (!big) {
            // header band and cornice with the symbol cube
            for (int x = d.x0 - (d.alongX ? 1 : 0); x <= d.x1 + (d.alongX ? 1 : 0); x++) {
                for (int z = d.z0 - (d.alongX ? 0 : 1); z <= d.z1 + (d.alongX ? 0 : 1); z++) {
                    c.set(x, d.h, z, Pal.BLACK);
                }
            }
            if (d.h + 1 < top) {
                int cx = (d.x0 + d.x1) / 2, cz = (d.z0 + d.z1) / 2;
                c.set(cx, d.h + 1, cz, Pal.symbol(d.number));
            }
            plate(c, d);
        } else {
            // full-height passage: a black header beam is not allowed (clear height must stay 6), so the frame
            // is a pair of pink pilasters with black edges
            for (int[] j : jambs) {
                int ox = d.alongX ? (j[0] < d.x0 ? -1 : 1) : 0;
                int oz = d.alongX ? 0 : (j[1] < d.z0 ? -1 : 1);
                // second pilaster layer (black) on both faces of the wall to give depth
                for (int y = 1; y < top; y++) {
                    c.set(j[0], y, j[1], y % 5 == 0 ? Pal.BLACK : Pal.PINK);
                }
                c.set(j[0], 0, j[1], Pal.LIGHT_WHITE);
            }
        }
    }

    /** Number plate: white text on the black header band, readable from both sides. */
    private static void plate(BuildContext c, Door d) {
        String num = String.format("%02d", d.number);
        double y = d.h + 0.3;
        if (d.alongX) {
            double x = d.centre();
            double zw = d.z0;
            if (d.a != null) {
                c.text(x, y, zw - 0.04, num, "white", 2.0f, 180f, false);
            }
            if (d.b != null) {
                c.text(x, y, zw + 1.04, num, "white", 2.0f, 0f, false);
            }
        } else {
            double z = d.centre();
            double xw = d.x0;
            if (d.a != null) {
                c.text(xw - 0.04, y, z, num, "white", 2.0f, 90f, false);
            }
            if (d.b != null) {
                c.text(xw + 1.04, y, z, num, "white", 2.0f, -90f, false);
            }
        }
    }
}
