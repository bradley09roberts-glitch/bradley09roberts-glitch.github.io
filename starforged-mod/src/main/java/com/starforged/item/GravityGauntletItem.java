package com.starforged.item;

import com.starforged.event.GravityGrips;
import com.starforged.util.Targeting;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Gravity Gauntlet: hold right-click on a creature to lift it into the air in front of you, release to hurl it.
 * Thrown creatures smash into whatever they hit.
 */
public class GravityGauntletItem extends Item {
    public static final double RANGE = 24.0;

    public GravityGauntletItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Entity target = Targeting.lookEntity(player, RANGE);
        if (!(target instanceof LivingEntity living) || !GravityGrips.canGrab(player, living)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("item.starforged.gravity_gauntlet.nothing"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            GravityGrips.grab(server, player, living);
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (level instanceof ServerLevel server && entity instanceof Player player) {
            if (!GravityGrips.hold(server, player)) {
                player.stopUsingItem();
            }
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (level instanceof ServerLevel server && entity instanceof Player player) {
            if (GravityGrips.release(server, player, true)) {
                player.getCooldowns().addCooldown(stack, 20);
                stack.hurtAndBreak(1, player, player.getUsedItemHand());
            }
        }
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 20 * 15;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel server && entity instanceof Player player) {
            GravityGrips.release(server, player, false);
        }
        return stack;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.gravity_gauntlet");
    }
}
