package com.grandline.quest.arc;

import com.grandline.GrandLine;
import com.grandline.quest.QuestDef;
import com.grandline.quest.arc.Arc.Beat;
import com.grandline.quest.arc.Arc.Cast;
import com.grandline.quest.arc.Arc.Choice;
import com.grandline.quest.arc.Arc.Decision;
import com.grandline.quest.arc.Arc.Role;
import com.grandline.quest.arc.Arc.Step;
import com.grandline.registry.ModDataComponents;
import com.grandline.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * The little language the chapter files ({@code *Arcs.java}) are written in: a chapter with its cast, and its steps as
 * quests (objectives, rewards, chain), each tagged with the beats it plays, plus the decision and its branches and the
 * places the steps happen at. Everything is registered at class-init time into the quest registry and {@link Arcs}.
 */
final class ArcBuilder {
    /** Where quests go while registering (the registry's map) and the saga's id set. */
    static Map<String, QuestDef> out;
    static Set<String> storyIds;

    final String id, part, island;
    final int chapter;
    final boolean full;
    final List<Cast> cast = new ArrayList<>();
    final List<Step> steps = new ArrayList<>();
    final List<Decision> decisions = new ArrayList<>();

    private ArcBuilder(String id, int chapter, String part, String island, boolean full) {
        this.id = id;
        this.chapter = chapter;
        this.part = part;
        this.island = island;
        this.full = full;
    }

    /** A chapter written to the full standard. */
    static ArcBuilder arc(String id, int chapter, String part, String island) {
        return new ArcBuilder(id, chapter, part, island, true);
    }

    /** A chapter listed for the journal only (its quests are defined elsewhere and keep their old shape). */
    static ArcBuilder listed(String id, int chapter, String part, String island) {
        return new ArcBuilder(id, chapter, part, island, false);
    }

    ArcBuilder cast(String who, Role role) {
        cast.add(new Cast(who, role));
        return this;
    }

    /** A step written here: registers its quest and records its beats. */
    ArcBuilder step(S q, Beat... beats) {
        q.arcId = id;
        q.add();
        steps.add(new Step(q.id, beats.length == 0 ? EnumSet.noneOf(Beat.class) : EnumSet.copyOf(Arrays.asList(beats))));
        return this;
    }

    /** A faction variant of a step already added ("shells_1.marine"): same beats and scenes as its base. */
    ArcBuilder variant(S q) {
        q.arcId = id;
        q.add();
        return this;
    }

    /** A step whose quest is defined elsewhere (journal-only chapters). */
    ArcBuilder existing(String questId, Beat... beats) {
        steps.add(new Step(questId, beats.length == 0 ? EnumSet.noneOf(Beat.class) : EnumSet.copyOf(Arrays.asList(beats))));
        return this;
    }

    /** A decision made while {@code step} is active, in a scene presented by {@code presenter}. */
    ArcBuilder decision(String decisionId, String step, String presenter, Choice... choices) {
        decisions.add(new Decision(decisionId, step, presenter, List.of(choices)));
        return this;
    }

    static Choice choice(String id, @Nullable String next) {
        return new Choice(id, next);
    }

    void add() {
        Arcs.add(new Arc(id, chapter, part, island, List.copyOf(cast), List.copyOf(steps), List.copyOf(decisions), full));
    }

    // ================================================================== steps

    /** A step quest offered by a notable. */
    static S q(String id, String notableGiver, String island) {
        return new S(id, "notable:" + notableGiver, island);
    }

    /** A step quest offered by an island role. */
    static S qRole(String id, String role, String island) {
        return new S(id, role, island);
    }

    static final class S {
        final String id, giver, island;
        String arcId, arcTag;
        final List<QuestDef.Objective> obj = new ArrayList<>();
        int xp;
        long beli, bounty;
        final Map<String, Integer> rep = new LinkedHashMap<>();
        final List<Supplier<ItemStack>> items = new ArrayList<>();
        final List<String> req = new ArrayList<>();
        String next, flag;

        S(String id, String giver, String island) {
            this.id = id;
            this.giver = giver;
            this.island = island;
        }

        S o(String type, String target, int n) {
            obj.add(new QuestDef.Objective(type, target, n));
            return this;
        }

        S kill(String role, int n) { return o("kill", role, n); }
        S defeat(String notable) { return o("defeat", notable, 1); }
        S arrest(String who) { return o("arrest", who, 1); }
        S talk(String who) { return o("talk", who, 1); }
        S talk(String who, int n) { return o("talk", who, n); }
        S discover(String island) { return o("discover", island, 1); }
        S poneglyph(int n) { return o("poneglyph", "any", n); }
        S collect(String item, int n) { return o("collect", item, n); }
        /** Go to a place of the step's island (a "reach" objective on a chapter place, see {@link #site}). */
        S reach(String name, String anchor) {
            ArcSites.declare(id, island, obj.size(), name, anchor, null);
            return o("reach", "arc." + name, 1);
        }
        S slay(String kind, int n) { return o("slay", kind, n); }
        /** The four parts of the combat lesson (land a blow, guard, dodge, use a technique). */
        S lesson() {
            for (String t : Arcs.LESSONS) o("lesson", t, 1);
            return this;
        }
        /** The player's decision (made in the presenter's scene). */
        S choice(String decision) { return o("choice", decision, 1); }
        S reward(int xp, long beli) { this.xp = xp; this.beli = beli; return this; }
        S bounty(long b) { bounty = b; return this; }
        S rep(String key, int v) { rep.put(key, v); return this; }
        S item(Supplier<ItemStack> s) { items.add(s); return this; }
        S req(String... ids) { req.addAll(List.of(ids)); return this; }
        S next(String id) { next = id; return this; }
        S flag(String f) { flag = f; return this; }
        /** The quest's "arc" tag when it must differ from the chapter id ("recruit": recruitment quests). */
        S tag(String t) { arcTag = t; return this; }

        /**
         * A place this step happens at: objective {@code objective} (or -1 for the whole step) is done at {@code anchor}
         * on the step's island; for kill objectives, foes of {@code role} turn up there while the player is near.
         * Anchors: "wilds", "dock", "center", "home:<notable>", "at:<dx>,<dz>" (from the island's centre).
         */
        S site(int objective, String name, String anchor, @Nullable String role) {
            ArcSites.declare(id, island, objective, name, anchor, role);
            return this;
        }

        void add() {
            QuestDef q = new QuestDef(id, giver, island, List.copyOf(obj), xp, beli, bounty, Map.copyOf(rep), List.copyOf(items), List.copyOf(req), next, flag, false,
                    arcTag != null ? arcTag : arcId);
            if (out.put(id, q) != null) throw new IllegalStateException("duplicate quest " + id);
            storyIds.add(id);
        }
    }

    // ================================================================== reward items

    static Supplier<ItemStack> item(String id, int n) {
        return () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(GrandLine.MODID, id)), n);
    }

    static Supplier<ItemStack> vanilla(String id, int n) {
        return () -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(id)), n);
    }

    static Supplier<ItemStack> scroll(String style) {
        return () -> {
            ItemStack s = new ItemStack(ModItems.STYLE_SCROLL.get());
            s.set(ModDataComponents.STYLE.get(), style);
            return s;
        };
    }

    static Supplier<ItemStack> pose(String island) {
        return () -> {
            ItemStack s = new ItemStack(ModItems.ETERNAL_POSE.get());
            s.set(ModDataComponents.POSE_TARGET.get(), island);
            return s;
        };
    }

    static final Supplier<ItemStack> LOG_POSE = () -> new ItemStack(ModItems.LOG_POSE.get());
    static final Supplier<ItemStack> SEA_CHART = () -> new ItemStack(ModItems.SEA_CHART.get());
    static final Supplier<ItemStack> RUBBING = () -> new ItemStack(ModItems.PONEGLYPH_RUBBING.get());

    static Supplier<ItemStack> meat(int n) {
        return () -> new ItemStack(ModItems.MEAT_ON_BONE.get(), n);
    }

    static Supplier<ItemStack> steak(int n) {
        return () -> new ItemStack(ModItems.SEA_KING_STEAK.get(), n);
    }

    static Supplier<ItemStack> cuffs(int n) {
        return () -> new ItemStack(ModItems.SEASTONE_CUFFS.get(), n);
    }
}
