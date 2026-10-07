package io.github.bradley09roberts.hardcorefriends.ai.action;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Per-friend, per-tick building blocks for tasks: walking somewhere, mining with real tool speed and wear,
 * placing blocks from the backpack, and reshaping blocks with a tool. Every block change goes through
 * {@link WorldEditGuard}.
 */
public final class Actions {
	public enum Result {
		RUNNING,
		DONE,
		FAILED
	}

	/** How far a friend can reach to break or place, like a player in survival. */
	public static final double REACH = 4.5;
	private static final int REPATH_INTERVAL = 20;
	private static final int STUCK_LIMIT = 120;

	private final CompanionEntity c;

	// walking
	private @Nullable BlockPos walkTarget;
	private int repathTimer;
	private int noProgressTicks;
	private double bestDistance = Double.MAX_VALUE;
	private boolean stuck;

	// mining
	private @Nullable BlockPos miningPos;
	private float miningProgress;
	private int lastCrackStage = -1;
	private int swingTimer;

	public Actions(CompanionEntity companion) {
		this.c = companion;
	}

	// ---------------------------------------------------------------- walking

	public double speed() {
		return 1.0 + c.friendId().speedBonus();
	}

	/** True when the friend's eyes are within reach of the centre of a block. */
	public boolean canReach(BlockPos pos) {
		return c.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH;
	}

	/**
	 * Walks towards a block until within {@code reach} blocks (measured feet-to-block-centre). Returns true once
	 * close enough. Call every tick. Check {@link #isStuck()} to give up.
	 */
	public boolean walkTo(BlockPos target, double reach) {
		double dist = c.position().distanceTo(Vec3.atBottomCenterOf(target));
		if (dist <= reach) {
			c.getNavigation().stop();
			resetWalk();
			return true;
		}
		if (!target.equals(walkTarget)) {
			walkTarget = target.immutable();
			repathTimer = 0;
			noProgressTicks = 0;
			bestDistance = Double.MAX_VALUE;
			stuck = false;
		}
		if (dist < bestDistance - 0.25) {
			bestDistance = dist;
			noProgressTicks = 0;
		} else if (++noProgressTicks > STUCK_LIMIT) {
			stuck = true;
		}
		if (--repathTimer <= 0 || c.getNavigation().isDone()) {
			repathTimer = REPATH_INTERVAL;
			Path path = c.getNavigation().createPath(target, Math.max(0, (int) Math.floor(reach) - 1));
			if (path == null || !c.getNavigation().moveTo(path, speed())) {
				if (++noProgressTicks > STUCK_LIMIT / 2 && c.getNavigation().isDone()) {
					stuck = true;
				}
			}
		}
		return false;
	}

	/** Walks to within reach of an entity. */
	public boolean walkToEntity(Entity target, double reach) {
		if (c.distanceTo(target) <= reach) {
			c.getNavigation().stop();
			return true;
		}
		if (--repathTimer <= 0 || c.getNavigation().isDone()) {
			repathTimer = 10;
			c.getNavigation().moveTo(target, speed());
		}
		return false;
	}

	public boolean isStuck() {
		return stuck;
	}

	public void stopWalking() {
		c.getNavigation().stop();
		resetWalk();
	}

	private void resetWalk() {
		walkTarget = null;
		noProgressTicks = 0;
		bestDistance = Double.MAX_VALUE;
		stuck = false;
	}

	// ----------------------------------------------------------------- mining

	/**
	 * Mines a block over several ticks using the best tool in the backpack, like a player would. Returns
	 * {@link Result#DONE} once broken (drops go into the backpack), {@link Result#FAILED} if out of reach or refused
	 * by the guard. The caller must already be within reach.
	 */
	public Result mine(BlockPos pos, WorldEditGuard.Reason reason) {
		ServerLevel level = (ServerLevel) c.level();
		if (!canReach(pos)) {
			cancelMining();
			return Result.FAILED;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			cancelMining();
			return Result.DONE;
		}
		WorldEditGuard.Verdict verdict = WorldEditGuard.canBreak(c, pos, reason);
		if (!verdict.allowed() && !"pacing".equals(verdict.why())) {
			cancelMining();
			return Result.FAILED;
		}
		if (!pos.equals(miningPos)) {
			cancelMining();
			miningPos = pos.immutable();
			miningProgress = 0;
			equipBestFor(state);
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0) {
			cancelMining();
			return Result.FAILED;
		}
		ItemStack tool = c.getMainHandItem();
		float toolSpeed = tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state);
		boolean correct = !state.requiresCorrectToolForDrops() || (!tool.isEmpty() && tool.isCorrectToolForDrops(state));
		float perTick = hardness == 0 ? 1.0F : (float) (toolSpeed / hardness / (correct ? 30.0F : 100.0F) * Unity.workSpeed(c));
		miningProgress += perTick;
		if (--swingTimer <= 0) {
			c.swingArm();
			swingTimer = 6;
		}
		int stage = (int) (miningProgress * 10.0F) - 1;
		if (stage != lastCrackStage) {
			level.destroyBlockProgress(c.getId(), pos, Math.min(stage, 9));
			lastCrackStage = stage;
		}
		if (miningProgress >= 1.0F) {
			boolean broken = WorldEditGuard.breakBlock(c, pos, reason);
			if (!broken && WorldEditGuard.canBreak(c, pos, reason).why().equals("pacing")) {
				return Result.RUNNING;
			}
			cancelMining();
			return broken ? Result.DONE : Result.FAILED;
		}
		return Result.RUNNING;
	}

	public void cancelMining() {
		if (miningPos != null && c.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(c.getId(), miningPos, -1);
		}
		miningPos = null;
		miningProgress = 0;
		lastCrackStage = -1;
	}

	// ---------------------------------------------------------------- placing

	/**
	 * Places a block using one matching item from the backpack. Returns true on success. The item is returned to
	 * the backpack if placement is refused.
	 */
	public boolean place(BlockPos pos, BlockState state, Predicate<ItemStack> material, WorldEditGuard.Reason reason) {
		if (!canReach(pos)) {
			return false;
		}
		if (!WorldEditGuard.canPlace(c, pos, state, reason).allowed()) {
			return false;
		}
		ItemStack item = c.backpack().take(material, 1);
		if (item.isEmpty()) {
			return false;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (WorldEditGuard.placeBlock(c, pos, state, reason)) {
			c.swingArm();
			return true;
		}
		ItemStack left = c.backpack().insert(item);
		if (!left.isEmpty()) {
			c.spawnAtLocation((ServerLevel) c.level(), left);
		}
		return false;
	}

	/**
	 * Reshapes a block with a tool (hoe for farmland, shovel for paths). The tool loses 1 durability on success.
	 * Pass a null tool tag for bare-hand changes such as picking berries.
	 */
	public boolean transform(BlockPos pos, BlockState newState, WorldEditGuard.Reason reason, @Nullable TagKey<Item> toolTag) {
		if (!canReach(pos)) {
			return false;
		}
		if (toolTag != null && !equip(s -> s.is(toolTag))) {
			return false;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(pos));
		if (!WorldEditGuard.transformBlock(c, pos, newState, reason)) {
			return false;
		}
		c.swingArm();
		if (toolTag != null && !Unity.carefulHands(c)) {
			c.damageMainHandTool(1);
		}
		return true;
	}

	// -------------------------------------------------------------- equipment

	/** True if a matching item is in the main hand or the backpack. */
	public boolean has(Predicate<ItemStack> filter) {
		ItemStack hand = c.getMainHandItem();
		return (!hand.isEmpty() && filter.test(hand)) || c.backpack().has(filter);
	}

	public boolean hasTool(TagKey<Item> toolTag) {
		return has(s -> s.is(toolTag));
	}

	/** Moves a matching item from the backpack to the main hand (swapping the previous one back). */
	public boolean equip(Predicate<ItemStack> filter) {
		ItemStack hand = c.getMainHandItem();
		if (!hand.isEmpty() && filter.test(hand)) {
			return true;
		}
		int slot = c.backpack().slotOf(filter);
		if (slot < 0) {
			return false;
		}
		ItemStack chosen = c.backpack().removeSlot(slot);
		c.setItemSlot(EquipmentSlot.MAINHAND, chosen);
		if (!hand.isEmpty()) {
			ItemStack left = c.backpack().insert(hand);
			if (!left.isEmpty()) {
				c.spawnAtLocation((ServerLevel) c.level(), left);
			}
		}
		return true;
	}

	/** Holds whichever carried item mines this block fastest (or keeps the current item). */
	public void equipBestFor(BlockState state) {
		ItemStack hand = c.getMainHandItem();
		float best = hand.isEmpty() ? 1.0F : hand.getDestroySpeed(state);
		boolean bestCorrect = !hand.isEmpty() && hand.isCorrectToolForDrops(state);
		int bestSlot = -1;
		for (int i = 0; i < c.backpack().capacity(); i++) {
			ItemStack s = c.backpack().get(i);
			if (s.isEmpty() || !s.isDamageableItem()) {
				continue;
			}
			float speed = s.getDestroySpeed(state);
			boolean correct = s.isCorrectToolForDrops(state);
			if ((correct && !bestCorrect) || (correct == bestCorrect && speed > best)) {
				best = speed;
				bestCorrect = correct;
				bestSlot = i;
			}
		}
		if (bestSlot >= 0) {
			ItemStack chosen = c.backpack().get(bestSlot);
			equip(s -> s == chosen);
		}
	}

	/** Clears walking and mining state. Tasks call this from {@code stop()}. */
	public void reset() {
		stopWalking();
		cancelMining();
	}
}
