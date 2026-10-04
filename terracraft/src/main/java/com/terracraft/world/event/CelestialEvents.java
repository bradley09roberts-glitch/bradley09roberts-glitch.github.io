package com.terracraft.world.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terracraft.TerraCraft;
import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.entity.boss.CelestialPillar;
import com.terracraft.entity.boss.LunaticCultist;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.RegistryObject;
import com.terracraft.registry.content.MobContent;
import com.terracraft.world.dungeon.DungeonManager;
import com.terracraft.world.spawn.SafeZones;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The end of the game:
 * <ul>
 *     <li>After Golem, four cultists pray outside the Dungeon (whenever no celestial event is under way); disturbing
 *     them brings the Lunatic Cultist.</li>
 *     <li>His defeat brings down the four Celestial Pillars, far out in four directions from spawn. Each is guarded by
 *     its own creatures, and killing those near it wears its shield down.</li>
 *     <li>When the last pillar falls, Moon Lord comes after a minute ("Impending doom approaches...").</li>
 *     <li>{@code /terraria celestial start|stop|status|tp <kind>}</li>
 * </ul>
 */
public final class CelestialEvents {
    /** Pillars fought at once and their sites (x, z per kind; empty until the Cultist falls). */
    public static final class State extends SavedData {
        private static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().optionalFieldOf("sites", List.of()).forGetter(s -> s.sites),
            Codec.INT.optionalFieldOf("doom", 0).forGetter(s -> s.doom),
            Codec.INT.optionalFieldOf("down", 0).forGetter(s -> s.down)
        ).apply(i, State::new));
        public static final SavedDataType<State> TYPE = new SavedDataType<>(TerraCraft.id("celestial_events"), State::new, CODEC, null);

        private final List<Integer> sites;
        /** Ticks left until Moon Lord arrives (0 = not coming). */
        private int doom;
        /** Pillars of the current event already destroyed (one bit per kind). */
        private int down;

        public State() {
            this(List.of(), 0, 0);
        }

        private State(List<Integer> sites, int doom, int down) {
            this.sites = new ArrayList<>(sites);
            this.doom = doom;
            this.down = down;
        }

        boolean isDown(CelestialPillar.Kind kind) {
            return (down & (1 << kind.ordinal())) != 0;
        }

        public static State get(MinecraftServer server) {
            return server.getDataStorage().computeIfAbsent(TYPE);
        }

        boolean active() {
            return sites.size() == 8;
        }

        BlockPos site(CelestialPillar.Kind kind) {
            return new BlockPos(sites.get(kind.ordinal() * 2), 0, sites.get(kind.ordinal() * 2 + 1));
        }
    }

    private record Guard(RegistryObject<? extends EntityType<? extends TerrariaMob>> type, boolean flying) {}

    private static final int DOOM_TICKS = 1200;
    private static final int PILLAR_RANGE = 80;

    private CelestialEvents() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(CelestialEvents.class);
        com.terracraft.command.TerrariaCommand.addExtension(root -> root.then(Commands.literal("celestial").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("start").executes(ctx -> {
                startPillars(ctx.getSource().getServer().overworld());
                return 1;
            }))
            .then(Commands.literal("stop").executes(ctx -> {
                State state = State.get(ctx.getSource().getServer());
                state.sites.clear();
                state.doom = 0;
                state.down = 0;
                state.setDirty();
                ctx.getSource().sendSuccess(() -> Component.literal("Celestial event stopped."), true);
                return 1;
            }))
            .then(Commands.literal("status").executes(ctx -> {
                MinecraftServer server = ctx.getSource().getServer();
                State state = State.get(server);
                StringBuilder text = new StringBuilder(state.active() ? "Celestial pillars:" : "The pillars have not come.");
                if (state.active()) {
                    for (CelestialPillar.Kind kind : CelestialPillar.Kind.values()) {
                        BlockPos site = state.site(kind);
                        text.append(String.format(" %s [%d %d] %s;", kind.id(), site.getX(), site.getZ(),
                            state.isDown(kind) ? "destroyed" : "standing"));
                    }
                }
                if (state.doom > 0) {
                    text.append(" Moon Lord in ").append(state.doom / 20).append("s");
                }
                ctx.getSource().sendSuccess(() -> Component.literal(text.toString()).withStyle(ChatFormatting.AQUA), false);
                return 1;
            }))
            .then(Commands.literal("tp").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(CelestialPillar.Kind.values()).map(CelestialPillar.Kind::id), b))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    State state = State.get(ctx.getSource().getServer());
                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind");
                    if (!state.active()) {
                        return 0;
                    }
                    for (CelestialPillar.Kind kind : CelestialPillar.Kind.values()) {
                        if (kind.id().equals(id)) {
                            BlockPos site = state.site(kind);
                            ServerLevel level = ctx.getSource().getServer().overworld();
                            level.getChunk(site.getX() >> 4, (site.getZ() + 24) >> 4);
                            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getX(), site.getZ() + 24);
                            player.teleportTo(level, site.getX() + 0.5, y, site.getZ() + 24.5, java.util.Set.of(), 180.0F, 0.0F, true);
                            return 1;
                        }
                    }
                    return 0;
                })))));
    }

    private static List<Guard> guards(CelestialPillar.Kind kind) {
        return switch (kind) {
            case SOLAR -> List.of(new Guard(MobContent.SELENIAN, false), new Guard(MobContent.SROLLER, false), new Guard(MobContent.CORITE, true));
            case VORTEX -> List.of(new Guard(MobContent.STORM_DIVER, false), new Guard(MobContent.ALIEN_HORNET, true), new Guard(MobContent.VORTEXIAN, false));
            case NEBULA -> List.of(new Guard(MobContent.NEBULA_FLOATER, true), new Guard(MobContent.BRAIN_SUCKLER, true), new Guard(MobContent.PREDICTOR, false));
            case STARDUST -> List.of(new Guard(MobContent.STAR_CELL, true), new Guard(MobContent.FLOW_INVADER, true), new Guard(MobContent.TWINKLE_POPPER, false));
        };
    }

    private static RegistryObject<? extends EntityType<CelestialPillar>> pillarType(CelestialPillar.Kind kind) {
        return switch (kind) {
            case SOLAR -> MobContent.SOLAR_PILLAR;
            case VORTEX -> MobContent.VORTEX_PILLAR;
            case NEBULA -> MobContent.NEBULA_PILLAR;
            case STARDUST -> MobContent.STARDUST_PILLAR;
        };
    }

    /** The Lunatic Cultist has fallen: pick the pillar sites and announce them. */
    public static void startPillars(ServerLevel level) {
        MinecraftServer server = level.getServer();
        State state = State.get(server);
        state.sites.clear();
        BlockPos spawn = level.getRespawnData().pos();
        RandomSource random = level.getRandom();
        double offset = random.nextDouble() * Math.PI * 2;
        for (CelestialPillar.Kind kind : CelestialPillar.Kind.values()) {
            double angle = offset + kind.ordinal() * Math.PI / 2 + (random.nextDouble() - 0.5) * 0.6;
            double distance = 300 + random.nextInt(300);
            state.sites.add(Mth.floor(spawn.getX() + Math.cos(angle) * distance));
            state.sites.add(Mth.floor(spawn.getZ() + Math.sin(angle) * distance));
        }
        state.doom = 0;
        state.down = 0;
        state.setDirty();
        server.getPlayerList().broadcastSystemMessage(Component.translatable("event.terracraft.pillars.start").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
    }

    /** A devotee was struck: they all vanish and the Lunatic Cultist rises. */
    public static void disturbDevotees(ServerLevel level, ServerPlayer player, LunaticCultist.Devotee struck) {
        if (BossSummoning.isAlive(level, MobContent.LUNATIC_CULTIST.get())) {
            return;
        }
        BlockPos at = struck.blockPosition();
        for (LunaticCultist.Devotee devotee : level.getEntitiesOfClass(LunaticCultist.Devotee.class, struck.getBoundingBox().inflate(32))) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, devotee.getX(), devotee.getY() + 1, devotee.getZ(), 30, 0.3, 0.8, 0.3, 0.5);
            devotee.discard();
        }
        BossSummoning.summonAt(level, player, MobContent.LUNATIC_CULTIST.get(), at.getX() + 0.5, at.getY() + 4, at.getZ() + 0.5);
    }

    /** A pillar fell: once all four are down, Moon Lord is on his way. */
    public static void pillarDestroyed(ServerLevel level, CelestialPillar.Kind kind) {
        MinecraftServer server = level.getServer();
        State state = State.get(server);
        state.down |= 1 << kind.ordinal();
        state.setDirty();
        if (!state.active() || state.down != 0b1111) {
            return;
        }
        state.sites.clear();
        state.down = 0;
        state.doom = DOOM_TICKS;
        state.setDirty();
        server.getPlayerList().broadcastSystemMessage(Component.translatable("event.terracraft.moon_lord.doom").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD), false);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        State state = State.get(server);
        if (state.doom > 0) {
            doom(level, state);
        }
        if (server.getTickCount() % 20 != 11) {
            return;
        }
        if (server.getTickCount() % 100 == 11) {
            maintainDevotees(level);
        }
        if (state.active()) {
            for (CelestialPillar.Kind kind : CelestialPillar.Kind.values()) {
                if (!state.isDown(kind)) {
                    maintainPillar(level, state, kind);
                }
            }
        }
    }

    private static void doom(ServerLevel level, State state) {
        state.doom--;
        if (state.doom % 400 == 0 && state.doom > 0) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("event.terracraft.moon_lord.doom").withStyle(ChatFormatting.DARK_AQUA), false);
        }
        if (state.doom > 0) {
            if (state.doom % 20 == 0) {
                state.setDirty();
            }
            return;
        }
        state.setDirty();
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.isEmpty() || BossSummoning.isAlive(level, MobContent.MOON_LORD.get())) {
            return;
        }
        ServerPlayer player = players.get(level.getRandom().nextInt(players.size()));
        BossSummoning.summonAt(level, player, MobContent.MOON_LORD.get(), player.getX(), player.getY() + 6, player.getZ() + 28);
    }

    /** Four cultists praying outside the Dungeon after Golem, whenever no celestial event is under way. */
    private static void maintainDevotees(ServerLevel level) {
        MinecraftServer server = level.getServer();
        State state = State.get(server);
        if (!ProgressionManager.has(server, ProgressionFlags.GOLEM) || state.active() || state.doom > 0
            || BossSummoning.isAlive(level, MobContent.LUNATIC_CULTIST.get()) || BossSummoning.isAlive(level, MobContent.MOON_LORD.get())) {
            return;
        }
        var layout = DungeonManager.layout(level);
        BlockPos entrance = layout.entrance();
        BlockPos spot = layout.oldManSpot();
        int cx = (entrance.getX() + spot.getX() * 3) / 4 + (spot.getX() - entrance.getX()) / 2;
        int cz = (entrance.getZ() + spot.getZ() * 3) / 4 + (spot.getZ() - entrance.getZ()) / 2;
        BlockPos center = new BlockPos(cx, spot.getY(), cz);
        if (!level.isLoaded(center) || level.getNearestPlayer(cx, center.getY(), cz, 128, false) == null
            || !level.getEntitiesOfClass(LunaticCultist.Devotee.class, new net.minecraft.world.phys.AABB(center).inflate(24)).isEmpty()) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            int x = Mth.floor(cx + Math.cos(a) * 2.5);
            int z = Mth.floor(cz + Math.sin(a) * 2.5);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            LunaticCultist.Devotee devotee = MobContent.CULTIST_DEVOTEE.get().create(level, EntitySpawnReason.EVENT);
            if (devotee == null) {
                return;
            }
            // they face the middle of their circle
            float yaw = (float) (Mth.atan2(cz + 0.5 - (z + 0.5), cx + 0.5 - (x + 0.5)) * Mth.RAD_TO_DEG) - 90.0F;
            devotee.snapTo(x + 0.5, y, z + 0.5, yaw, 0.0F);
            devotee.setYHeadRot(yaw);
            devotee.yBodyRot = yaw;
            devotee.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.EVENT, null);
            devotee.setPersistenceRequired();
            level.addFreshEntity(devotee);
        }
    }

    /** Puts a standing pillar on its site once a player comes near, and keeps its guards coming. */
    private static void maintainPillar(ServerLevel level, State state, CelestialPillar.Kind kind) {
        BlockPos site = state.site(kind);
        ServerPlayer near = null;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(site.getX(), player.getY(), site.getZ()) < 160 * 160) {
                near = player;
                break;
            }
        }
        if (near == null || !level.isLoaded(site)) {
            return;
        }
        List<CelestialPillar> found = level.getEntitiesOfClass(CelestialPillar.class,
            new net.minecraft.world.phys.AABB(site.getX() - 16, level.getMinY(), site.getZ() - 16, site.getX() + 16, level.getMaxY(), site.getZ() + 16),
            p -> p.kind() == kind);
        CelestialPillar pillar;
        if (found.isEmpty()) {
            pillar = pillarType(kind).get().create(level, EntitySpawnReason.EVENT);
            if (pillar == null) {
                return;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, site.getX(), site.getZ());
            pillar.snapTo(site.getX() + 0.5, y, site.getZ() + 0.5, 0.0F, 0.0F);
            pillar.finalizeSpawn(level, level.getCurrentDifficultyAt(site), EntitySpawnReason.EVENT, null);
            level.addFreshEntity(pillar);
        } else {
            pillar = found.get(0);
        }
        if (pillar.shield() <= 0) {
            return;
        }
        RandomSource random = level.getRandom();
        List<Guard> guards = guards(kind);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(pillar) > PILLAR_RANGE * PILLAR_RANGE) {
                continue;
            }
            int around = level.getEntitiesOfClass(TerrariaMob.class, player.getBoundingBox().inflate(48), m -> isGuard(m, kind)).size();
            for (int n = 0; n < 2 && around < 10; n++, around++) {
                Guard guard = guards.get(random.nextInt(guards.size()));
                TerrariaMob mob = guard.type().get().create(level, EntitySpawnReason.EVENT);
                if (mob == null) {
                    break;
                }
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 16 + random.nextDouble() * 12;
                int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
                int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + (guard.flying() ? 6 : 0);
                if (SafeZones.isSafe(level.getServer(), x, y, z)) {
                    break;
                }
                mob.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(player.blockPosition()), EntitySpawnReason.EVENT, null);
                mob.setTarget(player);
                level.addFreshEntity(mob);
            }
        }
    }

    private static boolean isGuard(net.minecraft.world.entity.Entity entity, CelestialPillar.Kind kind) {
        for (Guard guard : guards(kind)) {
            if (entity.getType() == guard.type().get()) {
                return true;
            }
        }
        return false;
    }

    /** Each guard killed by a player near its pillar wears the shield down. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }
        for (CelestialPillar.Kind kind : CelestialPillar.Kind.values()) {
            if (isGuard(event.getEntity(), kind)) {
                for (CelestialPillar pillar : level.getEntitiesOfClass(CelestialPillar.class, event.getEntity().getBoundingBox().inflate(PILLAR_RANGE * 1.5),
                    p -> p.kind() == kind && p.shield() > 0)) {
                    pillar.weaken(level);
                    return;
                }
                return;
            }
        }
    }
}
