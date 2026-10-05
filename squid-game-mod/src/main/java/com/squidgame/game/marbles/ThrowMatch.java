package com.squidgame.game.marbles;

import com.squidgame.SquidGameMod;
import com.squidgame.core.marbles.MarbleLedger;
import com.squidgame.core.marbles.MarblesRules;
import com.squidgame.core.marbles.MatchOutcome;
import com.squidgame.core.marbles.Side;
import com.squidgame.core.marbles.ThrowDuel;
import com.squidgame.core.marbles.ThrowModel;
import com.squidgame.core.marbles.ThrowScoring;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.MarbleProjectile;
import com.squidgame.net.HudPayload;
import com.squidgame.net.ModNetwork;
import com.squidgame.registry.ModItems;
import com.squidgame.registry.ModSounds;
import com.squidgame.tournament.Announcer;
import com.squidgame.tournament.Contestant;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The target-throw match of one pair. The partners take turns throwing a marble at the bullseye of their plot (the
 * first thrower alternates every round); the marble that rests closer wins marbles from the other.
 *
 * <pre>
 *  INTRO -> TURN -> FLIGHT -> LANDED -> (TURN of the partner | ROUND_RESULT) -> ... -> END
 * </pre>
 * A human throws with the vanilla use key (charge, release); the server turns the release into a launch with
 * {@link ThrowModel}. An NPC plans its throw with the same model and submits it through the same method. The flight is
 * flown by a real {@link MarbleProjectile}; the match scores the spot where it comes to rest.
 */
final class ThrowMatch extends Match {
    enum State {INTRO, TURN, FLIGHT, LANDED, ROUND_RESULT, END}

    private static final int TINT_A = 0x55C8FF;
    private static final int TINT_B = 0xFFA030;

    private final ThrowDuel duel;
    private final ThrowModel.Params tm;
    private State state = State.INTRO;
    private long stateEnd;
    private long turnStart;
    private long turnEnds;
    private int turnTotal;
    private Side turnSide;
    private long flightStart;
    private Vec3 pendingLanding;
    private boolean timeCalled;
    private boolean suddenDeathAnnounced;
    private final List<MarbleProjectile> onFloor = new ArrayList<>();
    private ThrowDuel.RoundResult lastRound;
    private Vec3 predictedLanding;

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    ThrowMatch(MarblesGame game, Plot plot, Contestant a, Contestant b, Rng rng) {
        super(game, plot, a, b, MarblesRules.Variant.THROW, rng);
        this.tm = game.params().throwModel();
        Side first = rng.nextBoolean() ? Side.A : Side.B;
        this.duel = new ThrowDuel(game.params().startMarbles(), first, game.params().throwRounds(), game.params().stakeCap());
    }

    @Override
    MarbleLedger ledger() {
        return duel.ledger();
    }

    // ------------------------------------------------------------------ public view for NPC behaviours

    State state() {
        return state;
    }

    /** The side whose throw it is while a throw can be submitted, else null. */
    Side turnSide() {
        return state == State.TURN && !decided() ? turnSide : null;
    }

    long turnStart() {
        return turnStart;
    }

    int roundNo() {
        return duel.roundNo();
    }

    int marbles(Side s) {
        return duel.ledger().count(s);
    }

    boolean suddenDeath() {
        return duel.isSuddenDeath();
    }

    Vec3 bullseye() {
        return plot.bullseye();
    }

    /** Where a marble leaves an NPC's hand: in front of the chest, towards the target. */
    Vec3 handOrigin(Contestant c) {
        Vec3 p = c.position(ctx.level);
        if (p == null) {
            p = plot.padPos(sideOf(c));
        }
        return p.add(plot.laneDir().scale(0.35)).add(0, 1.05, 0);
    }

    ThrowModel.Params model() {
        return tm;
    }

    /** 0..1: how much this partner has riding on the next throw (behind, nearly out of marbles, sudden death). */
    double pressure(Side me) {
        if (duel.isSuddenDeath()) {
            return 1.0;
        }
        double lead = (marbles(me) - marbles(me.other())) / (double) Math.max(1, marbles(me) + marbles(me.other()));
        double p = 0.3 - lead;
        if (marbles(me) <= 2) {
            p += 0.3;
        }
        return Rng.clamp01(p);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    void begin(long now) {
        setUp();
        state = State.INTRO;
        stateEnd = now + ticks(params.introSeconds());
        for (Side s : Side.values()) {
            var e = ctx.npc(of(s));
            if (e != null && of(s).isAiControlled()) {
                e.faceYaw(Plot.yawToward(plot.padPos(s), plot.bullseye()));
            }
        }
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
                    startTurn(now);
                }
            }
            case TURN -> {
                reticle(now);
                if (now >= turnEnds) {
                    autoThrow(now);
                }
            }
            case FLIGHT -> {
                if (pendingLanding != null) {
                    land(now);
                } else if (now - flightStart > (long) (params.flightSeconds() * 20) + 60) {
                    // the marble vanished (never landed; a flight is physical time, not scaled): count it where the thrower stands
                    pendingLanding = plot.padPos(turnSide);
                    land(now);
                }
            }
            case LANDED -> {
                if (now >= stateEnd) {
                    if (duel.roundComplete()) {
                        enterRoundResult(now);
                    } else {
                        startTurn(now);
                    }
                }
            }
            case ROUND_RESULT -> {
                if (now >= stateEnd) {
                    afterRound(now);
                }
            }
            case END -> {
            }
        }
    }

    private void startTurn(long now) {
        turnSide = duel.turn();
        if (turnSide == null) {
            return;
        }
        state = State.TURN;
        turnStart = now;
        // the charge is physical time (the use key has to be held), so even a fast game gives every throw enough of it
        turnTotal = Math.max(ticks(duel.isSuddenDeath() ? params.overtimeThrowSeconds() : params.throwSeconds()),
                tm.maxChargeTicks() + 40);
        turnEnds = now + turnTotal;
        Contestant thrower = of(turnSide);
        Contestant waiting = of(turnSide.other());
        for (Side s : Side.values()) {
            var e = ctx.npc(of(s));
            if (e != null && of(s).isAiControlled()) {
                e.faceYaw(Plot.yawToward(plot.padPos(s), plot.bullseye()));
                if (s != turnSide) {
                    e.setActivity(Activity.NONE);
                }
            }
        }
        sendThrowState(thrower, true);
        sendThrowState(waiting, false);
        if (ctx.config().debug) {
            SquidGameMod.LOGGER.info("Marbles turn k={} round {}: No.{} throws ({}), timer {} ticks", plot.k(), duel.roundNo(),
                    thrower.displayNumber(), thrower.isHumanControlled() ? "human" : "npc", turnTotal);
        }
        if (thrower.isHumanControlled()) {
            ServerPlayer p = thrower.player(ctx.server());
            if (p != null) {
                game.giveMarbles(thrower, shown(turnSide));
                p.playNotifySound(ModSounds.UI_SELECT, SoundSource.MASTER, 0.8f, 1.4f);
            }
        }
        if (waiting.isHumanControlled()) {
            game.tell(waiting, Component.translatable("squidgame.game.marbles.throw.their_turn", thrower.displayNumber()));
        }
    }

    private void sendThrowState(Contestant c, boolean active) {
        if (!c.isHumanControlled()) {
            return;
        }
        ServerPlayer p = c.player(ctx.server());
        if (p != null) {
            ModNetwork.send(p, active
                    ? new ThrowStatePayload(true, plot.floorY(), game.difficulty().ordinal(), (int) Math.max(0, turnEnds - game.clock()), turnTotal)
                    : ThrowStatePayload.inactive());
        }
    }

    /** While the human thrower charges, a dust ring on the floor shows where the marble would land (visible to them only). */
    private void reticle(long now) {
        if (now % 2 != 0) {
            return;
        }
        Contestant c = of(turnSide);
        if (!c.isHumanControlled()) {
            return;
        }
        ServerPlayer p = c.player(ctx.server());
        if (p == null || !p.isUsingItem() || !p.getUseItem().is(ModItems.MARBLE)) {
            return;
        }
        Vec3 aim = aimOf(p);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.2f), 0.7f);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            ctx.level.sendParticles(p, dust, true, aim.x + Math.cos(ang) * 0.3, aim.y + 0.05, aim.z + Math.sin(ang) * 0.3, 1, 0, 0, 0, 0);
        }
        ctx.level.sendParticles(p, dust, true, aim.x, aim.y + 0.05, aim.z, 1, 0, 0, 0, 0);
    }

    private Vec3 launchOrigin(ServerPlayer p) {
        return new Vec3(p.getX(), p.getEyeY() - 0.1, p.getZ());
    }

    private Vec3 aimOf(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        ThrowModel.Vec aim = ThrowModel.aimFromLook(tm, vec(launchOrigin(p)), vec(look), plot.floorY());
        return new Vec3(aim.x(), aim.y(), aim.z());
    }

    private static ThrowModel.Vec vec(Vec3 v) {
        return new ThrowModel.Vec(v.x, v.y, v.z);
    }

    // ------------------------------------------------------------------ throws (humans and NPCs)

    /** The human released the charged marble: validate position and timing, then throw. */
    void onHumanRelease(Contestant c, ServerPlayer p, int charged) {
        if (decided() || !involves(c)) {
            return;
        }
        Side s = sideOf(c);
        if (state != State.TURN || turnSide != s) {
            p.displayClientMessage(Component.translatable("squidgame.game.marbles.throw.not_turn"), true);
            return;
        }
        if (charged < tm.minChargeTicks()) {
            p.displayClientMessage(Component.translatable("squidgame.game.marbles.throw.too_weak"), true);
            return;
        }
        Vec3 pos = p.position();
        if (plot.alongLine(pos) > 0.35 || pos.distanceTo(plot.padPos(s)) > 4.5 || !plot.near(pos, 2.0)) {
            p.displayClientMessage(Component.translatable("squidgame.game.marbles.throw.behind_line"), true);
            p.playNotifySound(ModSounds.UI_DENY, SoundSource.MASTER, 0.8f, 1.0f);
            return;
        }
        submitThrow(c, launchOrigin(p), aimOf(p), charged);
    }

    /** One throw per turn. Returns false (and changes nothing) unless it is {@code who}'s turn. */
    boolean submitThrow(Contestant who, Vec3 origin, Vec3 aim, int chargeTicks) {
        if (state != State.TURN || decided() || !involves(who) || sideOf(who) != turnSide || chargeTicks < tm.minChargeTicks()) {
            return false;
        }
        launch(turnSide, origin, aim, Math.min(chargeTicks, tm.maxChargeTicks()));
        return true;
    }

    private void autoThrow(long now) {
        Contestant c = of(turnSide);
        ServerPlayer p = c.isHumanControlled() ? c.player(ctx.server()) : null;
        Vec3 origin = p != null ? launchOrigin(p) : handOrigin(c);
        Vec3 b = plot.bullseye();
        Vec3 aim = new Vec3(b.x + rng.gaussian(0, 2.0), b.y, b.z + rng.gaussian(0, 2.0));
        double ideal = ThrowModel.idealCharge(tm, vec(origin), vec(aim));
        int charge = (int) Math.round(Rng.clamp(ideal + rng.gaussian(0, 5.0), tm.minChargeTicks(), tm.maxChargeTicks()));
        if (c.isHumanControlled()) {
            game.tell(c, Component.translatable("squidgame.game.marbles.throw.too_slow"));
        }
        launch(turnSide, origin, aim, charge);
    }

    private void launch(Side side, Vec3 origin, Vec3 aim, int charge) {
        ThrowModel.Vec v = ThrowModel.launch(tm, vec(origin), vec(aim), charge, rng);
        if (ctx.config().debug) {
            ThrowModel.Landing predicted = ThrowModel.landing(vec(origin), v, plot.floorY());
            predictedLanding = new Vec3(predicted.x(), predicted.y(), predicted.z());
            SquidGameMod.LOGGER.info("Marbles throw k={} No.{} charge={} aim=({}, {}) predicted=({}, {}) speed={}", plot.k(),
                    of(side).displayNumber(), charge, f(aim.x), f(aim.z), f(predicted.x()), f(predicted.z()),
                    f(Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z())));
        }
        MarbleProjectile m = new MarbleProjectile(ctx.level, origin.x, origin.y, origin.z);
        m.addTag("squidgame_temp");
        m.setTint(side == Side.A ? TINT_A : TINT_B);
        m.launch(new Vec3(v.x(), v.y(), v.z()), plot.floorY(), landed -> pendingLanding = landed.landingPos());
        ctx.level.addFreshEntity(m);
        onFloor.add(m);
        pendingLanding = null;
        state = State.FLIGHT;
        flightStart = game.clock();
        Contestant c = of(side);
        gesture(c, "marble_release");
        pose(c, Activity.NONE);
        soundAt(origin, ModSounds.MARBLE_CLICK, 0.9f, 1.4f);
        sendThrowState(c, false);
    }

    private void land(long now) {
        Vec3 at = pendingLanding;
        pendingLanding = null;
        Vec3 b = plot.bullseye();
        ThrowScoring.Result r = ThrowScoring.score(at.x - b.x, at.z - b.z);
        if (ctx.config().debug && predictedLanding != null) {
            SquidGameMod.LOGGER.info("Marbles landing k={} No.{} landed=({}, {}) off-prediction={} ring={} distance={}", plot.k(),
                    of(turnSide).displayNumber(), f(at.x), f(at.z), f(Math.hypot(at.x - predictedLanding.x, at.z - predictedLanding.z)),
                    r.ring(), f(r.distance()));
        }
        duel.recordThrow(turnSide, r);
        state = State.LANDED;
        stateEnd = now + ticks(params.landingHoldSeconds());
        Contestant c = of(turnSide);
        int tint = turnSide == Side.A ? TINT_A : TINT_B;
        DustParticleOptions ring = new DustParticleOptions(new Vector3f(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f), 1.0f);
        for (int i = 0; i < 14; i++) {
            double ang = i * Math.PI * 2 / 14;
            ctx.level.sendParticles(ring, at.x + Math.cos(ang) * 0.3, at.y + 0.08, at.z + Math.sin(ang) * 0.3, 1, 0, 0, 0, 0);
        }
        ctx.level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.1, at.z, 5, 0.12, 0.05, 0.12, 0.05);
        soundAt(at, ModSounds.MARBLE_DROP, 1.0f, 1.0f);
        ctx.schedule(ticks(0.3), () -> soundAt(at, ModSounds.MARBLE_ROLL, 0.5f, 1.2f));
        if (r.ring() >= 4) {
            soundAt(at, ModSounds.UI_NUMBER_CALL, 1.0f, r.ring() == ThrowScoring.BULLSEYE ? 1.5f : 1.1f);
            gesture(c, "nod");
        } else if (!r.onTarget()) {
            gesture(c, "shake_head");
        }
        Component msg = Component.translatable("squidgame.game.marbles.throw.result", c.displayNumber(),
                Component.translatable("squidgame.game.marbles.ring." + ThrowScoring.ringKey(r.ring())),
                String.format(Locale.ROOT, "%.1f", r.distance()));
        toPartners((x, p) -> p.displayClientMessage(msg, true));
    }

    private void enterRoundResult(long now) {
        lastRound = duel.resolveRound();
        state = State.ROUND_RESULT;
        stateEnd = now + ticks(params.resultSeconds());
        syncShown();
        ThrowDuel.RoundResult rr = lastRound;
        if (rr.winner() == null) {
            toPartners((c, p) -> Announcer.title(p, Component.translatable("squidgame.game.marbles.throw.draw"), Component.empty(), 2, 30, 8));
            return;
        }
        Contestant w = of(rr.winner()), l = of(rr.winner().other());
        gesture(w, "nod");
        gesture(l, "shocked");
        Vec3 from = l.position(ctx.level) == null ? plot.padPos(rr.winner().other()) : l.position(ctx.level).add(0, 1.1, 0);
        Vec3 to = w.position(ctx.level) == null ? plot.padPos(rr.winner()) : w.position(ctx.level).add(0, 1.1, 0);
        if (rr.moved() > 0) {
            var marble = new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, new net.minecraft.world.item.ItemStack(ModItems.MARBLE));
            Vec3 dir = to.subtract(from);
            for (int i = 0; i < Math.min(12, 3 + rr.moved() * 2); i++) {
                ctx.level.sendParticles(marble, from.x, from.y, from.z, 0, dir.x * 0.07, 0.12 + 0.06 * (i % 3) + dir.y * 0.05, dir.z * 0.07, 1.0 + 0.1 * (i % 4));
            }
        }
        toPartners((c, p) -> {
            boolean won = rr.winner() == sideOf(c);
            Component title = rr.moved() > 0
                    ? Component.translatable(won ? "squidgame.game.marbles.throw.won" : "squidgame.game.marbles.throw.lost", rr.moved())
                    : Component.translatable("squidgame.game.marbles.throw.closest", w.displayNumber());
            Announcer.title(p, title, Component.translatable("squidgame.game.marbles.throw.score", shown(sideOf(c)), shown(sideOf(c).other())), 2, 36, 8);
            p.playNotifySound(won ? ModSounds.UI_CONFIRM : ModSounds.UI_DENY, SoundSource.MASTER, 0.9f, won ? 1.2f : 0.9f);
            if (!won && rr.moved() >= 3) {
                ctx.danger(p, 0.3f, 30, 2, 0xFFC01010);
            }
        });
    }

    private void clearFloor() {
        for (MarbleProjectile m : onFloor) {
            m.discard();
        }
        onFloor.clear();
    }

    private void afterRound(long now) {
        clearFloor();
        if (duel.over()) {
            finish(duel.outcome(), now);
            return;
        }
        duel.advance(timeCalled);
        if (duel.over()) {
            finish(duel.outcome(), now);
            return;
        }
        announceSuddenDeath();
        startTurn(now);
    }

    private void announceSuddenDeath() {
        if (duel.isSuddenDeath() && !suddenDeathAnnounced) {
            suddenDeathAnnounced = true;
            toPartners((c, p) -> {
                Announcer.title(p, Component.translatable("squidgame.game.marbles.sudden.title"),
                        Component.translatable("squidgame.game.marbles.sudden.subtitle"), 3, 50, 10);
                p.playNotifySound(ModSounds.DANGER_STING, SoundSource.MASTER, 1.0f, 1.0f);
            });
        }
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
        boolean fresh = state == State.INTRO || (state == State.TURN && duel.firstThrowOfRound() == null);
        if (fresh) {
            clearFloor();
            duel.settleByCount();
            if (duel.over()) {
                finish(duel.outcome(), now);
            } else {
                announceSuddenDeath();
                startTurn(now);
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
        sendThrowState(a, false);
        sendThrowState(b, false);
    }

    @Override
    void release() {
        clearFloor();
        sendThrowState(a, false);
        sendThrowState(b, false);
    }

    @Override
    void resendUi(Contestant c) {
        if (state == State.TURN && !decided() && turnSide != null && of(turnSide).number == c.number) {
            sendThrowState(c, true);
        }
    }

    @Override
    String summary() {
        MatchOutcome o = outcome();
        return String.format("match k=%d throw No.%s vs No.%s -> No.%s wins (%s, %d rounds, marbles %s)", plot.k(), a.displayNumber(),
                b.displayNumber(), winner().displayNumber(), o.reason(), duel.history().size(), duel.ledger());
    }

    // ------------------------------------------------------------------ HUD

    @Override
    void hudExtra(Contestant viewer, Side me, List<HudPayload.Widget> out) {
        if (decided()) {
            return;
        }
        Component who;
        if (state == State.INTRO) {
            who = Component.translatable("squidgame.game.marbles.hud.intro");
        } else if (turnSide == me) {
            who = Component.translatable("squidgame.game.marbles.hud.your_throw");
        } else {
            who = Component.translatable("squidgame.game.marbles.hud.their_throw", of(me.other()).displayNumber());
        }
        out.add(HudPayload.Widget.line("round", Component.translatable(duel.isSuddenDeath() ? "squidgame.game.marbles.hud.round.sudden"
                : "squidgame.game.marbles.hud.round", duel.roundNo(), duel.maxRounds(), who)));
        // no banner or bar here: the aim needs a clear view and the throw panel (client) carries the turn timer
    }

    @Override
    void lastMarbleWarning(List<HudPayload.Widget> out) {
        out.add(HudPayload.Widget.line("last", Component.translatable("squidgame.game.marbles.banner.last_marble").withStyle(ChatFormatting.RED)));
    }
}
