package com.starforged.entity.projectile;

import com.starforged.registry.ModEntities;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The thrown Singularity Grenade. Tears open a black hole where it lands. */
public class ThrownSingularityGrenade extends ThrowableItemProjectile {
    public ThrownSingularityGrenade(EntityType<? extends ThrownSingularityGrenade> type, Level level) {
        super(type, level);
    }

    public ThrownSingularityGrenade(Level level, LivingEntity owner, ItemStack stack) {
        super(ModEntities.THROWN_SINGULARITY.get(), owner, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SINGULARITY_GRENADE.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.level().addParticle(ModParticles.VOID_MOTE.get(), this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel server) {
            Vec3 at = hitResult.getLocation().add(0, 1.2, 0);
            SingularityEntity.spawn(server, at, this.getOwner(), 100, 1.0F);
            this.discard();
        }
    }
}
