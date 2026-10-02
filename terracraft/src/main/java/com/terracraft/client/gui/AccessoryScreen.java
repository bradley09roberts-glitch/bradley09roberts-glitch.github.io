package com.terracraft.client.gui;

import com.terracraft.client.ClientState;
import com.terracraft.menu.AccessoryMenu;
import com.terracraft.network.packet.SyncPlayerStatsPacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Terraria equipment page: armor, accessories, inventory and a stat summary. */
public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu> {
    private static final int PANEL = 0xF0203070;
    private static final int BORDER = 0xFF5070C8;
    private static final int SLOT = 0xFF101830;
    private static final int SLOT_EDGE = 0xFF3A4A8A;
    private static final int ACCESSORY_SLOT = 0xFF182848;

    public AccessoryScreen(AccessoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AccessoryMenu.WIDTH, AccessoryMenu.HEIGHT);
        this.inventoryLabelY = 91;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, BORDER);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            boolean accessory = i >= AccessoryMenu.ARMOR_SLOTS && i < AccessoryMenu.ARMOR_SLOTS + menu.accessorySlots();
            graphics.fill(x, y, x + 18, y + 18, SLOT_EDGE);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, accessory ? ACCESSORY_SLOT : SLOT);
        }
        SyncPlayerStatsPacket stats = ClientState.stats();
        int x = leftPos + 80;
        int y = topPos + 18 + ((menu.accessorySlots() + 4) / 5) * 18 + 6;
        graphics.text(font, Component.translatable("screen.terracraft.equipment.defense", stats.defense()), x, y, 0xFFC8C8FF, false);
        graphics.text(font, Component.translatable("screen.terracraft.equipment.life", stats.maxLife()), x, y + 10, 0xFFFF9696, false);
        graphics.text(font, Component.translatable("screen.terracraft.equipment.mana", stats.maxMana()), x, y + 20, 0xFF9696FF, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, Component.translatable("screen.terracraft.equipment.accessories"), 80, 7, 0xFFFFFFFF, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE0E0E0, false);
    }
}
