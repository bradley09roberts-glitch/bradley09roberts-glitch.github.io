package com.squidgame.entity.ai;

import com.squidgame.entity.ContestantEntity;
import com.squidgame.entity.NpcBehavior;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Default behaviour while contestants wait (dormitory, waiting rooms, results): stand around, glance at whoever
 * is close, shuffle a little and occasionally nod, shake their head or fidget, in a way that depends on their
 * personality (nervous contestants fidget and look around, confident ones stand still).
 */
public final class WaitingBehavior implements NpcBehavior {
    private Vec3 anchor;
    private int timer;
    private int glance;
    private boolean shuffling;

    @Override
    public void start(ContestantEntity npc) {
        anchor = npc.position();
        timer = npc.rng().rangeInt(20, 80);
    }

    @Override
    public void tick(ContestantEntity npc) {
        if (anchor == null || npc.position().distanceToSqr(anchor) > 9.0 && !shuffling) {
            anchor = npc.position(); // teleported: re-anchor
        }
        if (shuffling) {
            if (!npc.isTryingToMove() || npc.position().distanceToSqr(anchor) < 0.5) {
                shuffling = false;
                npc.stopMoving();
            }
            return;
        }
        if (--timer > 0) {
            if (glance > 0) {
                glance--;
            }
            return;
        }
        var p = npc.personality();
        timer = npc.rng().rangeInt(40, 160);
        double roll = npc.rng().nextDouble();
        LivingEntity near = npc.level().getNearestPlayer(npc, 8.0);
        if (near != null && roll < 0.45) {
            npc.lookAtPos(near.getEyePosition());
            glance = 40;
            if (npc.rng().chance(0.25)) {
                npc.triggerAction(npc.rng().chance(p.courage()) ? "nod" : "shake_head");
            }
        } else if (roll < 0.45 + 0.25 * (1.0 - p.caution() + 0.3)) {
            // small shuffle around the anchor
            Vec3 target = anchor.add((npc.rng().nextDouble() - 0.5) * 2.4, 0, (npc.rng().nextDouble() - 0.5) * 2.4);
            if (npc.moveToward(target, 0.55)) {
                shuffling = true;
            }
        } else if (roll < 0.85) {
            npc.lookAtPos(npc.getEyePosition().add((npc.rng().nextDouble() - 0.5) * 12, (npc.rng().nextDouble() - 0.5) * 2, (npc.rng().nextDouble() - 0.5) * 12));
        }
    }
}
