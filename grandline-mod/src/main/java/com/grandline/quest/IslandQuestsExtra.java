package com.grandline.quest;

import com.grandline.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A local job on each canon island added by CanonIslands#additions, offered by an island role that the island's set
 * piece (LandmarkBuildersExtra) or town puts there. Places only: no canon characters. Text: lang/en_us/islands_extra.json.
 */
final class IslandQuestsExtra {
    private static final String ARC = "local";

    static void register(Map<String, QuestDef> all) {
        Supplier<ItemStack> chart = () -> new ItemStack(ModItems.SEA_CHART.get());
        Supplier<ItemStack> logPose = () -> new ItemStack(ModItems.LOG_POSE.get());
        Supplier<ItemStack> meat = () -> new ItemStack(ModItems.MEAT_ON_BONE.get(), 4);
        // ---- the Blues ----
        q(all, "tequila_wolf_1", "villager", "tequila_wolf", List.of(o("kill", "jailer", 2)), 600, 900, 0, Map.of("island/tequila_wolf", 80, "revolutionary", 40), List.of(meat));
        q(all, "germa_kingdom_1", "noble", "germa_kingdom", List.of(o("kill", "pirate", 4)), 700, 1500, 0, Map.of("island/germa_kingdom", 80), List.of(chart));
        q(all, "rommel_kingdom_1", "blacksmith", "rommel_kingdom", List.of(o("collect", "minecraft:iron_ingot", 6), o("kill", "bandit", 3)), 600, 1000, 0,
                Map.of("island/rommel_kingdom", 80), List.of(() -> new ItemStack(Items.SHIELD)));
        q(all, "oykot_kingdom_1", "scholar", "oykot_kingdom", List.of(o("collect", "minecraft:book", 2), o("kill", "bandit", 4)), 600, 1000, 0,
                Map.of("island/oykot_kingdom", 80), List.of(chart));
        q(all, "god_valley_1", "scholar", "god_valley", List.of(o("kill", "ruin_guardian", 2)), 900, 1500, 0, Map.of("civilian", 30), List.of(chart));
        // ---- Paradise ----
        q(all, "kyuka_island_1", "innkeeper", "kyuka_island", List.of(o("kill", "pirate", 5)), 3500, 6000, 0, Map.of("island/kyuka_island", 100), List.of(meat));
        // ---- the New World ----
        q(all, "lulusia_kingdom_1", "keeper", "lulusia_kingdom", List.of(o("kill", "pirate", 6)), 25000, 25000, 0, Map.of("civilian", 60), List.of(logPose));
        q(all, "prodence_kingdom_1", "master", "prodence_kingdom", List.of(o("kill", "pirate", 8)), 28000, 30000, 0, Map.of("island/prodence_kingdom", 150), List.of(meat));
        q(all, "karai_bari_island_1", "bartender", "karai_bari_island", List.of(o("kill", "marine", 3)), 30000, 35000, 5_000_000, Map.of("pirate", 60, "marine", -40), List.of());
        q(all, "mogaro_kingdom_1", "noble", "mogaro_kingdom", List.of(o("collect", "minecraft:gold_ingot", 4), o("kill", "pirate", 4)), 28000, 32000, 0,
                Map.of("island/mogaro_kingdom", 150), List.of(logPose));
    }

    private static QuestDef.Objective o(String type, String target, int n) {
        return new QuestDef.Objective(type, target, n);
    }

    private static void q(Map<String, QuestDef> all, String id, String giver, String island, List<QuestDef.Objective> obj, int xp, long beli, long bounty,
                          Map<String, Integer> rep, List<Supplier<ItemStack>> items) {
        if (all.put(id, new QuestDef(id, giver, island, obj, xp, beli, bounty, rep, items, List.of(), null, null, false, ARC)) != null)
            throw new IllegalStateException("duplicate quest " + id);
    }

    private IslandQuestsExtra() {}
}
