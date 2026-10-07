package io.github.bradley09roberts.hardcorefriends.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import io.github.bradley09roberts.hardcorefriends.HardcoreFriends;

/** Data-driven lists that decide what friends may eat, recruit with, break and treat as player-built. */
public final class ModTags {
	/** Common foods accepted for recruitment (two items per friend, any mix). */
	public static final TagKey<Item> RECRUIT_FOOD = item("recruit_food");
	/** Foods friends eat to heal and share with players. */
	public static final TagKey<Item> COMPANION_FOOD = item("companion_food");

	/** Never touched by any friend for any reason. */
	public static final TagKey<Block> NEVER_TOUCH = block("never_touch");
	/** Blocks that suggest something was built by a player. Friends never break these and build away from them. */
	public static final TagKey<Block> BUILD_MARKERS = block("build_markers");
	/** Natural underground blocks a miner may dig. */
	public static final TagKey<Block> MINEABLE_NATURAL = block("mineable_natural");
	/** Natural surface blocks a forager may quarry for building material. */
	public static final TagKey<Block> EARTH_GATHERABLE = block("earth_gatherable");
	/** Ores a miner looks for. */
	public static final TagKey<Block> WANTED_ORES = block("wanted_ores");

	private ModTags() {
	}

	private static TagKey<Item> item(String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, path));
	}

	private static TagKey<Block> block(String path) {
		return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(HardcoreFriends.MOD_ID, path));
	}
}
