package com.grandline.quest.arc;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * One chapter of the saga: a place, a cast, a problem, and steps that run arrival → cast → problem → discovery →
 * escalation → a decision → climax → aftermath. The steps are ordinary quests ({@link com.grandline.quest.QuestDef})
 * chained by {@code next}, so tracking, the minimap trail and the journal keep working; this record only adds what a
 * story needs on top: who is who, why it matters, which step plays which beat, and the decision with its branches.
 * Text: {@code arc.grandline.<id>} (title), {@code .why} (the problem), {@code .cast.<who>} (one line per cast member),
 * {@code recap.grandline.<step>[.<choice>]} (one line per completed step), scenes under {@code scene.grandline.<step>.*}.
 *
 * @param part  part label key suffix ({@code arc.grandline.part.<part>}): "east_blue", "north_blue", "paradise"...
 * @param full  whether the chapter is written to the full standard (scenes, decision, consequences); chapters that are
 *              not are still listed for the journal (chapter, cast, problem, recap)
 */
public record Arc(String id, int chapter, String part, String island, List<Cast> cast, List<Step> steps, List<Decision> decisions, boolean full) {

    /** The story beats a step can play. A full chapter covers all of them, in this order. */
    public enum Beat { ARRIVAL, CAST, PROBLEM, DISCOVERY, ESCALATION, DECISION, CLIMAX, AFTERMATH }

    /**
     * What a cast member is to the story. Allies, givers, the missing and recruits are protected: they never die to
     * stray blows and always yield to players. Villains cannot be killed before the player's fight step (they yield to
     * players who are not there yet and shrug off everything else).
     */
    public enum Role {
        GIVER, ALLY, MISSING, RECRUIT, RIVAL, VILLAIN, BOSS;

        public boolean protectedAlways() {
            return this == GIVER || this == ALLY || this == MISSING || this == RECRUIT;
        }

        public boolean foe() {
            return this == VILLAIN || this == BOSS;
        }
    }

    /** A cast member: a notable id and what they are to this chapter. */
    public record Cast(String who, Role role) {}

    /** A step: its quest id (the base id; faction variants share its beats and scenes) and the beats it plays. */
    public record Step(String quest, EnumSet<Beat> beats) {}

    /**
     * A bounded choice, made in a scene presented by {@code presenter} while {@code step} is active (a "choice"
     * objective whose target is this decision's id). Each choice sets {@code choice:<id>=<choice>} and may send the
     * story down its own next step; its consequences are listed in {@link Consequences}.
     */
    public record Decision(String id, String step, String presenter, List<Choice> choices) {
        @Nullable
        public Choice choice(String id) {
            for (Choice c : choices) if (c.id().equals(id)) return c;
            return null;
        }
    }

    /** One option of a decision; {@code next} is the step it leads to (null = the decision step's own next). */
    public record Choice(String id, @Nullable String next) {}

    public String titleKey() {
        return "arc.grandline." + id;
    }

    public String whyKey() {
        return "arc.grandline." + id + ".why";
    }

    public String castKey(String who) {
        return "arc.grandline." + id + ".cast." + who;
    }

    public String partKey() {
        return "arc.grandline.part." + part;
    }

    @Nullable
    public Step step(String baseId) {
        for (Step s : steps) if (s.quest().equals(baseId)) return s;
        return null;
    }

    public int indexOf(String baseId) {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).quest().equals(baseId)) return i;
        return -1;
    }

    @Nullable
    public Cast castMember(String who) {
        for (Cast c : cast) if (c.who().equals(who)) return c;
        return null;
    }
}
