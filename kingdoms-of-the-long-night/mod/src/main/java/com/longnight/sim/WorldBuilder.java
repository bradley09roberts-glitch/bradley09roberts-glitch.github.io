package com.longnight.sim;

import com.longnight.content.Content;
import com.longnight.content.Culture;
import com.longnight.content.NameSet;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Creates the world the player wakes into: realms with histories and rulers, established
 * settlements full of families, small camps of settlers still looking for a home, outlaw
 * bands, and the ruins of kingdoms that fell long ago.
 */
public final class WorldBuilder {
    private WorldBuilder() {}

    /** A candidate place found by the terrain survey. */
    public record Site(BlockPos pos, String culture, boolean water, boolean forest, boolean hills, double quality) {}

    public static Settlement newSettlement(WorldSim sim, String culture, BlockPos center, long day, String name) {
        Settlement s = new Settlement(UUID.randomUUID());
        s.culture = culture;
        s.center = center;
        s.foundedDay = day;
        s.name = name;
        s.radius = 40;
        sim.settlements.put(s.id, s);
        sim.setDirty();
        return s;
    }

    public static void generate(WorldSim sim, List<Site> sites, int settlements, int camps, int banditCamps, long seed) {
        Random r = new Random(seed);
        double dpy = sim.rules.daysPerYear;
        List<Site> pool = new ArrayList<>(sites);
        pool.sort(Comparator.comparingDouble(Site::quality).reversed());
        List<Site> chosen = new ArrayList<>();
        // spread settlements out: at least 260 blocks apart
        for (Site s : pool) {
            if (chosen.size() >= settlements) break;
            if (far(s, chosen, 260)) chosen.add(s);
        }
        pool.removeAll(chosen);

        // ---- realms: group established settlements by culture and proximity
        List<Faction> realms = new ArrayList<>();
        List<Settlement> established = new ArrayList<>();
        for (Site site : chosen) {
            NameSet ns = Content.names(site.culture());
            Settlement s = newSettlement(sim, site.culture(), site.pos(), -(long) ((40 + r.nextInt(260)) * dpy), uniqueName(sim, ns, r));
            established.add(s);
            Faction realm = null;
            for (Faction f : realms) {
                Settlement cap = sim.settlement(f.capital);
                if (f.culture.equals(s.culture) && cap != null && Math.sqrt(cap.center.distSqr(s.center)) < 700 && sim.settlementsOf(f).size() < 3) {
                    realm = f;
                }
            }
            if (realm == null) {
                realm = new Faction(UUID.randomUUID());
                Culture c = Content.culture(s.culture);
                realm.culture = s.culture;
                String gov = c.governments.isEmpty() ? "MONARCHY" : c.governments.get(r.nextInt(c.governments.size()));
                try {
                    realm.government = Faction.Government.valueOf(gov);
                } catch (IllegalArgumentException e) {
                    realm.government = Faction.Government.MONARCHY;
                }
                realm.name = ns.realmName(s.name, r);
                realm.capital = s.id;
                realm.foundedDay = s.foundedDay;
                realm.bannerColor = c.bannerColor;
                realm.bannerAccent = c.bannerAccent;
                realm.treasury = 200 + r.nextInt(400);
                sim.factions.put(realm.id, realm);
                realms.add(realm);
            }
            s.faction = realm.id;
        }
        // ---- people and buildings for each established settlement
        for (Settlement s : established) {
            Faction f = sim.faction(s.faction);
            boolean capital = f != null && s.id.equals(f.capital);
            int pop = capital ? 22 + r.nextInt(14) : 9 + r.nextInt(14);
            populate(sim, s, pop, r);
            raiseBuildings(sim, s, r);
            stock(s, r, 1.0);
            s.treasury = 60 + pop * 6 + r.nextInt(100);
            s.materialized = false;
            s.chronicle(0, s.name + " was founded in the year " + Calendar.year(s.foundedDay, sim.rules.daysPerYear) + " of the Reckoning.");
        }
        // ---- rulers and heirs
        for (Faction f : realms) {
            Settlement cap = sim.settlement(f.capital);
            Person ruler = sim.residents(cap).stream().filter(p -> sim.isAdult(p, 0) && sim.ageOf(p, 0) > sim.rules.adultAge + 8)
                    .max(Comparator.comparingDouble(p -> p.ambition + p.prestige / 50.0 + (p.children.isEmpty() ? 0 : 0.3)))
                    .orElse(null);
            if (ruler == null) continue;
            f.ruler = ruler.id;
            ruler.profession = Profession.RULER;
            ruler.workplace = null;
            ruler.title = f.rulerTitle(ruler) + " of " + f.name;
            ruler.prestige += 30;
            ruler.coins += 200;
            if (r.nextBoolean()) ruler.epithet = Content.names(ruler.culture).epithet(r);
            Person heir = Politics.chooseHeir(sim, f, 0);
            f.heir = heir == null ? null : heir.id;
            // a remembered predecessor
            NameSet ns = Content.names(f.culture);
            String old = ns.given(r.nextBoolean() ? Person.Sex.MALE : Person.Sex.FEMALE, r) + " " + ns.epithet(r);
            f.chronicle(-(long) (dpy * (5 + r.nextInt(30))), old + " ruled " + f.name + " before " + ruler.given + ".");
        }
        // ---- relations: neighbours, old grudges, friendships
        for (int i = 0; i < realms.size(); i++) {
            for (int j = i + 1; j < realms.size(); j++) {
                Faction a = realms.get(i), b = realms.get(j);
                int rel = a.culture.equals(b.culture) ? 20 : -5;
                rel += r.nextInt(41) - 20;
                a.relations.put(b.id, rel);
                b.relations.put(a.id, rel);
                if (rel > 25) {
                    a.stances.put(b.id, Faction.Stance.TRADE);
                    b.stances.put(a.id, Faction.Stance.TRADE);
                }
            }
        }
        if (realms.size() >= 2) {
            Faction a = realms.get(r.nextInt(realms.size()));
            Faction b = realms.get(r.nextInt(realms.size()));
            if (a != b) {
                a.relations.put(b.id, -45);
                b.relations.put(a.id, -45);
                long when = -(long) (dpy * (8 + r.nextInt(30)));
                String text = a.name + " and " + b.name + " fought the War of the " + pick(r, "Broken Ford", "Salt Road", "Three Banners",
                        "Grey Hills", "Burning Barns", "Widow's Bridge") + "; neither side has forgotten it.";
                a.chronicle(when, text);
                b.chronicle(when, text);
                sim.history.add("Year " + Calendar.year(when, sim.rules.daysPerYear) + ": " + text);
            }
        }
        // ---- small camps of settlers who start with nothing but tents
        List<Site> campSites = new ArrayList<>();
        for (Site s : pool) {
            if (campSites.size() >= camps) break;
            if (far(s, chosen, 220) && far(s, campSites, 220)) campSites.add(s);
        }
        pool.removeAll(campSites);
        for (Site site : campSites) {
            NameSet ns = Content.names(site.culture());
            Settlement camp = newSettlement(sim, site.culture(), site.pos(), -(long) (dpy * 0.5), uniqueCamp(sim, ns, r));
            camp.radius = 30;
            populate(sim, camp, 4 + r.nextInt(4), r);
            stock(camp, r, 0.35);
            camp.treasury = 20 + r.nextInt(30);
            Building tents = new Building(UUID.randomUUID(), BuildingType.CAMPSITE);
            tents.seed = r.nextLong();
            tents.origin = camp.center.offset(-4, 0, 5);
            tents.faith = Planner.dominantFaith(sim, camp);
            Construction.initPlan(camp, tents);
            tents.progress = tents.total;
            tents.state = Building.State.COMPLETE;
            camp.buildings.add(tents);
            camp.chronicle(0, "A band of " + camp.residents.size() + " " + Content.culture(camp.culture).name + " pitched their tents here, looking for a new home.");
            // camps may owe loyalty to a nearby realm of their people, or none
            for (Faction f : realms) {
                Settlement cap = sim.settlement(f.capital);
                if (f.culture.equals(camp.culture) && cap != null && Math.sqrt(cap.center.distSqr(camp.center)) < 900 && r.nextBoolean()) {
                    camp.faction = f.id;
                }
            }
        }
        // ---- outlaw bands
        List<Site> wild = new ArrayList<>();
        for (Site s : pool) {
            if (wild.size() >= banditCamps) break;
            if (far(s, chosen, 300) && far(s, campSites, 250) && far(s, wild, 300)) wild.add(s);
        }
        pool.removeAll(wild);
        for (Site site : wild) {
            String culture = pickOutlawCulture(r);
            NameSet ns = Content.names(culture);
            Settlement camp = newSettlement(sim, culture, site.pos(), -(long) (dpy * 2), uniqueCamp(sim, ns, r));
            camp.kind = Settlement.Kind.BANDIT_CAMP;
            camp.radius = 24;
            Faction band = new Faction(UUID.randomUUID());
            band.government = Faction.Government.OUTLAWS;
            band.culture = culture;
            band.name = "Outlaws of " + camp.name;
            band.capital = camp.id;
            band.bannerColor = 15;
            band.bannerAccent = 14;
            sim.factions.put(band.id, band);
            camp.faction = band.id;
            int n = 3 + r.nextInt(3);
            for (int i = 0; i < n; i++) {
                Person p = sim.createPerson(i == 0 || r.nextFloat() < 0.6f ? culture : Content.CULTURE_IDS[r.nextInt(Content.CULTURE_IDS.length)],
                        r.nextFloat() < 0.75f ? Person.Sex.MALE : Person.Sex.FEMALE, -(long) ((sim.rules.adultAge + 2 + r.nextInt(22)) * dpy), r);
                p.kindness *= 0.6f;
                p.bravery = Math.min(1f, p.bravery + 0.15f);
                sim.join(p, camp);
                p.profession = Profession.BANDIT;
                p.weaponTier = 1;
                if (i == 0) {
                    band.ruler = p.id;
                    p.title = "Captain of the " + camp.name + " band";
                    p.epithet = ns.epithet(r);
                    p.armorTier = 1;
                }
            }
            Building tents = new Building(UUID.randomUUID(), BuildingType.CAMPSITE);
            tents.seed = r.nextLong();
            tents.origin = camp.center.offset(-4, 0, -4);
            tents.faith = "ember";
            Construction.initPlan(camp, tents);
            tents.progress = tents.total;
            tents.state = Building.State.COMPLETE;
            camp.buildings.add(tents);
            camp.stock.add(Commodity.MEAT, 10 + r.nextInt(20));
            camp.stock.add(Commodity.ALE, 5);
            camp.hunger = r.nextInt(4);
        }
        // ---- ruins of lost kingdoms
        String[] lost = {"Aelmarch", "Kar Vethis", "Old Corrow", "Sundhallow", "Ystrel", "Morrowgate", "Duskwater", "Hethmoor"};
        int ruins = 0;
        for (Site site : new ArrayList<>(pool)) {
            if (ruins >= 3) break;
            if (!far(site, chosen, 200) || !far(site, campSites, 200) || !far(site, wild, 200)) continue;
            Settlement ruin = newSettlement(sim, site.culture(), site.pos(), -(long) (dpy * (300 + r.nextInt(600))), lost[r.nextInt(lost.length)]);
            ruin.kind = Settlement.Kind.RUIN;
            ruin.radius = 26;
            for (BuildingType t : new BuildingType[]{BuildingType.TEMPLE, BuildingType.WATCHTOWER, BuildingType.HOUSE, BuildingType.HOUSE}) {
                Building b = Planner.plan(sim, ruin, t, -1);
                if (b == null) continue;
                b.progress = (int) (b.total * (0.45 + r.nextDouble() * 0.35));
                b.state = Building.State.RUINED;
            }
            ruin.chronicle(0, "Here stood " + ruin.name + ", in the years before the Reckoning.");
            sim.history.add("Before the Reckoning: " + ruin.name + " fell, and no living folk remember why.");
            pool.remove(site);
            ruins++;
        }
        // remaining good sites are left for future settlers
        for (Site s : pool) if (sim.spareSites.size() < 40) sim.spareSites.add(s.pos());
        sim.generated = true;
        sim.log(0, "The chronicle of the world begins. " + realms.size() + " realms, " + established.size() + " settlements, "
                + campSites.size() + " camps of settlers, " + wild.size() + " outlaw bands.");
        sim.setDirty();
    }

    static String pickOutlawCulture(Random r) {
        String[] o = {"skrell", "ashborn", "valemen", "skrell", "tidefolk", "aszari"};
        return o[r.nextInt(o.length)];
    }

    static String pick(Random r, String... s) {
        return s[r.nextInt(s.length)];
    }

    static boolean far(Site s, List<Site> others, double min) {
        for (Site o : others) {
            double dx = s.pos().getX() - o.pos().getX(), dz = s.pos().getZ() - o.pos().getZ();
            if (dx * dx + dz * dz < min * min) return false;
        }
        return true;
    }

    public static String uniqueCamp(WorldSim sim, NameSet ns, Random r) {
        for (int i = 0; i < 20; i++) {
            String n = ns.campName(r);
            boolean dup = false;
            for (Settlement s : sim.settlements.values()) if (s.name.equalsIgnoreCase(n)) dup = true;
            if (!dup) return n;
        }
        return uniqueName(sim, ns, r);
    }

    static String uniqueName(WorldSim sim, NameSet ns, Random r) {
        for (int i = 0; i < 20; i++) {
            String n = ns.settlementName(r);
            boolean dup = false;
            for (Settlement s : sim.settlements.values()) if (s.name.equalsIgnoreCase(n)) dup = true;
            if (!dup) return n;
        }
        return ns.settlementName(r) + " " + (1 + r.nextInt(9));
    }

    /** Families: couples with children, widows, elders, young singles; some neighbours of other peoples. */
    public static void populate(WorldSim sim, Settlement s, int target, Random r) {
        double dpy = sim.rules.daysPerYear;
        int adult = sim.rules.adultAge;
        Culture c = Content.culture(s.culture);
        double lifeScale = Math.max(1.0, c.lifespan / 70.0);
        int made = 0;
        while (made < target) {
            String culture = r.nextFloat() < 0.88f ? s.culture : Content.CULTURE_IDS[r.nextInt(Content.CULTURE_IDS.length)];
            if (culture.equals("skrell") && !s.culture.equals("skrell") && r.nextBoolean()) culture = s.culture;
            int kind = r.nextInt(10);
            if (kind < 6) {
                // a married couple and their children
                double ageA = adult + 4 + r.nextInt(26);
                Person a = sim.createPerson(culture, Person.Sex.MALE, -(long) (ageA * lifeScale * dpy), r);
                Person b = sim.createPerson(r.nextFloat() < 0.9f ? culture : s.culture, Person.Sex.FEMALE,
                        -(long) ((ageA + r.nextInt(7) - 3) * lifeScale * dpy), r);
                if (r.nextFloat() < 0.06f) { // some couples are two men or two women
                    b.sex = a.sex;
                    b.given = Content.names(b.culture).given(b.sex, r);
                    a.orientation = Person.Orientation.GAY;
                    b.orientation = Person.Orientation.GAY;
                } else {
                    if (a.orientation == Person.Orientation.GAY) a.orientation = Person.Orientation.STRAIGHT;
                    if (b.orientation == Person.Orientation.GAY) b.orientation = Person.Orientation.STRAIGHT;
                }
                a.spouse = b.id;
                b.spouse = a.id;
                if (!"patronymic".equals(Content.names(culture).familyStyle) && b.culture.equals(a.culture) && r.nextFloat() < 0.6f) b.family = a.family;
                a.changeOpinion(b.id, 50 + r.nextInt(30));
                b.changeOpinion(a.id, 50 + r.nextInt(30));
                a.relate(b.id).familiarity = 100;
                b.relate(a.id).familiarity = 100;
                sim.join(a, s);
                sim.join(b, s);
                made += 2;
                if (a.sex != b.sex) {
                    int kids = r.nextInt(4);
                    double motherAge = sim.ageOf(b, 0) / lifeScale;
                    for (int k = 0; k < kids && made < target + 2; k++) {
                        double childAge = Math.max(0, motherAge - adult - 1 - r.nextInt(Math.max(1, (int) (motherAge - adult))));
                        Person child = sim.createChild(b, a, -(long) (childAge * lifeScale * dpy), r);
                        child.settlement = null;
                        sim.join(child, s);
                        a.changeOpinion(child.id, 60);
                        b.changeOpinion(child.id, 60);
                        child.changeOpinion(a.id, 50);
                        child.changeOpinion(b.id, 50);
                        made++;
                    }
                }
                giveSkills(sim, a, r);
                giveSkills(sim, b, r);
            } else if (kind < 8) {
                // a young single adult
                Person p = sim.createPerson(culture, null, -(long) ((adult + r.nextInt(10)) * lifeScale * dpy), r);
                sim.join(p, s);
                giveSkills(sim, p, r);
                made++;
            } else {
                // an elder, perhaps widowed
                Culture cc = Content.culture(culture);
                double age = cc.lifespan * (0.62 + r.nextDouble() * 0.2);
                Person p = sim.createPerson(culture, null, -(long) (age * dpy), r);
                p.remember(-(long) (dpy * 3), "bereaved", null, "buried my spouse long ago", -10);
                p.prestige += 8;
                sim.join(p, s);
                giveSkills(sim, p, r);
                made++;
            }
        }
        // neighbours know each other
        List<Person> people = sim.residents(s);
        for (int i = 0; i < people.size() * 3; i++) {
            Person a = people.get(r.nextInt(people.size()));
            Person b = people.get(r.nextInt(people.size()));
            if (a != b) Life.interact(sim, a, b, 0, r);
        }
    }

    static void giveSkills(WorldSim sim, Person p, Random r) {
        Growth.giveTrade(p, r);
        double years = sim.ageOf(p, 0) - sim.rules.adultAge;
        for (var e : p.skills.entrySet()) e.setValue((float) Math.min(95, e.getValue() + years * 0.8));
        p.prestige += (int) (years / 4);
        p.coins += r.nextInt(30);
    }

    /** Raise the buildings an established settlement would have built over its history. */
    static void raiseBuildings(WorldSim sim, Settlement s, Random r) {
        for (int i = 0; i < 40; i++) {
            BuildingType t = Planner.next(sim, s, 0);
            if (t == null) break;
            Building b = Planner.plan(sim, s, t, s.foundedDay);
            if (b == null) break;
            b.progress = b.total;
            b.state = Building.State.COMPLETE;
            b.completedDay = s.foundedDay + r.nextInt(Math.max(1, (int) -s.foundedDay));
            // as buildings appear, people take up work there
            Jobs.assign(sim, s, 0);
        }
        Life.house(sim, s, sim.residents(s));
        Jobs.assign(sim, s, 0);
        Planner.updateTier(sim, s, 0);
        // housing for everyone in an established place
        int guard = 0;
        while (s.housingCapacity() < s.residents.size() && guard++ < 10) {
            Building b = Planner.plan(sim, s, BuildingType.HOUSE, s.foundedDay);
            if (b == null) break;
            b.progress = b.total;
            b.state = Building.State.COMPLETE;
        }
        Life.house(sim, s, sim.residents(s));
        Jobs.assign(sim, s, 0);
        Planner.updateTier(sim, s, 0);
    }

    static void stock(Settlement s, Random r, double scale) {
        int pop = Math.max(4, s.residents.size());
        s.stock.add(Commodity.GRAIN, pop * 6 * scale);
        s.stock.add(Commodity.BREAD, pop * 3 * scale);
        s.stock.add(Commodity.VEGETABLES, pop * 3 * scale);
        s.stock.add(Commodity.MEAT, pop * 2 * scale);
        s.stock.add(Commodity.LOGS, 30 * scale);
        s.stock.add(Commodity.PLANKS, 80 * scale);
        s.stock.add(Commodity.STONE, 90 * scale);
        s.stock.add(Commodity.GLASS, 10 * scale);
        s.stock.add(Commodity.FUEL, 20 * scale);
        s.stock.add(Commodity.TOOLS, 4 * scale);
        s.stock.add(Commodity.IRON, 6 * scale);
        s.stock.add(Commodity.WOOL, 6 * scale);
    }
}
