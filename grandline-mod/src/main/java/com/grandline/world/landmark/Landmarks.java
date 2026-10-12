package com.grandline.world.landmark;

import com.grandline.world.Island;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.Plot;

import java.util.HashMap;
import java.util.Map;

/**
 * Named set pieces. Each landmark has a footprint spec and a builder. Landmarks without a dedicated builder
 * fall back to {@link LandmarkBuilders#placeholder} which is tracked as a placeholder in docs/INVENTORY.md.
 */
public final class Landmarks {
    /** width/depth are local footprint; flatten = terrain pad; atSeaLevel = built on water; fixedY overrides height. */
    public record Spec(int width, int depth, boolean flatten, boolean atSeaLevel, int fixedY) {}

    public interface LandmarkBuilder {
        void build(Builder b, Island island, BuildStyle style);
    }

    private static final Map<String, Spec> SPECS = new HashMap<>();
    private static final Map<String, LandmarkBuilder> BUILDERS = new HashMap<>();
    private static final Spec DEFAULT = new Spec(9, 9, true, false, 0);

    static {
        reg("starter_shrine", new Spec(9, 9, true, false, 0), LandmarkBuilders::starterShrine);
        reg("treasure_cache", new Spec(7, 7, true, false, 0), LandmarkBuilders::treasureCache);
        reg("ancient_ruin", new Spec(15, 15, true, false, 0), LandmarkBuilders::ancientRuin);
        reg("fruit_tree_grove", new Spec(11, 11, true, false, 0), LandmarkBuilders::fruitTreeGrove);
        reg("windmill", new Spec(13, 11, true, false, 0), LandmarkBuilders::windmill);
        reg("baratie", new Spec(23, 45, false, true, 0), LandmarkBuilders::baratie);
        reg("arlong_park", new Spec(31, 31, true, false, 0), LandmarkBuilders::arlongPark);
        reg("execution_platform", new Spec(13, 13, true, false, 0), LandmarkBuilders::executionPlatform);
        reg("marine_base", new Spec(27, 27, true, false, 0), LandmarkBuilders::marineBase);
        reg("marine_headquarters", new Spec(41, 41, true, false, 0), LandmarkBuilders::marineHeadquarters);
        reg("drum_castle", new Spec(25, 25, true, false, 0), LandmarkBuilders::drumCastle);
        reg("twin_capes_lighthouse", new Spec(9, 9, true, false, 0), LandmarkBuilders::twinCapesLighthouse);
        reg("sea_train_station", new Spec(15, 11, true, false, 0), LandmarkBuilders::seaTrainStation);
        reg("water7_fountain", new Spec(21, 21, true, false, 0), LandmarkBuilders::water7Fountain);
        reg("tower_of_justice", new Spec(17, 17, true, false, 0), LandmarkBuilders::towerOfJustice);
        reg("impel_down", new Spec(31, 31, true, false, 0), LandmarkBuilders::impelDown);
        reg("sword_dojo", new Spec(15, 13, true, false, 0), LandmarkBuilders::swordDojo);
        reg("knock_up_marker", new Spec(5, 5, false, true, 0), LandmarkBuilders::knockUpMarker);
        reg("treasure_tree", new Spec(41, 41, true, false, 0), LandmarkBuilders::giantTree);
        reg("tree_of_knowledge", new Spec(31, 31, true, false, 0), LandmarkBuilders::giantTreeRuined);
        reg("laugh_tale_ruins", new Spec(25, 25, true, false, 0), LandmarkBuilders::laughTaleRuins);
        reg("mary_geoise", new Spec(41, 41, false, false, 301), LandmarkBuilders::maryGeoise);
        reg("port_beacon", new Spec(7, 7, true, false, 0), LandmarkBuilders::twinCapesLighthouse);
        DimensionLandmarks.registerAll(Landmarks::reg); // White Sea and deep-sea set pieces
        // ---- hand-designed canon set pieces (BluesLandmarks / ParadiseLandmarks / GrandLineLandmarks / NewWorldLandmarks)
        reg("gray_terminal", new Spec(37, 33, true, false, 0), BluesLandmarks::grayTerminal);
        reg("hilltop_mansion", new Spec(33, 29, true, false, 0), BluesLandmarks::hilltopMansion);
        reg("tangerine_grove", new Spec(25, 21, true, false, 0), BluesLandmarks::tangerineGrove);
        reg("white_city", new Spec(47, 47, true, false, 0), BluesLandmarks::whiteCity);
        reg("explorers_monument", new Spec(27, 27, true, false, 0), BluesLandmarks::explorersMonument);
        reg("junkyard_fort", new Spec(37, 37, true, false, 0), BluesLandmarks::junkyardFort);
        reg("royal_castle", new Spec(51, 51, true, false, 0), BluesLandmarks::royalCastle);
        reg("welcome_arch", new Spec(27, 25, true, false, 0), ParadiseLandmarks::welcomeArch);
        reg("giant_duel_ground", new Spec(45, 45, true, false, 0), ParadiseLandmarks::giantDuelGround);
        reg("volcano_vent", new Spec(27, 27, true, false, 0), ParadiseLandmarks::volcanoVent);
        reg("alubarna_palace", new Spec(55, 55, true, false, 0), ParadiseLandmarks::alubarnaPalace);
        reg("nanohana_port", new Spec(41, 35, false, true, 0), ParadiseLandmarks::nanohanaPort);
        reg("rainbase_casino", new Spec(37, 37, true, false, 0), ParadiseLandmarks::rainbaseCasino);
        reg("yuba_oasis", new Spec(29, 29, true, false, 0), ParadiseLandmarks::yubaOasis);
        reg("cricket_house", new Spec(21, 19, true, false, 0), ParadiseLandmarks::cricketHouse);
        reg("galley_la_docks", new Spec(45, 41, false, true, 0), GrandLineLandmarks::galleyLaDocks);
        reg("gates_of_justice", new Spec(47, 17, false, true, 0), GrandLineLandmarks::gatesOfJustice);
        reg("haunted_mansion", new Spec(45, 45, true, false, 0), GrandLineLandmarks::hauntedMansion);
        reg("ruined_castle", new Spec(41, 41, true, false, 0), GrandLineLandmarks::ruinedCastle);
        reg("mangrove_grove", new Spec(37, 37, true, false, 0), GrandLineLandmarks::mangroveGrove);
        reg("auction_house", new Spec(33, 35, true, false, 0), GrandLineLandmarks::auctionHouse);
        reg("kuja_palace", new Spec(45, 45, true, false, 0), GrandLineLandmarks::kujaPalace);
        reg("abandoned_lab", new Spec(41, 35, true, false, 0), NewWorldLandmarks::abandonedLab);
        reg("corrida_colosseum", new Spec(51, 51, true, false, 0), NewWorldLandmarks::corridaColosseum);
        reg("royal_plateau", new Spec(45, 45, true, false, 0), NewWorldLandmarks::royalPlateau);
        reg("tontatta_village", new Spec(29, 29, true, false, 0), NewWorldLandmarks::tontattaVillage);
        reg("giant_legs", new Spec(41, 41, true, false, 0), NewWorldLandmarks::giantLegs);
        reg("cake_chateau", new Spec(51, 51, true, false, 0), NewWorldLandmarks::cakeChateau);
        reg("flower_capital_castle", new Spec(45, 45, true, false, 0), FinalSeaLandmarks::flowerCapitalCastle);
        reg("waterfall_gate", new Spec(25, 23, true, false, 0), FinalSeaLandmarks::waterfallGate);
        reg("skull_dome", new Spec(51, 51, true, false, 0), FinalSeaLandmarks::skullDome);
        reg("revolutionary_hq", new Spec(41, 41, true, false, 0), FinalSeaLandmarks::revolutionaryHq);
        reg("future_lab", new Spec(41, 41, true, false, 0), FinalSeaLandmarks::futureLab);
        reg("pirate_hive", new Spec(41, 41, true, false, 0), FinalSeaLandmarks::pirateHive);
    }

    private static void reg(String id, Spec spec, LandmarkBuilder builder) {
        SPECS.put(id, spec);
        BUILDERS.put(id, builder);
    }

    public static Spec spec(String id) {
        return SPECS.getOrDefault(id, DEFAULT);
    }

    public static boolean implemented(String id) {
        return BUILDERS.containsKey(id);
    }

    /** Every landmark id with a registered builder (sorted, for verification tooling). */
    public static java.util.SortedSet<String> ids() {
        return java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(BUILDERS.keySet()));
    }

    public static void build(Plot plot, Island island, Builder b) {
        LandmarkBuilder lb = BUILDERS.get(plot.landmark());
        if (lb == null) LandmarkBuilders.placeholder(b, island, island.style, plot.landmark());
        else lb.build(b, island, island.style);
    }

    private Landmarks() {}
}
