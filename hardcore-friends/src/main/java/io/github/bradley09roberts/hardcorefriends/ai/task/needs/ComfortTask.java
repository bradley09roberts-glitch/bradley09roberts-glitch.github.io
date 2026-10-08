package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * A cold, wet or miserable friend goes somewhere cosy: they huddle by a lit campfire or step inside the cabin,
 * whichever is nearer, and stay until they have warmed up. Comfort then climbs on its own (the surroundings are cosy)
 * with a little extra for taking the time to settle.
 */
public final class ComfortTask implements CompanionTask {
	/** Below this comfort a friend looks for somewhere cosy. */
	static final double CHILLY = 30;
	/** Warm enough to go back to work. */
	static final double WARM = 65;
	/** Extra comfort per second while settled somewhere cosy, on top of the cosy surroundings themselves. */
	static final double SETTLE_BONUS = 1.0;
	private static final int STAY_TICKS = 20 * 30;
	private static final int WALK_TICKS = 20 * 25;
	private static final int RESCAN = 20 * 10;

	private @Nullable BlockPos spot;
	private @Nullable BlockPos fire;
	private boolean arrived;
	private int ticks;
	private @Nullable BlockPos knownFire;
	private long scannedAt = -100_000;

	@Override
	public String id() {
		return "needs.cosy";
	}

	@Override
	public String describe() {
		return fire != null ? "warming up by the fire" : "warming up indoors";
	}

	@Override
	public double score(CompanionEntity c) {
		double comfort = c.needs().get(Need.COMFORT);
		if (comfort >= CHILLY) {
			return 0;
		}
		if (cachedFire(c) == null && !Spots.cabinBuilt(c)) {
			return 0; // nowhere cosy to go yet
		}
		return 35 + (CHILLY - comfort); // 35 when a little chilly, up to 65
	}

	/** The lit campfire to warm up at, rescanned every ten seconds at most. */
	private @Nullable BlockPos cachedFire(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (now - scannedAt >= RESCAN || knownFire != null && !Spots.isLitCampfire(level.getBlockState(knownFire))) {
			scannedAt = now;
			knownFire = Spots.litCampfire(c);
		}
		return knownFire;
	}

	@Override
	public boolean start(CompanionEntity c) {
		arrived = false;
		ticks = 0;
		spot = null;
		fire = null;
		BlockPos campfire = cachedFire(c);
		BlockPos fireSpot = campfire == null ? null : Spots.beside(c, campfire, 2);
		if (fireSpot == null && campfire != null) {
			fireSpot = Spots.beside(c, campfire, 1);
		}
		BlockPos cabinSpot = Spots.cabinBuilt(c) ? c.restPos() : null;
		if (fireSpot != null && (cabinSpot == null || fireSpot.distSqr(c.blockPosition()) <= cabinSpot.distSqr(c.blockPosition()))) {
			spot = fireSpot;
			fire = campfire;
		} else {
			spot = cabinSpot;
		}
		return spot != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (spot == null) {
			return TaskStatus.FAILURE;
		}
		if (!arrived) {
			if (c.actions().walkTo(spot, 1.0)) {
				arrived = true;
				ticks = 0;
				c.setPose(Pose.CROUCHING); // huddled up, hands to the warmth
				Speech.say(c, Line.COSY);
			} else if (c.actions().isStuck() || ++ticks > WALK_TICKS) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (fire != null) {
			ServerLevel level = (ServerLevel) c.level();
			if (!Spots.isLitCampfire(level.getBlockState(fire))) {
				return TaskStatus.FAILURE; // the fire went out
			}
			c.getLookControl().setLookAt(Vec3.atCenterOf(fire));
		}
		c.needs().add(Need.COMFORT, SETTLE_BONUS / 20.0);
		return ++ticks >= STAY_TICKS || c.needs().get(Need.COMFORT) >= WARM ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		if (c.getPose() == Pose.CROUCHING) {
			c.setPose(Pose.STANDING);
		}
		spot = null;
		fire = null;
		arrived = false;
		ticks = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 20;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return WALK_TICKS + STAY_TICKS + 20 * 5;
	}
}
