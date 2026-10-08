package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * A friend too weak to work ({@link CompanionEntity#tooWeakToWork}: badly hurt and too hungry to heal, as a starving
 * friend soon is) comes home and rests: they sit by the lit campfire, or at their own sleeping place, out of harm's
 * way. At night they sleep instead ({@link SleepTask} scores higher), and the moment there is food the eating job
 * takes over ({@link EatTask} scores far higher). Meanwhile the scheduler keeps them off any work that could lead them
 * into danger; growing food in the camp scores above resting, so a starving camp can still feed itself.
 *
 * <p>Resting cannot heal a starving friend, but it keeps a friend on one heart where help is, instead of in the mine
 * or out in the woods.
 */
public final class RestTask implements CompanionTask {
	/** Just above idling: a weak friend still eats, sleeps or grows food when they can, and rests otherwise. */
	static final double SCORE = 8;
	private static final int STAY_TICKS = 20 * 30;
	private static final int WALK_TICKS = 20 * 45;
	private static final double REACH = 1.0;

	private @Nullable BlockPos spot;
	private @Nullable BlockPos fire;
	private boolean arrived;
	private int ticks;

	@Override
	public String id() {
		return "needs.rest";
	}

	@Override
	public String describe() {
		if (!arrived) {
			return "going home to rest";
		}
		return fire != null ? "resting by the fire" : "resting";
	}

	@Override
	public double score(CompanionEntity c) {
		return c.tooWeakToWork() ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		arrived = false;
		ticks = 0;
		fire = Spots.litCampfire(c);
		spot = fire != null ? Spots.beside(c, fire, 2) : null;
		if (spot == null) {
			fire = null;
			spot = SleepTask.bedFor(c);
		}
		return spot != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (spot == null) {
			return TaskStatus.FAILURE;
		}
		if (!c.tooWeakToWork()) {
			return TaskStatus.SUCCESS; // fed or healed: back to ordinary life
		}
		if (!arrived) {
			boolean there = c.actions().walkTo(spot, REACH);
			if (!there && (c.actions().isStuck() || ++ticks > WALK_TICKS)) {
				if (c.blockPosition().distSqr(spot) > 16) {
					return TaskStatus.FAILURE;
				}
				there = true; // close enough: rest here
			}
			if (there) {
				arrived = true;
				ticks = 0;
				c.actions().stopWalking();
				c.setPose(Pose.CROUCHING); // sitting down, worn out
			}
			return TaskStatus.RUNNING;
		}
		c.getNavigation().stop();
		if (fire != null) {
			c.getLookControl().setLookAt(Vec3.atCenterOf(fire));
		}
		return ++ticks >= STAY_TICKS ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
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
		return 40;
	}

	@Override
	public int maxTicks() {
		return WALK_TICKS + STAY_TICKS + 20 * 5;
	}
}
