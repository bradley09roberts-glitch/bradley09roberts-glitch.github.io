package com.starforged.sun.item;

import com.starforged.item.LoreItem;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunSounds;
import com.starforged.sun.entity.HeliosOrbEntity;
import com.starforged.util.Fx;
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

/** Helios Scepter: summons a miniature sun that orbits you for 20 seconds, lancing your enemies with sunbeams. */
public class HeliosScepterItem extends Item {
    public HeliosScepterItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            HeliosOrbEntity.summon(server, player);
            Fx.column(server, ModParticles.SOLAR_SPARK.get(), player.position(), 3.5, 40, 0.8, 0.15);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SunSounds.SCEPTER_SUMMON.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            player.getCooldowns().addCooldown(stack, HeliosOrbEntity.LIFETIME + 100);
            stack.hurtAndBreak(1, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.helios_scepter");
    }
}
