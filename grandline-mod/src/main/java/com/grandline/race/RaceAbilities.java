package com.grandline.race;

import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.player.Race;

import java.util.List;

/**
 * Racial techniques ({@link Ability.Source#RACE}): 2–4 per race, owned by the race id, trained by the race mastery
 * ("race/&lt;race&gt;"). Players hold them in the fourth ability set; NPCs of the race fight with them.
 */
public final class RaceAbilities {
    private static boolean booted;

    public static synchronized void bootstrap() {
        if (booted) return;
        booted = true;
        CanonRaceAbilities.register();
        OriginalRaceAbilities.register();
    }

    public static List<Ability> of(Race r) {
        bootstrap();
        return Abilities.of(Ability.Source.RACE, r.id());
    }

    private RaceAbilities() {}
}
