package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.civic.BlueprintLibrary;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speciality;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.survival.Skills;

/**
 * Sage's planning of the village's trades, every ten seconds in the camp's world: who holds which trade, and which
 * workplaces to ask the village for next.
 *
 * <p><b>Who.</b> Each standing workplace without a holder goes to the grown-up who suits it best: their speciality
 * among the trade's suited ones (the best suited first), their interest, their skill at that kind of work, a newcomer
 * before one of the nine (a newcomer shares a speciality with a named friend, so their time is freest), and never the
 * last friend of a speciality if anyone else will do. Only grown-ups at work in the camp's world are chosen, never a
 * child or a stranger, and at least {@code friendsWithoutTrade} grown-ups are always left without a trade, so the camp's
 * own work never runs short of hands (a trade's jobs only add to its holder's day; their speciality still comes first
 * when the camp needs it). Someone holding a trade at the camp moves to its workplace once one stands.
 *
 * <p><b>At the camp.</b> Before the village has workplaces, a camp of six or more grown-ups gives a few trades to people
 * plainly: a stallholder at the supply chest (from the Hamlet), a fisher on the nearest bank, a shepherd at the pen and a
 * cook at the campfire.
 *
 * <p><b>Letting go.</b> A trade is given up when its holder dies, is dismissed, has not been seen in the camp's world for
 * two days, or their workplace no longer stands.
 *
 * <p><b>Asking for buildings.</b> Once an in-game day at most, while the village has fewer workplaces than about half its
 * grown-ups, the next workplace on the list that suits the camp (a fishing hut where there is water, a school once
 * there are children) is asked of the village's town plan ({@link VillageLink}).
 */
final class Planner {
	private static final int INTERVAL = 200;
	private static final int REQUEST_EVERY = 1200;
	/** A holder not seen in the camp's world for this long has let their trade go. */
	private static final long ABSENT_TICKS = 48_000;
	/** A camp needs this many grown-ups before anyone takes up a trade at the camp itself. */
	private static final int CAMP_TRADE_ADULTS = 6;
	/** Days before the same kind of building is asked for again. */
	private static final long ASK_AGAIN_DAYS = 3;

	/** One building the market would like, from what stage, and when it makes sense. */
	private record Want(String kind, int stage, String reason, Predicate<ServerLevel> when) {
	}

	private static final List<Want> WANTS = List.of(
		new Want("shop:general", 2, "a general store, so the village can trade", level -> true),
		new Want("workplace:fisher", 2, "a fishing hut on the bank for fresh fish", level -> !FishSpots.campSpots(level).isEmpty()),
		new Want("workplace:farmer", 3, "a farm shed for the farmer's composter", level -> true),
		new Want("shop:bakery", 3, "a bakery for bread and pies", level -> true),
		new Want("civic:tavern", 3, "a tavern where meals are cooked for everyone", level -> true),
		new Want("workplace:shepherd", 3, "a shepherd's hut for the flock", Planner::penHasSheep),
		new Want("workplace:carpenter", 3, "a carpenter's workshop for stairs, doors and furniture", level -> true),
		new Want("civic:school", 3, "a school for the children", level -> anyChild(level)),
		new Want("workplace:mason", 4, "a mason's yard to cut stone for the builders", Planner::ironToSpare),
		new Want("workplace:blacksmith", 4, "a smithy for tools and armour", Planner::ironToSpare),
		new Want("workplace:doctor", 4, "a clinic for the hurt and poorly", level -> true),
		new Want("workplace:beekeeper", 4, "an apiary for honey", level -> true),
		new Want("shop:butcher", 4, "a butcher's shop", level -> Pen.of(level).isPresent()),
		new Want("shop:tailor", 4, "a tailor's shop for carpets and beds", level -> true),
		new Want("shop:fishmonger", 4, "a fish stall by the fishing hut", level -> !Workplaces.ofKind(level, "workplace:fisher").isEmpty()),
		new Want("shop:smith", 4, "a smith's shop to sell tools", level -> !Workplaces.ofKind(level, "workplace:blacksmith").isEmpty()));

	/** The trades held at the camp before their workplaces stand, in the order they are given. */
	private static final List<Trade> CAMP_TRADES = List.of(Trade.INNKEEPER, Trade.FISHER, Trade.SHOPKEEPER, Trade.SHEPHERD);

	private Planner() {
	}

	/** Every server tick: does its work every {@value #INTERVAL} ticks in the camp's world. */
	static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % INTERVAL != 61) {
			return;
		}
		ServerLevel level = campLevel(server);
		if (level == null) {
			return;
		}
		plan(server, level);
		if (t % REQUEST_EVERY == 61) {
			request(server, level);
		}
	}

	/** The camp's world, if a camp is set. */
	static @Nullable ServerLevel campLevel(MinecraftServer server) {
		CampData data = Camp.data(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (Camp.isCampLevel(level, data)) {
				return level;
			}
		}
		return null;
	}

	/** Works out who holds which trade now (also run by {@code /friends trades} so the list is fresh). */
	static void plan(MinecraftServer server, ServerLevel level) {
		MarketData data = MarketData.get(server);
		long now = level.getGameTime();
		Map<UUID, CompanionEntity> loaded = new HashMap<>();
		for (CompanionEntity c : Companions.in(level)) {
			loaded.put(c.getUUID(), c);
		}
		letGo(server, level, data, loaded, now);
		if (!FriendsConfig.get().villageTrades) {
			return;
		}
		List<CompanionEntity> adults = new ArrayList<>();
		for (CompanionEntity c : loaded.values()) {
			if (!c.isChild() && c.isAlive()) {
				adults.add(c);
			}
		}
		adults.sort(Comparator.comparingInt(CompanionEntity::rosterIndex));
		int maxHolders = Math.max(0, adults.size() - FriendsConfig.get().friendsWithoutTrade);
		// Workplaces first: a camp holder of the same trade moves in, otherwise the best-suited free grown-up.
		for (Workplace w : Workplaces.all(level)) {
			if (data.holderOf(w.key()).isPresent()) {
				continue;
			}
			MarketData.Holding atCamp = campHolder(data, w.trade());
			if (atCamp != null) {
				CompanionEntity c = loaded.get(atCamp.who);
				data.assign(atCamp.who, w.trade(), w.key(), atCamp.name, Camp.day(level), now);
				if (c != null) {
					Speech.say(c, Line.TRADE_TAKEN, w.trade().title().toLowerCase(Locale.ROOT));
				}
				announce(server, atCamp.name + " has moved into " + w.described() + " as the village's "
					+ w.trade().title().toLowerCase(Locale.ROOT) + ".");
				continue;
			}
			if (data.holdings().size() >= maxHolders) {
				continue;
			}
			CompanionEntity best = choose(data, adults, w.trade());
			if (best != null) {
				take(server, level, data, best, w.trade(), w, now);
			}
		}
		// Plainer trades at the camp, while there is no workplace for them.
		if (adults.size() < CAMP_TRADE_ADULTS) {
			return;
		}
		for (Trade trade : CAMP_TRADES) {
			if (data.holdings().size() >= maxHolders) {
				return;
			}
			if (anyHolder(data, trade) || hasWorkplace(level, trade) || !campTradePossible(level, trade, false)) {
				continue;
			}
			CompanionEntity best = choose(data, adults, trade);
			if (best != null) {
				take(server, level, data, best, trade, null, now);
			}
		}
	}

	/** Gives up the trades whose holders are gone, grew distant, or whose workplace no longer stands. */
	private static void letGo(MinecraftServer server, ServerLevel level, MarketData data, Map<UUID, CompanionEntity> loaded, long now) {
		for (MarketData.Holding h : new ArrayList<>(data.holdings())) {
			CompanionEntity c = loaded.get(h.who);
			if (c != null) {
				if (c.isChild() || !c.isTeamMember()) {
					data.release(h.who);
					continue;
				}
				data.seen(h, now, c.displayName());
			} else if (now - h.seen > ABSENT_TICKS) {
				data.release(h.who);
				continue;
			} else if (now < h.seen) {
				data.seen(h, now, h.name); // a clock from another world: start counting afresh
			}
			// A camp trade lapses when the camp can no longer support it (the pen is empty, the stall's chest is gone); a
			// workplace's trade when the building's site is let go.
			// (A camp trade is only judged while its holder is about, so the camp's ground is loaded to look at.)
			boolean gone = h.atCamp() ? c != null && !campTradePossible(level, h.trade, true) : Workplaces.byKey(level, h.site).isEmpty();
			if (gone) {
				data.release(h.who);
			}
		}
	}

	/** Someone who has died, been dismissed or left: their trade is free at once. */
	static void release(MinecraftServer server, UUID who) {
		MarketData.get(server).release(who);
	}

	private static void take(MinecraftServer server, ServerLevel level, MarketData data, CompanionEntity c, Trade trade,
		@Nullable Workplace w, long now) {
		data.assign(c.getUUID(), trade, w == null ? "" : w.key(), c.displayName(), Camp.day(level), now);
		String title = (w == null ? trade.campTitle() : trade.title()).toLowerCase(Locale.ROOT);
		Speech.say(c, Line.TRADE_TAKEN, title);
		Optional<CompanionEntity> sage = Companions.find(FriendId.SAGE).filter(s -> s != c && s.level() == level);
		String where = w == null ? " at the camp" : ", at " + w.described();
		announce(server, (sage.isPresent() ? "Sage has asked " + c.displayName() + " to be the village's " : c.displayName() + " is now the village's ")
			+ title + where + ".");
	}

	private static void announce(MinecraftServer server, String text) {
		Speech.announce(server, Component.literal(text).withStyle(ChatFormatting.GRAY));
	}

	/** The free grown-up who suits this trade best, or null. */
	private static @Nullable CompanionEntity choose(MarketData data, List<CompanionEntity> adults, Trade trade) {
		Map<Role, Integer> perRole = new EnumMap<>(Role.class);
		for (CompanionEntity c : adults) {
			if (c.mode() == CompanionMode.WORK) {
				perRole.merge(c.friendId().role(), 1, Integer::sum);
			}
		}
		CompanionEntity best = null;
		double bestFit = Double.NEGATIVE_INFINITY;
		for (CompanionEntity c : adults) {
			if (c.mode() != CompanionMode.WORK || data.holding(c.getUUID()) != null) {
				continue;
			}
			double fit = fit(c, trade, perRole);
			if (fit > bestFit) {
				bestFit = fit;
				best = c;
			}
		}
		return best;
	}

	/** How well a grown-up suits a trade (see the class comment). */
	static double fit(CompanionEntity c, Trade trade, Map<Role, Integer> perRole) {
		Role role = c.friendId().role();
		List<Role> suits = trade.suits();
		double fit = 0;
		int rank = suits.indexOf(role);
		if (rank >= 0) {
			fit += 6 - 2 * rank;
		}
		if (suits.contains(Speciality.interest(c.friendId()))) {
			fit += 2;
		}
		fit += 0.5 * Skills.level(c, Speciality.workName(trade.skillRole()));
		if (c.isSettler()) {
			fit += 3;
		}
		if (perRole.getOrDefault(role, 0) <= 1) {
			fit -= 4; // the only one of their speciality at work: their own work needs them
		}
		if (role == Role.WARRIOR && trade != Trade.BLACKSMITH && trade != Trade.BUTCHER) {
			fit -= 3; // a fighter keeps the watch and guards the camp
		}
		return fit;
	}

	private static MarketData.@Nullable Holding campHolder(MarketData data, Trade trade) {
		for (MarketData.Holding h : data.holdings()) {
			if (h.trade == trade && h.atCamp()) {
				return h;
			}
		}
		return null;
	}

	private static boolean anyHolder(MarketData data, Trade trade) {
		for (MarketData.Holding h : data.holdings()) {
			if (h.trade == trade) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasWorkplace(ServerLevel level, Trade trade) {
		for (Workplace w : Workplaces.all(level)) {
			if (w.trade() == trade) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True if a trade can be worked at the camp itself, plainly, before it has a workplace. {@code keeping}: asked for
	 * someone who already holds it, who keeps it on a little less (one sheep left in the pen is still a flock to tend).
	 */
	static boolean campTradePossible(ServerLevel level, Trade trade, boolean keeping) {
		CampData data = Camp.data(level.getServer());
		return switch (trade) {
			case SHOPKEEPER -> data.stage() >= 2 && SupplyChest.of(level).isPresent();
			case FISHER -> !FishSpots.campSpots(level).isEmpty();
			case SHEPHERD -> sheepInPen(level) >= (keeping ? 1 : 2);
			case INNKEEPER -> data.isCompleted(Structures.CAMPFIRE) || data.isCompleted(Structures.FURNACE);
			default -> false;
		};
	}

	static boolean penHasSheep(ServerLevel level) {
		return sheepInPen(level) >= 2;
	}

	private static long sheepInPen(ServerLevel level) {
		Optional<Pen> pen = Pen.of(level);
		return pen.isEmpty() ? 0 : pen.get().animals(level).stream().filter(a -> a instanceof Sheep).count();
	}

	private static boolean ironToSpare(ServerLevel level) {
		return Stores.supplyCount(level, s -> s.is(Items.IRON_INGOT)) >= 16;
	}

	private static boolean anyChild(ServerLevel level) {
		for (CompanionEntity c : Companions.in(level)) {
			if (c.isChild()) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- requests

	/** Asks the village for the next workplace on the list, once a day at most (see the class comment). */
	private static void request(MinecraftServer server, ServerLevel level) {
		FriendsConfig cfg = FriendsConfig.get();
		if (!cfg.villageTrades || !cfg.requestWorkplaces || !VillageLink.available()) {
			return;
		}
		MarketData data = MarketData.get(server);
		long day = Camp.day(level);
		for (long asked : data.requested().values()) {
			if (asked == day) {
				return; // one request a day is plenty
			}
		}
		int adults = 0;
		for (CompanionEntity c : Companions.in(level)) {
			if (!c.isChild()) {
				adults++;
			}
		}
		int stage = Camp.data(server).stage();
		int have = 0;
		for (Want want : WANTS) {
			if (Workplaces.underWay(level, want.kind())) {
				have++;
			}
		}
		if (have >= adults / 2 + 1) {
			return;
		}
		for (Want want : WANTS) {
			if (stage < want.stage() || Workplaces.underWay(level, want.kind())) {
				continue;
			}
			long last = data.requestedOn(want.kind());
			if (last >= 0 && day - last < ASK_AGAIN_DAYS && day >= last) {
				continue;
			}
			if (BlueprintLibrary.get().byKind(want.kind()).isEmpty() || !want.when().test(level)) {
				continue;
			}
			data.markRequested(want.kind(), day);
			VillageLink.request(server, want.kind(), want.reason());
			return;
		}
	}
}
