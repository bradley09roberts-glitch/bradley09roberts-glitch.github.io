package io.github.bradley09roberts.hardcorefriends.life;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.civic.Professions;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;

/**
 * Market day, every Saturday from the morning until the late afternoon: the village's shopkeepers (the market part's
 * trades that keep a shop: the shopkeeper, the baker, the butcher, the fishmonger, the tailor and the smith) spend more
 * of the day at their stalls ({@link MarketStallTask}), calling out to passers-by, so a player finds them at the counter
 * and can trade; and the others come to look round the stalls in their spare time ({@link MarketVisitTask}). Prices and
 * stock stay the market's own: nothing is added, nothing duplicated.
 */
final class MarketDay {
	/** The trades that keep a shop counter. */
	static final Set<String> SHOP_TRADES = Set.of("shopkeeper", "baker", "butcher", "fishmonger", "tailor", "blacksmith");
	static final long FROM = 1500;
	static final long UNTIL = 10000;

	private MarketDay() {
	}

	/** True during market hours today, in the camp's world, by daylight. */
	static boolean open(ServerLevel level) {
		if (!FriendsConfig.get().villageLife || !Camp.isCampLevel(level, Camp.data(level.getServer())) || Camp.isNight(level)) {
			return false;
		}
		long day = Calendar.today(level.getServer());
		long time = Calendar.time(level.getServer());
		return Calendar.marketDay(day) && time >= FROM && time < UNTIL;
	}

	/** True if the village has any stalls to visit: a market square, or someone keeping a shop. */
	static boolean hasStalls(ServerLevel level) {
		if (VillagePlan.isBuilt(level.getServer(), "civic:market")) {
			return true;
		}
		for (CompanionEntity c : Places.freePeople(level)) {
			if (stall(c).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/** Where this friend keeps a stall (their shop's work place), if they hold a shop trade. */
	static Optional<BlockPos> stall(CompanionEntity c) {
		Optional<String> trade = Professions.get().professionOf(c);
		if (trade.isEmpty() || !SHOP_TRADES.contains(trade.get())) {
			return Optional.empty();
		}
		return Professions.get().workplaceOf(c);
	}

	/** "the baker's stall". */
	static String stallName(CompanionEntity c) {
		String title = Professions.get().professionOf(c).map(t -> Professions.get().title(t)).orElse("market");
		return "the " + title.toLowerCase(Locale.ROOT) + "'s stall";
	}
}
