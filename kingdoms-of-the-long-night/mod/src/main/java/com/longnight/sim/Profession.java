package com.longnight.sim;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Occupations. Each production profession has a workplace and one or more recipes that
 * describe one full working day's output for an average-skill worker. Embodied workers
 * perform the same recipes in eight smaller "work cycles" per day, so a settlement produces
 * the same amount whether it is being watched by a player or simulated in the distance.
 */
public enum Profession {
    LABOURER("Labourer", null, Kind.CIVIC),
    FARMER("Farmer", BuildingType.FARM, Kind.FOOD),
    LUMBERJACK("Lumberjack", BuildingType.LUMBER_CAMP, Kind.MATERIAL),
    QUARRIER("Quarry Worker", BuildingType.QUARRY, Kind.MATERIAL),
    MINER("Miner", BuildingType.MINE, Kind.MATERIAL),
    FISHER("Fisher", BuildingType.FISHERY, Kind.FOOD),
    HUNTER("Hunter", BuildingType.HUNTING_LODGE, Kind.FOOD),
    SHEPHERD("Shepherd", BuildingType.PASTURE, Kind.FOOD),
    MILLER("Miller", BuildingType.MILL, Kind.CRAFT),
    BAKER("Baker", BuildingType.BAKERY, Kind.CRAFT),
    BREWER("Brewer", BuildingType.TAVERN, Kind.CRAFT),
    CARPENTER("Carpenter", BuildingType.CARPENTRY, Kind.CRAFT),
    BLACKSMITH("Blacksmith", BuildingType.SMITHY, Kind.CRAFT),
    WEAVER("Weaver", BuildingType.WEAVERY, Kind.CRAFT),
    BUILDER("Builder", BuildingType.BUILDERS_YARD, Kind.CIVIC),
    MERCHANT("Merchant", BuildingType.MARKET, Kind.SERVICE),
    INNKEEPER("Innkeeper", BuildingType.TAVERN, Kind.SERVICE),
    PRIEST("Priest", BuildingType.SHRINE, Kind.SERVICE),
    HEALER("Healer", BuildingType.HEALER_HOUSE, Kind.SERVICE),
    TEACHER("Teacher", BuildingType.SCHOOL, Kind.SERVICE),
    SCHOLAR("Scholar", BuildingType.LIBRARY, Kind.SERVICE),
    GRAVEDIGGER("Gravedigger", BuildingType.GRAVEYARD, Kind.SERVICE),
    STEWARD("Steward", BuildingType.TOWN_HALL, Kind.CIVIC),
    GUARD("Guard", BuildingType.WATCHTOWER, Kind.MILITARY),
    SOLDIER("Soldier", BuildingType.BARRACKS, Kind.MILITARY),
    RULER("Ruler", null, Kind.CIVIC),
    BANDIT("Bandit", null, Kind.MILITARY);

    public enum Kind { FOOD, MATERIAL, CRAFT, SERVICE, CIVIC, MILITARY }

    public record Recipe(Map<Commodity, Double> inputs, Map<Commodity, Double> outputs) {
        static Recipe of(Object... kv) {
            Map<Commodity, Double> in = new EnumMap<>(Commodity.class);
            Map<Commodity, Double> out = new EnumMap<>(Commodity.class);
            Map<Commodity, Double> target = out;
            for (int i = 0; i < kv.length; i++) {
                if ("->".equals(kv[i])) {
                    target = out;
                    continue;
                }
                if ("<-".equals(kv[i])) {
                    target = in;
                    continue;
                }
                Commodity c = (Commodity) kv[i];
                double q = ((Number) kv[++i]).doubleValue();
                target.put(c, q);
            }
            return new Recipe(in, out);
        }
    }

    public final String displayName;
    public final BuildingType workplace;
    public final Kind kind;

    Profession(String displayName, BuildingType workplace, Kind kind) {
        this.displayName = displayName;
        this.workplace = workplace;
        this.kind = kind;
    }

    public boolean producesGoods() {
        return !recipes().isEmpty();
    }

    /**
     * Daily recipes, in order of preference. A worker performs the first recipe whose inputs
     * are available and whose output is still wanted; a blocked worker reports why.
     */
    public List<Recipe> recipes() {
        return switch (this) {
            case FARMER -> List.of(Recipe.of("->", Commodity.GRAIN, 7.0, Commodity.VEGETABLES, 3.0));
            case LUMBERJACK -> List.of(Recipe.of("->", Commodity.LOGS, 6.0, Commodity.FUEL, 1.0));
            // the quarry yard digs sand too and fires a little glass in its kiln when there is fuel
            case QUARRIER -> List.of(Recipe.of("<-", Commodity.FUEL, 0.5, "->", Commodity.STONE, 8.0, Commodity.GLASS, 1.5),
                    Recipe.of("->", Commodity.STONE, 10.0));
            case MINER -> List.of(Recipe.of("->", Commodity.ORE, 3.0, Commodity.FUEL, 2.0, Commodity.STONE, 2.0));
            case FISHER -> List.of(Recipe.of("->", Commodity.FISH, 5.0));
            case HUNTER -> List.of(Recipe.of("->", Commodity.MEAT, 3.0, Commodity.LEATHER, 1.0));
            case SHEPHERD -> List.of(Recipe.of("->", Commodity.WOOL, 3.0, Commodity.MEAT, 1.0));
            case MILLER -> List.of(Recipe.of("<-", Commodity.GRAIN, 8.0, "->", Commodity.FLOUR, 8.0));
            case BAKER -> List.of(Recipe.of("<-", Commodity.FLOUR, 6.0, Commodity.FUEL, 1.0, "->", Commodity.BREAD, 10.0));
            case BREWER -> List.of(Recipe.of("<-", Commodity.GRAIN, 4.0, "->", Commodity.ALE, 6.0));
            case CARPENTER -> List.of(Recipe.of("<-", Commodity.LOGS, 4.0, "->", Commodity.PLANKS, 16.0),
                    Recipe.of("<-", Commodity.LOGS, 3.0, "->", Commodity.FUEL, 3.0));
            case BLACKSMITH -> List.of(
                    Recipe.of("<-", Commodity.ORE, 3.0, Commodity.FUEL, 2.0, "->", Commodity.IRON, 3.0),
                    Recipe.of("<-", Commodity.IRON, 2.0, Commodity.FUEL, 1.0, Commodity.PLANKS, 1.0, "->", Commodity.TOOLS, 1.0),
                    Recipe.of("<-", Commodity.IRON, 2.0, Commodity.FUEL, 1.0, Commodity.PLANKS, 1.0, "->", Commodity.WEAPONS, 1.0),
                    Recipe.of("<-", Commodity.IRON, 4.0, Commodity.FUEL, 1.0, Commodity.LEATHER, 1.0, "->", Commodity.ARMOR, 1.0));
            case WEAVER -> List.of(Recipe.of("<-", Commodity.WOOL, 3.0, "->", Commodity.CLOTH, 3.0));
            default -> List.of();
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Profession byId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LABOURER;
        }
    }
}
