package com.starforged.sun.world;

import com.starforged.Starforged;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.sun.SunSounds;
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
 * Travel between the Overworld and the Sunlands. Gateways are linked by coordinates: stepping into one at x/z
 * takes you to x/z on the other side, where a matching gateway is raised if there isn't one already.
 */
public final class SunlandsTravel {
    public static final ResourceKey<Level> SUNLANDS = ResourceKey.create(Registries.DIMENSION, Starforged.id("sunlands"));

    private SunlandsTravel() {
    }

    /**
     * The Sunlands sit above the weather. Every dimension shares the Overworld's weather, so without this a storm back
     * home would grey out the golden sky (and could even call lightning down on the temples).
     */
    public static void suppressWeather(Level level) {
        if (level.dimension() == SUNLANDS && (level.getRainLevel(1.0F) > 0.0F || level.getThunderLevel(1.0F) > 0.0F)) {
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
        }
    }

    public static boolean isSunlands(Level level) {
        return level.dimension() == SUNLANDS;
    }

    public static @Nullable TeleportTransition destination(ServerLevel current, Entity entity, BlockPos entry) {
        ResourceKey<Level> targetKey = current.dimension() == SUNLANDS ? Level.OVERWORLD : SUNLANDS;
        ServerLevel target = current.getServer().getLevel(targetKey);
        if (target == null) {
            return null;
        }
        BlockPos gate = gatewayAt(target, entry.getX(), entry.getZ());
        Vec3 arrival = new Vec3(gate.getX() + 0.5, gate.getY(), gate.getZ() + 2.5);
        boolean toSunlands = targetKey == SUNLANDS;
        return new TeleportTransition(target, arrival, Vec3.ZERO, 0.0F, entity.getXRot(), e -> arrived(e, target, toSunlands));
    }

    /** Teleports an entity straight to the Sunlands (used by /starforged sunlands). */
    public static void sendToSunlands(ServerPlayer player) {
        ServerLevel target = player.level().getServer().getLevel(SUNLANDS);
        if (target == null) {
            return;
        }
        BlockPos gate = gatewayAt(target, player.getBlockX(), player.getBlockZ());
        player.teleport(new TeleportTransition(target, new Vec3(gate.getX() + 0.5, gate.getY(), gate.getZ() + 2.5), Vec3.ZERO, 0.0F, 0.0F,
            e -> arrived(e, target, true)));
    }

    /** Returns the middle of the gateway pool at x/z in {@code level}, building one on the surface if needed. */
    public static BlockPos gatewayAt(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int y = Mth.clamp(surface, level.getMinY() + 8, level.getMaxY() - 12);
        BlockPos guess = new BlockPos(x, y, z);
        BlockPos existing = GatewayBuilder.find(level, guess, 6, 12);
        if (existing != null) {
            return existing;
        }
        GatewayBuilder.build(level, guess);
        return guess;
    }

    private static void arrived(Entity entity, ServerLevel level, boolean toSunlands) {
        level.playSound(null, entity.blockPosition(), SunSounds.GATEWAY_TRAVEL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ModParticles.SOLAR_SPARK.get(), entity.getX(), entity.getY() + 1.0, entity.getZ(), 60, 0.6, 1.0, 0.6, 0.15);
        if (toSunlands && entity instanceof ServerPlayer player) {
            SunFx.title(player, Component.translatable("event.starforged.sunlands.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable("event.starforged.sunlands.subtitle").withStyle(ChatFormatting.YELLOW), 10, 60, 20);
        }
    }
}
