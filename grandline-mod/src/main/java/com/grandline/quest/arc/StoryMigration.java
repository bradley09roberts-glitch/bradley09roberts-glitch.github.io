package com.grandline.quest.arc;

import com.grandline.GrandLine;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

/**
 * Brings characters from before the chapter structure onto it, once (flag {@link Arcs#MIGRATED}), at login:
 * <ul>
 * <li>a quest id that no longer exists is replaced by the step that took its place;</li>
 * <li>a quest in progress whose objectives changed keeps the progress of every objective it still has (matched by
 * type and target); parts of the combat lesson added to a step already under way count as shown;</li>
 * <li>steps added in front of a step the character has already reached are marked done, without rewards (decision
 * branches and recruitment steps excepted: a decision skipped this way is simply not made, and recruitment stays on
 * offer from the recruit as before);</li>
 * <li>quests that are now complete (objectives already met) are finished and paid as usual.</li>
 * </ul>
 * Nobody is sent back and nobody is left without a next step: everything the character had done still counts.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class StoryMigration {
    /** Objectives of quests as they were before the chapters ("type:target" in order), for those that changed. */
    private static final Map<String, List<String>> LEGACY = new HashMap<>();
    /** Old quest ids that no longer exist → the step that replaced them. */
    private static final Map<String, String> RENAMED = new HashMap<>();

    private StoryMigration() {}

    static void legacy(String questId, String... objectives) {
        LEGACY.put(questId, List.of(objectives));
    }

    static void renamed(String oldId, String newId) {
        RENAMED.put(oldId, newId);
    }

    /** Quest ids whose objectives changed (self-test). */
    public static Set<String> legacyIds() {
        return Collections.unmodifiableSet(LEGACY.keySet());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            try {
                migrate(sp);
            } catch (RuntimeException ex) {
                GrandLine.LOGGER.error("Story migration failed for {}", sp.getName().getString(), ex);
            }
        }
    }

    /** Migrate this character if it has not been yet. Returns the number of changes made. */
    public static int migrate(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        if (!d.created || d.flags.contains(Arcs.MIGRATED)) return 0;
        int changes = 0;
        // 1. ids that were replaced
        for (Map.Entry<String, String> r : RENAMED.entrySet()) {
            if (d.completedQuests.remove(r.getKey())) {
                d.completedQuests.add(r.getValue());
                changes++;
            }
            CompoundTag p = d.questProgress.remove(r.getKey());
            if (p != null) {
                keepBeaten(d, r.getKey(), p, null);
                if (!d.questProgress.containsKey(r.getValue()) && !QuestRegistry.isDone(d, r.getValue())) d.questProgress.put(r.getValue(), remap(r.getKey(), r.getValue(), p));
                changes++;
            }
            if (d.trackedQuest.equals(r.getKey())) d.trackedQuest = r.getValue();
        }
        // 2. quests under way whose objectives changed (a fight already won there is remembered: it may now be a later step)
        for (String id : List.copyOf(d.questProgress.keySet())) {
            if (!LEGACY.containsKey(id) || QuestRegistry.get(id) == null) continue;
            keepBeaten(d, id, d.questProgress.get(id), null);
            d.questProgress.put(id, remap(id, id, d.questProgress.get(id)));
            changes++;
        }
        // 3. steps in front of a step already reached; a fight won in an old quest that has moved to a later step of the
        //    same chapter counts as reached (and done) too
        Set<String> reached = new HashSet<>();
        for (String id : d.completedQuests) reached.add(QuestRegistry.baseId(id));
        for (String id : d.questProgress.keySet()) reached.add(QuestRegistry.baseId(id));
        Map<Arc, Set<String>> wonBefore = new HashMap<>();
        for (String id : List.copyOf(d.completedQuests)) {
            Arc a = Arcs.arcOf(id);
            if (a != null && (LEGACY.containsKey(id) || LEGACY.containsKey(QuestRegistry.baseId(id))))
                keepBeaten(d, LEGACY.containsKey(id) ? id : QuestRegistry.baseId(id), null, wonBefore.computeIfAbsent(a, k -> new HashSet<>()));
        }
        for (Arc a : Arcs.all()) {
            if (!a.full()) continue;
            Set<String> branches = new HashSet<>(), inArc = new HashSet<>();
            for (Arc.Decision dec : a.decisions()) for (Arc.Choice c : dec.choices()) if (c.next() != null) branches.add(c.next());
            for (Arc.Step s : a.steps()) inArc.add(s.quest());
            Set<String> won = wonBefore.getOrDefault(a, Set.of());
            if (!won.isEmpty())
                for (Arc.Step s : a.steps()) {
                    QuestDef q = QuestRegistry.get(QuestRegistry.resolve(s.quest(), d));
                    if (q == null || reached.contains(s.quest()) || branches.contains(s.quest())) continue;
                    boolean fight = false, allWon = true;
                    for (QuestDef.Objective o : q.objectives()) {
                        if (!o.type().equals("defeat") && !o.type().equals("arrest")) continue;
                        fight = true;
                        allWon &= won.contains(o.target());
                    }
                    if (fight && allWon && q.objectives().stream().allMatch(o -> o.type().equals("defeat") || o.type().equals("arrest"))) {
                        d.completedQuests.add(q.id());
                        if (q.flag() != null) d.flags.add(q.flag());
                        reached.add(s.quest());
                        changes++;
                    }
                }
            Set<String> reachedHere = new HashSet<>(reached);
            reachedHere.retainAll(inArc); // only a later step of the same chapter counts (other chapters can be played on their own)
            if (reachedHere.isEmpty()) continue;
            for (Arc.Step s : a.steps()) {
                String base = s.quest();
                if (branches.contains(base) || reached.contains(base)) continue;
                QuestDef q = QuestRegistry.get(base);
                if (q == null || q.arc().equals("recruit")) continue;
                if (!leadsTo(base, reachedHere, inArc)) continue;
                d.completedQuests.add(QuestRegistry.resolve(base, d));
                if (q.flag() != null) d.flags.add(q.flag());
                changes++;
            }
        }
        d.flags.add(Arcs.MIGRATED);
        // 4. anything now complete is finished (and paid) the usual way
        for (String id : List.copyOf(d.questProgress.keySet())) if (Quests.recheck(sp, id)) changes++;
        CharacterManager.markDirty(sp);
        if (changes > 0) GrandLine.LOGGER.info("Story migration for {}: {} change(s)", sp.getName().getString(), changes);
        return changes;
    }

    /**
     * The fights an old quest had won (its defeat and arrest objectives: all of them for a completed quest, the met ones
     * for progress under way) are remembered as beaten, so the step that now holds that fight counts it at once.
     */
    static void keepBeaten(CharacterData d, String oldId, @org.jetbrains.annotations.Nullable CompoundTag prog, @org.jetbrains.annotations.Nullable Set<String> into) {
        List<String> old = LEGACY.get(oldId);
        if (old == null) return;
        for (int j = 0; j < old.size(); j++) {
            String[] p = old.get(j).split(":", 2);
            if (p.length < 2 || !(p[0].equals("defeat") || p[0].equals("arrest"))) continue;
            if (prog != null && prog.getInt("o" + j) < 1) continue;
            d.flags.add("beat:" + p[1]);
            if (into != null) into.add(p[1]);
        }
    }

    /** Progress of an old quest carried to a new definition: matching objectives keep their counts. */
    static CompoundTag remap(String oldId, String newId, CompoundTag prog) {
        QuestDef q = QuestRegistry.get(newId);
        List<String> old = LEGACY.get(oldId);
        if (q == null) return prog.copy();
        CompoundTag np = new CompoundTag();
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDef.Objective o = q.objectives().get(i);
            if (o.type().equals("lesson")) { // a veteran of the old step has fought already
                np.putInt("o" + i, o.count());
                continue;
            }
            int j = old == null ? i : old.indexOf(o.type() + ":" + o.target());
            if (j >= 0 && prog.contains("o" + j)) np.putInt("o" + i, Math.min(o.count(), prog.getInt("o" + j)));
        }
        return np;
    }

    /**
     * Whether the chain from this step (following next pointers, every decision branch and faction variant, staying
     * inside {@code within}) reaches one of these.
     */
    static boolean leadsTo(String from, Set<String> targets, Set<String> within) {
        Deque<String> todo = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        todo.add(from);
        while (!todo.isEmpty() && seen.size() < 400) {
            String cur = todo.poll();
            if (!seen.add(cur)) continue;
            QuestDef q = QuestRegistry.get(cur);
            if (q == null) continue;
            List<String> nexts = new ArrayList<>();
            if (q.next() != null) nexts.add(QuestRegistry.baseId(q.next()));
            Arc.Decision dec = Arcs.decisionAt(cur);
            if (dec != null) for (Arc.Choice c : dec.choices()) if (c.next() != null) nexts.add(c.next());
            // faction variants may continue differently: follow them too
            for (String v : List.of("marine", "revolutionary", "bounty_hunter", "civilian")) {
                QuestDef vq = QuestRegistry.get(cur + "." + v);
                if (vq != null && vq.next() != null) nexts.add(QuestRegistry.baseId(vq.next()));
            }
            for (String n : nexts) {
                if (targets.contains(n)) return true;
                if (within.contains(n)) todo.add(n);
            }
        }
        return false;
    }
}
