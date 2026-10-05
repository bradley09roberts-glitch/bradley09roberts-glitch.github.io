package com.squidgame.game.tug;

import com.squidgame.SquidGameMod;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import com.squidgame.core.Difficulty;
import com.squidgame.core.GameKind;
import com.squidgame.core.tug.BeatClock;
import com.squidgame.core.tug.TeamPlanner;
import com.squidgame.core.tug.TugInputGate;
import com.squidgame.core.tug.TugMatch;
import com.squidgame.core.tug.TugRules;
import com.squidgame.core.tug.TugSim;
import com.squidgame.core.tug.TugTraits;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.RopeEntity;
import com.squidgame.entity.ai.WaitingBehavior;
import com.squidgame.game.EliminationCause;
import com.squidgame.game.GameContext;
import com.squidgame.game.GameResult;
import com.squidgame.game.MiniGame;
import com.squidgame.net.HudPayload;
import com.squidgame.net.ModNetwork;
import com.squidgame.registry.ModEntities;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import com.squidgame.tournament.Teleporter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tug of War. Two teams stand single file on two platforms and pull a rope across a pit; the team whose end is pulled over its
 * edge falls into the pit and is eliminated, the other team survives. With more than 64 contestants the field plays in several
 * heats one after the other.
 *
 * <p>Humans and NPCs steer the same {@link TugSim}: a human through validated client input ({@code tug.input} for the held pull /
 * brace keys, {@code tug.heave} for a tap on the beat), an NPC through {@link TugNpcBehavior}, which decides from public
 * information only. The rules, the planner and the NPC policy are pure and tested in {@code core/tug}; this class is the
 * Minecraft side: teams, platforms, the rope entity, the fall, announcements, HUD and networking.
 *
 * <p>Stages of a heat: {@code WALK_IN} (teams march onto the platforms) - {@code COUNT_IN} (the beat ticks four times) -
 * {@code MATCH} (the simulation runs, with a sudden death after an exact tie) - {@code FALL} (the losing team is dragged
 * over the edge, the platform collapses) - {@code AFTERMATH} (the winners cheer, the platform is repaired) - the next heat.
 */
public final class TugOfWarGame implements MiniGame {
    enum Stage {IDLE, WALK_IN, COUNT_IN, MATCH, FALL, AFTERMATH, BETWEEN, DONE}

    private static final String[] TEAM_KEY = {"squidgame.game.tug_of_war.team.red", "squidgame.game.tug_of_war.team.blue"};
    private static final int[] TEAM_RGB = {0xE04848, 0x4A86F0};
    private static final ChatFormatting[] TEAM_FORMAT = {ChatFormatting.RED, ChatFormatting.BLUE};

    /** A held key counts for this long without a refresh from the client; humans are corrected if they stray this far from their slot. */
    private static final double STRAY_ALONG = 2.6, STRAY_ACROSS = 1.8;
    private static final int AFTERMATH_TICKS = 70;
    private static final int BETWEEN_TICKS = TugRules.BETWEEN_TICKS;
    private static final double HAND_OFFSET = 0.55;

    /** What the game remembers about a human between messages. */
    private static final class HumanInput {
        final TugInputGate.Limiter limiter = new TugInputGate.Limiter();
        final TugInputGate.Held held = new TugInputGate.Held();
        int heaveSeq;
        int heaveResult = TugNet.HEAVE_NONE;
        int heaveError;
        float heaveQuality;
    }

    private final Map<Integer, HumanInput> humans = new HashMap<>();
    private final Set<Integer> arrived = new HashSet<>();
    private final Set<Integer> fallenOut = new HashSet<>();
    private final List<int[]> results = new ArrayList<>();
    private final TugPlatform[] platforms = new TugPlatform[2];
    private final List<List<Marker>> slots = List.of(new ArrayList<>(), new ArrayList<>());
    private final List<List<Marker>> waiting = List.of(new ArrayList<>(), new ArrayList<>());
    private final List<Marker> spare = new ArrayList<>();

    private Difficulty difficulty = Difficulty.NORMAL;
    private TugRules.Params params = TugRules.params(Difficulty.NORMAL);
    @Nullable
    private TeamPlanner.Plan plan;
    @Nullable
    private TugHeat heat;
    private int heatIndex;
    private Stage stage = Stage.IDLE;
    private long stageSince;
    private long lastBeatSounded = Long.MIN_VALUE;
    @Nullable
    private RopeEntity rope;
    private Vec3 ropeCenter = Vec3.ZERO;
    private float gapHalf = 7f;
    private boolean timedOut;
    private int heatsDecided;
    private int fellTotal;
    private long lastDangerCue;
    private long lastCreak;
    private double lastSync0, lastSync1;
    private long fallStart;
    private int fallLoser = -1;
    @Nullable
    private CompoundTag pendingRepair;

    // ------------------------------------------------------------------ MiniGame: description

    @Override
    public GameKind type() {
        return GameKind.TUG_OF_WAR;
    }

    @Override
    public List<Component> instructions(GameContext ctx) {
        TugRules.Params p = TugRules.params(ctx.difficulty());
        return List.of(
                Component.translatable("squidgame.game.tug_of_war.instruction.1"),
                Component.translatable("squidgame.game.tug_of_war.instruction.2", Component.keybind("key.squidgame.tug.pull")),
                Component.translatable("squidgame.game.tug_of_war.instruction.3", Component.keybind("key.squidgame.tug.brace")),
                Component.translatable("squidgame.game.tug_of_war.instruction.4", Component.keybind("key.squidgame.tug.heave")),
                Component.translatable("squidgame.game.tug_of_war.instruction.5"),
                Component.translatable("squidgame.game.tug_of_war.instruction.6", p.heatLimitTicks() / 20, ctx.difficulty().id));
    }

    @Override
    public Component objective(GameContext ctx, @Nullable Contestant viewer) {
        if (viewer != null && heat != null && !heat.contains(viewer) && stage != Stage.IDLE && viewer.isAlive()) {
            return Component.translatable("squidgame.game.tug_of_war.objective.wait");
        }
        if (viewer != null && stage == Stage.AFTERMATH && heat != null && viewer.isAlive()) {
            return Component.translatable("squidgame.game.tug_of_war.objective.won");
        }
        return Component.translatable("squidgame.game.tug_of_war.objective");
    }

    @Override
    public int timeLimitTicks(GameContext ctx) {
        return TugRules.totalTicks(TugRules.params(ctx.difficulty()), TugRules.heatsFor(ctx.alive().size()));
    }

    // ------------------------------------------------------------------ MiniGame: lifecycle

    @Override
    public void prepare(GameContext ctx) {
        difficulty = ctx.difficulty();
        params = TugRules.params(difficulty);
        humans.clear();
        arrived.clear();
        fallenOut.clear();
        results.clear();
        plan = null;
        heat = null;
        heatIndex = 0;
        stage = Stage.IDLE;
        timedOut = false;
        heatsDecided = 0;
        fellTotal = 0;
        ServerLevel level = ctx.level;
        // a deck left broken by a game that was interrupted by a server restart is repaired first
        if (pendingRepair != null) {
            int n = TugPlatform.repair(level, pendingRepair, "a") + TugPlatform.repair(level, pendingRepair, "b");
            SquidGameMod.LOGGER.info("Tug of War: repaired {} blocks of an interrupted game", n);
            pendingRepair = null;
        }
        for (int t = 0; t < 2; t++) {
            slots.get(t).clear();
            waiting.get(t).clear();
            slots.get(t).addAll(sorted(ctx.markers(t == TugRules.TEAM_A ? "tug.slot_a" : "tug.slot_b")));
            waiting.get(t).addAll(sorted(ctx.markers(t == TugRules.TEAM_A ? "tug.waiting_a" : "tug.waiting_b")));
        }
        spare.clear();
        spare.addAll(sorted(ctx.markers("tug.spare")));
        Marker rc = ctx.marker("tug.rope_center");
        Marker ra = ctx.marker("tug.rope_a");
        Marker rb = ctx.marker("tug.rope_b");
        if (ra != null && rb != null) {
            gapHalf = (float) ((rb.x() - ra.x()) / 2.0);
            ropeCenter = rc != null ? GameContext.pos(rc) : new Vec3((ra.x() + rb.x()) / 2.0, ra.y(), ra.z());
        } else {
            gapHalf = 7f;
            ropeCenter = rc != null ? GameContext.pos(rc) : (slots.get(0).isEmpty() ? Vec3.ZERO : GameContext.pos(slots.get(0).get(0)).add(8, 1.2, 0));
        }
        for (int t = 0; t < 2; t++) {
            platforms[t] = createPlatform(ctx, t);
            if (platforms[t] != null) {
                platforms[t].snapshot(TugRules.MAX_PER_TEAM + 4);
            }
        }
        spawnRope(ctx);
        ctx.spawnGuards();
    }

    @Nullable
    private TugPlatform createPlatform(GameContext ctx, int team) {
        List<Marker> s = slots.get(team);
        Region edge = ctx.region(team == TugRules.TEAM_A ? "tug.edge_a" : "tug.edge_b");
        if (s.isEmpty() || edge == null) {
            SquidGameMod.LOGGER.warn("Tug of War: no slots or edge region for team {}; the platform will not collapse", team);
            return null;
        }
        Marker first = s.get(0);
        int tip = ropeCenter.x >= first.x() ? edge.maxX() : edge.minX();
        return TugPlatform.create(ctx.level, first, tip, ropeCenter.x);
    }

    private static List<Marker> sorted(List<Marker> in) {
        List<Marker> out = new ArrayList<>(in);
        out.sort(Comparator.comparingInt(m -> m.getInt("slot", 0)));
        return out;
    }

    private void spawnRope(GameContext ctx) {
        discardRope();
        RopeEntity r = ModEntities.ROPE.create(ctx.level);
        if (r == null) {
            return;
        }
        r.moveTo(ropeCenter.x, ropeCenter.y, ropeCenter.z, 0f, 0f);
        r.setAxisYaw(90f);
        r.setGapHalf(gapHalf);
        r.setEnds(gapHalf + 1.5f, gapHalf + 1.5f);
        r.addTag("squidgame_temp");
        ctx.level.getChunk(r.blockPosition());
        ctx.level.addFreshEntity(r);
        rope = r;
    }

    private void discardRope() {
        if (rope != null) {
            rope.discard();
            rope = null;
        }
    }

    @Override
    public void placeContestants(GameContext ctx) {
        List<TeamPlanner.Candidate> candidates = new ArrayList<>();
        for (Contestant c : ctx.alive()) {
            candidates.add(new TeamPlanner.Candidate(c.number, c.isHuman(), TugTraits.rating(c.personality)));
        }
        plan = TeamPlanner.plan(candidates, ctx.rng().fork(31));
        SquidGameMod.LOGGER.info("Tug of War: {} contestants in {} heat(s) on {}", candidates.size(), plan.heats().size(), difficulty.id);
        List<Contestant> inHeat = new ArrayList<>();
        if (!plan.heats().isEmpty()) {
            TeamPlanner.Heat h = plan.heats().get(0);
            Teleporter.spread(ctx.level, contestants(ctx, h.teamA()), waiting.get(TugRules.TEAM_A));
            Teleporter.spread(ctx.level, contestants(ctx, h.teamB()), waiting.get(TugRules.TEAM_B));
            inHeat.addAll(contestants(ctx, h.teamA()));
            inHeat.addAll(contestants(ctx, h.teamB()));
        }
        List<Contestant> others = new ArrayList<>(ctx.alive());
        others.removeAll(inHeat);
        Teleporter.spread(ctx.level, others, spare);
        for (Contestant c : ctx.alive()) {
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setActivity(Activity.NONE);
            }
        }
    }

    private static List<Contestant> contestants(GameContext ctx, List<Integer> numbers) {
        List<Contestant> out = new ArrayList<>(numbers.size());
        for (int n : numbers) {
            Contestant c = ctx.byNumber(n);
            if (c != null && c.isAlive()) {
                out.add(c);
            }
        }
        return out;
    }

    @Override
    public void begin(GameContext ctx) {
        if (plan == null || plan.heats().isEmpty()) {
            stage = Stage.DONE;
            return;
        }
        startHeat(ctx, 0);
    }

    // ------------------------------------------------------------------ heats

    private void startHeat(GameContext ctx, int index) {
        heatIndex = index;
        arrived.clear();
        fallenOut.clear();
        fallLoser = -1;
        TeamPlanner.Heat planned = plan.heats().get(index);
        TugHeat h = new TugHeat(index);
        h.handicap[TugRules.TEAM_A] = planned.handicapA();
        h.handicap[TugRules.TEAM_B] = planned.handicapB();
        for (Contestant c : contestants(ctx, planned.teamA())) {
            h.add(TugRules.TEAM_A, c);
        }
        for (Contestant c : contestants(ctx, planned.teamB())) {
            h.add(TugRules.TEAM_B, c);
        }
        heat = h;
        long now = ctx.now();
        if (!h.complete()) {
            // one side has nobody left (eliminated by the tournament): the other side wins without a match
            SquidGameMod.LOGGER.info("Tug of War heat {}: a team is empty, nothing to play", index + 1);
            h.winner = h.teams.get(TugRules.TEAM_A).isEmpty() ? TugRules.TEAM_B : TugRules.TEAM_A;
            if (h.size() == 0) {
                h.winner = TugRules.TEAM_A;
            }
            setStage(Stage.AFTERMATH, now);
            return;
        }
        for (TugPlatform p : platforms) {
            if (p != null) {
                p.restore();
                p.placeFence();
            }
        }
        if (rope != null) {
            rope.setFall(0, now);
            rope.setOffset(0f);
            rope.setStrain(0f);
            rope.setEnds(ropeEnd(h, TugRules.TEAM_A), ropeEnd(h, TugRules.TEAM_B));
        }
        setStage(Stage.WALK_IN, now);
        announceHeat(ctx, h);
        int delayStep = 3;
        for (int t = 0; t < 2; t++) {
            List<Contestant> line = h.teams.get(t);
            for (int k = 0; k < line.size(); k++) {
                Contestant c = line.get(k);
                if (c.isAiControlled()) {
                    ContestantEntity e = ctx.npc(c);
                    if (e != null) {
                        e.setBehavior(new TugNpcBehavior(this, c, k * delayStep));
                    }
                } else {
                    sendHumanToSlot(ctx, c);
                }
            }
        }
        // everybody else watches from the gallery
        for (Contestant c : ctx.alive()) {
            if (!h.contains(c)) {
                ContestantEntity e = ctx.npc(c);
                if (e != null && !(e.behavior() instanceof WaitingBehavior)) {
                    e.setBehavior(new WaitingBehavior());
                }
            }
        }
    }

    private float ropeEnd(TugHeat h, int team) {
        List<Contestant> line = h.teams.get(team);
        List<Marker> s = slots.get(team);
        if (line.isEmpty() || s.isEmpty()) {
            return gapHalf + 1.5f;
        }
        Marker last = s.get(Math.min(line.size(), s.size()) - 1);
        double dir = Math.signum(ropeCenter.x - last.x());
        double hand = last.x() + dir * HAND_OFFSET;
        return (float) Math.abs(hand - ropeCenter.x);
    }

    /** Humans are taken to their slot with a short fade (the NPCs of the heat march onto the platform meanwhile). */
    private void sendHumanToSlot(GameContext ctx, Contestant c) {
        TugHeat h = heat;
        if (h == null) {
            return;
        }
        ServerPlayer p = c.player(ctx.server());
        Marker m = slotMarker(h.team(c), h.slot(c));
        if (p == null || m == null) {
            return;
        }
        ctx.fade(p, 6, 8, 14, 0xFF000000);
        ctx.schedule(6, () -> {
            if (heat == h && c.isAlive() && c.isHumanControlled()) {
                ctx.teleport(c, m);
                arrived.add(c.number);
            }
        });
    }

    @Nullable
    Marker slotMarker(int team, int slot) {
        List<Marker> s = slots.get(team);
        return s.isEmpty() ? null : s.get(Math.min(slot, s.size() - 1));
    }

    private void announceHeat(GameContext ctx, TugHeat h) {
        int heats = plan == null ? 1 : plan.heats().size();
        int a = h.teams.get(0).size(), b = h.teams.get(1).size();
        ctx.broadcast(Component.translatable("squidgame.game.tug_of_war.chat.heat", h.index + 1, heats,
                teamName(TugRules.TEAM_A), a, teamName(TugRules.TEAM_B), b).withStyle(ChatFormatting.GOLD));
        for (Contestant c : h.everyone()) {
            ServerPlayer p = c.player(ctx.server());
            if (p != null && c.isHumanControlled()) {
                int t = h.team(c);
                Announcer.title(p, teamName(t), Component.translatable("squidgame.game.tug_of_war.subtitle.team", h.teams.get(t).size(),
                        h.teams.get(1 - t).size()), 5, 50, 15);
                Announcer.chat(p, Component.translatable("squidgame.game.tug_of_war.chat.you", teamName(t), h.slot(c) + 1)
                        .withStyle(TEAM_FORMAT[t]));
            }
        }
        ctx.sound(ModSounds.ANNOUNCE_CHIME, 1f, 1f);
    }

    private static MutableComponent teamName(int team) {
        return Component.translatable(TEAM_KEY[team]).withStyle(s -> s.withColor(TEAM_RGB[team]).withBold(true));
    }

    private void setStage(Stage s, long now) {
        stage = s;
        stageSince = now;
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick(GameContext ctx) {
        long now = ctx.now();
        switch (stage) {
            case WALK_IN -> tickWalkIn(ctx, now);
            case COUNT_IN -> tickCountIn(ctx, now);
            case MATCH -> tickMatch(ctx, now);
            case FALL -> tickFall(ctx, now);
            case AFTERMATH -> tickAftermath(ctx, now);
            case BETWEEN -> tickBetween(ctx, now);
            default -> {
            }
        }
        if (heat != null && stage != Stage.IDLE && stage != Stage.DONE) {
            if (now % 10 == 0) {
                keepHumansInLine(ctx);
            }
            if (now % 2 == 0) {
                sendStates(ctx, now);
            }
        }
    }

    private int scaled(GameContext ctx, int ticks) {
        return Math.max(1, (int) Math.round(ticks * ctx.config().timeScale));
    }

    // ---- walk in

    private void tickWalkIn(GameContext ctx, long now) {
        TugHeat h = heat;
        int waited = (int) (now - stageSince);
        boolean all = true;
        for (Contestant c : h.everyone()) {
            if (c.isAlive() && !arrived.contains(c.number) && !(c.isHumanControlled() && ctx.npc(c) == null && c.player(ctx.server()) == null)) {
                all = false;
                break;
            }
        }
        int limit = Math.max(TugRules.INTRO_MAX_TICKS / 2, scaled(ctx, TugRules.INTRO_MAX_TICKS));
        if (all && waited > 30 || waited >= limit) {
            if (waited >= limit && !all) {
                for (Contestant c : h.everyone()) {
                    if (c.isAlive() && !arrived.contains(c.number)) {
                        Marker m = slotMarker(h.team(c), h.slot(c));
                        if (m != null) {
                            ctx.teleport(c, m);
                        }
                        arrived.add(c.number);
                    }
                }
            }
            startCountIn(ctx, now);
        }
    }

    // ---- count in

    private void startCountIn(GameContext ctx, long now) {
        TugHeat h = heat;
        int period = TugRules.beatPeriod(ctx.rng().fork(100 + heatIndex));
        long go = now + (long) TugRules.COUNT_IN_BEATS * period + 12;
        BeatClock clock = new BeatClock(go, period);
        List<TugMatch.Entrant> entrants = new ArrayList<>();
        for (Contestant c : h.everyone()) {
            entrants.add(new TugMatch.Entrant(c.number, h.team(c), c.personality, c.isHuman()));
        }
        // the heat is simulated with every member listed (team A first, in slot order): the member index is the order in the list
        h.startSim(params, clock, TugMatch.specs(entrants, params));
        for (Contestant c : h.everyone()) {
            if (!c.isAlive() && h.indexOf(c) >= 0) {
                // eliminated by the tournament while the teams were still walking in: they do not take part
                h.sim.remove(h.indexOf(c));
            }
        }
        h.limitSpan = scaled(ctx, params.heatLimitTicks());
        h.limitTick = go + h.limitSpan;
        h.suddenDeath = false;
        lastBeatSounded = Long.MIN_VALUE;
        setStage(Stage.COUNT_IN, now);
        for (Contestant c : h.everyone()) {
            ServerPlayer p = c.player(ctx.server());
            if (p != null && c.isHumanControlled()) {
                p.displayClientMessage(Component.translatable("squidgame.game.tug_of_war.banner.ready"), true);
            }
            if (c.isHumanControlled()) {
                humans.computeIfAbsent(c.number, k -> new HumanInput()).held.clear();
            }
        }
        SquidGameMod.LOGGER.info("Tug of War heat {}: {} v {} members, beat {} ticks, GO at tick {}", h.index + 1, h.teams.get(0).size(),
                h.teams.get(1).size(), period, go);
    }

    private void tickCountIn(GameContext ctx, long now) {
        TugHeat h = heat;
        BeatClock beat = h.beat;
        soundBeat(ctx, h, now, true);
        if (now >= beat.epoch) {
            goMatch(ctx, now);
        }
    }

    private void goMatch(GameContext ctx, long now) {
        TugHeat h = heat;
        setStage(Stage.MATCH, now);
        lastSync0 = lastSync1 = 0;
        lastCreak = now;
        ctx.sound(ModSounds.COUNTDOWN_FINAL, 1f, 1.2f);
        ctx.soundAt(ropeCenter, ModSounds.ROPE_STRAIN, SoundSource.NEUTRAL, 4f, 1.1f);
        ctx.title(Component.translatable("squidgame.game.tug_of_war.banner.pull").withStyle(ChatFormatting.GREEN), Component.empty(), 0, 20, 8);
        for (Contestant c : h.everyone()) {
            ContestantEntity e = ctx.npc(c);
            if (e != null && c.isAiControlled()) {
                e.setActivity(Activity.PULL_IDLE);
            }
        }
    }

    /** One short tick for every beat to the humans taking part: the metronome of the heat. */
    private void soundBeat(GameContext ctx, TugHeat h, long now, boolean countIn) {
        BeatClock beat = h.beat;
        if (beat == null) {
            return;
        }
        long next = beat.nextBeatAtOrAfter(now);
        if (next != now || lastBeatSounded == now) {
            return;
        }
        lastBeatSounded = now;
        long index = beat.indexAtOrBefore(now);
        boolean last = countIn && index == -1;
        for (Contestant c : h.everyone()) {
            if (c.isHumanControlled() && c.isAlive()) {
                ServerPlayer p = c.player(ctx.server());
                if (p != null) {
                    Announcer.sound(p, last ? ModSounds.COUNTDOWN_BEEP : ModSounds.COUNTDOWN_TICK, last ? 0.9f : 0.7f,
                            countIn ? 1.0f + 0.12f * (index + TugRules.COUNT_IN_BEATS) : 1.0f);
                }
            }
        }
    }

    // ---- match

    private void tickMatch(GameContext ctx, long now) {
        TugHeat h = heat;
        TugSim sim = h.sim;
        soundBeat(ctx, h, now, false);
        // humans: the held keys decide
        for (Contestant c : h.everyone()) {
            if (c.isHumanControlled()) {
                int i = h.indexOf(c);
                HumanInput in = humans.get(c.number);
                if (i >= 0 && in != null) {
                    sim.setStance(i, in.held.effort(now), in.held.brace(now));
                }
            }
        }
        sim.tick(now);
        for (TugSim.Event ev : sim.drainEvents()) {
            onSimEvent(ctx, h, ev);
        }
        checkFallers(ctx, h);
        updateRope(ctx, h, now);
        matchSounds(ctx, h, now);
        TugSim.Outcome out = sim.outcome();
        if (out == TugSim.Outcome.ONGOING && now >= h.limitTick) {
            out = timeUp(ctx, h, now);
        }
        if (out != TugSim.Outcome.ONGOING) {
            endMatch(ctx, h, out == TugSim.Outcome.A_WINS ? TugRules.TEAM_A : TugRules.TEAM_B, now);
        }
    }

    /** The heat's time is up: the rope decides; an exact tie gets a sudden death with refilled stamina, then a coin. */
    private TugSim.Outcome timeUp(GameContext ctx, TugHeat h, long now) {
        TugSim sim = h.sim;
        TugSim.Outcome v = sim.verdictAtTimeout();
        if (v != TugSim.Outcome.ONGOING) {
            h.timedOut = true;
            Component side = teamName(v == TugSim.Outcome.A_WINS ? TugRules.TEAM_A : TugRules.TEAM_B);
            ctx.broadcast(Component.translatable("squidgame.game.tug_of_war.chat.timeout", side).withStyle(ChatFormatting.YELLOW));
            return v;
        }
        if (!h.suddenDeath) {
            h.suddenDeath = true;
            h.timedOut = true;
            h.limitSpan = scaled(ctx, params.suddenDeathTicks());
            h.limitTick = now + h.limitSpan;
            sim.startSuddenDeath();
            ctx.broadcast(Component.translatable("squidgame.game.tug_of_war.chat.sudden").withStyle(ChatFormatting.RED));
            ctx.title(Component.translatable("squidgame.game.tug_of_war.banner.sudden").withStyle(ChatFormatting.RED), Component.empty(), 0, 30, 10);
            ctx.sound(ModSounds.ANNOUNCE_CHIME_ALERT, 1f, 0.8f);
            return TugSim.Outcome.ONGOING;
        }
        h.coinFlip = Math.abs(sim.offset()) < TugRules.TIE_EPSILON;
        if (h.coinFlip) {
            ctx.broadcast(Component.translatable("squidgame.game.tug_of_war.chat.coin").withStyle(ChatFormatting.YELLOW));
            return ctx.rng().nextBoolean() ? TugSim.Outcome.A_WINS : TugSim.Outcome.B_WINS;
        }
        return sim.offset() > 0 ? TugSim.Outcome.B_WINS : TugSim.Outcome.A_WINS;
    }

    private void onSimEvent(GameContext ctx, TugHeat h, TugSim.Event ev) {
        Contestant c = ctx.byNumber(ev.member());
        if (c == null) {
            return;
        }
        ContestantEntity e = ctx.npc(c);
        if (ev.kind() == TugSim.EventKind.EXHAUSTED) {
            if (e != null && c.isAiControlled()) {
                e.triggerAction("pull_slip");
            }
            ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
            if (p != null) {
                Announcer.sound(p, ModSounds.UI_DENY, 0.8f, 0.7f);
                p.displayClientMessage(Component.translatable("squidgame.game.tug_of_war.exhausted").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Somebody who is not on the platform any more (walked off, was pushed) is out; the heat goes on without them. */
    private void checkFallers(GameContext ctx, TugHeat h) {
        for (Contestant c : h.everyone()) {
            if (!c.isAlive() || fallenOut.contains(c.number)) {
                continue;
            }
            LivingEntity body = c.body(ctx.level);
            Marker m = slotMarker(h.team(c), h.slot(c));
            if (body == null || m == null || body.getY() > m.y() - 6.0) {
                continue;
            }
            fallenOut.add(c.number);
            ctx.eliminate(c, EliminationCause.FELL);
        }
    }

    private void updateRope(GameContext ctx, TugHeat h, long now) {
        TugSim sim = h.sim;
        if (rope == null) {
            return;
        }
        rope.setOffset((float) sim.offset());
        rope.setStrain((float) sim.strain());
        for (int t = 0; t < 2; t++) {
            double s = sim.sync(t);
            double prev = t == 0 ? lastSync0 : lastSync1;
            if (s > 0.10 && prev <= 0.06) {
                rope.firePulse(t == TugRules.TEAM_B, (float) Math.min(1.0, s * 2.0), now);
                if (s > 0.2) {
                    ctx.soundAt(ropeCenter, ModSounds.ROPE_CREAK, SoundSource.NEUTRAL, (float) (1.0 + 2.0 * s), 0.8f + (float) s * 0.6f);
                }
            }
            if (t == 0) {
                lastSync0 = s;
            } else {
                lastSync1 = s;
            }
        }
    }

    private void matchSounds(GameContext ctx, TugHeat h, long now) {
        TugSim sim = h.sim;
        double edge = Math.abs(sim.offset());
        if (sim.strain() > 0.55 && now - lastCreak > 36 && ctx.rng().chance(0.15)) {
            lastCreak = now;
            ctx.soundAt(ropeCenter, ModSounds.ROPE_CREAK, SoundSource.NEUTRAL, 1.5f + (float) sim.strain() * 2f,
                    ctx.rng().nextBoolean() ? 0.85f : 1.05f);
        }
        if (edge > 0.82 && now - lastCreak > 50) {
            lastCreak = now;
            ctx.soundAt(ropeCenter, ModSounds.ROPE_STRAIN, SoundSource.NEUTRAL, 3.0f, 0.9f + (float) edge * 0.3f);
        }
        if (now - lastDangerCue >= 40) {
            lastDangerCue = now;
            for (Contestant c : h.everyone()) {
                if (!c.isHumanControlled() || !c.isAlive()) {
                    continue;
                }
                double lead = leadOf(c);
                ServerPlayer p = c.player(ctx.server());
                if (p == null) {
                    continue;
                }
                if (lead < -0.75) {
                    ctx.danger(p, 0.45f, 36, 2, 0xFFD02020);
                    Announcer.sound(p, ModSounds.DANGER_HEARTBEAT, 0.9f, 1.1f);
                } else if (lead < -0.5) {
                    ctx.danger(p, 0.2f, 36, 1, 0xFFD06020);
                    Announcer.sound(p, ModSounds.DANGER_HEARTBEAT, 0.6f, 0.9f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the end of a heat

    private void endMatch(GameContext ctx, TugHeat h, int winner, long now) {
        h.winner = winner;
        heatsDecided++;
        TugSim sim = h.sim;
        long ticks = now - h.goTick;
        SquidGameMod.LOGGER.info("Tug of War heat {}: {} wins after {}s ({}; rope {}; stamina red {}% blue {}%; heaves hit {} missed {}{})",
                h.index + 1, winner == TugRules.TEAM_A ? "RED" : "BLUE", String.format("%.1f", ticks / 20.0),
                h.coinFlip ? "coin flip" : h.suddenDeath ? "sudden death" : h.timedOut ? "timeout" : "over the edge",
                String.format("%+.2f", sim.offset()), Math.round(sim.meanStamina(0) * 100), Math.round(sim.meanStamina(1) * 100),
                totalHeaves(sim, true), totalHeaves(sim, false), humanHeaves(h));
        results.add(new int[]{h.index, winner});
        beginFall(ctx, h, 1 - winner, now);
    }

    /** "; humans: #12 hit 9 missed 2, ..." for the log line of a heat that humans took part in (empty otherwise). */
    private static String humanHeaves(TugHeat h) {
        StringBuilder sb = new StringBuilder();
        for (Contestant c : h.everyone()) {
            TugSim.Member m = c.isHuman() ? h.member(c) : null;
            if (m != null) {
                sb.append(sb.length() == 0 ? "; humans:" : ",").append(" #").append(c.number).append(" hit ").append(m.heavesHit())
                        .append(" missed ").append(m.heavesMissed());
            }
        }
        return sb.toString();
    }

    private static int totalHeaves(TugSim sim, boolean hits) {
        int n = 0;
        for (int i = 0; i < sim.size(); i++) {
            n += hits ? sim.member(i).heavesHit() : sim.member(i).heavesMissed();
        }
        return n;
    }

    /** The losing team is yanked over the edge: announcement, flag, cheering winners, stumbling losers, drag and collapse. */
    private void beginFall(GameContext ctx, TugHeat h, int loser, long now) {
        fallLoser = loser;
        fallStart = now;
        setStage(Stage.FALL, now);
        int winner = 1 - loser;
        if (rope != null) {
            rope.setFall(loser == TugRules.TEAM_A ? 1 : 2, now);
        }
        ctx.title(Component.translatable("squidgame.game.tug_of_war.title.wins", teamName(winner)),
                Component.translatable("squidgame.game.tug_of_war.subtitle.falls", teamName(loser)), 0, 50, 15);
        ctx.broadcast(Component.translatable("squidgame.game.tug_of_war.chat.result", teamName(winner), teamName(loser))
                .withStyle(ChatFormatting.GOLD));
        ctx.sound(ModSounds.ANNOUNCE_CHIME_ALERT, 1f, 0.9f);
        ctx.soundAt(ropeCenter, ModSounds.DANGER_STING, SoundSource.NEUTRAL, 6f, 0.7f);
        ctx.soundAt(ropeCenter, ModSounds.ROPE_STRAIN, SoundSource.NEUTRAL, 6f, 0.7f);
        for (Contestant c : h.teams.get(winner)) {
            celebrate(ctx, c);
        }
        for (Contestant c : h.teams.get(loser)) {
            if (!c.isAlive()) {
                continue;
            }
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setBehavior(null);
                e.stopMoving();
                e.setActivity(Activity.NONE);
                e.triggerAction("stumble");
            }
            ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
            if (p != null) {
                ctx.danger(p, 0.7f, 50, 3, 0xFFD02020);
            }
        }
        // the winners' platform is safe: nobody on it can fall any more
        TugPlatform won = platforms[winner];
        if (won != null) {
            won.removeFence();
        }
        TugPlatform lost = platforms[loser];
        if (lost != null) {
            lost.removeFence();
        }
    }

    private void celebrate(GameContext ctx, Contestant c) {
        if (!c.isAlive()) {
            return;
        }
        ContestantEntity e = ctx.npc(c);
        if (e != null && c.isAiControlled()) {
            e.setBehavior(null);
            e.stopMoving();
            e.setActivity(c.personality.courage() > 0.55 ? Activity.CELEBRATE_FIST : Activity.CELEBRATE);
        }
        ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
        if (p != null) {
            Announcer.sound(p, ModSounds.GAME_WIN_FANFARE, 0.9f, 1f);
        }
    }

    private void tickFall(GameContext ctx, long now) {
        TugHeat h = heat;
        long t = now - fallStart;
        TugPlatform lost = platforms[fallLoser];
        List<Contestant> losers = h.teams.get(fallLoser);
        // 1. the drag: from the first moment the losers are pulled towards the gap, faster and faster
        if (t >= 8) {
            double speed = Math.min(0.5, 0.10 + 0.02 * (t - 8));
            for (Contestant c : losers) {
                if (c.isAlive()) {
                    drag(ctx, c, fallLoser, speed);
                }
            }
        }
        // 2. the edge gives way, then the deck collapses from the tip back to the last member
        if (lost != null) {
            if (t == 14) {
                ctx.soundAt(lost.columnCenter(0), ModSounds.ROPE_STRAIN, SoundSource.BLOCKS, 8f, 0.6f);
            }
            if (t >= 16) {
                int rear = losers.size() + 2;
                int target = t == 16 ? 2 : Math.min(rear, 2 + (int) ((t - 16) * 1.2));
                lost.collapseTo(target, t == 16);
            }
        }
        // 3. whoever has dropped below the platform is out
        double standY = lost != null ? lost.standY : ropeCenter.y - 1.2;
        boolean allDown = true;
        for (Contestant c : losers) {
            if (!c.isAlive()) {
                continue;
            }
            LivingEntity body = c.body(ctx.level);
            if (body != null && body.getY() < standY - 5.0) {
                fellTotal++;
                ctx.eliminate(c, EliminationCause.LOST_TEAM);
                ctx.level.sendParticles(ParticleTypes.POOF, body.getX(), body.getY() + 0.5, body.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            } else {
                allDown = false;
            }
        }
        int limit = scaled(ctx, TugRules.FALL_TICKS);
        if ((allDown && t > 40) || t >= limit) {
            for (Contestant c : losers) {
                if (c.isAlive()) {
                    fellTotal++;
                    ctx.eliminate(c, EliminationCause.LOST_TEAM);
                }
            }
            setStage(Stage.AFTERMATH, now);
        }
    }

    /** Pulls a loser along the platform towards the gap: a velocity applied every tick (NPCs and humans alike). */
    private void drag(GameContext ctx, Contestant c, int team, double speed) {
        LivingEntity body = c.body(ctx.level);
        if (body == null) {
            return;
        }
        double dir = Math.signum(ropeCenter.x - body.getX());
        Vec3 v = body.getDeltaMovement();
        body.setDeltaMovement(dir * speed, v.y, v.z * 0.5);
        body.hasImpulse = true;
        if (body instanceof ServerPlayer p) {
            p.hurtMarked = true;
        }
    }

    // ---- aftermath, between heats

    private void tickAftermath(GameContext ctx, long now) {
        TugHeat h = heat;
        if (now - stageSince < scaled(ctx, AFTERMATH_TICKS)) {
            return;
        }
        for (TugPlatform p : platforms) {
            if (p != null && p.hasChanges()) {
                ctx.soundAt(ropeCenter, ModSounds.DOOR_LOCK, SoundSource.BLOCKS, 3f, 0.8f);
                p.restore();
            }
        }
        if (rope != null) {
            rope.setFall(0, now);
        }
        if (plan != null && heatIndex + 1 < plan.heats().size()) {
            moveToWaiting(ctx, plan.heats().get(heatIndex + 1), h);
            setStage(Stage.BETWEEN, now);
        } else {
            setStage(Stage.DONE, now);
        }
    }

    /** Winners of the last heat go to the gallery, the next heat's teams to their lobbies. */
    private void moveToWaiting(GameContext ctx, TeamPlanner.Heat next, @Nullable TugHeat previous) {
        List<Contestant> a = contestants(ctx, next.teamA());
        List<Contestant> b = contestants(ctx, next.teamB());
        List<Contestant> gallery = new ArrayList<>();
        if (previous != null) {
            for (Contestant c : previous.everyone()) {
                if (c.isAlive()) {
                    gallery.add(c);
                }
            }
        }
        gallery.removeAll(a);
        gallery.removeAll(b);
        Teleporter.spread(ctx.level, gallery, spare);
        Teleporter.spread(ctx.level, a, waiting.get(TugRules.TEAM_A));
        Teleporter.spread(ctx.level, b, waiting.get(TugRules.TEAM_B));
        for (Contestant c : gallery) {
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setBehavior(new WaitingBehavior());
                e.setActivity(Activity.NONE);
            }
        }
        for (Contestant c : ctx.alive()) {
            if (c.isHumanControlled()) {
                ServerPlayer p = c.player(ctx.server());
                if (p != null) {
                    ctx.fade(p, 6, 8, 14, 0xFF000000);
                }
            }
        }
        for (Contestant c : concat(a, b)) {
            ContestantEntity e = ctx.npc(c);
            if (e != null) {
                e.setBehavior(new WaitingBehavior());
                e.setActivity(Activity.NONE);
            }
        }
    }

    private static List<Contestant> concat(List<Contestant> a, List<Contestant> b) {
        List<Contestant> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private void tickBetween(GameContext ctx, long now) {
        if (now - stageSince >= scaled(ctx, BETWEEN_TICKS)) {
            startHeat(ctx, heatIndex + 1);
        }
    }

    // ------------------------------------------------------------------ MiniGame: end of the game

    @Override
    public boolean isFinished(GameContext ctx) {
        return stage == Stage.DONE;
    }

    @Override
    public void onTimeout(GameContext ctx) {
        timedOut = true;
    }

    @Override
    public GameResult conclude(GameContext ctx) {
        List<Contestant> eliminated = new ArrayList<>();
        TugHeat h = heat;
        // a heat that was still going when the clock ran out is decided by the rope (or, if it never started, by a simulation)
        if (h != null && stage != Stage.DONE && stage != Stage.IDLE && h.winner < 0) {
            int winner = decideNow(ctx, h);
            h.winner = winner;
            heatsDecided++;
            for (Contestant c : h.teams.get(1 - winner)) {
                if (c.isAlive()) {
                    eliminated.add(c);
                    ctx.eliminate(c, EliminationCause.LOST_TEAM);
                }
            }
        } else if (h != null && fallLoser >= 0) {
            for (Contestant c : h.teams.get(fallLoser)) {
                if (c.isAlive()) {
                    eliminated.add(c);
                    ctx.eliminate(c, EliminationCause.LOST_TEAM);
                }
            }
        }
        // heats that were never played are resolved by a simulation with the NPC policies (their members could not act anyway)
        if (plan != null) {
            int from = h == null ? 0 : (stage == Stage.DONE ? plan.heats().size() : heatIndex + 1);
            for (int i = from; i < plan.heats().size(); i++) {
                resolveUnplayed(ctx, plan.heats().get(i), i, eliminated);
            }
        }
        List<Contestant> survivors = new ArrayList<>(ctx.alive());
        int teamsFallen = Math.max(heatsDecided, results.size());
        return new GameResult(survivors, eliminated, Component.translatable("squidgame.game.tug_of_war.headline", survivors.size()),
                Component.translatable("squidgame.game.tug_of_war.detail", fellTotal + eliminated.size(), teamsFallen));
    }

    /** The winner of a running heat when the game must end now. */
    private int decideNow(GameContext ctx, TugHeat h) {
        if (h.sim != null && stage == Stage.MATCH) {
            TugSim.Outcome v = h.sim.verdictAtTimeout();
            if (v != TugSim.Outcome.ONGOING) {
                return v == TugSim.Outcome.A_WINS ? TugRules.TEAM_A : TugRules.TEAM_B;
            }
        }
        return simulate(ctx, h.teams, h.handicap[0], h.handicap[1]);
    }

    private void resolveUnplayed(GameContext ctx, TeamPlanner.Heat ph, int index, List<Contestant> eliminated) {
        List<List<Contestant>> teams = List.of(contestants(ctx, ph.teamA()), contestants(ctx, ph.teamB()));
        if (teams.get(0).isEmpty() || teams.get(1).isEmpty()) {
            return;
        }
        int winner = simulate(ctx, teams, ph.handicapA(), ph.handicapB());
        heatsDecided++;
        SquidGameMod.LOGGER.info("Tug of War heat {} was not played: {} wins by simulation", index + 1, winner == 0 ? "RED" : "BLUE");
        for (Contestant c : teams.get(1 - winner)) {
            eliminated.add(c);
            ctx.eliminate(c, EliminationCause.LOST_TEAM, eliminated.size() % 6);
        }
    }

    private int simulate(GameContext ctx, List<List<Contestant>> teams, double handicapA, double handicapB) {
        List<TugMatch.Entrant> entrants = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            for (Contestant c : teams.get(t)) {
                entrants.add(new TugMatch.Entrant(c.number, t, c.personality, c.isHuman()));
            }
        }
        TugMatch.Result r = TugMatch.run(difficulty, entrants, Map.of(), ctx.rng().fork(900 + heatsDecided), null, handicapA, handicapB);
        return r.winner();
    }

    @Override
    public void cleanup(GameContext ctx) {
        for (int i = 0; i < platforms.length; i++) {
            if (platforms[i] != null) {
                platforms[i].restore();
                platforms[i] = null;
            }
        }
        discardRope();
        humans.clear();
        arrived.clear();
        heat = null;
        plan = null;
        stage = Stage.IDLE;
        fallLoser = -1;
        pendingRepair = null;
    }

    // ------------------------------------------------------------------ hooks

    @Override
    public void onContestantEliminated(GameContext ctx, Contestant c, EliminationCause cause) {
        TugHeat h = heat;
        if (h == null || !h.contains(c)) {
            return;
        }
        int i = h.indexOf(c);
        if (i >= 0 && h.sim != null) {
            h.sim.remove(i);
        }
        humans.remove(c.number);
    }

    @Override
    public void onControllerChanged(GameContext ctx, Contestant c) {
        TugHeat h = heat;
        if (h == null || !h.contains(c) || !c.isAlive()) {
            return;
        }
        HumanInput in = humans.get(c.number);
        if (in != null) {
            in.held.clear();
        }
        ContestantEntity e = ctx.npc(c);
        if (e != null && c.isAiControlled()) {
            // the stand-in carries on from where the human left off
            if (stage == Stage.WALK_IN || stage == Stage.COUNT_IN || stage == Stage.MATCH) {
                e.setBehavior(new TugNpcBehavior(this, c, 0));
            } else {
                e.setBehavior(new WaitingBehavior());
            }
        }
    }

    @Override
    public void saveState(CompoundTag tag) {
        boolean any = false;
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < 2; i++) {
            if (platforms[i] != null && platforms[i].hasSnapshot()) {
                platforms[i].save(t, i == 0 ? "a" : "b");
                any = true;
            }
        }
        if (any) {
            tag.put("repair", t);
        } else if (pendingRepair != null) {
            tag.put("repair", pendingRepair);
        }
    }

    @Override
    public void loadState(CompoundTag tag) {
        pendingRepair = tag.contains("repair", Tag.TAG_COMPOUND) ? tag.getCompound("repair") : null;
    }

    // ------------------------------------------------------------------ client input

    @Override
    public void onClientAction(GameContext ctx, Contestant c, ServerPlayer player, String id, CompoundTag data) {
        if (id.startsWith("tug.")) {
            handleInput(ctx, c, player.connection.latency(), id, data);
        }
    }

    /**
     * Validates and applies one message from a human. Dropped without a trace: anything outside a running match, from somebody
     * who is not a human member of the heat, over the rate limit, or malformed. {@code latencyMillis} is the connection's
     * measured round trip, used to move a heave back to the moment the key was pressed.
     */
    void handleInput(GameContext ctx, Contestant c, int latencyMillis, String id, CompoundTag data) {
        TugHeat h = heat;
        if (h == null || stage != Stage.MATCH || h.sim == null || !c.isAlive() || !c.isHumanControlled()) {
            return;
        }
        int member = h.indexOf(c);
        if (member < 0 || !h.sim.member(member).present()) {
            return;
        }
        HumanInput in = humans.computeIfAbsent(c.number, k -> new HumanInput());
        long now = ctx.now();
        if (!in.limiter.allow(now)) {
            return;
        }
        switch (id) {
            case "tug.input" -> {
                if (data.contains("p", Tag.TAG_BYTE) && data.contains("b", Tag.TAG_BYTE)) {
                    in.held.set(data.getBoolean("p"), data.getBoolean("b"), now);
                }
            }
            case "tug.heave" -> {
                if (!in.limiter.allowHeave(now)) {
                    return;
                }
                long press = TugInputGate.pressTick(now, latencyMillis);
                TugSim.HeaveReport r = h.sim.heave(member, press, TugRules.HUMAN_FORGIVENESS);
                recordHeave(ctx, c, in, r);
            }
            default -> {
            }
        }
    }

    private void recordHeave(GameContext ctx, Contestant c, HumanInput in, TugSim.HeaveReport r) {
        int result = switch (r.result()) {
            case HIT -> TugNet.HEAVE_HIT;
            case MISTIMED -> TugNet.HEAVE_MISTIMED;
            case EXHAUSTED -> TugNet.HEAVE_EXHAUSTED;
            case TOO_SOON -> TugNet.HEAVE_TOO_SOON;
            default -> TugNet.HEAVE_NONE;
        };
        if (result == TugNet.HEAVE_NONE || result == TugNet.HEAVE_TOO_SOON) {
            return;
        }
        in.heaveSeq++;
        in.heaveResult = result;
        in.heaveError = r.error();
        in.heaveQuality = (float) r.quality();
        ServerPlayer p = c.player(ctx.server());
        if (p == null) {
            return;
        }
        if (result == TugNet.HEAVE_HIT) {
            Announcer.sound(p, ModSounds.UI_SELECT, 0.8f, 0.8f + 0.5f * (float) r.quality());
            ctx.soundAt(ropeCenter, ModSounds.ROPE_CREAK, SoundSource.NEUTRAL, 0.6f + 1.2f * (float) r.quality(), 1.2f);
        } else if (result == TugNet.HEAVE_MISTIMED) {
            Announcer.sound(p, ModSounds.UI_DENY, 0.5f, 1.2f);
        } else {
            Announcer.sound(p, ModSounds.UI_DENY, 0.6f, 0.7f);
        }
    }

    // ------------------------------------------------------------------ keeping humans in line

    /** A human who wandered off their slot (the platform is a corridor, but it is 5 wide and 50 long) is put back. */
    private void keepHumansInLine(GameContext ctx) {
        TugHeat h = heat;
        if (stage != Stage.COUNT_IN && stage != Stage.MATCH) {
            return;
        }
        for (Contestant c : h.everyone()) {
            if (!c.isHumanControlled() || !c.isAlive() || !arrived.contains(c.number)) {
                continue;
            }
            ServerPlayer p = c.player(ctx.server());
            Marker m = slotMarker(h.team(c), h.slot(c));
            if (p == null || m == null || p.getY() < m.y() - 2.0) {
                continue;
            }
            if (Math.abs(p.getX() - m.x()) > STRAY_ALONG || Math.abs(p.getZ() - m.z()) > STRAY_ACROSS) {
                ctx.teleport(c, new Vec3(m.x(), m.y(), m.z()), p.getYRot());
                p.displayClientMessage(Component.translatable("squidgame.game.tug_of_war.back_in_line"), true);
            }
        }
    }

    // ------------------------------------------------------------------ what NPC behaviours may know and do

    Stage stage() {
        return stage;
    }

    boolean inHeat(Contestant c) {
        return heat != null && heat.contains(c);
    }

    long stageSince() {
        return stageSince;
    }

    void onArrived(Contestant c) {
        arrived.add(c.number);
    }

    /** The slot of a heat member, as a position and a yaw. */
    @Nullable
    Marker slotOf(Contestant c) {
        TugHeat h = heat;
        return h == null || !h.contains(c) ? null : slotMarker(h.team(c), h.slot(c));
    }

    @Nullable
    BeatClock beat() {
        return heat == null ? null : heat.beat;
    }

    boolean suddenDeath() {
        return heat != null && heat.suddenDeath;
    }

    double window() {
        return params.window();
    }

    @Nullable
    TugSim.Member memberOf(Contestant c) {
        return heat == null ? null : heat.member(c);
    }

    int teamOf(Contestant c) {
        return heat == null ? -1 : heat.team(c);
    }

    /** How well the contestant's team is doing as anybody can see it on the rope: +1 = won, -1 = about to fall. */
    double leadOf(Contestant c) {
        TugHeat h = heat;
        if (h == null || h.sim == null) {
            return 0;
        }
        return h.team(c) == TugRules.TEAM_B ? h.sim.offset() : -h.sim.offset();
    }

    /** Share of a team that visibly strains (the animations show it to everybody). */
    double strained(int team) {
        return heat == null || heat.sim == null ? 0 : heat.sim.strainedFraction(team);
    }

    /** Fraction of the current time limit (the heat, or the sudden death) that is left: the clock is public. */
    double timeLeftFraction(long now) {
        TugHeat h = heat;
        return h == null ? 1.0 : Mth.clamp((h.limitTick - now) / (double) Math.max(1, h.limitSpan), 0.0, 1.0);
    }

    /** The difficulty's skill bonus for this NPC: only the NPC team that opposes a team with humans gets it. */
    double skillBonus(Contestant c) {
        TugHeat h = heat;
        if (h == null || !h.contains(c)) {
            return 0;
        }
        boolean[] human = new boolean[2];
        for (Contestant m : h.everyone()) {
            if (m.isHuman()) {
                human[h.team(m)] = true;
            }
        }
        int mine = h.team(c);
        return human[1 - mine] && !human[mine] ? params.npcSkillBonus() : 0.0;
    }

    void npcStance(Contestant c, double effort, boolean brace) {
        TugHeat h = heat;
        if (h == null || h.sim == null || stage != Stage.MATCH) {
            return;
        }
        int i = h.indexOf(c);
        if (i >= 0) {
            h.sim.setStance(i, effort, brace);
        }
    }

    TugSim.HeaveReport npcHeave(Contestant c, long pressTick) {
        TugHeat h = heat;
        int i = h == null ? -1 : h.indexOf(c);
        if (h == null || h.sim == null || i < 0 || stage != Stage.MATCH) {
            return new TugSim.HeaveReport(TugSim.HeaveResult.INACTIVE, 0, 0);
        }
        return h.sim.heave(i, pressTick, 0.0);
    }

    // ------------------------------------------------------------------ HUD and state for clients

    @Override
    public void hudWidgets(GameContext ctx, @Nullable Contestant viewer, List<HudPayload.Widget> out) {
        TugHeat h = heat;
        if (h == null || stage == Stage.IDLE) {
            return;
        }
        int heats = plan == null ? 1 : plan.heats().size();
        out.add(HudPayload.Widget.line("teams", Component.translatable("squidgame.game.tug_of_war.hud.teams", teamName(TugRules.TEAM_A),
                h.teams.get(0).size(), teamName(TugRules.TEAM_B), h.teams.get(1).size())));
        if (heats > 1) {
            out.add(HudPayload.Widget.line("heat", Component.translatable("squidgame.game.tug_of_war.hud.heat", h.index + 1, heats)));
        }
        TugSim sim = h.sim;
        int team = viewer == null || !viewer.isAlive() ? -1 : h.team(viewer);
        if (sim != null && (stage == Stage.MATCH || stage == Stage.FALL)) {
            if (team >= 0) {
                out.add(HudPayload.Widget.bar("stamina", Component.translatable("squidgame.game.tug_of_war.hud.team_stamina"),
                        (float) sim.meanStamina(team), 1f, TEAM_RGB[team]));
            }
            if (stage == Stage.MATCH) {
                int left = (int) Math.max(0, (h.limitTick - ctx.now()) / 20);
                out.add(HudPayload.Widget.counter("time", "icon_timer", Component.translatable("squidgame.game.tug_of_war.hud.time"), left, -1));
                if (team >= 0 && leadOf(viewer) < -0.7) {
                    out.add(HudPayload.Widget.banner("danger", Component.translatable("squidgame.game.tug_of_war.banner.danger"), 0xFF3030));
                } else if (h.suddenDeath) {
                    out.add(HudPayload.Widget.banner("sudden", Component.translatable("squidgame.game.tug_of_war.banner.sudden"), 0xFFB030));
                }
            }
        }
    }

    private void sendStates(GameContext ctx, long now) {
        TugHeat h = heat;
        TugSim sim = h.sim;
        int stageCode = switch (stage) {
            case WALK_IN -> TugNet.STAGE_WALK;
            case COUNT_IN -> TugNet.STAGE_COUNT_IN;
            case MATCH -> h.suddenDeath ? TugNet.STAGE_SUDDEN : TugNet.STAGE_MATCH;
            case FALL, AFTERMATH -> TugNet.STAGE_FALL;
            case BETWEEN -> TugNet.STAGE_BETWEEN;
            default -> TugNet.STAGE_NONE;
        };
        BeatClock beat = h.beat;
        for (ServerPlayer p : Announcer.audience(ctx.server())) {
            Contestant c = ctx.of(p);
            boolean participant = c != null && c.isAlive() && h.contains(c) && c.isHumanControlled();
            if (!participant && now % 4 != 0) {
                continue;
            }
            int team = participant ? h.team(c) : -1;
            int role = participant ? (team == TugRules.TEAM_A ? TugNet.ROLE_TEAM_A : TugNet.ROLE_TEAM_B)
                    : (c != null && c.isAlive() ? TugNet.ROLE_WAIT : TugNet.ROLE_WATCH);
            TugSim.Member m = participant ? h.member(c) : null;
            HumanInput in = participant ? humans.get(c.number) : null;
            float stamina = m == null ? 1f : (float) m.stamina();
            int stance = m == null ? 0 : m.stance().ordinal();
            double lead = participant ? leadOf(c) : 0;
            int danger = lead < -0.75 ? 2 : lead < -0.5 ? 1 : 0;
            float sync = sim == null ? 0f : (float) (team >= 0 ? sim.sync(team) : Math.max(sim.sync(0), sim.sync(1)));
            int timeLeft = sim == null || stage != Stage.MATCH ? 0 : (int) Math.max(0, h.limitTick - now);
            TugNet.StatePayload payload = new TugNet.StatePayload(stageCode, role, now, beat == null ? 0 : beat.epoch,
                    beat == null ? 28 : beat.period, (float) params.window(),
                    sim == null ? 0f : (float) sim.offset(), sim == null ? 0f : (float) sim.velocity(), sim == null ? 0f : (float) sim.strain(),
                    stamina, stance, m != null && m.exhausted(),
                    sim == null ? 1f : (float) sim.meanStamina(0), sim == null ? 1f : (float) sim.meanStamina(1), sync,
                    sim == null ? h.teams.get(0).size() : sim.presentCount(0), sim == null ? h.teams.get(1).size() : sim.presentCount(1),
                    timeLeft, in == null ? 0 : in.heaveSeq, in == null ? 0 : in.heaveResult, in == null ? 0 : in.heaveError,
                    in == null ? 0f : in.heaveQuality, danger);
            ModNetwork.send(p, payload);
        }
    }
}
