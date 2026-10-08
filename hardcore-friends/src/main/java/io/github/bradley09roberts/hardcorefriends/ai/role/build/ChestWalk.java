package io.github.bradley09roberts.hardcorefriends.ai.role.build;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/** Walks a friend to the supply chest. Shared by the workshop jobs (sawing planks, making torches). */
public final class ChestWalk {
	/** Progress of a walk. */
	public enum State {
		WALKING,
		ARRIVED,
		FAILED
	}

	private ChestWalk() {
	}

	/** Call every tick. Once {@link State#ARRIVED}, the chest is within reach. */
	public static State tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<BlockPos> pos = Camp.data(level.getServer()).chestPos();
		if (pos.isEmpty() || SupplyChest.of(level).isEmpty()) {
			return State.FAILED;
		}
		Actions actions = c.actions();
		BlockPos chest = pos.get();
		if (actions.canReach(chest) && c.position().distanceToSqr(Vec3.atBottomCenterOf(chest)) < 9) {
			actions.stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(chest));
			return State.ARRIVED;
		}
		if (actions.walkTo(chest, 2.0)) {
			return State.ARRIVED;
		}
		return actions.isStuck() ? State.FAILED : State.WALKING;
	}

	/** The linked supply chest, if loaded. */
	public static Optional<Container> chest(CompanionEntity c) {
		return SupplyChest.of((ServerLevel) c.level());
	}
}
