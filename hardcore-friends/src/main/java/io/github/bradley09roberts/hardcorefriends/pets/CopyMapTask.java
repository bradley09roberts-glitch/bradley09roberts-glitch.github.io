package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.EntityApproach;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.pets.PetsData.MapRecord;
import io.github.bradley09roberts.hardcorefriends.survival.Trips;

/**
 * A player asked for a copy of a map ({@code /friends map}): the map maker makes one from the camp's own stock (an
 * empty map from the chest, or eight paper and a compass made into one at the table) and walks it over. The copy is the
 * same map as the original, as a copy made at a table is. With nothing to make one from, the maker says so and the
 * request is dropped; a request nobody can see to within {@value #EXPIRES_TICKS} ticks lapses with a word to the player.
 * Never a free item: the empty map the copy is made on always comes out of the camp's stock. It is day work, not a night
 * job: at night bedtime outranks it, so a copy under way at nightfall is put down and its request lapses (and
 * {@code /friends map} says to ask again in the morning).
 */
final class CopyMapTask implements CompanionTask {
	static final String ID = "pets.copy_map";
	private static final double SCORE = 72;
	/** A player further than this from the map maker waits until they come closer. */
	private static final double RANGE = 48;
	private static final double HAND_REACH = 2.5;
	static final int EXPIRES_TICKS = 20 * 150;

	/** One player's request: which map, and when they asked (game time). */
	record Request(int mapId, long at) {
	}

	private static final Map<UUID, Request> REQUESTS = new ConcurrentHashMap<>();

	private enum Phase {
		CHEST,
		TABLE,
		GIVE
	}

	private Phase phase = Phase.CHEST;
	private @Nullable UUID player;
	private @Nullable BlockPos table;
	private final EntityApproach approach = new EntityApproach();

	static void request(ServerPlayer player, MapRecord map, long now) {
		REQUESTS.put(player.getUUID(), new Request(map.id, now));
	}

	static boolean pending(UUID player) {
		return REQUESTS.containsKey(player);
	}

	static void clear() {
		REQUESTS.clear();
	}

	/** Lapses requests nobody saw to in time, telling the player (once a second, from the server tick). */
	static void expire(MinecraftServer server, long now) {
		REQUESTS.entrySet().removeIf(e -> {
			if (now - e.getValue().at() < EXPIRES_TICKS && now >= e.getValue().at()) {
				return false;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p != null) {
				p.sendSystemMessage(Component.literal("Nobody could bring you a copy of the map just now. Ask again later, or "
					+ "right-click the camp's explorer with an empty map.").withStyle(ChatFormatting.GRAY));
			}
			return true;
		});
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "making a copy of a map";
	}

	@Override
	public double score(CompanionEntity c) {
		if (REQUESTS.isEmpty() || !(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.WORK
			|| !Maps.isMaker(c) || c.getTarget() != null || c.isAsleep() || Trips.state(c) != null) {
			return 0;
		}
		return waiting(c, level) != null ? SCORE : 0;
	}

	/** A player with a request who is near the maker, in the same level (and inside the camp at night). */
	private static @Nullable ServerPlayer waiting(CompanionEntity c, ServerLevel level) {
		for (UUID id : REQUESTS.keySet()) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null && mayGoTo(c, level, p, false)) {
				return p;
			}
		}
		return null;
	}

	/**
	 * True if the maker may go to this player: in the same level and within {@value #RANGE} blocks, and when dark (a
	 * thunderstorm by day) only with both of them inside the camp (the map maker never walks out into the night for a copy: they set off only from
	 * home, and once {@code underway} stop a step or two past the camp's edge). Asked again all the way over, so a player
	 * who walks off out of the camp is not followed.
	 */
	private static boolean mayGoTo(CompanionEntity c, ServerLevel level, ServerPlayer p, boolean underway) {
		if (p.level() != level || p.isSpectator() || p.distanceToSqr(c) > RANGE * RANGE) {
			return false;
		}
		if (!Camp.isNight(level)) {
			return true;
		}
		Optional<BlockPos> centre = Camp.center(level);
		return centre.isPresent() && PetBrain.aboutCamp(level, centre.get(), p.blockPosition(), 0)
			&& (underway ? PetBrain.aboutCamp(level, centre.get(), c.blockPosition(), 4) : Trips.home(c));
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		ServerPlayer p = waiting(c, level);
		if (p == null) {
			return false;
		}
		player = p.getUUID();
		approach.reset();
		table = null;
		if (c.backpack().count(Workbench.EMPTY_MAP) >= 1) {
			phase = Phase.GIVE;
		} else if (Workbench.carriesMapMakings(c.backpack())) {
			phase = Phase.TABLE;
		} else if (Workbench.chestHasMapMakings(level)) {
			phase = Phase.CHEST;
		} else {
			noPaper(c, p);
			return false;
		}
		return true;
	}

	/** Nothing to make a copy on: the maker says so and the request is dropped. */
	private static void noPaper(CompanionEntity c, ServerPlayer p) {
		REQUESTS.remove(p.getUUID());
		if (!Speech.say(c, Line.MAP_NO_PAPER)) {
			Speech.tell(p, c, "I can't make a copy: the chest is short of paper or a compass.");
		}
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		ServerPlayer p = player == null ? null : level.getServer().getPlayerList().getPlayer(player);
		Request req = player == null ? null : REQUESTS.get(player);
		if (p == null || req == null || !mayGoTo(c, level, p, true)) {
			return TaskStatus.FAILURE; // the request stays: seen to when they are back, or it lapses
		}
		switch (phase) {
			case CHEST -> {
				ChestWalk.State walk = ChestWalk.tick(c);
				if (walk == ChestWalk.State.FAILED) {
					return TaskStatus.FAILURE;
				}
				if (walk == ChestWalk.State.ARRIVED) {
					Optional<Container> chest = ChestWalk.chest(c);
					if (chest.isEmpty() || !Workbench.takeMapMakings(level, chest.get(), c.backpack())) {
						noPaper(c, p);
						return TaskStatus.FAILURE;
					}
					phase = c.backpack().count(Workbench.EMPTY_MAP) >= 1 ? Phase.GIVE : Phase.TABLE;
				}
				return TaskStatus.RUNNING;
			}
			case TABLE -> {
				if (table == null) {
					table = Workbench.mapTable(level);
					if (table == null) {
						return TaskStatus.FAILURE;
					}
				}
				if (!Workbench.atTable(c, table)) {
					c.actions().walkTo(table, Workbench.TABLE_REACH);
					return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
				}
				c.actions().stopWalking();
				c.getLookControl().setLookAt(Vec3.atCenterOf(table));
				boolean crafting = level.getBlockState(table).is(Blocks.CRAFTING_TABLE);
				if (!Workbench.craftEmptyMap(c, crafting)) {
					BlockPos crafter = Workbench.craftingTable(level);
					if (!crafting && crafter != null && !crafter.equals(table)) {
						table = crafter;
						return TaskStatus.RUNNING;
					}
					noPaper(c, p);
					return TaskStatus.FAILURE;
				}
				phase = Phase.GIVE;
				return TaskStatus.RUNNING;
			}
			case GIVE -> {
				if (!approach.walk(c, p, HAND_REACH)) {
					return approach.isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
				}
				c.getLookControl().setLookAt(p);
				Optional<MapRecord> rec = PetsData.get(level.getServer()).map(req.mapId()).filter(Maps::done);
				if (rec.isEmpty()) {
					REQUESTS.remove(p.getUUID());
					return TaskStatus.FAILURE;
				}
				if (c.backpack().remove(Workbench.EMPTY_MAP, 1) < 1) {
					return TaskStatus.FAILURE;
				}
				hand(c, p, Maps.copyOf(level, rec.get()));
				REQUESTS.remove(p.getUUID());
				return TaskStatus.SUCCESS;
			}
		}
		return TaskStatus.FAILURE;
	}

	/** Puts the copy in the player's inventory (at their feet if it is full) and says so. */
	static void hand(CompanionEntity c, ServerPlayer p, ItemStack copy) {
		if (!p.getInventory().add(copy) && !copy.isEmpty()) {
			p.spawnAtLocation(p.level(), copy); // a full inventory: at their feet
		}
		c.swingArm();
		if (!Speech.say(c, Line.MAP_HANDED, p.getName().getString())) {
			Speech.tell(p, c, "Here's a copy of the map.");
		}
		if (c.level() instanceof ServerLevel level) {
			Camp.data(level.getServer()).addStat("map_copies", 1);
		}
	}

	@Override
	public void stop(CompanionEntity c) {
		player = null;
		table = null;
		phase = Phase.CHEST;
		approach.reset();
	}

	@Override
	public int failureCooldown() {
		return 20 * 20;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}
