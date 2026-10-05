package com.squidgame.game.dalgona;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.squidgame.SquidGameMod;
import com.squidgame.build.Marker;
import com.squidgame.core.Difficulty;
import com.squidgame.core.GameKind;
import com.squidgame.core.dalgona.CookieSim;
import com.squidgame.core.dalgona.DalgonaRules;
import com.squidgame.core.dalgona.DalgonaShape;
import com.squidgame.core.dalgona.NpcCarver;
import com.squidgame.core.dalgona.StrokeValidator;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.game.EliminationCause;
import com.squidgame.game.GameContext;
import com.squidgame.game.GameResult;
import com.squidgame.game.MiniGame;
import com.squidgame.net.HudPayload;
import com.squidgame.net.ModNetwork;
import com.squidgame.net.OpenScreenPayload;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import com.squidgame.tournament.DisplayUtil;
import com.squidgame.tournament.Teleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Game 2: Dalgona (the honeycomb). Everybody sits at a desk with a tin, picks one blind, and has to carve the pressed
 * shape out of the cookie with a needle without cracking it before time runs out. A cracked cookie or the end of the
 * clock means a guard fires.
 *
 * <p>The whole simulation is {@link CookieSim}: humans send needle strokes from the carving screen (validated by a
 * {@link StrokeValidator}, applied to the contestant's own cookie), NPCs carve with {@link NpcCarver} (driven by
 * {@link DalgonaNpcBehavior}) - same physics, same limits. The server is the only source of truth: the client's
 * screen shows what {@link DalgonaNet.StatePayload} says.
 *
 * <p>Seating: every contestant is placed on a {@code dalgona.seat} marker (see {@link SeatAnchor}); NPCs sit with the
 * seated animations, humans ride an invisible seat so that they stay put and are seen sitting.
 */
public final class DalgonaGame implements MiniGame {
    /** Ticks a human sees the cookie break before the guard fires. */
    private static final int HUMAN_FAIL_DELAY = 50;
    private static final int NPC_FAIL_DELAY = 22;
    /** Ticks a human sees "time is up" before the guard fires. */
    private static final int HUMAN_TIMEOUT_DELAY = 36;
    /** Minimum ticks between two guard shots (they are staggered so that each one reads). */
    private static final int SHOT_SPACING = 8;
    private static final int PUSH_GAP = 3;
    private static final int HEARTBEAT = 10;
    private static final String TAG_TEMP = "squidgame_temp";

    enum Stage {
        /** Seated, waiting for the game to start. */
        WAITING,
        /** Choosing a tin. */
        SELECTING,
        /** Carving the cookie. */
        CARVING,
        /** The shape is free: safe. */
        DONE,
        /** The cookie cracked. */
        BROKEN,
        /** The clock ran out. */
        TIMED_OUT,
        /** Eliminated by something else (admin, disconnect). */
        OUT
    }

    /** Everything the game keeps about one contestant. */
    static final class Slot {
        final Contestant c;
        final int index;
        SeatAnchor anchor;
        Stage stage = Stage.WAITING;
        DalgonaShape plannedShape;
        DalgonaShape shape;
        long cookieSeed;
        CookieSim sim;
        NpcCarver carver;
        final StrokeValidator validator = new StrokeValidator();
        long pickAt;
        long lastStep;
        long lastPush;
        boolean dirty;
        int pendingEvents;
        boolean condemned;
        /** Game tick (GameContext.now) at which the guard fires at this contestant, once condemned. */
        long shotAt;
        Entity seatEntity;
        long lastReseat;

        Slot(Contestant c, int index) {
            this.c = c;
            this.index = index;
        }
    }

    private GameContext ctx;
    private Difficulty difficulty = Difficulty.NORMAL;
    private DalgonaRules.Params params = DalgonaRules.params(Difficulty.NORMAL);
    private Rng rng = new Rng(0);
    private final Map<Integer, Slot> slots = new LinkedHashMap<>();
    private boolean placed;
    private boolean started;
    private long clock;
    private long selectionEnd;
    private long lastShotAt;
    private long realOrigin = System.nanoTime();
    private int doneCount;
    private int crackedCount;
    private int timedOutCount;
    @Nullable
    private Display.TextDisplay board;
    private String boardShown = "";

    // ------------------------------------------------------------------ access for the NPC behaviour

    @Nullable
    Slot slotOf(Contestant c) {
        return slots.get(c.number);
    }

    @Nullable
    GameContext context() {
        return ctx;
    }

    long clock() {
        return clock;
    }

    Difficulty difficulty() {
        return difficulty;
    }

    DalgonaRules.Params params() {
        return params;
    }

    /** Ticks left on the game clock (what a contestant reads off the timer). */
    int ticksLeft() {
        return ctx == null ? 0 : ctx.tournament.remainingPhaseTicks();
    }

    // ------------------------------------------------------------------ MiniGame: text

    @Override
    public GameKind type() {
        return GameKind.DALGONA;
    }

    @Override
    public List<Component> instructions(GameContext ctx) {
        DalgonaRules.Params p = DalgonaRules.params(ctx.difficulty());
        return List.of(
                Component.translatable("squidgame.game.dalgona.instruction.1"),
                Component.translatable("squidgame.game.dalgona.instruction.2"),
                Component.translatable("squidgame.game.dalgona.instruction.3"),
                Component.translatable("squidgame.game.dalgona.instruction.4", p.licks()),
                Component.translatable("squidgame.game.dalgona.instruction.5", p.carveSeconds(), (int) Math.round(p.successThreshold() * 100)),
                Component.translatable("squidgame.game.dalgona.instruction.6", Component.translatable(ctx.difficulty().translationKey())));
    }

    @Override
    public Component objective(GameContext ctx, @Nullable Contestant viewer) {
        Slot s = viewer == null ? null : slots.get(viewer.number);
        if (s != null) {
            switch (s.stage) {
                case SELECTING -> {
                    return Component.translatable("squidgame.game.dalgona.objective.select");
                }
                case CARVING -> {
                    return Component.translatable("squidgame.game.dalgona.objective.carve", Component.translatable(s.shape.translationKey()));
                }
                case DONE -> {
                    return Component.translatable("squidgame.game.dalgona.objective.safe");
                }
                case BROKEN, TIMED_OUT -> {
                    return Component.translatable("squidgame.game.dalgona.objective.failed");
                }
                default -> {
                }
            }
        }
        return Component.translatable("squidgame.game.dalgona.objective");
    }

    @Override
    public int timeLimitTicks(GameContext ctx) {
        return DalgonaRules.timeLimitTicks(ctx.difficulty());
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void prepare(GameContext ctx) {
        this.ctx = ctx;
        difficulty = ctx.difficulty();
        params = DalgonaRules.params(difficulty);
        rng = ctx.rng().fork(0xDA16);
        ctx.spawnGuards();

        List<Marker> seats = new ArrayList<>(ctx.markers("dalgona.seat"));
        seats.sort(Comparator.comparingInt(m -> m.getInt("slot", 0)));
        Map<Integer, Marker> stations = new HashMap<>();
        for (Marker m : ctx.markers("dalgona.station")) {
            stations.put(m.getInt("slot", -1), m);
        }
        List<Contestant> alive = ctx.alive();
        if (seats.isEmpty() && !alive.isEmpty()) {
            SquidGameMod.LOGGER.warn("Dalgona: the arena has no dalgona.seat markers");
        }
        // more contestants than seats: they share a seat and sit side by side on the bench (two per seat: 0.9 apart)
        int perSeat = seats.isEmpty() ? 1 : (alive.size() + seats.size() - 1) / seats.size();
        double spread = perSeat <= 1 ? 0.0 : Math.min(0.9, 1.8 / perSeat);
        for (int i = 0; i < alive.size(); i++) {
            Contestant c = alive.get(i);
            Slot s = new Slot(c, i);
            Marker seat = seats.isEmpty() ? fallbackSeat(ctx, i) : seats.get(i % seats.size());
            Marker station = stations.get(seat.getInt("slot", i % Math.max(1, seats.size())));
            double jitter = seats.isEmpty() ? 0.0 : ((i / seats.size()) - (perSeat - 1) / 2.0) * spread;
            s.anchor = SeatAnchor.resolve(ctx.level, seat, station, jitter);
            slots.put(c.number, s);
        }
        spawnBoard();
        SquidGameMod.LOGGER.info("Dalgona prepared: {} contestants, {} seats, difficulty {}", slots.size(), seats.size(), difficulty.id);
    }

    private static Marker fallbackSeat(GameContext ctx, int i) {
        Marker base = ctx.marker("arena.exit");
        double bx = base == null ? 0.5 : base.x();
        double by = base == null ? 65 : base.y();
        double bz = base == null ? 0.5 : base.z();
        return new Marker("dalgona.seat", bx + (i % 16) * 1.5 - 12, by, bz + (i / 16) * 1.5, 180f, "slot=" + i);
    }

    @Override
    public void placeContestants(GameContext ctx) {
        placed = true;
        for (Slot s : slots.values()) {
            if (s.c.isAlive()) {
                place(s);
            }
        }
        ctx.assignBehaviors(c -> new DalgonaNpcBehavior(this, c));
    }

    private void place(Slot s) {
        if (s.c.isHumanControlled()) {
            ServerPlayer p = s.c.player(ctx.server());
            if (p != null) {
                ctx.teleport(s.c, s.anchor.humanSeat(), s.anchor.yaw());
                mountSeat(s, p);
            }
        } else {
            ContestantEntity e = ctx.npc(s.c);
            if (e != null) {
                seatNpc(s, e);
            }
        }
    }

    /**
     * Sits an NPC body on its seat: no gravity (it hovers where the bench is), seated pose, facing the table. The body
     * origin sits inside the 0.5 high bench slab (the sit animation rests the thighs 0.23 above it), so the navigation
     * is only stopped, never {@code stopMoving()}: that would make vanilla's move control "jump out of the block".
     */
    private void seatNpc(Slot s, ContestantEntity e) {
        e.setBehavior(null);
        e.getNavigation().stop();
        e.setNoGravity(true);
        e.teleportSafely(s.anchor.npcPos());
        e.faceYaw(s.anchor.yaw());
        e.setActivity(Activity.DALGONA_SIT);
        e.setHeldItem(0);
        e.lookAtPos(s.anchor.tableTop().add(0, 0.2, 0));
    }

    @Override
    public void begin(GameContext ctx) {
        this.ctx = ctx;
        started = true;
        clock = 0;
        lastShotAt = 0;
        realOrigin = System.nanoTime();
        int selection = DalgonaRules.selectionTicks(ctx.config().timeScale);
        selectionEnd = selection;
        DalgonaShape[] shapes = DalgonaRules.assignShapes(rng.fork(5), difficulty, slots.size());
        int i = 0;
        for (Slot s : slots.values()) {
            s.plannedShape = shapes[i++];
            s.cookieSeed = rng.fork(1000 + s.c.number).seed();
            s.stage = Stage.SELECTING;
            // NPCs take a moment to decide, one to eight seconds in
            s.pickAt = 20 + rng.fork(2000 + s.c.number).nextInt(Math.max(1, Math.min(selection - 10, 140)));
            if (s.c.isHumanControlled()) {
                openTins(s);
            }
        }
        ctx.title(Component.translatable("squidgame.game.dalgona.title.select"), Component.translatable("squidgame.game.dalgona.subtitle.select"), 5, 50, 15);
        SquidGameMod.LOGGER.info("Dalgona started: {} contestants pick a tin (selection {} ticks)", slots.size(), selection);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick(GameContext ctx) {
        clock++;
        for (Slot s : slots.values()) {
            Contestant c = s.c;
            if (!c.isAlive()) {
                continue;
            }
            switch (s.stage) {
                case SELECTING -> {
                    boolean human = c.isHumanControlled();
                    if (clock >= selectionEnd || (!human && clock >= s.pickAt)) {
                        pick(s);
                    }
                }
                case CARVING -> tickCarving(s);
                default -> {
                }
            }
            if (c.isHumanControlled() && s.stage != Stage.OUT) {
                holdSeat(s);
            }
        }
        if (clock % 10 == 0) {
            updateBoard();
        }
        int left = ticksLeft();
        if (left > 0 && left <= 200 && left % 20 == 0) {
            clockCue(left <= 60);
        }
    }

    /** A ticking clock for everybody who is still carving during the last ten seconds, beeping in the last three. */
    private void clockCue(boolean beep) {
        for (Slot s : slots.values()) {
            if (s.stage == Stage.CARVING && s.c.isHumanControlled() && s.c.isAlive()) {
                ServerPlayer p = s.c.player(ctx.server());
                if (p != null) {
                    Announcer.sound(p, beep ? ModSounds.COUNTDOWN_BEEP : ModSounds.COUNTDOWN_TICK, 0.7f, beep ? 1.25f : 1.0f);
                }
            }
        }
    }

    private void tickCarving(Slot s) {
        CookieSim sim = s.sim;
        sim.tick();
        if (sim.isDone()) {
            finish(s);
        } else if (sim.isCracked()) {
            crack(s);
        } else if (s.c.isHumanControlled() && (s.dirty && clock - s.lastPush >= PUSH_GAP || clock - s.lastPush >= HEARTBEAT)) {
            pushState(s);
        }
    }

    /** The contestant opens a tin: its shape is fixed, the cookie simulation starts. */
    private void pick(Slot s) {
        if (s.stage != Stage.SELECTING) {
            return;
        }
        s.shape = s.plannedShape;
        s.sim = new CookieSim(s.shape, params, s.cookieSeed);
        s.stage = Stage.CARVING;
        s.c.stats.put("dalgonaShape", (double) s.shape.ordinal());
        if (s.c.isHumanControlled()) {
            SquidGameMod.LOGGER.info("Dalgona: {} opened a tin: {}", s.c.label(), s.shape.name().toLowerCase());
            openCookie(s, true);
        } else {
            startNpc(s);
        }
    }

    /** Gives an AI-controlled contestant its carving hands (a stand-in continues from the cookie's current state). */
    private void startNpc(Slot s) {
        Rng r = new Rng(s.cookieSeed).fork(77);
        NpcCarver.Profile profile = NpcCarver.Profile.of(s.c.personality, difficulty, r.fork(1));
        s.carver = new NpcCarver(s.shape, s.sim, profile, r.fork(2));
        if (s.sim.hasNeedle() || s.sim.carvedCount() > 0) {
            s.carver.resumeNear(s.sim.needleX(), s.sim.needleY());
        }
        s.lastStep = clock;
        ContestantEntity e = ctx.npc(s.c);
        if (e != null) {
            e.setHeldItem(1);
        }
    }

    // ------------------------------------------------------------------ outcomes

    private void finish(Slot s) {
        s.stage = Stage.DONE;
        doneCount++;
        s.c.stats.put("dalgonaFinish", (double) clock);
        Vec3 at = s.anchor.tableTop();
        ctx.soundAt(at, ModSounds.UI_NUMBER_CALL, SoundSource.PLAYERS, 1.0f, 1.25f);
        ctx.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.3, at.z, 10, 0.25, 0.15, 0.25, 0.0);
        ctx.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.4, at.z, 8, 0.2, 0.2, 0.2, 0.03);
        ServerPlayer p = s.c.isHumanControlled() ? s.c.player(ctx.server()) : null;
        if (p != null) {
            pushState(s);
            Announcer.sound(p, ModSounds.UI_CONFIRM, 1f, 1.2f);
            p.displayClientMessage(Component.translatable("squidgame.game.dalgona.safe", doneCount), true);
            ctx.schedule(70, () -> closeScreens(s));
        }
        ContestantEntity e = ctx.npc(s.c);
        if (e != null && s.c.isAiControlled()) {
            e.triggerAction("dalgona_success");
            e.setActivity(Activity.SIT_IDLE);
            e.setHeldItem(0);
        }
    }

    private void crack(Slot s) {
        s.stage = Stage.BROKEN;
        crackedCount++;
        s.c.stats.put("dalgonaCracked", (double) clock);
        Vec3 at = s.anchor.tableTop();
        ctx.soundAt(at, ModSounds.DALGONA_SNAP, SoundSource.PLAYERS, 1.4f, 1.0f);
        crumbs(at, 24);
        ServerPlayer p = s.c.isHumanControlled() ? s.c.player(ctx.server()) : null;
        if (p != null) {
            pushState(s);
            ctx.danger(p, 0.7f, 50, 3, 0xFFB01010);
        }
        ContestantEntity e = ctx.npc(s.c);
        if (e != null && s.c.isAiControlled()) {
            e.triggerAction("dalgona_crack");
            e.setActivity(Activity.DALGONA_FAIL);
            e.setHeldItem(0);
        }
        shoot(s, EliminationCause.BROKE_COOKIE, p != null ? HUMAN_FAIL_DELAY : NPC_FAIL_DELAY + rng.nextInt(20));
    }

    private void crumbs(Vec3 at, int count) {
        ctx.level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.HONEYCOMB)), at.x, at.y + 0.2, at.z,
                count, 0.25, 0.12, 0.25, 0.08);
        ctx.level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.3, at.z, 6, 0.2, 0.1, 0.2, 0.15);
    }

    /** A guard fires at this contestant, staggered after the previous shot. */
    private void shoot(Slot s, EliminationCause cause, int baseDelay) {
        if (s.condemned) {
            return;
        }
        s.condemned = true;
        long now = ctx.now();
        long at = Math.max(now + baseDelay, lastShotAt + SHOT_SPACING);
        lastShotAt = at;
        s.shotAt = at;
        ctx.schedule((int) (at - now), () -> fire(s.c, cause));
    }

    /** A guard aims at the contestant and fires (without a guard the elimination is immediate). */
    private void fire(Contestant c, EliminationCause cause) {
        if (!c.isAlive()) {
            return;
        }
        LivingEntity body = c.body(ctx.level);
        if (body == null || ctx.guards().list().isEmpty()) {
            ctx.eliminate(c, cause);
            return;
        }
        ctx.guards().fireAt(body, 6 + ctx.rng().nextInt(6), () -> ctx.eliminate(c, cause));
    }

    @Override
    public boolean isFinished(GameContext ctx) {
        if (!started) {
            return false;
        }
        for (Contestant c : ctx.alive()) {
            Slot s = slots.get(c.number);
            if (s != null && (s.stage == Stage.WAITING || s.stage == Stage.SELECTING || s.stage == Stage.CARVING)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onTimeout(GameContext ctx) {
        ctx.title(Component.translatable("squidgame.game.dalgona.time_up"), Component.empty(), 0, 30, 10);
        for (Slot s : slots.values()) {
            if (s.c.isAlive() && (s.stage == Stage.SELECTING || s.stage == Stage.CARVING)) {
                s.stage = Stage.TIMED_OUT;
                timedOutCount++;
                if (s.c.isHumanControlled() && s.sim != null) {
                    pushState(s);
                } else if (s.c.isHumanControlled()) {
                    closeScreens(s);
                }
            }
        }
    }

    @Override
    public GameResult conclude(GameContext ctx) {
        List<Contestant> out = new ArrayList<>();
        for (Contestant c : ctx.alive()) {
            Slot s = slots.get(c.number);
            if (s == null || s.stage != Stage.DONE) {
                out.add(c);
            }
        }
        // The guards deal with everyone who did not make it, one after another, all within the eliminations phase.
        // Cookies that cracked earlier already have their shot queued (a human gets time to watch the cookie break);
        // the rest are lined up behind those shots.
        // The volley has to fit into the eliminations phase (six seconds at time scale 1) minus the time a shot takes.
        int window = Math.max(8, ctx.config().ticks(6) - 16);
        int gap = Math.max(1, Math.min(SHOT_SPACING, window / 12));
        List<Contestant> fresh = new ArrayList<>();
        long now = ctx.now();
        long queuedUntil = now - gap;
        for (Contestant c : out) {
            Slot s = slots.get(c.number);
            if (s != null && s.condemned && s.shotAt <= now + window) {
                queuedUntil = Math.max(queuedUntil, s.shotAt);
            } else {
                // not condemned yet, or queued so far behind that it would miss the eliminations phase
                fresh.add(c);
            }
        }
        long free = Math.max(now, queuedUntil + gap);
        int room = (int) Math.max(1, window - (free - now));
        int spacing = Math.max(1, Math.min(gap, room / Math.max(1, fresh.size())));
        for (int i = 0; i < fresh.size(); i++) {
            Contestant c = fresh.get(i);
            Slot s = slots.get(c.number);
            if (s != null) {
                s.condemned = true;
            }
            EliminationCause cause = s != null && s.stage == Stage.BROKEN ? EliminationCause.BROKE_COOKIE : EliminationCause.TIMEOUT;
            long at = free + (long) i * spacing;
            if (c.isHumanControlled()) {
                // a human gets a moment to read "time is up" before the guard fires
                at = Math.max(at, now + Math.min(HUMAN_TIMEOUT_DELAY, window / 2));
            }
            at = Math.min(at, now + window);
            if (s != null) {
                s.shotAt = at;
            }
            ctx.schedule((int) Math.max(0, at - now), () -> fire(c, cause));
        }
        if (!fresh.isEmpty()) {
            lastShotAt = Math.max(lastShotAt, free + (long) (fresh.size() - 1) * spacing);
        }
        List<Contestant> survivors = new ArrayList<>();
        for (Contestant c : ctx.alive()) {
            Slot s = slots.get(c.number);
            if (s != null && s.stage == Stage.DONE) {
                survivors.add(c);
            }
        }
        logOutcome();
        return new GameResult(survivors, out, Component.translatable("squidgame.game.dalgona.headline", survivors.size()),
                Component.translatable("squidgame.game.dalgona.detail", crackedCount, timedOutCount));
    }

    private void logOutcome() {
        Map<DalgonaShape, int[]> perShape = new EnumMap<>(DalgonaShape.class);
        for (Slot s : slots.values()) {
            if (s.shape != null) {
                int[] t = perShape.computeIfAbsent(s.shape, k -> new int[2]);
                t[1]++;
                if (s.stage == Stage.DONE) {
                    t[0]++;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        perShape.forEach((k, v) -> sb.append(' ').append(k.id).append(' ').append(v[0]).append('/').append(v[1]));
        SquidGameMod.LOGGER.info("Dalgona result: {} freed, {} cracked, {} out of time;{}", doneCount, crackedCount, timedOutCount, sb);
    }

    @Override
    public void cleanup(GameContext ctx) {
        started = false;
        placed = false;
        for (Slot s : slots.values()) {
            discardSeat(s);
            if (s.c.isHumanControlled()) {
                closeScreens(s);
            }
            ContestantEntity e = this.ctx == null ? null : this.ctx.npc(s.c);
            if (e != null) {
                e.setNoGravity(false);
                e.setHeldItem(0);
                if (!e.getActivity().isDown()) {
                    e.setActivity(Activity.NONE);
                }
            }
        }
        slots.clear();
        if (board != null) {
            board.discard();
            board = null;
        }
        boardShown = "";
        doneCount = 0;
        crackedCount = 0;
        timedOutCount = 0;
        if (this.ctx != null) {
            this.ctx.cleanupGuards();
        }
    }

    // ------------------------------------------------------------------ controller changes / eliminations

    @Override
    public void onControllerChanged(GameContext ctx, Contestant c) {
        Slot s = slots.get(c.number);
        if (s == null || !placed) {
            return;
        }
        s.validator.reset();
        if (c.isAiControlled()) {
            // the human left: an AI stand-in takes the seat and carries on with the same cookie
            discardSeat(s);
            ContestantEntity e = ctx.npc(c);
            if (e != null && c.isAlive()) {
                seatNpc(s, e);
                if (s.stage == Stage.CARVING) {
                    startNpc(s);
                } else if (s.stage == Stage.DONE) {
                    e.setActivity(Activity.SIT_IDLE);
                } else if (s.stage == Stage.BROKEN || s.stage == Stage.TIMED_OUT) {
                    e.setActivity(Activity.DALGONA_FAIL);
                }
                e.setBehavior(new DalgonaNpcBehavior(this, c));
            }
        } else if (c.isAlive()) {
            // the human is back: seat them again and show the cookie as it is
            place(s);
            s.carver = null;
            switch (s.stage) {
                case SELECTING -> openTins(s);
                case CARVING, DONE, BROKEN, TIMED_OUT -> {
                    if (s.sim != null) {
                        openCookie(s, false);
                    }
                }
                default -> {
                }
            }
        }
    }

    @Override
    public void onContestantEliminated(GameContext ctx, Contestant c, EliminationCause cause) {
        Slot s = slots.get(c.number);
        if (s == null) {
            return;
        }
        if (s.stage == Stage.WAITING || s.stage == Stage.SELECTING || s.stage == Stage.CARVING || s.stage == Stage.DONE) {
            s.stage = cause == EliminationCause.BROKE_COOKIE ? Stage.BROKEN : cause == EliminationCause.TIMEOUT ? Stage.TIMED_OUT : Stage.OUT;
        }
        s.condemned = true;
        closeScreens(s);
        discardSeat(s);
        ContestantEntity e = ctx.npc(c);
        if (e != null) {
            // the body falls down in front of the bench instead of lying on it
            e.setNoGravity(false);
            e.setHeldItem(0);
            e.teleportSafely(s.anchor.floorPos());
            e.faceYaw(s.anchor.yaw());
        }
    }

    // ------------------------------------------------------------------ human input

    @Override
    public void onClientAction(GameContext ctx, Contestant c, ServerPlayer player, String id, CompoundTag data) {
        Slot s = slots.get(c.number);
        if (!started || s == null || !c.isAlive() || !c.isHumanControlled()) {
            return;
        }
        switch (id) {
            case "dalgona.pick" -> onPick(s, data);
            case "dalgona.stroke" -> onStroke(s, player, data);
            case "dalgona.lick" -> onLick(s, player);
            case "dalgona.sync" -> {
                if (s.stage == Stage.SELECTING) {
                    openTins(s);
                } else if (s.sim != null) {
                    pushState(s);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Real time in ticks of 50 ms since the game began (offset so that it never reads as zero or negative). The stroke
     * validator credits what an honest client can have done in the time that really passed: counting game ticks would
     * punish a client whenever the server lags and its messages arrive in a bunch.
     */
    private long realTicks() {
        return (System.nanoTime() - realOrigin) / 50_000_000L + 1000L;
    }

    private void onPick(Slot s, CompoundTag data) {
        if (s.stage != Stage.SELECTING) {
            return;
        }
        int tin = data.getInt("tin");
        if (tin < 0 || tin >= DalgonaRules.TIN_COUNT) {
            return;
        }
        // the choice is blind: whatever tin was clicked, the shape is the one the draw gave this contestant
        pick(s);
    }

    private void onStroke(Slot s, ServerPlayer player, CompoundTag data) {
        if (s.stage != Stage.CARVING || s.sim == null) {
            return;
        }
        int[] pts = data.getIntArray("p");
        int flags = data.getByte("f");
        StrokeValidator.Verdict v = s.validator.check(realTicks(), pts, pts.length, data.getInt("t"), (flags & 1) != 0, (flags & 2) != 0,
                s.sim.hasNeedle(), s.sim.needleX(), s.sim.needleY());
        if (v.tamper) {
            SquidGameMod.LOGGER.warn("Dalgona: {} keeps sending invalid strokes; the cookie breaks", s.c.label());
            s.sim.crack();
        }
        if (v.accepted) {
            if (v.penalty > 0) {
                s.sim.penalize(v.penalty);
                s.pendingEvents |= DalgonaNet.E_PENALTY;
            }
            CookieSim.Result r = s.sim.stroke(v.xs, v.ys, v.n, v.dt, v.newStroke);
            if (r.spike) {
                s.pendingEvents |= DalgonaNet.E_SPIKE;
            }
        }
        s.dirty = true;
        if (s.sim.isDone()) {
            finish(s);
        } else if (s.sim.isCracked()) {
            crack(s);
        }
    }

    private void onLick(Slot s, ServerPlayer player) {
        if (s.stage != Stage.CARVING || s.sim == null) {
            return;
        }
        if (s.sim.lick()) {
            s.pendingEvents |= DalgonaNet.E_LICK;
            lickEffects(s);
            pushState(s);
        } else {
            Announcer.sound(player, ModSounds.UI_DENY, 0.6f, 1.0f);
            pushState(s);
        }
    }

    /** Sound and sparkle of a lick at the contestant's desk (heard by whoever sits close). */
    void lickEffects(Slot s) {
        Vec3 at = s.anchor.tableTop();
        ctx.soundAt(at, ModSounds.UI_SELECT, SoundSource.PLAYERS, 0.6f, 0.55f);
        ctx.level.sendParticles(ParticleTypes.FALLING_HONEY, at.x, at.y + 0.5, at.z, 4, 0.12, 0.05, 0.12, 0.0);
    }

    // ------------------------------------------------------------------ screens and state

    private void openTins(Slot s) {
        ServerPlayer p = s.c.player(ctx.server());
        if (p == null) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("left", (int) Math.max(0, selectionEnd - clock));
        t.putInt("total", (int) Math.max(1, selectionEnd));
        t.putLong("seed", s.cookieSeed);
        ctx.screen(p, "dalgona_tins", OpenScreenPayload.OPEN, t);
    }

    private void openCookie(Slot s, boolean intro) {
        ServerPlayer p = s.c.player(ctx.server());
        if (p == null || s.sim == null) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("shape", s.shape.ordinal());
        t.putInt("difficulty", difficulty.ordinal());
        t.putLong("seed", s.cookieSeed);
        t.putBoolean("intro", intro);
        ctx.screen(p, "dalgona", OpenScreenPayload.OPEN, t);
        pushState(s);
    }

    private void closeScreens(Slot s) {
        ServerPlayer p = s.c.player(ctx.server());
        if (p != null) {
            ctx.screen(p, "dalgona", OpenScreenPayload.CLOSE, new CompoundTag());
            ctx.screen(p, "dalgona_tins", OpenScreenPayload.CLOSE, new CompoundTag());
        }
    }

    private void pushState(Slot s) {
        ServerPlayer p = s.c.player(ctx.server());
        if (p == null || s.sim == null) {
            return;
        }
        CookieSim sim = s.sim;
        int flags = 0;
        if (sim.isCracked()) {
            flags |= DalgonaNet.F_CRACKED;
        }
        if (sim.isDone()) {
            flags |= DalgonaNet.F_DONE;
        }
        if (s.stage == Stage.TIMED_OUT) {
            flags |= DalgonaNet.F_TIMEOUT;
        }
        ModNetwork.send(p, new DalgonaNet.StatePayload((float) sim.stress(), sim.carvedCount(), sim.licksLeft(), sim.lickCooldown(),
                sim.lickLock(), flags, s.pendingEvents, ctx.tournament.remainingPhaseTicks(), ctx.tournament.phaseLength, sim.carvedBits()));
        s.pendingEvents = 0;
        s.dirty = false;
        s.lastPush = clock;
    }

    @Override
    public boolean onBlockUse(GameContext ctx, Contestant c, ServerPlayer player, BlockPos pos) {
        Slot s = slots.get(c.number);
        if (!started || s == null || !c.isAlive() || !c.isHumanControlled()) {
            return false;
        }
        BlockPos mine = s.anchor.stationBlock();
        boolean own = mine != null ? mine.equals(pos) : pos.distSqr(BlockPos.containing(s.anchor.tableTop())) < 4.0;
        if (!own) {
            Announcer.sound(player, ModSounds.UI_DENY, 0.6f, 1.0f);
            player.displayClientMessage(Component.translatable("squidgame.game.dalgona.not_yours"), true);
            return true;
        }
        switch (s.stage) {
            case SELECTING -> openTins(s);
            case CARVING, DONE, BROKEN, TIMED_OUT -> {
                if (s.sim != null) {
                    openCookie(s, false);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ seating of humans

    private void mountSeat(Slot s, ServerPlayer p) {
        discardSeat(s);
        Entity seat = spawnSeat(ctx.level, s.anchor.humanSeat());
        if (seat != null && p.startRiding(seat, true)) {
            s.seatEntity = seat;
            // the client answers boarding with "Press Shift to dismount": replace that hint with an empty message
            p.displayClientMessage(Component.empty(), true);
        } else if (seat != null) {
            seat.discard();
        }
        s.lastReseat = clock;
    }

    /** Keeps a human on their seat: re-seats them if they got off (sneaking, teleports), pins them if no seat exists. */
    private void holdSeat(Slot s) {
        ServerPlayer p = s.c.player(ctx.server());
        if (p == null || p.level() != ctx.level) {
            return;
        }
        if (s.seatEntity != null && !s.seatEntity.isRemoved()) {
            if (p.getVehicle() != s.seatEntity && clock - s.lastReseat >= 6) {
                mountSeat(s, p);
            }
        } else if (p.position().distanceToSqr(s.anchor.floorPos()) > 0.6 * 0.6 && clock - s.lastReseat >= 6) {
            s.lastReseat = clock;
            Teleporter.teleport(ctx, s.c, s.anchor.floorPos(), s.anchor.yaw());
        }
    }

    private void discardSeat(Slot s) {
        if (s.seatEntity != null) {
            s.seatEntity.ejectPassengers();
            s.seatEntity.discard();
            s.seatEntity = null;
        }
    }

    @Nullable
    private static Entity spawnSeat(ServerLevel level, Vec3 pos) {
        try {
            CompoundTag tag = TagParser.parseTag("{id:\"minecraft:armor_stand\",Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,"
                    + "Tags:[\"" + TAG_TEMP + "\",\"squidgame_seat\"]}");
            Entity e = EntityType.loadEntityRecursive(tag, level, entity -> {
                entity.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
                return entity;
            });
            if (e != null) {
                level.addFreshEntity(e);
            }
            return e;
        } catch (CommandSyntaxException | RuntimeException ex) {
            SquidGameMod.LOGGER.error("Dalgona: could not spawn a seat: {}", ex.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ HUD and chalkboard

    @Override
    public void hudWidgets(GameContext ctx, @Nullable Contestant viewer, List<HudPayload.Widget> out) {
        if (!started) {
            return;
        }
        Slot s = viewer == null ? null : slots.get(viewer.number);
        if (s != null && s.stage == Stage.CARVING && s.sim != null && viewer.isAlive()) {
            double stress = s.sim.stress();
            out.add(HudPayload.Widget.bar("stress", Component.translatable("squidgame.game.dalgona.hud.stress"), (float) stress, 100f, stressColor(stress)));
            out.add(HudPayload.Widget.counter("licks", "icon_lick", Component.translatable("squidgame.game.dalgona.hud.licks"), s.sim.licksLeft(), params.licks()));
            out.add(HudPayload.Widget.counter("carved", "icon_circle", Component.translatable("squidgame.game.dalgona.hud.carved"), Math.round(s.sim.progress() * 100), 100));
            if (stress >= 75) {
                out.add(HudPayload.Widget.banner("danger", Component.translatable("squidgame.game.dalgona.banner.danger"), 0xFF4040));
            }
        }
        out.add(HudPayload.Widget.line("finished", Component.translatable("squidgame.game.dalgona.hud.finished", doneCount, slots.size())));
    }

    private static int stressColor(double stress) {
        return stress < 40 ? 0x50D890 : stress < 70 ? 0xFFC040 : 0xFF4040;
    }

    private void spawnBoard() {
        Marker bm = ctx.marker("dalgona.board");
        if (bm == null) {
            return;
        }
        String json = boardJson();
        board = DisplayUtil.spawnText(ctx.level, new Vec3(bm.x(), bm.y() - 0.9, bm.z() + 0.06), bm.yaw(), json, 1.5f, false, TAG_TEMP);
        boardShown = json;
    }

    private void updateBoard() {
        if (board == null || board.isRemoved()) {
            return;
        }
        String json = boardJson();
        if (!json.equals(boardShown)) {
            DisplayUtil.setText(board, json);
            boardShown = json;
        }
    }

    private String boardJson() {
        Component text = Component.translatable("squidgame.game.dalgona.board", doneCount, crackedCount + timedOutCount, slots.size());
        return Component.Serializer.toJson(text, ctx.server().registryAccess());
    }
}
