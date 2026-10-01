package com.starforged.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The key that breaks the last seal. Used on a Celestial Altar to summon the Eclipse Sovereign.
 */
public class EclipseSigilItem extends Item {
    public EclipseSigilItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addLore(this.getDescriptionId(), 3, builder);
    }
}
