package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteGrading;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.survival.Shelters;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * No way out on foot: dig a staircase up, the way a player does, one step up and one across at a time, towards home
 * where the ground allows. Each step digs at most three blocks (headroom above the friend, and the two blocks of the
 * next step), and only natural earth, sand, gravel and stone, never a block with anything stored in it, never a block
 * touching water or lava (so nothing floods in), never under loose sand or gravel that would fall on them, never near
 * anything a player built, never a block the friends placed and never inside the camp: the {@code SURVIVAL} edit rules
 * allow exactly the blocks planned for the step and nothing else. A torch goes down every few steps in the dark if they carry one. Starting in the water (a hole with
 * steep sides) the first step is cut just above the waterline.
 *
 * <p>Done when the friend can get away on foot: out of the cave (for a friend lost underground) or free to walk
 * {@code freeRadius} blocks (for one shut in). Given up after {@value #MAX_STEPS} steps or when no direction can be dug.
 */
final class StairPlan implements Plan {
	private static final int MAX_STEPS = 96;
	private static final int GIVE_UP = 20 * 60 * 6;
	/** How long the climb onto a freshly dug step may take before the friend is helped up the last bit. */
	private static final int CLIMB_TICKS = 60;
	private static final int TORCH_EVERY = 5;
	/** Refusals that only mean "not just now". */
	private static final Set<String> WAITS = Set.of("pacing", "a player is right there", "not loaded");

	private enum Phase {
		CHOOSE,
		DIG,
		CLIMB
	}

	private final @Nullable BlockPos towards;
	private final boolean outOfCave;
	private final int freeRadius;
	private final List<Direction> order = new ArrayList<>();
	private final List<BlockPos> cells = new ArrayList<>();
	private final Set<BlockPos> refused = new HashSet<>();
	/** Directions that did not work from the current spot (cleared on every step up). */
	private final Set<Direction> failedHere = EnumSet.noneOf(Direction.class);
	/** The way the last step went: tried first, so the stairs run straight. */
	private @Nullable Direction preferred;
	private @Nullable Direction going;
	private @Nullable BlockPos from;
	private @Nullable BlockPos stepTo;
	private Phase phase = Phase.CHOOSE;
	private int phaseTicks;
	private int ticks;
	private int steps;

	/**
	 * {@code outOfCave}: done once out of the cave and free to walk a little; otherwise done once free to walk
	 * {@code freeRadius} blocks.
	 */
	StairPlan(@Nullable BlockPos towards, boolean outOfCave, int freeRadius) {
		this.towards = towards;
		this.outOfCave = outOfCave;
		this.freeRadius = freeRadius;
	}

	@Override
	public Kind kind() {
		return Kind.STAIR;
	}

	@Override
	public boolean start(CompanionEntity c, ServerLevel level) {
		if (c.isChild() || !c.isTeamMember() || !FriendsConfig.get().allowWorldEditing) {
			return false;
		}
		Direction first = towards != null && Math.abs(towards.getX() - c.getX()) + Math.abs(towards.getZ() - c.getZ()) > 2
			? Direction.getApproximateNearest(towards.getX() + 0.5 - c.getX(), 0, towards.getZ() + 0.5 - c.getZ())
			: c.getDirection();
		if (!first.getAxis().isHorizontal()) {
			first = c.getDirection();
		}
		order.add(first);
		order.add(first.getClockWise());
		order.add(first.getCounterClockWise());
		order.add(first.getOpposite());
		if (!choose(c, level)) {
			return false;
		}
		Speech.say(c, Line.DIGGING_OUT);
		return true;
	}

	@Override
	public Status tick(CompanionEntity c, ServerLevel level) {
		if (++ticks > GIVE_UP || steps >= MAX_STEPS) {
			return Status.FAILED;
		}
		phaseTicks++;
		switch (phase) {
			case CHOOSE -> {
				if (free(c, level)) {
					return Status.DONE;
				}
				return choose(c, level) ? Status.RUNNING : Status.FAILED;
			}
			case DIG -> {
				return dig(c, level);
			}
			case CLIMB -> {
				return climb(c, level);
			}
		}
		return Status.RUNNING;
	}

	/** The spot the stairs start from: the friend's feet, or the top of the water they are in. */
	private static BlockPos feet(CompanionEntity c, ServerLevel level) {
		BlockPos feet = c.blockPosition();
		if (level.getFluidState(feet).is(FluidTags.WATER)) {
			return new BlockPos(feet.getX(), Terrain.waterSurface(level, feet) - 1, feet.getZ());
		}
		return feet;
	}

	/** Plans the next step in the first direction that works; false when no direction does. */
	private boolean choose(CompanionEntity c, ServerLevel level) {
		Shelters.clearDigs(c);
		BlockPos f = feet(c, level);
		List<Direction> tries = new ArrayList<>(order);
		if (preferred != null) {
			tries.remove(preferred);
			tries.addFirst(preferred);
		}
		for (Direction d : tries) {
			if (failedHere.contains(d)) {
				continue;
			}
			BlockPos floor = f.relative(d);
			BlockPos step = floor.above();
			if (!level.isLoaded(floor) || !level.isLoaded(step.above())) {
				continue;
			}
			BlockState floorState = level.getBlockState(floor);
			if (!floorState.isFaceSturdy(level, floor, Direction.UP) || Terrain.hazard(floorState)
				|| !floorState.getFluidState().isEmpty()) {
				continue; // nothing firm to step up onto that way (open air, a fence, a slab)
			}
			List<BlockPos> dig = new ArrayList<>();
			for (BlockPos cell : List.of(f.above(2), step.above(), step)) {
				if (!Terrain.passable(level, cell) || !level.getFluidState(cell).isEmpty()) {
					dig.add(cell);
				}
			}
			if (!diggable(c, level, dig)) {
				continue;
			}
			dig.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
			cells.clear();
			cells.addAll(dig);
			from = f;
			stepTo = step;
			going = d;
			phase = cells.isEmpty() ? Phase.CLIMB : Phase.DIG;
			phaseTicks = 0;
			if (!cells.isEmpty()) {
				Shelters.planDigs(c, cells); // the edit rules let them dig exactly these
			}
			return true;
		}
		return false;
	}

	/** This direction does not work from here: plan again without it. */
	private void giveUpDirection() {
		if (going != null) {
			failedHere.add(going);
		}
		phase = Phase.CHOOSE;
	}

	private boolean diggable(CompanionEntity c, ServerLevel level, List<BlockPos> dig) {
		CampData data = Camp.data(level.getServer());
		boolean campHere = Camp.isCampLevel(level, data);
		for (BlockPos cell : dig) {
			if (refused.contains(cell) || !level.isLoaded(cell) || !level.isLoaded(cell.above())) {
				return false;
			}
			BlockState s = level.getBlockState(cell);
			if (s.hasBlockEntity() || !s.getFluidState().isEmpty()
				|| !SiteGrading.isGradeable(s) || WorldEditGuard.breachesFluid(level, cell)) {
				return false;
			}
			if (data.isPlacedByFriends(level, cell) || campHere && WorldEditGuard.inCampHorizontally(c, cell)) {
				return false; // the friends' own building work, or the camp itself: never dug to get out
			}
			if (!dig.contains(cell.above()) && level.getBlockState(cell.above()).getBlock() instanceof Fallable) {
				return false; // sand or gravel above would come down on them
			}
		}
		return true;
	}

	private Status dig(CompanionEntity c, ServerLevel level) {
		BlockPos f = from;
		if (f == null || !feet(c, level).equals(f)) {
			phase = Phase.CHOOSE; // pushed off the spot: plan again from where they are
			return Status.RUNNING;
		}
		cells.removeIf(cell -> Terrain.passable(level, cell) && level.getFluidState(cell).isEmpty());
		if (cells.isEmpty()) {
			phase = Phase.CLIMB;
			phaseTicks = 0;
			return Status.RUNNING;
		}
		c.getNavigation().stop();
		BlockPos cell = cells.getFirst();
		Actions.Result result = c.actions().mine(cell, Reason.SURVIVAL);
		if (result == Actions.Result.FAILED) {
			if (WAITS.contains(WorldEditGuard.canBreak(c, cell, Reason.SURVIVAL).why())) {
				return Status.RUNNING; // only "not just now" (a player right beside it): wait
			}
			refused.add(cell);
			c.actions().cancelMining();
			giveUpDirection();
		} else if (result == Actions.Result.DONE) {
			Wayfinder.progress(c);
		}
		return Status.RUNNING;
	}

	private Status climb(CompanionEntity c, ServerLevel level) {
		BlockPos to = stepTo;
		if (to == null) {
			return Status.FAILED;
		}
		double dx = to.getX() + 0.5 - c.getX();
		double dz = to.getZ() + 0.5 - c.getZ();
		if (c.getY() >= to.getY() - 0.01 && dx * dx + dz * dz < 0.36 && (c.onGround() || c.isInWater())) {
			steps++;
			Wayfinder.progress(c);
			if (steps % TORCH_EVERY == 0 && Senses.dark(c)) {
				CaveExitPlan.placeTorch(c, level, c.blockPosition());
			}
			Shelters.clearDigs(c);
			failedHere.clear();
			preferred = going; // keep the stairs running the same way
			phase = Phase.CHOOSE;
			phaseTicks = 0;
			return Status.RUNNING;
		}
		c.getMoveControl().setWantedPosition(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
		if ((c.onGround() || c.isInWater()) && phaseTicks % 10 == 1) {
			c.getJumpControl().jump();
		}
		if (phaseTicks > CLIMB_TICKS) {
			// The jumps keep falling short (a slab, a low ceiling of leaves): help them up the last bit, as there is room.
			if (Terrain.passable(level, to) && Terrain.passable(level, to.above()) && level.getFluidState(to).isEmpty()) {
				c.setDeltaMovement(0, 0, 0);
				c.setPos(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
				phaseTicks = 0;
			} else {
				giveUpDirection();
			}
		}
		return Status.RUNNING;
	}

	/** True once the friend can get away on foot from where they stand. */
	private boolean free(CompanionEntity c, ServerLevel level) {
		BlockPos f = c.blockPosition();
		if (c.isInWater()) {
			return false;
		}
		if (outOfCave) {
			return !Terrain.underground(level, f) && Ways.canLeave(level, f, 6, 250);
		}
		return Ways.canLeave(level, f, freeRadius, 1500);
	}

	@Override
	public void stop(CompanionEntity c) {
		Shelters.clearDigs(c);
		c.actions().cancelMining();
		c.getNavigation().stop();
	}
}
