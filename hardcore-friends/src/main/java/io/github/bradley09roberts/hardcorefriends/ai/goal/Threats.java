package io.github.bradley09roberts.hardcorefriends.ai.goal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

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

	/** True if the mob is currently trying to hurt this entity. */
	public static boolean isTargeting(LivingEntity attacker, LivingEntity victim) {
		return attacker instanceof Mob mob && mob.getTarget() == victim;
	}

	public static ServerLevel level(LivingEntity e) {
		return (ServerLevel) e.level();
	}
}
