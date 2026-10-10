package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;

/**
 * Looking for a plot for one building, a few candidates a tick: spots along the streets nearest the square first (or
 * round the square, on the waterside, or at the end of a main street for the gate), each first checked cheaply against
 * the plan (inside the village, off the streets and the square, clear of other plots and sites, the mines and the lamp
 * spots), then on the ground ({@link PlotSurvey}). A spot on level ground is taken at once; one needing levelling is
 * remembered, and the one needing the least work is taken if nothing level turns up, once it has been checked and
 * surveyed again (a search can take a while, and the ground or the sites round it may have changed meanwhile).
 */
final class PlotSearch {
	/** Where to look. */
	enum Mode {
		STREET,
		SQUARE,
		WATERFRONT,
		GATE,
		WALL
	}

	/** A plot found: where, and how the ground is to be made ready. */
	record Found(TownPlan.Candidate candidate, PlotSurvey.Result result) {
	}

	/** Surveys of uneven ground needing at most this much digging and filling per footprint block are taken at once. */
	private static final double GOOD_ENOUGH = 0.15;
	/** Cheap rejections checked per step at most, besides the surveys. */
	private static final int CHEAP_PER_STEP = 256;
	/** Water columns looked at for a waterside plot. */
	private static final int MAX_WATER = 160;

	final Mode mode;
	final Blueprint plan;
	final String kind;
	final String purpose;
	/**
	 * What the planner holds back when no plot is found: the kind it was asked for ({@code shop} when any shop will
	 * do, not the {@code shop:bakery} picked for it), a household's {@code home:} key, a length of wall.
	 */
	final String waitKey;
	final Set<UUID> intended;
	private final List<TownPlan.Candidate> candidates;
	private final BlockPos centre;
	private int index;
	private TownPlan.@Nullable Candidate best;
	private PlotSurvey.@Nullable Result bestResult;
	private boolean done;
	private final int[] rejected = new int[PlotSurvey.Reject.values().length + 1];

	PlotSearch(Mode mode, Blueprint plan, String kind, String purpose, String waitKey, Set<UUID> intended,
		List<TownPlan.Candidate> candidates, BlockPos centre) {
		this.mode = mode;
		this.plan = plan;
		this.kind = kind;
		this.purpose = purpose;
		this.waitKey = waitKey;
		this.intended = Set.copyOf(intended);
		this.candidates = candidates;
		this.centre = centre;
	}

	boolean done() {
		return done;
	}

	/**
	 * Checks candidates until {@code surveys} ground surveys have been made. Returns the plot once one is chosen, else
	 * null; {@link #done()} once every candidate has been looked at without a plot.
	 */
	@Nullable Found step(ServerLevel level, CampData camp, VillageData v, int surveys) {
		if (done) {
			return null;
		}
		List<int[]> taken = takenBoxes(camp, v);
		int cheap = 0;
		while (surveys > 0 && cheap < CHEAP_PER_STEP) {
			if (index >= candidates.size()) {
				done = true;
				TownPlan.Candidate c = best;
				if (c == null || !fits(camp, v, c, taken)) {
					return null; // nothing found, or the best spot has been taken since it was surveyed
				}
				// Surveyed again: the ground may have changed since (a player digging, a camp building begun).
				PlotSurvey.Result r = survey(level, camp, c).result();
				return r != null ? new Found(c, r) : null;
			}
			TownPlan.Candidate c = candidates.get(index++);
			if (!fits(camp, v, c, taken)) {
				cheap++;
				rejected[rejected.length - 1]++;
				continue;
			}
			surveys--;
			PlotSurvey.Outcome out = survey(level, camp, c);
			PlotSurvey.Result r = out.result();
			if (r == null) {
				PlotSurvey.Reject why = out.reject();
				if (why != null) {
					rejected[why.ordinal()]++;
				}
				continue;
			}
			double perBlock = r.cost() / (double) (plan.width() * plan.depth());
			if (perBlock <= GOOD_ENOUGH) {
				done = true;
				return new Found(c, r);
			}
			if (bestResult == null || r.cost() < bestResult.cost()) {
				best = c;
				bestResult = r;
			}
		}
		return null;
	}

	private PlotSurvey.Outcome survey(ServerLevel level, CampData camp, TownPlan.Candidate c) {
		boolean waterfront = mode == Mode.WATERFRONT;
		boolean grade = mode != Mode.SQUARE || plan.width() * plan.depth() > 9;
		return PlotSurvey.survey(level, camp, plan, c.origin(), c.rotation(), centre.getY(), waterfront, grade);
	}

	/** Plain words for what most often ruled spots out. */
	String problem() {
		int top = -1;
		for (int i = 0; i < PlotSurvey.Reject.values().length; i++) {
			if (rejected[i] > 0 && (top < 0 || rejected[i] > rejected[top])) {
				top = i;
			}
		}
		return top < 0 ? "there is no room left along the streets" : PlotSurvey.Reject.values()[top].words;
	}

	/** Footprints the plot must keep clear of: every reserved site and every plot. */
	private static List<int[]> takenBoxes(CampData camp, VillageData v) {
		List<int[]> boxes = new ArrayList<>(SiteFinder.reservedBoxes(camp, ""));
		for (VillageData.Plot p : v.plots()) {
			boxes.add(p.box);
		}
		return boxes;
	}

	/** The cheap checks: inside the village, off the streets (but the gate), off the square, clear of everything else. */
	private boolean fits(CampData camp, VillageData v, TownPlan.Candidate c, List<int[]> taken) {
		int[] box = c.box();
		int r = TownPlan.maxRadius() - 3;
		if (TownPlan.farthestSqr(centre, box) > (double) r * r) {
			return false;
		}
		if (mode == Mode.SQUARE) {
			if (TownPlan.distSqr(centre, box) < 3 * 3 || TownPlan.boxOnStreet(centre, box, 1)) {
				return false;
			}
		} else if (mode != Mode.GATE && mode != Mode.WALL) {
			if (TownPlan.distSqr(centre, box) < TownPlan.SQUARE * TownPlan.SQUARE || TownPlan.boxOnStreet(centre, box, 1)) {
				return false;
			}
		}
		if (mode != Mode.GATE && mode != Mode.WALL && TownPlan.coversLampSlot(centre, box)) {
			return false;
		}
		// A length of wall joins the gate (or the wall before it) end to end: it may touch, but never overlap.
		int gap = mode == Mode.WALL ? 0 : 1;
		for (int[] other : taken) {
			if (TownPlan.overlaps(box, other, gap)) {
				return false;
			}
		}
		return !TownPlan.nearMine(camp, v.dimension(), box);
	}

	// ------------------------------------------------------------- waterside

	/**
	 * Spots on the bank of the water round the village, for a plan whose front should face the water (the fishing hut):
	 * the middle of its front row on dry ground right beside a water surface, turned to face it.
	 */
	static List<TownPlan.Candidate> waterfrontCandidates(ServerLevel level, BlockPos centre, Blueprint plan) {
		int r = TownPlan.maxRadius() - 4;
		int mid = (plan.width() - 1) / 2;
		List<BlockPos> water = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r && water.size() < MAX_WATER * 4; dx += 3) {
			for (int dz = -r; dz <= r; dz += 3) {
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				if (dx * dx + dz * dz > r * r || !level.hasChunkAt(x, z)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
				m.set(x, top, z);
				if (Math.abs(top - centre.getY()) <= PlotSurvey.MAX_RISE && level.getFluidState(m).is(FluidTags.WATER)) {
					water.add(m.immutable());
				}
			}
		}
		water.sort(Comparator.comparingDouble(p -> p.distSqr(centre)));
		List<TownPlan.Candidate> list = new ArrayList<>();
		for (BlockPos w : water.subList(0, Math.min(MAX_WATER, water.size()))) {
			for (Direction land : Direction.Plane.HORIZONTAL) {
				BlockPos bank = w.relative(land);
				int rotation = Blueprint.rotationFacing(land.getOpposite());
				BlockPos origin = TownPlan.originAt(bank, rotation, mid, 0);
				list.add(new TownPlan.Candidate(TownPlan.WATERSIDE, 0, 0, rotation, origin, plan.footprint(origin, rotation)));
			}
		}
		return list;
	}
}
