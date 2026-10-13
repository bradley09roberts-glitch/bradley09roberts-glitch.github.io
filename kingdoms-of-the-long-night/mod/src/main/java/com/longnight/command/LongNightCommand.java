package com.longnight.command;

import com.longnight.entity.CitizenEntity;
import com.longnight.game.Founding;
import com.longnight.game.Journal;
import com.longnight.game.Trade;
import com.longnight.game.Ui;
import com.longnight.sim.Building;
import com.longnight.sim.BuildingType;
import com.longnight.sim.Commodity;
import com.longnight.sim.Faction;
import com.longnight.sim.Person;
import com.longnight.sim.Planner;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import com.longnight.world.Embodiment;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /longnight (alias /ln): the chat interface's buttons, player tools and admin/test tools. */
public final class LongNightCommand {
    private LongNightCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("longnight")
                .executes(c -> help(c.getSource()))
                .then(Commands.literal("help").executes(c -> help(c.getSource())))
                .then(Commands.literal("journal").executes(c -> {
                    Journal.open(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("here").executes(LongNightCommand::here))
                .then(withSettlement("market", (p, sim, s) -> Ui.market(p, sim, s)))
                .then(withSettlement("notices", (p, sim, s) -> Ui.notices(p, sim, s)))
                .then(withSettlement("people", (p, sim, s) -> Ui.people(p, sim, s)))
                .then(withSettlement("chronicle", (p, sim, s) -> Ui.chronicle(p, sim, s)))
                .then(withSettlement("town", (p, sim, s) -> Ui.settlement(p, sim, s)))
                .then(withSettlement("join", LongNightCommand::join))
                .then(Commands.literal("person").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    WorldSim sim = sim(c);
                    Person x = sim.person(uuid(StringArgumentType.getString(c, "id")));
                    if (x != null) Ui.person(p, sim, x);
                    return 1;
                })))
                .then(Commands.literal("talk").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("topic", StringArgumentType.word()).executes(LongNightCommand::talk))))
                .then(Commands.literal("buy").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("good", StringArgumentType.word())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(LongNightCommand::buy)))))
                .then(Commands.literal("realms").executes(LongNightCommand::realms))
                .then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("contract", StringArgumentType.word()).executes(LongNightCommand::accept))))
                .then(Commands.literal("plan").then(Commands.argument("building", StringArgumentType.word()).executes(LongNightCommand::plan)))
                .then(Commands.literal("rename").then(Commands.argument("name", StringArgumentType.greedyString()).executes(LongNightCommand::rename)))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("status").executes(LongNightCommand::status))
                        .then(Commands.literal("list").executes(LongNightCommand::list))
                        .then(Commands.literal("perf").executes(c -> {
                            c.getSource().sendSuccess(() -> Component.literal("[longnight] " + com.longnight.world.Perf.report()), false);
                            return 1;
                        }))
                        .then(Commands.literal("validate").executes(LongNightCommand::validate))
                        .then(Commands.literal("skip").then(Commands.argument("days", IntegerArgumentType.integer(1, 30)).executes(LongNightCommand::skip)))
                        .then(Commands.literal("tp").then(Commands.argument("name", StringArgumentType.greedyString()).executes(LongNightCommand::tp)))
                        .then(Commands.literal("observe").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                            WorldSim sim = sim(c);
                            Settlement s = byName(sim, StringArgumentType.getString(c, "name"));
                            if (s == null) return 0;
                            Embodiment.observe(c.getSource().getServer().overworld(), s, true);
                            c.getSource().sendSuccess(() -> Component.literal("[longnight] observing " + s.name), false);
                            return 1;
                        })))
                        .then(Commands.literal("inspect").then(Commands.argument("name", StringArgumentType.greedyString()).executes(LongNightCommand::inspect)))
                        .then(Commands.literal("raid").then(Commands.argument("name", StringArgumentType.greedyString()).executes(LongNightCommand::raid)))
                        .then(Commands.literal("charter").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            p.getInventory().add(Founding.charter());
                            return 1;
                        }))
                        .then(Commands.literal("history").executes(c -> {
                            WorldSim sim = sim(c);
                            int from = Math.max(0, sim.history.size() - 25);
                            for (int i = from; i < sim.history.size(); i++) c.getSource().sendSystemMessage(Component.literal(sim.history.get(i)));
                            return 1;
                        })));
        var node = d.register(root);
        d.register(Commands.literal("ln").redirect(node));
    }

    interface SettlementAction {
        void run(ServerPlayer p, WorldSim sim, Settlement s);
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> withSettlement(String name, SettlementAction a) {
        return Commands.literal(name).then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            WorldSim sim = sim(c);
            Settlement s = sim.settlement(uuid(StringArgumentType.getString(c, "id")));
            if (s == null) return 0;
            a.run(p, sim, s);
            return 1;
        }));
    }

    static WorldSim sim(CommandContext<CommandSourceStack> c) {
        return WorldSim.get(c.getSource().getServer());
    }

    static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static int help(CommandSourceStack src) {
        String[] lines = {
                "Kingdoms of the Long Night - /longnight (or /ln)",
                " journal  - places you know of",
                " here     - about the settlement you stand in",
                " realms   - the realms you have heard of",
                " plan <building> / rename <name> - for founders of a settlement",
                "Right-click people to talk, hearthstones for the settlement, storehouses to trade,",
                "notice boards for news. Carry a Founding Charter and place a hearthstone to found your own.",
                "Boundary stones protect your land from settlement builders."};
        for (String l : lines) src.sendSystemMessage(Component.literal(l));
        return 1;
    }

    static int here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorldSim sim = sim(c);
        Settlement s = sim.settlementAt(p.blockPosition(), 16);
        if (s == null) {
            Ui.msg(p, "You are in the wilds.");
            return 0;
        }
        Ui.settlement(p, sim, s);
        return 1;
    }

    static int talk(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorldSim sim = sim(c);
        Person x = sim.person(uuid(StringArgumentType.getString(c, "id")));
        if (x == null || !x.alive) return 0;
        UUID body = Embodiment.bodyOf(x.id);
        var e = body == null ? null : p.serverLevel().getEntity(body);
        if (e == null || e.distanceToSqr(p) > 12 * 12) {
            Ui.msg(p, x.given + " is too far away to hear you.");
            return 0;
        }
        if (e instanceof CitizenEntity ce) ce.brain.pause(60);
        Ui.talk(p, sim, x, StringArgumentType.getString(c, "topic"));
        if (StringArgumentType.getString(c, "topic").equals("news")) {
            // listening is how you learn of other places
            for (var entry : sim.settlement(x.settlement) == null ? List.<Map.Entry<Long, Long>>of() : new ArrayList<>(sim.settlement(x.settlement).knownNews.entrySet())) {
                var n = sim.newsById(entry.getKey());
                if (n != null && n.origin() != null) sim.learnOf(p.getUUID(), sim.settlement(n.origin()));
            }
        }
        return 1;
    }

    static int buy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorldSim sim = sim(c);
        Settlement s = sim.settlement(uuid(StringArgumentType.getString(c, "id")));
        Commodity good = Commodity.byId(StringArgumentType.getString(c, "good"));
        if (s == null || good == null) return 0;
        double dx = p.getX() - s.center.getX(), dz = p.getZ() - s.center.getZ();
        if (Math.sqrt(dx * dx + dz * dz) > s.radius + 16) {
            Ui.msg(p, "You must be in " + s.name + " to trade there.");
            return 0;
        }
        return Trade.buy(p, sim, s, good, IntegerArgumentType.getInteger(c, "count")) ? 1 : 0;
    }

    static int accept(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorldSim sim = sim(c);
        Settlement s = sim.settlement(uuid(StringArgumentType.getString(c, "id")));
        UUID cid = uuid(StringArgumentType.getString(c, "contract"));
        if (s == null || cid == null) return 0;
        int mine = 0;
        for (Settlement x : sim.settlements.values()) for (var k : x.contracts) if (p.getUUID().equals(k.takenBy)) mine++;
        for (var k : s.contracts) {
            if (!k.id.equals(cid)) continue;
            if (!k.open()) {
                Ui.msg(p, "Someone has already taken that work.");
                return 0;
            }
            if (mine >= 3) {
                Ui.msg(p, "You already have three contracts; finish one first.");
                return 0;
            }
            k.takenBy = p.getUUID();
            sim.setDirty();
            Ui.msg(p, "Agreed. " + k.describe(sim) + (k.kind.equals("deliver") ? " Bring it to the storehouse of " + s.name + "." : ""));
            return 1;
        }
        return 0;
    }

    static void join(ServerPlayer p, WorldSim sim, Settlement s) {
        if (s.kind != Settlement.Kind.SETTLEMENT) return;
        if (s.citizenPlayers.contains(p.getUUID())) {
            Ui.msg(p, "You are already a citizen of " + s.name + ".");
            return;
        }
        int rep = s.reputationOf(p.getUUID());
        if (rep < 20) {
            Ui.msg(p, "The steward looks you over: \"We don't know you yet. Trade with us, help us, and ask again.\" (standing: "
                    + Ui.standingWord(rep) + ")");
            return;
        }
        s.citizenPlayers.add(p.getUUID());
        s.changeReputation(p.getUUID(), 5);
        sim.setDirty();
        Ui.msg(p, "You are now a citizen of " + s.name + ". You'll get a fair price at its market, word of its troubles, and its watch will know your face.");
    }

    static int realms(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorldSim sim = sim(c);
        var known = sim.knownPlaces.getOrDefault(p.getUUID(), java.util.Set.of());
        java.util.Set<UUID> realms = new java.util.HashSet<>();
        for (UUID sid : known) {
            Settlement s = sim.settlement(sid);
            if (s != null && s.faction != null) realms.add(s.faction);
        }
        if (realms.isEmpty()) Ui.msg(p, "You have not yet learned of any realm.");
        for (UUID fid : realms) {
            Faction f = sim.faction(fid);
            if (f == null || f.government == Faction.Government.OUTLAWS) continue;
            Person r = sim.person(f.ruler);
            Ui.msg(p, f.name + " - " + f.government.realmWord + (r != null ? ", " + f.rulerTitle(r) + " " + r.fullName() : "")
                    + ". " + sim.settlementsOf(f).size() + " settlements. Wars: " + warsOf(sim, f));
        }
        return 1;
    }

    static String warsOf(WorldSim sim, Faction f) {
        List<String> w = new ArrayList<>();
        for (WorldSim.War war : sim.wars) {
            if (f.id.equals(war.attacker)) w.add("against " + name(sim, war.defender));
            if (f.id.equals(war.defender)) w.add("defending against " + name(sim, war.attacker));
        }
        return w.isEmpty() ? "none" : String.join(", ", w);
    }

    static String name(WorldSim sim, UUID f) {
        Faction x = sim.faction(f);
        return x == null ? "?" : x.name;
    }

    static Settlement ownSettlement(ServerPlayer p, WorldSim sim) {
        Settlement s = sim.settlementAt(p.blockPosition(), 32);
        if (s == null || !s.playerFounded || !p.getUUID().equals(s.founderPlayer)) {
            Ui.msg(p, "You must stand in a settlement you founded.");
            return null;
        }
        return s;
    }

    static int plan(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorldSim sim = sim(c);
        Settlement s = ownSettlement(p, sim);
        if (s == null) return 0;
        BuildingType t = BuildingType.byId(StringArgumentType.getString(c, "building"));
        if (t == null || t == BuildingType.CAMPSITE) {
            StringBuilder sb = new StringBuilder("Buildings: ");
            for (BuildingType x : BuildingType.values()) if (x != BuildingType.CAMPSITE) sb.append(x.id()).append(' ');
            Ui.msg(p, sb.toString());
            return 0;
        }
        Building b = Planner.plan(sim, s, t, WorldSim.dayOf(c.getSource().getServer().overworld()));
        if (b == null) Ui.msg(p, "No free plot could be found for a " + t.displayName.toLowerCase() + ".");
        else Ui.msg(p, "Your settlers will build a " + t.displayName.toLowerCase() + ". It needs: " + com.longnight.sim.Construction.remainingBill(s, b) + ".");
        return 1;
    }

    static int rename(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorldSim sim = sim(c);
        Settlement s = ownSettlement(p, sim);
        if (s == null) return 0;
        String name = StringArgumentType.getString(c, "name").trim();
        if (name.isEmpty() || name.length() > 32) return 0;
        s.chronicle(WorldSim.dayOf(c.getSource().getServer().overworld()), s.name + " was renamed " + name + ".");
        s.name = name;
        Faction f = sim.faction(s.faction);
        if (f != null && f.playerRealm) f.name = "Lordship of " + name;
        sim.setDirty();
        Ui.msg(p, "Henceforth this place is " + name + ".");
        return 1;
    }

    // ------------------------------------------------------------------ admin / testing

    static int status(CommandContext<CommandSourceStack> c) {
        WorldSim sim = sim(c);
        int alive = 0, dead = 0;
        for (Person p : sim.people.values()) {
            if (p.alive) alive++;
            else dead++;
        }
        int settle = 0, camps = 0, ruins = 0;
        for (Settlement s : sim.settlements.values()) {
            switch (s.kind) {
                case SETTLEMENT -> settle++;
                case BANDIT_CAMP -> camps++;
                case RUIN -> ruins++;
            }
        }
        String msg = "[longnight] day=" + sim.lastDay + " people alive=" + alive + " dead=" + dead + " settlements=" + settle
                + " outlawCamps=" + camps + " ruins=" + ruins + " realms=" + sim.factions.size() + " wars=" + sim.wars.size()
                + " caravans=" + sim.caravans.size() + " news=" + sim.news.size() + " bodies=" + Embodiment.bodyCount()
                + " nightStage=" + sim.nightStage;
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    static int list(CommandContext<CommandSourceStack> c) {
        WorldSim sim = sim(c);
        for (Settlement s : sim.settlements.values()) {
            int built = 0;
            for (Building b : s.buildings) if (b.usable()) built++;
            String line = String.format("[longnight] %s %s %s pop=%d buildings=%d/%d food=%.0f at %d %d %d %s", s.kind, s.tier, s.name, s.residents.size(),
                    built, s.buildings.size(), s.stock.foodUnits(), s.center.getX(), s.center.getY(), s.center.getZ(), s.materialized ? "materialized" : "");
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    static int validate(CommandContext<CommandSourceStack> c) {
        WorldSim sim = sim(c);
        List<String> errors = Invariants.check(sim, c.getSource().getLevel());
        if (errors.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("[longnight] VALIDATE OK people=" + sim.people.size()
                    + " settlements=" + sim.settlements.size()), false);
            return 1;
        }
        for (String e : errors.subList(0, Math.min(errors.size(), 30))) c.getSource().sendFailure(Component.literal("[longnight] VALIDATE FAIL " + e));
        return 0;
    }

    static int skip(CommandContext<CommandSourceStack> c) {
        int days = IntegerArgumentType.getInteger(c, "days");
        ServerLevel ow = c.getSource().getServer().overworld();
        ow.setDayTime(ow.getDayTime() + 24000L * days);
        WorldSim sim = sim(c);
        sim.tick(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal("[longnight] skipped " + days + " days; now day " + sim.lastDay), true);
        return 1;
    }

    static Settlement byName(WorldSim sim, String name) {
        for (Settlement s : sim.settlements.values()) if (s.name.equalsIgnoreCase(name)) return s;
        for (Settlement s : sim.settlements.values()) if (s.name.toLowerCase().startsWith(name.toLowerCase())) return s;
        return null;
    }

    static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        WorldSim sim = sim(c);
        Settlement s = byName(sim, StringArgumentType.getString(c, "name"));
        if (s == null) return 0;
        p.teleportTo(p.serverLevel(), s.center.getX() + 0.5, Math.max(s.center.getY() + 2, 70), s.center.getZ() + 12.5, p.getYRot(), p.getXRot());
        sim.learnOf(p.getUUID(), s);
        return 1;
    }

    static int raid(CommandContext<CommandSourceStack> c) {
        WorldSim sim = sim(c);
        Settlement s = byName(sim, StringArgumentType.getString(c, "name"));
        if (s == null) return 0;
        Settlement camp = sim.nearestSettlement(s.center, Settlement.Kind.BANDIT_CAMP);
        if (camp == null) return 0;
        s.raidSource = camp.id;
        s.raidDay = sim.lastDay;
        s.raidWarning = 2;
        s.raidStrength = camp.residents.size();
        sim.hooks.spawnRaid(sim, s, camp, s.raidStrength);
        c.getSource().sendSuccess(() -> Component.literal("[longnight] raid on " + s.name + " by " + camp.name + " scheduled for dusk"), true);
        return 1;
    }

    static int inspect(CommandContext<CommandSourceStack> c) {
        WorldSim sim = sim(c);
        Settlement s = byName(sim, StringArgumentType.getString(c, "name"));
        if (s == null) return 0;
        long day = WorldSim.dayOf(c.getSource().getServer().overworld());
        StringBuilder sb = new StringBuilder("[longnight] " + s.name + " " + s.kind + " " + s.tier + " pop=" + s.residents.size()
                + " housing=" + s.housingCapacity() + " treasury=" + s.treasury + " materialized=" + s.materialized + " active="
                + Embodiment.isActive(s) + "\n stock:");
        for (Commodity cm : Commodity.values()) if (s.stock.get(cm) >= 0.5) sb.append(' ').append(cm.id()).append('=').append(Math.round(s.stock.get(cm)));
        sb.append("\n buildings:");
        for (Building b : s.buildings) sb.append("\n  ").append(b.type.id()).append(' ').append(b.state).append(" paid=").append(b.progress)
                .append(" placed=").append(b.placed).append('/').append(b.total).append(b.problem.isEmpty() ? "" : " problem=" + b.problem);
        sb.append("\n people:");
        for (Person p : sim.residents(s)) {
            UUID body = Embodiment.bodyOf(p.id);
            sb.append("\n  ").append(p.fullName()).append(" age=").append((int) sim.ageOf(p, day)).append(' ').append(p.profession.id())
                    .append(" mood=").append(p.moodWord()).append(body != null ? " [body]" : "").append(p.activity.isEmpty() ? "" : " doing=" + p.activity)
                    .append(p.blocked.isEmpty() ? "" : " BLOCKED=" + p.blocked);
        }
        String out = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    static Map<UUID, Integer> countBodies(ServerLevel level) {
        Map<UUID, Integer> m = new HashMap<>();
        for (var e : level.getAllEntities()) if (e instanceof CitizenEntity ce && ce.personId != null && ce.isAlive()) m.merge(ce.personId, 1, Integer::sum);
        return m;
    }
}
