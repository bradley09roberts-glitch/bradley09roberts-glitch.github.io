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
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                                                                     net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof net.minecraft.server.level.ServerLevel sl && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            if (com.longnight.sim.Band.of(com.longnight.sim.WorldSim.get(sl.getServer()), sp.getUUID()) != null) {
                com.longnight.game.Founding.settleBand(sp, sl, sp.blockPosition().below(), true); // uses up the charter itself
                return net.minecraft.world.InteractionResultHolder.consume(stack);
            }
            sp.displayClientMessage(Component.literal("Set down a hearthstone while carrying this to found a settlement."), true);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Carry this when you set down a hearthstone").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("to found a settlement of your own.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Leading a company? Right-click to settle where you stand.").withStyle(ChatFormatting.GOLD));
    }
}
