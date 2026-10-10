package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * What the world remembers about the village's trades: who holds which trade and where (a workplace's site key, or
 * the camp itself for a trade held before its workplace is built), the building kinds the market has asked the village
 * for and when, and each shop's takings. People are known by entity UUID, which stays the same across dimensions.
 * Stored in {@code data/hardcorefriends_market.dat}.
 */
public final class MarketData extends SavedData {
	public static final Codec<MarketData> CODEC = CompoundTag.CODEC.xmap(MarketData::fromTag, MarketData::toTag);
	public static final SavedDataType<MarketData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "market"), MarketData::new, CODEC, null);

	/** One grown-up's trade. */
	public static final class Holding {
		public final UUID who;
		public final Trade trade;
		/** The workplace's key (see {@link Workplace#key()}), or "" for a trade held at the camp. */
		public final String site;
		/** Their name when they took it up, for listings while they are away. */
		public String name;
		/** In-game day they took it up. */
		public final long since;
		/** Game time they were last seen loaded in the camp's world. */
		public long seen;

		Holding(UUID who, Trade trade, String site, String name, long since, long seen) {
			this.who = who;
			this.trade = trade;
			this.site = site;
			this.name = name;
			this.since = since;
			this.seen = seen;
		}

		/** True while held at the camp, with no workplace of its own. */
		public boolean atCamp() {
			return site.isEmpty();
		}

		/** "Baker", or the camp title ("Stallholder") while held at the camp. */
		public String title() {
			return atCamp() ? trade.campTitle() : trade.title();
		}
	}

	private final Map<UUID, Holding> holdings = new LinkedHashMap<>();
	/** Building kinds asked of the village, with the in-game day of the last request. */
	private final Map<String, Long> requested = new LinkedHashMap<>();
	/** Trades made with players, per workplace key (or "camp"), and emeralds that changed hands. */
	private final Map<String, Long> sales = new LinkedHashMap<>();
	private long emeraldsTaken;
	private long emeraldsPaid;

	public MarketData() {
	}

	/** The world's market record (created empty the first time). */
	public static MarketData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// --------------------------------------------------------------- holdings

	/** This person's trade, or null if they hold none. */
	public @Nullable Holding holding(UUID who) {
		return holdings.get(who);
	}

	/** Who holds the trade at this workplace key, if anyone. */
	public Optional<Holding> holderOf(String site) {
		for (Holding h : holdings.values()) {
			if (h.site.equals(site)) {
				return Optional.of(h);
			}
		}
		return Optional.empty();
	}

	/** Every trade held, in the order given (read only). */
	public Collection<Holding> holdings() {
		return Collections.unmodifiableCollection(holdings.values());
	}

	/** Gives this person a trade (replacing any they held). */
	public Holding assign(UUID who, Trade trade, String site, String name, long day, long now) {
		Holding h = new Holding(who, trade, site, name, day, now);
		holdings.put(who, h);
		setDirty();
		return h;
	}

	/** Takes this person's trade away (they died, left, grew distant or the workplace is gone). */
	public @Nullable Holding release(UUID who) {
		Holding h = holdings.remove(who);
		if (h != null) {
			setDirty();
		}
		return h;
	}

	/** Notes a holder seen at the camp now (and their name, for listings while they are away). */
	public void seen(Holding h, long now, String name) {
		if (h.seen != now || !h.name.equals(name)) {
			h.seen = now;
			h.name = name;
			setDirty();
		}
	}

	// --------------------------------------------------------------- requests

	/** The in-game day this kind was last asked for, or -1. */
	public long requestedOn(String kind) {
		return requested.getOrDefault(kind, -1L);
	}

	/** Remembers that this building kind was asked of the village today. */
	public void markRequested(String kind, long day) {
		requested.put(kind, day);
		setDirty();
	}

	/** Every building kind asked for, with the day it was last asked (read only). */
	public Map<String, Long> requested() {
		return Collections.unmodifiableMap(requested);
	}

	// ------------------------------------------------------------------ sales

	/** Counts one trade with a player at a shop, and the emeralds paid in or out. */
	public void recordSale(String shop, int emeraldsIn, int emeraldsOut) {
		sales.merge(shop, 1L, Long::sum);
		emeraldsTaken += Math.max(0, emeraldsIn);
		emeraldsPaid += Math.max(0, emeraldsOut);
		setDirty();
	}

	/** Trades made at one shop (its workplace key, or "camp"). */
	public long sales(String shop) {
		return sales.getOrDefault(shop, 0L);
	}

	/** Trades made at every shop together. */
	public long totalSales() {
		long total = 0;
		for (long n : sales.values()) {
			total += n;
		}
		return total;
	}

	/** Emeralds players have paid the shops, all told. */
	public long emeraldsTaken() {
		return emeraldsTaken;
	}

	/** Emeralds the shops have paid players, all told. */
	public long emeraldsPaid() {
		return emeraldsPaid;
	}

	// ----------------------------------------------------------------- saving

	private static MarketData fromTag(CompoundTag tag) {
		MarketData data = new MarketData();
		for (Tag t : tag.getListOrEmpty("holdings")) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			UUID who = uuid(c.getStringOr("who", ""));
			Optional<Trade> trade = Trade.byId(c.getStringOr("trade", ""));
			if (who == null || trade.isEmpty()) {
				continue;
			}
			data.holdings.put(who, new Holding(who, trade.get(), c.getStringOr("site", ""), c.getStringOr("name", "Someone"),
				c.getLongOr("since", 0L), c.getLongOr("seen", 0L)));
		}
		CompoundTag req = tag.getCompoundOrEmpty("requested");
		for (String kind : req.keySet()) {
			data.requested.put(kind, req.getLongOr(kind, -1L));
		}
		CompoundTag sold = tag.getCompoundOrEmpty("sales");
		for (String shop : sold.keySet()) {
			data.sales.put(shop, sold.getLongOr(shop, 0L));
		}
		data.emeraldsTaken = tag.getLongOr("emeraldsTaken", 0L);
		data.emeraldsPaid = tag.getLongOr("emeraldsPaid", 0L);
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Holding h : holdings.values()) {
			CompoundTag c = new CompoundTag();
			c.putString("who", h.who.toString());
			c.putString("trade", h.trade.id());
			c.putString("site", h.site);
			c.putString("name", h.name);
			c.putLong("since", h.since);
			c.putLong("seen", h.seen);
			list.add(c);
		}
		tag.put("holdings", list);
		CompoundTag req = new CompoundTag();
		requested.forEach(req::putLong);
		tag.put("requested", req);
		CompoundTag sold = new CompoundTag();
		sales.forEach(sold::putLong);
		tag.put("sales", sold);
		tag.putLong("emeraldsTaken", emeraldsTaken);
		tag.putLong("emeraldsPaid", emeraldsPaid);
		return tag;
	}

	private static @Nullable UUID uuid(String s) {
		try {
			return s.isEmpty() ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Holders in a stable order, for listings. */
	public List<Holding> sortedHoldings() {
		List<Holding> list = new ArrayList<>(holdings.values());
		list.sort((a, b) -> a.trade != b.trade ? a.trade.compareTo(b.trade) : a.name.compareToIgnoreCase(b.name));
		return list;
	}
}
