package com.longnight.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * How word travels. Witnesses know at once; travellers carry gossip to neighbouring settlements;
 * caravans carry everything their home town knew (see Caravans); royal messengers carry grave
 * news (wars, deaths of rulers) across a realm and to its allies. Nobody knows everything.
 */
public final class NewsSystem {
    private NewsSystem() {}

    public static void daily(WorldSim sim, long day) {
        Random r = sim.rng;
        List<Settlement> all = new ArrayList<>();
        for (Settlement s : sim.settlements.values()) if (s.kind != Settlement.Kind.RUIN && s.center != null) all.add(s);
        // travellers between neighbours
        for (Settlement a : all) {
            for (Settlement b : all) {
                if (a == b) continue;
                double dist = Math.sqrt(a.center.distSqr(b.center));
                if (dist > 700) continue;
                double chance = 0.35 * (1 - dist / 700) + (a.hasUsable(BuildingType.TAVERN) ? 0.15 : 0);
                for (Map.Entry<Long, Long> e : new ArrayList<>(a.knownNews.entrySet())) {
                    if (b.knownNews.containsKey(e.getKey()) || e.getValue() >= day) continue;
                    NewsItem n = sim.newsById(e.getKey());
                    if (n == null || day - n.day() > 40 || n.importance() < 2) continue;
                    if (r.nextDouble() < chance) b.knownNews.put(n.id(), day);
                }
            }
        }
        // messengers within realms and to allies
        for (NewsItem n : sim.news) {
            if (n.importance() < 4 || day - n.day() > 10 || n.origin() == null) continue;
            Settlement origin = sim.settlement(n.origin());
            if (origin == null || origin.faction == null) continue;
            Faction f = sim.faction(origin.faction);
            for (Settlement s : all) {
                if (s.knownNews.containsKey(n.id())) continue;
                boolean same = origin.faction.equals(s.faction);
                boolean ally = f != null && s.faction != null && f.stance(s.faction) == Faction.Stance.ALLIANCE;
                if ((same && day - n.day() >= 1) || (ally && day - n.day() >= 2)) s.knownNews.put(n.id(), day);
            }
        }
        // forget very old gossip
        for (Settlement s : all) {
            while (s.knownNews.size() > 120) {
                Long first = s.knownNews.keySet().iterator().next();
                s.knownNews.remove(first);
            }
        }
    }

    /** News this person has heard, newest first. */
    public static List<NewsItem> knownBy(WorldSim sim, Person p, long day, int max) {
        List<NewsItem> out = new ArrayList<>();
        Settlement s = sim.settlement(p.settlement);
        if (s == null) return out;
        List<Map.Entry<Long, Long>> entries = new ArrayList<>(s.knownNews.entrySet());
        for (int i = entries.size() - 1; i >= 0 && out.size() < max; i--) {
            NewsItem n = sim.newsById(entries.get(i).getKey());
            if (n == null) continue;
            long learned = entries.get(i).getValue();
            long delay = n.importance() >= 3 ? 0 : (p.sociability < 0.3f ? 2 : p.sociability < 0.6f ? 1 : 0);
            if (learned + delay > day) continue;
            if (day - n.day() > 30) continue;
            out.add(n);
        }
        return out;
    }
}
