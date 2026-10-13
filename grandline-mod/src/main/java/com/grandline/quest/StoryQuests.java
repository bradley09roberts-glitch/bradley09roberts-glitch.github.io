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
        // Part I (the Four Embers' openings, the East Blue, the North, West and South Blue) is written as chapters with
        // scenes, decisions and consequences: quest/arc/EastBlueArcs.java and BluesArcs.java
        // Part II up to Sabaody likewise: quest/arc/ParadiseArcs.java and SabaodyRoadArcs.java
        com.grandline.quest.arc.Arcs.register(all, IDS);
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

    // ================================================================== Part II — the Calm Belt (after Sabaody)

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
        q("laughtale_2.revolutionary", "aurelia", "lodestar_island", "finale").poneglyph(1).talk("juniper").reward(300000, 1_000_000)
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
