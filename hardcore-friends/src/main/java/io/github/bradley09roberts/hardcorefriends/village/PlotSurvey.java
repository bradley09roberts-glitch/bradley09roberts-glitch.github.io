package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteClearing;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.SiteGrading;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Looks at the ground of one candidate plot and says whether a plan can stand there, and how: on level ground as it
 * is (one-block dips are filled with foundations by the builder), or on ground that needs a little levelling first
 * (survival's levelling: bumps dug away, dips filled, at most {@code maxGradeDepth} blocks either way). Every column of
 * the footprint must be natural ground (or the friends' own foundations) in the band round the camp's height: never
 * water, a field, a player's floor or a tree trunk; the building's space must be clear but for plants, snow and
 * natural leaves; the ground in front of the door must be within a step of the floor (for a waterside plan, the water
 * must be right in front instead); and nothing a player built may lie within two blocks (three when levelling). The
 * friends' own torches (lighting the camp's ground) do not count against a plot: the builder takes them up first.
 *
 * <p>Reads only loaded blocks: any part of the plot that is not loaded rules it out for now. Costs about the
 * footprint times the height in block reads, so the planner surveys at most a few candidates per tick.
 */
final class PlotSurvey {
	/** A floor stays within this many blocks of the camp centre's height. */
	static final int MAX_RISE = 8;

	/** Why a plot was turned down, counted so the village can explain an empty search. */
	enum Reject {
		NOT_LOADED("part of it is not loaded"),
		NO_GROUND("the ground is not natural (a floor, a roof or a gap)"),
		WATER("there is water"),
		FIELD("it is farmland"),
		TREES("trees stand there"),
		BLOCKED("something is in the way"),
		UNEVEN("the ground is too uneven"),
		PLAYER_BUILD("it is too close to something you built"),
		FRONT("the way in would be a step too high"),
		NO_WATERFRONT("it does not face the water");

		final String words;

		Reject(String words) {
			this.words = words;
		}
	}

	/** A plot that works: the floor height and any levelling to do first. */
	record Result(int floorY, List<BlockPos> cut, List<BlockPos> fill) {
		int cost() {
			return cut.size() + fill.size();
		}
	}

	/** The outcome: a result, or why not. */
	record Outcome(@Nullable Result result, @Nullable Reject reject) {
		static Outcome no(Reject why) {
			return new Outcome(null, why);
		}
	}

	private static final int NONE = Integer.MIN_VALUE;

	private PlotSurvey() {
	}

	/**
	 * Surveys a candidate. {@code origin} gives x and z (y is found here); {@code centreY} is the camp centre's height.
	 * {@code waterfront}: the plan's front must face water. {@code allowGrade}: uneven ground may be levelled.
	 */
	static Outcome survey(ServerLevel level, CampData data, Blueprint plan, BlockPos origin, int rotation, int centreY,
		boolean waterfront, boolean allowGrade) {
		int[] box = plan.footprint(origin, rotation);
		int width = box[2] - box[0] + 1;
		int depth = box[3] - box[1] + 1;
		int[] ground = new int[width * depth];
		int top = Integer.MIN_VALUE;
		int bottom = Integer.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				if (!level.hasChunkAt(box[0] + x, box[1] + z)) {
					return Outcome.no(Reject.NOT_LOADED);
				}
				int g = groundY(level, data, box[0] + x, box[1] + z, centreY, m);
				if (g < NONE + 16) {
					return Outcome.no(Reject.values()[g - NONE]);
				}
				ground[x * depth + z] = g;
				top = Math.max(top, g);
				bottom = Math.min(bottom, g);
			}
		}
		FriendsConfig cfg = FriendsConfig.get();
		int target;
		boolean levelled = false;
		if (top - bottom <= (plan.hasFoundations() ? 1 : 0)) {
			target = top;
		} else {
			int limit = cfg.maxGradeDepth;
			if (!allowGrade || !cfg.allowTerraforming || !cfg.allowWorldEditing || limit <= 0 || !plan.hasFoundations()
				|| top - bottom > 2 * limit) {
				return Outcome.no(Reject.UNEVEN);
			}
			target = NONE;
			int best = Integer.MAX_VALUE;
			for (int t = top - limit; t <= bottom + limit; t++) {
				int work = 0;
				for (int g : ground) {
					work += Math.abs(g - t);
				}
				if (work < best) {
					best = work;
					target = t;
				}
			}
			if (target == NONE || best > width * depth * 2) {
				return Outcome.no(Reject.UNEVEN);
			}
			levelled = true;
		}
		int floor = target + 1;
		if (Math.abs(floor - centreY) > MAX_RISE) {
			return Outcome.no(Reject.UNEVEN);
		}
		List<BlockPos> cut = new ArrayList<>();
		List<BlockPos> fill = new ArrayList<>();
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				int g = ground[x * depth + z];
				int wx = box[0] + x;
				int wz = box[1] + z;
				if (levelled) {
					for (int y = target + 1; y <= g; y++) {
						m.set(wx, y, wz);
						BlockState s = level.getBlockState(m);
						if (s.isAir()) {
							continue;
						}
						if (!s.getFluidState().isEmpty() || WorldEditGuard.touchesFluid(level, m)) {
							return Outcome.no(Reject.WATER);
						}
						if (s.hasBlockEntity() || !SiteGrading.isGradeable(s) || data.isPlacedByFriends(level, m)) {
							return Outcome.no(Reject.BLOCKED);
						}
						cut.add(m.immutable());
					}
					for (int y = g + 1; y <= target; y++) {
						m.set(wx, y, wz);
						BlockState s = level.getBlockState(m);
						if (!s.getFluidState().isEmpty()) {
							return Outcome.no(Reject.WATER);
						}
						if (!SiteGrading.isFillable(s)) {
							return Outcome.no(Reject.BLOCKED);
						}
						fill.add(m.immutable());
					}
				}
				// The building's space, from just above whichever is higher (the ground or the new level) to the top.
				for (int y = Math.max(g, target) + 1; y < floor + plan.height(); y++) {
					m.set(wx, y, wz);
					BlockState s = level.getBlockState(m);
					if (!s.getFluidState().isEmpty()) {
						return Outcome.no(Reject.WATER);
					}
					if (s.isAir() || WorldEditGuard.isClearablePlant(s) || SiteClearing.isNaturalLeaves(s) || ownTorch(level, data, s, m)) {
						continue;
					}
					return Outcome.no(s.is(BlockTags.LOGS) ? Reject.TREES : Reject.BLOCKED);
				}
			}
		}
		Reject front = checkFront(level, data, plan, origin, rotation, target, centreY, waterfront, m);
		if (front != null) {
			return Outcome.no(front);
		}
		int gap = levelled ? 3 : 2;
		if (nearPlayerBuild(level, data, box, Math.min(bottom, target) - 1, floor + plan.height() + 1, gap, m)) {
			return Outcome.no(Reject.PLAYER_BUILD);
		}
		return new Outcome(new Result(floor, List.copyOf(cut), List.copyOf(fill)), null);
	}

	/**
	 * The way in: the ground just in front of the middle of the plan's front must be within a step of the new ground
	 * level for two of its three columns. A waterside plan wants water there instead, at or just below the bank.
	 */
	private static @Nullable Reject checkFront(ServerLevel level, CampData data, Blueprint plan, BlockPos origin, int rotation,
		int target, int centreY, boolean waterfront, BlockPos.MutableBlockPos m) {
		int mid = (plan.width() - 1) / 2;
		int ok = 0;
		for (int dx = mid - 1; dx <= mid + 1; dx++) {
			BlockPos front = Blueprint.worldPos(origin, rotation, dx, 0, -1);
			if (!level.hasChunkAt(front.getX(), front.getZ())) {
				continue;
			}
			if (waterfront) {
				for (int y = target - 1; y <= target; y++) {
					m.set(front.getX(), y, front.getZ());
					if (level.getFluidState(m).is(FluidTags.WATER)) {
						ok++;
						break;
					}
				}
				continue;
			}
			int g = groundY(level, data, front.getX(), front.getZ(), centreY, m);
			if (g >= NONE + 16 && Math.abs(g - target) <= 1) {
				ok++;
			}
		}
		if (ok >= 2) {
			return null;
		}
		return waterfront ? Reject.NO_WATERFRONT : Reject.FRONT;
	}

	/**
	 * The top of the natural ground in a column, scanning down through the band round the camp's height past air,
	 * plants, snow and natural leaves; or {@code NONE + reject.ordinal()} when the column rules the plot out.
	 */
	static int groundY(ServerLevel level, CampData data, int x, int z, int centreY, BlockPos.MutableBlockPos m) {
		for (int y = centreY + MAX_RISE; y >= centreY - MAX_RISE - 1; y--) {
			m.set(x, y, z);
			BlockState s = level.getBlockState(m);
			if (s.isAir()) {
				continue;
			}
			if (!s.getFluidState().isEmpty()) {
				return NONE + Reject.WATER.ordinal();
			}
			if (WorldEditGuard.isClearablePlant(s) || SiteClearing.isNaturalLeaves(s) || ownTorch(level, data, s, m)) {
				continue;
			}
			if (s.is(BlockTags.LOGS)) {
				return NONE + Reject.TREES.ordinal();
			}
			if (s.is(Blocks.FARMLAND)) {
				return NONE + Reject.FIELD.ordinal();
			}
			boolean own = data.isPlacedByFriends(level, m);
			if (s.is(Blocks.DIRT_PATH) && own) {
				return y;
			}
			if (!s.isFaceSturdy(level, m, Direction.UP)) {
				return NONE + Reject.NO_GROUND.ordinal();
			}
			boolean ownFoundation = own && (s.is(Blocks.COBBLESTONE) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT)
				|| s.is(Blocks.STONE));
			return SiteFinder.isNaturalGround(s) || ownFoundation ? y : NONE + Reject.NO_GROUND.ordinal();
		}
		return NONE + Reject.NO_GROUND.ordinal();
	}

	/**
	 * A torch the friends put down themselves (the landscaper lights dark ground in the camp, which the village grows
	 * over): the builder takes it up before building ({@link VillageBuildTask}), so it never rules a plot out.
	 */
	static boolean ownTorch(ServerLevel level, CampData data, BlockState s, BlockPos pos) {
		return (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) && data.isPlacedByFriends(level, pos);
	}

	private static boolean nearPlayerBuild(ServerLevel level, CampData data, int[] box, int minY, int maxY, int gap,
		BlockPos.MutableBlockPos m) {
		for (int x = box[0] - gap; x <= box[2] + gap; x++) {
			for (int z = box[1] - gap; z <= box[3] + gap; z++) {
				if (!level.hasChunkAt(x, z)) {
					return true; // cannot look: keep clear
				}
				for (int y = minY - 1; y <= maxY; y++) {
					m.set(x, y, z);
					BlockState s = level.getBlockState(m);
					if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(level, m)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
