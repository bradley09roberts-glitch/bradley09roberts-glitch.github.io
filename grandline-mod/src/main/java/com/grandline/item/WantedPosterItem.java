package com.grandline.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/** A wanted poster (name + bounty stored in custom data). Opens the poster screen on the client. */
public class WantedPosterItem extends Item {
    public WantedPosterItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return;
        var t = cd.copyTag();
        tip.add(Component.literal(t.getString("name")).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("grandline.tooltip.bounty", String.format("%,d", t.getLong("bounty"))).withStyle(ChatFormatting.YELLOW));
        if (t.contains("alive")) tip.add(Component.translatable("grandline.poster.dead_or_alive").withStyle(ChatFormatting.DARK_RED));
    }
}
