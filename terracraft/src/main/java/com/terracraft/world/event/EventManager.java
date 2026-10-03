package com.terracraft.world.event;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.terracraft.command.TerrariaCommand;
import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.entity.mob.SlimeMob;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.SyncEventPacket;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.MobContent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.util.List;

/**
 * Runs world events. Night events may start at dusk (Blood Moon: 1 in 9 once someone has 120+ max life) and
 * end at dawn; day events may start at dawn (Slime Rain: 1 in 15) and end at dusk or when their kill goal is
 * reached. The active event multiplies spawn rates/caps, enables event spawn rules
 * ({@code "event": "blood_moon"}) and is synced to clients (red Blood Moon fog).
 */
public final class EventManager {
    private EventManager() {}

    public static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), EventManager.class);
        TerrariaCommand.addExtension(EventManager::commands);
    }

    public static @Nullable TerrariaEvent active(MinecraftServer server) {
        String id = EventState.get(server).active();
        return id.isEmpty() ? null : TerrariaEvents.get(id);
    }

    public static boolean isActive(MinecraftServer server, TerrariaEvent event) {
        return event.id().equals(EventState.get(server).active());
    }

    /** Progress bar of the running invasion ("Goblin Army": kills toward its goal). */
    private static final net.minecraft.server.level.ServerBossEvent INVASION_BAR = new net.minecraft.server.level.ServerBossEvent(
        java.util.UUID.fromString("6f0d1c33-4a5e-4d3e-9b6a-2c1f0b7d9e11"), Component.empty(),
        net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);

    public static void start(MinecraftServer server, TerrariaEvent event) {
        EventState state = EventState.get(server);
        state.active = event.id();
        state.kills = 0;
        state.setDirty();
        if (event == TerrariaEvents.BLOOD_MOON) {
            ProgressionManager.set(server, ProgressionFlags.BLOOD_MOON, true);
        }
        server.getPlayerList().broadcastSystemMessage(Component.translatable(event.startKey())
            .withStyle(event == TerrariaEvents.BLOOD_MOON ? ChatFormatting.DARK_RED : event.invasion() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA), false);
        TerraNetwork.sendToAll(new SyncEventPacket(event.id()));
        updateInvasionBar(server);
    }

    public static void stop(MinecraftServer server) {
        EventState state = EventState.get(server);
        TerrariaEvent event = active(server);
        state.active = "";
        state.kills = 0;
        state.setDirty();
        if (event != null) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable(event.endKey()).withStyle(ChatFormatting.GRAY), false);
        }
        TerraNetwork.sendToAll(new SyncEventPacket(""));
        updateInvasionBar(server);
    }

    private static void updateInvasionBar(MinecraftServer server) {
        TerrariaEvent event = active(server);
        if (event == null || !event.invasion()) {
            INVASION_BAR.removeAllPlayers();
            return;
        }
        INVASION_BAR.setName(Component.translatable("event.terracraft." + event.id()));
        INVASION_BAR.setProgress(Math.max(0.0F, 1.0F - EventState.get(server).kills() / (float) event.killGoal()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                INVASION_BAR.addPlayer(player);
            } else {
                INVASION_BAR.removePlayer(player);
            }
        }
    }

    @SubscribeEvent
    static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.server();
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel level = server.overworld();
        EventState state = EventState.get(server);
        boolean day = level.isBrightOutside();
        RandomSource random = level.getRandom();
        TerrariaEvent active = active(server);
        if (day != state.wasDay) {
            state.wasDay = day;
            state.setDirty();
            if (active != null && !active.invasion() && active.night() != !day) {
                stop(server);
                active = null;
            }
            if (active == null && !server.getPlayerList().getPlayers().isEmpty()) {
                boolean goblinsReady = ProgressionManager.has(server, ProgressionFlags.ORB_SMASHED)
                    && server.getPlayerList().getPlayers().stream().anyMatch(p -> p.getMaxHealth() >= 200);
                int goblinChance = ProgressionManager.has(server, ProgressionFlags.GOBLIN_ARMY) ? 30 : 3;
                if (day && goblinsReady && random.nextInt(goblinChance) == 0) {
                    start(server, TerrariaEvents.GOBLIN_ARMY);
                } else if (!day && random.nextInt(9) == 0 && server.getPlayerList().getPlayers().stream().anyMatch(p -> p.getMaxHealth() >= 120)) {
                    start(server, TerrariaEvents.BLOOD_MOON);
                } else if (day && random.nextInt(15) == 0) {
                    start(server, TerrariaEvents.SLIME_RAIN);
                }
            }
        }
        if (active == TerrariaEvents.SLIME_RAIN) {
            rainSlimes(level, random);
        }
        if (active != null && active.invasion()) {
            updateInvasionBar(server);
        }
    }

    /** Slime Rain: slimes drop out of the sky around every player on the surface. */
    private static void rainSlimes(ServerLevel level, RandomSource random) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !level.canSeeSky(player.blockPosition())) {
                continue;
            }
            List<SlimeMob> nearby = level.getEntitiesOfClass(SlimeMob.class, player.getBoundingBox().inflate(48));
            if (nearby.size() >= 10 || random.nextInt(3) != 0) {
                continue;
            }
            int x = Mth.floor(player.getX()) + random.nextInt(61) - 30;
            int z = Mth.floor(player.getZ()) + random.nextInt(61) - 30;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 25 + random.nextInt(10);
            EntityType<? extends TerrariaMob> type = switch (random.nextInt(10)) {
                case 0, 1 -> MobContent.PURPLE_SLIME.get();
                case 2 -> MobContent.RED_SLIME.get();
                case 3, 4, 5 -> MobContent.GREEN_SLIME.get();
                default -> MobContent.BLUE_SLIME.get();
            };
            TerrariaMob slime = type.create(level, EntitySpawnReason.EVENT);
            if (slime != null) {
                slime.snapTo(x + 0.5, Math.min(y, level.getMaxY() - 2), z + 0.5, random.nextFloat() * 360.0F, 0.0F);
                slime.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.EVENT, null);
                level.addFreshEntity(slime);
            }
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        TerrariaEvent running = active(level.getServer());
        if (running != null && running.invasion() && event.getSource().getEntity() instanceof ServerPlayer
            && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getPath().startsWith(running.memberPrefix())) {
            EventState state = EventState.get(level.getServer());
            state.kills++;
            state.setDirty();
            updateInvasionBar(level.getServer());
            if (state.kills >= running.killGoal()) {
                stop(level.getServer());
                if (running == TerrariaEvents.GOBLIN_ARMY) {
                    ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.GOBLIN_ARMY);
                }
            }
            return;
        }
        if (!(event.getEntity() instanceof SlimeMob)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (!isActive(server, TerrariaEvents.SLIME_RAIN) || !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EventState state = EventState.get(server);
        state.kills++;
        state.setDirty();
        boolean kingDefeated = ProgressionManager.has(server, ProgressionFlags.KING_SLIME);
        int goal = kingDefeated ? TerrariaEvents.SLIME_RAIN.killGoal() / 2 : TerrariaEvents.SLIME_RAIN.killGoal();
        if (state.kills >= goal) {
            stop(server);
            if (!kingDefeated) {
                BossSummoning.summon(level, player, MobContent.KING_SLIME.get(), BossSummoning.Arrival.FALL);
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TerraNetwork.sendToPlayer(player, new SyncEventPacket(EventState.get(player.level().getServer()).active()));
        }
    }

    private static void commands(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> start = Commands.literal("start");
        TerrariaEvents.all().forEach((id, event) -> start.then(Commands.literal(id).executes(ctx -> {
            start(ctx.getSource().getServer(), event);
            return 1;
        })));
        root.then(Commands.literal("event")
            .then(start)
            .then(Commands.literal("stop").executes(ctx -> {
                stop(ctx.getSource().getServer());
                return 1;
            }))
            .then(Commands.literal("status").executes(ctx -> {
                EventState state = EventState.get(ctx.getSource().getServer());
                ctx.getSource().sendSuccess(() -> Component.literal(state.active().isEmpty() ? "No event is running."
                    : "Active event: " + state.active() + " (kills " + state.kills() + ")"), false);
                return state.active().isEmpty() ? 0 : 1;
            })));
    }
}
