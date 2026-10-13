package com.longnight.sim;

import com.longnight.content.Content;
import com.longnight.content.Culture;
import com.longnight.content.Faith;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Daily personal life: food, shelter, health, ageing, relationships, courtship, families and work. */
public final class Life {
    private Life() {}

    static final Commodity[] FOOD_ORDER = {Commodity.BREAD, Commodity.MEAT, Commodity.FISH, Commodity.VEGETABLES, Commodity.GRAIN};

    public static void daily(WorldSim sim, long day) {
        Random r = sim.rng;
        for (Settlement s : new ArrayList<>(sim.settlements.values())) {
            if (s.kind == Settlement.Kind.RUIN) continue;
            List<Person> people = sim.residents(s);
            if (people.isEmpty()) continue;
            // a company on the road, and a young camp until its first fields stand, live by hunting and gathering
            if (s.kind == Settlement.Kind.BAND || (s.kind == Settlement.Kind.SETTLEMENT && s.tier == Settlement.Tier.CAMP
                    && !s.hasUsable(BuildingType.FARM) && !s.hasUsable(BuildingType.FISHERY))) Band.forage(sim, s);
            feed(sim, s, people, day);
            house(sim, s, people);
            health(sim, s, people, day, r);
            if (s.kind == Settlement.Kind.BANDIT_CAMP) continue;
            if (s.kind == Settlement.Kind.BAND) {
                socialise(sim, s, sim.residents(s), day, r); // friendships form on the road; homes, trades and families wait
                continue;
            }
            socialise(sim, s, sim.residents(s), day, r);
            courtship(sim, s, sim.residents(s), day, r);
            births(sim, s, sim.residents(s), day, r);
            comingOfAge(sim, s, sim.residents(s), day);
            Jobs.assign(sim, s, day);
            apprentices(sim, s, sim.residents(s), day);
        }
    }

    // ------------------------------------------------------------------ food

    static void feed(WorldSim sim, Settlement s, List<Person> people, long day) {
        double need = 0;
        for (Person p : people) need += sim.isAdult(p, day) ? 1.0 : 0.6;
        double eaten = 0;
        int variety = 0;
        // eat in order of preference, but spread across kinds for variety
        for (Commodity c : FOOD_ORDER) {
            if (eaten >= need) break;
            double units = Math.min(s.stock.get(c), (need - eaten) / c.nourishment);
            if (units <= 0.01) continue;
            s.stock.take(c, units);
            eaten += units * c.nourishment;
            variety++;
        }
        double frac = need <= 0 ? 1 : Math.min(1, eaten / need);
        boolean ale = s.kind == Settlement.Kind.SETTLEMENT && s.stock.take(Commodity.ALE, Math.max(1, people.size() * 0.25));
        for (Person p : people) {
            if (frac >= 0.99) p.food = Math.min(1f, p.food + 0.5f);
            else p.food = (float) Math.max(0, p.food - 0.45 * (1 - frac) + 0.2 * frac);
            if (variety >= 3) p.social = Math.min(1f, p.social + 0.02f);
            if (ale) p.social = Math.min(1f, p.social + 0.05f);
            if (frac < 0.6 && sim.rng.nextFloat() < 0.15f) {
                p.remember(day, "hunger", null, "went hungry in " + s.name, -8);
            }
        }
        if (frac < 0.6 && s.kind == Settlement.Kind.SETTLEMENT && sim.rng.nextFloat() < 0.35f) {
            sim.addNews(day, "shortage", "Food is running short in " + s.name + ".", s, 2, "item", "food");
        }
    }

    // ------------------------------------------------------------------ shelter

    static void house(WorldSim sim, Settlement s, List<Person> people) {
        // clear dead/moved residents from homes
        for (Building b : s.buildings) {
            b.residents.removeIf(id -> {
                Person p = sim.person(id);
                return p == null || !p.alive || !s.id.equals(p.settlement) || !b.usable();
            });
        }
        for (Person p : people) {
            Building home = s.building(p.home);
            if (home != null && home.usable() && home.residents.contains(p.id)) continue;
            p.home = null;
            // households stay together: join spouse or parent first
            Building target = null;
            for (UUID kin : new UUID[]{p.spouse, p.mother, p.father}) {
                Person k = sim.person(kin);
                if (k != null && k.alive && k.home != null) {
                    Building kb = s.building(k.home);
                    if (kb != null && kb.usable() && kb.residents.size() < capacity(kb) + 2) {
                        target = kb;
                        break;
                    }
                }
            }
            if (target == null) {
                for (Building b : s.buildings) {
                    if (b.usable() && b.type.housing > 0 && b.residents.size() < capacity(b)) {
                        target = b;
                        break;
                    }
                }
            }
            if (target != null) {
                target.residents.add(p.id);
                p.home = target.id;
            }
        }
        for (Person p : people) {
            if (p.home != null) p.rest = Math.min(1f, p.rest + 0.4f);
            else p.rest = Math.max(0.25f, p.rest - 0.25f);
        }
    }

    static int capacity(Building b) {
        return b.type.housing + (b.level - 1) * 2;
    }

    // ------------------------------------------------------------------ health & death

    static void health(WorldSim sim, Settlement s, List<Person> people, long day, Random r) {
        boolean healer = false;
        for (Person p : people) if (p.profession == Profession.HEALER) healer = true;
        boolean crowded = people.size() > s.housingCapacity() + 2;
        double dpy = sim.rules.daysPerYear;
        for (Person p : people) {
            Culture c = Content.culture(p.culture);
            double age = sim.ageOf(p, day);
            // illness
            if (p.illnessDays > 0) {
                p.illnessDays--;
                p.health = Math.max(0f, p.health - (healer ? 0.06f : 0.12f));
                double die = 0.03 * (1 - p.health) * (healer ? 0.4 : 1.0) * sim.rules.deathRate;
                if (r.nextDouble() < die || p.health <= 0f) {
                    sim.kill(p, day, "fever");
                    continue;
                }
            } else {
                double chance = 0.0025 + (p.food < 0.4f ? 0.01 : 0) + (crowded ? 0.004 : 0) + (p.home == null ? 0.003 : 0);
                if (r.nextDouble() < chance * sim.rules.deathRate) {
                    p.illnessDays = 2 + r.nextInt(5);
                    p.remember(day, "illness", null, "fell ill with a fever", -10);
                }
                p.health = Math.min(1f, p.health + (healer ? 0.15f : 0.08f));
            }
            // hunger
            if (p.food < 0.12f) {
                p.health -= 0.12f;
                if (p.health <= 0f) {
                    sim.kill(p, day, "starvation");
                    continue;
                }
            }
            // age
            double L = c.lifespan;
            double annual = 0.002;
            if (age > 0.75 * L) annual += Math.pow((age - 0.75 * L) / (0.25 * L), 3) * 0.22;
            if (age < 2) annual += 0.01;
            if (r.nextDouble() < annual / dpy * sim.rules.deathRate) {
                sim.kill(p, day, age > 0.75 * L ? "old age" : "a sudden illness");
            }
        }
    }

    // ------------------------------------------------------------------ relationships

    static float compatibility(Person a, Person b) {
        float diff = Math.abs(a.kindness - b.kindness) + Math.abs(a.piety - b.piety) + Math.abs(a.sociability - b.sociability)
                + Math.abs(a.ambition - b.ambition) + Math.abs(a.greed - b.greed);
        float c = 1f - diff / 5f;
        if (a.faith.equals(b.faith)) c += 0.12f;
        else c -= 0.15f * Content.faith(a.faith).strictness;
        if (a.culture.equals(b.culture)) c += 0.06f;
        c += (b.kindness - 0.5f) * 0.3f;
        return Math.max(0f, Math.min(1.2f, c));
    }

    static void socialise(WorldSim sim, Settlement s, List<Person> people, long day, Random r) {
        if (people.size() < 2) return;
        for (Person p : people) {
            int talks = 1 + Math.round(p.sociability * 3);
            for (int i = 0; i < talks; i++) {
                Person o = pickContact(sim, p, people, r);
                if (o == null || o == p) continue;
                interact(sim, p, o, day, r);
            }
            // loneliness / company
            int friends = 0;
            for (Person.Relation rel : p.relations.values()) if (rel.opinion >= 40) friends++;
            p.social = Math.max(0f, Math.min(1f, p.social + (friends > 0 ? 0.08f : -0.06f) + (p.spouse != null ? 0.05f : 0)));
        }
    }

    static Person pickContact(WorldSim sim, Person p, List<Person> people, Random r) {
        float x = r.nextFloat();
        if (x < 0.3f && p.spouse != null) return sim.person(p.spouse);
        if (x < 0.55f && p.workplace != null) {
            List<Person> mates = new ArrayList<>();
            for (Person o : people) if (p.workplace.equals(o.workplace) && o != p) mates.add(o);
            if (!mates.isEmpty()) return mates.get(r.nextInt(mates.size()));
        }
        return people.get(r.nextInt(people.size()));
    }

    static void interact(WorldSim sim, Person a, Person b, long day, Random r) {
        if (!b.alive) return;
        float ca = compatibility(a, b);
        float cb = compatibility(b, a);
        int da = Math.round(ca * 4.5f - 2f + (float) r.nextGaussian());
        int db = Math.round(cb * 4.5f - 2f + (float) r.nextGaussian());
        Person.Relation ra = a.relate(b.id);
        Person.Relation rb = b.relate(a.id);
        ra.familiarity = Math.min(100, ra.familiarity + 3);
        rb.familiarity = Math.min(100, rb.familiarity + 3);
        int beforeA = ra.opinion;
        a.changeOpinion(b.id, da);
        b.changeOpinion(a.id, db);
        if (beforeA < 40 && ra.opinion >= 40) a.remember(day, "friend", b.id, "became friends with " + b.given, 15);
        if (beforeA > -40 && ra.opinion <= -40) a.remember(day, "rival", b.id, "quarrelled bitterly with " + b.given, -15);
    }

    // ------------------------------------------------------------------ romance & marriage (non-explicit, adults only)

    static void courtship(WorldSim sim, Settlement s, List<Person> people, long day, Random r) {
        int adultAge = sim.rules.adultAge;
        for (Person p : people) {
            if (!sim.isAdult(p, day) || p.spouse != null) continue;
            Culture c = Content.culture(p.culture);
            if (p.courting != null) {
                Person o = sim.person(p.courting);
                if (o == null || !o.alive || o.spouse != null || !s.id.equals(o.settlement)) {
                    p.courting = null;
                    continue;
                }
                if (p.id.compareTo(o.id) > 0) continue; // handle each couple once
                p.courtDays++;
                o.courtDays = p.courtDays;
                p.changeOpinion(o.id, 3);
                o.changeOpinion(p.id, 3);
                if (p.opinionOf(o.id) < 5 || o.opinionOf(p.id) < 5) {
                    p.courting = null;
                    o.courting = null;
                    p.remember(day, "heartbreak", o.id, "parted ways with " + o.given, -12);
                    o.remember(day, "heartbreak", p.id, "parted ways with " + p.given, -12);
                    continue;
                }
                if (p.courtDays >= 3 + r.nextInt(4)) {
                    Person proposer = p.ambition >= o.ambition ? p : o;
                    Person answer = proposer == p ? o : p;
                    if (answer.opinionOf(proposer.id) >= 40) marry(sim, s, proposer, answer, day, r);
                    else if (r.nextFloat() < 0.3f) {
                        p.courting = null;
                        o.courting = null;
                        proposer.remember(day, "refused", answer.id, answer.given + " refused my proposal", -20);
                    }
                }
                continue;
            }
            if (r.nextFloat() > 0.12f + p.sociability * 0.12f) continue;
            double age = sim.ageOf(p, day);
            if (age > c.lifespan * 0.8) continue;
            Person best = null;
            int bestScore = 24;
            for (Person o : people) {
                if (o == p || o.spouse != null || o.courting != null || !sim.isAdult(o, day)) continue;
                if (!p.attractedTo(o) || !o.attractedTo(p)) continue;
                if (related(p, o)) continue;
                double gap = Math.abs(sim.ageOf(o, day) - age) * 70.0 / Math.max(c.lifespan, 40);
                if (gap > 14) continue;
                int score = p.opinionOf(o.id) + o.opinionOf(p.id) / 2 - (int) gap;
                if (!p.faith.equals(o.faith)) {
                    float strict = Math.max(Content.faith(p.faith).strictness, Content.faith(o.faith).strictness);
                    score -= (int) (strict * 30);
                }
                if (score > bestScore) {
                    best = o;
                    bestScore = score;
                }
            }
            if (best != null && adultAge > 0) {
                p.courting = best.id;
                best.courting = p.id;
                p.courtDays = 0;
                best.courtDays = 0;
                p.remember(day, "courting", best.id, "began courting " + best.given, 10);
                best.remember(day, "courting", p.id, "was courted by " + p.given, 10);
            }
        }
    }

    static boolean related(Person a, Person b) {
        if (a.id.equals(b.mother) || a.id.equals(b.father) || b.id.equals(a.mother) || b.id.equals(a.father)) return true;
        if (a.mother != null && a.mother.equals(b.mother)) return true;
        return a.father != null && a.father.equals(b.father);
    }

    public static void marry(WorldSim sim, Settlement s, Person a, Person b, long day, Random r) {
        a.spouse = b.id;
        b.spouse = a.id;
        a.courting = null;
        b.courting = null;
        a.remember(day, "wedding", b.id, "married " + b.given, 40);
        b.remember(day, "wedding", a.id, "married " + a.given, 40);
        // one spouse may take up the other's faith
        if (!a.faith.equals(b.faith)) {
            Faith fa = Content.faith(a.faith);
            Faith fb = Content.faith(b.faith);
            Person convert = a.piety * (0.5f + fa.strictness) < b.piety * (0.5f + fb.strictness) ? a : b;
            Person keeper = convert == a ? b : a;
            if (r.nextFloat() < 0.6f) {
                convert.faith = keeper.faith;
                convert.remember(day, "conversion", keeper.id, "took up the faith of " + keeper.given, 5);
            }
        }
        // the household settles into one home
        if (a.home == null && b.home != null) moveHome(sim, s, a, b.home);
        else if (b.home == null && a.home != null) moveHome(sim, s, b, a.home);
        else if (a.home != null && !a.home.equals(b.home)) moveHome(sim, s, b, a.home);
        for (Person o : sim.residents(s)) {
            if (o != a && o != b && (o.opinionOf(a.id) > 30 || o.opinionOf(b.id) > 30)) {
                o.remember(day, "wedding_guest", a.id, "celebrated the wedding of " + a.given + " and " + b.given, 6);
            }
        }
        Faith f = Content.faith(a.faith);
        sim.addNews(day, "marriage", a.fullName() + " and " + b.fullName() + " were wed in " + s.name + " by the rites of " + f.name + ".",
                s, 1, "person", a.given + " and " + b.given);
        s.chronicle(day, a.fullName() + " married " + b.fullName() + ".");
    }

    static void moveHome(WorldSim sim, Settlement s, Person p, UUID homeId) {
        Building old = s.building(p.home);
        if (old != null) old.residents.remove(p.id);
        Building nb = s.building(homeId);
        if (nb != null && !nb.residents.contains(p.id)) nb.residents.add(p.id);
        p.home = homeId;
        for (UUID cid : p.children) {
            Person c = sim.person(cid);
            if (c != null && c.alive && !sim.isAdult(c, sim.lastDay) && s.id.equals(c.settlement)) moveHome(sim, s, c, homeId);
        }
    }

    // ------------------------------------------------------------------ births

    static void births(WorldSim sim, Settlement s, List<Person> people, long day, Random r) {
        boolean foodOk = s.stock.foodUnits() > people.size() * 2;
        boolean roomOk = people.size() < s.housingCapacity() + 2;
        for (Person mother : people) {
            if (mother.sex != Person.Sex.FEMALE || mother.spouse == null) continue;
            Person father = sim.person(mother.spouse);
            if (father == null || !father.alive || father.sex != Person.Sex.MALE) continue;
            Culture c = Content.culture(mother.culture);
            double age = sim.ageOf(mother, day);
            if (age < sim.rules.adultAge || age > c.lifespan * 0.6) continue;
            if (mother.children.size() >= 6) continue;
            double yearly = 0.32 * Math.sqrt(70.0 / Math.max(40, c.lifespan));
            double chance = yearly / sim.rules.daysPerYear * sim.rules.birthRate * (foodOk ? 1 : 0.3) * (roomOk ? 1 : 0.35);
            if (r.nextDouble() >= chance) continue;
            Person child = sim.createChild(mother, father, day, r);
            if (!s.residents.contains(child.id)) s.residents.add(child.id);
            Building home = s.building(mother.home);
            if (home != null) home.residents.add(child.id);
            mother.remember(day, "birth", child.id, "gave birth to " + child.given, 35);
            father.remember(day, "birth", child.id, "became a father to " + child.given, 35);
            sim.addNews(day, "birth", "A child, " + child.given + ", was born to " + mother.given + " and " + father.fullName() + " in " + s.name + ".",
                    s, 1, "person", mother.given + " and " + father.given);
        }
    }

    // ------------------------------------------------------------------ growing up

    static void comingOfAge(WorldSim sim, Settlement s, List<Person> people, long day) {
        double dpy = sim.rules.daysPerYear;
        for (Person p : people) {
            double age = sim.ageOf(p, day);
            double ageYesterday = age - 1.0 / dpy;
            if (age >= sim.rules.adultAge && ageYesterday < sim.rules.adultAge) {
                p.remember(day, "adulthood", null, "came of age in " + s.name, 12);
                sim.addNews(day, "coming_of_age", p.fullName() + " has come of age in " + s.name + ".", s, 1);
                if (p.mentor != null) {
                    Person m = sim.person(p.mentor);
                    if (m != null && m.alive) {
                        m.remember(day, "pupil", p.id, "saw my apprentice " + p.given + " come of age", 10);
                        p.changeOpinion(m.id, 10);
                    }
                }
            }
        }
    }

    static void apprentices(WorldSim sim, Settlement s, List<Person> people, long day) {
        boolean school = false;
        for (Person p : people) if (p.profession == Profession.TEACHER) school = true;
        for (Person p : people) {
            double age = sim.ageOf(p, day);
            if (age >= sim.rules.adultAge || age < sim.rules.adultAge - 6) continue;
            Person m = sim.person(p.mentor);
            if (m == null || !m.alive || !s.id.equals(m.settlement) || !m.profession.producesGoods() && m.profession.workplace == null) {
                // find a master: a parent first, otherwise the most skilled worker in a needed trade
                m = null;
                for (UUID pid : new UUID[]{p.father, p.mother}) {
                    Person par = sim.person(pid);
                    if (par != null && par.alive && par.profession != Profession.LABOURER && par.profession != Profession.RULER) {
                        m = par;
                        break;
                    }
                }
                if (m == null) {
                    m = people.stream().filter(o -> sim.isAdult(o, day) && o.profession.workplace != null)
                            .max(Comparator.comparingDouble(o -> o.skill(o.profession))).orElse(null);
                }
                if (m != null && !m.id.equals(p.mentor)) {
                    p.mentor = m.id;
                    p.remember(day, "apprentice", m.id, "was apprenticed to " + m.fullName() + ", " + m.profession.displayName.toLowerCase(), 8);
                }
            }
            if (m != null) {
                float rate = 0.5f + m.skill(m.profession) / 200f + (school ? 0.3f : 0f);
                p.addSkill(m.profession, rate * (0.6f + p.diligence));
                p.activity = "learning the " + m.profession.displayName.toLowerCase() + "'s trade";
            }
        }
    }

    static Map<Commodity, Double> zero() {
        return new EnumMap<>(Commodity.class);
    }
}
