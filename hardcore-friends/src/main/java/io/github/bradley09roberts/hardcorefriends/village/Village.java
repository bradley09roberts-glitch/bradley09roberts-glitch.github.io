package io.github.bradley09roberts.hardcorefriends.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.SleepTask;
import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * A proper village: a town plan of streets and plots, a house of their own for every household built with real
 * materials, real beds to sleep in, a daily routine, civic buildings, and stages beyond the Settlement. Provides
 * {@code civic.Homes}.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 *
 * <p>The parts: {@link VillageData} (what the world remembers), {@link TownPlan} (the streets and plots, as geometry),
 * {@link PlotSurvey} and {@link PlotSearch} (finding a plot on real ground), {@link Planner} (what to build next, and
 * keeping it all up to date), {@link Households} and {@link Housing} (who lives where), {@link HomesProvider},
 * {@link VillageBuildTask} (building), {@link StreetsTask} (the streets), {@link GroundsTask} (water, the wheat field
 * and the orchard), {@link EveningTask} and {@link MealTask} (the daily routine), {@link VillageGrowth} (the Town and
 * the City), {@link VillagePlan} (the other packages' way in), {@link VillageCommands} and {@link VillageLines}. The
 * friends' own beds are slept in by the sleep job ({@code ai.task.needs.SleepTask}), which asks {@code civic.Homes}.
 * Every block change goes through the edit guard's existing reasons (BUILD for buildings, LANDSCAPE for streets, FARM
 * for water and fields, GRADE for levelling plots, owned by the survival package), so no new policy is registered.
 */
public final class Village {
	private Village() {
	}

	public static void init() {
		VillageLines.register();
		Homes.provide(new HomesProvider());
		jobs();
		CompanionEvents.TICK.add(Village::tick);
		CompanionEvents.DEATH.add((c, level, source) -> leaves(c, level));
		CompanionEvents.DISMISSED.add(Village::leaves);
		Construction.FINISHED.add((level, siteKey, plan) -> Planner.finished(level, siteKey));
		FriendsCommand.EXTENSIONS.add(VillageCommands::register);
		FriendsCommand.CAMP_STATUS.add(Village::campStatus);
		ServerTickEvents.END_SERVER_TICK.register(Village::serverTick);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	/**
	 * The jobs, for everyone. Building one's own home is personal (whoever's home it is builds it, at their keenness for
	 * building); the rest of the village is the builder's work, the streets and the decoration the landscaper's, the
	 * water, the wheat field and the orchard the farmer's, each with the specialist first. The evening and the meals
	 * are needs jobs (time off). The street, grounds and decoration jobs are one friend at a time; building claims one
	 * builder per building itself, so several houses go up at once.
	 */
	private static void jobs() {
		SpecialityTask.PERSONAL.add(VillageBuildTask.Mode.HOME.id);
		SpecialityTask.EXCLUSIVE.addAll(Set.of(StreetsTask.ID, GroundsTask.ID));
		TaskRegistry.PACKS.add(id -> List.of(
			new SpecialityTask(new VillageBuildTask(VillageBuildTask.Mode.HOME), Role.BUILDER),
			new SpecialityTask(new VillageBuildTask(VillageBuildTask.Mode.VILLAGE), Role.BUILDER),
			new SpecialityTask(new VillageBuildTask(VillageBuildTask.Mode.DECOR), Role.LANDSCAPER),
			new SpecialityTask(new StreetsTask(), Role.LANDSCAPER),
			new SpecialityTask(new GroundsTask(), Role.FARMER),
			new EveningTask(),
			new MealTask()));
	}

	private static void serverTick(MinecraftServer server) {
		try {
			Planner.tick(server);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("The village planner failed this tick", e);
		}
	}

	/**
	 * Every tick of every friend: a friend lying in a bed without the sleep job (the world was saved while they slept,
	 * or something else moved them) gets up, so nobody is left lying in a bed while walking about, and the bed is freed.
	 */
	private static void tick(CompanionEntity c, ServerLevel level) {
		if (c.tickCount % 10 == 0 && c.isSleeping() && !c.isAsleep()) {
			SleepTask.wake(c);
		}
	}

	/** Someone left for good: out of their bed (freeing it) and out of their home. */
	private static void leaves(CompanionEntity c, ServerLevel level) {
		if (c.isSleeping()) {
			c.stopSleeping();
		}
		Homes.get().moveOut(level.getServer(), c.getUUID());
	}

	/** A line for {@code /friends camp} about the village. */
	private static List<Component> campStatus(MinecraftServer server) {
		List<Component> lines = new ArrayList<>();
		CampData camp = Camp.data(server);
		VillageData v = VillageData.get(server);
		if (!FriendsConfig.get().villageHomes || camp.campPos().isEmpty()) {
			return lines;
		}
		if (v.centre().isEmpty()) {
			if (camp.stage() < Planner.VILLAGE_STAGE) {
				lines.add(Component.literal("Village: the friends lay out a town plan once the camp is a "
					+ Camp.stageName(Planner.VILLAGE_STAGE) + ".").withStyle(ChatFormatting.GRAY));
			}
			return lines;
		}
		int homes = 0;
		for (VillageData.Plot p : v.plots()) {
			homes += p.isHouse() && p.standing() ? 1 : 0;
		}
		lines.add(Component.literal("Village: " + homes + " homes standing, " + v.plots().size() + " plots in all (/friends village).")
			.withStyle(ChatFormatting.GRAY));
		return lines;
	}

	private static void clear() {
		Planner.clear();
		VillageBuildTask.clearClaims();
		StreetsTask.clear();
		EveningTask.clear();
		MealTask.clear();
	}
}
