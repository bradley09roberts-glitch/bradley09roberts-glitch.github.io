package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.combat.KillCredit;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.survival.ChunkLoader;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Expeditions with a player: friends who follow go through portals with them and come back with them, build and
 * light the Nether portal, barter with piglins, hunt blazes, find the stronghold by throwing eyes of ender, fill the
 * End portal, and fight the dragon: arrows for the crystals, pillars to the caged ones, blows when it perches.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 *
 * <p>Every tick, each friend is kept off vanilla portal travel ({@link #PORTAL_GUARD}): they only cross on purpose,
 * with this package. Once a second, a friend left on their own counts the time they have waited ({@link Travel}); once
 * every two seconds the team looks round each player they follow for a Nether fortress, a stronghold or an open End
 * portal (Sage's plan moves on when the players found them first), and the dragon fight is looked at
 * ({@link DragonFight}).
 */
public final class Expeditions {
	/**
	 * The portal cooldown every friend is kept on (refreshed before it runs out). On cooldown, standing in a portal does
	 * nothing, so a friend wandering through the camp's portal never ends up in the Nether alone.
	 */
	public static final int PORTAL_GUARD = 300;
	/** Players this close to a friend following them count as leading an expedition here. */
	private static final double WITH_FRIENDS = 32;

	private Expeditions() {
	}

	public static void init() {
		WorldEditGuard.POLICIES.put(WorldEditGuard.Reason.EXPEDITION, new ExpeditionPolicy());
		ExpeditionLines.register();
		PortalFollow.register();
		CompanionEntity.awayHome = Travel::awayHome;
		ChunkLoader.roamsInAnyMode = ChunkLoader.roamsInAnyMode.or(Travel::roaming);

		// Jobs: the camp's portal (the builder first) and finding the stronghold (Scout first, a stand-in without her).
		SpecialityTask.EXCLUSIVE.addAll(Set.of(PortalBuildTask.ID, StrongholdTask.ID));
		TaskRegistry.PACKS.add(id -> List.of(
			new SpecialityTask(new PortalBuildTask(), Role.BUILDER),
			new SpecialityTask(new StrongholdTask(), Role.EXPLORER)));

		CompanionEvents.GOALS.add((companion, goals, targets) -> {
			// Up a pillar to a cage is a reflex like pillaring out of reach: nothing interrupts it once begun.
			goals.addGoal(-1, new CageClimbGoal(companion));
			goals.addGoal(1, new EndHazardGoal(companion));
			goals.addGoal(3, new PackGoal(companion));
			goals.addGoal(3, new DragonFightGoal(companion));
			goals.addGoal(4, new TravelGoal(companion));
			goals.addGoal(4, new FillPortalGoal(companion));
			goals.addGoal(4, new NetherHelpGoal(companion));
		});
		CompanionEvents.TICK.add(Expeditions::tick);
		CompanionEvents.HIT.add(Expeditions::onHit);
		CompanionEvents.DEATH.add((companion, level, source) -> leaveParty(companion));
		CompanionEvents.DISMISSED.add((companion, level) -> leaveParty(companion));
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (victim instanceof EnderDragon dragon && victim.level() instanceof ServerLevel level) {
				DragonFight.defeated(level, dragon);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Expeditions::serverTick);
		FriendsCommand.EXTENSIONS.add(PartyCommand::register);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DragonFight.clear();
			StrongholdTask.clear();
			NetherHelpGoal.clear();
		});
	}

	private static void leaveParty(CompanionEntity c) {
		if (c.level() instanceof ServerLevel level) {
			ExpeditionData.get(level.getServer()).leave(c.getUUID());
		}
	}

	// ------------------------------------------------------------- per friend

	/** Every tick, for every friend (see the class description). */
	private static void tick(CompanionEntity c, ServerLevel level) {
		if (c.getPortalCooldown() < 40) {
			c.setPortalCooldown(PORTAL_GUARD);
		}
		if (level.dimension() == Level.END && c.getTarget() instanceof EnderDragon) {
			c.setTarget(null); // never chased like a mob over the void: the dragon fight goals deal with it
		}
		if ((c.tickCount + c.getId()) % 20 == 0 && c.isTeamMember()) {
			countWait(c, level);
		}
	}

	/**
	 * A friend left on their own counts the time: following a leader who is gone (offline, dead) or out of reach (in
	 * another dimension with no known way through), or told to stay in another dimension with no player left in it.
	 * After {@link Travel#WAIT_LIMIT} they go back to work: away from home, that means home through the portal they
	 * came in by ({@link TravelGoal}).
	 */
	private static void countWait(CompanionEntity c, ServerLevel level) {
		boolean alone = switch (c.mode()) {
			case FOLLOW -> {
				ServerPlayer leader = c.leader();
				boolean present = leader != null && leader.isAlive() && !leader.isSpectator();
				if (present && leader.level() == level) {
					yield false;
				}
				yield !(present && FriendsConfig.get().friendsFollowThroughPortals && Travel.wayTo(c, leader) != null);
			}
			case STAY -> Travel.abroad(c) && nobodyIn(level);
			default -> false;
		};
		if (!alone) {
			if (Travel.waited(c) > 0) {
				Travel.setWaited(c, 0);
			}
			return;
		}
		int waited = Travel.waited(c) + 20;
		if (waited < Travel.WAIT_LIMIT) {
			Travel.setWaited(c, waited);
			return;
		}
		Travel.setWaited(c, 0);
		boolean abroad = Travel.abroad(c);
		c.setMode(CompanionMode.WORK, null);
		Speech.say(c, abroad ? Line.PORTAL_HOME : Line.WORK);
	}

	private static boolean nobodyIn(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator()) {
				return false;
			}
		}
		return true;
	}

	/** A blaze brought down in the Nether with a player near enough to be credited: a rod, with luck. */
	private static void onHit(CompanionEntity c, ServerLevel level, Entity target, boolean killed) {
		if (killed && target instanceof Blaze && level.dimension() == Level.NETHER && KillCredit.creditedPlayer(c, level) != null) {
			Speech.say(c, Line.BLAZE_ROD);
		}
	}

	// ------------------------------------------------------------------- team

	private static void serverTick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % 20 == 7) {
			DragonFight.tick(server);
		}
		if (tick % 40 == 13) {
			lookAround(server);
		}
	}

	/**
	 * Round each player leading friends: in the Nether, a fortress they stand in is remembered (and one friend says so);
	 * in the overworld, a stronghold they are in, or an End portal open beside them, moves Sage's plan on.
	 */
	private static void lookAround(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			ServerLevel level = player.level();
			CompanionEntity friend = followerNear(player, level);
			if (friend == null) {
				continue;
			}
			BlockPos at = player.blockPosition();
			if (level.dimension() == Level.NETHER) {
				fortress(level, at, friend);
			} else if (level.dimension() == Level.OVERWORLD && EndPortal.inStronghold(level, at)) {
				stronghold(level, at);
			}
		}
	}

	/** The nearest friend following this player within {@value #WITH_FRIENDS} blocks, or null. */
	private static @Nullable CompanionEntity followerNear(ServerPlayer player, ServerLevel level) {
		CompanionEntity best = null;
		double bestDist = WITH_FRIENDS * WITH_FRIENDS;
		for (CompanionEntity c : Companions.in(level)) {
			ServerPlayer leader = c.leader();
			if (c.mode() == CompanionMode.FOLLOW && leader != null && leader.getUUID().equals(player.getUUID())) {
				double d = c.distanceToSqr(player);
				if (d < bestDist) {
					bestDist = d;
					best = c;
				}
			}
		}
		return best;
	}

	private static void fortress(ServerLevel level, BlockPos at, CompanionEntity friend) {
		Structure fortress = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.FORTRESS);
		if (fortress == null || !level.isLoaded(at)) {
			return;
		}
		StructureStart start = level.structureManager().getStructureAt(at, fortress);
		if (!start.isValid()) {
			return;
		}
		BoundingBox box = start.getBoundingBox();
		BlockPos middle = new BlockPos((box.minX() + box.maxX()) / 2, at.getY(), (box.minZ() + box.maxZ()) / 2);
		if (ExpeditionData.get(level.getServer()).addPlace(ExpeditionData.FORTRESS, Travel.dimId(level), middle, level.getGameTime())) {
			Speech.say(friend, Line.FORTRESS_SEEN);
		}
	}

	/** The players led the friends into a stronghold (perhaps before Scout found it): remembered, and the plan moves on. */
	private static void stronghold(ServerLevel level, BlockPos at) {
		MinecraftServer server = level.getServer();
		ExpeditionData.get(server).addPlace(ExpeditionData.STRONGHOLD, Travel.dimId(level), at, level.getGameTime());
		if (!ProgressPlan.enabled()) {
			return;
		}
		ProgressPlan.complete(server, Milestone.STRONGHOLD);
		if (portalOpenNear(level, at)) {
			ProgressPlan.complete(server, Milestone.END_PORTAL);
		}
	}

	private static boolean portalOpenNear(ServerLevel level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -3, -6), at.offset(6, 3, 6))) {
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.END_PORTAL)) {
				return true;
			}
		}
		return false;
	}
}
