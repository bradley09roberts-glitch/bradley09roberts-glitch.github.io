package com.squidgame.game.bridge;

import com.squidgame.SquidConfig;
import com.squidgame.SquidGameMod;
import com.squidgame.build.Marker;
import com.squidgame.core.Difficulty;
import com.squidgame.core.GameKind;
import com.squidgame.core.bridge.BridgeKnowledge;
import com.squidgame.core.bridge.BridgeLayout;
import com.squidgame.core.bridge.BridgeQueue;
import com.squidgame.core.bridge.BridgeRoute;
import com.squidgame.core.bridge.BridgeRules;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.GuardEntity;
import com.squidgame.game.EliminationCause;
import com.squidgame.game.GameContext;
import com.squidgame.game.GameResult;
import com.squidgame.game.MiniGame;
import com.squidgame.net.HudPayload;
import com.squidgame.net.ModNetwork;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import com.squidgame.tournament.Teleporter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Game 5, the Glass Bridge. Eighteen rows of two glass panels, one of each pair tempered and one fragile; nobody
 * knows which. Contestants cross one after another in a random order drawn at the start, the gate calling the
 * next one when the previous is well on its way, so the first ones gamble and the later ones learn from what they saw.
 *
 * <p>What is secret: the {@link BridgeRoute}, held only in this class (persisted with the game state so a resumed game
 * keeps it). What is public: everything that happens to a panel, recorded in the {@link BridgeKnowledge} log that NPC
 * behaviours, the HUD and the client overlay are built on. Humans and NPCs are judged by exactly the same code
 * ({@link #arrive}): touching a fragile panel cracks it at once and it shatters a few ticks later; touching it
 * condemns whoever it was, however fast they run on.
 */
public final class GlassBridgeGame implements MiniGame {
    private static final String GATE = "bridge_gate";
    private static final int FIRST_CALL_DELAY = 70;
    /** A body this far below the walking surface has fallen off the bridge. */
    private static final double FALL_DEPTH = 6.0;
    /** After the glass is gone a condemned contestant who is somehow still up is eliminated anyway. */
    private static final int FALL_GRACE = 26;
    private static final int STALL_CRACK_TICKS = 8;
    private static final long ORDER_SALT = 0xB41D6E0DL;
    private static final int LAST_EVENT_TICKS = 220;
    /** Slowness amplifier of a condemned human (10 = cannot walk); also how {@link #releaseHuman} recognises its own effect. */
    private static final int HUMAN_SLOW = 9;
    /** Zeroes the jump strength of a condemned human (a negative Jump Boost is clamped to level 1 by vanilla, so it cannot be used). */
    private static final net.minecraft.resources.ResourceLocation JUMP_LOCK = SquidGameMod.id("bridge_jump_lock");

    private record Task(long due, Runnable action) {
    }

    // ---- secret
    private BridgeRoute route;
    private BridgeRoute restoredRoute;
    private int[] restoredOrder;
    /** Rows shown up front in the interrupted game (-1 = none saved): a resumed game keeps exactly the same reveal. */
    private int restoredRevealed = -1;

    // ---- public state
    private Difficulty difficulty = Difficulty.NORMAL;
    private BridgeRules.Params params = BridgeRules.params(Difficulty.NORMAL);
    private PanelField field;
    private BridgeKnowledge knowledge;
    private BridgeQueue queue;
    private BridgePublicView view;
    private boolean started;
    private boolean noArena;
    private boolean timedOut;
    private long clock;
    private long nextCallAt;
    private long lastCallTick = Long.MIN_VALUE / 2;
    private long lastEntryTick;
    private int finishCounter;
    private int sentEvents = -1;
    /** Rows from the near end that the guards show to everybody before the first call (a small field, see {@link BridgeRules#revealedRows}). */
    private int revealedRows;
    private long lastEventTick = Long.MIN_VALUE / 2;
    private BridgeKnowledge.Event lastEvent;
    private Marker gateMarker;
    private double gateForwardX;
    private double gateForwardZ = 1.0;
    private double pitFloorY;
    private final List<Marker> cages = new ArrayList<>();

    private final Map<Integer, Crosser> crossers = new HashMap<>();
    private final Map<Integer, BridgeNpcBehavior> behaviors = new HashMap<>();
    private final Map<Integer, Marker> slotOf = new HashMap<>();
    private final Set<Integer> active = new LinkedHashSet<>();
    private final List<PanelField.Cell> dynamic = new ArrayList<>();
    private final List<Task> tasks = new ArrayList<>();
    private final List<Vec3> finishSpots = new ArrayList<>();

    // ------------------------------------------------------------------ MiniGame: text

    @Override
    public GameKind type() {
        return GameKind.GLASS_BRIDGE;
    }

    @Override
    public List<Component> instructions(GameContext ctx) {
        BridgeRules.Params p = BridgeRules.params(ctx.difficulty());
        String time = formatTime(BridgeRules.timeLimitTicks(ctx.difficulty(), Math.max(1, ctx.alive().size())));
        List<Component> lines = new ArrayList<>(List.of(
                Component.translatable("squidgame.game.glass_bridge.instruction.1"),
                Component.translatable("squidgame.game.glass_bridge.instruction.2"),
                Component.translatable("squidgame.game.glass_bridge.instruction.3"),
                Component.translatable("squidgame.game.glass_bridge.instruction.4"),
                Component.translatable("squidgame.game.glass_bridge.instruction.5", p.stallLimitTicks() / 20),
                Component.translatable("squidgame.game.glass_bridge.instruction.6", time, ctx.difficulty().id)));
        int fieldSize = Math.max(1, ctx.alive().size());
        int shown = plannedReveal(ctx.difficulty(), fieldSize, BridgeRoute.DEFAULT_ROWS);
        if (shown > 0) {
            // a small field cannot find the way across by trial and error: the guards have tested the first rows for it
            lines.add(Component.translatable("squidgame.game.glass_bridge.instruction.7", fieldSize, shown, BridgeRoute.DEFAULT_ROWS - shown));
        }
        return lines;
    }

    /** Rows the guards show up front: the saved number of a resumed game, otherwise what the field size calls for. */
    private int plannedReveal(Difficulty d, int fieldSize, int rows) {
        if (restoredRevealed >= 0) {
            return Math.min(restoredRevealed, Math.max(0, rows - 1));
        }
        return BridgeRules.revealedRows(d, fieldSize, rows);
    }

    @Override
    public Component objective(GameContext ctx, @Nullable Contestant viewer) {
        String base = "squidgame.game.glass_bridge.objective.";
        if (viewer == null || queue == null || !queue.contains(viewer.number)) {
            return Component.translatable(base + "watch");
        }
        return switch (queue.state(viewer.number)) {
            case QUEUED -> queue.next() == viewer.number ? Component.translatable(base + "next")
                    : Component.translatable(base + "queue", queue.aheadOf(viewer.number));
            case CALLED -> Component.translatable(base + "called");
            case ON_BRIDGE -> Component.translatable(base + "bridge");
            case FINISHED -> Component.translatable(base + "across");
            case OUT -> Component.translatable(base + "watch");
        };
    }

    @Override
    public int timeLimitTicks(GameContext ctx) {
        return BridgeRules.timeLimitTicks(ctx.difficulty(), Math.max(1, ctx.alive().size()));
    }

    private static String formatTime(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void prepare(GameContext ctx) {
        resetRound();
        difficulty = ctx.difficulty();
        params = BridgeRules.params(difficulty);
        field = PanelField.load(ctx);
        if (field == null) {
            noArena = true;
            ctx.spawnGuards();
            return;
        }
        int rows = field.rows();
        boolean routeRestored = restoredRoute != null && restoredRoute.rows() == rows;
        route = routeRestored ? restoredRoute : BridgeRoute.forGame(ctx.tournament.seed, ctx.tournament.gameNumber, rows);
        restoredRoute = null;
        revealedRows = plannedReveal(difficulty, ctx.alive().size(), rows);
        debug("{} rows will be shown up front for a field of {} ({})", revealedRows, ctx.alive().size(),
                restoredRevealed >= 0 ? "restored from the saved game state" : "from the size of the field");
        restoredRevealed = -1;
        debug(routeRestored ? "the hidden route was restored from the saved game state" : "a new hidden route was generated ({} rows)", rows);
        knowledge = new BridgeKnowledge(rows);
        // the bridge is always intact when a game starts (also after a server restart in the middle of a game)
        field.restoreAll();
        Marker pit = ctx.marker("bridge.pit_floor");
        pitFloorY = pit != null ? pit.y() : field.topY - 70;
        cages.clear();
        cages.addAll(ctx.markers("bridge.hanging_cage"));
        gateMarker = ctx.marker("bridge.gate");
        if (gateMarker != null) {
            ctx.doors().create(ctx.level, GATE, gateMarker, "squidgame:tile_black");
            double rad = Math.toRadians(gateMarker.yaw());
            gateForwardX = -Math.sin(rad);
            gateForwardZ = Math.cos(rad);
        }
        ctx.spawnGuards();
    }

    /** How far (blocks, along the direction of travel) a position is beyond the gate's door plane; negative = in the queue area. */
    private double beyondGate(Vec3 p) {
        return (p.x - gateMarker.x()) * gateForwardX + (p.z - gateMarker.z()) * gateForwardZ;
    }

    private void resetRound() {
        revealedRows = 0;
        started = false;
        noArena = false;
        timedOut = false;
        clock = 0;
        nextCallAt = FIRST_CALL_DELAY;
        lastCallTick = Long.MIN_VALUE / 2;
        lastEntryTick = 0;
        finishCounter = 0;
        sentEvents = -1;
        lastEventTick = Long.MIN_VALUE / 2;
        lastEvent = null;
        crossers.clear();
        behaviors.clear();
        slotOf.clear();
        active.clear();
        dynamic.clear();
        tasks.clear();
        finishSpots.clear();
        queue = null;
        view = null;
    }

    @Override
    public void placeContestants(GameContext ctx) {
        if (noArena) {
            return;
        }
        List<Contestant> alive = new ArrayList<>(ctx.alive());
        queue = new BridgeQueue(drawOrder(ctx, alive));
        List<Marker> slots = new ArrayList<>(ctx.markers("bridge.queue"));
        slots.sort(Comparator.comparingInt(m -> m.getInt("slot", 0)));
        List<Contestant> ordered = new ArrayList<>();
        for (int n : queue.order()) {
            Contestant c = ctx.byNumber(n);
            if (c != null) {
                ordered.add(c);
                crossers.put(n, new Crosser(n));
            }
        }
        Teleporter.spread(ctx.level, ordered, slots);
        for (int i = 0; i < ordered.size(); i++) {
            Contestant c = ordered.get(i);
            if (!slots.isEmpty()) {
                slotOf.put(c.number, slots.get(i % slots.size()));
            }
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setActivity(Activity.NONE);
            }
        }
        finishSpots.clear();
        List<Marker> fs = new ArrayList<>(ctx.markers("bridge.finish_spawn"));
        fs.sort(Comparator.comparingInt(m -> m.getInt("slot", 0)));
        for (Marker m : fs) {
            finishSpots.add(new Vec3(m.x(), m.y(), m.z()));
        }
        Vec3 staging;
        if (gateMarker != null) {
            double rad = Math.toRadians(gateMarker.yaw());
            staging = new Vec3(gateMarker.x() + Math.sin(rad) * 2.0, gateMarker.y(), gateMarker.z() - Math.cos(rad) * 2.0);
        } else if (!slots.isEmpty()) {
            staging = new Vec3(slots.get(0).x(), slots.get(0).y(), slots.get(0).z());
        } else {
            staging = new Vec3(field.layout.start().centerX(), field.topY, field.layout.start().maxZ() - 2.5);
        }
        view = new BridgePublicView(field, knowledge, queue, params, difficulty, crossers, () -> clock, staging, finishSpots);
    }

    /**
     * The crossing order. A resumed game keeps the saved order: contestants who were eliminated before the restart are
     * simply left out, everybody else keeps their relative place. A fresh game draws a random permutation.
     */
    private List<Integer> drawOrder(GameContext ctx, List<Contestant> alive) {
        List<Integer> numbers = new ArrayList<>();
        for (Contestant c : alive) {
            numbers.add(c.number);
        }
        if (restoredOrder != null) {
            List<Integer> kept = BridgeQueue.restoreOrder(restoredOrder, numbers);
            restoredOrder = null;
            if (kept != null) {
                debug("the crossing order was restored from the saved game state ({} contestants)", kept.size());
                return kept;
            }
            debug("the saved crossing order does not cover every contestant: drawing a new one");
        }
        Rng rng = ctx.rng().fork(ORDER_SALT);
        rng.shuffle(numbers);
        return numbers;
    }

    @Override
    public void begin(GameContext ctx) {
        started = true;
        clock = 0;
        nextCallAt = FIRST_CALL_DELAY;
        if (noArena) {
            return;
        }
        showFirstRows(ctx);
        ctx.assignBehaviors(c -> makeBehavior(c));
        sentEvents = -1;
        syncMap(ctx, true);
    }

    /**
     * A small field is shown the first rows of the bridge: for each of them the safe lane is published through the same
     * public-knowledge channel everything else uses (so NPCs, the HUD and the client overlay know it and nothing else), the
     * glass that holds chimes and shimmers one row after the other, and the rows behind stay the secret of the route.
     */
    private void showFirstRows(GameContext ctx) {
        if (revealedRows <= 0) {
            return;
        }
        for (int r = 0; r < revealedRows; r++) {
            knowledge.record(r, route.safeLane(r), BridgeKnowledge.Outcome.SHOWN, 0);
        }
        debug("the guards show the first {} rows of the bridge ({} left to find)", revealedRows, field.rows() - revealedRows);
        for (int r = 0; r < revealedRows; r++) {
            final PanelField.Cell cell = field.cell(r, route.safeLane(r));
            later(8 + 3 * r, () -> field.holdFx(cell, new Vec3(cell.centerX(), field.topY, cell.centerZ())));
        }
        ctx.actionBar(Component.translatable("squidgame.game.glass_bridge.revealed", revealedRows, field.rows() - revealedRows));
    }

    /** A soft shimmer on the shown glass that holds, so a contestant can also see in the world where to step. */
    private void shimmerShownRows() {
        for (int r = 0; r < revealedRows; r++) {
            PanelField.Cell cell = field.cell(r, route.safeLane(r));
            if (cell.hasGlass()) {
                field.shimmer(cell);
            }
        }
    }

    private BridgeNpcBehavior makeBehavior(Contestant c) {
        BridgeNpcBehavior b = new BridgeNpcBehavior(view, c.number);
        behaviors.put(c.number, b);
        return b;
    }

    @Override
    public void onControllerChanged(GameContext ctx, Contestant c) {
        if (!started || noArena || view == null) {
            return;
        }
        ContestantEntity e = ctx.npc(c);
        if (c.isAlive() && c.isAiControlled() && e != null) {
            e.setBehavior(makeBehavior(c));
        } else {
            behaviors.remove(c.number);
        }
    }

    @Override
    public void onContestantEliminated(GameContext ctx, Contestant c, EliminationCause cause) {
        Crosser cr = crossers.get(c.number);
        if (cr != null && !cr.out) {
            markOut(cr);
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick(GameContext ctx) {
        if (!started || noArena || queue == null || view == null) {
            return;
        }
        clock++;
        runTasks();
        if (revealedRows > 0 && clock % 30 == 0) {
            shimmerShownRows();
        }
        tickPanels(ctx);
        tickGate(ctx);
        for (Integer n : new ArrayList<>(active)) {
            Contestant c = ctx.byNumber(n);
            Crosser cr = crossers.get(n);
            if (c == null || cr == null || !c.isAlive() || cr.out) {
                if (cr != null) {
                    markOut(cr);
                }
                continue;
            }
            tickCrosser(ctx, c, cr);
        }
        if (clock % 5 == 0) {
            tickGuards(ctx);
        }
        tickMotors(ctx);
        tickHumanWarnings(ctx);
        syncMap(ctx, false);
    }

    private void runTasks() {
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (t.due() <= clock) {
                tasks.remove(i--);
                t.action().run();
            }
        }
    }

    private void later(int ticks, Runnable r) {
        tasks.add(new Task(clock + Math.max(0, ticks), r));
    }

    private void tickMotors(GameContext ctx) {
        for (Map.Entry<Integer, BridgeNpcBehavior> e : behaviors.entrySet()) {
            Contestant c = ctx.byNumber(e.getKey());
            if (c == null || !c.isAlive()) {
                continue;
            }
            ContestantEntity npc = c.npc(ctx.level);
            if (npc != null && npc.behavior() == e.getValue()) {
                e.getValue().motorTick(npc);
            }
        }
    }

    // ------------------------------------------------------------------ the gate and the queue

    private void tickGate(GameContext ctx) {
        int next = queue.next();
        if (next >= 0 && clock >= nextCallAt) {
            int prev = queue.lastCalled();
            boolean hasPrev = prev > 0;
            boolean gone = false;
            int prevRow = -1;
            if (hasPrev) {
                BridgeQueue.State st = queue.state(prev);
                gone = st == BridgeQueue.State.FINISHED || st == BridgeQueue.State.OUT;
                Crosser pc = crossers.get(prev);
                prevRow = pc == null ? -1 : pc.rowReached;
            }
            BridgeQueue.Flow flow = new BridgeQueue.Flow(queue.someoneCalled(), queue.count(BridgeQueue.State.ON_BRIDGE),
                    clock - lastCallTick, hasPrev, gone, prevRow);
            if (BridgeQueue.mayRelease(params, flow)) {
                callNext(ctx, next);
            }
        }
        // close the gate again once the called contestant is through (and nobody stands in the doorway)
        if (view.gateOpen && !queue.someoneCalled() && clock - lastEntryTick > 24 && !anyoneInDoorway(ctx)) {
            view.gateOpen = false;
            ctx.doors().close(ctx.level, GATE);
        }
    }

    private boolean anyoneInDoorway(GameContext ctx) {
        if (gateMarker == null) {
            return false;
        }
        for (Contestant c : ctx.alive()) {
            Vec3 p = c.position(ctx.level);
            if (p != null && Math.abs(p.z - gateMarker.z()) < 1.3 && Math.abs(p.x - gateMarker.x()) < 4.5 && p.y > field.topY - 1) {
                return true;
            }
        }
        return false;
    }

    private void callNext(GameContext ctx, int number) {
        Contestant c = ctx.byNumber(number);
        Crosser cr = crossers.get(number);
        if (c == null || cr == null || !c.isAlive()) {
            queue.out(number);
            return;
        }
        if (!queue.call(number)) {
            return;
        }
        cr.calledTick = clock;
        cr.progressTick = clock;
        active.add(number);
        lastCallTick = clock;
        lastEntryTick = clock;
        if (gateMarker != null) {
            view.gateOpen = true;
            view.gateOpenedAt = clock;
            ctx.doors().open(ctx.level, GATE);
            GuardEntity g = ctx.guards().nearestArmed(new Vec3(gateMarker.x(), gateMarker.y(), gateMarker.z()));
            if (g != null) {
                g.gesture("open_door");
            }
        }
        ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
        if (p != null) {
            Announcer.title(p, Component.translatable("squidgame.game.glass_bridge.title.your_turn"),
                    Component.translatable("squidgame.game.glass_bridge.title.your_turn.sub"), 4, 40, 10);
            Announcer.sound(p, ModSounds.UI_NUMBER_CALL, 1f, 1f);
        }
        int waiting = queue.count(BridgeQueue.State.QUEUED);
        ctx.actionBar(Component.translatable("squidgame.game.glass_bridge.called", c.displayNumber(), waiting));
        debug("called No. {} ({} still waiting){}", c.displayNumber(), waiting, p != null ? " [human]" : "");
    }

    /** Humans cannot slip through the gate out of turn, and finishers cannot wander back onto the deck. */
    private void tickGuards(GameContext ctx) {
        for (Contestant c : ctx.aliveHumans()) {
            Crosser cr = crossers.get(c.number);
            Vec3 pos = c.position(ctx.level);
            if (cr == null || pos == null || queue == null) {
                continue;
            }
            BridgeQueue.State st = queue.state(c.number);
            boolean onDeckLevel = pos.y > field.topY - 1.5;
            if (st == BridgeQueue.State.QUEUED && onDeckLevel && gateMarker != null && beyondGate(pos) > 0.9) {
                Marker slot = slotOf.get(c.number);
                if (slot != null) {
                    ctx.teleport(c, slot);
                    ServerPlayer p = c.player(ctx.server());
                    if (p != null) {
                        p.displayClientMessage(Component.translatable("squidgame.game.glass_bridge.wait_turn"), true);
                        Announcer.sound(p, ModSounds.UI_DENY, 1f, 1f);
                    }
                }
            } else if (st == BridgeQueue.State.FINISHED && onDeckLevel && pos.z < field.layout.finish().minZ() - 0.4
                    && pos.z > field.layout.start().maxZ()) {
                // finishers do not walk back onto the glass
                Vec3 spot = view.finishSpot(Math.max(1, cr.finishRank));
                ctx.teleport(c, new Vec3(spot.x, field.topY, spot.z), 180f);
            }
        }
    }

    // ------------------------------------------------------------------ one contestant on the bridge

    private void tickCrosser(GameContext ctx, Contestant c, Crosser cr) {
        LivingEntity body = c.body(ctx.level);
        if (body == null) {
            return; // an absent human whose stand-in has not appeared yet
        }
        Vec3 pos = body.position();
        if (pos.y < field.topY - FALL_DEPTH) {
            fell(ctx, c, cr);
            return;
        }
        BridgeQueue.State st = queue.state(c.number);
        if (st == BridgeQueue.State.CALLED && clock - cr.calledTick > params.callLimitTicks()) {
            BridgeNpcBehavior nb = behaviors.get(c.number);
            debug("No. {} did not step onto the bridge in time (at {}, {}, {}; {})", c.displayNumber(),
                    Math.round(pos.x * 10) / 10.0, Math.round(pos.y * 10) / 10.0, Math.round(pos.z * 10) / 10.0,
                    nb == null ? "human or no behaviour" : nb.describe());
            ctx.broadcast(Component.translatable("squidgame.game.glass_bridge.frozen_at_gate", c.displayNumber()));
            markOut(cr);
            ctx.eliminate(c, EliminationCause.TIMEOUT);
            return;
        }
        if (onFinishPlatform(pos, body)) {
            if (BridgeRules.skipsRow(cr.rowReached, field.rows())) {
                // a long jump from the last but one row onto the far platform would dodge the last row
                putBack(ctx, c, cr, pos);
                return;
            }
            finish(ctx, c, cr);
            return;
        }
        updateOccupancy(cr, pos);
        PanelField.Cell under = field.supportOf(body);
        if (under != null) {
            cr.offPanelTicks = 0;
            if (under != cr.cell) {
                arrive(ctx, c, cr, under, pos);
            }
        } else if (cr.cell != null && ++cr.offPanelTicks > 120 && body.onGround()) {
            // back on the start platform after having entered the bridge: no way to stall the gate by standing there
            debug("No. {} left the bridge and idled", c.displayNumber());
            markOut(cr);
            ctx.eliminate(c, EliminationCause.TIMEOUT);
            return;
        }
        if (cr.condemned && clock >= cr.condemnedDeadline) {
            debug("No. {} was condemned and is still up: eliminated anyway", c.displayNumber());
            fell(ctx, c, cr);
            return;
        }
        tickStall(ctx, c, cr, body, under);
    }

    private boolean onFinishPlatform(Vec3 pos, LivingEntity body) {
        return field.layout.finish().contains(pos.x, pos.z) && Math.abs(pos.y - field.topY) < 0.8 && body.onGround();
    }

    /** Occupancy follows the body: the panel it is above holds its number, so nobody else hops onto it. */
    private void updateOccupancy(Crosser cr, Vec3 pos) {
        PanelField.Cell now = field.cellOver(pos);
        if (now == cr.over) {
            return;
        }
        if (cr.over != null && cr.over.occupant == cr.number) {
            cr.over.occupant = 0;
        }
        cr.over = now;
        if (now != null) {
            now.occupant = cr.number;
            if (now.reservedBy == cr.number) {
                now.reservedBy = 0;
            }
        }
    }

    /** The one place where a panel is judged: the contestant just touched down on {@code cell}. */
    private void arrive(GameContext ctx, Contestant c, Crosser cr, PanelField.Cell cell, Vec3 pos) {
        if (BridgeRules.skipsRow(cr.rowReached, cell.row)) {
            // a long sprint jump over a whole row: not a way to dodge its gamble. Nothing is judged; back to where they came from.
            putBack(ctx, c, cr, pos);
            return;
        }
        cr.cell = cell;
        if (queue.state(c.number) == BridgeQueue.State.CALLED) {
            queue.onBridge(c.number);
            lastEntryTick = clock;
        }
        if (cell.row > cr.rowReached) {
            cr.rowReached = cell.row;
            cr.progressTick = clock;
        }
        cell.occupant = c.number;
        cell.reservedBy = cell.reservedBy == c.number ? 0 : cell.reservedBy;
        boolean fragile = !route.isSafe(cell.row, cell.lane);
        if (fragile || cell.state == PanelField.State.CRACKING) {
            // the first touch starts the crack; whoever else touches a cracking panel goes down with it
            condemn(ctx, c, cr, cell, pos, false);
            return;
        }
        if (cr.condemned) {
            return; // an escape from a cracking panel changes nothing: the verdict stands
        }
        // tempered: after the moment in which a fragile panel would have gone, everybody has seen it hold
        final int row = cell.row, lane = cell.lane;
        later(params.confirmTicks(), () -> {
            if (knowledge.record(row, lane, BridgeKnowledge.Outcome.HELD, clock)) {
                noteEvent();
                debug("row {} lane {} was seen holding", row + 1, lane);
            }
            field.holdFx(cell, new Vec3(cell.centerX(), field.topY, cell.centerZ()));
        });
    }

    /** Puts a contestant who jumped over a row back on the last panel they stood on (or the front edge of the start platform). */
    private void putBack(GameContext ctx, Contestant c, Crosser cr, Vec3 pos) {
        LivingEntity body = c.body(ctx.level);
        float yaw = body == null ? 0f : body.getYRot();
        Vec3 target;
        if (cr.cell != null && cr.cell.hasGlass()) {
            target = new Vec3(cr.cell.centerX(), field.topY, cr.cell.centerZ());
        } else {
            BridgeLayout.Rect start = field.layout.start();
            target = new Vec3(Math.max(start.minX() + 0.7, Math.min(start.maxX() - 0.7, pos.x)), field.topY, start.maxZ() - 0.7);
        }
        debug("No. {} jumped over row {}: put back onto {}", c.displayNumber(), cr.rowReached + 2,
                cr.cell == null ? "the start platform" : "row " + (cr.cell.row + 1) + " lane " + cr.cell.lane + " (" + cr.cell.state + ")");
        ctx.teleport(c, target, yaw);
        ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
        if (p != null) {
            p.displayClientMessage(Component.translatable("squidgame.game.glass_bridge.no_skip"), true);
            Announcer.sound(p, ModSounds.UI_DENY, 1f, 1f);
        }
    }

    /** The panel under {@code c} starts cracking: a fragile one touched, or any panel stalled on. */
    private void condemn(GameContext ctx, Contestant c, Crosser cr, PanelField.Cell cell, Vec3 pos, boolean stall) {
        cr.condemned = true;
        int delay = stall ? STALL_CRACK_TICKS : ctx.rng().rangeInt(params.shatterDelayMin(), params.shatterDelayMax());
        if (cell.state != PanelField.State.CRACKING) {
            cell.state = PanelField.State.CRACKING;
            cell.crackStart = clock;
            cell.stateUntil = clock + delay;
            cell.stallBreak = stall;
            dynamic.add(cell);
            field.crackFx(cell, pos);
        }
        cr.condemnedDeadline = cell.stateUntil + FALL_GRACE;
        debug("No. {} stepped on row {} lane {}: {}", c.displayNumber(), cell.row + 1, cell.lane, stall ? "stall" : "CRACK");
        ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
        if (p != null) {
            // frozen with fear until the glass goes: no step (Slowness 10) and no jump (jump strength zeroed)
            lockHuman(p, (int) (cell.stateUntil - clock) + 6);
            ctx.danger(p, 0.85f, 22, 3, 0xFFE8F4FF);
            ctx.fade(p, 0, 1, 10, 0x55FFFFFF);
        }
    }

    private static void lockHuman(ServerPlayer p, int ticks) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, HUMAN_SLOW, true, false, false));
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(JUMP_LOCK)) {
            jump.addTransientModifier(new AttributeModifier(JUMP_LOCK, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Undoes {@link #lockHuman}: only this game's own modifier and effect are touched. */
    private static void releaseHuman(ServerPlayer p) {
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(JUMP_LOCK);
        }
        MobEffectInstance slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null && slow.getAmplifier() == HUMAN_SLOW) {
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
    }

    private void tickPanels(GameContext ctx) {
        for (int i = 0; i < dynamic.size(); i++) {
            PanelField.Cell cell = dynamic.get(i);
            if (cell.state == PanelField.State.CRACKING) {
                long total = Math.max(1, cell.stateUntil - cell.crackStart);
                double progress = (clock - cell.crackStart) / (double) total;
                field.setCrackStage(cell, Math.max(0, Math.min(9, (int) Math.floor(progress * 10) + 1)));
                if (clock >= cell.stateUntil) {
                    shatterCell(ctx, cell);
                }
            } else if (cell.state == PanelField.State.REBUILDING) {
                if (clock >= cell.stateUntil) {
                    if (field.rebuild(cell)) {
                        debug("the panel of row {} lane {} that the stall rule broke has re-formed", cell.row + 1, cell.lane);
                        dynamic.remove(i--);
                    } else {
                        cell.stateUntil = clock + 10;
                    }
                }
            } else {
                dynamic.remove(i--);
            }
        }
    }

    private void shatterCell(GameContext ctx, PanelField.Cell cell) {
        boolean fragile = !cell.stallBreak;
        field.shatter(cell, fragile);
        Vec3 at = new Vec3(cell.centerX(), field.topY, cell.centerZ());
        // the wail of whoever goes down with it
        ctx.soundAt(at, ModSounds.ELIMINATION_BUZZER, SoundSource.PLAYERS, 1.2f, 0.55f);
        if (fragile) {
            if (knowledge.record(cell.row, cell.lane, BridgeKnowledge.Outcome.BROKE, clock)) {
                noteEvent();
            }
            dynamic.remove(cell);
        } else {
            cell.stateUntil = clock + BridgeRules.REBUILD_TICKS;
        }
    }

    private void tickStall(GameContext ctx, Contestant c, Crosser cr, LivingEntity body, @Nullable PanelField.Cell under) {
        if (cr.cell == null || cr.condemned) {
            return;
        }
        // waiting behind somebody does not count: the timer is held while a panel of the next row is occupied
        cr.held = field.rowOccupied(cr.cell.row + 1, c.number);
        if (cr.held) {
            cr.progressTick++;
        }
        if (under == cr.cell && BridgeRules.stallExpired(params, cr.progressTick, clock)) {
            BridgeNpcBehavior nb = behaviors.get(c.number);
            debug("No. {} stalled for {} s on row {} ({})", c.displayNumber(), params.stallLimitTicks() / 20, cr.cell.row + 1,
                    nb == null ? "human" : nb.describe());
            ctx.broadcast(Component.translatable("squidgame.game.glass_bridge.stalled", c.displayNumber()));
            condemn(ctx, c, cr, cr.cell, body.position(), true);
        }
    }

    private void fell(GameContext ctx, Contestant c, Crosser cr) {
        int row = cr.cell == null ? 0 : cr.cell.row + 1;
        Vec3 where = c.position(ctx.level);
        if (!cr.condemned && c.isAiControlled()) {
            // an NPC must only ever fall because a panel was fragile or stalled on: anything else is a movement bug
            SquidGameMod.LOGGER.warn("[bridge] No. {} (AI) fell without being condemned, near ({}, {}, {}), last panel row {}",
                    c.displayNumber(), where == null ? "?" : String.format("%.2f", where.x), where == null ? "?" : String.format("%.2f", where.y),
                    where == null ? "?" : String.format("%.2f", where.z), row);
        }
        if (where != null) {
            dramaAt(ctx, where);
        }
        markOut(cr);
        if (row > 0) {
            ctx.broadcast(Component.translatable("squidgame.game.glass_bridge.fell", c.displayNumber(), row));
        } else {
            ctx.broadcast(Component.translatable("squidgame.game.glass_bridge.fell_start", c.displayNumber()));
        }
        ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
        if (p != null) {
            releaseHuman(p);
        }
        ctx.eliminate(c, EliminationCause.FELL);
    }

    private void finish(GameContext ctx, Contestant c, Crosser cr) {
        cr.finished = true;
        cr.finishRank = ++finishCounter;
        queue.finished(c.number);
        active.remove(c.number);
        clearCrosser(cr);
        c.stats.put("finishRank", (double) cr.finishRank);
        c.stats.put("finishTick", (double) clock);
        ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
        if (p != null) {
            Announcer.title(p, Component.translatable("squidgame.game.glass_bridge.title.crossed"),
                    Component.translatable("squidgame.game.glass_bridge.title.crossed.sub", cr.finishRank), 4, 50, 15);
            Announcer.sound(p, ModSounds.UI_CONFIRM, 1f, 1.2f);
        }
        ctx.actionBar(Component.translatable("squidgame.game.glass_bridge.crossed", c.displayNumber(), cr.finishRank));
        debug("No. {} crossed the bridge (#{})", c.displayNumber(), cr.finishRank);
    }

    /** The hall reacts to a fall: the spotlight rigs above the deck flash and the body is heard hitting the pit floor. */
    private void dramaAt(GameContext ctx, Vec3 where) {
        List<Marker> sorted = new ArrayList<>(cages);
        sorted.sort(Comparator.comparingDouble(m -> Math.abs(m.z() - where.z)));
        for (int i = 0; i < Math.min(2, sorted.size()); i++) {
            Marker m = sorted.get(i);
            ctx.level.sendParticles(ParticleTypes.FLASH, m.x(), m.y(), m.z(), 1, 0, 0, 0, 0);
            ctx.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x(), m.y() - 0.4, m.z(), 14, 0.7, 0.3, 0.7, 0.1);
            ctx.soundAt(new Vec3(m.x(), m.y(), m.z()), ModSounds.DANGER_STING, SoundSource.BLOCKS, 0.7f, 1.5f);
        }
        final double x = where.x, z = where.z;
        later(26, () -> ctx.soundAt(new Vec3(x, pitFloorY, z), ModSounds.ELIMINATION_BODY_FALL, SoundSource.PLAYERS, 1.6f, 0.8f));
    }

    /** Removes the contestant from the bridge's books: occupancy, reservations and the active set. */
    private void markOut(Crosser cr) {
        cr.out = true;
        behaviors.remove(cr.number);
        active.remove(cr.number);
        if (queue != null) {
            queue.out(cr.number);
        }
        clearCrosser(cr);
    }

    private void clearCrosser(Crosser cr) {
        if (cr.over != null && cr.over.occupant == cr.number) {
            cr.over.occupant = 0;
        }
        cr.over = null;
        if (field != null) {
            for (PanelField.Cell cell : field.all()) {
                if (cell.occupant == cr.number) {
                    cell.occupant = 0;
                }
                if (cell.reservedBy == cr.number) {
                    cell.reservedBy = 0;
                }
            }
        }
    }

    private void noteEvent() {
        lastEvent = knowledge.event(knowledge.eventCount() - 1);
        lastEventTick = clock;
    }

    // ------------------------------------------------------------------ human cues

    private void tickHumanWarnings(GameContext ctx) {
        if (clock % 20 != 0) {
            return;
        }
        for (Contestant c : ctx.aliveHumans()) {
            Crosser cr = crossers.get(c.number);
            if (cr == null || cr.cell == null || cr.condemned || cr.finished || cr.held) {
                continue;
            }
            int level = BridgeRules.stallWarning(params, BridgeRules.stallRemaining(params, cr.progressTick, clock));
            ServerPlayer p = c.player(ctx.server());
            if (p == null || level == 0) {
                continue;
            }
            // the client adds the heartbeat to every sustained danger cue
            ctx.danger(p, 0.18f + 0.14f * level, 24, level, 0xFFD02020);
        }
    }

    // ------------------------------------------------------------------ public map for the client overlay

    private void syncMap(GameContext ctx, boolean force) {
        if (knowledge == null) {
            return;
        }
        boolean changed = knowledge.eventCount() != sentEvents;
        if (!force && !changed && clock % 100 != 0) {
            return;
        }
        sentEvents = knowledge.eventCount();
        BridgeMapPayload payload = new BridgeMapPayload(knowledge.rows(), (int) Math.floor(field.layout.panel(0, 0).rect().minZ()),
                (int) Math.round(field.layout.panel(1, 0).rect().minZ() - field.layout.panel(0, 0).rect().minZ()),
                field.layout.isLeftLane(1) ? 1 : 0, knowledge.snapshot());
        for (ServerPlayer p : Announcer.audience(ctx.server())) {
            ModNetwork.send(p, payload);
        }
    }

    // ------------------------------------------------------------------ HUD

    @Override
    public void hudWidgets(GameContext ctx, @Nullable Contestant viewer, List<HudPayload.Widget> out) {
        if (!started || noArena || queue == null) {
            return;
        }
        String k = "squidgame.game.glass_bridge.hud.";
        Crosser cr = viewer == null ? null : crossers.get(viewer.number);
        BridgeQueue.State st = viewer != null && queue.contains(viewer.number) ? queue.state(viewer.number) : null;
        if (viewer != null && viewer.isAlive() && st != null && cr != null) {
            switch (st) {
                case QUEUED -> out.add(HudPayload.Widget.counter("queue", "icon_survivors", Component.translatable(k + "ahead"),
                        queue.aheadOf(viewer.number), -1));
                case CALLED -> {
                    out.add(HudPayload.Widget.banner("turn", Component.translatable(k + "banner.turn"), 0x40FF70));
                    int left = Math.max(0, params.callLimitTicks() - (int) (clock - cr.calledTick));
                    out.add(HudPayload.Widget.bar("call", Component.translatable(k + "call"), left / 20f, params.callLimitTicks() / 20f, 0x40D070));
                }
                case ON_BRIDGE -> {
                    if (cr.cell != null) {
                        out.add(HudPayload.Widget.counter("row", "icon_glass", Component.translatable(k + "row"), cr.cell.row + 1, field.rows()));
                        int remaining = BridgeRules.stallRemaining(params, cr.progressTick, clock);
                        int warn = BridgeRules.stallWarning(params, remaining);
                        if (cr.held) {
                            out.add(HudPayload.Widget.bar("stall", Component.translatable(k + "stall.held"), remaining / 20f, params.stallLimitTicks() / 20f, 0x5A9CFF));
                        } else {
                            int color = warn >= 2 ? 0xFF3030 : warn == 1 ? 0xFFC030 : 0x50D890;
                            out.add(HudPayload.Widget.bar("stall", Component.translatable(k + "stall"), remaining / 20f, params.stallLimitTicks() / 20f, color));
                            if (warn >= 2) {
                                out.add(HudPayload.Widget.banner("hurry", Component.translatable(k + "banner.hurry"), 0xFF3030));
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
        if (lastEvent != null && clock - lastEventTick < LAST_EVENT_TICKS) {
            out.add(HudPayload.Widget.line("event", eventText(lastEvent)));
        }
        out.add(HudPayload.Widget.line("status", Component.translatable(k + "status", queue.count(BridgeQueue.State.ON_BRIDGE)
                + queue.count(BridgeQueue.State.CALLED), queue.count(BridgeQueue.State.FINISHED), queue.count(BridgeQueue.State.QUEUED))));
    }

    private Component eventText(BridgeKnowledge.Event e) {
        String side = field.layout.isLeftLane(e.lane()) ? "left" : "right";
        return Component.translatable("squidgame.game.glass_bridge.hud.event." + (e.outcome() == BridgeKnowledge.Outcome.BROKE ? "broke" : "held"),
                e.row() + 1, Component.translatable("squidgame.game.glass_bridge.lane." + side));
    }

    // ------------------------------------------------------------------ end of the game

    @Override
    public boolean isFinished(GameContext ctx) {
        if (!started) {
            return false;
        }
        if (noArena) {
            return true;
        }
        for (Contestant c : ctx.alive()) {
            if (queue == null || queue.state(c.number) != BridgeQueue.State.FINISHED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onTimeout(GameContext ctx) {
        timedOut = true;
        ctx.title(Component.translatable("squidgame.game.glass_bridge.time_up"), Component.empty(), 0, 30, 10);
    }

    @Override
    public GameResult conclude(GameContext ctx) {
        List<Contestant> survivors = new ArrayList<>();
        List<Contestant> eliminated = new ArrayList<>();
        for (Contestant c : new ArrayList<>(ctx.alive())) {
            Crosser cr = crossers.get(c.number);
            if (noArena || (cr != null && cr.finished)) {
                survivors.add(c);
                continue;
            }
            // not across: the glass under them gives way, whoever they are
            PanelField.Cell cell = cr == null ? null : cr.over != null ? cr.over : cr.cell;
            LivingEntity body = c.body(ctx.level);
            if (cell != null && body != null && field.cellOver(body.position()) != null && cell.hasGlass()) {
                shatterNow(ctx, cell);
            }
            if (cr != null) {
                markOut(cr);
            }
            ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
            if (p != null) {
                releaseHuman(p);
            }
            ctx.eliminate(c, EliminationCause.TIMEOUT);
            eliminated.add(c);
        }
        return new GameResult(survivors, eliminated,
                Component.translatable("squidgame.game.glass_bridge.headline", survivors.size()),
                Component.translatable(timedOut ? "squidgame.game.glass_bridge.detail.timeout" : "squidgame.game.glass_bridge.detail"));
    }

    private void shatterNow(GameContext ctx, PanelField.Cell cell) {
        cell.stallBreak = true;
        field.shatter(cell, false);
        cell.stateUntil = Long.MAX_VALUE;
        ctx.soundAt(new Vec3(cell.centerX(), field.topY, cell.centerZ()), ModSounds.ELIMINATION_BUZZER, SoundSource.PLAYERS, 1.2f, 0.55f);
    }

    @Override
    public void cleanup(GameContext ctx) {
        started = false;
        if (field != null) {
            field.restoreAll();
            field.clearCracks();
        }
        for (ServerPlayer p : Announcer.audience(ctx.server())) {
            ModNetwork.send(p, BridgeMapPayload.empty());
            releaseHuman(p);
        }
        ctx.cleanupGuards();
        tasks.clear();
        dynamic.clear();
        active.clear();
        crossers.clear();
        behaviors.clear();
        view = null;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void saveState(CompoundTag tag) {
        if (route != null) {
            tag.putLong("route", route.toBits());
            tag.putInt("rows", route.rows());
        }
        if (route != null) {
            tag.putInt("revealed", revealedRows);
        }
        if (queue != null) {
            tag.putIntArray("order", queue.order().stream().mapToInt(Integer::intValue).toArray());
        }
    }

    @Override
    public void loadState(CompoundTag tag) {
        if (tag.contains("route") && tag.contains("rows")) {
            int rows = tag.getInt("rows");
            if (rows >= 1 && rows <= BridgeRoute.MAX_ROWS) {
                restoredRoute = BridgeRoute.ofBits(tag.getLong("route"), rows);
            }
        }
        if (tag.contains("order")) {
            restoredOrder = tag.getIntArray("order");
        }
        if (tag.contains("revealed")) {
            restoredRevealed = tag.getInt("revealed");
        }
    }

    // ------------------------------------------------------------------ helpers

    private void debug(String msg, Object... args) {
        if (SquidConfig.get().debug) {
            SquidGameMod.LOGGER.info("[bridge] " + msg, args);
        }
    }
}
