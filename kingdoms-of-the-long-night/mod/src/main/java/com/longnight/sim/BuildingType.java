package com.longnight.sim;

import java.util.Locale;

/**
 * Every building has a gameplay purpose. Footprint sizes are the plot the generator needs
 * (width along x, depth along z before rotation).
 */
public enum BuildingType {
    HOUSE("House", 7, 7, 0, 4, 0),
    LARGE_HOUSE("Large House", 9, 7, 0, 7, 0),
    FARM("Farm", 11, 11, 2, 0, 0),
    LUMBER_CAMP("Lumber Camp", 7, 7, 2, 0, 0),
    QUARRY("Quarry", 9, 9, 2, 0, 0),
    MINE("Mine", 7, 7, 2, 0, 0),
    FISHERY("Fishery", 7, 7, 2, 0, 0),
    HUNTING_LODGE("Hunting Lodge", 7, 7, 1, 0, 0),
    PASTURE("Pasture", 11, 11, 1, 0, 0),
    MILL("Mill", 7, 7, 1, 0, 0),
    BAKERY("Bakery", 7, 7, 1, 0, 0),
    CARPENTRY("Carpentry", 7, 7, 2, 0, 0),
    SMITHY("Smithy", 7, 7, 2, 0, 0),
    WEAVERY("Weavery", 7, 7, 2, 0, 0),
    TAVERN("Tavern", 9, 9, 2, 2, 0),
    STOREHOUSE("Storehouse", 7, 7, 0, 0, 600),
    GRANARY("Granary", 7, 7, 0, 0, 500),
    MARKET("Market", 9, 9, 2, 0, 0),
    SHRINE("Shrine", 5, 5, 1, 0, 0),
    TEMPLE("Temple", 11, 9, 2, 0, 0),
    WATCHTOWER("Watchtower", 5, 5, 1, 0, 0),
    BARRACKS("Barracks", 9, 7, 4, 4, 0),
    TOWN_HALL("Town Hall", 11, 9, 2, 0, 200),
    SCHOOL("School", 9, 7, 1, 0, 0),
    LIBRARY("Library", 7, 9, 1, 0, 0),
    HEALER_HOUSE("House of Healing", 7, 7, 1, 0, 0),
    GRAVEYARD("Graveyard", 9, 9, 1, 0, 0),
    WELL("Well", 5, 5, 0, 0, 0),
    BUILDERS_YARD("Builder's Yard", 7, 7, 3, 0, 100),
    CAMPSITE("Campsite", 9, 9, 0, 6, 80);

    public final String displayName;
    public final int width;
    public final int depth;
    /** number of worker slots */
    public final int workerSlots;
    /** number of residents it can house */
    public final int housing;
    /** extra ledger storage it provides */
    public final int storage;

    BuildingType(String displayName, int width, int depth, int workerSlots, int housing, int storage) {
        this.displayName = displayName;
        this.width = width;
        this.depth = depth;
        this.workerSlots = workerSlots;
        this.housing = housing;
        this.storage = storage;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BuildingType byId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
