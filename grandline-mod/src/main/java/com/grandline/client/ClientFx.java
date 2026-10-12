package com.grandline.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
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

    // ---- shapes used by the second wave of fruits ----

    /** Upper half of a sphere (domes, cages, bells). */
    private static void dome(ClientLevel l, ParticleOptions p, double x, double y, double z, double r, int n) {
        RandomSource rnd = l.random;
        for (int i = 0; i < n; i++) {
            double u = rnd.nextDouble(), th = rnd.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            l.addParticle(p, x + r * s * Math.cos(th), y + r * u, z + r * s * Math.sin(th), 0, 0, 0);
        }
    }

    /** Filled disc on the ground, particles drifting towards (inward > 0) or away from the centre. */
    private static void disc(ClientLevel l, ParticleOptions p, double x, double y, double z, double r, int n, double inward, double up) {
        RandomSource rnd = l.random;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            double cx = Math.cos(a), cz = Math.sin(a);
            l.addParticle(p, x + cx * d, y + 0.1, z + cz * d, -cx * inward, up, -cz * inward);
        }
    }

    /** Rotating spiral (whirlpools, sandstorms, vortices): particles carry tangential velocity. */
    private static void spiral(ClientLevel l, ParticleOptions p, double x, double y, double z, double r, double height, int n, double spin, double inward) {
        RandomSource rnd = l.random;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, h = rnd.nextDouble() * height, d = 0.3 + rnd.nextDouble() * r;
            double cx = Math.cos(a), cz = Math.sin(a);
            l.addParticle(p, x + cx * d, y + h, z + cz * d, -cz * spin - cx * inward, 0.02, cx * spin - cz * inward);
        }
    }

    /** Vertical column around an entity-sized box (binds, statues, sprouted arms). */
    private static void column(ClientLevel l, ParticleOptions p, double x, double y, double z, double w, double h, int n) {
        RandomSource rnd = l.random;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.max(0.3, w * 0.6 + 0.2);
            l.addParticle(p, x + Math.cos(a) * rr, y + rnd.nextDouble() * Math.max(0.5, h), z + Math.sin(a) * rr, 0, 0.01, 0);
        }
    }

    /** Vertical plane perpendicular to a horizontal direction (walls, barriers, waves). */
    private static void plane(ClientLevel l, ParticleOptions p, double x, double y, double z, double dx, double dz, double half, double height, int n) {
        RandomSource rnd = l.random;
        double len = Math.max(1e-4, Math.sqrt(dx * dx + dz * dz));
        double sx = -dz / len, sz = dx / len;
        for (int i = 0; i < n; i++) {
            double w = (rnd.nextDouble() * 2 - 1) * half, h = (rnd.nextDouble() - 0.5) * height;
            l.addParticle(p, x + sx * w, y + h, z + sz * w, 0, 0, 0);
        }
    }

    /** Jagged white cracks radiating from a point (Gura). */
    private static void cracks(ClientLevel l, double x, double y, double z, double r, int lines) {
        RandomSource rnd = l.random;
        for (int i = 0; i < lines; i++) {
            Vec3 a = new Vec3(x, y, z);
            Vec3 dir = new Vec3(rnd.nextDouble() - 0.5, (rnd.nextDouble() - 0.5) * 0.8, rnd.nextDouble() - 0.5).normalize();
            int segs = 3 + rnd.nextInt(3);
            for (int sg = 0; sg < segs; sg++) {
                Vec3 b = a.add(dir.scale(r / segs)).add((rnd.nextDouble() - 0.5) * 0.6, (rnd.nextDouble() - 0.5) * 0.6, (rnd.nextDouble() - 0.5) * 0.6);
                line(l, dust(0xFFFFFF, 1.1f), a, b, 0.3);
                a = b;
            }
        }
    }

    private static int rainbow(RandomSource r) {
        int[] c = {0xFF4A4A, 0xFFA04A, 0xFFF04A, 0x6AFF6A, 0x4AC8FF, 0x8A6AFF, 0xF06AFF};
        return c[r.nextInt(c.length)];
    }

    public static void play(String kind, double x, double y, double z, float a, float b, float c) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null) return;
        if (kind.equals("weather")) {
            ClientWeather.update((int) a, b, c);
            return;
        }
        if (canonWave3(l, kind, x, y, z, a, b, c)) return; // fruit wave 3 (canon), see block below
        if (kind.startsWith("hit_")) {
            String el = kind.substring(4);
            ParticleOptions p = switch (el) {
                case "fire", "magma" -> ParticleTypes.FLAME;
                case "ice" -> ParticleTypes.SNOWFLAKE;
                case "lightning", "light" -> ParticleTypes.ELECTRIC_SPARK;
                case "water" -> ParticleTypes.SPLASH;
                case "wind" -> ParticleTypes.CLOUD;
                case "darkness" -> ParticleTypes.SQUID_INK;
                case "sand" -> dust(0xD9B56A, 1.2f);
                case "smoke" -> ParticleTypes.CLOUD;
                case "sound" -> ParticleTypes.NOTE;
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
                case "light_bead", "mirror_beam" -> {
                    burst(l, ParticleTypes.END_ROD, x, y, z, 14, 0.6, 0.25);
                    l.addParticle(ParticleTypes.FLASH, x, y, z, 0, 0, 0);
                }
                case "magma_fist", "meteor" -> {
                    burst(l, ParticleTypes.LAVA, x, y, z, 10 + (int) (a * 6), a, 0.2);
                    burst(l, ParticleTypes.FLAME, x, y, z, 16, a, 0.25);
                    burst(l, ParticleTypes.LARGE_SMOKE, x, y, z, 8, a, 0.05);
                }
                case "sand_blade" -> burst(l, dust(0xD9B56A, 1.4f), x, y, z, 20, 0.8, 0.2);
                case "smoke_fist", "white_snake" -> burst(l, ParticleTypes.CLOUD, x, y, z, 18, 0.8, 0.12);
                case "dark_debris" -> burst(l, dust(0x1A0F24, 1.4f), x, y, z, 10, 0.5, 0.2);
                case "mirror_shard" -> burst(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), x, y, z, 14, 0.4, 0.2);
                case "phoenix_flame" -> burst(l, ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 18, 0.8, 0.2);
                case "salt_shard" -> burst(l, dust(0xF4F4F4, 1.0f), x, y, z, 10, 0.4, 0.2);
                case "spiral_drill" -> spiral(l, ParticleTypes.CLOUD, x, y - 0.5, z, 1.2, 1.2, 20, 0.3, 0.05);
                case "water_drill" -> spiral(l, ParticleTypes.SPLASH, x, y - 0.5, z, 1.2, 1.2, 24, 0.3, 0.05);
                case "tempest_kick" -> burst(l, ParticleTypes.SWEEP_ATTACK, x, y, z, 2, 0.4, 0);
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
            // ---------------- second-wave fruits ----------------
            // Pika
            case "light_beam", "light_trail" -> {
                line(l, ParticleTypes.END_ROD, new Vec3(x, y, z), new Vec3(a, b, c), 0.6);
                line(l, dust(0xFFF59A, 1.3f), new Vec3(x, y, z), new Vec3(a, b, c), 0.35);
            }
            case "light_burst" -> {
                l.addParticle(ParticleTypes.FLASH, x, y, z, 0, 0, 0);
                sphere(l, ParticleTypes.END_ROD, x, y, z, Math.max(0.6, a), (int) (20 + a * 12));
            }
            case "light_pillar" -> {
                for (int i = 0; i < 30; i++) l.addParticle(ParticleTypes.END_ROD, x + (l.random.nextDouble() - 0.5) * 0.8, y + l.random.nextDouble() * b, z + (l.random.nextDouble() - 0.5) * 0.8, 0, -0.4, 0);
                ring(l, dust(0xFFF59A, 1.6f), x, y + 0.2, z, a, 36, 0.35);
                l.addParticle(ParticleTypes.FLASH, x, y + 0.5, z, 0, 0, 0);
            }
            case "light_sword" -> {
                ring(l, ParticleTypes.END_ROD, x, y, z, a, 40, 0.4);
                for (int i = 0; i < 5; i++) l.addParticle(ParticleTypes.SWEEP_ATTACK, x + (l.random.nextDouble() - 0.5) * a, y, z + (l.random.nextDouble() - 0.5) * a, 0, 0, 0);
            }
            // Magu
            case "magma_burst" -> {
                burst(l, ParticleTypes.LAVA, x, y, z, 14, a, 0.2);
                burst(l, ParticleTypes.FLAME, x, y, z, 18, a, 0.2);
            }
            case "magma_floor" -> {
                disc(l, ParticleTypes.LAVA, x, y, z, a, (int) (a * 6), 0, 0.1);
                disc(l, ParticleTypes.SMOKE, x, y, z, a, (int) (a * 6), 0, 0.05);
            }
            case "meteor_sky" -> {
                burst(l, ParticleTypes.LARGE_SMOKE, x, y, z, 60, a * 1.5, 0.02);
                burst(l, ParticleTypes.FLAME, x, y, z, 40, a, 0.05);
            }
            case "eruption" -> {
                for (int i = 0; i < 40; i++) {
                    double ang = l.random.nextDouble() * Math.PI * 2, d = l.random.nextDouble() * a;
                    l.addParticle(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, x + Math.cos(ang) * d, y + 0.2, z + Math.sin(ang) * d, 0, 0.3 + l.random.nextDouble() * 0.4, 0);
                }
                burst(l, ParticleTypes.LARGE_SMOKE, x, y + 1, z, 20, a, 0.05);
                if (a >= 6) shake(10);
            }
            // Suna
            case "sandstorm" -> spiral(l, dust(0xD9B56A, 1.6f), x, y, z, a, 6, (int) (40 + a * 10), 0.35, 0.05);
            case "quicksand" -> {
                disc(l, dust(0xC9A55A, 1.5f), x, y, z, a, (int) (a * 10), 0.15, -0.02);
                spiral(l, dust(0xD9B56A, 1.2f), x, y, z, a, 1, 30, 0.25, 0.1);
            }
            case "sand_wave" -> plane(l, dust(0xD9B56A, 1.5f), x, y + 0.6, z, a, b, Math.max(1, c), 1.2, 24);
            case "sand_fissure" -> {
                for (int i = 0; i < 16; i++) l.addParticle(dust(0xC9A55A, 1.6f), x + (l.random.nextDouble() - 0.5), y + 0.2, z + (l.random.nextDouble() - 0.5), 0, 0.3 + l.random.nextDouble() * 0.3, 0);
                l.addParticle(ParticleTypes.SWEEP_ATTACK, x, y + 0.8, z, 0, 0, 0);
            }
            case "sand_drain" -> {
                line(l, dust(0x8AC8FF, 0.9f), new Vec3(x, y, z), new Vec3(a, b, c), 0.4);
                burst(l, dust(0xD9B56A, 1.4f), x, y, z, 24, 0.9, 0.08);
            }
            // Moku
            case "smoke_bind" -> {
                double rr = Math.max(0.4, a * 0.7), hh = Math.max(1.5, b);
                for (int i = 0; i < 40; i++) {
                    double t = i / 40.0, ang = t * Math.PI * 6;
                    l.addParticle(ParticleTypes.CLOUD, x + Math.cos(ang) * rr, y - 1 + t * hh, z + Math.sin(ang) * rr, 0, 0, 0);
                }
            }
            case "smoke_screen" -> {
                burst(l, ParticleTypes.LARGE_SMOKE, x, y + 1, z, (int) (30 + a * 6), a * 1.6, 0.03);
                burst(l, dust(0xD0D0D0, 2.5f), x, y + 1, z, (int) (20 + a * 4), a * 1.6, 0.02);
            }
            case "smoke_trail" -> line(l, ParticleTypes.CLOUD, new Vec3(x, y, z), new Vec3(a, b, c), 0.5);
            case "smoke_cage" -> {
                for (int h = 0; h < 8; h += 2) ring(l, ParticleTypes.LARGE_SMOKE, x, y + h, z, a / 0.3, 24, 0);
                spiral(l, ParticleTypes.CLOUD, x, y, z, a, 8, 30, 0.2, 0.05);
            }
            // Yami
            case "black_hole" -> {
                disc(l, dust(0x1A0F24, 2.0f), x, y, z, a, (int) (a * 10), 0.25, 0);
                disc(l, ParticleTypes.SQUID_INK, x, y, z, a * 0.5, 10, 0.1, 0.02);
            }
            case "black_world" -> {
                ring(l, dust(0x1A0F24, 2.5f), x, y + 0.5, z, a, 120, 0.7);
                disc(l, ParticleTypes.SQUID_INK, x, y, z, a, 40, 0.3, 0.02);
                shake(4);
            }
            case "dark_pull" -> line(l, dust(0x5A2A7A, 1.4f), new Vec3(x, y, z), new Vec3(a, b, c), 0.35);
            case "dark_grasp" -> {
                column(l, dust(0x1A0F24, 1.8f), x, y - 1, z, a, b, 40);
                column(l, ParticleTypes.SQUID_INK, x, y - 1, z, a, b, 10);
            }
            case "darkness_burst" -> {
                sphere(l, dust(0x1A0F24, 2.0f), x, y, z, a, 90);
                burst(l, ParticleTypes.SQUID_INK, x, y, z, 30, a, 0.3);
            }
            // Gura
            case "quake_cone" -> {
                Vec3 dir = new Vec3(a, b, c).normalize();
                for (int i = 1; i <= 8; i++) cracks(l, x + dir.x * i, y + dir.y * i, z + dir.z * i, 0.6 + i * 0.15, 1);
                ring(l, ParticleTypes.CLOUD, x + dir.x * 2, y + dir.y * 2, z + dir.z * 2, 3, 20, 0.4);
                shake(6);
            }
            case "air_crack" -> {
                cracks(l, x, y, z, a, 10);
                ring(l, ParticleTypes.CLOUD, x, y, z, a, 50, 0.5);
                shake(8);
            }
            case "tsunami" -> {
                plane(l, c > 0 ? ParticleTypes.SPLASH : dust(0x8A7A6A, 1.6f), x, y + 1.5, z, a, b, 3, 3, 40);
                if (c > 0) plane(l, ParticleTypes.BUBBLE_POP, x, y + 1, z, a, b, 3, 2, 20);
                else cracks(l, x, y + 0.2, z, 2, 2);
            }
            case "island_tilt" -> {
                ring(l, ParticleTypes.CLOUD, x, y + 0.5, z, a, 160, 0.9);
                cracks(l, x, y + 0.3, z, a * 0.5, 16);
                shake(25);
            }
            // Ito
            case "parasite", "sky_walk" -> {
                double top = kind.equals("sky_walk") ? 10 : 4;
                for (int i = 0; i < 4; i++) {
                    double ox = (l.random.nextDouble() - 0.5) * 0.8, oz = (l.random.nextDouble() - 0.5) * 0.8;
                    line(l, dust(0xF4F4F4, 0.6f), new Vec3(x + ox, y, z + oz), new Vec3(x + ox, y + top, z + oz), 0.3);
                }
            }
            case "string_lines" -> {
                double len = Math.sqrt(a * a + b * b);
                double dx = len < 1e-4 ? 0 : a / len, dz = len < 1e-4 ? 1 : b / len;
                for (int s5 = -2; s5 <= 2; s5++) {
                    Vec3 from = new Vec3(x - dz * s5 * 0.8, y + 1.5, z + dx * s5 * 0.8);
                    line(l, dust(0xF4F4F4, 0.8f), from, from.add(dx * c, -2.5, dz * c), 0.3);
                }
            }
            case "overheat" -> {
                line(l, dust(0xFF5A2A, 1.4f), new Vec3(x, y, z), new Vec3(a, b, c), 0.3);
                line(l, ParticleTypes.FLAME, new Vec3(x, y, z), new Vec3(a, b, c), 0.8);
            }
            case "birdcage" -> {
                for (int m = 0; m < 16; m++) {
                    double ang = m * Math.PI / 8;
                    for (int i = 0; i <= 12; i++) {
                        double t = i / 12.0 * Math.PI / 2;
                        l.addParticle(dust(0xF4F4F4, 0.9f), x + Math.cos(ang) * a * Math.cos(t), y + a * Math.sin(t), z + Math.sin(ang) * a * Math.cos(t), 0, 0, 0);
                    }
                }
            }
            // Hana
            case "petals" -> burst(l, ParticleTypes.CHERRY_LEAVES, x, y, z, (int) (12 + a * 6), Math.max(0.6, a), 0.05);
            case "flower_eyes" -> {
                burst(l, ParticleTypes.CHERRY_LEAVES, x, y, z, 6, 0.6, 0.05);
                burst(l, dust(0xF29AC9, 0.8f), x, y, z, 6, 0.3, 0);
            }
            case "sprout_arms" -> {
                column(l, dust(c == 2 ? 0xF29AC9 : 0xE9C8A8, 1.3f), x, y, z, a, b, 30);
                column(l, ParticleTypes.CHERRY_LEAVES, x, y, z, a, b, 8);
            }
            case "giant_hand" -> {
                for (int i = 0; i < 50; i++) l.addParticle(dust(0xE9C8A8, 2.5f), x + (l.random.nextDouble() - 0.5) * a, y + l.random.nextDouble() * a * 1.5, z + (l.random.nextDouble() - 0.5) * a, 0, 0.05, 0);
                burst(l, ParticleTypes.CHERRY_LEAVES, x, y + a, z, 30, a, 0.05);
            }
            // Bari
            case "barrier" -> plane(l, dust(0x9AE0FF, 1.4f), x, y, z, a, b, Math.max(1, c), 3.5, 70);
            case "salt_wall" -> plane(l, dust(0xF4F4F4, 1.6f), x, y, z, a, b, Math.max(1, c), 3.5, 70);
            case "barrier_ball", "mirror_shield" -> {
                sphere(l, dust(kind.startsWith("barrier") ? 0x9AE0FF : 0xC0E8FF, 1.1f), x, y, z, Math.max(1, a), 70);
                sphere(l, ParticleTypes.END_ROD, x, y, z, Math.max(1, a), 6);
            }
            case "barrier_crash" -> {
                line(l, dust(0x9AE0FF, 1.6f), new Vec3(x, y, z), new Vec3(a, b, c), 0.3);
                l.addParticle(ParticleTypes.SWEEP_ATTACK, a, b, c, 0, 0, 0);
            }
            case "barrier_shatter", "mirror_step" -> {
                burst(l, new BlockParticleOption(ParticleTypes.BLOCK, kind.startsWith("barrier") ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.GLASS.defaultBlockState()), x, y, z, 30, Math.max(1, a), 0.25);
                burst(l, ParticleTypes.END_ROD, x, y, z, 8, 1, 0.1);
            }
            // Tori (Phoenix)
            case "blue_flame" -> {
                sphere(l, ParticleTypes.SOUL_FIRE_FLAME, x, y, z, Math.max(1, a), (int) (30 + a * 10));
                if (b > 0) ring(l, ParticleTypes.HEART, x, y, z, a, 10, 0.1);
            }
            case "blue_flame_trail" -> line(l, ParticleTypes.SOUL_FIRE_FLAME, new Vec3(x, y, z), new Vec3(a, b, c), 0.4);
            case "phoenix_wings" -> {
                for (int i = 0; i < 40; i++) {
                    double side = l.random.nextDouble() * a, ang = l.random.nextDouble() * Math.PI * 2;
                    l.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(ang) * side, y + 0.3 - side * 0.2, z + Math.sin(ang) * side, 0, -0.02, 0);
                }
            }
            case "regen_flame" -> burst(l, ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 6, 0.8, 0.02);
            // Zoan beasts
            case "charge_dust" -> {
                for (int i = 0; i < 20; i++) l.addParticle(ParticleTypes.POOF, x - a * i * 0.3 + (l.random.nextDouble() - 0.5), y + 0.2, z - b * i * 0.3 + (l.random.nextDouble() - 0.5), 0, 0.05, 0);
            }
            case "claw" -> {
                for (int i = 0; i < 3; i++) l.addParticle(ParticleTypes.SWEEP_ATTACK, x + (i - 1) * 0.3, y + (i - 1) * 0.2, z, 0, 0, 0);
                burst(l, ParticleTypes.CRIT, x, y, z, 6, 0.5, 0.3);
            }
            // Shio
            case "salt" -> burst(l, dust(0xF4F4F4, 1.2f), x, y, z, (int) (12 + a * 8), Math.max(0.5, a), 0.06);
            case "salt_flats" -> disc(l, dust(0xF4F4F4, 1.4f), x, y, z, a, (int) (a * 10), -0.05, 0.03);
            case "salt_statue" -> {
                column(l, dust(0xF4F4F4, 1.6f), x, y, z, a, b, 50);
                column(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), x, y, z, a, b, 16);
            }
            // Kagami
            case "mirror_flash" -> {
                l.addParticle(ParticleTypes.FLASH, x, y, z, 0, 0, 0);
                burst(l, ParticleTypes.END_ROD, x, y, z, 40, 2.5, 0.3);
            }
            case "mirror_reflect" -> {
                burst(l, ParticleTypes.END_ROD, x, y, z, 8, 0.4, 0.15);
                burst(l, dust(0xC0E8FF, 1.0f), x, y, z, 8, 0.6, 0.05);
            }
            case "prism_beam" -> {
                Vec3 from = new Vec3(x, y, z), d = new Vec3(a, b, c).subtract(from);
                int n = (int) Math.max(2, d.length() / 0.4);
                DustParticleOptions col = dust(rainbow(l.random), 1.1f);
                for (int i = 0; i <= n; i++) {
                    Vec3 q = from.add(d.scale(i / (double) n));
                    l.addParticle(col, q.x, q.y, q.z, 0, 0, 0);
                }
            }
            case "mirror_dome" -> {
                dome(l, dust(0xC0E8FF, 1.4f), x, y, z, a, (int) (a * 14));
                dome(l, ParticleTypes.END_ROD, x, y, z, a, 10);
            }
            // Uzu
            case "whirlpool" -> {
                spiral(l, ParticleTypes.SPLASH, x, y, z, a, 0.6, (int) (40 + a * 6), 0.5, 0.15);
                spiral(l, ParticleTypes.BUBBLE_POP, x, y, z, a * 0.6, 0.4, 20, 0.4, 0.1);
                if (b > 0) shake(3);
            }
            case "vortex" -> spiral(l, ParticleTypes.CLOUD, x, y, z, a, 4 + a * 0.3, (int) (30 + a * 6), 0.45, 0.1);
            case "whirl_trail" -> {
                Vec3 from = new Vec3(x, y, z), d = new Vec3(a, b, c).subtract(from);
                int n = (int) Math.max(4, d.length() / 0.3);
                for (int i = 0; i <= n; i++) {
                    Vec3 q = from.add(d.scale(i / (double) n));
                    double ang = i * 0.8;
                    l.addParticle(ParticleTypes.SPLASH, q.x + Math.cos(ang) * 0.6, q.y + Math.sin(ang) * 0.6, q.z, 0, 0, 0);
                }
            }
            // Kane
            case "bell_ring" -> {
                ring(l, dust(0xE0C04A, 1.6f), x, y, z, a, (int) (30 + a * 8), 0.5);
                for (int i = 0; i < 6; i++) l.addParticle(ParticleTypes.NOTE, x + (l.random.nextDouble() - 0.5) * 2, y + 1 + l.random.nextDouble(), z + (l.random.nextDouble() - 0.5) * 2, l.random.nextDouble(), 0, 0);
                if (a >= 8) shake(6);
            }
            case "bell_toll" -> {
                double rr = Math.max(0.8, a) / 0.3, hh = Math.max(1.5, b);
                for (int h = 0; h < 3; h++) ring(l, dust(0xE0C04A, 1.3f), x, y + 0.3 + h * hh / 3, z, rr, 20, 0.05);
                l.addParticle(ParticleTypes.NOTE, x, y + hh + 0.4, z, l.random.nextDouble(), 0, 0);
            }
            case "resonance" -> {
                for (int h = 0; h < Math.min(6, (int) a); h++) ring(l, dust(0xE0C04A, 1.2f), x, y - 0.6 + h * 0.4, z, (0.6 + h * 0.2) / 0.3, 16, 0.08);
                l.addParticle(ParticleTypes.NOTE, x, y + 1.2, z, l.random.nextDouble(), 0, 0);
            }
            case "bell_dome" -> {
                dome(l, dust(0xE0C04A, 1.6f), x, y, z, a, 80);
                l.addParticle(ParticleTypes.NOTE, x, y + a + 0.5, z, 0.5, 0, 0);
            }
            case "great_bell" -> {
                dome(l, dust(0xE0C04A, 2.2f), x, y + b, z, a, 120);
                line(l, dust(0x8A7A2A, 1.0f), new Vec3(x, y + b, z), new Vec3(x, y, z), 0.5);
                ring(l, dust(0xE0C04A, 1.4f), x, y + 0.2, z, a, 40, 0.1);
            }
            case "sound_beam" -> {
                Vec3 from = new Vec3(x, y, z), d = new Vec3(a, b, c).subtract(from);
                int n = (int) Math.max(1, d.length() / 2.5);
                for (int i = 1; i <= n; i++) {
                    Vec3 q = from.add(d.scale(i / (double) n));
                    l.addParticle(ParticleTypes.SONIC_BOOM, q.x, q.y, q.z, 0, 0, 0);
                }
            }
            default -> burst(l, ParticleTypes.CRIT, x, y, z, 6, 0.5, 0.2);
        }
    }

    /** Projectile trails (called from AbilityProjectile on the client). */
    public static void trail(Entity p, String kind, float size) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null) return;
        double x = p.getX(), y = p.getY() + size / 2, z = p.getZ();
        if (canonWave3Trail(l, p, kind, x, y, z, size)) return; // fruit wave 3 (canon), see block below
        switch (kind) {
            case "fire_fist", "fire_bullet", "flame_emperor", "fire_bird" -> burst(l, ParticleTypes.FLAME, x, y, z, 2 + (int) (size * 6), size, 0.02);
            case "ice_spear" -> burst(l, ParticleTypes.SNOWFLAKE, x, y, z, 2, size, 0.01);
            case "lightning_bolt" -> burst(l, ParticleTypes.ELECTRIC_SPARK, x, y, z, 4, size, 0.1);
            case "water_shot" -> burst(l, ParticleTypes.SPLASH, x, y, z, 2, size, 0.02);
            case "wind_blade", "tempest" -> burst(l, ParticleTypes.CLOUD, x, y, z, 2, size, 0.01);
            case "air_blade", "air_blade_big", "crescent" -> burst(l, dust(0xEFFFFF, 1.0f), x, y, z, 3, size, 0.0);
            case "haki_wave" -> burst(l, dust(0x111111, 1.4f), x, y, z, 3, size, 0.0);
            case "cannonball" -> l.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
            case "light_bead", "mirror_beam" -> l.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
            case "magma_fist", "meteor" -> {
                burst(l, ParticleTypes.FLAME, x, y, z, 2 + (int) (size * 4), size, 0.02);
                if (l.random.nextInt(3) == 0) l.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
                l.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0.02, 0);
            }
            case "sand_blade" -> burst(l, dust(0xD9B56A, 1.2f), x, y, z, 3, size, 0.01);
            case "smoke_fist", "white_snake" -> burst(l, ParticleTypes.CLOUD, x, y, z, 2, size, 0.01);
            case "dark_debris" -> burst(l, dust(0x1A0F24, 1.2f), x, y, z, 1, size, 0);
            case "string_bullet", "salt_shard" -> l.addParticle(dust(0xF4F4F4, 0.7f), x, y, z, 0, 0, 0);
            case "mirror_shard" -> burst(l, dust(0xC0E8FF, 0.8f), x, y, z, 1, size, 0);
            case "phoenix_flame" -> burst(l, ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 3, size, 0.01);
            case "spiral_drill", "water_drill" -> {
                double ang = p.tickCount * 1.2;
                l.addParticle(kind.startsWith("water") ? ParticleTypes.SPLASH : ParticleTypes.CLOUD, x + Math.cos(ang) * size, y + Math.sin(ang) * size, z, 0, 0, 0);
                l.addParticle(kind.startsWith("water") ? ParticleTypes.BUBBLE_POP : ParticleTypes.CLOUD, x - Math.cos(ang) * size, y - Math.sin(ang) * size, z, 0, 0, 0);
            }
            case "tempest_kick" -> burst(l, dust(0xEFFFFF, 1.0f), x, y, z, 3, size, 0.0);
            case "bullet", "pellet", "knife", "arrow", "spear" -> {}
            default -> burst(l, ParticleTypes.ENCHANTED_HIT, x, y, z, 1, size, 0.0);
        }
    }

    // ===================================================================== fruit wave 3 (canon)
    // Effects and projectile trails for Yuki, Gasu, Numa, Mori, Bomu, Supa, Nikyu, Mochi, Horo, Doru, Soru, Hobi, Toki
    // and the Elephant / Allosaurus / Pteranodon / Spinosaurus / Seiryu / Orochi Zoans. Return true when handled;
    // anything else falls through to the shared effects above. (Projectile textures fall back to "energy".)

    private static boolean canonWave3(ClientLevel l, String kind, double x, double y, double z, float a, float b, float c) {
        RandomSource r = l.random;
        Vec3 from = new Vec3(x, y, z), to = new Vec3(a, b, c);
        if (kind.startsWith("impact_")) {
            switch (kind.substring(7)) {
                case "snow_shuriken" -> {
                    burst(l, ParticleTypes.SNOWFLAKE, x, y, z, 10, 0.4, 0.2);
                    burst(l, ParticleTypes.ITEM_SNOWBALL, x, y, z, 6, 0.3, 0.1);
                }
                case "mud_ball" -> burst(l, dust(0x4A3A22, 1.4f), x, y, z, 12, 0.5, 0.2);
                case "wood_stake" -> burst(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()), x, y, z, 10, 0.3, 0.15);
                case "bomb_pellet", "toy_soldier" -> burst(l, ParticleTypes.SMOKE, x, y, z, 8, 0.4, 0.05);
                case "steel_crescent", "wing_crescent" -> {
                    burst(l, ParticleTypes.SWEEP_ATTACK, x, y, z, 2, 0.4, 0);
                    burst(l, ParticleTypes.CRIT, x, y, z, 8, 0.4, 0.3);
                }
                case "paw_bubble" -> {
                    ring(l, ParticleTypes.POOF, x, y, z, 1.5, 16, 0.25);
                    burst(l, dust(0xFFD0E0, 1.3f), x, y, z, 12, 0.6, 0.1);
                }
                case "mochi_fist" -> burst(l, dust(0xF5EEDC, 1.8f), x, y, z, 22, 0.8, 0.15);
                case "negative_hollow", "mini_hollow", "toku_hollow" -> {
                    burst(l, dust(0xF0D0F0, 1.4f), x, y, z, kind.endsWith("toku_hollow") ? 50 : 14, Math.max(0.5, a), 0.15);
                    burst(l, ParticleTypes.SOUL, x, y, z, 6, 0.6, 0.05);
                }
                case "wax_harpoon" -> burst(l, ParticleTypes.WAX_ON, x, y, z, 12, 0.5, 0.1);
                case "prometheus_sun" -> {
                    burst(l, ParticleTypes.FLAME, x, y, z, 40, 1.5, 0.3);
                    burst(l, ParticleTypes.LAVA, x, y, z, 10, 1.2, 0.2);
                }
                case "toy_box" -> burst(l, ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 30, 1.2, 0.3);
                default -> {
                    return false;
                }
            }
            return true;
        }
        switch (kind) {
            case "intangible_gasu" -> burst(l, dust(0xB98AD6, 1.4f), x, y, z, 20, 1.0, 0.1);
            case "intangible_numa" -> burst(l, dust(0x4A3A22, 1.4f), x, y, z, 18, 0.9, 0.1);
            case "intangible_mori" -> {
                burst(l, ParticleTypes.COMPOSTER, x, y, z, 12, 0.9, 0.1);
                burst(l, dust(0x2E7A2E, 1.2f), x, y, z, 10, 0.9, 0.1);
            }
            case "intangible_mochi" -> burst(l, dust(0xF5EEDC, 1.6f), x, y, z, 22, 1.0, 0.12);
            case "hit_poison" -> burst(l, dust(0x7E3FA8, 1.2f), x, y, z, 8 + (int) Math.min(12, a), 0.5, 0.3);
            // Yuki
            case "snow_blade" -> {
                ring(l, ParticleTypes.SNOWFLAKE, x, y, z, a, 40, 0.45);
                burst(l, ParticleTypes.ITEM_SNOWBALL, x, y, z, 16, a * 0.6, 0.1);
            }
            case "blizzard" -> {
                spiral(l, ParticleTypes.SNOWFLAKE, x, y, z, a, 5, (int) (30 + a * 8), 0.4, 0.05);
                burst(l, dust(0xF4FAFF, 1.8f), x, y + 2, z, 20, a * 1.5, 0.05);
            }
            case "kamakura" -> {
                dome(l, dust(0xF4FAFF, 1.6f), x, y, z, a, 90);
                dome(l, ParticleTypes.SNOWFLAKE, x, y, z, a, 20);
            }
            case "whiteout" -> {
                burst(l, dust(0xF4FAFF, 2.5f), x, y + 2, z, (int) (40 + a * 4), a * 1.6, 0.04);
                spiral(l, ParticleTypes.SNOWFLAKE, x, y, z, a, 6, 40, 0.5, 0.05);
            }
            // Gasu
            case "gas_jet" -> {
                line(l, ParticleTypes.FLAME, from, to, 0.5);
                line(l, dust(0xB98AD6, 1.1f), from, to, 0.6);
            }
            case "gas_explosion" -> {
                l.addParticle(ParticleTypes.EXPLOSION, x, y, z, 0, 0, 0);
                burst(l, ParticleTypes.FLAME, x, y, z, 30, a, 0.25);
                burst(l, dust(0x7E3FA8, 1.6f), x, y, z, 20, a, 0.1);
            }
            case "gas_cloud" -> {
                burst(l, dust(0x7E3FA8, 2.0f), x, y + 1, z, (int) (20 + a * 6), a * 1.6, 0.02);
                disc(l, ParticleTypes.WITCH, x, y, z, a, 12, 0.05, 0.03);
            }
            case "gas_robe" -> {
                column(l, ParticleTypes.FLAME, x, y - 1, z, a, 2, 20);
                column(l, dust(0xB98AD6, 1.0f), x, y - 1, z, a, 2, 10);
            }
            case "shinokuni" -> {
                burst(l, dust(0x5A2A7A, 2.5f), x, y + 1.5, z, (int) (30 + a * 6), a * 1.7, 0.02);
                burst(l, dust(0x9A5AC0, 2.0f), x, y + 3, z, 20, a, 0.02);
                disc(l, ParticleTypes.WITCH, x, y, z, a, 16, 0.05, 0.05);
            }
            // Numa
            case "swamp_gulp" -> burst(l, dust(0x4A3A22, 1.3f), x, y, z, 10, 0.6, 0.15);
            case "mud_grab" -> {
                column(l, dust(0x4A3A22, 1.6f), x, y, z, a, b, 30);
                burst(l, ParticleTypes.BUBBLE_POP, x, y + 0.2, z, 8, Math.max(0.6, a), 0.05);
            }
            case "swamp" -> {
                disc(l, dust(0x4A3A22, 1.6f), x, y, z, a, (int) (a * 8), 0.05 + b * 0.1, -0.01);
                disc(l, ParticleTypes.BUBBLE_POP, x, y, z, a, 10, 0, 0.05);
            }
            case "mud_burst" -> burst(l, dust(0x5A4A2A, 1.8f), x, y, z, (int) (20 + a * 8), Math.max(1, a), 0.3);
            // Mori
            case "roots" -> {
                column(l, dust(0x6A4A2A, 1.6f), x, y, z, a * 0.5, 2.5, 30);
                burst(l, ParticleTypes.COMPOSTER, x, y + 0.5, z, 10, a, 0.1);
            }
            case "life_drain" -> {
                line(l, dust(0x8AC94A, 1.0f), from, to, 0.4);
                burst(l, ParticleTypes.COMPOSTER, x, y, z, 8, 0.6, 0.05);
            }
            case "forest_burst" -> {
                disc(l, ParticleTypes.COMPOSTER, x, y, z, a, (int) (a * 8), -0.05, 0.15);
                burst(l, dust(0x2E7A2E, 1.8f), x, y + 2, z, 30, a, 0.05);
                if (b > 0) shake(6);
            }
            case "leaf_heal" -> burst(l, ParticleTypes.HAPPY_VILLAGER, x, y, z, 6, 0.8, 0.02);
            // Bomu
            case "breath_trail" -> line(l, ParticleTypes.SMOKE, from, to, 0.6);
            // Supa
            case "blade_sweep" -> {
                ring(l, dust(0xC0C0C8, 1.2f), x, y, z, a, 40, 0.5);
                for (int i = 0; i < 3; i++) l.addParticle(ParticleTypes.SWEEP_ATTACK, x + (r.nextDouble() - 0.5) * a * 0.6, y, z + (r.nextDouble() - 0.5) * a * 0.6, 0, 0, 0);
            }
            case "steel_body" -> {
                column(l, dust(0xC0C0C8, 1.3f), x, y - 1, z, a, 2, 30);
                burst(l, ParticleTypes.CRIT, x, y, z, 10, 0.8, 0.2);
            }
            case "blade_trail" -> {
                line(l, dust(0xC0C0C8, 1.2f), from, to, 0.3);
                line(l, ParticleTypes.SWEEP_ATTACK, from, to, 1.5);
            }
            case "blade_storm" -> {
                sphere(l, dust(0xC0C0C8, 1.2f), x, y, z, a * 0.7, 60);
                for (int i = 0; i < 8; i++) l.addParticle(ParticleTypes.SWEEP_ATTACK, x + (r.nextDouble() - 0.5) * a, y + (r.nextDouble() - 0.5) * 2, z + (r.nextDouble() - 0.5) * a, 0, 0, 0);
            }
            // Nikyu
            case "paw_burst" -> {
                line(l, dust(0xFFD0E0, 1.2f), from, to, 0.5);
                burst(l, ParticleTypes.POOF, x, y, z, 10, 0.6, 0.1);
            }
            case "pain_bubble" -> {
                sphere(l, dust(0xAA2A40, 1.2f), x, y, z, Math.max(0.5, a) * 0.6, 40);
                sphere(l, dust(0xFFD0E0, 0.8f), x, y, z, Math.max(0.5, a) * 0.6, 20);
            }
            case "ursus_charge" -> sphere(l, dust(0xFFD0E0, 1.6f), x, y, z, a, 80);
            case "ursus_shock" -> {
                l.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 0, 0, 0);
                sphere(l, dust(0xFFD0E0, 2.0f), x, y, z, a * 0.6, 120);
                ring(l, ParticleTypes.CLOUD, x, y - 0.8, z, a, 80, 0.8);
                shake(16);
            }
            // Mochi
            case "mogura" -> line(l, dust(0xF5EEDC, 1.6f), from, to, 0.3);
            case "mochi_bind" -> column(l, dust(0xF5EEDC, 1.8f), x, y - 1, z, a, b, 40);
            case "mochi_fists" -> {
                for (int i = 0; i < 4; i++) {
                    double ox = (r.nextDouble() - 0.5) * a * 1.4, oz = (r.nextDouble() - 0.5) * a * 1.4;
                    line(l, dust(0xF5EEDC, 2.0f), new Vec3(x + ox, y + 5, z + oz), new Vec3(x + ox, y + 0.3, z + oz), 0.4);
                }
                ring(l, dust(0xF5EEDC, 1.4f), x, y + 0.2, z, a, 30, 0.3);
            }
            case "mochi_donut" -> ring(l, dust(0xF5EEDC, 1.5f), x, y, z, Math.max(1, a) / 0.3, 24, 0.05);
            case "mochi_flood" -> {
                disc(l, dust(0xF5EEDC, 2.0f), x, y, z, a, (int) (a * 10), -0.05, 0.05 + b * 0.3);
                if (b > 0) shake(8);
            }
            // Horo
            case "negative_hollow" -> {
                column(l, dust(0xF0D0F0, 1.4f), x, y - 1, z, a, b, 30);
                burst(l, ParticleTypes.SOUL, x, y, z, 6, 0.6, 0.02);
            }
            case "ghost_body" -> {
                burst(l, dust(0xF0D0F0, 1.6f), x, y, z, 30, 1.0, 0.05);
                burst(l, ParticleTypes.SOUL, x, y, z, 10, 0.8, 0.05);
            }
            case "hollow_parade" -> {
                ring(l, dust(0xF0D0F0, 2.0f), x, y, z, a, 120, 0.7);
                burst(l, ParticleTypes.SOUL, x, y + 1, z, 30, a * 0.8, 0.05);
            }
            // Doru
            case "wax_bind" -> {
                column(l, dust(0xF0F0E0, 1.6f), x, y, z, a, Math.max(0.8, b), 30);
                burst(l, ParticleTypes.WAX_ON, x, y + 0.5, z, 8, Math.max(0.6, a), 0.05);
            }
            case "wax_wall" -> {
                plane(l, dust(0xF0F0E0, 1.6f), x, y, z, a, b, Math.max(1, c), 3.0, 70);
                plane(l, ParticleTypes.WAX_ON, x, y, z, a, b, Math.max(1, c), 3.0, 12);
            }
            case "wax_armour" -> {
                column(l, dust(0xF0F0E0, 1.4f), x, y - 1, z, a, 2, 40);
                burst(l, ParticleTypes.WAX_ON, x, y, z, 12, 1, 0.05);
            }
            case "wax_spiral" -> {
                spiral(l, dust(0xF0F0E0, 1.5f), x, y, z, a, 1.5, (int) (30 + a * 6), 0.35, 0.05);
                disc(l, ParticleTypes.WAX_ON, x, y, z, a, 10, 0, 0.05);
            }
            // Soru
            case "soul_resist" -> burst(l, ParticleTypes.ENCHANTED_HIT, x, y, z, 10, 0.8, 0.1);
            case "soul_pull" -> {
                line(l, ParticleTypes.SOUL, from, to, 0.6);
                burst(l, dust(0xF5C04A, 1.2f), x, y, z, 10, 0.6, 0.1);
            }
            case "soul_pocus" -> {
                ring(l, dust(0xFF6A6A, 1.6f), x, y, z, a, (int) (40 + a * 6), 0.6);
                burst(l, ParticleTypes.SOUL, x, y + 1, z, 20, a * 0.6, 0.05);
                if (b > 0) shake(10);
            }
            case "zeus_cloud" -> {
                burst(l, dust(0x3A3A4A, 3f), x, y, z, 50, a * 1.5, 0.02);
                burst(l, ParticleTypes.ELECTRIC_SPARK, x, y - 0.5, z, 16, a, 0.2);
            }
            case "ikoku" -> {
                line(l, dust(0xF5C04A, 1.4f), from, to, 0.35);
                line(l, ParticleTypes.SWEEP_ATTACK, from, to, 2);
                line(l, ParticleTypes.CLOUD, from, to, 0.8);
                shake(6);
            }
            case "homie_land" -> {
                if (c > 0) disc(l, ParticleTypes.COMPOSTER, x, y, z, 10, 60, 0, 0.1);
                else {
                    column(l, dust(0x6A4A2A, 1.5f), x, y, z, a, b, 24);
                    burst(l, ParticleTypes.HAPPY_VILLAGER, x, y + b, z, 4, 0.5, 0.05);
                }
            }
            // Hobi
            case "toy_poof" -> {
                burst(l, ParticleTypes.POOF, x, y, z, 12, 0.6 * Math.max(1, a), 0.05);
                burst(l, ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 10, 0.6, 0.15);
            }
            case "confetti" -> {
                burst(l, ParticleTypes.TOTEM_OF_UNDYING, x, y, z, (int) (20 + a * 8), a, 0.3);
                for (int i = 0; i < 12; i++) l.addParticle(dust(rainbow(r), 1.2f), x + (r.nextDouble() - 0.5) * a, y + r.nextDouble(), z + (r.nextDouble() - 0.5) * a, 0, 0.05, 0);
                if (b > 0) shake(4);
            }
            // Toki
            case "time_vanish", "time_return" -> {
                column(l, dust(0xD0D0FF, 1.4f), x, y - 1, z, a, b, 36);
                burst(l, ParticleTypes.REVERSE_PORTAL, x, y, z, 24, 0.8, 0.2);
                if (kind.endsWith("return")) l.addParticle(ParticleTypes.FLASH, x, y, z, 0, 0, 0);
            }
            case "time_trail" -> {
                line(l, dust(0xD0D0FF, 1.1f), from, to, 0.4);
                line(l, ParticleTypes.REVERSE_PORTAL, from, to, 0.8);
            }
            case "hourglass" -> {
                ring(l, dust(0xD0D0FF, 1.6f), x, y + 0.2, z, a / 0.3, (int) (30 + a * 6), 0);
                spiral(l, dust(0xE8D08A, 1.2f), x, y, z, a, 3, 30, -0.3, 0.05);
                if (b > 0) shake(8);
            }
            // Zoans: Elephant
            case "trunk_sweep" -> {
                ring(l, dust(0x9A9AA8, 1.4f), x, y, z, a, 30, 0.5);
                l.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0, 0, 0);
            }
            case "water_jet" -> {
                line(l, ParticleTypes.SPLASH, from, to, 0.3);
                line(l, ParticleTypes.FALLING_WATER, from, to, 0.5);
                burst(l, ParticleTypes.SPLASH, a, b, c, 20, 1.2, 0.3);
            }
            // Ancient Zoans
            case "bite" -> {
                for (int i = -1; i <= 1; i += 2) l.addParticle(ParticleTypes.SWEEP_ATTACK, x, y + i * 0.3, z, 0, 0, 0);
                burst(l, ParticleTypes.CRIT, x, y, z, 10 + (int) (a * 6), 0.4 * Math.max(1, a), 0.3);
                burst(l, dust(0x8A0A0A, 1.0f), x, y, z, 6, 0.5, 0.1);
            }
            case "tail_sweep", "sail_spin" -> {
                ring(l, ParticleTypes.CLOUD, x, y, z, a, 40, 0.5);
                if (kind.equals("sail_spin")) ring(l, dust(0xC04A3A, 1.4f), x, y + 0.6, z, a, 40, 0.5);
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3;
                    l.addParticle(ParticleTypes.SWEEP_ATTACK, x + Math.cos(ang) * a * 0.6, y, z + Math.sin(ang) * a * 0.6, 0, 0, 0);
                }
            }
            case "wing_gust" -> {
                ring(l, ParticleTypes.CLOUD, x, y, z, a, 30, 0.6);
                burst(l, ParticleTypes.SMALL_GUST, x, y + 0.5, z, 4, a * 0.5, 0);
            }
            case "dive_trail" -> {
                line(l, ParticleTypes.CLOUD, from, to, 0.5);
                l.addParticle(ParticleTypes.GUST, a, b, c, 0, 0, 0);
            }
            // Mythical Zoans: Seiryu, Orochi
            case "dragon_cloud" -> {
                disc(l, ParticleTypes.CLOUD, x, y, z, a, 30, -0.05, 0.02);
                burst(l, dust(0xC0E0FF, 1.6f), x, y, z, 16, a, 0.05);
            }
            case "bolo_breath" -> {
                line(l, ParticleTypes.FLAME, from, to, 0.35);
                line(l, dust(0xFFC23A, 1.6f), from, to, 0.5);
                line(l, ParticleTypes.LAVA, from, to, 2.5);
                shake(5);
            }
            case "serpent_strike" -> {
                line(l, dust(0x5A2A6A, 1.3f), from, to, 0.35);
                burst(l, ParticleTypes.CRIT, a, b, c, 6, 0.4, 0.2);
            }
            case "venom" -> {
                burst(l, dust(0x6ACB3A, 1.2f), x, y, z, 14, Math.max(0.6, a), 0.1);
                burst(l, ParticleTypes.ITEM_SLIME, x, y, z, 6, 0.6, 0.1);
                if (b > 0) burst(l, ParticleTypes.HEART, x, y + 0.5, z, 4, 0.8, 0.05);
            }
            case "coil" -> {
                double rr = Math.max(0.5, a * 0.8), hh = Math.max(1.5, b);
                for (int i = 0; i < 40; i++) {
                    double t = i / 40.0, ang = t * Math.PI * 6;
                    l.addParticle(dust(0x5A2A6A, 1.4f), x + Math.cos(ang) * rr, y - 1 + t * hh, z + Math.sin(ang) * rr, 0, 0, 0);
                }
            }
            case "head_lost" -> {
                burst(l, dust(0x5A2A6A, 1.6f), x, y, z, 30, 1.2, 0.3);
                burst(l, ParticleTypes.SOUL, x, y, z, 10, 0.8, 0.1);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean canonWave3Trail(ClientLevel l, Entity p, String kind, double x, double y, double z, float size) {
        switch (kind) {
            case "snow_shuriken" -> l.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0, 0, 0);
            case "mud_ball" -> l.addParticle(dust(0x4A3A22, 1.2f), x, y, z, 0, 0, 0);
            case "wood_stake" -> l.addParticle(dust(0x8A6A3A, 0.8f), x, y, z, 0, 0, 0);
            case "bomb_pellet" -> l.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
            case "steel_crescent" -> burst(l, dust(0xC0C0C8, 1.0f), x, y, z, 2, size, 0);
            case "wing_crescent" -> burst(l, dust(0xC09A6A, 1.0f), x, y, z, 2, size, 0);
            case "paw_bubble" -> burst(l, dust(0xFFD0E0, 1.2f), x, y, z, 2, size, 0);
            case "mochi_fist" -> burst(l, dust(0xF5EEDC, 1.6f), x, y, z, 3, size, 0);
            case "negative_hollow", "mini_hollow", "toku_hollow" -> {
                burst(l, dust(0xF0D0F0, 1.2f), x, y, z, kind.startsWith("toku") ? 4 : 1, size, 0);
                if (p.tickCount % 4 == 0) l.addParticle(ParticleTypes.SOUL, x, y, z, 0, 0.01, 0);
            }
            case "wax_harpoon" -> l.addParticle(dust(0xF0F0E0, 0.9f), x, y, z, 0, 0, 0);
            case "prometheus_sun" -> {
                burst(l, ParticleTypes.FLAME, x, y, z, 4, size, 0.02);
                if (l.random.nextInt(3) == 0) l.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            }
            case "toy_soldier" -> l.addParticle(dust(rainbow(l.random), 0.8f), x, y, z, 0, 0, 0);
            case "toy_box" -> {
                if (p.tickCount % 2 == 0) l.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 0, 0, 0);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
    // ===================================================================== end fruit wave 3 (canon)

    public static int shakeTicks = 0;

    public static void shake(int ticks) {
        shakeTicks = Math.max(shakeTicks, ticks);
    }

    private ClientFx() {}
}
