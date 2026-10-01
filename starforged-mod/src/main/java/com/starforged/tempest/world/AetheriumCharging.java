package com.starforged.tempest.world;

import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestItems;
import com.starforged.tempest.TempestSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Aetherium has to be charged by lightning before it can be worked. Any bolt that lands within three blocks of
 * Aetherium Ingots or Blocks of Aetherium lying on the ground charges them (the bolt can't burn them).
 */
public final class AetheriumCharging {
    private AetheriumCharging() {
    }

    public static void chargeAround(ServerLevel level, Vec3 center, double radius) {
        boolean any = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(radius, radius + 1.0, radius))) {
            ItemStack stack = item.getItem();
            Item charged = stack.is(TempestItems.AETHERIUM_INGOT.get()) ? TempestItems.CHARGED_AETHERIUM_INGOT.get()
                : stack.is(TempestItems.AETHERIUM_BLOCK.get()) ? TempestItems.CHARGED_AETHERIUM_BLOCK.get() : null;
            item.setInvulnerable(true);
            if (charged == null) {
                continue;
            }
            item.setItem(new ItemStack(charged, stack.getCount()));
            item.setDeltaMovement(item.getDeltaMovement().add(0, 0.25, 0));
            level.sendParticles(ModParticles.STATIC_SPARK.get(), item.getX(), item.getY() + 0.3, item.getZ(), 25, 0.2, 0.3, 0.2, 0.15);
            any = true;
        }
        if (any) {
            level.playSound(null, center.x, center.y, center.z, TempestSounds.CHARGE.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
        }
    }
}
