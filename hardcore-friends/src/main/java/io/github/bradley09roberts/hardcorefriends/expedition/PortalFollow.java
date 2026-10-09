package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.survival.ChunkLoader;

/**
 * Friends following a player go through portals with them. When a player changes dimension (through a portal, or
 * out of the End after the dragon), every friend following them who was within {@value #COME_ALONG} blocks of where
 * they left comes along, onto a safe spot beside them; a follower further off remembers the portal and walks to it
 * ({@link TravelGoal}), and is given a roaming ticket so they keep moving once the player has gone. Each crossing is
 * remembered both ways ({@link ExpeditionData}), so friends can always find a way back.
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

	private static final Map<UUID, Last> LAST = new HashMap<>();
	private static final List<PlayerMove> PLAYER_MOVES = new ArrayList<>();
	private static final List<FriendMove> FRIEND_MOVES = new ArrayList<>();

	private PortalFollow() {
	}

	static void register() {
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			Last last = LAST.get(player.getUUID());
			if (last != null && last.dim().equals(origin.dimension())) {
				PLAYER_MOVES.add(new PlayerMove(player.getUUID(), origin.dimension(), last.pos()));
			}
		});
		// Leaving the End by the exit portal shows the credits and then makes the player anew at their spawn point.
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
		PLAYER_MOVES.clear();
		FRIEND_MOVES.clear();
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
		LAST.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			LAST.put(player.getUUID(), new Last(player.level().dimension(), player.position()));
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
				Travel.setRoaming(c, true);
				if (!ChunkLoader.startRoaming(c, "following " + player.getName().getString() + " through a portal")) {
					Travel.setRoaming(c, false);
				}
			}
		}
		boolean spoken = false;
		int crossed = 0;
		for (CompanionEntity c : along) {
			BlockPos near = FollowLeaderGoal.catchUpSpot(to, portalThere, c.getRandom());
			CompanionEntity moved = Travel.cross(c, to, near != null ? near : portalThere, portalHere, portalThere);
			if (moved == null) {
				// No safe footing by the player (a lava lake round the portal...): they make for the portal instead.
				Travel.setChase(c, player.getUUID(), portalHere, Travel.dimId(to), portalThere);
				continue;
			}
			crossed++;
			if (!spoken) {
				spoken = Speech.say(moved, Line.PORTAL_THROUGH);
			}
		}
		if (crossed > 0 && to.dimension() == Level.END && ProgressPlan.enabled()) {
			// Into the End with the friends: whoever found the stronghold, the plan has got this far.
			ProgressPlan.complete(server, Milestone.STRONGHOLD);
			ProgressPlan.complete(server, Milestone.END_PORTAL);
		}
		if (stranded(from)) {
			strandedCheck(from);
		}
	}

	/** True when no player is left in this dimension. */
	private static boolean stranded(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The last player has left a dimension: friends still there, away from home and not off after them, are given a
	 * roaming ticket (while there are any to give) so they can wait at the portal they came in by and then make their
	 * way home, instead of freezing where they stand.
	 */
	private static void strandedCheck(ServerLevel level) {
		for (CompanionEntity c : Companions.in(level)) {
			if (Travel.roaming(c) || !Travel.abroad(c)) {
				continue;
			}
			Travel.setRoaming(c, true);
			if (!ChunkLoader.startRoaming(c, "making their way home from another dimension")) {
				Travel.setRoaming(c, false);
				return; // no more tickets to give
			}
		}
	}
}
