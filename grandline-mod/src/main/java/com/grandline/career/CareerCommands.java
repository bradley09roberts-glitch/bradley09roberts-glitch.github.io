package com.grandline.career;

import com.grandline.GrandLine;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Characters;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /career}: the player's own career (status, and the perks that are orders rather than conversations: squad,
 * ship, Buster Call request, territory claim). {@code /career admin ...} (operators) sets merit or rank, sends a World
 * Government envoy and lists the limited seats.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class CareerCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("career")
                .executes(CareerCommands::status)
                .then(Commands.literal("status").executes(CareerCommands::status))
                .then(Commands.literal("squad").executes(c -> Squads.call(c.getSource().getPlayerOrException()) > 0 ? 1 : 0))
                .then(Commands.literal("dismiss").executes(c -> {
                    Squads.dismiss(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("ship").executes(c -> CareerPerks.callShip(c.getSource().getPlayerOrException()) != null ? 1 : 0))
                .then(Commands.literal("bustercall").then(Commands.argument("target", EntityArgument.player()).executes(c ->
                        CareerPerks.requestBusterCall(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "target")) ? 1 : 0)))
                .then(Commands.literal("claim").executes(c -> {
                    Component why = CareerPerks.claim(c.getSource().getPlayerOrException());
                    if (why != null) c.getSource().sendFailure(why);
                    return why == null ? 1 : 0;
                }))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("merit").then(Commands.argument("n", LongArgumentType.longArg(0)).executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            CharacterData d = Characters.get(sp);
                            if (Careers.ensure(sp, d) == null) return 0;
                            d.career.merit = LongArgumentType.getLong(c, "n");
                            d.career.collected = d.career.merit;
                            d.career.due = false;
                            Careers.check(sp);
                            CharacterManager.markDirty(sp);
                            return ok(c, "Merit " + d.career.merit);
                        })))
                        .then(Commands.literal("rank").then(Commands.argument("n", IntegerArgumentType.integer(0, 12)).executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            CharacterData d = Characters.get(sp);
                            CareerTrack t = Careers.ensure(sp, d);
                            if (t == null) return 0;
                            d.factionRank = Math.min(t.top(), IntegerArgumentType.getInteger(c, "n"));
                            d.career.best = Math.max(d.career.best, d.factionRank);
                            Careers.changed(sp);
                            CharacterManager.markDirty(sp);
                            return ok(c, "Rank " + t.rank(d.factionRank).id());
                        })))
                        .then(Commands.literal("envoy").executes(c -> Warlords.spawnEnvoy(c.getSource().getPlayerOrException()) != null ? 1 : 0))
                        .then(Commands.literal("seats").executes(c -> {
                            CareerWorldData w = CareerWorldData.get(c.getSource().getServer());
                            StringBuilder sb = new StringBuilder("Seats:");
                            for (String s : CareerWorldData.SEATS.keySet()) {
                                sb.append("\n ").append(s).append(' ').append(w.holders(s).size()).append('/').append(CareerWorldData.SEATS.get(s));
                                for (var h : w.holders(s)) sb.append(" ").append(h.name());
                            }
                            return ok(c, sb.toString());
                        }))));
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        for (Component line : Careers.status(sp)) sp.sendSystemMessage(line);
        return 1;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private CareerCommands() {}
}
