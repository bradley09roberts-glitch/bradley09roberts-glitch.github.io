package com.grandline.world.islands;

import com.grandline.player.Blue;
import com.grandline.world.IslandShape;
import com.grandline.world.IslandSpec;
import com.grandline.world.IslandTheme;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.settlement.SettlementType;

import java.util.ArrayList;
import java.util.List;

import static com.grandline.world.IslandShape.*;
import static com.grandline.world.IslandTheme.*;
import static com.grandline.world.settlement.SettlementType.*;

/**
 * Fixed positions of every canon island (plus the four original starter islands) in the main ocean world.
 * Sky islands (Skypiea, Weatheria) and the deep sea (Fish-Man Island) live in their own dimensions.
 */
public final class CanonIslands {
    public static List<IslandSpec> all() {
        List<IslandSpec> l = new ArrayList<>();
        // ---------------- East Blue (x>0, z>0): the route runs west toward Reverse Mountain ----------------
        l.add(c("shiokaze_isle", 36000, 20000, 380).shape(HILLS, 26).theme(TEMPERATE).settle(VILLAGE).starter(Blue.EAST).tier(1).landmark("starter_shrine", 40, -60).named("Shiokaze Isle"));
        l.add(c("dawn_island", 31000, 23500, 700).shape(MOUNTAIN, 75).theme(TEMPERATE).settle(VILLAGE).tier(1).landmark("windmill", 0, 260).landmark("gray_terminal", -260, -120));
        l.add(c("shimotsuki_village", 33500, 15000, 400).shape(HILLS, 28).theme(TEMPERATE).settle(VILLAGE).tier(1).landmark("sword_dojo", 30, 30));
        l.add(c("shells_town", 27000, 18500, 450).shape(HILLS, 24).theme(TEMPERATE).settle(TOWN).faction("marine").tier(1).landmark("marine_base", 90, -40));
        l.add(c("orange_town", 23000, 22000, 380).shape(HILLS, 18).theme(FLOWER).settle(TOWN).tier(1));
        l.add(c("island_of_rare_animals", 25500, 27000, 300).shape(HILLS, 30).theme(TROPICAL).settle(NONE).tier(2).landmark("treasure_cache", 0, 0));
        l.add(c("syrup_village", 19000, 16500, 520).shape(PLATEAU, 26).theme(TEMPERATE).settle(VILLAGE).tier(1).landmark("hilltop_mansion", 140, 40));
        l.add(c("mirror_ball_island", 17000, 25500, 280).shape(FLAT, 8).theme(TROPICAL).settle(HAMLET).tier(2));
        l.add(c("baratie", 15500, 13500, 22).shape(FLAT, 0).theme(TEMPERATE).settle(NONE).tier(2).landmark("baratie", 0, 0));
        l.add(c("cocoyasi", 12000, 18500, 650).shape(HILLS, 35).theme(TROPICAL).settle(VILLAGE).tier(2).landmark("arlong_park", 330, -60).landmark("tangerine_grove", -120, 80));
        l.add(c("loguetown", 5500, 9500, 600).shape(HILLS, 22).theme(TEMPERATE).style(BuildStyle.STONE_TOWN).settle(CITY).faction("marine").tier(2).landmark("execution_platform", 0, 0).landmark("marine_base", 220, 120));
        // ---------------- North Blue (x>0, z<0) ----------------
        l.add(c("fuyugane_isle", 36000, -20000, 380).shape(HILLS, 26).theme(SNOW).settle(VILLAGE).starter(Blue.NORTH).tier(1).landmark("starter_shrine", 40, 60).named("Fuyugane Isle"));
        l.add(c("flevance", 28000, -24000, 500).shape(HILLS, 20).theme(ICE).style(BuildStyle.PALACE).settle(RUINS).tier(2).landmark("white_city", 0, 0));
        l.add(c("minion_island", 22000, -14000, 350).shape(MOUNTAIN, 60).theme(SNOW).settle(NONE).tier(2));
        l.add(c("lvneel", 16000, -21000, 550).shape(HILLS, 30).theme(TAIGA).settle(CITY).tier(1).landmark("explorers_monument", 0, 0));
        l.add(c("swallow_island", 9000, -12000, 380).shape(HILLS, 24).theme(TAIGA).settle(VILLAGE).tier(2));
        l.add(c("spider_miles", 30000, -10000, 400).shape(SPIRES, 70).theme(ROCKY).settle(FORTRESS).faction("pirate").tier(2).landmark("junkyard_fort", 0, 0));
        // ---------------- West Blue (x<0, z<0) ----------------
        l.add(c("kirinoha_isle", -36000, -20000, 380).shape(HILLS, 26).theme(MEADOW).settle(VILLAGE).starter(Blue.WEST).tier(1).landmark("starter_shrine", -40, 60).named("Kirinoha Isle"));
        l.add(c("ohara", -27000, -24000, 500).shape(HILLS, 30).theme(TEMPERATE).settle(RUINS).tier(2).landmark("tree_of_knowledge", 0, 0));
        l.add(c("ilisia_kingdom", -20000, -15000, 550).shape(HILLS, 26).theme(FLOWER).style(BuildStyle.PALACE).settle(CITY).tier(1).landmark("royal_castle", 0, -150));
        l.add(c("kano_country", -12000, -19000, 450).shape(TERRACES, 50).theme(TEMPERATE).settle(TOWN).tier(2).landmark("sword_dojo", 0, 40));
        // ---------------- South Blue (x<0, z>0) ----------------
        l.add(c("hinata_isle", -36000, 20000, 380).shape(HILLS, 26).theme(TROPICAL).settle(VILLAGE).starter(Blue.SOUTH).tier(1).landmark("starter_shrine", -40, -60).named("Hinata Isle"));
        l.add(c("baterilla", -28000, 24000, 450).shape(HILLS, 22).theme(TROPICAL).settle(VILLAGE).tier(1));
        l.add(c("karate_island", -21000, 15000, 380).shape(TERRACES, 45).theme(TROPICAL).settle(VILLAGE).tier(2).landmark("sword_dojo", 0, 0));
        l.add(c("sorbet_kingdom", -14000, 22000, 500).shape(HILLS, 24).theme(SAVANNA).settle(TOWN).tier(2));
        l.add(c("torino_kingdom", -8000, 12000, 450).shape(GIANT_TREE, 40).theme(TROPICAL).settle(HAMLET).tier(2));
        // ---------------- Reverse Mountain and Paradise (x>0, |z|<4000) ----------------
        l.add(c("twin_capes", 1900, 0, 220).shape(FLAT, 10).theme(ROCKY).settle(HAMLET).tier(2).landmark("twin_capes_lighthouse", 0, 0));
        l.add(c("whisky_peak", 5500, 1800, 450).shape(SPIRES, 95).theme(TEMPERATE).settle(TOWN).faction("bounty_hunter").tier(3).landmark("welcome_arch", -200, 0));
        l.add(c("little_garden", 10000, -1600, 700).shape(HILLS, 55).theme(TROPICAL).settle(NONE).tier(4).landmark("giant_duel_ground", 0, 0).landmark("volcano_vent", 220, 140));
        l.add(c("drum_island", 14000, 2200, 700).shape(DRUM, 150).theme(SNOW).settle(VILLAGE).tier(3).landmark("drum_castle", 0, 0));
        l.add(c("alabasta", 20000, -600, 1400).shape(DUNES, 18).theme(DESERT).settle(CITY).tier(3).landmark("alubarna_palace", -350, 0).landmark("nanohana_port", 950, 300).landmark("rainbase_casino", 250, -520).landmark("yuba_oasis", -80, 500));
        l.add(c("jaya", 25500, 2600, 600).shape(HILLS, 30).theme(TROPICAL).settle(TOWN).faction("pirate").tier(3).landmark("knock_up_marker", 520, 300).landmark("cricket_house", -380, 120));
        l.add(c("long_ring_long_land", 30000, -2200, 650).shape(RING, 10).theme(MEADOW).settle(HAMLET).tier(3));
        l.add(c("san_faldo", 31500, 3300, 320).shape(HILLS, 18).theme(TEMPERATE).style(BuildStyle.VENETIAN).settle(TOWN).tier(3).landmark("sea_train_station", 0, -150));
        l.add(c("water_7", 34500, 400, 800).shape(PLATEAU, 22).theme(ROCKY).style(BuildStyle.VENETIAN).settle(CITY).tier(4).landmark("water7_fountain", 0, 0).landmark("galley_la_docks", 420, 260).landmark("sea_train_station", -380, 0));
        l.add(c("st_poplar", 33000, -3300, 300).shape(HILLS, 16).theme(FLOWER).style(BuildStyle.VENETIAN).settle(TOWN).tier(3).landmark("sea_train_station", 0, 130));
        l.add(c("pucci", 37000, 3400, 300).shape(HILLS, 16).theme(TEMPERATE).style(BuildStyle.VENETIAN).settle(TOWN).tier(3).landmark("sea_train_station", 0, -130));
        l.add(c("enies_lobby", 38800, -1800, 450).shape(PLATEAU, 45).theme(WHITE_ROCK).style(BuildStyle.PALACE).settle(FORTRESS).faction("world_government").tier(5).landmark("tower_of_justice", 0, 0).landmark("gates_of_justice", 380, -150));
        l.add(c("boin_archipelago", 40500, 3200, 420).shape(ARCHIPELAGO, 40).theme(TROPICAL).settle(NONE).tier(4));
        l.add(c("thriller_bark", 43000, 3000, 500).shape(HILLS, 25).theme(DARK).settle(FORTRESS).faction("pirate").tier(5).landmark("haunted_mansion", 0, 0));
        l.add(c("namakura_island", 44500, -1200, 300).shape(HILLS, 20).theme(TEMPERATE).settle(HAMLET).tier(4));
        l.add(c("banaro_island", 46500, -2600, 320).shape(HILLS, 20).theme(TEMPERATE).settle(RUINS).tier(4));
        l.add(c("momoiro_island", 47500, 2700, 300).shape(HILLS, 30).theme(FLOWER).settle(HAMLET).tier(4));
        l.add(c("kuraigana_island", 49500, 1200, 450).shape(HILLS, 45).theme(DARK).settle(RUINS).tier(5).landmark("ruined_castle", 0, 0));
        l.add(c("karakuri_island", 51500, 3300, 350).shape(HILLS, 30).theme(FUTURE).settle(HAMLET).tier(4));
        l.add(c("marineford", 54200, -2500, 550).shape(PLATEAU, 18).theme(WHITE_ROCK).style(BuildStyle.MARINE).settle(MARINE_BASE).faction("marine").tier(6).landmark("marine_headquarters", 0, 0));
        l.add(c("sabaody_archipelago", 57600, 0, 900).shape(ARCHIPELAGO, 20).theme(SWAMP).settle(CITY).tier(5).landmark("mangrove_grove", 0, 0).landmark("auction_house", 260, 160));
        // ---------------- Calm Belt ----------------
        l.add(c("amazon_lily", 46000, 5250, 650).shape(CLIFF_WALL, 95).theme(TROPICAL).settle(TOWN).faction("kuja").tier(5).landmark("kuja_palace", 0, 0));
        l.add(c("rusukaina", 40000, -5300, 450).shape(HILLS, 50).theme(TROPICAL).settle(NONE).tier(6));
        l.add(c("impel_down", 52000, -5400, 170).shape(FLAT, 4).theme(ROCKY).settle(NONE).faction("world_government").tier(7).landmark("impel_down", 0, 0));
        // ---------------- Red Line ----------------
        l.add(c("mary_geoise", 60000, 0, 1).shape(FLAT, 0).theme(WHITE_ROCK).settle(NONE).faction("world_government").tier(9).landmark("mary_geoise", 0, 0));
        // ---------------- New World (x<0, |z|<4000), sailed west → east ----------------
        l.add(c("new_world_gate", -58500, 0, 350).shape(HILLS, 20).theme(ROCKY).settle(PORT).tier(6).named("New World Gate").landmark("port_beacon", 150, 0));
        l.add(c("new_marineford", -56500, -2900, 520).shape(PLATEAU, 22).theme(WHITE_ROCK).style(BuildStyle.MARINE).settle(MARINE_BASE).faction("marine").tier(8).landmark("marine_headquarters", 0, 0));
        l.add(c("punk_hazard", -53500, 2000, 650).shape(HILLS, 45).theme(VOLCANIC).split(ICE).settle(RUINS).tier(6).landmark("abandoned_lab", 0, 0));
        l.add(c("g5_base", -51800, -3300, 330).shape(FLAT, 8).theme(ICE).style(BuildStyle.MARINE).settle(MARINE_BASE).faction("marine").tier(6));
        l.add(c("dressrosa", -48500, -1400, 900).shape(HILLS, 40).theme(MEADOW).settle(CITY).tier(7).landmark("corrida_colosseum", 250, 150).landmark("royal_plateau", -200, -100));
        l.add(c("green_bit", -46700, -2800, 350).shape(HILLS, 30).theme(TROPICAL).settle(NONE).tier(6).landmark("tontatta_village", 0, 0));
        l.add(c("zou", -43000, 2600, 700).shape(PLATEAU, 120).theme(GIANT_FOREST).settle(VILLAGE).tier(7).landmark("giant_legs", 0, 0));
        l.add(c("sphinx", -40000, -3000, 350).shape(HILLS, 20).theme(DESERT).settle(HAMLET).tier(6));
        l.add(c("whole_cake_island", -36500, -1800, 1100).shape(HILLS, 50).theme(CANDY).settle(CITY).faction("pirate").tier(8).landmark("cake_chateau", 0, 0));
        l.add(c("cacao_island", -34300, -3400, 350).shape(HILLS, 20).theme(CANDY).settle(TOWN).faction("pirate").tier(7));
        l.add(c("jam_island", -38800, 400, 300).shape(HILLS, 20).theme(CANDY).settle(TOWN).faction("pirate").tier(7));
        l.add(c("foodvalten", -33000, 3300, 400).shape(HILLS, 25).theme(TEMPERATE).settle(TOWN).tier(7));
        l.add(c("wano_country", -29000, 1200, 1500).shape(CLIFF_WALL, 140).theme(CHERRY).style(BuildStyle.WANO).settle(CITY).tier(8).landmark("flower_capital_castle", 0, -200).landmark("waterfall_gate", 1300, 0));
        l.add(c("onigashima", -26200, -1800, 450).shape(MOUNTAIN, 90).theme(DARK).style(BuildStyle.WANO).settle(FORTRESS).faction("pirate").tier(9).landmark("skull_dome", 0, 0));
        l.add(c("baltigo", -24000, 3300, 450).shape(FLAT, 12).theme(WHITE_ROCK).settle(FORTRESS).faction("revolutionary").tier(7).landmark("revolutionary_hq", 0, 0));
        l.add(c("egghead", -20500, -2500, 600).shape(MOUNTAIN, 80).theme(FUTURE).style(BuildStyle.FUTURE).settle(TOWN).tier(8).landmark("future_lab", 0, 0));
        l.add(c("hachinosu", -17000, 3000, 500).shape(SPIRES, 90).theme(VOLCANIC).settle(FORTRESS).faction("pirate").tier(9).landmark("pirate_hive", 0, 0));
        l.add(c("elbaph", -12500, 1500, 1500).shape(GIANT_TREE, 60).theme(GIANT_FOREST).style(BuildStyle.GIANT).settle(CITY).faction("giants").tier(9).landmark("treasure_tree", 0, 0));
        l.add(c("lodestar_island", -7000, -2400, 450).shape(HILLS, 50).theme(VOLCANIC).settle(NONE).tier(9));
        l.add(c("laugh_tale", -3000, 0, 600).shape(HILLS, 40).theme(TROPICAL).settle(NONE).tier(10).landmark("laugh_tale_ruins", 0, 0));
        return l;
    }

    private static IslandSpec c(String id, int x, int z, int r) {
        return IslandSpec.canon(id, x, z, r);
    }

    private CanonIslands() {}
}
