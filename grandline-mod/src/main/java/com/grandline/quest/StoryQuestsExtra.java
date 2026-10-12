package com.grandline.quest;

import com.grandline.GrandLine;
import com.grandline.registry.ModDataComponents;
import com.grandline.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Supplier;

/**
 * More of "The Saltfire Saga" (docs/STORY.md): the Skypiea arc in the White Sea (after Jaya), the Fish-Man Island arc
 * in the deep sea (between Sabaody and the New World), and a local story on every canon island that had none.
 * Same conventions as {@link StoryQuests}: faction variants are "<id>.<faction>"; givers are notables
 * ({@code notable:<id>}) or, rarely, an island role. Objective types: kill, defeat, talk, discover, poneglyph,
 * arrest (role, faction or notable) and collect (item id or #tag, counted from the inventory and handed over on
 * completion, see {@link QuestCollect}). Text: lang/en_us/story_extra.json.
 */
public final class StoryQuestsExtra {
    /** Quest ids registered here (also part of {@link StoryQuests#IDS}). */
    static final Set<String> IDS = new LinkedHashSet<>();

    /** Ids of the quests added by this file (self-test: coverage before/after). */
    public static Set<String> ids() {
        return Collections.unmodifiableSet(IDS);
    }
    private static Map<String, QuestDef> out;

    static void register(Map<String, QuestDef> all) {
        out = all;
        skypiea();
        fishmanIsland();
        localStories();
        out = null;
    }

    // ================================================================== builder

    private static final class Q {
        final String id, giver, island, arc;
        final List<QuestDef.Objective> obj = new ArrayList<>();
        int xp;
        long beli, bounty;
        final Map<String, Integer> rep = new LinkedHashMap<>();
        final List<Supplier<ItemStack>> items = new ArrayList<>();
        final List<String> req = new ArrayList<>();
        String next, flag;

        Q(String id, String giver, String island, String arc) {
            this.id = id;
            this.giver = giver;
            this.island = island;
            this.arc = arc;
        }

        Q o(String type, String target, int n) { obj.add(new QuestDef.Objective(type, target, n)); return this; }
        Q kill(String role, int n) { return o("kill", role, n); }
        Q defeat(String notable) { return o("defeat", notable, 1); }
        Q arrest(String who) { return o("arrest", who, 1); }
        Q arrest(String who, int n) { return o("arrest", who, n); }
        Q talk(String who) { return o("talk", who, 1); }
        Q talk(String who, int n) { return o("talk", who, n); }
        Q discover(String island) { return o("discover", island, 1); }
        Q poneglyph(int n) { return o("poneglyph", "any", n); }
        Q collect(String item, int n) { return o("collect", item, n); }
        Q reward(int xp, long beli) { this.xp = xp; this.beli = beli; return this; }
        Q bounty(long b) { bounty = b; return this; }
        Q rep(String key, int v) { rep.put(key, v); return this; }
        Q item(Supplier<ItemStack> s) { items.add(s); return this; }
        Q req(String... ids) { req.addAll(List.of(ids)); return this; }
        Q next(String id) { next = id; return this; }
        Q flag(String f) { flag = f; return this; }

        void add() {
            if (out.put(id, new QuestDef(id, giver, island, List.copyOf(obj), xp, beli, bounty, Map.copyOf(rep), List.copyOf(items), List.copyOf(req), next, flag, false, arc)) != null)
                throw new IllegalStateException("duplicate quest " + id);
            IDS.add(id);
            StoryQuests.IDS.add(id);
        }
    }

    private static Q q(String id, String notableGiver, String island, String arc) {
        return new Q(id, "notable:" + notableGiver, island, arc);
    }

    private static Q qRole(String id, String role, String island, String arc) {
        return new Q(id, role, island, arc);
    }

    // ---- reward items ----
    private static Supplier<ItemStack> item(String id, int n) {
        return () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(GrandLine.MODID, id)), n);
    }

    private static Supplier<ItemStack> vanilla(String id, int n) {
        return () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(id)), n);
    }

    private static Supplier<ItemStack> scroll(String style) {
        return () -> {
            ItemStack s = new ItemStack(ModItems.STYLE_SCROLL.get());
            s.set(ModDataComponents.STYLE.get(), style);
            return s;
        };
    }

    private static Supplier<ItemStack> pose(String island) {
        return () -> {
            ItemStack s = new ItemStack(ModItems.ETERNAL_POSE.get());
            s.set(ModDataComponents.POSE_TARGET.get(), island);
            return s;
        };
    }

    private static final Supplier<ItemStack> LOG_POSE = () -> new ItemStack(ModItems.LOG_POSE.get());
    private static final Supplier<ItemStack> SEA_CHART = () -> new ItemStack(ModItems.SEA_CHART.get());

    private static Supplier<ItemStack> meat(int n) {
        return () -> new ItemStack(ModItems.MEAT_ON_BONE.get(), n);
    }

    private static Supplier<ItemStack> steak(int n) {
        return () -> new ItemStack(ModItems.SEA_KING_STEAK.get(), n);
    }

    // ================================================================== Part II — Skypiea, "The Storm Throne" (after Jaya)

    private static void skypiea() {
        String A = "skypiea";
        // Heaven's Gate and Angel Island
        q("sky_1", "amarante", "heavens_gate", A).discover("heavens_gate").discover("angel_island").reward(4000, 5000)
                .rep("island/angel_island", 50).req("jaya_2").next("sky_2").add();
        q("sky_2", "ferris", "angel_island", A).talk("lumi").talk("priest").discover("upper_yard").reward(4500, 6000)
                .req("sky_1").next("sky_3").add();
        q("sky_2.civilian", "ferris", "angel_island", A).talk("lumi").talk("priest").talk("resident", 3).discover("upper_yard").reward(4500, 7000)
                .rep("civilian", 100).req("sky_1").next("sky_3").add();
        // Upper Yard: the four trials
        q("sky_3", "wakiza", "upper_yard", A).kill("ruin_guardian", 3).defeat("ilse").defeat("garamond").reward(6000, 9000)
                .rep("island/upper_yard", 100).req("sky_2").next("sky_4").add();
        q("sky_3.marine", "wakiza", "upper_yard", A).kill("ruin_guardian", 3).arrest("ilse").arrest("garamond").reward(6000, 12000)
                .rep("island/upper_yard", 100).rep("marine", 100).req("sky_2").next("sky_4").add();
        q("sky_4", "wakiza", "upper_yard", A).defeat("lulla").defeat("oswin").reward(7000, 10000).rep("island/upper_yard", 100)
                .req("sky_3").next("sky_5").add();
        // Weatheria
        q("sky_5", "isidore", "weatheria", A).discover("weatheria").talk("pinwheel").collect("minecraft:gold_ingot", 4).reward(6000, 8000)
                .item(item("weather_staff", 1)).req("sky_4").next("sky_6").add();
        // the false god
        q("sky_6", "wakiza", "upper_yard", A).defeat("cirrus").reward(10000, 20000).bounty(70_000_000).rep("island/upper_yard", 300).rep("civilian", 60)
                .req("sky_5").flag("skypiea_freed").next("sky_7").add();
        q("sky_6.marine", "wakiza", "upper_yard", A).arrest("cirrus").reward(10000, 30000).rep("island/upper_yard", 300).rep("marine", 250)
                .req("sky_5").flag("skypiea_freed").next("sky_7").add();
        q("sky_6.revolutionary", "wakiza", "upper_yard", A).defeat("cirrus").talk("ayasha").reward(10000, 15000).bounty(50_000_000)
                .rep("island/upper_yard", 300).rep("revolutionary", 250).req("sky_5").flag("skypiea_freed").next("sky_7").add();
        q("sky_6.bounty_hunter", "wakiza", "upper_yard", A).arrest("cirrus").reward(10000, 60000).rep("island/upper_yard", 300).rep("bounty_hunter", 200)
                .req("sky_5").flag("skypiea_freed").next("sky_7").add();
        q("sky_6.civilian", "isidore", "weatheria", A).talk("isidore").talk("ferris").talk("wakiza").kill("ruin_guardian", 3).reward(10000, 15000)
                .rep("island/upper_yard", 300).rep("civilian", 250).req("sky_5").flag("skypiea_freed").next("sky_7").add();
        // the golden bell
        q("sky_7", "wakiza", "upper_yard", A).talk("ayasha").poneglyph(1).reward(8000, 25000).item(item("impact_dial", 1)).item(vanilla("gold_ingot", 8))
                .req("sky_6").flag("rang_the_golden_bell").add();
        // the Cloud End: a message for the Sky People's daughter below
        qRole("sky_8", "canal_guide", "cloud_end", A).talk("aria").reward(5000, 5000).item(SEA_CHART).req("sky_1").add();
    }

    // ================================================================== Part II — Fish-Man Island, "The Lantern in the Abyss" (Sabaody → New World)

    private static void fishmanIsland() {
        String A = "fishman_island";
        q("fmi_1", "ume", "sabaody_archipelago", A).discover("fishman_island").reward(8000, 10000).req("sabaody_1").next("fmi_2").add();
        q("fmi_2", "perla", "fishman_island", A).talk("marisol").talk("sawara").reward(8000, 12000).req("fmi_1").next("fmi_3").add();
        // the turncoats in the Coral Guard and their druggist
        q("fmi_3", "sawara", "fishman_island", A).kill("fishman_guard", 4).defeat("okoze").reward(12000, 20000).rep("island/fishman_island", 150)
                .req("fmi_2").next("fmi_4").add();
        q("fmi_3.marine", "sawara", "fishman_island", A).arrest("fishman_guard", 2).arrest("okoze").reward(12000, 25000).rep("island/fishman_island", 150)
                .rep("marine", 150).req("fmi_2").next("fmi_4").add();
        q("fmi_3.bounty_hunter", "sawara", "fishman_island", A).kill("fishman_guard", 4).arrest("okoze").reward(12000, 40000).rep("island/fishman_island", 150)
                .rep("bounty_hunter", 150).req("fmi_2").next("fmi_4").add();
        // the petition
        q("fmi_4", "marisol", "fishman_island", A).talk("resident", 3).talk("nereo").reward(10000, 15000).rep("island/fishman_island", 150)
                .req("fmi_3").next("fmi_5").add();
        q("fmi_4.civilian", "marisol", "fishman_island", A).talk("resident", 5).talk("merchant").talk("nereo").reward(10000, 15000)
                .rep("island/fishman_island", 150).rep("civilian", 200).req("fmi_3").next("fmi_5").add();
        // the Sea Forest: the queen's grave and the stone she died for
        q("fmi_5", "tidwell", "sea_forest", A).discover("coral_reef").collect("minecraft:sea_pickle", 4).poneglyph(1).reward(12000, 15000)
                .item(scroll("fishman_jujutsu")).req("fmi_4").next("fmi_6").add();
        // the Abyssal Tide
        q("fmi_6", "sawara", "fishman_island", A).defeat("lorcan").reward(16000, 30000).bounty(90_000_000).rep("island/fishman_island", 300).rep("civilian", 80)
                .req("fmi_5").flag("abyssal_tide_broken").next("fmi_7").add();
        q("fmi_6.marine", "sawara", "fishman_island", A).arrest("lorcan").reward(16000, 40000).rep("island/fishman_island", 300).rep("marine", 250)
                .req("fmi_5").flag("abyssal_tide_broken").next("fmi_7").add();
        q("fmi_6.revolutionary", "marisol", "fishman_island", A).defeat("lorcan").talk("marisol").reward(16000, 25000).bounty(60_000_000)
                .rep("island/fishman_island", 300).rep("revolutionary", 250).req("fmi_5").flag("abyssal_tide_broken").next("fmi_7").add();
        q("fmi_6.bounty_hunter", "sawara", "fishman_island", A).arrest("lorcan").reward(16000, 90000).rep("island/fishman_island", 300).rep("bounty_hunter", 250)
                .req("fmi_5").flag("abyssal_tide_broken").next("fmi_7").add();
        q("fmi_6.civilian", "marisol", "fishman_island", A).talk("resident", 3).talk("nereo").kill("fishman_guard", 3).reward(16000, 25000)
                .rep("island/fishman_island", 300).rep("civilian", 250).req("fmi_5").flag("abyssal_tide_broken").next("fmi_7").add();
        // the way on
        q("fmi_7", "nereo", "fishman_island", A).discover("sunken_ruins").poneglyph(1).reward(14000, 20000).item(pose("new_world_gate"))
                .req("fmi_6").next("fmi_8").add();
        q("fmi_8", "nereo", "fishman_island", A).discover("new_world_gate").talk("agathe").reward(15000, 25000).req("fmi_7").flag("fishman_island_ally").add();
    }

    // ================================================================== local stories: one for every canon island that had none

    private static void localStories() {
        String A = "local";
        // ---- East Blue ----
        q("dawn_1", "wren", "dawn_island", A).kill("bandit", 5).reward(450, 800).item(meat(4)).rep("island/dawn_island", 80).add();
        q("shimotsuki_1", "ren", "shimotsuki_village", A).talk("student").kill("bandit", 4).reward(500, 600).item(item("katana", 1))
                .rep("island/shimotsuki_village", 80).add();
        q("rare_animals_1", "fern", "island_of_rare_animals", A).kill("bandit", 6).reward(600, 900).item(SEA_CHART).rep("civilian", 20).add();
        q("mirror_ball_1", "tamsin", "mirror_ball_island", A).kill("bandit", 5).reward(600, 1000).rep("island/mirror_ball_island", 80).add();
        q("mirror_ball_1.revolutionary", "tamsin", "mirror_ball_island", A).kill("marine", 3).reward(700, 1000).rep("revolutionary", 100).add();
        // ---- North Blue ----
        q("flevance_1", "agnes", "flevance", A).kill("bandit", 5).reward(600, 900).item(meat(4)).rep("civilian", 30).add();
        q("minion_1", "sigvald", "minion_island", A).collect("#minecraft:logs", 16).kill("bandit", 3).reward(600, 900).item(item("bullet", 32)).add();
        q("swallow_1", "bjorn", "swallow_island", A).kill("bandit", 5).reward(600, 1000).rep("island/swallow_island", 80).add();
        q("swallow_1.revolutionary", "bjorn", "swallow_island", A).kill("marine", 3).reward(700, 1000).rep("revolutionary", 100).add();
        q("spider_1", "nell", "spider_miles", A).kill("bandit", 3).collect("minecraft:iron_ingot", 6).reward(600, 1200).item(item("slingshot", 1))
                .item(item("pellet", 32)).add();
        // ---- West Blue ----
        q("ohara_1", "oleander", "ohara", A).collect("minecraft:book", 3).kill("bandit", 4).reward(700, 1000).rep("island/ohara", 100).add();
        q("baterilla_1", "mireille", "baterilla", A).kill("bandit", 5).reward(600, 1000).rep("island/baterilla", 80).add();
        q("baterilla_1.revolutionary", "mireille", "baterilla", A).kill("marine", 3).reward(700, 1000).rep("revolutionary", 100).add();
        // ---- South Blue ----
        q("torino_1", "kestrel", "torino_kingdom", A).collect("minecraft:feather", 8).kill("bandit", 3).reward(600, 900).item(meat(4)).add();
        // ---- Paradise ----
        q("san_faldo_1", "livia", "san_faldo", A).kill("pirate", 5).talk("station_master").reward(3500, 6000).rep("island/san_faldo", 100).add();
        q("st_poplar_1", "rosalind", "st_poplar", A).collect("#minecraft:small_flowers", 12).reward(3000, 5000).rep("island/st_poplar", 100).add();
        q("pucci_1", "gustave", "pucci", A).collect("minecraft:cod", 6).kill("pirate", 3).reward(3500, 6000).item(steak(2)).add();
        q("boin_1", "ivo", "boin_archipelago", A).kill("pirate", 5).reward(4000, 5000).item(item("pop_green", 12)).add();
        q("namakura_1", "hattie", "namakura_island", A).collect("minecraft:iron_ingot", 8).reward(4000, 3000).item(item("fine_cutlass", 1)).add();
        q("banaro_1", "corvin", "banaro_island", A).kill("pirate", 6).reward(4500, 7000).item(meat(8)).rep("island/banaro_island", 100).add();
        q("momoiro_1", "peony", "momoiro_island", A).defeat("peony").collect("minecraft:pink_dye", 4).reward(5000, 6000).item(scroll("okama_kenpo")).add();
        q("karakuri_1", "linnea", "karakuri_island", A).collect("minecraft:redstone", 16).kill("pirate", 3).reward(5000, 8000).item(item("impact_dial", 1)).add();
        // ---- the Red Line ----
        q("holy_land_1", "juniper", "mary_geoise", A).talk("ume").talk("marisol").kill("marine", 4).reward(40000, 40000).rep("revolutionary", 200).add();
        // ---- New World ----
        q("nw_harbour_1", "agathe", "new_world_gate", A).kill("pirate", 8).reward(25000, 30000).item(LOG_POSE).add();
        q("punk_rook_1", "rook", "punk_hazard", A).kill("pirate", 6).reward(25000, 30000).rep("civilian", 60).add();
        q("sphinx_1", "nana", "sphinx", A).kill("pirate", 8).reward(30000, 25000).rep("island/sphinx", 150).add();
        q("cacao_1", "praline", "cacao_island", A).collect("minecraft:cocoa_beans", 12).kill("pirate", 4).reward(30000, 35000).add();
        q("jam_1", "bramble", "jam_island", A).collect("minecraft:sweet_berries", 16).reward(28000, 30000).add();
        q("foodvalten_1", "hansel", "foodvalten", A).kill("pirate", 8).reward(30000, 35000).rep("island/foodvalten", 150).add();
        q("oni_miners_1", "gonzaburo", "onigashima", A).kill("pirate", 10).reward(45000, 40000).rep("civilian", 100).add();
        q("hachinosu_moll_1", "moll", "hachinosu", A).kill("pirate", 10).reward(50000, 50000).rep("civilian", 100).add();
        q("laughtale_cinder_1", "cinder", "laugh_tale", A).poneglyph(1).kill("pirate", 6).reward(80000, 50000).flag("met_old_cinder").add();
    }

    private StoryQuestsExtra() {}
}
