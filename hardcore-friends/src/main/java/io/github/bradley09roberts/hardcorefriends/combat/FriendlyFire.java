package io.github.bradley09roberts.hardcorefriends.combat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Wildlife;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Makes friendly fire impossible, and keeps arrow hits counting as a friend's blows. Every bit of damage whose source
 * (or the owner of the arrow that dealt it) is a friend is checked before it lands: it is cancelled outright when the
 * victim is a player, another friend, a villager or wandering trader, a golem, an allay, an armour stand, or an animal
 * that is somebody's (named, tamed or owned, on a lead, ridden, saddled or armoured). Melee already refuses players and
 * friends, and the archers check their line of fire; this is the last word whatever happens (a stray arrow, a
 * deflection, a thorns enchantment on a friend's armour hitting a player who struck them).
 *
 * <p>Damage a friend deals to a hostile is credited to a nearby player ({@link KillCredit}), and a friend's arrow that
 * hits tells the {@code CompanionEvents.HIT} listeners, as a melee blow does; an arrow that kills a hostile earns the
 * team Unity as a killing blow in melee does.
 */
public final class FriendlyFire {
	private FriendlyFire() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(FriendlyFire::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (victim.isAlive()) {
				arrowLanded(victim, source, false);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> arrowLanded(victim, source, true));
	}

	/** False to cancel damage a friend would deal to someone they must never hurt. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof CompanionEntity friend)) {
			return true;
		}
		if (isProtected(victim)) {
			return false;
		}
		if (victim instanceof Enemy) {
			KillCredit.credit(friend, victim);
		}
		return true;
	}

	/**
	 * Who a friend must never hurt: players, friends, villagers and traders, golems, allays, armour stands, and anybody's
	 * animal (see the class description). Hostile mobs, even named ones, are fair game.
	 */
	public static boolean isProtected(Entity victim) {
		if (victim instanceof Player || victim instanceof CompanionEntity || victim instanceof AbstractVillager
			|| victim instanceof AbstractGolem || victim instanceof Allay || victim instanceof ArmorStand) {
			return true;
		}
		if (victim instanceof Enemy) {
			return false;
		}
		if (victim instanceof Animal animal) {
			return Wildlife.isSomebodys(animal);
		}
		if (victim.hasCustomName() || victim instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
			return true;
		}
		return victim instanceof Mob mob && mob.isLeashed();
	}

	/** A friend's arrow hit (or killed) a mob: the HIT listeners hear of it, and a kill of a hostile earns Unity. */
	private static void arrowLanded(LivingEntity victim, DamageSource source, boolean killed) {
		if (!(source.getDirectEntity() instanceof Projectile) || !(source.getEntity() instanceof CompanionEntity friend)
			|| !(victim.level() instanceof ServerLevel level) || friend.level() != level) {
			return;
		}
		if (killed && victim instanceof Enemy) {
			Unity.add(level, Unity.DEFENCE, 3, 60);
			Camp.data(level.getServer()).addStat("mobs_defeated", 1);
		}
		friend.onHitLanded(level, victim);
	}
}
