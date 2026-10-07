package io.github.bradley09roberts.hardcorefriends.ai.task;

import java.util.ArrayList;
import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.AegisTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.FernTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.FlintTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.OakTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.RowanTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.SageTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.ScoutTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.SparkTasks;
import io.github.bradley09roberts.hardcorefriends.ai.role.TerraTasks;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.CommonTasks;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/** Builds the job list for each friend: shared upkeep jobs plus their role's own routines. */
public final class TaskRegistry {
	private TaskRegistry() {
	}

	public static List<CompanionTask> create(FriendId id) {
		List<CompanionTask> tasks = new ArrayList<>(CommonTasks.create(id));
		tasks.addAll(switch (id) {
			case FERN -> FernTasks.create();
			case OAK -> OakTasks.create();
			case FLINT -> FlintTasks.create();
			case SCOUT -> ScoutTasks.create();
			case SPARK -> SparkTasks.create();
			case AEGIS -> AegisTasks.create();
			case SAGE -> SageTasks.create();
			case TERRA -> TerraTasks.create();
			case ROWAN -> RowanTasks.create();
		});
		return tasks;
	}
}
