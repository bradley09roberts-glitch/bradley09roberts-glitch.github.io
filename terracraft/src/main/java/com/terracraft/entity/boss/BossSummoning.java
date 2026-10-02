package com.terracraft.entity.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Summons bosses the Terraria way (from items or commands) and announces them. */
public final class BossSummoning {
    /** Where a boss appears relative to the summoner. */
    public enum Arrival {
        /** Drops from the sky near the player (King Slime). */
        FALL,
        /** Flies in from off-screen (Eye of Cthulhu). */
        OFFSCREEN
    }

    private BossSummoning() {}

    public static boolean isAlive(ServerLevel level, EntityType<?> type) {
        for (var entity : level.getAllEntities()) {
            if (entity.getType() == type && entity.isAlive()) {
                return true;
            }
        }
        return false;
    }

    /** @return the boss, or {@code null} if one is already alive or spawning failed */
    public static TerrariaBoss summon(ServerLevel level, ServerPlayer player, EntityType<? extends TerrariaBoss> type, Arrival arrival) {
        if (isAlive(level, type)) {
            return null;
        }
        TerrariaBoss boss = type.create(level, EntitySpawnReason.EVENT);
        if (boss == null) {
            return null;
        }
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        double x;
        double y;
        double z;
        if (arrival == Arrival.FALL) {
            x = player.getX() + Math.cos(angle) * 10;
            z = player.getZ() + Math.sin(angle) * 10;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            y = Math.min(level.getMaxY() - 4, Math.max(ground, player.getY()) + 18);
            if (player.getY() < ground - 6) {
                // underground: appear on the player's level instead of on the surface far above
                y = player.getY() + 3;
            }
        } else {
            x = player.getX() + Math.cos(angle) * 32;
            z = player.getZ() + Math.sin(angle) * 32;
            y = player.getY() + 14;
        }
        boss.snapTo(x, y, z, player.getRandom().nextFloat() * 360.0F, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)), EntitySpawnReason.EVENT, null);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.terracraft.boss.awoken", boss.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        return boss;
    }

    public static boolean inOverworld(ServerPlayer player) {
        return player.level().dimension() == Level.OVERWORLD;
    }
}
