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
    /** Generated quests of the current world (see {@link #installGenerated}); declared first so lookups work during class init. */
    private static final Map<String, QuestDef> DYNAMIC = new LinkedHashMap<>();
    private static final Map<String, List<QuestDef>> DYNAMIC_BY_GIVER = new HashMap<>();
    private static final Map<String, net.minecraft.network.chat.Component> TITLES = new HashMap<>(), DESCS = new HashMap<>();

    private static QuestDef.Objective o(String type, String target, int n) {
        return new QuestDef.Objective(type, target, n);
    }

    private static void q(String id, String giver, String island, List<QuestDef.Objective> obj, int xp, long beli, long bounty,
                          Map<String, Integer> rep, List<Supplier<ItemStack>> items, List<String> req, @Nullable String next, @Nullable String flag, boolean repeat, String arc) {
        ALL.put(id, new QuestDef(id, giver, island, obj, xp, beli, bounty, rep, items, req, next, flag, repeat, arc));
    }

    static {
        // ---------------- Awakening trial (offered by Awakening when fruit mastery and level allow) ----------------
        q("awakening_trial", "any", "", List.of(o("fruit_kill", "any", 12), o("discover", "any_new_world", 1)), 4000, 0, 0, Map.of(), List.of(), List.of(),
                null, Awakening.TRIAL_FLAG, false, "awakening");
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
        q("execution_town", "station_master", "loguetown", List.of(o("discover", "loguetown", 1), o("talk", "bartolo", 1)), 600, 2000, 0, Map.of(), List.of(), List.of(), "reverse_mountain", null, false, "gate");
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
        // ---------------- The Saltfire Saga: island arcs, faction variants, crew quests (docs/STORY.md) ----------------
        StoryQuests.register(ALL);
        StoryQuestsExtra.register(ALL); // Skypiea, Fish-Man Island and a local story on every other canon island
        IslandQuestsExtra.register(ALL); // a local job on each canon island added by CanonIslands.additions
        com.grandline.activity.ActivityQuests.register(ALL); // sea activities: fishing errands, treasure board job
        com.grandline.quest.story.SeaJobs.register(ALL); // board jobs won in sea battles ("sea_battle" objectives)
        com.grandline.quest.arc.Arcs.registerListed(); // the later chapters, for the journal (their quests are registered above)
    }

    // ------------------------------------------------------------------ faction variants & requirements

    private static final Set<String> PATHS = Set.of("pirate", "marine", "revolutionary", "bounty_hunter", "civilian");

    /** Faction of a variant id ("alabasta_2.marine" → "marine"), or null for a base quest. */
    @Nullable
    public static String variantFaction(String id) {
        int dot = id.lastIndexOf('.');
        if (dot < 0) return null;
        String f = id.substring(dot + 1);
        return PATHS.contains(f) ? f : null;
    }

    /** Base id of a quest, without any faction suffix. */
    public static String baseId(String id) {
        return variantFaction(id) == null ? id : id.substring(0, id.lastIndexOf('.'));
    }

    /** The version of a quest this character gets: "<id>.<faction>" when such a variant exists, else the base. */
    public static String resolve(String id, com.grandline.player.CharacterData d) {
        String v = baseId(id) + "." + d.faction.id();
        return get(v) != null ? v : baseId(id);
    }

    /** Whether this quest, or any faction variant of it, has been completed. */
    public static boolean isDone(com.grandline.player.CharacterData d, String id) {
        String base = baseId(id);
        if (d.completedQuests.contains(id) || d.completedQuests.contains(base)) return true;
        for (String c : d.completedQuests) if (c.startsWith(base + ".") && variantFaction(c) != null) return true;
        return false;
    }

    /**
     * Requirements: every listed quest done (any variant counts), a variant only for its own faction, and the
     * base version hidden from factions that have their own variant.
     */
    public static boolean requirementsMet(com.grandline.player.CharacterData d, QuestDef q) {
        String vf = variantFaction(q.id());
        if (vf != null && !vf.equals(d.faction.id())) return false;
        if (vf == null && get(q.id() + "." + d.faction.id()) != null) return false;
        for (String r : q.requires()) if (!isDone(d, r)) return false;
        return true;
    }

    /** Ids of the Saltfire Saga quests (StoryQuests), in registration order. */
    public static Set<String> storyQuestIds() {
        return Collections.unmodifiableSet(StoryQuests.IDS);
    }

    /** Whether a giver may offer this quest to the character right now. */
    public static boolean canOffer(com.grandline.player.CharacterData d, QuestDef q) {
        if (d.questProgress.containsKey(q.id())) return false;
        if (!q.repeatable() && isDone(d, q.id())) return false;
        return requirementsMet(d, q) && com.grandline.quest.arc.Arcs.inOrder(d, q); // story chapters are offered in order
    }

    @Nullable
    public static QuestDef get(String id) {
        QuestDef q = ALL.get(id);
        return q != null ? q : DYNAMIC.get(id);
    }

    /** Every quest: the hand-written ones, then the ones generated for the current world (island stories, jobs). */
    public static Collection<QuestDef> all() {
        if (DYNAMIC.isEmpty()) return ALL.values();
        List<QuestDef> l = new ArrayList<>(ALL.size() + DYNAMIC.size());
        l.addAll(ALL.values());
        l.addAll(DYNAMIC.values());
        return Collections.unmodifiableList(l);
    }

    /** Quests a giver role can offer on an island to this character. */
    public static List<QuestDef> offeredBy(String role, String islandId, boolean starterIsland) {
        List<QuestDef> out = new ArrayList<>();
        for (QuestDef q : ALL.values()) {
            if (!q.giverRole().equals(role)) continue;
            if (q.island().equals("*") || q.island().equals(islandId) || (q.island().equals("*starter") && starterIsland)) out.add(q);
        }
        for (QuestDef q : DYNAMIC_BY_GIVER.getOrDefault(role, List.of()))
            if (q.island().equals("*") || q.island().equals(islandId)) out.add(q);
        return out;
    }

    // ------------------------------------------------------------------ generated quests (per world)

    /**
     * Quests generated from the world seed (island stories, job-board jobs: quest/story). They are installed when a
     * world starts and dropped when it stops, never saved: the same seed always regenerates the same quests. Their
     * titles and descriptions are components with arguments (generated names and places) instead of fixed keys.
     */

    /** Replace the generated quests (and their texts) with these. */
    public static void installGenerated(Collection<QuestDef> quests, Map<String, net.minecraft.network.chat.Component> titles,
                                        Map<String, net.minecraft.network.chat.Component> descs) {
        clearGenerated();
        for (QuestDef q : quests) {
            if (ALL.containsKey(q.id()) || DYNAMIC.put(q.id(), q) != null) throw new IllegalStateException("duplicate quest " + q.id());
            DYNAMIC_BY_GIVER.computeIfAbsent(q.giverRole(), k -> new ArrayList<>()).add(q);
        }
        TITLES.putAll(titles);
        DESCS.putAll(descs);
    }

    public static void clearGenerated() {
        DYNAMIC.clear();
        DYNAMIC_BY_GIVER.clear();
        TITLES.clear();
        DESCS.clear();
    }

    /** Whether this quest was generated for the current world. */
    public static boolean isGenerated(String id) {
        return DYNAMIC.containsKey(id);
    }

    /** Number of generated quests installed. */
    public static int generatedCount() {
        return DYNAMIC.size();
    }

    /** Display title of a quest (generated quests carry their own text with names and places). */
    public static net.minecraft.network.chat.Component title(QuestDef q) {
        net.minecraft.network.chat.Component t = TITLES.get(q.id());
        return t != null ? t : net.minecraft.network.chat.Component.translatable(q.titleKey());
    }

    /** Description of a generated quest, or null for the hand-written ones. */
    @Nullable
    public static net.minecraft.network.chat.Component generatedDescription(QuestDef q) {
        return DESCS.get(q.id());
    }

    private QuestRegistry() {}
}
