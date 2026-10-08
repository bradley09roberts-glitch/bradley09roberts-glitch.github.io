package io.github.bradley09roberts.hardcorefriends.ai.role.terra;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.CampData;

/**
 * What Terra has planted, remembered in {@link CampData#memory} under {@value #MEMORY}: flower positions and tree
 * positions (a sapling, or the trunk it grew into).
 */
public final class Garden {
	public static final String MEMORY = "terra.garden";
	private static final String FLOWERS = "flowers";
	private static final String TREES = "trees";

	private Garden() {
	}

	public static List<BlockPos> flowers(CampData data) {
		return read(data, FLOWERS);
	}

	public static List<BlockPos> trees(CampData data) {
		return read(data, TREES);
	}

	public static void addFlower(CampData data, BlockPos pos) {
		add(data, FLOWERS, pos);
	}

	public static void addTree(CampData data, BlockPos pos) {
		add(data, TREES, pos);
	}

	/**
	 * Forgets plants that are gone (picked, trampled or felled) and returns how many of each are still growing as
	 * {@code {flowers, trees}}. Positions in unloaded chunks are kept and counted.
	 */
	public static int[] prune(ServerLevel level, CampData data) {
		List<BlockPos> flowers = new ArrayList<>();
		for (BlockPos p : flowers(data)) {
			if (!level.isLoaded(p) || level.getBlockState(p).is(BlockTags.FLOWERS)) {
				flowers.add(p);
			}
		}
		List<BlockPos> trees = new ArrayList<>();
		for (BlockPos p : trees(data)) {
			BlockState s = level.isLoaded(p) ? level.getBlockState(p) : null;
			if (s == null || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.LOGS)) {
				trees.add(p);
			}
		}
		write(data, FLOWERS, flowers);
		write(data, TREES, trees);
		return new int[] {flowers.size(), trees.size()};
	}

	private static List<BlockPos> read(CampData data, String key) {
		List<BlockPos> out = new ArrayList<>();
		data.memory(MEMORY).getLongArray(key).ifPresent(a -> {
			for (long l : a) {
				out.add(BlockPos.of(l));
			}
		});
		return out;
	}

	private static void add(CampData data, String key, BlockPos pos) {
		List<BlockPos> list = read(data, key);
		if (!list.contains(pos)) {
			list.add(pos.immutable());
			write(data, key, list);
		}
	}

	private static void write(CampData data, String key, List<BlockPos> list) {
		long[] a = new long[list.size()];
		for (int i = 0; i < a.length; i++) {
			a[i] = list.get(i).asLong();
		}
		CompoundTag tag = data.memory(MEMORY);
		tag.putLongArray(key, a);
		data.setDirty();
	}
}
