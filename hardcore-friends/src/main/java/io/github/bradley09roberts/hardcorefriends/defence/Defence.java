package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.people.People;

/**
 * Defending the village: the alarm bell, children and non-fighters taking cover at home, guards at the walls,
 * gate and watchtower, standing up to raids, and a fire watch.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 *
 * <p>The parts: {@link Alarm} (what rings the alarm, the all-clear, raids won), {@link Ringing} and
 * {@link RingBellTask} (ringing the bell), {@link Bells} and {@link PutUpBellTask} (the village's bells), {@link Duty}
 * (who fights and who takes cover, and the job filter), {@link Shelters} and {@link TakeCoverTask}, {@link Posts},
 * {@link ToPostsTask}, {@link GuardRota}, {@link GuardDutyTask} and {@link GuardRestTask} (the guards),
 * {@link DefenceTargetGoal} (who the defenders go for first), {@link FireWatch} and {@link PutOutFireTask},
 * {@link DefenceCommands} and {@link DefenceLines}. The only blocks changed are fire put out (the edit guard's
 * landscaping rules: fire is a replaceable block in the camp), the friends' own doors shut (their own contraptions'
 * rule), a torch at a dark post and the friends' own bell stood at the square (the building rules), so no new edit
 * reason or policy is registered. Nothing here touches a player's build, hurts anyone but hostiles, or changes deaths,
 * respawns, game rules or difficulty.
 */
public final class Defence {
	/** Every job of this package has an id starting with this. */
	public static final String JOB_PREFIX = "defence.";

	private Defence() {
	}

	public static void init() {
		DefenceLines.register();

		// The alarm's jobs for everyone (each scores nothing for the people it is not for), the guards' night and their
		// nap, the fire watch, and putting up a bell (the builder first, anyone in their place).
		SpecialityTask.EXCLUSIVE.add(PutUpBellTask.ID);
		TaskRegistry.PACKS.add(id -> List.of(new RingBellTask(), new TakeCoverTask(), new ToPostsTask(), new GuardDutyTask(),
			new GuardRestTask(), new PutOutFireTask(), new SpecialityTask(new PutUpBellTask(), Role.BUILDER)));
		TaskScheduler.NIGHT_JOBS.addAll(Set.of(RingBellTask.ID, TakeCoverTask.ID, ToPostsTask.ID, GuardDutyTask.ID, PutOutFireTask.ID));
		TaskScheduler.FIT_WHEN_WEAK.add(TakeCoverTask.ID); // a friend too weak to work still gets indoors
		TaskScheduler.JOB_FILTERS.add(Duty::mayDo);
		People.allowChildJob(TakeCoverTask.ID); // children take cover too (they do no other job of this package)

		CompanionEvents.GOALS.add((companion, goals, targets) -> targets.addGoal(2, new DefenceTargetGoal(companion)));
		CompanionEvents.DEATH.add((companion, level, source) -> FireWatch.forget(companion.getUUID()));
		CompanionEvents.DISMISSED.add((companion, level) -> FireWatch.forget(companion.getUUID()));
		Archery.HOLDS_POST.add(GuardRota::holdsPost);
		// Nobody stands both halves of the night: the first shift's guards are not picked for the second watch.
		NightWatch.EXCUSED.add((c, watch) -> watch == NightWatch.Watch.SECOND && c.level() instanceof ServerLevel level
			&& GuardRota.stoodFirstShift(c, level));

		FriendsCommand.EXTENSIONS.add(DefenceCommands::register);
		FriendsCommand.CAMP_STATUS.add(DefenceCommands::campStatus);
		UseBlockCallback.EVENT.register(Alarm::bellUsed);
		ServerTickEvents.END_SERVER_TICK.register(Defence::serverTick);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	/** Every server tick: the alarm and its jobs twice a second, the guard rota and the fire watch once a second. */
	private static void serverTick(MinecraftServer server) {
		try {
			Alarm.tick(server);
			int tick = server.getTickCount();
			if (tick % Alarm.INTERVAL != 4) {
				return;
			}
			CampData data = Camp.data(server);
			ServerLevel level = Area.campLevel(server, data);
			if (level == null) {
				GuardRota.clear();
				Duty.clear();
				return;
			}
			if (tick % 20 == 4) {
				GuardRota.tick(level, data);
			} else {
				FireWatch.tick(level, data);
			}
			Duty.tick(level, data);
		} catch (RuntimeException e) {
			HardcoreFriends.LOGGER.error("The village's defence failed this tick", e);
		}
	}

	private static void clear() {
		Alarm.clear();
		Bells.clear();
		Duty.clear();
		FireWatch.clear();
		GuardRota.clear();
		Posts.clear();
		Shelters.clear();
	}
}
