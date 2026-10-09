package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Natural trees that stand on a reserved building site, for when the camp is in a forest and no tree-free spot
 * exists. The site search records exactly which logs have to go and the box of space the building needs free of
 * leaves. Those trees, and only those, may then be felled inside the camp; every other camp tree stays protected.
 * The record is kept in camp memory per plan, so a restart carries on where the clearing stopped.
 */
public final class SiteClearing {
	private static final String MEMORY = "hardcorefriends.site_clearing";

	/**
	 * A plan's site still to be cleared: the logs to fell, the leaf-free box {minX, minY, minZ, maxX, maxY, maxZ}, and
	 * blocks a friend could not reach and gave up on (left alone, so they never hold the build up for good).
	 */
	public record Job(String planId, List<BlockPos> logs, int[] box, Set<BlockPos> skipped) {
	}

	private SiteClearing() {
	}

	/** Records the trees (all their logs) to fell and the box to clear of leaves for a newly reserved site. */
	public static void reserve(CampData data, String planId, Collection<BlockPos> logs, int[] box) {
		CompoundTag tag = new CompoundTag();
		tag.putLongArray("logs", logs.stream().mapToLong(BlockPos::asLong).toArray());
		tag.putIntArray("box", box);
		data.memory(MEMORY).put(planId, tag);
		data.setDirty();
	}

	public static void forget(CampData data, String planId) {
		if (data.memory(MEMORY).contains(planId)) {
			data.memory(MEMORY).remove(planId);
			data.setDirty();
		}
	}

	/** The clearing job of a plan whose site is reserved and not yet built, if any. */
	public static Optional<Job> job(CampData data, String planId) {
		CompoundTag tag = data.memory(MEMORY).getCompoundOrEmpty(planId);
		if (tag.isEmpty() || data.site(planId).isEmpty() || data.isCompleted(planId)) {
			return Optional.empty();
		}
		long[] packed = tag.getLongArray("logs").orElse(new long[0]);
		int[] box = tag.getIntArray("box").orElse(new int[0]);
		if (box.length != 6) {
			return Optional.empty();
		}
		List<BlockPos> logs = new ArrayList<>(packed.length);
		for (long l : packed) {
			logs.add(BlockPos.of(l));
		}
		Set<BlockPos> skipped = new HashSet<>();
		for (long l : tag.getLongArray("skipped").orElse(new long[0])) {
			skipped.add(BlockPos.of(l));
		}
		return Optional.of(new Job(planId, logs, box, skipped));
	}

	/** Leaves a block alone for good: a friend could not reach it. */
	public static void skip(CampData data, Job job, BlockPos pos) {
		CompoundTag tag = data.memory(MEMORY).getCompound(job.planId()).orElse(null);
		if (tag == null) {
			return;
		}
		long[] old = tag.getLongArray("skipped").orElse(new long[0]);
		long[] grown = java.util.Arrays.copyOf(old, old.length + 1);
		grown[old.length] = pos.asLong();
		tag.putLongArray("skipped", grown);
		job.skipped().add(pos.immutable());
		data.setDirty();
	}

	/** The first plan with clearing still to do, if any. */
	public static Optional<Job> active(ServerLevel level, CampData data) {
		for (String planId : data.memory(MEMORY).keySet()) {
			Optional<Job> job = job(data, planId);
			if (job.isPresent() && pending(level, job.get())) {
				return job;
			}
		}
		return Optional.empty();
	}

	/** True while any recorded log still stands or natural leaves fill the building's space. */
	public static boolean pending(ServerLevel level, Job job) {
		return !standingLogs(level, job).isEmpty() || !leaves(level, job).isEmpty();
	}

	/** The recorded logs that are still logs, bottom-up. Unloaded ones count as standing. */
	public static List<BlockPos> standingLogs(ServerLevel level, Job job) {
		List<BlockPos> list = new ArrayList<>();
		for (BlockPos p : job.logs()) {
			if (job.skipped().contains(p)) {
				continue;
			}
			if (!level.isLoaded(p) || level.getBlockState(p).is(BlockTags.LOGS)) {
				list.add(p);
			}
		}
		list.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
		return list;
	}

	/** Natural leaves inside the building's space. */
	public static List<BlockPos> leaves(ServerLevel level, Job job) {
		int[] b = job.box();
		List<BlockPos> list = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(b[0], b[1], b[2], b[3], b[4], b[5])) {
			if (level.isLoaded(p) && isNaturalLeaves(level.getBlockState(p)) && !job.skipped().contains(p)) {
				list.add(p.immutable());
			}
		}
		return list;
	}

	/** Leaves that grew on a tree, as opposed to leaves a player placed (those are persistent). */
	public static boolean isNaturalLeaves(BlockState state) {
		return state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT)
			&& !state.getValue(LeavesBlock.PERSISTENT);
	}

	/** A readable name for a plan's site in speech (a camp structure's name, or a library plan's). */
	public static String siteName(@Nullable String planId) {
		if (planId == null) {
			return "building";
		}
		for (Structures.Entry e : Structures.ALL) {
			if (e.id().equals(planId)) {
				return e.displayName();
			}
		}
		return "building";
	}
}
