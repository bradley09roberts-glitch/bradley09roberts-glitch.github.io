package io.github.bradley09roberts.hardcorefriends.life;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.civic.Families;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * When someone of the team dies for good. The Chronicle tells it gently ("Fern Hart was lost in a fight with a zombie,
 * far from home."), a grave is made at the cemetery and a funeral held the next evening, and everyone mourns for a
 * while: {@value #FRIEND_DAYS} days for the camp, {@value #FAMILY_DAYS} for a husband or wife, parents and children.
 * Mourning slowly wears down fun and company (never below {@value #FLOOR}, so nobody is pushed into despair), brings
 * sad words now and then, and sends the mourner to the grave ({@link VisitGraveTask}). The people package's own words
 * at the moment of the death, and the camp's goodbye, are left as they are: this adds what comes after.
 */
final class Grief {
	static final long FRIEND_DAYS = 2;
	static final long FAMILY_DAYS = 4;
	/** Mourning never takes fun or company below this. */
	static final double FLOOR = 30;
	/** How much fun and company mourning takes a second (twice as much for family). */
	private static final double FUN_LOSS = 0.04;
	private static final double SOCIAL_LOSS = 0.03;

	private Grief() {
	}

	/** {@code CompanionEvents.DEATH}: the Chronicle, the funeral, the grave and the mourning. */
	static void died(CompanionEntity c, ServerLevel level, DamageSource source) {
		if (!c.isTeamMember()) {
			return;
		}
		MinecraftServer server = level.getServer();
		LifeData data = LifeData.get(server);
		long day = Calendar.today(server);
		long clock = server.overworld().getOverworldClockTime();
		UUID id = c.getUUID();
		String name = c.displayName();
		LifeData.Person person = data.personFor(id, name, day, c.isChild());
		person.gone = true;
		data.mourning.remove(id);
		// Their family as last seen (the families' records may already have let them go by now), and as it stands.
		Set<UUID> family = new LinkedHashSet<>();
		if (person.partner != null) {
			family.add(person.partner);
		}
		family.addAll(person.parents);
		family.addAll(person.children);
		Families.get().partnerOf(server, id).ifPresent(family::add);
		family.addAll(Families.get().parentsOf(server, id));
		family.addAll(Families.get().childrenOf(server, id));
		family.remove(id);
		String full = Chronicle.fullName(server, id, name);
		CampData camp = Camp.data(server);
		if (data.started || camp.campPos().isPresent()) {
			Chronicle.write(server, (c.isChild() ? "Little " : "") + full + " " + gently(source, c, level) + ".");
		}
		if (camp.campPos().isPresent()) {
			holdFuneral(data, id, name, day + 1);
			if (FriendsConfig.get().graves && data.graves.size() < LifeData.MAX_GRAVES) {
				LifeData.Grave grave = new LifeData.Grave(data.nextGrave++, id, name, full, day);
				grave.family.addAll(family);
				grave.dimension = camp.campDimension();
				data.graves.add(grave);
			}
			Speech.announce(server, Component.literal("The village will say goodbye to " + name
				+ " tomorrow evening (/friends calendar).").withStyle(ChatFormatting.GRAY));
		}
		for (LifeData.Person other : data.people.values()) {
			if (other.gone || other.id.equals(id)) {
				continue;
			}
			boolean deep = family.contains(other.id);
			long until = clock + (deep ? FAMILY_DAYS : FRIEND_DAYS) * Calendar.DAY_TICKS;
			LifeData.Mourn old = data.mourning.get(other.id);
			if (old == null || old.until() < until || deep && !old.deep()) {
				data.mourning.put(other.id, new LifeData.Mourn(name, until, deep));
			}
		}
		data.setDirty();
	}

	/** {@code CompanionEvents.DISMISSED}: they leave the camp (and the Chronicle says so). */
	static void dismissed(CompanionEntity c, ServerLevel level) {
		if (!c.isTeamMember()) {
			return;
		}
		MinecraftServer server = level.getServer();
		LifeData data = LifeData.get(server);
		data.person(c.getUUID()).ifPresent(p -> p.gone = true);
		data.mourning.remove(c.getUUID());
		data.setDirty();
		Chronicle.write(server, c.displayName() + " left the camp.");
	}

	/** Adds someone to the funeral held on {@code day} (one funeral for everyone lost the same day). */
	private static void holdFuneral(LifeData data, UUID id, String name, long day) {
		for (LifeData.Funeral f : data.funerals) {
			if (f.day == day && f.ids.size() < 6) {
				f.ids.add(id);
				f.names.add(name);
				return;
			}
		}
		LifeData.Funeral f = new LifeData.Funeral(day);
		f.ids.add(id);
		f.names.add(name);
		data.funerals.add(f);
	}

	/**
	 * Once a second for each friend (a {@code CompanionEvents.TICK} hook, spread out): a mourner's fun and company wear
	 * down a little, and now and then they say they miss the one they lost. Mourning ends on its day.
	 */
	static void tick(CompanionEntity c, ServerLevel level) {
		if ((c.tickCount + c.getId()) % 20 != 0 || !c.isTeamMember() || !c.isAlive()) {
			return;
		}
		MinecraftServer server = level.getServer();
		LifeData data = LifeData.get(server);
		if (data.mourning.isEmpty()) {
			return;
		}
		LifeData.Mourn m = data.mourning.get(c.getUUID());
		if (m == null) {
			return;
		}
		if (server.overworld().getOverworldClockTime() >= m.until()) {
			data.mourning.remove(c.getUUID());
			data.setDirty();
			return;
		}
		double weight = m.deep() ? 2 : 1;
		Needs needs = c.needs();
		if (needs.get(Needs.Need.FUN) > FLOOR) {
			needs.add(Needs.Need.FUN, -FUN_LOSS * weight);
		}
		if (needs.get(Needs.Need.SOCIAL) > FLOOR) {
			needs.add(Needs.Need.SOCIAL, -SOCIAL_LOSS * weight);
		}
		if (!c.isAsleep() && c.getTarget() == null && c.getRandom().nextInt(m.deep() ? 90 : 180) == 0) {
			Speech.say(c, Line.MOURNING, m.name());
		}
	}

	/** A gentle telling of how someone died: "was lost in a fight with a zombie, far from home". */
	static String gently(DamageSource source, CompanionEntity c, ServerLevel level) {
		Entity attacker = source.getEntity();
		String how;
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			how = attacker instanceof LivingEntity && !(attacker instanceof Player) ? "was lost to " + withArticle(attacker) + "'s blast"
				: "was lost in an explosion";
		} else if (attacker instanceof Player) {
			how = "was lost in an accident";
		} else if (attacker instanceof LivingEntity) {
			how = "was lost in a fight with " + withArticle(attacker);
		} else if (source.is(DamageTypeTags.IS_FALL)) {
			how = "was lost in a fall";
		} else if (source.is(DamageTypeTags.IS_DROWNING)) {
			how = "was lost to the water";
		} else if (source.is(DamageTypes.LAVA)) {
			how = "was lost to lava";
		} else if (source.is(DamageTypeTags.IS_FIRE)) {
			how = "was lost to fire";
		} else if (source.is(DamageTypeTags.IS_FREEZING)) {
			how = "was lost to the cold";
		} else if (source.is(DamageTypes.STARVE)) {
			how = "was lost to hunger";
		} else if (source.is(DamageTypes.IN_WALL)) {
			how = "was lost in a cave-in";
		} else if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			how = "was lost to the void";
		} else if (source.is(DamageTypes.LIGHTNING_BOLT)) {
			how = "was struck by lightning";
		} else if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.MAGIC)) {
			how = "was lost to a sickness";
		} else {
			how = "was lost";
		}
		return how + where(c, level);
	}

	/** ", in the Nether", ", in the End", ", far from home", or nothing in or near the camp. */
	private static String where(CompanionEntity c, ServerLevel level) {
		if (level.dimension() == Level.NETHER) {
			return ", in the Nether";
		}
		if (level.dimension() == Level.END) {
			return ", in the End";
		}
		CampData camp = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, camp) || camp.campPos().isEmpty()) {
			return ", far from home";
		}
		int far = Camp.radius(camp) + FriendsConfig.get().resourceRadius;
		return Camp.horizontalDistSqr(camp.campPos().get(), c.blockPosition()) > (double) far * far ? ", far from home" : "";
	}

	/** "a zombie", "an enderman", or a named creature's own name. */
	private static String withArticle(Entity e) {
		if (e.hasCustomName()) {
			return e.getName().getString();
		}
		String name = e.getType().getDescription().getString().toLowerCase(Locale.ROOT);
		return ("aeiou".indexOf(name.isEmpty() ? 'x' : name.charAt(0)) >= 0 ? "an " : "a ") + name;
	}
}
