package io.github.bradley09roberts.hardcorefriends.progress;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;

/**
 * What the camp remembers about beating the game, stored in {@code data/hardcorefriends_progress.dat}: which steps of
 * Sage's plan are done (and on which day), the goal last announced, the camp's experience pool and enchanting seed,
 * the obsidian the friends cast themselves (only those blocks may be mined back out), and where the sugar cane patch
 * and the nether wart beds are. Kept apart from the camp's own data so this package's saves never touch it.
 */
public final class ProgressData extends SavedData {
	public static final Codec<ProgressData> CODEC = CompoundTag.CODEC.xmap(ProgressData::fromTag, ProgressData::toTag);
	public static final SavedDataType<ProgressData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "progress"), ProgressData::new, CODEC, null);

	/** Most cast obsidian blocks remembered (a portal and a table need 14; this leaves lots of room for spares). */
	private static final int MAX_CAST = 1024;
	private static final int MAX_CANE_PATCHES = 2;
	private static final int MAX_WART_BEDS = 6;

	private final EnumMap<Milestone, Long> done = new EnumMap<>(Milestone.class);
	private @Nullable Milestone announced;
	private int experience;
	private int enchantSeed;
	/** Obsidian the friends made from lava, per dimension. */
	private final Map<String, LongOpenHashSet> cast = new HashMap<>();
	private final List<Long> canePatches = new ArrayList<>();
	private final List<Long> wartBeds = new ArrayList<>();

	public ProgressData() {
	}

	public static ProgressData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ------------------------------------------------------------------ plan

	public boolean isDone(Milestone m) {
		return done.containsKey(m);
	}

	/** The in-game day a step was reached, or -1. */
	public long doneOn(Milestone m) {
		return done.getOrDefault(m, -1L);
	}

	/** Records a step as done. Returns false if it already was. */
	public boolean markDone(Milestone m, long day) {
		if (done.containsKey(m)) {
			return false;
		}
		done.put(m, day);
		setDirty();
		return true;
	}

	/** The goal Sage last announced, so it is said once per goal. */
	public @Nullable Milestone announced() {
		return announced;
	}

	public void setAnnounced(@Nullable Milestone m) {
		if (announced != m) {
			announced = m;
			setDirty();
		}
	}

	// ------------------------------------------------------------ experience

	public int experience() {
		return experience;
	}

	public void setExperience(int points) {
		int clamped = Math.clamp(points, 0, Experience.MAX_POINTS);
		if (clamped != experience) {
			experience = clamped;
			setDirty();
		}
	}

	/** The camp's enchanting seed: like a player's, it decides the table's offers and changes after every enchant. */
	public int enchantSeed() {
		return enchantSeed;
	}

	public void setEnchantSeed(int seed) {
		enchantSeed = seed;
		setDirty();
	}

	// --------------------------------------------------------------- casting

	/** Remembers obsidian the friends cast, so they (and only they) may mine it back out. */
	public void recordCast(ServerLevel level, BlockPos pos) {
		LongOpenHashSet set = cast.computeIfAbsent(Camp.dimensionId(level), k -> new LongOpenHashSet());
		if (set.size() < MAX_CAST && set.add(pos.asLong())) {
			setDirty();
		}
	}

	public boolean isCast(ServerLevel level, BlockPos pos) {
		LongOpenHashSet set = cast.get(Camp.dimensionId(level));
		return set != null && set.contains(pos.asLong());
	}

	public void forgetCast(ServerLevel level, BlockPos pos) {
		LongOpenHashSet set = cast.get(Camp.dimensionId(level));
		if (set != null && set.remove(pos.asLong())) {
			setDirty();
		}
	}

	/** Cast obsidian still standing somewhere (positions only; the caller checks the block), at most {@code limit}. */
	public List<BlockPos> castPositions(ServerLevel level, int limit) {
		List<BlockPos> list = new ArrayList<>();
		LongOpenHashSet set = cast.get(Camp.dimensionId(level));
		if (set != null) {
			for (long l : set) {
				if (list.size() >= limit) {
					break;
				}
				list.add(BlockPos.of(l));
			}
		}
		return list;
	}

	// -------------------------------------------------------------- patches

	/** The water blocks of the friends' sugar cane patches (in the camp's dimension). */
	public List<BlockPos> canePatches() {
		List<BlockPos> list = new ArrayList<>();
		for (long l : canePatches) {
			list.add(BlockPos.of(l));
		}
		return list;
	}

	public void addCanePatch(BlockPos water) {
		if (canePatches.size() < MAX_CANE_PATCHES && !canePatches.contains(water.asLong())) {
			canePatches.add(water.asLong());
			setDirty();
		}
	}

	public void removeCanePatch(BlockPos water) {
		if (canePatches.remove(Long.valueOf(water.asLong()))) {
			setDirty();
		}
	}

	public boolean canAddCanePatch() {
		return canePatches.size() < MAX_CANE_PATCHES;
	}

	/** The soul sand of the friends' nether wart beds. */
	public List<BlockPos> wartBeds() {
		List<BlockPos> list = new ArrayList<>();
		for (long l : wartBeds) {
			list.add(BlockPos.of(l));
		}
		return list;
	}

	public void addWartBed(BlockPos soulSand) {
		if (wartBeds.size() < MAX_WART_BEDS && !wartBeds.contains(soulSand.asLong())) {
			wartBeds.add(soulSand.asLong());
			setDirty();
		}
	}

	public void removeWartBed(BlockPos soulSand) {
		if (wartBeds.remove(Long.valueOf(soulSand.asLong()))) {
			setDirty();
		}
	}

	public int maxWartBeds() {
		return MAX_WART_BEDS;
	}

	// ----------------------------------------------------------- persistence

	private static ProgressData fromTag(CompoundTag tag) {
		ProgressData data = new ProgressData();
		CompoundTag doneTag = tag.getCompoundOrEmpty("done");
		for (String key : doneTag.keySet()) {
			Milestone m = Milestone.byKey(key);
			if (m != null) {
				data.done.put(m, doneTag.getLongOr(key, 0L));
			}
		}
		data.announced = Milestone.byKey(tag.getStringOr("announced", ""));
		data.experience = Math.clamp(tag.getIntOr("xp", 0), 0, Experience.MAX_POINTS);
		data.enchantSeed = tag.getIntOr("seed", 0);
		CompoundTag castTag = tag.getCompoundOrEmpty("cast");
		for (String dim : castTag.keySet()) {
			long[] positions = castTag.getLongArray(dim).orElse(new long[0]);
			LongOpenHashSet set = new LongOpenHashSet();
			for (int i = 0; i < positions.length && i < MAX_CAST; i++) {
				set.add(positions[i]);
			}
			data.cast.put(dim, set);
		}
		for (long l : tag.getLongArray("cane").orElse(new long[0])) {
			if (data.canePatches.size() < MAX_CANE_PATCHES) {
				data.canePatches.add(l);
			}
		}
		for (long l : tag.getLongArray("wart").orElse(new long[0])) {
			if (data.wartBeds.size() < MAX_WART_BEDS) {
				data.wartBeds.add(l);
			}
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		CompoundTag doneTag = new CompoundTag();
		done.forEach((m, day) -> doneTag.putLong(m.key(), day));
		tag.put("done", doneTag);
		if (announced != null) {
			tag.putString("announced", announced.key());
		}
		tag.putInt("xp", experience);
		tag.putInt("seed", enchantSeed);
		CompoundTag castTag = new CompoundTag();
		cast.forEach((dim, set) -> {
			if (!set.isEmpty()) {
				castTag.putLongArray(dim, set.toLongArray());
			}
		});
		tag.put("cast", castTag);
		tag.putLongArray("cane", canePatches.stream().mapToLong(Long::longValue).toArray());
		tag.putLongArray("wart", wartBeds.stream().mapToLong(Long::longValue).toArray());
		return tag;
	}
}
