package com.grandline.journey;

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

/**
 * A Den Den Mushi. Everything happens on the server ({@link DenDenMushi}); using the snail opens its chat menu:
 * the personal snail lists who you can call (or answers / hangs up), the crew snail and the baby snail prompt for a
 * message, the black snail listens in on the Marines.
 */
public final class DenDenMushiItem extends Item {
    public enum Kind { PERSONAL, CREW, BABY, BLACK }

    public final Kind kind;

    public DenDenMushiItem(Kind kind, Properties p) {
        super(p);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) DenDenMushi.use(sp, kind);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.grandline." + switch (kind) {
            case PERSONAL -> "den_den_mushi";
            case CREW -> "crew_den_den_mushi";
            case BABY -> "baby_den_den_mushi";
            case BLACK -> "black_den_den_mushi";
        } + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
