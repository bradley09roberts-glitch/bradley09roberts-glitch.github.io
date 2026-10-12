package com.grandline.fruit;

public enum FruitType {
    LOGIA, PARAMECIA, SPECIAL_PARAMECIA, ZOAN, ANCIENT_ZOAN, MYTHICAL_ZOAN;

    public boolean isZoan() {
        return this == ZOAN || this == ANCIENT_ZOAN || this == MYTHICAL_ZOAN;
    }
}
