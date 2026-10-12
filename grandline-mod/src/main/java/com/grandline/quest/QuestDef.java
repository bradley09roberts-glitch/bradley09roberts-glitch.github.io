package com.grandline.quest;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** A quest: objectives of (type, target, count); rewards; chaining via {@code next}. */
public record QuestDef(String id, String giverRole, String island, List<Objective> objectives, int xp, long beli, long bounty,
                       Map<String, Integer> reputation, List<Supplier<ItemStack>> rewardItems, List<String> requires,
                       @Nullable String next, @Nullable String flag, boolean repeatable, String arc) {
    public record Objective(String type, String target, int count) {}

    public String titleKey() {
        return "quest.grandline." + id;
    }

    public String descKey() {
        return "quest.grandline." + id + ".desc";
    }
}
