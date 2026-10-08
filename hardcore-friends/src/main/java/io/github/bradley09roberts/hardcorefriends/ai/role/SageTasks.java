package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.sage.ObserveTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.sage.ReviewStoresTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * Sage's own routines: keeping an eye on the camp and its people, and reviewing the stores to set the team's focus.
 * Advice is given passively by {@link SageAdvisor}.
 */
public final class SageTasks {
	private SageTasks() {
	}

	/** Fresh task instances for one Sage. */
	public static List<CompanionTask> create() {
		return List.of(new ObserveTask(), new ReviewStoresTask());
	}
}
