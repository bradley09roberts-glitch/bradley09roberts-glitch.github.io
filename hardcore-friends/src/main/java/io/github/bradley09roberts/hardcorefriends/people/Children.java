package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.settler.SettlerData;
import io.github.bradley09roberts.hardcorefriends.survival.Skills;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Childhood. A child ({@link CompanionEntity#isChild()}) never works, fights or changes a block: a job filter keeps them
 * to their own needs, their games and lessons, coming home and the odd wedding ({@link #mayDo}); the friend entity
 * itself refuses them a target and the edit guard refuses their block changes. They do not follow players off on
 * adventures, are not handed tools, and run to a parent when a monster comes ({@link ChildRefugeGoal}); parents and
 * Aegis go for anything after them first ({@link ProtectChildGoal}).
 *
 * <p>After {@code childhoodDays} in-game days a child grows up ({@link #growUp}): full size, a short celebration, and
 * from then on a newcomer of the team who works at their trade with whatever they learnt as a child. People born in
 * the camp never count against the newcomer limit ({@code maxSettlers}); the population cap ({@code maxPopulation})
 * is what limits births.
 */
final class Children {
	/** Jobs a child may do besides their own needs ({@code needs.*}). Other packages may add to it. */
	static final Set<String> JOBS = ConcurrentHashMap.newKeySet();
	/** Unity for a child growing up and a baby arriving (category "family"). */
	static final String UNITY_FAMILY = "family";

	static {
		JOBS.addAll(Set.of(PlayTask.ID, LearnTask.ID, ChildHomeTask.ID, StayCloseTask.ID, WeddingTask.ID,
			"common.idle", "common.return_home", "common.leave_pen"));
	}

	/** A child in danger and the monster after them. */
	record Danger(CompanionEntity child, Mob threat) {
	}

	private record DangerList(long at, List<Danger> list) {
	}

	private static final Map<ResourceKey<Level>, DangerList> DANGER = new HashMap<>();

	private Children() {
	}

	static void clear() {
		DANGER.clear();
	}

	/** The job filter: a child only takes on their needs and {@link #JOBS}; grown-ups are not affected. */
	static boolean mayDo(CompanionEntity c, String jobId) {
		return !c.isChild() || jobId.startsWith("needs.") || JOBS.contains(jobId);
	}

	/** Once a second for each child: stay home rather than follow anyone off, and grow up when the time comes. */
	static void second(CompanionEntity c, ServerLevel level, PeopleData data, PeopleData.Person person) {
		MinecraftServer server = level.getServer();
		if (c.mode() == CompanionMode.FOLLOW) {
			ServerPlayer leader = c.leader();
			c.setMode(CompanionMode.WORK, null);
			if (leader != null) {
				leader.sendSystemMessage(Component.literal(c.displayName() + " is too young to go off adventuring and stays at home.")
					.withStyle(ChatFormatting.GRAY));
			}
		}
		long day = PeopleEvents.day(server);
		if (person.bornDay < 0) {
			person.bornDay = day; // a child whose records were lost: their childhood starts now
			data.setDirty();
		}
		if (day - person.bornDay >= FriendsConfig.get().childhoodDays && !c.isAsleep() && roomToGrow(c, level)) {
			growUp(c, level, data, person);
		}
	}

	/**
	 * Room for a grown-up's body where the child stands: a child fits through gaps and under ledges a grown-up does not,
	 * and growing up inside a block would suffocate them. They wait until they step somewhere roomier.
	 */
	private static boolean roomToGrow(CompanionEntity c, ServerLevel level) {
		AABB grown = c.getType().getDimensions().makeBoundingBox(c.position()).deflate(1.0E-4);
		return level.noCollision(c, grown) && !level.containsAnyLiquid(grown);
	}

	/**
	 * A child grows up: full size, a grown-up's skin if theirs was a child's, on the team's records as a newcomer born
	 * here (never counted against the newcomer limit), and a short celebration. They start work at their trade the
	 * next time they choose a job, with the skills they learnt as a child.
	 */
	static void growUp(CompanionEntity c, ServerLevel level, PeopleData data, PeopleData.Person person) {
		MinecraftServer server = level.getServer();
		c.setChild(false);
		person.child = false;
		person.grownDay = PeopleEvents.day(server);
		data.setDirty();
		Persona persona = c.persona();
		if (persona != null && !Skins.suits(persona.skin(), false)) {
			c.setPersona(new Persona(persona.name(), persona.colour(), Skins.randomFor(c.getRandom(), false), persona.archetype()));
		}
		SettlerData settlers = SettlerData.get(server);
		SettlerData.Newcomer record = settlers.recordJoined(c.getUUID(), c.displayName(), c.friendId(), c.nameColour(), null, "",
			level.getGameTime());
		record.born = true;
		settlers.setDirty();
		c.scheduler().interrupt();
		String trade = c.friendId().role().title().toLowerCase(Locale.ROOT);
		Speech.say(c, Line.GROWN_UP, trade);
		Relationships.announceGold(server, person.fullName() + " has grown up and starts work as a " + trade + best(c) + ".");
		Unity.add(level, UNITY_FAMILY, 15, 45);
	}

	/** ", and is already good at farming" for the skill a child practised most, or "". */
	private static String best(CompanionEntity c) {
		String best = null;
		int bestLevel = 0;
		for (String kind : Skills.kinds()) {
			int lv = Skills.level(c, kind);
			if (lv > bestLevel) {
				bestLevel = lv;
				best = kind;
			}
		}
		return best == null ? "" : ", and is already good at " + best;
	}

	/**
	 * {@code CompanionEvents.INTERACT}: a child takes food (to eat, or keep for later) but not tools, weapons or
	 * building stuff: those are for grown-ups, and a child carrying a sword would end up on the night watch.
	 */
	static InteractionResult interact(CompanionEntity c, ServerPlayer player, InteractionHand hand) {
		if (!c.isChild() || !c.isTeamMember()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty() || held.get(DataComponents.FOOD) != null && CompanionEntity.isEdible(held)) {
			return InteractionResult.PASS;
		}
		player.sendSystemMessage(Component.literal(c.displayName() + " is too young to look after that. Children only take food.")
			.withStyle(ChatFormatting.GRAY));
		return InteractionResult.SUCCESS_SERVER;
	}

	// ---------------------------------------------------------------- danger

	/**
	 * Children in this world with a monster after them or right beside them, worked out at most every 10 ticks for the
	 * whole world (one box search per child), however many grown-ups ask.
	 */
	static List<Danger> inDanger(ServerLevel level) {
		long now = level.getGameTime();
		DangerList cached = DANGER.get(level.dimension());
		if (cached == null || now - cached.at() >= 10 || now < cached.at()) {
			List<Danger> list = new ArrayList<>();
			for (CompanionEntity child : Companions.in(level)) {
				if (!child.isChild()) {
					continue;
				}
				for (Mob mob : level.getEntitiesOfClass(Mob.class, child.getBoundingBox().inflate(16, 8, 16),
					m -> m.isAlive() && Threats.isThreat(m) && (m.getTarget() == child || m.distanceToSqr(child) <= 4 * 4))) {
					list.add(new Danger(child, mob));
				}
			}
			cached = new DangerList(now, List.copyOf(list));
			DANGER.put(level.dimension(), cached);
		}
		return cached.list();
	}

	/** True if {@code adult} is one of this child's parents. */
	static boolean isParent(MinecraftServer server, CompanionEntity adult, CompanionEntity child) {
		Optional<PeopleData.Person> p = PeopleData.get(server).person(child.getUUID());
		return p.isPresent() && p.get().parents.contains(adult.getUUID());
	}
}
