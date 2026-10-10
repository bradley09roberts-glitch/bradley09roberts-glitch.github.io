package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Work at a trade's station ({@link Products}): the baker at the bakery's oven, the innkeeper at the tavern's kitchen
 * (or, as the camp's cook, at the campfire), the butcher at the shop's smoker, the smith making goods for the smith's
 * shop. One batch a run: the ingredients (and fuel, for cooking) are fetched from the supply chest, the work is done at
 * the station (a little while per piece, quicker for a skilled or cheerful friend), and the goods go onto a shop's
 * shelves (its own chests) up to the product's target, the rest to the supply chest. Any other workplace's goods (the
 * tavern's meals) all go to the supply chest: only a shop's shelves are ever emptied again, by sales or, for food, by the
 * keeper taking it back when the camp is hungry, so food left in the tavern's chests would feed nobody.
 * Exactly the ingredients fetched are used up; a run broken off leaves them in the backpack for the camp's tidying.
 * The station block is only stood beside and never changed.
 */
final class CraftTask extends TradeJob {
	static final String ID = "market.craft";
	private static final double SCORE = 45;
	private static final int MAX_CRAFTS = 8;
	private static final int REPLAN = 100;
	/** Most of one product a workplace's own chests (a shop's shelves) keep. */
	private static final int SHELF = 24;

	private enum Phase {
		FETCH,
		WORK,
		DELIVER
	}

	/** A batch: the product, how many crafts, and where to do it. */
	private record Batch(Products.Product product, int crafts, BlockPos station) {
	}

	private @Nullable Batch batch;
	private @Nullable Workplace place;
	private Phase phase = Phase.FETCH;
	private int ticks;
	private int workTicks;
	private int fuelTenths;
	private int made;
	private final List<Integer> fetched = new ArrayList<>();
	private int fuelFetched;
	private @Nullable Predicate<ItemStack> fuelKind;
	private long plannedAt = Long.MIN_VALUE / 2;
	private @Nullable Batch planned;
	private String doing = "working at the station";
	private boolean triedShelves;
	private boolean returnedEmpties;

	CraftTask() {
		super(ID, Set.of("fern.bake", "fern.cook"), Trade.BAKER, Trade.INNKEEPER, Trade.BUTCHER, Trade.BLACKSMITH);
	}

	@Override
	public String describe() {
		return doing;
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (!workingHours(level) || c.backpack().freeSlots() < 3 || Stores.supplyPos(level).isEmpty()) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt >= REPLAN || now < plannedAt) {
			plannedAt = now;
			planned = plan(c, level, h, w);
		}
		Batch b = planned;
		if (b == null) {
			return 0;
		}
		boolean food = b.product().output().components().has(net.minecraft.core.component.DataComponents.FOOD);
		return food ? SCORE * Math.min(1.4, CampNeeds.weight(CampNeeds.Need.FOOD)) : SCORE * 0.9;
	}

	/** The most wanted product whose ingredients the camp has, and how many crafts of it. */
	private static @Nullable Batch plan(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		List<Products.Product> list = Products.of(h.trade, w);
		if (list.isEmpty()) {
			return null;
		}
		BlockPos work = station(level, h, w, false);
		BlockPos heat = station(level, h, w, true);
		List<BlockPos> shelves = shelves(level, w);
		Batch best = null;
		int bestShort = 0;
		for (Products.Product p : list) {
			BlockPos at = p.heat() ? heat : work;
			if (at == null || !p.allowed().test(level)) {
				continue;
			}
			// What is on the shelves counts only while it may stay there: food short, the camp's food is what is made for.
			int onShelves = shelfTarget(p) > 0 ? Stores.count(level, shelves, s -> s.is(p.output())) : 0;
			int have = Stores.supplyCount(level, s -> s.is(p.output())) + onShelves;
			int shortBy = p.target() - have;
			if (shortBy <= 0) {
				continue;
			}
			int crafts = Math.min(MAX_CRAFTS, (shortBy + p.yield() - 1) / p.yield());
			for (Products.Input in : p.inputs()) {
				int spare = Stores.supplyCount(level, in.match()) - in.keep();
				crafts = Math.min(crafts, Math.max(0, spare) / in.count());
			}
			if (p.heat()) {
				crafts = Math.min(crafts, fuelAvailable(level) / 10);
			}
			if (crafts <= 0) {
				continue;
			}
			if (shortBy > bestShort) {
				bestShort = shortBy;
				best = new Batch(p, crafts, at);
			}
		}
		return best;
	}

	/** Items the supply chest's spare fuel can cook (coal and charcoal first; planks only above 32). */
	private static int fuelAvailable(ServerLevel level) {
		int coal = Stores.supplyCount(level, s -> Products.fuelValue(s) == Products.COAL_SMELTS * 10);
		int planks = Math.max(0, Stores.supplyCount(level, s -> s.is(net.minecraft.tags.ItemTags.PLANKS)) - 32);
		return coal * Products.COAL_SMELTS * 10 + planks * 15;
	}

	/**
	 * Where to work: the workplace's job block (for cooking, a smoker, furnace or campfire by it), or for the camp's
	 * cook the camp's campfire or furnace. Null if there is nowhere fit.
	 */
	private static @Nullable BlockPos station(ServerLevel level, MarketData.Holding h, @Nullable Workplace w, boolean heat) {
		if (w == null) {
			BlockPos camp = Market.campStation(level, h.trade);
			return camp != null && (!heat || level.isLoaded(camp) && isHeat(level.getBlockState(camp))) ? camp : null;
		}
		if (!heat) {
			return w.job();
		}
		if (level.isLoaded(w.job()) && isHeat(level.getBlockState(w.job()))) {
			return w.job();
		}
		for (BlockPos p : BlockPos.betweenClosed(w.job().offset(-4, -1, -4), w.job().offset(4, 2, 4))) {
			if (level.isLoaded(p) && isHeat(level.getBlockState(p))) {
				return p.immutable();
			}
		}
		return null;
	}

	private static boolean isHeat(BlockState s) {
		return s.is(Blocks.SMOKER) || s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(BlockTags.CAMPFIRES);
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		MarketData.Holding h = holding(c);
		if (h == null) {
			return false;
		}
		place = workplace(c, h);
		batch = plan(c, level, h, place);
		plannedAt = Long.MIN_VALUE / 2;
		Batch b = batch;
		if (b == null) {
			return false;
		}
		phase = Phase.FETCH;
		ticks = 0;
		made = 0;
		fetched.clear();
		fuelFetched = 0;
		fuelKind = null;
		fuelTenths = 0;
		triedShelves = false;
		returnedEmpties = false;
		doing = b.product().doing();
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Batch b = batch;
		if (b == null) {
			return TaskStatus.FAILURE;
		}
		return switch (phase) {
			case FETCH -> fetch(c, level, b);
			case WORK -> work(c, level, b);
			case DELIVER -> deliver(c, level, b);
		};
	}

	private TaskStatus fetch(CompanionEntity c, ServerLevel level, Batch b) {
		Optional<BlockPos> supply = Stores.supplyPos(level);
		if (supply.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		switch (Stores.walk(c, supply.get(), 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		Backpack bp = c.backpack();
		int crafts = b.crafts();
		// Take what each ingredient allows now, never below what the camp keeps back.
		for (Products.Input in : b.product().inputs()) {
			int spare = Stores.supplyCount(level, in.match()) - in.keep();
			crafts = Math.min(crafts, Math.max(0, spare) / in.count());
		}
		if (crafts <= 0) {
			return TaskStatus.FAILURE;
		}
		for (Products.Input in : b.product().inputs()) {
			int before = bp.count(in.match());
			Stores.withdraw(c, supply.get(), in.match(), crafts * in.count());
			fetched.add(bp.count(in.match()) - before);
		}
		for (int i = 0; i < b.product().inputs().size(); i++) {
			crafts = Math.min(crafts, fetched.get(i) / b.product().inputs().get(i).count());
		}
		if (b.product().heat() && crafts > 0) {
			crafts = fetchFuel(c, level, supply.get(), crafts);
		}
		if (crafts <= 0) {
			return TaskStatus.FAILURE; // what was fetched goes back with the tidying
		}
		batch = new Batch(b.product(), crafts, b.station());
		phase = Phase.WORK;
		ticks = 0;
		workTicks = (int) Math.min(300, (40 + 20 * crafts) / Math.max(0.5, c.workSpeed(WorldEditGuard.Reason.FARM)));
		return TaskStatus.RUNNING;
	}

	/** Fetches fuel for {@code crafts} pieces (coal first, else spare planks); returns how many it can cook. */
	private int fetchFuel(CompanionEntity c, ServerLevel level, BlockPos supply, int crafts) {
		Predicate<ItemStack> coal = s -> Products.fuelValue(s) == Products.COAL_SMELTS * 10;
		Predicate<ItemStack> planks = s -> s.is(net.minecraft.tags.ItemTags.PLANKS);
		Backpack bp = c.backpack();
		int wantCoal = (crafts + Products.COAL_SMELTS - 1) / Products.COAL_SMELTS;
		int before = bp.count(coal);
		Stores.withdraw(c, supply, coal, wantCoal);
		int got = bp.count(coal) - before;
		if (got > 0) {
			fuelKind = coal;
			fuelFetched = got;
			fuelTenths = got * Products.COAL_SMELTS * 10;
			return Math.min(crafts, fuelTenths / 10);
		}
		int spare = Math.max(0, Stores.supplyCount(level, planks) - 32);
		int wantPlanks = Math.min(spare, (crafts * 10 + 14) / 15);
		before = bp.count(planks);
		Stores.withdraw(c, supply, planks, wantPlanks);
		got = bp.count(planks) - before;
		fuelKind = planks;
		fuelFetched = got;
		fuelTenths = got * 15;
		return Math.min(crafts, fuelTenths / 10);
	}

	private TaskStatus work(CompanionEntity c, ServerLevel level, Batch b) {
		switch (Stores.walk(c, b.station(), 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		MarketData.Holding h = holding(c);
		if (ticks == 0) {
			say(c, b.product());
		}
		if (++ticks % 15 == 0) {
			c.swingArm();
			BlockPos s = b.station();
			if (b.product().heat()) {
				level.sendParticles(ParticleTypes.SMOKE, s.getX() + 0.5, s.getY() + 1.1, s.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
			}
		}
		if (ticks % 40 == 1 && h != null) {
			level.playSound(null, b.station(), Products.workSound(h.trade), SoundSource.BLOCKS, 0.7F, 1.0F);
		}
		if (ticks < workTicks) {
			return TaskStatus.RUNNING;
		}
		made = convert(c, level, b);
		if (made <= 0) {
			return TaskStatus.FAILURE;
		}
		phase = Phase.DELIVER;
		return TaskStatus.RUNNING;
	}

	/** Uses up the fetched ingredients (and fuel) and puts the goods in the backpack. Returns how many were made. */
	private int convert(CompanionEntity c, ServerLevel level, Batch b) {
		Backpack bp = c.backpack();
		Products.Product p = b.product();
		int crafts = b.crafts();
		for (int i = 0; i < p.inputs().size(); i++) {
			Products.Input in = p.inputs().get(i);
			crafts = Math.min(crafts, Math.min(fetched.get(i), bp.count(in.match())) / in.count());
		}
		Predicate<ItemStack> fuel = fuelKind;
		if (p.heat()) {
			if (fuel == null) {
				return 0;
			}
			int perPiece = Math.max(1, fuelTenths / Math.max(1, fuelFetched));
			crafts = Math.min(crafts, Math.min(fuelFetched, bp.count(fuel)) * perPiece / 10);
		}
		int out = crafts * p.yield();
		int returned = crafts * p.returnsCount();
		int slotsNeeded = (out + p.output().getDefaultMaxStackSize() - 1) / p.output().getDefaultMaxStackSize();
		if (crafts <= 0 || bp.freeSlots() < slotsNeeded) {
			return 0;
		}
		for (Products.Input in : p.inputs()) {
			bp.remove(in.match(), crafts * in.count());
		}
		if (p.heat() && fuel != null) {
			int perPiece = Math.max(1, fuelTenths / Math.max(1, fuelFetched));
			bp.remove(fuel, (crafts * 10 + perPiece - 1) / perPiece);
		}
		Stores.give(c, new ItemStack(p.output(), out));
		if (p.returns() != null && returned > 0) {
			Stores.give(c, new ItemStack(p.returns(), returned));
		}
		Camp.data(level.getServer()).addStat("market.made", out);
		return out;
	}

	/**
	 * A shop's shelves (its own chests); none for any other workplace, whose goods all go to the supply chest (see the
	 * class comment).
	 */
	private static List<BlockPos> shelves(ServerLevel level, @Nullable Workplace w) {
		return w != null && w.isShop() ? Stores.chests(level, w) : List.of();
	}

	/**
	 * How many of a product a shop's shelves keep: food only while the camp has food to spare (otherwise all of it goes
	 * to the supply chest, where everyone eats from), and never more than a shelf's worth.
	 */
	private static int shelfTarget(Products.Product p) {
		boolean food = p.output().components().has(net.minecraft.core.component.DataComponents.FOOD);
		if (food && CampNeeds.need(CampNeeds.Need.FOOD) > Catalogue.FOOD_TO_SPARE + 0.15) {
			return 0;
		}
		return Math.min(p.target(), SHELF);
	}

	private TaskStatus deliver(CompanionEntity c, ServerLevel level, Batch b) {
		Products.Product p = b.product();
		List<BlockPos> shelves = triedShelves ? List.of() : shelves(level, place);
		int onShelves = Stores.count(level, shelves, s -> s.is(p.output()));
		int shelfRoom = Math.max(0, shelfTarget(p) - onShelves);
		BlockPos to = !shelves.isEmpty() && shelfRoom > 0 ? shelves.getFirst() : Stores.supplyPos(level).orElse(null);
		if (to == null) {
			return TaskStatus.SUCCESS; // nowhere to put it: it stays in the backpack for the tidying
		}
		switch (Stores.walk(c, to, 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.SUCCESS; // made, at least; the tidying takes it to the chest
			}
			case ARRIVED -> {
			}
		}
		boolean toShelves = shelves.contains(to);
		int put = Stores.deposit(c, to, s -> s.is(p.output()), toShelves ? Math.min(made, shelfRoom) : made);
		made -= put;
		Item back = p.returns();
		if (back != null && !toShelves && !returnedEmpties) {
			returnedEmpties = true; // the cake's buckets go back to the supply chest, for milking
			Stores.deposit(c, to, s -> s.is(back), b.crafts() * p.returnsCount());
		}
		if (made > 0 && toShelves) {
			triedShelves = true;
			return TaskStatus.RUNNING; // the rest to the supply chest
		}
		return TaskStatus.SUCCESS;
	}

	private static void say(CompanionEntity c, Products.Product p) {
		Line line = p.line();
		if (line == null) {
			return;
		}
		if (line == Line.COOKING) {
			Speech.say(c, line, p.doing().replace("cooking ", "").replace("cooked ", ""));
		} else if (line.args() == 0) {
			Speech.say(c, line);
		} else {
			Speech.say(c, line, p.doing());
		}
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		batch = null;
		place = null;
		fetched.clear();
		doing = "working at the station";
	}

	@Override
	public int failureCooldown() {
		return 20 * 45;
	}

	@Override
	public int successCooldown() {
		return 20 * 10;
	}

	@Override
	public int maxTicks() {
		return 20 * 150;
	}

	/** "Bread" from an item, for messages. */
	static String itemName(ItemStack s) {
		return s.getHoverName().getString().toLowerCase(Locale.ROOT);
	}
}
