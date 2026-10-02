package com.terracraft.item.weapon;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.player.ManaManager;
import com.terracraft.player.TerraPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Wands, staffs and spell tomes: spend mana, fire a magic projectile. */
public class MagicWeaponItem extends TerraItem implements UsableWeapon {
    private final ProjectileKind projectile;
    private final int projectiles;
    private final float spread;
    private final SoundEvent sound;

    public MagicWeaponItem(Properties properties, ProjectileKind projectile, int projectiles, float spread) {
        this(properties, projectile, projectiles, spread, SoundEvents.ILLUSIONER_CAST_SPELL);
    }

    public MagicWeaponItem(Properties properties, ProjectileKind projectile, int projectiles, float spread, SoundEvent sound) {
        super(properties);
        this.projectile = projectile;
        this.projectiles = projectiles;
        this.spread = spread;
        this.sound = sound;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return UsableWeapon.use(this, level, player, hand);
    }

    @Override
    public boolean fire(ServerPlayer player, ItemStack stack) {
        TerraItemStats stats = TerraItemStats.of(stack);
        TerraPlayerData data = TerraPlayerData.get(player);
        if (!ManaManager.consume(player, data, stats.mana())) {
            return false;
        }
        WeaponFiring.fire(player.level(), player, projectile, stats.velocity(), projectiles, spread, 1.0F,
            stats.damage(), stats.damageClass(), stats.crit(), stats.knockback());
        WeaponFiring.sound(player, sound, 1.4F);
        return true;
    }
}
