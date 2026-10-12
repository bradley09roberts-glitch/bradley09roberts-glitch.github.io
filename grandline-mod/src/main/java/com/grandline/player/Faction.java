package com.grandline.player;

import java.util.Locale;

/** Core faction structures preserved from the source. */
public enum Faction {
    PIRATE(true),
    MARINE(true),
    REVOLUTIONARY(true),
    BOUNTY_HUNTER(true),
    CIVILIAN(true),
    WORLD_GOVERNMENT(false),
    CIPHER_POL(false),
    WILD(false);

    public final boolean startingPath;

    Faction(boolean startingPath) {
        this.startingPath = startingPath;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "faction.grandline." + id();
    }

    public static Faction byId(String id) {
        for (Faction f : values()) if (f.id().equals(id)) return f;
        return CIVILIAN;
    }

    /** Base attitude of faction a toward faction b, -100..100. */
    public static int baseAttitude(Faction a, Faction b) {
        if (a == b) return a == PIRATE ? 0 : 60;
        if (a == WILD || b == WILD) return -100;
        boolean law = isLaw(a), law2 = isLaw(b);
        if (law && law2) return 60;
        if ((law && b == PIRATE) || (law2 && a == PIRATE)) return -80;
        if ((law && b == REVOLUTIONARY) || (law2 && a == REVOLUTIONARY)) return -90;
        if (a == CIVILIAN || b == CIVILIAN) return 20;
        if ((a == BOUNTY_HUNTER && b == PIRATE) || (b == BOUNTY_HUNTER && a == PIRATE)) return -50;
        return 0;
    }

    public static boolean isLaw(Faction f) {
        return f == MARINE || f == WORLD_GOVERNMENT || f == CIPHER_POL;
    }
}
