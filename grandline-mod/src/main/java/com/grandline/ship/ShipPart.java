package com.grandline.ship;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.entity.PartEntity;

/** One collidable block of a ship's hull/deck (axis-aligned box that follows the rotating ship). */
public class ShipPart extends PartEntity<ShipEntity> {
    public final float localX, localZ;   // ship-local offset (x = starboard, z = toward bow)
    public final float top;              // deck height above the ship's waterline origin
    private final EntityDimensions size;

    public ShipPart(ShipEntity ship, float localX, float localZ, float width, float height, float top) {
        super(ship);
        this.localX = localX;
        this.localZ = localZ;
        this.top = top;
        this.size = EntityDimensions.scalable(width, height);
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
        return size;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !getParent().isSinking();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        return getParent().hurtPart(this, src, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interactPart(this, player, hand);
    }

    @Override
    public boolean is(Entity e) {
        return this == e || getParent() == e;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
