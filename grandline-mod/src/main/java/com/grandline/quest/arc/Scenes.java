package com.grandline.quest.arc;

import com.grandline.GrandLine;
import com.grandline.net.GLNetwork;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.quest.Quests;
import com.grandline.quest.arc.Arc.Decision;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Authored scenes: a few lines, each spoken by someone (a notable, the player, a role, or the narrator), sometimes
 * ending in a decision. A scene plays when a step starts ({@code start}), when it ends ({@code end}) and when the
 * player talks to the person a step is about ({@code talk}; for a decision step that is the decision itself, which can
 * be replayed until it is made).
 *
 * <p>Text: {@code scene.grandline.<step>.<event>.<n>} = {@code "<speaker>|<line>"} (n from 1), where step is the quest id
 * (a faction variant first, then its base) and a version for the choice made in the chapter is tried first
 * ({@code ...<event>.<choice>.<n>}). Speakers: {@code you}, {@code narrator}, a notable id, a role id, or a
 * {@code speaker.grandline.<id>} name. Decision options: {@code scene.grandline.choice.<decision>.<choice>}. Every line
 * gets the player's character name as {@code %1$s}.
 *
 * <p>Scenes queue per player. Talking to someone shows the queue first, inside that conversation; otherwise the queue
 * is shown as its own conversation as soon as the player is out of a fight (or after half a minute regardless).
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class Scenes {
    private static final int MAX_LINES = 40;
    private static final Map<UUID, Deque<Pending>> QUEUE = new HashMap<>();
    private static final Map<UUID, Long> QUEUED_AT = new HashMap<>();
    /** Recently played scene prefixes per player (newest last), for tests and the journal. */
    private static final Map<UUID, Deque<String>> PLAYED = new HashMap<>();

    /** A queued scene: its key prefix and, for a decision scene, the decision. */
    record Pending(String prefix, @Nullable String decision) {}

    private Scenes() {}

    // ================================================================== resolving

    static boolean has(String key) {
        return Language.getInstance().has(key);
    }

    /**
     * The key prefix of a step's scene for this character, or null when the step has none: the variant's own scene,
     * then the base step's, each first in the version for a choice the character made in the chapter.
     */
    @Nullable
    public static String prefix(CharacterData d, String questId, String event) {
        Arc a = Arcs.arcOf(questId);
        List<String> ids = questId.equals(QuestRegistry.baseId(questId)) ? List.of(questId) : List.of(questId, QuestRegistry.baseId(questId));
        Collection<String> chosen = a == null ? List.of() : Arcs.choices(d, a).values();
        for (String id : ids) {
            for (String c : chosen) {
                String p = "scene.grandline." + id + "." + event + "." + c;
                if (has(p + ".1")) return p;
            }
            String p = "scene.grandline." + id + "." + event;
            if (has(p + ".1")) return p;
        }
        return null;
    }

    /** Number of lines a scene has. */
    public static int lines(String prefix) {
        int n = 0;
        while (n < MAX_LINES && has(prefix + "." + (n + 1))) n++;
        return n;
    }

    // ================================================================== playing

    /** Queue a step's scene for an event ("start", "end"), if it has one. Returns the prefix queued, or null. */
    @Nullable
    public static String play(ServerPlayer sp, QuestDef q, String event) {
        CharacterData d = Characters.get(sp);
        Decision dec = event.equals("start") ? Arcs.decisionAt(q.id()) : null;
        if (dec != null && Arcs.choice(d, dec.id()) == null) {
            // a decision step opens on the decision itself
            String p = prefix(d, q.id(), "talk");
            if (p != null) {
                enqueue(sp, new Pending(p, dec.id()));
                return p;
            }
        }
        String p = prefix(d, q.id(), event);
        if (p != null) enqueue(sp, new Pending(p, null));
        return p;
    }

    /**
     * Talking to a notable: the scenes of the active steps that are about them (a pending decision they present, or a
     * step's own "talk" scene for its giver).
     */
    public static void onTalk(ServerPlayer sp, GLNpc npc) {
        if (npc.notableId().isEmpty()) return;
        CharacterData d = Characters.get(sp);
        for (String id : List.copyOf(d.questProgress.keySet())) {
            QuestDef q = QuestRegistry.get(id);
            if (q == null || Arcs.arcOf(id) == null) continue;
            Decision dec = Arcs.decisionAt(id);
            if (dec != null && Arcs.choice(d, dec.id()) == null && dec.presenter().equals(npc.notableId())) {
                String p = prefix(d, id, "talk");
                if (p != null) enqueue(sp, new Pending(p, dec.id()));
                continue;
            }
            if (dec == null && q.giverRole().equals("notable:" + npc.notableId())) {
                String p = prefix(d, id, "talk");
                if (p != null) enqueue(sp, new Pending(p, null));
            }
        }
    }

    static void enqueue(ServerPlayer sp, Pending p) {
        Deque<Pending> q = QUEUE.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>());
        for (Pending x : q) if (x.prefix().equals(p.prefix())) return;
        q.addLast(p);
        QUEUED_AT.putIfAbsent(sp.getUUID(), sp.level().getGameTime());
        Deque<String> log = PLAYED.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>());
        log.addLast(p.prefix());
        while (log.size() > 64) log.pollFirst();
    }

    /** Whether anything is waiting to be shown to this player. */
    public static boolean pending(ServerPlayer sp) {
        Deque<Pending> q = QUEUE.get(sp.getUUID());
        return q != null && !q.isEmpty();
    }

    /** Scenes played (queued) for this player, oldest first. */
    public static List<String> played(ServerPlayer sp) {
        return List.copyOf(PLAYED.getOrDefault(sp.getUUID(), new ArrayDeque<>()));
    }

    /** Forget a player's queue and log (tests). */
    public static void reset(ServerPlayer sp) {
        QUEUE.remove(sp.getUUID());
        QUEUED_AT.remove(sp.getUUID());
        PLAYED.remove(sp.getUUID());
    }

    /**
     * Take the queue as scene data for the dialogue screen ({@code lines}: line keys; {@code decision} and
     * {@code choices} when it ends in a decision), or null when nothing is queued. A decision scene ends the data: what
     * follows it stays queued for after the choice.
     */
    @Nullable
    public static CompoundTag drain(ServerPlayer sp) {
        Deque<Pending> q = QUEUE.get(sp.getUUID());
        if (q == null || q.isEmpty()) return null;
        CharacterData d = Characters.get(sp);
        CompoundTag t = new CompoundTag();
        ListTag lines = new ListTag();
        while (!q.isEmpty()) {
            Pending p = q.peekFirst();
            Decision dec = p.decision() == null ? null : Arcs.decision(p.decision());
            if (dec != null && Arcs.choice(d, dec.id()) != null) { // decided meanwhile
                q.pollFirst();
                continue;
            }
            if (dec != null && !lines.isEmpty()) break; // a decision gets its own screen, after what came before it
            q.pollFirst();
            int n = lines(p.prefix());
            for (int i = 1; i <= n; i++) lines.add(net.minecraft.nbt.StringTag.valueOf(p.prefix() + "." + i));
            if (dec != null) {
                t.putString("decision", dec.id());
                ListTag choices = new ListTag();
                for (Arc.Choice c : dec.choices()) {
                    CompoundTag ct = new CompoundTag();
                    ct.putString("id", c.id());
                    ct.putString("k", "scene.grandline.choice." + dec.id() + "." + c.id());
                    choices.add(ct);
                }
                t.put("choices", choices);
                break;
            }
        }
        if (q.isEmpty()) {
            QUEUE.remove(sp.getUUID());
            QUEUED_AT.remove(sp.getUUID());
        } else {
            QUEUED_AT.put(sp.getUUID(), sp.level().getGameTime());
        }
        if (lines.isEmpty() && !t.contains("decision")) return null;
        t.put("lines", lines);
        t.putString("you", d.name == null || d.name.isEmpty() ? sp.getName().getString() : d.name);
        return t;
    }

    // ================================================================== decisions

    /**
     * The player picked an option of a decision scene. Accepted only while the decision's step is active and nothing
     * was chosen yet; records the choice ({@code choice:<decision>=<choice>}) and completes the step's choice objective.
     */
    public static boolean choose(ServerPlayer sp, String decisionId, String choiceId) {
        Decision dec = Arcs.decision(decisionId);
        if (dec == null) return false;
        CharacterData d = Characters.get(sp);
        if (Arcs.choice(d, dec.id()) != null || Arcs.active(d, dec.step()) == null) return false;
        Arc.Choice c = dec.choice(choiceId);
        if (c == null) return false;
        d.flags.add(Arcs.choiceFlag(dec.id(), c.id()));
        sp.sendSystemMessage(Component.translatable("arc.grandline.chose", Component.translatable("scene.grandline.choice." + dec.id() + "." + c.id()))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        CharacterManager.markDirty(sp);
        Quests.progress(sp, "choice", dec.id(), 1);
        return true;
    }

    // ================================================================== showing queued scenes outside a conversation

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        if (QUEUE.isEmpty() || s.getTickCount() % 10 != 4) return;
        long now = s.overworld().getGameTime();
        for (UUID id : List.copyOf(QUEUE.keySet())) {
            ServerPlayer sp = s.getPlayerList().getPlayer(id);
            if (sp == null) {
                QUEUE.remove(id); // logged out: the steps are saved; their scenes can be replayed by talking to the cast
                QUEUED_AT.remove(id);
                continue;
            }
            if (sp instanceof FakePlayer) continue;
            long since = now - QUEUED_AT.getOrDefault(id, now);
            if (since < 10 || !calm(sp) && since < 600) continue;
            show(sp);
        }
    }

    /** Send the queued scenes as their own conversation now. */
    public static void show(ServerPlayer sp) {
        if (sp instanceof FakePlayer || sp.connection == null) return;
        CompoundTag scene = drain(sp);
        if (scene == null) return;
        CompoundTag t = new CompoundTag();
        t.putInt("npc", -1);
        t.putString("name", "");
        t.putString("role", "");
        t.put("scene", scene);
        t.put("options", new ListTag());
        GLNetwork.toPlayer(sp, new GLNetwork.OpenScreen("dialogue", t));
    }

    /** Not in the middle of a fight: unhurt for three seconds and nobody close by is after them. */
    static boolean calm(ServerPlayer sp) {
        if (sp.tickCount - sp.getLastHurtByMobTimestamp() < 60 && sp.getLastHurtByMob() != null) return false;
        for (GLNpc n : sp.serverLevel().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(14), n -> n.getTarget() == sp)) return false;
        return !Characters.get(sp).isKnockedDown();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        QUEUE.remove(e.getEntity().getUUID());
        QUEUED_AT.remove(e.getEntity().getUUID());
    }
}
