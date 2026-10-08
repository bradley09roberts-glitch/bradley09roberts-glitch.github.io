package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;

/**
 * What a trading trip takes, what it buys, and the trade itself. Friends take the camp's surplus (crops when food is
 * plentiful, wool, coal, sticks, string, feathers, flint, paper, clay, leather, rotten flesh) and a few emeralds from
 * the supply chest, sell surplus to villagers who buy it for emeralds, and spend emeralds on what the camp is short
 * of: food, glass, arrows for anyone with a bow, ender pearls and enchanted books later on, and a piece of iron gear.
 * A trade works exactly as a player's does: the villager's own offer, its real price, paid in full from the backpack,
 * and the offer is used up (the villager gains experience as usual). Nothing is ever taken without paying.
 */
public final class Trading {
	/** Most emeralds a trip takes from the chest (the rest stay yours). */
	public static final int MAX_EMERALDS_TAKEN = 12;
	/** Selling stops once this many emeralds are carried. */
	private static final int EMERALDS_ENOUGH = 32;
	/** Most kinds of goods one trip carries. */
	private static final int MAX_KINDS = 4;

	/** Something the camp may have spare: how much of it stays in the chest, and whether it is food. */
	private record Good(Predicate<ItemStack> item, int keep, boolean food) {
	}

	private static final List<Good> GOODS = List.of(
		new Good(s -> s.is(Items.ROTTEN_FLESH), 0, false),
		new Good(s -> s.is(Items.STRING), 8, false),
		new Good(s -> s.is(Items.FEATHER), 16, false),
		new Good(s -> s.is(Items.FLINT), 8, false),
		new Good(s -> s.is(Items.STICK), 64, false),
		new Good(s -> s.is(Items.PAPER), 8, false),
		new Good(s -> s.is(Items.CLAY_BALL), 8, false),
		new Good(s -> s.is(Items.LEATHER), 8, false),
		new Good(s -> s.is(ItemTags.WOOL), 16, false),
		new Good(s -> s.is(Items.COAL), 32, false),
		new Good(s -> s.is(Items.WHEAT), 64, true),
		new Good(s -> s.is(Items.CARROT), 48, true),
		new Good(s -> s.is(Items.POTATO), 48, true),
		new Good(s -> s.is(Items.BEETROOT), 32, true),
		new Good(s -> s.is(Items.PUMPKIN), 4, true),
		new Good(s -> s.is(Items.MELON), 4, true));

	/** What the camp may want to buy, and how many trades of it one trip makes at most. */
	public enum Want {
		FOOD(4),
		GLASS(2),
		ARROWS(2),
		PEARLS(2),
		BOOKS(1),
		IRON(1);

		private final int trades;

		Want(int trades) {
			this.trades = trades;
		}

		public int trades() {
			return trades;
		}

		/** True when a trade's result is this kind of thing. */
		public boolean matches(ItemStack result) {
			return switch (this) {
				case FOOD -> CompanionEntity.isEdible(result);
				case GLASS -> result.is(Items.GLASS) || result.is(Items.GLASS_PANE);
				case ARROWS -> result.is(ItemTags.ARROWS);
				case PEARLS -> result.is(Items.ENDER_PEARL);
				case BOOKS -> result.is(Items.ENCHANTED_BOOK);
				case IRON -> isIronGear(result.getItem());
			};
		}
	}

	private static final Set<Item> IRON_GEAR = Set.of(Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL,
		Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.SHIELD, Items.DIAMOND_SWORD,
		Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS,
		Items.DIAMOND_BOOTS);

	private Trading() {
	}

	private static boolean isIronGear(Item item) {
		return IRON_GEAR.contains(item);
	}

	/** True for something a trip carries to sell (or pay with): surplus goods, emeralds and books. */
	public static boolean isTradeGood(ItemStack s) {
		if (s.is(Items.EMERALD) || s.is(Items.BOOK)) {
			return true;
		}
		for (Good good : GOODS) {
			if (good.item().test(s)) {
				return true;
			}
		}
		return false;
	}

	/** What the camp would like to buy right now. */
	public static Set<Want> wants(ServerLevel level, CampData data, @Nullable Container chest) {
		Set<Want> wants = EnumSet.noneOf(Want.class);
		if (CampNeeds.need(CampNeeds.Need.FOOD) >= 0.25) {
			wants.add(Want.FOOD);
		}
		int glass = chest == null ? 0 : SupplyChest.count(chest, s -> s.is(Items.GLASS) || s.is(Items.GLASS_PANE));
		if (glass < 16) {
			wants.add(Want.GLASS);
		}
		boolean archer = Companions.all().stream().anyMatch(f -> f.actions().has(s -> s.is(Items.BOW) || s.is(Items.CROSSBOW)));
		if (archer && (chest == null || SupplyChest.count(chest, s -> s.is(ItemTags.ARROWS)) < 32)) {
			wants.add(Want.ARROWS);
		}
		if (data.stage() >= 3 && (chest == null || SupplyChest.count(chest, s -> s.is(Items.ENDER_PEARL)) < 12)) {
			wants.add(Want.PEARLS);
		}
		if (data.stage() >= 3 && chest != null && SupplyChest.count(chest, s -> s.is(Items.BOOK)) > 0
			&& SupplyChest.count(chest, s -> s.is(Items.ENCHANTED_BOOK)) < 4) {
			wants.add(Want.BOOKS);
		}
		if (chest == null || SupplyChest.count(chest, s -> isIronGear(s.getItem())) < 2) {
			wants.add(Want.IRON);
		}
		return wants;
	}

	/** True when the chest holds anything worth taking to a village: surplus goods or emeralds. */
	public static boolean hasSomethingToTrade(Container chest) {
		if (SupplyChest.count(chest, s -> s.is(Items.EMERALD)) > 0) {
			return true;
		}
		boolean foodToSpare = CampNeeds.need(CampNeeds.Need.FOOD) < 0.3;
		for (Good good : GOODS) {
			if ((!good.food() || foodToSpare) && SupplyChest.count(chest, good.item()) > good.keep()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Packs goods for a trip from the chest into the backpack: surplus of up to {@value #MAX_KINDS} kinds (a stack
	 * of each at most), a few emeralds, and a book or two for a librarian when enchanted books are wanted, always
	 * leaving two backpack slots free for what is bought. Returns how many items were taken.
	 */
	public static int pack(CompanionEntity c, Container chest, Set<Want> wants) {
		Backpack bp = c.backpack();
		int taken = 0;
		int kinds = 0;
		boolean foodToSpare = CampNeeds.need(CampNeeds.Need.FOOD) < 0.3;
		Trips.packKit(c, chest);
		taken += SupplyChest.withdraw(chest, bp, s -> s.is(Items.EMERALD), MAX_EMERALDS_TAKEN);
		if (wants.contains(Want.BOOKS) && bp.freeSlots() > 2) {
			taken += SupplyChest.withdraw(chest, bp, s -> s.is(Items.BOOK), 2);
		}
		for (Good good : GOODS) {
			if (kinds >= MAX_KINDS || bp.freeSlots() <= 2) {
				break;
			}
			if (good.food() && !foodToSpare) {
				continue;
			}
			int spare = SupplyChest.count(chest, good.item()) - good.keep();
			if (spare <= 0) {
				continue;
			}
			int moved = SupplyChest.withdraw(chest, bp, good.item(), Math.min(64, spare));
			if (moved > 0) {
				taken += moved;
				kinds++;
			}
		}
		return taken;
	}

	/** One trade done: what was got, for the friend to say and the summary. */
	public record Done(ItemStack got, boolean sold) {
	}

	/**
	 * Makes the next useful trade with this villager, if any: selling surplus for emeralds first (while fewer than
	 * {@value #EMERALDS_ENOUGH} are carried), then buying what is wanted with what is carried. {@code made} counts the
	 * trades made of each want so far this trip.
	 */
	public static Optional<Done> tradeOnce(CompanionEntity c, Villager villager, Set<Want> wants, Map<Want, Integer> made) {
		if (!(c.level() instanceof ServerLevel)) {
			return Optional.empty();
		}
		MerchantOffers offers = villager.getOffers();
		if (c.backpack().count(Items.EMERALD) < EMERALDS_ENOUGH) {
			for (MerchantOffer offer : offers) {
				if (!offer.isOutOfStock() && offer.getResult().is(Items.EMERALD) && sellable(offer) && affordable(c, offer)
					&& trade(c, villager, offer)) {
					return Optional.of(new Done(offer.getResult().copy(), true));
				}
			}
		}
		for (MerchantOffer offer : offers) {
			if (offer.isOutOfStock() || offer.getResult().is(Items.EMERALD)) {
				continue;
			}
			Want want = wantFor(offer.getResult(), wants, made);
			if (want != null && affordable(c, offer) && trade(c, villager, offer)) {
				made.merge(want, 1, Integer::sum);
				return Optional.of(new Done(offer.getResult().copy(), false));
			}
		}
		return Optional.empty();
	}

	/** True when a villager has any offer a friend carrying this backpack could use now. */
	public static boolean anythingFor(CompanionEntity c, Villager villager, Set<Want> wants, Map<Want, Integer> made) {
		for (MerchantOffer offer : villager.getOffers()) {
			if (offer.isOutOfStock() || !affordable(c, offer)) {
				continue;
			}
			if (offer.getResult().is(Items.EMERALD) ? sellable(offer) : wantFor(offer.getResult(), wants, made) != null) {
				return true;
			}
		}
		return false;
	}

	private static @Nullable Want wantFor(ItemStack result, Set<Want> wants, Map<Want, Integer> made) {
		for (Want want : wants) {
			if (want.matches(result) && made.getOrDefault(want, 0) < want.trades()) {
				return want;
			}
		}
		return null;
	}

	/** An offer buying something the camp has spare (paid in a single kind of item). */
	private static boolean sellable(MerchantOffer offer) {
		if (!offer.getCostB().isEmpty()) {
			return false;
		}
		ItemStack cost = offer.getCostA();
		for (Good good : GOODS) {
			if (good.item().test(cost)) {
				return true;
			}
		}
		return false;
	}

	/** True when the backpack holds the full price of an offer, and has room for what it gives. */
	private static boolean affordable(CompanionEntity c, MerchantOffer offer) {
		Backpack bp = c.backpack();
		ItemCost a = offer.getItemCostA();
		if (bp.count(a::test) < offer.getCostA().getCount()) {
			return false;
		}
		Optional<ItemCost> b = offer.getItemCostB();
		if (b.isPresent() && bp.count(b.get()::test) < offer.getCostB().getCount()) {
			return false;
		}
		return bp.canFit(offer.getResult());
	}

	/** Pays for an offer from the backpack and takes what it gives, as a player's trade does. */
	private static boolean trade(CompanionEntity c, Villager villager, MerchantOffer offer) {
		Backpack bp = c.backpack();
		ItemStack payA = bp.take(offer.getItemCostA()::test, offer.getCostA().getCount());
		ItemStack payB = offer.getItemCostB().map(b -> bp.take(b::test, offer.getCostB().getCount())).orElse(ItemStack.EMPTY);
		boolean paid = offer.take(payA, payB);
		giveBack(c, payA);
		giveBack(c, payB);
		if (!paid) {
			return false;
		}
		ItemStack got = offer.assemble();
		villager.notifyTrade(offer);
		villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
		giveBack(c, got);
		c.getLookControl().setLookAt(villager);
		villager.getLookControl().setLookAt(c);
		c.swingArm();
		return true;
	}

	private static void giveBack(CompanionEntity c, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStack left = c.backpack().insert(stack);
		if (!left.isEmpty() && c.level() instanceof ServerLevel level) {
			c.spawnAtLocation(level, left);
		}
	}

	/** True when a villager is someone a friend may trade with now: grown up, awake and not busy with a player. */
	public static boolean available(Villager villager) {
		return villager.isAlive() && !villager.isBaby() && !villager.isSleeping() && !villager.isTrading();
	}

	/** A plain description of what was got, e.g. "6 bread". */
	public static String describe(ItemStack stack) {
		return stack.getCount() + " " + stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
	}

	/** Totals of what was bought and sold, for the summary at home. */
	public static final class Tally {
		private final Map<Want, Integer> made = new EnumMap<>(Want.class);
		private int sold;
		private int bought;

		public Map<Want, Integer> made() {
			return made;
		}

		public void add(Done done) {
			if (done.sold()) {
				sold++;
			} else {
				bought++;
			}
		}

		public int sold() {
			return sold;
		}

		public int bought() {
			return bought;
		}
	}
}
