package com.squidgame.client.game.bridge;

import com.squidgame.client.state.ClientState;
import com.squidgame.core.Phase;
import com.squidgame.core.bridge.BridgeKnowledge.LaneState;
import com.squidgame.game.bridge.BridgeMapPayload;
import com.squidgame.net.HudPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A tiny map of the bridge, one cell per panel (the near end at the bottom), showing only what everybody has seen:
 * grey = nobody knows, green = seen holding, dim red = known to be fragile, black with a red frame = shattered. The
 * lanes are drawn as the contestant sees them (left is left when facing the far platform) and the current row is
 * framed. It is built from the server's public knowledge packet only, so it can never show more than a spectator saw.
 */
final class BridgeOverlay {
    private static final int PANEL = 0xB0101018;
    private static final int PINK = 0xFFE0457B;
    private static final int UNKNOWN = 0xFF4A4F5C;
    private static final int SAFE = 0xFF38D878;
    private static final int WEAK = 0xFF8C3A46;
    private static final int BROKEN_FRAME = 0xFFD03030;
    private static final int CELL_W = 11;
    private static final int CELL_H = 5;
    private static final int GAP = 1;

    private static volatile BridgeMapPayload map;

    private BridgeOverlay() {
    }

    static void update(BridgeMapPayload payload) {
        map = payload.rows() > 0 ? payload : null;
    }

    static void clear() {
        map = null;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        BridgeMapPayload m = map;
        Minecraft mc = Minecraft.getInstance();
        if (m == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        HudPayload hud = ClientState.hud;
        if (hud == null || !"glass_bridge".equals(hud.gameId()) || Phase.byOrdinal(hud.phase()) != Phase.GAME) {
            return;
        }
        Font font = mc.font;
        int rows = m.rows();
        int panelW = 2 * CELL_W + GAP + 14;
        int panelH = 13 + rows * (CELL_H + GAP) + 5;
        int x0 = g.guiWidth() - panelW - 6;
        int y0 = 30;
        g.fill(x0, y0, x0 + panelW, y0 + panelH, PANEL);
        g.renderOutline(x0, y0, panelW, panelH, PINK);
        g.drawCenteredString(font, Component.translatable("squidgame.game.glass_bridge.overlay.title"), x0 + panelW / 2, y0 + 3, 0xFFFFD84A);

        int cx = x0 + 7;
        int top = y0 + 13;
        int myRow = Mth.clamp((int) Math.floor((mc.player.getZ() - m.firstZ()) / (double) Math.max(1, m.pitch())), -1, rows);
        for (int r = 0; r < rows; r++) {
            int y = top + (rows - 1 - r) * (CELL_H + GAP);
            for (int col = 0; col < 2; col++) {
                int lane = col == 0 ? m.leftLane() : 1 - m.leftLane();
                int x = cx + col * (CELL_W + GAP);
                int idx = r * 2 + lane;
                LaneState s = idx < m.states().length ? LaneState.values()[Mth.clamp(m.states()[idx], 0, LaneState.values().length - 1)] : LaneState.UNKNOWN;
                switch (s) {
                    case SAFE -> g.fill(x, y, x + CELL_W, y + CELL_H, SAFE);
                    case WEAK -> g.fill(x, y, x + CELL_W, y + CELL_H, WEAK);
                    case BROKEN -> {
                        g.fill(x, y, x + CELL_W, y + CELL_H, 0xFF0B0B10);
                        g.renderOutline(x, y, CELL_W, CELL_H, BROKEN_FRAME);
                    }
                    default -> g.fill(x, y, x + CELL_W, y + CELL_H, UNKNOWN);
                }
            }
            if (r == myRow) {
                g.renderOutline(cx - 2, y - 1, 2 * CELL_W + GAP + 4, CELL_H + 2, 0xFFFFFFFF);
            }
        }
        if (myRow < 0) {
            g.drawCenteredString(font, "v", x0 + panelW / 2, top + rows * (CELL_H + GAP) - 2, 0xFFFFFFFF);
        }
    }
}
