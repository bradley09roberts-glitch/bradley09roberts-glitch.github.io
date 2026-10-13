package com.longnight.game;

import com.longnight.block.GravestoneBlockEntity;
import com.longnight.content.Content;
import com.longnight.content.Culture;
import com.longnight.sim.Building;
import com.longnight.sim.Calendar;
import com.longnight.sim.Commodity;
import com.longnight.sim.Construction;
import com.longnight.sim.Economy;
import com.longnight.sim.Faction;
import com.longnight.sim.NewsItem;
import com.longnight.sim.NewsSystem;
import com.longnight.sim.Person;
import com.longnight.sim.Politics;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * Chat-based interface (works on servers with vanilla-compatible clients and in tests). Every
 * button is a clickable command, so every action is also scriptable and testable.
 */
public final class Ui {
    private Ui() {}

    public static void msg(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    public static MutableComponent hint(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    static MutableComponent title(String text) {
        return Component.literal("== " + text + " ==").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    public static MutableComponent button(String label, String command, String hover) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    static long day(ServerPlayer p) {
        return WorldSim.dayOf(p.serverLevel().getServer().overworld());
    }

    // ------------------------------------------------------------------ settlement

    /** "5 blocks to the north-east" from the player's point of view (or "right here"). */
    public static String where(ServerPlayer p, net.minecraft.core.BlockPos pos) {
        double dx = pos.getX() + 0.5 - p.getX(), dz = pos.getZ() + 0.5 - p.getZ();
        int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        return d <= 1 ? "right here" : d + " blocks to the " + Journal.compass(dx, dz);
    }

    public static void settlement(ServerPlayer p, WorldSim sim, Settlement s) {
        long day = day(p);
        Culture c = Content.culture(s.culture);
        Faction f = sim.faction(s.faction);
        Person ruler = f == null ? null : sim.person(f.ruler);
        p.sendSystemMessage(title(s.name));
        msg(p, s.tier.displayName + " of the " + c.name + ". Founded in year " + Calendar.year(s.foundedDay, sim.rules.daysPerYear)
                + ". " + s.residents.size() + " people, housing for " + s.housingCapacity() + ".");
        if (f != null) {
            String rulerText = ruler != null ? f.rulerTitle(ruler) + " " + ruler.fullName() : "an empty throne";
            if (f.playerRealm && f.playerRuler != null) rulerText = "a player-lord";
            msg(p, "Realm: " + f.name + " (" + f.government.realmWord.toLowerCase() + "), ruled by " + rulerText + ". The ruler's mind turns to "
                    + Politics.agendaText(f.agenda) + ".");
        } else msg(p, "Beholden to no realm.");
        double foodDays = s.stock.foodUnits() / Math.max(1, s.residents.size());
        msg(p, "Food in store: " + (int) foodDays + " days. Treasury: " + Coins.format(s.treasury) + ". Mood: " + moodText(s.morale) + ".");
        for (Building b : s.buildings) {
            if (!b.underConstruction()) continue;
            int pct = b.total == 0 ? 0 : b.progress * 100 / b.total;
            String line = " - Building a " + b.type.displayName.toLowerCase() + ": " + pct + "%";
            if (!b.problem.isEmpty()) line += " (" + b.problem + ")";
            msg(p, line);
        }
        if (s.raidWarning > 0) p.sendSystemMessage(Component.literal(" ! " + threatText(s, sim)).withStyle(ChatFormatting.RED));
        int rep = s.reputationOf(p.getUUID());
        String standing = s.citizenPlayers.contains(p.getUUID()) ? "You are a citizen here." : "Your standing here: " + standingWord(rep) + ".";
        msg(p, standing);
        String id = s.id.toString();
        p.sendSystemMessage(Component.empty()
                .append(button("Market", "/longnight market " + id, "Prices and goods for sale"))
                .append(" ").append(button("Notices", "/longnight notices " + id, "News and needs"))
                .append(" ").append(button("People", "/longnight people " + id, "Who lives here"))
                .append(" ").append(button("Chronicle", "/longnight chronicle " + id, "The local history"))
                .append(" ").append(button("Join", "/longnight join " + id, "Ask to become a citizen")));
    }

    static String moodText(double m) {
        if (m > 0.75) return "thriving";
        if (m > 0.55) return "content";
        if (m > 0.4) return "uneasy";
        if (m > 0.25) return "troubled";
        return "desperate";
    }

    public static String standingWord(int rep) {
        if (rep >= 60) return "honoured";
        if (rep >= 30) return "trusted";
        if (rep >= 10) return "welcome";
        if (rep > -10) return "a stranger";
        if (rep > -40) return "distrusted";
        return "hated";
    }

    static String threatText(Settlement s, WorldSim sim) {
        Settlement src = sim.settlement(s.raidSource);
        String who = src == null ? "outlaws" : "the outlaws of " + src.name;
        return switch (s.raidWarning) {
            case 1 -> "Rumours say " + who + " are watching the roads.";
            case 2 -> "Scouts of " + who + " have been seen. An attack is expected at dusk tomorrow.";
            default -> who + " are attacking!";
        };
    }

    public static void market(ServerPlayer p, WorldSim sim, Settlement s) {
        p.sendSystemMessage(title("Market of " + s.name));
        msg(p, "Your purse: " + Coins.format(Coins.purse(p)) + ". Sell by using goods on the storehouse; sneak to donate.");
        String id = s.id.toString();
        for (Commodity c : Commodity.values()) {
            int have = s.stock.whole(c);
            if (have <= 0 && TradeGoods.itemFor(c) == null) continue;
            MutableComponent line = Component.literal(String.format("%-11s %4d in store  buy %s / sell %s  ", c.displayName, have,
                    Coins.format(Trade.buyPrice(sim, s, c)), Coins.format(Trade.sellPrice(sim, s, c))));
            if (TradeGoods.itemFor(c) != null && have > 0) {
                line.append(button("1", "/longnight buy " + id + " " + c.id() + " 1", "Buy one"));
                if (have >= 8) line.append(" ").append(button("8", "/longnight buy " + id + " " + c.id() + " 8", "Buy eight"));
            }
            p.sendSystemMessage(line);
        }
    }

    public static void notices(ServerPlayer p, WorldSim sim, Settlement s) {
        long day = day(p);
        p.sendSystemMessage(title("Notices of " + s.name));
        msg(p, Calendar.describe(day, sim.rules.daysPerYear) + ".");
        List<Map.Entry<Long, Long>> known = new java.util.ArrayList<>(s.knownNews.entrySet());
        int shown = 0;
        for (int i = known.size() - 1; i >= 0 && shown < 6; i--) {
            NewsItem n = sim.newsById(known.get(i).getKey());
            if (n == null || day - n.day() > 20) continue;
            msg(p, " * " + n.text() + (n.day() < day ? " (" + (day - n.day()) + " days ago)" : " (today)"));
            shown++;
        }
        if (shown == 0) msg(p, " * Nothing new has been posted.");
        List<Commodity> needs = Economy.shortages(sim, s);
        if (!needs.isEmpty()) {
            StringBuilder sb = new StringBuilder("WANTED, good prices paid: ");
            for (int i = 0; i < needs.size() && i < 6; i++) sb.append(i == 0 ? "" : ", ").append(needs.get(i).displayName.toLowerCase());
            p.sendSystemMessage(Component.literal(sb.toString()).withStyle(ChatFormatting.YELLOW));
        }
        for (Building b : s.buildings) {
            if (b.underConstruction() && !b.problem.isEmpty()) {
                msg(p, "The builders of the " + b.type.displayName.toLowerCase() + " ask for help: still needed " + Construction.remainingBill(s, b) + ".");
                break;
            }
        }
        for (com.longnight.sim.Contracts.Contract k : s.contracts) {
            MutableComponent line = Component.literal(" > " + k.describe(sim) + " (until day " + k.expiresDay + ") ");
            if (k.open()) line.append(button("Accept", "/longnight accept " + s.id + " " + k.id, "Take on this work"));
            else if (p.getUUID().equals(k.takenBy)) line.append(Component.literal("[yours]").withStyle(ChatFormatting.GREEN));
            else line.append(Component.literal("[taken]").withStyle(ChatFormatting.DARK_GRAY));
            p.sendSystemMessage(line);
        }
    }

    public static void people(ServerPlayer p, WorldSim sim, Settlement s) {
        long day = day(p);
        p.sendSystemMessage(title("People of " + s.name));
        int n = 0;
        for (Person x : sim.residents(s)) {
            if (n++ >= 30) {
                msg(p, "...and " + (s.residents.size() - 30) + " more.");
                break;
            }
            int age = (int) sim.ageOf(x, day);
            String job = sim.isAdult(x, day) ? x.profession.displayName.toLowerCase() : "child";
            p.sendSystemMessage(Component.literal(x.fullName() + ", " + age + ", " + job + (x.title.isEmpty() ? "" : " - " + x.title) + " ")
                    .append(button("?", "/longnight person " + x.id, "More about " + x.given)));
        }
    }

    public static void chronicle(ServerPlayer p, WorldSim sim, Settlement s) {
        p.sendSystemMessage(title("Chronicle of " + s.name));
        int from = Math.max(0, s.chronicle.size() - 12);
        for (int i = from; i < s.chronicle.size(); i++) msg(p, s.chronicle.get(i));
        Faction f = sim.faction(s.faction);
        if (f != null) {
            int ff = Math.max(0, f.chronicle.size() - 5);
            for (int i = ff; i < f.chronicle.size(); i++) p.sendSystemMessage(Component.literal(f.chronicle.get(i)).withStyle(ChatFormatting.GRAY));
        }
    }

    public static void grave(ServerPlayer p, GravestoneBlockEntity g) {
        p.sendSystemMessage(Component.literal("Here lies " + g.name).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
        msg(p, g.dates + (g.cause.isEmpty() ? "" : " - " + g.cause));
        if (!g.epitaph.isEmpty()) p.sendSystemMessage(Component.literal("\"" + g.epitaph + "\"").withStyle(ChatFormatting.ITALIC));
        if (!g.rite.isEmpty()) p.sendSystemMessage(hint("By the rites of " + g.faith + ": " + g.rite + "."));
    }

    // ------------------------------------------------------------------ conversation

    public static void citizen(ServerPlayer p, WorldSim sim, Person x) {
        long day = day(p);
        Settlement s = sim.settlement(x.settlement);
        Culture c = Content.culture(x.culture);
        int age = (int) sim.ageOf(x, day);
        boolean adult = sim.isAdult(x, day);
        String what = adult ? x.profession.displayName.toLowerCase() : "child";
        p.sendSystemMessage(Component.literal(x.fullName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(" - " + c.name + " " + what + ", " + age + " years, " + Content.faith(x.faith).adherent.toLowerCase())
                        .withStyle(ChatFormatting.GRAY)));
        if (!x.title.isEmpty()) p.sendSystemMessage(Component.literal(x.title).withStyle(ChatFormatting.LIGHT_PURPLE));
        p.sendSystemMessage(Component.literal("\"" + Dialogue.greeting(sim, x, p, s, day) + "\"").withStyle(ChatFormatting.WHITE));
        if (!x.blocked.isEmpty() && adult) p.sendSystemMessage(Component.literal("\"" + x.blocked + "\"").withStyle(ChatFormatting.YELLOW));
        String id = x.id.toString();
        MutableComponent row = Component.empty()
                .append(button("News", "/longnight talk " + id + " news", "Ask what they have heard"))
                .append(" ").append(button("Work", "/longnight talk " + id + " work", "Ask about their work"))
                .append(" ").append(button("Family", "/longnight talk " + id + " family", "Ask about their family"))
                .append(" ").append(button("Life", "/longnight talk " + id + " life", "Ask how life is"));
        if (s != null && s.kind == Settlement.Kind.SETTLEMENT) row.append(" ").append(button("Town", "/longnight town " + s.id, "Ask about " + s.name));
        if (s != null && s.kind == Settlement.Kind.BAND && p.getUUID().equals(s.founderPlayer)) {
            row.append(" ").append(button("Settle here", "/longnight settle", "Make your home here, where you stand (uses the Founding Charter)"));
        }
        p.sendSystemMessage(row);
        if (s != null && adult && p.getUUID().equals(s.founderPlayer)) {
            // you are their lord: you can tell them what to do
            if (!x.order.isEmpty()) msg(p, "Your orders: " + ("build".equals(x.order) ? "help the builders" : "fell trees") + ".");
            MutableComponent orders = Component.literal("Orders: ").withStyle(ChatFormatting.GRAY);
            if (s.kind == Settlement.Kind.SETTLEMENT) {
                orders.append(button("Help build", "/longnight order " + id + " build", "Lay blocks with the builders"))
                        .append(" ").append(button("Fell trees", "/longnight order " + id + " fell", "Clear trees nearby; the wood goes to the stores"))
                        .append(" ").append(button("Own trade", "/longnight order " + id + " trade", "Go back to their own work"))
                        .append(" ").append(button("Come with me", "/longnight order " + id + " follow", "Travel with you"));
            } else {
                orders.append(button("Go home", "/longnight order " + id + " home", "Go back to your village"));
            }
            p.sendSystemMessage(orders);
        }
    }

    public static void talk(ServerPlayer p, WorldSim sim, Person x, String topic) {
        long day = day(p);
        Settlement s = sim.settlement(x.settlement);
        switch (topic) {
            case "news" -> {
                List<NewsItem> heard = NewsSystem.knownBy(sim, x, day, 3);
                if (heard.isEmpty()) {
                    say(p, x, "Nothing worth repeating, I'm afraid. Ask at the tavern, or wait for a caravan.");
                    return;
                }
                for (NewsItem n : heard) say(p, x, Dialogue.retell(sim, x, n, day));
            }
            case "work" -> say(p, x, Dialogue.work(sim, x, s, day));
            case "family" -> say(p, x, Dialogue.family(sim, x, day));
            case "life" -> say(p, x, Dialogue.life(sim, x, p, s, day));
            default -> citizen(p, sim, x);
        }
        x.changeOpinion(p.getUUID(), 0);
    }

    static void say(ServerPlayer p, Person x, String text) {
        p.sendSystemMessage(Component.literal(x.given + ": ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    public static void person(ServerPlayer p, WorldSim sim, Person x) {
        long day = day(p);
        p.sendSystemMessage(title(x.fullName()));
        msg(p, (x.alive ? "Age " + (int) sim.ageOf(x, day) : "Died in year " + Calendar.year(x.deathDay, sim.rules.daysPerYear) + " (" + x.deathCause + ")")
                + ". " + Content.culture(x.culture).name + ", " + Content.faith(x.faith).name + ".");
        if (x.alive) {
            msg(p, "Trade: " + x.profession.displayName + " (skill " + (int) x.skill(x.profession) + "). Mood: " + x.moodWord() + ". "
                    + (x.activity.isEmpty() ? "" : "Lately: " + x.activity + "."));
            msg(p, "Temperament: " + Dialogue.temperament(x) + ".");
        }
        msg(p, Dialogue.family(sim, x, day));
        int shown = 0;
        for (int i = x.memories.size() - 1; i >= 0 && shown < 4; i--, shown++) {
            Person.Memory m = x.memories.get(i);
            p.sendSystemMessage(hint("  remembers: " + m.text() + " (day " + m.day() + ")"));
        }
    }
}
