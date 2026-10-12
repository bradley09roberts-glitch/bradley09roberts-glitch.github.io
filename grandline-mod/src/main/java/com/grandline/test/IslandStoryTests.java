package com.grandline.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grandline.GrandLine;
import com.grandline.combat.style.FightingStyles;
import com.grandline.npc.Archetypes;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.MarkerCollector;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableHooks;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.player.Race;
import com.grandline.quest.QuestCollect;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestEvents;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.story.*;
import com.grandline.quest.story.StoryModel.*;
import com.grandline.registry.ModItems;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Procedural island stories and the job board (quest/story): every original island has a story; generation is
 * deterministic per seed; every objective can progress and every text exists; every template is played end to end on
 * the server with real NPCs (talks, kills, arrests, beasts, places, goods); story people spawn on their islands; a
 * named-bounty job brings out its outlaw only for the hunter and pays out; every other job type completes.
 * Phase 1 runs it after the existing story checks; {@code GL_SELFTEST=stories} runs it on its own (with those checks).
 */
final class IslandStoryTests {
    private static final int LOAD_R = 1;
    private static Generated world;
    private static final Map<StoryTemplate, IslandStory> PLAY = new EnumMap<>(StoryTemplate.class);
    private static final List<Object[]> VARIANTS = new ArrayList<>(); // {IslandStory, Faction}
    private static final List<long[]> TICKETS = new ArrayList<>();
    private static Snapshot snap;
    private static Island jobIsland;

    private static void check(String name, boolean ok, String detail) {
        SelfTest.check(name, ok, detail);
    }

    /** Quick phase only: the test character is created first, as phase 1 does early on (the story checks expect one). */
    static void prelude(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            FakePlayer fp = SelfTest.fake(s);
            BlockPos c = SelfTest.arena(s.overworld()); // where phase 1 keeps the test character
            fp.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            CharacterData d = Characters.get(fp);
            if (d.created) return 0;
            CompoundTag req = new CompoundTag();
            req.putString("name", "Kaito the Tester");
            req.putString("race", "human");
            req.putString("faction", "pirate");
            req.putString("origin", "east");
            req.put("appearance", new com.grandline.player.Appearance().save());
            var err = com.grandline.player.CharacterManager.create(fp, req);
            check("test character created (quick phase)", err == null, err == null ? "" : err.getString());
            return 0;
        });
        // phase 1 has long had Shells Town loaded (ferry tests) when the crew checks run; warm it up the same way, so the
        // freshly spawned crew candidate is already tracked by the level when the checks look for it
        steps.add(s -> {
            var n = NotableCharacters.get("petra");
            Island isl = n == null ? null : WorldPlan.of(s).byId(n.island());
            if (isl == null) return 0;
            ChunkPos cp = new ChunkPos((isl.x + n.dx()) >> 4, (isl.z + n.dz()) >> 4);
            s.overworld().getChunkSource().addRegionTicket(TicketType.FORCED, cp, 3, cp);
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) s.overworld().getChunk(cp.x + dx, cp.z + dz);
            return 100;
        });
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        steps.add(IslandStoryTests::coverage);
        steps.add(IslandStoryTests::determinism);
        steps.add(IslandStoryTests::validity);
        steps.add(IslandStoryTests::texts);
        // ---- every template, played end to end (the world's own story where the world has one)
        steps.add(IslandStoryTests::choose);
        for (StoryTemplate t : StoryTemplate.values()) {
            steps.add(s -> loadStory(s, PLAY.get(t)));
            steps.add(s -> {
                playChecked(s, PLAY.get(t), Faction.CIVILIAN, "");
                unloadAll(s);
                return 0;
            });
        }
        // ---- faction variants: Marines arrest, pirates take a cut
        for (int i = 0; i < 3; i++) {
            int k = i;
            steps.add(s -> k < VARIANTS.size() ? loadStory(s, (IslandStory) VARIANTS.get(k)[0]) : 0);
            steps.add(s -> {
                if (k < VARIANTS.size()) playChecked(s, (IslandStory) VARIANTS.get(k)[0], (Faction) VARIANTS.get(k)[1], " (" + ((Faction) VARIANTS.get(k)[1]).id() + " variant)");
                unloadAll(s);
                return 0;
            });
        }
        steps.add(IslandStoryTests::spawnSample);
        steps.add(IslandStoryTests::spawnSampleCheck);
        // ---- the job board
        steps.add(IslandStoryTests::loadJobs);
        steps.add(IslandStoryTests::board);
        steps.add(IslandStoryTests::huntJob);
        steps.add(IslandStoryTests::otherJobs);
        steps.add(IslandStoryTests::dialogue);
        steps.add(IslandStoryTests::cleanup);
    }

    // ================================================================== data

    private static int coverage(MinecraftServer s) {
        IslandStories.ensure(s);
        WorldPlan plan = WorldPlan.of(s);
        world = IslandStories.current();
        if (plan == null || world == null) {
            check("island stories installed", false, "plan " + plan + ", stories " + world);
            return 0;
        }
        snap = Snapshot.of(Characters.get(SelfTest.fake(s)));
        List<String> missing = new ArrayList<>(), thin = new ArrayList<>();
        Map<StoryTemplate, Integer> count = new EnumMap<>(StoryTemplate.class);
        int originals = 0;
        for (Island i : plan.islands) {
            if (i.canon) continue;
            originals++;
            IslandStory st = IslandStories.storyOf(i.id);
            if (st == null) {
                missing.add(i.id);
                continue;
            }
            count.merge(st.template(), 1, Integer::sum);
            Set<Integer> stepsN = new HashSet<>();
            for (QuestDef q : st.quests()) stepsN.add(Integer.parseInt(QuestRegistry.baseId(q.id()).substring(QuestRegistry.baseId(q.id()).lastIndexOf('_') + 1)));
            Person giver = IslandStories.person(st.giver());
            if (stepsN.size() < 2 || stepsN.size() > 4 || giver == null || !giver.island().equals(i.id)) thin.add(i.id + " steps " + stepsN + " giver " + st.giver());
        }
        check("every original island has a local story", originals > 100 && missing.isEmpty(),
                (originals - missing.size()) + "/" + originals + " original islands; " + count.size() + " templates used " + count + (missing.isEmpty() ? "" : "; missing " + missing));
        check("every story has 2-4 steps and a giver living on its island", thin.isEmpty(), thin.isEmpty() ? world.stories().size() + " stories" : thin.toString());
        check("stories use many templates", count.size() >= 12, count.size() + " of " + StoryTemplate.values().length);
        int people = 0, storyQuests = 0, jobs = 0;
        Map<String, Integer> jobTypes = new TreeMap<>();
        for (IslandStory st : world.stories()) {
            people += st.people().size();
            storyQuests += st.quests().size();
        }
        for (QuestDef q : world.quests())
            if (q.giverRole().equals(JobBoard.GIVER)) {
                jobs++;
                String type = q.id().substring(4, q.id().indexOf('_', 4));
                jobTypes.merge(type, 1, Integer::sum);
            }
        check("story people and job board generated", people >= originals * 3 / 2 && jobs > 500 && jobTypes.keySet().containsAll(List.of("deliver", "escort", "hunt", "fish", "cook", "supply", "wreck"))
                        && (jobTypes.containsKey("nest") || jobTypes.containsKey("camp")),
                people + " story people, " + storyQuests + " story quests, " + world.people().size() + " generated people in all, " + jobs + " jobs " + jobTypes);
        return 0;
    }

    private static int determinism(MinecraftServer s) {
        WorldPlan plan = WorldPlan.of(s);
        long t0 = System.nanoTime();
        String mine = IslandStories.digest(world);
        String again = IslandStories.digest(IslandStories.generateAll(WorldPlan.fresh(plan.seed)));
        long t1 = System.nanoTime();
        Generated other = IslandStories.generateAll(WorldPlan.fresh(plan.seed + 1));
        String diff = IslandStories.digest(other);
        int same = 0;
        Set<String> names = new HashSet<>();
        for (IslandStory st : world.stories()) for (Person p : st.people()) names.add(p.name());
        for (IslandStory st : other.stories()) for (Person p : st.people()) if (names.contains(p.name())) same++;
        check("story generation is deterministic (two independent plans of the same seed agree)", mine.equals(again) && !mine.isEmpty(),
                mine.length() + " chars of fingerprint; regenerated in " + (t1 - t0) / 1_000_000 + " ms");
        check("another seed gives other stories", !mine.equals(diff) && other.stories().size() > 100,
                other.stories().size() + " stories on seed+1; " + same + " people's names shared by chance");
        return 0;
    }

    private static int validity(MinecraftServer s) {
        WorldPlan plan = WorldPlan.of(s);
        Set<String> types = Set.of("talk", "defeat", "arrest", "kill", "slay", "reach", "collect", "discover", "poneglyph", "escort", "sink", "convoy");
        List<String> stuck = new ArrayList<>();
        Map<String, Integer> used = new TreeMap<>();
        for (QuestDef q : world.quests())
            for (QuestDef.Objective o : q.objectives()) {
                used.merge(o.type(), 1, Integer::sum);
                if (!types.contains(o.type()) || !StoryTestsExtra.progresses(s, o)) stuck.add(q.id() + " " + o.type() + ":" + o.target());
                if ((o.type().equals("talk") || o.type().equals("defeat") || o.type().equals("arrest")) && IslandStories.person(o.target()) == null)
                    stuck.add(q.id() + " " + o.type() + " targets nobody generated: " + o.target());
            }
        check("every generated objective uses a type and target the engine progresses", stuck.isEmpty(),
                world.quests().size() + " generated quests " + used + (stuck.isEmpty() ? "" : "; stuck: " + stuck.subList(0, Math.min(8, stuck.size()))));

        // sites bound to objectives exist; kill/slay places hold that role or creature
        List<String> badSites = new ArrayList<>();
        for (var e : world.questSites().entrySet()) {
            QuestDef q = QuestRegistry.get(e.getKey());
            for (SiteRef r : e.getValue()) {
                Site site = IslandStories.site(r.site());
                if (q == null || site == null) badSites.add(e.getKey() + "→" + r.site());
                else if (r.objective() >= 0) {
                    QuestDef.Objective o = q.objectives().get(r.objective());
                    boolean ok = switch (o.type()) {
                        case "kill" -> site.kind() == SiteKind.ROLE && site.what().equals(o.target());
                        case "slay" -> (site.kind() == SiteKind.BEAST || site.kind() == SiteKind.SEA_KING) && site.what().equals(o.target());
                        case "reach" -> site.kind() == SiteKind.REACH && site.key().equals(o.target());
                        default -> false;
                    };
                    if (!ok) badSites.add(q.id() + " o" + r.objective() + " " + o.type() + ":" + o.target() + " at " + site.kind() + ":" + site.what());
                }
            }
        }
        check("objective places match their objectives", badSites.isEmpty(), world.questSites().size() + " quests with places" + (badSites.isEmpty() ? "" : "; " + badSites.subList(0, Math.min(8, badSites.size()))));

        // people: valid roles, styles, weapons, races; homes on dry land of their island
        List<String> bad = new ArrayList<>();
        int checked = 0;
        for (Person p : world.people()) {
            checked++;
            Island home = plan.byId(p.island());
            if (!Archetypes.has(p.role())) bad.add(p.id() + " role " + p.role());
            if (FightingStyles.get(p.style()) == null) bad.add(p.id() + " style " + p.style());
            if (!p.weapon().isEmpty() && BuiltInRegistries.ITEM.get(GrandLine.id(p.weapon())) == net.minecraft.world.item.Items.AIR) bad.add(p.id() + " weapon " + p.weapon());
            if (!Race.byId(p.race()).id().equals(p.race())) bad.add(p.id() + " race " + p.race());
            if (home == null || home.t(p.x(), p.z()) > 1.0) bad.add(p.id() + " home " + p.x() + "," + p.z() + " not on " + p.island());
            if (p.name().isBlank() || NotableCharacters.get(p.id()) == null) bad.add(p.id() + " name/registration");
        }
        check("generated people have valid roles, styles, weapons, races and homes on their islands", bad.isEmpty(),
                checked + " people" + (bad.isEmpty() ? "" : "; " + bad.subList(0, Math.min(8, bad.size()))));

        // faction variants resolve: Marines get arrests, pirates their cut
        int marine = 0, pirate = 0;
        CharacterData m = new CharacterData(), pi = new CharacterData();
        m.faction = Faction.MARINE;
        pi.faction = Faction.PIRATE;
        for (QuestDef q : world.quests()) {
            String vf = QuestRegistry.variantFaction(q.id());
            if (vf == null) continue;
            String base = QuestRegistry.baseId(q.id());
            if (vf.equals("marine") && QuestRegistry.resolve(base, m).equals(q.id()) && !QuestRegistry.requirementsMet(m, QuestRegistry.get(base))) marine++;
            if (vf.equals("pirate") && QuestRegistry.resolve(base, pi).equals(q.id())) pirate++;
        }
        check("story faction variants resolve (Marine arrests, pirate cuts)", marine > 20 && pirate > 5, marine + " Marine variants, " + pirate + " pirate variants");
        return 0;
    }

    /** Every text key generated quests, people, places and rumours use exists in the mod's language file. */
    private static int texts(MinecraftServer s) {
        Set<String> keys = new HashSet<>();
        try (InputStream in = GrandLine.class.getResourceAsStream("/assets/grandline/lang/en_us.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            keys.addAll(o.keySet());
        } catch (Exception e) {
            check("island story texts exist", false, "cannot read en_us.json: " + e);
            return 0;
        }
        Set<String> used = new TreeSet<>(), missing = new TreeSet<>();
        List<Component> all = new ArrayList<>();
        all.addAll(world.titles().values());
        all.addAll(world.descs().values());
        for (Person p : world.people()) all.add(p.title());
        for (Site site : world.sites()) all.add(site.label());
        CharacterData fresh = new CharacterData(), done = new CharacterData();
        for (IslandStory st : world.stories()) {
            all.add(IslandStories.rumor(st.island()));
            done.completedQuests.add(st.lastStep());
        }
        for (Person p : world.people()) {
            var n = NotableCharacters.get(p.id());
            if (n == null) continue;
            all.add(IslandStories.greeting(n, fresh));
            all.add(IslandStories.greeting(n, done));
        }
        for (Component c : all) collectKeys(c, used);
        for (String k : used) if ((k.startsWith("story.") || k.startsWith("job.") || k.startsWith("grandline.") || k.contains(".grandline.")) && !keys.contains(k)) missing.add(k);
        for (String k : List.of("grandline.board.name", "dialogue.grandline.board", "role.grandline.bounty_board", "grandline.poster.last_seen", "grandline.story.reached",
                "job.grandline.letter_for", "job.grandline.where")) if (!keys.contains(k)) missing.add(k);
        check("every island story and job text exists (titles, descriptions, greetings, rumours, places)", missing.isEmpty(),
                used.size() + " keys used" + (missing.isEmpty() ? "" : "; missing " + missing));
        // and renders with its arguments: no leftover placeholders or raw keys (the server's language has the mod's texts)
        List<String> broken = new ArrayList<>();
        int rendered = 0;
        for (Component c : all) {
            if (c == null) continue;
            String text = c.getString();
            rendered++;
            if (text.contains("%") || text.contains("story.grandline.") || text.contains("job.grandline.") || text.isBlank()) broken.add(text);
        }
        check("island story and job texts render with their names and places", broken.isEmpty() && rendered > 1000,
                rendered + " texts rendered, e.g. \"" + (all.isEmpty() || all.get(0) == null ? "" : all.get(0).getString()) + "\" / \""
                        + (world.descs().isEmpty() ? "" : world.descs().values().iterator().next().getString()) + "\"" + (broken.isEmpty() ? "" : "; broken " + broken.subList(0, Math.min(5, broken.size()))));
        return 0;
    }

    private static void collectKeys(Component c, Set<String> out) {
        if (c == null) return;
        if (c.getContents() instanceof TranslatableContents tc) {
            out.add(tc.getKey());
            for (Object a : tc.getArgs()) if (a instanceof Component ac) collectKeys(ac, out);
        }
        for (Component sib : c.getSiblings()) collectKeys(sib, out);
    }

    // ================================================================== playing stories

    /** One story per template (the world's own where it has one, otherwise one made for the test), plus variant runs. */
    private static int choose(MinecraftServer s) {
        WorldPlan plan = WorldPlan.of(s);
        List<IslandStory> extra = new ArrayList<>();
        for (StoryTemplate t : StoryTemplate.values()) {
            IslandStory own = null;
            for (IslandStory st : world.stories()) if (st.template() == t) { own = st; break; }
            if (own == null) own = forced(plan, t, "t" + t.ordinal() + "_", extra);
            if (own != null) PLAY.put(t, own);
        }
        // variants on fresh copies (the first run's villains are gone for good)
        Object[][] want = {{StoryTemplate.BANDIT_TOLL, Faction.MARINE}, {StoryTemplate.WHODUNIT, Faction.MARINE}, {StoryTemplate.SMUGGLERS, Faction.PIRATE}};
        for (Object[] w : want) {
            IslandStory st = forced(plan, (StoryTemplate) w[0], "tv" + VARIANTS.size() + "_", extra);
            if (st != null) VARIANTS.add(new Object[]{st, w[1]});
        }
        if (!extra.isEmpty()) IslandStories.install(plan, merged(world, extra));
        check("a playable story exists for every template", PLAY.size() == StoryTemplate.values().length && VARIANTS.size() == 3,
                PLAY.size() + " templates (" + extra.size() + " made for the test on fitting islands), " + VARIANTS.size() + " variant runs");
        return 0;
    }

    private static IslandStory forced(WorldPlan plan, StoryTemplate t, String prefix, List<IslandStory> extra) {
        for (Island i : plan.islands)
            if (IslandStories.fits(plan, i, t)) {
                IslandStory st = IslandStories.generate(plan, i, t, prefix);
                extra.add(st);
                return st;
            }
        return null;
    }

    private static Generated merged(Generated g, List<IslandStory> extra) {
        List<IslandStory> stories = new ArrayList<>(g.stories());
        List<QuestDef> quests = new ArrayList<>(g.quests());
        List<Person> people = new ArrayList<>(g.people());
        Map<String, Component> titles = new LinkedHashMap<>(g.titles()), descs = new LinkedHashMap<>(g.descs());
        Map<String, List<SiteRef>> qs = new LinkedHashMap<>(g.questSites());
        List<Site> sites = new ArrayList<>(g.sites());
        for (IslandStory st : extra) {
            stories.add(st);
            quests.addAll(st.quests());
            people.addAll(st.people());
            titles.putAll(st.titles());
            descs.putAll(st.descs());
            qs.putAll(st.sites());
            sites.addAll(st.places());
        }
        return new Generated(stories, quests, people, titles, descs, qs, sites, g.gifts());
    }

    private static int loadStory(MinecraftServer s, IslandStory st) {
        if (st == null) return 0;
        ServerLevel l = s.overworld();
        for (Person p : st.people()) load(s, l, p.x(), p.z());
        for (Site site : st.places()) load(s, l, site.x(), site.z());
        return 30;
    }

    private static void playChecked(MinecraftServer s, IslandStory st, Faction f, String suffix) {
        if (st == null) {
            check("story plays end to end" + suffix, false, "no story");
            return;
        }
        FakePlayer fp = SelfTest.fake(s);
        CharacterData d = Characters.get(fp);
        Faction before = d.faction;
        d.faction = f;
        long beli = d.beli;
        List<String> log = new ArrayList<>();
        String problem = playStory(s, fp, st, log);
        long gained = d.beli - beli;
        boolean done = finished(d, st);
        check("story '" + st.template().id + "' plays end to end" + suffix, problem == null && done && gained > 0,
                st.island() + ": " + String.join(" → ", log) + (problem == null ? "" : " ✗ " + problem) + "; +" + gained + " Beli");
        d.faction = before;
    }

    private static boolean finished(CharacterData d, IslandStory st) {
        String last = st.lastStep();
        for (String c : d.completedQuests) if (QuestRegistry.baseId(c).equals(last)) return true;
        return false;
    }

    /** Play a story with the real mechanics; null when it went through, otherwise what got stuck. */
    static String playStory(MinecraftServer s, FakePlayer fp, IslandStory st, List<String> log) {
        CharacterData d = Characters.get(fp);
        String first = QuestRegistry.resolve(st.lastStep().substring(0, st.lastStep().lastIndexOf('_')) + "_1", d);
        if (!Quests.start(fp, first)) return "could not start " + first;
        String base = st.lastStep().substring(0, st.lastStep().lastIndexOf('_') + 1);
        for (int guard = 0; guard < 8; guard++) {
            String active = null;
            for (String q : d.questProgress.keySet()) if (q.startsWith(base)) active = q;
            if (active == null) return finished(d, st) ? null : "no active step";
            String problem = playQuest(s, fp, active);
            log.add(active.substring(base.length()));
            if (problem != null) return problem;
            if (d.questProgress.containsKey(active)) return active + " did not complete: " + d.questProgress.get(active);
        }
        return "too many steps";
    }

    /** Satisfy every objective of an active quest the way a player would; null when all went through. */
    static String playQuest(MinecraftServer s, FakePlayer fp, String id) {
        QuestDef q = QuestRegistry.get(id);
        CharacterData d = Characters.get(fp);
        ServerLevel l = s.overworld();
        if (q == null) return "unknown quest " + id;
        for (int i = 0; i < q.objectives().size(); i++) {
            if (!d.questProgress.containsKey(id)) return null; // completed early (collect)
            QuestDef.Objective o = q.objectives().get(i);
            int have = d.questProgress.get(id).getInt("o" + i);
            if (have >= o.count()) continue;
            int need = o.count() - have;
            switch (o.type()) {
                case "talk" -> {
                    HumanoidNpc npc = body(s, o.target());
                    if (npc == null) return "nobody to talk to: " + o.target() + " (" + NotableState.get(s).status(o.target()) + ")";
                    QuestEvents.onTalk(fp, npc);
                }
                case "defeat" -> {
                    HumanoidNpc npc = body(s, o.target());
                    if (npc == null) return "nobody to defeat: " + o.target();
                    npc.hurt(fp.damageSources().playerAttack(fp), 1_000_000f);
                }
                case "arrest" -> {
                    HumanoidNpc npc = body(s, o.target());
                    if (npc == null) return "nobody to arrest: " + o.target();
                    npc.arrestedBy(fp);
                }
                case "kill", "slay" -> {
                    Site site = siteOf(id, i);
                    if (site == null) return "no place for " + o.type() + ":" + o.target();
                    double x = fp.getX(), y = fp.getY(), z = fp.getZ();
                    fp.moveTo(site.x() + 0.5, 70, site.z() + 0.5); // credit needs the slayer nearby
                    try {
                        for (int k = 0; k < need; k++) {
                            Entity e = StorySites.spawn(l, site, k);
                            if (!(e instanceof LivingEntity le)) return "no room for " + site.what() + " at " + site.key();
                            if (le instanceof GLNpc g && !g.role().equals(o.target())) return "wrong foe " + g.role();
                            le.hurt(fp.damageSources().playerAttack(fp), 1_000_000f);
                            if (le.isAlive()) le.hurt(fp.damageSources().playerAttack(fp), 1_000_000f);
                        }
                    } finally {
                        fp.moveTo(x, y, z);
                    }
                }
                case "reach" -> {
                    Site site = IslandStories.site(o.target());
                    if (site == null) return "unknown place " + o.target();
                    double x = fp.getX(), y = fp.getY(), z = fp.getZ();
                    fp.moveTo(site.x() + 0.5, 70, site.z() + 0.5);
                    StorySites.tickPlayer(fp);
                    fp.moveTo(x, y, z);
                }
                case "collect" -> {
                    ItemStack st = stackFor(o.target(), need);
                    if (st.isEmpty()) return "no item for " + o.target();
                    fp.getInventory().add(st);
                    QuestCollect.sync(fp);
                }
                case "escort" -> { // the merchant brought to the dock and spoken to (SeaJobTests plays the real journey)
                    String p = com.grandline.quest.story.SeaJobs.deliverForTest(fp, id);
                    if (p != null) return p;
                }
                case "discover" -> Quests.progress(fp, "discover", o.target(), 1);
                case "poneglyph" -> Quests.progress(fp, "poneglyph", "any", 1);
                default -> {
                    return "cannot play objective type " + o.type();
                }
            }
            if (d.questProgress.containsKey(id) && d.questProgress.get(id).getInt("o" + i) < o.count())
                return o.type() + ":" + o.target() + " did not progress (" + d.questProgress.get(id).getInt("o" + i) + "/" + o.count() + ")";
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private static Site siteOf(String questId, int objective) {
        for (SiteRef r : IslandStories.sitesOf(questId)) if (r.objective() == objective) return IslandStories.site(r.site());
        return null;
    }

    /** The living body of a generated person: already about, or spawned at their home now. */
    @org.jetbrains.annotations.Nullable
    static HumanoidNpc body(MinecraftServer s, String id) {
        ServerLevel l = s.overworld();
        NotableState st = NotableState.get(s);
        NotableState.Entry e = st.peek(id);
        if (e != null && e.status == NotableState.Status.ALIVE) {
            if (e.entity != null && l.getEntity(e.entity) instanceof HumanoidNpc n && n.isAlive()) return n;
            st.set(id, NotableState.Status.UNSPAWNED, null, "", l.getGameTime());
        }
        return NotableSpawner.spawnNow(l, id);
    }

    private static ItemStack stackFor(String target, int n) {
        if (target.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(target.substring(1)));
            for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) return new ItemStack(h.value(), n);
            return ItemStack.EMPTY;
        }
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(target));
        return it == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(it, n);
    }

    // ================================================================== story people spawn on their islands

    private static final List<Person> SAMPLE = new ArrayList<>();

    private static int spawnSample(MinecraftServer s) {
        SAMPLE.clear();
        ServerLevel l = s.overworld();
        // one person from every eighth story, givers first: their homes are loaded as a player would
        List<IslandStory> stories = world.stories();
        for (int i = 3; i < stories.size(); i += Math.max(1, stories.size() / 10)) {
            IslandStory st = stories.get(i);
            Person p = IslandStories.person(st.giver());
            if (p == null || NotableState.get(s).status(p.id()) != NotableState.Status.UNSPAWNED) continue;
            SAMPLE.add(p);
            load(s, l, p.x(), p.z());
        }
        return 30;
    }

    private static int spawnSampleCheck(MinecraftServer s) {
        WorldPlan plan = WorldPlan.of(s);
        ServerLevel l = s.overworld();
        List<String> bad = new ArrayList<>(), ok = new ArrayList<>();
        for (Person p : SAMPLE) {
            NotableSpawner.scanAt(l, p.x(), p.z()); // the proximity rule, as if a player walked up
            NotableState.Entry e = NotableState.get(s).peek(p.id());
            Entity ent = e == null || e.entity == null ? null : l.getEntity(e.entity);
            Island at = ent == null ? null : plan.islandAt(ent.getBlockX(), ent.getBlockZ());
            boolean good = ent instanceof HumanoidNpc n && at != null && at.id.equals(p.island()) && n.notableId().equals(p.id())
                    && n.getCustomName() != null && n.getCustomName().getString().equals(p.name());
            (good ? ok : bad).add(p.name() + "@" + p.island() + (ent == null ? " (not spawned)" : " at " + ent.blockPosition()));
        }
        unloadAll(s);
        check("story people spawn on their own islands when a player comes near", !SAMPLE.isEmpty() && bad.isEmpty(),
                ok.size() + "/" + SAMPLE.size() + " " + (bad.isEmpty() ? ok.subList(0, Math.min(3, ok.size())) : "failed: " + bad));
        return 0;
    }

    // ================================================================== job board

    private static String jobId(String type, int k) {
        return "job_" + type + "_" + jobIsland.id + (k >= 0 ? "_" + k : "");
    }

    private static int loadJobs(MinecraftServer s) {
        WorldPlan plan = WorldPlan.of(s);
        jobIsland = null;
        for (Island i : plan.islands)
            if (!i.canon && QuestRegistry.get("job_hunt_" + i.id + "_0") != null && QuestRegistry.get("job_escort_" + i.id) != null
                    && QuestRegistry.get("job_deliver_" + i.id + "_0") != null && QuestRegistry.get("job_wreck_" + i.id) != null) {
                jobIsland = i;
                break;
            }
        if (jobIsland == null) {
            check("a board island with every job type", false, "none found");
            return 0;
        }
        ServerLevel l = s.overworld();
        for (QuestDef q : QuestRegistry.offeredBy(JobBoard.GIVER, jobIsland.id, false)) {
            if (!q.island().equals(jobIsland.id)) continue;
            for (QuestDef.Objective o : q.objectives()) {
                Person p = IslandStories.person(o.target());
                if (p != null) load(s, l, p.x(), p.z());
            }
            for (SiteRef r : IslandStories.sitesOf(q.id())) {
                Site site = IslandStories.site(r.site());
                if (site != null) load(s, l, site.x(), site.z());
            }
        }
        return 30;
    }

    private static int board(MinecraftServer s) {
        if (jobIsland == null) return 0;
        FakePlayer fp = SelfTest.fake(s);
        CharacterData d = Characters.get(fp);
        d.faction = Faction.CIVILIAN;
        List<QuestDef> offers = JobBoard.offers(jobIsland, d);
        Set<String> types = new TreeSet<>();
        for (QuestDef q : offers) if (q.id().startsWith("job_")) types.add(q.id().substring(4, q.id().indexOf('_', 4)));
        boolean huntGated = offers.stream().anyMatch(q -> q.id().equals(jobId("hunt", 0))) && offers.stream().noneMatch(q -> q.id().equals(jobId("hunt", 1)));
        check("a notice board offers the classic and the generated jobs, outlaws one at a time", types.size() >= 8 && huntGated && offers.size() >= 13,
                jobIsland.id + ": " + offers.size() + " notices, types " + types + (huntGated ? "" : "; hunt gating wrong"));
        // the board itself: a real marker in town, found from a click on the board
        BlockPos marker = null, found = null;
        for (Plot p : jobIsland.settlement().plots)
            if (p.type() == BuildingType.BOUNTY_BOARD || p.type() == BuildingType.MARINE_POST)
                for (MarkerCollector.Marker m : MarkerCollector.markers(jobIsland, p)) if (m.kind().equals("bounty_board")) marker = m.pos();
        if (marker != null) found = JobBoard.boardAt(jobIsland, marker.offset(0, 0, 1));
        check("right-clicking the town's notice board finds it", marker != null && found != null, "marker " + marker + ", found " + found);
        return 0;
    }

    private static int huntJob(MinecraftServer s) {
        if (jobIsland == null) return 0;
        FakePlayer fp = SelfTest.fake(s);
        CharacterData d = Characters.get(fp);
        ServerLevel l = s.overworld();
        d.faction = Faction.CIVILIAN;
        String id = jobId("hunt", 0);
        QuestDef q = QuestRegistry.get(id);
        Person outlaw = IslandStories.person(q.objectives().get(0).target());
        // not about for someone without the job
        d.questProgress.keySet().removeIf(k -> k.startsWith("job_"));
        NotableSpawner.scanAt(l, outlaw.x() + 4, outlaw.z(), fp);
        NotableState.Status before = NotableState.get(s).status(outlaw.id());
        long beli = d.beli;
        boolean started = Quests.start(fp, QuestRegistry.resolve(id, d));
        ItemStack poster = ItemStack.EMPTY;
        for (int i = 0; i < fp.getInventory().getContainerSize(); i++) {
            ItemStack st = fp.getInventory().getItem(i);
            CustomData cd = st.get(DataComponents.CUSTOM_DATA);
            if (st.is(ModItems.WANTED_POSTER.get()) && cd != null && outlaw.id().equals(cd.copyTag().getString("target"))) poster = st;
        }
        CompoundTag pt = poster.isEmpty() ? new CompoundTag() : poster.get(DataComponents.CUSTOM_DATA).copyTag();
        boolean posterOk = !poster.isEmpty() && pt.getString("name").equals(outlaw.name()) && pt.getInt("x") == outlaw.x() && pt.getInt("z") == outlaw.z()
                && !pt.getString("where").isEmpty() && pt.getLong("bounty") > 0;
        NotableSpawner.scanAt(l, outlaw.x() + 4, outlaw.z(), fp);
        NotableState.Entry e = NotableState.get(s).peek(outlaw.id());
        Entity body = e == null || e.entity == null ? null : l.getEntity(e.entity);
        Island at = body == null ? null : WorldPlan.of(s).islandAt(body.getBlockX(), body.getBlockZ());
        check("a named-bounty job: the outlaw hides until hunted, the wanted poster says where, they turn up there",
                before == NotableState.Status.UNSPAWNED && started && posterOk && body instanceof HumanoidNpc && at != null && at.id.equals(outlaw.island()),
                outlaw.name() + " on " + outlaw.island() + ": before job " + before + ", poster " + pt + ", body " + (body == null ? "none" : body.blockPosition()));
        // guards at the hideout while the hunt is on
        double x = fp.getX(), y = fp.getY(), z = fp.getZ();
        fp.moveTo(outlaw.x() + 2, 70, outlaw.z());
        StorySites.tickPlayer(fp);
        fp.moveTo(x, y, z);
        int guards = 0;
        for (SiteRef r : IslandStories.sitesOf(QuestRegistry.resolve(id, d))) {
            Site site = IslandStories.site(r.site());
            if (site != null) guards += StorySites.present(l, site);
        }
        // bring the outlaw down: the job pays out
        if (body instanceof HumanoidNpc n) n.hurt(fp.damageSources().playerAttack(fp), 1_000_000f);
        boolean paid = QuestRegistry.isDone(d, id) && d.beli - beli >= q.beli();
        boolean next = JobBoard.offers(jobIsland, d).stream().anyMatch(o -> o.id().equals(jobId("hunt", 1)));
        check("defeating the hunted outlaw completes the job and pays the bounty; the next poster goes up", paid && next && guards > 0,
                "paid " + (d.beli - beli) + " Beli (job " + q.beli() + "), guards at the hideout " + guards + ", next outlaw offered " + next);
        return 0;
    }

    private static int otherJobs(MinecraftServer s) {
        if (jobIsland == null) return 0;
        FakePlayer fp = SelfTest.fake(s);
        CharacterData d = Characters.get(fp);
        List<String> results = new ArrayList<>(), failed = new ArrayList<>();
        for (String type : List.of("deliver", "escort", "nest", "camp", "wreck", "fish", "cook", "supply")) {
            String id = QuestRegistry.get(jobId(type, 0)) != null ? jobId(type, 0) : jobId(type, -1);
            QuestDef q = QuestRegistry.get(id);
            if (q == null) continue;
            long beli = d.beli;
            if (!Quests.start(fp, id)) {
                failed.add(type + ": not started");
                continue;
            }
            // conditional people (the escorted merchant) appear for the player on the job
            for (QuestDef.Objective o : q.objectives()) {
                Person p = IslandStories.person(o.target());
                if (p != null && p.conditional()) NotableSpawner.scanAt(s.overworld(), p.x() + 3, p.z(), fp);
            }
            String problem = playQuest(s, fp, id);
            boolean done = !d.questProgress.containsKey(id) && d.beli - beli >= q.beli();
            if (type.equals("deliver")) {
                boolean letter = false;
                for (int i = 0; i < fp.getInventory().getContainerSize(); i++)
                    if (fp.getInventory().getItem(i).is(net.minecraft.world.item.Items.PAPER) && fp.getInventory().getItem(i).has(DataComponents.CUSTOM_NAME)) letter = true;
                if (!letter) problem = (problem == null ? "" : problem + "; ") + "no letter";
            }
            (problem == null && done ? results : failed).add(type + (problem == null ? "" : ": " + problem) + (done ? "" : " (not done)"));
        }
        check("every other job type completes with real NPCs, beasts, places and goods", failed.isEmpty() && results.size() >= 7,
                "done " + results + (failed.isEmpty() ? "" : "; failed " + failed));
        unloadAll(s);
        return 0;
    }

    // ================================================================== dialogue

    private static int dialogue(MinecraftServer s) {
        FakePlayer fp = SelfTest.fake(s);
        CharacterData d = Characters.get(fp);
        d.faction = Faction.CIVILIAN;
        ServerLevel l = s.overworld();
        IslandStory st = null;
        for (IslandStory c : world.stories()) {
            if (NotableState.get(s).status(c.giver()) != NotableState.Status.UNSPAWNED) continue;
            if (QuestRegistry.isDone(d, c.lastStep()) || d.questProgress.keySet().stream().anyMatch(k -> k.contains(c.island()))) continue;
            st = c;
            break;
        }
        if (st == null) {
            check("story givers offer their story in dialogue", false, "no untouched story");
            return 0;
        }
        Person giver = IslandStories.person(st.giver());
        load(s, l, giver.x(), giver.z());
        HumanoidNpc npc = body(s, giver.id());
        String step1 = QuestRegistry.resolve(st.lastStep().substring(0, st.lastStep().lastIndexOf('_')) + "_1", d);
        CompoundTag t = new CompoundTag();
        t.putString("greeting", "dialogue.grandline.greet." + (npc == null ? "" : npc.role()));
        ListTag opts = new ListTag();
        if (npc != null) {
            NotableHooks.decorate(fp, npc, d, t, opts, false);
            com.grandline.dialogue.Dialogues.labelGenerated(fp, t, opts);
        }
        boolean offered = opts.toString().contains("quest:" + step1);
        check("story givers greet in their own words and offer their story (labelled with names)", npc != null && offered && t.contains("greetingText")
                        && t.getCompound("labels").contains("quest:" + step1) && t.getString("name").startsWith(giver.name()) && IslandStories.rumor(st.island()) != null,
                giver.name() + " on " + st.island() + ": options " + opts + ", name '" + t.getString("name") + "'");
        unloadAll(s);
        return 0;
    }

    // ================================================================== cleanup

    private static int cleanup(MinecraftServer s) {
        unloadAll(s);
        FakePlayer fp = SelfTest.fake(s);
        if (snap != null) snap.restore(Characters.get(fp));
        fp.getInventory().clearContent();
        if (world != null) IslandStories.install(WorldPlan.of(s), world); // back to the world's own set
        // fixture for phase 2: the stories must regenerate identically after a restart, and the fates stick
        if (world != null && jobIsland != null) {
            String outlaw = "job_" + jobIsland.id + "@outlaw0";
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("selftest-stories.txt"),
                        IslandStories.digest(world).hashCode() + " " + world.quests().size() + " " + outlaw + " " + NotableState.get(s).status(outlaw));
            } catch (Exception e) {
                GrandLine.LOGGER.warn("stories fixture not written", e);
            }
        }
        try {
            InventoryDump.write(s); // run/inventory.json for tools/gen_inventory.py (stories per island)
        } catch (Exception e) {
            GrandLine.LOGGER.warn("inventory dump failed", e);
        }
        return 0;
    }

    // ================================================================== phase 2 (after a restart)

    static void phase2(Deque<SelfTest.Step> steps) {
        steps.add(s -> {
            String[] fx;
            try {
                fx = java.nio.file.Files.readString(java.nio.file.Path.of("selftest-stories.txt")).trim().split(" ");
            } catch (Exception e) {
                check("island stories regenerate identically after a restart", false, "no phase 1 fixture: " + e);
                return 0;
            }
            IslandStories.ensure(s);
            Generated g = IslandStories.current();
            boolean same = g != null && String.valueOf(IslandStories.digest(g).hashCode()).equals(fx[0]) && String.valueOf(g.quests().size()).equals(fx[1]);
            check("island stories regenerate identically after a restart (nothing about them is saved)", same,
                    g == null ? "not installed" : g.stories().size() + " stories, " + g.quests().size() + " quests (phase 1: " + fx[1] + ")");
            NotableState.Status st = NotableState.get(s).status(fx[2]);
            check("a generated outlaw's fate survives the restart", st.name().equals(fx[3]) && st.gone() && NotableCharacters.get(fx[2]) != null,
                    fx[2] + ": " + st + " (phase 1: " + fx[3] + ")");
            return 0;
        });
    }

    // ================================================================== helpers

    private static void load(MinecraftServer s, ServerLevel l, int x, int z) {
        ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
        l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, LOAD_R, cp);
        TICKETS.add(new long[]{cp.x, cp.z});
        for (int dx = -LOAD_R; dx <= LOAD_R; dx++)
            for (int dz = -LOAD_R; dz <= LOAD_R; dz++) l.getChunk(cp.x + dx, cp.z + dz);
    }

    private static void unloadAll(MinecraftServer s) {
        ServerLevel l = s.overworld();
        for (long[] t : TICKETS) {
            ChunkPos cp = new ChunkPos((int) t[0], (int) t[1]);
            l.getChunkSource().removeRegionTicket(TicketType.FORCED, cp, LOAD_R, cp);
        }
        TICKETS.clear();
    }

    /** The test character's state before these tests (restored afterwards). */
    private record Snapshot(Faction faction, long bounty, long beli, Map<String, Integer> rep, Set<String> done, Map<String, CompoundTag> progress, Set<String> flags) {
        static Snapshot of(CharacterData d) {
            return new Snapshot(d.faction, d.bounty, d.beli, new HashMap<>(d.reputation), new LinkedHashSet<>(d.completedQuests), new HashMap<>(d.questProgress), new HashSet<>(d.flags));
        }

        void restore(CharacterData d) {
            d.faction = faction;
            d.bounty = bounty;
            d.beli = beli;
            d.reputation.clear();
            d.reputation.putAll(rep);
            d.completedQuests.clear();
            d.completedQuests.addAll(done);
            d.questProgress.clear();
            d.questProgress.putAll(progress);
            d.flags.clear();
            d.flags.addAll(flags);
        }
    }

    private IslandStoryTests() {}
}
