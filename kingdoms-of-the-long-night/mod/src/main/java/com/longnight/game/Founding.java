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

    public static ItemStack charter() {
        return new ItemStack(ModItems.FOUNDING_CHARTER.get());
    }
}
