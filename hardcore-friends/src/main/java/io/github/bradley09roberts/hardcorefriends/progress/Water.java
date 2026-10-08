package io.github.bradley09roberts.hardcorefriends.progress;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.CampSurvey;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Water for the plan's work: buckets for casting obsidian, bottles for brewing. A friend fills a bucket only from a
 * source that tops itself up straight away (part of a pool, with two or more sources beside it and solid ground or
 * water under it, which is vanilla's rule for water turning back into a source), so the world never changes, and fills
 * bottles from any still water, which never uses up the source in vanilla either. Known water comes from the farm's
 * camp survey, or a small surface scan round the camp made at most every {@value #SCAN_INTERVAL} ticks.
 */
public final class Water {
	private static final int SCAN_RADIUS = 24;
	private static final int SCAN_INTERVAL = 1200;
	private static final int MAX_FOUND = 24;

	/** The team's memory of surface water round the camp. */
	private static final class Found {
		private List<BlockPos> water = List.of();
		private long scannedAt = Long.MIN_VALUE / 2;
	}

	private Water() {
	}

	/** A still water source block (not flowing, not waterlogged). */
	public static boolean isSource(BlockState state) {
		return state.is(Blocks.WATER) && state.getFluidState().isSource();
	}

	/**
	 * True for a water source that is part of a pool and refills at once when a bucketful is taken: two or more
	 * sources beside it, and solid ground or a water source under it.
	 */
	public static boolean refills(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !isSource(level.getBlockState(pos))) {
			return false;
		}
		int sources = 0;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = pos.relative(d);
			if (level.isLoaded(n) && isSource(level.getBlockState(n))) {
				sources++;
			}
		}
		BlockState under = level.getBlockState(pos.below());
		return sources >= 2 && (under.isSolid() || isSource(under));
	}

	/** Known still water near the camp, nearest {@code near} first; bucket filling asks for refilling sources only. */
	public static @Nullable BlockPos find(ServerLevel level, BlockPos near, boolean refillingOnly) {
		List<BlockPos> candidates = new java.util.ArrayList<>(CampSurvey.of(level).water());
		candidates.addAll(scanned(level, near));
		candidates.sort(Comparator.comparingDouble(p -> p.distSqr(near)));
		for (BlockPos p : candidates) {
			if (refillingOnly ? refills(level, p) : level.isLoaded(p) && isSource(level.getBlockState(p))) {
				return p;
			}
		}
		return null;
	}

	private static List<BlockPos> scanned(ServerLevel level, BlockPos near) {
		Found found = TeamCache.get(level, "progress.water", Found::new);
		long now = level.getGameTime();
		if (now - found.scannedAt >= SCAN_INTERVAL || now < found.scannedAt) {
			found.scannedAt = now;
			found.water = scan(level, near);
		}
		return found.water;
	}

	/** Surface water sources within {@value #SCAN_RADIUS} blocks: one heightmap lookup and a block read per column. */
	private static List<BlockPos> scan(ServerLevel level, BlockPos near) {
		List<BlockPos> list = new java.util.ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS && list.size() < MAX_FOUND; dx += 2) {
			for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS && list.size() < MAX_FOUND; dz += 2) {
				int x = near.getX() + dx;
				int z = near.getZ() + dz;
				if (!level.hasChunkAt(x, z)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
				m.set(x, top, z);
				if (Math.abs(top - near.getY()) <= 12 && isSource(level.getBlockState(m))) {
					list.add(m.immutable());
				}
			}
		}
		return list;
	}

	/** Turns one carried empty bucket into a water bucket from the source in reach. Returns true on success. */
	public static boolean fillBucket(CompanionEntity c, BlockPos source) {
		ServerLevel level = (ServerLevel) c.level();
		if (!refills(level, source) || !c.actions().canReach(source)) {
			return false;
		}
		ItemStack empty = c.backpack().take(s -> s.is(Items.BUCKET), 1);
		if (empty.isEmpty()) {
			return false;
		}
		give(c, new ItemStack(Items.WATER_BUCKET));
		c.getLookControl().setLookAt(Vec3.atCenterOf(source));
		c.swingArm();
		level.playSound(null, source, SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
		return true;
	}

	/** Fills up to {@code max} carried glass bottles with water from the still water in reach. Returns how many. */
	public static int fillBottles(CompanionEntity c, BlockPos source, int max) {
		ServerLevel level = (ServerLevel) c.level();
		if (!level.isLoaded(source) || !isSource(level.getBlockState(source)) || !c.actions().canReach(source)) {
			return 0;
		}
		ItemStack bottles = c.backpack().take(s -> s.is(Items.GLASS_BOTTLE), max);
		if (bottles.isEmpty()) {
			return 0;
		}
		for (int i = 0; i < bottles.getCount(); i++) {
			give(c, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(source));
		c.swingArm();
		level.playSound(null, source, SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
		return bottles.getCount();
	}

	/** True for a bottle of plain water. */
	public static boolean isWaterBottle(ItemStack s) {
		if (!s.is(Items.POTION)) {
			return false;
		}
		PotionContents contents = s.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		return contents != null && contents.is(Potions.WATER);
	}

	private static void give(CompanionEntity c, ItemStack stack) {
		ItemStack left = c.backpack().insert(stack);
		if (!left.isEmpty()) {
			c.spawnAtLocation((ServerLevel) c.level(), left);
		}
	}
}
