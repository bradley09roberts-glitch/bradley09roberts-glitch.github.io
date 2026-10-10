package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/**
 * What the world remembers about the village: where its town plan is centred, which streets are open, every plot
 * (what stands on it or is being built there, and for a house who lives in it and in which bed), the buildings other
 * packages asked for, and the names of the people who live there (so homes can be listed while their people are away).
 * One instance per server, stored in {@code data/hardcorefriends_village.dat}; the camp's own data stays in
 * {@code CampData}, and the buildings themselves are ordinary library sites there (the plot records their site key).
 */
public final class VillageData extends SavedData {
	public static final Codec<VillageData> CODEC = CompoundTag.CODEC.xmap(VillageData::fromTag, VillageData::toTag);
	public static final SavedDataType<VillageData> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, "village"), VillageData::new, CODEC, null);

	/** Where a plot stands in its life: reserved and being built, or standing. */
	public enum PlotState {
		BUILDING,
		STANDING
	}

	/**
	 * One plot of the town plan and the building on it.
	 *
	 * <p>{@code street} is the street it fronts ({@link TownPlan#STREETS} index), or -1 for the square, the waterside,
	 * the town gate and walls. {@code box} is the building's footprint {minX, minZ, maxX, maxZ}. For a house,
	 * {@code intended} is the household it was planned for and {@code residents} who lives there now, each with the
	 * index of their bed among the plan's {@code bed} markers (-1: no bed free yet).
	 */
	public static final class Plot {
		public final int id;
		public final String kind;
		public final String planId;
		public final String siteKey;
		public final int[] box;
		public final int street;
		public final int side;
		public final int rotation;
		public final int floorY;
		public final String purpose;
		public final long plannedAt;
		public PlotState state = PlotState.BUILDING;
		public final Set<UUID> intended = new LinkedHashSet<>();
		public final Map<UUID, Integer> residents = new LinkedHashMap<>();

		public Plot(int id, String kind, String planId, String siteKey, int[] box, int street, int side, int rotation, int floorY,
			String purpose, long plannedAt) {
			this.id = id;
			this.kind = kind;
			this.planId = planId;
			this.siteKey = siteKey;
			this.box = box.clone();
			this.street = street;
			this.side = side;
			this.rotation = rotation;
			this.floorY = floorY;
			this.purpose = purpose;
			this.plannedAt = plannedAt;
		}

		public boolean isHouse() {
			return "house".equals(kind);
		}

		public boolean standing() {
			return state == PlotState.STANDING;
		}

		/** The middle of the footprint, at floor level. */
		public BlockPos middle() {
			return new BlockPos((box[0] + box[2]) / 2, floorY, (box[1] + box[3]) / 2);
		}

		/** True if the kind is this one, or this kind without a colon covers it ("shop" covers "shop:bakery"). */
		public boolean isKind(String wanted) {
			return kind.equals(wanted) || !wanted.contains(":") && kind.startsWith(wanted + ":");
		}
	}

	/** A building another package asked the village for (a shop, a workplace), waiting for a plot. */
	public record Request(String kind, String reason, int count, long at) {
	}

	private @Nullable BlockPos centre;
	private String dimension = "minecraft:overworld";
	private long planDay = -1;
	/** How finished the street surfaces are: 0 trodden earth, 1 gravel, 2 cobblestone. */
	private int streetLevel;
	/** Street index to the open stretch {from, to} along it, relative to the camp centre. */
	private final Map<Integer, int[]> open = new LinkedHashMap<>();
	private final List<Plot> plots = new ArrayList<>();
	private int nextPlotId = 1;
	private final Map<String, Request> requests = new LinkedHashMap<>();
	private final Map<UUID, String> names = new HashMap<>();
	private final Set<String> announced = new LinkedHashSet<>();

	public VillageData() {
	}

	public static VillageData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	// ------------------------------------------------------------------ plan

	/** The camp centre the town plan was laid out round, or empty before there is a plan. */
	public Optional<BlockPos> centre() {
		return Optional.ofNullable(centre);
	}

	public String dimension() {
		return dimension;
	}

	public long planDay() {
		return planDay;
	}

	/** Lays out a new plan round this centre, forgetting any old one (the caller has released its sites). */
	public void startPlan(BlockPos centre, String dimension, long day) {
		this.centre = centre.immutable();
		this.dimension = dimension;
		this.planDay = day;
		this.streetLevel = 0;
		open.clear();
		plots.clear();
		announced.clear();
		setDirty();
	}

	/** Forgets the plan (the camp moved away, or the village was switched off). Requests and names are kept. */
	public void clearPlan() {
		centre = null;
		planDay = -1;
		streetLevel = 0;
		open.clear();
		plots.clear();
		announced.clear();
		setDirty();
	}

	public int streetLevel() {
		return streetLevel;
	}

	public void setStreetLevel(int level) {
		if (level != streetLevel) {
			streetLevel = level;
			setDirty();
		}
	}

	/** The open stretch of a street {from, to}, or null while nothing fronts it yet. */
	public int @Nullable [] open(int street) {
		return open.get(street);
	}

	/** Opens a street at least as far as {@code from}..{@code to}. Returns true if it was not open at all before. */
	public boolean openStreet(int street, int from, int to) {
		int[] range = open.get(street);
		if (range == null) {
			open.put(street, new int[] {Math.min(from, to), Math.max(from, to)});
			setDirty();
			return true;
		}
		int lo = Math.min(range[0], Math.min(from, to));
		int hi = Math.max(range[1], Math.max(from, to));
		if (lo != range[0] || hi != range[1]) {
			range[0] = lo;
			range[1] = hi;
			setDirty();
		}
		return false;
	}

	public Map<Integer, int[]> openStreets() {
		return open;
	}

	// ----------------------------------------------------------------- plots

	public List<Plot> plots() {
		return plots;
	}

	public int nextPlotId() {
		int id = nextPlotId++;
		setDirty();
		return id;
	}

	public void addPlot(Plot plot) {
		plots.add(plot);
		setDirty();
	}

	public void removePlot(Plot plot) {
		if (plots.remove(plot)) {
			setDirty();
		}
	}

	public Optional<Plot> plotBySite(String siteKey) {
		for (Plot p : plots) {
			if (p.siteKey.equals(siteKey)) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	/** The house this person lives in, if any. */
	public Optional<Plot> homeOf(UUID resident) {
		for (Plot p : plots) {
			if (p.isHouse() && p.residents.containsKey(resident)) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	// -------------------------------------------------------------- requests

	public Map<String, Request> requests() {
		return requests;
	}

	public void putRequest(Request request) {
		requests.put(request.kind(), request);
		setDirty();
	}

	public void removeRequest(String kind) {
		if (requests.remove(kind) != null) {
			setDirty();
		}
	}

	// ----------------------------------------------------------------- names

	/** The name last seen for this person ("Fern", "Pip"), for listing homes while they are away. */
	public String name(UUID id) {
		return names.getOrDefault(id, "someone");
	}

	public void rememberName(UUID id, String name) {
		if (!name.equals(names.put(id, name))) {
			setDirty();
		}
	}

	public void forgetName(UUID id) {
		if (names.remove(id) != null) {
			setDirty();
		}
	}

	// ------------------------------------------------------------- announced

	/** Marks a one-off announcement as made; true if it had not been made before. */
	public boolean announceOnce(String key) {
		if (announced.add(key)) {
			setDirty();
			return true;
		}
		return false;
	}

	public boolean wasAnnounced(String key) {
		return announced.contains(key);
	}

	public void touch() {
		setDirty();
	}

	// ----------------------------------------------------------- persistence

	private static VillageData fromTag(CompoundTag tag) {
		VillageData data = new VillageData();
		tag.getLongArray("centre").filter(a -> a.length == 1).ifPresent(a -> data.centre = BlockPos.of(a[0]));
		data.dimension = tag.getStringOr("dim", "minecraft:overworld");
		data.planDay = tag.getLongOr("planDay", -1L);
		data.streetLevel = Math.clamp(tag.getIntOr("streetLevel", 0), 0, 2);
		CompoundTag openTag = tag.getCompoundOrEmpty("open");
		for (String key : openTag.keySet()) {
			int[] range = openTag.getIntArray(key).orElse(new int[0]);
			try {
				if (range.length == 2) {
					data.open.put(Integer.parseInt(key), range.clone());
				}
			} catch (NumberFormatException ignored) {
				// a damaged entry is simply dropped
			}
		}
		data.nextPlotId = Math.max(1, tag.getIntOr("nextPlot", 1));
		for (Tag t : tag.getListOrEmpty("plots")) {
			if (!(t instanceof CompoundTag p)) {
				continue;
			}
			int[] box = p.getIntArray("box").orElse(new int[0]);
			String siteKey = p.getStringOr("site", "");
			if (box.length != 4 || siteKey.isEmpty()) {
				continue;
			}
			Plot plot = new Plot(p.getIntOr("id", 0), p.getStringOr("kind", "house"), p.getStringOr("plan", ""), siteKey, box,
				p.getIntOr("street", -1), p.getIntOr("side", 0), p.getIntOr("rot", 0), p.getIntOr("floor", 0),
				p.getStringOr("purpose", ""), p.getLongOr("at", 0L));
			plot.state = p.getBooleanOr("standing", false) ? PlotState.STANDING : PlotState.BUILDING;
			for (Tag u : p.getListOrEmpty("intended")) {
				u.asString().flatMap(VillageData::uuid).ifPresent(plot.intended::add);
			}
			CompoundTag res = p.getCompoundOrEmpty("residents");
			for (String key : res.keySet()) {
				uuid(key).ifPresent(id -> plot.residents.put(id, res.getIntOr(key, -1)));
			}
			data.plots.add(plot);
			data.nextPlotId = Math.max(data.nextPlotId, plot.id + 1);
		}
		for (Tag t : tag.getListOrEmpty("requests")) {
			if (t instanceof CompoundTag r) {
				String kind = r.getStringOr("kind", "");
				if (!kind.isEmpty()) {
					data.requests.put(kind, new Request(kind, r.getStringOr("reason", ""), Math.max(1, r.getIntOr("count", 1)),
						r.getLongOr("at", 0L)));
				}
			}
		}
		CompoundTag namesTag = tag.getCompoundOrEmpty("names");
		for (String key : namesTag.keySet()) {
			uuid(key).ifPresent(id -> data.names.put(id, namesTag.getStringOr(key, "someone")));
		}
		for (Tag t : tag.getListOrEmpty("announced")) {
			t.asString().ifPresent(data.announced::add);
		}
		return data;
	}

	private CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		if (centre != null) {
			tag.putLongArray("centre", new long[] {centre.asLong()});
		}
		tag.putString("dim", dimension);
		tag.putLong("planDay", planDay);
		tag.putInt("streetLevel", streetLevel);
		CompoundTag openTag = new CompoundTag();
		open.forEach((street, range) -> openTag.putIntArray(Integer.toString(street), range.clone()));
		tag.put("open", openTag);
		tag.putInt("nextPlot", nextPlotId);
		ListTag plotList = new ListTag();
		for (Plot plot : plots) {
			CompoundTag p = new CompoundTag();
			p.putInt("id", plot.id);
			p.putString("kind", plot.kind);
			p.putString("plan", plot.planId);
			p.putString("site", plot.siteKey);
			p.putIntArray("box", plot.box.clone());
			p.putInt("street", plot.street);
			p.putInt("side", plot.side);
			p.putInt("rot", plot.rotation);
			p.putInt("floor", plot.floorY);
			p.putString("purpose", plot.purpose);
			p.putLong("at", plot.plannedAt);
			p.putBoolean("standing", plot.state == PlotState.STANDING);
			ListTag intended = new ListTag();
			for (UUID id : plot.intended) {
				intended.add(StringTag.valueOf(id.toString()));
			}
			p.put("intended", intended);
			CompoundTag res = new CompoundTag();
			plot.residents.forEach((id, bed) -> res.putInt(id.toString(), bed));
			p.put("residents", res);
			plotList.add(p);
		}
		tag.put("plots", plotList);
		ListTag requestList = new ListTag();
		for (Request r : requests.values()) {
			CompoundTag t = new CompoundTag();
			t.putString("kind", r.kind());
			t.putString("reason", r.reason());
			t.putInt("count", r.count());
			t.putLong("at", r.at());
			requestList.add(t);
		}
		tag.put("requests", requestList);
		CompoundTag namesTag = new CompoundTag();
		names.forEach((id, name) -> namesTag.putString(id.toString(), name));
		tag.put("names", namesTag);
		ListTag announcedTag = new ListTag();
		for (String key : announced) {
			announcedTag.add(StringTag.valueOf(key));
		}
		tag.put("announced", announcedTag);
		return tag;
	}

	private static Optional<UUID> uuid(String text) {
		try {
			return Optional.of(UUID.fromString(text));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
