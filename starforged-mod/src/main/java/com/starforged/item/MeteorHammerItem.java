package com.starforged.item;

import com.starforged.client.ClientHooks;
import com.starforged.event.HammerSlams;
import com.starforged.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor Hammer: leap into the sky and come crashing down like a falling star.
 * Using it in mid-air turns into an instant ground-pound dive.
 */
public class MeteorHammerItem extends Item {
    public static final int COOLDOWN = 70;

    public MeteorHammerItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean grounded = player.onGround() || player.isInWater();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();

        if (level.isClientSide()) {
            // Movement of the local player is client-authoritative, so the launch happens here.
            if (grounded) {
                player.setDeltaMovement(flat.x * 1.15, 1.1, flat.z * 1.15);
            } else {
                player.setDeltaMovement(player.getDeltaMovement().x * 0.3, -1.6, player.getDeltaMovement().z * 0.3);
            }
            ClientHooks.startHammerDive();
        } else if (level instanceof ServerLevel server) {
            HammerSlams.begin(player, !grounded);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HAMMER_LEAP.get(), SoundSource.PLAYERS, 1.0F, grounded ? 1.0F : 1.3F);
            server.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.5, 0.05, 0.5, 0.05);
            player.getCooldowns().addCooldown(stack, COOLDOWN);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Every swing lands like an impact: heavy knockback and a puff of embers.
        Vec3 away = target.position().subtract(attacker.position()).multiply(1, 0, 1);
        if (away.lengthSqr() > 1.0E-4) {
            away = away.normalize();
            com.starforged.util.Combat.knock(target, 1.2, away);
        }
        if (attacker.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.meteor_hammer");
        LoreItem.addAbility(builder, "ability.starforged.meteor_hammer_hit");
    }
}
