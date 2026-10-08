package io.github.bradley09roberts.hardcorefriends.camp.build;

import net.minecraft.core.BlockPos;

/** One copy of a blueprint on the ground: where its local (0, 0, 0) is and how it is turned (0–3). */
public record Part(BlockPos origin, int rotation) {
}
