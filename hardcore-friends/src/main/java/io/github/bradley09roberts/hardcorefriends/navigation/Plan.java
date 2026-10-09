package io.github.bradley09roberts.hardcorefriends.navigation;

import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * One way out of trouble that takes a while (swimming to a shore, walking out of a cave, digging a staircase, a
 * temporary step), run by {@link WayOutGoal} while the {@link Wayfinder} says so. A plan only moves the friend and
 * changes blocks through {@code Actions} and the edit guard.
 */
interface Plan {
	/** What a plan says after each tick. */
	enum Status {
		RUNNING,
		DONE,
		FAILED
	}

	/** The kinds of plan, in the order they are usually tried. */
	enum Kind {
		/** Swim to the best way out of the water. */
		SHORE,
		/** Walk the way found to the nearest spot under open sky. */
		CAVE_EXIT,
		/** Put a carried block (or two) underfoot to get up a step, then take it back. */
		STEP,
		/** Dig a staircase up through natural ground. */
		STAIR
	}

	Kind kind();

	/** Gets going; false when there is nothing this plan can do from here (nothing is changed then). */
	boolean start(CompanionEntity c, ServerLevel level);

	Status tick(CompanionEntity c, ServerLevel level);

	/** Always called once the plan ends, however it ends: tidies up (records of blocks to dig, mining under way). */
	void stop(CompanionEntity c);
}
