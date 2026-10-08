package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.CampFurnace;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.SmeltTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The farmer cooks raw meat and fish (from her backpack or the supply chest) on the camp's lit campfire, as a player
 * does: up to four pieces go on the fire's free slots (vanilla {@link CampfireBlockEntity} cooks them), she tends the
 * fire while they cook, and each time one of the slots she filled is done she picks up that piece (freshly dropped,
 * the cooked form of what she put on) and finally puts the cooked food in the supply chest, where everyone eats from.
 * Food a player put on the same fire, and cooked food already lying about, is left alone.
 *
 * <p>When the campfire is full she uses the camp furnace the friends built instead (a plain furnace: a blast furnace
 * cannot cook food), with coal, charcoal or planks fetched for exactly what goes in: she never loads more meat than
 * the fuel can cook, so the furnace is never left holding raw meat it cannot finish (that would stop the miner's
 * smelting). A furnace found in that state (it went out with raw meat in it) is given the fuel it lacks. Whoever
 * empties the furnace takes the food to the chest.
 */
public final class CookMeatTask implements CompanionTask {
	public static final String ID = "fern.cook";
	private static final double BASE = 48;
	private static final int FIRE_SLOTS = 4;
	private static final int TEND_LIMIT = 900;
	private static final int SEARCH_INTERVAL = 200;
	private static final int SEARCH_RADIUS = 10;
	/** A cooked piece dropped this recently (ticks) is the one her emptied slot just gave. */
	private static final int FRESH_TICKS = 40;
	/** At most this much meat goes in the furnace at once (one coal's worth). */
	private static final int FURNACE_LOAD = 8;
	private static final Predicate<ItemStack> FUEL = s -> s.is(ItemTags.COALS) || s.is(ItemTags.PLANKS);

	private enum Phase {
		FETCH,
		TO_FIRE,
		TEND,
		STORE,
		TO_FURNACE
	}

	/** The team's knowledge of the camp's campfire. */
	private static final class Known {
		private @Nullable BlockPos pos;
		private long nextSearch;
	}

	/** A cooked piece one of her slots has given, to be picked up while it is fresh. */
	private record Owed(Item cooked, long since) {
	}

	/**
	 * Loading the furnace from the backpack: the meat to add (to an empty input only), the fuel to add, and how many
	 * pieces in the furnace its fuel covers before and after.
	 */
	private record Load(ItemStack meat, int add, ItemStack fuel, int fuelItems, int coveredBefore, int covered) {
		boolean useful() {
			return covered > coveredBefore;
		}
	}

	private final CampFurnace furnace = new CampFurnace();
	private @Nullable Phase phase;
	private boolean useFurnace;
	private boolean fetched;
	private @Nullable BlockPos fire;
	private int placed;
	private int collected;
	private int tended;
	/** The stack she put in each campfire slot (null where the slot is not hers, or is done). */
	private final ItemStack[] mine = new ItemStack[FIRE_SLOTS];
	private final Item[] rawIn = new Item[FIRE_SLOTS];
	private final List<Owed> owed = new ArrayList<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "cooking meat";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		boolean raw = Stores.available(c, Livestock::isRawMeat) > 0;
		BlockPos at = raw ? campfire(c) : null;
		boolean fireFree = at != null && freeSlots(level, at) > 0;
		if (!fireFree && furnaceFor(c) == null) {
			return 0;
		}
		return Math.min(69, BASE * CampNeeds.weight(CampNeeds.Need.FOOD));
	}

	// ------------------------------------------------------------- finding

	/** The camp's lit campfire: the one the builder made, else any lit campfire near the camp centre. */
	static @Nullable BlockPos campfire(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Known known = TeamCache.get(level, "ranch.campfire", Known::new);
		if (known.pos != null && litCampfire(level, known.pos)) {
			return known.pos;
		}
		known.pos = null;
		long now = level.getGameTime();
		if (now < known.nextSearch) {
			return null;
		}
		known.nextSearch = now + SEARCH_INTERVAL;
		CampData data = Camp.data(level.getServer());
		Optional<CampData.Site> site = data.site(Structures.CAMPFIRE);
		if (site.isPresent() && litCampfire(level, site.get().origin)) {
			known.pos = site.get().origin;
			return known.pos;
		}
		BlockPos centre = c.homePos();
		double best = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
			centre.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
			if (litCampfire(level, p) && p.distSqr(centre) < best) {
				best = p.distSqr(centre);
				known.pos = p.immutable();
			}
		}
		return known.pos;
	}

	private static boolean litCampfire(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState s = level.getBlockState(pos);
		return s.getBlock() instanceof CampfireBlock && s.getValue(CampfireBlock.LIT)
			&& level.getBlockEntity(pos) instanceof CampfireBlockEntity;
	}

	private static int freeSlots(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity fire)) {
			return 0;
		}
		int free = 0;
		for (ItemStack s : fire.getItems()) {
			if (s.isEmpty()) {
				free++;
			}
		}
		return free;
	}

	/**
	 * The camp furnace, if the friends built it, it is a plain furnace with its output emptied, and there is cooking to
	 * do in it: its input is empty and raw meat and fuel are at hand, or it went out with raw meat its fuel cannot
	 * cook and more of its fuel is at hand.
	 */
	private @Nullable AbstractFurnaceBlockEntity furnaceFor(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos pos = furnace.pos(c);
		if (pos == null || !Camp.data(level.getServer()).isPlacedByFriends(level, pos)) {
			return null; // a player's furnace is theirs to cook in
		}
		if (!level.getBlockState(pos).is(Blocks.FURNACE)) {
			return null; // a blast furnace only smelts ore
		}
		AbstractFurnaceBlockEntity f = CampFurnace.furnaceAt(level, pos);
		if (f == null || !f.getItem(CampFurnace.SLOT_RESULT).isEmpty()) {
			return null;
		}
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		ItemStack fuel = f.getItem(CampFurnace.SLOT_FUEL);
		if (!fuel.isEmpty() && SmeltTask.halfSmeltsPer(fuel) == 0) {
			return null; // fuel the friends do not use: somebody else's cooking
		}
		if (input.isEmpty()) {
			boolean fuelled = !fuel.isEmpty() || Stores.available(c, FUEL) > 0;
			return fuelled && Stores.available(c, Livestock::isRawMeat) > 0 ? f : null;
		}
		return Livestock.isRawMeat(input) && wentOut(level, pos, f) && Stores.available(c, sameFuel(fuel)) > 0 ? f : null;
	}

	/** Not burning, with more raw meat in it than the fuel in its slot can cook. */
	private static boolean wentOut(ServerLevel level, BlockPos pos, AbstractFurnaceBlockEntity f) {
		BlockState s = level.getBlockState(pos);
		if (s.hasProperty(AbstractFurnaceBlock.LIT) && s.getValue(AbstractFurnaceBlock.LIT)) {
			return false;
		}
		ItemStack fuel = f.getItem(CampFurnace.SLOT_FUEL);
		return f.getItem(CampFurnace.SLOT_INPUT).getCount() * 2 > SmeltTask.halfSmeltsPer(fuel) * fuel.getCount();
	}

	/** Fuel that may join what is in the fuel slot: the same item, or coal or planks for an empty slot. */
	private static Predicate<ItemStack> sameFuel(ItemStack fuelSlot) {
		return fuelSlot.isEmpty() ? FUEL : s -> ItemStack.isSameItemSameComponents(s, fuelSlot);
	}

	/** The fuel she would add from the backpack: more of what is in the slot, else coal, else planks (live reference). */
	private static ItemStack fuelToAdd(Backpack bp, ItemStack fuelSlot) {
		if (!fuelSlot.isEmpty()) {
			return bp.find(s -> ItemStack.isSameItemSameComponents(s, fuelSlot));
		}
		ItemStack coal = bp.find(s -> s.is(ItemTags.COALS));
		return coal.isEmpty() ? bp.find(s -> s.is(ItemTags.PLANKS)) : coal;
	}

	/** Half-cooks the fuel in the slot gives, plus what she could add to it from the backpack. */
	private static int fuelHalves(Backpack bp, ItemStack fuelSlot) {
		ItemStack fuel = fuelToAdd(bp, fuelSlot);
		int inSlot = SmeltTask.halfSmeltsPer(fuelSlot) * fuelSlot.getCount();
		if (fuel.isEmpty()) {
			return inSlot;
		}
		int space = fuelSlot.isEmpty() ? fuel.getMaxStackSize() : fuelSlot.getMaxStackSize() - fuelSlot.getCount();
		int carried = Math.min(space, bp.count(s -> ItemStack.isSameItemSameComponents(s, fuel)));
		return inSlot + SmeltTask.halfSmeltsPer(fuel) * carried;
	}

	/** Works out what loading this furnace from the backpack would do; nothing is moved. */
	private static Load plan(Backpack bp, AbstractFurnaceBlockEntity f) {
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		ItemStack fuelSlot = f.getItem(CampFurnace.SLOT_FUEL);
		int inSlot = SmeltTask.halfSmeltsPer(fuelSlot) * fuelSlot.getCount();
		ItemStack fuel = fuelToAdd(bp, fuelSlot);
		int per = SmeltTask.halfSmeltsPer(fuel);
		int queued = input.getCount();
		ItemStack meat = input.isEmpty() ? bp.find(Livestock::isRawMeat) : ItemStack.EMPTY;
		int add = 0;
		if (!meat.isEmpty()) {
			ItemStack sample = meat;
			int canCook = fuelHalves(bp, fuelSlot) / 2;
			add = Math.min(Math.min(FURNACE_LOAD, bp.count(s -> ItemStack.isSameItemSameComponents(s, sample))), canCook);
		}
		int need = (queued + add) * 2 - inSlot;
		int fuelItems = 0;
		if (need > 0 && per > 0) {
			int space = fuelSlot.isEmpty() ? fuel.getMaxStackSize() : fuelSlot.getMaxStackSize() - fuelSlot.getCount();
			int carried = Math.min(space, bp.count(s -> ItemStack.isSameItemSameComponents(s, fuel)));
			fuelItems = Math.min(carried, (need + per - 1) / per);
		}
		int coveredBefore = Math.min(queued, inSlot / 2);
		int covered = Math.min(queued + add, (inSlot + per * fuelItems) / 2);
		return new Load(meat.isEmpty() ? ItemStack.EMPTY : meat.copyWithCount(1), add,
			fuel.isEmpty() ? ItemStack.EMPTY : fuel.copyWithCount(1), fuelItems, coveredBefore, covered);
	}

	/** True if what she carries already loads the furnace as fully as it can be: no trip to the chest needed. */
	private static boolean carriesEnough(Backpack bp, AbstractFurnaceBlockEntity f) {
		Load load = plan(bp, f);
		if (!load.useful()) {
			return false;
		}
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		if (!input.isEmpty()) {
			return load.covered() >= input.getCount(); // all of what is stuck in there
		}
		int carried = bp.count(s -> ItemStack.isSameItemSameComponents(s, load.meat()));
		return load.add() >= Math.min(FURNACE_LOAD, carried);
	}

	// ----------------------------------------------------------------- run

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		boolean raw = Stores.available(c, Livestock::isRawMeat) > 0;
		fire = raw ? campfire(c) : null;
		useFurnace = fire == null || freeSlots(level, fire) == 0;
		AbstractFurnaceBlockEntity f = useFurnace ? furnaceFor(c) : null;
		if (useFurnace && f == null) {
			return false;
		}
		placed = 0;
		collected = 0;
		tended = 0;
		fetched = false;
		Arrays.fill(mine, null);
		Arrays.fill(rawIn, null);
		owed.clear();
		if (f != null) {
			phase = carriesEnough(c.backpack(), f) ? Phase.TO_FURNACE : Phase.FETCH;
		} else {
			phase = c.backpack().has(Livestock::isRawMeat) ? Phase.TO_FIRE : Phase.FETCH;
		}
		Speech.say(c, Line.COOKING, meatName(c));
		return true;
	}

	/** What is being cooked, in plain words ("beef"), from what is carried or in the chest. */
	private static String meatName(CompanionEntity c) {
		ItemStack s = c.backpack().find(Livestock::isRawMeat);
		if (s.isEmpty()) {
			s = SupplyChest.of((ServerLevel) c.level()).map(chest -> {
				for (int i = 0; i < chest.getContainerSize(); i++) {
					if (Livestock.isRawMeat(chest.getItem(i))) {
						return chest.getItem(i);
					}
				}
				return ItemStack.EMPTY;
			}).orElse(ItemStack.EMPTY);
		}
		String name = s.isEmpty() ? "meat" : s.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
		return name.startsWith("raw ") ? name.substring(4) : name;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (phase == null) {
			return TaskStatus.FAILURE;
		}
		return switch (phase) {
			case FETCH -> fetch(c);
			case TO_FIRE -> toFire(c);
			case TEND -> tend(c);
			case STORE -> store(c);
			case TO_FURNACE -> toFurnace(c);
		};
	}

	private TaskStatus fetch(CompanionEntity c) {
		boolean[] failed = {false};
		Optional<Container> chest = Stores.atChest(c, failed);
		if (failed[0]) {
			return TaskStatus.FAILURE;
		}
		if (chest.isEmpty()) {
			return TaskStatus.RUNNING;
		}
		fetched = true;
		Backpack bp = c.backpack();
		if (!useFurnace) {
			SupplyChest.withdraw(chest.get(), bp, Livestock::isRawMeat, Math.max(0, FIRE_SLOTS - bp.count(Livestock::isRawMeat)));
			if (!bp.has(Livestock::isRawMeat)) {
				return TaskStatus.FAILURE;
			}
			phase = Phase.TO_FIRE;
			return TaskStatus.RUNNING;
		}
		AbstractFurnaceBlockEntity f = furnaceFor(c);
		if (f == null) {
			return TaskStatus.FAILURE;
		}
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		if (input.isEmpty()) {
			SupplyChest.withdraw(chest.get(), bp, Livestock::isRawMeat, Math.max(0, FURNACE_LOAD - bp.count(Livestock::isRawMeat)));
		}
		withdrawFuel(chest.get(), bp, f);
		phase = Phase.TO_FURNACE;
		return TaskStatus.RUNNING;
	}

	/** Takes as much coal, charcoal or planks from the chest as the meat going in (or stuck in) the furnace needs. */
	private static void withdrawFuel(Container chest, Backpack bp, AbstractFurnaceBlockEntity f) {
		ItemStack input = f.getItem(CampFurnace.SLOT_INPUT);
		ItemStack fuelSlot = f.getItem(CampFurnace.SLOT_FUEL);
		int meat;
		if (input.isEmpty()) {
			ItemStack sample = bp.find(Livestock::isRawMeat);
			meat = sample.isEmpty() ? 0 : Math.min(FURNACE_LOAD, bp.count(s -> ItemStack.isSameItemSameComponents(s, sample)));
		} else {
			meat = input.getCount();
		}
		int need = meat * 2 - fuelHalves(bp, fuelSlot);
		if (need <= 0) {
			return;
		}
		if (fuelSlot.isEmpty() || fuelSlot.is(ItemTags.COALS)) {
			SupplyChest.withdraw(chest, bp, sameFuel(fuelSlot).and(s -> s.is(ItemTags.COALS)), (need + 15) / 16);
			need = meat * 2 - fuelHalves(bp, fuelSlot);
		}
		if (need > 0 && (fuelSlot.isEmpty() || fuelSlot.is(ItemTags.PLANKS)) && !bp.has(s -> s.is(ItemTags.COALS))) {
			ItemStack carried = bp.find(s -> s.is(ItemTags.PLANKS));
			Predicate<ItemStack> planks = !fuelSlot.isEmpty() ? sameFuel(fuelSlot)
				: carried.isEmpty() ? s -> s.is(ItemTags.PLANKS) : s -> ItemStack.isSameItemSameComponents(s, carried);
			SupplyChest.withdraw(chest, bp, planks, (need + 2) / 3);
		}
	}

	/** Puts raw meat on the fire, one piece per free slot, remembering which slots are hers. */
	private TaskStatus toFire(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos at = fire;
		if (at == null || !litCampfire(level, at)) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().canReach(at) || c.position().distanceToSqr(Vec3.atBottomCenterOf(at)) > 3.0 * 3.0) {
			c.actions().walkTo(at, 2.0);
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		c.getLookControl().setLookAt(Vec3.atCenterOf(at));
		if (!(level.getBlockEntity(at) instanceof CampfireBlockEntity campfire)) {
			return TaskStatus.FAILURE;
		}
		NonNullList<ItemStack> items = campfire.getItems();
		while (placed < FIRE_SLOTS) {
			boolean[] empty = new boolean[items.size()];
			for (int i = 0; i < items.size(); i++) {
				empty[i] = items.get(i).isEmpty();
			}
			ItemStack one = c.backpack().take(Livestock::isRawMeat, 1);
			if (one.isEmpty()) {
				break;
			}
			Item raw = one.getItem();
			if (!campfire.placeFood(level, c, one)) {
				Stores.giveBack(c, one);
				break;
			}
			for (int i = 0; i < Math.min(items.size(), FIRE_SLOTS); i++) {
				if (empty[i] && !items.get(i).isEmpty()) {
					mine[i] = items.get(i); // the very stack on the fire: it is replaced when it is done
					rawIn[i] = raw;
					break;
				}
			}
			placed++;
		}
		if (placed == 0) {
			if (furnaceFor(c) != null) {
				useFurnace = true; // the fire filled up meanwhile: the furnace, with fuel fetched for it
				phase = Phase.FETCH;
				return TaskStatus.RUNNING;
			}
			return TaskStatus.FAILURE;
		}
		c.swingArm();
		phase = Phase.TEND;
		return TaskStatus.RUNNING;
	}

	/** Stays by the fire while the meat cooks, picking up each piece her slots give as it drops. */
	private TaskStatus tend(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos at = fire;
		if (at == null) {
			return TaskStatus.FAILURE;
		}
		if (++tended > TEND_LIMIT) {
			return finishTending(); // the fire went out or someone took it: what is left cooks on
		}
		if (c.position().distanceToSqr(Vec3.atBottomCenterOf(at)) > 3.0 * 3.0) {
			c.actions().walkTo(at, 2.0);
		} else {
			c.actions().stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(at));
		}
		long now = level.getGameTime();
		if (level.getBlockEntity(at) instanceof CampfireBlockEntity campfire) {
			NonNullList<ItemStack> items = campfire.getItems();
			for (int i = 0; i < FIRE_SLOTS; i++) {
				if (mine[i] != null && (i >= items.size() || items.get(i) != mine[i])) {
					owed.add(new Owed(Livestock.cookedFrom(rawIn[i]), now)); // her piece is done: it has just dropped
					mine[i] = null;
				}
			}
		} else {
			Arrays.fill(mine, null); // the fire is gone, and what was on it with it
		}
		if (!owed.isEmpty()) {
			pickUpCooked(c, level, at);
			owed.removeIf(o -> now - o.since() > FRESH_TICKS); // someone else picked it up first
		}
		for (ItemStack s : mine) {
			if (s != null) {
				return TaskStatus.RUNNING;
			}
		}
		return owed.isEmpty() ? finishTending() : TaskStatus.RUNNING;
	}

	private TaskStatus finishTending() {
		if (collected > 0) {
			phase = Phase.STORE;
			return TaskStatus.RUNNING;
		}
		phase = null;
		return TaskStatus.FAILURE;
	}

	/** Picks up fresh cooked pieces of the kinds her slots are owed, never more than she is owed. */
	private void pickUpCooked(CompanionEntity c, ServerLevel level, BlockPos at) {
		AABB around = new AABB(at).inflate(3.0, 2.0, 3.0);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around,
			e -> e.isAlive() && e.getAge() <= FRESH_TICKS && !(e.getOwner() instanceof Player) && isOwed(e.getItem()))) {
			ItemStack stack = item.getItem();
			int due = 0;
			for (Owed o : owed) {
				if (stack.is(o.cooked())) {
					due++;
				}
			}
			int want = Math.min(stack.getCount(), due);
			if (want <= 0) {
				continue;
			}
			ItemStack left = c.backpack().insert(stack.copyWithCount(want));
			int picked = want - left.getCount();
			if (picked <= 0) {
				continue;
			}
			c.take(item, picked);
			collected += picked;
			int settle = picked;
			for (Iterator<Owed> it = owed.iterator(); it.hasNext() && settle > 0;) {
				if (stack.is(it.next().cooked())) {
					it.remove();
					settle--;
				}
			}
			if (picked >= stack.getCount()) {
				item.discard();
			} else {
				item.setItem(stack.copyWithCount(stack.getCount() - picked));
			}
		}
	}

	private boolean isOwed(ItemStack s) {
		if (s.isEmpty() || s.is(Items.AIR)) {
			return false;
		}
		for (Owed o : owed) {
			if (s.is(o.cooked())) {
				return true;
			}
		}
		return false;
	}

	/** Takes the cooked food to the supply chest. */
	private TaskStatus store(CompanionEntity c) {
		boolean[] failed = {false};
		Optional<Container> chest = Stores.atChest(c, failed);
		if (failed[0]) {
			return TaskStatus.SUCCESS; // cooked all the same; it goes in with the next deposit
		}
		if (chest.isEmpty()) {
			return TaskStatus.RUNNING;
		}
		int stored = SupplyChest.deposit(c.backpack(), chest.get(), Livestock::isCookedMeat, collected);
		ServerLevel level = (ServerLevel) c.level();
		Camp.data(level.getServer()).addStat("meals_cooked", collected);
		return stored > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	/**
	 * Loads raw meat (only as much as the fuel can cook) and fuel into the camp furnace, or refuels one that went out
	 * with raw meat in it; it cooks there and is collected with the furnace's output.
	 */
	private TaskStatus toFurnace(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		AbstractFurnaceBlockEntity f = furnaceFor(c);
		BlockPos pos = furnace.pos(c);
		if (f == null || pos == null) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().canReach(pos)) {
			c.actions().walkTo(pos, 2.0);
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		Backpack bp = c.backpack();
		Load load = plan(bp, f);
		if (!load.useful()) {
			if (!fetched) {
				phase = Phase.FETCH; // not enough fuel carried: from the chest first
				return TaskStatus.RUNNING;
			}
			return TaskStatus.FAILURE; // nothing loaded: never meat the fuel cannot cook
		}
		if (load.add() > 0) {
			ItemStack meat = bp.take(s -> ItemStack.isSameItemSameComponents(s, load.meat()), load.add());
			f.setItem(CampFurnace.SLOT_INPUT, meat);
		}
		if (load.fuelItems() > 0) {
			ItemStack fuel = bp.take(s -> ItemStack.isSameItemSameComponents(s, load.fuel()), load.fuelItems());
			ItemStack fuelSlot = f.getItem(CampFurnace.SLOT_FUEL);
			if (fuelSlot.isEmpty()) {
				f.setItem(CampFurnace.SLOT_FUEL, fuel);
			} else {
				fuelSlot.grow(fuel.getCount());
				f.setItem(CampFurnace.SLOT_FUEL, fuelSlot);
			}
		}
		f.setChanged();
		c.swingArm();
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F);
		Camp.data(level.getServer()).addStat("meals_cooked", load.covered() - load.coveredBefore());
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		phase = null;
		fire = null;
		Arrays.fill(mine, null);
		owed.clear();
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int failureCooldown() {
		return 400;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
