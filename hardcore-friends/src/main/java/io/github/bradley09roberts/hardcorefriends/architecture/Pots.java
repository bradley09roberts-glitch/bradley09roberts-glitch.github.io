package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which potted block a plant makes: a poppy in a pot is {@code potted_poppy}. Vanilla keeps that map private, so it is
 * rebuilt once from every flower-pot block's contents.
 */
public final class Pots {
	private static @Nullable Map<Block, Block> potted;

	private Pots() {
	}

	/** The potted block for this plant item, or null if it does not go in a pot. */
	public static @Nullable BlockState pottedFor(ItemStack plant) {
		Block content = Block.byItem(plant.getItem());
		if (content == Blocks.AIR) {
			return null;
		}
		Block pot = map().get(content);
		return pot == null ? null : pot.defaultBlockState();
	}

	private static synchronized Map<Block, Block> map() {
		Map<Block, Block> m = potted;
		if (m == null) {
			m = new HashMap<>();
			for (Block b : BuiltInRegistries.BLOCK) {
				if (b instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) {
					m.put(pot.getPotted(), pot);
				}
			}
			potted = m;
		}
		return m;
	}
}
