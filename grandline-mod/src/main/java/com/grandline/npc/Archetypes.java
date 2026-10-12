package com.grandline.npc;

import com.grandline.npc.Archetype.Hostility;
import com.grandline.player.Faction;
import com.grandline.player.Race;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Role → archetype table. Roles come from building/landmark markers and encounter tables. */
public final class Archetypes {
    private static final Map<String, Archetype> BY_ROLE = new HashMap<>();

    private static void a(String id, Faction f, int tier, List<String> weapons, String style, List<String> skins, Hostility h,
                          boolean kills, double fruit, double haki, float hp, int armor, Race race, boolean merchant, boolean coward) {
        BY_ROLE.put(id, new Archetype(id, f, tier, weapons, style, skins, h, kills, fruit, haki, hp, armor, race, merchant, coward));
    }

    static {
        List<String> none = List.of();
        // civilians
        for (String r : List.of("resident", "villager", "child", "farmer", "patron", "miller", "keeper", "lighthouse_keeper", "canal_guide", "station_master", "dockworker", "student", "scholar"))
            a(r, Faction.CIVILIAN, 1, none, "brawler", List.of("villager_1", "villager_2", "villager_3", "villager_4", "villager_5"), Hostility.PASSIVE, false, 0, 0, 20, 0, Race.HUMAN, false, true);
        for (String r : List.of("shopkeeper", "merchant", "bartender", "innkeeper", "blacksmith", "shipwright", "head_chef", "cook", "waiter", "priest", "noble", "doctor", "apprentice_doctor", "elder"))
            a(r, Faction.CIVILIAN, 1, r.equals("blacksmith") || r.equals("shipwright") ? List.of("war_hammer") : r.contains("chef") || r.equals("cook") ? List.of("knife") : none,
                    r.contains("chef") || r.equals("cook") ? "black_leg" : "brawler", List.of("merchant_1", "merchant_2", "villager_3"), Hostility.DEFENSIVE, false, 0, 0, 24, 0, Race.HUMAN, true, false);
        a("guard", Faction.CIVILIAN, 2, List.of("spear", "cutlass"), "brawler", List.of("guard_1"), Hostility.LAWFUL, false, 0, 0.05, 30, 4, Race.HUMAN, false, false);
        a("lookout", Faction.PIRATE, 2, List.of("musket", "flintlock"), "sniper", List.of("pirate_1", "pirate_2"), Hostility.AGGRESSIVE, false, 0, 0, 24, 2, Race.HUMAN, false, false);
        a("master", Faction.CIVILIAN, 6, List.of("katana"), "ittoryu", List.of("master_1"), Hostility.DEFENSIVE, false, 0, 0.8, 80, 6, Race.HUMAN, false, false);
        // marines
        a("marine", Faction.MARINE, 2, List.of("cutlass", "flintlock", "musket", "spear"), "brawler", List.of("marine_1", "marine_2", "marine_3"), Hostility.LAWFUL, false, 0, 0.02, 28, 4, Race.HUMAN, false, false);
        a("marine_officer", Faction.MARINE, 4, List.of("cutlass", "fine_cutlass", "seastone_jitte"), "rokushiki", List.of("marine_officer_1"), Hostility.LAWFUL, false, 0.05, 0.3, 50, 8, Race.HUMAN, false, false);
        a("marine_captain", Faction.MARINE, 5, List.of("fine_katana", "seastone_jitte", "war_hammer"), "ittoryu", List.of("marine_captain_1"), Hostility.LAWFUL, false, 0.15, 0.6, 80, 10, Race.HUMAN, false, false);
        a("vice_admiral", Faction.MARINE, 8, List.of("fine_katana", "iron_kanabo"), "rokushiki", List.of("vice_admiral_1"), Hostility.LAWFUL, false, 0.4, 1.0, 180, 14, Race.HUMAN, false, false);
        a("admiral", Faction.MARINE, 10, List.of("enma", "murakumogiri"), "rokushiki", List.of("admiral_1"), Hostility.LAWFUL, true, 1.0, 1.0, 320, 18, Race.HUMAN, false, false);
        a("warden", Faction.WORLD_GOVERNMENT, 8, List.of("seastone_jitte"), "brawler", List.of("warden_1"), Hostility.LAWFUL, false, 0.5, 0.8, 200, 14, Race.HUMAN, false, false);
        a("jailer", Faction.WORLD_GOVERNMENT, 4, List.of("spear"), "brawler", List.of("jailer_1"), Hostility.LAWFUL, false, 0, 0.1, 45, 8, Race.HUMAN, false, false);
        a("jailer_beast", Faction.WILD, 6, none, "brawler", List.of("beast_1"), Hostility.AGGRESSIVE, true, 0, 0, 90, 8, Race.GIANT, false, false);
        a("cp_agent", Faction.CIPHER_POL, 6, none, "rokushiki", List.of("cp_agent_1"), Hostility.LAWFUL, true, 0.2, 0.5, 90, 10, Race.HUMAN, false, false);
        a("cp_leader", Faction.CIPHER_POL, 8, none, "rokushiki", List.of("cp_leader_1"), Hostility.LAWFUL, true, 0.8, 0.9, 180, 12, Race.HUMAN, false, false);
        a("celestial_guard", Faction.WORLD_GOVERNMENT, 9, List.of("fine_katana"), "rokushiki", List.of("cp_leader_1"), Hostility.LAWFUL, true, 0.3, 1.0, 220, 16, Race.HUMAN, false, false);
        // outlaws
        a("bandit", Faction.WILD, 1, List.of("knife", "cutlass", "bo_staff"), "brawler", List.of("bandit_1", "bandit_2"), Hostility.AGGRESSIVE, false, 0, 0, 22, 1, Race.HUMAN, false, true);
        a("pirate", Faction.PIRATE, 2, List.of("cutlass", "flintlock", "knife", "dual_blades"), "brawler", List.of("pirate_1", "pirate_2", "pirate_3"), Hostility.AGGRESSIVE, false, 0.02, 0.02, 26, 2, Race.HUMAN, false, true);
        a("pirate_captain", Faction.PIRATE, 4, List.of("fine_cutlass", "anchor_chain", "nodachi"), "brawler", List.of("pirate_captain_1"), Hostility.AGGRESSIVE, true, 0.35, 0.3, 70, 6, Race.HUMAN, false, false);
        // the living sea: merchant crews and bounty hunters (ship/LivingSea)
        a("sea_merchant", Faction.CIVILIAN, 2, List.of("flintlock", "fine_cutlass"), "brawler", List.of("trader_1"), Hostility.DEFENSIVE, false, 0, 0.05, 34, 2, Race.HUMAN, true, false);
        a("sailor", Faction.CIVILIAN, 2, List.of("cutlass", "knife", "flintlock"), "brawler", List.of("sailor_1", "sailor_2"), Hostility.DEFENSIVE, false, 0, 0, 28, 1, Race.HUMAN, false, false);
        a("bounty_hunter", Faction.BOUNTY_HUNTER, 3, List.of("katana", "musket", "spear", "flintlock"), "brawler", List.of("bounty_hunter_1", "bounty_hunter_2"), Hostility.LAWFUL, false, 0.05, 0.1, 36, 4, Race.HUMAN, false, false);
        a("ruin_guardian", Faction.WILD, 4, List.of("spear"), "brawler", List.of("guardian_1"), Hostility.AGGRESSIVE, true, 0, 0.2, 60, 8, Race.HUMAN, false, false);
        // fish-men
        a("fishman_guard", Faction.PIRATE, 3, none, "fishman_karate", List.of("fishman_1", "fishman_2"), Hostility.AGGRESSIVE, false, 0, 0, 40, 4, Race.FISHMAN, false, false);
        a("fishman_brute", Faction.PIRATE, 4, List.of("kanabo", "war_trident"), "fishman_karate", List.of("fishman_2"), Hostility.AGGRESSIVE, false, 0, 0.1, 60, 6, Race.FISHMAN, false, false);
        a("fishman_boss", Faction.PIRATE, 6, List.of("war_trident"), "fishman_karate", List.of("fishman_boss_1"), Hostility.AGGRESSIVE, true, 0, 0.5, 140, 10, Race.FISHMAN, false, false);
        // giants & revolutionaries
        a("giant_warrior", Faction.CIVILIAN, 7, List.of("iron_kanabo", "war_hammer"), "brawler", List.of("giant_1"), Hostility.DEFENSIVE, false, 0, 0.6, 200, 10, Race.GIANT, false, false);
        a("giant_elder", Faction.CIVILIAN, 9, List.of("iron_kanabo"), "hasshoken", List.of("giant_1"), Hostility.PASSIVE, false, 0, 1.0, 300, 14, Race.GIANT, false, false);
        a("revolutionary", Faction.REVOLUTIONARY, 4, List.of("cutlass", "musket"), "brawler", List.of("revolutionary_1"), Hostility.DEFENSIVE, false, 0.1, 0.2, 45, 6, Race.HUMAN, false, false);
        a("kuja_warrior", Faction.CIVILIAN, 6, List.of("war_bow", "spear"), "brawler", List.of("kuja_1"), Hostility.DEFENSIVE, false, 0, 0.9, 70, 6, Race.HUMAN, false, false);
        // the rarer peoples: gladiators of the Corrida Colosseum, Snakeneck lancers serving the Kuja
        a("lunarian_warrior", Faction.PIRATE, 7, List.of("iron_kanabo", "fine_katana"), "brawler", List.of("pirate_captain_1"), Hostility.DEFENSIVE, false, 0, 0.7, 160, 12, Race.LUNARIAN, false, false);
        a("buccaneer_brawler", Faction.PIRATE, 6, List.of("brass_knuckles", "war_hammer"), "brawler", List.of("pirate_1", "pirate_2"), Hostility.DEFENSIVE, false, 0, 0.4, 130, 10, Race.BUCCANEER, false, false);
        a("snakeneck_lancer", Faction.CIVILIAN, 4, List.of("spear", "war_bow"), "brawler", List.of("kuja_1"), Hostility.DEFENSIVE, false, 0, 0.3, 55, 5, Race.SNAKENECK, false, false);
    }

    public static Archetype get(String role) {
        Archetype a = BY_ROLE.get(role);
        return a != null ? a : BY_ROLE.get("villager");
    }

    public static boolean has(String role) {
        return BY_ROLE.containsKey(role);
    }

    public static java.util.Set<String> roles() {
        return BY_ROLE.keySet();
    }

    private Archetypes() {}
}
