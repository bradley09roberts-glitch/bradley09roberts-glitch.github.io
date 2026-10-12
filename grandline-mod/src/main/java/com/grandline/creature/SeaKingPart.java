package com.grandline.creature;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One body segment of a Sea King: a collidable, hittable box that follows the serpent's swimming path. Damage is
 * forwarded to the Sea King itself (like {@link com.grandline.ship.ShipPart} does for ships).
 */
public class SeaKingPart extends PartEntity<SeaKing> {
    public final int index;

    public SeaKingPart(SeaKing parent, int index) {
        super(parent);
        this.index = index;
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        SeaKing p = getParent();
        if (p == null) return EntityDimensions.scalable(1, 1);
        float d = SeaKing.diameter(index) * p.getScale();
        return EntityDimensions.scalable(d * 1.05f, d);
    }

    @Override
    public boolean canBeCollidedWith() {
        return getParent().isAlive();
    }

    @Override
    public boolean isPickable() {
        return getParent().isAlive();
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return !isInvulnerableTo(src) && getParent().hurtFromPart(this, src, amount);
    }

    @Override
    public boolean is(Entity e) {
        return this == e || getParent() == e;
    }

    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
