package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.mine.CampFurnace;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.CollectSmeltedTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.DigMineTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MineExposedOreTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.SmeltTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * Flint's own routines: mining exposed ores, working one staircase mine with branch tunnels, and smelting ore at
 * the camp furnace. Depositing, restocking and returning home at night are shared upkeep jobs.
 */
public final class FlintTasks {
	private FlintTasks() {
	}

	public static List<CompanionTask> create() {
		CampFurnace furnace = new CampFurnace();
		return List.of(
			new MineExposedOreTask(),
			new DigMineTask(),
			new SmeltTask(furnace),
			new CollectSmeltedTask(furnace));
	}
}
