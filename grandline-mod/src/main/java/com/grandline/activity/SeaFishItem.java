package com.grandline.activity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A raw sea catch (edible raw, better cooked): shows its quality and the sea it comes from. */
public class SeaFishItem extends Item {
    public SeaFishItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int q = Meals.quality(stack);
        if (q > 0) tip.add(Component.translatable("grandline.quality.catch." + q).withStyle(q == 2 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        tip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
