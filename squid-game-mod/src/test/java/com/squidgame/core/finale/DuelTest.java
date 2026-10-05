package com.squidgame.core.finale;

import com.squidgame.core.Difficulty;
import com.squidgame.core.util.Rng;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The rules of a duel in isolation: every outcome of an exchange, the end conditions and the difficulty table. */
class DuelTest {
    /** Slot 0 = attacker at (0.5, 17) looking north, slot 1 = defender 1.5 blocks in front of it looking back. */
    private static Duel duel(Difficulty d) {
        Duel duel = new Duel(FinaleRules.params(d), SquidShape.court(), Role.ATTACKER, new Rng(1));
        duel.setPose(0, 0.5, 17.0, 180);
        duel.setPose(1, 0.5, 15.5, 0);
        return duel;
    }

    private static Duel duel() {
        return duel(Difficulty.NORMAL);
    }

    /** Runs {@code n} ticks (re-feeding the same poses) and returns every event. */
    private static List<CombatEvent> run(Duel d, int n) {
        List<CombatEvent> all = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            all.addAll(d.step());
        }
        return all;
    }

    private static CombatEvent first(List<CombatEvent> ev, CombatEvent.Type type) {
        return ev.stream().filter(e -> e.type() == type).findFirst().orElse(null);
    }

    private static long count(List<CombatEvent> ev, CombatEvent.Type type) {
        return ev.stream().filter(e -> e.type() == type).count();
    }

    private static void tap(Duel d, int slot) {
        d.attackDown(slot);
        d.attackUp(slot);
    }

    private static void tapAndStep(Duel d, int slot, int ticks) {
        // a tap = down now, up on the next tick
        d.attackDown(slot);
        d.step();
        d.attackUp(slot);
        for (int i = 1; i < ticks; i++) {
            d.step();
        }
    }

    // ------------------------------------------------------------------ light strike

    @Test
    void lightStrikeLandsFourTicksAfterThePress() {
        Duel d = duel();
        tap(d, 0);
        List<CombatEvent> ev = new ArrayList<>();
        int hitTick = -1;
        for (int t = 1; t <= 8 && hitTick < 0; t++) {
            List<CombatEvent> now = d.step();
            ev.addAll(now);
            if (first(now, CombatEvent.Type.HIT) != null) {
                hitTick = t;
            }
        }
        assertEquals(1 + FinaleRules.TAP_SWING, hitTick, "pressed in tick 1, lands in tick 5");
        CombatEvent hit = first(ev, CombatEvent.Type.HIT);
        assertEquals(FinaleRules.LIGHT.damage(), hit.amount(), 1e-9);
        assertEquals(100 - FinaleRules.LIGHT.damage(), d.health(1), 1e-9);
        assertEquals(1, hit.impulseSlot(), "the victim is pushed");
        assertEquals(0.0, hit.ix(), 1e-9);
        assertEquals(-FinaleRules.LIGHT.knockback(), hit.iz(), 1e-9, "away from the attacker (north)");
        assertEquals(Act.STUN, d.view(1).act());
        assertEquals(100 - FinaleRules.LIGHT.cost() + FinaleRules.HIT_REWARD, d.stamina(0), 1e-9);
    }

    @Test
    void aTapReleasedLaterStillLandsAtThePressPlusSwingOrAtTheRelease() {
        // released after 2 ticks: still lands 4 ticks after the press
        Duel d = duel();
        d.attackDown(0);
        d.step();
        d.step();
        d.attackUp(0);
        List<CombatEvent> ev = new ArrayList<>(d.step());
        ev.addAll(d.step());
        assertNull(first(ev, CombatEvent.Type.HIT), "not yet: press in tick 1 lands in tick 5");
        ev.addAll(d.step());
        assertNotNull(first(ev, CombatEvent.Type.HIT));
    }

    @Test
    void holdingMoreThanASwingBeforeReleaseLandsAtOnce() {
        Duel d = duel();
        d.attackDown(0);
        run(d, 6);
        d.attackUp(0);
        List<CombatEvent> ev = d.step();
        assertNotNull(first(ev, CombatEvent.Type.HIT), "released after 7 ticks, below the heavy threshold: a light strike right now");
        assertEquals(FinaleRules.LIGHT.damage(), first(ev, CombatEvent.Type.HIT).amount(), 1e-9);
    }

    @Test
    void outOfReachAndOutsideTheConeWhiff() {
        Duel far = duel();
        far.setPose(1, 0.5, 13.0, 0);
        tap(far, 0);
        List<CombatEvent> ev = run(far, 8);
        assertNotNull(first(ev, CombatEvent.Type.WHIFF));
        assertNull(first(ev, CombatEvent.Type.HIT));
        assertEquals(100, far.health(1), 1e-9);

        Duel behind = duel();
        behind.setPose(1, 0.5, 18.5, 180); // directly behind the attacker
        tap(behind, 0);
        assertNotNull(first(run(behind, 8), CombatEvent.Type.WHIFF), "strikes only hit in front");

        Duel flank = duel();
        flank.setPose(1, 2.0, 16.5, 0);    // about 55 degrees off the line of sight: just outside the 50 degree cone
        tap(flank, 0);
        assertNull(first(run(flank, 8), CombatEvent.Type.HIT));
    }

    @Test
    void aWhiffCostsExtraRecovery() {
        Duel d = duel();
        d.setPose(1, 0.5, 12.0, 0);
        tap(d, 0);
        int normal = FinaleRules.LIGHT.windup() + FinaleRules.LIGHT.recovery();
        run(d, normal + 1);
        assertEquals(Act.LIGHT, d.view(0).act(), "a whiff keeps the attacker busy longer than a hit would");
        run(d, FinaleRules.LIGHT.whiff());
        assertEquals(Act.NONE, d.view(0).act());
    }

    // ------------------------------------------------------------------ heavy strike

    @Test
    void longHoldChargesAHeavyStrike() {
        Duel d = duel();
        d.attackDown(0);
        List<CombatEvent> ev = new ArrayList<>(run(d, 6));
        assertNull(first(ev, CombatEvent.Type.CHARGE_START), "a short press is still just a tap");
        ev.addAll(run(d, 1));
        assertNotNull(first(ev, CombatEvent.Type.CHARGE_START), "charge becomes visible once held for 6 ticks");
        assertEquals(Act.CHARGE, d.view(0).act());
        ev.addAll(run(d, 8));
        d.attackUp(0);
        ev.addAll(run(d, 6));
        CombatEvent hit = first(ev, CombatEvent.Type.HIT);
        assertNotNull(hit);
        assertEquals(ActionKind.HEAVY, hit.kind());
        double power = FinaleRules.heavyPower(15);
        assertEquals(FinaleRules.heavyDamage(power), hit.amount(), 1e-9);
        assertTrue(hit.amount() > 2 * FinaleRules.LIGHT.damage(), "a heavy hit is worth well over two light ones");
        assertTrue(Math.abs(hit.iz()) > 2 * FinaleRules.LIGHT.knockback());
    }

    @Test
    void heavyPowerGrowsWithTheChargeUpToTheCap() {
        assertEquals(0, FinaleRules.heavyPower(FinaleRules.HEAVY_MIN_HOLD), 1e-9);
        assertEquals(1, FinaleRules.heavyPower(FinaleRules.HEAVY_FULL_HOLD), 1e-9);
        assertEquals(1, FinaleRules.heavyPower(FinaleRules.HEAVY_MAX_HOLD), 1e-9);
        assertTrue(FinaleRules.heavyDamage(1) > FinaleRules.heavyDamage(0));
    }

    @Test
    void theChargeIsReleasedAutomaticallyAtTheCap() {
        Duel d = duel();
        d.attackDown(0);
        List<CombatEvent> ev = run(d, FinaleRules.HEAVY_MAX_HOLD + FinaleRules.HEAVY.windup() + 3);
        CombatEvent hit = first(ev, CombatEvent.Type.HIT);
        assertNotNull(hit, "a button that never comes up still releases the heavy strike");
        assertEquals(ActionKind.HEAVY, hit.kind());
    }

    @Test
    void aLightStrikeInterruptsAChargeAsACounterHit() {
        Duel d = duel();
        d.attackDown(1);                       // the defender starts charging
        run(d, 8);
        assertEquals(Act.CHARGE, d.view(1).act());
        tap(d, 0);
        List<CombatEvent> ev = run(d, 6);
        CombatEvent hit = first(ev, CombatEvent.Type.HIT);
        assertNotNull(hit);
        assertEquals(FinaleRules.LIGHT.damage() * FinaleRules.COUNTER_BONUS, hit.amount(), 1e-9, "counter hit bonus");
        assertEquals(Act.STUN, d.view(1).act(), "the charge is gone");
        assertNull(first(run(d, 20), CombatEvent.Type.SWING), "and no heavy strike follows");
    }

    // ------------------------------------------------------------------ guard

    @Test
    void aGuardAbsorbsMostOfALightStrikeAndPaysWithStamina() {
        Duel d = duel();
        d.guard(1, true);
        run(d, 12);   // well past the parry window
        assertTrue(d.view(1).guardUp());
        double before = d.stamina(1);
        tap(d, 0);
        List<CombatEvent> ev = run(d, 8);
        CombatEvent blocked = first(ev, CombatEvent.Type.BLOCKED);
        assertNotNull(blocked);
        assertEquals(FinaleRules.LIGHT.damage() * FinaleRules.BLOCK_KEEPS, blocked.amount(), 1e-9);
        assertEquals(100 - FinaleRules.LIGHT.damage() * FinaleRules.BLOCK_KEEPS, d.health(1), 1e-9);
        assertTrue(d.stamina(1) <= before - FinaleRules.LIGHT.blockDrain() + 1.0, "the guard paid stamina");
        assertNull(first(ev, CombatEvent.Type.HIT));
        assertEquals(Act.NONE, d.view(1).act(), "a blocked hit does not stagger");
        assertTrue(Math.abs(blocked.iz()) < FinaleRules.LIGHT.knockback(), "and pushes less");
    }

    @Test
    void aGuardRaisedJustBeforeTheImpactParries() {
        Duel d = duel();
        d.attackDown(0);
        d.attackUp(0);
        d.guard(1, true);
        List<CombatEvent> ev = run(d, 8);
        assertNotNull(first(ev, CombatEvent.Type.PARRIED));
        assertEquals(100, d.health(1), 1e-9);
        assertEquals(Act.BROKEN, d.view(0).act(), "the parried attacker is staggered");
        assertEquals(0, first(ev, CombatEvent.Type.PARRIED).impulseSlot(), "and thrown back");
        assertTrue(d.stamina(1) > 99.0, "the parry pays the defender back");
    }

    @Test
    void theParryWindowShrinksOnHarderDifficulties() {
        assertTrue(FinaleRules.params(Difficulty.NORMAL).parryWindow() > FinaleRules.params(Difficulty.HARD).parryWindow());
        assertTrue(FinaleRules.params(Difficulty.HARD).parryWindow() > FinaleRules.params(Difficulty.EXTREME).parryWindow());
    }

    @Test
    void aHeavyStrikeBreaksAnExhaustedGuard() {
        Duel d = duel();
        d.guard(1, true);
        run(d, 12);
        d.fighter(1).stamina = 20;   // less than a heavy hit drains
        d.attackDown(0);
        run(d, 14);
        d.attackUp(0);
        List<CombatEvent> ev = run(d, 8);
        CombatEvent br = first(ev, CombatEvent.Type.GUARD_BREAK);
        assertNotNull(br, "guard broken");
        assertTrue(br.amount() > FinaleRules.heavyDamage(0) * 0.99, "the broken guard takes the full hit");
        assertEquals(Act.BROKEN, d.view(1).act());
        assertTrue(d.view(1).exhausted());
        assertEquals(0, d.stamina(1), 1e-9);
    }

    @Test
    void aShoveGoesThroughTheGuard() {
        Duel d = duel();
        d.guard(1, true);
        run(d, 12);
        d.shove(0);
        List<CombatEvent> ev = run(d, 10);
        CombatEvent b = first(ev, CombatEvent.Type.BLOCKED);
        assertNotNull(b);
        assertEquals(ActionKind.SHOVE, b.kind());
        assertTrue(Math.abs(b.iz()) > 0.5, "still a big push: " + b.iz());
        assertTrue(Math.abs(b.iz()) > FinaleRules.LIGHT.knockback() * 1.5);
    }

    @Test
    void aGuardOnlyCoversTheFront() {
        Duel d = duel();
        d.setPose(1, 0.5, 15.5, 180);   // defender looks away from the attacker
        d.guard(1, true);
        run(d, 12);
        tap(d, 0);
        List<CombatEvent> ev = run(d, 8);
        assertNotNull(first(ev, CombatEvent.Type.HIT), "hit in the back through the guard");
    }

    @Test
    void raisingTheGuardCancelsACharge() {
        Duel d = duel();
        d.attackDown(0);
        run(d, 8);
        assertEquals(Act.CHARGE, d.view(0).act());
        d.guard(0, true);
        assertEquals(Act.NONE, d.view(0).act(), "a feint: charge, then guard");
        d.attackUp(0);
        assertNull(first(run(d, 12), CombatEvent.Type.SWING), "the release does nothing");
    }

    // ------------------------------------------------------------------ dodge

    @Test
    void aDodgeInsideTheWindowAvoidsTheHit() {
        Duel d = duel();
        tap(d, 0);
        d.step();
        d.step();
        d.dodge(1, 1, 0);     // sideways, two ticks before the impact
        List<CombatEvent> ev = run(d, 6);
        assertNotNull(first(ev, CombatEvent.Type.DODGED));
        assertEquals(100, d.health(1), 1e-9);
        double[] dash = d.dash(1);
        assertTrue(dash != null || first(ev, CombatEvent.Type.DODGE) != null);
        CombatEvent dodge = first(ev, CombatEvent.Type.DODGE);
        assertEquals(FinaleRules.DASH_SPEED, dodge.ix(), 1e-9);
        assertEquals(1, dodge.impulseSlot());
    }

    @Test
    void aDodgeInTheVeryTickOfTheImpactStillWorks() {
        Duel d = duel();
        tap(d, 0);
        run(d, 4);
        d.dodge(1, 1, 0);
        List<CombatEvent> ev = d.step();
        assertNotNull(first(ev, CombatEvent.Type.DODGED), "inputs are processed before impacts");
        assertEquals(100, d.health(1), 1e-9);
    }

    @Test
    void aDodgeThatIsTooEarlyRunsOutBeforeTheHeavyStrikeLands() {
        Duel d = duel();
        d.attackDown(0);
        d.dodge(1, 1, 0);          // the dodge window closes long before the charge is released
        run(d, 14);
        d.attackUp(0);
        List<CombatEvent> ev = run(d, 8);
        CombatEvent hit = first(ev, CombatEvent.Type.HIT);
        assertNotNull(hit, "timing matters: dodging as soon as the charge starts is punished");
        assertEquals(ActionKind.HEAVY, hit.kind());
    }

    @Test
    void aDodgeCostsStaminaAndHasACooldown() {
        Duel d = duel();
        d.dodge(0, 1, 0);
        List<CombatEvent> ev = new ArrayList<>(d.step());
        assertEquals(FinaleRules.DODGE_COST, 100 - d.stamina(0) + 0, 1.0);
        assertNotNull(first(ev, CombatEvent.Type.DODGE));
        d.dodge(0, 1, 0);
        ev.clear();
        ev.addAll(run(d, 3));
        assertNull(first(ev, CombatEvent.Type.DODGE), "no second dodge during the cooldown");
        run(d, FinaleRules.DODGE_COOLDOWN);
        d.dodge(0, 1, 0);
        assertNotNull(first(run(d, 1), CombatEvent.Type.DODGE));
    }

    @Test
    void dodgingCancelsAWindUp() {
        Duel d = duel();
        d.attackDown(0);
        run(d, 10);
        assertEquals(Act.CHARGE, d.view(0).act());
        d.dodge(0, -1, 0);
        d.step();
        assertEquals(Act.DODGE, d.view(0).act());
        d.attackUp(0);
        assertNull(first(run(d, 12), CombatEvent.Type.HIT), "the released charge is gone");
    }

    @Test
    void theDodgeWindowShrinksOnHarderDifficulties() {
        int n = FinaleRules.params(Difficulty.NORMAL).dodgeIframes();
        int h = FinaleRules.params(Difficulty.HARD).dodgeIframes();
        int e = FinaleRules.params(Difficulty.EXTREME).dodgeIframes();
        assertTrue(n > h && h > e);
    }

    // ------------------------------------------------------------------ stamina

    @Test
    void actionsCostStaminaAndItComesBackAfterADelay() {
        Duel d = duel();
        d.setPose(1, 0.5, 8.0, 0);   // out of reach: nothing to hit, nothing to be rewarded for
        d.shove(0);
        d.step();
        double afterShove = d.stamina(0);
        assertEquals(100 - FinaleRules.SHOVE.cost(), afterShove, 1e-9);
        run(d, 20);
        assertTrue(d.stamina(0) <= afterShove + 1e-9, "no regeneration during the delay");
        run(d, 100);
        assertTrue(d.stamina(0) > afterShove + 10, "regenerated afterwards: " + d.stamina(0));
    }

    @Test
    void anExhaustedFighterCannotStartActionsUntilItHasRecovered() {
        Duel d = duel();
        d.fighter(0).stamina = 1;
        tap(d, 0);
        List<CombatEvent> ev = new ArrayList<>(d.step());
        assertNotNull(first(ev, CombatEvent.Type.DENIED), "not enough stamina for a strike");
        d.fighter(0).stamina = FinaleRules.LIGHT.cost();
        tap(d, 0);
        ev = run(d, 6);
        assertNotNull(first(ev, CombatEvent.Type.EXHAUSTED), "spending the last stamina exhausts the fighter");
        assertTrue(d.view(0).exhausted());
        run(d, 14);                  // the strike that exhausted the fighter is over
        d.attackDown(0);
        assertNotNull(first(run(d, 2), CombatEvent.Type.DENIED), "nothing starts while exhausted");
        run(d, 40 + 14 * 20);
        assertFalse(d.view(0).exhausted(), "recovered");
    }

    @Test
    void sprintingDrainsStamina() {
        Duel d = duel();
        d.setSprinting(0, true);
        run(d, 60);
        assertTrue(d.stamina(0) < 100 - 10, "sprinting for three seconds costs a visible amount: " + d.stamina(0));
        d.setSprinting(0, false);
        double low = d.stamina(0);
        run(d, 100);
        assertTrue(d.stamina(0) > low);
    }

    @Test
    void harderDifficultiesHaveSmallerStaminaPoolsAndMoreDamage() {
        FinaleRules.Params n = FinaleRules.params(Difficulty.NORMAL);
        FinaleRules.Params h = FinaleRules.params(Difficulty.HARD);
        FinaleRules.Params e = FinaleRules.params(Difficulty.EXTREME);
        assertTrue(n.staminaMax() > h.staminaMax() && h.staminaMax() > e.staminaMax());
        assertTrue(n.staminaRegen() > h.staminaRegen() && h.staminaRegen() > e.staminaRegen());
        assertTrue(n.damageScale() < h.damageScale() && h.damageScale() < e.damageScale());
        assertTrue(n.duelTicks() > h.duelTicks() && h.duelTicks() > e.duelTicks());
        assertEquals(180 * 20, n.duelTicks());
        assertEquals(150 * 20, h.duelTicks());
        assertEquals(120 * 20, e.duelTicks());
    }

    // ------------------------------------------------------------------ trades and chains

    @Test
    void strikesThatLandInTheSameTickBothConnect() {
        Duel d = duel();
        tap(d, 0);
        tap(d, 1);
        List<CombatEvent> ev = run(d, 6);
        assertEquals(2, count(ev, CombatEvent.Type.HIT));
        assertEquals(93, d.health(0), 1e-9);
        assertEquals(93, d.health(1), 1e-9);
        for (CombatEvent e : ev) {
            if (e.type() == CombatEvent.Type.HIT) {
                // the first strike applied interrupts the other fighter's action: its event still names its own kind
                assertEquals(ActionKind.LIGHT, e.kind(), "both events keep the kind of their strike");
            }
        }
    }

    @Test
    void simultaneousHeavyAndLightKeepTheirOwnKindsAndStuns() {
        Duel d = duel();
        d.fighter(1).stamina = 100;
        // slot 1 charges a heavy strike and releases it; slot 0 taps in the very tick it lands
        d.attackDown(1);
        run(d, FinaleRules.HEAVY_MIN_HOLD + 1);
        d.attackUp(1);
        List<CombatEvent> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            if (i == 1) {
                tap(d, 0);
            }
            all.addAll(d.step());
        }
        for (CombatEvent e : all) {
            if (e.type() == CombatEvent.Type.HIT || e.type() == CombatEvent.Type.BLOCKED || e.type() == CombatEvent.Type.GUARD_BREAK) {
                assertNotNull(e.kind(), "every impact names the kind of the strike that caused it");
            }
        }
    }

    @Test
    void aFighterThatJustRecoveredFromAStaggerCannotBeStaggeredAgainAtOnce() {
        Duel d = duel();
        tap(d, 0);
        run(d, 5);
        assertEquals(Act.STUN, d.view(1).act());
        int guard = 0;
        while (d.view(1).act() == Act.STUN && guard++ < 40) {
            d.step();
        }
        assertEquals(Act.NONE, d.view(1).act());
        tap(d, 0);
        List<CombatEvent> ev = run(d, 8);
        assertNotNull(first(ev, CombatEvent.Type.HIT), "the second strike still hurts");
        assertEquals(100 - 2 * FinaleRules.LIGHT.damage(), d.health(1), 1e-9);
        assertEquals(Act.NONE, d.view(1).act(), "but the grace period after a stagger prevents a stun lock");
    }

    // ------------------------------------------------------------------ end conditions

    @Test
    void healthZeroIsAKnockout() {
        Duel d = duel();
        d.fighter(1).health = 5;
        tap(d, 0);
        run(d, 8);
        assertTrue(d.finished());
        assertEquals(0, d.outcome().winner());
        assertEquals(Duel.Reason.KNOCKOUT, d.outcome().reason());
    }

    @Test
    void leavingThePaintedLinesLoses() {
        Duel d = duel();
        d.setPose(0, 0.5, 26.0, 180);
        d.step();
        assertTrue(d.finished());
        assertEquals(1, d.outcome().winner());
        assertEquals(Duel.Reason.OUT_OF_BOUNDS, d.outcome().reason());

        Duel e = duel();
        e.setPose(1, 20, 17, 0);
        e.step();
        assertEquals(0, e.outcome().winner());
        assertEquals(Duel.Reason.OUT_OF_BOUNDS, e.outcome().reason());
    }

    @Test
    void standingInTheCircleLongEnoughWinsForTheAttacker() {
        Duel d = duel();
        d.setPose(0, 0.5, -19.5, 180);
        d.setPose(1, 0.5, 5.0, 0);
        int ticks = FinaleRules.params(Difficulty.NORMAL).captureTicks();
        for (int i = 1; i < ticks; i++) {
            d.step();
            assertFalse(d.finished(), "tick " + i);
        }
        assertEquals((ticks - 1) / (double) ticks, d.captureProgress(), 1e-9);
        d.step();
        assertTrue(d.finished());
        assertEquals(0, d.outcome().winner());
        assertEquals(Duel.Reason.CAPTURE, d.outcome().reason());
    }

    @Test
    void leavingTheCircleResetsTheCapture() {
        Duel d = duel();
        d.setPose(1, 0.5, 5.0, 0);
        d.setPose(0, 0.5, -19.5, 180);
        int ticks = d.params().captureTicks();
        run(d, ticks * 2 / 3);
        assertTrue(d.captureProgress() > 0.5);
        d.setPose(0, 0.5, -10.0, 180);
        d.step();
        assertEquals(0, d.captureProgress(), 1e-9);
        d.setPose(0, 0.5, -19.5, 180);
        run(d, ticks / 2);
        assertFalse(d.finished(), "the count starts again from zero");
    }

    @Test
    void onlyTheAttackerCanCapture() {
        Duel d = new Duel(FinaleRules.params(Difficulty.NORMAL), SquidShape.court(), Role.DEFENDER, new Rng(1));
        d.setPose(0, 0.5, -19.5, 180);    // slot 0 is the defender here
        d.setPose(1, 0.5, 21.5, 0);
        run(d, 60);
        assertFalse(d.finished(), "the defender may stand in the circle forever");
    }

    @Test
    void timeUpIsADefenderWin() {
        Duel d = duel();
        run(d, d.params().duelTicks() - 1);
        assertFalse(d.finished());
        d.step();
        assertTrue(d.finished());
        assertEquals(1, d.outcome().winner(), "slot 1 is the defender");
        assertEquals(Duel.Reason.TIMEOUT, d.outcome().reason());
        assertEquals(0, d.ticksLeft());
    }

    @Test
    void forcingATimeoutOrAForfeitDecidesImmediately() {
        Duel a = duel();
        a.timeUp();
        assertEquals(Duel.Reason.TIMEOUT, a.outcome().reason());
        assertEquals(1, a.outcome().winner());
        Duel b = duel();
        b.forfeit(1);
        assertEquals(0, b.outcome().winner());
        assertEquals(Duel.Reason.FORFEIT, b.outcome().reason());
        b.forfeit(0);
        assertEquals(0, b.outcome().winner(), "the first decision stands");
    }

    @Test
    void bothKnockedOutInTheSameTickTheOneWithMoreHealthWins() {
        Duel d = duel();
        d.fighter(0).health = -3;
        d.fighter(1).health = -8;
        d.step();
        assertEquals(0, d.outcome().winner());
        Duel e = duel();
        e.fighter(0).health = 0;
        e.fighter(1).health = 0;
        e.step();
        assertTrue(e.finished(), "an exact tie is broken by the (seeded) coin");
        Duel e2 = duel();
        e2.fighter(0).health = 0;
        e2.fighter(1).health = 0;
        e2.step();
        assertEquals(e.outcome().winner(), e2.outcome().winner(), "deterministic for a given seed");
    }

    @Test
    void afterTheDecisionNothingChanges() {
        Duel d = duel();
        d.forfeit(0);
        tap(d, 1);
        assertTrue(d.step().isEmpty());
        assertEquals(100, d.health(0), 1e-9);
    }

    // ------------------------------------------------------------------ determinism

    @Test
    void sameInputsGiveTheSameEvents() {
        List<CombatEvent> a = scripted();
        List<CombatEvent> b = scripted();
        assertEquals(a, b);
        assertFalse(a.isEmpty());
    }

    private static List<CombatEvent> scripted() {
        Duel d = duel(Difficulty.HARD);
        List<CombatEvent> all = new ArrayList<>();
        for (int t = 0; t < 400; t++) {
            if (t % 23 == 0) {
                d.attackDown(0);
            }
            if (t % 23 == 9) {
                d.attackUp(0);
            }
            if (t % 31 == 5) {
                d.guard(1, true);
            }
            if (t % 31 == 17) {
                d.guard(1, false);
            }
            if (t % 47 == 20) {
                d.shove(1);
            }
            if (t % 53 == 30) {
                d.dodge(0, 0, 1);
            }
            all.addAll(d.step());
        }
        return all;
    }

    @Test
    void speedFactorFollowsWhatTheFighterIsDoing() {
        Duel d = duel();
        assertEquals(1.0, d.speedFactor(0), 1e-9);
        d.guard(0, true);
        run(d, 6);
        assertEquals(0.55, d.speedFactor(0), 1e-9, "a raised guard slows the walk");
        d.guard(0, false);
        d.attackDown(1);
        run(d, 8);
        assertEquals(0.55, d.speedFactor(1), 1e-9, "so does a charge");
        d.fighter(1).act = Act.BROKEN;
        assertEquals(0.0, d.speedFactor(1), 1e-9);
    }

    @Test
    void helperTapAndStepIsConsistent() {
        Duel d = duel();
        tapAndStep(d, 0, 6);
        assertEquals(100 - FinaleRules.LIGHT.damage(), d.health(1), 1e-9);
    }
}
