package com.squidgame.entity;

import com.squidgame.registry.ModItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A thrown marble (target-throw variant of the marbles game); landing position and scoring are handled by the game. */
public class MarbleProjectile extends ThrowableItemProjectile {
    public MarbleProjectile(EntityType<? extends MarbleProjectile> type, Level level) {
        super(type, level);
    }

    public MarbleProjectile(Level level, LivingEntity owner) {
        super(com.squidgame.registry.ModEntities.MARBLE, owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.MARBLE;
    }
}
