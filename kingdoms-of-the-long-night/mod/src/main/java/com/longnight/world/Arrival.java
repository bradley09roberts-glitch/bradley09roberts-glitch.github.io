package com.longnight.world;

import com.longnight.content.Content;
import com.longnight.game.Ui;
import com.longnight.sim.Faction;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The first time a player walks into a place, they learn what it is and who rules it. */
public final class Arrival {
    private Arrival() {}

    public static void visit(ServerPlayer player, WorldSim sim, Settlement s, long day) {
        if (!s.visitors.add(player.getUUID())) return;
        sim.setDirty();
        switch (s.kind) {
            case RUIN -> player.sendSystemMessage(Component.literal("§7§oYou stand among the ruins of " + s.name
                    + ". Whoever lived here is long gone.§r"));
            case BANDIT_CAMP -> player.sendSystemMessage(Component.literal("§c§oSmoke, crude tents and watchful eyes: an outlaw camp ("
                    + s.name + "). You are not welcome here.§r"));
            default -> {
                Faction f = sim.faction(s.faction);
                Person ruler = f == null ? null : sim.person(f.ruler);
                String realm = f == null ? "beholden to no realm" : "of " + f.name + (ruler != null ? ", ruled by " + ruler.fullName() : "");
                player.sendSystemMessage(Component.literal("§6§l" + s.name + "§r§6 - a " + s.tier.displayName.toLowerCase() + " of the "
                        + Content.culture(s.culture).name + ", " + realm + ". " + s.residents.size() + " souls live here.§r"));
                if (s.raidWarning > 0) player.sendSystemMessage(Component.literal("§cThe folk here look worried; talk is of outlaws on the roads.§r"));
                player.sendSystemMessage(Ui.hint("Right-click people to talk; the hearthstone, storehouse and notice board tell you more."));
                for (Settlement o : sim.settlements.values()) {
                    if (o != s) s.reputation.putIfAbsent(player.getUUID(), 0);
                }
            }
        }
    }
}
