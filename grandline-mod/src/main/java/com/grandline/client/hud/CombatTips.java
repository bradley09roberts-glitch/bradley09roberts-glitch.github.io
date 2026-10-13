package com.grandline.client.hud;

import com.grandline.client.ClientState;
import com.grandline.client.Keys;
import com.grandline.net.GLNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The first-use lesson: the first time a character fights (enters Combat Mode or strikes someone), a small card with
 * the five things the combat loop is made of, with the player's own keys, stays up for 15 seconds. It is shown once
 * per character (the server remembers it); /controls shows the full page any time.
 */
public final class CombatTips {
    private static long shownAt = -1;

    private CombatTips() {}

    /** Called when the player starts fighting; shows the card once per character. */
    public static void onFirstFight() {
        if (shownAt >= 0 || !ClientState.hasData || ClientState.LOCAL.flags.contains("seen_controls")) return;
        shownAt = ClientState.clientTicks;
        ClientState.LOCAL.flags.add("seen_controls");
        PacketDistributor.sendToServer(new GLNetwork.UiAction("seen_controls", new CompoundTag()));
    }

    public static void reset() {
        shownAt = -1;
    }

    public static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (shownAt < 0 || mc.options.hideGui || mc.player == null) return;
        long age = ClientState.clientTicks - shownAt;
        if (age > 300) return;
        Font font = mc.font;
        var o = mc.options;
        Component[] lines = {
                Component.translatable("hud.grandline.tips.title"),
                Component.translatable("hud.grandline.tips.strike", o.keyAttack.getTranslatedKeyMessage()),
                Component.translatable("hud.grandline.tips.guard", Keys.COMBAT_MODE.getTranslatedKeyMessage(), o.keyUse.getTranslatedKeyMessage()),
                Component.translatable("hud.grandline.tips.dodge", Keys.DODGE.getTranslatedKeyMessage()),
                Component.translatable("hud.grandline.tips.techniques"),
                Component.translatable("hud.grandline.tips.tells"),
                Component.translatable("hud.grandline.tips.more")
        };
        // a narrow card on the right, under the minimap: clear of the crosshair, the hotbar, the hearts and the chat
        int w = Math.min(220, g.guiWidth() / 2 - 40);
        java.util.List<net.minecraft.util.FormattedCharSequence> rows = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            for (var r : font.split(lines[i], w)) {
                rows.add(r);
                colors.add(i == 0 ? 0xFFE8C060 : i == lines.length - 1 ? 0xFFA09A8C : 0xFFE6E0D2);
            }
        }
        int x = g.guiWidth() - w - 10, y = Math.max(170, g.guiHeight() / 2 - rows.size() * 5);
        int alpha = age > 260 ? (int) (0xC0 * (300 - age) / 40f) : 0xC0;
        g.fill(x - 6, y - 5, x + w + 6, y + rows.size() * 10 + 3, (alpha << 24) | 0x101418);
        for (int i = 0; i < rows.size(); i++) g.drawString(font, rows.get(i), x, y + i * 10, colors.get(i), true);
    }
}
