package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/**
 * What the world remembers about the people of the camp and how they get on: everyone on the team (the named friends,
 * newcomers and the children born here, alive or not) with their family name, parents and partner, and every pair's
 * friendship and romance, courting, engagement, wedding day and baby on the way. People are known by entity UUID, which
 * stays the same through dimension changes; a named friend who dies and is recruited again is a new person. Stored in
 * {@code data/hardcorefriends_people.dat}.
 */
public final class PeopleData extends SavedData {
	public static final Codec<PeopleData> CODEC = CompoundTag.CODEC.xmap(PeopleData::fromTag, PeopleData::toTag);
	public static final SavedDataType<PeopleData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "people"), PeopleData::new, CODEC, null);

	/** Friendship and romance run from 0 to this. */
	public static final double MAX = 100;
	/** Where two people who have just met start. */
	public static final double FIRST_MEETING = 10;

	/** Whether a person is still about. */
	public enum State {
		ALIVE,
		/** Fell for good (Hardcore). */
		DEAD,
		/** Left the team (dismissed). */
		GONE
	}

	/** Where two people stand romantically. */
	public enum Status {
		NONE,
		/** Going out together. */
		DATING,
		/** Promised to marry; the wedding day is set. */
		ENGAGED,
		MARRIED,
		/** Were together once, and called it off. */
		PARTED;

		/** Going out, engaged or married. */
		public boolean together() {
			return this == DATING || this == ENGAGED || this == MARRIED;
		}
	}

	/** One person of the camp. */
	public static final class Person {
		public final UUID id;
		public String name;
		/** Their family name, "" until given one. */
		public String family = "";
		public FriendId archetype;
		public int colour;
		/** One of the nine named friends. */
		public boolean named;
		/** Still a child. */
		public boolean child;
		/** Born in the camp: the in-game day they arrived; -1 for anyone who joined grown up. */
		public long bornDay = -1;
		/** The in-game day they grew up; -1 if they have not (or arrived grown up). */
		public long grownDay = -1;
		public final List<UUID> parents = new ArrayList<>(2);
		/** The parent a child lives with (the household they belong to), or null. */
		public @Nullable UUID homeParent;
		/** Their sweetheart, fiancé or spouse (see the pair's {@link Bond#status}), or null. */
		public @Nullable UUID partner;
		public State state = State.ALIVE;
		/** Overworld clock time they died or left; 0 while alive. */
		public long endedAt;
		/** Overworld clock time until which they mourn a child (no new baby meanwhile). */
		public long mournUntil;

		Person(UUID id, String name, FriendId archetype, int colour) {
			this.id = id;
			this.name = name;
			this.archetype = archetype;
			this.colour = colour;
		}

		public boolean alive() {
			return state == State.ALIVE;
		}

		/** "Fern Hart", or just "Fern" before a family name is given. */
		public String fullName() {
			return family.isEmpty() ? name : name + " " + family;
		}
	}

	/** How two people get on. The pair is kept in UUID order. */
	public static final class Bond {
		public final UUID a;
		public final UUID b;
		public double friendship = FIRST_MEETING;
		public double romance;
		public Status status = Status.NONE;
		/** Overworld clock time the status began. */
		public long since;
		/** Dates they have been on. */
		public int dates;
		/** The in-game day of their last date; -1 for none. */
		public long lastDateDay = -1;
		/** Their becoming good friends has been said. */
		public boolean friendsSaid;
		/** Overworld clock time of their last quarrel. */
		public long lastQuarrel = Long.MIN_VALUE / 2;
		/** Engaged: the in-game day the wedding is planned for; -1 otherwise. */
		public long weddingDay = -1;
		/** Married: a baby is due at this overworld clock time; -1 when none is on the way. */
		public long babyDue = -1;
		/** The in-game day their last baby arrived; -1 for none. */
		public long lastBabyDay = -1;

		Bond(UUID a, UUID b) {
			this.a = a;
			this.b = b;
		}

		/** The other one of the pair. */
		public UUID other(UUID one) {
			return one.equals(a) ? b : a;
		}

		public boolean has(UUID one) {
			return one.equals(a) || one.equals(b);
		}
	}

	private final Map<UUID, Person> people = new LinkedHashMap<>();
	private final Map<String, Bond> bonds = new LinkedHashMap<>();

	public PeopleData() {
	}

	public static PeopleData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ----------------------------------------------------------------- people

	public Optional<Person> person(UUID id) {
		return Optional.ofNullable(people.get(id));
	}

	public Collection<Person> people() {
		return people.values();
	}

	/**
	 * This friend's record, made the first time they are seen and kept up to date with their name, trade, colour and
	 * age (a newcomer is renamed when they join; a child grows up). A family name is given to anyone without one.
	 */
	public Person personFor(CompanionEntity c, RandomSource random) {
		Person p = people.get(c.getUUID());
		boolean changed = false;
		if (p == null) {
			p = new Person(c.getUUID(), c.displayName(), c.friendId(), c.nameColour());
			people.put(p.id, p);
			changed = true;
		}
		if (!p.name.equals(c.displayName()) || p.archetype != c.friendId() || p.colour != c.nameColour()) {
			p.name = c.displayName();
			p.archetype = c.friendId();
			p.colour = c.nameColour();
			changed = true;
		}
		boolean named = !c.isSettler();
		if (p.named != named || p.child != c.isChild()) {
			p.named = named;
			p.child = c.isChild();
			changed = true;
		}
		if (p.state != State.ALIVE && c.isAlive()) {
			p.state = State.ALIVE; // the same person back again (an old save, a world copied back)
			p.endedAt = 0;
			changed = true;
		}
		if (p.family.isEmpty()) {
			p.family = Names.family(random, familyNamesInUse());
			changed = true;
		}
		if (changed) {
			setDirty();
		}
		return p;
	}

	/** Makes a record for a baby before they are first seen. */
	Person addBaby(UUID id, String name, String family, FriendId archetype, int colour, List<UUID> parents, long day) {
		Person p = new Person(id, name, archetype, colour);
		p.family = family;
		p.child = true;
		p.bornDay = day;
		p.parents.addAll(parents);
		p.homeParent = parents.isEmpty() ? null : parents.getFirst();
		people.put(id, p);
		setDirty();
		return p;
	}

	/** Family names (lower case) of the living. */
	Set<String> familyNamesInUse() {
		Set<String> names = new HashSet<>();
		for (Person p : people.values()) {
			if (p.alive() && !p.family.isEmpty()) {
				names.add(p.family.toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	/** First names (lower case) of the living people of the camp. */
	Set<String> firstNamesInUse() {
		Set<String> names = new HashSet<>();
		for (Person p : people.values()) {
			if (p.alive()) {
				names.add(p.name.toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	/** Living children (people still young) of the camp. */
	public List<Person> livingChildren() {
		List<Person> list = new ArrayList<>();
		for (Person p : people.values()) {
			if (p.alive() && p.child) {
				list.add(p);
			}
		}
		return list;
	}

	public List<Person> childrenOf(UUID parent) {
		List<Person> list = new ArrayList<>();
		for (Person p : people.values()) {
			if (p.parents.contains(parent)) {
				list.add(p);
			}
		}
		return list;
	}

	/** The married partner of this person, if they have one and are both alive. */
	public Optional<UUID> spouse(UUID id) {
		Person p = people.get(id);
		if (p == null || p.partner == null) {
			return Optional.empty();
		}
		Bond bond = bondIf(id, p.partner);
		Person other = people.get(p.partner);
		return bond != null && bond.status == Status.MARRIED && other != null && other.alive() && p.alive()
			? Optional.of(p.partner) : Optional.empty();
	}

	/** The bond with their current partner (going out, engaged or married), if any. */
	public @Nullable Bond partnerBond(UUID id) {
		Person p = people.get(id);
		if (p == null || p.partner == null) {
			return null;
		}
		Bond bond = bondIf(id, p.partner);
		return bond != null && bond.status.together() ? bond : null;
	}

	/**
	 * Family: one is the other's parent or grandparent, they share a parent, or one is married to the other's parent.
	 * Family never court each other.
	 */
	public boolean related(UUID a, UUID b) {
		Person pa = people.get(a);
		Person pb = people.get(b);
		if (pa == null || pb == null) {
			return false;
		}
		if (pa.parents.contains(b) || pb.parents.contains(a)) {
			return true;
		}
		for (UUID parent : pa.parents) {
			if (pb.parents.contains(parent)) {
				return true;
			}
			Person grand = people.get(parent);
			if (grand != null && (grand.parents.contains(b) || b.equals(grand.partner))) {
				return true;
			}
		}
		for (UUID parent : pb.parents) {
			Person grand = people.get(parent);
			if (grand != null && (grand.parents.contains(a) || a.equals(grand.partner))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Who lives together with this person: a grown-up, their spouse and the children still young who live with either
	 * of them; a child, the household of the parent they live with (or just themself with no living parent).
	 */
	public Set<UUID> household(UUID id) {
		Person p = people.get(id);
		Set<UUID> home = new LinkedHashSet<>();
		if (p == null) {
			home.add(id);
			return home;
		}
		if (p.child) {
			Person parent = p.homeParent == null ? null : people.get(p.homeParent);
			if (parent != null && parent.alive()) {
				return household(parent.id);
			}
			home.add(id);
			return home;
		}
		home.add(id);
		Optional<UUID> spouse = spouse(id);
		spouse.ifPresent(home::add);
		for (Person child : people.values()) {
			if (child.alive() && child.child && child.homeParent != null
				&& (child.homeParent.equals(id) || spouse.isPresent() && child.homeParent.equals(spouse.get()))) {
				home.add(child.id);
			}
		}
		return home;
	}

	// ------------------------------------------------------------------ bonds

	static String key(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "/" + b : b + "/" + a;
	}

	/** How these two get on, made on first meeting. */
	public Bond bond(UUID x, UUID y) {
		return bonds.computeIfAbsent(key(x, y), k -> {
			setDirty();
			return x.compareTo(y) < 0 ? new Bond(x, y) : new Bond(y, x);
		});
	}

	/** How these two get on, if they have ever met. */
	public @Nullable Bond bondIf(UUID x, UUID y) {
		return bonds.get(key(x, y));
	}

	public Collection<Bond> bonds() {
		return bonds.values();
	}

	/** Every bond this person has, closest first. */
	public List<Bond> bondsOf(UUID id) {
		List<Bond> list = new ArrayList<>();
		for (Bond bond : bonds.values()) {
			if (bond.has(id)) {
				list.add(bond);
			}
		}
		list.sort((x, y) -> Double.compare(y.friendship + y.romance, x.friendship + x.romance));
		return list;
	}

	/**
	 * Forgets the bonds a person who is gone no longer needs: everything but their partner, so the records stay small.
	 * Their family links (parents, children, partner) stay.
	 */
	void pruneBondsOf(UUID id) {
		Person p = people.get(id);
		UUID keep = p == null ? null : p.partner;
		bonds.values().removeIf(bond -> bond.has(id) && (keep == null || !bond.has(keep)));
		setDirty();
	}

	// ------------------------------------------------------------ persistence

	private static PeopleData fromTag(CompoundTag tag) {
		PeopleData data = new PeopleData();
		for (Tag t : tag.getListOrEmpty("people")) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			UUID id = uuid(c.getStringOr("id", ""));
			if (id == null) {
				continue;
			}
			FriendId archetype = FriendId.byKey(c.getStringOr("archetype", "rowan")).orElse(FriendId.ROWAN);
			Person p = new Person(id, c.getStringOr("name", "Someone"), archetype, c.getIntOr("colour", 0xFFFFFF));
			p.family = c.getStringOr("family", "");
			p.named = c.getBooleanOr("named", false);
			p.child = c.getBooleanOr("child", false);
			p.bornDay = c.getLongOr("born", -1L);
			p.grownDay = c.getLongOr("grown", -1L);
			for (Tag parent : c.getListOrEmpty("parents")) {
				parent.asString().map(PeopleData::uuid).ifPresent(p.parents::add);
			}
			p.homeParent = uuid(c.getStringOr("home", ""));
			p.partner = uuid(c.getStringOr("partner", ""));
			p.state = state(c.getStringOr("state", "ALIVE"));
			p.endedAt = c.getLongOr("ended", 0L);
			p.mournUntil = c.getLongOr("mourn", 0L);
			data.people.put(id, p);
		}
		for (Tag t : tag.getListOrEmpty("bonds")) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			UUID a = uuid(c.getStringOr("a", ""));
			UUID b = uuid(c.getStringOr("b", ""));
			if (a == null || b == null || a.equals(b)) {
				continue;
			}
			Bond bond = a.compareTo(b) < 0 ? new Bond(a, b) : new Bond(b, a);
			bond.friendship = clamp(c.getDoubleOr("friendship", FIRST_MEETING));
			bond.romance = clamp(c.getDoubleOr("romance", 0));
			bond.status = status(c.getStringOr("status", "NONE"));
			bond.since = c.getLongOr("since", 0L);
			bond.dates = c.getIntOr("dates", 0);
			bond.lastDateDay = c.getLongOr("lastDate", -1L);
			bond.friendsSaid = c.getBooleanOr("friendsSaid", false);
			bond.lastQuarrel = c.getLongOr("quarrel", Long.MIN_VALUE / 2);
			bond.weddingDay = c.getLongOr("wedding", -1L);
			bond.babyDue = c.getLongOr("due", -1L);
			bond.lastBabyDay = c.getLongOr("lastBaby", -1L);
			data.bonds.put(key(a, b), bond);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Person p : people.values()) {
			CompoundTag c = new CompoundTag();
			c.putString("id", p.id.toString());
			c.putString("name", p.name);
			c.putString("family", p.family);
			c.putString("archetype", p.archetype.key());
			c.putInt("colour", p.colour);
			c.putBoolean("named", p.named);
			c.putBoolean("child", p.child);
			c.putLong("born", p.bornDay);
			c.putLong("grown", p.grownDay);
			ListTag parents = new ListTag();
			for (UUID parent : p.parents) {
				parents.add(net.minecraft.nbt.StringTag.valueOf(parent.toString()));
			}
			c.put("parents", parents);
			if (p.homeParent != null) {
				c.putString("home", p.homeParent.toString());
			}
			if (p.partner != null) {
				c.putString("partner", p.partner.toString());
			}
			c.putString("state", p.state.name());
			c.putLong("ended", p.endedAt);
			c.putLong("mourn", p.mournUntil);
			list.add(c);
		}
		tag.put("people", list);
		ListTag bondList = new ListTag();
		for (Bond bond : bonds.values()) {
			CompoundTag c = new CompoundTag();
			c.putString("a", bond.a.toString());
			c.putString("b", bond.b.toString());
			c.putDouble("friendship", bond.friendship);
			c.putDouble("romance", bond.romance);
			c.putString("status", bond.status.name());
			c.putLong("since", bond.since);
			c.putInt("dates", bond.dates);
			c.putLong("lastDate", bond.lastDateDay);
			c.putBoolean("friendsSaid", bond.friendsSaid);
			c.putLong("quarrel", bond.lastQuarrel);
			c.putLong("wedding", bond.weddingDay);
			c.putLong("due", bond.babyDue);
			c.putLong("lastBaby", bond.lastBabyDay);
			bondList.add(c);
		}
		tag.put("bonds", bondList);
		return tag;
	}

	static double clamp(double v) {
		return Double.isFinite(v) ? Math.clamp(v, 0.0, MAX) : 0.0;
	}

	private static State state(String s) {
		try {
			return State.valueOf(s);
		} catch (IllegalArgumentException e) {
			return State.ALIVE;
		}
	}

	private static Status status(String s) {
		try {
			return Status.valueOf(s);
		} catch (IllegalArgumentException e) {
			return Status.NONE;
		}
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
