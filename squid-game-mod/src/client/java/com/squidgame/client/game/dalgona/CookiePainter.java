package com.squidgame.client.game.dalgona;

import com.mojang.math.Axis;
import com.squidgame.core.dalgona.CrackPattern;
import com.squidgame.core.dalgona.DalgonaShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws the cookie of the carving screen: tin, honeycomb disc, the pressed groove with fragile spots marked, the
 * carved furrow with fresh-cut glints, the tolerance lane around the needle, growing cracks and impact decals, the
 * needle with its heat glow, plus the success "pop" and the shattering shards. Pure drawing: all state comes from the
 * {@link CookieModel} and the {@link View}.
 */
final class CookiePainter {
    private CookiePainter() {
    }

    static final ResourceLocation COOKIE = tex("cookie");
    static final ResourceLocation NEEDLE = tex("needle");
    static final ResourceLocation TABLE = tex("table");
    private static final ResourceLocation[] CRACK = {tex("crack_0"), tex("crack_1"), tex("crack_2"), tex("crack_3")};

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("squidgame", "textures/gui/dalgona/" + name + ".png");
    }

    // palette
    private static final int GROOVE_SHADOW = 0xB0482810;
    private static final int GROOVE = 0xFF8A5622;
    private static final int GROOVE_LIGHT = 0xA0F2C778;
    private static final int FURROW = 0xFF2B170A;
    private static final int FURROW_EDGE = 0xFF4A2A12;
    private static final int CHIP = 0xFFE2B060;
    private static final int CAUTION = 0xFFFFB030;
    private static final int CRACK_DARK = 0xE0160B04;
    private static final int CRACK_LIGHT = 0x60FFF0C8;

    /** Where and how the cookie is drawn this frame. */
    static final class View {
        float ox;
        float oy;
        float size;
        float k;
        long now;
        float alpha = 1f;
        double needleX;
        double needleY;
        boolean needleValid;
        boolean needleDown;
        /** 0 calm .. 1 about to crack. */
        float heat;
        /** Success pop animation 0..1, or -1. */
        float pop = -1f;
        /** The cookie is gone (shattered): only the shards are drawn. */
        boolean shattered;
        float pulse;
        /** Lick animation progress 0..1, or -1. */
        float lick = -1f;

        float sx(double u) {
            return ox + (float) u * k;
        }

        float sy(double v) {
            return oy + (float) v * k;
        }
    }

    // ------------------------------------------------------------------ background

    static void table(GuiGraphics g, int w, int h) {
        int tile = 96;
        for (int y = 0; y < h; y += tile) {
            for (int x = 0; x < w; x += tile) {
                g.blit(TABLE, x, y, Math.min(tile, w - x), Math.min(tile, h - y), 0f, 0f, 64 * Math.min(tile, w - x) / tile,
                        64 * Math.min(tile, h - y) / tile, 64, 64);
            }
        }
        g.fill(0, 0, w, h, 0xB0120A05);
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            float f = (1f - i / (float) steps);
            int a = (int) (110 * f * f);
            int dx = (int) (w * 0.20f * i / steps);
            int dy = (int) (h * 0.28f * i / steps);
            int ndx = (int) (w * 0.20f * (i + 1) / steps);
            int ndy = (int) (h * 0.28f * (i + 1) / steps);
            int col = a << 24;
            g.fill(dx, dy, w - dx, ndy, col);
            g.fill(dx, h - ndy, w - dx, h - dy, col);
            g.fill(dx, ndy, ndx, h - ndy, col);
            g.fill(w - ndx, ndy, w - dx, h - ndy, col);
        }
    }

    // ------------------------------------------------------------------ the cookie

    static void paint(GuiGraphics g, CookieModel m, View v) {
        float cx = v.ox + v.size / 2f;
        float cy = v.oy + v.size / 2f;
        tin(g, cx, cy, v.size);
        if (v.shattered) {
            // the cookie is gone: the bottom of the empty tin shows
            float r = v.size * 0.5f;
            Gfx.disc(g, cx, cy, r * 0.985f, 0xFF2A2B31, 40);
            Gfx.ring(g, cx, cy, r * 0.80f, r * 0.82f, 0x30FFFFFF, 40);
            Gfx.quad(g, cx - r * 0.7f, cy - r * 0.35f, cx - r * 0.25f, cy - r * 0.72f, cx - r * 0.1f, cy - r * 0.55f, cx - r * 0.55f, cy - r * 0.2f, 0x18FFFFFF);
            Gfx.end(g);
            return;
        }
        g.setColor(1f, 1f, 1f, v.alpha);
        g.blit(COOKIE, Math.round(v.ox), Math.round(v.oy), Math.round(v.size), Math.round(v.size), 0f, 0f, 128, 128, 128, 128);
        g.setColor(1f, 1f, 1f, 1f);

        Gfx.end(g);
        figureTint(g, m, v);
        if (v.needleValid && m.canCarve()) {
            lane(g, m, v);
        }
        groove(g, m, v);
        furrow(g, m, v);
        caution(g, m, v);
        Gfx.end(g);
        cracks(g, m, v);
        if (v.pop >= 0) {
            pop(g, m, v);
        }
        if (v.lick >= 0) {
            lickSheen(g, v);
        }
        decals(g, m, v);
    }

    /** The tin the cookie sits in: brushed aluminium rings and a shadow on the table. */
    private static void tin(GuiGraphics g, float cx, float cy, float size) {
        float r = size * 0.5f;
        Gfx.disc(g, cx + size * 0.02f, cy + size * 0.035f, r * 1.12f, 0x70000000, 40);
        Gfx.disc(g, cx, cy, r * 1.09f, 0xFF3C3D44, 40);
        Gfx.ring(g, cx, cy, r * 1.0f, r * 1.09f, 0xFFA7A9B3, 40);
        Gfx.ring(g, cx, cy, r * 1.045f, r * 1.07f, 0xFFD9DBE3, 40);
        Gfx.ring(g, cx, cy, r * 0.985f, r * 1.0f, 0xFF50525B, 40);
        Gfx.end(g);
    }

    private static final Map<DalgonaShape, float[][]> SPANS = new EnumMap<>(DalgonaShape.class);

    /** Horizontal spans (canvas units, pairs of x0, x1) covering the figure, one row per 8 canvas units. */
    private static float[][] spans(DalgonaShape s) {
        return SPANS.computeIfAbsent(s, shape -> {
            int rows = DalgonaShape.CANVAS / 8;
            float[][] out = new float[rows][];
            int vn = shape.vertexCount();
            double[] xs = new double[vn];
            for (int r = 0; r < rows; r++) {
                double y = (r + 0.5) * 8;
                int c = 0;
                for (int i = 0, j = vn - 1; i < vn; j = i++) {
                    double yi = shape.vertexY(i), yj = shape.vertexY(j);
                    if ((yi > y) != (yj > y)) {
                        xs[c++] = (shape.vertexX(j) - shape.vertexX(i)) * (y - yi) / (yj - yi) + shape.vertexX(i);
                    }
                }
                java.util.Arrays.sort(xs, 0, c);
                float[] row = new float[c];
                for (int i = 0; i < c; i++) {
                    row[i] = (float) xs[i];
                }
                out[r] = row;
            }
            return out;
        });
    }

    /** A faint warm tint over the figure so it reads as the piece that has to come out. */
    private static void figureTint(GuiGraphics g, CookieModel m, View v) {
        float[][] sp = spans(m.shape);
        int col = Gfx.fade(0x30FFE2A0, v.alpha);
        for (int r = 0; r < sp.length; r++) {
            float[] row = sp[r];
            for (int i = 0; i + 1 < row.length; i += 2) {
                Gfx.rect(g, v.sx(row[i]), v.sy(r * 8), v.sx(row[i + 1]), v.sy(r * 8 + 8), col);
            }
        }
        Gfx.end(g);
    }

    /** The tolerance lane around the groove near the needle: the part inside the figure is tinted red, it cuts deeper. */
    private static void lane(GuiGraphics g, CookieModel m, View v) {
        double tol = m.params.tolerance();
        double reach = 150;
        DalgonaShape s = m.shape;
        int n = m.n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double d = Math.hypot(s.sampleX(i) - v.needleX, s.sampleY(i) - v.needleY);
            if (d > reach) {
                continue;
            }
            float fade = (float) (1.0 - d / reach);
            float ix0 = s.sampleX(i), iy0 = s.sampleY(i), ix1 = s.sampleX(j), iy1 = s.sampleY(j);
            float nx0 = m.normalX[i] * m.insideSign[i], ny0 = m.normalY[i] * m.insideSign[i];
            float nx1 = m.normalX[j] * m.insideSign[j], ny1 = m.normalY[j] * m.insideSign[j];
            float t = (float) tol;
            int inner = Gfx.argb((int) (46 * fade * v.alpha), 0xFF7048);
            int outer = Gfx.argb((int) (34 * fade * v.alpha), 0xFFF2D0);
            // inside half (towards the figure) and outside half of the lane
            Gfx.quad(g, v.sx(ix0), v.sy(iy0), v.sx(ix1), v.sy(iy1), v.sx(ix1 + nx1 * t), v.sy(iy1 + ny1 * t), v.sx(ix0 + nx0 * t), v.sy(iy0 + ny0 * t), inner);
            Gfx.quad(g, v.sx(ix0), v.sy(iy0), v.sx(ix1), v.sy(iy1), v.sx(ix1 - nx1 * t), v.sy(iy1 - ny1 * t), v.sx(ix0 - nx0 * t), v.sy(iy0 - ny0 * t), outer);
        }
        Gfx.end(g);
    }

    /** The pressed groove: shadow, body and a lit edge, so it reads as an engraving. */
    private static void groove(GuiGraphics g, CookieModel m, View v) {
        DalgonaShape s = m.shape;
        int n = m.n;
        float w = Math.max(1.4f, v.k * 7f);
        float off = Math.max(0.6f, v.k * 2.2f);
        for (int i = 0; i < n; i++) {
            if (m.carvedAt(i) && m.carvedAt((i + 1) % n)) {
                continue;
            }
            int j = (i + 1) % n;
            float x0 = v.sx(s.sampleX(i)), y0 = v.sy(s.sampleY(i)), x1 = v.sx(s.sampleX(j)), y1 = v.sy(s.sampleY(j));
            Gfx.line(g, x0 + off, y0 + off, x1 + off, y1 + off, w, Gfx.fade(GROOVE_SHADOW, v.alpha));
            Gfx.line(g, x0, y0, x1, y1, w, Gfx.fade(GROOVE, v.alpha));
            Gfx.line(g, x0 - off * 0.7f, y0 - off * 0.7f, x1 - off * 0.7f, y1 - off * 0.7f, w * 0.45f, Gfx.fade(GROOVE_LIGHT, v.alpha));
        }
    }

    /** What has been carved away: a dark furrow with crumbs, and a bright glint where the needle just cut. */
    private static void furrow(GuiGraphics g, CookieModel m, View v) {
        DalgonaShape s = m.shape;
        int n = m.n;
        float w = Math.max(2f, v.k * 12f);
        for (int i = 0; i < n; i++) {
            if (!m.carvedAt(i)) {
                continue;
            }
            int j = (i + 1) % n;
            float x0 = v.sx(s.sampleX(i)), y0 = v.sy(s.sampleY(i)), x1 = v.sx(s.sampleX(j)), y1 = v.sy(s.sampleY(j));
            if (m.carvedAt(j)) {
                Gfx.line(g, x0, y0, x1, y1, w * 1.25f, Gfx.fade(FURROW_EDGE, v.alpha));
                Gfx.line(g, x0, y0, x1, y1, w, Gfx.fade(FURROW, v.alpha));
            } else {
                Gfx.disc(g, x0, y0, w * 0.55f, Gfx.fade(FURROW, v.alpha), 8);
            }
            // crumbs next to the furrow, fixed per sample
            int h = (int) (((long) i * 2654435761L) >>> 7);
            if ((h & 3) == 0) {
                float side = ((h >> 2) & 1) == 0 ? 1f : -1f;
                float cxm = (x0 + x1) / 2f + m.normalX[i] * side * w * (0.9f + ((h >> 3) & 3) * 0.25f);
                float cym = (y0 + y1) / 2f + m.normalY[i] * side * w * (0.9f + ((h >> 3) & 3) * 0.25f);
                float cs = Math.max(0.8f, v.k * (2.5f + ((h >> 5) & 3)));
                Gfx.rect(g, cxm - cs / 2, cym - cs / 2, cxm + cs / 2, cym + cs / 2, Gfx.fade(CHIP, v.alpha));
            }
            long age = v.now - m.carvedAt[i];
            if (age >= 0 && age < 380) {
                float f = 1f - age / 380f;
                Gfx.disc(g, x0, y0, w * (0.8f + 0.5f * f), Gfx.argb((int) (190 * f * v.alpha), 0xFFF0B0), 10);
            }
        }
    }

    /** Amber marks along the fragile stretches of the groove; they wake up when the needle gets close. */
    private static void caution(GuiGraphics g, CookieModel m, View v) {
        DalgonaShape s = m.shape;
        int n = m.n;
        float len = Math.max(2.5f, v.k * 16f);
        float w = Math.max(1f, v.k * 4f);
        for (int i = 0; i < n; i += 2) {
            double f = s.fragility(i);
            if (f < 1.35 || m.carvedAt(i)) {
                continue;
            }
            float near = 0f;
            if (v.needleValid) {
                near = (float) Math.max(0.0, 1.0 - Math.hypot(s.sampleX(i) - v.needleX, s.sampleY(i) - v.needleY) / 170.0);
            }
            float strength = (float) Math.min(1.0, (f - 1.2) / 1.3);
            float a = (0.24f + 0.36f * near + 0.12f * (float) Math.sin(v.pulse * 3 + i * 0.3)) * strength;
            float cx = v.sx(s.sampleX(i)), cy = v.sy(s.sampleY(i));
            float nx = m.normalX[i], ny = m.normalY[i];
            Gfx.line(g, cx - nx * len, cy - ny * len, cx + nx * len, cy + ny * len, w, Gfx.argb((int) (255 * Math.min(1f, a) * v.alpha), CAUTION & 0xFFFFFF));
        }
    }

    /** Hairline cracks, grown to match the stress. */
    private static void cracks(GuiGraphics g, CookieModel m, View v) {
        double stress = m.cracked ? 100 : m.stressShown;
        float w = Math.max(1f, v.k * 5f);
        float off = Math.max(0.5f, v.k * 1.6f);
        for (CrackPattern.Branch b : m.cracks) {
            double shown = CrackPattern.revealed(b, stress);
            if (shown <= 0) {
                continue;
            }
            int full = (int) Math.floor(shown);
            for (int i = 0; i <= full && i < b.segments(); i++) {
                double t = i < full ? 1.0 : shown - full;
                if (t <= 0) {
                    break;
                }
                float x0 = v.sx(b.xs()[i]), y0 = v.sy(b.ys()[i]);
                float x1 = x0 + (v.sx(b.xs()[i + 1]) - x0) * (float) t;
                float y1 = y0 + (v.sy(b.ys()[i + 1]) - y0) * (float) t;
                Gfx.line(g, x0 + off, y0 + off, x1 + off, y1 + off, w * 0.8f, Gfx.fade(CRACK_LIGHT, v.alpha));
                Gfx.line(g, x0, y0, x1, y1, w, Gfx.fade(CRACK_DARK, v.alpha));
            }
        }
        Gfx.end(g);
    }

    /** Impact marks where stress spiked, drawn from the crack textures. */
    private static void decals(GuiGraphics g, CookieModel m, View v) {
        for (CookieModel.Decal d : m.decals) {
            float age = (v.now - d.bornMs) / 1000f;
            float a = Math.max(0.35f, 0.95f - age * 0.08f);
            float size = v.k * (150f + 40f * d.severity);
            g.pose().pushPose();
            g.pose().translate(v.sx(d.x), v.sy(d.y), 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(d.rotation));
            g.setColor(0.10f, 0.05f, 0.02f, a * v.alpha);
            g.blit(CRACK[d.severity], Math.round(-size / 2), Math.round(-size / 2), Math.round(size), Math.round(size), 0f, 0f, 64, 64, 64, 64);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }
    }

    /** The freed figure lifts out of the cookie. */
    private static void pop(GuiGraphics g, CookieModel m, View v) {
        float e = easeOut(v.pop);
        float[][] sp = spans(m.shape);
        float scale = 1f + 0.12f * e;
        float lift = -v.size * 0.025f * e;
        float cx = 512f, cy = 512f;
        int min = sp.length, max = 0;
        for (int r = 0; r < sp.length; r++) {
            if (sp[r].length >= 2) {
                min = Math.min(min, r);
                max = Math.max(max, r);
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int r = 0; r < sp.length; r++) {
                float[] row = sp[r];
                int col = pass == 0 ? Gfx.argb((int) (120 * e), 0x000000)
                        : Gfx.lerpColor(0xFFF8D88A, 0xFFD9983A, (r - min) / (float) Math.max(1, max - min));
                float so = pass == 0 ? v.size * 0.03f * e : 0f;
                for (int i = 0; i + 1 < row.length; i += 2) {
                    float x0 = v.sx(cx + (row[i] - cx) * scale) + so;
                    float x1 = v.sx(cx + (row[i + 1] - cx) * scale) + so;
                    float y0 = v.sy(cy + (r * 8 - cy) * scale) + lift + so;
                    float y1 = v.sy(cy + (r * 8 + 8 - cy) * scale) + lift + so;
                    Gfx.rect(g, x0, y0, x1, y1 + 0.6f, col);
                }
            }
        }
        // sparkles around the freed piece
        for (int i = 0; i < 9; i++) {
            double a = i * 2.399 + v.pop * 2.0;
            double rad = (200 + 80 * Math.sin(i * 1.7)) * (0.6 + 0.9 * v.pop);
            float life = Math.max(0f, Math.min(1f, 1.8f - Math.abs(v.pop * 2f - 0.6f - (i % 3) * 0.25f) * 2f));
            Gfx.sparkle(g, v.sx(512 + Math.cos(a) * rad), v.sy(512 + Math.sin(a) * rad), v.k * (26f + 18f * (i % 3)), Gfx.argb((int) (230 * life), 0xFFF0A8));
        }
        Gfx.end(g);
    }

    private static void lickSheen(GuiGraphics g, View v) {
        float t = v.lick;
        float band = v.size * 0.22f;
        float pos = (-0.3f + 1.6f * easeInOut(t)) * v.size;
        float fade = (float) Math.sin(Math.PI * t);
        int col = Gfx.argb((int) (80 * fade), 0xFFFFFF);
        // a slanted wet sheen sweeping over the cookie, clipped to the disc row by row
        float cx = v.ox + v.size / 2f;
        float cy = v.oy + v.size / 2f;
        float r = v.size * 0.47f;
        float slant = band * 0.6f;
        int rows = Math.max(8, (int) r);
        for (int i = 0; i < rows; i++) {
            float y0 = cy - r + 2f * r * i / rows;
            float y1 = cy - r + 2f * r * (i + 1) / rows;
            float dy = (y0 + y1) / 2f - cy;
            float half = (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            float bx0 = v.ox + pos - slant * (dy + r) / (2f * r);
            float x0 = Math.max(bx0, cx - half);
            float x1 = Math.min(bx0 + band, cx + half);
            if (x1 > x0) {
                Gfx.rect(g, x0, y0, x1, y1 + 0.5f, col);
            }
        }
        for (int i = 0; i < 5; i++) {
            float dx = v.ox + v.size * (0.2f + 0.15f * i);
            float dy = v.oy + v.size * (0.25f + 0.12f * ((i * 3) % 5)) + t * v.size * 0.12f;
            Gfx.disc(g, dx, dy, v.size * 0.012f, Gfx.argb((int) (150 * fade), 0xFFE9A0), 8);
        }
        Gfx.end(g);
    }

    // ------------------------------------------------------------------ needle

    static void needle(GuiGraphics g, CookieModel m, View v, float mouseX, float mouseY, boolean active) {
        int rgb = v.heat < 0.5f ? Gfx.lerpColor(0xFF6FF2D0, 0xFFFFC040, v.heat * 2f) & 0xFFFFFF
                : Gfx.lerpColor(0xFFFFC040, 0xFFFF3A30, (v.heat - 0.5f) * 2f) & 0xFFFFFF;
        float r = v.size * (v.needleDown ? 0.05f : 0.032f);
        if (active) {
            Gfx.glow(g, mouseX, mouseY, r * (1f + 0.8f * v.heat), rgb, v.needleDown ? 1f : 0.55f);
            Gfx.disc(g, mouseX, mouseY, Math.max(1.3f, v.k * 6f), Gfx.argb(255, rgb), 10);
        }
        Gfx.end(g);
        float s = v.size / 128f * 0.8f;
        int w = Math.round(16 * s);
        int h = Math.round(32 * s);
        g.blit(NEEDLE, Math.round(mouseX), Math.round(mouseY), w, h, 0f, 0f, 16, 32, 16, 32);
    }

    // ------------------------------------------------------------------ shards

    /** A piece of the shattered cookie (a square of the cookie texture). */
    static final class Shard {
        float x;
        float y;
        float vx;
        float vy;
        float rot;
        float vrot;
        final int u;
        final int vv;

        Shard(float x, float y, float vx, float vy, float vrot, int u, int vv) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.vrot = vrot;
            this.u = u;
            this.vv = vv;
        }
    }

    static void shards(GuiGraphics g, java.util.List<Shard> shards, View v, float alpha) {
        float tile = 16f * v.size / 128f;
        for (Shard sh : shards) {
            g.pose().pushPose();
            g.pose().translate(sh.x, sh.y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(sh.rot));
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(COOKIE, Math.round(-tile / 2), Math.round(-tile / 2), Math.round(tile), Math.round(tile), sh.u, sh.vv, 16, 16, 128, 128);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }
    }

    static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }
}
