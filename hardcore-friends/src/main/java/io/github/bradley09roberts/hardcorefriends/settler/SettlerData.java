package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.ArrayList;
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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/**
 * What the world remembers about newcomers, kept apart from the nine friends' ledger: every newcomer recruited (alive,
 * fallen or gone, with who asked them in and where they were last seen), the names of strangers living out in the
 * world (so no two living people share a name), the villages already looked at for newcomers, and when the next
 * traveller is due at the camp. Stored in {@code data/hardcorefriends_settlers.dat}.
 */
public final class SettlerData extends SavedData {
	public static final Codec<SettlerData> CODEC = CompoundTag.CODEC.xmap(SettlerData::fromTag, SettlerData::toTag);
	public static final SavedDataType<SettlerData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "settlers"), SettlerData::new, CODEC, null);

	/** How a recruited newcomer's story ended, or that it goes on. */
	public enum State {
		ALIVE,
		DEAD,
		DISMISSED
	}

	/** One recruited newcomer. Positions are refreshed every few seconds while they are loaded. */
	public static final class Newcomer {
		public final UUID id;
		public String name;
		public FriendId archetype;
		public int colour;
		public State state = State.ALIVE;
		public @Nullable UUID recruitedBy;
		public String recruitedByName = "";
		public long recruitedAt;
		public long endedAt;
		public @Nullable BlockPos lastPos;
		public String lastDimension = "minecraft:overworld";
		public String cause = "";

		public Newcomer(UUID id, String name, FriendId archetype, int colour) {
			this.id = id;
			this.name = name;
			this.archetype = archetype;
			this.colour = colour;
		}
	}

	private final Map<UUID, Newcomer> newcomers = new LinkedHashMap<>();
	/** Strangers living out in the world (not recruited, not known to be dead), by id: their names. */
	private final Map<UUID, String> strangers = new LinkedHashMap<>();
	/** Villages already looked at for newcomers: "dimension|structure|start chunk". */
	private final Set<String> villages = new LinkedHashSet<>();
	/** Game time from which the next traveller may visit the camp; -1 until first planned. */
	private long nextWandererAt = -1;
	/** Game time until which the current traveller's visit lasts (no other comes meanwhile). */
	private long wandererUntil = -1;

	public SettlerData() {
	}

	public static SettlerData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ------------------------------------------------------------- newcomers

	public Optional<Newcomer> newcomer(UUID id) {
		return Optional.ofNullable(newcomers.get(id));
	}

	/** Every newcomer ever recruited in this world, in the order they joined. */
	public List<Newcomer> newcomers() {
		return new ArrayList<>(newcomers.values());
	}

	/** Records a newcomer joining the team (a stranger no more). */
	public Newcomer recordJoined(UUID id, String name, FriendId archetype, int colour, @Nullable UUID by, String byName, long now) {
		Newcomer n = newcomers.computeIfAbsent(id, k -> new Newcomer(id, name, archetype, colour));
		n.name = name;
		n.archetype = archetype;
		n.colour = colour;
		n.state = State.ALIVE;
		n.recruitedBy = by;
		n.recruitedByName = byName;
		n.recruitedAt = now;
		n.cause = "";
		strangers.remove(id);
		setDirty();
		return n;
	}

	/** Newcomers on the team and alive. */
	public int aliveCount() {
		int n = 0;
		for (Newcomer c : newcomers.values()) {
			if (c.state == State.ALIVE) {
				n++;
			}
		}
		return n;
	}

	/** Newcomers on the team and alive whom this player asked in. */
	public int aliveRecruitedBy(UUID player) {
		int n = 0;
		for (Newcomer c : newcomers.values()) {
			if (c.state == State.ALIVE && player.equals(c.recruitedBy)) {
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------- strangers

	/** Notes a stranger living somewhere, so their name is not given to anyone else while they live. */
	public void rememberStranger(UUID id, String name) {
		if (!name.equals(strangers.put(id, name))) {
			setDirty();
		}
	}

	/** Forgets a stranger who died, left or joined the team. */
	public void forgetStranger(UUID id) {
		if (strangers.remove(id) != null) {
			setDirty();
		}
	}

	public boolean isKnownStranger(UUID id) {
		return strangers.containsKey(id);
	}

	/**
	 * Names (lower case) that a new person may not take: living strangers' and living newcomers', apart from
	 * {@code except} (the person being named).
	 */
	public Set<String> namesInUse(@Nullable UUID except) {
		Set<String> names = new HashSet<>();
		strangers.forEach((id, name) -> {
			if (!id.equals(except)) {
				names.add(name.toLowerCase(Locale.ROOT));
			}
		});
		for (Newcomer n : newcomers.values()) {
			if (n.state == State.ALIVE && !n.id.equals(except)) {
				names.add(n.name.toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	/** Names (lower case) of the living newcomers on the team, apart from {@code except}. */
	public Set<String> teamNames(@Nullable UUID except) {
		Set<String> names = new HashSet<>();
		for (Newcomer n : newcomers.values()) {
			if (n.state == State.ALIVE && !n.id.equals(except)) {
				names.add(n.name.toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

	// -------------------------------------------------------------- villages

	public boolean villageLookedAt(String key) {
		return villages.contains(key);
	}

	public void markVillage(String key) {
		if (villages.add(key)) {
			setDirty();
		}
	}

	// ------------------------------------------------------------- travellers

	public long nextWandererAt() {
		return nextWandererAt;
	}

	public void setNextWandererAt(long time) {
		nextWandererAt = time;
		setDirty();
	}

	public long wandererUntil() {
		return wandererUntil;
	}

	public void setWandererUntil(long time) {
		wandererUntil = time;
		setDirty();
	}

	// ------------------------------------------------------------ persistence

	private static SettlerData fromTag(CompoundTag tag) {
		SettlerData data = new SettlerData();
		for (Tag t : tag.getListOrEmpty("newcomers")) {
			if (!(t instanceof CompoundTag c)) {
				continue;
			}
			UUID id = uuid(c.getStringOr("id", ""));
			if (id == null) {
				continue;
			}
			FriendId archetype = FriendId.byKey(c.getStringOr("archetype", "rowan")).orElse(FriendId.ROWAN);
			Newcomer n = new Newcomer(id, c.getStringOr("name", "Someone"), archetype, c.getIntOr("colour", 0xFFFFFF));
			try {
				n.state = State.valueOf(c.getStringOr("state", "ALIVE"));
			} catch (IllegalArgumentException e) {
				n.state = State.ALIVE;
			}
			n.recruitedBy = uuid(c.getStringOr("by", ""));
			n.recruitedByName = c.getStringOr("byName", "");
			n.recruitedAt = c.getLongOr("at", 0L);
			n.endedAt = c.getLongOr("ended", 0L);
			c.getLongArray("pos").filter(a -> a.length == 1).ifPresent(a -> n.lastPos = BlockPos.of(a[0]));
			n.lastDimension = c.getStringOr("dim", "minecraft:overworld");
			n.cause = c.getStringOr("cause", "");
			data.newcomers.put(id, n);
		}
		CompoundTag strangerTag = tag.getCompoundOrEmpty("strangers");
		for (String key : strangerTag.keySet()) {
			UUID id = uuid(key);
			if (id != null) {
				data.strangers.put(id, strangerTag.getStringOr(key, ""));
			}
		}
		for (Tag t : tag.getListOrEmpty("villages")) {
			t.asString().ifPresent(data.villages::add);
		}
		data.nextWandererAt = tag.getLongOr("nextWanderer", -1L);
		data.wandererUntil = tag.getLongOr("wandererUntil", -1L);
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (Newcomer n : newcomers.values()) {
			CompoundTag c = new CompoundTag();
			c.putString("id", n.id.toString());
			c.putString("name", n.name);
			c.putString("archetype", n.archetype.key());
			c.putInt("colour", n.colour);
			c.putString("state", n.state.name());
			if (n.recruitedBy != null) {
				c.putString("by", n.recruitedBy.toString());
			}
			c.putString("byName", n.recruitedByName);
			c.putLong("at", n.recruitedAt);
			c.putLong("ended", n.endedAt);
			if (n.lastPos != null) {
				c.putLongArray("pos", new long[] {n.lastPos.asLong()});
			}
			c.putString("dim", n.lastDimension);
			c.putString("cause", n.cause);
			list.add(c);
		}
		tag.put("newcomers", list);
		CompoundTag strangerTag = new CompoundTag();
		strangers.forEach((id, name) -> strangerTag.putString(id.toString(), name));
		tag.put("strangers", strangerTag);
		ListTag villageTag = new ListTag();
		for (String key : villages) {
			villageTag.add(StringTag.valueOf(key));
		}
		tag.put("villages", villageTag);
		tag.putLong("nextWanderer", nextWandererAt);
		tag.putLong("wandererUntil", wandererUntil);
		return tag;
	}

	private static @Nullable UUID uuid(String s) {
		if (s.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
