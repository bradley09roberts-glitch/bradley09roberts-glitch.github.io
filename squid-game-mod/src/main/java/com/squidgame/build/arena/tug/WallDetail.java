package com.squidgame.build.arena.tug;

import com.squidgame.build.BuildContext;

/**
 * Poured-concrete skin of the hall and its equipment: panelled walls with expansion joints, hazard plinths, the big
 * painted title, ducts, pipes, caged lamps and the freight doors at hall-floor level.
 */
final class WallDetail {
    private WallDetail() {
    }

    static void build(BuildContext c) {
        skin(c);
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            longWall(c, sgn);
        }
        endWalls(c);
    }

    // ------------------------------------------------------------------ skin

    private static void skin(BuildContext c) {
        // long walls (inner face = first wall layer), west half mirrored east
        Sym.pattern(c, Geo.HX0, 1, -Geo.HZ - 1, -1, Geo.CEIL - 1, -Geo.HZ - 1, (x, y, z) -> Surf.wall(x, y));
        Sym.pattern(c, Geo.HX0, 1, Geo.HZ + 1, -1, Geo.CEIL - 1, Geo.HZ + 1, (x, y, z) -> Surf.wall(x, y));
        // end walls above the plateau (u = z, plinth sits on the plateau floor)
        Sym.pattern(c, Geo.HX0 - 1, Geo.DECK + 1, -Geo.HZ, Geo.HX0 - 1, Geo.CEIL - 1, Geo.HZ, (x, y, z) -> Surf.wall(z, y - Geo.DECK));
        // pier faces toward the pit: clean concrete above the ring, dirty pit wall below
        Sym.pattern(c, Geo.PIER_X1, 1, -Geo.HZ, Geo.PIER_X1, Geo.DECK - 1, Geo.HZ, (x, y, z) -> Surf.wall(z, y));
    }

    // ------------------------------------------------------------------ long walls (sgn -1 = north z<0, +1 = south z>0)

    private static void longWall(BuildContext c, int sgn) {
        final int zf = sgn * (Geo.HZ + 1);             // wall face layer
        final int zi = sgn * Geo.HZ;                    // first free cell in front of it
        final int zi3 = sgn * (Geo.HZ - 2);

        // --- title plate: black backing with the big painted title (14 tall, 102 wide)
        c.fill(-53, 43, zf, 52, 58, zf, Pal.BLACK);
        for (int x = -53; x <= 52; x++) {
            c.set(x, 43, zf, Pal.hazard(x));
            c.set(x, 58, zf, Pal.hazard(x + 2));
        }
        String title = "TUG OF WAR";
        int w = Glyphs.width(title, 2);                 // 102
        if (sgn < 0) {
            Glyphs.paint(c, title, -w / 2, 44, zf, true, +1, 2, Pal.YELLOW);
        } else {
            Glyphs.paint(c, title, w / 2 - 1, 44, zf, true, -1, 2, Pal.YELLOW);
        }

        // --- hazard bands under the lower gallery and under the upper catwalk
        for (int x = Geo.HX0; x <= Geo.HX1; x++) {
            c.set(x, 36, zf, Pal.hazard(x));
            c.set(x, 37, zf, Pal.hazard(x + 2));
        }
        for (int x = GalleryBuilder.CAT_X0; x <= -GalleryBuilder.CAT_X0 - 1; x++) {
            c.set(x, 56, zf, Pal.hazard(x));
            c.set(x, 57, zf, Pal.hazard(x + 2));
        }

        // --- air duct above the upper catwalk (3x3, y 62..64), flanges every 8 blocks, grilles on the room side
        Sym.fill(c, Geo.HX0, 62, Math.min(zi3, zi), -1, 64, Math.max(zi3, zi), Pal.SMOOTH);
        for (int x = -73; x <= -1; x += 8) {
            Sym.fill(c, x, 61, Math.min(zi3, zi), x + 1, 65, Math.max(zi3, zi), Pal.IRON);
        }
        for (int x = -69; x <= -3; x += 8) {
            Sym.fill(c, x, 63, zi3, x + 2, 63, zi3, Pal.BARS);
        }

        // --- caged lamps above the duct and at hall-floor level, one per panel
        for (int x = -69; x <= -9; x += 10) {
            cage(c, x, 67, zi);
            cage(c, x, 12, zi);
        }

        // --- pipe runs at hall-floor level (two parallel pipes, brackets every 10 blocks)
        for (int y : new int[]{24, 25}) {
            Sym.fill(c, Geo.HX0, y, zi, -1, y, zi, "minecraft:oxidized_copper");
        }
        for (int x = -70; x <= -1; x += 10) {
            Sym.fill(c, x, 23, zi, x + 1, 26, zi, Pal.IRON);
        }
        // vertical riser pipes beside every second joint
        for (int x = -60; x <= -10; x += 20) {
            Sym.fill(c, x, 1, zi, x, 23, zi, "minecraft:oxidized_copper");
            Sym.fill(c, x, 1, zi, x, 3, zi, Pal.YELLOW);
        }

        // --- freight doors at hall-floor level: 12 wide x 12 high, recessed, hazard frame, double steel leaves
        door(c, sgn, -31);
    }

    /** A 3x3x1 cage light: lantern core inside iron bars, mounted on the wall face. */
    private static void cage(BuildContext c, int x, int y, int zi) {
        Sym.fill(c, x - 1, y - 1, zi, x + 2, y + 2, zi, Pal.BARS);
        Sym.fill(c, x, y, zi, x + 1, y + 1, zi, Pal.SEA);
    }

    private static void door(BuildContext c, int sgn, int xw) {
        int zf = sgn * (Geo.HZ + 1);
        int zr = sgn * (Geo.HZ + 2);                    // recessed leaf plane
        int xe = xw + 11;
        // recess
        Sym.fill(c, xw, 1, zf, xe, 12, zf, Pal.AIR);
        // frame: jambs, lintel with hazard, header band
        Sym.fill(c, xw - 2, 1, zf, xw - 1, 14, zf, Pal.BLACK);
        Sym.fill(c, xe + 1, 1, zf, xe + 2, 14, zf, Pal.BLACK);
        for (int x = xw - 2; x <= xe + 2; x++) {
            c.set(x, 13, zf, Pal.hazard(x));
            c.set(x, 14, zf, Pal.hazard(x + 2));
            c.set(Sym.mx(x), 13, zf, Pal.hazard(x));
            c.set(Sym.mx(x), 14, zf, Pal.hazard(x + 2));
        }
        // leaves: iron panels with a rivet grid, centre seam
        for (int x = xw; x <= xe; x++) {
            for (int y = 1; y <= 12; y++) {
                String st = Pal.IRON;
                if (x == xw + 5 || x == xw + 6) {
                    st = Pal.BLACK;                                  // seam
                } else if ((x - xw) % 3 == 1 && y % 4 == 1) {
                    st = Pal.STEEL_C;                                // rivets
                } else if (y == 6 || y == 7) {
                    st = (x - xw) % 2 == 0 ? Pal.STEEL : Pal.IRON;   // push bar
                }
                Sym.set(c, x, y, zr, st);
            }
        }
        // hazard apron on the hall floor in front of the door
        for (int x = xw - 2; x <= xe + 2; x++) {
            for (int dz = 0; dz < 3; dz++) {
                String hz = Pal.hazard(x + dz);
                Sym.set(c, x, 0, sgn * (Geo.HZ - dz), hz);
            }
        }
        // lamp over the door
        Sym.fill(c, xw + 2, 15, zf, xw + 9, 15, zf, Pal.STEEL);
        Sym.fill(c, xw + 3, 16, sgn * Geo.HZ, xw + 4, 16, sgn * Geo.HZ, Pal.SEA);
        Sym.fill(c, xw + 7, 16, sgn * Geo.HZ, xw + 8, 16, sgn * Geo.HZ, Pal.SEA);
    }

    // ------------------------------------------------------------------ end walls and pier faces

    private static void endWalls(BuildContext c) {
        // giant team letters on the end walls above the rooms (A red in the west, B blue in the east), black plate behind
        Sym.fill(c, Geo.HX0 - 1, 52, -12, Geo.HX0 - 1, 74, 12, Pal.BLACK);
        // pier faces: team plate and giant letter facing the pit
        Sym.fill(c, Geo.PIER_X1, 8, -12, Geo.PIER_X1, 36, 12, Pal.BLACK);
        for (int z = -12; z <= 12; z++) {
            Sym.set(c, Geo.PIER_X1, 8, z, Pal.hazard(z));
            Sym.set(c, Geo.PIER_X1, 36, z, Pal.hazard(z + 2));
        }
        // letters: A on the west wall / pier (x runs toward the viewer), B mirrored on the east
        glyphWest(c, 'A', Geo.HX0 - 1, 53, 3, Pal.RED);
        glyphWest(c, 'A', Geo.PIER_X1, 12, 3, Pal.RED);
        glyphEast(c, 'B', Sym.mx(Geo.HX0 - 1), 53, 3, Pal.BLUE);
        glyphEast(c, 'B', Sym.mx(Geo.PIER_X1), 12, 3, Pal.BLUE);
    }

    /** One big letter on a wall plane x = fixed viewed from the east (west-side walls): reading direction = decreasing z? see below. */
    private static void glyphWest(BuildContext c, char ch, int x, int y0, int scale, String state) {
        // viewer stands east of the wall looking west: left-to-right is increasing z (looking -x, east... ) -> +z is to the left,
        // so draw with decreasing z from the right edge.
        int w = Glyphs.width(String.valueOf(ch), scale);
        Glyphs.paint(c, String.valueOf(ch), w / 2 - 1 + 0, y0, x, false, -1, scale, state);
    }

    private static void glyphEast(BuildContext c, char ch, int x, int y0, int scale, String state) {
        int w = Glyphs.width(String.valueOf(ch), scale);
        Glyphs.paint(c, String.valueOf(ch), -w / 2, y0, x, false, +1, scale, state);
    }
}
