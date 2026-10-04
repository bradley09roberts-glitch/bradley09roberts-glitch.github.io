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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(EventManager.class);
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
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
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
                // pirates may sail in on their own once an altar has been smashed in Hardmode
                boolean piratesReady = ProgressionManager.isHardmode(server) && ProgressionManager.has(server, ProgressionFlags.ALTAR_SMASHED);
                if (day && goblinsReady && random.nextInt(goblinChance) == 0) {
                    start(server, TerrariaEvents.GOBLIN_ARMY);
                } else if (day && piratesReady && random.nextInt(ProgressionManager.has(server, ProgressionFlags.PIRATES) ? 60 : 20) == 0) {
                    start(server, TerrariaEvents.PIRATE_INVASION);
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
        if (active == TerrariaEvents.PIRATE_INVASION) {
            maybeSendDutchman(level, random);
        }
    }

    /**
     * Once a third of the pirates are beaten, the Flying Dutchman may appear high above a player on the surface
     * (one at a time).
     */
    private static void maybeSendDutchman(ServerLevel level, RandomSource random) {
        if (EventState.get(level.getServer()).kills() < TerrariaEvents.PIRATE_INVASION.killGoal() / 3 || random.nextInt(30) != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !level.canSeeSky(player.blockPosition())
                || !level.getEntitiesOfClass(com.terracraft.entity.mob.FlyingDutchman.class, player.getBoundingBox().inflate(160)).isEmpty()) {
                continue;
            }
            TerrariaMob ship = MobContent.FLYING_DUTCHMAN.get().create(level, EntitySpawnReason.EVENT);
            if (ship != null) {
                double side = random.nextBoolean() ? 1 : -1;
                ship.snapTo(player.getX() + side * 40, player.getY() + 18, player.getZ() + random.nextInt(11) - 5, side > 0 ? 90.0F : -90.0F, 0.0F);
                ship.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.EVENT, null);
                level.addFreshEntity(ship);
                level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("event.terracraft.pirate_invasion.dutchman")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
            return;
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
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        TerrariaEvent running = active(level.getServer());
        if (running != null && running.invasion() && event.getSource().getEntity() instanceof ServerPlayer
            && running.isMember(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getPath())) {
            EventState state = EventState.get(level.getServer());
            // the big ones count for more (the Flying Dutchman 10, the Pirate Captain 5)
            state.kills += event.getEntity() instanceof com.terracraft.entity.mob.FlyingDutchman ? 10
                : event.getEntity().getType() == MobContent.PIRATE_CAPTAIN.get() ? 5 : 1;
            state.setDirty();
            updateInvasionBar(level.getServer());
            if (state.kills >= running.killGoal()) {
                stop(level.getServer());
                if (running == TerrariaEvents.GOBLIN_ARMY) {
                    ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.GOBLIN_ARMY);
                } else if (running == TerrariaEvents.PIRATE_INVASION) {
                    ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.PIRATES);
                } else if (running == TerrariaEvents.FROST_LEGION) {
                    ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.FROST_LEGION);
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

    /**
     * Presents: enemies killed by a player now and then drop a Present - 1 in 13 between December 15 and January 1
     * (Terraria's Christmas), 1 in 40 in snowy biomes in Hardmode, otherwise 1 in 150 in Hardmode.
     */
    @SubscribeEvent
    public static void onPresentDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer)
            || !(event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy)) {
            return;
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        boolean christmas = today.getMonthValue() == 12 && today.getDayOfMonth() >= 15 || today.getMonthValue() == 1 && today.getDayOfMonth() == 1;
        boolean hardmode = ProgressionManager.isHardmode(level.getServer());
        boolean snowy = level.getBiome(event.getEntity().blockPosition()).value().coldEnoughToSnow(event.getEntity().blockPosition(), level.getSeaLevel());
        int chance = christmas ? 13 : !hardmode ? 0 : snowy ? 40 : 150;
        if (chance > 0 && level.getRandom().nextInt(chance) == 0) {
            var entity = event.getEntity();
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                new net.minecraft.world.item.ItemStack(com.terracraft.registry.content.FrostLegionContent.PRESENT.get())));
        }
    }

    /** Hardmode enemies killed by a player at the ocean or a beach sometimes drop a Pirate Map (1 in 17, like Terraria). */
    @SubscribeEvent
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer)
            || !(event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy) || !ProgressionManager.isHardmode(level.getServer())
            || isActive(level.getServer(), TerrariaEvents.PIRATE_INVASION)) {
            return;
        }
        var biome = level.getBiome(event.getEntity().blockPosition());
        if ((biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)) && level.getRandom().nextInt(17) == 0) {
            var entity = event.getEntity();
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                new net.minecraft.world.item.ItemStack(com.terracraft.registry.content.PirateContent.PIRATE_MAP.get())));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
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
