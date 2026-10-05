package com.squidgame.client.game.dalgona;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Small immediate-mode drawing helpers for the carving screens: float-coordinate quads, thick lines, discs and rings
 * batched through the GUI buffer. Quads are wound the way vanilla's {@code fill} winds them so the GUI render type's
 * face culling never swallows them whatever order the corners are given in. Call {@link #end} before drawing anything
 * that is not batched (textured blits) so the order of layers is kept.
 */
final class Gfx {
    private Gfx() {
    }

    /** Flushes everything batched so far. */
    static void end(GuiGraphics g) {
        g.flush();
    }

    static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Multiplies the alpha channel of a colour. */
    static int fade(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static void quad(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        float cross = (x1 - x0) * (y2 - y1) - (y1 - y0) * (x2 - x1);
        if (cross > 0) {
            vc.addVertex(m, x0, y0, 0).setColor(argb);
            vc.addVertex(m, x3, y3, 0).setColor(argb);
            vc.addVertex(m, x2, y2, 0).setColor(argb);
            vc.addVertex(m, x1, y1, 0).setColor(argb);
        } else {
            vc.addVertex(m, x0, y0, 0).setColor(argb);
            vc.addVertex(m, x1, y1, 0).setColor(argb);
            vc.addVertex(m, x2, y2, 0).setColor(argb);
            vc.addVertex(m, x3, y3, 0).setColor(argb);
        }
    }

    static void rect(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        quad(g, x0, y0, x1, y0, x1, y1, x0, y1, argb);
    }

    /** A line of the given thickness (flat ends). */
    static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float width, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) {
            float h = width / 2f;
            rect(g, x0 - h, y0 - h, x0 + h, y0 + h, argb);
            return;
        }
        float nx = -dy / len * width / 2f;
        float ny = dx / len * width / 2f;
        quad(g, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    static void tri(GuiGraphics g, float x0, float y0, float x1, float y1, float x2, float y2, int argb) {
        quad(g, x0, y0, x1, y1, x2, y2, x2, y2, argb);
    }

    static void disc(GuiGraphics g, float cx, float cy, float r, int argb) {
        disc(g, cx, cy, r, argb, 20);
    }

    static void disc(GuiGraphics g, float cx, float cy, float r, int argb, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            tri(g, cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, argb);
        }
    }

    /** A ring between two radii. */
    static void ring(GuiGraphics g, float cx, float cy, float r0, float r1, int argb, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(g, cx + c0 * r0, cy + s0 * r0, cx + c0 * r1, cy + s0 * r1, cx + c1 * r1, cy + s1 * r1, cx + c1 * r0, cy + s1 * r0, argb);
        }
    }

    /** A circular sector of a ring (used for cooldown sweeps), angles in radians clockwise from 12 o'clock. */
    static void sector(GuiGraphics g, float cx, float cy, float r, double from, double to, int argb) {
        int steps = Math.max(2, (int) Math.ceil((to - from) / (Math.PI / 12)));
        for (int i = 0; i < steps; i++) {
            double a0 = from + (to - from) * i / steps - Math.PI / 2;
            double a1 = from + (to - from) * (i + 1) / steps - Math.PI / 2;
            tri(g, cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, argb);
        }
    }

    /** Soft glow: a few concentric discs whose alpha adds up towards the centre. */
    static void glow(GuiGraphics g, float cx, float cy, float radius, int rgb, float strength) {
        for (int i = 0; i < 5; i++) {
            float f = 1f - i / 5f;
            disc(g, cx, cy, radius * f, argb((int) (255 * strength * 0.16f), rgb), 18);
        }
    }

    /** A four-pointed sparkle. */
    static void sparkle(GuiGraphics g, float cx, float cy, float size, int argb) {
        float t = size * 0.18f;
        tri(g, cx, cy - size, cx + t, cy, cx - t, cy, argb);
        tri(g, cx, cy + size, cx + t, cy, cx - t, cy, argb);
        tri(g, cx - size, cy, cx, cy + t, cx, cy - t, argb);
        tri(g, cx + size, cy, cx, cy + t, cx, cy - t, argb);
    }
}
