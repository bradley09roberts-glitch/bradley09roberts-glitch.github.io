package com.terracraft.item;

import com.terracraft.registry.ModDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Base class for plain TerraCraft items: names are coloured by Terraria rarity. */
public class TerraItem extends Item {
    public TerraItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return colored(prefixed(super.getName(stack), stack), stack);
    }

    /** "Legendary Copper Broadsword": the Terraria prefix in front of the name. */
    public static Component prefixed(Component name, ItemStack stack) {
        com.terracraft.item.modifier.Modifier modifier = com.terracraft.item.modifier.Modifiers.of(stack);
        return modifier == null ? name : Component.translatable("modifier.terracraft.name_format", modifier.displayName(), name);
    }

    /** Applies the rarity colour from the stack's stats (shared by every TerraCraft item class). */
    public static Component colored(Component name, ItemStack stack) {
        TerraItemStats stats = stack.get(ModDataComponents.STATS);
        if (stats == null || stats.rarity() == TerraRarity.WHITE) {
            return name;
        }
        return name.copy().withStyle(style -> style.withColor(stats.rarity().color()));
    }
}
