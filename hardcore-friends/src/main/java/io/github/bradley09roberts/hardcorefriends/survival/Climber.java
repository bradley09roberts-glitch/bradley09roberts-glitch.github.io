package io.github.bradley09roberts.hardcorefriends.survival;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Climbs one block straight up the way players do: jump, and place a block where your feet were while in the air.
 * Used to pillar up out of reach and to climb out of a dug-down shelter. The block comes from the backpack and goes
 * through the edit guard ({@code SURVIVAL}). If a jump does not get high enough a few times running (a low ceiling
 * of leaves, a slab underfoot), the friend is lifted the last bit, as long as there is room above.
 */
final class Climber {
	enum Result {
		CLIMBING,
		/** A block was placed under the friend; {@link #lastPlaced()} says where. */
		PLACED,
		NO_BLOCKS,
		BLOCKED
	}

	private static final int TICKS_PER_TRY = 12;
	private static final int TRIES_BEFORE_LIFT = 3;

	private @Nullable BlockPos base;
	private @Nullable BlockPos lastPlaced;
	private int ticks;
	private int tries;

	void reset() {
		base = null;
		ticks = 0;
		tries = 0;
	}

	@Nullable BlockPos lastPlaced() {
		return lastPlaced;
	}

	/** Places the block where the friend's feet were; true when done (the friend is clear of the spot by now). */
	private boolean place(CompanionEntity c, ServerLevel level, BlockPos feet, ItemStack block) {
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return false;
		}
		BlockState state = Shelters.stateOf(block);
		ItemStack template = block.copyWithCount(1);
		if (!c.actions().place(feet, state, s -> ItemStack.isSameItemSameComponents(s, template), WorldEditGuard.Reason.SURVIVAL)) {
			return false;
		}
		lastPlaced = feet;
		reset();
		return true;
	}

	/** Call every tick until it reports {@link Result#PLACED} (one block up) or a reason it cannot. */
	Result step(CompanionEntity c, ServerLevel level) {
		if (base == null) {
			if (!c.onGround()) {
				return Result.CLIMBING; // wait to land first
			}
			base = c.blockPosition();
			ticks = 0;
		}
		BlockPos feet = base;
		BlockPos aboveHead = feet.above(2);
		if (!level.getBlockState(aboveHead).getCollisionShape(level, aboveHead).isEmpty()
			|| !level.getFluidState(aboveHead).isEmpty()) {
			reset();
			return Result.BLOCKED;
		}
		ItemStack block = c.backpack().find(Shelters::isShelterBlock);
		if (block.isEmpty()) {
			reset();
			return Result.NO_BLOCKS;
		}
		c.getNavigation().stop();
		Vec3 motion = c.getDeltaMovement();
		c.setDeltaMovement(0, motion.y, 0);
		if (ticks == 0) {
			c.setPos(feet.getX() + 0.5, c.getY(), feet.getZ() + 0.5);
			c.getJumpControl().jump();
		}
		ticks++;
		if (c.getY() >= feet.getY() + 1.0 && place(c, level, feet, block)) {
			return Result.PLACED;
		}
		if (ticks > TICKS_PER_TRY) {
			ticks = 0;
			if (++tries >= TRIES_BEFORE_LIFT) {
				// The jumps fall short: lift them the last bit (there is room above) and place at once, before they drop.
				c.setPos(feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5);
				c.setDeltaMovement(Vec3.ZERO);
				if (place(c, level, feet, block)) {
					return Result.PLACED;
				}
				reset();
				return Result.BLOCKED;
			}
		}
		return Result.CLIMBING;
	}
}
