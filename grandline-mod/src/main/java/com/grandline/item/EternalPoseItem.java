package com.grandline.item;

import com.grandline.navigation.Navigation;
import com.grandline.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Eternal Pose: permanently points to one island. */
public class EternalPoseItem extends Item {
    public EternalPoseItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) Navigation.readEternalPose(sp, player.getItemInHand(hand));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        String t = stack.get(ModDataComponents.POSE_TARGET.get());
        if (t != null) tip.add(Component.translatable("grandline.tooltip.points_to", Component.translatable("island.grandline." + t)).withStyle(ChatFormatting.AQUA));
    }
}
