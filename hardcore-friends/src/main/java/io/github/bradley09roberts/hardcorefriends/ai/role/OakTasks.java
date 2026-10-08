package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.BuildTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ClearSiteTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ProcessWoodTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.RepairTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * The builder's routines: building the camp's improvements, repairing them, sawing planks for the team, and clearing
 * the trees off his own building site while the build waits on it (the forager does that too, more eagerly).
 */
public final class OakTasks {
	private OakTasks() {
	}

	public static List<CompanionTask> create() {
		return List.of(new BuildTask(), new RepairTask(), new ProcessWoodTask(), new ClearSiteTask(45));
	}
}
