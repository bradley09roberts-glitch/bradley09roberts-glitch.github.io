package com.longnight.sim;

/**
 * The slow approach of the Long Night. Omens and blight build over time; the Night itself rises
 * only if Nahire, the chained star in the Hollow (the Ender Dragon), is slain; each stage brings omens, then blight, then worse. Stage effects that are
 * implemented in this release: news/omens, a growing blight on harvests, realms turning to
 * fortification and faith. Later stages (undead hosts, the failing sun) are planned.
 */
public final class LongNightArc {
    private LongNightArc() {}

    public static final double[] THRESHOLDS = {0.0, 0.15, 0.35, 0.6, 0.85, 1.0};

    static final String[] OMENS = {
            "",
            "Stars fall over the Haldmarch on clear nights, and the Watchers of the Star-Sea have stopped saying what it means.",
            "A grey blight is spreading through the fields; grain rots on the stalk in places no one can explain.",
            "Gravediggers whisper that the earth will not settle over new graves, and the nights grow longer.",
            "The sun rises pale and late. Temples are full and the roads are full of people going nowhere.",
            "The Long Night has come."
    };

    public static void daily(WorldSim sim, long day) {
        // Omens and blight come with time; beyond that the Night rises only if Nahire (the Ender Dragon) is slain
        double rate = sim.dragonSlain ? 0.012 : 0.005;
        double cap = sim.dragonSlain ? 1.0 : THRESHOLDS[2] + 0.05;
        sim.nightPressure = Math.min(cap, sim.nightPressure + rate);
        int stage = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) if (sim.nightPressure >= THRESHOLDS[i]) stage = i;
        if (stage > sim.nightStage) {
            sim.nightStage = stage;
            Settlement anywhere = null;
            for (Settlement s : sim.settlements.values()) if (s.kind == Settlement.Kind.SETTLEMENT) anywhere = s;
            NewsItem n = sim.addNews(day, "long_night", OMENS[stage], anywhere, 4);
            for (Settlement s : sim.settlements.values()) s.knownNews.putIfAbsent(n.id(), day);
            sim.log(day, "The Long Night stirs (stage " + stage + ").");
            for (Faction f : sim.factions.values()) {
                if (stage >= 3 && f.agenda != Faction.Agenda.RECOVER) f.agenda = Faction.Agenda.FORTIFY;
            }
        }
    }

    /** Harvest multiplier from the blight. */
    public static double blightFactor(WorldSim sim) {
        return switch (sim.nightStage) {
            case 0, 1 -> 1.0;
            case 2 -> 0.88;
            case 3 -> 0.75;
            default -> 0.6;
        };
    }

    public static void onDragonSlain(WorldSim sim, long day) {
        if (sim.dragonSlain) return;
        sim.dragonSlain = true;
        sim.nightPressure = Math.min(1.0, sim.nightPressure + 0.2);
        NewsItem n = sim.addNews(day, "long_night",
                "Across the Haldmarch every star-reader woke at the same hour, weeping. Something old has died in the Hollow, and the nights are lengthening.", null, 5);
        for (Settlement s : sim.settlements.values()) s.knownNews.putIfAbsent(n.id(), day);
    }
}
