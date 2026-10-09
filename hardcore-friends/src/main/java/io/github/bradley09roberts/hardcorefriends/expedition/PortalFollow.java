package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;
import io.github.bradley09roberts.hardcorefriends.ai.goal.FollowLeaderGoal;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;

/**
 * Friends following a player go through portals with them. When a player changes dimension (through a portal, or
 * out of the End after the dragon), every friend following them who was within {@value #COME_ALONG} blocks of where
 * they left comes along, onto a safe spot beside them; a follower further off remembers the portal and walks to it
 * ({@link TravelGoal}), and is given a roaming ticket so they keep moving once the player has gone. Each crossing is
 * remembered both ways ({@link ExpeditionData}), so friends can always find a way back.
 *
 * <p>A player can also leave a dimension without a portal: by logging out, by dying, or into the credits after the
 * dragon (the first time through the End's exit portal the player is taken out of the End at once, and only comes
 * back at their spawn point when the credits end). With no player left, that part of the world stops running at once,
 * so this is looked for every tick ({@link #playerLeft}): the friends left there are given roaming tickets so they can
 * wait and then go home, and after the dragon the player's followers are taken home to the camp straight away.
 *
 * <p>Crossings are queued and made at the end of the server tick, outside entity ticking: a friend crossing to
 * another dimension is replaced by a new entity, which must not happen half way through anyone's tick.
 */
final class PortalFollow {
	/** Followers this close to where their leader left come along at once. */
	static final double COME_ALONG = 32;

	/** Where each player was at the end of the last tick, so a crossing knows where they left from. */
	private record Last(ResourceKey<Level> dim, Vec3 pos) {
	}

	/** A player who changed dimension this tick: from where. */
	private record PlayerMove(UUID player, ResourceKey<Level> from, Vec3 fromPos) {
	}

	/** A friend's own crossing, asked for during their tick and made at its end. */
	private record FriendMove(CompanionEntity friend, ServerLevel to, BlockPos near, BlockPos portalHere, BlockPos portalThere,
		@Nullable Line line) {
	}

	/**
	 * A follower coming along with their leader, tried each tick for a little while: the land by the player on the far
	 * side may take a tick or two to start running after they arrive. {@code spoken} is shared by the group, so one
	 * friend speaks for them all.
	 */
	private static final class Along {
		final CompanionEntity friend;
		final UUID player;
		final BlockPos portalHere;
		final BlockPos portalThere;
		final boolean[] spoken;
		int triesLeft = COME_ALONG_TRIES;

		Along(CompanionEntity friend, UUID player, BlockPos portalHere, BlockPos portalThere, boolean[] spoken) {
			this.friend = friend;
			this.player = player;
			this.portalHere = portalHere;
			this.portalThere = portalThere;
			this.spoken = spoken;
		}
	}

	/** How many ticks a follower keeps trying to come along before making for the portal on foot instead. */
	private static final int COME_ALONG_TRIES = 40;

	private static final Map<UUID, Last> LAST = new HashMap<>();
	/** Players already seen gone from where they were (logged out, dead, a spectator, in the credits), so seen once. */
	private static final Set<UUID> GONE = new HashSet<>();
	private static final List<PlayerMove> PLAYER_MOVES = new ArrayList<>();
	private static final List<FriendMove> FRIEND_MOVES = new ArrayList<>();
	private static final List<Along> ALONG = new ArrayList<>();

	private PortalFollow() {
	}

	static void register() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			Last last = LAST.get(player.getUUID());
			if (last != null && last.dim().equals(origin.dimension())) {
				PLAYER_MOVES.add(new PlayerMove(player.getUUID(), origin.dimension(), last.pos()));
			}
		});
		// Leaving the End by the exit portal shows the credits and then makes the player anew at their spawn point
		// (the friends left in the End were looked after when the credits began: see playerLeft).
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (alive && oldPlayer.level() != newPlayer.level()) {
				PLAYER_MOVES.add(new PlayerMove(newPlayer.getUUID(), oldPlayer.level().dimension(), oldPlayer.position()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PortalFollow::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	static void clear() {
		LAST.clear();
		GONE.clear();
		PLAYER_MOVES.clear();
		FRIEND_MOVES.clear();
		ALONG.clear();
	}

	/** Asks for a friend's own crossing, made at the end of this tick (see the class description). */
	static void request(CompanionEntity friend, ServerLevel to, BlockPos near, BlockPos portalHere, BlockPos portalThere,
		@Nullable Line line) {
		for (FriendMove m : FRIEND_MOVES) {
			if (m.friend() == friend) {
				return;
			}
		}
		FRIEND_MOVES.add(new FriendMove(friend, to, near, portalHere, portalThere, line));
	}

	private static void tick(MinecraftServer server) {
		if (!PLAYER_MOVES.isEmpty()) {
			List<PlayerMove> moves = List.copyOf(PLAYER_MOVES);
			PLAYER_MOVES.clear();
			for (PlayerMove move : moves) {
				try {
					playerMoved(server, move);
				} catch (RuntimeException e) {
					HardcoreFriends.LOGGER.error("Friends could not follow a player through a portal", e);
				}
			}
		}
		if (!ALONG.isEmpty()) {
			comeAlong(server);
		}
		if (!FRIEND_MOVES.isEmpty()) {
			List<FriendMove> moves = List.copyOf(FRIEND_MOVES);
			FRIEND_MOVES.clear();
			for (FriendMove move : moves) {
				try {
					CompanionEntity moved = Travel.cross(move.friend(), move.to(), move.near(), move.portalHere(), move.portalThere());
					if (moved != null && move.line() != null) {
						Speech.say(moved, move.line());
					}
				} catch (RuntimeException e) {
					HardcoreFriends.LOGGER.error("{} could not go through a portal", move.friend().displayName(), e);
				}
			}
		}
		for (Map.Entry<UUID, Last> e : LAST.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			// Logged out, dead (or taken out of the End for the credits, which counts as not alive), or a spectator.
			if (player != null && player.isAlive() && !player.isSpectator()) {
				GONE.remove(e.getKey());
			} else if (GONE.add(e.getKey())) {
				try {
					playerLeft(server, player, e.getValue());
				} catch (RuntimeException ex) {
					HardcoreFriends.LOGGER.error("Friends could not be looked after when a player left", ex);
				}
			}
		}
		LAST.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		GONE.removeIf(id -> !LAST.containsKey(id));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			LAST.put(player.getUUID(), new Last(player.level().dimension(), player.position()));
		}
	}

	/**
	 * A player has left the dimension they were in other than through a portal: logged out, died, became a spectator,
	 * or went into the credits after the dragon. Their part of the world stops running at once, so: after the dragon,
	 * their followers go home now ({@link #homeAfterDragon}); and the friends left there with nobody to follow are given
	 * roaming tickets so they can wait and then make their way home ({@link #strandedCheck}).
	 */
	private static void playerLeft(MinecraftServer server, @Nullable ServerPlayer player, Last last) {
		ServerLevel from = server.getLevel(last.dim());
		if (from == null) {
			return;
		}
		if (player != null && player.wonGame && from.dimension() == Level.END) {
			homeAfterDragon(server, player, from, last.pos());
		}
		strandedCheck(from);
	}

	/**
	 * A player went through the End's exit portal for the first time: the credits roll, the player is out of the End
	 * at once and only comes back, at their spawn point, when the credits end (minutes later, or never if they quit).
	 * The End stops running within a second, so the friends following them come home now, to the camp (which keeps
	 * running while anyone is online), and catch up with the player once they are back. Without a running camp to go to
	 * in another dimension, they are left to {@link #strandedCheck} (a roaming ticket while there are any to give, and
	 * they come along when the player respawns).
	 */
	private static void homeAfterDragon(MinecraftServer server, ServerPlayer player, ServerLevel end, Vec3 exitPos) {
		ServerLevel home = server.getLevel(Travel.homeDimension(server));
		if (home == null || home == end) {
			return;
		}
		BlockPos camp = Camp.center(home).filter(home::isPositionEntityTicking).orElse(null);
		if (camp == null) {
			return;
		}
		boolean spoken = false;
		for (CompanionEntity c : Companions.in(end)) {
			ServerPlayer leader = c.leader();
			if (c.mode() != CompanionMode.FOLLOW || leader == null || !leader.getUUID().equals(player.getUUID()) || c.isRemoved()) {
				continue;
			}
			ALONG.removeIf(a -> a.friend == c);
			CompanionEntity moved = Travel.cross(c, home, camp, BlockPos.containing(exitPos), camp);
			if (moved != null && !spoken) {
				spoken = Speech.say(moved, Line.PORTAL_THROUGH);
			}
		}
	}

	private static void playerMoved(MinecraftServer server, PlayerMove move) {
		ServerPlayer player = server.getPlayerList().getPlayer(move.player());
		ServerLevel from = server.getLevel(move.from());
		if (player == null || from == null || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel to = player.level();
		if (to == from) {
			return;
		}
		BlockPos portalHere = BlockPos.containing(move.fromPos());
		BlockPos portalThere = player.blockPosition();
		ExpeditionData data = ExpeditionData.get(server);
		long now = from.getGameTime();
		data.addCrossing(Travel.dimId(from), portalHere, Travel.dimId(to), portalThere, now);
		if (to.dimension() == Level.NETHER) {
			data.addPlace(ExpeditionData.NETHER_PORTAL, Travel.dimId(to), portalThere, now);
		} else if (from.dimension() == Level.NETHER) {
			data.addPlace(ExpeditionData.NETHER_PORTAL, Travel.dimId(from), portalHere, now);
		}
		if (!FriendsConfig.get().friendsFollowThroughPortals) {
			return;
		}
		List<CompanionEntity> along = new ArrayList<>();
		for (CompanionEntity c : Companions.in(from)) {
			ServerPlayer leader = c.leader();
			if (c.mode() != CompanionMode.FOLLOW || leader == null || !leader.getUUID().equals(player.getUUID()) || c.isRemoved()) {
				continue;
			}
			if (c.position().distanceToSqr(move.fromPos()) <= COME_ALONG * COME_ALONG) {
				along.add(c);
			} else {
				// Too far off to come at once: they make for the portal, and their land keeps running while they do.
				Travel.setChase(c, player.getUUID(), portalHere, Travel.dimId(to), portalThere);
				Travel.holdLand(c, "following " + player.getName().getString() + " through a portal");
			}
		}
		boolean[] spoken = new boolean[1];
		for (CompanionEntity c : along) {
			ALONG.add(new Along(c, player.getUUID(), portalHere, portalThere, spoken));
		}
		comeAlong(server);
		strandedCheck(from);
	}

	/**
	 * Brings each waiting follower across beside their leader (see {@link Along}). One whose leader has gone again, or
	 * who still finds no safe footing by them after a couple of seconds (a lava lake round the portal...), makes for the
	 * portal on foot instead ({@link TravelGoal}).
	 */
	private static void comeAlong(MinecraftServer server) {
		List<Along> waiting = List.copyOf(ALONG);
		ALONG.clear();
		for (Along a : waiting) {
			CompanionEntity c = a.friend;
			ServerPlayer player = server.getPlayerList().getPlayer(a.player);
			if (!c.isAlive() || c.isRemoved() || player == null || !player.isAlive() || c.level() == player.level()) {
				continue;
			}
			ServerLevel to = player.level();
			CompanionEntity moved = null;
			try {
				BlockPos near = FollowLeaderGoal.catchUpSpot(to, a.portalThere, c.getRandom());
				moved = Travel.cross(c, to, near != null ? near : a.portalThere, a.portalHere, a.portalThere);
			} catch (RuntimeException e) {
				HardcoreFriends.LOGGER.error("{} could not come through a portal", c.displayName(), e);
			}
			if (moved != null) {
				if (!a.spoken[0]) {
					a.spoken[0] = Speech.say(moved, Line.PORTAL_THROUGH);
				}
				if (to.dimension() == Level.END && ProgressPlan.enabled()) {
					// Into the End with the friends: whoever found the stronghold, the plan has got this far.
					ProgressPlan.complete(server, Milestone.STRONGHOLD);
					ProgressPlan.complete(server, Milestone.END_PORTAL);
				}
			} else if (--a.triesLeft > 0) {
				ALONG.add(a);
			} else {
				Travel.setChase(c, a.player, a.portalHere, Travel.dimId(to), a.portalThere);
				Travel.holdLand(c, "following " + player.getName().getString() + " through a portal");
			}
		}
	}

	/** True when no living player (spectators do not count) is left in this dimension. */
	private static boolean stranded(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator()) {
				return false;
			}
		}
		return true;
	}

	/** True when the friend's leader can be followed: online, alive (not dead, not in the credits), not a spectator. */
	private static boolean leaderPresent(CompanionEntity c) {
		ServerPlayer leader = c.leader();
		return leader != null && leader.isAlive() && !leader.isSpectator();
	}

	/**
	 * Friends away from home in this dimension who would freeze where they stand are given a roaming ticket (while
	 * there are any to give), so they can wait by the portal they came in by and then make their way home: once the
	 * last player has left it, everyone there not off after a leader who can be followed; and, even with other players
	 * still about, the followers whose leader has gone (logged out, died, or into the credits after the dragon).
	 */
	private static void strandedCheck(ServerLevel level) {
		boolean empty = stranded(level);
		for (CompanionEntity c : Companions.in(level)) {
			if (!Travel.abroad(c) || Travel.holdsLand(c)) {
				continue; // at home, or their land already keeps running
			}
			boolean following = c.mode() == CompanionMode.FOLLOW;
			if (following && leaderPresent(c) || !empty && !following) {
				continue; // off after a leader who is about (coming along, or following), or other players are here
			}
			if (!Travel.holdLand(c, "making their way home from another dimension")) {
				return; // no more tickets to give
			}
		}
	}
}
