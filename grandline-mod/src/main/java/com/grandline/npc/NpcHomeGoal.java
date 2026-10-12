package com.grandline.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Daily schedule: stay near the workplace by day, return home at night; never drift far from the settlement. */
public class NpcHomeGoal extends Goal {
    private final HumanoidNpc npc;

    public NpcHomeGoal(HumanoidNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private int leash() {
        long day = npc.level().getDayTime() % 24000L;
        boolean night = day > 12800 && day < 23200;
        return night ? 4 : (npc.archetype().merchant() ? 6 : 24);
    }

    @Override
    public boolean canUse() {
        BlockPos h = npc.home();
        if (h.equals(BlockPos.ZERO) || npc.getTarget() != null) return false;
        return npc.blockPosition().distSqr(h) > leash() * leash();
    }

    @Override
    public void start() {
        BlockPos h = npc.home();
        npc.getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 0.8);
    }

    @Override
    public boolean canContinueToUse() {
        return !npc.getNavigation().isDone() && npc.getTarget() == null;
    }
}
