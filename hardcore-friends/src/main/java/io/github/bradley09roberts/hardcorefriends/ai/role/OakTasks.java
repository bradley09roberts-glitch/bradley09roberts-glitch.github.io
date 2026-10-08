package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.BuildTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ClearSiteTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ProcessWoodTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.RepairTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/** Oak's own routines: building the camp's improvements, repairing them and sawing planks for the team. */
public final class OakTasks {
	private OakTasks() {
	}

	public static List<CompanionTask> create() {
		// While a build waits for its site to be cleared of trees, Oak lends a hand.
		return List.of(new BuildTask(), new RepairTask(), new ProcessWoodTask(), new ClearSiteTask(45));
	}
}
