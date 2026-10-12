package com.grandline.client.hud;

import com.grandline.client.ClientState;
import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.player.CharacterData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Stamina + Haki gauges, ability bar in Combat Mode, knockdown timer, lock-on readout. */
public final class HudOverlay {
    private static final String[] SET_NAMES = {"hud.grandline.set.martial", "hud.grandline.set.fruit", "hud.grandline.set.haki", "hud.grandline.set.race"};

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientState.hasData) return;
        CharacterData d = ClientState.LOCAL;
        if (!d.created) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();

        // gauges (bottom-left)
        int x = 8, y = h - 30;
        bar(g, x, y, 90, 5, d.stamina / d.maxStamina(), 0xFF2EC27E, 0xFF0E3A26);
        g.drawString(font, Component.translatable("hud.grandline.stamina"), x, y - 9, 0xFFFFFFFF, true);
        if (d.observationUnlocked || d.armamentUnlocked || d.conquerorAwakened) {
            bar(g, x, y + 10, 90, 5, d.hakiGauge / d.maxHakiGauge(), 0xFF7A3FD0, 0xFF26123F);
        }
        g.drawString(font, Component.translatable("hud.grandline.level", d.level), x + 96, y - 2, 0xFFFFD34A, true);
        if (d.bounty > 0) g.drawString(font, Component.translatable("hud.grandline.bounty", String.format("%,d", d.bounty)), x + 96, y + 8, 0xFFFFAA33, true);
        g.drawString(font, Component.literal("฿ " + String.format("%,d", d.beli)), x, y + 18, 0xFFE8D27A, true);

        if (d.isKnockedDown()) {
            Component c = Component.translatable("hud.grandline.knocked", d.knockdownTicks / 20);
            g.drawCenteredString(font, c, w / 2, h / 2 - 30, 0xFFFF4040);
        }

        if (ClientState.combatMode) {
            int sx = w / 2 - 91, sy = h - 23;
            g.fill(sx - 1, sy - 1, sx + 183, sy + 23, 0xAA000000);
            for (int i = 0; i < 9; i++) {
                int bx = sx + i * 20 + 1;
                g.fill(bx, sy + 1, bx + 18, sy + 19, 0xFF2A2A34);
                String id = d.loadoutSlot(ClientState.abilitySet, i);
                if (id != null) {
                    Ability a = Abilities.get(id);
                    String label = a == null ? "?" : shortName(a);
                    long cd = ClientState.COOLDOWNS.getOrDefault(id, 0L) - ClientState.clientTicks;
                    if (cd > 0 && a != null) {
                        int fillH = (int) (18 * Math.min(1.0, cd / (double) Math.max(1, a.cooldown())));
                        g.fill(bx, sy + 19 - fillH, bx + 18, sy + 19, 0xAA000000);
                    }
                    g.drawCenteredString(font, label, bx + 9, sy + 6, cd > 0 ? 0xFF888888 : 0xFFFFFFFF);
                }
                g.drawString(font, String.valueOf(i + 1), bx + 1, sy + 1, 0xFF9999AA, false);
            }
            g.drawCenteredString(font, Component.translatable(SET_NAMES[ClientState.abilitySet]), w / 2, sy - 11, 0xFFFFD34A);
            // hovered ability name
            if (ClientState.lockTarget >= 0 && mc.level != null) {
                Entity t = mc.level.getEntity(ClientState.lockTarget);
                if (t != null) g.drawCenteredString(font, Component.translatable("hud.grandline.locked", t.getDisplayName()), w / 2, h / 2 + 12, 0xFFFF6060);
            }
            g.drawString(font, Component.translatable("hud.grandline.combat_mode"), w - 90, h - 12, 0xFFFF7050, true);
        }
    }

    private static String shortName(Ability a) {
        String s = Component.translatable(a.translationKey()).getString();
        String[] parts = s.split(" ");
        if (parts.length >= 2) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return s.length() > 2 ? s.substring(0, 2).toUpperCase() : s.toUpperCase();
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int fg, int bg) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + (int) (w * Math.max(0, Math.min(1, frac))), y + h, fg);
    }

    private HudOverlay() {}
}
