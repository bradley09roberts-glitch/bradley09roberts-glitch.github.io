package com.starforged.tempest.item;

import com.starforged.item.LoreItem;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.event.TempestAbilities;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Skybreaker Halberd: a long-reaching aetherium polearm. Right-click for <b>Thunderfall</b>: vault high into the air,
 * and when you come back down you slam into the ground in a ring of lightning.
 */
public class SkybreakerHalberdItem extends Item {
    public SkybreakerHalberdItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.5, 0, 0.5).add(player.getLookAngle().multiply(0.6, 0, 0.6)).add(0, 1.35, 0));
            player.hurtMarked = true;
            TempestAbilities.startSlam(player);
            server.sendParticles(ModParticles.STATIC_SPARK.get(), player.getX(), player.getY() + 0.2, player.getZ(), 30, 0.4, 0.1, 0.4, 0.2);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), TempestSounds.HALBERD_LAUNCH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getCooldowns().addCooldown(stack, 100);
            stack.hurtAndBreak(2, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.thunderfall");
    }
}
