package com.squidgame.game.finale;

import com.squidgame.core.finale.CourtGeometry;
import com.squidgame.core.finale.FinaleRules;
import com.squidgame.core.finale.Motion;
import com.squidgame.entity.ContestantEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * An NPC (or the stand-in of a disconnected player) in the fight. It walks with {@link Motion}, the movement model
 * shared with the headless simulator, written straight into the entity's velocity every tick: no pathfinding is
 * needed on the open court, and the NPC strafes and backs off while facing its opponent like a player does.
 */
final class NpcBody extends FightBody {
    private final ContestantEntity npc;
    private final Motion motion = new Motion();

    NpcBody(ContestantEntity npc) {
        this.npc = npc;
        npc.getNavigation().stop();
        npc.stopMoving();
    }

    ContestantEntity npc() {
        return npc;
    }

    @Override
    LivingEntity entity() {
        return npc;
    }

    @Override
    boolean isPlayer() {
        return false;
    }

    @Override
    void impulse(double ix, double iz) {
        motion.impulse(ix, iz);
    }

    @Override
    void dash(double vx, double vz, int ticks) {
        motion.dash(vx, vz, ticks);
    }

    @Override
    void cancelDash() {
        motion.cancelDash();
    }

    @Override
    void move(double wishX, double wishZ, CourtGeometry court) {
        double[] dash = motion.dashVelocity();
        if (dash != null && court.edgeDistance(npc.getX() + dash[0], npc.getZ() + dash[1]) < FinaleRules.DASH_EDGE) {
            motion.cancelDash();
        }
        double[] d = motion.step(wishX, wishZ);
        Vec3 v = npc.getDeltaMovement();
        npc.setDeltaMovement(d[0], v.y, d[1]);
        npc.getNavigation().stop();
    }

    @Override
    void setSpeedFactor(double f) {
        // nothing to do: the wished velocity the game hands to move() is already scaled by the fighter's state
    }

    @Override
    void release() {
        motion.reset();
    }

    /**
     * Puts the body at a pose of a replayed duel (a ceremony shows a recorded fight, so there is no physics: the pose
     * is taken as it is, without the velocity that would carry it on).
     */
    void place(double x, double y, double z, double yaw) {
        npc.setPos(x, y, z);
        npc.faceYaw((float) yaw);
        Vec3 v = npc.getDeltaMovement();
        npc.setDeltaMovement(0, Math.min(0, v.y), 0);
        npc.getNavigation().stop();
    }
}
