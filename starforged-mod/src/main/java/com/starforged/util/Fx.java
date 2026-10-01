package com.starforged.util;

import com.starforged.StarforgedConfig;
import com.starforged.network.ModNetwork;
import com.starforged.network.ScreenShakePacket;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side visual effect helpers: particle shapes and camera shake.
 */
public final class Fx {
    private Fx() {
    }

    /** A horizontal ring of particles. */
    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int count, double outwardSpeed, double upSpeed) {
        for (int i = 0; i < count; i++) {
            double angle = (i / (double) count) * Math.PI * 2.0;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            level.sendParticles(particle, true, true, center.x + cos * radius, center.y, center.z + sin * radius,
                0, cos * outwardSpeed, upSpeed, sin * outwardSpeed, 1.0);
        }
    }

    /** A spherical shell of particles moving outward. */
    public static void sphere(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int count, double outwardSpeed) {
        for (int i = 0; i < count; i++) {
            double u = level.getRandom().nextDouble() * 2.0 - 1.0;
            double theta = level.getRandom().nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(1.0 - u * u);
            double dx = r * Math.cos(theta);
            double dz = r * Math.sin(theta);
            level.sendParticles(particle, true, true, center.x + dx * radius, center.y + u * radius, center.z + dz * radius,
                0, dx * outwardSpeed, u * outwardSpeed, dz * outwardSpeed, 1.0);
        }
    }

    /** A random burst of particles. */
    public static void burst(ServerLevel level, ParticleOptions particle, Vec3 center, int count, double spread, double speed) {
        level.sendParticles(particle, true, true, center.x, center.y, center.z, count, spread, spread, spread, speed);
    }

    /** A straight line of particles between two points. */
    public static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double step) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-4) {
            return;
        }
        Vec3 dir = delta.scale(1.0 / length);
        for (double d = 0; d <= length; d += step) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(particle, true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** A vertical column of particles. */
    public static void column(ServerLevel level, ParticleOptions particle, Vec3 base, double height, int count, double spread, double speed) {
        for (int i = 0; i < count; i++) {
            double y = level.getRandom().nextDouble() * height;
            level.sendParticles(particle, true, true, base.x + (level.getRandom().nextDouble() - 0.5) * spread, base.y + y,
                base.z + (level.getRandom().nextDouble() - 0.5) * spread, 0, 0.0, speed, 0.0, 1.0);
        }
    }

    /**
     * Shakes the camera of every player within {@code radius}, fading with distance.
     */
    public static void shake(ServerLevel level, Vec3 center, double radius, float intensity, int duration) {
        if (!StarforgedConfig.SCREEN_SHAKE.get()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            double dist = player.position().distanceTo(center);
            if (dist < radius) {
                float scaled = (float) (intensity * (1.0 - dist / radius));
                if (scaled > 0.02F) {
                    ModNetwork.sendTo(player, new ScreenShakePacket(scaled, duration));
                }
            }
        }
    }
}
