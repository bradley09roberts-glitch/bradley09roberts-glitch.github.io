package com.squidgame.item;

import com.squidgame.tournament.TournamentManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A glass marble. Outside the marbles game it does nothing. Inside the target-throw variant, holding right click
 * charges the throw and releasing it throws (the game owns the physics and scoring through
 * {@link TournamentManager#onMarbleRelease}).
 */
public class MarbleItem extends Item {
    public MarbleItem(Properties props) {
        super(props);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity, int timeLeft) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            int charged = getUseDuration(stack, entity) - timeLeft;
            TournamentManager.onMarbleRelease(sp, charged);
        }
    }
}
