package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * A lonely friend goes and has a chat. They pick another friend nearby who is awake and not in trouble (preferring
 * one who is free and one they have not just talked to), walk over, and the two face each other and exchange a few
 * words. A friend who is only idling stops to listen; a busy one answers over their shoulder and carries on. A player
 * in camp is good company too.
 *
 * <p>Both sides feel better afterwards (the one who came over a little more), and every chat between friends adds
 * a point of Unity. The same two friends wait a couple of minutes before chatting again.
 */
public final class SocializeTask implements CompanionTask {
	/** Below this social need a friend goes looking for company. */
	static final double LONELY = 45;
	static final double STARTER_GAIN = 35;
	static final double PARTNER_GAIN = 20;
	static final double PLAYER_GAIN = 30;
	private static final double FRIEND_RANGE = 24;
	private static final double PLAYER_RANGE = 16;
	private static final double TALK_REACH = 2.5;
	private static final double PLAYER_REACH = 3.0;
	/** How long a chat lasts once the two are together. */
	private static final int CHAT_TICKS = 20 * 5;
	private static final int REPLY_AT = 30;
	private static final int APPROACH_TICKS = 20 * 25;
	/** A friend who is free stops for a chat: enough to set aside idling and odd jobs, not real work. */
	private static final double LISTEN_SCORE = 60;
	private static final double LISTEN_DISTANCE = 6;
	private static final int LISTEN_TICKS = 20 * 12;
	private static final long SAME_PAIR_GAP = 20 * 120;

	/**
	 * Friends who have been asked for a chat: who asked them, and until when the invitation stands (a chat never runs
	 * longer than the job's time limit, so an invitation left behind by a friend who was unloaded mid-chat lapses).
	 */
	private static final Map<UUID, Invite> INVITES = new HashMap<>();
	/** When each pair (friend or player) last chatted, so chats get spread around. */
	private static final Map<String, Long> LAST_CHAT = new HashMap<>();

	private record Invite(UUID from, long until) {
	}

	private @Nullable CompanionEntity partner;
	private @Nullable Player player;
	private @Nullable UUID listeningTo;
	private boolean together;
	private int ticks;

	@Override
	public String id() {
		return "needs.socialize";
	}

	@Override
	public String describe() {
		if (listeningTo != null) {
			return "having a chat";
		}
		if (partner != null) {
			return "chatting with " + partner.displayName();
		}
		return player != null ? "chatting with " + player.getName().getString() : "looking for company";
	}

	@Override
	public double score(CompanionEntity c) {
		Invite invite = invitation(c.getUUID(), c.level().getGameTime());
		if (invite != null) {
			CompanionEntity starter = companion(c, invite.from());
			if (starter == null) {
				INVITES.remove(c.getUUID()); // they left; the invitation lapses
			} else {
				return starter.distanceTo(c) <= LISTEN_DISTANCE ? LISTEN_SCORE : 0;
			}
		}
		double social = c.needs().get(Need.SOCIAL);
		if (social >= LONELY) {
			return 0;
		}
		if (pickFriend(c) == null && pickPlayer(c) == null) {
			return 0;
		}
		return 30 + (LONELY - social) * 0.8; // 30 when a little lonely, up to 66
	}

	@Override
	public boolean start(CompanionEntity c) {
		ticks = 0;
		together = false;
		partner = null;
		player = null;
		listeningTo = null;
		long now = c.level().getGameTime();
		Invite invite = invitation(c.getUUID(), now);
		if (invite != null) {
			listeningTo = invite.from();
			return companion(c, invite.from()) != null;
		}
		CompanionEntity friend = pickFriend(c);
		Player p = pickPlayer(c);
		if (p != null && (friend == null || p.distanceToSqr(c) < friend.distanceToSqr(c))) {
			player = p;
			return true;
		}
		if (friend == null) {
			return false;
		}
		partner = friend;
		INVITES.put(friend.getUUID(), new Invite(c.getUUID(), now + maxTicks()));
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (listeningTo != null) {
			return listen(c);
		}
		if (player != null) {
			return chatWithPlayer(c, player);
		}
		CompanionEntity friend = partner;
		if (friend == null || !available(friend, c)) {
			return TaskStatus.FAILURE;
		}
		if (!together) {
			if (c.actions().walkToEntity(friend, TALK_REACH)) {
				together = true;
				ticks = 0;
				Speech.say(c, Line.CHAT, friend.displayName());
			} else if (c.actions().isStuck() || ++ticks > APPROACH_TICKS) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		if (c.distanceTo(friend) > TALK_REACH + 1.5) {
			c.actions().walkToEntity(friend, TALK_REACH); // a busy friend may wander a step or two while they talk
		} else {
			c.actions().stopWalking();
		}
		c.getLookControl().setLookAt(friend);
		friend.getLookControl().setLookAt(c);
		if (++ticks == REPLY_AT) {
			Speech.say(friend, Line.CHAT_REPLY, c.displayName());
		}
		if (ticks < CHAT_TICKS) {
			return TaskStatus.RUNNING;
		}
		c.needs().add(Need.SOCIAL, STARTER_GAIN);
		friend.needs().add(Need.SOCIAL, PARTNER_GAIN);
		Unity.chat((ServerLevel) c.level());
		remember(c.getUUID(), friend.getUUID(), c.level().getGameTime());
		return TaskStatus.SUCCESS;
	}

	/** Answering a friend who came over: stand still and face them until they have had their say. */
	private TaskStatus listen(CompanionEntity c) {
		UUID starterId = listeningTo;
		Invite invite = invitation(c.getUUID(), c.level().getGameTime());
		CompanionEntity starter = starterId == null ? null : companion(c, starterId);
		if (starter == null || invite == null || !invite.from().equals(starterId) || ++ticks > LISTEN_TICKS) {
			return TaskStatus.SUCCESS; // the chat is over
		}
		c.actions().stopWalking();
		c.getLookControl().setLookAt(starter);
		return TaskStatus.RUNNING;
	}

	private TaskStatus chatWithPlayer(CompanionEntity c, Player p) {
		if (!p.isAlive() || p.isRemoved() || p.level() != c.level() || p.isSpectator()) {
			return TaskStatus.FAILURE;
		}
		if (!together) {
			if (c.actions().walkToEntity(p, PLAYER_REACH)) {
				together = true;
				ticks = 0;
				Speech.say(c, Line.GREETING, "taking a break to chat", p.getName().getString());
			} else if (c.actions().isStuck() || ++ticks > APPROACH_TICKS) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		boolean walkedOff = c.distanceTo(p) > PLAYER_REACH + 3;
		if (!walkedOff) {
			c.actions().stopWalking();
			c.getLookControl().setLookAt(p);
			if (++ticks < CHAT_TICKS) {
				return TaskStatus.RUNNING;
			}
		}
		// A player who walks off mid-chat still counts for the part that happened.
		c.needs().add(Need.SOCIAL, PLAYER_GAIN * Math.min(ticks, CHAT_TICKS) / CHAT_TICKS);
		remember(c.getUUID(), p.getUUID(), c.level().getGameTime());
		return TaskStatus.SUCCESS;
	}

	// ------------------------------------------------------------- choosing

	/** The best friend to talk to: close, free, not just talked to. Null if nobody suitable is within range. */
	private static @Nullable CompanionEntity pickFriend(CompanionEntity c) {
		CompanionEntity best = null;
		double bestRank = Double.MAX_VALUE;
		long now = c.level().getGameTime();
		for (CompanionEntity other : Companions.all()) {
			if (other == c || other.level() != c.level() || !available(other, c) || inChat(other.getUUID(), now)) {
				continue;
			}
			double d = other.distanceToSqr(c);
			if (d > FRIEND_RANGE * FRIEND_RANGE || chattedRecently(c.getUUID(), other.getUUID(), now)) {
				continue;
			}
			double rank = d + (isFree(other) ? 0 : 100); // a free friend is worth a few extra steps
			if (rank < bestRank) {
				bestRank = rank;
				best = other;
			}
		}
		return best;
	}

	/** A player in camp within talking distance, if the friend has not just chatted with them. */
	private static @Nullable Player pickPlayer(CompanionEntity c) {
		Player p = c.level().getNearestPlayer(c, PLAYER_RANGE);
		if (p == null || p.isSpectator() || !p.isAlive() || !Spots.inCamp(c, p.blockPosition())
			|| chattedRecently(c.getUUID(), p.getUUID(), c.level().getGameTime())) {
			return null;
		}
		return p;
	}

	/** Awake, working near camp and not fleeing, fighting or following someone. */
	private static boolean available(CompanionEntity other, CompanionEntity asker) {
		return other.isAlive() && !other.isRemoved() && other.level() == asker.level() && !other.isAsleep()
			&& !other.isRetreating() && other.getTarget() == null && other.mode() != CompanionMode.FOLLOW;
	}

	/** Idling or looking after themselves rather than in the middle of work. */
	private static boolean isFree(CompanionEntity other) {
		CompanionTask job = other.mode() == CompanionMode.WORK ? other.scheduler().current() : null;
		return job == null || job.id().startsWith("needs.") || job.id().equals("common.idle");
	}

	/** The standing invitation for this friend, if any; lapsed ones are forgotten. */
	private static @Nullable Invite invitation(UUID friend, long now) {
		Invite invite = INVITES.get(friend);
		if (invite != null && now > invite.until()) {
			INVITES.remove(friend);
			return null;
		}
		return invite;
	}

	/** Already asked for a chat by someone, or asking someone. */
	private static boolean inChat(UUID friend, long now) {
		if (invitation(friend, now) != null) {
			return true;
		}
		for (Invite invite : INVITES.values()) {
			if (invite.from().equals(friend) && now <= invite.until()) {
				return true;
			}
		}
		return false;
	}

	private static boolean chattedRecently(UUID a, UUID b, long now) {
		Long last = LAST_CHAT.get(pairKey(a, b));
		return last != null && now - last < SAME_PAIR_GAP && now >= last;
	}

	/** Notes a chat, forgetting chats too long ago to matter so the memory stays small. */
	private static void remember(UUID a, UUID b, long now) {
		LAST_CHAT.values().removeIf(last -> now - last >= SAME_PAIR_GAP || now < last);
		LAST_CHAT.put(pairKey(a, b), now);
	}

	private static String pairKey(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "/" + b : b + "/" + a;
	}

	private static @Nullable CompanionEntity companion(CompanionEntity c, UUID id) {
		Entity e = ((ServerLevel) c.level()).getEntity(id);
		return e instanceof CompanionEntity other && other.isAlive() && !other.isRemoved() ? other : null;
	}

	@Override
	public void stop(CompanionEntity c) {
		if (partner != null) {
			Invite invite = INVITES.get(partner.getUUID());
			if (invite != null && invite.from().equals(c.getUUID())) {
				INVITES.remove(partner.getUUID());
			}
		}
		partner = null;
		player = null;
		listeningTo = null;
		together = false;
		ticks = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 20;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 45;
	}

	/** Forgets every invitation and chat memory (tests and server restarts). */
	public static void clear() {
		INVITES.clear();
		LAST_CHAT.clear();
	}
}
