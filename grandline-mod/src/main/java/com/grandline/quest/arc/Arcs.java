package com.grandline.quest.arc;

import com.grandline.player.CharacterData;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.arc.Arc.Decision;
import com.grandline.quest.arc.Arc.Role;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The chapters of the saga ({@link Arc}) and what the rest of the game asks about them: which chapter a quest belongs
 * to, the decision a step holds and what the player chose, where a chain goes next after a choice, and what a notable
 * is to the story (to protect the people a later scene needs). Chapters are registered by the chapter files while the
 * quest registry initialises ({@link #register}).
 */
public final class Arcs {
    /** Targets of "lesson" objectives (com.grandline.combat.Lessons reports them). */
    public static final List<String> LESSONS = List.of("strike", "guard", "dodge", "technique");
    /** Flag set once a character's quests have been brought over to the chapter structure ({@link StoryMigration}). */
    public static final String MIGRATED = "story_v2";

    private static final Map<String, Arc> BY_ID = new LinkedHashMap<>();
    private static final Map<String, Arc> BY_STEP = new HashMap<>();
    private static final Map<String, Decision> DECISIONS = new LinkedHashMap<>();
    private static final Map<String, Decision> DECISION_BY_STEP = new HashMap<>();
    private static final Map<String, List<Arc>> BY_CAST = new HashMap<>();
    /** notable id → base ids of steps that fight it (defeat or arrest objectives), filled lazily from the registry. */
    private static Map<String, Set<String>> fightSteps;

    private Arcs() {}

    // ================================================================== registration

    /** Called by StoryQuests while the quest registry initialises: every chapter, in saga order. */
    public static void register(Map<String, QuestDef> all, Set<String> storyIds) {
        ArcBuilder.out = all;
        ArcBuilder.storyIds = storyIds;
        try {
            EastBlueArcs.register();
            BluesArcs.register();
        } finally {
            ArcBuilder.out = null;
            ArcBuilder.storyIds = null;
        }
    }

    /** Chapters listed for the journal whose quests are registered later (Paradise onwards); called after all quests. */
    public static void registerListed() {
        LaterArcs.register();
    }

    static void add(Arc a) {
        if (BY_ID.put(a.id(), a) != null) throw new IllegalStateException("duplicate arc " + a.id());
        for (Arc.Step s : a.steps()) if (BY_STEP.putIfAbsent(s.quest(), a) != null) throw new IllegalStateException("step in two arcs: " + s.quest());
        for (Decision d : a.decisions()) {
            if (DECISIONS.put(d.id(), d) != null) throw new IllegalStateException("duplicate decision " + d.id());
            DECISION_BY_STEP.put(d.step(), d);
            for (Arc.Choice c : d.choices()) if (c.next() != null) BY_STEP.putIfAbsent(c.next(), a);
        }
        for (Arc.Cast c : a.cast()) BY_CAST.computeIfAbsent(c.who(), k -> new ArrayList<>()).add(a);
        fightSteps = null;
    }

    // ================================================================== lookups

    public static Collection<Arc> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    @Nullable
    public static Arc get(String id) {
        return BY_ID.get(id);
    }

    /** The chapter a quest (or any faction variant of it) belongs to, or null. */
    @Nullable
    public static Arc arcOf(String questId) {
        return BY_STEP.get(QuestRegistry.baseId(questId));
    }

    @Nullable
    public static Decision decision(String id) {
        return DECISIONS.get(id);
    }

    public static Collection<Decision> decisions() {
        return Collections.unmodifiableCollection(DECISIONS.values());
    }

    /** The decision made during this step (by base id), or null. */
    @Nullable
    public static Decision decisionAt(String questId) {
        return DECISION_BY_STEP.get(QuestRegistry.baseId(questId));
    }

    /** What the character chose in a decision, or null if they have not decided. */
    @Nullable
    public static String choice(CharacterData d, String decisionId) {
        String p = "choice:" + decisionId + "=";
        for (String f : d.flags) if (f.startsWith(p)) return f.substring(p.length());
        return null;
    }

    public static String choiceFlag(String decisionId, String choiceId) {
        return "choice:" + decisionId + "=" + choiceId;
    }

    /** The choices this character made in a chapter (decision id → choice id). */
    public static Map<String, String> choices(CharacterData d, Arc a) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Decision dec : a.decisions()) {
            String c = choice(d, dec.id());
            if (c != null) m.put(dec.id(), c);
        }
        return m;
    }

    /** Where a chain goes after this quest: the chosen branch of its decision, else its own {@code next}. */
    @Nullable
    public static String next(QuestDef q, CharacterData d) {
        Decision dec = decisionAt(q.id());
        if (dec != null) {
            String c = choice(d, dec.id());
            Arc.Choice ch = c == null ? null : dec.choice(c);
            if (ch != null && ch.next() != null) return ch.next();
        }
        return q.next();
    }

    /** The active quest id (exact variant) of a step for this character, or null. */
    @Nullable
    public static String active(CharacterData d, String baseId) {
        for (String id : d.questProgress.keySet()) if (QuestRegistry.baseId(id).equals(baseId)) return id;
        return null;
    }

    // ================================================================== the cast

    /** What a notable is to the story: the strongest role over every chapter it appears in, or null. */
    @Nullable
    public static Role roleOf(String notableId) {
        List<Arc> arcs = BY_CAST.get(notableId);
        if (arcs == null) return null;
        Role best = null;
        for (Arc a : arcs) {
            Arc.Cast c = a.castMember(notableId);
            if (c == null) continue;
            // a foe in one chapter is a foe; otherwise the first role listed
            if (best == null || c.role().foe() && !best.foe()) best = c.role();
        }
        return best;
    }

    /** Whether a notable is a foe in a chapter written to the full standard (its fight is staged). */
    public static boolean stagedFoe(String notableId) {
        List<Arc> arcs = BY_CAST.get(notableId);
        if (arcs == null) return false;
        for (Arc a : arcs) {
            Arc.Cast c = a.castMember(notableId);
            if (a.full() && c != null && c.role().foe()) return true;
        }
        return false;
    }

    /** Chapters a notable appears in. */
    public static List<Arc> arcsWith(String notableId) {
        return BY_CAST.getOrDefault(notableId, List.of());
    }

    /** Base ids of the steps that fight this notable (defeat or arrest objectives on it). */
    public static Set<String> fightSteps(String notableId) {
        Map<String, Set<String>> m = fightSteps;
        if (m == null) {
            m = new HashMap<>();
            for (QuestDef q : QuestRegistry.all()) {
                if (QuestRegistry.isGenerated(q.id())) continue;
                for (QuestDef.Objective o : q.objectives())
                    if (o.type().equals("defeat") || o.type().equals("arrest"))
                        m.computeIfAbsent(o.target(), k -> new HashSet<>()).add(QuestRegistry.baseId(q.id()));
            }
            fightSteps = m;
        }
        return m.getOrDefault(notableId, Set.of());
    }

    /**
     * Whether this character has reached the fight with a staged foe: one of its fight steps is active or done (any
     * variant). Before that, the foe only yields to them.
     */
    public static boolean reachedFight(CharacterData d, String notableId) {
        for (String step : fightSteps(notableId)) {
            if (QuestRegistry.isDone(d, step) || active(d, step) != null) return true;
        }
        return false;
    }

    // ================================================================== the journal

    /** One line per completed step of the chapter, in order (choice-specific lines where the decision changed things). */
    public static List<String> recapKeys(CharacterData d, Arc a, java.util.function.Predicate<String> exists) {
        List<String> out = new ArrayList<>();
        Map<String, String> chosen = choices(d, a);
        List<String> order = new ArrayList<>();
        for (Arc.Step s : a.steps()) {
            order.add(s.quest());
            Decision dec = DECISION_BY_STEP.get(s.quest());
            if (dec != null) for (Arc.Choice c : dec.choices()) if (c.next() != null && !order.contains(c.next()) && a.step(c.next()) == null) order.add(c.next());
        }
        for (String step : order) {
            if (!QuestRegistry.isDone(d, step)) continue;
            String key = null;
            for (String c : chosen.values()) if (exists.test("recap.grandline." + step + "." + c)) key = "recap.grandline." + step + "." + c;
            if (key == null && exists.test("recap.grandline." + step)) key = "recap.grandline." + step;
            if (key != null) out.add(key);
        }
        return out;
    }
}
