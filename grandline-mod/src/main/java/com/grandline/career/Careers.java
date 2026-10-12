package com.grandline.career;

import com.grandline.GrandLine;
import com.grandline.journey.Journey;
import com.grandline.npc.GLNpc;
import com.grandline.npc.NpcOutcomes;
import com.grandline.npc.crew.CrewManager;
import com.grandline.npc.notable.NotableCharacters;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.grandline.player.Faction;
import com.grandline.quest.QuestDef;
import com.grandline.quest.QuestRegistry;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Careers: one per starting path, with ranks the character climbs (ladders in {@link CareerTrack}), real perks
 * ({@link CareerPerks}, {@link Squads}, {@link Warlords}, {@link Missions}) and titles shown before the player's name
 * in chat and the tab list (and on name tags and the character sheet).
 * <ul>
 *   <li><b>Marines</b> earn merit from arrests, pirate bounties brought in, sea battles won against pirates and story
 *   deeds; once qualified (merit, level, and from Ensign up enlistment, sea victories, the saga's Parts, Haki and the
 *   limited Admiral seats) they report to a Marine officer for the promotion talk. Attacking Marines or civilians
 *   earns demerits (a demotion every ten); a Seaman Recruit who keeps at it, or a Marine whose standing collapses,
 *   deserts: a pirate with a bounty.</li>
 *   <li><b>Pirates</b> rise with their bounty and deeds: Rookie, Supernova (100M in Paradise), Emperor (1B and an
 *   Emperor beaten or territory held); a Supernova may accept the Government's letter of marque (Warlord).</li>
 *   <li><b>Revolutionaries</b> run missions for the cell and report for promotion; <b>bounty hunters</b> climb licence
 *   tiers by the bounties they collect; <b>civilians</b> make a name in their best trade.</li>
 * </ul>
 * Game code calls the {@code on...} hooks from where things happen (one line each); big promotions are announced to
 * the whole server and fire journey milestones. The career belongs to the character: a permadeath starts it over.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class Careers {
    /** Character flag: a Marine who deserted (title "Deserter" until the pirate makes a name). */
    public static final String DESERTER = "deserter";
    /** Character flags set by the path quests. */
    public static final String ENLISTED = "enlisted", REV_CONTACT = "revolution_contact", LICENSED = "licensed_hunter";
    /** Demerits that cost a Marine one rank. */
    public static final int DEMERITS_PER_DEMOTION = 10;
    /** Story quests that close a Part of the saga (a pirate's deeds). */
    static final Set<String> PART_ENDS = Set.of("logue_3", "north_lvneel_passage", "west_ilisia_audience", "south_sorbet_royal", "sabaody_3", "war_3", "lodestar_1");

    /** Journey milestone per rank ("track:rank" → milestone id); each is the criterion of a journey advancement. */
    public static final Map<String, String> MILESTONES = new LinkedHashMap<>();

    static {
        for (String r : List.of("seaman", "ensign", "captain", "vice_admiral", "admiral", "fleet_admiral")) MILESTONES.put("marine:" + r, "career_marine_" + r);
        MILESTONES.put("pirate:supernova", "career_pirate_supernova");
        MILESTONES.put("pirate:warlord", "career_pirate_warlord");
        MILESTONES.put("pirate:emperor", "career_pirate_emperor");
        MILESTONES.put("revolutionary:officer", "career_revolutionary_officer");
        MILESTONES.put("revolutionary:commander", "career_revolutionary_commander");
        MILESTONES.put("revolutionary:chief_of_staff", "career_revolutionary_chief");
        for (String r : List.of("licensed", "gold", "black")) MILESTONES.put("bounty_hunter:" + r, "career_hunter_" + r);
        for (String r : List.of("apprentice", "master", "grand")) MILESTONES.put("civilian:" + r, "career_civilian_" + r);
    }

    /** Tests: every career message to a player (and every announcement, with a null player). Null in normal play. */
    @Nullable public static BiConsumer<ServerPlayer, Component> tap;

    // ================================================================== messages

    static void tell(ServerPlayer sp, Component c) {
        if (tap != null) tap.accept(sp, c);
        sp.sendSystemMessage(c);
    }

    static void announce(MinecraftServer server, Component c) {
        if (tap != null) tap.accept(null, c);
        server.getPlayerList().broadcastSystemMessage(c, false);
    }

    static long now(MinecraftServer s) {
        return s.overworld().getGameTime();
    }

    static long day(MinecraftServer s) {
        return s.overworld().getDayTime() / 24000L;
    }

    static Component title(CharacterData d) {
        MutableComponent t = CareerTrack.title(d);
        return t != null ? t : Component.literal("");
    }

    static Component rankTitle(CharacterData d, CareerTrack t, int rank) {
        if (t == CareerTrack.CIVILIAN && rank > 0)
            return Component.translatable(t.rankKey(rank), Component.translatable("career.grandline.civilian.spec." + CareerTrack.specialty(d))).withStyle(t.color);
        return Component.translatable(t.rankKey(rank)).withStyle(t.color);
    }

    // ================================================================== state

    /** The character's career, started (or started over after a change of faction) as needed; null without one. */
    @Nullable
    public static CareerTrack ensure(ServerPlayer sp, CharacterData d) {
        if (!d.created) return null;
        CareerTrack t = CareerTrack.of(d.faction);
        if (t == null) return null;
        if (!t.id().equals(d.career.track)) {
            d.career.reset(t.id());
            d.factionRank = 0;
            d.career.lastPaidDay = day(sp.server);
            CharacterManager.markDirty(sp);
        }
        return t;
    }

    public static int rank(CharacterData d) {
        return CareerTrack.rankOf(d);
    }

    /** The seat a rank occupies (limited per world), or null. */
    @Nullable
    static String seatFor(CareerTrack t, int rank) {
        if (t == CareerTrack.MARINE && rank == 11) return CareerWorldData.ADMIRAL;
        if (t == CareerTrack.MARINE && rank == 12) return CareerWorldData.FLEET_ADMIRAL;
        if (t == CareerTrack.REVOLUTIONARY && rank == 3) return CareerWorldData.CHIEF_OF_STAFF;
        return null;
    }

    // ================================================================== requirements

    private static Component req(String key, Object... args) {
        return Component.translatable("career.grandline.req." + key, args);
    }

    private static String n(long v) {
        return String.format("%,d", v);
    }

    /** What still stands between the character and rank {@code i} of the career (empty: qualified). */
    public static List<Component> missing(ServerPlayer sp, CharacterData d, CareerTrack t, int i) {
        List<Component> out = new ArrayList<>();
        if (i > t.top()) return List.of(req("top"));
        CareerTrack.Rank r = t.rank(i);
        long p = t.progress(d);
        if (p < r.need()) out.add(req(switch (t) {
            case MARINE, REVOLUTIONARY -> "merit";
            case PIRATE -> "bounty";
            case BOUNTY_HUNTER -> "collected";
            case CIVILIAN -> "points";
        }, n(p), n(r.need())));
        if (d.level < r.level()) out.add(req("level", r.level()));
        gates(sp, d, t, i, out);
        return out;
    }

    /** The gates beyond merit and level (see the class comment). */
    static void gates(ServerPlayer sp, CharacterData d, CareerTrack t, int i, List<Component> out) {
        CareerState c = d.career;
        String seat = seatFor(t, i);
        if (seat != null && !CareerWorldData.get(sp.server).open(seat, d.characterId)) out.add(req("seat"));
        switch (t) {
            case MARINE -> {
                if (i >= 4 && !d.flags.contains(ENLISTED)) out.add(req("enlisted"));
                if (i >= 7 && c.count("sea_wins") < (i >= 8 ? 3 : 1)) out.add(req("sea_wins", c.count("sea_wins"), i >= 8 ? 3 : 1));
                if (i >= 9 && !QuestRegistry.isDone(d, "sabaody_3")) out.add(req("part", "II"));
                if (i == 10 && !(d.observationUnlocked || d.armamentUnlocked)) out.add(req("haki_one"));
                if (i >= 11 && !QuestRegistry.isDone(d, "war_3")) out.add(req("part", "III"));
                if (i >= 11 && !(d.observationUnlocked && d.armamentUnlocked)) out.add(req("haki_both"));
                if (i >= 12 && !QuestRegistry.isDone(d, "lodestar_1")) out.add(req("part", "IV"));
                if (i >= 12 && !d.conquerorAwakened) out.add(req("conqueror"));
            }
            case PIRATE -> {
                if (i == 1 && !inGrandLine(sp, d)) out.add(req("paradise"));
                if (i == 1 && c.count("deeds") < 2) out.add(req("deeds", c.count("deeds"), 2));
                if (i == 2 && !c.emperorDeed && c.territories.isEmpty()) out.add(req("emperor_deed"));
            }
            case REVOLUTIONARY -> {
                if (i >= 1 && !d.flags.contains(REV_CONTACT)) out.add(req("contact"));
                int need = i >= 3 ? 15 : i >= 2 ? 6 : 2;
                if (c.count("missions") < need) out.add(req("missions", c.count("missions"), need));
                if (i >= 3 && !QuestRegistry.isDone(d, "war_3")) out.add(req("part", "III"));
            }
            case BOUNTY_HUNTER -> {
                if (i >= 1 && !d.flags.contains(LICENSED) && c.collected < 50_000) out.add(req("licence"));
                int heads = i >= 5 ? 5 : i >= 4 ? 1 : 0;
                if (c.count("heads") < heads) out.add(req("heads", c.count("heads"), heads));
            }
            case CIVILIAN -> {}
        }
    }

    /** Paradise or beyond: an island of the Grand Line discovered. */
    static boolean inGrandLine(ServerPlayer sp, CharacterData d) {
        WorldPlan plan = WorldPlan.of(sp.server);
        if (plan == null) return false;
        for (String id : d.discoveredIslands) {
            Island isl = plan.byId(id);
            if (isl != null && isl.zone.isGrandLine()) return true;
        }
        return false;
    }

    // ================================================================== promotion and demotion

    /**
     * Promotions that need no talk happen at once; for Marines and Revolutionaries a qualified character is told to
     * report to an officer (once per rank).
     */
    public static void check(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = ensure(sp, d);
        if (t == null) return;
        if (!t.talk) {
            for (int guard = 0; guard <= t.top() && promote(sp, d, t); guard++) ;
            return;
        }
        int next = rank(d) + 1;
        if (!d.career.due && next <= t.top() && missing(sp, d, t, next).isEmpty()) {
            d.career.due = true;
            tell(sp, Component.translatable("grandline.career.due." + t.id(), rankTitle(d, t, next)).withStyle(ChatFormatting.GOLD));
            CharacterManager.markDirty(sp);
        }
    }

    /** Rise one rank if qualified. For talk careers this is called by the promotion talk. */
    public static boolean promote(ServerPlayer sp, CharacterData d, CareerTrack t) {
        int from = rank(d), to = from + 1;
        if (to > t.top() || !missing(sp, d, t, to).isEmpty()) return false;
        CareerWorldData w = CareerWorldData.get(sp.server);
        String oldSeat = seatFor(t, from), newSeat = seatFor(t, to);
        if (newSeat != null && !w.take(newSeat, d.characterId, sp.getUUID(), d.name, now(sp.server))) return false;
        if (oldSeat != null && !oldSeat.equals(newSeat)) w.leave(oldSeat, d.characterId);
        CareerState c = d.career;
        d.factionRank = to;
        c.best = Math.max(c.best, to);
        c.due = false;
        String rid = t.rank(to).id();
        Component title = rankTitle(d, t, to);
        tell(sp, Component.translatable("grandline.career.promoted", title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        CareerPerks.onPromoted(sp, d, t, to);
        if (big(t, to)) announce(sp.server, Component.translatable("grandline.career.announce." + t.id(), d.name, title).withStyle(ChatFormatting.YELLOW));
        String m = MILESTONES.get(t.id() + ":" + rid);
        if (m != null) Journey.milestone(sp, m);
        if (t == CareerTrack.PIRATE && to == t.top() && c.warlord) Warlords.renounce(sp, d); // no Emperor serves the Government
        GrandLine.LOGGER.info("Career: {} ({}) promoted to {} {}", d.name, sp.getGameProfile().getName(), t.id(), rid);
        changed(sp);
        return true;
    }

    /** Promotions announced to the whole server. */
    static boolean big(CareerTrack t, int rank) {
        return switch (t) {
            case MARINE -> rank >= 7;
            case PIRATE -> rank >= 1;
            case REVOLUTIONARY -> rank >= 2;
            case BOUNTY_HUNTER -> rank >= 4;
            case CIVILIAN -> rank >= 3;
        };
    }

    /** Down one rank (a Seaman Recruit deserts instead). */
    public static void demote(ServerPlayer sp, CharacterData d, CareerTrack t, Component reason) {
        int from = rank(d);
        if (from == 0) {
            if (t == CareerTrack.MARINE) desert(sp, d, reason);
            return;
        }
        int to = from - 1;
        CareerWorldData w = CareerWorldData.get(sp.server);
        String oldSeat = seatFor(t, from), newSeat = seatFor(t, to);
        if (oldSeat != null) w.leave(oldSeat, d.characterId);
        if (newSeat != null) w.take(newSeat, d.characterId, sp.getUUID(), d.name, now(sp.server));
        CareerState c = d.career;
        d.factionRank = to;
        c.merit = Math.min(c.merit, t.rank(to).need());
        c.due = false;
        c.add("demotions", 1);
        Component title = rankTitle(d, t, to);
        tell(sp, Component.translatable("grandline.career.demoted", title, reason).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        if (big(t, from)) announce(sp.server, Component.translatable("grandline.career.announce.demoted", d.name, title).withStyle(ChatFormatting.RED));
        if (Squads.rankSize(t, to) == 0) Squads.dismiss(sp);
        changed(sp);
    }

    /** A Marine offence (attacking Marines or civilians, a crime): demerits, and a demotion for every ten. */
    public static void offence(ServerPlayer sp, CharacterData d, int points, Component what) {
        CareerTrack t = ensure(sp, d);
        if (t != CareerTrack.MARINE) return;
        CareerState c = d.career;
        c.demerits += points;
        tell(sp, Component.translatable("grandline.career.offence", what, c.demerits, DEMERITS_PER_DEMOTION).withStyle(ChatFormatting.RED));
        while (c.demerits >= DEMERITS_PER_DEMOTION && d.faction == Faction.MARINE) {
            c.demerits -= DEMERITS_PER_DEMOTION;
            demote(sp, d, t, what);
        }
        CharacterManager.markDirty(sp);
    }

    /** A Seaman Recruit's last offence: the deserter becomes a pirate with a bounty (raised through Bounties). */
    static void desert(ServerPlayer sp, CharacterData d, Component reason) {
        d.faction = Faction.PIRATE;
        tell(sp, Component.translatable("grandline.faction.deserter").withStyle(ChatFormatting.DARK_RED));
        long bounty = onDeserted(sp, d);
        com.grandline.npc.Bounties.raise(sp, d, bounty, Component.translatable("grandline.career.deserted_reason"));
    }

    /**
     * Bounties.raise branded a Marine a deserter (or {@link #desert} did): the seats are lost, the squad sent home, the
     * Marine colours struck from their ships, and the career starts over as a pirate. Returns the deserter's bounty.
     */
    public static long onDeserted(ServerPlayer sp, CharacterData d) {
        int was = d.career.track.equals(CareerTrack.MARINE.id()) ? d.factionRank : 0;
        Component oldTitle = rankTitle(d, CareerTrack.MARINE, was);
        d.faction = Faction.PIRATE;
        CareerWorldData.get(sp.server).release(d.characterId);
        Squads.dismiss(sp);
        CareerPerks.strikeMarineColours(sp);
        d.flags.add(DESERTER);
        long bounty = 5_000_000L * (was + 1);
        d.career.reset(CareerTrack.PIRATE.id());
        d.factionRank = 0;
        d.career.lastPaidDay = day(sp.server);
        announce(sp.server, Component.translatable("grandline.career.announce.deserter", d.name, oldTitle, n(d.bounty + bounty)).withStyle(ChatFormatting.DARK_RED));
        GrandLine.LOGGER.info("Career: {} deserted the Marines as {}", d.name, CareerTrack.MARINE.rank(was).id());
        changed(sp);
        return bounty;
    }

    /** Title, name tag and character sheet follow a change. */
    static void changed(ServerPlayer sp) {
        sp.refreshDisplayName();
        sp.refreshTabListName();
        CharacterManager.markVisualsDirty(sp);
    }

    // ================================================================== merit

    static void merit(ServerPlayer sp, CharacterData d, long pts) {
        if (pts <= 0) return;
        d.career.merit += pts;
        sp.displayClientMessage(Component.translatable("grandline.career.merit", pts, n(d.career.merit)).withStyle(ChatFormatting.AQUA), true);
    }

    static void deed(ServerPlayer sp, CharacterData d, Component what) {
        d.career.add("deeds", 1);
        sp.displayClientMessage(Component.translatable("grandline.career.deed", what, d.career.count("deeds")).withStyle(ChatFormatting.RED), true);
    }

    static void collect(ServerPlayer sp, CharacterData d, long beli) {
        if (beli <= 0) return;
        d.career.collected += beli;
        sp.displayClientMessage(Component.translatable("grandline.career.collected", n(beli), n(d.career.collected)).withStyle(ChatFormatting.GOLD), true);
    }

    // ================================================================== hooks (called from where things happen)

    /** QuestEvents.onKill: an NPC fell to the player (or their crew). */
    public static void onKill(ServerPlayer sp, GLNpc npc) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = ensure(sp, d);
        if (t == null) return;
        Faction f = npc.faction();
        boolean outlaw = f == Faction.PIRATE || f == Faction.WILD, law = Faction.isLaw(f);
        switch (t) {
            case MARINE -> {
                if (outlaw) merit(sp, d, 1 + npc.tier() / 2);
            }
            case PIRATE -> {
                if (d.career.warlord && f == Faction.PIRATE) Warlords.duty(sp, d, 1);
            }
            case REVOLUTIONARY -> {
                if (law) merit(sp, d, 2 + npc.tier());
                Missions.onKill(sp, d, npc);
            }
            case BOUNTY_HUNTER -> {
                if (outlaw) collect(sp, d, NpcOutcomes.rewardFor(npc) / 2);
            }
            case CIVILIAN -> {}
        }
        if (!npc.notableId().isEmpty()) onNotableDefeated(sp, npc.notableId(), npc.tier());
        check(sp);
        CharacterManager.markDirty(sp);
    }

    /** NpcOutcomes.npcArrested: an outlaw cuffed and taken in. Returns the licence bonus on the reward. */
    public static long onArrest(ServerPlayer sp, CharacterData d, GLNpc npc, long reward) {
        CareerTrack t = ensure(sp, d);
        if (t == null) return 0;
        boolean notable = !npc.notableId().isEmpty();
        long bonus = 0;
        CareerState c = d.career;
        switch (t) {
            case MARINE -> {
                c.add("arrests", 1);
                c.add("brought_in", reward);
                merit(sp, d, 4 + 2L * npc.tier() + (notable ? 10L * npc.tier() : 0) + reward / 20_000);
            }
            case BOUNTY_HUNTER -> {
                bonus = reward * rank(d) * 10 / 100;
                c.add("arrests", 1);
                if (notable) c.add("heads", 1);
                collect(sp, d, reward + bonus);
            }
            case PIRATE -> {
                if (c.warlord) Warlords.duty(sp, d, 1);
            }
            default -> {}
        }
        check(sp);
        return bonus;
    }

    /** A named character was beaten (killed, or yielded) by the player or their crew: deeds and Emperor-class. */
    public static void onNotableDefeated(ServerPlayer sp, String notableId, int tier) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = ensure(sp, d);
        if (t == null || d.career.count("beat:" + notableId) > 0) return;
        d.career.add("beat:" + notableId, 1);
        var n = NotableCharacters.get(notableId);
        NotableCharacters.Group g = n == null ? null : n.group();
        Faction nf = n == null ? null : n.faction();
        boolean emperorTier = g == NotableCharacters.Group.EMPEROR || tier >= 12;
        switch (t) {
            case MARINE -> {
                if (nf == Faction.PIRATE || nf == Faction.WILD || g == NotableCharacters.Group.VILLAIN || g == NotableCharacters.Group.WARLORD)
                    merit(sp, d, 8L * tier);
            }
            case PIRATE -> {
                deed(sp, d, n == null ? Component.literal(notableId) : NotableCharacters.name(n));
                if (emperorTier && !d.career.emperorDeed) {
                    d.career.emperorDeed = true;
                    tell(sp, Component.translatable("grandline.career.emperor_beaten", n == null ? Component.literal(notableId) : NotableCharacters.name(n))
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            case REVOLUTIONARY -> {
                if ((nf != null && Faction.isLaw(nf)) || g == NotableCharacters.Group.STEWARD || g == NotableCharacters.Group.WORLD_NOBLE) merit(sp, d, 8L * tier);
            }
            default -> {}
        }
        check(sp);
        CharacterManager.markDirty(sp);
    }

    /** For other features (boss fights): an Emperor-, Admiral- or Warlord-tier foe beaten by this player. */
    public static void onBossDefeated(ServerPlayer sp, String notableId, int tier) {
        onNotableDefeated(sp, notableId, tier);
    }

    /** LivingSea.defeated: an NPC ship the player fought was beaten or sunk. */
    public static void onShipDefeated(ServerPlayer sp, String shipFaction, int size) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = ensure(sp, d);
        if (t == null) return;
        CareerState c = d.career;
        switch (t) {
            case MARINE -> {
                if (shipFaction.equals("pirate")) {
                    c.add("sea_wins", 1);
                    merit(sp, d, 15L * size);
                }
            }
            case PIRATE -> {
                if (shipFaction.equals("marine")) deed(sp, d, Component.translatable("grandline.career.deed.marine_ship"));
                if (shipFaction.equals("pirate") && c.warlord) Warlords.duty(sp, d, 3);
            }
            case REVOLUTIONARY -> {
                if (shipFaction.equals("marine")) {
                    merit(sp, d, 10L * size);
                    Missions.onMarineShip(sp, d);
                }
            }
            case BOUNTY_HUNTER -> {
                if (shipFaction.equals("pirate")) collect(sp, d, 2000L * size);
            }
            default -> {}
        }
        check(sp);
        CharacterManager.markDirty(sp);
    }

    /** Quests.finish: story deeds, path quests, board jobs; the end of the war abolishes the Warlord charter. */
    public static void onQuestDone(ServerPlayer sp, CharacterData d, QuestDef q) {
        CareerTrack t = ensure(sp, d);
        if (t == null) return;
        String base = QuestRegistry.baseId(q.id()), vf = QuestRegistry.variantFaction(q.id());
        boolean story = QuestRegistry.storyQuestIds().contains(q.id());
        long pts = (story ? 25 : 0) + (vf != null && vf.equals(t.id()) ? 15 : 0) + ("board".equals(q.arc()) ? 5 : 0)
                + (base.equals("path_" + t.id()) ? 40 : 0);
        switch (t) {
            case MARINE, REVOLUTIONARY -> merit(sp, d, pts);
            case PIRATE -> {
                if (PART_ENDS.contains(base) || base.equals("path_pirate")) deed(sp, d, QuestRegistry.title(q));
                if (base.equals("war_3") && d.career.warlord) Warlords.abolish(sp, d);
            }
            default -> {}
        }
        check(sp);
    }

    /** QuestEvents.onTalk: a conversation (missions that rouse an island). */
    public static void onTalk(ServerPlayer sp, GLNpc npc) {
        CharacterData d = Characters.get(sp);
        if (ensure(sp, d) == CareerTrack.REVOLUTIONARY) Missions.onTalk(sp, d, npc);
    }

    /** QuestEvents.onDiscover: a new island charted (explorers sell the chart to the Gazette). */
    public static void onDiscover(ServerPlayer sp, String islandId) {
        CharacterData d = Characters.get(sp);
        if (ensure(sp, d) != CareerTrack.CIVILIAN) return;
        CareerPerks.chartSold(sp, d);
        check(sp);
    }

    /** QuestEvents.onPoneglyph: a new poneglyph read (scholars are paid for the rubbing). */
    public static void onPoneglyph(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        if (ensure(sp, d) != CareerTrack.CIVILIAN) return;
        CareerPerks.rubbingSold(sp, d);
        check(sp);
    }

    /** Shops.buy: Beli spent with a merchant (a civilian's trade). */
    public static void onTrade(ServerPlayer sp, long price) {
        CharacterData d = Characters.get(sp);
        if (ensure(sp, d) != CareerTrack.CIVILIAN) return;
        d.career.trade += price;
        check(sp);
    }

    /**
     * Bounties.raise, before the bounty grows: a Marine's crime earns demerits; anything that would raise a Warlord's
     * frozen bounty breaks the charter. Returns Beli to add to the raise (the revoked Warlord's restored bounty).
     */
    public static long onCrime(ServerPlayer sp, CharacterData d, long amount) {
        if (amount <= 0 || !d.created) return 0;
        CareerTrack t = ensure(sp, d);
        if (t == CareerTrack.MARINE) {
            offence(sp, d, 5 + (int) Math.min(15, amount / 1_000_000L), Component.translatable("grandline.career.offence.crime"));
            return 0;
        }
        if (t == CareerTrack.PIRATE && d.career.warlord) return Warlords.revoke(sp, d, Component.translatable("grandline.career.warlord.breach_crime"));
        return 0;
    }

    /** Bounties.raise, after: a pirate's infamy follows the bounty. */
    public static void onBountyRaised(ServerPlayer sp, CharacterData d) {
        check(sp);
    }

    // ================================================================== hostility (GLNpc.isHostileTo)

    /**
     * Career opinions on who an NPC fights (null: no opinion): squads and banner men stand by their leader; the law
     * leaves a Warlord and a revolutionary lying low alone; pirates respect a Supernova, and on their own islands any
     * pirate with a poster.
     */
    @Nullable
    public static Boolean hostility(GLNpc npc, LivingEntity e) {
        Boolean squad = Squads.hostility(npc, e);
        if (squad != null) return squad;
        if (!(e instanceof Player p) || npc.level().isClientSide) return null;
        CharacterData d = Characters.get(p);
        if (!d.created) return null;
        Faction f = npc.faction();
        if (Faction.isLaw(f)) {
            if (d.faction == Faction.PIRATE && d.career.warlord && d.career.track.equals(CareerTrack.PIRATE.id())) return false;
            if (d.faction == Faction.REVOLUTIONARY && d.career.safeUntil > npc.level().getGameTime()) return false;
            return null;
        }
        if (f == Faction.PIRATE && d.faction == Faction.PIRATE && npc.notableId().isEmpty() && !npc.getTags().contains(com.grandline.ship.LivingSea.CREW)
                && d.career.track.equals(CareerTrack.PIRATE.id())) {
            if (rank(d) >= 1 || d.career.warlord) return false;
            if (d.bounty >= 1_000_000L && CareerPerks.pirateIsland(npc)) return false;
        }
        return null;
    }

    /** NpcShipAi / NavalCombat: Marine ships leave a Warlord's ships alone (and a Warlord's guns spare them). */
    public static boolean protectedAtSea(@Nullable Player p) {
        if (p == null) return false;
        CharacterData d = Characters.get(p);
        return d.created && d.faction == Faction.PIRATE && d.career.warlord && d.career.track.equals(CareerTrack.PIRATE.id());
    }

    // ================================================================== visuals and names

    /** CharacterManager.visuals: the title travels with the other visuals (name tags on clients). */
    public static void visuals(Player p, CharacterData d, CompoundTag t) {
        MutableComponent title = CareerTrack.title(d);
        if (title != null) t.putString("careerTitle", Component.Serializer.toJson(title, p.registryAccess()));
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MutableComponent title = CareerTrack.title(Characters.get(sp));
        if (title != null) e.setDisplayname(CareerTrack.decorate(title, e.getDisplayname()));
    }

    @SubscribeEvent
    public static void onTabName(PlayerEvent.TabListNameFormat e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        MutableComponent title = CareerTrack.title(Characters.get(sp));
        if (title != null) e.setDisplayName(CareerTrack.decorate(title, e.getDisplayName() != null ? e.getDisplayName() : sp.getName()));
    }

    // ================================================================== events

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        CharacterData d = Characters.get(sp);
        if (ensure(sp, d) != null) {
            CareerPerks.payDay(sp, d);
            Warlords.tick(sp, d);
            check(sp);
        }
        changed(sp);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        if (s.getTickCount() % 20 != 7) return;
        Squads.tick(s);
        if (s.getTickCount() % 200 != 7) return;
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) {
            CharacterData d = Characters.get(sp);
            if (ensure(sp, d) == null) continue;
            CareerPerks.payDay(sp, d);
            Warlords.tick(sp, d);
        }
        Warlords.envoys(s);
    }

    /** Unprovoked attacks: a Marine striking Marines or civilians earns demerits; a Warlord striking the law loses the letter. */
    private static final Map<UUID, Long> STRUCK = new HashMap<>();

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof GLNpc npc) || npc.level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer sp)) return;
        CharacterData d = Characters.get(sp);
        if (!d.created || npc.flag(GLNpc.FLAG_SURRENDER) || npc.flag(GLNpc.FLAG_ARRESTED) || Squads.isMember(npc)) return;
        Faction f = npc.faction();
        boolean law = Faction.isLaw(f), civ = f == Faction.CIVILIAN;
        boolean marine = d.faction == Faction.MARINE, warlord = d.faction == Faction.PIRATE && d.career.warlord;
        if (!(marine && (law || civ)) && !(warlord && law)) return;
        if (npc.getTarget() == sp || npc.getLastHurtByMob() == sp) return; // already fighting: only the first blow is the offence
        long now = npc.level().getGameTime();
        Long last = STRUCK.get(npc.getUUID());
        if (last != null && now - last < 600) return;
        STRUCK.put(npc.getUUID(), now);
        if (STRUCK.size() > 512) STRUCK.values().removeIf(v -> now - v > 600);
        if (marine) offence(sp, d, law ? 4 : 3, Component.translatable(law ? "grandline.career.offence.marine" : "grandline.career.offence.civilian"));
        else {
            long penalty = Warlords.revoke(sp, d, Component.translatable("grandline.career.warlord.breach_attack"));
            if (penalty > 0) com.grandline.npc.Bounties.raise(sp, d, penalty, Component.translatable("grandline.career.warlord.revoked_reason"));
        }
    }

    /** A notable who yields (NotableHooks cancels the killing blow and sets the surrender flag) counts as beaten. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onYield(LivingIncomingDamageEvent e) {
        if (!e.isCanceled() || !(e.getEntity() instanceof GLNpc npc) || npc.level().isClientSide || npc.notableId().isEmpty() || !npc.flag(GLNpc.FLAG_SURRENDER)) return;
        Entity by = e.getSource().getEntity();
        ServerPlayer sp = by instanceof ServerPlayer p ? p : by instanceof GLNpc g ? CrewManager.captainOf(g) : null;
        if (sp != null && Characters.get(sp).created) onNotableDefeated(sp, npc.notableId(), npc.tier());
    }

    /** Permadeath: the dead character's seats and territories are free again (the new life starts its own career). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClone(PlayerEvent.Clone e) {
        if (!e.isWasDeath() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        CharacterData old = Characters.get(e.getOriginal());
        if (!old.created) return;
        CareerWorldData.get(sp.server).release(old.characterId);
        Squads.dismiss(e.getOriginal().getUUID(), sp.server);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Squads.dismiss(sp);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        STRUCK.clear();
        Squads.clear();
        Warlords.clear();
    }

    // ================================================================== status (/career)

    /** The lines of /career: title, progress, next rank and what it still needs, perks. */
    public static List<Component> status(ServerPlayer sp) {
        CharacterData d = Characters.get(sp);
        CareerTrack t = ensure(sp, d);
        List<Component> out = new ArrayList<>();
        if (t == null) {
            out.add(Component.translatable("grandline.career.none"));
            return out;
        }
        int r = rank(d);
        out.add(Component.translatable("grandline.career.status.title", Component.translatable("faction.grandline." + t.id()), title(d)).withStyle(ChatFormatting.GOLD));
        CareerState c = d.career;
        out.add(Component.translatable("grandline.career.status." + t.id(), n(t.progress(d)), c.count("arrests"), c.count("sea_wins"), c.count("deeds"),
                c.count("missions"), c.count("heads"), c.demerits, CareerTrack.specPoints(d, "explorer"), CareerTrack.specPoints(d, "scholar"),
                CareerTrack.specPoints(d, "merchant")).withStyle(ChatFormatting.GRAY));
        if (r < t.top()) {
            out.add(Component.translatable("grandline.career.status.next", rankTitle(d, t, r + 1)).withStyle(ChatFormatting.YELLOW));
            List<Component> miss = missing(sp, d, t, r + 1);
            if (miss.isEmpty()) out.add(Component.translatable(t.talk ? "grandline.career.status.report_" + t.id() : "grandline.career.status.ready").withStyle(ChatFormatting.GREEN));
            for (Component m : miss) out.add(Component.literal(" - ").append(m).withStyle(ChatFormatting.GRAY));
        } else out.add(Component.translatable("grandline.career.status.top").withStyle(ChatFormatting.GREEN));
        out.addAll(CareerPerks.perkLines(sp, d, t));
        return out;
    }

    /** Journey.earned: the career milestones the saved data already shows (ranks reached, the Warlord letter). */
    public static Set<String> earnedMilestones(CharacterData d) {
        Set<String> out = new LinkedHashSet<>();
        CareerTrack t = CareerTrack.of(d.faction);
        if (!d.created || t == null || !t.id().equals(d.career.track)) return out;
        int best = Math.max(d.career.best, rank(d));
        for (int i = 1; i <= best && i <= t.top(); i++) {
            String m = MILESTONES.get(t.id() + ":" + t.rank(i).id());
            if (m != null) out.add(m);
        }
        if (d.career.warlord || d.flags.contains(Warlords.REVOKED)) out.add(MILESTONES.get("pirate:warlord"));
        return out;
    }

    /** A rank-issued item (the coat, the jitte, the rank sword) with the character's name on it. */
    static ItemStack issued(ItemStack st, CharacterData d, String nameKey) {
        if (nameKey != null) st.set(DataComponents.CUSTOM_NAME, Component.translatable(nameKey, d.name).withStyle(ChatFormatting.AQUA));
        return st;
    }

    private Careers() {}
}
