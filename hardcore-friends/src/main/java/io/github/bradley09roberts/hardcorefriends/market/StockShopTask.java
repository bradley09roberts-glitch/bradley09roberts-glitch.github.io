package io.github.bradley09roberts.hardcorefriends.market;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Keeping a shop stocked: one trip between the supply chest and the shop's own chests. In order:
 * <ul>
 * <li>while the camp is short of food (its food need {@value #HUNGRY} or more), food in the shop goes back to the supply
 * chest, where everyone eats from;</li>
 * <li>takings above {@value #TILL_HIGH} emeralds go to the supply chest (for the camp's trading trips), and a till
 * below {@value #TILL_LOW} is topped up from it when the chest has plenty;</li>
 * <li>a good of the shop's line running low on its shelves (under four trades' worth) is fetched from the supply chest,
 * only what the camp can spare ({@link Catalogue.Good#spare}).</li>
 * </ul>
 * Exactly what was fetched is put away; a trip interrupted halfway leaves the goods in the keeper's backpack, which the
 * camp's ordinary tidying takes to the supply chest. The camp stall trades straight from the supply chest and needs no
 * stocking.
 */
final class StockShopTask extends TradeJob {
	static final String ID = "market.stock_shop";
	private static final double SCORE = 44;
	private static final double HUNGRY = 0.6;
	private static final int TILL_HIGH = 48;
	private static final int TILL_KEEP = 32;
	private static final int TILL_LOW = 8;
	private static final int SUPPLY_EMERALDS_KEPT = 16;
	private static final int REPLAN = 200;

	/** One trip: what to carry, how many, and which way. */
	private record Trip(String what, Predicate<ItemStack> match, int count, boolean toShop) {
	}

	private @Nullable Trip trip;
	private @Nullable Shop shop;
	private boolean loaded;
	private int carried;
	private long plannedAt = Long.MIN_VALUE / 2;
	private @Nullable Trip planned;

	StockShopTask() {
		super(ID, Set.of(), Trade.SHOPKEEPER, Trade.BAKER, Trade.BUTCHER, Trade.FISHMONGER, Trade.TAILOR, Trade.BLACKSMITH);
	}

	@Override
	public String describe() {
		Trip t = trip;
		return t == null ? "stocking the shop" : t.toShop() ? "stocking the shop with " + t.what() : "taking " + t.what() + " to the supply chest";
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (!workingHours(level) || c.backpack().freeSlots() < 2) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt >= REPLAN || now < plannedAt) {
			plannedAt = now;
			Optional<Shop> s = Shop.of(level, c, h);
			planned = s.isPresent() && !s.get().stall() && !s.get().stock().isEmpty() ? plan(level, s.get()) : null;
		}
		return planned != null ? SCORE : 0;
	}

	/** The most useful trip for this shop now, or null. */
	private static @Nullable Trip plan(ServerLevel level, Shop shop) {
		Optional<BlockPos> supply = Stores.supplyPos(level);
		if (supply.isEmpty()) {
			return null;
		}
		if (CampNeeds.need(CampNeeds.Need.FOOD) >= HUNGRY) {
			int food = Stores.count(level, shop.stock(), KeepList::isFood);
			if (food > 0) {
				return new Trip("food", KeepList::isFood, Math.min(64, food), false);
			}
		}
		Predicate<ItemStack> emerald = s -> s.is(Items.EMERALD);
		int till = Stores.count(level, shop.stock(), emerald);
		if (till > TILL_HIGH) {
			return new Trip("the takings", emerald, Math.min(64, till - TILL_KEEP), false);
		}
		int spareEmeralds = Stores.supplyCount(level, emerald) - SUPPLY_EMERALDS_KEPT;
		if (till < TILL_LOW && spareEmeralds > TILL_LOW) {
			return new Trip("emeralds for the till", emerald, Math.min(spareEmeralds, 16 - till), true);
		}
		for (Catalogue.Good good : Catalogue.sells(shop.type())) {
			int onShelves = Stores.count(level, shop.stock(), good.match());
			int target = good.unit() * 4;
			if (onShelves >= target) {
				continue;
			}
			int spare = good.spare(Stores.supplyCount(level, good.match()));
			int n = Math.min(Math.min(target - onShelves, spare), 64);
			if (n >= good.unit()) {
				return new Trip(good.key().replace('_', ' '), good.match(), n, true);
			}
		}
		return null;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		shop = Shop.of(level, c, holding(c)).orElse(null);
		trip = shop == null || shop.stall() ? null : plan(level, shop);
		loaded = false;
		carried = 0;
		plannedAt = Long.MIN_VALUE / 2;
		return trip != null && Stores.supplyPos(level).isPresent();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Trip t = trip;
		Shop s = shop;
		Optional<BlockPos> supply = Stores.supplyPos(level);
		if (t == null || s == null || supply.isEmpty() || s.stock().isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (!loaded) {
			BlockPos source = t.toShop() ? supply.get() : firstHolding(level, s, t.match());
			if (source == null) {
				return TaskStatus.FAILURE;
			}
			switch (Stores.walk(c, source, 2.5)) {
				case WALKING -> {
					return TaskStatus.RUNNING;
				}
				case FAILED -> {
					return TaskStatus.FAILURE;
				}
				case ARRIVED -> {
				}
			}
			int before = c.backpack().count(t.match());
			Stores.withdraw(c, source, t.match(), t.count() - carried);
			carried += c.backpack().count(t.match()) - before;
			if (!t.toShop() && carried < t.count() && firstHolding(level, s, t.match()) != null && c.backpack().freeSlots() > 1) {
				return TaskStatus.RUNNING; // more of it in the shop's next chest
			}
			if (carried <= 0) {
				return TaskStatus.FAILURE;
			}
			loaded = true;
			return TaskStatus.RUNNING;
		}
		BlockPos dest = t.toShop() ? firstWithRoom(level, s) : supply.get();
		if (dest == null) {
			return TaskStatus.FAILURE; // the shop's chests are full: the goods go to the supply chest with the tidying
		}
		switch (Stores.walk(c, dest, 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		int put = Stores.deposit(c, dest, t.match(), carried);
		carried -= put;
		if (carried > 0 && t.toShop() && put > 0 && firstWithRoom(level, s) != null) {
			return TaskStatus.RUNNING; // the rest into the next chest
		}
		return carried <= 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
	}

	private static @Nullable BlockPos firstHolding(ServerLevel level, Shop s, Predicate<ItemStack> match) {
		for (BlockPos p : s.stock()) {
			if (Stores.count(level, java.util.List.of(p), match) > 0) {
				return p;
			}
		}
		return null;
	}

	private static @Nullable BlockPos firstWithRoom(ServerLevel level, Shop s) {
		for (BlockPos p : s.stock()) {
			var box = Stores.at(level, p);
			if (box.isPresent()) {
				for (int i = 0; i < box.get().getContainerSize(); i++) {
					if (box.get().getItem(i).isEmpty()) {
						return p;
					}
				}
			}
		}
		return null;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		trip = null;
		shop = null;
		carried = 0;
		loaded = false;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
