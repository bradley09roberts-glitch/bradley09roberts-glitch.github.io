package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The settler package's hooks into each newcomer's life ({@code CompanionEvents}): a stranger's first tick (their name
 * checked against the world's living people), greeting players who come close, a traveller arriving and, after a day,
 * leaving; a recruit's records kept up to date (where they were last seen), and arriving at the camp after following
 * the player home; and what happens when one dies or is dismissed.
 *
 * <p>The tick hook runs for every friend every tick, so it returns at once for the nine named friends and does its
 * work once a second per newcomer, spread over the second by entity id.
 */
final class SettlerEvents {
	/** A player this close (and in sight) is greeted. */
	private static final double GREET_RANGE = 5;
	/** A leaving traveller is only taken out of the world when no player is this close... */
	private static final double UNSEEN_NEAR = 24;
	/** ...and no player who could see them is this close. */
	private static final double UNSEEN_FAR = 64;

	private SettlerEvents() {
	}

	/** The {@code CompanionEvents.TICK} hook. */
	static void tick(CompanionEntity c, ServerLevel level) {
		if (!c.isSettler() || (c.tickCount + c.getId()) % 20 != 0 || !c.isAlive()) {
			return;
		}
		if (c.mode() == CompanionMode.STRANGER) {
			strangerSecond(c, level);
		} else {
			recruitSecond(c, level);
		}
	}

	private static void strangerSecond(CompanionEntity c, ServerLevel level) {
		CompoundTag tag = Strangers.state(c);
		SettlerData data = SettlerData.get(level.getServer());
		if (!tag.getBooleanOr(Strangers.REGISTERED, false)) {
			register(c, data);
			tag.putBoolean(Strangers.REGISTERED, true);
		}
		boolean traveller = Personas.Origin.ROAD.key().equals(tag.getStringOr(Strangers.ORIGIN, ""));
		if (traveller && travellerSecond(c, level, tag, data)) {
			return; // gone
		}
		if (!tag.getBooleanOr(Strangers.LEAVING, false)) {
			greet(c, level);
		}
	}

	/**
	 * A stranger made as the world was generated is checked against the living people once it first ticks: a name
	 * already taken by someone living is changed before anyone has met them.
	 */
	private static void register(CompanionEntity c, SettlerData data) {
		Persona persona = c.persona();
		if (persona == null) {
			return;
		}
		if (data.namesInUse(c.getUUID()).contains(persona.name().toLowerCase(Locale.ROOT))) {
			persona = new Persona(Personas.freeName(c.getRandom(), data.namesInUse(c.getUUID())), persona.colour(), persona.skin(),
				persona.archetype());
			c.setPersona(persona);
		}
		data.rememberStranger(c.getUUID(), persona.name());
	}

	private static void greet(CompanionEntity c, ServerLevel level) {
		Player near = level.getNearestPlayer(c, GREET_RANGE);
		if (!(near instanceof ServerPlayer player) || player.isSpectator() || !c.hasLineOfSight(player)) {
			return;
		}
		if (Speech.say(c, Line.STRANGER_HELLO)) {
			c.getLookControl().setLookAt(player);
			if (Strangers.state(c).getIntOr(Strangers.REQUEST, -1) < 0) {
				Strangers.note(player, Strangers.nameAndTrade(c) + " is not on your team. Right-click them to talk.");
			}
		}
	}

	/**
	 * A traveller's day at the camp: they say hello when they reach the camp's edge, and once their day is up they set
	 * off, and are gone as soon as nobody can see them go. Returns true when they have left the world.
	 */
	private static boolean travellerSecond(CompanionEntity c, ServerLevel level, CompoundTag tag, SettlerData data) {
		long now = level.getGameTime();
		if (!tag.getBooleanOr(Strangers.ARRIVED, false)
			&& Camp.horizontalDistSqr(c.blockPosition(), c.homePos()) <= 8 * 8) {
			tag.putBoolean(Strangers.ARRIVED, true);
			Speech.say(c, Line.WANDERER_ARRIVES);
		}
		if (!tag.getBooleanOr(Strangers.LEAVING, false)) {
			if (now >= tag.getLongOr(Strangers.LEAVE_AT, Long.MAX_VALUE)) {
				tag.putBoolean(Strangers.LEAVING, true);
				Speech.say(c, Line.WANDERER_LEAVES);
			}
			return false;
		}
		if (unseen(c, level)) {
			data.forgetStranger(c.getUUID());
			Companions.untrack(c);
			c.discard();
			return true;
		}
		return false;
	}

	/** Nobody is close by, and nobody further off who could see them. */
	private static boolean unseen(CompanionEntity c, ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double d = player.distanceToSqr(c);
			if (d <= UNSEEN_NEAR * UNSEEN_NEAR || d <= UNSEEN_FAR * UNSEEN_FAR && player.hasLineOfSight(c)) {
				return false;
			}
		}
		return true;
	}

	/** A recruit: their record kept fresh, and the walk home ends at the camp. */
	private static void recruitSecond(CompanionEntity c, ServerLevel level) {
		if (c.isChild()) {
			return; // a child of the camp (the people package keeps their records) joins these records when grown up
		}
		CompoundTag tag = Strangers.state(c);
		if (tag.getBooleanOr(Strangers.TO_CAMP, false)) {
			if (c.mode() != CompanionMode.FOLLOW) {
				tag.remove(Strangers.TO_CAMP); // given other orders on the way
			} else {
				CampData camp = Camp.data(level.getServer());
				BlockPos centre = Camp.center(level).orElse(null);
				int r = Camp.radius(camp);
				if (centre != null && Camp.horizontalDistSqr(c.blockPosition(), centre) <= (double) r * r) {
					tag.remove(Strangers.TO_CAMP);
					c.setMode(CompanionMode.WORK, c.leader());
					Speech.say(c, Line.WORK);
				}
			}
		}
		if ((c.tickCount + c.getId()) % 100 != 0) {
			return;
		}
		SettlerData data = SettlerData.get(level.getServer());
		SettlerData.Newcomer record = data.newcomer(c.getUUID()).orElse(null);
		if (record == null) {
			// Recruited before the records were kept, or the records were lost: start them now.
			record = data.recordJoined(c.getUUID(), c.displayName(), c.friendId(), c.nameColour(), uuid(tag.getStringOr(Strangers.RECRUITED_BY, "")),
				tag.getStringOr(Strangers.RECRUITED_BY_NAME, ""), level.getGameTime());
		}
		if (record.state != SettlerData.State.ALIVE) {
			return; // a dismissed or fallen record is final: this is someone the records no longer follow
		}
		BlockPos pos = c.blockPosition();
		String dim = Camp.dimensionId(level);
		if (!pos.equals(record.lastPos) || !dim.equals(record.lastDimension)) {
			record.lastPos = pos;
			record.lastDimension = dim;
			data.setDirty();
		}
	}

	/** The {@code CompanionEvents.DEATH} hook: a fallen recruit is gone for good; a stranger's name is free again. */
	static void died(CompanionEntity c, ServerLevel level, DamageSource source) {
		if (!c.isSettler()) {
			return;
		}
		SettlerData data = SettlerData.get(level.getServer());
		data.forgetStranger(c.getUUID());
		String cause = source.getLocalizedDeathMessage(c).getString();
		if (c.isTeamMember()) {
			data.newcomer(c.getUUID()).ifPresent(n -> {
				n.state = SettlerData.State.DEAD;
				n.cause = cause;
				n.endedAt = level.getGameTime();
				n.lastPos = c.blockPosition();
				n.lastDimension = Camp.dimensionId(level);
				data.setDirty();
			});
			return;
		}
		// A stranger was never on the team, so nobody is told but those close enough to see it.
		Component message = Component.literal(Strangers.nameAndTrade(c) + ", who lived here, has died: " + cause + ".")
			.withStyle(ChatFormatting.GRAY);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(c) <= UNSEEN_FAR * UNSEEN_FAR) {
				player.sendSystemMessage(message);
			}
		}
	}

	/** The {@code CompanionEvents.DISMISSED} hook. */
	static void dismissed(CompanionEntity c, ServerLevel level) {
		if (!c.isSettler()) {
			return;
		}
		SettlerData data = SettlerData.get(level.getServer());
		data.newcomer(c.getUUID()).ifPresent(n -> {
			n.state = SettlerData.State.DISMISSED;
			n.endedAt = level.getGameTime();
			n.lastPos = c.blockPosition();
			n.lastDimension = Camp.dimensionId(level);
			data.setDirty();
		});
	}

	private static @Nullable UUID uuid(String s) {
		try {
			return s.isEmpty() ? null : UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
