package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.survival.Shelters;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Shut in a shallow hole (one or two blocks too deep to jump out of): put a carried block of dirt or cobblestone
 * underfoot, the way players do (jump, and place it where your feet were), once or twice, step out onto the edge, then
 * turn round and take the blocks back. Nothing is left behind unless a block can no longer be reached or taken (then
 * it stays, as a player's would). Uses the {@code SURVIVAL} edit rules, which only allow plain dirt and stone right
 * round the friend, never near anything a player built, never into water.
 */
final class StepPlan implements Plan {
	/** The most blocks put down to get out. */
	static final int MAX_RISE = 2;
	private static final int TICKS_PER_JUMP = 12;
	private static final int JUMPS_BEFORE_LIFT = 3;
	private static final int GIVE_UP = 20 * 40;

	private enum Phase {
		RISE,
		OUT,
		TAKE_BACK
	}

	private final List<BlockPos> placed = new ArrayList<>();
	private Phase phase = Phase.RISE;
	private int rise;
	private @Nullable BlockPos base;
	private @Nullable BlockPos ledge;
	private int jumpTicks;
	private int jumps;
	private int ticks;
	private int phaseTicks;

	@Override
	public Kind kind() {
		return Kind.STEP;
	}

	/** How many blocks a friend at {@code feet} must rise to step out (1 or 2), or 0 when that is not enough. */
	static int riseNeeded(ServerLevel level, BlockPos feet) {
		for (int r = 1; r <= MAX_RISE; r++) {
			if (!Terrain.passable(level, feet.above(r + 1))) {
				return 0; // no room overhead to rise this far
			}
			if (ledgeAfter(level, feet, r) != null) {
				return r;
			}
		}
		return 0;
	}

	/** The spot to step onto after rising {@code r} blocks from {@code feet}: level with them, or one up. */
	private static @Nullable BlockPos ledgeAfter(ServerLevel level, BlockPos feet, int r) {
		BlockPos raised = feet.above(r);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = raised.relative(d);
			if (Terrain.canStand(level, side)) {
				return side;
			}
			if (Terrain.passable(level, raised.above(2)) && Terrain.canStand(level, side.above())) {
				return side.above();
			}
		}
		return null;
	}

	@Override
	public boolean start(CompanionEntity c, ServerLevel level) {
		if (c.isChild() || !c.isTeamMember() || !FriendsConfig.get().allowWorldEditing || c.isInWater() || !c.onGround()) {
			return false;
		}
		rise = riseNeeded(level, c.blockPosition());
		return rise > 0 && Shelters.blocksCarried(c) >= rise;
	}

	@Override
	public Status tick(CompanionEntity c, ServerLevel level) {
		if (++ticks > GIVE_UP) {
			return Status.FAILED;
		}
		phaseTicks++;
		return switch (phase) {
			case RISE -> rise(c, level);
			case OUT -> out(c, level);
			case TAKE_BACK -> takeBack(c, level);
		};
	}

	private Status rise(CompanionEntity c, ServerLevel level) {
		if (placed.size() >= rise) {
			ledge = ledgeAfter(level, placed.getFirst(), placed.size());
			if (ledge == null) {
				phase = Phase.TAKE_BACK; // the way out has gone: take the blocks back and think again
				return Status.RUNNING;
			}
			phase = Phase.OUT;
			phaseTicks = 0;
			return Status.RUNNING;
		}
		if (base == null) {
			if (!c.onGround()) {
				return Status.RUNNING; // land first
			}
			base = c.blockPosition();
			jumpTicks = 0;
		}
		BlockPos at = base;
		c.getNavigation().stop();
		Vec3 motion = c.getDeltaMovement();
		c.setDeltaMovement(0, motion.y, 0);
		if (jumpTicks == 0) {
			c.setPos(at.getX() + 0.5, c.getY(), at.getZ() + 0.5);
			c.getJumpControl().jump();
		}
		jumpTicks++;
		if (c.getY() >= at.getY() + 1.0 && placeUnder(c, at)) {
			return Status.RUNNING;
		}
		if (jumpTicks > TICKS_PER_JUMP) {
			jumpTicks = 0;
			if (++jumps >= JUMPS_BEFORE_LIFT) {
				// The jumps fall short: lift them the last bit (there is room above) and place at once.
				c.setPos(at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5);
				c.setDeltaMovement(Vec3.ZERO);
				if (!placeUnder(c, at)) {
					phase = Phase.TAKE_BACK;
				}
			}
		}
		return Status.RUNNING;
	}

	private boolean placeUnder(CompanionEntity c, BlockPos at) {
		if (c.level().getGameTime() - c.lastEditTick() < 4) {
			return false;
		}
		ItemStack block = c.backpack().find(Shelters::isShelterBlock);
		if (block.isEmpty()) {
			phase = Phase.TAKE_BACK;
			return false;
		}
		BlockState state = Shelters.stateOf(block);
		ItemStack template = block.copyWithCount(1);
		if (!c.actions().place(at, state, s -> ItemStack.isSameItemSameComponents(s, template), Reason.SURVIVAL)) {
			return false;
		}
		Shelters.addPiece(c, at);
		placed.add(at);
		base = null;
		jumps = 0;
		return true;
	}

	private Status out(CompanionEntity c, ServerLevel level) {
		BlockPos to = ledge;
		if (to == null || phaseTicks > 80) {
			phase = Phase.TAKE_BACK;
			return Status.RUNNING;
		}
		double dx = to.getX() + 0.5 - c.getX();
		double dz = to.getZ() + 0.5 - c.getZ();
		if (c.getY() >= to.getY() - 0.2 && dx * dx + dz * dz < 0.36 && c.onGround()) { // a path block is a little low
			phase = Phase.TAKE_BACK;
			phaseTicks = 0;
			return Status.RUNNING;
		}
		c.getMoveControl().setWantedPosition(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
		if (to.getY() > c.getBlockY() && c.onGround() && phaseTicks % 10 == 1) {
			c.getJumpControl().jump();
		}
		return Status.RUNNING;
	}

	/** Takes the blocks back, the top one first, from where the friend now stands; any out of reach are left. */
	private Status takeBack(CompanionEntity c, ServerLevel level) {
		while (!placed.isEmpty()) {
			BlockPos top = placed.getLast();
			if (level.getBlockState(top).isAir() || !Shelters.isPiece(c, top)) {
				forget(c, top);
				continue;
			}
			if (c.blockPosition().below().equals(top) || !c.actions().canReach(top)) {
				forget(c, top); // standing on it, or out of reach: left standing
				continue;
			}
			Actions.Result result = c.actions().mine(top, Reason.SURVIVAL);
			if (result == Actions.Result.RUNNING) {
				return Status.RUNNING;
			}
			forget(c, top);
			return Status.RUNNING;
		}
		return Ways.canLeave(level, c.blockPosition(), 6, 200) ? Status.DONE : Status.FAILED;
	}

	private void forget(CompanionEntity c, BlockPos pos) {
		placed.remove(pos);
		Shelters.removePiece(c, pos);
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().cancelMining();
		for (BlockPos pos : List.copyOf(placed)) {
			Shelters.removePiece(c, pos); // anything not taken back stays where it is
		}
		placed.clear();
	}
}
