package com.terracraft.entity.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Walker that climbs walls like a spider (Blood Crawler, later wall creepers). */
public class ClimberMob extends WalkerMob {
    public ClimberMob(EntityType<? extends ClimberMob> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean onClimbable() {
        return horizontalCollision || super.onClimbable();
    }
}
