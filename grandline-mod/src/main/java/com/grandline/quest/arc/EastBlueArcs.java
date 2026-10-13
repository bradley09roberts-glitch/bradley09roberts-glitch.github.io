package com.grandline.quest.arc;

import com.grandline.quest.arc.Arc.Role;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Map;

import static com.grandline.quest.arc.Arc.Beat.*;
import static com.grandline.quest.arc.ArcBuilder.*;

/**
 * Part I of the saga in the East Blue, written as chapters (docs/STORY_OVERHAUL.md): the opening on Shiokaze Isle and
 * in Shells Town, then Orange Town, Syrup Village, the Baratie, Cocoyasi and Loguetown. Text:
 * lang/en_us/story_overhaul.json (chapter 1) and story_overhaul_east.json (chapters 2-6).
 */
final class EastBlueArcs {
    private EastBlueArcs() {}

    static void register() {
        justiceLevy();
        marmaladeSiege();
        clockworkRegatta();
        feastUnderSiege();
        coralKeep();
        beginningsAndEndings();
        // each recruitment step ends by telling the player how to ask (the existing "recruit" option, CrewManager.recruit)
        for (String who : List.of("petra", "carmen", "tomas", "kenji", "kaimon", "odile"))
            Consequences.bonus("recruit_" + who, null, 0, Map.of(), List.of(), "arc.grandline.recruit_hint." + who);
    }

    // ================================================================== Chapter 1 — The Justice Levy (Shiokaze Isle → Shells Town)

    /**
     * The reference chapter. A port's lost supplies (bandits raided Shiokaze's harbour shed; Fumio teaches the player
     * to fight while they get it back) → a missing person (Mio, the supply sloop's skipper, never came home) → an
     * official's scheme (Captain Gallowgate's "Justice Levy" goes into his own strongbox) → a potential crew member
     * (Petra, locked in the base yard for refusing to fire on a crowd) → a rescue (the cell block) → a decision (face
     * Gallowgate in the square, or hand the ledger to his honest lieutenant) → the leader → the port changes → Petra's
     * choice to sail → a lead to Orange Town.
     */
    private static void justiceLevy() {
        String A = "justice_levy";
        arc(A, 1, "east_blue", "shells_town")
                .cast("fumio", Role.GIVER).cast("mio", Role.MISSING).cast("petra", Role.RECRUIT).cast("hayashi", Role.ALLY).cast("gallowgate", Role.VILLAIN)
                .step(q("ember_east_1", "fumio", "shiokaze_isle").kill("bandit", 3).lesson().reward(120, 300).item(meat(3)).item(SEA_CHART).next("ember_east_2")
                        .site(0, "shiokaze_shed_thieves", "wilds", "bandit"), ARRIVAL, CAST, PROBLEM)
                .step(q("ember_east_2", "fumio", "shiokaze_isle").discover("shells_town").reward(250, 500).req("ember_east_1").next("shells_1"), DISCOVERY)
                .step(q("shells_1", "petra", "shells_town").discover("shells_town").kill("marine", 4).reward(400, 800)
                        .rep("civilian", 20).rep("island/shells_town", 80).rep("marine", -40).next("shells_petra")
                        .site(1, "shells_levy_line", "dock", "marine"), DISCOVERY)
                .variant(q("shells_1.marine", "petra", "shells_town").discover("shells_town").talk("resident").kill("bandit", 4).reward(400, 800)
                        .rep("marine", 40).rep("island/shells_town", 80).next("shells_petra")
                        .site(2, "shells_levy_thugs", "dock", "bandit"))
                .step(q("shells_petra", "petra", "shells_town").talk("petra").reward(100, 0).req("shells_1").next("shells_rescue"), ESCALATION)
                .step(q("shells_rescue", "petra", "shells_town").kill("bandit", 3).talk("mio").reward(300, 400).rep("island/shells_town", 40).req("shells_petra")
                        .next("shells_plan").site(0, "shells_cell_block", "home:mio,6,4", "bandit"), ESCALATION)
                .step(q("shells_plan", "petra", "shells_town").choice("shells_plan").reward(50, 0).req("shells_rescue"), DECISION)
                .step(q("shells_square", "petra", "shells_town").kill("marine", 3).reward(250, 300).req("shells_plan").next("shells_2")
                        .site(0, "shells_square", "center", "marine"), CLIMAX)
                .step(q("shells_ledger", "petra", "shells_town").talk("hayashi").reward(150, 0).req("shells_plan").next("shells_2"), CLIMAX)
                .step(q("shells_2", "petra", "shells_town").defeat("gallowgate").reward(700, 1500).bounty(5_000_000)
                        .rep("island/shells_town", 150).rep("civilian", 30).req("shells_plan").next("shells_after"), CLIMAX)
                .variant(q("shells_2.marine", "petra", "shells_town").defeat("gallowgate").reward(700, 3000)
                        .rep("island/shells_town", 150).rep("marine", 100).req("shells_plan").flag("exposed_gallowgate").next("shells_after"))
                .step(q("shells_after", "mio", "shells_town").talk("mio").reward(200, 500).req("shells_2").next("recruit_petra"), AFTERMATH)
                .step(q("recruit_petra", "petra", "shells_town").tag("recruit").talk("petra").reward(300, 0).item(item("bullet", 32)).req("shells_2")
                        .next("orange_1"), AFTERMATH)
                .decision("shells_plan", "shells_plan", "petra", choice("square", "shells_square"), choice("ledger", "shells_ledger"))
                .add();
        // the town that saw Iron-Collar fall in its own square pays you back at the counter; the Marines remember
        Consequences.affects(A, "shiokaze_isle");
        Consequences.discount(A, "shells_town", "shells_plan=square", 0.85);
        Consequences.discount(A, "shiokaze_isle", null, 0.9); // the supply sloop sails full again
        Consequences.bonus("shells_2", "shells_plan=square", 0, Map.of("island/shells_town", 100, "civilian", 20), List.of(), "arc.grandline.justice_levy.bonus.square");
        Consequences.bonus("shells_2", "shells_plan=ledger", 0, Map.of("marine", 80), List.of(cuffs(2)), "arc.grandline.justice_levy.bonus.ledger");
        Consequences.aid("shells_2", "shells_plan=square", "gallowgate", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.justice_levy.aid.square");
        Consequences.aid("shells_2", "shells_plan=ledger", "gallowgate", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.justice_levy.aid.ledger");
        // before the chapters: the bandit step had no lesson, recruitment was a bandit hunt
        StoryMigration.legacy("ember_east_1", "kill:bandit");
        StoryMigration.legacy("recruit_petra", "kill:bandit");
    }

    // ================================================================== Chapter 2 — The Marmalade Siege (Orange Town)

    /**
     * Rotgut's still-house pirates have seized the orchards three days before the Marmalade Festival. Carmen wants her
     * scattered band back; the drummer is chained to a still; the stills are rigged to burn the orchards. Decide: play
     * the festival under Rotgut's nose (Carmen's band plays you through the fight, but his men torch the east orchard),
     * or cut the fuses first (the orchards are saved, the festival waits a year).
     */
    private static void marmaladeSiege() {
        String A = "marmalade_siege";
        arc(A, 2, "east_blue", "orange_town")
                .cast("carmen", Role.RECRUIT).cast("clementine", Role.ALLY).cast("rotgut", Role.VILLAIN)
                .step(q("orange_1", "carmen", "orange_town").discover("orange_town").talk("carmen").reward(300, 600).next("orange_band"), ARRIVAL, CAST)
                .step(q("orange_band", "carmen", "orange_town").talk("resident", 2).kill("bandit", 3).reward(300, 500).req("orange_1").next("orange_still")
                        .site(1, "orange_cannery", "wilds", "bandit"), PROBLEM)
                .step(q("orange_still", "carmen", "orange_town").kill("bandit", 4).reward(350, 600).rep("island/orange_town", 40).req("orange_band").next("orange_choice")
                        .site(0, "orange_stills", "home:rotgut,10,8", "bandit"), DISCOVERY, ESCALATION)
                .step(q("orange_choice", "carmen", "orange_town").choice("orange_plan").reward(50, 0).req("orange_still"), DECISION)
                .step(q("orange_festival", "carmen", "orange_town").talk("clementine").reward(150, 200).req("orange_choice").next("orange_2"), CLIMAX)
                .step(q("orange_fuses", "carmen", "orange_town").kill("bandit", 3).reward(250, 300).req("orange_choice").next("orange_2")
                        .site(0, "orange_fuses", "home:rotgut,-8,12", "bandit"), CLIMAX)
                .step(q("orange_2", "carmen", "orange_town").defeat("rotgut").reward(800, 2000).rep("island/orange_town", 150).rep("civilian", 30)
                        .req("orange_choice").next("recruit_carmen"), CLIMAX)
                .step(q("recruit_carmen", "carmen", "orange_town").tag("recruit").talk("carmen").reward(300, 500).req("orange_2").next("syrup_1"), AFTERMATH)
                .decision("orange_plan", "orange_choice", "carmen", choice("festival", "orange_festival"), choice("fuses", "orange_fuses"))
                .add();
        Consequences.bonus("orange_2", "orange_plan=festival", 0, Map.of("civilian", 40), List.of(), "arc.grandline.marmalade_siege.bonus.festival");
        Consequences.bonus("orange_2", "orange_plan=fuses", 0, Map.of("island/orange_town", 100), List.of(meat(6), vanilla("bread", 12)), "arc.grandline.marmalade_siege.bonus.fuses");
        Consequences.discount(A, "orange_town", "orange_plan=fuses", 0.85);
        Consequences.aid("orange_2", "orange_plan=festival", "rotgut", MobEffects.REGENERATION, 0, 30, "arc.grandline.marmalade_siege.aid.festival");
        StoryMigration.legacy("orange_1", "discover:orange_town", "kill:bandit");
        StoryMigration.legacy("recruit_carmen", "talk:resident", "kill:bandit");
    }

    // ================================================================== Chapter 3 — The Clockwork Regatta (Syrup Village)

    /**
     * Gizmo the Tinker Pirate stole Tomas's racing sloop and seeded the regatta course with clockwork sharks. Decide:
     * talk Tomas into taking the helm again (he finds his nerve and his sloop cuts across the bay during the fight), or
     * jam the sharks' winding keys first (the children's race is safe and the village owes you).
     */
    private static void clockworkRegatta() {
        String A = "clockwork_regatta";
        arc(A, 3, "east_blue", "syrup_village")
                .cast("tomas", Role.RECRUIT).cast("gizmo", Role.VILLAIN)
                .step(q("syrup_1", "tomas", "syrup_village").discover("syrup_village").talk("tomas").reward(350, 600).next("syrup_sharks"), ARRIVAL, CAST)
                .step(q("syrup_sharks", "tomas", "syrup_village").kill("bandit", 4).reward(300, 500).req("syrup_1").next("syrup_workshop")
                        .site(0, "syrup_dock", "dock", "bandit"), PROBLEM)
                .step(q("syrup_workshop", "tomas", "syrup_village").kill("bandit", 3).reward(350, 600).rep("island/syrup_village", 40).req("syrup_sharks")
                        .next("syrup_choice").site(0, "syrup_workshop", "home:gizmo,6,6", "bandit"), DISCOVERY, ESCALATION)
                .step(q("syrup_choice", "tomas", "syrup_village").choice("syrup_helm").reward(50, 0).req("syrup_workshop"), DECISION)
                .step(q("syrup_race", "tomas", "syrup_village").talk("tomas").reward(150, 200).req("syrup_choice").next("syrup_2"), CLIMAX)
                .step(q("syrup_keys", "tomas", "syrup_village").kill("bandit", 3).reward(250, 300).req("syrup_choice").next("syrup_2")
                        .site(0, "syrup_keys", "home:gizmo,-6,8", "bandit"), CLIMAX)
                .step(q("syrup_2", "tomas", "syrup_village").defeat("gizmo").reward(900, 2500).rep("island/syrup_village", 150).req("syrup_choice")
                        .next("recruit_tomas"), CLIMAX)
                .step(q("recruit_tomas", "tomas", "syrup_village").tag("recruit").talk("tomas").reward(400, 500).item(SEA_CHART).req("syrup_2").next("baratie_1"), AFTERMATH)
                .decision("syrup_helm", "syrup_choice", "tomas", choice("helm", "syrup_race"), choice("keys", "syrup_keys"))
                .add();
        Consequences.bonus("syrup_2", "syrup_helm=helm", 0, Map.of("island/syrup_village", 60), List.of(LOG_POSE), "arc.grandline.clockwork_regatta.bonus.helm");
        Consequences.bonus("syrup_2", "syrup_helm=keys", 1500, Map.of("island/syrup_village", 120, "civilian", 30), List.of(), "arc.grandline.clockwork_regatta.bonus.keys");
        Consequences.discount(A, "syrup_village", "syrup_helm=keys", 0.85);
        Consequences.aid("syrup_2", "syrup_helm=helm", "gizmo", MobEffects.MOVEMENT_SPEED, 1, 40, "arc.grandline.clockwork_regatta.aid.helm");
        StoryMigration.legacy("syrup_1", "discover:syrup_village", "kill:bandit");
        StoryMigration.legacy("recruit_tomas", "discover:mirror_ball_island");
    }

    // ================================================================== Chapter 4 — A Feast Under Siege (the Baratie)

    /**
     * The Gourmand's glutton fleet besieges the floating restaurant for its recipe book, whose last page is the old
     * owner's chart of the East Blue's supply routes. Decide: serve Fosco a forged book (Kenji's plan: his crew dines on
     * it and Kenji fights at your side; the Baratie keeps its book and its specials) or let off-duty Vice Admiral
     * Horatio's squadron take the fleet (his bodyguards still stand in the way; the book goes to the Marines as
     * evidence and Horatio pays the bill).
     */
    private static void feastUnderSiege() {
        String A = "feast_under_siege";
        arc(A, 4, "east_blue", "baratie")
                .cast("kenji", Role.RECRUIT).cast("horatio", Role.ALLY).cast("fosco", Role.VILLAIN)
                .step(q("baratie_1", "kenji", "baratie").discover("baratie").talk("kenji").reward(400, 800).next("baratie_boarders"), ARRIVAL, CAST)
                .variant(q("baratie_1.marine", "kenji", "baratie").discover("baratie").talk("horatio").talk("kenji").reward(400, 1500).rep("marine", 60).next("baratie_boarders"))
                .step(q("baratie_boarders", "kenji", "baratie").kill("bandit", 5).reward(400, 700).req("baratie_1").next("baratie_book")
                        .site(0, "baratie_deck", "center", "bandit"), PROBLEM)
                .step(q("baratie_book", "kenji", "baratie").talk("horatio").reward(250, 300).req("baratie_boarders").next("baratie_choice"), DISCOVERY, ESCALATION)
                .step(q("baratie_choice", "kenji", "baratie").choice("baratie_plan").reward(50, 0).req("baratie_book"), DECISION)
                .step(q("baratie_feast", "kenji", "baratie").talk("fosco").reward(150, 200).req("baratie_choice").next("baratie_2"), CLIMAX)
                .step(q("baratie_guard", "kenji", "baratie").kill("bandit", 3).reward(250, 300).req("baratie_choice").next("baratie_2")
                        .site(0, "baratie_deck", "center", "bandit"), CLIMAX)
                .step(q("baratie_2", "kenji", "baratie").defeat("fosco").reward(1000, 3000).rep("island/baratie", 150).rep("civilian", 40).item(steak(2))
                        .req("baratie_choice").next("recruit_kenji"), CLIMAX)
                .step(q("recruit_kenji", "kenji", "baratie").tag("recruit").talk("kenji").reward(400, 800).req("baratie_2").next("cocoyasi_1"), AFTERMATH)
                .decision("baratie_plan", "baratie_choice", "kenji", choice("forgery", "baratie_feast"), choice("squadron", "baratie_guard"))
                .add();
        Consequences.stock(A, "baratie", "baratie_plan=forgery", "grandline:pirates_feast", 1, 900);
        Consequences.stock(A, "baratie", "baratie_plan=forgery", "grandline:sea_king_hotpot", 1, 1400);
        Consequences.bonus("baratie_2", "baratie_plan=forgery", 0, Map.of("island/baratie", 80), List.of(item("saltfire_curry", 2)), "arc.grandline.feast_under_siege.bonus.forgery");
        Consequences.bonus("baratie_2", "baratie_plan=squadron", 2500, Map.of("marine", 80), List.of(), "arc.grandline.feast_under_siege.bonus.squadron");
        Consequences.aid("baratie_2", "baratie_plan=forgery", "fosco", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.feast_under_siege.aid.forgery");
        Consequences.aid("baratie_2", "baratie_plan=squadron", "fosco", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.feast_under_siege.aid.squadron");
        StoryMigration.legacy("baratie_1", "discover:baratie", "kill:bandit");
        StoryMigration.legacy("baratie_1.marine", "discover:baratie", "talk:horatio", "kill:bandit");
        StoryMigration.legacy("recruit_kenji", "talk:horatio", "kill:bandit");
    }

    // ================================================================== Chapter 5 — The Coral Keep (Cocoyasi)

    /**
     * Gorgo Hammerjaw walled the waterfront into a fortress and taxes every tangerine; Kaimon, one of his own, defected
     * when Gorgo took the village children as "tax collateral". Decide: fight at Kaimon's side at the Keep's gate (the
     * village sees a Fish-Man stand for them) or rally the villagers to hold the seawall themselves (the village finds
     * its own courage, but still looks at Kaimon like a shark).
     */
    private static void coralKeep() {
        String A = "coral_keep";
        arc(A, 5, "east_blue", "cocoyasi")
                .cast("kaimon", Role.RECRUIT).cast("gorgo", Role.VILLAIN)
                .step(q("cocoyasi_1", "kaimon", "cocoyasi").discover("cocoyasi").kill("fishman_guard", 2).reward(700, 1500).next("coco_grove")
                        .site(1, "coco_keep_gate", "home:gorgo,-24,8", "fishman_guard"), ARRIVAL, CAST, PROBLEM)
                .step(q("coco_grove", "kaimon", "cocoyasi").talk("resident", 2).reward(300, 500).req("cocoyasi_1").next("coco_cellar"), DISCOVERY)
                .step(q("coco_cellar", "kaimon", "cocoyasi").kill("fishman_guard", 3).reward(500, 800).rep("island/cocoyasi", 60).req("coco_grove").next("coco_choice")
                        .site(0, "coco_cellar", "home:gorgo,8,-12", "fishman_guard"), ESCALATION)
                .step(q("coco_choice", "kaimon", "cocoyasi").choice("coco_plan").reward(50, 0).req("coco_cellar"), DECISION)
                .step(q("coco_gate", "kaimon", "cocoyasi").kill("fishman_brute", 2).reward(400, 500).req("coco_choice").next("cocoyasi_2")
                        .site(0, "coco_keep_gate", "home:gorgo,-24,8", "fishman_brute"), CLIMAX)
                .step(q("coco_seawall", "kaimon", "cocoyasi").talk("resident", 3).reward(250, 300).req("coco_choice").next("cocoyasi_2"), CLIMAX)
                .step(q("cocoyasi_2", "kaimon", "cocoyasi").defeat("gorgo").reward(1400, 4000).rep("island/cocoyasi", 300).rep("civilian", 60)
                        .item(pose("loguetown")).req("coco_choice").next("recruit_kaimon"), CLIMAX)
                .step(q("recruit_kaimon", "kaimon", "cocoyasi").tag("recruit").talk("kaimon").reward(500, 500).req("cocoyasi_2").next("logue_1"), AFTERMATH)
                .decision("coco_plan", "coco_choice", "kaimon", choice("side_by_side", "coco_gate"), choice("seawall", "coco_seawall"))
                .add();
        Consequences.bonus("cocoyasi_2", "coco_plan=side_by_side", 0, Map.of("island/cocoyasi", 80), List.of(scroll("fishman_karate")), "arc.grandline.coral_keep.bonus.side_by_side");
        Consequences.bonus("cocoyasi_2", "coco_plan=seawall", 2000, Map.of("island/cocoyasi", 150, "civilian", 40), List.of(), "arc.grandline.coral_keep.bonus.seawall");
        Consequences.discount(A, "cocoyasi", "coco_plan=seawall", 0.85);
        Consequences.aid("cocoyasi_2", "coco_plan=side_by_side", "gorgo", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.coral_keep.aid.side_by_side");
        StoryMigration.legacy("cocoyasi_2", "kill:fishman_brute", "defeat:gorgo");
        StoryMigration.legacy("recruit_kaimon", "talk:resident", "kill:bandit");
    }

    // ================================================================== Chapter 6 — The Town of Beginnings and Endings (Loguetown)

    /**
     * Every road in the East Blue ends at the platform where the Saltfire King died. Odile's stolen almanac pages say a
     * storm will break at midnight on the anniversary, and Captain Valka means to close the harbour on every rookie in
     * it. Decide: stand on the platform when the Saltfire rises (the Gazette's photo of the decade, and Valka marks you
     * as her rival) or get the market under cover before the storm (the town owes you; Valka owes you a grudging nod).
     * The climax keeps its five faction versions.
     */
    private static void beginningsAndEndings() {
        String A = "beginnings_endings";
        arc(A, 6, "east_blue", "loguetown")
                .cast("odile", Role.RECRUIT).cast("bartolo", Role.ALLY).cast("valka", Role.RIVAL).cast("tobias", Role.RIVAL).cast("pim", Role.ALLY)
                .step(q("logue_1", "odile", "loguetown").discover("loguetown").talk("bartolo").reward(800, 2000).next("logue_odile"), ARRIVAL, CAST)
                .step(q("logue_odile", "odile", "loguetown").talk("odile").reward(200, 300).req("logue_1").next("logue_pages"), PROBLEM)
                .step(q("logue_pages", "odile", "loguetown").kill("bandit", 5).reward(500, 800).req("logue_odile").next("logue_choice")
                        .site(0, "logue_alleys", "home:odile,18,10", "bandit"), DISCOVERY, ESCALATION)
                .step(q("logue_choice", "odile", "loguetown").choice("logue_plan").reward(50, 0).req("logue_pages"), DECISION)
                .step(q("logue_platform", "odile", "loguetown").talk("bartolo").reward(200, 300).req("logue_choice").next("logue_2"), CLIMAX)
                .step(q("logue_shelter", "odile", "loguetown").talk("resident", 3).reward(250, 400).rep("island/loguetown", 60).req("logue_choice").next("logue_2"), CLIMAX)
                .step(q("logue_2", "odile", "loguetown").defeat("tobias").kill("marine", 5).reward(1500, 4000).bounty(10_000_000)
                        .rep("pirate", 100).req("logue_choice").next("recruit_odile").site(1, "logue_platform_watch", "center", "marine"), CLIMAX)
                .variant(q("logue_2.marine", "valka", "loguetown").defeat("tobias").kill("bandit", 5).reward(1500, 6000).rep("marine", 150)
                        .req("logue_choice").next("recruit_odile").site(1, "logue_alleys", "home:odile,18,10", "bandit"))
                .variant(q("logue_2.revolutionary", "pim", "loguetown").talk("pim").kill("marine", 3).reward(1500, 3000).bounty(6_000_000)
                        .rep("revolutionary", 150).req("logue_choice").next("recruit_odile").site(1, "logue_platform_watch", "center", "marine"))
                .variant(q("logue_2.bounty_hunter", "odile", "loguetown").defeat("tobias").kill("bandit", 5).reward(1500, 8000).rep("bounty_hunter", 150)
                        .req("logue_choice").next("recruit_odile").site(1, "logue_alleys", "home:odile,18,10", "bandit"))
                .variant(q("logue_2.civilian", "bartolo", "loguetown").talk("valka").talk("tobias").talk("pim").reward(1500, 5000).rep("civilian", 150)
                        .req("logue_choice").next("recruit_odile"))
                .step(q("recruit_odile", "odile", "loguetown").tag("recruit").talk("odile").reward(500, 500).item(SEA_CHART).req("logue_2").next("logue_3"), AFTERMATH)
                .step(q("logue_3", "odile", "loguetown").discover("twin_capes").reward(2000, 3000).item(LOG_POSE).req("logue_2").flag("saw_the_saltfire")
                        .next("capes_1"), AFTERMATH)
                .decision("logue_plan", "logue_choice", "odile", choice("platform", "logue_platform"), choice("shelter", "logue_shelter"))
                .add();
        Consequences.bonus("logue_2", "logue_plan=platform", 0, Map.of("pirate", 40, "civilian", 20), List.of(item("wanted_poster", 1)), "arc.grandline.beginnings_endings.bonus.platform");
        Consequences.bonus("logue_2", "logue_plan=shelter", 3000, Map.of("island/loguetown", 150, "civilian", 60), List.of(), "arc.grandline.beginnings_endings.bonus.shelter");
        Consequences.discount(A, "loguetown", "logue_plan=shelter", 0.9);
        Consequences.aid("logue_2", "logue_plan=platform", "tobias", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.beginnings_endings.aid.platform");
        StoryMigration.legacy("recruit_odile", "kill:bandit");
    }
}
