package com.starforged.moon.world;

import com.starforged.Starforged;
import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
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
 * Travel between the Overworld and the Pale Reach. Like Solar Gateways, Lunar Gateways are linked by coordinates and a
 * matching gateway is raised on the far side if there isn't one already.
 */
public final class PaleReachTravel {
    public static final ResourceKey<Level> PALE_REACH = ResourceKey.create(Registries.DIMENSION, Starforged.id("pale_reach"));

    private PaleReachTravel() {
    }

    public static boolean isPaleReach(Level level) {
        return level.dimension() == PALE_REACH;
    }

    /** The Pale Reach has no air, so no weather either. */
    public static void suppressWeather(Level level) {
        if (level.dimension() == PALE_REACH && (level.getRainLevel(1.0F) > 0.0F || level.getThunderLevel(1.0F) > 0.0F)) {
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
        }
    }

    public static @Nullable TeleportTransition destination(ServerLevel current, Entity entity, BlockPos entry) {
        ResourceKey<Level> targetKey = current.dimension() == PALE_REACH ? Level.OVERWORLD : PALE_REACH;
        ServerLevel target = current.getServer().getLevel(targetKey);
        if (target == null) {
            return null;
        }
        BlockPos gate = gatewayAt(target, entry.getX(), entry.getZ());
        Vec3 arrival = new Vec3(gate.getX() + 0.5, gate.getY(), gate.getZ() + 2.5);
        boolean toMoon = targetKey == PALE_REACH;
        return new TeleportTransition(target, arrival, Vec3.ZERO, 0.0F, entity.getXRot(), e -> arrived(e, target, toMoon));
    }

    /** Teleports a player straight to the Pale Reach (used by /starforged palereach). */
    public static void sendToPaleReach(ServerPlayer player) {
        ServerLevel target = player.level().getServer().getLevel(PALE_REACH);
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
        int y = Mth.clamp(surface, level.getMinY() + 8, level.getMaxY() - 12);
        BlockPos guess = new BlockPos(x, y, z);
        BlockPos existing = MoonGatewayBuilder.find(level, guess, 6, 12);
        if (existing != null) {
            return existing;
        }
        MoonGatewayBuilder.build(level, guess);
        return guess;
    }

    private static void arrived(Entity entity, ServerLevel level, boolean toMoon) {
        level.playSound(null, entity.blockPosition(), MoonSounds.GATEWAY_TRAVEL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), entity.getX(), entity.getY() + 1.0, entity.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
        if (toMoon && entity instanceof ServerPlayer player) {
            SunFx.title(player, Component.translatable("event.starforged.pale_reach.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                Component.translatable("event.starforged.pale_reach.subtitle").withStyle(ChatFormatting.GRAY), 10, 60, 20);
            player.sendSystemMessage(MoonTides.describe(level));
        }
    }
}
