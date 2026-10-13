package com.longnight.game;

import com.longnight.content.Content;
import com.longnight.content.NameSet;
import com.longnight.registry.ModItems;
import com.longnight.sim.Building;
import com.longnight.sim.BuildingType;
import com.longnight.sim.Construction;
import com.longnight.sim.Faction;
import com.longnight.sim.Growth;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldBuilder;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * A player founds a settlement by placing a hearthstone while carrying a Founding Charter
 * (bought from a town steward or crafted). A few landless settlers answer the charter;
 * more come if there is food and shelter. The player is the founder and lord, but the people
 * still plan and build for themselves; the player steers with materials, coin and requests.
 */
public final class Founding {
    private Founding() {}

    public static void found(ServerPlayer player, ServerLevel level, BlockPos pos) {
        WorldSim sim = WorldSim.get(level.getServer());
        if (com.longnight.sim.Band.of(sim, player.getUUID()) != null) {
            // the leader of a company sets down a hearthstone: their own people settle here
            settleBand(player, level, pos.below(), false);
            return;
        }
        Settlement near = sim.settlementAt(pos, 48);
        if (near != null) {
            if (near.kind == Settlement.Kind.SETTLEMENT) Ui.msg(player, "This hearthstone now stands in " + near.name + ". (Settlements need room: found yours further away.)");
            return;
        }
        int charter = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.FOUNDING_CHARTER.get())) charter = i;
        }
        if (charter < 0 && !player.isCreative()) {
            Ui.msg(player, "A hearthstone alone does not make a settlement. Carry a Founding Charter when you set it down.");
            return;
        }
        if (sim.claimed(pos, 0)) {
            for (WorldSim.Claim c : sim.claims) {
                if (c.contains(pos, 0) && !c.owner().equals(player.getUUID())) {
                    Ui.msg(player, "This land is claimed by " + c.ownerName() + ".");
                    return;
                }
            }
        }
        if (charter >= 0) player.getInventory().getItem(charter).shrink(1);
        long day = WorldSim.dayOf(level.getServer().overworld());
        Settlement nearest = sim.nearestSettlement(pos, Settlement.Kind.SETTLEMENT);
        String culture = nearest != null ? nearest.culture : "valemen";
        NameSet ns = Content.names(culture);
        Settlement s = WorldBuilder.newSettlement(sim, culture, pos.below(), day, ns.settlementName(sim.rng));
        s.playerFounded = true;
        s.founderPlayer = player.getUUID();
        s.materialized = true;
        s.radius = 36;
        s.citizenPlayers.add(player.getUUID());
        s.reputation.put(player.getUUID(), 50);
        s.visitors.add(player.getUUID());
        Faction f = new Faction(UUID.randomUUID());
        f.name = "Lordship of " + s.name;
        f.culture = culture;
        f.government = Faction.Government.LORDSHIP;
        f.capital = s.id;
        f.playerRealm = true;
        f.playerRuler = player.getUUID();
        f.foundedDay = day;
        f.bannerColor = sim.rng.nextInt(16);
        sim.factions.put(f.id, f);
        s.faction = f.id;
        // settlers answer the charter: a builder, a farmer and a hand
        for (int i = 0; i < 3; i++) {
            Person p = sim.createPerson(i == 2 && sim.rng.nextBoolean() ? Content.CULTURE_IDS[sim.rng.nextInt(5)] : culture, null,
                    day - (long) ((sim.rules.adultAge + 2 + sim.rng.nextInt(15)) * sim.rules.daysPerYear), sim.rng);
            Growth.giveTrade(p, sim.rng);
            if (i == 0) p.skills.put(com.longnight.sim.Profession.BUILDER, 35f);
            if (i == 1) p.skills.put(com.longnight.sim.Profession.FARMER, 35f);
            sim.join(p, s);
            p.remember(day, "pioneer", null, "answered the charter of " + player.getGameProfile().getName() + " to found " + s.name, 15);
            p.changeOpinion(player.getUUID(), 30);
        }
        Building tents = new Building(UUID.randomUUID(), BuildingType.CAMPSITE);
        tents.seed = sim.rng.nextLong();
        tents.origin = s.center.offset(-4, 0, 5);
        tents.faith = "hearth";
        Construction.initPlan(s, tents);
        s.buildings.add(tents);   // pitched by the settlers (paid by their own packs below)
        tents.progress = tents.total;
        tents.state = Building.State.COMPLETE;
        s.stock.add(com.longnight.sim.Commodity.BREAD, 20);
        s.stock.add(com.longnight.sim.Commodity.PLANKS, 30);
        s.stock.add(com.longnight.sim.Commodity.LOGS, 10);
        s.chronicle(day, "Founded by " + player.getGameProfile().getName() + " under a charter of settlement.");
        sim.addNews(day, "founding", player.getGameProfile().getName() + " has founded " + s.name + " and calls for settlers.", s, 3,
                "person", player.getGameProfile().getName(), "other_settlement", s.name);
        sim.setDirty();
        Ui.msg(player, "You set down the hearthstone and read out the charter. " + s.name + " is founded! Three settlers have answered your call.");
        player.sendSystemMessage(Ui.hint("Settlers need food and shelter. Bring logs, planks, stone and food to a storehouse (they will build one); "
                + "use /longnight plan <building> to ask for a building, /longnight rename <name> to rename the place."));
    }

    /**
     * The player's travelling company makes its home where the player stands (ground = the block under their feet).
     * Uses up a Founding Charter if the player carries one (the company's own charter needs none in creative).
     * Returns true if they settled.
     */
    public static boolean settleBand(ServerPlayer player, ServerLevel level, BlockPos ground, boolean placeHearth) {
        WorldSim sim = WorldSim.get(level.getServer());
        Settlement band = com.longnight.sim.Band.of(sim, player.getUUID());
        if (band == null) {
            Ui.msg(player, "You have no company with you. (Carry a Founding Charter and set down a hearthstone to call settlers.)");
            return false;
        }
        if (band.residents.isEmpty()) {
            Ui.msg(player, "There is no one left of your company to settle with you.");
            return false;
        }
        if (level != level.getServer().overworld()) {
            Ui.msg(player, "Your company will not make a home in this place.");
            return false;
        }
        String why = com.longnight.sim.Band.unsuitable(sim, ground, player.getUUID());
        if (why != null) {
            Ui.msg(player, why);
            return false;
        }
        int charter = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.FOUNDING_CHARTER.get())) charter = i;
        }
        if (charter < 0 && !player.isCreative()) {
            Ui.msg(player, "You need the Founding Charter to claim this land for your company.");
            return false;
        }
        if (charter >= 0) player.getInventory().getItem(charter).shrink(1);
        long day = WorldSim.dayOf(level.getServer().overworld());
        String leader = player.getGameProfile().getName();
        Settlement s = com.longnight.sim.Band.settle(sim, band, ground, leader, day);
        if (placeHearth) {
            BlockPos hearth = placeHearthstone(level, ground);
            if (hearth != null) {
                player.sendSystemMessage(Ui.hint("Your hearthstone stands " + Ui.where(player, hearth)
                        + ". Right-click it to see how the village is doing; sneak + right-click with logs, planks, stone or food to give them."));
            } else {
                s.materialized = false; // the ground around is still loading: it is set down as soon as it is there
            }
        }
        Ui.msg(player, "You read out the charter and your company sets down their packs. " + s.name + " is founded, and you are its lord.");
        player.sendSystemMessage(Ui.hint("They pitch tents first, then plan and build for themselves: a lumber camp, houses, fields. "
                + "Watch them work, bring logs, planks, stone and food to help, and use /longnight plan <building> to ask for something."));
        return true;
    }

    /**
     * Sets the hearthstone down near a settlement's centre on real ground: the usual spot 4 blocks north first, then a few
     * others around it, each searched from 4 blocks above to 4 below the centre (slopes, hollows, tall grass, snow).
     * Never in water, never on leaves, never replacing anything but grass and the like. Returns where, or null.
     */
    public static BlockPos placeHearthstone(ServerLevel level, BlockPos ground) {
        int[][] spots = {{0, -4}, {3, -3}, {-3, -3}, {4, 0}, {-4, 0}, {0, 4}, {3, 3}, {-3, 3}, {0, -2}, {2, 0}};
        for (int[] o : spots) {
            for (int dy = 5; dy >= -3; dy--) {
                BlockPos q = ground.offset(o[0], dy, o[1]);
                if (!com.longnight.world.Places.ready(level, q)) continue;
                net.minecraft.world.level.block.state.BlockState at = level.getBlockState(q), below = level.getBlockState(q.below());
                if (!(at.isAir() || at.canBeReplaced()) || !at.getFluidState().isEmpty()) continue;
                if (!below.isSolid() || below.is(net.minecraft.tags.BlockTags.LEAVES) || !below.getFluidState().isEmpty()) continue;
                level.setBlock(q, com.longnight.registry.ModBlocks.HEARTHSTONE.get().defaultBlockState(), 3);
                return q;
            }
        }
        return null;
    }

    /** First arrival: the player sets out with a company of settlers who follow them until they choose a place. */
    public static void startBand(ServerPlayer player, WorldSim sim, int size) {
        if (size <= 0 || com.longnight.sim.Band.of(sim, player.getUUID()) != null) return;
        long day = WorldSim.dayOf(player.serverLevel().getServer().overworld());
        Settlement band = com.longnight.sim.Band.create(sim, player.getUUID(), player.getGameProfile().getName(), player.blockPosition(), day, size);
        player.getInventory().add(charter());
        StringBuilder who = new StringBuilder();
        for (Person p : sim.residents(band)) {
            if (who.length() > 0) who.append(", ");
            who.append(p.given).append(" the ").append(p.profession.displayName.toLowerCase());
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7You do not travel alone. " + size + " people have chosen to follow you and make a new home "
                + "wherever you choose: §f" + who + "§7.§r"));
        player.sendSystemMessage(Ui.hint("Lead them somewhere good: flat land, water, trees, away from other towns. When you find it, right-click with the "
                + "Founding Charter (or talk to one of them and choose Settle here). They carry food, tools and timber for a start."));
    }

    public static ItemStack charter() {
        return new ItemStack(ModItems.FOUNDING_CHARTER.get());
    }
}
