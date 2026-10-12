package com.grandline.client.screen;

import com.grandline.client.ClientState;
import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.net.GLNetwork;
import com.grandline.player.CharacterData;
import com.grandline.player.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Character sheet: stats (spend points), styles (choose active), Haki, fruit, bounty, reputation, ability loadout. */
public class CharacterScreen extends Screen {
    public CharacterScreen() {
        super(Component.translatable("screen.grandline.character"));
    }

    private CharacterData d() {
        return ClientState.LOCAL;
    }

    @Override
    protected void init() {
        int x = width / 2 - 190, y = 60;
        for (Stat s : Stat.values()) {
            addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("stat", s.id());
                PacketDistributor.sendToServer(new GLNetwork.UiAction("spend_stat", t));
            }).bounds(x + 120, y - 4, 16, 16).build());
            y += 18;
        }
        int sy = 60;
        for (String style : d().knownStyles) {
            addRenderableWidget(Button.builder(Component.translatable("style.grandline." + style), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("style", style);
                PacketDistributor.sendToServer(new GLNetwork.UiAction("set_style", t));
            }).bounds(width / 2 + 20, sy, 160, 18).build());
            sy += 20;
        }
        addRenderableWidget(Button.builder(Component.translatable(d().pvpFlag ? "screen.grandline.pvp_off" : "screen.grandline.pvp_on"), b -> {
            CompoundTag t = new CompoundTag();
            t.putBoolean("on", !d().pvpFlag);
            PacketDistributor.sendToServer(new GLNetwork.UiAction("pvp_flag", t));
            onClose();
        }).bounds(width / 2 - 190, height - 30, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        CharacterData d = d();
        int x = width / 2 - 190;
        g.drawString(font, Component.literal(d.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, 12, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("screen.grandline.summary", Component.translatable(d.race.translationKey()), Component.translatable(d.faction.translationKey()),
                Component.translatable(d.origin.translationKey()), d.level), x, 24, 0xFFDDDDDD);
        g.drawString(font, Component.translatable("screen.grandline.points", d.statPoints, String.format("%,d", d.bounty), String.format("%,d", d.beli)), x, 36, 0xFFFFD34A);
        int y = 60;
        for (Stat s : Stat.values()) {
            g.drawString(font, Component.translatable(s.translationKey()).append(": " + d.stat(s)), x, y, 0xFFFFFFFF);
            y += 18;
        }
        y += 6;
        g.drawString(font, Component.translatable("screen.grandline.fruit", d.fruit == null ? Component.translatable("screen.grandline.none") : Component.translatable("fruit.grandline." + d.fruit)), x, y, 0xFFE07AE0);
        y += 12;
        g.drawString(font, Component.translatable("screen.grandline.haki", flag(d.observationUnlocked), flag(d.armamentUnlocked), flag(d.conquerorAwakened)), x, y, 0xFFC8A2FF);
        y += 12;
        g.drawString(font, Component.translatable("screen.grandline.mastery_obs_arm", d.mastery("haki/observation"), d.mastery("haki/armament")), x, y, 0xFFAAAAAA);
        y += 16;
        String[] sets = {"hud.grandline.set.martial", "hud.grandline.set.fruit", "hud.grandline.set.haki"};
        for (int s = 0; s < 3; s++) {
            g.drawString(font, Component.translatable(sets[s]), x, y, 0xFFFFD34A);
            y += 10;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                String id = d.loadoutSlot(s, i);
                if (id == null) continue;
                Ability a = Abilities.get(id);
                sb.append(i + 1).append(": ").append(a == null ? id : Component.translatable(a.translationKey()).getString()).append("  ");
            }
            for (var line : font.getSplitter().splitLines(sb.toString(), 360, net.minecraft.network.chat.Style.EMPTY)) {
                g.drawString(font, line.getString(), x, y, 0xFFCCCCCC);
                y += 10;
            }
        }
        g.drawString(font, Component.translatable("screen.grandline.styles", Component.translatable("style.grandline." + d.activeStyle)), width / 2 + 20, 46, 0xFFFFD34A);
        g.drawString(font, Component.translatable("screen.grandline.discovered", d.discoveredIslands.size()), width / 2 + 20, height - 46, 0xFFAAAAAA);
    }

    private Component flag(boolean b) {
        return Component.literal(b ? "✔" : "✘").withStyle(b ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
