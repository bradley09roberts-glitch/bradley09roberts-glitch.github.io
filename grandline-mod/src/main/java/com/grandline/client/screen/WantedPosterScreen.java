package com.grandline.client.screen;

import com.grandline.GrandLine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A wanted poster held up to the screen: the painted poster (Higgsfield art, textures/gui/wanted_poster.png) with the
 * wanted character's face in the portrait frame (their skin when they are online), their name and the bounty.
 */
public class WantedPosterScreen extends Screen {
    public static final ResourceLocation TEX = GrandLine.id("textures/gui/wanted_poster.png");
    /** Texture size and the portrait / name / bounty areas measured on the painted poster. */
    public static final int TW = 512, TH = 768;
    public static final int FRAME_X = 70, FRAME_Y = 144, FRAME_W = 372, FRAME_H = 313;
    public static final int NAME_Y = 572, BOUNTY_Y = 636;

    private final CompoundTag poster;

    public WantedPosterScreen(CompoundTag poster) {
        super(Component.translatable("item.grandline.wanted_poster"));
        this.poster = poster;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int h = Math.min(height - 16, 360), w = h * TW / TH;
        int x0 = (width - w) / 2, y0 = (height - h) / 2;
        float s = (float) h / TH;
        g.blit(TEX, x0, y0, w, h, 0, 0, TW, TH, TW, TH);
        // portrait: the wanted player's face if the client knows their skin, otherwise an ink silhouette
        int fs = (int) (Math.min(FRAME_W, FRAME_H) * s * 0.8f);
        int fx = x0 + (int) ((FRAME_X + FRAME_W / 2f) * s) - fs / 2, fy = y0 + (int) ((FRAME_Y + FRAME_H / 2f) * s) - fs / 2;
        PlayerInfo info = null;
        if (poster.hasUUID("uuid") && minecraft != null && minecraft.getConnection() != null) info = minecraft.getConnection().getPlayerInfo(poster.getUUID("uuid"));
        if (info != null) PlayerFaceRenderer.draw(g, info.getSkin(), fx, fy, fs);
        else {
            g.fill(fx + fs / 4, fy + fs / 8, fx + fs * 3 / 4, fy + fs * 5 / 8, 0xC03A2A1A);
            g.fill(fx + fs / 8, fy + fs * 5 / 8, fx + fs * 7 / 8, fy + fs, 0xC03A2A1A);
        }
        // name and bounty in dark ink, scaled to the poster
        String name = poster.getString("name").toUpperCase(java.util.Locale.ROOT);
        String bounty = String.format("฿ %,d -", poster.getLong("bounty"));
        ink(g, name, x0 + w / 2, y0 + (int) (NAME_Y * s), Math.max(1f, h / 260f));
        ink(g, bounty, x0 + w / 2, y0 + (int) (BOUNTY_Y * s), Math.max(1f, h / 220f));
        if (!poster.getString("faction").isEmpty())
            ink(g, Component.translatable("faction.grandline." + poster.getString("faction")).getString(), x0 + w / 2, y0 + (int) ((BOUNTY_Y + 52) * s), Math.max(0.75f, h / 420f));
    }

    private void ink(GuiGraphics g, String text, int cx, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, -4, 0xFF2B1A0C, false);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Called from {@link com.grandline.item.WantedPosterItem} on the client only. */
    public static void open(CompoundTag poster) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new WantedPosterScreen(poster));
    }
}
