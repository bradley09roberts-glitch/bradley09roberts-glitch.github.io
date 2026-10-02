package com.terracraft.item.weapon;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Thrown weapons. Consumable ones (shuriken, throwing knives) use up the stack; returning ones
 * (boomerangs) are limited to one projectile in flight, as in Terraria.
 */
public class ThrownWeaponItem extends TerraItem implements UsableWeapon {
    private final ProjectileKind projectile;
    private final boolean consumable;

    public ThrownWeaponItem(Properties properties, ProjectileKind projectile, boolean consumable) {
        super(properties);
        this.projectile = projectile;
        this.consumable = consumable;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return UsableWeapon.use(this, level, player, hand);
    }

    @Override
    public boolean fire(ServerPlayer player, ItemStack stack) {
        if (!consumable) {
            boolean inFlight = !player.level().getEntitiesOfClass(TerrariaProjectile.class, player.getBoundingBox().inflate(64),
                p -> p.getOwner() == player && p.kind() == projectile).isEmpty();
            if (inFlight) {
                return false;
            }
        }
        TerraItemStats stats = TerraItemStats.of(stack);
        WeaponFiring.fire(player.level(), player, projectile, stats.velocity(), 1, 0.0F, 0.5F,
            stats.damage(), stats.damageClass(), stats.crit(), stats.knockback());
        WeaponFiring.sound(player, SoundEvents.WITCH_THROW, 1.2F);
        if (consumable && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return true;
    }
}
