package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MinePlan;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * The geometry of the town plan: a grid of six streets round the camp centre (the High Street running east to west
 * through the middle, Market Street north to south, and four lanes {@value #SPACING} blocks out), a square of open
 * ground at the centre where the two main streets cross, and plots along both sides of every street. A plot's front
 * row sits {@value #FRONT} blocks from the street's middle line (the street is three blocks wide, then a one-block
 * verge where the front door opens), so every building faces the street it stands on. Lamp posts stand on the verge
 * every {@value #LAMP_SPACING} blocks, on alternate sides.
 *
 * <p>Streets follow the ground as it lies: a street is a line on the map, and the path is laid on whatever ground is
 * there. Plots are only taken where the ground suits ({@link PlotSurvey}), so a hill, a lake or a player's build simply
 * leaves a gap in the row. Pure geometry: nothing here reads the world.
 */
public final class TownPlan {
	/** Distance between parallel streets: two rows of the deepest plots back to back fit between them. */
	public static final int SPACING = 34;
	/** Half the width of a street's path (the path is {@code 2 × HALF + 1} wide). */
	public static final int HALF = 1;
	/** How far a plot's front row stands from the street's middle line: the path, then the verge. */
	public static final int FRONT = HALF + 2;
	/** Open ground kept round the camp centre for the square. */
	public static final int SQUARE = 7;
	/** The square's decorations (the well, benches, a fountain) stand within this many blocks of the centre. */
	public static final int SQUARE_OUTER = 17;
	/** Lamp posts along the streets, this far apart. */
	public static final int LAMP_SPACING = 12;
	/** How far a plot keeps from the friends' mines (dug ground and stairways). */
	private static final int MINE_GAP = 3;

	/** One street of the grid: its name, whether it runs east to west (along x) and how far its line is from the centre. */
	public record Street(int index, String name, boolean alongX, int offset) {
	}

	/** The streets, main ones first. */
	public static final List<Street> STREETS = List.of(
		new Street(0, "High Street", true, 0),
		new Street(1, "Market Street", false, 0),
		new Street(2, "North Lane", true, -SPACING),
		new Street(3, "South Lane", true, SPACING),
		new Street(4, "West Lane", false, -SPACING),
		new Street(5, "East Lane", false, SPACING));

	/**
	 * A spot a plan could go: the street it fronts (-1 for none), which side of it (+1 south or east, -1 north or west),
	 * how far along it, the plan's rotation and its origin (y still to be found) and footprint {minX, minZ, maxX, maxZ}.
	 */
	public record Candidate(int street, int side, int along, int rotation, BlockPos origin, int[] box) {
	}

	private TownPlan() {
	}

	/** The radius the village may spread to, from the settings. */
	public static int maxRadius() {
		return FriendsConfig.get().villageRadius;
	}

	// ----------------------------------------------------------------- streets

	/** The street's line through this world column: the distance across it ({@code perp}) and along it. */
	public static int across(Street s, BlockPos centre, int x, int z) {
		return s.alongX() ? z - (centre.getZ() + s.offset()) : x - (centre.getX() + s.offset());
	}

	public static int along(Street s, BlockPos centre, int x, int z) {
		return s.alongX() ? x - centre.getX() : z - centre.getZ();
	}

	/** The world column at {@code along} on the street's line, {@code across} blocks to one side. */
	public static BlockPos column(Street s, BlockPos centre, int along, int across) {
		return s.alongX() ? new BlockPos(centre.getX() + along, centre.getY(), centre.getZ() + s.offset() + across)
			: new BlockPos(centre.getX() + s.offset() + across, centre.getY(), centre.getZ() + along);
	}

	/** True if this column lies on any street's path (within {@code extra} more blocks of it), within the village. */
	public static boolean onStreet(BlockPos centre, int x, int z, int extra) {
		int r = maxRadius();
		for (Street s : STREETS) {
			if (Math.abs(across(s, centre, x, z)) <= HALF + extra && Math.abs(along(s, centre, x, z)) <= r) {
				return true;
			}
		}
		return false;
	}

	/** True if the box {minX, minZ, maxX, maxZ}, grown by {@code gap}, touches any street's path. */
	public static boolean boxOnStreet(BlockPos centre, int[] box, int gap) {
		for (Street s : STREETS) {
			int lineFixed = s.alongX() ? centre.getZ() + s.offset() : centre.getX() + s.offset();
			int lo = s.alongX() ? box[1] : box[0];
			int hi = s.alongX() ? box[3] : box[2];
			if (hi + gap >= lineFixed - HALF && lo - gap <= lineFixed + HALF) {
				return true;
			}
		}
		return false;
	}

	/** A readable side of a street: "the north side of High Street". */
	public static String sideName(int street, int side) {
		if (street < 0 || street >= STREETS.size()) {
			return "by the square";
		}
		Street s = STREETS.get(street);
		String dir = s.alongX() ? (side > 0 ? "south" : "north") : (side > 0 ? "east" : "west");
		return "the " + dir + " side of " + s.name();
	}

	// ------------------------------------------------------------- lamp slots

	/** The lamp post spots along a street, on its verge, every {@value #LAMP_SPACING} blocks on alternate sides. */
	public static List<BlockPos> lampSlots(Street s, BlockPos centre) {
		List<BlockPos> list = new ArrayList<>();
		int r = maxRadius() - 3;
		for (int k = 1; k * LAMP_SPACING - LAMP_SPACING / 2 <= r; k++) {
			int t = k * LAMP_SPACING - LAMP_SPACING / 2;
			for (int sign : new int[] {1, -1}) {
				int side = (k % 2 == 0) == (sign > 0) ? 1 : -1;
				BlockPos p = column(s, centre, sign * t, side * (HALF + 1));
				if (!onStreet(centre, p.getX(), p.getZ(), 0)) {
					list.add(p);
				}
			}
		}
		return list;
	}

	private static @Nullable List<BlockPos> slotCache;
	private static @Nullable BlockPos slotCentre;
	private static int slotRadius;

	/** Every lamp spot of every street (worked out once per plan centre and radius). */
	public static synchronized List<BlockPos> allLampSlots(BlockPos centre) {
		List<BlockPos> cached = slotCache;
		if (cached != null && centre.equals(slotCentre) && slotRadius == maxRadius()) {
			return cached;
		}
		List<BlockPos> all = new ArrayList<>();
		for (Street s : STREETS) {
			all.addAll(lampSlots(s, centre));
		}
		slotCache = List.copyOf(all);
		slotCentre = centre.immutable();
		slotRadius = maxRadius();
		return slotCache;
	}

	/** True if the box, grown by one, covers a lamp spot of any street. */
	public static boolean coversLampSlot(BlockPos centre, int[] box) {
		for (BlockPos p : allLampSlots(centre)) {
			if (p.getX() >= box[0] - 1 && p.getX() <= box[2] + 1 && p.getZ() >= box[1] - 1 && p.getZ() <= box[3] + 1) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------- candidates

	/**
	 * The origin that puts the plan's local cell ({@code localX}, 0, {@code localZ}) on {@code anchor} when turned by
	 * {@code rotation} (y as given).
	 */
	public static BlockPos originAt(BlockPos anchor, int rotation, int localX, int localZ) {
		BlockPos offset = Blueprint.worldPos(BlockPos.ZERO, rotation, localX, 0, localZ);
		return anchor.subtract(new BlockPos(offset.getX(), 0, offset.getZ()));
	}

	/**
	 * Every spot along the streets where this plan could front a street, nearest the centre first: on both sides of
	 * every street, at every second block along it, out to the village's radius.
	 */
	public static List<Candidate> streetCandidates(BlockPos centre, Blueprint plan) {
		int r = maxRadius();
		int mid = (plan.width() - 1) / 2;
		List<Candidate> list = new ArrayList<>();
		for (Street s : STREETS) {
			for (int t = -r; t <= r; t += 2) {
				for (int side : new int[] {1, -1}) {
					int rotation = s.alongX() ? (side > 0 ? 0 : 2) : (side > 0 ? 3 : 1);
					BlockPos anchor = column(s, centre, t, side * FRONT);
					BlockPos origin = originAt(anchor, rotation, mid, 0);
					list.add(new Candidate(s.index(), side, t, rotation, origin, plan.footprint(origin, rotation)));
				}
			}
		}
		list.sort(Comparator.comparingDouble(c -> distSqr(centre, c.box())));
		return list;
	}

	/** Spots round the square for its decorations (a well, a fountain, benches), each turned to face the centre. */
	public static List<Candidate> squareCandidates(BlockPos centre, Blueprint plan) {
		List<Candidate> list = new ArrayList<>();
		for (int ring = 4; ring <= SQUARE_OUTER - 2; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					BlockPos spot = centre.offset(dx, 0, dz);
					int rotation = Blueprint.rotationFacing(SiteFinder.directionTo(spot, centre));
					BlockPos origin = plan.originFor(spot, rotation);
					int[] box = plan.footprint(origin, rotation);
					if (farthestSqr(centre, box) <= SQUARE_OUTER * SQUARE_OUTER) {
						list.add(new Candidate(-1, 0, ring, rotation, origin, box));
					}
				}
			}
		}
		return list;
	}

	/**
	 * Spots at the ends of the High Street and Market Street, beyond the last plots, for the town gate: it straddles
	 * the street with the way through running along it and its front facing out of the village.
	 */
	public static List<Candidate> gateCandidates(BlockPos centre, Blueprint plan, VillageData data) {
		List<Candidate> list = new ArrayList<>();
		int r = maxRadius() - 4;
		int mid = (plan.width() - 1) / 2;
		int depthMid = (plan.depth() - 1) / 2;
		for (int street = 0; street <= 1; street++) {
			Street s = STREETS.get(street);
			int[] open = data.open(street);
			int lo = open == null ? -16 : open[0];
			int hi = open == null ? 16 : open[1];
			for (int sign : new int[] {1, -1}) {
				int start = sign > 0 ? hi + 4 : -lo + 4;
				for (int t = start; t <= r; t += 2) {
					Direction out = s.alongX() ? (sign > 0 ? Direction.EAST : Direction.WEST) : (sign > 0 ? Direction.SOUTH : Direction.NORTH);
					int rotation = Blueprint.rotationFacing(out);
					BlockPos anchor = column(s, centre, sign * t, 0);
					BlockPos origin = originAt(anchor, rotation, mid, depthMid);
					list.add(new Candidate(street, 0, sign * t, rotation, origin, plan.footprint(origin, rotation)));
				}
			}
		}
		list.sort(Comparator.comparingDouble(c -> Math.abs(c.along())));
		return list;
	}

	/**
	 * A segment of wall joined end to end to another piece (the gate, or the last segment), on its left or right as
	 * seen from the piece's front, using the plans' {@code join} spots: the new segment's own end meets the piece's
	 * join spot. Null if either plan lacks join spots.
	 */
	public static @Nullable Candidate joinedTo(Blueprint piece, BlockPos pieceOrigin, int rotation, Blueprint wall, boolean right) {
		int[] pieceJoin = extremeJoin(piece, right);
		int[] wallJoin = extremeJoin(wall, !right);
		if (pieceJoin == null || wallJoin == null) {
			return null;
		}
		// The wall's own end cell (one in from its join spot) sits on the piece's join spot.
		int wallEndX = right ? wallJoin[0] + 1 : wallJoin[0] - 1;
		int offsetX = pieceJoin[0] - wallEndX;
		int offsetZ = pieceJoin[2] - wallJoin[2];
		BlockPos shift = Blueprint.worldPos(BlockPos.ZERO, rotation, offsetX, 0, offsetZ);
		BlockPos origin = new BlockPos(pieceOrigin.getX() + shift.getX(), 0, pieceOrigin.getZ() + shift.getZ());
		return new Candidate(-1, right ? 1 : -1, 0, rotation, origin, wall.footprint(origin, rotation));
	}

	private static int @Nullable [] extremeJoin(Blueprint plan, boolean highest) {
		int[] best = null;
		for (int[] p : plan.marker("join")) {
			if (best == null || (highest ? p[0] > best[0] : p[0] < best[0])) {
				best = p;
			}
		}
		return best;
	}

	// -------------------------------------------------------------- distances

	/** Squared distance from the centre to the nearest point of a box. */
	public static double distSqr(BlockPos centre, int[] box) {
		double dx = Math.max(0, Math.max(box[0] - centre.getX(), centre.getX() - box[2]));
		double dz = Math.max(0, Math.max(box[1] - centre.getZ(), centre.getZ() - box[3]));
		return dx * dx + dz * dz;
	}

	/** Squared distance from the centre to the farthest corner of a box. */
	public static double farthestSqr(BlockPos centre, int[] box) {
		double dx = Math.max(Math.abs(box[0] - centre.getX()), Math.abs(box[2] - centre.getX()));
		double dz = Math.max(Math.abs(box[1] - centre.getZ()), Math.abs(box[3] - centre.getZ()));
		return dx * dx + dz * dz;
	}

	/** True if two boxes {minX, minZ, maxX, maxZ}, one grown by {@code gap}, overlap. */
	public static boolean overlaps(int[] a, int[] b, int gap) {
		return a[0] - gap <= b[2] && a[2] + gap >= b[0] && a[1] - gap <= b[3] && a[3] + gap >= b[1];
	}

	/** True if the box comes within {@value #MINE_GAP} blocks of the friends' staircase mine or deep mine. */
	public static boolean nearMine(CampData data, String dimension, int[] box) {
		for (String key : new String[] {MinePlan.KEY, MinePlan.DEEP_KEY}) {
			MinePlan plan = MinePlan.of(data, key);
			if (!plan.exists() || !plan.dimension().equals(dimension)) {
				continue;
			}
			BoundingBox b = plan.box();
			if (overlaps(box, new int[] {b.minX(), b.minZ(), b.maxX(), b.maxZ()}, MINE_GAP)) {
				return true;
			}
		}
		return false;
	}
}
