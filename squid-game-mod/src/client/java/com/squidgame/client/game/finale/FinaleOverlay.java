package com.squidgame.client.game.finale;

import com.squidgame.game.finale.FightStatePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The fight overlay: health and stamina of both fighters, the role banner, the circle capture, the warning when the
 * white line is close, hit markers and the words that float up when a blow lands, is blocked, parried or dodged, and
 * the coin toss before the duel. The server sends the numbers ({@link FightStatePayload}); nothing here is a rule.
 */
@Environment(EnvType.CLIENT)
final class FinaleOverlay {
    private static final int ATTACKER_COLOR = 0xFFE0457B, DEFENDER_COLOR = 0xFF4AA3FF;
    private static final int BLOCK_W = 124;
    /** First free row below the tournament's title and objective panel (6 + up to 64 high): everything of the fight starts here. */
    private static final int TOP = 76;
    private static final ResourceLocation HEART = icon("icon_heart"), STAMINA = icon("icon_stamina");

    private FinaleOverlay() {
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath("squidgame", "textures/gui/hud/" + name + ".png");
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        FightStatePayload s = FightClientState.fresh();
        if (s == null || mc.options.hideGui || mc.player == null || s.stage() == FightStatePayload.NONE) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        FightClientState.animate(s);
        if (s.stage() == FightStatePayload.COIN) {
            drawCoin(g, font, s, w, h);
            return;
        }
        if (s.stage() == FightStatePayload.CEREMONY) {
            drawCeremony(g, font, s, w);
            return;
        }
        // own fighter on the left (a spectator sees the attacker there)
        int mine = s.myRole() == 2 ? 1 : 0;
        drawFighter(g, font, s, mine, 8, TOP, s.myRole() != 0, false);
        drawFighter(g, font, s, 1 - mine, w - 8 - BLOCK_W, TOP, false, true);
        drawBanner(g, font, s, w);
        drawClock(g, font, s, w);
        drawCapture(g, font, s, w, h);
        drawLineWarning(g, font, s, w, h);
        drawThreat(g, font, s, w, h, mine);
        drawFeedback(g, font, w, h);
    }

    // ------------------------------------------------------------------ fighters

    private static void drawFighter(GuiGraphics g, Font font, FightStatePayload s, int idx, int x, int y, boolean me, boolean right) {
        boolean attacker = idx == 0;
        int roleColor = attacker ? ATTACKER_COLOR : DEFENDER_COLOR;
        g.fill(x - 3, y - 3, x + BLOCK_W + 3, y + 44, 0xA0101018);
        g.renderOutline(x - 3, y - 3, BLOCK_W + 6, 47, roleColor);
        Component title = Component.translatable(me ? "squidgame.game.final.overlay.you" : "squidgame.game.final.overlay.fighter",
                String.format("%03d", s.number()[idx]));
        Component role = Component.translatable(attacker ? "squidgame.game.final.overlay.attacker" : "squidgame.game.final.overlay.defender");
        g.drawString(font, title, x, y, 0xFFFFFFFF, true);
        g.drawString(font, role, x + BLOCK_W - font.width(role), y, roleColor, true);
        float hp = FightClientState.shownHealth[idx] / 1000f;
        int hpColor = hp > 0.5f ? 0xFF4CD964 : hp > 0.25f ? 0xFFFFC040 : 0xFFFF4040;
        g.blit(HEART, x, y + 10, 8, 8, 0f, 0f, 16, 16, 16, 16);
        bar(g, x + 10, y + 11, BLOCK_W - 10, 7, hp, hpColor);
        String hpText = Integer.toString(Math.round(FightClientState.shownHealth[idx] / 10f));
        g.drawString(font, hpText, x + BLOCK_W - font.width(hpText) - 2, y + 11, 0xFFFFFFFF, true);
        boolean exhausted = (s.flags()[idx] & FightStatePayload.FLAG_EXHAUSTED) != 0;
        boolean denied = me && System.nanoTime() - FightClientState.deniedAt < 350_000_000L;
        float st = FightClientState.shownStamina[idx] / 1000f;
        int stColor = exhausted || denied ? (System.nanoTime() / 120_000_000L % 2 == 0 ? 0xFFFF4040 : 0xFF802020) : 0xFFE8B84A;
        g.blit(STAMINA, x, y + 21, 8, 8, 0f, 0f, 16, 16, 16, 16);
        bar(g, x + 10, y + 23, BLOCK_W - 10, 4, st, stColor);
        StringBuilder flags = new StringBuilder();
        int f = s.flags()[idx];
        if ((f & FightStatePayload.FLAG_GUARD) != 0) {
            flags.append(Component.translatable("squidgame.game.final.overlay.guard").getString()).append("  ");
        }
        if ((f & FightStatePayload.FLAG_INVULNERABLE) != 0) {
            flags.append(Component.translatable("squidgame.game.final.overlay.dodge").getString()).append("  ");
        }
        if ((f & FightStatePayload.FLAG_CHARGING) != 0) {
            flags.append(Component.translatable("squidgame.game.final.overlay.charging").getString()).append("  ");
        }
        if ((f & FightStatePayload.FLAG_STAGGERED) != 0) {
            flags.append(Component.translatable("squidgame.game.final.overlay.staggered").getString()).append("  ");
        }
        if (exhausted) {
            flags.append(Component.translatable("squidgame.game.final.overlay.exhausted").getString());
        }
        g.drawString(font, flags.toString(), x, y + 31, 0xFFC8C8C8, false);
    }

    private static void bar(GuiGraphics g, int x, int y, int width, int height, float frac, int color) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xC0000000);
        g.fill(x, y, x + (int) (width * Mth.clamp(frac, 0f, 1f)), y + height, color);
    }

    /** What you have to do, from the moment the coin has landed (the title of the reveal is soon replaced by the countdown). */
    private static void drawBanner(GuiGraphics g, Font font, FightStatePayload s, int w) {
        if (s.myRole() == 0 || s.stage() != FightStatePayload.FIGHT && s.stage() != FightStatePayload.READY) {
            return;
        }
        boolean attacker = s.myRole() == 1;
        Component text = Component.translatable(attacker ? "squidgame.game.final.overlay.banner.attacker" : "squidgame.game.final.overlay.banner.defender");
        int tw = font.width(text) + 12;
        int x = (w - tw) / 2, y = TOP;
        g.fill(x, y, x + tw, y + 13, 0x90101018);
        g.renderOutline(x, y, tw, 13, attacker ? ATTACKER_COLOR : DEFENDER_COLOR);
        g.drawCenteredString(font, text, w / 2, y + 3, 0xFFFFFFFF);
    }

    /** Time left of this duel (the tournament's own timer above counts down the whole game). */
    private static void drawClock(GuiGraphics g, Font font, FightStatePayload s, int w) {
        if (s.stage() != FightStatePayload.FIGHT) {
            return;
        }
        int seconds = Math.max(0, s.ticksLeft() / 20);
        int color = seconds < 15 ? 0xFFFF5050 : seconds < 40 ? 0xFFFFC040 : 0xFFE0E0E0;
        g.drawCenteredString(font, String.format("%d:%02d", seconds / 60, seconds % 60), w / 2, TOP + 17, color);
    }

    // ------------------------------------------------------------------ circle, line, threats

    private static void drawCapture(GuiGraphics g, Font font, FightStatePayload s, int w, int h) {
        if (s.capture() <= 0) {
            return;
        }
        int bw = 120, x = (w - bw) / 2, y = h - 72;
        g.drawCenteredString(font, Component.translatable("squidgame.game.final.overlay.capture", s.capture()), w / 2, y - 11, 0xFFFFD84A);
        bar(g, x, y, bw, 6, s.capture() / 100f, 0xFFFFD84A);
    }

    /** Red edges of the screen and a warning when the white line is close: touching it loses the duel. */
    private static void drawLineWarning(GuiGraphics g, Font font, FightStatePayload s, int w, int h) {
        if (s.myRole() == 0 || s.edge() < 0 || s.edge() >= 30 || s.stage() != FightStatePayload.FIGHT) {
            return;
        }
        float closeness = 1f - s.edge() / 30f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.nanoTime() / 90_000_000.0);
        vignette(g, w, h, 0xE02020, closeness * 0.55f * pulse);
        if (s.edge() < 15) {
            Component text = Component.translatable("squidgame.game.final.overlay.line");
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * 0.62f, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            g.drawCenteredString(font, text, 0, 0, ((int) (255 * pulse) << 24) | 0xFF3030);
            g.pose().popPose();
        }
    }

    /** The opponent is charging a heavy strike: say so loudly (the telegraph is also on the body). */
    private static void drawThreat(GuiGraphics g, Font font, FightStatePayload s, int w, int h, int mine) {
        if (s.myRole() == 0 || s.stage() != FightStatePayload.FIGHT) {
            return;
        }
        if ((s.flags()[1 - mine] & FightStatePayload.FLAG_CHARGING) != 0) {
            float pulse = 0.6f + 0.4f * (float) Math.sin(System.nanoTime() / 70_000_000.0);
            g.drawCenteredString(font, Component.translatable("squidgame.game.final.overlay.heavy_warning"), w / 2, Math.max(TOP + 30, (int) (h * 0.40f)),
                    ((int) (255 * pulse) << 24) | 0xFFB040);
        }
    }

    // ------------------------------------------------------------------ hit feedback

    private static void drawFeedback(GuiGraphics g, Font font, int w, int h) {
        long now = System.nanoTime();
        // hit marker around the crosshair
        long since = now - FightClientState.hitMarkerAt;
        if (since < 180_000_000L && FightClientState.hitMarkerAt != 0) {
            int a = (int) (255 * (1f - since / 180_000_000f));
            int c = (a << 24) | (FightClientState.hitMarkerKind == FightStatePayload.HIT_HEAVY ? 0xFF8030
                    : FightClientState.hitMarkerKind == FightStatePayload.HIT_BLOCKED ? 0x9AA0A8 : 0xFFFFFF);
            int cx = w / 2, cy = h / 2;
            for (int i = 4; i <= 8; i++) {
                g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, c);
                g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, c);
                g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, c);
                g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
            }
        }
        for (FightClientState.Popup p : FightClientState.popups()) {
            float age = (now - p.bornNanos()) / (float) FightClientState.popupLife();
            float rise = age * 22f;
            int a = (int) (255 * Mth.clamp(1.4f - age * 1.4f, 0f, 1f));
            float scale = p.big() ? 1.5f : 1.1f;
            float x = p.mine() ? w * 0.5f - 46 : w * 0.5f + 46;
            float y = h * 0.5f + (p.mine() ? 22 : -26) - rise;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawCenteredString(font, p.text(), 0, 0, (a << 24) | (p.color() & 0xFFFFFF));
            g.pose().popPose();
        }
    }

    private static void vignette(GuiGraphics g, int w, int h, int rgb, float strength) {
        int depthX = (int) (w * 0.16f), depthY = (int) (h * 0.22f);
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            float f = 1f - i / (float) steps;
            int alpha = (int) (Mth.clamp(strength * f * f, 0f, 1f) * 190f);
            int col = (alpha << 24) | rgb;
            int ix = depthX * i / steps, iy = depthY * i / steps;
            int nx = depthX * (i + 1) / steps, ny = depthY * (i + 1) / steps;
            g.fill(ix, iy, w - ix, ny, col);
            g.fill(ix, h - ny, w - ix, h - iy, col);
            g.fill(ix, ny, nx, h - ny, col);
            g.fill(w - nx, ny, w - ix, h - ny, col);
        }
    }

    // ------------------------------------------------------------------ coin toss and ceremony

    /** The coin flips between the two numbers and lands on the attacker's. */
    private static void drawCoin(GuiGraphics g, Font font, FightStatePayload s, int w, int h) {
        int t = s.coinTick();
        int total = 64;
        float progress = Mth.clamp(t / (float) total, 0f, 1f);
        float eased = 1f - (1f - progress) * (1f - progress);
        double angle = eased * Math.PI * 9.0;
        boolean landed = progress >= 1f || t >= total - 4;
        double flip = landed ? 1.0 : Math.abs(Math.cos(angle));
        boolean faceA = landed || ((int) (angle / Math.PI)) % 2 == 0;
        int cx = w / 2, cy = (int) (h * 0.45f), r = 24;
        float bounce = landed ? 0f : (float) Math.sin(progress * Math.PI) * 26f;
        int top = (int) (cy - bounce);
        g.drawCenteredString(font, Component.translatable("squidgame.game.final.coin.title"), cx, top - r - 22, 0xFFFFD84A);
        int rows = Math.max(2, (int) (r * flip));
        for (int dy = -rows; dy <= rows; dy++) {
            double fy = dy / (double) Math.max(1, rows);
            int half = (int) (r * Math.sqrt(Math.max(0, 1 - fy * fy)));
            int rim = Math.abs(dy) > rows - 2 ? 0xFFB8860B : 0xFFFFD04A;
            g.fill(cx - half, top + dy, cx + half, top + dy + 1, rim);
        }
        if (flip > 0.35) {
            int number = faceA ? s.number()[0] : s.number()[1];
            g.drawCenteredString(font, String.format("%03d", number), cx, top - 4, 0xFF604000);
        }
        if (landed) {
            g.drawCenteredString(font, Component.translatable("squidgame.game.final.coin.attacks", String.format("%03d", s.number()[0])),
                    cx, top + r + 10, ATTACKER_COLOR);
            g.drawCenteredString(font, Component.translatable("squidgame.game.final.coin.defends", String.format("%03d", s.number()[1])),
                    cx, top + r + 22, DEFENDER_COLOR);
        }
    }

    private static void drawCeremony(GuiGraphics g, Font font, FightStatePayload s, int w) {
        g.drawCenteredString(font, Component.translatable("squidgame.game.final.overlay.ceremony",
                String.format("%03d", s.number()[0]), String.format("%03d", s.number()[1])), w / 2, TOP, 0xFFFFD84A);
    }
}
