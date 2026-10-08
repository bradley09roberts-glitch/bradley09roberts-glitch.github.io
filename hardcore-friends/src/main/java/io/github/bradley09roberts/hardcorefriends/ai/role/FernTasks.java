package io.github.bradley09roberts.hardcorefriends.ai.role;

import java.util.List;

import io.github.bradley09roberts.hardcorefriends.ai.role.farm.BakeBreadTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.BoneMealTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.FarmContext;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.FarmPlotTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.HarvestTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.ReplantTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.TillTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.BreedTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.BringAnimalTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.ButcherTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.CookMeatTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.HuntTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.ShutGateTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;

/**
 * The farmer's routines (Fern's, and anyone's who helps or stands in for her): harvesting and replanting, sowing
 * empty farmland, tilling near water, laying out the farm plot, baking bread and using bone meal; and keeping
 * livestock (see {@code ai.role.ranch}): bringing wild animals home to the pen, breeding them, butchering the surplus,
 * cooking the meat on the campfire, shutting the pen gate, and hunting in the gathering ring when food is short. One
 * friend's farming routines share one {@link FarmContext}, so the fields are surveyed once.
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
			new BoneMealTask(farm),
			new CookMeatTask(),
			new BringAnimalTask(),
			new BreedTask(),
			new ButcherTask(),
			new ShutGateTask(),
			new HuntTask());
	}
}
