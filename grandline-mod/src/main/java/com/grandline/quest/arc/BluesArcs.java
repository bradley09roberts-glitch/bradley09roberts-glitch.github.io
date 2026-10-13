package com.grandline.quest.arc;

import com.grandline.quest.arc.Arc.Role;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Map;

import static com.grandline.quest.arc.Arc.Beat.*;
import static com.grandline.quest.arc.ArcBuilder.*;

/**
 * Part I for players who start in the North, West and South Blue: each Ember's opening on its starter isle (with the
 * same combat lesson as the East), then that Blue's own chapter, ending at the Twin Capes. Text:
 * lang/en_us/story_overhaul_blues.json.
 */
final class BluesArcs {
    private BluesArcs() {}

    static void register() {
        scrapBaron();
        swallowAndCrown();
        iceHouse();
    }

    // ================================================================== North Blue — The Scrap Baron (Fuyugane Isle → Spider Miles → Lvneel)

    /**
     * Fuyugane's whalers had their harpoon guns stolen and sold to Rusty the Scrap Baron, who is bolting every gun in
     * the North Blue onto gunboats to raid Lvneel. Nell the junk-picker knows the yard. Decide: spike the gunboats
     * (Lvneel is never raided and its king pays well, but the press-ganged pickers wait in the pens until the Baron
     * falls) or free the pickers first (they fight beside you, but the gunboats slip out and raid Lvneel's harbour).
     */
    private static void scrapBaron() {
        String A = "scrap_baron";
        arc(A, 1, "north_blue", "spider_miles")
                .cast("solveig", Role.GIVER).cast("nell", Role.ALLY).cast("haakon", Role.ALLY).cast("rusty", Role.VILLAIN)
                .step(q("ember_north_1", "solveig", "fuyugane_isle").kill("bandit", 3).lesson().reward(120, 300).item(meat(3)).item(SEA_CHART).next("ember_north_2")
                        .site(0, "fuyugane_raiders", "wilds", "bandit"), ARRIVAL, CAST, PROBLEM)
                .step(q("ember_north_2", "solveig", "fuyugane_isle").discover("spider_miles").reward(250, 500).req("ember_north_1")
                        .next("north_nell"), DISCOVERY)
                .step(q("north_nell", "nell", "spider_miles").talk("nell").reward(150, 200).req("ember_north_2").next("north_yard"), DISCOVERY)
                .step(q("north_yard", "nell", "spider_miles").kill("bandit", 4).reward(400, 700).req("north_nell").next("north_choice")
                        .site(0, "spider_gun_yard", "home:rusty,14,0", "bandit"), ESCALATION)
                .step(q("north_choice", "nell", "spider_miles").choice("north_plan").reward(50, 0).req("north_yard"), DECISION)
                .step(q("north_spike", "nell", "spider_miles").kill("bandit", 3).reward(250, 400).req("north_choice").next("north_scrap_baron")
                        .site(0, "spider_slipway", "home:rusty,-12,8", "bandit"), CLIMAX)
                .step(q("north_pens", "nell", "spider_miles").kill("bandit", 3).reward(250, 400).rep("civilian", 30).req("north_choice").next("north_scrap_baron")
                        .site(0, "spider_pens", "home:nell,10,-8", "bandit"), CLIMAX)
                .step(q("north_scrap_baron", "nell", "spider_miles").defeat("rusty").reward(600, 1500).rep("island/spider_miles", 100).req("north_choice")
                        .next("north_lvneel_passage"), CLIMAX)
                .step(q("north_lvneel_passage", "haakon", "lvneel").discover("lvneel").talk("haakon").reward(800, 2000).item(LOG_POSE)
                        .req("north_scrap_baron").next("capes_1"), AFTERMATH)
                .decision("north_plan", "north_choice", "nell", choice("gunboats", "north_spike"), choice("pickers", "north_pens"))
                .add();
        Consequences.affects(A, "fuyugane_isle", "lvneel");
        Consequences.discount(A, "fuyugane_isle", null, 0.9); // the whalers hunt again
        Consequences.bonus("north_lvneel_passage", "north_plan=gunboats", 3000, Map.of("island/lvneel", 120), List.of(), "arc.grandline.scrap_baron.bonus.gunboats");
        Consequences.bonus("north_scrap_baron", "north_plan=pickers", 0, Map.of("civilian", 40, "island/spider_miles", 60), List.of(item("slingshot", 1), item("pellet", 48)),
                "arc.grandline.scrap_baron.bonus.pickers");
        Consequences.aid("north_scrap_baron", "north_plan=pickers", "rusty", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.scrap_baron.aid.pickers");
        StoryMigration.legacy("ember_north_1", "kill:bandit");
        StoryMigration.legacy("north_scrap_baron", "discover:spider_miles", "defeat:rusty", "kill:bandit");
    }

    // ================================================================== West Blue — The Swallow and the Crown (Kirinoha Isle → Kano Country → Ilisia)

    /**
     * Brigands on Ilisia's royal road raid Kirinoha's wool caravans. Master Kagerou's trial teaches the player to ride
     * the wind; on the road they find the "brigands" are the queen's own guards, unpaid for a year because Lord
     * Treasurer Corvel emptied the treasury and hired real cut-throats to blame them. Decide: pardon the deserters and
     * let them help take the treasurer (they fight beside you and guard the road again) or bring everyone before the
     * royal court (justice by the book: the queen knights you, but the road goes unguarded for a season).
     */
    private static void swallowAndCrown() {
        String A = "swallow_crown";
        arc(A, 1, "west_blue", "ilisia_kingdom")
                .cast("percival", Role.GIVER).cast("kagerou", Role.ALLY).cast("isadora", Role.ALLY).cast("corvel", Role.VILLAIN)
                .step(q("ember_west_1", "percival", "kirinoha_isle").kill("bandit", 3).lesson().reward(120, 300).item(meat(3)).item(SEA_CHART).next("ember_west_2")
                        .site(0, "kirinoha_raiders", "wilds", "bandit"), ARRIVAL, CAST, PROBLEM)
                .step(q("ember_west_2", "percival", "kirinoha_isle").discover("kano_country").reward(250, 500).req("ember_west_1")
                        .next("west_kano_trial"), DISCOVERY)
                .step(q("west_kano_trial", "kagerou", "kano_country").discover("kano_country").defeat("kagerou").reward(600, 1000).item(scroll("ittoryu"))
                        .req("ember_west_2").next("west_road"), DISCOVERY)
                .step(q("west_road", "isadora", "ilisia_kingdom").discover("ilisia_kingdom").kill("bandit", 5).reward(500, 800).req("west_kano_trial")
                        .next("west_ilisia_audience").site(1, "ilisia_road", "wilds", "bandit"), ESCALATION)
                .step(q("west_ilisia_audience", "isadora", "ilisia_kingdom").talk("isadora").reward(300, 500).req("west_road").next("west_choice"), ESCALATION)
                .step(q("west_choice", "isadora", "ilisia_kingdom").choice("west_plan").reward(50, 0).req("west_ilisia_audience"), DECISION)
                .step(q("west_pardon", "isadora", "ilisia_kingdom").talk("resident", 2).reward(200, 300).req("west_choice").next("west_corvel"), CLIMAX)
                .step(q("west_court", "isadora", "ilisia_kingdom").kill("bandit", 3).reward(250, 400).req("west_choice").next("west_corvel")
                        .site(0, "ilisia_cutthroats", "home:corvel,-10,14", "bandit"), CLIMAX)
                .step(q("west_corvel", "isadora", "ilisia_kingdom").defeat("corvel").reward(700, 1500).rep("island/ilisia_kingdom", 150).req("west_choice")
                        .next("west_knighted"), CLIMAX)
                .step(q("west_knighted", "isadora", "ilisia_kingdom").talk("isadora").reward(400, 1000).item(LOG_POSE).req("west_corvel").next("capes_1"), AFTERMATH)
                .decision("west_plan", "west_choice", "isadora", choice("pardon", "west_pardon"), choice("court", "west_court"))
                .add();
        Consequences.affects(A, "kirinoha_isle");
        Consequences.discount(A, "kirinoha_isle", null, 0.9); // the caravans run again
        Consequences.discount(A, "ilisia_kingdom", "west_plan=pardon", 0.85);
        Consequences.bonus("west_corvel", "west_plan=court", 0, Map.of("island/ilisia_kingdom", 150), List.of(item("fine_cutlass", 1)), "arc.grandline.swallow_crown.bonus.court");
        Consequences.bonus("west_corvel", "west_plan=pardon", 0, Map.of("civilian", 40), List.of(), "arc.grandline.swallow_crown.bonus.pardon");
        Consequences.aid("west_corvel", "west_plan=pardon", "corvel", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.swallow_crown.aid.pardon");
        StoryMigration.legacy("ember_west_1", "kill:bandit");
        StoryMigration.legacy("west_ilisia_audience", "discover:ilisia_kingdom", "kill:bandit", "talk:isadora");
    }

    // ================================================================== South Blue — The Ice House (Hinata Isle → Karate Island → Sorbet Kingdom)

    /**
     * The ice ships that keep the South Blue's catch fresh have stopped: Captain Brinicle, a sea-karate student thrown
     * out of Master Kiko's dojo, seized the Sorbet Kingdom's royal ice house. Decide: storm it now with Kiko (she fights
     * beside you, but Brinicle melts half the stores out of spite) or wait for the night frost and sneak the ice out
     * first (the stores are saved and the king is generous, but Brinicle is warned and his crew stands guard).
     */
    private static void iceHouse() {
        String A = "ice_house";
        arc(A, 1, "south_blue", "sorbet_kingdom")
                .cast("mahina", Role.GIVER).cast("kiko", Role.ALLY).cast("gelato", Role.ALLY).cast("brinicle", Role.VILLAIN)
                .step(q("ember_south_1", "mahina", "hinata_isle").kill("bandit", 3).lesson().reward(120, 300).item(meat(3)).item(SEA_CHART).next("ember_south_2")
                        .site(0, "hinata_raiders", "wilds", "bandit"), ARRIVAL, CAST, PROBLEM)
                .step(q("ember_south_2", "mahina", "hinata_isle").discover("karate_island").reward(250, 500).req("ember_south_1")
                        .next("south_karate_cup"), DISCOVERY)
                .step(q("south_karate_cup", "kiko", "karate_island").discover("karate_island").defeat("kiko").kill("bandit", 3).reward(600, 1000)
                        .item(scroll("tide_grapple")).req("ember_south_2").next("south_freezers").site(2, "karate_ring", "home:kiko,10,0", "bandit"), DISCOVERY)
                .step(q("south_freezers", "gelato", "sorbet_kingdom").discover("sorbet_kingdom").kill("bandit", 5).reward(500, 800).req("south_karate_cup")
                        .next("south_choice").site(1, "sorbet_ice_road", "wilds", "bandit"), ESCALATION)
                .step(q("south_choice", "gelato", "sorbet_kingdom").choice("south_plan").reward(50, 0).req("south_freezers"), DECISION)
                .step(q("south_storm", "gelato", "sorbet_kingdom").talk("kiko").reward(150, 200).req("south_choice").next("south_brinicle"), CLIMAX)
                .step(q("south_sneak", "gelato", "sorbet_kingdom").kill("bandit", 3).reward(250, 400).req("south_choice").next("south_brinicle")
                        .site(0, "sorbet_ice_house", "home:brinicle,-8,10", "bandit"), CLIMAX)
                .step(q("south_brinicle", "gelato", "sorbet_kingdom").defeat("brinicle").reward(700, 1500).rep("island/sorbet_kingdom", 150).req("south_choice")
                        .next("south_sorbet_royal"), CLIMAX)
                .step(q("south_sorbet_royal", "gelato", "sorbet_kingdom").talk("gelato").reward(500, 1500).item(LOG_POSE).req("south_brinicle").next("capes_1"), AFTERMATH)
                .decision("south_plan", "south_choice", "gelato", choice("storm", "south_storm"), choice("frost", "south_sneak"))
                .add();
        Consequences.affects(A, "hinata_isle", "karate_island");
        Consequences.discount(A, "hinata_isle", null, 0.9); // the ice ships call again
        Consequences.discount(A, "sorbet_kingdom", "south_plan=frost", 0.85);
        Consequences.bonus("south_sorbet_royal", "south_plan=frost", 3000, Map.of("island/sorbet_kingdom", 100), List.of(), "arc.grandline.ice_house.bonus.frost");
        Consequences.bonus("south_brinicle", "south_plan=storm", 0, Map.of("island/karate_island", 120), List.of(scroll("tide_grapple")), "arc.grandline.ice_house.bonus.storm");
        Consequences.aid("south_brinicle", "south_plan=storm", "brinicle", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.ice_house.aid.storm");
        StoryMigration.legacy("ember_south_1", "kill:bandit");
        StoryMigration.legacy("south_sorbet_royal", "discover:sorbet_kingdom", "kill:bandit", "talk:gelato");
    }
}
