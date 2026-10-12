package com.longnight.sim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A realm: kingdom, clan, republic, council or order. Settlements belong to at most one faction. */
public class Faction {
    public enum Government {
        MONARCHY("Kingdom", "King", "Queen", "hereditary"),
        COUNCIL("Council", "High Councillor", "High Councillor", "elected by the council of houses"),
        CLAN("Clanhold", "Chieftain", "Chieftain", "the strongest of the clan"),
        REPUBLIC("Republic", "Doge", "Doge", "the wealthiest merchant"),
        THEOCRACY("Holy Order", "High Priest", "High Priestess", "the most devout"),
        LORDSHIP("Lordship", "Lord", "Lady", "hereditary"),
        OUTLAWS("Band", "Captain", "Captain", "whoever is feared most");

        public final String realmWord;
        public final String titleMale;
        public final String titleFemale;
        public final String succession;

        Government(String realmWord, String titleMale, String titleFemale, String succession) {
            this.realmWord = realmWord;
            this.titleMale = titleMale;
            this.titleFemale = titleFemale;
            this.succession = succession;
        }
    }

    public enum Stance { PEACE, TRADE, ALLIANCE, WAR, TRIBUTARY, OVERLORD }

    /** What the current ruler wants, derived from temperament and circumstance. */
    public enum Agenda { CONSOLIDATE, FORTIFY, EXPAND, CONQUER, TRADE, TAX, PIETY, RECOVER }

    public UUID id;
    public String name = "";
    public String culture = "valemen";
    public Government government = Government.MONARCHY;
    public UUID ruler;
    public UUID capital;
    public UUID heir;
    public long foundedDay;
    public int treasury;
    public double taxRate = 0.1;
    public Agenda agenda = Agenda.CONSOLIDATE;
    public boolean playerRealm;
    public UUID playerRuler;
    public boolean destroyed;
    public int bannerColor;      // DyeColor id
    public int bannerAccent;     // DyeColor id
    public int warWeariness;
    public final Map<UUID, Integer> relations = new HashMap<>();   // faction -> -100..100
    public final Map<UUID, Stance> stances = new HashMap<>();
    public final Map<UUID, Integer> playerRep = new HashMap<>();
    public final List<String> chronicle = new ArrayList<>();
    public final List<UUID> pastRulers = new ArrayList<>();

    public Faction(UUID id) {
        this.id = id;
    }

    public Stance stance(UUID other) {
        return stances.getOrDefault(other, Stance.PEACE);
    }

    public int relation(UUID other) {
        return relations.getOrDefault(other, 0);
    }

    public void changeRelation(UUID other, int delta) {
        relations.put(other, Math.max(-100, Math.min(100, relation(other) + delta)));
    }

    public boolean atWarWith(UUID other) {
        return stance(other) == Stance.WAR;
    }

    public String rulerTitle(Person p) {
        if (p == null) return government.titleMale;
        return p.sex == Person.Sex.FEMALE ? government.titleFemale : government.titleMale;
    }

    public int playerReputation(UUID player) {
        return playerRep.getOrDefault(player, 0);
    }

    public void changePlayerRep(UUID player, int delta) {
        playerRep.put(player, Math.max(-100, Math.min(100, playerReputation(player) + delta)));
    }

    public void chronicle(long day, String text) {
        chronicle.add("Day " + day + ": " + text);
        if (chronicle.size() > 80) chronicle.remove(0);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("culture", culture);
        t.putString("gov", government.name());
        Nbt.putUUID(t, "ruler", ruler);
        Nbt.putUUID(t, "capital", capital);
        Nbt.putUUID(t, "heir", heir);
        t.putLong("founded", foundedDay);
        t.putInt("treasury", treasury);
        t.putDouble("tax", taxRate);
        t.putString("agenda", agenda.name());
        t.putBoolean("playerRealm", playerRealm);
        Nbt.putUUID(t, "playerRuler", playerRuler);
        t.putBoolean("destroyed", destroyed);
        t.putInt("banner", bannerColor);
        t.putInt("accent", bannerAccent);
        t.putInt("weary", warWeariness);
        ListTag rel = new ListTag();
        relations.forEach((k, v) -> {
            CompoundTag r = new CompoundTag();
            r.putUUID("f", k);
            r.putInt("v", v);
            r.putString("s", stance(k).name());
            rel.add(r);
        });
        stances.forEach((k, v) -> {
            if (!relations.containsKey(k)) {
                CompoundTag r = new CompoundTag();
                r.putUUID("f", k);
                r.putInt("v", 0);
                r.putString("s", v.name());
                rel.add(r);
            }
        });
        t.put("rel", rel);
        ListTag rep = new ListTag();
        playerRep.forEach((k, v) -> {
            CompoundTag r = new CompoundTag();
            r.putUUID("p", k);
            r.putInt("v", v);
            rep.add(r);
        });
        t.put("rep", rep);
        t.put("chronicle", Nbt.stringList(chronicle));
        t.put("pastRulers", Nbt.uuidList(pastRulers));
        return t;
    }

    public static Faction load(CompoundTag t) {
        Faction f = new Faction(t.getUUID("id"));
        f.name = t.getString("name");
        f.culture = t.getString("culture");
        f.government = Government.valueOf(t.getString("gov"));
        f.ruler = Nbt.getUUID(t, "ruler");
        f.capital = Nbt.getUUID(t, "capital");
        f.heir = Nbt.getUUID(t, "heir");
        f.foundedDay = t.getLong("founded");
        f.treasury = t.getInt("treasury");
        f.taxRate = t.getDouble("tax");
        f.agenda = Agenda.valueOf(t.getString("agenda"));
        f.playerRealm = t.getBoolean("playerRealm");
        f.playerRuler = Nbt.getUUID(t, "playerRuler");
        f.destroyed = t.getBoolean("destroyed");
        f.bannerColor = t.getInt("banner");
        f.bannerAccent = t.getInt("accent");
        f.warWeariness = t.getInt("weary");
        for (Tag x : t.getList("rel", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) x;
            UUID k = r.getUUID("f");
            f.relations.put(k, r.getInt("v"));
            Stance s = Stance.valueOf(r.getString("s"));
            if (s != Stance.PEACE) f.stances.put(k, s);
        }
        for (Tag x : t.getList("rep", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) x;
            f.playerRep.put(r.getUUID("p"), r.getInt("v"));
        }
        f.chronicle.addAll(Nbt.readStringList(t, "chronicle"));
        f.pastRulers.addAll(Nbt.readUuidList(t, "pastRulers"));
        return f;
    }
}
