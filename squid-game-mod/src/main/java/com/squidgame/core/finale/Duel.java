package com.squidgame.core.finale;

import com.squidgame.core.util.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.squidgame.core.finale.FinaleRules.*;

/**
 * One duel on the squid court: the complete rule engine, pure and deterministic. The live game feeds it the real
 * positions of the two bodies and their buttons, calls {@link #step()} once per server tick and shows the returned
 * {@link CombatEvent}s; the headless simulator feeds it simulated positions. Humans and NPCs use exactly the same
 * entry points ({@link #attackDown}, {@link #attackUp}, {@link #guard}, {@link #shove}, {@link #dodge}).
 *
 * <p>One tick, in this order: timers run, buffered and new button commands start actions, acts advance (an attack
 * whose wind-up is over lands <i>this</i> tick), all impacts of the tick are resolved simultaneously against the
 * state at the start of the resolution (so two strikes that land together both connect), then guard, stamina and the
 * end conditions are updated: knock-out, out of bounds, the circle held for long enough, time up.
 */
public final class Duel {
    public enum Reason {KNOCKOUT, CAPTURE, OUT_OF_BOUNDS, TIMEOUT, FORFEIT}

    /** The result: {@code winner}/{@code loser} are slots, {@code tick} is when it was decided. */
    public record Outcome(int winner, int loser, Reason reason, int tick) {
    }

    /** Counters of one fighter (results line, balancing). */
    public record Summary(int lights, int heavies, int shoves, int dodges, int hitsLanded, int hitsTaken, int blocked,
                          int parries, int guardBreaks, int whiffs, double damageDealt, double damageTaken,
                          double health, double stamina) {
    }

    private final Params params;
    private final CourtGeometry court;
    private final Rng rng;
    private final Fighter[] f = new Fighter[2];
    private final List<CombatEvent> events = new ArrayList<>();
    private final List<Fighter> landing = new ArrayList<>(2);
    private int tick;
    private int captureTicks;
    private Outcome outcome;

    /**
     * @param roleOfSlot0 the role of fighter 0 (fighter 1 gets the other one)
     * @param rng         only used to break exact ties (both fighters out of the fight in the same tick)
     */
    public Duel(Params params, CourtGeometry court, Role roleOfSlot0, Rng rng) {
        this.params = params;
        this.court = court;
        this.rng = rng;
        f[0] = new Fighter(0, roleOfSlot0, params.staminaMax());
        f[1] = new Fighter(1, roleOfSlot0.other(), params.staminaMax());
        for (Fighter x : f) {
            CourtGeometry.Pt p = x.role == Role.ATTACKER ? court.attackerSpawn() : court.defenderSpawn();
            x.x = p.x();
            x.z = p.z();
        }
        face(f[0], f[1]);
        face(f[1], f[0]);
    }

    private static void face(Fighter a, Fighter b) {
        a.yaw = yawOf(b.x - a.x, b.z - a.z);
    }

    // ================================================================== inputs

    /** Feeds the body's position and view yaw (degrees, Minecraft convention). Call every tick before {@link #step}. */
    public void setPose(int slot, double x, double z, double yawDeg) {
        Fighter x0 = f[slot];
        x0.x = x;
        x0.z = z;
        x0.yaw = yawDeg;
    }

    /** True while the body is sprinting (costs stamina). */
    public void setSprinting(int slot, boolean sprinting) {
        f[slot].sprinting = sprinting;
    }

    /** The attack button went down. */
    public void attackDown(int slot) {
        f[slot].cmdAttackDown = true;
    }

    /** The attack button was released: a tap is a light strike, a long hold a heavy one. */
    public void attackUp(int slot) {
        f[slot].cmdAttackUp = true;
    }

    /** The guard button is held (true) or released (false). Raising the guard cancels a charge in progress (a feint). */
    public void guard(int slot, boolean held) {
        Fighter x = f[slot];
        x.guardHeld = held;
        if (held && x.attackHeld && (x.act == Act.CHARGE || x.act == Act.NONE)) {
            x.act = Act.NONE;
            x.kind = null;
            x.attackHeld = false;
            x.heldTicks = 0;
        }
    }

    /** The shove button was pressed. */
    public void shove(int slot) {
        f[slot].cmdShove = true;
    }

    /** A dodge towards the world direction (dx, dz); a zero vector dodges backwards. */
    public void dodge(int slot, double dx, double dz) {
        Fighter x = f[slot];
        x.cmdDodge = true;
        x.dodgeX = dx;
        x.dodgeZ = dz;
    }

    /** The fighter leaves the fight (disconnect, administrator): the other one wins at once. */
    public void forfeit(int slot) {
        if (outcome == null) {
            decide(1 - slot, slot, Reason.FORFEIT);
        }
    }

    /** Ends the duel now as a timeout: the defender wins (used when the whole game runs out of time). */
    public void timeUp() {
        if (outcome == null) {
            Fighter d = f[0].role == Role.DEFENDER ? f[0] : f[1];
            decide(d.slot, 1 - d.slot, Reason.TIMEOUT);
        }
    }

    // ================================================================== queries

    public Params params() {
        return params;
    }

    public CourtGeometry court() {
        return court;
    }

    public int tick() {
        return tick;
    }

    public int ticksLeft() {
        return Math.max(0, params.duelTicks() - tick);
    }

    public boolean finished() {
        return outcome != null;
    }

    public Outcome outcome() {
        return outcome;
    }

    public Role role(int slot) {
        return f[slot].role;
    }

    /** The slot holding the given role. */
    public int slotOf(Role role) {
        return f[0].role == role ? 0 : 1;
    }

    /** Progress of the attacker's capture of the circle, 0..1. */
    public double captureProgress() {
        return Math.min(1.0, captureTicks / (double) params.captureTicks());
    }

    public double health(int slot) {
        return f[slot].health;
    }

    public double stamina(int slot) {
        return f[slot].stamina;
    }

    /** Velocity (blocks per tick) the world must still give this fighter's body for its dash, or null when not dashing. */
    public double[] dash(int slot) {
        Fighter x = f[slot];
        return x.dashLeft > 0 ? new double[]{x.dashX, x.dashZ} : null;
    }

    /** The mutable fighter (tests arrange situations through it). */
    Fighter fighter(int slot) {
        return f[slot];
    }

    public FighterView view(int slot) {
        Fighter x = f[slot];
        boolean windingUp = (x.act.isAttack() && !x.impacted) || x.act == Act.CHARGE;
        boolean recovering = x.act.isAttack() && x.impacted;
        return new FighterView(slot, x.role, x.x, x.z, x.yaw, x.health, x.stamina, x.act, x.actTick, windingUp,
                recovering, x.act == Act.CHARGE ? x.heldTicks : 0, guardUp(x), x.guardHeld, x.iframes > 0,
                x.stunLeft, x.exhausted, x.dodgeCooldown == 0, x.sprinting);
    }

    /** Multiplier on the walking speed implied by what the fighter is doing (the world applies it to the body). */
    public double speedFactor(int slot) {
        Fighter x = f[slot];
        double m = switch (x.act) {
            case NONE -> guardUp(x) ? 0.55 : x.guardHeld ? 0.8 : 1.0;
            case CHARGE -> 0.55;
            case LIGHT -> x.impacted ? 0.6 : 0.75;
            case HEAVY -> 0.35;
            case SHOVE -> x.impacted ? 0.45 : 0.5;
            case DODGE -> 1.0;
            case STUN -> 0.1;
            case BROKEN -> 0.0;
        };
        return x.exhausted ? m * 0.75 : m;
    }

    /** True when sprinting is possible and pays off right now (no action, no guard, stamina left). */
    public boolean canSprint(int slot) {
        Fighter x = f[slot];
        return x.act == Act.NONE && !x.guardHeld && !x.exhausted && x.stamina > 1;
    }

    public Summary summary(int slot) {
        Fighter x = f[slot];
        Fighter.Stats s = x.stats;
        return new Summary(s.lights, s.heavies, s.shoves, s.dodges, s.hitsLanded, s.hitsTaken, s.blocked, s.parries,
                s.guardBreaks, s.whiffs, s.damageDealt, s.damageTaken, x.health, x.stamina);
    }

    private boolean guardUp(Fighter x) {
        return x.guardTicks >= GUARD_RAISE;
    }

    // ================================================================== the tick

    /** Advances the duel by one tick and returns what happened (the list is reused: copy it to keep it). */
    public List<CombatEvent> step() {
        events.clear();
        if (outcome != null) {
            return events;
        }
        tick++;
        for (Fighter x : f) {
            timers(x);
        }
        for (Fighter x : f) {
            commands(x);
        }
        landing.clear();
        for (Fighter x : f) {
            advance(x);
        }
        resolveImpacts();
        for (Fighter x : f) {
            upkeep(x);
        }
        judge();
        return events;
    }

    private void emit(CombatEvent.Type type, int actor, int target, ActionKind kind, double amount, double ix, double iz) {
        events.add(new CombatEvent(type, actor, target, kind, amount, ix, iz));
    }

    private void timers(Fighter x) {
        if (x.stunLeft > 0 && --x.stunLeft == 0 && x.act.isStagger()) {
            x.act = Act.NONE;
            x.kind = null;
            x.grace = STUN_GRACE;
        }
        if (x.grace > 0 && !x.act.isStagger()) {
            x.grace--;
        }
        if (x.iframes > 0) {
            x.iframes--;
        }
        if (x.dodgeCooldown > 0) {
            x.dodgeCooldown--;
        }
        if (x.bufAttack > 0 && --x.bufAttack == 0) {
            x.bufAttackUp = false;
        }
        if (x.bufShove > 0) {
            x.bufShove--;
        }
        if (x.dashLeft > 0) {
            x.dashLeft--;
        }
        x.sinceHit++;
        if (x.attackHeld) {
            x.heldTicks++;
        }
    }

    // ------------------------------------------------------------------ commands

    private void commands(Fighter x) {
        if (x.cmdDodge) {
            x.cmdDodge = false;
            tryDodge(x);
        }
        if (x.cmdShove) {
            x.cmdShove = false;
            if (x.act == Act.NONE) {
                tryShove(x);
            } else {
                x.bufShove = INPUT_BUFFER;
            }
        } else if (x.bufShove > 0 && x.act == Act.NONE) {
            x.bufShove = 0;
            tryShove(x);
        }
        if (x.cmdAttackDown) {
            x.cmdAttackDown = false;
            pressAttack(x);
        } else if (x.bufAttack > 0 && x.act == Act.NONE && !x.attackHeld) {
            x.bufAttack = 0;
            if (x.exhausted) {
                x.bufAttackUp = false;
                deny(x);
            } else {
                beginHold(x);
                if (x.bufAttackUp) {
                    x.bufAttackUp = false;
                    releaseAttack(x);
                }
            }
        }
        if (x.cmdAttackUp) {
            x.cmdAttackUp = false;
            releaseAttack(x);
        }
        if (x.attackHeld) {
            if (x.act == Act.NONE && x.heldTicks >= CHARGE_SHOW) {
                x.act = Act.CHARGE;
                x.kind = ActionKind.HEAVY;
                emit(CombatEvent.Type.CHARGE_START, x.slot, -1, ActionKind.HEAVY, 0, 0, 0);
            }
            if (x.heldTicks >= HEAVY_MAX_HOLD) {
                releaseAttack(x);
            }
        }
    }

    private void pressAttack(Fighter x) {
        if (x.attackHeld) {
            return;
        }
        if (x.act != Act.NONE) {
            x.bufAttack = INPUT_BUFFER;
            x.bufAttackUp = false;
            return;
        }
        if (x.exhausted) {
            deny(x);
            return;
        }
        beginHold(x);
    }

    private void beginHold(Fighter x) {
        x.attackHeld = true;
        x.heldTicks = 0;
        x.guardTicks = 0;
    }

    private void releaseAttack(Fighter x) {
        if (!x.attackHeld) {
            if (x.bufAttack > 0) {
                x.bufAttackUp = true;
            }
            return;
        }
        x.attackHeld = false;
        int held = x.heldTicks;
        x.heldTicks = 0;
        if (x.act == Act.CHARGE) {
            x.act = Act.NONE;
            x.kind = null;
        }
        if (x.act != Act.NONE) {
            return;
        }
        boolean heavy = held >= HEAVY_MIN_HOLD;
        if (heavy && x.stamina >= HEAVY.cost()) {
            startHeavy(x, held);
        } else if (x.stamina >= LIGHT.cost()) {
            startLight(x, heavy ? LIGHT.windup() : held);
        } else {
            deny(x);
        }
    }

    private void startLight(Fighter x, int held) {
        spend(x, LIGHT.cost());
        beginAct(x, Act.LIGHT, ActionKind.LIGHT, LIGHT.windup(), LIGHT.recovery());
        // the swing counts from the press: a tap released after 2 ticks lands 2 ticks later
        x.actTick = Math.min(held - 1, LIGHT.windup() - 1);
        x.stats.lights++;
        emit(CombatEvent.Type.SWING, x.slot, -1, ActionKind.LIGHT, 0, 0, 0);
    }

    private void startHeavy(Fighter x, int held) {
        x.power = heavyPower(held);
        spend(x, HEAVY.cost());
        beginAct(x, Act.HEAVY, ActionKind.HEAVY, HEAVY.windup(), HEAVY.recovery());
        x.stats.heavies++;
        emit(CombatEvent.Type.SWING, x.slot, -1, ActionKind.HEAVY, x.power, 0, 0);
    }

    private void tryShove(Fighter x) {
        if (x.exhausted || x.stamina < SHOVE.cost()) {
            deny(x);
            return;
        }
        spend(x, SHOVE.cost());
        x.attackHeld = false;
        x.heldTicks = 0;
        beginAct(x, Act.SHOVE, ActionKind.SHOVE, SHOVE.windup(), SHOVE.recovery());
        x.stats.shoves++;
        emit(CombatEvent.Type.SWING, x.slot, -1, ActionKind.SHOVE, 0, 0, 0);
    }

    private void tryDodge(Fighter x) {
        boolean windupOnly = x.act.isAttack() && !x.impacted;
        if (!(x.act == Act.NONE || x.act == Act.CHARGE || windupOnly) || x.dodgeCooldown > 0) {
            return;
        }
        if (x.exhausted || x.stamina < DODGE_COST) {
            deny(x);
            return;
        }
        spend(x, DODGE_COST);
        x.attackHeld = false;
        x.heldTicks = 0;
        x.bufAttack = 0;
        beginAct(x, Act.DODGE, ActionKind.DODGE, Integer.MAX_VALUE, 0);
        x.impacted = true;
        x.endAt = DODGE_LENGTH;
        x.iframes = params.dodgeIframes();
        x.dodgeCooldown = DODGE_COOLDOWN;
        x.dashLeft = DASH_TICKS;
        double dx = x.dodgeX, dz = x.dodgeZ;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            dx = -forwardX(x.yaw);
            dz = -forwardZ(x.yaw);
            len = 1;
        }
        x.dashX = dx / len * DASH_SPEED;
        x.dashZ = dz / len * DASH_SPEED;
        x.stats.dodges++;
        emit(CombatEvent.Type.DODGE, x.slot, -1, ActionKind.DODGE, DASH_TICKS, x.dashX, x.dashZ);
    }

    private void beginAct(Fighter x, Act act, ActionKind kind, int windup, int recovery) {
        x.act = act;
        x.kind = kind;
        x.actTick = -1;
        x.impactAt = windup;
        x.endAt = windup == Integer.MAX_VALUE ? 0 : windup + recovery;
        x.impacted = false;
        x.guardTicks = 0;
    }

    private void deny(Fighter x) {
        x.stats.denied++;
        emit(CombatEvent.Type.DENIED, x.slot, -1, null, 0, 0, 0);
    }

    private void spend(Fighter x, double cost) {
        x.stamina -= cost;
        x.regenDelayLeft = params.regenDelay();
        if (x.stamina <= 0) {
            x.stamina = 0;
            becomeExhausted(x);
        }
    }

    private void becomeExhausted(Fighter x) {
        if (!x.exhausted) {
            x.exhausted = true;
            emit(CombatEvent.Type.EXHAUSTED, x.slot, -1, null, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ acts

    private void advance(Fighter x) {
        if (x.act == Act.NONE || x.act == Act.CHARGE || x.act.isStagger()) {
            return;
        }
        x.actTick++;
        if (x.act.isAttack() && !x.impacted && x.actTick >= x.impactAt) {
            x.impacted = true;
            landing.add(x);
        }
        if (x.actTick >= x.endAt && x.impacted) {
            x.act = Act.NONE;
            x.kind = null;
        }
    }

    // ------------------------------------------------------------------ impacts

    private enum Result {WHIFF, DODGED, PARRIED, BLOCKED, GUARD_BREAK, HIT}

    /**
     * The outcome of one landing strike, computed before anything is applied. The kind of the strike is part of it:
     * when two strikes land in the same tick the first one applied interrupts the other fighter's action (and clears it).
     */
    private record Resolution(Fighter att, Fighter def, ActionKind kind, Result result, double damage, double drain,
                              double ix, double iz, boolean counter) {
    }

    private void resolveImpacts() {
        if (landing.isEmpty()) {
            return;
        }
        List<Resolution> res = new ArrayList<>(2);
        for (Fighter a : landing) {
            res.add(evaluate(a, f[1 - a.slot]));
        }
        for (Resolution r : res) {
            apply(r);
        }
    }

    private Resolution evaluate(Fighter a, Fighter d) {
        double damage, knock, drain;
        double reach, cone;
        switch (a.kind) {
            case LIGHT -> {
                damage = LIGHT.damage();
                knock = LIGHT.knockback();
                drain = LIGHT.blockDrain();
                reach = LIGHT.reach();
                cone = LIGHT.cone();
            }
            case HEAVY -> {
                damage = heavyDamage(a.power);
                knock = heavyKnockback(a.power);
                drain = heavyBlockDrain(a.power);
                reach = HEAVY.reach();
                cone = HEAVY.cone();
            }
            default -> {
                damage = SHOVE.damage();
                knock = SHOVE.knockback();
                drain = SHOVE.blockDrain();
                reach = SHOVE.reach();
                cone = SHOVE.cone();
            }
        }
        damage *= params.damageScale();
        double dx = d.x - a.x, dz = d.z - a.z;
        double dist = Math.hypot(dx, dz);
        boolean inCone = dist < 0.4 || Math.abs(angleDiff(a.yaw, yawOf(dx, dz))) <= cone;
        if (dist > reach || !inCone) {
            return new Resolution(a, d, a.kind, Result.WHIFF, 0, 0, 0, 0, false);
        }
        double nx = dist < 1e-6 ? forwardX(a.yaw) : dx / dist;
        double nz = dist < 1e-6 ? forwardZ(a.yaw) : dz / dist;
        if (d.iframes > 0) {
            return new Resolution(a, d, a.kind, Result.DODGED, 0, 0, 0, 0, false);
        }
        boolean guarding = d.act == Act.NONE && guardUp(d)
                && Math.abs(angleDiff(d.yaw, yawOf(-nx, -nz))) <= GUARD_CONE;
        if (guarding) {
            if (d.guardTicks - GUARD_RAISE < params.parryWindow()) {
                return new Resolution(a, d, a.kind, Result.PARRIED, 0, 0, -nx * 0.25, -nz * 0.25, false);
            }
            if (d.stamina - drain <= 0) {
                return new Resolution(a, d, a.kind, Result.GUARD_BREAK, damage, drain, nx * knock, nz * knock, false);
            }
            double kb = knock * (a.kind == ActionKind.SHOVE ? BLOCK_KB_SHOVE : BLOCK_KB_STRIKE);
            return new Resolution(a, d, a.kind, Result.BLOCKED, damage * BLOCK_KEEPS, drain, nx * kb, nz * kb, false);
        }
        boolean counter = (d.act.isAttack() && !d.impacted) || d.act == Act.CHARGE;
        return new Resolution(a, d, a.kind, Result.HIT, damage * (counter ? COUNTER_BONUS : 1.0), drain, nx * knock, nz * knock, counter);
    }

    private void apply(Resolution r) {
        Fighter a = r.att(), d = r.def();
        ActionKind kind = r.kind();
        Spec spec = kind == ActionKind.LIGHT ? LIGHT : kind == ActionKind.HEAVY ? HEAVY : SHOVE;
        switch (r.result()) {
            case WHIFF -> {
                a.endAt += spec.whiff();
                a.stats.whiffs++;
                emit(CombatEvent.Type.WHIFF, a.slot, d.slot, kind, 0, 0, 0);
            }
            case DODGED -> {
                a.endAt += spec.whiff();
                d.stamina = Math.min(params.staminaMax(), d.stamina + DODGE_REFUND);
                emit(CombatEvent.Type.DODGED, a.slot, d.slot, kind, 0, 0, 0);
            }
            case PARRIED -> {
                d.stats.parries++;
                d.stamina = Math.min(params.staminaMax(), d.stamina + PARRY_REWARD);
                a.stamina = Math.max(0, a.stamina - 6);
                stagger(a, Act.BROKEN, PARRIED_STUN);
                emit(CombatEvent.Type.PARRIED, a.slot, d.slot, kind, 0, r.ix(), r.iz());
            }
            case BLOCKED -> {
                d.stats.blocked++;
                d.stamina -= r.drain();
                d.regenDelayLeft = params.regenDelay();
                damage(a, d, r.damage());
                emit(CombatEvent.Type.BLOCKED, a.slot, d.slot, kind, r.damage(), r.ix(), r.iz());
            }
            case GUARD_BREAK -> {
                d.stats.guardBreaks++;
                d.stamina = 0;
                becomeExhausted(d);
                d.regenDelayLeft = params.regenDelay();
                damage(a, d, r.damage());
                stagger(d, Act.BROKEN, BROKEN_STUN);
                emit(CombatEvent.Type.GUARD_BREAK, a.slot, d.slot, kind, r.damage(), r.ix(), r.iz());
            }
            case HIT -> {
                a.stamina = Math.min(params.staminaMax(), a.stamina + HIT_REWARD);
                damage(a, d, r.damage());
                if (d.grace == 0) {
                    int combo = d.sinceHit < COMBO_WINDOW ? d.combo + 1 : 1;
                    double shorten = Math.max(0.4, 1.0 - 0.2 * (combo - 1));
                    int stun = (int) Math.round(spec.stun() * (r.counter() ? 1.3 : 1.0) * shorten);
                    d.combo = combo;
                    stagger(d, Act.STUN, Math.max(2, stun));
                }
                d.sinceHit = 0;
                emit(CombatEvent.Type.HIT, a.slot, d.slot, kind, r.damage(), r.ix(), r.iz());
            }
        }
    }

    private void damage(Fighter a, Fighter d, double amount) {
        d.health -= amount;
        d.stats.hitsTaken++;
        d.stats.damageTaken += amount;
        a.stats.hitsLanded++;
        a.stats.damageDealt += amount;
    }

    /** Interrupts whatever the fighter was doing and staggers it. */
    private void stagger(Fighter x, Act act, int ticks) {
        x.act = act;
        x.kind = null;
        x.stunLeft = ticks;
        x.attackHeld = false;
        x.heldTicks = 0;
        x.bufAttack = 0;
        x.bufShove = 0;
        x.guardTicks = 0;
        x.impacted = false;
    }

    // ------------------------------------------------------------------ end of tick

    private void upkeep(Fighter x) {
        boolean wasUp = guardUp(x);
        if (x.act == Act.NONE && x.guardHeld && !x.attackHeld) {
            x.guardTicks++;
        } else {
            x.guardTicks = 0;
        }
        boolean nowUp = guardUp(x);
        if (nowUp != wasUp) {
            emit(nowUp ? CombatEvent.Type.GUARD_UP : CombatEvent.Type.GUARD_DOWN, x.slot, -1, null, 0, 0, 0);
        }
        if (x.act == Act.CHARGE) {
            x.stamina -= CHARGE_DRAIN;
            x.regenDelayLeft = params.regenDelay();
            if (x.stamina <= 0) {
                x.stamina = 0;
                becomeExhausted(x);
                x.act = Act.NONE;
                x.kind = null;
                x.attackHeld = false;
                x.heldTicks = 0;
            }
        } else if (x.sprinting && canSprint(x.slot)) {
            x.stamina -= SPRINT_DRAIN;
            x.regenDelayLeft = Math.max(x.regenDelayLeft, params.regenDelay() / 2);
            if (x.stamina <= 0) {
                x.stamina = 0;
                becomeExhausted(x);
            }
        }
        if (x.regenDelayLeft > 0) {
            x.regenDelayLeft--;
        } else if (x.stamina < params.staminaMax()) {
            double rate = params.staminaRegen() * (x.guardHeld ? GUARD_REGEN : 1.0) * (x.act == Act.NONE ? 1.0 : 0.6);
            x.stamina = Math.min(params.staminaMax(), x.stamina + rate);
        }
        if (x.exhausted && x.stamina >= EXHAUST_RECOVER) {
            x.exhausted = false;
            emit(CombatEvent.Type.RECOVERED, x.slot, -1, null, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ the verdict

    private void judge() {
        Reason[] out = new Reason[2];
        for (Fighter x : f) {
            if (x.health <= 0) {
                out[x.slot] = Reason.KNOCKOUT;
            } else if (!court.isIn(x.x, x.z)) {
                out[x.slot] = Reason.OUT_OF_BOUNDS;
            }
        }
        if (out[0] != null || out[1] != null) {
            int loser;
            if (out[0] != null && out[1] != null) {
                loser = tieBreakLoser();
            } else {
                loser = out[0] != null ? 0 : 1;
            }
            decide(1 - loser, loser, out[loser]);
            return;
        }
        Fighter att = f[slotOf(Role.ATTACKER)];
        captureTicks = court.inCircle(att.x, att.z) ? captureTicks + 1 : 0;
        if (captureTicks >= params.captureTicks()) {
            decide(att.slot, 1 - att.slot, Reason.CAPTURE);
        } else if (tick >= params.duelTicks()) {
            int d = slotOf(Role.DEFENDER);
            decide(d, 1 - d, Reason.TIMEOUT);
        }
    }

    /** Both fighters are out of the fight in the same tick: more health wins, then the deeper position, then luck. */
    private int tieBreakLoser() {
        if (f[0].health != f[1].health) {
            return f[0].health < f[1].health ? 0 : 1;
        }
        double e0 = court.edgeDistance(f[0].x, f[0].z), e1 = court.edgeDistance(f[1].x, f[1].z);
        if (e0 != e1) {
            return e0 < e1 ? 0 : 1;
        }
        return rng.nextBoolean() ? 0 : 1;
    }

    private void decide(int winner, int loser, Reason reason) {
        outcome = new Outcome(winner, loser, reason, tick);
    }
}
