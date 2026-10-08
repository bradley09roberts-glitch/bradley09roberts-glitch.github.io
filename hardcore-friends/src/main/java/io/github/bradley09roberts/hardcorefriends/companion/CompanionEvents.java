package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/**
 * Hooks into a friend's life that the feature packages ({@code combat}, {@code survival}, {@code settler},
 * {@code progress}, {@code expedition}, {@code town}) register from their {@code init()}, so new behaviour plugs in
 * without every feature editing {@link CompanionEntity}. Listeners run on the server thread in the order registered.
 * Per-friend state a feature needs to keep goes in {@link CompanionEntity#extra()} under the feature's own key.
 */
public final class CompanionEvents {
	/** Every server tick of every friend (team members and strangers alike), after the friend's own upkeep. */
	public interface Tick {
		void tick(CompanionEntity companion, ServerLevel level);
	}

	/**
	 * A player right-clicks a friend with the main hand. Return {@link InteractionResult#PASS} to let the next listener
	 * and then the friend's own handling (status, backpack, food and gifts) run; anything else ends it there.
	 */
	public interface Interact {
		InteractionResult interact(CompanionEntity companion, ServerPlayer player, InteractionHand hand);
	}

	/**
	 * A friend is about to take damage (players and friends who cannot hurt them have already been filtered out).
	 * Return the damage to take: the same amount to change nothing, less to soften the blow, 0 or less to cancel it.
	 */
	public interface Hurt {
		float hurt(CompanionEntity companion, ServerLevel level, DamageSource source, float amount);
	}

	/** A friend's melee or ranged hit landed. {@code killed} is true when the target died of it. */
	public interface Hit {
		void hit(CompanionEntity companion, ServerLevel level, Entity target, boolean killed);
	}

	/** A friend died (called once, before their backpack is dropped and the camp is told). */
	public interface Death {
		void died(CompanionEntity companion, ServerLevel level, DamageSource source);
	}

	/** A friend was dismissed from the team with {@code /friends dismiss} (just before they leave the world). */
	public interface Dismissed {
		void dismissed(CompanionEntity companion, ServerLevel level);
	}

	/**
	 * Adds AI goals to a newly created friend. Runs inside the entity's constructor (from {@code registerGoals}), so
	 * only the selectors and the entity's type and level are ready: keep it to {@code addGoal} calls.
	 */
	public interface Goals {
		void addGoals(CompanionEntity companion, GoalSelector goals, GoalSelector targets);
	}

	public static final List<Tick> TICK = new CopyOnWriteArrayList<>();
	public static final List<Interact> INTERACT = new CopyOnWriteArrayList<>();
	public static final List<Hurt> HURT = new CopyOnWriteArrayList<>();
	public static final List<Hit> HIT = new CopyOnWriteArrayList<>();
	public static final List<Death> DEATH = new CopyOnWriteArrayList<>();
	public static final List<Goals> GOALS = new CopyOnWriteArrayList<>();
	public static final List<Dismissed> DISMISSED = new CopyOnWriteArrayList<>();

	private CompanionEvents() {
	}
}
