package com.starforged.event;

import com.starforged.item.StarforgedArmorItem;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side logic for the special armor:
 * <ul>
 *     <li>Starmetal set: Night Vision at night and a retaliatory Starburst when struck.</li>
 *     <li>Comet Boots: double jump (input handled client-side) and immunity to fall damage.</li>
 *     <li>Nebula Cloak: elytra flight with a starwind boost (client-side thrust).</li>
 *     <li>Eclipse Crown: permanent Night Vision and reveals every hostile creature nearby.</li>
 * </ul>
 */
public final class ArmorAbilities {
    private static final Map<UUID, Long> LAST_STARBURST = new HashMap<>();
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorAbilities() {
    }

    public static boolean has(LivingEntity entity, EquipmentSlot slot, StarforgedArmorItem.Ability ability) {
        ItemStack stack = entity.getItemBySlot(slot);
        return stack.getItem() instanceof StarforgedArmorItem armor && armor.ability() == ability;
    }

    public static boolean hasStarmetalSet(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR) {
            if (!has(entity, slot, StarforgedArmorItem.Ability.STARMETAL_SET)) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasCometBoots(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.FEET).is(ModItems.COMET_BOOTS.get());
    }

    public static boolean hasEclipseCrown(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.ECLIPSE_CROWN.get());
    }

    /** Called every server tick for each player. */
    public static void tick(ServerPlayer player) {
        ServerLevel level = player.level();
        boolean crown = hasEclipseCrown(player);
        if ((crown || (hasStarmetalSet(player) && level.isDarkOutside())) && player.tickCount % 20 == 0) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() < 240) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, true));
            }
        }
        if (crown && player.tickCount % 40 == 0) {
            AABB area = player.getBoundingBox().inflate(28.0);
            for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, area, e -> e instanceof Enemy && e.isAlive())) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false), player);
            }
        }
    }

    /** Called when a living entity is hurt; handles the Starmetal Starburst retaliation. */
    public static void onHurt(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer player) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        if (!hasStarmetalSet(player) || player.getRandom().nextFloat() > 0.3F) {
            return;
        }
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Long last = LAST_STARBURST.get(player.getUUID());
        if (last != null && now - last < 100) {
            return;
        }
        LAST_STARBURST.put(player.getUUID(), now);
        Vec3 center = player.position().add(0, 1.0, 0);
        for (LivingEntity target : Combat.targetsAround(level, player, center, 4.5)) {
            target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STARLIGHT, player), 5.0F);
            Combat.blast(target, center, 0.9, 0.35);
        }
        Fx.sphere(level, ModParticles.STAR_SPARKLE.get(), center, 0.4, 45, 0.45);
        Fx.ring(level, ParticleTypes.END_ROD, center, 0.6, 24, 0.35, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.STARBURST.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    /** Fall damage multiplier for armor effects (0 = immune). */
    public static float fallMultiplier(LivingEntity entity) {
        if (hasCometBoots(entity)) {
            return 0.0F;
        }
        if (entity instanceof Player player && HammerSlams.isFallImmune(player)) {
            return 0.0F;
        }
        return hasStarmetalSet(entity) ? 0.5F : 1.0F;
    }

    /** The client reported a Comet Boots mid-air jump: validate, then show it to everyone. */
    public static void onDoubleJump(ServerPlayer player) {
        if (!hasCometBoots(player) || player.onGround() || player.isPassenger()) {
            return;
        }
        player.resetFallDistance();
        ServerLevel level = player.level();
        Vec3 feet = player.position();
        Fx.ring(level, ModParticles.ASTRAL_GLINT.get(), feet, 0.4, 16, 0.25, -0.05);
        Fx.burst(level, ModParticles.STAR_SPARKLE.get(), feet, 12, 0.3, 0.05);
        Fx.burst(level, ParticleTypes.CLOUD, feet, 6, 0.2, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COMET_JUMP.get(), SoundSource.PLAYERS, 0.8F,
            0.9F + player.getRandom().nextFloat() * 0.2F);
    }

    public static void clear(Player player) {
        LAST_STARBURST.remove(player.getUUID());
    }
}
