package com.grandline.world.settlement;

public enum SettlementType {
    NONE(0, 0), HAMLET(3, 5), VILLAGE(6, 11), TOWN(12, 20), CITY(22, 36), PORT(10, 18), MARINE_BASE(4, 8), FORTRESS(4, 7), RUINS(4, 9);

    public final int minBuildings, maxBuildings;

    SettlementType(int minBuildings, int maxBuildings) {
        this.minBuildings = minBuildings;
        this.maxBuildings = maxBuildings;
    }
}
