package io.github.bradley09roberts.hardcorefriends.people;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Children go home early. From the end of the afternoon (time of day {@value #FROM}) until nightfall a child goes
 * home, to beside their own bed in the family's village home (or just inside its door), or, without a house, to the
 * camp's resting place (the cabin), and stays in quietly, so they are indoors before the monsters come out; at
 * nightfall the sleep job takes over and puts them to bed, a step away.
 * Changes no block.
 */
final class ChildHomeTask implements CompanionTask {
	static final String ID = People.JOB_PREFIX + "child_home";
	/** Home time. */
	static final long FROM = 11000;
	private static final double SCORE = 70;

	private @Nullable BlockPos home;
	private boolean arrived;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return arrived ? "staying in for the evening" : "going home for the evening";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!c.isChild() || !(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		long time = Camp.timeOfDay(level);
		return time >= FROM && time < 13000 && !Camp.isNightTime(level) ? SCORE : 0;
	}

	/** Somewhere in their own house if the village gave them one, otherwise the camp's resting place (the cabin). */
	static BlockPos homeSpot(CompanionEntity c) {
		BlockPos house = houseSpot(c);
		return house != null ? house : c.restPos();
	}

	/**
	 * A spot to stand in the child's own house: beside their bed (either half, nearest the child, as the sleep job
	 * stands by it), else just inside the front door, else on the doorstep. Null without a house, or with it not loaded or
	 * in another world. A handful of block reads; call it when a job starts or a refuge is picked, not every tick.
	 */
	static @Nullable BlockPos houseSpot(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return null;
		}
		BlockPos bed = Homes.get().bedFor(c).orElse(null);
		if (bed != null && level.isLoaded(bed)) {
			BlockPos beside = besideBed(level, c, bed);
			if (beside != null) {
				return beside;
			}
		}
		Homes.Home home = Homes.get().homeOf(level.getServer(), c.getUUID()).orElse(null);
		if (home == null || !home.dimension().equals(Camp.dimensionId(level)) || !level.isLoaded(home.door())) {
			return null;
		}
		// The plan's door spot is the doorstep outside: the door is beside it, and the room beyond the door.
		BlockPos step = home.door();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos door = step.relative(d);
			BlockPos in = door.relative(d);
			if (level.isLoaded(in) && level.getBlockState(door).getBlock() instanceof DoorBlock && Spots.isStandable(level, in)
				&& !level.canSeeSky(in.above())) {
				return in;
			}
		}
		return Spots.isStandable(level, step) ? step : null;
	}

	/**
	 * A standable spot beside this bed, by its foot or its head (the village marks the foot), nearest the child; null if
	 * it is walled in. With the bed gone, beside where it stood.
	 */
	private static @Nullable BlockPos besideBed(ServerLevel level, CompanionEntity c, BlockPos foot) {
		BlockState state = level.getBlockState(foot);
		BlockPos other = foot;
		if (state.getBlock() instanceof AbstractBedBlock) {
			Direction facing = state.getValue(AbstractBedBlock.FACING);
			other = foot.relative(state.getValue(AbstractBedBlock.PART) == BedPart.FOOT ? facing : facing.getOpposite());
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos part : new BlockPos[] {foot, other}) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos p = part.relative(d);
				if (p.equals(foot) || p.equals(other) || !Spots.isStandable(level, p)) {
					continue;
				}
				double dist = p.distSqr(c.blockPosition());
				if (dist < bestDist) {
					bestDist = dist;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	@Override
	public boolean start(CompanionEntity c) {
		home = homeSpot(c);
		arrived = c.blockPosition().closerThan(home, 3);
		if (!arrived) {
			Speech.say(c, Line.CHILD_BEDTIME);
		}
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		BlockPos to = home;
		if (to == null || !(c.level() instanceof ServerLevel level) || Camp.isNightTime(level) || Camp.timeOfDay(level) >= 13000) {
			return TaskStatus.SUCCESS; // bedtime: the sleep job takes over
		}
		if (!arrived) {
			if (c.actions().walkTo(to, 1.5)) {
				arrived = true;
			} else if (c.actions().isStuck()) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (c.tickCount % 60 == 0) {
			c.getLookControl().setLookAt(c.getX() + c.getRandom().nextInt(9) - 4, c.getEyeY(), c.getZ() + c.getRandom().nextInt(9) - 4);
		}
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		home = null;
		arrived = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 15;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
