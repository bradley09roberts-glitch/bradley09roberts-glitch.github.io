package com.starforged.item;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;

/**
 * Void Scythe: every hit drinks life from the victim. Right-click to REAP - a 360 degree void sweep that
 * drags every nearby enemy into the blade.
 */
public class VoidScytheItem extends Item {
    public static final int COOLDOWN = 80;
    private static final double REAP_RADIUS = 6.0;

    public VoidScytheItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        attacker.heal(2.0F);
        if (attacker.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.VOID_MOTE.get(), target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Vec3 center = player.position().add(0, 1.0, 0);
            List<LivingEntity> victims = Combat.targetsAround(server, player, center, REAP_RADIUS);
            for (LivingEntity victim : victims) {
                victim.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.VOID_REND, player), 9.0F);
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1), player);
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), player);
                Vec3 pull = player.position().subtract(victim.position()).multiply(1, 0, 1);
                if (pull.lengthSqr() > 1.0) {
                    victim.setDeltaMovement(victim.getDeltaMovement().add(pull.normalize().scale(0.9)).add(0, 0.25, 0));
                    victim.hurtMarked = true;
                }
                player.heal(1.5F);
            }
            // Swirling crescent of void energy.
            for (int arm = 0; arm < 3; arm++) {
                for (int i = 0; i < 28; i++) {
                    double t = i / 28.0;
                    double angle = arm * (Math.PI * 2 / 3) + t * Math.PI * 2;
                    double r = REAP_RADIUS * (0.35 + 0.65 * t);
                    server.sendParticles(ModParticles.VOID_MOTE.get(), true, true, center.x + Math.cos(angle) * r, center.y - 0.3 + t * 0.6,
                        center.z + Math.sin(angle) * r, 0, -Math.sin(angle) * 0.2, 0.02, Math.cos(angle) * 0.2, 1.0);
                }
            }
            Fx.ring(server, ParticleTypes.SWEEP_ATTACK, center, 2.2, 10, 0.0, 0.0);
            Fx.ring(server, ParticleTypes.REVERSE_PORTAL, center, REAP_RADIUS, 60, -0.4, 0.05);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SCYTHE_REAP.get(), SoundSource.PLAYERS, 1.3F, 1.0F);
            if (!victims.isEmpty()) {
                Fx.shake(server, center, 12.0, 0.5F, 8);
            }
            player.getCooldowns().addCooldown(stack, COOLDOWN);
            stack.hurtAndBreak(2, player, hand);
        }
        player.swing(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.void_scythe_leech");
        LoreItem.addAbility(builder, "ability.starforged.void_scythe_reap");
    }
}
