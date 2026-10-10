package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * What the world remembers about the camp's pets and maps. Pets are known by entity UUID (the same through chunk
 * loading and dimension changes) with their owner, kind, name and where they were last seen, so {@code /friends pets}
 * can answer while a pet is unloaded. Maps are known by their map number, with what they show, who is drawing or
 * carrying them and where they hang. Stored in {@code data/hardcorefriends_pets.dat}.
 */
public final class PetsData extends SavedData {
	public static final Codec<PetsData> CODEC = CompoundTag.CODEC.xmap(PetsData::fromTag, PetsData::toTag);
	public static final SavedDataType<PetsData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "pets"), PetsData::new, CODEC, null);

	/** One pet of the camp. */
	public static final class Pet {
		public final UUID id;
		public final PetKind kind;
		public String name;
		/** Who it belongs to (a friend, newcomer or child, by entity UUID), or null for a pet of the whole camp. */
		public @Nullable UUID owner;
		/** The owner's name when last known, for listing while they are away. */
		public String ownerName = "";
		/** Where it was last seen: dimension id and block position. */
		public String dimension = "";
		public BlockPos lastPos = BlockPos.ZERO;
		/** Game time it was last seen loaded. */
		public long lastSeen;
		/** In-game day it was adopted, and last given a treat from the stock (-1 never). */
		public long adoptedDay;
		public long lastFedDay = -1;
		/** Checks in a row its last spot was loaded but the pet was nowhere to be found (see {@link PetEvents}). */
		public int missing;

		Pet(UUID id, PetKind kind, String name) {
			this.id = id;
			this.kind = kind;
			this.name = name;
		}
	}

	/** Where a map is in its life. */
	public enum MapState {
		/** Being drawn: carried by its maker, who fills it in as they walk. */
		DRAWING,
		/** Finished, carried home to be hung up or put away. */
		FINISHED,
		/** Hanging in an item frame (the town hall or library). */
		HUNG,
		/** Put away in the camp's chest (no town hall yet, or no room or frame for it). */
		STORED,
		/** Lost with its maker, or taken from its frame or the chest. */
		GONE;

		static MapState byName(String s) {
			try {
				return valueOf(s);
			} catch (IllegalArgumentException e) {
				return GONE;
			}
		}
	}

	/** One map the camp's map maker made. */
	public static final class MapRecord {
		/** The game's own map number (the item's map id). */
		public final int id;
		/** What it shows: "the camp", "the land to the north-east". */
		public final String shows;
		public final String dimension;
		public final int centreX;
		public final int centreZ;
		/** The game's map scale, 0 (1:1) to 4 (1:16). */
		public final int scale;
		/** True for the map of the camp itself, false for a trip's map. */
		public final boolean camp;
		public MapState state = MapState.DRAWING;
		/** Who is carrying it while drawing or bringing it home, or null. */
		public @Nullable UUID holder;
		public String maker = "";
		/** The item frame's spot while hung. */
		public @Nullable BlockPos frame;
		public long startedDay;
		public long finishedDay = -1;
		/** Share of the map drawn, 0 to 1, as last counted. */
		public float coverage;

		MapRecord(int id, String shows, String dimension, int centreX, int centreZ, int scale, boolean camp) {
			this.id = id;
			this.shows = shows;
			this.dimension = dimension;
			this.centreX = centreX;
			this.centreZ = centreZ;
			this.scale = scale;
			this.camp = camp;
		}

		/** Half the width of the land it covers, in blocks. */
		public int halfWidth() {
			return 64 << scale;
		}

		/** True if this block column lies on the map. */
		public boolean covers(String dim, int x, int z) {
			int half = halfWidth();
			return dimension.equals(dim) && x >= centreX - half && x < centreX + half && z >= centreZ - half && z < centreZ + half;
		}

		/** "Map of the camp". */
		public String title() {
			return "Map of " + shows;
		}
	}

	private final Map<UUID, Pet> pets = new LinkedHashMap<>();
	private final Map<Integer, MapRecord> maps = new LinkedHashMap<>();

	public PetsData() {
	}

	public static PetsData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ------------------------------------------------------------------- pets

	public Collection<Pet> pets() {
		return pets.values();
	}

	public Optional<Pet> pet(UUID id) {
		return Optional.ofNullable(pets.get(id));
	}

	/** This person's pet, if they have one. */
	public Optional<Pet> petOf(UUID owner) {
		for (Pet p : pets.values()) {
			if (owner.equals(p.owner)) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	public void addPet(Pet pet) {
		pets.put(pet.id, pet);
		setDirty();
	}

	Pet newPet(UUID id, PetKind kind, String name) {
		Pet pet = new Pet(id, kind, name);
		addPet(pet);
		return pet;
	}

	public void removePet(UUID id) {
		if (pets.remove(id) != null) {
			setDirty();
		}
	}

	/** Every living pet's name, lower case, so a new pet gets a name of its own. */
	public Set<String> petNames() {
		Set<String> names = new HashSet<>();
		for (Pet p : pets.values()) {
			names.add(p.name.toLowerCase(Locale.ROOT));
		}
		return names;
	}

	// ------------------------------------------------------------------- maps

	public Collection<MapRecord> maps() {
		return maps.values();
	}

	public Optional<MapRecord> map(int id) {
		return Optional.ofNullable(maps.get(id));
	}

	MapRecord newMap(int id, String shows, String dimension, int centreX, int centreZ, int scale, boolean camp) {
		MapRecord m = new MapRecord(id, shows, dimension, centreX, centreZ, scale, camp);
		maps.put(id, m);
		setDirty();
		return m;
	}

	/** The maps in a state, oldest first. */
	public List<MapRecord> maps(MapState state) {
		List<MapRecord> list = new ArrayList<>();
		for (MapRecord m : maps.values()) {
			if (m.state == state) {
				list.add(m);
			}
		}
		return list;
	}

	/** The camp's own map, unless it was lost. */
	public Optional<MapRecord> campMap() {
		for (MapRecord m : maps.values()) {
			if (m.camp && m.state != MapState.GONE) {
				return Optional.of(m);
			}
		}
		return Optional.empty();
	}

	/** The maps a friend is drawing or bringing home. */
	public List<MapRecord> carriedBy(UUID holder) {
		List<MapRecord> list = new ArrayList<>();
		for (MapRecord m : maps.values()) {
			if (holder.equals(m.holder) && (m.state == MapState.DRAWING || m.state == MapState.FINISHED)) {
				list.add(m);
			}
		}
		return list;
	}

	// ------------------------------------------------------------ persistence

	private static PetsData fromTag(CompoundTag tag) {
		PetsData data = new PetsData();
		for (Tag t : tag.getListOrEmpty("pets")) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			UUID id = uuid(c.getStringOr("id", ""));
			if (id == null) {
				continue;
			}
			Pet p = new Pet(id, PetKind.byKey(c.getStringOr("kind", "cat")), c.getStringOr("name", "Pet"));
			p.owner = uuid(c.getStringOr("owner", ""));
			p.ownerName = c.getStringOr("ownerName", "");
			p.dimension = c.getStringOr("dim", "");
			p.lastPos = BlockPos.of(c.getLongOr("pos", 0L));
			p.lastSeen = c.getLongOr("seen", 0L);
			p.adoptedDay = c.getLongOr("adopted", 0L);
			p.lastFedDay = c.getLongOr("fed", -1L);
			data.pets.put(id, p);
		}
		for (Tag t : tag.getListOrEmpty("maps")) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			int id = c.getIntOr("id", -1);
			if (id < 0) {
				continue;
			}
			MapRecord m = new MapRecord(id, c.getStringOr("shows", "the land"), c.getStringOr("dim", ""),
				c.getIntOr("x", 0), c.getIntOr("z", 0), Math.clamp(c.getIntOr("scale", 1), 0, 4), c.getBooleanOr("camp", false));
			m.state = MapState.byName(c.getStringOr("state", "GONE"));
			m.holder = uuid(c.getStringOr("holder", ""));
			m.maker = c.getStringOr("maker", "");
			m.frame = c.getLong("frame").map(BlockPos::of).orElse(null);
			m.startedDay = c.getLongOr("started", 0L);
			m.finishedDay = c.getLongOr("finished", -1L);
			m.coverage = c.getFloatOr("coverage", 0F);
			data.maps.put(id, m);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag petList = new ListTag();
		for (Pet p : pets.values()) {
			CompoundTag c = new CompoundTag();
			c.putString("id", p.id.toString());
			c.putString("kind", p.kind.key());
			c.putString("name", p.name);
			if (p.owner != null) {
				c.putString("owner", p.owner.toString());
			}
			c.putString("ownerName", p.ownerName);
			c.putString("dim", p.dimension);
			c.putLong("pos", p.lastPos.asLong());
			c.putLong("seen", p.lastSeen);
			c.putLong("adopted", p.adoptedDay);
			c.putLong("fed", p.lastFedDay);
			petList.add(c);
		}
		tag.put("pets", petList);
		ListTag mapList = new ListTag();
		for (MapRecord m : maps.values()) {
			CompoundTag c = new CompoundTag();
			c.putInt("id", m.id);
			c.putString("shows", m.shows);
			c.putString("dim", m.dimension);
			c.putInt("x", m.centreX);
			c.putInt("z", m.centreZ);
			c.putInt("scale", m.scale);
			c.putBoolean("camp", m.camp);
			c.putString("state", m.state.name());
			if (m.holder != null) {
				c.putString("holder", m.holder.toString());
			}
			c.putString("maker", m.maker);
			if (m.frame != null) {
				c.putLong("frame", m.frame.asLong());
			}
			c.putLong("started", m.startedDay);
			c.putLong("finished", m.finishedDay);
			c.putFloat("coverage", m.coverage);
			mapList.add(c);
		}
		tag.put("maps", mapList);
		return tag;
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
