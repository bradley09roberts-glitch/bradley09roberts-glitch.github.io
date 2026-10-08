package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Shooting with a bow. Runs at the melee goal's priority with the same move and look controls, and the two never
 * run together: the melee goal stands aside whenever {@link Archery#prefersBow} says this friend would rather shoot
 * their target from where they are, and this goal stops as soon as it would not.
 *
 * <p>The friend holds the bow, gets within {@value Archery#BOW_RANGE} blocks and in sight of the target, and stands
 * still to shoot; a target out of sight or out of range is walked towards. Each arrow takes a full draw of
 * {@value #DRAW_TICKS} ticks, as a player's does, then a short pause. While anyone or anything that must not be hit
 * stands in or near the arrow's flight ({@link LineOfFire}), the friend holds the draw and steps sideways for a clear
 * shot, and never lets go. Arrows come from the backpack ({@link Archery#shoot}).
 */
public class BowAttackGoal extends Goal {
	/** A full draw, as a player's (and a skeleton's) bow takes. */
	private static final int DRAW_TICKS = 20;
	/** The pause between arrows (a skeleton's on Hard). */
	private static final int RELOAD_TICKS = 20;
	/** How often the line of fire is checked. */
	private static final int LINE_CHECK_TICKS = 5;
	/** A draw held this long without a shot (out of sight, or someone in the way) is let down. */
	private static final int HOLD_LIMIT = 60;

	/** How often the way to a target out of sight or range is worked out again. */
	private static final int REPATH_TICKS = 10;

	private final CompanionEntity companion;
	private int reload;
	private int lineCheck;
	private boolean clear;
	private int repath;
	private int strafeTicks;
	private boolean strafeLeft;
	/** What was in hand before the bow, taken up again when the fight is over. */
	private @Nullable Item heldBefore;

	public BowAttackGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = companion.getTarget();
		return target != null && target.isAlive() && !companion.isAsleep() && Archery.prefersBow(companion, target);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		reload = 0;
		lineCheck = 0;
		clear = false;
		repath = 0;
		strafeTicks = 0;
		ItemStack hand = companion.getMainHandItem();
		heldBefore = hand.isEmpty() || hand.is(Items.BOW) ? null : hand.getItem();
		Archery.holdBow(companion);
		LivingEntity target = companion.getTarget();
		if (target != null) {
			Speech.say(companion, Line.DRAW_BOW, target.getName().getString());
		}
	}

	@Override
	public void stop() {
		if (companion.isUsingItem() && companion.getUsedItemHand() == InteractionHand.MAIN_HAND) {
			companion.stopUsingItem();
		}
		companion.getNavigation().stop();
		companion.getMoveControl().strafe(0.0F, 0.0F);
		Item before = heldBefore;
		heldBefore = null;
		LivingEntity target = companion.getTarget();
		if (before != null && (target == null || !target.isAlive()) && companion.getMainHandItem().is(Items.BOW)) {
			companion.actions().equip(s -> s.is(before)); // the fight is over: back to the tool they had in hand
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = companion.getTarget();
		if (target == null || !(companion.level() instanceof ServerLevel level)) {
			return;
		}
		if (!companion.getMainHandItem().is(Items.BOW) && !Archery.holdBow(companion)) {
			return; // the bow broke: the goal ends on its own (no bow, no shooting)
		}
		companion.getLookControl().setLookAt(target, 30.0F, 30.0F);
		boolean sight = companion.getSensing().hasLineOfSight(target);
		double dist = companion.distanceTo(target);
		boolean inRange = dist <= Archery.BOW_RANGE;
		if (--lineCheck <= 0) {
			lineCheck = LINE_CHECK_TICKS;
			clear = sight && inRange && LineOfFire.clear(companion, target);
		}
		move(target, sight, inRange);
		if (companion.isUsingItem()) {
			if (companion.getUsedItemHand() != InteractionHand.MAIN_HAND) {
				companion.stopUsingItem(); // lowering the shield to draw
				return;
			}
			int drawn = companion.getTicksUsingItem();
			if (drawn >= DRAW_TICKS && sight && clear) {
				companion.stopUsingItem();
				if (Archery.shoot(companion, level, target, BowItem.getPowerForTime(drawn))) {
					reload = RELOAD_TICKS;
				}
			} else if (drawn > DRAW_TICKS + HOLD_LIMIT) {
				companion.stopUsingItem(); // no shot for a while: ease the string
				reload = RELOAD_TICKS;
			}
			return;
		}
		if (--reload <= 0 && sight && inRange && clear) {
			companion.startUsingItem(InteractionHand.MAIN_HAND);
			companion.markEngaged();
		}
	}

	/** Closes in on a target out of sight or range, stands still to shoot, and steps sideways when the line is blocked. */
	private void move(LivingEntity target, boolean sight, boolean inRange) {
		if (!sight || !inRange) {
			strafeTicks = 0;
			if (--repath <= 0) {
				repath = REPATH_TICKS;
				companion.getNavigation().moveTo(target, 1.0);
			}
			return;
		}
		repath = 0;
		companion.getNavigation().stop();
		if (clear) {
			strafeTicks = 0;
			companion.getMoveControl().strafe(0.0F, 0.0F);
			return;
		}
		if (strafeTicks <= 0) {
			strafeTicks = 20 + companion.getRandom().nextInt(20);
			strafeLeft = companion.getRandom().nextBoolean();
		}
		strafeTicks--;
		companion.getMoveControl().strafe(0.0F, strafeLeft ? 0.5F : -0.5F);
	}
}
