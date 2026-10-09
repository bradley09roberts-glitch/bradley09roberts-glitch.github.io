package io.github.bradley09roberts.hardcorefriends.people;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The people package's hooks into the friends' lives and the server's ticks: each friend's record kept up to date (and
 * a child's growing up checked) once a second, the once-a-minute round of friendships, babies and weddings, the
 * wedding ceremony's second-by-second steps, short delays between one line and its answer, and what a death or a
 * departure means for a family. Everything runs on the server thread; memory-only state is forgotten when a server
 * stops.
 */
final class PeopleEvents {
	/** A ticket for something to happen a little later (a reply, a child's first words). */
	private record Later(long at, Runnable run) {
	}

	/** Parents mourn a child for this long (overworld clock ticks): no new baby meanwhile. */
	static final long MOURNING = 3 * 24000L;
	private static final List<Later> LATER = new ArrayList<>();

	private PeopleEvents() {
	}

	/** The overworld clock, which runs on through nights the players sleep through. */
	static long clock(MinecraftServer server) {
		return server.overworld().getOverworldClockTime();
	}

	/** The in-game day number by the overworld clock. */
	static long day(MinecraftServer server) {
		return clock(server) / 24000L;
	}

	/** Runs {@code run} on the server thread about {@code ticks} ticks from now (the run checks its own facts again). */
	static void later(MinecraftServer server, int ticks, Runnable run) {
		if (LATER.size() < 256) {
			LATER.add(new Later(server.getTickCount() + Math.max(1, ticks), run));
		}
	}

	/** The loaded, living team member with this UUID, if any: one look-up in each world's entity index. */
	static @Nullable CompanionEntity loaded(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof CompanionEntity c && c.isAlive() && !c.isRemoved() && c.isTeamMember()) {
				return c;
			}
		}
		return null;
	}

	static void clear() {
		LATER.clear();
		Relationships.clear();
		Weddings.clear();
		Children.clear();
		DateTask.clear();
		PlayTask.clear();
		LearnTask.clear();
	}

	// ----------------------------------------------------------- server tick

	/** {@code ServerTickEvents.END_SERVER_TICK}. */
	static void serverTick(MinecraftServer server) {
		runLater(server);
		int t = server.getTickCount();
		if (t % 20 == 3) {
			Weddings.second(server);
		}
		if (t % 1200 == 611) {
			Relationships.minute(server);
			Births.minute(server);
		}
	}

	private static void runLater(MinecraftServer server) {
		if (LATER.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		List<Runnable> due = new ArrayList<>();
		for (Iterator<Later> it = LATER.iterator(); it.hasNext(); ) {
			Later later = it.next();
			if (now >= later.at()) {
				due.add(later.run());
				it.remove();
			}
		}
		for (Runnable run : due) {
			try {
				run.run();
			} catch (RuntimeException e) {
				HardcoreFriends.LOGGER.error("A delayed people event failed", e);
			}
		}
	}

	// ------------------------------------------------------------- friends

	/** {@code CompanionEvents.TICK}: once a second per team member, spread by entity id. */
	static void tick(CompanionEntity c, ServerLevel level) {
		if (!c.isTeamMember() || (c.tickCount + c.getId()) % 20 != 0 || !c.isAlive()) {
			return;
		}
		PeopleData data = PeopleData.get(level.getServer());
		PeopleData.Person person = data.personFor(c, c.getRandom());
		if (c.isChild()) {
			Children.second(c, level, data, person);
		}
	}

	/**
	 * {@code CompanionEvents.DEATH}: in Hardcore the fallen are gone for good. A partner mourns (a widowed husband or wife
	 * keeps the household), parents mourn a child, a wedding or a baby on the way is no more, and children who lived with
	 * them go on living with their other parent.
	 */
	static void died(CompanionEntity c, ServerLevel level, DamageSource source) {
		if (!c.isTeamMember()) {
			return;
		}
		MinecraftServer server = level.getServer();
		PeopleData data = PeopleData.get(server);
		PeopleData.Person p = data.personFor(c, c.getRandom());
		long clock = clock(server);
		p.state = PeopleData.State.DEAD;
		p.endedAt = clock;
		leaveFamily(server, data, p, true);
		for (UUID parentId : p.parents) {
			CompanionEntity parent = loaded(server, parentId);
			data.person(parentId).ifPresent(parentRecord -> parentRecord.mournUntil = clock + MOURNING);
			if (parent != null && parent != c) {
				Speech.say(parent, Line.MOURN_CHILD, p.name);
				parent.needs().add(Needs.Need.SOCIAL, -30);
				parent.needs().add(Needs.Need.FUN, -30);
			}
		}
		if (p.child) {
			Relationships.announce(server, "The whole camp mourns little " + p.name + ".");
		}
		data.pruneBondsOf(p.id);
		data.setDirty();
	}

	/** {@code CompanionEvents.DISMISSED}: they leave the team, their romance ends and their bed is free. */
	static void dismissed(CompanionEntity c, ServerLevel level) {
		if (!c.isTeamMember()) {
			return;
		}
		MinecraftServer server = level.getServer();
		PeopleData data = PeopleData.get(server);
		PeopleData.Person p = data.personFor(c, c.getRandom());
		p.state = PeopleData.State.GONE;
		p.endedAt = clock(server);
		leaveFamily(server, data, p, false);
		data.pruneBondsOf(p.id);
		data.setDirty();
	}

	/**
	 * What someone leaving for good means for those close to them: their partner mourns or is left behind, plans for a
	 * wedding or a baby end, and children who lived with them live with their other parent.
	 */
	private static void leaveFamily(MinecraftServer server, PeopleData data, PeopleData.Person p, boolean died) {
		PeopleData.Bond bond = data.partnerBond(p.id);
		if (bond != null) {
			UUID otherId = bond.other(p.id);
			bond.weddingDay = -1;
			bond.babyDue = -1;
			if (!died) {
				bond.status = PeopleData.Status.PARTED;
			}
			data.person(otherId).ifPresent(other -> other.partner = null); // free to love again, in time
			CompanionEntity other = loaded(server, otherId);
			if (other != null && died) {
				Speech.say(other, Line.MOURN_PARTNER, p.name);
				other.needs().add(Needs.Need.SOCIAL, -40);
				other.needs().add(Needs.Need.FUN, -30);
			}
			if (Weddings.involves(p.id)) {
				Weddings.clear();
			}
		}
		for (PeopleData.Person child : data.childrenOf(p.id)) {
			if (child.alive() && p.id.equals(child.homeParent)) {
				child.homeParent = null;
				for (UUID parent : child.parents) {
					if (!parent.equals(p.id) && data.person(parent).map(PeopleData.Person::alive).orElse(false)) {
						child.homeParent = parent;
					}
				}
			}
		}
		Homes.get().moveOut(server, p.id);
	}
}
