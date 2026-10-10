package io.github.bradley09roberts.hardcorefriends.market;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.architecture.MaterialDemand;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.camp.build.Supplies;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Making the building parts the builders are short of ({@code architecture.MaterialDemand}), ahead of them, at the
 * trade's workplace:
 * <ul>
 * <li>the <b>mason</b> at the stonecutter, at its better yields (a stone block makes a stair or two slabs, where a
 * crafting table needs six blocks for four stairs): stone bricks, stairs, slabs and walls of cobblestone, stone, stone
 * bricks, bricks and sandstone, and cut sandstone;</li>
 * <li>the <b>carpenter</b> at the workshop's crafting table: wooden stairs, slabs, doors, trapdoors, fences, gates,
 * pressure plates, ladders, chests, barrels and composters, by the game's recipes;</li>
 * <li>the <b>tailor</b>: carpets and beds (each of wool of one colour), and with a tailor's shop a few of each for its
 * shelves.</li>
 * </ul>
 * One trip a run: the ingredients from the supply chest, the work at the workplace, the parts back to the supply chest
 * where the builders take them (a tailor's shop stock to its own chests). Only real ingredients are used: the camp's
 * crafting rules for wood and wool ({@code camp.build.Supplies}), and for the mason the same blocks in, cut pieces out.
 * Never scores when the builders need nothing of the trade's kind.
 */
final class PartsTask extends TradeJob {
	static final String ID = "market.parts";
	private static final double SCORE = 42;
	private static final int MAX_EACH = 16;
	private static final int MAX_KINDS = 3;
	private static final int REPLAN = 100;

	/** A stonecutter cut: {@code per} pieces of {@code output} from one block of {@code input}. */
	private record Cut(Stock output, Stock input, int per) {
	}

	private static final List<Cut> CUTS = List.of(
		new Cut(Stock.STONE_BRICKS, Stock.STONE, 1),
		new Cut(Stock.STONE_BRICK_STAIRS, Stock.STONE_BRICKS, 1), new Cut(Stock.STONE_BRICK_STAIRS, Stock.STONE, 1),
		new Cut(Stock.STONE_BRICK_SLAB, Stock.STONE_BRICKS, 2), new Cut(Stock.STONE_BRICK_SLAB, Stock.STONE, 2),
		new Cut(Stock.STONE_BRICK_WALL, Stock.STONE_BRICKS, 1), new Cut(Stock.STONE_BRICK_WALL, Stock.STONE, 1),
		new Cut(Stock.STONE_STAIRS, Stock.STONE, 1), new Cut(Stock.STONE_SLAB, Stock.STONE, 2),
		new Cut(Stock.SMOOTH_STONE_SLAB, Stock.SMOOTH_STONE, 2),
		new Cut(Stock.COBBLESTONE_STAIRS, Stock.COBBLESTONE, 1), new Cut(Stock.COBBLESTONE_SLAB, Stock.COBBLESTONE, 2),
		new Cut(Stock.COBBLESTONE_WALL, Stock.COBBLESTONE, 1),
		new Cut(Stock.BRICK_STAIRS, Stock.BRICKS, 1), new Cut(Stock.BRICK_SLAB, Stock.BRICKS, 2),
		new Cut(Stock.SANDSTONE_STAIRS, Stock.SANDSTONE, 1), new Cut(Stock.SANDSTONE_SLAB, Stock.SANDSTONE, 2),
		new Cut(Stock.SANDSTONE_WALL, Stock.SANDSTONE, 1), new Cut(Stock.CUT_SANDSTONE, Stock.SANDSTONE, 1));

	private static final Set<Stock> CARPENTRY = Set.of(Stock.WOOD_STAIRS, Stock.SLAB, Stock.DOOR, Stock.TRAPDOOR, Stock.FENCE,
		Stock.FENCE_GATE, Stock.PRESSURE_PLATE, Stock.LADDER, Stock.CHEST, Stock.BARREL, Stock.COMPOSTER);
	private static final Set<Stock> TAILORING = Set.of(Stock.CARPET, Stock.BED);

	private enum Phase {
		FETCH,
		WORK,
		DELIVER
	}

	private @Nullable Map<Stock, Integer> wanted;
	private final Map<Stock, Integer> before = new EnumMap<>(Stock.class);
	private final Map<Cut, Integer> cuts = new LinkedHashMap<>();
	private @Nullable Workplace place;
	private @Nullable Trade trade;
	private boolean forShop;
	private Phase phase = Phase.FETCH;
	private int ticks;
	private int workTicks;
	private long plannedAt = Long.MIN_VALUE / 2;
	private @Nullable Map<Stock, Integer> planned;

	PartsTask() {
		super(ID, Set.of(), Trade.MASON, Trade.CARPENTER, Trade.TAILOR);
	}

	@Override
	public String describe() {
		Trade t = trade;
		return t == Trade.MASON ? "cutting stone for the builders" : t == Trade.CARPENTER ? "making stairs, doors and furniture"
			: t == Trade.TAILOR ? "making carpets and beds" : "making building parts";
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (w == null || !workingHours(level) || c.backpack().freeSlots() < 4 || Stores.supplyPos(level).isEmpty()) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt >= REPLAN || now < plannedAt) {
			plannedAt = now;
			planned = plan(c, level, h.trade, w);
		}
		return planned == null || planned.isEmpty() ? 0 : Math.min(60, SCORE * CampNeeds.weight(CampNeeds.Need.BUILD));
	}

	/** What to make this trip (kind to count), or null. */
	private static @Nullable Map<Stock, Integer> plan(CompanionEntity c, ServerLevel level, Trade trade, Workplace w) {
		Optional<Container> chest = SupplyChest.of(level);
		if (chest.isEmpty()) {
			return null;
		}
		Map<Stock, Integer> want = new EnumMap<>(Stock.class);
		Map<Stock, Integer> missing = MaterialDemand.missing(level.getServer());
		if (trade == Trade.MASON) {
			if (!hasStonecutter(level, w)) {
				return null;
			}
			for (Map.Entry<Stock, Integer> e : missing.entrySet()) {
				if (want.size() < MAX_KINDS && cutFor(level, e.getKey(), Math.min(MAX_EACH, e.getValue())) != null) {
					want.put(e.getKey(), Math.min(MAX_EACH, e.getValue()));
				}
			}
			return want.isEmpty() ? null : want;
		}
		Set<Stock> family = trade == Trade.CARPENTER ? CARPENTRY : TAILORING;
		for (Map.Entry<Stock, Integer> e : missing.entrySet()) {
			int n = Math.min(MAX_EACH, e.getValue());
			if (want.size() < MAX_KINDS && family.contains(e.getKey()) && Supplies.canMake(c, chest.get(), e.getKey(), n)) {
				want.put(e.getKey(), n);
			}
		}
		if (want.isEmpty() && trade == Trade.TAILOR && w.isShop()) {
			// Nothing wanted for building: a few carpets and a bed for the shop's shelves.
			List<BlockPos> shelves = Stores.chests(level, w);
			if (!shelves.isEmpty()) {
				if (Stores.count(level, shelves, Stock.CARPET.item()) < 8 && Supplies.canMake(c, chest.get(), Stock.CARPET, 6)) {
					want.put(Stock.CARPET, 6);
				} else if (Stores.count(level, shelves, Stock.BED.item()) < 1 && Supplies.canMake(c, chest.get(), Stock.BED, 1)) {
					want.put(Stock.BED, 1);
				}
			}
		}
		return want.isEmpty() ? null : want;
	}

	/** A cut that makes {@code n} of a kind from what the supply chest holds, or null. */
	private static @Nullable Cut cutFor(ServerLevel level, Stock output, int n) {
		for (Cut cut : CUTS) {
			if (cut.output() == output && Stores.supplyCount(level, cut.input().item()) >= (n + cut.per() - 1) / cut.per()) {
				return cut;
			}
		}
		return null;
	}

	private static boolean hasStonecutter(ServerLevel level, Workplace w) {
		for (BlockPos p : BlockPos.betweenClosed(w.job().offset(-3, -1, -3), w.job().offset(3, 2, 3))) {
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.STONECUTTER)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		MarketData.Holding h = holding(c);
		place = workplace(c, h);
		Workplace w = place;
		if (h == null || w == null) {
			return false;
		}
		trade = h.trade;
		wanted = plan(c, level, h.trade, w);
		plannedAt = Long.MIN_VALUE / 2;
		Map<Stock, Integer> want = wanted;
		if (want == null) {
			return false;
		}
		forShop = h.trade == Trade.TAILOR && w.isShop() && MaterialDemand.missing(level.getServer()).keySet().stream().noneMatch(TAILORING::contains);
		before.clear();
		for (Stock s : want.keySet()) {
			before.put(s, c.backpack().count(s.item()));
		}
		cuts.clear();
		phase = Phase.FETCH;
		ticks = 0;
		Speech.say(c, Line.TRADE_WORK, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Map<Stock, Integer> want = wanted;
		Workplace w = place;
		Trade t = trade;
		if (want == null || w == null || t == null) {
			return TaskStatus.FAILURE;
		}
		return switch (phase) {
			case FETCH -> fetch(c, level, t, want);
			case WORK -> work(c, level, t, w, want);
			case DELIVER -> deliver(c, level, w, want);
		};
	}

	private TaskStatus fetch(CompanionEntity c, ServerLevel level, Trade t, Map<Stock, Integer> want) {
		Optional<BlockPos> supply = Stores.supplyPos(level);
		Optional<Container> chest = SupplyChest.of(level);
		if (supply.isEmpty() || chest.isEmpty()) {
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
		if (t == Trade.MASON) {
			for (Map.Entry<Stock, Integer> e : want.entrySet()) {
				Cut cut = cutFor(level, e.getKey(), e.getValue());
				if (cut == null) {
					continue;
				}
				int blocks = (e.getValue() + cut.per() - 1) / cut.per();
				int had = c.backpack().count(cut.input().item());
				Stores.withdraw(c, supply.get(), cut.input().item(), blocks);
				int got = c.backpack().count(cut.input().item()) - had;
				if (got > 0) {
					cuts.merge(cut, got, Integer::sum);
				}
			}
			if (cuts.isEmpty()) {
				return TaskStatus.FAILURE;
			}
		} else {
			// Finished parts and ingredients out of the chest; whatever needs no table is made here and now.
			new Supplies(c, chest.get(), Crafting.nearCraftingTable(c)).gather(want);
		}
		phase = Phase.WORK;
		ticks = 0;
		workTicks = (int) Math.min(240, 60 / Math.max(0.5, c.workSpeed(WorldEditGuard.Reason.BUILD)) + 10 * want.size());
		return TaskStatus.RUNNING;
	}

	private TaskStatus work(CompanionEntity c, ServerLevel level, Trade t, Workplace w, Map<Stock, Integer> want) {
		switch (Stores.walk(c, w.job(), 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		if (++ticks % 15 == 0) {
			c.swingArm();
		}
		if (ticks % 40 == 1) {
			level.playSound(null, w.job(), Products.workSound(t), SoundSource.BLOCKS, 0.7F, 1.0F);
		}
		if (ticks < workTicks) {
			return TaskStatus.RUNNING;
		}
		if (t == Trade.MASON) {
			if (!hasStonecutter(level, w)) {
				return TaskStatus.FAILURE;
			}
			cutStone(c);
		} else {
			new Supplies(c, null, Crafting.nearCraftingTable(c)).gather(want);
		}
		phase = Phase.DELIVER;
		return TaskStatus.RUNNING;
	}

	/** Each fetched block in, its cut pieces out (the stonecutter's yields). */
	private void cutStone(CompanionEntity c) {
		Backpack bp = c.backpack();
		for (Map.Entry<Cut, Integer> e : cuts.entrySet()) {
			Cut cut = e.getKey();
			Item out = cut.output().vanillaItem();
			if (out == null) {
				continue;
			}
			int blocks = Math.min(e.getValue(), bp.count(cut.input().item()));
			int removed = bp.remove(cut.input().item(), blocks);
			if (removed > 0) {
				Stores.give(c, new ItemStack(out, removed * cut.per()));
			}
		}
		cuts.clear();
	}

	private TaskStatus deliver(CompanionEntity c, ServerLevel level, Workplace w, Map<Stock, Integer> want) {
		List<BlockPos> shelves = Stores.chests(level, w);
		BlockPos to = forShop && !shelves.isEmpty() ? shelves.getFirst() : Stores.supplyPos(level).orElse(null);
		if (to == null) {
			return TaskStatus.SUCCESS;
		}
		switch (Stores.walk(c, to, 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.SUCCESS; // the parts are made; the tidying takes them to the chest
			}
			case ARRIVED -> {
			}
		}
		int made = 0;
		for (Stock s : want.keySet()) {
			int extra = c.backpack().count(s.item()) - before.getOrDefault(s, 0);
			if (extra > 0) {
				made += Stores.deposit(c, to, s.item(), extra);
			}
		}
		return made > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		wanted = null;
		place = null;
		cuts.clear();
		before.clear();
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 15;
	}

	@Override
	public int maxTicks() {
		return 20 * 150;
	}
}
