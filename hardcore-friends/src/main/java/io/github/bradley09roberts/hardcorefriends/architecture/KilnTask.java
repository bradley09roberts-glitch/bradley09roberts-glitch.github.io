package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.CampFurnace;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.SmeltTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Making building materials at the camp furnace: glass from sand, stone from cobblestone, smooth stone from stone,
 * bricks from clay and smooth sandstone from sandstone, whichever the buildings under way are short of
 * ({@link MaterialDemand}) and the camp has the makings of. The friend takes the makings and enough fuel from the
 * supply chest (coal or charcoal; planks only when there are plenty) and loads the friends' own furnace, only when its
 * input is empty or holds the same thing and its output is free for what it makes, so nobody's smelting is disturbed
 * and a player's furnace is never used. The results are collected by the usual furnace job and put in the chest.
 */
public final class KilnTask implements CompanionTask {
	public static final String ID = "oak.kiln";
	private static final Stock[] SMELTED = {Stock.GLASS, Stock.STONE, Stock.SMOOTH_STONE, Stock.BRICK, Stock.SMOOTH_SANDSTONE};
	private static final int RECHECK = 100;
	/** Planks go in the furnace only when the chest holds at least this many: they are building material first. */
	private static final int SPARE_PLANKS = 48;
	private static final int MAX_LOAD = 32;
	/** Half-smelts one plank burns for (see {@link SmeltTask#halfSmeltsPer}). */
	private static final int PLANK_HALF_SMELTS = 3;

	private enum Phase {
		CHEST,
		FURNACE
	}

	/** One load decided on: what to make, from which item, how many. */
	private record Work(Stock making, Item from, int count) {
	}

	private final CampFurnace furnace = new CampFurnace();
	private @Nullable Work work;
	private long checkedAt = Long.MIN_VALUE / 2;
	private Phase phase = Phase.CHEST;
	/** Planks taken from the chest's spare ones as fuel for this load: the only planks that may go in the furnace. */
	private int fuelPlanks;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Work w = work;
		return w == null ? "making building materials" : "making " + w.making().describe(2).substring(2);
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - checkedAt >= RECHECK || now < checkedAt) {
			checkedAt = now;
			work = choose(c, level);
		}
		return work == null ? 0 : 44 * CampNeeds.weight(CampNeeds.Need.BUILD);
	}

	/** What to smelt now, if the buildings need it, the camp has its makings and fuel, and the furnace is free for it. */
	private @Nullable Work choose(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return null;
		}
		BlockPos pos = furnace.pos(c);
		AbstractFurnaceBlockEntity f = pos == null ? null : CampFurnace.furnaceAt(level, pos);
		if (f == null || !data.isPlacedByFriends(level, pos)) {
			return null; // only the friends' own furnace
		}
		Container chest = SupplyChest.of(level).orElse(null);
		if (!hasFuel(c, chest)) {
			return null;
		}
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		ItemStack output = f.getItem(CampFurnace.SLOT_RESULT);
		for (Stock making : SMELTED) {
			int lack = MaterialDemand.missing(level.getServer(), making);
			Stock from = making.smeltedFrom();
			Item result = making.smeltedItem();
			if (lack <= 0 || from == null || result == null) {
				continue;
			}
			if (!output.isEmpty() && !output.is(result)) {
				continue;
			}
			Item fromItem = itemOf(c, chest, from, input);
			if (fromItem == null) {
				continue;
			}
			int room = input.isEmpty() ? 64 : input.getMaxStackSize() - input.getCount();
			int have = c.backpack().count(fromItem) + (chest == null ? 0 : SupplyChest.count(chest, s -> s.is(fromItem)));
			int count = Math.min(Math.min(lack, MAX_LOAD), Math.min(room, have));
			if (count > 0) {
				return new Work(making, fromItem, count);
			}
		}
		return null;
	}

	/** The item to load for a kind (sand or red sand...): what the input slot already holds, or one the camp has. */
	private static @Nullable Item itemOf(CompanionEntity c, @Nullable Container chest, Stock from, ItemStack input) {
		if (!input.isEmpty()) {
			return from.matches(input) ? input.getItem() : null;
		}
		ItemStack carried = c.backpack().find(from::matches);
		if (!carried.isEmpty()) {
			return carried.getItem();
		}
		if (chest != null) {
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack s = chest.getItem(i);
				if (from.matches(s)) {
					return s.getItem();
				}
			}
		}
		return null;
	}

	private static boolean hasFuel(CompanionEntity c, @Nullable Container chest) {
		if (c.backpack().has(s -> s.is(ItemTags.COALS))) {
			return true;
		}
		return chest != null && (SupplyChest.count(chest, s -> s.is(ItemTags.COALS)) > 0
			|| SupplyChest.count(chest, s -> s.is(ItemTags.PLANKS)) >= SPARE_PLANKS);
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		work = choose(c, level);
		if (work == null) {
			return false;
		}
		fuelPlanks = 0;
		phase = carriesEnough(c, work) ? Phase.FURNACE : Phase.CHEST;
		Speech.say(c, Line.FIRING_KILN, work.making().describe(2).substring(2));
		return true;
	}

	private static boolean carriesEnough(CompanionEntity c, Work w) {
		return c.backpack().count(w.from()) >= w.count() && coalHalfSmelts(c.backpack()) >= w.count() * 2;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Work w = work;
		if (w == null) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (phase == Phase.CHEST) {
			switch (ChestWalk.tick(c)) {
				case WALKING -> {
					return TaskStatus.RUNNING;
				}
				case FAILED -> {
					return TaskStatus.FAILURE;
				}
				case ARRIVED -> {
					Optional<Container> chest = ChestWalk.chest(c);
					if (chest.isEmpty()) {
						return TaskStatus.FAILURE;
					}
					Backpack bp = c.backpack();
					int need = w.count() - bp.count(w.from());
					if (need > 0) {
						SupplyChest.withdraw(chest.get(), bp, s -> s.is(w.from()), need);
					}
					// Only coal and charcoal count as carried fuel: the builder's planks are for building.
					int halfSmelts = w.count() * 2;
					int carriedFuel = coalHalfSmelts(bp);
					if (carriedFuel < halfSmelts) {
						int coalNeeded = (halfSmelts - carriedFuel + 15) / 16;
						int got = SupplyChest.withdraw(chest.get(), bp, s -> s.is(ItemTags.COALS), coalNeeded);
						if (got < coalNeeded && SupplyChest.count(chest.get(), s -> s.is(ItemTags.PLANKS)) >= SPARE_PLANKS) {
							int planks = ((halfSmelts - coalHalfSmelts(bp)) + PLANK_HALF_SMELTS - 1) / PLANK_HALF_SMELTS;
							int spare = Math.min(planks, 24);
							fuelPlanks += SupplyChest.withdraw(chest.get(), bp, s -> s.is(ItemTags.PLANKS), spare);
						}
					}
					if (bp.count(w.from()) <= 0 || coalHalfSmelts(bp) <= 0 && fuelPlanks <= 0) {
						return TaskStatus.FAILURE;
					}
					phase = Phase.FURNACE;
				}
			}
			return TaskStatus.RUNNING;
		}
		BlockPos target = furnace.pos(c);
		if (target == null) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().canReach(target)) {
			c.actions().walkTo(target, 2.0);
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		AbstractFurnaceBlockEntity f = CampFurnace.furnaceAt(level, target);
		if (f == null || !Camp.data(level.getServer()).isPlacedByFriends(level, target)) {
			furnace.forget(c);
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(target));
		int loaded = load(c.backpack(), f, w);
		if (loaded <= 0) {
			return TaskStatus.FAILURE;
		}
		c.swingArm();
		level.playSound(null, target, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F);
		Camp.data(level.getServer()).addStat("materials_smelted", loaded);
		return TaskStatus.SUCCESS;
	}

	/** Coal and charcoal carried, in half-smelts (16 each; see {@link SmeltTask#halfSmeltsPer}). */
	private static int coalHalfSmelts(Backpack bp) {
		int total = 0;
		for (ItemStack s : bp.stacks()) {
			if (s.is(ItemTags.COALS)) {
				total += SmeltTask.halfSmeltsPer(s) * s.getCount();
			}
		}
		return total;
	}

	/**
	 * The fuel to put in: coal or charcoal, or planks only up to the spare ones taken from the chest for this load (never
	 * the builder's own building planks); the same as the fuel slot already holds, if it holds any. A slot of planks
	 * with no planks to spare gets nothing more: it burns down, and coal goes in next time.
	 */
	private ItemStack fuelFor(Backpack bp, ItemStack fuelSlot) {
		Predicate<ItemStack> usable = s -> s.is(ItemTags.COALS) || fuelPlanks > 0 && s.is(ItemTags.PLANKS);
		if (!fuelSlot.isEmpty()) {
			return bp.find(s -> usable.test(s) && ItemStack.isSameItemSameComponents(s, fuelSlot));
		}
		ItemStack coal = bp.find(s -> s.is(ItemTags.COALS));
		return coal.isEmpty() ? bp.find(usable) : coal;
	}

	/**
	 * Puts the makings in the input slot (empty, or holding the same) and enough fuel in the fuel slot (empty, or the
	 * same fuel) for everything queued. Returns how many items went in.
	 */
	private int load(Backpack bp, AbstractFurnaceBlockEntity f, Work w) {
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		if (!input.isEmpty() && !input.is(w.from())) {
			return 0;
		}
		Item result = w.making().smeltedItem();
		ItemStack output = f.getItem(CampFurnace.SLOT_RESULT);
		if (result == null || !output.isEmpty() && !output.is(result)) {
			return 0;
		}
		int room = input.isEmpty() ? w.from().getDefaultMaxStackSize() : input.getMaxStackSize() - input.getCount();
		ItemStack taken = bp.take(s -> s.is(w.from()), Math.min(room, w.count()));
		if (taken.isEmpty()) {
			return 0;
		}
		int loaded = taken.getCount();
		if (input.isEmpty()) {
			f.setItem(CampFurnace.SLOT_INPUT, taken);
		} else {
			input.grow(loaded);
			f.setItem(CampFurnace.SLOT_INPUT, input);
		}
		int queued = f.getItem(CampFurnace.SLOT_INPUT).getCount();
		ItemStack fuelSlot = f.getItem(CampFurnace.SLOT_FUEL);
		int needHalf = queued * 2 - SmeltTask.halfSmeltsPer(fuelSlot) * fuelSlot.getCount();
		if (needHalf > 0) {
			ItemStack fuel = fuelFor(bp, fuelSlot);
			int per = SmeltTask.halfSmeltsPer(fuel);
			if (!fuel.isEmpty() && per > 0 && f.canPlaceItem(CampFurnace.SLOT_FUEL, fuel)) {
				ItemStack template = fuel.copyWithCount(1);
				boolean planks = template.is(ItemTags.PLANKS);
				int want = (needHalf + per - 1) / per;
				if (planks) {
					want = Math.min(want, fuelPlanks);
				}
				int space = fuelSlot.isEmpty() ? template.getMaxStackSize() : fuelSlot.getMaxStackSize() - fuelSlot.getCount();
				ItemStack fuelTaken = bp.take(s -> ItemStack.isSameItemSameComponents(s, template), Math.min(want, space));
				if (!fuelTaken.isEmpty()) {
					if (planks) {
						fuelPlanks -= fuelTaken.getCount();
					}
					if (fuelSlot.isEmpty()) {
						f.setItem(CampFurnace.SLOT_FUEL, fuelTaken);
					} else {
						fuelSlot.grow(fuelTaken.getCount());
						f.setItem(CampFurnace.SLOT_FUEL, fuelSlot);
					}
				}
			}
		}
		f.setChanged();
		return loaded;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		phase = Phase.CHEST;
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
