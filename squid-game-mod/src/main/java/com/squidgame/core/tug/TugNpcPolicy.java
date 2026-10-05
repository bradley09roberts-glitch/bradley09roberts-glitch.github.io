package com.squidgame.core.tug;

import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;

/**
 * How an NPC pulls. A pure decision model: it sees only what a human standing in the same line would see (the rope's lead
 * and how fast it moves, its own stamina, how many teammates and opponents visibly strain, the shared beat, the clock)
 * plus its own personality, and answers with the same two inputs a human has: how to pull and when to heave.
 *
 * <ul>
 *   <li><b>patience</b> decides how much stamina is kept in reserve, how long a pulling stint lasts and how readily the
 *       NPC rests or braces when the rope is stable;</li>
 *   <li><b>aggression / risk tolerance</b> set the pulling effort and the urge to burst early;</li>
 *   <li><b>courage</b> decides what happens when the team is losing: brave NPCs go all out, nervous ones panic (erratic
 *       effort, sloppy timing);</li>
 *   <li><b>cooperation</b> sets how often the NPC heaves with the beat, <b>reaction speed</b> and <b>skill</b> how precisely
 *       (the timing jitter is measured in ticks around the beat).</li>
 * </ul>
 * Every number is O(1) per call; the owner calls {@link #decide} at its own cadence.
 */
public final class TugNpcPolicy {
    public enum Mode {PULL, HOLD, REST, SURGE, PANIC}

    /** A visible swing of the rope, for reactions. */
    public enum Swing {NONE, JOLT, RALLY}

    /**
     * @param lead             rope offset seen from the NPC's own side: +1 = its team has won, -1 = it is about to fall
     * @param teamStrained     fraction of the own team that visibly strains (public: the animations show it)
     * @param opponentStrained the same for the other team
     * @param timeLeft         fraction of the heat time that is left (the clock is public)
     */
    public record View(long now, double lead, double stamina, boolean exhausted, double teamStrained,
                       double opponentStrained, double timeLeft, BeatClock beat, boolean suddenDeath) {
    }

    /**
     * @param effort    how hard to pull (0 = not at all)
     * @param brace     lean back (anchor, cheap)
     * @param pressTick if &gt;= 0: press heave now, registered at this tick (the planned moment, which may be a tick or two ago
     *                  when the owner's AI step is throttled)
     */
    public record Action(double effort, boolean brace, long pressTick) {
    }

    /** Chance per beat that an NPC wants to heave: base + cooperation * factor (before the mode and stamina adjust it). */
    private static final double ATTEMPT_BASE = 0.12;
    private static final double ATTEMPT_COOP = 0.5;
    /** Timing jitter (ticks, standard deviation) of the least and the most precise NPC. */
    private static final double SIGMA_WORST = 4.0;
    private static final double SIGMA_BEST = 1.3;

    private final Personality p;
    private final Rng rng;
    private final double skill;
    private final double window;
    private final double ambition;
    private final double reserve;
    private final double resumeLevel;
    private final int pullStint;
    private final int holdStint;
    private final boolean brave;
    private final double sigmaBase;
    private final double bias;
    private final double reactionBase;

    private Mode mode = Mode.PULL;
    private long modeSince;
    private Mode pending;
    private long pendingAt;
    private long nextThink;
    private int panicPeriod = 10;
    private long panicFlip;
    private boolean panicHigh = true;
    private double effort;

    private long lastNow = -1;
    private double lastLead;
    private double trend;
    private final long[] sampleTick = new long[8];
    private final double[] sampleLead = new double[8];
    private int samples;
    private long lastSwing = -1000;
    private Swing swing = Swing.NONE;
    private long plannedBeat = Long.MIN_VALUE;
    private long plannedAt = -1;

    /**
     * @param skillBonus added to the NPC's skill (the difficulty's bonus for the NPC team opposing a human)
     * @param window     half width of the heave window in ticks (the NPC aims for the beat itself, not for the window)
     */
    public TugNpcPolicy(Personality p, double skillBonus, Rng rng, double window) {
        this.p = p;
        this.rng = rng;
        this.window = window;
        this.skill = Rng.clamp(p.skill() + skillBonus, 0.05, 1.0);
        this.ambition = Rng.clamp(0.50 + 0.30 * p.aggression() + 0.15 * (1 - p.patience()) + 0.10 * p.riskTolerance(), 0.45, 1.0);
        this.reserve = 0.14 + 0.36 * p.patience() + 0.10 * (1 - p.aggression());
        this.resumeLevel = Math.min(0.92, reserve + 0.28 + 0.15 * p.patience());
        this.pullStint = (int) ((5 + 9 * p.patience()) * 20);
        this.holdStint = (int) ((2 + 3 * p.patience()) * 20);
        this.brave = p.courage() > 0.42 || p.riskTolerance() > 0.7;
        this.sigmaBase = Rng.lerp(SIGMA_WORST, SIGMA_BEST, 0.55 * p.reactionSpeed() + 0.45 * skill);
        this.bias = 0.8 * (0.5 - p.courage()) - 0.6 * (p.aggression() - 0.5);
        this.reactionBase = Rng.lerp(13.0, 3.0, p.reactionSpeed()) * (1.0 - skillBonus);
    }

    public Mode mode() {
        return mode;
    }

    public double effort() {
        return effort;
    }

    /** The visible swing since the last call (cleared by reading it). */
    public Swing takeSwing() {
        Swing s = swing;
        swing = Swing.NONE;
        return s;
    }

    public Action decide(View v) {
        track(v);
        if (v.now >= nextThink) {
            think(v);
        }
        if (pending != null && v.now >= pendingAt) {
            setMode(pending, v.now);
            pending = null;
        }
        if (v.exhausted && (mode == Mode.PULL || mode == Mode.SURGE || mode == Mode.PANIC)) {
            // the body gives out: no reaction time
            setMode(v.lead > 0.1 || v.teamStrained > 0.6 ? Mode.REST : Mode.HOLD, v.now);
            pending = null;
        }
        boolean brace = false;
        switch (mode) {
            case PULL -> effort = Math.min(1.0, ambition * (v.lead > 0.45 ? 0.8 : (v.lead < -0.15 ? 1.12 : 1.0)));
            case SURGE -> effort = 1.0;
            case PANIC -> {
                if (v.now >= panicFlip) {
                    panicHigh = !panicHigh;
                    panicFlip = v.now + panicPeriod + rng.nextInt(7);
                }
                effort = panicHigh ? 1.0 : 0.25;
            }
            case HOLD -> {
                effort = 0.0;
                brace = true;
            }
            default -> effort = 0.0;
        }
        return new Action(effort, brace, heave(v));
    }

    // ------------------------------------------------------------------ perception

    private void track(View v) {
        if (lastNow >= 0 && v.now > lastNow) {
            double dt = v.now - lastNow;
            double rate = (v.lead - lastLead) / dt * 20.0;
            trend += (rate - trend) * Math.min(1.0, 0.08 * dt);
        }
        lastNow = v.now;
        lastLead = v.lead;
        if (samples == 0 || v.now - sampleTick[(samples - 1) & 7] >= 8) {
            sampleTick[samples & 7] = v.now;
            sampleLead[samples & 7] = v.lead;
            samples++;
        }
        // visible swings: the rope moved a lot within the last two seconds
        if (v.now - lastSwing > 80 && samples >= 3) {
            double old = Double.NaN;
            for (int i = Math.max(0, samples - 8); i < samples; i++) {
                if (v.now - sampleTick[i & 7] <= 50) {
                    old = sampleLead[i & 7];
                    break;
                }
            }
            if (!Double.isNaN(old)) {
                double d = v.lead - old;
                if (d < -0.18) {
                    swing = Swing.JOLT;
                    lastSwing = v.now;
                } else if (d > 0.20 && v.lead < 0.5) {
                    swing = Swing.RALLY;
                    lastSwing = v.now;
                }
            }
        }
    }

    // ------------------------------------------------------------------ decisions

    private void think(View v) {
        nextThink = v.now + 8 + rng.nextInt(7);
        double lead = v.lead + rng.gaussian(0, 0.04 * (1.25 - skill));
        Mode want = want(v, lead);
        if (want == mode) {
            pending = null;
            return;
        }
        if (want == pending) {
            return;
        }
        boolean urgent = want == Mode.SURGE || want == Mode.PANIC;
        // do not thrash between modes: a mode is kept for a moment unless the situation is dire
        if (!urgent && v.now - modeSince < (mode == Mode.HOLD || mode == Mode.REST ? 30 : 24)) {
            return;
        }
        int delay = (int) Math.max(1, Math.round(reactionBase * (urgent ? 0.5 : 1.0) + rng.gaussian(0, 1.2)));
        pending = want;
        pendingAt = v.now + delay;
    }

    private Mode want(View v, double lead) {
        boolean desperate = lead < -0.55 || (lead < -0.30 && trend < -0.06);
        boolean comfy = lead > 0.45 && trend > -0.03;
        boolean patient = p.patience() > 0.45 && !v.suddenDeath;
        long dwell = v.now - modeSince;
        if (v.exhausted) {
            return comfy || v.teamStrained > 0.6 ? Mode.REST : Mode.HOLD;
        }
        if (desperate) {
            return brave || v.timeLeft < 0.15 || v.suddenDeath ? Mode.SURGE : Mode.PANIC;
        }
        if (mode == Mode.REST) {
            // a team that is behind cannot wait for a full recovery
            double need = lead < -0.25 ? Math.min(resumeLevel, reserve + 0.12) : resumeLevel;
            return v.stamina >= need ? Mode.PULL : Mode.REST;
        }
        if (v.stamina < reserve && !v.suddenDeath) {
            // running low: when behind, the last of the strength anchors the line; otherwise recover
            return lead < -0.25 ? Mode.HOLD : Mode.REST;
        }
        if (mode == Mode.HOLD && v.stamina < resumeLevel * 0.85 && lead > -0.2 && dwell < holdStint) {
            return Mode.HOLD;
        }
        if (comfy) {
            // well ahead: the patient save their strength (and brace once they are fresh), the others keep pulling
            return patient ? (v.stamina < 0.8 ? Mode.REST : Mode.HOLD) : Mode.PULL;
        }
        if (patient && (mode == Mode.PULL || mode == Mode.SURGE || mode == Mode.PANIC) && dwell > pullStint && lead > -0.2
                && Math.abs(trend) < 0.05 && v.timeLeft > 0.25) {
            // calm and contested: a patient contestant eases off for a moment to keep the reserve up
            return Mode.HOLD;
        }
        return Mode.PULL;
    }

    private void setMode(Mode m, long now) {
        mode = m;
        modeSince = now;
        if (m == Mode.PANIC) {
            panicPeriod = 8 + rng.nextInt(9);
            panicFlip = now + panicPeriod;
            panicHigh = true;
        }
    }

    // ------------------------------------------------------------------ heaves

    /** Plans at most one heave per beat; returns the planned press tick once it is due, otherwise -1. */
    private long heave(View v) {
        if (plannedAt < 0) {
            long next = v.beat.nextBeatAtOrAfter(v.now);
            if (next > plannedBeat) {
                plannedBeat = next;
                if (wantsHeave(v)) {
                    plannedAt = next + jitter(v);
                }
            }
        }
        if (plannedAt >= 0 && v.now >= plannedAt) {
            long at = plannedAt;
            plannedAt = -1;
            // conditions may have changed since the plan was made; a press that is long overdue is dropped
            return v.exhausted || mode == Mode.REST || v.now - at > 6 ? -1 : at;
        }
        return -1;
    }

    private boolean wantsHeave(View v) {
        if (v.exhausted || mode == Mode.REST) {
            return false;
        }
        double base = ATTEMPT_BASE + ATTEMPT_COOP * p.cooperation();
        double byMode = switch (mode) {
            case PULL -> 1.0;
            case SURGE -> 1.15;
            case HOLD -> 0.45;
            case PANIC -> 0.6;
            default -> 0.0;
        };
        double byStamina = v.stamina > 0.35 ? 1.0 : (v.stamina > 0.15 ? 0.45 : 0.0);
        double byCalm = v.lead > 0.5 ? 0.6 : 1.0;
        return rng.chance(Rng.clamp(base * byMode * byStamina * byCalm, 0.0, 0.95));
    }

    private int jitter(View v) {
        double sigma = sigmaBase * (1.0 + 0.5 * (1.0 - v.stamina)) * (mode == Mode.PANIC ? 1.8 : 1.0);
        int j = (int) Math.round(rng.gaussian(bias, sigma));
        int cap = (int) Math.ceil(window) + 3;
        return Math.max(-cap, Math.min(cap, j));
    }
}
