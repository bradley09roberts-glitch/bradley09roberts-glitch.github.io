package com.terracraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** Block item with Terraria rarity-coloured name. */
public class TerraBlockItem extends BlockItem {
    public TerraBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return TerraItem.colored(super.getName(stack), stack);
    }
}
