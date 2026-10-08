package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.CampStock;
import io.github.bradley09roberts.hardcorefriends.progress.Stations;
import io.github.bradley09roberts.hardcorefriends.progress.Tiers;
import io.github.bradley09roberts.hardcorefriends.progress.Water;

/**
 * Brewing at the friends' own brewing stand (Spark's speciality), with the stand's real workings, the way the cooks
 * use the furnace: water bottles go in (glass bottles filled at still water, which never uses the water up), then
 * nether wart to make awkward potions, then the reagent for the potion the camp wants most and has the makings of:
 * fire resistance (magma cream) for the Nether first, then healing (glistering melon), regeneration (ghast tear) and
 * strength (blaze powder the eyes of ender do not need). Every step is checked against the game's own brewing
 * recipes before anything goes in. Blaze powder fuels the stand when its fuel slot is empty. The stand brews on its own
 * (20 seconds a step); finished potions are taken back to the chest on a later visit. Only the stand the friends built
 * is used, never a player's.
 */
public final class BrewTask implements CompanionTask {
	private static final int SLOT_INGREDIENT = 3;
	private static final int SLOT_FUEL = 4;
	/** Reagents for the awkward potion, the camp's favourite first. */
	private static final Item[] REAGENTS = {Items.MAGMA_CREAM, Items.GLISTERING_MELON_SLICE, Items.GHAST_TEAR, Items.BLAZE_POWDER};

	private enum Phase {
		FETCH,
		FILL,
		STAND,
		RETURN
	}

	private Phase phase = Phase.FETCH;
	private @Nullable BlockPos stand;
	private @Nullable BlockPos water;
	private int bottlesWanted;
	private @Nullable Item reagent;
	private boolean fuel;
	/** Everything taken from the chest or off the stand this visit, so exactly that goes back. */
	private final Set<ItemStack> carried = Collections.newSetFromMap(new IdentityHashMap<>());
	private final List<String> brewed = new ArrayList<>();

	@Override
	public String id() {
		return "spark.brew";
	}

	@Override
	public String describe() {
		return "brewing potions";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || Camp.isNight(level)) {
			return 0;
		}
		BrewingStandBlockEntity be = standEntity(level);
		if (be == null) {
			return 0;
		}
		if (hasFinished(be) && be.getItem(SLOT_INGREDIENT).isEmpty()) {
			return 45;
		}
		return plan(c, level, be) ? 40 : 0;
	}

	private static @Nullable BrewingStandBlockEntity standEntity(ServerLevel level) {
		BlockPos pos = Stations.brewingStand(level);
		return pos != null && level.getBlockEntity(pos) instanceof BrewingStandBlockEntity be ? be : null;
	}

	/** A bottle slot holds something brewed (not plain water and not merely awkward). */
	private static boolean hasFinished(BrewingStandBlockEntity be) {
		for (int i = 0; i < 3; i++) {
			if (finished(be.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	private static boolean finished(ItemStack s) {
		if (s.isEmpty()) {
			return false;
		}
		PotionContents contents = s.get(DataComponents.POTION_CONTENTS);
		return contents == null || !contents.is(Potions.WATER) && !contents.is(Potions.AWKWARD);
	}

	/**
	 * Works out this visit (bottles to load, the reagent, fuel) from the stand and what the camp has; true when there
	 * is something useful to do. Never plans a reagent the game has no brewing recipe for.
	 */
	private boolean plan(CompanionEntity c, ServerLevel level, BrewingStandBlockEntity be) {
		MinecraftServer server = level.getServer();
		CampStock.Snapshot s = CampStock.get(server);
		CampData camp = Camp.data(server);
		bottlesWanted = 0;
		reagent = null;
		fuel = false;
		boolean ingredientFree = be.getItem(SLOT_INGREDIENT).isEmpty();
		if (!ingredientFree) {
			fuel = be.getItem(SLOT_FUEL).isEmpty() && spare(server, s, camp) >= 1 && available(c, s, Items.BLAZE_POWDER);
			return fuel; // brewing (or waiting for fuel): leave it be
		}
		ItemStack sample = ItemStack.EMPTY;
		int empty = 0;
		for (int i = 0; i < 3; i++) {
			ItemStack b = be.getItem(i);
			if (b.isEmpty()) {
				empty++;
			} else if (!finished(b) && sample.isEmpty()) {
				sample = b;
			}
		}
		if (sample.isEmpty()) {
			if (empty < 3 || !available(c, s, Items.NETHER_WART)) {
				return false; // finished potions still in, or nothing to start a batch with
			}
			java.util.function.Predicate<ItemStack> bottle = st -> Water.isWaterBottle(st) || st.is(Items.GLASS_BOTTLE);
			if (c.backpack().count(bottle) + Trips.inChest(c, bottle) == 0) {
				return false;
			}
			bottlesWanted = 3;
			sample = PotionContents.createItemStack(Items.POTION, Potions.WATER);
		}
		for (Item r : candidates(sample, s, server, camp)) {
			if (available(c, s, r) && hasRecipe(level, sample, new ItemStack(r))) {
				reagent = r;
				break;
			}
		}
		if (reagent == null) {
			return false;
		}
		fuel = be.getItem(SLOT_FUEL).isEmpty() && spare(server, s, camp) >= 1 + (reagent == Items.BLAZE_POWDER ? 1 : 0)
			&& available(c, s, Items.BLAZE_POWDER);
		return true;
	}

	/** What may go in next: nether wart on water, the reagents (in the camp's order) on awkward potions. */
	private static List<Item> candidates(ItemStack bottle, CampStock.Snapshot s, MinecraftServer server, CampData camp) {
		PotionContents contents = bottle.get(DataComponents.POTION_CONTENTS);
		if (contents != null && contents.is(Potions.WATER)) {
			return List.of(Items.NETHER_WART);
		}
		List<Item> list = new ArrayList<>();
		for (Item r : REAGENTS) {
			if (r == Items.MAGMA_CREAM && s.fireResistance() >= 6) {
				continue; // enough for a Nether trip; something else now
			}
			if (r == Items.BLAZE_POWDER && spare(server, s, camp) < 2) {
				continue; // the eyes of ender come first
			}
			list.add(r);
		}
		return list;
	}

	private static int spare(MinecraftServer server, CampStock.Snapshot s, CampData camp) {
		return WorkshopTask.sparePowder(server, s, camp);
	}

	private static boolean available(CompanionEntity c, CampStock.Snapshot s, Item item) {
		return c.backpack().has(st -> st.is(item)) || s.inChest(item) > 0;
	}

	/** True when the game itself brews this bottle with this reagent. */
	private static boolean hasRecipe(ServerLevel level, ItemStack bottle, ItemStack reagentStack) {
		return level.recipeAccess().getRecipeFor(RecipeType.BREWING, new BrewingInput(bottle, reagentStack), level).isPresent();
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		stand = Stations.brewingStand(level);
		BrewingStandBlockEntity be = standEntity(level);
		if (stand == null || be == null) {
			return false;
		}
		carried.clear();
		brewed.clear();
		water = null;
		boolean collect = hasFinished(be) && be.getItem(SLOT_INGREDIENT).isEmpty();
		if (!plan(c, level, be) && !collect) {
			return false;
		}
		phase = needsChest(c) ? Phase.FETCH : Phase.STAND;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	private boolean needsChest(CompanionEntity c) {
		return bottlesWanted > 0 && c.backpack().count(Water::isWaterBottle) < bottlesWanted
			|| reagent != null && !c.backpack().has(st -> st.is(reagent))
			|| fuel && !c.backpack().has(st -> st.is(Items.BLAZE_POWDER));
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos at = stand;
		if (at == null) {
			return TaskStatus.FAILURE;
		}
		return switch (phase) {
			case FETCH -> fetch(c, level, at);
			case FILL -> fill(c);
			case STAND -> atStand(c, level, at);
			case RETURN -> putBack(c);
		};
	}

	private TaskStatus fetch(CompanionEntity c, ServerLevel level, BlockPos at) {
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
		Set<ItemStack> before = Collections.newSetFromMap(new IdentityHashMap<>());
		before.addAll(c.backpack().stacks());
		int room = Math.max(0, c.backpack().freeSlots() - 1);
		int bottles = Math.min(room, bottlesWanted - c.backpack().count(Water::isWaterBottle));
		if (bottles > 0) {
			bottles -= Trips.take(chest.get(), c, Water::isWaterBottle, bottles);
			if (bottles > 0) {
				Trips.take(chest.get(), c, st -> st.is(Items.GLASS_BOTTLE), bottles);
			}
		}
		Item r = reagent;
		if (r != null && !c.backpack().has(st -> st.is(r))) {
			Trips.take(chest.get(), c, st -> st.is(r), 1);
		}
		if (fuel && !c.backpack().has(st -> st.is(Items.BLAZE_POWDER))) {
			Trips.take(chest.get(), c, st -> st.is(Items.BLAZE_POWDER), 1);
		}
		for (ItemStack s : c.backpack().stacks()) {
			if (!before.contains(s)) {
				carried.add(s);
			}
		}
		if (c.backpack().has(st -> st.is(Items.GLASS_BOTTLE)) && c.backpack().count(Water::isWaterBottle) < bottlesWanted) {
			water = Water.find(level, at, false);
			if (water != null) {
				phase = Phase.FILL;
				return TaskStatus.RUNNING;
			}
		}
		phase = Phase.STAND;
		return TaskStatus.RUNNING;
	}

	private TaskStatus fill(CompanionEntity c) {
		BlockPos source = water;
		if (source == null) {
			phase = Phase.STAND;
			return TaskStatus.RUNNING;
		}
		if (!Trips.reach(c, source)) {
			if (c.actions().isStuck()) {
				phase = Phase.STAND;
			}
			return TaskStatus.RUNNING;
		}
		Set<ItemStack> before = Collections.newSetFromMap(new IdentityHashMap<>());
		before.addAll(c.backpack().stacks());
		Water.fillBottles(c, source, Math.max(0, bottlesWanted - c.backpack().count(Water::isWaterBottle)));
		for (ItemStack s : c.backpack().stacks()) {
			if (!before.contains(s)) {
				carried.add(s);
			}
		}
		phase = Phase.STAND;
		return TaskStatus.RUNNING;
	}

	private TaskStatus atStand(CompanionEntity c, ServerLevel level, BlockPos at) {
		if (!Trips.reach(c, at)) {
			if (c.actions().isStuck()) {
				phase = Phase.RETURN;
			}
			return TaskStatus.RUNNING;
		}
		if (!(level.getBlockEntity(at) instanceof BrewingStandBlockEntity be)) {
			phase = Phase.RETURN;
			return TaskStatus.RUNNING;
		}
		boolean changed = false;
		// Finished potions come off first.
		if (be.getItem(SLOT_INGREDIENT).isEmpty()) {
			for (int i = 0; i < 3; i++) {
				ItemStack b = be.getItem(i);
				if (finished(b)) {
					Set<ItemStack> before = Collections.newSetFromMap(new IdentityHashMap<>());
					before.addAll(c.backpack().stacks());
					String name = potionName(b);
					ItemStack left = c.backpack().insert(b.copy());
					be.setItem(i, left);
					for (ItemStack s : c.backpack().stacks()) {
						if (!before.contains(s)) {
							carried.add(s);
						}
					}
					if (left.isEmpty()) {
						brewed.add(name);
					}
					changed = true;
				}
			}
		}
		// Water bottles into empty slots, when nothing is brewing.
		if (be.getItem(SLOT_INGREDIENT).isEmpty() && bottlesWanted > 0) {
			for (int i = 0; i < 3; i++) {
				if (be.getItem(i).isEmpty()) {
					ItemStack bottle = c.backpack().take(Water::isWaterBottle, 1);
					if (!bottle.isEmpty()) {
						be.setItem(i, bottle);
						changed = true;
					}
				}
			}
		}
		// The reagent, if the game brews it with what is in the stand.
		Item r = reagent;
		if (r != null && be.getItem(SLOT_INGREDIENT).isEmpty()) {
			ItemStack sample = ItemStack.EMPTY;
			for (int i = 0; i < 3 && sample.isEmpty(); i++) {
				if (!be.getItem(i).isEmpty() && !finished(be.getItem(i))) {
					sample = be.getItem(i);
				}
			}
			ItemStack one = new ItemStack(r);
			if (!sample.isEmpty() && hasRecipe(level, sample, one) && be.canPlaceItem(SLOT_INGREDIENT, one)) {
				ItemStack taken = c.backpack().take(st -> st.is(r), 1);
				if (!taken.isEmpty()) {
					be.setItem(SLOT_INGREDIENT, taken);
					changed = true;
				}
			}
		}
		// Fuel.
		if (fuel && be.getItem(SLOT_FUEL).isEmpty()) {
			ItemStack powder = new ItemStack(Items.BLAZE_POWDER);
			if (be.canPlaceItem(SLOT_FUEL, powder)) {
				ItemStack taken = c.backpack().take(st -> st.is(Items.BLAZE_POWDER), 1);
				if (!taken.isEmpty()) {
					be.setItem(SLOT_FUEL, taken);
					changed = true;
				}
			}
		}
		if (changed) {
			be.setChanged();
			c.swingArm();
			level.playSound(null, at, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 1.0F);
		}
		if (!brewed.isEmpty()) {
			Speech.say(c, Line.POTIONS_BREWED, brewed.getFirst());
			Camp.data(level.getServer()).addStat("potions_brewed", brewed.size());
		}
		phase = Phase.RETURN;
		return TaskStatus.RUNNING;
	}

	/** "fire resistance", "healing" and so on, for the friend's line. */
	private static String potionName(ItemStack s) {
		PotionContents contents = s.get(DataComponents.POTION_CONTENTS);
		if (contents != null && contents.potion().isPresent()) {
			return contents.potion().get().value().name().replace('_', ' ').toLowerCase(Locale.ROOT);
		}
		return Tiers.fireResistance(s) ? "fire resistance" : "potions";
	}

	private TaskStatus putBack(CompanionEntity c) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (walk == ChestWalk.State.FAILED || chest.isEmpty()) {
			return TaskStatus.SUCCESS;
		}
		Trips.putBack(chest.get(), c, carried::contains);
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		stand = null;
		water = null;
		carried.clear();
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
