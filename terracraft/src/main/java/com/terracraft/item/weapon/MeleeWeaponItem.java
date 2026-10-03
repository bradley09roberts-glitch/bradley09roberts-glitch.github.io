package com.terracraft.item.weapon;

import com.terracraft.item.TerraItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Swords, shortswords and other swung weapons. Damage, speed and knockback are main-hand attribute
 * modifiers created by {@link WeaponProperties#melee}; crits and class bonuses come from the damage pipeline.
 * An optional {@link OnHit} adds Terraria on-hit effects (Blade of Grass poison, Bee Keeper bees).
 */
public class MeleeWeaponItem extends TerraItem {
    @FunctionalInterface
    public interface OnHit {
        void apply(ItemStack stack, LivingEntity target, LivingEntity attacker);
    }

    private final @Nullable OnHit onHit;

    public MeleeWeaponItem(Properties properties) {
        this(properties, null);
    }

    public MeleeWeaponItem(Properties properties, @Nullable OnHit onHit) {
        super(properties);
        this.onHit = onHit;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        if (onHit != null && !attacker.level().isClientSide()) {
            onHit.apply(stack, target, attacker);
        }
    }
}
