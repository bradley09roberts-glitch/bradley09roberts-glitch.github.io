package com.squidgame.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.squidgame.SquidConfig;
import com.squidgame.build.ArenaBuilders;
import com.squidgame.build.ArenaId;
import com.squidgame.build.Marker;
import com.squidgame.core.Difficulty;
import com.squidgame.game.EliminationCause;
import com.squidgame.tournament.Contestant;
import com.squidgame.tournament.Tournament;
import com.squidgame.tournament.TournamentManager;
import com.squidgame.world.ArenaWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /squid ...}: generate the complex, enter it, start / reset a tournament and administer it.
 * Everything a player needs is also reachable in-world (recruiter card, registration terminal) so singleplayer
 * worlds without cheats work.
 */
public final class SquidCommands {
    private SquidCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("squid");

        root.then(Commands.literal("build")
                .requires(s -> s.hasPermission(2))
                .executes(c -> build(c, false))
                .then(Commands.literal("force").executes(c -> build(c, true))));
        root.then(Commands.literal("enter").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            TournamentManager m = mgr(c);
            m.enterOrBuild(p);
            return 1;
        }));
        root.then(Commands.literal("leave").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            mgr(c).leaveArena(p);
            return 1;
        }));
        root.then(Commands.literal("start")
                .executes(c -> start(c, null, -1, false))
                .then(Commands.argument("difficulty", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("normal", "hard", "extreme"), b))
                        .executes(c -> start(c, Difficulty.byId(StringArgumentType.getString(c, "difficulty"), Difficulty.NORMAL), -1, false))
                        .then(Commands.argument("npcs", IntegerArgumentType.integer(0, 456))
                                .executes(c -> start(c, Difficulty.byId(StringArgumentType.getString(c, "difficulty"), Difficulty.NORMAL),
                                        IntegerArgumentType.getInteger(c, "npcs"), false)))));
        root.then(Commands.literal("join").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            String err = mgr(c).registerPlayer(p);
            if (err != null) {
                c.getSource().sendFailure(Component.literal(err));
                return 0;
            }
            return 1;
        }));
        root.then(Commands.literal("spectate").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            TournamentManager m = mgr(c);
            if (!ArenaWorld.isArena(p.level())) {
                m.enterOrBuild(p);
            }
            m.makeSpectator(p);
            return 1;
        }));
        root.then(Commands.literal("status").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            mgr(c).statusMessage(p);
            return 1;
        }));
        root.then(Commands.literal("skip").requires(s -> s.hasPermission(2)).executes(c -> {
            String err = mgr(c).skip();
            if (err != null) {
                c.getSource().sendFailure(Component.literal(err));
                return 0;
            }
            c.getSource().sendSuccess(() -> Component.literal("Skipped."), true);
            return 1;
        }));
        root.then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
            return logged("reset", () -> mgr(c).reset(false));
        }).then(Commands.literal("home").executes(c -> {
            return logged("reset home", () -> mgr(c).reset(true));
        })));
        root.then(Commands.literal("config").requires(s -> s.hasPermission(2))
                .executes(c -> {
                    SquidConfig cfg = SquidConfig.get();
                    for (String k : SquidConfig.keys()) {
                        String key = k;
                        c.getSource().sendSuccess(() -> Component.literal(key + " = " + cfg.getValue(key)), false);
                    }
                    return 1;
                })
                .then(Commands.argument("key", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SquidConfig.keys(), b))
                        .executes(c -> {
                            String k = StringArgumentType.getString(c, "key");
                            String v = SquidConfig.get().getValue(k);
                            c.getSource().sendSuccess(() -> Component.literal(k + " = " + v), false);
                            return v == null ? 0 : 1;
                        })
                        .then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> {
                            String err = SquidConfig.get().setValue(StringArgumentType.getString(c, "key"),
                                    StringArgumentType.getString(c, "value"));
                            if (err != null) {
                                c.getSource().sendFailure(Component.literal(err));
                                return 0;
                            }
                            c.getSource().sendSuccess(() -> Component.literal("Saved."), true);
                            return 1;
                        }))));
        // free exploration of the arenas when no tournament is running
        root.then(Commands.literal("arena").then(Commands.argument("name", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(ArenaId.values()).map(a -> a.id).toList(), b))
                .executes(c -> tour(c, StringArgumentType.getString(c, "name")))));

        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug").requires(s -> s.hasPermission(2));
        debug.then(Commands.literal("simulate")
                .executes(c -> start(c, null, -1, true))
                .then(Commands.argument("npcs", IntegerArgumentType.integer(2, 456))
                        .executes(c -> start(c, null, IntegerArgumentType.getInteger(c, "npcs"), true))
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .executes(c -> start(c, Difficulty.byId(StringArgumentType.getString(c, "difficulty"), Difficulty.NORMAL),
                                        IntegerArgumentType.getInteger(c, "npcs"), true)))));
        // /squid debug play <game> [npcs] [difficulty] : the executing player takes part (if any); registration is
        // closed at once and the tournament ends after this single game. Use /squid debug simulate for NPC-only runs.
        debug.then(Commands.literal("play").then(Commands.argument("game", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.squidgame.core.GameKind.values()).map(g -> g.id).toList(), b))
                .executes(c -> playSingle(c, -1, null))
                .then(Commands.argument("npcs", IntegerArgumentType.integer(0, 456))
                        .executes(c -> playSingle(c, IntegerArgumentType.getInteger(c, "npcs"), null))
                        .then(Commands.argument("difficulty", StringArgumentType.word())
                                .executes(c -> playSingle(c, IntegerArgumentType.getInteger(c, "npcs"),
                                        Difficulty.byId(StringArgumentType.getString(c, "difficulty"), Difficulty.NORMAL)))))));
        debug.then(Commands.literal("perf").executes(c -> {
            TournamentManager m = mgr(c);
            Tournament t = m.tournament();
            int npcs = t == null ? 0 : (int) t.roster.all().stream().filter(Contestant::isAiControlled).count();
            for (String line : com.squidgame.tournament.Profiler.report(npcs).split("\n")) {
                c.getSource().sendSuccess(() -> Component.literal(line), false);
            }
            double mspt = c.getSource().getServer().getAverageTickTimeNanos() / 1_000_000.0;
            c.getSource().sendSuccess(() -> Component.literal(String.format("  server average tick (vanilla): %.2f ms", mspt)), false);
            return 1;
        }));
        debug.then(Commands.literal("timescale").then(Commands.argument("x", DoubleArgumentType.doubleArg(0.02, 10)).executes(c -> {
            SquidConfig.get().setValue("timeScale", Double.toString(DoubleArgumentType.getDouble(c, "x")));
            c.getSource().sendSuccess(() -> Component.literal("timeScale = " + SquidConfig.get().timeScale), true);
            return 1;
        })));
        debug.then(Commands.literal("eliminate").then(Commands.argument("number", IntegerArgumentType.integer(1, 456)).executes(c -> {
            TournamentManager m = mgr(c);
            Tournament t = m.tournament();
            if (t == null || t.ctx == null) {
                c.getSource().sendFailure(Component.literal("No game is running."));
                return 0;
            }
            Contestant ct = t.roster.get(IntegerArgumentType.getInteger(c, "number"));
            if (ct == null) {
                c.getSource().sendFailure(Component.literal("No such contestant."));
                return 0;
            }
            m.eliminate(t.ctx, ct, EliminationCause.ADMIN);
            return 1;
        })));
        debug.then(Commands.literal("roster").executes(c -> {
            Tournament t = mgr(c).tournament();
            if (t == null) {
                c.getSource().sendFailure(Component.literal("No tournament."));
                return 0;
            }
            for (Contestant ct : t.roster.all()) {
                c.getSource().sendSuccess(() -> Component.literal(ct.label() + " " + ct.status() + " " + ct.personality.describe()), false);
            }
            return 1;
        }));
        debug.then(Commands.literal("markers").then(Commands.argument("arena", StringArgumentType.word()).executes(c -> {
            ArenaId id = ArenaId.byId(StringArgumentType.getString(c, "arena"));
            if (id == null) {
                return 0;
            }
            var data = mgr(c).arenaData();
            var rec = data.record(id);
            if (rec == null) {
                c.getSource().sendFailure(Component.literal("Not built."));
                return 0;
            }
            rec.markers.forEach((k, v) -> c.getSource().sendSuccess(() -> Component.literal(k + " x" + v.size()), false));
            rec.regions.forEach((k, v) -> v.forEach(r -> c.getSource().sendSuccess(() -> Component.literal("region " + k + " ["
                    + r.minX() + "," + r.minY() + "," + r.minZ() + " .. " + r.maxX() + "," + r.maxY() + "," + r.maxZ() + "]"), false)));
            return 1;
        })));
        debug.then(Commands.literal("rules").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).executes(c -> {
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player");
            String line = mgr(c).restrictions().describe(target);
            c.getSource().sendSuccess(() -> Component.literal(line), false);
            return 1;
        })));
        debug.then(Commands.literal("builders").executes(c -> {
            for (ArenaId id : ArenaId.values()) {
                String s = id + ": " + (ArenaBuilders.isPlaceholder(id) ? "placeholder" : "real");
                c.getSource().sendSuccess(() -> Component.literal(s), false);
            }
            return 1;
        }));
        root.then(debug);
        d.register(root);
    }

    /** Runs a command body and logs the stack trace of any failure (vanilla only prints a generic message). */
    private static int logged(String what, Runnable body) {
        try {
            body.run();
            return 1;
        } catch (RuntimeException e) {
            com.squidgame.SquidGameMod.LOGGER.error("/squid {} failed", what, e);
            throw e;
        }
    }

    private static TournamentManager mgr(CommandContext<CommandSourceStack> c) {
        return TournamentManager.get();
    }

    private static int build(CommandContext<CommandSourceStack> c, boolean force) {
        TournamentManager m = mgr(c);
        List<ArenaId> all = new ArrayList<>(List.of(ArenaId.values()));
        c.getSource().sendSuccess(() -> Component.literal("Building the tournament complex" + (force ? " (forced rebuild)" : "") + "..."), true);
        m.builds().request(all, force, msg -> c.getSource().sendSystemMessage(msg), null);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> c, Difficulty d, int npcs, boolean npcOnly) {
        TournamentManager m = mgr(c);
        ServerPlayer p = c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        String err = m.start(npcOnly ? null : p, d, npcs, npcOnly);
        if (err != null) {
            c.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        if (npcOnly && p != null) {
            // watch the simulation
            m.enterOrBuild(p);
        }
        return 1;
    }

    private static int playSingle(CommandContext<CommandSourceStack> c, int npcs, Difficulty d) {
        com.squidgame.core.GameKind kind = com.squidgame.core.GameKind.byId(StringArgumentType.getString(c, "game"));
        if (kind == null) {
            c.getSource().sendFailure(Component.literal("Unknown game. One of: red_light, dalgona, tug_of_war, marbles, glass_bridge, final"));
            return 0;
        }
        TournamentManager m = mgr(c);
        ServerPlayer p = c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        String err = m.startSingleGame(p, kind, d, npcs, p == null);
        if (err != null) {
            c.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    private static int tour(CommandContext<CommandSourceStack> c, String name) {
        ServerPlayer p;
        try {
            p = c.getSource().getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            c.getSource().sendFailure(Component.literal("Players only."));
            return 0;
        }
        ArenaId id = ArenaId.byId(name);
        if (id == null) {
            c.getSource().sendFailure(Component.literal("Unknown arena."));
            return 0;
        }
        String err = mgr(c).tourArena(p, id);
        if (err != null) {
            c.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }
}
