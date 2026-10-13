package com.grandline.npc.crew;

import com.grandline.combat.Cover;
import com.grandline.npc.HumanoidNpc;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A crew member's choice of target (registered first in the target selector, so the generic "attack whatever is
 * hostile" goals never run for companions). Only foes of the party ({@link Companions#isFoe}) and only near the
 * captain: never a neutral, never a chase across the island.
 * <ul>
 * <li>a focus order wins while it lasts; regrouping and being downed clear the target;</li>
 * <li><b>fighter</b>: whoever is going for the captain, nearest to the captain first;</li>
 * <li><b>ranged</b>: the captain's own target, then foes going for the captain, preferring ones in the open;</li>
 * <li><b>support</b>: only foes that come close to it or to the captain;</li>
 * <li>holding: only foes near the posted spot; reserve members only defend themselves.</li>
 * </ul>
 */
public class CompanionTargetGoal extends Goal {
    /** How far from the captain (or the hold spot) companions look for foes, and the farthest they keep fighting one. */
    static final double ENGAGE = 16, LEASH = 24, HOLD_RADIUS = 10, FOCUS_LEASH = 36;
    private final HumanoidNpc npc;
    private int scan;

    public CompanionTargetGoal(HumanoidNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return !npc.level().isClientSide && !npc.notableId().isEmpty() && CrewManager.isCrew(npc);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        scan = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        CrewManager.Member m = CrewManager.memberOf(npc);
        if (m == null) return;
        long now = npc.level().getGameTime();
        LivingEntity cur = npc.getTarget();
        if (m.downed || now < m.regroupUntil) {
            if (cur != null) npc.setTarget(null);
            return;
        }
        ServerPlayer cap = CrewManager.captainOf(npc);
        if (cur != null && !keep(m, cap, cur)) {
            npc.setTarget(null);
            cur = null;
        }
        if (--scan > 0) return;
        scan = 5;
        LivingEntity best = choose(m, cap, cur, now);
        if (best != null && best != cur) npc.setTarget(best);
    }

    /** Still a target worth fighting: a foe, and not too far from where this companion is meant to be. */
    boolean keep(CrewManager.Member m, @Nullable ServerPlayer cap, LivingEntity t) {
        if (!Companions.isFoe(npc, t)) return false;
        if (!m.active) return npc.distanceToSqr(t) < 16 * 16 && (npc.getLastHurtByMob() == t || t instanceof Mob mb && mb.getTarget() == npc);
        if (m.holding() && m.holdPos() != null) return t.distanceToSqr(Vec3.atCenterOf(m.holdPos())) < (HOLD_RADIUS + 4) * (HOLD_RADIUS + 4);
        if (cap == null || cap.level() != npc.level()) return npc.distanceToSqr(t) < 16 * 16;
        double leash = t.getId() == m.focusId ? FOCUS_LEASH : LEASH;
        return t.distanceToSqr(cap) < leash * leash;
    }

    @Nullable
    private LivingEntity choose(CrewManager.Member m, @Nullable ServerPlayer cap, @Nullable LivingEntity cur, long now) {
        if (m.focusId >= 0) {
            if (now <= m.focusUntil && npc.level().getEntity(m.focusId) instanceof LivingEntity f && keep(m, cap, f)) return f;
            m.focusId = -1;
        }
        if (!m.active || cap == null || cap.level() != npc.level()) { // in reserve, or the captain is away: self-defence only
            LivingEntity by = npc.getLastHurtByMob();
            return cur != null ? cur : by != null && keep(m, cap, by) ? by : null;
        }
        CompanionRole role = CompanionRole.of(npc);
        if (role == CompanionRole.RANGED && !m.holding()) {
            LivingEntity ct = Companions.captainTarget(cap);
            if (ct != null && keep(m, cap, ct)) return ct;
        }
        Vec3 anchor = m.holding() && m.holdPos() != null ? Vec3.atCenterOf(m.holdPos()) : cap.position();
        double radius = m.holding() ? HOLD_RADIUS : ENGAGE;
        LivingEntity best = null, threat = null;
        double bestScore = Double.MAX_VALUE, threatDist = Double.MAX_VALUE;
        for (LivingEntity e : npc.level().getEntitiesOfClass(LivingEntity.class, new AABB(anchor, anchor).inflate(radius), e -> e != npc && e.isAlive())) {
            if (e.position().distanceTo(anchor) > radius || !keep(m, cap, e)) continue;
            boolean onCaptain = e instanceof Mob mob && mob.getTarget() == cap, onMe = e instanceof Mob mob2 && mob2.getTarget() == npc;
            double dc = e.distanceTo(cap), dn = e.distanceTo(npc);
            if (onCaptain && dc < threatDist) {
                threatDist = dc;
                threat = e;
            }
            double score = switch (role) {
                case FIGHTER -> dc - (onCaptain ? 8 : 0) - (onMe ? 3 : 0);
                case RANGED -> dc - (onCaptain ? 4 : 0) + (Cover.canSee(npc, e) ? 0 : 6);
                case SUPPORT -> dn <= 5 || (onMe || onCaptain) && dc <= 6 ? dn : Double.MAX_VALUE;
            };
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        // a fighter drops what it is doing for whoever goes for the captain; others stick with their foe
        if (role == CompanionRole.FIGHTER && threat != null && cur != null && !(cur instanceof Mob cm && cm.getTarget() == cap)) return threat;
        if (cur != null) return cur;
        return best;
    }
}
