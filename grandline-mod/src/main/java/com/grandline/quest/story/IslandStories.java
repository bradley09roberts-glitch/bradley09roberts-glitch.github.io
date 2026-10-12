package com.grandline.quest.story;

import com.grandline.GrandLine;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableCharacters.Notable;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.story.StoryModel.*;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A local story on every original island, and a richer job board on every settled island, generated from the world
 * seed. Generation is a pure function of the world plan: nothing is saved, the same seed regenerates the same people,
 * places and quests (players' progress and the people's fates are saved by the usual quest and notable systems).
 * Installed into {@link QuestRegistry} and {@link NotableCharacters} when the server starts.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class IslandStories {
    private static WorldPlan installedFor;
    private static Generated current;
    private static final Map<String, IslandStory> BY_ISLAND = new HashMap<>();
    private static final Map<String, IslandStory> BY_PERSON = new HashMap<>();
    private static final Map<String, Person> PEOPLE = new HashMap<>();
    /** Conditional person id → quests whose objectives need them (they only appear for a player on one of those). */
    private static final Map<String, Set<String>> CONDITIONAL = new HashMap<>();
    private static final Map<String, List<SiteRef>> QUEST_SITES = new HashMap<>();
    private static final Map<String, Site> SITES = new HashMap<>();

    // ================================================================== generation (pure)

    /** The story of one original island (null for canon islands). */
    @Nullable
    public static IslandStory generate(WorldPlan plan, Island isl) {
        if (isl.canon || plan.kind != WorldPlan.Kind.OCEAN) return null;
        StoryContext c = new StoryContext(plan, isl, "", 0);
        double total = 0;
        StoryTemplate[] all = StoryTemplate.values();
        double[] w = new double[all.length];
        for (int i = 0; i < all.length; i++) total += w[i] = Math.max(0, all[i].weight(c));
        double roll = c.r.nextDouble() * total;
        StoryTemplate pick = StoryTemplate.RIVAL_CREW;
        for (int i = 0; i < all.length; i++) {
            if (w[i] <= 0) continue;
            roll -= w[i];
            if (roll <= 0) {
                pick = all[i];
                break;
            }
        }
        return generate(c, pick);
    }

    /** A story of a given template on an island, with an id prefix of its own (tests play every template this way). */
    public static IslandStory generate(WorldPlan plan, Island isl, StoryTemplate t, String prefix) {
        return generate(new StoryContext(plan, isl, prefix, 0), t);
    }

    private static IslandStory generate(StoryContext c, StoryTemplate t) {
        StoryBuilder b = new StoryBuilder(c, t);
        t.build(b);
        return b.build();
    }

    /** Whether a template can be generated on this island at all. */
    public static boolean fits(WorldPlan plan, Island isl, StoryTemplate t) {
        return !isl.canon && t.weight(new StoryContext(plan, isl, "", 0)) > 0;
    }

    /** Every original island's story plus every settled island's jobs, for this plan. */
    public static Generated generateAll(WorldPlan plan) {
        List<IslandStory> stories = new ArrayList<>();
        List<QuestDef> quests = new ArrayList<>();
        List<Person> people = new ArrayList<>();
        Map<String, Component> titles = new LinkedHashMap<>(), descs = new LinkedHashMap<>();
        Map<String, List<SiteRef>> qs = new LinkedHashMap<>();
        Map<String, Site> sites = new LinkedHashMap<>();
        for (Island isl : plan.islands) {
            IslandStory s = generate(plan, isl);
            if (s == null) continue;
            stories.add(s);
            add(s, quests, people, titles, descs, qs, sites);
        }
        Map<String, JobBoard.StartGift> gifts = new LinkedHashMap<>();
        JobBoard.generate(plan, stories, quests, people, titles, descs, qs, sites, gifts);
        return new Generated(stories, quests, people, titles, descs, qs, new ArrayList<>(sites.values()), gifts);
    }

    static void add(IslandStory s, List<QuestDef> quests, List<Person> people, Map<String, Component> titles, Map<String, Component> descs,
                    Map<String, List<SiteRef>> qs, Map<String, Site> sites) {
        quests.addAll(s.quests());
        people.addAll(s.people());
        titles.putAll(s.titles());
        descs.putAll(s.descs());
        qs.putAll(s.sites());
        for (Site site : s.places()) sites.put(site.key(), site);
    }

    /** A compact fingerprint of a plan's stories (determinism checks): template, people, quests and objectives. */
    public static String digest(Generated g) {
        StringBuilder sb = new StringBuilder();
        for (IslandStory s : g.stories()) {
            sb.append(s.island()).append(':').append(s.template().id);
            for (Person p : s.people()) sb.append('|').append(p.name()).append('/').append(p.role()).append('@').append(p.x()).append(',').append(p.z());
            for (QuestDef q : s.quests()) sb.append('|').append(q.id()).append(q.objectives());
            sb.append('\n');
        }
        for (QuestDef q : g.quests()) if (q.giverRole().equals("bounty_board")) sb.append(q.id()).append(q.objectives()).append('\n');
        return sb.toString();
    }

    // ================================================================== install

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onStarting(ServerStartingEvent e) {
        ensure(e.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        uninstall();
    }

    /** Install this server's generated stories and jobs if not done yet (idempotent; cheap when installed). */
    public static void ensure(MinecraftServer server) {
        if (server.overworld() == null) return; // levels not created yet
        WorldPlan plan = WorldPlan.of(server);
        if (plan == installedFor) return;
        if (plan == null) {
            uninstall();
            return;
        }
        long t0 = System.nanoTime();
        install(plan, generateAll(plan));
        GrandLine.LOGGER.info("Island stories: {} stories, {} generated quests, {} generated people, {} places in {} ms", current.stories().size(),
                current.quests().size(), current.people().size(), current.sites().size(), (System.nanoTime() - t0) / 1_000_000);
    }

    /** Install a generated set for a plan, replacing what was there (tests install extra stories this way too). */
    public static void install(WorldPlan plan, Generated g) {
        uninstall();
        current = g;
        List<Notable> notables = new ArrayList<>();
        Map<String, Component> names = new HashMap<>(), titles = new HashMap<>();
        for (Person p : g.people()) {
            Island home = plan.byId(p.island());
            if (home == null) continue;
            // notables live at an offset from their island's centre
            notables.add(NotableCharacters.generated(p.id(), p.group(), p.faction(), p.role(), p.tier(), p.island(), p.x() - home.x, p.z() - home.z,
                    p.style(), 0, 0, p.race(), p.weapon(), p.disposition()));
            names.put(p.id(), Component.literal(p.name()));
            titles.put(p.id(), p.title());
            PEOPLE.put(p.id(), p);
        }
        NotableCharacters.installGenerated(notables, names, titles);
        QuestRegistry.installGenerated(g.quests(), g.titles(), g.descs());
        for (IslandStory s : g.stories()) {
            BY_ISLAND.putIfAbsent(s.island(), s);
            for (Person p : s.people()) BY_PERSON.put(p.id(), s);
        }
        QUEST_SITES.putAll(g.questSites());
        for (Site s : g.sites()) SITES.put(s.key(), s);
        for (QuestDef q : g.quests())
            for (QuestDef.Objective o : q.objectives()) {
                Person p = PEOPLE.get(o.target());
                if (p != null && p.conditional()) CONDITIONAL.computeIfAbsent(p.id(), k -> new HashSet<>()).add(q.id());
            }
        JobBoard.installed(g);
        installedFor = plan;
    }

    public static void uninstall() {
        if (current == null && installedFor == null) return;
        QuestRegistry.clearGenerated();
        NotableCharacters.clearGenerated();
        BY_ISLAND.clear();
        BY_PERSON.clear();
        PEOPLE.clear();
        CONDITIONAL.clear();
        QUEST_SITES.clear();
        SITES.clear();
        JobBoard.uninstalled();
        current = null;
        installedFor = null;
    }

    // ================================================================== lookups

    @Nullable
    public static Generated current() {
        return current;
    }

    @Nullable
    public static IslandStory storyOf(String islandId) {
        return BY_ISLAND.get(islandId);
    }

    @Nullable
    public static Person person(String id) {
        return PEOPLE.get(id);
    }

    @Nullable
    public static Site site(String key) {
        return SITES.get(key);
    }

    public static List<SiteRef> sitesOf(String questId) {
        return QUEST_SITES.getOrDefault(questId, List.of());
    }

    public static boolean hasSites() {
        return !QUEST_SITES.isEmpty();
    }

    /** Whether a generated person may appear near this player (conditional people only for those whose job needs them). */
    public static boolean mayAppear(Notable n, @Nullable ServerPlayer sp) {
        Set<String> need = CONDITIONAL.get(n.id());
        if (need == null || sp == null) return true;
        CharacterData d = Characters.get(sp);
        for (String q : d.questProgress.keySet()) if (need.contains(q)) return true;
        return false;
    }

    /** What a generated person says (before, during or after their island's story); null for everyone else. */
    @Nullable
    public static Component greeting(Notable n, CharacterData d) {
        Person p = PEOPLE.get(n.id());
        if (p == null) return null;
        IslandStory s = BY_PERSON.get(n.id());
        if (s == null) return JobBoard.greeting(p);
        boolean done = QuestRegistry.isDone(d, s.lastStep());
        String key = "story.grandline." + s.template().id + (done && p.id().equals(s.giver()) ? ".thanks." : ".greet.") + p.slot();
        return Component.translatable(key, s.args());
    }

    /** A rumour about an original island's story (islanders tell it after their usual gossip), or null. */
    @Nullable
    public static Component rumor(String islandId) {
        IslandStory s = BY_ISLAND.get(islandId);
        return s == null ? null : Component.translatable("story.grandline." + s.template().id + ".rumor", s.args());
    }

    /** Hook from {@code Quests.start}: job extras (wanted posters, letters). */
    public static void onQuestStarted(ServerPlayer sp, QuestDef q) {
        JobBoard.onStarted(sp, q);
    }

    private IslandStories() {}
}
