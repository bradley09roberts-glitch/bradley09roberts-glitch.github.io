package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.TreeFinder;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Finds and reserves building sites. A site must sit inside the camp (two blocks in from its edge) on firm, natural
 * ground (never a player's floor or roof) that varies by at most one block, with only air or clearable plants where
 * the building goes, nothing that looks player-built within two blocks, and a one-block gap to every other reserved
 * site. The search starts at the plan's preferred spot and widens ring by ring across the whole camp, a few
 * candidates per tick, counting why spots were turned down so a failed search can be explained.
 *
 * <p>When no such spot exists, a second pass accepts a few natural trees to fell ({@link SiteClearing}), and a third
 * accepts uneven natural ground to level, choosing the site that needs the least digging and filling
 * ({@link SiteGrading}).
 *
 * <p>A search runs over many ticks while others reserve sites too (the village plans its plots meanwhile), so each
 * step looks at the reserved sites as they are now, a best spot remembered from earlier in a pass is checked again
 * before it is taken, and the builder checks the parts once more before reserving them ({@link #isFree}).
 */
public final class SiteFinder {
	/** Extra parts of multi-part sites (post rows) live in camp memory under this key. */
	public static final String PARTS_MEMORY = "hardcorefriends.build_parts";
	/** The search always covers at least this many rings around the preferred spot, and then the whole camp. */
	private static final int MAX_RING = 9;
	/** Spots outside the camp or on another site are cheap to rule out; at most this many per step. */
	private static final int CHEAP_CHECKS_PER_STEP = 256;
	private static final int MARKER_GAP = 2;
	private static final int NO_GROUND = Integer.MIN_VALUE;
	private static final int TREE_IN_THE_WAY = Integer.MIN_VALUE + 1;
	/** A site's floor stays within this many blocks of the camp's height, so ground is only looked for in that band. */
	private static final int MAX_RISE = 8;
	/** Most natural trees a site may need felled. */
	private static final int MAX_SITE_TREES = 4;
	/** A levelled site keeps this far from anything player-built (digging reaches further than building). */
	private static final int GRADE_MARKER_GAP = 3;
	/** A site needing no more than this many blocks dug and filled is taken without looking further. */
	private static final int GRADE_GOOD_ENOUGH = 4;

	/**
	 * Ground other packages keep clear of the camp's own buildings, given the level and a footprint {minX, minZ, maxX,
	 * maxZ}: true rules the footprint out (the village's streets and lamp spots, once its town plan is laid out).
	 * Checked for every candidate a search makes, so each must be cheap.
	 */
	public static final List<BiPredicate<ServerLevel, int[]>> KEEP_CLEAR = new CopyOnWriteArrayList<>();

	private SiteFinder() {
	}

	// --------------------------------------------------------------- lookup

	/** The reserved parts of a plan, or an empty list if it has no site yet. */
	public static List<Part> parts(CampData data, Blueprint bp) {
		return parts(data, bp.id(), bp);
	}

	/** The reserved parts of the site with this key, built from this plan, or an empty list if there is no site. */
	public static List<Part> parts(CampData data, String siteKey, Blueprint bp) {
		Optional<CampData.Site> site = data.site(siteKey);
		if (site.isEmpty()) {
			return List.of();
		}
		if (bp.parts() == 1) {
			return List.of(new Part(site.get().origin, site.get().rotation));
		}
		CompoundTag tag = data.memory(PARTS_MEMORY).getCompoundOrEmpty(siteKey);
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
		reserve(data, bp.id(), bp, parts, bp.wood());
	}

	/**
	 * Reserves a site under a key (a structure id, or a library site's own key such as {@code village.house.3}),
	 * remembering the plan and the wood its wooden parts should prefer. Progress starts from zero.
	 */
	public static void reserve(CampData data, String siteKey, Blueprint bp, List<Part> parts, @Nullable String wood) {
		SiteClearing.forget(data, siteKey); // a fresh site starts with nothing to fell
		SiteGrading.forget(data, siteKey); // ... and nothing to level
		Blueprints.recordPlan(data, siteKey, bp, wood);
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
			data.memory(PARTS_MEMORY).put(siteKey, tag);
		}
		data.putSite(siteKey, new CampData.Site(first.origin(), first.rotation(), 0));
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
			Optional<Blueprint> bp = Blueprints.forSite(data, id);
			if (bp.isPresent()) {
				if (bp.get().anchor() == Blueprint.Anchor.CABIN || bp.get().anchor() == Blueprint.Anchor.CHEST_TOP) {
					continue; // part of another structure
				}
				for (Part part : parts(data, id, bp.get())) {
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

	/**
	 * True if none of these parts touches another reserved site now (with the usual one-block gap): a search spans many
	 * ticks, so the builder checks again just before reserving what it found.
	 */
	public static boolean isFree(CampData data, String siteKey, Blueprint bp, List<Part> parts) {
		List<int[]> others = reservedBoxes(data, siteKey);
		for (Part part : parts) {
			if (clashes(bp.footprint(part.origin(), part.rotation()), others)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True if a footprint touches any of these boxes. Single blocks of camp furniture (chest, table, furnace) may stand
	 * side by side; anything else keeps a one-block gap.
	 */
	private static boolean clashes(int[] box, List<int[]> others) {
		boolean single = box[0] == box[2] && box[1] == box[3];
		for (int[] other : others) {
			int gap = single && other[0] == other[2] && other[1] == other[3] ? 0 : 1;
			if (box[0] - gap <= other[2] && box[2] + gap >= other[0] && box[1] - gap <= other[3] && box[3] + gap >= other[1]) {
				return true;
			}
		}
		return false;
	}

	// --------------------------------------------------------------- search

	/** Starts a search for a free site for every part of a plan. */
	public static Search search(ServerLevel level, CampData data, Blueprint bp) {
		return new Search(level, data, bp);
	}

	/** Why a candidate spot was turned down, counted so a failed search can say what got in the way. */
	public enum Reject {
		/** Outside the camp, on the camp centre, or overlapping another reserved site: cheap to rule out. */
		NO_ROOM("there is no room left in the camp"),
		NO_GROUND("the ground is not natural (water or player floors)"),
		UNEVEN("the ground is too uneven"),
		TREES("trees are in the way (too many, too tall, or by your builds)"),
		BLOCKED("blocks are in the way"),
		PLAYER_BUILD("they are too close to things you built");

		private final String words;

		Reject(String words) {
			this.words = words;
		}
	}

	/** An incremental search: call {@link #step} once per tick until it reports a result. */
	public static final class Search {
		private final ServerLevel level;
		private final CampData data;
		private final Blueprint bp;
		private final BlockPos centre;
		private final int radius;
		/** Other sites' footprints and the parts found so far: read afresh every step. */
		private List<int[]> taken;
		private final Set<BlockPos> ignoredMarkers = new HashSet<>();
		private final List<Part> found = new ArrayList<>();
		private final int maxRing;
		private final int[] rejected = new int[Reject.values().length];
		private BlockPos anchor;
		private int ring;
		private int cell;
		private boolean failed;
		private @Nullable Reject lastReject;
		/** Second pass: natural trees on a site are allowed, to be felled first. Only when no tree-free site exists. */
		private final boolean treesAllowed;
		private boolean secondPass;
		private final Map<BlockPos, TreeFinder.Tree> treeByLog = new HashMap<>();
		private final Set<BlockPos> notFellable = new HashSet<>();
		private final Set<BlockPos> candidateTrees = new HashSet<>();
		private boolean candidateLeaves;
		private @Nullable Part best;
		private int bestTrees = Integer.MAX_VALUE;
		private List<BlockPos> bestLogs = List.of();
		private int[] bestClearBox = new int[0];
		private List<BlockPos> logsToFell = List.of();
		private int[] clearBox = new int[0];
		/** Which pass the search is on: 1 flat and clear, 2 with trees to fell, 3 with ground to level. */
		private int pass = 1;
		/** Third pass: uneven natural ground is allowed, to be levelled first. Only when no other site exists. */
		private final boolean gradingAllowed;
		private boolean gradePass;
		private @Nullable Part bestGrade;
		private int bestGradeCost = Integer.MAX_VALUE;
		private List<BlockPos> bestCut = List.of();
		private List<BlockPos> bestFill = List.of();
		private List<BlockPos> gradeCut = List.of();
		private List<BlockPos> gradeFill = List.of();

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
			// Rings around the preferred spot, out until they cover the whole camp.
			int reach = 0;
			for (int[] offset : bp.offsets()) {
				reach = Math.max(reach, Math.max(Math.abs(offset[0]), Math.abs(offset[1])));
			}
			reach += Math.max(Math.abs(anchor.getX() - centre.getX()), Math.abs(anchor.getZ() - centre.getZ()));
			this.maxRing = Math.max(MAX_RING, radius + reach);
			this.treesAllowed = FriendsConfig.get().allowTreeFelling && bp.parts() == 1 && bp.hasFoundations();
			FriendsConfig cfg = FriendsConfig.get();
			this.gradingAllowed = cfg.allowTerraforming && cfg.allowWorldEditing && cfg.maxGradeDepth > 0
				&& bp.parts() == 1 && bp.hasFoundations();
		}

		/** The natural blocks to dig away to level the found site (often none), top-down order not implied. */
		public List<BlockPos> gradeCut() {
			return gradeCut;
		}

		/** The spaces to fill to level the found site (often none). */
		public List<BlockPos> gradeFill() {
			return gradeFill;
		}

		/** The logs of the natural trees standing on the found site, which must be felled before building (often none). */
		public List<BlockPos> logsToFell() {
			return logsToFell;
		}

		/** The building's space on the found site, {minX, minY, minZ, maxX, maxY, maxZ}, to clear of leaves. */
		public int[] clearBox() {
			return clearBox;
		}

		public boolean failed() {
			return failed;
		}

		/** Plain words for what most often ruled spots out, for a builder to explain a failed search. */
		public String problem() {
			Reject top = null;
			int total = 0;
			for (Reject r : Reject.values()) {
				int n = rejected[r.ordinal()];
				total += r == Reject.NO_ROOM ? 0 : n;
				if (r != Reject.NO_ROOM && n > 0 && (top == null || n > rejected[top.ordinal()])) {
					top = r;
				}
			}
			if (top == null) {
				return Reject.NO_ROOM.words;
			}
			return "at " + total + " spots I tried, " + top.words;
		}

		/** Every reason with its count, for the server log. */
		public String breakdown() {
			StringBuilder b = new StringBuilder();
			for (Reject r : Reject.values()) {
				b.append(b.isEmpty() ? "" : ", ").append(r.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(rejected[r.ordinal()]);
			}
			return b.toString();
		}

		/** How often each reason ruled a spot out (for tests and diagnostics). */
		public int rejected(Reject reason) {
			return rejected[reason.ordinal()];
		}

		/** Checks up to {@code budget} candidates. Returns the parts once every part has a site, else null. */
		public @Nullable List<Part> step(int budget) {
			if (failed) {
				return null;
			}
			refreshTaken(); // a site may have been reserved since the last step
			// Spots outside the camp or on another site cost almost nothing to rule out, so only real ground checks
			// count against the budget (with a cap on the cheap ones too).
			int cheap = 0;
			while (budget > 0 && cheap < CHEAP_CHECKS_PER_STEP) {
				int part = found.size();
				if (part >= bp.parts()) {
					return found;
				}
				int[] offset = bp.offsets().get(part);
				int[] cellOffset = ringCell(ring, cell);
				if (cellOffset == null) {
					ring++;
					cell = 0;
					if (ring > maxRing) {
						if (pass == 1 && treesAllowed && found.isEmpty()) {
							// No tree-free spot anywhere: look again, accepting a few natural trees to fell.
							pass = 2;
							secondPass = true;
							ring = 0;
							cell = 0;
							continue;
						}
						if (pass == 2 && best != null && !stillFree(best)) {
							// Another site was reserved on the best spot since it was found: look again.
							best = null;
							bestTrees = Integer.MAX_VALUE;
							ring = 0;
							cell = 0;
							continue;
						}
						if (pass == 2 && best != null) {
							logsToFell = bestLogs;
							clearBox = bestClearBox;
							found.add(best);
							return found;
						}
						if (pass < 3 && gradingAllowed && found.isEmpty()) {
							// No level spot anywhere: look again, accepting uneven natural ground to dig down and fill up.
							pass = 3;
							secondPass = false;
							gradePass = true;
							ring = 0;
							cell = 0;
							continue;
						}
						if (pass == 3 && bestGrade != null && !stillFree(bestGrade)) {
							bestGrade = null;
							bestGradeCost = Integer.MAX_VALUE;
							ring = 0;
							cell = 0;
							continue;
						}
						if (pass == 3 && bestGrade != null) {
							gradeCut = bestCut;
							gradeFill = bestFill;
							found.add(bestGrade);
							return found;
						}
						failed = true;
						return null;
					}
					continue;
				}
				cell++;
				BlockPos footprintCentre = anchor.offset(offset[0] + cellOffset[0], 0, offset[1] + cellOffset[1]);
				int rotation = bp.facesCentre() ? Blueprint.rotationFacing(directionTo(footprintCentre, centre)) : 0;
				lastReject = null;
				Part candidate = check(footprintCentre, rotation);
				if (lastReject != null) {
					rejected[lastReject.ordinal()]++;
				}
				if (lastReject == Reject.NO_ROOM) {
					cheap++;
				} else {
					budget--;
				}
				if (candidate != null) {
					found.add(candidate);
					taken.add(bp.footprint(candidate.origin(), candidate.rotation()));
					ring = 0;
					cell = 0;
				}
			}
			return found.size() >= bp.parts() ? found : null;
		}

		/** Reads the other sites' footprints as they are now, and adds the parts found so far. */
		private void refreshTaken() {
			List<int[]> now = reservedBoxes(data, bp.id());
			for (Part part : found) {
				now.add(bp.footprint(part.origin(), part.rotation()));
			}
			taken = now;
		}

		/** True if a part remembered earlier in the search still keeps clear of every site reserved since. */
		private boolean stillFree(Part part) {
			return !clashes(bp.footprint(part.origin(), part.rotation()), taken);
		}

		/**
		 * Returns the part placed here if this footprint is a good site, else null. On the second pass a site with
		 * trees is remembered as the best so far (fewest trees) instead, and the search runs on.
		 */
		private @Nullable Part check(BlockPos footprintCentre, int rotation) {
			BlockPos flatOrigin = bp.originFor(footprintCentre, rotation);
			int[] box = bp.footprint(flatOrigin, rotation);
			candidateTrees.clear();
			candidateLeaves = false;
			// Inside the camp, with room to spare, and away from the centre itself.
			for (int[] corner : new int[][] {{box[0], box[1]}, {box[0], box[3]}, {box[2], box[1]}, {box[2], box[3]}}) {
				double dx = corner[0] - centre.getX();
				double dz = corner[1] - centre.getZ();
				if (dx * dx + dz * dz > (double) radius * radius || !level.hasChunkAt(corner[0], corner[1])) {
					return reject(Reject.NO_ROOM);
				}
			}
			if (centre.getX() >= box[0] - 1 && centre.getX() <= box[2] + 1 && centre.getZ() >= box[1] - 1 && centre.getZ() <= box[3] + 1) {
				return reject(Reject.NO_ROOM);
			}
			if (clashes(box, taken)) {
				return reject(Reject.NO_ROOM);
			}
			for (BiPredicate<ServerLevel, int[]> keepClear : KEEP_CLEAR) {
				if (keepClear.test(level, box)) {
					return reject(Reject.NO_ROOM);
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
					if (g == TREE_IN_THE_WAY) {
						return reject(Reject.TREES);
					}
					if (g == NO_GROUND) {
						return reject(Reject.NO_GROUND);
					}
					ground[x * depth + z] = g;
					top = Math.max(top, g);
					bottom = Math.min(bottom, g);
				}
			}
			if (gradePass) {
				return checkGraded(box, ground, top, bottom, flatOrigin, rotation, footprintCentre.getY());
			}
			if (top - bottom > (bp.hasFoundations() ? 1 : 0) || Math.abs(top + 1 - centre.getY()) > MAX_RISE) {
				return reject(Reject.UNEVEN);
			}
			int floor = top + 1;
			// The building's space must be empty apart from plants.
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < depth; z++) {
					for (int y = ground[x * depth + z] + 1; y < floor + bp.height(); y++) {
						m.set(box[0] + x, y, box[1] + z);
						BlockState s = level.getBlockState(m);
						if ((s.isAir() || WorldEditGuard.isClearablePlant(s)) && s.getFluidState().isEmpty()) {
							continue;
						}
						if (SiteClearing.isNaturalLeaves(s)) {
							if (!secondPass) {
								return reject(Reject.TREES);
							}
							candidateLeaves = true;
							continue;
						}
						if (s.is(BlockTags.LOGS)) {
							if (secondPass && noteTree(m.immutable())) {
								continue;
							}
							return reject(Reject.TREES);
						}
						return reject(Reject.BLOCKED);
					}
				}
			}
			if (nearPlayerBuild(box, floor - 1, floor + bp.height())) {
				return reject(Reject.PLAYER_BUILD);
			}
			Part part = new Part(new BlockPos(flatOrigin.getX(), floor, flatOrigin.getZ()), rotation);
			if (!secondPass || candidateTrees.isEmpty() && !candidateLeaves) {
				return part;
			}
			int[] space = {box[0], floor - 1, box[1], box[2], floor + bp.height(), box[3]};
			if (candidateTrees.isEmpty()) {
				logsToFell = List.of(); // only overhanging leaves to trim: as good as it gets
				clearBox = space;
				return part;
			}
			if (candidateTrees.size() > MAX_SITE_TREES) {
				return reject(Reject.TREES);
			}
			if (candidateTrees.size() < bestTrees) {
				Set<BlockPos> logs = new LinkedHashSet<>();
				for (BlockPos base : candidateTrees) {
					logs.addAll(treeByLog.get(base).logs());
				}
				best = part;
				bestTrees = candidateTrees.size();
				bestLogs = List.copyOf(logs);
				bestClearBox = space;
			}
			return null;
		}

		/**
		 * True if this log belongs to a natural tree a friend can fell completely, well clear of anything a player
		 * built; the tree then counts against this candidate. Each tree is judged once per search.
		 */
		private boolean noteTree(BlockPos log) {
			TreeFinder.Tree tree = treeByLog.get(log);
			if (tree == null) {
				if (notFellable.contains(log)) {
					return false;
				}
				Optional<TreeFinder.Tree> found = TreeFinder.analyse(level, log, TreeFinder.FELLABLE_HEIGHT);
				boolean ok = found.isPresent();
				if (ok) {
					for (BlockPos p : found.get().logs()) {
						if (WorldEditGuard.looksPlayerBuilt(level, p, 2, data)) {
							ok = false;
							break;
						}
					}
				}
				if (!ok) {
					notFellable.add(log);
					found.ifPresent(t -> notFellable.addAll(t.logs()));
					return false;
				}
				tree = found.get();
				for (BlockPos p : tree.logs()) {
					treeByLog.put(p, tree);
				}
				treeByLog.put(tree.base(), tree);
			}
			candidateTrees.add(tree.base());
			return true;
		}

		private @Nullable Part reject(Reject why) {
			lastReject = why;
			return null;
		}

		/**
		 * Third pass: a footprint on uneven natural ground, to be levelled before building. Picks the new ground level
		 * that needs the least digging and filling, at most {@code maxGradeDepth} blocks down or up anywhere. Every block
		 * to dig must be natural earth, sand, gravel or stone (or a plant), not placed by the friends, with no water or
		 * lava touching it, every space to fill must be dry, the building's space above must be clear as on flat
		 * ground, the ground just in front of the door side must be within a block of the new level (so the way in is
		 * not a wall), and nothing player-built may lie within {@value #GRADE_MARKER_GAP} blocks. The cheapest site
		 * found is remembered and the search runs on; one needing almost no work is taken at once.
		 */
		private @Nullable Part checkGraded(int[] box, int[] ground, int top, int bottom, BlockPos flatOrigin, int rotation,
			int nearY) {
			int limit = FriendsConfig.get().maxGradeDepth;
			if (top - bottom > 2 * limit) {
				return reject(Reject.UNEVEN);
			}
			int target = Integer.MIN_VALUE;
			int estimate = Integer.MAX_VALUE;
			int estimateFill = Integer.MAX_VALUE;
			for (int t = top - limit; t <= bottom + limit; t++) {
				if (Math.abs(t + 1 - centre.getY()) > MAX_RISE) {
					continue;
				}
				int dig = 0;
				int fill = 0;
				for (int g : ground) {
					if (g > t) {
						dig += g - t;
					} else {
						fill += t - g;
					}
				}
				// Least work; on a tie, less to fill (digging pays for itself in spoil).
				if (dig + fill < estimate || dig + fill == estimate && fill < estimateFill) {
					target = t;
					estimate = dig + fill;
					estimateFill = fill;
				}
			}
			if (target == Integer.MIN_VALUE) {
				return reject(Reject.UNEVEN);
			}
			if (estimate >= bestGradeCost) {
				return null; // a cheaper site is already known
			}
			if (!frontWithinReach(flatOrigin, rotation, target, nearY)) {
				return reject(Reject.UNEVEN);
			}
			int width = box[2] - box[0] + 1;
			int depth = box[3] - box[1] + 1;
			int floor = target + 1;
			List<BlockPos> cut = new ArrayList<>();
			List<BlockPos> fill = new ArrayList<>();
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int x = 0; x < width; x++) {
				for (int z = 0; z < depth; z++) {
					int g = ground[x * depth + z];
					// Dig: the bump above the new level, then the building's space above that must be free.
					for (int y = target + 1; y < floor + bp.height(); y++) {
						m.set(box[0] + x, y, box[1] + z);
						BlockState s = level.getBlockState(m);
						if (y <= g) {
							if (s.isAir()) {
								continue;
							}
							if (!s.getFluidState().isEmpty() || WorldEditGuard.touchesFluid(level, m)) {
								return reject(Reject.NO_GROUND);
							}
							if (s.hasBlockEntity() || !SiteGrading.isGradeable(s) || data.isPlacedByFriends(level, m)) {
								return reject(Reject.BLOCKED); // only natural ground is dug: the friends' own blocks are not
							}
							cut.add(m.immutable());
							continue;
						}
						if ((s.isAir() || WorldEditGuard.isClearablePlant(s)) && s.getFluidState().isEmpty()) {
							continue;
						}
						return reject(SiteClearing.isNaturalLeaves(s) || s.is(BlockTags.LOGS) ? Reject.TREES : Reject.BLOCKED);
					}
					// Fill: every empty space from the old ground up to the new level, including a hollow under it.
					for (int y = Math.min(g + 1, target); y <= target; y++) {
						m.set(box[0] + x, y, box[1] + z);
						BlockState s = level.getBlockState(m);
						if (!s.getFluidState().isEmpty()) {
							return reject(Reject.NO_GROUND);
						}
						if (SiteGrading.isFillable(s)) {
							fill.add(m.immutable());
						} else if (y > g) {
							return reject(Reject.BLOCKED);
						}
					}
				}
			}
			int cost = cut.size() + fill.size();
			if (cost >= bestGradeCost) {
				return null;
			}
			if (nearPlayerBuild(box, Math.min(bottom, target) - 1, Math.max(top, floor + bp.height()), GRADE_MARKER_GAP)) {
				return reject(Reject.PLAYER_BUILD);
			}
			Part part = new Part(new BlockPos(flatOrigin.getX(), floor, flatOrigin.getZ()), rotation);
			bestGrade = part;
			bestGradeCost = cost;
			bestCut = List.copyOf(cut);
			bestFill = List.copyOf(fill);
			if (cost <= GRADE_GOOD_ENOUGH) {
				gradeCut = bestCut;
				gradeFill = bestFill;
				return part; // hardly any work: no need to look further
			}
			return null;
		}

		/**
		 * True when the ground just in front of the plan's front (where doors and gates are) lies within a block of the
		 * new ground level for at least two of the three columns before its middle, so friends can walk in.
		 */
		private boolean frontWithinReach(BlockPos flatOrigin, int rotation, int target, int nearY) {
			int mid = (bp.width() - 1) / 2;
			int ok = 0;
			for (int dx = mid - 1; dx <= mid + 1; dx++) {
				BlockPos front = Blueprint.worldPos(flatOrigin, rotation, dx, 0, -1);
				if (!level.hasChunkAt(front.getX(), front.getZ())) {
					continue;
				}
				int g = groundY(front.getX(), front.getZ(), nearY);
				if (g != NO_GROUND && g != TREE_IN_THE_WAY && Math.abs(g - target) <= 1) {
					ok++;
				}
			}
			return ok >= 2;
		}

		private boolean nearPlayerBuild(int[] box, int minY, int maxY) {
			return nearPlayerBuild(box, minY, maxY, MARKER_GAP);
		}

		private boolean nearPlayerBuild(int[] box, int minY, int maxY, int gap) {
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int x = box[0] - gap; x <= box[2] + gap; x++) {
				for (int z = box[1] - gap; z <= box[3] + gap; z++) {
					for (int y = minY - 1; y <= maxY + 1; y++) {
						m.set(x, y, z);
						if (!level.isLoaded(m)) {
							continue;
						}
						BlockState s = level.getBlockState(m);
						if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(level, m)
							&& !ignoredMarkers.contains(m)) {
							return true;
						}
					}
				}
			}
			return false;
		}

		/**
		 * Top of the natural ground in a column, or {@link #NO_GROUND}, scanning down through the band a floor may
		 * use ({@value #MAX_RISE} blocks either side of the camp's height) through air, plants and leaves, so hillsides and spots under a tree canopy are judged by their real
		 * ground. A trunk means a tree is in the way ({@link #TREE_IN_THE_WAY}); on the second pass a fellable natural
		 * tree is looked through instead and judged by the soil it grows from. Only natural terrain counts as ground
		 * (or the friends' own foundation cobblestone and paths): a player's floor or roof, or water, never does.
		 */
		private int groundY(int x, int z, int nearY) {
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int y = nearY + MAX_RISE; y >= nearY - MAX_RISE - 1; y--) {
				m.set(x, y, z);
				BlockState s = level.getBlockState(m);
				if (s.isAir() || (WorldEditGuard.isClearablePlant(s) || SiteClearing.isNaturalLeaves(s)) && s.getFluidState().isEmpty()) {
					continue;
				}
				if (s.is(BlockTags.LOGS)) {
					if (!secondPass || !noteTree(m.immutable())) {
						return TREE_IN_THE_WAY;
					}
					continue;
				}
				if (s.is(Blocks.DIRT_PATH) && data.isPlacedByFriends(level, m)) {
					return y; // Terra's camp paths are trodden earth; a player's path stays theirs
				}
				if (!s.getFluidState().isEmpty() || !s.isFaceSturdy(level, m, Direction.UP)) {
					return NO_GROUND;
				}
				boolean ownFoundation = s.is(Blocks.COBBLESTONE) && data.isPlacedByFriends(level, m);
				return isNaturalGround(s) || ownFoundation ? y : NO_GROUND;
			}
			return NO_GROUND;
		}
	}

	/**
	 * Natural terrain a building may stand on: earth (dirt, grass, podzol, mycelium, mud, moss), sand, gravel, clay,
	 * natural stone, sandstone, badlands terracotta and snow. Logs, leaves and anything a player crafts are not ground.
	 */
	public static boolean isNaturalGround(BlockState s) {
		return s.is(BlockTags.SUBSTRATE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
			|| s.is(BlockTags.BADLANDS_TERRACOTTA) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE)
			|| s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CALCITE);
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
