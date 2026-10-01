package com.starforged.event;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * "Total Eclipse" zones created by the Eclipse Blade: a dome of darkness centred on the wielder that slows,
 * weakens and burns every enemy inside.
 */
public final class EclipseFields {
    public static final double RADIUS = 8.0;
    private static final List<Field> FIELDS = new ArrayList<>();

    private static final class Field {
        final UUID owner;
        final ResourceKey<Level> dimension;
        int remaining;
        final int total;

        Field(UUID owner, ResourceKey<Level> dimension, int duration) {
            this.owner = owner;
            this.dimension = dimension;
            this.remaining = duration;
            this.total = duration;
        }
    }

    private EclipseFields() {
    }

    public static void start(ServerLevel level, Player owner, int duration) {
        FIELDS.add(new Field(owner.getUUID(), level.dimension(), duration));
        Vec3 center = owner.position().add(0, 1, 0);
        Fx.sphere(level, ModParticles.ECLIPSE_FLARE.get(), center, 1.0, 40, 0.5);
        Fx.ring(level, ParticleTypes.SONIC_BOOM, center, 3.0, 8, 0.0, 0.0);
        Fx.shake(level, center, 20.0, 0.8F, 20);
    }

    public static void tick(ServerLevel level) {
        if (FIELDS.isEmpty()) {
            return;
        }
        Iterator<Field> it = FIELDS.iterator();
        while (it.hasNext()) {
            Field field = it.next();
            if (!field.dimension.equals(level.dimension())) {
                continue;
            }
            Player owner = level.getPlayerByUUID(field.owner);
            if (owner == null || !owner.isAlive() || --field.remaining <= 0) {
                it.remove();
                continue;
            }
            Vec3 center = owner.position().add(0, 0.2, 0);
            // Dome outline: rotating rings of flares at several heights.
            if (field.remaining % 2 == 0) {
                double spin = (field.total - field.remaining) * 0.08;
                for (int ring = 0; ring < 4; ring++) {
                    double h = ring / 4.0 * RADIUS * 0.9;
                    double r = Math.sqrt(Math.max(0.0, RADIUS * RADIUS - h * h));
                    int count = 10 - ring * 2;
                    for (int i = 0; i < count; i++) {
                        double a = spin * (ring % 2 == 0 ? 1 : -1) + i / (double) count * Math.PI * 2;
                        level.sendParticles(ring == 0 ? ModParticles.ECLIPSE_FLARE.get() : ModParticles.VOID_MOTE.get(), true, true,
                            center.x + Math.cos(a) * r, center.y + h, center.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                }
            }
            if (field.remaining % 20 == 0) {
                for (LivingEntity target : Combat.targetsAround(level, owner, center, RADIUS)) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), owner);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1), owner);
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0), owner);
                    target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.ECLIPSE_BEAM, owner), 4.0F);
                    level.sendParticles(ModParticles.ECLIPSE_FLARE.get(), target.getX(), target.getY(0.5), target.getZ(), 2, 0.2, 0.3, 0.2, 0.0);
                }
            }
        }
    }
}
