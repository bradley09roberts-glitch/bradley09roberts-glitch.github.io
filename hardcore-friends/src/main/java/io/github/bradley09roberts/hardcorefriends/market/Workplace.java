package io.github.bradley09roberts.hardcorefriends.market;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.architecture.Construction;

/**
 * One place a trade is worked: a finished library building of a shop or workplace kind (or one stall of the market
 * square, which has four). {@code key} is the site key, with {@code #n} for a market stall. The spots come from the
 * plan's markers: {@code job} (the work station), {@code counter} and {@code customer} (shops), and the {@code chest}
 * markers (the shop's stock, the workplace's store); any other marker is looked up when a job needs it.
 */
public record Workplace(String key, String site, int slot, String kind, Trade trade, String name, BlockPos job,
	@Nullable BlockPos counter, @Nullable BlockPos customer, List<BlockPos> chests) {

	/** A shop: players may trade with its keeper at the counter. */
	public boolean isShop() {
		return counter != null && (kind.startsWith("shop:") || kind.equals("civic:market"));
	}

	/** The shop's line of goods: "bakery", "general", "butcher", "fishmonger", "tailor" or "smith". */
	public String shopType() {
		if (kind.equals("civic:market")) {
			return "general";
		}
		int colon = kind.indexOf(':');
		return colon >= 0 ? kind.substring(colon + 1) : kind;
	}

	/** "the bakery", "a market stall". */
	public String described() {
		return kind.equals("civic:market") ? "a market stall" : "the " + name;
	}

	/** World positions of another of the plan's markers ({@code fishing}, {@code hive}, {@code teacher}...). */
	public List<BlockPos> markers(ServerLevel level, String marker) {
		return Construction.markers(level, site, marker);
	}
}
