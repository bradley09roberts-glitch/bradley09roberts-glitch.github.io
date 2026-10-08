package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.guard.GuardTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * Aegis's own routine: standing guard over players and the camp. Fighting itself is handled by the combat reflexes;
 * taking better gear from the supply chest is every friend's job now (Aegis first; see
 * {@link io.github.bradley09roberts.hardcorefriends.ai.role.guard.EquipGearTask}, added by the combat package).
 */
public final class AegisTasks {
	private AegisTasks() {
	}

	/** Fresh task instances for one Aegis. */
	public static List<CompanionTask> create() {
		return List.of(new GuardTask());
	}
}
