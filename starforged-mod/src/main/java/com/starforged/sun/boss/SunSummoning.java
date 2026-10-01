package com.starforged.sun.boss;

import com.starforged.registry.ModParticles;
import com.starforged.sun.SunEntities;
import com.starforged.sun.SunFx;
import com.starforged.sun.SunSounds;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The waking of the Sun Warden: a shaft of sunlight hammers the altar, the ground quakes and splits, flame
 * rings race outward - and the Warden rises out of the earth behind the altar.
 */
public final class SunSummoning {
    private static final List<Ritual> RITUALS = new ArrayList<>();
    private static final int SPAWN_TICK = 110;

    private static final class Ritual {
        final ResourceKey<Level> dimension;
        final BlockPos altar;
        final BlockPos rise;
        int tick;

        Ritual(ResourceKey<Level> dimension, BlockPos altar, BlockPos rise) {
            this.dimension = dimension;
            this.altar = altar;
            this.rise = rise;
        }
    }

    private SunSummoning() {
    }

    public static boolean tryBegin(ServerLevel level, BlockPos altar, ServerPlayer player) {
        for (Ritual ritual : RITUALS) {
            if (ritual.dimension.equals(level.dimension()) && ritual.altar.closerThan(altar, 96)) {
                player.sendOverlayMessage(Component.translatable("block.starforged.sun_altar.busy").withStyle(ChatFormatting.RED));
                return false;
            }
        }
        if (!level.getEntitiesOfClass(SunWardenEntity.class, new AABB(altar).inflate(128)).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.sun_altar.busy").withStyle(ChatFormatting.RED));
            return false;
        }
        begin(level, altar, player.position());
        return true;
    }

    /** Starts the ritual; the Warden rises on the far side of the altar from {@code viewer}. */
    public static void begin(ServerLevel level, BlockPos altar, Vec3 viewer) {
        Vec3 away = Vec3.atCenterOf(altar).subtract(viewer).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) {
            away = new Vec3(0, 0, 1);
        }
        away = away.normalize().scale(7.0);
        BlockPos rise = BlockPos.containing(altar.getX() + 0.5 + away.x, altar.getY() - 1, altar.getZ() + 0.5 + away.z);
        RITUALS.add(new Ritual(level.dimension(), altar.immutable(), rise));
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
        Vec3 top = Vec3.atBottomCenterOf(ritual.altar).add(0, 1.0, 0);
        Vec3 rise = Vec3.atBottomCenterOf(ritual.rise.above());
        // A shaft of sunlight pours down onto the altar.
        if (t % 2 == 0) {
            double height = Math.min(70.0, t * 1.5);
            for (int i = 0; i < 6; i++) {
                double y = level.getRandom().nextDouble() * height;
                level.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ModParticles.SOLAR_SPARK.get(), true, true,
                    top.x + (level.getRandom().nextDouble() - 0.5) * 0.8, top.y + y, top.z + (level.getRandom().nextDouble() - 0.5) * 0.8, 1, 0, -0.1, 0, 0.0);
            }
        }
        if (t == 1) {
            level.playSound(null, top.x, top.y, top.z, SunSounds.SUN_ALTAR_ACTIVATE.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
            Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), top, 0.5, 60, 0.5);
            SunFx.messageNear(level, top, 96.0, Component.translatable("event.starforged.sun_summon.altar").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        }
        if (t == 30) {
            SunFx.titleNear(level, top, 128.0, Component.translatable("event.starforged.sun_summon.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable("event.starforged.sun_summon.subtitle").withStyle(ChatFormatting.YELLOW), 10, 50, 10);
            level.playSound(null, top.x, top.y, top.z, SunSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        if (t > 30 && t < SPAWN_TICK && t % 10 == 0) {
            Fx.shake(level, top, 48.0, 0.3F + t / (float) SPAWN_TICK * 0.8F, 12);
            Fx.ring(level, ParticleTypes.FLAME, rise.add(0, 0.2, 0), 1.0 + (t - 30) * 0.08, 40, 0.25, 0.0);
            level.sendParticles(ParticleTypes.LAVA, rise.x, rise.y, rise.z, 8, 1.5, 0.1, 1.5, 0.0);
        }
        if (t == SPAWN_TICK) {
            SunWardenEntity warden = SunEntities.SUN_WARDEN.get().create(level, EntitySpawnReason.EVENT);
            if (warden != null) {
                warden.setHome(ritual.rise);
                warden.snapTo(rise.x, rise.y - 6.5, rise.z, 0.0F, 0.0F);
                level.addFreshEntity(warden);
            }
            level.playSound(null, rise.x, rise.y, rise.z, SunSounds.WARDEN_SUPERNOVA.get(), SoundSource.HOSTILE, 5.0F, 0.5F);
            Fx.shake(level, rise, 64.0, 1.4F, 30);
            return true;
        }
        return false;
    }
}
