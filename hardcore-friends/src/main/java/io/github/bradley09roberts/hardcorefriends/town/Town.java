package io.github.bradley09roberts.hardcorefriends.town;

import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.FeedPlayerTask;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.survival.ChunkLoader;

/**
 * Several players: the camp's owner and the players they trust, each friend's bond with each player, a job board
 * of what the camp needs, mourning a fallen player and keeping their things safe, notes left for the camp, deliveries
 * to players' mailboxes, fair limits on a server, and opt-in siege nights.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}. The town package changes no blocks, so it registers no block-edit rules.
 *
 * <p>The parts: {@link TownPermissions} (owner, trust, who may do what), {@link Bonds} (each friend's bond with each
 * player), {@link JobBoard} (the camp's requests and deliveries; other packages add requests through
 * {@link JobBoard#SUPPLIERS}), {@link Mourning} and {@link KeepItemsSafeTask} (a player's death), {@link Notes},
 * {@link Mail} and {@link MailTripTask} (mailboxes and deliveries), {@link Sieges}, {@link TownData} (what the world
 * remembers), {@link TownCommands} and {@link TownLines}.
 */
public final class Town {
	private Town() {
	}

	public static void init() {
		TownLines.register();
		FriendsCommand.EXTENSIONS.add(TownCommands::register);
		FriendsCommand.CAMP_STATUS.add(TownCommands::memorial);

		// Permission checks come before every other right-click handler (the settler package's asking-in included);
		// the hello by bond comes after the others, just before the friend's own handling.
		CompanionEvents.INTERACT.add(0, TownPermissions::interact);
		CompanionEvents.INTERACT.add(Bonds::greet);
		CompanionEvents.GIFT.add(Bonds::gift);
		CompanionEvents.HURT.add(Bonds::hurt);
		CompanionEvents.DEATH.add(Bonds::died);
		CompanionEvents.DEATH.add(Sieges::friendDied);
		CompanionEvents.DISMISSED.add(Bonds::dismissed);
		CompanionEvents.TICK.add(Bonds::tick);
		FeedPlayerTask.priority = Bonds::feedPriority;

		// Jobs for everyone: keeping a fallen player's things safe, and mailbox deliveries (one friend at a time).
		TaskRegistry.PACKS.add(id -> List.of(new KeepItemsSafeTask(), new MailTripTask()));

		// The camp only keeps running while its owner or a trusted player is online (when trust is required).
		ChunkLoader.restrictCampLoad(TownPermissions::keepsCampRunning);

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Mourning.playerDied(player, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> Bonds.afterDamage(entity, source, taken));
		ServerPlayerEvents.JOIN.register(player -> TownData.get(player.level().getServer()).remember(player));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Mourning.tick(server);
			Notes.tick(server);
			Sieges.tick(server);
			if (server.getTickCount() % 1200 == 600) {
				Bonds.timeTogether(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	/** Forgets what is kept in memory only: daily caps, death spots, note waits, the planned delivery. */
	private static void clear() {
		Bonds.clear();
		Mourning.clear();
		Notes.clear();
		Mail.clear();
	}

	/**
	 * Forgets everything the town remembers: the owner and trust, bonds, notes, mailboxes... Only for automated tests,
	 * which each use a new mock player and must not be refused as untrusted strangers.
	 */
	public static void resetForTests(MinecraftServer server) {
		TownData.get(server).resetForTests();
		clear();
	}
}
