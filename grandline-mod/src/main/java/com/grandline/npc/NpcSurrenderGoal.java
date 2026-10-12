package com.grandline.npc;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Non-boss fighters below 15% health may surrender: they stop fighting and can be spared or arrested. */
public class NpcSurrenderGoal extends Goal {
    private final HumanoidNpc npc;

    public NpcSurrenderGoal(HumanoidNpc npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (npc.flag(GLNpc.FLAG_SURRENDER)) return true;
        if (npc.tier() >= 6 || npc.archetype().killsDowned() || npc.getTarget() == null) return false;
        if (npc.getHealth() > npc.getMaxHealth() * 0.15f) return false;
        if (npc.getRandom().nextFloat() < 0.5f) {
            npc.setFlag(GLNpc.FLAG_SURRENDER, true);
            npc.setTarget(null);
            com.grandline.net.GLNetwork.anim(npc, "surrender");
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        npc.getNavigation().stop();
        npc.setTarget(null);
        // a crew that struck its colours (a merchant yielding at sea, see ship/LivingSea) stays yielded however healthy
        if (npc.getHealth() > npc.getMaxHealth() * 0.6f && !npc.getTags().contains("gl_yielded")) npc.setFlag(GLNpc.FLAG_SURRENDER, false);
    }
}
