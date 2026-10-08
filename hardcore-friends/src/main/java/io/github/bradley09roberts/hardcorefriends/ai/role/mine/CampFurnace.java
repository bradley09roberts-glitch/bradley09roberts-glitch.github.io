package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Finds and remembers the camp's furnace (or blast furnace) within {@value #RADIUS} blocks of the supply chest or
 * the camp centre. Furnaces the friends built themselves are preferred. The search is cached and repeated at most
 * every {@value #SEARCH_INTERVAL} ticks. One instance is shared by Flint's smelting routines.
 */
public final class CampFurnace {
	/** Furnace slots, as in {@link AbstractFurnaceBlockEntity}. */
	public static final int SLOT_INPUT = 0;
	public static final int SLOT_FUEL = 1;
	public static final int SLOT_RESULT = 2;
	public static final int RADIUS = 10;
	private static final int SEARCH_INTERVAL = 200;

	private @Nullable BlockPos cached;
	private long nextSearch;

	/** The furnace position, if one is known and still there. */
	public @Nullable BlockPos pos(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (cached != null && furnaceAt(level, cached) != null) {
			return cached;
		}
		cached = null;
		long now = level.getGameTime();
		if (now < nextSearch) {
			return null;
		}
		nextSearch = now + SEARCH_INTERVAL;
		cached = search(c, level);
		return cached;
	}

	public @Nullable AbstractFurnaceBlockEntity get(CompanionEntity c) {
		BlockPos pos = pos(c);
		return pos == null ? null : furnaceAt((ServerLevel) c.level(), pos);
	}

	/** Forgets the cached furnace so the next call searches again (used by tests and after camp changes). */
	public void forget() {
		cached = null;
		nextSearch = 0;
	}

	public static @Nullable AbstractFurnaceBlockEntity furnaceAt(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.FURNACE) && !state.is(Blocks.BLAST_FURNACE)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace ? furnace : null;
	}

	private static @Nullable BlockPos search(CompanionEntity c, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		List<BlockPos> centres = new ArrayList<>();
		if (Camp.isCampLevel(level, data)) {
			data.chestPos().ifPresent(centres::add);
		}
		centres.add(c.homePos());
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (BlockPos centre : centres) {
			for (int dx = -RADIUS; dx <= RADIUS; dx++) {
				for (int dz = -RADIUS; dz <= RADIUS; dz++) {
					for (int dy = -4; dy <= 4; dy++) {
						m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
						if (furnaceAt(level, m) == null) {
							continue;
						}
						// Prefer furnaces the friends built, then the one nearest the centre.
						double score = m.distSqr(centre) + (data.isPlacedByFriends(level, m) ? 0 : 10_000);
						if (score < bestScore) {
							bestScore = score;
							best = m.immutable();
						}
					}
				}
			}
		}
		return best;
	}
}
