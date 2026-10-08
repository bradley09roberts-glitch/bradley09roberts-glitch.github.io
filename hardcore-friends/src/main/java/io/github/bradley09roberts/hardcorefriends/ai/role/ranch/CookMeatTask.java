package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.CampFurnace;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The farmer cooks raw meat and fish (from her backpack or the supply chest) on the camp's lit campfire, as a player
 * does: up to four pieces go on the fire (vanilla {@link CampfireBlockEntity} cooks them), she tends the fire while
 * they cook, picks up each cooked piece as the fire drops it, and puts the cooked food in the supply chest, where
 * everyone eats from. Only as many cooked pieces as she put on are picked up, so a player's own cooking is left alone.
 * When the campfire is full she loads the camp furnace the friends built instead (with coal, charcoal or planks for
 * fuel); whoever empties that furnace takes the food to the chest.
 */
public final class CookMeatTask implements CompanionTask {
	public static final String ID = "fern.cook";
	private static final double BASE = 48;
	private static final int FIRE_SLOTS = 4;
	private static final int TEND_LIMIT = 900;
	private static final int SEARCH_INTERVAL = 200;
	private static final int SEARCH_RADIUS = 10;
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

	private final CampFurnace furnace = new CampFurnace();
	private @Nullable Phase phase;
	private boolean useFurnace;
	private @Nullable BlockPos fire;
	private int placed;
	private int collected;
	private int tended;

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
		if (!Camp.isCampLevel(level, data) || Stores.available(c, Livestock::isRawMeat) == 0) {
			return 0;
		}
		BlockPos at = campfire(c);
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

	/** The camp furnace, if the friends built it and it has room for meat, and fuel is at hand. */
	private @Nullable AbstractFurnaceBlockEntity furnaceFor(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos pos = furnace.pos(c);
		if (pos == null || !Camp.data(level.getServer()).isPlacedByFriends(level, pos)) {
			return null; // a player's furnace is theirs to cook in
		}
		AbstractFurnaceBlockEntity f = CampFurnace.furnaceAt(level, pos);
		if (f == null || !f.getItem(CampFurnace.SLOT_INPUT).isEmpty() || !f.getItem(CampFurnace.SLOT_RESULT).isEmpty()) {
			return null;
		}
		boolean fuelled = !f.getItem(CampFurnace.SLOT_FUEL).isEmpty() || Stores.available(c, FUEL) > 0;
		return fuelled ? f : null;
	}

	// ----------------------------------------------------------------- run

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		fire = campfire(c);
		useFurnace = fire == null || freeSlots(level, fire) == 0;
		if (useFurnace && furnaceFor(c) == null) {
			return false;
		}
		placed = 0;
		collected = 0;
		tended = 0;
		boolean needFuel = useFurnace && furnaceFor(c) != null
			&& furnaceFor(c).getItem(CampFurnace.SLOT_FUEL).isEmpty() && !c.backpack().has(FUEL);
		phase = c.backpack().has(Livestock::isRawMeat) && !needFuel ? (useFurnace ? Phase.TO_FURNACE : Phase.TO_FIRE) : Phase.FETCH;
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
		int want = useFurnace ? 8 : FIRE_SLOTS;
		SupplyChest.withdraw(chest.get(), c.backpack(), Livestock::isRawMeat, Math.max(0, want - c.backpack().count(Livestock::isRawMeat)));
		if (useFurnace && !c.backpack().has(FUEL)) {
			if (SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(ItemTags.COALS), 1) == 0) {
				SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(ItemTags.PLANKS), 6);
			}
		}
		if (!c.backpack().has(Livestock::isRawMeat)) {
			return TaskStatus.FAILURE;
		}
		phase = useFurnace ? Phase.TO_FURNACE : Phase.TO_FIRE;
		return TaskStatus.RUNNING;
	}

	/** Puts raw meat on the fire, one piece per free slot. */
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
		while (placed < FIRE_SLOTS) {
			ItemStack one = c.backpack().take(Livestock::isRawMeat, 1);
			if (one.isEmpty()) {
				break;
			}
			if (!campfire.placeFood(level, c, one)) {
				Stores.giveBack(c, one);
				break;
			}
			placed++;
		}
		if (placed == 0) {
			if (furnaceFor(c) != null) {
				useFurnace = true; // the fire filled up meanwhile
				phase = Phase.TO_FURNACE;
				return TaskStatus.RUNNING;
			}
			return TaskStatus.FAILURE;
		}
		c.swingArm();
		phase = Phase.TEND;
		return TaskStatus.RUNNING;
	}

	/** Stays by the fire while the meat cooks, picking up each cooked piece the fire drops. */
	private TaskStatus tend(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos at = fire;
		if (at == null) {
			return TaskStatus.FAILURE;
		}
		if (++tended > TEND_LIMIT) {
			phase = collected > 0 ? Phase.STORE : null; // the fire went out or someone took it: what is left cooks on
			return collected > 0 ? TaskStatus.RUNNING : TaskStatus.FAILURE;
		}
		if (c.position().distanceToSqr(Vec3.atBottomCenterOf(at)) > 3.0 * 3.0) {
			c.actions().walkTo(at, 2.0);
		} else {
			c.actions().stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(at));
		}
		if (tended % 10 == 0) {
			pickUpCooked(c, level, at);
		}
		if (collected >= placed) {
			phase = Phase.STORE;
		}
		return TaskStatus.RUNNING;
	}

	private void pickUpCooked(CompanionEntity c, ServerLevel level, BlockPos at) {
		AABB around = new AABB(at).inflate(3.0, 2.0, 3.0);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around,
			e -> e.isAlive() && Livestock.isCookedMeat(e.getItem()) && !(e.getOwner() instanceof Player))) {
			if (collected >= placed) {
				return;
			}
			ItemStack stack = item.getItem();
			int want = Math.min(stack.getCount(), placed - collected);
			ItemStack left = c.backpack().insert(stack.copyWithCount(want));
			int picked = want - left.getCount();
			if (picked <= 0) {
				continue;
			}
			c.take(item, picked);
			collected += picked;
			if (picked >= stack.getCount()) {
				item.discard();
			} else {
				item.setItem(stack.copyWithCount(stack.getCount() - picked));
			}
		}
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

	/** Loads raw meat and fuel into the camp furnace; it cooks there and is collected with the furnace's output. */
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
		ItemStack sample = c.backpack().find(Livestock::isRawMeat);
		if (sample.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		ItemStack meat = c.backpack().take(s -> ItemStack.isSameItemSameComponents(s, sample), 8);
		f.setItem(CampFurnace.SLOT_INPUT, meat);
		if (f.getItem(CampFurnace.SLOT_FUEL).isEmpty()) {
			ItemStack coal = c.backpack().take(s -> s.is(ItemTags.COALS), 1); // one coal cooks eight
			ItemStack fuel = coal.isEmpty() ? c.backpack().take(s -> s.is(ItemTags.PLANKS), (meat.getCount() * 2 + 2) / 3) : coal;
			f.setItem(CampFurnace.SLOT_FUEL, fuel);
		}
		f.setChanged();
		c.swingArm();
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 0.8F);
		Camp.data(level.getServer()).addStat("meals_cooked", meat.getCount());
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		phase = null;
		fire = null;
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
