package com.longnight.game;

import com.longnight.sim.Band;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.server.level.ServerPlayer;

/**
 * A lord's orders to their own people (those of a settlement they founded, or of their travelling company):
 * help the builders, fell trees, go back to their own trade, come along on the road, or go home.
 */
public final class Orders {
    private Orders() {}

    /** Gives an order; returns what the person answers (or why it cannot be given). */
    public static String give(ServerPlayer lord, WorldSim sim, Person x, String what) {
        if (x == null || !x.alive) return "There is no one by that name.";
        Settlement s = sim.settlement(x.settlement);
        if (s == null || !lord.getUUID().equals(s.founderPlayer)) return x.given + " does not answer to you.";
        long day = WorldSim.dayOf(lord.serverLevel().getServer().overworld());
        if (!sim.isAdult(x, day)) return x.given + " is only a child.";
        String reply;
        switch (what) {
            case "build" -> {
                x.order = "build";
                reply = "Aye. I'll lend a hand with the building.";
            }
            case "fell" -> {
                x.order = "fell";
                reply = "I'll fetch an axe. The wood goes to the stores.";
            }
            case "trade" -> {
                x.order = "";
                reply = "Back to my own work as a " + x.profession.displayName.toLowerCase() + ", then.";
            }
            case "follow" -> {
                if (s.kind == Settlement.Kind.BAND) return x.given + " is already on the road with you.";
                Settlement party = Band.party(sim, lord.getUUID(), lord.getGameProfile().getName(), lord.blockPosition(), day);
                x.order = "";
                sim.join(x, party);
                x.remember(day, "journey", null, "left " + s.name + " to travel with " + lord.getGameProfile().getName(), 6);
                reply = "Lead on. I'll keep close.";
            }
            case "home" -> {
                if (s.kind != Settlement.Kind.BAND) return x.given + " is already at home in " + s.name + ".";
                Settlement home = null;
                for (Settlement o : sim.settlements.values()) {
                    if (o.kind != Settlement.Kind.SETTLEMENT || !lord.getUUID().equals(o.founderPlayer) || o.center == null) continue;
                    if (home == null || o.center.distSqr(lord.blockPosition()) < home.center.distSqr(lord.blockPosition())) home = o;
                }
                if (home == null) return "\"We have no home yet. Find us a place and settle.\"";
                sim.join(x, home);
                reply = "I'll go back to " + home.name + ".";
            }
            default -> {
                return "Orders: build, fell, trade, follow, home.";
            }
        }
        x.changeOpinion(lord.getUUID(), 1);
        sim.setDirty();
        return "\"" + reply + "\"";
    }
}
