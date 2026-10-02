package com.terracraft.entity.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/** A slime that bursts into smaller slimes when it dies (Mother Slime -> 2-3 Baby Slimes). */
public class MotherSlimeMob extends SlimeMob {
    private final Supplier<? extends EntityType<? extends TerrariaMob>> child;
    private final int minChildren;
    private final int maxChildren;

    public MotherSlimeMob(EntityType<? extends SlimeMob> type, Level level, Supplier<? extends EntityType<? extends TerrariaMob>> child,
                          int minChildren, int maxChildren) {
        super(type, level);
        this.child = child;
        this.minChildren = minChildren;
        this.maxChildren = maxChildren;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            int count = minChildren + random.nextInt(maxChildren - minChildren + 1);
            for (int i = 0; i < count; i++) {
                TerrariaMob baby = child.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (baby != null) {
                    baby.snapTo(new net.minecraft.world.phys.Vec3(getX() + (random.nextDouble() - 0.5), getY() + 0.2, getZ() + (random.nextDouble() - 0.5)), random.nextFloat() * 360.0F, 0.0F);
                    baby.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                    baby.setDeltaMovement((random.nextDouble() - 0.5) * 0.4, 0.3, (random.nextDouble() - 0.5) * 0.4);
                    level.addFreshEntity(baby);
                }
            }
        }
    }
}
