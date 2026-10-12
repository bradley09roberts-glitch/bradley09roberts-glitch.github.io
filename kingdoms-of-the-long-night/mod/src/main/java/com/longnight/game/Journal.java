package com.longnight.game;

import com.longnight.content.Content;
import com.longnight.sim.Calendar;
import com.longnight.sim.Faction;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The player's own knowledge of the world: only places they have visited or heard of. */
public final class Journal {
    private Journal() {}

    public static void open(ServerPlayer p) {
        WorldSim sim = WorldSim.get(p.serverLevel().getServer());
        long day = WorldSim.dayOf(p.serverLevel().getServer().overworld());
        p.sendSystemMessage(Component.literal("== Traveller's Journal ==").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.sendSystemMessage(Component.literal(Calendar.describe(day, sim.rules.daysPerYear) + ". Day " + day + " of your journey.")
                .withStyle(ChatFormatting.GRAY));
        Set<UUID> known = sim.knownPlaces.getOrDefault(p.getUUID(), Set.of());
        List<Settlement> list = new ArrayList<>();
        for (UUID id : known) {
            Settlement s = sim.settlement(id);
            if (s != null && s.center != null) list.add(s);
        }
        list.sort(Comparator.comparingDouble(s -> s.center.distSqr(p.blockPosition())));
        if (list.isEmpty()) Ui.msg(p, "You know of no settlements yet. Follow roads and smoke; ask travellers.");
        for (Settlement s : list) {
            double dx = s.center.getX() - p.getX(), dz = s.center.getZ() - p.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            String dir = compass(dx, dz);
            boolean visited = s.visitors.contains(p.getUUID());
            Faction f = sim.faction(s.faction);
            String what = switch (s.kind) {
                case RUIN -> "ruins";
                case BANDIT_CAMP -> "outlaw camp";
                default -> s.tier.displayName.toLowerCase() + " of the " + Content.culture(s.culture).name
                        + (f != null ? ", " + f.name : "");
            };
            Component line = Component.literal((visited ? "" : "(heard of) ") + s.name + " - " + what + ", " + dist + " blocks " + dir)
                    .withStyle(s.kind == Settlement.Kind.BANDIT_CAMP ? ChatFormatting.RED : visited ? ChatFormatting.WHITE : ChatFormatting.GRAY);
            p.sendSystemMessage(line);
        }
        int rep = 0;
        for (Settlement s : sim.settlements.values()) rep = Math.max(rep, s.reputationOf(p.getUUID()));
        p.sendSystemMessage(Ui.hint("Your best standing anywhere: " + Ui.standingWord(rep) + ". /longnight help for more."));
    }

    public static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        String[] dirs = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return dirs[(int) Math.floorMod(Math.round(a / 45.0), 8)];
    }
}
