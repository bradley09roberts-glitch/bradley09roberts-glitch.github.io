package com.starforged.tempest.world;

import com.starforged.Starforged;
import com.starforged.moon.world.PaleReachTravel;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.tempest.TempestSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Travel to and from Stormreach. Stormgates are linked by coordinates like the other gateways; the matching gate is raised
 * on the far side if there isn't one already. Gates opened in the Pale Reach lead to Stormreach too, but come home to the
 * Overworld.
 */
public final class StormreachTravel {
    public static final ResourceKey<Level> STORMREACH = ResourceKey.create(Registries.DIMENSION, Starforged.id("stormreach"));
    /** Gates over the void are built at this height on a fresh floating island. */
    private static final int SKY_GATE_Y = 112;

    private StormreachTravel() {
    }

    public static boolean isStormreach(Level level) {
        return level.dimension() == STORMREACH;
    }

    /** It is always storming in Stormreach. */
    public static void forceStorm(Level level) {
        if (level.dimension() == STORMREACH) {
            level.setRainLevel(1.0F);
            level.setThunderLevel(1.0F);
        }
    }

    /** Where a Skybreaker Core may open a gate: the Overworld, Stormreach, or the Pale Reach at High Tide. */
    public static boolean canOpenGate(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD || isStormreach(level)
            || PaleReachTravel.isPaleReach(level) && com.starforged.moon.world.MoonTides.isHighTide(level);
    }

    public static @Nullable TeleportTransition destination(ServerLevel current, Entity entity, BlockPos entry) {
        ResourceKey<Level> targetKey = current.dimension() == STORMREACH ? Level.OVERWORLD : STORMREACH;
        ServerLevel target = current.getServer().getLevel(targetKey);
        if (target == null) {
            return null;
        }
        BlockPos gate = gatewayAt(target, entry.getX(), entry.getZ());
        Vec3 arrival = new Vec3(gate.getX() + 0.5, gate.getY(), gate.getZ() + 2.5);
        boolean toStorm = targetKey == STORMREACH;
        return new TeleportTransition(target, arrival, Vec3.ZERO, 0.0F, entity.getXRot(), e -> arrived(e, target, toStorm));
    }

    /** Teleports a player straight to Stormreach (used by /starforged stormreach). */
    public static void sendToStormreach(ServerPlayer player) {
        ServerLevel target = player.level().getServer().getLevel(STORMREACH);
        if (target == null) {
            return;
        }
        BlockPos gate = gatewayAt(target, player.getBlockX(), player.getBlockZ());
        player.teleport(new TeleportTransition(target, new Vec3(gate.getX() + 0.5, gate.getY(), gate.getZ() + 2.5), Vec3.ZERO, 0.0F, 0.0F,
            e -> arrived(e, target, true)));
    }

    public static BlockPos gatewayAt(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean overVoid = surface <= level.getMinY() + 4;
        int y = overVoid ? SKY_GATE_Y : Mth.clamp(surface, level.getMinY() + 8, level.getMaxY() - 12);
        BlockPos guess = new BlockPos(x, y, z);
        BlockPos existing = StormgateBuilder.find(level, guess, 6, 12);
        if (existing != null) {
            return existing;
        }
        if (overVoid) {
            StormgateBuilder.buildIsland(level, guess);
        }
        StormgateBuilder.build(level, guess);
        return guess;
    }

    private static void arrived(Entity entity, ServerLevel level, boolean toStorm) {
        entity.setPortalCooldown();
        level.playSound(null, entity.blockPosition(), TempestSounds.GATEWAY_TRAVEL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ModParticles.STATIC_SPARK.get(), entity.getX(), entity.getY() + 1.0, entity.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
        if (toStorm && entity instanceof ServerPlayer player) {
            SunFx.title(player, Component.translatable("event.starforged.stormreach.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.translatable("event.starforged.stormreach.subtitle").withStyle(ChatFormatting.GRAY), 10, 60, 20);
        }
    }
}
