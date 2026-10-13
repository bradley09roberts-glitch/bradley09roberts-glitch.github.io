package com.grandline.test;

import com.grandline.npc.Archetypes;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableHooks;
import com.grandline.npc.notable.NotableSpawner;
import com.grandline.player.CharacterData;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.player.Knockdown;
import com.grandline.quest.*;
import com.grandline.quest.arc.*;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Self-test of the story overhaul (quest/arc, GL_SELFTEST=story_arcs): every chapter's data and text; the opening
 * chapter played end to end by a fake player (the combat lesson, early completion, a logout mid-chapter, a knockdown
 * mid-step, a decision, protected story people, the boss credited through QuestEvents.onKill, rewards paid once, the
 * journal's chapter/who/why/next/where/danger/recap, the town changed afterwards); every other chapter played through
 * on each of its choices; and the migration of characters saved before the chapters existed.
 */
final class StoryOverhaulTests {
    private StoryOverhaulTests() {}

    private static FakePlayer player(MinecraftServer s, String name, Faction f) {
        UUID id = UUID.nameUUIDFromBytes(("gl-story-overhaul-" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer fp = FakePlayerFactory.get(s.overworld(), new GameProfile(id, name.length() > 16 ? name.substring(0, 16) : name));
        CharacterData d = Characters.get(fp);
        d.created = true;
        d.name = name;
        d.faction = f;
        d.questProgress.clear();
        d.completedQuests.clear();
        d.flags.clear();
        d.discoveredIslands.clear();
        d.trackedQuest = "";
        d.flags.add(Arcs.MIGRATED); // a character made after the chapters existed
        Scenes.reset(fp);
        return fp;
    }

    private static boolean has(String key) {
        return Language.getInstance().has(key);
    }

    static void phase1(Deque<SelfTest.Step> steps) {
        steps.add(StoryOverhaulTests::data);
        steps.add(s -> {
            SelfTest.questTextCheck(s); // every quest still has a description and readable objectives
            return 0;
        });
        // the opening chapter needs Shells Town's people loaded: wait for the chunks, like a player arriving would
        final int[] wait = {0};
        steps.add(s -> {
            Island isl = WorldPlan.of(s).byId("shells_town");
            var n = NotableCharacters.get("gallowgate");
            ChunkPos cp = new ChunkPos((isl.x + n.dx()) >> 4, (isl.z + n.dz()) >> 4);
            ServerLevel l = s.overworld();
            if (wait[0]++ == 0) l.getChunkSource().addRegionTicket(TicketType.FORCED, cp, 4, cp);
            boolean ready = true;
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) if (l.getChunkSource().getChunkNow(cp.x + dx, cp.z + dz) == null) ready = false;
            return ready && wait[0] > 40 || wait[0] > 900 ? 20 : -1;
        });
        steps.add(StoryOverhaulTests::opening);
        steps.add(StoryOverhaulTests::otherChapters);
        steps.add(StoryOverhaulTests::migration);
    }

    // ================================================================== data and text

    private static int data(MinecraftServer s) {
        List<String> bad = new ArrayList<>();
        int full = 0, scenes = 0, lines = 0;
        for (Arc a : Arcs.all()) {
            if (!has(a.titleKey())) bad.add(a.id() + ": no title");
            if (!has(a.whyKey())) bad.add(a.id() + ": no problem text");
            if (!has(a.partKey())) bad.add(a.id() + ": no part label");
            for (Arc.Cast c : a.cast()) {
                if (NotableCharacters.get(c.who()) == null) bad.add(a.id() + ": cast " + c.who() + " is nobody");
                if (!has(a.castKey(c.who()))) bad.add(a.id() + ": no cast line for " + c.who());
            }
            EnumSet<Arc.Beat> beats = EnumSet.noneOf(Arc.Beat.class);
            int lastMin = -1;
            for (Arc.Step st : a.steps()) {
                QuestDef q = QuestRegistry.get(st.quest());
                if (q == null) {
                    bad.add(a.id() + ": step " + st.quest() + " is not a quest");
                    continue;
                }
                beats.addAll(st.beats());
                int min = st.beats().isEmpty() ? lastMin : st.beats().iterator().next().ordinal();
                if (a.full() && min < lastMin) bad.add(a.id() + ": beats out of order at " + st.quest());
                lastMin = Math.max(lastMin, min);
                if (!has(q.titleKey())) bad.add(st.quest() + ": no title");
                if (QuestText.description(q) == null) bad.add(st.quest() + ": no description");
                Arc.Decision dec = Arcs.decisionAt(st.quest());
                if (dec != null) {
                    for (Arc.Choice c : dec.choices()) if (!has("recap.grandline." + st.quest() + "." + c.id())) bad.add(st.quest() + ": no recap for " + c.id());
                } else if (!has("recap.grandline." + st.quest())) bad.add(st.quest() + ": no recap");
                // scenes of this step (every faction version, every event, every choice) are well formed, in every chapter
                for (String ev : List.of("start", "end", "talk")) {
                    List<String> prefixes = new ArrayList<>();
                    for (String id : List.of(st.quest(), st.quest() + ".marine", st.quest() + ".revolutionary", st.quest() + ".bounty_hunter", st.quest() + ".civilian")) {
                        prefixes.add("scene.grandline." + id + "." + ev);
                        for (Arc.Decision d2 : a.decisions()) for (Arc.Choice c : d2.choices()) prefixes.add("scene.grandline." + id + "." + ev + "." + c.id());
                    }
                    for (String p : prefixes) {
                        int n = Scenes.lines(p);
                        if (n == 0) continue;
                        scenes++;
                        for (int i = 1; i <= n; i++) {
                            lines++;
                            String v = Language.getInstance().getOrDefault(p + "." + i);
                            int bar = v.indexOf('|');
                            String who = bar < 0 ? "" : v.substring(0, bar);
                            boolean known = who.equals("you") || who.equals("narrator") || NotableCharacters.get(who) != null || has("speaker.grandline." + who)
                                    || Archetypes.has(who);
                            if (bar < 0 || !known || v.substring(bar + 1).isBlank()) bad.add(p + "." + i + " speaker '" + who + "'");
                        }
                    }
                }
                if (!a.full()) continue;
                // kill objectives of the chapter's steps have a place where their foes turn up
                for (String id : List.of(st.quest(), st.quest() + ".marine", st.quest() + ".revolutionary", st.quest() + ".bounty_hunter", st.quest() + ".civilian")) {
                    QuestDef v = QuestRegistry.get(id);
                    if (v == null) continue;
                    for (int i = 0; i < v.objectives().size(); i++) {
                        final int oi = i;
                        if (v.objectives().get(i).type().equals("kill") && com.grandline.quest.story.IslandStories.sitesOf(id).stream().noneMatch(r -> r.objective() == oi))
                            bad.add(id + ": kill objective " + i + " has no place");
                    }
                }
            }
            if (a.full()) {
                full++;
                if (!beats.containsAll(EnumSet.allOf(Arc.Beat.class))) bad.add(a.id() + ": beats missing " + EnumSet.complementOf(beats));
                if (a.decisions().isEmpty()) bad.add(a.id() + ": no decision");
                if (Consequences.climax(a) == null) bad.add(a.id() + ": no climax step");
            }
            for (Arc.Decision dec : a.decisions()) {
                QuestDef q = QuestRegistry.get(dec.step());
                if (q == null || q.objectives().stream().noneMatch(o -> o.type().equals("choice") && o.target().equals(dec.id()))) bad.add(dec.id() + ": step has no choice objective");
                if (NotableCharacters.get(dec.presenter()) == null) bad.add(dec.id() + ": presenter " + dec.presenter());
                if (!has("arc.grandline.decision." + dec.id())) bad.add(dec.id() + ": no decision label");
                if (dec.choices().size() < 2 || dec.choices().size() > 3) bad.add(dec.id() + ": " + dec.choices().size() + " choices");
                if (Scenes.lines("scene.grandline." + dec.step() + ".talk") == 0) bad.add(dec.id() + ": no decision scene");
                for (Arc.Choice c : dec.choices()) {
                    if (!has("scene.grandline.choice." + dec.id() + "." + c.id())) bad.add(dec.id() + "." + c.id() + ": no label");
                    if (c.next() != null && QuestRegistry.get(c.next()) == null) bad.add(dec.id() + "." + c.id() + ": next " + c.next());
                }
            }
        }
        for (String site : ArcSites.names()) if (!has("arc.grandline.site." + site)) bad.add("place " + site + " has no label");
        for (String k : Consequences.messageKeys()) if (!has(k)) bad.add("missing line " + k);
        for (String k : List.of("grandline.quest.obj.lesson.strike", "grandline.quest.obj.lesson.guard", "grandline.quest.obj.lesson.dodge",
                "grandline.quest.obj.lesson.technique", "grandline.quest.obj.choice", "journal.grandline.chapter", "journal.grandline.danger",
                "dialogue.grandline.option.spar", "screen.grandline.scene.next"))
            if (!has(k)) bad.add("missing " + k);
        SelfTest.check("story chapters: data, beats in order, decisions, scenes and text are complete", bad.isEmpty() && full >= 9,
                Arcs.all().size() + " chapters (" + full + " at full standard), " + scenes + " scenes, " + lines + " scene lines, " + Arcs.decisions().size()
                        + " decisions, " + Consequences.declared() + " consequences" + (bad.isEmpty() ? "" : "; problems: " + bad.subList(0, Math.min(14, bad.size())) + (bad.size() > 14 ? " +" + (bad.size() - 14) : "")));
        // the lesson objective reads as words with the key to press
        QuestDef e1 = QuestRegistry.get("ember_east_1");
        List<String> texts = new ArrayList<>();
        for (QuestDef.Objective o : e1.objectives()) texts.add(QuestText.objective(s, o).getString());
        SelfTest.check("the combat lesson's objectives read as words", texts.size() == 5 && texts.get(1).startsWith("Lesson: land a light blow")
                && texts.get(3).startsWith("Lesson: dodge") && texts.stream().noneMatch(t -> t.startsWith("grandline.")), texts.toString());
        return 0;
    }

    // ================================================================== the opening chapter, end to end

    private static int opening(MinecraftServer s) {
        ServerLevel l = s.overworld();
        WorldPlan plan = WorldPlan.of(s);
        FakePlayer fp = player(s, "OpeningPirate", Faction.PIRATE);
        CharacterData d = Characters.get(fp);
        d.level = 8; // about where a player reaches Shells Town
        Island shiokaze = plan.byId("shiokaze_isle"), shells = plan.byId("shells_town");
        fp.moveTo(shiokaze.x + 0.5, 90, shiokaze.z + 0.5);

        // ---- step 1: the lesson and the stolen supplies
        boolean started = Quests.start(fp, "ember_east_1");
        CompoundTag log0 = QuestGuide.log(fp);
        String next0 = text(firstQuest(log0, "ember_east_1"), "next");
        SelfTest.check("opening: Fumio's step starts with its scene, tracked, the lesson first in line", started && d.trackedQuest.equals("ember_east_1")
                && Scenes.played(fp).contains("scene.grandline.ember_east_1.start") && next0.startsWith("Lesson"), "next: " + next0 + ", scenes " + Scenes.played(fp));
        for (String part : Arcs.LESSONS) Quests.progress(fp, "lesson", part, 1);
        HumanoidNpc b1 = SelfTest.npc(l, "bandit", 1, BlockPos.containing(fp.position()).offset(3, 0, 0));
        HumanoidNpc b2 = SelfTest.npc(l, "bandit", 1, BlockPos.containing(fp.position()).offset(-3, 0, 0));
        QuestEvents.onKill(fp, b1);
        QuestEvents.onKill(fp, b1); // reported twice (a crew kill reaches more than one hook): counts once
        int afterDup = d.questProgress.get("ember_east_1").getInt("o0");
        QuestEvents.onKill(fp, b2);
        b1.discard();
        b2.discard();
        // ---- logout mid-step: the character goes to disk and comes back
        CompoundTag saved = d.serializeNBT(s.registryAccess());
        CharacterData back = new CharacterData();
        back.deserializeNBT(s.registryAccess(), saved);
        boolean roundTrip = back.questProgress.get("ember_east_1") != null && back.questProgress.get("ember_east_1").getInt("o0") == 2
                && back.flags.contains("lesson:dodge") && back.trackedQuest.equals("ember_east_1");
        d.deserializeNBT(s.registryAccess(), saved); // log back in
        // ---- knocked down mid-step: nothing is lost
        Knockdown.knockDown(fp, fp.damageSources().generic()); // no attacker: nobody to rob and release the player
        boolean downKeeps = d.isKnockedDown() && d.questProgress.get("ember_east_1").getInt("o0") == 2;
        Knockdown.standUp(fp, d, 1f);
        SelfTest.check("opening: kills count once each; progress survives logout (NBT round trip) and a knockdown", afterDup == 1 && roundTrip && downKeeps,
                "after a double report " + afterDup + ", round trip " + roundTrip + ", knocked down and kept " + downKeeps);
        // ---- early completion: Shells Town already discovered before the next step begins
        d.discoveredIslands.add("shells_town");
        long beli0 = d.beli;
        HumanoidNpc b3 = SelfTest.npc(l, "bandit", 1, BlockPos.containing(fp.position()).offset(0, 0, 3));
        QuestEvents.onKill(fp, b3);
        b3.discard();
        long beli1 = d.beli;
        boolean early = QuestRegistry.isDone(d, "ember_east_1") && QuestRegistry.isDone(d, "ember_east_2") && d.questProgress.containsKey("shells_1")
                && d.questProgress.get("shells_1").getInt("o0") == 1;
        // ---- finishing twice never pays twice
        boolean again = Quests.recheck(fp, "ember_east_1") || Quests.start(fp, "ember_east_1");
        Quests.progress(fp, "kill", "bandit", 3);
        Quests.progress(fp, "discover", "shells_town", 1);
        SelfTest.check("opening: early completion (island already discovered) and rewards paid once", early && !again && d.beli == beli1 && beli1 - beli0 >= 800,
                "Beli " + beli0 + " -> " + beli1 + " -> " + d.beli + ", active " + d.questProgress.keySet());
        // ---- Shells Town: the levy line, Petra, the cell block
        fp.moveTo(shells.x + 0.5, 90, shells.z + 0.5);
        for (int i = 0; i < 4; i++) {
            HumanoidNpc m = SelfTest.npc(l, "marine", 1, BlockPos.containing(fp.position()).offset(i, 0, 4));
            QuestEvents.onKill(fp, m);
            m.discard();
        }
        Quests.progress(fp, "talk", "petra", 1);
        Quests.progress(fp, "kill", "bandit", 3);
        Quests.progress(fp, "talk", "mio", 1);
        boolean atDecision = d.questProgress.containsKey("shells_plan") && Scenes.played(fp).contains("scene.grandline.shells_plan.talk")
                && QuestRegistry.isDone(d, "shells_petra") && QuestRegistry.isDone(d, "shells_rescue");
        // ---- the decision: bounded, recorded once, sends the chapter down its branch
        boolean bogus = Scenes.choose(fp, "shells_plan", "burn_it_all");
        boolean chose = Scenes.choose(fp, "shells_plan", "square");
        boolean twice = Scenes.choose(fp, "shells_plan", "ledger");
        SelfTest.check("opening: the decision is offered in a scene, only real options count, and only once", atDecision && !bogus && chose && !twice
                        && "square".equals(Arcs.choice(d, "shells_plan")) && d.questProgress.containsKey("shells_square") && !d.questProgress.containsKey("shells_ledger"),
                "active " + d.questProgress.keySet() + ", choice " + Arcs.choice(d, "shells_plan"));
        Quests.progress(fp, "kill", "marine", 3);
        boolean climax = d.questProgress.containsKey("shells_2") && Scenes.played(fp).contains("scene.grandline.shells_2.start.square");

        // ---- the people a later scene needs are protected
        HumanoidNpc gallowgate = live(l, "gallowgate");
        HumanoidNpc petra = live(l, "petra");
        FakePlayer early2 = player(s, "PassingRookie", Faction.PIRATE);
        early2.moveTo(shells.x + 0.5, 90, shells.z + 0.5);
        boolean villainYields = false, strayBlocked = false, allySurvives = false;
        String protDetail = "gallowgate " + (gallowgate != null) + ", petra " + (petra != null);
        if (gallowgate != null) {
            HumanoidNpc stray = SelfTest.npc(l, "bandit", 1, gallowgate.blockPosition().offset(2, 0, 0));
            float hp = gallowgate.getHealth();
            gallowgate.invulnerableTime = 0;
            gallowgate.hurt(l.damageSources().mobAttack(stray), 10000);
            strayBlocked = gallowgate.isAlive() && gallowgate.getHealth() == hp;
            gallowgate.invulnerableTime = 0;
            gallowgate.hurt(early2.damageSources().playerAttack(early2), 100000); // a player who has not reached the fight
            gallowgate.invulnerableTime = 0;
            gallowgate.hurt(early2.damageSources().playerAttack(early2), 100000); // and again: still not final
            villainYields = gallowgate.isAlive() && gallowgate.flag(GLNpc.FLAG_SURRENDER) && Characters.get(early2).flags.contains("beat:gallowgate");
            stray.discard();
            protDetail += ", villain hp " + gallowgate.getHealth() + " surrendered " + gallowgate.flag(GLNpc.FLAG_SURRENDER);
        }
        if (petra != null) {
            HumanoidNpc stray = SelfTest.npc(l, "bandit", 1, petra.blockPosition().offset(2, 0, 0));
            float hp = petra.getHealth();
            petra.invulnerableTime = 0;
            petra.hurt(l.damageSources().mobAttack(stray), 10000);
            boolean blocked = petra.getHealth() == hp;
            for (int i = 0; i < 3; i++) {
                petra.invulnerableTime = 0;
                petra.hurt(early2.damageSources().playerAttack(early2), 100000);
            }
            allySurvives = blocked && petra.isAlive();
            stray.discard();
            petra.setFlag(GLNpc.FLAG_SURRENDER, false); // back on her feet for the rest of the chapter
            petra.setHealth(petra.getMaxHealth());
        }
        SelfTest.check("protected story people: stray blows do nothing; the villain only yields before the player's fight step; an ally never dies",
                strayBlocked && villainYields && allySurvives, protDetail);
        // the passing rookie beat Gallowgate already: when their own fight step comes, it counts at once
        CharacterData e2 = Characters.get(early2);
        e2.completedQuests.addAll(List.of("shells_1", "shells_petra", "shells_rescue", "shells_plan"));
        boolean preBeaten = Quests.start(early2, "shells_2") && QuestRegistry.isDone(e2, "shells_2");
        SelfTest.check("early completion: a boss already beaten counts the moment the fight step starts", preBeaten, "active " + e2.questProgress.keySet());

        // ---- the journal for the climax
        if (gallowgate != null) fp.moveTo(gallowgate.getX() + 4, gallowgate.getY(), gallowgate.getZ());
        CompoundTag entry = firstQuest(QuestGuide.log(fp), "shells_2");
        String chapter = text(entry, "chapter"), why = text(entry, "why"), next = text(entry, "next"), where = text(entry, "where"), danger = text(entry, "danger");
        ListTag who = entry == null ? new ListTag() : entry.getList("who", Tag.TAG_STRING), recap = entry == null ? new ListTag() : entry.getList("recap", Tag.TAG_STRING);
        String whoText = joined(who), recapText = joined(recap);
        fp.moveTo(shiokaze.x + 0.5, 90, shiokaze.z + 0.5); // far away: the danger comes from the data, not a live foe
        String farDanger = ArcJournal.dangerText(fp, QuestRegistry.get("shells_2"), d.questProgress.get("shells_2"));
        SelfTest.check("journal: chapter, who, why, next, where, danger and recap for the climax", climax && entry != null && "story".equals(entry.getString("group"))
                        && chapter.startsWith("Chapter 1") && chapter.contains("The Justice Levy") && who.size() >= 5 && whoText.contains("Petra") && whoText.contains("(gives this step)")
                        && why.contains("Shells Town") && next.contains("Gallowgate") && !where.isEmpty() && danger.contains("Gallowgate") && danger.contains("(boss)")
                        && farDanger.contains("Gallowgate") && recap.size() >= 7 && recapText.contains("square"),
                "chapter: " + chapter + " | next: " + next + " | where: " + where + (entry != null && entry.getBoolean("area") ? " (area)" : "") + " | danger: " + danger
                        + " | far: " + farDanger + " | who " + who.size() + " | recap " + recap.size() + ": " + recapText);

        // ---- help in the climax, then the boss, credited through QuestEvents.onKill (as a crew companion's kill is)
        int aided = 0;
        if (gallowgate != null) {
            fp.moveTo(gallowgate.getX() + 3, gallowgate.getY(), gallowgate.getZ());
            aided = Consequences.tickAid(fp, l.getGameTime() + 100000);
        }
        boolean strength = fp.hasEffect(MobEffects.DAMAGE_BOOST);
        int shellsRep = d.reputation("island/shells_town");
        if (gallowgate != null) QuestEvents.onKill(fp, gallowgate);
        else Quests.progress(fp, "defeat", "gallowgate", 1);
        int repGain = d.reputation("island/shells_town") - shellsRep;
        SelfTest.check("the climax: the town's help (the choice's aid), the boss credited through onKill, the choice's bonus paid", aided == 1 && strength
                        && QuestRegistry.isDone(d, "shells_2") && d.questProgress.containsKey("shells_after") && repGain == 250,
                "aid " + aided + ", strength " + strength + ", island reputation +" + repGain + ", active " + d.questProgress.keySet());

        // ---- the port changes, Petra's choice, the lead to Orange Town
        HumanoidNpc resident = SelfTest.npc(l, "resident", 1, BlockPos.containing(shells.x, 70, shells.z));
        CompoundTag tagR = probeIsland(resident, "shells_town");
        String townLine = Consequences.greeting(resident, d);
        resident.discard();
        Quests.progress(fp, "talk", "mio", 1);
        Quests.progress(fp, "talk", "petra", 1);
        boolean recruitOffered = false;
        if (petra != null) {
            CompoundTag t = new CompoundTag();
            t.putString("greeting", "dialogue.grandline.greet.marine");
            ListTag opts = new ListTag();
            NotableHooks.decorate(fp, petra, d, t, opts, false);
            recruitOffered = opts.toString().contains("\"recruit\"");
        }
        SelfTest.check("after the chapter: townsfolk talk about it, shops are cheaper, Petra can be recruited, Orange Town is next",
                "arc.grandline.justice_levy.after.townsfolk.shells_town.square".equals(townLine) && Math.abs(Consequences.priceFactor(d, "shells_town") - 0.85) < 1e-6
                        && Math.abs(Consequences.priceFactor(d, "shiokaze_isle") - 0.9) < 1e-6 && QuestRegistry.isDone(d, "recruit_petra")
                        && recruitOffered && d.questProgress.containsKey("orange_1") && Scenes.played(fp).contains("scene.grandline.recruit_petra.end.square"),
                "line " + townLine + " (" + tagR + "), price x" + Consequences.priceFactor(d, "shells_town") + ", recruit offered " + recruitOffered + ", active " + d.questProgress.keySet());

        // ---- the other branch, briefly: the ledger
        FakePlayer m = player(s, "LedgerMarine", Faction.MARINE);
        CharacterData md = Characters.get(m);
        md.completedQuests.addAll(List.of("ember_east_1", "ember_east_2", "shells_1.marine", "shells_petra", "shells_rescue"));
        Quests.start(m, "shells_plan");
        Scenes.choose(m, "shells_plan", "ledger");
        boolean toLedger = md.questProgress.containsKey("shells_ledger");
        Quests.progress(m, "talk", "hayashi", 1);
        boolean marineClimax = md.questProgress.containsKey("shells_2.marine");
        int cuffs0 = m.getInventory().countItem(com.grandline.registry.ModItems.SEASTONE_CUFFS.get());
        Quests.progress(m, "defeat", "gallowgate", 1);
        int cuffs = m.getInventory().countItem(com.grandline.registry.ModItems.SEASTONE_CUFFS.get()) - cuffs0;
        SelfTest.check("the other choice: the ledger branch, the Marine climax variant, Hayashi's cuffs, no discount",
                toLedger && marineClimax && QuestRegistry.isDone(md, "shells_2") && cuffs == 2 && Consequences.priceFactor(md, "shells_town") == 1.0,
                "ledger " + toLedger + ", marine climax " + marineClimax + ", cuffs " + cuffs + ", price x" + Consequences.priceFactor(md, "shells_town"));
        return 0;
    }

    @org.jetbrains.annotations.Nullable
    private static HumanoidNpc live(ServerLevel l, String id) {
        var st = com.grandline.npc.notable.NotableState.get(l.getServer());
        var en = st.peek(id);
        if (en != null && en.entity != null && l.getEntity(en.entity) instanceof HumanoidNpc h) return h;
        return NotableSpawner.spawnNow(l, id);
    }

    private static CompoundTag probeIsland(GLNpc npc, String island) {
        CompoundTag t = new CompoundTag();
        npc.addAdditionalSaveData(t);
        t.putString("Island", island);
        npc.readAdditionalSaveData(t);
        CompoundTag out = new CompoundTag();
        out.putString("island", npc.islandId());
        return out;
    }

    @org.jetbrains.annotations.Nullable
    private static CompoundTag firstQuest(CompoundTag log, String id) {
        ListTag l = log.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) if (l.getCompound(i).getString("id").equals(id)) return l.getCompound(i);
        return null;
    }

    private static String text(@org.jetbrains.annotations.Nullable CompoundTag t, String key) {
        if (t == null || !t.contains(key)) return "";
        return plain(t.getString(key));
    }

    private static String plain(String json) {
        try {
            Component c = Component.Serializer.fromJson(json, net.minecraft.core.RegistryAccess.EMPTY);
            return c == null ? "" : c.getString();
        } catch (RuntimeException e) {
            return json;
        }
    }

    private static String joined(ListTag l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) sb.append(i == 0 ? "" : " / ").append(plain(l.getString(i)));
        return sb.toString();
    }

    // ================================================================== every other chapter, on each of its choices

    private static int otherChapters(MinecraftServer s) {
        List<String> bad = new ArrayList<>();
        int played = 0, steps = 0;
        for (Arc a : Arcs.all()) {
            if (!a.full()) continue;
            int choices = a.decisions().isEmpty() ? 1 : a.decisions().get(0).choices().size();
            for (int c = 0; c < choices; c++) {
                FakePlayer fp = player(s, "Ch" + a.id().hashCode() % 1000 + "_" + c, Faction.PIRATE);
                CharacterData d = Characters.get(fp);
                String problem = playThrough(fp, a, c);
                played++;
                if (problem != null) {
                    bad.add(a.id() + "/" + c + ": " + problem);
                    continue;
                }
                for (Arc.Step st : a.steps()) {
                    if (!QuestRegistry.isDone(d, st.quest())) continue;
                    steps++;
                    boolean scene = Scenes.played(fp).stream().anyMatch(p -> p.startsWith("scene.grandline." + st.quest() + "."));
                    if (!scene) bad.add(a.id() + ": no scene played for " + st.quest());
                }
                if (!Consequences.climaxDone(d, a)) bad.add(a.id() + "/" + c + ": climax not done");
                if (Arcs.recapKeys(d, a, StoryOverhaulTests::has).size() < 6) bad.add(a.id() + "/" + c + ": recap too short");
            }
        }
        SelfTest.check("every chapter at full standard plays end to end on each of its choices, with a scene on every step", bad.isEmpty() && played >= 18,
                played + " playthroughs, " + steps + " steps" + (bad.isEmpty() ? "" : "; " + bad.subList(0, Math.min(10, bad.size()))));
        return 0;
    }

    /** Play a chapter from its first step, doing each step's objectives; null if it ran to the end, else what went wrong. */
    static String playThrough(FakePlayer fp, Arc a, int choice) {
        CharacterData d = Characters.get(fp);
        String first = a.steps().get(0).quest();
        if (!Quests.start(fp, QuestRegistry.resolve(first, d))) return "first step " + first + " would not start";
        for (int guard = 0; guard < 40; guard++) {
            String active = null;
            for (String id : d.questProgress.keySet()) if (Arcs.arcOf(id) == a) active = id;
            if (active == null) return null;
            QuestDef q = QuestRegistry.get(active);
            CompoundTag prog = d.questProgress.get(active);
            for (int i = 0; i < q.objectives().size() && d.questProgress.containsKey(active); i++) {
                QuestDef.Objective o = q.objectives().get(i);
                int left = o.count() - prog.getInt("o" + i);
                if (left <= 0) continue;
                switch (o.type()) {
                    case "choice" -> {
                        Arc.Decision dec = Arcs.decision(o.target());
                        if (dec == null || !Scenes.played(fp).contains("scene.grandline." + dec.step() + ".talk")) return "decision " + o.target() + " was not presented";
                        Scenes.choose(fp, dec.id(), dec.choices().get(choice % dec.choices().size()).id());
                    }
                    case "discover" -> {
                        d.discoveredIslands.add(o.target());
                        Quests.progress(fp, "discover", o.target(), 1);
                    }
                    default -> Quests.progress(fp, o.type(), o.target(), left);
                }
            }
            if (d.questProgress.containsKey(active)) return "stuck at " + active + " " + d.questProgress.get(active);
        }
        return "did not end";
    }

    // ================================================================== saves from before the chapters

    private static int migration(MinecraftServer s) {
        List<String> bad = new ArrayList<>();
        // 1) mid-way through Shells Town under the old chain: shells_1 done, the old shells_2 (defeat Gallowgate) under way
        FakePlayer a = player(s, "OldSaveShells", Faction.PIRATE);
        CharacterData d = Characters.get(a);
        d.flags.remove(Arcs.MIGRATED);
        d.completedQuests.addAll(List.of("ember_east_1", "ember_east_2", "shells_1"));
        d.questProgress.put("shells_2", new CompoundTag());
        d.trackedQuest = "shells_2";
        StoryMigration.migrate(a);
        if (!d.questProgress.containsKey("shells_2")) bad.add("shells_2 no longer under way");
        for (String id : List.of("shells_petra", "shells_rescue", "shells_plan")) if (!QuestRegistry.isDone(d, id)) bad.add(id + " not passed");
        if (QuestRegistry.isDone(d, "shells_square") || QuestRegistry.isDone(d, "shells_ledger")) bad.add("a decision branch was marked done");
        if (!d.flags.contains(Arcs.MIGRATED) || !d.trackedQuest.equals("shells_2")) bad.add("flag or tracking lost");
        Quests.progress(a, "defeat", "gallowgate", 1);
        if (!d.questProgress.containsKey("shells_after")) bad.add("after the old climax the chapter goes on: " + d.questProgress.keySet());
        // 2) the old Orange Town step (discover + 6 bandits), half done: the new step keeps the island, needs Carmen
        FakePlayer b = player(s, "OldSaveOrange", Faction.PIRATE);
        CharacterData db = Characters.get(b);
        db.flags.remove(Arcs.MIGRATED);
        CompoundTag op = new CompoundTag();
        op.putInt("o0", 1);
        op.putInt("o1", 4);
        db.questProgress.put("orange_1", op);
        db.discoveredIslands.add("orange_town");
        StoryMigration.migrate(b);
        CompoundTag np = db.questProgress.get("orange_1");
        if (np == null || np.getInt("o0") != 1 || np.getInt("o1") != 0) bad.add("orange_1 remap " + np);
        Quests.progress(b, "talk", "carmen", 1);
        if (!db.questProgress.containsKey("orange_band")) bad.add("orange_1 did not lead on: " + db.questProgress.keySet());
        // 3) the old bandit step under way (2 of 3): the lesson counts as shown for a veteran, the bandits keep their count
        FakePlayer c = player(s, "OldSaveEmber", Faction.PIRATE);
        CharacterData dc = Characters.get(c);
        dc.flags.remove(Arcs.MIGRATED);
        CompoundTag ep = new CompoundTag();
        ep.putInt("o0", 2);
        dc.questProgress.put("ember_east_1", ep);
        StoryMigration.migrate(c);
        CompoundTag ne = dc.questProgress.get("ember_east_1");
        boolean lessons = ne != null && ne.getInt("o1") == 1 && ne.getInt("o4") == 1 && ne.getInt("o0") == 2;
        if (!lessons) bad.add("ember_east_1 remap " + ne);
        Quests.progress(c, "kill", "bandit", 1);
        if (!QuestRegistry.isDone(dc, "ember_east_1")) bad.add("ember_east_1 not finishable after migration");
        // 4) the old Sorbet audience under way: the new steps in front of it are passed, its talk objective kept
        FakePlayer e = player(s, "OldSaveSorbet", Faction.PIRATE);
        CharacterData de = Characters.get(e);
        de.flags.remove(Arcs.MIGRATED);
        de.completedQuests.addAll(List.of("ember_south_1", "ember_south_2", "south_karate_cup"));
        CompoundTag sp = new CompoundTag();
        sp.putInt("o0", 1);
        sp.putInt("o1", 5);
        sp.putInt("o2", 1);
        de.questProgress.put("south_sorbet_royal", sp);
        StoryMigration.migrate(e);
        if (!QuestRegistry.isDone(de, "south_sorbet_royal") || !de.questProgress.containsKey("capes_1")) bad.add("sorbet audience (talked already) did not complete: " + de.questProgress.keySet());
        for (String id : List.of("south_freezers", "south_choice", "south_brinicle")) if (!QuestRegistry.isDone(de, id)) bad.add(id + " not passed");
        // 5) the old Long Ring quest finished (it held the duel with Ayame): the duel, now a later step, counts as won, the
        //    steps in front of it are passed, and Gunnar's recruitment is on offer
        FakePlayer g = player(s, "OldSaveRing", Faction.PIRATE);
        CharacterData dg = Characters.get(g);
        dg.flags.remove(Arcs.MIGRATED);
        dg.completedQuests.add("ring_1");
        dg.discoveredIslands.add("long_ring_long_land");
        StoryMigration.migrate(g);
        for (String id : List.of("ring_round1", "ring_knots", "ring_pledge", "ring_choice", "ring_final"))
            if (!QuestRegistry.isDone(dg, id)) bad.add("ring: " + id + " not passed");
        if (QuestRegistry.isDone(dg, "ring_expose") || QuestRegistry.isDone(dg, "ring_quiet")) bad.add("ring: a decision branch was marked done");
        if (!dg.flags.contains("beat:ayame")) bad.add("ring: the old duel was not remembered");
        if (QuestRegistry.isDone(dg, "recruit_gunnar") || !QuestRegistry.canOffer(dg, QuestRegistry.get("recruit_gunnar"))) bad.add("ring: recruitment not on offer");
        // 6) every quest a migrated character has under way exists, and old completions are all still there
        for (CharacterData x : List.of(d, db, dc, de, dg))
            for (String id : x.questProgress.keySet()) if (QuestRegistry.get(id) == null) bad.add("under way but unknown: " + id);
        if (!db.completedQuests.isEmpty() && !db.completedQuests.contains("orange_1")) bad.add("orange: completions lost");
        // 7) migration runs once
        int again = StoryMigration.migrate(a);
        if (again != 0) bad.add("migrated twice");
        SelfTest.check("save migration: old quest ids land on the equivalent step, nobody sent back or stuck, once per character", bad.isEmpty(),
                bad.isEmpty() ? StoryMigration.legacyIds().size() + " changed quests mapped" : bad.toString());
        return 0;
    }
}
