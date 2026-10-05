package com.squidgame.core.finale;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NPC finalists in numbers: whole duels between two {@link NpcBrain}s, simulated headless. The thresholds leave a wide
 * margin around what the tuning gives (they would only fail if the NPCs became lopsided, passive, or stopped using the
 * rules); the point is to catch a duel that nobody would enjoy watching.
 */
class NpcDuelTest {
    private static final CourtGeometry COURT = SquidShape.court();

    private static List<DuelSimulator.Result> sample(Difficulty d, int n, long seedBase) {
        List<DuelSimulator.Result> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Rng r = new Rng(seedBase + i);
            Personality a = Personality.generate(r), b = Personality.generate(r);
            Role first = i % 2 == 0 ? Role.ATTACKER : Role.DEFENDER;
            out.add(new DuelSimulator(d, COURT, a, b, first, seedBase * 7 + i).run());
        }
        return out;
    }

    private static double share(List<DuelSimulator.Result> rs, Duel.Reason reason) {
        return rs.stream().filter(r -> r.outcome().reason() == reason).count() / (double) rs.size();
    }

    // ------------------------------------------------------------------ determinism

    @Test
    void theSameSeedPlaysTheSameDuel() {
        Rng r = new Rng(5);
        Personality a = Personality.generate(r), b = Personality.generate(r);
        DuelSimulator.Result one = new DuelSimulator(Difficulty.NORMAL, COURT, a, b, Role.ATTACKER, 99).run();
        DuelSimulator.Result two = new DuelSimulator(Difficulty.NORMAL, COURT, a, b, Role.ATTACKER, 99).run();
        assertEquals(one.outcome(), two.outcome());
        assertEquals(one.log().size(), two.log().size());
        for (int i = 0; i < one.log().size(); i++) {
            assertEquals(one.log().get(i), two.log().get(i), "event " + i);
        }
        assertEquals(one.summary()[0], two.summary()[0]);
        assertEquals(one.summary()[1], two.summary()[1]);
    }

    @Test
    void otherSeedsPlayOtherDuels() {
        Rng r = new Rng(5);
        Personality a = Personality.generate(r), b = Personality.generate(r);
        Map<Integer, Integer> lengths = new java.util.HashMap<>();
        for (long seed = 1; seed <= 12; seed++) {
            lengths.merge(new DuelSimulator(Difficulty.NORMAL, COURT, a, b, Role.ATTACKER, seed).run().ticks(), 1, Integer::sum);
        }
        assertTrue(lengths.size() >= 6, "twelve seeds give at least six different durations: " + lengths);
    }

    @Test
    void theLogIsOrderedAndConsistentWithTheSummary() {
        for (DuelSimulator.Result res : sample(Difficulty.NORMAL, 25, 4000)) {
            int last = 0, impacts = 0;
            for (DuelSimulator.Timed t : res.log()) {
                assertTrue(t.tick() >= last, "events are in order");
                assertTrue(t.tick() <= res.outcome().tick());
                last = t.tick();
                CombatEvent.Type type = t.event().type();
                if (type == CombatEvent.Type.HIT || type == CombatEvent.Type.BLOCKED || type == CombatEvent.Type.GUARD_BREAK) {
                    impacts++;
                    assertNotNull(t.event().kind(), "an impact always knows what caused it");
                }
            }
            assertEquals(res.summary()[0].hitsLanded() + res.summary()[1].hitsLanded(), impacts);
            assertEquals(res.summary()[0].hitsLanded(), res.summary()[1].hitsTaken());
            assertEquals(2, res.roles().length);
            assertNotEquals(res.roles()[0], res.roles()[1]);
        }
    }

    // ------------------------------------------------------------------ balance and variety

    @Test
    void neitherRoleHasAnUnfairAdvantageOnAnyDifficulty() {
        for (Difficulty d : Difficulty.values()) {
            List<DuelSimulator.Result> rs = sample(d, 240, 1000);
            double attacker = rs.stream().filter(r -> r.winnerRole() == Role.ATTACKER).count() / (double) rs.size();
            assertTrue(attacker >= 0.40 && attacker <= 0.68, d + ": the attacker wins " + attacker);
        }
    }

    @Test
    void everyKindOfEndingHappensAndTimeoutsAreRare() {
        List<DuelSimulator.Result> rs = sample(Difficulty.NORMAL, 300, 1000);
        Map<Duel.Reason, Double> shares = new EnumMap<>(Duel.Reason.class);
        for (Duel.Reason reason : Duel.Reason.values()) {
            shares.put(reason, share(rs, reason));
        }
        assertTrue(shares.get(Duel.Reason.KNOCKOUT) >= 0.10, "knockouts " + shares);
        assertTrue(shares.get(Duel.Reason.CAPTURE) >= 0.10, "captures " + shares);
        assertTrue(shares.get(Duel.Reason.OUT_OF_BOUNDS) >= 0.08, "pushed over the line " + shares);
        assertTrue(shares.get(Duel.Reason.TIMEOUT) <= 0.03, "timeouts " + shares);
        assertEquals(0.0, shares.get(Duel.Reason.FORFEIT), "nobody forfeits a simulated duel");
    }

    @Test
    void duelsAreWatchableAndNeverOverrunTheClock() {
        for (Difficulty d : Difficulty.values()) {
            List<DuelSimulator.Result> rs = sample(d, 150, 2000);
            List<Integer> ticks = new ArrayList<>();
            for (DuelSimulator.Result r : rs) {
                ticks.add(r.ticks());
                assertTrue(r.ticks() <= FinaleRules.params(d).duelTicks(), d + ": within the time limit");
            }
            Collections.sort(ticks);
            assertTrue(ticks.get(ticks.size() / 10) >= 8 * 20, d + ": nine in ten duels last at least eight seconds");
            int median = ticks.get(ticks.size() / 2);
            assertTrue(median >= 20 * 20 && median <= 60 * 20, d + ": the median duel lasts " + median / 20.0 + " s");
        }
    }

    @Test
    void fightersUseTheWholeToolbox() {
        List<DuelSimulator.Result> rs = sample(Difficulty.NORMAL, 200, 3000);
        double lights = 0, heavies = 0, shoves = 0, dodges = 0, blocks = 0, parries = 0, whiffs = 0;
        for (DuelSimulator.Result r : rs) {
            for (Duel.Summary s : r.summary()) {
                lights += s.lights();
                heavies += s.heavies();
                shoves += s.shoves();
                dodges += s.dodges();
                blocks += s.blocked();
                parries += s.parries();
                whiffs += s.whiffs();
            }
        }
        int n = rs.size();
        assertTrue(lights / n >= 9, "light strikes per duel " + lights / n);
        assertTrue(heavies / n >= 1.0, "heavy strikes per duel " + heavies / n);
        assertTrue(shoves / n >= 2.5, "shoves per duel " + shoves / n);
        assertTrue(dodges / n >= 0.7, "dodges per duel " + dodges / n);
        assertTrue((blocks + parries) / n >= 1.0, "blocks and parries per duel " + (blocks + parries) / n);
        assertTrue(whiffs / n >= 1.0, "misses per duel " + whiffs / n + ": fighters bait and mistime too");
    }

    @Test
    void nobodyWalksOverTheLineByThemselves() {
        for (Difficulty d : Difficulty.values()) {
            for (DuelSimulator.Result r : sample(d, 150, 31)) {
                if (r.outcome().reason() == Duel.Reason.OUT_OF_BOUNDS) {
                    assertTrue(r.summary()[r.outcome().loser()].hitsTaken() >= 1,
                            d + ": a fighter that left the court had been hit or pushed before");
                }
            }
        }
    }

    // ------------------------------------------------------------------ difficulty and personality

    @Test
    void harderDifficultiesHitHarder() {
        double normal = damagePerHit(sample(Difficulty.NORMAL, 100, 6000));
        double extreme = damagePerHit(sample(Difficulty.EXTREME, 100, 6000));
        assertTrue(extreme > normal * 1.12, "damage per landed hit " + normal + " -> " + extreme);
    }

    private static double damagePerHit(List<DuelSimulator.Result> rs) {
        double damage = 0, hits = 0;
        for (DuelSimulator.Result r : rs) {
            for (Duel.Summary s : r.summary()) {
                damage += s.damageDealt();
                hits += s.hitsLanded();
            }
        }
        return damage / hits;
    }

    @Test
    void skilledFightersBeatClumsyOnes() {
        int highWins = 0, n = 240;
        for (int i = 0; i < n; i++) {
            Rng r = new Rng(77 + i);
            Personality hi = withSkill(Personality.generate(r, Personality.Archetype.ATHLETE), 0.9f);
            Personality lo = withSkill(Personality.generate(r, Personality.Archetype.ATHLETE), 0.15f);
            boolean hiFirst = i % 2 == 0;
            Role role0 = (i / 2) % 2 == 0 ? Role.ATTACKER : Role.DEFENDER;
            DuelSimulator.Result res = (hiFirst ? new DuelSimulator(Difficulty.NORMAL, COURT, hi, lo, role0, i)
                    : new DuelSimulator(Difficulty.NORMAL, COURT, lo, hi, role0, i)).run();
            if (res.outcome().winner() == (hiFirst ? 0 : 1)) {
                highWins++;
            }
        }
        assertTrue(highWins > n * 0.55, "the skilled fighter wins " + highWins + " of " + n);
        assertTrue(highWins < n * 0.95, "but skill is not everything: " + highWins + " of " + n);
    }

    private static Personality withSkill(Personality p, float skill) {
        return new Personality(p.archetype(), p.courage(), p.reactionSpeed(), p.patience(), skill, p.aggression(),
                p.cooperation(), p.riskTolerance());
    }

    @Test
    void boldDefendersHoldTheNeckAndTimidOnesWaitAtTheCircle() {
        double bold = defenderDistanceFromGoal(Personality.Archetype.BRAWLER);
        double timid = defenderDistanceFromGoal(Personality.Archetype.NERVOUS);
        assertTrue(bold > timid + 6, "a brawler waits " + bold + " blocks from the circle's centre, a nervous one " + timid);
    }

    /** Average distance of a defender of the given type from the circle centre while a passive rookie attacks. */
    private static double defenderDistanceFromGoal(Personality.Archetype arch) {
        double sum = 0;
        int n = 20;
        for (int i = 0; i < n; i++) {
            Rng r = new Rng(500 + i);
            Personality def = Personality.generate(r, arch);
            Personality atk = Personality.generate(r, Personality.Archetype.ROOKIE);
            DuelSimulator sim = new DuelSimulator(Difficulty.NORMAL, COURT, atk, def, Role.ATTACKER, 900 + i);
            double acc = 0;
            int cnt = 0;
            for (int t = 1; t <= 520 && !sim.duel().finished(); t++) {
                sim.step();
                if (t >= 200) {
                    double[] p = sim.position(1);
                    acc += Math.hypot(p[0] - COURT.circleCenter().x(), p[1] - COURT.circleCenter().z());
                    cnt++;
                }
            }
            sum += cnt == 0 ? 0 : acc / cnt;
        }
        return sum / n;
    }

    @Test
    void personalityShowsInHowTheyFight() {
        double reckless = meanSeconds(Personality.Archetype.RECKLESS);
        double nervous = meanSeconds(Personality.Archetype.NERVOUS);
        assertTrue(nervous > reckless * 1.25, "nervous fighters take their time (" + nervous + " s), reckless ones do not (" + reckless + " s)");
        assertTrue(shovesPerDuel(Personality.Archetype.CALCULATING) > shovesPerDuel(Personality.Archetype.ROOKIE) * 1.5,
                "a calculating fighter uses the line, a rookie rarely thinks of shoving");
        assertTrue(blocksPerDuel(Personality.Archetype.NERVOUS) > blocksPerDuel(Personality.Archetype.RECKLESS),
                "a nervous fighter blocks more than a reckless one");
    }

    private static List<DuelSimulator.Result> mirror(Personality.Archetype arch) {
        List<DuelSimulator.Result> out = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            Rng r = new Rng(1000 + i);
            out.add(new DuelSimulator(Difficulty.NORMAL, COURT, Personality.generate(r, arch), Personality.generate(r, arch),
                    i % 2 == 0 ? Role.ATTACKER : Role.DEFENDER, 77 + i).run());
        }
        return out;
    }

    private static double meanSeconds(Personality.Archetype arch) {
        return mirror(arch).stream().mapToInt(DuelSimulator.Result::ticks).average().orElse(0) / 20.0;
    }

    private static double shovesPerDuel(Personality.Archetype arch) {
        return mirror(arch).stream().mapToInt(r -> r.summary()[0].shoves() + r.summary()[1].shoves()).average().orElse(0);
    }

    private static double blocksPerDuel(Personality.Archetype arch) {
        return mirror(arch).stream().mapToInt(r -> r.summary()[0].blocked() + r.summary()[1].blocked()
                + r.summary()[0].parries() + r.summary()[1].parries()).average().orElse(0);
    }

    @Test
    void everyArchetypeCanFightEveryOtherInBothRoles() {
        Personality.Archetype[] all = Personality.Archetype.values();
        for (int i = 0; i < all.length; i++) {
            for (int j = 0; j < all.length; j++) {
                for (Role role : Role.values()) {
                    Rng r = new Rng(i * 31L + j);
                    DuelSimulator.Result res = new DuelSimulator(Difficulty.HARD, COURT, Personality.generate(r, all[i]),
                            Personality.generate(r, all[j]), role, i * 100L + j).run();
                    assertNotNull(res.outcome());
                    assertTrue(res.ticks() > 0 && res.ticks() <= FinaleRules.params(Difficulty.HARD).duelTicks());
                }
            }
        }
    }

    @Test
    void theNpcsPlayAnywhereOnTheMapNotJustAtTheOrigin() {
        // the real arena lies at x = 6000: the brain and the rules must not care where the court is. The duels are not
        // identical (rounding noise breaks the exact ties of a symmetric court), but they must come out alike
        CourtGeometry far = SquidShape.court(6000, 0);
        int n = 200;
        double[] near = new double[4], away = new double[4];
        for (int i = 0; i < n; i++) {
            Rng r = new Rng(1000 + i);
            Personality a = Personality.generate(r), b = Personality.generate(r);
            Role first = i % 2 == 0 ? Role.ATTACKER : Role.DEFENDER;
            tally(near, new DuelSimulator(Difficulty.NORMAL, COURT, a, b, first, 7 + i).run());
            tally(away, new DuelSimulator(Difficulty.NORMAL, far, a, b, first, 7 + i).run());
        }
        for (int k = 0; k < 4; k++) {
            assertEquals(near[k] / n, away[k] / n, 0.12, "outcome statistic " + k + " differs between the two places");
        }
    }

    /** Counts attacker wins, knockouts, captures and pushes over the line. */
    private static void tally(double[] into, DuelSimulator.Result r) {
        into[0] += r.winnerRole() == Role.ATTACKER ? 1 : 0;
        into[1] += r.outcome().reason() == Duel.Reason.KNOCKOUT ? 1 : 0;
        into[2] += r.outcome().reason() == Duel.Reason.CAPTURE ? 1 : 0;
        into[3] += r.outcome().reason() == Duel.Reason.OUT_OF_BOUNDS ? 1 : 0;
    }
}
