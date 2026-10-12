package com.grandline.client.screen;

import com.grandline.net.GLNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.network.PacketDistributor;

/** NPC conversation: greeting reacts to who you are; options lead to quests, shops, training and services. */
public class DialogueScreen extends Screen {
    private final CompoundTag data;

    public DialogueScreen(CompoundTag data) {
        super(Component.literal(data.getString("name")));
        this.data = data;
    }

    @Override
    protected void init() {
        ListTag opts = data.getList("options", Tag.TAG_STRING);
        int y = height / 2 + 10;
        for (int i = 0; i < opts.size(); i++) {
            String opt = opts.getString(i);
            Component label;
            Component generated = data.getCompound("labels").contains(opt) ? parse(data.getCompound("labels").getString(opt)) : null;
            if (generated != null) label = generated; // generated quests: title with names and places, sent by the server
            else if (opt.startsWith("quest:")) label = Component.translatable("dialogue.grandline.option.quest", Component.translatable("quest.grandline." + opt.substring(6)));
            else if (opt.startsWith("ferry|") || opt.startsWith("seatrain|")) {
                String[] p = opt.split("\\|");
                label = Component.translatable("dialogue.grandline.option." + p[0], p.length > 3 ? p[3] : p[1], p.length > 2 ? p[2] : "?");
            } else label = Component.translatable("dialogue.grandline.option." + opt);
            addRenderableWidget(Button.builder(label, b -> choose(opt)).bounds(width / 2 - 110, y, 220, 18).build());
            y += 21;
        }
    }

    private void choose(String opt) {
        if (!opt.equals("leave")) {
            CompoundTag t = new CompoundTag();
            t.putInt("npc", data.getInt("npc"));
            t.putString("option", opt);
            PacketDistributor.sendToServer(new GLNetwork.UiAction("dialogue", t));
        }
        if (!opt.equals("trade")) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = width / 2 - 160, y = height / 2 - 90;
        g.fill(x - 6, y - 6, x + 326, y + 86, 0xCC101018);
        g.drawString(font, title.copy().withStyle(net.minecraft.ChatFormatting.GOLD), x, y, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("role.grandline." + data.getString("role")).withStyle(net.minecraft.ChatFormatting.GRAY), x, y + 11, 0xFFAAAAAA);
        int ly = y + 28;
        Component greeting = data.contains("greetingText") ? parse(data.getString("greetingText")) : null;
        if (greeting == null) greeting = Component.translatable(data.getString("greeting"));
        for (FormattedText line : font.getSplitter().splitLines(greeting, 310, Style.EMPTY)) {
            g.drawString(font, line.getString(), x, ly, 0xFFEFEFEF);
            ly += 11;
        }
    }

    /** A component serialized by the server (generated names and places), or null if it cannot be read. */
    @org.jetbrains.annotations.Nullable
    private static Component parse(String json) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || json.isEmpty()) return null;
        try {
            return Component.Serializer.fromJson(json, mc.level.registryAccess());
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
