package com.grandline.quest.arc;

import com.grandline.quest.arc.Arc.Role;

import static com.grandline.quest.arc.Arc.Beat.*;
import static com.grandline.quest.arc.ArcBuilder.listed;

/**
 * Chapters whose quests keep their earlier shape (StoryQuests / StoryQuestsExtra): the two branches into the other seas
 * (Skypiea above Jaya, Fish-Man Island below Sabaody), the Calm Belt, the war and the New World. They are listed here so
 * the journal can show their chapter, cast, problem and recap; they have no scenes, decisions or consequences yet.
 * Text: lang/en_us/story_overhaul_later.json.
 *
 * <p>Cast roles here only protect people who are never fought (a giver or ally yields instead of dying); everyone who is
 * fought anywhere is listed as a rival or villain, which changes nothing in a listed chapter.
 */
final class LaterArcs {
    private LaterArcs() {}

    static void register() {
        // ---- Part II, the branches into the other seas
        listed("storm_throne", 13, "sky", "angel_island")
                .cast("amarante", Role.GIVER).cast("ferris", Role.ALLY).cast("lumi", Role.ALLY).cast("wakiza", Role.GIVER).cast("ayasha", Role.ALLY)
                .cast("isidore", Role.GIVER).cast("pinwheel", Role.ALLY).cast("ilse", Role.VILLAIN).cast("garamond", Role.VILLAIN).cast("lulla", Role.VILLAIN)
                .cast("oswin", Role.VILLAIN).cast("cirrus", Role.BOSS)
                .existing("sky_1", ARRIVAL).existing("sky_2", CAST, PROBLEM).existing("sky_3", DISCOVERY).existing("sky_4", ESCALATION)
                .existing("sky_5", DISCOVERY).existing("sky_6", CLIMAX).existing("sky_7", AFTERMATH).existing("sky_8", AFTERMATH)
                .add();
        listed("lantern_abyss", 19, "deep", "fishman_island")
                .cast("perla", Role.GIVER).cast("marisol", Role.GIVER).cast("sawara", Role.GIVER).cast("nereo", Role.GIVER).cast("tidwell", Role.GIVER)
                .cast("okoze", Role.VILLAIN).cast("lorcan", Role.BOSS)
                .existing("fmi_1", ARRIVAL).existing("fmi_2", CAST).existing("fmi_3", PROBLEM).existing("fmi_4", DISCOVERY).existing("fmi_5", ESCALATION)
                .existing("fmi_6", CLIMAX).existing("fmi_7", AFTERMATH).existing("fmi_8", AFTERMATH)
                .add();
        // ---- the Calm Belt and the war (Part III)
        listed("jade_camellia", 20, "calm_belt", "amazon_lily").cast("hisui", Role.RIVAL).existing("lily_1", ARRIVAL, CLIMAX).add();
        listed("hermits_silence", 21, "calm_belt", "rusukaina").cast("tsukumo", Role.GIVER).existing("rusukaina_1", ARRIVAL, AFTERMATH).add();
        listed("descent", 22, "calm_belt", "impel_down").cast("grimsby", Role.RIVAL).existing("impel_1", ARRIVAL, CLIMAX).add();
        listed("lamplighter_execution", 23, "war", "marineford")
                .cast("haruka", Role.MISSING).cast("osric", Role.RIVAL).cast("constance", Role.RIVAL).cast("hrolf", Role.ALLY)
                .existing("war_1", ARRIVAL, PROBLEM).existing("war_2", CLIMAX).existing("war_3", AFTERMATH)
                .add();
        listed("last_blade", 24, "war", "kuraigana_island").cast("jinpachi", Role.RIVAL).existing("kuraigana_1", ARRIVAL, CLIMAX).add();
        // ---- the New World (Part IV)
        listed("counterfeit_flame", 25, "new_world", "punk_hazard")
                .cast("seraphine", Role.GIVER).cast("agathe", Role.ALLY).cast("hesper", Role.VILLAIN)
                .existing("nw_gate_1", ARRIVAL).existing("punk_1", CLIMAX)
                .add();
        listed("marionette", 26, "new_world", "dressrosa").cast("florian", Role.VILLAIN).existing("dress_1", ARRIVAL, PROBLEM).existing("dress_2", CLIMAX).add();
        listed("tiny_tyrant", 27, "new_world", "green_bit")
                .cast("bolt", Role.RECRUIT).cast("pipistrello", Role.VILLAIN).existing("greenbit_1", ARRIVAL, CLIMAX).existing("recruit_bolt", AFTERMATH)
                .add();
        listed("thunder_mane", 28, "new_world", "zou")
                .cast("leonhart", Role.RIVAL).cast("pashmina", Role.RECRUIT).existing("zou_1", ARRIVAL, CLIMAX).existing("recruit_pashmina", AFTERMATH)
                .add();
        listed("midnight_carnival", 29, "new_world", "whole_cake_island")
                .cast("fennimore", Role.VILLAIN).cast("marzipan", Role.VILLAIN).cast("barnum", Role.BOSS)
                .existing("cake_1", ARRIVAL, PROBLEM).existing("cake_2", CLIMAX)
                .add();
        listed("flower_capital", 30, "new_world", "wano_country")
                .cast("rin", Role.RECRUIT).cast("hagane", Role.VILLAIN).existing("wano_1", ARRIVAL, CLIMAX).existing("recruit_rin", AFTERMATH)
                .add();
        listed("krakens_wager", 31, "new_world", "onigashima")
                .cast("rin", Role.RECRUIT).cast("gorou", Role.VILLAIN).cast("tsubaki", Role.VILLAIN).cast("kurogane", Role.BOSS)
                .existing("oni_1", ARRIVAL, ESCALATION).existing("oni_2", CLIMAX)
                .add();
        listed("genius_and_shadow", 32, "new_world", "egghead")
                .cast("minerva", Role.GIVER).cast("elowen", Role.RECRUIT).cast("vespera", Role.VILLAIN)
                .existing("egghead_1", ARRIVAL, CLIMAX).existing("recruit_elowen", AFTERMATH)
                .add();
        listed("green_dawn", 33, "new_world", "baltigo").cast("sigrun", Role.GIVER).cast("lysander", Role.RIVAL).cast("prudence", Role.GIVER)
                .existing("baltigo_1", ARRIVAL, CAST).add();
        listed("fifth_emperor", 34, "new_world", "hachinosu").cast("sterling", Role.BOSS).existing("hachinosu_1", ARRIVAL, CLIMAX).add();
        listed("giants_hearth", 35, "new_world", "elbaph").cast("sigrid", Role.RIVAL).cast("hrolf", Role.ALLY).existing("elbaph_1", ARRIVAL, CLIMAX).add();
        listed("unmoving_star", 36, "new_world", "lodestar_island").cast("asagao", Role.RIVAL).cast("aurelia", Role.RIVAL)
                .existing("lodestar_1", ARRIVAL, CLIMAX).add();
        listed("saltfire", 37, "new_world", "laugh_tale").cast("nox", Role.BOSS).cast("juniper", Role.ALLY).cast("bartolo", Role.ALLY)
                .existing("laughtale_1", ARRIVAL, CLIMAX).existing("laughtale_2", AFTERMATH)
                .add();
    }
}
