package com.terracraft.entity.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;

/** A walker that blinks to a spot near its target every few seconds (Chaos Elemental). */
public class TeleporterMob extends WalkerMob {
    private int teleportTimer = 60;

    public TeleporterMob(EntityType<? extends TeleporterMob> type, Level level) {
        super(type, level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || --teleportTimer > 0) {
            return;
        }
        teleportTimer = 80 + random.nextInt(60);
        for (int attempt = 0; attempt < 8; attempt++) {
            double x = target.getX() + (random.nextDouble() - 0.5) * 12;
            double z = target.getZ() + (random.nextDouble() - 0.5) * 12;
            double y = target.getY() + random.nextInt(5) - 2;
            double ox = getX(), oy = getY(), oz = getZ();
            if (randomTeleport(x, y, z, false)) {
                level.sendParticles(ParticleTypes.WITCH, ox, oy + 1, oz, 20, 0.3, 0.6, 0.3, 0.05);
                level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.05);
                playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.3F);
                break;
            }
        }
    }
}
