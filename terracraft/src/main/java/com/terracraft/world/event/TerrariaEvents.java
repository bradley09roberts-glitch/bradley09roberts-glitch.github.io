package com.terracraft.world.event;

import java.util.LinkedHashMap;
import java.util.Map;

/** Event catalogue. */
public final class TerrariaEvents {
    private static final Map<String, TerrariaEvent> EVENTS = new LinkedHashMap<>();

    /** Blood Moon: a red night with far more (and nastier) enemies. */
    public static final TerrariaEvent BLOOD_MOON = register(new TerrariaEvent("blood_moon", true, 3.0F, 2.0F, 0));
    /** Slime Rain: slimes fall from the sky all day; 150 kills (75 after King Slime) end it, summoning King Slime the first time. */
    public static final TerrariaEvent SLIME_RAIN = register(new TerrariaEvent("slime_rain", false, 1.0F, 1.0F, 150));

    /** Goblin Army: an invasion of goblins on the surface until 80 of them are killed. */
    public static final TerrariaEvent GOBLIN_ARMY = register(new TerrariaEvent("goblin_army", false, 5.0F, 3.0F, 80, true, "goblin_"));

    private TerrariaEvents() {}

    private static TerrariaEvent register(TerrariaEvent event) {
        EVENTS.put(event.id(), event);
        return event;
    }

    public static TerrariaEvent get(String id) {
        return EVENTS.get(id);
    }

    public static Map<String, TerrariaEvent> all() {
        return EVENTS;
    }
}
