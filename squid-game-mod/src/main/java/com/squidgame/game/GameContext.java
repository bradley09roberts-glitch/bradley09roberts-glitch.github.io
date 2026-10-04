package com.squidgame.game;

import com.squidgame.SquidConfig;
import com.squidgame.build.ArenaId;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import com.squidgame.core.Difficulty;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.NpcBehavior;
import com.squidgame.net.DangerPayload;
import com.squidgame.net.FadePayload;
import com.squidgame.net.ModNetwork;
import com.squidgame.net.OpenScreenPayload;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import com.squidgame.tournament.DoorService;
import com.squidgame.tournament.GuardService;
import com.squidgame.tournament.Teleporter;
import com.squidgame.tournament.Tournament;
import com.squidgame.tournament.TournamentManager;
import com.squidgame.world.ArenaData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The window a {@link MiniGame} gets onto the tournament: roster queries, arena markers, eliminations, announcements,
 * scheduling, guards, doors, NPC behaviours and client UI. One context exists per game instance.
 */
public final class GameContext {
    private record Scheduled(long dueTick, Runnable action) {
    }

    public final TournamentManager manager;
    public final Tournament tournament;
    public final ServerLevel level;
    public final ArenaId arena;
    private final Rng rng;
    private final List<Scheduled> scheduled = new ArrayList<>();
    private GuardService guards;

    public GameContext(TournamentManager manager, Tournament tournament, ServerLevel level, ArenaId arena, Rng rng) {
        this.manager = manager;
        this.tournament = tournament;
        this.level = level;
        this.arena = arena;
        this.rng = rng;
    }

    public MinecraftServer server() {
        return level.getServer();
    }

    public Difficulty difficulty() {
        return tournament.difficulty;
    }

    public Rng rng() {
        return rng;
    }

    public SquidConfig config() {
        return SquidConfig.get();
    }

    /** Server game time in ticks (monotonic). */
    public long now() {
        return level.getGameTime();
    }

    /** Ticks since the GAME phase began. */
    public int gameTicks() {
        return tournament.gameTicks;
    }

    // ------------------------------------------------------------------ arena data

    public ArenaData data() {
        return ArenaData.get(server());
    }

    public List<Marker> markers(String name) {
        return data().markers(arena, name);
    }

    @Nullable
    public Marker marker(String name) {
        return data().marker(arena, name);
    }

    @Nullable
    public Region region(String name) {
        return data().region(arena, name);
    }

    public List<Region> regions(String name) {
        return data().regions(arena, name);
    }

    public static Vec3 pos(Marker m) {
        return new Vec3(m.x(), m.y(), m.z());
    }

    // ------------------------------------------------------------------ roster

    public List<Contestant> alive() {
        return tournament.roster.alive();
    }

    public List<Contestant> aliveHumans() {
        List<Contestant> l = new ArrayList<>();
        for (Contestant c : alive()) {
            if (c.isHumanControlled()) {
                l.add(c);
            }
        }
        return l;
    }

    public List<Contestant> aliveAi() {
        List<Contestant> l = new ArrayList<>();
        for (Contestant c : alive()) {
            if (c.isAiControlled()) {
                l.add(c);
            }
        }
        return l;
    }

    @Nullable
    public Contestant byNumber(int number) {
        return tournament.roster.get(number);
    }

    @Nullable
    public Contestant of(ServerPlayer p) {
        return tournament.roster.ofPlayer(p.getUUID());
    }

    /** The NPC body (pure NPC or stand-in) of a contestant. */
    @Nullable
    public ContestantEntity npc(Contestant c) {
        return c.npc(level);
    }

    /** All alive contestants sorted by distance to a point (bodies that exist only). */
    public List<Contestant> nearestAlive(Vec3 pos, double maxDistance) {
        List<Contestant> out = new ArrayList<>();
        for (Contestant c : alive()) {
            Vec3 p = c.position(level);
            if (p != null && p.distanceToSqr(pos) <= maxDistance * maxDistance) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparingDouble(c -> c.position(level).distanceToSqr(pos)));
        return out;
    }

    // ------------------------------------------------------------------ actions

    /** Eliminates a contestant now. Idempotent: an already-eliminated contestant is ignored. */
    public void eliminate(Contestant c, EliminationCause cause) {
        manager.eliminate(this, c, cause);
    }

    /** Eliminates after {@code delayTicks} (stagger several eliminations for effect). */
    public void eliminate(Contestant c, EliminationCause cause, int delayTicks) {
        if (delayTicks <= 0) {
            manager.eliminate(this, c, cause);
        } else {
            schedule(delayTicks, () -> manager.eliminate(this, c, cause));
        }
    }

    /**
     * Eliminates the contestant with a guard visibly aiming and firing first (falls back to an immediate
     * elimination when no guard is available).
     */
    public void eliminateByGuard(Contestant c, int aimTicks) {
        var body = c.body(level);
        if (body == null || guards == null || guards.list().isEmpty()) {
            eliminate(c, EliminationCause.SHOT);
            return;
        }
        guards.fireAt(body, aimTicks, () -> eliminate(c, EliminationCause.SHOT));
    }

    public void broadcast(Component msg) {
        Announcer.chat(server(), msg);
    }

    public void title(Component title, Component subtitle, int in, int stay, int out) {
        Announcer.title(server(), title, subtitle, in, stay, out);
    }

    public void actionBar(Component msg) {
        Announcer.actionBar(server(), msg);
    }

    /** PA / UI sound for everyone. */
    public void sound(SoundEvent s, float volume, float pitch) {
        Announcer.sound(server(), s, volume, pitch);
    }

    /** Positional world sound. */
    public void soundAt(Vec3 pos, SoundEvent s, SoundSource source, float volume, float pitch) {
        Announcer.soundAt(level, pos, s, source, volume, pitch);
    }

    public void danger(ServerPlayer p, float intensity, int ticks, int pulses, int argb) {
        ModNetwork.send(p, new DangerPayload(intensity, ticks, pulses, argb));
    }

    public void dangerAllHumans(float intensity, int ticks, int pulses, int argb) {
        for (Contestant c : aliveHumans()) {
            ServerPlayer p = c.player(server());
            if (p != null) {
                danger(p, intensity, ticks, pulses, argb);
            }
        }
    }

    public void fade(ServerPlayer p, int in, int hold, int out, int argb) {
        ModNetwork.send(p, new FadePayload(in, hold, out, argb));
    }

    /** Opens / updates / closes a client screen for a player. */
    public void screen(ServerPlayer p, String screen, int action, CompoundTag data) {
        ModNetwork.send(p, new OpenScreenPayload(screen, action, data));
    }

    public void teleport(Contestant c, Vec3 pos, float yaw) {
        Teleporter.teleport(this, c, pos, yaw);
    }

    public void teleport(Contestant c, Marker m) {
        Teleporter.teleport(this, c, new Vec3(m.x(), m.y(), m.z()), m.yaw());
    }

    // ------------------------------------------------------------------ scheduling

    /** Runs {@code action} after {@code delayTicks} server ticks (dropped when the game ends). */
    public void schedule(int delayTicks, Runnable action) {
        scheduled.add(new Scheduled(now() + Math.max(0, delayTicks), action));
    }

    public void tickScheduler() {
        if (scheduled.isEmpty()) {
            return;
        }
        long t = now();
        for (int i = 0; i < scheduled.size(); i++) {
            Scheduled s = scheduled.get(i);
            if (s.dueTick() <= t) {
                scheduled.remove(i--);
                try {
                    s.action().run();
                } catch (RuntimeException e) {
                    com.squidgame.SquidGameMod.LOGGER.error("scheduled game action failed", e);
                }
            }
        }
    }

    public void clearScheduled() {
        scheduled.clear();
    }

    // ------------------------------------------------------------------ guards / doors / NPCs

    public GuardService guards() {
        if (guards == null) {
            guards = new GuardService(level);
        }
        return guards;
    }

    /** Spawns the arena's guards from its markers. */
    public void spawnGuards() {
        guards().spawnFor(arena, data());
    }

    public DoorService doors() {
        return manager.doors();
    }

    /** Assigns a behaviour to every alive AI-controlled contestant, created per NPC by the factory. */
    public void assignBehaviors(java.util.function.Function<Contestant, NpcBehavior> factory) {
        for (Contestant c : aliveAi()) {
            ContestantEntity e = npc(c);
            if (e != null) {
                e.setBehavior(factory.apply(c));
            }
        }
    }

    public void clearBehaviors() {
        for (Contestant c : tournament.roster.all()) {
            ContestantEntity e = c.npc(level);
            if (e != null) {
                e.setBehavior(null);
            }
        }
    }

    public void cleanupGuards() {
        if (guards != null) {
            guards.despawnAll();
            guards = null;
        }
    }
}
