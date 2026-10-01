package com.starforged.moon.boss;

import com.starforged.moon.MoonEntities;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.world.MoonGravity;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The calling of the Pale Matriarch: the sky darkens into an eclipse, the sea climbs into the air and gathers into a
 * hanging sphere, gravity fails - everyone floats - and she emerges from the water. Then, all at once, gravity returns.
 */
public final class MatriarchSummoning {
    private static final List<Ritual> RITUALS = new ArrayList<>();
    private static final int SPAWN_TICK = 120;

    private static final class Ritual {
        final ResourceKey<Level> dimension;
        final BlockPos altar;
        int tick;

        Ritual(ResourceKey<Level> dimension, BlockPos altar) {
            this.dimension = dimension;
            this.altar = altar;
        }
    }

    private MatriarchSummoning() {
    }

    public static boolean tryBegin(ServerLevel level, BlockPos altar, ServerPlayer player) {
        for (Ritual ritual : RITUALS) {
            if (ritual.dimension.equals(level.dimension()) && ritual.altar.closerThan(altar, 96)) {
                player.sendOverlayMessage(Component.translatable("block.starforged.moon_altar.busy").withStyle(ChatFormatting.RED));
                return false;
            }
        }
        if (!level.getEntitiesOfClass(PaleMatriarchEntity.class, new AABB(altar).inflate(128)).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.moon_altar.busy").withStyle(ChatFormatting.RED));
            return false;
        }
        begin(level, altar);
        return true;
    }

    public static void begin(ServerLevel level, BlockPos altar) {
        RITUALS.add(new Ritual(level.dimension(), altar.immutable()));
    }

    public static void tick(ServerLevel level) {
        if (RITUALS.isEmpty()) {
            return;
        }
        Iterator<Ritual> it = RITUALS.iterator();
        while (it.hasNext()) {
            Ritual ritual = it.next();
            if (!ritual.dimension.equals(level.dimension())) {
                continue;
            }
            ritual.tick++;
            if (step(level, ritual)) {
                it.remove();
            }
        }
    }

    private static boolean step(ServerLevel level, Ritual ritual) {
        int t = ritual.tick;
        Vec3 altar = Vec3.atBottomCenterOf(ritual.altar).add(0, 1.0, 0);
        Vec3 sphere = altar.add(0, 9.0, 0);
        List<Player> players = level.getEntitiesOfClass(Player.class, new AABB(ritual.altar).inflate(48.0, 32.0, 48.0), p -> !p.isSpectator());
        if (t == 1) {
            level.playSound(null, altar.x, altar.y, altar.z, MoonSounds.MOON_ALTAR_ACTIVATE.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
            Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), altar, 0.5, 60, 0.5);
            SunFx.messageNear(level, altar, 96.0, Component.translatable("event.starforged.moon_summon.altar").withStyle(ChatFormatting.AQUA,
                ChatFormatting.ITALIC));
        }
        // Eclipse: the light dims in slow pulses.
        if (t % 20 == 1) {
            for (Player player : players) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
            }
        }
        // The sea climbs into the sky: streams of water spiral up from the edge of the arena into the sphere.
        if (t > 10 && t < SPAWN_TICK) {
            for (int i = 0; i < 6; i++) {
                double a = t * 0.15 + i * Math.PI / 3;
                double r = 16.0 - (t % 20) * 0.6;
                double h = (t % 20) * 0.45;
                Vec3 p = altar.add(Math.cos(a) * r, h - 1.0, Math.sin(a) * r);
                level.sendParticles(ParticleTypes.SPLASH, true, true, p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.0);
                level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), true, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            double radius = Math.min(4.0, t / 25.0);
            Fx.sphere(level, ParticleTypes.SPLASH, sphere, radius, (int) (12 * radius), 0.0);
            if (t % 4 == 0) {
                Fx.sphere(level, ModParticles.MOON_DUST.get(), sphere, radius * 0.8, 10, 0.0);
            }
        }
        if (t == 40) {
            SunFx.titleNear(level, altar, 128.0, Component.translatable("event.starforged.moon_summon.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.translatable("event.starforged.moon_summon.subtitle").withStyle(ChatFormatting.WHITE), 10, 50, 10);
            level.playSound(null, altar.x, altar.y, altar.z, MoonSounds.MATRIARCH_ROAR.get(), SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        // Gravity fails: everyone floats.
        if (t == 70) {
            level.playSound(null, altar.x, altar.y, altar.z, MoonSounds.MATRIARCH_INVERSION.get(), SoundSource.HOSTILE, 5.0F, 1.2F);
            for (Player player : players) {
                MoonGravity.invert(player, 120);
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0, false, false));
            }
        }
        if (t > 30 && t < SPAWN_TICK && t % 10 == 0) {
            Fx.shake(level, altar, 48.0, 0.3F + t / (float) SPAWN_TICK * 0.6F, 12);
        }
        if (t == SPAWN_TICK) {
            PaleMatriarchEntity boss = MoonEntities.PALE_MATRIARCH.get().create(level, EntitySpawnReason.EVENT);
            if (boss != null) {
                boss.setHome(ritual.altar);
                boss.snapTo(sphere.x, sphere.y - 2.5, sphere.z, 0.0F, 0.0F);
                level.addFreshEntity(boss);
            }
            level.playSound(null, sphere.x, sphere.y, sphere.z, MoonSounds.MATRIARCH_WAVE.get(), SoundSource.HOSTILE, 5.0F, 0.5F);
            Fx.sphere(level, ParticleTypes.SPLASH, sphere, 4.0, 300, 0.8);
            return true;
        }
        return false;
    }
}
