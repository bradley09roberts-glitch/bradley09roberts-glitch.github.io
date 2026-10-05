package com.squidgame.game.bridge;

import com.squidgame.SquidConfig;
import com.squidgame.SquidGameMod;
import com.squidgame.core.Personality;
import com.squidgame.core.bridge.BridgeKnowledge;
import com.squidgame.core.bridge.BridgeLayout;
import com.squidgame.core.bridge.BridgeNpcRules;
import com.squidgame.core.bridge.BridgeRules;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.NpcBehavior;
import net.minecraft.world.phys.Vec3;

/**
 * How an NPC plays the glass bridge. It only ever uses what a human in the hall could know:
 * <ul>
 *   <li>what has <b>publicly happened</b>: panels seen shattering or holding arrive through the public event log of
 *       {@link BridgePublicView} and are written to the NPC's own {@code NpcMemory} (with a small chance to miss one),
 *       so a row is "known" to this NPC only if it noticed it; the hidden route is never reachable from here;</li>
 *   <li>who stands where, which panels are gone, the queue and the gate;</li>
 *   <li>its own <b>personality</b>: reckless contestants hop almost at once, nervous or patient ones stare at the glass
 *       for seconds, the very frightened may freeze until the stall limit breaks the panel under them; under pressure
 *       (stall timer, fear of recent falls) even a known row can be botched.</li>
 * </ul>
 * Thinking runs through {@link #tick} (possibly throttled); the body is driven tick by tick by the {@link BridgeMotor},
 * which the game steps every server tick.
 */
final class BridgeNpcBehavior implements NpcBehavior {
    private enum Mode {
        /** Standing in the queue. */
        QUEUE,
        /** Next in line: stepping up to the gate. */
        STAGE,
        /** Called: reacting before walking to the edge. */
        REACT,
        /** Walking to the take-off spot (platform edge or edge of the current panel). */
        WALK,
        /** At the take-off spot: hesitating, waiting for the panel to be free, then leaping. */
        THINK,
        /** In the air. */
        HOP,
        /** Just landed: regaining balance. */
        SETTLE,
        /** Frozen with fear until the stall limit breaks the glass. */
        FROZEN,
        /** The glass under this NPC is cracking. */
        DOOMED,
        /** Across: celebrating or sobbing. */
        FINISH
    }

    private static final String MEM_KEY = "bridge.safe.";

    private final BridgePublicView view;
    private final int number;
    private final BridgeMotor motor = new BridgeMotor();

    private Personality p;
    private Rng rng;
    private double walkSpeed;
    private Mode mode = Mode.QUEUE;
    private int cursor;
    private double fear;
    private long reactUntil;
    private long thinkUntil;
    private long settleUntil;
    private int lookTimer;

    // the current decision
    private int targetRow;
    private int targetLane;
    private boolean decidedKnown;
    private int lastLane = -1;
    private BridgeLayout.Vec2 takeoff;
    private BridgeLayout.Vec2 landing;
    private boolean landingOnFinish;
    private boolean reserved;
    private boolean hesitated;

    // finish routine
    private Vec3 finishTarget;
    private int finishTimer;

    BridgeNpcBehavior(BridgePublicView view, int number) {
        this.view = view;
        this.number = number;
    }

    // ------------------------------------------------------------------ NpcBehavior

    @Override
    public void start(ContestantEntity npc) {
        p = npc.personality();
        rng = npc.rng();
        walkSpeed = BridgeNpcRules.walkSpeed(p);
        cursor = 0;
        syncMemory(npc);
        lookTimer = 40 + rng.nextInt(80);
        // a stand-in that takes over mid-game continues from wherever its human was
        if (view.hasFinished(number)) {
            mode = Mode.FINISH;
        } else if (view.cellOf(number) != null) {
            mode = Mode.SETTLE;
            settleUntil = view.now() + 10;
        } else if (view.isCalled(number)) {
            mode = Mode.REACT;
            reactUntil = view.now() + 20;
        } else {
            mode = Mode.QUEUE;
        }
    }

    @Override
    public void stop(ContestantEntity npc) {
        motor.stop(npc);
        view.release(number);
    }

    @Override
    public void onStuck(ContestantEntity npc) {
        // the motor never uses navigation, so nothing can get stuck in the vanilla sense
    }

    @Override
    public void onWitnessElimination(ContestantEntity npc, int otherNumber, double distance) {
        fear = Math.min(1.0, fear + 0.12 + (1.0 - p.courage()) * 0.3);
        if (distance < 12 && mode != Mode.HOP && mode != Mode.DOOMED && rng.chance(0.55 * (1.2 - p.courage()))) {
            npc.triggerAction("shocked");
        }
    }

    /** Called by the game every server tick: advances the walk / leap. */
    void motorTick(ContestantEntity npc) {
        motor.tick(npc);
    }

    /** One line of state for the debug log (why is this NPC not moving?). */
    String describe() {
        return "mode=" + mode + " motor=" + motor.mode() + " targetRow=" + targetRow + " lane=" + targetLane
                + " decidedKnown=" + decidedKnown + " thinkIn=" + (thinkUntil - view.now()) + " reactIn=" + (reactUntil - view.now())
                + " reserved=" + reserved + " onDeck=" + view.isOnDeck(number);
    }

    // ------------------------------------------------------------------ thinking

    @Override
    public void tick(ContestantEntity npc) {
        syncMemory(npc);
        fear = Math.max(0.0, fear - 0.0006);
        if (view.isOut(number)) {
            return;
        }
        if (view.hasFinished(number)) {
            tickFinish(npc);
            return;
        }
        if (view.myPanelCracked(number) && mode != Mode.DOOMED) {
            motor.stop(npc);
            view.release(number);
            mode = Mode.DOOMED;
            npc.setActivity(Activity.COWER);
            npc.triggerAction(rng.chance(0.5) ? "lose_balance" : "shocked");
            return;
        }
        switch (mode) {
            case QUEUE -> tickQueue(npc);
            case STAGE -> tickStage(npc);
            case REACT -> tickReact(npc);
            case WALK -> tickWalk(npc);
            case THINK -> tickThink(npc);
            case HOP -> tickHop(npc);
            case SETTLE -> tickSettle(npc);
            case FROZEN -> tickFrozen(npc);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ memory: what this NPC has noticed

    /** Copies new public events into this NPC's own memory; a rare lapse of attention means a row stays unknown to it. */
    private void syncMemory(ContestantEntity npc) {
        BridgeKnowledge k = view.knowledge();
        double attention = BridgeNpcRules.attention(p, view.difficulty());
        while (cursor < k.eventCount()) {
            BridgeKnowledge.Event e = k.event(cursor++);
            if (rng.chance(attention)) {
                npc.memory().remember(MEM_KEY + e.row(), k.safeLane(e.row()));
            }
        }
    }

    /** The safe lane of {@code row} as this NPC remembers it, or {@link BridgeKnowledge#UNKNOWN}. */
    private int rememberedLane(ContestantEntity npc, int row) {
        return (int) npc.memory().number(MEM_KEY + row, BridgeKnowledge.UNKNOWN);
    }

    // ------------------------------------------------------------------ queue, gate

    private void tickQueue(ContestantEntity npc) {
        if (view.isCalled(number)) {
            beginReact(npc);
            return;
        }
        if (view.isOnDeck(number)) {
            mode = Mode.STAGE;
            Vec3 s = view.staging();
            motor.walkTo(s, walkSpeed * 1.2, view.layout().start().inset(0.6));
            npc.setActivity(Activity.NONE);
            return;
        }
        // idle in line: glance at the bridge now and then, nervous ones fidget
        if (--lookTimer <= 0) {
            lookTimer = 50 + rng.nextInt(120);
            BridgeLayout l = view.layout();
            int row = rng.nextInt(view.rows());
            npc.lookAtPos(new Vec3(l.laneCenterX(rng.nextInt(2)), view.topY() + 1.0, l.panel(row, 0).rect().centerZ()));
            if (p.caution() > 0.6 && rng.chance(0.25)) {
                npc.triggerAction("shake_head");
            }
        }
    }

    private void tickStage(ContestantEntity npc) {
        if (view.isCalled(number)) {
            beginReact(npc);
            return;
        }
        if (!motor.busy() && motor.takeResult() == BridgeMotor.Result.ARRIVED) {
            npc.setActivity(Activity.ATTENTION);
            Vec3 s = view.staging();
            BridgeMotor.faceTowards(npc, s.x, s.z + 5, 90f);
        }
    }

    private void beginReact(ContestantEntity npc) {
        mode = Mode.REACT;
        motor.stop(npc);
        npc.setActivity(Activity.NONE);
        long ready = view.calledAt(number) + 16; // let the doors slide away first
        reactUntil = Math.max(ready, view.now() + BridgeNpcRules.callReactionTicks(p, view.difficulty(), rng));
    }

    private void tickReact(ContestantEntity npc) {
        if (view.now() < reactUntil) {
            return;
        }
        // first row: choose a lane and walk to the front edge of the platform in line with it
        beginDecision(npc, -1);
    }

    // ------------------------------------------------------------------ deciding and walking

    /** Starts the decision for the row after {@code fromRow} (-1 = from the platform). */
    private void beginDecision(ContestantEntity npc, int fromRow) {
        BridgeLayout layout = view.layout();
        targetRow = fromRow + 1;
        reserved = false;
        hesitated = false;
        landingOnFinish = targetRow >= view.rows();
        if (landingOnFinish) {
            targetLane = view.cellOf(number) != null ? view.cellOf(number).lane : 0;
            BridgeLayout.Panel from = layout.panel(fromRow, targetLane);
            takeoff = new BridgeLayout.Vec2(Math.max(from.rect().minX() + BridgeLayout.TAKEOFF_MARGIN,
                    Math.min(from.rect().maxX() - BridgeLayout.TAKEOFF_MARGIN, npc.getX())),
                    from.rect().maxZ() - BridgeLayout.TAKEOFF_MARGIN);
            landing = layout.finishLanding(takeoff);
            thinkUntil = view.now();
            startWalk(npc, takeoff, boundsOf(from));
            return;
        }
        chooseLane(npc);
        if (mode == Mode.FROZEN) {
            return;
        }
        BridgeLayout.Panel to = layout.panel(targetRow, targetLane);
        if (fromRow < 0) {
            takeoff = layout.gateTakeoff(to);
            startWalk(npc, takeoff, layout.start().inset(BridgeLayout.TAKEOFF_MARGIN));
        } else {
            BridgeLayout.Panel from = layout.panel(fromRow, view.cellOf(number).lane);
            takeoff = layout.takeoff(from, to);
            startWalk(npc, takeoff, boundsOf(from));
        }
        landing = layout.landing(takeoff, to);
    }

    private BridgeLayout.Rect boundsOf(BridgeLayout.Panel from) {
        return from.rect().inset(BridgeLayout.TAKEOFF_MARGIN - 0.15);
    }

    private void startWalk(ContestantEntity npc, BridgeLayout.Vec2 to, BridgeLayout.Rect bounds) {
        mode = Mode.WALK;
        motor.walkTo(new Vec3(to.x(), view.topY(), to.z()), walkSpeed, bounds);
        npc.setActivity(Activity.BRIDGE_BALANCE);
    }

    /** Picks the lane for {@link #targetRow}: the remembered safe lane (a slip is possible) or a guess after hesitating. */
    private void chooseLane(ContestantEntity npc) {
        BridgeRules.Params params = view.params();
        int known = rememberedLane(npc, targetRow);
        double pressure = BridgeRules.stallPressure(params, view.stallRemaining(number));
        long now = view.now();
        if (known != BridgeKnowledge.UNKNOWN) {
            decidedKnown = true;
            targetLane = known;
            int other = 1 - known;
            boolean slip = view.panelIntact(targetRow, other)
                    && rng.chance(BridgeNpcRules.slipChance(p, view.difficulty(), pressure, fear));
            if (slip) {
                targetLane = other; // a slip of nerve or memory
            }
            thinkUntil = now;
            traceDecision(slip ? "SLIP" : "known");
            return;
        }
        decidedKnown = false;
        if (view.cellOf(number) != null && BridgeNpcRules.freezes(p, view.difficulty(), true, rng)) {
            mode = Mode.FROZEN;
            npc.setActivity(Activity.BRIDGE_HESITATE);
            return;
        }
        targetLane = BridgeNpcRules.guessLane(p, lastLane, rng);
        thinkUntil = now + BridgeNpcRules.hesitationTicks(p, params, fear, rng);
        traceDecision("GUESS");
    }

    /** Debug log of one lane decision (what this NPC believed at that moment), for auditing that nothing but public events decides. */
    private void traceDecision(String kind) {
        if (SquidConfig.get().debug) {
            SquidGameMod.LOGGER.info("[bridge] No. {} decides row {} lane {} ({})", String.format("%03d", number), targetRow + 1, targetLane, kind);
        }
    }

    private void tickWalk(ContestantEntity npc) {
        BridgeMotor.Result r = motor.takeResult();
        if (r == BridgeMotor.Result.ARRIVED || (!motor.busy() && r == BridgeMotor.Result.NONE)) {
            mode = Mode.THINK;
            npc.setActivity(Activity.BRIDGE_BALANCE);
        }
        // pressure: the stall timer is running low, stop dithering
        if (view.stallRemaining(number) < view.params().stallLimitTicks() * 0.2) {
            thinkUntil = Math.min(thinkUntil, view.now());
        }
    }

    private void tickThink(ContestantEntity npc) {
        BridgeLayout layout = view.layout();
        long now = view.now();
        Vec3 aim = landing == null ? null : new Vec3(landing.x(), view.topY(), landing.z());
        if (aim != null) {
            BridgeMotor.faceTowards(npc, aim.x, aim.z, 25f);
        }
        if (!landingOnFinish && view.panelBroken(targetRow, targetLane)) {
            // a hole in the glass is plain to see (even for somebody who missed the crash): that was the weak panel,
            // so the other one holds; look again and choose
            npc.memory().remember(MEM_KEY + targetRow, 1 - targetLane);
            beginDecision(npc, targetRow - 1);
            return;
        }
        if (!landingOnFinish && !decidedKnown) {
            // the row may have been revealed while we were staring at it
            if (rememberedLane(npc, targetRow) != BridgeKnowledge.UNKNOWN) {
                beginDecision(npc, targetRow - 1);
                return;
            }
            if (view.stallRemaining(number) < view.params().stallLimitTicks() * 0.2) {
                thinkUntil = Math.min(thinkUntil, now);
            }
        }
        if (now < thinkUntil) {
            if (!hesitated && thinkUntil - now > 30) {
                hesitated = true;
                npc.setActivity(Activity.BRIDGE_HESITATE);
                if (p.caution() > 0.5) {
                    npc.triggerAction("bridge_step"); // a tentative foot out over the glass
                }
            }
            return;
        }
        npc.setActivity(Activity.BRIDGE_BALANCE);
        if (!landingOnFinish) {
            // never gamble on the other lane while somebody just stepped onto an unrevealed row: wait for the verdict
            if (!decidedKnown && view.rowOccupied(targetRow, number)) {
                return;
            }
            if (!view.panelHasGlass(targetRow, targetLane) || !view.panelFree(targetRow, targetLane, number)) {
                return;
            }
            view.reserve(number, targetRow, targetLane);
            reserved = true;
            lastLane = targetLane;
        }
        mode = Mode.HOP;
        motor.leapTo(npc, landing, view.topY());
    }

    private void tickHop(ContestantEntity npc) {
        BridgeMotor.Result r = motor.takeResult();
        if (r == BridgeMotor.Result.NONE) {
            return;
        }
        if (reserved) {
            view.release(number);
            reserved = false;
        }
        npc.triggerAction("land");
        mode = Mode.SETTLE;
        settleUntil = view.now() + BridgeNpcRules.settleTicks(p, rng);
        npc.setActivity(Activity.BRIDGE_BALANCE);
    }

    private void tickSettle(ContestantEntity npc) {
        PanelField.Cell cell = view.cellOf(number);
        if (cell == null || view.now() < settleUntil) {
            return;
        }
        beginDecision(npc, cell.row);
    }

    private void tickFrozen(ContestantEntity npc) {
        // stays put; the stall rule breaks the panel in the end. Occasionally trembles.
        if (--lookTimer <= 0) {
            lookTimer = 60 + rng.nextInt(80);
            npc.triggerAction(rng.chance(0.5) ? "shake_head" : "shocked");
        }
    }

    // ------------------------------------------------------------------ across

    private void tickFinish(ContestantEntity npc) {
        if (mode != Mode.FINISH) {
            mode = Mode.FINISH;
            motor.stop(npc);
            view.release(number);
            npc.triggerAction("relieved");
            finishTimer = 30 + rng.nextInt(40);
            finishTarget = null;
            return;
        }
        if (--finishTimer > 0) {
            return;
        }
        if (finishTarget == null) {
            finishTarget = view.finishSpot(Math.max(1, view.finishRank(number)));
            motor.walkTo(finishTarget, walkSpeed * 1.4, view.finishBounds());
            npc.setActivity(Activity.NONE);
            return;
        }
        if (!motor.busy() && motor.takeResult() == BridgeMotor.Result.ARRIVED) {
            BridgeMotor.faceTowards(npc, finishTarget.x, finishTarget.z - 8, 120f);
            npc.setActivity(p.courage() > 0.6 ? Activity.CELEBRATE_FIST : (p.caution() > 0.6 ? Activity.SOB : Activity.CELEBRATE));
            finishTimer = Integer.MAX_VALUE;
        }
    }

}
