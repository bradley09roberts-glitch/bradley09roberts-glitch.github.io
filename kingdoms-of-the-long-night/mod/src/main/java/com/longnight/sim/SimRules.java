package com.longnight.sim;

import com.longnight.Config;

/** A snapshot of the tunable rules, so the simulation can also run in unit tests without a config. */
public class SimRules {
    public int daysPerYear = 8;
    public int adultAge = 16;
    public double birthRate = 1.0;
    public double deathRate = 1.0;
    public boolean raids = true;
    public int firstRaidDay = 5;
    public int raidWarningDays = 2;
    public boolean wars = true;
    public int maxCatchUpDays = 10;

    public static SimRules fromConfig() {
        SimRules r = new SimRules();
        try {
            r.daysPerYear = Config.DAYS_PER_YEAR.get();
            r.adultAge = Config.ADULT_AGE.get();
            r.birthRate = Config.BIRTH_RATE.get();
            r.deathRate = Config.DEATH_RATE.get();
            r.raids = Config.RAIDS.get();
            r.firstRaidDay = Config.FIRST_RAID_DAY.get();
            r.raidWarningDays = Config.RAID_WARNING_DAYS.get();
            r.wars = Config.WARS.get();
            r.maxCatchUpDays = Config.MAX_CATCHUP_DAYS.get();
        } catch (IllegalStateException notLoaded) {
            // config not loaded yet (e.g. unit tests) - keep defaults
        }
        return r;
    }
}
