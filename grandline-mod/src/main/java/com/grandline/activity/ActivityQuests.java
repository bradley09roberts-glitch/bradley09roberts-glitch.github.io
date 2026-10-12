package com.grandline.activity;

import com.grandline.quest.QuestDef;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Quests of the sea activities (registered into QuestRegistry), and the reward hooks other quests can use:
 * {@link #treasureMap()} (a blank map, drawn for the player when it lands in their inventory) and {@link #dish}.
 * Objective types: {@code fish} (target: the catch's item id, or "any") counted by {@link SeaFishing#onCatch}, and
 * {@code treasure} ("any") counted when a map's secret is found ({@link TreasureMaps#opened}).
 */
public final class ActivityQuests {
    /** A blank treasure map as a quest reward. */
    public static Supplier<ItemStack> treasureMap() {
        return () -> new ItemStack(ActivityItems.TREASURE_MAP.get());
    }

    public static Supplier<ItemStack> dish(String id, int n) {
        return () -> new ItemStack(ActivityItems.DISHES.stream().filter(d -> d.getId().getPath().equals(id)).findFirst().orElseThrow().get(), n);
    }

    public static void register(Map<String, QuestDef> all) {
        // a barkeep's rumour: a few sea fish for the kitchen, and a map changes hands
        q(all, "tavern_rumour", "bartender", List.of(o("fish", "any", 3)), 200, 300, List.of(treasureMap()), true, "activities");
        // a cook wants the freshest catch and pays in signature dishes
        q(all, "cooks_catch", "cook", List.of(o("fish", "any", 5)), 300, 600, List.of(dish("saltfire_curry", 2)), true, "activities");
        q(all, "chefs_giant", "head_chef", List.of(o("fish", "behemoth_carp", 1)), 1200, 3000, List.of(dish("pirates_feast", 2)), true, "activities");
        // board job: follow any treasure map to its X
        q(all, "board_treasure", "bounty_board", List.of(o("treasure", "any", 1)), 500, 2500, List.of(treasureMap()), true, "board");
    }

    private static QuestDef.Objective o(String type, String target, int n) {
        return new QuestDef.Objective(type, target, n);
    }

    private static void q(Map<String, QuestDef> all, String id, String giver, List<QuestDef.Objective> obj, int xp, long beli,
                          List<Supplier<ItemStack>> items, boolean repeat, String arc) {
        all.put(id, new QuestDef(id, giver, "*", obj, xp, beli, 0, Map.of(), items, List.of(), null, null, repeat, arc));
    }

    private ActivityQuests() {}
}
