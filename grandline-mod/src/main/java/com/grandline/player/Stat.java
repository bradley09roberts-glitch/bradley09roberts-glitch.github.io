package com.grandline.player;

import java.util.Locale;

public enum Stat {
    STRENGTH, ENDURANCE, AGILITY, WILLPOWER, TECHNIQUE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "stat.grandline." + id();
    }
}
