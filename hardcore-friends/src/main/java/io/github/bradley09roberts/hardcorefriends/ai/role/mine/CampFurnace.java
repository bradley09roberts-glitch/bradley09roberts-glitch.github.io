package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Finds and remembers the camp's furnace (or blast furnace) within {@value #RADIUS} blocks of the supply chest or
 * the camp centre. Furnaces the friends built themselves are preferred. The search is cached and repeated at most
 * every {@value #SEARCH_INTERVAL} ticks, and the result is shared by the whole team (the camp has one furnace, however
 * many friends look for it).
 */
public final class CampFurnace {
	/** Furnace slots, as in {@link AbstractFurnaceBlockEntity}. */
	public static final int SLOT_INPUT = 0;
	public static final int SLOT_FUEL = 1;
	public static final int SLOT_RESULT = 2;
	public static final int RADIUS = 10;
	private static final int SEARCH_INTERVAL = 200;

	/** What the team knows about the furnace: where it is, and when to look again if nobody knows. */
	private static final class Known {
		private @Nullable BlockPos pos;
		private long nextSearch;
	}

	private static Known known(ServerLevel level) {
		return TeamCache.get(level, "camp.furnace", Known::new);
	}

	/** The furnace position, if one is known and still there. */
	public @Nullable BlockPos pos(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Known known = known(level);
		if (known.pos != null && furnaceAt(level, known.pos) != null) {
			return known.pos;
		}
		known.pos = null;
		long now = level.getGameTime();
		if (now < known.nextSearch) {
			return null;
		}
		known.nextSearch = now + SEARCH_INTERVAL;
		known.pos = search(c, level);
		return known.pos;
	}

	public @Nullable AbstractFurnaceBlockEntity get(CompanionEntity c) {
		BlockPos pos = pos(c);
		return pos == null ? null : furnaceAt((ServerLevel) c.level(), pos);
	}

	/** Forgets the cached furnace so the next call searches again (used by tests and after camp changes). */
	public void forget(CompanionEntity c) {
		Known known = known((ServerLevel) c.level());
		known.pos = null;
		known.nextSearch = 0;
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
