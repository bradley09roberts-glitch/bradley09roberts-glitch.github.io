package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/**
 * Everything the team remembers about the world: the camp, the supply chest, settlement progress, the Unity bond,
 * who has been recruited or lost, discoveries and blocks the friends placed themselves. One instance per server,
 * stored in {@code data/hardcorefriends_camp.dat}.
 */
public final class CampData extends SavedData {
	public static final Codec<CampData> CODEC = CompoundTag.CODEC.xmap(CampData::fromTag, CampData::toTag);
	public static final SavedDataType<CampData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "camp"), CampData::new, CODEC, null);

	private static final int MAX_POIS = 64;
	private static final int MAX_LOG = 256;
	private static final int MAX_PLACED = 20000;

	// Camp
	private BlockPos campPos;
	private String campDimension = "minecraft:overworld";
	private BlockPos chestPos;
	private int stage;
	private final Set<String> completed = new LinkedHashSet<>();
	private final Map<String, Site> sites = new LinkedHashMap<>();
	private final LongOpenHashSet placedBlocks = new LongOpenHashSet();

	// Unity
	private int unity;
	private long unityDay = -1;
	private final Map<String, Integer> unityToday = new HashMap<>();
	private long lastRallyTime = -1_000_000L;

	// People
	private final EnumMap<FriendId, Ledger> ledger = new EnumMap<>(FriendId.class);

	// Knowledge
	private final List<Poi> pois = new ArrayList<>();
	private final Map<String, CompoundTag> memory = new HashMap<>();
	private final Map<String, Long> stats = new HashMap<>();

	// Not saved: recent block edits for /friends log
	private final Deque<String> editLog = new ArrayDeque<>();

	public CampData() {
	}

	// ----------------------------------------------------------------- camp

	public Optional<BlockPos> campPos() {
		return Optional.ofNullable(campPos);
	}

	public String campDimension() {
		return campDimension;
	}

	public void setCamp(BlockPos pos, String dimension) {
		this.campPos = pos.immutable();
		this.campDimension = dimension;
		setDirty();
	}

	public Optional<BlockPos> chestPos() {
		return Optional.ofNullable(chestPos);
	}

	public void setChestPos(BlockPos pos) {
		this.chestPos = pos == null ? null : pos.immutable();
		setDirty();
	}

	public int stage() {
		return stage;
	}

	public void setStage(int stage) {
		this.stage = stage;
		setDirty();
	}

	public boolean isCompleted(String structureId) {
		return completed.contains(structureId);
	}

	public void markCompleted(String structureId) {
		if (completed.add(structureId)) {
			setDirty();
		}
	}

	public Set<String> completed() {
		return completed;
	}

	public Optional<Site> site(String structureId) {
		return Optional.ofNullable(sites.get(structureId));
	}

	public Map<String, Site> sites() {
		return sites;
	}

	public void putSite(String structureId, Site site) {
		sites.put(structureId, site);
		setDirty();
	}

	public void removeSite(String structureId) {
		if (sites.remove(structureId) != null) {
			setDirty();
		}
	}

	/** Records a block the friends placed so they may later upgrade or repair it and build next to it. */
	public void recordPlaced(BlockPos pos) {
		if (placedBlocks.size() < MAX_PLACED && placedBlocks.add(pos.asLong())) {
			setDirty();
		}
	}

	public void forgetPlaced(BlockPos pos) {
		if (placedBlocks.remove(pos.asLong())) {
			setDirty();
		}
	}

	public boolean isPlacedByFriends(BlockPos pos) {
		return placedBlocks.contains(pos.asLong());
	}

	// ---------------------------------------------------------------- unity

	public int unity() {
		return unity;
	}

	public void setUnity(int value) {
		this.unity = Math.clamp(value, 0, 1000);
		setDirty();
	}

	/** How much of a capped Unity category has been earned on the given in-game day. */
	public int unityEarnedToday(String category, long day) {
		rollDay(day);
		return unityToday.getOrDefault(category, 0);
	}

	public void addUnityEarnedToday(String category, long day, int amount) {
		rollDay(day);
		unityToday.merge(category, amount, Integer::sum);
		setDirty();
	}

	private void rollDay(long day) {
		if (day != unityDay) {
			unityDay = day;
			unityToday.clear();
			setDirty();
		}
	}

	public long lastRallyTime() {
		return lastRallyTime;
	}

	public void setLastRallyTime(long time) {
		this.lastRallyTime = time;
		setDirty();
	}

	// --------------------------------------------------------------- ledger

	public Ledger ledger(FriendId id) {
		return ledger.computeIfAbsent(id, k -> new Ledger());
	}

	public void touchLedger() {
		setDirty();
	}

	// ------------------------------------------------------------ knowledge

	public List<Poi> pois() {
		return pois;
	}

	/** Adds a point of interest unless one of the same type is already known within 4 blocks. */
	public boolean addPoi(String type, BlockPos pos, long gameTime) {
		for (Poi poi : pois) {
			if (poi.type.equals(type) && poi.pos.distSqr(pos) <= 16) {
				return false;
			}
		}
		if (pois.size() >= MAX_POIS) {
			pois.removeFirst();
		}
		pois.add(new Poi(type, pos.immutable(), gameTime));
		setDirty();
		return true;
	}

	public void removePoi(Poi poi) {
		if (pois.remove(poi)) {
			setDirty();
		}
	}

	/**
	 * Free-form persistent memory for one routine, such as Flint's mine progress or Rowan's current quarry.
	 * Callers must call {@link #setDirty()} after changing the returned tag.
	 */
	public CompoundTag memory(String key) {
		return memory.computeIfAbsent(key, k -> new CompoundTag());
	}

	public void addStat(String key, long amount) {
		stats.merge(key, amount, Long::sum);
		setDirty();
	}

	public long stat(String key) {
		return stats.getOrDefault(key, 0L);
	}

	public Map<String, Long> stats() {
		return stats;
	}

	public void logEdit(String entry) {
		if (editLog.size() >= MAX_LOG) {
			editLog.removeFirst();
		}
		editLog.addLast(entry);
	}

	public Deque<String> editLog() {
		return editLog;
	}

	// ---------------------------------------------------------------- types

	/** A reserved building site for one blueprint. {@code progress} counts placed blueprint entries. */
	public static final class Site {
		public final BlockPos origin;
		public final int rotation;
		public int progress;

		public Site(BlockPos origin, int rotation, int progress) {
			this.origin = origin.immutable();
			this.rotation = rotation;
			this.progress = progress;
		}
	}

	public enum LifeState {
		NEVER_RECRUITED,
		ALIVE,
		DEAD,
		DISMISSED
	}

	/** What the team knows about one friend. */
	public static final class Ledger {
		public LifeState state = LifeState.NEVER_RECRUITED;
		public UUID entityId;
		public long diedAtGameTime;
		public int deaths;
		public BlockPos lastKnownPos;
		public String lastKnownDimension = "minecraft:overworld";
		public String deathCause = "";
	}

	/** Something a friend found: an ore vein, a tree, lava, a cave entrance. */
	public static final class Poi {
		public final String type;
		public final BlockPos pos;
		public final long foundAt;

		public Poi(String type, BlockPos pos, long foundAt) {
			this.type = type;
			this.pos = pos;
			this.foundAt = foundAt;
		}
	}

	// --------------------------------------------------------- persistence

	private static CampData fromTag(CompoundTag tag) {
		CampData data = new CampData();
		tag.getLongArray("camp").filter(a -> a.length == 1).ifPresent(a -> data.campPos = BlockPos.of(a[0]));
		data.campDimension = tag.getStringOr("campDim", "minecraft:overworld");
		tag.getLongArray("chest").filter(a -> a.length == 1).ifPresent(a -> data.chestPos = BlockPos.of(a[0]));
		data.stage = tag.getIntOr("stage", 0);
		for (Tag t : tag.getListOrEmpty("completed")) {
			t.asString().ifPresent(data.completed::add);
		}
		CompoundTag sitesTag = tag.getCompoundOrEmpty("sites");
		for (String key : sitesTag.keySet()) {
			CompoundTag s = sitesTag.getCompoundOrEmpty(key);
			data.sites.put(key, new Site(BlockPos.of(s.getLongOr("origin", 0L)), s.getIntOr("rot", 0), s.getIntOr("progress", 0)));
		}
		tag.getLongArray("placed").ifPresent(a -> {
			for (long l : a) {
				data.placedBlocks.add(l);
			}
		});
		data.unity = Math.clamp(tag.getIntOr("unity", 0), 0, 1000);
		data.unityDay = tag.getLongOr("unityDay", -1L);
		CompoundTag today = tag.getCompoundOrEmpty("unityToday");
		for (String key : today.keySet()) {
			data.unityToday.put(key, today.getIntOr(key, 0));
		}
		data.lastRallyTime = tag.getLongOr("lastRally", -1_000_000L);
		CompoundTag ledgerTag = tag.getCompoundOrEmpty("ledger");
		for (FriendId id : FriendId.values()) {
			ledgerTag.getCompound(id.key()).ifPresent(l -> {
				Ledger e = new Ledger();
				try {
					e.state = LifeState.valueOf(l.getStringOr("state", "NEVER_RECRUITED"));
				} catch (IllegalArgumentException ex) {
					e.state = LifeState.NEVER_RECRUITED;
				}
				String uuid = l.getStringOr("uuid", "");
				if (!uuid.isEmpty()) {
					try {
						e.entityId = UUID.fromString(uuid);
					} catch (IllegalArgumentException ignored) {
						e.entityId = null;
					}
				}
				e.diedAtGameTime = l.getLongOr("diedAt", 0L);
				e.deaths = l.getIntOr("deaths", 0);
				l.getLongArray("pos").filter(a -> a.length == 1).ifPresent(a -> e.lastKnownPos = BlockPos.of(a[0]));
				e.lastKnownDimension = l.getStringOr("dim", "minecraft:overworld");
				e.deathCause = l.getStringOr("cause", "");
				data.ledger.put(id, e);
			});
		}
		for (Tag t : tag.getListOrEmpty("pois")) {
			if (t instanceof CompoundTag p) {
				data.pois.add(new Poi(p.getStringOr("type", "unknown"), BlockPos.of(p.getLongOr("pos", 0L)), p.getLongOr("at", 0L)));
			}
		}
		CompoundTag mem = tag.getCompoundOrEmpty("memory");
		for (String key : mem.keySet()) {
			data.memory.put(key, mem.getCompoundOrEmpty(key).copy());
		}
		CompoundTag st = tag.getCompoundOrEmpty("stats");
		for (String key : st.keySet()) {
			data.stats.put(key, st.getLongOr(key, 0L));
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		if (campPos != null) {
			tag.putLongArray("camp", new long[] {campPos.asLong()});
		}
		tag.putString("campDim", campDimension);
		if (chestPos != null) {
			tag.putLongArray("chest", new long[] {chestPos.asLong()});
		}
		tag.putInt("stage", stage);
		ListTag completedTag = new ListTag();
		for (String id : completed) {
			completedTag.add(StringTag.valueOf(id));
		}
		tag.put("completed", completedTag);
		CompoundTag sitesTag = new CompoundTag();
		sites.forEach((key, site) -> {
			CompoundTag s = new CompoundTag();
			s.putLong("origin", site.origin.asLong());
			s.putInt("rot", site.rotation);
			s.putInt("progress", site.progress);
			sitesTag.put(key, s);
		});
		tag.put("sites", sitesTag);
		tag.putLongArray("placed", placedBlocks.toLongArray());
		tag.putInt("unity", unity);
		tag.putLong("unityDay", unityDay);
		CompoundTag today = new CompoundTag();
		unityToday.forEach(today::putInt);
		tag.put("unityToday", today);
		tag.putLong("lastRally", lastRallyTime);
		CompoundTag ledgerTag = new CompoundTag();
		ledger.forEach((id, e) -> {
			CompoundTag l = new CompoundTag();
			l.putString("state", e.state.name());
			if (e.entityId != null) {
				l.putString("uuid", e.entityId.toString());
			}
			l.putLong("diedAt", e.diedAtGameTime);
			l.putInt("deaths", e.deaths);
			if (e.lastKnownPos != null) {
				l.putLongArray("pos", new long[] {e.lastKnownPos.asLong()});
			}
			l.putString("dim", e.lastKnownDimension);
			l.putString("cause", e.deathCause);
			ledgerTag.put(id.key(), l);
		});
		tag.put("ledger", ledgerTag);
		ListTag poiTag = new ListTag();
		for (Poi poi : pois) {
			CompoundTag p = new CompoundTag();
			p.putString("type", poi.type);
			p.putLong("pos", poi.pos.asLong());
			p.putLong("at", poi.foundAt);
			poiTag.add(p);
		}
		tag.put("pois", poiTag);
		CompoundTag mem = new CompoundTag();
		memory.forEach((key, value) -> mem.put(key, value.copy()));
		tag.put("memory", mem);
		CompoundTag st = new CompoundTag();
		stats.forEach(st::putLong);
		tag.put("stats", st);
		return tag;
	}
}
