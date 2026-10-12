package com.grandline.client.screen;

import com.grandline.net.GLNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Beli shop. */
public class ShopScreen extends Screen {
    private final CompoundTag data;

    public ShopScreen(CompoundTag data) {
        super(Component.translatable("screen.grandline.shop"));
        this.data = data;
    }

    @Override
    protected void init() {
        ListTag l = data.getList("offers", Tag.TAG_COMPOUND);
        int cols = 2;
        for (int i = 0; i < l.size(); i++) {
            CompoundTag o = l.getCompound(i);
            int x = width / 2 - 200 + (i % cols) * 205, y = 40 + (i / cols) * 24;
            int idx = o.getInt("i");
            addRenderableWidget(Button.builder(Component.literal("฿" + String.format("%,d", o.getLong("price"))), b -> {
                CompoundTag t = new CompoundTag();
                t.putInt("npc", data.getInt("npc"));
                t.putInt("index", idx);
                PacketDistributor.sendToServer(new GLNetwork.UiAction("buy", t));
            }).bounds(x + 130, y, 66, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFD34A);
        g.drawCenteredString(font, Component.literal("฿ " + String.format("%,d", data.getLong("beli"))), width / 2, 24, 0xFFE8D27A);
        ListTag l = data.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag o = l.getCompound(i);
            int x = width / 2 - 200 + (i % 2) * 205, y = 40 + (i / 2) * 24;
            ItemStack s = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(o.getString("item"))), o.getInt("count"));
            g.renderItem(s, x, y + 2);
            g.renderItemDecorations(font, s, x, y + 2);
            Component name = o.contains("painting") ? Component.translatable("painting.grandline." + o.getString("painting") + ".title") : s.getHoverName();
            g.drawString(font, name, x + 20, y + 6, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
