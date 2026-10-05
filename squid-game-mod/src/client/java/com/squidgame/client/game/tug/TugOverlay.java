package com.squidgame.client.game.tug;

import com.squidgame.game.tug.TugNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Tug of War overlay, drawn on top of the tournament HUD from the server's snapshots:
 * <ul>
 *   <li>the rope bar under the objective panel: the flag, the rope's velocity, both teams' stamina and which side is yours;</li>
 *   <li>the beat ring: a ring that closes onto the target on every beat, with the hit window as a band; it flashes on the beat;
 *       a heave verdict (PERFECT / GOOD / TOO EARLY ...) pops up after every heave;</li>
 *   <li>the personal panel: stamina (flashing when exhausted), the team's sync and the real bound keys with the held ones lit.</li>
 * </ul>
 * Spectators and players waiting for their heat see the rope bar and a dim ring so that they can learn the rhythm.
 */
final class TugOverlay {
    private TugOverlay() {
    }

    private static final int PINK = 0xFFE0457B;
    private static final int PANEL = 0xB0101018;
    private static final int[] TEAM_RGB = {0xE04848, 0x4A86F0};
    private static final double RING_MIN = 11, RING_MAX = 32;
    /** The rope bar sits under the tournament's objective panel; the other elements keep clear of it. */
    private static final int ROPE_BAR_Y = 82, ROPE_BAR_BOTTOM = ROPE_BAR_Y + 18;

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        TugInput.frame(mc);
        TugNet.StatePayload s = TugClientState.state();
        if (s == null || mc.options.hideGui || mc.player == null || s.stage() == TugNet.STAGE_NONE) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        boolean pulling = s.role() == TugNet.ROLE_TEAM_A || s.role() == TugNet.ROLE_TEAM_B;
        drawRopeBar(g, font, s, w, pulling);
        boolean rhythm = s.stage() == TugNet.STAGE_COUNT_IN || s.stage() == TugNet.STAGE_MATCH || s.stage() == TugNet.STAGE_SUDDEN;
        if (rhythm) {
            drawBeatRing(g, font, s, w, h, pulling);
        }
        if (pulling && rhythm) {
            drawPersonal(g, font, s, w, h);
        }
    }

    // ------------------------------------------------------------------ rope bar

    private static void drawRopeBar(GuiGraphics g, Font font, TugNet.StatePayload s, int w, boolean pulling) {
        int bw = Math.min(w - 40, 250);
        int bx = (w - bw) / 2;
        int by = ROPE_BAR_Y;
        int bh = 10;
        int half = bw / 2;
        g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 8, PANEL);
        g.renderOutline(bx - 3, by - 3, bw + 6, bh + 11, PINK);
        g.fill(bx, by, bx + half, by + bh, 0xFF5A2020);
        g.fill(bx + half, by, bx + bw, by + bh, 0xFF1F3A66);
        // the last fifth on each side is the edge: whoever's end of the rope reaches it falls
        int edge = bw / 5;
        g.fill(bx, by, bx + 2, by + bh, 0xFFFF3030);
        g.fill(bx + bw - 2, by, bx + bw, by + bh, 0xFFFF3030);
        g.fill(bx + 2, by, bx + edge / 2, by + bh, 0x40FF4040);
        g.fill(bx + bw - edge / 2, by, bx + bw - 2, by + bh, 0x40FF4040);
        g.fill(bx + half, by - 1, bx + half + 1, by + bh + 1, 0x80FFFFFF);
        // stamina of both teams, thin strips under the bar growing from the middle outwards
        int sa = (int) (half * Mth.clamp(s.staminaA(), 0f, 1f));
        int sb = (int) (half * Mth.clamp(s.staminaB(), 0f, 1f));
        g.fill(bx + half - sa, by + bh + 2, bx + half, by + bh + 4, 0xFFE04848);
        g.fill(bx + half, by + bh + 2, bx + half + sb, by + bh + 4, 0xFF4A86F0);
        // the flag
        float offset = Mth.clamp(s.offset(), -1f, 1f);
        int mx = bx + half + Math.round(offset * (half - 2));
        boolean risky = Math.abs(offset) > 0.8f;
        int flag = risky && (System.nanoTime() / 150_000_000L) % 2 == 0 ? 0xFFFFD040 : 0xFFFFFFFF;
        g.fill(mx - 1, by - 3, mx + 1, by + bh + 3, flag);
        g.fill(mx + 1, by - 3, mx + 6, by + 1, 0xFFE02020);
        // the rope's velocity as chevrons beside the flag
        double v = s.velocity() * 20.0;
        if (Math.abs(v) > 0.015) {
            int dir = v > 0 ? 1 : -1;
            int n = Math.abs(v) > 0.12 ? 3 : Math.abs(v) > 0.05 ? 2 : 1;
            for (int i = 0; i < n; i++) {
                int cx = mx + dir * (8 + i * 4);
                g.fill(cx, by + 3, cx + 1, by + 7, 0xFFFFFFFF);
                g.fill(cx - dir, by + 2, cx - dir + 1, by + 3, 0xFFFFFFFF);
                g.fill(cx - dir, by + 7, cx - dir + 1, by + 8, 0xFFFFFFFF);
            }
        }
        // labels
        g.drawString(font, Component.translatable("squidgame.game.tug_of_war.hud.red"), bx + 3, by + 1, 0xFFFFB0B0, true);
        Component blue = Component.translatable("squidgame.game.tug_of_war.hud.blue");
        g.drawString(font, blue, bx + bw - 3 - font.width(blue), by + 1, 0xFFB0C8FF, true);
        if (pulling) {
            Component you = Component.translatable("squidgame.game.tug_of_war.hud.you");
            boolean teamA = s.role() == TugNet.ROLE_TEAM_A;
            int yx = teamA ? bx + 3 : bx + bw - 3 - font.width(you);
            g.drawString(font, you, yx, by + bh + 5, 0xFF000000 | TEAM_RGB[teamA ? 0 : 1], true);
        }
    }

    // ------------------------------------------------------------------ beat ring

    private static void drawBeatRing(GuiGraphics g, Font font, TugNet.StatePayload s, int w, int h, boolean pulling) {
        int period = Math.max(2, s.beatPeriod());
        double tf = TugClientState.tickNow();
        double since = tf - s.beatEpoch();
        double phase = (since % period + period) % period / period;      // 0 right after a beat, towards 1 before the next
        float visible = pulling ? 1f : 0.35f;
        int cx = w / 2;
        int cy = (int) (h * 0.58);
        double window = Math.max(0.5, s.window());
        double bandPx = (RING_MAX - RING_MIN) * window / period;
        // the hit window: a band around the target ring
        ring(g, cx, cy, RING_MIN + bandPx, 1, argb(0.30f * visible, 0xFFFFFF));
        ring(g, cx, cy, RING_MIN - bandPx, 1, argb(0.30f * visible, 0xFFFFFF));
        // the target: it lights up in the window around the beat and flashes just after it
        double distance = Math.min(phase, 1 - phase) * period;
        boolean inWindow = distance <= window;
        float flash = phase < 0.2 ? (float) (1 - phase / 0.2) : 0f;
        int targetRgb = inWindow ? 0xFFE060 : 0xFFFFFF;
        ring(g, cx, cy, RING_MIN, 2, argb((0.55f + 0.45f * flash) * visible, targetRgb));
        if (flash > 0.05f) {
            ring(g, cx, cy, RING_MIN + 10 * (1 - flash), 1, argb(0.6f * flash * visible, 0xFFE060));
        }
        // the ring that closes in on the target
        double r = RING_MIN + (RING_MAX - RING_MIN) * (1 - phase);
        float a = (float) (0.25 + 0.75 * phase * phase) * visible;
        ring(g, cx, cy, r, 2, argb(a, inWindow ? 0xFFE060 : 0xFFFFFF));
        // count-in: how many beats until the pull starts
        if (s.stage() == TugNet.STAGE_COUNT_IN) {
            int left = (int) Math.ceil(-since / period - 1e-6);
            if (left >= 1 && left <= 9) {
                String t = Integer.toString(left);
                g.pose().pushPose();
                g.pose().translate(cx, cy - 7, 0);
                g.pose().scale(2f, 2f, 1f);
                g.drawCenteredString(font, t, 0, 0, argb(visible, 0xFFE060));
                g.pose().popPose();
            }
        }
        if (pulling) {
            drawFeedback(g, font, cx, cy);
        }
    }

    private static void drawFeedback(GuiGraphics g, Font font, int cx, int cy) {
        long since = TugClientState.sinceFeedback();
        long life = 800_000_000L;
        if (since >= life) {
            return;
        }
        float t = since / (float) life;
        int result = TugClientState.feedbackResult();
        int error = TugClientState.feedbackError();
        float quality = TugClientState.feedbackQuality();
        String key;
        int rgb;
        if (result == TugNet.HEAVE_HIT) {
            if (quality >= 0.9f) {
                key = "perfect";
                rgb = 0xFFD84A;
            } else if (quality >= 0.6f) {
                key = "good";
                rgb = 0x60E080;
            } else {
                key = "ok";
                rgb = 0xE0E0E0;
            }
        } else if (result == TugNet.HEAVE_EXHAUSTED) {
            key = "tired";
            rgb = 0xFF5050;
        } else {
            key = error < 0 ? "early" : "late";
            rgb = error < 0 ? 0xFFA030 : 0xFF5050;
        }
        Component text = Component.translatable("squidgame.game.tug_of_war.feedback." + key);
        // beside the ring, on the side away from the personal panel, so it never covers the rope in the middle
        int y = cy - 5 - (int) (t * 10);
        float scale = result == TugNet.HEAVE_HIT && quality >= 0.9f ? 1.6f : 1.2f;
        g.pose().pushPose();
        g.pose().translate(cx - RING_MAX - 8, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -font.width(text), 0, argb(1f - t * t, rgb), true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ personal panel

    private static void drawPersonal(GuiGraphics g, Font font, TugNet.StatePayload s, int w, int h) {
        int pw = 154;
        int ph = 66;
        int px = w - pw - 6;
        // at the right edge between the rope bar and the hotbar row, so that neither the hotbar nor the hunger bar covers it
        int py = Math.max(ROPE_BAR_BOTTOM + 6, h / 2 - ph / 2 + 10);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, PINK);
        int teamRgb = TEAM_RGB[s.role() == TugNet.ROLE_TEAM_A ? 0 : 1];
        g.drawString(font, Component.translatable("squidgame.game.tug_of_war.hud.stamina"), px + 5, py + 4, 0xFFCFCFCF, true);
        if (s.exhausted()) {
            boolean on = (System.nanoTime() / 200_000_000L) % 2 == 0;
            Component ex = Component.translatable("squidgame.game.tug_of_war.hud.exhausted");
            g.drawString(font, ex, px + pw - 5 - font.width(ex), py + 4, on ? 0xFFFF4040 : 0xFFA02020, true);
        }
        int bx = px + 5;
        int bw = pw - 10;
        float st = Mth.clamp(s.stamina(), 0f, 1f);
        int col = s.exhausted() ? 0xFFC03030 : st > 0.5f ? 0xFF50D890 : st > 0.25f ? 0xFFFFC040 : 0xFFFF5040;
        g.fill(bx - 1, py + 14, bx + bw + 1, py + 24, 0xAA000000);
        g.fill(bx, py + 15, bx + (int) (bw * st), py + 23, col);
        // where stamina starts to tell: below half the pull force falls
        g.fill(bx + bw / 2, py + 14, bx + bw / 2 + 1, py + 24, 0x80FFFFFF);
        // team sync: the share of the team in a heave burst
        g.drawString(font, Component.translatable("squidgame.game.tug_of_war.hud.sync"), px + 5, py + 27, 0xFF9A9AA8, false);
        float sync = Mth.clamp(s.sync() * 2f, 0f, 1f);
        int sx = px + 5 + font.width(Component.translatable("squidgame.game.tug_of_war.hud.sync")) + 5;
        int sw = px + pw - 5 - sx;
        g.fill(sx, py + 29, sx + sw, py + 33, 0xAA000000);
        g.fill(sx, py + 29, sx + (int) (sw * sync), py + 33, sync > 0.5f ? 0xFFFFD84A : teamRgb | 0xFF000000);
        // the keys, as the player has bound them
        int kw = (pw - 10 - 8) / 3;
        key(g, font, TugInput.PULL, "pull", px + 5, py + 39, kw, s.stance() == 1 && !s.exhausted());
        key(g, font, TugInput.HEAVE, "heave", px + 5 + kw + 4, py + 39, kw, TugClientState.sinceFeedback() < 150_000_000L);
        key(g, font, TugInput.BRACE, "brace", px + 5 + 2 * (kw + 4), py + 39, kw, s.stance() == 2 || s.stance() == 3);
    }

    private static void key(GuiGraphics g, Font font, KeyMapping key, String label, int x, int y, int w, boolean lit) {
        boolean held = TugInput.held(key);
        int bg = lit || held ? 0xFF3A7A4A : 0xFF2A2A34;
        g.fill(x, y, x + w, y + 22, bg);
        g.renderOutline(x, y, w, 22, held ? 0xFF9AFFB0 : 0xFF55556A);
        String name = key.getTranslatedKeyMessage().getString();
        int nameWidth = Math.max(1, font.width(name));
        float fit = Math.min(1f, (w - 4) / (float) nameWidth);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + 3 + (1f - fit) * 3f, 0);
        g.pose().scale(fit, fit, 1f);
        g.drawCenteredString(font, name, 0, 0, 0xFFFFFFFF);
        g.pose().popPose();
        g.drawCenteredString(font, Component.translatable("squidgame.game.tug_of_war.hud.key." + label), x + w / 2, y + 12, 0xFFB0B0C0);
    }

    // ------------------------------------------------------------------ drawing helpers

    private static int argb(float alpha, int rgb) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** A circle outline made of small squares (crisp at every GUI scale, no custom render type needed). */
    private static void ring(GuiGraphics g, int cx, int cy, double radius, int thickness, int argb) {
        if (radius < 1) {
            return;
        }
        int points = Math.max(24, (int) (Math.PI * 2 * radius / 1.6));
        int half = thickness / 2;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            g.fill(x - half, y - half, x - half + thickness, y - half + thickness, argb);
        }
    }
}
