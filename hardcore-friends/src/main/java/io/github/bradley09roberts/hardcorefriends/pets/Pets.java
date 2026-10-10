package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.civic.Families;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.people.People;

/**
 * Pets and maps: friends and children adopt cats and dogs that follow them and sleep at home, and Scout draws
 * maps of the land round the camp and where trips went.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}. No block is changed here, so there are no edit rules: an
 * item frame for a map is an entity, hung only where the edit guard would let a builder place a block.
 *
 * <p>The parts: {@link PetsData} (what the world remembers), {@link PetKind} and {@link PetNames}; {@link AdoptTask}
 * and {@link FeedPetTask} (the friends' jobs), {@link PetBrain} with {@link PetMoveGoal} and {@link PetGuardGoal} (what
 * a pet does), {@link PetEvents} (the hooks: loading, deaths, damage, an owner lost); {@link Maps},
 * {@link MapPainter}, {@link MapFrames}, {@link MakeMapTask}, {@link HangMapTask} and {@link CopyMapTask} (Scout's
 * maps), {@link Workbench} (stock and tables), {@link PetsCommands} and {@link PetsLines}.
 */
public final class Pets {
	/** Entity tag on every pet of the camp, saved with the animal, so it is known again when it loads. */
	public static final String PET_TAG = "hardcorefriends.pet";
	/** Entity tag on the item frames the map maker hung. */
	public static final String FRAME_TAG = "hardcorefriends.map_frame";

	private Pets() {
	}

	public static void init() {
		PetsLines.register();
		Workbench.teachRecipes();

		TaskRegistry.PACKS.add(id -> List.of(new AdoptTask(), new FeedPetTask(), new MakeMapTask(), new HangMapTask(),
			new CopyMapTask()));
		// Children may adopt a pet, feed it and play with it: none of it changes a block or takes them out of the camp.
		People.allowChildJob(AdoptTask.ID);
		People.allowChildJob(FeedPetTask.ID);
		// A player waiting in the camp for a copy of a map can have it after dark too.
		TaskScheduler.NIGHT_JOBS.add(CopyMapTask.ID);
		// The map maker keeps the maps they are drawing or bringing home, a spare empty map and a frame for hanging one.
		KeepList.addCommonRule(new KeepList.Rule("maps", s -> s.is(Items.FILLED_MAP) || s.is(Items.MAP), 6));
		KeepList.addCommonRule(new KeepList.Rule("item frames", s -> s.is(Items.ITEM_FRAME), 1));

		CompanionEvents.TICK.add(PetEvents::companionTick);
		CompanionEvents.DEATH.add((c, level, source) -> PetEvents.ownerLeft(c, level, true));
		CompanionEvents.DISMISSED.add((c, level) -> PetEvents.ownerLeft(c, level, false));
		CompanionEvents.HURT.add(PetEvents::companionHurt);
		CompanionEvents.INTERACT.add(PetEvents::interact);

		ServerEntityEvents.ENTITY_LOAD.register(PetEvents::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register(PetEvents::unloaded);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(PetEvents::allowDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(PetEvents::died);
		ServerTickEvents.END_SERVER_TICK.register(PetEvents::serverTick);

		FriendsCommand.EXTENSIONS.add(PetsCommands::register);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	private static void clear() {
		PetBrain.clear();
		Workbench.clear();
		Maps.clear();
		CopyMapTask.clear();
		AdoptTask.clear();
	}

	// ---------------------------------------------------------------- helpers

	/** True for one of the camp's pets (an animal the friends tamed). */
	static boolean isPet(Entity e) {
		return e instanceof TamableAnimal && e.entityTags().contains(PET_TAG);
	}

	/** A loaded friend, newcomer or child by entity UUID, in any level, or null. */
	static @Nullable CompanionEntity companion(MinecraftServer server, @Nullable UUID id) {
		if (id == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof CompanionEntity c && c.isAlive()) {
				return c;
			}
		}
		return null;
	}

	/** A loaded pet by entity UUID, in any level, or null. */
	static @Nullable TamableAnimal pet(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof TamableAnimal a && a.isAlive() && isPet(a)) {
				return a;
			}
		}
		return null;
	}

	/** "Pip Hart", or just "Pip" before a family name. */
	static String fullName(MinecraftServer server, CompanionEntity c) {
		Optional<String> family = Families.get().familyName(server, c.getUUID());
		return family.filter(f -> !f.isBlank()).map(f -> c.displayName() + " " + f).orElse(c.displayName());
	}

	/**
	 * True if the camp has a home for this person's pet: their own house in the village, or, while the village has no
	 * houses at all, a finished cabin at the camp.
	 */
	static boolean hasHomeFor(MinecraftServer server, UUID person) {
		Homes.Provider homes = Homes.get();
		if (homes.homeOf(server, person).isPresent()) {
			return true;
		}
		return homes.homes(server).isEmpty() && Camp.data(server).isCompleted(Structures.CABIN);
	}

	/** True if the camp already keeps as many pets as the settings allow. */
	static boolean atCap(MinecraftServer server) {
		return PetsData.get(server).pets().size() >= FriendsConfig.get().maxPets;
	}

	/**
	 * True if this person may have a pet now: pets are on, they are on the team (working, not off following a player),
	 * they have none, the camp has room for one more and a home for it.
	 */
	static boolean mayHavePet(MinecraftServer server, CompanionEntity who) {
		return FriendsConfig.get().pets && who.isAlive() && who.isTeamMember() && who.mode() == CompanionMode.WORK
			&& PetsData.get(server).petOf(who.getUUID()).isEmpty() && !atCap(server) && hasHomeFor(server, who.getUUID());
	}
}
