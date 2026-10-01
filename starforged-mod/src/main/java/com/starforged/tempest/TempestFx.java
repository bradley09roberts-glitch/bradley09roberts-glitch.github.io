package com.starforged.tempest;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModTags;
import com.starforged.tempest.world.StormNetwork;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Lightning strikes and arcs shared by Tempestforged weapons, creatures and Veyr. */
public final class TempestFx {
    private TempestFx() {
    }

    /**
     * Calls a bolt down at {@code at} that hurts every valid target within {@code radius} of it (it never sets fires or
     * transforms creatures - the bolt itself is only for show). Stormborn creatures shrug it off.
     */
    public static void strike(ServerLevel level, Vec3 at, @Nullable Entity attacker, float damage, double radius) {
        StormNetwork.visualBolt(level, BlockPos.containing(at));
        for (LivingEntity victim : Combat.targetsAround(level, attacker, at.add(0, 1.0, 0), radius)) {
            if (victim.is(ModTags.STORMBORN) && !(attacker instanceof net.minecraft.world.entity.player.Player)) {
                continue;
            }
            victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STORM, attacker), damage);
        }
        Fx.burst(level, ModParticles.STATIC_SPARK.get(), at.add(0, 0.5, 0), 30, 0.6, 0.15);
    }

    /** An arc of lightning between two points that hurts {@code victim}. */
    public static void zap(ServerLevel level, Vec3 from, LivingEntity victim, @Nullable Entity attacker, float damage) {
        StormNetwork.arcTo(level, from, victim.position().add(0, victim.getBbHeight() * 0.5, 0));
        victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STORM, attacker), damage);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), TempestSounds.NETWORK_PULSE.get(), SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    /** A thunderclap: sound plus camera shake. */
    public static void thunder(ServerLevel level, Vec3 at, float volume, float shake) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, volume, 0.9F + level.getRandom().nextFloat() * 0.2F);
        if (shake > 0) {
            Fx.shake(level, at, 24.0, shake, 10);
        }
    }
}
