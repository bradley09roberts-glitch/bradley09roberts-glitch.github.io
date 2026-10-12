package com.grandline.ship;

import com.grandline.GrandLine;
import com.grandline.npc.HumanoidNpc;
import com.grandline.registry.ModEntities;
import com.grandline.world.WorldLayout;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /ship: captain's orders for anyone commanding the ship they are aboard (fire at will, hold fire, ammo, cut lines,
 * status), plus op-only /ship test helpers used by the automated client test (enemy ships, gun crews, looks).
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class NavalCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("ship")
                .then(Commands.literal("fire").executes(c -> order(c, "fire", "")))
                .then(Commands.literal("hold").executes(c -> order(c, "hold", "")))
                .then(Commands.literal("cut").executes(c -> order(c, "cut", "")))
                .then(Commands.literal("ammo").then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(NavalCombat.AMMO, b))
                        .executes(c -> order(c, "ammo", StringArgumentType.getString(c, "kind")))))
                .then(Commands.literal("status").executes(NavalCommands::status))
                .then(Commands.literal("test").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("enemy").executes(c -> enemy(c, "brigantine", "pirate"))
                                .then(Commands.argument("class", StringArgumentType.word()).executes(c -> enemy(c, StringArgumentType.getString(c, "class"), "pirate"))
                                        .then(Commands.argument("faction", StringArgumentType.word()).executes(c -> enemy(c, StringArgumentType.getString(c, "class"), StringArgumentType.getString(c, "faction"))))))
                        .then(Commands.literal("squadron").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            boolean hq = SeaEncounters.nearHeadquarters(sp.server, sp.getX(), sp.getZ());
                            int n = SeaEncounters.spawnSquadron(sp.serverLevel(), sp, WorldLayout.zoneAt(sp.getBlockX(), sp.getBlockZ()), hq).size();
                            return n > 0 ? ok(c, "Squadron of " + n + (hq ? " (Headquarters)" : "")) : fail(c, "No open water");
                        }))
                        .then(Commands.literal("gunners").executes(c -> gunners(c, 4))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 20)).executes(c -> gunners(c, IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("look").then(Commands.argument("figurehead", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ShipLook.FIGUREHEADS, b))
                                .then(Commands.argument("emblem", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ShipLook.EMBLEMS, b))
                                        .executes(c -> look(c, 0, 0, 0, 0))
                                        .then(Commands.argument("colours", StringArgumentType.greedyString()).executes(c -> {
                                            // emblem,flag,sail,hull palette indices e.g. 2,3,1,4
                                            String[] p = StringArgumentType.getString(c, "colours").split(",");
                                            int[] v = new int[4];
                                            for (int i = 0; i < 4 && i < p.length; i++) v[i] = Math.max(0, Math.min(7, Integer.parseInt(p[i].trim())));
                                            return look(c, v[0], v[1], v[2], v[3]);
                                        })))))
                        .then(Commands.literal("parade").executes(NavalCommands::parade))
                        .then(Commands.literal("helm").executes(c -> {
                            // back to the helm of the nearest ship the player owns
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            ShipEntity best = null;
                            for (ShipEntity s : sp.serverLevel().getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(150), s -> sp.getUUID().equals(s.owner) && !s.flag(ShipEntity.F_PRIZE)))
                                if (best == null || s.distanceToSqr(sp) < best.distanceToSqr(sp)) best = s;
                            if (best == null) return fail(c, "No ship of yours near");
                            if (best.getFirstPassengerAtSeat(best.helmSeat) != null && best.getFirstPassengerAtSeat(best.helmSeat) != sp) return fail(c, "Helm taken");
                            sp.stopRiding();
                            return best.seatPlayer(sp, best.helmSeat) ? ok(c, "At the helm of " + best.shipName()) : fail(c, "Could not take the helm");
                        }))
                        .then(Commands.literal("grapple").executes(NavalCommands::grapple))
                        .then(Commands.literal("board").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            ShipEntity own = NavalCombat.shipOf(sp);
                            ShipEntity other = own == null ? null : NavalCombat.linked(own);
                            if (other == null || !other.flag(ShipEntity.F_NPC)) return fail(c, "Not grappled to an NPC ship");
                            return ok(c, NavalCombat.launchBoarders(other, own, 4) + " boarders away");
                        }))
                        .then(Commands.literal("clear").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            int n = 0;
                            for (ShipEntity s : sp.serverLevel().getEntitiesOfClass(ShipEntity.class, sp.getBoundingBox().inflate(250), s -> s.flag(ShipEntity.F_NPC) || s.shipName().startsWith("Parade"))) {
                                for (var u : NavalCombat.npcCrew(s)) if (sp.serverLevel().getEntity(u) != null) sp.serverLevel().getEntity(u).discard();
                                NavalCombat.unlink(s);
                                s.ejectPassengers();
                                s.discard();
                                n++;
                            }
                            return ok(c, "Removed " + n + " ships");
                        }))));
    }

    private static int order(CommandContext<CommandSourceStack> c, String op, String arg) throws CommandSyntaxException {
        return NavalCombat.order(c.getSource().getPlayerOrException(), op, arg) ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ShipEntity s = NavalCombat.shipOf(sp);
        if (s == null) return fail(c, "Not aboard a ship");
        ShipEntity l = NavalCombat.linked(s);
        int gunners = 0;
        for (int i = 1; i <= s.cls.cannons; i++) if (s.getFirstPassengerAtSeat(i) != null) gunners++;
        String msg = String.format("%s: hull %d/%d, rigging %d%%, guns %d/%d manned (%s, %s), %s", s.shipName(), (int) s.hull(), (int) s.maxHull(), (int) (s.rigging() * 100),
                gunners, s.cls.cannons, s.flag(ShipEntity.F_FIRE_AT_WILL) ? "fire at will" : "hold fire", NavalCombat.ammo(s),
                l == null ? "no lines" : (s.towing() ? "towing " : "lines to ") + l.shipName());
        return ok(c, msg);
    }

    /** Spawn a hostile NPC ship 45 blocks off the beam of the player's ship (or the player), sailing alongside. */
    private static int enemy(CommandContext<CommandSourceStack> c, String cls, String faction) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel l = sp.serverLevel();
        ShipEntity own = NavalCombat.shipOf(sp);
        Vec3 at = own != null ? own.toWorld(45, 0, 10) : sp.position().add(45, 0, 0);
        float yaw = own != null ? own.getYRot() : sp.getYRot();
        Vec3 pos = Shipyard.findWater(l, BlockPos.containing(at));
        if (pos == null) return fail(c, "No open water");
        boolean marine = faction.equals("marine");
        ShipEntity e = SeaEncounters.spawnShip(l, pos, yaw, ShipClass.byId(cls), marine, Math.max(2, WorldLayout.zoneAt(sp.getBlockX(), sp.getBlockZ()).dangerTier), marine ? "Marine Patrol" : "Raider");
        if (e == null) return fail(c, "Spawn failed");
        e.noDespawn = true; // a test opponent stays even while the tester flies about in spectator mode (cleared by /ship test clear)
        return ok(c, "Spawned " + e.shipName() + " (" + e.cls.id() + ")");
    }

    /** Hire hands for the player's guns (testing): civilian sailors seated at the cannons, fire at will. */
    private static int gunners(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ShipEntity own = NavalCombat.shipOf(sp);
        if (own == null) return fail(c, "Not aboard a ship");
        int n = 0;
        for (int i = 0; i < count; i++) {
            int seat = NavalCombat.freeGun(own);
            if (seat <= 0) break;
            HumanoidNpc h = ModEntities.HUMANOID_NPC.get().create(sp.serverLevel());
            if (h == null) break;
            Vec3 p = own.toWorld(own.seatLocal(seat).x, own.seatLocal(seat).y, own.seatLocal(seat).z);
            h.moveTo(p.x, p.y, p.z, 0, 0);
            h.configure("guard", 4, "", BlockPos.ZERO, "", sp.getRandom());
            sp.serverLevel().addFreshEntity(h);
            if (own.seatEntity(h, seat)) n++;
        }
        NavalCombat.setFireAtWill(own, sp, true);
        return ok(c, n + " gunners at the guns");
    }

    private static int look(CommandContext<CommandSourceStack> c, int ec, int fc, int sc, int hc) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ShipEntity own = NavalCombat.shipOf(sp);
        if (own == null) return fail(c, "Not aboard a ship");
        ShipLook look = new ShipLook(StringArgumentType.getString(c, "figurehead"), StringArgumentType.getString(c, "emblem"), ShipLook.EMBLEM_COLORS[ec], ShipLook.FLAGS[fc]).valid();
        Shipyard.customise(own, look, ShipLook.SAILS[sc], ShipLook.HULLS[hc]);
        return ok(c, "Look " + own.look().encode());
    }

    /** One sloop per figurehead in a line abeam of the player's ship (testing the carvings). */
    private static int parade(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel l = sp.serverLevel();
        ShipEntity own = NavalCombat.shipOf(sp);
        float yaw = own != null ? own.getYRot() : 0;
        int n = 0;
        int count = ShipLook.FIGUREHEADS.size();
        for (int i = 0; i < count; i++) {
            // a line abreast ahead of the bow, so the carvings face back toward the ship
            double lx = (i - (count - 1) / 2.0) * 9;
            Vec3 at = own != null ? own.toWorld(lx, 0, own.cls.length / 2.0 + 30) : sp.position().add(lx, 0, 30);
            ShipEntity s = ModEntities.SHIPS.get(ShipClass.CARAVEL).get().create(l);
            if (s == null) continue;
            s.moveTo(at.x, com.grandline.world.WorldPlan.SEA_LEVEL + 0.9, at.z, yaw + 180, 0);
            s.setShipName("Parade " + ShipLook.FIGUREHEADS.get(i));
            s.setHull(s.cls.hull);
            s.setLook(new ShipLook(ShipLook.FIGUREHEADS.get(i), ShipLook.EMBLEMS.get(i % 7), ShipLook.EMBLEM_COLORS[i % 8], ShipLook.FLAGS[(i + 1) % 8]));
            s.setColors(ShipLook.SAILS[i % 8], ShipLook.HULLS[i % 8]);
            s.setSail(3); // sails set (still anchored) so the emblems show
            l.addFreshEntity(s);
            n++;
        }
        return ok(c, n + " ships on parade");
    }

    private static int grapple(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ShipEntity own = NavalCombat.shipOf(sp);
        if (own == null) return fail(c, "Not aboard a ship");
        ShipEntity best = null;
        double bd = 120 * 120;
        for (ShipEntity s : sp.serverLevel().getEntitiesOfClass(ShipEntity.class, own.getBoundingBox().inflate(120), s -> s != own && s.flag(ShipEntity.F_NPC))) {
            if (s.distanceToSqr(own) < bd) {
                bd = s.distanceToSqr(own);
                best = s;
            }
        }
        if (best == null) return fail(c, "No NPC ship near");
        NavalCombat.link(best, own, false);
        NavalCombat.noteAggression(own, best);
        return ok(c, "Grappled to " + best.shipName());
    }

    static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    private NavalCommands() {}
}
