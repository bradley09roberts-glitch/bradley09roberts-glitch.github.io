package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.civic.Families;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.expedition.ExpeditionData;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressData;
import io.github.bradley09roberts.hardcorefriends.settler.Personas;
import io.github.bradley09roberts.hardcorefriends.settler.SettlerData;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;

/**
 * The Village Chronicle: the camp's history, one dated line at a time ("Day 42, spring: Mabel and Oak were married at
 * the town hall."). Lines come from what happens: arrivals, births, weddings and children growing up, deaths (told
 * gently), buildings finished, the camp growing a stage, the feasts and funerals, the steps of Sage's plan (iron,
 * diamonds, the Nether, the stronghold, the dragon) and raids beaten off. Most of it is noticed by looking at what the
 * other parts of the mod already keep, every few seconds ({@link #poll}), so nothing elsewhere has to change: a
 * wedding is a husband or wife the families' records did not have before, a birth a child not seen before, an arrival
 * a new entry in the camp's ledger or the newcomers' records. Deaths and buildings come straight from their hooks.
 *
 * <p>The lines are also written into a real book ({@link ChronicleTask}): 100 pages a volume, a new volume when one
 * is full. Each line is given its volume when it is written, so the pages never shuffle. {@code /friends chronicle}
 * shows it all in chat for anyone.
 */
public final class Chronicle {
	/** What a book page holds: lines, and characters a line, in the game's book font (roughly). */
	static final int PAGE_LINES = 13;
	static final int LINE_CHARS = 19;
	/** A written book holds 100 pages: the title page and 99 of lines. */
	static final int MAX_PAGES = 100;
	/** The lines shown on one page of {@code /friends chronicle}. */
	static final int CHAT_PAGE = 8;
	/**
	 * Raids near the camp being watched, by raid id (not saved). Only a raid still under way is taken up: a finished one
	 * stays about for its celebration afterwards, and would otherwise be told again at every look.
	 */
	private static final Map<Integer, Raid> RAIDS = new HashMap<>();
	private static final int RAID_RANGE = 96;

	private Chronicle() {
	}

	static void clear() {
		RAIDS.clear();
	}

	// ------------------------------------------------------------------ writing

	/** Adds a dated line (today's date) to the Chronicle. Long text is cut to keep a page readable. */
	static void write(MinecraftServer server, String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		String line = text.strip();
		if (line.length() > 220) {
			line = line.substring(0, 217) + "...";
		}
		LifeData data = LifeData.get(server);
		long day = Calendar.today(server);
		int volume = data.entries.isEmpty() ? 1 : data.entries.getLast().volume();
		List<LifeData.Entry> same = new ArrayList<>();
		for (LifeData.Entry e : data.entries) {
			if (e.volume() == volume) {
				same.add(e);
			}
		}
		same.add(new LifeData.Entry(day, line, volume));
		if (pageCount(same) > MAX_PAGES) {
			volume++;
		}
		data.entries.add(new LifeData.Entry(day, line, volume));
		while (data.entries.size() > LifeData.MAX_ENTRIES) {
			data.entries.removeFirst();
			data.bookWritten = Math.max(0, data.bookWritten - 1);
		}
		data.setDirty();
	}

	// ------------------------------------------------------------------ noticing

	/**
	 * Looks at what the camp, the families, the newcomers and Sage's plan record, and writes what is new. The first
	 * time (a new world, or an old one the update came to) everything already there is taken as known, and the
	 * Chronicle opens with a line about the camp as it is.
	 */
	static void poll(MinecraftServer server) {
		CampData camp = Camp.data(server);
		if (camp.campPos().isEmpty()) {
			return;
		}
		LifeData data = LifeData.get(server);
		long day = Calendar.today(server);
		if (!data.started) {
			begin(server, data, camp, day);
			return;
		}
		stages(server, data, camp);
		structures(server, data, camp);
		milestones(server, data);
		arrivals(server, data, camp);
		people(server, data, day);
		firsts(server, data, camp);
		raids(server, camp);
	}

	private static void begin(MinecraftServer server, LifeData data, CampData camp, long day) {
		data.started = true;
		data.lastStage = camp.stage();
		data.knownCompleted.addAll(camp.completed());
		ProgressData progress = ProgressData.get(server);
		for (Milestone m : Milestone.values()) {
			if (progress.isDone(m)) {
				data.milestones.add(m.key());
			}
		}
		List<UUID> known = new ArrayList<>();
		for (FriendId id : FriendId.values()) {
			CampData.Ledger ledger = camp.ledger(id);
			if (ledger.state == CampData.LifeState.ALIVE && ledger.entityId != null) {
				data.ledgerIds.put(id.key(), ledger.entityId);
				known.add(ledger.entityId);
			}
		}
		for (SettlerData.Newcomer n : SettlerData.get(server).newcomers()) {
			data.newcomersSeen.add(n.id);
			known.add(n.id);
		}
		for (CompanionEntity c : Companions.all()) {
			known.add(c.getUUID());
			if (c.isChild()) {
				data.childrenSeen.add(c.getUUID());
			}
		}
		// Families already made before the Chronicle began are known: no wedding or birth is told twice.
		for (UUID id : known) {
			Families.get().partnerOf(server, id).ifPresent(partner -> data.marriages.add(pairKey(id, partner)));
			data.childrenSeen.addAll(Families.get().childrenOf(server, id));
		}
		data.dragons = camp.stat("dragons_defeated");
		for (ExpeditionData.Place place : ExpeditionData.get(server).places()) {
			if (place.dim().equals(Level.NETHER.identifier().toString())) {
				data.firsts.add("nether");
			} else if (place.dim().equals(Level.END.identifier().toString())) {
				data.firsts.add("end");
			}
		}
		if (data.dragons > 0) {
			data.firsts.add("end");
		}
		int people = VillagePlan.population(server);
		data.setDirty();
		write(server, "The Village Chronicle begins. The camp is a " + Camp.stageName(camp.stage()).toLowerCase(java.util.Locale.ROOT)
			+ ", home to " + people + (people == 1 ? " person." : " people."));
	}

	private static void stages(MinecraftServer server, LifeData data, CampData camp) {
		int stage = camp.stage();
		if (stage > data.lastStage && data.lastStage >= 0) {
			for (int s = data.lastStage + 1; s <= stage; s++) {
				write(server, "The camp grew into " + article(Camp.stageName(s)) + ".");
			}
		}
		if (stage != data.lastStage) {
			data.lastStage = stage;
			data.setDirty();
		}
	}

	/** The camp's own improvements (the cabin, the watchtower...). The village's buildings come from their own hook. */
	private static void structures(MinecraftServer server, LifeData data, CampData camp) {
		for (String id : camp.completed()) {
			if (data.knownCompleted.add(id)) {
				data.setDirty();
				for (Structures.Entry e : Structures.ALL) {
					if (e.id().equals(id)) {
						write(server, "The friends finished the " + e.displayName() + ".");
						break;
					}
				}
			}
		}
	}

	private static void milestones(MinecraftServer server, LifeData data) {
		ProgressData progress = ProgressData.get(server);
		for (Milestone m : Milestone.values()) {
			if (progress.isDone(m) && data.milestones.add(m.key())) {
				data.setDirty();
				String text = switch (m) {
					case IRON_AGE -> "The camp reached the iron age: iron tools for the miners and iron swords for the fighters.";
					case DIAMONDS -> "The first diamonds were found, and a diamond pickaxe made.";
					case ENCHANTING -> "The camp's enchanting table was set up among its bookshelves.";
					case NETHER_READY -> "Obsidian and a flint and steel were ready: the way to the Nether lay open.";
					case BLAZE_RODS -> "Blaze rods were brought home from the Nether.";
					case ENDER_PEARLS -> "Enough ender pearls were gathered for the journey ahead.";
					case EYES_OF_ENDER -> "Eyes of ender were made, to find the stronghold.";
					case STRONGHOLD -> "The stronghold was found.";
					case END_PORTAL -> "The End portal was opened.";
					default -> null; // settling is the camp's own stage; the dragon is told when it falls
				};
				if (text != null) {
					write(server, text);
				}
			}
		}
	}

	/** The nine joining (or joining again), newcomers asked in, and children of the camp growing up. */
	private static void arrivals(MinecraftServer server, LifeData data, CampData camp) {
		for (FriendId id : FriendId.values()) {
			CampData.Ledger ledger = camp.ledger(id);
			if (ledger.state != CampData.LifeState.ALIVE || ledger.entityId == null || ledger.entityId.equals(data.ledgerIds.get(id.key()))) {
				continue;
			}
			data.ledgerIds.put(id.key(), ledger.entityId);
			data.setDirty();
			write(server, id.displayName() + (ledger.deaths > 0 ? " joined the camp again." : " joined the camp."));
		}
		for (SettlerData.Newcomer n : SettlerData.get(server).newcomers()) {
			if (n.state != SettlerData.State.ALIVE || !data.newcomersSeen.add(n.id)) {
				continue;
			}
			data.setDirty();
			if (n.born) {
				write(server, fullName(server, n.id, n.name) + " grew up and began work as " + Personas.tradeWithArticle(n.archetype.role()) + ".");
			} else {
				write(server, n.name + " joined the camp" + (n.recruitedByName.isBlank() ? "." : ", asked in by " + n.recruitedByName + "."));
			}
		}
	}

	/**
	 * Everyone loaded on the team: their record kept (and their family as it stands, so a death is mourned by the right
	 * people), and a new child or a new husband or wife told.
	 */
	private static void people(MinecraftServer server, LifeData data, long day) {
		Families.Provider families = Families.get();
		for (CompanionEntity c : Companions.all()) {
			UUID id = c.getUUID();
			LifeData.Person p = data.personFor(id, c.displayName(), day, c.isChild());
			UUID partner = families.partnerOf(server, id).orElse(null);
			List<UUID> parents = families.parentsOf(server, id);
			List<UUID> children = families.childrenOf(server, id);
			if (!java.util.Objects.equals(partner, p.partner) || !p.parents.equals(parents) || !p.children.equals(children)) {
				p.partner = partner;
				p.parents.clear();
				p.parents.addAll(parents);
				p.children.clear();
				p.children.addAll(children);
				data.setDirty();
			}
			if (c.isChild() && data.childrenSeen.add(id)) {
				data.setDirty();
				p.born = true;
				p.joined = day;
				List<String> names = new ArrayList<>();
				for (UUID parent : parents) {
					names.add(nameOf(server, data, parent));
				}
				write(server, fullName(server, id, c.displayName()) + " was born" + (names.isEmpty() ? "." : " to " + and(names) + "."));
			}
			if (partner != null && data.marriages.add(pairKey(id, partner))) {
				data.setDirty();
				String family = families.familyName(server, id).orElse("");
				boolean hall = VillagePlan.isBuilt(server, "civic:town_hall");
				write(server, c.displayName() + " and " + nameOf(server, data, partner) + " were married" + (hall ? " at the town hall" : "")
					+ (family.isEmpty() ? "." : ", and became the " + family + " family."));
			}
		}
	}

	/** The first time anyone of the team stands in the Nether or the End, and every dragon defeated. */
	private static void firsts(MinecraftServer server, LifeData data, CampData camp) {
		List<String> nether = new ArrayList<>();
		List<String> end = new ArrayList<>();
		for (CompanionEntity c : Companions.all()) {
			if (c.level().dimension() == Level.NETHER) {
				nether.add(c.displayName());
			} else if (c.level().dimension() == Level.END) {
				end.add(c.displayName());
			}
		}
		if (!nether.isEmpty() && data.firsts.add("nether")) {
			data.setDirty();
			write(server, and(nether) + (nether.size() == 1 ? " was the first" : " were the first") + " of the camp to set foot in the Nether.");
		}
		if (!end.isEmpty() && data.firsts.add("end")) {
			data.setDirty();
			write(server, and(end) + (end.size() == 1 ? " was the first" : " were the first") + " of the camp to reach the End.");
		}
		long dragons = camp.stat("dragons_defeated");
		if (dragons > data.dragons) {
			write(server, data.dragons == 0 ? "The ender dragon was defeated! The camp's long journey reached its end."
				: "The ender dragon was defeated once more.");
			data.dragons = dragons;
			data.setDirty();
		}
	}

	/** Pillager raids on the village: told when one is beaten off (or, sadly, is not). */
	private static void raids(MinecraftServer server, CampData camp) {
		ServerLevel level = Places.campLevel(server);
		BlockPos centre = camp.campPos().orElse(null);
		if (level == null || centre == null || !level.isLoaded(centre)) {
			return;
		}
		Raid raid = level.getRaids().getNearbyRaid(centre, RAID_RANGE * RAID_RANGE);
		if (raid != null && !raid.isOver()) {
			level.getRaids().getId(raid).ifPresent(id -> RAIDS.putIfAbsent(id, raid));
		}
		for (Iterator<Map.Entry<Integer, Raid>> it = RAIDS.entrySet().iterator(); it.hasNext();) {
			Raid r = it.next().getValue();
			if (r.isVictory()) {
				write(server, "Raiders came to the village, and were driven off.");
				it.remove();
			} else if (r.isLoss()) {
				write(server, "Raiders came to the village. It was a hard day, and they could not be held off.");
				it.remove();
			} else if (r.isStopped() || r.isOver()) {
				it.remove();
			}
		}
	}

	/** A building of the village (or any library building) was finished: houses, civic buildings, shops, workplaces. */
	static void buildingFinished(ServerLevel level, String siteKey, Blueprint plan) {
		MinecraftServer server = level.getServer();
		if (!Camp.isCampLevel(level, Camp.data(server))) {
			return;
		}
		String kind = plan.kind() == null ? "" : plan.kind();
		String name = plan.name() == null ? "" : plan.name();
		if (kind.equals("house")) {
			write(server, "A new house" + (name.isBlank() ? "" : ", " + name + ",") + " went up in the village.");
		} else if (kind.startsWith("civic:")) {
			write(server, "The village's " + pretty(kind) + " was finished.");
		} else if (kind.startsWith("shop:") || kind.startsWith("workplace:") || kind.startsWith("farm:")) {
			write(server, "The " + (name.isBlank() ? pretty(kind) : name.toLowerCase(java.util.Locale.ROOT)) + " was finished.");
		} else if (kind.equals("decor:fountain") || kind.equals("decor:garden")) {
			write(server, "A " + pretty(kind) + " was made in the village.");
		}
	}

	// ------------------------------------------------------------------ the book

	/** The pages of one volume, title page first, each line dated, packed as many to a page as fit. */
	static List<Filterable<Component>> pages(List<LifeData.Entry> entries, int volume) {
		List<Filterable<Component>> pages = new ArrayList<>();
		long begun = entries.isEmpty() ? 0 : entries.getFirst().day();
		pages.add(Filterable.passThrough(Component.literal("The Village Chronicle\n\n").withStyle(ChatFormatting.BOLD)
			.append(Component.literal("Volume " + roman(volume) + "\n\n").withStyle(s -> s.withBold(false)))
			.append(Component.literal("The story of our village, as the friends wrote it down.\n\nBegun on " + Calendar.label(begun) + ".")
				.withStyle(s -> s.withBold(false)))));
		MutableComponent page = null;
		int lines = 0;
		for (LifeData.Entry e : entries) {
			int need = 1 + wrapLines(e.text()) + (lines > 0 ? 1 : 0);
			if (page != null && lines + need > PAGE_LINES) {
				pages.add(Filterable.passThrough(page));
				page = null;
				lines = 0;
				need -= 1;
			}
			if (page == null) {
				page = Component.empty();
			} else {
				page.append(Component.literal("\n\n"));
			}
			page.append(Component.literal(Calendar.label(e.day())).withStyle(ChatFormatting.BOLD));
			page.append(Component.literal("\n" + e.text()));
			lines += need;
		}
		if (page != null) {
			pages.add(Filterable.passThrough(page));
		}
		return pages.size() > MAX_PAGES ? pages.subList(0, MAX_PAGES) : pages;
	}

	/** How many pages these lines take, with the title page (the same packing as {@link #pages}). */
	static int pageCount(List<LifeData.Entry> entries) {
		int pages = 1;
		boolean open = false;
		int lines = 0;
		for (LifeData.Entry e : entries) {
			int need = 1 + wrapLines(e.text()) + (lines > 0 ? 1 : 0);
			if (open && lines + need > PAGE_LINES) {
				open = false;
				lines = 0;
				need -= 1;
			}
			if (!open) {
				pages++;
				open = true;
			}
			lines += need;
		}
		return pages;
	}

	/** Lines a piece of text wraps to in a book, roughly (whole words onto {@value #LINE_CHARS}-character lines). */
	static int wrapLines(String text) {
		int lines = 1;
		int used = 0;
		for (String word : text.split(" ")) {
			int len = word.length();
			if (used == 0) {
				used = len;
			} else if (used + 1 + len <= LINE_CHARS) {
				used += 1 + len;
			} else {
				lines++;
				used = len;
			}
			while (used > LINE_CHARS) {
				lines++;
				used -= LINE_CHARS;
			}
		}
		return lines;
	}

	/** The lines of one volume. */
	static List<LifeData.Entry> volume(LifeData data, int volume) {
		List<LifeData.Entry> list = new ArrayList<>();
		for (LifeData.Entry e : data.entries) {
			if (e.volume() == volume) {
				list.add(e);
			}
		}
		return list;
	}

	// ------------------------------------------------------------------ chat

	/** {@code /friends chronicle [page]}: the Chronicle in chat, {@value #CHAT_PAGE} lines a page, the latest by default. */
	static List<Component> show(MinecraftServer server, int page) {
		LifeData data = LifeData.get(server);
		List<Component> out = new ArrayList<>();
		int total = Math.max(1, (data.entries.size() + CHAT_PAGE - 1) / CHAT_PAGE);
		int p = page <= 0 ? total : Math.min(page, total);
		out.add(Component.literal("The Village Chronicle (page " + p + " of " + total + ")").withStyle(ChatFormatting.GOLD));
		if (data.entries.isEmpty()) {
			out.add(Component.literal("Nothing is written yet: the Chronicle begins once the camp is set up (/friends camp set).")
				.withStyle(ChatFormatting.GRAY));
			return out;
		}
		for (int i = (p - 1) * CHAT_PAGE; i < Math.min(data.entries.size(), p * CHAT_PAGE); i++) {
			LifeData.Entry e = data.entries.get(i);
			out.add(Component.literal(Calendar.label(e.day()) + ": ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(e.text()).withStyle(ChatFormatting.WHITE)));
		}
		if (total > 1) {
			out.add(Component.literal("/friends chronicle <page> for other pages (1 is the oldest).").withStyle(ChatFormatting.GRAY));
		}
		out.add(Component.literal(whereIsTheBook(server, data)).withStyle(ChatFormatting.GRAY));
		return out;
	}

	/** Where the book is kept, in a sentence. */
	static String whereIsTheBook(MinecraftServer server, LifeData data) {
		if (!io.github.bradley09roberts.hardcorefriends.config.FriendsConfig.get().chronicleBook) {
			return "The friends keep the Chronicle in their heads only (chronicleBook is off in the settings).";
		}
		BlockPos pos = data.bookPos;
		String volume = "Volume " + roman(data.volume);
		if (data.bookPlace.equals("lectern") && pos != null) {
			return volume + " of the book lies on the lectern in the town hall, at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
				+ (data.volume > 1 ? "; earlier volumes are in the supply chest." : ".");
		}
		if (data.bookPlace.equals("chest")) {
			return volume + " of the book is kept in the camp's supply chest" + (data.volume > 1 ? ", with the earlier ones." : ".");
		}
		return "The book itself is not written yet: the keeper needs a book and quill (a book, an ink sac and a feather) "
			+ "from the supply chest.";
	}

	// ------------------------------------------------------------------ words

	/** "Fern", "Fern and Oak", "Fern, Oak and Flint". */
	static String and(List<String> names) {
		if (names.isEmpty()) {
			return "";
		}
		if (names.size() == 1) {
			return names.getFirst();
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
	}

	/** A person's name from the loaded world, the village's records, or "someone". */
	static String nameOf(MinecraftServer server, LifeData data, @Nullable UUID id) {
		if (id == null) {
			return "someone";
		}
		for (CompanionEntity c : Companions.everyone()) {
			if (c.getUUID().equals(id)) {
				return c.displayName();
			}
		}
		Optional<LifeData.Person> p = data.person(id);
		if (p.isPresent()) {
			return p.get().name;
		}
		return SettlerData.get(server).newcomer(id).map(n -> n.name).orElse("someone");
	}

	/** "Pip Hart": a first name and the family name, if the family has one. */
	static String fullName(MinecraftServer server, UUID id, String first) {
		String family = Families.get().familyName(server, id).orElse("");
		return family.isBlank() || first.endsWith(" " + family) ? first : first + " " + family;
	}

	static String pairKey(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	/** "a Village", "an Ice hut": the stage name with its article, the first letter kept. */
	private static String article(String noun) {
		String lower = noun.toLowerCase(java.util.Locale.ROOT);
		return ("aeiou".indexOf(lower.charAt(0)) >= 0 ? "an " : "a ") + lower;
	}

	/** "civic:town_hall" as "town hall". */
	static String pretty(String kind) {
		int colon = kind.indexOf(':');
		return (colon >= 0 ? kind.substring(colon + 1) : kind).replace('_', ' ');
	}

	/** 1 to "I", 4 to "IV"... */
	static String roman(int n) {
		int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder sb = new StringBuilder();
		int left = Math.max(1, n);
		for (int i = 0; i < values.length; i++) {
			while (left >= values[i]) {
				sb.append(numerals[i]);
				left -= values[i];
			}
		}
		return sb.toString();
	}
}
