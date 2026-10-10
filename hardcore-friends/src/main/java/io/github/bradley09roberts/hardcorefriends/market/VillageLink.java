package io.github.bradley09roberts.hardcorefriends.market;

import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;

/**
 * The market's one way of asking the village for a building: the village package decides <i>where</i> shops and
 * workplaces go, so the market only says which kind it would like, and why, through {@link VillagePlan}. With the
 * village's homes and town plan turned off ({@code villageHomes}) nothing is asked for and the trades work plainly at
 * the camp. Built workplaces are found through the building records themselves ({@link Workplaces}), so nothing else
 * of the village is needed.
 */
final class VillageLink {
	private VillageLink() {
	}

	/** True if the village's town plan takes requests (it is on in the settings). */
	static boolean available() {
		return FriendsConfig.get().villageHomes;
	}

	/**
	 * Asks the village to put up a building of this kind ({@code "shop:bakery"}), saying why for its listings. Returns
	 * false if there is no town plan to ask, or the building library has no plan of that kind.
	 */
	static boolean request(MinecraftServer server, String kind, String reason) {
		return available() && VillagePlan.requestBuilding(server, kind, reason);
	}
}
