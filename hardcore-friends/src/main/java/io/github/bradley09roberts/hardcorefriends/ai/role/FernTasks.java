package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.farm.BakeBreadTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.BoneMealTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.FarmContext;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.FarmPlotTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.HarvestTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.ReplantTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.TillTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * Fern's own routines: harvesting and replanting, sowing empty farmland, tilling near water, laying out the farm
 * plot, baking bread and using bone meal. They share one {@link FarmContext}, so the fields are surveyed once.
 */
public final class FernTasks {
	private FernTasks() {
	}

	public static List<CompanionTask> create() {
		FarmContext farm = new FarmContext();
		return List.of(
			new HarvestTask(farm),
			new ReplantTask(farm),
			new TillTask(farm),
			new FarmPlotTask(farm),
			new BakeBreadTask(farm),
			new BoneMealTask(farm));
	}
}
