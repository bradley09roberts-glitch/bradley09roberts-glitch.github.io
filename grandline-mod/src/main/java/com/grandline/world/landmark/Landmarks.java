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
        reg("windmill", new Spec(9, 9, true, false, 0), LandmarkBuilders::windmill);
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

    public static void build(Plot plot, Island island, Builder b) {
        LandmarkBuilder lb = BUILDERS.get(plot.landmark());
        if (lb == null) LandmarkBuilders.placeholder(b, island, island.style, plot.landmark());
        else lb.build(b, island, island.style);
    }

    private Landmarks() {}
}
