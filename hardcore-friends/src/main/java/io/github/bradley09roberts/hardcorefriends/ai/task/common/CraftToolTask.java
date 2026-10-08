package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Makes a replacement role tool at the camp crafting table when none is carried and the chest has none spare.
 * Ingredients (sticks, planks, logs, cobblestone, iron) come from the backpack and the supply chest and are really
 * consumed. If no tool can be made the friend says so and waits a good while before trying again.
 */
public final class CraftToolTask implements CompanionTask {
	private static final int TABLE_SEARCH_RADIUS = 8;
	private static final int TABLE_RESCAN_TICKS = 200;

	private static final Predicate<ItemStack> STICKS = s -> s.is(Items.STICK);
	private static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
	private static final Predicate<ItemStack> LOGS = s -> s.is(ItemTags.LOGS);
	private static final Predicate<ItemStack> STONE = s -> s.is(ItemTags.STONE_TOOL_MATERIALS);
	private static final Predicate<ItemStack> IRON = s -> s.is(Items.IRON_INGOT);

	private enum Phase {
		FETCH,
		TO_TABLE
	}

	private @Nullable BlockPos cachedTable;
	private long tableCheckedAt = -100_000;
	private Phase phase = Phase.FETCH;
	private @Nullable BlockPos tablePos;
	/** The tool this run makes: for their speciality, or for one they cover. */
	private @Nullable TagKey<Item> tool;
	private @Nullable BlockPos chestPos;

	@Override
	public String id() {
		return "common.craft_tool";
	}

	@Override
	public String describe() {
		return "crafting a tool";
	}

	@Override
	public double score(CompanionEntity c) {
		TagKey<Item> tool = KeepList.missingTool(c);
		if (tool == null) {
			return 0;
		}
		ServerLevel level = (ServerLevel) c.level();
		Optional<Container> chest = SupplyChest.of(level);
		if (chest.isPresent() && SupplyChest.count(chest.get(), s -> s.is(tool)) > 0) {
			return 0; // restocking will fetch it
		}
		boolean cover = KeepList.isCoverTool(c, tool);
		if (findTable(c) == null || affordable(c.backpack(), chest.orElse(null), tool).isEmpty()) {
			if (!cover) {
				Speech.say(c, Line.NEED_TOOL, KeepList.toolName(tool));
			}
			return 0;
		}
		// A tool for a speciality they only cover is made in spare time, after their own work.
		return cover ? 25 : 65;
	}

	@Override
	public boolean start(CompanionEntity c) {
		tablePos = findTable(c);
		chestPos = Upkeep.chestPos((ServerLevel) c.level()).orElse(null);
		phase = chestPos != null ? Phase.FETCH : Phase.TO_TABLE;
		tool = KeepList.missingTool(c);
		return tablePos != null && tool != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		TagKey<Item> tool = this.tool;
		if (tool == null || tablePos == null) {
			return TaskStatus.FAILURE;
		}
		if (c.actions().hasTool(tool)) {
			return TaskStatus.SUCCESS; // someone handed one over meanwhile
		}
		ServerLevel level = (ServerLevel) c.level();
		if (phase == Phase.FETCH) {
			if (chestPos == null || (Crafting.affordableTier(c.backpack(), tool).isPresent() && !chestHasBetter(c, tool))) {
				phase = Phase.TO_TABLE;
				return TaskStatus.RUNNING;
			}
			if (!c.actions().walkTo(chestPos, Upkeep.CHEST_REACH)) {
				return c.actions().isStuck() ? giveUp(c, tool) : TaskStatus.RUNNING;
			}
			Optional<Container> chest = SupplyChest.at(level, chestPos);
			if (chest.isPresent()) {
				c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
				fetchMaterials(c, chest.get(), tool);
				c.swingArm();
			}
			phase = Phase.TO_TABLE;
			return TaskStatus.RUNNING;
		}
		if (Crafting.affordableTier(c.backpack(), tool).isEmpty()) {
			return giveUp(c, tool);
		}
		if (!level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)) {
			cachedTable = null;
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(tablePos, 2.0)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(tablePos));
		if (!Crafting.craftTool(c, tool)) {
			return giveUp(c, tool);
		}
		c.swingArm();
		Upkeep.equipBest(c, s -> s.is(tool));
		Camp.data(level.getServer()).addStat("tools_crafted", 1);
		return TaskStatus.SUCCESS;
	}

	private TaskStatus giveUp(CompanionEntity c, TagKey<Item> tool) {
		Speech.say(c, Line.NEED_TOOL, KeepList.toolName(tool));
		return TaskStatus.FAILURE;
	}

	/** True when the chest would let the friend make a better tool than the backpack alone. */
	private boolean chestHasBetter(CompanionEntity c, TagKey<Item> tool) {
		Optional<Container> chest = SupplyChest.of((ServerLevel) c.level());
		if (chest.isEmpty()) {
			return false;
		}
		Optional<Crafting.Tier> own = Crafting.affordableTier(c.backpack(), tool);
		Optional<Crafting.Tier> pooled = affordable(c.backpack(), chest.get(), tool);
		return pooled.isPresent() && (own.isEmpty() || pooled.get().ordinal() > own.get().ordinal());
	}

	// ------------------------------------------------------------ materials

	/** The best tier craftable from the backpack plus the chest, without moving anything. */
	static Optional<Crafting.Tier> affordable(Backpack backpack, @Nullable Container chest, TagKey<Item> tool) {
		Backpack pool = new Backpack();
		pool.setCapacity(Backpack.MAX_SLOTS);
		addToPool(pool, backpack, chest, STICKS, Items.STICK);
		addToPool(pool, backpack, chest, PLANKS, Items.OAK_PLANKS);
		addToPool(pool, backpack, chest, LOGS, Items.OAK_LOG);
		addToPool(pool, backpack, chest, STONE, Items.COBBLESTONE);
		addToPool(pool, backpack, chest, IRON, Items.IRON_INGOT);
		return Crafting.affordableTier(pool, tool);
	}

	private static void addToPool(Backpack pool, Backpack backpack, @Nullable Container chest, Predicate<ItemStack> filter, Item sample) {
		int total = backpack.count(filter) + (chest == null ? 0 : SupplyChest.count(chest, filter));
		if (total > 0) {
			pool.insert(new ItemStack(sample, Math.min(total, 64)));
		}
	}

	/** Withdraws just enough ingredients for the best tool the chest and backpack can make together. */
	private static void fetchMaterials(CompanionEntity c, Container chest, TagKey<Item> tool) {
		Optional<Crafting.Tier> tier = affordable(c.backpack(), chest, tool);
		if (tier.isEmpty()) {
			return;
		}
		Backpack bp = c.backpack();
		int head = headCount(tool);
		int sticks = tool == ItemTags.SWORDS ? 1 : 2;
		switch (tier.get()) {
			case IRON -> withdraw(chest, bp, IRON, head - bp.count(IRON));
			case STONE -> withdraw(chest, bp, STONE, head - bp.count(STONE));
			case WOOD -> {
				int short1 = head + 2 - bp.count(PLANKS) - 4 * bp.count(LOGS);
				withdraw(chest, bp, PLANKS, short1);
				int short2 = head + 2 - bp.count(PLANKS) - 4 * bp.count(LOGS);
				withdraw(chest, bp, LOGS, (short2 + 3) / 4);
			}
		}
		if (bp.count(STICKS) < sticks) {
			withdraw(chest, bp, STICKS, sticks - bp.count(STICKS));
		}
		if (bp.count(STICKS) < sticks && bp.count(PLANKS) < 2 && !bp.has(LOGS)) {
			withdraw(chest, bp, PLANKS, 2 - bp.count(PLANKS));
			if (bp.count(PLANKS) < 2) {
				withdraw(chest, bp, LOGS, 1);
			}
		}
	}

	private static void withdraw(Container chest, Backpack bp, Predicate<ItemStack> filter, int amount) {
		if (amount > 0) {
			SupplyChest.withdraw(chest, bp, filter, amount);
		}
	}

	private static int headCount(TagKey<Item> tool) {
		if (tool == ItemTags.PICKAXES || tool == ItemTags.AXES) {
			return 3;
		}
		if (tool == ItemTags.HOES || tool == ItemTags.SWORDS) {
			return 2;
		}
		return 1;
	}

	// --------------------------------------------------------------- table

	/** A crafting table near the supply chest or the camp centre. Cached and rescanned every 10 seconds. */
	@Nullable BlockPos findTable(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (cachedTable != null) {
			if (now - tableCheckedAt < TABLE_RESCAN_TICKS * 6 && level.isLoaded(cachedTable)
				&& level.getBlockState(cachedTable).is(Blocks.CRAFTING_TABLE)) {
				return cachedTable;
			}
		} else if (now - tableCheckedAt < TABLE_RESCAN_TICKS) {
			return null;
		}
		tableCheckedAt = now;
		cachedTable = null;
		Optional<BlockPos> chest = Upkeep.chestPos(level);
		if (chest.isPresent()) {
			cachedTable = scan(level, chest.get());
		}
		if (cachedTable == null) {
			cachedTable = scan(level, c.homePos());
		}
		return cachedTable;
	}

	private static @Nullable BlockPos scan(ServerLevel level, BlockPos centre) {
		int r = TABLE_SEARCH_RADIUS;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -3, -r), centre.offset(r, 3, r))) {
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
				return p.immutable();
			}
		}
		return null;
	}

	@Override
	public void stop(CompanionEntity c) {
		tablePos = null;
		chestPos = null;
		phase = Phase.FETCH;
	}

	@Override
	public int failureCooldown() {
		return 2400;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
