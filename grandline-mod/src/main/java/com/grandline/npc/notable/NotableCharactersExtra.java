package com.grandline.npc.notable;

import com.grandline.npc.notable.NotableCharacters.Disposition;
import com.grandline.npc.notable.NotableCharacters.Group;
import com.grandline.player.Faction;

import static com.grandline.npc.notable.NotableCharacters.add;
import static com.grandline.npc.notable.NotableCharacters.n;

/**
 * The rest of the Saltfire Saga cast (docs/STORY.md): the Sky People of the White Sea (the Skypiea arc), the court
 * and enemies of Fish-Man Island (the Fish-Man Island arc), and one islander for every canon island that had no
 * story of its own. Sky and deep-sea characters live in those dimensions' plans; {@link NotableSpawner} spawns them
 * when a player is near, like everyone else. Quests: {@code quest/StoryQuestsExtra.java}; text:
 * {@code lang/en_us/story_extra.json}.
 */
final class NotableCharactersExtra {
    static void register() {
        final Group RULER = Group.RULER, VIL = Group.VILLAIN, PATRON = Group.PATRON;
        final Faction PIRATE = Faction.PIRATE, CIV = Faction.CIVILIAN, REVF = Faction.REVOLUTIONARY, WILD = Faction.WILD;
        final Disposition FRIENDLY = Disposition.FRIENDLY, RIVAL = Disposition.RIVAL, VILLAIN = Disposition.VILLAIN;

        // ---------------- The White Sea: Skypiea (grandline:sky) ----------------
        add(n("amarante", PATRON, CIV, "keeper", 5, "heavens_gate", 0, 16).race("sky_people").haki(60, 10, 0).disp(FRIENDLY));
        add(n("ferris", PATRON, CIV, "merchant", 4, "angel_island", 16, -60).race("sky_people").haki(40, 10, 0).disp(FRIENDLY));
        add(n("lumi", PATRON, CIV, "canal_guide", 4, "angel_island", 40, 30).style("storm_dance").weapon("bo_staff").race("sky_people").haki(55, 10, 0).disp(FRIENDLY));
        add(n("wakiza", RULER, CIV, "master", 7, "upper_yard", -60, 70).style("storm_dance").weapon("steel_spear").race("sky_people").haki(70, 60, 0).disp(FRIENDLY));
        add(n("ayasha", RULER, CIV, "elder", 6, "upper_yard", 120, -74).style("life_return").race("sky_people").haki(80, 20, 0).disp(FRIENDLY));
        // the "god" and the four priests of the trials (original fruits and dials)
        add(n("cirrus", VIL, WILD, "master", 9, "upper_yard", 160, 120).fruit("tenki").style("storm_dance").weapon("weather_staff").race("sky_people")
                .haki(92, 70, 0).disp(VILLAIN));
        add(n("ilse", VIL, WILD, "ruin_guardian", 7, "upper_yard", 200, -220).fruit("wana").style("storm_dance").weapon("spear").race("sky_people").haki(70, 40, 0).disp(VILLAIN));
        add(n("garamond", VIL, WILD, "ruin_guardian", 7, "upper_yard", -180, -160).fruit("kusari").style("anchor_brawl").weapon("anchor_chain").race("sky_people")
                .haki(60, 60, 0).disp(VILLAIN));
        add(n("lulla", VIL, WILD, "ruin_guardian", 7, "upper_yard", 60, 250).fruit("nemu").weapon("bo_staff").race("sky_people").haki(75, 40, 0).disp(VILLAIN));
        add(n("oswin", VIL, WILD, "ruin_guardian", 7, "upper_yard", -250, 178).style("storm_dance").weapon("impact_dial").race("sky_people").haki(70, 55, 0).disp(VILLAIN));
        // Weatheria's scholars
        add(n("isidore", PATRON, CIV, "scholar", 5, "weatheria", 4, 15).race("sky_people").haki(50, 10, 0).disp(FRIENDLY));
        add(n("pinwheel", PATRON, CIV, "scholar", 5, "weatheria", -14, 6).weapon("weather_staff").race("sky_people").haki(45, 20, 0).disp(FRIENDLY));

        // ---------------- The deep sea: Fish-Man Island (grandline:deep_sea) ----------------
        add(n("nereo", RULER, CIV, "noble", 8, "fishman_island", 2, -24).style("fishman_jujutsu").weapon("war_trident").race("merfolk").scale(1.6f)
                .haki(70, 70, 30).disp(FRIENDLY));
        add(n("marisol", RULER, CIV, "noble", 4, "fishman_island", -8, -20).race("merfolk").haki(60, 10, 20).disp(FRIENDLY));
        add(n("sawara", PATRON, CIV, "guard", 8, "fishman_island", 6, -4).style("ittoryu").weapon("fine_cutlass").race("fishman").haki(65, 70, 0).disp(FRIENDLY));
        add(n("perla", PATRON, CIV, "bartender", 4, "fishman_island", 150, 86).race("merfolk").haki(40, 10, 0).disp(FRIENDLY));
        add(n("lorcan", VIL, PIRATE, "fishman_boss", 9, "fishman_island", -150, -110).style("fishman_karate").weapon("war_trident").race("fishman")
                .haki(70, 85, 0).disp(VILLAIN));
        add(n("okoze", VIL, PIRATE, "fishman_brute", 7, "fishman_island", -128, -126).fruit("kusuri").style("fishman_karate").weapon("knife").race("fishman")
                .haki(50, 55, 0).disp(VILLAIN));
        add(n("tidwell", PATRON, CIV, "keeper", 6, "sea_forest", -8, 10).style("fishman_karate").race("fishman").haki(60, 40, 0).disp(FRIENDLY));

        // ---------------- Islanders of the canon islands that had no story yet ----------------
        // East Blue
        add(n("wren", PATRON, CIV, "bartender", 2, "dawn_island", 20, 240).disp(FRIENDLY));
        add(n("ren", PATRON, CIV, "master", 5, "shimotsuki_village", 30, 52).style("ittoryu").weapon("katana").haki(40, 40, 0).disp(FRIENDLY));
        add(n("fern", PATRON, CIV, "scholar", 2, "island_of_rare_animals", 25, 25).haki(20, 0, 0).disp(FRIENDLY));
        // North Blue
        add(n("agnes", PATRON, CIV, "priest", 3, "flevance", 0, 90).style("life_return").haki(30, 10, 0).disp(FRIENDLY));
        add(n("sigvald", PATRON, CIV, "elder", 3, "minion_island", 150, 100).weapon("musket").style("sniper").haki(30, 10, 0).disp(FRIENDLY));
        add(n("nell", PATRON, CIV, "dockworker", 2, "spider_miles", -60, 70).weapon("slingshot").style("sniper").disp(FRIENDLY));
        // South Blue
        add(n("kestrel", PATRON, CIV, "elder", 3, "torino_kingdom", 30, 40).race("stormwing").haki(30, 10, 0).disp(FRIENDLY));
        // Paradise
        add(n("livia", PATRON, CIV, "merchant", 4, "san_faldo", 15, 25).weapon("rapier").haki(20, 10, 0).disp(FRIENDLY));
        add(n("rosalind", PATRON, CIV, "merchant", 3, "st_poplar", 15, -20).race("vinefolk").disp(FRIENDLY));
        add(n("gustave", PATRON, CIV, "head_chef", 4, "pucci", -15, 20).style("black_leg").weapon("knife").haki(30, 30, 0).disp(FRIENDLY));
        add(n("ivo", PATRON, CIV, "scholar", 4, "boin_archipelago", 0, 0).race("vinefolk").haki(20, 0, 0).disp(FRIENDLY));
        add(n("hattie", PATRON, CIV, "blacksmith", 5, "namakura_island", 10, 15).weapon("war_hammer").race("stonehide").haki(20, 40, 0).disp(FRIENDLY));
        add(n("corvin", PATRON, CIV, "cook", 4, "banaro_island", 12, 20).style("black_leg").race("emberkin").haki(20, 20, 0).disp(FRIENDLY));
        add(n("peony", PATRON, CIV, "noble", 6, "momoiro_island", 10, 15).style("okama_kenpo").race("long_leg").haki(40, 50, 0).disp(RIVAL));
        add(n("linnea", PATRON, CIV, "scholar", 5, "karakuri_island", 12, 12).race("three_eye").weapon("hand_cannon").haki(50, 10, 0).disp(FRIENDLY));
        // the Red Line
        add(n("juniper", PATRON, REVF, "revolutionary", 7, "mary_geoise", -14, 12).style("black_leg").haki(60, 40, 0).disp(FRIENDLY));
        // New World
        add(n("agathe", PATRON, CIV, "dockworker", 6, "new_world_gate", 100, 20).weapon("flintlock").style("gunslinger").haki(40, 30, 0).disp(FRIENDLY));
        add(n("rook", PATRON, CIV, "scholar", 6, "punk_hazard", 200, 60).weapon("hand_cannon").haki(30, 20, 0).disp(FRIENDLY));
        add(n("nana", PATRON, CIV, "elder", 6, "sphinx", 10, 15).style("hasshoken").haki(60, 50, 0).disp(FRIENDLY));
        add(n("praline", PATRON, CIV, "merchant", 6, "cacao_island", 12, 18).race("buccaneer").scale(1.3f).disp(FRIENDLY));
        add(n("bramble", PATRON, CIV, "cook", 6, "jam_island", -12, 18).race("mink").style("electro").disp(FRIENDLY));
        add(n("hansel", PATRON, CIV, "miller", 6, "foodvalten", 15, -15).weapon("war_hammer").haki(20, 30, 0).disp(FRIENDLY));
        add(n("gonzaburo", PATRON, CIV, "dockworker", 7, "onigashima", 150, 150).weapon("kanabo").style("anchor_brawl").haki(40, 60, 0).disp(FRIENDLY));
        add(n("moll", PATRON, CIV, "bartender", 7, "hachinosu", -40, 60).weapon("flintlock").style("gunslinger").haki(50, 40, 0).disp(FRIENDLY));
        add(n("cinder", PATRON, CIV, "elder", 8, "laugh_tale", -120, 90).style("black_leg").haki(80, 60, 0).disp(FRIENDLY));
    }

    private NotableCharactersExtra() {}
}
