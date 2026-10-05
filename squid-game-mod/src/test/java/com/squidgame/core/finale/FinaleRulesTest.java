package com.squidgame.core.finale;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The numbers of the duel: helpers, charge scaling and the difficulty table. */
class FinaleRulesTest {
    @Test
    void yawAndDirectionAreInverse() {
        for (int yaw = -180; yaw < 180; yaw += 15) {
            double back = FinaleRules.yawOf(FinaleRules.forwardX(yaw), FinaleRules.forwardZ(yaw));
            assertEquals(0.0, FinaleRules.angleDiff(yaw, back), 1e-6, "yaw " + yaw);
        }
        assertEquals(0.0, FinaleRules.yawOf(0, 1), 1e-9, "south is yaw 0");
        assertEquals(90.0, FinaleRules.yawOf(-1, 0), 1e-9, "west is yaw 90");
        assertEquals(180.0, Math.abs(FinaleRules.yawOf(0, -1)), 1e-9, "north is yaw 180");
        assertEquals(-90.0, FinaleRules.yawOf(1, 0), 1e-9, "east is yaw -90");
    }

    @Test
    void angleDifferenceIsShortestAndSigned() {
        assertEquals(10, FinaleRules.angleDiff(0, 10), 1e-9);
        assertEquals(-10, FinaleRules.angleDiff(10, 0), 1e-9);
        assertEquals(20, FinaleRules.angleDiff(170, -170), 1e-9, "across the seam");
        assertEquals(-20, FinaleRules.angleDiff(-170, 170), 1e-9);
        assertEquals(-180, FinaleRules.angleDiff(0, 180), 1e-9);
        for (int a = -360; a <= 360; a += 45) {
            for (int b = -360; b <= 360; b += 45) {
                double d = FinaleRules.angleDiff(a, b);
                assertTrue(d >= -180 && d < 180, a + " -> " + b + " = " + d);
            }
        }
    }

    @Test
    void heavyStrikesGrowWithTheChargeUpToACap() {
        assertEquals(0.0, FinaleRules.heavyPower(0), 1e-9);
        assertEquals(0.0, FinaleRules.heavyPower(FinaleRules.HEAVY_MIN_HOLD), 1e-9);
        assertEquals(1.0, FinaleRules.heavyPower(FinaleRules.HEAVY_FULL_HOLD), 1e-9);
        assertEquals(1.0, FinaleRules.heavyPower(500), 1e-9);
        double lastDamage = 0, lastKnock = 0, lastDrain = 0;
        for (int held = FinaleRules.HEAVY_MIN_HOLD; held <= FinaleRules.HEAVY_FULL_HOLD; held++) {
            double p = FinaleRules.heavyPower(held);
            assertTrue(FinaleRules.heavyDamage(p) >= lastDamage);
            assertTrue(FinaleRules.heavyKnockback(p) >= lastKnock);
            assertTrue(FinaleRules.heavyBlockDrain(p) >= lastDrain);
            lastDamage = FinaleRules.heavyDamage(p);
            lastKnock = FinaleRules.heavyKnockback(p);
            lastDrain = FinaleRules.heavyBlockDrain(p);
        }
        assertEquals(FinaleRules.HEAVY.damage(), FinaleRules.heavyDamage(0), 1e-9);
        assertTrue(FinaleRules.heavyDamage(1) > FinaleRules.heavyDamage(0));
    }

    @Test
    void theActionsAreOrderedByWeight() {
        FinaleRules.Spec light = FinaleRules.LIGHT, heavy = FinaleRules.HEAVY, shove = FinaleRules.SHOVE;
        assertTrue(light.windup() < shove.windup() && light.windup() <= heavy.windup(), "a light strike is the fastest");
        assertTrue(light.cost() < shove.cost() && shove.cost() < heavy.cost(), "and the cheapest, the heavy strike costs most");
        assertTrue(light.damage() < heavy.damage());
        assertTrue(shove.damage() < light.damage(), "a shove is for position, not damage");
        assertTrue(shove.knockback() > heavy.knockback() && heavy.knockback() > light.knockback(), "a shove pushes furthest");
        assertTrue(light.recovery() < heavy.recovery());
        // two lights per exchange are not enough to knock somebody out
        assertTrue(100 / light.damage() > 8, "a knockout takes at least nine clean light strikes");
        assertTrue(FinaleRules.travel(shove.knockback()) > 2.0, "a shove moves somebody over two blocks");
    }

    @Test
    void theDifficultyTableGetsHarderStepByStep() {
        FinaleRules.Params n = FinaleRules.params(Difficulty.NORMAL);
        FinaleRules.Params h = FinaleRules.params(Difficulty.HARD);
        FinaleRules.Params e = FinaleRules.params(Difficulty.EXTREME);
        assertTrue(n.duelTicks() > h.duelTicks() && h.duelTicks() > e.duelTicks(), "less time");
        assertTrue(n.staminaMax() > h.staminaMax() && h.staminaMax() > e.staminaMax(), "a smaller stamina pool");
        assertTrue(n.staminaRegen() > h.staminaRegen() && h.staminaRegen() > e.staminaRegen(), "slower regeneration");
        assertTrue(n.regenDelay() < h.regenDelay() && h.regenDelay() < e.regenDelay(), "a longer wait before it starts");
        assertTrue(n.damageScale() < h.damageScale() && h.damageScale() < e.damageScale(), "more damage per hit");
        assertTrue(n.dodgeIframes() > h.dodgeIframes() && h.dodgeIframes() > e.dodgeIframes(), "a tighter dodge window");
        assertTrue(n.parryWindow() > h.parryWindow() && h.parryWindow() > e.parryWindow(), "a tighter parry window");
        assertEquals(n.captureTicks(), h.captureTicks(), "the capture time is the same everywhere: it is the objective");
        assertEquals(n.captureTicks(), e.captureTicks());
        assertEquals(1.0, n.damageScale(), 1e-9, "normal is the reference");
    }

    @Test
    void everyDifficultyIsPlayable() {
        for (Difficulty d : Difficulty.values()) {
            FinaleRules.Params p = FinaleRules.params(d);
            assertTrue(p.duelTicks() >= 100 * 20, "at least 100 s per duel");
            assertTrue(p.staminaMax() >= 60);
            assertTrue(p.dodgeIframes() >= 3, "a dodge window a human can hit on a 50 ms server tick");
            assertTrue(p.parryWindow() >= 2);
            // stamina pays for at least five light strikes in a row and one heavy strike plus a dodge
            assertTrue(p.staminaMax() >= 5 * FinaleRules.LIGHT.cost());
            assertTrue(p.staminaMax() >= FinaleRules.HEAVY.cost() + FinaleRules.DODGE_COST);
            assertTrue(p.captureTicks() >= 20 && p.captureTicks() <= 100, "one to five seconds in the circle");
            assertTrue(p.staminaRegen() * 20 > 5, "at least five stamina per second");
        }
    }

    @Test
    void opponentsGetStrongerOnHarderDifficulties() {
        Rng rng = new Rng(3);
        for (Personality.Archetype a : Personality.Archetype.values()) {
            Personality p = Personality.generate(rng, a);
            double normal = p.effectiveSkill(Difficulty.NORMAL);
            double hard = p.effectiveSkill(Difficulty.HARD);
            double extreme = p.effectiveSkill(Difficulty.EXTREME);
            assertTrue(normal <= hard && hard <= extreme, a + " skill " + normal + " " + hard + " " + extreme);
            int rn = p.reactionDelayTicks(new Rng(5), Difficulty.NORMAL);
            int re = p.reactionDelayTicks(new Rng(5), Difficulty.EXTREME);
            assertTrue(re <= rn, a + " reaction " + rn + " -> " + re);
        }
    }

    @Test
    void movementConstantsMatchVanilla() {
        assertEquals(4.317, FinaleRules.WALK * 20, 0.01, "walking speed in blocks per second");
        assertEquals(5.612, FinaleRules.SPRINT * 20, 0.01, "sprinting speed in blocks per second");
        assertTrue(FinaleRules.DASH_SPEED * FinaleRules.DASH_TICKS > 2.5, "a dodge covers about three blocks");
        assertTrue(FinaleRules.DASH_SPEED > FinaleRules.SPRINT, "and is faster than sprinting");
        assertTrue(FinaleRules.DODGE_COOLDOWN > FinaleRules.DODGE_LENGTH, "a dodge cannot be chained at once");
        assertTrue(FinaleRules.CAPTURE_FRACTION > 0 && FinaleRules.CAPTURE_FRACTION < 1);
        assertTrue(FinaleRules.DASH_EDGE > FinaleRules.OUT_MARGIN, "a dash stops before the line");
        assertEquals(FinaleRules.DASH_SPEED * 1.2, FinaleRules.DASH_SLIDE, 0.05, "a player slides about 0.6 blocks after the last tick of a dash");
    }
}
