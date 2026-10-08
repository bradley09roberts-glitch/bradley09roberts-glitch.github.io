package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampProgress;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Fern's stage-one improvement, the farm plot. Once the camp holds 16 farmland the plot counts as done. Without any
 * water in the camp she takes a water bucket from the supply chest, digs a one-block hole in the middle of a flat
 * 9×9 patch, pours the water in (keeping the empty bucket) and reserves the site; tilling then works around it.
 * Without a bucket she asks for one and waits a good while before asking again.
 */
public final class FarmPlotTask implements CompanionTask {
	private static final int PLOT_FARMLAND = 16;
	private static final int TARGET_TIMEOUT = 600;

	private enum Phase {
		COMPLETE,
		FETCH,
		DIG,
		POUR
	}

	private final FarmContext farm;
	private Phase phase = Phase.COMPLETE;
	private @Nullable BlockPos site;
	private int phaseTicks;

	public FarmPlotTask(FarmContext farm) {
		this.farm = farm;
	}

	@Override
	public String id() {
		return "fern.farm_plot";
	}

	@Override
	public String describe() {
		return "laying out the farm";
	}

	@Override
	public int failureCooldown() {
		return 20 * 120;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public double score(CompanionEntity c) {
		CampData data = Camp.data(c.level().getServer());
		if (data.stage() < 1 || data.isCompleted(Structures.FARM_PLOT)) {
			return 0;
		}
		farm.stepCampSurvey(c);
		if (!farm.campSurveyed()) {
			return 0;
		}
		if (farm.campFarmland() >= PLOT_FARMLAND) {
			return 65;
		}
		if (!farm.campWater().isEmpty()) {
			// Tilling grows the farm from here; only speak up if Fern lacks what tilling needs.
			return canTill(c) ? 0 : 25;
		}
		return bucketAvailable(c) ? 60 : 30;
	}

	private static boolean canTill(CompanionEntity c) {
		return c.actions().hasTool(ItemTags.HOES) && c.backpack().count(Crops.IS_SEED) >= 4;
	}

	private static boolean bucketAvailable(CompanionEntity c) {
		if (c.backpack().has(s -> s.is(Items.WATER_BUCKET))) {
			return true;
		}
		return SupplyChest.of((ServerLevel) c.level()).map(chest -> SupplyChest.count(chest, s -> s.is(Items.WATER_BUCKET)) > 0)
			.orElse(false);
	}

	@Override
	public boolean start(CompanionEntity c) {
		phaseTicks = 0;
		site = null;
		if (farm.campFarmland() >= PLOT_FARMLAND) {
			phase = Phase.COMPLETE;
			return true;
		}
		if (!farm.campWater().isEmpty()) {
			if (!c.actions().hasTool(ItemTags.HOES)) {
				Speech.say(c, Line.NEED_TOOL, "hoe");
			} else {
				Speech.say(c, Line.NEED_MATERIALS, "some seeds to sow");
			}
			return false;
		}
		if (!bucketAvailable(c)) {
			Speech.say(c, Line.NEED_MATERIALS, "a water bucket");
			return false;
		}
		site = FarmPlotFinder.find(c);
		if (site == null) {
			Speech.say(c, Line.NEED_MATERIALS, "a flat patch of grass for the farm");
			return false;
		}
		phase = c.backpack().has(s -> s.is(Items.WATER_BUCKET)) ? Phase.DIG : Phase.FETCH;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (phase == Phase.COMPLETE) {
			CampProgress.complete(c, Structures.FARM_PLOT);
			return TaskStatus.SUCCESS;
		}
		BlockPos centre = site;
		if (centre == null || ++phaseTicks > TARGET_TIMEOUT) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		return switch (phase) {
			case FETCH -> fetch(c, level);
			case DIG -> dig(c, level, centre);
			case POUR -> pour(c, level, centre);
			default -> TaskStatus.FAILURE;
		};
	}

	private TaskStatus fetch(CompanionEntity c, ServerLevel level) {
		Optional<BlockPos> chestPos = Camp.data(level.getServer()).chestPos();
		Optional<Container> chest = SupplyChest.of(level);
		if (chestPos.isEmpty() || chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(chestPos.get(), 2.5)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		if (SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(Items.WATER_BUCKET), 1) == 0) {
			return TaskStatus.FAILURE;
		}
		phase = Phase.DIG;
		phaseTicks = 0;
		return TaskStatus.RUNNING;
	}

	/** Stands beside the plot centre (never on it) within reach. Returns true once in place. */
	private static boolean standBeside(CompanionEntity c, BlockPos centre) {
		Actions actions = c.actions();
		BlockPos feet = c.blockPosition();
		boolean onCentre = feet.getX() == centre.getX() && feet.getZ() == centre.getZ();
		if (!onCentre && actions.canReach(centre)) {
			actions.stopWalking();
			return true;
		}
		actions.walkTo(centre.offset(2, 1, 0), 0.8);
		return false;
	}

	private TaskStatus dig(CompanionEntity c, ServerLevel level, BlockPos centre) {
		BlockState state = level.getBlockState(centre);
		if (state.isAir()) {
			phase = Phase.POUR;
			return TaskStatus.RUNNING;
		}
		if (!standBeside(c, centre)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		BlockState above = level.getBlockState(centre.above());
		if (!above.isAir()) {
			return c.actions().mine(centre.above(), Reason.LANDSCAPE) == Actions.Result.FAILED ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		if (Crops.isTillable(state)) {
			// Turning the centre into farmland makes it Fern's own block, which she may then dig out.
			EditSteps.Step step = EditSteps.transform(c, centre, Blocks.FARMLAND.defaultBlockState(), Reason.FARM,
				c.actions().hasTool(ItemTags.HOES) ? ItemTags.HOES : null);
			return step == EditSteps.Step.FAILED ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		if (state.is(Blocks.FARMLAND)) {
			return c.actions().mine(centre, Reason.BUILD) == Actions.Result.FAILED ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		return TaskStatus.FAILURE;
	}

	private TaskStatus pour(CompanionEntity c, ServerLevel level, BlockPos centre) {
		if (!level.getBlockState(centre).isAir()) {
			return TaskStatus.FAILURE;
		}
		if (!standBeside(c, centre)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		return switch (EditSteps.place(c, centre, Blocks.WATER.defaultBlockState(), s -> s.is(Items.WATER_BUCKET), Reason.FARM)) {
			case DONE -> {
				ItemStack left = c.backpack().insert(new ItemStack(Items.BUCKET));
				if (!left.isEmpty()) {
					c.spawnAtLocation(level, left);
				}
				CampData data = Camp.data(level.getServer());
				data.putSite(Structures.FARM_PLOT, new CampData.Site(centre, 0, 0));
				farm.noteWater(centre);
				farm.invalidate();
				yield TaskStatus.SUCCESS;
			}
			case WAIT -> TaskStatus.RUNNING;
			case FAILED -> TaskStatus.FAILURE;
		};
	}

	@Override
	public void stop(CompanionEntity c) {
		site = null;
	}
}
