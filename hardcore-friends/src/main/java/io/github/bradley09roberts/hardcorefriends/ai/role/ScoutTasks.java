package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.scout.ExploreTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.ReportTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * Scout's own routines: exploring rings around camp by day while noting ores, trees, lava and villages, then
 * reporting the finds to a player in camp. Hazard warnings run separately in {@link ScoutSenses}.
 */
public final class ScoutTasks {
	private ScoutTasks() {
	}

	public static List<CompanionTask> create() {
		return List.of(new ExploreTask(), new ReportTask());
	}
}
