package com.squidgame.core.tug;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plays a whole heat without Minecraft: every member is steered by an NPC policy (or a scripted controller in tests)
 * and the {@link TugSim} runs to a result. Used to auto-resolve heats that cannot be played out (the game's clock ran
 * out) and by the unit tests to check the balance of the rules.
 */
public final class TugMatch {
    private TugMatch() {
    }

    /** A member's brain; the NPC policy is one implementation, tests plug in scripted humans. */
    public interface Controller {
        TugNpcPolicy.Action decide(TugNpcPolicy.View view);
    }

    public record Entrant(int id, int team, Personality personality, boolean human) {
    }

    /**
     * @param winner   {@link TugRules#TEAM_A} or {@link TugRules#TEAM_B}
     * @param ticks    length of the heat in ticks (including a sudden death)
     * @param timedOut true if nobody pulled the rope over the edge: the rope position (or a coin flip) decided
     * @param coinFlip true if even the sudden death ended in an exact tie
     */
    public record Result(int winner, double offset, int ticks, boolean timedOut, boolean suddenDeath, boolean coinFlip,
                         double staminaA, double staminaB, int heavesHit, int heavesMissed) {
    }

    /** The team that gets the difficulty's skill bonus: the NPC-only team opposing a team with humans (-1: none). */
    public static int boostedTeam(List<Entrant> entrants) {
        boolean humanA = false, humanB = false;
        for (Entrant e : entrants) {
            if (e.human()) {
                if (e.team() == TugRules.TEAM_A) {
                    humanA = true;
                } else {
                    humanB = true;
                }
            }
        }
        if (humanA && !humanB) {
            return TugRules.TEAM_B;
        }
        if (humanB && !humanA) {
            return TugRules.TEAM_A;
        }
        return -1;
    }

    /** Specs for the simulation: traits from the personalities, the opposing team of the humans boosted by the difficulty. */
    public static List<TugSim.Spec> specs(List<Entrant> entrants, TugRules.Params params) {
        int boosted = boostedTeam(entrants);
        List<TugSim.Spec> specs = new ArrayList<>();
        for (Entrant e : entrants) {
            double bonus = e.team() == boosted ? params.npcSkillBonus() * TugRules.NPC_STRENGTH_SHARE : 0.0;
            specs.add(new TugSim.Spec(e.id(), e.team(), TugTraits.strength(e.personality(), bonus), TugTraits.endurance(e.personality())));
        }
        return specs;
    }

    /** Force multiplier of a team (see {@link TeamPlanner#handicap}). */
    public static double handicap(List<Entrant> entrants, int team) {
        int mine = 0, other = 0;
        for (Entrant e : entrants) {
            if (e.team() == team) {
                mine++;
            } else {
                other++;
            }
        }
        return TeamPlanner.handicap(mine, other);
    }

    /** Observes the simulation after every tick (tests record the rope's trajectory). */
    public interface Tracer {
        void tick(long tick, TugSim sim);
    }

    public static Result run(Difficulty difficulty, List<Entrant> entrants, Rng rng) {
        return run(difficulty, entrants, Map.of(), rng, null);
    }

    public static Result run(Difficulty difficulty, List<Entrant> entrants, Map<Integer, Controller> overrides, Rng rng,
                             Tracer tracer) {
        return run(difficulty, entrants, overrides, rng, tracer, handicap(entrants, TugRules.TEAM_A), handicap(entrants, TugRules.TEAM_B));
    }

    /**
     * @param overrides scripted controllers by contestant id (everybody else is an NPC policy)
     * @param tracer    optional observer, called after every simulated tick
     */
    public static Result run(Difficulty difficulty, List<Entrant> entrants, Map<Integer, Controller> overrides, Rng rng,
                             Tracer tracer, double handicapA, double handicapB) {
        TugRules.Params params = TugRules.params(difficulty);
        BeatClock beat = new BeatClock(0, TugRules.beatPeriod(rng.fork(1)));
        TugSim sim = new TugSim(params, beat, specs(entrants, params), handicapA, handicapB, 0);
        int boosted = boostedTeam(entrants);
        int n = entrants.size();
        Controller[] brains = new Controller[n];
        double[] forgiveness = new double[n];
        for (int i = 0; i < n; i++) {
            Entrant e = entrants.get(i);
            Controller c = overrides.get(e.id());
            if (c == null) {
                double bonus = e.team() == boosted ? params.npcSkillBonus() : 0.0;
                TugNpcPolicy policy = new TugNpcPolicy(e.personality(), bonus, rng.fork(1000L + e.id()), params.window());
                c = policy::decide;
            } else {
                forgiveness[i] = TugRules.HUMAN_FORGIVENESS;
            }
            brains[i] = c;
        }
        int limit = params.heatLimitTicks();
        long tick = 0;
        boolean sudden = false;
        long end = limit;
        while (sim.outcome() == TugSim.Outcome.ONGOING) {
            if (tick >= end) {
                TugSim.Outcome v = sim.verdictAtTimeout();
                if (v != TugSim.Outcome.ONGOING || sudden) {
                    break;
                }
                sudden = true;
                sim.startSuddenDeath();
                end = tick + params.suddenDeathTicks();
                continue;
            }
            step(sim, entrants, brains, forgiveness, tick, limit, end, sudden);
            if (tracer != null) {
                tracer.tick(tick, sim);
            }
            tick++;
        }
        TugSim.Outcome out = sim.outcome();
        boolean timedOut = out == TugSim.Outcome.ONGOING;
        boolean coin = false;
        if (timedOut) {
            out = sim.verdictAtTimeout();
            if (out == TugSim.Outcome.ONGOING) {
                coin = true;
                out = rng.fork(2).nextBoolean() ? TugSim.Outcome.A_WINS : TugSim.Outcome.B_WINS;
            }
        }
        int hit = 0, missed = 0;
        for (int i = 0; i < sim.size(); i++) {
            hit += sim.member(i).heavesHit();
            missed += sim.member(i).heavesMissed();
        }
        return new Result(out == TugSim.Outcome.A_WINS ? TugRules.TEAM_A : TugRules.TEAM_B, sim.offset(), (int) tick, timedOut, sudden, coin,
                sim.meanStamina(TugRules.TEAM_A), sim.meanStamina(TugRules.TEAM_B), hit, missed);
    }

    private static void step(TugSim sim, List<Entrant> entrants, Controller[] brains, double[] forgiveness, long tick, int limit,
                             long end, boolean sudden) {
        double timeLeft = Math.max(0.0, (end - tick) / (double) limit);
        double[] strained = {sim.strainedFraction(TugRules.TEAM_A), sim.strainedFraction(TugRules.TEAM_B)};
        double offset = sim.offset();
        for (int i = 0; i < brains.length; i++) {
            TugSim.Member m = sim.member(i);
            if (!m.present()) {
                continue;
            }
            int team = entrants.get(i).team();
            double lead = team == TugRules.TEAM_B ? offset : -offset;
            TugNpcPolicy.View view = new TugNpcPolicy.View(tick, lead, m.stamina(), m.exhausted(), strained[team],
                    strained[1 - team], timeLeft, sim.beat(), sudden);
            TugNpcPolicy.Action a = brains[i].decide(view);
            sim.setStance(i, a.effort(), a.brace());
            if (a.pressTick() >= 0) {
                sim.heave(i, a.pressTick(), forgiveness[i]);
            }
        }
        sim.tick(tick);
    }
}
