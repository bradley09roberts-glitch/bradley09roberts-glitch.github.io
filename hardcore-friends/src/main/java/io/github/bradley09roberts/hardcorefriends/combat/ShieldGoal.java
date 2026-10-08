package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Raising a shield. A friend with a shield in the off hand raises it (uses it, as a player holds right-click) facing
 * the danger when an archer within {@value #ARCHER_RANGE} blocks is drawing on them or on someone within
 * {@value #NEAR_FRIEND} blocks of them, or a creeper within {@value #CREEPER_RANGE} blocks is swelling to blow. A
 * raised shield blocks hits from the front once it has been up a moment ({@code BLOCKS_ATTACKS}' block delay); it is
 * lowered to strike ({@code CompanionMeleeGoal} drops it just before a blow lands) and to draw a bow, and when the
 * danger has passed. This goal claims no movement or look control, so it runs alongside fighting and fleeing; it turns
 * the friend's head towards the danger, since a shield only covers the front.
 */
public class ShieldGoal extends Goal {
	/** Archers drawing this close are blocked. */
	private static final double ARCHER_RANGE = 20;
	/** An archer drawing on someone this close to the friend may hit them too. */
	private static final double NEAR_FRIEND = 3;
	/** A swelling creeper this close is blocked. */
	private static final double CREEPER_RANGE = 5;
	private static final int SCAN_TICKS = 4;

	private final CompanionEntity companion;
	private @Nullable LivingEntity danger;
	private int scan;

	public ShieldGoal(CompanionEntity companion) {
		this.companion = companion;
		this.setFlags(EnumSet.noneOf(Goal.Flag.class));
	}

	@Override
	public boolean canUse() {
		if (--scan > 0) {
			return false;
		}
		scan = SCAN_TICKS;
		danger = findDanger();
		return danger != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (--scan <= 0) {
			scan = SCAN_TICKS;
			danger = findDanger();
		}
		return danger != null && danger.isAlive();
	}

	/** An archer drawing on this friend or someone beside them, or a creeper about to blow close by; null if none. */
	private @Nullable LivingEntity findDanger() {
		if (!companion.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) || companion.isAsleep()
			|| companion.isRetreating() || drawingBow()) {
			return null;
		}
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : Threats.around(companion, ARCHER_RANGE)) {
			double d = e.distanceToSqr(companion);
			if (d >= bestDist) {
				continue;
			}
			if (e instanceof Creeper creeper) {
				if ((creeper.getSwellDir() > 0 || creeper.isIgnited()) && d <= CREEPER_RANGE * CREEPER_RANGE) {
					best = creeper;
					bestDist = d;
				}
			} else if (e.isUsingItem() && Threats.isRanged(e) && e instanceof Mob mob && aimsNear(mob)) {
				best = e;
				bestDist = d;
			}
		}
		return best;
	}

	/** The archer is drawing on this friend, or on someone within {@value #NEAR_FRIEND} blocks of them. */
	private boolean aimsNear(Mob archer) {
		LivingEntity aim = archer.getTarget();
		return aim != null && (aim == companion || aim.distanceToSqr(companion) <= NEAR_FRIEND * NEAR_FRIEND)
			&& archer.getSensing().hasLineOfSight(companion);
	}

	/** Busy with a bow (drawing, or about to): the shield waits. */
	private boolean drawingBow() {
		return companion.getMainHandItem().is(Items.BOW) && companion.getTarget() != null;
	}

	@Override
	public void start() {
		raise();
		Speech.say(companion, Line.RAISE_SHIELD);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity d = danger;
		if (d == null) {
			return;
		}
		companion.getLookControl().setLookAt(d, 30.0F, 30.0F);
		if (!companion.isUsingItem()) {
			raise(); // lowered to strike: up again
		}
	}

	private void raise() {
		if (!companion.isUsingItem() && companion.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			companion.startUsingItem(InteractionHand.OFF_HAND);
		}
	}

	@Override
	public void stop() {
		danger = null;
		if (companion.isUsingItem() && companion.getUsedItemHand() == InteractionHand.OFF_HAND) {
			companion.stopUsingItem();
		}
	}
}
