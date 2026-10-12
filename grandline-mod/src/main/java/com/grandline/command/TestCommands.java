package com.grandline.command;

import com.grandline.combat.Abilities;
import com.grandline.combat.Ability;
import com.grandline.combat.CombatManager;
import com.grandline.ship.ShipClass;
import com.grandline.ship.ShipEntity;
import com.grandline.ship.Shipyard;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

import java.util.Arrays;
import java.util.Locale;

/** Verification helpers used by the automated client test and by testers (/grandline test ...). Op only. */
public final class TestCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("test")
                .then(Commands.literal("env").executes(c -> {
                    var s = c.getSource().getServer();
                    s.overworld().setDayTime(6000);
                    s.overworld().setWeatherParameters(24000 * 5, 0, false, false);
                    s.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, s);
                    s.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, s);
                    return GLCommands.ok(c, "Test environment: noon, clear, cycles frozen");
                }))
                .then(Commands.literal("calm").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
                    com.grandline.world.weather.SeaWeather.calm = com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "on");
                    return GLCommands.ok(c, com.grandline.world.weather.SeaWeather.calm ? "Sea weather held calm (until restart or /grandline test calm false)" : "Sea weather live");
                })))
                .then(Commands.literal("ship").then(Commands.argument("class", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ShipClass.values()).map(ShipClass::id), b))
                        .executes(c -> ship(c.getSource(), StringArgumentType.getString(c, "class"), false))
                        .then(Commands.literal("helm").executes(c -> ship(c.getSource(), StringArgumentType.getString(c, "class"), true)))))
                .then(Commands.literal("sail").then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    if (!(sp.getVehicle() instanceof ShipEntity ship)) return GLCommands.fail(c, "Not at a ship station");
                    ship.setSail(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "level"));
                    return GLCommands.ok(c, "Sail " + ship.sail());
                })))
                .then(Commands.literal("anchor").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    if (!(sp.getVehicle() instanceof ShipEntity ship)) return GLCommands.fail(c, "Not at a ship station");
                    ship.setAnchored(com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "on"));
                    return GLCommands.ok(c, "Anchored " + ship.anchored());
                })))
                .then(Commands.literal("boss").then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                    // stage a boss fight ten blocks in front of the player (client test of the boss bar and telegraphs)
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(c, "id");
                    if (com.grandline.boss.Bosses.get(id) == null) return GLCommands.fail(c, "Not a boss: " + id);
                    var at = net.minecraft.core.BlockPos.containing(sp.position().add(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(10)));
                    var boss = com.grandline.test.BossTests.boss(sp.serverLevel(), id, at);
                    var fight = com.grandline.boss.BossManager.start(boss);
                    if (fight == null) return GLCommands.fail(c, "Fight did not start");
                    fight.engage(sp);
                    return GLCommands.ok(c, "Boss " + id + " engaged");
                })))
                .then(Commands.literal("passing").executes(c -> passing(c, -1))
                        .then(Commands.argument("offset", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 30))
                                .executes(c -> passing(c, com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "offset")))))
                .then(Commands.literal("calm").executes(c -> {
                    // pause the living sea and clear NPC ships nearby (two-client test: no chance encounter at the start)
                    com.grandline.core.GLConfig.LIVING_SEA.set(false);
                    var l = c.getSource().getLevel();
                    var at = c.getSource().getPosition();
                    int n = 0;
                    for (var sh : l.getEntitiesOfClass(com.grandline.ship.ShipEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(400))) {
                        if (sh.npcFaction.isEmpty()) continue;
                        for (var u : com.grandline.ship.NavalCombat.npcCrew(sh)) {
                            var e = l.getEntity(u);
                            if (e != null) e.discard();
                        }
                        sh.discard();
                        n++;
                    }
                    return GLCommands.ok(c, "Living sea paused; " + n + " NPC ships cleared");
                }))
                .then(Commands.literal("base").executes(c -> {
                    // stand on a claimable island heart holding a crew flag (client test of planting it, the base and the chart)
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    var at = com.grandline.test.BaseTests.stageClaim(sp);
                    if (at == null) return GLCommands.fail(c, "No claimable island heart found");
                    return GLCommands.ok(c, "Claim staged at " + at.getX() + " " + at.getY() + " " + at.getZ());
                }))
                .then(Commands.literal("board").executes(c -> {
                    // open the notice board of the island the player is on (client test of the board screen)
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    var plan = com.grandline.world.WorldPlan.forLevel(sp.level());
                    var isl = plan == null ? null : plan.nearest(sp.getBlockX(), sp.getBlockZ(), 400);
                    if (isl == null) return GLCommands.fail(c, "No island here");
                    for (var p : isl.settlement().plots)
                        for (var m : com.grandline.npc.MarkerCollector.markers(isl, p))
                            if (m.kind().equals("bounty_board")) {
                                com.grandline.quest.story.JobBoard.open(sp, isl, m.pos());
                                return GLCommands.ok(c, "Board at " + m.pos().toShortString());
                            }
                    return GLCommands.fail(c, "No notice board on " + isl.id);
                }))
                .then(Commands.literal("storm").executes(c -> {
                    // fly to the nearest violent weather (testing the weather system)
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    var ow = sp.serverLevel();
                    double bx = 0, bz = 0;
                    float best = 0;
                    for (int dx = -12000; dx <= 12000; dx += 300)
                        for (int dz = -12000; dz <= 12000; dz += 300) {
                            var w = com.grandline.world.weather.SeaWeather.at(ow, sp.getX() + dx, sp.getZ() + dz);
                            boolean violent = w.type() == com.grandline.world.weather.SeaWeather.Type.STORM || w.type() == com.grandline.world.weather.SeaWeather.Type.CYCLONE;
                            if (violent && w.intensity() > best) {
                                best = w.intensity();
                                bx = sp.getX() + dx;
                                bz = sp.getZ() + dz;
                            }
                        }
                    if (best == 0) return GLCommands.fail(c, "No storm within 12,000 blocks");
                    sp.teleportTo(ow, bx, 90, bz, sp.getYRot(), 10);
                    return GLCommands.ok(c, "Storm at " + (int) bx + ", " + (int) bz + " intensity " + best);
                }))
                .then(com.grandline.creature.CreatureTestCommands.seaKing())
                .then(com.grandline.creature.CreatureTestCommands.zoan())
                .then(com.grandline.creature.CreatureTestCommands.beastNpc())
                .then(com.grandline.creature.CreatureTestCommands.wildlife())
                .then(com.grandline.creature.CreatureTestCommands.calmBelt())
                .then(StructureCommands.view())
                .then(StructureCommands.place())
                .then(StructureCommands.showcase())
                .then(StructureCommands.kit())
                .then(Commands.literal("inventory").executes(c -> {
                    try {
                        var out = com.grandline.test.InventoryDump.write(c.getSource().getServer());
                        return GLCommands.ok(c, "Inventory written to " + out);
                    } catch (Exception ex) {
                        return GLCommands.fail(c, "Inventory failed: " + ex);
                    }
                }))
                .then(Commands.literal("ability").then(Commands.argument("id", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Abilities.all().stream().map(Ability::id), b))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            Ability a = Abilities.get(StringArgumentType.getString(c, "id"));
                            if (a == null) return GLCommands.fail(c, "Unknown ability");
                            return CombatManager.useAbility(sp, a, null) ? GLCommands.ok(c, "Used " + a.id()) : GLCommands.fail(c, "Ability refused");
                        })));
    }

    private static int ship(CommandSourceStack src, String cls, boolean helm) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ShipClass c;
        try {
            c = ShipClass.valueOf(cls.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("Unknown ship class"));
            return 0;
        }
        ShipEntity ship = Shipyard.spawnAtDock(sp, sp, c);
        if (ship == null) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("No water nearby"));
            return 0;
        }
        if (helm) {
            ship.seatPlayer(sp, ship.helmSeat);
            ship.setAnchored(false);
            ship.setSail(2);
        } else {
            var deck = ship.toWorld(0, 1.2, -c.length * 0.25);
            sp.teleportTo(deck.x, deck.y, deck.z);
        }
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Spawned " + ship.shipName()), true);
        return 1;
    }

    private TestCommands() {}

    /**
     * A merchant ship sails past the player's ship the other way, {@code offset} blocks off her centreline (-1: just
     * clear of her side). Two-client test: whoever stands on deck must stay on their own deck even where the two
     * hulls overlap.
     */
    private static int passing(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c, double offset)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        var l = sp.serverLevel();
        var mine = l.getEntitiesOfClass(com.grandline.ship.ShipEntity.class, sp.getBoundingBox().inflate(40)).stream()
                .min(java.util.Comparator.comparingDouble(s -> s.distanceToSqr(sp))).orElse(null);
        if (mine == null) return GLCommands.fail(c, "No ship near you");
        var cls = com.grandline.ship.ShipClass.CARAVEL;
        double side = offset < 0 ? mine.cls.beam / 2.0 + cls.beam / 2.0 + 0.6 : offset;
        var at = mine.toWorld(side, 0, mine.cls.length + cls.length);
        var m = com.grandline.ship.SeaEncounters.spawnShip(l, new net.minecraft.world.phys.Vec3(at.x, mine.getY(), at.z), mine.getYRot() + 180, cls,
                com.grandline.ship.LivingSea.MERCHANT, 1, "Test Passer");
        if (m == null) return GLCommands.fail(c, "Could not spawn the passing ship");
        m.npcAiOff = true;
        m.noDespawn = true;
        m.autopilot = true;
        m.setSail(3);
        return GLCommands.ok(c, "Passing ship launched " + String.format(java.util.Locale.ROOT, "%.1f", side) + " blocks off the centreline");
    }
}
