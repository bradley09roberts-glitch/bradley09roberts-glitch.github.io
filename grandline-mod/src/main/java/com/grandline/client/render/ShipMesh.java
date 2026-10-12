package com.grandline.client.render;

import com.grandline.ship.ShipClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-built ship geometry in ship-local block coordinates (x across, y up from the waterline, z toward the bow).
 * Faces are split into 1x1 tiles so the 16px textures tile like real blocks.
 */
public final class ShipMesh {
    public static final int T_HULL = 0, T_DECK = 1, T_SAIL = 2, T_IRON = 3, T_RAIL = 4, T_WINDOW = 5, T_FLAG = 6, T_GOLD = 7, T_ROPE = 8, T_MARINE_FLAG = 9;
    public static final int TINT_NONE = 0, TINT_HULL = 1, TINT_SAIL = 2;

    /** One quad: 4 corners (x,y,z each), tile, tint, normal. */
    public record Quad(float[] v, int tile, int tint, float nx, float ny, float nz) {}

    public final List<Quad> quads = new ArrayList<>();
    public final ShipClass cls;

    private ShipMesh(ShipClass cls) {
        this.cls = cls;
    }

    private static final ShipMesh[] CACHE = new ShipMesh[ShipClass.values().length];

    public static ShipMesh of(ShipClass c) {
        ShipMesh m = CACHE[c.ordinal()];
        if (m == null) CACHE[c.ordinal()] = m = build(c);
        return m;
    }

    // ---- primitive emitters (split into unit tiles) ----

    void quadX(float x, float y0, float y1, float z0, float z1, boolean positive, int tile, int tint) {
        for (float y = y0; y < y1 - 1e-4f; y += 1) {
            float ya = y, yb = Math.min(y1, y + 1);
            for (float z = z0; z < z1 - 1e-4f; z += 1) {
                float za = z, zb = Math.min(z1, z + 1);
                if (positive) quads.add(new Quad(new float[]{x, ya, za, x, ya, zb, x, yb, zb, x, yb, za}, tile, tint, 1, 0, 0));
                else quads.add(new Quad(new float[]{x, ya, zb, x, ya, za, x, yb, za, x, yb, zb}, tile, tint, -1, 0, 0));
            }
        }
    }

    void quadZ(float z, float x0, float x1, float y0, float y1, boolean positive, int tile, int tint) {
        for (float y = y0; y < y1 - 1e-4f; y += 1) {
            float ya = y, yb = Math.min(y1, y + 1);
            for (float x = x0; x < x1 - 1e-4f; x += 1) {
                float xa = x, xb = Math.min(x1, x + 1);
                if (positive) quads.add(new Quad(new float[]{xb, ya, z, xa, ya, z, xa, yb, z, xb, yb, z}, tile, tint, 0, 0, 1));
                else quads.add(new Quad(new float[]{xa, ya, z, xb, ya, z, xb, yb, z, xa, yb, z}, tile, tint, 0, 0, -1));
            }
        }
    }

    void quadY(float y, float x0, float x1, float z0, float z1, boolean up, int tile, int tint) {
        for (float x = x0; x < x1 - 1e-4f; x += 1) {
            float xa = x, xb = Math.min(x1, x + 1);
            for (float z = z0; z < z1 - 1e-4f; z += 1) {
                float za = z, zb = Math.min(z1, z + 1);
                if (up) quads.add(new Quad(new float[]{xa, y, za, xa, y, zb, xb, y, zb, xb, y, za}, tile, tint, 0, 1, 0));
                else quads.add(new Quad(new float[]{xa, y, zb, xa, y, za, xb, y, za, xb, y, zb}, tile, tint, 0, -1, 0));
            }
        }
    }

    void box(float x0, float y0, float z0, float x1, float y1, float z1, int tile, int tint) {
        quadX(x1, y0, y1, z0, z1, true, tile, tint);
        quadX(x0, y0, y1, z0, z1, false, tile, tint);
        quadZ(z1, x0, x1, y0, y1, true, tile, tint);
        quadZ(z0, x0, x1, y0, y1, false, tile, tint);
        quadY(y1, x0, x1, z0, z1, true, tile, tint);
        quadY(y0, x0, x1, z0, z1, false, tile, tint);
    }

    static ShipMesh build(ShipClass c) {
        ShipMesh m = new ShipMesh(c);
        float half = c.length / 2f, top = c.freeboard, bot = -c.draft;
        int slices = c.length;
        float prevHb = -1;
        for (int i = 0; i < slices; i++) {
            float z0 = -half + i, z1 = z0 + 1;
            float t = (i + 0.5f) / slices;
            float hb = Math.round(c.halfBeamAt(t) * 2) / 2f;
            boolean quarter = t < 0.18f && c.interior;
            float deck = top + (quarter ? 1 : 0);
            // hull sides
            m.quadX(hb, bot, deck, z0, z1, true, T_HULL, TINT_HULL);
            m.quadX(-hb, bot, deck, z0, z1, false, T_HULL, TINT_HULL);
            m.quadY(bot, -hb, hb, z0, z1, false, T_HULL, TINT_HULL);
            m.quadY(deck, -hb, hb, z0, z1, true, T_DECK, TINT_NONE);
            // step faces between slices of different width / deck height
            if (i == 0) m.quadZ(z0, -hb, hb, bot, deck, false, quarter ? T_WINDOW : T_HULL, quarter ? TINT_NONE : TINT_HULL);
            if (i == slices - 1) m.quadZ(z1, -hb, hb, bot, deck, true, T_HULL, TINT_HULL);
            if (prevHb >= 0 && prevHb != hb) {
                float lo = Math.min(prevHb, hb), hi = Math.max(prevHb, hb);
                boolean facesBack = hb > prevHb; // wider now: exposed face points toward the stern
                m.quadZ(z0, lo, hi, bot, deck, !facesBack, T_HULL, TINT_HULL);
                m.quadZ(z0, -hi, -lo, bot, deck, !facesBack, T_HULL, TINT_HULL);
            }
            // quarterdeck front wall
            if (quarter && (i + 1.5f) / slices >= 0.18f) m.quadZ(z1, -hb, hb, top, top + 1, true, T_WINDOW, TINT_NONE);
            // rails
            if (c != ShipClass.ROWBOAT) {
                m.box(hb - 0.15f, deck, z0, hb, deck + 0.8f, z1, T_RAIL, TINT_NONE);
                m.box(-hb, deck, z0, -hb + 0.15f, deck + 0.8f, z1, T_RAIL, TINT_NONE);
            }
            prevHb = hb;
        }
        // (the figurehead is drawn per ship from ShipDecor: it is chosen at the shipyard)
        // masts and yards
        for (int mi = 0; mi < c.masts; mi++) {
            float mz = mastZ(c, mi);
            float h = mastHeight(c, mi);
            m.box(-0.25f, top, mz - 0.25f, 0.25f, top + h, mz + 0.25f, T_RAIL, TINT_NONE);
            float span = c.beam * 0.75f + 1;
            m.box(-span, top + h - 1.2f, mz - 0.12f, span, top + h - 0.9f, mz + 0.12f, T_RAIL, TINT_NONE);
            m.box(-span * 0.8f, top + 2.2f, mz - 0.1f, span * 0.8f, top + 2.45f, mz + 0.1f, T_RAIL, TINT_NONE);
        }
        // cannons
        for (int k = 0; k < c.cannons; k++) {
            int side = k % 2 == 0 ? 1 : -1;
            float lz = -half * 0.55f + (k / 2) * (c.length * 0.75f / Math.max(1, c.cannons / 2));
            float hb = c.halfBeamAt((lz + half) / c.length);
            float x0 = side > 0 ? hb - 0.6f : -hb - 0.9f, x1 = side > 0 ? hb + 0.9f : -hb + 0.6f;
            m.box(x0, top + 0.2f, lz - 0.25f, x1, top + 0.65f, lz + 0.25f, T_IRON, TINT_NONE);
        }
        // oars for the rowboat
        if (c == ShipClass.ROWBOAT) {
            m.box(-2.2f, top - 0.1f, -0.1f, 2.2f, top + 0.05f, 0.1f, T_RAIL, TINT_NONE);
            m.box(-0.6f, 0, -0.5f, 0.6f, top - 0.1f, 0.5f, T_DECK, TINT_NONE);
        }
        return m;
    }

    public static float mastZ(ShipClass c, int m) {
        float half = c.length / 2f;
        if (c.masts == 1) return c.length * 0.08f;
        return -half * 0.35f + m * (c.length * 0.6f / Math.max(1, c.masts - 1));
    }

    public static float mastHeight(ShipClass c, int m) {
        float base = Math.min(22, 5 + c.length * 0.55f);
        return c.masts == 3 && m == 1 ? base + 2 : base;
    }
}
