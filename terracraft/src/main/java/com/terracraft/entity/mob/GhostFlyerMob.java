package com.terracraft.entity.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A flyer that drifts through walls (Cursed Skull, later ghosts and spirits). */
public class GhostFlyerMob extends FlyerMob {
    public GhostFlyerMob(EntityType<? extends GhostFlyerMob> type, Level level, Style style) {
        super(type, level, style);
        noPhysics = true;
    }
}
