package com.longnight.sim;

import com.longnight.content.Content;
import com.longnight.content.NameSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Realms, rulers and their wars. Rulers have temperaments that become agendas; agendas drive
 * construction priorities, taxes, treaties and (rarely, with a cause) war. Battles kill real
 * soldiers from real families; lost wars cost land; rulers die and are succeeded.
 */
public final class Politics {
    private Politics() {}

    public static void daily(WorldSim sim, long day) {
        Random r = sim.rng;
        for (Faction f : new ArrayList<>(sim.factions.values())) {
            if (f.destroyed || f.government == Faction.Government.OUTLAWS) continue;
            List<Settlement> lands = sim.settlementsOf(f);
            if (lands.isEmpty()) {
                f.destroyed = true;
                f.chronicle(day, "The realm is no more.");
                sim.log(day, f.name + " has fallen; no settlement flies its banner.");
                continue;
            }
            if (f.playerRealm) {
                f.agenda = Faction.Agenda.CONSOLIDATE; // a player's realm acts only through its player
                taxes(f);
                continue;
            }
            Person ruler = sim.person(f.ruler);
            if (ruler == null || !ruler.alive) succession(sim, f, day);
            ruler = sim.person(f.ruler);
            if (Math.floorMod(day + f.id.hashCode(), 4) == 0) updateAgenda(sim, f, ruler, lands, day);
            taxes(f);
            if (f.heir == null || sim.person(f.heir) == null || !sim.person(f.heir).alive) {
                Person h = chooseHeir(sim, f, day);
                f.heir = h == null ? null : h.id;
            }
        }
        diplomacy(sim, day, r);
        wars(sim, day, r);
        rebellions(sim, day, r);
    }

    static void taxes(Faction f) {
        if (f.agenda == Faction.Agenda.TAX) f.taxRate = Math.min(0.3, f.taxRate + 0.01);
        else f.taxRate = Math.max(0.08, f.taxRate - 0.005);
    }

    static void updateAgenda(WorldSim sim, Faction f, Person ruler, List<Settlement> lands, long day) {
        if (ruler == null) {
            f.agenda = Faction.Agenda.CONSOLIDATE;
            return;
        }
        double food = 0;
        int pop = 0;
        boolean threatened = false;
        for (Settlement s : lands) {
            food += s.stock.foodUnits();
            pop += s.residents.size();
            if (s.raidWarning > 0 || s.raidsSurvived > 0) threatened = true;
        }
        boolean atWar = false;
        for (WorldSim.War w : sim.wars) if (f.id.equals(w.attacker) || f.id.equals(w.defender)) atWar = true;
        Faction.Agenda old = f.agenda;
        if (food < pop * 3) f.agenda = Faction.Agenda.RECOVER;
        else if (atWar || (threatened && ruler.bravery < 0.55f)) f.agenda = Faction.Agenda.FORTIFY;
        else if ((ruler.ambition > 0.62f && ruler.bravery > 0.45f || ruler.greed > 0.72f && ruler.ambition > 0.5f) && strength(sim, f) > 4) f.agenda = Faction.Agenda.CONQUER;
        else if (ruler.ambition > 0.6f) f.agenda = Faction.Agenda.EXPAND;
        else if (ruler.greed > 0.65f) f.agenda = Faction.Agenda.TAX;
        else if (ruler.piety > 0.7f) f.agenda = Faction.Agenda.PIETY;
        else if (ruler.sociability > 0.6f || ruler.kindness > 0.6f) f.agenda = Faction.Agenda.TRADE;
        else f.agenda = Faction.Agenda.CONSOLIDATE;
        if (old != f.agenda) f.chronicle(day, f.rulerTitle(ruler) + " " + ruler.given + " turns to " + agendaText(f.agenda) + ".");
    }

    public static String agendaText(Faction.Agenda a) {
        return switch (a) {
            case CONSOLIDATE -> "ordering the realm";
            case FORTIFY -> "fortifying the borders";
            case EXPAND -> "settling new land";
            case CONQUER -> "thoughts of conquest";
            case TRADE -> "trade and friendship";
            case TAX -> "filling the treasury";
            case PIETY -> "works of faith";
            case RECOVER -> "feeding the hungry";
        };
    }

    public static int strength(WorldSim sim, Faction f) {
        int n = 0;
        for (Settlement s : sim.settlementsOf(f)) {
            for (Person p : sim.residents(s)) {
                if (p.profession == Profession.SOLDIER) n += 2;
                else if (p.profession == Profession.GUARD) n += 1;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ succession

    public static Person chooseHeir(WorldSim sim, Faction f, long day) {
        Person ruler = sim.person(f.ruler);
        List<Person> pool = new ArrayList<>();
        for (Settlement s : sim.settlementsOf(f)) for (Person p : sim.residents(s)) if (sim.isAdult(p, day)) pool.add(p);
        if (pool.isEmpty()) return null;
        switch (f.government) {
            case MONARCHY, LORDSHIP -> {
                if (ruler != null) {
                    Person eldest = null;
                    for (UUID cid : ruler.children) {
                        Person c = sim.person(cid);
                        if (c != null && c.alive && (eldest == null || c.birthDay < eldest.birthDay)) eldest = c;
                    }
                    if (eldest != null) return eldest;
                    Person spouse = sim.person(ruler.spouse);
                    if (spouse != null && spouse.alive) return spouse;
                }
                return pool.stream().max(Comparator.comparingInt(p -> p.prestige)).orElse(null);
            }
            case COUNCIL -> {
                return pool.stream().max(Comparator.comparingDouble(p -> p.prestige + sim.ageOf(p, day) * 0.3 + p.sociability * 10)).orElse(null);
            }
            case CLAN -> {
                return pool.stream().max(Comparator.comparingDouble(p -> p.prestige + p.bravery * 25 + (p.profession == Profession.SOLDIER ? 10 : 0))).orElse(null);
            }
            case REPUBLIC -> {
                return pool.stream().max(Comparator.comparingDouble(p -> p.coins + p.prestige * 3 + (p.profession == Profession.MERCHANT ? 30 : 0))).orElse(null);
            }
            case THEOCRACY -> {
                return pool.stream().max(Comparator.comparingDouble(p -> p.piety * 30 + p.prestige + (p.profession == Profession.PRIEST ? 25 : 0))).orElse(null);
            }
            default -> {
                return pool.stream().max(Comparator.comparingDouble(p -> p.bravery * 20 + p.prestige)).orElse(null);
            }
        }
    }

    public static void succession(WorldSim sim, Faction f, long day) {
        if (f.playerRealm) return;
        Person old = sim.person(f.ruler);
        if (old != null) {
            f.pastRulers.add(old.id);
            if (!old.alive) {
                sim.addNews(day, "ruler_death", f.rulerTitle(old) + " " + old.fullName() + " of " + f.name + " is dead.", sim.settlement(f.capital), 4,
                        "person", old.fullName(), "realm", f.name);
            }
            old.title = "";
        }
        Person heir = sim.person(f.heir);
        if (heir == null || !heir.alive || !sim.isAdult(heir, day)) heir = chooseHeir(sim, f, day);
        if (heir == null) {
            f.ruler = null;
            return;
        }
        // a strong-willed rival may dispute an untested heir
        Person rival = null;
        for (Settlement s : sim.settlementsOf(f)) {
            for (Person p : sim.residents(s)) {
                if (p != heir && sim.isAdult(p, day) && p.ambition > 0.82f && p.prestige > heir.prestige + 5 && sim.rng.nextFloat() < 0.5f) {
                    rival = p;
                }
            }
        }
        f.ruler = heir.id;
        f.heir = null;
        if (heir.profession != Profession.RULER) {
            heir.profession = Profession.RULER;
            heir.workplace = null;
        }
        heir.title = f.rulerTitle(heir) + " of " + f.name;
        heir.prestige += 10;
        heir.remember(day, "crowned", null, "became " + heir.title, 30);
        if (f.government == Faction.Government.MONARCHY || f.government == Faction.Government.CLAN) {
            NameSet ns = Content.names(heir.culture);
            if (heir.epithet.isEmpty() && sim.rng.nextFloat() < 0.5f) heir.epithet = ns.epithet(sim.rng);
        }
        f.chronicle(day, heir.fullName() + " became " + f.rulerTitle(heir) + " (" + f.government.succession + ").");
        sim.addNews(day, "succession", heir.fullName() + " is now " + f.rulerTitle(heir) + " of " + f.name + ".", sim.settlement(f.capital), 4,
                "person", heir.fullName(), "realm", f.name);
        Settlement home = sim.settlement(heir.settlement);
        if (home != null && !home.id.equals(f.capital) && sim.settlement(f.capital) == null) f.capital = home.id;
        if (rival != null) {
            Settlement seat = sim.settlement(rival.settlement);
            if (seat != null && !seat.id.equals(f.capital)) {
                Faction rebels = secede(sim, f, seat, rival, day, "disputed the succession of " + heir.given);
                declareWar(sim, f, rebels, seat, "a disputed succession", day);
            }
        }
    }

    // ------------------------------------------------------------------ diplomacy

    static void diplomacy(WorldSim sim, long day, Random r) {
        List<Faction> realms = new ArrayList<>();
        for (Faction f : sim.factions.values()) if (!f.destroyed && f.government != Faction.Government.OUTLAWS) realms.add(f);
        for (int i = 0; i < realms.size(); i++) {
            for (int j = i + 1; j < realms.size(); j++) {
                Faction a = realms.get(i), b = realms.get(j);
                double dist = distance(sim, a, b);
                // neighbours rub against each other; distant realms barely matter
                int drift = 0;
                if (dist < 600) drift -= (a.culture.equals(b.culture) ? 0 : 1);
                if (a.culture.equals(b.culture)) drift += 1;
                if (a.agenda == Faction.Agenda.TRADE || b.agenda == Faction.Agenda.TRADE) drift += 1;
                if (a.agenda == Faction.Agenda.CONQUER || b.agenda == Faction.Agenda.CONQUER) drift -= 1;
                if (r.nextFloat() < 0.5f) {
                    a.changeRelation(b.id, drift);
                    b.changeRelation(a.id, drift);
                }
                Faction.Stance st = a.stance(b.id);
                int rel = (a.relation(b.id) + b.relation(a.id)) / 2;
                if (st == Faction.Stance.WAR) continue;
                if (st == Faction.Stance.PEACE && rel > 25 && r.nextFloat() < 0.1f) {
                    setStance(a, b, Faction.Stance.TRADE);
                    news(sim, day, a.name + " and " + b.name + " have agreed a trade pact.", a, 3, "peace");
                } else if (st == Faction.Stance.TRADE && rel > 60 && r.nextFloat() < 0.05f) {
                    setStance(a, b, Faction.Stance.ALLIANCE);
                    news(sim, day, a.name + " and " + b.name + " have sworn an alliance.", a, 3, "peace");
                } else if ((st == Faction.Stance.TRADE || st == Faction.Stance.ALLIANCE) && rel < 0) {
                    setStance(a, b, Faction.Stance.PEACE);
                    news(sim, day, "The pact between " + a.name + " and " + b.name + " has lapsed amid bad feeling.", a, 2, "war");
                }
                if (sim.rules.wars && day > 12 && st == Faction.Stance.PEACE && dist < 1100) {
                    Faction aggressor = a.agenda == Faction.Agenda.CONQUER ? a : b.agenda == Faction.Agenda.CONQUER ? b : null;
                    // an old, bitter grudge can drive an expanding realm to war as well
                    if (aggressor == null && rel < -60) aggressor = a.agenda == Faction.Agenda.EXPAND ? a : b.agenda == Faction.Agenda.EXPAND ? b : null;
                    if (aggressor != null) {
                        Faction victim = aggressor == a ? b : a;
                        if (aggressor.relation(victim.id) < -35 && strength(sim, aggressor) > strength(sim, victim) * 1.15 + 1
                                && r.nextFloat() < 0.06f && !victim.playerRealm) {
                            Settlement target = nearestOf(sim, victim, aggressor);
                            if (target != null) declareWar(sim, aggressor, victim, target, causeFor(sim, aggressor, victim, target, r), day);
                        }
                    }
                }
            }
        }
    }

    static String causeFor(WorldSim sim, Faction a, Faction b, Settlement target, Random r) {
        String[] causes = {"claims on " + target.name, "an old grudge over the border near " + target.name,
                "the murder of an envoy", "tolls on the road through " + target.name, "an insult at the " + a.name + " court"};
        return causes[r.nextInt(causes.length)];
    }

    static void setStance(Faction a, Faction b, Faction.Stance s) {
        if (s == Faction.Stance.PEACE) {
            a.stances.remove(b.id);
            b.stances.remove(a.id);
        } else {
            a.stances.put(b.id, s);
            b.stances.put(a.id, s);
        }
    }

    static void news(WorldSim sim, long day, String text, Faction f, int imp, String type) {
        Settlement cap = sim.settlement(f.capital);
        sim.addNews(day, type, text, cap, imp, "realm", f.name);
    }

    static double distance(WorldSim sim, Faction a, Faction b) {
        double best = Double.MAX_VALUE;
        for (Settlement x : sim.settlementsOf(a)) {
            for (Settlement y : sim.settlementsOf(b)) {
                if (x.center == null || y.center == null) continue;
                best = Math.min(best, Math.sqrt(x.center.distSqr(y.center)));
            }
        }
        return best;
    }

    static Settlement nearestOf(WorldSim sim, Faction victim, Faction aggressor) {
        Settlement best = null;
        double bestD = Double.MAX_VALUE;
        for (Settlement v : sim.settlementsOf(victim)) {
            for (Settlement a : sim.settlementsOf(aggressor)) {
                double d = v.center.distSqr(a.center);
                if (d < bestD && !v.id.equals(victim.capital)) {
                    bestD = d;
                    best = v;
                }
            }
        }
        if (best == null) {
            List<Settlement> l = sim.settlementsOf(victim);
            if (!l.isEmpty()) best = l.get(0);
        }
        return best;
    }

    // ------------------------------------------------------------------ war

    public static void declareWar(WorldSim sim, Faction attacker, Faction defender, Settlement target, String cause, long day) {
        WorldSim.War w = new WorldSim.War();
        w.attacker = attacker.id;
        w.defender = defender.id;
        w.target = target.id;
        w.cause = cause;
        w.startDay = day;
        sim.wars.add(w);
        setStance(attacker, defender, Faction.Stance.WAR);
        attacker.changeRelation(defender.id, -30);
        defender.changeRelation(attacker.id, -40);
        String text = attacker.name + " has declared war on " + defender.name + " over " + cause + ".";
        attacker.chronicle(day, text);
        defender.chronicle(day, text);
        Settlement cap = sim.settlement(attacker.capital);
        sim.addNews(day, "war", text, cap != null ? cap : target, 4, "realm", attacker.name, "other_realm", defender.name);
        target.knownNews.put(sim.nextNewsId - 1, day);
        sim.hooks.announce(sim, target, text + " " + target.name + " lies in its path.", 3);
    }

    static void wars(WorldSim sim, long day, Random r) {
        for (WorldSim.War w : new ArrayList<>(sim.wars)) {
            if (!sim.wars.contains(w)) continue;
            Faction a = sim.faction(w.attacker), d = sim.faction(w.defender);
            Settlement target = sim.settlement(w.target);
            if (a == null || d == null || a.destroyed || d.destroyed || target == null || !d.id.equals(target.faction)) {
                if (a != null && d != null) setStance(a, d, Faction.Stance.PEACE);
                sim.wars.remove(w);
                continue;
            }
            a.warWeariness++;
            d.warWeariness++;
            if (Math.floorMod(day - w.startDay, 2) == 1) battle(sim, w, a, d, target, day, r);
            String peace = null;
            if (w.score >= 50) {
                peace = target.name + " is ceded to " + a.name + ". The war between " + a.name + " and " + d.name + " is over.";
                cede(sim, target, a, day);
            } else if (w.score <= -40) {
                int tribute = Math.min(a.treasury, 200);
                a.treasury -= tribute;
                d.treasury += tribute;
                peace = a.name + " sues for peace and pays " + tribute + " pennies in reparations to " + d.name + ".";
            } else if (a.warWeariness > 24 + r.nextInt(10) || d.warWeariness > 30 + r.nextInt(10)) {
                peace = a.name + " and " + d.name + " make a weary peace; the border stays where it was.";
            }
            if (peace != null) {
                setStance(a, d, Faction.Stance.PEACE);
                a.warWeariness = 0;
                d.warWeariness = 0;
                a.changeRelation(d.id, 10);
                d.changeRelation(a.id, 5);
                a.chronicle(day, peace);
                d.chronicle(day, peace);
                sim.addNews(day, "peace", peace, target, 4, "realm", a.name, "other_realm", d.name);
                sim.wars.remove(w);
            }
        }
    }

    static void battle(WorldSim sim, WorldSim.War w, Faction a, Faction d, Settlement target, long day, Random r) {
        List<Person> atk = fighters(sim, a, day);
        List<Person> def = fighters(sim, d, day);
        for (Person p : sim.residents(target)) if (sim.isAdult(p, day) && !def.contains(p) && p.bravery > 0.6f) def.add(p);
        if (atk.isEmpty()) {
            w.score -= 10;
            return;
        }
        double sa = 0, sd = 0;
        for (Person p : atk) sa += 1 + p.weaponTier * 0.4 + p.armorTier * 0.4 + p.bravery * 0.4;
        for (Person p : def) sd += 1 + p.weaponTier * 0.4 + p.armorTier * 0.4 + p.bravery * 0.4;
        sd *= target.hasUsable(BuildingType.WATCHTOWER) ? 1.3 : 1.0;
        sa *= 0.75 + r.nextDouble() * 0.5;
        sd *= 0.75 + r.nextDouble() * 0.5;
        boolean attackerWins = sa > sd;
        w.battles++;
        w.score += attackerWins ? 15 + r.nextInt(10) : -(10 + r.nextInt(10));
        int lossA = attackerWins ? r.nextInt(2) : 1 + r.nextInt(2);
        int lossD = attackerWins ? 1 + r.nextInt(2) : r.nextInt(2);
        String field = "the field of " + target.name;
        List<String> dead = new ArrayList<>();
        dead.addAll(casualties(sim, atk, lossA, day, "fell at the battle of " + field));
        dead.addAll(casualties(sim, def, lossD, day, "fell defending " + target.name));
        if (attackerWins) {
            for (Building b : target.buildings) if (b.state == Building.State.COMPLETE && r.nextFloat() < 0.1f) b.state = Building.State.DAMAGED;
            target.morale = Math.max(0, target.morale - 0.15);
        }
        String text = "Battle at " + target.name + ": " + (attackerWins ? a.name : d.name) + " carried the day."
                + (dead.isEmpty() ? "" : " Fallen: " + String.join(", ", dead) + ".");
        sim.addNews(day, "battle", text, target, 3, "realm", a.name, "other_realm", d.name);
        for (Person p : atk) if (p.alive) p.remember(day, "battle", null, "fought at the battle of " + target.name, attackerWins ? 5 : -15);
        for (Person p : def) if (p.alive) p.remember(day, "battle", null, "fought to defend " + target.name, attackerWins ? -15 : 10);
    }

    static List<String> casualties(WorldSim sim, List<Person> side, int n, long day, String cause) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n && !side.isEmpty(); i++) {
            Person p = side.remove(sim.rng.nextInt(side.size()));
            if (sim.rng.nextFloat() < 0.4f) {
                p.health = Math.max(0.15f, p.health - 0.5f);
                p.remember(day, "wounded", null, "was wounded in battle; the wound still aches", -15);
                continue;
            }
            out.add(p.fullName());
            sim.kill(p, day, cause);
        }
        return out;
    }

    static List<Person> fighters(WorldSim sim, Faction f, long day) {
        List<Person> out = new ArrayList<>();
        for (Settlement s : sim.settlementsOf(f)) {
            for (Person p : sim.residents(s)) {
                if (!sim.isAdult(p, day)) continue;
                if (p.profession == Profession.SOLDIER || p.profession == Profession.GUARD) out.add(p);
            }
        }
        // levies: when the realm lacks soldiers, brave labourers are called up
        if (out.size() < 3) {
            for (Settlement s : sim.settlementsOf(f)) {
                for (Person p : sim.residents(s)) {
                    if (out.size() >= 4) break;
                    if (sim.isAdult(p, day) && p.profession == Profession.LABOURER && p.bravery > 0.5f) out.add(p);
                }
            }
        }
        return out;
    }

    static void cede(WorldSim sim, Settlement s, Faction to, long day) {
        Faction from = sim.faction(s.faction);
        s.faction = to.id;
        s.chronicle(day, s.name + " now flies the banner of " + to.name + ".");
        for (Person p : sim.residents(s)) {
            p.loyalty = Math.max(0.05f, p.loyalty - 0.4f);
            p.remember(day, "conquered", null, "saw " + s.name + " taken by " + to.name, -30);
        }
        if (from != null && s.id.equals(from.capital)) {
            List<Settlement> rest = sim.settlementsOf(from);
            from.capital = rest.isEmpty() ? null : rest.get(0).id;
        }
    }

    public static Faction secede(WorldSim sim, Faction from, Settlement seat, Person leader, long day, String why) {
        Faction f = new Faction(UUID.randomUUID());
        NameSet ns = Content.names(seat.culture);
        f.name = ns.realmName(seat.name, sim.rng);
        f.culture = seat.culture;
        f.government = from.government == Faction.Government.MONARCHY ? Faction.Government.LORDSHIP : from.government;
        f.capital = seat.id;
        f.ruler = leader.id;
        f.foundedDay = day;
        f.bannerColor = sim.rng.nextInt(16);
        f.bannerAccent = sim.rng.nextInt(16);
        leader.profession = Profession.RULER;
        leader.title = f.rulerTitle(leader) + " of " + f.name;
        seat.faction = f.id;
        f.relations.put(from.id, -60);
        from.relations.put(f.id, -60);
        sim.factions.put(f.id, f);
        String text = leader.fullName() + " of " + seat.name + " " + why + " and proclaimed " + f.name + ".";
        f.chronicle(day, text);
        from.chronicle(day, text);
        sim.addNews(day, "rebellion", text, seat, 4, "person", leader.fullName(), "realm", f.name, "other_realm", from.name);
        return f;
    }

    static void rebellions(WorldSim sim, long day, Random r) {
        for (Settlement s : new ArrayList<>(sim.settlements.values())) {
            if (s.kind != Settlement.Kind.SETTLEMENT || s.faction == null) continue;
            Faction f = sim.faction(s.faction);
            if (f == null || s.id.equals(f.capital) || f.playerRealm) continue;
            List<Person> people = sim.residents(s);
            if (people.size() < 6) continue;
            double loyalty = 0;
            for (Person p : people) loyalty += p.loyalty;
            loyalty /= people.size();
            // loyalty heals slowly under a fair ruler, decays under heavy taxes
            for (Person p : people) p.loyalty = (float) Math.max(0, Math.min(1, p.loyalty + (f.taxRate > 0.2 ? -0.01 : 0.005)));
            if (loyalty > 0.25 || r.nextFloat() > 0.05f) continue;
            Person leader = people.stream().filter(p -> sim.isAdult(p, day)).max(Comparator.comparingDouble(p -> p.ambition + p.bravery)).orElse(null);
            if (leader == null) continue;
            Faction rebels = secede(sim, f, s, leader, day, "rose against " + f.name);
            if (sim.rules.wars) declareWar(sim, f, rebels, s, "the rebellion of " + s.name, day);
        }
    }
}
