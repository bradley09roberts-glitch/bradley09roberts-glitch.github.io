package com.grandline.career;

import com.grandline.player.CharacterData;
import com.grandline.player.Faction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The five careers, one per starting path, and their rank ladders. Pure data and title logic over {@link CharacterData}
 * (the client's character sheet uses it too); the server side lives in {@link Careers}.
 * <p>
 * Each rank needs a measure of progress ({@link #progress}) and a level; the stronger gates (story Parts, Haki, seats,
 * sea victories, missions, notable heads) are checked by {@link Careers#gates}. Marines and Revolutionaries are promoted
 * in person ({@link #talk}): once qualified they report to an officer; the other careers rise on their own.
 */
public enum CareerTrack {
    /** Merit points: arrests, pirate bounties brought in, sea battles won against pirates, story deeds. */
    MARINE(Faction.MARINE, ChatFormatting.AQUA, true, List.of(
            r("seaman_recruit", 0, 1), r("seaman", 30, 1), r("petty_officer", 80, 3), r("chief_petty_officer", 160, 6),
            r("ensign", 280, 10), r("lieutenant", 450, 15), r("commander", 700, 20), r("captain", 1_000, 25),
            r("commodore", 1_400, 30), r("rear_admiral", 1_900, 40), r("vice_admiral", 2_600, 50), r("admiral", 3_600, 65),
            r("fleet_admiral", 5_000, 80))),
    /** Infamy follows the bounty (Beli); the deeds and the Paradise/Emperor conditions are gates. */
    PIRATE(Faction.PIRATE, ChatFormatting.RED, false, List.of(
            r("rookie", 0, 1), r("supernova", 100_000_000L, 1), r("emperor", 1_000_000_000L, 50))),
    /** Merit points: missions for the cell (liberation, sabotage, incitement), blows against the Government, story deeds. */
    REVOLUTIONARY(Faction.REVOLUTIONARY, ChatFormatting.GREEN, true, List.of(
            r("cell_member", 0, 1), r("officer", 250, 15), r("commander", 900, 30), r("chief_of_staff", 2_500, 55))),
    /** Licence tiers by the Beli of the bounties collected. */
    BOUNTY_HUNTER(Faction.BOUNTY_HUNTER, ChatFormatting.GOLD, false, List.of(
            r("apprentice", 0, 1), r("licensed", 20_000, 1), r("bronze", 150_000, 12), r("silver", 600_000, 25),
            r("gold", 2_500_000, 40), r("black", 10_000_000, 60))),
    /** Points in the best of three trades: islands charted (explorer), poneglyphs read (scholar), Beli traded (merchant). */
    CIVILIAN(Faction.CIVILIAN, ChatFormatting.WHITE, false, List.of(
            r("townsfolk", 0, 1), r("apprentice", 50, 1), r("journeyman", 150, 8), r("master", 400, 20), r("grand", 1_000, 35)));

    public record Rank(String id, long need, int level) {}

    private static Rank r(String id, long need, int level) {
        return new Rank(id, need, level);
    }

    public final Faction faction;
    public final ChatFormatting color;
    /** Promotions need a talk with an officer of the career (Marines, Revolutionaries). */
    public final boolean talk;
    public final List<Rank> ranks;

    CareerTrack(Faction faction, ChatFormatting color, boolean talk, List<Rank> ranks) {
        this.faction = faction;
        this.color = color;
        this.talk = talk;
        this.ranks = ranks;
    }

    public String id() {
        return faction.id();
    }

    @Nullable
    public static CareerTrack of(Faction f) {
        for (CareerTrack t : values()) if (t.faction == f) return t;
        return null;
    }

    @Nullable
    public static CareerTrack byId(String id) {
        for (CareerTrack t : values()) if (t.id().equals(id)) return t;
        return null;
    }

    public int top() {
        return ranks.size() - 1;
    }

    public Rank rank(int i) {
        return ranks.get(Math.max(0, Math.min(top(), i)));
    }

    public int indexOf(String rankId) {
        for (int i = 0; i < ranks.size(); i++) if (ranks.get(i).id.equals(rankId)) return i;
        return -1;
    }

    /** "career.grandline.marine.vice_admiral" */
    public String rankKey(int i) {
        return "career.grandline." + id() + "." + rank(i).id;
    }

    // ================================================================== progress

    /** The measure ranks are compared against (merit, bounty, Beli collected or trade points). */
    public long progress(CharacterData d) {
        CareerState c = d.career;
        return switch (this) {
            case MARINE, REVOLUTIONARY -> c.merit;
            case PIRATE -> d.bounty;
            case BOUNTY_HUNTER -> c.collected;
            case CIVILIAN -> specPoints(d, specialty(d));
        };
    }

    public static final List<String> SPECIALTIES = List.of("explorer", "scholar", "merchant");

    /** Civilian points in one trade: 10 per island charted beyond home, 40 per poneglyph read, 1 per 500 Beli traded. */
    public static long specPoints(CharacterData d, String spec) {
        return switch (spec) {
            case "explorer" -> Math.max(0, d.discoveredIslands.size() - 1) * 10L;
            case "scholar" -> d.flags.stream().filter(f -> f.startsWith("poneglyph_")).count() * 40L;
            case "merchant" -> d.career.trade / 500L;
            default -> 0;
        };
    }

    /** The civilian's strongest trade (ties: explorer, scholar, merchant). */
    public static String specialty(CharacterData d) {
        String best = SPECIALTIES.get(0);
        long bp = -1;
        for (String s : SPECIALTIES) {
            long p = specPoints(d, s);
            if (p > bp) {
                bp = p;
                best = s;
            }
        }
        return best;
    }

    // ================================================================== titles

    /** The character's rank in its current career (0 until the career has started). */
    public static int rankOf(CharacterData d) {
        CareerTrack t = of(d.faction);
        return t != null && t.id().equals(d.career.track) ? Math.max(0, Math.min(t.top(), d.factionRank)) : 0;
    }

    /** The title shown before the name (chat, tab list, name tag) and on the character sheet; null without a career. */
    @Nullable
    public static MutableComponent title(CharacterData d) {
        if (!d.created) return null;
        CareerTrack t = of(d.faction);
        if (t == null) return null;
        int rank = rankOf(d);
        CareerState c = d.career;
        MutableComponent out;
        ChatFormatting color = t.color;
        if (t == PIRATE && c.warlord) {
            out = Component.translatable("career.grandline.pirate.warlord");
            color = ChatFormatting.DARK_PURPLE;
        } else if (t == PIRATE && rank == PIRATE.top()) {
            out = c.territoryName.isEmpty() ? Component.translatable(t.rankKey(rank))
                    : Component.translatable("career.grandline.pirate.emperor_of", territoryComponent(c.territoryName));
            color = ChatFormatting.GOLD;
        } else if (t == PIRATE && rank == 0 && d.flags.contains(Careers.DESERTER)) {
            out = Component.translatable("career.grandline.pirate.deserter");
            color = ChatFormatting.DARK_RED;
        } else if (t == CIVILIAN && rank > 0) {
            out = Component.translatable(t.rankKey(rank), Component.translatable("career.grandline.civilian.spec." + specialty(d)));
        } else out = Component.translatable(t.rankKey(rank));
        return out.withStyle(color);
    }

    /** A stored island label: "#key" is a translation key (canon islands), anything else the island's own name. */
    public static Component territoryComponent(String label) {
        return label.startsWith("#") ? Component.translatable(label.substring(1)) : Component.literal(label);
    }

    /** "[title] name" for chat and the tab list. */
    public static Component decorate(Component title, Component name) {
        return Component.empty().append(title).append(Component.literal(" ")).append(name);
    }
}
