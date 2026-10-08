package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Walks a friend over to a player or another friend and notices when that stops working. The plain entity walk keeps
 * re-pathing forever, so a target on a roof or a pillar would be chased until the task timed out. Here the walk counts
 * as stuck once the friend has gone {@value #STUCK_LIMIT} ticks without getting at least a quarter of a block
 * closer. Keep one per task and call {@link #reset()} when the task starts.
 */
public final class EntityApproach {
	/** Ticks without getting closer before the target counts as out of reach. */
	public static final int STUCK_LIMIT = 120;
	private static final double PROGRESS = 0.25;

	private @Nullable UUID target;
	private double bestDistance = Double.MAX_VALUE;
	private int noProgressTicks;

	/** Walks towards {@code entity} until within {@code reach} blocks. Returns true once there. Call every tick. */
	public boolean walk(CompanionEntity c, Entity entity, double reach) {
		if (!entity.getUUID().equals(target)) {
			reset();
			target = entity.getUUID();
		}
		if (c.actions().walkToEntity(entity, reach)) {
			bestDistance = Double.MAX_VALUE;
			noProgressTicks = 0;
			return true;
		}
		double dist = c.distanceTo(entity);
		if (dist < bestDistance - PROGRESS) {
			bestDistance = dist;
			noProgressTicks = 0;
		} else {
			noProgressTicks++;
		}
		return false;
	}

	/** True once the walk has stopped getting the friend any closer. */
	public boolean isStuck() {
		return noProgressTicks > STUCK_LIMIT;
	}

	public void reset() {
		target = null;
		bestDistance = Double.MAX_VALUE;
		noProgressTicks = 0;
	}
}
