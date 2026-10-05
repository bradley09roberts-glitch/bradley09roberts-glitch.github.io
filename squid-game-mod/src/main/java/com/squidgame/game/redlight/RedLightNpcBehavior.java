package com.squidgame.game.redlight;

import com.squidgame.build.Region;
import com.squidgame.core.Personality;
import com.squidgame.core.redlight.DollCycle;
import com.squidgame.core.util.Rng;
import com.squidgame.entity.Activity;
import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.NpcBehavior;
import com.squidgame.tournament.Contestant;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How an NPC plays Red Light, Green Light. It only uses what a human could perceive ({@link RedLightGreenLightGame.PublicView}:
 * the chant it hears, the doll visibly turning, the eyes lighting up, shots it witnesses) and its own personality:
 * <ul>
 *   <li><b>reaction speed</b> sets the delay between perceiving and acting;</li>
 *   <li><b>caution / patience / risk</b> set the safety margin: cautious contestants brake well before the chant ends,
 *       reckless ones run until the last syllable and gamble on stopping in time;</li>
 *   <li><b>skill</b> sharpens the estimate of when the chant ends (it varies per cycle) and lowers the chance of a
 *       mistake (a wobble or a stumble) while frozen;</li>
 *   <li><b>courage</b> decides how much a nearby elimination shakes them (hesitation, cowering, extra margin).</li>
 * </ul>
 * Whether they survive follows from the same {@code RedLightRules} the players are judged by.
 */
final class RedLightNpcBehavior implements NpcBehavior {
    private enum Mode {WAIT, RUN, BRAKING, FROZEN, SAFE}

    private final RedLightGreenLightGame game;
    private final Contestant contestant;
    private Mode mode = Mode.WAIT;
    private double laneX;
    private double speedMul;
    private int reactionTicks;
    private int safetyTicks;
    private double estimateSigma;
    private int resumeDelay;
    private int fearTicks;
    private long brakeAtTick = -1;
    private int wobbleTicks;
    private Vec3 wobbleDir = Vec3.ZERO;
    private Vec3 safeTarget;
    private int safeTimer;
    private boolean decidedThisGreen;
    /** This chant's misjudgement of the end, in ticks (drawn once per chant: a persistent bias, not per-tick noise). */
    private double estimateBias = Double.NaN;
    private int stumbleCooldown;
    private DollCycle.Light lastLight = DollCycle.Light.GREEN;
    private long greenSince;

    RedLightNpcBehavior(RedLightGreenLightGame game, Contestant contestant) {
        this.game = game;
        this.contestant = contestant;
    }

    @Override
    public void start(ContestantEntity npc) {
        Personality p = npc.personality();
        Rng r = npc.rng();
        var diff = game.difficultyForNpc();
        double skill = p.effectiveSkill(diff);
        reactionTicks = p.reactionDelayTicks(r, diff);
        // safety margin the NPC wants between its own stop and the end of the allowance
        double caution = p.caution();
        safetyTicks = (int) Math.round(Mth.lerp(caution, 0.0, 9.0) + r.gaussian(0, 1.5));
        if (p.riskTolerance() > 0.75) {
            safetyTicks = Math.min(safetyTicks, 2);
        }
        estimateSigma = Mth.lerp(skill, 9.0, 1.5);
        speedMul = 1.05 + 0.40 * (0.45 * skill + 0.30 * p.riskTolerance() + 0.25 * p.courage());
        Region field = game.view().field();
        double minX = field == null ? -50 : field.minX() + 3;
        double maxX = field == null ? 50 : field.maxX() - 3;
        laneX = Mth.clamp(npc.getX() + r.gaussian(0, 2.0), minX, maxX);
        resumeDelay = reactionTicks + (int) (p.patience() * 6);
        mode = Mode.WAIT;
        decidedThisGreen = false;
        greenSince = game.view().now();
    }

    @Override
    public void onWitnessElimination(ContestantEntity npc, int otherNumber, double distance) {
        Personality p = npc.personality();
        int fear = (int) ((1.0 - p.courage()) * 110 + 25 * (1.0 - distance / 18.0));
        fearTicks = Math.max(fearTicks, fear);
        if (distance < 9 && npc.rng().chance(0.7)) {
            npc.triggerAction("shocked");
        }
        if (distance < 6 && p.courage() < 0.35 && mode == Mode.RUN) {
            // panic: freeze on the spot for a moment
            brake(npc);
        }
    }

    @Override
    public void onStuck(ContestantEntity npc) {
        // step sideways and carry on
        laneX = Mth.clamp(laneX + (npc.rng().nextBoolean() ? 3 : -3), -50, 50);
    }

    @Override
    public void tick(ContestantEntity npc) {
        var view = game.view();
        if (game.hasFinished(contestant.number)) {
            safeRoutine(npc, view);
            return;
        }
        if (game.isCondemned(contestant.number)) {
            npc.stopMoving();
            return;
        }
        if (fearTicks > 0) {
            fearTicks--;
        }
        if (stumbleCooldown > 0) {
            stumbleCooldown--;
        }
        DollCycle.Light light = view.light();
        long now = view.now();
        if (light != lastLight) {
            if (light == DollCycle.Light.GREEN) {
                greenSince = now;
                decidedThisGreen = false;
                brakeAtTick = -1;
                estimateBias = Double.NaN;
            }
            lastLight = light;
        }
        switch (light) {
            case GREEN -> tickGreen(npc, view, now);
            case TURNING -> tickTurning(npc, view, now);
            case RED -> tickRed(npc, view);
        }
    }

    // ------------------------------------------------------------------ green

    private void tickGreen(ContestantEntity npc, RedLightGreenLightGame.PublicView view, long now) {
        if (mode == Mode.FROZEN || mode == Mode.BRAKING || mode == Mode.WAIT) {
            if (decidedThisGreen) {
                // braked on purpose because the chant is about to end: stay put until the next chant begins
                if (mode == Mode.BRAKING && npc.horizontalSpeed() < 0.02) {
                    mode = Mode.FROZEN;
                }
                return;
            }
            // wait for the chant to be clearly underway before moving again (patient / frightened contestants wait longer)
            int need = resumeDelay / 2 + (fearTicks > 0 ? fearTicks / 4 : 0);
            if (view.syllablesHeard() >= 1 && now - greenSince >= need) {
                mode = Mode.RUN;
                npc.setActivity(Activity.NONE);
            } else if (mode == Mode.BRAKING && npc.horizontalSpeed() < 0.02) {
                mode = Mode.FROZEN;
            }
            if (mode != Mode.RUN) {
                return;
            }
        }
        if (mode != Mode.RUN) {
            return;
        }
        // keep running along the lane towards the finish line
        Vec3 target = new Vec3(laneX, npc.getY(), view.finishZ() + 6.0);
        npc.moveDirect(target, speedMul);
        // occasional stumble for clumsy runners (a believable mistake that costs a moment)
        if (stumbleCooldown == 0 && npc.horizontalSpeed() > 0.2
                && npc.rng().chance(0.0006 * (1.3 - npc.personality().skill()))) {
            npc.triggerAction("stumble");
            npc.setDeltaMovement(npc.getDeltaMovement().scale(0.4));
            stumbleCooldown = 200;
        }
        anticipateEnd(npc, view, now);
    }

    /** Estimates when the chant ends from the tempo heard so far, and brakes early enough for the personality. */
    private void anticipateEnd(ContestantEntity npc, RedLightGreenLightGame.PublicView view, long now) {
        int heard = view.syllablesHeard();
        if (heard < 3 || decidedThisGreen) {
            return;
        }
        long first = view.syllableStartTick(0);
        long last = view.syllableStartTick(heard - 1);
        double interval = (last - first) / (double) (heard - 1);
        // syllable weights: nine regular ones, a long final "da" (1.7x)
        double remainingSyllables = (DollCycle.SYLLABLES - heard) + 1.7 - Math.min(1.0, (now - last) / Math.max(1.0, interval));
        // humans cannot time a chant to the tick: the estimate is off by a few ticks (sharper with skill)
        if (Double.isNaN(estimateBias)) {
            estimateBias = npc.rng().gaussian(0, estimateSigma);
        }
        double estRemaining = remainingSyllables * interval + estimateBias;
        int allowance = view.allowanceTicks();
        double lead = reactionTicks + safetyTicks + (fearTicks > 0 ? 6 : 0) - allowance;
        if (estRemaining <= lead) {
            decidedThisGreen = true;
            brake(npc);
        }
    }

    // ------------------------------------------------------------------ turning / red

    private void tickTurning(ContestantEntity npc, RedLightGreenLightGame.PublicView view, long now) {
        if (mode == Mode.RUN) {
            // the doll is visibly turning: react after the personal delay
            if (brakeAtTick < 0) {
                brakeAtTick = view.lightChangedTick() + reactionTicks + rollLapse(npc);
            }
            if (now >= brakeAtTick) {
                brake(npc);
            } else {
                Vec3 target = new Vec3(laneX, npc.getY(), view.finishZ() + 6.0);
                npc.moveDirect(target, speedMul);
            }
        } else if (mode == Mode.BRAKING && npc.horizontalSpeed() < 0.02) {
            mode = Mode.FROZEN;
        }
    }

    private void tickRed(ContestantEntity npc, RedLightGreenLightGame.PublicView view) {
        if (mode == Mode.RUN) {
            // the eyes are lit: a runner who has not reacted yet (slow reflexes, an attention lapse) brakes when their own
            // reaction time is over - possibly too late for the rules, which judge them exactly like a human
            if (brakeAtTick < 0) {
                brakeAtTick = view.lightChangedTick() + reactionTicks + rollLapse(npc);
            }
            if (view.now() >= brakeAtTick) {
                brake(npc);
            } else {
                npc.moveDirect(new Vec3(laneX, npc.getY(), view.finishZ() + 6.0), speedMul);
            }
        }
        if (mode == Mode.WAIT) {
            mode = Mode.FROZEN;
        }
        if (npc.horizontalSpeed() < 0.02 && mode == Mode.BRAKING) {
            mode = Mode.FROZEN;
        }
        if (mode == Mode.FROZEN) {
            if (npc.getActivity() == Activity.NONE) {
                Personality p = npc.personality();
                npc.setActivity(p.skill() > 0.6 ? Activity.FREEZE_BALANCE : Activity.FREEZE_STIFF);
            }
            // nervous, clumsy contestants sometimes twitch: an involuntary step that the rules will punish
            if (wobbleTicks > 0) {
                wobbleTicks--;
                npc.moveDirect(npc.position().add(wobbleDir), 0.6);
            } else {
                Personality p = npc.personality();
                double chance = 0.00008 * (1.5 - p.skill()) * (1.0 + (1.0 - p.courage())) * (fearTicks > 0 ? 1.8 : 1.0);
                if (npc.rng().chance(chance)) {
                    wobbleTicks = 3;
                    wobbleDir = new Vec3(npc.rng().gaussian(0, 0.7), 0, npc.rng().gaussian(0, 0.7));
                    npc.setActivity(Activity.NONE);
                    npc.triggerAction("lose_balance");
                }
            }
        }
    }

    /**
     * Attention lapses: now and then a contestant notices the turn late (looking at their feet, jostled by a neighbour,
     * rattled by the last shot). Rare for skilled and brave runners, more common when frightened. A lapse longer than the
     * rest of the stopping allowance is what gets a runner shot on Normal; on harder presets the shorter allowance does the rest.
     */
    private int rollLapse(ContestantEntity npc) {
        Personality p = npc.personality();
        double skill = p.effectiveSkill(game.difficultyForNpc());
        double chance = 0.012 + 0.04 * (1.0 - skill) + 0.02 * (1.0 - p.courage());
        // the stopping allowance already decides most deaths on Extreme (6 ticks); on Hard (13) a lapse is what kills
        chance *= switch (game.difficultyForNpc()) {
            case NORMAL -> 1.3;
            case HARD -> 2.4;
            case EXTREME -> 1.5;
        };
        if (fearTicks > 0) {
            chance *= 2.0;
        }
        int lapse = npc.rng().chance(chance) ? npc.rng().rangeInt(8, 26) : 0;
        if (lapse > 0) {
            com.squidgame.SquidGameMod.debug("RLGL: {} lapses for {} ticks (reaction {})", contestant.label(), lapse, reactionTicks);
        }
        return lapse;
    }

    private void brake(ContestantEntity npc) {
        npc.stopMoving();
        mode = Mode.BRAKING;
        brakeAtTick = -1;
    }

    // ------------------------------------------------------------------ safe zone

    private void safeRoutine(ContestantEntity npc, RedLightGreenLightGame.PublicView view) {
        if (mode != Mode.SAFE) {
            mode = Mode.SAFE;
            npc.stopMoving();
            npc.setActivity(Activity.NONE);
            safeTimer = 20 + npc.rng().nextInt(40);
            safeTarget = null;
        }
        if (--safeTimer > 0) {
            return;
        }
        Region z = view.safeZone();
        if (safeTarget == null && z != null) {
            safeTarget = new Vec3(Mth.lerp(npc.rng().nextDouble(), z.minX() + 3, z.maxX() - 3), npc.getY(),
                    Mth.lerp(npc.rng().nextDouble(), z.minZ() + 2, z.maxZ() - 4));
            npc.moveToward(safeTarget, 0.6);
            return;
        }
        if (safeTarget != null && npc.position().distanceToSqr(safeTarget) < 2.5) {
            npc.stopMoving();
            Personality p = npc.personality();
            npc.setActivity(p.courage() > 0.6 ? Activity.CELEBRATE_FIST : (p.caution() > 0.6 ? Activity.SOB : Activity.CELEBRATE));
            safeTimer = 120 + npc.rng().nextInt(200);
            safeTarget = null;
        } else if (safeTarget != null && !npc.isTryingToMove()) {
            npc.moveToward(safeTarget, 0.6);
        }
    }
}
