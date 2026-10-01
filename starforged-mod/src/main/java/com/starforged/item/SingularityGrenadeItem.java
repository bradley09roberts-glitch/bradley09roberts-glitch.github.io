package com.starforged.item;

import com.starforged.entity.projectile.ThrownSingularityGrenade;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
 * A collapsed star in a glass shell. Throw it and a miniature black hole tears open wherever it lands.
 */
public class SingularityGrenadeItem extends Item {
    public SingularityGrenadeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.6F, 0.5F);
        if (level instanceof ServerLevel server) {
            ThrownSingularityGrenade grenade = new ThrownSingularityGrenade(server, player, stack.copyWithCount(1));
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.35F, 0.6F);
            server.addFreshEntity(grenade);
            player.getCooldowns().addCooldown(stack, 30);
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.singularity");
    }
}
