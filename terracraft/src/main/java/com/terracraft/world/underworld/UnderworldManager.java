package com.terracraft.world.underworld;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.entity.boss.WallOfFlesh;
import com.terracraft.npc.NpcManager;
import com.terracraft.npc.TownNpc;
import com.terracraft.npc.TownNpcs;
import com.terracraft.registry.content.MobContent;
import com.terracraft.registry.content.UnderworldContent;
import com.terracraft.world.TerrariaLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;

/**
 * Wall of Flesh summoning, Terraria style: a Guide Voodoo Doll thrown into lava in the Underworld while the
 * Guide is alive kills the Guide and awakens the Wall of Flesh at the edge of the screen, crawling toward the
 * thrower. Without a living Guide the doll just floats there.
 */
public final class UnderworldManager {
    private UnderworldManager() {}

    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(UnderworldManager::onServerTick);
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.server();
        if (server.getTickCount() % 10 != 3) {
            return;
        }
        ServerLevel level = server.overworld();
        for (ServerPlayer player : level.players()) {
            if (player.getY() > TerrariaLayer.UNDERWORLD_START + 8) {
                continue;
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(48),
                e -> e.getItem().is(UnderworldContent.GUIDE_VOODOO_DOLL.get()) && e.isInLava())) {
                if (tryAwaken(level, player, item)) {
                    return;
                }
            }
        }
    }

    private static boolean tryAwaken(ServerLevel level, ServerPlayer player, ItemEntity doll) {
        if (BossSummoning.isAlive(level, MobContent.WALL_OF_FLESH.get())) {
            return false;
        }
        var guideId = NpcManager.livingEntity(level.getServer(), TownNpcs.GUIDE.id());
        if (guideId.isEmpty() || !(level.getEntity(guideId.get()) instanceof TownNpc guide)) {
            return false;   // the Guide must be alive (and loaded) for the doll to work
        }
        doll.discard();
        guide.hurtServer(level, level.damageSources().lava(), 10_000.0F);
        WallOfFlesh wall = MobContent.WALL_OF_FLESH.get().create(level, EntitySpawnReason.EVENT);
        if (wall == null) {
            return true;
        }
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
        double x = player.getX() - facing.getStepX() * 32;
        double z = player.getZ() - facing.getStepZ() * 32;
        wall.snapTo(x, player.getY(), z, facing.toYRot(), 0.0F);
        wall.setFacing(facing);
        wall.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.EVENT, null);
        wall.setTarget(player);
        level.addFreshEntity(wall);
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.terracraft.boss.awoken", wall.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
        return true;
    }
}
