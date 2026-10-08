package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;

/**
 * A code-defined building plan for one camp improvement (a {@link Structures} id). The plan is drawn facing north
 * (its front, where the door is, on the low-z side) on a {@code width × depth} footprint; a reserved site rotates
 * it so the front faces the camp centre. Entries are placed bottom-up, with attachments (doors, torches, ladders,
 * pressure plates) after everything that holds them up.
 *
 * <p>Some improvements are several small copies of one plan, such as four torch posts: they list one preferred
 * offset per copy. Each copy is a "part" with its own origin.
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

	/** One block of the plan in local coordinates, with the tweak that sets its facing, half or shape. */
	public record Entry(int dx, int dy, int dz, MaterialSpec material, UnaryOperator<BlockState> tweak, boolean attachment) {
		/** The block this entry becomes when built from the chosen item on a site with the given rotation. */
		public BlockState stateFor(ItemStack chosen, Rotation rotation) {
			return tweak.apply(material.baseState(chosen)).rotate(rotation);
		}
	}

	private final String id;
	private final int width;
	private final int depth;
	private final int height;
	private final Anchor anchor;
	private final List<int[]> offsets;
	private final boolean faceCentre;
	private final boolean foundations;
	private final List<Entry> entries;

	private Blueprint(Builder b) {
		this.id = b.id;
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
		this.height = top;
	}

	public static Builder builder(String id, int width, int depth) {
		return new Builder(id, width, depth);
	}

	/** The {@link Structures} id this plan builds. */
	public String id() {
		return id;
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
		private Anchor anchor = Anchor.CENTRE;
		private final List<int[]> offsets = new ArrayList<>();
		private boolean faceCentre = true;
		private boolean foundations = true;
		private final List<Entry> entries = new ArrayList<>();

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

		public Blueprint build() {
			if (offsets.isEmpty()) {
				offsets.add(new int[] {0, 0});
			}
			return new Blueprint(this);
		}
	}
}
