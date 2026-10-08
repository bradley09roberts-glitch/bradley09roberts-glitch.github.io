package io.github.bradley09roberts.hardcorefriends.progress;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import io.github.bradley09roberts.hardcorefriends.ai.role.TeamCache;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;

/**
 * Where the camp's work stations for the plan are: the enchanting table (the library's, or one a player set up by the
 * supply chest), the friends' own anvil and the friends' own brewing stand. The anvil and the stand are changed by
 * using them (the anvil wears, the stand fills), so only the ones the friends built count; an enchanting table is only
 * read, so a player's will do. Looking for a player's table is a bounded search, done at most every
 * {@value #SEARCH_INTERVAL} ticks for the whole team.
 */
public final class Stations {
	/** How many bookshelves a table can use, as in vanilla. */
	public static final int MAX_SHELVES = 15;
	private static final int SEARCH_RADIUS = 10;
	private static final int SEARCH_DY = 3;
	private static final int SEARCH_INTERVAL = 600;

	/** The team's memory of a player's enchanting table near the chest. */
	private static final class Found {
		private @Nullable BlockPos table;
		private long searchedAt = Long.MIN_VALUE / 2;
	}

	private Stations() {
	}

	/** The library's table if it stands, else an enchanting table near the supply chest, else null. Camp level only. */
	public static @Nullable BlockPos enchantingTable(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return null;
		}
		Optional<CampData.Site> site = data.site(Structures.LIBRARY);
		if (site.isPresent()) {
			BlockPos pos = Blueprints.at(site.get(), Blueprints.LIBRARY_TABLE);
			if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) {
				return pos;
			}
		}
		Found found = TeamCache.get(level, "progress.table", Found::new);
		long now = level.getGameTime();
		if (found.table != null && (!level.isLoaded(found.table) || !level.getBlockState(found.table).is(Blocks.ENCHANTING_TABLE))) {
			found.table = null;
		}
		if (found.table == null && (now - found.searchedAt >= SEARCH_INTERVAL || now < found.searchedAt)) {
			found.searchedAt = now;
			found.table = search(level, data);
		}
		return found.table;
	}

	private static @Nullable BlockPos search(ServerLevel level, CampData data) {
		BlockPos centre = data.chestPos().or(data::campPos).orElse(null);
		if (centre == null) {
			return null;
		}
		BlockPos best = null;
		int bestPower = -1;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
				if (!level.hasChunkAt(centre.getX() + dx, centre.getZ() + dz)) {
					continue;
				}
				for (int dy = -SEARCH_DY; dy <= SEARCH_DY; dy++) {
					m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					if (level.getBlockState(m).is(Blocks.ENCHANTING_TABLE)) {
						int power = power(level, m);
						if (power > bestPower) {
							bestPower = power;
							best = m.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	/** How many bookshelves power this table, counted as the table counts them (at most 15). */
	public static int power(ServerLevel level, BlockPos table) {
		int shelves = 0;
		for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
			BlockPos shelf = table.offset(offset);
			if (level.isLoaded(shelf) && EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
				shelves++;
			}
		}
		return Math.min(MAX_SHELVES, shelves);
	}

	/** The friends' own anvil (any wear), or null. */
	public static @Nullable BlockPos anvil(ServerLevel level) {
		BlockPos pos = siteBlock(level, Structures.ANVIL, Blueprints.ANVIL);
		if (pos == null || !level.getBlockState(pos).is(BlockTags.ANVIL)) {
			return null;
		}
		return Camp.data(level.getServer()).isPlacedByFriends(level, pos) ? pos : null;
	}

	/** The friends' own brewing stand, or null. */
	public static @Nullable BlockPos brewingStand(ServerLevel level) {
		BlockPos pos = siteBlock(level, Structures.BREWING_STAND, Blueprints.BREWING_STAND);
		if (pos == null || !(level.getBlockEntity(pos) instanceof BrewingStandBlockEntity)) {
			return null;
		}
		return Camp.data(level.getServer()).isPlacedByFriends(level, pos) ? pos : null;
	}

	/** The world position of a one-block station's site, if reserved and loaded in this (the camp's) level. */
	private static @Nullable BlockPos siteBlock(ServerLevel level, String id, Blueprint plan) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return null;
		}
		Optional<CampData.Site> site = data.site(id);
		if (site.isEmpty()) {
			return null;
		}
		BlockPos pos = Blueprint.worldPos(site.get().origin, site.get().rotation, 0, 0, 0);
		return level.isLoaded(pos) ? pos : null;
	}
}
