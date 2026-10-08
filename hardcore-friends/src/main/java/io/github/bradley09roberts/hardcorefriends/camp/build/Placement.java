package io.github.bradley09roberts.hardcorefriends.camp.build;

import net.minecraft.core.BlockPos;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;

/**
 * One blueprint entry resolved to a world position on a reserved site. {@code index} is its place in the build
 * order, which is what {@code CampData.Site.progress} counts.
 */
public record Placement(int index, int part, BlockPos pos, Blueprint.Entry entry, int rotation) {
	public boolean isFoundation() {
		return entry.material() == MaterialSpec.FOUNDATION;
	}
}
