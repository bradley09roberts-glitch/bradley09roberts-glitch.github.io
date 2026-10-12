package com.longnight.sim;

import com.longnight.content.Content;

import java.util.List;
import java.util.Random;

/** Names on signboards: "The Gilded Ox", "Hallam's Smithy". */
public final class BuildingNames {
    private BuildingNames() {}

    static final List<String> TAVERN_ADJ = List.of("Gilded", "Drowsy", "Crooked", "Merry", "Black", "Silver", "Laughing", "Wandering",
            "Old", "Red", "Sleeping", "Rusty", "Lonely", "Painted", "Burning");
    static final List<String> TAVERN_NOUN = List.of("Ox", "Lantern", "Kettle", "Stag", "Boar", "Goose", "Anchor", "Hearth", "Wyrm",
            "Crown", "Barrel", "Owl", "Thistle", "Hound", "Ember");

    public static String name(WorldSim sim, Settlement s, Building b) {
        Random r = new Random(b.seed);
        return switch (b.type) {
            case TAVERN -> "The " + pick(TAVERN_ADJ, r) + " " + pick(TAVERN_NOUN, r);
            case TEMPLE -> "Temple of " + Content.faith(b.faith.isEmpty() ? "hearth" : b.faith).name.replace("The ", "the ");
            case SHRINE -> "Shrine of " + Content.faith(b.faith.isEmpty() ? "hearth" : b.faith).name.replace("The ", "the ");
            case TOWN_HALL -> s.name + " Moot Hall";
            case MARKET -> s.name + " Market";
            case WATCHTOWER -> pick(List.of("North", "East", "South", "West", "High", "Old"), r) + " Watch";
            case GRAVEYARD -> s.name + " Burial Ground";
            case STOREHOUSE -> s.name + " Stores";
            default -> "";
        };
    }

    static String pick(List<String> l, Random r) {
        return l.get(r.nextInt(l.size()));
    }
}
