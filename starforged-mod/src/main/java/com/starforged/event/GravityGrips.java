package com.starforged.event;

import com.starforged.entity.boss.EclipseSovereignEntity;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side state for the Gravity Gauntlet: who is holding whom, and who was just thrown.
 */
public final class GravityGrips {
    private static final Map<UUID, Integer> HELD = new HashMap<>();
    private static final Map<Integer, Thrown> THROWN = new HashMap<>();

    private record Thrown(UUID thrower, long until) {
    }

    private GravityGrips() {
    }

    public static boolean canGrab(Player player, LivingEntity target) {
        if (target instanceof EclipseSovereignEntity || target == player || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        // The heavyweights resist being lifted.
        return target.getBbWidth() * target.getBbHeight() < 7.0F;
    }

    public static void grab(ServerLevel level, Player player, LivingEntity target) {
        HELD.put(player.getUUID(), target.getId());
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.GAUNTLET_GRAB.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, target.position().add(0, target.getBbHeight() / 2, 0), 30, 0.4, 0.3);
    }

    /** Called every tick while the gauntlet is in use. Returns false once the grip is lost. */
    public static boolean hold(ServerLevel level, Player player) {
        Integer id = HELD.get(player.getUUID());
        if (id == null) {
            return false;
        }
        Entity entity = level.getEntity(id);
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > 32.0) {
            HELD.remove(player.getUUID());
            return false;
        }
        Vec3 anchor = player.getEyePosition().add(player.getViewVector(1.0F).scale(3.5 + target.getBbWidth()))
            .subtract(0, target.getBbHeight() * 0.5, 0);
        Vec3 pull = anchor.subtract(target.position());
        target.setDeltaMovement(pull.scale(0.35));
        target.resetFallDistance();
        target.hurtMarked = true;
        if (level.getGameTime() % 2 == 0) {
            Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.8)).add(0, -0.4, 0);
            Fx.line(level, ModParticles.ASTRAL_GLINT.get(), hand, target.position().add(0, target.getBbHeight() / 2, 0), 0.8);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(0.5), target.getZ(), 3, 0.3, 0.3, 0.3, 0.1);
        }
        return true;
    }

    /** Releases the held creature, optionally hurling it along the player's look vector. */
    public static boolean release(ServerLevel level, Player player, boolean hurl) {
        Integer id = HELD.remove(player.getUUID());
        if (id == null) {
            return false;
        }
        Entity entity = level.getEntity(id);
        if (!(entity instanceof LivingEntity target)) {
            return false;
        }
        if (hurl) {
            Vec3 throwDir = player.getViewVector(1.0F);
            target.setDeltaMovement(throwDir.scale(2.6).add(0, 0.25, 0));
            target.hurtMarked = true;
            THROWN.put(target.getId(), new Thrown(player.getUUID(), level.getGameTime() + 60));
            level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.GAUNTLET_THROW.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
            Fx.burst(level, ParticleTypes.GUST, target.position(), 1, 0.0, 0.0);
        }
        return true;
    }

    /** Checks thrown creatures for impacts. Called once per server tick per level. */
    public static void tickThrown(ServerLevel level) {
        if (THROWN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Thrown>> it = THROWN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Thrown> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) {
                continue;
            }
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || level.getGameTime() > entry.getValue().until) {
                it.remove();
                continue;
            }
            Player thrower = level.getPlayerByUUID(entry.getValue().thrower);
            boolean hitMob = false;
            for (LivingEntity other : Combat.targetsAround(level, thrower, target.position().add(0, target.getBbHeight() / 2, 0), 1.2 + target.getBbWidth() / 2)) {
                if (other != target) {
                    other.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE, target, thrower), 8.0F);
                    Combat.blast(other, target.position(), 0.9, 0.4);
                    hitMob = true;
                }
            }
            if (hitMob || target.horizontalCollision || (target.verticalCollision && level.getGameTime() > entry.getValue().until - 55)) {
                float speed = (float) target.getDeltaMovement().length();
                target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE, thrower, thrower), 6.0F + speed * 4.0F);
                Fx.burst(level, ParticleTypes.EXPLOSION, target.position(), 2, 0.3, 0.0);
                Fx.burst(level, ModParticles.STAR_SPARKLE.get(), target.position(), 25, 0.6, 0.15);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.HAMMER_SLAM.get(), SoundSource.PLAYERS, 0.8F, 1.5F);
                it.remove();
            }
        }
    }

    public static void clear(Player player) {
        HELD.remove(player.getUUID());
    }
}
