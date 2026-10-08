package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.guard.EquipGearTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.guard.GuardTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * Aegis's own routines: taking better weapons, armour and a shield from the supply chest, and standing guard over
 * players and the camp. Fighting itself is handled by the combat reflexes.
 */
public final class AegisTasks {
	private AegisTasks() {
	}

	/** Fresh task instances for one Aegis. */
	public static List<CompanionTask> create() {
		return List.of(new EquipGearTask(), new GuardTask());
	}
}
