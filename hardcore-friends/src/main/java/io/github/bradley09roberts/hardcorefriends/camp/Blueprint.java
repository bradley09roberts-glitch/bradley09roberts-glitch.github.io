package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;

/**
 * A building plan: either one of the camp's own improvements (a {@link Structures} id) or a plan from the building
 * library (a house, a shop, a workplace...). The plan is drawn facing north (its front, where the door is, on the
 * low-z side) on a {@code width × depth} footprint; a reserved site rotates it, usually so the front faces the camp
 * centre. Entries are placed bottom-up, with attachments (doors, windows, furniture, lights) after everything that
 * holds them up.
 *
 * <p>Some improvements are several small copies of one plan, such as four torch posts: they list one preferred
 * offset per copy. Each copy is a "part" with its own origin.
 *
 * <p>Library plans also carry what the rest of the mod needs to know about them: a {@link #kind()} such as
 * {@code house} or {@code shop:bakery}, {@link #styles()} such as {@code spruce} or {@code snowy}, named
 * {@link #markers()} (the door, each bed, the chest, a shop counter...) and free-form {@link #meta()} such as the
 * number of beds.
 */
public final class Blueprint {
	/** What a blueprint's preferred offsets are measured from. */
	public enum Anchor {
		/** The camp centre. */
		CENTRE,
		/** The supply chest (or, without one, where Oak puts the supply chest). */
		CHEST,
		/** Directly on top of the supply chest, without a site search. */
		CHEST_TOP,
		/** Shares the cabin's site, for additions to the cabin. */
		CABIN
	}

	/**
	 * What an entry may be built from instead when its own material cannot be had (a torch instead of a lantern): its
	 * material, the tweak that sets its facing or shape, and the wood or colour it would like best.
	 */
	public record Alternative(MaterialSpec material, UnaryOperator<BlockState> tweak, @Nullable String variant) {
	}

	/**
	 * One block of the plan in local coordinates, with the tweak that sets its facing, half or shape.
	 *
	 * @param variant  the wood ("spruce") or colour ("red") this entry would like best, or null for the plan's own wood
	 * @param optional true for decoration that is left out while its material cannot be had (a flower pot without
	 *                 bricks, a window without glass), so it never holds a building up; the repair job adds it later
	 * @param fallback what to build instead when the material cannot be had, or null
	 */
	public record Entry(int dx, int dy, int dz, MaterialSpec material, UnaryOperator<BlockState> tweak, boolean attachment,
		@Nullable String variant, boolean optional, @Nullable Alternative fallback) {

		/** A plain entry with no preference, never optional and with nothing to fall back on. */
		public Entry(int dx, int dy, int dz, MaterialSpec material, UnaryOperator<BlockState> tweak, boolean attachment) {
			this(dx, dy, dz, material, tweak, attachment, null, false, null);
		}

		/** The block this entry becomes when built from the chosen item on a site with the given rotation. */
		public BlockState stateFor(ItemStack chosen, Rotation rotation) {
			return tweak.apply(material.baseState(chosen)).rotate(rotation);
		}

		/** True if the block in the world fulfils this entry, with its own material or its fallback. */
		public boolean isBuilt(BlockState state) {
			return material.isBuilt(state) || fallback != null && fallback.material().isBuilt(state);
		}

		/** This entry made of its fallback instead (same place, same build order), or this entry if it has none. */
		public Entry withFallback() {
			Alternative alt = fallback;
			if (alt == null) {
				return this;
			}
			return new Entry(dx, dy, dz, alt.material(), alt.tweak(), attachment, alt.variant(), optional, null);
		}
	}

	private final String id;
	private final String planId;
	private final String name;
	private final String kind;
	private final List<String> styles;
	private final @Nullable String wood;
	private final int width;
	private final int depth;
	private final int height;
	private final Anchor anchor;
	private final List<int[]> offsets;
	private final boolean faceCentre;
	private final boolean foundations;
	private final List<Entry> entries;
	private final Map<String, List<int[]>> markers;
	private final Map<String, String> meta;

	private Blueprint(Builder b) {
		this.id = b.id;
		this.planId = b.planId == null ? b.id : b.planId;
		this.name = b.name == null ? b.id.replace('_', ' ') : b.name;
		this.kind = b.kind;
		this.styles = List.copyOf(b.styles);
		this.wood = b.wood;
		this.width = b.width;
		this.depth = b.depth;
		this.anchor = b.anchor;
		this.offsets = List.copyOf(b.offsets);
		this.faceCentre = b.faceCentre;
		this.foundations = b.foundations;
		List<Entry> sorted = new ArrayList<>(b.entries);
		// Stable sort keeps the drawing order inside one layer (a door's lower half before its top half).
		sorted.sort(Comparator.comparing(Entry::attachment).thenComparingInt(Entry::dy));
		this.entries = List.copyOf(sorted);
		int top = 0;
		for (Entry e : sorted) {
			top = Math.max(top, e.dy() + 1);
		}
		this.height = Math.max(top, b.height);
		Map<String, List<int[]>> m = new LinkedHashMap<>();
		b.markers.forEach((k, v) -> m.put(k, List.copyOf(v)));
		this.markers = Map.copyOf(m);
		this.meta = Map.copyOf(b.meta);
	}

	public static Builder builder(String id, int width, int depth) {
		return new Builder(id, width, depth);
	}

	/**
	 * The plan's default site key: the {@link Structures} id for the camp's own improvements, the plan's library id
	 * ({@code hardcorefriends:house/oak_cottage}) for library plans.
	 */
	public String id() {
		return id;
	}

	/**
	 * Which version of which plan this is, as remembered with a reserved site ({@code camp/cabin}, {@code legacy/cabin},
	 * or a library id), so a site keeps the plan it was started with.
	 */
	public String planId() {
		return planId;
	}

	/** A readable name in lower case, for speech ("cabin", "oak cottage"). */
	public String name() {
		return name;
	}

	/** What the plan is for: {@code house}, {@code shop:bakery}, {@code civic:well}, {@code camp:cabin}... */
	public String kind() {
		return kind;
	}

	/** Looks and places the plan suits: woods ({@code spruce}), stone kinds ({@code sandstone}), biomes ({@code snowy}). */
	public List<String> styles() {
		return styles;
	}

	/** The wood this plan would like for its wooden parts ("spruce"), or null for whatever wood the camp has. */
	public @Nullable String wood() {
		return wood;
	}

	public int width() {
		return width;
	}

	public int depth() {
		return depth;
	}

	/** Number of layers from the ground-level layer (dy = 0) to the top. */
	public int height() {
		return height;
	}

	public Anchor anchor() {
		return anchor;
	}

	/** Preferred footprint-centre offsets (x, z) from the anchor, one per part. */
	public List<int[]> offsets() {
		return offsets;
	}

	public int parts() {
		return offsets.size();
	}

	/** True if the site is turned so the plan's front faces the camp centre. */
	public boolean facesCentre() {
		return faceCentre;
	}

	/** True if a one-block dip under the footprint is filled with dirt or cobblestone before building. */
	public boolean hasFoundations() {
		return foundations;
	}

	/** Ordered entries of one part: structure bottom-up, then attachments bottom-up. */
	public List<Entry> entries() {
		return entries;
	}

	/** Named positions in local coordinates ({x, y, z}): {@code door}, {@code bed}, {@code chest}, {@code counter}... */
	public Map<String, List<int[]>> markers() {
		return markers;
	}

	/** The local positions of one marker (empty if the plan has none). */
	public List<int[]> marker(String name) {
		return markers.getOrDefault(name, List.of());
	}

	/** Free-form facts about the plan ({@code beds}, {@code capacity}, {@code profession}...), as text. */
	public Map<String, String> meta() {
		return meta;
	}

	/** A whole-number fact from {@link #meta()}, or {@code fallback} if missing or not a number. */
	public int metaInt(String key, int fallback) {
		String v = meta.get(key);
		if (v == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(v.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** World positions of a marker for a part with this origin and rotation. */
	public List<BlockPos> markerPositions(String name, BlockPos origin, int rotation) {
		List<BlockPos> list = new ArrayList<>();
		for (int[] p : marker(name)) {
			list.add(worldPos(origin, rotation, p[0], p[1], p[2]));
		}
		return list;
	}

	/** The same plan under another site key, plan id and preferred spots (the second cabin is the cabin again). */
	public Blueprint copyAs(String newId, String newPlanId, List<int[]> newOffsets) {
		Builder b = new Builder(newId, width, depth);
		b.planId = newPlanId;
		b.name = null;
		b.kind = kind;
		b.styles.addAll(styles);
		b.wood = wood;
		b.anchor = anchor;
		b.offsets.addAll(newOffsets);
		b.faceCentre = faceCentre;
		b.foundations = foundations;
		b.entries.addAll(entries);
		b.height = height;
		markers.forEach((k, v) -> b.markers.put(k, new ArrayList<>(v)));
		b.meta.putAll(meta);
		return b.build();
	}

	// --------------------------------------------------------------- geometry

	/** Site rotation 0–3 as a vanilla {@link Rotation} (1 = clockwise 90°, so north becomes east). */
	public static Rotation rotation(int rotation) {
		return switch (Math.floorMod(rotation, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** The rotation that turns the plan's front (north) to face {@code direction}. */
	public static int rotationFacing(Direction direction) {
		return switch (direction) {
			case EAST -> 1;
			case SOUTH -> 2;
			case WEST -> 3;
			default -> 0;
		};
	}

	/** World position of a local block for a part with this origin and rotation. */
	public static BlockPos worldPos(BlockPos origin, int rotation, int dx, int dy, int dz) {
		return switch (Math.floorMod(rotation, 4)) {
			case 1 -> origin.offset(-dz, dy, dx);
			case 2 -> origin.offset(-dx, dy, -dz);
			case 3 -> origin.offset(dz, dy, -dx);
			default -> origin.offset(dx, dy, dz);
		};
	}

	/** The origin that puts the footprint's centre on {@code centre} for the given rotation. */
	public BlockPos originFor(BlockPos centre, int rotation) {
		BlockPos rotatedCentre = worldPos(BlockPos.ZERO, rotation, (width - 1) / 2, 0, (depth - 1) / 2);
		return centre.subtract(rotatedCentre);
	}

	/** Lowest corner (x, z) and highest corner of the rotated footprint, as {minX, minZ, maxX, maxZ}. */
	public int[] footprint(BlockPos origin, int rotation) {
		BlockPos a = worldPos(origin, rotation, 0, 0, 0);
		BlockPos b = worldPos(origin, rotation, width - 1, 0, depth - 1);
		return new int[] {Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
			Math.max(a.getZ(), b.getZ())};
	}

	// ---------------------------------------------------------------- builder

	/** Fluent construction of a plan. */
	public static final class Builder {
		private final String id;
		private final int width;
		private final int depth;
		private @Nullable String planId;
		private @Nullable String name;
		private String kind = "camp";
		private final List<String> styles = new ArrayList<>();
		private @Nullable String wood;
		private Anchor anchor = Anchor.CENTRE;
		private final List<int[]> offsets = new ArrayList<>();
		private boolean faceCentre = true;
		private boolean foundations = true;
		private final List<Entry> entries = new ArrayList<>();
		private int height;
		private final Map<String, List<int[]>> markers = new LinkedHashMap<>();
		private final Map<String, String> meta = new LinkedHashMap<>();

		private Builder(String id, int width, int depth) {
			this.id = id;
			this.width = width;
			this.depth = depth;
		}

		public Builder anchor(Anchor anchor) {
			this.anchor = anchor;
			if (anchor == Anchor.CHEST_TOP || anchor == Anchor.CABIN) {
				this.foundations = false;
			}
			return this;
		}

		/** Adds one copy whose footprint centre sits at this offset from the anchor. */
		public Builder at(int x, int z) {
			offsets.add(new int[] {x, z});
			return this;
		}

		public Builder fixedFacing() {
			this.faceCentre = false;
			return this;
		}

		/** Which version of which plan this is (see {@link Blueprint#planId()}). */
		public Builder planId(String planId) {
			this.planId = planId;
			return this;
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder kind(String kind) {
			this.kind = kind;
			return this;
		}

		public Builder style(String style) {
			this.styles.add(style);
			return this;
		}

		public Builder wood(@Nullable String wood) {
			this.wood = wood;
			return this;
		}

		public Builder foundations(boolean foundations) {
			this.foundations = foundations;
			return this;
		}

		/** At least this many layers, even if the top ones hold no entries. */
		public Builder minHeight(int height) {
			this.height = Math.max(this.height, height);
			return this;
		}

		public Builder marker(String name, int x, int y, int z) {
			markers.computeIfAbsent(name, k -> new ArrayList<>()).add(new int[] {x, y, z});
			return this;
		}

		public Builder meta(String key, String value) {
			meta.put(key, value);
			return this;
		}

		public Builder put(int dx, int dy, int dz, MaterialSpec material) {
			return put(dx, dy, dz, material, UnaryOperator.identity());
		}

		public Builder put(int dx, int dy, int dz, MaterialSpec material, UnaryOperator<BlockState> tweak) {
			entries.add(new Entry(dx, dy, dz, material, tweak, false));
			return this;
		}

		/** An entry that hangs on or stands on other entries; placed after the main structure. */
		public Builder attach(int dx, int dy, int dz, MaterialSpec material, UnaryOperator<BlockState> tweak) {
			entries.add(new Entry(dx, dy, dz, material, tweak, true));
			return this;
		}

		public Builder attach(int dx, int dy, int dz, MaterialSpec material) {
			return attach(dx, dy, dz, material, UnaryOperator.identity());
		}

		/** Adds a fully described entry (from a plan file). */
		public Builder entry(Entry entry) {
			entries.add(entry);
			return this;
		}

		public Blueprint build() {
			if (offsets.isEmpty()) {
				offsets.add(new int[] {0, 0});
			}
			return new Blueprint(this);
		}
	}
}
