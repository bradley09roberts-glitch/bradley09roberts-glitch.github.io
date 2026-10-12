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
                .then(Commands.literal("ship").then(Commands.argument("class", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ShipClass.values()).map(ShipClass::id), b))
                        .executes(c -> ship(c.getSource(), StringArgumentType.getString(c, "class"), false))
                        .then(Commands.literal("helm").executes(c -> ship(c.getSource(), StringArgumentType.getString(c, "class"), true)))))
                .then(com.grandline.creature.CreatureTestCommands.seaKing())
                .then(com.grandline.creature.CreatureTestCommands.zoan())
                .then(com.grandline.creature.CreatureTestCommands.beastNpc())
                .then(com.grandline.creature.CreatureTestCommands.wildlife())
                .then(com.grandline.creature.CreatureTestCommands.calmBelt())
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
}
