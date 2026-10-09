package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SiteClearing;
import io.github.bradley09roberts.hardcorefriends.camp.SiteGrading;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Levels a building site on uneven ground ({@link SiteGrading}): first the bumps, dug away top-down so nothing is
 * left hanging (the spoil goes into the backpack), then the dips, filled bottom-up with dirt or cobblestone, the
 * spoil first. A pickaxe and fill are fetched from the supply chest when the job needs them; short of fill, the
 * friend says so and the camp's dirt need rises, so the quarry fills the gap. Terra's speciality; the builder helps
 * while the building waits, and anyone stands in through the speciality rules. One friend at a time.
 */
public final class GradeSiteTask implements CompanionTask {
	public static final String ID = "terra.grade";
	private static final int BLOCK_TIMEOUT = 200;

	private enum Phase {
		PREP,
		CUT,
		FILL
	}

	private final double baseScore;
	private final Set<BlockPos> givenUp = new HashSet<>();
	private SiteGrading.@Nullable Job job;
	private Phase phase = Phase.CUT;
	private @Nullable BlockPos current;
	private @Nullable BlockPos stepAside;
	private int currentTicks;
	private int worked;
	private boolean fetched;

	public GradeSiteTask(double baseScore) {
		this.baseScore = baseScore;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		SiteGrading.Job j = job;
		return "levelling the " + SiteClearing.siteName(j == null ? null : j.planId()) + " site";
	}

	@Override
	public double score(CompanionEntity c) {
		FriendsConfig cfg = FriendsConfig.get();
		if (!cfg.allowTerraforming || !cfg.allowWorldEditing || !(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		if (c.backpack().freeSlots() == 0 && !c.backpack().canFit(new ItemStack(Items.DIRT, 8))) {
			return 0; // a deposit first
		}
		return SiteGrading.active(level, data).isPresent() ? baseScore : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<SiteGrading.Job> active = SiteGrading.active(level, Camp.data(level.getServer()));
		if (active.isEmpty()) {
			return false;
		}
		job = active.get();
		givenUp.clear();
		current = null;
		stepAside = null;
		worked = 0;
		fetched = false;
		phase = needsChest(c, level, active.get()) ? Phase.PREP : Phase.CUT;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	/** True when a trip to the chest first would help: stone to dig without a pickaxe, or not enough fill to hand. */
	private boolean needsChest(CompanionEntity c, ServerLevel level, SiteGrading.Job j) {
		if (SupplyChest.of(level).isEmpty() || fetched) {
			return false;
		}
		List<BlockPos> cuts = SiteGrading.cutsLeft(level, j);
		boolean stone = cuts.stream().anyMatch(p -> level.getBlockState(p).is(BlockTags.BASE_STONE_OVERWORLD));
		if (stone && !c.actions().hasTool(ItemTags.PICKAXES)) {
			return true;
		}
		return fillShort(c, level, j, cuts) > 0;
	}

	/** How many more fill blocks the job needs than the friend carries and will dig up. */
	private static int fillShort(CompanionEntity c, ServerLevel level, SiteGrading.Job j, List<BlockPos> cuts) {
		int spoil = 0;
		boolean pickaxe = c.actions().hasTool(ItemTags.PICKAXES);
		for (BlockPos p : cuts) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.DIRT) || s.is(BlockTags.GRASS_BLOCKS) || pickaxe && s.is(Blocks.STONE)) {
				spoil++;
			}
		}
		return SiteGrading.fillsLeft(level, j).size() - spoil - c.backpack().count(GradeSiteTask::isFillItem);
	}

	/** Plain dirt, coarse dirt, cobblestone or stone: what dips are filled with. */
	static boolean isFillItem(ItemStack s) {
		return s.is(Items.DIRT) || s.is(Items.COARSE_DIRT) || s.is(Items.COBBLESTONE) || s.is(Items.STONE);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		SiteGrading.Job j = job;
		if (j == null) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (!SiteGrading.isOpen(Camp.data(level.getServer()), j.planId())) {
			return done(c, level); // retired (finished, or the site built on or given up): nothing here is ours to dig
		}
		return switch (phase) {
			case PREP -> prep(c, level, j);
			case CUT -> cut(c, level, j);
			case FILL -> fill(c, level, j);
		};
	}

	private TaskStatus prep(CompanionEntity c, ServerLevel level, SiteGrading.Job j) {
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				fetched = true;
				phase = Phase.CUT;
				return TaskStatus.RUNNING;
			}
			case ARRIVED -> {
			}
		}
		fetched = true;
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isPresent()) {
			List<BlockPos> cuts = SiteGrading.cutsLeft(level, j);
			boolean stone = cuts.stream().anyMatch(p -> level.getBlockState(p).is(BlockTags.BASE_STONE_OVERWORLD));
			if (stone && !c.actions().hasTool(ItemTags.PICKAXES)) {
				SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(ItemTags.PICKAXES), 1);
			}
			int shortBy = fillShort(c, level, j, cuts);
			if (shortBy > 0) {
				int got = SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(Items.DIRT) || s.is(Items.COARSE_DIRT),
					Math.min(64, shortBy));
				if (got < shortBy) {
					SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(Items.COBBLESTONE), Math.min(64, shortBy - got));
				}
			}
		}
		phase = Phase.CUT;
		return TaskStatus.RUNNING;
	}

	private TaskStatus cut(CompanionEntity c, ServerLevel level, SiteGrading.Job j) {
		if (current == null) {
			current = next(SiteGrading.cutsLeft(level, j), c, true);
			currentTicks = 0;
			if (current == null) {
				phase = Phase.FILL;
				return TaskStatus.RUNNING;
			}
		}
		BlockPos target = current;
		if (level.getBlockState(target).isAir()) {
			current = null;
			return TaskStatus.RUNNING;
		}
		if (++currentTicks > BLOCK_TIMEOUT) {
			giveUp(c, level, j);
			return TaskStatus.RUNNING;
		}
		Actions actions = c.actions();
		if (!actions.canReach(target)) {
			actions.walkTo(target, 2.0);
			if (actions.isStuck()) {
				giveUp(c, level, j);
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		switch (actions.mine(target, Reason.GRADE)) {
			case DONE -> {
				worked++;
				current = null;
			}
			case FAILED -> giveUp(c, level, j);
			case RUNNING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	private TaskStatus fill(CompanionEntity c, ServerLevel level, SiteGrading.Job j) {
		if (current == null) {
			current = next(SiteGrading.fillsLeft(level, j), c, false);
			currentTicks = 0;
			if (current == null) {
				return done(c, level);
			}
		}
		BlockPos target = current;
		if (!SiteGrading.isFillable(level.getBlockState(target))) {
			current = null;
			return TaskStatus.RUNNING;
		}
		if (++currentTicks > BLOCK_TIMEOUT) {
			giveUp(c, level, j);
			return TaskStatus.RUNNING;
		}
		ItemStack carried = c.backpack().find(GradeSiteTask::isFillItem);
		if (carried.isEmpty()) {
			return shortOfFill(c, level, j);
		}
		Actions actions = c.actions();
		if (stepAside != null) {
			if (actions.walkTo(stepAside, 0.5) || actions.isStuck()) {
				stepAside = null;
				actions.stopWalking();
			}
			return TaskStatus.RUNNING;
		}
		if (!actions.canReach(target)) {
			actions.walkTo(target, 2.0);
			if (actions.isStuck()) {
				giveUp(c, level, j);
			}
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return TaskStatus.RUNNING; // the guard paces edits
		}
		BlockState state = Block.byItem(carried.getItem()).defaultBlockState();
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, target, state, Reason.GRADE);
		if (!verdict.allowed()) {
			if ("someone is standing there".equals(verdict.why())) {
				if (c.getBoundingBox().intersects(new AABB(target))) {
					stepAside = Shelters.standableNear(level, target, c.blockPosition());
					if (stepAside == null) {
						giveUp(c, level, j);
					}
				}
				return TaskStatus.RUNNING; // wait for whoever it is to move (or time out)
			}
			giveUp(c, level, j);
			return TaskStatus.RUNNING;
		}
		ItemStack template = carried.copyWithCount(1);
		Predicate<ItemStack> same = s -> ItemStack.isSameItemSameComponents(s, template);
		if (actions.place(target, state, same, Reason.GRADE)) {
			worked++;
			current = null;
		} else {
			giveUp(c, level, j);
		}
		return TaskStatus.RUNNING;
	}

	/** Out of fill: one more look in the chest, otherwise say so and let the camp's dirt need rise. */
	private TaskStatus shortOfFill(CompanionEntity c, ServerLevel level, SiteGrading.Job j) {
		if (!fetched && SupplyChest.of(level).isPresent()) {
			phase = Phase.PREP;
			current = null;
			return TaskStatus.RUNNING;
		}
		int missing = SiteGrading.fillsLeft(level, j).size();
		String text = missing + " dirt or cobblestone to level the " + SiteClearing.siteName(j.planId()) + " site";
		CampNeeds.reportBuildShortage(level, Map.of(CampNeeds.Need.DIRT, missing), text);
		Speech.say(c, Line.NEED_MATERIALS, text);
		return worked > 0 ? done(c, level) : TaskStatus.FAILURE;
	}

	private TaskStatus done(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (worked > 0) {
			data.addStat("site_blocks_levelled", worked);
		}
		SiteGrading.Job j = job;
		if (j != null) {
			// Levelled: retire the plan now, before the building's floor goes down where the bumps were.
			SiteGrading.job(data, j.planId()).ifPresent(open -> SiteGrading.stillToDo(level, data, open));
		}
		return worked > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE; // nothing done: wait a while before looking again
	}

	/** The next position: the highest (to dig) or lowest (to fill) layer first, nearest first within it. */
	private @Nullable BlockPos next(List<BlockPos> left, CompanionEntity c, boolean highestFirst) {
		BlockPos here = c.blockPosition();
		Comparator<BlockPos> layer = Comparator.comparingInt(BlockPos::getY);
		if (highestFirst) {
			layer = layer.reversed();
		}
		return left.stream().filter(p -> !givenUp.contains(p))
			.min(layer.thenComparingDouble(p -> p.distSqr(here))).orElse(null);
	}

	private void giveUp(CompanionEntity c, ServerLevel level, SiteGrading.Job j) {
		BlockPos p = current;
		if (p != null) {
			givenUp.add(p);
			SiteGrading.skip(Camp.data(level.getServer()), j, p);
		}
		current = null;
		stepAside = null;
		c.actions().reset();
	}

	@Override
	public void stop(CompanionEntity c) {
		job = null;
		current = null;
		stepAside = null;
		c.actions().reset();
	}

	@Override
	public int maxTicks() {
		return 20 * 240;
	}

	@Override
	public int failureCooldown() {
		return 400;
	}
}
