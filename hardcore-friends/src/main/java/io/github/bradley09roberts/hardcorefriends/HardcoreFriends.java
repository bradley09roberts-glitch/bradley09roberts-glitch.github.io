package io.github.bradley09roberts.hardcorefriends;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import io.github.bradley09roberts.hardcorefriends.architecture.Architecture;
import io.github.bradley09roberts.hardcorefriends.combat.Combat;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.defence.Defence;
import io.github.bradley09roberts.hardcorefriends.event.ModEvents;
import io.github.bradley09roberts.hardcorefriends.expedition.Expeditions;
import io.github.bradley09roberts.hardcorefriends.life.VillageLife;
import io.github.bradley09roberts.hardcorefriends.market.Market;
import io.github.bradley09roberts.hardcorefriends.navigation.Navigation;
import io.github.bradley09roberts.hardcorefriends.people.People;
import io.github.bradley09roberts.hardcorefriends.pets.Pets;
import io.github.bradley09roberts.hardcorefriends.progress.Progression;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;
import io.github.bradley09roberts.hardcorefriends.registry.ModItems;
import io.github.bradley09roberts.hardcorefriends.settler.Settlers;
import io.github.bradley09roberts.hardcorefriends.survival.Survival;
import io.github.bradley09roberts.hardcorefriends.town.Town;
import io.github.bradley09roberts.hardcorefriends.village.Village;

/**
 * Hardcore Friends: nine human companions with their own skills and personalities, a shared camp that grows into a
 * settlement, and a Unity bond. Player Hardcore rules are never changed.
 */
public class HardcoreFriends implements ModInitializer {
	public static final String MOD_ID = "hardcorefriends";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		FriendsConfig.load();
		ModItems.init();
		ModEntities.init();
		ModEvents.register();
		Combat.init();
		Survival.init();
		Settlers.init();
		Progression.init();
		Expeditions.init();
		Town.init();
		Architecture.init();
		Navigation.init();
		People.init();
		Village.init();
		Market.init();
		VillageLife.init();
		Defence.init();
		Pets.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> FriendsCommand.register(dispatcher));
		LOGGER.info("Hardcore Friends ready: nine friends, one life each.");
	}
}
