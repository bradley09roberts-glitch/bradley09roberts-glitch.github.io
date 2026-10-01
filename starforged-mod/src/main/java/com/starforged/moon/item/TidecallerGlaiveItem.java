package com.starforged.moon.item;

import com.starforged.item.LoreItem;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.entity.TideWaveEntity;
import com.starforged.moon.event.MoonAbilities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Tidecaller Glaive, the Pale Matriarch's own weapon. Right-click sends a <b>Silver Tide</b> rolling forward; sneak +
 * right-click <b>reverses gravity</b> around you - enemies float helplessly upward, then slam back down.
 */
public class TidecallerGlaiveItem extends Item {
    public TidecallerGlaiveItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            if (player.isShiftKeyDown()) {
                MoonAbilities.reverseGravity(server, player, 10.0);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.MATRIARCH_INVERSION.get(), SoundSource.PLAYERS, 1.2F, 1.4F);
                player.getCooldowns().addCooldown(stack, 200);
                stack.hurtAndBreak(4, player, hand);
            } else {
                TideWaveEntity.send(server, player, 11.0F);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.TIDE_WAVE.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
                player.getCooldowns().addCooldown(stack, 60);
                stack.hurtAndBreak(2, player, hand);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.silver_tide");
        LoreItem.addAbility(builder, "ability.starforged.reverse_gravity");
    }
}
