package com.squidgame.game.marbles;

import com.squidgame.SquidGameMod;
import com.squidgame.core.marbles.MarbleLedger;
import com.squidgame.core.marbles.MarblesRules;
import com.squidgame.core.marbles.MatchOutcome;
import com.squidgame.core.marbles.OddEvenDuel;
import com.squidgame.core.marbles.OddEvenRound;
import com.squidgame.core.marbles.OddEvenRound.Reveal;
import com.squidgame.core.marbles.Parity;
import com.squidgame.core.marbles.Side;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.net.HudPayload;
import com.squidgame.net.OpenScreenPayload;
import com.squidgame.registry.ModItems;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The odd-or-even match of one pair. Both partners decide at the same time (the holder hides marbles, the guesser
 * wagers and calls), so nobody can read anything from the other's timing; when both have locked in - or the decision
 * timer ran out and a random legal move was made - the fist opens, the verdict is shown and the marbles change hands.
 *
 * <pre>
 *  INTRO -> DECIDE -> LOCKED -> REVEAL -> (next round | END)
 * </pre>
 * The duel ({@link OddEvenDuel}) already knows the result when the reveal starts; the HUD, hotbar and table only
 * catch up at the moment of the verdict so nothing is spoiled.
 */
final class OddEvenMatch extends Match {
    static final String SCREEN = MarblesNet.SCREEN_ODD_EVEN;

    enum State {INTRO, DECIDE, LOCKED, REVEAL, END}

    private final OddEvenDuel duel;
    private State state = State.INTRO;
    private long stateEnd;
    private long decisionEnds;
    private int decisionTotal;
    private long decisionStart;
    private boolean timeCalled;
    private Reveal reveal;
    private boolean verdictShown;
    private long verdictAt;
    private boolean overtimeAnnounced;

    OddEvenMatch(MarblesGame game, Plot plot, Contestant a, Contestant b, Rng rng) {
        super(game, plot, a, b, MarblesRules.Variant.ODD_EVEN, rng);
        Side first = rng.nextBoolean() ? Side.A : Side.B;
        this.duel = new OddEvenDuel(game.params().startMarbles(), first, game.params().oddEvenRounds());
    }

    @Override
    MarbleLedger ledger() {
        return duel.ledger();
    }

    // ------------------------------------------------------------------ public view for NPC behaviours

    State state() {
        return state;
    }

    boolean deciding() {
        return state == State.DECIDE && !decided();
    }

    int roundNo() {
        return duel.roundNo();
    }

    Side holderSide() {
        return duel.round().holder();
    }

    int maxHold() {
        return duel.round().maxHold();
    }

    boolean wagerForced() {
        return duel.round().wagerIsForced();
    }

    /** The marbles a side owns right now (public: both stacks are on the table). */
    int marbles(Side s) {
        return duel.ledger().count(s);
    }

    /** Revealed rounds so far: the only history anybody may read. */
    List<Reveal> publicHistory() {
        return duel.history();
    }

    long decisionStart() {
        return decisionStart;
    }

    int decisionTotalTicks() {
        return decisionTotal;
    }

    boolean hasLockedIn(Side s) {
        OddEvenRound r = duel.round();
        return r.holder() == s ? r.holdLocked() : r.guessLocked();
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    void begin(long now) {
        setUp();
        state = State.INTRO;
        stateEnd = now + ticks(params.introSeconds());
        faceEachOther();
        toPartners((c, p) -> sendState(c, p, now));
    }

    @Override
    void tick(long now) {
        if (decided()) {
            tickEnd(now);
            return;
        }
        switch (state) {
            case INTRO -> {
                if (now >= stateEnd) {
                    startRound(now);
                }
            }
            case DECIDE -> {
                OddEvenRound r = duel.round();
                if (r.isComplete()) {
                    enterLocked(now);
                } else if (now >= decisionEnds) {
                    autoPlay(now);
                    enterLocked(now);
                }
            }
            case LOCKED -> {
                if (now >= stateEnd) {
                    enterReveal(now);
                }
            }
            case REVEAL -> {
                if (!verdictShown && now >= verdictAt) {
                    showVerdict(now);
                }
                if (now >= stateEnd) {
                    afterRound(now);
                }
            }
            case END -> {
            }
        }
    }

    private void startRound(long now) {
        state = State.DECIDE;
        decisionStart = now;
        decisionTotal = ticks(duel.isOvertime() ? params.overtimeDecisionSeconds() : params.decisionSeconds());
        decisionEnds = now + decisionTotal;
        reveal = null;
        verdictShown = false;
        faceEachOther();
        Side holder = duel.round().holder();
        pose(of(holder), Activity.NONE);
        pose(of(holder.other()), Activity.THINK);
        toPartners((c, p) -> {
            sendState(c, p, now);
            p.playNotifySound(ModSounds.UI_SELECT, SoundSource.MASTER, 0.7f, 1.3f);
            if (shown(sideOf(c)) <= 2 || duel.isOvertime()) {
                ctx.danger(p, 0.25f, 40, 2, 0xFFC01010);
            }
        });
    }

    /** The decision timer ran out: random legal moves for whoever has not locked in. */
    private void autoPlay(long now) {
        OddEvenRound r = duel.round();
        for (Side s : Side.values()) {
            boolean missing = r.holder() == s ? !r.holdLocked() : !r.guessLocked();
            Contestant c = of(s);
            if (missing && c.isHumanControlled()) {
                game.tell(c, Component.translatable("squidgame.game.marbles.auto_move"));
            }
        }
        r.autoHold(rng);
        r.autoGuess(rng);
    }

    private void enterLocked(long now) {
        state = State.LOCKED;
        stateEnd = now + ticks(1.0);
        Vec3 mid = midpoint();
        soundAt(mid, ModSounds.MARBLE_CLICK, 0.8f, 0.8f);
        toPartners((c, p) -> sendState(c, p, now));
    }

    private void enterReveal(long now) {
        reveal = duel.resolveRound();
        if (ctx.config().debug) {
            SquidGameMod.LOGGER.info("Marbles reveal k={} round {}: holder No.{} hid {}, guesser No.{} called {} x{} -> {} ({} moved), marbles {}",
                    plot.k(), reveal.round(), of(reveal.holder()).displayNumber(), reveal.hidden(), of(reveal.guesser()).displayNumber(),
                    reveal.guess(), reveal.wager(), reveal.correct() ? "right" : "wrong", reveal.moved(), duel.ledger());
        }
        state = State.REVEAL;
        verdictShown = false;
        verdictAt = now + ticks(params.revealSeconds() * 0.62);
        stateEnd = now + ticks(params.revealSeconds() + params.resultSeconds());
        Contestant holder = of(reveal.holder());
        gesture(holder, "marble_reveal");
        pose(holder, Activity.NONE);
        pose(of(reveal.guesser()), Activity.NONE);
        Vec3 mid = midpoint();
        soundAt(mid, ModSounds.MARBLE_CLICK, 1.0f, 1.0f);
        ctx.schedule(ticks(0.4), () -> soundAt(mid, ModSounds.MARBLE_CLICK, 0.9f, 1.15f));
        ctx.schedule(ticks(0.8), () -> soundAt(mid, ModSounds.MARBLE_CLICK, 0.9f, 1.3f));
        toPartners((c, p) -> sendState(c, p, now));
    }

    /** The moment of truth: marbles fly from the loser to the winner and every counter catches up. */
    private void showVerdict(long now) {
        verdictShown = true;
        Contestant winner = of(reveal.winner());
        Contestant loser = of(reveal.winner().other());
        Vec3 from = chest(loser), to = chest(winner);
        int n = Math.min(12, 3 + reveal.moved() * 2);
        ItemParticleOption marble = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.MARBLE));
        Vec3 dir = to.subtract(from);
        for (int i = 0; i < n; i++) {
            double lift = 0.12 + 0.06 * (i % 3);
            ctx.level.sendParticles(marble, from.x, from.y, from.z, 0, dir.x * 0.07, lift + dir.y * 0.05, dir.z * 0.07, 1.0 + 0.1 * (i % 4));
        }
        soundAt(to, ModSounds.MARBLE_DROP, 1.0f, 1.0f);
        soundAt(from, ModSounds.MARBLE_ROLL, 0.8f, 1.0f);
        syncShown();
        gesture(winner, "nod");
        gesture(loser, "shocked");
        if (ledger().isOut(reveal.winner().other())) {
            ctx.level.sendParticles(ParticleTypes.CRIT, from.x, from.y + 0.3, from.z, 24, 0.4, 0.3, 0.4, 0.2);
        }
        toPartners((c, p) -> {
            boolean won = reveal.winner() == sideOf(c);
            p.playNotifySound(won ? ModSounds.UI_CONFIRM : ModSounds.UI_DENY, SoundSource.MASTER, 0.9f, won ? 1.2f : 0.9f);
            if (!won && reveal.moved() >= Math.max(3, shown(sideOf(c)) / 2)) {
                ctx.danger(p, 0.35f, 30, 2, 0xFFC01010);
            }
        });
    }

    private void afterRound(long now) {
        if (!verdictShown) {
            showVerdict(now);
        }
        if (duel.over()) {
            finish(duel.outcome(), now);
            return;
        }
        duel.advance(timeCalled);
        if (duel.over()) {
            finish(duel.outcome(), now);
            return;
        }
        announceOvertime();
        startRound(now);
    }

    private void announceOvertime() {
        if (duel.isOvertime() && !overtimeAnnounced) {
            overtimeAnnounced = true;
            toPartners((c, p) -> {
                Announcer.title(p, Component.translatable("squidgame.game.marbles.overtime.title"),
                        Component.translatable("squidgame.game.marbles.overtime.subtitle"), 3, 50, 10);
                p.playNotifySound(ModSounds.DANGER_STING, SoundSource.MASTER, 1.0f, 1.0f);
            });
        }
    }

    // ------------------------------------------------------------------ decisions (humans and NPCs)

    /** The holder locks in how many marbles are in the fist. */
    boolean submitHold(Contestant who, int count) {
        if (state != State.DECIDE || decided() || !involves(who)) {
            return false;
        }
        Side s = sideOf(who);
        if (!duel.round().submitHold(s, count).accepted()) {
            return false;
        }
        onLockedIn(s);
        return true;
    }

    /** The guesser locks in the wager and the call. */
    boolean submitGuess(Contestant who, int wager, Parity call) {
        if (state != State.DECIDE || decided() || !involves(who)) {
            return false;
        }
        Side s = sideOf(who);
        if (!duel.round().submitGuess(s, wager, call).accepted()) {
            return false;
        }
        onLockedIn(s);
        return true;
    }

    private void onLockedIn(Side s) {
        Contestant c = of(s);
        Vec3 pos = plot.padPos(s);
        soundAt(pos, ModSounds.MARBLE_CLICK, 0.5f, 1.4f);
        if (duel.round().holder() == s) {
            pose(c, Activity.MARBLE_HOLD);
        } else {
            gesture(c, "marble_guess");
            pose(c, Activity.NONE);
        }
        long now = game.clock();
        toPartners((p0, p) -> sendState(p0, p, now));
    }

    // ------------------------------------------------------------------ time, forfeits, end

    @Override
    void timeCall(long now) {
        if (decided() || timeCalled) {
            return;
        }
        timeCalled = true;
        toPartners((c, p) -> {
            Announcer.title(p, Component.translatable("squidgame.game.marbles.time.title"),
                    Component.translatable("squidgame.game.marbles.time.subtitle"), 3, 40, 10);
            p.playNotifySound(ModSounds.ANNOUNCE_CHIME_ALERT, SoundSource.MASTER, 1.0f, 1.0f);
        });
        boolean running = state == State.INTRO || (state == State.DECIDE && !duel.round().isComplete());
        if (running) {
            duel.settleByCount();
            if (duel.over()) {
                finish(duel.outcome(), now);
            } else {
                announceOvertime();
                startRound(now);
            }
        }
    }

    @Override
    void forceResolve(long now) {
        if (decided()) {
            return;
        }
        if (!duel.over()) {
            duel.forceResolve(rng);
        }
        finish(duel.outcome(), now);
    }

    @Override
    void forfeit(Side loser, long now) {
        if (decided()) {
            return;
        }
        duel.forfeit(loser);
        finish(duel.outcome(), now);
    }

    @Override
    void onDecided(long now) {
        state = State.END;
        syncShown();
        toPartners((c, p) -> sendState(c, p, now));
        ctx.schedule(ticks(3.4), () -> toPartners((c, p) -> ctx.screen(p, SCREEN, OpenScreenPayload.CLOSE, new CompoundTag())));
    }

    @Override
    void release() {
        toPartners((c, p) -> ctx.screen(p, SCREEN, OpenScreenPayload.CLOSE, new CompoundTag()));
    }

    @Override
    String summary() {
        MatchOutcome o = outcome();
        return String.format("match k=%d odd_even No.%s vs No.%s -> No.%s wins (%s, %d rounds, marbles %s)", plot.k(), a.displayNumber(),
                b.displayNumber(), winner().displayNumber(), o.reason(), duel.history().size(), duel.ledger());
    }

    // ------------------------------------------------------------------ human UI

    @Override
    void resendUi(Contestant c) {
        ServerPlayer p = c.player(ctx.server());
        if (p != null && c.isAlive() && !closed()) {
            sendState(c, p, game.clock());
        }
    }

    private void sendState(Contestant c, ServerPlayer p, long now) {
        ctx.screen(p, SCREEN, OpenScreenPayload.UPDATE, stateFor(c, now));
    }

    /** The screen's state for one partner: public information plus that partner's own locked-in choice. */
    CompoundTag stateFor(Contestant viewer, long now) {
        Side me = sideOf(viewer);
        Contestant opp = opponentOf(viewer);
        OddEvenRound r = duel.round();
        CompoundTag t = new CompoundTag();
        t.putString("phase", switch (state) {
            case INTRO -> "intro";
            case DECIDE -> "decide";
            case LOCKED -> "locked";
            case REVEAL -> "reveal";
            case END -> "over";
        });
        t.putInt("round", duel.roundNo());
        t.putInt("maxRounds", duel.maxRounds());
        t.putBoolean("overtime", duel.isOvertime());
        t.putInt("me", viewer.number);
        t.putInt("opp", opp.number);
        t.putString("oppName", opp.name);
        int mine = shown(me), theirs = shown(me.other());
        if (state == State.REVEAL && verdictShown && reveal != null) {
            // the screen applies the verdict itself when its timeline reaches it: send the stacks as they were before it
            int moved = reveal.winner() == me ? reveal.moved() : -reveal.moved();
            mine -= moved;
            theirs += moved;
        }
        t.putInt("myMarbles", mine);
        t.putInt("oppMarbles", theirs);
        boolean holder = r.holder() == me;
        t.putString("role", holder ? "hold" : "guess");
        t.putInt("maxHold", r.maxHold());
        t.putInt("maxWager", r.maxWager());
        t.putBoolean("forced", r.wagerIsForced());
        boolean myLocked = holder ? r.holdLocked() : r.guessLocked();
        t.putBoolean("myLocked", myLocked);
        t.putBoolean("oppLocked", holder ? r.guessLocked() : r.holdLocked());
        if (myLocked) {
            t.putInt("myHold", r.ownHold(me));
            Parity g = r.ownGuess(me);
            if (g != null) {
                t.putBoolean("myGuessOdd", g == Parity.ODD);
                t.putInt("myWager", r.ownWager(me));
            }
        }
        t.putInt("remaining", state == State.DECIDE ? (int) Math.max(0, decisionEnds - now) : 0);
        t.putInt("total", decisionTotal);
        if (reveal != null && state == State.REVEAL) {
            CompoundTag rv = new CompoundTag();
            rv.putInt("hidden", reveal.hidden());
            rv.putBoolean("guessOdd", reveal.guess() == Parity.ODD);
            rv.putInt("wager", reveal.wager());
            rv.putBoolean("correct", reveal.correct());
            rv.putBoolean("iWon", reveal.winner() == me);
            rv.putBoolean("iHeld", reveal.holder() == me);
            rv.putInt("delta", reveal.winner() == me ? reveal.moved() : -reveal.moved());
            rv.putInt("revealTicks", ticks(params.revealSeconds()));
            rv.putInt("verdictIn", (int) Math.max(0, verdictAt - now));
            t.put("rev", rv);
        }
        ListTag hist = new ListTag();
        List<Reveal> all = duel.history();
        int end = state == State.REVEAL ? all.size() - 1 : all.size();
        for (int i = Math.max(0, end - 6); i < end; i++) {
            Reveal h = all.get(i);
            CompoundTag e = new CompoundTag();
            e.putInt("r", h.round());
            e.putBoolean("held", h.holder() == me);
            e.putInt("n", h.hidden());
            e.putBoolean("odd", h.guess() == Parity.ODD);
            e.putInt("w", h.wager());
            e.putBoolean("ok", h.correct());
            e.putInt("d", h.winner() == me ? h.moved() : -h.moved());
            hist.add(e);
        }
        t.put("hist", hist);
        if (decided()) {
            CompoundTag over = new CompoundTag();
            over.putBoolean("won", winner().number == viewer.number);
            over.putString("reason", outcome().reason().name().toLowerCase(java.util.Locale.ROOT));
            t.put("over", over);
        }
        return t;
    }

    // ------------------------------------------------------------------ HUD

    @Override
    void hudExtra(Contestant viewer, Side me, List<HudPayload.Widget> out) {
        if (decided()) {
            return;
        }
        boolean holder = duel.round().holder() == me && state != State.INTRO;
        Component role = state == State.INTRO ? Component.translatable("squidgame.game.marbles.hud.intro")
                : Component.translatable(holder ? "squidgame.game.marbles.hud.role.hold" : "squidgame.game.marbles.hud.role.guess");
        out.add(HudPayload.Widget.line("round", Component.translatable(duel.isOvertime() ? "squidgame.game.marbles.hud.round.overtime"
                : "squidgame.game.marbles.hud.round", duel.roundNo(), duel.maxRounds(), role)));
        if (state == State.DECIDE) {
            long left = Math.max(0, decisionEnds - game.clock());
            out.add(HudPayload.Widget.bar("decide", Component.translatable("squidgame.game.marbles.hud.decide"), left, decisionTotal, 0xE0457B));
        }
    }

    // ------------------------------------------------------------------ helpers

    private Vec3 midpoint() {
        Vec3 pa = plot.padPos(Side.A), pb = plot.padPos(Side.B);
        return new Vec3((pa.x + pb.x) / 2, pa.y + 1.2, (pa.z + pb.z) / 2);
    }

    private Vec3 chest(Contestant c) {
        Vec3 p = c.position(ctx.level);
        return p == null ? midpoint() : p.add(0, 1.15, 0);
    }
}
