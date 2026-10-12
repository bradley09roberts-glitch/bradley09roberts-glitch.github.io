package com.grandline.client.render;

import com.grandline.ship.ShipClass;

import java.util.*;

/**
 * Customisation geometry for ships: carved figureheads (box models cached per carving and class) and the pixel
 * emblems flown on flags and painted on the main sail. Coordinates are ship-local (x across, y up from the
 * waterline, z toward the bow), like {@link ShipMesh}.
 */
public final class ShipDecor {
    public static final int T_PAINT = 10, T_SCALES = 11, T_MANE = 12, T_FEATHER = 13, T_BONE = 14, T_DARK = 15;

    /** One coloured quad: 4 corners, atlas tile, ARGB tint, normal. */
    public record Quad(float[] v, int tile, int argb, float nx, float ny, float nz) {}

    // ================================================================== emblems (rows top to bottom, X = filled)

    public static final Map<String, String[]> EMBLEMS = new LinkedHashMap<>();

    static {
        EMBLEMS.put("skull", new String[]{
                "....XXX....",
                "..XXXXXXX..",
                ".XXXXXXXXX.",
                ".XX..X..XX.",
                ".XX..X..XX.",
                ".XXXX.XXXX.",
                "..XXXXXXX..",
                "...X.X.X...",
                "XX.......XX",
                "..XX...XX..",
                "....XXX....",
                "..XX...XX..",
                "XX.......XX"});
        EMBLEMS.put("swords", new String[]{
                "X.........X",
                ".X.......X.",
                "..X.XXX.X..",
                "...XXXXX...",
                "...X.X.X...",
                "...XXXXX...",
                "....X.X....",
                "...X...X...",
                "..X.....X..",
                "XXX.....XXX",
                ".X.......X.",
                "X.........X"});
        EMBLEMS.put("hat", new String[]{
                "...XXXXX...",
                "..XXXXXXX..",
                "XXXXXXXXXXX",
                "...........",
                "..XXXXXXX..",
                ".XX..X..XX.",
                ".XX..X..XX.",
                ".XXXX.XXXX.",
                "..XXXXXXX..",
                "...X.X.X...",
                "X.........X",
                ".XXX...XXX.",
                "X...XXX...X"});
        EMBLEMS.put("flame", new String[]{
                ".....X.....",
                "....XX.....",
                "...XXX..X..",
                "..XXXX.XX..",
                "..XXXXXXX..",
                ".XXXX.XXXX.",
                ".XXX...XXX.",
                ".XX.....XX.",
                ".XX..X..XX.",
                "..X.XXX.X..",
                "..XXXXXXX..",
                "...XXXXX..."});
        EMBLEMS.put("anchor", new String[]{
                ".....X.....",
                "....X.X....",
                ".....X.....",
                "..XXXXXXX..",
                ".....X.....",
                ".....X.....",
                ".....X.....",
                "X....X....X",
                "XX...X...XX",
                ".XX..X..XX.",
                "..XXXXXXX..",
                "....XXX...."});
        EMBLEMS.put("star", new String[]{
                ".....X.....",
                ".....X.....",
                "....XXX....",
                "XXXXXXXXXXX",
                ".XXXXXXXXX.",
                "..XXXXXXX..",
                "...XXXXX...",
                "..XXXXXXX..",
                "..XXX.XXX..",
                ".XX.....XX.",
                ".X.......X."});
        EMBLEMS.put("crescent", new String[]{
                "....XXXX...",
                "..XXX......",
                ".XX........",
                ".XX......X.",
                "XX......X..",
                "XX.....X...",
                "XX....X....",
                "XX..XX.....",
                ".XX.X......",
                ".XXX.......",
                "..XXX......",
                "....XXXX..."});
        EMBLEMS.put("marine", new String[]{
                "...........",
                "XX.......XX",
                ".XX.....XX.",
                "..XX...XX..",
                "...XX.XX...",
                "....XXX....",
                ".....X.....",
                "...........",
                "XXXXXXXXXXX",
                "...........",
                ".XXXXXXXXX."});
    }

    public static String[] emblem(String id) {
        return EMBLEMS.getOrDefault(id, EMBLEMS.get("skull"));
    }

    private static final Map<String[], List<int[]>> RUNS = new IdentityHashMap<>();

    /** Horizontal runs of filled pixels: {row, colStart, colEnd(exclusive)} (cached per bitmap). */
    public static List<int[]> runs(String[] bmp) {
        return RUNS.computeIfAbsent(bmp, ShipDecor::computeRuns);
    }

    private static List<int[]> computeRuns(String[] bmp) {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < bmp.length; r++) {
            String row = bmp[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != 'X') {
                    c++;
                    continue;
                }
                int s = c;
                while (c < row.length() && row.charAt(c) == 'X') c++;
                out.add(new int[]{r, s, c});
            }
        }
        return out;
    }

    // ================================================================== figureheads

    private static final Map<String, List<Quad>> CACHE = new HashMap<>();

    public static List<Quad> figurehead(String id, ShipClass c) {
        return CACHE.computeIfAbsent(id + "/" + c.id(), k -> build(id, c));
    }

    /** Builder in carving units: origin at the bow tip on deck level, scaled to the hull. */
    private static final class B {
        final List<Quad> q = new ArrayList<>();
        final float s, oy, oz;

        B(ShipClass c) {
            s = c == ShipClass.ROWBOAT ? 0.4f : Math.max(0.6f, Math.min(1.45f, c.length / 17f));
            oy = c.freeboard;
            oz = c.length / 2f;
        }

        void box(float x0, float y0, float z0, float x1, float y1, float z1, int tile, int rgb) {
            int argb = 0xFF000000 | rgb;
            float X0 = x0 * s, X1 = x1 * s, Y0 = oy + y0 * s, Y1 = oy + y1 * s, Z0 = oz + z0 * s, Z1 = oz + z1 * s;
            q.add(new Quad(new float[]{X1, Y0, Z0, X1, Y0, Z1, X1, Y1, Z1, X1, Y1, Z0}, tile, argb, 1, 0, 0));
            q.add(new Quad(new float[]{X0, Y0, Z1, X0, Y0, Z0, X0, Y1, Z0, X0, Y1, Z1}, tile, argb, -1, 0, 0));
            q.add(new Quad(new float[]{X1, Y0, Z1, X0, Y0, Z1, X0, Y1, Z1, X1, Y1, Z1}, tile, argb, 0, 0, 1));
            q.add(new Quad(new float[]{X0, Y0, Z0, X1, Y0, Z0, X1, Y1, Z0, X0, Y1, Z0}, tile, argb, 0, 0, -1));
            q.add(new Quad(new float[]{X0, Y1, Z0, X1, Y1, Z0, X1, Y1, Z1, X0, Y1, Z1}, tile, argb, 0, 1, 0));
            q.add(new Quad(new float[]{X0, Y0, Z1, X1, Y0, Z1, X1, Y0, Z0, X0, Y0, Z0}, tile, argb, 0, -1, 0));
        }

        /** Mirrored pair (x and -x). */
        void pair(float x0, float y0, float z0, float x1, float y1, float z1, int tile, int rgb) {
            box(x0, y0, z0, x1, y1, z1, tile, rgb);
            box(-x1, y0, z0, -x0, y1, z1, tile, rgb);
        }
    }

    private static final int GOLD = 0xE2B33C;

    private static List<Quad> build(String id, ShipClass c) {
        B b = new B(c);
        switch (id) {
            case "lion" -> {
                b.box(-0.35f, -0.9f, -0.3f, 0.35f, 0.6f, 0.5f, ShipMesh.T_RAIL, 0xFFFFFF);
                int mane = 0xE07A1E, face = 0xF2C64A;
                b.box(-1.1f, 0.2f, 0.3f, 1.1f, 2.4f, 0.9f, T_MANE, mane);
                b.pair(1.1f, 0.6f, 0.35f, 1.4f, 2.0f, 0.85f, T_MANE, mane);
                b.box(-0.7f, 2.4f, 0.35f, 0.7f, 2.7f, 0.85f, T_MANE, mane);
                b.box(-0.7f, -0.1f, 0.35f, 0.7f, 0.2f, 0.85f, T_MANE, mane);
                b.box(-0.75f, 0.5f, 0.9f, 0.75f, 2.0f, 1.6f, T_PAINT, face);
                b.box(-0.42f, 0.55f, 1.6f, 0.42f, 1.2f, 2.05f, T_PAINT, 0xF7DC86);
                b.box(-0.16f, 1.05f, 2.05f, 0.16f, 1.25f, 2.1f, T_DARK, 0xFFFFFF);
                b.box(-0.3f, 0.62f, 2.05f, 0.3f, 0.68f, 2.08f, T_DARK, 0xFFFFFF);
                b.pair(0.22f, 1.42f, 1.6f, 0.52f, 1.66f, 1.64f, T_DARK, 0xFFFFFF);
                b.pair(0.45f, 1.95f, 1.0f, 0.85f, 2.35f, 1.3f, T_PAINT, face);
            }
            case "ram" -> {
                b.box(-0.35f, -0.9f, -0.3f, 0.35f, 0.8f, 0.6f, ShipMesh.T_RAIL, 0xFFFFFF);
                b.box(-0.55f, 0.4f, 0.4f, 0.55f, 1.45f, 1.5f, T_MANE, 0xEDE6D6);
                b.box(-0.36f, 0.35f, 1.5f, 0.36f, 0.95f, 2.0f, T_PAINT, 0xD9CDB5);
                b.box(-0.2f, 0.62f, 2.0f, 0.2f, 0.75f, 2.03f, T_DARK, 0xFFFFFF);
                b.pair(0.2f, 1.05f, 1.5f, 0.42f, 1.22f, 1.53f, T_DARK, 0xFFFFFF);
                int horn = 0xC9A86A;
                b.pair(0.55f, 1.05f, 0.5f, 1.05f, 1.55f, 1.1f, T_BONE, horn);
                b.pair(0.95f, 0.5f, 0.55f, 1.4f, 1.25f, 1.0f, T_BONE, horn);
                b.pair(0.75f, 0.15f, 0.85f, 1.15f, 0.6f, 1.35f, T_BONE, horn);
                b.pair(0.55f, 0.25f, 1.3f, 0.85f, 0.5f, 1.55f, T_BONE, horn);
            }
            case "swan" -> {
                int w = 0xF8F8F8;
                b.box(-0.6f, -0.7f, -0.3f, 0.6f, 0.5f, 1.0f, T_FEATHER, w);
                b.box(-0.2f, 0.5f, 0.7f, 0.2f, 1.1f, 1.1f, T_FEATHER, w);
                b.box(-0.2f, 1.1f, 0.9f, 0.2f, 1.7f, 1.3f, T_FEATHER, w);
                b.box(-0.2f, 1.7f, 0.85f, 0.2f, 2.3f, 1.25f, T_FEATHER, w);
                b.box(-0.2f, 2.3f, 0.95f, 0.2f, 2.8f, 1.35f, T_FEATHER, w);
                b.box(-0.26f, 2.7f, 1.0f, 0.26f, 3.1f, 1.7f, T_FEATHER, w);
                b.box(-0.12f, 2.76f, 1.7f, 0.12f, 2.95f, 2.25f, T_PAINT, 0xF08A20);
                b.box(-0.14f, 2.85f, 1.6f, 0.14f, 3.02f, 1.76f, T_DARK, 0xFFFFFF);
                b.pair(0.26f, 2.9f, 1.35f, 0.28f, 3.0f, 1.47f, T_DARK, 0xFFFFFF);
                b.pair(0.6f, -0.3f, -1.3f, 0.95f, 0.75f, 0.8f, T_FEATHER, 0xEDEDED);
            }
            case "dragon" -> {
                int g = 0x3A8A4A, g2 = 0x2A6A36, bone = 0xE0D4A8;
                b.box(-0.42f, -0.9f, -0.3f, 0.42f, 1.2f, 0.7f, T_SCALES, g);
                b.box(-0.6f, 1.0f, 0.5f, 0.6f, 1.8f, 1.9f, T_SCALES, g);
                b.box(-0.45f, 1.15f, 1.9f, 0.45f, 1.65f, 2.85f, T_SCALES, g);
                b.box(-0.4f, 0.62f, 1.8f, 0.4f, 0.95f, 2.65f, T_SCALES, g2);
                b.pair(0.25f, 0.95f, 2.1f, 0.35f, 1.15f, 2.2f, T_BONE, bone);
                b.pair(0.25f, 0.95f, 2.45f, 0.35f, 1.15f, 2.55f, T_BONE, bone);
                b.pair(0.22f, 1.5f, 2.85f, 0.32f, 1.6f, 2.88f, T_DARK, 0xFFFFFF);
                b.pair(0.6f, 1.42f, 1.25f, 0.64f, 1.64f, 1.6f, T_PAINT, 0xF4D23A);
                b.pair(0.35f, 1.8f, 0.6f, 0.55f, 2.6f, 0.8f, T_BONE, bone);
                b.pair(0.38f, 2.4f, 0.25f, 0.52f, 2.55f, 0.6f, T_BONE, bone);
                for (int i = 0; i < 4; i++) b.box(-0.07f, 1.2f - i * 0.5f + 0.6f, 0.05f - i * 0.15f, 0.07f, 1.5f - i * 0.5f + 0.6f, 0.3f - i * 0.15f, T_PAINT, 0xC83A2A);
            }
            case "skull" -> {
                int bone = 0xEEE8D8;
                b.box(-0.3f, -0.9f, -0.3f, 0.3f, 0.4f, 0.5f, ShipMesh.T_RAIL, 0x707070);
                b.box(-1.45f, 0.2f, 0.3f, 1.45f, 0.42f, 0.52f, T_BONE, bone);
                b.box(-1.45f, 1.35f, 0.3f, 1.45f, 1.57f, 0.52f, T_BONE, bone);
                b.pair(1.3f, 0.08f, 0.25f, 1.6f, 0.55f, 0.58f, T_BONE, bone);
                b.pair(1.3f, 1.22f, 0.25f, 1.6f, 1.7f, 0.58f, T_BONE, bone);
                b.box(-0.75f, 0.35f, 0.4f, 0.75f, 1.85f, 1.7f, T_BONE, bone);
                b.pair(0.15f, 1.0f, 1.7f, 0.55f, 1.42f, 1.73f, T_DARK, 0xFFFFFF);
                b.box(-0.1f, 0.72f, 1.7f, 0.1f, 0.95f, 1.73f, T_DARK, 0xFFFFFF);
                b.box(-0.55f, -0.05f, 0.6f, 0.55f, 0.36f, 1.55f, T_BONE, bone);
                for (float x = -0.42f; x < 0.45f; x += 0.21f) b.box(x, 0.22f, 1.55f, x + 0.05f, 0.45f, 1.58f, T_DARK, 0xFFFFFF);
            }
            case "serpent" -> {
                int blue = 0x2A6EA0, fin = 0xD04A4A;
                b.box(-0.42f, -0.9f, -0.3f, 0.42f, 0.4f, 0.6f, T_SCALES, blue);
                b.box(-0.4f, 0.2f, 0.4f, 0.4f, 1.0f, 1.2f, T_SCALES, blue);
                b.box(-0.4f, 0.8f, 1.0f, 0.4f, 1.6f, 1.8f, T_SCALES, blue);
                b.box(-0.48f, 1.4f, 1.6f, 0.48f, 2.15f, 2.85f, T_SCALES, blue);
                b.box(-0.36f, 1.25f, 2.0f, 0.36f, 1.45f, 2.7f, T_SCALES, 0x1E5480);
                b.pair(0.48f, 1.55f, 1.6f, 0.95f, 2.4f, 1.95f, T_PAINT, fin);
                b.box(-0.06f, 2.15f, 1.2f, 0.06f, 2.6f, 2.2f, T_PAINT, fin);
                b.pair(0.48f, 1.8f, 2.25f, 0.52f, 1.98f, 2.5f, T_PAINT, 0xF4D23A);
                b.box(-0.05f, 1.42f, 2.85f, 0.05f, 1.48f, 3.3f, T_PAINT, 0xD02A2A);
            }
            default -> b.box(-0.3f, -0.4f, -0.2f, 0.3f, 0.6f, 1.2f, ShipMesh.T_GOLD, 0xFFFFFF); // classic scroll
        }
        if (!id.equals("classic") && !id.equals("skull") && !id.equals("swan")) b.box(-0.18f, -0.4f, -0.4f, 0.18f, 0.1f, 0.1f, T_PAINT, GOLD); // gilded mount
        return b.q;
    }

    private ShipDecor() {}
}
