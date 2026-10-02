package com.terracraft.menu;

import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * Terraria's equipment page: armor, accessory slots and the player inventory.
 * Slot layout: 0-3 armor, then {@link #accessorySlots} accessory slots, then 36 inventory slots.
 */
public class AccessoryMenu extends AbstractContainerMenu {
    public static final int ARMOR_SLOTS = 4;
    /** Screen size matching the slot layout. */
    public static final int WIDTH = 176;
    public static final int HEIGHT = 184;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Identifier[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
        InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    private final int accessorySlots;
    private final Container accessories;
    private final Player player;

    /** Client side: number of usable accessory slots (set by the client from synced stats). */
    public static IntSupplier clientSlotCount = () -> 5;

    /** Client constructor (from the open-screen packet). */
    public AccessoryMenu(int id, Inventory inventory, @Nullable FriendlyByteBuf data) {
        this(id, inventory, new SimpleContainer(TerraPlayerData.MAX_ACCESSORY_SLOTS),
            data == null ? clientSlotCount.getAsInt() : data.readVarInt());
    }

    /** Server constructor. */
    public AccessoryMenu(int id, Inventory inventory, TerraPlayerData data) {
        this(id, inventory, data.accessories(), data.usableAccessorySlots());
    }

    private AccessoryMenu(int id, Inventory inventory, Container accessories, int accessorySlots) {
        super(ModMenus.ACCESSORIES.get(), id);
        this.accessories = accessories;
        this.accessorySlots = Math.min(accessorySlots, TerraPlayerData.MAX_ACCESSORY_SLOTS);
        this.player = inventory.player;
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            addSlot(new ArmorSlot(inventory, player, ARMOR[i], 39 - i, 8, 8 + i * 18, ARMOR_ICONS[i]));
        }
        for (int i = 0; i < this.accessorySlots; i++) {
            addSlot(new AccessorySlot(i, 80 + (i % 5) * 18, 18 + (i / 5) * 18));
        }
        addStandardInventorySlots(inventory, 8, 102);
    }

    public int accessorySlots() {
        return accessorySlots;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int equipmentEnd = ARMOR_SLOTS + accessorySlots;
        int inventoryStart = equipmentEnd;
        int hotbarStart = inventoryStart + 27;
        int end = hotbarStart + 9;
        if (index < equipmentEnd) {
            if (!moveItemStackTo(stack, inventoryStart, end, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof AccessoryItem) {
            if (!moveItemStackTo(stack, ARMOR_SLOTS, equipmentEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (armorIndex(stack) >= 0 && !slots.get(armorIndex(stack)).hasItem()) {
            if (!moveItemStackTo(stack, armorIndex(stack), armorIndex(stack) + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < hotbarStart) {
            if (!moveItemStackTo(stack, hotbarStart, end, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, inventoryStart, hotbarStart, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private static int armorIndex(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) {
            return -1;
        }
        for (int i = 0; i < ARMOR.length; i++) {
            if (ARMOR[i] == equippable.slot()) {
                return i;
            }
        }
        return -1;
    }

    /** Accepts one accessory, never a duplicate of another equipped accessory. */
    private final class AccessorySlot extends Slot {
        AccessorySlot(int index, int x, int y) {
            super(accessories, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!(stack.getItem() instanceof AccessoryItem)) {
                return false;
            }
            for (int i = 0; i < accessorySlots; i++) {
                if (i != getContainerSlot() && accessories.getItem(i).is(stack.getItem())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
