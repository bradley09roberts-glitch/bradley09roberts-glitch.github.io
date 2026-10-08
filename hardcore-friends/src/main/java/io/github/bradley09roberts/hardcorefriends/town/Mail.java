package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModItems;

/**
 * Players' mailboxes and the deliveries friends take there. A player registers a chest or barrel of their own with
 * {@code /friends mailbox}; friends only ever put things into that one container, never take anything out, and never
 * touch any other. A delivery goes to a player of the camp (the owner or a trusted player) whose mailbox is in the
 * camp's dimension within {@code maxDeliveryDistance} blocks of the camp: what they asked for with
 * {@code /friends send <item> <count>} (oldest first), or, every {@value #SURPLUS_EVERY_DAYS} days, a share of what the
 * camp has plenty of (food, torches, coal, arrows), always leaving the camp a good store.
 */
public final class Mail {
	/** A player of the camp gets a share of the camp's surplus at most this often, in in-game days. */
	public static final int SURPLUS_EVERY_DAYS = 3;
	/** How far away a player may be to register the container they look at. */
	public static final double REGISTER_REACH = 5.0;
	/** Most deliveries one player may have waiting. */
	public static final int MAX_PER_PLAYER = 3;
	/** How long the next delivery is worked out for everyone, in ticks. */
	private static final int CACHE_TICKS = 200;
	/** A delivery asked for and not made within this long (three in-game days) is dropped. */
	private static final long REQUEST_LASTS = 72000L;

	/** One kind of item to deliver and how many. */
	public record Want(Identifier item, int count) {
	}

	/** A delivery to make: to whom, where, what, and the request it fills (null for a share of the surplus). */
	public record Delivery(UUID player, String name, BlockPos mailbox, List<Want> wants, TownData.@Nullable SendRequest request) {
	}

	private static long cachedAt = Long.MIN_VALUE;
	private static @Nullable Delivery cached;
	/** Players whose delivery could not be made today (an unreachable mailbox), by day: no more tries until tomorrow. */
	private static final Map<UUID, Long> GAVE_UP = new HashMap<>();

	private Mail() {
	}

	// ------------------------------------------------------------ mailboxes

	/**
	 * Registers the container at {@code pos} as the player's mailbox. Returns why not, or null when registered: it must
	 * be a chest or barrel, not the camp's supply chest, nobody else's mailbox, not locked and not an unopened loot
	 * chest (a dungeon's or a village's).
	 */
	static @Nullable String register(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		if (!SupplyChest.isValidStorage(level, pos)) {
			return "Look at a chest or barrel of your own (within 5 blocks) and try again.";
		}
		CampData camp = Camp.data(level.getServer());
		if (Camp.isCampLevel(level, camp) && camp.chestPos().map(p -> samePlace(level, p, pos)).orElse(false)) {
			return "That is the camp's supply chest. Your mailbox must be a chest or barrel of your own.";
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof RandomizableContainerBlockEntity loot && loot.getLootTable() != null) {
			return "That chest has never been opened: it is not yours. Choose a chest or barrel you placed.";
		}
		if (be instanceof BaseContainerBlockEntity container && container.isLocked()) {
			return "That container is locked, so the friends could not open it.";
		}
		TownData data = TownData.get(level.getServer());
		String dimension = Camp.dimensionId(level);
		for (Map.Entry<UUID, TownData.Mailbox> e : data.mailboxes().entrySet()) {
			if (!e.getKey().equals(player.getUUID()) && e.getValue().dimension().equals(dimension)
				&& samePlace(level, e.getValue().pos(), pos)) {
				return "That is " + data.name(e.getKey()) + "'s mailbox already.";
			}
		}
		data.remember(player);
		data.setMailbox(player.getUUID(), new TownData.Mailbox(dimension, pos.immutable()));
		return null;
	}

	/** The same container: the same block, or the two halves of one double chest. */
	private static boolean samePlace(ServerLevel level, BlockPos a, BlockPos b) {
		if (a.equals(b)) {
			return true;
		}
		if (!level.isLoaded(b)) {
			return false;
		}
		BlockState state = level.getBlockState(b);
		return state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
			&& ChestBlock.getConnectedBlockPos(b, state).equals(a);
	}

	/** Why a mailbox gets no deliveries, or null when it can: the camp's dimension, within reach of the camp. */
	static @Nullable String outOfReach(MinecraftServer server, TownData.Mailbox mailbox) {
		CampData camp = Camp.data(server);
		int max = FriendsConfig.get().maxDeliveryDistance;
		if (max <= 0) {
			return "Deliveries are switched off on this world (maxDeliveryDistance is 0).";
		}
		if (camp.campPos().isEmpty()) {
			return "There is no camp yet to deliver from.";
		}
		if (!camp.campDimension().equals(mailbox.dimension())) {
			return "It is not in the camp's dimension, so friends cannot take deliveries there.";
		}
		double d = Math.sqrt(Camp.horizontalDistSqr(camp.campPos().get(), mailbox.pos()));
		if (d > max) {
			return String.format(java.util.Locale.ROOT, "It is %.0f blocks from the camp; deliveries go up to %d blocks.", d, max);
		}
		return null;
	}

	/** True when this player gets deliveries: a player of the camp (or anyone, when trust is not required). */
	static boolean receives(MinecraftServer server, UUID player) {
		return !FriendsConfig.get().requireTrust || TownPermissions.isOwnerOrTrusted(server, player);
	}

	// ------------------------------------------------------------ deliveries

	/** The next delivery to make, worked out at most every ten seconds for every friend together. */
	static @Nullable Delivery next(ServerLevel level) {
		long now = level.getGameTime();
		if (now - cachedAt >= 0 && now - cachedAt < CACHE_TICKS) {
			return cached;
		}
		cachedAt = now;
		cached = plan(level);
		return cached;
	}

	/** Forgets the worked-out delivery, so the next look is fresh (a delivery started, or a new request). */
	static void invalidate() {
		cachedAt = Long.MIN_VALUE;
	}

	/** Oldest request first; otherwise a share of the surplus for a player whose turn it is. */
	static @Nullable Delivery plan(ServerLevel level) {
		MinecraftServer server = level.getServer();
		CampData camp = Camp.data(server);
		if (!Camp.isCampLevel(level, camp) || FriendsConfig.get().maxDeliveryDistance <= 0) {
			return null;
		}
		TownData data = TownData.get(server);
		long day = Camp.day(level);
		long now = level.getGameTime();
		GAVE_UP.values().removeIf(d -> d != day);
		// A request nobody could carry out in three days (or with nowhere to take it) is dropped, so the queue never clogs.
		data.pruneQueue(r -> now - r.askedAt() > REQUEST_LASTS || now < r.askedAt() || data.mailbox(r.player()).isEmpty());
		for (TownData.SendRequest r : data.queue()) {
			Optional<TownData.Mailbox> box = data.mailbox(r.player());
			if (box.isEmpty() || outOfReach(server, box.get()) != null || !receives(server, r.player()) || GAVE_UP.containsKey(r.player())) {
				continue;
			}
			return new Delivery(r.player(), r.playerName(), box.get().pos(), List.of(new Want(r.item(), r.count())), r);
		}
		Optional<Container> chest = SupplyChest.of(level);
		if (chest.isEmpty()) {
			return null;
		}
		List<Want> surplus = null;
		for (Map.Entry<UUID, TownData.Mailbox> e : data.mailboxes().entrySet()) {
			UUID player = e.getKey();
			if (day - data.lastSurplus(player) < SURPLUS_EVERY_DAYS || outOfReach(server, e.getValue()) != null
				|| !receives(server, player) || GAVE_UP.containsKey(player)) {
				continue;
			}
			if (surplus == null) {
				surplus = surplus(chest.get());
				if (surplus.isEmpty()) {
					return null;
				}
			}
			return new Delivery(player, data.name(player), e.getValue().pos(), surplus, null);
		}
		return null;
	}

	/**
	 * A share of what the camp has plenty of, leaving it a good store: 8 of its most plentiful food when the camp is
	 * well fed and the chest holds 32 or more; 16 torches from 48; 8 coal (or charcoal) from 32; 16 arrows from 64.
	 */
	static List<Want> surplus(Container chest) {
		List<Want> wants = new ArrayList<>();
		if (CampNeeds.need(CampNeeds.Need.FOOD) < 0.1 && SupplyChest.count(chest, CompanionEntity::isEdible) >= 32) {
			Item most = mostOf(chest);
			if (most != null && SupplyChest.count(chest, s -> s.is(most)) >= 16) {
				wants.add(new Want(BuiltInRegistries.ITEM.getKey(most), 8));
			}
		}
		if (SupplyChest.count(chest, s -> s.is(Items.TORCH)) >= 48) {
			wants.add(new Want(BuiltInRegistries.ITEM.getKey(Items.TORCH), 16));
		}
		if (SupplyChest.count(chest, s -> s.is(Items.COAL)) >= 32) {
			wants.add(new Want(BuiltInRegistries.ITEM.getKey(Items.COAL), 8));
		} else if (SupplyChest.count(chest, s -> s.is(Items.CHARCOAL)) >= 32) {
			wants.add(new Want(BuiltInRegistries.ITEM.getKey(Items.CHARCOAL), 8));
		}
		if (SupplyChest.count(chest, s -> s.is(Items.ARROW)) >= 64) {
			wants.add(new Want(BuiltInRegistries.ITEM.getKey(Items.ARROW), 16));
		}
		return wants;
	}

	/** The food the chest holds most of. */
	private static @Nullable Item mostOf(Container chest) {
		Map<Item, Integer> counts = new HashMap<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack s = chest.getItem(i);
			if (!s.isEmpty() && CompanionEntity.isEdible(s)) {
				counts.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		Item best = null;
		int bestCount = 0;
		for (Map.Entry<Item, Integer> e : counts.entrySet()) {
			if (e.getValue() > bestCount) {
				bestCount = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	/**
	 * The item an id names, unless it is something friends never send: a backpack (a fallen friend's, or a fallen
	 * player's things) or a shulker box (a whole store in one).
	 */
	static Optional<Item> sendable(Identifier id) {
		return BuiltInRegistries.ITEM.getOptional(id)
			.filter(item -> item != Items.AIR && item != ModItems.BACKPACK && !new ItemStack(item).is(ItemTags.SHULKER_BOXES));
	}

	/** True for what may go into a delivery: the right item, and never a bag of anyone's belongings. */
	static boolean matches(ItemStack s, Item item) {
		return !s.isEmpty() && s.is(item) && !s.is(ModItems.BACKPACK) && !s.is(ItemTags.SHULKER_BOXES);
	}

	/** No more tries today for this player's delivery (their mailbox could not be reached). */
	static void gaveUp(ServerLevel level, UUID player) {
		GAVE_UP.put(player, Camp.day(level));
		invalidate();
	}

	static void clear() {
		cachedAt = Long.MIN_VALUE;
		cached = null;
		GAVE_UP.clear();
	}
}
