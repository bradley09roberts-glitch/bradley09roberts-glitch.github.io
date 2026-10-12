package com.grandline.player;

import java.util.Locale;

/** The four seas a character can originate from. */
public enum Blue {
    EAST, WEST, NORTH, SOUTH;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "blue.grandline." + id();
    }

    public static Blue byId(String id) {
        for (Blue b : values()) if (b.id().equals(id)) return b;
        return EAST;
    }
}
