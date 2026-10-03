package com.terracraft.world.dungeon;

import com.terracraft.TerraCraft;
import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.entity.boss.Skeletron;
import com.terracraft.entity.mob.DungeonGuardian;
import com.terracraft.npc.NpcManager;
import com.terracraft.npc.NpcWorldData;
import com.terracraft.npc.TownNpc;
import com.terracraft.npc.TownNpcs;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.MobContent;
import com.terracraft.world.evil.EvilBiomeFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;


/**
 * Runs the Dungeon while the world is played:
 * <ul>
 *     <li>the Old Man waits outside the entrance until Skeletron is defeated (he comes back if a fight is lost);</li>
 *     <li>cursing him at night summons Skeletron;</li>
 *     <li>before Skeletron is beaten, entering the Dungeon brings the Dungeon Guardian;</li>
 *     <li>{@code /terraria worldgen dungeon} tells where the Dungeon is.</li>
 * </ul>
 */
public final class DungeonManager {
    private static long cachedSeed = Long.MIN_VALUE;
    private static DungeonLayout cached;

    private DungeonManager() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(DungeonManager.class);
        com.terracraft.command.TerrariaCommand.addExtension(root -> root.then(Commands.literal("worldgen").then(Commands.literal("dungeon")
            .executes(ctx -> {
                DungeonLayout layout = layout(ctx.getSource().getServer().overworld());
                BlockPos entrance = layout.entrance();
                ctx.getSource().sendSuccess(() -> Component.literal(String.format("Dungeon entrance at %d %d %d (%s bricks, %d rooms and halls), door faces %s",
                    entrance.getX(), entrance.getY(), entrance.getZ(), layout.brick().name().toLowerCase(java.util.Locale.ROOT),
                    layout.boxes().size(), layout.doorFacing().getSerializedName())).withStyle(ChatFormatting.AQUA), false);
                return 1;
            }))));
    }

    /** The world's Dungeon (computed once per seed; safe to call from world generation threads). */
    public static synchronized DungeonLayout layout(ServerLevel level) {
        long seed = level.getSeed();
        if (seed != cachedSeed || cached == null) {
            int[] spot = DungeonLayout.location(seed, EvilBiomeFeature.landTest(level));
            var generator = level.getChunkSource().getGenerator();
            int surface = generator.getBaseHeight(spot[0], spot[1], Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
            surface = Math.max(surface, generator.getSeaLevel() + 1);
            cached = DungeonLayout.build(seed, spot[0], spot[1], surface, level.getMinY());
            cachedSeed = seed;
            TerraCraft.LOGGER.info("Dungeon for seed {} at {} ({} boxes)", seed, cached.entrance(), cached.boxes().size());
        }
        return cached;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (DungeonManager.class) {
            cached = null;
            cachedSeed = Long.MIN_VALUE;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        boolean cursed = !ProgressionManager.has(server, ProgressionFlags.SKELETRON);
        if (cursed) {
            for (ServerPlayer player : level.players()) {
                if (guardianShouldHunt(level, player) && !player.isCreative() && !player.isSpectator() && guardianFor(level, player) == null) {
                    summonGuardian(level, player);
                }
            }
        }
        if (server.getTickCount() % 100 == 7) {
            maintainOldMan(level, cursed);
        }
    }

    /** True while the Dungeon is still cursed and the player is inside it. */
    public static boolean guardianShouldHunt(ServerLevel level, Player player) {
        return player.level() == level && level.dimension() == net.minecraft.world.level.Level.OVERWORLD
            && !ProgressionManager.has(level.getServer(), ProgressionFlags.SKELETRON) && layout(level).isInside(player.blockPosition());
    }

    private static @Nullable DungeonGuardian guardianFor(ServerLevel level, Player player) {
        for (DungeonGuardian guardian : level.getEntitiesOfClass(DungeonGuardian.class, player.getBoundingBox().inflate(160))) {
            if (player.getUUID().equals(guardian.victim())) {
                return guardian;
            }
        }
        return null;
    }

    private static void summonGuardian(ServerLevel level, ServerPlayer player) {
        DungeonGuardian guardian = MobContent.DUNGEON_GUARDIAN.get().create(level, EntitySpawnReason.EVENT);
        if (guardian == null) {
            return;
        }
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        guardian.snapTo(player.getX() + Math.cos(angle) * 28, player.getY() + 6, player.getZ() + Math.sin(angle) * 28, 0.0F, 0.0F);
        guardian.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.EVENT, null);
        guardian.hunt(player);
        level.addFreshEntity(guardian);
    }

    /** Keeps the Old Man at the entrance while the curse lasts; removes him once Skeletron has fallen. */
    private static void maintainOldMan(ServerLevel level, boolean cursed) {
        MinecraftServer server = level.getServer();
        NpcWorldData data = NpcWorldData.get(server);
        boolean present = NpcManager.isPresent(server, TownNpcs.OLD_MAN.id());
        if (!cursed) {
            if (present) {
                NpcManager.livingEntity(server, TownNpcs.OLD_MAN.id()).map(level::getEntity).ifPresent(e -> e.discard());
                NpcManager.forget(server, TownNpcs.OLD_MAN.id());
            }
            return;
        }
        if (present || BossSummoning.isAlive(level, MobContent.SKELETRON.get())) {
            return;
        }
        BlockPos spot = layout(level).oldManSpot();
        if (!level.isLoaded(spot) || level.getNearestPlayer(spot.getX(), spot.getY(), spot.getZ(), 128, false) == null) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
        NpcManager.spawn(level, data, TownNpcs.OLD_MAN, new BlockPos(spot.getX(), y, spot.getZ()), "", false);
    }

    /** The Old Man's "Curse" button: summons Skeletron at night. */
    public static void curse(ServerPlayer player, TownNpc oldMan) {
        ServerLevel level = player.level();
        if (!level.isDarkOutside()) {
            NpcManager.openChat(player, oldMan, "npc.terracraft.old_man.day", "");
            return;
        }
        if (BossSummoning.isAlive(level, MobContent.SKELETRON.get())) {
            return;
        }
        Skeletron boss = MobContent.SKELETRON.get().create(level, EntitySpawnReason.EVENT);
        if (boss == null) {
            return;
        }
        boss.snapTo(oldMan.getX(), oldMan.getY() + 3, oldMan.getZ(), oldMan.getYRot(), 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(oldMan.blockPosition()), EntitySpawnReason.EVENT, null);
        boss.setTarget(player);
        level.addFreshEntity(boss);
        oldMan.discard();
        NpcManager.forget(level.getServer(), TownNpcs.OLD_MAN.id());
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.terracraft.boss.awoken", boss.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
    }

    /** Direction and distance to the Dungeon in words, for the Guide's hint. */
    public static String directions(ServerLevel level, BlockPos from) {
        BlockPos entrance = layout(level).entrance();
        int dx = entrance.getX() - from.getX();
        int dz = entrance.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        String direction = names[(int) Math.floorMod(Math.round(angle / 45.0), 8)];
        int distance = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
        return direction + ", about " + (distance / 50 * 50) + " blocks away";
    }
}
