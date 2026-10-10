package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * One player trading with one shopkeeper across the counter, through the game's own trading screen.
 *
 * <p><b>Nothing made, nothing lost.</b> When the screen opens, the goods on offer are really taken out of the shop's
 * chests and held for the sale, and so are the emeralds the shop might pay out ({@value #FLOAT} at most, from its
 * takings). Each sale uses up exactly the goods it hands over (the screen shows a copy, the held goods are what it
 * stands for) and keeps the emeralds paid; each purchase pays out one held emerald and puts exactly what the player gave
 * into the supply chest. When the screen closes, for any reason (the player shuts it, walks off, logs out or dies, the
 * keeper is called away, night falls, the server stops), everything still held goes back: goods to the shop's chests,
 * emeralds to its takings chest, anything without room to the supply chest, then the keeper's backpack, and only then
 * onto the ground at the counter. An offer runs out as soon as its held goods are gone, and every purchase offer runs
 * out the moment the held emeralds cannot pay for it, so the screen never hands over more than is there.
 *
 * <p>The game's trading screen insists its trader is an entity (it plays the trade sound from one), so this is a marker
 * entity that is never added to the world: it only stands at the keeper's side for the screen's sake.
 */
final class ShopTrade extends Marker implements Merchant {
	/** Most emeralds held to pay players for what the camp needs, per visit. */
	static final int FLOAT = 32;
	/** Most trades of one good offered at once. */
	private static final int MAX_UNITS = 8;
	private static final int MAX_SALES = 10;
	private static final int MAX_PURCHASES = 6;
	/** How far from the counter the keeper may step while serving. */
	private static final double COUNTER_SLACK = 6;
	private static final double CUSTOMER_RANGE = 10;

	/** Goods on sale, really held out of the shop's chests until sold or put back. */
	private static final class Sale {
		final MerchantOffer offer;
		final List<ItemStack> held = new ArrayList<>();
		final int unit;
		final int price;

		Sale(MerchantOffer offer, int unit, int price) {
			this.offer = offer;
			this.unit = unit;
			this.price = price;
		}

		/** Uses up {@code n} of the held goods (they went to the player as the screen's copy). */
		void useUp(int n) {
			int left = n;
			for (ItemStack s : held) {
				int take = Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
				if (left <= 0) {
					break;
				}
			}
			held.removeIf(ItemStack::isEmpty);
		}
	}

	/** Something the village buys: {@code unit} of {@code item} for one emerald. */
	private record Purchase(MerchantOffer offer, Item item, int unit) {
	}

	private final CompanionEntity keeper;
	private final ServerPlayer customer;
	private final Shop shop;
	private final MerchantOffers offers = new MerchantOffers();
	private final List<Sale> sales = new ArrayList<>();
	private final List<Purchase> purchases = new ArrayList<>();
	/** Emeralds held: taken from the shop's takings to pay out, plus those paid in during this visit. */
	private int till;
	private boolean closed;
	private @Nullable Player trading;

	private ShopTrade(CompanionEntity keeper, ServerPlayer customer, Shop shop) {
		super(EntityTypes.MARKER, keeper.level());
		this.keeper = keeper;
		this.customer = customer;
		this.shop = shop;
		this.setPos(keeper.getX(), keeper.getY(), keeper.getZ());
	}

	/**
	 * Lays out the shop's offers and opens the trading screen. Returns null (with nothing taken) if there is nothing to
	 * trade today.
	 */
	static @Nullable ShopTrade open(CompanionEntity keeper, ServerPlayer customer, Shop shop) {
		ShopTrade trade = new ShopTrade(keeper, customer, shop);
		trade.stockUp((ServerLevel) keeper.level());
		if (trade.offers.isEmpty()) {
			trade.close();
			return null;
		}
		trade.trading = customer;
		OptionalInt id = customer.openMenu(new SimpleMenuProvider((containerId, inventory, p) -> new MerchantMenu(containerId, inventory, trade),
			Component.literal(shop.title() + " - " + keeper.displayName())));
		if (id.isEmpty()) {
			trade.close();
			return null;
		}
		customer.sendMerchantOffers(id.getAsInt(), trade.offers, 0, 0, false, false);
		return trade;
	}

	// ------------------------------------------------------------ the offers

	private void stockUp(ServerLevel level) {
		Set<Item> selling = new HashSet<>();
		if (Catalogue.sellsTools(shop.type())) {
			stockTools(level, selling);
		}
		for (Catalogue.Good good : Catalogue.sells(shop.type())) {
			if (sales.size() >= MAX_SALES) {
				break;
			}
			// At the camp stall only what the camp can spare of the good, all its kinds together (oak and spruce planks).
			int spareLeft = shop.stall() ? good.spare(Stores.count(level, shop.stock(), good.match())) : Integer.MAX_VALUE;
			for (ItemStack sample : distinct(level, good, 2)) {
				if (sales.size() >= MAX_SALES) {
					break;
				}
				int available = Math.min(spareLeft, Stores.count(level, shop.stock(), s -> ItemStack.isSameItemSameComponents(s, sample)));
				int units = Math.min(MAX_UNITS, available / good.unit());
				if (units <= 0) {
					continue;
				}
				Sale sale = new Sale(new MerchantOffer(new ItemCost(Items.EMERALD, good.price()), sample.copyWithCount(good.unit()), units, 0, 0.0F),
					good.unit(), good.price());
				int got = take(level, s -> ItemStack.isSameItemSameComponents(s, sample), units * good.unit(), sale.held);
				int real = got / good.unit();
				if (real <= 0) {
					putBack(level, sale.held);
					continue;
				}
				if (real < units) {
					// Fewer than expected came out (someone took some meanwhile): offer what is really held.
					List<ItemStack> extra = new ArrayList<>();
					splitOff(sale.held, got - real * good.unit(), extra);
					putBack(level, extra);
					sale = rebuilt(sale, sample, good, real);
				}
				sales.add(sale);
				offers.add(sale.offer);
				selling.add(sample.getItem());
				spareLeft -= real * good.unit();
			}
		}
		stockPurchases(level, selling);
	}

	/** The smith's tools and armour, one offer for each piece in the shop's chests. */
	private void stockTools(ServerLevel level, Set<Item> selling) {
		for (BlockPos pos : shop.stock()) {
			Optional<Container> box = Stores.at(level, pos);
			if (box.isEmpty()) {
				continue;
			}
			Container c = box.get();
			for (int i = 0; i < c.getContainerSize() && sales.size() < MAX_SALES; i++) {
				ItemStack s = c.getItem(i);
				int price = s.isEmpty() ? 0 : Catalogue.toolPrice(s);
				if (price <= 0 || s.getCount() != 1) {
					continue;
				}
				ItemStack piece = c.removeItem(i, 1);
				c.setChanged();
				if (piece.isEmpty()) {
					continue;
				}
				Sale sale = new Sale(new MerchantOffer(new ItemCost(Items.EMERALD, price), piece.copy(), 1, 0, 0.0F), 1, price);
				sale.held.add(piece);
				sales.add(sale);
				offers.add(sale.offer);
				selling.add(piece.getItem());
			}
		}
	}

	/** What the village buys today, paid from emeralds held out of its takings. */
	private void stockPurchases(ServerLevel level, Set<Item> selling) {
		List<Catalogue.Want> wanted = new ArrayList<>();
		for (Catalogue.Want w : Catalogue.buys(shop.type())) {
			if (!selling.contains(w.item()) && w.needed().test(level)) {
				wanted.add(w);
			}
		}
		if (wanted.isEmpty() || shop.treasury() == null) {
			return;
		}
		Optional<Container> takings = Stores.at(level, shop.treasury());
		if (takings.isEmpty()) {
			return;
		}
		// Plain emeralds only: what is paid out (and put back at closing) is plain emeralds, so nothing changes hands but
		// emeralds for emeralds.
		till += takeFrom(takings.get(), s -> s.is(Items.EMERALD) && s.getComponentsPatch().isEmpty(), FLOAT, new ArrayList<>());
		if (till <= 0) {
			return;
		}
		for (Catalogue.Want w : wanted) {
			if (purchases.size() >= MAX_PURCHASES) {
				break;
			}
			MerchantOffer offer = new MerchantOffer(new ItemCost(w.item(), w.unit()), new ItemStack(Items.EMERALD), Math.min(w.maxTrades(), till), 0, 0.0F);
			purchases.add(new Purchase(offer, w.item(), w.unit()));
			offers.add(offer);
		}
	}

	/** Up to {@code max} different items of a good in the shop's stock (two woods of planks are two offers). */
	private List<ItemStack> distinct(ServerLevel level, Catalogue.Good good, int max) {
		List<ItemStack> samples = new ArrayList<>();
		for (BlockPos pos : shop.stock()) {
			Optional<Container> box = Stores.at(level, pos);
			if (box.isEmpty()) {
				continue;
			}
			Container c = box.get();
			for (int i = 0; i < c.getContainerSize() && samples.size() < max; i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty() || !good.match().test(s) || s.isDamaged() || s.isEnchanted()) {
					continue;
				}
				boolean known = false;
				for (ItemStack k : samples) {
					known |= ItemStack.isSameItemSameComponents(k, s);
				}
				if (!known) {
					samples.add(s.copyWithCount(1));
				}
			}
		}
		return samples;
	}

	/** Takes up to {@code max} matching items out of the shop's stock chests into {@code into}. Returns how many. */
	private int take(ServerLevel level, java.util.function.Predicate<ItemStack> match, int max, List<ItemStack> into) {
		int got = 0;
		for (BlockPos pos : shop.stock()) {
			if (got >= max) {
				break;
			}
			Optional<Container> box = Stores.at(level, pos);
			if (box.isPresent()) {
				got += takeFrom(box.get(), match, max - got, into);
			}
		}
		return got;
	}

	private static int takeFrom(Container c, java.util.function.Predicate<ItemStack> match, int max, List<ItemStack> into) {
		int got = 0;
		for (int i = 0; i < c.getContainerSize() && got < max; i++) {
			ItemStack s = c.getItem(i);
			if (s.isEmpty() || !match.test(s)) {
				continue;
			}
			ItemStack piece = c.removeItem(i, Math.min(max - got, s.getCount()));
			if (!piece.isEmpty()) {
				got += piece.getCount();
				into.add(piece);
			}
		}
		if (got > 0) {
			c.setChanged();
		}
		return got;
	}

	/** Moves {@code n} items off the end of {@code from} into {@code to}. */
	private static void splitOff(List<ItemStack> from, int n, List<ItemStack> to) {
		int left = n;
		for (int i = from.size() - 1; i >= 0 && left > 0; i--) {
			ItemStack s = from.get(i);
			int take = Math.min(left, s.getCount());
			to.add(s.split(take));
			left -= take;
		}
		from.removeIf(ItemStack::isEmpty);
	}

	private static Sale rebuilt(Sale old, ItemStack sample, Catalogue.Good good, int units) {
		Sale sale = new Sale(new MerchantOffer(new ItemCost(Items.EMERALD, good.price()), sample.copyWithCount(good.unit()), units, 0, 0.0F),
			good.unit(), good.price());
		sale.held.addAll(old.held);
		return sale;
	}

	// ------------------------------------------------------------- trading

	@Override
	public void setTradingPlayer(@Nullable Player player) {
		if (player == null) {
			close();
		} else {
			trading = player;
		}
	}

	@Override
	public @Nullable Player getTradingPlayer() {
		return trading;
	}

	@Override
	public MerchantOffers getOffers() {
		return offers;
	}

	@Override
	public void overrideOffers(MerchantOffers newOffers) {
		// Only the player's own screen is ever told the offers; the shop's are what this side holds.
	}

	@Override
	public void notifyTrade(MerchantOffer offer) {
		if (closed || !(keeper.level() instanceof ServerLevel level)) {
			return;
		}
		offer.increaseUses();
		MarketData data = MarketData.get(level.getServer());
		for (Sale sale : sales) {
			if (sale.offer == offer) {
				sale.useUp(sale.unit);
				till += sale.price;
				data.recordSale(shop.key(), sale.price, 0);
				thank(level);
				break;
			}
		}
		for (Purchase p : purchases) {
			if (p.offer == offer) {
				till -= 1;
				// Exactly what the player gave, into the camp's stores.
				Stores.supplyPos(level).ifPresentOrElse(
					pos -> putAway(level, List.of(pos), new ItemStack(p.item(), p.unit())),
					() -> putAway(level, shop.stock(), new ItemStack(p.item(), p.unit())));
				data.recordSale(shop.key(), 0, 1);
				thank(level);
				break;
			}
		}
		boolean changed = false;
		for (Purchase p : purchases) {
			if (till < 1 && !p.offer.isOutOfStock()) {
				p.offer.setToOutOfStock();
				changed = true;
			}
		}
		if (changed && customer.containerMenu instanceof MerchantMenu menu) {
			customer.sendMerchantOffers(menu.containerId, offers, 0, 0, false, false);
		}
	}

	private void thank(ServerLevel level) {
		Speech.say(keeper, Line.SHOP_SALE, customer.getName().getString());
		keeper.swingArm();
		Unity.add(level, "trade", 1, 10);
	}

	@Override
	public void notifyTradeUpdated(ItemStack itemStack) {
	}

	@Override
	public int getVillagerXp() {
		return 0;
	}

	@Override
	public void overrideXp(int xp) {
	}

	@Override
	public boolean showProgressBar() {
		return false;
	}

	@Override
	public SoundEvent getNotifyTradeSound() {
		return SoundEvents.ITEM_PICKUP;
	}

	@Override
	public boolean isClientSide() {
		return false;
	}

	/**
	 * The screen stays open while the keeper is alive, at their counter, not fighting, by day, still keeping this shop,
	 * and the player is close by.
	 */
	@Override
	public boolean stillValid(Player player) {
		if (closed || player != trading || !player.isAlive() || !keeper.isAlive() || keeper.isRemoved()
			|| keeper.level() != player.level() || !(keeper.level() instanceof ServerLevel level)) {
			return false;
		}
		if (player.distanceToSqr(keeper) > CUSTOMER_RANGE * CUSTOMER_RANGE
			|| keeper.position().distanceToSqr(Vec3.atBottomCenterOf(shop.counter())) > COUNTER_SLACK * COUNTER_SLACK) {
			return false;
		}
		if (Camp.isNightTime(level) || keeper.getTarget() != null || keeper.isAsleep()) {
			return false;
		}
		MarketData.Holding h = Market.holding(level.getServer(), keeper);
		return h != null && (shop.stall() ? h.atCamp() && h.trade == Trade.SHOPKEEPER : h.site.equals(shop.key()));
	}

	// --------------------------------------------------------------- closing

	/** True while the screen is open. */
	boolean isOpen() {
		return !closed;
	}

	CompanionEntity keeper() {
		return keeper;
	}

	ServerPlayer customer() {
		return customer;
	}

	/** Puts everything still held back (see the class comment). Safe to call more than once. */
	void close() {
		if (closed) {
			return;
		}
		closed = true;
		trading = null;
		Shops.forget(this);
		if (!(keeper.level() instanceof ServerLevel level)) {
			return;
		}
		for (Sale sale : sales) {
			putBack(level, sale.held);
		}
		sales.clear();
		if (till > 0 && shop.treasury() != null) {
			int left = till;
			till = 0;
			while (left > 0) {
				int n = Math.min(64, left);
				putAway(level, List.of(shop.treasury()), new ItemStack(Items.EMERALD, n));
				left -= n;
			}
		} else if (till > 0) {
			int left = till;
			till = 0;
			while (left > 0) {
				int n = Math.min(64, left);
				putAway(level, shop.stock(), new ItemStack(Items.EMERALD, n));
				left -= n;
			}
		}
		if (customer.containerMenu instanceof MerchantMenu && customer.isAlive() && !customer.hasDisconnected()) {
			keeper.playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.0F);
		}
	}

	private void putBack(ServerLevel level, List<ItemStack> held) {
		for (ItemStack s : held) {
			if (!s.isEmpty()) {
				putAway(level, shop.stock(), s);
			}
		}
		held.clear();
	}

	/**
	 * Puts a stack into the first of {@code where} with room, then the supply chest, then the keeper's backpack (while
	 * they live), then onto the ground at the counter: never lost.
	 */
	private void putAway(ServerLevel level, List<BlockPos> where, ItemStack stack) {
		ItemStack left = stack.copy();
		for (BlockPos p : where) {
			if (left.isEmpty()) {
				return;
			}
			Optional<Container> box = Stores.at(level, p);
			if (box.isPresent()) {
				left = SupplyChest.insert(box.get(), left);
			}
		}
		if (!left.isEmpty()) {
			Optional<Container> supply = SupplyChest.of(level);
			if (supply.isPresent()) {
				left = SupplyChest.insert(supply.get(), left);
			}
		}
		if (!left.isEmpty() && keeper.isAlive() && !keeper.isRemoved()) {
			left = Stores.intoBackpack(keeper.backpack(), left);
		}
		if (!left.isEmpty()) {
			BlockPos at = shop.counter();
			Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, left);
		}
	}
}
