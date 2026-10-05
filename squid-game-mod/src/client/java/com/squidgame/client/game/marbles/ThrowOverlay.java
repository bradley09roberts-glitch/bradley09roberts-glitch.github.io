package com.squidgame.client.game.marbles;

import com.squidgame.core.Difficulty;
import com.squidgame.core.marbles.MarblesRules;
import com.squidgame.core.marbles.ThrowModel;
import com.squidgame.game.marbles.ThrowStatePayload;
import com.squidgame.registry.ModItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The throw panel, shown on the player's own turn: the bar fills while the use key is held, a gold notch marks the
 * release point that lands the marble exactly where the crosshair meets the floor, and the green zone around it is what
 * counts as a good release. The notch and the aimed range follow the crosshair every frame, using the same
 * {@link ThrowModel} the server throws with. The turn timer runs along the bottom of the panel.
 */
@Environment(EnvType.CLIENT)
final class ThrowOverlay {
    private static final int BOX_W = 196, BOX_H = 44, BAR_W = 180, BAR_H = 10;
    private static volatile ThrowStatePayload state = ThrowStatePayload.inactive();
    private static volatile long receivedAt;

    private ThrowOverlay() {
    }

    static void accept(ThrowStatePayload payload) {
        state = payload;
        receivedAt = System.nanoTime();
    }

    static void reset() {
        state = ThrowStatePayload.inactive();
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ThrowStatePayload st = state;
        if (!st.active() || mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        Difficulty[] all = Difficulty.values();
        ThrowModel.Params tp = MarblesRules.params(all[Mth.clamp(st.difficulty(), 0, all.length - 1)]).throwModel();
        var player = mc.player;
        ThrowModel.Vec eye = new ThrowModel.Vec(player.getX(), player.getEyeY() - 0.1, player.getZ());
        Vec3 look = player.getLookAngle();
        ThrowModel.Vec aim = ThrowModel.aimFromLook(tp, eye, new ThrowModel.Vec(look.x, look.y, look.z), st.floorY());
        double ideal = ThrowModel.idealCharge(tp, eye, aim);
        double range = eye.horizontalDistance(aim);
        boolean charging = player.isUsingItem() && player.getUseItem().is(ModItems.MARBLE);
        int ticks = charging ? player.getTicksUsingItem() : 0;
        double max = tp.maxChargeTicks();
        double diff = ticks - ideal;
        boolean inZone = charging && Math.abs(diff) <= 1.5;

        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int x0 = (w - BOX_W) / 2, y0 = h - 92;
        g.fill(x0, y0, x0 + BOX_W, y0 + BOX_H, 0xCC101018);
        g.renderOutline(x0, y0, BOX_W, BOX_H, FlatButton.PINK);

        // title, time left, range of the aimed spot
        g.drawString(font, Component.translatable("squidgame.game.marbles.ui.throw.charge"), x0 + 8, y0 + 5, 0xFFFFD84A, true);
        long left = Math.max(0, st.ticksLeft() - (System.nanoTime() - receivedAt) / 50_000_000L);
        float frac = st.ticksTotal() <= 0 ? 0f : Mth.clamp(left / (float) st.ticksTotal(), 0f, 1f);
        Component secs = Component.literal((left + 19) / 20 + " s");
        g.drawString(font, secs, x0 + BOX_W / 2 - font.width(secs) / 2, y0 + 5, frac < 0.2f ? 0xFFFF5555 : 0xFFFFFFFF, true);
        Component rangeText = Component.translatable("squidgame.game.marbles.ui.throw.range", String.format(Locale.ROOT, "%.1f", range));
        g.drawString(font, rangeText, x0 + BOX_W - 8 - font.width(rangeText), y0 + 5, 0xFFCFCFCF, true);

        // the power bar with the sweet spot
        int x = x0 + (BOX_W - BAR_W) / 2, y = y0 + 17;
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF1C1C24);
        // the green zone: a release within 1.5 ticks of the ideal one
        int z0 = x + (int) Mth.clamp(BAR_W * (ideal - 1.5) / max, 0, BAR_W);
        int z1 = x + (int) Mth.clamp(BAR_W * (ideal + 1.5) / max, 0, BAR_W);
        g.fill(z0, y, Math.max(z1, z0 + 2), y + BAR_H, 0xFF2E8B57);
        int color = Math.abs(diff) <= 1.5 ? 0xFF55FF88 : Math.abs(diff) <= 4 ? 0xFFFFC040 : diff < 0 ? 0xFF8FD8FF : 0xFFFF5555;
        int fx = x + (int) (BAR_W * Mth.clamp(ticks / max, 0, 1));
        if (charging) {
            g.fill(x, y + 2, fx, y + BAR_H - 2, color);
        }
        int nx = x + (int) Mth.clamp(BAR_W * ideal / max, 0, BAR_W);
        g.fill(nx - 1, y - 3, nx + 1, y + BAR_H + 3, 0xFFFFD84A);

        // what to do
        g.drawCenteredString(font, Component.translatable(charging ? "squidgame.game.marbles.ui.throw.hint.charging" : "squidgame.game.marbles.ui.throw.hint"),
                x0 + BOX_W / 2, y0 + 31, inZone ? 0xFF55FF88 : 0xFFCFCFCF);

        // the turn timer
        int tx = x0 + 6, tw = BOX_W - 12;
        g.fill(tx, y0 + BOX_H - 5, tx + tw, y0 + BOX_H - 2, 0x66000000);
        g.fill(tx, y0 + BOX_H - 5, tx + (int) (tw * frac), y0 + BOX_H - 2, frac < 0.2f ? 0xFFFF4040 : frac < 0.45f ? 0xFFFFC040 : 0xFF50D890);
    }
}
