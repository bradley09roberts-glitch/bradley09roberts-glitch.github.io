package io.github.bradley09roberts.hardcorefriends.expedition;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * A friend's arrow that can hurt the ender dragon. In 26.3 the dragon only takes damage from players or from damage
 * types in {@code minecraft:always_hurts_ender_dragons}, so a friend's ordinary arrow bounces off. This one is an
 * ordinary arrow in every way (the same entity type, so it looks, saves and is picked up like any other) except that
 * a hit on the dragon is dealt with the mod's own arrow damage type, which the data pack adds to that tag. Damage is
 * worked out as the game works out an arrow's (speed times base damage, the bow's enchantments, a critical bonus),
 * and the dragon's own rules still apply (a hit on anything but the head counts for a quarter, arrows do nothing
 * while it sits on the portal). Anything else it hits is hit as by any arrow.
 */
final class DragonArrow extends Arrow {
	/** The arrow's base damage, kept here because the game's own copy is private. */
	private double damage = 2.0;

	DragonArrow(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
		super(level, owner, pickup, weapon);
	}

	@Override
	public void setBaseDamage(double baseDamage) {
		super.setBaseDamage(baseDamage);
		this.damage = baseDamage;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		Entity entity = hit.getEntity();
		EnderDragon dragon = entity instanceof EnderDragonPart part ? part.parentMob : entity instanceof EnderDragon d ? d : null;
		if (dragon == null || !(getOwner() instanceof CompanionEntity friend) || !(level() instanceof ServerLevel level)) {
			super.onHitEntity(hit);
			return;
		}
		DamageSource source = DragonFight.arrowSource(level, this, friend);
		if (source == null) {
			super.onHitEntity(hit);
			return;
		}
		double base = damage;
		ItemStack weapon = getWeaponItem();
		if (weapon != null) {
			base = EnchantmentHelper.modifyDamage(level, weapon, entity, source, (float) base);
		}
		float speed = (float) getDeltaMovement().length();
		int amount = Mth.ceil(Mth.clamp(speed * base, 0.0, 2.147483647E9));
		if (isCritArrow()) {
			amount = (int) Math.min(amount + (long) random.nextInt(amount / 2 + 2), Integer.MAX_VALUE);
		}
		friend.setLastHurtMob(dragon);
		if (entity instanceof EnderDragonPart part) {
			dragon.hurt(level, part, source, amount);
		} else {
			dragon.hurtServer(level, source, amount);
		}
		playSound(SoundEvents.ARROW_HIT, 1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F));
		discard();
	}
}
