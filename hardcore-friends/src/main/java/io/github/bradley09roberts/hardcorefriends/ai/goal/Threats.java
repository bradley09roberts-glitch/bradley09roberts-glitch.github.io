package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/** Recognises hostile mobs worth reacting to. */
public final class Threats {
	private Threats() {
	}

	/** Hostile mobs, plus neutral mobs (endermen, piglins) only once they are angry at someone. */
	public static boolean isThreat(LivingEntity e) {
		if (!e.isAlive() || !(e instanceof Enemy) || e instanceof CompanionEntity) {
			return false;
		}
		if (e instanceof NeutralMob && e instanceof Mob mob) {
			return mob.getTarget() != null;
		}
		return true;
	}

	public static List<LivingEntity> around(LivingEntity centre, double radius) {
		AABB box = centre.getBoundingBox().inflate(radius, radius / 2, radius);
		return centre.level().getEntitiesOfClass(LivingEntity.class, box, Threats::isThreat);
	}

	public static @Nullable LivingEntity nearest(LivingEntity centre, double radius) {
		LivingEntity best = null;
		double bestDist = radius * radius;
		for (LivingEntity e : around(centre, radius)) {
			double d = e.distanceToSqr(centre);
			if (d < bestDist) {
				best = e;
				bestDist = d;
			}
		}
		return best;
	}

	public static @Nullable Creeper nearestCreeper(LivingEntity centre, double radius) {
		Creeper best = null;
		double bestDist = radius * radius;
		AABB box = centre.getBoundingBox().inflate(radius, radius / 2, radius);
		for (Creeper c : centre.level().getEntitiesOfClass(Creeper.class, box, Creeper::isAlive)) {
			double d = c.distanceToSqr(centre);
			if (d < bestDist) {
				best = c;
				bestDist = d;
			}
		}
		return best;
	}

	/** Mobs that attack from a distance: archers, crossbow and trident users, witches, blazes, ghasts, breezes, shulkers. */
	public static boolean isRanged(LivingEntity e) {
		return e instanceof RangedAttackMob || e instanceof Blaze || e instanceof Ghast || e instanceof Breeze || e instanceof Shulker;
	}

	/** The nearest ranged attacker within {@code radius} that is aiming at {@code victim} and can see them, or null. */
	public static @Nullable LivingEntity nearestShooter(LivingEntity victim, double radius) {
		LivingEntity best = null;
		double bestDist = radius * radius;
		for (LivingEntity e : around(victim, radius)) {
			double d = e.distanceToSqr(victim);
			if (d < bestDist && isRanged(e) && e instanceof Mob mob && mob.getTarget() == victim
				&& mob.getSensing().hasLineOfSight(victim)) {
				best = e;
				bestDist = d;
			}
		}
		return best;
	}

	/** The nearest ranged attacker within {@code radius} in plain sight of {@code centre}, aiming at them or not. */
	public static @Nullable LivingEntity nearestArcher(LivingEntity centre, double radius) {
		LivingEntity best = null;
		double bestDist = radius * radius;
		for (LivingEntity e : around(centre, radius)) {
			double d = e.distanceToSqr(centre);
			if (d < bestDist && isRanged(e) && centre.hasLineOfSight(e)) {
				best = e;
				bestDist = d;
			}
		}
		return best;
	}

	/** True if a ranged attacker stands within {@code radius} of {@code pos}. */
	public static boolean archerNear(ServerLevel level, BlockPos pos, double radius) {
		AABB box = new AABB(pos).inflate(radius, radius / 2, radius);
		double r2 = radius * radius;
		return !level.getEntitiesOfClass(LivingEntity.class, box,
			e -> isThreat(e) && isRanged(e) && e.distanceToSqr(Vec3.atBottomCenterOf(pos)) < r2).isEmpty();
	}

	/** True if the mob is currently trying to hurt this entity. */
	public static boolean isTargeting(LivingEntity attacker, LivingEntity victim) {
		return attacker instanceof Mob mob && mob.getTarget() == victim;
	}

	public static ServerLevel level(LivingEntity e) {
		return (ServerLevel) e.level();
	}
}
