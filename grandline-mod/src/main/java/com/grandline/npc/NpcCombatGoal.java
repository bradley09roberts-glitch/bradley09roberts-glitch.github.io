package com.grandline.npc;

import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.combat.CombatState;
import com.grandline.combat.weapon.WeaponClass;
import com.grandline.haki.Haki;
import com.grandline.registry.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Tactical melee/ranged combat: approach or kite depending on weapon, strafe, combo attacks through the
 * shared combat system, guard against telegraphed attacks, dodge, and use techniques on cooldowns.
 */
public class NpcCombatGoal extends Goal {
    private final HumanoidNpc npc;
    private int strafeTicks;
    private int strafeDir = 1;
    private int abilityDelay = 40;
    private int repath;

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
    }

    @Override
    public void stop() {
        CombatState s = npc.combat();
        s.armament = false;
        s.observation = false;
        s.blocking = false;
        npc.setAggressive(false);
        npc.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity t = npc.getTarget();
        if (t == null) return;
        long now = npc.level().getGameTime();
        CombatState s = npc.combat();
        npc.getLookControl().setLookAt(t, 30, 30);
        double dist = npc.distanceTo(t);
        WeaponClass wc = CombatManager.weaponOf(npc.getMainHandItem());
        boolean ranged = wc != null && wc.ranged;
        double reach = 2.4 * Math.max(0.7, npc.getScale()) + (wc == WeaponClass.SPEAR || wc == WeaponClass.NAGINATA ? 1.5 : 0);
        int tier = npc.tier();

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

        // ---- techniques ----
        if (--abilityDelay <= 0) {
            abilityDelay = Math.max(20, 90 - tier * 7) + npc.getRandom().nextInt(40);
            List<Ability> list = npc.techniques();
            if (!list.isEmpty()) {
                Ability a = list.get(npc.getRandom().nextInt(list.size()));
                if (!a.id().endsWith("/toggle") && !a.id().endsWith("/transform") && !s.onCooldown(a.id(), now)) {
                    CombatManager.useAbility(npc, a, t);
                }
            }
        }

        // ---- movement ----
        if (ranged) {
            if (dist < 7) moveAway(t);
            else if (dist > 20) path(t, 1.0);
            else strafe(t);
            if (dist < 28 && npc.hasLineOfSight(t)) CombatManager.fireRanged(npc, npc.getMainHandItem(), false);
            return;
        }
        if (dist > reach + 0.5) {
            path(t, dist > 8 ? 1.15 : 1.0);
        } else {
            npc.getNavigation().stop();
            strafe(t);
            if (now >= s.nextAttackAllowed && !s.blocking) {
                boolean heavy = npc.getRandom().nextFloat() < 0.15f + tier * 0.02f;
                CombatManager.attack(npc, heavy, heavy ? 6 : 0);
            }
        }
    }

    private void path(LivingEntity t, double speed) {
        if (--repath <= 0) {
            repath = 8;
            npc.getNavigation().moveTo(t, speed);
        }
    }

    private void strafe(LivingEntity t) {
        if (--strafeTicks <= 0) {
            strafeTicks = 20 + npc.getRandom().nextInt(30);
            strafeDir = npc.getRandom().nextBoolean() ? 1 : -1;
        }
        npc.getMoveControl().strafe(-0.1f, 0.35f * strafeDir);
    }

    private void moveAway(LivingEntity t) {
        Vec3 away = npc.position().subtract(t.position()).normalize().scale(6);
        npc.getNavigation().moveTo(npc.getX() + away.x, npc.getY(), npc.getZ() + away.z, 1.1);
    }
}
