package com.grandline.quest.arc;

import com.grandline.GrandLine;
import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * How a chapter changes the world for the player who lived it, in ways the game already shows: what people say
 * (notables, townsfolk and the local Marines), shop prices and stock, reputation and rewards that depend on the
 * choice made, and help in the climax from the people the choice sided with. Everything is read from the character's
 * own state (completed steps and {@code choice:} flags), so it is per player and survives restarts.
 *
 * <p>Lines: {@code arc.grandline.<arc>.during.<notable>[.<choice>]} while the chapter is under way;
 * {@code arc.grandline.<arc>.after.<notable>[.<choice>]}, {@code ...after.townsfolk.<island>[.<choice>]} and
 * {@code ...after.marines.<island>[.<choice>]} once its climax is done.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class Consequences {
    /** A shop discount on an island once a chapter's climax is done (for one choice, or any). */
    record Discount(String arc, String island, @Nullable String choice, double factor) {}

    /** Extra stock in an island's shops once a chapter's climax is done. */
    public record Stock(String arc, String island, @Nullable String choice, String item, int count, long price) {}

    /** Extra reward when a step finishes, for one choice (or any): Beli, reputation, items, and a line saying why. */
    record Bonus(String step, @Nullable String choice, long beli, Map<String, Integer> rep, List<Supplier<ItemStack>> items, String message) {}

    /** Help in a fight: while a step is active (for one choice), near a notable (or anywhere), an effect and a line. */
    record Aid(String step, @Nullable String choice, @Nullable String near, Holder<MobEffect> effect, int amplifier, int seconds, String message) {}

    private static final List<Discount> DISCOUNTS = new ArrayList<>();
    private static final List<Stock> STOCK = new ArrayList<>();
    private static final List<Bonus> BONUSES = new ArrayList<>();
    private static final List<Aid> AIDS = new ArrayList<>();
    /** arc id → islands whose townsfolk talk about it (the chapter's own island is always one). */
    private static final Map<String, List<String>> AFFECTS = new HashMap<>();
    /** player|step → game time an aid was last given. */
    private static final Map<String, Long> AIDED = new HashMap<>();

    private Consequences() {}

    // ================================================================== declarations (chapter files)

    static void affects(String arc, String... islands) {
        AFFECTS.computeIfAbsent(arc, k -> new ArrayList<>()).addAll(List.of(islands));
    }

    static void discount(String arc, String island, @Nullable String choice, double factor) {
        DISCOUNTS.add(new Discount(arc, island, choice, factor));
    }

    static void stock(String arc, String island, @Nullable String choice, String item, int count, long price) {
        STOCK.add(new Stock(arc, island, choice, item, count, price));
    }

    static void bonus(String step, @Nullable String choice, long beli, Map<String, Integer> rep, List<Supplier<ItemStack>> items, String message) {
        BONUSES.add(new Bonus(step, choice, beli, rep, items, message));
    }

    static void aid(String step, @Nullable String choice, @Nullable String near, Holder<MobEffect> effect, int amplifier, int seconds, String message) {
        AIDS.add(new Aid(step, choice, near, effect, amplifier, seconds, message));
    }

    // ================================================================== state

    /** The last step of a chapter that plays its climax (the boss fight). */
    @Nullable
    public static String climax(Arc a) {
        String last = null;
        for (Arc.Step s : a.steps()) if (s.beats().contains(Arc.Beat.CLIMAX) && Arcs.decisionAt(s.quest()) == null) last = s.quest();
        return last;
    }

    /** Whether the character has finished this chapter's climax. */
    public static boolean climaxDone(CharacterData d, Arc a) {
        String c = climax(a);
        return c != null && QuestRegistry.isDone(d, c);
    }

    /** Whether the character has started the chapter (any step active or done). */
    static boolean started(CharacterData d, Arc a) {
        for (Arc.Step s : a.steps()) if (QuestRegistry.isDone(d, s.quest()) || Arcs.active(d, s.quest()) != null) return true;
        return false;
    }

    /** "decision=choice" condition: null always holds; otherwise the character made that choice. */
    static boolean holds(CharacterData d, @Nullable String choice) {
        if (choice == null) return true;
        int eq = choice.indexOf('=');
        String made = Arcs.choice(d, choice.substring(0, eq));
        return choice.substring(eq + 1).equals(made);
    }

    static List<String> islands(Arc a) {
        List<String> l = new ArrayList<>();
        l.add(a.island());
        l.addAll(AFFECTS.getOrDefault(a.id(), List.of()));
        return l;
    }

    // ================================================================== what people say

    /**
     * The line an NPC greets this character with because of a chapter they lived, or null for the usual greeting.
     * Notables of the cast: during the chapter and after its climax; townsfolk and Marines of the islands the chapter
     * touched: after its climax. The latest chapter that has something to say wins.
     */
    @Nullable
    public static String greeting(GLNpc npc, CharacterData d) {
        List<Arc> arcs = new ArrayList<>(Arcs.all());
        Collections.reverse(arcs);
        String who = npc.notableId();
        for (Arc a : arcs) {
            if (!a.full()) continue;
            Collection<String> chosen = Arcs.choices(d, a).values();
            if (!who.isEmpty()) {
                if (a.castMember(who) == null) continue;
                String stage = climaxDone(d, a) ? "after" : started(d, a) ? "during" : null;
                if (stage == null) continue;
                String k = pick("arc.grandline." + a.id() + "." + stage + "." + who, chosen);
                if (k != null) return k;
                continue;
            }
            if (!islands(a).contains(npc.islandId()) || !climaxDone(d, a)) continue;
            String group = npc.faction() == Faction.MARINE ? "marines" : npc.faction() == Faction.CIVILIAN ? "townsfolk" : null;
            if (group == null) continue;
            String k = pick("arc.grandline." + a.id() + ".after." + group + "." + npc.islandId(), chosen);
            if (k != null) return k;
        }
        return null;
    }

    @Nullable
    private static String pick(String base, Collection<String> chosen) {
        Language lang = Language.getInstance();
        for (String c : chosen) if (lang.has(base + "." + c)) return base + "." + c;
        return lang.has(base) ? base : null;
    }

    // ================================================================== shops

    /** Price factor in an island's shops from the chapters this character finished there (1 = none). */
    public static double priceFactor(CharacterData d, String island) {
        double f = 1;
        for (Discount x : DISCOUNTS) {
            if (!x.island().equals(island)) continue;
            Arc a = Arcs.get(x.arc());
            if (a != null && climaxDone(d, a) && holds(d, x.choice())) f *= x.factor();
        }
        return f;
    }

    /** Extra goods an island's merchants sell this character because of a chapter. */
    public static List<Stock> stock(CharacterData d, String island) {
        List<Stock> l = new ArrayList<>();
        for (Stock x : STOCK) {
            if (!x.island().equals(island)) continue;
            Arc a = Arcs.get(x.arc());
            if (a != null && climaxDone(d, a) && holds(d, x.choice())) l.add(x);
        }
        return l;
    }

    // ================================================================== rewards that depend on the choice

    /** Called when a step finishes (after its own rewards, once): the bonus for the choice the character made. */
    public static void onFinish(ServerPlayer sp, CharacterData d, QuestDef q) {
        String base = QuestRegistry.baseId(q.id());
        for (Bonus b : BONUSES) {
            if (!b.step().equals(base) || !holds(d, b.choice())) continue;
            d.beli += b.beli();
            b.rep().forEach(d::addReputation);
            for (Supplier<ItemStack> it : b.items()) {
                ItemStack st = it.get();
                if (!sp.getInventory().add(st)) sp.drop(st, false);
            }
            sp.sendSystemMessage(Component.translatable(b.message()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        CharacterManager.markDirty(sp);
    }

    // ================================================================== help in the climax

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        if (AIDS.isEmpty() || s.getTickCount() % 40 != 21) return;
        long now = s.overworld().getGameTime();
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) tickAid(sp, now);
    }

    /** Give this player the help their choice earned, if they are where it applies (also called by the self-test). */
    public static int tickAid(ServerPlayer sp, long now) {
        CharacterData d = Characters.get(sp);
        int given = 0;
        for (Aid a : AIDS) {
            if (Arcs.active(d, a.step()) == null || !holds(d, a.choice())) continue;
            if (a.near() != null) {
                boolean near = !sp.serverLevel().getEntitiesOfClass(GLNpc.class, sp.getBoundingBox().inflate(32), n -> a.near().equals(n.notableId()) && n.isAlive()).isEmpty();
                if (!near) continue;
            }
            String key = sp.getStringUUID() + "|" + a.step();
            Long last = AIDED.get(key);
            if (last != null && now - last < 2400) continue;
            AIDED.put(key, now);
            sp.addEffect(new MobEffectInstance(a.effect(), a.seconds() * 20, a.amplifier()));
            sp.sendSystemMessage(Component.translatable(a.message()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            given++;
        }
        return given;
    }

    /** Declared bonuses, aids, discounts and stock (self-test: every line they show exists). */
    public static List<String> messageKeys() {
        List<String> l = new ArrayList<>();
        for (Bonus b : BONUSES) l.add(b.message());
        for (Aid a : AIDS) l.add(a.message());
        return l;
    }

    public static int declared() {
        return DISCOUNTS.size() + STOCK.size() + BONUSES.size() + AIDS.size();
    }
}
