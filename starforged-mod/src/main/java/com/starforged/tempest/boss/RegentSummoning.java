package com.starforged.tempest.boss;

import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.tempest.TempestEntities;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.StormNetwork;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
 * The calling of Veyr: the storm gathers at the rim of the summit and spirals inward, the sky goes white with
 * lightning, and his empty armour is struck down onto the altar - where it assembles itself.
 */
public final class RegentSummoning {
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

    private RegentSummoning() {
    }

    public static boolean tryBegin(ServerLevel level, BlockPos altar, ServerPlayer player) {
        boolean busy = RITUALS.stream().anyMatch(r -> r.dimension.equals(level.dimension()) && r.altar.closerThan(altar, 96))
            || !level.getEntitiesOfClass(VeyrEntity.class, new AABB(altar).inflate(128)).isEmpty();
        if (busy) {
            player.sendOverlayMessage(Component.translatable("block.starforged.tempest_altar.busy").withStyle(ChatFormatting.RED));
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
        if (t == 1) {
            level.playSound(null, altar.x, altar.y, altar.z, TempestSounds.ALTAR_ACTIVATE.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
            Fx.sphere(level, ModParticles.STATIC_SPARK.get(), altar, 0.5, 60, 0.5);
            SunFx.messageNear(level, altar, 96.0, Component.translatable("event.starforged.regent_summon.altar").withStyle(ChatFormatting.AQUA,
                ChatFormatting.ITALIC));
        }
        // The storm gathers at the rim and spirals in toward the altar.
        if (t > 5 && t < SPAWN_TICK) {
            double progress = t / (double) SPAWN_TICK;
            for (int i = 0; i < 8; i++) {
                double a = t * 0.12 + i * Math.PI / 4;
                double r = 16.0 * (1.0 - progress) + 2.0;
                Vec3 p = altar.add(Math.cos(a) * r, 1.0 + Math.sin(t * 0.2 + i) * 1.5, Math.sin(a) * r);
                level.sendParticles(ModParticles.STORM_WISP.get(), true, true, p.x, p.y, p.z, 2, 0.4, 0.4, 0.4, 0.01);
                level.sendParticles(ModParticles.STATIC_SPARK.get(), true, true, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
            }
            if (t % 15 == 0) {
                double a = level.getRandom().nextDouble() * Math.PI * 2;
                StormNetwork.visualBolt(level, BlockPos.containing(altar.add(Math.cos(a) * 14, -1, Math.sin(a) * 14)));
            }
            if (t % 10 == 0) {
                Fx.shake(level, altar, 48.0, 0.3F + (float) progress * 0.6F, 12);
            }
        }
        if (t == 40) {
            SunFx.titleNear(level, altar, 128.0, Component.translatable("event.starforged.regent_summon.title").withStyle(ChatFormatting.AQUA,
                ChatFormatting.BOLD), Component.translatable("event.starforged.regent_summon.subtitle").withStyle(ChatFormatting.WHITE), 10, 50, 10);
            level.playSound(null, altar.x, altar.y, altar.z, TempestSounds.SUPERCELL.get(), SoundSource.HOSTILE, 5.0F, 0.7F);
        }
        if (t == SPAWN_TICK) {
            VeyrEntity boss = TempestEntities.VEYR.get().create(level, EntitySpawnReason.EVENT);
            if (boss != null) {
                boss.setHome(ritual.altar.below());
                Vec3 spot = altar.add(0, 0, 4.0);
                boss.snapTo(spot.x, spot.y, spot.z, 180.0F, 0.0F);
                level.addFreshEntity(boss);
            }
            StormNetwork.visualBolt(level, BlockPos.containing(altar.add(0, 0, 4.0)));
            level.playSound(null, altar.x, altar.y, altar.z, TempestSounds.VEYR_THUNDERSTEP.get(), SoundSource.HOSTILE, 6.0F, 0.6F);
            return true;
        }
        return false;
    }
}
