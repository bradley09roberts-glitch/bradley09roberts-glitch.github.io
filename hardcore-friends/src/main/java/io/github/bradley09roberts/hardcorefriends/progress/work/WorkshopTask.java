package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.CampFurnace;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.SmeltTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.CampStock;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.progress.Stations;

/**
 * Sage's workshop: crafting what the plan needs from what the camp has gathered, a batch at a time, at the camp's
 * crafting table. Paper from sugar cane and books from paper and leather (for the library's shelves), a bucket for
 * obsidian, a flint and steel for the portal, glass bottles for brewing (and putting sand in the camp furnace to make
 * the glass), eyes of ender from ender pearls and blaze powder, and for the brewer magma cream and glistering melon.
 * The ingredients come out of the supply chest and the results go back in. Blaze rods are only ground into powder
 * beyond what the eyes of ender and a brewing stand still need.
 */
public final class WorkshopTask implements CompanionTask {
	private static final int MAX_CRAFTS = 6;

	/** One thing to take from the chest: matching items, how many. */
	private record Need(Predicate<ItemStack> match, int count) {
	}

	/** One batch: what it makes (and how many in all), from what, and whether it is sand for the furnace. */
	private record Job(String what, Item output, int count, List<Need> needs, boolean smelt) {
	}

	private enum Phase {
		FETCH,
		WORK,
		RETURN
	}

	private final CampFurnace furnace = new CampFurnace();
	private @Nullable Job job;
	private Phase phase = Phase.FETCH;
	private @Nullable BlockPos station;

	@Override
	public String id() {
		return "sage.workshop";
	}

	@Override
	public String describe() {
		Job j = job;
		return j == null ? "crafting for the plan" : "making " + j.what();
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			return 0;
		}
		return choose(c, level) != null ? 36 : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		job = choose(c, (ServerLevel) c.level());
		phase = Phase.FETCH;
		station = null;
		return job != null;
	}

	// ----------------------------------------------------------------- choice

	/** The first batch the plan wants and the chest can make, or null. Reads only the camp count (cheap). */
	private @Nullable Job choose(CompanionEntity c, ServerLevel level) {
		MinecraftServer server = level.getServer();
		CampStock.Snapshot s = CampStock.get(server);
		if (!s.hasChest()) {
			return null;
		}
		CampData camp = Camp.data(server);
		int books = ProgressPlan.wanted(server, Items.BOOK);
		if (books > 0 && s.inChest(Items.PAPER) >= 3 && s.inChest(Items.LEATHER) >= 1) {
			int n = Math.min(MAX_CRAFTS, Math.min(books, Math.min(s.inChest(Items.PAPER) / 3, s.inChest(Items.LEATHER))));
			return new Job("books", Items.BOOK, n, List.of(need(Items.PAPER, 3 * n), need(Items.LEATHER, n)), false);
		}
		int paper = ProgressPlan.wanted(server, Items.PAPER);
		if (paper > 0 && s.inChest(Items.SUGAR_CANE) >= 3) {
			int crafts = Math.min(MAX_CRAFTS, Math.min((paper + 2) / 3, s.inChest(Items.SUGAR_CANE) / 3));
			return new Job("paper", Items.PAPER, 3 * crafts, List.of(need(Items.SUGAR_CANE, 3 * crafts)), false);
		}
		if (ProgressPlan.wants(server, Items.BUCKET) && s.inChest(Items.IRON_INGOT) >= 3) {
			return new Job("a bucket", Items.BUCKET, 1, List.of(need(Items.IRON_INGOT, 3)), false);
		}
		if (ProgressPlan.wants(server, Items.FLINT_AND_STEEL) && s.inChest(Items.FLINT) >= 1 && s.inChest(Items.IRON_INGOT) >= 1) {
			return new Job("a flint and steel", Items.FLINT_AND_STEEL, 1, List.of(need(Items.FLINT, 1), need(Items.IRON_INGOT, 1)), false);
		}
		int bottles = ProgressPlan.wanted(server, Items.GLASS_BOTTLE);
		if (bottles > 0 && s.inChest(Items.GLASS) >= 3) {
			return new Job("glass bottles", Items.GLASS_BOTTLE, 3, List.of(need(Items.GLASS, 3)), false);
		}
		int eyes = ProgressPlan.wanted(server, Items.ENDER_EYE);
		int powder = s.inChest(Items.BLAZE_POWDER);
		int rods = s.inChest(Items.BLAZE_ROD);
		if (eyes > 0 && s.inChest(Items.ENDER_PEARL) >= 1 && powder + 2 * rods >= 1) {
			int n = Math.min(MAX_CRAFTS, Math.min(eyes, Math.min(s.inChest(Items.ENDER_PEARL), powder + 2 * rods)));
			int fromPowder = Math.min(powder, n);
			int rodsNeeded = (n - fromPowder + 1) / 2;
			List<Need> needs = new ArrayList<>(List.of(need(Items.ENDER_PEARL, n)));
			if (fromPowder > 0) {
				needs.add(need(Items.BLAZE_POWDER, fromPowder));
			}
			if (rodsNeeded > 0) {
				needs.add(need(Items.BLAZE_ROD, rodsNeeded));
			}
			return new Job("eyes of ender", Items.ENDER_EYE, n, needs, false);
		}
		boolean brewing = Stations.brewingStand(level) != null;
		int spare = sparePowder(server, s, camp);
		if (brewing && s.fireResistance() < 3 && s.inChest(Items.SLIME_BALL) >= 1 && s.inChest(Items.MAGMA_CREAM) == 0 && spare >= 1) {
			return new Job("magma cream", Items.MAGMA_CREAM, 1,
				List.of(need(Items.SLIME_BALL, 1), powder >= 1 ? need(Items.BLAZE_POWDER, 1) : need(Items.BLAZE_ROD, 1)), false);
		}
		if (brewing && s.inChest(Items.MELON_SLICE) >= 1 && s.inChest(Items.GLISTERING_MELON_SLICE) == 0
			&& (s.inChest(Items.GOLD_NUGGET) >= 8 || s.inChest(Items.GOLD_INGOT) >= 1)) {
			return new Job("glistering melon", Items.GLISTERING_MELON_SLICE, 1, List.of(need(Items.MELON_SLICE, 1),
				s.inChest(Items.GOLD_NUGGET) >= 8 ? need(Items.GOLD_NUGGET, 8) : need(Items.GOLD_INGOT, 1)), false);
		}
		if (brewing && s.total(Items.BLAZE_POWDER) == 0 && rods >= 1 && spare >= 2) {
			return new Job("blaze powder", Items.BLAZE_POWDER, 2, List.of(need(Items.BLAZE_ROD, 1)), false);
		}
		if (bottles > 0 && s.total(Items.GLASS) < 3 && s.inChest(Items.SAND) >= 3) {
			AbstractFurnaceBlockEntity f = ownFurnace(c, level);
			int coal = s.inChest(Items.COAL) + s.inChest(Items.CHARCOAL);
			if (f != null && f.getItem(CampFurnace.SLOT_INPUT).isEmpty() && coal >= 1) {
				int n = Math.min(8, s.inChest(Items.SAND));
				return new Job("glass", Items.GLASS, n, List.of(need(Items.SAND, n), new Need(st -> st.is(ItemTags.COALS), 1)), true);
			}
		}
		return null;
	}

	/**
	 * Blaze powder the camp can spare, counting a rod as two: what is left after the eyes of ender still to make (until
	 * that step of the plan is done) and the rod a brewing stand still needs.
	 */
	static int sparePowder(MinecraftServer server, CampStock.Snapshot s, CampData camp) {
		int reserve = 0;
		if (!ProgressPlan.isDone(server, Milestone.EYES_OF_ENDER)) {
			reserve += Math.max(0, ProgressPlan.EYES_WANTED - s.total(Items.ENDER_EYE));
		}
		if (!camp.isCompleted(Structures.BREWING_STAND) && s.total(Items.BREWING_STAND) == 0) {
			reserve += 2;
		}
		return 2 * s.total(Items.BLAZE_ROD) + s.total(Items.BLAZE_POWDER) - reserve;
	}

	private static Need need(Item item, int count) {
		return new Need(st -> st.is(item), count);
	}

	/** The camp furnace, only if the friends built it (glass is only collected from their own). */
	private @Nullable AbstractFurnaceBlockEntity ownFurnace(CompanionEntity c, ServerLevel level) {
		BlockPos pos = furnace.pos(c);
		if (pos == null || !Camp.data(level.getServer()).isPlacedByFriends(level, pos)) {
			return null;
		}
		return CampFurnace.furnaceAt(level, pos);
	}

	// ------------------------------------------------------------------ work

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Job j = job;
		if (j == null) {
			return TaskStatus.FAILURE;
		}
		return switch (phase) {
			case FETCH -> fetch(c, j);
			case WORK -> j.smelt() ? smelt(c, j) : craft(c, j);
			case RETURN -> putBack(c, j);
		};
	}

	private TaskStatus fetch(CompanionEntity c, Job j) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.FAILED) {
			return TaskStatus.FAILURE;
		}
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		for (Need n : j.needs()) {
			int have = c.backpack().count(n.match());
			Trips.take(chest.get(), c, n.match(), n.count() - have);
			if (c.backpack().count(n.match()) < n.count()) {
				returnAll(chest.get(), c, j);
				return TaskStatus.FAILURE; // someone took them meanwhile, or no room
			}
		}
		station = j.smelt() ? furnace.pos(c) : Trips.craftingTable(c);
		if (station == null) {
			returnAll(chest.get(), c, j);
			return TaskStatus.FAILURE;
		}
		phase = Phase.WORK;
		return TaskStatus.RUNNING;
	}

	private TaskStatus craft(CompanionEntity c, Job j) {
		BlockPos table = station;
		if (table == null) {
			return TaskStatus.FAILURE;
		}
		if (!Crafting.nearCraftingTable(c) && !Trips.reach(c, table)) {
			return c.actions().isStuck() ? back(c) : TaskStatus.RUNNING;
		}
		int before = c.backpack().count(j.output());
		Crafting.ensure(c, j.output(), before + j.count());
		int made = c.backpack().count(j.output()) - before;
		if (made > 0) {
			c.swingArm();
			Camp.data(((ServerLevel) c.level()).getServer()).addStat("plan_items_crafted", made);
			Speech.say(c, Line.WORK_START, describe());
		}
		return back(c);
	}

	private TaskStatus smelt(CompanionEntity c, Job j) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos pos = station;
		AbstractFurnaceBlockEntity f = pos == null ? null : ownFurnace(c, level);
		if (pos == null || f == null) {
			return back(c);
		}
		if (!Trips.reach(c, pos)) {
			return c.actions().isStuck() ? back(c) : TaskStatus.RUNNING;
		}
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		if (input.isEmpty()) {
			ItemStack sand = c.backpack().take(st -> st.is(Items.SAND), j.count());
			if (!sand.isEmpty()) {
				f.setItem(CampFurnace.SLOT_INPUT, sand);
				addFuel(c, f, sand.getCount());
				f.setChanged();
				c.swingArm();
				level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F);
			}
		}
		return back(c);
	}

	/** Enough carried coal or planks in the fuel slot for {@code items} smelts, as the miner fuels ore. */
	private static void addFuel(CompanionEntity c, AbstractFurnaceBlockEntity f, int items) {
		ItemStack fuelSlot = f.getItem(CampFurnace.SLOT_FUEL);
		int needHalf = items * 2 - SmeltTask.halfSmeltsPer(fuelSlot) * fuelSlot.getCount();
		if (needHalf <= 0) {
			return;
		}
		ItemStack fuel = fuelSlot.isEmpty()
			? c.backpack().find(st -> st.is(ItemTags.COALS)).isEmpty() ? c.backpack().find(st -> st.is(ItemTags.PLANKS)) : c.backpack().find(st -> st.is(ItemTags.COALS))
			: c.backpack().find(st -> ItemStack.isSameItemSameComponents(st, fuelSlot));
		int per = SmeltTask.halfSmeltsPer(fuel);
		if (fuel.isEmpty() || per <= 0 || !f.canPlaceItem(CampFurnace.SLOT_FUEL, fuel)) {
			return;
		}
		ItemStack template = fuel.copyWithCount(1);
		int want = (needHalf + per - 1) / per;
		int space = fuelSlot.isEmpty() ? template.getMaxStackSize() : fuelSlot.getMaxStackSize() - fuelSlot.getCount();
		ItemStack taken = c.backpack().take(st -> ItemStack.isSameItemSameComponents(st, template), Math.min(want, space));
		if (taken.isEmpty()) {
			return;
		}
		if (fuelSlot.isEmpty()) {
			f.setItem(CampFurnace.SLOT_FUEL, taken);
		} else {
			fuelSlot.grow(taken.getCount());
			f.setItem(CampFurnace.SLOT_FUEL, fuelSlot);
		}
	}

	private TaskStatus back(CompanionEntity c) {
		phase = Phase.RETURN;
		return TaskStatus.RUNNING;
	}

	private TaskStatus putBack(CompanionEntity c, Job j) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (walk == ChestWalk.State.FAILED || chest.isEmpty()) {
			return TaskStatus.SUCCESS; // the deposit job takes it in later
		}
		returnAll(chest.get(), c, j);
		return TaskStatus.SUCCESS;
	}

	/** Puts what was made and any ingredients left over back in the chest. */
	private static void returnAll(Container chest, CompanionEntity c, Job j) {
		Trips.putBack(chest, c, st -> st.is(j.output()));
		for (Need n : j.needs()) {
			Trips.putBack(chest, c, n.match());
		}
		Trips.putBack(chest, c, st -> st.is(Items.BLAZE_POWDER) || st.is(Items.GOLD_NUGGET));
	}

	@Override
	public void stop(CompanionEntity c) {
		station = null;
	}

	@Override
	public int failureCooldown() {
		return 600;
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
