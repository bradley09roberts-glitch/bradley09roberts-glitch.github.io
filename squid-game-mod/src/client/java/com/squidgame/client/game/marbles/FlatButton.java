package com.squidgame.client.game.marbles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A flat dark button with a pink outline, matching the tournament HUD panels; can be shown as selected. */
@Environment(EnvType.CLIENT)
final class FlatButton extends Button {
    static final int PINK = 0xFFE0457B;
    boolean selected;
    int accent = PINK;

    FlatButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hover = active && isHoveredOrFocused();
        int bg = !active ? 0xFF24242C : selected ? accent : hover ? 0xFF3C3C4C : 0xFF20202A;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
        g.renderOutline(getX(), getY(), getWidth(), getHeight(), selected ? 0xFFFFFFFF : active ? accent : 0xFF50505A);
        int color = !active ? 0xFF8A8A94 : 0xFFFFFFFF;
        g.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2,
                getY() + (getHeight() - 8) / 2, color);
    }
}
