package com.longnight.game;

import com.longnight.content.Content;
import com.longnight.sim.Building;
import com.longnight.sim.Commodity;
import com.longnight.sim.Construction;
import com.longnight.sim.Economy;
import com.longnight.sim.Faction;
import com.longnight.sim.NewsItem;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * What people say. Generated from their actual state (mood, memories, work, family, what news has
 * reached them) with hand-written phrasing; no external AI service is involved.
 */
public final class Dialogue {
    private Dialogue() {}

    static Random rng(Person x, long day) {
        return new Random(x.id.getMostSignificantBits() ^ day * 31);
    }

    public static String greeting(WorldSim sim, Person x, ServerPlayer p, Settlement s, long day) {
        Person.Relation rel = x.relate(p.getUUID());
        rel.familiarity = Math.min(100, rel.familiarity + 2);
        int rep = s == null ? 0 : s.reputationOf(p.getUUID());
        int opinion = rel.opinion + rep / 2;
        Random r = rng(x, day);
        if (x.profession == com.longnight.sim.Profession.BANDIT) {
            return pick(r, "Keep walking, stranger, while you still can.", "Coin or blood. Your choice.", "You've the look of someone who'll be missed. Shame.");
        }
        if (s != null && s.raidWarning >= 2) return pick(r, "Outlaws! They'll be on us by dusk. Have you a blade?", "Get inside, stranger, there's raiders coming!");
        if (x.mood() < -0.45f) {
            Person.Memory worst = worstMemory(x);
            if (worst != null && worst.weight() <= -25) return "Forgive me. I " + worst.text() + ", and I can't think of much else.";
            return pick(r, "Leave me be.", "These are hard days.", "What do you want?");
        }
        if (opinion >= 40) return pick(r, "Well met again, friend!", "Ah, it's you! Good to see a kind face.", "Our friend returns. What can I do for you?");
        if (opinion <= -30) return pick(r, "You again. Say your piece and go.", "I know what you've done here.", "Keep your hands where I can see them.");
        String[] base = {"Good day to you, traveller.", "Hail, stranger. You're not from " + (s == null ? "around here" : s.name) + ".",
                "Light keep you.", "Well met.", "Mind the mud, the road's poor this season."};
        if (rel.familiarity > 10) base = new String[]{"Back again? Welcome.", "Hello again.", "Good to see you, traveller."};
        return pick(r, base);
    }

    public static String retell(WorldSim sim, Person x, NewsItem n, long day) {
        String template = Content.rumour(n.type(), rng(x, n.id()));
        String body = n.retell(template);
        long ago = day - n.day();
        String when = ago <= 0 ? "" : ago == 1 ? " That was yesterday." : " That was " + ago + " days ago.";
        if (!body.equals(n.text())) return body;
        String lead = pick(rng(x, n.id() + 7), "I heard that ", "They say ", "Word is that ", "A traveller told us that ");
        return lead + lowerFirst(n.text()) + when;
    }

    static String lowerFirst(String s) {
        if (s.isEmpty()) return s;
        // keep proper names capitalised: only lower common sentence starters
        if (s.startsWith("The ") || s.startsWith("A ") || s.startsWith("An ") || s.startsWith("Outlaw") || s.startsWith("Food ")
                || s.startsWith("Travellers ") || s.startsWith("Stars ") || s.startsWith("Battle ")) {
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
        return s;
    }

    public static String work(WorldSim sim, Person x, Settlement s, long day) {
        if (!sim.isAdult(x, day)) {
            Person m = sim.person(x.mentor);
            if (m != null) return "I'm learning the " + m.profession.displayName.toLowerCase() + "'s trade from " + m.given + ". One day I'll be better than them!";
            return "I'm too young for work, says my mother. I'm not.";
        }
        StringBuilder sb = new StringBuilder();
        float skill = x.skill(x.profession);
        String level = skill > 75 ? "a master" : skill > 50 ? "skilled" : skill > 25 ? "competent" : "still learning";
        switch (x.profession) {
            case LABOURER -> sb.append("I take what work there is: hauling, digging, helping the builders. ");
            case RULER -> sb.append("I rule. It is less glorious than the songs. ");
            default -> sb.append("I'm a ").append(x.profession.displayName.toLowerCase()).append(", and ").append(level).append(" at it. ");
        }
        if (!x.activity.isEmpty()) sb.append("Lately I've been ").append(x.activity).append(". ");
        if (!x.blocked.isEmpty()) sb.append(x.blocked).append(" ");
        if (s != null) {
            for (Building b : s.buildings) {
                if (b.underConstruction() && !b.problem.isEmpty()) {
                    sb.append("The ").append(b.type.displayName.toLowerCase()).append(" is stalled; we still need ")
                            .append(Construction.remainingBill(s, b)).append(". Bring materials to the storehouse if you'd help.");
                    return sb.toString();
                }
            }
            List<Commodity> short_ = Economy.shortages(sim, s);
            if (!short_.isEmpty()) {
                sb.append("We're short of ").append(short_.get(0).displayName.toLowerCase());
                if (short_.size() > 1) sb.append(" and ").append(short_.get(1).displayName.toLowerCase());
                sb.append("; the storehouse pays well for it.");
            }
        }
        return sb.toString().trim();
    }

    public static String family(WorldSim sim, Person x, long day) {
        List<String> parts = new ArrayList<>();
        Person sp = sim.person(x.spouse);
        if (sp != null) parts.add("married to " + sp.fullName());
        else {
            for (Person.Memory m : x.memories) {
                if (m.kind().equals("bereaved") && m.text().contains("wife") || m.text().contains("husband")) {
                    parts.add("widowed");
                    break;
                }
            }
        }
        Person court = sim.person(x.courting);
        if (court != null) parts.add("courting " + court.given);
        List<String> kids = new ArrayList<>();
        List<String> lost = new ArrayList<>();
        for (UUID cid : x.children) {
            Person c = sim.person(cid);
            if (c == null) continue;
            if (c.alive) kids.add(c.given + " (" + (int) sim.ageOf(c, day) + ")");
            else lost.add(c.given);
        }
        if (!kids.isEmpty()) parts.add("children: " + String.join(", ", kids));
        if (!lost.isEmpty()) parts.add("mourning " + String.join(", ", lost));
        Person mo = sim.person(x.mother), fa = sim.person(x.father);
        if (mo != null || fa != null) {
            String ps = (fa != null ? fa.given + (fa.alive ? "" : " (dead)") : "") + (mo != null && fa != null ? " and " : "")
                    + (mo != null ? mo.given + (mo.alive ? "" : " (dead)") : "");
            parts.add("child of " + ps);
        }
        if (parts.isEmpty()) return x.given + " has no family to speak of.";
        return x.given + ": " + String.join("; ", parts) + ".";
    }

    public static String life(WorldSim sim, Person x, ServerPlayer p, Settlement s, long day) {
        StringBuilder sb = new StringBuilder();
        sb.append(switch (x.moodWord()) {
            case "content" -> "Life is good, thank the " + faithWord(x) + ". ";
            case "at ease" -> "Can't complain, and I try not to. ";
            case "unsettled" -> "Well enough, I suppose. ";
            case "unhappy" -> "Not well, if you want the truth. ";
            default -> "I don't know how much more I can bear. ";
        });
        if (x.food < 0.4f) sb.append("We've been going hungry. ");
        if (x.home == null) sb.append("I've no roof of my own. ");
        if (x.illnessDays > 0) sb.append("And this fever won't leave me. ");
        Person.Memory best = bestRecent(x);
        if (best != null) sb.append("Still, I ").append(best.text()).append(". ");
        // opinions of others
        UUID friend = null, rival = null;
        int fo = 39, ro = -39;
        for (var e : x.relations.entrySet()) {
            if (sim.person(e.getKey()) == null) continue;
            if (e.getValue().opinion > fo) {
                fo = e.getValue().opinion;
                friend = e.getKey();
            }
            if (e.getValue().opinion < ro) {
                ro = e.getValue().opinion;
                rival = e.getKey();
            }
        }
        if (friend != null && !friend.equals(x.spouse)) sb.append(sim.person(friend).given).append(" is a true friend to me. ");
        if (rival != null) sb.append("And I'll not share a table with ").append(sim.person(rival).given).append(". ");
        if (s != null && s.faction != null) {
            Faction f = sim.faction(s.faction);
            Person ruler = f == null ? null : sim.person(f.ruler);
            if (ruler != null && ruler != x) {
                int op = x.opinionOf(ruler.id);
                if (f.taxRate > 0.2) sb.append("The ").append(f.rulerTitle(ruler).toLowerCase()).append("'s taxes bleed us dry. ");
                else if (x.loyalty < 0.3f) sb.append("I owe nothing to ").append(f.name).append(". ");
                else if (op > 30) sb.append(ruler.given).append(" is a fair ruler. ");
            }
        }
        return sb.toString().trim();
    }

    static String faithWord(Person x) {
        return switch (x.faith) {
            case "hearth" -> "Hearth";
            case "sun" -> "Sun";
            case "tide" -> "Drowned Mother";
            case "grove" -> "Grove";
            case "ancestors" -> "Forefathers";
            case "stars" -> "stars";
            case "ember" -> "embers";
            default -> "gods";
        };
    }

    static Person.Memory worstMemory(Person x) {
        Person.Memory w = null;
        for (Person.Memory m : x.memories) if (w == null || m.weight() < w.weight()) w = m;
        return w;
    }

    static Person.Memory bestRecent(Person x) {
        Person.Memory b = null;
        for (int i = x.memories.size() - 1; i >= Math.max(0, x.memories.size() - 8); i--) {
            Person.Memory m = x.memories.get(i);
            if (m.weight() >= 10 && (b == null || m.weight() > b.weight())) b = m;
        }
        return b;
    }

    public static String temperament(Person x) {
        List<String> t = new ArrayList<>();
        if (x.bravery > 0.7f) t.add("brave");
        else if (x.bravery < 0.3f) t.add("timid");
        if (x.diligence > 0.7f) t.add("hard-working");
        else if (x.diligence < 0.3f) t.add("idle");
        if (x.sociability > 0.7f) t.add("sociable");
        else if (x.sociability < 0.3f) t.add("solitary");
        if (x.ambition > 0.7f) t.add("ambitious");
        if (x.piety > 0.7f) t.add("devout");
        else if (x.piety < 0.25f) t.add("sceptical");
        if (x.kindness > 0.7f) t.add("kind");
        else if (x.kindness < 0.3f) t.add("cruel");
        if (x.greed > 0.7f) t.add("greedy");
        return t.isEmpty() ? "even-tempered" : String.join(", ", t);
    }

    static String pick(Random r, String... s) {
        return s[r.nextInt(s.length)];
    }
}
