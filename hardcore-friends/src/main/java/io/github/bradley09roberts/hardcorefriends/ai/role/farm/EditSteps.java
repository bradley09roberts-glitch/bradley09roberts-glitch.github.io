package io.github.bradley09roberts.hardcorefriends.ai.role.farm;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Wraps the guarded block edits in {@link io.github.bradley09roberts.hardcorefriends.ai.action.Actions} so a routine
 * can tell "refused" apart from "wait a moment": the guard paces each friend to one edit every few ticks.
 */
public final class EditSteps {
	/** Outcome of one attempt. */
	public enum Step {
		DONE,
		WAIT,
		FAILED
	}

	private EditSteps() {
	}

	/** Places a block from the backpack (see {@code Actions.place}). The caller must already be within reach. */
	public static Step place(CompanionEntity c, BlockPos pos, BlockState state, Predicate<ItemStack> item, WorldEditGuard.Reason reason) {
		WorldEditGuard.Verdict v = WorldEditGuard.canPlace(c, pos, state, reason);
		if (!v.allowed()) {
			return paced(v) ? Step.WAIT : Step.FAILED;
		}
		return c.actions().place(pos, state, item, reason) ? Step.DONE : Step.FAILED;
	}

	/** Reshapes a block (see {@code Actions.transform}). The caller must already be within reach. */
	public static Step transform(CompanionEntity c, BlockPos pos, BlockState newState, WorldEditGuard.Reason reason,
			@Nullable TagKey<Item> tool) {
		WorldEditGuard.Verdict v = WorldEditGuard.canTransform(c, pos, newState, reason);
		if (!v.allowed()) {
			return paced(v) ? Step.WAIT : Step.FAILED;
		}
		return c.actions().transform(pos, newState, reason, tool) ? Step.DONE : Step.FAILED;
	}

	/** True when the guard only refused because this friend edited a block a moment ago. */
	public static boolean paced(WorldEditGuard.Verdict verdict) {
		return "pacing".equals(verdict.why());
	}
}
