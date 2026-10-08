package io.github.bradley09roberts.hardcorefriends.ai.role.guard;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;

/**
 * Locates the lookout spot on top of the team's watchtower: the highest floor block the friends placed near the
 * tower site with two blocks of headroom, and the foot of its ladder. Works with whatever shape the blueprint has.
 */
public final class Watchtower {
	/** Where to stand at night, and the lowest ladder block to climb from (null when there is no ladder). */
	public record Lookout(BlockPos stand, @Nullable BlockPos ladderFoot) {
	}

	private static final int HALF_WIDTH = 4;
	private static final int MIN_HEIGHT = 3;
	private static final int MAX_HEIGHT = 16;

	private Watchtower() {
	}

	/** The lookout, or null when no watchtower site exists or nothing tall has been built there yet. */
	public static @Nullable Lookout find(ServerLevel level, CampData data) {
		CampData.Site site = data.site(Structures.WATCHTOWER).orElse(null);
		if (site == null) {
			return null;
		}
		BlockPos origin = site.origin;
		if (!level.isLoaded(origin)) {
			return null;
		}
		BlockPos stand = null;
		BlockPos ladder = null;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dy = MAX_HEIGHT; dy >= -1; dy--) {
			for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
				for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
					m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState state = level.getBlockState(m);
					if (stand == null && dy >= MIN_HEIGHT && data.isPlacedByFriends(level, m)
						&& state.isFaceSturdy(level, m, Direction.UP)
						&& level.getBlockState(m.above()).getCollisionShape(level, m.above()).isEmpty()
						&& level.getBlockState(m.above(2)).getCollisionShape(level, m.above(2)).isEmpty()
						&& !level.getBlockState(m.above()).is(BlockTags.CLIMBABLE)) {
						stand = m.above().immutable();
					}
					if (state.is(BlockTags.CLIMBABLE) && dy <= MIN_HEIGHT) {
						ladder = m.immutable(); // keeps the lowest one, since dy counts down
					}
				}
			}
		}
		return stand == null ? null : new Lookout(stand, ladder);
	}
}
