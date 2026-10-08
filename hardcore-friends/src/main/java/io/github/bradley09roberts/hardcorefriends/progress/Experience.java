package io.github.bradley09roberts.hardcorefriends.progress;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The camp's shared experience. Friends have no experience levels of their own, so the camp keeps one pool that
 * works exactly like a player's: it grows by vanilla amounts when a friend mines an ore that gives experience, smelts
 * in the camp furnace or defeats a monster, it has levels by the player's formula, and enchanting or repairing spends
 * levels from it as it would from a player. So a level-30 enchant needs the pool at level 30 and costs 3 levels.
 */
public final class Experience {
	/** A cap far beyond any use (about level 150), so the number can never overflow. */
	public static final int MAX_POINTS = 1_000_000;
	private static final int MAX_LEVEL = 200;

	private Experience() {
	}

	// ---------------------------------------------------------------- levels

	/** Points needed to go from {@code level} to the next, by the player's formula. */
	public static int neededForNext(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
	}

	/** Total points a player has at the very start of {@code level}. */
	public static int pointsAt(int level) {
		int total = 0;
		for (int l = 0; l < level && l < MAX_LEVEL; l++) {
			total += neededForNext(l);
		}
		return total;
	}

	/** The level a pool of this many points reaches. */
	public static int levelOf(int points) {
		int level = 0;
		int left = points;
		while (level < MAX_LEVEL && left >= neededForNext(level)) {
			left -= neededForNext(level);
			level++;
		}
		return level;
	}

	public static int points(MinecraftServer server) {
		return ProgressData.get(server).experience();
	}

	public static int level(MinecraftServer server) {
		return levelOf(points(server));
	}

	/** Adds points to the camp's pool. */
	public static void add(MinecraftServer server, int points) {
		if (points > 0) {
			ProgressData data = ProgressData.get(server);
			data.setExperience(data.experience() + points);
		}
	}

	/**
	 * Spends whole levels, as an enchant or a repair does for a player: the pool drops that many levels and keeps its
	 * progress into the level it lands on. Returns false (spending nothing) if the pool is below that many levels.
	 */
	public static boolean spendLevels(MinecraftServer server, int levels) {
		ProgressData data = ProgressData.get(server);
		int points = data.experience();
		int level = levelOf(points);
		if (levels <= 0 || level < levels) {
			return levels <= 0;
		}
		double progress = (points - pointsAt(level)) / (double) neededForNext(level);
		int newLevel = level - levels;
		data.setExperience(pointsAt(newLevel) + (int) Math.floor(progress * neededForNext(newLevel)));
		return true;
	}

	// --------------------------------------------------------------- sources

	/** A friend broke a block: an ore that gives experience to a player gives the same to the camp. */
	public static void onEdit(WorldEditGuard.EditEvent event) {
		if (!"broke".equals(event.verb()) || !(event.companion().level() instanceof ServerLevel level)) {
			return;
		}
		int xp = oreExperience(event.state(), level.getRandom());
		if (xp > 0) {
			xp = EnchantmentHelper.processBlockExperience(level, event.companion().getMainHandItem(), xp);
			add(level.getServer(), xp);
		}
	}

	/** The experience a player gets for breaking this block (the vanilla ranges), 0 for most blocks. */
	static int oreExperience(BlockState state, RandomSource random) {
		if (state.is(BlockItemTags.COAL_ORES.block())) {
			return random.nextIntBetweenInclusive(0, 2);
		}
		if (state.is(BlockItemTags.DIAMOND_ORES.block()) || state.is(BlockItemTags.EMERALD_ORES.block())) {
			return random.nextIntBetweenInclusive(3, 7);
		}
		if (state.is(BlockItemTags.LAPIS_ORES.block()) || state.is(Blocks.NETHER_QUARTZ_ORE)) {
			return random.nextIntBetweenInclusive(2, 5);
		}
		if (state.is(BlockItemTags.REDSTONE_ORES.block())) {
			return random.nextIntBetweenInclusive(1, 5);
		}
		if (state.is(Blocks.NETHER_GOLD_ORE)) {
			return random.nextIntBetweenInclusive(0, 1);
		}
		return 0;
	}

	/** A friend's blow defeated a monster: the experience it would drop for a player goes to the camp. */
	public static void onHit(CompanionEntity c, ServerLevel level, Entity target, boolean killed) {
		if (killed && target instanceof Enemy && target instanceof LivingEntity living) {
			add(level.getServer(), living.getExperienceReward(level, c));
		}
	}

	/**
	 * A friend took smelted items out of the camp's own furnace: the experience the recipe gives a player (0.7 an
	 * iron or copper ingot, 1 a gold ingot, 0.35 cooked food, 0.1 glass), with the fraction as a chance, as in vanilla.
	 */
	public static void onSmelted(ServerLevel level, ItemStack out, int count) {
		if (count <= 0 || out.isEmpty()) {
			return;
		}
		double each;
		if (out.is(Items.GOLD_INGOT)) {
			each = 1.0;
		} else if (out.is(Items.IRON_INGOT) || out.is(Items.COPPER_INGOT)) {
			each = 0.7;
		} else if (out.has(net.minecraft.core.component.DataComponents.FOOD)) {
			each = 0.35;
		} else if (out.is(ItemTags.COALS)) {
			each = 0.15;
		} else {
			each = 0.1;
		}
		double total = each * count;
		int whole = (int) Math.floor(total);
		if (level.getRandom().nextDouble() < total - whole) {
			whole++;
		}
		add(level.getServer(), whole);
	}
}
