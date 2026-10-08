package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * What the world remembers about the players of the camp, kept apart from the camp's own data so several players can
 * share one camp fairly: who owns the camp and whom they trust, every friend's bond with every player, how much each
 * player has helped the camp, the players the camp has lost, notes left for the camp, players' mailboxes and the
 * deliveries asked for, and when the next siege night is due. Stored in {@code data/hardcorefriends_town.dat}.
 *
 * <p>A friend's bonds are kept under their {@linkplain Bonds#key key}: one of the nine friends by their own name
 * ({@code "fern"}), a newcomer by their entity id, so a dismissed friend who rejoins still remembers who sent them
 * away, and a fallen friend's bonds are let go (whoever comes back later is somebody new).
 */
public final class TownData extends SavedData {
	public static final Codec<TownData> CODEC = CompoundTag.CODEC.xmap(TownData::fromTag, TownData::toTag);
	public static final SavedDataType<TownData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "town"), TownData::new, CODEC, null);

	/** Most players whose names are remembered (for lists and for trusting someone who is offline). */
	private static final int MAX_PLAYERS = 256;
	/** Most players remembered on the memorial. */
	private static final int MAX_MEMORIALS = 50;
	/** Notes kept: the last 20. */
	public static final int MAX_NOTES = 20;
	/** Deliveries waiting at once, for everyone together. */
	public static final int MAX_QUEUE = 12;

	/** A player the camp lost. */
	public record Memorial(String name, String cause, String dimension, BlockPos pos, long day) {
	}

	/** A note left for the camp, read out once by a friend at camp. */
	public static final class Note {
		public final UUID author;
		public final String authorName;
		public final String text;
		public final long day;
		public boolean read;

		public Note(UUID author, String authorName, String text, long day, boolean read) {
			this.author = author;
			this.authorName = authorName;
			this.text = text;
			this.day = day;
			this.read = read;
		}
	}

	/** A player's mailbox: a chest or barrel of their own that friends may put deliveries in, and nothing else. */
	public record Mailbox(String dimension, BlockPos pos) {
	}

	/** A delivery a player asked for: so many of one item from the supply chest to their mailbox. */
	public record SendRequest(UUID player, String playerName, Identifier item, int count, long askedAt) {
	}

	private @Nullable UUID owner;
	private final Map<UUID, String> trusted = new LinkedHashMap<>();
	private final Map<UUID, String> players = new LinkedHashMap<>();
	private final Map<String, Map<UUID, Integer>> bonds = new LinkedHashMap<>();
	private final Map<String, String> friendNames = new LinkedHashMap<>();
	private final Map<UUID, Integer> helped = new LinkedHashMap<>();
	private final List<Memorial> memorials = new ArrayList<>();
	private final List<Note> notes = new ArrayList<>();
	private final Map<UUID, Mailbox> mailboxes = new LinkedHashMap<>();
	private final List<SendRequest> queue = new ArrayList<>();
	/** The in-game day of each player's last delivery of the camp's surplus. */
	private final Map<UUID, Long> lastSurplus = new LinkedHashMap<>();
	/** Siege nights: the day of the next one (-1 until planned), how far tonight's has got, and whether anyone fell. */
	private long nextSiegeDay = -1;
	private int siegePhase;
	private long siegeDay = -1;
	private boolean siegeLost;

	public TownData() {
	}

	public static TownData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Forgets everything. Only used by automated tests, which run one at a time (each with its own mock player). */
	public void resetForTests() {
		owner = null;
		trusted.clear();
		players.clear();
		bonds.clear();
		friendNames.clear();
		helped.clear();
		memorials.clear();
		notes.clear();
		mailboxes.clear();
		queue.clear();
		lastSurplus.clear();
		nextSiegeDay = -1;
		siegePhase = 0;
		siegeDay = -1;
		siegeLost = false;
		setDirty();
	}

	// ----------------------------------------------------------------- players

	/** Remembers a player's current name (players can change their name; the id stays). */
	public void remember(ServerPlayer player) {
		remember(player.getUUID(), player.getName().getString());
	}

	public void remember(UUID id, String name) {
		if (name.equals(players.get(id))) {
			return;
		}
		players.remove(id);
		players.put(id, name);
		// Past the limit the longest-unseen names go first, never the owner's or a trusted player's.
		for (Iterator<UUID> it = players.keySet().iterator(); players.size() > MAX_PLAYERS && it.hasNext();) {
			UUID oldest = it.next();
			if (!oldest.equals(owner) && !trusted.containsKey(oldest) && !oldest.equals(id)) {
				it.remove();
			}
		}
		if (trusted.containsKey(id)) {
			trusted.put(id, name);
		}
		setDirty();
	}

	/** A known player's name, or "someone". */
	public String name(@Nullable UUID id) {
		if (id == null) {
			return "someone";
		}
		return players.getOrDefault(id, trusted.getOrDefault(id, "someone"));
	}

	/** A player remembered by name (any case), if any. */
	public Optional<UUID> byName(String name) {
		for (Map.Entry<UUID, String> e : players.entrySet()) {
			if (e.getValue().equalsIgnoreCase(name)) {
				return Optional.of(e.getKey());
			}
		}
		return Optional.empty();
	}

	public Map<UUID, String> players() {
		return players;
	}

	// ------------------------------------------------------------ owner, trust

	public Optional<UUID> owner() {
		return Optional.ofNullable(owner);
	}

	public String ownerName() {
		return name(owner);
	}

	public void setOwner(UUID id, String name) {
		remember(id, name);
		if (owner != null && !owner.equals(id)) {
			trusted.put(owner, name(owner)); // the old owner stays trusted
		}
		owner = id;
		trusted.remove(id);
		setDirty();
	}

	public boolean isOwner(UUID id) {
		return id.equals(owner);
	}

	public boolean isTrusted(UUID id) {
		return trusted.containsKey(id);
	}

	public Map<UUID, String> trusted() {
		return trusted;
	}

	public void trust(UUID id, String name) {
		remember(id, name);
		trusted.put(id, name);
		setDirty();
	}

	public boolean untrust(UUID id) {
		boolean removed = trusted.remove(id) != null;
		if (removed) {
			setDirty();
		}
		return removed;
	}

	// ------------------------------------------------------------------- bonds

	/** A friend's bond with a player, -100..100 (0 when they have never met). */
	public int bond(String friend, UUID player) {
		Map<UUID, Integer> map = bonds.get(friend);
		return map == null ? 0 : map.getOrDefault(player, 0);
	}

	/** Sets a bond (clamped), and remembers the friend's name for lists. */
	public void setBond(String friend, String friendName, UUID player, int value) {
		bonds.computeIfAbsent(friend, k -> new LinkedHashMap<>()).put(player, Math.clamp(value, Bonds.MIN, Bonds.MAX));
		friendNames.put(friend, friendName);
		setDirty();
	}

	/** Every player this friend has a bond with. */
	public Map<UUID, Integer> bondsOf(String friend) {
		return bonds.getOrDefault(friend, Map.of());
	}

	/** Every friend with bonds, by key. */
	public Map<String, Map<UUID, Integer>> allBonds() {
		return bonds;
	}

	public String friendName(String friend) {
		return friendNames.getOrDefault(friend, friend);
	}

	/** Lets go of a friend's bonds (they fell, or a newcomer left for good). */
	public void forgetFriend(String friend) {
		boolean changed = bonds.remove(friend) != null;
		changed |= friendNames.remove(friend) != null;
		if (changed) {
			setDirty();
		}
	}

	// ------------------------------------------------------------------ helped

	public int helped(UUID player) {
		return helped.getOrDefault(player, 0);
	}

	public void addHelped(UUID player, int amount) {
		helped.merge(player, amount, Integer::sum);
		setDirty();
	}

	public Map<UUID, Integer> helpedAll() {
		return helped;
	}

	// -------------------------------------------------------------- memorials

	public List<Memorial> memorials() {
		return memorials;
	}

	public void addMemorial(Memorial memorial) {
		memorials.add(memorial);
		while (memorials.size() > MAX_MEMORIALS) {
			memorials.removeFirst();
		}
		setDirty();
	}

	// ------------------------------------------------------------------ notes

	public List<Note> notes() {
		return notes;
	}

	public void addNote(Note note) {
		notes.add(note);
		while (notes.size() > MAX_NOTES) {
			notes.removeFirst();
		}
		setDirty();
	}

	/** The oldest note nobody has read out yet, if any. */
	public Optional<Note> firstUnread() {
		for (Note n : notes) {
			if (!n.read) {
				return Optional.of(n);
			}
		}
		return Optional.empty();
	}

	// -------------------------------------------------------------- mailboxes

	public Optional<Mailbox> mailbox(UUID player) {
		return Optional.ofNullable(mailboxes.get(player));
	}

	public Map<UUID, Mailbox> mailboxes() {
		return mailboxes;
	}

	/** Whose mailbox this is, if it is anyone's. */
	public Optional<UUID> mailboxOwner(String dimension, BlockPos pos) {
		for (Map.Entry<UUID, Mailbox> e : mailboxes.entrySet()) {
			if (e.getValue().dimension().equals(dimension) && e.getValue().pos().equals(pos)) {
				return Optional.of(e.getKey());
			}
		}
		return Optional.empty();
	}

	public void setMailbox(UUID player, @Nullable Mailbox mailbox) {
		if (mailbox == null) {
			mailboxes.remove(player);
			queue.removeIf(r -> r.player().equals(player));
		} else {
			mailboxes.put(player, mailbox);
		}
		setDirty();
	}

	public List<SendRequest> queue() {
		return queue;
	}

	public boolean enqueue(SendRequest request) {
		if (queue.size() >= MAX_QUEUE) {
			return false;
		}
		queue.add(request);
		setDirty();
		return true;
	}

	public void dequeue(SendRequest request) {
		if (queue.remove(request)) {
			setDirty();
		}
	}

	public long lastSurplus(UUID player) {
		return lastSurplus.getOrDefault(player, Long.MIN_VALUE);
	}

	public void setLastSurplus(UUID player, long day) {
		lastSurplus.put(player, day);
		setDirty();
	}

	// ------------------------------------------------------------------ sieges

	public long nextSiegeDay() {
		return nextSiegeDay;
	}

	public int siegePhase() {
		return siegePhase;
	}

	public long siegeDay() {
		return siegeDay;
	}

	public boolean siegeLost() {
		return siegeLost;
	}

	public void planSiege(long day) {
		nextSiegeDay = day;
		setDirty();
	}

	public void setSiege(int phase, long day, boolean lost) {
		siegePhase = phase;
		siegeDay = day;
		siegeLost = lost;
		setDirty();
	}

	public void markSiegeLoss() {
		if (siegePhase > 0 && !siegeLost) {
			siegeLost = true;
			setDirty();
		}
	}

	// ------------------------------------------------------------ persistence

	private static TownData fromTag(CompoundTag tag) {
		TownData data = new TownData();
		data.owner = uuid(tag.getStringOr("owner", ""));
		readNames(tag.getCompoundOrEmpty("trusted"), data.trusted);
		readNames(tag.getCompoundOrEmpty("players"), data.players);
		CompoundTag bondsTag = tag.getCompoundOrEmpty("bonds");
		for (String friend : bondsTag.keySet()) {
			CompoundTag f = bondsTag.getCompoundOrEmpty(friend);
			Map<UUID, Integer> map = new LinkedHashMap<>();
			CompoundTag scores = f.getCompoundOrEmpty("scores");
			for (String key : scores.keySet()) {
				UUID id = uuid(key);
				if (id != null) {
					map.put(id, Math.clamp(scores.getIntOr(key, 0), Bonds.MIN, Bonds.MAX));
				}
			}
			data.bonds.put(friend, map);
			data.friendNames.put(friend, f.getStringOr("name", friend));
		}
		CompoundTag helpedTag = tag.getCompoundOrEmpty("helped");
		for (String key : helpedTag.keySet()) {
			UUID id = uuid(key);
			if (id != null) {
				data.helped.put(id, Math.max(0, helpedTag.getIntOr(key, 0)));
			}
		}
		for (Tag t : tag.getListOrEmpty("memorials")) {
			if (t instanceof CompoundTag m) {
				data.memorials.add(new Memorial(m.getStringOr("name", "someone"), m.getStringOr("cause", ""),
					m.getStringOr("dim", "minecraft:overworld"), BlockPos.of(m.getLongOr("pos", 0L)), m.getLongOr("day", 0L)));
			}
		}
		for (Tag t : tag.getListOrEmpty("notes")) {
			if (t instanceof CompoundTag n) {
				UUID author = uuid(n.getStringOr("author", ""));
				if (author != null) {
					data.notes.add(new Note(author, n.getStringOr("name", "someone"), n.getStringOr("text", ""),
						n.getLongOr("day", 0L), n.getBooleanOr("read", false)));
				}
			}
		}
		CompoundTag mailTag = tag.getCompoundOrEmpty("mailboxes");
		for (String key : mailTag.keySet()) {
			UUID id = uuid(key);
			CompoundTag m = mailTag.getCompoundOrEmpty(key);
			if (id != null) {
				data.mailboxes.put(id, new Mailbox(m.getStringOr("dim", "minecraft:overworld"), BlockPos.of(m.getLongOr("pos", 0L))));
			}
		}
		for (Tag t : tag.getListOrEmpty("queue")) {
			if (t instanceof CompoundTag q) {
				UUID id = uuid(q.getStringOr("player", ""));
				Identifier item = Identifier.tryParse(q.getStringOr("item", ""));
				if (id != null && item != null) {
					data.queue.add(new SendRequest(id, q.getStringOr("name", "someone"), item, Math.clamp(q.getIntOr("count", 1), 1, 64),
						q.getLongOr("at", 0L)));
				}
			}
		}
		CompoundTag surplusTag = tag.getCompoundOrEmpty("surplus");
		for (String key : surplusTag.keySet()) {
			UUID id = uuid(key);
			if (id != null) {
				data.lastSurplus.put(id, surplusTag.getLongOr(key, Long.MIN_VALUE));
			}
		}
		data.nextSiegeDay = tag.getLongOr("nextSiege", -1L);
		data.siegePhase = tag.getIntOr("siegePhase", 0);
		data.siegeDay = tag.getLongOr("siegeDay", -1L);
		data.siegeLost = tag.getBooleanOr("siegeLost", false);
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		if (owner != null) {
			tag.putString("owner", owner.toString());
		}
		tag.put("trusted", writeNames(trusted));
		tag.put("players", writeNames(players));
		CompoundTag bondsTag = new CompoundTag();
		bonds.forEach((friend, map) -> {
			if (map.isEmpty()) {
				return;
			}
			CompoundTag f = new CompoundTag();
			f.putString("name", friendNames.getOrDefault(friend, friend));
			CompoundTag scores = new CompoundTag();
			map.forEach((id, score) -> scores.putInt(id.toString(), score));
			f.put("scores", scores);
			bondsTag.put(friend, f);
		});
		tag.put("bonds", bondsTag);
		CompoundTag helpedTag = new CompoundTag();
		helped.forEach((id, n) -> helpedTag.putInt(id.toString(), n));
		tag.put("helped", helpedTag);
		ListTag memorialTag = new ListTag();
		for (Memorial m : memorials) {
			CompoundTag t = new CompoundTag();
			t.putString("name", m.name());
			t.putString("cause", m.cause());
			t.putString("dim", m.dimension());
			t.putLong("pos", m.pos().asLong());
			t.putLong("day", m.day());
			memorialTag.add(t);
		}
		tag.put("memorials", memorialTag);
		ListTag noteTag = new ListTag();
		for (Note n : notes) {
			CompoundTag t = new CompoundTag();
			t.putString("author", n.author.toString());
			t.putString("name", n.authorName);
			t.putString("text", n.text);
			t.putLong("day", n.day);
			t.putBoolean("read", n.read);
			noteTag.add(t);
		}
		tag.put("notes", noteTag);
		CompoundTag mailTag = new CompoundTag();
		mailboxes.forEach((id, m) -> {
			CompoundTag t = new CompoundTag();
			t.putString("dim", m.dimension());
			t.putLong("pos", m.pos().asLong());
			mailTag.put(id.toString(), t);
		});
		tag.put("mailboxes", mailTag);
		ListTag queueTag = new ListTag();
		for (SendRequest r : queue) {
			CompoundTag t = new CompoundTag();
			t.putString("player", r.player().toString());
			t.putString("name", r.playerName());
			t.putString("item", r.item().toString());
			t.putInt("count", r.count());
			t.putLong("at", r.askedAt());
			queueTag.add(t);
		}
		tag.put("queue", queueTag);
		CompoundTag surplusTag = new CompoundTag();
		lastSurplus.forEach((id, day) -> surplusTag.putLong(id.toString(), day));
		tag.put("surplus", surplusTag);
		tag.putLong("nextSiege", nextSiegeDay);
		tag.putInt("siegePhase", siegePhase);
		tag.putLong("siegeDay", siegeDay);
		tag.putBoolean("siegeLost", siegeLost);
		return tag;
	}

	private static void readNames(CompoundTag tag, Map<UUID, String> into) {
		for (String key : tag.keySet()) {
			UUID id = uuid(key);
			if (id != null) {
				into.put(id, tag.getStringOr(key, "someone"));
			}
		}
	}

	private static CompoundTag writeNames(Map<UUID, String> names) {
		CompoundTag tag = new CompoundTag();
		names.forEach((id, name) -> tag.putString(id.toString(), name));
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
