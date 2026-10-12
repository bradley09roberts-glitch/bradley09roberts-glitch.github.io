package com.grandline.client;

import com.grandline.GrandLine;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Minimap in the top-right corner (toggle with Z): the terrain around the player, north up, the player's arrow, and
 * the tracked quest's route as a yellow trail to its target (an arrow on the rim when the target is off the map).
 * Under it: the current objective and the distance. M opens the full sea chart with the same route.
 */
@EventBusSubscriber(modid = GrandLine.MODID, value = Dist.CLIENT)
public final class Minimap {
    /** Texture size in blocks (one pixel each) and the part of it shown, in blocks. */
    private static final int TEX = 160, VIEW = 100, SIZE = 100;
    public static final int TRAIL = 0xFFFFD23C, TRAIL_EDGE = 0xFF5A3A00;
    private static final ResourceLocation ID = GrandLine.id("minimap");
    private static boolean enabled = true;
    private static DynamicTexture tex;
    private static int ox, oz;
    private static boolean sampled;

    private Minimap() {}

    public static void toggle() {
        enabled = !enabled;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.level == null || mc.player == null || mc.level.getGameTime() % 10 != 0) return;
        if (tex == null) {
            tex = new DynamicTexture(TEX, TEX, true);
            mc.getTextureManager().register(ID, tex);
        }
        NativeImage img = tex.getPixels();
        if (img == null) return;
        ox = mc.player.getBlockX();
        oz = mc.player.getBlockZ();
        TerrainSampler.sample(mc.level, ox, oz, TEX, img);
        tex.upload();
        sampled = true;
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (!enabled || p == null || mc.options.hideGui || !sampled || mc.getDebugOverlay().showDebugScreen()) return;
        Font font = mc.font;
        int x0 = g.guiWidth() - SIZE - 6, y0 = 6;
        boolean good = false, bad = false;
        for (var eff : p.getActiveEffects()) {
            if (!eff.isVisible()) continue;
            if (eff.getEffect().value().isBeneficial()) good = true;
            else bad = true;
        }
        y0 += (good ? 26 : 0) + (bad ? 26 : 0);
        double k = SIZE / (double) VIEW; // screen pixels per block
        double px = p.getX(), pz = p.getZ();
        int cx = x0 + SIZE / 2, cy = y0 + SIZE / 2;
        // frame
        g.fill(x0 - 3, y0 - 3, x0 + SIZE + 3, y0 + SIZE + 3, 0xE0101418);
        g.fill(x0 - 2, y0 - 2, x0 + SIZE + 2, y0 + SIZE + 2, 0xFF6E5A3A);
        g.fill(x0, y0, x0 + SIZE, y0 + SIZE, 0xFF1C3A5C);
        g.enableScissor(x0, y0, x0 + SIZE, y0 + SIZE);
        // terrain: the texture's centre is (ox, oz); shift by how far the player has moved since it was painted
        float u = (float) (px - (ox - TEX / 2.0) - VIEW / 2.0), v = (float) (pz - (oz - TEX / 2.0) - VIEW / 2.0);
        g.blit(ID, x0, y0, SIZE, SIZE, u, v, VIEW, VIEW, TEX, TEX);
        // the route and the target
        if (Guidance.here()) {
            int[] r = Guidance.route;
            double lx = px, lz = pz; // the trail starts at the player, then follows the route from its second point
            for (int i = r.length >= 4 ? 2 : 0; i + 1 < r.length; i += 2) {
                trail(g, cx, cy, k, px, pz, lx, lz, r[i], r[i + 1], x0, y0);
                lx = r[i];
                lz = r[i + 1];
            }
            if (r.length < 2) trail(g, cx, cy, k, px, pz, px, pz, Guidance.tx, Guidance.tz, x0, y0);
            double tdx = (Guidance.tx - px) * k, tdz = (Guidance.tz - pz) * k;
            if (Math.abs(tdx) < SIZE / 2.0 - 3 && Math.abs(tdz) < SIZE / 2.0 - 3) {
                marker(g, cx + (int) tdx, cy + (int) tdz);
            }
        }
        // the player: an arrow turned to where they face
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(p.getYRot() + 180));
        g.fill(-1, -5, 2, 4, 0xFF000000);
        g.fill(-3, -1, 4, 3, 0xFF000000);
        g.fill(0, -4, 1, 3, 0xFFFFFFFF);
        g.fill(-2, 0, 3, 2, 0xFFFFFFFF);
        g.pose().popPose();
        g.disableScissor();
        // target off the map: an arrow on the rim pointing at it
        if (Guidance.here()) {
            double tdx = (Guidance.tx - px) * k, tdz = (Guidance.tz - pz) * k;
            double m = Math.max(Math.abs(tdx), Math.abs(tdz));
            if (m >= SIZE / 2.0 - 3) {
                double s = (SIZE / 2.0 - 4) / m;
                int ex = cx + (int) (tdx * s), ez = cy + (int) (tdz * s);
                g.fill(ex - 3, ez - 3, ex + 4, ez + 4, TRAIL_EDGE);
                g.fill(ex - 2, ez - 2, ex + 3, ez + 3, TRAIL);
            }
        }
        g.drawString(font, "N", cx - 2, y0 + 2, 0xFFFFFFFF, true);
        // the objective and distance under the map
        int ty = y0 + SIZE + 6;
        if (Guidance.active()) {
            Component line = Guidance.objective != null ? Guidance.objective : Guidance.title;
            List<FormattedCharSequence> lines = font.split(line, SIZE + 40);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                g.drawString(font, lines.get(i), x0 + SIZE - font.width(lines.get(i)), ty, 0xFFFFE9A0, true);
                ty += 10;
            }
            Component where;
            if (Guidance.here()) {
                int dist = (int) Math.hypot(Guidance.tx - px, Guidance.tz - pz);
                where = Guidance.label == null ? Component.translatable("hud.grandline.guide.dist", dist)
                        : Component.translatable("hud.grandline.guide.where", Guidance.label, dist);
            } else if (Guidance.hasTarget && Guidance.label != null) {
                where = Component.translatable("hud.grandline.guide.elsewhere", Guidance.label);
            } else {
                where = Guidance.hint;
            }
            if (where != null) {
                for (FormattedCharSequence l : font.split(where, SIZE + 40)) {
                    g.drawString(font, l, x0 + SIZE - font.width(l), ty, 0xFFD0D0D0, true);
                    ty += 10;
                    if (ty > y0 + SIZE + 46) break;
                }
            }
        }
        Component keys = Component.translatable("hud.grandline.guide.keys");
        g.drawString(font, keys, x0 + SIZE - font.width(keys), ty + 1, 0x90FFFFFF, true);
    }

    /** One leg of the route as a thick yellow line, marched in screen pixels (clipped by the scissor). */
    private static void trail(GuiGraphics g, int cx, int cy, double k, double px, double pz, double ax, double az, double bx, double bz, int x0, int y0) {
        double sx = cx + (ax - px) * k, sy = cy + (az - pz) * k, ex = cx + (bx - px) * k, ey = cy + (bz - pz) * k;
        // skip legs entirely outside the square
        if (Math.max(sx, ex) < x0 - 2 || Math.min(sx, ex) > x0 + SIZE + 2 || Math.max(sy, ey) < y0 - 2 || Math.min(sy, ey) > y0 + SIZE + 2) return;
        double len = Math.hypot(ex - sx, ey - sy);
        int steps = (int) Math.min(4000, Math.max(1, len));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) (sx + (ex - sx) * t), y = (int) (sy + (ey - sy) * t);
            if (x < x0 - 2 || x > x0 + SIZE + 2 || y < y0 - 2 || y > y0 + SIZE + 2) continue;
            g.fill(x - 1, y - 1, x + 2, y + 2, TRAIL_EDGE);
        }
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) (sx + (ex - sx) * t), y = (int) (sy + (ey - sy) * t);
            if (x < x0 - 2 || x > x0 + SIZE + 2 || y < y0 - 2 || y > y0 + SIZE + 2) continue;
            g.fill(x, y, x + 1, y + 1, TRAIL);
        }
    }

    /** The target: a yellow diamond with a dark edge. */
    public static void marker(GuiGraphics g, int x, int y) {
        for (int dy = -5; dy <= 5; dy++) {
            int w = 5 - Math.abs(dy);
            g.fill(x - w, y + dy, x + w + 1, y + dy + 1, TRAIL_EDGE);
        }
        for (int dy = -3; dy <= 3; dy++) {
            int w = 3 - Math.abs(dy);
            g.fill(x - w, y + dy, x + w + 1, y + dy + 1, TRAIL);
        }
    }
}
