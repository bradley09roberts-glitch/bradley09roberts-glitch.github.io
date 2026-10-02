package com.terracraft.item.weapon;

import com.terracraft.item.TerraItem;
import com.terracraft.item.TerraItemStats;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.player.stats.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bows, guns and launchers. Terraria-style: no charging, a fixed use time, ammo consumed from the
 * inventory. Shot damage = weapon damage + ammo damage; the ammo chooses the projectile.
 */
public class RangedWeaponItem extends TerraItem implements UsableWeapon {
    private final AmmoType ammoType;
    private final SoundEvent sound;
    private final float inaccuracy;
    private final int projectiles;
    private final float spread;

    public RangedWeaponItem(Properties properties, AmmoType ammoType, SoundEvent sound, float inaccuracy, int projectiles, float spread) {
        super(properties);
        this.ammoType = ammoType;
        this.sound = sound;
        this.inaccuracy = inaccuracy;
        this.projectiles = projectiles;
        this.spread = spread;
    }

    public AmmoType ammoType() {
        return ammoType;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return UsableWeapon.use(this, level, player, hand);
    }

    @Override
    public boolean fire(ServerPlayer player, ItemStack stack) {
        ItemStack ammoStack = AmmoRegistry.find(player, ammoType);
        AmmoInfo ammo = AmmoRegistry.info(ammoStack);
        if (ammo == null) {
            player.sendOverlayMessage(Component.translatable("message.terracraft.no_ammo",
                Component.translatable("ammo.terracraft." + ammoType.name().toLowerCase(java.util.Locale.ROOT))).withStyle(ChatFormatting.RED));
            return false;
        }
        TerraItemStats stats = TerraItemStats.of(stack);
        WeaponFiring.fire(player.level(), player, ammo.projectile(), stats.velocity() + ammo.velocityBonus(), projectiles, spread,
            inaccuracy, stats.damage() + ammo.damage(), stats.damageClass(), stats.crit(), stats.knockback() + ammo.knockback());
        WeaponFiring.sound(player, sound, 1.0F);
        float conservation = TerraPlayerData.get(player).stats().get(Stat.AMMO_CONSERVATION);
        if (!player.getAbilities().instabuild && player.getRandom().nextFloat() >= conservation) {
            ammoStack.shrink(1);
        }
        return true;
    }
}
