package com.grandline.test;

import com.grandline.npc.Archetypes;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.MarkerCollector;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableHooks;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestCollect;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.StoryQuestsExtra;
import com.grandline.world.DeepWorld;
import com.grandline.world.Island;
import com.grandline.world.SkyWorld;
import com.grandline.world.WorldPlan;
import com.grandline.world.islands.CanonIslands;
import com.grandline.world.settlement.Plot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.*;

/**
 * Self-test steps for the Skypiea and Fish-Man Island arcs and the local stories (quest/StoryQuestsExtra.java):
 * coverage of every canon island, objective types that really progress, sky and deep-sea notables spawning near a
 * player, and the Skypiea chain played end to end.
 */
final class StoryTestsExtra {
    private static final Set<String> FACTIONS = Set.of("pirate", "marine", "revolutionary", "bounty_hunter", "civilian", "world_government", "cipher_pol", "wild");

    /** The ocean world, the White Sea and the deep sea. */
    static List<WorldPlan> plans(MinecraftServer s) {
        List<WorldPlan> l = new ArrayList<>();
        WorldPlan ocean = WorldPlan.of(s);
        if (ocean != null) l.add(ocean);
        for (var key : List.of(SkyWorld.LEVEL, DeepWorld.LEVEL)) {
            ServerLevel lvl = s.getLevel(key);
            WorldPlan p = lvl == null ? null : WorldPlan.forLevel(lvl);
            if (p != null) l.add(p);
        }
        return l;
    }

    /** The plan (ocean, sky or deep) holding this island, or null. */
    static WorldPlan planOf(MinecraftServer s, String islandId) {
        for (WorldPlan p : plans(s)) if (p.byId(islandId) != null) return p;
        return null;
    }

    /** Whether an objective names something the game actually reports progress for (see QuestEvents and its callers). */
    static boolean progresses(MinecraftServer s, QuestDef.Objective o) {
        if (o.count() == 0) return true; // vacuous ("kill X 0"): always complete
        if (o.count() < 0) return false;
        String t = o.target();
        return switch (o.type()) {
            case "kill" -> Archetypes.has(t);
            case "defeat" -> NotableCharacters.get(t) != null;
            case "talk" -> NotableCharacters.get(t) != null || Archetypes.has(t) || t.equals("path_contact");
            case "arrest" -> NotableCharacters.get(t) != null || Archetypes.has(t) || FACTIONS.contains(t);
            case "discover" -> {
                if (t.equals("any") || t.equals("any_canon") || t.equals("any_new_world")) yield true;
                WorldPlan p = planOf(s, t);
                Island i = p == null ? null : p.byId(t);
                yield i != null && i.radius >= 10; // smaller islands are not indexed, so nobody can stand "on" them
            }
            case "poneglyph", "ship", "arrested", "fruit_kill" -> t.equals("any");
            case "collect" -> QuestCollect.valid(t);
            // generated island stories and jobs (quest/story): places to reach, beasts and Sea Kings to slay
            case "reach" -> com.grandline.quest.story.IslandStories.site(t) != null;
            case "slay" -> com.grandline.quest.story.StorySites.slayable(t);
            default -> false;
        };
    }

    private static void load(ServerLevel l, int x, int z, int radius) {
        ChunkPos cp = new ChunkPos(x >> 4, z >> 4);
        l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, radius, cp);
        l.setChunkForced(cp.x, cp.z, true);
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) l.getChunk(cp.x + dx, cp.z + dz);
    }

    private static int[] home(MinecraftServer s, String id) {
        var n = NotableCharacters.get(id);
        Island isl = planOf(s, n.island()).byId(n.island());
        return new int[]{isl.x + n.dx(), isl.z + n.dz()};
    }

    /** Spawn check for a notable living in another dimension: a "player" stands 20 blocks from its home. */
    private static HumanoidNpc proximitySpawn(MinecraftServer s, ServerLevel level, String id) {
        int[] h = home(s, id);
        NotableSpawner.scanAt(level, h[0] + 20, h[1]);
        NotableState.Entry e = NotableState.get(s).peek(id);
        Entity ent = e == null || e.entity == null ? null : level.getEntity(e.entity);
        return ent instanceof HumanoidNpc npc ? npc : null;
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        // ---------- data: homes, coverage, objectives, givers ----------
        steps.add(s -> {
            List<String> bad = new ArrayList<>();
            Map<WorldPlan.Kind, Integer> byKind = new EnumMap<>(WorldPlan.Kind.class);
            for (var n : NotableCharacters.all()) {
                WorldPlan p = planOf(s, n.island());
                if (p == null) bad.add(n.id() + "@" + n.island());
                else byKind.merge(p.kind, 1, Integer::sum);
            }
            SelfTest.check("every notable's home island exists in a plan (ocean/sky/deep)", bad.isEmpty(),
                    NotableCharacters.all().size() + " notables " + byKind + (bad.isEmpty() ? "" : "; missing: " + bad));

            Set<String> withQuest = new HashSet<>(), withQuestBefore = new HashSet<>();
            for (QuestDef q : QuestRegistry.all()) {
                withQuest.add(q.island());
                if (!StoryQuestsExtra.ids().contains(q.id())) withQuestBefore.add(q.island());
            }
            List<String> canon = new ArrayList<>(), missing = new ArrayList<>();
            for (var spec : CanonIslands.all()) canon.add(spec.id);
            int before = 0;
            for (String id : canon) {
                if (!withQuest.contains(id)) missing.add(id);
                if (withQuestBefore.contains(id)) before++;
            }
            SelfTest.check("every canon island has at least one quest", missing.isEmpty(),
                    (canon.size() - missing.size()) + "/" + canon.size() + " canon islands (before the extra stories: " + before + ")"
                            + (missing.isEmpty() ? "" : "; none on " + missing));

            // the named islands of the White Sea and the deep sea: a quest given there, or one that sends you there
            Set<String> targets = new HashSet<>(withQuest);
            for (QuestDef q : QuestRegistry.all()) for (var o : q.objectives()) if (o.type().equals("discover")) targets.add(o.target());
            List<String> dimMissing = new ArrayList<>();
            int dimNamed = 0;
            for (WorldPlan p : plans(s)) {
                if (p.kind == WorldPlan.Kind.OCEAN) continue;
                for (Island i : p.islands) {
                    if (!i.canon || i.id.startsWith("sky_islet_")) continue;
                    dimNamed++;
                    if (!targets.contains(i.id)) dimMissing.add(i.id);
                }
            }
            SelfTest.check("every named sky and deep-sea island has a quest", dimNamed >= 9 && dimMissing.isEmpty(),
                    (dimNamed - dimMissing.size()) + "/" + dimNamed + (dimMissing.isEmpty() ? "" : "; missing " + dimMissing));

            List<String> stuck = new ArrayList<>();
            int objectives = 0;
            Map<String, Integer> types = new TreeMap<>();
            for (QuestDef q : QuestRegistry.all())
                for (var o : q.objectives()) {
                    objectives++;
                    types.merge(o.type(), 1, Integer::sum);
                    if (!progresses(s, o)) stuck.add(q.id() + " " + o.type() + ":" + o.target() + "x" + o.count());
                }
            SelfTest.check("every quest objective uses a type and target that progresses", stuck.isEmpty(),
                    QuestRegistry.all().size() + " quests, " + objectives + " objectives " + types + (stuck.isEmpty() ? "" : "; stuck: " + stuck));

            List<String> misplaced = new ArrayList<>();
            for (QuestDef q : QuestRegistry.all()) {
                if (!q.giverRole().startsWith("notable:") || q.island().equals("*")) continue;
                var n = NotableCharacters.get(q.giverRole().substring(8));
                if (n == null || !n.island().equals(q.island())) misplaced.add(q.id());
            }
            SelfTest.check("notable quest givers live on their quests' islands", misplaced.isEmpty(), misplaced.toString());

            // the new arcs' set pieces carry the stones their quests ask for
            int stones = 0;
            for (String id : List.of("upper_yard", "sea_forest")) {
                WorldPlan p = planOf(s, id);
                Island i = p == null ? null : p.byId(id);
                if (i == null) continue;
                for (Plot plot : i.settlement().plots)
                    for (var m : MarkerCollector.markers(i, plot)) if (m.kind().equals("poneglyph")) stones++;
            }
            SelfTest.check("Shandora and the Sea Forest each hold a poneglyph", stones >= 2, stones + " poneglyph markers");
            return 0;
        });
        // ---------- sky and deep-sea notables spawn near a player ----------
        steps.add(s -> {
            ServerLevel sky = s.getLevel(SkyWorld.LEVEL), deep = s.getLevel(DeepWorld.LEVEL);
            if (sky == null || deep == null) return 0;
            int[] a = home(s, "amarante"), t = home(s, "tidwell"), k = home(s, "nereo");
            load(sky, a[0], a[1], 3);
            load(deep, t[0], t[1], 3);
            load(deep, k[0], k[1], 3);
            return 40;
        });
        steps.add(s -> {
            ServerLevel sky = s.getLevel(SkyWorld.LEVEL), deep = s.getLevel(DeepWorld.LEVEL);
            if (sky == null || deep == null) {
                SelfTest.check("sky and deep notables spawn", false, "dimensions missing");
                return 0;
            }
            NotableState st = NotableState.get(s);
            HumanoidNpc gate = proximitySpawn(s, sky, "amarante");
            SelfTest.check("a sky notable spawns when a player is near (Heaven's Gate)", gate != null && gate.level() == sky && "amarante".equals(gate.notableId())
                            && gate.raceId().equals("sky_people") && "heavens_gate".equals(gate.islandId()),
                    gate == null ? "status " + st.status("amarante") : gate.getName().getString() + " at " + gate.blockPosition() + " race " + gate.raceId());
            SelfTest.check("far-away sky notables stay unspawned", st.status("cirrus") == NotableState.Status.UNSPAWNED, "cirrus " + st.status("cirrus"));
            HumanoidNpc keeper = proximitySpawn(s, deep, "tidwell");
            SelfTest.check("a deep-sea notable spawns when a player is near (Sea Forest)", keeper != null && keeper.level() == deep && "sea_forest".equals(keeper.islandId())
                            && keeper.raceId().equals("fishman"),
                    keeper == null ? "status " + st.status("tidwell") : keeper.getName().getString() + " at " + keeper.blockPosition());
            HumanoidNpc king = proximitySpawn(s, deep, "nereo");
            SelfTest.check("Fish-Man Island's court spawns inside the bubble", king != null && king.level() == deep && DeepWorld.inAnyDome(king.getBlockX(), king.getBlockY(), king.getBlockZ())
                            && st.status("marisol") == NotableState.Status.ALIVE && st.status("sawara") == NotableState.Status.ALIVE,
                    king == null ? "status " + st.status("nereo") : king.getName().getString() + " at " + king.blockPosition() + ", marisol " + st.status("marisol")
                            + ", sawara " + st.status("sawara") + ", lorcan " + st.status("lorcan"));
            if (gate != null) {
                FakePlayer fp = SelfTest.fake(s);
                CharacterData cd = new CharacterData();
                cd.faction = Faction.PIRATE;
                cd.completedQuests.add("jaya_2");
                CompoundTag tag = new CompoundTag();
                tag.putString("greeting", "dialogue.grandline.greet.keeper");
                ListTag opts = new ListTag();
                NotableHooks.decorate(fp, gate, cd, tag, opts, false);
                SelfTest.check("the Gatekeeper greets and offers the Skypiea arc", tag.getString("greeting").equals("dialogue.grandline.notable.amarante")
                        && opts.toString().contains("quest:sky_1"), opts.toString());
            }
            return 0;
        });
        // ---------- the Skypiea arc, end to end ----------
        steps.add(s -> {
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            Faction before = d.faction;
            long bounty0 = d.bounty, beli0 = d.beli;
            d.faction = Faction.PIRATE;
            boolean hadJaya = d.completedQuests.contains("jaya_2");
            d.completedQuests.add("jaya_2");
            List<String> sky = List.of("heavens_gate", "angel_island", "upper_yard", "weatheria", "cloud_end");
            Set<String> wasDiscovered = new HashSet<>();
            for (String i : sky) if (d.discoveredIslands.remove(i)) wasDiscovered.add(i);
            List<String> log = new ArrayList<>();
            java.util.function.Predicate<String> at = id -> d.questProgress.containsKey(id);
            boolean ok = Quests.start(fp, "sky_1");
            Quests.progress(fp, "discover", "heavens_gate", 1);
            Quests.progress(fp, "discover", "angel_island", 1);
            ok &= d.completedQuests.contains("sky_1") && at.test("sky_2");
            log.add("1:" + ok);
            Quests.progress(fp, "talk", "lumi", 1);
            Quests.progress(fp, "talk", "priest", 1);
            Quests.progress(fp, "discover", "upper_yard", 1);
            ok &= d.completedQuests.contains("sky_2") && at.test("sky_3");
            log.add("2:" + ok);
            Quests.progress(fp, "kill", "ruin_guardian", 3);
            Quests.progress(fp, "defeat", "ilse", 1);
            Quests.progress(fp, "defeat", "garamond", 1);
            ok &= d.completedQuests.contains("sky_3") && at.test("sky_4");
            log.add("3:" + ok);
            Quests.progress(fp, "defeat", "lulla", 1);
            Quests.progress(fp, "defeat", "oswin", 1);
            ok &= d.completedQuests.contains("sky_4") && at.test("sky_5");
            log.add("4:" + ok);
            Quests.progress(fp, "discover", "weatheria", 1);
            Quests.progress(fp, "talk", "pinwheel", 1);
            boolean waiting = at.test("sky_5"); // the gold is still missing
            int gold0 = QuestCollect.count(fp, "minecraft:gold_ingot");
            fp.getInventory().add(new ItemStack(Items.GOLD_INGOT, 5));
            QuestCollect.sync(fp);
            int goldLeft = QuestCollect.count(fp, "minecraft:gold_ingot") - gold0;
            boolean staff = QuestCollect.count(fp, "grandline:weather_staff") > 0;
            ok &= waiting && d.completedQuests.contains("sky_5") && goldLeft == 1 && staff && at.test("sky_6");
            log.add("5:" + ok + " (gold left " + goldLeft + ", staff " + staff + ")");
            Quests.progress(fp, "defeat", "cirrus", 1);
            ok &= d.completedQuests.contains("sky_6") && d.flags.contains("skypiea_freed") && at.test("sky_7");
            log.add("6:" + ok);
            Quests.progress(fp, "talk", "ayasha", 1);
            Quests.progress(fp, "poneglyph", "any", 1);
            ok &= d.completedQuests.contains("sky_7") && d.flags.contains("rang_the_golden_bell");
            log.add("7:" + ok);
            for (String q : d.questProgress.keySet()) if (q.startsWith("sky_")) ok = false;
            SelfTest.check("Skypiea arc plays end to end (discover, talk, kill, defeat, collect, poneglyph)", ok, String.join(" ", log));
            // cleanup
            for (int i = 1; i <= 8; i++) d.completedQuests.remove("sky_" + i);
            d.flags.remove("skypiea_freed");
            d.flags.remove("rang_the_golden_bell");
            if (!hadJaya) d.completedQuests.remove("jaya_2");
            for (String i : sky) if (!wasDiscovered.contains(i)) d.discoveredIslands.remove(i); else d.discoveredIslands.add(i);
            d.faction = before;
            d.bounty = bounty0;
            d.beli = beli0;
            return 0;
        });
        // ---------- faction variants: a Marine arrests the Lantern of the Abyss ----------
        steps.add(s -> {
            FakePlayer fp = SelfTest.fake(s);
            CharacterData d = Characters.get(fp);
            Faction before = d.faction;
            d.faction = Faction.MARINE;
            boolean resolve = QuestRegistry.resolve("sky_6", d).equals("sky_6.marine") && QuestRegistry.resolve("fmi_6", d).equals("fmi_6.marine")
                    && !QuestRegistry.requirementsMet(d, QuestRegistry.get("fmi_6"));
            d.completedQuests.add("fmi_5");
            boolean started = Quests.start(fp, "fmi_6.marine");
            Quests.progress(fp, "arrest", "lorcan", 1);
            boolean done = d.completedQuests.contains("fmi_6.marine") && d.flags.contains("abyssal_tide_broken") && d.questProgress.containsKey("fmi_7");
            SelfTest.check("Fish-Man Island Marine variant: resolve, arrest credit, chain on", resolve && started && done,
                    "resolve " + resolve + ", started " + started + ", active " + d.questProgress.keySet());
            d.questProgress.remove("fmi_7");
            d.completedQuests.remove("fmi_5");
            d.completedQuests.remove("fmi_6.marine");
            d.flags.remove("abyssal_tide_broken");
            // a collect objective naming an item tag counts any flower
            fp.getInventory().add(new ItemStack(Items.DANDELION, 2));
            fp.getInventory().add(new ItemStack(Items.POPPY, 1));
            int flowers = QuestCollect.count(fp, "#minecraft:small_flowers");
            SelfTest.check("collect objectives accept item tags", flowers >= 3 && QuestCollect.valid("#minecraft:small_flowers") && !QuestCollect.valid("minecraft:no_such_item"),
                    flowers + " small flowers counted");
            d.faction = before;
            return 0;
        });
    }

    private StoryTestsExtra() {}
}
