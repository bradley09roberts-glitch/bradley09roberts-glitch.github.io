package com.grandline.npc;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Cowardly or civilian NPCs run from danger and call for allies when hurt. */
public class NpcFleeGoal extends Goal {
    private final HumanoidNpc npc;
    private LivingEntity threat;

    public NpcFleeGoal(HumanoidNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity attacker = npc.getLastHurtByMob();
        if (attacker == null) return false;
        boolean civilian = npc.archetype().hostility() == Archetype.Hostility.PASSIVE;
        boolean scared = npc.archetype().coward() && npc.getHealth() < npc.getMaxHealth() * 0.3f;
        if (!civilian && !scared) return false;
        threat = attacker;
        return npc.distanceTo(attacker) < 20;
    }

    @Override
    public void start() {
        NpcOutcomes.callForHelp(npc, threat);
        Vec3 away = npc.position().subtract(threat.position()).normalize().scale(14);
        npc.getNavigation().moveTo(npc.getX() + away.x, npc.getY(), npc.getZ() + away.z, 1.3);
    }

    @Override
    public boolean canContinueToUse() {
        return !npc.getNavigation().isDone() && threat != null && threat.isAlive() && npc.distanceTo(threat) < 22;
    }
}
