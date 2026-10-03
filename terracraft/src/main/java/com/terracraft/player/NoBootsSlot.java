package com.terracraft.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/**
 * Terraria armor is three pieces (helmet, chest, greaves; the greaves include the boots), so the player has no
 * boots slot: the inventory's feet slot is replaced by a hidden, disabled one, and anything that still ends up on
 * the feet (right-click equipping, commands) is moved back into the inventory.
 */
public final class NoBootsSlot {
    private static final int FEET_MENU_SLOT = InventoryMenu.ARMOR_SLOT_START + 3;

    private NoBootsSlot() {}

    public static void register() {
        EntityJoinLevelEvent.BUS.addListener(NoBootsSlot::onJoin);
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) {
            hide(player.inventoryMenu);
        }
    }

    private static void hide(InventoryMenu menu) {
        Slot feet = menu.slots.get(FEET_MENU_SLOT);
        if (feet instanceof Hidden) {
            return;
        }
        Slot hidden = new Hidden(feet.container, feet.getContainerSlot(), feet.x, feet.y);
        hidden.index = FEET_MENU_SLOT;
        menu.slots.set(FEET_MENU_SLOT, hidden);
    }

    public static void tick(ServerPlayer player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty()) {
            return;
        }
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        if (!player.getInventory().add(boots)) {
            player.drop(boots, false);
        }
    }

    private static final class Hidden extends Slot {
        Hidden(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
