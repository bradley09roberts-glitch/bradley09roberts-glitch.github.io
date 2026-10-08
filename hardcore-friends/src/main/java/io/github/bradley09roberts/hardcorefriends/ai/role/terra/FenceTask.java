package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampProgress;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * Once the camp is a Village, Terra fences the farm: wooden fences along the {@link FarmFence} line with one fence
 * gate, left open so friends can walk in and out, standing outside the line while working so nobody gets shut in.
 * Fences and the gate come from the backpack or the supply chest, or are crafted at a crafting table from planks,
 * logs and sticks. Cells that are not open ground (water, trees, buildings, a player's path or anything near a
 * player's build) are skipped. When every open cell has its fence, the farm fence is finished. Only farmland the
 * friends tilled is fenced; when the camp's fields are all the player's, there is nothing to fence and the farm fence
 * no longer holds the camp back.
 */
public final class FenceTask implements CompanionTask {
	private static final int PER_RUN = 24;
	private static final int SCAN_INTERVAL = 200;
	private static final int TABLE_RADIUS = 8;
	private static final double CHEST_REACH = 2.0;
	private static final int MAX_DEFERRALS = 2;

	private static final Predicate<ItemStack> FENCE = s -> s.is(ItemTags.WOODEN_FENCES) && s.getItem() instanceof BlockItem;
	private static final Predicate<ItemStack> GATE = s -> s.is(ItemTags.FENCE_GATES) && s.getItem() instanceof BlockItem;
	private static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
	private static final Predicate<ItemStack> LOGS = s -> Crafting.planksFor(s).isPresent();
	private static final Predicate<ItemStack> STICKS = s -> s.is(Items.STICK);

	private enum Phase {
		FETCH,
		CRAFT,
		PLACE
	}

	private FarmFence.@Nullable Plan plan;
	private final List<FarmFence.Cell> todo = new ArrayList<>();
	private boolean gateTodo;
	private int doneCells;
	private long scannedAt = -100_000;
	private @Nullable BlockPos table;

	private @Nullable Phase phase;
	private boolean fetched;
	private boolean crafted;
	private final List<FarmFence.Cell> queue = new ArrayList<>();
	private final Map<BlockPos, Integer> deferrals = new HashMap<>();
	private FarmFence.@Nullable Cell current;
	private int placed;

	@Override
	public String id() {
		return "terra.fence";
	}

	@Override
	public String describe() {
		return "fencing the farm";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (data.stage() < 3 || data.isCompleted(Structures.FARM_FENCE) || !Camp.isCampLevel(level, data)) {
			return 0;
		}
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		if (level.getGameTime() - scannedAt >= SCAN_INTERVAL) {
			scannedAt = level.getGameTime();
			survey(level, data);
			table = findTable(level, data);
			if (plan == null && data.isCompleted(Structures.FARM_PLOT) && FarmFence.nothingToFence(level, data)) {
				// The camp's fields are the player's own: there is no farm of ours to fence, so the camp need not wait.
				data.markCompleted(Structures.FARM_FENCE);
				return 0;
			}
		}
		checkFinished(c, data);
		if (todo.isEmpty()) {
			return 0;
		}
		if (!materialsAvailable(c)) {
			Speech.say(c, Line.NEED_MATERIALS, "fences or planks and sticks");
			return 0;
		}
		return 45;
	}

	/** Re-plans the fence and sorts its cells into done and still to do. */
	private void survey(ServerLevel level, CampData data) {
		plan = FarmFence.plan(level, data);
		todo.clear();
		gateTodo = false;
		doneCells = 0;
		if (plan == null) {
			return;
		}
		for (FarmFence.Cell cell : plan.cells()) {
			FarmFence.Status status = FarmFence.status(level, data, cell);
			if (status == FarmFence.Status.DONE) {
				doneCells++;
			} else if (status == FarmFence.Status.TODO) {
				todo.add(cell);
				gateTodo |= cell.gate();
			}
		}
	}

	private void checkFinished(CompanionEntity c, CampData data) {
		if (plan != null && todo.isEmpty() && doneCells >= 4 && !data.isCompleted(Structures.FARM_FENCE)) {
			CampProgress.complete(c, Structures.FARM_FENCE);
		}
	}

	private int fencesNeeded() {
		int n = 0;
		for (FarmFence.Cell cell : todo) {
			if (!cell.gate()) {
				n++;
			}
		}
		return n;
	}

	private boolean materialsAvailable(CompanionEntity c) {
		Backpack bp = c.backpack();
		if (bp.has(FENCE) || (gateTodo && bp.has(GATE)) || chestHas(c, FENCE) || (gateTodo && chestHas(c, GATE))) {
			return true;
		}
		return table != null && (bp.has(PLANKS.or(LOGS)) || chestHas(c, PLANKS.or(LOGS)));
	}

	private static boolean chestHas(CompanionEntity c, Predicate<ItemStack> filter) {
		return ChestFetch.chestHas(c, filter);
	}

	/** A crafting table near the supply chest or the camp centre. */
	private static @Nullable BlockPos findTable(ServerLevel level, CampData data) {
		List<BlockPos> centres = new ArrayList<>();
		data.chestPos().ifPresent(centres::add);
		data.campPos().ifPresent(centres::add);
		for (BlockPos centre : centres) {
			for (BlockPos p : BlockPos.betweenClosed(centre.offset(-TABLE_RADIUS, -3, -TABLE_RADIUS),
				centre.offset(TABLE_RADIUS, 3, TABLE_RADIUS))) {
				if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
					return p.immutable();
				}
			}
		}
		return null;
	}

	// ----------------------------------------------------------------- run

	@Override
	public boolean start(CompanionEntity c) {
		queue.clear();
		deferrals.clear();
		current = null;
		placed = 0;
		fetched = false;
		crafted = false;
		if (todo.isEmpty()) {
			return false;
		}
		queue.addAll(todo);
		phase = nextPhase(c);
		if (phase == null) {
			return false;
		}
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	/** What to do next: fetch from the chest, craft at the table, or place what is carried. Null when stuck. */
	private @Nullable Phase nextPhase(CompanionEntity c) {
		Backpack bp = c.backpack();
		int fencesShort = Math.max(0, Math.min(PER_RUN, fencesNeeded()) - bp.count(FENCE));
		boolean gateShort = gateTodo && !bp.has(GATE);
		boolean chestFences = fencesShort > 0 && chestHas(c, FENCE);
		boolean chestGate = gateShort && chestHas(c, GATE);
		boolean chestWood = (fencesShort > 0 || gateShort) && table != null && chestHas(c, PLANKS.or(LOGS));
		if (!fetched && (chestFences || chestGate || chestWood)) {
			return Phase.FETCH;
		}
		if (!crafted && (fencesShort > 0 || gateShort) && canCraft(c)) {
			return Phase.CRAFT;
		}
		if (bp.has(FENCE) || (gateTodo && bp.has(GATE))) {
			return Phase.PLACE;
		}
		return null;
	}

	/** True if a crafting table is known and wood is carried. */
	private boolean canCraft(CompanionEntity c) {
		return table != null && c.backpack().has(PLANKS.or(LOGS));
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (phase == null) {
			return finish(c, level);
		}
		return switch (phase) {
			case FETCH -> fetch(c, level);
			case CRAFT -> craft(c, level);
			case PLACE -> place(c, level);
		};
	}

	/** At the chest: takes fences and a gate first, then the wood to make what is still missing. */
	private TaskStatus fetch(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		Optional<BlockPos> pos = data.chestPos();
		Optional<Container> chest = SupplyChest.of(level);
		if (pos.isEmpty() || chest.isEmpty()) {
			fetched = true;
			phase = nextPhase(c);
			return TaskStatus.RUNNING;
		}
		if (!c.actions().walkTo(pos.get(), CHEST_REACH)) {
			if (c.actions().isStuck()) {
				fetched = true;
				phase = nextPhase(c);
			}
			return TaskStatus.RUNNING;
		}
		Backpack bp = c.backpack();
		Container box = chest.get();
		int fencesWanted = Math.min(PER_RUN, fencesNeeded());
		SupplyChest.withdraw(box, bp, FENCE, Math.max(0, fencesWanted - bp.count(FENCE)));
		if (gateTodo && !bp.has(GATE)) {
			SupplyChest.withdraw(box, bp, GATE, 1);
		}
		int fencesShort = Math.max(0, fencesWanted - bp.count(FENCE));
		boolean gateShort = gateTodo && !bp.has(GATE);
		if (table != null && (fencesShort > 0 || gateShort)) {
			// 3 fences take 4 planks and 2 sticks (1 plank); a gate takes 2 planks and 4 sticks (2 planks).
			int crafts = (fencesShort + 2) / 3;
			int planksWanted = Math.min(64, crafts * 5 + (gateShort ? 4 : 0));
			int planksHeld = bp.count(PLANKS) + bp.count(STICKS) / 2 + 4 * bp.count(LOGS);
			if (planksHeld < planksWanted) {
				SupplyChest.withdraw(box, bp, STICKS, Math.min(32, crafts * 2 + (gateShort ? 4 : 0)));
				planksHeld = bp.count(PLANKS) + bp.count(STICKS) / 2 + 4 * bp.count(LOGS);
			}
			if (planksHeld < planksWanted) {
				SupplyChest.withdraw(box, bp, PLANKS, planksWanted - planksHeld);
				planksHeld = bp.count(PLANKS) + bp.count(STICKS) / 2 + 4 * bp.count(LOGS);
			}
			if (planksHeld < planksWanted) {
				SupplyChest.withdraw(box, bp, LOGS, (planksWanted - planksHeld + 3) / 4);
			}
		}
		BlockPos p = pos.get();
		c.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
		c.swingArm();
		fetched = true;
		phase = nextPhase(c);
		return TaskStatus.RUNNING;
	}

	/** At the crafting table: makes the fences (and gate) still missing from carried wood. */
	private TaskStatus craft(CompanionEntity c, ServerLevel level) {
		BlockPos at = table;
		if (at == null || !level.getBlockState(at).is(Blocks.CRAFTING_TABLE)) {
			crafted = true;
			phase = nextPhase(c);
			return TaskStatus.RUNNING;
		}
		if (!c.actions().walkTo(at, 2.5)) {
			if (c.actions().isStuck()) {
				crafted = true;
				phase = nextPhase(c);
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		c.getLookControl().setLookAt(Vec3.atCenterOf(at));
		Backpack bp = c.backpack();
		if (gateTodo && !bp.has(GATE)) {
			Crafting.craftWood(c, Crafting.WoodShape.FENCE_GATE, 1);
		}
		int fencesShort = Math.max(0, Math.min(PER_RUN, fencesNeeded()) - bp.count(FENCE));
		if (fencesShort > 0) {
			Crafting.craftWood(c, Crafting.WoodShape.FENCE, fencesShort);
		}
		c.swingArm();
		crafted = true;
		phase = nextPhase(c);
		if (phase == null) {
			Speech.say(c, Line.NEED_MATERIALS, "planks and sticks for fences");
		}
		return TaskStatus.RUNNING;
	}

	/** Walks the fence line placing fences, standing just outside the line for each one. */
	private TaskStatus place(CompanionEntity c, ServerLevel level) {
		if (current == null) {
			if (placed >= PER_RUN) {
				return finish(c, level);
			}
			current = pickNext(c);
			if (current == null) {
				return finish(c, level);
			}
		}
		FarmFence.Cell cell = current;
		BlockPos pos = cell.pos();
		FarmFence.Status status = FarmFence.status(level, Camp.data(level.getServer()), cell);
		if (status != FarmFence.Status.TODO) {
			current = null;
			return TaskStatus.RUNNING;
		}
		BlockPos stand = pos.relative(cell.outward());
		boolean inside = c.getBoundingBox().intersects(new AABB(pos));
		if (inside || !c.actions().canReach(pos) || c.position().distanceToSqr(Vec3.atBottomCenterOf(stand)) > 2.5 * 2.5) {
			c.actions().walkTo(stand, inside ? 0.5 : 1.2);
			if (c.actions().isStuck()) {
				c.actions().stopWalking();
				defer(cell);
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		Predicate<ItemStack> material = cell.gate() ? GATE : FENCE;
		ItemStack stock = c.backpack().find(material);
		if (stock.isEmpty() || !(stock.getItem() instanceof BlockItem blockItem)) {
			current = null; // not carrying this kind; it waits for the next run
			return TaskStatus.RUNNING;
		}
		BlockState state = blockItem.getBlock().defaultBlockState();
		if (cell.gate()) {
			// Left open: friends cannot open gates, and a shut one would lock Fern in or out of her farm.
			state = state.setValue(FenceGateBlock.FACING, cell.outward()).setValue(FenceGateBlock.OPEN, true);
		}
		state = Block.updateFromNeighbourShapes(state, level, pos);
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, pos, state, Reason.LANDSCAPE);
		if (!verdict.allowed()) {
			if (!"pacing".equals(verdict.why())) {
				defer(cell);
			}
			return TaskStatus.RUNNING;
		}
		Item item = stock.getItem();
		if (c.actions().place(pos, state, s -> s.is(item), Reason.LANDSCAPE)) {
			placed++;
			current = null;
		} else {
			defer(cell);
		}
		return TaskStatus.RUNNING;
	}

	/** Puts a cell that could not be done right now (someone in the way, no path) to the back of the queue. */
	private void defer(FarmFence.Cell cell) {
		current = null;
		int tries = deferrals.merge(cell.pos(), 1, Integer::sum);
		if (tries <= MAX_DEFERRALS) {
			queue.add(cell);
		}
	}

	/** The nearest remaining cell Terra has material for. */
	private FarmFence.@Nullable Cell pickNext(CompanionEntity c) {
		Backpack bp = c.backpack();
		boolean haveFence = bp.has(FENCE);
		boolean haveGate = bp.has(GATE);
		FarmFence.Cell best = null;
		double bestDist = Double.MAX_VALUE;
		for (FarmFence.Cell cell : queue) {
			if (cell.gate() ? !haveGate : !haveFence) {
				continue;
			}
			double d = cell.pos().distSqr(c.blockPosition());
			if (d < bestDist) {
				bestDist = d;
				best = cell;
			}
		}
		if (best != null) {
			queue.remove(best);
		}
		return best;
	}

	private TaskStatus finish(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		survey(level, data);
		scannedAt = level.getGameTime();
		checkFinished(c, data);
		if (placed > 0) {
			data.addStat("fences_placed", placed);
			return TaskStatus.SUCCESS;
		}
		return TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		queue.clear();
		deferrals.clear();
		current = null;
		phase = null;
		placed = 0;
	}

	@Override
	public int successCooldown() {
		return 60;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}
