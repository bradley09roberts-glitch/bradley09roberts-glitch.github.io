package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Breaking a fall the way players do: a friend falling far with a water bucket pours the water where they are about
 * to land, just before they hit the ground, and scoops it back up once they are down (the bucket is filled again).
 * Only onto firm ground in the open, never where water boils away, never within six blocks of anything
 * player-built, and only where the water can be picked straight up again (the {@code SURVIVAL} edit rules).
 * Checked every tick for every friend, so it costs nothing until a friend is actually falling.
 */
final class WaterClutch {
	/** Falls longer than this (in blocks) hurt; the clutch is for those. */
	private static final double SAFE_FALL = 4.0;
	/** How far below a falling friend the ground is looked for. */
	private static final int LOOK_DOWN = 32;
	/** Water a friend poured to land in, waiting to be scooped up, and when it was poured. */
	private static final Map<CompanionEntity, Pending> POURED = new WeakHashMap<>();

	private record Pending(BlockPos pos, long at) {
	}

	private WaterClutch() {
	}

	/** A {@code CompanionEvents.TICK} listener. */
	static void tick(CompanionEntity c, ServerLevel level) {
		Pending pending = POURED.get(c);
		if (pending != null) {
			scoopUp(c, level, pending);
			return;
		}
		if (c.fallDistance < SAFE_FALL || c.onGround() || c.isInWater() || c.getDeltaMovement().y > -0.4 || c.isPassenger()) {
			return;
		}
		if (!c.actions().has(s -> s.is(Items.WATER_BUCKET))) {
			return;
		}
		BlockPos landing = landing(level, c);
		if (landing == null) {
			return;
		}
		double drop = c.getY() - landing.getY();
		double speed = -c.getDeltaMovement().y;
		if (drop > speed * 2 + 0.5 || c.fallDistance + drop < SAFE_FALL + 1) {
			return; // not yet: pour at the last moment, and only for a fall that would hurt
		}
		pour(c, level, landing);
	}

	/** The air just above the ground the friend is falling towards, or null if there is no good spot. */
	private static @Nullable BlockPos landing(ServerLevel level, CompanionEntity c) {
		Vec3 motion = c.getDeltaMovement();
		BlockPos.MutableBlockPos m = BlockPos.containing(c.getX() + motion.x, c.getY(), c.getZ() + motion.z).mutable();
		for (int i = 0; i < LOOK_DOWN; i++) {
			BlockState s = level.getBlockState(m);
			if (!s.getFluidState().isEmpty()) {
				return null; // landing in water (or lava) anyway
			}
			if (!s.getCollisionShape(level, m).isEmpty()) {
				BlockPos ground = m.immutable();
				BlockPos above = ground.above();
				boolean firm = s.isFaceSturdy(level, ground, Direction.UP);
				return firm && level.getBlockState(above).isAir() ? above : null;
			}
			m.move(Direction.DOWN);
			if (!level.isInWorldBounds(m)) {
				return null;
			}
		}
		return null;
	}

	private static void pour(CompanionEntity c, ServerLevel level, BlockPos landing) {
		BlockState water = Blocks.WATER.defaultBlockState();
		if (!WorldEditGuard.canPlace(c, landing, water, WorldEditGuard.Reason.SURVIVAL).allowed()) {
			return;
		}
		ItemStack bucket = takeWaterBucket(c);
		if (bucket.isEmpty()) {
			return;
		}
		if (!WorldEditGuard.placeBlock(c, landing, water, WorldEditGuard.Reason.SURVIVAL)) {
			giveBack(c, level, bucket);
			return;
		}
		giveBack(c, level, new ItemStack(Items.BUCKET));
		Shelters.addPiece(c, landing);
		POURED.put(c, new Pending(landing, level.getGameTime()));
		level.playSound(null, landing, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.0F);
		c.swingArm();
	}

	/** Once down, the water goes back into the bucket (the edit guard paces this a few ticks after pouring). */
	private static void scoopUp(CompanionEntity c, ServerLevel level, Pending pending) {
		long age = level.getGameTime() - pending.at();
		boolean down = c.onGround() || c.isInWater();
		if (age < 5 || !down && age < 60) {
			return;
		}
		BlockState there = level.getBlockState(pending.pos());
		boolean ours = there.getFluidState().is(FluidTags.WATER) && there.getFluidState().isSource();
		if (!ours) {
			forget(c, level, pending); // already gone (it flowed away, or someone took it)
			return;
		}
		if (WorldEditGuard.transformBlock(c, pending.pos(), Blocks.AIR.defaultBlockState(), WorldEditGuard.Reason.SURVIVAL)) {
			Camp.data(level.getServer()).forgetPlaced(level, pending.pos());
			int empty = c.backpack().remove(s -> s.is(Items.BUCKET), 1);
			if (empty > 0) {
				giveBack(c, level, new ItemStack(Items.WATER_BUCKET));
			}
			level.playSound(null, pending.pos(), SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
			forget(c, level, pending);
			if (c.fallDistance < 1) {
				Speech.say(c, Line.CLUTCH);
			}
			Camp.data(level.getServer()).addStat("falls_broken", 1);
		} else if (age > 200 || c.distanceToSqr(Vec3.atCenterOf(pending.pos())) > 25) {
			forget(c, level, pending); // could not take it back (moved away): it stays, recorded as theirs
		}
	}

	private static void forget(CompanionEntity c, ServerLevel level, Pending pending) {
		POURED.remove(c);
		Shelters.removePiece(c, pending.pos());
	}

	/** Takes a water bucket from the hand or the backpack. */
	private static ItemStack takeWaterBucket(CompanionEntity c) {
		ItemStack fromPack = c.backpack().take(s -> s.is(Items.WATER_BUCKET), 1);
		if (!fromPack.isEmpty()) {
			return fromPack;
		}
		ItemStack hand = c.getMainHandItem();
		if (hand.is(Items.WATER_BUCKET)) {
			return hand.split(1);
		}
		return ItemStack.EMPTY;
	}

	private static void giveBack(CompanionEntity c, ServerLevel level, ItemStack stack) {
		ItemStack left = c.backpack().insert(stack);
		if (!left.isEmpty()) {
			c.spawnAtLocation(level, left);
		}
	}

	/** Forgets pending water (a server stopping). */
	static void clear() {
		POURED.clear();
	}
}
