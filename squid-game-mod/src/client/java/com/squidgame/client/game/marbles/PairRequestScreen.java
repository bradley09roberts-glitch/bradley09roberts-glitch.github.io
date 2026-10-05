package com.squidgame.client.game.marbles;

import com.squidgame.client.screen.ScreenRegistry;
import com.squidgame.game.marbles.MarblesNet;
import com.squidgame.net.ClientActionPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The modal partnership offer: somebody asks the player to be their partner. Accept or decline; ignoring it (Esc or
 * the timer running out on the server) counts as no. The answer goes to the server, which checks that the offer exists.
 */
@Environment(EnvType.CLIENT)
final class PairRequestScreen extends Screen implements ScreenRegistry.ClosableByServer {
    private static final int PW = 250, PH = 98;
    private final int from;
    private final String name;
    private final int total;
    private final long openedAt = System.nanoTime();
    private boolean answered;

    PairRequestScreen(CompoundTag data) {
        super(Component.translatable("squidgame.game.marbles.ui.pair.title"));
        this.from = data.getInt("from");
        this.name = data.getString("name");
        this.total = Math.max(1, data.getInt("total"));
    }

    @Override
    public String screenId() {
        return MarblesNet.SCREEN_PAIR_REQUEST;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int px() {
        return (width - PW) / 2;
    }

    private int py() {
        return Math.max(8, height / 2 - PH - 6);
    }

    @Override
    protected void init() {
        int px = px(), py = py();
        addRenderableWidget(new FlatButton(px + 14, py + PH - 30, 104, 20, Component.translatable("squidgame.game.marbles.ui.pair.accept"), b -> answer(true)));
        addRenderableWidget(new FlatButton(px + PW - 118, py + PH - 30, 104, 20, Component.translatable("squidgame.game.marbles.ui.pair.decline"), b -> answer(false)));
    }

    private void answer(boolean accept) {
        if (!answered) {
            answered = true;
            CompoundTag t = new CompoundTag();
            t.putInt("from", from);
            t.putBoolean("accept", accept);
            ClientPlayNetworking.send(new ClientActionPayload(MarblesNet.ACTION_PAIR_ANSWER, t));
        }
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        answer(false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // keep the village visible behind the offer
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int px = px(), py = py(), cx = px + PW / 2;
        g.fill(px, py, px + PW, py + PH, 0xEE101018);
        g.renderOutline(px, py, PW, PH, FlatButton.PINK);
        g.drawCenteredString(font, Component.translatable("squidgame.game.marbles.ui.pair.title"), cx, py + 7, 0xFFFFD84A);
        g.drawCenteredString(font, Component.translatable("squidgame.game.marbles.ui.pair.text", String.format("%03d", from), name), cx, py + 22, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("squidgame.game.marbles.ui.pair.hint"), cx, py + 36, 0xFF9A9AA4);
        float frac = Mth.clamp(1f - (System.nanoTime() - openedAt) / (total * 50_000_000f), 0f, 1f);
        g.fill(px + 14, py + 52, px + PW - 14, py + 56, 0x66000000);
        g.fill(px + 14, py + 52, px + 14 + (int) ((PW - 28) * frac), py + 56, frac < 0.25f ? 0xFFFF4040 : 0xFF50D890);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
