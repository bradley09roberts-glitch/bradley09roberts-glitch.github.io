package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.EnumSet;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * Newcomers: people living in villages, at survivor camps out in the world and on the road, each with their own
 * name, look and trade (one of the nine kinds of work). Strangers mind their own home until a player earns their
 * trust (they say what they would like first), then join the team and work like any friend.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 *
 * <p>The parts: {@link Personas} (names, trades, stories), {@link Strangers} (making strangers and safe spots),
 * {@link StrangerGoal} (their everyday life), {@link Requests} and {@link Recruiting} (asking them in),
 * {@link VillageSettlers}, {@link SurvivorCamp} and {@link Wanderers} (where they live), {@link SettlerData} (what the
 * world remembers), {@link SettlerEvents} (their ticks, deaths and dismissals), {@link SettlerCommands}
 * ({@code /friends newcomers}) and {@link SettlerLines} (what they say). Strangers never change a block, so this
 * package registers no block-edit rules and no jobs: once recruited, a newcomer works with their trade's jobs.
 */
public final class Settlers {
	private Settlers() {
	}

	public static void init() {
		SurvivorCamp.register();
		SettlerLines.register();
		SettlerCommands.register();
		CompanionEvents.GOALS.add((companion, goals, targets) -> goals.addGoal(5, new StrangerGoal(companion)));
		CompanionEvents.TICK.add(SettlerEvents::tick);
		CompanionEvents.INTERACT.add(Recruiting::interact);
		CompanionEvents.DEATH.add(SettlerEvents::died);
		CompanionEvents.DISMISSED.add(SettlerEvents::dismissed);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			VillageSettlers.tick(server);
			Wanderers.tick(server);
		});
	}

	/**
	 * The trades of the newcomers on the team who are alive, loaded or not, so the camp counts a newcomer builder as a
	 * builder when deciding what it still needs to grow (see {@code CampProgress}).
	 */
	public static Set<Role> teamRoles(MinecraftServer server) {
		Set<Role> roles = EnumSet.noneOf(Role.class);
		for (SettlerData.Newcomer n : SettlerData.get(server).newcomers()) {
			if (n.state == SettlerData.State.ALIVE) {
				roles.add(n.archetype.role());
			}
		}
		return roles;
	}
}
