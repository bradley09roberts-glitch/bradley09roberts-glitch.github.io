package io.github.bradley09roberts.hardcorefriends.market;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * A shop as its keeper runs it today: where they stand ({@code counter}), what line of goods it keeps ({@code type},
 * see {@link Catalogue}), the chests its stock is in, and the chest its takings go to. A shop building keeps its stock
 * in its own chests (everything of its line in them is for sale) and its takings in the first of them. A stallholder at
 * the camp, before the village has a shop, trades straight from the supply chest, and only what the camp can spare.
 *
 * @param key the workplace key, or {@code "camp"} for the stall at the supply chest
 */
record Shop(String key, String type, String title, String described, BlockPos counter, @Nullable BlockPos customer,
	List<BlockPos> stock, @Nullable BlockPos treasury, boolean stall) {

	/** The keeper's shop, if their trade has them keep one (a shop workplace, or the stall at the camp). */
	static Optional<Shop> of(ServerLevel level, CompanionEntity keeper, MarketData.@Nullable Holding h) {
		if (h == null) {
			return Optional.empty();
		}
		if (h.atCamp()) {
			if (h.trade != Trade.SHOPKEEPER) {
				return Optional.empty();
			}
			Optional<BlockPos> chest = Stores.supplyPos(level);
			return chest.map(pos -> new Shop("camp", "stall", "Camp Stall", "the camp stall", pos, null, List.of(pos), pos, true));
		}
		Optional<Workplace> found = Workplaces.byKey(level, h.site);
		if (found.isEmpty() || !found.get().isShop() || found.get().counter() == null) {
			return Optional.empty();
		}
		Workplace w = found.get();
		List<BlockPos> chests = Stores.chests(level, w);
		String title = w.kind().equals("civic:market") ? "Market Stall" : capitalised(w.name());
		return Optional.of(new Shop(w.key(), w.shopType(), title, w.described(), w.counter(), w.customer(), chests,
			chests.isEmpty() ? null : chests.getFirst(), false));
	}

	/** "General Store" from "general store". */
	static String capitalised(String name) {
		StringBuilder sb = new StringBuilder(name.length());
		boolean start = true;
		for (char ch : name.toCharArray()) {
			sb.append(start ? Character.toUpperCase(ch) : ch);
			start = ch == ' ';
		}
		return sb.toString();
	}
}
