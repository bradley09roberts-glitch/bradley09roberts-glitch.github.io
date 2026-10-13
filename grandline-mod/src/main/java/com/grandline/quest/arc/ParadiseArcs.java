package com.grandline.quest.arc;

import com.grandline.quest.arc.Arc.Role;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Map;

import static com.grandline.quest.arc.Arc.Beat.*;
import static com.grandline.quest.arc.ArcBuilder.*;

/**
 * Part II of the saga, the Paradise Gauntlet, written as chapters: the Twin Capes, Whisky Peak, Little Garden, Drum
 * Island and Alabasta. Text: lang/en_us/story_overhaul_paradise.json. (Jaya onwards keep their earlier shape for now
 * and are listed for the journal in {@link LaterArcs}.)
 */
final class ParadiseArcs {
    private ParadiseArcs() {}

    static void register() {
        upwardMountain();
        gildedWelcome();
        bakeOff();
        panacea();
        saltWells();
        for (String who : List.of("ignatz", "hilde", "pippa", "sefa"))
            Consequences.bonus("recruit_" + who, null, 0, Map.of(), List.of(), "arc.grandline.recruit_hint." + who);
    }

    // ================================================================== Chapter 7 — The Mountain That Flows Upward (Twin Capes)

    /**
     * The lighthouse at the foot of Reverse Mountain is dark and ships are breaking on the capes. Its keeper's sister,
     * the Lamplighter, didn't write this year — and the wreckers' haul holds her letter, opened by the World Government:
     * "don't light the lamp for me this year, they're watching it". Decide: light it anyway (the rookies come down safe;
     * whoever watches knows someone still looks for her) or keep it dark as she asked (Barnacle hunts in the dark; she
     * stays hidden).
     */
    private static void upwardMountain() {
        String A = "upward_mountain";
        arc(A, 7, "paradise", "twin_capes")
                .cast("wilhelmina", Role.GIVER).cast("orrin", Role.VILLAIN)
                .step(q("capes_1", "wilhelmina", "twin_capes").talk("wilhelmina").kill("pirate", 3).reward(2000, 3000).item(LOG_POSE).next("capes_wreck")
                        .site(1, "capes_beach", "dock", "pirate"), ARRIVAL, CAST, PROBLEM)
                .step(q("capes_wreck", "wilhelmina", "twin_capes").reach("capes_wreck", "wilds").reward(600, 800).req("capes_1").next("capes_letter"), DISCOVERY)
                .step(q("capes_letter", "wilhelmina", "twin_capes").talk("wilhelmina").reward(300, 0).req("capes_wreck").next("capes_choice"), ESCALATION)
                .step(q("capes_choice", "wilhelmina", "twin_capes").choice("capes_plan").reward(100, 0).req("capes_letter"), DECISION)
                .step(q("capes_light", "wilhelmina", "twin_capes").kill("pirate", 3).reward(600, 800).req("capes_choice").next("capes_orrin")
                        .site(0, "capes_lighthouse", "home:wilhelmina,6,6", "pirate"), CLIMAX)
                .step(q("capes_dark", "wilhelmina", "twin_capes").reach("capes_rocks", "home:orrin,10,-10").reward(500, 600).req("capes_choice").next("capes_orrin"), CLIMAX)
                .step(q("capes_orrin", "wilhelmina", "twin_capes").defeat("orrin").reward(1200, 2000).rep("island/twin_capes", 100).req("capes_choice")
                        .next("capes_2"), CLIMAX)
                .step(q("capes_2", "wilhelmina", "twin_capes").discover("whisky_peak").reward(1500, 2000).req("capes_1").next("whisky_1"), AFTERMATH)
                .decision("capes_plan", "capes_choice", "wilhelmina", choice("lamp", "capes_light"), choice("dark", "capes_dark"))
                .add();
        Consequences.bonus("capes_orrin", "capes_plan=lamp", 2500, Map.of("island/twin_capes", 100), List.of(), "arc.grandline.upward_mountain.bonus.lamp");
        Consequences.bonus("capes_orrin", "capes_plan=dark", 0, Map.of(), List.of(pose("whisky_peak")), "arc.grandline.upward_mountain.bonus.dark");
        Consequences.aid("capes_orrin", "capes_plan=lamp", "orrin", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.upward_mountain.aid.lamp");
        Consequences.aid("capes_orrin", "capes_plan=dark", "orrin", MobEffects.NIGHT_VISION, 0, 90, "arc.grandline.upward_mountain.aid.dark");
    }

    // ================================================================== Chapter 8 — The Gilded Welcome (Whisky Peak)

    /**
     * Whisky Peak robs its guests at midnight for the Gilded Ledger, and the Ledger's bookkeeper has found a column he
     * can't live with: salt delivered to Alabasta's wells. Decide: give the books to Hugo's Hunters' Lodge (every honest
     * hunter reads them and the Lodge fights beside you; the Ledger knows) or let the Ledger think they burned and carry
     * copies to Alabasta (no help tonight; the copies seal the case there).
     */
    private static void gildedWelcome() {
        String A = "gilded_welcome";
        arc(A, 8, "paradise", "whisky_peak")
                .cast("hugo", Role.GIVER).cast("ignatz", Role.RECRUIT).cast("doubloon", Role.VILLAIN).cast("farthing", Role.VILLAIN)
                .step(q("whisky_1", "hugo", "whisky_peak").discover("whisky_peak").talk("hugo").reward(1500, 3000).next("whisky_party"), ARRIVAL, CAST)
                .variant(q("whisky_1.bounty_hunter", "hugo", "whisky_peak").discover("whisky_peak").talk("hugo").reward(1500, 5000).rep("bounty_hunter", 150)
                        .item(cuffs(4)).next("whisky_party"))
                .step(q("whisky_party", "hugo", "whisky_peak").kill("pirate", 6).reward(1200, 2500).req("whisky_1").next("whisky_ignatz")
                        .site(0, "whisky_party", "center", "pirate"), PROBLEM)
                .step(q("whisky_ignatz", "hugo", "whisky_peak").talk("ignatz").reward(400, 500).req("whisky_party").next("whisky_hunt"), DISCOVERY)
                .step(q("whisky_hunt", "hugo", "whisky_peak").kill("pirate", 3).reward(800, 1200).req("whisky_ignatz").next("whisky_choice")
                        .site(0, "whisky_alley", "home:ignatz,10,6", "pirate"), ESCALATION)
                .step(q("whisky_choice", "hugo", "whisky_peak").choice("whisky_plan").reward(100, 0).req("whisky_hunt"), DECISION)
                .step(q("whisky_lodge", "hugo", "whisky_peak").talk("hugo").reward(400, 500).req("whisky_choice").next("whisky_2"), CLIMAX)
                .step(q("whisky_copy", "hugo", "whisky_peak").kill("pirate", 3).reward(700, 1000).req("whisky_choice").next("whisky_2")
                        .site(0, "whisky_counting_house", "home:doubloon,12,0", "pirate"), CLIMAX)
                .step(q("whisky_2", "hugo", "whisky_peak").defeat("doubloon").defeat("farthing").reward(3500, 8000).rep("island/whisky_peak", 150)
                        .req("whisky_1").next("recruit_ignatz"), CLIMAX)
                .step(q("recruit_ignatz", "ignatz", "whisky_peak").tag("recruit").talk("ignatz").reward(1200, 2000).req("whisky_2").next("garden_1"), AFTERMATH)
                .decision("whisky_plan", "whisky_choice", "ignatz", choice("lodge", "whisky_lodge"), choice("copy", "whisky_copy"))
                .add();
        Consequences.bonus("whisky_2", "whisky_plan=lodge", 0, Map.of("bounty_hunter", 100), List.of(cuffs(2)), "arc.grandline.gilded_welcome.bonus.lodge");
        Consequences.bonus("whisky_2", "whisky_plan=copy", 5000, Map.of(), List.of(), "arc.grandline.gilded_welcome.bonus.copy");
        Consequences.aid("whisky_2", "whisky_plan=lodge", "doubloon", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.gilded_welcome.aid.lodge");
        StoryMigration.legacy("whisky_1", "discover:whisky_peak", "kill:pirate");
        StoryMigration.legacy("whisky_1.bounty_hunter", "discover:whisky_peak", "talk:hugo", "kill:pirate");
        StoryMigration.legacy("recruit_ignatz", "kill:pirate");
    }

    // ================================================================== Chapter 9 — The Seventy-Year Bake-Off (Little Garden)

    /**
     * Two giant grandmothers have cooked against each other for seventy years; whoever's dish makes the other cry wins.
     * The Ledger's Mr. Shilling means to seal both in wax during the final tasting. Decide: Little Hilde cooks a third
     * dish that makes both cry (the duel ends after seventy years) or the tasting goes on under your guard (the duel goes
     * on, as they like it).
     */
    private static void bakeOff() {
        String A = "bake_off";
        arc(A, 9, "paradise", "little_garden")
                .cast("astrid", Role.GIVER).cast("ylva", Role.ALLY).cast("hilde", Role.RECRUIT).cast("shilling", Role.VILLAIN)
                .step(q("garden_1", "astrid", "little_garden").discover("little_garden").talk("astrid").reward(1500, 2500).next("garden_ylva"), ARRIVAL, CAST)
                .step(q("garden_ylva", "astrid", "little_garden").talk("ylva").reward(600, 800).req("garden_1").next("garden_pantry"), PROBLEM)
                .step(q("garden_pantry", "astrid", "little_garden").kill("pirate", 5).reward(1200, 2000).req("garden_ylva").next("garden_hilde")
                        .site(0, "garden_pantry", "home:hilde,10,8", "pirate"), DISCOVERY)
                .step(q("garden_hilde", "astrid", "little_garden").talk("hilde").reward(400, 500).req("garden_pantry").next("garden_choice"), ESCALATION)
                .step(q("garden_choice", "astrid", "little_garden").choice("garden_plan").reward(100, 0).req("garden_hilde"), DECISION)
                .step(q("garden_dish", "astrid", "little_garden").talk("astrid").talk("ylva").reward(600, 800).req("garden_choice").next("garden_2"), CLIMAX)
                .step(q("garden_guard", "astrid", "little_garden").kill("pirate", 3).reward(800, 1000).req("garden_choice").next("garden_2")
                        .site(0, "garden_duel_ground", "home:astrid,14,-6", "pirate"), CLIMAX)
                .step(q("garden_2", "astrid", "little_garden").defeat("shilling").reward(4000, 9000).rep("island/little_garden", 200).item(steak(4))
                        .req("garden_1").next("recruit_hilde"), CLIMAX)
                .step(q("recruit_hilde", "hilde", "little_garden").tag("recruit").talk("hilde").reward(1500, 1000).req("garden_2").next("drum_1"), AFTERMATH)
                .decision("garden_plan", "garden_choice", "hilde", choice("third_dish", "garden_dish"), choice("guard", "garden_guard"))
                .add();
        Consequences.bonus("garden_2", "garden_plan=third_dish", 0, Map.of(), List.of(steak(8)), "arc.grandline.bake_off.bonus.third_dish");
        Consequences.bonus("garden_2", "garden_plan=guard", 4000, Map.of("island/little_garden", 150), List.of(meat(16)), "arc.grandline.bake_off.bonus.guard");
        Consequences.aid("garden_2", "garden_plan=third_dish", "shilling", MobEffects.REGENERATION, 0, 30, "arc.grandline.bake_off.aid.third_dish");
        Consequences.aid("garden_2", "garden_plan=guard", "shilling", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.bake_off.aid.guard");
        StoryMigration.legacy("garden_1", "discover:little_garden", "talk:ylva", "kill:pirate");
        StoryMigration.legacy("recruit_hilde", "talk:astrid", "kill:pirate");
    }

    // ================================================================== Chapter 10 — Panacea (Drum Island)

    /**
     * Drum Island is hooked on a tonic that keeps it just sick enough to keep buying; the real doctor was run out of
     * town. Decide: let the rookie Raven smoke the quack out (quick, and she fights beside you, but the castle's real
     * medicine burns) or take the medicine first, Pippa's way (slower, harder; the village is cured that night).
     */
    private static void panacea() {
        String A = "panacea";
        arc(A, 10, "paradise", "drum_island")
                .cast("pippa", Role.RECRUIT).cast("raven", Role.RIVAL).cast("panacea", Role.VILLAIN)
                .step(q("drum_1", "pippa", "drum_island").discover("drum_island").talk("pippa").reward(1800, 3000).next("drum_rounds"), ARRIVAL, CAST)
                .step(q("drum_rounds", "pippa", "drum_island").talk("resident", 2).reward(600, 800).req("drum_1").next("drum_raven"), PROBLEM)
                .step(q("drum_raven", "pippa", "drum_island").talk("raven").reward(500, 800).req("drum_rounds").next("drum_peddlers"), DISCOVERY)
                .step(q("drum_peddlers", "pippa", "drum_island").kill("pirate", 4).reward(1200, 1800).req("drum_raven").next("drum_choice")
                        .site(0, "drum_peddlers", "center", "pirate"), ESCALATION)
                .step(q("drum_choice", "pippa", "drum_island").choice("drum_plan").reward(100, 0).req("drum_peddlers"), DECISION)
                .step(q("drum_smoke", "pippa", "drum_island").talk("raven").reward(500, 600).req("drum_choice").next("drum_2"), CLIMAX)
                .step(q("drum_cure", "pippa", "drum_island").kill("pirate", 3).reward(900, 1200).req("drum_choice").next("drum_2")
                        .site(0, "drum_castle_stores", "home:panacea,12,8", "pirate"), CLIMAX)
                .step(q("drum_2", "pippa", "drum_island").defeat("panacea").reward(5000, 10000).rep("island/drum_island", 200).rep("civilian", 50)
                        .req("drum_1").next("recruit_pippa"), CLIMAX)
                .step(q("recruit_pippa", "pippa", "drum_island").tag("recruit").talk("pippa").reward(1500, 1000).req("drum_2").next("alabasta_1"), AFTERMATH)
                .decision("drum_plan", "drum_choice", "pippa", choice("smoke", "drum_smoke"), choice("cure", "drum_cure"))
                .add();
        Consequences.bonus("drum_2", "drum_plan=smoke", 0, Map.of("pirate", 40), List.of(steak(2)), "arc.grandline.panacea.bonus.smoke");
        Consequences.bonus("drum_2", "drum_plan=cure", 0, Map.of("island/drum_island", 100), List.of(), "arc.grandline.panacea.bonus.cure");
        Consequences.discount(A, "drum_island", "drum_plan=cure", 0.85);
        Consequences.aid("drum_2", "drum_plan=smoke", "panacea", MobEffects.DAMAGE_BOOST, 0, 40, "arc.grandline.panacea.aid.smoke");
        StoryMigration.legacy("drum_1", "discover:drum_island", "talk:raven", "kill:pirate");
        StoryMigration.legacy("recruit_pippa", "talk:resident", "kill:pirate");
    }

    // ================================================================== Chapter 11 — Salt in the Wells (Alabasta)

    /**
     * The Gilded Ledger salts Alabasta's oases so the king must buy water; its President, the Warlord Saltmarsh, wants
     * the poneglyph under the palace. On the night he strikes, the palace spring and the vault can't both be held.
     * Decide: the spring (the capital keeps its water; the stone is salted to dust) or the stone (the poneglyph survives;
     * the king spends ten years greening a salted spring). The climax keeps its four faction versions.
     */
    private static void saltWells() {
        String A = "salt_wells";
        arc(A, 11, "paradise", "alabasta")
                .cast("sefa", Role.RECRUIT).cast("rashid", Role.ALLY).cast("celestine", Role.VILLAIN).cast("saltmarsh", Role.BOSS)
                .step(q("alabasta_1", "sefa", "alabasta").discover("alabasta").talk("rashid").kill("pirate", 6).reward(4000, 8000).next("alabasta_stone")
                        .site(2, "alabasta_wells", "home:rashid,40,30", "pirate"), ARRIVAL, CAST, PROBLEM)
                .step(q("alabasta_stone", "sefa", "alabasta").talk("sefa").reward(800, 1000).req("alabasta_1").next("alabasta_2"), DISCOVERY)
                .step(q("alabasta_2", "sefa", "alabasta").defeat("celestine").reward(5000, 12000).bounty(30_000_000).req("alabasta_1").next("alabasta_choice"), ESCALATION)
                .variant(q("alabasta_2.marine", "rashid", "alabasta").defeat("celestine").talk("rashid").reward(5000, 15000).rep("marine", 150)
                        .req("alabasta_1").next("alabasta_choice"))
                .variant(q("alabasta_2.revolutionary", "sefa", "alabasta").talk("rashid").kill("pirate", 8).reward(5000, 8000).bounty(20_000_000)
                        .rep("revolutionary", 200).req("alabasta_1").next("alabasta_choice").site(1, "alabasta_oasis_towns", "home:rashid,-30,60", "pirate"))
                .step(q("alabasta_choice", "sefa", "alabasta").choice("alabasta_plan").reward(200, 0).req("alabasta_2"), DECISION)
                .step(q("alabasta_spring", "sefa", "alabasta").kill("pirate", 4).reward(1500, 2000).req("alabasta_choice").next("alabasta_3")
                        .site(0, "alabasta_spring", "home:rashid,12,-14", "pirate"), CLIMAX)
                .step(q("alabasta_vault", "sefa", "alabasta").kill("pirate", 4).reward(1500, 2000).req("alabasta_choice").next("alabasta_3")
                        .site(0, "alabasta_vault", "home:sefa,10,10", "pirate"), CLIMAX)
                .step(q("alabasta_3", "sefa", "alabasta").defeat("saltmarsh").reward(8000, 20000).bounty(80_000_000).rep("island/alabasta", 300).rep("civilian", 80)
                        .req("alabasta_2").flag("saltmarsh_toppled").next("recruit_sefa"), CLIMAX)
                .variant(q("alabasta_3.marine", "rashid", "alabasta").defeat("saltmarsh").reward(8000, 30000).rep("island/alabasta", 300).rep("marine", 250)
                        .req("alabasta_2").flag("saltmarsh_toppled").next("recruit_sefa"))
                .variant(q("alabasta_3.bounty_hunter", "sefa", "alabasta").defeat("saltmarsh").reward(8000, 60000).rep("island/alabasta", 300).rep("bounty_hunter", 200)
                        .req("alabasta_2").flag("saltmarsh_toppled").next("recruit_sefa"))
                .variant(q("alabasta_3.civilian", "rashid", "alabasta").talk("rashid").kill("pirate", 6).reward(8000, 15000).rep("island/alabasta", 300).rep("civilian", 200)
                        .req("alabasta_2").flag("saltmarsh_toppled").next("recruit_sefa").site(1, "alabasta_caravans", "home:rashid,-40,-30", "pirate"))
                .step(q("recruit_sefa", "sefa", "alabasta").tag("recruit").poneglyph(1).talk("rashid").reward(2500, 2000).req("alabasta_2").next("jaya_1")
                        .site(0, "alabasta_poneglyph", "home:sefa,10,10", null), AFTERMATH)
                .decision("alabasta_plan", "alabasta_choice", "sefa", choice("spring", "alabasta_spring"), choice("stone", "alabasta_vault"))
                .add();
        Consequences.bonus("alabasta_3", "alabasta_plan=spring", 0, Map.of("island/alabasta", 100, "civilian", 40), List.of(), "arc.grandline.salt_wells.bonus.spring");
        Consequences.bonus("alabasta_3", "alabasta_plan=stone", 0, Map.of(), List.of(RUBBING), "arc.grandline.salt_wells.bonus.stone");
        // the Whisky Peak choice pays off here: the copied books seal the case against the Ledger
        Consequences.bonus("alabasta_3", "whisky_plan=copy", 10000, Map.of("marine", 50), List.of(), "arc.grandline.salt_wells.bonus.books");
        Consequences.discount(A, "alabasta", "alabasta_plan=spring", 0.85);
        Consequences.aid("alabasta_3", "alabasta_plan=spring", "saltmarsh", MobEffects.REGENERATION, 0, 30, "arc.grandline.salt_wells.aid.spring");
        Consequences.aid("alabasta_3", "alabasta_plan=stone", "saltmarsh", MobEffects.DAMAGE_RESISTANCE, 0, 40, "arc.grandline.salt_wells.aid.stone");
    }
}
