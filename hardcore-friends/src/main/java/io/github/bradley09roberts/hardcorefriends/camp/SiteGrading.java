package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Levelling plans for building sites on uneven ground. When no flat spot fits a building, the site search may choose
 * one where the ground only needs a little digging and filling ({@code SiteFinder}, third pass): the bumps above the
 * new ground level are dug away, top-down, and the dips below it filled, bottom-up, with dirt or cobblestone. The plan
 * records exactly which blocks to cut and which spaces to fill, and the box they lie in; the levelling job ({@code
 * survival.GradeSiteTask}) works through it, the building waits for it, and the {@code GRADE} edit rules only allow
 * those blocks and spaces. Kept in camp memory per plan, so a restart carries on where the levelling stopped.
 */
public final class SiteGrading {
	private static final String MEMORY = "hardcorefriends.site_grading";
	/** How long a worked-out answer to "is there levelling to do?" is reused, in ticks. */
	private static final int ACTIVE_CACHE_TICKS = 20;

	/**
	 * A plan's levelling still to do: the natural blocks to dig away, the spaces to fill, the box around both {minX,
	 * minY, minZ, maxX, maxY, maxZ}, and positions a friend could not work and gave up on (left alone, so they never
	 * hold the build up for good).
	 */
	public record Job(String planId, LongSet cut, LongSet fill, int[] box, LongSet skipped) {
		/** True if the position lies inside the plan's box. */
		public boolean inBox(BlockPos pos) {
			return pos.getX() >= box[0] && pos.getX() <= box[3] && pos.getY() >= box[1] && pos.getY() <= box[4]
				&& pos.getZ() >= box[2] && pos.getZ() <= box[5];
		}
	}

	private static long activeAt = Long.MIN_VALUE;
	private static @Nullable Job activeJob;

	private SiteGrading() {
	}

	/** Records the levelling of a newly reserved site: the blocks to dig away and the spaces to fill. */
	public static void reserve(CampData data, String planId, Collection<BlockPos> cut, Collection<BlockPos> fill) {
		if (cut.isEmpty() && fill.isEmpty()) {
			forget(data, planId);
			return;
		}
		int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (Collection<BlockPos> list : List.of(cut, fill)) {
			for (BlockPos p : list) {
				box[0] = Math.min(box[0], p.getX());
				box[1] = Math.min(box[1], p.getY());
				box[2] = Math.min(box[2], p.getZ());
				box[3] = Math.max(box[3], p.getX());
				box[4] = Math.max(box[4], p.getY());
				box[5] = Math.max(box[5], p.getZ());
			}
		}
		CompoundTag tag = new CompoundTag();
		tag.putLongArray("cut", cut.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putLongArray("fill", fill.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putIntArray("box", box);
		data.memory(MEMORY).put(planId, tag);
		data.setDirty();
		activeAt = Long.MIN_VALUE;
	}

	public static void forget(CampData data, String planId) {
		if (data.memory(MEMORY).contains(planId)) {
			data.memory(MEMORY).remove(planId);
			data.setDirty();
		}
		activeAt = Long.MIN_VALUE;
	}

	/** The levelling job of a plan whose site is reserved and not yet built, if any. */
	public static Optional<Job> job(CampData data, String planId) {
		CompoundTag tag = data.memory(MEMORY).getCompoundOrEmpty(planId);
		if (tag.isEmpty() || data.site(planId).isEmpty() || data.isCompleted(planId)) {
			return Optional.empty();
		}
		int[] box = tag.getIntArray("box").orElse(new int[0]);
		if (box.length != 6) {
			return Optional.empty();
		}
		return Optional.of(new Job(planId, set(tag, "cut"), set(tag, "fill"), box, set(tag, "skipped")));
	}

	private static LongSet set(CompoundTag tag, String key) {
		long[] packed = tag.getLongArray(key).orElse(new long[0]);
		LongSet set = new LongOpenHashSet(packed.length);
		for (long l : packed) {
			set.add(l);
		}
		return set;
	}

	/** Leaves a position alone for good: a friend could not reach or work it. */
	public static void skip(CampData data, Job job, BlockPos pos) {
		CompoundTag tag = data.memory(MEMORY).getCompound(job.planId()).orElse(null);
		if (tag == null) {
			return;
		}
		long[] old = tag.getLongArray("skipped").orElse(new long[0]);
		long[] grown = java.util.Arrays.copyOf(old, old.length + 1);
		grown[old.length] = pos.asLong();
		tag.putLongArray("skipped", grown);
		job.skipped().add(pos.asLong());
		data.setDirty();
		activeAt = Long.MIN_VALUE;
	}

	/**
	 * The first plan with levelling still to do, if any. The answer is worked out at most once a second for everyone
	 * (every friend's levelling job asks while choosing what to do).
	 */
	public static Optional<Job> active(ServerLevel level, CampData data) {
		long now = level.getGameTime();
		if (now - activeAt >= 0 && now - activeAt < ACTIVE_CACHE_TICKS) {
			return Optional.ofNullable(activeJob);
		}
		activeAt = now;
		activeJob = null;
		for (String planId : List.copyOf(data.memory(MEMORY).keySet())) {
			Optional<Job> job = job(data, planId);
			if (job.isEmpty()) {
				if (data.site(planId).isEmpty() || data.isCompleted(planId)) {
					forget(data, planId); // built, or the site was given up: nothing left to level
					activeAt = now;
				}
				continue;
			}
			if (pending(level, job.get())) {
				activeJob = job.get();
				break;
			}
		}
		return Optional.ofNullable(activeJob);
	}

	/** Forgets the cached answer of {@link #active}, e.g. when a server stops. */
	public static void clearCache() {
		activeAt = Long.MIN_VALUE;
		activeJob = null;
	}

	/**
	 * True while a recorded block still stands to be dug or a recorded space still waits to be filled. A position in
	 * a chunk that is not loaded counts as still to do (and is not loaded to look).
	 */
	public static boolean pending(ServerLevel level, Job job) {
		for (long l : job.cut()) {
			BlockPos p = BlockPos.of(l);
			if (!job.skipped().contains(l) && (!level.isLoaded(p) || !level.getBlockState(p).isAir())) {
				return true;
			}
		}
		return !fillsLeft(level, job).isEmpty();
	}

	/** The recorded blocks still standing, top-down (so nothing is left hanging); only those in loaded chunks. */
	public static List<BlockPos> cutsLeft(ServerLevel level, Job job) {
		List<BlockPos> list = new ArrayList<>();
		for (long l : job.cut()) {
			if (job.skipped().contains(l)) {
				continue;
			}
			BlockPos p = BlockPos.of(l);
			if (level.isLoaded(p) && !level.getBlockState(p).isAir()) {
				list.add(p);
			}
		}
		list.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
		return list;
	}

	/** The recorded spaces still empty (air, plants or snow), bottom-up. */
	public static List<BlockPos> fillsLeft(ServerLevel level, Job job) {
		List<BlockPos> list = new ArrayList<>();
		for (long l : job.fill()) {
			if (job.skipped().contains(l)) {
				continue;
			}
			BlockPos p = BlockPos.of(l);
			if (level.isLoaded(p) && isFillable(level.getBlockState(p))) {
				list.add(p);
			}
		}
		list.sort(Comparator.comparingInt(BlockPos::getY));
		return list;
	}

	/** A space a fill block can go into: air, or a plant or snow layer that the block simply replaces. */
	public static boolean isFillable(BlockState state) {
		return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	/**
	 * Natural ground a site may be levelled through: earth, sand, gravel, clay and natural stone (the blocks a forager
	 * may quarry), plus grass and dirt of every kind, plants and snow. Nothing a player crafts, and no ore.
	 */
	public static boolean isGradeable(BlockState state) {
		return state.is(ModTags.EARTH_GATHERABLE) || state.is(BlockTags.DIRT) || state.is(BlockTags.GRASS_BLOCKS)
			|| state.is(Blocks.SNOW) || WorldEditGuard.isClearablePlant(state) && state.getFluidState().isEmpty();
	}

	/** What a dip may be filled with: plain dirt, coarse dirt, cobblestone or stone. */
	public static boolean isFillBlock(BlockState state) {
		return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE);
	}

	/** The levelling plan whose box holds this position, if any (for the edit rules). */
	public static Optional<Job> planAt(CampData data, BlockPos pos) {
		for (String planId : data.memory(MEMORY).keySet()) {
			CompoundTag tag = data.memory(MEMORY).getCompoundOrEmpty(planId);
			int[] box = tag.getIntArray("box").orElse(new int[0]);
			if (box.length == 6 && pos.getX() >= box[0] && pos.getX() <= box[3] && pos.getY() >= box[1] && pos.getY() <= box[4]
				&& pos.getZ() >= box[2] && pos.getZ() <= box[5]) {
				Optional<Job> job = job(data, planId);
				if (job.isPresent()) {
					return job;
				}
			}
		}
		return Optional.empty();
	}
}
