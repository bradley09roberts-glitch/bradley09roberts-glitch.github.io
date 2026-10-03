package com.terracraft.item.weapon;

import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.item.TerraItemStats;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.player.stats.Ability;
import net.minecraft.server.level.ServerPlayer;

/** Space Gun: a laser pistol that costs no mana while the full Meteor armor set is worn. */
public class SpaceGunItem extends MagicWeaponItem {
    public SpaceGunItem(Properties properties, ProjectileKind projectile) {
        super(properties, projectile, 1, 0.0F, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST);
    }

    @Override
    protected int manaCost(ServerPlayer player, TerraPlayerData data, TerraItemStats stats) {
        return data.stats().has(Ability.FREE_SPACE_GUN) ? 0 : super.manaCost(player, data, stats);
    }
}
