package com.grandline.world.settlement;

/** Building archetypes with footprint (local width × depth). Door is on local z = 0 (north side). */
public enum BuildingType {
    HOUSE_SMALL(7, 7), HOUSE(9, 9), HOUSE_LARGE(11, 9), TAVERN(13, 11), SHOP(9, 9), BLACKSMITH(9, 9), INN(11, 11),
    SHIPWRIGHT(13, 11), CHURCH(9, 15), MARINE_POST(13, 13), WATCHTOWER(5, 5), LIGHTHOUSE(7, 7), WAREHOUSE(13, 9),
    MANSION(15, 13), MARKET_STALL(5, 5), WELL(3, 3), PLAZA(9, 9), FARM(9, 9), RUINED_HOUSE(9, 9), BOUNTY_BOARD(3, 3),
    DOJO(13, 11), HUT(5, 5), LANDMARK(1, 1),
    // island secrets (placed away from towns)
    BURIED_TREASURE(5, 5), HIDDEN_CACHE(7, 7), LOST_SHRINE(7, 7), SHIPWRECK(9, 21);

    public final int w, d;

    BuildingType(int w, int d) {
        this.w = w;
        this.d = d;
    }
}
