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
 * Quests of "The Saltfire Saga" (docs/STORY.md): an arc per major island, faction variants ("<id>.<faction>",
 * resolved automatically when a chain continues), a recruitment quest and a personal quest for every recruitable
 * crew member. Givers are notables ({@code notable:<id>}); a few New World arcs are offered by ordinary islanders.
 * Objective types used: kill (NPC role), defeat (notable id), talk (role or notable id), discover (island id),
 * poneglyph. All text is in lang/en_us/story.json under quest.grandline.<id>[.desc].
 */
final class StoryQuests {
    private static Map<String, QuestDef> out;

    static void register(Map<String, QuestDef> all) {
        out = all;
        embers();
        eastBlue();
        otherBlues();
        paradise();
        calmBelt();
        war();
        newWorld();
        crew();
        out = null;
    }

    // ================================================================== builder

    /** Every quest id registered by the saga, in registration order. */
    static final Set<String> IDS = new LinkedHashSet<>();

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

        Q kill(String role, int n) { obj.add(new QuestDef.Objective("kill", role, n)); return this; }
        Q defeat(String notable) { obj.add(new QuestDef.Objective("defeat", notable, 1)); return this; }
        Q talk(String who) { obj.add(new QuestDef.Objective("talk", who, 1)); return this; }
        Q talk(String who, int n) { obj.add(new QuestDef.Objective("talk", who, n)); return this; }
        Q discover(String island) { obj.add(new QuestDef.Objective("discover", island, 1)); return this; }
        Q poneglyph(int n) { obj.add(new QuestDef.Objective("poneglyph", "any", n)); return this; }
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
        }
    }

    private static Q q(String id, String notableGiver, String island, String arc) {
        return new Q(id, "notable:" + notableGiver, island, arc);
    }

    /** Offered by an ordinary islander role instead of a notable. */
    private static Q qRole(String id, String role, String island, String arc) {
        return new Q(id, role, island, arc);
    }

    // ---- reward items ----
    private static Supplier<ItemStack> item(String id, int n) {
        return () -> {
            var it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(GrandLine.MODID, id));
            return new ItemStack(it, n);
        };
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
    private static final Supplier<ItemStack> RUBBING = () -> new ItemStack(ModItems.PONEGLYPH_RUBBING.get());

    private static Supplier<ItemStack> meat(int n) {
        return () -> new ItemStack(ModItems.MEAT_ON_BONE.get(), n);
    }

    private static Supplier<ItemStack> steak(int n) {
        return () -> new ItemStack(ModItems.SEA_KING_STEAK.get(), n);
    }

    private static Supplier<ItemStack> cuffs(int n) {
        return () -> new ItemStack(ModItems.SEASTONE_CUFFS.get(), n);
    }

    // ================================================================== Part I — the Four Embers (starter isles)

    private static void embers() {
        String A = "embers";
        q("ember_east_1", "fumio", "shiokaze_isle", A).kill("bandit", 3).reward(120, 300).item(meat(3)).next("ember_east_2").add();
        q("ember_east_2", "fumio", "shiokaze_isle", A).discover("shells_town").reward(250, 500).req("ember_east_1").next("shells_1").add();
        q("ember_north_1", "solveig", "fuyugane_isle", A).kill("bandit", 3).reward(120, 300).item(meat(3)).next("ember_north_2").add();
        q("ember_north_2", "solveig", "fuyugane_isle", A).discover("spider_miles").reward(250, 500).req("ember_north_1").next("north_scrap_baron").add();
        q("ember_west_1", "percival", "kirinoha_isle", A).kill("bandit", 3).reward(120, 300).item(meat(3)).next("ember_west_2").add();
        q("ember_west_2", "percival", "kirinoha_isle", A).discover("kano_country").reward(250, 500).req("ember_west_1").next("west_kano_trial").add();
        q("ember_south_1", "mahina", "hinata_isle", A).kill("bandit", 3).reward(120, 300).item(meat(3)).next("ember_south_2").add();
        q("ember_south_2", "mahina", "hinata_isle", A).discover("karate_island").reward(250, 500).req("ember_south_1").next("south_karate_cup").add();
    }

    // ================================================================== Part I — East Blue

    private static void eastBlue() {
        String A = "east_blue";
        // Shells Town — "The Justice Levy"
        q("shells_1", "petra", "shells_town", A).discover("shells_town").kill("marine", 4).reward(400, 800)
                .rep("civilian", 20).rep("island/shells_town", 80).rep("marine", -40).next("shells_2").add();
        q("shells_1.marine", "petra", "shells_town", A).discover("shells_town").talk("resident").kill("bandit", 4).reward(400, 800)
                .rep("marine", 40).rep("island/shells_town", 80).next("shells_2").add();
        q("shells_2", "petra", "shells_town", A).defeat("gallowgate").reward(700, 1500).bounty(5_000_000)
                .rep("island/shells_town", 150).rep("civilian", 30).req("shells_1").next("orange_1").add();
        q("shells_2.marine", "petra", "shells_town", A).defeat("gallowgate").reward(700, 3000)
                .rep("island/shells_town", 150).rep("marine", 100).req("shells_1").flag("exposed_gallowgate").next("orange_1").add();
        q("recruit_petra", "petra", "shells_town", "recruit").kill("bandit", 3).reward(300, 0).item(item("bullet", 32)).req("shells_2").add();
        // Orange Town — "The Marmalade Siege"
        q("orange_1", "carmen", "orange_town", A).discover("orange_town").kill("bandit", 6).reward(500, 1000).next("orange_2").add();
        q("orange_2", "carmen", "orange_town", A).defeat("rotgut").reward(800, 2000).rep("island/orange_town", 150).rep("civilian", 30)
                .req("orange_1").next("syrup_1").add();
        q("recruit_carmen", "carmen", "orange_town", "recruit").talk("resident", 2).kill("bandit", 3).reward(300, 500).req("orange_2").add();
        // Syrup Village — "The Clockwork Regatta"
        q("syrup_1", "tomas", "syrup_village", A).discover("syrup_village").kill("bandit", 5).reward(550, 1200).next("syrup_2").add();
        q("syrup_2", "tomas", "syrup_village", A).defeat("gizmo").reward(900, 2500).rep("island/syrup_village", 150)
                .req("syrup_1").next("baratie_1").add();
        q("recruit_tomas", "tomas", "syrup_village", "recruit").discover("mirror_ball_island").reward(400, 500).item(SEA_CHART).req("syrup_2").add();
        // Baratie — "A Feast Under Siege"
        q("baratie_1", "kenji", "baratie", A).discover("baratie").kill("bandit", 6).reward(600, 1500).next("baratie_2").add();
        q("baratie_1.marine", "kenji", "baratie", A).discover("baratie").talk("horatio").kill("bandit", 6).reward(600, 2500).rep("marine", 60).next("baratie_2").add();
        q("baratie_2", "kenji", "baratie", A).defeat("fosco").reward(1000, 3000).rep("island/baratie", 150).rep("civilian", 40)
                .item(steak(2)).req("baratie_1").next("cocoyasi_1").add();
        q("recruit_kenji", "kenji", "baratie", "recruit").talk("horatio").kill("bandit", 4).reward(400, 800).req("baratie_2").add();
        // Cocoyasi — "The Coral Keep"
        q("cocoyasi_1", "kaimon", "cocoyasi", A).discover("cocoyasi").kill("fishman_guard", 2).reward(700, 1500).next("cocoyasi_2").add();
        q("cocoyasi_2", "kaimon", "cocoyasi", A).kill("fishman_brute", 2).defeat("gorgo").reward(1400, 4000)
                .rep("island/cocoyasi", 300).rep("civilian", 60).item(pose("loguetown")).req("cocoyasi_1").next("logue_1").add();
        q("recruit_kaimon", "kaimon", "cocoyasi", "recruit").talk("resident", 2).kill("bandit", 3).reward(500, 500).req("cocoyasi_2").add();
        // Loguetown — "The Town of Beginnings and Endings"
        q("logue_1", "odile", "loguetown", A).discover("loguetown").talk("bartolo").reward(800, 2000).next("logue_2").add();
        q("logue_2", "odile", "loguetown", A).defeat("tobias").kill("marine", 5).reward(1500, 4000).bounty(10_000_000)
                .rep("pirate", 100).req("logue_1").next("logue_3").add();
        q("logue_2.marine", "valka", "loguetown", A).defeat("tobias").kill("bandit", 5).reward(1500, 6000).rep("marine", 150)
                .req("logue_1").next("logue_3").add();
        q("logue_2.revolutionary", "pim", "loguetown", A).talk("pim").kill("marine", 3).reward(1500, 3000).bounty(6_000_000)
                .rep("revolutionary", 150).req("logue_1").next("logue_3").add();
        q("logue_2.bounty_hunter", "odile", "loguetown", A).defeat("tobias").kill("bandit", 5).reward(1500, 8000).rep("bounty_hunter", 150)
                .req("logue_1").next("logue_3").add();
        q("logue_2.civilian", "bartolo", "loguetown", A).talk("valka").talk("tobias").talk("pim").reward(1500, 5000).rep("civilian", 150)
                .req("logue_1").next("logue_3").add();
        q("logue_3", "odile", "loguetown", A).discover("twin_capes").reward(2000, 3000).item(LOG_POSE).req("logue_2").flag("saw_the_saltfire").next("capes_1").add();
        q("recruit_odile", "odile", "loguetown", "recruit").kill("bandit", 5).reward(500, 500).item(SEA_CHART).req("logue_1").add();
    }

    // ================================================================== Part I — North, West and South Blue prologues

    private static void otherBlues() {
        q("north_scrap_baron", "haakon", "lvneel", "north_blue").discover("spider_miles").defeat("rusty").kill("bandit", 4).reward(600, 1500)
                .next("north_lvneel_passage").add();
        q("north_lvneel_passage", "haakon", "lvneel", "north_blue").discover("lvneel").talk("haakon").reward(800, 2000).item(LOG_POSE)
                .req("north_scrap_baron").next("capes_1").add();
        q("west_kano_trial", "kagerou", "kano_country", "west_blue").discover("kano_country").defeat("kagerou").reward(600, 1000).item(scroll("ittoryu"))
                .next("west_ilisia_audience").add();
        q("west_ilisia_audience", "isadora", "ilisia_kingdom", "west_blue").discover("ilisia_kingdom").kill("bandit", 5).talk("isadora").reward(800, 2000)
                .item(LOG_POSE).req("west_kano_trial").next("capes_1").add();
        q("south_karate_cup", "kiko", "karate_island", "south_blue").discover("karate_island").defeat("kiko").kill("bandit", 3).reward(600, 1000)
                .item(scroll("tide_grapple")).next("south_sorbet_royal").add();
        q("south_sorbet_royal", "gelato", "sorbet_kingdom", "south_blue").discover("sorbet_kingdom").kill("bandit", 5).talk("gelato").reward(800, 2000)
                .item(LOG_POSE).req("south_karate_cup").next("capes_1").add();
    }

    // ================================================================== Part II — the Paradise Gauntlet

    private static void paradise() {
        String A = "paradise";
        // Twin Capes
        q("capes_1", "wilhelmina", "twin_capes", A).talk("wilhelmina").kill("pirate", 3).reward(2000, 3000).item(LOG_POSE).next("capes_2").add();
        q("capes_2", "wilhelmina", "twin_capes", A).discover("whisky_peak").reward(1500, 2000).req("capes_1").next("whisky_1").add();
        // Whisky Peak — "The Gilded Welcome"
        q("whisky_1", "hugo", "whisky_peak", A).discover("whisky_peak").kill("pirate", 6).reward(2500, 5000).next("whisky_2").add();
        q("whisky_1.bounty_hunter", "hugo", "whisky_peak", A).discover("whisky_peak").talk("hugo").kill("pirate", 6).reward(2500, 8000)
                .rep("bounty_hunter", 150).item(cuffs(4)).next("whisky_2").add();
        q("whisky_2", "hugo", "whisky_peak", A).defeat("doubloon").defeat("farthing").reward(3500, 8000).rep("island/whisky_peak", 150)
                .req("whisky_1").next("garden_1").add();
        q("recruit_ignatz", "ignatz", "whisky_peak", "recruit").kill("pirate", 4).reward(1200, 2000).req("whisky_2").add();
        // Little Garden — "The Seventy-Year Bake-Off"
        q("garden_1", "astrid", "little_garden", A).discover("little_garden").talk("ylva").kill("pirate", 5).reward(3000, 5000).next("garden_2").add();
        q("garden_2", "astrid", "little_garden", A).defeat("shilling").reward(4000, 9000).rep("island/little_garden", 200).item(steak(4))
                .req("garden_1").next("drum_1").add();
        q("recruit_hilde", "hilde", "little_garden", "recruit").talk("astrid").kill("pirate", 4).reward(1500, 1000).req("garden_2").add();
        // Drum Island — "Panacea"
        q("drum_1", "pippa", "drum_island", A).discover("drum_island").talk("raven").kill("pirate", 4).reward(3500, 6000).next("drum_2").add();
        q("drum_2", "pippa", "drum_island", A).defeat("panacea").reward(5000, 10000).rep("island/drum_island", 200).rep("civilian", 50)
                .req("drum_1").next("alabasta_1").add();
        q("recruit_pippa", "pippa", "drum_island", "recruit").talk("resident", 2).kill("pirate", 3).reward(1500, 1000).req("drum_2").add();
        // Alabasta — "Salt in the Wells"
        q("alabasta_1", "sefa", "alabasta", A).discover("alabasta").talk("rashid").kill("pirate", 6).reward(4000, 8000).next("alabasta_2").add();
        q("alabasta_2", "sefa", "alabasta", A).defeat("celestine").reward(5000, 12000).bounty(30_000_000).req("alabasta_1").next("alabasta_3").add();
        q("alabasta_2.marine", "rashid", "alabasta", A).defeat("celestine").talk("rashid").reward(5000, 15000).rep("marine", 150)
                .req("alabasta_1").next("alabasta_3").add();
        q("alabasta_2.revolutionary", "sefa", "alabasta", A).talk("rashid").kill("pirate", 8).reward(5000, 8000).bounty(20_000_000)
                .rep("revolutionary", 200).req("alabasta_1").next("alabasta_3").add();
        q("alabasta_3", "sefa", "alabasta", A).defeat("saltmarsh").reward(8000, 20000).bounty(80_000_000).rep("island/alabasta", 300).rep("civilian", 80)
                .req("alabasta_2").flag("saltmarsh_toppled").next("jaya_1").add();
        q("alabasta_3.marine", "rashid", "alabasta", A).defeat("saltmarsh").reward(8000, 30000).rep("island/alabasta", 300).rep("marine", 250)
                .req("alabasta_2").flag("saltmarsh_toppled").next("jaya_1").add();
        q("alabasta_3.bounty_hunter", "sefa", "alabasta", A).defeat("saltmarsh").reward(8000, 60000).rep("island/alabasta", 300).rep("bounty_hunter", 200)
                .req("alabasta_2").flag("saltmarsh_toppled").next("jaya_1").add();
        q("alabasta_3.civilian", "rashid", "alabasta", A).talk("rashid").kill("pirate", 6).reward(8000, 15000).rep("island/alabasta", 300).rep("civilian", 200)
                .req("alabasta_2").flag("saltmarsh_toppled").next("jaya_1").add();
        q("recruit_sefa", "sefa", "alabasta", "recruit").poneglyph(1).talk("rashid").reward(2500, 2000).req("alabasta_2").add();
        // Jaya — "The Debt and the Sky"
        q("jaya_1", "aria", "jaya", A).discover("jaya").talk("felicity").kill("pirate", 8).reward(5000, 10000).next("jaya_2").add();
        q("jaya_2", "aria", "jaya", A).defeat("usurio").reward(6500, 15000).rep("island/jaya", 200).req("jaya_1").next("water7_1").add();
        q("recruit_aria", "aria", "jaya", "recruit").kill("pirate", 5).discover("long_ring_long_land").reward(2500, 2000).req("jaya_2").add();
        // Long Ring Long Land — "Tug-of-Tides"
        q("ring_1", "gunnar", "long_ring_long_land", A).discover("long_ring_long_land").defeat("ayame").kill("pirate", 4).reward(5000, 8000).add();
        q("recruit_gunnar", "gunnar", "long_ring_long_land", "recruit").talk("ayame").reward(2000, 1000).req("ring_1").add();
        // Water 7 — "The Three Guilds"
        q("water7_1", "hollis", "water_7", A).discover("water_7").talk("kanna").talk("matthias").reward(6000, 12000).next("water7_2").add();
        q("water7_2", "hollis", "water_7", A).defeat("nimue").defeat("brick").reward(9000, 20000).bounty(40_000_000).req("water7_1").next("water7_3").add();
        q("water7_2.marine", "hollis", "water_7", A).defeat("nimue").defeat("brick").reward(9000, 25000).rep("marine", 200)
                .req("water7_1").next("water7_3").add();
        q("water7_3", "hollis", "water_7", A).talk("kanna").kill("pirate", 6).reward(6000, 10000).rep("island/water_7", 250).req("water7_2").next("enies_1").add();
        q("recruit_kanna", "kanna", "water_7", "recruit").talk("hollis").kill("pirate", 5).reward(3000, 3000).req("water7_2").add();
        // Enies Lobby — "The Gates of Justice"
        q("enies_1", "nimbus", "enies_lobby", A).discover("enies_lobby").kill("cp_agent", 3).reward(9000, 15000).bounty(60_000_000).next("enies_2").add();
        q("enies_1.marine", "dmitri", "enies_lobby", A).discover("enies_lobby").talk("dmitri").kill("pirate", 6).reward(9000, 20000).rep("marine", 200)
                .next("enies_2").add();
        q("enies_2", "nimbus", "enies_lobby", A).defeat("gregor").reward(12000, 25000).bounty(80_000_000).req("enies_1").next("enies_3").add();
        q("enies_2.marine", "dmitri", "enies_lobby", A).talk("nimbus").talk("dmitri").kill("pirate", 6).reward(12000, 30000).rep("marine", 250)
                .flag("chose_evacuation").req("enies_1").next("enies_3").add();
        q("enies_3", "nimbus", "enies_lobby", A).kill("marine", 6).reward(8000, 15000).bounty(50_000_000).req("enies_2").next("thriller_1").add();
        q("enies_3.marine", "dmitri", "enies_lobby", A).talk("dmitri").kill("pirate", 6).reward(8000, 20000).rep("marine", 200).req("enies_2").next("thriller_1").add();
        q("enies_3.revolutionary", "nimbus", "enies_lobby", A).kill("marine", 6).reward(8000, 15000).bounty(40_000_000)
                .rep("revolutionary", 250).req("enies_2").next("thriller_1").add();
        q("enies_3.bounty_hunter", "nimbus", "enies_lobby", A).kill("pirate", 6).reward(8000, 30000).rep("bounty_hunter", 200).req("enies_2").next("thriller_1").add();
        q("enies_3.civilian", "nimbus", "enies_lobby", A).talk("nimbus").talk("dmitri").reward(8000, 12000).rep("civilian", 200).req("enies_2").next("thriller_1").add();
        q("recruit_nimbus", "nimbus", "enies_lobby", "recruit").kill("cp_agent", 2).reward(3000, 3000).req("enies_2").add();
        // Thriller Bark — "The Mirror-Monger"
        q("thriller_1", "mortimer", "thriller_bark", A).discover("thriller_bark").kill("pirate", 8).reward(9000, 15000).next("thriller_2").add();
        q("thriller_2", "mortimer", "thriller_bark", A).defeat("lucrezia").reward(13000, 30000).bounty(70_000_000).rep("civilian", 80)
                .req("thriller_1").next("sabaody_1").add();
        q("thriller_2.marine", "mortimer", "thriller_bark", A).defeat("lucrezia").reward(13000, 40000).rep("marine", 200)
                .req("thriller_1").next("sabaody_1").add();
        // Sabaody — "The Auction of Souls"
        q("sabaody_1", "ume", "sabaody_archipelago", A).discover("sabaody_archipelago").talk("coralie").talk("edison").reward(10000, 20000).next("sabaody_2").add();
        q("sabaody_2", "coralie", "sabaody_archipelago", A).defeat("augustin").kill("pirate", 6).reward(14000, 30000).bounty(100_000_000)
                .rep("civilian", 100).req("sabaody_1").next("sabaody_3").add();
        q("sabaody_2.marine", "zenith", "sabaody_archipelago", A).talk("zenith").talk("pompadour").kill("pirate", 8).reward(14000, 40000).rep("marine", 250)
                .req("sabaody_1").next("sabaody_3").add();
        q("sabaody_2.civilian", "coralie", "sabaody_archipelago", A).talk("coralie").talk("ume").kill("pirate", 6).reward(14000, 20000).rep("civilian", 250)
                .req("sabaody_1").next("sabaody_3").add();
        q("sabaody_3", "ume", "sabaody_archipelago", A).kill("marine", 8).talk("ume").reward(15000, 20000).bounty(50_000_000)
                .req("sabaody_2").flag("lamplighter_captured").next("lily_1").add();
        q("sabaody_3.marine", "zenith", "sabaody_archipelago", A).talk("zenith").kill("pirate", 8).reward(15000, 30000).rep("marine", 200)
                .req("sabaody_2").flag("lamplighter_captured").next("lily_1").add();
    }

    // ================================================================== Part II — the Calm Belt interlude

    private static void calmBelt() {
        String A = "calm_belt";
        q("lily_1", "hisui", "amazon_lily", A).discover("amazon_lily").defeat("hisui").reward(15000, 25000).req("sabaody_3").next("rusukaina_1").add();
        q("rusukaina_1", "tsukumo", "rusukaina", A).discover("rusukaina").talk("tsukumo").reward(15000, 5000).item(scroll("hasshoken"))
                .req("sabaody_3").next("impel_1").add();
        q("impel_1", "tsukumo", "rusukaina", A).discover("impel_down").kill("jailer", 2).kill("jailer_beast", 2).reward(18000, 10000).bounty(120_000_000)
                .req("sabaody_3").next("war_1").add();
        q("impel_1.marine", "grimsby", "impel_down", A).discover("impel_down").talk("grimsby").kill("pirate", 6).reward(18000, 20000).rep("marine", 250)
                .req("sabaody_3").next("war_1").add();
        q("impel_1.bounty_hunter", "grimsby", "impel_down", A).discover("impel_down").talk("grimsby").kill("pirate", 6).reward(18000, 40000)
                .rep("bounty_hunter", 200).req("sabaody_3").next("war_1").add();
    }

    // ================================================================== Part III — the War of the Crescent Bay

    private static void war() {
        String A = "war";
        q("war_1", "haruka", "marineford", A).discover("marineford").kill("marine", 10).reward(25000, 30000).bounty(200_000_000)
                .rep("pirate", 300).req("sabaody_3").next("war_2").add();
        q("war_1.marine", "osric", "marineford", A).discover("marineford").talk("osric").kill("pirate", 10).reward(25000, 50000).rep("marine", 300)
                .req("sabaody_3").next("war_2").add();
        q("war_1.revolutionary", "haruka", "marineford", A).discover("marineford").kill("marine", 8).reward(25000, 30000)
                .bounty(150_000_000).rep("revolutionary", 300).req("sabaody_3").next("war_2").add();
        q("war_1.bounty_hunter", "haruka", "marineford", A).discover("marineford").kill("pirate", 6).kill("marine", 4).reward(25000, 60000)
                .rep("bounty_hunter", 200).req("sabaody_3").next("war_2").add();
        q("war_1.civilian", "haruka", "marineford", A).discover("marineford").talk("osric").talk("resident", 3).reward(25000, 30000).rep("civilian", 300)
                .req("sabaody_3").next("war_2").add();
        q("war_2", "haruka", "marineford", A).kill("marine", 10).talk("haruka").reward(30000, 40000).bounty(300_000_000).req("war_1").next("war_3").add();
        q("war_2.marine", "osric", "marineford", A).talk("haruka").talk("constance").reward(30000, 60000).rep("marine", 300)
                .flag("stood_beside_haruka").req("war_1").next("war_3").add();
        q("war_2.revolutionary", "haruka", "marineford", A).kill("marine", 8).talk("haruka").reward(30000, 40000).bounty(200_000_000)
                .rep("revolutionary", 300).req("war_1").next("war_3").add();
        q("war_2.bounty_hunter", "haruka", "marineford", A).talk("haruka").kill("pirate", 8).reward(30000, 100000).rep("bounty_hunter", 300)
                .req("war_1").next("war_3").add();
        q("war_2.civilian", "haruka", "marineford", A).talk("osric").talk("haruka").reward(30000, 40000).rep("civilian", 300)
                .req("war_1").next("war_3").add();
        q("war_3", "haruka", "marineford", A).talk("haruka").reward(20000, 20000).item(pose("new_world_gate")).req("war_2").flag("war_survived")
                .next("nw_gate_1").add();
        q("kuraigana_1", "jinpachi", "kuraigana_island", A).discover("kuraigana_island").defeat("jinpachi").reward(20000, 0).item(scroll("iaido"))
                .req("war_2").add();
    }

    // ================================================================== Part IV — the New World

    private static void newWorld() {
        String A = "new_world";
        q("nw_gate_1", "seraphine", "g5_base", A).discover("new_world_gate").kill("pirate", 10).reward(25000, 30000).req("war_3").next("punk_1").add();
        q("nw_gate_1.marine", "seraphine", "g5_base", A).discover("g5_base").talk("seraphine").kill("pirate", 10).reward(25000, 40000).rep("marine", 300)
                .req("war_3").next("punk_1").add();
        q("punk_1", "seraphine", "g5_base", A).discover("punk_hazard").defeat("hesper").reward(30000, 40000).bounty(150_000_000)
                .req("nw_gate_1").next("dress_1").add();
        q("punk_1.marine", "seraphine", "g5_base", A).discover("punk_hazard").defeat("hesper").reward(30000, 60000).rep("marine", 300)
                .req("nw_gate_1").next("dress_1").add();
        // Dressrosa and the candy islands are offered by their islanders (no notable there is on the player's side)
        qRole("dress_1", "resident", "dressrosa", A).discover("dressrosa").kill("pirate", 10).reward(30000, 40000).next("dress_2").add();
        qRole("dress_2", "resident", "dressrosa", A).defeat("florian").reward(40000, 60000).item(RUBBING).req("dress_1").flag("rubbing_dressrosa")
                .next("greenbit_1").add();
        q("greenbit_1", "bolt", "green_bit", A).discover("green_bit").defeat("pipistrello").reward(35000, 40000).next("zou_1").add();
        q("recruit_bolt", "bolt", "green_bit", "recruit").kill("pirate", 6).discover("dressrosa").reward(8000, 5000).req("greenbit_1").add();
        q("zou_1", "leonhart", "zou", A).discover("zou").defeat("leonhart").reward(40000, 30000).item(RUBBING).flag("rubbing_zou").next("cake_1").add();
        q("recruit_pashmina", "pashmina", "zou", "recruit").talk("leonhart").kill("pirate", 8).reward(8000, 5000).req("zou_1").add();
        qRole("cake_1", "resident", "whole_cake_island", A).discover("whole_cake_island").defeat("fennimore").kill("pirate", 10).reward(45000, 50000)
                .next("cake_2").add();
        qRole("cake_2", "resident", "whole_cake_island", A).defeat("marzipan").defeat("barnum").reward(80000, 120000).item(RUBBING)
                .req("cake_1").flag("rubbing_cake").next("wano_1").add();
        q("wano_1", "rin", "wano_country", A).discover("wano_country").defeat("hagane").kill("pirate", 10).reward(60000, 60000).next("oni_1").add();
        q("recruit_rin", "rin", "wano_country", "recruit").kill("pirate", 6).discover("onigashima").reward(10000, 8000).req("wano_1").add();
        q("oni_1", "rin", "wano_country", A).discover("onigashima").defeat("gorou").defeat("tsubaki").reward(70000, 80000).req("wano_1").next("oni_2").add();
        q("oni_2", "rin", "wano_country", A).defeat("kurogane").reward(120000, 200000).item(RUBBING).req("oni_1").flag("rubbing_oni").next("egghead_1").add();
        q("egghead_1", "minerva", "egghead", A).discover("egghead").talk("minerva").defeat("vespera").reward(90000, 80000).next("baltigo_1").add();
        q("recruit_elowen", "elowen", "egghead", "recruit").talk("minerva").kill("pirate", 8).reward(15000, 10000).req("egghead_1").add();
        q("baltigo_1", "sigrun", "baltigo", A).discover("baltigo").talk("sigrun").talk("lysander").reward(50000, 30000).next("hachinosu_1").add();
        q("baltigo_1.revolutionary", "sigrun", "baltigo", A).discover("baltigo").talk("sigrun").talk("lysander").reward(50000, 30000).rep("revolutionary", 500)
                .next("hachinosu_1").add();
        q("baltigo_1.marine", "prudence", "new_marineford", A).discover("baltigo").defeat("lysander").talk("prudence").reward(50000, 60000).rep("marine", 400)
                .next("hachinosu_1").add();
        q("hachinosu_1", "sigrun", "baltigo", A).discover("hachinosu").defeat("sterling").reward(120000, 250000).bounty(500_000_000)
                .req("baltigo_1").next("elbaph_1").add();
        q("hachinosu_1.marine", "prudence", "new_marineford", A).discover("hachinosu").defeat("sterling").reward(120000, 300000).rep("marine", 500)
                .req("baltigo_1").next("elbaph_1").add();
        q("elbaph_1", "sigrid", "elbaph", A).discover("elbaph").defeat("sigrid").talk("hrolf").reward(100000, 50000).next("lodestar_1").add();
        q("lodestar_1", "aurelia", "lodestar_island", A).discover("lodestar_island").defeat("asagao").defeat("aurelia").reward(150000, 100000)
                .req("elbaph_1", "dress_2", "zou_1", "cake_2", "oni_2").item(pose("laugh_tale")).next("laughtale_1").add();
        // Laugh Tale — the ending, one per path
        q("laughtale_1", "aurelia", "lodestar_island", "finale").discover("laugh_tale").defeat("nox").reward(200000, 300000).req("lodestar_1").next("laughtale_2").add();
        q("laughtale_2", "aurelia", "lodestar_island", "finale").poneglyph(1).reward(300000, 1_000_000).bounty(1_500_000_000)
                .req("laughtale_1").flag("ending_saltfire_kindled").add();
        q("laughtale_2.marine", "aurelia", "lodestar_island", "finale").poneglyph(1).reward(300000, 1_000_000).rep("marine", 1000)
                .req("laughtale_1").flag("ending_true_justice").add();
        q("laughtale_2.revolutionary", "aurelia", "lodestar_island", "finale").poneglyph(1).discover("mary_geoise").reward(300000, 1_000_000)
                .rep("revolutionary", 1000).req("laughtale_1").flag("ending_dawn").add();
        q("laughtale_2.bounty_hunter", "aurelia", "lodestar_island", "finale").poneglyph(1).reward(300000, 3_000_000).rep("bounty_hunter", 1000)
                .req("laughtale_1").flag("ending_last_bounty").add();
        q("laughtale_2.civilian", "aurelia", "lodestar_island", "finale").poneglyph(1).talk("bartolo").reward(300000, 1_000_000).rep("civilian", 1000)
                .req("laughtale_1").flag("ending_free_press").add();
    }

    // ================================================================== personal quests (offered by the crew member while in your crew)

    private static void crew() {
        String A = "crew";
        q("personal_odile", "odile", "*", A).discover("drum_island").discover("alabasta").discover("water_7").reward(6000, 5000)
                .item(scroll("storm_dance")).req("recruit_odile").add();
        q("personal_hilde", "hilde", "*", A).discover("baratie").discover("alabasta").kill("pirate", 8).reward(6000, 4000).item(steak(16))
                .req("recruit_hilde").add();
        q("personal_pippa", "pippa", "*", A).discover("punk_hazard").talk("minerva").reward(20000, 10000).item(scroll("life_return")).req("recruit_pippa").add();
        q("personal_kanna", "kanna", "*", A).discover("enies_lobby").kill("cp_agent", 3).reward(12000, 10000).item(item("tidebreaker", 1))
                .req("recruit_kanna").add();
        q("personal_petra", "petra", "*", A).discover("sabaody_archipelago").kill("pirate_captain", 3).reward(10000, 6000).item(item("musket", 1))
                .item(item("bullet", 64)).req("recruit_petra").add();
        q("personal_carmen", "carmen", "*", A).discover("sabaody_archipelago").talk("coralie").reward(8000, 6000).item(item("impact_dial", 1))
                .req("recruit_carmen").add();
        q("personal_sefa", "sefa", "*", A).poneglyph(3).reward(15000, 8000).item(RUBBING).req("recruit_sefa").add();
        q("personal_tomas", "tomas", "*", A).discover("twin_capes").talk("wilhelmina").reward(5000, 4000).item(LOG_POSE).req("recruit_tomas").add();
        q("personal_kenji", "kenji", "*", A).discover("kuraigana_island").defeat("jinpachi").reward(20000, 8000).item(scroll("iaido")).req("recruit_kenji").add();
        q("personal_kaimon", "kaimon", "*", A).discover("sabaody_archipelago").defeat("augustin").reward(15000, 8000).item(scroll("fishman_karate"))
                .req("recruit_kaimon").add();
        q("personal_aria", "aria", "*", A).discover("long_ring_long_land").discover("jaya").kill("pirate", 6).reward(10000, 6000).item(item("war_bow", 1))
                .req("recruit_aria").add();
        q("personal_ignatz", "ignatz", "*", A).defeat("celestine").reward(10000, 50000).req("recruit_ignatz").add();
        q("personal_nimbus", "nimbus", "*", A).discover("egghead").defeat("vespera").reward(60000, 20000).item(scroll("rokushiki")).req("recruit_nimbus").add();
        q("personal_gunnar", "gunnar", "*", A).discover("elbaph").talk("hrolf").reward(40000, 10000).item(item("iron_kanabo", 1)).req("recruit_gunnar").add();
        q("personal_rin", "rin", "*", A).defeat("tsubaki").reward(60000, 20000).item(item("fine_katana", 1)).item(scroll("iaido")).req("recruit_rin").add();
        q("personal_pashmina", "pashmina", "*", A).defeat("gorou").reward(60000, 20000).item(scroll("electro")).req("recruit_pashmina").add();
        q("personal_bolt", "bolt", "*", A).discover("egghead").talk("minerva").reward(40000, 20000).item(item("bazooka", 1)).req("recruit_bolt").add();
        q("personal_elowen", "elowen", "*", A).discover("lodestar_island").talk("aurelia").reward(80000, 30000).item(pose("laugh_tale")).req("recruit_elowen").add();
    }

    private StoryQuests() {}
}
