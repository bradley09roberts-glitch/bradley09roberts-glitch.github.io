package io.github.bradley09roberts.hardcorefriends.ai.role.forage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.role.farm.Ground;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.TreeFinder;
import io.github.bradley09roberts.hardcorefriends.world.TreeFinder.Tree;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * What Rowan knows about the land around the camp, shared by her routines. Searches are cached and spread out:
 * every 5 seconds one tree search (24 blocks around Rowan, a remembered tree, or one of eight points on the
 * gathering ring, in turn) and one berry search around Rowan.
 */
public final class ForageContext {
	private static final int SCAN_INTERVAL = 100;
	private static final int TREE_RADIUS = 24;
	/** Taller trees cannot be felled completely from the ground. */
	private static final int MAX_TREE_HEIGHT = 6;
	private static final int RING_POINTS = 8;
	private static final int BERRY_RADIUS = 24;
	private static final int BERRY_DY = 4;
	private static final long FELLED_MEMORY = 20L * 60 * 6;
	private static final long SKIP_TIME = 20L * 60 * 5;

	private long treeScanAt = Long.MIN_VALUE / 2;
	private int scanTurn;
	private @Nullable Tree tree;
	private final Map<BlockPos, Long> skipped = new HashMap<>();

	private long berryScanAt = Long.MIN_VALUE / 2;
	private final List<BlockPos> berries = new ArrayList<>();

	private final Deque<Felled> felled = new ArrayDeque<>();

	/** A tree Rowan cut down, remembered so she can collect saplings, apples and sticks as its leaves fall. */
	public record Felled(BlockPos pos, long at) {
	}

	// ------------------------------------------------------------------ trees

	/** Trees may only be felled outside the camp (the camp's greenery is Terra's) but inside the gathering ring. */
	public static boolean mayFell(CompanionEntity c, BlockPos pos) {
		return WorldEditGuard.inResourceZone(c, pos) && !WorldEditGuard.inCamp(c, pos);
	}

	/** The nearest known fellable tree, refreshing the search every 5 seconds. */
	public @Nullable Tree tree(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (tree != null && (!level.isLoaded(tree.base()) || !level.getBlockState(tree.base()).is(BlockTags.LOGS)
			|| isSkipped(tree.base(), now))) {
			tree = null;
		}
		if (now - treeScanAt >= SCAN_INTERVAL) {
			treeScanAt = now;
			scanTrees(c, level, now);
		}
		return tree;
	}

	private void scanTrees(CompanionEntity c, ServerLevel level, long now) {
		skipped.values().removeIf(until -> until < now);
		CampData data = Camp.data(level.getServer());
		BlockPos centre = scanCentre(c, level, data);
		if (centre == null || !level.isLoaded(centre)) {
			return;
		}
		Optional<Tree> found = TreeFinder.nearest(level, centre, TREE_RADIUS, MAX_TREE_HEIGHT,
			pos -> mayFell(c, pos) && !isSkipped(pos, now));
		BlockPos here = c.blockPosition();
		if (found.isPresent() && (tree == null || found.get().base().distSqr(here) < tree.base().distSqr(here))) {
			tree = found.get();
		}
		// Forget remembered trees that are gone.
		for (CampData.Poi poi : List.copyOf(data.pois())) {
			if (poi.type.equals("tree") && poi.pos.distSqr(centre) <= TREE_RADIUS * TREE_RADIUS && level.isLoaded(poi.pos)
				&& !logsAround(level, poi.pos)) {
				data.removePoi(poi);
			}
		}
	}

	private static boolean logsAround(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
			if (level.getBlockState(p).is(BlockTags.LOGS)) {
				return true;
			}
		}
		return false;
	}

	/** Takes turns: around Rowan, around a remembered tree, and around a point on the gathering ring. */
	private @Nullable BlockPos scanCentre(CompanionEntity c, ServerLevel level, CampData data) {
		int turn = scanTurn++ % 3;
		BlockPos here = c.blockPosition();
		if (turn == 1) {
			Optional<CampData.Poi> poi = data.pois().stream()
				.filter(p -> p.type.equals("tree") && mayFell(c, p.pos) && !isSkipped(p.pos, level.getGameTime()))
				.min(Comparator.comparingDouble(p -> p.pos.distSqr(here)));
			if (poi.isPresent()) {
				return poi.get().pos;
			}
			turn = 2;
		}
		if (turn == 2) {
			BlockPos home = c.homePos();
			int distance = Camp.radius(data) + 14;
			double angle = (scanTurn / 3 % RING_POINTS) * (Math.PI * 2 / RING_POINTS);
			int x = home.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = home.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!level.hasChunkAt(x, z)) {
				return null;
			}
			int y = Ground.surfaceY(level, x, z, home.getY());
			return new BlockPos(x, y == Ground.NONE ? home.getY() : y + 1, z);
		}
		return here;
	}

	private boolean isSkipped(BlockPos pos, long now) {
		Long until = skipped.get(pos);
		return until != null && until > now;
	}

	/** Ignores a tree for a few minutes, e.g. when Rowan could not reach it. */
	public void skip(BlockPos base, long now) {
		skipped.put(base.immutable(), now + SKIP_TIME);
		if (tree != null && tree.base().equals(base)) {
			tree = null;
		}
	}

	/** Remembers a felled tree and clears the cached target. */
	public void noteFelled(BlockPos base, long now) {
		felled.addLast(new Felled(base.immutable(), now));
		while (felled.size() > 8) {
			felled.removeFirst();
		}
		tree = null;
		treeScanAt = Long.MIN_VALUE / 2;
	}

	// ---------------------------------------------------------------- berries

	/** Sweet berry bushes with berries (age 2 or 3) within 24 blocks in the gathering area, nearest first. */
	public List<BlockPos> berries(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		if (now - berryScanAt >= SCAN_INTERVAL) {
			berryScanAt = now;
			scanBerries(c, level);
		}
		berries.removeIf(p -> !isRipeBush(level.getBlockState(p)));
		return berries;
	}

	public void invalidateBerries() {
		berryScanAt = Long.MIN_VALUE / 2;
	}

	public static boolean isRipeBush(BlockState state) {
		return state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) >= 2;
	}

	private void scanBerries(CompanionEntity c, ServerLevel level) {
		berries.clear();
		BlockPos here = c.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -BERRY_RADIUS; dx <= BERRY_RADIUS; dx++) {
			for (int dz = -BERRY_RADIUS; dz <= BERRY_RADIUS; dz++) {
				if (dx * dx + dz * dz > BERRY_RADIUS * BERRY_RADIUS || !level.hasChunkAt(here.getX() + dx, here.getZ() + dz)) {
					continue;
				}
				for (int dy = -BERRY_DY; dy <= BERRY_DY; dy++) {
					m.set(here.getX() + dx, here.getY() + dy, here.getZ() + dz);
					if (isRipeBush(level.getBlockState(m)) && WorldEditGuard.inResourceZone(c, m)) {
						berries.add(m.immutable());
					}
				}
			}
		}
		berries.sort(Comparator.comparingDouble(p -> p.distSqr(here)));
	}

	// ------------------------------------------------------------ dropped items

	/** Saplings, apples and sticks lying near trees Rowan felled in the last few minutes. */
	public List<ItemEntity> fallenGoods(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		List<ItemEntity> found = new ArrayList<>();
		for (Iterator<Felled> it = felled.iterator(); it.hasNext();) {
			Felled f = it.next();
			if (now - f.at() > FELLED_MEMORY) {
				it.remove();
				continue;
			}
			if (!level.isLoaded(f.pos())) {
				continue;
			}
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(f.pos()).inflate(8),
				e -> e.isAlive() && !e.hasPickUpDelay() && isForageable(e.getItem()))) {
				if (!found.contains(item) && WorldEditGuard.inResourceZone(c, item.blockPosition())) {
					found.add(item);
				}
			}
		}
		BlockPos here = c.blockPosition();
		found.sort(Comparator.comparingDouble(e -> e.blockPosition().distSqr(here)));
		return found;
	}

	public static boolean isForageable(ItemStack stack) {
		return stack.is(Items.APPLE) || stack.is(Items.STICK) || stack.is(ItemTags.SAPLINGS);
	}
}
