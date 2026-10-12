package com.longnight.sim;

/** The eight-month common reckoning of the Haldmarch. One in-game day is one month by default (configurable). */
public final class Calendar {
    private Calendar() {}

    public static final String[] MONTHS = {
            "Brightwake", "Seedtide", "Blossomtide", "Highsun", "Reaping", "Leaffall", "Dimming", "Deepnight"
    };

    public enum Season { SPRING, SUMMER, AUTUMN, WINTER }

    public static int month(long day, int daysPerYear) {
        long doy = Math.floorMod(day, daysPerYear);
        return (int) (doy * 8 / daysPerYear);
    }

    public static long year(long day, int daysPerYear) {
        return Math.floorDiv(day, daysPerYear) + 412; // the Reckoning began 412 years before the first day
    }

    public static Season season(long day, int daysPerYear) {
        int m = month(day, daysPerYear);
        if (m <= 2) return Season.SPRING;
        if (m <= 4) return Season.SUMMER;
        if (m == 5) return Season.AUTUMN;
        return Season.WINTER;
    }

    public static String monthName(long day, int daysPerYear) {
        return MONTHS[month(day, daysPerYear)];
    }

    public static String describe(long day, int daysPerYear) {
        return monthName(day, daysPerYear) + ", year " + year(day, daysPerYear) + " of the Reckoning";
    }

    /** Multiplier for field and pasture yields. */
    public static double harvestFactor(long day, int daysPerYear) {
        return switch (month(day, daysPerYear)) {
            case 0 -> 0.4;   // Brightwake: ploughing
            case 1 -> 0.7;   // Seedtide: sowing
            case 2 -> 0.9;   // Blossomtide
            case 3 -> 1.2;   // Highsun: haymaking
            case 4 -> 1.6;   // Reaping: the harvest
            case 5 -> 1.0;   // Leaffall: last gleanings, slaughter
            case 6 -> 0.35;  // Dimming
            default -> 0.15; // Deepnight
        };
    }
}
