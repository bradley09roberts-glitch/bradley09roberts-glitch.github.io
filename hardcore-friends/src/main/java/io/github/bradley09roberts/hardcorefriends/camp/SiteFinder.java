package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Finds and reserves building sites. A site must sit inside the camp (two blocks in from its edge) on firm ground
 * that varies by at most one block, with only air or clearable plants where the building goes, nothing that looks
 * player-built within two blocks, and a one-block gap to every other reserved site. The search starts at the
 * plan's preferred spot and widens ring by ring, a few candidates per tick.
 */
public final class SiteFinder {
	/** Extra parts of multi-part sites (post rows) live in camp memory under this key. */
	public static final String PARTS_MEMORY = "hardcorefriends.build_parts";
	private static final int MAX_RING = 9;
	private static final int MARKER_GAP = 2;
	private static final int NO_GROUND = Integer.MIN_VALUE;

	private SiteFinder() {
	}

	// --------------------------------------------------------------- lookup

	/** The reserved parts of a plan, or an empty list if it has no site yet. */
	public static List<Part> parts(CampData data, Blueprint bp) {
		Optional<CampData.Site> site = data.site(bp.id());
		if (site.isEmpty()) {
			return List.of();
		}
		if (bp.parts() == 1) {
			return List.of(new Part(site.get().origin, site.get().rotation));
		}
		CompoundTag tag = data.memory(PARTS_MEMORY).getCompoundOrEmpty(bp.id());
		long[] origins = tag.getLongArray("origins").orElse(new long[0]);
		int[] rotations = tag.getIntArray("rotations").orElse(new int[0]);
		if (origins.length != bp.parts() || rotations.length != origins.length) {
			return List.of();
		}
		List<Part> parts = new ArrayList<>();
		for (int i = 0; i < origins.length; i++) {
			parts.add(new Part(BlockPos.of(origins[i]), rotations[i]));
		}
		return parts;
	}

	/** Reserves a site so nobody else builds there. Progress starts from zero. */
	public static void reserve(CampData data, Blueprint bp, List<Part> parts) {
		Part first = parts.getFirst();
		if (parts.size() > 1) {
			long[] origins = new long[parts.size()];
			int[] rotations = new int[parts.size()];
			for (int i = 0; i < parts.size(); i++) {
				origins[i] = parts.get(i).origin().asLong();
				rotations[i] = parts.get(i).rotation();
			}
			CompoundTag tag = new CompoundTag();
			tag.putLongArray("origins", origins);
			tag.putIntArray("rotations", rotations);
			data.memory(PARTS_MEMORY).put(bp.id(), tag);
		}
		data.putSite(bp.id(), new CampData.Site(first.origin(), first.rotation(), 0));
	}

	/**
	 * Sites that need no search: additions to the cabin and the hopper on the supply chest. Returns null if the
	 * plan is searched for normally, or an empty list if the fixed site is not available.
	 */
	public static @Nullable List<Part> fixedParts(ServerLevel level, CampData data, Blueprint bp) {
		return switch (bp.anchor()) {
			case CABIN -> data.isCompleted(Structures.CABIN) ? parts(data, Blueprints.CABIN) : List.of();
			case CHEST_TOP -> {
				for (BlockPos half : chestHalves(level, data)) {
					BlockState above = level.getBlockState(half.above());
					if (above.isAir()) {
						yield List.of(new Part(half, 0));
					}
				}
				yield List.of();
			}
			default -> null;
		};
	}

	/** The linked supply chest's block positions (both halves of a double chest), or none. */
	public static List<BlockPos> chestHalves(ServerLevel level, CampData data) {
		Optional<BlockPos> chest = data.chestPos();
		if (chest.isEmpty() || !level.isLoaded(chest.get())) {
			return List.of();
		}
		BlockPos pos = chest.get();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			return List.of(pos, ChestBlock.getConnectedBlockPos(pos, state));
		}
		return List.of(pos);
	}

	/** Horizontal boxes {minX, minZ, maxX, maxZ} of every reserved site except {@code skipId}. */
	public static List<int[]> reservedBoxes(CampData data, String skipId) {
		List<int[]> boxes = new ArrayList<>();
		for (Map.Entry<String, CampData.Site> e : data.sites().entrySet()) {
			String id = e.getKey();
			if (id.equals(skipId)) {
				continue;
			}
			BlockPos o = e.getValue().origin;
			Optional<Blueprint> bp = Blueprints.forId(id);
			if (bp.isPresent()) {
				if (bp.get().anchor() == Blueprint.Anchor.CABIN || bp.get().anchor() == Blueprint.Anchor.CHEST_TOP) {
					continue; // part of another structure
				}
				for (Part part : parts(data, bp.get())) {
					boxes.add(bp.get().footprint(part.origin(), part.rotation()));
				}
			} else if (id.equals(Structures.FARM_PLOT)) {
				boxes.add(new int[] {o.getX() - 4, o.getZ() - 4, o.getX() + 4, o.getZ() + 4});
			} else {
				boxes.add(new int[] {o.getX() - 1, o.getZ() - 1, o.getX() + 1, o.getZ() + 1});
			}
		}
		return boxes;
	}

	// --------------------------------------------------------------- search

	/** Starts a search for a free site for every part of a plan. */
	public static Search search(ServerLevel level, CampData data, Blueprint bp) {
		return new Search(level, data, bp);
	}

	/** An incremental search: call {@link #step} once per tick until it reports a result. */
	public static final class Search {
		private final ServerLevel level;
		private final CampData data;
		private final Blueprint bp;
		private final BlockPos centre;
		private final int radius;
		private final List<int[]> taken;
		private final Set<BlockPos> ignoredMarkers = new HashSet<>();
		private final List<Part> found = new ArrayList<>();
		private BlockPos anchor;
		private int ring;
		private int cell;
		private boolean failed;

		private Search(ServerLevel level, CampData data, Blueprint bp) {
			this.level = level;
			this.data = data;
			this.bp = bp;
			this.centre = data.campPos().orElse(BlockPos.ZERO);
			this.radius = Camp.radius(data) - 2;
			this.taken = reservedBoxes(data, bp.id());
			List<BlockPos> chest = chestHalves(level, data);
			if (bp.anchor() == Blueprint.Anchor.CHEST) {
				ignoredMarkers.addAll(chest); // the supply chest is meant to be right next door
				int[] o = Blueprints.SUPPLY_CHEST.offsets().getFirst();
				this.anchor = chest.isEmpty() ? centre.offset(o[0], 0, o[1]) : chest.getFirst();
			} else {
				this.anchor = centre;
			}
		}

		public boolean failed() {
			return failed;
		}

		/** Checks up to {@code budget} candidates. Returns the parts once every part has a site, else null. */
		public @Nullable List<Part> step(int budget) {
			if (failed) {
				return null;
			}
			while (budget-- > 0) {
				int part = found.size();
				if (part >= bp.parts()) {
					return found;
				}
				int[] offset = bp.offsets().get(part);
				int[] cellOffset = ringCell(ring, cell);
				if (cellOffset == null) {
					ring++;
					cell = 0;
					if (ring > MAX_RING) {
						failed = true;
						return null;
					}
					continue;
				}
				cell++;
				BlockPos footprintCentre = anchor.offset(offset[0] + cellOffset[0], 0, offset[1] + cellOffset[1]);
				int rotation = bp.facesCentre() ? Blueprint.rotationFacing(directionTo(footprintCentre, centre)) : 0;
				Part candidate = check(footprintCentre, rotation);
				if (candidate != null) {
					found.add(candidate);
					taken.add(bp.footprint(candidate.origin(), candidate.rotation()));
					ring = 0;
					cell = 0;
				}
			}
			return found.size() >= bp.parts() ? found : null;
		}

		/** Returns the part placed here if this footprint is a good site, else null. */
		private @Nullable Part check(BlockPos footprintCentre, int rotation) {
			BlockPos flatOrigin = bp.originFor(footprintCentre, rotation);
			int[] box = bp.footprint(flatOrigin, rotation);
			// Inside the camp, with room to spare, and away from the centre itself.
			for (int[] corner : new int[][] {{box[0], box[1]}, {box[0], box[3]}, {box[2], box[1]}, {box[2], box[3]}}) {
				double dx = corner[0] - centre.getX();
				double dz = corner[1] - centre.getZ();
				if (dx * dx + dz * dz > (double) radius * radius || !level.hasChunkAt(corner[0], corner[1])) {
					return null;
				}
			}
			if (centre.getX() >= box[0] - 1 && centre.getX() <= box[2] + 1 && centre.getZ() >= box[1] - 1 && centre.getZ() <= box[3] + 1) {
				return null;
			}
			boolean single = box[0] == box[2] && box[1] == box[3];
			for (int[] other : taken) {
				// Single blocks of camp furniture (chest, table, furnace) may stand side by side; anything else keeps a gap.
				int gap = single && other[0] == other[2] && other[1] == other[3] ? 0 : 1;
				if (box[0] - gap <= other[2] && box[2] + gap >= other[0] && box[1] - gap <= other[3] && box[3] + gap >= other[1]) {
					return null;
				}
			}
			// Firm, nearly level ground.
			int width = box[2] - box[0] + 1;
			int depth = box[3] - box[1] + 1;
			int[] ground = new int[width * depth];
			int top = Integer.MIN_VALUE;
			int bottom = Integer.MAX_VALUE;
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < depth; z++) {
					int g = groundY(box[0] + x, box[1] + z, footprintCentre.getY());
					if (g == NO_GROUND) {
						return null;
					}
					ground[x * depth + z] = g;
					top = Math.max(top, g);
					bottom = Math.min(bottom, g);
				}
			}
			if (top - bottom > (bp.hasFoundations() ? 1 : 0) || Math.abs(top + 1 - centre.getY()) > 8) {
				return null;
			}
			int floor = top + 1;
			// The building's space must be empty apart from plants.
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < depth; z++) {
					for (int y = ground[x * depth + z] + 1; y < floor + bp.height(); y++) {
						m.set(box[0] + x, y, box[1] + z);
						BlockState s = level.getBlockState(m);
						if (!(s.isAir() || WorldEditGuard.isClearablePlant(s)) || !s.getFluidState().isEmpty()) {
							return null;
						}
					}
				}
			}
			if (nearPlayerBuild(box, floor - 1, floor + bp.height())) {
				return null;
			}
			return new Part(new BlockPos(flatOrigin.getX(), floor, flatOrigin.getZ()), rotation);
		}

		private boolean nearPlayerBuild(int[] box, int minY, int maxY) {
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int x = box[0] - MARKER_GAP; x <= box[2] + MARKER_GAP; x++) {
				for (int z = box[1] - MARKER_GAP; z <= box[3] + MARKER_GAP; z++) {
					for (int y = minY - 1; y <= maxY + 1; y++) {
						m.set(x, y, z);
						if (!level.isLoaded(m)) {
							continue;
						}
						BlockState s = level.getBlockState(m);
						if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(m)
							&& !ignoredMarkers.contains(m)) {
							return true;
						}
					}
				}
			}
			return false;
		}

		/** Top of the natural ground in a column near {@code nearY}, or {@link #NO_GROUND}. */
		private int groundY(int x, int z, int nearY) {
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int y = nearY + 3; y >= nearY - 5; y--) {
				m.set(x, y, z);
				BlockState s = level.getBlockState(m);
				if (s.isAir() || WorldEditGuard.isClearablePlant(s) && s.getFluidState().isEmpty()) {
					continue;
				}
				if (!s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || !s.isFaceSturdy(level, m, Direction.UP)) {
					return NO_GROUND;
				}
				return y;
			}
			return NO_GROUND;
		}
	}

	/** The main horizontal direction from one position towards another. */
	public static Direction directionTo(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** The {@code index}-th cell on the square ring at distance {@code ring}, or null past the last cell. */
	private static int @Nullable [] ringCell(int ring, int index) {
		if (ring == 0) {
			return index == 0 ? new int[] {0, 0} : null;
		}
		int side = 2 * ring;
		if (index >= 4 * side) {
			return null;
		}
		int leg = index / side;
		int along = index % side;
		return switch (leg) {
			case 0 -> new int[] {-ring + along, -ring};
			case 1 -> new int[] {ring, -ring + along};
			case 2 -> new int[] {ring - along, ring};
			default -> new int[] {-ring, ring - along};
		};
	}
}
