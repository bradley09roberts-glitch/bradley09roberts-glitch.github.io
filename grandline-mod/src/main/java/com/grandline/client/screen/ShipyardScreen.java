package com.grandline.client.screen;

import com.grandline.net.GLNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shipwright: buy a ship, upgrade/repair/rename the nearest owned ship, recover sunk ships. */
public class ShipyardScreen extends Screen {
    private static final int[] SAILS = {0xF4EEDC, 0xF4F4F4, 0x2A2A2A, 0xC9483B, 0x3A7BD5, 0xE8B04A, 0x2E9E6A, 0x8A5AC9};
    private final CompoundTag data;
    private EditBox name;
    private int sailIdx = 0;

    public ShipyardScreen(CompoundTag data) {
        super(Component.translatable("screen.grandline.shipyard"));
        this.data = data;
    }

    private void send(CompoundTag t) {
        t.putInt("npc", data.getInt("npc"));
        PacketDistributor.sendToServer(new GLNetwork.UiAction("ship", t));
    }

    @Override
    protected void init() {
        int x = width / 2 - 200, y = 40;
        ListTag classes = data.getList("classes", Tag.TAG_COMPOUND);
        for (int i = 0; i < classes.size(); i++) {
            CompoundTag c = classes.getCompound(i);
            String id = c.getString("id");
            addRenderableWidget(Button.builder(Component.translatable("screen.grandline.buy_ship", Component.translatable("ship_class.grandline." + id), String.format("%,d", c.getLong("price"))), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "buy");
                t.putString("cls", id);
                send(t);
            }).bounds(x, y + i * 22, 190, 20).build());
        }
        ListTag ups = data.getList("upgrades", Tag.TAG_COMPOUND);
        for (int i = 0; i < ups.size(); i++) {
            CompoundTag u = ups.getCompound(i);
            String id = u.getString("id");
            addRenderableWidget(Button.builder(Component.translatable("screen.grandline.buy_upgrade", Component.translatable("ship_upgrade.grandline." + id), String.format("%,d", u.getLong("price"))), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "upgrade");
                t.putString("upgrade", id);
                send(t);
            }).bounds(x + 205, y + i * 22, 195, 20).build());
        }
        int ry = y + Math.max(classes.size(), ups.size()) * 22 + 10;
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.repair"), b -> {
            CompoundTag t = new CompoundTag();
            t.putString("op", "repair");
            send(t);
        }).bounds(x, ry, 100, 20).build());
        name = new EditBox(font, x + 110, ry, 140, 20, Component.translatable("screen.grandline.ship_name"));
        name.setMaxLength(24);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.sail_colour"), b -> sailIdx = (sailIdx + 1) % SAILS.length).bounds(x + 255, ry, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.grandline.rename"), b -> {
            CompoundTag t = new CompoundTag();
            t.putString("op", "rename");
            t.putString("name", name.getValue());
            t.putInt("sail", SAILS[sailIdx]);
            t.putInt("hullc", 0x8A5A32);
            send(t);
        }).bounds(x + 330, ry, 70, 20).build());
        ListTag owned = data.getList("owned", Tag.TAG_COMPOUND);
        for (int i = 0; i < owned.size(); i++) {
            CompoundTag o = owned.getCompound(i);
            if (!o.getBoolean("sunk")) continue;
            String sid = o.getString("id");
            addRenderableWidget(Button.builder(Component.translatable("screen.grandline.recover", o.getString("name")), b -> {
                CompoundTag t = new CompoundTag();
                t.putString("op", "recover");
                t.putString("ship", sid);
                send(t);
            }).bounds(x, ry + 30 + i * 22, 250, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFD34A);
        g.drawCenteredString(font, Component.literal("฿ " + String.format("%,d", data.getLong("beli"))), width / 2, 24, 0xFFE8D27A);
        int x = width / 2 - 200;
        int ry = 40 + Math.max(data.getList("classes", Tag.TAG_COMPOUND).size(), data.getList("upgrades", Tag.TAG_COMPOUND).size()) * 22 + 10;
        g.fill(x + 255 + 72, ry + 4, x + 255 + 80, ry + 16, 0xFF000000 | SAILS[sailIdx]);
        ListTag owned = data.getList("owned", Tag.TAG_COMPOUND);
        int oy = ry + 30;
        for (int i = 0; i < owned.size(); i++) {
            CompoundTag o = owned.getCompound(i);
            if (o.getBoolean("sunk")) continue;
            g.drawString(font, Component.translatable("screen.grandline.owned_ship", o.getString("name"), Component.translatable("ship_class.grandline." + o.getString("cls")), o.getInt("dist")), x + 260, oy + i * 12, 0xFFCCCCCC);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
