package com.grandline.quest.arc;

import com.grandline.balance.Tuning;
import com.grandline.npc.Archetype;
import com.grandline.npc.Archetypes;
import com.grandline.npc.GLNpc;
import com.grandline.npc.HumanoidNpc;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.npc.notable.NotableState;
import com.grandline.player.CharacterData;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.registry.ModEntities;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * What the journal says about a quest beyond its own text: whether it is part of the story, a side story or a job;
 * for a story step its chapter, who is who (the giver first), why it matters, and a recap of the chapter so far (one
 * line per completed step); and for any quest with foes, how dangerous the worst of them is for this player
 * ({@link Tuning#threat}), e.g. "Dangerous — Mordecai Gallowgate (boss)".
 */
public final class ArcJournal {
    private ArcJournal() {}

    /** "story" (the saga: chapters, recruitment, crew quests), "job" (generated, board, repeatable) or "side". */
    public static String group(QuestDef q) {
        if (QuestRegistry.isGenerated(q.id()) || q.repeatable() || q.arc().equals("board") || q.arc().equals("activity")) return "job";
        if (Arcs.arcOf(q.id()) != null || q.arc().equals("recruit") || q.arc().equals("crew")) return "story";
        if (q.arc().equals("local") || q.arc().equals("paths") || q.arc().equals("awakening") || q.arc().equals("embers")) return "side";
        return QuestRegistry.storyQuestIds().contains(q.id()) ? "story" : "side";
    }

    public static void fill(ServerPlayer sp, CharacterData d, QuestDef q, CompoundTag prog, CompoundTag e, Function<Component, String> json) {
        Arc a = Arcs.arcOf(q.id());
        Language lang = Language.getInstance();
        ListTag who = new ListTag();
        String giver = q.giverRole().startsWith("notable:") ? q.giverRole().substring(8) : null;
        if (a != null) {
            e.putString("chapter", json.apply(chapterLine(a)));
            e.putString("arc", a.id());
            if (lang.has(a.whyKey())) e.putString("why", json.apply(Component.translatable(a.whyKey())));
            if (giver != null) who.add(StringTag.valueOf(json.apply(castLine(a, giver, true))));
            for (Arc.Cast c : a.cast()) if (!c.who().equals(giver)) who.add(StringTag.valueOf(json.apply(castLine(a, c.who(), false))));
            ListTag recap = new ListTag();
            for (String k : Arcs.recapKeys(d, a, lang::has)) recap.add(StringTag.valueOf(json.apply(Component.translatable(k))));
            e.put("recap", recap);
        } else if (giver != null && NotableCharacters.get(giver) != null) {
            var n = NotableCharacters.get(giver);
            who.add(StringTag.valueOf(json.apply(Component.translatable("journal.grandline.who.giver", NotableCharacters.name(n), NotableCharacters.title(n)))));
        } else if (!q.giverRole().startsWith("notable:") && Archetypes.has(q.giverRole())) {
            who.add(StringTag.valueOf(json.apply(Component.translatable("journal.grandline.who.role", Component.translatable("role.grandline." + q.giverRole())))));
        }
        if (!who.isEmpty()) e.put("who", who);
        Component danger = danger(sp, q, prog);
        if (danger != null) e.putString("danger", json.apply(danger));
    }

    public static Component chapterLine(Arc a) {
        return Component.translatable("journal.grandline.chapter", a.chapter(), Component.translatable(a.partKey()), Component.translatable(a.titleKey()));
    }

    /** "Name — what they are to this chapter" (the giver marked as such). */
    static Component castLine(Arc a, String who, boolean giver) {
        var n = NotableCharacters.get(who);
        Component name = n != null ? NotableCharacters.name(n) : Component.translatable("speaker.grandline." + who);
        Component line = Language.getInstance().has(a.castKey(who)) ? Component.translatable(a.castKey(who))
                : n != null ? NotableCharacters.title(n) : Component.literal("");
        return Component.translatable(giver ? "journal.grandline.who.cast_giver" : "journal.grandline.who.cast", name, line);
    }

    // ================================================================== danger

    /**
     * The most dangerous foe of the step's remaining objectives for this player, as "Threat — who (rank)", or a
     * "nothing to fight" line for steps without foes; null if it cannot be judged.
     */
    @Nullable
    public static Component danger(ServerPlayer sp, QuestDef q, CompoundTag prog) {
        Tuning.Threat worst = null;
        Component worstWho = null;
        boolean anyFoe = false;
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestDef.Objective o = q.objectives().get(i);
            if (prog.getInt("o" + i) >= o.count()) continue;
            String type = o.type();
            if (!(type.equals("kill") || type.equals("defeat") || type.equals("arrest"))) continue;
            String t = o.target();
            var n = NotableCharacters.get(t);
            if (n == null && !Archetypes.has(t)) continue;
            if (n != null && NotableState.get(sp.server).isGone(t)) continue;
            anyFoe = true;
            Tuning.Threat th;
            try {
                th = threat(sp, q, t);
            } catch (RuntimeException ex) {
                th = null;
            }
            if (th == null) continue;
            if (worst == null || th.ordinal() > worst.ordinal()) {
                worst = th;
                worstWho = foeLabel(q, o, n != null);
            }
        }
        if (worst == null) return anyFoe ? null : Component.translatable("journal.grandline.danger.none");
        return Component.translatable("journal.grandline.danger", Component.translatable("journal.grandline.threat." + worst.name().toLowerCase(java.util.Locale.ROOT)), worstWho);
    }

    private static Component foeLabel(QuestDef q, QuestDef.Objective o, boolean notable) {
        if (!notable) {
            Component role = Component.translatable("role.grandline." + o.target());
            return o.count() > 1 ? Component.translatable("journal.grandline.foe.many", role, o.count()) : role;
        }
        var n = NotableCharacters.get(o.target());
        Arc a = Arcs.arcOf(q.id());
        Arc.Cast c = a == null ? null : a.castMember(o.target());
        boolean boss = com.grandline.boss.Bosses.get(o.target()) != null || c != null && c.role() == Arc.Role.BOSS
                || c != null && c.role() == Arc.Role.VILLAIN && a.step(QuestRegistry.baseId(q.id())) != null
                && a.step(QuestRegistry.baseId(q.id())).beats().contains(Arc.Beat.CLIMAX);
        return Component.translatable(boss ? "journal.grandline.foe.boss" : "journal.grandline.foe.named", NotableCharacters.name(n));
    }

    /** The threat of a foe for this player: the live one if it is near, else a stand-in built from its data. */
    @Nullable
    static Tuning.Threat threat(ServerPlayer sp, QuestDef q, String target) {
        var n = NotableCharacters.get(target);
        for (GLNpc g : sp.serverLevel().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(96),
                g -> g.isAlive() && (n != null ? target.equals(g.notableId()) : target.equals(g.role()) && g.notableId().isEmpty())))
            return Tuning.threat(sp, g);
        HumanoidNpc probe = ModEntities.HUMANOID_NPC.get().create(sp.serverLevel());
        if (probe == null) return null;
        CompoundTag t = probe.saveWithoutId(new CompoundTag());
        String island = n != null ? n.island() : q.island() == null || q.island().startsWith("*") ? "" : q.island();
        int tier;
        if (n != null) {
            t.putString("Role", n.role());
            t.putString("Notable", n.id());
            t.putByte("Flags", GLNpc.FLAG_NOTABLE);
            tier = n.tier();
        } else {
            Archetype a = Archetypes.get(target);
            var plan = com.grandline.world.WorldPlan.of(sp.server);
            var isl = plan == null || island.isEmpty() ? null : plan.byId(island);
            int it = isl != null ? isl.tier : Tuning.tierOfLevel(com.grandline.player.Characters.get(sp).level);
            tier = Math.max(1, Math.max(a.baseTier(), it + (a.baseTier() > 3 ? a.baseTier() - 3 : 0)));
            if (a.faction() == Faction.CIVILIAN && a.hostility() == Archetype.Hostility.PASSIVE) tier = 1;
            t.putString("Role", a.id());
            t.putString("Notable", "");
            t.putByte("Flags", (byte) 0);
        }
        t.putInt("Tier", tier);
        t.putString("Island", island);
        probe.load(t);
        return Tuning.threat(sp, probe);
    }

    /** A plain-text danger line (self-test). */
    public static String dangerText(ServerPlayer sp, QuestDef q, CompoundTag prog) {
        Component c = danger(sp, q, prog);
        return c == null ? "" : c.getString();
    }

    /** "Chapter 1 · East Blue — The Justice Levy" as plain text (self-test). */
    public static MutableComponent chapterText(Arc a) {
        return chapterLine(a).copy();
    }
}
