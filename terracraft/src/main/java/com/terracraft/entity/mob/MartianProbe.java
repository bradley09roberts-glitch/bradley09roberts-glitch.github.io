package com.terracraft.entity.mob;

import com.terracraft.world.event.EventManager;
import com.terracraft.world.event.TerrariaEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Martian Probe (after Golem): drifts in and scans the first player it finds. Once the scan is done it flees
 * upward; if it gets away, Martian Madness begins. Shoot it down first to stop the invasion.
 */
public class MartianProbe extends TerrariaMob {
    private static final int SCAN_TICKS = 100;
    private int scanned;
    private boolean fleeing;
    private int fleeTicks;

    public MartianProbe(EntityType<? extends MartianProbe> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        if (fleeing) {
            Vec3 motion = getDeltaMovement().scale(0.95).add(0, 0.06, 0);
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            if (++fleeTicks > 120) {
                if (EventManager.active(level.getServer()) == null) {
                    EventManager.start(level.getServer(), TerrariaEvents.MARTIAN_MADNESS);
                }
                discard();
            }
            return;
        }
        Player target = level.getNearestPlayer(this, 40);
        if (target == null || target.isSpectator()) {
            setDeltaMovement(getDeltaMovement().scale(0.9));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        Vec3 goal = target.position().add(0, 6, 0);
        Vec3 motion = getDeltaMovement().scale(0.9).add(goal.subtract(position()).normalize().scale(0.03));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (distanceToSqr(target) < 20 * 20 && hasLineOfSight(target)) {
            scanned++;
            if (scanned % 10 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.8, 0.3, 0.05);
                playSound(SoundEvents.BEACON_AMBIENT, 1.0F, 2.0F);
            }
            if (scanned >= SCAN_TICKS) {
                fleeing = true;
                target.sendSystemMessage(Component.translatable("event.terracraft.martian_madness.probe").withStyle(ChatFormatting.AQUA));
            }
        }
    }
}
