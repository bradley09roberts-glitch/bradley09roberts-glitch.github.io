package com.terracraft.player.stats;

import com.terracraft.config.TerraConfig;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.accessory.AccessoryItem;
import com.terracraft.item.armor.ArmorSet;
import com.terracraft.item.armor.TerrariaArmorItem;
import com.terracraft.player.TerraPlayerData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.HashSet;
import java.util.Set;

/** Stat sources for worn armor (with set bonuses and vanilla armor conversion) and accessories. */
public final class EquipmentStatSources {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private EquipmentStatSources() {}

    /** Terraria armor: defense + piece bonuses + full set bonus. Vanilla armor converts to defense. */
    public static void armor(Player player, TerraPlayerData data, PlayerStats stats) {
        ArmorSet commonSet = null;
        int setPieces = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof TerrariaArmorItem armor) {
                stats.add(Stat.DEFENSE, TerraItemStats.of(stack).defense());
                armor.pieceEffects().applyTo(stats);
                if (slot != EquipmentSlot.FEET) {
                    if (setPieces == 0) {
                        commonSet = armor.set();
                        setPieces = 1;
                    } else if (armor.set().equals(commonSet)) {
                        setPieces++;
                    }
                }
            } else {
                stats.add(Stat.DEFENSE, vanillaDefense(stack, slot));
            }
        }
        if (commonSet != null && setPieces == 3) {
            commonSet.bonus().applyTo(stats);
            stats.activeSetBonus = commonSet.id().toString();
        }
    }

    /** Vanilla armor points (from the item's attribute modifiers) scaled into Terraria defense. */
    private static float vanillaDefense(ItemStack stack, EquipmentSlot slot) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double[] armor = {0.0};
        modifiers.forEach(slot, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                armor[0] += modifier.amount();
            }
        });
        return (float) Math.round(armor[0] * TerraConfig.COMMON.vanillaArmorDefenseScale.get());
    }

    /** Accessories in usable slots; duplicates count once. */
    public static void accessories(Player player, TerraPlayerData data, PlayerStats stats) {
        Set<Item> seen = new HashSet<>();
        int usable = data.usableAccessorySlots();
        for (int slot = 0; slot < usable; slot++) {
            ItemStack stack = data.accessories().getItem(slot);
            if (stack.getItem() instanceof AccessoryItem accessory && seen.add(accessory)) {
                accessory.effects().applyTo(stats);
                if (accessory instanceof com.terracraft.item.accessory.WingsItem w
                    && (stats.wings == null || w.flight().flightTicks() > stats.wings.flightTicks())) {
                    stats.wings = w.flight();
                }
                stats.add(Stat.DEFENSE, TerraItemStats.of(stack).defense());
                com.terracraft.item.modifier.Modifier modifier = com.terracraft.item.modifier.Modifiers.of(stack);
                if (modifier != null) {
                    modifier.applyAccessory(stats);
                }
            }
        }
    }
}
