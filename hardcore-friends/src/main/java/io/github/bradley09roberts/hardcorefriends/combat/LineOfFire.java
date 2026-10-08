package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * A clear line of fire. A friend never looses an arrow while anyone or anything that is not a hostile stands in or near
 * the arrow's flight: a player, another friend, a villager, an iron golem, an animal (the camp's, a player's pet, or
 * a wild one), an armour stand, a boat or a cart. The flight is the arrow's arc from the friend's eyes to the target
 * (aimed above the target to allow for the drop, as the shot is) and on a few blocks beyond it, in case it misses;
 * anything within {@value #CLEARANCE} blocks of it blocks the shot. Hostiles near the line do not: hitting one of
 * those does no harm. Friendly fire is also cancelled outright ({@link FriendlyFire}); this check is there so the
 * friends do not even try.
 */
public final class LineOfFire {
	/** How close to the arrow's flight anything that is not hostile may stand. */
	private static final double CLEARANCE = 1.5;
	/** How far past the target a missed arrow is followed. */
	private static final double OVERSHOOT = 5;
	/** Points checked along the flight, per block. */
	private static final double SAMPLES_PER_BLOCK = 2;

	private LineOfFire() {
	}

	/** True when nothing but hostiles stands in or near the arrow's flight from the friend to the target. */
	public static boolean clear(CompanionEntity c, LivingEntity target) {
		Vec3 from = new Vec3(c.getX(), c.getEyeY() - 0.1, c.getZ());
		Vec3 to = new Vec3(target.getX(), target.getY(0.3333333333333333), target.getZ());
		Vec3 line = to.subtract(from);
		double length = line.length();
		if (length < 0.5) {
			return true;
		}
		Vec3 dir = line.scale(1.0 / length);
		Vec3 end = to.add(dir.scale(OVERSHOOT));
		// The arrow rises above the straight line by about a twentieth of the distance mid-way (it is aimed high).
		double arc = Math.sqrt(line.x * line.x + line.z * line.z) * 0.05;
		AABB box = new AABB(from, end).inflate(CLEARANCE + 1.0).expandTowards(0, arc + 1.0, 0);
		List<Entity> near = c.level().getEntities(c, box, e -> e != target && blocks(e));
		if (near.isEmpty()) {
			return true;
		}
		double total = length + OVERSHOOT;
		int samples = (int) Math.ceil(total * SAMPLES_PER_BLOCK);
		for (Entity e : near) {
			AABB body = e.getBoundingBox().inflate(CLEARANCE);
			for (int i = 0; i <= samples; i++) {
				double along = total * i / samples;
				double f = Math.min(1.0, along / length);
				Vec3 p = from.add(dir.scale(along)).add(0, 4 * arc * f * (1 - f), 0);
				if (body.contains(p)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Anything an arrow could hit that is not a hostile (projectiles, dropped items and orbs aside). */
	private static boolean blocks(Entity e) {
		if (e instanceof Projectile || e instanceof ItemEntity || e instanceof ExperienceOrb || !e.isAlive() || e.isSpectator()) {
			return false;
		}
		if (!e.canBeHitByProjectile() && !(e instanceof LivingEntity)) {
			return false;
		}
		return !(e instanceof LivingEntity living && Threats.isThreat(living));
	}
}
