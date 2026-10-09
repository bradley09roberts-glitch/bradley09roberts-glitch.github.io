package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Sand for glass and sandstone, and clay for bricks and flower pots, when the buildings under way are short of them
 * ({@link MaterialDemand}): by day the friend digs the top block of natural sand or clay on dry ground in the gathering
 * ring (never inside the camp, never a block touching water, the guard's {@code GATHER_EARTH} rules: never near
 * anything player-built). Only the surface layer is taken, so no pits are left and nothing falls. The places are found
 * by a slow scan of the ring's surface, a few dozen columns at a time, shared by the team; the forager's job, anyone
 * may help.
 */
public final class DigSandTask implements CompanionTask {
	public static final String ID = "rowan.dig_sand";
	private static final int COLUMNS_PER_STEP = 96;
	private static final int KEEP = 48;
	private static final int PER_RUN = 12;
	private static final int BLOCK_TIMEOUT = 20 * 20;

	/** The team's knowledge of dry sand and clay on the surface around the camp. */
	private static final class Finds {
		private final List<BlockPos> sand = new ArrayList<>();
		private final List<BlockPos> clay = new ArrayList<>();
		private final List<BlockPos> sandNext = new ArrayList<>();
		private final List<BlockPos> clayNext = new ArrayList<>();
		private int cursor;
		private long stepAt = Long.MIN_VALUE / 2;
	}

	private @Nullable BlockPos current;
	private boolean wantClay;
	private int dug;
	private int blockTicks;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return wantClay ? "digging clay" : "digging sand";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().allowQuarrying) {
			return 0;
		}
		if (Camp.isNight(level) || Camp.isDusk(level)) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		boolean sand = MaterialDemand.missing(level.getServer(), Stock.SAND) > 0;
		boolean clay = MaterialDemand.missing(level.getServer(), Stock.CLAY_BALL) > 0;
		if (!sand && !clay) {
			return 0;
		}
		if (c.backpack().freeSlots() == 0 && !c.backpack().canFit(new ItemStack(Items.SAND, 8))) {
			return 0;
		}
		Finds finds = finds(level);
		scanStep(c, level, data, finds);
		boolean any = sand && !finds.sand.isEmpty() || clay && !finds.clay.isEmpty();
		return any ? 40 * CampNeeds.weight(CampNeeds.Need.DIRT) : 0;
	}

	private static Finds finds(ServerLevel level) {
		return TeamCache.get(level, "architecture.sand", Finds::new);
	}

	/**
	 * Looks at the next few dozen columns of the gathering ring (every other column, row by row) for dry sand or clay on
	 * top; a full pass replaces what was known. At most once a second for the whole team.
	 */
	private static void scanStep(CompanionEntity c, ServerLevel level, CampData data, Finds finds) {
		long now = level.getGameTime();
		if (now - finds.stepAt < 20 && now >= finds.stepAt) {
			return;
		}
		finds.stepAt = now;
		Optional<BlockPos> centre = data.campPos();
		if (centre.isEmpty()) {
			return;
		}
		int inner = WorldEditGuard.campRadius(c) + 1;
		int outer = inner + FriendsConfig.get().resourceRadius - 1;
		int side = outer / 2 * 2 + 1;
		int perRow = side / 2 + 1;
		int total = perRow * perRow;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int n = 0; n < COLUMNS_PER_STEP; n++) {
			if (finds.cursor >= total) {
				finds.sand.clear();
				finds.sand.addAll(sorted(finds.sandNext, centre.get()));
				finds.clay.clear();
				finds.clay.addAll(sorted(finds.clayNext, centre.get()));
				finds.sandNext.clear();
				finds.clayNext.clear();
				finds.cursor = 0;
				return;
			}
			int i = finds.cursor++;
			int x = centre.get().getX() - outer + (i % perRow) * 2;
			int z = centre.get().getZ() - outer + (i / perRow) * 2;
			long dx = x - centre.get().getX();
			long dz = z - centre.get().getZ();
			long d2 = dx * dx + dz * dz;
			if (d2 < (long) inner * inner || d2 > (long) outer * outer || !level.hasChunkAt(x, z)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			m.set(x, y, z);
			BlockState s = level.getBlockState(m);
			boolean isSand = s.is(Blocks.SAND) || s.is(Blocks.RED_SAND);
			boolean isClay = s.is(Blocks.CLAY);
			if (!isSand && !isClay || WorldEditGuard.touchesFluid(level, m) || data.isPlacedByFriends(level, m)) {
				continue;
			}
			List<BlockPos> into = isSand ? finds.sandNext : finds.clayNext;
			if (into.size() < KEEP * 2) {
				into.add(m.immutable());
			}
		}
	}

	private static List<BlockPos> sorted(List<BlockPos> list, BlockPos centre) {
		List<BlockPos> copy = new ArrayList<>(list);
		copy.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		return copy.size() > KEEP ? new ArrayList<>(copy.subList(0, KEEP)) : copy;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		wantClay = MaterialDemand.missing(level.getServer(), Stock.SAND) <= 0;
		dug = 0;
		blockTicks = 0;
		current = nearest(c, level, null);
		if (current == null) {
			return false;
		}
		Speech.say(c, Line.DIGGING_SAND, wantClay ? "clay" : "sand");
		return true;
	}

	/** The nearest known block of what is wanted to the friend (or to {@code near}), still there. */
	private @Nullable BlockPos nearest(CompanionEntity c, ServerLevel level, @Nullable BlockPos near) {
		Finds finds = finds(level);
		List<BlockPos> list = wantClay ? finds.clay : finds.sand;
		BlockPos from = near != null ? near : c.blockPosition();
		list.removeIf(p -> !stillThere(level, p));
		return list.stream().min(Comparator.comparingDouble(p -> p.distSqr(from))).orElse(null);
	}

	private boolean stillThere(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return true; // cannot tell; the walk there will see
		}
		BlockState s = level.getBlockState(p);
		boolean right = wantClay ? s.is(Blocks.CLAY) : s.is(Blocks.SAND) || s.is(Blocks.RED_SAND);
		return right && level.getBlockState(p.above()).isAir();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos target = current;
		if (target == null || Camp.isDusk(level) || Camp.isNight(level)) {
			return dug > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		if (!c.backpack().canFit(new ItemStack(wantClay ? Items.CLAY_BALL : Items.SAND, 4)) && c.backpack().freeSlots() == 0) {
			return dug > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		Actions actions = c.actions();
		if (!stillThere(level, target) || ++blockTicks > BLOCK_TIMEOUT) {
			return moveOn(c, level, target, false);
		}
		if (!actions.canReach(target)) {
			actions.walkTo(target, 2.0);
			if (actions.isStuck()) {
				actions.stopWalking();
				return moveOn(c, level, target, false);
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		return switch (actions.mine(target, WorldEditGuard.Reason.GATHER_EARTH)) {
			case DONE -> moveOn(c, level, target, true);
			case FAILED -> moveOn(c, level, target, false);
			case RUNNING -> TaskStatus.RUNNING;
		};
	}

	private TaskStatus moveOn(CompanionEntity c, ServerLevel level, BlockPos done, boolean success) {
		Finds finds = finds(level);
		(wantClay ? finds.clay : finds.sand).remove(done);
		blockTicks = 0;
		if (success) {
			dug++;
			Camp.data(level.getServer()).addStat(wantClay ? "clay_dug" : "sand_dug", 1);
		}
		if (dug >= PER_RUN) {
			return TaskStatus.SUCCESS;
		}
		BlockPos next = nearest(c, level, done);
		if (next == null || next.distSqr(done) > 12 * 12) {
			return dug > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		current = next;
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().reset();
		current = null;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 150;
	}
}
