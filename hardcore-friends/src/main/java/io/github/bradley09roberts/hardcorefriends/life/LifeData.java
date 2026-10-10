package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * What the world remembers of village life: the people (when each joined or was born, their last birthday, and their
 * family as last seen, so a death can be mourned by the right people even after the people package has let them go),
 * the cemetery and its graves, the funerals still to hold, who is mourning whom, the Village Chronicle's entries and
 * where its book is kept, the temporary blocks to take down again (festival lights, a musician's note block), and what
 * the Chronicle has already noticed (stages, buildings, milestones, arrivals, weddings, births, firsts). Stored in
 * {@code data/hardcorefriends_life.dat}.
 */
public final class LifeData extends SavedData {
	public static final Codec<LifeData> CODEC = CompoundTag.CODEC.xmap(LifeData::fromTag, LifeData::toTag);
	public static final SavedDataType<LifeData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "life"), LifeData::new, CODEC, null);

	/** The most Chronicle entries kept (about forty volumes); beyond it the oldest are let go. */
	static final int MAX_ENTRIES = 5000;
	/** The most temporary blocks remembered at once. */
	static final int MAX_TEMPS = 64;
	/** The most graves remembered. */
	static final int MAX_GRAVES = 400;

	/** One person of the village, as village life knows them. */
	static final class Person {
		final UUID id;
		String name;
		/** The day (overworld clock days) they joined the camp, or were born. */
		long joined;
		boolean born;
		/** The last day their birthday was kept, -1 if never. */
		long lastBirthday = -1;
		/** Left for good (died or dismissed). */
		boolean gone;
		/** Their husband or wife, parents and children as last seen (refreshed while they are loaded). */
		@Nullable UUID partner;
		final List<UUID> parents = new ArrayList<>(2);
		final List<UUID> children = new ArrayList<>();

		Person(UUID id, String name, long joined, boolean born) {
			this.id = id;
			this.name = name;
			this.joined = joined;
			this.born = born;
		}
	}

	/** A grave at the cemetery (or one still to be made). */
	static final class Grave {
		final int number;
		final UUID id;
		final String name;
		final String fullName;
		final long died;
		/** Partner, parents and children of the one buried, as they were: they visit most. */
		final Set<UUID> family = new LinkedHashSet<>();
		/** Where its headstone is (null until a spot is found) and which way its sign faces (2D direction value). */
		@Nullable BlockPos pos;
		String dimension = "";
		int facing;
		boolean made;
		/** Failed attempts at making it (no spot, no stone, a spot nobody could walk to), so a hopeless one is given up. */
		int tries;
		/** Walks to its spot that came to nothing (not saved): after two, the spot is let go and another chosen. */
		int walkFails;
		/** The day its last attempt failed (not saved): for the rest of that day the graves after it go first. */
		long failedDay = -1;

		Grave(int number, UUID id, String name, String fullName, long died) {
			this.number = number;
			this.id = id;
			this.name = name;
			this.fullName = fullName;
			this.died = died;
		}

		/** The camp site key that reserves the grave's ground, so nothing is built over it. */
		String siteKey() {
			return VillageLife.GRAVE_SITES + number;
		}
	}

	/** A funeral still to hold, for one or more people who died the same day. */
	static final class Funeral {
		final List<UUID> ids = new ArrayList<>();
		final List<String> names = new ArrayList<>();
		/** The day it is held (in the evening). */
		long day;
		int tries;

		Funeral(long day) {
			this.day = day;
		}
	}

	/** One line of the Chronicle: the day, the words, and which volume of the book it belongs to. */
	record Entry(long day, String text, int volume) {
	}

	/** A block put up for an occasion, to be taken down again (lights until the morning, a note block after the tune). */
	static final class Temp {
		final String dimension;
		final BlockPos pos;
		final Block block;
		/** Game time from which it may be taken down. */
		final long until;
		int fails;

		Temp(String dimension, BlockPos pos, Block block, long until) {
			this.dimension = dimension;
			this.pos = pos.immutable();
			this.block = block;
			this.until = until;
		}
	}

	/** Someone mourning: for whom, until when (overworld clock), and whether it is family (deeper and longer). */
	record Mourn(String name, long until, boolean deep) {
	}

	// people
	final Map<UUID, Person> people = new LinkedHashMap<>();
	final Map<UUID, Mourn> mourning = new HashMap<>();

	// the cemetery
	final List<Grave> graves = new ArrayList<>();
	@Nullable BlockPos cemetery;
	String cemeteryDimension = "";
	/** 2D direction value from the cemetery towards the camp (graves face the camp). */
	int cemeteryFacing;
	int nextCell;
	int nextGrave = 1;
	final List<Funeral> funerals = new ArrayList<>();

	// the Chronicle
	final List<Entry> entries = new ArrayList<>();
	/** How many entries the physical book holds (written up to). */
	int bookWritten;
	/** The volume being written. */
	int volume = 1;
	/** Where the current volume is kept: "lectern", "chest" or "" (not made yet, or lost). */
	String bookPlace = "";
	@Nullable BlockPos bookPos;
	String bookDimension = "";

	// temporary blocks
	final List<Temp> temps = new ArrayList<>();

	// what the Chronicle has noticed
	boolean started;
	int lastStage = -1;
	final Set<String> knownCompleted = new HashSet<>();
	final Set<String> milestones = new HashSet<>();
	final Map<String, UUID> ledgerIds = new HashMap<>();
	final Set<UUID> newcomersSeen = new HashSet<>();
	final Set<String> marriages = new HashSet<>();
	final Set<UUID> childrenSeen = new HashSet<>();
	final Set<String> firsts = new HashSet<>();
	long dragons;

	// the calendar's own memory
	/** The camp's crops harvested when this autumn began, and in which year (for the harvest festival). */
	long harvestBase = -1;
	long harvestYear = -1;
	/** Days on which an occasion was announced or held (kept short). */
	final Set<String> done = new LinkedHashSet<>();

	public LifeData() {
	}

	public static LifeData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ----------------------------------------------------------------- people

	Optional<Person> person(UUID id) {
		return Optional.ofNullable(people.get(id));
	}

	/** The record for this person, made on first sight (joining today, or born today for a child). */
	Person personFor(UUID id, String name, long today, boolean born) {
		Person p = people.get(id);
		if (p == null) {
			p = new Person(id, name, today, born);
			people.put(id, p);
			setDirty();
		} else if (!p.name.equals(name)) {
			p.name = name;
			setDirty();
		}
		return p;
	}

	// -------------------------------------------------------------- occasions

	/** True (once) the first time an occasion key is marked: "announce:42", "feast:42"... */
	boolean markDone(String key) {
		if (!done.add(key)) {
			return false;
		}
		while (done.size() > 200) {
			done.remove(done.iterator().next());
		}
		setDirty();
		return true;
	}

	boolean isDone(String key) {
		return done.contains(key);
	}

	// ------------------------------------------------------------------ temps

	void addTemp(String dimension, BlockPos pos, Block block, long until) {
		temps.removeIf(t -> t.dimension.equals(dimension) && t.pos.equals(pos));
		if (temps.size() >= MAX_TEMPS) {
			temps.removeFirst();
		}
		temps.add(new Temp(dimension, pos, block, until));
		setDirty();
	}

	void removeTemp(Temp t) {
		if (temps.remove(t)) {
			setDirty();
		}
	}

	// --------------------------------------------------------------- persistence

	private static LifeData fromTag(CompoundTag tag) {
		LifeData d = new LifeData();
		for (Tag t : tag.getListOrEmpty("people")) {
			if (t instanceof CompoundTag p) {
				UUID id = uuid(p.getStringOr("id", ""));
				if (id == null) {
					continue;
				}
				Person person = new Person(id, p.getStringOr("name", "Someone"), p.getLongOr("joined", 0L), p.getBooleanOr("born", false));
				person.lastBirthday = p.getLongOr("birthday", -1L);
				person.gone = p.getBooleanOr("gone", false);
				person.partner = uuid(p.getStringOr("partner", ""));
				readIds(p, "parents", person.parents);
				readIds(p, "children", person.children);
				d.people.put(id, person);
			}
		}
		CompoundTag mourn = tag.getCompoundOrEmpty("mourning");
		for (String key : mourn.keySet()) {
			UUID id = uuid(key);
			CompoundTag m = mourn.getCompoundOrEmpty(key);
			if (id != null) {
				d.mourning.put(id, new Mourn(m.getStringOr("name", "a friend"), m.getLongOr("until", 0L), m.getBooleanOr("deep", false)));
			}
		}
		for (Tag t : tag.getListOrEmpty("graves")) {
			if (t instanceof CompoundTag g) {
				UUID id = uuid(g.getStringOr("id", ""));
				if (id == null) {
					continue;
				}
				Grave grave = new Grave(g.getIntOr("number", 0), id, g.getStringOr("name", "A friend"), g.getStringOr("full", ""),
					g.getLongOr("died", 0L));
				g.getLong("pos").ifPresent(l -> grave.pos = BlockPos.of(l));
				grave.dimension = g.getStringOr("dim", "");
				grave.facing = g.getIntOr("facing", 0);
				grave.made = g.getBooleanOr("made", false);
				grave.tries = g.getIntOr("tries", 0);
				List<UUID> family = new ArrayList<>();
				readIds(g, "family", family);
				grave.family.addAll(family);
				d.graves.add(grave);
			}
		}
		tag.getLong("cemetery").ifPresent(l -> d.cemetery = BlockPos.of(l));
		d.cemeteryDimension = tag.getStringOr("cemeteryDim", "");
		d.cemeteryFacing = tag.getIntOr("cemeteryFacing", 0);
		d.nextCell = tag.getIntOr("nextCell", 0);
		d.nextGrave = Math.max(1, tag.getIntOr("nextGrave", d.graves.size() + 1));
		for (Tag t : tag.getListOrEmpty("funerals")) {
			if (t instanceof CompoundTag f) {
				Funeral funeral = new Funeral(f.getLongOr("day", 0L));
				funeral.tries = f.getIntOr("tries", 0);
				readIds(f, "ids", funeral.ids);
				for (Tag n : f.getListOrEmpty("names")) {
					n.asString().ifPresent(funeral.names::add);
				}
				if (!funeral.ids.isEmpty() && funeral.ids.size() == funeral.names.size()) {
					d.funerals.add(funeral);
				}
			}
		}
		for (Tag t : tag.getListOrEmpty("entries")) {
			if (t instanceof CompoundTag e) {
				d.entries.add(new Entry(e.getLongOr("day", 0L), e.getStringOr("text", ""), Math.max(1, e.getIntOr("vol", 1))));
			}
		}
		d.bookWritten = Math.clamp(tag.getIntOr("bookWritten", 0), 0, d.entries.size());
		d.volume = Math.max(1, tag.getIntOr("volume", 1));
		d.bookPlace = tag.getStringOr("bookPlace", "");
		tag.getLong("bookPos").ifPresent(l -> d.bookPos = BlockPos.of(l));
		d.bookDimension = tag.getStringOr("bookDim", "");
		for (Tag t : tag.getListOrEmpty("temps")) {
			if (t instanceof CompoundTag p) {
				Identifier id = Identifier.tryParse(p.getStringOr("block", ""));
				Block block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.AIR);
				if (block != Blocks.AIR) {
					d.temps.add(new Temp(p.getStringOr("dim", ""), BlockPos.of(p.getLongOr("pos", 0L)), block, p.getLongOr("until", 0L)));
				}
			}
		}
		d.started = tag.getBooleanOr("started", false);
		d.lastStage = tag.getIntOr("lastStage", -1);
		readStrings(tag, "completed", d.knownCompleted);
		readStrings(tag, "milestones", d.milestones);
		CompoundTag ledger = tag.getCompoundOrEmpty("ledger");
		for (String key : ledger.keySet()) {
			UUID id = uuid(ledger.getStringOr(key, ""));
			if (id != null) {
				d.ledgerIds.put(key, id);
			}
		}
		List<UUID> ids = new ArrayList<>();
		readIds(tag, "newcomers", ids);
		d.newcomersSeen.addAll(ids);
		ids.clear();
		readIds(tag, "children", ids);
		d.childrenSeen.addAll(ids);
		readStrings(tag, "marriages", d.marriages);
		readStrings(tag, "firsts", d.firsts);
		d.dragons = tag.getLongOr("dragons", 0L);
		d.harvestBase = tag.getLongOr("harvestBase", -1L);
		d.harvestYear = tag.getLongOr("harvestYear", -1L);
		readStrings(tag, "done", d.done);
		return d;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag peopleTag = new ListTag();
		for (Person p : people.values()) {
			CompoundTag t = new CompoundTag();
			t.putString("id", p.id.toString());
			t.putString("name", p.name);
			t.putLong("joined", p.joined);
			t.putBoolean("born", p.born);
			t.putLong("birthday", p.lastBirthday);
			t.putBoolean("gone", p.gone);
			if (p.partner != null) {
				t.putString("partner", p.partner.toString());
			}
			writeIds(t, "parents", p.parents);
			writeIds(t, "children", p.children);
			peopleTag.add(t);
		}
		tag.put("people", peopleTag);
		CompoundTag mourn = new CompoundTag();
		mourning.forEach((id, m) -> {
			CompoundTag t = new CompoundTag();
			t.putString("name", m.name());
			t.putLong("until", m.until());
			t.putBoolean("deep", m.deep());
			mourn.put(id.toString(), t);
		});
		tag.put("mourning", mourn);
		ListTag gravesTag = new ListTag();
		for (Grave g : graves) {
			CompoundTag t = new CompoundTag();
			t.putInt("number", g.number);
			t.putString("id", g.id.toString());
			t.putString("name", g.name);
			t.putString("full", g.fullName);
			t.putLong("died", g.died);
			if (g.pos != null) {
				t.putLong("pos", g.pos.asLong());
			}
			t.putString("dim", g.dimension);
			t.putInt("facing", g.facing);
			t.putBoolean("made", g.made);
			t.putInt("tries", g.tries);
			writeIds(t, "family", g.family);
			gravesTag.add(t);
		}
		tag.put("graves", gravesTag);
		if (cemetery != null) {
			tag.putLong("cemetery", cemetery.asLong());
		}
		tag.putString("cemeteryDim", cemeteryDimension);
		tag.putInt("cemeteryFacing", cemeteryFacing);
		tag.putInt("nextCell", nextCell);
		tag.putInt("nextGrave", nextGrave);
		ListTag funeralsTag = new ListTag();
		for (Funeral f : funerals) {
			CompoundTag t = new CompoundTag();
			t.putLong("day", f.day);
			t.putInt("tries", f.tries);
			writeIds(t, "ids", f.ids);
			ListTag names = new ListTag();
			for (String n : f.names) {
				names.add(StringTag.valueOf(n));
			}
			t.put("names", names);
			funeralsTag.add(t);
		}
		tag.put("funerals", funeralsTag);
		ListTag entriesTag = new ListTag();
		for (Entry e : entries) {
			CompoundTag t = new CompoundTag();
			t.putLong("day", e.day());
			t.putString("text", e.text());
			t.putInt("vol", e.volume());
			entriesTag.add(t);
		}
		tag.put("entries", entriesTag);
		tag.putInt("bookWritten", bookWritten);
		tag.putInt("volume", volume);
		tag.putString("bookPlace", bookPlace);
		if (bookPos != null) {
			tag.putLong("bookPos", bookPos.asLong());
		}
		tag.putString("bookDim", bookDimension);
		ListTag tempsTag = new ListTag();
		for (Temp t : temps) {
			CompoundTag p = new CompoundTag();
			p.putString("dim", t.dimension);
			p.putLong("pos", t.pos.asLong());
			p.putString("block", BuiltInRegistries.BLOCK.getKey(t.block).toString());
			p.putLong("until", t.until);
			tempsTag.add(p);
		}
		tag.put("temps", tempsTag);
		tag.putBoolean("started", started);
		tag.putInt("lastStage", lastStage);
		writeStrings(tag, "completed", knownCompleted);
		writeStrings(tag, "milestones", milestones);
		CompoundTag ledger = new CompoundTag();
		ledgerIds.forEach((key, id) -> ledger.putString(key, id.toString()));
		tag.put("ledger", ledger);
		writeIds(tag, "newcomers", newcomersSeen);
		writeIds(tag, "children", childrenSeen);
		writeStrings(tag, "marriages", marriages);
		writeStrings(tag, "firsts", firsts);
		tag.putLong("dragons", dragons);
		tag.putLong("harvestBase", harvestBase);
		tag.putLong("harvestYear", harvestYear);
		writeStrings(tag, "done", done);
		return tag;
	}

	private static void readIds(CompoundTag tag, String key, List<UUID> into) {
		for (Tag t : tag.getListOrEmpty(key)) {
			t.asString().map(LifeData::uuid).ifPresent(into::add);
		}
	}

	private static void writeIds(CompoundTag tag, String key, Iterable<UUID> ids) {
		ListTag list = new ListTag();
		for (UUID id : ids) {
			list.add(StringTag.valueOf(id.toString()));
		}
		tag.put(key, list);
	}

	private static void readStrings(CompoundTag tag, String key, Set<String> into) {
		for (Tag t : tag.getListOrEmpty(key)) {
			t.asString().ifPresent(into::add);
		}
	}

	private static void writeStrings(CompoundTag tag, String key, Iterable<String> values) {
		ListTag list = new ListTag();
		for (String s : values) {
			list.add(StringTag.valueOf(s));
		}
		tag.put(key, list);
	}

	static @Nullable UUID uuid(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
