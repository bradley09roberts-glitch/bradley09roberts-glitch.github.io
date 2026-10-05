package com.squidgame.client.hud;

import com.squidgame.client.state.ClientState;
import com.squidgame.core.Phase;
import com.squidgame.net.HudPayload;
import com.squidgame.net.ResultsPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The tournament HUD: objective, timer, survivors, own number, per-game widgets, danger vignette, fades, results board. */
public final class SquidHud {
    private static final int PINK = 0xFFE0457B;
    private static final int PANEL = 0xB0101018;
    private static final Map<String, Boolean> ICON_EXISTS = new HashMap<>();

    private SquidHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        HudPayload hud = ClientState.hud;
        // the HUD only exists while the server keeps sending it (player in the tournament dimension)
        boolean fresh = hud != null && (System.nanoTime() - ClientState.hudReceivedAt) < 2_500_000_000L;

        drawDanger(g, w, h);
        if (fresh) {
            drawMain(g, font, hud, w, h, mc);
        }
        drawResults(g, font, w, h);
        float fade = ClientState.fadeAlpha();
        if (fade > 0f) {
            int a = (int) (Mth.clamp(fade, 0f, 1f) * 255f);
            int rgb = ClientState.fadeColor() & 0xFFFFFF;
            g.fill(0, 0, w, h, (a << 24) | rgb);
        }
    }

    // ------------------------------------------------------------------ danger

    private static void drawDanger(GuiGraphics g, int w, int h) {
        float s = ClientState.dangerStrength();
        if (s <= 0.01f) {
            return;
        }
        int rgb = ClientState.dangerColor() & 0xFFFFFF;
        int depthX = (int) (w * 0.22f), depthY = (int) (h * 0.30f);
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float f = 1f - i / (float) steps;
            int alpha = (int) (Mth.clamp(s * f * f, 0f, 1f) * 200f);
            int col = (alpha << 24) | rgb;
            int ix = depthX * i / steps, iy = depthY * i / steps;
            int nx = depthX * (i + 1) / steps, ny = depthY * (i + 1) / steps;
            g.fill(ix, iy, w - ix, ny, col);               // top
            g.fill(ix, h - ny, w - ix, h - iy, col);      // bottom
            g.fill(ix, ny, nx, h - ny, col);               // left
            g.fill(w - nx, ny, w - ix, h - ny, col);      // right
        }
    }

    // ------------------------------------------------------------------ main hud

    private static void drawMain(GuiGraphics g, Font font, HudPayload hud, int w, int h, Minecraft mc) {
        Phase phase = Phase.byOrdinal(hud.phase());
        boolean lobby = phase == Phase.LOBBY;
        int cx = w / 2;
        int boxW = Math.min(w - 20, 260);
        int y = 6;

        // title + objective panel
        Component title = hud.title();
        Component objective = hud.objective();
        int lines = 1 + (objective.getString().isEmpty() ? 0 : 1);
        int extraLines = 0;
        for (HudPayload.Widget wd : hud.widgets()) {
            if (wd.kind() == HudPayload.LINE) {
                extraLines++;
            }
        }
        int boxH = 8 + lines * 11 + extraLines * 10 + (hud.timerTotal() > 0 && !lobby ? 14 : 0);
        panel(g, cx - boxW / 2, y, boxW, boxH);
        g.drawCenteredString(font, title, cx, y + 5, 0xFFFFD84A);
        int ty = y + 16;
        if (!objective.getString().isEmpty()) {
            g.drawCenteredString(font, objective, cx, ty, 0xFFFFFFFF);
            ty += 11;
        }
        for (HudPayload.Widget wd : hud.widgets()) {
            if (wd.kind() == HudPayload.LINE) {
                g.drawCenteredString(font, wd.label(), cx, ty, 0xFFCFCFCF);
                ty += 10;
            }
        }
        if (hud.timerTotal() > 0 && !lobby) {
            int seconds = Math.max(0, hud.timerTicks() / 20);
            String t = String.format("%02d:%02d", seconds / 60, seconds % 60);
            float frac = Mth.clamp(hud.timerTicks() / (float) hud.timerTotal(), 0f, 1f);
            int barW = boxW - 24;
            int by = y + boxH - 9;
            g.fill(cx - barW / 2, by, cx + barW / 2, by + 4, 0x66000000);
            int col = frac < 0.2f ? 0xFFFF4040 : frac < 0.45f ? 0xFFFFC040 : 0xFF50D890;
            g.fill(cx - barW / 2, by, cx - barW / 2 + (int) (barW * frac), by + 4, col);
            g.drawString(font, t, cx + barW / 2 - font.width(t), by - 10, 0xFFFFFFFF, true);
        }

        // survivors, top right
        if (!lobby && hud.total() > 0) {
            String s = hud.alive() + " / " + hud.total();
            int sw = font.width(s) + 22;
            panel(g, w - sw - 6, 6, sw, 18);
            icon(g, "icon_survivors", w - sw - 2, 8, 14);
            g.drawString(font, s, w - sw + 14, 11, 0xFFFFFFFF, true);
        }

        // own number plate, bottom left
        if (hud.myNumber() > 0) {
            int px = 8, py = h - 34;
            g.fill(px, py, px + 52, py + 26, 0xFFF4F4F4);
            g.renderOutline(px, py, 52, 26, 0xFF202020);
            String num = String.format("%03d", hud.myNumber());
            g.pose().pushPose();
            g.pose().translate(px + 26, py + 5, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, num, 0, 0, 0xFF111111);
            g.pose().popPose();
            Component st = switch (hud.myStatus()) {
                case HudPayload.STATUS_ELIMINATED -> Component.translatable("squidgame.hud.status.eliminated");
                case HudPayload.STATUS_WINNER -> Component.translatable("squidgame.hud.status.winner");
                case HudPayload.STATUS_SPECTATOR -> Component.translatable("squidgame.hud.status.spectator");
                default -> Component.translatable("squidgame.hud.status.alive");
            };
            int sc = hud.myStatus() == HudPayload.STATUS_ELIMINATED ? 0xFFFF5555 : 0xFF55FF88;
            g.drawString(font, st, px + 58, py + 9, sc, true);
        } else if (hud.myStatus() == HudPayload.STATUS_SPECTATOR) {
            g.drawString(font, Component.translatable("squidgame.hud.status.spectator"), 8, h - 20, 0xFFFFAA55, true);
        }

        drawWidgets(g, font, hud.widgets(), w, h, cx, cx - boxW / 2, y + boxH);
    }

    /**
     * @param panelLeft   left edge of the objective panel; counters that would run into it start below it instead
     * @param panelBottom bottom edge of the objective panel; the traffic light hangs from it
     */
    private static void drawWidgets(GuiGraphics g, Font font, List<HudPayload.Widget> widgets, int w, int h, int cx,
                                    int panelLeft, int panelBottom) {
        int barY = h - 62;
        int counterY = 40;
        for (HudPayload.Widget wd : widgets) {
            switch (wd.kind()) {
                case HudPayload.LIGHT -> drawLight(g, font, wd, cx, panelBottom + 17);
                case HudPayload.BANNER -> {
                    float pulse = 0.75f + 0.25f * (float) Math.sin(System.nanoTime() / 120_000_000.0);
                    int a = (int) (255 * pulse);
                    int col = (a << 24) | (wd.color() & 0xFFFFFF);
                    g.pose().pushPose();
                    g.pose().translate(cx, h * 0.46f, 0);
                    g.pose().scale(2.4f, 2.4f, 1f);
                    g.drawCenteredString(font, wd.label(), 0, 0, col);
                    g.pose().popPose();
                }
                case HudPayload.BAR -> {
                    int bw = 140;
                    int x = cx - bw / 2;
                    g.fill(x - 1, barY - 1, x + bw + 1, barY + 7, 0xAA000000);
                    float f = wd.max() <= 0 ? 0 : Mth.clamp(wd.value() / wd.max(), 0f, 1f);
                    g.fill(x, barY, x + (int) (bw * f), barY + 6, wd.color() | 0xFF000000);
                    g.drawString(font, wd.label(), x, barY - 10, 0xFFFFFFFF, true);
                    barY -= 18;
                }
                case HudPayload.COUNTER -> {
                    String txt = wd.max() < 0 ? Integer.toString((int) wd.value()) : (int) wd.value() + " / " + (int) wd.max();
                    int tw = font.width(txt) + font.width(wd.label()) + 28;
                    if (6 + tw > panelLeft - 4 && counterY < panelBottom + 6) {
                        counterY = panelBottom + 6; // narrow screens / large GUI scale: do not draw over the objective panel
                    }
                    panel(g, 6, counterY, tw, 16);
                    icon(g, wd.icon(), 9, counterY + 1, 14);
                    g.drawString(font, wd.label(), 26, counterY + 4, 0xFFCFCFCF, true);
                    g.drawString(font, txt, 30 + font.width(wd.label()), counterY + 4, 0xFFFFFFFF, true);
                    counterY += 19;
                }
                default -> {
                }
            }
        }
    }

    private static void drawLight(GuiGraphics g, Font font, HudPayload.Widget wd, int cx, int y) {
        int state = (int) wd.value();
        int col = state == 0 ? 0xFF3CDC6E : state == 1 ? 0xFFFFC030 : 0xFFFF3030;
        int r = 11;
        int x = cx;
        g.fill(x - r - 3, y - r - 3, x + r + 3, y + r + 3, 0xCC000000);
        g.fill(x - r, y - r, x + r, y + r, col);
        g.renderOutline(x - r, y - r, 2 * r, 2 * r, 0xFFFFFFFF);
        g.drawCenteredString(font, wd.label(), x, y + r + 5, col);
    }

    // ------------------------------------------------------------------ results board

    private static void drawResults(GuiGraphics g, Font font, int w, int h) {
        ResultsPayload r = ClientState.results;
        if (r == null) {
            return;
        }
        if (System.nanoTime() > ClientState.resultsEndsAt) {
            ClientState.results = null;
            return;
        }
        int pw = Math.min(w - 40, 360);
        int rows = Math.max(1, (r.eliminated().size() + 11) / 12);
        int ph = 84 + rows * 11 + (r.survivors().size() > 0 ? 24 : 0);
        int x = (w - pw) / 2;
        int y = Math.max(40, h / 2 - ph / 2 - 20);
        g.fill(x, y, x + pw, y + ph, 0xD0101018);
        g.renderOutline(x, y, pw, ph, PINK);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + 8, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawCenteredString(font, r.headline(), 0, 0, 0xFFFFD84A);
        g.pose().popPose();
        g.drawCenteredString(font, r.subline(), w / 2, y + 28, 0xFFFFFFFF);
        int yy = y + 44;
        String sv = Component.translatable("squidgame.results.survivors", r.survivors().size()).getString();
        g.drawString(font, sv, x + 10, yy, 0xFF55FF88, true);
        yy += 12;
        if (!r.survivors().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (int n : r.survivors()) {
                if (shown++ >= 24) {
                    sb.append("...");
                    break;
                }
                sb.append(String.format("%03d ", n));
            }
            g.drawString(font, sb.toString(), x + 10, yy, 0xFFD0FFD8, false);
            yy += 12;
        }
        String el = Component.translatable("squidgame.results.eliminated", r.eliminated().size()).getString();
        g.drawString(font, el, x + 10, yy, 0xFFFF6666, true);
        yy += 12;
        int col = 0;
        StringBuilder row = new StringBuilder();
        for (int n : r.eliminated()) {
            row.append(String.format("%03d ", n));
            if (++col == 12) {
                g.drawString(font, row.toString(), x + 10, yy, 0xFFFFB0B0, false);
                yy += 11;
                row.setLength(0);
                col = 0;
            }
        }
        if (row.length() > 0) {
            g.drawString(font, row.toString(), x + 10, yy, 0xFFFFB0B0, false);
        }
        Component outcome = switch (r.myOutcome()) {
            case ResultsPayload.OUTCOME_SURVIVED -> Component.translatable("squidgame.results.you_survived", String.format("%03d", r.myNumber()));
            case ResultsPayload.OUTCOME_ELIMINATED -> Component.translatable("squidgame.results.you_eliminated", String.format("%03d", r.myNumber()));
            case ResultsPayload.OUTCOME_WINNER -> Component.translatable("squidgame.results.you_won", String.format("%,d", r.prizeWon()));
            default -> null;
        };
        if (outcome != null) {
            int oc = r.myOutcome() == ResultsPayload.OUTCOME_ELIMINATED ? 0xFFFF5555 : 0xFF55FF88;
            g.drawCenteredString(font, outcome, w / 2, y + ph - 16, oc);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.renderOutline(x, y, w, h, PINK);
    }

    private static void icon(GuiGraphics g, String name, int x, int y, int size) {
        if (name == null || name.isEmpty()) {
            return;
        }
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("squidgame", "textures/gui/hud/" + name + ".png");
        boolean exists = ICON_EXISTS.computeIfAbsent(name, k -> Minecraft.getInstance().getResourceManager().getResource(rl).isPresent());
        if (exists) {
            g.blit(rl, x, y, size, size, 0f, 0f, 16, 16, 16, 16);
        }
    }
}
