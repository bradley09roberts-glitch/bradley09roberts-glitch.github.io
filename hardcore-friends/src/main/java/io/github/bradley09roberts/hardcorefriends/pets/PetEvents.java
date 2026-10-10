package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.civic.Families;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The hooks that keep pets and maps in step with the world. Pets are found again when they load (by their tag) and
 * given their goals; loaded pets are thought about once a second; a pet that dies is mourned and its record goes; a pet
 * whose owner dies or leaves goes to their family (a child first), else stays on as the camp's. A pet can never hurt a
 * player, a friend, a villager, a golem or anyone's animal: any damage it would deal to something that is not a monster
 * is refused here, and a pet's own owner is never hurt by it either. A friend near their pet is a little happier and now
 * and then makes a fuss of it. Right-clicking the map maker with an empty map gets a copy of a finished map.
 */
final class PetEvents {
	/** Checks in a row (every {@value #MISSING_CHECK} ticks) a pet's last spot was loaded but it was not, before it is lost. */
	private static final int MISSING_LIMIT = 30;
	private static final int MISSING_CHECK = 200;
	private static final int PLACED_MAPS_CHECK = 600;
	private static final double FUSS_RANGE = 5;

	private static final Set<TamableAnimal> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	private PetEvents() {
	}

	// ------------------------------------------------------------------ pets

	/** A pet of the camp loaded: give it its goals, and bring its record (and owner) up to date. */
	static void loaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof TamableAnimal a) || !Pets.isPet(a)) {
			return;
		}
		LOADED.add(a);
		PetsData data = PetsData.get(level.getServer());
		Optional<PetsData.Pet> record = data.pet(a.getUUID());
		if (record.isEmpty()) {
			PetKind kind = PetKind.of(a);
			if (kind == null) {
				return;
			}
			// Known by its tag but not on the list (the list was lost): back to its owner if they are here and have no
			// other pet, else a pet of the camp.
			PetsData.Pet p = data.newPet(a.getUUID(), kind, a.hasCustomName() && a.getCustomName() != null ? a.getCustomName().getString() : "Pet");
			p.adoptedDay = Camp.day(level);
			EntityReference<LivingEntity> was = a.getOwnerReference();
			CompanionEntity owner = was == null ? null : Pets.companion(level.getServer(), was.getUUID());
			if (owner != null && owner.isTeamMember() && data.petOf(owner.getUUID()).isEmpty()) {
				p.owner = owner.getUUID();
				p.ownerName = owner.displayName();
			}
			record = Optional.of(p);
		}
		PetsData.Pet p = record.get();
		// An owner changed while the pet was away (its owner died or left, and family took it in): catch it up.
		EntityReference<LivingEntity> ref = a.getOwnerReference();
		if (p.owner != null && (ref == null || !p.owner.equals(ref.getUUID()))) {
			a.setOwnerReference(EntityReference.of(p.owner));
		}
		p.dimension = Camp.dimensionId(level);
		p.lastPos = a.blockPosition();
		p.lastSeen = level.getGameTime();
		p.missing = 0;
		data.setDirty();
		PetBrain.install(a);
	}

	/** A pet unloaded with its chunk (or left for another dimension): remember exactly where. */
	static void unloaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof TamableAnimal a) || !Pets.isPet(a)) {
			return;
		}
		LOADED.remove(a);
		PetsData data = PetsData.get(level.getServer());
		data.pet(a.getUUID()).ifPresent(p -> {
			p.dimension = Camp.dimensionId(level);
			p.lastPos = a.blockPosition();
			p.lastSeen = level.getGameTime();
			data.setDirty();
		});
	}

	/** A pet died: its record goes, its owner grieves, everyone is told how. */
	static void died(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof TamableAnimal a) || !Pets.isPet(a) || !(a.level() instanceof ServerLevel level)) {
			return;
		}
		LOADED.remove(a);
		MinecraftServer server = level.getServer();
		PetsData data = PetsData.get(server);
		Optional<PetsData.Pet> record = data.pet(a.getUUID());
		if (record.isEmpty()) {
			return;
		}
		lost(server, record.get(), a.getCombatTracker().getDeathMessage().getString() + ".");
	}

	/** A pet gone for good (died, or missing too long): its owner says so and everyone hears. */
	private static void lost(MinecraftServer server, PetsData.Pet p, String how) {
		PetsData.get(server).removePet(p.id);
		CompanionEntity owner = Pets.companion(server, p.owner);
		if (owner != null) {
			Speech.say(owner, Line.PET_LOST, p.name, p.kind.word());
		}
		String whose = p.owner == null ? "the camp's " + p.kind.word() : ownerName(server, p) + "'s " + p.kind.word();
		Speech.announce(server, Component.literal(how + " (" + p.name + " was " + whose + ".)").withStyle(ChatFormatting.GRAY));
		Camp.data(server).addStat("pets_lost", 1);
	}

	private static String ownerName(MinecraftServer server, PetsData.Pet p) {
		CompanionEntity owner = Pets.companion(server, p.owner);
		return owner != null ? Pets.fullName(server, owner) : p.ownerName.isEmpty() ? "someone" : p.ownerName;
	}

	/**
	 * A friend died or was dismissed: their pet goes to family who have none, a young child of theirs first, then their
	 * husband or wife, their grown children, their parents; else to any child of the camp without one; else it stays on
	 * as the camp's own pet. Their maps went with their backpack.
	 */
	static void ownerLeft(CompanionEntity c, ServerLevel level, boolean died) {
		Maps.holderLeft(c, level);
		MinecraftServer server = level.getServer();
		PetsData data = PetsData.get(server);
		Optional<PetsData.Pet> record = data.petOf(c.getUUID());
		if (record.isEmpty()) {
			return;
		}
		PetsData.Pet p = record.get();
		UUID heir = heir(server, c, data);
		p.owner = heir;
		CompanionEntity next = Pets.companion(server, heir);
		p.ownerName = next != null ? next.displayName() : "";
		data.setDirty();
		TamableAnimal a = Pets.pet(server, p.id);
		if (a != null && heir != null) {
			a.setOwnerReference(EntityReference.of(heir));
		}
		// A pet of the whole camp keeps its late owner's mark, so nobody takes it for a stray.
		String whose = Pets.fullName(server, c) + "'s " + p.kind.word() + " " + p.name;
		String text = next != null ? whose + " now lives with " + Pets.fullName(server, next) + "."
			: heir != null ? whose + " now lives with the family." : whose + " stays on at the camp, everyone's pet now.";
		Speech.announce(server, Component.literal(text).withStyle(ChatFormatting.GRAY));
	}

	/** Who takes a pet in when its owner is gone (see {@link #ownerLeft}), or null for nobody. */
	private static @Nullable UUID heir(MinecraftServer server, CompanionEntity c, PetsData data) {
		UUID self = c.getUUID();
		Families.Provider families = Families.get();
		Set<UUID> household = families.householdOf(server, self);
		List<UUID> order = new ArrayList<>();
		List<UUID> children = families.childrenOf(server, self);
		for (UUID kid : children) {
			if (household.contains(kid)) {
				order.add(kid); // young ones at home first
			}
		}
		families.partnerOf(server, self).ifPresent(order::add);
		order.addAll(children);
		List<UUID> parents = families.parentsOf(server, self);
		order.addAll(parents);
		for (CompanionEntity other : Companions.all()) {
			if (other.isChild()) {
				order.add(other.getUUID());
			}
		}
		for (UUID id : new LinkedHashSet<>(order)) {
			if (id.equals(self) || data.petOf(id).isPresent()) {
				continue;
			}
			CompanionEntity who = Pets.companion(server, id);
			if (who == null) {
				// Away just now: a partner or child is taken on trust (the family lists name only the living), but a
				// parent is listed whether living or not, so only one who is here takes the pet in.
				if (!parents.contains(id)) {
					return id;
				}
				continue;
			}
			if (who.isTeamMember() && who.mode() != CompanionMode.STRANGER) {
				return id;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- damage

	/**
	 * The last word on pets and harm: a pet of the camp only ever hurts monsters. Anything else it would hurt (a
	 * player, a friend, a villager, a golem, any animal, another pet) is spared, whatever set it off.
	 */
	static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker == null || !Pets.isPet(attacker)) {
			return true;
		}
		return victim instanceof Enemy && !(victim instanceof Player) && !(victim instanceof CompanionEntity);
	}

	/** A friend is never hurt by one of the camp's pets (so nobody ever takes against a pet). */
	static float companionHurt(CompanionEntity c, ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		return attacker != null && Pets.isPet(attacker) ? 0 : amount;
	}

	// ----------------------------------------------------------------- ticks

	/** Every server tick: each loaded pet thinks once a second; lost pets and maps are checked now and then. */
	static void serverTick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!LOADED.isEmpty()) {
			PetsData data = PetsData.get(server);
			for (TamableAnimal a : new ArrayList<>(LOADED)) {
				if (a.isRemoved() || !a.isAlive()) {
					LOADED.remove(a);
					continue;
				}
				if ((a.tickCount + a.getId()) % 20 != 0 || !(a.level() instanceof ServerLevel level)) {
					continue;
				}
				Optional<PetsData.Pet> record = data.pet(a.getUUID());
				if (record.isEmpty()) {
					continue;
				}
				PetsData.Pet p = record.get();
				p.dimension = Camp.dimensionId(level);
				p.lastPos = a.blockPosition();
				p.lastSeen = level.getGameTime();
				p.missing = 0;
				data.setDirty(); // where it was last seen is saved with the world
				PetBrain.think(level, a, p);
			}
		}
		if (now % MISSING_CHECK == 0) {
			checkMissing(server);
		}
		if (now % PLACED_MAPS_CHECK == 7) {
			Maps.checkPlaced(server);
		}
		if (now % 20 == 3) {
			CopyMapTask.expire(server, now);
		}
	}

	/**
	 * A pet whose last spot is loaded but which is nowhere in the loaded world (gone some way the game did not tell us
	 * of) is counted missing; after {@value #MISSING_LIMIT} checks in a row it is given up as lost.
	 */
	private static void checkMissing(MinecraftServer server) {
		PetsData data = PetsData.get(server);
		for (PetsData.Pet p : new ArrayList<>(data.pets())) {
			ServerLevel level = levelOf(server, p.dimension);
			if (level == null || !level.isLoaded(p.lastPos) || Pets.pet(server, p.id) != null) {
				p.missing = 0;
				continue;
			}
			if (++p.missing >= MISSING_LIMIT) {
				lost(server, p, p.name + " the " + p.kind.word() + " has not been seen for a long while and is given up as lost.");
			}
		}
	}

	private static @Nullable ServerLevel levelOf(MinecraftServer server, String dimension) {
		for (ServerLevel level : server.getAllLevels()) {
			if (Camp.dimensionId(level).equals(dimension)) {
				return level;
			}
		}
		return null;
	}

	/** Every tick of every friend: maps are drawn, and now and then an owner near their pet cheers up and fusses it. */
	static void companionTick(CompanionEntity c, ServerLevel level) {
		Maps.tick(c, level);
		if ((c.tickCount + c.getId()) % 100 != 0 || !c.isTeamMember() || c.isAsleep()) {
			return;
		}
		Optional<PetsData.Pet> record = PetsData.get(level.getServer()).petOf(c.getUUID());
		if (record.isEmpty() || !(level.getEntity(record.get().id) instanceof TamableAnimal a) || !a.isAlive()
			|| a.distanceToSqr(c) > FUSS_RANGE * FUSS_RANGE) {
			return;
		}
		// Company of their own pet: a little fun and comfort.
		c.needs().add(Needs.Need.FUN, 1.0);
		c.needs().add(Needs.Need.COMFORT, 0.5);
		if (c.getTarget() == null && c.mode() == CompanionMode.WORK && c.getRandom().nextInt(4) == 0) {
			if (Speech.say(c, Line.PET_PLAY, record.get().name, record.get().kind.word())) {
				c.getLookControl().setLookAt(a);
			}
		}
	}

	// ------------------------------------------------------------------ maps

	/**
	 * A player right-clicks the map maker holding an empty map: they get a copy of a finished map (the one showing where
	 * they stand, else the camp's, else the newest), drawn on their own empty map, which is used up.
	 */
	static InteractionResult interact(CompanionEntity c, ServerPlayer player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.MAP) || !c.isTeamMember() || !Maps.isExplorer(c) || !(c.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		Optional<PetsData.MapRecord> map = Maps.bestFor(level, player.blockPosition());
		if (map.isEmpty()) {
			Speech.tell(player, c, "I haven't finished a map yet. Give me a little time to draw one!");
			return InteractionResult.SUCCESS_SERVER;
		}
		held.consume(1, player);
		CopyMapTask.hand(c, player, Maps.copyOf(level, map.get()));
		return InteractionResult.SUCCESS_SERVER;
	}
}
