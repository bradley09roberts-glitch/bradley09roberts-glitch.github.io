package com.squidgame.core.finale;

import com.squidgame.core.Difficulty;
import com.squidgame.core.Personality;
import com.squidgame.core.finale.CourtGeometry.Pt;
import com.squidgame.core.util.Rng;

import static com.squidgame.core.finale.FinaleRules.*;

/**
 * The mind of an NPC finalist. Pure: every tick it is shown the duel and answers with an {@link Intent} - where to
 * walk and look and which buttons to press, exactly the controls a human has. It only looks at
 * {@link FighterView}s (what a player sees on screen and in the HUD bars) and it perceives <i>changes</i> of the
 * opponent's state (a wind-up begins, a guard goes up) only after its personal reaction delay, so wind-ups can be
 * answered by fast fighters, light strikes cannot, and feints and baits work on slow ones.
 *
 * <h2>How a fight is played</h2>
 * Far from the opponent the attacker walks the course (neck, then circle) and the defender holds its post. In range
 * the NPC plays short <b>modes</b> chosen by personality: a {@link Mode#POKE} burst of light strikes, a
 * {@link Mode#BAIT} (hovering just outside the opponent's reach until it commits and whiffs), a {@link Mode#GUARD}
 * advance behind a raised guard that ends in a shove or a heavy strike, a {@link Mode#HEAVY} set-up, and a
 * {@link Mode#RESET} (back off, regain stamina) after an exchange or when tired. On top of that it always reacts to
 * telegraphs (dodge a heavy strike or shove if fast enough, poke a charging opponent, guard), punishes openings and
 * shoves whenever that pushes the opponent over the line.
 *
 * <h2>How personality shows</h2>
 * aggression sets tempo, burst length and how early a defender leaves its post; courage the stamina and health at
 * which it backs off and whether it holds the neck; patience how long it baits and guards; skill (with the difficulty
 * bonus) the quality of its answers, range estimates and use of the line; risk tolerance heavy strikes and sprinting;
 * reaction speed the perception delay.
 */
public final class NpcBrain {
    /** What the NPC wants this tick. {@code speed}: 0 = stand, 1 = walk, 1.3 = sprint (times the state's speed factor). */
    public record Intent(double dirX, double dirZ, double speed, double yaw, boolean guard, boolean attackHeld,
                         boolean shove, boolean dodge, double dodgeX, double dodgeZ) {
    }

    private enum Mode {APPROACH, POKE, BAIT, GUARD, HEAVY, RESET}

    /** Where a defender waits: beyond the neck's mouth, at the circle's entrance, or inside the goal itself. */
    private enum Post {NECK, MOUTH, KEEPER}

    private static final int HISTORY = 32;
    /** Distance from the neck's centre to the forward defender's post (just beyond the neck's mouth). */
    private static final double NECK_REACH = 5.2;

    private final int slot;
    private final CourtGeometry court;
    private final Rng rng;
    private final Params params;

    private final double skill, aggression, courage, patience, risk;
    private final int reaction;
    private final double turnRate;
    private final double retreatStamina, retreatHealth;
    private final double engage;
    private final Post postKind;
    private final double postJitter;
    private final double sprintBias;

    private final FighterView[] foeHistory = new FighterView[HISTORY];

    private Mode mode = Mode.APPROACH;
    private int modeUntil;
    private int pokesLeft;
    private int cooldownUntil;
    private int guardUntil;
    private int strafeUntil;
    private int strafeSign = 1;
    private boolean sprintNow;
    private int sprintUntil;
    private boolean holding;
    private int releaseAt;
    private int reactedTo = Integer.MIN_VALUE;
    private double lastDist = -1;
    private int lastExchange;
    private double lastHealth = HEALTH_MAX, lastFoeHealth = HEALTH_MAX;

    public NpcBrain(int slot, Personality p, Difficulty difficulty, Params params, CourtGeometry court, Rng rng) {
        this.slot = slot;
        this.court = court;
        this.rng = rng;
        this.params = params;
        this.skill = p.effectiveSkill(difficulty);
        this.aggression = p.aggression();
        this.courage = p.courage();
        this.patience = p.patience();
        this.risk = p.riskTolerance();
        this.reaction = p.reactionDelayTicks(rng, difficulty);
        this.turnRate = Rng.lerp(13, 26, skill);
        this.retreatStamina = 18 + 26 * (1 - courage);
        this.retreatHealth = 8 + 30 * (1 - courage) * (1 - 0.5 * aggression);
        this.engage = LIGHT.reach() - 0.45 - 0.35 * (1 - skill) * rng.nextDouble();
        double bold = 0.55 * aggression + 0.45 * courage + 0.25 * (rng.nextDouble() - 0.5);
        this.postKind = bold > 0.62 ? Post.NECK : (courage < 0.5 || aggression < 0.3) ? Post.KEEPER : Post.MOUTH;
        this.postJitter = rng.nextDouble();
        this.sprintBias = 0.30 + 0.55 * (0.5 * risk + 0.5 * aggression);
    }

    /** Ticks this NPC needs to notice a change in its opponent's state. */
    public int reactionTicks() {
        return reaction;
    }

    // ================================================================== one decision

    /** Everything the NPC knows this tick. */
    private final class Frame {
        final int now;
        final FighterView me, foe, seen;
        final Role role;
        final double dist, ux, uz, yawTo, edge, timeFrac;

        Frame(Duel duel) {
            this.now = duel.tick();
            this.me = duel.view(slot);
            this.foe = duel.view(1 - slot);
            this.role = me.role();
            foeHistory[now & (HISTORY - 1)] = foe;
            FighterView old = now >= reaction ? foeHistory[(now - reaction) & (HISTORY - 1)] : null;
            this.seen = old != null ? old : foe;
            double dx = foe.x() - me.x(), dz = foe.z() - me.z();
            this.dist = Math.max(1e-6, Math.hypot(dx, dz));
            this.ux = dx / dist;
            this.uz = dz / dist;
            this.yawTo = yawOf(dx, dz);
            this.edge = court.edgeDistance(me.x(), me.z());
            this.timeFrac = duel.ticksLeft() / (double) params.duelTicks();
        }

        boolean attacker() {
            return role == Role.ATTACKER;
        }

        boolean free() {
            return me.act() == Act.NONE && !me.exhausted() && !holding;
        }

        double stamina() {
            return me.stamina();
        }

        double facingError() {
            return Math.abs(angleDiff(me.yaw(), yawTo));
        }

        /** Positive when we are closing in on the opponent (blocks per tick). */
        double closing() {
            return lastDist < 0 ? 0 : lastDist - dist;
        }

        boolean tired() {
            return me.stamina() < retreatStamina || me.exhausted();
        }

        /** The attacker is in, or about to be in, the circle: a defender must stop it now, whatever it costs. */
        boolean captureThreat() {
            return !attacker() && !foe.helpless() && court.circleDistance(foe.x(), foe.z()) < court.captureRadius() + 2.5;
        }

        boolean losing() {
            return me.health() < retreatHealth && foe.health() > me.health();
        }
    }

    /** The answer being built. */
    private static final class Plan {
        boolean guard, attackHeld, shove, dodge;
        double dodgeX, dodgeZ;
        double dirX, dirZ, speed;
        boolean moveSet;
    }

    public Intent think(Duel duel) {
        Frame f = new Frame(duel);
        Plan p = new Plan();
        noteDamage(f);
        if (f.me.act().isStagger()) {
            holding = false;
            lastDist = f.dist;
            return finish(f, p);
        }
        if (holding) {
            continueCharge(f, p);
        }
        defend(f, p);
        if (!p.dodge && f.free()) {
            opportunism(f, p);
        }
        if (!p.dodge && !p.shove && !p.attackHeld && !holding) {
            fight(f, p);
        }
        lastDist = f.dist;
        return finish(f, p);
    }

    private void noteDamage(Frame f) {
        if (f.me.health() < lastHealth - 0.5 || f.foe.health() < lastFoeHealth - 0.5) {
            lastExchange = f.now;
        }
        lastHealth = f.me.health();
        lastFoeHealth = f.foe.health();
    }

    /** 0..1: how overdue a decision is - nobody has been hurt for a while, or (attacker) the clock is running out. */
    private double urgency(Frame f) {
        double stall = Rng.clamp01((f.now - lastExchange - 140) / 160.0);
        double clock = f.attacker() ? Rng.clamp01((0.55 - f.timeFrac) / 0.55) : 0;
        return Math.max(stall, clock);
    }

    private Intent finish(Frame f, Plan p) {
        if (f.now < guardUntil && !holding && !p.attackHeld && !p.shove && f.me.act() == Act.NONE) {
            p.guard = true;
        }
        double yaw = faceYaw(f);
        double dx = p.dirX, dz = p.dirZ;
        if (p.moveSet) {
            double[] safe = safeDir(f, dx, dz);
            dx = safe[0];
            dz = safe[1];
        }
        double speed = p.moveSet ? p.speed : 0;
        if (p.guard && speed > 1.0) {
            speed = 1.0;
        }
        return new Intent(dx, dz, speed, yaw, p.guard, p.attackHeld, p.shove, p.dodge, p.dodgeX, p.dodgeZ);
    }

    private double faceYaw(Frame f) {
        double target = f.yawTo;
        if (f.attacker() && f.dist > 9 && !f.foe.helpless()) {
            Pt goal = courseTarget(f);
            target = yawOf(goal.x() - f.me.x(), goal.z() - f.me.z());
        }
        double step = Math.max(-turnRate, Math.min(turnRate, angleDiff(f.me.yaw(), target)));
        if (f.me.act() == Act.HEAVY && f.me.windingUp()) {
            step *= 0.3;
        }
        return f.me.yaw() + step;
    }

    // ================================================================== reactions

    private void defend(Frame f, Plan p) {
        FighterView seen = f.seen;
        Act a = seen.act();
        boolean swing = a.isAttack() && seen.windingUp();
        boolean charge = a == Act.CHARGE;
        double foeReach = a == Act.SHOVE ? SHOVE.reach() : (a == Act.HEAVY || charge) ? HEAVY.reach() : LIGHT.reach();
        if ((swing || charge) && f.dist <= foeReach + 1.2) {
            int id = (f.now - reaction) - (charge ? seen.charge() : seen.actTick());
            if (id != reactedTo) {
                reactedTo = id;
                answerTelegraph(f, p, a, charge);
            }
        }
    }

    private void answerTelegraph(Frame f, Plan p, Act a, boolean charge) {
        boolean heavyOrShove = a == Act.HEAVY || a == Act.SHOVE;
        double acuity = 0.20 + 0.70 * skill;
        if (heavyOrShove && f.me.dodgeReady() && f.stamina() >= DODGE_COST + 4 && !f.me.exhausted()
                && f.me.act() != Act.DODGE && rng.chance(acuity * (0.65 + 0.35 * courage))) {
            sidestep(f, p);
            return;
        }
        if (charge) {
            // the opponent is charging a heavy strike: poke it, back off, or turtle - depending on nerve
            double poke = (0.20 + 0.55 * aggression) * (0.5 + 0.5 * skill);
            if (f.dist <= LIGHT.reach() + 0.3 && f.free() && rng.chance(poke)) {
                p.attackHeld = true;      // a tap: the light strike that interrupts the charge
                cooldownUntil = f.now + 8;
                return;
            }
            double back = (1 - courage) * 0.5 + 0.25 * skill;
            if (rng.chance(back) && f.dist < HEAVY.reach() + 0.8) {
                enter(f, Mode.RESET, 12 + rng.nextInt(10));
                return;
            }
            if (rng.chance(0.35 + 0.5 * skill)) {
                guardUntil = f.now + 14;
            }
            return;
        }
        if (rng.chance(0.25 + 0.55 * skill * (1.0 - 0.5 * aggression))) {
            guardUntil = f.now + 12 + (int) (patience * 10);
        }
    }

    /** Dashes sideways (towards the side with more room), or back when the court is too narrow. */
    private void sidestep(Frame f, Plan p) {
        double tx = -f.uz, tz = f.ux;
        double left = court.rayExit(f.me.x(), f.me.z(), tx, tz, 4.0);
        double right = court.rayExit(f.me.x(), f.me.z(), -tx, -tz, 4.0);
        double need = DASH_SPEED * DASH_TICKS + 0.8;
        double sx, sz;
        // an attacker dashes past the defender towards the goal; a defender keeps its distance
        Pt goal = courseTarget(f);
        double gl = Math.max(1e-6, Math.hypot(goal.x() - f.me.x(), goal.z() - f.me.z()));
        double gx = (goal.x() - f.me.x()) / gl, gz = (goal.z() - f.me.z()) / gl;
        double forward = f.attacker() ? 0.9 : -0.3;
        if (Math.max(left, right) < need) {
            sx = f.attacker() ? gx : -f.ux;
            sz = f.attacker() ? gz : -f.uz;
        } else if (left >= right) {
            sx = tx + forward * (f.attacker() ? gx : f.ux);
            sz = tz + forward * (f.attacker() ? gz : f.uz);
        } else {
            sx = -tx + forward * (f.attacker() ? gx : f.ux);
            sz = -tz + forward * (f.attacker() ? gz : f.uz);
        }
        p.dodge = true;
        p.dodgeX = sx;
        p.dodgeZ = sz;
        holding = false;
        enter(f, Mode.RESET, 10);
    }

    // ================================================================== opportunism

    /** Things worth doing at once whatever the current mode: the shove that ends it, the strike at an opening. */
    private void opportunism(Frame f, Plan p) {
        if (f.now < cooldownUntil) {
            return;
        }
        FighterView foe = f.foe;
        boolean aligned = f.facingError() <= LIGHT.cone() - 14;
        boolean inShove = f.dist <= SHOVE.reach() - 0.15 && f.facingError() <= SHOVE.cone() - 12;
        double pushRoom = court.rayExit(foe.x(), foe.z(), f.ux, f.uz, 6.0);
        double shoveTravel = travel(SHOVE.knockback()) * (foe.guardUp() ? BLOCK_KB_SHOVE : 1.0);
        boolean lethal = inShove && pushRoom <= shoveTravel - OUT_MARGIN + 0.35 && !foe.invulnerable();
        if (lethal && f.stamina() >= SHOVE.cost() && rng.chance(0.25 + 0.40 * skill)) {
            p.shove = true;
            cooldownUntil = f.now + SHOVE.windup() + SHOVE.recovery() / 2;
            return;
        }
        boolean inLight = estimate(f) <= LIGHT.reach() - 0.25 && aligned;
        // something is running at us: meet it with a strike or a shove thrown ahead of it
        if (f.closing() > 0.12 && !foe.helpless() && f.stamina() >= SHOVE.cost() + 4 && f.dist < 5.0) {
            if (lead(f, SHOVE.windup()) <= SHOVE.reach() - 0.2 && f.facingError() <= SHOVE.cone() - 12
                    && rng.chance(0.10 + 0.30 * skill)) {
                p.shove = true;
                cooldownUntil = f.now + SHOVE.windup() + SHOVE.recovery() / 2;
                return;
            }
            if (inLight && rng.chance(0.12 + 0.26 * skill)) {
                strike(f, p);
                return;
            }
        }
        boolean opening = f.seen.helpless() && !foe.guardUp() && foe.helpless();
        if (opening && (inLight || inShove) && !f.tired()) {
            boolean heavyPunish = foe.act() == Act.BROKEN && foe.stunLeft() >= 12 && f.stamina() >= HEAVY.cost() + 12
                    && f.dist <= HEAVY.reach() - 0.3;
            if (heavyPunish) {
                startHeavy(f, p, HEAVY_MIN_HOLD + 1);
            } else if (inLight && rng.chance(0.45 + 0.5 * skill)) {
                strike(f, p);
            } else if (inShove && f.stamina() >= SHOVE.cost() + 8) {
                p.shove = true;
                cooldownUntil = f.now + 14;
            }
        }
    }

    /** Distance at which a strike thrown now would land: skilled fighters lead a moving opponent. */
    private double estimate(Frame f) {
        return lead(f, LIGHT.windup());
    }

    /** The predicted distance to the opponent when an attack with this wind-up lands. */
    private double lead(Frame f, int windup) {
        double predicted = f.dist - (skill > 0.4 ? Math.max(-0.1, f.closing()) * windup * 0.85 : 0);
        return predicted + rng.gaussian(0, 0.35 * (1 - skill));
    }

    private void strike(Frame f, Plan p) {
        p.attackHeld = true;      // a one-tick press is a light strike
        cooldownUntil = f.now + LIGHT.windup() + 4 + rng.nextInt(1 + (int) (9 * (1 - aggression)));
    }

    private void startHeavy(Frame f, Plan p, int hold) {
        holding = true;
        releaseAt = f.now + hold;
        p.attackHeld = true;
        cooldownUntil = f.now + hold + HEAVY.windup() + 10;
    }

    private void continueCharge(Frame f, Plan p) {
        if (f.me.act() != Act.CHARGE && f.me.act() != Act.NONE) {
            holding = false;
            return;
        }
        boolean pointless = f.dist > HEAVY.reach() + 2.4 || f.foe.invulnerable();
        if (pointless && f.now < releaseAt - 2 && rng.chance(0.6)) {
            holding = false;
            p.guard = true;                 // a feint: the charge is cancelled by raising the guard
            guardUntil = f.now + 8;
            return;
        }
        if (f.now >= releaseAt) {
            holding = false;
            return;                          // attackHeld stays false: the button comes up
        }
        p.attackHeld = true;
        // keep walking in while charging
        approach(f, p, HEAVY.reach() - 0.5, 0.0, 0.8);
    }

    // ================================================================== fighting

    private void enter(Frame f, Mode m, int ticks) {
        mode = m;
        modeUntil = f.now + ticks;
        if (m == Mode.POKE) {
            pokesLeft = 1 + rng.nextInt(1 + (int) (1 + 2.2 * aggression));
        }
    }

    private void fight(Frame f, Plan p) {
        if (f.me.act() == Act.DODGE) {
            return;
        }
        if (f.me.act() == Act.CHARGE || f.me.act().isAttack()) {
            // keep stepping in while the swing winds up: the opponent will not stand still either
            if (f.me.windingUp() && f.dist > LIGHT.reach() - 1.0) {
                approach(f, p, LIGHT.reach() - 1.0, 0.0, 1.0);
            }
            return;
        }
        if (f.attacker()) {
            fightAttacker(f, p);
        } else {
            fightDefender(f, p);
        }
    }

    private void fightAttacker(Frame f, Plan p) {
        FighterView foe = f.foe;
        Pt me = new Pt(f.me.x(), f.me.z());
        if (court.inCircle(me.x(), me.z())) {
            holdTheGoal(f, p);
            return;
        }
        boolean laneOpen = !blocks(f) || foe.helpless() && foe.stunLeft() >= 8;
        boolean desperate = f.timeFrac < 0.18;
        boolean passed = court.along(me.x(), me.z()) > court.along(foe.x(), foe.z()) + 0.5;
        if (laneOpen && (passed || foe.helpless() || desperate || f.dist > 9)) {
            // nobody in the way: run for it
            Pt goal = courseTarget(f);
            double speed = 1.0;
            if (f.now >= sprintUntil) {
                sprintUntil = f.now + 30;
                sprintNow = rng.chance(sprintBias) || passed || desperate;
            }
            if (sprintNow && f.stamina() > 22 && (f.dist > 7 || passed)) {
                speed = 1.3;
            }
            moveTo(p, f.me, goal, speed);
            return;
        }
        if (f.dist > 8.5) {
            moveTo(p, f.me, courseTarget(f), 1.0);
            return;
        }
        combat(f, p);
    }

    private void holdTheGoal(Frame f, Plan p) {
        Pt c = court.circleCenter();
        double toC = Math.hypot(c.x() - f.me.x(), c.z() - f.me.z());
        if (toC > 1.5 && f.dist > LIGHT.reach() + 0.6) {
            moveTo(p, f.me, c, 1.0);
        } else {
            approach(f, p, LIGHT.reach() + 1.4, 0.4, 1.0);
        }
        if (f.free() && f.dist < LIGHT.reach() + 1.6 && !f.foe.helpless() && f.now >= guardUntil && rng.chance(0.10)) {
            guardUntil = f.now + 14;
        }
        if (f.free() && f.now >= cooldownUntil && f.dist < LIGHT.reach() - 0.3 && f.facingError() < 35 && rng.chance(0.08)) {
            strike(f, p);
        }
    }

    private void fightDefender(Frame f, Plan p) {
        FighterView foe = f.foe;
        boolean capturing = court.inCircle(foe.x(), foe.z());
        boolean pastMe = court.along(foe.x(), foe.z()) > court.along(f.me.x(), f.me.z()) + 0.5;
        double engageRange = (postKind == Post.KEEPER ? 4.6 : postKind == Post.MOUTH ? 5.6 : 6.6) + 2.2 * aggression + 3.5 * urgency(f);
        Pt post = post();
        double toPost = Math.hypot(post.x() - f.me.x(), post.z() - f.me.z());
        if (pastMe && f.dist > LIGHT.reach() + 0.3 && !capturing) {
            // it got by: back to the goal, quickly (sprint), to stand between it and the centre
            moveTo(p, f.me, court.circleCenter(), f.stamina() > 10 ? 1.3 : 1.0);
            return;
        }
        if (capturing || f.captureThreat() || f.timeFrac < 0.12 && foe.health() > f.me.health()
                || f.dist < engageRange && (toPost < leash() || f.dist < LIGHT.reach() + 1.0)) {
            combat(f, p);
            return;
        }
        mode = Mode.APPROACH;
        if (toPost > 0.6) {
            moveTo(p, f.me, post, toPost > 8 ? 1.15 : 1.0);
        } else {
            double lat = court.lateral(foe.x(), foe.z()) - court.lateral(f.me.x(), f.me.z());
            if (Math.abs(lat) > 1.2 && court.along(foe.x(), foe.z()) > 4.0) {
                double sgn = Math.signum(lat);
                p.dirX = -court.axis().z() * sgn;
                p.dirZ = court.axis().x() * sgn;
                p.speed = 0.5;
                p.moveSet = true;
            }
        }
    }

    /** True when the opponent stands in the lane between us and the goal (no way to run past). */
    private boolean blocks(Frame f) {
        Pt goal = courseTarget(f);
        double gx = goal.x() - f.me.x(), gz = goal.z() - f.me.z();
        double gl = Math.max(1e-6, Math.hypot(gx, gz));
        double rx = f.foe.x() - f.me.x(), rz = f.foe.z() - f.me.z();
        double along = (rx * gx + rz * gz) / gl;
        double across = Math.abs(-rx * gz / gl + rz * gx / gl);
        return along > 0 && along < gl && across < 2.4;
    }

    /** Where this defender waits (see {@link Post}). */
    private Pt post() {
        Pt axis = court.axis();
        Pt target;
        if (postKind == Post.NECK) {
            Pt n = court.neckCenter();
            target = new Pt(n.x() + axis.x() * NECK_REACH, n.z() + axis.z() * NECK_REACH);
        } else {
            Pt c = court.circleCenter();
            double stand = postKind == Post.KEEPER ? 0.4 + 0.8 * postJitter : court.captureRadius() + 2.0 + 1.5 * postJitter;
            target = new Pt(c.x() - axis.x() * stand, c.z() - axis.z() * stand);
        }
        return court.clampInside(target, 1.5);
    }

    /** How far from its post a defender follows the fight before it returns. */
    private double leash() {
        return switch (postKind) {
            case KEEPER -> 3.5;
            case MOUTH -> 4.5;
            case NECK -> 5.0;
        };
    }

    // ------------------------------------------------------------------ the modes

    private void combat(Frame f, Plan p) {
        if (f.captureThreat()) {
            // the goal is being taken: attack, whatever it costs
            if (mode != Mode.POKE || f.now >= modeUntil) {
                enter(f, Mode.POKE, 40);
                pokesLeft = 6;
            }
        } else if ((f.tired() || f.losing()) && urgency(f) < 0.6) {
            if (mode != Mode.RESET) {
                enter(f, Mode.RESET, 24 + rng.nextInt(20));
            }
        } else if (mode == Mode.APPROACH || f.now >= modeUntil) {
            pick(f);
        }
        switch (mode) {
            case POKE -> poke(f, p);
            case BAIT -> bait(f, p);
            case GUARD -> guardPress(f, p);
            case HEAVY -> heavySetup(f, p);
            case RESET -> reset(f, p);
            default -> poke(f, p);
        }
    }

    private void pick(Frame f) {
        FighterView foe = f.foe;
        double wPoke = 1.0 + 1.6 * aggression;
        double wBait = (0.3 + 1.7 * patience) * (1.2 - 0.6 * aggression);
        double wGuard = (0.2 + 1.3 * (1 - aggression)) * (0.6 + 0.8 * skill) * (foe.act() == Act.CHARGE || foe.helpless() ? 0.3 : 1.0);
        double wHeavy = (0.40 + 1.5 * risk * (0.3 + aggression)) * (f.stamina() >= 45 ? 1.0 : 0.0) * (foe.guardUp() ? 2.5 : 1.0);
        if (foe.guardUp()) {
            wPoke *= 0.35;
        }
        double u = urgency(f);
        wPoke += 3.0 * u;
        wBait *= 1.0 - 0.8 * u;
        wGuard *= 1.0 - 0.6 * u;
        double r = rng.nextDouble() * (wPoke + wBait + wGuard + wHeavy);
        if (r < wPoke) {
            enter(f, Mode.POKE, 70);
        } else if (r < wPoke + wBait) {
            enter(f, Mode.BAIT, 22 + (int) (patience * 30) + rng.nextInt(12));
        } else if (r < wPoke + wBait + wGuard) {
            enter(f, Mode.GUARD, 26 + (int) (patience * 30));
        } else {
            enter(f, Mode.HEAVY, 60);
        }
    }

    /** A burst of light strikes at engaging distance. */
    private void poke(Frame f, Plan p) {
        boolean helpless = f.foe.helpless() && !f.foe.guardUp();
        approach(f, p, helpless ? Math.max(1.2, engage - 0.8) : engage, 0.30 * herd(f), helpless ? 1.15 : 1.0);
        if (f.free() && f.now >= cooldownUntil && estimate(f) <= LIGHT.reach() - 0.25 && f.facingError() <= LIGHT.cone() - 14
                && rng.chance(0.16 + 0.22 * aggression + 0.12 * skill)) {
            // not every tick in range: two fighters that always strike on the same tick look like clockwork
            strike(f, p);
            if (--pokesLeft <= 0) {
                if (f.stamina() >= 55 && rng.chance(0.18 + 0.50 * risk)) {
                    enter(f, Mode.HEAVY, 60);          // the burst ends in a big strike
                } else {
                    enter(f, rng.chance(0.55 * (1 - aggression) + 0.25) ? Mode.RESET : Mode.BAIT, 14 + rng.nextInt(18));
                }
            }
        }
    }

    /** Hover just outside the opponent's reach; a whiff of its own is the cue to commit. */
    private void bait(Frame f, Plan p) {
        approach(f, p, HEAVY.reach() + 0.8 + rng.nextDouble() * 0.2, 0.45 * herd(f), 1.0);
        if (f.foe.recovering() && f.dist < LIGHT.reach() + 1.4) {
            enter(f, Mode.POKE, 40);     // it swung and missed: now
        }
    }

    /** Advance behind the guard; at close range drop it for a shove or a heavy strike. */
    private void guardPress(Frame f, Plan p) {
        FighterView foe = f.foe;
        guardUntil = Math.max(guardUntil, f.now + 2);
        approach(f, p, engage - 0.1, 0.35 * herd(f), 0.8);
        if (f.dist <= engage + 0.3 && f.free() && f.now >= cooldownUntil && f.facingError() <= 30 && rng.chance(0.14)) {
            guardUntil = f.now;
            double heavy = foe.guardUp() && f.stamina() >= 50 ? 0.55 * (0.4 + risk) : 0.18 * risk;
            if (f.stamina() >= SHOVE.cost() + 6 && rng.chance(foe.guardUp() ? 0.5 : 0.35) && f.dist <= SHOVE.reach() - 0.2) {
                p.shove = true;
                cooldownUntil = f.now + SHOVE.windup() + 8;
            } else if (rng.chance(heavy)) {
                startHeavy(f, p, HEAVY_MIN_HOLD + 1 + rng.nextInt(5));
            } else {
                strike(f, p);
            }
            enter(f, Mode.RESET, 12 + rng.nextInt(12));
        }
    }

    /** Close in and charge a heavy strike (the opponent sees the telegraph). */
    private void heavySetup(Frame f, Plan p) {
        approach(f, p, HEAVY.reach() - 0.4, 0.2 * herd(f), 1.0);
        if (f.free() && f.now >= cooldownUntil && f.dist <= HEAVY.reach() + 0.5 && f.facingError() <= 30 && f.stamina() >= HEAVY.cost() + 10) {
            startHeavy(f, p, HEAVY_MIN_HOLD + 1 + rng.nextInt(1 + (int) (7 * (1 - 0.5 * skill))));
            enter(f, Mode.RESET, 24);
        } else if (f.stamina() < HEAVY.cost() + 10) {
            enter(f, Mode.RESET, 20);
        }
    }

    /** Back off to regain breath (a cautious fighter keeps the guard up while doing so). */
    private void reset(Frame f, Plan p) {
        boolean anchored = !f.attacker() && court.circleDistance(f.me.x(), f.me.z()) < court.captureRadius() + 3.0;
        approach(f, p, anchored ? Math.min(f.dist, LIGHT.reach() + 0.4) : LIGHT.reach() + 2.2 + (f.tired() ? 1.2 : 0), 0.35 * herd(f), 1.0);
        if (f.free() && f.dist < LIGHT.reach() + 1.8 && f.now >= guardUntil && !f.foe.helpless() && rng.chance(0.05 + 0.10 * (1 - aggression))) {
            guardUntil = f.now + 10 + (int) (patience * 14);
        }
        if (f.now >= modeUntil && !f.tired()) {
            mode = Mode.APPROACH;
        }
    }

    /** +1 / -1: the strafe direction that brings the opponent's nearest edge behind it (a random side when none is close). */
    private double herd(Frame f) {
        if (f.now >= strafeUntil) {
            strafeUntil = f.now + 14 + rng.nextInt(24);
            if (rng.chance(0.5)) {
                strafeSign = -strafeSign;
            }
        }
        FighterView foe = f.foe;
        double edge = court.edgeDistance(foe.x(), foe.z());
        if (edge > 3.4 || f.dist > LIGHT.reach() + 2.2 || skill < 0.3) {
            return strafeSign;
        }
        Pt e = court.nearestEdgePoint(foe.x(), foe.z());
        double ox = e.x() - foe.x(), oz = e.z() - foe.z();
        double ol = Math.max(1e-6, Math.hypot(ox, oz));
        ox /= ol;
        oz /= ol;
        // we want the outward direction to line up with the direction from us to the foe
        double cross = f.ux * oz - f.uz * ox;
        return cross > 0 ? -1 : 1;
    }

    // ================================================================== steering helpers

    /** Walk to a point. */
    private void moveTo(Plan p, FighterView me, Pt goal, double speed) {
        double dx = goal.x() - me.x(), dz = goal.z() - me.z();
        double len = Math.hypot(dx, dz);
        if (len < 0.15) {
            return;
        }
        p.dirX = dx / len;
        p.dirZ = dz / len;
        p.speed = Math.min(speed, Math.max(0.35, len / 1.5 * speed));
        p.moveSet = true;
    }

    /** Keep a distance to the opponent (positive error closes in, negative backs off) while strafing around it. */
    private void approach(Frame f, Plan p, double desired, double strafe, double speed) {
        double err = f.dist - desired;
        double radial = Math.max(-1.0, Math.min(1.0, err / 1.2));
        double tx = -f.uz, tz = f.ux;
        double dx = f.ux * radial + tx * strafe;
        double dz = f.uz * radial + tz * strafe;
        double len = Math.hypot(dx, dz);
        if (len < 0.12) {
            return;
        }
        p.dirX = dx / len;
        p.dirZ = dz / len;
        p.speed = speed * Math.min(1.0, len);
        p.moveSet = true;
    }

    /** The next point on the way to the circle: through the neck, then straight at the centre. */
    private Pt courseTarget(Frame f) {
        Pt neck = court.neckCenter();
        double toNeck = (neck.x() - f.me.x()) * court.axis().x() + (neck.z() - f.me.z()) * court.axis().z();
        return toNeck > CourtGeometry.NECK_HALF_LENGTH ? neck : court.circleCenter();
    }

    /** Keeps the NPC off the line: steers inwards near the edge and never walks out of the court. */
    private double[] safeDir(Frame f, double dx, double dz) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            return new double[]{0, 0};
        }
        dx /= len;
        dz /= len;
        double ed = f.edge;
        Pt in = court.inward(f.me.x(), f.me.z());
        // in a fight the line is a weapon: keep well away from it while the opponent is close
        double keep = f.dist < LIGHT.reach() + 2.5 ? 2.3 : 1.8;
        if (ed < keep) {
            double w = (keep - ed) * 0.9;
            dx += in.x() * w;
            dz += in.z() * w;
            double l2 = Math.hypot(dx, dz);
            dx /= l2;
            dz /= l2;
        }
        double room = court.rayExit(f.me.x(), f.me.z(), dx, dz, 1.6);
        if (room < 1.3 && ed < 2.4) {
            dx = in.x();
            dz = in.z();
        }
        return new double[]{dx, dz};
    }
}
