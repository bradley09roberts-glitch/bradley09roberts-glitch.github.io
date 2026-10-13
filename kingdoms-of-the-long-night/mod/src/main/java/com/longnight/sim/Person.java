package com.longnight.sim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A persistent individual. The Person record is the authoritative identity; a CitizenEntity is
 * only a temporary body used when a player is nearby. Dead people stay in the registry (for
 * graves, family trees and history) but are never given a body again.
 */
public class Person {
    public enum Sex { MALE, FEMALE }

    public enum Orientation { STRAIGHT, GAY, BI }

    public static final int MAX_MEMORIES = 24;
    public static final int MAX_RELATIONS = 32;

    public UUID id;
    public String given = "";
    public String family = "";
    public String epithet = "";
    public Sex sex = Sex.MALE;
    public Orientation orientation = Orientation.STRAIGHT;
    public String culture = "valemen";
    public String faith = "hearth";
    public long birthDay;
    public long deathDay = Long.MIN_VALUE;
    public String deathCause = "";
    public boolean alive = true;
    public boolean buried = false;

    public UUID settlement;      // residence (null = wanderer / outlaw band)
    public UUID home;            // building id
    public UUID workplace;       // building id
    public Profession profession = Profession.LABOURER;
    public final EnumMap<Profession, Float> skills = new EnumMap<>(Profession.class);
    public UUID mentor;          // master when apprenticed
    public String title = "";    // e.g. "Lord of Ashford"
    public int prestige;

    // temperament, each 0..1
    public float bravery, diligence, sociability, ambition, piety, kindness, greed;

    // needs, each 0..1 (1 = fully satisfied)
    public float food = 1f, rest = 1f, safety = 1f, social = 0.7f;
    public float health = 1f;
    public int illnessDays;
    public float loyalty = 0.7f;

    // family
    public UUID mother, father, spouse;
    public final List<UUID> children = new ArrayList<>();
    public UUID courting;
    public int courtDays;

    // possessions (abstract)
    public int coins;
    public int weaponTier;  // 0 none, 1 basic, 2 good
    public int armorTier;   // 0 none, 1 padded, 2 mail
    public boolean hasTools;

    public final Map<UUID, Relation> relations = new HashMap<>();
    public final List<Memory> memories = new ArrayList<>();

    // what the person is doing / why they are stuck (shown to players)
    public String activity = "";
    /** a standing order from their lord: "" (their own trade), "build" (help the builders), "fell" (clear trees) */
    public String order = "";
    public String blocked = "";

    public static final class Relation {
        public int opinion;      // -100..100
        public int familiarity;  // 0..100

        public Relation() {}

        public Relation(int opinion, int familiarity) {
            this.opinion = opinion;
            this.familiarity = familiarity;
        }
    }

    public record Memory(long day, String kind, UUID other, String text, int weight) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("d", day);
            t.putString("k", kind);
            if (other != null) t.putUUID("o", other);
            t.putString("t", text);
            t.putInt("w", weight);
            return t;
        }

        static Memory load(CompoundTag t) {
            return new Memory(t.getLong("d"), t.getString("k"), t.hasUUID("o") ? t.getUUID("o") : null,
                    t.getString("t"), t.getInt("w"));
        }
    }

    public Person(UUID id) {
        this.id = id;
    }

    // ------------------------------------------------------------------ derived values

    public String fullName() {
        String base = family.isEmpty() ? given : given + " " + family;
        return epithet.isEmpty() ? base : base + " " + epithet;
    }

    public String shortName() {
        return given;
    }

    public double ageYears(long today, double daysPerYear) {
        return (today - birthDay) / daysPerYear;
    }

    public float skill(Profession p) {
        return skills.getOrDefault(p, 0f);
    }

    public void addSkill(Profession p, float amount) {
        skills.put(p, Math.min(100f, skill(p) + amount));
    }

    /** -1 (miserable) .. 1 (content) */
    public float mood() {
        float m = (food - 0.5f) * 0.8f + (rest - 0.5f) * 0.4f + (safety - 0.5f) * 0.6f + (social - 0.5f) * 0.4f
                + (health - 0.7f) * 0.6f;
        int recent = 0;
        for (Memory mem : memories) recent += mem.weight;
        m += Math.max(-0.4f, Math.min(0.4f, recent / 200f));
        return Math.max(-1f, Math.min(1f, m));
    }

    public String moodWord() {
        float m = mood();
        if (m > 0.45f) return "content";
        if (m > 0.15f) return "at ease";
        if (m > -0.15f) return "unsettled";
        if (m > -0.45f) return "unhappy";
        return "despairing";
    }

    public Relation relation(UUID other) {
        return relations.get(other);
    }

    public int opinionOf(UUID other) {
        Relation r = relations.get(other);
        return r == null ? 0 : r.opinion;
    }

    public Relation relate(UUID other) {
        Relation r = relations.get(other);
        if (r == null) {
            if (relations.size() >= MAX_RELATIONS) {
                // forget the least familiar acquaintance who is not family
                UUID weakest = null;
                int weakestFam = Integer.MAX_VALUE;
                for (Map.Entry<UUID, Relation> e : relations.entrySet()) {
                    UUID k = e.getKey();
                    if (k.equals(spouse) || k.equals(mother) || k.equals(father) || children.contains(k)) continue;
                    if (e.getValue().familiarity < weakestFam) {
                        weakestFam = e.getValue().familiarity;
                        weakest = k;
                    }
                }
                if (weakest != null) relations.remove(weakest);
            }
            r = new Relation();
            relations.put(other, r);
        }
        return r;
    }

    public void changeOpinion(UUID other, int delta) {
        if (other == null || other.equals(id)) return;
        Relation r = relate(other);
        r.opinion = Math.max(-100, Math.min(100, r.opinion + delta));
    }

    public void remember(long day, String kind, UUID other, String text, int weight) {
        memories.add(new Memory(day, kind, other, text, weight));
        if (memories.size() > MAX_MEMORIES) {
            // forget the weakest old memory, but keep the most emotionally significant ones
            int idx = 0;
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < memories.size() - 4; i++) {
                int w = Math.abs(memories.get(i).weight) * 4 - (int) Math.min(40, (day - memories.get(i).day));
                if (w < best) {
                    best = w;
                    idx = i;
                }
            }
            memories.remove(idx);
        }
    }

    public boolean isAdult(long today, double daysPerYear, int adultAge) {
        return ageYears(today, daysPerYear) >= adultAge;
    }

    public boolean attractedTo(Person other) {
        if (other == null) return false;
        boolean same = other.sex == sex;
        return switch (orientation) {
            case STRAIGHT -> !same;
            case GAY -> same;
            case BI -> true;
        };
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("given", given);
        t.putString("family", family);
        t.putString("epithet", epithet);
        t.putString("sex", sex.name());
        t.putString("orient", orientation.name());
        t.putString("culture", culture);
        t.putString("faith", faith);
        t.putLong("born", birthDay);
        t.putLong("died", deathDay);
        t.putString("cause", deathCause);
        t.putBoolean("alive", alive);
        t.putBoolean("buried", buried);
        Nbt.putUUID(t, "settlement", settlement);
        Nbt.putUUID(t, "home", home);
        Nbt.putUUID(t, "work", workplace);
        t.putString("prof", profession.id());
        CompoundTag sk = new CompoundTag();
        skills.forEach((k, v) -> sk.putFloat(k.id(), v));
        t.put("skills", sk);
        Nbt.putUUID(t, "mentor", mentor);
        t.putString("title", title);
        t.putInt("prestige", prestige);
        t.putFloat("brave", bravery);
        t.putFloat("dili", diligence);
        t.putFloat("soci", sociability);
        t.putFloat("ambi", ambition);
        t.putFloat("piety", piety);
        t.putFloat("kind", kindness);
        t.putFloat("greed", greed);
        t.putFloat("food", food);
        t.putFloat("rest", rest);
        t.putFloat("safety", safety);
        t.putFloat("social", social);
        t.putFloat("health", health);
        t.putInt("ill", illnessDays);
        t.putFloat("loyal", loyalty);
        Nbt.putUUID(t, "mother", mother);
        Nbt.putUUID(t, "father", father);
        Nbt.putUUID(t, "spouse", spouse);
        t.put("children", Nbt.uuidList(children));
        Nbt.putUUID(t, "courting", courting);
        t.putInt("courtDays", courtDays);
        t.putInt("coins", coins);
        t.putInt("wpn", weaponTier);
        t.putInt("arm", armorTier);
        t.putBoolean("tools", hasTools);
        ListTag rel = new ListTag();
        relations.forEach((k, v) -> {
            CompoundTag r = new CompoundTag();
            r.putUUID("p", k);
            r.putInt("o", v.opinion);
            r.putInt("f", v.familiarity);
            rel.add(r);
        });
        t.put("rel", rel);
        ListTag mem = new ListTag();
        for (Memory m : memories) mem.add(m.save());
        t.put("mem", mem);
        t.putString("act", activity);
        t.putString("blocked", blocked);
        t.putString("order", order);
        return t;
    }

    public static Person load(CompoundTag t) {
        Person p = new Person(t.getUUID("id"));
        p.given = t.getString("given");
        p.family = t.getString("family");
        p.epithet = t.getString("epithet");
        p.sex = Sex.valueOf(t.getString("sex"));
        p.orientation = t.contains("orient") ? Orientation.valueOf(t.getString("orient")) : Orientation.STRAIGHT;
        p.culture = t.getString("culture");
        p.faith = t.getString("faith");
        p.birthDay = t.getLong("born");
        p.deathDay = t.getLong("died");
        p.deathCause = t.getString("cause");
        p.alive = t.getBoolean("alive");
        p.buried = t.getBoolean("buried");
        p.settlement = Nbt.getUUID(t, "settlement");
        p.home = Nbt.getUUID(t, "home");
        p.workplace = Nbt.getUUID(t, "work");
        p.profession = Profession.byId(t.getString("prof"));
        CompoundTag sk = t.getCompound("skills");
        for (String k : sk.getAllKeys()) p.skills.put(Profession.byId(k), sk.getFloat(k));
        p.mentor = Nbt.getUUID(t, "mentor");
        p.title = t.getString("title");
        p.prestige = t.getInt("prestige");
        p.bravery = t.getFloat("brave");
        p.diligence = t.getFloat("dili");
        p.sociability = t.getFloat("soci");
        p.ambition = t.getFloat("ambi");
        p.piety = t.getFloat("piety");
        p.kindness = t.getFloat("kind");
        p.greed = t.getFloat("greed");
        p.food = t.getFloat("food");
        p.rest = t.getFloat("rest");
        p.safety = t.getFloat("safety");
        p.social = t.getFloat("social");
        p.health = t.getFloat("health");
        p.illnessDays = t.getInt("ill");
        p.loyalty = t.getFloat("loyal");
        p.mother = Nbt.getUUID(t, "mother");
        p.father = Nbt.getUUID(t, "father");
        p.spouse = Nbt.getUUID(t, "spouse");
        p.children.addAll(Nbt.readUuidList(t, "children"));
        p.courting = Nbt.getUUID(t, "courting");
        p.courtDays = t.getInt("courtDays");
        p.coins = t.getInt("coins");
        p.weaponTier = t.getInt("wpn");
        p.armorTier = t.getInt("arm");
        p.hasTools = t.getBoolean("tools");
        for (Tag x : t.getList("rel", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) x;
            p.relations.put(r.getUUID("p"), new Relation(r.getInt("o"), r.getInt("f")));
        }
        for (Tag x : t.getList("mem", Tag.TAG_COMPOUND)) p.memories.add(Memory.load((CompoundTag) x));
        p.activity = t.getString("act");
        p.blocked = t.getString("blocked");
        p.order = t.getString("order");
        return p;
    }
}
