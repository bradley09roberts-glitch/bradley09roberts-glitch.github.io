package com.longnight.sim;

import com.longnight.content.Content;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Outlaw bands and the raids they launch. Raids are announced in stages (rumours, scouts,
 * the bell) so a careful player always has warning. If the target is embodied (a player is
 * near), the raiders are spawned as real people; otherwise the clash is resolved abstractly.
 * Every casualty is a real person on one side or the other.
 */
public final class Threats {
    private Threats() {}

    public static void daily(WorldSim sim, long day) {
        Random r = sim.rng;
        for (Settlement camp : new ArrayList<>(sim.settlements.values())) {
            if (camp.kind != Settlement.Kind.BANDIT_CAMP) continue;
            List<Person> band = sim.residents(camp);
            if (band.isEmpty()) {
                camp.kind = Settlement.Kind.RUIN;
                camp.chronicle(day, "The outlaw camp lies empty.");
                sim.addNews(day, "bandit_camp", "The outlaws of " + camp.name + " are no more.", camp, 2);
                continue;
            }
            camp.hunger += 1 + band.size() / 4;
            // the band slowly recruits desperate souls
            if (band.size() < 4 && r.nextFloat() < 0.15f) recruit(sim, camp, day);
            if (!sim.rules.raids || day < sim.rules.firstRaidDay - sim.rules.raidWarningDays) continue;
            if (camp.hunger < 8 || pendingFrom(sim, camp)) continue;
            Settlement target = chooseTarget(sim, camp);
            if (target == null) continue;
            int fighters = 0;
            for (Person p : band) if (sim.isAdult(p, day)) fighters++;
            if (fighters < 2) continue;
            target.raidSource = camp.id;
            target.raidDay = Math.max(day + sim.rules.raidWarningDays, sim.rules.firstRaidDay);
            target.raidWarning = 1;
            target.raidStrength = fighters;
            camp.hunger = 0;
            String text = "Travellers whisper of outlaws from " + camp.name + " watching the roads near " + target.name + ".";
            sim.addNews(day, "raid_warning", text, target, 2, "other_settlement", camp.name,
                    "days", String.valueOf(target.raidDay - day));
            sim.hooks.announce(sim, target, "Rumours reach " + target.name + ": outlaws have been seen watching the roads.", 1);
        }
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT || s.raidSource == null) continue;
            Settlement camp = sim.settlement(s.raidSource);
            if (camp == null || camp.kind != Settlement.Kind.BANDIT_CAMP) {
                clearRaid(s);
                continue;
            }
            long left = s.raidDay - day;
            if (left == 1 && s.raidWarning < 2) {
                s.raidWarning = 2;
                boolean tower = s.hasUsable(BuildingType.WATCHTOWER);
                String text = tower ? "The watch on " + s.name + "'s tower spotted outlaw scouts and campfire smoke. They will come tomorrow at dusk."
                        : "Outlaw scouts were seen near " + s.name + ". Folk fear an attack within a day.";
                sim.addNews(day, "raid_warning", text, s, 3, "other_settlement", camp.name, "days", "1");
                sim.hooks.announce(sim, s, text, 2);
                for (Person p : sim.residents(s)) p.safety = Math.max(0.1f, p.safety - 0.3f);
            }
            if (left <= 0) {
                s.raidWarning = 3;
                if (!sim.hooks.spawnRaid(sim, s, camp, s.raidStrength)) {
                    resolveAbstract(sim, s, camp, day);
                }
                // an embodied raid is resolved by what happens in the world; the hook calls endPhysicalRaid
            }
        }
    }

    static boolean pendingFrom(WorldSim sim, Settlement camp) {
        for (Settlement s : sim.settlements.values()) if (camp.id.equals(s.raidSource)) return true;
        return false;
    }

    static Settlement chooseTarget(WorldSim sim, Settlement camp) {
        Settlement best = null;
        double bestScore = 0;
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT || s.center == null || s.residents.size() < 4 || s.raidSource != null) continue;
            double dist = Math.sqrt(s.center.distSqr(camp.center));
            if (dist > 1000) continue;
            double wealth = s.stock.foodUnits() + s.treasury * 0.2 + s.stock.get(Commodity.TOOLS) * 4;
            double defence = defence(sim, s) + 1;
            double score = wealth / defence / (1 + dist / 300);
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    public static double defence(WorldSim sim, Settlement s) {
        double d = 0;
        for (Person p : sim.residents(s)) {
            if (!sim.isAdult(p, sim.lastDay)) continue;
            if (p.profession == Profession.GUARD || p.profession == Profession.SOLDIER) {
                d += 1.6 + p.weaponTier * 0.6 + p.armorTier * 0.5 + p.bravery * 0.5;
            } else {
                d += 0.25 * p.bravery;
            }
        }
        if (s.hasUsable(BuildingType.WATCHTOWER)) d *= 1.25;
        if (s.hasUsable(BuildingType.BARRACKS)) d *= 1.15;
        return d;
    }

    static double attack(WorldSim sim, Settlement camp) {
        double a = 0;
        for (Person p : sim.residents(camp)) if (sim.isAdult(p, sim.lastDay)) a += 1.4 + p.bravery * 0.6 + p.weaponTier * 0.4;
        return a;
    }

    /** Resolve a raid on a settlement nobody is watching. */
    public static void resolveAbstract(WorldSim sim, Settlement s, Settlement camp, long day) {
        Random r = sim.rng;
        double def = defence(sim, s) * (0.7 + r.nextDouble() * 0.6);
        double atk = attack(sim, camp) * (0.7 + r.nextDouble() * 0.6);
        boolean repelled = def >= atk;
        List<Person> defenders = sim.residents(s);
        defenders.sort((a, b) -> Boolean.compare(isFighter(b), isFighter(a)));
        int defLoss = repelled ? r.nextInt(2) : 1 + r.nextInt(3);
        int atkLoss = repelled ? Math.max(1, (int) Math.round(sim.residents(camp).size() * (0.3 + r.nextDouble() * 0.3))) : r.nextInt(2);
        List<String> fallen = new ArrayList<>();
        for (int i = 0; i < defLoss && i < defenders.size(); i++) {
            Person p = defenders.get(i);
            if (!sim.isAdult(p, day)) continue;
            if (r.nextFloat() < 0.5f) {
                p.health = Math.max(0.2f, p.health - 0.4f);
                p.remember(day, "wounded", null, "was wounded fighting outlaws", -20);
                continue;
            }
            fallen.add(p.fullName());
            sim.kill(p, day, "slain by outlaws of " + camp.name);
        }
        List<Person> band = sim.residents(camp);
        for (int i = 0; i < atkLoss && !band.isEmpty(); i++) {
            Person b = band.remove(r.nextInt(band.size()));
            sim.kill(b, day, "killed raiding " + s.name);
        }
        String text;
        if (repelled) {
            text = "The folk of " + s.name + " drove off outlaws from " + camp.name + ".";
            s.morale = Math.min(1, s.morale + 0.1);
            s.raidsSurvived++;
            for (Person p : sim.residents(s)) {
                if (isFighter(p)) {
                    p.prestige += 2;
                    p.remember(day, "victory", null, "helped drive the outlaws from " + s.name, 15);
                }
            }
        } else {
            double share = 0.2 + r.nextDouble() * 0.2;
            for (Commodity c : Commodity.values()) {
                double take = s.stock.get(c) * share;
                if (take > 0) s.stock.take(c, take);
            }
            int coins = (int) (s.treasury * share);
            s.treasury -= coins;
            camp.treasury += coins;
            text = "Outlaws from " + camp.name + " plundered " + s.name + ".";
            s.morale = Math.max(0, s.morale - 0.2);
            s.raidsSurvived++;
            for (Building b : s.buildings) {
                if (b.state == Building.State.COMPLETE && r.nextFloat() < 0.15f) {
                    b.state = Building.State.DAMAGED;
                    break;
                }
            }
            for (Person p : sim.residents(s)) p.remember(day, "plundered", null, "saw " + s.name + " plundered by outlaws", -25);
        }
        if (!fallen.isEmpty()) text += " Fallen: " + String.join(", ", fallen) + ".";
        sim.addNews(day, "raid", text, s, 3, "other_settlement", camp.name);
        s.chronicle(day, text);
        sim.hooks.announce(sim, s, text, 2);
        clearRaid(s);
    }

    /** Called by the world when an embodied raid has ended (raiders dead, fled, or dawn). */
    public static void endPhysicalRaid(WorldSim sim, Settlement s, Settlement camp, long day, boolean repelled, int looted) {
        String text = repelled ? "Outlaws from " + (camp == null ? "the wilds" : camp.name) + " were beaten back from " + s.name + "."
                : "Outlaws from " + (camp == null ? "the wilds" : camp.name) + " ransacked part of " + s.name + ".";
        if (repelled) s.morale = Math.min(1, s.morale + 0.1);
        else s.morale = Math.max(0, s.morale - 0.15);
        s.raidsSurvived++;
        sim.addNews(day, "raid", text, s, 3, "other_settlement", camp == null ? "the wilds" : camp.name);
        s.chronicle(day, text);
        clearRaid(s);
    }

    public static void clearRaid(Settlement s) {
        s.raidSource = null;
        s.raidDay = -1;
        s.raidWarning = 0;
        s.raidStrength = 0;
    }

    static boolean isFighter(Person p) {
        return p.profession == Profession.GUARD || p.profession == Profession.SOLDIER;
    }

    static void recruit(WorldSim sim, Settlement camp, long day) {
        // an unhappy soul somewhere turns outlaw; otherwise a drifter from the wilds
        Person recruit = null;
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT) continue;
            for (Person p : sim.residents(s)) {
                if (sim.isAdult(p, day) && p.mood() < -0.45f && p.kindness < 0.35f && p.spouse == null && sim.rng.nextFloat() < 0.3f) {
                    recruit = p;
                    break;
                }
            }
            if (recruit != null) {
                sim.addNews(day, "outlaw", recruit.fullName() + " left " + s.name + " in bitterness and took to the wilds.", s, 2,
                        "person", recruit.fullName());
                break;
            }
        }
        if (recruit == null) {
            String culture = Content.culture(camp.culture).id;
            recruit = sim.createPerson(culture, null, day - (long) ((sim.rules.adultAge + 2 + sim.rng.nextInt(20)) * sim.rules.daysPerYear), sim.rng);
            recruit.kindness *= 0.5f;
        }
        sim.join(recruit, camp);
        recruit.profession = Profession.BANDIT;
        recruit.weaponTier = Math.max(recruit.weaponTier, 1);
        recruit.remember(day, "outlaw", null, "joined the outlaws of " + camp.name, -5);
    }

    /** Outlaws may rob a caravan whose road passes near their camp. Returns true if the caravan was lost. */
    public static boolean ambush(WorldSim sim, WorldSim.Caravan cv, Settlement from, Settlement to, long day) {
        if (from.center == null || to.center == null) return false;
        for (Settlement camp : sim.settlements.values()) {
            if (camp.kind != Settlement.Kind.BANDIT_CAMP || camp.residents.size() < 2) continue;
            double d = distToSegment(camp.center.getX(), camp.center.getZ(), from.center.getX(), from.center.getZ(),
                    to.center.getX(), to.center.getZ());
            if (d > 250) continue;
            if (sim.rng.nextFloat() > 0.12f) continue;
            for (var g : cv.goods.entrySet()) camp.stock.add(g.getKey(), g.getValue());
            camp.hunger = Math.max(0, camp.hunger - 4);
            Person leader = sim.person(cv.leader);
            String who = "";
            if (leader != null && leader.alive) {
                if (sim.rng.nextFloat() < 0.4f) {
                    sim.kill(leader, day, "killed when outlaws ambushed a caravan");
                    who = " " + leader.fullName() + " was killed.";
                } else {
                    leader.activity = "";
                    leader.remember(day, "ambushed", null, "was robbed by outlaws on the road", -25);
                }
            }
            String text = "Outlaws of " + camp.name + " ambushed a caravan from " + from.name + " bound for " + to.name + "." + who;
            sim.addNews(day, "raid", text, from, 3, "other_settlement", camp.name);
            return true;
        }
        return false;
    }

    static double distToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double len = vx * vx + vz * vz;
        double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len));
        double cx = ax + t * vx, cz = az + t * vz;
        return Math.sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz));
    }
}
