package com.grandline.npc;

import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.Cover;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Tactical melee/ranged combat through the shared combat system, with fair rules:
 * <ul>
 * <li><b>Perception.</b> A foe only swings, shoots or uses a technique when it can actually reach the target (the
 * same line-of-effect test as for every blow, see {@link Cover}). When it loses sight it goes to where it last saw
 * the target and searches; after {@link #FORGET} ticks without sight it gives up and goes home.</li>
 * <li><b>Melee slots.</b> Only a few foes press one victim at once ({@link AttackSlots}); the rest circle a few steps
 * out, guard and wait. A holder steps back after a short burst so the pressure rotates. Bosses need no slot.</li>
 * <li><b>Ranged foes</b> keep their distance and move for a clear shot instead of firing into walls.</li>
 * <li><b>Bounded pursuit.</b> Settlement NPCs do not chase further than {@link #LEASH} blocks from home, and a foe
 * that makes no progress towards its target for several seconds gives up rather than grinding against a wall.</li>
 * </ul>
 */
public class NpcCombatGoal extends Goal {
    /** Ticks without sight of the target before giving up. */
    static final int FORGET = 160;
    /** Blocks from home a settlement NPC will chase. */
    static final int LEASH = 64;
    /** Swings in one burst before a slot holder steps back. */
    static final int BURST = 3;

    private final HumanoidNpc npc;
    private int strafeTicks;
    private int strafeDir = 1;
    private int abilityDelay = 40;
    private int repath;
    private int sightCheck;
    private boolean canStrike, sees;
    private long lastSeen;
    private Vec3 lastKnown = Vec3.ZERO;
    private int swings;
    private long restUntil;
    private double bestDist = Double.MAX_VALUE;
    private int stuckChecks;
    private long nextProgressCheck;

    public NpcCombatGoal(HumanoidNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = npc.getTarget();
        return t != null && t.isAlive() && !npc.flag(GLNpc.FLAG_SURRENDER) && !npc.hasEffect(ModEffects.KNOCKED_OUT);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && npc.distanceToSqr(npc.getTarget()) < 48 * 48;
    }

    @Override
    public void start() {
        CombatState s = npc.combat();
        s.armament = npc.hakiArmament() > 0;
        s.observation = npc.hakiObservation() > 0;
        npc.setAggressive(true);
        long now = npc.level().getGameTime();
        lastSeen = now;
        lastKnown = npc.getTarget() != null ? npc.getTarget().position() : npc.position();
        sightCheck = 0;
        swings = 0;
        restUntil = 0;
        bestDist = Double.MAX_VALUE;
        stuckChecks = 0;
        nextProgressCheck = now + 40;
    }

    @Override
    public void stop() {
        CombatState s = npc.combat();
        s.armament = false;
        s.observation = false;
        s.blocking = false;
        LivingEntity t = npc.getTarget();
        if (t != null) AttackSlots.release(t, npc);
        npc.setAggressive(false);
        npc.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /** Stop fighting this target: forget it and let the home goal take the NPC back. */
    private void disengage(LivingEntity t) {
        AttackSlots.release(t, npc);
        npc.calmTowards(t, 200);
        npc.setTarget(null);
        npc.setLastHurtByMob(null);
        npc.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = npc.getTarget();
        if (t == null || com.grandline.boss.BossManager.busy(npc)) return; // a boss winding up a signature attack stands still
        long now = npc.level().getGameTime();
        CombatState s = npc.combat();
        boolean boss = com.grandline.boss.BossManager.isBoss(npc);

        // ---- perception: the same line-of-effect test as for blows, refreshed a few times a second ----
        if (--sightCheck <= 0) {
            sightCheck = 4;
            canStrike = Cover.reachable(npc, t);
            sees = canStrike || npc.getSensing().hasLineOfSight(t);
            if (sees) {
                lastSeen = now;
                lastKnown = t.position();
            }
        }
        if (!boss && now - lastSeen > FORGET) {
            disengage(t);
            return;
        }
        BlockPos home = npc.home();
        if (!boss && !home.equals(BlockPos.ZERO) && npc.distanceToSqr(Vec3.atCenterOf(home)) > (double) LEASH * LEASH) {
            disengage(t);
            return;
        }
        double dist = npc.distanceTo(t);
        if (!sees) {
            // search: walk to where the target was last seen
            AttackSlots.release(t, npc);
            if (--repath <= 0) {
                repath = 10;
                npc.getNavigation().moveTo(lastKnown.x, lastKnown.y, lastKnown.z, 1.0);
            }
            return;
        }
        npc.getLookControl().setLookAt(t, 30, 30);
        WeaponClass wc = CombatManager.weaponOf(npc.getMainHandItem());
        boolean ranged = wc != null && wc.ranged;
        double reach = 2.4 * Math.max(0.7, npc.getScale()) + (wc == WeaponClass.SPEAR || wc == WeaponClass.NAGINATA ? 1.5 : 0);
        int tier = npc.tier();

        // ---- stuck: no progress towards a target it cannot strike ----
        if (now >= nextProgressCheck) {
            nextProgressCheck = now + 40;
            if (!canStrike && dist > reach && dist > bestDist - 0.5) {
                if (++stuckChecks >= 4) {
                    disengage(t);
                    return;
                }
            } else stuckChecks = 0;
            bestDist = Math.min(bestDist, dist);
        }

        // ---- defence: react to the target's wind-up ----
        CombatState ts = CombatState.of(t);
        if (dist < 5 && now - ts.lastAttack <= 2 && !s.blocking && npc.getRandom().nextFloat() < 0.12f + tier * 0.05f) {
            if (npc.getRandom().nextFloat() < 0.4f + tier * 0.03f) {
                float side = npc.getRandom().nextBoolean() ? 1 : -1;
                CombatManager.dodge(npc, side, -0.3f);
            } else {
                s.blocking = true;
                s.blockStart = now;
                s.schedule(now + 12, () -> s.blocking = false);
            }
        }

        // ---- techniques: only with a clear line and the target within technique range ----
        if (--abilityDelay <= 0) {
            abilityDelay = Math.max(30, 90 - tier * 6) + npc.getRandom().nextInt(40);
            List<Ability> list = npc.techniques();
            if (!list.isEmpty() && canStrike && dist <= 18) {
                Ability a = list.get(npc.getRandom().nextInt(list.size()));
                if (!a.id().endsWith("/toggle") && !a.id().endsWith("/transform") && !s.onCooldown(a.id(), now)) {
                    CombatManager.useAbility(npc, a, t);
                }
            }
        }

        // ---- ranged: keep distance, move for a clear shot ----
        if (ranged) {
            if (!canStrike) path(t, 1.0);
            else if (dist < 7) moveAway(t, 6);
            else if (dist > 20) path(t, 1.0);
            else strafe();
            if (dist < 28 && canStrike) CombatManager.fireRanged(npc, npc.getMainHandItem(), false);
            return;
        }

        // ---- melee: a slot on the target, a clear line, then the blow ----
        boolean slot = boss || now >= restUntil && AttackSlots.claim(t, npc, now);
        if (!slot) {
            // wait a few steps out, circling with guard up now and then
            if (dist < 3.5) moveAway(t, 3);
            else if (dist > 6.5) path(t, 1.0);
            else {
                npc.getNavigation().stop();
                strafe();
                if (!s.blocking && npc.getRandom().nextFloat() < 0.02f) {
                    s.blocking = true;
                    s.blockStart = now;
                    s.schedule(now + 20, () -> s.blocking = false);
                }
            }
            return;
        }
        if (dist > reach + 0.5 || !canStrike) {
            path(t, dist > 8 ? 1.15 : 1.0);
        } else {
            npc.getNavigation().stop();
            strafe();
            if (now >= s.nextAttackAllowed && !s.blocking) {
                boolean heavy = npc.getRandom().nextFloat() < 0.12f + tier * 0.02f;
                if (CombatManager.attack(npc, heavy, heavy ? 6 : 0) && !boss && ++swings >= BURST) {
                    // step back and let another foe have a turn
                    swings = 0;
                    AttackSlots.release(t, npc);
                    restUntil = now + 30 + npc.getRandom().nextInt(30);
                }
            }
        }
    }

    private void path(LivingEntity t, double speed) {
        if (--repath <= 0) {
            repath = 8;
            npc.getNavigation().moveTo(t, speed);
        }
    }

    private void strafe() {
        if (--strafeTicks <= 0) {
            strafeTicks = 20 + npc.getRandom().nextInt(30);
            strafeDir = npc.getRandom().nextBoolean() ? 1 : -1;
        }
        npc.getMoveControl().strafe(-0.1f, 0.35f * strafeDir);
    }

    private void moveAway(LivingEntity t, double by) {
        Vec3 away = npc.position().subtract(t.position()).normalize().scale(by);
        npc.getNavigation().moveTo(npc.getX() + away.x, npc.getY(), npc.getZ() + away.z, 1.1);
    }
}
