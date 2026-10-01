package com.starforged.tempest.item;

import com.starforged.item.LoreItem;
import com.starforged.tempest.entity.CycloneEntity;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Gale Blades: twin blades, very fast. Right-click to whip up a <b>Cyclone</b> that races forward, sweeping enemies up. */
public class GaleBladesItem extends Item {
    public GaleBladesItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            CycloneEntity.sendOut(server, player, player.position().add(player.getLookAngle().multiply(1.5, 0, 1.5)), player.getLookAngle(), 4.0F);
            player.getCooldowns().addCooldown(stack, 70);
            player.swing(hand, true);
            stack.hurtAndBreak(2, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.cyclone");
    }
}
