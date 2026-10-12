package com.grandline.quest;

import com.grandline.item.DevilFruitItem;
import com.grandline.registry.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * Quest definitions. The original main story ("The Saltfire Saga") is documented in docs/STORY.md; this file holds
 * the playable quests implemented so far. Text lives in the language file under quest.grandline.<id>.
 */
public final class QuestRegistry {
    private static final Map<String, QuestDef> ALL = new LinkedHashMap<>();

    private static QuestDef.Objective o(String type, String target, int n) {
        return new QuestDef.Objective(type, target, n);
    }

    private static void q(String id, String giver, String island, List<QuestDef.Objective> obj, int xp, long beli, long bounty,
                          Map<String, Integer> rep, List<Supplier<ItemStack>> items, List<String> req, @Nullable String next, @Nullable String flag, boolean repeat, String arc) {
        ALL.put(id, new QuestDef(id, giver, island, obj, xp, beli, bounty, rep, items, req, next, flag, repeat, arc));
    }

    static {
        // ---------------- Arc 1: "Embers of the Blue" (every starter island) ----------------
        q("first_steps", "elder", "*starter", List.of(o("talk", "elder", 1)), 60, 100, 0, Map.of(), List.of(() -> new ItemStack(ModItems.LOG_POSE.get()), () -> new ItemStack(Items.BREAD, 6)),
                List.of(), "bandit_trouble", null, false, "embers");
        q("bandit_trouble", "elder", "*starter", List.of(o("kill", "bandit", 4)), 180, 400, 0, Map.of("civilian", 20), List.of(() -> new ItemStack(ModItems.MEAT_ON_BONE.get(), 4)),
                List.of("first_steps"), "shipwrights_favor", null, false, "embers");
        q("shipwrights_favor", "shipwright", "*starter", List.of(o("talk", "shipwright", 1), o("ship", "any", 1)), 200, 0, 0, Map.of(), List.of(),
                List.of("bandit_trouble"), "first_voyage", null, false, "embers");
        q("first_voyage", "shipwright", "*starter", List.of(o("discover", "any_canon", 1)), 300, 600, 0, Map.of(), List.of(() -> new ItemStack(ModItems.SEA_CHART.get())),
                List.of("shipwrights_favor"), "path_choice", null, false, "embers");
        q("path_choice", "elder", "*", List.of(o("talk", "path_contact", 1)), 150, 0, 0, Map.of(), List.of(), List.of("first_voyage"), null, "path_chosen", false, "embers");
        // ---------------- Arc 2: "The Gate of Reverse Mountain" ----------------
        q("execution_town", "station_master", "loguetown", List.of(o("discover", "loguetown", 1), o("talk", "legend", 1)), 600, 2000, 0, Map.of(), List.of(), List.of(), "reverse_mountain", null, false, "gate");
        q("reverse_mountain", "lighthouse_keeper", "twin_capes", List.of(o("discover", "twin_capes", 1)), 1200, 3000, 0, Map.of(), List.of(() -> new ItemStack(ModItems.LOG_POSE.get())), List.of(), "whisky_welcome", null, false, "gate");
        q("whisky_welcome", "bartender", "whisky_peak", List.of(o("discover", "whisky_peak", 1), o("kill", "bounty_hunter_ambusher", 0), o("kill", "pirate", 6)), 1500, 5000, 2_000_000, Map.of("bounty_hunter", -50), List.of(), List.of(), null, null, false, "gate");
        // ---------------- Arc 1 side: Arlong Park liberation (East Blue) ----------------
        q("fishman_occupation", "resident", "cocoyasi", List.of(o("kill", "fishman_guard", 4), o("kill", "fishman_brute", 2), o("kill", "fishman_boss", 1)), 2500, 8000, 8_000_000,
                Map.of("island/cocoyasi", 400, "civilian", 60), List.of(() -> new ItemStack(ModItems.ETERNAL_POSE.get())), List.of(), null, "liberated_cocoyasi", false, "embers");
        // ---------------- Path quests ----------------
        q("path_pirate", "path_contact", "*", List.of(o("kill", "marine", 3)), 500, 1000, 0, Map.of("pirate", 50), List.of(), List.of(), null, "jolly_roger", false, "paths");
        q("path_marine", "marine_officer", "*", List.of(o("kill", "pirate", 5), o("kill", "bandit", 5)), 500, 3000, 0, Map.of("marine", 120), List.of(() -> new ItemStack(ModItems.SEASTONE_CUFFS.get(), 2)), List.of(), null, "enlisted", false, "paths");
        q("path_revolutionary", "revolutionary", "*", List.of(o("kill", "cp_agent", 1), o("discover", "any_canon", 2)), 700, 1500, 1_500_000, Map.of("revolutionary", 150, "marine", -60), List.of(), List.of(), null, "revolution_contact", false, "paths");
        q("path_bounty_hunter", "bartender", "*", List.of(o("arrest", "pirate", 2)), 600, 4000, 0, Map.of("bounty_hunter", 120), List.of(() -> new ItemStack(ModItems.SEASTONE_CUFFS.get(), 4)), List.of(), null, "licensed_hunter", false, "paths");
        // ---------------- Repeatable board jobs ----------------
        q("board_bandits", "bounty_board", "*", List.of(o("kill", "bandit", 5)), 150, 800, 0, Map.of("civilian", 5), List.of(), List.of(), null, null, true, "board");
        q("board_pirates", "bounty_board", "*", List.of(o("kill", "pirate", 4)), 250, 1500, 0, Map.of("marine", 10), List.of(), List.of(), null, null, true, "board");
        q("board_marines", "bounty_board", "*", List.of(o("kill", "marine", 4)), 300, 1200, 3_000_000, Map.of("pirate", 10), List.of(), List.of(), null, null, true, "board");
        q("board_explore", "bounty_board", "*", List.of(o("discover", "any", 2)), 400, 1000, 0, Map.of(), List.of(), List.of(), null, null, true, "board");
        q("board_ruins", "bounty_board", "*", List.of(o("poneglyph", "any", 1)), 600, 2000, 0, Map.of(), List.of(), List.of(), null, null, true, "board");
    }

    @Nullable
    public static QuestDef get(String id) {
        return ALL.get(id);
    }

    public static Collection<QuestDef> all() {
        return ALL.values();
    }

    /** Quests a giver role can offer on an island to this character. */
    public static List<QuestDef> offeredBy(String role, String islandId, boolean starterIsland) {
        List<QuestDef> out = new ArrayList<>();
        for (QuestDef q : ALL.values()) {
            if (!q.giverRole().equals(role)) continue;
            if (q.island().equals("*") || q.island().equals(islandId) || (q.island().equals("*starter") && starterIsland)) out.add(q);
        }
        return out;
    }

    private QuestRegistry() {}
}
