package com.grandline.quest.arc;

import com.grandline.quest.arc.Arc.Role;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Map;

import static com.grandline.quest.arc.Arc.Beat.*;
import static com.grandline.quest.arc.ArcBuilder.*;

/**
 * The second half of Part II, the road to Sabaody, written as chapters: Jaya, Long Ring Long Land, Water 7, Enies
 * Lobby, Thriller Bark and the Sabaody Archipelago. Text: lang/en_us/story_overhaul_sabaody.json. (The two branches
 * into the other seas, Skypiea above Jaya and Fish-Man Island below Sabaody, keep their earlier shape and are listed
 * for the journal in {@link LaterArcs}.)
 */
final class SabaodyRoadArcs {
    private SabaodyRoadArcs() {}

    static void register() {
        debtAndSky();
        tugOfTides();
        threeGuilds();
        gatesOfJustice();
        mirrorMonger();
        auctionOfSouls();
        for (String who : List.of("aria", "gunnar", "kanna", "nimbus"))
            Consequences.bonus("recruit_" + who, null, 0, Map.of(), List.of(), "arc.grandline.recruit_hint." + who);
    }

    // ================================================================== Chapter 12 — The Debt and the Sky (Jaya)

    /**
     * Everyone in Jaya owes Usurio Shakkin, including Aria, the Sky People girl who fell to the Blue Sea at six and has
     * been building balloons to go home ever since: her "debt" was signed in her name when she was six. Decide what to
     * do with his ledgers: burn every one (the whole town is free; the thief Felicity, who wanted them, goes her own way)
     * or let Felicity steal them for her cut (she fights beside you and pays you, but a cat now holds the town's debts).
     */
    private static void debtAndSky() {
        String A = "debt_and_sky";
        arc(A, 12, "paradise", "jaya")
                .cast("aria", Role.RECRUIT).cast("felicity", Role.RIVAL).cast("usurio", Role.VILLAIN)
                .step(q("jaya_1", "aria", "jaya").discover("jaya").talk("aria").reward(2500, 4000).next("jaya_collectors"), ARRIVAL, CAST)
                .step(q("jaya_collectors", "aria", "jaya").kill("pirate", 5).reward(1200, 2000).req("jaya_1").next("jaya_felicity")
                        .site(0, "jaya_workshop", "home:aria,-10,-8", "pirate"), PROBLEM)
                .step(q("jaya_felicity", "aria", "jaya").talk("felicity").reward(500, 800).req("jaya_collectors").next("jaya_envelope"), DISCOVERY)
                .step(q("jaya_envelope", "aria", "jaya").kill("pirate", 4).reward(1000, 1500).req("jaya_felicity").next("jaya_choice")
                        .site(0, "jaya_counting_house", "home:usurio,14,10", "pirate"), ESCALATION)
                .step(q("jaya_choice", "aria", "jaya").choice("jaya_plan").reward(100, 0).req("jaya_envelope"), DECISION)
                .step(q("jaya_bonfire", "aria", "jaya").kill("pirate", 3).reward(800, 1000).req("jaya_choice").next("jaya_2")
                        .site(0, "jaya_ledger_vault", "home:usurio,-12,10", "pirate"), CLIMAX)
                .step(q("jaya_heist", "aria", "jaya").reach("jaya_back_door", "home:usurio,-14,-12").reward(700, 800).req("jaya_choice").next("jaya_2"), CLIMAX)
                .step(q("jaya_2", "aria", "jaya").defeat("usurio").reward(5000, 12000).rep("island/jaya", 150).req("jaya_1").next("recruit_aria"), CLIMAX)
                .step(q("recruit_aria", "aria", "jaya").tag("recruit").talk("aria").reward(1500, 2000).req("jaya_2").next("ring_1"), AFTERMATH)
                .decision("jaya_plan", "jaya_choice", "felicity", choice("burn", "jaya_bonfire"), choice("deal", "jaya_heist"))
                .add();
        Consequences.bonus("jaya_2", "jaya_plan=burn", 0, Map.of("island/jaya", 150, "civilian", 40), List.of(), "arc.grandline.debt_and_sky.bonus.burn");
        Consequences.bonus("jaya_2", "jaya_plan=deal", 8000, Map.of(), List.of(item("impact_dial", 1)), "arc.grandline.debt_and_sky.bonus.deal");
        Consequences.discount(A, "jaya", "jaya_plan=burn", 0.85);
        Consequences.aid("jaya_2", "jaya_plan=burn", "usurio", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.debt_and_sky.aid.burn");
        Consequences.aid("jaya_2", "jaya_plan=deal", "usurio", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.debt_and_sky.aid.deal");
        StoryMigration.legacy("jaya_1", "discover:jaya", "talk:felicity", "kill:pirate");
        StoryMigration.legacy("recruit_aria", "kill:pirate", "discover:long_ring_long_land");
    }

    // ================================================================== Chapter 14 — Tug-of-Tides (Long Ring Long Land)

    /**
     * The nomads' Tug-of-Tides: three rounds, crew against crew, and the stake is the loser's flag. Ayame "Thousand
     * Knots" has won three years running by tying the rope to a stake, and holds the Long Ring flag, the herds and the
     * oath of its champion Gunnar. Decide, before the final: expose the stake to the whole camp (her old wins are void
     * and the herds go home, but she brings her whole crew into the ring) or say nothing and let Gunnar pull it at night
     * (she loses fairly with her pride intact, and pays her side bet; the herds stay hers).
     */
    private static void tugOfTides() {
        String A = "tug_of_tides";
        String I = "long_ring_long_land";
        arc(A, 14, "paradise", I)
                .cast("gunnar", Role.RECRUIT).cast("ayame", Role.VILLAIN)
                .step(q("ring_1", "gunnar", I).discover(I).talk("gunnar").reward(2000, 3000).next("ring_round1"), ARRIVAL, CAST, PROBLEM)
                .step(q("ring_round1", "gunnar", I).kill("pirate", 4).reward(1000, 1500).req("ring_1").next("ring_knots")
                        .site(0, "ring_field", "home:gunnar,-24,26", "pirate"), PROBLEM)
                .step(q("ring_knots", "gunnar", I).reach("ring_stake", "home:ayame,-16,10").reward(600, 800).req("ring_round1").next("ring_pledge"), DISCOVERY)
                .step(q("ring_pledge", "gunnar", I).talk("gunnar").reward(300, 0).req("ring_knots").next("ring_choice"), ESCALATION)
                .step(q("ring_choice", "gunnar", I).choice("ring_plan").reward(100, 0).req("ring_pledge"), DECISION)
                .step(q("ring_expose", "gunnar", I).kill("pirate", 3).reward(800, 1000).req("ring_choice").next("ring_final")
                        .site(0, "ring_field", "home:gunnar,-24,26", "pirate"), CLIMAX)
                .step(q("ring_quiet", "gunnar", I).reach("ring_stake", "home:ayame,-16,10").reward(600, 800).req("ring_choice").next("ring_final"), CLIMAX)
                .step(q("ring_final", "gunnar", I).defeat("ayame").reward(4000, 8000).rep("island/" + I, 150).req("ring_1").next("recruit_gunnar"), CLIMAX)
                .step(q("recruit_gunnar", "gunnar", I).tag("recruit").talk("ayame").reward(1500, 1000).req("ring_final").next("water7_1"), AFTERMATH)
                .decision("ring_plan", "ring_choice", "gunnar", choice("expose", "ring_expose"), choice("quiet", "ring_quiet"))
                .add();
        Consequences.bonus("ring_final", "ring_plan=expose", 0, Map.of("island/" + I, 150), List.of(meat(8)), "arc.grandline.tug_of_tides.bonus.expose");
        Consequences.bonus("ring_final", "ring_plan=quiet", 4000, Map.of(), List.of(item("whip", 1)), "arc.grandline.tug_of_tides.bonus.quiet");
        Consequences.discount(A, I, "ring_plan=expose", 0.85);
        Consequences.aid("ring_final", "ring_plan=expose", "ayame", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.tug_of_tides.aid.expose");
        Consequences.aid("ring_final", "ring_plan=quiet", "ayame", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.tug_of_tides.aid.quiet");
        StoryMigration.legacy("ring_1", "discover:" + I, "defeat:ayame", "kill:pirate");
    }

    // ================================================================== Chapter 15 — The Three Guilds (Water 7)

    /**
     * Someone tried to put a spike through Guildmaster Hollis's head, and the city blames the Dovetails, the guild it
     * banned years ago for a sabotaged keel. The attackers are Cipher Pol agents inside his own guild, after the
     * blueprints of an ancient warship in his safe. Decide: burn the blueprints in the forge (nobody ever builds it; the
     * guilds stand with you) or let Kanna hide them in a keel (a ship's plans inside a ship: you sail with them, and the
     * agents come at you alone).
     */
    private static void threeGuilds() {
        String A = "three_guilds";
        String I = "water_7";
        arc(A, 15, "paradise", I)
                .cast("hollis", Role.GIVER).cast("kanna", Role.RECRUIT).cast("matthias", Role.RIVAL).cast("nimue", Role.VILLAIN).cast("brick", Role.VILLAIN)
                .step(q("water7_1", "hollis", I).discover(I).talk("hollis").reward(2500, 5000).next("water7_kanna"), ARRIVAL, CAST, PROBLEM)
                .step(q("water7_kanna", "hollis", I).talk("kanna").reward(600, 800).req("water7_1").next("water7_matthias"), PROBLEM)
                .step(q("water7_matthias", "hollis", I).talk("matthias").reward(600, 800).req("water7_kanna").next("water7_yard"), DISCOVERY)
                .step(q("water7_yard", "hollis", I).kill("pirate", 5).reward(1500, 2500).req("water7_matthias").next("water7_choice")
                        .site(0, "water7_dovetail_yard", "home:kanna,10,8", "pirate"), ESCALATION)
                .step(q("water7_choice", "hollis", I).choice("water7_plan").reward(100, 0).req("water7_yard"), DECISION)
                .step(q("water7_burn", "hollis", I).reach("water7_forge", "home:hollis,6,-10").reward(600, 800).req("water7_choice").next("water7_2"), CLIMAX)
                .step(q("water7_hide", "hollis", I).reach("water7_dry_dock", "home:kanna,-14,6").reward(600, 800).req("water7_choice").next("water7_2"), CLIMAX)
                .step(q("water7_2", "hollis", I).defeat("nimue").defeat("brick").reward(6000, 15000).bounty(40_000_000).req("water7_1").next("water7_3"), CLIMAX)
                .variant(q("water7_2.marine", "hollis", I).defeat("nimue").defeat("brick").reward(6000, 20000).rep("marine", 200).req("water7_1").next("water7_3"))
                .step(q("water7_3", "hollis", I).talk("kanna").reward(3000, 6000).rep("island/" + I, 200).req("water7_2").next("recruit_kanna"), AFTERMATH)
                .step(q("recruit_kanna", "kanna", I).tag("recruit").talk("hollis").reward(1500, 1500).req("water7_2").next("enies_1"), AFTERMATH)
                .decision("water7_plan", "water7_choice", "hollis", choice("burn", "water7_burn"), choice("hide", "water7_hide"))
                .add();
        Consequences.bonus("water7_2", "water7_plan=burn", 0, Map.of("island/" + I, 150), List.of(), "arc.grandline.three_guilds.bonus.burn");
        Consequences.bonus("water7_2", "water7_plan=hide", 6000, Map.of(), List.of(item("war_hammer", 1)), "arc.grandline.three_guilds.bonus.hide");
        Consequences.discount(A, I, "water7_plan=burn", 0.85);
        Consequences.aid("water7_2", "water7_plan=burn", "nimue", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.three_guilds.aid.burn");
        Consequences.aid("water7_2", "water7_plan=burn", "brick", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.three_guilds.aid.burn");
        StoryMigration.legacy("water7_1", "discover:" + I, "talk:kanna", "talk:matthias");
        StoryMigration.legacy("water7_3", "talk:kanna", "kill:pirate");
        StoryMigration.legacy("recruit_kanna", "talk:hollis", "kill:pirate");
    }

    // ================================================================== Chapter 16 — The Gates of Justice (Enies Lobby)

    /**
     * Cipher Pol has taken its prisoners, Kanna's father among them, to the island of law, to be sent through the Gates
     * of Justice at dawn; Director Hanzaki has asked for a Buster Call to tidy up, with the island's own clerks still on
     * it. The trainee Nimbus has been told to fire on the prisoners if anyone tries to free them. Decide: go up the tower
     * for Hanzaki at once (Nimbus delays the convoy alone) or stop the convoy at the bridge with her first (the prisoners
     * are safe sooner; Hanzaki gets time to start the Buster Call). The climax and the escape keep their faction versions.
     */
    private static void gatesOfJustice() {
        String A = "gates_of_justice";
        String I = "enies_lobby";
        arc(A, 16, "paradise", I)
                .cast("nimbus", Role.RECRUIT).cast("kanna", Role.ALLY).cast("gregor", Role.VILLAIN).cast("dmitri", Role.RIVAL)
                .step(q("enies_1", "nimbus", I).discover(I).kill("cp_agent", 3).reward(4000, 6000).bounty(20_000_000).next("enies_nimbus")
                        .site(1, "enies_front_gate", "dock", "cp_agent"), ARRIVAL, PROBLEM)
                .variant(q("enies_1.marine", "dmitri", I).discover(I).talk("dmitri").kill("pirate", 6).reward(4000, 8000).rep("marine", 100).next("enies_nimbus")
                        .site(2, "enies_pier_pirates", "home:dmitri,-24,16", "pirate"))
                .step(q("enies_nimbus", "nimbus", I).talk("nimbus").reward(600, 800).req("enies_1").next("enies_buster"), CAST, DISCOVERY)
                .step(q("enies_buster", "nimbus", I).reach("enies_bridge", "at:200,-80").reward(800, 1000).req("enies_nimbus").next("enies_choice"), ESCALATION)
                .step(q("enies_choice", "nimbus", I).choice("enies_plan").reward(100, 0).req("enies_buster"), DECISION)
                .step(q("enies_tower", "nimbus", I).kill("cp_agent", 3).reward(1200, 2000).req("enies_choice").next("enies_2")
                        .site(0, "enies_tower_stairs", "home:gregor,8,-10", "cp_agent"), CLIMAX)
                .step(q("enies_convoy", "nimbus", I).kill("cp_agent", 3).reward(1200, 2000).req("enies_choice").next("enies_2")
                        .site(0, "enies_convoy", "at:300,-120", "cp_agent"), CLIMAX)
                .step(q("enies_2", "nimbus", I).defeat("gregor").reward(8000, 15000).bounty(60_000_000).req("enies_1").next("enies_3"), CLIMAX)
                .variant(q("enies_2.marine", "dmitri", I).talk("nimbus").talk("dmitri").kill("pirate", 6).reward(8000, 20000).rep("marine", 250)
                        .flag("chose_evacuation").req("enies_1").next("enies_3").site(2, "enies_pier_pirates", "home:dmitri,-24,16", "pirate"))
                .step(q("enies_3", "nimbus", I).kill("marine", 6).reward(6000, 12000).bounty(40_000_000).req("enies_2").next("recruit_nimbus")
                        .site(0, "enies_buster_line", "home:dmitri,-20,10", "marine"), AFTERMATH)
                .variant(q("enies_3.marine", "dmitri", I).talk("dmitri").kill("pirate", 6).reward(6000, 16000).rep("marine", 200).req("enies_2").next("recruit_nimbus")
                        .site(1, "enies_pier_pirates", "home:dmitri,-24,16", "pirate"))
                .variant(q("enies_3.revolutionary", "nimbus", I).kill("marine", 6).reward(6000, 12000).bounty(30_000_000).rep("revolutionary", 250)
                        .req("enies_2").next("recruit_nimbus").site(0, "enies_buster_line", "home:dmitri,-20,10", "marine"))
                .variant(q("enies_3.bounty_hunter", "nimbus", I).kill("pirate", 6).reward(6000, 24000).rep("bounty_hunter", 200).req("enies_2").next("recruit_nimbus")
                        .site(0, "enies_pier_pirates", "home:dmitri,-24,16", "pirate"))
                .variant(q("enies_3.civilian", "nimbus", I).talk("nimbus").talk("dmitri").reward(6000, 10000).rep("civilian", 200).req("enies_2").next("recruit_nimbus"))
                .step(q("recruit_nimbus", "nimbus", I).tag("recruit").kill("cp_agent", 2).reward(3000, 3000).req("enies_2").next("thriller_1")
                        .site(0, "enies_old_squad", "home:nimbus,10,10", "cp_agent"), AFTERMATH)
                .decision("enies_plan", "enies_choice", "nimbus", choice("tower", "enies_tower"), choice("convoy", "enies_convoy"))
                .add();
        Consequences.bonus("enies_2", "enies_plan=tower", 10000, Map.of(), List.of(cuffs(2)), "arc.grandline.gates_of_justice.bonus.tower");
        Consequences.bonus("enies_2", "enies_plan=convoy", 0, Map.of("civilian", 80, "island/water_7", 150), List.of(), "arc.grandline.gates_of_justice.bonus.convoy");
        // the Water 7 choice pays off here: the plans Hanzaki came for were in your keel all along
        Consequences.bonus("enies_2", "water7_plan=hide", 0, Map.of("revolutionary", 60), List.of(scroll("rokushiki")), "arc.grandline.gates_of_justice.bonus.keel");
        Consequences.aid("enies_2", "enies_plan=tower", "gregor", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.gates_of_justice.aid.tower");
        Consequences.aid("enies_2", "enies_plan=convoy", "gregor", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.gates_of_justice.aid.convoy");
    }

    // ================================================================== Chapter 17 — The Mirror-Monger (Thriller Bark)

    /**
     * A fog-bound island where the Warlord Countess Kagamiya steals the reflections of passing sailors and makes them
     * her soldiers; the rival Mortimer's crew lost theirs, and in the fog so did you. At dawn she sails, and whoever has
     * no reflection then never gets it back. Decide how to reach her: Mortimer's way, under the graveyard into her crypt
     * (a surprise; her oldest soldiers guard it) or the front door at first light, loud, so every sailor without a
     * reflection sees you go in and follows.
     */
    private static void mirrorMonger() {
        String A = "mirror_monger";
        String I = "thriller_bark";
        arc(A, 17, "paradise", I)
                .cast("mortimer", Role.GIVER).cast("lucrezia", Role.VILLAIN)
                .step(q("thriller_1", "mortimer", I).discover(I).talk("mortimer").reward(3000, 5000).next("thriller_soldiers"), ARRIVAL, CAST)
                .step(q("thriller_soldiers", "mortimer", I).kill("pirate", 6).reward(2000, 3000).req("thriller_1").next("thriller_mirrors")
                        .site(0, "thriller_graveyard", "home:mortimer,14,10", "pirate"), PROBLEM)
                .step(q("thriller_mirrors", "mortimer", I).reach("thriller_hall_of_mirrors", "home:lucrezia,-16,6").reward(800, 1000).req("thriller_soldiers")
                        .next("thriller_dawn"), DISCOVERY)
                .step(q("thriller_dawn", "mortimer", I).talk("mortimer").reward(300, 0).req("thriller_mirrors").next("thriller_choice"), ESCALATION)
                .step(q("thriller_choice", "mortimer", I).choice("thriller_plan").reward(100, 0).req("thriller_dawn"), DECISION)
                .step(q("thriller_crypt", "mortimer", I).kill("pirate", 3).reward(900, 1200).req("thriller_choice").next("thriller_2")
                        .site(0, "thriller_crypt", "home:mortimer,-12,16", "pirate"), CLIMAX)
                .step(q("thriller_gate", "mortimer", I).reach("thriller_gate", "at:0,-30").reward(700, 800).req("thriller_choice").next("thriller_2"), CLIMAX)
                .step(q("thriller_2", "mortimer", I).defeat("lucrezia").reward(7000, 18000).bounty(70_000_000).rep("civilian", 80).req("thriller_1").next("thriller_3"), CLIMAX)
                .variant(q("thriller_2.marine", "mortimer", I).defeat("lucrezia").reward(7000, 25000).rep("marine", 200).req("thriller_1").next("thriller_3"))
                .step(q("thriller_3", "mortimer", I).talk("mortimer").reward(1500, 2000).req("thriller_2").next("sabaody_1"), AFTERMATH)
                .decision("thriller_plan", "thriller_choice", "mortimer", choice("graves", "thriller_crypt"), choice("front", "thriller_gate"))
                .add();
        Consequences.bonus("thriller_2", "thriller_plan=graves", 5000, Map.of(), List.of(item("anchor_chain", 1)), "arc.grandline.mirror_monger.bonus.graves");
        Consequences.bonus("thriller_2", "thriller_plan=front", 0, Map.of("civilian", 100, "pirate", 40), List.of(), "arc.grandline.mirror_monger.bonus.front");
        Consequences.aid("thriller_2", "thriller_plan=graves", "lucrezia", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.mirror_monger.aid.graves");
        Consequences.aid("thriller_2", "thriller_plan=front", "lucrezia", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.mirror_monger.aid.front");
        StoryMigration.legacy("thriller_1", "discover:" + I, "kill:pirate");
    }

    // ================================================================== Chapter 18 — The Auction of Souls (Sabaody)

    /**
     * Every rookie crew arrives at once; the bubble-coater Ume needs three days to coat your ship for the deep, and
     * tomorrow the Fence's auction sells a Mink child and Coralie's sister to a World Noble. Decide: break them out by
     * force (the noble gets punched; everyone claims it) or buy them back with the crews' pooled money and cut the
     * collars outside (no riot, and the auction is beaten at its own game). Either way the auctioneer fights, and then the
     * Admiral lands: the Lamplighter's hiding place has been sold.
     */
    private static void auctionOfSouls() {
        String A = "auction_of_souls";
        String I = "sabaody_archipelago";
        arc(A, 18, "paradise", I)
                .cast("ume", Role.GIVER).cast("coralie", Role.ALLY).cast("edison", Role.RIVAL).cast("augustin", Role.VILLAIN).cast("pompadour", Role.VILLAIN)
                .cast("zenith", Role.RIVAL)
                .step(q("sabaody_1", "ume", I).discover(I).talk("coralie").talk("edison").reward(4000, 8000).next("sabaody_ume"), ARRIVAL, CAST)
                .step(q("sabaody_ume", "ume", I).talk("ume").reward(800, 1000).req("sabaody_1").next("sabaody_catchers"), PROBLEM)
                .step(q("sabaody_catchers", "ume", I).kill("pirate", 5).reward(2000, 3000).req("sabaody_ume").next("sabaody_auction")
                        .site(0, "sabaody_catchers", "home:coralie,-20,30", "pirate"), DISCOVERY)
                .step(q("sabaody_auction", "ume", I).reach("sabaody_auction_house", "at:260,160").reward(800, 1000).req("sabaody_catchers").next("sabaody_choice"), ESCALATION)
                .step(q("sabaody_choice", "ume", I).choice("sabaody_plan").reward(100, 0).req("sabaody_auction"), DECISION)
                .step(q("sabaody_riot", "ume", I).kill("pirate", 4).reward(1500, 2000).req("sabaody_choice").next("sabaody_2")
                        .site(0, "sabaody_auction_floor", "at:252,150", "pirate"), CLIMAX)
                .step(q("sabaody_bid", "ume", I).talk("edison").talk("coralie").reward(1200, 0).req("sabaody_choice").next("sabaody_2"), CLIMAX)
                .step(q("sabaody_2", "coralie", I).defeat("augustin").kill("pirate", 6).reward(8000, 18000).bounty(60_000_000).rep("civilian", 100)
                        .req("sabaody_1").next("sabaody_3").site(1, "sabaody_auction_guards", "home:augustin,12,8", "pirate"), CLIMAX)
                .variant(q("sabaody_2.marine", "zenith", I).talk("zenith").talk("pompadour").kill("pirate", 8).reward(8000, 25000).rep("marine", 250)
                        .req("sabaody_1").next("sabaody_3").site(2, "sabaody_rioters", "at:240,180", "pirate"))
                .variant(q("sabaody_2.civilian", "coralie", I).talk("coralie").talk("ume").kill("pirate", 6).reward(8000, 14000).rep("civilian", 250)
                        .req("sabaody_1").next("sabaody_3").site(2, "sabaody_rioters", "at:240,180", "pirate"))
                .step(q("sabaody_3", "ume", I).kill("marine", 8).talk("ume").reward(9000, 12000).bounty(50_000_000).req("sabaody_2").flag("lamplighter_captured")
                        .next("lily_1").site(0, "sabaody_admiral_landing", "dock", "marine"), AFTERMATH)
                .variant(q("sabaody_3.marine", "zenith", I).talk("zenith").kill("pirate", 8).reward(9000, 20000).rep("marine", 200).req("sabaody_2")
                        .flag("lamplighter_captured").next("lily_1").site(1, "sabaody_rioters", "at:240,180", "pirate"))
                .decision("sabaody_plan", "sabaody_choice", "coralie", choice("punch", "sabaody_riot"), choice("buy", "sabaody_bid"))
                .add();
        Consequences.bonus("sabaody_2", "sabaody_plan=punch", 0, Map.of("pirate", 80, "revolutionary", 80), List.of(), "arc.grandline.auction_of_souls.bonus.punch");
        Consequences.bonus("sabaody_2", "sabaody_plan=buy", 0, Map.of("civilian", 120, "island/" + I, 150), List.of(), "arc.grandline.auction_of_souls.bonus.buy");
        Consequences.discount(A, I, "sabaody_plan=buy", 0.85);
        Consequences.aid("sabaody_2", "sabaody_plan=punch", "augustin", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.auction_of_souls.aid.punch");
        Consequences.aid("sabaody_2", "sabaody_plan=buy", "augustin", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.auction_of_souls.aid.buy");
    }
}
