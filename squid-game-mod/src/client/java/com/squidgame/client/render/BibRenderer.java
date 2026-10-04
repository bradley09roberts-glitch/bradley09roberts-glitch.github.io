package com.squidgame.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws the white number bib with black digits (3x5 pixel font) as flat quads in the current pose-stack frame.
 * The frame origin is the bib centre; the quad faces -Z when {@code front} is true and +Z otherwise. All sizes are
 * in model pixels (1/16 block).
 */
public final class BibRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float PX = 1f / 16f;

    private static final String[] GLYPHS = {
            "111101101101111", // 0
            "010110010010111", // 1
            "111001111100111", // 2
            "111001111001111", // 3
            "101101111001001", // 4
            "111100111001111", // 5
            "111100111101111", // 6
            "111001001010010", // 7
            "111101111101111", // 8
            "111101111001111"  // 9
    };

    private BibRenderer() {
    }

    /** Draws a three digit number (zero padded) on a bib of the given pixel size. */
    public static void draw(PoseStack ps, MultiBufferSource buffers, int light, int overlay, int number, boolean front,
                            float bibW, float bibH) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        Matrix4f pose = ps.last().pose();
        float nz = front ? -1f : 1f;
        float dir = front ? 1f : -1f; // mirror x for the back so digits read correctly from behind
        // bib
        quad(vc, ps, pose, -bibW / 2 * PX, -bibH / 2 * PX, bibW / 2 * PX, bibH / 2 * PX, 0f, 1f, 1f, 1f, light, overlay, nz);
        // digits
        int n = Math.max(0, Math.min(999, number));
        int[] digits = {n / 100, (n / 10) % 10, n % 10};
        float unit = Math.min((bibW - 1.2f) / 11f, (bibH - 0.8f) / 5f) * PX;
        float totalW = 11f * unit;
        float startX = -totalW / 2f;
        float topY = 5f * unit / 2f;
        float zOff = (front ? -1f : 1f) * 0.002f;
        for (int d = 0; d < 3; d++) {
            String g = GLYPHS[digits[d]];
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    if (g.charAt(row * 3 + col) == '1') {
                        float cx = (d * 4 + col) * unit;
                        float x0 = startX + cx;
                        float x1 = x0 + unit;
                        float y1 = topY - row * unit;
                        float y0 = y1 - unit;
                        if (!front) {
                            float t = x0;
                            x0 = -x1;
                            x1 = -t;
                        }
                        quad(vc, ps, pose, x0, y0, x1, y1, zOff, 0.05f, 0.05f, 0.05f, light, overlay, nz);
                    }
                }
            }
        }
        if (dir == 0) {
            return;
        }
    }

    private static void quad(VertexConsumer vc, PoseStack ps, Matrix4f pose, float x0, float y0, float x1, float y1, float z,
                             float r, float g, float b, int light, int overlay, float nz) {
        int ri = (int) (r * 255), gi = (int) (g * 255), bi = (int) (b * 255);
        vc.addVertex(pose, x0, y0, z).setColor(ri, gi, bi, 255).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(ps.last(), 0f, 0f, nz);
        vc.addVertex(pose, x1, y0, z).setColor(ri, gi, bi, 255).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(ps.last(), 0f, 0f, nz);
        vc.addVertex(pose, x1, y1, z).setColor(ri, gi, bi, 255).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(ps.last(), 0f, 0f, nz);
        vc.addVertex(pose, x0, y1, z).setColor(ri, gi, bi, 255).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(ps.last(), 0f, 0f, nz);
    }
}
