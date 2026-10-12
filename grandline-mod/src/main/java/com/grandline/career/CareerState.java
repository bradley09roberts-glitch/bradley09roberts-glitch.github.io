package com.grandline.career;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * A character's career, saved inside {@link com.grandline.player.CharacterData} (so it belongs to the character: a
 * permadeath archives it with everything else and the next life starts again). The rank itself is the character's
 * {@code factionRank}; {@link #track} says which career that rank belongs to (a deserter starts over as a pirate).
 */
public final class CareerState {
    /** The career this state belongs to (a faction id), "" before it starts. */
    public String track = "";
    /** Marine / Revolutionary merit points. */
    public long merit;
    /** Marine offences since the last demotion (attacking Marines or civilians, crimes). */
    public int demerits;
    /** Bounty hunter: Beli of the bounties collected. */
    public long collected;
    /** Civilian merchant: Beli traded in shops. */
    public long trade;
    /** The in-game day of the last rank pay ({@link #NEVER}: not yet started). */
    public long lastPaidDay = NEVER;
    public static final long NEVER = Long.MIN_VALUE;
    /** The highest rank ever held in this career. */
    public int best;
    /** Tallies: arrests, brought_in, sea_wins, deeds, missions, freed, heads, demotions... */
    public final Map<String, Long> counts = new TreeMap<>();
    /** A promotion is due and has been announced to the player (talk careers). */
    public boolean due;

    // ---- pirates
    public boolean warlord;
    public long warlordSince, dutyDue;
    public int dutyDone;
    /** Beat an Emperor-tier foe. */
    public boolean emperorDeed;
    /** Islands held (ids) and the label of the first one ("#translation.key" or a literal name) for the title. */
    public final List<String> territories = new ArrayList<>();
    public String territoryName = "";

    // ---- revolutionaries
    public String mission = "";
    public int missionProgress;
    public final List<String> missionSeen = new ArrayList<>();

    // ---- cooldowns (overworld game time)
    public long squadReady, shipReady, busterReady, envoyReady, safeUntil;

    public long count(String k) {
        return counts.getOrDefault(k, 0L);
    }

    public void add(String k, long n) {
        counts.merge(k, n, Long::sum);
    }

    /** Start (again) in a career: everything but the identity of the character is cleared. */
    public void reset(String newTrack) {
        CareerState fresh = new CareerState();
        load(fresh.save());
        track = newTrack;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("track", track);
        t.putLong("merit", merit);
        t.putInt("demerits", demerits);
        t.putLong("collected", collected);
        t.putLong("trade", trade);
        t.putLong("paidDay", lastPaidDay);
        t.putInt("best", best);
        CompoundTag c = new CompoundTag();
        counts.forEach(c::putLong);
        t.put("counts", c);
        t.putBoolean("due", due);
        t.putBoolean("warlord", warlord);
        t.putLong("warlordSince", warlordSince);
        t.putLong("dutyDue", dutyDue);
        t.putInt("dutyDone", dutyDone);
        t.putBoolean("emperorDeed", emperorDeed);
        t.put("territories", strings(territories));
        t.putString("territoryName", territoryName);
        t.putString("mission", mission);
        t.putInt("missionProgress", missionProgress);
        t.put("missionSeen", strings(missionSeen));
        t.putLong("squadReady", squadReady);
        t.putLong("shipReady", shipReady);
        t.putLong("busterReady", busterReady);
        t.putLong("envoyReady", envoyReady);
        t.putLong("safeUntil", safeUntil);
        return t;
    }

    public void load(CompoundTag t) {
        track = t.getString("track");
        merit = t.getLong("merit");
        demerits = t.getInt("demerits");
        collected = t.getLong("collected");
        trade = t.getLong("trade");
        lastPaidDay = t.contains("paidDay") ? t.getLong("paidDay") : NEVER;
        best = t.getInt("best");
        counts.clear();
        CompoundTag c = t.getCompound("counts");
        for (String k : c.getAllKeys()) counts.put(k, c.getLong(k));
        due = t.getBoolean("due");
        warlord = t.getBoolean("warlord");
        warlordSince = t.getLong("warlordSince");
        dutyDue = t.getLong("dutyDue");
        dutyDone = t.getInt("dutyDone");
        emperorDeed = t.getBoolean("emperorDeed");
        territories.clear();
        read(t, "territories", territories);
        territoryName = t.getString("territoryName");
        mission = t.getString("mission");
        missionProgress = t.getInt("missionProgress");
        missionSeen.clear();
        read(t, "missionSeen", missionSeen);
        squadReady = t.getLong("squadReady");
        shipReady = t.getLong("shipReady");
        busterReady = t.getLong("busterReady");
        envoyReady = t.getLong("envoyReady");
        safeUntil = t.getLong("safeUntil");
    }

    private static ListTag strings(Collection<String> c) {
        ListTag l = new ListTag();
        for (String s : c) l.add(StringTag.valueOf(s));
        return l;
    }

    private static void read(CompoundTag t, String key, Collection<String> out) {
        ListTag l = t.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
    }
}
