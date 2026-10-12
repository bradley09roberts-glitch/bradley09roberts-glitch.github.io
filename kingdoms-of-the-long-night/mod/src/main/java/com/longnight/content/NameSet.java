package com.longnight.content;

import com.google.gson.JsonObject;
import com.longnight.sim.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Naming conventions of a culture (data/longnight/names/<culture>.json). */
public class NameSet {
    public final List<String> male = new ArrayList<>();
    public final List<String> female = new ArrayList<>();
    public final List<String> family = new ArrayList<>();
    public String familyStyle = "surname";
    public String patronymicMale = "son of {father}";
    public String patronymicFemale = "daughter of {father}";
    public final List<String> epithets = new ArrayList<>();
    public final List<String> settlementPrefix = new ArrayList<>();
    public final List<String> settlementSuffix = new ArrayList<>();
    public final List<String> realmPatterns = new ArrayList<>();
    public final List<String> campNames = new ArrayList<>();

    public static final NameSet FALLBACK = new NameSet();

    static {
        FALLBACK.male.addAll(List.of("Aldric", "Bram", "Cedric", "Doran", "Edwin", "Garrick", "Hal", "Osric"));
        FALLBACK.female.addAll(List.of("Alys", "Brenna", "Cora", "Edith", "Hilde", "Maud", "Rowena", "Wynn"));
        FALLBACK.family.addAll(List.of("Ashdown", "Brook", "Carter", "Fenwick", "Holt", "Marsh", "Thorne", "Wells"));
        FALLBACK.settlementPrefix.addAll(List.of("Ash", "Bram", "Elm", "Fern", "Hollow", "Oak", "Thorn", "Wold"));
        FALLBACK.settlementSuffix.addAll(List.of("ford", "ham", "ley", "stead", "wick", "by", "ton", "mere"));
        FALLBACK.realmPatterns.addAll(List.of("Kingdom of {name}", "Realm of {name}"));
        FALLBACK.campNames.addAll(List.of("Wayfarers' Camp", "Last Fire", "Pilgrims' Rest"));
        FALLBACK.epithets.addAll(List.of("the Bold", "the Wise", "the Stern"));
    }

    public static NameSet parse(JsonObject o) {
        NameSet n = new NameSet();
        n.male.addAll(Content.strings(o.get("given_male")));
        n.female.addAll(Content.strings(o.get("given_female")));
        n.family.addAll(Content.strings(o.get("family")));
        n.familyStyle = Culture.str(o, "family_style", "surname");
        n.patronymicMale = Culture.str(o, "patronymic_male", "son of {father}");
        n.patronymicFemale = Culture.str(o, "patronymic_female", "daughter of {father}");
        n.epithets.addAll(Content.strings(o.get("epithets")));
        n.settlementPrefix.addAll(Content.strings(o.get("settlement_prefix")));
        n.settlementSuffix.addAll(Content.strings(o.get("settlement_suffix")));
        n.realmPatterns.addAll(Content.strings(o.get("realm_patterns")));
        n.campNames.addAll(Content.strings(o.get("camp_names")));
        if (n.male.isEmpty()) n.male.addAll(FALLBACK.male);
        if (n.female.isEmpty()) n.female.addAll(FALLBACK.female);
        if (n.family.isEmpty()) n.family.addAll(FALLBACK.family);
        if (n.settlementPrefix.isEmpty()) n.settlementPrefix.addAll(FALLBACK.settlementPrefix);
        if (n.settlementSuffix.isEmpty()) n.settlementSuffix.addAll(FALLBACK.settlementSuffix);
        if (n.realmPatterns.isEmpty()) n.realmPatterns.addAll(FALLBACK.realmPatterns);
        if (n.campNames.isEmpty()) n.campNames.addAll(FALLBACK.campNames);
        if (n.epithets.isEmpty()) n.epithets.addAll(FALLBACK.epithets);
        return n;
    }

    static String pick(List<String> l, Random r) {
        return l.isEmpty() ? "" : l.get(r.nextInt(l.size()));
    }

    public String given(Person.Sex sex, Random r) {
        return pick(sex == Person.Sex.FEMALE ? female : male, r);
    }

    public String newFamily(Random r) {
        return pick(family, r);
    }

    /** The family part of a child's name given its parents. */
    public String inheritFamily(Person child, Person father, Person mother, Random r) {
        if ("patronymic".equals(familyStyle)) {
            Person parent = father != null ? father : mother;
            String parentName = parent != null ? parent.given : pick(male, r);
            String pattern = child.sex == Person.Sex.FEMALE ? patronymicFemale : patronymicMale;
            return pattern.replace("{father}", parentName);
        }
        if (father != null && !father.family.isEmpty() && !"patronymic".equals(familyStyle)) return stripPatronymic(father.family, r);
        if (mother != null && !mother.family.isEmpty()) return stripPatronymic(mother.family, r);
        return newFamily(r);
    }

    private String stripPatronymic(String fam, Random r) {
        return fam.startsWith("son of") || fam.startsWith("daughter of") ? newFamily(r) : fam;
    }

    /** Family name for a freshly generated (founding-generation) person. */
    public String rootFamily(Person p, Random r) {
        if ("patronymic".equals(familyStyle)) {
            String pattern = p.sex == Person.Sex.FEMALE ? patronymicFemale : patronymicMale;
            return pattern.replace("{father}", pick(male, r));
        }
        return newFamily(r);
    }

    public String settlementName(Random r) {
        String a = pick(settlementPrefix, r);
        String b = pick(settlementSuffix, r);
        if (b.isEmpty()) return a;
        if (b.startsWith(" ")) return a + b;
        // avoid doubled letters like "Ashhold"
        if (!a.isEmpty() && Character.toLowerCase(a.charAt(a.length() - 1)) == Character.toLowerCase(b.charAt(0))) {
            b = b.substring(1);
        }
        return a + b;
    }

    public String realmName(String base, Random r) {
        return pick(realmPatterns, r).replace("{name}", base);
    }

    public String campName(Random r) {
        return pick(campNames, r);
    }

    public String epithet(Random r) {
        return pick(epithets, r);
    }
}
