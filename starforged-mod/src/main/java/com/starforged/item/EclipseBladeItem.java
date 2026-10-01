package com.starforged.item;

import com.starforged.entity.projectile.EclipseWaveEntity;
import com.starforged.event.EclipseFields;
import com.starforged.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Eclipse Blade, torn from the Sovereign itself.
 * Right-click: hurl a piercing crescent of eclipse-light.
 * Sneak + right-click: TOTAL ECLIPSE - a dome of darkness that cripples every enemy inside.
 */
public class EclipseBladeItem extends Item {
    public static final int WAVE_COOLDOWN = 24;
    public static final int ECLIPSE_COOLDOWN = 400;

    public EclipseBladeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            if (player.isShiftKeyDown()) {
                EclipseFields.start(server, player, 140);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLADE_ECLIPSE.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
                player.getCooldowns().addCooldown(stack, ECLIPSE_COOLDOWN);
                stack.hurtAndBreak(4, player, hand);
            } else {
                EclipseWaveEntity.fire(server, player, 13.0F);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLADE_WAVE.get(), SoundSource.PLAYERS, 1.2F,
                    0.9F + server.getRandom().nextFloat() * 0.2F);
                player.getCooldowns().addCooldown(stack, WAVE_COOLDOWN);
                stack.hurtAndBreak(1, player, hand);
            }
        }
        player.swing(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), attacker);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.eclipse_wave");
        LoreItem.addAbility(builder, "ability.starforged.total_eclipse");
    }
}
