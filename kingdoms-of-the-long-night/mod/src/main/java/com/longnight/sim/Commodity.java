package com.longnight.sim;

import java.util.Locale;

/**
 * Abstract goods tracked in settlement ledgers. A settlement's stockpile is the single
 * source of truth for its goods: items deposited by players are converted into units
 * and items are only created when units are removed, so nothing can be duplicated.
 */
public enum Commodity {
    // food
    GRAIN("Grain", 2, Category.FOOD, 0.7),
    FLOUR("Flour", 3, Category.FOOD, 0.0),
    BREAD("Bread", 4, Category.FOOD, 1.5),
    MEAT("Meat", 5, Category.FOOD, 1.3),
    FISH("Fish", 4, Category.FOOD, 1.1),
    VEGETABLES("Vegetables", 2, Category.FOOD, 0.8),
    ALE("Ale", 4, Category.LUXURY, 0.0),
    // building materials
    LOGS("Logs", 2, Category.MATERIAL, 0),
    PLANKS("Planks", 1, Category.MATERIAL, 0),
    STONE("Stone", 1, Category.MATERIAL, 0),
    GLASS("Glass", 3, Category.MATERIAL, 0),
    // metal & fuel
    ORE("Iron Ore", 4, Category.MATERIAL, 0),
    IRON("Iron", 8, Category.MATERIAL, 0),
    FUEL("Fuel", 2, Category.MATERIAL, 0),
    // goods
    TOOLS("Tools", 24, Category.GOODS, 0),
    WEAPONS("Weapons", 30, Category.GOODS, 0),
    ARMOR("Armour", 40, Category.GOODS, 0),
    WOOL("Wool", 3, Category.MATERIAL, 0),
    CLOTH("Cloth", 6, Category.GOODS, 0),
    LEATHER("Leather", 5, Category.MATERIAL, 0);

    public enum Category { FOOD, LUXURY, MATERIAL, GOODS }

    public final String displayName;
    /** base price in copper pennies */
    public final int basePrice;
    public final Category category;
    /** how much daily hunger one unit satisfies (0 = not edible) */
    public final double nourishment;

    Commodity(String displayName, int basePrice, Category category, double nourishment) {
        this.displayName = displayName;
        this.basePrice = basePrice;
        this.category = category;
        this.nourishment = nourishment;
    }

    public boolean edible() {
        return nourishment > 0;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Commodity byId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
