package com.terracraft.entity.mob;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.registry.content.MobContent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Prismatic Lacewing: a glowing rainbow moth that flutters about the Hallow at night after Plantera. Killing it
 * calls down the Empress of Light.
 */
public class PrismaticLacewing extends TerrariaMob {
    private Vec3 heading = Vec3.ZERO;

    public PrismaticLacewing(EntityType<? extends PrismaticLacewing> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean countsTowardSpawnCap() {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        if (tickCount % 30 == 0 || heading == Vec3.ZERO) {
            heading = new Vec3(random.nextDouble() - 0.5, (random.nextDouble() - 0.45) * 0.6, random.nextDouble() - 0.5).normalize().scale(0.06);
        }
        Vec3 motion = getDeltaMovement().scale(0.9).add(heading).add(0, Mth.sin(tickCount * 0.3F) * 0.02, 0);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        setYRot((float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F);
        yBodyRot = getYRot();
        if (tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.2, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && source.getEntity() instanceof ServerPlayer player) {
            BossSummoning.summonAt(level, player, MobContent.EMPRESS_OF_LIGHT.get(), getX(), getY() + 3, getZ());
        }
    }
}
