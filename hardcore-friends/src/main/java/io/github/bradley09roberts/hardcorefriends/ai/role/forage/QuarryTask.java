package io.github.bradley09roberts.hardcorefriends.ai.role.forage;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.EditSteps;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MiningHelper;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * By day, Rowan digs dirt and stone from a 5×5 quarry in the gathering ring, top layer first and never more than
 * two layers deep, up to 10 blocks per run. One corner block of the lower layer is left as a step so nobody gets
 * trapped, she never digs a block with a drop or fluid beneath it, and she leaves stone alone unless she carries a
 * pickaxe that gets drops from it. The quarry (corner, next cell, dimension, earlier sites) is kept in camp memory
 * under {@code rowan.quarry}, so work resumes after a restart; an exhausted quarry is replaced by a new site. She
 * only quarries in the camp's dimension and never touches a quarry recorded in another one.
 */
public final class QuarryTask implements CompanionTask {
	public static final String MEMORY = "rowan.quarry";
	public static final int LAYERS = 2;
	private static final int CELLS = QuarrySiteFinder.SIZE * QuarrySiteFinder.SIZE;
	private static final int PER_RUN = 10;
	private static final int CELL_TIMEOUT = 200;
	private static final int MAX_REMEMBERED = 16;

	private @Nullable BlockPos current;
	private int currentTicks;
	private int dug;
	private int searchTurn;

	@Override
	public String id() {
		return "rowan.quarry";
	}

	@Override
	public String describe() {
		return "quarrying stone and earth";
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		// The quarry lies outside the camp, so dusk counts as night: the return home would only call her back.
		if (Camp.isNight(level) || Camp.isDusk(level) || !FriendsConfig.get().allowQuarrying) {
			return 0;
		}
		if (!mayUseMemory(level, Camp.data(level.getServer()))) {
			return 0;
		}
		if (c.backpack().freeSlots() == 0 && !c.backpack().canFit(new ItemStack(Items.DIRT, 10))
			&& !c.backpack().canFit(new ItemStack(Items.COBBLESTONE, 10))) {
			return 0;
		}
		return 40 * Math.max(CampNeeds.weight(CampNeeds.Need.STONE), CampNeeds.weight(CampNeeds.Need.DIRT));
	}

	// ------------------------------------------------------------- memory

	/** The active quarry's north-west corner at its top layer, or null. */
	public static @Nullable BlockPos corner(CampData data) {
		CompoundTag mem = data.memory(MEMORY);
		return mem.getBooleanOr("active", false) ? BlockPos.of(mem.getLongOr("corner", 0L)) : null;
	}

	/** The block a quarry cell number (0–49) stands for: top layer first, row by row. */
	public static BlockPos cellPos(BlockPos corner, int cell) {
		int layer = cell / CELLS;
		int i = cell % CELLS;
		return corner.offset(i % QuarrySiteFinder.SIZE, -layer, i / QuarrySiteFinder.SIZE);
	}

	/**
	 * The quarry memory holds camp-dimension coordinates: it is only used (advanced, finished or replaced) while Rowan
	 * is in the camp's dimension, and a quarry recorded in another dimension is left alone.
	 */
	private static boolean mayUseMemory(ServerLevel level, CampData data) {
		if (data.campPos().isPresent() && !Camp.isCampLevel(level, data)) {
			return false;
		}
		CompoundTag mem = data.memory(MEMORY);
		String dim = mem.getStringOr("dim", "");
		return !mem.getBooleanOr("active", false) || dim.isEmpty() || dim.equals(Camp.dimensionId(level));
	}

	/** The lower layer's first corner block stays as a step out of the pit. */
	private static boolean isStep(int cell) {
		return cell == CELLS;
	}

	private static List<BlockPos> earlier(CompoundTag mem) {
		List<BlockPos> list = new ArrayList<>();
		mem.getLongArray("done").ifPresent(a -> {
			for (long l : a) {
				list.add(BlockPos.of(l));
			}
		});
		return list;
	}

	private static void finishQuarry(CampData data, BlockPos corner) {
		CompoundTag mem = data.memory(MEMORY);
		List<BlockPos> done = earlier(mem);
		done.add(corner);
		while (done.size() > MAX_REMEMBERED) {
			done.removeFirst();
		}
		mem.putLongArray("done", done.stream().mapToLong(BlockPos::asLong).toArray());
		mem.putBoolean("active", false);
		mem.putInt("cell", 0);
		data.setDirty();
	}

	// -------------------------------------------------------------- running

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		current = null;
		dug = 0;
		if (!mayUseMemory(level, data)) {
			return false;
		}
		return corner(data) != null || beginNewSite(c, data);
	}

	/** Chooses and remembers a fresh quarry site. Returns false if there is none nearby. */
	private boolean beginNewSite(CompanionEntity c, CampData data) {
		CompoundTag mem = data.memory(MEMORY);
		BlockPos site = QuarrySiteFinder.find(c, earlier(mem), searchTurn++);
		if (site == null) {
			return false;
		}
		mem.putBoolean("active", true);
		mem.putLong("corner", site.asLong());
		mem.putString("dim", Camp.dimensionId((ServerLevel) c.level()));
		mem.putInt("cell", 0);
		data.setDirty();
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNight(level) || Camp.isDusk(level)) {
			return dug > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE; // the quarry waits for daylight
		}
		CampData data = Camp.data(level.getServer());
		BlockPos corner = corner(data);
		if (corner == null) {
			return TaskStatus.FAILURE;
		}
		CompoundTag mem = data.memory(MEMORY);
		int cell = mem.getIntOr("cell", 0);
		if (!level.isLoaded(corner) || !level.isLoaded(corner.offset(QuarrySiteFinder.SIZE - 1, 0, QuarrySiteFinder.SIZE - 1))) {
			// Too far away for the land to be loaded: head over before judging any cell.
			c.actions().walkTo(corner.above(), 2.0);
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		if (current == null) {
			// Skip cells that are already empty or must not be dug (a few per tick at most).
			int skipped = 0;
			while (cell < CELLS * LAYERS && skipped < 8 && !diggable(c, level, cellPos(corner, cell), cell)) {
				cell++;
				skipped++;
			}
			if (cell != mem.getIntOr("cell", 0)) {
				mem.putInt("cell", cell);
				data.setDirty();
			}
			if (cell >= CELLS * LAYERS) {
				finishQuarry(data, corner);
				if (dug > 0) {
					return TaskStatus.SUCCESS;
				}
				return beginNewSite(c, data) ? TaskStatus.RUNNING : TaskStatus.FAILURE;
			}
			if (skipped >= 8) {
				return TaskStatus.RUNNING;
			}
			current = cellPos(corner, cell);
			currentTicks = 0;
		}
		BlockPos target = current;
		Actions actions = c.actions();
		if (++currentTicks > CELL_TIMEOUT) {
			advance(data, mem, cell);
			return TaskStatus.RUNNING;
		}
		if (!actions.canReach(target)) {
			actions.walkTo(target.above(), 1.5);
			if (actions.isStuck()) {
				advance(data, mem, cell);
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		switch (actions.mine(target, Reason.GATHER_EARTH)) {
			case DONE -> {
				dug++;
				data.addStat("blocks_quarried", 1);
				advance(data, mem, cell);
				if (dug >= PER_RUN) {
					return TaskStatus.SUCCESS;
				}
			}
			case FAILED -> advance(data, mem, cell);
			case RUNNING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	private void advance(CampData data, CompoundTag mem, int cell) {
		mem.putInt("cell", cell + 1);
		data.setDirty();
		current = null;
	}

	/** A natural block the guard lets Rowan quarry, with solid ground under it. */
	private static boolean diggable(CompanionEntity c, ServerLevel level, BlockPos pos, int cell) {
		if (isStep(cell) || !level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.is(ModTags.EARTH_GATHERABLE)) {
			return false;
		}
		if (!MiningHelper.canHarvest(c, state)) {
			return false; // stone without a pickaxe would give nothing: leave it
		}
		BlockState below = level.getBlockState(pos.below());
		if (below.isAir() || !below.getFluidState().isEmpty() || below.canBeReplaced()) {
			return false; // never open a drop into a cave or let fluid in
		}
		WorldEditGuard.Verdict v = WorldEditGuard.canBreak(c, pos, Reason.GATHER_EARTH);
		return v.allowed() || EditSteps.paced(v);
	}

	@Override
	public void stop(CompanionEntity c) {
		current = null;
	}
}
