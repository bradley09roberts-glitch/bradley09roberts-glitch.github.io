package com.starforged.sun.item;

import com.starforged.item.LoreItem;
import com.starforged.sun.entity.SunburstFlaskEntity;
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

/** Bottled sunlight. Shatters in a blinding solar flash that burns and dazzles everything nearby. */
public class SunburstFlaskItem extends Item {
    public SunburstFlaskItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F, 0.8F);
        if (level instanceof ServerLevel server) {
            SunburstFlaskEntity flask = new SunburstFlaskEntity(server, player, stack.copyWithCount(1));
            flask.shootFromRotation(player, player.getXRot(), player.getYRot(), -10.0F, 1.2F, 0.6F);
            server.addFreshEntity(flask);
            player.getCooldowns().addCooldown(stack, 20);
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.sunburst_flask");
    }
}
