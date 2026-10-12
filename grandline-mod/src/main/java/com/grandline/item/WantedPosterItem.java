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
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null && level.isClientSide) com.grandline.client.screen.WantedPosterScreen.open(cd.copyTag()); // client-only class, never reached on a server
        if (cd != null && player instanceof net.minecraft.server.level.ServerPlayer sp) com.grandline.career.CareerPerks.track(sp, cd.copyTag()); // careers: tracking hints
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return;
        var t = cd.copyTag();
        tip.add(Component.literal(t.getString("name")).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("grandline.tooltip.bounty", String.format("%,d", t.getLong("bounty"))).withStyle(ChatFormatting.YELLOW));
        if (t.contains("alive")) tip.add(Component.translatable("grandline.poster.dead_or_alive").withStyle(ChatFormatting.DARK_RED));
        if (t.contains("where")) // job-board posters say where the outlaw was last seen
            tip.add(Component.translatable("grandline.poster.last_seen", t.getString("where"), t.getInt("x"), t.getInt("z")).withStyle(ChatFormatting.GRAY));
    }
}
