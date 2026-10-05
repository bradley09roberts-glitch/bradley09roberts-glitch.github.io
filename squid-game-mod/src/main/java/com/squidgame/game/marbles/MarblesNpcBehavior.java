package com.squidgame.game.marbles;

import com.squidgame.build.Region;
import com.squidgame.core.Personality;
import com.squidgame.core.marbles.OddEvenStrategy;
import com.squidgame.core.marbles.PairingPlanner;
import com.squidgame.core.marbles.Side;
import com.squidgame.core.marbles.ThrowModel;
import com.squidgame.core.marbles.ThrowStrategy;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.NpcBehavior;
import com.squidgame.tournament.Contestant;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * How an NPC plays the marbles game. It only uses what a human at the same place could know: who stands where, who is
 * still looking for a partner, both marble stacks, the revealed rounds of its match - never the opponent's current
 * fist, wager or call. Personality shows in what it does:
 * <ul>
 *   <li><b>Pairing</b>: team players start asking at once and accept almost every offer, loners wait, decline and are
 *       paired at random in the end; nobody asks someone who just refused.</li>
 *   <li><b>Odd or even</b>: how long it thinks (patient contestants deliberate for seconds), what it calls (pattern
 *       reading grows with skill), how much it bets (cautious: one or two marbles, gamblers go big when ahead), a
 *       visible fist while it holds and a nod or a shake of the head after the reveal.</li>
 *   <li><b>Target throw</b>: a short moment to size up the throw, then a visible wind-up as long as its charge; skill
 *       sets the aim and timing error, nerves (behind on marbles, courage) make it worse.</li>
 * </ul>
 */
final class MarblesNpcBehavior implements NpcBehavior {
    private final MarblesGame game;
    private final Contestant me;

    // pairing
    private long nextAskAt = -1;
    private long nextWanderAt;
    private int approach = -1;
    private long approachUntil;

    // odd or even
    private int seenRound = -1;
    private long lockAt;
    private boolean locked;

    // throw
    private long seenTurn = -1;
    private long planAt;
    private ThrowStrategy.Plan plan;
    private Vec3 origin;
    private long releaseAt;
    private boolean thrown;

    MarblesNpcBehavior(MarblesGame game, Contestant me) {
        this.game = game;
        this.me = me;
    }

    @Override
    public void start(ContestantEntity npc) {
        nextAskAt = -1;
        nextWanderAt = 0;
        approach = -1;
        seenRound = -1;
        seenTurn = -1;
    }

    @Override
    public void tick(ContestantEntity npc) {
        Match m = game.matchOf(me.number);
        if (m == null) {
            if (game.pairing() != null && game.pairingOpen()) {
                pairing(npc);
            }
            return;
        }
        if (m.decided()) {
            return;
        }
        if (m instanceof OddEvenMatch o) {
            oddEven(npc, o);
        } else if (m instanceof ThrowMatch t) {
            throwing(npc, t);
        }
    }

    @Override
    public void onWitnessElimination(ContestantEntity npc, int otherNumber, double distance) {
        // a quick flinch when somebody close drops, never while thinking about a decision
        Personality p = npc.personality();
        if (distance < 10 && p.courage() < 0.55 && npc.rng().chance(0.3) && game.matchOf(me.number) == null) {
            npc.triggerAction("shocked");
        }
    }

    // ------------------------------------------------------------------ pairing

    private void pairing(ContestantEntity npc) {
        PairingManager pm = game.pairing();
        long now = game.clock();
        Personality p = npc.personality();
        if (pm.isPaired(me.number)) {
            stayWithPartner(npc, pm);
            return;
        }
        if (pm.hasOutstanding(me.number) || pm.hasIncomingHuman(me.number)) {
            // waiting for an answer, or thinking about an offer: stand still and look at the other
            npc.stopMoving();
            int other = pm.hasOutstanding(me.number) ? pm.outstandingTarget(me.number) : -1;
            Contestant oc = other >= 0 ? game.ctx().byNumber(other) : null;
            Vec3 op = oc == null ? null : oc.position(game.ctx().level);
            if (op != null) {
                npc.faceToward(op);
            }
            return;
        }
        if (nextAskAt < 0) {
            nextAskAt = now + game.ticks(game.params().pairingSeconds() * PairingPlanner.firstProposalFraction(p, npc.rng()));
        }
        if (approach >= 0) {
            approachTarget(npc, pm, now);
            return;
        }
        if (now >= nextAskAt) {
            chooseTarget(npc, pm, now);
            return;
        }
        wander(npc, now);
    }

    private void chooseTarget(ContestantEntity npc, PairingManager pm, long now) {
        List<PairingPlanner.Candidate> candidates = pm.candidatesFor(me);
        int idx = PairingPlanner.pickTarget(candidates, npc.rng());
        if (idx < 0) {
            nextAskAt = now + game.ticks(3);
            return;
        }
        approach = candidates.get(idx).number();
        approachUntil = now + game.ticks(7);
    }

    private void approachTarget(ContestantEntity npc, PairingManager pm, long now) {
        Contestant target = game.ctx().byNumber(approach);
        Vec3 tp = target == null || !target.isAlive() || pm.isPaired(approach) ? null : target.position(game.ctx().level);
        if (tp == null || now > approachUntil) {
            approach = -1;
            nextAskAt = now + game.ticks(2.5 + npc.rng().nextDouble() * 3);
            return;
        }
        if (npc.position().distanceTo(tp) > 3.3) {
            npc.moveToward(tp, 0.85);
            return;
        }
        npc.stopMoving();
        npc.faceToward(tp);
        PairingManager.Result r = pm.propose(me, target);
        approach = -1;
        nextAskAt = now + game.ticks(r == PairingManager.Result.OK ? 7 + npc.rng().nextDouble() * 5 : 2.5);
    }

    private void wander(ContestantEntity npc, long now) {
        if (now < nextWanderAt) {
            return;
        }
        Rng r = npc.rng();
        nextWanderAt = now + game.ticks(2.5 + r.nextDouble() * 5.0);
        Region square = game.ctx().region("marbles.square");
        Vec3 p = npc.position();
        double x = p.x + r.gaussian(0, 3.0), z = p.z + r.gaussian(0, 3.0);
        if (square != null) {
            x = Mth.clamp(x, square.minX() + 1.5, square.maxX() - 0.5);
            z = Mth.clamp(z, square.minZ() + 1.5, square.maxZ() - 0.5);
        }
        if (r.chance(0.55)) {
            npc.moveToward(new Vec3(x, p.y, z), 0.45);
        } else {
            npc.stopMoving();
            npc.lookAtPos(p.add(r.gaussian(0, 6), 1.5, r.gaussian(0, 6)));
        }
    }

    private void stayWithPartner(ContestantEntity npc, PairingManager pm) {
        Contestant partner = game.ctx().byNumber(pm.partnerOf(me.number));
        Vec3 pp = partner == null ? null : partner.position(game.ctx().level);
        if (pp == null) {
            return;
        }
        if (npc.position().distanceTo(pp) > 2.6) {
            npc.moveToward(pp, 0.6);
        } else {
            npc.stopMoving();
            npc.lookAtPos(pp.add(0, 1.5, 0));
        }
    }

    // ------------------------------------------------------------------ odd or even

    private void oddEven(ContestantEntity npc, OddEvenMatch o) {
        if (!o.deciding()) {
            return;
        }
        Side my = o.sideOf(me);
        Personality p = npc.personality();
        int round = o.roundNo();
        if (seenRound != round) {
            seenRound = round;
            locked = false;
            double fraction = OddEvenStrategy.thinkFraction(p, npc.rng());
            lockAt = o.decisionStart() + (long) (fraction * o.decisionTotalTicks());
        }
        if (locked || o.hasLockedIn(my) || game.clock() < lockAt) {
            return;
        }
        var difficulty = game.difficulty();
        var history = o.publicHistory();
        if (o.holderSide() == my) {
            int count = OddEvenStrategy.hold(p, difficulty, my, o.marbles(my), history, npc.rng());
            locked = o.submitHold(me, count);
        } else {
            OddEvenStrategy.Call call = OddEvenStrategy.call(p, difficulty, my, o.maxHold(), history, npc.rng());
            int wager = OddEvenStrategy.wager(p, o.marbles(my), o.marbles(my.other()), call, o.wagerForced() ? 1 : 0, npc.rng());
            locked = o.submitGuess(me, wager, call.parity());
        }
    }

    // ------------------------------------------------------------------ target throw

    private void throwing(ContestantEntity npc, ThrowMatch t) {
        Side my = t.sideOf(me);
        if (t.turnSide() != my) {
            plan = null;
            return;
        }
        long now = game.clock();
        Personality p = npc.personality();
        if (seenTurn != t.turnStart()) {
            seenTurn = t.turnStart();
            plan = null;
            thrown = false;
            planAt = now + game.ticks((20 + (1.0 - p.reactionSpeed()) * 30 + p.patience() * 25 + npc.rng().nextInt(10)) / 20.0);
            npc.faceYaw(Plot.yawToward(npc.position(), t.bullseye()));
            npc.setActivity(Activity.NONE);
        }
        if (thrown) {
            return;
        }
        if (plan == null) {
            if (now < planAt) {
                return;
            }
            origin = t.handOrigin(me);
            Vec3 b = t.bullseye();
            plan = ThrowStrategy.plan(p, game.difficulty(), t.model(), new ThrowModel.Vec(origin.x, origin.y, origin.z),
                    new ThrowModel.Vec(b.x, b.y, b.z), t.pressure(my), npc.rng());
            releaseAt = now + plan.chargeTicks();
            npc.setActivity(Activity.MARBLE_WINDUP);
            return;
        }
        if (now >= releaseAt) {
            Vec3 aim = new Vec3(plan.aim().x(), plan.aim().y(), plan.aim().z());
            thrown = true;
            if (!t.submitThrow(me, origin, aim, plan.chargeTicks())) {
                npc.setActivity(Activity.NONE);
            }
            plan = null;
        }
    }
}
