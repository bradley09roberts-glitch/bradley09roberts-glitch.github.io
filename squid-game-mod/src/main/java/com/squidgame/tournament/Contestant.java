package com.squidgame.tournament;

import com.squidgame.core.Appearance;
import com.squidgame.core.ContestantStatus;
import com.squidgame.core.GameKind;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.ContestantEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One tournament participant: a stable identity (number, name, personality, appearance), its life
 * status, and who currently controls the body (a human, or the AI - either an NPC or the auto-pilot
 * stand-in of a disconnected human). Identity and status are persisted; body references are
 * transient and re-resolved from entity UUIDs.
 */
public final class Contestant {
    public final int number;
    public final UUID id;
    public final String name;
    public final Personality personality;
    public final Appearance appearance;
    /** UUID of the human who registered, or null for a pure NPC. */
    @Nullable
    public final UUID playerId;

    private ContestantStatus status = ContestantStatus.ALIVE;
    private int eliminatedInGame = -1;
    private String eliminationCause = "";
    private int eliminationOrder = -1;
    /** Human is offline: an AI stand-in drives the body until they return or the grace period ends. */
    private boolean absent;
    private long absentSinceTick;
    @Nullable
    private UUID bodyEntity;
    private int gamesSurvived;
    private int rank;

    /** Per-game scratch values (cleared when a game starts). Persisted only for the current game's key set. */
    public final Map<String, Double> stats = new HashMap<>();

    private transient ContestantEntity cachedNpc;
    private transient ServerPlayer cachedPlayer;
    /** Per-game transient object (e.g. a Dalgona challenge); never persisted. */
    public transient Object gameState;

    public Contestant(int number, UUID id, String name, Personality personality, Appearance appearance, @Nullable UUID playerId) {
        this.number = number;
        this.id = id;
        this.name = name;
        this.personality = personality;
        this.appearance = appearance;
        this.playerId = playerId;
    }

    public static Contestant createNpc(int number, Rng rng) {
        Rng r = rng.fork(number);
        return new Contestant(number, new UUID(r.seed() ^ number, r.seed() * 31 + number),
                com.squidgame.core.NameGenerator.generate(r), Personality.generate(r), Appearance.generate(r), null);
    }

    public static Contestant createHuman(int number, ServerPlayer player, Rng rng) {
        Rng r = rng.fork(number * 7919L);
        return new Contestant(number, player.getUUID(), player.getGameProfile().getName(),
                Personality.generate(r), Appearance.generate(r), player.getUUID());
    }

    // ---------------------------------------------------------------- identity helpers

    public boolean isHuman() {
        return playerId != null;
    }

    /** True while a real player is online and in control of this contestant. */
    public boolean isHumanControlled() {
        return playerId != null && !absent;
    }

    /** True while AI drives the body (a pure NPC, or a human's stand-in). */
    public boolean isAiControlled() {
        return !isHumanControlled();
    }

    public String displayNumber() {
        return String.format("%03d", number);
    }

    public String label() {
        return "No. " + displayNumber() + " " + name;
    }

    // ---------------------------------------------------------------- status

    public ContestantStatus status() {
        return status;
    }

    public boolean isAlive() {
        return status == ContestantStatus.ALIVE;
    }

    public boolean isEliminated() {
        return status == ContestantStatus.ELIMINATED;
    }

    /** Terminal: once eliminated nothing may bring a contestant back. Returns false if already terminal. */
    public boolean markEliminated(int gameIndex, String cause, int order) {
        if (status != ContestantStatus.ALIVE) {
            return false;
        }
        status = ContestantStatus.ELIMINATED;
        eliminatedInGame = gameIndex;
        eliminationCause = cause;
        eliminationOrder = order;
        return true;
    }

    public boolean markWinner() {
        if (status != ContestantStatus.ALIVE) {
            return false;
        }
        status = ContestantStatus.WINNER;
        return true;
    }

    public int eliminatedInGame() {
        return eliminatedInGame;
    }

    public String eliminationCause() {
        return eliminationCause;
    }

    public int eliminationOrder() {
        return eliminationOrder;
    }

    public int gamesSurvived() {
        return gamesSurvived;
    }

    public void incrementGamesSurvived() {
        gamesSurvived++;
    }

    public int rank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    // ---------------------------------------------------------------- absence (disconnect)

    public boolean isAbsent() {
        return absent;
    }

    public long absentSince() {
        return absentSinceTick;
    }

    public void setAbsent(boolean absent, long tick) {
        this.absent = absent;
        this.absentSinceTick = tick;
    }

    // ---------------------------------------------------------------- body

    @Nullable
    public UUID bodyEntityId() {
        return bodyEntity;
    }

    public void setBodyEntity(@Nullable ContestantEntity e) {
        this.cachedNpc = e;
        this.bodyEntity = e == null ? null : e.getUUID();
    }

    public void setPlayer(@Nullable ServerPlayer p) {
        this.cachedPlayer = p;
    }

    @Nullable
    public ServerPlayer player(MinecraftServer server) {
        if (playerId == null) {
            return null;
        }
        if (cachedPlayer != null && !cachedPlayer.hasDisconnected() && !cachedPlayer.isRemoved()) {
            return cachedPlayer;
        }
        cachedPlayer = server.getPlayerList().getPlayer(playerId);
        return cachedPlayer;
    }

    /** The NPC body (pure NPC or stand-in), or null if none is currently loaded. */
    @Nullable
    public ContestantEntity npc(ServerLevel level) {
        if (cachedNpc != null && !cachedNpc.isRemoved() && cachedNpc.level() == level) {
            return cachedNpc;
        }
        if (bodyEntity != null) {
            Entity e = level.getEntity(bodyEntity);
            if (e instanceof ContestantEntity ce && !ce.isRemoved()) {
                cachedNpc = ce;
                return ce;
            }
        }
        cachedNpc = null;
        return null;
    }

    /** The living entity that physically represents this contestant right now, or null. */
    @Nullable
    public LivingEntity body(ServerLevel level) {
        if (isHumanControlled()) {
            ServerPlayer p = player(level.getServer());
            if (p != null && p.level() == level) {
                return p;
            }
            return null;
        }
        return npc(level);
    }

    @Nullable
    public Vec3 position(ServerLevel level) {
        LivingEntity b = body(level);
        return b == null ? null : b.position();
    }

    // ---------------------------------------------------------------- persistence

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("number", number);
        t.putUUID("id", id);
        t.putString("name", name);
        t.putInt("status", status.ordinal());
        t.putInt("elimGame", eliminatedInGame);
        t.putString("elimCause", eliminationCause);
        t.putInt("elimOrder", eliminationOrder);
        t.putInt("survived", gamesSurvived);
        t.putInt("rank", rank);
        if (playerId != null) {
            t.putUUID("player", playerId);
        }
        t.putBoolean("absent", absent);
        t.putLong("appearance", appearance.pack());
        t.putString("archetype", personality.archetype().name());
        t.putFloat("courage", personality.courage());
        t.putFloat("reaction", personality.reactionSpeed());
        t.putFloat("patience", personality.patience());
        t.putFloat("skill", personality.skill());
        t.putFloat("aggression", personality.aggression());
        t.putFloat("cooperation", personality.cooperation());
        t.putFloat("risk", personality.riskTolerance());
        return t;
    }

    public static Contestant load(CompoundTag t) {
        Personality.Archetype arch;
        try {
            arch = Personality.Archetype.valueOf(t.getString("archetype"));
        } catch (IllegalArgumentException e) {
            arch = Personality.Archetype.ROOKIE;
        }
        Personality p = new Personality(arch, t.getFloat("courage"), t.getFloat("reaction"), t.getFloat("patience"),
                t.getFloat("skill"), t.getFloat("aggression"), t.getFloat("cooperation"), t.getFloat("risk"));
        UUID player = t.hasUUID("player") ? t.getUUID("player") : null;
        Contestant c = new Contestant(t.getInt("number"), t.getUUID("id"), t.getString("name"), p,
                Appearance.unpack(t.getLong("appearance")), player);
        int st = t.getInt("status");
        c.status = ContestantStatus.values()[Math.max(0, Math.min(ContestantStatus.values().length - 1, st))];
        c.eliminatedInGame = t.getInt("elimGame");
        c.eliminationCause = t.getString("elimCause");
        c.eliminationOrder = t.getInt("elimOrder");
        c.gamesSurvived = t.getInt("survived");
        c.rank = t.getInt("rank");
        c.absent = t.getBoolean("absent");
        return c;
    }

    @Override
    public String toString() {
        return label() + " " + status;
    }

    /** Convenience stat accessors. */
    public double stat(String key, double fallback) {
        return stats.getOrDefault(key, fallback);
    }

    public void clearGameScratch(GameKind nextGame) {
        stats.clear();
        gameState = null;
    }
}
