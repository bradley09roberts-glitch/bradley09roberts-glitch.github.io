package com.longnight.sim;

import com.longnight.content.Content;
import com.longnight.content.NameSet;
import net.minecraft.core.BlockPos;

import java.util.Random;
import java.util.UUID;

/**
 * A player's company of settlers: real people who travel with the player until the player chooses where to settle.
 * On the road the band has no land and no buildings; its centre is wherever its leader stands, and its people live off
 * the packs they carry and what they forage. When the leader settles, the same group becomes the founded settlement,
 * so everyone keeps who they are, what they carry and what they remember.
 */
public final class Band {
    private Band() {}

    /** The trades a company sets out with, in order: the first ones matter most for a young camp. */
    static final Profession[] TRADES = {Profession.BUILDER, Profession.FARMER, Profession.LUMBERJACK, Profession.BUILDER,
            Profession.QUARRIER, Profession.GUARD, Profession.HUNTER, Profession.CARPENTER, Profession.FARMER, Profession.LABOURER};

    public static Settlement of(WorldSim sim, UUID leader) {
        for (Settlement s : sim.settlements.values()) {
            if (s.kind == Settlement.Kind.BAND && leader.equals(s.founderPlayer)) return s;
        }
        return null;
    }

    public static Settlement create(WorldSim sim, UUID leader, String leaderName, BlockPos at, long day, int size) {
        Settlement near = sim.nearestSettlement(at, Settlement.Kind.SETTLEMENT);
        String culture = near != null ? near.culture : "valemen";
        Settlement s = WorldBuilder.newSettlement(sim, culture, at, day, "the company of " + leaderName);
        s.kind = Settlement.Kind.BAND;
        s.playerFounded = true;
        s.founderPlayer = leader;
        s.radius = 8;
        s.reputation.put(leader, 50);
        Random r = sim.rng;
        for (int i = 0; i < size; i++) {
            // most share the culture of the nearest land; a wanderer or two come from further away
            String c = i >= 3 && r.nextInt(3) == 0 ? Content.CULTURE_IDS[r.nextInt(Content.CULTURE_IDS.length)] : culture;
            Person p = sim.createPerson(c, null, day - (long) ((sim.rules.adultAge + 3 + r.nextInt(20)) * sim.rules.daysPerYear), r);
            Profession t = TRADES[i % TRADES.length];
            p.profession = t;
            p.skills.put(t, 35f + r.nextInt(25));
            Growth.giveTrade(p, r); // and something else they can turn a hand to
            sim.join(p, s);
            p.changeOpinion(leader, 35);
            p.remember(day, "pioneer", null, "set out with " + leaderName + " to find a place of our own", 15);
        }
        // they know each other from the road
        for (Person a : sim.residents(s)) for (Person b : sim.residents(s)) if (a != b) a.changeOpinion(b.id, 10 + r.nextInt(15));
        // what they carry: food for the road, seed grain, tools, and enough timber and stone to start
        s.stock.add(Commodity.BREAD, 4.0 * size);
        s.stock.add(Commodity.MEAT, 2.0 * size);
        s.stock.add(Commodity.GRAIN, 4.0 * size);
        s.stock.add(Commodity.TOOLS, size);
        s.stock.add(Commodity.PLANKS, 40);
        s.stock.add(Commodity.LOGS, 16);
        s.stock.add(Commodity.STONE, 24);
        s.stock.add(Commodity.FUEL, 10);
        s.stock.add(Commodity.GLASS, 8); // a few panes wrapped in straw, for the first house's windows
        s.chronicle(day, "A company of " + size + " set out with " + leaderName + " to find a place of their own.");
        sim.setDirty();
        return s;
    }

    /** The player's travelling party: their company if they have one, else a new, empty one people can join. */
    public static Settlement party(WorldSim sim, UUID leader, String leaderName, BlockPos at, long day) {
        Settlement b = of(sim, leader);
        if (b != null) return b;
        Settlement near = sim.nearestSettlement(at, Settlement.Kind.SETTLEMENT);
        b = WorldBuilder.newSettlement(sim, near != null ? near.culture : "valemen", at, day, "the company of " + leaderName);
        b.kind = Settlement.Kind.BAND;
        b.playerFounded = true;
        b.founderPlayer = leader;
        b.radius = 8;
        b.reputation.put(leader, 50);
        sim.setDirty();
        return b;
    }

    /** On the road the company hunts and gathers as it goes: not a feast, but enough to stretch the packs. */
    static void forage(WorldSim sim, Settlement s) {
        int n = s.residents.size();
        s.stock.add(Commodity.VEGETABLES, 0.5 * n);
        s.stock.add(Commodity.MEAT, 0.2 * n);
    }

    /** Is this place far enough from other settlements (and not someone else's claim) to found one here? Null if so. */
    public static String unsuitable(WorldSim sim, BlockPos pos, UUID leader) {
        for (Settlement o : sim.settlements.values()) {
            if (o.kind == Settlement.Kind.BAND || o.center == null) continue;
            double d = Math.sqrt(o.center.distSqr(new BlockPos(pos.getX(), o.center.getY(), pos.getZ())));
            if (d < o.radius + 48) return o.kind == Settlement.Kind.SETTLEMENT ? "This is the land of " + o.name + ". Settle further away."
                    : "Too close to " + o.name + ".";
        }
        for (WorldSim.Claim c : sim.claims) {
            if (c.contains(pos, 0) && !c.owner().equals(leader)) return "This land is claimed by " + c.ownerName() + ".";
        }
        return null;
    }

    /**
     * The company makes its home here: the band becomes a settlement and the leader's lordship. Tents are pitched from
     * their packs; from then on they plan and build for themselves.
     */
    public static Settlement settle(WorldSim sim, Settlement band, BlockPos at, String leaderName, long day) {
        NameSet ns = Content.names(band.culture);
        band.kind = Settlement.Kind.SETTLEMENT;
        band.name = ns.settlementName(sim.rng);
        band.center = at;
        band.radius = 36;
        band.materialized = true;
        band.foundedDay = day;
        band.citizenPlayers.add(band.founderPlayer);
        band.visitors.add(band.founderPlayer);
        Faction f = new Faction(UUID.randomUUID());
        f.name = "Lordship of " + band.name;
        f.culture = band.culture;
        f.government = Faction.Government.LORDSHIP;
        f.capital = band.id;
        f.playerRealm = true;
        f.playerRuler = band.founderPlayer;
        f.foundedDay = day;
        f.bannerColor = sim.rng.nextInt(16);
        sim.factions.put(f.id, f);
        band.faction = f.id;
        Building tents = new Building(UUID.randomUUID(), BuildingType.CAMPSITE);
        tents.seed = sim.rng.nextLong();
        tents.origin = at.offset(-4, 0, 5);
        tents.faith = "hearth";
        Construction.initPlan(band, tents);
        band.buildings.add(tents); // pitched from their packs: the canvas and poles were carried, not taken from the stores
        tents.progress = tents.total;
        tents.state = Building.State.COMPLETE;
        for (Person p : sim.residents(band)) {
            p.remember(day, "founding", null, "founded " + band.name + " with " + leaderName, 25);
            p.activity = "making camp";
        }
        band.chronicle(day, "Founded by " + leaderName + " and the company who travelled with them.");
        sim.addNews(day, "founding", leaderName + " and their company have founded " + band.name + ".", band, 3,
                "person", leaderName, "other_settlement", band.name);
        sim.setDirty();
        return band;
    }
}
