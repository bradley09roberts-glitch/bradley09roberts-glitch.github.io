package com.starforged.moon.item;

import com.starforged.item.LoreItem;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.event.MoonAbilities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Stasis Bell: ring it and time stands still around you for four seconds - creatures freeze mid-step, arrows and
 * fireballs hang in the air. When time resumes, everything carries on exactly where it left off.
 */
public class StasisBellItem extends Item {
    public StasisBellItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            MoonAbilities.ringStasis(serverPlayer, 16.0, 80);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.BELL_RING.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
            player.getCooldowns().addCooldown(stack, 600);
            stack.hurtAndBreak(1, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.stasis_bell");
    }
}
