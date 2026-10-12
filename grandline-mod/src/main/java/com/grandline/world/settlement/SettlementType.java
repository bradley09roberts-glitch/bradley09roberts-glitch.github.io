package com.grandline.world.settlement;

public enum SettlementType {
    NONE(0, 0), HAMLET(3, 6), VILLAGE(8, 14), TOWN(16, 28), CITY(36, 60), PORT(14, 24), MARINE_BASE(6, 10), FORTRESS(5, 9), RUINS(5, 10);

    public final int minBuildings, maxBuildings;

    SettlementType(int minBuildings, int maxBuildings) {
        this.minBuildings = minBuildings;
        this.maxBuildings = maxBuildings;
    }
}
