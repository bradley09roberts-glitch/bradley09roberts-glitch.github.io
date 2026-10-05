package com.squidgame.tournament;

import com.squidgame.SquidConfig;
import com.squidgame.SquidGameMod;
import com.squidgame.build.ArenaBuilders;
import com.squidgame.build.ArenaId;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import com.squidgame.core.ContestantStatus;
import com.squidgame.core.Difficulty;
import com.squidgame.core.GameKind;
import com.squidgame.core.Phase;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.ai.WaitingBehavior;
import com.squidgame.game.EliminationCause;
import com.squidgame.game.GameContext;
import com.squidgame.game.GameRegistry;
import com.squidgame.game.GameResult;
import com.squidgame.game.MiniGame;
import com.squidgame.net.FadePayload;
import com.squidgame.net.ModNetwork;
import com.squidgame.net.NumbersPayload;
import com.squidgame.net.ResultsPayload;
import com.squidgame.registry.ModItems;
import com.squidgame.registry.ModSounds;
import com.squidgame.world.ArenaData;
import com.squidgame.world.ArenaWorld;
import com.squidgame.world.BuildManager;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide controller of the tournament: lifecycle, phase state machine, eliminations, player handling
 * (disconnects, late joins, respawns), persistence and cleanup. Everything runs on the server thread.
 *
 * <p>Phases: LOBBY -> REGISTRATION -> [INSTRUCTIONS -> COUNTDOWN -> GAME -> ELIMINATIONS -> RESULTS -> TRANSITION]*
 * -> FINAL_WINNER -> RESTART (back to LOBBY, or REGISTRATION again when auto-restart is on).
 */
public final class TournamentManager {
    private static TournamentManager current;

    public static final long PRIZE_PER_ELIMINATION = 100_000_000L;

    private final MinecraftServer server;
    private final BuildManager builds;
    private final PlayerStore store = new PlayerStore();
    private final DoorService doors = new DoorService();
    private final ChunkLoader chunks;
    private final HudService hud;
    private final Restrictions restrictions = new Restrictions();
    private final Set<UUID> spectators = new HashSet<>();
    private final List<PendingSpectate> pendingSpectate = new ArrayList<>();
    private final List<BodyRemoval> bodyRemovals = new ArrayList<>();
    private Tournament t;
    private TournamentData data;
    private long tickCounter;
    private boolean awaitingBuild;
    private record Delayed(long due, Runnable action) {
    }

    private final List<Delayed> delayed = new ArrayList<>();
    @Nullable
    private GameKind pendingForcedGame;
    private boolean pendingKeepRegistrationOpen;
    private int lastNumbersHash;

    private record PendingSpectate(UUID player, long dueTick, boolean waitForGround) {
    }

    private record BodyRemoval(UUID entity, long dueTick) {
    }

    private TournamentManager(MinecraftServer server) {
        this.server = server;
        this.builds = new BuildManager(server);
        this.chunks = new ChunkLoader(server);
        this.hud = new HudService(this);
    }

    // =========================================================================================== static API

    @Nullable
    public static TournamentManager get() {
        return current;
    }

    public static void onServerStarted(MinecraftServer server) {
        current = new TournamentManager(server);
        current.init();
    }

    public static void onServerStopping(MinecraftServer server) {
        if (current != null) {
            current.shutdown();
            current = null;
        }
    }

    public static void onTerminalUse(ServerPlayer p, BlockPos pos) {
        if (current != null) {
            current.terminalUse(p);
        }
    }

    public static void onCardUse(ServerPlayer p) {
        if (current != null) {
            current.cardUse(p);
        }
    }

    public static void onBlockUse(ServerPlayer p, BlockPos pos) {
        TournamentManager m = current;
        if (m != null && m.t != null && m.t.game != null && m.t.ctx != null) {
            Contestant c = m.t.roster.ofPlayer(p.getUUID());
            if (c != null) {
                m.t.game.onBlockUse(m.t.ctx, c, p, pos);
            }
        }
    }

    public static boolean onInteractContestant(ServerPlayer p, ContestantEntity e) {
        TournamentManager m = current;
        if (m == null || m.t == null || m.t.game == null || m.t.ctx == null) {
            return false;
        }
        Contestant c = m.t.roster.ofPlayer(p.getUUID());
        return c != null && m.t.game.onInteractContestant(m.t.ctx, c, p, e);
    }

    public static void onMarbleRelease(ServerPlayer p, int charged) {
        TournamentManager m = current;
        if (m != null && m.t != null && m.t.game != null && m.t.ctx != null) {
            Contestant c = m.t.roster.ofPlayer(p.getUUID());
            if (c != null) {
                m.t.game.onMarbleRelease(m.t.ctx, c, p, charged);
            }
        }
    }

    public static void onClientAction(ServerPlayer p, String id, CompoundTag data) {
        TournamentManager m = current;
        if (m != null && m.t != null && m.t.game != null && m.t.ctx != null) {
            Contestant c = m.t.roster.ofPlayer(p.getUUID());
            if (c != null) {
                m.t.game.onClientAction(m.t.ctx, c, p, id, data);
            }
        }
    }

    /** True if the entity belongs to the running tournament (NPC bodies are discarded when orphaned). */
    public static boolean isManaged(Entity e) {
        TournamentManager m = current;
        if (m == null) {
            return false;
        }
        if (e instanceof ContestantEntity ce) {
            return m.t != null && m.t.roster.get(ce.contestantNumber()) != null;
        }
        // guards, dolls, ropes are spawned by games and removed by them; keep them while a tournament runs
        return m.t != null;
    }

    // =========================================================================================== accessors

    public MinecraftServer server() {
        return server;
    }

    @Nullable
    public Tournament tournament() {
        return t;
    }

    public BuildManager builds() {
        return builds;
    }

    public DoorService doors() {
        return doors;
    }

    public Restrictions restrictions() {
        return restrictions;
    }

    public PlayerStore store() {
        return store;
    }

    public boolean isSpectator(ServerPlayer p) {
        return spectators.contains(p.getUUID());
    }

    /** True if the player holds a snapshot or is a contestant, i.e. the tournament has changed their state. */
    public boolean isInvolved(ServerPlayer p) {
        return store.has(p.getUUID()) || (t != null && t.roster.ofPlayer(p.getUUID()) != null);
    }

    @Nullable
    public ServerLevel arenaLevel() {
        return ArenaWorld.level(server);
    }

    public ArenaData arenaData() {
        return ArenaData.get(server);
    }

    // =========================================================================================== lifecycle

    private void init() {
        data = TournamentData.get(server);
        store.load(data.snapshots);
        ServerLevel level = arenaLevel();
        if (level != null) {
            chunks.releaseEverything();
            NpcFactory.purge(level);
        }
        if (data.tournament != null && SquidConfig.get().resumeOnRestart) {
            try {
                resume(Tournament.load(data.tournament));
            } catch (RuntimeException e) {
                SquidGameMod.LOGGER.error("Could not resume the saved tournament; resetting", e);
                data.tournament = null;
                t = null;
            }
        } else if (data.tournament != null) {
            SquidGameMod.LOGGER.info("Saved tournament discarded (resumeOnRestart=false)");
            data.tournament = null;
        }
        data.setDirty();
    }

    private void shutdown() {
        saveNow();
    }

    private void saveNow() {
        if (data == null) {
            return;
        }
        data.snapshots = store.save();
        data.tournament = t == null ? null : t.save();
        data.setDirty();
    }

    /** Restores a saved tournament at the start of the interrupted step. All humans are treated as absent. */
    private void resume(Tournament saved) {
        ServerLevel level = arenaLevel();
        if (level == null || saved.roster.aliveCount() < 2 && saved.phase != Phase.FINAL_WINNER) {
            SquidGameMod.LOGGER.info("Saved tournament cannot be resumed; discarding");
            return;
        }
        this.t = saved;
        for (Contestant c : saved.roster.all()) {
            if (c.isHuman() && c.isAlive()) {
                c.setAbsent(true, 0);
            }
        }
        SquidGameMod.LOGGER.info("Resuming tournament: phase {} game {} with {} alive", saved.phase, saved.gameNumber,
                saved.roster.aliveCount());
        // respawn bodies in the hub, then replay the interrupted step
        List<Marker> spawns = arenaData().markers(ArenaId.HUB, "dorm.npc_spawn");
        int i = 0;
        for (Contestant c : saved.roster.alive()) {
            Vec3 p = spawns.isEmpty() ? hubFallback(i) : new Vec3(spawns.get(i % spawns.size()).x(), spawns.get(i % spawns.size()).y(), spawns.get(i % spawns.size()).z());
            ContestantEntity e = NpcFactory.spawnContestant(level, c, p, 0f);
            if (e != null) {
                e.setBehavior(new WaitingBehavior());
            }
            i++;
        }
        chunks.force(ArenaId.HUB);
        switch (saved.phase) {
            case REGISTRATION, LOBBY -> enter(Phase.REGISTRATION);
            case INSTRUCTIONS, COUNTDOWN, GAME, ELIMINATIONS, RESULTS -> {
                // replay the interrupted game from its start
                saved.game = null;
                saved.ctx = null;
                saved.gameNumber = Math.max(0, saved.gameNumber - 1);
                if (!saved.played.isEmpty() && saved.gameType != null && saved.played.get(saved.played.size() - 1) == saved.gameType) {
                    saved.played.remove(saved.played.size() - 1);
                }
                saved.gameType = saved.played.isEmpty() ? null : saved.played.get(saved.played.size() - 1);
                enterInstructions();
            }
            case TRANSITION -> enterInstructions();
            case FINAL_WINNER -> enter(Phase.RESTART);
            case RESTART -> enter(Phase.RESTART);
        }
    }

    private Vec3 hubFallback(int i) {
        Marker m = arenaData().marker(ArenaId.HUB, "dorm.player_spawn");
        double bx = m == null ? 0.5 : m.x();
        double by = m == null ? 65 : m.y();
        double bz = m == null ? 0.5 : m.z();
        return new Vec3(bx + (i % 12) * 1.2 - 6, by, bz + (i / 12) * 1.2 + 2);
    }

    // =========================================================================================== tick

    public void tick() {
        tickCounter++;
        long profStart = Profiler.start();
        builds.tick();
        if (t != null) {
            try {
                tickTournament();
            } catch (RuntimeException e) {
                SquidGameMod.LOGGER.error("Tournament tick failed in phase {}", t.phase, e);
            }
        }
        tickDelayed();
        tickPendingSpectate();
        tickBodyRemovals();
        tickPlayers();
        long hudStart = Profiler.start();
        hud.tick();
        Profiler.end(Profiler.Section.HUD, hudStart);
        if (tickCounter % 100 == 0) {
            syncNumbers(false);
        }
        if (tickCounter % 1200 == 0 && data != null) {
            saveNow();
        }
        Profiler.end(Profiler.Section.MANAGER, profStart);
        Profiler.tick();
    }

    private void tickPlayers() {
        ServerLevel level = arenaLevel();
        if (level == null) {
            return;
        }
        boolean portals = t == null && tickCounter % 5 == 0;
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (portals) {
                tickGatePortal(p);
            }
            boolean flight = spectators.contains(p.getUUID());
            restrictions.tickPlayer(p, flight);
            // spectators/eliminated stay inside the complex
            if (spectators.contains(p.getUUID()) && p.getY() < -40) {
                Marker m = arenaData().marker(ArenaId.HUB, "hub.spectator");
                if (m != null) {
                    p.teleportTo(level, m.x(), m.y(), m.z(), m.yaw(), 0f);
                }
            }
        }
    }

    /**
     * Free exploration: walking into one of the six gates at the top of the stairway hall (regions {@code gate.<arena>}, widened
     * by a few blocks in front of the doorway) takes a visitor to that arena's entrance while no tournament is running.
     */
    private void tickGatePortal(ServerPlayer p) {
        if (ArenaId.HUB != arenaIdAt(p) || p.isSpectator()) {
            return;
        }
        for (ArenaId id : ArenaId.values()) {
            if (id.game == null) {
                continue;
            }
            Region r = arenaData().region(ArenaId.HUB, "gate." + id.id);
            if (r != null && p.getX() >= r.minX() && p.getX() < r.maxX() + 1 && p.getY() >= r.minY() - 1 && p.getY() < r.maxY() + 1
                    && p.getZ() >= r.minZ() && p.getZ() < r.maxZ() + 4) {
                String err = tourArena(p, id);
                if (err != null) {
                    p.displayClientMessage(Component.literal(err), true);
                }
                return;
            }
        }
    }

    private static ArenaId arenaIdAt(ServerPlayer p) {
        int index = Math.round((float) (p.getX() / 1000.0));
        for (ArenaId id : ArenaId.values()) {
            if (id.originX == index * 1000 && ArenaWorld.isArena(p.level())) {
                return id;
            }
        }
        return null;
    }

    /** Sends a visitor to an arena's entrance (no tournament may be running). Returns an error message, or null on success. */
    public String tourArena(ServerPlayer p, ArenaId id) {
        if (t != null) {
            return "A tournament is running; arenas are in use.";
        }
        if (!ArenaWorld.isArena(p.level())) {
            enterOrBuild(p);
        }
        ServerLevel level = arenaLevel();
        Marker spot = id == ArenaId.HUB ? arenaData().marker(id, "dorm.player_spawn") : arenaData().marker(id, "waiting.player_entry");
        if (spot == null || level == null) {
            return "Arena not built yet. Run /squid build.";
        }
        level.getChunk(BlockPos.containing(spot.x(), spot.y(), spot.z()));
        p.teleportTo(level, spot.x(), spot.y(), spot.z(), spot.yaw(), 0f);
        Restrictions.noteTeleport(p);
        return null;
    }

    private void tickTournament() {
        t.phaseTicks++;
        if (t.ctx != null) {
            t.ctx.tickScheduler();
        }
        switch (t.phase) {
            case REGISTRATION -> tickRegistration();
            case INSTRUCTIONS -> tickInstructions();
            case COUNTDOWN -> tickCountdown();
            case GAME -> tickGame();
            case ELIMINATIONS -> {
                if (t.phaseTicks >= t.phaseLength) {
                    enter(Phase.RESULTS);
                }
            }
            case RESULTS -> {
                if (t.phaseTicks >= t.phaseLength) {
                    enter(Phase.TRANSITION);
                }
            }
            case TRANSITION -> tickTransition();
            case FINAL_WINNER -> {
                if (t.phaseTicks >= t.phaseLength) {
                    enter(Phase.RESTART);
                }
            }
            case RESTART -> {
                if (t.phaseTicks >= t.phaseLength) {
                    finishRestart();
                }
            }
            default -> {
            }
        }
        if (t != null && tickCounter % 20 == 0) {
            tickAbsentPlayers();
        }
    }

    // =========================================================================================== starting

    /**
     * Starts a tournament. Builds the complex first if needed. {@code starter} (may be null for a console start)
     * is registered automatically when present in the arena dimension.
     */
    public String start(@Nullable ServerPlayer starter, @Nullable Difficulty difficulty, int npcOverride, boolean npcOnly) {
        if (t != null) {
            return "A tournament is already running (phase " + t.phase.id + ").";
        }
        if (awaitingBuild) {
            return "The complex is still being prepared.";
        }
        Difficulty d = difficulty != null ? difficulty : Difficulty.byId(SquidConfig.get().defaultDifficulty, Difficulty.NORMAL);
        ServerLevel level = arenaLevel();
        if (level == null) {
            return "The tournament dimension is not available.";
        }
        List<ArenaId> all = List.of(ArenaId.values());
        boolean needBuild = false;
        for (ArenaId id : all) {
            if (!arenaData().isUpToDate(id, ArenaBuilders.get(id).version())) {
                needBuild = true;
            }
        }
        final UUID starterId = starter == null ? null : starter.getUUID();
        Runnable begin = () -> {
            awaitingBuild = false;
            ServerPlayer s = starterId == null ? null : server.getPlayerList().getPlayer(starterId);
            beginRegistration(s, d, npcOverride, npcOnly);
        };
        if (needBuild) {
            awaitingBuild = true;
            Announcer.chat(server, Component.translatable("squidgame.msg.building"));
            builds.request(all, false, msg -> Announcer.chat(server, msg), begin);
            return null;
        }
        begin.run();
        return null;
    }

    /** Debug: starts a tournament that plays exactly one game (registration is closed immediately). */
    public String startSingleGame(@Nullable ServerPlayer starter, GameKind game, @Nullable Difficulty difficulty, int npcOverride, boolean npcOnly,
                                  boolean keepRegistrationOpen) {
        pendingForcedGame = game;
        pendingKeepRegistrationOpen = keepRegistrationOpen;
        String err = start(starter, difficulty, npcOverride, npcOnly);
        if (err != null) {
            pendingForcedGame = null;
        }
        return err;
    }

    private void beginRegistration(@Nullable ServerPlayer starter, Difficulty d, int npcOverride, boolean npcOnly) {
        if (t != null) {
            return;
        }
        ServerLevel level = arenaLevel();
        t = new Tournament();
        if (pendingForcedGame != null) {
            t.forcedGame = pendingForcedGame;
            t.singleGame = true;
            pendingForcedGame = null;
        }
        t.seed = server.overworld().getRandom().nextLong();
        t.difficulty = d;
        t.npcOnly = npcOnly;
        t.startedAtTick = tickCounter;
        spectators.clear();
        chunks.force(ArenaId.HUB);
        Rng rng = new Rng(t.seed);

        if (starter != null && !npcOnly) {
            enterArena(starter);
            registerPlayer(starter);
        }
        int humans = t.roster.humanCount();
        SquidConfig cfg = SquidConfig.get();
        int npcCount;
        if (npcOverride >= 0) {
            npcCount = npcOverride;
        } else if (cfg.npcCount >= 0) {
            npcCount = cfg.npcCount;
        } else {
            npcCount = cfg.totalContestants - humans;
        }
        npcCount = Math.max(0, Math.min(npcCount, cfg.maxContestants - humans));
        spawnNpcs(level, rng, npcCount);
        enter(Phase.REGISTRATION);
        if (t.forcedGame != null && !pendingKeepRegistrationOpen) {
            closeRegistration();
        }
        pendingKeepRegistrationOpen = false;
        Announcer.chat(server, Component.translatable("squidgame.msg.registration_open", d.id));
        Announcer.title(server, Component.translatable("squidgame.title.registration"),
                Component.translatable("squidgame.subtitle.registration", t.roster.size()), 10, 60, 20);
        Announcer.sound(server, ModSounds.ANNOUNCE_CHIME, 1f, 1f);
        saveNow();
    }

    private void spawnNpcs(ServerLevel level, Rng rng, int count) {
        List<Marker> spawns = arenaData().markers(ArenaId.HUB, "dorm.npc_spawn");
        int base = t.roster.size();
        for (int i = 0; i < count; i++) {
            int number = t.roster.allocateNumber(rng, -1);
            Contestant c = Contestant.createNpc(number, rng);
            t.roster.add(c);
            Vec3 p;
            float yaw = 0f;
            if (spawns.isEmpty()) {
                p = hubFallback(base + i);
            } else {
                Marker m = spawns.get((base + i) % spawns.size());
                p = new Vec3(m.x(), m.y(), m.z());
                yaw = m.yaw();
            }
            ContestantEntity e = NpcFactory.spawnContestant(level, c, p, yaw);
            if (e != null) {
                e.setBehavior(new WaitingBehavior());
            }
        }
    }

    // =========================================================================================== players: enter / join / leave

    /** Brings a player into the tournament dimension (snapshot, adventure mode, kit) at the hub. */
    public void enterArena(ServerPlayer p) {
        ServerLevel level = arenaLevel();
        if (level == null) {
            return;
        }
        store.capture(p);
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.experienceLevel = 0;
        p.experienceProgress = 0;
        p.totalExperience = 0;
        p.getInventory().add(new ItemStack(ModItems.RECRUITER_CARD));
        restrictions.setDesiredMode(p, GameType.ADVENTURE);
        p.setGameMode(GameType.ADVENTURE);
        Marker m = arenaData().marker(ArenaId.HUB, "dorm.player_spawn");
        Vec3 pos = m == null ? new Vec3(0.5, ArenaId.ORIGIN_Y + 1, 0.5) : new Vec3(m.x(), m.y(), m.z());
        level.getChunk(BlockPos.containing(pos));
        p.teleportTo(level, pos.x, pos.y, pos.z, m == null ? 0f : m.yaw(), 0f);
        Restrictions.noteTeleport(p);
        setArenaRespawn(p);
        saveNow();
        syncNumbers(true);
    }

    /** Whoever dies inside the complex (e.g. by leaving the world's bottom) comes back in the dormitory, not the overworld. */
    private void setArenaRespawn(ServerPlayer p) {
        Marker m = arenaData().marker(ArenaId.HUB, "dorm.player_spawn");
        Vec3 pos = m == null ? new Vec3(0.5, ArenaId.ORIGIN_Y + 1, 0.5) : new Vec3(m.x(), m.y(), m.z());
        p.setRespawnPosition(ArenaWorld.DIMENSION, BlockPos.containing(pos), 0f, true, false);
    }

    /** Sends a player home (restores their snapshot). Contestants forfeit. */
    public void leaveArena(ServerPlayer p) {
        if (t != null) {
            Contestant c = t.roster.ofPlayer(p.getUUID());
            if (c != null && c.isAlive()) {
                if (t.ctx != null && t.phase.inArena) {
                    eliminate(t.ctx, c, EliminationCause.DISCONNECTED);
                } else {
                    c.markEliminated(t.gameNumber, EliminationCause.DISCONNECTED.id, ++t.eliminationCounter);
                    removeBody(c);
                }
            }
        }
        spectators.remove(p.getUUID());
        restrictions.forget(p.getUUID());
        pendingSpectate.removeIf(ps -> ps.player.equals(p.getUUID()));
        if (!store.restore(p)) {
            // nothing stored: send to the overworld spawn so the player is not stranded in the void dimension
            ServerLevel ow = server.overworld();
            var spawn = ow.getSharedSpawnPos();
            p.setGameMode(GameType.SURVIVAL);
            p.teleportTo(ow, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
        }
        saveNow();
        syncNumbers(true);
        endIfNoHumans();
    }

    /** Registers a player as a contestant (registration phase only). */
    public String registerPlayer(ServerPlayer p) {
        if (t == null) {
            return "No tournament is accepting registrations. Use /squid start.";
        }
        Contestant existing = t.roster.ofPlayer(p.getUUID());
        if (existing != null) {
            return "You are already registered as No. " + existing.displayNumber() + ".";
        }
        if (t.phase != Phase.REGISTRATION) {
            return "Registration is closed.";
        }
        if (t.roster.size() >= SquidConfig.get().maxContestants) {
            return "The tournament is full.";
        }
        if (!store.has(p.getUUID())) {
            enterArena(p);
        }
        Rng rng = new Rng(t.seed ^ p.getUUID().getLeastSignificantBits());
        // keep the total constant: drop the highest numbered NPC when auto-sizing
        if (SquidConfig.get().npcCount < 0 && t.roster.size() >= SquidConfig.get().totalContestants) {
            removeLastNpc();
        }
        int number = t.roster.allocateNumber(rng, -1);
        Contestant c = Contestant.createHuman(number, p, rng);
        t.roster.add(c);
        c.setPlayer(p);
        spectators.remove(p.getUUID());
        restrictions.setDesiredMode(p, GameType.ADVENTURE);
        p.sendSystemMessage(Component.translatable("squidgame.msg.registered", c.displayNumber()));
        Announcer.title(p, Component.literal(c.displayNumber()), Component.translatable("squidgame.subtitle.your_number"), 5, 50, 15);
        Announcer.sound(p, ModSounds.UI_CONFIRM, 1f, 1f);
        syncNumbers(true);
        saveNow();
        return null;
    }

    private void removeLastNpc() {
        Contestant last = null;
        for (Contestant c : t.roster.all()) {
            if (!c.isHuman() && (last == null || c.number > last.number)) {
                last = c;
            }
        }
        if (last != null) {
            removeBody(last);
            t.roster.remove(last);
        }
    }

    private void removeBody(Contestant c) {
        ServerLevel level = arenaLevel();
        if (level == null) {
            return;
        }
        ContestantEntity e = c.npc(level);
        if (e != null) {
            e.discard();
        }
        c.setBodyEntity(null);
    }

    /** A player right-clicked the registration terminal. */
    private void terminalUse(ServerPlayer p) {
        if (t == null) {
            String err = start(p, null, -1, false);
            if (err != null) {
                p.sendSystemMessage(Component.literal(err));
            }
            return;
        }
        if (t.phase == Phase.REGISTRATION) {
            String err = registerPlayer(p);
            if (err != null) {
                p.sendSystemMessage(Component.literal(err));
            }
            return;
        }
        statusMessage(p);
    }

    private void cardUse(ServerPlayer p) {
        if (!ArenaWorld.isArena(p.level())) {
            // card used outside the dimension: enter (builds the hub first if necessary)
            enterOrBuild(p);
            return;
        }
        terminalUse(p);
    }

    /** /squid enter and card use outside the dimension: builds the hub on first use. */
    public void enterOrBuild(ServerPlayer p) {
        ServerLevel level = arenaLevel();
        if (level == null) {
            p.sendSystemMessage(Component.literal("The tournament dimension is not available."));
            return;
        }
        if (!arenaData().isUpToDate(ArenaId.HUB, ArenaBuilders.get(ArenaId.HUB).version())) {
            if (!SquidConfig.get().autoBuildOnEnter) {
                p.sendSystemMessage(Component.literal("The tournament complex has not been built. Run /squid build."));
                return;
            }
            final UUID id = p.getUUID();
            p.sendSystemMessage(Component.translatable("squidgame.msg.building"));
            builds.request(List.of(ArenaId.HUB), false, p::sendSystemMessage, () -> {
                ServerPlayer again = server.getPlayerList().getPlayer(id);
                if (again != null) {
                    enterArena(again);
                }
            });
            return;
        }
        if (t != null && t.phase != Phase.REGISTRATION && t.phase != Phase.LOBBY && t.roster.ofPlayer(p.getUUID()) == null) {
            // a tournament is already under way: the newcomer watches it (the same rule as for a player who logs in mid-tournament)
            if (!SquidConfig.get().lateJoinSpectate) {
                p.sendSystemMessage(Component.translatable("squidgame.msg.enter_denied"));
                return;
            }
            enterArena(p);
            makeSpectator(p);
            p.sendSystemMessage(Component.translatable("squidgame.msg.late_join"));
            return;
        }
        enterArena(p);
    }

    public void statusMessage(ServerPlayer p) {
        if (t == null) {
            p.sendSystemMessage(Component.translatable("squidgame.msg.status.idle"));
            return;
        }
        Contestant c = t.roster.ofPlayer(p.getUUID());
        p.sendSystemMessage(Component.translatable("squidgame.msg.status.running", Component.translatable(t.phase.translationKey()),
                t.gameType == null ? Component.literal("-") : Component.translatable(t.gameType.titleKey()),
                t.roster.aliveCount(), t.roster.size(), t.difficulty.id));
        if (c != null) {
            p.sendSystemMessage(Component.translatable("squidgame.msg.status.you", c.displayNumber(), c.status().name()));
        }
    }

    // =========================================================================================== phase machine

    private void enter(Phase p) {
        t.phase = p;
        t.phaseTicks = 0;
        SquidConfig cfg = SquidConfig.get();
        SquidGameMod.LOGGER.info("Tournament phase -> {} (game {} {}, {} alive of {})", p, t.gameNumber, t.gameType, t.roster.aliveCount(), t.roster.size());
        switch (p) {
            case REGISTRATION -> t.phaseLength = cfg.ticks(cfg.registrationSeconds);
            case INSTRUCTIONS -> enterInstructions();
            case COUNTDOWN -> enterCountdown();
            case GAME -> enterGame();
            case ELIMINATIONS -> enterEliminations();
            case RESULTS -> enterResults();
            case TRANSITION -> enterTransition();
            case FINAL_WINNER -> enterFinalWinner();
            case RESTART -> {
                t.phaseLength = cfg.ticks(cfg.restartDelaySeconds);
                cleanupEverything(false);
                Announcer.chat(server, Component.translatable("squidgame.msg.restarting"));
            }
            default -> {
            }
        }
        saveNow();
    }

    // ---------------------------------------------------------------- registration

    private void tickRegistration() {
        if (t.phaseTicks >= t.phaseLength) {
            if (t.roster.size() < 2) {
                Announcer.chat(server, Component.translatable("squidgame.msg.not_enough"));
                t.phaseTicks = 0;
                return;
            }
            // registration closed: freeze the roster
            Announcer.chat(server, Component.translatable("squidgame.msg.registration_closed", t.roster.size()));
            enterInstructions();
            return;
        }
        int left = t.phaseLength - t.phaseTicks;
        if (left % 200 == 0 && left > 0) {
            Announcer.actionBar(server, Component.translatable("squidgame.msg.registration_countdown", left / 20, t.roster.size()));
        }
    }

    /** Ends registration early (/squid skip). */
    public void closeRegistration() {
        if (t != null && t.phase == Phase.REGISTRATION) {
            t.phaseTicks = t.phaseLength;
        }
    }

    // ---------------------------------------------------------------- instructions

    private void enterInstructions() {
        ServerLevel level = arenaLevel();
        Tournament tr = t;
        tr.phase = Phase.INSTRUCTIONS;
        tr.phaseTicks = 0;
        GameKind last = tr.played.isEmpty() ? null : tr.played.get(tr.played.size() - 1);
        GameKind next = tr.nextGame(last);
        if (next == null) {
            enter(Phase.FINAL_WINNER);
            return;
        }
        tr.gameNumber++;
        tr.gameType = next;
        tr.played.add(next);
        SquidGameMod.LOGGER.info("Tournament phase -> INSTRUCTIONS (game {} {}, {} alive of {})", tr.gameNumber, next, tr.roster.aliveCount(), tr.roster.size());
        ArenaId arena = ArenaId.forGame(next);
        chunks.force(arena);
        Rng grng = new Rng(tr.seed * 31 + tr.gameNumber * 977L);
        GameContext ctx = new GameContext(this, tr, level, arena, grng);
        MiniGame game = GameRegistry.create(next);
        if (!tr.savedGameState.isEmpty()) {
            game.loadState(tr.savedGameState);
            tr.savedGameState = new CompoundTag();
        }
        tr.ctx = ctx;
        tr.game = game;
        tr.gameTicks = 0;
        for (Contestant c : tr.roster.alive()) {
            c.clearGameScratch(next);
        }
        SquidConfig cfg = SquidConfig.get();
        tr.phaseLength = cfg.ticks(cfg.instructionSeconds);

        // fade humans out/in while everyone is moved to the arena's waiting room
        fadeHumans(10, 12, 18, 0xFF000000);
        List<Marker> slots = ctx.markers("waiting.spawn");
        Teleporter.spread(level, tr.roster.alive(), slots);
        for (Contestant c : tr.roster.alive()) {
            ContestantEntity e = c.npc(level);
            if (e != null) {
                e.setBehavior(new WaitingBehavior());
                e.setActivity(Activity.NONE);
            }
        }
        // the gate between waiting room and arena stays closed until the countdown ends
        Marker gate = ctx.marker("gate.door");
        if (gate != null) {
            doors.create(level, "gate", gate, "squidgame:tile_pink");
        }
        game.prepare(ctx);
        // announce
        Component title = Component.translatable(next.titleKey());
        Announcer.title(server, title, Component.translatable("squidgame.subtitle.game_number", tr.gameNumber), 10, 60, 20);
        Announcer.sound(server, ModSounds.ANNOUNCE_CHIME, 1f, 1f);
        Announcer.chat(server, Component.translatable("squidgame.msg.game_header", tr.gameNumber, title).withStyle(net.minecraft.ChatFormatting.GOLD));
        List<Component> lines = game.instructions(ctx);
        int spacing = Math.max(20, Math.min(100, tr.phaseLength / Math.max(2, lines.size() + 1)));
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            ctx.schedule(40 + i * spacing, () -> {
                Announcer.chat(server, Component.literal("  ").append(line).withStyle(net.minecraft.ChatFormatting.YELLOW));
                Announcer.sound(server, ModSounds.UI_SELECT, 0.6f, 1.2f);
            });
        }
        syncNumbers(true);
        saveNow();
    }

    private void tickInstructions() {
        if (t.phaseTicks >= t.phaseLength) {
            enter(Phase.COUNTDOWN);
        }
    }

    // ---------------------------------------------------------------- countdown

    private void enterCountdown() {
        SquidConfig cfg = SquidConfig.get();
        t.phaseLength = cfg.ticks(cfg.countdownSeconds);
        t.lastCountdownSecond = -1;
        ServerLevel level = arenaLevel();
        fadeHumans(6, 6, 12, 0xFF000000);
        t.game.placeContestants(t.ctx);
        doors.open(level, "gate");
        Announcer.sound(server, ModSounds.ANNOUNCE_CHIME_ALERT, 1f, 1f);
    }

    private void tickCountdown() {
        int total = Math.max(1, t.phaseLength);
        int remaining = total - t.phaseTicks;
        int secondsLeft = (int) Math.ceil(remaining / (20.0 * SquidConfig.get().timeScale));
        if (secondsLeft != t.lastCountdownSecond && secondsLeft >= 1 && secondsLeft <= 5) {
            t.lastCountdownSecond = secondsLeft;
            Announcer.title(server, Component.literal(Integer.toString(secondsLeft)), Component.empty(), 0, 15, 5);
            Announcer.sound(server, secondsLeft == 1 ? ModSounds.COUNTDOWN_FINAL : ModSounds.COUNTDOWN_BEEP, 1f, 1f);
        }
        if (t.phaseTicks >= t.phaseLength) {
            enter(Phase.GAME);
        }
    }

    // ---------------------------------------------------------------- game

    private void enterGame() {
        t.gameTicks = 0;
        int limit = (int) Math.max(20, t.game.timeLimitTicks(t.ctx) * SquidConfig.get().timeScale);
        t.phaseLength = limit;
        t.game.begin(t.ctx);
        Announcer.title(server, Component.translatable("squidgame.title.go"), Component.empty(), 0, 20, 10);
        Announcer.sound(server, ModSounds.GAME_START_HORN, 1f, 1f);
    }

    private int watchdogTimer;

    private void tickGame() {
        t.gameTicks++;
        long gameStart = Profiler.start();
        t.game.tick(t.ctx);
        Profiler.end(Profiler.Section.GAME, gameStart);
        if (t.roster.aliveCount() == 0 || t.game.isFinished(t.ctx)) {
            enter(Phase.ELIMINATIONS);
            return;
        }
        if (t.phaseTicks >= t.phaseLength) {
            t.game.onTimeout(t.ctx);
            enter(Phase.ELIMINATIONS);
            return;
        }
        if (++watchdogTimer >= 40) {
            watchdogTimer = 0;
            bodyWatchdog();
        }
    }

    /** Alive contestants without a body (lost entity) or far below the arena are eliminated so progress never stalls. */
    private void bodyWatchdog() {
        ServerLevel level = arenaLevel();
        for (Contestant c : t.roster.alive()) {
            LivingEntity b = c.body(level);
            if (b == null) {
                if (c.isHumanControlled()) {
                    continue; // offline players are handled by the absence rules
                }
                if (c.isAiControlled() && c.bodyEntityId() == null) {
                    continue;
                }
                eliminate(t.ctx, c, EliminationCause.OUT_OF_BOUNDS);
            } else if (b.getY() < ArenaId.ORIGIN_Y - 130) {
                eliminate(t.ctx, c, EliminationCause.FELL);
            } else if (c.isHumanControlled()) {
                enforceBounds(c, b);
            }
        }
    }

    /**
     * Humans must stay inside the arena while a game runs (walking back through the gate or out of the play area is not an
     * escape): a warning first, then they are put back, and after about eight seconds outside they are eliminated.
     */
    private void enforceBounds(Contestant c, LivingEntity body) {
        Region bounds = t.ctx.region("arena.bounds");
        if (bounds == null) {
            return;
        }
        final double margin = 3.0;
        boolean outside = body.getX() < bounds.minX() - margin || body.getX() > bounds.maxX() + 1 + margin
                || body.getZ() < bounds.minZ() - margin || body.getZ() > bounds.maxZ() + 1 + margin;
        if (!outside) {
            c.stats.remove("boundsStrikes");
            return;
        }
        int strikes = (int) c.stat("boundsStrikes", 0) + 1;
        c.stats.put("boundsStrikes", (double) strikes);
        ServerPlayer p = c.player(server);
        if (strikes >= 4) {
            eliminate(t.ctx, c, EliminationCause.OUT_OF_BOUNDS);
            return;
        }
        if (p != null) {
            Announcer.chat(p, Component.translatable("squidgame.rule.out_of_bounds"));
            Announcer.sound(p, ModSounds.UI_DENY, 1f, 1f);
            if (strikes >= 2) {
                double x = Math.max(bounds.minX() + 1.5, Math.min(bounds.maxX() - 0.5, body.getX()));
                double z = Math.max(bounds.minZ() + 1.5, Math.min(bounds.maxZ() - 0.5, body.getZ()));
                p.teleportTo(arenaLevel(), x, body.getY(), z, p.getYRot(), p.getXRot());
                Restrictions.noteTeleport(p);
            }
        }
    }

    // ---------------------------------------------------------------- eliminations / results

    private GameResult lastResult;

    private void enterEliminations() {
        SquidConfig cfg = SquidConfig.get();
        t.phaseLength = cfg.ticks(6);
        Announcer.sound(server, ModSounds.GAME_END_BUZZER, 1f, 1f);
        Announcer.title(server, Component.translatable("squidgame.title.game_over"), Component.empty(), 0, 30, 10);
        lastResult = t.game.conclude(t.ctx);
        // eliminations ordered by conclude() may be staggered over the next seconds: the survivors are final once RESULTS starts
    }

    private void enterResults() {
        SquidConfig cfg = SquidConfig.get();
        t.phaseLength = cfg.ticks(cfg.resultsSeconds);
        t.lastEliminated.clear();
        for (Contestant c : t.roster.all()) {
            if (c.isEliminated() && c.eliminatedInGame() == t.gameNumber) {
                t.lastEliminated.add(c.number);
            }
        }
        for (Contestant c : t.roster.alive()) {
            c.incrementGamesSurvived();
        }
        SquidGameMod.LOGGER.info("Game {} ({}) concluded: {} survivors, {} eliminated in this game, {} eliminated overall", t.gameNumber,
                t.gameType, t.roster.aliveCount(), t.lastEliminated.size(), t.roster.eliminatedInOrder().size());
        Announcer.sound(server, ModSounds.GAME_RESULTS_STING, 1f, 1f);
        List<Integer> survivors = new ArrayList<>();
        for (Contestant c : t.roster.alive()) {
            survivors.add(c.number);
        }
        List<Integer> elim = new ArrayList<>(t.lastEliminated);
        Component headline = lastResult != null ? lastResult.headline()
                : Component.translatable("squidgame.results.headline", t.roster.aliveCount());
        Component sub = lastResult != null ? lastResult.detail() : Component.empty();
        if (t.roster.aliveCount() == 0) {
            headline = Component.translatable("squidgame.results.none_survived");
        }
        long prize = prizePool();
        for (ServerPlayer p : Announcer.audience(server)) {
            Contestant me = t.roster.ofPlayer(p.getUUID());
            int outcome = me == null ? ResultsPayload.OUTCOME_NONE
                    : me.isAlive() ? ResultsPayload.OUTCOME_SURVIVED : ResultsPayload.OUTCOME_ELIMINATED;
            ModNetwork.send(p, new ResultsPayload(headline, sub, survivors, elim.size() > 60 ? elim.subList(0, 60) : elim,
                    me == null ? 0 : me.number, outcome, t.phaseLength, prize));
        }
        Announcer.chat(server, Component.translatable("squidgame.msg.results_summary", survivors.size(), elim.size(),
                Component.literal(String.format("%,d", prize))));
        syncNumbers(true);
        saveNow();
    }

    public long prizePool() {
        if (t == null) {
            return 0;
        }
        return (long) t.roster.eliminatedInOrder().size() * PRIZE_PER_ELIMINATION;
    }

    // ---------------------------------------------------------------- transition

    private void enterTransition() {
        ServerLevel level = arenaLevel();
        SquidConfig cfg = SquidConfig.get();
        ArenaId arena = t.ctx != null ? t.ctx.arena : null;
        endGameObjects();
        if (arena != null) {
            chunks.release(arena);
        }
        GameKind last = t.played.isEmpty() ? null : t.played.get(t.played.size() - 1);
        GameKind next = t.nextGame(last);
        if (next == null) {
            enter(Phase.FINAL_WINNER);
            return;
        }
        t.phaseLength = cfg.ticks(cfg.transitionSeconds);
        if (t.forcedGame == null) {
            for (GameKind skipped : Planner.skipped(last, t.roster.aliveCount())) {
                Announcer.chat(server, Component.translatable("squidgame.msg.game_skipped", Component.translatable(skipped.titleKey()),
                        skipped.minParticipants, t.roster.aliveCount()));
            }
        }
        // survivors go back to the dormitory to rest; eliminated humans keep spectating there
        fadeHumans(14, 20, 18, 0xFF000000);
        List<Marker> bunks = arenaData().markers(ArenaId.HUB, "dorm.npc_spawn");
        List<Contestant> alive = t.roster.alive();
        for (int i = 0; i < alive.size(); i++) {
            Contestant c = alive.get(i);
            Vec3 p;
            float yaw = 0f;
            if (bunks.isEmpty()) {
                p = hubFallback(i);
            } else {
                Marker m = bunks.get(i % bunks.size());
                p = new Vec3(m.x(), m.y(), m.z());
                yaw = m.yaw();
            }
            Teleporter.teleport(level, c, p, yaw);
            ContestantEntity e = c.npc(level);
            if (e != null) {
                e.setBehavior(new WaitingBehavior());
                e.setActivity(Activity.NONE);
            }
        }
        moveSpectatorsToHub();
        Announcer.title(server, Component.translatable("squidgame.title.transition", t.gameNumber),
                Component.translatable("squidgame.subtitle.transition", alive.size()), 10, 70, 20);
        Announcer.chat(server, Component.translatable("squidgame.msg.transition", t.roster.aliveCount()));
        t.ctx = null;
        t.game = null;
        saveNow();
    }

    private void moveSpectatorsToHub() {
        ServerLevel level = arenaLevel();
        Marker m = arenaData().marker(ArenaId.HUB, "hub.spectator");
        if (m == null) {
            m = arenaData().marker(ArenaId.HUB, "dorm.player_spawn");
        }
        if (m == null) {
            return;
        }
        for (UUID id : new ArrayList<>(spectators)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.teleportTo(level, m.x(), m.y(), m.z(), m.yaw(), 0f);
                Restrictions.noteTeleport(p);
            }
        }
    }

    private void tickTransition() {
        int left = t.phaseLength - t.phaseTicks;
        if (left == 100) {
            Announcer.sound(server, ModSounds.ANNOUNCE_CHIME_ALERT, 1f, 0.8f);
            Announcer.chat(server, Component.translatable("squidgame.msg.lights_out"));
        }
        if (t.phaseTicks >= t.phaseLength) {
            enterInstructions();
        }
    }

    private void endGameObjects() {
        ServerLevel level = arenaLevel();
        if (t != null && t.game != null && t.ctx != null) {
            try {
                t.game.cleanup(t.ctx);
            } catch (RuntimeException e) {
                SquidGameMod.LOGGER.error("Game cleanup failed", e);
            }
            t.ctx.clearScheduled();
            t.ctx.clearBehaviors();
            t.ctx.cleanupGuards();
        }
        doors.removeAll(level);
    }

    // ---------------------------------------------------------------- final winner

    private void enterFinalWinner() {
        ServerLevel level = arenaLevel();
        SquidConfig cfg = SquidConfig.get();
        endGameObjects();
        t.phaseLength = cfg.ticks(cfg.winnerSeconds);
        t.winners.clear();
        for (Contestant c : t.roster.alive()) {
            c.markWinner();
            t.winners.add(c.number);
        }
        long prize = prizePool();
        Component headline;
        Component sub;
        if (t.winners.isEmpty()) {
            headline = Component.translatable("squidgame.results.no_winner");
            sub = Component.translatable("squidgame.results.no_winner.sub");
        } else if (t.winners.size() == 1) {
            Contestant w = t.roster.get(t.winners.get(0));
            headline = Component.translatable("squidgame.results.winner", w.displayNumber());
            sub = Component.translatable("squidgame.results.winner.sub", w.name, Component.literal(String.format("%,d", prize)));
        } else {
            headline = Component.translatable("squidgame.results.winners", t.winners.size());
            sub = Component.translatable("squidgame.results.winner.shared", Component.literal(String.format("%,d", prize / t.winners.size())));
        }
        // ceremony: everyone to the hub podium
        Marker pod = arenaData().marker(ArenaId.HUB, "hub.podium");
        List<Marker> spawns = arenaData().markers(ArenaId.HUB, "dorm.npc_spawn");
        fadeHumans(14, 24, 20, 0xFFFFFFFF);
        int i = 0;
        for (Contestant c : t.roster.alive()) {
            Marker m = pod != null ? pod : (spawns.isEmpty() ? null : spawns.get(i % spawns.size()));
            Vec3 p = m == null ? hubFallback(i) : new Vec3(m.x() + (i % 3) * 1.5, m.y(), m.z() + (i / 3) * 1.5);
            Teleporter.teleport(level, c, p, m == null ? 0f : m.yaw());
            ContestantEntity e = c.npc(level);
            if (e != null) {
                e.setBehavior(null);
                e.setActivity(Activity.CELEBRATE);
            }
            i++;
        }
        moveSpectatorsToHub();
        for (Contestant c : t.roster.all()) {
            ContestantEntity e = c.npc(level);
            if (e != null && !c.isAlive() && c.isEliminated() && !e.isRemoved()) {
                e.discard();
            }
        }
        Announcer.sound(server, ModSounds.GAME_WIN_FANFARE, 1f, 1f);
        List<Integer> sv = new ArrayList<>(t.winners);
        for (ServerPlayer p : Announcer.audience(server)) {
            Contestant me = t.roster.ofPlayer(p.getUUID());
            int outcome = me == null ? ResultsPayload.OUTCOME_NONE : me.status() == ContestantStatus.WINNER
                    ? ResultsPayload.OUTCOME_WINNER : ResultsPayload.OUTCOME_ELIMINATED;
            ModNetwork.send(p, new ResultsPayload(headline, sub, sv, List.of(), me == null ? 0 : me.number, outcome,
                    t.phaseLength, t.winners.isEmpty() ? 0 : prize / t.winners.size()));
        }
        Announcer.title(server, headline, sub, 10, 120, 30);
        Announcer.chat(server, headline);
        spawnFireworks(level, t.winners.isEmpty() ? null : t.roster.get(t.winners.get(0)));
        syncNumbers(true);
        saveNow();
    }

    private void spawnFireworks(ServerLevel level, @Nullable Contestant winner) {
        if (winner == null) {
            return;
        }
        Vec3 c = winner.position(level);
        if (c == null) {
            return;
        }
        int[][] palettes = {{0xED1B76, 0xFFFFFF}, {0x0FA89C, 0xFFFFFF}, {0xFFD23F, 0xED1B76}};
        for (int i = 0; i < 16; i++) {
            final int k = i;
            later(10 + i * 8, () -> {
                double ang = k * 2.4;
                double rad = 3 + k % 4;
                int[] pal = palettes[k % palettes.length];
                ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
                FireworkExplosion ex = new FireworkExplosion(k % 3 == 0 ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.LARGE_BALL,
                        IntList.of(pal), IntList.of(0xFFFFFF), true, k % 2 == 0);
                rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(ex)));
                level.addFreshEntity(new FireworkRocketEntity(level, c.x + Math.cos(ang) * rad, c.y + 1, c.z + Math.sin(ang) * rad, rocket));
            });
        }
    }

    /** Runs {@code action} after {@code ticks} manager ticks (independent of any game context). */
    private void later(int ticks, Runnable action) {
        delayed.add(new Delayed(tickCounter + ticks, action));
    }

    private void tickDelayed() {
        if (delayed.isEmpty()) {
            return;
        }
        for (int i = 0; i < delayed.size(); i++) {
            Delayed d = delayed.get(i);
            if (d.due() <= tickCounter) {
                delayed.remove(i--);
                try {
                    d.action().run();
                } catch (RuntimeException e) {
                    SquidGameMod.LOGGER.error("delayed task failed", e);
                }
            }
        }
    }

    // ---------------------------------------------------------------- restart

    private void finishRestart() {
        boolean again = SquidConfig.get().autoRestart && !Announcer.audience(server).isEmpty();
        t = null;
        lastResult = null;
        if (again) {
            // new registration with the visitors currently in the dimension
            ServerPlayer first = Announcer.audience(server).get(0);
            Difficulty d = Difficulty.byId(SquidConfig.get().defaultDifficulty, Difficulty.NORMAL);
            beginRegistration(first, d, -1, false);
            for (ServerPlayer p : Announcer.audience(server)) {
                registerPlayer(p);
            }
        } else {
            Announcer.chat(server, Component.translatable("squidgame.msg.back_in_lobby"));
        }
        saveNow();
        syncNumbers(true);
    }

    /**
     * Removes every temporary object of the tournament. {@code restorePlayers} sends humans home; otherwise they
     * are returned to the hub lobby as visitors with normal arena rules.
     */
    private void cleanupEverything(boolean restorePlayers) {
        ServerLevel level = arenaLevel();
        if (level == null) {
            return;
        }
        endGameObjects();
        NpcFactory.purge(level);
        chunks.releaseAllActive();
        chunks.releaseEverything();
        pendingSpectate.clear();
        bodyRemovals.clear();
        spectators.clear();
        Marker spawn = arenaData().marker(ArenaId.HUB, "dorm.player_spawn");
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (restorePlayers) {
                restrictions.forget(p.getUUID());
                store.restore(p);
            } else {
                restrictions.setDesiredMode(p, GameType.ADVENTURE);
                p.setGameMode(GameType.ADVENTURE);
                p.setInvulnerable(false);
                if (spawn != null) {
                    p.teleportTo(level, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), 0f);
                    Restrictions.noteTeleport(p);
                }
            }
        }
    }

    /** Aborts the tournament and cleans everything up (/squid reset). */
    public void reset(boolean sendHome) {
        ServerLevel level = arenaLevel();
        if (t != null) {
            if (t.game != null && t.ctx != null) {
                endGameObjects();
            }
            t = null;
        }
        lastResult = null;
        awaitingBuild = false;
        if (level != null) {
            doors.removeAll(level);
            cleanupEverything(sendHome);
        }
        saveNow();
        syncNumbers(true);
        Announcer.chat(server, Component.translatable("squidgame.msg.reset"));
    }

    /** Skips ahead: ends registration, the current game (everyone not yet safe is eliminated) or the current rest. */
    public String skip() {
        if (t == null) {
            return "No tournament is running.";
        }
        switch (t.phase) {
            case REGISTRATION -> closeRegistration();
            case INSTRUCTIONS -> t.phaseTicks = t.phaseLength;
            case COUNTDOWN -> t.phaseTicks = t.phaseLength;
            case GAME -> {
                t.phaseTicks = t.phaseLength;
            }
            case ELIMINATIONS, RESULTS, TRANSITION, FINAL_WINNER, RESTART -> t.phaseTicks = t.phaseLength;
            default -> {
            }
        }
        return null;
    }

    private void endIfNoHumans() {
        if (t == null || t.npcOnly) {
            return;
        }
        boolean anyHuman = false;
        ServerLevel level = arenaLevel();
        if (level != null) {
            for (ServerPlayer p : level.players()) {
                if (Restrictions.isParticipant(p) || t.roster.ofPlayer(p.getUUID()) != null) {
                    anyHuman = true;
                }
            }
        }
        if (!anyHuman && t.roster.humanCount() > 0) {
            // everyone who registered has left: nothing to watch, shut it down
            SquidGameMod.LOGGER.info("No humans left in the arena; ending the tournament");
            reset(false);
        }
    }

    // =========================================================================================== eliminations

    /** Applies an elimination: status, effects, body handling, announcements, game hook. Idempotent. */
    public void eliminate(GameContext ctx, Contestant c, EliminationCause cause) {
        if (t == null || !c.markEliminated(t.gameNumber, cause.id, ++t.eliminationCounter)) {
            return;
        }
        ServerLevel level = arenaLevel();
        LivingEntity body = c.body(level);
        Vec3 pos = body != null ? body.position() : null;
        if (pos != null) {
            switch (cause.effect) {
                case COLLAPSE -> {
                    level.sendParticles(new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.1f), 1.4f), pos.x, pos.y + 1.0, pos.z, 40, 0.35, 0.6, 0.35, 0.02);
                    level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.6, pos.z, 8, 0.3, 0.3, 0.3, 0.02);
                    Announcer.soundAt(level, pos, ModSounds.ELIMINATION_BODY_FALL, SoundSource.PLAYERS, 1.2f, 1f);
                }
                case VANISH -> level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 1.0, pos.z, 20, 0.3, 0.6, 0.3, 0.03);
                default -> {
                }
            }
        }
        if (c.isHumanControlled()) {
            ServerPlayer p = c.player(server);
            if (p != null) {
                ModNetwork.send(p, new FadePayload(0, 6, 25, 0xAA8B0000));
                Announcer.title(p, Component.translatable("squidgame.title.eliminated"),
                        Component.translatable("squidgame.title.eliminated.sub", Component.translatable(cause.translationKey())), 0, 60, 20);
                Announcer.sound(p, ModSounds.ELIMINATION_BUZZER, 1f, 1f);
                if (cause == EliminationCause.FELL || cause == EliminationCause.LOST_TEAM) {
                    pendingSpectate.add(new PendingSpectate(p.getUUID(), tickCounter + 70, true));
                } else {
                    pendingSpectate.add(new PendingSpectate(p.getUUID(), tickCounter + 20, false));
                }
            }
        }
        ContestantEntity npc = c.npc(level);
        if (npc != null) {
            npc.setBehavior(null);
            npc.stopMoving();
            npc.getNavigation().stop();
            if (cause.effect == EliminationCause.Effect.COLLAPSE) {
                npc.setActivity(npc.getRandom().nextBoolean() ? Activity.ELIMINATED_FORWARD : Activity.ELIMINATED_BACKWARD);
                bodyRemovals.add(new BodyRemoval(npc.getUUID(), tickCounter + 100 + npc.getRandom().nextInt(60)));
            } else if (cause.effect == EliminationCause.Effect.VANISH) {
                bodyRemovals.add(new BodyRemoval(npc.getUUID(), tickCounter + 5));
            } else {
                bodyRemovals.add(new BodyRemoval(npc.getUUID(), tickCounter + 120));
            }
        }
        // witnesses react
        if (pos != null) {
            for (ContestantEntity other : level.getEntitiesOfClass(ContestantEntity.class,
                    new net.minecraft.world.phys.AABB(pos.x - 18, pos.y - 6, pos.z - 18, pos.x + 18, pos.y + 6, pos.z + 18))) {
                if (other != npc && other.contestantNumber() != c.number) {
                    double d = other.position().distanceTo(pos);
                    if (d < 18) {
                        other.witnessElimination(c.number, d);
                    }
                }
            }
        }
        if (t.game != null) {
            try {
                t.game.onContestantEliminated(ctx, c, cause);
            } catch (RuntimeException e) {
                SquidGameMod.LOGGER.error("onContestantEliminated failed", e);
            }
        }
        MutableComponent msg = Component.translatable("squidgame.msg.eliminated", c.displayNumber(), Component.translatable(cause.translationKey()))
                .withStyle(net.minecraft.ChatFormatting.RED);
        announceElimination(msg);
        syncNumbers(false);
        saveNow();
        if (t.roster.aliveHumanCount() == 0 && t.roster.humanCount() > 0 && !t.npcOnly && !SquidConfig.get().spectateAfterElimination) {
            // all humans are out and spectating is disabled: wrap up quickly
            t.phaseTicks = Math.max(t.phaseTicks, t.phaseLength - 40);
        }
    }

    // a timeout can eliminate dozens at once: after a few chat lines the rest are summed up in one line (the results board
    // sits where a flooded chat would cover it)
    private long elimWindowStart = -1000;
    private int elimWindowCount;
    private int elimSuppressed;

    private void announceElimination(MutableComponent msg) {
        if (tickCounter - elimWindowStart > 40) {
            flushSuppressedEliminations();
            elimWindowStart = tickCounter;
            elimWindowCount = 0;
        }
        if (elimWindowCount++ < 5) {
            Announcer.chat(server, msg);
        } else if (elimSuppressed++ == 0) {
            later(45, this::flushSuppressedEliminations);
        }
    }

    private void flushSuppressedEliminations() {
        if (elimSuppressed > 0) {
            Announcer.chat(server, Component.translatable("squidgame.msg.more_eliminated", elimSuppressed)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            elimSuppressed = 0;
        }
    }

    private void tickPendingSpectate() {
        if (pendingSpectate.isEmpty()) {
            return;
        }
        ServerLevel level = arenaLevel();
        Iterator<PendingSpectate> it = pendingSpectate.iterator();
        while (it.hasNext()) {
            PendingSpectate ps = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(ps.player);
            if (p == null) {
                it.remove();
                continue;
            }
            boolean ready = tickCounter >= ps.dueTick || (ps.waitForGround && p.onGround() && tickCounter >= ps.dueTick - 50)
                    || p.getY() < ArenaId.ORIGIN_Y - 100;
            if (ready) {
                it.remove();
                makeSpectator(p);
            }
        }
    }

    /** Turns a player into a spectator and places them at the current arena's viewing point. */
    public void makeSpectator(ServerPlayer p) {
        spectators.add(p.getUUID());
        restrictions.setDesiredMode(p, GameType.SPECTATOR);
        p.setGameMode(GameType.SPECTATOR);
        ServerLevel level = arenaLevel();
        Marker m = null;
        if (t != null && t.ctx != null && t.phase.inArena) {
            m = t.ctx.marker("arena.spectator");
        }
        if (m == null) {
            m = arenaData().marker(ArenaId.HUB, "hub.spectator");
        }
        if (m != null && level != null) {
            p.teleportTo(level, m.x(), m.y(), m.z(), m.yaw(), 0f);
            Restrictions.noteTeleport(p);
        }
        syncNumbers(true);
    }

    private void tickBodyRemovals() {
        if (bodyRemovals.isEmpty()) {
            return;
        }
        ServerLevel level = arenaLevel();
        Iterator<BodyRemoval> it = bodyRemovals.iterator();
        while (it.hasNext()) {
            BodyRemoval r = it.next();
            if (tickCounter >= r.dueTick) {
                it.remove();
                Entity e = level == null ? null : level.getEntity(r.entity);
                if (e != null) {
                    level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
                    e.discard();
                }
            }
        }
    }

    /** A participant would die from some vanilla cause (void, /kill...). */
    public void onParticipantWouldDie(ServerPlayer p) {
        // keep them alive and put them back; contestants that fell out of the world are eliminated by the watchdog
        p.setHealth(p.getMaxHealth());
        p.fallDistance = 0;
        if (t != null && t.ctx != null) {
            Contestant c = t.roster.ofPlayer(p.getUUID());
            if (c != null && c.isAlive() && t.phase.inArena) {
                eliminate(t.ctx, c, EliminationCause.OUT_OF_BOUNDS);
                return;
            }
        }
        Marker m = arenaData().marker(ArenaId.HUB, "dorm.player_spawn");
        ServerLevel level = arenaLevel();
        if (m != null && level != null) {
            p.teleportTo(level, m.x(), m.y(), m.z(), m.yaw(), 0f);
            Restrictions.noteTeleport(p);
        }
    }

    /** Route a left click: games may handle it; otherwise it is cancelled. Returns true to cancel. */
    public boolean onPlayerAttack(ServerPlayer p, Entity target) {
        if (t == null || t.game == null || t.ctx == null || t.phase != Phase.GAME) {
            return true;
        }
        Contestant c = t.roster.ofPlayer(p.getUUID());
        if (c == null || !c.isAlive()) {
            return true;
        }
        return t.game.onPlayerAttack(t.ctx, c, p, target);
    }

    // =========================================================================================== connections

    public void onPlayerJoin(ServerPlayer p) {
        ServerLevel level = arenaLevel();
        Contestant c = t == null ? null : t.roster.ofPlayer(p.getUUID());
        if (c != null && c.isAlive()) {
            // a contestant returns: take the body back from the auto-pilot
            resumeControl(p, c);
            return;
        }
        boolean inArena = ArenaWorld.isArena(p.level());
        if (store.has(p.getUUID()) || inArena) {
            if (t == null && store.has(p.getUUID()) && !inArena) {
                // stale snapshot (crash) and the player is already outside: just forget it
                store.forget(p.getUUID());
                return;
            }
            if (t != null && t.phase != Phase.REGISTRATION && t.phase != Phase.LOBBY) {
                if (SquidConfig.get().lateJoinSpectate) {
                    if (!store.has(p.getUUID())) {
                        store.capture(p);
                    }
                    makeSpectator(p);
                    p.sendSystemMessage(Component.translatable("squidgame.msg.late_join"));
                } else {
                    store.restore(p);
                }
            } else if (t == null) {
                if (store.has(p.getUUID()) && !inArena) {
                    store.restore(p);
                } else if (inArena) {
                    restrictions.setDesiredMode(p, GameType.ADVENTURE);
                    setArenaRespawn(p);
                }
            }
        }
        syncNumbers(true);
    }

    private void resumeControl(ServerPlayer p, Contestant c) {
        ServerLevel level = arenaLevel();
        ContestantEntity npc = c.npc(level);
        Vec3 pos = npc != null ? npc.position() : null;
        float yaw = npc != null ? npc.getYRot() : 0f;
        if (!store.has(p.getUUID())) {
            store.capture(p);
        }
        c.setAbsent(false, tickCounter);
        c.setPlayer(p);
        if (npc != null) {
            npc.setBehavior(null);
            npc.discard();
            c.setBodyEntity(null);
        }
        restrictions.setDesiredMode(p, GameType.ADVENTURE);
        p.setGameMode(GameType.ADVENTURE);
        if (pos == null) {
            Marker m = arenaData().marker(ArenaId.HUB, "dorm.player_spawn");
            pos = m == null ? new Vec3(0.5, ArenaId.ORIGIN_Y + 1, 0.5) : new Vec3(m.x(), m.y(), m.z());
        }
        p.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0f);
        Restrictions.noteTeleport(p);
        p.sendSystemMessage(Component.translatable("squidgame.msg.welcome_back", c.displayNumber()));
        if (t.game != null && t.ctx != null) {
            t.game.onControllerChanged(t.ctx, c);
        }
        syncNumbers(true);
        saveNow();
    }

    public void onPlayerDisconnect(ServerPlayer p) {
        pendingSpectate.removeIf(ps -> ps.player.equals(p.getUUID()));
        spectators.remove(p.getUUID());
        restrictions.forget(p.getUUID());
        if (t == null) {
            return;
        }
        Contestant c = t.roster.ofPlayer(p.getUUID());
        if (c == null || !c.isAlive() || c.isAbsent()) {
            return;
        }
        ServerLevel level = arenaLevel();
        if (level == null || !ArenaWorld.isArena(p.level())) {
            return;
        }
        // an AI stand-in with the same number takes over the body until the player returns
        c.setAbsent(true, tickCounter);
        Vec3 pos = p.position();
        ContestantEntity e = NpcFactory.spawnContestant(level, c, pos, p.getYRot());
        if (e != null) {
            e.setBehavior(new WaitingBehavior());
            if (t.game != null && t.ctx != null) {
                t.game.onControllerChanged(t.ctx, c);
            }
        }
        saveNow();
    }

    /** Absent humans whose grace period ran out are eliminated. */
    private void tickAbsentPlayers() {
        long grace = (long) SquidConfig.get().disconnectGraceSeconds * 20L;
        for (Contestant c : t.roster.alive()) {
            if (c.isHuman() && c.isAbsent() && tickCounter - c.absentSince() > grace) {
                GameContext ctx = t.ctx;
                if (ctx != null && t.phase.inArena) {
                    eliminate(ctx, c, EliminationCause.DISCONNECTED);
                } else {
                    c.markEliminated(t.gameNumber, EliminationCause.DISCONNECTED.id, ++t.eliminationCounter);
                    removeBody(c);
                }
            }
        }
    }

    // =========================================================================================== helpers

    private void fadeHumans(int in, int hold, int out, int argb) {
        for (ServerPlayer p : Announcer.audience(server)) {
            ModNetwork.send(p, new FadePayload(in, hold, out, argb));
        }
    }

    /** Sends the player-number map to every client (tracksuit + bib rendering). */
    public void syncNumbers(boolean force) {
        java.util.Map<UUID, Integer> map = new java.util.LinkedHashMap<>();
        if (t != null) {
            for (Contestant c : t.roster.all()) {
                if (c.playerId != null && (c.isAlive() || c.status() == ContestantStatus.WINNER)) {
                    map.put(c.playerId, c.number);
                }
            }
        }
        int hash = map.hashCode();
        if (!force && hash == lastNumbersHash) {
            return;
        }
        lastNumbersHash = hash;
        NumbersPayload payload = new NumbersPayload(map);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ModNetwork.send(p, payload);
        }
    }
}
