package com.grandline.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Client visual effects for combat, Haki and Devil Fruit powers, built from particles. */
public final class ClientFx {
    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f), size);
    }

    private static void burst(ClientLevel l, ParticleOptions p, double x, double y, double z, int n, double spread, double speed) {
        RandomSource r = l.random;
        for (int i = 0; i < n; i++) {
            l.addParticle(p, x + (r.nextDouble() - 0.5) * spread, y + (r.nextDouble() - 0.5) * spread, z + (r.nextDouble() - 0.5) * spread,
                    (r.nextDouble() - 0.5) * speed, (r.nextDouble() - 0.5) * speed, (r.nextDouble() - 0.5) * speed);
        }
    }

    private static void ring(ClientLevel l, ParticleOptions p, double x, double y, double z, double radius, int n, double out) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            double cx = Math.cos(a), cz = Math.sin(a);
            l.addParticle(p, x + cx * radius * 0.3, y, z + cz * radius * 0.3, cx * out, 0.02, cz * out);
        }
    }

    private static void sphere(ClientLevel l, ParticleOptions p, double x, double y, double z, double r, int n) {
        RandomSource rnd = l.random;
        for (int i = 0; i < n; i++) {
            double u = rnd.nextDouble() * 2 - 1, th = rnd.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            l.addParticle(p, x + r * s * Math.cos(th), y + r * u, z + r * s * Math.sin(th), 0, 0, 0);
        }
    }

    private static void line(ClientLevel l, ParticleOptions p, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.max(2, d.length() / step);
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            l.addParticle(p, q.x, q.y, q.z, 0, 0, 0);
        }
    }

    public static void play(String kind, double x, double y, double z, float a, float b, float c) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null) return;
        if (kind.startsWith("hit_")) {
            String el = kind.substring(4);
            ParticleOptions p = switch (el) {
                case "fire", "magma" -> ParticleTypes.FLAME;
                case "ice" -> ParticleTypes.SNOWFLAKE;
                case "lightning", "light" -> ParticleTypes.ELECTRIC_SPARK;
                case "water" -> ParticleTypes.SPLASH;
                case "wind" -> ParticleTypes.CLOUD;
                case "darkness" -> ParticleTypes.SQUID_INK;
                default -> ParticleTypes.CRIT;
            };
            burst(l, p, x, y, z, 6 + (int) Math.min(14, a), 0.5, 0.4);
            if (b > 0) {
                burst(l, dust(0x111111, 1.4f), x, y, z, 8, 0.6, 0.2);
                burst(l, ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.4, 0.5);
            }
            if (a > 12) l.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0, 0, 0);
            return;
        }
        if (kind.startsWith("intangible_")) {
            String f = kind.substring(11);
            ParticleOptions p = switch (f) {
                case "mera", "magu" -> ParticleTypes.FLAME;
                case "hie", "yuki" -> ParticleTypes.SNOWFLAKE;
                case "goro", "pika" -> ParticleTypes.ELECTRIC_SPARK;
                case "suna" -> dust(0xD9B56A, 1.2f);
                case "kaze" -> ParticleTypes.CLOUD;
                case "yami" -> ParticleTypes.SQUID_INK;
                default -> ParticleTypes.LARGE_SMOKE;
            };
            burst(l, p, x, y, z, 18, 0.9, 0.15);
            return;
        }
        if (kind.startsWith("impact_")) {
            String k = kind.substring(7);
            switch (k) {
                case "fire_fist", "fire_bullet", "flame_emperor", "fire_bird" -> burst(l, ParticleTypes.FLAME, x, y, z, 20 + (int) (a * 10), a, 0.3);
                case "ice_spear" -> burst(l, ParticleTypes.SNOWFLAKE, x, y, z, 16, 0.6, 0.3);
                case "lightning_bolt" -> burst(l, ParticleTypes.ELECTRIC_SPARK, x, y, z, 18, 0.6, 0.6);
                case "water_shot" -> burst(l, ParticleTypes.SPLASH, x, y, z, 16, 0.5, 0.4);
                case "wind_blade", "tempest", "air_blade", "air_blade_big", "crescent" -> burst(l, ParticleTypes.SWEEP_ATTACK, x, y, z, 2, 0.4, 0);
                default -> burst(l, ParticleTypes.CRIT, x, y, z, 8, 0.4, 0.3);
            }
            return;
        }
        if (kind.startsWith("buff_")) {
            burst(l, ParticleTypes.ENCHANTED_HIT, x, y, z, 14, 1.0, 0.2);
            return;
        }
        switch (kind) {
            case "dodge" -> burst(l, ParticleTypes.CLOUD, x, y, z, 6, 0.6, 0.05);
            case "observation_dodge", "future_sight" -> burst(l, dust(0xC8A2FF, 1.0f), x, y, z, 14, 0.8, 0.05);
            case "parry" -> {
                burst(l, ParticleTypes.ELECTRIC_SPARK, x, y, z, 12, 0.4, 0.6);
                burst(l, ParticleTypes.CRIT, x, y, z, 10, 0.4, 0.6);
            }
            case "block" -> burst(l, ParticleTypes.CRIT, x, y, z, 6, 0.4, 0.3);
            case "guard_break" -> l.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
            case "explosion" -> {
                l.addParticle(a > 4 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
                burst(l, ParticleTypes.LARGE_SMOKE, x, y, z, 12, a, 0.1);
            }
            case "fire_ring" -> ring(l, ParticleTypes.FLAME, x, y, z, a, 80, 0.35);
            case "ice_wave" -> {
                Vec3 dir = new Vec3(a, 0, b).normalize();
                for (int i = 1; i <= (int) c; i++) burst(l, ParticleTypes.SNOWFLAKE, x + dir.x * i, y + 0.3, z + dir.z * i, 6, i * 0.5, 0.05);
            }
            case "ice_encase" -> burst(l, ParticleTypes.ITEM_SNOWBALL, x, y + b / 2, z, 30, Math.max(a, 1), 0.05);
            case "discharge" -> {
                sphere(l, ParticleTypes.ELECTRIC_SPARK, x, y, z, a, 60);
                ring(l, ParticleTypes.ELECTRIC_SPARK, x, y - 0.8, z, a, 40, 0.4);
            }
            case "storm_cloud" -> burst(l, ParticleTypes.LARGE_SMOKE, x, y, z, 80, a * 1.5, 0.02);
            case "rubber_arm" -> line(l, dust(0xE9C8A8, 1.6f), new Vec3(x, y, z), new Vec3(x + a * 12, y + b * 12, z + c * 12), 0.4);
            case "steam" -> burst(l, ParticleTypes.CLOUD, x, y, z, 20, 0.8, 0.08);
            case "giant_fist" -> {
                l.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 0, 0, 0);
                sphere(l, dust(0xE9C8A8, 2.5f), x, y, z, a, 80);
            }
            case "nika" -> {
                ring(l, ParticleTypes.CLOUD, x, y, z, a, 120, 0.6);
                sphere(l, dust(0xFFFFFF, 2.0f), x, y + 1, z, 2, 60);
            }
            case "shambles" -> sphere(l, dust(0x6ACBE8, 1.0f), x, y, z, 0.8, 30);
            case "room" -> sphere(l, dust(0x6ACBE8, 1.6f), x, y, z, a, (int) (a * 40));
            case "room_slash" -> ring(l, ParticleTypes.SWEEP_ATTACK, x, y, z, a, 12, 0.6);
            case "split", "chop_festival" -> burst(l, dust(0xC9483B, 1.2f), x, y, z, 24, Math.max(1, a), 0.3);
            case "updraft" -> {
                for (int i = 0; i < 40; i++) l.addParticle(ParticleTypes.CLOUD, x + (l.random.nextDouble() - 0.5) * a, y, z + (l.random.nextDouble() - 0.5) * a, 0, 0.6, 0);
            }
            case "cyclone", "tornado" -> {
                for (int i = 0; i < 40; i++) {
                    double ang = l.random.nextDouble() * Math.PI * 2, h = l.random.nextDouble() * 6, rr = 0.5 + h * a / 8;
                    l.addParticle(ParticleTypes.CLOUD, x + Math.cos(ang) * rr, y + h, z + Math.sin(ang) * rr, -Math.sin(ang) * 0.3, 0.05, Math.cos(ang) * 0.3);
                }
            }
            case "tailwind" -> burst(l, ParticleTypes.CLOUD, x, y, z, 30, 2, 0.4);
            case "howl" -> ring(l, dust(0xD0D0E0, 1.2f), x, y, z, a, 60, 0.5);
            case "lightning_trail", "fire_trail", "dash_line" -> line(l, kind.startsWith("lightning") ? ParticleTypes.ELECTRIC_SPARK : kind.startsWith("fire") ? ParticleTypes.FLAME : ParticleTypes.CLOUD, new Vec3(x, y, z), new Vec3(a, b, c), 0.5);
            case "shockwave" -> ring(l, ParticleTypes.CLOUD, x, y, z, a, 50, 0.5);
            case "swing_arc" -> l.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0, 0, 0);
            case "water_ripple" -> ring(l, ParticleTypes.SPLASH, x, y, z, a, 40, 0.3);
            case "vines" -> burst(l, ParticleTypes.COMPOSTER, x, y, z, 40, a * 2, 0.1);
            case "heal" -> burst(l, ParticleTypes.HEART, x, y, z, 6, 1.0, 0.1);
            case "sulong" -> sphere(l, dust(0xFFFFFF, 1.8f), x, y, z, 1.2, 60);
            case "conqueror_burst", "conqueror_pulse" -> {
                ring(l, dust(0x1A0F24, 2.5f), x, y, z, a, kind.endsWith("burst") ? 160 : 40, 0.8);
                if (kind.endsWith("burst")) {
                    burst(l, ParticleTypes.ELECTRIC_SPARK, x, y + 1, z, 30, 3, 0.4);
                    shake(10);
                }
            }
            case "black_lightning", "ryuo_burst" -> {
                burst(l, dust(0x111111, 1.6f), x, y, z, 30, Math.max(1.5, a), 0.3);
                burst(l, ParticleTypes.ELECTRIC_SPARK, x, y, z, 16, Math.max(1.5, a), 0.6);
            }
            case "transform" -> burst(l, ParticleTypes.LARGE_SMOKE, x, y, z, 30, 1.6, 0.05);
            case "muzzle" -> {
                l.addParticle(ParticleTypes.SMOKE, x, y, z, a * 0.1, b * 0.1, c * 0.1);
                l.addParticle(ParticleTypes.FLAME, x, y, z, a * 0.05, b * 0.05, c * 0.05);
            }
            case "mirage" -> burst(l, dust(0x9AD8FF, 1.0f), x, y, z, 30, 1.2, 0.05);
            default -> burst(l, ParticleTypes.CRIT, x, y, z, 6, 0.5, 0.2);
        }
    }

    /** Projectile trails (called from AbilityProjectile on the client). */
    public static void trail(Entity p, String kind, float size) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null) return;
        double x = p.getX(), y = p.getY() + size / 2, z = p.getZ();
        switch (kind) {
            case "fire_fist", "fire_bullet", "flame_emperor", "fire_bird" -> burst(l, ParticleTypes.FLAME, x, y, z, 2 + (int) (size * 6), size, 0.02);
            case "ice_spear" -> burst(l, ParticleTypes.SNOWFLAKE, x, y, z, 2, size, 0.01);
            case "lightning_bolt" -> burst(l, ParticleTypes.ELECTRIC_SPARK, x, y, z, 4, size, 0.1);
            case "water_shot" -> burst(l, ParticleTypes.SPLASH, x, y, z, 2, size, 0.02);
            case "wind_blade", "tempest" -> burst(l, ParticleTypes.CLOUD, x, y, z, 2, size, 0.01);
            case "air_blade", "air_blade_big", "crescent" -> burst(l, dust(0xEFFFFF, 1.0f), x, y, z, 3, size, 0.0);
            case "haki_wave" -> burst(l, dust(0x111111, 1.4f), x, y, z, 3, size, 0.0);
            case "cannonball" -> l.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
            case "bullet", "pellet", "knife", "arrow", "spear" -> {}
            default -> burst(l, ParticleTypes.ENCHANTED_HIT, x, y, z, 1, size, 0.0);
        }
    }

    public static int shakeTicks = 0;

    public static void shake(int ticks) {
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    private ClientFx() {}
}
