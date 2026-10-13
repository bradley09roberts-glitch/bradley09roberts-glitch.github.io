package com.grandline.npc.crew;

import com.grandline.npc.GLNpc;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableCharacters.Notable;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * How a companion fights, derived from its crew role (the ship job stays what it was: a sniper still makes the
 * cannons hit harder, a musician still restores stamina at sea).
 * <ul>
 * <li>{@link #FIGHTER}: close range. Takes on whoever is going for the captain, steps in front of them, and holds
 * their attention (rank 3: Rallying Challenge).</li>
 * <li>{@link #RANGED}: keeps 6-12 blocks back, behind the captain, shoots only with a clear line and focuses the
 * captain's target (rank 3: Pinning Shot).</li>
 * <li>{@link #SUPPORT}: stays at the captain's side, heals in short pulses (a few per fight) and gets the fallen back
 * up twice as fast (rank 3: Encore).</li>
 * </ul>
 */
public enum CompanionRole {
    FIGHTER(1.3f, "challenge"),
    RANGED(1.0f, "pinning_shot"),
    SUPPORT(1.0f, "encore");

    /** Health relative to the reference character of the captain's level (front-liners take the blows). */
    public final float healthFactor;
    /** The extra technique bond rank 3 unlocks. */
    public final String technique;

    CompanionRole(float healthFactor, String technique) {
        this.healthFactor = healthFactor;
        this.technique = technique;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return "companion_role.grandline." + id();
    }

    public String techniqueKey() {
        return "companion_technique.grandline." + technique;
    }

    /** Crew job -> fighting role. Every recruitable's job is listed; anything unknown fights up close. */
    private static final Map<String, CompanionRole> BY_CREW_ROLE = Map.ofEntries(
            Map.entry("swordsman", FIGHTER), Map.entry("swordswoman", FIGHTER), Map.entry("fighter", FIGHTER), Map.entry("bodyguard", FIGHTER),
            Map.entry("warrior", FIGHTER), Map.entry("shipwright", FIGHTER), Map.entry("cook", FIGHTER), Map.entry("spy", FIGHTER),
            Map.entry("sniper", RANGED), Map.entry("lookout", RANGED), Map.entry("quartermaster", RANGED), Map.entry("helmsman", RANGED),
            Map.entry("engineer", RANGED), Map.entry("stargazer", RANGED),
            Map.entry("musician", SUPPORT), Map.entry("doctor", SUPPORT), Map.entry("navigator", SUPPORT), Map.entry("archaeologist", SUPPORT));

    public static CompanionRole of(@Nullable String crewRole) {
        return crewRole == null ? FIGHTER : BY_CREW_ROLE.getOrDefault(crewRole, FIGHTER);
    }

    public static CompanionRole of(@Nullable Notable n) {
        return n == null ? FIGHTER : of(n.crewRole());
    }

    public static CompanionRole of(GLNpc npc) {
        return of(NotableCharacters.get(npc.notableId()));
    }

    public static CompanionRole byId(String id) {
        for (CompanionRole r : values()) if (r.id().equals(id)) return r;
        return FIGHTER;
    }
}
