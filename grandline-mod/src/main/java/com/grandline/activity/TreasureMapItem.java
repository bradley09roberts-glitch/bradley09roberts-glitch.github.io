package com.grandline.activity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A treasure map. Blank until it reaches a player's inventory, where {@link TreasureMaps#assign} draws it for one real
 * island secret; using it opens the painted map (island, X, bearing and distance) and marks the X on the sea chart.
 */
public class TreasureMapItem extends Item {
    public TreasureMapItem(Properties p) {
        super(p);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer sp) || sp.tickCount % 10 != 0) return;
        if (stack.get(ActivityRegistry.TREASURE.get()) == null) TreasureMaps.assign(stack, sp, slot);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            TreasureTarget t = TreasureMaps.assign(stack, sp, hand == InteractionHand.MAIN_HAND ? sp.getInventory().selected : 40);
            if (t == null) sp.displayClientMessage(Component.translatable("grandline.treasure.faded").withStyle(ChatFormatting.GRAY), true);
            else TreasureMaps.show(sp, t);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        TreasureTarget t = stack.get(ActivityRegistry.TREASURE.get());
        return t != null && t.grand() && !t.solved();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        TreasureTarget t = stack.get(ActivityRegistry.TREASURE.get());
        if (t == null) {
            tip.add(Component.translatable("item.grandline.treasure_map.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tip.add(Component.translatable("item.grandline.treasure_map.of", t.islandName()).withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("grandline.treasure.kind." + t.kind()).withStyle(ChatFormatting.GRAY));
        if (t.grand()) tip.add(Component.translatable("item.grandline.treasure_map.grand").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (t.solved()) tip.add(Component.translatable("item.grandline.treasure_map.solved").withStyle(ChatFormatting.GREEN));
    }
}
