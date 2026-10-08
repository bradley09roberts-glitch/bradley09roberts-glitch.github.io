package io.github.bradley09roberts.hardcorefriends.progress;

import java.util.Map;
import java.util.Optional;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * What the camp owns, counted once for everybody: the supply chest plus every loaded friend's backpack and hand. Sage's
 * plan checks its steps against it and the plan's jobs score from it, so scoring never walks through chests itself.
 * Counted afresh at most every {@value #MAX_AGE} ticks, on the server thread, when someone asks.
 */
public final class CampStock {
	private static final int MAX_AGE = 100;

	/** One count of the camp's things. Immutable once made. */
	public static final class Snapshot {
		private final Object2IntOpenHashMap<Item> total = new Object2IntOpenHashMap<>();
		private final Object2IntOpenHashMap<Item> chest = new Object2IntOpenHashMap<>();
		private int ironPickaxes;
		private int diamondPickaxes;
		private int ironSwords;
		private int fireResistance;
		private int miners;
		private int minersWithIron;
		private int warriors;
		private boolean hasChest;

		/** How many of this item the camp owns (chest, backpacks and hands). */
		public int total(Item item) {
			return total.getInt(item);
		}

		/** How many of this item are in the supply chest. */
		public int inChest(Item item) {
			return chest.getInt(item);
		}

		/** Pickaxes that mine diamonds (iron and better) the camp owns. */
		public int ironPickaxes() {
			return ironPickaxes;
		}

		/** Pickaxes that mine obsidian (diamond and better) the camp owns. */
		public int diamondPickaxes() {
			return diamondPickaxes;
		}

		/** Iron, diamond or netherite swords the camp owns. */
		public int ironSwords() {
			return ironSwords;
		}

		public int fireResistance() {
			return fireResistance;
		}

		/** Loaded miners on the team, and how many of them carry an iron (or better) pickaxe. */
		public int miners() {
			return miners;
		}

		public int minersWithIron() {
			return minersWithIron;
		}

		/** Loaded warriors on the team (the fighters the plan wants iron swords for). */
		public int warriors() {
			return warriors;
		}

		/** True when the supply chest was there to count. */
		public boolean hasChest() {
			return hasChest;
		}

		private void add(ItemStack s, boolean inChest) {
			if (s.isEmpty()) {
				return;
			}
			total.addTo(s.getItem(), s.getCount());
			if (inChest) {
				chest.addTo(s.getItem(), s.getCount());
			}
			int tier = Tiers.pickaxe(s);
			if (tier >= Tiers.IRON) {
				ironPickaxes += s.getCount();
			}
			if (tier >= Tiers.DIAMOND) {
				diamondPickaxes += s.getCount();
			}
			if (Tiers.ironSword(s)) {
				ironSwords += s.getCount();
			}
			if (Tiers.fireResistance(s)) {
				fireResistance += s.getCount();
			}
		}
	}

	private static Snapshot current = new Snapshot();
	private static long countedAt = Long.MIN_VALUE;

	private CampStock() {
	}

	/** The camp's things, counted at most {@value #MAX_AGE} ticks ago. */
	public static Snapshot get(MinecraftServer server) {
		long now = server.getTickCount();
		if (now - countedAt >= MAX_AGE || now < countedAt) {
			countedAt = now;
			current = count(server);
		}
		return current;
	}

	/** Forgets the last count (a world closing), so the next world starts afresh. */
	public static void clear() {
		current = new Snapshot();
		countedAt = Long.MIN_VALUE;
	}

	private static Snapshot count(MinecraftServer server) {
		Snapshot s = new Snapshot();
		CampData data = Camp.data(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (!Camp.isCampLevel(level, data)) {
				continue;
			}
			Optional<Container> chest = SupplyChest.of(level);
			if (chest.isPresent()) {
				s.hasChest = true;
				Container container = chest.get();
				for (int i = 0; i < container.getContainerSize(); i++) {
					s.add(container.getItem(i), true);
				}
			}
			break;
		}
		for (CompanionEntity c : Companions.all()) {
			s.add(c.getMainHandItem(), false);
			Backpack bp = c.backpack();
			for (int i = 0; i < Backpack.MAX_SLOTS; i++) {
				s.add(bp.get(i), false);
			}
			if (c.friendId().role() == Role.MINER) {
				s.miners++;
				if (Tiers.bestPickaxe(c) >= Tiers.IRON) {
					s.minersWithIron++;
				}
			}
			if (c.friendId().role() == Role.WARRIOR) {
				s.warriors++;
			}
		}
		return s;
	}

	/** Every counted item and its total, for the goals display. */
	public static Map<Item, Integer> totals(Snapshot s) {
		return s.total;
	}
}
