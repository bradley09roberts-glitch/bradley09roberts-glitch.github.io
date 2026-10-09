package io.github.bradley09roberts.hardcorefriends.town;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.survival.Trips;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Siege nights, a group event for servers that want one ({@code siegeNights}, off by default). Once the camp is a
 * Village, every five to eight nights a wave of monsters gathers at the camp's edge at midnight. It is announced at
 * dusk ("Something's stirring tonight. Stay close.") so players can come and help. The wave is fair for the camp:
 * {@value #MIN_WAVE} to {@value #MAX_WAVE} zombies, skeletons and spiders (never creepers, which would blow up
 * buildings), more for a bigger camp and more defenders. They only appear on dark ground where monsters could spawn
 * anyway, never within {@value #PLAYER_GAP} blocks of a player or {@value #FRIEND_GAP} of a friend, never on or right
 * beside anything built, and only while a player is near enough to the camp for them to stay. At dawn the wave's
 * monsters still near the camp are gone. If everyone lives to see the dawn, the camp gains {@value #REWARD} Unity.
 */
public final class Sieges {
	public static final int MIN_WAVE = 4;
	public static final int MAX_WAVE = 12;
	public static final int PLAYER_GAP = 24;
	public static final int FRIEND_GAP = 8;
	public static final int REWARD = 30;
	/** A player must be within this many blocks of the camp centre for the wave to come (monsters far from every player despawn). */
	private static final int PLAYER_NEAR = 112;
	private static final int NONE = 0;
	private static final int ANNOUNCED = 1;
	private static final int SPAWNED = 2;
	/** Tag on every monster of a wave. */
	static final String TAG = "hardcorefriends.siege";
	/** A monster of the wave goes for a defender this close (about a zombie's own follow range). */
	private static final double TARGET_RANGE = 32;

	private Sieges() {
	}

	/** Every second: plan the next siege night, warn at dusk, gather the wave at midnight, and reward the dawn. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 13) {
			return;
		}
		TownData data = TownData.get(server);
		CampData camp = Camp.data(server);
		ServerLevel level = campLevel(server, camp);
		if (level == null) {
			if (data.siegePhase() != NONE) {
				data.setSiege(NONE, -1, false); // the camp moved somewhere without nights: no siege tonight
			}
			return;
		}
		long day = Camp.day(level);
		long time = Camp.timeOfDay(level);
		int phase = data.siegePhase();
		if (phase != NONE) {
			// The dawn after the siege night (or a night skipped by sleeping, or any later day).
			if (day > data.siegeDay() && time < 12000 || day > data.siegeDay() + 1) {
				dawn(server, level, data, phase);
			} else if (phase == ANNOUNCED && day == data.siegeDay() && time >= 18000 && time < 20000) {
				gather(level, camp, data);
			}
			return;
		}
		if (!FriendsConfig.get().siegeNights || camp.stage() < 3) {
			return;
		}
		if (data.nextSiegeDay() < 0 || data.nextSiegeDay() < day) {
			data.planSiege(day + 5 + level.getRandom().nextInt(4));
			return;
		}
		if (data.nextSiegeDay() == day && time >= 12000 && time < 13000) {
			data.setSiege(ANNOUNCED, day, false);
			warn(server);
		}
	}

	/** The camp's level, if it has a day and night (no sieges in the Nether or the End). */
	private static @Nullable ServerLevel campLevel(MinecraftServer server, CampData camp) {
		for (ServerLevel level : server.getAllLevels()) {
			if (Camp.isCampLevel(level, camp)) {
				return level.dimensionType().hasFixedTime() ? null : level;
			}
		}
		return null;
	}

	private static void warn(MinecraftServer server) {
		List<CompanionEntity> friends = Companions.all();
		if (friends.isEmpty()) {
			notice(server, "Something's stirring tonight. Stay close to the camp.");
		} else {
			Trips.announce(friends.get(server.overworld().getRandom().nextInt(friends.size())), Line.SIEGE_DUSK);
		}
		notice(server, "A siege night: at midnight monsters will gather at the edge of the camp. Come and help defend it!");
	}

	/** Midnight: the wave gathers at the camp's edge, on one side, if anyone is near enough to face it. */
	private static void gather(ServerLevel level, CampData camp, TownData data) {
		data.setSiege(SPAWNED, data.siegeDay(), data.siegeLost());
		BlockPos centre = camp.campPos().orElseThrow();
		int playersNear = 0;
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && Camp.horizontalDistSqr(p.blockPosition(), centre) <= (double) PLAYER_NEAR * PLAYER_NEAR) {
				playersNear++;
			}
		}
		if (playersNear == 0 || level.getDifficulty() == Difficulty.PEACEFUL) {
			// Nothing comes, so there is nothing to reward at dawn either.
			notice(level.getServer(), "Whatever was stirring passed the camp by tonight.");
			data.setSiege(NONE, -1, false);
			data.planSiege(Camp.day(level) + 5 + level.getRandom().nextInt(4));
			return;
		}
		int radius = Camp.radius(camp);
		int defenders = 0;
		for (CompanionEntity c : Companions.in(level)) {
			if (Camp.horizontalDistSqr(c.blockPosition(), centre) <= (double) (radius + 16) * (radius + 16)) {
				defenders++;
			}
		}
		int wave = Mth.clamp(MIN_WAVE + camp.stage() - 2 + defenders / 3 + (playersNear - 1), MIN_WAVE, MAX_WAVE);
		RandomSource random = level.getRandom();
		double side = random.nextDouble() * Math.PI * 2;
		int spawned = 0;
		for (int attempt = 0; attempt < wave * 8 && spawned < wave; attempt++) {
			double angle = side + (random.nextDouble() - 0.5) * Math.toRadians(80);
			double dist = radius + 2 + random.nextInt(7);
			int x = Mth.floor(centre.getX() + 0.5 + Math.cos(angle) * dist);
			int z = Mth.floor(centre.getZ() + 0.5 + Math.sin(angle) * dist);
			EntityType<? extends Mob> type = pick(random);
			if (spawnOne(level, type, x, z)) {
				spawned++;
			}
		}
		if (spawned == 0) {
			notice(level.getServer(), "Whatever was stirring found no way to the camp tonight.");
			data.setSiege(NONE, -1, false);
			data.planSiege(Camp.day(level) + 5 + random.nextInt(4));
		}
	}

	/** Mostly zombies, some skeletons, a few spiders; never creepers. */
	private static EntityType<? extends Mob> pick(RandomSource random) {
		int roll = random.nextInt(10);
		if (roll < 6) {
			return EntityTypes.ZOMBIE;
		}
		return roll < 9 ? EntityTypes.SKELETON : EntityTypes.SPIDER;
	}

	/** One monster on the surface at this column, where one could spawn naturally in the dark, away from everyone. */
	private static boolean spawnOne(ServerLevel level, EntityType<? extends Mob> type, int x, int z) {
		BlockPos column = new BlockPos(x, level.getMinY(), z);
		if (!level.isLoaded(column) || !level.isPositionEntityTicking(column)) {
			return false;
		}
		BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		CampData camp = Camp.data(level.getServer());
		// Never on or right beside anything built: the friends' work, or what looks player-made (a path, a fence, a chest).
		if (level.getBlockState(pos.below()).is(ModTags.BUILD_MARKERS) || camp.isPlacedByFriends(level, pos.below())
			|| WorldEditGuard.looksPlayerBuilt(level, pos, 1, camp)) {
			return false;
		}
		AABB near = new AABB(pos).inflate(PLAYER_GAP);
		if (!level.getEntitiesOfClass(ServerPlayer.class, near, p -> !p.isSpectator()).isEmpty()
			|| !Companions.nearAnyone(level, new AABB(pos).inflate(FRIEND_GAP)).isEmpty()) {
			return false;
		}
		if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)
			|| !SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, level.getRandom())) {
			return false;
		}
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return false;
		}
		mob.snapTo(x + 0.5, pos.getY(), z + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		if (!level.noCollision(mob) || !level.isUnobstructed(mob)) {
			mob.discard();
			return false;
		}
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		mob.addTag(TAG);
		for (Entity rider : mob.getIndirectPassengers()) {
			rider.addTag(TAG); // a spider's skeleton rider is part of the wave too
		}
		level.addFreshEntityWithPassengers(mob);
		// The wave comes for the camp: the nearest friend within reach, or else the nearest player.
		LivingEntity target = nearestDefender(level, mob);
		if (target != null) {
			mob.setTarget(target);
		}
		return true;
	}

	/** The nearest friend on the team within {@value #TARGET_RANGE} blocks of the monster, or else the nearest player. */
	private static @Nullable LivingEntity nearestDefender(ServerLevel level, Mob mob) {
		LivingEntity best = null;
		double bestDist = TARGET_RANGE * TARGET_RANGE;
		for (CompanionEntity c : Companions.near(level, mob.getBoundingBox().inflate(TARGET_RANGE))) {
			double d = c.distanceToSqr(mob);
			if (d < bestDist) {
				bestDist = d;
				best = c;
			}
		}
		if (best == null) {
			for (ServerPlayer p : level.players()) {
				double d = p.distanceToSqr(mob);
				if (!p.isSpectator() && !p.isCreative() && p.isAlive() && d < bestDist) {
					bestDist = d;
					best = p;
				}
			}
		}
		return best;
	}

	/** The morning after: a reward if nobody fell, and the next siege night planned. */
	private static void dawn(MinecraftServer server, ServerLevel level, TownData data, int phase) {
		boolean lost = data.siegeLost();
		data.setSiege(NONE, -1, false);
		data.planSiege(Camp.day(level) + 5 + level.getRandom().nextInt(4));
		if (phase != SPAWNED) {
			return;
		}
		disperse(level, Camp.data(server));
		if (lost) {
			notice(server, "The siege is over, but not everyone saw the dawn.");
			return;
		}
		List<CompanionEntity> friends = Companions.all();
		if (!friends.isEmpty()) {
			Trips.announce(friends.get(level.getRandom().nextInt(friends.size())), Line.SIEGE_HELD);
		}
		Unity.add(level, "siege", REWARD, 0);
		notice(server, "Everyone lived through the siege night. Unity +" + REWARD + ".");
	}

	/**
	 * The wave leaves with the night: its monsters still about near the camp at dawn are gone (one a player has named
	 * stays). Any further off despawn as monsters do when no player is near.
	 */
	private static void disperse(ServerLevel level, CampData camp) {
		camp.campPos().ifPresent(centre -> {
			int r = Camp.radius(camp) + 48;
			AABB around = new AABB(centre).inflate(r, 64, r);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && m.entityTags().contains(TAG) && !m.hasCustomName())) {
				mob.discard();
			}
		});
	}

	/** A friend fell during a siege night: no reward this time. */
	static void friendDied(CompanionEntity c, ServerLevel level, DamageSource source) {
		if (c.isTeamMember()) {
			TownData.get(level.getServer()).markSiegeLoss();
		}
	}

	/** A player died during a siege night, in the camp's dimension: no reward this time. */
	static void playerDied(ServerPlayer player) {
		ServerLevel level = player.level();
		if (Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			TownData.get(level.getServer()).markSiegeLoss();
		}
	}

	/** A plain announcement to every player, in grey. */
	private static void notice(MinecraftServer server, String text) {
		Speech.announce(server, Component.literal(text).withStyle(ChatFormatting.GRAY));
	}
}
