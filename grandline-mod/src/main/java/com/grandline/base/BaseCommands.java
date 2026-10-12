package com.grandline.base;

import com.grandline.GrandLine;
import com.grandline.base.CrewBases.Claim;
import com.grandline.ship.BaseRaids;
import com.grandline.ship.ShipEntity;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /base} for every player: the base menu (also opened by using the flag) with clickable upgrade, hire and dock
 * buttons, plus {@code build|hire|dock|name|ship|list|abandon|transfer}; operators also get {@code raid} (start a raid on
 * the base you stand in) and {@code remove <island>}.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class BaseCommands {

    private static MutableComponent button(Component label, String command, ChatFormatting colour, @Nullable Component hover) {
        return Component.literal("[").append(label).append("]").withStyle(Style.EMPTY.withColor(colour)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover != null ? hover : Component.literal(command))));
    }

    /** The base menu for a claim (the player's own base when {@code c} is null). */
    public static int menu(ServerPlayer sp, @Nullable Claim c) {
        if (c == null) c = CrewBases.inOwnBase(sp) != null ? CrewBases.inOwnBase(sp) : CrewBases.baseOf(sp);
        if (c == null) {
            CrewBases.tell(sp, Component.translatable("grandline.base.none").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        var server = sp.server;
        boolean crew = CrewBases.isCrew(sp, c);
        CrewBases.tell(sp, Component.translatable("grandline.base.menu.title", c.crewName(), CrewBases.islandName(server, c)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        CrewBases.tell(sp, Component.translatable("grandline.base.menu.flag", c.flag.getX(), c.flag.getY(), c.flag.getZ(), c.founderName,
                c.hasDock ? Component.translatable("grandline.base.menu.dock", c.dockX, c.dockZ) : Component.translatable("grandline.base.menu.no_dock")).withStyle(ChatFormatting.GRAY));
        if (!crew) return 1;
        // upgrades
        MutableComponent ups = Component.translatable("grandline.base.menu.upgrades").withStyle(ChatFormatting.YELLOW);
        for (BaseUpgrades.Upgrade u : BaseUpgrades.ALL.values()) {
            ups.append(" ");
            if (c.has(u.id())) ups.append(Component.literal("✔ ").append(BaseUpgrades.name(u.id())).withStyle(ChatFormatting.GREEN));
            else ups.append(button(BaseUpgrades.name(u.id()), "/base build " + u.id(), ChatFormatting.AQUA,
                    Component.translatable("grandline.base.upgrade." + u.id() + ".desc").append("\n").append(BaseUpgrades.cost(u))));
        }
        CrewBases.tell(sp, ups);
        // hands
        MutableComponent hands = Component.translatable("grandline.base.menu.hands", c.hands.size(), BaseHands.capacity(c)).withStyle(ChatFormatting.YELLOW);
        if (BaseHands.capacity(c) > c.hands.size())
            BaseHands.ROLES.forEach((role, wage) -> hands.append(" ").append(button(Component.translatable("role.grandline." + role), "/base hire " + role, ChatFormatting.AQUA,
                    Component.translatable("grandline.base.beli", String.format("%,d", wage)))));
        CrewBases.tell(sp, hands);
        // the dock and the slip
        List<ShipEntity> moored = CrewBases.mooredShips(sp.serverLevel(), c, CrewBases.online(server));
        MutableComponent dock = Component.translatable("grandline.base.menu.moored", moored.size()).withStyle(ChatFormatting.YELLOW);
        dock.append(" ").append(button(Component.translatable("grandline.base.menu.set_dock"), "/base dock", ChatFormatting.AQUA, Component.translatable("grandline.base.menu.set_dock.desc")));
        if (c.has(BaseUpgrades.SLIP))
            for (String up : com.grandline.ship.Shipyard.UPGRADES.keySet()) {
                Long price = BaseUpgrades.slipPrice(up);
                if (price != null) dock.append(" ").append(button(Component.translatable("ship_upgrade.grandline." + up), "/base ship " + up, ChatFormatting.DARK_AQUA,
                        Component.translatable("grandline.base.beli", String.format("%,d", price))));
            }
        CrewBases.tell(sp, dock);
        int stacks = 0;
        for (int i = 0; i < c.warehouse.getContainerSize(); i++) if (!c.warehouse.getItem(i).isEmpty()) stacks++;
        BaseRaids.Raid raid = BaseRaids.raidOn(c.island);
        CrewBases.tell(sp, Component.translatable("grandline.base.menu.status", stacks, CrewBases.WAREHOUSE_SIZE, c.raidsRepelled,
                raid == null ? Component.translatable("grandline.base.menu.no_raid") : Component.translatable("grandline.base.menu.raid_" + raid.phase)).withStyle(ChatFormatting.GRAY));
        MutableComponent tail = Component.empty();
        tail.append(button(Component.translatable("grandline.base.menu.rename"), "/base name ", ChatFormatting.GRAY, null).withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/base name "))));
        if (CrewBases.isLeader(sp, c)) tail.append(" ").append(button(Component.translatable("grandline.base.menu.abandon"), "/base abandon", ChatFormatting.RED, Component.translatable("grandline.base.menu.abandon.desc")));
        CrewBases.tell(sp, tail);
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    @Nullable
    private static Claim own(ServerPlayer sp) {
        Claim c = CrewBases.baseOf(sp);
        if (c == null) CrewBases.tell(sp, Component.translatable("grandline.base.none").withStyle(ChatFormatting.GRAY));
        return c;
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("base")
                .executes(c -> menu(player(c), null))
                .then(Commands.literal("build").then(Commands.argument("upgrade", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(BaseUpgrades.ALL.keySet(), b))
                        .executes(c -> {
                            ServerPlayer sp = player(c);
                            Claim cl = own(sp);
                            return cl != null && BaseUpgrades.build(sp, cl, StringArgumentType.getString(c, "upgrade")) ? 1 : 0;
                        })))
                .then(Commands.literal("hire").then(Commands.argument("role", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(BaseHands.ROLES.keySet(), b))
                        .executes(c -> {
                            ServerPlayer sp = player(c);
                            Claim cl = own(sp);
                            return cl != null && BaseHands.hire(sp, cl, StringArgumentType.getString(c, "role")) ? 1 : 0;
                        })))
                .then(Commands.literal("dock").executes(c -> {
                    ServerPlayer sp = player(c);
                    Claim cl = own(sp);
                    return cl != null && CrewBases.setDock(sp, cl) ? 1 : 0;
                }))
                .then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer sp = player(c);
                    Claim cl = own(sp);
                    return cl != null && CrewBases.rename(sp, cl, StringArgumentType.getString(c, "name")) ? 1 : 0;
                })))
                .then(Commands.literal("ship").then(Commands.argument("upgrade", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(com.grandline.ship.Shipyard.UPGRADES.keySet(), b))
                        .executes(c -> {
                            ServerPlayer sp = player(c);
                            Claim cl = own(sp);
                            return cl != null && BaseUpgrades.shipUpgrade(sp, cl, StringArgumentType.getString(c, "upgrade")) ? 1 : 0;
                        })))
                .then(Commands.literal("list").executes(BaseCommands::list))
                .then(Commands.literal("abandon").executes(c -> {
                    ServerPlayer sp = player(c);
                    Claim cl = own(sp);
                    if (cl == null) return 0;
                    CrewBases.tell(sp, Component.translatable("grandline.base.abandon_confirm", CrewBases.islandName(sp.server, cl)).append(" ")
                            .append(button(Component.translatable("grandline.base.menu.abandon"), "/base abandon confirm", ChatFormatting.RED, null)).withStyle(ChatFormatting.GRAY));
                    return 1;
                }).then(Commands.literal("confirm").executes(c -> {
                    ServerPlayer sp = player(c);
                    Claim cl = own(sp);
                    return cl != null && CrewBases.abandon(sp, cl) ? 1 : 0;
                })))
                .then(Commands.literal("transfer").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer sp = player(c);
                    Claim cl = own(sp);
                    return cl != null && CrewBases.transfer(sp, cl, EntityArgument.getPlayer(c, "player")) ? 1 : 0;
                })))
                .then(Commands.literal("raid").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer sp = player(c);
                    Claim cl = CrewBases.claimAt(sp.level(), sp.getBlockX(), sp.getBlockZ());
                    if (cl == null) cl = CrewBases.baseOf(sp);
                    if (cl == null) return 0;
                    BaseRaids.Rules r = new BaseRaids.Rules();
                    r.force = true;
                    List<ServerPlayer> near = new ArrayList<>(CrewBases.crewNear(CrewBases.online(sp.server), cl));
                    if (near.isEmpty()) near.add(sp);
                    BaseRaids.Raid raid = BaseRaids.decide(sp.serverLevel(), cl, near, sp.server.getPlayerList().getPlayerCount(), r);
                    c.getSource().sendSuccess(() -> Component.literal(raid == null ? "No raid (no room under the ship cap, no open water, or one already)" : "Raid: " + raid.ship.shipName() + " (" + raid.faction + ")"), true);
                    return raid == null ? 0 : 1;
                }))
                .then(Commands.literal("remove").requires(s -> s.hasPermission(2)).then(Commands.argument("island", StringArgumentType.word()).executes(c -> {
                    Claim cl = CrewBases.claimOn(c.getSource().getServer(), StringArgumentType.getString(c, "island"));
                    if (cl == null) return 0;
                    CrewBases.remove(c.getSource().getServer(), cl, "struck", null);
                    return 1;
                }))));
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = player(c);
        var all = CrewBases.claims(sp.server);
        if (all.isEmpty()) {
            CrewBases.tell(sp, Component.translatable("grandline.base.list_empty").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        for (Claim cl : all)
            CrewBases.tell(sp, Component.translatable("grandline.base.list_entry", cl.crewName(), CrewBases.islandName(sp.server, cl), cl.flag.getX(), cl.flag.getZ())
                    .withStyle(CrewBases.isCrew(sp, cl) ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return all.size();
    }

    private BaseCommands() {}
}
