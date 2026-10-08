package io.github.bradley09roberts.hardcorefriends.ai.role.scout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.ScoutSenses;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MiningHelper;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.TreeFinder;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * What Scout notices while exploring. Every {@value #INTERVAL} ticks Scout surveys the area within
 * {@value #RADIUS} blocks (±{@value #HEIGHT} vertically), one slice per tick so the work is spread out, and then
 * records points of interest: exposed ores inside the resource zone, natural trees, lava close to camp and villages.
 * Valuable ores and villages are announced; lava near camp is a warning to the players there.
 */
public final class AreaSurvey {
	public static final int RADIUS = 16;
	public static final int HEIGHT = 8;
	public static final int INTERVAL = 40;
	/** Lava closer than this to the camp centre is worth a warning. */
	public static final int LAVA_WARN_RADIUS = 32;
	private static final int MAX_ORES = 32;
	private static final int MAX_TREE_CHECKS = 3;

	private @Nullable BlockPos centre;
	private int slice;
	private long cycleStart = Long.MIN_VALUE / 2;
	private final List<BlockPos> ores = new ArrayList<>();
	private final List<BlockPos> logs = new ArrayList<>();
	private @Nullable BlockPos lava;
	private @Nullable BlockPos bell;

	/** Forgets any half-finished survey; the next tick starts a fresh one. */
	public void reset() {
		centre = null;
		cycleStart = Long.MIN_VALUE / 2;
	}

	/** Advances the survey by one slice. Call every tick while exploring. */
	public void tick(CompanionEntity scout) {
		ServerLevel level = (ServerLevel) scout.level();
		long now = level.getGameTime();
		if (centre == null) {
			if (now - cycleStart < INTERVAL) {
				return;
			}
			begin(scout, now);
		}
		if (slice <= 2 * RADIUS) {
			scanSlice(level, slice++);
			return;
		}
		finish(scout, level);
		centre = null;
	}

	/** Surveys the whole area at once. */
	public void scanNow(CompanionEntity scout) {
		ServerLevel level = (ServerLevel) scout.level();
		begin(scout, level.getGameTime());
		while (slice <= 2 * RADIUS) {
			scanSlice(level, slice++);
		}
		finish(scout, level);
		centre = null;
	}

	private void begin(CompanionEntity scout, long now) {
		centre = scout.blockPosition();
		cycleStart = now;
		slice = 0;
		ores.clear();
		logs.clear();
		lava = null;
		bell = null;
	}

	private void scanSlice(ServerLevel level, int index) {
		BlockPos c = centre;
		if (c == null) {
			return;
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int x = c.getX() - RADIUS + index;
		BlockPos campCentre = Camp.center(level).orElse(null);
		for (int dz = -RADIUS; dz <= RADIUS; dz++) {
			m.set(x, c.getY(), c.getZ() + dz);
			if (!level.isLoaded(m)) {
				continue;
			}
			for (int dy = -HEIGHT; dy <= HEIGHT; dy++) {
				m.setY(c.getY() + dy);
				BlockState state = level.getBlockState(m);
				if (state.isAir()) {
					continue;
				}
				if (MiningHelper.isWantedOre(state)) {
					if (ores.size() < MAX_ORES && MiningHelper.touchesAir(level, m)) {
						ores.add(m.immutable());
					}
				} else if (state.is(BlockTags.LOGS)) {
					if (logs.size() < MAX_TREE_CHECKS * 4 && isTrunkBase(level, m)) {
						logs.add(m.immutable());
					}
				} else if (state.is(Blocks.BELL)) {
					bell = m.immutable();
				} else if (campCentre != null && state.getFluidState().is(FluidTags.LAVA)
					&& Camp.horizontalDistSqr(m, campCentre) <= LAVA_WARN_RADIUS * LAVA_WARN_RADIUS
					&& (lava == null || Camp.horizontalDistSqr(m, campCentre) < Camp.horizontalDistSqr(lava, campCentre))) {
					lava = m.immutable();
				}
			}
		}
	}

	private static boolean isTrunkBase(ServerLevel level, BlockPos log) {
		BlockState below = level.getBlockState(log.below());
		return below.is(BlockTags.DIRT) || below.is(BlockTags.GRASS_BLOCKS) || below.is(BlockTags.SUPPORTS_VEGETATION);
	}

	private void finish(CompanionEntity scout, ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		ScoutLog log = ScoutLog.of(data);
		long now = level.getGameTime();
		BlockPos announce = null;
		int announceRank = 0;
		for (BlockPos ore : ores) {
			if (!WorldEditGuard.inResourceZone(scout, ore) || !data.addPoi(ScoutLog.ORE, ore, now)) {
				continue;
			}
			BlockState state = level.getBlockState(ore);
			int rank = oreRank(state);
			log.noteFind(ScoutLog.ORE, oreName(state) + " at " + Compass.coords(ore), rank);
			if (rank >= 4 && rank > announceRank) {
				announce = ore;
				announceRank = rank;
			}
		}
		int treeChecks = 0;
		for (BlockPos base : logs) {
			if (treeChecks >= MAX_TREE_CHECKS) {
				break;
			}
			if (!WorldEditGuard.inResourceZone(scout, base)) {
				continue;
			}
			treeChecks++;
			if (TreeFinder.isNaturalTreeLog(level, base) && data.addPoi(ScoutLog.TREE, base, now)) {
				log.noteFind(ScoutLog.TREE, "", 0);
			}
		}
		BlockPos village = bell;
		if (village == null) {
			List<Villager> villagers = level.getEntitiesOfClass(Villager.class,
				scout.getBoundingBox().inflate(RADIUS, HEIGHT, RADIUS), Entity::isAlive);
			if (!villagers.isEmpty()) {
				village = villagers.getFirst().blockPosition();
			}
		}
		if (village != null && data.addPoi(ScoutLog.VILLAGE, village, now)) {
			String text = "a village around " + Compass.coords(village);
			log.noteFind(ScoutLog.VILLAGE, text, 10);
			Speech.say(scout, Line.DISCOVERY, text);
		} else if (announce != null) {
			Speech.say(scout, Line.DISCOVERY, oreName(level.getBlockState(announce)) + " at " + Compass.coords(announce));
		}
		if (lava != null && data.addPoi(ScoutLog.LAVA, lava, now)) {
			log.noteFind(ScoutLog.LAVA, "lava at " + Compass.coords(lava) + ", close to camp", 3);
			List<ServerPlayer> nearCamp = ScoutSenses.playersNearCamp(level);
			if (!nearCamp.isEmpty()) {
				ScoutSenses.warnAll(scout, nearCamp, "lava at " + Compass.coords(lava) + ", close to camp - watch your step");
			}
		}
	}

	/** How exciting an ore is: diamond 9, emerald 8, gold 7, lapis 6, redstone 5, iron 4, copper 2, coal 1. */
	public static int oreRank(BlockState state) {
		if (state.is(BlockItemTags.DIAMOND_ORES.block())) {
			return 9;
		}
		if (state.is(BlockItemTags.EMERALD_ORES.block())) {
			return 8;
		}
		if (state.is(BlockTags.GOLD_ORES)) {
			return 7;
		}
		if (state.is(BlockItemTags.LAPIS_ORES.block())) {
			return 6;
		}
		if (state.is(BlockItemTags.REDSTONE_ORES.block())) {
			return 5;
		}
		if (state.is(BlockTags.IRON_ORES)) {
			return 4;
		}
		if (state.is(BlockTags.COPPER_ORES)) {
			return 2;
		}
		return 1;
	}

	public static String oreName(BlockState state) {
		return state.getBlock().getName().getString().toLowerCase(Locale.ROOT);
	}
}
