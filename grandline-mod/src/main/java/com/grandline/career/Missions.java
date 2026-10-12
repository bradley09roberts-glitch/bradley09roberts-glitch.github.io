package com.grandline.career;

import com.grandline.npc.GLNpc;
import com.grandline.player.CharacterData;
import com.grandline.player.CharacterManager;
import com.grandline.player.Faction;
import com.grandline.ship.BusterCall;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Revolutionary missions, given by any revolutionary (dialogue "Any orders for the cell?"), one at a time, in turn:
 * <ul>
 *   <li><b>incite</b>: rouse an island: talk to three of its people, then its elder (not on a Marine-held island);</li>
 *   <li><b>sabotage</b>: strike the Marines: beat three Marines near one of their bases, or beat a Marine ship;</li>
 *   <li><b>liberate</b>: free the captives of the Sabaody auction house: beat two of its guards (or World Government
 *   men) within 80 blocks of it; three captives go free.</li>
 * </ul>
 * Each completed mission counts toward the revolutionary ranks and pays merit.
 */
public final class Missions {
    public static final List<String> ORDER = List.of("incite", "sabotage", "liberate");
    static final int LIBERATE_REACH = 80;
    static final Set<String> COMMONERS = Set.of("resident", "villager", "farmer", "patron", "miller", "dockworker", "keeper", "student", "fisherman", "cook",
            "waiter", "bartender", "shopkeeper", "merchant", "innkeeper", "blacksmith", "shipwright");

    /** The next mission in turn for this revolutionary. */
    public static String next(CharacterData d) {
        return ORDER.get((int) (d.career.count("missions") % ORDER.size()));
    }

    public static boolean assign(ServerPlayer sp, CharacterData d) {
        if (!d.career.mission.isEmpty()) return false;
        d.career.mission = next(d);
        d.career.missionProgress = 0;
        d.career.missionSeen.clear();
        Careers.tell(sp, Component.translatable("grandline.career.mission.given." + d.career.mission).withStyle(ChatFormatting.GREEN));
        CharacterManager.markDirty(sp);
        return true;
    }

    static void progress(ServerPlayer sp, CharacterData d, int need) {
        d.career.missionProgress++;
        if (d.career.missionProgress >= need) complete(sp, d);
        else sp.displayClientMessage(Component.translatable("grandline.career.mission.progress",
                Component.translatable("grandline.career.mission." + d.career.mission), d.career.missionProgress, need).withStyle(ChatFormatting.GREEN), true);
        CharacterManager.markDirty(sp);
    }

    static void complete(ServerPlayer sp, CharacterData d) {
        String m = d.career.mission;
        long merit = switch (m) {
            case "liberate" -> 120;
            case "sabotage" -> 100;
            default -> 80;
        };
        if (m.equals("liberate")) d.career.add("freed", 3);
        d.career.add("missions", 1);
        d.career.mission = "";
        d.career.missionProgress = 0;
        d.career.missionSeen.clear();
        Careers.tell(sp, Component.translatable("grandline.career.mission.done." + m, merit).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        Careers.merit(sp, d, merit);
        Careers.check(sp);
    }

    /** A conversation: inciting an island. */
    static void onTalk(ServerPlayer sp, CharacterData d, GLNpc npc) {
        if (!d.career.mission.equals("incite") || npc.islandId().isEmpty() || npc.faction() != Faction.CIVILIAN) return;
        Island isl = island(sp, npc.islandId());
        if (isl == null || isl.faction.equals("marine")) return;
        var seen = d.career.missionSeen;
        if (!seen.isEmpty() && !seen.get(0).equals("@" + isl.id)) return; // one island at a time (the first talk picks it)
        if (seen.isEmpty()) seen.add("@" + isl.id);
        String who = npc.getUUID().toString();
        if (seen.contains(who)) return;
        int people = seen.size() - 1;
        if (npc.role().equals("elder")) {
            if (people < 3) {
                sp.displayClientMessage(Component.translatable("grandline.career.mission.incite_elder_early", people, 3).withStyle(ChatFormatting.GRAY), true);
                return;
            }
            seen.add(who);
            progress(sp, d, 4);
            return;
        }
        if (!COMMONERS.contains(npc.role()) || people >= 3) return;
        seen.add(who);
        progress(sp, d, 4);
    }

    /** A kill: sabotage near a Marine base, liberation at the auction house. */
    static void onKill(ServerPlayer sp, CharacterData d, GLNpc npc) {
        String m = d.career.mission;
        if (m.equals("sabotage") && npc.faction() == Faction.MARINE && BusterCall.marineBaseNear(npc) != null) progress(sp, d, 3);
        else if (m.equals("liberate") && (npc.role().equals("guard") || npc.faction() == Faction.WORLD_GOVERNMENT) && nearAuction(sp, npc.getX(), npc.getZ()))
            progress(sp, d, 2);
    }

    /** A Marine ship beaten: sabotage done at once. */
    static void onMarineShip(ServerPlayer sp, CharacterData d) {
        if (d.career.mission.equals("sabotage")) complete(sp, d);
    }

    /** The Sabaody auction house (island centre plus its landmark offset), or null without the ocean world. */
    @Nullable
    public static int[] auctionHouse(ServerPlayer sp) {
        Island isl = island(sp, "sabaody_archipelago");
        if (isl == null) return null;
        for (Island.LandmarkPlacement lp : isl.landmarks)
            if (lp.id().equals("auction_house")) return new int[]{isl.x + lp.dx(), isl.z + lp.dz()};
        return null;
    }

    static boolean nearAuction(ServerPlayer sp, double x, double z) {
        int[] a = auctionHouse(sp);
        return a != null && Math.hypot(x - a[0], z - a[1]) < LIBERATE_REACH;
    }

    @Nullable
    private static Island island(ServerPlayer sp, String id) {
        WorldPlan plan = WorldPlan.of(sp.server);
        return plan == null ? null : plan.byId(id);
    }

    private Missions() {}
}
