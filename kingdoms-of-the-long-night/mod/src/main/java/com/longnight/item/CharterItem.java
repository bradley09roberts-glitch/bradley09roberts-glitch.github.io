package com.longnight.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A charter of settlement: carry it when placing a hearthstone to found your own settlement. */
public class CharterItem extends Item {
    public CharterItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Carry this when you set down a hearthstone").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("to found a settlement of your own.").withStyle(ChatFormatting.GRAY));
    }
}
