package com.squidgame.core;

import java.util.Locale;

/**
 * Global difficulty presets. Every game derives its concrete parameters from these
 * generic multipliers (plus its own tables), so "Hard" and "Extreme" consistently mean:
 * tighter timings, harder tasks, stronger opponents and scarcer resources.
 *
 * <p>Pure Java (no Minecraft types) so rules can be unit tested.
 */
public enum Difficulty {
    //            id         time  tol   res   skill  allow
    NORMAL("normal",   1.00, 1.00, 1.00, 0.00, 1.00),
    HARD("hard",       0.85, 0.80, 0.75, 0.12, 0.60),
    EXTREME("extreme", 0.70, 0.60, 0.50, 0.25, 0.25);

    public final String id;
    /** Multiplies every game time limit (smaller = less time). */
    public final double timeScale;
    /** Multiplies spatial / timing tolerances (smaller = tighter, e.g. dalgona needle tolerance). */
    public final double toleranceScale;
    /** Multiplies helpful resources (licks, stamina pool, starting marbles bonus...). */
    public final double resourceScale;
    /** Added to every NPC's effective skill (0..1 clamped by the consumer): stronger opponents. */
    public final double npcSkillBonus;
    /** Multiplies stopping / reaction allowances (Red Light grace, dodge windows...). */
    public final double allowanceScale;

    Difficulty(String id, double timeScale, double toleranceScale, double resourceScale,
               double npcSkillBonus, double allowanceScale) {
        this.id = id;
        this.timeScale = timeScale;
        this.toleranceScale = toleranceScale;
        this.resourceScale = resourceScale;
        this.npcSkillBonus = npcSkillBonus;
        this.allowanceScale = allowanceScale;
    }

    public String translationKey() {
        return "squidgame.difficulty." + id;
    }

    public static Difficulty byId(String id, Difficulty fallback) {
        if (id == null) {
            return fallback;
        }
        String s = id.toLowerCase(Locale.ROOT);
        for (Difficulty d : values()) {
            if (d.id.equals(s)) {
                return d;
            }
        }
        return fallback;
    }

    public Difficulty next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
