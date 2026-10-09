package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.WoodWork;
import io.github.bradley09roberts.hardcorefriends.civic.BlueprintLibrary;

/**
 * Choosing a look that suits a place: the styles a spot's biome calls for (snowy spruce in the taiga, sandstone in
 * the desert, birch in a birch forest...), the wood the camp actually has in its supply chest, and from those the best
 * plan of a kind for a site. Plans whose wood or stone the camp has plenty of win over plans it would have to go out
 * and gather for, so a camp of oak builds oak until it has spruce.
 */
public final class Styles {
	private Styles() {
	}

	/**
	 * The styles that suit this spot, most fitting first: a biome word ({@code snowy}, {@code taiga}, {@code desert},
	 * {@code plains}, {@code forest}...) and the wood or stone that goes with it ({@code spruce}, {@code sandstone}...).
	 */
	public static List<String> stylesAt(ServerLevel level, BlockPos pos) {
		Set<String> styles = new LinkedHashSet<>();
		Holder<Biome> biome = level.getBiome(pos);
		boolean snowy = biome.value().coldEnoughToSnow(pos, level.getSeaLevel()) || biome.is(BiomeTags.HAS_VILLAGE_SNOWY)
			|| biome.is(BiomeTags.HAS_IGLOO);
		if (snowy) {
			styles.add("snowy");
		}
		if (biome.is(BiomeTags.HAS_VILLAGE_DESERT) || biome.is(BiomeTags.HAS_DESERT_PYRAMID)) {
			styles.add("desert");
			styles.add("sandstone");
		} else if (biome.is(BiomeTags.IS_BADLANDS)) {
			styles.add("desert");
			styles.add("sandstone");
		} else if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.HAS_VILLAGE_TAIGA) || snowy) {
			styles.add("taiga");
			styles.add("spruce");
		} else if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
			styles.add("forest");
			styles.add("birch");
		} else if (biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN)) {
			styles.add("forest");
			styles.add("dark_oak");
		} else if (biome.is(BiomeTags.IS_SAVANNA) || biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
			styles.add("savanna");
			styles.add("acacia");
		} else if (biome.is(BiomeTags.IS_JUNGLE)) {
			styles.add("jungle");
		} else if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
			styles.add("mountain");
			styles.add("stone");
		} else if (biome.is(BiomeTags.IS_FOREST)) {
			styles.add("forest");
			styles.add("oak");
		} else {
			styles.add("plains");
			styles.add("oak");
		}
		return List.copyOf(styles);
	}

	/**
	 * Wood in the supply chest, counted in planks (a log is four), by wood name ("oak" → 120). Empty without a chest.
	 */
	public static Map<String, Integer> woodInStore(ServerLevel level) {
		Map<String, Integer> wood = new HashMap<>();
		Optional<Container> chest = SupplyChest.of(level);
		if (chest.isEmpty()) {
			return wood;
		}
		Container c = chest.get();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			int planks = s.is(ItemTags.LOGS) ? s.getCount() * 4 : s.is(ItemTags.PLANKS) ? s.getCount() : 0;
			String w = planks > 0 ? WoodWork.woodOf(s) : null;
			if (w != null) {
				wood.merge(w, planks, Integer::sum);
			}
		}
		return wood;
	}

	/** Stone-like materials in the supply chest: "stone" (cobblestone and stone), "sandstone" (sand and sandstone). */
	private static Map<String, Integer> stoneInStore(ServerLevel level) {
		Map<String, Integer> stone = new HashMap<>();
		SupplyChest.of(level).ifPresent(c -> {
			stone.put("stone", SupplyChest.count(c, s -> s.is(Items.COBBLESTONE) || s.is(Items.STONE) || s.is(Items.STONE_BRICKS)));
			stone.put("sandstone", SupplyChest.count(c, s -> s.is(Items.SAND) || s.is(Items.SANDSTONE)));
		});
		return stone;
	}

	/**
	 * The best plan of this kind for a site here: plans whose styles match the spot score most, then plans whose wood or
	 * stone the camp has plenty of in store; the filter (enough beds, say) must pass. Ties go to the plan whose id sorts
	 * first, so the choice is the same every time for the same camp. Empty if no plan of the kind passes.
	 */
	public static Optional<Blueprint> pick(ServerLevel level, BlockPos pos, String kind, Predicate<Blueprint> filter) {
		return pick(level, pos, BlueprintLibrary.get().byKind(kind), filter);
	}

	/** {@link #pick(ServerLevel, BlockPos, String, Predicate)} from a given list of plans. */
	public static Optional<Blueprint> pick(ServerLevel level, BlockPos pos, List<Blueprint> plans, Predicate<Blueprint> filter) {
		List<String> here = stylesAt(level, pos);
		Map<String, Integer> wood = woodInStore(level);
		Map<String, Integer> stone = stoneInStore(level);
		int totalWood = Math.max(1, wood.values().stream().mapToInt(Integer::intValue).sum());
		Blueprint best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		List<Blueprint> sorted = new ArrayList<>(plans);
		sorted.sort((a, b) -> a.id().compareTo(b.id()));
		for (Blueprint plan : sorted) {
			if (!filter.test(plan)) {
				continue;
			}
			double score = 0;
			for (int i = 0; i < here.size(); i++) {
				if (plan.styles().contains(here.get(i))) {
					score += 10 - i * 2;
				}
			}
			String w = plan.wood();
			if (w != null) {
				score += 8.0 * wood.getOrDefault(w, 0) / totalWood;
				if (wood.getOrDefault(w, 0) == 0) {
					score -= 3; // the camp would have to go and find this wood first
				}
			}
			for (String s : List.of("stone", "sandstone")) {
				if (plan.styles().contains(s)) {
					score += stone.getOrDefault(s, 0) >= 32 ? 3 : -3;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				best = plan;
			}
		}
		return Optional.ofNullable(best);
	}

	/** The wood a camp building here should prefer: the plan's own, else the biome's, else the wood in store most. */
	public static @Nullable String woodFor(ServerLevel level, BlockPos pos, Blueprint plan) {
		if (plan.wood() != null) {
			return plan.wood();
		}
		Map<String, Integer> wood = woodInStore(level);
		for (String s : stylesAt(level, pos)) {
			if (wood.getOrDefault(s, 0) >= 16) {
				return s;
			}
		}
		return wood.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
	}
}
