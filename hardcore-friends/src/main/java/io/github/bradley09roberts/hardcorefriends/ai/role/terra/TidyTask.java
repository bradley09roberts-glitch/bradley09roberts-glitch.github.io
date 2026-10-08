package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Terra keeps the camp core neat: clears tall and short grass within 8 blocks of the camp centre and fills
 * one-block-deep holes in level ground with dirt from the backpack or the supply chest.
 */
public final class TidyTask implements CompanionTask {
	private static final int CORE = 8;
	private static final int SCAN_INTERVAL = 100;
	private static final int WEEDS_PER_RUN = 8;
	private static final int HOLES_PER_RUN = 4;
	private static final double WORK_REACH = 2.5;
	private static final Predicate<ItemStack> DIRT = s -> s.is(Items.DIRT);

	private final List<BlockPos> weeds = new ArrayList<>();
	private final List<BlockPos> holes = new ArrayList<>();
	private long scannedAt = -100_000;

	private @Nullable BlockPos current;
	private boolean currentIsHole;
	private boolean fetching;
	private int weedsDone;
	private int holesDone;

	@Override
	public String id() {
		return "terra.tidy";
	}

	@Override
	public String describe() {
		return "tidying the camp";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (data.campPos().isEmpty() || !Camp.isCampLevel(level, data)) {
			return 0;
		}
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		if (level.getGameTime() - scannedAt >= SCAN_INTERVAL) {
			scannedAt = level.getGameTime();
			scan(level, data);
		}
		if (!weeds.isEmpty() || (!holes.isEmpty() && dirtAvailable(c))) {
			return 35;
		}
		return 0;
	}

	private static boolean dirtAvailable(CompanionEntity c) {
		return c.backpack().has(DIRT) || ChestFetch.chestHas(c, DIRT);
	}

	private void scan(ServerLevel level, CampData data) {
		weeds.clear();
		holes.clear();
		BlockPos centre = data.campPos().orElseThrow();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -CORE; dx <= CORE; dx++) {
			for (int dz = -CORE; dz <= CORE; dz++) {
				if (dx * dx + dz * dz > CORE * CORE) {
					continue;
				}
				for (int dy = 3; dy >= -3; dy--) {
					m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (!level.isLoaded(m)) {
						break;
					}
					BlockState s = level.getBlockState(m);
					if (Landscape.isWeed(s) && !Landscape.isWeed(level.getBlockState(m.below()))) {
						weeds.add(m.immutable());
					} else if (isHole(level, data, m)) {
						holes.add(m.immutable());
					}
				}
			}
		}
	}

	/**
	 * A one-block pit in level ground: air with solid blocks below and on all four sides, open above. Pits in anything
	 * a player or the friends built are left alone, since they may be there on purpose.
	 */
	static boolean isHole(ServerLevel level, CampData data, BlockPos pos) {
		if (!level.getBlockState(pos).isAir()) {
			return false;
		}
		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP) || Landscape.isPlayerMade(level, floor, below, data)
			|| data.isPlacedByFriends(level, below)) {
			return false;
		}
		if (!Landscape.isOpen(level.getBlockState(pos.above())) || WorldEditGuard.touchesFluid(level, pos)) {
			return false;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos side = pos.relative(d);
			BlockState wall = level.getBlockState(side);
			if (!wall.isFaceSturdy(level, side, d.getOpposite()) || Landscape.isPlayerMade(level, wall, side, data)
				|| data.isPlacedByFriends(level, side) || !Landscape.isOpen(level.getBlockState(side.above()))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean start(CompanionEntity c) {
		current = null;
		fetching = false;
		weedsDone = 0;
		holesDone = 0;
		return !weeds.isEmpty() || !holes.isEmpty();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (fetching) {
			ChestFetch.Result r = ChestFetch.step(c, DIRT, 8);
			if (r == ChestFetch.Result.RUNNING) {
				return TaskStatus.RUNNING;
			}
			fetching = false;
			if (!c.backpack().has(DIRT)) {
				holes.clear();
			}
		}
		if (current == null && !pickNext(c)) {
			return finish(level);
		}
		BlockPos target = current;
		if (target == null) {
			return TaskStatus.RUNNING; // fetching dirt first
		}
		Actions actions = c.actions();
		if (!actions.canReach(target) || c.position().distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) > 3.5 * 3.5) {
			actions.walkTo(target, WORK_REACH);
			if (actions.isStuck()) {
				actions.stopWalking();
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		if (!currentIsHole) {
			if (!Landscape.isWeed(level.getBlockState(target))) {
				current = null;
				return TaskStatus.RUNNING;
			}
			Actions.Result r = actions.mine(target, Reason.LANDSCAPE);
			if (r == Actions.Result.DONE) {
				weedsDone++;
				current = null;
			} else if (r == Actions.Result.FAILED) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		if (!isHole(level, data, target)) {
			current = null;
			return TaskStatus.RUNNING;
		}
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, target, dirt, Reason.LANDSCAPE);
		if (!verdict.allowed()) {
			if (!"pacing".equals(verdict.why())) {
				current = null;
			}
			return TaskStatus.RUNNING;
		}
		if (actions.place(target, dirt, DIRT, Reason.LANDSCAPE)) {
			holesDone++;
		}
		current = null;
		return TaskStatus.RUNNING;
	}

	/** Chooses the nearest weed, then the nearest hole (fetching dirt first if none is carried). */
	private boolean pickNext(CompanionEntity c) {
		if (weedsDone < WEEDS_PER_RUN && !weeds.isEmpty()) {
			current = takeNearest(c, weeds);
			currentIsHole = false;
			return true;
		}
		if (holesDone < HOLES_PER_RUN && !holes.isEmpty()) {
			if (!c.backpack().has(DIRT)) {
				if (ChestFetch.chestHas(c, DIRT)) {
					fetching = true;
					return true; // current stays null; the fetch runs next tick
				}
				holes.clear();
				return false;
			}
			current = takeNearest(c, holes);
			currentIsHole = true;
			return true;
		}
		return false;
	}

	private static BlockPos takeNearest(CompanionEntity c, List<BlockPos> list) {
		BlockPos best = list.getFirst();
		for (BlockPos p : list) {
			if (p.distSqr(c.blockPosition()) < best.distSqr(c.blockPosition())) {
				best = p;
			}
		}
		list.remove(best);
		return best;
	}

	private TaskStatus finish(ServerLevel level) {
		scannedAt = -100_000;
		if (weedsDone + holesDone == 0) {
			return TaskStatus.FAILURE;
		}
		Camp.data(level.getServer()).addStat("holes_filled", holesDone);
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		current = null;
		fetching = false;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
