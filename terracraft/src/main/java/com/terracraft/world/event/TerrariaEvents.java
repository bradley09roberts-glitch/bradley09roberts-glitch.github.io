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

    /** Pirate Invasion (Hardmode): pirates, parrots and the Flying Dutchman until 120 kills (the Dutchman counts for ten). */
    public static final TerrariaEvent PIRATE_INVASION = register(new TerrariaEvent("pirate_invasion", false, 5.0F, 3.0F, 120, true,
        "pirate_|parrot|flying_dutchman"));
    /** Frost Legion (Hardmode, from a Snow Globe): snowmen with knives, guns and snowballs until 80 are killed. */
    public static final TerrariaEvent FROST_LEGION = register(new TerrariaEvent("frost_legion", false, 5.0F, 3.0F, 80, true,
        "mister_stabby|snowman_gangsta|snow_balla"));

    /** Pumpkin Moon (Hardmode night, Pumpkin Moon Medallion): 15 waves of Halloween horrors until dawn. */
    public static final TerrariaEvent PUMPKIN_MOON = register(new TerrariaEvent("pumpkin_moon", true, 1.0F, 1.0F, 0, true,
        "scarecrow|splinterling|hellhound|poltergeist|headless_horseman|mourning_wood|pumpking", 15));
    /** Frost Moon (Hardmode night, Naughty Present): 20 waves of Christmas creatures until dawn. */
    public static final TerrariaEvent FROST_MOON = register(new TerrariaEvent("frost_moon", true, 1.0F, 1.0F, 0, true,
        "zombie_elf|gingerbread_man|elf_archer|nutcracker|yeti|flocko|everscream|santa_nk1|ice_queen", 20));

    /** Martian Madness (after Golem, when a Martian Probe escapes): martians and the Martian Saucer until 150 kills. */
    public static final TerrariaEvent MARTIAN_MADNESS = register(new TerrariaEvent("martian_madness", false, 5.0F, 3.0F, 150, true,
        "gray_grunt|ray_gunner|brain_scrambler|gigazapper|martian_officer|martian_drone|scutlix|martian_saucer"));

    /** Solar Eclipse (after a mechanical boss, or a Solar Tablet): the sun goes dark and movie monsters walk all day. */
    public static final TerrariaEvent SOLAR_ECLIPSE = register(new TerrariaEvent("solar_eclipse", false, 4.0F, 2.0F, 0));

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
