package com.terracraft.item.summon;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.weapon.UsableWeapon;
import com.terracraft.item.weapon.WeaponFiring;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * A whip: each swing lashes out and back (a {@link ProjectileKind.Behavior#WHIP} projectile), striking everything
 * along the way with summon damage and tagging it for the player's minions.
 */
public class WhipItem extends TerraItem implements UsableWeapon {
    private final Supplier<ProjectileKind> lash;

    public WhipItem(Properties properties, Supplier<ProjectileKind> lash) {
        super(properties);
        this.lash = lash;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return UsableWeapon.use(this, level, player, hand);
    }

    @Override
    public boolean fire(ServerPlayer player, ItemStack stack) {
        TerraItemStats stats = TerraItemStats.of(stack);
        TerrariaProjectile.shoot(player.level(), player, lash.get(), WeaponFiring.muzzle(player), player.getLookAngle(), 0.1F, 0.0F,
            stats.damage(), stats.damageClass(), com.terracraft.combat.DamageCalc.BASE_CRIT + stats.crit(), stats.knockback());
        WeaponFiring.sound(player, SoundEvents.FISHING_BOBBER_THROW, 1.4F);
        return true;
    }
}
