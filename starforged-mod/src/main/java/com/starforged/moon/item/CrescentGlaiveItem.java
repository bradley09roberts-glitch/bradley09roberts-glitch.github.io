package com.starforged.moon.item;

import com.starforged.item.LoreItem;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.entity.CrescentGlaiveEntity;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Crescent Glaive: a long-reaching moonsilver polearm. Right-click hurls it in a full double orbit around you
 * (<b>Crescent Orbit</b>); sneak + right-click plants it and nails nearby enemies to the ground (<b>Gravity Nail</b>).
 */
public class CrescentGlaiveItem extends Item {
    public CrescentGlaiveItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            if (player.isShiftKeyDown()) {
                Vec3 center = player.position();
                for (LivingEntity target : Combat.targetsAround(server, player, center.add(0, 1, 0), 7.0)) {
                    Vec3 pull = center.subtract(target.position()).multiply(0.15, 0, 0.15);
                    target.setDeltaMovement(pull.x, -1.2, pull.z);
                    target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 5), player);
                    target.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.GRAVITY, player), 7.0F);
                }
                Fx.ring(server, ModParticles.LUNAR_GLIMMER.get(), center.add(0, 0.2, 0), 1.0, 70, 0.6, 0.0);
                Fx.column(server, ModParticles.MOON_DUST.get(), center, 4.0, 40, 0.4, -0.2);
                Fx.shake(server, center, 16.0, 0.6F, 10);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.MATRIARCH_LANCE.get(), SoundSource.PLAYERS, 1.0F, 1.6F);
                player.getCooldowns().addCooldown(stack, 140);
                stack.hurtAndBreak(3, player, hand);
            } else {
                CrescentGlaiveEntity.spin(server, player, 10.0F);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), MoonSounds.GLAIVE_THROW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                player.getCooldowns().addCooldown(stack, CrescentGlaiveEntity.LIFE + 16);
                stack.hurtAndBreak(1, player, hand);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.crescent_orbit");
        LoreItem.addAbility(builder, "ability.starforged.gravity_nail");
    }
}
